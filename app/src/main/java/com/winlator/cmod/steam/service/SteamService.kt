package com.winlator.cmod.steam.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.widget.Toast
import androidx.room.withTransaction
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.utils.NetworkMonitor
import com.winlator.cmod.PluviaApp
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.data.DepotInfo
import com.winlator.cmod.steam.data.DownloadFailedException
import com.winlator.cmod.steam.enums.DownloadPhase
import com.winlator.cmod.steam.data.DownloadInfo
import com.winlator.cmod.steam.data.GameProcessInfo
import com.winlator.cmod.steam.data.LaunchInfo
import com.winlator.cmod.steam.data.OwnedGames
import com.winlator.cmod.steam.data.PostSyncInfo
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.data.SteamControllerConfigDetail
import com.winlator.cmod.steam.data.SteamFriend
import com.winlator.cmod.steam.data.SteamLicense
import com.winlator.cmod.steam.data.UserFileInfo
import com.winlator.cmod.steam.data.EncryptedAppTicket
import com.winlator.cmod.db.PluviaDatabase
import com.winlator.cmod.steam.db.dao.ChangeNumbersDao
import com.winlator.cmod.steam.db.dao.FileChangeListsDao
import com.winlator.cmod.steam.db.dao.SteamAppDao
import com.winlator.cmod.steam.db.dao.SteamLicenseDao
import com.winlator.cmod.steam.db.dao.CachedLicenseDao
import com.winlator.cmod.steam.enums.LoginResult
import com.winlator.cmod.steam.enums.OS
import com.winlator.cmod.steam.enums.OSArch
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.events.AndroidEvent
import com.winlator.cmod.steam.events.SteamEvent
import com.winlator.cmod.steam.utils.Net
import com.winlator.cmod.steam.utils.SteamUtils
import com.winlator.cmod.steam.utils.MarkerUtils
import com.winlator.cmod.steam.utils.ContainerUtils
import com.winlator.cmod.steam.enums.Marker
import com.winlator.cmod.steam.utils.generateSteamApp
import com.winlator.cmod.utils.NotificationHelper
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.service.DownloadService
import com.winlator.cmod.utils.StorageUtils
import com.winlator.cmod.steam.enums.Language
import com.winlator.cmod.steam.enums.AppType
import com.winlator.cmod.steam.enums.ControllerSupport
import com.winlator.cmod.steam.enums.GameSource
import com.winlator.cmod.core.GPUInformation
import dagger.hilt.android.AndroidEntryPoint
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientObjects.ECloudPendingRemoteOperation
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesFamilygroupsSteamclient
import `in`.dragonbra.javasteam.rpc.service.FamilyGroups
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesAuthSteamclient.CAuthentication_PollAuthSessionStatus_Request
import `in`.dragonbra.javasteam.rpc.service.Authentication
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.Steam3Session
import `in`.dragonbra.javasteam.steam.discovery.FileServerListProvider
import `in`.dragonbra.javasteam.steam.discovery.ServerQuality
import `in`.dragonbra.javasteam.steam.handlers.steamapps.GamePlayedInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStateCallback
import `in`.dragonbra.javasteam.steam.handlers.steamgameserver.SteamGameServer
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.SteamMasterServer
import `in`.dragonbra.javasteam.steam.handlers.steamscreenshots.SteamScreenshots
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamuser.ChatMode
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.SteamWorkshop
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.FileData
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections
import java.util.EnumSet
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.lang.NullPointerException
import com.winlator.cmod.steam.data.AppInfo
import com.winlator.cmod.steam.db.dao.AppInfoDao
import kotlinx.coroutines.ensureActive
import com.winlator.cmod.steam.utils.LicenseSerializer
import com.winlator.cmod.steam.data.CachedLicense
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.PlayingSessionStateCallback
import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import `in`.dragonbra.javasteam.types.DepotManifest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONObject
import android.util.Base64
import com.winlator.cmod.steam.data.DownloadingAppInfo
import com.winlator.cmod.steam.db.dao.DownloadingAppInfoDao
import com.winlator.cmod.steam.db.dao.EncryptedAppTicketDao
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class SteamService : Service(), IChallengeUrlChanged {

    // To view log messages in android logcat properly
    private val logger = object : LogListener {
        override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
            val logMessage = message ?: "No message given"
            Timber.i(throwable, "[${clazz.simpleName}] -> $logMessage")
        }

        override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
            val logMessage = message ?: "No message given"
            Timber.e(throwable, "[${clazz.simpleName}] -> $logMessage")
        }
    }

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

    internal var callbackManager: CallbackManager? = null
    internal var steamClient: SteamClient? = null
    internal val callbackSubscriptions: ArrayList<Closeable> = ArrayList()

    private var _unifiedFriends: SteamUnifiedFriends? = null
    private var _steamUser: SteamUser? = null
    private var _steamApps: SteamApps? = null
    private var _steamFriends: SteamFriends? = null
    private var _steamCloud: SteamCloud? = null
    private var _steamFamilyGroups: FamilyGroups? = null

    private var _loginResult: LoginResult = LoginResult.Failed

    private var licenses: List<License> = emptyList()

    private var retryAttempt = 0

    private val appPicsChannel = Channel<List<PICSRequest>>(
        capacity = 1_000,
        onBufferOverflow = BufferOverflow.SUSPEND,
        onUndeliveredElement = { droppedApps ->
            Timber.w("App PICS Channel dropped: ${droppedApps.size} apps")
        },
    )

    private val packagePicsChannel = Channel<List<PICSRequest>>(
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

    // Connectivity management for Wi-Fi-only downloads
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Add these as class properties
    private var picsGetProductInfoJob: Job? = null
    private var picsChangesCheckerJob: Job? = null
    private var friendCheckerJob: Job? = null

    private val _isPlayingBlocked = MutableStateFlow(false)
    val isPlayingBlocked = _isPlayingBlocked.asStateFlow()

    // Cache in-memory the local persona state.
    private val _localPersona = MutableStateFlow(
        SteamFriend(name = PrefManager.steamUserName, avatarHash = PrefManager.steamUserAvatarHash),
    )
    val localPersona = _localPersona.asStateFlow()

    companion object {
        const val MAX_PICS_BUFFER = 256

        const val MAX_RETRY_ATTEMPTS = 20

        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_PKG_ID: Int = Int.MAX_VALUE
        private const val STEAM_CONTROLLER_CONFIG_FILENAME = "steam_controller_config.vdf"
        private const val DOWNLOAD_INFO_DIR = ".DownloadInfo"
        private const val DOWNLOAD_INFO_FILE = "depot_bytes.json"
        private const val LEGACY_DOWNLOAD_INFO_FILE = "bytes_downloaded.txt"

        /**
         * Default timeout to use when making requests
         */
        var requestTimeout = 30.seconds

        /**
         * Default timeout to use when reading the response body
         */
        var responseTimeout = 120.seconds

        private val PROTOCOL_TYPES = EnumSet.of(ProtocolTypes.WEB_SOCKET)

        internal var instance: SteamService? = null

        val isWifiConnected: Boolean get() = NetworkMonitor.isWifiConnected.value

        /** @return true if download may proceed; false if blocked (notifies user) */
        private fun checkWifiOrNotify(): Boolean {
            if (PrefManager.downloadOnWifiOnly && !isWifiConnected) {
                val svc = instance
                if (svc != null) {
                    svc.notificationHelper.notify(svc.getString(R.string.download_no_wifi))
                } else {
                    Timber.w("checkWifiOrNotify: no SteamService instance to notify")
                }
                return false
            }
            return true
        }

        fun pauseAll() {
            downloadJobs.values.forEach { if (it.isActive()) it.cancel("Paused all") }
            Unit
        }

        fun resumeAll() {
            downloadJobs.forEach { (appId, info) ->
                val status = info.getStatusFlow().value
                if (!info.isActive() && (status == DownloadPhase.QUEUED || status == DownloadPhase.PAUSED || status == DownloadPhase.FAILED)) {
                    downloadApp(appId)
                }
            }
            Unit
        }

        fun cancelAll() {
            downloadJobs.values.toList().forEach { it.cancel("Cancelled all") }
            downloadJobs.clear()
            Unit
        }

        fun checkQueue() {
            val maxParallel = PrefManager.downloadQueueSize
            val activeCount = downloadJobs.values.count { it.isActive() && it.getStatusFlow().value != DownloadPhase.QUEUED }
            
            if (activeCount < maxParallel) {
                val nextInQueue = downloadJobs.entries.find { it.value.getStatusFlow().value == DownloadPhase.QUEUED }
                if (nextInQueue != null) {
                    Timber.i("Starting queued download for appId: ${nextInQueue.key}")
                    downloadApp(nextInQueue.key)
                }
            }
            Unit
        }

        private val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()

        private fun notifyDownloadStarted(appId: Int) {
            PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, true))
        }

        private fun notifyDownloadStopped(appId: Int) {
            PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
        }

        private fun removeDownloadJob(appId: Int, forceRemove: Boolean = false) {
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
            val toRemove = downloadJobs.filterValues { !it.isActive() }.keys
            toRemove.forEach { removeDownloadJob(it, forceRemove = true) }
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

        private fun inferResumeDlcAppIds(appId: Int, appDirPath: String): List<Int> {
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
                val container = if (ContainerUtils.hasContainer(context, "STEAM_${appId}")) {
                    ContainerUtils.getContainer(context, "STEAM_${appId}")
                } else null
                val containerLanguage = container?.language ?: PrefManager.containerLanguage
                val depots = getDownloadableDepots(appId = appId, preferredLanguage = containerLanguage)
                depots.asSequence()
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

        private fun deleteRecursivelyWithRetries(target: File, maxAttempts: Int = 5, delayMs: Long = 250L): Boolean {
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

        fun hasPartialDownload(appId: Int): Boolean {
            if (isAppInstalled(appId)) return false

            val appDirPath = getAppDirPath(appId)
            val downloadingApp = getDownloadingAppInfoOf(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasPartialFiles = hasPartialDownloadFiles(appDirPath)
            val hasPersistedMetadata = hasPersistedDepotResumeMetadata(appDirPath)
            val isResumable = if (hasCompleteMarker) {
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

        data class CloudSyncMessage(val appId: Int, val isUpload: Boolean, val message: String, val progress: Float)
        val cloudSyncStatus = MutableStateFlow<CloudSyncMessage?>(null)

        @Volatile
        var isImporting: Boolean = false

        var isStopping: Boolean = false
            private set
        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow = _isConnectedFlow.asStateFlow()

        var isConnected: Boolean 
            get() {
                val real = (instance?.steamClient?.isConnected == true)
                if (real != _isConnectedFlow.value) _isConnectedFlow.value = real
                return real
            }
            private set(value) { _isConnectedFlow.value = value }

        var isRunning: Boolean = false
            private set
        var isLoggingOut: Boolean = false
            private set

        private val _isLoggedInFlow = MutableStateFlow(false)
        val isLoggedInFlow = _isLoggedInFlow.asStateFlow()

        val isLoggedIn: Boolean
            get() {
                if (isLoggingOut) return false
                val real = (instance?.steamClient?.steamID?.isValid == true)
                if (real != _isLoggedInFlow.value) _isLoggedInFlow.value = real
                return real
            }

        var isWaitingForQRAuth: Boolean = false
            private set

        fun syncStates() {
            val connected = instance?.steamClient?.isConnected == true
            if (connected != _isConnectedFlow.value) _isConnectedFlow.value = connected

            val loggedIn = !isLoggingOut && (instance?.steamClient?.steamID?.isValid == true)
            if (loggedIn != _isLoggedInFlow.value) _isLoggedInFlow.value = loggedIn
        }

        /**
         * Checks if the user has stored Steam credentials (refresh token).
         * Used to determine if auto-reconnection should be attempted on app start.
         */
        fun hasStoredCredentials(context: Context): Boolean {
            PrefManager.init(context)
            return PrefManager.refreshToken.isNotBlank()
        }

        private val serverListPath: String
            get() = Paths.get(DownloadService.baseCacheDirPath, "server_list.bin").pathString

        val internalAppInstallPath: String
            get() = Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "common").pathString

        val externalAppInstallPath: String
            get() = Paths.get(PrefManager.externalStoragePath, "Steam", "steamapps", "common").pathString

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
                    val baseDir = com.winlator.cmod.core.FileUtils.getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
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
                    val baseDir = com.winlator.cmod.core.FileUtils.getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    if (baseDir != null) return Paths.get(baseDir, "staging").pathString
                }

                return if (PrefManager.useExternalStorage) {
                    externalAppStagingPath
                } else {
                    internalAppStagingPath
                }
            }

        val userSteamId: SteamID?
            get() = instance?.steamClient?.steamID

        val familyMembers: List<Int>
            get() = instance?.familyGroupMembers ?: emptyList()

        val isLoginInProgress: Boolean
            get() = instance?._loginResult == LoginResult.InProgress

        suspend fun setPersonaState(state: EPersonaState) = withContext(Dispatchers.IO) {
            PrefManager.personaState = state.code()
            instance?._steamFriends?.setPersonaState(state)
        }

        suspend fun requestUserPersona() = withContext(Dispatchers.IO) {
            // in order to get user avatar url and other info
            userSteamId?.let { instance?._steamFriends?.requestFriendInfo(it) }
        }

        suspend fun getSelfCurrentlyPlayingAppId(): Int? = withContext(Dispatchers.IO) {
            val self = instance?.localPersona?.value ?: return@withContext null
            if (self.isPlayingGame) self.gameAppID else null
        }

        suspend fun kickPlayingSession(onlyGame: Boolean = true): Boolean = withContext(Dispatchers.IO) {
            val user = instance?._steamUser ?: return@withContext false
            try {
                instance?._isPlayingBlocked?.value = true
                user.kickPlayingSession(onlyStopGame = onlyGame)

                // Wait for PlayingSessionStateCallback to indicate unblocked
                val deadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < deadline) {
                    if (instance?._isPlayingBlocked?.value == false) return@withContext true
                    delay(100)
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Get licenses from database for use with DepotDownloader
         */
        suspend fun getLicensesFromDb(): List<License> = withContext(Dispatchers.IO) {
            val cached = instance?.cachedLicenseDao?.getAll() ?: return@withContext emptyList()
            cached.mapNotNull { cachedLicense ->
                LicenseSerializer.deserializeLicense(cachedLicense.licenseJson)
            }
        }

        fun getPkgInfoOf(appId: Int): SteamLicense? {
            return runBlocking(Dispatchers.IO) {
                instance?.licenseDao?.findLicense(
                    instance?.appDao?.findApp(appId)?.packageId ?: INVALID_PKG_ID,
                )
            }
        }

        fun getAppInfoOf(appId: Int): SteamApp? {
            return runBlocking(Dispatchers.IO) { instance?.appDao?.findApp(appId) }
        }

        fun getDownloadingAppInfoOf(appId: Int): DownloadingAppInfo? {
            return runBlocking(Dispatchers.IO) { instance?.downloadingAppInfoDao?.getDownloadingApp(appId) }
        }

        fun getDownloadableDlcAppsOf(appId: Int): List<SteamApp>? {
            return runBlocking(Dispatchers.IO) { instance?.appDao?.findDownloadableDLCApps(appId) }
        }

        fun getHiddenDlcAppsOf(appId: Int): List<SteamApp>? {
            return runBlocking(Dispatchers.IO) { instance?.appDao?.findHiddenDLCApps(appId) }
        }

        fun getInstalledApp(appId: Int): AppInfo? {
            return runBlocking(Dispatchers.IO) { instance?.appInfoDao?.getInstalledApp(appId) }
        }

        fun getInstalledDepotsOf(appId: Int): List<Int>? {
            return getInstalledApp(appId)?.downloadedDepots
        }

        fun getInstalledDlcDepotsOf(appId: Int): List<Int>? {
            return getInstalledApp(appId)?.dlcDepots
        }

        private fun tryRecoverInstalledAppInfo(appId: Int): AppInfo? {
            val dirPath = getAppDirPath(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasInProgressMarker = MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            if (!hasCompleteMarker || hasInProgressMarker) return null

            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) return null

            val downloadedDepotIds = runCatching { getMainAppDepots(appId).keys.sorted() }.getOrDefault(emptyList())
            val recovered = AppInfo(
                id = appId,
                isDownloaded = true,
                downloadedDepots = downloadedDepotIds,
                dlcDepots = emptyList(),
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
                val apps = runCatching { db.steamAppDao().getAllAsList() }.getOrElse {
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

        fun getAllDownloads(): Map<Int, DownloadInfo> {
            return downloadJobs
        }

        fun getAppDownloadInfo(appId: Int): DownloadInfo? {
            return downloadJobs[appId]
        }

        fun isAppInstalled(appId: Int): Boolean {
            val appInfo = getInstalledApp(appId) ?: tryRecoverInstalledAppInfo(appId)
            if (appInfo?.isDownloaded != true) return false
            val dirPath = getAppDirPath(appId)
            return MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
        }

        fun uninstallApp(appId: Int, onComplete: (Boolean) -> Unit = {}) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val appInfo = getInstalledApp(appId)
                    if (appInfo != null) {
                        instance?.appInfoDao?.update(appInfo.copy(isDownloaded = false))
                    }
                    val dirPath = getAppDirPath(appId)
                    val dirFile = java.io.File(dirPath)
                    if (dirFile.exists() && dirFile.isDirectory) {
                        deleteRecursivelyWithRetries(dirFile)
                    }
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

        fun getAppDlc(appId: Int): Map<Int, DepotInfo> {
            return getAppInfoOf(appId)?.let {
                it.depots.filter { it.value.dlcAppId != INVALID_APP_ID }
            }.orEmpty()
        }

        suspend fun getOwnedAppDlc(appId: Int): Map<Int, DepotInfo> {
            val client = instance?.steamClient ?: return emptyMap()
            val accountId = client.steamID?.accountID?.toInt() ?: return emptyMap()
            val ownedGameIds = getOwnedGames(userSteamId!!.convertToUInt64()).map { it.appId }.toHashSet()


            return getAppDlc(appId).filter { (_, depot) ->
                when {
                    /* Base-game depots always download */
                    depot.dlcAppId == INVALID_APP_ID -> true

                    /* ① licence cache */
                    instance?.licenseDao?.findLicense(depot.dlcAppId) != null -> true

                    /* ② PICS row */
                    instance?.appDao?.findApp(depot.dlcAppId) != null -> true

                    /* ③ owned-games list */
                    depot.dlcAppId in ownedGameIds -> true

                    /* ④ final online / cached call */
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
                val checkingAppDlcIds = appInfo.depots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct()
                checkingAppDlcIds.forEach { checkingDlcId ->
                    val checkMap = appInfo.depots.filter { it.value.dlcAppId == checkingDlcId }
                    if (checkMap.size == 1) {
                        val depotInfo = checkMap[checkMap.keys.first()]!!
                        if (depotInfo.osList.contains(OS.none) &&
                            depotInfo.manifests.isEmpty() &&
                            hiddenDlcAppIds.isNotEmpty() && hiddenDlcAppIds.contains(checkingDlcId)) {
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
        suspend fun refreshOwnedGamesFromServer(): Int = withContext(Dispatchers.IO) {
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
        fun filterForDownloadableDepots(depot: DepotInfo, has64Bit: Boolean, preferredLanguage: String, ownedDlc: Map<Int, DepotInfo>?): Boolean {
            if (depot.manifests.isEmpty() && depot.encryptedManifests.isNotEmpty())
                return false
            // 1. Has something to download
            if (depot.manifests.isEmpty() && !depot.sharedInstall)
                return false
            // 2. Supported OS
            if (!(depot.osList.contains(OS.windows) ||
                        (!depot.osList.contains(OS.linux) && !depot.osList.contains(OS.macos)))
            )
                return false
            // 3. 64-bit or indeterminate
            // Arch selection: allow 64-bit and Unknown always.
            // Allow 32-bit only when no 64-bit depot exists.
            val archOk = when (depot.osArch) {
                OSArch.Arch64, OSArch.Unknown -> true
                OSArch.Arch32 -> !has64Bit
                else -> false
            }
            if (!archOk) return false
            // 4. DLC you actually own
            if (depot.dlcAppId != INVALID_APP_ID && ownedDlc != null && !ownedDlc.containsKey(depot.depotId))
                return false
            // 5. Language filter - if depot has language, it must match preferred language
            if (depot.language.isNotEmpty() && !depot.language.equals(preferredLanguage, ignoreCase = true))
                return false

            return true
        }

        fun getMainAppDepots(appId: Int): Map<Int, DepotInfo> {
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
            val preferredLanguage = PrefManager.containerLanguage

            // If the game ships any 64-bit depot for Windows, prefer those and ignore x86 ones
            val has64Bit = appInfo.depots.values.any { 
                it.osArch == OSArch.Arch64 && (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
            }

            return appInfo.depots.asSequence()
                .filter { (depotId, depot) ->
                    return@filter filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc)
                }
                .associate { it.toPair() }
        }

        /**
         * Get downloadable depots for a given app, including all DLCs
         * @return Map of app ID to depot ID to depot info
         */
        fun getDownloadableDepots(appId: Int, preferredLanguage: String = PrefManager.containerLanguage): Map<Int, DepotInfo> {
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val ownedDlc = runBlocking { getOwnedAppDlc(appId) }

            // If the game ships any 64-bit depot for Windows, prefer those and ignore x86 ones
            val has64Bit = appInfo.depots.values.any { 
                it.osArch == OSArch.Arch64 && (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
            }

            val map = mutableMapOf<Int, DepotInfo>()
            for ((depotId, depot) in appInfo.depots) {
                if (filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc)) {
                    map[depotId] = depot
                }
            }

            val indirectDlcApps = getDownloadableDlcAppsOf(appId).orEmpty()
            for (dlcApp in indirectDlcApps) {
                for ((depotId, depot) in dlcApp.depots) {
                    if (filterForDownloadableDepots(depot, has64Bit, preferredLanguage, null)) {
                        // Add DLC Depots with custom object
                        map[depotId] = DepotInfo(
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

        fun getAppDirName(app: SteamApp?): String {
            // The folder name, if it got made
            var appName = app?.config?.installDir.orEmpty()
            if (appName.isEmpty()) {
                appName = app?.name.orEmpty()
            }
            return appName
        }

        fun getAppDirPath(gameId: Int): String {

            val info = getAppInfoOf(gameId)

            // Check custom install directory first (only if it's a full absolute path)
            // installDir from PICS metadata is just a folder name, custom installs save full path
            val customDir = info?.installDir.orEmpty()
            if (customDir.isNotEmpty() && (customDir.startsWith("/") || customDir.contains(File.separator))) {
                // It's a full path (custom install location)
                return customDir
            }

            val appName = getAppDirName(info)
            val oldName = info?.name.orEmpty()

            // Respect user-selected default download folder
            val context = PluviaApp.instance.applicationContext
            if (context != null) {
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir = com.winlator.cmod.core.FileUtils.getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    Timber.i("getAppDirPath: resolved baseDir $baseDir from URI $storeDefaultUri")
                    if (baseDir != null) {
                        val path = Paths.get(baseDir, appName)
                        if (Files.exists(path)) {
                            Timber.i("getAppDirPath: found existing path $path")
                            return path.pathString
                        }
                        if (oldName.isNotEmpty()) {
                            val oldPath = Paths.get(baseDir, oldName)
                            if (Files.exists(oldPath)) {
                                Timber.i("getAppDirPath: found existing oldPath $oldPath")
                                return oldPath.pathString
                            }
                        }
                        // If it doesn't exist yet, this is where we'll install it
                        Timber.i("getAppDirPath: returning new path $path")
                        return path.pathString
                    }
                }
            }

            // Internal first (legacy installs), external second
            val internalPath = Paths.get(internalAppInstallPath, appName)
            if (Files.exists(internalPath)) return internalPath.pathString
            val internalOld = Paths.get(internalAppInstallPath, oldName)
            if (oldName.isNotEmpty() && Files.exists(internalOld)) return internalOld.pathString

            val externalPath = Paths.get(externalAppInstallPath, appName)
            if (Files.exists(externalPath)) return externalPath.pathString
            val externalOld = Paths.get(externalAppInstallPath, oldName)
            if (oldName.isNotEmpty() && Files.exists(externalOld)) return externalOld.pathString

            // Nothing on disk yet – default to whatever location you want new installs to use
            if (PrefManager.useExternalStorage) {
                return externalPath.pathString
            }
            return internalPath.pathString
        }

        private fun createSteamShortcut(context: Context, appId: Int) {
            try {
                val container = ContainerUtils.getOrCreateContainer(context, "STEAM_$appId")
                val appInfo = getAppInfoOf(appId) ?: return
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

                com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString())
                Timber.i("Created Steam shortcut for ${appInfo.name} in container ${container.id}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to create Steam shortcut for appId $appId")
            }
        }

        private fun isExecutable(flags: Any): Boolean = when (flags) {
            // SteamKit-JVM (most forks) – flags is EnumSet<EDepotFileFlag>
            is EnumSet<*> -> {
                flags.contains(EDepotFileFlag.Executable) ||
                        flags.contains(EDepotFileFlag.CustomExecutable)
            }

            // SteamKit-C# protobuf port – flags is UInt / Int / Long
            is Int -> (flags and 0x20) != 0 || (flags and 0x80) != 0
            is Long -> ((flags and 0x20L) != 0L) || ((flags and 0x80L) != 0L)

            else -> false
        }

        /* -------------------------------------------------------------------------- */
        /* 1. Extra patterns & word lists                                             */
        /* -------------------------------------------------------------------------- */

        // Unreal Engine "Shipping" binaries (e.g. Stray-Win64-Shipping.exe)
        private val UE_SHIPPING = Regex(
            """.*-win(32|64)(-shipping)?\.exe$""",
            RegexOption.IGNORE_CASE,
        )

        // UE folder hint …/Binaries/Win32|64/…
        private val UE_BINARIES = Regex(
            """.*/binaries/win(32|64)/.*\.exe$""",
            RegexOption.IGNORE_CASE,
        )

        // Tools / crash-dumpers to push down
        private val NEGATIVE_KEYWORDS = listOf(
            "crash", "handler", "viewer", "compiler", "tool",
            "setup", "unins", "eac", "launcher", "steam",
        )

        /* add near-name helper */
        private fun fuzzyMatch(a: String, b: String): Boolean {
            /* strip digits & punctuation, compare first 5 letters */
            val cleanA = a.replace(Regex("[^a-z]"), "")
            val cleanB = b.replace(Regex("[^a-z]"), "")
            return cleanA.take(5) == cleanB.take(5)
        }

        /* add generic short-name detector: one letter + digits, ≤4 chars  */
        private val GENERIC_NAME = Regex("^[a-z]\\d{1,3}\\.exe$", RegexOption.IGNORE_CASE)

        /* -------------------------------------------------------------------------- */
        /* 2. Heuristic score (same signature!)                                       */
        /* -------------------------------------------------------------------------- */

        private fun scoreExe(
            file: FileData,
            gameName: String,
            hasExeFlag: Boolean,
        ): Int {
            var s = 0
            val path = file.fileName.lowercase()

            // 1️⃣ UE shipping or binaries folder bonus
            if (UE_SHIPPING.matches(path)) s += 300
            if (UE_BINARIES.containsMatchIn(path)) s += 250

            // 2️⃣ root-folder exe bonus
            if (!path.contains('/')) s += 200

            // 3️⃣ filename contains the game / installDir
            if (path.contains(gameName) || fuzzyMatch(path, gameName)) s += 100

            // 4️⃣ obvious tool / crash-dumper penalty
            if (NEGATIVE_KEYWORDS.any { it in path }) s -= 150
            if (GENERIC_NAME.matches(file.fileName)) s -= 200   // ← new

            // 5️⃣ Executable | CustomExecutable flag
            if (hasExeFlag) s += 50

            Timber.i("Score for $path: $s")

            return s
        }

        fun FileData.isStub(): Boolean {
            /* stub detector (same short rules) */
            val generic = Regex("^[a-z]\\d{1,3}\\.exe$", RegexOption.IGNORE_CASE)
            val bad = listOf("launcher", "steam", "crash", "handler", "setup", "unins", "eac")
            val n = fileName.lowercase()
            val stub = generic.matches(n) || bad.any { it in n } || totalSize < 1_000_000
            if (stub) Timber.d("Stub filtered: $fileName  size=$totalSize")
            return stub
        }

        /** select the primary binary */
        fun choosePrimaryExe(
            files: List<FileData>?,
            gameName: String,
        ): FileData? = files?.maxWithOrNull { a, b ->
            val sa = scoreExe(a, gameName, isExecutable(a.flags))   // <- fixed
            val sb = scoreExe(b, gameName, isExecutable(b.flags))

            when {
                sa != sb -> sa - sb                                 // higher score wins
                else -> (a.totalSize - b.totalSize).toInt()     // tie-break on size
            }
        }

        /**
         * Picks the real shipped EXE for a Steam app.
         *
         * ❶ try the dev-supplied launch entry (skip obvious stubs)
         * ❷ else score all manifest-flagged EXEs and keep the best
         * ❸ else fall back to the largest flagged EXE in the biggest depot
         * If everything fails, return the game's install directory.
         */
        fun getInstalledExe(appId: Int): String {
            val appInfo = getAppInfoOf(appId) ?: return ""

            val installDir = appInfo.config.installDir.ifEmpty { appInfo.name }

            val depots = appInfo.depots.values.filter { d ->
                !d.sharedInstall && (d.osList.isEmpty() ||
                        d.osList.any { it.name.equals("windows", true) || it.name.equals("none", true) })
            }
            Timber.i("Depots considered: $depots")

            /* launch targets (lower-case) */
            val launchTargets = appInfo.config.launch
                .mapNotNull { it.executable.lowercase() }.toSet() ?: emptySet()

            Timber.i("Launch targets from appinfo: $launchTargets")

            /* ---------------------------------------------------------- */
            val flagged = mutableListOf<Pair<FileData, Long>>()   // (file, depotSize)
            var largestDepotSize = 0L

            // Use DepotDownloader to fetch manifests
            val steamClient = instance?.steamClient
            val licenses = runBlocking { getLicensesFromDb() }
            if (steamClient == null || licenses.isEmpty()) {
                Timber.w("Cannot fetch manifests: steamClient or licenses not available")
                // Fallback to last resort
                return (getAppInfoOf(appId)?.let { appInfo ->
                    getWindowsLaunchInfos(appId).firstOrNull()
                })?.executable ?: ""
            }

            for (depot in depots) {
                val mi = depot.manifests["public"] ?: continue
                if (mi.size > largestDepotSize) largestDepotSize = mi.size

                // Check cache first
                val man = DepotManifest.loadFromFile("${getAppDirPath(appId)}/.DepotDownloader/${depot.depotId}_${mi.gid}.manifest")

                Timber.d("Using manifest for depot ${depot.depotId}  size=${mi.size}")

                /* 1️⃣ exact launch entry that isn't a stub */
                man?.files?.firstOrNull { f ->
                    f.fileName.lowercase() in launchTargets && !f.isStub()
                }?.let {
                    Timber.i("Picked via launch entry: ${it.fileName}")
                    return it.fileName.replace('\\', '/').toString()
                }

                /* collect for later */
                man?.files?.filter { isExecutable(it.flags) || it.fileName.endsWith(".exe", true) }
                    ?.forEach { flagged += it to mi.size }
            }

            Timber.i("Flagged executable candidates: ${flagged.map { it.first.fileName }}")

            /* 2️⃣ scorer (unchanged) */
            choosePrimaryExe(
                flagged
                    .map { it.first }
                    .let { pool ->
                        val noStubs = pool.filterNot { it.isStub() }
                        if (noStubs.isNotEmpty()) noStubs else pool
                    },
                installDir.lowercase(),
            )?.let {
                Timber.i("Picked via scorer: ${it.fileName}")
                return it.fileName.replace('\\', '/')
            }

            /* 3️⃣ fallback: biggest exe from the biggest depot */
            flagged
                .filter { it.second == largestDepotSize }
                .maxByOrNull { it.first.totalSize }
                ?.let {
                    Timber.i("Picked via largest-depot fallback: ${it.first.fileName}")
                    return it.first.fileName.replace('\\', '/').toString()
                }

            /* 4️⃣ last resort */
            Timber.w("No executable found; falling back to install dir")
            return (getAppInfoOf(appId)?.let { appInfo ->
                getWindowsLaunchInfos(appId).firstOrNull()
            })?.executable ?: ""
        }

        suspend fun deleteApp(appId: Int): Boolean = withContext(Dispatchers.IO) {
            val appDirPath = getAppDirPath(appId)
            val isUnsafeDeleteTarget = appDirPath == internalAppInstallPath || appDirPath == externalAppInstallPath

            // Guard against accidental root deletion if path resolution failed.
            if (isUnsafeDeleteTarget) {
                Timber.e("Refusing to delete appId=$appId because resolved path points to install root: $appDirPath")
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
            if (!isUnsafeDeleteTarget) {
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                clearPersistedProgressSnapshot(appDirPath)
            }

            // Also delete staging and shadercache folders if they exist
            val stagingPath = Paths.get(defaultAppStagingPath, appId.toString()).pathString
            val stagingDir = File(stagingPath)
            if (stagingDir.exists()) {
                Timber.i("Deleting staging folder for appId $appId: $stagingPath")
                deleteRecursivelyWithRetries(stagingDir)
            }

            val shaderCachePath = Paths.get(defaultStoragePath, "Steam", "steamapps", "shadercache", appId.toString()).pathString
            val shaderCacheDir = File(shaderCachePath)
            if (shaderCacheDir.exists()) {
                Timber.i("Deleting shadercache folder for appId $appId: $shaderCachePath")
                deleteRecursivelyWithRetries(shaderCacheDir)
            }

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

        fun setCustomInstallPath(appId: Int, customInstallPath: String): String {
            val appInfo = getAppInfoOf(appId)
            val folderName = getAppDirName(appInfo)
            val safeFolderName = if (folderName.isNotEmpty()) folderName else appId.toString()

            val customFile = File(customInstallPath)
            val finalPath = if (customFile.name.equals(safeFolderName, ignoreCase = true)) {
                // User selected the game folder itself
                customFile.absolutePath
            } else {
                // User selected parent folder, create/use subfolder
                File(customInstallPath, safeFolderName).absolutePath
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

        fun downloadApp(appId: Int): DownloadInfo? {
            val currentDownloadInfo = downloadJobs[appId]
            if (currentDownloadInfo != null) {
                if (!currentDownloadInfo.isActive()) {
                    removeDownloadJob(appId)
                } else {
                    return downloadApp(appId, currentDownloadInfo.downloadingAppIds, isUpdateOrVerify = false)
                }
            }

            val downloadingAppInfo = getDownloadingAppInfoOf(appId)
            val appDirPath = getAppDirPath(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasPartialFiles = hasPartialDownloadFiles(appDirPath)
            val hasPersistedMetadata = hasPersistedDepotResumeMetadata(appDirPath)
            val hasResumablePayload = if (hasCompleteMarker) {
                downloadingAppInfo != null || hasPersistedMetadata
            } else {
                hasPartialFiles
            }
            if (hasResumablePayload) {
                // Resume persisted progress whenever partial files exist, even if the
                // DownloadingAppInfo row is missing (can happen after cancellation races).
                val resumeDlcAppIds = downloadingAppInfo?.dlcAppIds
                    ?: run {
                        val inferred = inferResumeDlcAppIds(appId, appDirPath)
                        if (inferred.isNotEmpty()) {
                            inferred
                        } else {
                            resolveInstalledDlcIdsForUpdateOrVerify(appId)
                        }
                    }
                return downloadApp(
                    appId = appId,
                    dlcAppIds = resumeDlcAppIds,
                    includeInstalledDepots = false,
                    enableVerify = false,
                    allowPersistedProgress = true,
                    hasPersistedResumeRow = downloadingAppInfo != null,
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

        fun downloadAppForUpdate(appId: Int): DownloadInfo? {
            return downloadApp(
                appId,
                resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = true,
                enableVerify = false,
                allowPersistedProgress = false,
            )
        }

        fun downloadAppForVerify(appId: Int): DownloadInfo? {
            return downloadApp(
                appId,
                resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = true,
                enableVerify = true,
                allowPersistedProgress = false,
            )
        }

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

        fun downloadApp(appId: Int, dlcAppIds: List<Int>, isUpdateOrVerify: Boolean, customInstallPath: String? = null): DownloadInfo? {
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
        ): DownloadInfo? {
            if (!checkWifiOrNotify()) {
                Timber.w("Download aborted: Wi-Fi only enabled but not connected to Wi-Fi")
                return null
            }
            val appInfo = getAppInfoOf(appId)
            if (appInfo == null) {
                Timber.e("Download aborted: Could not find AppInfo for appId: $appId")
                return null
            }

            val downloadableDepots = getDownloadableDepots(appId)
            if (downloadableDepots.isEmpty()) {
                Timber.w("Download aborted: No downloadable depots found for appId: $appId")
                instance?.let { service ->
                    service.scope.launch(Dispatchers.Main) {
                        Toast.makeText(service.applicationContext, "No downloadable content found for this game", Toast.LENGTH_LONG).show()
                    }
                }
                return null
            }

            // Delegate to the full depot-level downloadApp overload
            return downloadApp(
                appId = appId,
                downloadableDepots = downloadableDepots,
                userSelectedDlcAppIds = dlcAppIds,
                branch = "public",
                includeInstalledDepots = includeInstalledDepots,
                enableVerify = enableVerify,
                allowPersistedProgress = allowPersistedProgress,
                hasPersistedResumeRow = hasPersistedResumeRow,
                customInstallPath = customInstallPath,
            )
        }

        fun isImageFsInstalled(context: Context): Boolean {
            return ImageFs.find(context).isValid()
        }

        fun isImageFsInstallable(context: Context, variant: String): Boolean {
            if (variant.equals("BIONIC")) {
                return File(context.filesDir, "imagefs_bionic.txz").exists() || context.assets.list("")
                    ?.contains("imagefs_bionic.txz") == true
            } else {
                return File(context.filesDir, "imagefs_gamenative.txz").exists() || context.assets.list("")
                    ?.contains("imagefs_gamenative.txz") == true
            }
        }

        fun isSteamInstallable(context: Context): Boolean {
            return File(context.filesDir, "steam.tzst").exists()
        }

        fun isFileInstallable(context: Context, filename: String): Boolean {
            return File(context.filesDir, filename).exists()
        }

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
            val primaryUrl = "https://downloads.gamenative.app/$fileName"
            val fallbackUrl = "https://pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev/$fileName"
            try {
                fetchFile(primaryUrl, dest, onProgress)
            } catch (e: Exception) {
                Timber.w(e, "Primary download failed; retrying with fallback URL")
                try {
                    fetchFile(fallbackUrl, dest, onProgress)
                } catch (e2: Exception) {
                    withContext(Dispatchers.Main) {
                        val msg = "Download failed with ${e2.message ?: e2.toString()}. Please disable VPN or try a different network."
                        android.widget.Toast.makeText(context.applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
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
                Timber.d("Downloading imagefs_bionic to " + dest.toString());
                fetchFileWithFallback("imagefs_bionic.txz", dest, context, onDownloadProgress)
            } else {
                Timber.d("Downloading imagefs_gamenative to " + File(context.filesDir, "imagefs_gamenative.txz"));
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
            Timber.d("Downloading imagefs_patches_gamenative.tzst to " + dest.toString());
            fetchFileWithFallback("imagefs_patches_gamenative.tzst", dest, context, onDownloadProgress)
        }

        fun downloadFile(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
            fileName: String
        ) = parentScope.async {
            Timber.i("${fileName} will be downloaded")
            val dest = File(context.filesDir, fileName)
            Timber.d("Downloading ${fileName} to " + dest.toString());
            fetchFileWithFallback(fileName, dest, context, onDownloadProgress)
        }

        fun downloadSteam(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            val dest = File(context.filesDir, "steam.tzst")
            Timber.d("Downloading steam.tzst to " + dest.toString());
            fetchFileWithFallback("steam.tzst", dest, context, onDownloadProgress)
        }

        private fun selectSteamControllerConfig(
            details: List<SteamControllerConfigDetail>,
        ): SteamControllerConfigDetail? {
            if (details.isEmpty()) return null

            val branchPriority = listOf("default", "public")
            val controllerPriority = listOf(
                "controller_xbox360",
                "controller_xboxone",
                "controller_steamcontroller_gordon",
            )

            for (branch in branchPriority) {
                for (controllerType in controllerPriority) {
                    val match = details.firstOrNull { detail ->
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
            val manifestPath = getAppInfoOf(appId)
                ?.config
                ?.steamInputManifestPath
                ?.trim()
                .orEmpty()
            if (manifestPath.isEmpty()) return null

            return resolvePathCaseInsensitive(appDirPath, manifestPath)
        }

        private fun loadConfigFromManifest(
            manifestFile: File,
        ): String? {
            if (!manifestFile.exists()) return null
            val manifestDirPath = manifestFile.parentFile?.path ?: return null

            val manifestText = manifestFile.readText(Charsets.UTF_8)
            val configText = try {
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
                val actionManifest = if (kv.name?.equals("Action Manifest", ignoreCase = true) == true) {
                    kv
                } else {
                    kv["Action Manifest"]
                }
                if (actionManifest === KeyValue.INVALID) return null

                val configs = actionManifest["configurations"]
                if (configs === KeyValue.INVALID || configs.children.isEmpty()) {
                    throw IllegalStateException("No configurations found in Action Manifest")
                }

                val preferredControllers = listOf(
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

                        val configFile = resolvePathCaseInsensitive(manifestDirPath, configPath)
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
            val directFile = File(baseDirPath, relativePath)
            if (directFile.exists()) return directFile

            var currentDir = File(baseDirPath)
            if (!currentDir.exists() || !currentDir.isDirectory) return null

            val segments = relativePath.split('/', '\\').filter { it.isNotEmpty() }
            for ((index, segment) in segments.withIndex()) {
                val entries = currentDir.listFiles() ?: return null
                val matched = entries.firstOrNull {
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
                1 -> readDownloadedSteamInputTemplate(appId)
                13 -> {
                    val manifestFile = resolveSteamInputManifestFile(appId, getAppDirPath(appId))
                        ?: return null
                    loadConfigFromManifest(manifestFile)
                }
                2, 12 -> readBuiltInSteamInputTemplate("controller_xboxone_gamepad_fps.vdf")
                6 -> readBuiltInSteamInputTemplate("controller_xboxone_wasd.vdf")
                4, 5 -> readBuiltInSteamInputTemplate("gamepad_joystick.vdf")
                else -> readBuiltInSteamInputTemplate("gamepad+mouse.vdf")
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
        ): DownloadInfo? {
            var appDirPath = getAppDirPath(appId)
            Timber.i("downloadApp called for appId: $appId, customInstallPath: $customInstallPath")

            if (customInstallPath != null) {
                // Determine if customInstallPath is the game folder itself or the parent
                val appInfo = getAppInfoOf(appId)
                val folderName = getAppDirName(appInfo)
                val safeFolderName = if (folderName.isNotEmpty()) folderName else appId.toString()
                
                val customFile = File(customInstallPath)
                val finalPath = if (customFile.name.equals(safeFolderName, ignoreCase = true)) {
                    // User selected the game folder itself
                    customFile.absolutePath
                } else {
                    // User selected parent folder, create/use subfolder
                    File(customInstallPath, safeFolderName).absolutePath
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

            if (!checkWifiOrNotify()) {
                Timber.w("Download aborted: Wi-Fi only enabled but not connected to Wi-Fi")
                return null
            }

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
                                Toast.makeText(service.applicationContext, "Failed to create download directory. Check permissions.", Toast.LENGTH_LONG).show()
                            }
                        }
                        return null
                    }
                }
                
                // Add in-progress marker
                if (!MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) {
                    Timber.e("Failed to add DOWNLOAD_IN_PROGRESS_MARKER at $appDirPath")
                }
                
                // If this is not an update/verify, remove the complete marker to reset state
                if (!includeInstalledDepots) {
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
            Timber.d("Main app depots count: ${mainDepots.size}")
            val originalMainAppDepots = mainDepots.filter { (_, depot) ->
                depot.dlcAppId == INVALID_APP_ID
            } + mainDepots.filter { (_, depot) ->
                userSelectedDlcAppIds.contains(depot.dlcAppId) && depot.manifests.isNotEmpty()
            }
            var mainAppDepots = originalMainAppDepots
            Timber.d("Filtered main app depots count: ${mainAppDepots.size}")

            // Depots from DLC App
            val dlcAppDepots = downloadableDepots.filter { (_, depot) ->
                !mainAppDepots.map { it.key }.contains(depot.depotId) &&
                userSelectedDlcAppIds.contains(depot.dlcAppId) && indirectDlcAppIds.contains(depot.dlcAppId) && depot.manifests.isNotEmpty()
            }
            Timber.d("Filtered DLC app depots count: ${dlcAppDepots.size}")

            // Remove depots that are already downloaded only when install metadata is trusted.
            // But if a custom path is provided, we want to check/download everything at the new location
            var installedApp = getInstalledApp(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            var hasTrustedInstalledState = installedApp?.isDownloaded == true && hasCompleteMarker
            if (!includeInstalledDepots && installedApp != null && !hasTrustedInstalledState && customInstallPath == null) {
                val hasStaleInstallMetadata = installedApp.isDownloaded ||
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

            val allDepots = originalMainAppDepots + dlcAppDepots
            // Use install (uncompressed) size for progress tracking
            val depotSizeById = allDepots.mapValues { (_, depot) ->
                val mInfo = depot.manifests[branch] ?: depot.encryptedManifests[branch]
                (mInfo?.size ?: 1L).coerceAtLeast(1L)
            }

            // Load persisted progress snapshot to skip fully downloaded depots
            val persistedDepotBytes = if (allowPersistedProgress) {
                DownloadInfo.loadPersistedDepotBytes(appDirPath)
            } else {
                emptyMap()
            }

            val fullyDownloadedDepotsFromSnapshot = mutableSetOf<Int>()
            if (persistedDepotBytes.isNotEmpty()) {
                for ((depotId, _) in allDepots) {
                    val depotSize = depotSizeById[depotId] ?: 1L
                    val downloadedBytes = persistedDepotBytes[depotId] ?: 0L
                    if (downloadedBytes >= depotSize) {
                        fullyDownloadedDepotsFromSnapshot.add(depotId)
                    }
                }
                if (fullyDownloadedDepotsFromSnapshot.isNotEmpty()) {
                    Timber.i("Skipping ${fullyDownloadedDepotsFromSnapshot.size} fully downloaded depots from snapshot")
                    mainAppDepots = mainAppDepots.filter { it.key !in fullyDownloadedDepotsFromSnapshot }
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
                    Timber.i("selectedDepots is empty before snapshot filtering - App already installed.")
                    
                    // Instead of returning null, create a completed/verifying job so it shows in UI
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
                    
                    // Show success message to user
                    instance?.let { service ->
                        service.scope.launch(Dispatchers.Main) {
                            Toast.makeText(service.applicationContext, "Download complete", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    return info
                }

                // Snapshot says all depots are complete but marker is missing.
                // Finalize metadata/markers directly instead of re-queuing depots.
                val canFinalizeFromSnapshot = allowPersistedProgress &&
                    fullyDownloadedDepotsFromSnapshot.isNotEmpty() &&
                    (hasCompleteMarker || hasPersistedResumeRow)
                if (canFinalizeFromSnapshot) {
                    Timber.i("All resume depots appear complete from snapshot; finalizing without downloader")
                    val info = finalizeSnapshotResumeAsComplete(
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
            val allDepotIdsByDlcAppId = dlcAppDepots.values
                .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                .mapValues { (_, depotIds) -> depotIds.sorted() }
            val selectedDepotIdsByDlcAppId = selectedDepots.values
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

            // There are some apps, the dlc depots does not have dlcAppId in the data, need to set it back
            val mainAppDlcIds = getMainAppDlcIdsWithoutProperDepotDlcIds(appId)

            // If there are no DLC depots, download the main app only
            if (dlcAppDepots.isEmpty()) {
                // Because all dlcIDs are coming from main depots, need to add the dlcID to main app in order to save it to db after finish download
                mainAppDlcIds.addAll(mainAppDepots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct())

                // Refresh id List, so only main app is downloaded
                calculatedDlcAppIds.clear()
                downloadingAppIds.clear()
                downloadingAppIds.add(appId)
            }

            Timber.i("Starting download for $appId")
            Timber.i("App contains ${mainAppDepots.size} depot(s): ${mainAppDepots.keys}")
            Timber.i("DLC contains ${dlcAppDepots.size} depot(s): ${dlcAppDepots.keys}")
            Timber.i("downloadingAppIds: $downloadingAppIds")

            val service = instance ?: run {
                Timber.e("SteamService instance is null, cannot start download job.")
                return null
            }

            // Save downloading app info
            runBlocking {
                service.downloadingAppInfoDao.insert(
                    DownloadingAppInfo(
                        appId,
                        dlcAppIds = userSelectedDlcAppIds
                    ),
                )
                Unit
            }

            val maxParallel = PrefManager.downloadQueueSize
            val activeCount = downloadJobs.values.count { it.isActive() && it.getStatusFlow().value != DownloadPhase.QUEUED }

            if (activeCount >= maxParallel) {
                Timber.i("Download limit reached ($maxParallel), queuing appId: $appId")
                val info = DownloadInfo(selectedDepots.size, appId, downloadingAppIds).also { di ->
                    di.setPersistencePath(appDirPath)
                    di.updateStatus(DownloadPhase.QUEUED, "Queued...")
                    di.setActive(false)
                }
                downloadJobs[appId] = info
                notifyDownloadStarted(appId)
                return info
            }

            val info = DownloadInfo(selectedDepots.size, appId, downloadingAppIds).also { di ->
                di.setPersistencePath(appDirPath)

                // Set weights for each depot based on manifest sizes
                val selectedDepotSizes = selectedDepots.mapValues { (depotId, _) ->
                    depotSizeById[depotId] ?: 1L
                }
                selectedDepots.keys.forEachIndexed { index, depotId ->
                    di.setWeight(index, selectedDepotSizes[depotId] ?: 1L)
                }

                // Track progress only for depots in this active run so excluded/complete depots
                // (including DLC already marked complete) cannot pre-fill progress at startup.
                val selectedTotalBytes = selectedDepotSizes.values.sum()
                val totalBytes = selectedTotalBytes.coerceAtLeast(1L)

                // Total expected size (used for ETA based on recent download speed)
                di.setTotalExpectedBytes(totalBytes)

                var resumedBytes = 0L

                if (allowPersistedProgress) {
                    for ((depotId, bytes) in persistedDepotBytes) {
                        // If the depot was excluded because it's fully downloaded, we still need to track its bytes
                        // so that future snapshots retain this progress.
                        val depotSize = depotSizeById[depotId] ?: continue
                        val safeBytes = bytes.coerceIn(0L, depotSize)
                        di.depotCumulativeUncompressedBytes[depotId] = java.util.concurrent.atomic.AtomicLong(safeBytes)
                        // Count resumed bytes only for depots actively downloading in this run.
                        if (depotId in selectedDepots) {
                            resumedBytes += safeBytes
                        }
                    }
                } else {
                    di.clearPersistedBytesDownloaded(appDirPath)
                }
                resumedBytes = resumedBytes.coerceIn(0L, totalBytes)

                if (resumedBytes > 0L) {
                    di.initializeBytesDownloaded(resumedBytes)
                    Timber.i("Resumed download: initialized with $resumedBytes bytes")
                }

                val downloadJob = service.scope.launch {
                    var depotDownloader: DepotDownloader? = null
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
                                        Toast.makeText(instance?.applicationContext ?: return@withContext, "Retrying download (attempt $attempt/$maxRetries)...", Toast.LENGTH_SHORT).show()
                                    }
                                    kotlinx.coroutines.delay(3000L * attempt) // Exponential backoff
                                }

                                // Wait for steamClient to be connected and logged in
                                var client = instance?.steamClient
                                var waitAttempts = 0
                                // Increased wait limit and robustness
                                while ((client == null || !isConnected || !isLoggedIn) && waitAttempts < 60) {
                                    val reason = when {
                                        client == null -> "initializing"
                                        !isConnected -> "connecting"
                                        !isLoggedIn -> "logging in"
                                        else -> "waiting"
                                    }
                                    Timber.i("Waiting for Steam client ($reason, attempt $waitAttempts)...")
                                    di.updateStatusMessage("Waiting for connection ($reason)...")
                                    delay(1000L)
                                    client = instance?.steamClient
                                    waitAttempts++
                                    
                                    // If waiting too long, check network
                                    if (waitAttempts % 5 == 0 && !isWifiConnected && PrefManager.downloadOnWifiOnly) {
                                         di.updateStatusMessage("Waiting for Wi-Fi...")
                                    }
                                }

                                if (client == null || !isConnected || !isLoggedIn) {
                                    throw Exception("Steam client not connected or logged in. Please check your connection and login status.")
                                }

                                // Get licenses from database
                                Timber.i("Retrieving licenses from database for appId: $appId")
                                di.updateStatusMessage("Retrieving licenses...")
                                var licenses = getLicensesFromDb()
                                waitAttempts = 0
                                while (licenses.isEmpty() && waitAttempts < 10) {
                                    Timber.i("Waiting for licenses to be available (attempt $waitAttempts)...")
                                    di.updateStatusMessage("Waiting for licenses...")
                                    delay(1000L)
                                    licenses = getLicensesFromDb()
                                    waitAttempts++
                                }

                                if (licenses.isEmpty()) {
                                    throw Exception("No Steam licenses found. Please ensure you are logged in.")
                                }
                                Timber.i("Retrieved ${licenses.size} licenses from database")

                                // Optimized ratios for 8-core mobile devices
                                val cpuCores = Runtime.getRuntime().availableProcessors()
                                var downloadRatio = 1.0
                                var decompressRatio = 0.5 // Start with balanced default

                                when (PrefManager.downloadSpeed.toInt()) {
                                    8 -> {
                                        downloadRatio = 0.6
                                        decompressRatio = 0.2
                                    }
                                    16 -> {
                                        downloadRatio = 1.2
                                        decompressRatio = 0.4
                                    }
                                    24 -> {
                                        downloadRatio = 1.5
                                        decompressRatio = 0.5
                                    }
                                    32 -> {
                                        downloadRatio = 2.4
                                        decompressRatio = 0.8
                                    }
                                    else -> {
                                        // Auto/Default optimization for 8 cores
                                        if (cpuCores >= 8) {
                                            downloadRatio = 1.5  // Aggressive downloading
                                            decompressRatio = 0.5 // Moderate decompression to prevent UI stutter
                                        } else {
                                            downloadRatio = 1.0
                                            decompressRatio = 0.4
                                        }
                                    }
                                }

                                val maxDownloads = (cpuCores * downloadRatio).toInt().coerceAtLeast(2)
                                val maxDecompress = (cpuCores * decompressRatio).toInt().coerceAtLeast(1)

                                Timber.i("Download Config - Cores: $cpuCores, DL Ratio: $downloadRatio, Decomp Ratio: $decompressRatio")
                                Timber.i("Threads - Max Downloads: $maxDownloads, Max Decompress: $maxDecompress")

                                // Create DepotDownloader instance
                                Timber.i("Initializing DepotDownloader for appId: $appId (attempt $attempt)")
                                di.updateStatusMessage("Initializing downloader...")
                                depotDownloader = DepotDownloader(
                                    client,
                                    licenses,
                                    debug = false,
                                    androidEmulation = true,
                                    maxDownloads = maxDownloads,
                                    maxDecompress = maxDecompress,
                                    parentJob = coroutineContext[Job],
                                    autoStartDownload = false,
                                )

                                // Create listeners for DLC apps
                                val depotIdToIndex = selectedDepots.keys.mapIndexed { index, depotId -> depotId to index }.toMap()
                                val listener = AppDownloadListener(
                                    di,
                                    depotIdToIndex,
                                    selectedDepotSizes,
                                )
                                depotDownloader!!.addListener(listener)

                                if (mainAppDepots.isNotEmpty()) {
                                    val mainAppDepotIds = mainAppDepots.keys.sorted()
                                    val mainAppItem = AppItem(
                                        appId,
                                        installDirectory = appDirPath,
                                        depot = mainAppDepotIds,
                                        verify = enableVerify,
                                    )
                                    depotDownloader!!.add(mainAppItem)
                                }

                                calculatedDlcAppIds.forEach { dlcAppId ->
                                    val dlcDepotIds = selectedDepotIdsByDlcAppId[dlcAppId].orEmpty()
                                    if (dlcDepotIds.isEmpty()) return@forEach

                                    val dlcAppItem = AppItem(
                                        dlcAppId,
                                        installDirectory = appDirPath,
                                        depot = dlcDepotIds,
                                        verify = enableVerify,
                                    )
                                    depotDownloader!!.add(dlcAppItem)
                                }

                                // Steam Controller Config download
                                val appConfig = getAppInfoOf(appId)?.config
                                if (appConfig?.steamControllerTemplateIndex == 1) {
                                    val controllerConfig = appConfig.steamControllerConfigDetails
                                        .let { selectSteamControllerConfig(it) }

                                    if (controllerConfig != null) {
                                        val publishedFileId = controllerConfig.publishedFileId
                                        runCatching {
                                            val requestBody = FormBody.Builder().add("itemcount", "1").add("publishedfileids[0]", publishedFileId.toString()).build()
                                            val request = Request.Builder().url("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1").post(requestBody).build()
                                            Net.http.newCall(request).execute().use { response ->
                                                if (response.isSuccessful) {
                                                    val responseBody = response.body?.string()
                                                    if (!responseBody.isNullOrEmpty()) {
                                                        val responseJson = JSONObject(responseBody)
                                                        val responseData = responseJson.optJSONObject("response")
                                                        val fileUrl = responseData?.optJSONArray("publishedfiledetails")?.optJSONObject(0)?.optString("file_url", "")?.trim()
                                                        if (!fileUrl.isNullOrEmpty()) {
                                                            val configFile = File(appDirPath, STEAM_CONTROLLER_CONFIG_FILENAME)
                                                            val downloadRequest = Request.Builder().url(fileUrl).get().build()
                                                            Net.http.newCall(downloadRequest).execute().use { downloadResponse ->
                                                                if (downloadResponse.isSuccessful) {
                                                                    downloadResponse.body?.byteStream()?.use { input ->
                                                                        configFile.outputStream().use { output -> input.copyTo(output) }
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

                                // Signal that no more items will be added
                                depotDownloader!!.finishAdding()

                                // Start Download
                                di.updateStatusMessage("Starting download...")
                                depotDownloader!!.startDownloading()

                                Timber.i("Downloading game to $appDirPath (attempt $attempt)")

                                // Wait for completion - safely handle the deferred result to avoid Unit cast errors
                                try {
                                    val completion = depotDownloader?.getCompletion()
                                    if (completion is kotlinx.coroutines.Deferred<*>) {
                                        Timber.i("Waiting for DepotDownloader Deferred completion...")
                                        completion.await()
                                    } else if (completion != null) {
                                        // If it's a CompletableFuture or other type, try to join it
                                        Timber.i("Downloader completion is ${completion.javaClass.simpleName}, waiting...")
                                        if (completion is java.util.concurrent.CompletableFuture<*>) {
                                            completion.join()
                                        }
                                    } else {
                                        Timber.i("Downloader completion is null, assuming immediate success")
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Timber.w(e, "DepotDownloader completion await encountered an error")
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
                                // Close the downloader from the failed attempt
                                runCatching { depotDownloader?.close() }.onFailure { closeError ->
                                    Timber.w(closeError, "Failed to close downloader on retry for app $appId")
                                }
                                depotDownloader = null
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
                                val dlcDepotIds = allDepotIdsByDlcAppId[dlcAppId].orEmpty()
                                completeAppDownload(di, dlcAppId, dlcDepotIds, emptyList(), appDirPath)
                            }
                            Timber.i("Installation finalized for appId: $appId")

                            // Show success message to user
                            instance?.let { service ->
                                service.scope.launch(Dispatchers.Main) {
                                    Toast.makeText(service.applicationContext, "Download complete", Toast.LENGTH_SHORT).show()
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
                        removeDownloadJob(appId)
                        return@launch
                    } catch (e: CancellationException) {
                        if (di.isDeleting) {
                            Timber.d("Download cancelled for deletion for app $appId")
                            return@launch
                        }

                        Timber.d(e, "Download paused for app $appId")
                        // Keep downloadingAppInfo on cancellation so resume does not fall into verify mode.
                        di.persistProgressSnapshot(force = true)
                        di.updateStatus(DownloadPhase.PAUSED)
                        di.setActive(false)
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Download failed for app $appId")
                        clearFailedResumeState(appId)

                        val errorMsg = when (e) {
                            is ClassCastException -> "Casting error: ${e.message}"
                            is NullPointerException -> "Null reference: ${e.message}"
                            else -> e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                        }

                        di.updateStatus(DownloadPhase.FAILED, "Failed: $errorMsg")
                        di.setActive(false)
                        // Clean up markers and DB state
                        MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                        runBlocking {
                            instance?.downloadingAppInfoDao?.deleteApp(appId)
                            Unit
                        }
                        removeDownloadJob(appId)
                        // Show error to user
                        instance?.let { service ->
                            service.scope.launch(Dispatchers.Main) {
                                Toast.makeText(service.applicationContext, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                                Unit
                            }
                        }
                        PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
                        Unit
                    } finally {
                        runCatching {
                            depotDownloader?.close()
                            Unit
                        }.onFailure { closeError ->
                            Timber.w(closeError, "Failed to close downloader for app $appId")
                        }
                        Unit
                    }
                    Unit
                }
                downloadJob.invokeOnCompletion { throwable ->
                    if (throwable is CancellationException && throwable !is DownloadFailedException) {
                        if (!di.isDeleting) {
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
            val allDepotIdsByDlcAppId = dlcAppDepots.values
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

            val mainAppDlcIds = getMainAppDlcIdsWithoutProperDepotDlcIds(appId)
            if (dlcAppDepots.isEmpty()) {
                mainAppDlcIds.addAll(mainAppDepots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct())
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
                    Toast.makeText(service.applicationContext, "Download complete", Toast.LENGTH_SHORT).show()
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
                // If manifest-size top-up was deferred during depot completion, settle the
                // remaining bytes once at the end to avoid visible mid-download jumps.
                val totalExpectedBytes = downloadInfo.getTotalExpectedBytes()
                if (totalExpectedBytes > 0L) {
                    val downloadedBytes = downloadInfo.getBytesDownloaded()
                    val remainingBytes = (totalExpectedBytes - downloadedBytes).coerceAtLeast(0L)
                    if (remainingBytes > 0L) {
                        downloadInfo.updateBytesDownloaded(remainingBytes, System.currentTimeMillis())
                        downloadInfo.emitProgressChange()
                    }
                }

                // Handle completion: add markers
                withContext(Dispatchers.IO) {
                    MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DLL_REPLACED)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_COLDCLIENT_USED)

                    // Ensure the main app is marked as downloaded in the DB
                    val mainAppId = downloadInfo.gameId
                    val service = instance
                    if (service != null) {
                        val mainAppInfo = service.appInfoDao.getInstalledApp(mainAppId)
                        if (mainAppInfo != null) {
                            if (!mainAppInfo.isDownloaded) {
                                service.appInfoDao.update(mainAppInfo.copy(isDownloaded = true))
                                Timber.i("Marked main app $mainAppId as downloaded in DB")
                            }
                        } else {
                            service.appInfoDao.insert(AppInfo(mainAppId, isDownloaded = true))
                            Timber.i("Inserted main app $mainAppId as downloaded in DB")
                        }
                    }
                    Unit
                }

                val service = instance
                if (service != null) {
                    createSteamShortcut(service, downloadInfo.gameId)
                }

                downloadInfo.updateStatus(DownloadPhase.COMPLETE)
                PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(downloadInfo.gameId))

                // Clear persisted bytes file on successful completion
                downloadInfo.clearPersistedBytesDownloaded(appDirPath, sync = true)
                checkQueue()
            }
            Unit
        }

        // onChunkCompleted reports GLOBAL cumulative bytes across all depots, not per depot.
        // We diff successive global values to get the real per chunk delta.
        private class AppDownloadListener(
            private val downloadInfo: DownloadInfo,
            private val depotIdToIndex: Map<Int, Int>,
            private val depotMaxBytesById: Map<Int, Long>,
        ) : IDownloadListener {
            private var lastByteProgressAtMs: Long = 0L

            // Last global cumulative uncompressed bytes from onChunkCompleted
            private val lastGlobalUncompressedBytes = java.util.concurrent.atomic.AtomicLong(0L)

            // Consumes monotonic global cumulative bytes and returns only newly observed delta.
            // This is resilient to out-of-order callbacks from concurrent decompression workers.
            private fun consumeGlobalUncompressedDelta(currentGlobalBytes: Long): Long {
                if (currentGlobalBytes <= 0L) return 0L

                while (true) {
                    val previousGlobalBytes = lastGlobalUncompressedBytes.get()
                    if (currentGlobalBytes <= previousGlobalBytes) {
                        return 0L
                    }

                    if (lastGlobalUncompressedBytes.compareAndSet(previousGlobalBytes, currentGlobalBytes)) {
                        return currentGlobalBytes - previousGlobalBytes
                    }
                }
            }

            // Sets per depot cumulative value, returns delta
            private fun updateDepotBytesAndGetDelta(depotId: Int, reportedBytes: Long): Long {
                if (!depotIdToIndex.containsKey(depotId)) {
                    return 0L
                }

                var atomicBytes = downloadInfo.depotCumulativeUncompressedBytes[depotId]
                if (atomicBytes == null) {
                    atomicBytes = java.util.concurrent.atomic.AtomicLong(0L)
                    val existing = downloadInfo.depotCumulativeUncompressedBytes.putIfAbsent(depotId, atomicBytes)
                    if (existing != null) {
                        atomicBytes = existing
                    }
                }

                if (reportedBytes <= 0L) {
                    return 0L
                }

                val maxBytes = depotMaxBytesById[depotId]?.coerceAtLeast(1L)
                val clampedReportedBytes = if (maxBytes != null) {
                    reportedBytes.coerceIn(0L, maxBytes)
                } else {
                    reportedBytes
                }

                var deltaBytes = 0L
                while (true) {
                    val prev = atomicBytes.get()
                    val next = maxOf(prev, clampedReportedBytes)
                    if (prev == next) {
                        break
                    }
                    if (atomicBytes.compareAndSet(prev, next)) {
                        deltaBytes = (next - prev).coerceAtLeast(0L)
                        break
                    }
                }
                return deltaBytes
            }

            // Adds delta to per depot cumulative bytes, clamped to depot max
            private fun addDeltaToDepotBytes(depotId: Int, delta: Long) {
                if (delta <= 0L) return
                if (!depotIdToIndex.containsKey(depotId)) return

                var atomicBytes = downloadInfo.depotCumulativeUncompressedBytes[depotId]
                if (atomicBytes == null) {
                    atomicBytes = java.util.concurrent.atomic.AtomicLong(0L)
                    val existing = downloadInfo.depotCumulativeUncompressedBytes.putIfAbsent(depotId, atomicBytes)
                    if (existing != null) {
                        atomicBytes = existing
                    }
                }

                val maxBytes = depotMaxBytesById[depotId]?.coerceAtLeast(1L) ?: Long.MAX_VALUE
                while (true) {
                    val prev = atomicBytes.get()
                    val next = (prev + delta).coerceIn(0L, maxBytes)
                    if (prev == next) break
                    if (atomicBytes.compareAndSet(prev, next)) break
                }
            }

            override fun onItemAdded(item: DownloadItem) {
                Timber.d("Item ${item.appId} added to queue")
                Unit
            }

            override fun onDownloadStarted(item: DownloadItem) {
                Timber.i("Item ${item.appId} download started")
                downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)
                Unit
            }

            override fun onDownloadCompleted(item: DownloadItem) {
                Timber.i("Item ${item.appId} download completed")
                Unit
            }
            override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                if (error is CancellationException && error !is DownloadFailedException) {
                    if (downloadInfo.isDeleting) {
                        Timber.d("Item ${item.appId} download cancelled for deletion")
                        return
                    }
                    // Treat cancellation as pause: preserve resume metadata/state.
                    Timber.d(error, "Item ${item.appId} download paused")
                    downloadInfo.persistProgressSnapshot(force = true)
                    downloadInfo.updateStatus(DownloadPhase.PAUSED)
                    downloadInfo.setActive(false)
                    return
                }

                Timber.e(error, "Item ${item.appId} failed to download")
                // Hard stop current session on item failure so we do not continue toward completion
                // with missing/corrupt files. Resume metadata is preserved for retry.
                downloadInfo.persistProgressSnapshot(force = true)
                downloadInfo.updateStatus(DownloadPhase.FAILED)
                downloadInfo.failedToDownload()

                instance?.let { service ->
                    service.scope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            service.applicationContext,
                            "Download error for depot ${item.appId}: ${error.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                Unit
            }

            override fun onStatusUpdate(message: String) {
                Timber.d("Download status: $message")
                
                // Extract filename if present (usually "Downloading filename..." or "Verifying filename...")
                if (message.startsWith("Downloading ", ignoreCase = true) || 
                    message.startsWith("Verifying ", ignoreCase = true) ||
                    message.startsWith("Patching ", ignoreCase = true)) {
                    val parts = message.split(" ")
                    if (parts.size > 1) {
                        val fileName = parts[1].removeSuffix("...")
                        downloadInfo.updateCurrentFileName(fileName)
                    }
                }

                val mappedStatus = DownloadPhase.fromMessage(message)
                if (mappedStatus != null) {
                    if (
                        mappedStatus == DownloadPhase.PREPARING ||
                        mappedStatus == DownloadPhase.VERIFYING ||
                        mappedStatus == DownloadPhase.PATCHING
                    ) {
                        val nowMs = System.currentTimeMillis()
                        val recentlyMovedBytes = nowMs - lastByteProgressAtMs <= 5_000L
                        if (recentlyMovedBytes && downloadInfo.getStatusFlow().value == DownloadPhase.DOWNLOADING) {
                            return
                        }
                    }
                    downloadInfo.updateStatus(mappedStatus, message)
                } else {
                    downloadInfo.updateStatusMessage(message)
                }
                Unit
            }
            override fun onChunkCompleted(
                depotId: Int,
                depotPercentComplete: Float,
                compressedBytes: Long,
                uncompressedBytes: Long,
            ) {
                // uncompressedBytes is global cumulative across all depots.
                val deltaBytes = consumeGlobalUncompressedDelta(uncompressedBytes)

                if (deltaBytes > 0L) {
                    lastByteProgressAtMs = System.currentTimeMillis()

                    addDeltaToDepotBytes(depotId, deltaBytes)
                    downloadInfo.updateBytesDownloaded(deltaBytes, lastByteProgressAtMs)
                    downloadInfo.markProgressSnapshotDirty()

                    // If resume verification/prepare phases already ran and bytes are now moving again,
                    // report active downloading.
                    val currentPhase = downloadInfo.getStatusFlow().value
                    if (
                        currentPhase == DownloadPhase.UNKNOWN ||
                        currentPhase == DownloadPhase.PREPARING ||
                        currentPhase == DownloadPhase.VERIFYING ||
                        currentPhase == DownloadPhase.PATCHING
                    ) {
                        downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)
                    }
                }

                depotIdToIndex[depotId]?.let { index ->
                    downloadInfo.setProgress(depotPercentComplete, index)
                }

                // Emit progress change
                downloadInfo.emitProgressChange()
                Unit
            }

            override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                Timber.i("Depot $depotId completed (compressed: $compressedBytes, uncompressed: $uncompressedBytes)")

                // Catch up any uncompressed bytes not covered by onChunkCompleted
                val deltaBytes = updateDepotBytesAndGetDelta(depotId, uncompressedBytes)

                if (deltaBytes > 0L) {
                    lastByteProgressAtMs = System.currentTimeMillis()
                    downloadInfo.updateBytesDownloaded(deltaBytes, lastByteProgressAtMs)
                    downloadInfo.markProgressSnapshotDirty()
                }

                // Credit full manifest size so dedup gaps don't stall progress
                val manifestSize = depotMaxBytesById[depotId] ?: 0L
                val tracked = downloadInfo.depotCumulativeUncompressedBytes[depotId]?.get() ?: 0L
                val remaining = manifestSize - tracked
                if (remaining > 0L) {
                    addDeltaToDepotBytes(depotId, remaining)
                    downloadInfo.markProgressSnapshotDirty()
                }

                val currentPhase = downloadInfo.getStatusFlow().value
                if (
                    currentPhase == DownloadPhase.UNKNOWN ||
                    currentPhase == DownloadPhase.PREPARING ||
                    currentPhase == DownloadPhase.VERIFYING ||
                    currentPhase == DownloadPhase.PATCHING
                ) {
                    downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)
                }

                depotIdToIndex[depotId]?.let { index ->
                    downloadInfo.setProgress(1f, index)
                }

                // Emit progress change
                downloadInfo.emitProgressChange()
                Unit
            }
        }


        fun getWindowsLaunchInfos(appId: Int): List<LaunchInfo> {
            return getAppInfoOf(appId)?.let { appInfo ->
                appInfo.config.launch.filter { launchInfo ->
                    // since configOS was unreliable and configArch was even more unreliable
                    launchInfo.executable.endsWith(".exe")
                }
            }.orEmpty()
        }

        suspend fun notifyRunningProcesses(vararg gameProcesses: GameProcessInfo) = withContext(Dispatchers.IO) {
            instance?.let { steamInstance ->
                if (isConnected) {
                    val gamesPlayed = gameProcesses.mapNotNull { gameProcess ->
                        getAppInfoOf(gameProcess.appId)?.let { appInfo ->
                            getPkgInfoOf(gameProcess.appId)?.let { pkgInfo ->
                                appInfo.branches[gameProcess.branch]?.let { branch ->
                                    val processId = gameProcess.processes
                                        .firstOrNull { it.parentIsSteam }
                                        ?.processId
                                        ?: gameProcess.processes.firstOrNull()?.processId
                                        ?: 0

                                    val userAccountId = userSteamId!!.accountID.toInt()
                                    GamePlayedInfo(
                                        gameId = gameProcess.appId.toLong(),
                                        processId = processId,
                                        ownerId = if (pkgInfo.ownerAccountId.contains(userAccountId)) {
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

                    steamInstance._steamApps?.notifyGamesPlayed(
                        gamesPlayed = gamesPlayed,
                        clientOsType = EOSType.AndroidUnknown,
                    )
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
        ): Deferred<PostSyncInfo> = parentScope.async {
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
                        val steamCloud = steamInstance?._steamCloud

                        if (steamInstance != null && appInfo != null && steamCloud != null) {
                            progressWrapper("Checking Cloud Saves...", 0f)
                            val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                appInfo = appInfo,
                                clientId = clientId,
                                steamInstance = steamInstance,
                                steamCloud = steamCloud,
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

                                    val pendingRemoteOperations = steamCloud.signalAppLaunchIntent(
                                        appId = appId,
                                        clientId = clientId,
                                        machineName = SteamUtils.getMachineName(steamInstance),
                                        ignorePendingOperations = ignorePendingOperations,
                                        osType = EOSType.AndroidUnknown,
                                    ).await()

                                    if (pendingRemoteOperations.isNotEmpty() && !ignorePendingOperations) {
                                        syncResult = PostSyncInfo(
                                            syncResult = SyncResult.PendingOperations,
                                            pendingRemoteOperations = pendingRemoteOperations,
                                        )
                                    } else if (ignorePendingOperations &&
                                        pendingRemoteOperations.any {
                                            it.operation == ECloudPendingRemoteOperation.k_ECloudPendingRemoteOperationAppSessionActive
                                        }
                                    ) {
                                        steamInstance._steamUser!!.kickPlayingSession()
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

        suspend fun forceSyncUserFiles(
            appId: Int,
            prefixToPath: (String) -> String,
            preferredSave: SaveLocation = SaveLocation.None,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            overrideLocalChangeNumber: Long? = null,
        ): Deferred<PostSyncInfo> = parentScope.async {
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
                        val steamCloud = steamInstance?._steamCloud

                        if (steamInstance != null && appInfo != null && steamCloud != null) {
                            val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                appInfo = appInfo,
                                clientId = clientId,
                                steamInstance = steamInstance,
                                steamCloud = steamCloud,
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

        suspend fun closeApp(
            appId: Int,
            isOffline: Boolean,
            prefixToPath: (String) -> String,
            onProgress: ((message: String, progress: Float) -> Unit)? = null,
        ) = withContext(Dispatchers.IO) {
            async {
                if (isOffline || !isConnected) {
                    return@async
                }

                if (!tryAcquireSync(appId)) {
                    Timber.w("Cannot close app when sync already in progress for appId=$appId")
                    return@async
                }

                try {
                    val progressWrapper: (String, Float) -> Unit = { msg, prog ->
                        cloudSyncStatus.value = CloudSyncMessage(appId, true, msg, prog)
                        onProgress?.invoke(msg, prog)
                    }
                    val maxAttempts = 3
                    for (attempt in 1..maxAttempts) {
                        try {
                            val clientId = PrefManager.clientId
                            val steamInstance = instance
                            val appInfo = getAppInfoOf(appId)
                            val steamCloud = steamInstance?._steamCloud

                            if (steamInstance != null && appInfo != null && steamCloud != null) {
                                progressWrapper("Checking Local Saves...", 0f)
                                val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                    appInfo = appInfo,
                                    clientId = clientId,
                                    steamInstance = steamInstance,
                                    steamCloud = steamCloud,
                                    parentScope = this@async,
                                    prefixToPath = prefixToPath,
                                    onProgress = progressWrapper,
                                ).await()

                                steamCloud.signalAppExitSyncDone(
                                    appId = appId,
                                    clientId = clientId,
                                    uploadsCompleted = postSyncInfo?.uploadsCompleted == true,
                                    uploadsRequired = postSyncInfo?.uploadsRequired == false,
                                )
                            }
                            break
                        } catch (e: AsyncJobFailedException) {
                            if (attempt == maxAttempts) {
                                Timber.e(e, "Close app sync failed after $maxAttempts attempts for app $appId")
                            } else {
                                Timber.w("Close app sync attempt $attempt failed for app $appId, retrying...")
                                delay(1000L * attempt)
                            }
                        }
                    }
                } finally {
                    cloudSyncStatus.value = null
                    releaseSync(appId)
                }
            }
        }

        interface CloudSyncCallback {
            fun onProgress(message: String, progress: Float)
            fun onComplete()
        }

        @JvmStatic
        fun syncCloudOnExit(context: android.content.Context, appId: Int, callback: CloudSyncCallback) {
            val accountId = userSteamId?.accountID ?: 0L
            val prefixToPath: (String) -> String = { prefix ->
                com.winlator.cmod.steam.enums.PathType.from(prefix).toAbsPath(context, appId, accountId)
            }
            CoroutineScope(Dispatchers.IO).launch {
                closeApp(
                    appId = appId,
                    isOffline = false,
                    prefixToPath = prefixToPath,
                    onProgress = { msg, prog -> callback.onProgress(msg, prog) }
                )
                notifyRunningProcesses()
                withContext(Dispatchers.Main) {
                    callback.onComplete()
                }
            }
        }

        data class FileChanges(
            val filesDeleted: List<UserFileInfo>,
            val filesModified: List<UserFileInfo>,
            val filesCreated: List<UserFileInfo>,
        )

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

            val vdf = buildString {
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

            return vdf;
        }

        private fun login(
            username: String,
            accessToken: String? = null,
            refreshToken: String? = null,
            password: String? = null,
            rememberSession: Boolean = false,
            twoFactorAuth: String? = null,
            emailAuth: String? = null,
            clientId: Long? = null,
        ) {
            isLoggingOut = false
            val steamUser = instance!!._steamUser!!

            // Sensitive info, only print in DEBUG build.
//            if (BuildConfig.DEBUG) {
//                Timber.d(
//                    """
//                    Login Information:
//                     Username: $username
//                     AccessToken: $accessToken
//                     RefreshToken: $refreshToken
//                     Password: $password
//                     Remember Session: $rememberSession
//                     TwoFactorAuth: $twoFactorAuth
//                     EmailAuth: $emailAuth
//                    """.trimIndent(),
//                )
//            }

            PrefManager.username = username

            if ((password != null && rememberSession) || refreshToken != null) {
                if (accessToken != null) {
                    PrefManager.accessToken = accessToken
                }

                if (refreshToken != null) {
                    PrefManager.refreshToken = refreshToken
                }

                if (clientId != null) {
                    PrefManager.clientId = clientId
                }
            }

            val event = SteamEvent.LogonStarted(username)
            PluviaApp.events.emit(event)

            steamUser.logOn(
                LogOnDetails(
                    username = SteamUtils.removeSpecialChars(username).trim(),
                    password = password?.let { SteamUtils.removeSpecialChars(it).trim() },
                    shouldRememberPassword = rememberSession,
                    twoFactorCode = twoFactorAuth,
                    authCode = emailAuth,
                    accessToken = refreshToken,
                    loginID = SteamUtils.getUniqueDeviceId(instance!!),
                    machineName = SteamUtils.getMachineName(instance!!),
                    chatMode = ChatMode.NEW_STEAM_CHAT,
                ),
            )
        }

        suspend fun startLoginWithCredentials(
            username: String,
            password: String,
            rememberSession: Boolean,
            authenticator: IAuthenticator,
        ) = withContext(Dispatchers.IO) {
            try {
                Timber.i("Logging in via credentials.")
                instance!!._loginResult = LoginResult.InProgress
                Timber.i("Set login result to InProgress.")
                instance!!.steamClient?.let { steamClient ->
                    val authDetails = AuthSessionDetails().apply {
                        this.username = username.trim()
                        this.password = password.trim()
                        this.persistentSession = rememberSession
                        this.authenticator = authenticator
                        this.deviceFriendlyName = SteamUtils.getMachineName(instance!!)
                        this.clientOSType = EOSType.WinUnknown
                    }

                    val event = SteamEvent.LogonStarted(username)
                    PluviaApp.events.emit(event)

                    // Outer loop: retries the entire auth session when the connection
                    // drops (e.g. TryAnotherCM) during 2FA polling.
                    var loginComplete = false
                    while (isActive && !loginComplete) {
                        val currentClient = instance!!.steamClient ?: break
                        val authSession = currentClient.authentication.beginAuthSessionViaCredentials(authDetails).await()

                        Timber.d("Waiting for authentication result (handles 2FA). Interval: ${authSession.pollingInterval}s")

                        // pollingWaitForResult handles the full 2FA flow:
                        // - Checks allowedConfirmations for the required guard type
                        // - Calls IAuthenticator callbacks (acceptDeviceConfirmation, getDeviceCode, getEmailCode)
                        // - Submits the guard code to Steam via sendSteamGuardCode
                        // - Polls until authentication completes
                        var pollResult: AuthPollResult? = null
                        var disconnected = false
                        while (isActive && pollResult == null && !disconnected) {
                            try {
                                pollResult = authSession.pollingWaitForResult().await()
                            } catch (e: AuthenticationException) {
                                if (e.result == EResult.Expired || e.result == EResult.FileNotFound) {
                                    Timber.w("Auth session expired during 2FA wait, retrying...")
                                    delay(authSession.pollingInterval.toLong() * 1000L)
                                    continue
                                }
                                throw e
                            } catch (e: java.util.concurrent.CancellationException) {
                                // Connection dropped (TryAnotherCM) — all pending futures
                                // are cancelled. Wait for reconnection and restart the
                                // auth session so the user doesn't see a spurious failure.
                                if (!isActive) throw CancellationException("Coroutine cancelled")
                                Timber.w("Auth poll cancelled (likely TryAnotherCM), waiting for reconnection...")
                                disconnected = true
                            }
                        }

                        if (disconnected) {
                            // Wait for the service to reconnect to a new CM server
                            Timber.i("Waiting for Steam reconnection before retrying credential auth...")
                            try {
                                withTimeout(30_000L) {
                                    isConnectedFlow.first { it }
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                throw Exception("Timed out waiting for Steam reconnection")
                            }
                            Timber.i("Reconnected, restarting credential auth session...")
                            delay(500L) // brief settle time after reconnect
                            continue
                        }

                        if (pollResult == null) {
                            throw CancellationException("Credential auth polling cancelled")
                        }

                        Timber.i("Authentication successful for ${pollResult.accountName}")

                        if (pollResult.accountName.isEmpty() && pollResult.refreshToken.isEmpty()) {
                            throw Exception("No account name or refresh token received.")
                        }

                        login(
                            clientId = authSession.clientID,
                            username = pollResult.accountName,
                            accessToken = pollResult.accessToken,
                            refreshToken = pollResult.refreshToken,
                            rememberSession = rememberSession,
                        )
                        loginComplete = true
                    }
                } ?: run {
                    Timber.e("Could not logon: Failed to connect to Steam")

                    val event = SteamEvent.LogonEnded(username, LoginResult.Failed, "No connection to Steam")
                    PluviaApp.events.emit(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")

                val message = when (e) {
                    is CancellationException -> "Unknown cancellation"
                    is AuthenticationException -> e.result?.name ?: e.message
                    else -> e.message ?: e.javaClass.name
                }

                val event = SteamEvent.LogonEnded(username, LoginResult.Failed, message)
                PluviaApp.events.emit(event)
            }
        }

        suspend fun startLoginWithQr() = withContext(Dispatchers.IO) {
            try {
                Timber.i("Logging in via QR.")

                instance!!.steamClient?.let { steamClient ->
                    isWaitingForQRAuth = true

                    val authDetails = AuthSessionDetails().apply {
                        this.deviceFriendlyName = SteamUtils.getMachineName(instance!!)
                        this.clientOSType = EOSType.WinUnknown
                        this.persistentSession = true
                    }

                    val authSession = steamClient.authentication.beginAuthSessionViaQR(authDetails).await()

                    // Steam will periodically refresh the challenge url, this callback allows you to draw a new qr code.
                    authSession.challengeUrlChanged = instance

                    val qrEvent = SteamEvent.QrChallengeReceived(authSession.challengeUrl)
                    PluviaApp.events.emit(qrEvent)

                    Timber.d("PollingInterval: ${authSession.pollingInterval.toLong()}")

                    var authPollResult: AuthPollResult? = null

                    // FIX: Poll via raw protobuf RPC instead of JavaSteam's pollAuthSessionStatus()
                    // so we can read hadRemoteInteraction — true when QR is scanned but not yet
                    // approved. This lets the UI show the 2FA screen while the user confirms.
                    val authService = steamClient.getHandler<SteamUnifiedMessages>()!!
                        .createService<Authentication>()
                    var qrScannedEmitted = false

                    while (isWaitingForQRAuth && authPollResult == null) {
                        try {
                            val request = CAuthentication_PollAuthSessionStatus_Request.newBuilder().apply {
                                clientId = authSession.clientID
                                requestId = com.google.protobuf.ByteString.copyFrom(authSession.requestID)
                            }.build()

                            val result = authService.pollAuthSessionStatus(request).await()

                            if (result.result != EResult.OK) {
                                throw AuthenticationException("Failed to poll status", result.result)
                            }

                            val response = result.body

                            // Replicate handlePollAuthSessionStatusResponse behaviour
                            if (response.newClientId != 0L) {
                                authSession.clientID = response.newClientId
                            }
                            if (response.newChallengeUrl.isNotEmpty()) {
                                val urlEvent = SteamEvent.QrChallengeReceived(response.newChallengeUrl)
                                PluviaApp.events.emit(urlEvent)
                            }

                            // Detect scan before approval
                            if (!qrScannedEmitted && response.hadRemoteInteraction) {
                                qrScannedEmitted = true
                                PluviaApp.events.emit(SteamEvent.QrCodeScanned)
                            }

                            // Check for completion
                            if (response.refreshToken.isNotEmpty()) {
                                authPollResult = AuthPollResult(response)
                            } else {
                                delay(authSession.pollingInterval.toLong() * 1000L)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Poll auth session status error")
                            throw e
                        }
                    }

                    isWaitingForQRAuth = false

                    val event = SteamEvent.QrAuthEnded(authPollResult != null)
                    PluviaApp.events.emit(event)

                    // there is a chance qr got cancelled and there is no authPollResult
                    if (authPollResult == null) {
                        Timber.e("Got no auth poll result")
                        throw Exception("Got no auth poll result")
                    }

                    login(
                        clientId = authSession.clientID,
                        username = authPollResult.accountName,
                        accessToken = authPollResult.accessToken,
                        refreshToken = authPollResult.refreshToken,
                    )
                } ?: run {
                    Timber.e("Could not start QR logon: Failed to connect to Steam")

                    val event = SteamEvent.QrAuthEnded(success = false, message = "No connection to Steam")
                    PluviaApp.events.emit(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "QR failed")

                val message = when (e) {
                    is CancellationException -> "QR Session timed out"
                    is AuthenticationException -> e.result?.name ?: e.message
                    else -> e.message ?: e.javaClass.name
                }

                val event = SteamEvent.QrAuthEnded(success = false, message = message)
                PluviaApp.events.emit(event)
            }
        }

        fun stopLoginWithQr() {
            Timber.i("Stopping QR polling")

            isWaitingForQRAuth = false
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, SteamService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start SteamService")
            }
        }

        fun stop() {
            instance?.let { steamInstance ->
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

            // Cancel background jobs immediately
            instance?.picsGetProductInfoJob?.cancel()
            instance?.picsChangesCheckerJob?.cancel()
            instance?.friendCheckerJob?.cancel()

            // Emit event synchronously so the UI can react in the same frame
            PluviaApp.events.emit(SteamEvent.LoggedOut(username))

            // Tell Steam we're leaving (best-effort, service will stop in onLoggedOff)
            instance?.let { svc ->
                svc.scope.launch(Dispatchers.Default) {
                    try {
                        clearDatabase()
                        svc._steamUser?.logOff()
                    } catch (e: Exception) {
                        Timber.e(e, "Error during async logOff")
                    }
                }
            }
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
                        // changeNumbersDao.deleteAll()
                        // fileChangeListsDao.deleteAll()
                        
                        licenseDao.deleteAll()
                        encryptedAppTicketDao.deleteAll()
                        downloadingAppInfoDao.deleteAll()
                    }
                }
            }
        }

        private fun performLogOffDuties() {
            val username = PrefManager.username

            clearUserData()

            val event = SteamEvent.LoggedOut(username)
            PluviaApp.events.emit(event)

            // Cancel previous continuous jobs or else they will continue to run even after logout
            instance?.picsGetProductInfoJob?.cancel()
            instance?.picsChangesCheckerJob?.cancel()
            instance?.friendCheckerJob?.cancel()
        }

        suspend fun getOwnedGames(friendID: Long): List<OwnedGames> = withContext(Dispatchers.IO) {
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
        ): Boolean = withContext(Dispatchers.IO) {
            // Don't try if there's no internet
            if (!isConnected) return@withContext false

            val steamApps = instance?._steamApps ?: return@withContext false

            // ── 1. Fetch the latest app header from Steam (PICS).
            val pics = steamApps.picsGetProductInfo(
                apps = listOf(PICSRequest(id = appId)),
                packages = emptyList(),
            ).await()

            val remoteAppInfo = pics.results
                .firstOrNull()
                ?.apps
                ?.values
                ?.firstOrNull()
                ?: return@withContext false          // nothing returned ⇒ treat as up-to-date

            val remoteSteamApp = remoteAppInfo.keyValues.generateSteamApp()
            val localSteamApp = getAppInfoOf(appId) ?: return@withContext true // not cached yet

            // ── 2. Compare manifest IDs of the depots we actually install.
            getDownloadableDepots(appId).keys.any { depotId ->
                val remoteManifest = remoteSteamApp.depots[depotId]?.manifests?.get(branch)
                val localManifest = localSteamApp.depots[depotId]?.manifests?.get(branch)
                // If remote manifest is null, skip this depot (hack for Castle Crashers)
                if (remoteManifest == null) return@any false
                remoteManifest?.gid != localManifest?.gid
            }
        }

        suspend fun checkDlcOwnershipViaPICSBatch(dlcAppIds: Set<Int>): Set<Int> {
            if (dlcAppIds.isEmpty()) return emptySet()

            val steamApps = instance?._steamApps ?: return emptySet()

            try {
                // Step 1: Get access tokens for all DLC appIds at once
                val tokens = steamApps.picsGetAccessTokens(
                    appIds = dlcAppIds.toList(),
                    packageIds = emptyList(),
                ).await()

                Timber.d("Access tokens response:")
                Timber.d("  - Granted tokens: ${tokens.appTokens.keys}")
                Timber.d("  - Denied tokens: ${tokens.appTokensDenied}")

                // Step 2: Filter to only appIds that have tokens (we own them)
                val ownedAppIds = tokens.appTokens.keys.filter { it in dlcAppIds }.toSet()

                Timber.d("Owned appIds (from tokens): $ownedAppIds")

                if (ownedAppIds.isEmpty()) {
                    Timber.w("No owned DLCs found via access tokens")
                    return emptySet()
                }

                // Step 3: Create PICSRequests for all owned appIds
                val picsRequests = ownedAppIds.map { appId ->
                    val token = tokens.appTokens[appId] ?: return@map null
                    PICSRequest(id = appId, accessToken = token)
                }.filterNotNull()

                Timber.d("Created ${picsRequests.size} PICS requests")

                if (picsRequests.isEmpty()) return emptySet()

                // Step 4: Query PICS for all apps at once (batch them)
                // Note: Steam has limits, so you might need to chunk if > 100 apps
                val chunkSize = 100
                val allOwnedAppIds = mutableSetOf<Int>()

                picsRequests.chunked(chunkSize).forEach { chunk ->
                    Timber.d("Querying PICS chunk with ${chunk.size} apps")
                    val callback = steamApps.picsGetProductInfo(
                        apps = chunk,
                        packages = emptyList(),
                    ).await()

                    // Collect all appIds that returned results
                    callback.results.forEach { picsCallback ->
                        val returnedAppIds = picsCallback.apps.keys
                        Timber.d("  PICS result: ${returnedAppIds.size} apps returned")
                        allOwnedAppIds.addAll(picsCallback.apps.keys)
                    }
                }

                Timber.i("Final owned DLC appIds: $allOwnedAppIds")
                Timber.i("Total owned: ${allOwnedAppIds.size} out of ${dlcAppIds.size} checked")

                return allOwnedAppIds
            } catch (e: Exception) {
                Timber.e(e, "Failed to check DLC ownership via PICS batch for ${dlcAppIds.size} appIds")
                return emptySet()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        notificationHelper = NotificationHelper(applicationContext)
        val notification = notificationHelper.createForegroundNotification("Steam Service is running")
        startForeground(1, notification)

        _isConnectedFlow.value = steamClient?.isConnected ?: false
        _isLoggedInFlow.value = steamClient?.steamID?.isValid ?: false

        // JavaSteam logger CME hot-fix
        runCatching {
            val clazz = Class.forName("in.dragonbra.javasteam.util.log.LogManager")
            val field = clazz.getDeclaredField("LOGGERS").apply { isAccessible = true }
            field.set(
                /* obj = */ null,
                java.util.concurrent.ConcurrentHashMap<Any, Any>(),   // replaces the HashMap
            )
        }

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)

        // pause downloads when WiFi/Ethernet is lost
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // only pause if no WiFi/LAN remains (avoids false pause on multi-network)
                if (PrefManager.downloadOnWifiOnly && !isWifiConnected) {
                    for ((appId, info) in downloadJobs.entries.toList()) {
                        Timber.d("Cancelling job")
                        info.cancel()
                        PluviaApp.events.emit(AndroidEvent.DownloadPausedDueToConnectivity(appId))
                        removeDownloadJob(appId)
                    }
                    notificationHelper.notify(getString(R.string.download_paused_wifi))
                }
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // To view log messages in android logcat properly
        LogManager.addListener(logger)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification intents
        when (intent?.action) {
            NotificationHelper.ACTION_EXIT -> {
                Timber.d("Exiting app via notification intent")

                val event = AndroidEvent.EndProcess
                PluviaApp.events.emit(event)

                return START_NOT_STICKY
            }
        }

        if (!isRunning) {
            Timber.i("Using server list path: $serverListPath")
            
            // Ensure parent directory exists
            File(serverListPath).parentFile?.mkdirs()

            val configuration = SteamConfiguration.create {
                it.withProtocolTypes(PROTOCOL_TYPES)
                it.withCellID(PrefManager.cellId)
                it.withServerListProvider(FileServerListProvider(File(serverListPath)))
                it.withConnectionTimeout(60000L)
                it.withHttpClient(
                    OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)   // Time to establish connection
                        .readTimeout(60, TimeUnit.SECONDS)      // Max inactivity between reads
                        .writeTimeout(30, TimeUnit.SECONDS)     // Time for writes
                        .build(),
                )
            }

            // create our steam client instance
            steamClient = SteamClient(configuration).apply {
                // remove callbacks we're not using.
                removeHandler(SteamGameServer::class.java)
                removeHandler(SteamMasterServer::class.java)
                removeHandler(SteamWorkshop::class.java)
                removeHandler(SteamScreenshots::class.java)
                removeHandler(SteamUserStats::class.java)
            }

            // create the callback manager which will route callbacks to function calls
            callbackManager = CallbackManager(steamClient!!)

            // get the different handlers to be used throughout the service
            _steamUser = steamClient!!.getHandler(SteamUser::class.java)
            _steamApps = steamClient!!.getHandler(SteamApps::class.java)
            _steamFriends = steamClient!!.getHandler(SteamFriends::class.java)
            _steamCloud = steamClient!!.getHandler(SteamCloud::class.java)

            _unifiedFriends = SteamUnifiedFriends(this)
            _steamFamilyGroups = steamClient!!.getHandler<SteamUnifiedMessages>()!!.createService<FamilyGroups>()

            // subscribe to the callbacks we are interested in
            with(callbackSubscriptions) {
                with(callbackManager!!) {
                    add(subscribe(ConnectedCallback::class.java, ::onConnected))
                    add(subscribe(DisconnectedCallback::class.java, ::onDisconnected))
                    add(subscribe(LoggedOnCallback::class.java, ::onLoggedOn))
                    add(subscribe(LoggedOffCallback::class.java, ::onLoggedOff))
                    add(subscribe(PersonaStateCallback::class.java, ::onPersonaStateReceived))
                    add(subscribe(LicenseListCallback::class.java, ::onLicenseList))
                    add(subscribe(PlayingSessionStateCallback::class.java, ::onPlayingSessionState))
                }
            }

            isRunning = true

            // we should use Dispatchers.IO here since we are running a sleeping/blocking function
            // "The idea is that the IO dispatcher spends a lot of time waiting (IO blocked),
            // while the Default dispatcher is intended for CPU intensive tasks, where there
            // is little or no sleep."
            // source: https://stackoverflow.com/a/59040920
            scope.launch {
                while (isRunning) {
                    // logD("runWaitCallbacks")

                    try {
                        callbackManager!!.runWaitCallbacks(1000L)
                    } catch (e: Exception) {
                        Timber.e("runWaitCallbacks failed: $e")
                    }
                }
            }

            connectToSteam()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Persist download progress for all active downloads
        // This is a safety net for OS kills (unlikely but possible)
        downloadJobs.values.forEach { downloadInfo ->
            downloadInfo.persistProgressSnapshot(force = true)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel()

        // Unregister Wi-Fi connectivity callback
        connectivityManager.unregisterNetworkCallback(networkCallback)

        scope.launch { stop() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectToSteam() {
        scope.launch {
            while (isRunning && !isConnected) {
                Timber.d("connectToSteam: attempting connection...")
                try {
                    steamClient!!.connect()
                    
                    // Wait for connection for up to 10 seconds
                    var waitAttempts = 0
                    while (!isConnected && waitAttempts < 10) {
                        delay(1000)
                        waitAttempts++
                    }
                    
                    if (!isConnected) {
                        Timber.w("Failed to connect to Steam, marking endpoint bad and force disconnecting")
                        try {
                            steamClient!!.servers.tryMark(steamClient!!.currentEndpoint, PROTOCOL_TYPES, ServerQuality.BAD)
                        } catch (e: Exception) {}
                        
                        try {
                            steamClient!!.disconnect()
                        } catch (e: Exception) {}
                        
                        // Wait before retrying
                        delay(5000)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during connectToSteam")
                    delay(5000)
                }
            }
        }
    }

    private suspend fun stop() {
        Timber.i("Stopping Steam service")
        if (steamClient != null && steamClient!!.isConnected) {
            isStopping = true

            steamClient!!.disconnect()

            while (isStopping) {
                delay(200L)
            }

            // the reason we don't clearValues() here is because the onDisconnect
            // callback does it for us
        } else {
            clearValues()
        }
    }

    private fun clearValues() {
        _loginResult = LoginResult.Failed
        isRunning = false
        isConnected = false
        isLoggingOut = false
        isWaitingForQRAuth = false

        steamClient = null
        _steamUser = null
        _steamApps = null
        _steamFriends = null
        _steamCloud = null

        callbackSubscriptions.forEach { it.close() }
        callbackSubscriptions.clear()
        callbackManager = null

        _unifiedFriends?.close()
        _unifiedFriends = null

        isStopping = false
        retryAttempt = 0

        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)
        PluviaApp.events.clearAllListenersOf<SteamEvent<Any>>()

        LogManager.removeListener(logger)
    }

    private fun reconnect() {
        notificationHelper.notify("Retrying...")

        isConnected = false

        val event = SteamEvent.Disconnected
        PluviaApp.events.emit(event)

        steamClient!!.disconnect()
    }

    // region [REGION] callbacks
    @Suppress("UNUSED_PARAMETER", "unused")
    private fun onConnected(callback: ConnectedCallback) {
        Timber.i("Connected to Steam")

        retryAttempt = 0
        isConnected = true

        var isAutoLoggingIn = false

        if (PrefManager.username.isNotEmpty() && PrefManager.refreshToken.isNotEmpty()) {
            isAutoLoggingIn = true

            login(
                username = PrefManager.username,
                refreshToken = PrefManager.refreshToken,
                rememberSession = true,
            )
        }

        val event = SteamEvent.Connected(isAutoLoggingIn)
        PluviaApp.events.emit(event)
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        Timber.i("Disconnected from Steam. User initiated: ${callback.isUserInitiated}")

        isConnected = false

        if (!isStopping && retryAttempt < MAX_RETRY_ATTEMPTS) {
            retryAttempt++

            Timber.w("Attempting to reconnect (retry $retryAttempt)")

            // isLoggingOut = false
            val event = SteamEvent.RemotelyDisconnected
            PluviaApp.events.emit(event)

            connectToSteam()
        } else {
            val event = SteamEvent.Disconnected
            PluviaApp.events.emit(event)

            clearValues()

            stopSelf()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun onLoggedOn(callback: LoggedOnCallback) {
        Timber.i("Logged onto Steam: ${callback.result}")
        
        if (callback.result == EResult.OK) {
            isLoggingOut = false
            _isLoggedInFlow.value = true
        } else {
            _isLoggedInFlow.value = false
        }

        if (userSteamId?.isValid == true) {
            if (PrefManager.steamUserAccountId != userSteamId!!.accountID.toInt()) {
                PrefManager.steamUserAccountId = userSteamId!!.accountID.toInt()
                Timber.d("Saving logged in Steam accountID ${userSteamId!!.accountID.toInt()}")
            }
            val steamId64 = userSteamId!!.convertToUInt64()
            if (PrefManager.steamUserSteamId64 != steamId64) {
                PrefManager.steamUserSteamId64 = steamId64
                Timber.d("Saving logged in Steam ID64 $steamId64")
            }
        }

        when (callback.result) {
            EResult.TryAnotherCM -> {
                _loginResult = LoginResult.Failed
                reconnect()
            }

            EResult.OK -> {
                // Steam's GeoIP-based cellID is often wrong (e.g. returning Romania for US users).
                // Only log it for debugging; keep cellId at 0 ("Automatic") unless the user
                // manually picks a server in Settings > Stores > Download Server.
                Timber.d("Steam suggested cellID: ${callback.cellID} (not auto-saving, using Automatic unless manually set)")

                // retrieve persona data of logged in user
                scope.launch { requestUserPersona() }

                // Request family share info if we have a familyGroupId.
                if (callback.familyGroupId != 0L) {
                    scope.launch {
                        val request = SteammessagesFamilygroupsSteamclient.CFamilyGroups_GetFamilyGroup_Request.newBuilder().apply {
                            familyGroupid = callback.familyGroupId
                        }.build()

                        _steamFamilyGroups!!.getFamilyGroup(request).await().let {
                            if (it.result != EResult.OK) {
                                Timber.w("An error occurred loading family group info.")
                                return@launch
                            }

                            val response = it.body

                            Timber.i("Found family share: ${response.name}, with ${response.membersCount} members.")

                            response.membersList.forEach { member ->
                                val accountID = SteamID(member.steamid).accountID.toInt()
                                familyGroupMembers.add(accountID)
                            }
                        }
                    }
                }

                picsChangesCheckerJob = continuousPICSChangesChecker()
                picsGetProductInfoJob = continuousPICSGetProductInfo()

                // Tell steam we're online, this allows friends to update.
                _steamFriends?.setPersonaState(EPersonaState.from(PrefManager.personaState) ?: EPersonaState.Online)

                notificationHelper.notify("Connected")

                _loginResult = LoginResult.Success
            }

            else -> {
                clearUserData()

                _loginResult = LoginResult.Failed

                reconnect()
            }
        }

        val event = SteamEvent.LogonEnded(PrefManager.username, _loginResult)
        PluviaApp.events.emit(event)
    }

    private fun onLoggedOff(callback: LoggedOffCallback) {
        Timber.i("Logged off of Steam: ${callback.result}")

        _isLoggedInFlow.value = false

        notificationHelper.notify("Disconnected...")

        if (isLoggingOut || callback.result == EResult.LogonSessionReplaced) {
            // logOut() already handled cleanup; just stop the service.
            if (!isLoggingOut) performLogOffDuties()

            scope.launch { stop() }
        } else if (callback.result == EResult.LoggedInElsewhere) {
            // received when a client runs an app and wants to forcibly close another
            // client running an app
            val event = SteamEvent.ForceCloseApp
            PluviaApp.events.emit(event)

            reconnect()
        } else {
            reconnect()
        }
    }

    private fun onPlayingSessionState(callback: PlayingSessionStateCallback) {
        Timber.d("onPlayingSessionState called with isPlayingBlocked = " + callback.isPlayingBlocked)
        _isPlayingBlocked.value = callback.isPlayingBlocked
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun onPersonaStateReceived(callback: PersonaStateCallback) {
        // Ignore accounts that arent individuals
        if (!callback.friendId.isIndividualAccount) {
            return
        }

        // Ignore states where the name is blank.
        if (callback.playerName.isEmpty()) {
            return
        }

        // Timber.d("Persona state received: ${callback.name}")

        // Capture steamClient before launching the coroutine to avoid a race condition where
        // steamClient is nulled out (service stopped) by the time the coroutine runs inside
        // db.withTransaction, which would cause a NullPointerException.
        val localSteamClient = steamClient ?: return

        scope.launch {
            db.withTransaction {
                // Send off an event if we change states.
                if (callback.friendId == localSteamClient.steamID) {
                    Timber.d("Local persona state received: ${callback.playerName}")

                    val avatarHash = callback.avatarHash.toHexString()
                    val playerName = callback.playerName

                    // Update local state flow
                    _localPersona.update {
                        it.copy(
                            avatarHash = avatarHash,
                            name = playerName,
                            state = callback.personaState ?: EPersonaState.Offline,
                            gameAppID = callback.gamePlayedAppId,
                            gameName = appDao.findApp(callback.gamePlayedAppId)?.name ?: callback.gameName,
                        )
                    }

                    // Cache local persona
                    PrefManager.steamUserAvatarHash = avatarHash
                    PrefManager.steamUserName = playerName

                    val event = SteamEvent.PersonaStateReceived(localPersona.value)
                    PluviaApp.events.emit(event)
                }
            }
        }
    }

    private fun onLicenseList(callback: LicenseListCallback) {
        if (callback.result != EResult.OK) {
            Timber.w("Failed to get License list")
            return
        }

        Timber.i("Received License List ${callback.result}, size: ${callback.licenseList.size}")

        scope.launch {
            db.withTransaction {
                // Note: I assume with every launch we do, in fact, update the licenses for app the apps if we join or get removed
                //      from family sharing... We really can't test this as there is a 1-year cooldown.
                //      Then 'findStaleLicences' will find these now invalid items to remove.

                // Store raw licenses for DepotDownloader - each license in its own row
                licenses = callback.licenseList
                cachedLicenseDao.deleteAll()
                val cachedLicenses = callback.licenseList.map { license ->
                    CachedLicense(licenseJson = LicenseSerializer.serializeLicense(license))
                }
                cachedLicenseDao.insertAll(cachedLicenses)

                val licensesToAdd = callback.licenseList
                    .groupBy { it.packageID }
                    .map { licensesEntry ->
                        val preferred = licensesEntry.value.firstOrNull {
                            it.ownerAccountID == userSteamId?.accountID?.toInt()
                        } ?: licensesEntry.value.first()
                        SteamLicense(
                            packageId = licensesEntry.key,
                            lastChangeNumber = preferred.lastChangeNumber,
                            timeCreated = preferred.timeCreated,
                            timeNextProcess = preferred.timeNextProcess,
                            minuteLimit = preferred.minuteLimit,
                            minutesUsed = preferred.minutesUsed,
                            paymentMethod = preferred.paymentMethod,
                            licenseFlags = licensesEntry.value
                                .map { it.licenseFlags }
                                .reduceOrNull { first, second ->
                                    val combined = EnumSet.copyOf(first)
                                    combined.addAll(second)
                                    combined
                                } ?: EnumSet.noneOf(ELicenseFlags::class.java),
                            purchaseCode = preferred.purchaseCode,
                            licenseType = preferred.licenseType,
                            territoryCode = preferred.territoryCode,
                            accessToken = preferred.accessToken,
                            ownerAccountId = licensesEntry.value.map { it.ownerAccountID }, // Read note above
                            masterPackageID = preferred.masterPackageID,
                        )
                    }

                if (licensesToAdd.isNotEmpty()) {
                    Timber.i("Adding ${licensesToAdd.size} licenses")
                    licenseDao.insertAll(licensesToAdd)
                }

                val licensesToRemove = licenseDao.findStaleLicences(
                    packageIds = callback.licenseList.map { it.packageID },
                )
                if (licensesToRemove.isNotEmpty()) {
                    Timber.i("Removing ${licensesToRemove.size} (stale) licenses")
                    val packageIds = licensesToRemove.map { it.packageId }
                    licenseDao.deleteStaleLicenses(packageIds)
                }

                // Get PICS information with the current license database.
                licenseDao.getAllLicenses()
                    .map { PICSRequest(it.packageId, it.accessToken) }
                    .chunked(MAX_PICS_BUFFER)
                    .forEach { chunk ->
                        Timber.d("onLicenseList: Queueing ${chunk.size} package(s) for PICS")
                        packagePicsChannel.send(chunk)
                    }
            }
        }
    }

    override fun onChanged(qrAuthSession: QrAuthSession?) {
        qrAuthSession?.let { qr ->
            if (!BuildConfig.DEBUG) {
                Timber.d("QR code changed -> ${qr.challengeUrl}")
            }

            val event = SteamEvent.QrChallengeReceived(qr.challengeUrl)
            PluviaApp.events.emit(event)
        } ?: run { Timber.w("QR challenge url was null") }
    }
    // endregion

    /**
     * Request changes for apps and packages since a given change number.
     * Checks every [PICS_CHANGE_CHECK_DELAY] seconds.
     * Results are returned in a [PICSChangesCallback]
     */
    private fun continuousPICSChangesChecker(): Job = scope.launch {
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
                val changesSince = _steamApps!!.picsGetChangesSince(
                    lastChangeNumber = PrefManager.lastPICSChangeNumber,
                    sendAppChangeList = true,
                    sendPackageChangelist = true,
                ).await()

                if (PrefManager.lastPICSChangeNumber == changesSince.currentChangeNumber) {
                    Timber.w("Change number was the same as last change number, skipping")
                    return@launch
                }

                // Set our last change number
                PrefManager.lastPICSChangeNumber = changesSince.currentChangeNumber

                Timber.d(
                    "picsGetChangesSince:" +
                            "\n\tlastChangeNumber: ${changesSince.lastChangeNumber}" +
                            "\n\tcurrentChangeNumber: ${changesSince.currentChangeNumber}" +
                            "\n\tisRequiresFullUpdate: ${changesSince.isRequiresFullUpdate}" +
                            "\n\tisRequiresFullAppUpdate: ${changesSince.isRequiresFullAppUpdate}" +
                            "\n\tisRequiresFullPackageUpdate: ${changesSince.isRequiresFullPackageUpdate}" +
                            "\n\tappChangesCount: ${changesSince.appChanges.size}" +
                            "\n\tpkgChangesCount: ${changesSince.packageChanges.size}",

                    )

                // Process any app changes
                launch {
                    changesSince.appChanges.values
                        .filter { changeData ->
                            // only queue PICS requests for apps existing in the db that have changed
                            val app = appDao.findApp(changeData.id) ?: return@filter false
                            changeData.changeNumber != app.lastChangeNumber
                        }
                        .map { PICSRequest(id = it.id) }
                        .chunked(MAX_PICS_BUFFER)
                        .forEach { chunk ->
                            ensureActive()
                            Timber.d("onPicsChanges: Queueing ${chunk.size} app(s) for PICS")
                            appPicsChannel.send(chunk)
                        }
                }

                // Process any package changes
                launch {
                    val pkgsWithChanges = changesSince.packageChanges.values
                        .filter { changeData ->
                            // only queue PICS requests for pkgs existing in the db that have changed
                            val pkg = licenseDao.findLicense(changeData.id) ?: return@filter false
                            changeData.changeNumber != pkg.lastChangeNumber
                        }

                    if (pkgsWithChanges.isNotEmpty()) {
                        val pkgsForAccessTokens = pkgsWithChanges.filter { it.isNeedsToken }.map { it.id }

                        val accessTokens = _steamApps?.picsGetAccessTokens(emptyList(), pkgsForAccessTokens)
                            ?.await()?.packageTokens ?: emptyMap()

                        ensureActive()

                        pkgsWithChanges
                            .map { PICSRequest(it.id, accessTokens[it.id] ?: 0) }
                            .chunked(MAX_PICS_BUFFER)
                            .forEach { chunk ->
                                Timber.d("onPicsChanges: Queueing ${chunk.size} package(s) for PICS")
                                packagePicsChannel.send(chunk)
                            }
                    }
                }
            } catch (e: NullPointerException) {
                Timber.w("No lastPICSChangeNumber, skipping")
            } catch (e: AsyncJobFailedException) {
                Timber.w("AsyncJobFailedException, skipping")
            }
        }
    }

    /**
     * A buffered flow to parse so many PICS requests in a given moment.
     */
    private fun continuousPICSGetProductInfo(): Job = scope.launch {
        // Launch both coroutines within this parent job
        launch {
            appPicsChannel.receiveAsFlow()
                .filter { it.isNotEmpty() }
                .buffer(capacity = MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
                .collect { appRequests ->
                    Timber.d("Processing ${appRequests.size} app PICS requests")

                    ensureActive()
                    if (!isLoggedIn) return@collect
                    val steamApps = instance?._steamApps ?: return@collect

                    val callback = steamApps.picsGetProductInfo(
                        apps = appRequests,
                        packages = emptyList(),
                    ).await()

                    callback.results.forEachIndexed { index, picsCallback ->
                        Timber.d(
                            "onPicsProduct: ${index + 1} of ${callback.results.size}" +
                                    "\n\tReceived PICS result of ${picsCallback.apps.size} app(s)." +
                                    "\n\tReceived PICS result of ${picsCallback.packages.size} package(s).",
                        )

                        ensureActive()
                        val steamAppsMap = picsCallback.apps.values.mapNotNull { app ->
                            val appFromDb = appDao.findApp(app.id)
                            val packageId = appFromDb?.packageId ?: INVALID_PKG_ID
                            val packageFromDb = if (packageId != INVALID_PKG_ID) licenseDao.findLicense(packageId) else null
                            val ownerAccountId = packageFromDb?.ownerAccountId ?: emptyList()

                            // Apps with -1 for the ownerAccountId should be added.
                            //  This can help with friend game names.

                            // TODO maybe apps with -1 for the ownerAccountId can be stripped with necessities and name.

                            if (app.changeNumber != appFromDb?.lastChangeNumber) {
                                // Preserve custom install path if it exists (full absolute path)
                                val existingInstallDir = appFromDb?.installDir.orEmpty()
                                val preserveInstallDir = existingInstallDir.isNotEmpty() &&
                                    (existingInstallDir.startsWith("/") || existingInstallDir.contains(File.separator))

                                app.keyValues.generateSteamApp().copy(
                                    packageId = packageId,
                                    ownerAccountId = ownerAccountId,
                                    receivedPICS = true,
                                    lastChangeNumber = app.changeNumber,
                                    licenseFlags = packageFromDb?.licenseFlags ?: EnumSet.noneOf(ELicenseFlags::class.java),
                                    installDir = if (preserveInstallDir) existingInstallDir else app.keyValues.generateSteamApp().installDir,
                                )
                            } else {
                                null
                            }
                        }

                        if (steamAppsMap.isNotEmpty()) {
                            Timber.i("Inserting ${steamAppsMap.size} PICS apps to database")
                            db.withTransaction {
                                appDao.insertAll(steamAppsMap)
                            }
                        }
                    }
                }
        }

        launch {
            packagePicsChannel.receiveAsFlow()
                .filter { it.isNotEmpty() }
                .buffer(capacity = MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
                .collect { packageRequests ->
                    Timber.d("Processing ${packageRequests.size} package PICS requests")

                    ensureActive()
                    if (!isLoggedIn) return@collect
                    val steamApps = instance?._steamApps ?: return@collect

                    val callback = steamApps.picsGetProductInfo(
                        apps = emptyList(),
                        packages = packageRequests,
                    ).await()

                    callback.results.forEach { picsCallback ->
                        // Don't race the queue.
                        if (!isLoggedIn) return@collect
                        val queue = Collections.synchronizedList(mutableListOf<Int>())

                        db.withTransaction {
                            picsCallback.packages.values.forEach { pkg ->
                                val appIds = pkg.keyValues["appids"].children.map { it.asInteger() }
                                licenseDao.updateApps(pkg.id, appIds)

                                val depotIds = pkg.keyValues["depotids"].children.map { it.asInteger() }
                                licenseDao.updateDepots(pkg.id, depotIds)

                                // Insert a stub row (or update) of SteamApps to the database.
                                appIds.forEach { appid ->
                                    val steamApp = appDao.findApp(appid)?.copy(packageId = pkg.id)
                                    if (steamApp != null) {
                                        appDao.update(steamApp)
                                    } else {
                                        val stubSteamApp = SteamApp(id = appid, packageId = pkg.id)
                                        appDao.insert(stubSteamApp)
                                    }
                                }

                                queue.addAll(appIds)
                            }
                        }

                        try {
                            // TODO: This could be an issue. (Stalling)
                            steamApps.picsGetAccessTokens(
                                appIds = queue,
                                packageIds = emptyList(),
                            ).await()
                                .appTokens
                                .forEach { (key, value) ->
                                    appTokens[key] = value
                                }

                            // Get PICS information with the app ids.
                            queue
                                .map { PICSRequest(id = it, accessToken = appTokens[it] ?: 0L) }
                                .chunked(MAX_PICS_BUFFER)
                                .forEach { chunk ->
                                    Timber.d("bufferedPICSGetProductInfo: Queueing ${chunk.size} for PICS")
                                    appPicsChannel.send(chunk)
                                }
                        } catch (e: AsyncJobFailedException) {
                            Timber.w("Could not get PICS product info $e")
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

            // Request new ticket from Steam
            val steamApps = instance?._steamApps ?: null
            val response = try {
                withTimeout(5_000) {
                    steamApps?.requestEncryptedAppTicket(appId)?.await()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to request encrypted app ticket for app $appId")
                return null
            }

            if (response?.result != EResult.OK || response.encryptedAppTicket == null) {
                Timber.w("Failed to get encrypted app ticket for app $appId: ${response?.result}")
                return null
            }

            // Extract all fields from the protobuf message
            val ticketProto = response.encryptedAppTicket
            val ticket = EncryptedAppTicket(
                appId = appId,
                result = response.result.code(),
                ticketVersionNo = ticketProto!!.ticketVersionNo.toInt(),
                crcEncryptedTicket = ticketProto.crcEncryptedticket.toInt(),
                cbEncryptedUserData = ticketProto.cbEncrypteduserdata.toInt(),
                cbEncryptedAppOwnershipTicket = ticketProto.cbEncryptedAppownershipticket.toInt(),
                encryptedTicket = ticketProto.toByteArray(),
                timestamp = now,
            )

            // Store in database
            encryptedAppTicketDao.insert(ticket)
            Timber.d("Stored new encrypted app ticket protobuf for app $appId")

            ticket.encryptedTicket
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
