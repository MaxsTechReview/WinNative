package com.winlator.cmod.feature.steamcloudsync

import android.content.Context
import com.winlator.cmod.feature.stores.steam.data.PostSyncInfo
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.enums.SaveLocation
import com.winlator.cmod.feature.stores.steam.enums.SyncResult
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
import com.winlator.cmod.feature.stores.steam.utils.FileUtils
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SteamCloudSyncHelper {
    private const val LABEL_NO_LOCAL_SAVES = "No local saves"
    private const val LABEL_NO_CLOUD_SAVES = "No cloud saves"
    private const val LABEL_CLOUD_UNREACHABLE = "Cloud unreachable"

    private fun formatTimestamp(
        timestampMs: Long?,
        emptyLabel: String,
    ): String {
        if (timestampMs == null || timestampMs <= 0L) return emptyLabel
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
    }

    @JvmStatic
    fun isOfflineMode(shortcut: Shortcut?): Boolean =
        shortcut != null && shortcut.getExtra("offline_mode", "0") == "1"

    @JvmStatic
    fun forceDownloadOnContainerSwap(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        if (shortcut.getExtra("game_source") != "STEAM") return false
        if (shortcut.getExtra("cloud_force_download").isEmpty()) return false

        val result = runBlocking { forceDownload(context, shortcut) }
        if (result) {
            shortcut.putExtra("cloud_force_download", null)
            shortcut.saveData()
        }

        Timber.i("Force Steam cloud download for %s: %s", shortcut.name, result)
        return result
    }

    suspend fun forceDownload(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return false
        return forceDownloadById(context, appId, shortcut.container)
    }

    suspend fun forceDownloadById(
        context: Context,
        appId: Int,
        containerHint: Container? = null,
    ): Boolean =
        try {
            val prefixToPath = steamPrefixResolver(context, appId, containerHint)
            val syncInfo =
                SteamService
                    .forceSyncUserFiles(
                        appId = appId,
                        prefixToPath = prefixToPath,
                        preferredSave = SaveLocation.Remote,
                        overrideLocalChangeNumber = -1,
                    ).await()

            val ok = syncInfo?.syncResult == SyncResult.Success || syncInfo?.syncResult == SyncResult.UpToDate
            if (ok) {
                probeCache.remove(appId)
                runCatching {
                    SteamService.pushCloudStateToLibSteamClient(appId)
                }.onFailure { e ->
                    Timber.w(e, "forceDownloadById: libsteamclient mirror refresh failed for app=%d", appId)
                }
            }
            ok
        } catch (e: Exception) {
            Timber.e(e, "Failed to force Steam cloud download for appId=%d", appId)
            false
        }

    @JvmStatic
    fun hasLocalCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        if (shortcut.getExtra("game_source") != "STEAM") return false
        val appId = shortcut.getExtra("app_id")
        if (appId.isEmpty()) return false
        val appIdInt = appId.toIntOrNull() ?: return false

        return hasActualLocalSaves(context, appIdInt, shortcut.container)
    }

    fun hasActualLocalSaves(
        context: Context,
        appId: Int,
        containerHint: Container? = null,
    ): Boolean {
        val appInfo = SteamService.getAppInfoOf(appId) ?: return false
        val prefixToPath = steamPrefixResolver(context, appId, containerHint)

        val userDataPath = Paths.get(prefixToPath(PathType.SteamUserData.name))
        if (FileUtils.anyFileMatches(userDataPath, "*", maxDepth = 5)) return true

        val savePatterns =
            appInfo.ufs.saveFilePatterns
                .filter { it.root.isWindows && it.root != PathType.SteamUserData }

        return savePatterns.any { pattern ->
            val basePath = Paths.get(prefixToPath(pattern.root.name), pattern.substitutedPath)
            FileUtils.anyFileMatches(
                rootPath = basePath,
                pattern = pattern.pattern,
                maxDepth = if (pattern.recursive != 0) -1 else 0,
            )
        }
    }

    private fun newestTimestampInFiles(
        rootPath: Path,
        pattern: String,
        maxDepth: Int,
    ): Long? {
        val stream = FileUtils.findFilesRecursive(rootPath, pattern, maxDepth = maxDepth)
        return try {
            var newest = 0L
            stream.forEach { path ->
                val modified =
                    runCatching {
                        if (Files.isRegularFile(path)) Files.getLastModifiedTime(path).toMillis() else 0L
                    }.getOrDefault(0L)
                if (modified > newest) {
                    newest = modified
                }
            }
            newest.takeIf { it > 0L }
        } finally {
            stream.close()
        }
    }

    private fun getNewestActualLocalCloudSaveTimestamp(
        context: Context,
        appId: Int,
        containerHint: Container? = null,
    ): Long? {
        val appInfo = SteamService.getAppInfoOf(appId) ?: return null
        val prefixToPath = steamPrefixResolver(context, appId, containerHint)

        val userDataNewest =
            newestTimestampInFiles(
                Paths.get(prefixToPath(PathType.SteamUserData.name)),
                "*",
                maxDepth = 5,
            )

        val patternNewest =
            appInfo.ufs.saveFilePatterns
                .filter { it.root.isWindows && it.root != PathType.SteamUserData }
            .mapNotNull { pattern ->
                val basePath = Paths.get(prefixToPath(pattern.root.name), pattern.substitutedPath)
                newestTimestampInFiles(
                    rootPath = basePath,
                    pattern = pattern.pattern,
                    maxDepth = if (pattern.recursive != 0) -1 else 0,
                )
            }.maxOrNull()

        return listOfNotNull(userDataNewest, patternNewest).maxOrNull()
    }

    @JvmStatic
    fun cloudSavesDiffer(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        if (!hasLocalCloudSaves(context, shortcut)) return false
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return false
        return runBlocking {
            try {
                SteamService.cloudSavesDiffer(appId) ?: true
            } catch (e: Exception) {
                Timber.e(e, "Steam cloud save diff check failed for %s", shortcut.name)
                true
            }
        }
    }

    /** Result of one conflict probe for the launch-time sync prompt. */
    data class CloudConflictProbe(
        val differs: Boolean,
        val timestamps: SteamCloudConflictTimestamps,
    )

    private const val PROBE_CACHE_TTL_MS = 60_000L
    private val probeCache = ConcurrentHashMap<Int, Pair<Long, CloudConflictProbe>>()

    @JvmStatic
    fun probeCloudConflict(
        context: Context,
        shortcut: Shortcut,
    ): CloudConflictProbe {
        val appId = shortcut.getExtra("app_id").toIntOrNull()
        if (appId == null || !hasLocalCloudSaves(context, shortcut)) {
            return CloudConflictProbe(
                differs = false,
                timestamps = SteamCloudConflictTimestamps(LABEL_NO_LOCAL_SAVES, LABEL_NO_CLOUD_SAVES),
            )
        }
        probeCache[appId]?.let { (ts, cached) ->
            if (System.currentTimeMillis() - ts < PROBE_CACHE_TTL_MS) return cached
        }
        activateContainer(context, shortcut.container)
        return runBlocking {
            try {
                val snapshot = SteamService.fetchCloudConflictSnapshot(appId, context)
                val localActual = getNewestActualLocalCloudSaveTimestamp(context, appId, shortcut.container)
                val localTracked =
                    SteamService.getTrackedCloudSaveFiles(appId)?.maxOfOrNull { it.timestamp }
                val localTs = localActual ?: localTracked
                val cloudLabel =
                    when {
                        snapshot == null -> LABEL_CLOUD_UNREACHABLE
                        else -> formatTimestamp(snapshot.newestRemoteTimestamp, LABEL_NO_CLOUD_SAVES)
                    }
                val probe = CloudConflictProbe(
                    differs = snapshot?.differs ?: false,
                    timestamps =
                        SteamCloudConflictTimestamps(
                            localTimestampLabel = formatTimestamp(localTs, LABEL_NO_LOCAL_SAVES),
                            cloudTimestampLabel = cloudLabel,
                        ),
                )
                if (snapshot != null) probeCache[appId] = System.currentTimeMillis() to probe
                probe
            } catch (e: Exception) {
                Timber.e(e, "Steam cloud conflict probe failed for %s", shortcut.name)
                CloudConflictProbe(
                    differs = false,
                    timestamps = SteamCloudConflictTimestamps(LABEL_NO_LOCAL_SAVES, LABEL_CLOUD_UNREACHABLE),
                )
            }
        }
    }

    fun timestampsFromSyncInfo(
        context: Context,
        shortcut: Shortcut,
        syncInfo: PostSyncInfo?,
    ): SteamCloudConflictTimestamps {
        if (syncInfo == null || (syncInfo.localTimestamp <= 0L && syncInfo.remoteTimestamp <= 0L)) {
            return getConflictTimestamps(context, shortcut)
        }
        return SteamCloudConflictTimestamps(
            localTimestampLabel = formatTimestamp(syncInfo.localTimestamp, LABEL_NO_LOCAL_SAVES),
            cloudTimestampLabel = formatTimestamp(syncInfo.remoteTimestamp, LABEL_NO_CLOUD_SAVES),
        )
    }

    @JvmStatic
    fun getConflictTimestamps(
        context: Context,
        shortcut: Shortcut,
    ): SteamCloudConflictTimestamps {
        val appId = shortcut.getExtra("app_id").toIntOrNull()
        return runBlocking {
            try {
                val localActual = appId?.let { getNewestActualLocalCloudSaveTimestamp(context, it, shortcut.container) }
                val localTracked =
                    appId
                        ?.let { SteamService.getTrackedCloudSaveFiles(it) }
                        ?.maxOfOrNull { it.timestamp }
                val snapshot = appId?.let { SteamService.fetchCloudConflictSnapshot(it, context) }
                val cloudLabel =
                    when {
                        appId == null -> LABEL_NO_CLOUD_SAVES
                        snapshot == null -> LABEL_CLOUD_UNREACHABLE
                        else -> formatTimestamp(snapshot.newestRemoteTimestamp, LABEL_NO_CLOUD_SAVES)
                    }
                SteamCloudConflictTimestamps(
                    localTimestampLabel = formatTimestamp(localActual ?: localTracked, LABEL_NO_LOCAL_SAVES),
                    cloudTimestampLabel = cloudLabel,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to build Steam cloud conflict timestamps for %s", shortcut.name)
                SteamCloudConflictTimestamps(LABEL_NO_LOCAL_SAVES, LABEL_CLOUD_UNREACHABLE)
            }
        }
    }

    @JvmStatic
    fun downloadCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        if (shortcut.getExtra("game_source") != "STEAM") return false
        val result = runBlocking { forceDownload(context, shortcut) }
        if (result) {
            markCloudSaveSynced(context, shortcut.getExtra("app_id"), shortcut.container)
        }
        Timber.i("Steam cloud save download for %s: %s", shortcut.name, result)
        return result
    }

    suspend fun syncBeforeLaunch(
        context: Context,
        shortcut: Shortcut,
        preferredSave: SaveLocation = SaveLocation.None,
        ignorePendingOperations: Boolean = false,
    ): PostSyncInfo? {
        if (shortcut.getExtra("game_source") != "STEAM") return null
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return null
        val prefixToPath = steamPrefixResolver(context, appId, shortcut.container)
        val syncInfo =
            SteamService
                .beginLaunchApp(
                    appId = appId,
                    preferredSave = preferredSave,
                    ignorePendingOperations = ignorePendingOperations,
                    prefixToPath = prefixToPath,
                    isOffline = isOfflineMode(shortcut),
                ).await()

        if (syncInfo.syncResult == SyncResult.Success || syncInfo.syncResult == SyncResult.UpToDate) {
            probeCache.remove(appId)
            runCatching {
                SteamService.pushCloudStateToLibSteamClient(appId)
            }.onFailure { e ->
                Timber.w(e, "syncBeforeLaunch: libsteamclient cloud refresh failed for app=%d", appId)
            }
        }
        return syncInfo
    }

    fun syncBeforeLaunchBlocking(
        context: Context,
        shortcut: Shortcut,
        preferredSave: SaveLocation = SaveLocation.None,
        ignorePendingOperations: Boolean = false,
    ): PostSyncInfo? =
        runBlocking(Dispatchers.IO) {
            syncBeforeLaunch(context, shortcut, preferredSave, ignorePendingOperations)
        }

    /**
     * Uploads local Steam save files for [appId] so they overwrite Steam Cloud.
     *
     * Used after "Use Local" in the launch-time conflict dialog. The explicit upload bumps
     * Steam's local change number and prevents the same conflict from recurring.
     */
    suspend fun uploadLocalSaves(
        context: Context,
        appId: Int,
        containerHint: Container? = null,
    ): Boolean =
        try {
            val prefixToPath = steamPrefixResolver(context, appId, containerHint)
            val syncInfo =
                SteamService
                    .forceSyncUserFiles(
                        appId = appId,
                        prefixToPath = prefixToPath,
                        preferredSave = SaveLocation.Local,
                        overrideLocalChangeNumber = -1,
                    ).await()
            val ok = syncInfo?.syncResult == SyncResult.Success || syncInfo?.syncResult == SyncResult.UpToDate
            if (ok) {
                probeCache.remove(appId)
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        SteamSaveSnapshotManager.recordSnapshot(
                            context,
                            appId,
                            GameSaveBackupManager.BackupOrigin.LOCAL,
                            containerHint,
                        )
                    }.onFailure { Timber.w(it, "Snapshot after Use-Local upload failed for appId=%d", appId) }
                }
            }
            ok
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload local Steam saves for appId=%d", appId)
            false
        }

    @JvmStatic
    fun uploadLocalSavesBlocking(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        if (shortcut.getExtra("game_source") != "STEAM") return false
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return false
        return runBlocking { uploadLocalSaves(context, appId, shortcut.container) }
    }

    @JvmStatic
    fun downloadCloudSaves(
        context: Context,
        gameId: String,
    ): Boolean {
        val appId = gameId.toIntOrNull() ?: return false
        val result = runBlocking { forceDownloadById(context, appId) }
        if (result) markCloudSaveSynced(context, gameId, ContainerUtils.getUsableContainerOrNull(context, gameId))
        Timber.i("Steam cloud save download for %s: %s", gameId, result)
        return result
    }

    private fun markCloudSaveSynced(
        context: Context,
        appId: String,
        container: Container?,
    ) {
        if (appId.isEmpty()) return
        val prefs = context.getSharedPreferences("cloud_sync_state", Context.MODE_PRIVATE)
        prefs.edit().putString("synced_STEAM_$appId", containerFingerprint(container)).apply()
    }

    private fun containerFingerprint(container: Container?): String {
        if (container == null) return "none"
        val root = container.rootDir ?: return "id-${container.id}"
        val wineDir = File(root, ".wine")
        val sig = if (wineDir.exists()) wineDir.lastModified() else 0L
        return "${container.id}:$sig"
    }

    private fun steamPrefixResolver(
        context: Context,
        appId: Int,
        containerHint: Container? = null,
    ): (String) -> String {
        // PathType resolves Windows-side save roots through the active `home/xuser`
        // symlink. Activate the game's container first so launcher restores, manual syncs,
        // and pre-launch checks do not read or write another game's wineprefix.
        //
        // Prefer the shortcut container when available. The appId fallback is only for
        // callers that do not have shortcut context.
        activateContainerForCloudOp(context, appId, containerHint)

        val accountId =
            SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserSteamId64.takeIf { it != 0L }?.let { it and 0xFFFFFFFFL }
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L
        return { prefix -> PathType.from(prefix).toAbsPath(context, appId, accountId) }
    }

    private fun activateContainerForCloudOp(
        context: Context,
        appId: Int,
        containerHint: Container?,
    ) {
        val target =
            containerHint
                ?: ContainerUtils.getUsableContainerOrNull(context, appId.toString())
                ?: return
        activateContainer(context, target)
    }

    private fun activateContainer(
        context: Context,
        container: Container,
    ) {
        runCatching {
            ContainerManager(context).activateContainer(container)
        }.onFailure { Timber.w(it, "Failed to activate container id=%d", container.id) }
    }
}
