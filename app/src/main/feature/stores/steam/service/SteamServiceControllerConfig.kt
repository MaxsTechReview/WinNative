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

// Steam Input / controller-config parsing helpers, split out of SteamService.kt (behavior-identical).

internal fun SteamService.Companion.selectSteamControllerConfig(details: List<SteamControllerConfigDetail>): SteamControllerConfigDetail? {
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

internal fun SteamService.Companion.resolveSteamInputManifestFile(
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

internal fun SteamService.Companion.loadConfigFromManifest(manifestFile: File): String? {
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

internal fun SteamService.Companion.parseManifestForConfig(
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

internal fun SteamService.Companion.resolvePathCaseInsensitive(
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

internal fun SteamService.Companion.readBuiltInSteamInputTemplate(fileName: String): String? {
    val assets = instance?.assets ?: return null
    return runCatching {
        assets.open("steaminput/$fileName").use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}

internal fun SteamService.Companion.readDownloadedSteamInputTemplate(appId: Int): String? {
    val configFile = File(getAppDirPath(appId), STEAM_CONTROLLER_CONFIG_FILENAME)
    if (!configFile.exists()) return null
    return configFile.readText(Charsets.UTF_8)
}
