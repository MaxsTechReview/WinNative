package com.winlator.cmod.feature.steamcloudsync

import android.app.Activity
import android.content.Context
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupHistoryEntry
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupStorage
import com.winlator.cmod.runtime.container.Container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Provides Steam Cloud entries for the save-history UI.
 *
 * Steam Cloud stores only the current version of each filename — no server-side version
 * history — so, like Steam's own remote-storage website, this lists one entry per file
 * currently in the cloud, each individually downloadable.
 *
 * Restoring syncs the full current cloud state through
 * [SteamCloudSyncHelper.forceDownloadById].
 *
 * Labels are stored locally in SharedPrefs. Delete is unsupported because Steam manages cloud
 * retention.
 *
 * Cloud file listings come from the C++ WN-Steam-Client through
 * [SteamService.fetchCloudFileList] and [SteamAutoCloud.CloudFileChangeList].
 */
object SteamCloudHistoryProvider {
    private const val TAG = "SteamCloudHistory"

    /** Maximum number of files shown in the history UI. */
    private const val MAX_FILES = 200

    private const val FILE_ID_SEPARATOR = ":file:"

    /** SharedPrefs file for user-set group labels. */
    private const val LABEL_PREFS = "steam_cloud_history_labels"

    sealed class HistoryResult {
        data class Entries(val list: List<BackupHistoryEntry>) : HistoryResult()
        object Empty : HistoryResult()
        object Unreachable : HistoryResult()
    }

    /**
     */
    suspend fun listCloudSaveGroupsDetailed(
        context: Context,
        appId: Int,
    ): HistoryResult =
        withContext(Dispatchers.IO) {
            try {
                val response = SteamService.fetchCloudFileList(appId, 0L)
                    ?: return@withContext HistoryResult.Unreachable
                val list = buildEntries(context, appId, response)
                if (list.isEmpty()) HistoryResult.Empty else HistoryResult.Entries(list)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listCloudSaveGroupsDetailed failed for appId=%d", appId)
                HistoryResult.Unreachable
            }
        }

    suspend fun listCloudSaveGroups(
        context: Context,
        appId: Int,
    ): List<BackupHistoryEntry> =
        when (val r = listCloudSaveGroupsDetailed(context, appId)) {
            is HistoryResult.Entries -> r.list
            HistoryResult.Empty, HistoryResult.Unreachable -> emptyList()
        }

    private fun toMillis(v: Long): Long {
        val ms = when {
            v <= 0L -> 0L
            v < 100_000_000_000L -> v * 1000L
            v > 100_000_000_000_000L -> v / 1000L
            else -> v
        }
        return if (ms > 4_102_444_800_000L) 0L else ms
    }

    /** The prefixed path used to download [file] from Steam Cloud (pathPrefix/filename). */
    private fun downloadPathFor(
        file: SteamAutoCloud.CloudFileInfo,
        response: SteamAutoCloud.CloudFileChangeList,
    ): String {
        val prefix = response.pathPrefixes.getOrNull(file.pathPrefixIndex).orEmpty()
        return if (prefix.isEmpty()) file.filename else "${prefix.trimEnd('/', '\\')}/${file.filename}"
    }

    /** Human-readable file path: root placeholders stripped, separators normalized. */
    private fun displayNameFor(downloadPath: String): String =
        downloadPath
            .replace(Regex("%\\w+%"), "")
            .replace('\\', '/')
            .trimStart('/')
            .ifEmpty { downloadPath }

    private fun buildEntries(
        context: Context,
        appId: Int,
        response: SteamAutoCloud.CloudFileChangeList,
    ): List<BackupHistoryEntry> {
        val persistedFiles: List<SteamAutoCloud.CloudFileInfo> =
            response.files
                .filter { it.isPersisted }
                .sortedByDescending { toMillis(it.timestamp) }

        if (persistedFiles.isEmpty()) return emptyList()

        val labelPrefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)

        return persistedFiles
            .take(MAX_FILES)
            .map { file ->
                val downloadPath = downloadPathFor(file, response)
                val fileId = "$appId$FILE_ID_SEPARATOR$downloadPath"
                BackupHistoryEntry(
                    fileId = fileId,
                    fileName = displayNameFor(downloadPath),
                    timestampMs = toMillis(file.timestamp),
                    origin = BackupOrigin.CLOUD,
                    sizeBytes = file.rawFileSize,
                    label = labelPrefs.getString(fileId, null),
                    storage = BackupStorage.STEAM_CLOUD,
                )
            }
    }

    /** Fetch the current cloud bytes for a [BackupHistoryEntry.fileId] produced by [buildEntries]. Null on failure. */
    suspend fun downloadFileBytes(
        appId: Int,
        fileId: String,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val downloadPath = fileId.substringAfter(FILE_ID_SEPARATOR, "").ifEmpty { return@withContext null }
            try {
                SteamService.downloadCloudFileBytes(appId, downloadPath)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "downloadFileBytes failed for appId=%d path=%s", appId, downloadPath)
                null
            }
        }

    /**
     * Restores by syncing the full current Steam Cloud file set to local.
     *
     * Steam Cloud does not expose per-group snapshots, so every history group restores the
     * same current cloud state.
     */
    suspend fun restoreSaveGroup(
        activity: Activity,
        appId: Int,
        @Suppress("UNUSED_PARAMETER") groupFileId: String,
        containerHint: Container? = null,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val ok = SteamCloudSyncHelper.forceDownloadById(activity, appId, containerHint)
                if (ok) {
                    BackupResult(true, "Synced current Steam Cloud state.")
                } else {
                    BackupResult(false, "Steam Cloud sync failed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreSaveGroup failed for appId=%d", appId)
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    /** Persists the user-set label for [groupFileId]. */
    fun setLabel(context: Context, groupFileId: String, label: String?) {
        val prefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        if (label.isNullOrEmpty()) edit.remove(groupFileId) else edit.putString(groupFileId, label)
        edit.apply()
    }
}
