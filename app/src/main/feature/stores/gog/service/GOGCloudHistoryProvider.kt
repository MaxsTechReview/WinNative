package com.winlator.cmod.feature.stores.gog.service

import android.content.Context
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupHistoryEntry
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

// GOG cloud storage has no per-snapshot history — entries mirror live cloud files
// and "restore" pulls the full current cloud state into the local save dir.
object GOGCloudHistoryProvider {
    private const val TAG = "GOGCloudHistory"
    private const val LABEL_PREFS = "gog_cloud_history_labels"

    suspend fun listCloudSaveGroups(
        context: Context,
        gameId: String,
        targetContainerId: Int? = null,
    ): List<BackupHistoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                val labelPrefs = context.getSharedPreferences(LABEL_PREFS, Context.MODE_PRIVATE)
                val normalizedAppId = if (gameId.startsWith("GOG_", ignoreCase = true)) gameId else "GOG_$gameId"
                GOGService
                    .listCloudSaveHistory(context, normalizedAppId, targetContainerId)
                    .map { entry ->
                        val fileId = encodeFileId(normalizedAppId, entry.locationName, entry.relativePath)
                        val displayName =
                            if (entry.locationName == "__default") {
                                entry.relativePath
                            } else {
                                "${entry.locationName}/${entry.relativePath}"
                            }
                        BackupHistoryEntry(
                            fileId = fileId,
                            fileName = displayName,
                            timestampMs = entry.timestampMs,
                            origin = BackupOrigin.CLOUD,
                            // GOG's listing API doesn't return file size; HEAD per object is too expensive.
                            sizeBytes = 0L,
                            label = labelPrefs.getString(fileId, null),
                            storage = BackupStorage.GOG_CLOUD,
                        )
                    }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listCloudSaveGroups failed for gameId=%s", gameId)
                emptyList()
            }
        }

    suspend fun restoreSaveGroup(
        context: Context,
        gameId: String,
        targetContainerId: Int? = null,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val normalizedAppId = if (gameId.startsWith("GOG_", ignoreCase = true)) gameId else "GOG_$gameId"
                val ok = GOGService.syncCloudSaves(context, normalizedAppId, "download", targetContainerId)
                if (ok) {
                    BackupResult(true, "Restored GOG cloud saves.")
                } else {
                    BackupResult(false, "GOG cloud restore failed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreSaveGroup failed for gameId=%s", gameId)
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
        appId: String,
        locationName: String,
        relativePath: String,
    ): String = "$appId|$locationName|$relativePath"
}
