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

// Launch-state priming + libsteamclient/achievements/workshop helpers, split out of SteamService.kt (behavior-identical).

internal fun SteamService.Companion.resolvePreferredLaunchBuildId(
    app: SteamApp?,
    branch: String,
): Int {
    val buildId =
        app?.branches?.get(branch)?.buildId
            ?: app?.branches?.get(STEAM_DEFAULT_BRANCH)?.buildId
            ?: app?.branches?.values?.firstOrNull()?.buildId
            ?: 0L
    return buildId.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Build id of the content actually on disk. Falls back to the branch's current build id only for
 * installs made before the installed build id was recorded, so a stale install is never reported
 * to a game as if it were the newest build.
 */
internal fun SteamService.Companion.resolveInstalledBuildId(
    appId: Int,
    branch: String,
): Int {
    val buildId =
        SteamBranchSelection.installedBuildId(
            recorded = getInstalledBuildId(appId),
            branches = getAppInfoOf(appId)?.branches.orEmpty(),
            branch = branch,
        )
    if (buildId > 0L) return buildId.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return resolvePreferredLaunchBuildId(getAppInfoOf(appId), branch)
}

internal fun SteamService.Companion.resolvePreferredLaunchDepotIds(
    appId: Int,
    branch: String,
    preferredLanguage: String = PrefManager.containerLanguage,
): IntArray {
    val trustedInstalledDepots =
        getInstalledApp(appId)
            ?.downloadedDepots
            .orEmpty()
            .asSequence()
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()
    if (trustedInstalledDepots.isNotEmpty()) {
        return trustedInstalledDepots.toIntArray()
    }

    val installedDlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty()
    val fallbackSelectedDepots =
        getSelectedDownloadDepots(
            appId = appId,
            userSelectedDlcAppIds = installedDlcAppIds,
            preferredLanguage = preferredLanguage,
            branch = branch,
        ).keys
            .asSequence()
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()
    if (fallbackSelectedDepots.isNotEmpty()) {
        Timber.w(
            "resolvePreferredLaunchDepotIds: appId=$appId branch=$branch " +
                "had no trusted depot snapshot; using ${fallbackSelectedDepots.size} selected depot(s)",
        )
        return fallbackSelectedDepots.toIntArray()
    }

    return IntArray(0)
}

internal suspend fun SteamService.Companion.primeLibSteamClientLaunchState(
    appId: Int,
    selectedBranch: String,
): Boolean {
    val svc = instance ?: return false
    val ctx = svc.applicationContext
    val libSteamClient = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
    if (!libSteamClient.ensureLoaded(ctx)) {
        Timber.w("primeLibSteamClientLaunchState: failed to load bridge library for appId=$appId")
        return false
    }
    libSteamClient.seedFromPrefManager(ctx)

    val manifestBranch = selectedBranch.ifBlank { STEAM_DEFAULT_BRANCH }
    val app = withContext(Dispatchers.IO) { svc.appDao.findApp(appId) }
    val rawInstalledApp = withContext(Dispatchers.IO) { svc.appInfoDao.getInstalledApp(appId) }
    val ownedIds = withContext(Dispatchers.IO) { svc.appDao.getAllAppIds() }
    val installedIds =
        withContext(Dispatchers.IO) { svc.appInfoDao.getAllInstalledAppIds().toMutableSet() }
            .apply {
                if (rawInstalledApp?.isDownloaded == true) {
                    add(appId)
                }
            }

    libSteamClient.setAppId(appId)
    if (ownedIds.isNotEmpty()) {
        libSteamClient.setOwnedApps(ownedIds.toIntArray())
    }
    if (installedIds.isNotEmpty()) {
        libSteamClient.setInstalledApps(installedIds.sorted().toIntArray())
    }

    app?.name?.takeIf { it.isNotBlank() }?.let { appName ->
        libSteamClient.setAppNames(intArrayOf(appId), arrayOf(appName))
    }

    val installDir = runCatching { getAppDirPath(appId) }.getOrNull()
    if (!installDir.isNullOrEmpty()) {
        libSteamClient.setAppInstallDir(appId, installDir)
    }

    val buildId = resolveInstalledBuildId(appId, manifestBranch)
    if (buildId > 0) {
        libSteamClient.setAppBuildId(appId, buildId)
    }

    val depotIds = resolvePreferredLaunchDepotIds(appId, manifestBranch)
    if (depotIds.isNotEmpty()) {
        libSteamClient.setAppInstalledDepots(appId, depotIds)
    }

    app?.packageId
        ?.takeIf { it != INVALID_PKG_ID }
        ?.let { packageId ->
            libSteamClient.setAppSourcePackages(appId, intArrayOf(packageId))
        }

    val accountId = runCatching {
        com.winlator.cmod.feature.stores.steam.utils
            .SteamUtils.getSteam3AccountId().toLong()
    }.getOrNull() ?: 0L
    if (accountId > 0L) {
        val remoteDir = runCatching {
            com.winlator.cmod.feature.stores.steam.enums
                .PathType.SteamUserData.toAbsPath(
                    svc,
                    appId,
                    accountId,
                )
        }.getOrNull()
        if (!remoteDir.isNullOrEmpty()) {
            libSteamClient.setAppCloudRemoteDir(appId, remoteDir)
        }
    }

    libSteamClient.setAppCurrentBeta(appId, selectedBranch)
    Timber.i(
        "primeLibSteamClientLaunchState: app=$appId branch=$manifestBranch " +
            "buildId=$buildId depots=${depotIds.size} owned=${ownedIds.size} installed=${installedIds.size}",
    )
    return true
}

internal fun SteamService.Companion.pushAchievementSchemaToLibSteamClient(
    appId: Int,
    achievements: List<com.winlator.cmod.feature.stores.steam.statsgen.Achievement>,
    stats: List<com.winlator.cmod.feature.stores.steam.statsgen.Stat>,
    nameToBlockBit: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    val locale = PrefManager.containerLanguage.ifBlank { "english" }
    fun pick(map: Map<String, String>?): String =
        map?.get(locale) ?: map?.get("english") ?: map?.values?.firstOrNull() ?: ""

    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
        .setAppId(appId)

    val n = achievements.size
    if (n == 0) {
        com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
            .setAchievementSchema(emptyArray(), emptyArray(), emptyArray(), emptyArray(), BooleanArray(0))
        Timber.i("Pushed empty achievement schema to libsteamclient.so (app $appId)")
        return
    }

    val apiNames     = Array(n) { i -> achievements[i].name }
    val displayNames = Array(n) { i -> pick(achievements[i].displayName) }
    val descriptions = Array(n) { i -> pick(achievements[i].description) }
    val icons        = Array(n) { i -> achievements[i].icon ?: "" }
    val hidden       = BooleanArray(n) { i -> achievements[i].hidden != 0 }
    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
        .setAchievementSchema(apiNames, displayNames, descriptions, icons, hidden)

    val statNames = mutableListOf<String>()
    val statIds = mutableListOf<Int>()
    for (s in stats) {
        val id = s.id.toIntOrNull() ?: continue
        if (s.name.isEmpty() || id < 0) continue
        statNames.add(s.name)
        statIds.add(id)
    }
    if (statNames.isNotEmpty()) {
        com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
            .setStatIds(statNames.toTypedArray(), statIds.toIntArray())
        Timber.i("Pushed stat name→id map for app $appId: ${statNames.size} entries")
    }

    if (nameToBlockBit.isNotEmpty()) {
        val mappedNames = mutableListOf<String>()
        val mappedBlocks = mutableListOf<Int>()
        val mappedBits = mutableListOf<Int>()
        for (a in achievements) {
            val pair = nameToBlockBit[a.name] ?: continue
            mappedNames.add(a.name)
            mappedBlocks.add(pair.first)
            mappedBits.add(pair.second)
        }
        if (mappedNames.isNotEmpty()) {
            com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                .setAchievementBlockBits(
                    mappedNames.toTypedArray(),
                    mappedBlocks.toIntArray(),
                    mappedBits.toIntArray(),
                )
            Timber.i("Pushed achievement bit-pack mapping for app $appId: ${mappedNames.size} entries")
        }
    }

    var localeAddsPushed = 0
    for (a in achievements) {
        val dnByLocale = a.displayName ?: emptyMap()
        val dsByLocale = a.description ?: emptyMap()
        val locales = dnByLocale.keys union dsByLocale.keys
        for (loc in locales) {
            if (loc.equals("english", ignoreCase = true)) continue
            val dn = dnByLocale[loc]
            val ds = dsByLocale[loc]
            if (dn.isNullOrEmpty() && ds.isNullOrEmpty()) continue
            com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                .addAchievementLocale(a.name, loc, dn, ds)
            ++localeAddsPushed
        }
    }

    var unlocksPushed = 0
    for (a in achievements) {
        val unlocked = a.unlocked == true
        val ts       = a.unlockTimestamp ?: 0
        if (unlocked || ts > 0) {
            com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                .setAchievementProgress(a.name, unlocked, ts)
            ++unlocksPushed
        }
    }
    for (s in stats) {
        when (s.type) {
            "int" -> com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                .setStatInt(s.name, s.default.toIntOrNull() ?: 0)
            "float" -> com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                .setStatFloat(s.name, s.default.toFloatOrNull() ?: 0f)
        }
    }
    Timber.i("Pushed achievement schema to libsteamclient.so: app=$appId ach=$n unlocks=$unlocksPushed stats=${stats.size} localeAdds=$localeAddsPushed")

    cacheAchievementSchemaJson(appId, achievements, stats, nameToBlockBit)
}

internal fun SteamService.Companion.cacheAchievementSchemaJson(
    appId: Int,
    achievements: List<com.winlator.cmod.feature.stores.steam.statsgen.Achievement>,
    stats: List<com.winlator.cmod.feature.stores.steam.statsgen.Stat>,
    nameToBlockBit: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    if (appId <= 0) return
    val ctx = instance?.applicationContext ?: return
    try {
        val dir = File(ctx.filesDir, "wn_lsteam_schemas")
        if (!dir.exists()) dir.mkdirs()
        val root = JSONObject()
        root.put("v", 1)
        root.put("ts", System.currentTimeMillis() / 1000)
        val achArr = JSONArray()
        for (a in achievements) {
            val o = JSONObject()
            o.put("name", a.name)
            a.displayName?.takeIf { it.isNotEmpty() }?.let { m ->
                val mo = JSONObject()
                m.forEach { (k, v) -> mo.put(k, v) }
                o.put("displayName", mo)
            }
            a.description?.takeIf { it.isNotEmpty() }?.let { m ->
                val mo = JSONObject()
                m.forEach { (k, v) -> mo.put(k, v) }
                o.put("description", mo)
            }
            a.icon?.takeIf { it.isNotEmpty() }?.let { o.put("icon", it) }
            if (a.hidden != 0) o.put("hidden", a.hidden)
            a.unlocked?.let { o.put("unlocked", it) }
            a.unlockTimestamp?.let { o.put("unlockTimestamp", it) }
            achArr.put(o)
        }
        root.put("achievements", achArr)
        val statArr = JSONArray()
        for (s in stats) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("type", s.type)
            o.put("default", s.default)
            statArr.put(o)
        }
        root.put("stats", statArr)
        if (nameToBlockBit.isNotEmpty()) {
            val bitsArr = JSONArray()
            for ((name, pair) in nameToBlockBit) {
                val o = JSONObject()
                o.put("name", name)
                o.put("block", pair.first)
                o.put("bit", pair.second)
                bitsArr.put(o)
            }
            root.put("nameToBlockBit", bitsArr)
        }
        File(dir, "$appId.json").writeText(root.toString(), Charsets.UTF_8)
        Timber.i("Cached schema for app $appId: ${achievements.size} ach, " +
            "${stats.size} stats, ${nameToBlockBit.size} bit-mappings")
    } catch (t: Throwable) {
        Timber.w(t, "cacheAchievementSchemaJson failed appId=$appId")
    }
}

internal fun SteamService.Companion.downloadWorkshopPreview(url: String, dest: File) {
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
        // Discard an over-cap (truncated) or empty download — never let mods.json reference a corrupt preview.
        if (over || total == 0L) dest.delete()
    } finally {
        conn.disconnect()
    }
}

internal fun SteamService.Companion.findSteamSettingsDir(
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

/** Decode a hex string (from the native JNI layer) to bytes; empty array for too-short/empty input. */
internal fun SteamService.Companion.hexToBytes(hex: String): ByteArray {
    if (hex.length < 2) return ByteArray(0)
    val n = hex.length / 2
    val out = ByteArray(n)
    for (i in 0 until n) {
        out[i] = ((Character.digit(hex[i * 2], 16) shl 4) or
            Character.digit(hex[i * 2 + 1], 16)).toByte()
    }
    return out
}

/** Write achievement/stat values back to Steam (CMsgClientStoreUserStats2). Fire-and-forget. */
internal suspend fun SteamService.Companion.sendStoreUserStats(
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
