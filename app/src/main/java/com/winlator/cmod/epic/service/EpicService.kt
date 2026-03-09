package com.winlator.cmod.epic.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.winlator.cmod.steam.data.DownloadInfo
import com.winlator.cmod.epic.data.EpicCredentials
import com.winlator.cmod.epic.data.EpicGame
import com.winlator.cmod.steam.data.LaunchInfo
import com.winlator.cmod.epic.data.EpicGameToken
import com.winlator.cmod.steam.utils.MarkerUtils
import com.winlator.cmod.steam.enums.Marker
import com.winlator.cmod.steam.events.AndroidEvent
import com.winlator.cmod.PluviaApp
import com.winlator.cmod.steam.utils.ContainerUtils
import com.winlator.cmod.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.*
import com.winlator.cmod.epic.ui.util.SnackbarManager
import timber.log.Timber

/**
 * Epic Games Service - thin coordinator that delegates to other Epic managers.
 */
@AndroidEntryPoint
class EpicService : Service() {

    companion object {
        private var instance: EpicService? = null

        private const val ACTION_SYNC_LIBRARY = "com.winlator.cmod.EPIC_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "com.winlator.cmod.EPIC_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L // 15 minutes

        // Sync tracking variables
        private var syncInProgress: Boolean = false
        private var backgroundSyncJob: Job? = null
        private var lastSyncTimestamp: Long = 0L
        private var hasPerformedInitialSync: Boolean = false

        val isRunning: Boolean
            get() = instance != null

        fun start(context: Context) {

            Timber.tag("EPIC").d("Starting service...")
            // If already running, do nothing
            if (isRunning) {
                Timber.tag("EPIC").d("[EpicService] Service already running, skipping start")
                return
            }

            // First-time start: always sync without throttle
            if (!hasPerformedInitialSync) {
                Timber.tag("EPIC").i("[EpicService] First-time start - starting service with initial sync")
                val intent = Intent(context, EpicService::class.java)
                intent.action = ACTION_SYNC_LIBRARY
                context.startForegroundService(intent)
                return
            }

            // Subsequent starts: always start service, but check throttle for sync
            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSyncTimestamp

            val intent = Intent(context, EpicService::class.java)
            if (timeSinceLastSync >= SYNC_THROTTLE_MILLIS) {
                Timber.tag("EPIC").i("[EpicService] Starting service with automatic sync (throttle passed)")
                intent.action = ACTION_SYNC_LIBRARY
            } else {
                val remainingMinutes = (SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60
                Timber.tag("EPIC").i("Starting service without sync - throttled (${remainingMinutes}min remaining)")
                // Start service without sync action
            }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            Timber.tag("EPIC").i("Triggering manual library sync (bypasses throttle)")
            val intent = Intent(context, EpicService::class.java)
            intent.action = ACTION_MANUAL_SYNC
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.let { service ->
                service.stopSelf()
            }
        }

        // ==========================================================================
        // AUTHENTICATION - Delegate to EpicAuthManager
        // ==========================================================================

        suspend fun authenticateWithCode(context: Context, authorizationCode: String): Result<EpicCredentials> {
            return EpicAuthManager.authenticateWithCode(context, authorizationCode)
        }

        fun hasStoredCredentials(context: Context): Boolean {
            return EpicAuthManager.hasStoredCredentials(context)
        }

        suspend fun getStoredCredentials(context: Context): Result<EpicCredentials> {
            return EpicAuthManager.getStoredCredentials(context)
        }

        /**
         * Logout from Epic - clears credentials, database, and stops service
         */
        suspend fun logout(context: Context): Result<Unit> {
            return withContext(Dispatchers.IO) {
                try {
                    Timber.tag("EPIC").i("Logging out from Epic...")

                    // Clear stored credentials first, regardless of service state
                    val credentialsCleared = EpicAuthManager.clearStoredCredentials(context)
                    if (!credentialsCleared) {
                        Timber.tag("Epic").e("Failed to clear credentials during logout")
                        return@withContext Result.failure(Exception("Failed to clear stored credentials"))
                    }

                    // Get instance to clean up service-specific data
                    val instance = getInstance()
                    if (instance != null) {
                        // Clear all nonInstalled Epic games from database
                        instance.epicManager.deleteAllNonInstalledGames()
                        Timber.tag("Epic").i("All Non-installed Epic games removed from database")

                        // Stop the service
                        stop()
                    } else {
                        Timber.tag("Epic").w("Service not running during logout, but credentials were cleared")
                    }

                    Timber.tag("Epic").i("Logout completed successfully")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.tag("Epic").e(e, "Error during logout")
                    Result.failure(e)
                }
            }
        }

        // ==========================================================================
        // SYNC & OPERATIONS
        // ==========================================================================

        fun hasActiveOperations(): Boolean {
            return syncInProgress || backgroundSyncJob?.isActive == true || hasActiveDownload()
        }

        private fun setSyncInProgress(inProgress: Boolean) {
            syncInProgress = inProgress
        }

        fun isSyncInProgress(): Boolean = syncInProgress

        fun getInstance(): EpicService? = instance

        // ==========================================================================
        // DOWNLOAD OPERATIONS - Delegate to instance EpicManager
        // ==========================================================================

        fun hasActiveDownload(): Boolean {
            return getInstance()?.activeDownloads?.isNotEmpty() ?: false
        }

        fun getCurrentlyDownloadingGame(): Int? {
            return getInstance()?.activeDownloads?.keys?.firstOrNull()
        }

        fun getDownloadInfo(appId: Int): DownloadInfo? {
            return getInstance()?.activeDownloads?.get(appId)
        }

        fun getAllDownloads(): Map<Int, DownloadInfo> {
            return getInstance()?.activeDownloads ?: emptyMap()
        }

        suspend fun deleteGame(context: Context, appId: Int): Result<Unit> {
            var instance = getInstance()
            if (instance == null) {
                Timber.tag("Epic").i("deleteGame: Service not running, attempting to start...")
                start(context)
                
                // Wait up to 2 seconds for service to start
                for (i in 0..20) {
                    kotlinx.coroutines.delay(100)
                    instance = getInstance()
                    if (instance != null) break
                }
            }

            if (instance == null) {
                Timber.tag("Epic").e("deleteGame: EpicService failed to start or instance is still null")
                return Result.failure(Exception("Epic service is not active. Please try again in a moment."))
            }

            return try {
                Timber.tag("Epic").i("Starting uninstallation for appId: $appId")

                // Terminate any running Wine processes to avoid file locks
                withContext(Dispatchers.Main) {
                    Timber.tag("Epic").d("Terminating Wine processes...")
                    com.winlator.cmod.core.ProcessHelper.terminateAllWineProcesses()
                    // Wait a moment for processes to exit
                    kotlinx.coroutines.delay(1000)
                }

                // Get the game to find its install path
                val game = instance.epicManager.getGameById(appId)
                if (game == null) {
                    Timber.tag("Epic").e("deleteGame: Game not found in DB: $appId")
                    return Result.failure(Exception("Game not found: $appId"))
                }

                // Delete game folder
                val path = if (game.installPath.isNotEmpty()) game.installPath else EpicConstants.getGameInstallPath(context, game.appName)
                val gameDir = File(path)
                
                // Safety check: Ensure we are NOT deleting the base Epic/games directory
                val baseDir = EpicConstants.defaultEpicGamesPath(context)
                if (gameDir.absolutePath == File(baseDir).absolutePath) {
                    Timber.tag("Epic").e("Safety Triggered: Refusing to delete base Epic games directory: $path")
                } else if (gameDir.exists()) {
                    Timber.tag("Epic").i("Deleting installation folder: $path")
                    try {
                        val deleted = gameDir.deleteRecursively()
                        if (deleted) {
                            Timber.tag("Epic").i("Successfully deleted installation folder")
                        } else {
                            Timber.tag("Epic").w("Failed to delete some files in installation folder")
                        }
                    } catch (e: Exception) {
                        Timber.tag("Epic").e(e, "Exception while deleting installation folder")
                    }
                    
                    // Cleanup markers
                    MarkerUtils.removeMarker(path, Marker.DOWNLOAD_COMPLETE_MARKER)
                    MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                } else {
                    Timber.tag("Epic").w("Installation folder not found: $path")
                }

                // Uninstall from database (keeps the entry but marks as not installed)
                Timber.tag("Epic").d("Updating database: marking game $appId as uninstalled")
                instance.epicManager.uninstall(appId)

                // Delete shortcuts and containers
                withContext(Dispatchers.IO) {
                    val containerManager = com.winlator.cmod.container.ContainerManager(context)
                    val shortcuts = containerManager.loadShortcuts()
                    val containersToDelete = mutableSetOf<Int>()
                    
                    Timber.tag("Epic").d("Scanning ${shortcuts.size} shortcuts for game $appId")
                    shortcuts.forEach { shortcut ->
                        val source = shortcut.getExtra("game_source")
                        val shortcutAppId = shortcut.getExtra("app_id")
                        
                        if (source == "EPIC" && shortcutAppId == appId.toString()) {
                            if (shortcut.container != null) {
                                containersToDelete.add(shortcut.container.id)
                            }
                            Timber.tag("Epic").d("Deleting shortcut file: ${shortcut.file.absolutePath}")
                            shortcut.file.delete()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        containersToDelete.forEach { id ->
                            val container = containerManager.getContainerById(id)
                            if (container != null) {
                                Timber.tag("Epic").i("Requesting deletion of container ${container.id} for game $appId")
                                containerManager.removeContainerAsync(container) {}
                            }
                        }
                        
                        // Also try to delete the legacy named container if it exists
                        ContainerUtils.deleteContainer(context, "EPIC_${game.id}")
                    }
                }

                // Trigger library refresh event
                Timber.tag("Epic").d("Emitting LibraryInstallStatusChanged event")
                com.winlator.cmod.PluviaApp.events.emitJava(
                    com.winlator.cmod.steam.events.AndroidEvent.LibraryInstallStatusChanged(appId)
                )

                Timber.tag("Epic").i("Successfully completed uninstallation for appId: $appId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "Critical failure during uninstallation for appId: $appId")
                Result.failure(e)
            }
        }

        suspend fun cleanupDownload(context: Context, appId: Int) {
            withContext(Dispatchers.IO) {
                getInstance()?.epicManager?.getGameById(appId)?.let { game ->
                    val path = EpicConstants.getGameInstallPath(context, game.appName)
                    MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                }
            }
            getInstance()?.activeDownloads?.remove(appId)
        }

        fun cancelDownload(appId: Int): Boolean {
            val instance = getInstance()
            val downloadInfo = instance?.activeDownloads?.get(appId)

            return if (downloadInfo != null) {
                Timber.tag("EPIC").i("Cancelling download for Epic game: $appId")
                downloadInfo.cancel()
                instance.activeDownloads.remove(appId)
                Timber.tag("EPIC").d("Download cancelled for Epic game: $appId")
                true
            } else {
                Timber.w("No active download found for Epic game: $appId")
                false
            }
        }

        // ==========================================================================
        // GAME & LIBRARY OPERATIONS
        // ==========================================================================

        fun getEpicGameOf(appId: Int): EpicGame? {
            return runBlocking(Dispatchers.IO) {
                getInstance()?.epicManager?.getGameById(appId)
            }
        }

        fun getEpicGameByAppName(appName: String): EpicGame? {
            return runBlocking(Dispatchers.IO) {
                getInstance()?.epicManager?.getGameByAppName(appName)
            }
        }

        fun getDLCForGame(appId: Int): List<EpicGame> {
            return runBlocking(Dispatchers.IO) { getDLCForGameSuspend(appId) }
        }

        suspend fun getDLCForGameSuspend(appId: Int): List<EpicGame> {
            return getInstance()?.epicManager?.getDLCForTitle(appId) ?: emptyList()
        }

        suspend fun updateEpicGame(game: EpicGame) {
            getInstance()?.epicManager?.updateGame(game)
        }


        fun isGameInstalled(context: Context, appId: Int): Boolean {
            val game = getEpicGameOf(appId) ?: return false

            if (game.isInstalled && game.installPath.isNotEmpty()) {
                return MarkerUtils.hasMarker(game.installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            }

            val installPath = game.installPath.takeIf { it.isNotEmpty() }
                ?: game.appName.takeIf { it.isNotEmpty() }?.let {
                    EpicConstants.getGameInstallPath(context, it)
                }
                ?: return false

            val isDownloadComplete = MarkerUtils.hasMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val isDownloadInProgress = MarkerUtils.hasMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            if (isDownloadComplete && !isDownloadInProgress) {
                val updatedGame = game.copy(
                    isInstalled = true,
                    installPath = installPath,
                )
                runBlocking(Dispatchers.IO) {
                    getInstance()?.epicManager?.updateGame(updatedGame)
                }
                return true
            }

            return false
        }

        fun getInstallPath(appId: Int): String? {
            val game = getEpicGameOf(appId)
            return if (game?.isInstalled == true && game.installPath.isNotEmpty()) {
                game.installPath
            } else {
                null
            }
        }

        suspend fun getInstalledExe(appId: Int): String {
            return getInstance()?.epicManager?.getInstalledExe(appId) ?: ""
        }

        /**
         * Resolves the effective launch executable for an Epic game.
         * Container id is expected to be "EPIC_&lt;numericId&gt;" (from library). Returns empty if
         * game is not installed, no executable can be found, or containerId cannot be parsed.
         */
        suspend fun getLaunchExecutable(containerId: String): String {
            val gameId = try {
                ContainerUtils.extractGameIdFromContainerId(containerId)
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "Failed to parse Epic containerId: $containerId")
                return ""
            }
            return getInstance()?.epicManager?.getLaunchExecutable(gameId) ?: ""
        }

        suspend fun refreshLibrary(context: Context): Result<Int> {
            return getInstance()?.epicManager?.refreshLibrary(context)
                ?: Result.failure(Exception("Service not available"))
        }

        suspend fun fetchManifestSizes(context: Context, appId: Int): EpicManager.ManifestSizes {
            return getInstance()?.epicManager?.fetchManifestSizes(context, appId)
                ?: EpicManager.ManifestSizes(installSize = 0L, downloadSize = 0L)
        }

        fun downloadGame(context: Context, appId: Int, dlcGameIds: List<Int>, installPath: String, containerLanguage: String): Result<DownloadInfo> {
            val instance = getInstance() ?: return Result.failure(Exception("Service not available"))

            val game = runBlocking { instance.epicManager.getGameById(appId) }
                ?: return Result.failure(Exception("Game not found for appId: $appId"))
            val gameId = game.id ?: return Result.failure(Exception("Game ID not found for appId: $appId"))

            // Check if already downloading
            if (instance.activeDownloads.containsKey(appId)) {
                Timber.tag("Epic").w("Download already in progress for $appId")
                return Result.success(instance.activeDownloads[appId]!!)
            }

            // Create DownloadInfo before launching coroutine to avoid race condition
            val downloadInfo = DownloadInfo(
                jobCount = 1,
                gameId = appId,
                downloadingAppIds = CopyOnWriteArrayList<Int>(),
            )

            instance.activeDownloads[appId] = downloadInfo
            downloadInfo.setActive(true)

            // Start download in background
            val job = instance.scope.launch {
                try {
                    val commonRedistDir = File(installPath, "_CommonRedist")
                    Timber.tag("Epic").i("Starting download for game: ${game.title}, gameId: ${game.id}")

                    val result = instance.epicDownloadManager.downloadGame(
                        context,
                        game,
                        installPath,
                        downloadInfo,
                        containerLanguage,
                        dlcGameIds,
                        commonRedistDir,
                    )

                    Timber.tag("Epic").d("Download result: ${if (result.isSuccess) "SUCCESS" else "FAILURE: ${result.exceptionOrNull()?.message}"}")

                    if (result.isSuccess) {
                        Timber.i("[Download] Completed successfully for game $gameId")
                        downloadInfo.setProgress(1.0f)
                        downloadInfo.setActive(false)

                        SnackbarManager.show("Download completed successfully!")
                    } else {
                        val error = result.exceptionOrNull()
                        Timber.e(error, "[Download] Failed for game $gameId")
                        downloadInfo.setProgress(-1.0f)
                        downloadInfo.setActive(false)

                        SnackbarManager.show("Download failed: ${error?.message ?: "Unknown error"}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Download] Exception for game $gameId")
                    downloadInfo.setProgress(-1.0f)
                    downloadInfo.setActive(false)

                    SnackbarManager.show("Download error: ${e.message ?: "Unknown error"}")
                } finally {
                    instance.activeDownloads.remove(appId)
                    Timber.d("[Download] Finished for game $gameId, progress: ${downloadInfo.getProgress()}, active: ${downloadInfo.isActive()}")
                }
            }
            downloadInfo.setDownloadJob(job)

            // Return the DownloadInfo immediately so caller can track progress
            return Result.success(downloadInfo)
        }

        suspend fun refreshSingleGame(appId: Int, context: Context): Result<EpicGame?> {
            // For now, just get from database
            val game = getInstance()?.epicManager?.getGameById(appId)
            // TODO: Fix this up.
            return if (game != null) {
                Result.success(game)
            } else {
                Result.failure(Exception("Game not found: $appId"))
            }
        }

        // ==========================================================================
        // Game Launcher Helpers
        // ==========================================================================

        suspend fun getGameLaunchToken(
            context: Context,
            namespace: String? = null,
            catalogItemId: String? = null,
            requiresOwnershipToken: Boolean = false
        ): Result<EpicGameToken> {
            return EpicAuthManager.getGameLaunchToken(context, namespace, catalogItemId, requiresOwnershipToken)
        }

        suspend fun buildLaunchParameters(
            context: Context,
            game: EpicGame,
            offline: Boolean = false,
            languageCode: String = "en-US"
        ): Result<List<String>> {
            return EpicGameLauncher.buildLaunchParameters(context, game, offline, languageCode)
        }

        fun cleanupLaunchTokens(context: Context) {
            EpicGameLauncher.cleanupOwnershipTokens(context)
        }

        // ==========================================================================
        // CLOUD SAVES HELPERS
        // ==========================================================================

        /**
         * Get the Epic account ID from stored credentials
         */
        fun getAccountId(): String? {
            return try {
                val context = getInstance()?.applicationContext ?: return null
                val credentialsResult = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    EpicAuthManager.getStoredCredentials(context)
                }
                credentialsResult.getOrNull()?.accountId
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "Failed to get account ID")
                null
            }
        }
    }

    private lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var epicManager: EpicManager

    @Inject
    lateinit var epicDownloadManager: EpicDownloadManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track active downloads by GameNative Int ID
    private val activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = { stop() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.tag("Epic").i("[EpicService] Service created")

        // Initialize notification helper for foreground service
        notificationHelper = NotificationHelper(applicationContext)
        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag("EPIC").d("onStartCommand() - action: ${intent?.action}")

        val instance = getInstance()
        // Start as foreground service
        val notification = notificationHelper.createForegroundNotification("Connected")
        startForeground(1, notification)

        // Determine if we should sync based on the action
        val shouldSync = when (intent?.action) {
            ACTION_MANUAL_SYNC -> {
                Timber.tag("EPIC").i("Manual sync requested - bypassing throttle")
                true
            }

            ACTION_SYNC_LIBRARY -> {
                Timber.tag("EPIC").i("Automatic sync requested")
                true
            }

            null -> {
                // Service restarted by Android with null intent (START_STICKY behavior)
                // Only sync if we haven't done initial sync yet, or if it's been a while
                val timeSinceLastSync = System.currentTimeMillis() - lastSyncTimestamp
                val shouldResync = !hasPerformedInitialSync || timeSinceLastSync >= SYNC_THROTTLE_MILLIS

                if (shouldResync) {
                    Timber.tag("EPIC").i("Service restarted by Android - performing sync (hasPerformedInitialSync=$hasPerformedInitialSync, timeSinceLastSync=${timeSinceLastSync}ms)")
                    true
                } else {
                    Timber.tag("EPIC").d("Service restarted by Android - skipping sync (throttled)")
                    false
                }
            }

            else -> {
                // Service started without sync action (e.g., just to keep it alive)
                Timber.tag("EPIC").d(" Service started without sync action")
                false
            }
        }

        // Start background library sync if requested
        if (shouldSync && (backgroundSyncJob == null || backgroundSyncJob?.isActive != true)) {
            Timber.tag("EPIC").i("Starting background library sync")

            backgroundSyncJob?.cancel() // Cancel any existing job
            backgroundSyncJob = scope.launch {
                try {
                    setSyncInProgress(true)
                    Timber.tag("EPIC").d("Starting background library sync")
                    val syncResult = epicManager.startBackgroundSync(applicationContext)
                    if (syncResult.isFailure) {
                        Timber.w("Failed to start background sync: ${syncResult.exceptionOrNull()?.message}")
                    } else {
                        Timber.tag("EPIC").i("Background library sync completed successfully")
                        // Update last sync timestamp on successful sync
                        lastSyncTimestamp = System.currentTimeMillis()
                        // Mark that initial sync has been performed
                        hasPerformedInitialSync = true
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception starting background sync")
                } finally {
                    setSyncInProgress(false)
                }
            }
        } else if (shouldSync) {
            Timber.tag("EPIC").d("Background sync already in progress, skipping")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("Epic").i("[EpicService] Service destroyed")
        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)

        // Cancel sync operations
        backgroundSyncJob?.cancel()
        setSyncInProgress(false)

        scope.cancel() // Cancel any ongoing operations
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
