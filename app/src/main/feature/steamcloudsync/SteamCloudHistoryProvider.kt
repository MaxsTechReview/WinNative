package com.winlator.cmod.feature.steamcloudsync

import android.app.Activity
import android.content.Context
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupHistoryEntry
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupStorage
import com.winlator.cmod.runtime.container.Container
import `in`.dragonbra.javasteam.protobufs.steamclient.Enums.ECloudStoragePersistState
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest

/**
 * Provider for the Steam game "Save History" UI rows backed by **Steam Cloud's current
 * file listing**, not local rolling snapshots.
 *
 * Steam Cloud (per `steammessages_cloud.steamclient.proto`) keeps ONE record per filename
 * — there is no server-side version history. The user sees what they see on
 * `store.steampowered.com/account/remotestorageapp/?appid=X`: each file's name, size, and
 * last-modified time. To match the user's mental model of "the last 30 saves", we group
 * cloud files whose timestamps cluster within [GROUP_WINDOW_MS] of each other into one
 * "save event" entry — most games write all their save files together on Save, producing
 * one group per save action.
 *
 * Restore semantics: tapping Restore on a group calls [SteamCloudSyncHelper.forceDownloadById]
 * which syncs the entire current cloud state to local. Since Steam Cloud has the SAME current
 * state regardless of which group the user picks, the restore is effectively idempotent
 * across groups — the grouping is a visibility aid, not a per-group download primitive.
 * (Implementing per-group partial download would require duplicating the gzip/zip/fsync
 * download pipeline from `SteamAutoCloud.downloadFiles`; the user-visible result is
 * functionally identical because cloud is the single source of truth.)
 *
 * Rename: local-only label stored in SharedPrefs keyed on `<appId>:<groupId>`.
 * Delete: not supported (Steam manages cloud retention; the UI hides Delete for this storage).
 */
object SteamCloudHistoryProvider {
    private const val TAG = "SteamCloudHistory"

    /** Time window for grouping files into a "save event". 120s covers batched saves. */
    private const val GROUP_WINDOW_MS = 120_000L

    /** Maximum number of groups surfaced in the history UI. */
    private const val MAX_GROUPS = 30

    /** SharedPrefs file for user-set group labels (Steam Cloud has no native label support). */
    private const val LABEL_PREFS = "steam_cloud_history_labels"

    /**
     * Fetch the current Steam Cloud file listing for [appId] and group the files into
     * "save events" by timestamp clusters. Returns the [MAX_GROUPS] newest groups as
     * [BackupHistoryEntry] rows with `storage = BackupStorage.STEAM_CLOUD`.
     *
     * Returns an empty list if Steam is unavailable, the user isn't signed in, or the
     * cloud listing is empty.
     */
    suspend fun listCloudSaveGroups(
        context: Context,
        appId: Int,
    ): List<BackupHistoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                val response = SteamService.fetchCloudFileList(appId, 0L) ?: return@withContext emptyList()

                val persistedFiles: List<AppFileInfo> =
                    response.files
                        .filter { it.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStatePersisted }
                        .sortedByDescending { it.timestamp?.time ?: 0L }

                if (persistedFiles.isEmpty()) return@withContext emptyList()

                // Cluster files by timestamp proximity. Walk sorted-DESC files; each new file
                // either joins the open cluster (if its timestamp is within GROUP_WINDOW_MS of
                // the cluster's most-recent member) or starts a fresh cluster.
                class FileCluster {
                    val files = mutableListOf<AppFileInfo>()
                    val timestamps = mutableListOf<Long>()
                    fun representativeTs(): Long = timestamps.maxOrNull() ?: 0L
                    fun earliestTs(): Long = timestamps.minOrNull() ?: 0L
                }

                val clusters = mutableListOf<FileCluster>()
                for (file in persistedFiles) {
                    val ts: Long = file.timestamp?.time ?: continue
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

                clusters
                    .take(MAX_GROUPS)
                    .map { cluster ->
                        val sortedFilenames = cluster.files.map { it.filename }.sorted()
                        val groupId = buildGroupId(sortedFilenames, cluster.earliestTs())
                        val totalSize = cluster.files.sumOf { it.rawFileSize.toLong() }
                        val timestampMs = cluster.representativeTs()
                        val label = labelPrefs.getString("$appId:$groupId", null)
                        val firstFile = cluster.files.first().filename
                        val fileName =
                            if (cluster.files.size == 1) firstFile
                            else "$firstFile (+${cluster.files.size - 1} more)"
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
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listCloudSaveGroups failed for appId=%d", appId)
                emptyList()
            }
        }

    /**
     * Restore the cloud state by syncing the entire current cloud file set to local.
     *
     * **Why a full sync rather than a per-group download:** Steam Cloud only stores the
     * current version of each filename. Every group in the history list points at the SAME
     * current cloud state — they differ only in which subset of files was last modified in
     * a given time window. Doing a full sync is correct, cheap (we already have a robust
     * download pipeline), and gives the user identical behavior across group selections.
     *
     * Subsequent launch's conflict probe will see local == cloud → no spurious dialog.
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
                    // Honest copy: Steam Cloud only stores the current state, so this is what
                    // we actually did. Older "groups" in the history are visibility aids, not
                    // version-history rollback points (Steam doesn't keep prior versions).
                    BackupResult(true, "Synced current Steam Cloud state.")
                } else {
                    BackupResult(false, "Steam Cloud sync failed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreSaveGroup failed for appId=%d", appId)
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    /** Persist the user-set label for [groupFileId] (local SharedPrefs, never sent to Steam). */
    fun setLabel(context: Context, groupFileId: String, label: String?) {
        val prefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        if (label.isNullOrEmpty()) edit.remove(groupFileId) else edit.putString(groupFileId, label)
        edit.apply()
    }

    /**
     * Stable group identifier: SHA-256 of sorted filenames + earliest timestamp, truncated
     * to 16 hex chars. Stable across re-fetches as long as the cluster's file set and time
     * window don't change.
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
