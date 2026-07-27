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

// Download-queue control, resume/cleanup helpers + sync flags, split out of SteamService.kt (behavior-identical).

// Overlay the real unlock state onto schema-derived achievement definitions.
internal suspend fun SteamService.Companion.mergeAchievementUnlockState(
    appId: Int,
    achievements: List<com.winlator.cmod.feature.stores.steam.statsgen.Achievement>,
    nameToBlockBit: Map<String, Pair<Int, Int>>,
): List<com.winlator.cmod.feature.stores.steam.statsgen.Achievement> {
    if (achievements.isEmpty() || nameToBlockBit.isEmpty()) return achievements
    val statsJson = withWnSession { s -> s.getUserStatsFull(appId) } ?: return achievements
    val blockUnlock = HashMap<Int, List<Long>>()
    runCatching {
        val obj = JSONObject(statsJson)
        if (obj.optInt("eresult", 2) != EResult.OK.code()) return achievements
        val blocks = obj.optJSONArray("achievementBlocks") ?: return achievements
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            val times = b.optJSONArray("unlockTimes")
            val list = ArrayList<Long>(times?.length() ?: 0)
            for (j in 0 until (times?.length() ?: 0)) list.add(times!!.getLong(j))
            blockUnlock[b.optInt("achievementId")] = list
        }
    }
    if (blockUnlock.isEmpty()) return achievements
    val unlockedTotal = blockUnlock.values.sumOf { times -> times.count { it != 0L } }
    Timber.i("Achievements: app=$appId merged unlock state ($unlockedTotal unlocked across ${blockUnlock.size} blocks)")
    return achievements.map { ach ->
        val mapped = nameToBlockBit[ach.name] ?: return@map ach
        val t = blockUnlock[mapped.first]?.getOrNull(mapped.second) ?: 0L
        if (t != 0L) ach.copy(unlocked = true, unlockTimestamp = t.toInt()) else ach.copy(unlocked = false)
    }
}

internal fun SteamService.Companion.downloadUrlsFor(fileName: String): List<String> {
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

internal fun SteamService.Companion.notifyDownloadStarted(appId: Int) {
    PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, true))
}

internal fun SteamService.Companion.notifyDownloadStopped(appId: Int) {
    PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
}

internal fun SteamService.Companion.removeDownloadJob(
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

internal fun SteamService.Companion.clearCompletedDownloadsInternal(dispatchQueueAfterClear: Boolean) {
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
internal fun SteamService.Companion.hasPartialDownloadFiles(appDirPath: String): Boolean {
    val appDir = File(appDirPath)
    if (!appDir.exists()) return false

    val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
    if (persistenceFile.exists() && persistenceFile.length() > 0L) {
        return true
    }

    // Complete marker present and no persisted resume file → fully installed, not a resumable partial.
    if (MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)) {
        return false
    }

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

internal fun SteamService.Companion.inferResumeDlcAppIds(
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

internal fun SteamService.Companion.hasPersistedDepotResumeMetadata(appDirPath: String): Boolean {
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

internal fun SteamService.Companion.clearPersistedProgressSnapshot(appDirPath: String) {
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

internal fun SteamService.Companion.clearFailedResumeState(appId: Int) {
    val appDirPath = getAppDirPath(appId)
    clearPersistedProgressSnapshot(appDirPath)
    runBlocking(Dispatchers.IO) {
        instance?.downloadingAppInfoDao?.deleteApp(appId)
    }
}

internal fun SteamService.Companion.deleteRecursivelyWithRetries(
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

internal fun SteamService.Companion.cleanupSteamAppCacheDirs(appId: Int) {
    StoreArtworkCache.deleteGame(PluviaApp.instance, "steam", appId.toString())
    steamAppCacheDirs(appId).forEach { dir ->
        if (!dir.exists()) return@forEach
        Timber.i("Deleting Steam cache folder for appId $appId: ${dir.absolutePath}")
        if (!deleteRecursivelyWithRetries(dir)) {
            Timber.w("Failed to fully delete Steam cache folder for appId $appId: ${dir.absolutePath}")
        }
    }
}

internal fun SteamService.Companion.steamAppCacheDirs(appId: Int): List<File> {
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

internal fun SteamService.Companion.steamProtectedInstallRoots(): List<String> =
    listOf(
        internalAppInstallPath,
        externalAppInstallPath,
        defaultAppInstallPath,
    ).filter { it.isNotBlank() }.distinct()


internal fun SteamService.Companion.getSyncFlag(appId: Int): AtomicBoolean {
    val existing = syncInProgressApps[appId]
    if (existing != null) {
        return existing
    }
    val created = AtomicBoolean(false)
    val prior = syncInProgressApps.putIfAbsent(appId, created)
    return prior ?: created
}

internal fun SteamService.Companion.tryAcquireSync(appId: Int): Boolean {
    val flag = getSyncFlag(appId)
    return flag.compareAndSet(false, true)
}

internal fun SteamService.Companion.releaseSync(appId: Int) {
    val flag = syncInProgressApps[appId]
    flag?.set(false)
    if (flag != null && !flag.get()) {
        syncInProgressApps.remove(appId, flag)
    }
}
