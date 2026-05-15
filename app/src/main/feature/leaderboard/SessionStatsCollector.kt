package com.winlator.cmod.feature.leaderboard

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Per-session accumulator that records the metrics needed to produce a [PerfDigest] at
 * the end of a game session. Runs a single 1 Hz sampler on its own thread; the rest of
 * the work happens lock-free (LongAdder for frame counts, synchronized list for the
 * frametime stream).
 *
 * Lifecycle: [start] when the game session begins, [recordFrame] from the X server
 * render thread on every present, [stop] when the session ends. After [stop] the
 * accumulator can be queried via [finalizeDigest].
 *
 * This collector deliberately does its own sysfs / system-service polling instead of
 * piggy-backing on the perf HUD. That way recording works even when the HUD is hidden
 * (the user might want a clean screen but still want their session logged). Cost: an
 * extra few sysfs reads per second, which is negligible.
 */
class SessionStatsCollector(
    private val context: Context,
    private val gameSource: String,
    private val gameId: String,
    private val containerConfigFingerprint: String,
    private val gpuRendererString: String,
    private val socModelString: String,
    private val androidId: String,
    private val flagsAtStart: Int,
) {
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val frameCounter = LongAdder()
    // Primitive ring buffer of frametimes for p50/p99 — bounded so 4-hour sessions
    // don't allocate megabytes of boxed Longs. When the session is longer than the
    // buffer (capacity / typical_fps seconds), we keep the most-recent window. That's
    // a reasonable trade for Tier 0; Tier 1 will use a server-side stat with the full
    // CSV.
    private val frametimesBuffer = LongArray(FRAMETIMES_CAPACITY)
    private val frametimesWriteIndex = java.util.concurrent.atomic.AtomicLong(0)

    @Volatile private var sessionStartElapsedRealtimeMs: Long = 0
    @Volatile private var sessionStopElapsedRealtimeMs: Long = 0

    private val batteryPctAtStart = AtomicLong(-1)
    private val batteryPctLatest = AtomicLong(-1)
    private val batteryCurrentSumUa = AtomicLong(0)
    private val batterySampleCount = AtomicLong(0)

    private val cpuPctRunningSum = AtomicLong(0)
    private val cpuPctSampleCount = AtomicLong(0)
    private val gpuPctRunningSum = AtomicLong(0)
    private val gpuPctSampleCount = AtomicLong(0)

    private val socTempCx10Running = AtomicLong(0)
    private val socTempSamples = AtomicLong(0)
    private val socTempPeakCx10 = AtomicLong(0)

    private val thermalThrottleTicks = AtomicLong(0)
    private val flags = AtomicLong(flagsAtStart.toLong())

    private var sampler: ScheduledExecutorService? = null
    private val sampleSource: SampleSource = SampleSource(context)
    private var lastCpuTimeNs: Long = 0
    private var lastWallNs: Long = 0

    /** Begin sampling. Idempotent. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        sessionStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        batteryPctAtStart.set(sampleSource.readBatteryPct().toLong())
        batteryPctLatest.set(batteryPctAtStart.get())
        lastCpuTimeNs = sampleSource.readSelfCpuTimeNs()
        lastWallNs = System.nanoTime()
        val exec = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "PerfSessionStats").apply {
                priority = Thread.MIN_PRIORITY + 1
                isDaemon = true
            }
        }
        sampler = exec
        exec.scheduleAtFixedRate({ tick() }, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Called from the X server render thread on every present. Must be cheap — just two
     * atomic ops and an arraylist add under a tiny synchronized block.
     */
    fun recordFrame(frametimeNs: Long) {
        if (!started.get() || stopped.get()) return
        frameCounter.increment()
        if (frametimeNs > 0L && frametimeNs < MAX_PLAUSIBLE_FRAMETIME_NS) {
            val pos = frametimesWriteIndex.getAndIncrement()
            frametimesBuffer[(pos and FRAMETIMES_MASK).toInt()] = frametimeNs
        }
    }

    /**
     * Convenience wrapper used by [FrameRating.FrameObserver]: compute the frametime
     * delta from the last present and forward to [recordFrame]. Internally tracks the
     * previous nanoTime; first call seeds the baseline and emits no frametime.
     */
    fun onFramePresent(nanoTime: Long) {
        if (!started.get() || stopped.get()) return
        val prev = lastFrameNanoForObserver
        lastFrameNanoForObserver = nanoTime
        if (prev == 0L) {
            frameCounter.increment()
        } else {
            recordFrame(nanoTime - prev)
        }
    }

    @Volatile private var lastFrameNanoForObserver: Long = 0

    fun raiseFlag(bit: Int) {
        flags.updateAndGet { it or bit.toLong() }
    }

    /** Stops sampling and freezes final state. Idempotent. */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        sessionStopElapsedRealtimeMs = SystemClock.elapsedRealtime()
        batteryPctLatest.set(sampleSource.readBatteryPct().toLong())
        sampler?.shutdown()
        try {
            sampler?.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        sampler = null
    }

    /** Builds the final [PerfDigest]. Safe to call after [stop]. */
    fun finalizeDigest(): PerfDigest {
        val durationMs = sessionStopElapsedRealtimeMs - sessionStartElapsedRealtimeMs
        val durationSeconds = (durationMs / 1000L).coerceAtLeast(1L)
        val frames = frameCounter.sum()
        val avgFps = if (durationSeconds > 0) frames.toFloat() / durationSeconds.toFloat() else 0f

        val totalWritten = frametimesWriteIndex.get()
        val (medianFt, p99Ft) = if (totalWritten <= 0) {
            0f to 0f
        } else {
            val n = minOf(totalWritten, FRAMETIMES_CAPACITY.toLong()).toInt()
            val snapshot = LongArray(n)
            System.arraycopy(frametimesBuffer, 0, snapshot, 0, n)
            snapshot.sort()
            val medianNs = snapshot[n / 2]
            val p99Ns = snapshot[((n - 1) * 99) / 100]
            (medianNs / 1_000_000f) to (p99Ns / 1_000_000f)
        }
        val p1Low = if (p99Ft > 0f) 1000f / p99Ft else 0f
        val avgFt = if (avgFps > 0f) 1000f / avgFps else 0f

        val avgCpu = avgFromAtomic(cpuPctRunningSum, cpuPctSampleCount)
        val avgGpu = avgFromAtomic(gpuPctRunningSum, gpuPctSampleCount)
        val avgTemp = if (socTempSamples.get() > 0) socTempCx10Running.get().toFloat() / socTempSamples.get() / 10f else 0f
        val peakTemp = socTempPeakCx10.get().toFloat() / 10f
        val avgBatteryCurrent = if (batterySampleCount.get() > 0) (batteryCurrentSumUa.get() / batterySampleCount.get()).toInt() else 0
        val avgBatteryCurrentMa = (avgBatteryCurrent / 1000).coerceIn(0, 65535)
        val battStart = batteryPctAtStart.get()
        val battEnd = batteryPctLatest.get()
        val battDrop = if (battStart > 0 && battEnd in 0..battStart) (battStart - battEnd).toInt() else 0

        val finalFlags = flags.get().toInt() or
            if (thermalThrottleTicks.get() > 0) PerfDigest.FLAG_THERMAL_THROTTLED else 0

        return PerfDigest(
            schemaVersion = PerfDigest.SCHEMA_VERSION,
            flags = finalFlags,
            sessionDurationSeconds = durationSeconds,
            avgFps = avgFps,
            p1LowFps = p1Low,
            avgFrametimeMs = avgFt,
            p99FrametimeMs = p99Ft,
            medianFrametimeMs = medianFt,
            avgCpuPct = avgCpu.toInt().coerceIn(0, 100),
            avgGpuPct = avgGpu.toInt().coerceIn(0, 100),
            peakSocTempC = peakTemp,
            avgSocTempC = avgTemp,
            batteryDropPct = battDrop.coerceIn(0, 255),
            avgBatteryCurrentMa = avgBatteryCurrentMa,
            thermalThrottleSeconds = thermalThrottleTicks.get().toInt().coerceIn(0, 65535),
            gameIdHash = PerfDigest.gameIdHashOf(gameSource, gameId),
            gpuModelHash = HardwareDictionary.gpuHashOf(gpuRendererString),
            socModelHash = HardwareDictionary.socHashOf(socModelString),
            containerConfigHash = PerfDigest.shortHash(containerConfigFingerprint),
            androidIdShortHash = PerfDigest.shortHash(androidId),
        )
    }

    /** Per-second sampler. Runs on PerfSessionStats thread. */
    private fun tick() {
        try {
            val nowCpu = sampleSource.readSelfCpuTimeNs()
            val nowWall = System.nanoTime()
            val cpuDelta = nowCpu - lastCpuTimeNs
            val wallDelta = nowWall - lastWallNs
            lastCpuTimeNs = nowCpu
            lastWallNs = nowWall
            if (wallDelta > 0L) {
                val cpuPct = (cpuDelta.toDouble() / wallDelta.toDouble() * 100.0).coerceIn(0.0, 100.0)
                cpuPctRunningSum.addAndGet((cpuPct * 100).toLong())
                cpuPctSampleCount.incrementAndGet()
            }

            val gpuPct = sampleSource.readGpuLoadPct()
            if (gpuPct >= 0f) {
                gpuPctRunningSum.addAndGet((gpuPct * 100).toLong())
                gpuPctSampleCount.incrementAndGet()
            }

            val socTempC = sampleSource.readSocTempC()
            if (socTempC > 0f) {
                val x10 = (socTempC * 10f).toLong()
                socTempCx10Running.addAndGet(x10)
                socTempSamples.incrementAndGet()
                while (true) {
                    val prev = socTempPeakCx10.get()
                    if (x10 <= prev || socTempPeakCx10.compareAndSet(prev, x10)) break
                }
            }

            val battCurrentUa = sampleSource.readBatteryCurrentUa()
            if (battCurrentUa != Long.MIN_VALUE) {
                batteryCurrentSumUa.addAndGet(kotlin.math.abs(battCurrentUa))
                batterySampleCount.incrementAndGet()
            }
            batteryPctLatest.set(sampleSource.readBatteryPct().toLong())

            if (sampleSource.isThrottling()) thermalThrottleTicks.incrementAndGet()
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Stats tick failed")
        }
    }

    private fun avgFromAtomic(sumX100: AtomicLong, count: AtomicLong): Float {
        val c = count.get()
        return if (c == 0L) 0f else sumX100.get().toFloat() / c / 100f
    }

    companion object {
        private const val TAG = "PerfSessionStats"
        private const val SAMPLE_INTERVAL_MS: Long = 1_000L
        private const val SHUTDOWN_TIMEOUT_MS: Long = 1_000L
        // Power of 2 — required for ring buffer mask math.
        // 65,536 entries × 8 bytes/long = 512 KB. At 60 FPS that's ~18 minutes of
        // history, beyond which the oldest entries are overwritten. Long enough for
        // representative p50/p99 quantiles on any realistic session.
        private const val FRAMETIMES_CAPACITY: Int = 65_536
        private const val FRAMETIMES_MASK: Long = (FRAMETIMES_CAPACITY - 1).toLong()
        private const val MAX_PLAUSIBLE_FRAMETIME_NS: Long = 1_000_000_000L // 1 second
    }

    /**
     * Polls the system for raw metric values. Centralized here so sysfs + Android API
     * access lives in one place — easier to add fallbacks or mock for testing later.
     */
    private class SampleSource(private val context: Context) {
        private val batteryManager: BatteryManager? =
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        private val powerManager: PowerManager? =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        fun readSelfCpuTimeNs(): Long = android.os.Debug.threadCpuTimeNanos()

        /**
         * Returns GPU busy percentage from the Adreno KGSL sysfs node, or -1 if unreadable
         * (SELinux denial on some OEMs, Mali GPU, etc.). Caller decides whether to log it.
         */
        fun readGpuLoadPct(): Float {
            return runCatching {
                java.io.File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").takeIf { it.canRead() }
                    ?.readText()
                    ?.trim()
                    ?.removeSuffix("%")
                    ?.toFloatOrNull()
                    ?: -1f
            }.getOrDefault(-1f)
        }

        /**
         * Reads the highest active CPU/SoC thermal zone temperature in Celsius from sysfs.
         * Returns 0 if no zone is readable. This is best-effort — sysfs thermal access is
         * OEM-dependent and may be restricted on hardened devices.
         */
        fun readSocTempC(): Float {
            return runCatching {
                val zonesDir = java.io.File("/sys/class/thermal").takeIf { it.isDirectory } ?: return@runCatching 0f
                var maxTemp = 0f
                zonesDir.listFiles { f -> f.name.startsWith("thermal_zone") }?.forEach { zone ->
                    val typeFile = java.io.File(zone, "type")
                    val tempFile = java.io.File(zone, "temp")
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
        }

        fun readBatteryPct(): Int =
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        /** Microamps (negative = discharging). [Long.MIN_VALUE] means unavailable. */
        fun readBatteryCurrentUa(): Long {
            val bm = batteryManager ?: return Long.MIN_VALUE
            return runCatching {
                bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            }.getOrDefault(Long.MIN_VALUE)
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun thermalStatus(): Int = powerManager?.currentThermalStatus ?: 0

        /**
         * True when the platform reports any thermal mitigation in effect. API 29+ uses
         * the official thermal status API; older devices return false (we don't pretend
         * to detect throttling without it).
         */
        fun isThrottling(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            return thermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE
        }
    }
}
