package com.winlator.cmod.runtime.system

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Writes a per-session CSV of performance metrics plus a JSON metadata sidecar to
 * `/sdcard/WinNative/logs/`. Sampled at 1 Hz on a dedicated background thread.
 *
 * Lifecycle:
 *  - [start] opens both files. Sidecar is written with `endedAt:null, clean:false` so a
 *    crashed session is identifiable.
 *  - [recordFramePresent] is called from the X server render thread on every present.
 *  - [stop] flushes CSV, fsyncs, and rewrites the sidecar with the final timestamps.
 *
 * Independent of the leaderboard submit path — the user can enable recording without
 * enabling leaderboard upload, and vice versa.
 */
class PerformanceRecorder(
    private val context: Context,
    private val sessionMetadata: SessionMetadata,
) {
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val frameCounter = LongAdder()
    private var lastFrameCount: Long = 0
    private val lastFrameNanoForObserver = AtomicLong(0)
    private val frametimesLock = Any()
    private val frametimesNs = ArrayList<Long>(INITIAL_FRAMETIMES_CAPACITY)

    @Volatile private var sessionStartElapsedRealtimeMs: Long = 0
    @Volatile private var sessionStartWallMs: Long = 0
    @Volatile private var csvFile: File? = null
    @Volatile private var sidecarFile: File? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var writerStream: FileOutputStream? = null

    private var sampler: ScheduledExecutorService? = null
    private var lastCpuTimeNs: Long = 0
    private var lastWallNs: Long = 0

    private val batteryManager: BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * Open both output files and begin sampling. Returns true if recording is active,
     * false if the output directory wasn't writable (logged but not crashy).
     */
    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return true
        sessionStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        sessionStartWallMs = System.currentTimeMillis()
        val dir = LogManager.getLogsDir(context)
        if (!dir.canWrite()) {
            Timber.tag(TAG).w("Logs dir not writable: $dir — recording disabled this session")
            started.set(false)
            return false
        }
        val stem = "perf_${sanitizeGameSlug(sessionMetadata.gameName)}_${TIMESTAMP_FORMAT.format(Date(sessionStartWallMs))}_v1"
        var csv = File(dir, "$stem.csv")
        var meta = File(dir, "$stem.meta.json")
        var n = 1
        while (csv.exists() || meta.exists()) {
            csv = File(dir, "${stem}_$n.csv")
            meta = File(dir, "${stem}_$n.meta.json")
            n++
        }
        csvFile = csv
        sidecarFile = meta
        return try {
            val stream = FileOutputStream(csv, false)
            writerStream = stream
            val w = BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8))
            writer = w
            w.write(CSV_HEADER)
            w.newLine()
            w.flush()
            writeSidecar(endedAtIso = null, clean = false)
            lastCpuTimeNs = Debug.threadCpuTimeNanos()
            lastWallNs = System.nanoTime()
            val exec = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "PerfRecorderWriter").apply {
                    priority = Thread.MIN_PRIORITY + 1
                    isDaemon = true
                }
            }
            sampler = exec
            exec.scheduleAtFixedRate({ tickSafe() }, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS)
            exec.scheduleAtFixedRate({ flushSafe() }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
            Timber.tag(TAG).i("Recording to ${csv.absolutePath}")
            true
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Failed to open recording files")
            closeWriterQuietly()
            started.set(false)
            false
        }
    }

    /**
     * Frame-present hook compatible with [com.winlator.cmod.runtime.display.ui.FrameRating.FrameObserver].
     * Tracks last nanoTime internally so the caller only has to forward the wall-clock present time.
     */
    fun recordFramePresent(nanoTime: Long) {
        if (!started.get() || stopped.get()) return
        frameCounter.increment()
        val prev = lastFrameNanoForObserver.getAndSet(nanoTime)
        if (prev != 0L) {
            val dt = nanoTime - prev
            if (dt in 1L..MAX_PLAUSIBLE_FRAMETIME_NS) {
                synchronized(frametimesLock) { frametimesNs.add(dt) }
            }
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        try {
            sampler?.shutdown()
            try {
                sampler?.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            // Final row capturing whatever is left in the window.
            tickSafe()
            try {
                writer?.flush()
                writerStream?.fd?.sync()
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "Final flush failed")
            }
            val endedIso = ISO_FORMAT.format(Date(System.currentTimeMillis()))
            writeSidecar(endedAtIso = endedIso, clean = true)
        } finally {
            closeWriterQuietly()
            sampler = null
        }
    }

    private fun tickSafe() {
        try {
            tick()
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Tick failed")
        }
    }

    private fun tick() {
        val w = writer ?: return
        val nowWall = System.currentTimeMillis()
        val elapsedMs = SystemClock.elapsedRealtime() - sessionStartElapsedRealtimeMs
        val nowCpu = Debug.threadCpuTimeNanos()
        val nowWallNs = System.nanoTime()
        val cpuDelta = nowCpu - lastCpuTimeNs
        val wallDelta = nowWallNs - lastWallNs
        lastCpuTimeNs = nowCpu
        lastWallNs = nowWallNs
        val cpuPct = if (wallDelta > 0L) (cpuDelta.toDouble() / wallDelta.toDouble() * 100.0).coerceIn(0.0, 100.0) else 0.0

        val framesNow = frameCounter.sum()
        val frameDelta = framesNow - lastFrameCount
        lastFrameCount = framesNow
        val fpsAvg = if (wallDelta > 0L) frameDelta.toFloat() / (wallDelta / 1_000_000_000f) else 0f

        val (medianFt, p99Ft) = drainAndComputeFrametimes()
        val gpuPct = readGpuLoadPct()
        val socTempC = readSocTempC()
        val thermalStatus = readThermalStatus()
        val battPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val battCurrentMa = readBatteryCurrentMa()
        val battVoltageMv = readBatteryVoltageMv()
        val pssKb = readPssKb()
        val availMemMb = readAvailMemMb()

        val row = buildString(256) {
            append(ISO_FORMAT.format(Date(nowWall)))
            append(',').append(elapsedMs)
            append(',').append(formatFloat(fpsAvg))
            append(',').append(frameDelta)
            append(',').append(formatFloat(medianFt))
            append(',').append(formatFloat(p99Ft))
            append(',').append(formatDouble(cpuPct))
            append(',').append(if (gpuPct < 0f) "NA" else formatFloat(gpuPct))
            append(',').append(thermalStatus)
            append(',').append(if (socTempC <= 0f) "NA" else formatFloat(socTempC))
            append(',').append(battPct)
            append(',').append(battCurrentMa)
            append(',').append(battVoltageMv)
            append(',').append(pssKb)
            append(',').append(availMemMb)
        }
        w.write(row)
        w.newLine()
    }

    private fun drainAndComputeFrametimes(): Pair<Float, Float> {
        val drained = synchronized(frametimesLock) {
            if (frametimesNs.isEmpty()) {
                LongArray(0)
            } else {
                val arr = frametimesNs.toLongArray()
                frametimesNs.clear()
                arr
            }
        }
        if (drained.isEmpty()) return 0f to 0f
        drained.sort()
        val median = drained[drained.size / 2] / 1_000_000f
        val p99 = drained[((drained.size - 1) * 99) / 100] / 1_000_000f
        return median to p99
    }

    private fun flushSafe() {
        try {
            writer?.flush()
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Periodic flush failed")
        }
    }

    private fun closeWriterQuietly() {
        try { writer?.close() } catch (_: Throwable) {}
        writer = null
        writerStream = null
    }

    private fun writeSidecar(endedAtIso: String?, clean: Boolean) {
        val sidecar = sidecarFile ?: return
        try {
            val json = JSONObject().apply {
                put("schemaVersion", 1)
                put("appVersion", sessionMetadata.appVersion)
                put("session", JSONObject().apply {
                    put("startedAt", ISO_FORMAT.format(Date(sessionStartWallMs)))
                    put("endedAt", endedAtIso ?: JSONObject.NULL)
                    put("clean", clean)
                })
                put("game", JSONObject().apply {
                    put("name", sessionMetadata.gameName)
                    put("slug", sanitizeGameSlug(sessionMetadata.gameName))
                    put("source", sessionMetadata.gameSource)
                    put("id", sessionMetadata.gameId)
                    put("exePath", sessionMetadata.exePath ?: JSONObject.NULL)
                })
                put("device", JSONObject().apply {
                    put("androidId", sessionMetadata.androidId)
                    put("model", Build.MODEL ?: "")
                    put("manufacturer", Build.MANUFACTURER ?: "")
                    put("hardware", Build.HARDWARE ?: "")
                    put("board", Build.BOARD ?: "")
                    put("socModel", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "")
                    put("socManufacturer", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "")
                    put("gpuRenderer", sessionMetadata.gpuRenderer)
                    put("androidApi", Build.VERSION.SDK_INT)
                    put("androidRelease", Build.VERSION.RELEASE ?: "")
                })
                put("settings", JSONObject().apply {
                    put("containerFingerprint", sessionMetadata.containerFingerprint)
                    put("effective", JSONObject(sessionMetadata.effectiveSettings))
                })
                put("schemaNotes", "CSV columns: $CSV_HEADER. Times are UTC ISO-8601.")
            }
            sidecar.writeText(json.toString(2), StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Sidecar write failed")
        }
    }

    private fun readGpuLoadPct(): Float =
        runCatching {
            File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").takeIf { it.canRead() }
                ?.readText()?.trim()?.removeSuffix("%")?.toFloatOrNull() ?: -1f
        }.getOrDefault(-1f)

    private fun readSocTempC(): Float = runCatching {
        val zonesDir = File("/sys/class/thermal").takeIf { it.isDirectory } ?: return@runCatching 0f
        var maxTemp = 0f
        zonesDir.listFiles { f -> f.name.startsWith("thermal_zone") }?.forEach { zone ->
            val typeFile = File(zone, "type")
            val tempFile = File(zone, "temp")
            if (typeFile.canRead() && tempFile.canRead()) {
                val type = runCatching { typeFile.readText().trim().lowercase() }.getOrNull() ?: return@forEach
                if (type.contains("cpu") || type.contains("soc") || type.contains("tsens")) {
                    val rawMilli = tempFile.readText().trim().toFloatOrNull() ?: return@forEach
                    val celsius = rawMilli / 1000f
                    if (celsius in 1f..150f && celsius > maxTemp) maxTemp = celsius
                }
            }
        }
        maxTemp
    }.getOrDefault(0f)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun thermalStatusName(): String = when (powerManager?.currentThermalStatus ?: 0) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "NONE"
    }

    private fun readThermalStatus(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thermalStatusName() else "NA"

    private fun readBatteryCurrentMa(): Int {
        val bm = batteryManager ?: return 0
        val ua = runCatching { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
            .getOrDefault(0L)
        return (kotlin.math.abs(ua) / 1000L).toInt()
    }

    private fun readBatteryVoltageMv(): Int {
        val intent = runCatching {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return 0
        return intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
    }

    private fun readPssKb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss
    }

    private fun readAvailMemMb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (mi.availMem / (1024L * 1024L)).toInt()
    }

    private fun formatFloat(f: Float): String =
        if (f.isNaN() || f.isInfinite()) "0" else "%.2f".format(Locale.ROOT, f)

    private fun formatDouble(d: Double): String =
        if (d.isNaN() || d.isInfinite()) "0" else "%.2f".format(Locale.ROOT, d)

    private fun sanitizeGameSlug(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "unknown"
        return trimmed
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(64)
            .ifEmpty { "unknown" }
    }

    data class SessionMetadata(
        val gameName: String,
        val gameSource: String,
        val gameId: String,
        val exePath: String?,
        val androidId: String,
        val gpuRenderer: String,
        val containerFingerprint: String,
        val effectiveSettings: Map<String, String>,
        val appVersion: String,
    )

    companion object {
        private const val TAG = "PerfRecorder"
        private const val SAMPLE_INTERVAL_MS: Long = 1_000L
        private const val FLUSH_INTERVAL_MS: Long = 5_000L
        private const val SHUTDOWN_TIMEOUT_MS: Long = 1_500L
        private const val INITIAL_FRAMETIMES_CAPACITY: Int = 4_096
        private const val MAX_PLAUSIBLE_FRAMETIME_NS: Long = 1_000_000_000L
        private const val CSV_HEADER: String =
            "t_iso8601,elapsed_ms,fps_avg,frame_count,frametime_median_ms,frametime_p99_ms," +
                "cpu_self_pct,gpu_busy_pct,thermal_status,soc_temp_c,battery_pct,battery_current_ma," +
                "battery_voltage_mv,pss_kb,avail_mem_mb"
        private val ISO_FORMAT: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        private val TIMESTAMP_FORMAT: SimpleDateFormat =
            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
    }
}
