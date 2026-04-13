package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.enums.DownloadPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class DownloadFailedException(
    message: String,
) : CancellationException(message)

class DownloadInfo(
    val jobCount: Int = 1,
    val gameId: Int,
    var downloadingAppIds: CopyOnWriteArrayList<Int>,
) {
    @Volatile var isDeleting: Boolean = false

    @Volatile var isCancelling: Boolean = false
    private var downloadJob: Job? = null
    private val downloadProgressListeners = CopyOnWriteArrayList<((Float) -> Unit)>()
    private val progresses: Array<Float> = Array(jobCount) { 0f }

    private val weights = FloatArray(jobCount) { 1f }
    private var weightSum = jobCount.toFloat()

    private var totalExpectedBytes = AtomicLong(0L)
    private var bytesDownloaded = AtomicLong(0L)

    @Volatile private var persistencePath: String? = null
    private val lastPersistTimestampMs = AtomicLong(0L)
    private val hasDirtyProgressSnapshot = AtomicBoolean(false)
    private val isPersistEnqueued = AtomicBoolean(false)
    private val snapshotWriteGeneration = AtomicLong(0L)

    private data class SpeedSample(
        val timeMs: Long,
        val bytes: Long,
    )

    private val speedSamples = ArrayDeque<SpeedSample>()

    @Volatile private var lastSpeedSampleMs = 0L

    @Volatile private var etaEmaSpeedBytesPerSec: Double = 0.0

    @Volatile private var hasEtaEmaSpeed: Boolean = false

    @Volatile private var isActive: Boolean = true
    private val status = MutableStateFlow(DownloadPhase.UNKNOWN)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val currentFileName = MutableStateFlow<String?>(null)
    private var retryCount: Int = 0
    private var maxRetries: Int = 3
    private var hasError: Boolean = false
    private var errorMessage: String = ""

    private val emitLock = Any()

    @Volatile private var lastProgressEmitTimeMs = 0L

    @Volatile private var lastEmittedProgress = -1f

    val depotCumulativeUncompressedBytes = java.util.concurrent.ConcurrentHashMap<Int, AtomicLong>()

    fun cancel() {
        cancel("Cancelled by user")
    }

    fun failedToDownload() {
        persistProgressSnapshot(force = true)
        status.value = DownloadPhase.FAILED
        setActive(false)
        downloadJob?.cancel(DownloadFailedException("Failed to download"))
    }

    fun cancel(message: String) {
        persistProgressSnapshot(force = true)
        setActive(false)
        downloadJob?.cancel(CancellationException(message))
    }

    fun setDownloadJob(job: Job) {
        downloadJob = job
    }

    suspend fun awaitCompletion(timeoutMs: Long = 5000L) {
        withTimeoutOrNull(timeoutMs) { downloadJob?.join() }
    }

    fun getProgress(): Float {
        val total = totalExpectedBytes.get()
        if (total > 0L) {
            val bytesProgress = (bytesDownloaded.get().toFloat() / total.toFloat()).coerceIn(0f, 1f)
            return bytesProgress
        }

        var totalProgress = 0f
        for (i in progresses.indices) {
            totalProgress += progresses[i] * weights[i]
        }
        return if (weightSum == 0f) 0f else totalProgress / weightSum
    }

    fun setProgress(
        amount: Float,
        jobIndex: Int = 0,
    ) {
        progresses[jobIndex] = amount
        emitProgressChange()
    }

    fun setWeight(
        jobIndex: Int,
        weightBytes: Long,
    ) {
        weights[jobIndex] = weightBytes.toFloat()
        weightSum = weights.sum()
    }

    fun setTotalExpectedBytes(bytes: Long) {
        totalExpectedBytes.set(if (bytes < 0L) 0L else bytes)
    }

    fun initializeBytesDownloaded(value: Long) {
        bytesDownloaded.set(if (value < 0L) 0L else value)
    }

    fun setPersistencePath(appDirPath: String?) {
        if (persistencePath != appDirPath) {
            lastPersistTimestampMs.set(0L)
            hasDirtyProgressSnapshot.set(false)
            persistencePath = appDirPath
            snapshotWriteGeneration.incrementAndGet()
        }
    }

    fun persistProgressSnapshot(force: Boolean = false) {
        val appDirPath = persistencePath ?: return
        val nowMs = System.currentTimeMillis()

        if (force) {
            val expectedGeneration = snapshotWriteGeneration.get()
            try {
                val persisted =
                    persistDepotBytesInternal(
                        appDirPath = appDirPath,
                        depotBytes = depotCumulativeUncompressedBytes,
                        expectedGeneration = expectedGeneration,
                    )
                if (persisted) {
                    lastPersistTimestampMs.set(nowMs)
                    hasDirtyProgressSnapshot.set(false)
                }
            } catch (e: Exception) {
                hasDirtyProgressSnapshot.set(true)
            }
            return
        }

        if (!hasDirtyProgressSnapshot.get()) {
            return
        }

        if (nowMs - lastPersistTimestampMs.get() < PROGRESS_SNAPSHOT_MIN_INTERVAL_MS) {
            return
        }

        val expectedGeneration = snapshotWriteGeneration.get()
        if (isPersistEnqueued.compareAndSet(false, true)) {
            SNAPSHOT_PERSIST_EXECUTOR.execute {
                try {
                    if (!hasDirtyProgressSnapshot.getAndSet(false)) return@execute
                    val persisted =
                        persistDepotBytesInternal(
                            appDirPath = appDirPath,
                            depotBytes = depotCumulativeUncompressedBytes,
                            expectedGeneration = expectedGeneration,
                        )
                    if (persisted) {
                        lastPersistTimestampMs.set(System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    hasDirtyProgressSnapshot.set(true)
                } finally {
                    isPersistEnqueued.set(false)
                }
            }
        }
    }

    fun markProgressSnapshotDirty() {
        hasDirtyProgressSnapshot.set(true)
        persistProgressSnapshot()
    }

    fun updateBytesDownloaded(
        deltaBytes: Long,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        if (!isActive) return
        if (deltaBytes <= 0L) return

        val currentBytes = bytesDownloaded.addAndGet(deltaBytes)
        if (currentBytes < 0L) {
            bytesDownloaded.set(0L)
        }
        if (timestampMs - lastSpeedSampleMs >= SPEED_SAMPLE_INTERVAL_MS) {
            lastSpeedSampleMs = timestampMs
            addSpeedSample(timestampMs, currentBytes.coerceAtLeast(0L))
        }
    }

    fun updateStatusMessage(message: String?) {
        statusMessage.value = message
    }

    fun updateStatus(
        status: DownloadPhase,
        message: String? = null,
    ) {
        val previousStatus = this.status.value
        if (previousStatus == status && message == null) return

        this.status.value = status

        if (status == DownloadPhase.DOWNLOADING &&
            previousStatus != DownloadPhase.DOWNLOADING &&
            previousStatus != DownloadPhase.UNKNOWN
        ) {
            resetSpeedTracking()
        }

        if (message != null) {
            statusMessage.value = message
        } else {
            statusMessage.value = null
        }
    }

    fun getStatusFlow(): StateFlow<DownloadPhase> = status

    fun getStatusMessageFlow(): StateFlow<String?> = statusMessage

    fun getCurrentFileNameFlow(): StateFlow<String?> = currentFileName

    fun updateCurrentFileName(name: String?) {
        currentFileName.value = name
    }

    private fun addSpeedSample(
        timestampMs: Long,
        currentBytes: Long,
    ) {
        synchronized(speedSamples) {
            speedSamples.add(SpeedSample(timestampMs, currentBytes))
            trimOldSamples(timestampMs, SPEED_SAMPLE_RETENTION_MS)
        }
    }

    private fun trimOldSamples(
        nowMs: Long,
        windowMs: Long,
    ) {
        val cutoff = nowMs - windowMs
        while (speedSamples.isNotEmpty() && speedSamples.first().timeMs < cutoff) {
            speedSamples.removeFirst()
        }
    }

    private fun getLastSampleAgeMs(nowMs: Long = System.currentTimeMillis()): Long? {
        synchronized(speedSamples) {
            if (speedSamples.isEmpty()) return null
            return (nowMs - speedSamples.last().timeMs).coerceAtLeast(0L)
        }
    }

    fun resetSpeedTracking() {
        synchronized(speedSamples) {
            speedSamples.clear()
        }
        lastSpeedSampleMs = 0L
        etaEmaSpeedBytesPerSec = 0.0
        hasEtaEmaSpeed = false
    }

    fun setActive(active: Boolean) {
        isActive = active
        if (!active) {
            resetSpeedTracking()
        }
    }

    fun isActive(): Boolean = isActive

    fun getRetryCount(): Int = retryCount

    fun hasError(): Boolean = hasError

    fun getErrorMessage(): String = errorMessage

    fun markError(message: String) {
        hasError = true
        errorMessage = message
        setActive(false)
    }

    fun clearError() {
        hasError = false
        errorMessage = ""
        retryCount = 0
    }

    fun getTotalExpectedBytes(): Long = totalExpectedBytes.get()

    fun getBytesDownloaded(): Long = bytesDownloaded.get()

    fun getBytesProgress(): Pair<Long, Long> {
        val total = totalExpectedBytes.get()
        val downloaded = bytesDownloaded.get()
        return if (total > 0L) {
            downloaded.coerceAtMost(total) to total
        } else {
            0L to 0L
        }
    }

    private fun getSpeedOverWindow(windowMs: Long): Double? {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs

        val first: SpeedSample
        val last: SpeedSample

        synchronized(speedSamples) {
            if (speedSamples.size < 2) return null
            last = speedSamples.last()

            var foundFirst = last
            val iterator = speedSamples.descendingIterator()
            while (iterator.hasNext()) {
                val sample = iterator.next()
                if (sample.timeMs < cutoff) {
                    break
                }
                foundFirst = sample
            }
            first = foundFirst
        }

        val elapsedMs = last.timeMs - first.timeMs
        if (elapsedMs <= 500L) return null

        val bytesDelta = last.bytes - first.bytes
        if (bytesDelta <= 0L) return 0.0

        return bytesDelta.toDouble() / (elapsedMs.toDouble() / 1000.0)
    }

    fun getCurrentDownloadSpeed(): Long? {
        if (!isActive) return null
        val speed = getSpeedOverWindow(CURRENT_SPEED_WINDOW_MS) ?: return null
        return speed.toLong()
    }

    fun getEstimatedTimeRemaining(): Long? {
        if (!isActive) return null
        val currentStatus = status.value
        if (currentStatus != DownloadPhase.UNKNOWN && currentStatus != DownloadPhase.DOWNLOADING) {
            return null
        }
        val total = totalExpectedBytes.get()
        val downloaded = bytesDownloaded.get()
        if (total <= 0L) return null
        if (downloaded >= total) return null

        val nowMs = System.currentTimeMillis()
        val rawSpeedBytesPerSec = getSpeedOverWindow(ETA_SPEED_WINDOW_MS)

        val speedBytesPerSec =
            when {
                rawSpeedBytesPerSec != null && rawSpeedBytesPerSec > 0.0 -> {
                    if (!hasEtaEmaSpeed || etaEmaSpeedBytesPerSec <= 0.0) {
                        hasEtaEmaSpeed = true
                        etaEmaSpeedBytesPerSec = rawSpeedBytesPerSec
                        rawSpeedBytesPerSec
                    } else {
                        val alpha = 0.2
                        etaEmaSpeedBytesPerSec =
                            alpha * rawSpeedBytesPerSec + (1.0 - alpha) * etaEmaSpeedBytesPerSec
                        etaEmaSpeedBytesPerSec
                    }
                }

                rawSpeedBytesPerSec == 0.0 -> {
                    return null
                }

                hasEtaEmaSpeed && etaEmaSpeedBytesPerSec > 0.0 -> {
                    val lastSampleAgeMs = getLastSampleAgeMs(nowMs) ?: return null
                    if (lastSampleAgeMs > ETA_SAMPLE_STALE_TIMEOUT_MS) return null
                    etaEmaSpeedBytesPerSec
                }

                else -> {
                    return null
                }
            }
        if (speedBytesPerSec <= 0.0) return null

        val remainingBytes = total - downloaded
        if (remainingBytes <= 0L) return null

        val etaSeconds = remainingBytes / speedBytesPerSec
        if (etaSeconds.isNaN() || etaSeconds.isInfinite() || etaSeconds <= 0.0) return null

        return (etaSeconds * 1000.0).toLong()
    }

    fun addProgressListener(listener: (Float) -> Unit) {
        downloadProgressListeners.add(listener)
    }

    fun removeProgressListener(listener: (Float) -> Unit) {
        downloadProgressListeners.remove(listener)
    }

    fun emitProgressChange() {
        val now = System.currentTimeMillis()
        if (now - lastProgressEmitTimeMs < 100L) return

        val currentProgress = getProgress()
        var shouldEmit = false
        synchronized(emitLock) {
            if (currentProgress >= 1f || currentProgress <= 0f ||
                (now - lastProgressEmitTimeMs >= 100L && abs(currentProgress - lastEmittedProgress) >= 0.001f)
            ) {
                lastProgressEmitTimeMs = now
                lastEmittedProgress = currentProgress
                shouldEmit = true
            }
        }

        if (shouldEmit) {
            for (listener in downloadProgressListeners) {
                listener(currentProgress)
            }
        }
    }

    companion object {
        private const val SPEED_SAMPLE_RETENTION_MS = 120_000L
        private const val SPEED_SAMPLE_INTERVAL_MS = 250L
        private const val CURRENT_SPEED_WINDOW_MS = 5_000L
        private const val ETA_SPEED_WINDOW_MS = 60_000L
        private const val ETA_SAMPLE_STALE_TIMEOUT_MS = 120_000L
        private const val PERSISTENCE_DIR = ".DownloadInfo"
        private const val PERSISTENCE_FILE = "depot_bytes.json"
        private const val PROGRESS_SNAPSHOT_MIN_INTERVAL_MS = 1_000L
        private val PERSISTENCE_IO_LOCK = Any()
        private val SNAPSHOT_PERSIST_EXECUTOR: ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "DownloadInfoSnapshotWriter").apply {
                    isDaemon = true
                }
            }

        fun loadPersistedDepotBytes(appDirPath: String): Map<Int, Long> {
            return try {
                val file = File(File(appDirPath, PERSISTENCE_DIR), PERSISTENCE_FILE)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    if (content.isEmpty()) return emptyMap()
                    val json = org.json.JSONObject(content)
                    val map = mutableMapOf<Int, Long>()
                    for (key in json.keys()) {
                        val depotId = key.toIntOrNull() ?: continue
                        map[depotId] = json.getLong(key).coerceAtLeast(0L)
                    }
                    map
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load persisted depot bytes from $appDirPath")
                emptyMap()
            }
        }

        private fun deletePersistedFiles(appDirPath: String) {
            synchronized(PERSISTENCE_IO_LOCK) {
                try {
                    val file = File(File(appDirPath, PERSISTENCE_DIR), PERSISTENCE_FILE)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clear persisted bytes downloaded from $appDirPath")
                }
            }
        }
    }

    fun clearPersistedBytesDownloaded(
        appDirPath: String,
        sync: Boolean = false,
    ) {
        lastPersistTimestampMs.set(0L)
        hasDirtyProgressSnapshot.set(false)
        snapshotWriteGeneration.incrementAndGet()
        if (sync) {
            deletePersistedFiles(appDirPath)
        } else {
            SNAPSHOT_PERSIST_EXECUTOR.execute {
                deletePersistedFiles(appDirPath)
            }
        }
    }

    private fun persistDepotBytesInternal(
        appDirPath: String,
        depotBytes: Map<Int, AtomicLong>,
        expectedGeneration: Long? = null,
    ): Boolean {
        synchronized(PERSISTENCE_IO_LOCK) {
            if (expectedGeneration != null && expectedGeneration != snapshotWriteGeneration.get()) {
                return false
            }
            val dir = File(appDirPath, PERSISTENCE_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, PERSISTENCE_FILE)

            val sb = java.lang.StringBuilder()
            sb.append('{')
            var first = true
            for ((depotId, atomicBytes) in depotBytes) {
                if (!first) sb.append(',')
                sb
                    .append('"')
                    .append(depotId)
                    .append("\":")
                    .append(atomicBytes.get().coerceAtLeast(0L))
                first = false
            }
            sb.append('}')
            val jsonText = sb.toString()

            val tempFile = File(dir, "$PERSISTENCE_FILE.tmp")
            tempFile.writeText(jsonText)
            if (!tempFile.renameTo(file)) {
                file.writeText(jsonText)
                tempFile.delete()
            }
        }
        return true
    }
}
