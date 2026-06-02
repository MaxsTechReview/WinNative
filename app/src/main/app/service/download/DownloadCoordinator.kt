package com.winlator.cmod.app.service.download

import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.app.db.PluviaDatabase
import com.winlator.cmod.app.db.download.DownloadRecord
import com.winlator.cmod.app.db.download.DownloadRecordDao
import com.winlator.cmod.feature.stores.steam.events.AndroidEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Coordinates persisted downloads across stores, allowing one active transfer at a time and
 * dispatching queued work to each store service.
 */
object DownloadCoordinator {
    private const val MAX_PARALLEL_DOWNLOADS = 1

    /** Per-store hook for actual download side effects. */
    interface Dispatcher {
        /** Start a download the coordinator just dequeued. */
        fun startQueued(record: DownloadRecord)

        /** Pause a running download while keeping partial files. */
        fun pauseRunning(record: DownloadRecord)

        /** Cancel a running download and delete partial files. */
        fun cancelRunning(record: DownloadRecord)
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dispatchers = mutableMapOf<String, Dispatcher>()

    @Volatile
    private var dao: DownloadRecordDao? = null

    @Volatile
    private var startupRestored = false

    private val recordsState = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: Flow<List<DownloadRecord>> = recordsState.asStateFlow()

    private val recordChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes = recordChanges.asSharedFlow()

    /** True only while a download is actively transferring. */
    fun hasActiveDownload(): Boolean =
        recordsState.value.any { it.status == DownloadRecord.STATUS_DOWNLOADING }

    fun init(database: PluviaDatabase) {
        if (dao != null) return
        dao = database.downloadRecordDao()
    }

    fun registerDispatcher(store: String, dispatcher: Dispatcher) {
        dispatchers[store] = dispatcher
        // Pick up queued records that were waiting for this dispatcher.
        if (dao != null) {
            scope.launch { tick() }
        }
    }

    fun unregisterDispatcher(store: String) {
        dispatchers.remove(store)
    }

    /** Result of [requestSlot]. */
    sealed class Decision {
        data class Start(val record: DownloadRecord) : Decision()

        data class Queue(val record: DownloadRecord) : Decision()
    }

    /** Persist a request and decide whether it starts now or waits in the queue. */
    suspend fun requestSlot(
        store: String,
        storeGameId: String,
        title: String = "",
        artUrl: String = "",
        installPath: String = "",
        selectedDlcs: String = "",
        language: String = "",
        taskType: String = DownloadRecord.TASK_INSTALL,
        bytesTotal: Long = 0L,
    ): Decision {
        val daoRef = dao ?: throw IllegalStateException("DownloadCoordinator not initialised")

        return mutex.withLock {
            val now = System.currentTimeMillis()
            val existing = daoRef.findByStoreGame(store, storeGameId)

            // tick() may dispatch into code that calls requestSlot again; keep the granted slot.
            if (existing != null && existing.status == DownloadRecord.STATUS_DOWNLOADING) {
                return@withLock Decision.Start(existing)
            }

            val activeCount = daoRef.countByStatus(DownloadRecord.STATUS_DOWNLOADING)

            val canStartNow = activeCount < MAX_PARALLEL_DOWNLOADS

            val (status, decisionFactory) =
                if (canStartNow) {
                    DownloadRecord.STATUS_DOWNLOADING to { r: DownloadRecord -> Decision.Start(r) }
                } else {
                    DownloadRecord.STATUS_QUEUED to { r: DownloadRecord -> Decision.Queue(r) }
                }

            val record =
                if (existing == null) {
                    val newRecord =
                        DownloadRecord(
                            store = store,
                            storeGameId = storeGameId,
                            title = title,
                            artUrl = artUrl,
                            installPath = installPath,
                            selectedDlcs = selectedDlcs,
                            language = language,
                            taskType = taskType,
                            bytesTotal = bytesTotal,
                            status = status,
                            createdAt = now,
                            updatedAt = now,
                        )
                    val id = daoRef.upsert(newRecord)
                    newRecord.copy(id = id)
                } else {
                    // Re-enqueue replaces request fields so empty DLC selection means base game only.
                    val updated =
                        existing.copy(
                            title = title,
                            artUrl = artUrl,
                            installPath = installPath,
                            selectedDlcs = selectedDlcs,
                            language = language,
                            taskType = taskType,
                            bytesTotal = if (bytesTotal > 0L) bytesTotal else existing.bytesTotal,
                            bytesDownloaded = 0L,
                            status = status,
                            errorMessage = null,
                            updatedAt = now,
                        )
                    daoRef.update(updated)
                    updated
                }

            refreshState(daoRef)
            decisionFactory(record)
        }
    }

    /** Update progress without locking the queue. */
    fun updateProgress(store: String, storeGameId: String, bytesDownloaded: Long, bytesTotal: Long) {
        val daoRef = dao ?: return
        scope.launch {
            val record = daoRef.findByStoreGame(store, storeGameId) ?: return@launch
            val safeTotal = bytesTotal.coerceAtLeast(0L)
            val safeDownloaded = bytesDownloaded.coerceAtLeast(0L).let { next ->
                if (safeTotal == record.bytesTotal && next < record.bytesDownloaded) {
                    record.bytesDownloaded
                } else {
                    next
                }
            }.let { next ->
                if (safeTotal > 0L) next.coerceAtMost(safeTotal) else next
            }
            daoRef.updateProgress(record.id, safeDownloaded, safeTotal)
            refreshState(daoRef)
        }
    }

    /** Persist a terminal status and start the next queued download. */
    suspend fun notifyFinished(
        store: String,
        storeGameId: String,
        finalStatus: String,
        error: String? = null,
    ) {
        val daoRef = dao ?: return
        mutex.withLock {
            val record = daoRef.findByStoreGame(store, storeGameId) ?: return@withLock
            daoRef.updateStatus(record.id, finalStatus, error)
            refreshState(daoRef)
        }
        // Drain after releasing the lock so dispatcher callbacks can re-enter.
        tick()
    }

    /** Mark a download PAUSED and ask its dispatcher to stop work. */
    suspend fun pause(store: String, storeGameId: String) {
        val daoRef = dao ?: return
        val record = daoRef.findByStoreGame(store, storeGameId) ?: return
        if (record.status == DownloadRecord.STATUS_COMPLETE ||
            record.status == DownloadRecord.STATUS_CANCELLED
        ) {
            return
        }
        mutex.withLock {
            daoRef.updateStatus(record.id, DownloadRecord.STATUS_PAUSED)
            refreshState(daoRef)
        }
        dispatchers[store]?.pauseRunning(record)
        tick()
    }

    suspend fun pauseAll() {
        val daoRef = dao ?: return
        val running = daoRef.findByStatus(DownloadRecord.STATUS_DOWNLOADING) +
            daoRef.findByStatus(DownloadRecord.STATUS_QUEUED)
        running.forEach { pause(it.store, it.storeGameId) }
    }

    /** Resume a paused, queued, or failed download. */
    suspend fun resume(store: String, storeGameId: String) {
        val daoRef = dao ?: return
        val record = daoRef.findByStoreGame(store, storeGameId) ?: return
        when (record.status) {
            DownloadRecord.STATUS_PAUSED,
            DownloadRecord.STATUS_QUEUED,
            DownloadRecord.STATUS_FAILED,
            -> {
                mutex.withLock {
                    daoRef.updateStatus(record.id, DownloadRecord.STATUS_QUEUED)
                    refreshState(daoRef)
                }
                tick()
            }
            else -> Unit
        }
    }

    suspend fun resumeAll() {
        val daoRef = dao ?: return
        // FAILED downloads keep enough state for Resume All to continue them.
        val toResume =
            daoRef.findByStatus(DownloadRecord.STATUS_PAUSED) +
                daoRef.findByStatus(DownloadRecord.STATUS_FAILED)
        toResume.forEach { resume(it.store, it.storeGameId) }
    }

    /** Cancel a download and ask its dispatcher to delete partial files. */
    suspend fun cancel(store: String, storeGameId: String) {
        val daoRef = dao ?: return
        val record = daoRef.findByStoreGame(store, storeGameId) ?: return
        mutex.withLock {
            daoRef.updateStatus(record.id, DownloadRecord.STATUS_CANCELLED)
            refreshState(daoRef)
        }
        dispatchers[store]?.cancelRunning(record)
        tick()
    }

    suspend fun cancelAll() {
        val daoRef = dao ?: return
        val cancellable =
            daoRef.findByStatus(DownloadRecord.STATUS_DOWNLOADING) +
                daoRef.findByStatus(DownloadRecord.STATUS_QUEUED) +
                daoRef.findByStatus(DownloadRecord.STATUS_PAUSED)
        cancellable.forEach { cancel(it.store, it.storeGameId) }
    }

    /** Remove finished records from the table. */
    suspend fun clear() {
        val daoRef = dao ?: return
        mutex.withLock {
            daoRef.deleteFinished()
            refreshState(daoRef)
        }
        PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(0, false))
    }

    /** Blocking variant for shutdown paths that may kill the process immediately after cleanup. */
    fun clearBlocking() {
        runBlocking { clear() }
    }

    /** Dispatch queued records while slots are available. */
    suspend fun tick() {
        val daoRef = dao ?: return
        val toStart = mutableListOf<DownloadRecord>()
        mutex.withLock {
            var activeCount = daoRef.countByStatus(DownloadRecord.STATUS_DOWNLOADING)
            val queued = daoRef.findByStatus(DownloadRecord.STATUS_QUEUED)
            val now = System.currentTimeMillis()

            for (record in queued) {
                if (activeCount >= MAX_PARALLEL_DOWNLOADS) break
                // Keep records QUEUED until their store dispatcher is ready.
                if (dispatchers[record.store] == null) continue
                val started = record.copy(status = DownloadRecord.STATUS_DOWNLOADING, updatedAt = now)
                daoRef.update(started)
                toStart.add(started)
                activeCount++
            }
            refreshState(daoRef)
        }

        // Dispatch outside the lock so callbacks can re-enter safely.
        toStart.forEach { record ->
            val dispatcher = dispatchers[record.store]
            if (dispatcher != null) {
                runCatching { dispatcher.startQueued(record) }
                    .onFailure { err ->
                        Timber.e(err, "Dispatcher ${record.store} failed to start record ${record.id}")
                        notifyFinished(record.store, record.storeGameId, DownloadRecord.STATUS_FAILED, err.message)
                    }
            } else {
                Timber.w("No dispatcher registered for store ${record.store}; record ${record.id} stays QUEUED")
            }
        }
    }

    /** Restore interrupted downloads once per process and drain the queue. */
    suspend fun onAppStart() {
        if (startupRestored) return
        startupRestored = true
        val daoRef = dao ?: return
        mutex.withLock {
            // Auto-resume interrupted active downloads.
            daoRef.replaceStatus(DownloadRecord.STATUS_DOWNLOADING, DownloadRecord.STATUS_QUEUED)
            refreshState(daoRef)
        }
        tick()
    }

    /** Trigger startup restoration from a non-coroutine caller. */
    fun attemptStartupRestoration() {
        if (startupRestored) return
        scope.launch { onAppStart() }
    }

    /** Exit hook; statuses are already persisted during each transition. */
    fun onAppExit() {
    }

    private suspend fun refreshState(daoRef: DownloadRecordDao) {
        val all = daoRef.getAll()
        recordsState.value = all
        recordChanges.tryEmit(Unit)
    }

    /** Synchronous helper for callers that aren't already in a coroutine. */
    fun blockingTick() {
        scope.launch { tick() }
    }

    /** Initialize records flow on startup. */
    suspend fun loadInitial() {
        val daoRef = dao ?: return
        refreshState(daoRef)
    }

    /** Look up a persisted record by store and game id. */
    suspend fun findRecord(store: String, storeGameId: String): DownloadRecord? {
        val daoRef = dao ?: return null
        return daoRef.findByStoreGame(store, storeGameId)
    }

    /** All records currently in the table (snapshot). */
    fun snapshotRecords(): List<DownloadRecord> = recordsState.value

    /** Internal: run a coordinator action from a non-coroutine context. */
    internal fun runOnScope(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    /** Test/debug helper. */
    internal fun runBlockingForTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }
}
