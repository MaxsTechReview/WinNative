package com.winlator.cmod.feature.stores.steam.service
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
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
import com.winlator.cmod.feature.stores.steam.data.WnDownloadTransientException
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
import com.winlator.cmod.feature.stores.steam.data.SteamFriendEntry
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
import com.winlator.cmod.feature.stores.common.StoreArtworkCache
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
import kotlinx.coroutines.sync.withPermit
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
import com.winlator.cmod.runtime.system.SessionKeepAliveService
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

// Install-path/shortcut helpers + download-request gating, split out of SteamService.kt (behavior-identical).

internal fun SteamService.Companion.isSuspiciousSteamInstallDirLeaf(value: String): Boolean {
    val normalized = value.trim().replace('\\', '/').trimEnd('/')
    if (normalized.isEmpty()) return false
    val leaf = normalized.substringAfterLast('/')
    return leaf.equals("common", ignoreCase = true) ||
        leaf.equals("steamapps", ignoreCase = true)
}

internal fun SteamService.Companion.isSuspiciousSteamInstallPath(path: String): Boolean {
    val normalized = path.trim().replace('\\', '/').trimEnd('/')
    if (normalized.isEmpty()) return false
    return normalized.endsWith("/steamapps/common", ignoreCase = true) ||
        normalized.endsWith("/steamapps", ignoreCase = true)
}

internal fun SteamService.Companion.normalizeInstallPath(path: String): String {
    if (path.isBlank()) return path
    return try {
        File(path).canonicalPath
    } catch (_: IOException) {
        File(path).absolutePath
    }
}

internal fun SteamService.Companion.createSteamShortcut(
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

        // Skip if present — rewriting on every verify/update wiped per-game [Extra Data] (wine version, dxwrapper, env vars, cover art).
        if (shortcutFile.exists() && shortcutFile.length() > 0L) {
            Timber.i(
                "Steam shortcut already exists for appId=$appId (${appInfo.name}); " +
                    "preserving existing per-game settings.",
            )
            return
        }

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

internal fun SteamService.Companion.resolveInstalledDlcIdsForUpdateOrVerify(appId: Int): List<Int> {
    val dlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toMutableList()

    getDownloadableDlcAppsOf(appId)?.forEach { dlcApp ->
        val installedDlcApp = getInstalledApp(dlcApp.id)
        if (installedDlcApp != null) {
            dlcAppIds.add(installedDlcApp.id)
        }
    }

    return dlcAppIds.distinct()
}

internal fun SteamService.Companion.parseDownloadScopeIds(scope: String): Set<Int> =
    scope
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()


internal fun SteamService.Companion.activeDownloadRecordFor(appId: Int): DownloadRecord? =
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


internal fun SteamService.Companion.rejectConflictingDownloadRequest(appId: Int, record: DownloadRecord): DownloadInfo? {
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
    // Return null so callers can tell the request was rejected; returning the pre-existing job would let a verify/update pop-up latch onto an unrelated in-flight download and mislabel it.
    return null
}
