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
 * Single source of truth for downloads across Steam / Epic / GOG.
 *
 * Responsibilities:
 *  * Enforces a single active download shared across all stores.
 *  * Persists every download as a [DownloadRecord] so they survive app restarts.
 *  * Auto-resumes downloads that were active when the app exited; leaves PAUSED ones paused.
 *
 * Flow of a download:
 *  1. The store-specific service receives a download request from the UI.
 *  2. It calls [requestSlot]. The coordinator persists or updates a DownloadRecord and tells
 *     the caller whether to start now ([Decision.Start]) or wait ([Decision.Queue]).
 *  3. When a download finishes, the store calls [notifyFinished]; the coordinator updates the
 *     record and dispatches the next queued download (if any) via the registered [Dispatcher].
 *  4. UI controls (pause / resume / cancel / clear) call into the coordinator, which mutates
 *     the record and asks the dispatcher to perform the side effect (cancel the running job,
 *     delete partial files, etc.).
 */
object DownloadCoordinator {
    private const val MAX_PARALLEL_DOWNLOADS = 1

    /**
     * A per-store hook the coordinator uses to start, pause, resume, or cancel an actual
     * download. Stores register their dispatcher at service startup.
     */
    interface Dispatcher {
        /**
         * Start a download that the coordinator just dequeued. The store should look up the
         * pending request matching this record and launch the actual coroutine. Called from
         * the coordinator's IO scope.
         */
        fun startQueued(record: DownloadRecord)

        /** Pause an actively running download, persisting partial files. */
        fun pauseRunning(record: DownloadRecord)

        /**
         * Cancel an actively running download and delete partial files. The coordinator has
         * already marked the record CANCELLED.
         */
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

    fun init(database: PluviaDatabase) {
        if (dao != null) return
        dao = database.downloadRecordDao()
    }

    fun registerDispatcher(store: String, dispatcher: Dispatcher) {
        dispatchers[store] = dispatcher
        // A newly-registered dispatcher might have queued records waiting from a previous
        // process or from a moment ago when it wasn't available yet. Drain the queue.
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

    /**
     * Persist a download request and decide whether to start it immediately or queue it. The
     * caller (a store service) should inspect the result; on Start it should launch the actual
     * download, on Queue it should create a UI entry showing QUEUED status and wait for the
     * coordinator to dispatch via [Dispatcher.startQueued].
     */
    suspend fun requestSlot(
        store: String,
        storeGameId: String,
        title: String = "",
        artUrl: String = "",
        installPath: String = "",
        selectedDlcs: String = "",
        language: String = "",
        bytesTotal: Long = 0L,
    ): Decision {
        val daoRef = dao ?: throw IllegalStateException("DownloadCoordinator not initialised")

        return mutex.withLock {
            val now = System.currentTimeMillis()
            val existing = daoRef.findByStoreGame(store, storeGameId)

            // Re-entry guard: tick() promotes a QUEUED record to DOWNLOADING and then
            // dispatches it to the store, which calls back into requestSlot via the public
            // download API. The slot was already granted by tick(); without this short
            // circuit we'd count the record against itself, decide there are no free slots,
            // and rewrite it back to QUEUED — making Resume hang at "Queued" forever.
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
                            bytesTotal = bytesTotal,
                            status = status,
                            createdAt = now,
                            updatedAt = now,
                        )
                    val id = daoRef.upsert(newRecord)
                    newRecord.copy(id = id)
                } else {
                    // We only reach here for re-enqueue cases (record was COMPLETE/CANCELLED/
                    // FAILED/PAUSED/QUEUED — anything but DOWNLOADING, which short-circuits
                    // above). For a re-enqueue the caller is fully respecifying the request,
                    // so OVERWRITE the row with the new values. Previously we did
                    // `selectedDlcs.ifEmpty { existing.selectedDlcs }` which was ambiguous —
                    // an empty list (user wants base game only) was indistinguishable from
                    // "caller didn't supply", so old DLC selections leaked through to a fresh
                    // download.
                    val updated =
                        existing.copy(
                            title = title,
                            artUrl = artUrl,
                            installPath = installPath,
                            selectedDlcs = selectedDlcs,
                            language = language,
                            bytesTotal = if (bytesTotal > 0L) bytesTotal else existing.bytesTotal,
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

    /** Update progress for a running download. Lightweight; runs without locking the queue. */
    fun updateProgress(store: String, storeGameId: String, bytesDownloaded: Long, bytesTotal: Long) {
        val daoRef = dao ?: return
        scope.launch {
            val record = daoRef.findByStoreGame(store, storeGameId) ?: return@launch
            daoRef.updateProgress(record.id, bytesDownloaded, bytesTotal)
        }
    }

    /**
     * Notify the coordinator that a download has terminated (success / fail / cancel / pause).
     * The coordinator persists the new status and starts the next queued download.
     */
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
        // Drain the queue outside the lock to avoid re-entrancy with dispatcher callbacks.
        tick()
    }

    /** Pause a running download. Marks PAUSED and asks the dispatcher to cancel its job. */
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

    /** Resume a paused / queued / failed download. */
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
        val toResume = daoRef.findByStatus(DownloadRecord.STATUS_PAUSED)
        toResume.forEach { resume(it.store, it.storeGameId) }
    }

    /** Cancel a download and delete partial files via the dispatcher. */
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

    /** Remove finished records (COMPLETE / CANCELLED / FAILED) from the table. */
    suspend fun clear() {
        val daoRef = dao ?: return
        mutex.withLock {
            daoRef.deleteFinished()
            refreshState(daoRef)
        }
        // Notify the Downloads tab to refresh.
        PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(0, false))
    }

    /** Blocking variant for shutdown paths that may kill the process immediately after cleanup. */
    fun clearBlocking() {
        runBlocking { clear() }
    }

    /**
     * Drain the queue: while there are free slots and queued records, dispatch the oldest one
     * to its store-specific dispatcher.
     */
    suspend fun tick() {
        val daoRef = dao ?: return
        val toStart = mutableListOf<DownloadRecord>()
        mutex.withLock {
            var activeCount = daoRef.countByStatus(DownloadRecord.STATUS_DOWNLOADING)
            val queued = daoRef.findByStatus(DownloadRecord.STATUS_QUEUED)
            val now = System.currentTimeMillis()

            for (record in queued) {
                if (activeCount >= MAX_PARALLEL_DOWNLOADS) break
                val started = record.copy(status = DownloadRecord.STATUS_DOWNLOADING, updatedAt = now)
                daoRef.update(started)
                toStart.add(started)
                activeCount++
            }
            refreshState(daoRef)
        }

        // Dispatch outside the lock so dispatchers can synchronously call back into the
        // coordinator if needed.
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

    /**
     * Called once on app startup. Records that were DOWNLOADING when the process died are
     * moved back to QUEUED (auto-resume); PAUSED records stay PAUSED until the user resumes
     * them. Then the queue is drained.
     *
     * Idempotent: subsequent calls within the same process are no-ops.
     */
    suspend fun onAppStart() {
        if (startupRestored) return
        startupRestored = true
        val daoRef = dao ?: return
        mutex.withLock {
            // DOWNLOADING -> QUEUED (auto-resume on next launch).
            daoRef.replaceStatus(DownloadRecord.STATUS_DOWNLOADING, DownloadRecord.STATUS_QUEUED)
            refreshState(daoRef)
        }
        tick()
    }

    /** Triggers onAppStart from a non-coroutine caller. */
    fun attemptStartupRestoration() {
        if (startupRestored) return
        scope.launch { onAppStart() }
    }

    /**
     * Called by AppTerminationHelper when the app is exiting. Does NOT pause everything — it
     * leaves DOWNLOADING records in DOWNLOADING state so they auto-resume on next launch, and
     * leaves PAUSED records PAUSED.
     */
    fun onAppExit() {
        // Nothing to persist here — every status transition was already written to the DAO.
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

    /** Initialize records flow on startup. Safe to call multiple times. */
    suspend fun loadInitial() {
        val daoRef = dao ?: return
        refreshState(daoRef)
    }

    /**
     * Look up the persisted record for a given store+gameId. Useful for resume to recover the
     * original install path / dlcs / language without going through the in-memory params map.
     */
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
