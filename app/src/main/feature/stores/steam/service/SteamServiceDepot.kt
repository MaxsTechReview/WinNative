package com.winlator.cmod.feature.stores.steam.service
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.GroupedBaseAppDlcDepot
import com.winlator.cmod.feature.stores.steam.service.SteamService.ManifestSizes
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

// Depot/manifest resolution + size calculation, split out of SteamService.kt (behavior-identical).

internal fun SteamService.Companion.getEntitledDepotIds(packageId: Int): Set<Int>? {
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

internal fun SteamService.Companion.isDepotEntitled(
    depotId: Int,
    depot: DepotInfo,
    entitledDepotIds: Set<Int>?,
): Boolean {
    // Explicit package grant wins (covers low-violence / regional packages).
    if (entitledDepotIds != null && depotId in entitledDepotIds) return true
    // Low-violence content needs an explicit grant; Steam denies its depot key otherwise.
    if (depot.lowViolence) return false
    if (entitledDepotIds == null) return true
    if (depot.sharedInstall || depot.depotFromApp != INVALID_APP_ID) return true
    // Package depot lists are often incomplete for base content; owning the app entitles it.
    return depot.dlcAppId == INVALID_APP_ID
}

private val SUPERSEDED_DEPOTS: Map<Int, Map<Int, Int>> =
    mapOf(
        993090 to mapOf(993092 to 993091),
    )

internal fun SteamService.Companion.dropSupersededDepots(
    appId: Int,
    depots: Map<Int, DepotInfo>,
): Map<Int, DepotInfo> {
    val rules = SUPERSEDED_DEPOTS[appId] ?: return depots
    val dropped = depots.keys.filter { rules[it]?.let { preferred -> preferred in depots } == true }
    if (dropped.isEmpty()) return depots
    Timber.i("Dropping superseded depots $dropped for appId=$appId; preferred twin is present")
    return depots.filterKeys { it !in dropped }
}

internal fun SteamService.Companion.repairSupersededInstall(
    appId: Int,
    appDirPath: String,
    selectedDepotIds: Set<Int>,
): Boolean {
    val rules = SUPERSEDED_DEPOTS[appId] ?: return false
    if (appDirPath.isBlank()) return false

    val installed = readInstalledDepotManifestIds(appDirPath)
    val stale = rules.filterKeys { it in installed }.filterValues { it in selectedDepotIds }
    if (stale.isEmpty()) return false

    val depotDir = File(appDirPath, ".DepotDownloader")
    val configFile = File(depotDir, "depot.config")
    val affected = stale.keys + stale.values

    val rewritten =
        runCatching {
            if (!configFile.isFile) return@runCatching false
            val json = JSONObject(configFile.readText())
            val manifests = json.optJSONObject("installedManifestIDs") ?: return@runCatching false
            affected.forEach { manifests.remove(it.toString()) }
            configFile.writeText(json.toString())
            true
        }.getOrElse {
            Timber.w(it, "Could not rewrite depot.config repairing superseded depots for appId=$appId")
            false
        }
    if (!rewritten) return false

    depotDir
        .listFiles()
        .orEmpty()
        .forEach { file ->
            if (!file.name.endsWith(".manifest")) return@forEach
            val depotId = file.name.substringBefore('_').toIntOrNull() ?: return@forEach
            if (depotId in affected) file.delete()
        }

    Timber.i(
        "Repaired superseded Steam install for appId=$appId: cleared depots $affected " +
            "so ${stale.values} re-download and overwrite files the stale twin had won",
    )
    return true
}

internal fun SteamService.Companion.getSelectedDownloadDepots(
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
    val appInfoForGrouping = getAppInfoOf(appId)
    val groupedBaseDlcDepotIds =
        appInfoForGrouping
            ?.let { getGroupedBaseAppDlcContentDepotIds(it) }
            .orEmpty()
    // Base-package-entitled depots are always base content; never let positional DLC grouping drop them (a DLC marker preceding base depots would zero the game's size, e.g. DMC5 601151/2).
    val baseEntitledDepotIds =
        appInfoForGrouping?.packageId?.let { getEntitledDepotIds(it) }.orEmpty()

    val selectedMainDepots =
        mainDepots.filter { (depotId, depot) ->
            (
                depot.dlcAppId == INVALID_APP_ID &&
                    (depotId !in groupedBaseDlcDepotIds || depotId in baseEntitledDepotIds)
            ) ||
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

internal fun SteamService.Companion.getGroupedBaseAppDlcContentDepotIds(appInfo: SteamApp): Set<Int> {
    return getGroupedBaseAppDlcDepots(appInfo).map { it.depotId }.toSet()
}

internal fun SteamService.Companion.getGroupedBaseAppDlcIds(
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

internal fun SteamService.Companion.getGroupedBaseAppDlcDepots(appInfo: SteamApp): List<GroupedBaseAppDlcDepot> {
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

internal fun SteamService.Companion.getSelectedBaseAppDlcContentDepots(
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

internal fun SteamService.Companion.resolveDepotManifestInfo(
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

internal fun SteamService.Companion.manifestDownloadBytes(manifest: ManifestInfo?): Long {
    if (manifest == null) return 0L
    val size = manifest.size.coerceAtLeast(0L)
    // Compressed download can't exceed uncompressed size; reject bogus stored values (legacy depots showed tens of TiB), bound to size — healCorruptManifestDownloadSizes() restores the real value.
    return manifest.download.takeIf { it in 1L..size } ?: size
}

internal fun SteamService.Companion.calculateManifestSizes(
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

internal fun SteamService.Companion.filterAlreadyInstalledDepots(
    appId: Int,
    depots: Map<Int, DepotInfo>,
    includeInstalledDepots: Boolean,
): Map<Int, DepotInfo> {
    if (includeInstalledDepots || depots.isEmpty()) return depots

    val installedApp = getTrustedInstalledAppInfo(appId) ?: return depots
    val installedDlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toSet()

    return depots.filter { (depotId, depot) ->
        val isInstalledBaseDepot =
            depot.dlcAppId == INVALID_APP_ID &&
                depotId in installedApp.downloadedDepots
        val isInstalledDlcDepot =
            depot.dlcAppId != INVALID_APP_ID &&
                depot.dlcAppId in installedDlcAppIds

        !isInstalledBaseDepot && !isInstalledDlcDepot
    }
}

internal fun SteamService.Companion.filterAlreadyInstalledDlcSelection(
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
