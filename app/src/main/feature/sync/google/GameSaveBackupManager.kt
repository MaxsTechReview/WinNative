package com.winlator.cmod.feature.sync.google
import android.app.Activity
import android.content.Context
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Tasks
import com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.winlator.cmod.feature.stores.steam.utils.FileUtils as SteamFileUtils

/**
 * Manages backup and restore of individual game cloud saves to/from
 * Google Play Games Services Saved Games (the same API used by store-token sync).
 *
 * For each game we keep:
 *   - **Manifest snapshot** `gs_<src>_<idHash>_m`: gzip-JSON list of history entries.
 *   - **Part snapshots** `gs_<src>_<idHash>_<histId>_p<N>`: 1..M chunks of the save zip.
 *
 * PGS caps each snapshot at 3 MB of binary data, so saves are split across parts and
 * reassembled on restore. The manifest is the single source of truth for which parts
 * are "live"; orphaned parts (left over from interrupted writes or pruned history) are
 * cleaned up by [gcOrphanedParts] on every successful backup, list, and delete.
 *
 * Public API is preserved from the prior Drive implementation so callers in
 * UnifiedActivity, GoogleScreen, CloudSyncConflictDialog, etc. compile unchanged.
 *
 * @see PgsSnapshotIO for the underlying snapshot open/commit/conflict-resolve helpers.
 */
object GameSaveBackupManager {
    private const val TAG = "GameSaveBackup"

    private const val PREFS_NAME = "google_store_login_sync"
    private const val KEY_GOOGLE_SYNC_ENABLED = "google_sync_enabled"
    private const val KEY_KEEP_REPLACED_BACKUP = "cloud_sync_keep_replaced_backup"

    /** Maximum number of history entries retained (and shown) per game. */
    const val MAX_HISTORY_ENTRIES = 30

    /** Entries older than this are pruned whenever history is listed or written. */
    const val HISTORY_MAX_AGE_DAYS = 30

    /** Legacy constant from the Drive era; no longer used but kept for source-compat. */
    @Suppress("unused")
    const val REQUEST_CODE_DRIVE_AUTH = 9002

    /** Max length of a user-provided history-entry label, after sanitization. */
    const val MAX_HISTORY_LABEL_LENGTH = 48

    // PGS data cap is 3 MB per snapshot; reserve 64 KB headroom for any framing overhead.
    private const val MAX_PART_BYTES: Int = 3 * 1024 * 1024 - 64 * 1024
    // Cap GC deletes per backup to avoid quota churn on heavy users.
    private const val GC_DELETE_BUDGET_PER_RUN = 50
    private const val MANIFEST_VERSION = 1

    enum class GameSource { STEAM, EPIC, GOG, CUSTOM }

    /** Origin of a history backup — identifies which side of a conflict it came from. */
    enum class BackupOrigin(val tag: String) {
        LOCAL("local"),
        CLOUD("cloud"),
        MANUAL("manual"),
        AUTO("auto"),
        ;

        companion object {
            fun fromTag(tag: String?): BackupOrigin? = entries.firstOrNull { it.tag == tag }
        }
    }

    data class BackupResult(
        val success: Boolean,
        val message: String,
    )

    /**
     * A backed-up save stored as one manifest-entry plus N part-snapshots in PGS.
     *
     * [fileId] is the manifest entry id (a `<histId>` like `t20260429143022123_4f1a`).
     * The legacy field name is preserved so existing call sites (UnifiedActivity)
     * continue to compile unchanged. [gameSource]/[gameId]/[gameName] are populated
     * by [listBackupHistory] so [renameBackupHistoryEntry]/[deleteBackupHistoryEntry]
     * can locate the manifest.
     */
    data class BackupHistoryEntry(
        val fileId: String,
        val fileName: String,
        val timestampMs: Long,
        val origin: BackupOrigin,
        val sizeBytes: Long,
        val label: String? = null,
        // PGS lookup context (null only for legacy-constructed entries; populated by listBackupHistory)
        internal val gameSource: GameSource? = null,
        internal val gameId: String? = null,
        internal val gameName: String? = null,
        // CUSTOM-only: the drive_c-relative path captured at backup time (so restores
        // always go back to where the backup came from, regardless of later changes
        // to the shortcut's current `custom_save_path`).
        internal val customRelPath: String? = null,
    )

    private data class SaveBackupSource(
        val zipRoot: String,
        val localDir: File,
        val exactFiles: List<File>? = null,
    )

    // ── Public API ──────────────────────────────────────────────────────────

    /** Manual backup — same effect as [backupDiscardedSave] with origin=MANUAL. */
    suspend fun backupToGoogle(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult = backupDiscardedSave(activity, gameSource, gameId, gameName, BackupOrigin.MANUAL)

    /**
     * Exit auto-backup: zips local saves and writes them to Save History with
     * origin=AUTO. Gated by the global [isAutoBackupEnabled] setting.
     */
    suspend fun autoBackupToGoogle(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult {
        val context = activity.applicationContext
        if (!isGoogleSyncEnabled(context)) {
            return BackupResult(false, "Google sync is not enabled.")
        }
        if (!isAutoBackupEnabled(context)) {
            return BackupResult(false, "Auto backup is not enabled.")
        }
        return backupDiscardedSave(activity, gameSource, gameId, gameName, BackupOrigin.AUTO)
    }

    fun isAutoBackupEnabled(context: Context): Boolean = prefs(context).getBoolean("cloud_sync_auto_backup", false)

    /**
     * Probe Play Games authentication. Now that game saves use PGS (not Drive), this
     * function returns true when the user is signed in to Play Games — kept named
     * `requestDriveAuthorization` for source compatibility with existing callers.
     */
    suspend fun requestDriveAuthorization(activity: Activity): Boolean =
        PgsSnapshotIO.awaitAuthenticatedSession(activity)

    /**
     * Push the current on-disk save up to the store provider (Steam / Epic / GOG).
     *
     * Does **not** touch PGS — pure "sync up" used when the user wants the provider's
     * cloud to match what's on disk. Kept named `restoreFromGoogle` for stable callers.
     */
    suspend fun restoreFromGoogle(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        @Suppress("UNUSED_PARAMETER") gameName: String,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext
                val ok = syncUpToProvider(context, gameSource, gameId)
                if (ok) {
                    BackupResult(true, "Save pushed to ${gameSource.name}.")
                } else {
                    BackupResult(false, "Failed to push save to ${gameSource.name}.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreFromGoogle (push) failed for $gameSource/$gameId")
                BackupResult(false, "Push failed: ${e.message}")
            }
        }

    /**
     * Legacy callback from the Drive consent flow. Now a no-op — kept so existing
     * call sites in UnifiedActivity compile.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onDriveAuthResult(
        activity: Activity,
        resultCode: Int,
    ) {
        // Drive consent path is gone; PGS sign-in is handled elsewhere.
    }

    /** Whether the conflict dialog checkbox "Keep a backup of the replaced save" is on. */
    fun isKeepReplacedBackupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_REPLACED_BACKUP, true)

    fun setKeepReplacedBackupEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_KEEP_REPLACED_BACKUP, enabled).apply()
    }

    /**
     * Snapshot the current local save files (as they exist on disk right now) and
     * upload to PGS as a new history entry plus N part snapshots.
     *
     * Does NOT sync with the provider first. Intended to be called just before an
     * operation that is about to overwrite local saves (e.g. conflict "Use Cloud"),
     * or from the manual/auto backup path to record an auditable history entry.
     */
    suspend fun backupDiscardedSave(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
        origin: BackupOrigin,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext
                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled.")
                }
                if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val client = PgsSnapshotIO.freshSnapshotsClient(activity)
                    ?: return@withContext BackupResult(false, "Activity is no longer active.")

                // For CUSTOM games, snapshot the current `custom_save_path` synchronously
                // here (before any async work) so it can't change mid-upload. We then pass
                // the same value into both getLocalSaveSources (for the live read) and the
                // ManifestEntry (so restore later finds the same path).
                val customRelPathSnapshot: String? =
                    if (gameSource == GameSource.CUSTOM) {
                        val sc = findCustomShortcutByUuid(context, gameId)
                        val ct = sc?.container
                        if (sc == null || ct == null) {
                            return@withContext BackupResult(false, "Shortcut not found for backup.")
                        }
                        val raw = sc.getExtra("custom_save_path")
                        CustomSavePath.normalizeAndValidate(raw, ct)
                            ?: return@withContext BackupResult(false, "Save folder not configured for this game.")
                    } else null

                val saveSources =
                    if (gameSource == GameSource.CUSTOM) {
                        getCustomSaveSources(context, gameId, customRelPathOverride = customRelPathSnapshot)
                    } else {
                        getLocalSaveSources(context, gameSource, gameId, forRestore = false)
                    }
                if (saveSources.isEmpty()) {
                    return@withContext BackupResult(false, "No local save files found to back up.")
                }

                // 1) Tree fingerprint over the *unchanged* local files (deterministic across runs).
                val fingerprint = computeTreeFingerprint(saveSources)

                // 2) Read existing manifest; de-dupe if latest entry has same fingerprint.
                val manifestName = manifestSnapshotName(gameSource, gameId, gameName)
                val existing = readManifest(client, manifestName)
                    ?: ManifestData(
                        version = MANIFEST_VERSION,
                        gameKey = manifestGameKey(gameSource, gameId, gameName),
                        history = emptyList(),
                    )
                if (existing.history.firstOrNull()?.fingerprint == fingerprint) {
                    Timber.tag(TAG).i(
                        "De-dupe skip for %s/%s: tree fingerprint matches latest history entry",
                        gameSource, gameId,
                    )
                    return@withContext BackupResult(true, "Save unchanged since last backup.")
                }

                // 3) Build the zip and slice into parts.
                val zipBytes = zipSaveSources(saveSources)
                if (zipBytes.isEmpty()) {
                    return@withContext BackupResult(false, "Save files are empty.")
                }
                val partCount = ((zipBytes.size + MAX_PART_BYTES - 1) / MAX_PART_BYTES).coerceAtLeast(1)
                val histId = newHistId()

                // 4) Write parts. On any failure, return without committing manifest — orphans cleaned by GC.
                for (i in 1..partCount) {
                    val start = (i - 1) * MAX_PART_BYTES
                    val end = minOf(i * MAX_PART_BYTES, zipBytes.size)
                    val slice = zipBytes.copyOfRange(start, end)
                    val partName = partSnapshotName(gameSource, gameId, gameName, histId, i)
                    val written = writeSnapshotBytes(
                        client = PgsSnapshotIO.freshSnapshotsClient(activity)
                            ?: return@withContext BackupResult(false, "Activity is no longer active."),
                        name = partName,
                        bytes = slice,
                        description = "Game save part $i/$partCount",
                        progressValue = partCount.toLong(),
                    )
                    if (!written) {
                        return@withContext BackupResult(false, "Failed to upload save part $i of $partCount.")
                    }
                }

                // 5) Update manifest (prepend new entry, prune old entries).
                val newEntry = ManifestEntry(
                    id = histId,
                    createdAt = System.currentTimeMillis(),
                    origin = origin,
                    label = null,
                    totalBytes = zipBytes.size.toLong(),
                    partCount = partCount,
                    fingerprint = fingerprint,
                    customRelPath = customRelPathSnapshot,
                )
                val (kept, pruned) = pruneHistory(listOf(newEntry) + existing.history)
                val updated = existing.copy(history = kept)
                if (!writeManifest(
                        client = PgsSnapshotIO.freshSnapshotsClient(activity)
                            ?: return@withContext BackupResult(false, "Activity is no longer active."),
                        name = manifestName,
                        manifest = updated,
                    )
                ) {
                    return@withContext BackupResult(false, "Failed to update backup manifest.")
                }

                // 6) Best-effort: delete parts of pruned entries.
                pruned.forEach { entry ->
                    for (i in 1..entry.partCount) {
                        val n = partSnapshotName(gameSource, gameId, gameName, entry.id, i)
                        runCatching {
                            PgsSnapshotIO.freshSnapshotsClient(activity)?.let { c ->
                                PgsSnapshotIO.deleteSnapshotByName(c, n)
                            }
                        }.onFailure { Timber.tag(TAG).w(it, "Failed to delete pruned part %s", n) }
                    }
                }

                // 7) Best-effort: GC any orphaned parts not referenced by the live manifest.
                runCatching {
                    PgsSnapshotIO.freshSnapshotsClient(activity)?.let { c ->
                        gcOrphanedParts(c, gameSource, gameId, gameName, updated)
                    }
                }.onFailure { Timber.tag(TAG).w(it, "GC failed") }

                BackupResult(true, "Save backed up to Save History.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "backupDiscardedSave failed for $gameSource/$gameId")
                BackupResult(false, "Failed to back up save: ${e.message}")
            }
        }

    /**
     * Lists up to [MAX_HISTORY_ENTRIES] backup entries for the given game, newest first.
     * Also performs a best-effort prune of entries older than [HISTORY_MAX_AGE_DAYS]
     * and an orphan-parts GC.
     */
    suspend fun listBackupHistory(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): List<BackupHistoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext
                if (!isGoogleSyncEnabled(context)) return@withContext emptyList()
                if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) return@withContext emptyList()
                val client = PgsSnapshotIO.freshSnapshotsClient(activity) ?: return@withContext emptyList()

                val manifestName = manifestSnapshotName(gameSource, gameId, gameName)
                val manifest = readManifest(client, manifestName) ?: return@withContext emptyList()

                // Auto-prune on read.
                val (kept, pruned) = pruneHistory(manifest.history)
                if (pruned.isNotEmpty()) {
                    val updated = manifest.copy(history = kept)
                    PgsSnapshotIO.freshSnapshotsClient(activity)?.let {
                        runCatching { writeManifest(it, manifestName, updated) }
                    }
                    pruned.forEach { entry ->
                        for (i in 1..entry.partCount) {
                            val n = partSnapshotName(gameSource, gameId, gameName, entry.id, i)
                            runCatching {
                                PgsSnapshotIO.freshSnapshotsClient(activity)?.let { c ->
                                    PgsSnapshotIO.deleteSnapshotByName(c, n)
                                }
                            }
                        }
                    }
                    runCatching {
                        PgsSnapshotIO.freshSnapshotsClient(activity)?.let { c ->
                            gcOrphanedParts(c, gameSource, gameId, gameName, updated)
                        }
                    }
                }

                kept.map { entry ->
                    BackupHistoryEntry(
                        fileId = entry.id,
                        fileName = humanFileName(entry),
                        timestampMs = entry.createdAt,
                        origin = entry.origin,
                        sizeBytes = entry.totalBytes,
                        label = entry.label,
                        gameSource = gameSource,
                        gameId = gameId,
                        gameName = gameName,
                        customRelPath = entry.customRelPath,
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listBackupHistory failed for $gameSource/$gameId")
                emptyList()
            }
        }

    /**
     * Download every part of a specific backup, reassemble the zip, and unzip it
     * over the local save directory. Existing local files are overwritten.
     */
    suspend fun restoreFromHistoryEntry(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        entry: BackupHistoryEntry,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext
                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled.")
                }
                if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val client = PgsSnapshotIO.freshSnapshotsClient(activity)
                    ?: return@withContext BackupResult(false, "Activity is no longer active.")

                val gameName = entry.gameName ?: ""
                val manifestName = manifestSnapshotName(gameSource, gameId, gameName)
                val manifest = readManifest(client, manifestName)
                    ?: return@withContext BackupResult(false, "No backup history found for this game.")

                val mEntry = manifest.history.firstOrNull { it.id == entry.fileId }
                    ?: return@withContext BackupResult(false, "Backup entry no longer exists.")
                if (mEntry.partCount <= 0) {
                    return@withContext BackupResult(false, "Backup entry is empty.")
                }

                val baos = ByteArrayOutputStream(mEntry.totalBytes.toInt().coerceAtLeast(0))
                for (i in 1..mEntry.partCount) {
                    val partName = partSnapshotName(gameSource, gameId, gameName, mEntry.id, i)
                    val readClient = PgsSnapshotIO.freshSnapshotsClient(activity)
                        ?: return@withContext BackupResult(false, "Activity is no longer active.")
                    val partBytes = readSnapshotBytes(readClient, partName)
                        ?: return@withContext BackupResult(false, "Backup part $i is missing or unreadable.")
                    baos.write(partBytes)
                }
                val zipBytes = baos.toByteArray()
                if (zipBytes.size.toLong() != mEntry.totalBytes) {
                    Timber.tag(TAG).w(
                        "Restore size mismatch for %s: expected %d, got %d",
                        mEntry.id, mEntry.totalBytes, zipBytes.size,
                    )
                }

                val saveSources =
                    if (gameSource == GameSource.CUSTOM) {
                        // Use the path captured at backup time (per-entry), not the
                        // shortcut's current `custom_save_path`. Re-validate every read.
                        // Fail clearly if missing/invalid — never fall back to drive_c
                        // or to the shortcut's current default.
                        val recorded = mEntry.customRelPath
                            ?: return@withContext BackupResult(
                                false,
                                "This backup is missing its target save folder and can't be restored.",
                            )
                        val sc = findCustomShortcutByUuid(context, gameId)
                        val ct = sc?.container
                            ?: return@withContext BackupResult(false, "Game shortcut not found.")
                        val validatedRel = CustomSavePath.normalizeAndValidate(recorded, ct)
                            ?: return@withContext BackupResult(
                                false,
                                "This backup's recorded save folder is invalid for the current container.",
                            )
                        getCustomSaveSources(context, gameId, customRelPathOverride = validatedRel)
                    } else {
                        getLocalSaveSources(context, gameSource, gameId, forRestore = true)
                    }
                if (saveSources.isEmpty()) {
                    return@withContext BackupResult(false, "Cannot determine save directory for this game.")
                }
                // Ensure the target dir exists for CUSTOM (mkdirs on first restore)
                if (gameSource == GameSource.CUSTOM) {
                    saveSources.firstOrNull()?.localDir?.mkdirs()
                }
                unzipToSources(zipBytes, saveSources)
                BackupResult(true, "Save restored from backup.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreFromHistoryEntry failed for $gameSource/$gameId")
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    /**
     * Set or clear the user label on a history entry by mutating the manifest.
     * No part snapshots are touched.
     */
    suspend fun renameBackupHistoryEntry(
        activity: Activity,
        entry: BackupHistoryEntry,
        newLabel: String?,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext
                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled.")
                }
                if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val client = PgsSnapshotIO.freshSnapshotsClient(activity)
                    ?: return@withContext BackupResult(false, "Activity is no longer active.")

                val source = entry.gameSource
                    ?: return@withContext BackupResult(false, "Entry is missing game context.")
                val gameId = entry.gameId
                    ?: return@withContext BackupResult(false, "Entry is missing game id.")
                val gameName = entry.gameName ?: ""
                val manifestName = manifestSnapshotName(source, gameId, gameName)
                val manifest = readManifest(client, manifestName)
                    ?: return@withContext BackupResult(false, "No backup history found.")

                val cleanLabel = sanitizeHistoryLabel(newLabel)
                val updated =
                    manifest.copy(
                        history =
                            manifest.history.map {
                                if (it.id == entry.fileId) it.copy(label = cleanLabel) else it
                            },
                    )
                val ok = writeManifest(client, manifestName, updated)
                if (ok) {
                    BackupResult(true, if (cleanLabel.isNullOrEmpty()) "Label cleared." else "Renamed.")
                } else {
                    BackupResult(false, "Rename failed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "renameBackupHistoryEntry failed for %s", entry.fileName)
                BackupResult(false, "Rename failed: ${e.message}")
            }
        }

    /** Permanently delete a Save History entry: drop from manifest then delete its parts. */
    suspend fun deleteBackupHistoryEntry(
        activity: Activity,
        entry: BackupHistoryEntry,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext
                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled.")
                }
                if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val client = PgsSnapshotIO.freshSnapshotsClient(activity)
                    ?: return@withContext BackupResult(false, "Activity is no longer active.")

                val source = entry.gameSource
                    ?: return@withContext BackupResult(false, "Entry is missing game context.")
                val gameId = entry.gameId
                    ?: return@withContext BackupResult(false, "Entry is missing game id.")
                val gameName = entry.gameName ?: ""
                val manifestName = manifestSnapshotName(source, gameId, gameName)
                val manifest = readManifest(client, manifestName)
                    ?: return@withContext BackupResult(false, "No backup history found.")

                val target = manifest.history.firstOrNull { it.id == entry.fileId }
                    ?: return@withContext BackupResult(true, "Backup already removed.")

                val updated = manifest.copy(history = manifest.history - target)
                val writeClient = PgsSnapshotIO.freshSnapshotsClient(activity)
                    ?: return@withContext BackupResult(false, "Activity is no longer active.")
                val ok = writeManifest(writeClient, manifestName, updated)
                if (!ok) {
                    return@withContext BackupResult(false, "Delete failed (manifest write).")
                }

                for (i in 1..target.partCount) {
                    val partName = partSnapshotName(source, gameId, gameName, target.id, i)
                    runCatching {
                        PgsSnapshotIO.freshSnapshotsClient(activity)?.let { c ->
                            PgsSnapshotIO.deleteSnapshotByName(c, partName)
                        }
                    }.onFailure { Timber.tag(TAG).w(it, "Failed to delete part %s", partName) }
                }

                runCatching {
                    PgsSnapshotIO.freshSnapshotsClient(activity)?.let { c ->
                        gcOrphanedParts(c, source, gameId, gameName, updated)
                    }
                }.onFailure { Timber.tag(TAG).w(it, "GC after delete failed") }

                BackupResult(true, "Backup deleted.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "deleteBackupHistoryEntry failed for %s", entry.fileName)
                BackupResult(false, "Delete failed: ${e.message}")
            }
        }

    /**
     * Strip out filename-hostile characters (path separators, control chars) and
     * cap length. Returns null if the result would be empty.
     */
    fun sanitizeHistoryLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned =
            raw
                .replace(Regex("""[/\\:*?"<>|\r\n\t]"""), "")
                .trim()
                .take(MAX_HISTORY_LABEL_LENGTH)
        return cleaned.ifEmpty { null }
    }

    // ── Provider sync helpers ───────────────────────────────────────────────

    private suspend fun syncUpToProvider(
        context: Context,
        source: GameSource,
        gameId: String,
    ): Boolean {
        return try {
            when (source) {
                GameSource.STEAM -> {
                    val appId = gameId.toIntOrNull() ?: return false
                    SteamService.syncCloudSavesForBackup(context, appId, "upload")
                }
                GameSource.EPIC -> {
                    val appId = gameId.toIntOrNull() ?: return false
                    EpicCloudSavesManager.syncCloudSaves(context, appId, "upload")
                }
                GameSource.GOG -> {
                    GOGService.syncCloudSaves(context, "GOG_$gameId", "upload")
                }
                // Custom games never push-to-provider — there is no provider.
                GameSource.CUSTOM -> false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "syncUpToProvider failed for $source/$gameId")
            false
        }
    }

    // ── Local save directory resolution ─────────────────────────────────────

    private suspend fun getLocalSaveSources(
        context: Context,
        source: GameSource,
        gameId: String,
        forRestore: Boolean,
    ): List<SaveBackupSource> =
        when (source) {
            GameSource.STEAM -> getSteamSaveSources(context, gameId, forRestore)
            GameSource.EPIC -> getEpicSaveSources(context, gameId, forRestore)
            GameSource.GOG -> getGogSaveSources(context, gameId, forRestore)
            // CUSTOM: backup uses live shortcut path; restore uses entry's recorded
            // path and bypasses this helper. customRelPath = null here means "use the
            // shortcut's current `custom_save_path`".
            GameSource.CUSTOM -> getCustomSaveSources(context, gameId, customRelPathOverride = null)
        }

    /**
     * Resolve the SaveBackupSource list for a CUSTOM game.
     *
     * - Looks up the [Shortcut] by uuid (= [gameId]).
     * - Uses [customRelPathOverride] if non-null (used during restore so old backups
     *   always go back to the path captured at backup time, even if the user has
     *   since changed the shortcut's `custom_save_path`). Otherwise reads the
     *   shortcut's current `custom_save_path`.
     * - Re-validates the path through [CustomSavePath.normalizeAndValidate] every
     *   call (treats both stored and manifest values as untrusted).
     * - Returns an empty list if the shortcut, container, or path can't be resolved.
     */
    private fun getCustomSaveSources(
        context: Context,
        gameId: String,
        customRelPathOverride: String?,
    ): List<SaveBackupSource> {
        if (gameId.isBlank()) return emptyList()
        val shortcut = findCustomShortcutByUuid(context, gameId) ?: return emptyList()
        val container = shortcut.container ?: return emptyList()
        val raw = customRelPathOverride ?: shortcut.getExtra("custom_save_path")
        val rel = CustomSavePath.normalizeAndValidate(raw, container) ?: return emptyList()
        val absDir = CustomSavePath.resolveAbsolute(rel, container)
        val zipRoot = "custom/${gameId.take(12).ifBlank { "x" }}"
        return listOf(SaveBackupSource(zipRoot = zipRoot, localDir = absDir))
    }

    /**
     * Look up a custom shortcut by its uuid extra. Returns null if not found.
     * Avoids loading shortcuts repeatedly within a single call by reading the
     * container manager once per invocation.
     */
    private fun findCustomShortcutByUuid(
        context: Context,
        uuid: String,
    ): com.winlator.cmod.runtime.container.Shortcut? {
        if (uuid.isBlank()) return null
        return runCatching {
            com.winlator.cmod.runtime.container.ContainerManager(context)
                .loadShortcuts()
                .firstOrNull { it.getExtra("uuid") == uuid }
        }.getOrNull()
    }

    private suspend fun getSteamSaveSources(
        context: Context,
        gameId: String,
        forRestore: Boolean,
    ): List<SaveBackupSource> {
        val appId = gameId.toIntOrNull() ?: return emptyList()
        val sources = linkedMapOf<String, SaveBackupSource>()
        val appDir = SteamService.getAppDirPath(appId)
        val goldbergSaves = File(appDir, "steam_settings/saves")
        if (forRestore || (goldbergSaves.exists() && !goldbergSaves.listFiles().isNullOrEmpty())) {
            sources["steam/steam_settings/saves"] =
                SaveBackupSource(
                    zipRoot = "steam/steam_settings/saves",
                    localDir = goldbergSaves,
                )
        }

        val accountId =
            SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L
        val prefixToPath: (String) -> String = { prefix ->
            PathType.from(prefix).toAbsPath(context, appId, accountId)
        }

        val trackedFiles = SteamService.getTrackedCloudSaveFiles(appId).orEmpty()
        if (trackedFiles.isNotEmpty()) {
            trackedFiles
                .groupBy { it.root to it.substitutedPath }
                .forEach { (key, files) ->
                    val (root, substitutedPath) = key
                    val localDir = File(Paths.get(prefixToPath(root.toString()), substitutedPath).toString())
                    val zipRoot = buildSteamZipRoot(root, substitutedPath)
                    val exactFiles =
                        files
                            .map { it.getAbsPath(prefixToPath) }
                            .map { it.toFile() }
                            .filter { forRestore || it.exists() }
                    if (forRestore || exactFiles.isNotEmpty()) {
                        sources[zipRoot] = SaveBackupSource(zipRoot, localDir, exactFiles)
                    }
                }
        } else {
            val appInfo = SteamService.getAppInfoOf(appId)
            val savePatterns =
                appInfo
                    ?.ufs
                    ?.saveFilePatterns
                    .orEmpty()
                    .filter { it.root.isWindows }
            if (savePatterns.isNotEmpty()) {
                savePatterns.groupBy { it.root to it.substitutedPath }.forEach { (key, patterns) ->
                    val (root, substitutedPath) = key
                    val localDir = File(Paths.get(prefixToPath(root.toString()), substitutedPath).toString())
                    val exactFiles = mutableListOf<File>()
                    patterns.forEach { pattern ->
                        if (localDir.exists()) {
                            SteamFileUtils
                                .findFilesRecursive(
                                    rootPath = localDir.toPath(),
                                    pattern = pattern.pattern,
                                    maxDepth = if (pattern.recursive != 0) 5 else 0,
                                ).forEach { path ->
                                    exactFiles += path.toFile()
                                }
                        }
                    }
                    if (forRestore || exactFiles.isNotEmpty()) {
                        sources[buildSteamZipRoot(root, substitutedPath)] =
                            SaveBackupSource(
                                zipRoot = buildSteamZipRoot(root, substitutedPath),
                                localDir = localDir,
                                exactFiles = exactFiles.distinct(),
                            )
                    }
                }
            } else {
                val steamUserDataDir = File(PathType.SteamUserData.toAbsPath(context, appId, accountId))
                if (forRestore || (steamUserDataDir.exists() && !steamUserDataDir.listFiles().isNullOrEmpty())) {
                    sources["steam/${PathType.SteamUserData.name}"] =
                        SaveBackupSource(
                            zipRoot = "steam/${PathType.SteamUserData.name}",
                            localDir = steamUserDataDir,
                        )
                }
            }
        }

        return sources.values.toList()
    }

    private suspend fun getEpicSaveSources(
        context: Context,
        gameId: String,
        forRestore: Boolean,
    ): List<SaveBackupSource> {
        val appId = gameId.toIntOrNull() ?: return emptyList()
        val saveDir = EpicCloudSavesManager.getResolvedSaveDirectory(context, appId) ?: return emptyList()
        return if (forRestore || (saveDir.exists() && !saveDir.listFiles().isNullOrEmpty())) {
            listOf(SaveBackupSource("epic/save", saveDir))
        } else {
            emptyList()
        }
    }

    private suspend fun getGogSaveSources(
        context: Context,
        gameId: String,
        forRestore: Boolean,
    ): List<SaveBackupSource> {
        val saveDirs = GOGService.getResolvedSaveDirectories(context, "GOG_$gameId")
        return saveDirs.mapIndexedNotNull { index, saveDir ->
            if (forRestore || (saveDir.exists() && !saveDir.listFiles().isNullOrEmpty())) {
                SaveBackupSource("gog/location_$index", saveDir)
            } else {
                null
            }
        }
    }

    // ── Zip / unzip ─────────────────────────────────────────────────────────

    private fun zipSaveSources(sources: List<SaveBackupSource>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            sources.forEach { source ->
                val zipRoot = source.zipRoot.trimEnd('/')
                if (zipRoot.isEmpty()) return@forEach
                zos.putNextEntry(ZipEntry("$zipRoot/"))
                zos.closeEntry()

                val exactFiles = source.exactFiles?.filter { it.exists() }.orEmpty()
                if (exactFiles.isNotEmpty()) {
                    exactFiles.forEach { file ->
                        val relativePath =
                            source.localDir
                                .toPath()
                                .relativize(file.toPath())
                                .toString()
                                .replace(File.separatorChar, '/')
                        addFileToZip(zos, file, "$zipRoot/$relativePath")
                    }
                } else if (source.localDir.exists()) {
                    zipDirRecursive(zos, source.localDir, zipRoot)
                }
            }
        }
        return baos.toByteArray()
    }

    private fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
    ) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var len: Int
            while (fis.read(buf).also { len = it } > 0) {
                zos.write(buf, 0, len)
            }
        }
        zos.closeEntry()
    }

    private fun zipDirRecursive(
        zos: ZipOutputStream,
        dir: File,
        baseName: String,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val entryName = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
            if (child.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
                zipDirRecursive(zos, child, entryName)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(child).use { fis ->
                    val buf = ByteArray(8192)
                    var len: Int
                    while (fis.read(buf).also { len = it } > 0) {
                        zos.write(buf, 0, len)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzipToSources(
        zipBytes: ByteArray,
        sources: List<SaveBackupSource>,
    ) {
        val sortedSources = sources.sortedByDescending { it.zipRoot.length }
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name
                val source =
                    sortedSources.firstOrNull {
                        entryName == "${it.zipRoot}/" || entryName.startsWith("${it.zipRoot}/")
                    }
                if (source == null) {
                    zis.closeEntry()
                    continue
                }

                val relativeName = entryName.removePrefix(source.zipRoot).removePrefix("/")
                if (relativeName.isEmpty()) {
                    source.localDir.mkdirs()
                    zis.closeEntry()
                    continue
                }

                val file = File(source.localDir, relativeName)
                if (!file.canonicalPath.startsWith(source.localDir.canonicalPath + File.separator) &&
                    file.canonicalPath != source.localDir.canonicalPath
                ) {
                    throw SecurityException("Zip entry tries to escape target directory")
                }

                if (entry!!.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (zis.read(buf).also { len = it } > 0) {
                            fos.write(buf, 0, len)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun buildSteamZipRoot(
        root: PathType,
        substitutedPath: String,
    ): String {
        val normalizedPath =
            substitutedPath
                .replace(File.separatorChar, '/')
                .trim('/')
        return if (normalizedPath.isEmpty()) {
            "steam/${root.name}"
        } else {
            "steam/${root.name}/$normalizedPath"
        }
    }

    // ── Tree fingerprint (deterministic, runs on the live save tree) ────────

    /**
     * SHA-256 over `(relativePath \0 fileBytes \0)` for every file under each save source,
     * with paths sorted ascending. This is independent of zip-impl quirks (entry timestamps,
     * compression level), so two backups of an unchanged save tree always produce the same
     * fingerprint and the de-dupe path triggers correctly.
     */
    private fun computeTreeFingerprint(sources: List<SaveBackupSource>): String {
        val md = MessageDigest.getInstance("SHA-256")
        val files = mutableListOf<Pair<String, File>>()
        sources.forEach { src ->
            val zipRoot = src.zipRoot.trimEnd('/')
            val exact = src.exactFiles?.filter { it.exists() && it.isFile }.orEmpty()
            if (exact.isNotEmpty()) {
                exact.forEach { f ->
                    val rel =
                        src.localDir
                            .toPath()
                            .relativize(f.toPath())
                            .toString()
                            .replace(File.separatorChar, '/')
                    files += "$zipRoot/$rel" to f
                }
            } else if (src.localDir.exists()) {
                walkDirForFingerprint(src.localDir, "$zipRoot/", files)
            }
        }
        files.sortBy { it.first }
        files.forEach { (path, file) ->
            md.update(path.toByteArray(StandardCharsets.UTF_8))
            md.update(0)
            FileInputStream(file).use { fis ->
                val buf = ByteArray(8192)
                var n: Int
                while (fis.read(buf).also { n = it } > 0) md.update(buf, 0, n)
            }
            md.update(0)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun walkDirForFingerprint(
        dir: File,
        prefix: String,
        out: MutableList<Pair<String, File>>,
    ) {
        val children = dir.listFiles() ?: return
        for (c in children.sortedBy { it.name }) {
            val name = "$prefix${c.name}"
            if (c.isDirectory) {
                walkDirForFingerprint(c, "$name/", out)
            } else if (c.isFile) {
                out += name to c
            }
        }
    }

    // ── PGS snapshot naming and identifiers ─────────────────────────────────

    private fun shortSrc(s: GameSource): String =
        when (s) {
            GameSource.STEAM -> "st"
            GameSource.EPIC -> "ep"
            GameSource.GOG -> "gg"
            GameSource.CUSTOM -> "cu"
        }

    /** First 16 hex of SHA-256 over a stable canonical key. URL-safe by construction. */
    private fun gameKeyHash(
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(manifestGameKey(source, gameId, gameName).toByteArray(StandardCharsets.UTF_8))
        return md.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private fun manifestGameKey(
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String = "${source.name}:$gameId:$gameName"

    private fun manifestSnapshotName(
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String = "gs_${shortSrc(source)}_${gameKeyHash(source, gameId, gameName)}_m"

    /** Returns prefix matching every snapshot belonging to a single game. */
    private fun gameSnapshotPrefix(
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String = "gs_${shortSrc(source)}_${gameKeyHash(source, gameId, gameName)}_"

    /** `t<yyyyMMddHHmmssSSS>_<hex4>` — millisecond UTC + 4-char SecureRandom nonce. */
    private fun newHistId(): String {
        val ts =
            SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
        val nonce = "%04x".format(secureRandom.nextInt(0x10000))
        return "t${ts}_$nonce"
    }

    private val secureRandom = SecureRandom()

    private fun partSnapshotName(
        source: GameSource,
        gameId: String,
        gameName: String,
        histId: String,
        partIndex: Int,
    ): String = "${gameSnapshotPrefix(source, gameId, gameName)}${histId}_p$partIndex"

    /** Decompose a part-snapshot name back into (histId, partIndex). Returns null if non-matching. */
    private fun parsePartSnapshotName(
        name: String,
        prefix: String,
    ): Pair<String, Int>? {
        if (!name.startsWith(prefix)) return null
        val tail = name.removePrefix(prefix) // e.g. "t20260429143022123_4f1a_p3"
        val pIdx = tail.lastIndexOf("_p")
        if (pIdx < 0) return null
        val histId = tail.substring(0, pIdx)
        val partStr = tail.substring(pIdx + 2)
        val part = partStr.toIntOrNull() ?: return null
        return histId to part
    }

    private fun humanFileName(entry: ManifestEntry): String {
        val fmt =
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val base = "${fmt.format(Date(entry.createdAt))}_${entry.origin.tag}"
        val label = entry.label
        return if (label.isNullOrEmpty()) base else "${base}_$label"
    }

    // ── Manifest read / write ───────────────────────────────────────────────

    private data class ManifestEntry(
        val id: String,
        val createdAt: Long,
        val origin: BackupOrigin,
        val label: String?,
        val totalBytes: Long,
        val partCount: Int,
        val fingerprint: String,
        /** CUSTOM-only: drive_c-relative target path. Null for STEAM/EPIC/GOG entries. */
        val customRelPath: String? = null,
    )

    private data class ManifestData(
        val version: Int,
        val gameKey: String,
        val history: List<ManifestEntry>,
    )

    private suspend fun readManifest(
        client: com.google.android.gms.games.SnapshotsClient,
        name: String,
    ): ManifestData? {
        val snapshot = PgsSnapshotIO.openSnapshot(client, name, createIfMissing = false) ?: return null
        val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
        return try {
            val bytes = snapshot.snapshotContents.readFully()
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            if (bytes.isEmpty()) {
                null
            } else {
                decodeManifest(bytes)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readManifest(%s) failed", name)
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            null
        } finally {
            PgsSnapshotIO.closeQuietly(pfd)
        }
    }

    private suspend fun writeManifest(
        client: com.google.android.gms.games.SnapshotsClient,
        name: String,
        manifest: ManifestData,
    ): Boolean {
        val bytes = encodeManifest(manifest)
        return writeSnapshotBytes(
            client = client,
            name = name,
            bytes = bytes,
            description = "Save history (${manifest.history.size} entries)",
            progressValue = manifest.history.size.toLong(),
        )
    }

    private suspend fun writeSnapshotBytes(
        client: com.google.android.gms.games.SnapshotsClient,
        name: String,
        bytes: ByteArray,
        description: String,
        progressValue: Long,
    ): Boolean {
        val snapshot = PgsSnapshotIO.openSnapshot(client, name, createIfMissing = true) ?: return false
        val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
        return try {
            if (!snapshot.snapshotContents.writeBytes(bytes)) {
                runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                Timber.tag(TAG).e("writeBytes failed for snapshot %s (%d bytes)", name, bytes.size)
                return false
            }
            val md =
                SnapshotMetadataChange
                    .Builder()
                    .setDescription(description)
                    .setProgressValue(progressValue)
                    .build()
            Tasks.await(client.commitAndClose(snapshot, md))
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "writeSnapshotBytes(%s) failed", name)
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            false
        } finally {
            PgsSnapshotIO.closeQuietly(pfd)
        }
    }

    private suspend fun readSnapshotBytes(
        client: com.google.android.gms.games.SnapshotsClient,
        name: String,
    ): ByteArray? {
        val snapshot = PgsSnapshotIO.openSnapshot(client, name, createIfMissing = false) ?: return null
        val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
        return try {
            val bytes = snapshot.snapshotContents.readFully()
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            bytes
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readSnapshotBytes(%s) failed", name)
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            null
        } finally {
            PgsSnapshotIO.closeQuietly(pfd)
        }
    }

    private fun encodeManifest(manifest: ManifestData): ByteArray {
        val json = JSONObject().apply {
            put("version", manifest.version)
            put("gameKey", manifest.gameKey)
            val arr = JSONArray()
            manifest.history.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("createdAt", e.createdAt)
                        put("origin", e.origin.tag)
                        put("label", e.label ?: JSONObject.NULL)
                        put("totalBytes", e.totalBytes)
                        put("partCount", e.partCount)
                        put("fingerprint", e.fingerprint)
                        if (e.customRelPath != null) put("customRelPath", e.customRelPath)
                    },
                )
            }
            put("history", arr)
        }
        val raw = json.toString().toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(raw) }
        return baos.toByteArray()
    }

    private fun decodeManifest(bytes: ByteArray): ManifestData? {
        return try {
            val raw =
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            val obj = JSONObject(String(raw, StandardCharsets.UTF_8))
            val version = obj.optInt("version", 1)
            val gameKey = obj.optString("gameKey")
            val arr = obj.optJSONArray("history") ?: JSONArray()
            val history = mutableListOf<ManifestEntry>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val origin = BackupOrigin.fromTag(e.optString("origin")) ?: continue
                val labelRaw = if (e.isNull("label")) null else e.optString("label").takeIf { it.isNotEmpty() }
                val customRel = if (e.has("customRelPath") && !e.isNull("customRelPath"))
                    e.optString("customRelPath").takeIf { it.isNotEmpty() } else null
                history += ManifestEntry(
                    id = e.optString("id"),
                    createdAt = e.optLong("createdAt"),
                    origin = origin,
                    label = labelRaw,
                    totalBytes = e.optLong("totalBytes"),
                    partCount = e.optInt("partCount", 1),
                    fingerprint = e.optString("fingerprint"),
                    customRelPath = customRel,
                )
            }
            ManifestData(version = version, gameKey = gameKey, history = history)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "decodeManifest failed")
            null
        }
    }

    /**
     * Returns (kept, pruned) where kept is `≤MAX_HISTORY_ENTRIES` newest entries within
     * the [HISTORY_MAX_AGE_DAYS] window, sorted newest-first.
     */
    private fun pruneHistory(history: List<ManifestEntry>): Pair<List<ManifestEntry>, List<ManifestEntry>> {
        val cutoff = System.currentTimeMillis() - HISTORY_MAX_AGE_DAYS * 24L * 60L * 60L * 1000L
        val sorted = history.sortedByDescending { it.createdAt }
        val withinAge = sorted.filter { it.createdAt > cutoff }
        val keep = withinAge.take(MAX_HISTORY_ENTRIES)
        val prune = sorted.filterNot { kept -> keep.any { it.id == kept.id } }
        return keep to prune
    }

    // ── Orphan-parts GC ─────────────────────────────────────────────────────

    /**
     * For a single game, list every snapshot in the user's account that matches the
     * game's prefix; identify part snapshots whose `<histId>` is not referenced by
     * the live manifest; delete those (capped at [GC_DELETE_BUDGET_PER_RUN]).
     */
    private suspend fun gcOrphanedParts(
        client: com.google.android.gms.games.SnapshotsClient,
        source: GameSource,
        gameId: String,
        gameName: String,
        manifest: ManifestData,
    ) {
        val all = PgsSnapshotIO.loadAllSnapshotMetadata(client, forceReload = true) ?: return
        val prefix = gameSnapshotPrefix(source, gameId, gameName)
        val liveIds = manifest.history.map { it.id }.toSet()
        var deleted = 0
        for (info in all) {
            if (deleted >= GC_DELETE_BUDGET_PER_RUN) break
            val name = info.uniqueName
            if (!name.startsWith(prefix)) continue
            // Skip the manifest itself.
            if (name == manifestSnapshotName(source, gameId, gameName)) continue
            val parsed = parsePartSnapshotName(name, prefix) ?: continue
            val (histId, _) = parsed
            if (histId in liveIds) continue
            // Orphan — try delete.
            runCatching {
                val ok = PgsSnapshotIO.deleteSnapshotByName(client, name)
                if (ok) deleted++
            }.onFailure { Timber.tag(TAG).w(it, "GC delete of %s failed", name) }
        }
        if (deleted > 0) {
            Timber.tag(TAG).i("GC: deleted %d orphan part snapshots for %s/%s", deleted, source, gameId)
        }
    }

    // ── Auth / preferences helpers ──────────────────────────────────────────

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isGoogleSyncEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GOOGLE_SYNC_ENABLED, false)
}
