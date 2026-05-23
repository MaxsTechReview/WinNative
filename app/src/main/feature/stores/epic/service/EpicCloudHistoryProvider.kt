package com.winlator.cmod.feature.stores.epic.service

import android.content.Context
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupHistoryEntry
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Provider for the Epic game "Save History" UI rows backed by Epic savesync manifests.
 *
 * Epic cloud saves store timestamped manifest files plus chunks. The manifest list is
 * the closest thing Epic exposes to save history, and each row can restore that specific
 * manifest instead of only syncing the latest cloud state.
 */
object EpicCloudHistoryProvider {
    private const val TAG = "EpicCloudHistory"
    private const val LABEL_PREFS = "epic_cloud_history_labels"

    suspend fun listCloudSaveGroups(
        context: Context,
        appId: Int,
    ): List<BackupHistoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                val labelPrefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)
                EpicCloudSavesManager
                    .listCloudSaveHistory(context, appId)
                    .map { entry ->
                        val fileId = encodeFileId(appId, entry.manifestPath)
                        val fileCountLabel =
                            when (entry.fileCount) {
                                0 -> entry.manifestPath.substringAfterLast("/")
                                1 -> "1 file"
                                else -> "${entry.fileCount} files"
                            }
                        BackupHistoryEntry(
                            fileId = fileId,
                            fileName = fileCountLabel,
                            timestampMs = entry.timestampMs,
                            origin = BackupOrigin.CLOUD,
                            sizeBytes = entry.sizeBytes,
                            label = labelPrefs.getString(fileId, null),
                            storage = BackupStorage.EPIC_CLOUD,
                        )
                    }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listCloudSaveGroups failed for appId=%d", appId)
                emptyList()
            }
        }

    suspend fun restoreSaveGroup(
        context: Context,
        appId: Int,
        groupFileId: String,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val manifestPath = decodeManifestPath(groupFileId)
                if (manifestPath.isBlank()) return@withContext BackupResult(false, "Invalid Epic cloud save entry.")
                val ok = EpicCloudSavesManager.restoreCloudSaveHistoryEntry(context, appId, manifestPath)
                if (ok) {
                    BackupResult(true, "Restored Epic cloud save.")
                } else {
                    BackupResult(false, "Epic cloud restore failed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreSaveGroup failed for appId=%d", appId)
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    fun setLabel(
        context: Context,
        groupFileId: String,
        label: String?,
    ) {
        val prefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        if (label.isNullOrEmpty()) edit.remove(groupFileId) else edit.putString(groupFileId, label)
        edit.apply()
    }

    private fun encodeFileId(
        appId: Int,
        manifestPath: String,
    ): String = "$appId:$manifestPath"

    private fun decodeManifestPath(groupFileId: String): String =
        groupFileId.substringAfter(":", missingDelimiterValue = groupFileId)
}
