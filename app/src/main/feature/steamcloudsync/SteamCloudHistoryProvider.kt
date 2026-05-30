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
import java.security.MessageDigest

/**
 * Provides Steam Cloud entries for the save-history UI.
 *
 * Steam Cloud stores the current version of each filename, not a server-side version history.
 * To approximate recent save events, files modified within [GROUP_WINDOW_MS] are grouped
 * together. This mirrors games that update several save files during one save action.
 *
 * Restoring any group syncs the full current cloud state through
 * [SteamCloudSyncHelper.forceDownloadById]. Groups only describe what changed together; they
 * are not independently restorable snapshots.
 *
 * Labels are stored locally in SharedPrefs. Delete is unsupported because Steam manages cloud
 * retention.
 *
 * Cloud file listings come from the C++ WN-Steam-Client through
 * [SteamService.fetchCloudFileList] and [SteamAutoCloud.CloudFileChangeList].
 */
object SteamCloudHistoryProvider {
    private const val TAG = "SteamCloudHistory"

    /** Time window for grouping files into one save event. */
    private const val GROUP_WINDOW_MS = 120_000L

    /** Maximum number of groups shown in the history UI. */
    private const val MAX_GROUPS = 30

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

        class FileCluster {
            val files = mutableListOf<SteamAutoCloud.CloudFileInfo>()
            val timestamps = mutableListOf<Long>()
            fun representativeTs(): Long = timestamps.maxOrNull() ?: 0L
            fun earliestTs(): Long = timestamps.minOrNull() ?: 0L
        }

        val clusters = mutableListOf<FileCluster>()
        for (file in persistedFiles) {
            val ts: Long = toMillis(file.timestamp).takeIf { it > 0L } ?: continue
            val current: FileCluster? = clusters.lastOrNull()
            val joinsCurrent: Boolean =
                current != null && (current.representativeTs() - ts) <= GROUP_WINDOW_MS
            val target: FileCluster =
                if (joinsCurrent) {
                    current!!
                } else {
                    FileCluster().also { clusters += it }
                }
            target.files += file
            target.timestamps += ts
        }

        val labelPrefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)

        return clusters
            .take(MAX_GROUPS)
            .map { cluster ->
                val sortedFilenames = cluster.files.map { it.filename }.sorted()
                val groupId = buildGroupId(sortedFilenames, cluster.earliestTs())
                val totalSize = cluster.files.sumOf { it.rawFileSize }
                val timestampMs = cluster.representativeTs()
                val label = labelPrefs.getString("$appId:$groupId", null)
                val firstFile = cluster.files.first().filename
                val fileName =
                    if (cluster.files.size == 1) {
                        firstFile
                    } else {
                        "$firstFile (+${cluster.files.size - 1} more)"
                    }
                BackupHistoryEntry(
                    fileId = "$appId:$groupId",
                    fileName = fileName,
                    timestampMs = timestampMs,
                    origin = BackupOrigin.CLOUD,
                    sizeBytes = totalSize,
                    label = label,
                    storage = BackupStorage.STEAM_CLOUD,
                )
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

    /**
     * Builds a stable ID from the cluster's sorted filenames and earliest timestamp.
     */
    private fun buildGroupId(sortedFilenames: List<String>, earliestTs: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(earliestTs.toString().toByteArray(Charsets.UTF_8))
        md.update(0)
        sortedFilenames.forEach {
            md.update(it.toByteArray(Charsets.UTF_8))
            md.update(0)
        }
        return md.digest().copyOfRange(0, 8).joinToString("") { "%02x".format(it) }
    }
}
