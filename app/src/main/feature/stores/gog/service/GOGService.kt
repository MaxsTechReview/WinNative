package com.winlator.cmod.feature.stores.gog.service
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.feature.stores.epic.ui.util.SnackbarManager
import com.winlator.cmod.feature.stores.gog.data.GOGCredentials
import com.winlator.cmod.feature.stores.gog.data.GOGGame
import com.winlator.cmod.feature.stores.gog.data.LibraryItem
import com.winlator.cmod.feature.stores.steam.data.DownloadInfo
import com.winlator.cmod.feature.stores.steam.data.LaunchInfo
import com.winlator.cmod.feature.stores.steam.enums.DownloadPhase
import com.winlator.cmod.feature.stores.steam.enums.Marker
import com.winlator.cmod.feature.stores.steam.events.AndroidEvent
import com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
import com.winlator.cmod.feature.stores.steam.utils.MarkerUtils
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.shared.android.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/**
 * GOG Service - thin abstraction layer that delegates to managers.
 *
 * Architecture:
 * - GOGApiClient: Api Layer for interacting with GOG's APIs
 * - GOGDownloadManager: Handles Download Logic for Games
 * - GOGConstants: Shared Constants for our GOG-related data
 * - GOGCloudSavesManager: Handler for Cloud Saves
 * - GOGAuthManager: Authentication and account management
 * - GOGManager: Game library, downloads, and installation
 * - GOGManifestParser: Parses and has utils for parsing/extracting/decompressing manifests.
 * - GOGDataMdoels: Data Models for GOG-related Data types such as API responses
 *
 */
@AndroidEntryPoint
class GOGService : Service() {
    companion object {
        private const val ACTION_SYNC_LIBRARY = "com.winlator.cmod.GOG_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "com.winlator.cmod.GOG_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L // 15 minutes

        private var instance: GOGService? = null

        // Sync tracking variables
        private var syncInProgress: Boolean = false
        private var backgroundSyncJob: Job? = null
        private var lastSyncTimestamp: Long = 0L
        private var hasPerformedInitialSync: Boolean = false

        val isRunning: Boolean
            get() = instance != null

        fun start(context: Context) {
            // If already running, do nothing
            if (isRunning) {
                Timber.d("[GOGService] Service already running, skipping start")
                return
            }

            // First-time start: always sync without throttle
            if (!hasPerformedInitialSync) {
                Timber.i("[GOGService] First-time start - starting service with initial sync")
                val intent = Intent(context, GOGService::class.java)
                intent.action = ACTION_SYNC_LIBRARY
                context.startForegroundService(intent)
                return
            }

            // Subsequent starts: always start service, but check throttle for sync
            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSyncTimestamp

            val intent = Intent(context, GOGService::class.java)
            if (timeSinceLastSync >= SYNC_THROTTLE_MILLIS) {
                Timber.i("[GOGService] Starting service with automatic sync (throttle passed)")
                intent.action = ACTION_SYNC_LIBRARY
            } else {
                val remainingMinutes = (SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60
                Timber.d("[GOGService] Starting service without sync - throttled (${remainingMinutes}min remaining)")
                // Start service without sync action
            }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            Timber.i("[GOGService] Triggering manual library sync (bypasses throttle)")
            val intent = Intent(context, GOGService::class.java)
            intent.action = ACTION_MANUAL_SYNC
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.let { service ->
                service.stopSelf()
            }
        }

        // ==========================================================================
        // AUTHENTICATION - Delegate to GOGAuthManager
        // ==========================================================================

        suspend fun authenticateWithCode(
            context: Context,
            authorizationCode: String,
        ): Result<GOGCredentials> = GOGAuthManager.authenticateWithCode(context, authorizationCode)

        fun hasStoredCredentials(context: Context): Boolean = GOGAuthManager.hasStoredCredentials(context)

        suspend fun getStoredCredentials(context: Context): Result<GOGCredentials> = GOGAuthManager.getStoredCredentials(context)

        suspend fun getResolvedSaveDirectories(
            context: Context,
            appId: String,
        ): List<File> {
            val activeInstance = getInstance() ?: return emptyList()
            val gameId = ContainerUtils.extractGameIdFromContainerId(appId).toString()
            val game = activeInstance.gogManager.getGameFromDbById(gameId) ?: return emptyList()
            return activeInstance.gogManager
                .getSaveDirectoryPath(context, appId, game.title)
                ?.map { File(it.location) }
                ?.filter { it.exists() || !it.path.isNullOrEmpty() }
                ?: emptyList()
        }

        suspend fun validateCredentials(context: Context): Result<Boolean> = GOGAuthManager.validateCredentials(context)

        fun clearStoredCredentials(context: Context): Boolean = GOGAuthManager.clearStoredCredentials(context)

        /**
         * Logout from GOG - clears credentials, database, and stops service
         */
        suspend fun logout(context: Context): Result<Unit> {
            return withContext(Dispatchers.IO) {
                try {
                    Timber.i("[GOGService] Logging out from GOG...")

                    // Get instance first before stopping the service
                    val instance = getInstance()
                    if (instance == null) {
                        clearStoredCredentials(context)
                        Timber.w("[GOGService] Service instance not available during logout; credentials cleared only")
                        return@withContext Result.success(Unit)
                    }

                    // Clear stored credentials
                    val credentialsCleared = clearStoredCredentials(context)
                    if (!credentialsCleared) {
                        Timber.w("[GOGService] Failed to clear credentials during logout")
                    }

                    // Clear all non-installed GOG games from database
                    instance.gogManager.deleteAllNonInstalledGames()
                    Timber.i("[GOGService] All non-installed GOG games removed from database")

                    // Stop the service
                    stop()

                    Timber.i("[GOGService] Logout completed successfully")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.e(e, "[GOGService] Error during logout")
                    Result.failure(e)
                }
            }
        }

        // ==========================================================================
        // SYNC & OPERATIONS
        // ==========================================================================

        fun hasActiveOperations(): Boolean = syncInProgress || backgroundSyncJob?.isActive == true

        private fun setSyncInProgress(inProgress: Boolean) {
            syncInProgress = inProgress
        }

        fun isSyncInProgress(): Boolean = syncInProgress

        fun getInstance(): GOGService? = instance

        // ==========================================================================
        // DOWNLOAD OPERATIONS - Delegate to instance GOGManager
        // ==========================================================================

        fun hasActiveDownload(): Boolean = getInstance()?.activeDownloads?.isNotEmpty() ?: false

        fun getCurrentlyDownloadingGame(): String? = getInstance()?.activeDownloads?.keys?.firstOrNull()

        fun getDownloadInfo(gameId: String): DownloadInfo? = getInstance()?.activeDownloads?.get(gameId)

        fun getAllDownloads(): Map<String, DownloadInfo> = getInstance()?.activeDownloads ?: emptyMap()

        fun cleanupDownload(gameId: String) {
            getInstance()?.activeDownloads?.remove(gameId)
        }

        fun cancelDownload(gameId: String): Boolean {
            val instance = getInstance()
            val downloadInfo = instance?.activeDownloads?.get(gameId)

            return if (downloadInfo != null) {
                Timber.i("Cancelling download for game: $gameId")
                downloadInfo.isCancelling = true
                downloadInfo.cancel("Cancelled by user")
                // Delete partially downloaded files
                CoroutineScope(Dispatchers.IO).launch {
                    downloadInfo.awaitCompletion(timeoutMs = 3000L)
                    val game = instance.gogManager.getGameFromDbById(gameId)
                    val installPath =
                        if (game != null) {
                            if (game.installPath.isNotEmpty()) {
                                game.installPath
                            } else {
                                instance.gogManager.getGameInstallPath(
                                    gameId,
                                    game.title,
                                )
                            }
                        } else {
                            ""
                        }
                    if (installPath.isNotEmpty()) {
                        val dirFile = File(installPath)
                        if (dirFile.exists() && dirFile.isDirectory) {
                            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                            dirFile.deleteRecursively()
                        }
                    }
                    downloadInfo.updateStatus(DownloadPhase.CANCELLED)
                    instance.activeDownloads.remove(gameId)
                }
                Timber.d("Download cancelled for game: $gameId")
                true
            } else {
                Timber.w("No active download found for game: $gameId")
                false
            }
        }

        fun pauseDownload(gameId: String) {
            val info = getInstance()?.activeDownloads?.get(gameId) ?: return
            val status = info.getStatusFlow().value
            if (status == DownloadPhase.COMPLETE || status == DownloadPhase.CANCELLED) return

            if (info.isActive()) {
                info.isCancelling = false
                info.updateStatus(DownloadPhase.PAUSED)
                info.cancel("Paused by user")
            } else {
                info.updateStatus(DownloadPhase.PAUSED)
                info.setActive(false)
            }
        }

        fun pauseAll() {
            getInstance()?.activeDownloads?.values?.forEach { info ->
                val status = info.getStatusFlow().value
                if (info.isActive()) {
                    info.isCancelling = false
                    info.updateStatus(DownloadPhase.PAUSED)
                    info.cancel("Paused all")
                } else if (status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED) {
                    info.updateStatus(DownloadPhase.PAUSED)
                    info.setActive(false)
                }
            }
        }

        fun resumeDownload(gameId: String) {
            val instance = getInstance() ?: return
            val context = com.winlator.cmod.app.service.DownloadService.appContext ?: return
            val info = instance.activeDownloads[gameId]
            val status = info?.getStatusFlow()?.value
            if (info != null && info.isActive()) return
            if (status != null && status != DownloadPhase.PAUSED && status != DownloadPhase.QUEUED && status != DownloadPhase.FAILED) {
                return
            }
            val game = runBlocking(Dispatchers.IO) { instance.gogManager.getGameFromDbById(gameId) } ?: return
            instance.activeDownloads.remove(gameId)
            val installPath =
                if (game.installPath.isNotEmpty()) {
                    game.installPath
                } else {
                    instance.gogManager.getGameInstallPath(
                        gameId,
                        game.title,
                    )
                }
            downloadGame(context, gameId, installPath, "")
        }

        fun resumeAll() {
            val instance = getInstance() ?: return
            instance.activeDownloads.keys
                .toList()
                .forEach(::resumeDownload)
        }

        fun cancelAll() {
            val instance = getInstance() ?: return
            CoroutineScope(Dispatchers.IO).launch {
                instance.activeDownloads.entries.toList().forEach { (gameId, info) ->
                    info.isCancelling = true
                    info.cancel("Cancelled all")
                    info.awaitCompletion(timeoutMs = 3000L)
                    val game = instance.gogManager.getGameFromDbById(gameId)
                    val installPath =
                        if (game != null) {
                            if (game.installPath.isNotEmpty()) {
                                game.installPath
                            } else {
                                instance.gogManager.getGameInstallPath(
                                    gameId,
                                    game.title,
                                )
                            }
                        } else {
                            ""
                        }
                    if (installPath.isNotEmpty()) {
                        val dirFile = File(installPath)
                        if (dirFile.exists() && dirFile.isDirectory) {
                            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                            dirFile.deleteRecursively()
                        }
                    }
                    info.updateStatus(DownloadPhase.CANCELLED)
                    instance.activeDownloads.remove(gameId)
                }
            }
        }

        fun clearCompletedDownloads() {
            val instance = getInstance() ?: return
            val toRemove =
                instance.activeDownloads
                    .filterValues {
                        val status = it.getStatusFlow().value
                        status == com.winlator.cmod.feature.stores.steam.enums.DownloadPhase.COMPLETE ||
                            status == com.winlator.cmod.feature.stores.steam.enums.DownloadPhase.CANCELLED
                    }.keys
            toRemove.forEach { instance.activeDownloads.remove(it) }
        }

        // ==========================================================================
        // GAME & LIBRARY OPERATIONS - Delegate to instance GOGManager
        // ==========================================================================

        fun getGOGGameOf(gameId: String): GOGGame? =
            runBlocking(Dispatchers.IO) {
                getInstance()?.gogManager?.getGameFromDbById(gameId)
            }

        suspend fun updateGOGGame(game: GOGGame) {
            getInstance()?.gogManager?.updateGame(game)
        }

        fun isGameInstalled(gameId: String): Boolean {
            return runBlocking(Dispatchers.IO) {
                val game = getInstance()?.gogManager?.getGameFromDbById(gameId)
                if (game?.isInstalled != true) {
                    return@runBlocking false
                }

                // Verify the installation is actually valid
                val (isValid, errorMessage) =
                    getInstance()?.gogManager?.verifyInstallation(gameId)
                        ?: Pair(false, "Service not available")
                if (!isValid) {
                    Timber.w("Game $gameId marked as installed but verification failed: $errorMessage")
                }
                isValid
            }
        }

        fun getInstallPath(gameId: String): String? =
            runBlocking(Dispatchers.IO) {
                val game = getInstance()?.gogManager?.getGameFromDbById(gameId)
                if (game?.isInstalled == true) game.installPath else null
            }

        fun verifyInstallation(gameId: String): Pair<Boolean, String?> =
            getInstance()?.gogManager?.verifyInstallation(gameId)
                ?: Pair(false, "Service not available")

        suspend fun getInstalledExe(libraryItem: LibraryItem): String =
            getInstance()?.gogManager?.getInstalledExe(libraryItem)
                ?: ""

        /**
         * Resolves the effective launch executable for a GOG game (container config or auto-detected).
         * Returns empty string if no executable can be found.
         */
        suspend fun getLaunchExecutable(
            appId: String,
            container: Container,
        ): String = getInstance()?.gogManager?.getLaunchExecutable(appId, container) ?: ""

        fun getGogWineStartCommand(
            libraryItem: LibraryItem,
            container: Container,
            bootToContainer: Boolean,
            appLaunchInfo: LaunchInfo?,
            envVars: com.winlator.cmod.runtime.wine.EnvVars,
            guestProgramLauncherComponent: com.winlator.cmod.runtime.display.environment.components.GuestProgramLauncherComponent,
            gameId: Int,
        ): String =
            getInstance()?.gogManager?.getGogWineStartCommand(
                libraryItem,
                container,
                bootToContainer,
                appLaunchInfo,
                envVars,
                guestProgramLauncherComponent,
                gameId,
            ) ?: "\"explorer.exe\""

        suspend fun refreshLibrary(context: Context): Result<Int> =
            getInstance()?.gogManager?.refreshLibrary(context)
                ?: Result.failure(Exception("Service not available"))

        fun downloadGame(
            context: Context,
            gameId: String,
            installPath: String,
            containerLanguage: String,
        ): Result<DownloadInfo?> {
            val activeInstance =
                getInstance() ?: run {
                    start(context)
                    return Result.failure(Exception("GOG service is starting. Please try again."))
                }
            val game =
                runBlocking(Dispatchers.IO) { activeInstance.gogManager.getGameFromDbById(gameId) }
                    ?: return Result.failure(Exception("Game not found: $gameId"))
            val effectiveInstallPath =
                if (installPath.isNotEmpty()) {
                    installPath
                } else {
                    activeInstance.gogManager.getGameInstallPath(
                        gameId,
                        game.title,
                    )
                }

            val existingDownload = activeInstance.activeDownloads[gameId]
            if (existingDownload != null) {
                if (existingDownload.isActive()) {
                    Timber.tag("GOG").w("Download already in progress for $gameId")
                    return Result.success(existingDownload)
                }
                activeInstance.activeDownloads.remove(gameId)
            }

            // Create DownloadInfo for progress tracking
            val downloadInfo =
                DownloadInfo(
                    jobCount = 1,
                    gameId = gameId.toIntOrNull() ?: 0,
                    downloadingAppIds = CopyOnWriteArrayList<Int>(),
                )

            // Track in activeDownloads first
            activeInstance.activeDownloads[gameId] = downloadInfo
            downloadInfo.setActive(true)
            downloadInfo.isCancelling = false
            downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)

            // Launch download in service scope so it runs independently
            val job =
                activeInstance.scope.launch {
                    try {
                        Timber.d("[Download] Starting download for game $gameId")
                        val commonRedistDir = File(effectiveInstallPath, "_CommonRedist")
                        Timber.tag("GOG").d("Will install dependencies to _CommonRedist")

                        val result =
                            activeInstance.gogDownloadManager.downloadGame(
                                gameId,
                                File(effectiveInstallPath),
                                downloadInfo,
                                containerLanguage,
                                true,
                                commonRedistDir,
                            )

                        if (result.isFailure) {
                            val error = result.exceptionOrNull()
                            when {
                                downloadInfo.isCancelling -> {
                                    Timber.i("[Download] Cancelled for game $gameId")
                                    downloadInfo.setActive(false)
                                    downloadInfo.updateStatus(DownloadPhase.CANCELLED)
                                }

                                !downloadInfo.isActive() -> {
                                    Timber.i("[Download] Paused for game $gameId")
                                    downloadInfo.setActive(false)
                                    downloadInfo.updateStatus(DownloadPhase.PAUSED)
                                }

                                else -> {
                                    Timber.e(error, "[Download] Failed for game $gameId")
                                    downloadInfo.setProgress(-1.0f)
                                    downloadInfo.setActive(false)
                                    downloadInfo.updateStatus(DownloadPhase.FAILED, error?.message ?: "Unknown error")
                                    SnackbarManager.show("Download failed: ${error?.message ?: "Unknown error"}")
                                }
                            }
                        } else {
                            Timber.i("[Download] Completed successfully for game $gameId")
                            downloadInfo.setProgress(1.0f)
                            downloadInfo.setActive(false)
                            downloadInfo.updateStatus(DownloadPhase.COMPLETE)

                            SnackbarManager.show("Download completed successfully!")
                        }
                    } catch (e: Exception) {
                        when {
                            downloadInfo.isCancelling -> {
                                Timber.i("[Download] Cancelled for game $gameId")
                                downloadInfo.setActive(false)
                                downloadInfo.updateStatus(DownloadPhase.CANCELLED)
                            }

                            !downloadInfo.isActive() -> {
                                Timber.i("[Download] Paused for game $gameId")
                                downloadInfo.setActive(false)
                                downloadInfo.updateStatus(DownloadPhase.PAUSED)
                            }

                            else -> {
                                Timber.e(e, "[Download] Exception for game $gameId")
                                downloadInfo.setProgress(-1.0f)
                                downloadInfo.setActive(false)
                                downloadInfo.updateStatus(DownloadPhase.FAILED, e.message ?: "Unknown error")
                                SnackbarManager.show("Download error: ${e.message ?: "Unknown error"}")
                            }
                        }
                    } finally {
                        val finalStatus = downloadInfo.getStatusFlow().value
                        if (finalStatus == DownloadPhase.COMPLETE || finalStatus == DownloadPhase.FAILED) {
                            activeInstance.activeDownloads.remove(gameId)
                        }
                        Timber.d(
                            "[Download] Finished for game $gameId, progress: ${downloadInfo.getProgress()}, active: ${downloadInfo.isActive()}",
                        )
                    }
                }
            downloadInfo.setDownloadJob(job)

            return Result.success(downloadInfo)
        }

        suspend fun refreshSingleGame(
            gameId: String,
            context: Context,
        ): Result<GOGGame?> =
            getInstance()?.gogManager?.refreshSingleGame(gameId, context)
                ?: Result.failure(Exception("Service not available"))

        /**
         * Delete/uninstall a GOG game
         * Delegates to GOGManager.deleteGame
         */
        suspend fun deleteGame(
            context: Context,
            libraryItem: LibraryItem,
        ): Result<Unit> =
            getInstance()?.gogManager?.deleteGame(context, libraryItem)
                ?: Result.failure(Exception("Service not available"))

        /**
         * Sync GOG cloud saves for a game
         * @param context Android context
         * @param appId Game app ID (e.g., "gog_123456")
         * @param preferredAction Preferred sync action: "download", "upload", or "none"
         * @return true if sync succeeded, false otherwise
         */
        suspend fun syncCloudSaves(
            context: Context,
            appId: String,
            preferredAction: String = "none",
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Timber.tag("GOG").d("[Cloud Saves] syncCloudSaves called for $appId with action: $preferredAction")

                    // Check if there's already a sync in progress for this appId
                    val serviceInstance = getInstance()
                    if (serviceInstance == null) {
                        Timber.tag("GOG").e("[Cloud Saves] Service instance not available for sync start")
                        return@withContext false
                    }

                    if (!serviceInstance.gogManager.startSync(appId)) {
                        Timber.tag("GOG").w("[Cloud Saves] Sync already in progress for $appId, skipping duplicate sync")
                        return@withContext false
                    }

                    try {
                        val instance = getInstance()
                        if (instance == null) {
                            Timber.tag("GOG").e("[Cloud Saves] Service instance not available")
                            return@withContext false
                        }

                        if (!GOGAuthManager.hasStoredCredentials(context)) {
                            Timber.tag("GOG").e("[Cloud Saves] Cannot sync saves: not authenticated")
                            return@withContext false
                        }

                        val authConfigPath = GOGAuthManager.getAuthConfigPath(context)
                        Timber.tag("GOG").d("[Cloud Saves] Using auth config path: $authConfigPath")

                        // Get game info
                        val gameId = ContainerUtils.extractGameIdFromContainerId(appId)
                        Timber.tag("GOG").d("[Cloud Saves] Extracted game ID: $gameId from appId: $appId")
                        val game = instance.gogManager.getGameFromDbById(gameId.toString())

                        if (game == null) {
                            Timber.tag("GOG").e("[Cloud Saves] Game not found for appId: $appId")
                            return@withContext false
                        }
                        Timber.tag("GOG").d("[Cloud Saves] Found game: ${game.title}")

                        // Get save directory paths (Android runs games through Wine, so always Windows)
                        Timber.tag("GOG").d("[Cloud Saves] Resolving save directory paths for $appId")
                        val saveLocations = instance.gogManager.getSaveDirectoryPath(context, appId, game.title)

                        if (saveLocations == null || saveLocations.isEmpty()) {
                            Timber.tag("GOG").w("[Cloud Saves] No save locations found for game $appId (cloud saves may not be enabled)")
                            return@withContext false
                        }
                        Timber.tag("GOG").i("[Cloud Saves] Found ${saveLocations.size} save location(s) for $appId")

                        var allSucceeded = true

                        // Sync each save location
                        for ((index, location) in saveLocations.withIndex()) {
                            try {
                                Timber
                                    .tag(
                                        "GOG",
                                    ).d("[Cloud Saves] Processing location ${index + 1}/${saveLocations.size}: '${location.name}'")

                                // Log directory state BEFORE sync
                                try {
                                    val saveDir = java.io.File(location.location)
                                    Timber.tag("GOG").d("[Cloud Saves] [BEFORE] Checking directory: ${location.location}")
                                    Timber
                                        .tag(
                                            "GOG",
                                        ).d(
                                            "[Cloud Saves] [BEFORE] Directory exists: ${saveDir.exists()}, isDirectory: ${saveDir.isDirectory}",
                                        )
                                    if (saveDir.exists() && saveDir.isDirectory) {
                                        val filesBefore = saveDir.listFiles()
                                        if (filesBefore != null && filesBefore.isNotEmpty()) {
                                            Timber.tag("GOG").i(
                                                "[Cloud Saves] [BEFORE] ${filesBefore.size} files in '${location.name}': ${filesBefore.joinToString(
                                                    ", ",
                                                ) {
                                                    it.name
                                                }}",
                                            )
                                        } else {
                                            Timber.tag("GOG").i("[Cloud Saves] [BEFORE] Directory '${location.name}' is empty")
                                        }
                                    } else {
                                        Timber.tag("GOG").i("[Cloud Saves] [BEFORE] Directory '${location.name}' does not exist yet")
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("GOG").e(e, "[Cloud Saves] [BEFORE] Failed to check directory")
                                }

                                // Get stored timestamp for this location
                                val timestampStr = instance.gogManager.getCloudSaveSyncTimestamp(appId, location.name)
                                val timestamp = timestampStr.toLongOrNull() ?: 0L

                                Timber
                                    .tag(
                                        "GOG",
                                    ).i(
                                        "[Cloud Saves] Syncing '${location.name}' for game $gameId (clientId: ${location.clientId}, path: ${location.location}, timestamp: $timestamp, action: $preferredAction)",
                                    )

                                // Validate clientSecret is available
                                if (location.clientSecret.isEmpty()) {
                                    Timber.tag("GOG").e("[Cloud Saves] Missing clientSecret for '${location.name}', skipping sync")
                                    continue
                                }

                                val cloudSavesManager = GOGCloudSavesManager(context)
                                val newTimestamp =
                                    cloudSavesManager.syncSaves(
                                        clientId = location.clientId,
                                        clientSecret = location.clientSecret,
                                        localPath = location.location,
                                        dirname = location.name,
                                        lastSyncTimestamp = timestamp,
                                        preferredAction = preferredAction,
                                    )

                                if (newTimestamp > 0) {
                                    // Success - store new timestamp
                                    instance.gogManager.setCloudSaveSyncTimestamp(appId, location.name, newTimestamp.toString())
                                    Timber.tag("GOG").d("[Cloud Saves] Updated timestamp for '${location.name}': $newTimestamp")

                                    // Log the save files in the directory after sync
                                    try {
                                        val saveDir = java.io.File(location.location)
                                        if (saveDir.exists() && saveDir.isDirectory) {
                                            val files = saveDir.listFiles()
                                            if (files != null && files.isNotEmpty()) {
                                                val fileList = files.joinToString(", ") { it.name }
                                                Timber
                                                    .tag(
                                                        "GOG",
                                                    ).i(
                                                        "[Cloud Saves] [$preferredAction] Files in '${location.name}': $fileList (${files.size} files)",
                                                    )

                                                // Log detailed file info
                                                files.forEach { file ->
                                                    val size = if (file.isFile) "${file.length()} bytes" else "directory"
                                                    Timber.tag("GOG").d("[Cloud Saves] [$preferredAction]   - ${file.name} ($size)")
                                                }
                                            } else {
                                                Timber
                                                    .tag(
                                                        "GOG",
                                                    ).w(
                                                        "[Cloud Saves] [$preferredAction] Directory '${location.name}' is empty at: ${location.location}",
                                                    )
                                            }
                                        } else {
                                            Timber
                                                .tag(
                                                    "GOG",
                                                ).w("[Cloud Saves] [$preferredAction] Directory not found: ${location.location}")
                                        }
                                    } catch (e: Exception) {
                                        Timber.tag("GOG").e(e, "[Cloud Saves] Failed to list files in directory: ${location.location}")
                                    }

                                    Timber
                                        .tag(
                                            "GOG",
                                        ).i("[Cloud Saves] Successfully synced save location '${location.name}' for game $gameId")
                                } else {
                                    Timber
                                        .tag(
                                            "GOG",
                                        ).e(
                                            "[Cloud Saves] Failed to sync save location '${location.name}' for game $gameId (timestamp: $newTimestamp)",
                                        )
                                    allSucceeded = false
                                }
                            } catch (e: Exception) {
                                Timber.tag("GOG").e(e, "[Cloud Saves] Exception syncing save location '${location.name}' for game $gameId")
                                allSucceeded = false
                            }
                        }

                        if (allSucceeded) {
                            Timber.tag("GOG").i("[Cloud Saves] All save locations synced successfully for $appId")
                            return@withContext true
                        } else {
                            Timber.tag("GOG").w("[Cloud Saves] Some save locations failed to sync for $appId")
                            return@withContext false
                        }
                    } finally {
                        // Always end the sync, even if an exception occurred
                        getInstance()?.gogManager?.endSync(appId)
                        Timber.tag("GOG").d("[Cloud Saves] Sync completed and lock released for $appId")
                    }
                } catch (e: Exception) {
                    Timber.tag("GOG").e(e, "[Cloud Saves] Failed to sync cloud saves for App ID: $appId")
                    return@withContext false
                }
            }
    }

    private lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var gogManager: GOGManager

    @Inject
    lateinit var gogDownloadManager: GOGDownloadManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track active downloads by game ID
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = { stop() }

    // GOGManager is injected by Hilt
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize notification helper for foreground service
        notificationHelper = NotificationHelper(applicationContext)
        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Timber.d("[GOGService] onStartCommand() - action: ${intent?.action}")

        // Start as foreground service
        val notification = notificationHelper.createForegroundNotification("Connected")
        startForeground(1, notification)

        // Determine if we should sync based on the action
        val shouldSync =
            when (intent?.action) {
                ACTION_MANUAL_SYNC -> {
                    Timber.i("[GOGService] Manual sync requested - bypassing throttle")
                    true
                }

                ACTION_SYNC_LIBRARY -> {
                    Timber.i("[GOGService] Automatic sync requested")
                    true
                }

                null -> {
                    // Service restarted by Android with null intent (START_STICKY behavior)
                    // Only sync if we haven't done initial sync yet, or if it's been a while
                    val timeSinceLastSync = System.currentTimeMillis() - lastSyncTimestamp
                    val shouldResync = !hasPerformedInitialSync || timeSinceLastSync >= SYNC_THROTTLE_MILLIS

                    if (shouldResync) {
                        Timber.i(
                            "[GOGService] Service restarted by Android - performing sync (hasPerformedInitialSync=$hasPerformedInitialSync, timeSinceLastSync=${timeSinceLastSync}ms)",
                        )
                        true
                    } else {
                        Timber.d("[GOGService] Service restarted by Android - skipping sync (throttled)")
                        false
                    }
                }

                else -> {
                    // Service started without sync action (e.g., just to keep it alive)
                    Timber.d("[GOGService] Service started without sync action")
                    false
                }
            }

        // Start background library sync if requested
        if (shouldSync && (backgroundSyncJob == null || backgroundSyncJob?.isActive != true)) {
            Timber.i("[GOGService] Starting background library sync")
            backgroundSyncJob?.cancel() // Cancel any existing job
            backgroundSyncJob =
                scope.launch {
                    try {
                        setSyncInProgress(true)
                        Timber.d("[GOGService]: Starting background library sync")

                        val syncResult = gogManager.startBackgroundSync(applicationContext)
                        if (syncResult.isFailure) {
                            Timber.w("[GOGService]: Failed to start background sync: ${syncResult.exceptionOrNull()?.message}")
                        } else {
                            Timber.i("[GOGService]: Background library sync completed successfully")
                            // Update last sync timestamp on successful sync
                            lastSyncTimestamp = System.currentTimeMillis()
                            // Mark that initial sync has been performed
                            hasPerformedInitialSync = true
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[GOGService]: Exception starting background sync")
                    } finally {
                        setSyncInProgress(false)
                    }
                }
        } else if (shouldSync) {
            Timber.d("[GOGService] Background sync already in progress, skipping")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
