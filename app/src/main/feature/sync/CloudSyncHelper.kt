package com.winlator.cmod.feature.sync
import android.content.Context
import com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.steamcloudsync.SteamCloudSyncHelper
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.runtime.container.Shortcut
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

object CloudSyncHelper {
    private fun formatTimestamp(timestampMs: Long?): String {
        if (timestampMs == null || timestampMs <= 0L) return "Unknown"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
    }

    private fun File.hasAnyFile(): Boolean = exists() && walkTopDown().any { it.isFile }

    private fun normalizeGogAppId(appId: String): String =
        if (appId.startsWith("GOG_", ignoreCase = true)) appId else "GOG_$appId"

    @JvmStatic
    fun forceDownloadOnContainerSwap(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val forceFlag = shortcut.getExtra("cloud_force_download")
        if (forceFlag.isEmpty()) return false

        val result =
            runBlocking {
                when (shortcut.getExtra("game_source")) {
                    "STEAM" -> SteamCloudSyncHelper.forceDownload(context, shortcut)
                    "GOG" -> forceGogDownload(context, shortcut)
                    "EPIC" -> forceEpicDownload(context, shortcut)
                    else -> false
                }
            }

        if (result) {
            shortcut.putExtra("cloud_force_download", null)
            shortcut.saveData()
        }

        Timber.i(
            "Force cloud download for %s (source=%s): %s",
            shortcut.name,
            shortcut.getExtra("game_source"),
            result,
        )
        return result
    }

    private suspend fun forceGogDownload(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val rawAppId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
        if (rawAppId.isEmpty()) return false
        return forceGogDownloadById(context, rawAppId)
    }

    private suspend fun forceGogDownloadById(
        context: Context,
        rawAppId: String,
    ): Boolean {
        if (rawAppId.isEmpty()) return false
        val appId = if (rawAppId.startsWith("GOG_", ignoreCase = true)) rawAppId else "GOG_$rawAppId"
        return try {
            GOGService.syncCloudSaves(context, appId, "download")
        } catch (e: Exception) {
            Timber.e(e, "Failed to force GOG cloud download for appId=%s", appId)
            false
        }
    }

    private suspend fun forceEpicDownload(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return false
        return forceEpicDownloadById(context, appId)
    }

    private suspend fun forceEpicDownloadById(
        context: Context,
        appId: Int,
    ): Boolean =
        try {
            EpicCloudSavesManager.syncCloudSaves(context, appId, "download")
        } catch (e: Exception) {
            Timber.e(e, "Failed to force Epic cloud download for appId=%d", appId)
            false
        }

    /**
     * Checks whether the given shortcut belongs to a supported store (Steam, Epic, GOG).
     */
    @JvmStatic
    fun isStoreGame(shortcut: Shortcut): Boolean {
        val source = shortcut.getExtra("game_source")
        return source == "STEAM" || source == "EPIC" || source == "GOG"
    }

    /**
     * Per-shortcut "Disable Cloud Sync" override. When true, every automatic
     * cloud interaction (launch download, conflict prompt, exit provider sync,
     * exit Drive auto-backup) is skipped. Manual user-initiated actions
     * (Back up, Restore, Sync from Cloud) are NOT blocked by this flag.
     */
    @JvmStatic
    fun isOfflineMode(shortcut: Shortcut?): Boolean =
        shortcut != null && shortcut.getExtra("offline_mode", "0") == "1"

    /**
     * Returns true when a previous cloud-save sync has been recorded for this
     * shortcut, indicating that local save data already exists on-device.
     */
    @JvmStatic
    fun hasLocalCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val gameSource = shortcut.getExtra("game_source")
        val appId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
        if (gameSource.isEmpty() || appId.isEmpty()) return false

        val prefs = context.getSharedPreferences("cloud_sync_state", Context.MODE_PRIVATE)
        if (prefs.contains("synced_${gameSource}_$appId")) return true

        return runBlocking {
            when (gameSource) {
                "STEAM" -> SteamCloudSyncHelper.hasActualLocalSaves(context, appId.toIntOrNull() ?: return@runBlocking false)
                "EPIC" -> {
                    val epicAppId = appId.toIntOrNull() ?: return@runBlocking false
                    EpicCloudSavesManager.getResolvedSaveDirectory(context, epicAppId)?.hasAnyFile() == true
                }
                "GOG" -> GOGService.hasActualLocalCloudSaves(context, normalizeGogAppId(appId))
                else -> false
            }
        }
    }

    /**
     * Marks this shortcut's cloud saves as having been synced locally.
     */
    @JvmStatic
    fun markCloudSaveSynced(
        context: Context,
        shortcut: Shortcut,
    ) {
        val gameSource = shortcut.getExtra("game_source")
        val appId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
        if (gameSource.isEmpty() || appId.isEmpty()) return

        val prefs = context.getSharedPreferences("cloud_sync_state", Context.MODE_PRIVATE)
        prefs.edit().putLong("synced_${gameSource}_$appId", System.currentTimeMillis()).apply()
    }

    /**
     * Lightweight probe: checks whether cloud saves differ from local saves
     * WITHOUT downloading or uploading any files.
     *
     * - **Steam**: compares local vs remote change numbers (single metadata call).
     * - **Epic**: uses [EpicCloudSavesManager.needsSync] which evaluates the
     *   sync action without performing it.
     * - **GOG**: compares resolved local saves with the provider file list
     *   without downloading or uploading files.
     *
     * @return `true` if cloud data differs from local (dialog should be shown),
     *         `false` if saves are in sync or if the check cannot be performed.
     */
    @JvmStatic
    fun cloudSavesDiffer(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        if (!isStoreGame(shortcut) || !hasLocalCloudSaves(context, shortcut)) return false

        return runBlocking {
            try {
                when (shortcut.getExtra("game_source")) {
                    "STEAM" -> {
                        SteamCloudSyncHelper.cloudSavesDiffer(context, shortcut)
                    }

                    "EPIC" -> {
                        val appId =
                            shortcut.getExtra("app_id").toIntOrNull()
                                ?: return@runBlocking false
                        EpicCloudSavesManager.needsSync(context, appId)
                    }

                    // GOG probe fetches metadata only; it does not download or upload save files.
                    "GOG" -> {
                        val appId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
                        GOGService.cloudSavesDiffer(context, normalizeGogAppId(appId)) ?: true
                    }

                    else -> {
                        false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Cloud save diff check failed for %s", shortcut.name)
                // Cannot determine — assume different so user gets to decide
                true
            }
        }
    }

    @JvmStatic
    fun getEpicConflictTimestamps(
        context: Context,
        shortcut: Shortcut,
    ): EpicCloudConflictTimestamps =
        runBlocking {
            try {
                val appId = shortcut.getExtra("app_id").toIntOrNull()
                val saveDir = appId?.let { EpicCloudSavesManager.getResolvedSaveDirectory(context, it) }
                val localNewest =
                    saveDir
                        ?.takeIf { it.exists() }
                        ?.walkTopDown()
                        ?.filter { it.isFile }
                        ?.maxOfOrNull(File::lastModified)
                // Must match the SharedPreferences name written by EpicCloudSavesManager.setSyncTimestamp.
                // Reading from "epic_games" silently always returned null and the conflict dialog
                // displayed "Unknown" for the cloud timestamp on every Epic prompt.
                val prefs = context.getSharedPreferences("epic_cloud_saves", Context.MODE_PRIVATE)
                val cloudTimestamp =
                    prefs
                        .getString("sync_timestamp_${appId ?: 0}", null)
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                EpicCloudConflictTimestamps(
                    localTimestampLabel = formatTimestamp(localNewest),
                    cloudTimestampLabel = formatTimestamp(cloudTimestamp),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to build Epic cloud conflict timestamps for %s", shortcut.name)
                EpicCloudConflictTimestamps("Unknown", "Unknown")
            }
        }

    @JvmStatic
    fun getGogConflictTimestamps(
        context: Context,
        shortcut: Shortcut,
    ): GogCloudConflictTimestamps =
        runBlocking {
            try {
                val appId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
                val localNewest =
                    GOGService
                        .getResolvedSaveDirectories(context, normalizeGogAppId(appId))
                        .asSequence()
                        .filter { it.exists() }
                        .flatMap { it.walkTopDown().asSequence() }
                        .filter { it.isFile }
                        .maxOfOrNull(File::lastModified)
                val cloudTimestamp = GOGService.getNewestCloudSaveSyncTimestamp(context, normalizeGogAppId(appId))
                GogCloudConflictTimestamps(
                    localTimestampLabel = formatTimestamp(localNewest),
                    cloudTimestampLabel = formatTimestamp(cloudTimestamp),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to build GOG cloud conflict timestamps for %s", shortcut.name)
                GogCloudConflictTimestamps("Unknown", "Unknown")
            }
        }

    /**
     * Downloads cloud saves for the given store game shortcut and
     * records a sync marker so subsequent launches can detect local data.
     */
    @JvmStatic
    fun downloadCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val result =
            runBlocking {
                when (shortcut.getExtra("game_source")) {
                    "STEAM" -> SteamCloudSyncHelper.forceDownload(context, shortcut)
                    "GOG" -> forceGogDownload(context, shortcut)
                    "EPIC" -> forceEpicDownload(context, shortcut)
                    else -> false
                }
            }
        if (result) {
            markCloudSaveSynced(context, shortcut)
        }
        Timber.i(
            "Cloud save download for %s (source=%s): %s",
            shortcut.name,
            shortcut.getExtra("game_source"),
            result,
        )
        return result
    }

    /**
     * Uploads the current local save files to the store cloud and records a sync marker.
     */
    @JvmStatic
    fun uploadCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val result =
            runBlocking {
                when (shortcut.getExtra("game_source")) {
                    "STEAM" -> false
                    "GOG" -> uploadGogCloudSaves(context, shortcut)
                    "EPIC" -> uploadEpicCloudSaves(context, shortcut)
                    else -> false
                }
            }
        if (result) {
            markCloudSaveSynced(context, shortcut)
        }
        Timber.i(
            "Cloud save upload for %s (source=%s): %s",
            shortcut.name,
            shortcut.getExtra("game_source"),
            result,
        )
        return result
    }

    private suspend fun uploadGogCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val rawAppId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
        if (rawAppId.isEmpty()) return false
        return try {
            GOGService.syncCloudSaves(context, normalizeGogAppId(rawAppId), "upload")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload GOG cloud saves for appId=%s", rawAppId)
            false
        }
    }

    private suspend fun uploadEpicCloudSaves(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return false
        return try {
            EpicCloudSavesManager.syncCloudSaves(context, appId, "upload")
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload Epic cloud saves for appId=%d", appId)
            false
        }
    }

    /**
     * Download cloud saves for a game without requiring a container shortcut.
     * Useful from the Cloud Saves screen when the user hasn't launched or pinned
     * the game yet (so no shortcut exists), but still wants to pull saves down.
     */
    @JvmStatic
    fun downloadCloudSaves(
        context: Context,
        source: GameSaveBackupManager.GameSource,
        gameId: String,
    ): Boolean {
        val result =
            runBlocking {
                when (source) {
                    GameSaveBackupManager.GameSource.STEAM -> {
                        SteamCloudSyncHelper.forceDownloadById(context, gameId.toIntOrNull() ?: return@runBlocking false)
                    }
                    GameSaveBackupManager.GameSource.EPIC -> {
                        val appId = gameId.toIntOrNull() ?: return@runBlocking false
                        forceEpicDownloadById(context, appId)
                    }
                    GameSaveBackupManager.GameSource.GOG -> forceGogDownloadById(context, gameId)
                    GameSaveBackupManager.GameSource.CUSTOM -> false
                }
            }
        if (result) {
            markCloudSaveSyncedById(context, source, gameId)
        }
        Timber.i("Cloud save download for %s/%s: %s", source, gameId, result)
        return result
    }

    private fun markCloudSaveSyncedById(
        context: Context,
        source: GameSaveBackupManager.GameSource,
        gameId: String,
    ) {
        if (gameId.isEmpty()) return
        val prefs = context.getSharedPreferences("cloud_sync_state", Context.MODE_PRIVATE)
        prefs.edit().putLong("synced_${source.name}_$gameId", System.currentTimeMillis()).apply()
    }
}
