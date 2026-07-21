package com.winlator.cmod.feature.stores.steam.service
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.INVALID_APP_ID
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.INVALID_PKG_ID
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.MAX_PICS_BUFFER
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.isLoggedIn
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.withWnSession
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

// Friends presence + chat + PICS pollers, split out of SteamService.kt (behavior-identical).

internal suspend fun SteamService.pushFriendsListToLibSteamClient() {
    var ids = LongArray(0)
    for (attempt in 0 until 30) {
        ids = withWnSession { s -> s.getFriendsList() } ?: LongArray(0)
        if (ids.isNotEmpty()) break
        delay(200)
    }
    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
        .setFriendsList(ids)
    Timber.i("Pushed ${ids.size} friend(s) to libsteamclient.so")

    if (ids.isEmpty()) return

    withWnSession { s ->
        s.requestFriendPersonas(ids, personaStateRequested = 1)
    }

    pushFriendPersonaNamesToLibSteamClient(ids)
}

internal suspend fun SteamService.pushFriendPersonaNamesToLibSteamClient(
    expectedIds: LongArray,
) {
    var lastCount = -1
    var json = "[]"
    for (attempt in 0 until 30) {
        json = withWnSession { s -> s.getFriendPersonas() } ?: "[]"
        val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        if (arr.length() >= expectedIds.size) break
        if (arr.length() == lastCount && arr.length() > 0) break
        lastCount = arr.length()
        delay(200)
    }
    val pushed = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
        .pushFriendPersonasJson(json, persistSnapshot = true)
    Timber.i("Pushed $pushed friend persona(s) to libsteamclient.so (snapshot persisted)")
}

internal fun SteamService.touchRecentChat(friendId: Long) {
    if (friendId == 0L) return
    _recentChats.update { it + (friendId to System.currentTimeMillis()) }
}

internal fun SteamService.isActiveConversation(steamId: Long): Boolean = activeConversations.containsKey(steamId)


internal fun SteamService.continuousIncomingMessagePoller(): Job =
    scope.launch {
        while (isActive && isLoggedIn) {
            delay(1000L)
            val grouped = runCatching { drainIncomingMessages() }.getOrNull()
            if (grouped.isNullOrEmpty()) continue
            dispatchIncomingChat(grouped)
        }
    }


internal fun SteamService.dispatchIncomingChat(
    grouped: Map<Long, List<com.winlator.cmod.feature.stores.steam.data.SteamChatMessage>>,
) {
    val friends = _friendsList.value.associateBy { it.steamId }
    val suppressed = GameSessionState.inGame && !PrefManager.chatInGameEnabled
    for ((friendId, messages) in grouped) {
        for (m in messages) _incomingChat.tryEmit(friendId to m)
        val fromFriend = messages.filter { !it.fromSelf && it.text.isNotBlank() }
        if (fromFriend.isEmpty()) continue
        touchRecentChat(friendId)
        if (isActiveConversation(friendId)) continue
        _unreadCounts.update { it + (friendId to ((it[friendId] ?: 0) + fromFriend.size)) }
        if (suppressed) continue
        val name = friends[friendId]?.name?.ifBlank { friendId.toString() } ?: friendId.toString()
        val preview = chatPreview(fromFriend.last().text)
        if (PrefManager.chatNotificationsEnabled) {
            runCatching { notificationHelper.notifyChatMessage(friendId, name, preview) }
        }
        if (PrefManager.chatHeadsEnabled) {
            runCatching {
                com.winlator.cmod.feature.stores.steam.chat.ChatOverlayService.onIncoming(this, friendId)
            }
        }
    }
}

internal fun SteamService.chatPreview(text: String): String {
    val t = text.trim()
    return if (t.startsWith("[img") || t.contains("steamusercontent.com")) {
        getString(com.winlator.cmod.R.string.steam_chat_image)
    } else {
        t
    }
}

/** Poll PICS for app/package changes since the last change number on a fixed interval. */
internal fun SteamService.continuousPICSChangesChecker(): Job =
    scope.launch {
        while (isActive && isLoggedIn) {
            PICSChangesCheck()
            delay(60.seconds)
        }
    }


internal fun SteamService.PICSChangesCheck() {
    scope.launch {
        ensureActive()

        try {
            // PICS change poll via the C++ WN-Steam-Client.
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

/** Buffered flow that batches bursts of PICS requests. */
internal fun SteamService.continuousPICSGetProductInfo(): Job =
    scope.launch {
        // App PICS — product info via the C++ WN-Steam-Client.
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

        // Package PICS — package info via the C++ WN-Steam-Client.
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
                                val appIds = pkg.optJSONArray("appids").toIntList()
                                licenseDao.updateApps(pkgId, appIds)
                                val depotIds = pkg.optJSONArray("depotids").toIntList()
                                licenseDao.updateDepots(pkgId, depotIds)

                                if (appIds.isNotEmpty()) {
                                    // Update package_id on existing rows in one statement; insert stubs for the rest (avoids a per-app find/update/insert N+1).
                                    val existing = appDao.findExistingIds(appIds).toHashSet()
                                    appDao.setPackageIdForApps(appIds, pkgId)
                                    val newApps = appIds.asSequence()
                                        .filter { it !in existing }
                                        .map { SteamApp(id = it, packageId = pkgId) }
                                        .toList()
                                    if (newApps.isNotEmpty()) appDao.insertAll(newApps)
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


/** Re-fetches apps whose stored manifest download>size (impossible — compressed can't exceed uncompressed); re-stores only when the fresh appinfo is clean so a bad response never overwrites good data. Self-limiting once rows are clean. */
internal fun SteamService.healCorruptManifestDownloadSizes(): Job =
    scope.launch {
        if (!isLoggedIn) return@launch

        fun SteamApp.hasCorruptDownload(): Boolean =
            depots.values.any { depot ->
                depot.manifests.values.any { m -> m.size > 0L && m.download > m.size }
            }

        val corruptAppIds =
            runCatching {
                withContext(Dispatchers.IO) { appDao.getAllAsList() }
                    .filter { it.hasCorruptDownload() }
                    .map { it.id }
            }.getOrElse { e ->
                Timber.w(e, "heal: scan for corrupt manifest download sizes failed")
                return@launch
            }
        if (corruptAppIds.isEmpty()) return@launch
        Timber.i(
            "heal: ${corruptAppIds.size} app(s) have download>size; re-fetching: ${corruptAppIds.sorted()}",
        )

        // Owned-app appinfo only comes back in full with the access token.
        val tokenMap = HashMap<Int, Long>()
        runCatching {
            withWnSession { session ->
                withContext(Dispatchers.IO) { session.getPicsAccessTokens(corruptAppIds, emptyList()) }
            }?.let { tokJson ->
                JSONObject(tokJson).optJSONObject("appTokens")?.let { at ->
                    for (k in at.keys()) tokenMap[k.toInt()] = at.getString(k).toLongOrNull() ?: 0L
                }
            }
        }.onFailure { e -> Timber.w(e, "heal: access-token fetch failed; trying public appinfo") }

        var healedCount = 0
        corruptAppIds.chunked(MAX_PICS_BUFFER).forEach { chunk ->
            ensureActive()
            val json =
                withWnSession { session ->
                    withContext(Dispatchers.IO) {
                        session.getPicsAppProductInfo(chunk, chunk.map { tokenMap[it] ?: 0L })
                    }
                } ?: return@forEach
            runCatching {
                val arr = JSONArray(json)
                val healed = mutableListOf<SteamApp>()
                for (i in 0 until arr.length()) {
                    ensureActive()
                    val entry = arr.getJSONObject(i)
                    val appId = entry.optInt("appid")
                    val changeNumber = entry.optInt("changeNumber")
                    val appinfo = entry.optJSONObject("appinfo") ?: continue
                    val generated = WnKeyValue.fromJsonObject(appinfo).generateSteamApp()
                    if (generated.id == INVALID_APP_ID) continue
                    // Only accept a re-fetch that actually removes the corruption.
                    if (generated.hasCorruptDownload()) {
                        Timber.w("heal: appId=$appId still reports download>size after re-fetch; leaving stored row")
                        continue
                    }
                    val appFromDb = appDao.findApp(appId)
                    val packageId = appFromDb?.packageId ?: INVALID_PKG_ID
                    val packageFromDb =
                        if (packageId != INVALID_PKG_ID) licenseDao.findLicense(packageId) else null
                    val existingInstallDir = appFromDb?.installDir.orEmpty()
                    val preserveInstallDir =
                        existingInstallDir.isNotEmpty() &&
                            (existingInstallDir.startsWith("/") || existingInstallDir.contains(File.separator))
                    healed.add(
                        generated.copy(
                            packageId = packageId,
                            ownerAccountId =
                                packageFromDb?.ownerAccountId ?: appFromDb?.ownerAccountId.orEmpty(),
                            receivedPICS = true,
                            lastChangeNumber = changeNumber,
                            licenseFlags =
                                packageFromDb?.licenseFlags
                                    ?: appFromDb?.licenseFlags
                                    ?: EnumSet.noneOf(ELicenseFlags::class.java),
                            installDir = if (preserveInstallDir) existingInstallDir else generated.installDir,
                        ),
                    )
                }
                if (healed.isNotEmpty()) {
                    db.withTransaction { appDao.insertAll(healed) }
                    healedCount += healed.size
                }
            }.onFailure { e -> Timber.w(e, "heal: batch processing failed") }
        }
        if (healedCount > 0) {
            Timber.i("heal: corrected manifest download sizes for $healedCount app(s)")
        }
    }

