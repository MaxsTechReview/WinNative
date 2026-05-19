package com.winlator.cmod.feature.stores.steam.service
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.widget.Toast
import androidx.room.withTransaction
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.app.db.PluviaDatabase
import com.winlator.cmod.app.db.download.DownloadRecord
import com.winlator.cmod.app.service.DownloadService
import com.winlator.cmod.app.service.NetworkMonitor
import com.winlator.cmod.app.service.download.DownloadCoordinator
import com.winlator.cmod.feature.shortcuts.LibraryShortcutUtils
import com.winlator.cmod.feature.stores.steam.data.AppInfo
import com.winlator.cmod.feature.stores.steam.data.CachedLicense
import com.winlator.cmod.feature.stores.steam.data.DepotInfo
import com.winlator.cmod.feature.stores.steam.data.DownloadFailedException
import com.winlator.cmod.feature.stores.steam.data.DownloadInfo
import com.winlator.cmod.feature.stores.steam.data.DownloadingAppInfo
import com.winlator.cmod.feature.stores.steam.data.EncryptedAppTicket
import com.winlator.cmod.feature.stores.steam.data.GameProcessInfo
import com.winlator.cmod.feature.stores.steam.data.LaunchInfo
import com.winlator.cmod.feature.stores.steam.data.ManifestInfo
import com.winlator.cmod.feature.stores.steam.data.OwnedGames
import com.winlator.cmod.feature.stores.steam.data.PostSyncInfo
import com.winlator.cmod.feature.stores.steam.data.SteamApp
import com.winlator.cmod.feature.stores.steam.data.SteamControllerConfigDetail
import com.winlator.cmod.feature.stores.steam.data.SteamFriend
import com.winlator.cmod.feature.stores.steam.data.SteamLicense
import com.winlator.cmod.feature.stores.steam.data.UserFileInfo
import com.winlator.cmod.feature.stores.steam.db.dao.AppInfoDao
import com.winlator.cmod.feature.stores.steam.db.dao.CachedLicenseDao
import com.winlator.cmod.feature.stores.steam.db.dao.ChangeNumbersDao
import com.winlator.cmod.feature.stores.steam.db.dao.DownloadingAppInfoDao
import com.winlator.cmod.feature.stores.steam.db.dao.EncryptedAppTicketDao
import com.winlator.cmod.feature.stores.steam.db.dao.FileChangeListsDao
import com.winlator.cmod.feature.stores.steam.db.dao.SteamAppDao
import com.winlator.cmod.feature.stores.steam.db.dao.SteamLicenseDao
import com.winlator.cmod.feature.stores.steam.enums.ControllerSupport
import com.winlator.cmod.feature.stores.steam.enums.DownloadPhase
import com.winlator.cmod.feature.stores.steam.enums.GameSource
import com.winlator.cmod.feature.stores.steam.enums.Language
import com.winlator.cmod.feature.stores.steam.enums.LoginResult
import com.winlator.cmod.feature.stores.steam.enums.Marker
import com.winlator.cmod.feature.stores.steam.enums.OS
import com.winlator.cmod.feature.stores.steam.enums.OSArch
import com.winlator.cmod.feature.stores.steam.enums.SaveLocation
import com.winlator.cmod.feature.stores.steam.enums.SyncResult
import com.auth0.android.jwt.JWT
import com.winlator.cmod.feature.stores.common.StoreAuthStatus
import com.winlator.cmod.feature.stores.common.StoreInstallPathSafety
import com.winlator.cmod.feature.stores.steam.events.AndroidEvent
import com.winlator.cmod.feature.stores.steam.events.SteamEvent
import com.winlator.cmod.feature.stores.steam.inventorygen.InventoryItemsGenerator
import com.winlator.cmod.feature.stores.steam.wnsteam.CaBundleExtractor
import com.winlator.cmod.feature.stores.steam.wnsteam.WnAuthCallback
import com.winlator.cmod.feature.stores.steam.wnsteam.WnDownloadListener
import com.winlator.cmod.feature.stores.steam.wnsteam.WnAuthResult
import com.winlator.cmod.feature.stores.steam.wnsteam.WnAuthenticator
import com.winlator.cmod.feature.stores.steam.wnsteam.WnLibraryStore
import com.winlator.cmod.feature.stores.steam.wnsteam.WnQrCallback
import com.winlator.cmod.feature.stores.steam.wnsteam.WnSteamSession
import com.winlator.cmod.feature.stores.steam.wnsteam.WnSteamStateObserver
import com.winlator.cmod.feature.stores.steam.workshop.WorkshopModsGenerator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import com.winlator.cmod.feature.stores.steam.statsgen.StatType
import com.winlator.cmod.feature.stores.steam.statsgen.StatsAchievementsGenerator
import com.winlator.cmod.feature.stores.steam.statsgen.VdfParser
import com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
import com.winlator.cmod.feature.stores.steam.utils.LicenseSerializer
import com.winlator.cmod.feature.stores.steam.utils.MarkerUtils
import com.winlator.cmod.feature.stores.steam.utils.Net
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.feature.stores.steam.utils.SteamUtils
import com.winlator.cmod.feature.stores.steam.utils.WnKeyValue
import com.winlator.cmod.feature.stores.steam.utils.generateSteamApp
import com.winlator.cmod.feature.steamcloudsync.SteamAutoCloud
import com.winlator.cmod.feature.sync.google.CloudSyncManager
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.system.GPUInformation
import com.winlator.cmod.shared.android.AppTerminationHelper
import com.winlator.cmod.shared.ui.toast.WinToast
import com.winlator.cmod.shared.android.NotificationHelper
import com.winlator.cmod.shared.io.StorageUtils
import dagger.hilt.android.AndroidEntryPoint
import com.winlator.cmod.feature.stores.steam.enums.EDepotFileFlag
import com.winlator.cmod.feature.stores.steam.enums.ELicenseFlags
import com.winlator.cmod.feature.stores.steam.enums.ELicenseType
import com.winlator.cmod.feature.stores.steam.enums.EPaymentMethod
import com.winlator.cmod.feature.stores.steam.enums.EOSType
import com.winlator.cmod.feature.stores.steam.enums.EPersonaState
import com.winlator.cmod.feature.stores.steam.enums.EResult
// Phase 9: the JavaSteam dependency has been fully removed. The Steam value
// types below are now in-house (steam.data / steam.utils).
import com.winlator.cmod.feature.stores.steam.data.AsyncJobFailedException
import com.winlator.cmod.feature.stores.steam.data.GamePlayedInfo
import com.winlator.cmod.feature.stores.steam.data.PICSRequest
import com.winlator.cmod.feature.stores.steam.data.SteamID
import com.winlator.cmod.feature.stores.steam.utils.KeyValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.NullPointerException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections
import java.util.Date
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class SteamService : Service() {
    @Inject
    lateinit var db: PluviaDatabase

    @Inject
    lateinit var licenseDao: SteamLicenseDao

    @Inject
    lateinit var appDao: SteamAppDao

    @Inject
    lateinit var changeNumbersDao: ChangeNumbersDao

    @Inject
    lateinit var appInfoDao: AppInfoDao

    @Inject
    lateinit var fileChangeListsDao: FileChangeListsDao

    @Inject
    lateinit var cachedLicenseDao: CachedLicenseDao

    @Inject
    lateinit var encryptedAppTicketDao: EncryptedAppTicketDao

    @Inject
    lateinit var downloadingAppInfoDao: DownloadingAppInfoDao

    private lateinit var notificationHelper: NotificationHelper

    private var _unifiedFriends: SteamUnifiedFriends? = null

    private var _loginResult: LoginResult = LoginResult.Failed

    private var retryAttempt = 0

    // Auto-reconnect coroutine for the C++ WN-Steam-Client session (Phase 9).
    @Volatile private var connectJob: Job? = null

    // Pending backoff-delayed reconnect scheduled by onWnDisconnected.
    @Volatile private var reconnectJob: Job? = null

    // Watches a freshly logged-on session: only once it has stayed up for
    // STABLE_CONNECTION_MS is the retry budget (retryAttempt) reset to 0.
    // A connection that logs on then drops within that window is NOT
    // healthy — resetting immediately let a flapping connection reconnect
    // without bound (the cause of the backgrounded-app battery drain).
    @Volatile private var stableConnectionJob: Job? = null

    // App-lifecycle gating for the Steam session. While the app is
    // backgrounded with nothing that needs Steam (no active download, no
    // running game) the session is suspended — disconnected, all reconnect
    // / PICS loops cancelled — so it draws no power. It wakes when the user
    // reopens the app. Driven from PluviaApp's activity-lifecycle callbacks.
    @Volatile private var appInForeground = true
    @Volatile private var suspendedForBackground = false

    // Cancellable timer that defers the background suspend decision by
    // BACKGROUND_IDLE_GRACE_MS — see scheduleBackgroundSuspendCheck.
    @Volatile private var backgroundIdleJob: Job? = null

    private val appPicsChannel =
        Channel<List<PICSRequest>>(
            capacity = 1_000,
            onBufferOverflow = BufferOverflow.SUSPEND,
            onUndeliveredElement = { droppedApps ->
                Timber.w("App PICS Channel dropped: ${droppedApps.size} apps")
            },
        )

    private val packagePicsChannel =
        Channel<List<PICSRequest>>(
            capacity = 1_000,
            onBufferOverflow = BufferOverflow.SUSPEND,
            onUndeliveredElement = { droppedPackages ->
                Timber.w("Package PICS Channel dropped: ${droppedPackages.size} packages")
            },
        )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = {
        Companion.stop()
    }

    // The current shared family group the logged in user is joined to.
    private var familyGroupMembers: ArrayList<Int> = arrayListOf()

    private val appTokens: ConcurrentHashMap<Int, Long> = ConcurrentHashMap()

    // Add these as class properties
    private var picsGetProductInfoJob: Job? = null
    private var picsChangesCheckerJob: Job? = null
    private var friendCheckerJob: Job? = null

    private val _isPlayingBlocked = MutableStateFlow(false)
    val isPlayingBlocked = _isPlayingBlocked.asStateFlow()

    // Cache in-memory the local persona state.
    private val _localPersona =
        MutableStateFlow(
            SteamFriend(name = PrefManager.steamUserName, avatarHash = PrefManager.steamUserAvatarHash),
        )
    val localPersona = _localPersona.asStateFlow()

    data class ManifestSizes(
        val installSize: Long = 0L,
        val downloadSize: Long = 0L,
    )

    data class SteamUpdateInfo(
        val hasUpdate: Boolean = false,
        val downloadSize: Long = 0L,
        val depotIds: List<Int> = emptyList(),
        val message: String? = null,
    )

    companion object {
        const val MAX_PICS_BUFFER = 256

        const val MAX_RETRY_ATTEMPTS = 20

        // A session must stay logged on this long before its reconnect is
        // considered successful and the retry budget is reset.
        private const val STABLE_CONNECTION_MS = 60_000L

        // Reconnect backoff cap — even a permanently-flapping connection
        // reconnects no more than once per this interval.
        private const val RECONNECT_BACKOFF_CAP_MS = 5 * 60_000L

        // connectAndLogon gives up after this many consecutive failed
        // bring-up attempts (with exponential backoff between them) rather
        // than retrying a doomed logon forever.
        private const val CONNECT_LOGON_MAX_ATTEMPTS = 8

        // Grace period after the app is backgrounded before the Steam session
        // is allowed to suspend. A brief app-switch (well under this) never
        // disconnects the session, so it isn't forced to reconnect on return —
        // that disconnect/reconnect thrash is the unnecessary battery drain.
        // While a connection-critical operation is running the suspend check
        // simply repeats once per interval until the work is done.
        private const val BACKGROUND_IDLE_GRACE_MS = 60_000L

        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_PKG_ID: Int = Int.MAX_VALUE
        private const val STEAM_CONTROLLER_CONFIG_FILENAME = "steam_controller_config.vdf"
        private const val DOWNLOAD_INFO_DIR = ".DownloadInfo"
        private const val DOWNLOAD_INFO_FILE = "depot_bytes.json"
        private const val LEGACY_DOWNLOAD_INFO_FILE = "bytes_downloaded.txt"
        private const val COMPONENTS_BASE_URL = "https://github.com/maxjivi05/Components/releases/download/Components"
        @Volatile
        private var startupMetadataRepairJob: Job? = null

        /**
         * Default timeout to use when making requests
         */
        var requestTimeout = 30.seconds

        /**
         * Default timeout to use when reading the response body
         */
        var responseTimeout = 120.seconds


        internal var instance: SteamService? = null

        var cachedAchievements: List<com.winlator.cmod.feature.stores.steam.statsgen.Achievement>? = null
            private set
        var cachedAchievementsAppId: Int? = null
            private set

        fun clearCachedAchievements() {
            cachedAchievements = null
            cachedAchievementsAppId = null
        }

        private fun downloadUrlsFor(fileName: String): List<String> {
            val alternate =
                when (fileName) {
                    "steam-token.tzst" -> "steam-token-r2.tzst"
                    else -> null
                }
            return if (alternate != null) {
                listOf(
                    "$COMPONENTS_BASE_URL/$fileName",
                    "$COMPONENTS_BASE_URL/$alternate",
                )
            } else {
                listOf("$COMPONENTS_BASE_URL/$fileName")
            }
        }

        fun pauseAll() {
            DownloadCoordinator.runOnScope { DownloadCoordinator.pauseAll() }
        }

        fun pauseDownload(appId: Int) {
            DownloadCoordinator.runOnScope {
                DownloadCoordinator.pause(DownloadRecord.STORE_STEAM, appId.toString())
            }
        }

        fun resumeAll() {
            DownloadCoordinator.runOnScope { DownloadCoordinator.resumeAll() }
        }

        fun resumeDownload(appId: Int) {
            DownloadCoordinator.runOnScope {
                DownloadCoordinator.resume(DownloadRecord.STORE_STEAM, appId.toString())
            }
        }

        fun cancelAll() {
            DownloadCoordinator.runOnScope { DownloadCoordinator.cancelAll() }
        }

        fun cancelDownload(appId: Int) {
            DownloadCoordinator.runOnScope {
                DownloadCoordinator.cancel(DownloadRecord.STORE_STEAM, appId.toString())
            }
        }

        // The cross-store DownloadCoordinator now owns global queue draining. This legacy
        // entry point is kept for binary compatibility with any callers that haven't been
        // migrated; instead of running the old Steam-only queue logic (which would race the
        // coordinator and double-start downloads) it just delegates to the coordinator.
        fun checkQueue() {
            DownloadCoordinator.blockingTick()
        }

        private val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()

        private fun notifyDownloadStarted(appId: Int) {
            PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, true))
        }

        private fun notifyDownloadStopped(appId: Int) {
            PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
        }

        private fun removeDownloadJob(
            appId: Int,
            forceRemove: Boolean = false,
        ) {
            if (forceRemove) {
                val removed = downloadJobs.remove(appId)
                if (removed != null) {
                    notifyDownloadStopped(appId)
                }
            } else {
                notifyDownloadStopped(appId)
            }
            checkQueue()
            Unit
        }

        fun clearCompletedDownloads() {
            clearCompletedDownloadsInternal(dispatchQueueAfterClear = true)
            // Also remove finished records from the cross-store coordinator table.
            DownloadCoordinator.runOnScope { DownloadCoordinator.clear() }
        }

        fun clearCompletedDownloadsForShutdown() {
            clearCompletedDownloadsInternal(dispatchQueueAfterClear = false)
        }

        private fun clearCompletedDownloadsInternal(dispatchQueueAfterClear: Boolean) {
            val toRemove =
                downloadJobs
                    .filterValues {
                        val status = it.getStatusFlow().value
                        status == DownloadPhase.COMPLETE ||
                            status == DownloadPhase.CANCELLED ||
                            status == DownloadPhase.FAILED
                    }.keys
            toRemove.forEach { appId ->
                val removed = downloadJobs.remove(appId)
                if (removed != null) {
                    notifyDownloadStopped(appId)
                }
            }
            if (dispatchQueueAfterClear && toRemove.isNotEmpty()) {
                checkQueue()
            }
        }

        /** Returns true if there is an incomplete download on disk (in-progress marker or actively downloading). */
        private fun hasPartialDownloadFiles(appDirPath: String): Boolean {
            val appDir = File(appDirPath)
            if (!appDir.exists()) return false

            val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
            if (persistenceFile.exists() && persistenceFile.length() > 0L) {
                return true
            }

            // If a complete install marker exists and there is no persisted resume file,
            // treat this as fully installed (not a resumable partial download).
            if (MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)) {
                return false
            }

            // Check for in-progress marker (this fork's convention)
            if (MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) {
                return true
            }

            val rootFiles = appDir.listFiles() ?: return false
            return rootFiles.any { file ->
                if (file.name != DOWNLOAD_INFO_DIR) {
                    true
                } else {
                    val nestedFiles = file.listFiles().orEmpty()
                    nestedFiles.any { nested ->
                        nested.name != DOWNLOAD_INFO_FILE && nested.name != LEGACY_DOWNLOAD_INFO_FILE
                    }
                }
            }
        }

        private fun inferResumeDlcAppIds(
            appId: Int,
            appDirPath: String,
        ): List<Int> {
            // Try to recover selected DLCs from persisted depot progress when metadata row is missing.
            return runCatching {
                val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
                if (!persistenceFile.exists() || !persistenceFile.canRead()) return@runCatching emptyList()

                val text = persistenceFile.readText().trim()
                if (text.isEmpty()) return@runCatching emptyList()

                val persistedDepotIds = mutableSetOf<Int>()
                val json = JSONObject(text)
                for (key in json.keys()) {
                    val depotId = key.toIntOrNull() ?: continue
                    persistedDepotIds.add(depotId)
                }
                if (persistedDepotIds.isEmpty()) return@runCatching emptyList()

                val context = instance!!.applicationContext
                val container =
                    if (ContainerUtils.hasContainer(context, "STEAM_$appId")) {
                        ContainerUtils.getContainer(context, "STEAM_$appId")
                    } else {
                        null
                    }
                val containerLanguage = container?.language ?: PrefManager.containerLanguage
                val depots = getDownloadableDepots(appId = appId, preferredLanguage = containerLanguage)
                depots
                    .asSequence()
                    .filter { (depotId, _) -> depotId in persistedDepotIds }
                    .map { (_, depot) -> depot.dlcAppId }
                    .filter { it != INVALID_APP_ID }
                    .distinct()
                    .toList()
            }.getOrElse {
                emptyList()
            }
        }

        private fun hasPersistedDepotResumeMetadata(appDirPath: String): Boolean {
            return runCatching {
                val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
                if (!persistenceFile.exists() || !persistenceFile.canRead()) return@runCatching false

                val text = persistenceFile.readText().trim()
                if (text.isEmpty()) return@runCatching false

                val json = JSONObject(text)
                json.keys().asSequence().any { key -> key.toIntOrNull() != null }
            }.getOrElse {
                false
            }
        }

        private fun clearPersistedProgressSnapshot(appDirPath: String) {
            val persistenceDir = File(appDirPath, DOWNLOAD_INFO_DIR)
            val persistenceFile = File(persistenceDir, DOWNLOAD_INFO_FILE)
            if (persistenceFile.exists()) {
                persistenceFile.delete()
            }
            val legacyFile = File(persistenceDir, LEGACY_DOWNLOAD_INFO_FILE)
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
            if (persistenceDir.exists() && persistenceDir.list().isNullOrEmpty()) {
                persistenceDir.delete()
            }
        }

        private fun clearFailedResumeState(appId: Int) {
            val appDirPath = getAppDirPath(appId)
            clearPersistedProgressSnapshot(appDirPath)
            runBlocking(Dispatchers.IO) {
                instance?.downloadingAppInfoDao?.deleteApp(appId)
            }
        }

        private fun deleteRecursivelyWithRetries(
            target: File,
            maxAttempts: Int = 5,
            delayMs: Long = 250L,
        ): Boolean {
            if (!target.exists()) return true

            repeat(maxAttempts) {
                if (target.deleteRecursively()) return true
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return !target.exists()
                }
            }

            return !target.exists()
        }

        private fun cleanupSteamAppCacheDirs(appId: Int) {
            steamAppCacheDirs(appId).forEach { dir ->
                if (!dir.exists()) return@forEach
                Timber.i("Deleting Steam cache folder for appId $appId: ${dir.absolutePath}")
                if (!deleteRecursivelyWithRetries(dir)) {
                    Timber.w("Failed to fully delete Steam cache folder for appId $appId: ${dir.absolutePath}")
                }
            }
        }

        private fun steamAppCacheDirs(appId: Int): List<File> {
            val appIdString = appId.toString()
            val dirs = linkedMapOf<String, File>()

            fun addDir(dir: File) {
                val normalized =
                    try {
                        dir.canonicalFile
                    } catch (_: IOException) {
                        dir.absoluteFile
                    }
                dirs[normalized.path] = normalized
            }

            fun addSteamAppsRoot(root: File) {
                addDir(File(root, "staging/$appIdString"))
                addDir(File(root, "shadercache/$appIdString"))
            }

            fun addInstallRoot(installRoot: String) {
                if (installRoot.isBlank()) return
                val root = File(installRoot)
                val steamAppsRoot =
                    if (root.name.equals("common", ignoreCase = true)) {
                        root.parentFile ?: root
                    } else {
                        root
                    }
                addSteamAppsRoot(steamAppsRoot)
            }

            addDir(File(defaultAppStagingPath, appIdString))
            if (defaultStoragePath.isNotBlank()) {
                addDir(File(defaultStoragePath, "Steam/steamapps/shadercache/$appIdString"))
            }

            addInstallRoot(internalAppInstallPath)
            addInstallRoot(externalAppInstallPath)
            addInstallRoot(defaultAppInstallPath)
            allInstallPaths.forEach(::addInstallRoot)

            return dirs.values.toList()
        }

        private fun steamProtectedInstallRoots(): List<String> =
            listOf(
                internalAppInstallPath,
                externalAppInstallPath,
                defaultAppInstallPath,
            ).filter { it.isNotBlank() }.distinct()

        fun hasPartialDownload(appId: Int): Boolean {
            if (isAppInstalled(appId)) return false

            val appDirPath = getAppDirPath(appId)
            val downloadingApp = getDownloadingAppInfoOf(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasPartialFiles = hasPartialDownloadFiles(appDirPath)
            val hasPersistedMetadata = hasPersistedDepotResumeMetadata(appDirPath)
            val isResumable =
                if (hasCompleteMarker) {
                    downloadingApp != null || hasPersistedMetadata
                } else {
                    hasPartialFiles
                }

            if (isResumable) {
                return true
            }

            if (downloadingApp != null) {
                runBlocking(Dispatchers.IO) {
                    instance?.downloadingAppInfoDao?.deleteApp(appId)
                }
            }

            if (hasCompleteMarker && !hasPersistedMetadata) {
                clearPersistedProgressSnapshot(appDirPath)
            }

            return false
        }

        private val syncInProgressApps = ConcurrentHashMap<Int, AtomicBoolean>()

        private fun getSyncFlag(appId: Int): AtomicBoolean {
            val existing = syncInProgressApps[appId]
            if (existing != null) {
                return existing
            }
            val created = AtomicBoolean(false)
            val prior = syncInProgressApps.putIfAbsent(appId, created)
            return prior ?: created
        }

        private fun tryAcquireSync(appId: Int): Boolean {
            val flag = getSyncFlag(appId)
            return flag.compareAndSet(false, true)
        }

        private fun releaseSync(appId: Int) {
            val flag = syncInProgressApps[appId]
            flag?.set(false)
            if (flag != null && !flag.get()) {
                syncInProgressApps.remove(appId, flag)
            }
        }

        // Track whether a game is currently running to prevent premature service stop
        @JvmStatic
        @Volatile
        var keepAlive: Boolean = false

        data class CloudSyncMessage(
            val appId: Int,
            val isUpload: Boolean,
            val message: String,
            val progress: Float,
        )

        val cloudSyncStatus = MutableStateFlow<CloudSyncMessage?>(null)

        @Volatile
        var isImporting: Boolean = false

        var isStopping: Boolean = false
            private set
        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow = _isConnectedFlow.asStateFlow()

        /**
         * Pure getter over [isConnectedFlow]. Do not read `steamClient.isConnected` here —
         * concurrent readers were mutating the flow as a side-effect, producing UI flicker
         * during CM reconnect gaps. Callbacks (`onConnected` / `onDisconnected` / `clearValues`)
         * are the only authoritative writers of the flow.
         */
        var isConnected: Boolean
            get() = _isConnectedFlow.value
            private set(value) {
                _isConnectedFlow.value = value
            }

        var isRunning: Boolean = false
            private set
        var isLoggingOut: Boolean = false
            private set

        private val _isLoggedInFlow = MutableStateFlow(false)
        val isLoggedInFlow = _isLoggedInFlow.asStateFlow()

        /**
         * Pure getter over [isLoggedInFlow]. Previously this read `steamClient.steamID.isValid`
         * and wrote the flow as a side-effect, which caused UI flicker whenever any caller
         * (StoresFragment.onResume, CloudSyncManager.rehydrateSteamSession, the 10s poll) read
         * the value during a transient CM disconnect. The flow is now only mutated by
         * authoritative sources: initLoginStatus(), onLoggedOn, onLoggedOff, logOut, clearValues.
         */
        val isLoggedIn: Boolean
            get() = !isLoggingOut && _isLoggedInFlow.value

        var isWaitingForQRAuth: Boolean = false
            private set

        // Active WnSteamSession for the in-flight credentials / QR auth
        // flow. Held in the companion so stopLoginWithQr() can cancel
        // from anywhere; cleared on success (when ownership moves to
        // wnSession) or on failure (when bringUpWnSession's finally
        // disconnects it).
        private var wnAuthSession: WnSteamSession? = null

        // Long-lived WnSteamSession that carries the post-logon CM
        // connection — the sole Steam connection (Phase 9). Owns the session
        // from the point the refresh token is acquired through logout.
        // @Volatile because logOut() reads from UI thread while the auth
        // flow writes from Dispatchers.IO.
        @Volatile private var wnSession: WnSteamSession? = null

        // True once the post-logon orchestration (onWnLoggedOn) has run for the
        // current wnSession. Reset on disconnect / teardown so a reconnect
        // re-runs it. Guards against the state observer double-firing.
        @Volatile private var wnLoggedOnHandled = false

        // Serializes WN-Steam-Client session bring-up. Without this, several
        // post-logon callers (requestUserPersona, setPersonaState, PICS, …)
        // each race into bringUpWnSession() and spin up *separate* CM
        // sessions; Steam allows only one session per account-instance, so
        // they kick each other (ClientLoggedOff eresult=34) in a cascade and
        // every reply-bearing request is lost. Only the bring-up is gated —
        // reuse of an already-logged-on session stays lock-free.
        private val wnSessionBringUpMutex = kotlinx.coroutines.sync.Mutex()

        /**
         * Live Kotlin facade over wnSession's native library store. Created
         * alongside the session in startLoginWith{Credentials,Qr} and torn
         * down by teardownPriorWnSession() / logOut(). Consumers (UI / Phase
         * 9 SteamApps replacement) collect `snapshots` to observe library
         * changes; `current` is the latest one-shot value.
         */
        @Volatile var wnLibrary: WnLibraryStore? = null
            private set

        /**
         * Tears down any prior long-lived WnSteamSession. Called at the
         * top of every login entry so a retry doesn't leak the previous
         * native handle (transport thread + heartbeat + TLS socket).
         */
        private fun teardownPriorWnSession() {
            val prior = wnSession
            wnSession = null
            wnLoggedOnHandled = false
            wnLibrary?.stopObserving()
            wnLibrary = null
            if (prior != null) {
                Timber.i("Tearing down prior wnSession before relogin")
                try { prior.disconnect() } catch (_: Throwable) {}
                try { prior.close()      } catch (_: Throwable) {}
            }
        }

        /**
         * Keeps [isConnectedFlow] in sync with the live socket state. Previously also wrote
         * [isLoggedInFlow] from `steamID.isValid`, which flipped the UI to "signed out"
         * whenever Valve's CM load-balanced us. The login flow is now purely callback-driven
         * (see [isLoggedIn] docs), so this method only touches the connected flow.
         */
        fun syncStates() {
            // Connected == the C++ WN-Steam-Client channel is up (state >= 2).
            val connected = (wnSession?.state() ?: 0) >= 2
            if (connected != _isConnectedFlow.value) _isConnectedFlow.value = connected
        }

        /**
         * Checks if the user has stored Steam credentials (refresh token).
         * Used to determine if auto-reconnection should be attempted on app start.
         */
        fun hasStoredCredentials(context: Context): Boolean {
            PrefManager.init(context)
            return PrefManager.refreshToken.isNotBlank()
        }

        /**
         * Classifies the current Steam session using the same [StoreAuthStatus] model Epic
         * uses. Lets the UI distinguish "reconnecting" / "token expired" / "no login" rather
         * than painting every non-ACTIVE state as "signed out."
         *
         * - LOGGED_OUT: no stored refresh token.
         * - EXPIRED:    refresh-token JWT's `exp` claim is in the past (~200 days old).
         * - ACTIVE:     [isLoggedInFlow] is true (JavaSteam callback confirmed login).
         * - REFRESHABLE: have a valid-looking refresh token but not yet logged on — the
         *                 service is still connecting, or we're mid-reconnect after a CM bounce.
         * - UNKNOWN:    refresh token exists but can't be parsed as a JWT.
         */
        fun getAuthStatus(context: Context): StoreAuthStatus {
            PrefManager.init(context)
            val refreshToken = PrefManager.refreshToken
            if (refreshToken.isBlank()) return StoreAuthStatus.LOGGED_OUT

            val jwtExpired: Boolean? =
                try {
                    JWT(refreshToken).isExpired(0)
                } catch (_: Exception) {
                    null
                }
            if (jwtExpired == true) return StoreAuthStatus.EXPIRED

            if (!isLoggingOut && _isLoggedInFlow.value) return StoreAuthStatus.ACTIVE

            return if (jwtExpired == null) StoreAuthStatus.UNKNOWN else StoreAuthStatus.REFRESHABLE
        }

        /**
         * Pre-seeds the login flow with stored credential state so the UI
         * doesn't flash a "sign in" prompt while the service is connecting.
         */
        fun initLoginStatus(context: Context) {
            if (!isLoggingOut) {
                _isLoggedInFlow.value = hasStoredCredentials(context)
            }
        }


        val internalAppInstallPath: String
            get() = Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "common").pathString

        val externalAppInstallPath: String
            get() = Paths.get(PrefManager.externalStoragePath, "Steam", "steamapps", "common").pathString

        val allInstallPaths: List<String>
            get() {
                val paths = mutableListOf(internalAppInstallPath)
                if (PrefManager.externalStoragePath.isNotBlank()) {
                    paths += externalAppInstallPath
                }
                for (volumePath in DownloadService.externalVolumePaths) {
                    if (volumePath.isNotBlank()) {
                        paths += Paths.get(volumePath, "Steam", "steamapps", "common").pathString
                    }
                }
                return paths.distinct()
            }

        private val internalAppStagingPath: String
            get() {
                return Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "staging").pathString
            }
        private val externalAppStagingPath: String
            get() {
                return Paths.get(PrefManager.externalStoragePath, "Steam", "steamapps", "staging").pathString
            }

        val defaultStoragePath: String
            get() {
                return if (PrefManager.useExternalStorage && File(PrefManager.externalStoragePath).exists()) {
                    // We still have an SD card file structure as expected
                    Timber.i("External storage path is " + PrefManager.externalStoragePath)
                    PrefManager.externalStoragePath
                } else {
                    if (instance != null) {
                        return DownloadService.baseDataDirPath
                    }
                    return ""
                }
            }

        val defaultAppInstallPath: String
            get() {
                val context = PluviaApp.instance.applicationContext ?: return internalAppInstallPath
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir =
                        com.winlator.cmod.shared.io.FileUtils
                            .getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    Timber.i("defaultAppInstallPath: resolved baseDir $baseDir from URI $storeDefaultUri")
                    if (baseDir != null) return baseDir
                }

                return if (PrefManager.useExternalStorage && File(PrefManager.externalStoragePath).exists()) {
                    // We still have an SD card file structure as expected
                    Timber.i("Using external storage")
                    Timber.i("install path for external storage is " + externalAppInstallPath)
                    externalAppInstallPath
                } else {
                    Timber.i("Using internal storage")
                    internalAppInstallPath
                }
            }

        val defaultAppStagingPath: String
            get() {
                val context = PluviaApp.instance.applicationContext ?: return internalAppStagingPath
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir =
                        com.winlator.cmod.shared.io.FileUtils
                            .getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    if (baseDir != null) return Paths.get(baseDir, "staging").pathString
                }

                return if (PrefManager.useExternalStorage) {
                    externalAppStagingPath
                } else {
                    internalAppStagingPath
                }
            }

        val userSteamId: SteamID?
            get() {
                // Phase 9: identity comes from the C++ WN-Steam-Client session;
                // fall back to the persisted SteamID64 during a reconnect gap.
                val live = wnSession?.steamId()?.takeIf { it != 0L }
                val id = live ?: PrefManager.steamUserSteamId64.takeIf { it != 0L }
                return id?.let { SteamID(it) }
            }

        val familyMembers: List<Int>
            get() = instance?.familyGroupMembers ?: emptyList()

        val isLoginInProgress: Boolean
            get() = instance?._loginResult == LoginResult.InProgress

        suspend fun setPersonaState(state: EPersonaState) =
            withContext(Dispatchers.IO) {
                PrefManager.personaState = state.code()
                // Publish persona state via the C++ WN-Steam-Client (Phase 9).
                withWnSession { session -> session.setPersonaState(state.code()) }
                // Reflect the change locally — Steam does not echo our own
                // persona state back to us, so the UI (status drawer) would
                // otherwise stay stale until the next requestUserPersona().
                instance?._localPersona?.update { it.copy(state = state) }
                instance?.localPersona?.value?.let {
                    PluviaApp.events.emit(SteamEvent.PersonaStateReceived(it))
                }
            }

        suspend fun requestUserPersona() =
            withContext(Dispatchers.IO) {
                // Fetch the local user's persona via the C++ WN-Steam-Client
                // (Phase 9). CMsgClientRequestFriendData is sent; the
                // CMsgClientPersonaState reply is server-pushed and cached —
                // poll getSelfPersona() for it.
                val svc = instance ?: return@withContext
                val json =
                    withWnSession { session ->
                        session.requestUserPersona()
                        var j: String? = null
                        for (i in 0 until 25) {
                            j = session.getSelfPersona()
                            if (j != null) break
                            delay(200)
                        }
                        j
                    } ?: return@withContext
                try {
                    val obj = JSONObject(json)
                    val avatarHash = obj.optString("avatarHash")
                    val playerName = obj.optString("playerName")
                    val gameAppId = obj.optInt("gameAppId")
                    svc._localPersona.update {
                        it.copy(
                            avatarHash = avatarHash.ifEmpty { it.avatarHash },
                            name = playerName.ifEmpty { it.name },
                            state = EPersonaState.from(obj.optInt("personaState")) ?: EPersonaState.Offline,
                            gameAppID = gameAppId,
                            gameName = svc.appDao.findApp(gameAppId)?.name
                                ?: obj.optString("gameName"),
                        )
                    }
                    if (avatarHash.isNotEmpty()) PrefManager.steamUserAvatarHash = avatarHash
                    if (playerName.isNotEmpty()) PrefManager.steamUserName = playerName
                    PluviaApp.events.emit(SteamEvent.PersonaStateReceived(svc.localPersona.value))
                    Timber.i("user persona via wn-steam-client: name='$playerName'")
                } catch (e: Exception) {
                    Timber.w(e, "requestUserPersona: persona parse failed")
                }
            }

        suspend fun getSelfCurrentlyPlayingAppId(): Int? =
            withContext(Dispatchers.IO) {
                val self = instance?.localPersona?.value ?: return@withContext null
                if (self.isPlayingGame) self.gameAppID else null
            }

        suspend fun kickPlayingSession(onlyGame: Boolean = true): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    instance?._isPlayingBlocked?.value = true
                    // Kick + wait via the C++ WN-Steam-Client (Phase 9). The
                    // server-pushed CMsgClientPlayingSessionState updates the
                    // C++ playing-blocked cache; poll it for the unblock.
                    val cleared = withWnSession { session ->
                        // Invalidate the C++ playing-blocked cache before the
                        // kick: the shared session is reused across calls, so
                        // the loop must only observe a *post-kick* server push.
                        session.markPlayingBlocked()
                        session.kickPlayingSession(onlyGame)
                        val deadline = System.currentTimeMillis() + 5000
                        var ok = false
                        while (System.currentTimeMillis() < deadline) {
                            delay(100)
                            if (!session.isPlayingBlocked()) { ok = true; break }
                        }
                        ok
                    } == true
                    instance?._isPlayingBlocked?.value = !cleared
                    cleared
                } catch (_: Exception) {
                    false
                }
            }

        /**
         * Get licenses from database for use with DepotDownloader
         */
        // The single caller only needs to know whether any licenses exist,
        // so this returns the raw cached rows (Phase 9 — there is no longer a
        // JavaSteam License object to deserialize into).
        suspend fun getLicensesFromDb(): List<CachedLicense> =
            withContext(Dispatchers.IO) {
                instance?.cachedLicenseDao?.getAll() ?: emptyList()
            }

        fun getPkgInfoOf(appId: Int): SteamLicense? =
            runBlocking(Dispatchers.IO) {
                instance?.licenseDao?.findLicense(
                    instance?.appDao?.findApp(appId)?.packageId ?: INVALID_PKG_ID,
                )
            }

        fun getAppInfoOf(appId: Int): SteamApp? = runBlocking(Dispatchers.IO) { instance?.appDao?.findApp(appId) }

        fun getDownloadingAppInfoOf(appId: Int): DownloadingAppInfo? =
            runBlocking(Dispatchers.IO) {
                instance?.downloadingAppInfoDao?.getDownloadingApp(appId)
            }

        fun getDownloadableDlcAppsOf(appId: Int): List<SteamApp>? =
            runBlocking(Dispatchers.IO) { instance?.appDao?.findDownloadableDLCApps(appId) }

        fun getSelectableDlcAppsOf(appId: Int): List<SteamApp> =
            runBlocking(Dispatchers.IO) {
                val service = instance ?: return@runBlocking emptyList()
                val appInfo = service.appDao.findApp(appId) ?: return@runBlocking emptyList()
                val preferredLanguage = PrefManager.containerLanguage
                val has64Bit =
                    appInfo.depots.values.any {
                        it.osArch == OSArch.Arch64 &&
                            (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
                    }

                val mainAppDlcIds =
                    appInfo.depots.values
                        .asSequence()
                        .filter { depot ->
                            depot.dlcAppId != INVALID_APP_ID &&
                                filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc = null)
                        }.map { it.dlcAppId }

                val indirectDlcApps = service.appDao.findDownloadableDLCApps(appId).orEmpty()
                val hiddenDlcApps = service.appDao.findHiddenDLCApps(appId).orEmpty()
                val dlcAppsById = (indirectDlcApps + hiddenDlcApps).associateBy { it.id }
                val indirectDlcIds = indirectDlcApps.map { it.id }.asSequence()
                val hiddenDlcIds = hiddenDlcApps.map { it.id }.asSequence()
                val groupedBaseDlcIds =
                    getGroupedBaseAppDlcIds(
                        appInfo = appInfo,
                        preferredLanguage = preferredLanguage,
                        has64Bit = has64Bit,
                    ).asSequence()

                val declaredDlcIds = appInfo.dlcAppIds.asSequence()

                val selectableDlcIds = (mainAppDlcIds + groupedBaseDlcIds + indirectDlcIds + hiddenDlcIds + declaredDlcIds).distinct().toList()

                if (selectableDlcIds.isEmpty()) return@runBlocking emptyList()

                selectableDlcIds
                    .mapNotNull { dlcAppId ->
                        val dlcApp = service.appDao.findApp(dlcAppId) ?: dlcAppsById[dlcAppId]
                        dlcApp?.takeIf { it.name.isNotBlank() }
                    }
                    .sortedBy { it.name.lowercase() }
            }

        fun getHiddenDlcAppsOf(appId: Int): List<SteamApp>? = runBlocking(Dispatchers.IO) { instance?.appDao?.findHiddenDLCApps(appId) }

        fun getInstalledApp(appId: Int): AppInfo? = runBlocking(Dispatchers.IO) { instance?.appInfoDao?.getInstalledApp(appId) }

        fun getInstalledDepotsOf(appId: Int): List<Int>? = getTrustedInstalledAppInfo(appId)?.downloadedDepots

        fun getInstalledDlcDepotsOf(appId: Int): List<Int>? {
            val installedApp = getTrustedInstalledAppInfo(appId)
            val installedDlcAppIds = installedApp?.dlcDepots.orEmpty().toMutableSet()
            installedDlcAppIds.addAll(getInstalledSelectableDlcAppIds(appId))

            if (installedApp != null && installedDlcAppIds != installedApp.dlcDepots.toSet()) {
                runBlocking(Dispatchers.IO) {
                    instance?.appInfoDao?.update(installedApp.copy(dlcDepots = installedDlcAppIds.sorted()))
                }
            }

            return installedDlcAppIds.sorted()
        }

        private fun getInstalledSelectableDlcAppIds(appId: Int): Set<Int> =
            getSelectableDlcAppsOf(appId)
                .mapNotNull { dlcApp ->
                    val dlcInfo = getInstalledApp(dlcApp.id)
                    if (dlcInfo?.isDownloaded == true) dlcApp.id else null
                }.toSet()

        private fun getTrustedInstalledAppInfo(appId: Int): AppInfo? {
            val appInfo = getInstalledApp(appId) ?: tryRecoverInstalledAppInfo(appId)
            if (appInfo?.isDownloaded != true) return null

            val dirPath = getAppDirPath(appId)
            val dir = File(dirPath)
            if (!dir.isDirectory) return null
            if (!MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_COMPLETE_MARKER)) return null

            return appInfo
        }

        private fun tryRecoverInstalledAppInfo(appId: Int): AppInfo? {
            val dirPath = getAppDirPath(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasInProgressMarker = MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            if (!hasCompleteMarker || hasInProgressMarker) return null

            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) return null

            val downloadedDepotIds = runCatching { getMainAppDepots(appId).keys.sorted() }.getOrDefault(emptyList())
            val installedDlcAppIds = getInstalledSelectableDlcAppIds(appId)
            val recovered =
                AppInfo(
                    id = appId,
                    isDownloaded = true,
                    downloadedDepots = downloadedDepotIds,
                    dlcDepots = installedDlcAppIds.sorted(),
                )

            runBlocking(Dispatchers.IO) {
                PluviaDatabase.getInstance().appInfoDao().insert(recovered)
            }
            Timber.i("Recovered Steam installed metadata from disk for appId=$appId at $dirPath")
            return recovered
        }

        fun repairInstalledMetadataFromDisk(): Int {
            return runBlocking(Dispatchers.IO) {
                val db = PluviaDatabase.getInstance()
                val apps =
                    runCatching { db.steamAppDao().getAllAsList() }.getOrElse {
                        Timber.e(it, "Failed to load Steam apps for install repair")
                        return@runBlocking 0
                    }

                var repairedCount = 0
                for (app in apps) {
                    val installedApp = db.appInfoDao().getInstalledApp(app.id)
                    if (installedApp?.isDownloaded == true) continue
                    if (tryRecoverInstalledAppInfo(app.id) != null) {
                        repairedCount++
                    }
                }
                repairedCount
            }
        }

        private fun countCompletedInstallMarkers(maxCount: Int = Int.MAX_VALUE): Int {
            var count = 0
            for (basePath in allInstallPaths) {
                val baseDir = File(basePath)
                val appDirs = baseDir.listFiles() ?: continue
                for (appDir in appDirs) {
                    if (!appDir.isDirectory) continue
                    val hasCompleteMarker = File(appDir, Marker.DOWNLOAD_COMPLETE_MARKER.fileName).exists()
                    if (!hasCompleteMarker) continue

                    val hasInProgressMarker = File(appDir, Marker.DOWNLOAD_IN_PROGRESS_MARKER.fileName).exists()
                    if (hasInProgressMarker) continue

                    count++
                    if (count >= maxCount) return count
                }
            }
            return count
        }

        private fun shouldRepairInstalledMetadata(): Boolean {
            val db =
                runCatching { PluviaDatabase.getInstance() }.getOrElse {
                    Timber.e(it, "Failed to access database for startup metadata repair gate")
                    return false
                }

            val knownAppCount =
                runBlocking(Dispatchers.IO) {
                    runCatching { db.steamAppDao().getAllAppIds().size }.getOrElse {
                        Timber.e(it, "Failed to load Steam app ids for startup metadata repair gate")
                        return@runBlocking 0
                    }
                }
            if (knownAppCount == 0) return false

            val installedDbCount =
                runBlocking(Dispatchers.IO) {
                    runCatching { db.appInfoDao().getAllInstalledAppIds().size }.getOrElse {
                        Timber.e(it, "Failed to load installed Steam app ids for startup metadata repair gate")
                        return@runBlocking 0
                    }
                }

            val diskInstallCount = countCompletedInstallMarkers(maxCount = installedDbCount + 1)
            return diskInstallCount > installedDbCount
        }

        fun maybeRepairInstalledMetadataOnStartup(context: Context) {
            val appContext = context.applicationContext
            if (!hasStoredCredentials(appContext)) return

            if (startupMetadataRepairJob?.isActive == true) return

            startupMetadataRepairJob =
                CoroutineScope(Dispatchers.IO).launch {
                    if (!shouldRepairInstalledMetadata()) return@launch
                delay(1500L)
                val repairedCount = repairInstalledMetadataFromDisk()
                if (repairedCount > 0) {
                    Timber.i("Startup metadata repair recovered $repairedCount Steam install record(s)")
                }
            }
        }

        fun getAllDownloads(): Map<Int, DownloadInfo> = downloadJobs

        fun getAppDownloadInfo(appId: Int): DownloadInfo? = downloadJobs[appId]

        fun isAppInstalled(appId: Int): Boolean {
            return getTrustedInstalledAppInfo(appId) != null
        }

        fun uninstallApp(
            appId: Int,
            onComplete: (Boolean) -> Unit = {},
        ) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val dirPath = getAppDirPath(appId)
                    val deleteCheck =
                        StoreInstallPathSafety.checkInstallDirDelete(
                            instance?.applicationContext ?: DownloadService.appContext,
                            dirPath,
                            protectedRoots = steamProtectedInstallRoots(),
                        )
                    if (!deleteCheck.allowed) {
                        Timber.e("Refusing to uninstall Steam appId=$appId from '$dirPath': ${deleteCheck.reason}")
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onComplete(false)
                        }
                        return@launch
                    }

                    val dirFile = java.io.File(dirPath)
                    if (dirFile.exists() && dirFile.isDirectory) {
                        val deleted = deleteRecursivelyWithRetries(dirFile)
                        if (!deleted) {
                            Timber.e("Failed to fully delete Steam appId=$appId at '$dirPath'")
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onComplete(false)
                            }
                            return@launch
                        }
                    }

                    cleanupSteamAppCacheDirs(appId)

                    val appInfo = getInstalledApp(appId)
                    if (appInfo != null) {
                        instance?.appInfoDao?.update(appInfo.copy(isDownloaded = false))
                    }
                    LibraryShortcutUtils.deleteSteamShortcuts(PluviaApp.instance, appId)
                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(appId))
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(true)
                    }
                } catch (e: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            }
        }

        fun getAppDlc(appId: Int): Map<Int, DepotInfo> =
            getAppInfoOf(appId)
                ?.let {
                    it.depots.filter { it.value.dlcAppId != INVALID_APP_ID }
                }.orEmpty()

        suspend fun getOwnedAppDlc(appId: Int): Map<Int, DepotInfo> {
            val ownedGameIds =
                runCatching {
                    val steamId = userSteamId ?: return@runCatching emptySet<Int>()
                    getOwnedGames(steamId.convertToUInt64()).map { it.appId }.toHashSet()
                }.getOrDefault(emptySet())

            return getAppDlc(appId)
                .filter { (_, depot) ->
                    when {
                        // Base-game depots always download
                        depot.dlcAppId == INVALID_APP_ID -> true

                        // ① licence cache. DLC app IDs are stored inside package rows,
                        // not as package IDs themselves.
                        runBlocking(Dispatchers.IO) {
                            instance?.licenseDao?.countLicensesForApp(depot.dlcAppId) ?: 0
                        } > 0 -> true

                        // ② PICS row
                        instance?.appDao?.findApp(depot.dlcAppId) != null -> true

                        // ③ owned-games list
                        depot.dlcAppId in ownedGameIds -> true

                        // ④ final online / cached call
                        else -> false
                    }
                }.toMap()
        }

        fun getMainAppDlcIdsWithoutProperDepotDlcIds(appId: Int): MutableList<Int> {
            val mainAppDlcIds = mutableListOf<Int>()
            val hiddenDlcAppIds = getHiddenDlcAppsOf(appId).orEmpty().map { it.id }

            val appInfo = getAppInfoOf(appId)
            if (appInfo != null) {
                // for each of the dlcAppId found in main depots, filter the count = 1, add that dlcAppId to dlcAppIds
                val checkingAppDlcIds =
                    appInfo.depots
                        .filter { it.value.dlcAppId != INVALID_APP_ID }
                        .map { it.value.dlcAppId }
                        .distinct()
                checkingAppDlcIds.forEach { checkingDlcId ->
                    val checkMap = appInfo.depots.filter { it.value.dlcAppId == checkingDlcId }
                    if (checkMap.size == 1) {
                        val depotInfo = checkMap[checkMap.keys.first()]!!
                        if (depotInfo.osList.contains(OS.none) &&
                            depotInfo.manifests.isEmpty() &&
                            hiddenDlcAppIds.isNotEmpty() && hiddenDlcAppIds.contains(checkingDlcId)
                        ) {
                            mainAppDlcIds.add(checkingDlcId)
                        }
                    }
                }
            }

            return mainAppDlcIds
        }

        /**
         * Refresh the owned games list by querying Steam, diffing with the local DB, and
         * queueing PICS requests for anything new so metadata gets populated.
         *
         * @return number of newly discovered appIds that were scheduled for PICS.
         */
        suspend fun refreshOwnedGamesFromServer(): Int =
            withContext(Dispatchers.IO) {
                val service = instance ?: return@withContext 0
                val unifiedFriends = service._unifiedFriends ?: return@withContext 0
                val steamId = userSteamId ?: return@withContext 0

                runCatching {
                    val ownedGames = unifiedFriends.getOwnedGames(steamId.convertToUInt64())
                    val remoteAppIds = ownedGames.map { it.appId }.filter { it > 0 }.toSet()
                    if (remoteAppIds.isEmpty()) {
                        return@runCatching 0
                    }

                    val localAppIds = service.appDao.getAllAppIds().toSet()
                    val missingAppIds = remoteAppIds - localAppIds
                    if (missingAppIds.isEmpty()) {
                        return@runCatching 0
                    }

                    missingAppIds
                        .chunked(MAX_PICS_BUFFER)
                        .forEach { chunk ->
                            val requests = chunk.map { PICSRequest(id = it) }
                            service.appPicsChannel.send(requests)
                        }

                    missingAppIds.size
                }.onFailure { error ->
                    Timber.tag("SteamService").e(error, "Failed to refresh owned games from server")
                }.getOrDefault(0)
            }

        /**
         * Common Filter for downloadable depots
         */
        fun filterForDownloadableDepots(
            depot: DepotInfo,
            has64Bit: Boolean,
            preferredLanguage: String,
            ownedDlc: Map<Int, DepotInfo>?,
        ): Boolean {
            if (depot.manifests.isEmpty() && depot.encryptedManifests.isNotEmpty()) {
                return false
            }
            // 1. Has something to download
            if (depot.manifests.isEmpty() && !depot.sharedInstall) {
                return false
            }
            // 2. Supported OS
            if (!(
                    depot.osList.contains(OS.windows) ||
                        (!depot.osList.contains(OS.linux) && !depot.osList.contains(OS.macos))
                )
            ) {
                return false
            }
            // 3. 64-bit or indeterminate
            // Arch selection: allow 64-bit and Unknown always.
            // Allow 32-bit only when no 64-bit depot exists.
            val archOk =
                when (depot.osArch) {
                    OSArch.Arch64, OSArch.Unknown -> true
                    OSArch.Arch32 -> !has64Bit
                    else -> false
                }
            if (!archOk) return false
            // 4. DLC you actually own
            if (depot.dlcAppId != INVALID_APP_ID && ownedDlc != null && !ownedDlc.containsKey(depot.depotId)) {
                return false
            }
            // 5. Language filter - if depot has language, it must match preferred language
            if (depot.language.isNotEmpty() && !depot.language.equals(preferredLanguage, ignoreCase = true)) {
                return false
            }

            return true
        }

        fun getMainAppDepots(appId: Int): Map<Int, DepotInfo> {
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
            val preferredLanguage = PrefManager.containerLanguage
            val entitledDepotIds = getEntitledDepotIds(appInfo.packageId)

            // If the game ships any 64-bit depot for Windows, prefer those and ignore x86 ones
            val has64Bit =
                appInfo.depots.values.any {
                    it.osArch == OSArch.Arch64 && (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
                }

            return appInfo.depots
                .asSequence()
                .filter { (depotId, depot) ->
                    return@filter isDepotEntitled(depotId, depot, entitledDepotIds) &&
                        filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc)
                }.associate { it.toPair() }
        }

        /**
         * Get downloadable depots for a given app, including all DLCs
         * @return Map of app ID to depot ID to depot info
         */
        fun getDownloadableDepots(
            appId: Int,
            preferredLanguage: String = PrefManager.containerLanguage,
        ): Map<Int, DepotInfo> {
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
            val entitledDepotIds = getEntitledDepotIds(appInfo.packageId)

            // If the game ships any 64-bit depot for Windows, prefer those and ignore x86 ones
            val has64Bit =
                appInfo.depots.values.any {
                    it.osArch == OSArch.Arch64 && (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
                }

            val map = mutableMapOf<Int, DepotInfo>()
            for ((depotId, depot) in appInfo.depots) {
                if (isDepotEntitled(depotId, depot, entitledDepotIds) &&
                    filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc)
                ) {
                    map[depotId] = depot
                }
            }

            val indirectDlcApps = getDownloadableDlcAppsOf(appId).orEmpty()
            for (dlcApp in indirectDlcApps) {
                val entitledDlcDepotIds = getEntitledDepotIds(dlcApp.packageId)
                for ((depotId, depot) in dlcApp.depots) {
                    if (isDepotEntitled(depotId, depot, entitledDlcDepotIds) &&
                        filterForDownloadableDepots(depot, has64Bit, preferredLanguage, null)
                    ) {
                        // Add DLC Depots with custom object
                        map[depotId] =
                            DepotInfo(
                                depotId = depot.depotId,
                                dlcAppId = dlcApp.id, // Set to DLC App ID
                                optionalDlcId = depot.optionalDlcId,
                                depotFromApp = depot.depotFromApp,
                                sharedInstall = depot.sharedInstall,
                                osList = depot.osList,
                                osArch = depot.osArch,
                                language = depot.language,
                                manifests = depot.manifests,
                                encryptedManifests = depot.encryptedManifests,
                            )
                    }
                }
            }

            return map
        }

        private fun getEntitledDepotIds(packageId: Int): Set<Int>? {
            if (packageId == INVALID_PKG_ID) return null
            val depotIds =
                runBlocking(Dispatchers.IO) {
                    instance
                        ?.licenseDao
                        ?.findLicense(packageId)
                        ?.depotIds
                        .orEmpty()
                }
            return depotIds.takeIf { it.isNotEmpty() }?.toSet()
        }

        private fun isDepotEntitled(
            depotId: Int,
            depot: DepotInfo,
            entitledDepotIds: Set<Int>?,
        ): Boolean {
            if (entitledDepotIds == null) return true
            if (depotId in entitledDepotIds) return true

            // Shared/proxied depots may not be listed directly on the package even though
            // they are required to resolve the owning depot's content.
            return depot.sharedInstall || depot.depotFromApp != INVALID_APP_ID
        }

        private fun getSelectedDownloadDepots(
            appId: Int,
            userSelectedDlcAppIds: Collection<Int>,
            preferredLanguage: String = PrefManager.containerLanguage,
            branch: String = "public",
        ): Map<Int, DepotInfo> {
            val downloadableDepots = getDownloadableDepots(appId, preferredLanguage)
            if (downloadableDepots.isEmpty()) return emptyMap()

            val selectedDlcIds = userSelectedDlcAppIds.toSet()
            val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }.toSet()
            val mainDepots = getMainAppDepots(appId)
            val groupedBaseDlcDepotIds =
                getAppInfoOf(appId)
                    ?.let { getGroupedBaseAppDlcContentDepotIds(it) }
                    .orEmpty()

            val selectedMainDepots =
                mainDepots.filter { (depotId, depot) ->
                    (depot.dlcAppId == INVALID_APP_ID && depotId !in groupedBaseDlcDepotIds) ||
                        (depot.dlcAppId in selectedDlcIds && resolveDepotManifestInfo(depot, branch) != null)
                } + getSelectedBaseAppDlcContentDepots(appId, selectedDlcIds, preferredLanguage, branch)

            val selectedDlcDepots =
                downloadableDepots.filter { (depotId, depot) ->
                    depotId !in selectedMainDepots &&
                        depot.dlcAppId in selectedDlcIds &&
                        depot.dlcAppId in indirectDlcAppIds &&
                        resolveDepotManifestInfo(depot, branch) != null
                }

            return selectedMainDepots + selectedDlcDepots
        }

        private fun getGroupedBaseAppDlcContentDepotIds(appInfo: SteamApp): Set<Int> {
            return getGroupedBaseAppDlcDepots(appInfo).map { it.depotId }.toSet()
        }

        private fun getGroupedBaseAppDlcIds(
            appInfo: SteamApp,
            preferredLanguage: String = PrefManager.containerLanguage,
            has64Bit: Boolean =
                appInfo.depots.values.any {
                    it.osArch == OSArch.Arch64 &&
                        (it.osList.contains(OS.windows) || it.osList.isEmpty() || it.osList.contains(OS.none))
                },
        ): Set<Int> {
            return getGroupedBaseAppDlcDepots(appInfo)
                .filter { groupedDepot ->
                    filterForDownloadableDepots(groupedDepot.depot, has64Bit, preferredLanguage, ownedDlc = null)
                }.map { it.dlcAppId }
                .toSet()
        }

        private data class GroupedBaseAppDlcDepot(
            val depotId: Int,
            val dlcAppId: Int,
            val depot: DepotInfo,
        )

        private fun getGroupedBaseAppDlcDepots(appInfo: SteamApp): List<GroupedBaseAppDlcDepot> {
            val declaredDlcIds =
                (
                    appInfo.dlcAppIds.asSequence() +
                        appInfo.depots.values.asSequence()
                            .map { it.dlcAppId }
                            .filter { it != INVALID_APP_ID }
                ).toSet()
            if (declaredDlcIds.isEmpty()) return emptyList()

            val depotIds = mutableListOf<GroupedBaseAppDlcDepot>()
            var activeDlcAppId: Int? = null
            for ((depotId, depot) in appInfo.depots) {
                val isDlcMarkerDepot =
                    depotId in declaredDlcIds &&
                        depot.manifests.isEmpty()
                if (isDlcMarkerDepot) {
                    activeDlcAppId = depotId
                    continue
                }

                val dlcAppId = activeDlcAppId
                if (dlcAppId != null && depot.dlcAppId == INVALID_APP_ID) {
                    depotIds += GroupedBaseAppDlcDepot(depotId, dlcAppId, depot)
                }
            }

            return depotIds
        }

        private fun getSelectedBaseAppDlcContentDepots(
            appId: Int,
            selectedDlcAppIds: Collection<Int>,
            preferredLanguage: String = PrefManager.containerLanguage,
            branch: String = "public",
        ): Map<Int, DepotInfo> {
            if (selectedDlcAppIds.isEmpty()) return emptyMap()
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val selectedDlcIds = selectedDlcAppIds.toSet()
            val declaredDlcIds =
                (
                    appInfo.dlcAppIds.asSequence() +
                        appInfo.depots.values.asSequence()
                            .map { it.dlcAppId }
                            .filter { it != INVALID_APP_ID }
                ).toSet()
            if (declaredDlcIds.isEmpty()) return emptyMap()

            val has64Bit =
                appInfo.depots.values.any {
                    it.osArch == OSArch.Arch64 &&
                        (it.osList.contains(OS.windows) || it.osList.isEmpty() || it.osList.contains(OS.none))
                }

            val selectedDepots = linkedMapOf<Int, DepotInfo>()
            var activeDlcAppId: Int? = null
            for ((depotId, depot) in appInfo.depots) {
                val isDlcMarkerDepot =
                    depotId in declaredDlcIds &&
                        depot.manifests.isEmpty()
                if (isDlcMarkerDepot) {
                    activeDlcAppId = depotId.takeIf { it in selectedDlcIds }
                    continue
                }

                val selectedDlcAppId =
                    when {
                        depot.dlcAppId in selectedDlcIds -> depot.dlcAppId
                        depot.dlcAppId == INVALID_APP_ID -> activeDlcAppId
                        else -> null
                    } ?: continue

                val selectedDepot =
                    if (depot.dlcAppId == selectedDlcAppId) {
                        depot
                    } else {
                        depot.copy(dlcAppId = selectedDlcAppId)
                    }

                if (!filterForDownloadableDepots(selectedDepot, has64Bit, preferredLanguage, ownedDlc = null)) continue
                if (resolveDepotManifestInfo(selectedDepot, branch) == null) continue
                selectedDepots[depotId] = selectedDepot
            }

            if (selectedDepots.isNotEmpty()) {
                Timber.i(
                    "Recovered base-app DLC content depots for appId=$appId " +
                        "selectedDlcAppIds=${selectedDlcIds.sorted()} " +
                        "depotIdsByDlc=${selectedDepots.values.groupBy({ it.dlcAppId }, { it.depotId })}",
                )
            }
            return selectedDepots
        }

        private fun resolveDepotManifestInfo(
            depot: DepotInfo,
            branch: String,
            visitedApps: MutableSet<Int> = mutableSetOf(),
        ): ManifestInfo? {
            depot.manifests[branch]?.let { return it }
            depot.encryptedManifests[branch]?.let { return it }

            if (!branch.equals("public", ignoreCase = true)) {
                depot.manifests["public"]?.let { return it }
                depot.encryptedManifests["public"]?.let { return it }
            }

            val sourceAppId = depot.depotFromApp
            if (sourceAppId == INVALID_APP_ID || !visitedApps.add(sourceAppId)) {
                return null
            }

            val sourceDepot = getAppInfoOf(sourceAppId)?.depots?.get(depot.depotId) ?: return null
            return resolveDepotManifestInfo(sourceDepot, branch, visitedApps)
        }

        private fun manifestDownloadBytes(manifest: ManifestInfo?): Long {
            if (manifest == null) return 0L
            return manifest.download.takeIf { it > 0L } ?: manifest.size.coerceAtLeast(0L)
        }

        private fun calculateManifestSizes(
            depots: Collection<DepotInfo>,
            branch: String,
        ): ManifestSizes {
            var totalInstallSize = 0L
            var totalDownloadSize = 0L

            depots.forEach { depot ->
                val manifest = resolveDepotManifestInfo(depot, branch)
                totalInstallSize += manifest?.size ?: 0L
                totalDownloadSize += manifestDownloadBytes(manifest)
            }

            return ManifestSizes(
                installSize = totalInstallSize,
                downloadSize = totalDownloadSize,
            )
        }

        private fun filterAlreadyInstalledDepots(
            appId: Int,
            depots: Map<Int, DepotInfo>,
            includeInstalledDepots: Boolean,
        ): Map<Int, DepotInfo> {
            if (includeInstalledDepots || depots.isEmpty()) return depots

            val installedApp = getTrustedInstalledAppInfo(appId) ?: return depots
            val installedDlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toSet()

            return depots.filter { (depotId, depot) ->
                val isInstalledBaseDepot =
                    depot.dlcAppId == INVALID_APP_ID ||
                        depotId in installedApp.downloadedDepots
                val isInstalledDlcDepot =
                    depot.dlcAppId != INVALID_APP_ID &&
                        depot.dlcAppId in installedDlcAppIds

                !isInstalledBaseDepot && !isInstalledDlcDepot
            }
        }

        private fun filterAlreadyInstalledDlcSelection(
            appId: Int,
            dlcAppIds: List<Int>,
            includeInstalledDepots: Boolean,
            customInstallPath: String?,
        ): List<Int> {
            val selected = dlcAppIds.distinct()
            if (selected.isEmpty() || includeInstalledDepots || customInstallPath != null) return selected

            val installedDlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toSet()
            if (installedDlcAppIds.isEmpty()) return selected

            val filtered = selected.filterNot { it in installedDlcAppIds }
            val skipped = selected - filtered.toSet()
            if (skipped.isNotEmpty()) {
                Timber.i(
                    "Skipping already-installed Steam DLC selection for appId=$appId " +
                        "dlcAppIds=${skipped.sorted()}",
                )
            }
            return filtered
        }

        fun getSelectedManifestSizes(
            appId: Int,
            userSelectedDlcAppIds: Collection<Int> = emptyList(),
            preferredLanguage: String = PrefManager.containerLanguage,
            branch: String = "public",
        ): ManifestSizes {
            val selectedDepots = getSelectedDownloadDepots(appId, userSelectedDlcAppIds, preferredLanguage, branch)
            if (selectedDepots.isEmpty()) return ManifestSizes()

            return calculateManifestSizes(selectedDepots.values, branch)
        }

        fun getInstallableSelectedManifestSizes(
            appId: Int,
            userSelectedDlcAppIds: Collection<Int> = emptyList(),
            preferredLanguage: String = PrefManager.containerLanguage,
            branch: String = "public",
        ): ManifestSizes {
            val selectedDepots = getSelectedDownloadDepots(appId, userSelectedDlcAppIds, preferredLanguage, branch)
            val installableDepots =
                filterAlreadyInstalledDepots(
                    appId = appId,
                    depots = selectedDepots,
                    includeInstalledDepots = false,
                )
            if (installableDepots.isEmpty()) return ManifestSizes()

            return calculateManifestSizes(installableDepots.values, branch)
        }

        fun getDlcOnlyManifestSizes(
            appId: Int,
            dlcAppId: Int,
            preferredLanguage: String = PrefManager.containerLanguage,
            branch: String = "public",
        ): ManifestSizes {
            val service = instance ?: return ManifestSizes()
            val mainAppInfo =
                runBlocking(Dispatchers.IO) { service.appDao.findApp(appId) } ?: return ManifestSizes()
            val has64Bit =
                mainAppInfo.depots.values.any {
                    it.osArch == OSArch.Arch64 &&
                        (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
                }

            val mainAppDlcDepots =
                getSelectedBaseAppDlcContentDepots(appId, listOf(dlcAppId), preferredLanguage, branch).values

            val dlcAppInfo = runBlocking(Dispatchers.IO) { service.appDao.findApp(dlcAppId) }
            val dlcAppDepots =
                dlcAppInfo?.depots?.values?.filter { depot ->
                    filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc = null)
                }.orEmpty()

            val combined = (mainAppDlcDepots + dlcAppDepots).associateBy { it.depotId }.values
            if (combined.isEmpty()) return ManifestSizes()

            return calculateManifestSizes(combined, branch)
        }

        fun getAppDirName(app: SteamApp?): String {
            // The folder name, if it got made
            var appName = app?.config?.installDir.orEmpty()
            if (appName.isEmpty()) {
                appName = app?.name.orEmpty()
            }
            return appName
        }

        private fun normalizeInstallPath(path: String): String {
            if (path.isBlank()) return path
            return try {
                File(path).canonicalPath
            } catch (_: IOException) {
                File(path).absolutePath
            }
        }

        fun getAppDirPath(gameId: Int): String {
            val info = getAppInfoOf(gameId)

            // Check custom install directory first (only if it's a full absolute path)
            // installDir from PICS metadata is just a folder name, custom installs save full path
            val customDir = info?.installDir.orEmpty()
            if (customDir.isNotEmpty() && (customDir.startsWith("/") || customDir.contains(File.separator))) {
                // It's a full path (custom install location)
                return normalizeInstallPath(customDir)
            }

            val appName = getAppDirName(info)
            val oldName = info?.name.orEmpty()

            // Respect user-selected default download folder
            val context = PluviaApp.instance.applicationContext
            if (context != null) {
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir =
                        com.winlator.cmod.shared.io.FileUtils
                            .getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    Timber.i("getAppDirPath: resolved baseDir $baseDir from URI $storeDefaultUri")
                    if (baseDir != null) {
                        val path = Paths.get(baseDir, appName)
                        if (Files.exists(path)) {
                            Timber.i("getAppDirPath: found existing path $path")
                            return normalizeInstallPath(path.pathString)
                        }
                        if (oldName.isNotEmpty()) {
                            val oldPath = Paths.get(baseDir, oldName)
                            if (Files.exists(oldPath)) {
                                Timber.i("getAppDirPath: found existing oldPath $oldPath")
                                return normalizeInstallPath(oldPath.pathString)
                            }
                        }
                        // If it doesn't exist yet, this is where we'll install it
                        Timber.i("getAppDirPath: returning new path $path")
                        return normalizeInstallPath(path.pathString)
                    }
                }
            }

            for (basePath in allInstallPaths) {
                val candidate = Paths.get(basePath, appName)
                if (Files.exists(candidate)) return normalizeInstallPath(candidate.pathString)
                if (oldName.isNotEmpty()) {
                    val oldCandidate = Paths.get(basePath, oldName)
                    if (Files.exists(oldCandidate)) return normalizeInstallPath(oldCandidate.pathString)
                }
            }

            // Nothing on disk yet – default to whatever location you want new installs to use
            if (PrefManager.useExternalStorage) {
                return normalizeInstallPath(Paths.get(externalAppInstallPath, appName).pathString)
            }
            return normalizeInstallPath(Paths.get(internalAppInstallPath, appName).pathString)
        }

        private fun createSteamShortcut(
            context: Context,
            appId: Int,
        ) {
            try {
                val container = ContainerUtils.getOrCreateContainer(context, "STEAM_$appId")
                val appInfo = getAppInfoOf(appId) ?: return
                val installPath = getAppDirPath(appId)
                val launchExecutable = getInstalledExe(appId)
                val desktopDir = container.getDesktopDir()
                if (!desktopDir.exists()) desktopDir.mkdirs()

                val shortcutFile = File(desktopDir, "${appInfo.name}.desktop")
                val content = StringBuilder()
                content.append("[Desktop Entry]\n")
                content.append("Type=Application\n")
                content.append("Name=${appInfo.name}\n")
                content.append("Exec=wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\"\n")
                content.append("Icon=steam_icon_$appId\n")
                content.append("\n[Extra Data]\n")
                content.append("game_source=STEAM\n")
                content.append("app_id=$appId\n")
                content.append("container_id=${container.id}\n")
                content.append("game_install_path=$installPath\n")
                content.append("launch_exe_path=$launchExecutable\n")
                content.append("use_container_defaults=1\n")

                com.winlator.cmod.shared.io.FileUtils
                    .writeString(shortcutFile, content.toString())
                Timber.i("Created Steam shortcut for ${appInfo.name} in container ${container.id}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to create Steam shortcut for appId $appId")
            }
        }

        /**
         * Resolves the executable for an installed Steam app.
         *
         * Phase 9: the old depot-manifest EXE scan + heuristic scorer were
         * removed with the JavaSteam dependency. Modern Steam depot manifests
         * store filenames AES-encrypted and the cached `.manifest` is never
         * decrypted on disk, so scanning it only ever yielded encrypted
         * (unusable) names — the scan effectively always fell through to the
         * launch-info path below. Exe detection now uses the app's own
         * appinfo `config.launch` entries directly.
         */
        fun getInstalledExe(appId: Int): String =
            getWindowsLaunchInfos(appId).firstOrNull()?.executable ?: ""

        fun getLaunchExecutable(
            appId: String,
            container: Container,
        ): String {
            if (container.isLaunchRealSteam) return "steam"
            val gameId = ContainerUtils.extractGameIdFromContainerId(appId)
            return container.executablePath.ifEmpty { getInstalledExe(gameId) }
        }

        suspend fun deleteApp(appId: Int): Boolean =
            withContext(Dispatchers.IO) {
                val appDirPath = getAppDirPath(appId)
                val deleteCheck =
                    StoreInstallPathSafety.checkInstallDirDelete(
                        instance?.applicationContext ?: DownloadService.appContext,
                        appDirPath,
                        protectedRoots = steamProtectedInstallRoots(),
                    )

                // Guard against accidental root deletion if path resolution failed.
                if (!deleteCheck.allowed) {
                    Timber.e("Refusing to delete appId=$appId from '$appDirPath': ${deleteCheck.reason}")
                    return@withContext false
                }

                // If an active download exists, stop it and wait briefly before deleting files.
                downloadJobs[appId]?.let { info ->
                    info.isDeleting = true
                    info.cancel("Cancelled for delete")
                    info.awaitCompletion(timeoutMs = 5000L)
                    removeDownloadJob(appId)
                }

                // Remove any download-complete marker
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                clearPersistedProgressSnapshot(appDirPath)

                cleanupSteamAppCacheDirs(appId)

                // Remove from DB synchronously so immediate reinstall cannot race with stale metadata.
                with(instance!!) {
                    db.withTransaction {
                        appInfoDao.deleteApp(appId)
                        changeNumbersDao.deleteByAppId(appId)
                        fileChangeListsDao.deleteByAppId(appId)
                        downloadingAppInfoDao.deleteApp(appId)

                        // Clear installDir in steam_app table
                        appDao.findApp(appId)?.let { steamApp ->
                            if (steamApp.installDir.isNotEmpty()) {
                                appDao.update(steamApp.copy(installDir = ""))
                                Timber.i("Cleared installDir for appId $appId in DB")
                            }
                        }

                        val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }
                        indirectDlcAppIds.forEach { dlcAppId ->
                            appInfoDao.deleteApp(dlcAppId)
                            changeNumbersDao.deleteByAppId(dlcAppId)
                            fileChangeListsDao.deleteByAppId(dlcAppId)
                        }
                    }
                }

                return@withContext deleteRecursivelyWithRetries(File(appDirPath))
            }

        fun setCustomInstallPath(
            appId: Int,
            customInstallPath: String,
        ): String {
            val appInfo = getAppInfoOf(appId)
            val folderName = getAppDirName(appInfo)
            val safeFolderName = if (folderName.isNotEmpty()) folderName else appId.toString()

            val customFile = File(customInstallPath)
            val finalPath =
                if (customFile.name.equals(safeFolderName, ignoreCase = true)) {
                    // User selected the game folder itself
                    normalizeInstallPath(customFile.absolutePath)
                } else {
                    // User selected parent folder, create/use subfolder
                    normalizeInstallPath(File(customInstallPath, safeFolderName).absolutePath)
                }

            // Update SteamApp in DB
            runBlocking(Dispatchers.IO) {
                instance?.appDao?.findApp(appId)?.let { steamApp ->
                    instance?.appDao?.update(steamApp.copy(installDir = finalPath))
                    Timber.i("Updated SteamApp installDir in DB to: $finalPath")
                }
            }
            return finalPath
        }

        fun downloadApp(appId: Int): DownloadInfo? = downloadApp(appId, dlcAppIdsHint = null)

        /**
         * Resume / start entry point that accepts an authoritative DLC selection [dlcAppIdsHint]
         * — typically supplied by the cross-store [DownloadCoordinator] from its persisted
         * [DownloadRecord.selectedDlcs] field. When provided, this list takes precedence over
         * the legacy fallback chain (DownloadingAppInfo → snapshot inference → installed DLCs)
         * because the coordinator's record is the only source guaranteed to remember the user's
         * original selection across pause/resume and across app restarts.
         *
         * Pass `null` (or call the no-arg overload) for legacy callers; we'll then look the
         * record up ourselves so coordinator-aware behavior still applies.
         */
        fun downloadApp(appId: Int, dlcAppIdsHint: List<Int>?): DownloadInfo? {
            val currentDownloadInfo = downloadJobs[appId]
            if (currentDownloadInfo != null) {
                if (!currentDownloadInfo.isActive()) {
                    removeDownloadJob(appId)
                } else {
                    return downloadApp(appId, currentDownloadInfo.downloadingAppIds, isUpdateOrVerify = false)
                }
            }

            // If the caller didn't supply an authoritative DLC list, try to recover one from
            // the DownloadCoordinator's persisted record. That record is store-agnostic but
            // currently only populated for coordinator-managed downloads; if it's missing we
            // fall through to the older DownloadingAppInfo-based recovery further down.
            val recordDlcIds: List<Int>? = dlcAppIdsHint
                ?: runCatching {
                    runBlocking(Dispatchers.IO) {
                        val record = DownloadCoordinator.findRecord(
                            DownloadRecord.STORE_STEAM,
                            appId.toString(),
                        )
                        record?.selectedDlcs
                            ?.split(',')
                            ?.mapNotNull { it.trim().toIntOrNull() }
                    }
                }.getOrNull()

            val downloadingAppInfo = getDownloadingAppInfoOf(appId)
            val appDirPath = getAppDirPath(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasPartialFiles = hasPartialDownloadFiles(appDirPath)
            val hasPersistedMetadata = hasPersistedDepotResumeMetadata(appDirPath)
            val hasResumablePayload =
                if (hasCompleteMarker) {
                    downloadingAppInfo != null || hasPersistedMetadata || recordDlcIds != null
                } else {
                    hasPartialFiles
                }
            if (hasResumablePayload) {
                // Strict trust order (per agreed fix plan with Codex):
                //   1. Coordinator record (authoritative, store-agnostic, durable across
                //      app restarts and DB migrations) — ALSO authoritative for an empty list
                //      (= "user wants base game only").
                //   2. DownloadingAppInfo DAO row (Steam-specific, written at first download).
                //   3. inferResumeDlcAppIds (snapshot of which depots have bytes — may be a
                //      subset, used only when no authoritative source exists).
                //   4. resolveInstalledDlcIdsForUpdateOrVerify (DLCs already installed —
                //      last-ditch legacy recovery).
                // Do NOT union: an empty list from an authoritative source means "no DLCs".
                val resumeDlcAppIds: List<Int> =
                    recordDlcIds
                        ?: downloadingAppInfo?.dlcAppIds
                        ?: run {
                            val inferred = inferResumeDlcAppIds(appId, appDirPath)
                            if (inferred.isNotEmpty()) inferred
                            else resolveInstalledDlcIdsForUpdateOrVerify(appId)
                        }
                return downloadApp(
                    appId = appId,
                    dlcAppIds = resumeDlcAppIds,
                    includeInstalledDepots = false,
                    enableVerify = false,
                    allowPersistedProgress = true,
                    hasPersistedResumeRow = downloadingAppInfo != null || recordDlcIds != null,
                )
            }

            if (downloadingAppInfo != null) {
                runBlocking(Dispatchers.IO) {
                    instance?.downloadingAppInfoDao?.deleteApp(appId)
                }
            }

            if (hasCompleteMarker && !hasPersistedMetadata) {
                clearPersistedProgressSnapshot(appDirPath)
            }

            if (!hasPartialFiles) {
                clearPersistedProgressSnapshot(appDirPath)
            }

            return downloadApp(
                appId = appId,
                dlcAppIds = resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = false,
                enableVerify = false,
                allowPersistedProgress = false,
            )
        }

        fun downloadAppForUpdate(
            appId: Int,
            targetDepotIds: Collection<Int> = emptyList(),
        ): DownloadInfo? =
            downloadApp(
                appId,
                resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = true,
                enableVerify = false,
                allowPersistedProgress = false,
                downloadTaskType = DownloadRecord.TASK_UPDATE,
                targetDepotIds = targetDepotIds.toSet().takeIf { it.isNotEmpty() },
            )

        fun downloadAppForVerify(appId: Int): DownloadInfo? =
            downloadApp(
                appId,
                resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = true,
                enableVerify = true,
                allowPersistedProgress = false,
                downloadTaskType = DownloadRecord.TASK_VERIFY,
            )

        private fun resolveInstalledDlcIdsForUpdateOrVerify(appId: Int): List<Int> {
            val dlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toMutableList()

            getDownloadableDlcAppsOf(appId)?.forEach { dlcApp ->
                val installedDlcApp = getInstalledApp(dlcApp.id)
                if (installedDlcApp != null) {
                    dlcAppIds.add(installedDlcApp.id)
                }
            }

            return dlcAppIds.distinct()
        }

        private fun parseDownloadScopeIds(scope: String): Set<Int> =
            scope
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()

        private fun activeDownloadRecordFor(appId: Int): DownloadRecord? =
            runCatching {
                runBlocking(Dispatchers.IO) {
                    DownloadCoordinator.findRecord(
                        DownloadRecord.STORE_STEAM,
                        appId.toString(),
                    )
                }
            }.getOrNull()
                ?.takeIf {
                    it.status in setOf(
                        DownloadRecord.STATUS_QUEUED,
                        DownloadRecord.STATUS_DOWNLOADING,
                        DownloadRecord.STATUS_PAUSED,
                    )
                }

        private fun rejectConflictingDownloadRequest(appId: Int, record: DownloadRecord): DownloadInfo? {
            Timber.i(
                "Refusing Steam download request for appId=$appId because an active record already exists " +
                    "status=${record.status} taskType=${record.taskType} selectedDlcs=${record.selectedDlcs}",
            )
            instance?.let { service ->
                service.scope.launch(Dispatchers.Main) {
                    WinToast.show(
                        service.applicationContext,
                        service.getString(R.string.store_game_download_already_active),
                        Toast.LENGTH_SHORT,
                    )
                }
            }
            // Return null so callers can tell the request was rejected. Returning the
            // pre-existing job here would let a verify/update pop-up latch onto an
            // unrelated in-flight download and mislabel it.
            return null
        }

        fun downloadApp(
            appId: Int,
            dlcAppIds: List<Int>,
            isUpdateOrVerify: Boolean,
            customInstallPath: String? = null,
        ): DownloadInfo? {
            // Backward-compatible API:
            // true => include already-downloaded depots (update scope), but do not force verify.
            return downloadApp(
                appId = appId,
                dlcAppIds = dlcAppIds,
                includeInstalledDepots = isUpdateOrVerify,
                enableVerify = false,
                allowPersistedProgress = false,
                customInstallPath = customInstallPath,
            )
        }

        private fun downloadApp(
            appId: Int,
            dlcAppIds: List<Int>,
            includeInstalledDepots: Boolean,
            enableVerify: Boolean,
            allowPersistedProgress: Boolean = false,
            hasPersistedResumeRow: Boolean = false,
            customInstallPath: String? = null,
            downloadTaskType: String = DownloadRecord.TASK_INSTALL,
            targetDepotIds: Set<Int>? = null,
        ): DownloadInfo? {
            val appInfo = getAppInfoOf(appId)
            if (appInfo == null) {
                Timber.e("Download aborted: Could not find AppInfo for appId: $appId")
                return null
            }

            val effectiveDlcAppIds =
                filterAlreadyInstalledDlcSelection(
                    appId = appId,
                    dlcAppIds = dlcAppIds,
                    includeInstalledDepots = includeInstalledDepots,
                    customInstallPath = customInstallPath,
                )

            val downloadableDepots = getDownloadableDepots(appId)
            if (downloadableDepots.isEmpty()) {
                Timber.w("Download aborted: No downloadable depots found for appId: $appId")
                instance?.let { service ->
                    service.scope.launch(Dispatchers.Main) {
                        WinToast.show(service.applicationContext, "No downloadable content found for this game", Toast.LENGTH_LONG)
                    }
                }
                return null
            }

            // Delegate to the full depot-level downloadApp overload
            return downloadApp(
                appId = appId,
                downloadableDepots = downloadableDepots,
                userSelectedDlcAppIds = effectiveDlcAppIds,
                branch = "public",
                includeInstalledDepots = includeInstalledDepots,
                enableVerify = enableVerify,
                allowPersistedProgress = allowPersistedProgress,
                hasPersistedResumeRow = hasPersistedResumeRow,
                customInstallPath = customInstallPath,
                downloadTaskType = downloadTaskType,
                targetDepotIds = targetDepotIds,
            )
        }

        fun isImageFsInstalled(context: Context): Boolean = ImageFs.find(context).isValid()

        fun isImageFsInstallable(
            context: Context,
            variant: String,
        ): Boolean {
            if (variant.equals("BIONIC")) {
                return File(context.filesDir, "imagefs_bionic.txz").exists() || context.assets
                    .list("")
                    ?.contains("imagefs_bionic.txz") == true
            } else {
                return File(context.filesDir, "imagefs_gamenative.txz").exists() || context.assets
                    .list("")
                    ?.contains("imagefs_gamenative.txz") == true
            }
        }

        fun isSteamInstallable(context: Context): Boolean = File(context.filesDir, "steam.tzst").exists()

        fun isFileInstallable(
            context: Context,
            filename: String,
        ): Boolean = File(context.filesDir, filename).exists()

        suspend fun fetchFile(
            url: String,
            dest: File,
            onProgress: (Float) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val tmp = File(dest.absolutePath + ".part")
            try {
                val http = SteamUtils.http

                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { rsp ->
                    check(rsp.isSuccessful) { "HTTP ${rsp.code}" }
                    val body = rsp.body ?: error("empty body")
                    val total = body.contentLength()
                    tmp.outputStream().use { out ->
                        body.byteStream().copyTo(out, 8 * 1024) { read ->
                            onProgress(read.toFloat() / total)
                        }
                    }
                    if (total > 0 && tmp.length() != total) {
                        tmp.delete()
                        error("incomplete download")
                    }
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }

        suspend fun fetchFileWithFallback(
            fileName: String,
            dest: File,
            context: Context,
            onProgress: (Float) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val urls = downloadUrlsFor(fileName)
            var lastError: Exception? = null
            for ((index, url) in urls.withIndex()) {
                try {
                    fetchFile(url, dest, onProgress)
                    return@withContext
                } catch (e: Exception) {
                    lastError = e
                    if (index < urls.lastIndex) {
                        Timber.w(e, "Download failed from $url; retrying with next URL")
                    }
                }
            }

            dest.delete()
            withContext(Dispatchers.Main) {
                val msg = "Download failed with ${lastError?.message ?: "unknown error"}. Please disable VPN or try a different network."
                WinToast.show(context.applicationContext, msg, android.widget.Toast.LENGTH_LONG)
            }
            throw IOException(
                "Failed to download $fileName. Please check your network connection or try a VPN.",
                lastError,
            )
        }

        /** copyTo with progress callback */
        private inline fun InputStream.copyTo(
            out: OutputStream,
            bufferSize: Int = DEFAULT_BUFFER_SIZE,
            progress: (Long) -> Unit,
        ) {
            val buf = ByteArray(bufferSize)
            var bytesRead: Int
            var total = 0L
            while (read(buf).also { bytesRead = it } >= 0) {
                if (bytesRead == 0) continue
                out.write(buf, 0, bytesRead)
                total += bytesRead
                progress(total)
            }
        }

        fun downloadImageFs(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            variant: String,
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            if (variant == "BIONIC") {
                val dest = File(context.filesDir, "imagefs_bionic.txz")
                Timber.d("Downloading imagefs_bionic to " + dest.toString())
                fetchFileWithFallback("imagefs_bionic.txz", dest, context, onDownloadProgress)
            } else {
                Timber.d("Downloading imagefs_gamenative to " + File(context.filesDir, "imagefs_gamenative.txz"))
                fetchFileWithFallback(
                    "imagefs_gamenative.txz",
                    File(context.filesDir, "imagefs_gamenative.txz"),
                    context,
                    onDownloadProgress,
                )
            }
        }

        fun downloadImageFsPatches(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            val dest = File(context.filesDir, "imagefs_patches_gamenative.tzst")
            Timber.d("Downloading imagefs_patches_gamenative.tzst to " + dest.toString())
            fetchFileWithFallback("imagefs_patches_gamenative.tzst", dest, context, onDownloadProgress)
        }

        fun downloadFile(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
            fileName: String,
        ) = parentScope.async {
            Timber.i("$fileName will be downloaded")
            val dest = File(context.filesDir, fileName)
            Timber.d("Downloading $fileName to " + dest.toString())
            fetchFileWithFallback(fileName, dest, context, onDownloadProgress)
        }

        fun downloadSteam(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            val dest = File(context.filesDir, "steam.tzst")
            Timber.d("Downloading steam.tzst to " + dest.toString())
            fetchFileWithFallback("steam.tzst", dest, context, onDownloadProgress)
        }

        private fun selectSteamControllerConfig(details: List<SteamControllerConfigDetail>): SteamControllerConfigDetail? {
            if (details.isEmpty()) return null

            val branchPriority = listOf("default", "public")
            val controllerPriority =
                listOf(
                    "controller_xbox360",
                    "controller_xboxone",
                    "controller_steamcontroller_gordon",
                    "controller_generic",
                )

            for (branch in branchPriority) {
                for (controllerType in controllerPriority) {
                    val match =
                        details.firstOrNull { detail ->
                            detail.controllerType.equals(controllerType, ignoreCase = true) &&
                                detail.enabledBranches.any { it.equals(branch, ignoreCase = true) }
                        }
                    if (match != null) return match
                }
            }

            return null
        }

        private fun resolveSteamInputManifestFile(
            appId: Int,
            appDirPath: String,
        ): File? {
            val manifestPath =
                getAppInfoOf(appId)
                    ?.config
                    ?.steamInputManifestPath
                    ?.trim()
                    .orEmpty()
            if (manifestPath.isEmpty()) return null

            return resolvePathCaseInsensitive(appDirPath, manifestPath)
        }

        private fun loadConfigFromManifest(manifestFile: File): String? {
            if (!manifestFile.exists()) return null
            val manifestDirPath = manifestFile.parentFile?.path ?: return null

            val manifestText = manifestFile.readText(Charsets.UTF_8)
            val configText =
                try {
                    parseManifestForConfig(manifestDirPath, manifestText)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse Steam Input manifest config at ${manifestFile.path}")
                    return null
                }
            return configText ?: manifestText
        }

        private fun parseManifestForConfig(
            manifestDirPath: String,
            manifestText: String,
        ): String? {
            return try {
                val kv = KeyValue.loadFromString(manifestText) ?: return null
                val actionManifest =
                    if (kv.name?.equals("Action Manifest", ignoreCase = true) == true) {
                        kv
                    } else {
                        kv["Action Manifest"]
                    }
                if (actionManifest === KeyValue.INVALID) return null

                val configs = actionManifest["configurations"]
                if (configs === KeyValue.INVALID || configs.children.isEmpty()) {
                    throw IllegalStateException("No configurations found in Action Manifest")
                }

                val preferredControllers =
                    listOf(
                        "controller_xboxone",
                        "controller_steamcontroller_gordon",
                        "controller_generic",
                        "controller_xbox360",
                    )

                for (controllerType in preferredControllers) {
                    val controllerBlock = configs[controllerType]
                    if (controllerBlock === KeyValue.INVALID) continue

                    for (entry in controllerBlock.children) {
                        val pathNode = entry["path"]
                        val configPath = pathNode.asString().orEmpty()
                        if (pathNode === KeyValue.INVALID || configPath.isEmpty()) continue

                        val configFile =
                            resolvePathCaseInsensitive(manifestDirPath, configPath)
                                ?: continue
                        return configFile.readText(Charsets.UTF_8)
                    }
                }

                throw IllegalStateException("No valid controller configuration found in Action Manifest")
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse Steam Input manifest config")
                null
            }
        }

        private fun resolvePathCaseInsensitive(
            baseDirPath: String,
            relativePath: String,
        ): File? {
            val normalizedPath = relativePath.replace('\\', '/')
            val directFile = File(baseDirPath, normalizedPath)
            if (directFile.exists()) return directFile

            var currentDir = File(baseDirPath)
            if (!currentDir.exists() || !currentDir.isDirectory) return null

            val segments = normalizedPath.split('/').filter { it.isNotEmpty() }
            for ((index, segment) in segments.withIndex()) {
                if (segment == ".") continue
                if (segment == "..") {
                    currentDir = currentDir.parentFile ?: return null
                    continue
                }
                val entries = currentDir.listFiles() ?: return null
                val matched =
                    entries.firstOrNull {
                        it.name.equals(segment, ignoreCase = true)
                    } ?: return null

                if (index == segments.lastIndex) {
                    return matched
                }

                if (!matched.isDirectory) return null
                currentDir = matched
            }

            return null
        }

        private fun readBuiltInSteamInputTemplate(fileName: String): String? {
            val assets = instance?.assets ?: return null
            return runCatching {
                assets.open("steaminput/$fileName").use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }

        private fun readDownloadedSteamInputTemplate(appId: Int): String? {
            val configFile = File(getAppDirPath(appId), STEAM_CONTROLLER_CONFIG_FILENAME)
            if (!configFile.exists()) return null
            return configFile.readText(Charsets.UTF_8)
        }

        fun resolveSteamControllerVdfText(appId: Int): String? {
            val config = getAppInfoOf(appId)?.config ?: return null
            return when (config.steamControllerTemplateIndex) {
                1 -> {
                    readDownloadedSteamInputTemplate(appId)
                }

                13 -> {
                    val manifestFile =
                        resolveSteamInputManifestFile(appId, getAppDirPath(appId))
                            ?: return null
                    loadConfigFromManifest(manifestFile)
                }

                2, 12 -> {
                    readBuiltInSteamInputTemplate("controller_xboxone_gamepad_fps.vdf")
                }

                6 -> {
                    readBuiltInSteamInputTemplate("controller_xboxone_wasd.vdf")
                }

                4, 5 -> {
                    readBuiltInSteamInputTemplate("gamepad_joystick.vdf")
                }

                else -> {
                    readBuiltInSteamInputTemplate("gamepad_joystick.vdf")
                }
            }
        }

        fun downloadApp(
            appId: Int,
            downloadableDepots: Map<Int, DepotInfo>,
            userSelectedDlcAppIds: List<Int>,
            branch: String,
            includeInstalledDepots: Boolean,
            enableVerify: Boolean,
            allowPersistedProgress: Boolean = false,
            hasPersistedResumeRow: Boolean = false,
            customInstallPath: String? = null,
            downloadTaskType: String = DownloadRecord.TASK_INSTALL,
            targetDepotIds: Set<Int>? = null,
        ): DownloadInfo? {
            var appDirPath = getAppDirPath(appId)
            Timber.i("downloadApp called for appId: $appId, customInstallPath: $customInstallPath")
            Timber.i(
                "Steam DLC selection: appId=$appId selectedDlcAppIds=${userSelectedDlcAppIds.sorted()} " +
                    "includeInstalledDepots=$includeInstalledDepots verify=$enableVerify allowResume=$allowPersistedProgress " +
                    "targetDepotIds=${targetDepotIds?.sorted().orEmpty()}",
            )

            activeDownloadRecordFor(appId)?.let { activeRecord ->
                val requestedScopeIds =
                    if (downloadTaskType == DownloadRecord.TASK_UPDATE && targetDepotIds != null) {
                        targetDepotIds
                    } else {
                        userSelectedDlcAppIds.toSet()
                    }
                val isSameCoordinatorDispatch =
                    customInstallPath == null &&
                        activeRecord.taskType == downloadTaskType &&
                        parseDownloadScopeIds(activeRecord.selectedDlcs) == requestedScopeIds
                if (!isSameCoordinatorDispatch) {
                    return rejectConflictingDownloadRequest(appId, activeRecord)
                }
            }

            if (customInstallPath != null) {
                // Determine if customInstallPath is the game folder itself or the parent
                val appInfo = getAppInfoOf(appId)
                val folderName = getAppDirName(appInfo)
                val safeFolderName = if (folderName.isNotEmpty()) folderName else appId.toString()

                val customFile = File(customInstallPath)
                val finalPath =
                    if (customFile.name.equals(safeFolderName, ignoreCase = true)) {
                        // User selected the game folder itself
                        normalizeInstallPath(customFile.absolutePath)
                    } else {
                        // User selected parent folder, create/use subfolder
                        normalizeInstallPath(File(customInstallPath, safeFolderName).absolutePath)
                    }

                appDirPath = finalPath
                Timber.i("Final custom appDirPath: $appDirPath")

                // Update SteamApp in DB
                runBlocking {
                    if (appInfo != null) {
                        val updatedApp = appInfo.copy(installDir = finalPath)
                        instance?.appDao?.update(updatedApp)
                        Timber.i("Updated SteamApp installDir in DB to: $finalPath")
                    }
                }
            }

            val hasTrustedInstallAtStart =
                customInstallPath == null &&
                    getTrustedInstalledAppInfo(appId) != null
            val isAddingDlcToTrustedInstall =
                hasTrustedInstallAtStart &&
                    !includeInstalledDepots &&
                    userSelectedDlcAppIds.isNotEmpty()

            // Ensure the download directory exists
            try {
                val dir = File(appDirPath)
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        Timber.i("Created download directory: $appDirPath")
                    } else {
                        Timber.e("Failed to create download directory (mkdirs returned false): $appDirPath")
                        instance?.let { service ->
                            service.scope.launch(Dispatchers.Main) {
                                WinToast.show(
                                    service.applicationContext,
                                    "Failed to create download directory. Check permissions.",
                                    Toast.LENGTH_LONG,
                                )
                            }
                        }
                        return null
                    }
                }

                // Add in-progress marker
                if (!MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) {
                    Timber.e("Failed to add DOWNLOAD_IN_PROGRESS_MARKER at $appDirPath")
                }

                // Fresh installs should reset completion state. When the base game is already
                // trusted, keep the marker while adding DLC so a cancelled DLC download does
                // not make the whole base install look missing.
                if (downloadTaskType == DownloadRecord.TASK_UPDATE) {
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                } else if (!includeInstalledDepots && !hasTrustedInstallAtStart) {
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error preparing download directory or markers: $appDirPath")
            }

            // If a custom path is provided, we want to force a new download at that location
            if (customInstallPath != null) {
                Timber.i("Custom path provided, cancelling any existing job for appId: $appId")
                downloadJobs[appId]?.cancel("Restarting download at custom path")
                downloadJobs.remove(appId)
            } else {
                // Only return existing job if it's still active
                val existingJob = downloadJobs[appId]
                if (existingJob != null && existingJob.isActive()) {
                    Timber.i("Returning existing active download job for appId: $appId")
                    return existingJob
                }
            }

            Timber.d("Checking depots for appId: $appId. downloadableDepots count: ${downloadableDepots.size}")
            if (downloadableDepots.isEmpty()) {
                Timber.w("Download aborted: downloadableDepots is empty for appId: $appId")
                return null
            }

            val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }
            Timber.d("Indirect DLC app IDs for appId $appId: $indirectDlcAppIds")

            // Depots from Main game
            val mainDepots = getMainAppDepots(appId)
            val groupedBaseDlcDepotIds =
                getAppInfoOf(appId)
                    ?.let { getGroupedBaseAppDlcContentDepotIds(it) }
                    .orEmpty()
            Timber.d("Main app depots count: ${mainDepots.size}")
            val baseMainAppDepots =
                if (isAddingDlcToTrustedInstall) {
                    Timber.i(
                        "Building DLC-only Steam download scope for installed appId=$appId " +
                            "selectedDlcAppIds=${userSelectedDlcAppIds.sorted()}",
                    )
                    emptyMap()
                } else {
                    mainDepots.filter { (depotId, depot) ->
                        depot.dlcAppId == INVALID_APP_ID && depotId !in groupedBaseDlcDepotIds
                    }
                }
            val targetDepotIdSet = targetDepotIds?.takeIf { it.isNotEmpty() }
            var originalMainAppDepots =
                baseMainAppDepots +
                    mainDepots.filter { (_, depot) ->
                        userSelectedDlcAppIds.contains(depot.dlcAppId) &&
                            resolveDepotManifestInfo(depot, branch) != null
                    } +
                    getSelectedBaseAppDlcContentDepots(
                        appId = appId,
                        selectedDlcAppIds = userSelectedDlcAppIds,
                        preferredLanguage = PrefManager.containerLanguage,
                        branch = branch,
                    )
            if (targetDepotIdSet != null) {
                originalMainAppDepots = originalMainAppDepots.filterKeys { it in targetDepotIdSet }
            }
            var mainAppDepots = originalMainAppDepots
            Timber.d("Filtered main app depots count: ${mainAppDepots.size}")

            // Depots from indirect DLC apps (those reachable via findDownloadableDLCApps, which
            // requires a cached license row).
            val indirectDlcAppDepots =
                downloadableDepots.filter { (_, depot) ->
                    !mainAppDepots.map { it.key }.contains(depot.depotId) &&
                        userSelectedDlcAppIds.contains(depot.dlcAppId) &&
                        indirectDlcAppIds.contains(depot.dlcAppId) &&
                        resolveDepotManifestInfo(depot, branch) != null
                }
            Timber.d("Filtered indirect DLC app depots count: ${indirectDlcAppDepots.size}")

            // Selected DLCs whose depots aren't reachable through `indirectDlcAppIds` (e.g. the
            // license row is stale or the DLC is declared on the base game only). Look them up
            // by appId so the download includes the same depots `getDlcOnlyManifestSizes` uses
            // on the store screen — keeping the pre-download estimate and the downloads-tab
            // total in sync.
            val coveredDlcAppIds =
                (originalMainAppDepots.values.asSequence() + indirectDlcAppDepots.values.asSequence())
                    .mapNotNull { d -> d.dlcAppId.takeIf { it != INVALID_APP_ID } }
                    .toSet()
            val missingDlcAppIds = userSelectedDlcAppIds.filter { it !in coveredDlcAppIds }
            val extraDlcAppDepots: Map<Int, DepotInfo> =
                if (missingDlcAppIds.isEmpty()) {
                    emptyMap()
                } else {
                    val appInfoForArch = getAppInfoOf(appId)
                    val extraHas64Bit =
                        appInfoForArch?.depots?.values?.any {
                            it.osArch == OSArch.Arch64 &&
                                (it.osList.contains(OS.windows) || it.osList.isEmpty() || it.osList.contains(OS.none))
                        } ?: false
                    val extraLanguage = PrefManager.containerLanguage
                    val coveredDepotIds = originalMainAppDepots.keys + indirectDlcAppDepots.keys
                    val collected = mutableMapOf<Int, DepotInfo>()
                    for (dlcAppId in missingDlcAppIds) {
                        val dlcAppInfo =
                            runBlocking(Dispatchers.IO) { instance?.appDao?.findApp(dlcAppId) }
                                ?: continue
                        for ((depotId, depot) in dlcAppInfo.depots) {
                            if (depotId in coveredDepotIds || depotId in collected) continue
                            if (!filterForDownloadableDepots(depot, extraHas64Bit, extraLanguage, ownedDlc = null)) continue
                            if (resolveDepotManifestInfo(depot, branch) == null) continue
                            collected[depotId] =
                                DepotInfo(
                                    depotId = depot.depotId,
                                    dlcAppId = dlcAppId,
                                    optionalDlcId = depot.optionalDlcId,
                                    depotFromApp = depot.depotFromApp,
                                    sharedInstall = depot.sharedInstall,
                                    osList = depot.osList,
                                    osArch = depot.osArch,
                                    language = depot.language,
                                    manifests = depot.manifests,
                                    encryptedManifests = depot.encryptedManifests,
                                )
                        }
                    }
                    collected
                }
            if (extraDlcAppDepots.isNotEmpty()) {
                Timber.d("Recovered ${extraDlcAppDepots.size} extra DLC depots for selected DLCs ${missingDlcAppIds}")
            }
            // Single combined view of DLC depots used by the rest of the function. Downstream
            // code groups by dlcAppId, computes totals, and persists DownloadingAppInfo from
            // this map — the extras need to be visible everywhere.
            val dlcAppDepots =
                (indirectDlcAppDepots + extraDlcAppDepots).let { depots ->
                    if (targetDepotIdSet == null) depots else depots.filterKeys { it in targetDepotIdSet }
                }

            // Remove depots that are already downloaded only when install metadata is trusted.
            // But if a custom path is provided, we want to check/download everything at the new location
            var installedApp = getInstalledApp(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            var hasTrustedInstalledState = installedApp?.isDownloaded == true && hasCompleteMarker
            if (!includeInstalledDepots && installedApp != null && !hasTrustedInstalledState && customInstallPath == null) {
                val hasStaleInstallMetadata =
                    installedApp.isDownloaded ||
                        installedApp.downloadedDepots.isNotEmpty() ||
                        installedApp.dlcDepots.isNotEmpty()
                if (hasStaleInstallMetadata) {
                    Timber.w(
                        "Clearing stale install metadata for appId=$appId " +
                            "(isDownloaded=${installedApp.isDownloaded}, marker=$hasCompleteMarker)",
                    )
                    runBlocking(Dispatchers.IO) {
                        instance?.appInfoDao?.deleteApp(appId)
                    }
                    installedApp = null
                }
                hasTrustedInstalledState = false
            }
            if (installedApp != null && !includeInstalledDepots && hasTrustedInstalledState && customInstallPath == null) {
                val beforeCount = mainAppDepots.size
                mainAppDepots = mainAppDepots.filter { it.key !in installedApp.downloadedDepots }
                Timber.d("Removed already downloaded depots. Count before: $beforeCount, after: ${mainAppDepots.size}")
            }

            // Resume support (Phase 9 — C++ depot downloader). The C++
            // DepotConfigStore (.DepotDownloader/depot.config) records each
            // depot's state: begin_depot is written before any file lands and
            // finish_depot ONLY after every file of the depot is fully
            // written. A "finished" entry therefore always means a complete
            // depot — unlike the old JavaSteam store it is SAFE to keep
            // across a pause/resume. download() skips finished depots and
            // write_depot() resumes the in-progress one chunk-by-chunk
            // (verifying each chunk's bytes already on disk). So only treat
            // the download as "fresh" — discarding depot.config so every
            // depot is re-examined — for a brand-new install (no depot.config
            // yet) or an explicit verify pass. This is what makes pause →
            // resume continue from saved progress instead of restarting.
            val depotConfigFile = File(File(appDirPath, ".DepotDownloader"), "depot.config")
            val isFreshDownload =
                downloadTaskType == DownloadRecord.TASK_VERIFY || !depotConfigFile.exists()
            Timber.i(
                "Download fresh=$isFreshDownload for appId=$appId " +
                    "(task=$downloadTaskType, depotConfigExists=${depotConfigFile.exists()})",
            )

            val allDepots = originalMainAppDepots + dlcAppDepots
            // Use install (uncompressed) size for progress tracking.
            // resolveDepotManifestInfo follows depot.depotFromApp and falls back to the public
            // branch, so shared/proxied DLC depots (whose real manifest lives on the base app)
            // contribute their full size to the total instead of the 1L fallback.
            val depotSizeById =
                allDepots.mapValues { (_, depot) ->
                    val mInfo = resolveDepotManifestInfo(depot, branch)
                    (mInfo?.size ?: 1L).coerceAtLeast(1L)
                }

            // Load persisted progress snapshot to skip fully downloaded depots.
            // Mutable so the safety check below can drop suspicious entries before they
            // poison di.depotCumulativeUncompressedBytes during resume init.
            var persistedDepotBytes: Map<Int, Long> =
                if (allowPersistedProgress) {
                    DownloadInfo.loadPersistedDepotBytes(appDirPath)
                } else {
                    emptyMap()
                }

            // SAFETY CHECK (scope-mismatch detection): if the persisted snapshot remembers
            // depots that aren't in the current resume scope, the userSelectedDlcAppIds we
            // resolved is a SUBSET of what was originally downloading. Continuing would
            // download only the subset and then incorrectly mark the game COMPLETE because
            // selectedDepots would empty out before the missing DLCs ever get registered.
            // Refuse to proceed — the user can cancel + redownload to recover.
            if (allowPersistedProgress && persistedDepotBytes.isNotEmpty()) {
                val depotsInScope = allDepots.keys
                val orphanSnapshotDepots = persistedDepotBytes.keys - depotsInScope
                if (orphanSnapshotDepots.isNotEmpty()) {
                    Timber.e(
                        "Resume scope mismatch for appId=$appId: snapshot has depot(s) " +
                            "$orphanSnapshotDepots that are not in the current download scope " +
                            "(scope depots: $depotsInScope). The DLC list used to resume is " +
                            "incomplete; refusing to finalize to avoid a partial-COMPLETE.",
                    )
                    instance?.let { service ->
                        service.scope.launch(Dispatchers.Main) {
                            WinToast.show(
                                service.applicationContext,
                                "Resume failed: download scope changed. Please cancel and re-download.",
                                Toast.LENGTH_LONG,
                            )
                        }
                    }
                    val info = DownloadInfo(1, appId, CopyOnWriteArrayList(listOf(appId)))
                    info.updateStatus(DownloadPhase.FAILED, "Resume scope mismatch — cancel and re-download")
                    info.setActive(false)
                    downloadJobs[appId] = info
                    notifyDownloadStarted(appId)
                    runBlocking {
                        DownloadCoordinator.notifyFinished(
                            DownloadRecord.STORE_STEAM,
                            appId.toString(),
                            DownloadRecord.STATUS_FAILED,
                            "Resume scope mismatch",
                        )
                    }
                    return info
                }
            }

            val fullyDownloadedDepotsFromSnapshot = mutableSetOf<Int>()
            if (persistedDepotBytes.isNotEmpty()) {
                for ((depotId, _) in allDepots) {
                    val depotSize = depotSizeById[depotId] ?: 1L
                    val downloadedBytes = persistedDepotBytes[depotId] ?: 0L
                    Timber.d(
                        "Resume snapshot for appId=$appId depot=$depotId: persisted=$downloadedBytes / size=$depotSize " +
                            (if (downloadedBytes >= depotSize) "-> SKIP-CANDIDATE" else "-> include"),
                    )
                    if (downloadedBytes >= depotSize) {
                        fullyDownloadedDepotsFromSnapshot.add(depotId)
                    }
                }
                if (fullyDownloadedDepotsFromSnapshot.isNotEmpty()) {
                    // CRITICAL safety: only TRUST the snapshot's "fully downloaded" claim
                    // when the COMPLETE marker exists for the game. Without that marker the
                    // game was a partial download — and we have a history of snapshot
                    // corruption pushing depots to depotSize prematurely (e.g., race
                    // attribution in older builds, or interrupted onDepotCompleted paths).
                    // For partial downloads, let DepotDownloader's per-file checksum
                    // validation handle resume: it'll skip files that already match on disk
                    // (cheap), and re-download missing chunks.
                    if (hasCompleteMarker) {
                        Timber.i(
                            "Skipping ${fullyDownloadedDepotsFromSnapshot.size} fully downloaded depots from snapshot " +
                                "(COMPLETE marker present): $fullyDownloadedDepotsFromSnapshot",
                        )
                        mainAppDepots = mainAppDepots.filter { it.key !in fullyDownloadedDepotsFromSnapshot }
                    } else {
                        Timber.w(
                            "REFUSING to skip ${fullyDownloadedDepotsFromSnapshot.size} depots claimed full by snapshot " +
                                "for appId=$appId because COMPLETE marker is absent. Depots will be re-validated " +
                                "by the downloader: $fullyDownloadedDepotsFromSnapshot",
                        )
                        // Drop the suspicious entries from persistedDepotBytes so they don't
                        // get re-loaded into di.depotCumulativeUncompressedBytes (which would
                        // re-persist them at depotSize on the next pause and re-trigger the
                        // bug). The in-memory tracker for these depots will start at 0 and
                        // grow with real download progress.
                        persistedDepotBytes = persistedDepotBytes.filterKeys {
                            it !in fullyDownloadedDepotsFromSnapshot
                        }
                        // Clear the on-disk snapshot file too. New persists will re-create
                        // it with only the real, current per-depot bytes.
                        clearPersistedProgressSnapshot(appDirPath)
                        fullyDownloadedDepotsFromSnapshot.clear()
                    }
                }
            }

            // Combine main app and DLC depots
            val filteredDlcAppDepots = dlcAppDepots.filter { it.key !in fullyDownloadedDepotsFromSnapshot }
            val selectedDepots = mainAppDepots + filteredDlcAppDepots
            Timber.i("Total selected depots for download: ${selectedDepots.size}")

            if (selectedDepots.isEmpty()) {
                // Check if it was empty even before snapshot filtering
                var preSnapshotMainAppDepots = originalMainAppDepots
                if (installedApp != null && !includeInstalledDepots && hasTrustedInstalledState) {
                    preSnapshotMainAppDepots = preSnapshotMainAppDepots.filter { it.key !in installedApp.downloadedDepots }
                }
                val preSnapshotSelectedDepots = preSnapshotMainAppDepots + dlcAppDepots

                if (preSnapshotSelectedDepots.isEmpty()) {
                    // The download resolved to zero depots. Two very different
                    // situations land here:
                    //  (1) nothing was selected and the base game is already
                    //      installed — a genuine no-op "complete".
                    //  (2) the user EXPLICITLY selected DLC(s) that own no
                    //      downloadable content — entitlement / branch-access
                    //      DLCs (e.g. "Dota 2 - Reborn Beta", appid 373300).
                    // The old code treated both as "Download complete", so (2)
                    // showed the user a silent "Complete / 0 B" that did
                    // nothing — the reported DLC-download bug.
                    val selectedContentlessDlc = userSelectedDlcAppIds.isNotEmpty()
                    Timber.i(
                        "selectedDepots empty for appId=$appId — " +
                            if (selectedContentlessDlc) {
                                "selected DLC(s) $userSelectedDlcAppIds have no downloadable content"
                            } else {
                                "app already installed"
                            },
                    )

                    // Instead of returning null, create a completed job so it shows in UI
                    val info = DownloadInfo(1, appId, CopyOnWriteArrayList(listOf(appId)))
                    info.updateStatus(DownloadPhase.COMPLETE)
                    info.setProgress(1f)
                    downloadJobs[appId] = info

                    if (allowPersistedProgress) {
                        Timber.i("Resume became a no-op; clearing stale persisted resume state")
                        clearFailedResumeState(appId)
                    }

                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)

                    if (selectedContentlessDlc) {
                        // Record the content-less DLC(s) as installed so the DLC
                        // picker reflects reality — they show "Installed" rather
                        // than staying perpetually "available". The user owns
                        // them and there is genuinely nothing to download.
                        runCatching {
                            runBlocking(Dispatchers.IO) {
                                val mainAppInfo = instance?.appInfoDao?.getInstalledApp(appId)
                                if (mainAppInfo != null) {
                                    val updatedDlc =
                                        (mainAppInfo.dlcDepots + userSelectedDlcAppIds)
                                            .distinct()
                                            .sorted()
                                    instance?.appInfoDao?.update(
                                        mainAppInfo.copy(dlcDepots = updatedDlc),
                                    )
                                    Timber.i(
                                        "Marked content-less DLC(s) installed for appId=$appId: dlcDepots=$updatedDlc",
                                    )
                                }
                            }
                        }.onFailure { e ->
                            Timber.w(e, "Failed to record content-less DLC(s) for appId=$appId")
                        }
                    }

                    // Honest message — don't claim a download happened when the
                    // selected DLC simply had no content to fetch.
                    instance?.let { service ->
                        service.scope.launch(Dispatchers.Main) {
                            if (selectedContentlessDlc) {
                                WinToast.show(
                                    service.applicationContext,
                                    "Selected DLC requires no download — marked installed",
                                    Toast.LENGTH_LONG,
                                )
                            } else {
                                WinToast.show(
                                    service.applicationContext,
                                    "Download complete",
                                    Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }

                    return info
                }

                // Snapshot says all depots are complete but marker is missing.
                // Finalize metadata/markers directly instead of re-queuing depots.
                val canFinalizeFromSnapshot =
                    allowPersistedProgress &&
                        fullyDownloadedDepotsFromSnapshot.isNotEmpty() &&
                        (hasCompleteMarker || hasPersistedResumeRow)
                if (canFinalizeFromSnapshot) {
                    Timber.i("All resume depots appear complete from snapshot; finalizing without downloader")
                    val info =
                        finalizeSnapshotResumeAsComplete(
                            appId = appId,
                            appDirPath = appDirPath,
                            mainAppDepots = preSnapshotMainAppDepots,
                            dlcAppDepots = dlcAppDepots,
                            userSelectedDlcAppIds = userSelectedDlcAppIds,
                        )
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    return info
                } else {
                    if (allowPersistedProgress) {
                        if (fullyDownloadedDepotsFromSnapshot.isNotEmpty()) {
                            Timber.w(
                                "Snapshot indicates completion for appId=$appId but state is untrusted " +
                                    "(marker=$hasCompleteMarker, resumeRow=$hasPersistedResumeRow); clearing resume metadata",
                            )
                        } else {
                            Timber.i("selectedDepots resolved empty on resume; clearing stale resume metadata")
                        }
                        clearFailedResumeState(appId)
                    } else {
                        Timber.i("selectedDepots resolved empty after filtering; skipping download start")
                    }
                }
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                return null
            }

            val downloadingAppIds = CopyOnWriteArrayList<Int>()
            val calculatedDlcAppIds = CopyOnWriteArrayList<Int>()
            val allDepotIdsByDlcAppId =
                dlcAppDepots.values
                    .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                    .mapValues { (_, depotIds) -> depotIds.sorted() }
            val selectedDlcDepotIdsByDlcAppId =
                filteredDlcAppDepots.values
                    .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                    .mapValues { (_, depotIds) -> depotIds.sorted() }

            userSelectedDlcAppIds.forEach { dlcAppId ->
                if (allDepotIdsByDlcAppId[dlcAppId]?.isNotEmpty() == true) {
                    downloadingAppIds.add(dlcAppId)
                    calculatedDlcAppIds.add(dlcAppId)
                }
            }

            // Add main app ID if there are main app depots
            if (mainAppDepots.isNotEmpty()) {
                downloadingAppIds.add(appId)
            }

            // There are some apps where DLC content lives under the base app without a
            // dlcAppId on every content depot. Only persist DLC metadata that belongs to
            // the current selected scope; otherwise marker-only DLCs can be falsely saved
            // as installed when a sibling DLC is selected.
            val selectedDlcAppIdSet = userSelectedDlcAppIds.toSet()
            val mainAppDlcIds =
                getMainAppDlcIdsWithoutProperDepotDlcIds(appId)
                    .filterTo(mutableListOf()) { it in selectedDlcAppIdSet }
            mainAppDlcIds.addAll(
                mainAppDepots.values
                    .map { it.dlcAppId }
                    .filter { it != INVALID_APP_ID && it in selectedDlcAppIdSet }
                    .distinct(),
            )

            // If there are no DLC depots, download the main app only
            if (dlcAppDepots.isEmpty()) {
                // Because all dlcIDs are coming from main depots, need to add the dlcID to main app in order to save it to db after finish download
                mainAppDlcIds.addAll(
                    mainAppDepots
                        .filter { it.value.dlcAppId != INVALID_APP_ID && it.value.dlcAppId in selectedDlcAppIdSet }
                        .map { it.value.dlcAppId }
                        .distinct(),
                )
                // Some Steam DLCs are entitlement/config DLC with no separate downloadable
                // depot. They still need to be remembered as selected/installed so launch
                // metadata can expose them later.
                mainAppDlcIds.addAll(userSelectedDlcAppIds)

                // Refresh id List, so only main app is downloaded
                calculatedDlcAppIds.clear()
                downloadingAppIds.clear()
                downloadingAppIds.add(appId)
            }

            Timber.i("Starting download for $appId")
            Timber.i("App contains ${mainAppDepots.size} depot(s): ${mainAppDepots.keys}")
            Timber.i("DLC contains ${dlcAppDepots.size} depot(s): ${dlcAppDepots.keys}")
            Timber.i("downloadingAppIds: $downloadingAppIds")

            val service =
                instance ?: run {
                    Timber.e("SteamService instance is null, cannot start download job.")
                    return null
                }

            val selectedDepotSizes =
                selectedDepots.mapValues { (depotId, _) ->
                    depotSizeById[depotId] ?: 1L
                }
            val selectedTotalBytes = selectedDepotSizes.values.sum()
            val totalBytes = selectedTotalBytes.coerceAtLeast(1L)
            val selectedDisplayDownloadBytes =
                selectedDepots.values
                    .sumOf { depot -> manifestDownloadBytes(resolveDepotManifestInfo(depot, branch)) }
                    .takeIf { it > 0L }
                    ?: totalBytes
            Timber.i(
                "Steam DLC selected download scope: appId=$appId selectedDlcAppIds=${userSelectedDlcAppIds.sorted()} " +
                    "calculatedDlcAppIds=${calculatedDlcAppIds.sorted()} mainDepotIds=${mainAppDepots.keys.sorted()} " +
                    "dlcDepotIdsByApp=$selectedDlcDepotIdsByDlcAppId totalBytes=$totalBytes " +
                    "displayDownloadBytes=$selectedDisplayDownloadBytes metadataDlcAppIds=${mainAppDlcIds.sorted()}",
            )

            // Save downloading app info
            runBlocking {
                service.downloadingAppInfoDao.insert(
                    DownloadingAppInfo(
                        appId,
                        dlcAppIds = userSelectedDlcAppIds,
                    ),
                )
                Unit
            }
            Timber.i(
                "Steam DLC selection persisted: appId=$appId selectedDlcAppIds=${userSelectedDlcAppIds.sorted()} " +
                    "installPath=$appDirPath",
            )

            // Ask the global coordinator whether this download can start now or must be queued
            // behind downloads from other stores too. The coordinator persists the decision in
            // its DownloadRecord table so the request survives an app restart.
            val coordDecision =
                runBlocking {
                    val title = getAppInfoOf(appId)?.name.orEmpty()
                    val persistedScope =
                        if (downloadTaskType == DownloadRecord.TASK_UPDATE && targetDepotIdSet != null) {
                            targetDepotIdSet.sorted().joinToString(",")
                        } else {
                            userSelectedDlcAppIds.joinToString(",")
                        }
                    DownloadCoordinator.requestSlot(
                        store = DownloadRecord.STORE_STEAM,
                        storeGameId = appId.toString(),
                        title = title,
                        installPath = appDirPath,
                        selectedDlcs = persistedScope,
                        taskType = downloadTaskType,
                        bytesTotal = selectedDisplayDownloadBytes,
                    )
                }
            Timber.i(
                "Steam DLC coordinator record: appId=$appId selectedDlcAppIds=${userSelectedDlcAppIds.sorted()} " +
                    "bytesTotal=$totalBytes displayDownloadBytes=$selectedDisplayDownloadBytes " +
                    "decision=${coordDecision::class.simpleName}",
            )
            if (coordDecision is DownloadCoordinator.Decision.Queue) {
                Timber.i("Coordinator queued appId: $appId")
                if (downloadTaskType == DownloadRecord.TASK_UPDATE) {
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                }
                val info =
                    DownloadInfo(selectedDepots.size, appId, downloadingAppIds).also { di ->
                        di.setPersistencePath(appDirPath)
                        di.setTotalExpectedBytes(totalBytes)
                        di.setDisplayTotalExpectedBytes(selectedDisplayDownloadBytes)
                        di.updateStatus(DownloadPhase.QUEUED, "Queued...")
                        di.setActive(false)
                    }
                downloadJobs[appId] = info
                notifyDownloadStarted(appId)
                return info
            }

            val info =
                DownloadInfo(selectedDepots.size, appId, downloadingAppIds).also { di ->
                    di.setPersistencePath(appDirPath)

                    // Set weights for each depot based on manifest sizes
                    selectedDepots.keys.forEachIndexed { index, depotId ->
                        di.setWeight(index, selectedDepotSizes[depotId] ?: 1L)
                    }

                    // Track progress only for depots in this active run so excluded/complete depots
                    // (including DLC already marked complete) cannot pre-fill progress at startup.

                    // Total expected size (used for ETA based on recent download speed)
                    di.setTotalExpectedBytes(totalBytes)
                    di.setDisplayTotalExpectedBytes(selectedDisplayDownloadBytes)

                    var resumedBytes = 0L

                    if (allowPersistedProgress) {
                        for ((depotId, bytes) in persistedDepotBytes) {
                            // If the depot was excluded because it's fully downloaded, we still need to track its bytes
                            // so that future snapshots retain this progress.
                            val depotSize = depotSizeById[depotId] ?: continue
                            val safeBytes = bytes.coerceIn(0L, depotSize)
                            di.depotCumulativeUncompressedBytes[depotId] =
                                java.util.concurrent.atomic
                                    .AtomicLong(safeBytes)
                            // Count resumed bytes only for depots actively downloading in this run.
                            if (depotId in selectedDepots) {
                                resumedBytes += safeBytes
                            }
                            Timber.i(
                                "RESUME-INIT depot=$depotId loaded=$safeBytes (snapshot=$bytes, max=$depotSize, " +
                                    "inSelected=${depotId in selectedDepots}, inSession=${depotId in selectedDepotSizes.keys})",
                            )
                        }
                    } else {
                        // SYNC clear so a stale snapshot from a previous (possibly buggy)
                        // session can't poison this fresh download. Async clear has a race
                        // window where new persists can read or even overwrite with stale
                        // depot byte counts.
                        di.clearPersistedBytesDownloaded(appDirPath, sync = true)
                        Timber.i("RESUME-INIT cleared persisted snapshot (sync) for fresh download appId=$appId")
                    }
                    resumedBytes = resumedBytes.coerceIn(0L, totalBytes)

                    if (resumedBytes > 0L) {
                        di.initializeBytesDownloaded(resumedBytes)
                        Timber.i("Resumed download: initialized with $resumedBytes bytes")
                    }

                    val downloadJob =
                        service.scope.launch {
                            // A WnSteamSession brought up by this worker when no
                            // logged-on wnSession is available. Kept worker-local
                            // (NOT promoted to the global wnSession field) so a
                            // concurrent logOut()/relogin can't close it mid-download.
                            // Disconnected + closed in this worker's finally.
                            var workerWnSession: WnSteamSession? = null
                            try {
                                // Retry loop for transient Steam API failures (AsyncJobFailedException) or missing client
                                val maxRetries = 3
                                var lastException: Exception? = null

                                for (attempt in 1..maxRetries) {
                                    lastException = null
                                    try {
                                        if (attempt > 1) {
                                            Timber.i("Retry attempt $attempt/$maxRetries for appId: $appId")
                                            di.updateStatusMessage("Retrying download (attempt $attempt/$maxRetries)...")
                                            withContext(Dispatchers.Main) {
                                                WinToast.show(
                                                    instance?.applicationContext ?: return@withContext,
                                                    "Retrying download (attempt $attempt/$maxRetries)...",
                                                    Toast.LENGTH_SHORT,
                                                )
                                            }
                                            kotlinx.coroutines.delay(3000L * attempt) // Exponential backoff
                                        }

                                        // Ensure a logged-on WN-Steam-Client C++ session for
                                        // the download (WnSteamSession.state() 3 == LoggedOn).
                                        // The long-lived wnSession is preferred; but it isn't
                                        // guaranteed to be logged on at download time (the app
                                        // may have restored from a cached refresh token, or the
                                        // CM channel idled out), so we bring up + log on a
                                        // session here if needed. The C++ downloader requests
                                        // depot keys itself — no JavaSteam license list needed.
                                        var wnReady = wnSession?.takeIf { it.state() == 3 }
                                        if (wnReady == null) {
                                            // Brief grace period: wnSession may be mid-logon.
                                            var grace = 0
                                            while (grace < 8 && wnSession?.state() != 3) {
                                                di.updateStatusMessage("Waiting for Steam connection...")
                                                delay(1000L)
                                                grace++
                                            }
                                            wnReady = wnSession?.takeIf { it.state() == 3 }
                                        }
                                        if (wnReady == null) {
                                            // Reuse a session this worker already brought up
                                            // on a prior retry attempt, if still logged on.
                                            wnReady = workerWnSession?.takeIf { it.state() == 3 }
                                        }
                                        if (wnReady == null) {
                                            Timber.i("downloadApp: no logged-on wnSession — bringing one up for the download")
                                            di.updateStatusMessage("Connecting to Steam...")
                                            val svc = instance
                                                ?: throw Exception("Steam service unavailable.")
                                            val refreshTok = PrefManager.refreshToken
                                            if (refreshTok.isBlank()) {
                                                throw Exception(
                                                    "Not logged in to Steam (no refresh token). " +
                                                        "Please sign in and try again.",
                                                )
                                            }
                                            // Discard a worker session left over from a prior
                                            // attempt that is no longer logged on.
                                            workerWnSession?.let { stale ->
                                                runCatching { stale.disconnect() }
                                                runCatching { stale.close() }
                                            }
                                            workerWnSession = null
                                            val brought = bringUpWnSession(svc)
                                                ?: throw Exception(
                                                    "WN-Steam-Client: could not connect to Steam.",
                                                )
                                            workerWnSession = brought
                                            // Download-only session: skip the library-populate
                                            // PICS crawl so it doesn't flood the CM while the
                                            // download needs the channel for depot keys.
                                            brought.setAutoPopulateLibrary(false)
                                            di.updateStatusMessage("Logging in to Steam...")
                                            if (!brought.logonWithRefreshToken(
                                                    refreshTok,
                                                    PrefManager.username,
                                                    PrefManager.steamUserSteamId64,
                                                )
                                            ) {
                                                throw Exception("WN-Steam-Client: logon request failed.")
                                            }
                                            var logonWait = 0
                                            while (brought.state() != 3 && logonWait < 30) {
                                                delay(1000L)
                                                logonWait++
                                            }
                                            if (brought.state() != 3) {
                                                throw Exception(
                                                    "WN-Steam-Client: could not log on to Steam. " +
                                                        "Please check your connection.",
                                                )
                                            }
                                            wnReady = brought
                                            Timber.i("downloadApp: WN-Steam session logged on for download")
                                        }

                                        // Stable non-null handle: wnReady is a mutable var
                                        // above, so capture it once for the download below.
                                        val wnSessionForDownload = wnReady
                                            ?: throw Exception("WN-Steam-Client session unavailable.")

                                        Timber.i("Initializing WN-Steam downloader for appId: $appId (attempt $attempt)")
                                        di.updateStatusMessage("Initializing downloader...")

                                        // CA bundle for HTTPS CDN verification (same file
                                        // CaBundleExtractor provides for the CM session).
                                        val caPath = CaBundleExtractor.ensureBundle(
                                            instance?.applicationContext
                                                ?: throw Exception("Steam service unavailable"),
                                        )

                                        // Total expected bytes across all depots — drives
                                        // di.getProgress(). Use ManifestInfo.size (the
                                        // DECOMPRESSED install size): WnDownloadListener.onProgress
                                        // reports decompressed bytes written, so the denominator
                                        // must be decompressed too (not the compressed .download
                                        // size, which would make the bar overshoot 1.0).
                                        val grandTotalBytes = selectedDepots.values.sumOf { depot ->
                                            resolveDepotManifestInfo(depot, branch)?.size ?: 0L
                                        }
                                        if (grandTotalBytes > 0L) di.setTotalExpectedBytes(grandTotalBytes)

                                        // The C++ downloadApp() takes a single appId (used for
                                        // depot-key entitlement checks), so split the work into
                                        // one batch per app: the main app, then each owned DLC.
                                        // Triple = (appId, depotIds, manifestIds).
                                        val wnBatches: List<Triple<Int, IntArray, LongArray>> = buildList {
                                            if (mainAppDepots.isNotEmpty()) {
                                                val ids = mainAppDepots.keys.sorted()
                                                add(Triple(
                                                    appId,
                                                    ids.toIntArray(),
                                                    ids.map {
                                                        resolveDepotManifestInfo(mainAppDepots[it]!!, branch)?.gid ?: 0L
                                                    }.toLongArray(),
                                                ))
                                            }
                                            calculatedDlcAppIds.forEach { dlcAppId ->
                                                val dlcDepotIds = selectedDlcDepotIdsByDlcAppId[dlcAppId].orEmpty()
                                                if (dlcDepotIds.isEmpty()) return@forEach
                                                Timber.i("Steam DLC batch queued: dlcAppId=$dlcAppId depotIds=$dlcDepotIds")
                                                add(Triple(
                                                    dlcAppId,
                                                    dlcDepotIds.toIntArray(),
                                                    dlcDepotIds.map { depotId ->
                                                        selectedDepots[depotId]?.let {
                                                            resolveDepotManifestInfo(it, branch)?.gid
                                                        } ?: 0L
                                                    }.toLongArray(),
                                                ))
                                            }
                                        }
                                        if (wnBatches.isEmpty()) {
                                            throw Exception("No depots resolved for download.")
                                        }
                                        Timber.i("WN download: ${wnBatches.size} app batch(es) to $appDirPath")

                                        // Steam Controller Config download
                                        val appConfig = getAppInfoOf(appId)?.config
                                        if (appConfig?.steamControllerTemplateIndex == 1) {
                                            val controllerConfig =
                                                appConfig.steamControllerConfigDetails
                                                    .let { selectSteamControllerConfig(it) }

                                            if (controllerConfig != null) {
                                                val publishedFileId = controllerConfig.publishedFileId
                                                runCatching {
                                                    val requestBody =
                                                        FormBody
                                                            .Builder()
                                                            .add(
                                                                "itemcount",
                                                                "1",
                                                            ).add("publishedfileids[0]", publishedFileId.toString())
                                                            .build()
                                                    val request =
                                                        Request
                                                            .Builder()
                                                            .url(
                                                                "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1",
                                                            ).post(requestBody)
                                                            .build()
                                                    Net.http.newCall(request).execute().use { response ->
                                                        if (response.isSuccessful) {
                                                            val responseBody = response.body?.string()
                                                            if (!responseBody.isNullOrEmpty()) {
                                                                val responseJson = JSONObject(responseBody)
                                                                val responseData = responseJson.optJSONObject("response")
                                                                val fileUrl =
                                                                    responseData
                                                                        ?.optJSONArray(
                                                                            "publishedfiledetails",
                                                                        )?.optJSONObject(0)
                                                                        ?.optString("file_url", "")
                                                                        ?.trim()
                                                                if (!fileUrl.isNullOrEmpty()) {
                                                                    val configFile = File(appDirPath, STEAM_CONTROLLER_CONFIG_FILENAME)
                                                                    val downloadRequest =
                                                                        Request
                                                                            .Builder()
                                                                            .url(fileUrl)
                                                                            .get()
                                                                            .build()
                                                                    Net.http.newCall(downloadRequest).execute().use { downloadResponse ->
                                                                        if (downloadResponse.isSuccessful) {
                                                                            downloadResponse.body?.byteStream()?.use { input ->
                                                                                configFile.outputStream().use { output ->
                                                                                    input.copyTo(output)
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Run each app batch through the C++ WN-Steam-Client
                                        // downloader. downloadApp() runs on a native worker
                                        // thread; suspendCancellableCoroutine bridges its
                                        // WnDownloadListener.onComplete back to this coroutine.
                                        // Progress maps per-depot cumulative bytes into
                                        // di.updateBytesDownloaded(delta).
                                        Timber.i("Downloading game to $appDirPath (attempt $attempt)")
                                        val wnDepotBytes = java.util.concurrent.ConcurrentHashMap<Int, Long>()
                                        val wnGlobalPrev = java.util.concurrent.atomic.AtomicLong(0L)
                                        // Throttle for DownloadRecord progress persistence — a DB
                                        // write per chunk would be far too frequent.
                                        val wnLastPersistMs = java.util.concurrent.atomic.AtomicLong(0L)
                                        for (batch in wnBatches) {
                                            val (batchAppId, batchDepotIds, batchManifestIds) = batch
                                            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                                                wnSessionForDownload.downloadApp(
                                                    batchAppId,
                                                    batchDepotIds,
                                                    batchManifestIds,
                                                    branch,
                                                    appDirPath,
                                                    isFreshDownload,
                                                    caPath,
                                                    // "Download Speed" setting → parallel
                                                    // chunk-download worker count.
                                                    PrefManager.downloadSpeed,
                                                    object : WnDownloadListener {
                                                        override fun onProgress(
                                                            depotId: Int,
                                                            depotDone: Long,
                                                            depotTotal: Long,
                                                            depotsDone: Int,
                                                            depotsTotal: Int,
                                                            verifying: Boolean,
                                                        ) {
                                                            // The native download worker runs on its own thread
                                                            // and may fire a few late callbacks after a pause /
                                                            // cancel (between the cancel flag being set and the
                                                            // worker unwinding). Ignore them — otherwise this
                                                            // would overwrite the PAUSED phase back to DOWNLOADING.
                                                            if (!di.isActive()) return
                                                            wnDepotBytes[depotId] = depotDone
                                                            // Record per-depot cumulative bytes so the
                                                            // throttled progress snapshot (depot_bytes.json)
                                                            // stays accurate — on the next resume this lets
                                                            // the UI restore the real % instead of starting
                                                            // the bar at 0 while write_depot re-verifies.
                                                            di.depotCumulativeUncompressedBytes
                                                                .getOrPut(depotId) {
                                                                    java.util.concurrent.atomic.AtomicLong(0L)
                                                                }.set(depotDone)
                                                            di.markProgressSnapshotDirty()
                                                            val g = wnDepotBytes.values.sum()
                                                            val delta = g - wnGlobalPrev.getAndSet(g)
                                                            if (delta > 0L) di.updateBytesDownloaded(delta)
                                                            // Drive the phase from the native `verifying`
                                                            // flag — VERIFYING while validating on-disk
                                                            // content, DOWNLOADING while actually fetching
                                                            // from the CDN — so a verify pass reads
                                                            // "Verifying" and only flips to "Downloading"
                                                            // once it starts pulling missing files. The
                                                            // status message carries a unique suffix (g)
                                                            // every tick: the Downloads row collects it via
                                                            // collectAsState() and a StateFlow dedups equal
                                                            // values, so a constant message would freeze
                                                            // the live byte count / speed; a changing value
                                                            // forces the recomposition that re-reads them.
                                                            di.updateStatus(
                                                                if (verifying) {
                                                                    DownloadPhase.VERIFYING
                                                                } else {
                                                                    DownloadPhase.DOWNLOADING
                                                                },
                                                                if (verifying) {
                                                                    "Verifying depot $depotId ($g)"
                                                                } else {
                                                                    "Downloading depot $depotId ($g)"
                                                                },
                                                            )
                                                            // Also notify the progress-bar listeners.
                                                            di.emitProgressChange()
                                                            // Persist progress to the DownloadRecord
                                                            // (throttled to 3s) so an app restart
                                                            // restores the real % instead of 0.
                                                            val nowMs = System.currentTimeMillis()
                                                            if (nowMs - wnLastPersistMs.get() >= 3000L) {
                                                                wnLastPersistMs.set(nowMs)
                                                                val (dispDone, dispTotal) =
                                                                    di.getDisplayBytesProgress()
                                                                DownloadCoordinator.updateProgress(
                                                                    DownloadRecord.STORE_STEAM,
                                                                    appId.toString(),
                                                                    dispDone,
                                                                    dispTotal,
                                                                )
                                                            }
                                                        }

                                                        override fun onComplete(
                                                            success: Boolean,
                                                            error: String,
                                                            bytesWritten: Long,
                                                            depotsCompleted: Int,
                                                            depotsSkipped: Int,
                                                        ) {
                                                            if (!cont.isActive) return
                                                            if (success) {
                                                                cont.resumeWith(Result.success(Unit))
                                                            } else if (!di.isActive() || di.isCancelling) {
                                                                // Pause/cancel aborted the native download — resume
                                                                // normally; the post-await barrier below classifies
                                                                // it as PAUSED/CANCELLED instead of a spurious FAILED.
                                                                cont.resumeWith(Result.success(Unit))
                                                            } else {
                                                                cont.resumeWith(
                                                                    Result.failure(
                                                                        Exception(
                                                                            "WN download failed (app $batchAppId): $error",
                                                                        ),
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    },
                                                )
                                                // Pause/cancel cancels this coroutine — abort the
                                                // native download worker so it stops promptly
                                                // instead of running on in the background.
                                                cont.invokeOnCancellation {
                                                    runCatching { wnSessionForDownload.cancelDownload() }
                                                }
                                            }
                                        }

                                        // Hard barrier: even if completion.await returned without
                                        // throwing, re-check for cancellation. JavaSteam can complete
                                        // its CompletableFuture as a side-effect of pending chunks
                                        // being cancelled (pendingChunks drains to 0 → finishDepot
                                        // Download fires) — in that race we must NOT proceed to
                                        // completeAppDownload, which would set the COMPLETE marker
                                        // for a paused/partial install.
                                        coroutineContext.ensureActive()
                                        if (!di.isActive() || di.isCancelling) {
                                            Timber.i(
                                                "DepotDownloader completion returned but DownloadInfo is no longer active " +
                                                    "(isActive=${di.isActive()}, isCancelling=${di.isCancelling}). " +
                                                    "Skipping completeAppDownload — the user paused or cancelled.",
                                            )
                                            throw CancellationException(
                                                if (di.isCancelling) "Cancelled by user" else "Paused by user",
                                            )
                                        }

                                        Timber.i("DepotDownloader finished for appId: $appId")

                                        // If it was extremely fast (e.g. already downloaded), ensure some visibility in UI
                                        if (di.getProgress() >= 1.0f) {
                                            delay(1000)
                                        }

                                        // If we got here without exception, download succeeded
                                        break
                                    } catch (e: AsyncJobFailedException) {
                                        lastException = e
                                        Timber.w(e, "AsyncJobFailedException on attempt $attempt/$maxRetries for appId: $appId")
                                        if (attempt >= maxRetries) {
                                            Timber.e("All $maxRetries retry attempts failed for appId: $appId")
                                            throw e
                                        }
                                        di.setActive(true)
                                        continue
                                    }
                                }

                                // Complete app download - Wrap in try-catch to ensure we don't crash at the finish line
                                try {
                                    di.updateStatusMessage("Finalizing installation...")
                                    Timber.i("Finalizing installation at path: $appDirPath")
                                    if (originalMainAppDepots.isNotEmpty()) {
                                        val mainAppDepotIds = originalMainAppDepots.keys.sorted()
                                        completeAppDownload(di, appId, mainAppDepotIds, mainAppDlcIds, appDirPath)
                                    }

                                    calculatedDlcAppIds.forEach { dlcAppId ->
                                        val dlcDepotIds = selectedDlcDepotIdsByDlcAppId[dlcAppId].orEmpty()
                                        completeAppDownload(di, dlcAppId, dlcDepotIds, emptyList(), appDirPath)
                                    }
                                    Timber.i("Installation finalized for appId: $appId")

                                    // Show success message to user
                                    instance?.let { service ->
                                        service.scope.launch(Dispatchers.Main) {
                                            WinToast.show(service.applicationContext, "Download complete", Toast.LENGTH_SHORT)
                                            Unit
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Error during finalize/database update for appId: $appId")
                                    throw e
                                }

                                // Remove the job here
                                removeDownloadJob(appId)

                                // Remove the downloading app info
                                runBlocking {
                                    instance?.downloadingAppInfoDao?.deleteApp(appId)
                                    Unit
                                }
                                Unit
                            } catch (e: DownloadFailedException) {
                                Timber.d(e, "Download failed for app $appId via cancellation")
                                clearFailedResumeState(appId)
                                di.updateStatus(DownloadPhase.FAILED)
                                di.setActive(false)
                                // Clean up markers
                                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                                if (downloadTaskType == DownloadRecord.TASK_UPDATE) {
                                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                                }
                                runBlocking {
                                    DownloadCoordinator.notifyFinished(
                                        DownloadRecord.STORE_STEAM,
                                        appId.toString(),
                                        DownloadRecord.STATUS_FAILED,
                                        e.message,
                                    )
                                }
                                removeDownloadJob(appId)
                                return@launch
                            } catch (e: CancellationException) {
                                if (di.isDeleting) {
                                    Timber.d("Download cancelled for deletion for app $appId")
                                    return@launch
                                }

                                if (di.isCancelling) {
                                    Timber.d("Download cancelled by user for app $appId")
                                    di.persistProgressSnapshot(force = true)
                                    di.updateStatus(DownloadPhase.CANCELLED)
                                    di.setActive(false)
                                    runBlocking {
                                        DownloadCoordinator.notifyFinished(
                                            DownloadRecord.STORE_STEAM,
                                            appId.toString(),
                                            DownloadRecord.STATUS_CANCELLED,
                                        )
                                    }
                                    throw e
                                }

                                Timber.d(e, "Download paused for app $appId")
                                // Keep downloadingAppInfo on cancellation so resume does not fall into verify mode.
                                di.persistProgressSnapshot(force = true)
                                di.updateStatus(DownloadPhase.PAUSED)
                                di.setActive(false)
                                runBlocking {
                                    DownloadCoordinator.notifyFinished(
                                        DownloadRecord.STORE_STEAM,
                                        appId.toString(),
                                        DownloadRecord.STATUS_PAUSED,
                                    )
                                }
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "Download failed for app $appId")
                                clearFailedResumeState(appId)

                                val errorMsg =
                                    when (e) {
                                        is ClassCastException -> "Casting error: ${e.message}"
                                        is NullPointerException -> "Null reference: ${e.message}"
                                        else -> e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                                    }

                                di.updateStatus(DownloadPhase.FAILED, errorMsg)
                                di.setActive(false)
                                // Clean up markers and DB state
                                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                                if (downloadTaskType == DownloadRecord.TASK_UPDATE) {
                                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                                }
                                runBlocking {
                                    instance?.downloadingAppInfoDao?.deleteApp(appId)
                                    Unit
                                }
                                runBlocking {
                                    DownloadCoordinator.notifyFinished(
                                        DownloadRecord.STORE_STEAM,
                                        appId.toString(),
                                        DownloadRecord.STATUS_FAILED,
                                        errorMsg,
                                    )
                                }
                                removeDownloadJob(appId)
                                // Show error to user
                                instance?.let { service ->
                                    service.scope.launch(Dispatchers.Main) {
                                        WinToast.show(service.applicationContext, "Download failed: $errorMsg", Toast.LENGTH_LONG)
                                        Unit
                                    }
                                }
                                PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
                                Unit
                            } finally {
                                // Tear down a session this worker brought up itself.
                                workerWnSession?.let { ws ->
                                    runCatching { ws.disconnect() }
                                    runCatching { ws.close() }
                                    Timber.i("downloadApp: closed worker WN-Steam session for app $appId")
                                }
                                workerWnSession = null
                                Unit
                            }
                            Unit
                        }
                    downloadJob.invokeOnCompletion { throwable ->
                        if (throwable is CancellationException && throwable !is DownloadFailedException) {
                            if (di.isDeleting) {
                                // Deletion handled externally
                            } else if (di.isCancelling) {
                                // Keep in downloadJobs for UI visibility, but still check queue
                                checkQueue()
                            } else {
                                Timber.d(throwable, "Download paused for app $appId")
                                removeDownloadJob(appId)
                            }
                        }
                    }
                    di.setDownloadJob(downloadJob)
                }

            downloadJobs[appId] = info
            info.updateStatus(DownloadPhase.PREPARING)
            notifyDownloadStarted(appId)
            return info
        }

        private fun finalizeSnapshotResumeAsComplete(
            appId: Int,
            appDirPath: String,
            mainAppDepots: Map<Int, DepotInfo>,
            dlcAppDepots: Map<Int, DepotInfo>,
            userSelectedDlcAppIds: List<Int>,
        ): DownloadInfo {
            val downloadingAppIds = CopyOnWriteArrayList<Int>()
            val calculatedDlcAppIds = CopyOnWriteArrayList<Int>()
            val allDepotIdsByDlcAppId =
                dlcAppDepots.values
                    .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                    .mapValues { (_, depotIds) -> depotIds.sorted() }

            userSelectedDlcAppIds.forEach { dlcAppId ->
                if (allDepotIdsByDlcAppId[dlcAppId]?.isNotEmpty() == true) {
                    downloadingAppIds.add(dlcAppId)
                    calculatedDlcAppIds.add(dlcAppId)
                }
            }

            // Add main app ID if there are main app depots
            if (mainAppDepots.isNotEmpty() && !downloadingAppIds.contains(appId)) {
                downloadingAppIds.add(appId)
            }

            val info = DownloadInfo(1, appId, downloadingAppIds)
            info.setPersistencePath(appDirPath)
            info.updateStatus(DownloadPhase.COMPLETE)
            info.setProgress(1f)
            downloadJobs[appId] = info
            notifyDownloadStarted(appId)

            val selectedDlcAppIdSet = userSelectedDlcAppIds.toSet()
            val mainAppDlcIds =
                getMainAppDlcIdsWithoutProperDepotDlcIds(appId)
                    .filterTo(mutableListOf()) { it in selectedDlcAppIdSet }
            mainAppDlcIds.addAll(
                mainAppDepots.values
                    .map { it.dlcAppId }
                    .filter { it != INVALID_APP_ID && it in selectedDlcAppIdSet }
                    .distinct(),
            )
            if (dlcAppDepots.isEmpty()) {
                mainAppDlcIds.addAll(
                    mainAppDepots
                        .filter { it.value.dlcAppId != INVALID_APP_ID && it.value.dlcAppId in selectedDlcAppIdSet }
                        .map { it.value.dlcAppId }
                        .distinct(),
                )
            }

            runBlocking(Dispatchers.IO) {
                if (mainAppDepots.isNotEmpty()) {
                    completeAppDownload(
                        downloadInfo = info,
                        downloadingAppId = appId,
                        entitledDepotIds = mainAppDepots.keys.sorted(),
                        selectedDlcAppIds = mainAppDlcIds,
                        appDirPath = appDirPath,
                    )
                }

                calculatedDlcAppIds.forEach { dlcAppId ->
                    val dlcDepotIds = allDepotIdsByDlcAppId[dlcAppId].orEmpty()
                    completeAppDownload(
                        downloadInfo = info,
                        downloadingAppId = dlcAppId,
                        entitledDepotIds = dlcDepotIds,
                        selectedDlcAppIds = emptyList(),
                        appDirPath = appDirPath,
                    )
                }

                instance?.downloadingAppInfoDao?.deleteApp(appId)
                Unit
            }

            // Show success message to user for no-op/resume completion
            instance?.let { service ->
                service.scope.launch(Dispatchers.Main) {
                    WinToast.show(service.applicationContext, "Download complete", Toast.LENGTH_SHORT)
                    Unit
                }
            }
            return info
        }

        private suspend fun completeAppDownload(
            downloadInfo: DownloadInfo,
            downloadingAppId: Int,
            entitledDepotIds: List<Int>,
            selectedDlcAppIds: List<Int>,
            appDirPath: String,
        ) {
            Timber.i("Item $downloadingAppId download completed, saving database")
            Timber.i(
                "Steam DLC downloaded item: baseAppId=${downloadInfo.gameId} completedAppId=$downloadingAppId " +
                    "entitledDepotIds=${entitledDepotIds.sorted()} selectedDlcAppIds=${selectedDlcAppIds.sorted()} " +
                    "remainingAppIds=${downloadInfo.downloadingAppIds.sorted()}",
            )

            // Update database
            val appInfo = instance?.appInfoDao?.getInstalledApp(downloadingAppId)

            // Update Saved AppInfo
            if (appInfo != null) {
                val updatedDownloadedDepots = (appInfo.downloadedDepots + entitledDepotIds).distinct()
                val updatedDlcDepots = (appInfo.dlcDepots + selectedDlcAppIds).distinct()

                instance?.appInfoDao?.update(
                    AppInfo(
                        downloadingAppId,
                        isDownloaded = true,
                        downloadedDepots = updatedDownloadedDepots.sorted(),
                        dlcDepots = updatedDlcDepots.sorted(),
                    ),
                )
            } else {
                instance?.appInfoDao?.insert(
                    AppInfo(
                        downloadingAppId,
                        isDownloaded = true,
                        downloadedDepots = entitledDepotIds.sorted(),
                        dlcDepots = selectedDlcAppIds.sorted(),
                    ),
                )
            }

            // Remove completed appId from downloadInfo.dlcAppIds and check if it was actually removed
            val wasRemoved = downloadInfo.downloadingAppIds.remove(downloadingAppId)
            if (!wasRemoved) {
                Timber.d("Item $downloadingAppId was already removed from downloading list, skipping redundant completion.")
                return
            }

            // All downloading appIds are removed
            if (downloadInfo.downloadingAppIds.isEmpty()) {
                Timber.i("All items for game ${downloadInfo.gameId} completed, running final completion logic.")
                Timber.i(
                    "Steam DLC download complete: appId=${downloadInfo.gameId} " +
                        "downloadedBytes=${downloadInfo.getBytesDownloaded()} totalBytes=${downloadInfo.getTotalExpectedBytes()}",
                )
                // Settle the remaining bytes once at the end so the visible progress doesn't
                // sit slightly under 100% when the game is actually complete (e.g. dedup
                // skipped chunks that never reported via onChunkCompleted).
                val totalExpectedBytes = downloadInfo.getTotalExpectedBytes()
                if (totalExpectedBytes > 0L) {
                    val downloadedBytes = downloadInfo.getBytesDownloaded()
                    val remainingBytes = (totalExpectedBytes - downloadedBytes).coerceAtLeast(0L)
                    if (remainingBytes > 0L) {
                        downloadInfo.updateBytesDownloaded(remainingBytes, System.currentTimeMillis())
                        downloadInfo.emitProgressChange()
                        updateCoordinatorDownloadProgress(downloadInfo)
                    }
                }

                // Handle completion: add markers
                withContext(Dispatchers.IO) {
                    MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DLL_REPLACED)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_COLDCLIENT_USED)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DRM_PATCHED)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DRM_UNPACK_CHECKED)

                    // Ensure the main app is marked as downloaded in the DB
                    val mainAppId = downloadInfo.gameId
                    val service = instance
                    if (service != null) {
                        val mainAppInfo = service.appInfoDao.getInstalledApp(mainAppId)
                        if (mainAppInfo != null) {
                            val updatedMainDlcDepots = (mainAppInfo.dlcDepots + selectedDlcAppIds).distinct().sorted()
                            service.appInfoDao.update(
                                mainAppInfo.copy(
                                    isDownloaded = true,
                                    dlcDepots = updatedMainDlcDepots,
                                ),
                            )
                            Timber.i(
                                "Marked main app $mainAppId as downloaded in DB with dlcDepots=$updatedMainDlcDepots",
                            )
                        } else {
                            service.appInfoDao.insert(
                                AppInfo(
                                    mainAppId,
                                    isDownloaded = true,
                                    dlcDepots = selectedDlcAppIds.distinct().sorted(),
                                ),
                            )
                            Timber.i("Inserted main app $mainAppId as downloaded in DB with dlcDepots=${selectedDlcAppIds.distinct().sorted()}")
                        }
                    }
                    Unit
                }

                val service = instance
                if (service != null) {
                    createSteamShortcut(service, downloadInfo.gameId)
                }

                // Mark download inactive BEFORE updating status so checkQueue() correctly
                // frees this slot for the next queued download. Without this, isActive() stays
                // true and blocks the queue until the user manually clears the entry.
                downloadInfo.setActive(false)
                downloadInfo.updateStatus(DownloadPhase.COMPLETE)
                PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(downloadInfo.gameId))

                downloadInfo.clearPersistedBytesDownloaded(appDirPath, sync = true)
                // Notify the global coordinator so it can advance the cross-store queue and
                // persist COMPLETE in the records table.
                runBlocking {
                    DownloadCoordinator.notifyFinished(
                        DownloadRecord.STORE_STEAM,
                        downloadInfo.gameId.toString(),
                        DownloadRecord.STATUS_COMPLETE,
                    )
                }
                checkQueue()
            }
            Unit
        }

        private fun updateCoordinatorDownloadProgress(downloadInfo: DownloadInfo) {
            val (displayDownloadedBytes, displayTotalBytes) = downloadInfo.getDisplayBytesProgress()
            DownloadCoordinator.updateProgress(
                DownloadRecord.STORE_STEAM,
                downloadInfo.gameId.toString(),
                displayDownloadedBytes,
                displayTotalBytes,
            )
        }

        fun getWindowsLaunchInfos(appId: Int): List<LaunchInfo> =
            getAppInfoOf(appId)
                ?.let { appInfo ->
                    appInfo.config.launch.filter { launchInfo ->
                        // since configOS was unreliable and configArch was even more unreliable
                        launchInfo.executable.endsWith(".exe")
                    }
                }.orEmpty()

        suspend fun notifyRunningProcesses(vararg gameProcesses: GameProcessInfo) =
            withContext(Dispatchers.IO) {
                instance?.let { steamInstance ->
                    if (isConnected) {
                        val gamesPlayed =
                            gameProcesses.mapNotNull { gameProcess ->
                                getAppInfoOf(gameProcess.appId)?.let { appInfo ->
                                    getPkgInfoOf(gameProcess.appId)?.let { pkgInfo ->
                                        appInfo.branches[gameProcess.branch]?.let { branch ->
                                            val processId =
                                                gameProcess.processes
                                                    .firstOrNull { it.parentIsSteam }
                                                    ?.processId
                                                    ?: gameProcess.processes.firstOrNull()?.processId
                                                    ?: 0

                                            val userAccountId = userSteamId!!.accountID.toInt()
                                            GamePlayedInfo(
                                                gameId = gameProcess.appId.toLong(),
                                                processId = processId,
                                                ownerId =
                                                    if (pkgInfo.ownerAccountId.contains(userAccountId)) {
                                                        userAccountId
                                                    } else {
                                                        pkgInfo.ownerAccountId.first()
                                                    },
                                                // TODO: figure out what this is and un-hardcode
                                                launchSource = 100,
                                                gameBuildId = branch.buildId.toInt(),
                                                processIdList = gameProcess.processes,
                                            )
                                        }
                                    }
                                }
                            }

                        Timber.i(
                            "GameProcessInfo:%s",
                            gamesPlayed.joinToString("\n") { game ->
                                """
                        |   processId: ${game.processId}
                        |   gameId: ${game.gameId}
                        |   processes: ${
                                    game.processIdList.joinToString("\n") { process ->
                                        """
                                |   processId: ${process.processId}
                                |   processIdParent: ${process.processIdParent}
                                |   parentIsSteam: ${process.parentIsSteam}
                                        """.trimMargin()
                                    }
                                }
                                """.trimMargin()
                            },
                        )

                        // Report running games via the C++ WN-Steam-Client (Phase 9).
                        val gamesJson = JSONArray()
                        gamesPlayed.forEach { g ->
                            val procs = JSONArray()
                            g.processIdList.forEach { p ->
                                procs.put(
                                    JSONObject()
                                        .put("pid", p.processId)
                                        .put("ppid", p.processIdParent)
                                        .put("isSteam", p.parentIsSteam),
                                )
                            }
                            gamesJson.put(
                                JSONObject()
                                    .put("gameId", g.gameId)
                                    .put("processId", g.processId)
                                    .put("ownerId", g.ownerId)
                                    .put("launchSource", g.launchSource)
                                    .put("gameBuildId", g.gameBuildId)
                                    .put("processes", procs),
                            )
                        }
                        withWnSession { session ->
                            withContext(Dispatchers.IO) {
                                session.notifyGamesPlayed(
                                    gamesJson.toString(),
                                    EOSType.AndroidUnknown.code(),
                                )
                            }
                        }
                    }
                }
            }

        fun beginLaunchApp(
            appId: Int,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            ignorePendingOperations: Boolean = false,
            preferredSave: SaveLocation = SaveLocation.None,
            prefixToPath: (String) -> String,
            isOffline: Boolean = false,
            onProgress: ((message: String, progress: Float) -> Unit)? = null,
        ): Deferred<PostSyncInfo> =
            parentScope.async {
                if (isOffline || !isConnected) {
                    return@async PostSyncInfo(SyncResult.UpToDate)
                }
                if (!tryAcquireSync(appId)) {
                    Timber.w("Cannot launch app when sync already in progress for appId=$appId")
                    return@async PostSyncInfo(SyncResult.InProgress)
                }

                try {
                    val progressWrapper: (String, Float) -> Unit = { msg, prog ->
                        cloudSyncStatus.value = CloudSyncMessage(appId, false, msg, prog)
                        onProgress?.invoke(msg, prog)
                    }
                    var syncResult = PostSyncInfo(SyncResult.UnknownFail)

                    val maxAttempts = 3
                    for (attempt in 1..maxAttempts) {
                        try {
                            val clientId = PrefManager.clientId
                            val steamInstance = instance
                            val appInfo = getAppInfoOf(appId)

                            if (steamInstance != null && appInfo != null) {
                                progressWrapper("Checking Cloud Saves...", 0f)
                                val postSyncInfo =
                                    SteamAutoCloud
                                        .syncUserFiles(
                                            appInfo = appInfo,
                                            clientId = clientId,
                                            steamInstance = steamInstance,
                                            preferredSave = preferredSave,
                                            parentScope = parentScope,
                                            prefixToPath = prefixToPath,
                                            onProgress = progressWrapper,
                                        ).await()

                                postSyncInfo?.let { info ->
                                    syncResult = info

                                    if (info.syncResult == SyncResult.Success || info.syncResult == SyncResult.UpToDate) {
                                        Timber.i(
                                            "Signaling app launch:\n\tappId: %d\n\tclientId: %s\n\tosType: %s",
                                            appId,
                                            PrefManager.clientId,
                                            EOSType.AndroidUnknown,
                                        )

                                        // Signal app-launch intent via the C++
                                        // WN-Steam-Client (Phase 9). Returns the
                                        // pending-remote-operation codes (empty = clear);
                                        // null = transport/auth failure.
                                        val pendingRemoteOperations =
                                            withWnSession { session ->
                                                withContext(Dispatchers.IO) {
                                                    session.signalAppLaunchIntent(
                                                        appId = appId,
                                                        clientId = clientId,
                                                        machineName = SteamUtils.getMachineName(steamInstance),
                                                        ignorePending = ignorePendingOperations,
                                                        osType = EOSType.AndroidUnknown.code(),
                                                    )
                                                }
                                            }

                                        if (pendingRemoteOperations == null) {
                                            // Failure — do NOT treat as clear-to-launch
                                            // (this RPC is the cloud-save conflict guard).
                                            Timber.w("signalAppLaunchIntent failed for app $appId — not proceeding")
                                            syncResult = PostSyncInfo(syncResult = SyncResult.UnknownFail)
                                        } else if (pendingRemoteOperations.isNotEmpty() && !ignorePendingOperations) {
                                            syncResult =
                                                PostSyncInfo(
                                                    syncResult = SyncResult.PendingOperations,
                                                    pendingRemoteOperations = pendingRemoteOperations,
                                                )
                                        } else if (ignorePendingOperations &&
                                            // 1 == ECloudPendingRemoteOperation AppSessionActive
                                            pendingRemoteOperations.any { it == 1 }
                                        ) {
                                            // Kick the other playing session via the C++
                                            // WN-Steam-Client (Phase 9).
                                            withWnSession { session ->
                                                withContext(Dispatchers.IO) {
                                                    session.kickPlayingSession()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break
                        } catch (e: AsyncJobFailedException) {
                            if (attempt == maxAttempts) {
                                Timber.e(e, "Cloud sync failed after $maxAttempts attempts for app $appId")
                                syncResult = PostSyncInfo(SyncResult.UnknownFail)
                            } else {
                                Timber.w("Cloud sync attempt $attempt failed for app $appId, retrying...")
                                delay(1000L * attempt)
                            }
                        }
                    }

                    return@async syncResult
                } finally {
                    cloudSyncStatus.value = null
                    releaseSync(appId)
                }
            }

        /**
         * Lightweight probe: checks whether the cloud save change number for
         * [appId] differs from the locally stored value.  No files are
         * downloaded or uploaded – only a single metadata call is made.
         *
         * @return `true` if cloud differs, `false` if in sync, `null` if the
         *         check could not be performed (service unavailable, etc.).
         */
        suspend fun cloudSavesDiffer(appId: Int): Boolean? {
            val steamInstance = instance ?: return null
            val localCN = steamInstance.changeNumbersDao.getByAppId(appId)?.changeNumber ?: return null
            return try {
                // Cloud.GetAppFileChangelist via the C++ WN-Steam-Client.
                val json =
                    withWnSession { session ->
                        withContext(Dispatchers.IO) { session.getCloudFileList(appId) }
                    } ?: return null
                val currentCN = JSONObject(json).optLong("currentChangeNumber", 0L)
                currentCN != localCN
            } catch (e: Exception) {
                Timber.e(e, "Failed to probe Steam cloud change number for appId=$appId")
                null
            }
        }

        suspend fun getTrackedCloudSaveFiles(appId: Int): List<UserFileInfo>? =
            withContext(Dispatchers.IO) {
                instance?.fileChangeListsDao?.getByAppId(appId)?.userFileInfo
            }

        // The C++ getCloudFileList always returns the full snapshot, so the
        // newest remote timestamp falls straight out of the conflict snapshot.
        suspend fun getNewestRemoteCloudSaveTimestamp(appId: Int): Long? =
            fetchCloudConflictSnapshot(appId)?.newestRemoteTimestamp

        data class CloudConflictSnapshot(
            val differs: Boolean,
            val newestRemoteTimestamp: Long?,
        )

        /**
         * Public wrapper around the cloud-file-list RPC (Cloud.GetAppFileChangelist
         * via the C++ WN-Steam-Client) for callers outside the SteamService companion.
         * The native call always returns the FULL listing — [changeNumber] is accepted
         * for source compatibility but ignored. Returns null if not logged on.
         */
        suspend fun fetchCloudFileList(
            appId: Int,
            @Suppress("UNUSED_PARAMETER") changeNumber: Long = 0L,
        ): SteamAutoCloud.CloudFileChangeList? =
            withContext(Dispatchers.IO) {
                val json =
                    withWnSession { session ->
                        withContext(Dispatchers.IO) { session.getCloudFileList(appId) }
                    } ?: return@withContext null
                try {
                    SteamAutoCloud.parseCloudFileChangeList(json)
                } catch (e: Exception) {
                    Timber.e(e, "fetchCloudFileList failed for appId=%d", appId)
                    null
                }
            }

        /**
         * Single-round-trip launch-time conflict probe.
         *
         * A real conflict requires BOTH an app-change-number mismatch AND per-file
         * content (size/SHA) divergence — a CN-only check produced spurious dialogs
         * whenever the CN was stale or bumped for unrelated reasons. Fast-paths on a
         * CN match; on CN mismatch it content-checks via
         * [SteamAutoCloud.cloudContentDiffersFromLocal]. Returns the conflict flag and
         * the newest remote timestamp from the one Cloud.GetAppFileChangelist response.
         */
        @JvmOverloads
        suspend fun fetchCloudConflictSnapshot(
            appId: Int,
            context: android.content.Context? = null,
        ): CloudConflictSnapshot? =
            withContext(Dispatchers.IO) {
                val localCN = instance?.changeNumbersDao?.getByAppId(appId)?.changeNumber

                // Cloud.GetAppFileChangelist via the C++ WN-Steam-Client — the full
                // remote file list. The parser scales the proto unix-second timestamps
                // to the millis the rest of the code expects.
                val wnJson =
                    withWnSession { session ->
                        withContext(Dispatchers.IO) { session.getCloudFileList(appId) }
                    } ?: return@withContext null
                try {
                    val response = SteamAutoCloud.parseCloudFileChangeList(wnJson)
                    val cnMismatch = localCN == null || response.currentChangeNumber != localCN
                    val newest =
                        response.files
                            .filter { it.isPersisted }
                            .mapNotNull { it.timestamp.takeIf { ts -> ts > 0L } }
                            .maxOrNull()

                    // CN match → no divergence; skip the per-file hashing. Fast path.
                    if (!cnMismatch) {
                        return@withContext CloudConflictSnapshot(differs = false, newestRemoteTimestamp = newest)
                    }

                    // CN mismatch — only a real conflict if file content actually
                    // diverges. Conservative ("differs=true") if no Context is available
                    // to resolve local paths.
                    val ctx = context ?: PluviaApp.instance
                    val contentDiffers =
                        if (ctx != null) {
                            val accountId =
                                userSteamId?.accountID?.toLong()
                                    ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                                    ?: 0L
                            val prefixToPath: (String) -> String = { prefix ->
                                com.winlator.cmod.feature.stores.steam.enums.PathType
                                    .from(prefix)
                                    .toAbsPath(ctx, appId, accountId)
                            }
                            com.winlator.cmod.feature.steamcloudsync.SteamAutoCloud
                                .cloudContentDiffersFromLocal(response, prefixToPath)
                        } else {
                            true
                        }
                    Timber.i(
                        "cloud conflict snapshot via wn-steam-client: app=$appId " +
                            "cnMismatch=$cnMismatch contentDiffers=$contentDiffers files=${response.files.size}",
                    )
                    CloudConflictSnapshot(differs = contentDiffers, newestRemoteTimestamp = newest)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse Steam cloud conflict snapshot for appId=$appId")
                    null
                }
            }

        suspend fun forceSyncUserFiles(
            appId: Int,
            prefixToPath: (String) -> String,
            preferredSave: SaveLocation = SaveLocation.None,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            overrideLocalChangeNumber: Long? = null,
        ): Deferred<PostSyncInfo> =
            parentScope.async {
                if (!tryAcquireSync(appId)) {
                    Timber.w("Cannot force sync when sync already in progress for appId=$appId")
                    return@async PostSyncInfo(SyncResult.InProgress)
                }

                try {
                    var syncResult = PostSyncInfo(SyncResult.UnknownFail)

                    val maxAttempts = 3
                    for (attempt in 1..maxAttempts) {
                        try {
                            val clientId = PrefManager.clientId
                            val steamInstance = instance
                            val appInfo = getAppInfoOf(appId)

                            if (steamInstance != null && appInfo != null) {
                                val postSyncInfo =
                                    SteamAutoCloud
                                        .syncUserFiles(
                                            appInfo = appInfo,
                                            clientId = clientId,
                                            steamInstance = steamInstance,
                                            preferredSave = preferredSave,
                                            parentScope = parentScope,
                                            prefixToPath = prefixToPath,
                                            overrideLocalChangeNumber = overrideLocalChangeNumber,
                                        ).await()

                                postSyncInfo?.let { info ->
                                    syncResult = info
                                    Timber.i("Force cloud sync completed for app $appId with result: ${info.syncResult}")
                                }
                            }
                            break
                        } catch (e: AsyncJobFailedException) {
                            if (attempt == maxAttempts) {
                                Timber.e(e, "Force cloud sync failed after $maxAttempts attempts for app $appId")
                            } else {
                                Timber.w("Force cloud sync attempt $attempt failed for app $appId, retrying...")
                                delay(1000L * attempt)
                            }
                        }
                    }

                    return@async syncResult
                } finally {
                    releaseSync(appId)
                }
            }

        suspend fun generateAchievements(
            appId: Int,
            configDirectory: String,
        ) = runCatching {
            // Primary path: the C++ WN-Steam-Client (Phase 9 — JavaSteam is
            // being dropped). CMsgClientGetUserStats returns the binary-VDF
            // UserGameStatsSchema — exactly what StatsAchievementsGenerator
            // consumes to emit Goldberg's achievements.json + stats.json.
            val schemaArray: ByteArray = run {
                val wn = withWnSession { session ->
                    withContext(Dispatchers.IO) { session.getUserStatsSchema(appId) }
                }
                if (wn != null && wn.isNotEmpty()) {
                    Timber.i("user-stats schema via wn-steam-client: ${wn.size} bytes (app $appId)")
                    wn
                } else {
                    Timber.w("wn-steam-client user-stats schema unavailable for app $appId")
                    return@runCatching
                }
            }
            val generator = StatsAchievementsGenerator()
            val result = generator.generateStatsAchievements(schemaArray, configDirectory)
            cachedAchievements = result.achievements
            cachedAchievementsAppId = appId

            val nameToBlockBit = result.nameToBlockBit
            if (nameToBlockBit.isNotEmpty()) {
                val mappingJson = JSONObject()
                nameToBlockBit.forEach { (name, pair) ->
                    mappingJson.put(name, JSONArray(listOf(pair.first, pair.second)))
                }
                File(configDirectory, "achievement_name_to_block.json").writeText(mappingJson.toString(), Charsets.UTF_8)
            }
        }.onFailure { e ->
            Timber.w(e, "Failed to generate achievements for appId=$appId")
        }

        /**
         * Fetch the app's Steam Inventory item definitions and write
         * gbe_fork's `steam_settings/items.json` + `default_items.json` into
         * [configDirectory]. Best-effort — silently no-ops when the app has no
         * inventory, the user isn't logged on, or the fetch fails. Mirrors
         * [generateAchievements].
         */
        suspend fun generateInventoryItems(
            appId: Int,
            configDirectory: String,
        ) = runCatching {
            // The GetItemDefArchive HTTPS GET needs the same PEM trust bundle
            // CaBundleExtractor provides for the CM session.
            val ctx = instance?.applicationContext ?: return@runCatching
            val caPath = CaBundleExtractor.ensureBundle(ctx)
            val archive =
                withWnSession { session ->
                    withContext(Dispatchers.IO) { session.getItemDefArchive(appId, caPath) }
                }
            if (archive == null) {
                Timber.i("Inventory item-def archive unavailable for app $appId")
                return@runCatching
            }
            val count = InventoryItemsGenerator.generate(archive, configDirectory)
            Timber.i("Inventory items generated for app $appId: $count definition(s)")
        }.onFailure { e ->
            Timber.w(e, "Failed to generate inventory items for appId=$appId")
        }

        /**
         * Fetch the signed-in account's subscribed Steam Workshop items for
         * [appId] as a JSON array string (see
         * [WnSteamSession.getSubscribedWorkshopItems]). Brings up a CM session
         * if needed. null when not logged on / on transport failure; "[]" when
         * the account has no subscriptions for the app.
         */
        suspend fun getSubscribedWorkshopItems(appId: Int): String? =
            withWnSession { session ->
                withContext(Dispatchers.IO) { session.getSubscribedWorkshopItems(appId) }
            }

        // Published-file-ids with an install in flight — guards against two
        // concurrent installs of the same item wiping each other's content dir.
        private val workshopInstallsInFlight =
            java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

        /**
         * Download and stage one subscribed Steam Workshop item for [appId].
         * The content is fetched via the depot pipeline into the persistent
         * staging area ([WorkshopModsGenerator]); the preview image is fetched
         * over HTTPS; the meta marker is written LAST so a partial download is
         * never mistaken for an installed item. Returns true on success.
         * BLOCKING — runs on Dispatchers.IO.
         */
        suspend fun installWorkshopItem(
            appId: Int,
            publishedFileId: Long,
            manifestId: Long,
            title: String,
            fileSizeBytes: Long,
            timeUpdated: Long,
            previewUrl: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                if (manifestId == 0L) {
                    Timber.w("Workshop item $publishedFileId has no content manifest — cannot install")
                    return@withContext false
                }
                if (!workshopInstallsInFlight.add(publishedFileId)) {
                    Timber.w("Workshop item $publishedFileId — install already in progress")
                    return@withContext false
                }
                try {
                    val ctx = instance?.applicationContext ?: return@withContext false
                    val caPath = CaBundleExtractor.ensureBundle(ctx)
                    val content = WorkshopModsGenerator.contentDir(ctx, appId, publishedFileId)
                    val meta = WorkshopModsGenerator.metaFile(ctx, appId, publishedFileId)
                    val preview = WorkshopModsGenerator.previewFile(ctx, appId, publishedFileId)
                    // A (re)install starts clean: drop any stale marker / content / preview.
                    meta.delete()
                    preview.delete()
                    content.deleteRecursively()
                    content.mkdirs()

                    val bytes =
                        withWnSession { session ->
                            session.downloadWorkshopItem(appId, manifestId, content.absolutePath, caPath)
                        } ?: -1L
                    if (bytes < 0L) {
                        Timber.w("Workshop content download failed for item $publishedFileId (app $appId)")
                        content.deleteRecursively()
                        return@withContext false
                    }
                    // The depot downloader leaves a .DepotDownloader resume folder in
                    // the install dir — drop it so it isn't exposed as mod content.
                    File(content, ".DepotDownloader").deleteRecursively()

                    // Preview image — best-effort; a missing preview must not fail the install.
                    if (previewUrl.isNotBlank()) {
                        runCatching { downloadWorkshopPreview(previewUrl, preview) }
                            .onFailure { Timber.d(it, "Workshop preview download skipped for $publishedFileId") }
                    }

                    // Meta marker written LAST — its presence means "fully installed".
                    meta.writeText(
                        org.json.JSONObject()
                            .put("title", title)
                            .put("fileSize", fileSizeBytes)
                            .put("timeUpdated", timeUpdated)
                            .put("manifestId", manifestId)
                            .toString(),
                        Charsets.UTF_8,
                    )
                    Timber.i("Workshop item $publishedFileId installed for app $appId ($bytes bytes)")
                    true
                } finally {
                    workshopInstallsInFlight.remove(publishedFileId)
                }
            }

        private fun downloadWorkshopPreview(url: String, dest: File) {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) return
                val type = conn.contentType.orEmpty()
                if (type.isNotEmpty() && !type.startsWith("image/")) return
                dest.parentFile?.mkdirs()
                val maxBytes = 16L * 1024 * 1024  // cap — a preview image is never this large
                var over = false
                var total = 0L
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) { over = true; break }
                            out.write(buf, 0, n)
                        }
                    }
                }
                // Discard an over-cap (truncated) or empty download — never
                // let mods.json reference a corrupt preview.
                if (over || total == 0L) dest.delete()
            } finally {
                conn.disconnect()
            }
        }

        fun getGseSaveDirs(appId: Int): List<File> {
            val context = instance?.applicationContext ?: return emptyList()
            val imageFs = ImageFs.find(context)
            val dirs = mutableListOf<File>()
            dirs.add(
                File(
                    imageFs.rootDir,
                    "${ImageFs.WINEPREFIX}/drive_c/users/xuser/AppData/Roaming/GSE Saves/$appId",
                ),
            )
            val accountId =
                userSteamId?.accountID?.toInt()
                    ?: PrefManager.steamUserAccountId.takeIf { it != 0 }
            if (accountId != null) {
                dirs.add(
                    File(
                        imageFs.rootDir,
                        "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/userdata/$accountId/$appId",
                    ),
                )
            }
            return dirs
        }

        suspend fun syncAchievementsFromGoldberg(appId: Int) {
            val context = instance?.applicationContext ?: return
            val gseSaveDirs = getGseSaveDirs(appId).filter { it.isDirectory }
            if (gseSaveDirs.isEmpty()) {
                Timber.d("No GSE save directory found for appId=$appId")
                return
            }

            val unlockedNames = mutableSetOf<String>()
            var gseStatsDir: File? = null

            for (gseSaveDir in gseSaveDirs) {
                val goldbergAchFile = File(gseSaveDir, "achievements.json")
                if (goldbergAchFile.exists()) {
                    try {
                        val json = JSONObject(goldbergAchFile.readText(Charsets.UTF_8))
                        for (name in json.keys()) {
                            val entry = json.optJSONObject(name) ?: continue
                            if (entry.optBoolean("earned", false)) {
                                unlockedNames.add(name)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse Goldberg achievements.json in ${gseSaveDir.absolutePath} for appId=$appId")
                    }
                }

                val statsDir = File(gseSaveDir, "stats")
                if (gseStatsDir == null && statsDir.isDirectory && (statsDir.listFiles()?.isNotEmpty() == true)) {
                    gseStatsDir = statsDir
                }
            }

            val hasStats = gseStatsDir != null

            if (unlockedNames.isEmpty() && !hasStats) {
                Timber.d("No earned achievements or stats found in Goldberg output for appId=$appId")
                return
            }

            val configDirectory = findSteamSettingsDir(context, appId)
            if (configDirectory == null) {
                Timber.w("Could not find steam_settings directory for appId=$appId")
                return
            }

            val result = storeAchievementUnlocks(appId, configDirectory, unlockedNames, gseStatsDir ?: gseSaveDirs.first().resolve("stats"))
            result.onFailure { e ->
                Timber.e(e, "Failed to sync achievements and stats to Steam for appId=$appId")
            }
        }

        private fun findSteamSettingsDir(
            context: Context,
            appId: Int,
        ): String? {
            val appDirPath = getAppDirPath(appId)
            val appDirSettings = File(appDirPath, "steam_settings")
            if (appDirSettings.isDirectory) {
                return appDirSettings.absolutePath
            }

            val container = ContainerUtils.getContainer(context, "STEAM_$appId") ?: return null
            val coldclientSettings =
                File(
                    container.rootDir,
                    ".wine/drive_c/Program Files (x86)/Steam/steam_settings",
                )
            if (coldclientSettings.isDirectory) {
                return coldclientSettings.absolutePath
            }

            return null
        }

        suspend fun storeAchievementUnlocks(
            appId: Int,
            configDirectory: String,
            unlockedNames: Set<String>,
            gseStatsDir: File,
        ): Result<Unit> =
            runCatching {
                val mySteamId = userSteamId
                    ?: throw IllegalStateException("storeAchievementUnlocks: no SteamID")

                // Fetch the app's user-stats (schema + crc + achievement
                // blocks) via the C++ WN-Steam-Client (Phase 9).
                val statsJson = withWnSession { session -> session.getUserStatsFull(appId) }
                    ?: throw IllegalStateException("getUserStats failed: no response")
                val statsObj = JSONObject(statsJson)
                val eresult = statsObj.optInt("eresult", 2)
                if (eresult != EResult.OK.code()) {
                    throw IllegalStateException("getUserStats failed: eresult=$eresult")
                }
                val crcStats = statsObj.optInt("crcStats")
                val schemaBytes = hexToBytes(statsObj.optString("schema"))
                val achievementBlocks = statsObj.optJSONArray("achievementBlocks")

                val allStats = mutableMapOf<Int, Int>()

                val mappingFile = File(configDirectory, "achievement_name_to_block.json")
                if (!mappingFile.exists() && unlockedNames.isNotEmpty()) {
                    generateAchievements(appId, configDirectory)
                }

                if (mappingFile.exists() && unlockedNames.isNotEmpty()) {
                    val mappingJson = JSONObject(mappingFile.readText(Charsets.UTF_8))
                    val nameToBlockBit = mutableMapOf<String, Pair<Int, Int>>()
                    for (key in mappingJson.keys()) {
                        val arr = mappingJson.optJSONArray(key) ?: continue
                        if (arr.length() >= 2) {
                            nameToBlockBit[key] = Pair(arr.getInt(0), arr.getInt(1))
                        }
                    }

                    for (i in 0 until (achievementBlocks?.length() ?: 0)) {
                        val block = achievementBlocks!!.getJSONObject(i)
                        val blockId = block.optInt("achievementId")
                        val unlockTimes = block.optJSONArray("unlockTimes")
                        var bitmask = 0
                        for (j in 0 until (unlockTimes?.length() ?: 0)) {
                            // unlock_time is a uint32 (can exceed Int range) —
                            // read as Long; non-zero means the bit is unlocked.
                            if (unlockTimes!!.getLong(j) != 0L) bitmask = bitmask or (1 shl j)
                        }
                        allStats[blockId] = bitmask
                    }

                    for (name in unlockedNames) {
                        val mapped = nameToBlockBit[name] ?: continue
                        val current = allStats.getOrDefault(mapped.first, 0)
                        allStats[mapped.first] = current or (1 shl mapped.second)
                    }
                }

                if (gseStatsDir.isDirectory) {
                    val statNameToId = mutableMapOf<String, Int>()
                    try {
                        val parsedSchema = VdfParser().binaryLoads(schemaBytes)
                        for ((_, appData) in parsedSchema) {
                            if (appData !is Map<*, *>) continue
                            val statInfo = (appData as Map<String, Any>)["stats"] as? Map<String, Any> ?: continue
                            for ((statKey, statData) in statInfo) {
                                if (statData !is Map<*, *>) continue
                                val stat = statData as Map<String, Any>
                                val statType = stat["type"]?.toString() ?: continue
                                if (statType == StatType.STAT_TYPE_BITS || statType == StatType.ACHIEVEMENTS) continue
                                val name = stat["name"]?.toString()?.lowercase() ?: continue
                                val id = statKey.toIntOrNull() ?: continue
                                statNameToId[name] = id
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse schema for stat name mapping, appId=$appId")
                    }

                    if (statNameToId.isNotEmpty()) {
                        for (statFile in gseStatsDir.listFiles() ?: emptyArray()) {
                            if (!statFile.isFile) continue
                            val statId = statNameToId[statFile.name.lowercase()] ?: continue
                            val bytes = statFile.readBytes()
                            if (bytes.size >= 4) {
                                val value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
                                allStats[statId] = value
                                Timber.d("Read GSE stat: ${statFile.name} -> statId=$statId, value=$value")
                            }
                        }
                    }
                }

                if (allStats.isEmpty()) {
                    Timber.d("No stats or achievements to store for appId=$appId")
                    return@runCatching
                }

                Timber.d("storeUserStats: appId=$appId, crcStats=$crcStats, stats=$allStats")
                sendStoreUserStats(appId, allStats, mySteamId.convertToUInt64(), crcStats)
            }

        /**
         * Decode a hex string (as produced by the C++ JNI layer) to bytes.
         * Returns an empty array for a too-short / empty input.
         */
        private fun hexToBytes(hex: String): ByteArray {
            if (hex.length < 2) return ByteArray(0)
            val n = hex.length / 2
            val out = ByteArray(n)
            for (i in 0 until n) {
                out[i] = ((Character.digit(hex[i * 2], 16) shl 4) or
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
            return out
        }

        /**
         * Write achievement / stat values back to Steam via the C++
         * WN-Steam-Client (CMsgClientStoreUserStats2). Fire-and-forget.
         */
        private suspend fun sendStoreUserStats(
            appId: Int,
            stats: Map<Int, Int>,
            steamId: Long,
            crcStats: Int,
        ) {
            if (stats.isEmpty()) return
            val statIds = IntArray(stats.size)
            val statValues = IntArray(stats.size)
            var i = 0
            for ((id, value) in stats) {
                statIds[i] = id
                statValues[i] = value
                i++
            }
            val sent = withWnSession { session ->
                session.storeUserStats(appId, steamId, crcStats, statIds, statValues)
                true
            }
            if (sent != true) {
                Timber.e("Failed to send storeUserStats for appId=$appId — no session")
            }
        }

        data class CloudSyncOutcome(
            val success: Boolean,
            val message: String = "",
        )

        suspend fun closeApp(
            appId: Int,
            isOffline: Boolean,
            prefixToPath: (String) -> String,
            onProgress: ((message: String, progress: Float) -> Unit)? = null,
        ): CloudSyncOutcome =
            withContext(Dispatchers.IO) {
                async {
                    if (isOffline || !isConnected) {
                        return@async CloudSyncOutcome(false, "Steam is offline.")
                    }

                    if (!tryAcquireSync(appId)) {
                        Timber.w("Cannot close app when sync already in progress for appId=$appId")
                        return@async CloudSyncOutcome(false, "Steam cloud sync is already in progress.")
                    }

                    try {
                        try {
                            syncAchievementsFromGoldberg(appId)
                        } catch (e: Exception) {
                            Timber.e(e, "Achievement sync failed for appId=$appId, continuing with cloud save sync")
                        }

                        val progressWrapper: (String, Float) -> Unit = { msg, prog ->
                            cloudSyncStatus.value = CloudSyncMessage(appId, true, msg, prog)
                            onProgress?.invoke(msg, prog)
                        }
                        val maxAttempts = 3
                        var lastErrorMessage = "Steam cloud save sync failed."
                        for (attempt in 1..maxAttempts) {
                            try {
                                val clientId = PrefManager.clientId
                                val steamInstance = instance
                                val appInfo = getAppInfoOf(appId)

                                if (steamInstance != null && appInfo != null) {
                                    progressWrapper("Checking Local Saves...", 0f)
                                    val postSyncInfo =
                                        SteamAutoCloud
                                            .syncUserFiles(
                                                appInfo = appInfo,
                                                clientId = clientId,
                                                steamInstance = steamInstance,
                                                parentScope = this@async,
                                                prefixToPath = prefixToPath,
                                                onProgress = progressWrapper,
                                            ).await()

                                    val syncResult = postSyncInfo?.syncResult ?: SyncResult.UnknownFail
                                    // Signal exit-sync-done via the C++ WN-Steam-Client (Phase 9).
                                    withWnSession { session ->
                                        withContext(Dispatchers.IO) {
                                            session.signalAppExitSyncDone(
                                                appId = appId,
                                                clientId = clientId,
                                                uploadsCompleted = postSyncInfo?.uploadsCompleted == true,
                                                uploadsRequired = postSyncInfo?.uploadsRequired == true,
                                            )
                                        }
                                    }

                                    if (syncResult == SyncResult.Success || syncResult == SyncResult.UpToDate) {
                                        return@async CloudSyncOutcome(true)
                                    }

                                    // Discriminate the failure message by SyncResult so callers
                                    // (SteamExitCloudSync.isRetryable, the UI retry loop) can
                                    // distinguish terminal failures (Conflict — needs user dialog)
                                    // from transient ones (UpdateFail/DownloadFail — worth retrying).
                                    lastErrorMessage =
                                        when (syncResult) {
                                            SyncResult.Conflict ->
                                                "Steam cloud save sync conflict — relaunch the game to resolve."
                                            SyncResult.PendingOperations ->
                                                "Steam cloud sync pending — another device may still be uploading."
                                            SyncResult.InProgress ->
                                                "Steam cloud sync already in progress."
                                            SyncResult.UpdateFail ->
                                                "Steam cloud save upload failed."
                                            SyncResult.DownloadFail ->
                                                "Steam cloud save download failed."
                                            else -> "Steam cloud save sync failed."
                                        }
                                } else {
                                    lastErrorMessage = "Steam cloud service is unavailable."
                                }
                            } catch (e: AsyncJobFailedException) {
                                // e.message often comes from SteamKit's EResult enum names
                                // (e.g. "Pending", "RemoteFileConflict"). The SteamExitCloudSync
                                // retry classifier matches substrings like "conflict" and "pending"
                                // — so SteamKit failures with those names will correctly short-circuit
                                // the retry loop without needing a SyncResult plumb-through here.
                                lastErrorMessage = e.message ?: "Steam cloud save sync failed."
                                if (attempt == maxAttempts) {
                                    Timber.e(e, "Close app sync failed after $maxAttempts attempts for app $appId")
                                } else {
                                    Timber.w("Close app sync attempt $attempt failed for app $appId, retrying...")
                                    delay(1000L * attempt)
                                }
                            }
                        }
                        return@async CloudSyncOutcome(false, lastErrorMessage)
                    } finally {
                        cloudSyncStatus.value = null
                        releaseSync(appId)
                    }
                }.await()
            }

        interface CloudSyncCallback {
            fun onProgress(
                message: String,
                progress: Float,
            )

            fun onComplete(
                success: Boolean,
                message: String,
            )
        }

        @JvmStatic
        fun beginLaunchAppBlocking(
            context: android.content.Context,
            appId: Int,
            ignorePendingOperations: Boolean = false,
            preferredSave: SaveLocation = SaveLocation.None,
            isOffline: Boolean = false,
            callback: CloudSyncCallback? = null,
        ): PostSyncInfo =
            runBlocking(Dispatchers.IO) {
                check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                    "beginLaunchAppBlocking must not be called on the main thread"
                }
                var completionSent = false
                val accountId =
                    userSteamId?.accountID?.toLong()
                        ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                        ?: 0L
                val prefixToPath: (String) -> String = { prefix ->
                    com.winlator.cmod.feature.stores.steam.enums.PathType
                        .from(prefix)
                        .toAbsPath(context, appId, accountId)
                }

                try {
                    beginLaunchApp(
                        appId = appId,
                        parentScope = this,
                        ignorePendingOperations = ignorePendingOperations,
                        preferredSave = preferredSave,
                        prefixToPath = prefixToPath,
                        isOffline = isOffline,
                        onProgress = { msg, prog -> callback?.onProgress(msg, prog) },
                    ).await()
                } catch (e: Exception) {
                    completionSent = true
                    callback?.onComplete(false, e.message ?: "Steam cloud sync failed.")
                    throw e
                } finally {
                    if (!completionSent) {
                        callback?.onComplete(true, "")
                    }
                }
            }

        /**
         * Sync cloud saves for backup/restore purposes without closing the app.
         * @param preferredAction "download" or "upload"
         * @return true if sync succeeded
         */
        suspend fun syncCloudSavesForBackup(
            context: android.content.Context,
            appId: Int,
            preferredAction: String,
        ): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val accountId =
                        userSteamId?.accountID?.toLong()
                            ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                            ?: 0L
                    val prefixToPath: (String) -> String = { prefix ->
                        com.winlator.cmod.feature.stores.steam.enums.PathType
                            .from(prefix)
                            .toAbsPath(context, appId, accountId)
                    }
                    val steamInst = instance
                    val appInfo = getAppInfoOf(appId)
                    val clientId = PrefManager.clientId

                    if (steamInst == null || appInfo == null) {
                        return@withContext false
                    }

                    SteamAutoCloud
                        .syncUserFiles(
                            appInfo = appInfo,
                            clientId = clientId,
                            steamInstance = steamInst,
                            prefixToPath = prefixToPath,
                            onProgress = { _, _ -> },
                        ).await()
                    true
                } catch (e: Exception) {
                    timber.log.Timber
                        .tag("SteamService")
                        .e(e, "syncCloudSavesForBackup failed")
                    false
                }
            }
        }

        @JvmStatic
        fun syncCloudOnExit(
            context: android.content.Context,
            appId: Int,
            callback: CloudSyncCallback,
        ) {
            val accountId =
                userSteamId?.accountID?.toLong()
                    ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                    ?: 0L
            val prefixToPath: (String) -> String = { prefix ->
                com.winlator.cmod.feature.stores.steam.enums.PathType
                    .from(prefix)
                    .toAbsPath(context, appId, accountId)
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val outcome =
                        closeApp(
                            appId = appId,
                            isOffline = false,
                            prefixToPath = prefixToPath,
                            onProgress = { msg, prog -> callback.onProgress(msg, prog) },
                        )
                    notifyRunningProcesses()
                    withContext(Dispatchers.Main) {
                        callback.onComplete(outcome.success, outcome.message)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        callback.onComplete(false, e.message ?: "Steam cloud save sync failed.")
                    }
                }
            }
        }

        /**
         * loginusers.vdf writer for the OAuth-style refresh-token flow introduced in 2024.
         *
         * @param steamId64    64-bit SteamID of the logged-in user
         * @param account      AccountName (same as you passed to logOn / poll result)
         * @param refreshToken Long-lived token you get from AuthSession / QR / credentials
         * @param accessToken  Optional – short-lived access token, Steam ignores it if absent
         * @param personaName  What the client shows in the drop-down; defaults to AccountName
         */
        internal fun getLoginUsersVdfOauth(
            steamId64: String,
            account: String,
            refreshToken: String,
            accessToken: String? = null,
            personaName: String = account,
        ): String {
            val epoch = System.currentTimeMillis() / 1_000

            val vdf =
                buildString {
                    appendLine("\"users\"")
                    appendLine("{")
                    appendLine("    \"$steamId64\"")
                    appendLine("    {")
                    appendLine("        \"AccountName\"          \"$account\"")
                    appendLine("        \"PersonaName\"          \"$personaName\"")
                    appendLine("        \"RememberPassword\"     \"1\"")
                    appendLine("        \"WantsOfflineMode\"     \"0\"")
                    appendLine("        \"SkipOfflineModeWarning\"     \"0\"")
                    appendLine("        \"AllowAutoLogin\"       \"1\"")
                    appendLine("        \"MostRecent\"           \"1\"")
                    appendLine("        \"Timestamp\"            \"$epoch\"")
                    appendLine("    }")
                    appendLine("}")
                }

            return vdf
        }

        /**
         * Persists the credentials acquired by a successful WN-Steam-Client
         * auth so a later cold start can auto-logon. Phase 9 replacement for
         * the old JavaSteam `login()` (which also drove `steamUser.logOn`).
         */
        private fun persistLoginTokens(
            username: String,
            accessToken: String?,
            refreshToken: String?,
            clientId: Long? = null,
        ) {
            isLoggingOut = false
            PrefManager.username = username
            if (accessToken != null) PrefManager.accessToken = accessToken
            if (refreshToken != null) PrefManager.refreshToken = refreshToken
            if (clientId != null) PrefManager.clientId = clientId
        }

        suspend fun startLoginWithCredentials(
            username: String,
            password: String,
            rememberSession: Boolean,
            authenticator: WnAuthenticator,
        ) = withContext(Dispatchers.IO) {
            val svc = instance ?: run {
                PluviaApp.events.emit(
                    SteamEvent.LogonEnded(username, LoginResult.Failed,
                        "SteamService not initialized"),
                )
                return@withContext
            }

            Timber.i("Logging in via credentials (wn-steam-client).")
            svc._loginResult = LoginResult.InProgress
            PluviaApp.events.emit(SteamEvent.LogonStarted(username))

            teardownPriorWnSession()

            val session = bringUpWnSession(svc) ?: run {
                PluviaApp.events.emit(
                    SteamEvent.LogonEnded(username, LoginResult.Failed,
                        "Failed to connect to Steam CM"),
                )
                return@withContext
            }
            wnAuthSession = session
            var keepSessionAlive = false
            try {
                val result = suspendCancellableCoroutine<WnAuthResult> { cont ->
                    session.startLoginWithCredentials(
                        username = username.trim(),
                        password = password.trim(),
                        persistentSession = rememberSession,
                        authenticator = authenticator,
                        callback = WnAuthCallback { r ->
                            if (cont.isActive) cont.resume(r)
                        },
                    )
                    cont.invokeOnCancellation { session.cancelLogin() }
                }

                if (!result.success || result.refreshToken.isEmpty()) {
                    Timber.e("WnSteam auth failed: %s", result.errorMessage)
                    PluviaApp.events.emit(
                        SteamEvent.LogonEnded(username, LoginResult.Failed,
                            if (result.errorMessage.isNotEmpty()) result.errorMessage
                            else "auth failed (eresult=${result.errorCode})"),
                    )
                    return@withContext
                }

                Timber.i("WnSteam auth OK for %s", result.accountName)

                // Persist the acquired tokens so a later cold start auto-logons.
                persistLoginTokens(
                    username = result.accountName,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                )

                // Phase 9 — promote the auth session to the long-lived logon
                // session: install the orchestrator observer, then drive the
                // C++ CMsgClientLogon. The observer fires onWnLoggedOn once the
                // session reaches LoggedOn (state 3) — that emits LogonEnded
                // and kicks off the post-logon work (persona, licenses, PICS).
                //
                // DO NOT INSERT A SUSPENSION POINT (withContext/delay/
                // suspendCancellable...) between the next four lines.
                // Cancellation mid-promotion would leave `wnSession` set
                // while `keepSessionAlive` is still false → the finally
                // block would close the session pointed-to by wnSession
                // (use-after-free risk identical to the prior nativeDestroy crash).
                installWnLogonObserver(session)
                wnSession = session
                wnAuthSession = null
                keepSessionAlive = true

                if (!session.logonWithRefreshToken(result.refreshToken, result.accountName, result.steamId)) {
                    Timber.w("WnSteam logon_with_refresh_token returned false (channel not Connected?)")
                }

                // Watchdog: if the CM logon never reaches LoggedOn, surface a
                // failure so the login UI doesn't hang on the spinner.
                svc.scope.launch {
                    var waited = 0
                    while (waited < 35 && session.state() != 3) { delay(1000); waited++ }
                    if (session.state() != 3 && wnSession === session) {
                        Timber.w("WnSteam CM logon never reached LoggedOn")
                        PluviaApp.events.emit(
                            SteamEvent.LogonEnded(result.accountName, LoginResult.Failed,
                                "Steam logon timed out"),
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")
                val message = when (e) {
                    is CancellationException -> "Unknown cancellation"
                    else -> e.message ?: e.javaClass.name
                }
                PluviaApp.events.emit(SteamEvent.LogonEnded(username, LoginResult.Failed, message))
            } finally {
                if (!keepSessionAlive) {
                    try { session.disconnect() } catch (_: Throwable) {}
                    try { session.close() } catch (_: Throwable) {}
                    if (wnAuthSession === session) wnAuthSession = null
                }
            }
        }

        /**
         * Orchestrator observer wired onto the long-lived [WnSteamSession]
         * (Phase 9). Drives the whole connection lifecycle off the C++
         * WN-Steam-Client's channel state — there is no JavaSteam client:
         *  - state 2 (Connected): mark [isConnectedFlow] connected.
         *  - state 3 (LoggedOn):  mark connected + logged-in, then run the
         *    post-logon orchestration ([onWnLoggedOn]) exactly once.
         *  - state 0 (Disconnected): clear the flows and, if this is still
         *    the shared session, hand off to [onWnDisconnected] (reconnect
         *    or stop).
         */
        private fun installWnLogonObserver(session: WnSteamSession) {
            // A fresh session begins a fresh logon — let onWnLoggedOn re-run.
            wnLoggedOnHandled = false
            session.setStateObserver(object : WnSteamStateObserver {
                override fun onStateChanged(state: Int) {
                    val name = when (state) {
                        0 -> "Disconnected"; 1 -> "Connecting"
                        2 -> "Connected";    3 -> "LoggedOn"
                        else -> "?($state)"
                    }
                    Timber.i("WnSteam(logon) state -> %s", name)
                    when (state) {
                        2 -> {
                            isConnected = true
                        }
                        3 -> {
                            isConnected = true
                            _isLoggedInFlow.value = true
                            if (!wnLoggedOnHandled) {
                                wnLoggedOnHandled = true
                                instance?.onWnLoggedOn(session)
                            }
                        }
                        0 -> {
                            if (wnSession === session) {
                                isConnected = false
                                _isLoggedInFlow.value = false
                                wnSession = null
                                wnLoggedOnHandled = false
                                instance?.onWnDisconnected()
                            }
                        }
                    }
                }
                override fun onClientMessage(emsg: Int, eresult: Int, body: ByteArray) {
                    Timber.d("WnSteam(logon) inbound emsg=%d eresult=%d body=%d bytes",
                        emsg, eresult, body.size)
                }
            })
            // Wire the Kotlin library facade now so it's ready to receive the
            // populate-complete observer fire that lands a couple of seconds
            // after the ClientLicenseList push.
            wnLibrary?.stopObserving()
            val library = WnLibraryStore(session)
            wnLibrary = library
            library.startObserving()
            // Log every snapshot transition so we can see in logcat when the
            // C++ populate pipeline completes. The flow is hot + replay=1 so
            // late collectors get the latest snapshot.
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                library.snapshots.collect { snap ->
                    Timber.i(
                        "WnLibrary snapshot: %d packages, %d owned apps (of %d tracked)",
                        snap.packages.size, snap.ownedApps.size, snap.allAppsCount,
                    )
                }
            }
        }

        /**
         * Creates a fresh [WnSteamSession], extracts the CA bundle, picks
         * a CM URL, connects, and waits for the encrypted channel to
         * reach Connected state (=2). Returns the live session on
         * success — caller takes ownership and is responsible for
         * disconnect/close. Returns null on any failure (logs reason).
         */
        /**
         * Run [block] with a logged-on WN-Steam-Client session. Reuses the
         * global [wnSession] when it is already logged on (state 3); else
         * brings up a temporary session, logs it on with the stored refresh
         * token, runs [block], and tears that temporary session down after.
         * Returns null if no logged-on session could be obtained.
         */
        internal suspend fun <T> withWnSession(
            block: suspend (WnSteamSession) -> T,
        ): T? {
            // Fast path: an already-logged-on session is safe to share
            // concurrently — no lock needed.
            wnSession?.takeIf { it.state() == 3 }?.let { return block(it) }
            // Bring-up is serialized: concurrent callers must NOT each spin
            // up their own CM session (Steam kicks all but one with
            // ClientLoggedOff eresult=34). Callers queue here and re-check
            // the shared session once inside the lock.
            return wnSessionBringUpMutex.withLock {
                wnSession?.takeIf { it.state() == 3 }?.let { return@withLock block(it) }
                val svc = instance ?: return@withLock null
                val refreshTok = PrefManager.refreshToken
                if (refreshTok.isBlank()) {
                    Timber.w("withWnSession: no stored refresh token")
                    return@withLock null
                }
                val brought = bringUpWnSession(svc) ?: run {
                    Timber.w("withWnSession: could not connect a session")
                    return@withLock null
                }
                var promoted = false
                try {
                    if (!brought.logonWithRefreshToken(
                            refreshTok,
                            PrefManager.username,
                            PrefManager.steamUserSteamId64,
                        )
                    ) {
                        Timber.w("withWnSession: logon request failed")
                        return@withLock null
                    }
                    var wait = 0
                    while (brought.state() != 3 && wait < 30) {
                        delay(1000L)
                        wait++
                    }
                    if (brought.state() != 3) {
                        Timber.w("withWnSession: session never reached LoggedOn")
                        return@withLock null
                    }
                    // Promote to the long-lived shared session. A throwaway
                    // session per call cannot hold Steam presence — the
                    // moment it disconnects, Steam drops the persona state /
                    // games-played it just published. Keeping one logged-on
                    // session alive fixes that AND lets every later caller
                    // take the lock-free fast path above.
                    installWnLogonObserver(brought)
                    wnSession = brought
                    promoted = true
                    isConnected = true
                    _isLoggedInFlow.value = true
                    // The state→LoggedOn transition happened above, before the
                    // observer was installed, so run the post-logon work here.
                    if (!wnLoggedOnHandled) {
                        wnLoggedOnHandled = true
                        instance?.onWnLoggedOn(brought)
                    }
                    block(brought)
                } finally {
                    // Only tear down a session we did NOT promote (logon
                    // failed / never reached LoggedOn) — the promoted one
                    // is now the shared session and must stay alive.
                    if (!promoted) {
                        runCatching { brought.disconnect() }
                        runCatching { brought.close() }
                    }
                }
            }
        }

        private suspend fun bringUpWnSession(svc: SteamService): WnSteamSession? {
            val caPath = CaBundleExtractor.ensureBundle(svc)
            if (caPath.isEmpty()) {
                Timber.e("Cannot start WnSteam session: CA bundle unavailable")
                return null
            }
            val cmUrl = withContext(Dispatchers.IO) {
                WnSteamSession.pickCmUrl(caPath)
            }
            if (cmUrl.isEmpty()) {
                Timber.e("Cannot start WnSteam session: no CM URL")
                return null
            }
            Timber.i("WnSteam: connecting to %s", cmUrl)

            val session = WnSteamSession()
            var ok = false
            try {
                session.setCaBundlePath(caPath)
                val connected = suspendCancellableCoroutine<Boolean> { cont ->
                    session.setStateObserver(object : WnSteamStateObserver {
                        override fun onStateChanged(state: Int) {
                            if (!cont.isActive) return
                            if (state == 2) cont.resume(true)
                            else if (state == 0) cont.resume(false)
                        }
                        override fun onClientMessage(emsg: Int, eresult: Int, body: ByteArray) {}
                    })
                    if (!session.connect(cmUrl)) cont.resume(false)
                    cont.invokeOnCancellation { session.disconnect() }
                }
                if (!connected) {
                    Timber.e("WnSteam channel did not reach Connected state")
                    return null
                }
                ok = true
                return session
            } finally {
                if (!ok) {
                    try { session.disconnect() } catch (_: Throwable) {}
                    try { session.close() } catch (_: Throwable) {}
                }
            }
        }

        suspend fun startLoginWithQr() = withContext(Dispatchers.IO) {
            val svc = instance ?: run {
                PluviaApp.events.emit(
                    SteamEvent.QrAuthEnded(success = false,
                        message = "SteamService not initialized"),
                )
                return@withContext
            }

            Timber.i("Logging in via QR (wn-steam-client).")
            isWaitingForQRAuth = true

            teardownPriorWnSession()

            val session = bringUpWnSession(svc) ?: run {
                isWaitingForQRAuth = false
                PluviaApp.events.emit(
                    SteamEvent.QrAuthEnded(success = false,
                        message = "Failed to connect to Steam"),
                )
                return@withContext
            }
            wnAuthSession = session
            var keepSessionAlive = false
            try {
                var qrScannedEmitted = false
                val result = suspendCancellableCoroutine<WnAuthResult> { cont ->
                    session.startLoginWithQr(
                        qrCallback = WnQrCallback { url ->
                            PluviaApp.events.emit(SteamEvent.QrChallengeReceived(url))
                        },
                        resultCallback = WnAuthCallback { r ->
                            if (!qrScannedEmitted && r.hadRemoteInteraction) {
                                qrScannedEmitted = true
                                PluviaApp.events.emit(SteamEvent.QrCodeScanned)
                            }
                            if (cont.isActive) cont.resume(r)
                        },
                    )
                    cont.invokeOnCancellation { session.cancelLogin() }
                }

                isWaitingForQRAuth = false
                PluviaApp.events.emit(SteamEvent.QrAuthEnded(result.success))

                if (!result.success || result.refreshToken.isEmpty()) {
                    Timber.e("WnSteam QR auth failed: %s", result.errorMessage)
                    return@withContext
                }

                // Persist the acquired tokens so a later cold start auto-logons.
                persistLoginTokens(
                    username = result.accountName,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                )

                // Phase 9 — promote QR session to the long-lived logon session.
                // DO NOT insert a suspension point in these four lines —
                // see the matching note in startLoginWithCredentials.
                installWnLogonObserver(session)
                wnSession = session
                wnAuthSession = null
                keepSessionAlive = true

                if (!session.logonWithRefreshToken(result.refreshToken, result.accountName, result.steamId)) {
                    Timber.w("WnSteam QR logon_with_refresh_token returned false")
                }

                // Watchdog: surface a failure if the CM logon hangs.
                svc.scope.launch {
                    var waited = 0
                    while (waited < 35 && session.state() != 3) { delay(1000); waited++ }
                    if (session.state() != 3 && wnSession === session) {
                        Timber.w("WnSteam QR CM logon never reached LoggedOn")
                        PluviaApp.events.emit(
                            SteamEvent.LogonEnded(result.accountName, LoginResult.Failed,
                                "Steam logon timed out"),
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "QR failed")
                isWaitingForQRAuth = false
                val message = when (e) {
                    is CancellationException -> "QR Session timed out"
                    else -> e.message ?: e.javaClass.name
                }
                PluviaApp.events.emit(SteamEvent.QrAuthEnded(success = false, message = message))
            } finally {
                if (!keepSessionAlive) {
                    try { session.disconnect() } catch (_: Throwable) {}
                    try { session.close() } catch (_: Throwable) {}
                    if (wnAuthSession === session) wnAuthSession = null
                }
            }
        }

        fun stopLoginWithQr() {
            Timber.i("Stopping QR polling")
            isWaitingForQRAuth = false
            wnAuthSession?.let {
                try { it.cancelLogin() } catch (_: Throwable) {}
            }
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, SteamService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start SteamService")
            }
        }

        /**
         * App-lifecycle hooks, called by PluviaApp when the app's last
         * activity stops ([onAppBackgrounded]) or its first activity starts
         * ([onAppForegrounded]). They let the Steam session sleep while the
         * app is minimized and idle — see [handleAppBackgrounded].
         */
        fun onAppForegrounded() {
            instance?.handleAppForegrounded()
        }

        fun onAppBackgrounded() {
            instance?.handleAppBackgrounded()
        }

        fun stop() {
            instance?.let { steamInstance ->
                if (!isStopping) {
                    isStopping = true
                    runCatching {
                        steamInstance.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    }.onFailure { Timber.w(it, "Failed to remove SteamService foreground state during shutdown") }
                    runCatching {
                        steamInstance.notificationHelper.cancel()
                    }.onFailure { Timber.w(it, "Failed to cancel SteamService notification during shutdown") }
                    steamInstance.stopSelf()
                }
                steamInstance.scope.launch {
                    steamInstance.stop()
                }
            }
        }

        fun logOut() {
            // Capture username before clearing anything
            val username = PrefManager.username

            // ── Atomic state flip ──
            isLoggingOut = true
            _isLoggedInFlow.value = false
            PrefManager.clearAuthTokens()

            // Tear down the long-lived WnSteam logon session.
            wnSession?.let { s ->
                try { s.disconnect() } catch (_: Throwable) {}
                try { s.close()      } catch (_: Throwable) {}
            }
            wnSession = null
            wnLibrary?.stopObserving()
            wnLibrary = null

            // Cancel background jobs immediately
            instance?.picsGetProductInfoJob?.cancel()
            instance?.picsChangesCheckerJob?.cancel()
            instance?.friendCheckerJob?.cancel()

            // Emit event synchronously so the UI can react in the same frame
            PluviaApp.events.emit(SteamEvent.LoggedOut(username))

            // The C++ WN-Steam-Client session was already disconnected above;
            // just clear the local database (best-effort).
            instance?.let { svc ->
                svc.scope.launch(Dispatchers.Default) {
                    try {
                        clearDatabase()
                    } catch (e: Exception) {
                        Timber.e(e, "Error during async logOff")
                    }
                }
            }
            // No JavaSteam onLoggedOff callback to stop the service any more —
            // do it here.
            stop()
        }

        fun requestSync() {
            instance?.let { service ->
                service.scope.launch {
                    refreshOwnedGamesFromServer()
                }
            }
        }

        private fun clearUserData() {
            PrefManager.clearAuthTokens()

            clearDatabase()
        }

        fun clearDatabase() {
            with(instance!!) {
                scope.launch {
                    db.withTransaction {
                        // We NO LONGER delete apps, change numbers, or file lists here.
                        // This preserves the installed games and shortcuts.
                        // appDao.deleteAll()
                        // We keep app metadata, but cloud sync caches are cleared separately.

                        licenseDao.deleteAll()
                        encryptedAppTicketDao.deleteAll()
                        downloadingAppInfoDao.deleteAll()
                    }
                }
            }
        }

        private fun clearCloudSyncCaches() {
            instance?.let { svc ->
                svc.scope.launch {
                    svc.db.withTransaction {
                        svc.changeNumbersDao.deleteAll()
                        svc.fileChangeListsDao.deleteAll()
                    }
                    Timber.i("Cleared cloud sync caches (change numbers + file lists)")
                }
            }
        }

        suspend fun getOwnedGames(friendID: Long): List<OwnedGames> =
            withContext(Dispatchers.IO) {
                instance?._unifiedFriends!!.getOwnedGames(friendID)
            }

        // Add helper to detect if any downloads or cloud sync are in progress
        fun hasActiveOperations(): Boolean {
            val anySyncInProgress = syncInProgressApps.values.any { it.get() }
            return anySyncInProgress || downloadJobs.values.any { it.getProgress() < 1f }
        }

        // Should service auto-stop when idle (backgrounded)?
        var autoStopWhenIdle: Boolean = false

        suspend fun isUpdatePending(
            appId: Int,
            branch: String = "public",
        ): Boolean = checkForAppUpdate(appId, branch).hasUpdate

        suspend fun checkForAppUpdate(
            appId: Int,
            branch: String = "public",
        ): SteamUpdateInfo =
            withContext(Dispatchers.IO) {
                fun SteamUpdateInfo.logged(): SteamUpdateInfo {
                    Timber.i(
                        "Steam update check result: appId=$appId branch=$branch " +
                            "hasUpdate=$hasUpdate downloadSize=$downloadSize depotIds=$depotIds message=$message",
                    )
                    return this
                }

                Timber.i("Steam update check started: appId=$appId branch=$branch")
                if (!isConnected || !isLoggedIn) {
                    return@withContext SteamUpdateInfo(message = "Steam is not connected").logged()
                }
                if (!isAppInstalled(appId)) {
                    return@withContext SteamUpdateInfo(message = "Game is not installed").logged()
                }

                val remoteSteamApp = fetchLatestSteamAppInfo(appId)
                    ?: return@withContext SteamUpdateInfo(message = "Could not fetch Steam metadata").logged()
                persistLatestSteamAppInfo(appId, remoteSteamApp)

                val appDirPath = getAppDirPath(appId)
                val selectedDepots =
                    getSelectedDownloadDepots(
                        appId = appId,
                        userSelectedDlcAppIds = resolveInstalledDlcIdsForUpdateOrVerify(appId),
                        preferredLanguage = PrefManager.containerLanguage,
                        branch = branch,
                    )
                if (selectedDepots.isEmpty()) {
                    return@withContext SteamUpdateInfo(message = "No installed depots to update").logged()
                }

                val installedManifestIds = readInstalledDepotManifestIds(appDirPath)
                val updateDepots =
                    selectedDepots.filter { (depotId, depot) ->
                        val manifest = resolveDepotManifestInfo(depot, branch) ?: return@filter false
                        val installedManifestId = installedManifestIds[depotId]
                        if (installedManifestId != null) {
                            installedManifestId != manifest.gid
                        } else {
                            !hasCachedDepotManifest(appDirPath, depotId, manifest.gid)
                        }
                    }

                if (updateDepots.isEmpty()) {
                    SteamUpdateInfo(hasUpdate = false).logged()
                } else {
                    SteamUpdateInfo(
                        hasUpdate = true,
                        downloadSize =
                            updateDepots.values
                                .sumOf { depot -> manifestDownloadBytes(resolveDepotManifestInfo(depot, branch)) }
                                .coerceAtLeast(0L),
                        depotIds = updateDepots.keys.sorted(),
                    ).logged()
                }
            }

        /**
         * Transitional bridge: converts a JavaSteam [KeyValue] tree into the
         * nested Map the in-house [WnKeyValue] consumes. Deleted once the
         * remaining JavaSteam PICS call sites are ported (Phase 9).
         */

        private suspend fun fetchLatestSteamAppInfo(appId: Int): SteamApp? {
            // Primary path: the C++ WN-Steam-Client (Phase 9 — JavaSteam is
            // being dropped). getPicsAppInfo returns {"changeNumber":N,
            // "appinfo":{...}} — the C++ side already parsed the appinfo VDF;
            // WnKeyValue decodes the JSON tree into a SteamApp.
            val wnApp =
                withWnSession { session ->
                    withContext(Dispatchers.IO) {
                        session.getPicsAppInfo(appId)?.let { json ->
                            try {
                                val obj = JSONObject(json)
                                val appinfo = obj.optJSONObject("appinfo") ?: return@let null
                                val app = WnKeyValue.fromJsonObject(appinfo).generateSteamApp()
                                if (app.id == INVALID_APP_ID) {
                                    null
                                } else {
                                    app.copy(
                                        receivedPICS = true,
                                        lastChangeNumber = obj.optInt("changeNumber", 0),
                                    )
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "wn-steam-client appinfo parse failed for appId=$appId")
                                null
                            }
                        }
                    }
                }
            if (wnApp != null) {
                Timber.i("app info via wn-steam-client: appId=$appId name='${wnApp.name}'")
                return wnApp
            }
            Timber.w("wn-steam-client app info unavailable for appId=$appId")
            return null
        }

        private suspend fun persistLatestSteamAppInfo(
            appId: Int,
            remoteSteamApp: SteamApp,
        ) {
            val service = instance ?: return
            val appFromDb = service.appDao.findApp(appId)
            val packageId = appFromDb?.packageId ?: remoteSteamApp.packageId
            val packageFromDb = if (packageId != INVALID_PKG_ID) service.licenseDao.findLicense(packageId) else null
            val existingInstallDir = appFromDb?.installDir.orEmpty()
            val preserveInstallDir =
                existingInstallDir.isNotEmpty() &&
                    (existingInstallDir.startsWith("/") || existingInstallDir.contains(File.separator))

            service.appDao.insert(
                remoteSteamApp.copy(
                    packageId = packageId,
                    ownerAccountId = packageFromDb?.ownerAccountId ?: appFromDb?.ownerAccountId.orEmpty(),
                    licenseFlags =
                        packageFromDb?.licenseFlags
                            ?: appFromDb?.licenseFlags
                            ?: EnumSet.noneOf(ELicenseFlags::class.java),
                    installDir = if (preserveInstallDir) existingInstallDir else remoteSteamApp.installDir,
                ),
            )
        }

        private fun readInstalledDepotManifestIds(appDirPath: String): Map<Int, Long> =
            runCatching {
                val configFile = File(File(appDirPath, ".DepotDownloader"), "depot.config")
                if (!configFile.exists() || !configFile.canRead()) return@runCatching emptyMap()
                val json = JSONObject(configFile.readText())
                val manifests = json.optJSONObject("installedManifestIDs") ?: return@runCatching emptyMap()
                val result = mutableMapOf<Int, Long>()
                for (key in manifests.keys()) {
                    val depotId = key.toIntOrNull() ?: continue
                    // INVALID_MANIFEST_ID (was DepotDownloader.INVALID_MANIFEST_ID).
                    result[depotId] = manifests.optLong(key, Long.MAX_VALUE)
                }
                result
            }.getOrElse {
                Timber.w(it, "Failed to read Steam depot.config for $appDirPath")
                emptyMap()
            }

        private fun hasCachedDepotManifest(
            appDirPath: String,
            depotId: Int,
            manifestId: Long,
        ): Boolean = File(File(appDirPath, ".DepotDownloader"), "${depotId}_${manifestId}.manifest").exists()

        private fun cleanupCancelledUpdate(appDirPath: String) {
            MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            clearPersistedProgressSnapshot(appDirPath)

            val stagingDir = File(File(appDirPath, ".DepotDownloader"), "staging")
            if (!stagingDir.exists()) return

            stagingDir
                .walkBottomUp()
                .forEach { staged ->
                    if (staged == stagingDir) return@forEach
                    if (staged.isDirectory) {
                        if (staged.list().isNullOrEmpty()) staged.delete()
                        return@forEach
                    }

                    val relative = staged.relativeTo(stagingDir)
                    val finalFile = File(appDirPath, relative.path)
                    runCatching {
                        finalFile.parentFile?.mkdirs()
                        if (finalFile.exists()) {
                            finalFile.delete()
                        }
                        if (!staged.renameTo(finalFile)) {
                            staged.copyTo(finalFile, overwrite = true)
                            staged.delete()
                        }
                    }.onFailure {
                        Timber.w(it, "Failed to restore staged Steam update file ${staged.absolutePath}")
                    }
                }

            if (stagingDir.exists() && stagingDir.list().isNullOrEmpty()) {
                stagingDir.delete()
            }
        }

        suspend fun checkDlcOwnershipViaPICSBatch(dlcAppIds: Set<Int>): Set<Int> {
            if (dlcAppIds.isEmpty()) return emptySet()

            try {
                // Step 1: PICS access tokens via the C++ WN-Steam-Client
                // (Phase 9). A granted token ⇒ candidate ownership.
                val tokJson =
                    withWnSession { session ->
                        withContext(Dispatchers.IO) {
                            session.getPicsAccessTokens(dlcAppIds.toList(), emptyList())
                        }
                    } ?: return emptySet()

                val tokenMap = HashMap<Int, Long>()
                JSONObject(tokJson).optJSONObject("appTokens")?.let { at ->
                    for (k in at.keys()) {
                        val id = k.toIntOrNull() ?: continue
                        if (id in dlcAppIds) {
                            tokenMap[id] = at.getString(k).toLongOrNull() ?: 0L
                        }
                    }
                }
                Timber.d("DLC ownership: ${tokenMap.size} candidate(s) from access tokens")
                if (tokenMap.isEmpty()) {
                    Timber.w("No owned DLCs found via access tokens")
                    return emptySet()
                }

                // Step 2: confirm via PICS product info — an app that returns
                // a product-info entry is owned/accessible.
                val allOwnedAppIds = mutableSetOf<Int>()
                tokenMap.keys.toList().chunked(100).forEach { chunk ->
                    val infoJson =
                        withWnSession { session ->
                            withContext(Dispatchers.IO) {
                                session.getPicsAppProductInfo(chunk, chunk.map { tokenMap[it] ?: 0L })
                            }
                        } ?: return@forEach
                    val arr = JSONArray(infoJson)
                    for (i in 0 until arr.length()) {
                        allOwnedAppIds.add(arr.getJSONObject(i).optInt("appid"))
                    }
                }

                Timber.i(
                    "Final owned DLC appIds (wn): $allOwnedAppIds " +
                        "(${allOwnedAppIds.size} of ${dlcAppIds.size} checked)",
                )
                return allOwnedAppIds
            } catch (e: Exception) {
                Timber.e(e, "checkDlcOwnershipViaPICSBatch (wn) failed for ${dlcAppIds.size} appIds")
                return emptySet()
            }
        }
    }

    private val coordinatorDispatcher =
        object : DownloadCoordinator.Dispatcher {
            override fun startQueued(record: DownloadRecord) {
                val appId = record.storeGameId.toIntOrNull() ?: return
                // Drop any stale queued/paused DownloadInfo from the in-memory map BEFORE
                // calling downloadApp(). Otherwise SteamService.downloadApp() finds the
                // inactive entry, calls removeDownloadJob() (which fires the legacy
                // checkQueue() and an extra notify event), and only then proceeds to build
                // a fresh DownloadInfo. Removing here directly avoids that duplicate path.
                downloadJobs.remove(appId)
                // For normal installs, selectedDlcs carries the authoritative DLC app IDs.
                // For update tasks, the same persisted field carries the changed depot IDs
                // reported by checkForAppUpdate(), so queued updates keep the narrowed scope.
                val persistedIds =
                    record.selectedDlcs
                        .split(',')
                        .mapNotNull { it.trim().toIntOrNull() }
                if (record.taskType == DownloadRecord.TASK_UPDATE) {
                    downloadAppForUpdate(appId, persistedIds)
                } else if (record.taskType == DownloadRecord.TASK_VERIFY) {
                    downloadAppForVerify(appId)
                } else {
                    downloadApp(appId, persistedIds)
                }
            }

            override fun pauseRunning(record: DownloadRecord) {
                val appId = record.storeGameId.toIntOrNull() ?: return
                val info = downloadJobs[appId] ?: return
                val status = info.getStatusFlow().value
                if (status == DownloadPhase.COMPLETE || status == DownloadPhase.CANCELLED) return
                if (info.isActive()) {
                    info.isCancelling = false
                    info.updateStatus(DownloadPhase.PAUSED)
                    info.cancel("Paused by user")
                } else if (status == DownloadPhase.QUEUED) {
                    info.updateStatus(DownloadPhase.PAUSED)
                    info.setActive(false)
                    notifyDownloadStopped(appId)
                }
            }

            override fun cancelRunning(record: DownloadRecord) {
                val appId = record.storeGameId.toIntOrNull() ?: return
                val info = downloadJobs[appId]
                val statusAtCancel = info?.getStatusFlow()?.value
                if (info != null) {
                    info.isCancelling = true
                    info.cancel("Cancelled by user")
                }
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val isUpdateTask = record.taskType == DownloadRecord.TASK_UPDATE
                    info?.awaitCompletion(timeoutMs = if (isUpdateTask) 10000L else 3000L)
                    val appDirPath = record.installPath.ifEmpty { getAppDirPath(appId) }
                    if (isUpdateTask) {
                        val updateNeverStarted =
                            statusAtCancel == DownloadPhase.QUEUED ||
                                (
                                    statusAtCancel == DownloadPhase.PAUSED &&
                                        MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER) &&
                                        !MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                                )
                        if (updateNeverStarted) {
                            MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                            MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                        } else {
                            cleanupCancelledUpdate(appDirPath)
                        }
                        try {
                            instance?.downloadingAppInfoDao?.deleteApp(appId)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to clear cancelled Steam update metadata for appId=$appId")
                        }
                        info?.updateStatus(DownloadPhase.CANCELLED)
                        removeDownloadJob(appId, forceRemove = true)
                        return@launch
                    }
                    val dirFile = java.io.File(appDirPath)
                    if (dirFile.exists() && dirFile.isDirectory) {
                        val deleteCheck =
                            StoreInstallPathSafety.checkInstallDirDelete(
                                instance?.applicationContext ?: DownloadService.appContext,
                                appDirPath,
                                protectedRoots = steamProtectedInstallRoots(),
                            )
                        if (deleteCheck.allowed) {
                            MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                            MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                            deleteRecursivelyWithRetries(dirFile)
                        } else {
                            Timber.e("Refusing to delete cancelled Steam download path '$appDirPath': ${deleteCheck.reason}")
                        }
                    }
                    info?.updateStatus(DownloadPhase.CANCELLED)
                    removeDownloadJob(appId, forceRemove = true)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        instance = this

        notificationHelper = NotificationHelper(applicationContext)
        val notification = notificationHelper.createForegroundNotification("Steam Service is running")
        startForeground(1, notification)

        // Connection/login flows are driven by the C++ WN-Steam-Client session
        // observer; isLoggedInFlow is pre-seeded by initLoginStatus().
        _isConnectedFlow.value = false

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)

        DownloadCoordinator.init(db)
        DownloadCoordinator.registerDispatcher(DownloadRecord.STORE_STEAM, coordinatorDispatcher)

        // Re-arm the background idle timer whenever a download changes state.
        // A download that kept the session awake while the app is backgrounded
        // can finish / pause — re-arming ensures the session is re-evaluated
        // (and suspended once idle) without waiting on the running timer's
        // current interval. The grace delay still applies.
        scope.launch {
            DownloadCoordinator.changes.collect {
                if (!appInForeground) scheduleBackgroundSuspendCheck()
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Notification intents
        when (intent?.action) {
            NotificationHelper.ACTION_EXIT -> {
                Timber.d("Exiting app via notification intent")
                AppTerminationHelper.stopManagedServices(applicationContext, "notification_exit")

                return START_NOT_STICKY
            }
        }

        if (!isRunning) {
            isRunning = true

            _unifiedFriends = SteamUnifiedFriends(this)
            // FamilyGroups / friends go through the C++ WN-Steam-Client (Phase 9).

            // Phase 9: there is no JavaSteam CM client. If we have stored
            // credentials, bring up the C++ WN-Steam-Client session and log
            // it on — its state observer drives the rest of the lifecycle.
            // A fresh login (no stored token yet) comes in later via
            // startLoginWith{Credentials,Qr}.
            if (PrefManager.refreshToken.isNotBlank()) {
                connectAndLogon()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }

        DownloadCoordinator.unregisterDispatcher(DownloadRecord.STORE_STEAM)

        // Persist download progress for all active downloads
        // This is a safety net for OS kills (unlikely but possible)
        downloadJobs.values.forEach { downloadInfo ->
            downloadInfo.persistProgressSnapshot(force = true)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel()

        if (!isStopping) {
            scope.launch { stop() }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("Task removed; stopping managed app services")
        AppTerminationHelper.stopManagedServices(applicationContext, "steam_task_removed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Brings up (or reuses) the long-lived C++ WN-Steam-Client session and
     * logs it on with the stored refresh token. [withWnSession] promotes the
     * brought-up session and installs the orchestrator observer (which fires
     * [onWnLoggedOn]). Retries every 5s until a logged-on session exists or
     * the service stops. Used for cold-start auto-logon and reconnect.
     */
    private fun connectAndLogon() {
        if (connectJob?.isActive == true) return
        connectJob =
            scope.launch {
                PluviaApp.events.emit(SteamEvent.Connected(true))
                var attempt = 0
                while (isRunning && !isStopping && PrefManager.refreshToken.isNotBlank()) {
                    if (wnSession?.state() == 3) break
                    Timber.d("connectAndLogon: bringing up WN-Steam-Client session...")
                    val state = withWnSession { it.state() }
                    if (state == 3) break
                    attempt++
                    if (attempt >= CONNECT_LOGON_MAX_ATTEMPTS) {
                        // Logon has failed this many times running — almost
                        // certainly an expired/revoked refresh token or a
                        // sustained outage. Stop here instead of spinning a
                        // full WSS + logon attempt every few seconds forever
                        // (a background battery drain). A foreground wake or
                        // an explicit re-login re-triggers connectAndLogon.
                        Timber.w("connectAndLogon: giving up after $attempt failed attempts")
                        break
                    }
                    val backoffMs = reconnectBackoffMs(attempt)
                    Timber.w("connectAndLogon: not logged on — retry $attempt in ${backoffMs}ms")
                    delay(backoffMs)
                }
            }
    }

    /**
     * App returned to the foreground. Wake the Steam session if it was
     * suspended for background — reconnect and let the logon observer
     * restart the PICS loops via onWnLoggedOn.
     */
    private fun handleAppForegrounded() {
        appInForeground = true
        // Cancel any pending suspend timer — the app is back, so the session
        // must stay up regardless of how long it was minimized.
        backgroundIdleJob?.cancel()
        backgroundIdleJob = null
        if (!suspendedForBackground) return
        suspendedForBackground = false
        Timber.i("App foregrounded — waking the WN-Steam-Client session")
        retryAttempt = 0
        if (isRunning && !isStopping && PrefManager.refreshToken.isNotBlank()) {
            connectAndLogon()
        }
    }

    /** App went to the background — arm the deferred suspend check. */
    private fun handleAppBackgrounded() {
        appInForeground = false
        scheduleBackgroundSuspendCheck()
    }

    /**
     * Arm (or re-arm) the background idle timer. After [BACKGROUND_IDLE_GRACE_MS]
     * of the app staying backgrounded, [maybeSuspendForBackground] decides
     * whether the Steam session may sleep. If a connection-critical operation
     * is still running at that point the check simply repeats once per grace
     * interval until the work finishes — so nothing has to hook every
     * operation's completion. A foreground event cancels this timer.
     */
    private fun scheduleBackgroundSuspendCheck() {
        backgroundIdleJob?.cancel()
        if (appInForeground || isStopping || isLoggingOut) return
        backgroundIdleJob =
            scope.launch {
                while (isActive) {
                    delay(BACKGROUND_IDLE_GRACE_MS)
                    if (appInForeground || isStopping || isLoggingOut || suspendedForBackground) {
                        return@launch
                    }
                    // Suspended → done. Still busy → loop and re-check later.
                    if (maybeSuspendForBackground()) return@launch
                }
            }
    }

    /**
     * Returns a human-readable reason the Steam connection must stay open, or
     * null when it is safe to suspend. Covers everything that would break or
     * corrupt data if the CM session dropped mid-operation:
     *  - an actively transferring game download (paused/queued do not count),
     *  - a running game session,
     *  - an in-flight cloud save sync — a download/restore or an upload, which
     *    must never be interrupted or the save file can be left corrupt.
     */
    private fun connectionCriticalWork(): String? =
        when {
            DownloadCoordinator.hasActiveDownload() -> "a download is active"
            PluviaApp.isGameSessionActive() -> "a game session is running"
            syncInProgressApps.values.any { it.get() } -> "a cloud save sync is in progress"
            else -> null
        }

    /**
     * Suspend the Steam session while the app is backgrounded so it draws no
     * power — UNLESS [connectionCriticalWork] reports work that still needs
     * the connection. Suspending disconnects the C++ session and cancels
     * every reconnect / PICS loop; the session wakes again from
     * [handleAppForegrounded]. Returns true if the session was suspended.
     */
    private fun maybeSuspendForBackground(): Boolean {
        if (appInForeground || isStopping || isLoggingOut || suspendedForBackground) return false
        val keepAliveReason = connectionCriticalWork()
        if (keepAliveReason != null) {
            Timber.i("App backgrounded but %s — keeping the Steam session connected", keepAliveReason)
            return false
        }
        Timber.i("App backgrounded and idle — suspending WN-Steam-Client session to save battery")
        suspendedForBackground = true
        connectJob?.cancel()
        reconnectJob?.cancel()
        stableConnectionJob?.cancel()
        picsChangesCheckerJob?.cancel()
        picsGetProductInfoJob?.cancel()
        wnSession?.let { s -> runCatching { s.disconnect() } }
        return true
    }

    private suspend fun stop() {
        Timber.i("Stopping Steam service")
        isStopping = true
        connectJob?.cancel()
        reconnectJob?.cancel()
        stableConnectionJob?.cancel()
        wnSession?.let { s ->
            runCatching { s.disconnect() }
            runCatching { s.close() }
        }
        wnSession = null
        clearValues()
    }

    private fun clearValues() {
        if (instance === this) {
            instance = null
        }

        _loginResult = LoginResult.Failed
        isRunning = false
        isConnected = false
        isLoggingOut = false
        isWaitingForQRAuth = false

        wnLoggedOnHandled = false
        wnLibrary?.stopObserving()
        wnLibrary = null

        _unifiedFriends?.close()
        _unifiedFriends = null

        isStopping = false
        retryAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        stableConnectionJob?.cancel()
        stableConnectionJob = null
        backgroundIdleJob?.cancel()
        backgroundIdleJob = null
        suspendedForBackground = false
        appInForeground = true

        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)
        PluviaApp.events.clearAllListenersOf<SteamEvent<Any>>()
    }

    // region [REGION] WN-Steam-Client lifecycle (Phase 9)

    /**
     * Channel-dropped handler — Phase 9 replacement for the JavaSteam
     * `onDisconnected` callback. Reconnects while credentials + retries
     * remain; otherwise emits Disconnected and stops the service. Fired
     * from the [installWnLogonObserver] state observer.
     */
    /**
     * Exponential reconnect backoff: 2s, 4s, 8s … doubling per attempt and
     * capped at [RECONNECT_BACKOFF_CAP_MS]. `attempt` is the 1-based retry
     * count. Without this, a connection that briefly logs on then drops
     * (typical when the app is backgrounded and Android throttles the
     * heartbeat) reconnects in a tight loop and overheats the device.
     */
    private fun reconnectBackoffMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 8) // 2^0 .. 2^8
        val seconds = (1L shl shift) * 2L // 2, 4, 8, …, 512
        return (seconds * 1000L).coerceAtMost(RECONNECT_BACKOFF_CAP_MS)
    }

    fun onWnDisconnected() {
        Timber.i("WN-Steam-Client channel disconnected")
        if (isStopping || isLoggingOut) return
        // A disconnect we triggered ourselves to sleep the backgrounded app
        // must NOT schedule a reconnect — that would defeat the suspend and
        // is the storm this whole change set exists to stop.
        if (suspendedForBackground) {
            Timber.i("Channel disconnect was an intentional background suspend — not reconnecting")
            return
        }
        // This drop means the just-ended session was NOT stable — cancel the
        // pending stable-connection timer so the retry budget keeps climbing
        // and the backoff below actually grows.
        stableConnectionJob?.cancel()
        stableConnectionJob = null
        if (retryAttempt < MAX_RETRY_ATTEMPTS && PrefManager.refreshToken.isNotBlank()) {
            retryAttempt++
            val backoffMs = reconnectBackoffMs(retryAttempt)
            Timber.w("Reconnect scheduled in ${backoffMs}ms (retry $retryAttempt/$MAX_RETRY_ATTEMPTS)")
            notificationHelper.notify("Retrying...")
            PluviaApp.events.emit(SteamEvent.RemotelyDisconnected)
            reconnectJob?.cancel()
            reconnectJob =
                scope.launch {
                    delay(backoffMs)
                    if (!isStopping && !isLoggingOut) connectAndLogon()
                }
        } else {
            PluviaApp.events.emit(SteamEvent.Disconnected)
            clearValues()
            stopSelf()
        }
    }

    /**
     * Post-logon orchestration — Phase 9 replacement for the JavaSteam
     * `onLoggedOn` callback. Runs exactly once per logged-on
     * [WnSteamSession] (guarded by `wnLoggedOnHandled`), fired from the
     * [installWnLogonObserver] state observer or the [withWnSession]
     * promotion path.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun onWnLoggedOn(session: WnSteamSession) {
        Timber.i("Logged onto Steam (WN-Steam-Client)")

        // Do NOT reset retryAttempt here. A connection that logs on then
        // drops within STABLE_CONNECTION_MS is not healthy; zeroing the
        // budget immediately let a flapping connection reconnect without
        // bound. Arm a timer instead — it resets retryAttempt only once the
        // session has stayed up long enough; onWnDisconnected cancels it.
        stableConnectionJob?.cancel()
        stableConnectionJob =
            scope.launch {
                delay(STABLE_CONNECTION_MS)
                retryAttempt = 0
                Timber.d("Connection stable — reconnect retry budget reset")
            }
        isLoggingOut = false
        _isLoggedInFlow.value = true

        val steamId64 = session.steamId()
        if (steamId64 != 0L) {
            // SteamID.accountID == the low 32 bits of the SteamID64.
            val accountId = (steamId64 and 0xFFFFFFFFL).toInt()
            if (PrefManager.steamUserAccountId != accountId) {
                PrefManager.steamUserAccountId = accountId
                Timber.d("Saving logged in Steam accountID $accountId")
                clearCloudSyncCaches()
            }
            if (PrefManager.steamUserSteamId64 != steamId64) {
                PrefManager.steamUserSteamId64 = steamId64
                Timber.d("Saving logged in Steam ID64 $steamId64")
            }
        }

        // retrieve persona data of logged in user
        scope.launch { requestUserPersona() }

        // Populate the license tables from the C++ WN-Steam-Client's
        // CMsgClientLicenseList.
        scope.launch { processLicenseList() }

        // Request family share info if the logon response gave us a family id.
        val familyGroupId = session.familyGroupId()
        if (familyGroupId != 0L) {
            scope.launch {
                val json = withWnSession { s -> s.getFamilyGroup(familyGroupId) }
                if (json == null) {
                    Timber.w("An error occurred loading family group info.")
                    return@launch
                }
                try {
                    val obj = JSONObject(json)
                    val members = obj.optJSONArray("members")
                    Timber.i(
                        "Found family share: ${obj.optString("name")}, " +
                            "with ${members?.length() ?: 0} members.",
                    )
                    if (members != null) {
                        for (i in 0 until members.length()) {
                            val memberId64 = members.getLong(i)
                            familyGroupMembers.add((memberId64 and 0xFFFFFFFFL).toInt())
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "family group: parse failed")
                }
            }
        }

        picsChangesCheckerJob?.cancel()
        picsChangesCheckerJob = continuousPICSChangesChecker()
        picsGetProductInfoJob?.cancel()
        picsGetProductInfoJob = continuousPICSGetProductInfo()

        // Tell steam we're online, this allows friends to update.
        scope.launch {
            withWnSession { s ->
                s.setPersonaState(
                    (EPersonaState.from(PrefManager.personaState) ?: EPersonaState.Online).code(),
                )
            }
        }

        notificationHelper.notify("Connected")
        _loginResult = LoginResult.Success
        PluviaApp.events.emit(SteamEvent.LogonEnded(PrefManager.username, LoginResult.Success))
    }
    // endregion

    /**
     * Populate the steam_license / cached_license Room tables from the
     * licenses the C++ WN-Steam-Client received (CMsgClientLicenseList).
     * Phase 9 replacement for the JavaSteam onLicenseList callback — driven
     * from the post-logon flow instead of a LicenseListCallback.
     */
    private suspend fun processLicenseList() {
        // The license list is pushed just after logon; poll briefly for it.
        var json: String? = null
        for (attempt in 0 until 15) {
            json = withWnSession { session -> session.getLicenseList() }
            if (json != null && json != "[]") break
            delay(200)
        }
        val arr =
            try {
                JSONArray(json ?: "[]")
            } catch (e: Exception) {
                Timber.w(e, "processLicenseList: bad license JSON")
                return
            }
        if (arr.length() == 0) {
            Timber.w("processLicenseList: no licenses received")
            return
        }
        Timber.i("Received License List, size: ${arr.length()}")

        data class RawLicense(
            val packageId: Int, val changeNumber: Int,
            val timeCreated: Long, val timeNextProcess: Long,
            val minuteLimit: Int, val minutesUsed: Int,
            val paymentMethod: Int, val flags: Int,
            val purchaseCountryCode: String, val licenseType: Int,
            val territoryCode: Int, val accessToken: Long,
            val ownerId: Int, val masterPackageId: Int,
        )
        val raw =
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RawLicense(
                    packageId = o.optInt("packageId"),
                    changeNumber = o.optInt("changeNumber"),
                    timeCreated = o.optLong("timeCreated"),
                    timeNextProcess = o.optLong("timeNextProcess"),
                    minuteLimit = o.optInt("minuteLimit"),
                    minutesUsed = o.optInt("minutesUsed"),
                    paymentMethod = o.optInt("paymentMethod"),
                    flags = o.optInt("flags"),
                    purchaseCountryCode = o.optString("purchaseCountryCode"),
                    licenseType = o.optInt("licenseType"),
                    territoryCode = o.optInt("territoryCode"),
                    accessToken = o.optLong("accessToken"),
                    ownerId = o.optInt("ownerId"),
                    masterPackageId = o.optInt("masterPackageId"),
                )
            }

        db.withTransaction {
            // Note: every launch refreshes licenses, so 'findStaleLicences'
            // picks up packages we no longer have (e.g. family-share changes).

            // Store raw licenses for the manifest-fetch path (CachedLicense).
            cachedLicenseDao.deleteAll()
            cachedLicenseDao.insertAll(
                raw.map { l ->
                    CachedLicense(
                        licenseJson =
                            LicenseSerializer.serializeLicenseFields(
                                packageID = l.packageId,
                                lastChangeNumber = l.changeNumber,
                                timeCreatedMs = l.timeCreated * 1000L,
                                timeNextProcessMs = l.timeNextProcess * 1000L,
                                minuteLimit = l.minuteLimit,
                                minutesUsed = l.minutesUsed,
                                paymentMethod = l.paymentMethod,
                                flags = l.flags,
                                purchaseCode = l.purchaseCountryCode,
                                licenseType = l.licenseType,
                                territoryCode = l.territoryCode,
                                accessToken = l.accessToken,
                                ownerAccountID = l.ownerId,
                                masterPackageID = l.masterPackageId,
                            ),
                    )
                },
            )

            val myAccountId = userSteamId?.accountID?.toInt()
            val licensesToAdd =
                raw.groupBy { it.packageId }.map { (packageId, group) ->
                    val preferred =
                        group.firstOrNull { it.ownerId == myAccountId }
                            ?: group.first()
                    // OR-combine the flag bitfields across every owner of the
                    // package (matches the old reduceOrNull behaviour).
                    val combinedFlags = EnumSet.noneOf(ELicenseFlags::class.java)
                    group.forEach { combinedFlags.addAll(ELicenseFlags.from(it.flags)) }
                    SteamLicense(
                        packageId = packageId,
                        lastChangeNumber = preferred.changeNumber,
                        timeCreated = Date(preferred.timeCreated * 1000L),
                        timeNextProcess = Date(preferred.timeNextProcess * 1000L),
                        minuteLimit = preferred.minuteLimit,
                        minutesUsed = preferred.minutesUsed,
                        paymentMethod = EPaymentMethod.from(preferred.paymentMethod) ?: EPaymentMethod.None,
                        licenseFlags = combinedFlags,
                        purchaseCode = preferred.purchaseCountryCode,
                        licenseType = ELicenseType.from(preferred.licenseType) ?: ELicenseType.NoLicense,
                        territoryCode = preferred.territoryCode,
                        accessToken = preferred.accessToken,
                        ownerAccountId = group.map { it.ownerId },
                        masterPackageID = preferred.masterPackageId,
                    )
                }

            if (licensesToAdd.isNotEmpty()) {
                Timber.i("Adding ${licensesToAdd.size} licenses")
                licenseDao.insertAll(licensesToAdd)
            }

            val licensesToRemove =
                licenseDao.findStaleLicences(packageIds = raw.map { it.packageId })
            if (licensesToRemove.isNotEmpty()) {
                Timber.i("Removing ${licensesToRemove.size} (stale) licenses")
                licenseDao.deleteStaleLicenses(licensesToRemove.map { it.packageId })
            }

            // Get PICS information with the current license database.
            licenseDao
                .getAllLicenses()
                .map { PICSRequest(it.packageId, it.accessToken) }
                .chunked(MAX_PICS_BUFFER)
                .forEach { chunk ->
                    Timber.d("processLicenseList: Queueing ${chunk.size} package(s) for PICS")
                    packagePicsChannel.send(chunk)
                }
        }
    }

    // QR challenge-URL updates now flow from WnSteamSession via WnQrCallback;
    // see startLoginWithQr below. The old JavaSteam IChallengeUrlChanged
    // hook was removed in Phase 2E.
    // endregion

    /**
     * Request changes for apps and packages since a given change number.
     * Checks every [PICS_CHANGE_CHECK_DELAY] seconds.
     * Results are returned in a [PICSChangesCallback]
     */
    private fun continuousPICSChangesChecker(): Job =
        scope.launch {
            while (isActive && isLoggedIn) {
                // Initial delay before each check
                delay(60.seconds)

                PICSChangesCheck()
            }
        }

    private fun PICSChangesCheck() {
        scope.launch {
            ensureActive()

            try {
                // PICS change poll via the C++ WN-Steam-Client (Phase 9).
                val changesJson =
                    withWnSession { session ->
                        withContext(Dispatchers.IO) {
                            session.getPicsChangesSince(PrefManager.lastPICSChangeNumber.toLong())
                        }
                    }
                if (changesJson == null) {
                    Timber.w("PICS changes-since via wn-steam-client unavailable, skipping")
                    return@launch
                }
                val changes = JSONObject(changesJson)
                val currentCN = changes.optLong("currentChangeNumber", 0L)

                if (PrefManager.lastPICSChangeNumber.toLong() == currentCN) {
                    Timber.w("Change number was the same as last change number, skipping")
                    return@launch
                }
                PrefManager.lastPICSChangeNumber = currentCN.toInt()

                val appChanges = changes.optJSONArray("apps")
                val pkgChanges = changes.optJSONArray("packages")
                Timber.d(
                    "picsGetChangesSince(wn): current=$currentCN " +
                        "apps=${appChanges?.length() ?: 0} pkgs=${pkgChanges?.length() ?: 0}",
                )

                // Process any app changes
                launch {
                    val reqs = mutableListOf<PICSRequest>()
                    if (appChanges != null) {
                        for (i in 0 until appChanges.length()) {
                            val c = appChanges.getJSONObject(i)
                            val appId = c.optInt("appid")
                            // only queue apps existing in the db that have changed
                            val dbApp = appDao.findApp(appId) ?: continue
                            if (c.optInt("changeNumber") != dbApp.lastChangeNumber) {
                                reqs.add(PICSRequest(id = appId))
                            }
                        }
                    }
                    reqs.chunked(MAX_PICS_BUFFER).forEach { chunk ->
                        ensureActive()
                        Timber.d("onPicsChanges: Queueing ${chunk.size} app(s) for PICS")
                        appPicsChannel.send(chunk)
                    }
                }

                // Process any package changes
                launch {
                    data class PkgChange(val id: Int, val needsToken: Boolean)
                    val changed = mutableListOf<PkgChange>()
                    if (pkgChanges != null) {
                        for (i in 0 until pkgChanges.length()) {
                            val c = pkgChanges.getJSONObject(i)
                            val pkgId = c.optInt("packageid")
                            val dbPkg = licenseDao.findLicense(pkgId) ?: continue
                            if (c.optInt("changeNumber") != dbPkg.lastChangeNumber) {
                                changed.add(PkgChange(pkgId, c.optBoolean("needsToken")))
                            }
                        }
                    }
                    if (changed.isNotEmpty()) {
                        val needTokenIds = changed.filter { it.needsToken }.map { it.id }
                        val tokens = HashMap<Int, Long>()
                        if (needTokenIds.isNotEmpty()) {
                            val tokJson =
                                withWnSession { session ->
                                    withContext(Dispatchers.IO) {
                                        session.getPicsAccessTokens(emptyList(), needTokenIds)
                                    }
                                }
                            if (tokJson != null) {
                                JSONObject(tokJson).optJSONObject("packageTokens")?.let { pt ->
                                    for (k in pt.keys()) {
                                        tokens[k.toInt()] = pt.getString(k).toLongOrNull() ?: 0L
                                    }
                                }
                            }
                        }
                        ensureActive()
                        changed
                            .map { PICSRequest(it.id, tokens[it.id] ?: 0L) }
                            .chunked(MAX_PICS_BUFFER)
                            .forEach { chunk ->
                                Timber.d("onPicsChanges: Queueing ${chunk.size} package(s) for PICS")
                                packagePicsChannel.send(chunk)
                            }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "PICSChangesCheck failed")
            }
        }
    }

    /**
     * A buffered flow to parse so many PICS requests in a given moment.
     */
    private fun continuousPICSGetProductInfo(): Job =
        scope.launch {
            // Launch both coroutines within this parent job
            // App PICS — product info via the C++ WN-Steam-Client (Phase 9).
            launch {
                appPicsChannel
                    .receiveAsFlow()
                    .filter { it.isNotEmpty() }
                    .buffer(capacity = MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
                    .collect { appRequests ->
                        Timber.d("Processing ${appRequests.size} app PICS requests")
                        ensureActive()
                        if (!isLoggedIn) return@collect

                        val json =
                            withWnSession { session ->
                                withContext(Dispatchers.IO) {
                                    session.getPicsAppProductInfo(
                                        appRequests.map { it.id },
                                        appRequests.map { it.accessToken },
                                    )
                                }
                            } ?: return@collect

                        try {
                            val arr = JSONArray(json)
                            val steamAppsList = mutableListOf<SteamApp>()
                            for (i in 0 until arr.length()) {
                                ensureActive()
                                try {
                                    val entry = arr.getJSONObject(i)
                                    val appId = entry.optInt("appid")
                                    val changeNumber = entry.optInt("changeNumber")
                                    val appinfo = entry.optJSONObject("appinfo") ?: continue

                                    val appFromDb = appDao.findApp(appId)
                                    if (changeNumber == appFromDb?.lastChangeNumber) continue

                                    val packageId = appFromDb?.packageId ?: INVALID_PKG_ID
                                    val packageFromDb =
                                        if (packageId != INVALID_PKG_ID) licenseDao.findLicense(packageId) else null
                                    val ownerAccountId = packageFromDb?.ownerAccountId ?: emptyList()

                                    val existingInstallDir = appFromDb?.installDir.orEmpty()
                                    val preserveInstallDir =
                                        existingInstallDir.isNotEmpty() &&
                                            (existingInstallDir.startsWith("/") || existingInstallDir.contains(File.separator))

                                    val generatedApp = WnKeyValue.fromJsonObject(appinfo).generateSteamApp()
                                    steamAppsList.add(
                                        generatedApp.copy(
                                            packageId = packageId,
                                            ownerAccountId = ownerAccountId,
                                            receivedPICS = true,
                                            lastChangeNumber = changeNumber,
                                            licenseFlags = packageFromDb?.licenseFlags ?: EnumSet.noneOf(ELicenseFlags::class.java),
                                            installDir =
                                                if (preserveInstallDir) existingInstallDir else generatedApp.installDir,
                                        ),
                                    )
                                } catch (e: Exception) {
                                    Timber.w(e, "PICS app entry decode failed")
                                }
                            }
                            if (steamAppsList.isNotEmpty()) {
                                Timber.i("Inserting ${steamAppsList.size} PICS apps to database (wn)")
                                db.withTransaction { appDao.insertAll(steamAppsList) }
                            }
                        } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            Timber.w(e, "PICS app batch processing failed")
                        }
                    }
            }

            // Package PICS — package info via the C++ WN-Steam-Client (Phase 9).
            launch {
                packagePicsChannel
                    .receiveAsFlow()
                    .filter { it.isNotEmpty() }
                    .buffer(capacity = MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
                    .collect { packageRequests ->
                        Timber.d("Processing ${packageRequests.size} package PICS requests")
                        ensureActive()
                        if (!isLoggedIn) return@collect

                        val json =
                            withWnSession { session ->
                                withContext(Dispatchers.IO) {
                                    session.getPicsPackageInfo(
                                        packageRequests.map { it.id },
                                        packageRequests.map { it.accessToken },
                                    )
                                }
                            } ?: return@collect

                        val queue = mutableListOf<Int>()
                        try {
                            val arr = JSONArray(json)
                            db.withTransaction {
                                for (i in 0 until arr.length()) {
                                    val pkg = arr.getJSONObject(i)
                                    val pkgId = pkg.optInt("packageid")
                                    val appIdsArr = pkg.optJSONArray("appids")
                                    val appIds =
                                        (0 until (appIdsArr?.length() ?: 0)).map { appIdsArr!!.getInt(it) }
                                    licenseDao.updateApps(pkgId, appIds)
                                    val depotIdsArr = pkg.optJSONArray("depotids")
                                    val depotIds =
                                        (0 until (depotIdsArr?.length() ?: 0)).map { depotIdsArr!!.getInt(it) }
                                    licenseDao.updateDepots(pkgId, depotIds)

                                    // Insert a stub row (or update) of SteamApps to the database.
                                    appIds.forEach { appid ->
                                        val steamApp = appDao.findApp(appid)?.copy(packageId = pkgId)
                                        if (steamApp != null) {
                                            appDao.update(steamApp)
                                        } else {
                                            appDao.insert(SteamApp(id = appid, packageId = pkgId))
                                        }
                                    }
                                    queue.addAll(appIds)
                                }
                            }
                        } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            Timber.w(e, "PICS package batch processing failed")
                        }

                        if (queue.isNotEmpty()) {
                            // App access tokens for the package's apps, then re-queue.
                            val tokens = HashMap<Int, Long>()
                            val tokJson =
                                withWnSession { session ->
                                    withContext(Dispatchers.IO) {
                                        session.getPicsAccessTokens(queue, emptyList())
                                    }
                                }
                            if (tokJson != null) {
                                JSONObject(tokJson).optJSONObject("appTokens")?.let { at ->
                                    for (k in at.keys()) {
                                        tokens[k.toInt()] = at.getString(k).toLongOrNull() ?: 0L
                                    }
                                }
                            }
                            queue
                                .map { PICSRequest(id = it, accessToken = tokens[it] ?: 0L) }
                                .chunked(MAX_PICS_BUFFER)
                                .forEach { chunk ->
                                    Timber.d("bufferedPICSGetProductInfo: Queueing ${chunk.size} for PICS")
                                    appPicsChannel.send(chunk)
                                }
                        }
                    }
            }
        }

    /**
     * Get encrypted app ticket for an app, with 30-minute caching.
     * Returns the serialized protobuf bytes, or null if unavailable.
     */
    suspend fun getEncryptedAppTicket(appId: Int): ByteArray? {
        return try {
            // Check database for existing ticket less than 30 minutes old
            val cachedTicket = encryptedAppTicketDao.getByAppId(appId)
            val now = System.currentTimeMillis()
            val thirtyMinutes = 30 * 60 * 1000L

            if (cachedTicket != null && (now - cachedTicket.timestamp) < thirtyMinutes) {
                Timber.d("Using cached encrypted app ticket protobuf for app $appId")
                return cachedTicket.encryptedTicket
            }

            // Primary path: the C++ WN-Steam-Client (Phase 9 — JavaSteam is
            // being dropped). RequestEncryptedAppTicket returns the serialized
            // EncryptedAppTicket protobuf — exactly what Goldberg's
            // configs.user.ini `ticket=` consumes.
            val wnTicket = withWnSession { session ->
                withContext(Dispatchers.IO) { session.requestEncryptedAppTicket(appId) }
            }
            if (wnTicket != null && wnTicket.isNotEmpty()) {
                runCatching {
                    encryptedAppTicketDao.insert(
                        EncryptedAppTicket(
                            appId = appId,
                            result = EResult.OK.code(),
                            ticketVersionNo = 0,
                            crcEncryptedTicket = 0,
                            cbEncryptedUserData = 0,
                            cbEncryptedAppOwnershipTicket = 0,
                            encryptedTicket = wnTicket,
                            timestamp = now,
                        ),
                    )
                }.onFailure { Timber.w(it, "encrypted app ticket cache insert failed") }
                Timber.i("encrypted app ticket via wn-steam-client: ${wnTicket.size} bytes (app $appId)")
                return wnTicket
            }
            Timber.w("wn-steam-client encrypted app ticket unavailable for app $appId")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting encrypted app ticket for app $appId")
            null
        }
    }

    /**
     * Get encrypted app ticket as base64 encoded string, with 30-minute caching.
     * Returns the base64 encoded ticket, or null if unavailable.
     */
    suspend fun getEncryptedAppTicketBase64(appId: Int): String? {
        val ticket = getEncryptedAppTicket(appId) ?: return null
        return Base64.encodeToString(ticket, Base64.NO_WRAP)
    }
}
