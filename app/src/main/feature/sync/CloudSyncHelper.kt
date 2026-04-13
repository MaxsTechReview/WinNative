package com.winlator.cmod.feature.sync
import android.content.Context
import com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager
import com.winlator.cmod.feature.stores.gog.service.GOGCloudSavesManager
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.enums.SaveLocation
import com.winlator.cmod.feature.stores.steam.enums.SyncResult
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
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
                    "STEAM" -> forceSteamDownload(context, shortcut)
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

    private suspend fun forceSteamDownload(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val appId = shortcut.getExtra("app_id").toIntOrNull() ?: return false
        return try {
            val accountId =
                SteamService.userSteamId?.accountID?.toLong()
                    ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                    ?: 0L
            val prefixToPath: (String) -> String = { prefix ->
                PathType.from(prefix).toAbsPath(context, appId, accountId)
            }

            val syncInfo =
                SteamService
                    .forceSyncUserFiles(
                        appId = appId,
                        prefixToPath = prefixToPath,
                        preferredSave = SaveLocation.Remote,
                        overrideLocalChangeNumber = -1,
                    ).await()

            syncInfo?.syncResult == SyncResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Failed to force Steam cloud download for appId=%d", appId)
            false
        }
    }

    private suspend fun forceGogDownload(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val rawAppId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
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
        return try {
            EpicCloudSavesManager.syncCloudSaves(context, appId, "download")
        } catch (e: Exception) {
            Timber.e(e, "Failed to force Epic cloud download for appId=%d", appId)
            false
        }
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
        return prefs.contains("synced_${gameSource}_$appId")
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
     * - **GOG**: defaults to `true` when local saves exist (no lightweight
     *   probe available; user is safely prompted).
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
                        val appId =
                            shortcut.getExtra("app_id").toIntOrNull()
                                ?: return@runBlocking false
                        // Returns null when service is unavailable → treat as "can't tell"
                        SteamService.cloudSavesDiffer(appId) ?: true
                    }

                    "EPIC" -> {
                        val appId =
                            shortcut.getExtra("app_id").toIntOrNull()
                                ?: return@runBlocking false
                        EpicCloudSavesManager.needsSync(context, appId)
                    }

                    // GOG has no lightweight probe; default to prompting user
                    "GOG" -> {
                        true
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
    fun getConflictTimestamps(
        context: Context,
        shortcut: Shortcut,
    ): CloudSyncConflictTimestamps =
        runBlocking {
            try {
                when (shortcut.getExtra("game_source")) {
                    "EPIC" -> {
                        val appId = shortcut.getExtra("app_id").toIntOrNull()
                        val saveDir = appId?.let { EpicCloudSavesManager.getResolvedSaveDirectory(context, it) }
                        val localNewest =
                            saveDir
                                ?.takeIf { it.exists() }
                                ?.walkTopDown()
                                ?.filter { it.isFile }
                                ?.maxOfOrNull(File::lastModified)
                        val prefs = context.getSharedPreferences("epic_games", Context.MODE_PRIVATE)
                        val cloudTimestamp =
                            prefs
                                .getString("sync_timestamp_${appId ?: 0}", null)
                                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                        CloudSyncConflictTimestamps(
                            localTimestampLabel = formatTimestamp(localNewest),
                            cloudTimestampLabel = formatTimestamp(cloudTimestamp),
                        )
                    }

                    "GOG" -> {
                        val appId = shortcut.getExtra("app_id").ifEmpty { shortcut.getExtra("gog_id") }
                        val installPath = shortcut.getExtra("game_install_path")
                        val localNewest =
                            installPath
                                .takeIf { it.isNotEmpty() }
                                ?.let { File(it) }
                                ?.takeIf { it.exists() }
                                ?.walkTopDown()
                                ?.filter { it.isFile }
                                ?.maxOfOrNull(File::lastModified)
                        val rawAppId = if (appId.startsWith("GOG_", ignoreCase = true)) appId else "GOG_$appId"
                        val gogManager = GOGService.getInstance()?.gogManager
                        val cloudTimestamp =
                            gogManager
                                ?.getCloudSaveSyncTimestamp(rawAppId, "default")
                                ?.toLongOrNull()
                                ?.times(1000)
                        CloudSyncConflictTimestamps(
                            localTimestampLabel = formatTimestamp(localNewest),
                            cloudTimestampLabel = formatTimestamp(cloudTimestamp),
                        )
                    }

                    "STEAM" -> {
                        val appId = shortcut.getExtra("app_id").toIntOrNull()
                        val localTracked =
                            appId
                                ?.let { SteamService.getTrackedCloudSaveFiles(it) }
                                ?.maxOfOrNull { it.timestamp }
                        CloudSyncConflictTimestamps(
                            localTimestampLabel = formatTimestamp(localTracked),
                            cloudTimestampLabel = "Unknown",
                        )
                    }

                    else -> {
                        CloudSyncConflictTimestamps("Unknown", "Unknown")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to build cloud conflict timestamps for %s", shortcut.name)
                CloudSyncConflictTimestamps("Unknown", "Unknown")
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
                    "STEAM" -> forceSteamDownload(context, shortcut)
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
}
