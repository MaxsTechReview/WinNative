package com.winlator.cmod.feature.stores.steam.wnsteam

import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class WnLauncherStatusTailer(
    private val logFile: File,
    private val gameDisplayName: String,
    private val pollIntervalMs: Long = 200L,
    private val onPhase: (phaseText: String) -> Unit,
    private val onLaunchComplete: (() -> Unit)? = null,
    private val onLaunchFailed: ((reason: String) -> Unit)? = null,
) {
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var thread: Thread? = null
    @Volatile private var lastEmitted: String = ""
    @Volatile private var launchAppDispatchedAt: Long = 0L
    @Volatile private var fileExistedAtStart: Boolean = false
    @Volatile private var launchCompleteSignaled: Boolean = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        fileExistedAtStart = logFile.exists()
        launchCompleteSignaled = false
        android.util.Log.i(TAG, "start: path=" + logFile.absolutePath
                + " exists=" + fileExistedAtStart
                + " size=" + (if (fileExistedAtStart) logFile.length() else -1L)
                + " canRead=" + logFile.canRead())
        thread = Thread({ tailLoop() }, "WnLauncherStatusTailer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    private fun tailLoop() {
        var lastOffset = 0L
        var openedOnce = false
        var iter = 0
        var totalLinesRead = 0
        android.util.Log.i(TAG, "tailLoop: entered, polling every ${pollIntervalMs}ms")
        while (running.get()) {
            iter++
            try {
                if (!logFile.exists()) {
                    if (iter % 25 == 1) {
                        android.util.Log.i(TAG, "tailLoop iter=$iter: file does not yet exist at ${logFile.absolutePath}")
                    }
                    Thread.sleep(pollIntervalMs)
                    continue
                }
                var linesThisIter = 0
                RandomAccessFile(logFile, "r").use { raf ->
                    val len = raf.length()
                    if (!openedOnce) {
                        openedOnce = true
                        if (fileExistedAtStart) {
                            lastOffset = len
                            android.util.Log.i(TAG, "tailLoop: first read; file len=$len — seeking to end (skipping any stale content from previous launch); waiting for launcher to truncate + write new content")
                        } else {
                            lastOffset = 0L
                            android.util.Log.i(TAG, "tailLoop: first read on freshly created log; file len=$len — reading from start")
                        }
                    } else if (len < lastOffset) {
                        android.util.Log.i(TAG, "tailLoop iter=$iter: file shrank from $lastOffset to $len bytes — launcher truncated, resetting offset")
                        lastOffset = 0L
                    }
                    raf.seek(lastOffset)
                    while (true) {
                        val line = raf.readLine() ?: break
                        linesThisIter++
                        totalLinesRead++
                        consumeLine(line)
                    }
                    lastOffset = raf.filePointer
                }
                if (linesThisIter > 0) {
                    android.util.Log.i(TAG, "tailLoop iter=$iter: read $linesThisIter new line(s), totalRead=$totalLinesRead, offset=$lastOffset")
                }
                watchdogTick()
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                android.util.Log.e(TAG, "tail iteration failed", e)
            }
            try {
                Thread.sleep(pollIntervalMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        android.util.Log.i(TAG, "tailLoop: exiting (running=${running.get()}, totalLinesRead=$totalLinesRead)")
    }

    private fun consumeLine(line: String) {
        if (!line.contains("[wn-launcher]")) return
        val isWatchingForExit = line.contains("watching \"") && line.contains("for exit")
        val isTerminal = (line.contains("is running") && line.contains("LaunchApp"))
                || isWatchingForExit
                || line.contains("game process started pid=")
        val isFatal = line.contains("LoadLibrary(") && line.contains("FAILED after all strategies")
        val isLaunchAppDispatched = line.contains("IClientAppManager.LaunchApp(appId=")
        val isCreateProcessFallback = line.contains("LaunchApp dispatched")
                && line.contains("never appeared")
                && line.contains("falling back to CreateProcess")
        val phase = phaseFor(line)
        if (phase != null && phase != lastEmitted) {
            emitPhase(phase, line)
        }
        if (isLaunchAppDispatched) launchAppDispatchedAt = System.currentTimeMillis()
        if (isTerminal) {
            if (launchCompleteSignaled) return
            launchCompleteSignaled = true
            android.util.Log.i(TAG, "terminal phase (LaunchApp is running) — signaling launch complete")
            // Disarm the watchdog: the game spawned successfully, so the 30s
            // post-dispatch timeout would otherwise fire mid-play and kill the
            // activity with a spurious onLaunchFailed.
            launchAppDispatchedAt = 0L
            main.post { onLaunchComplete?.invoke() }
        } else if (isFatal) {
            android.util.Log.w(TAG, "fatal phase (launcher LoadLibrary failed) — signaling launch failure")
            main.post { onLaunchFailed?.invoke("Steam Launcher could not start. Re-staging — please relaunch.") }
        } else if (isCreateProcessFallback) {
            // Keep the UI on "Launching <game>…" through the fallback; disarm the watchdog.
            android.util.Log.w(TAG, "LaunchApp exhausted retries — launcher will try CreateProcess fallback (UI stays on Launching…)")
            launchAppDispatchedAt = 0L
        }
    }

    private fun watchdogTick() {
        val dispatchedAt = launchAppDispatchedAt
        if (dispatchedAt == 0L) return  // disarmed on spawn / fallback
        if (System.currentTimeMillis() - dispatchedAt > LAUNCH_APP_WATCHDOG_MS) {
            android.util.Log.w(TAG, "watchdog: ${LAUNCH_APP_WATCHDOG_MS}ms elapsed after LaunchApp with no terminal — assuming launch failed")
            launchAppDispatchedAt = 0L
            main.post { onLaunchFailed?.invoke("Steam Launcher reached the game but it never started.") }
        }
    }

    private fun emitPhase(phase: String, line: String) {
        lastEmitted = phase
        android.util.Log.i(TAG, "phase change: \"$phase\" (from line: ${line.take(80)})")
        main.post { onPhase(phase) }
    }

    private fun phaseFor(line: String): String? = when {
        line.contains("in-process Steam launcher starting") -> "Starting Steam Launcher…"
        line.contains("steamclient64.dll loaded") -> "Loading Steam client…"
        line.contains("Steam_CreateGlobalUser OK") -> "Connecting to Steam…"
        line.contains("LogOn(") && line.contains("EResult=1") -> "Signing in to Steam…"
        line.contains("callback 101 SteamServersConnected") -> "Signed in — fetching game info…"
        line.contains("Steam_BLoggedOn=true") -> "Steam ready"
        line.contains("RequestAppInfoUpdate(appId=") -> "Updating game info…"
        line.contains("GetAppInstallState(appId=") -> "Verifying install…"
        line.contains("redist scan: scanning") -> "Scanning redistributables…"
        line.contains("installing redistributable:") -> phaseForInstallingRedist(line)
        line.contains("redist scan: installed") -> "Redistributables ready"
        line.contains("redist scan: ") && line.contains(" of ") -> "Redistributables ready"
        line.contains("redist scan: 0 *.exe installers") -> "No redistributables to install"
        line.contains("redist scan: no _CommonRedist") -> "No redistributables to install"
        line.contains("steamservice: post-start state=4") -> "Steam service running"
        line.contains("IClientAppManager.LaunchApp(appId=") -> "Launching $gameDisplayName…"
        line.contains("LoadLibrary(") && line.contains("FAILED after all strategies") ->
            "Steam Launcher failed — re-staging assets"
        else -> null
    }

    private fun phaseForInstallingRedist(line: String): String {
        val marker = "installing redistributable:"
        val start = line.indexOf(marker)
        if (start < 0) return "Installing redistributable…"
        val rest = line.substring(start + marker.length).trim()
        val name = rest.substringBefore(" (").trim()
        val ratio = rest.substringAfter("(", "").substringBefore(",", "").trim()
        return if (name.isNotEmpty() && ratio.contains("/")) {
            "Installing $name… ($ratio)"
        } else if (name.isNotEmpty()) {
            "Installing $name…"
        } else {
            "Installing redistributable…"
        }
    }

    companion object {
        private const val TAG = "WnLauncherTailer"
        private const val LAUNCH_APP_WATCHDOG_MS = 35_000L
    }
}
