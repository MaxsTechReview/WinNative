package com.winlator.cmod.feature.sync.google
import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Tasks
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.shared.android.ActivityResultHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import com.winlator.cmod.feature.stores.steam.utils.FileUtils as SteamFileUtils

/**
 * Manages backup and restore of individual game cloud saves to/from Google Drive.
 *
 * Files are stored in a "WinNative" folder on the user's Google Drive as zip files.
 *
 * Flow:
 *   Backup:  Download cloud save from provider (Steam/Epic/GOG) → zip → upload to Google Drive
 *   Restore: Download from Google Drive → unzip → upload back to provider
 */
object GameSaveBackupManager {
    private const val TAG = "GameSaveBackup"
    private const val DRIVE_ROOT_FOLDER_NAME = "WinNative"
    private const val DRIVE_GAMES_FOLDER_NAME = "Games"
    private const val DRIVE_HISTORY_FOLDER_NAME = "History"
    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS_NAME = "google_store_login_sync"
    private const val KEY_GOOGLE_SYNC_ENABLED = "google_sync_enabled"
    private const val KEY_KEEP_REPLACED_BACKUP = "cloud_sync_keep_replaced_backup"
    private const val AUTH_SESSION_RETRY_COUNT = 5
    private const val AUTH_SESSION_RETRY_DELAY_MS = 750L

    /** Maximum number of history entries retained (and shown) per game. */
    const val MAX_HISTORY_ENTRIES = 30

    /** Entries older than this are pruned whenever history is listed or written. */
    const val HISTORY_MAX_AGE_DAYS = 30

    const val REQUEST_CODE_DRIVE_AUTH = 9002

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    enum class GameSource { STEAM, EPIC, GOG }

    /** Origin of a history backup — identifies which side of a conflict it came from. */
    enum class BackupOrigin(val tag: String) {
        /** Local save that was replaced by a cloud version. */
        LOCAL("local"),
        /** Cloud save snapshot captured before local overwrote it. */
        CLOUD("cloud"),
        /** User-initiated manual snapshot. */
        MANUAL("manual"),
        /** Automatic backup (e.g. on exit). */
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

    /** A backed-up save stored under Games/History/<gameKey>/ on Drive. */
    data class BackupHistoryEntry(
        val fileId: String,
        val fileName: String,
        val timestampMs: Long,
        val origin: BackupOrigin,
        val sizeBytes: Long,
        /** Optional user label ("Boss fight", "Before ending", …). Null when unnamed. */
        val label: String? = null,
    )

    /** Max length of a user-provided history-entry label, after sanitization. */
    const val MAX_HISTORY_LABEL_LENGTH = 48

    private data class SaveBackupSource(
        val zipRoot: String,
        val localDir: File,
        val exactFiles: List<File>? = null,
    )

    private data class DriveFileInfo(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val modifiedTimeMs: Long,
    )

    /**
     * Back up the current local save state to Save History on Google Drive.
     *
     * Writes `WinNative/Games/History/<gameKey>/<timestamp>_manual.zip` and prunes
     * old entries (>30 days or beyond MAX_HISTORY_ENTRIES). Does NOT pull from
     * the store provider first — what's on disk is what gets saved. The legacy
     * primary-slot zip (`<platform>_<id>_<name>.zip`) is no longer written; it's
     * only read as a fallback when history is empty.
     */
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
     * Triggers the Google Drive account selection / authorization consent flow.
     * Returns true if authorization was already granted (token obtained),
     * or false if the consent UI was launched (caller should wait for onDriveAuthResult).
     */
    suspend fun requestDriveAuthorization(activity: Activity): Boolean =
        withContext(Dispatchers.IO) {
            val token = getDriveAccessToken(activity)
            token != null
        }

    /**
     * Push the current on-disk save up to the store provider (Steam / Epic / GOG).
     *
     * Does **not** touch Google Drive and does **not** snapshot local first — this
     * is a pure "sync up" used when the user wants the provider's cloud to match
     * whatever they're currently playing with on disk. Kept named `restoreFromGoogle`
     * for stable callers; the button label is "Restore cloud save".
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
     * Called from Activity's onActivityResult when Drive consent is completed.
     */
    fun onDriveAuthResult(
        activity: Activity,
        resultCode: Int,
    ) {
        if (resultCode == Activity.RESULT_OK) {
            Timber.tag(TAG).i("Drive authorization consent granted")
        } else {
            Timber.tag(TAG).w("Drive authorization consent denied (resultCode=%d)", resultCode)
        }
    }

    // ── Backup history (WinNative/Games/History/<gameKey>) ──

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
     * upload to `WinNative/Games/History/<gameKey>/<timestamp>_<origin>.zip`.
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
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val accessToken =
                    getDriveAccessToken(activity)
                        ?: return@withContext BackupResult(false, "Google Drive authorization required.")

                val saveSources = getLocalSaveSources(context, gameSource, gameId, forRestore = false)
                if (saveSources.isEmpty()) {
                    return@withContext BackupResult(false, "No local save files found to back up.")
                }
                val zipBytes = zipSaveSources(saveSources)
                if (zipBytes.isEmpty()) {
                    return@withContext BackupResult(false, "Save files are empty.")
                }

                val folderId =
                    getOrCreateHistoryGameFolder(accessToken, gameSource, gameId, gameName)
                        ?: return@withContext BackupResult(false, "Failed to access History folder on Google Drive.")

                val fileName = buildHistoryFileName(System.currentTimeMillis(), origin)
                val ok = createDriveFile(accessToken, folderId, fileName, zipBytes)
                if (!ok) {
                    return@withContext BackupResult(false, "Failed to upload history backup to Google Drive.")
                }

                // Best-effort prune — never fail the caller if this fails
                runCatching { pruneHistoryFolder(accessToken, folderId) }
                    .onFailure { Timber.tag(TAG).w(it, "History prune failed") }

                BackupResult(true, "Save backed up to Save History.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "backupDiscardedSave failed for $gameSource/$gameId")
                BackupResult(false, "Failed to back up save: ${e.message}")
            }
        }

    /**
     * Lists up to [MAX_HISTORY_ENTRIES] backup entries for the given game, newest first.
     * Also performs a best-effort prune of entries older than [HISTORY_MAX_AGE_DAYS].
     * Returns an empty list if not signed in, folder does not exist, or Drive returns no files.
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
                if (!awaitAuthenticatedSession(activity)) return@withContext emptyList()
                val accessToken = getDriveAccessToken(activity) ?: return@withContext emptyList()

                val folderId =
                    findHistoryGameFolder(accessToken, gameSource, gameId, gameName)
                        ?: return@withContext emptyList()

                val files = listDriveFilesInFolder(accessToken, folderId)
                val parsed =
                    files.mapNotNull { file ->
                        val parsedName =
                            parseHistoryFileName(file.name)
                                ?: return@mapNotNull null
                        BackupHistoryEntry(
                            fileId = file.id,
                            fileName = file.name,
                            timestampMs = parsedName.first,
                            origin = parsedName.second,
                            sizeBytes = file.sizeBytes,
                            label = parsedName.third,
                        )
                    }

                // Prune old entries in the background (don't block the UI)
                val cutoff = System.currentTimeMillis() - HISTORY_MAX_AGE_DAYS * 24L * 60L * 60L * 1000L
                parsed
                    .filter { it.timestampMs in 1L..cutoff }
                    .forEach { runCatching { deleteDriveFile(accessToken, it.fileId) } }

                parsed
                    .filter { it.timestampMs > cutoff }
                    .sortedByDescending { it.timestampMs }
                    .take(MAX_HISTORY_ENTRIES)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listBackupHistory failed for $gameSource/$gameId")
                emptyList()
            }
        }

    /**
     * Download a specific backup from history and unzip it over the local save directory.
     * Does NOT sync back to the store provider — restores locally only so the next launch
     * can pick up the restored files. Existing local files in the save dirs are overwritten.
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
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val accessToken =
                    getDriveAccessToken(activity)
                        ?: return@withContext BackupResult(false, "Google Drive authorization required.")

                val zipBytes = downloadDriveFile(accessToken, entry.fileId)
                if (zipBytes == null || zipBytes.isEmpty()) {
                    return@withContext BackupResult(false, "Downloaded backup is empty.")
                }

                val saveSources = getLocalSaveSources(context, gameSource, gameId, forRestore = true)
                if (saveSources.isEmpty()) {
                    return@withContext BackupResult(false, "Cannot determine save directory for this game.")
                }
                unzipToSources(zipBytes, saveSources)
                BackupResult(true, "Save restored from backup.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreFromHistoryEntry failed for $gameSource/$gameId")
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    /**
     * Set or clear the user label on a history entry by renaming the Drive file.
     * Timestamp + origin prefix are preserved, so sort order and age-based pruning
     * remain unchanged. Pass `newLabel = null` (or blank) to clear the label.
     *
     * Returns a [BackupResult]; on success, callers should re-list history to pick
     * up the renamed entry.
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
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val accessToken =
                    getDriveAccessToken(activity)
                        ?: return@withContext BackupResult(false, "Google Drive authorization required.")

                val cleanLabel = sanitizeHistoryLabel(newLabel)
                val newName = buildHistoryFileName(entry.timestampMs, entry.origin, cleanLabel)
                if (newName == entry.fileName) {
                    return@withContext BackupResult(true, "Nothing to rename.")
                }
                val ok = renameDriveFile(accessToken, entry.fileId, newName)
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

    /** Permanently delete a Save History entry from Google Drive. */
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
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }
                val accessToken =
                    getDriveAccessToken(activity)
                        ?: return@withContext BackupResult(false, "Google Drive authorization required.")

                val ok = deleteDriveFile(accessToken, entry.fileId)
                if (ok) {
                    BackupResult(true, "Backup deleted.")
                } else {
                    BackupResult(false, "Delete failed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "deleteBackupHistoryEntry failed for %s", entry.fileName)
                BackupResult(false, "Delete failed: ${e.message}")
            }
        }

    // ── Provider sync helpers ──

    private suspend fun syncDownFromProvider(
        context: Context,
        source: GameSource,
        gameId: String,
    ): Boolean {
        return try {
            when (source) {
                GameSource.STEAM -> {
                    val appId = gameId.toIntOrNull() ?: return false
                    SteamService.syncCloudSavesForBackup(context, appId, "download")
                }

                GameSource.EPIC -> {
                    val appId = gameId.toIntOrNull() ?: return false
                    EpicCloudSavesManager.syncCloudSaves(context, appId, "download")
                }

                GameSource.GOG -> {
                    GOGService.syncCloudSaves(context, "GOG_$gameId", "download")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "syncDownFromProvider failed for $source/$gameId")
            false
        }
    }

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
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "syncUpToProvider failed for $source/$gameId")
            false
        }
    }

    // ── Local save directory resolution ──

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

    // ── Zip helpers ──

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

    // ── Drive file naming ──

    private fun buildDriveFileName(
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String {
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9_ -]"), "").take(50).trim()
        val sanitizedId = gameId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${source.name.lowercase()}_${sanitizedId}_$sanitizedName.zip"
    }

    /** Folder name inside `WinNative/Games/History/` for a given game. Strips the `.zip` from the primary name. */
    private fun buildHistoryGameFolderName(
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String = buildDriveFileName(source, gameId, gameName).removeSuffix(".zip")

    /**
     * Produce `yyyyMMdd'T'HHmmss_<origin>[_<label>].zip` in UTC so filenames sort
     * chronologically. The optional label is the user-provided name for the entry.
     */
    private fun buildHistoryFileName(
        timestampMs: Long,
        origin: BackupOrigin,
        label: String? = null,
    ): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val base = "${fmt.format(Date(timestampMs))}_${origin.tag}"
        val clean = sanitizeHistoryLabel(label)
        return if (clean.isNullOrEmpty()) "$base.zip" else "${base}_$clean.zip"
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

    // Label captured non-greedily so it doesn't consume the `.zip` extension.
    private val historyFileNameRegex = Regex("""^(\d{8}T\d{6})_([a-z]+)(?:_(.+?))?\.zip$""")

    /** Returns (timestamp, origin, label) or null if the name does not match. */
    private fun parseHistoryFileName(name: String): Triple<Long, BackupOrigin, String?>? {
        val m = historyFileNameRegex.matchEntire(name) ?: return null
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val ts = runCatching { fmt.parse(m.groupValues[1])?.time }.getOrNull() ?: return null
        val origin = BackupOrigin.fromTag(m.groupValues[2]) ?: return null
        val label = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }
        return Triple(ts, origin, label)
    }

    // ── Auth helpers ──

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isGoogleSyncEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GOOGLE_SYNC_ENABLED, false)

    private fun isActivityValidForPlayGames(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        val lifecycleState = (activity as? LifecycleOwner)?.lifecycle?.currentState
        return lifecycleState?.isAtLeast(Lifecycle.State.STARTED) ?: true
    }

    private suspend fun isAuthenticatedBlocking(activity: Activity): Boolean {
        if (!isActivityValidForPlayGames(activity)) {
            Timber.tag(TAG).i(
                "Skipping Google auth check because %s is finishing or destroyed",
                activity::class.java.simpleName,
            )
            return false
        }
        return try {
            PlayGamesBootstrap.ensureInitialized(activity)
            val task = PlayGames.getGamesSignInClient(activity).isAuthenticated
            val result =
                withContext(Dispatchers.IO) {
                    try {
                        Tasks.await(task, 10, TimeUnit.SECONDS)
                    } catch (e: TimeoutException) {
                        Timber.tag(TAG).e("Timeout waiting for Google authentication state")
                        null
                    }
                }
            result?.isAuthenticated == true
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to read Google authentication state")
            false
        }
    }

    private suspend fun awaitAuthenticatedSession(activity: Activity): Boolean {
        if (!isActivityValidForPlayGames(activity)) {
            return false
        }
        PlayGamesBootstrap.ensureInitialized(activity)
        repeat(AUTH_SESSION_RETRY_COUNT) { attempt ->
            if (isAuthenticatedBlocking(activity)) {
                return true
            }
            if (attempt < AUTH_SESSION_RETRY_COUNT - 1) {
                delay(AUTH_SESSION_RETRY_DELAY_MS)
            }
        }
        return false
    }

    /**
     * Get an OAuth2 access token with Drive.file scope using AuthorizationClient.
     * If the user hasn't granted Drive access yet, launches the consent UI and returns null
     * (the caller should retry after consent is granted).
     */
    private suspend fun getDriveAccessToken(activity: Activity): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!isActivityValidForPlayGames(activity)) {
                    Timber.tag(TAG).w(
                        "Skipping Drive authorization because %s is no longer active",
                        activity::class.java.simpleName,
                    )
                    return@withContext null
                }
                val authRequest =
                    AuthorizationRequest
                        .builder()
                        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                        .build()

                val authResult: AuthorizationResult =
                    suspendCancellableCoroutine { cont ->
                        Identity
                            .getAuthorizationClient(activity)
                            .authorize(authRequest)
                            .addOnSuccessListener { result ->
                                cont.resume(result)
                            }.addOnFailureListener { e ->
                                Timber.tag(TAG).e(e, "AuthorizationClient.authorize failed")
                                cont.resume(null)
                            }
                    } ?: return@withContext null

                if (authResult.hasResolution()) {
                    // User needs to grant consent — launch the consent UI
                    Timber.tag(TAG).i("Drive authorization requires user consent, launching...")
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        withContext(Dispatchers.Main) {
                            val host = activity as? ActivityResultHost
                            if (host != null) {
                                host.launchDriveAuthRequest(pendingIntent.intentSender)
                            } else {
                                Timber.tag(TAG).e(
                                    "Activity %s cannot launch Drive auth flow",
                                    activity::class.java.simpleName,
                                )
                            }
                        }
                    }
                    return@withContext null // Will retry after consent
                }

                val token = authResult.accessToken
                if (token != null) {
                    Timber.tag(TAG).i("Got Drive access token via AuthorizationClient")
                } else {
                    Timber.tag(TAG).e("AuthorizationResult has no access token")
                }
                token
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to get Drive access token")
                null
            }
        }

    // ── Google Drive REST API helpers ──

    /**
     * Find or create the "WinNative" folder on Google Drive.
     */
    private fun getOrCreateGameBackupsFolder(accessToken: String): String? {
        val rootFolderId =
            getOrCreateDriveFolder(accessToken, null, DRIVE_ROOT_FOLDER_NAME)
                ?: return null
        return getOrCreateDriveFolder(accessToken, rootFolderId, DRIVE_GAMES_FOLDER_NAME)
    }

    private fun getOrCreateDriveFolder(
        accessToken: String,
        parentId: String?,
        folderName: String,
    ): String? {
        val queryBuilder =
            StringBuilder()
                .append("name='")
                .append(escapeDriveQuery(folderName))
                .append("'")
                .append(" and mimeType='application/vnd.google-apps.folder'")
                .append(" and trashed=false")
        if (parentId != null) {
            queryBuilder.append(" and '").append(parentId).append("' in parents")
        }
        val searchRequest =
            Request
                .Builder()
                .url(
                    "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(
                        queryBuilder.toString(),
                        "UTF-8",
                    )}&fields=files(id,name)",
                ).addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

        httpClient.newCall(searchRequest).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val folderId = files.getJSONObject(0).getString("id")
                    Timber.tag(TAG).d("Found existing Drive folder: %s", folderId)
                    return folderId
                }
            }
        }

        // Create the folder
        val metadata =
            JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                if (parentId != null) {
                    put("parents", org.json.JSONArray().put(parentId))
                }
            }

        val createRequest =
            Request
                .Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metadata.toString().toRequestBody("application/json".toMediaType()))
                .build()

        httpClient.newCall(createRequest).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val folderId = json.getString("id")
                Timber.tag(TAG).i("Created Drive folder %s: %s", folderName, folderId)
                return folderId
            }
            Timber.tag(TAG).e("Failed to create Drive folder %s: %d %s", folderName, response.code, response.message)
        }
        return null
    }

    /**
     * Find or create the game-specific history folder:
     * `WinNative/Games/History/<gameKey>/`.
     */
    private fun getOrCreateHistoryGameFolder(
        accessToken: String,
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String? {
        val gamesFolderId = getOrCreateGameBackupsFolder(accessToken) ?: return null
        val historyFolderId = getOrCreateDriveFolder(accessToken, gamesFolderId, DRIVE_HISTORY_FOLDER_NAME) ?: return null
        val gameKey = buildHistoryGameFolderName(source, gameId, gameName)
        return getOrCreateDriveFolder(accessToken, historyFolderId, gameKey)
    }

    /** Look up the history game folder without creating it (returns null if absent). */
    private fun findHistoryGameFolder(
        accessToken: String,
        source: GameSource,
        gameId: String,
        gameName: String,
    ): String? {
        val rootFolderId = findDriveFolder(accessToken, null, DRIVE_ROOT_FOLDER_NAME) ?: return null
        val gamesFolderId = findDriveFolder(accessToken, rootFolderId, DRIVE_GAMES_FOLDER_NAME) ?: return null
        val historyFolderId = findDriveFolder(accessToken, gamesFolderId, DRIVE_HISTORY_FOLDER_NAME) ?: return null
        val gameKey = buildHistoryGameFolderName(source, gameId, gameName)
        return findDriveFolder(accessToken, historyFolderId, gameKey)
    }

    private fun findDriveFolder(
        accessToken: String,
        parentId: String?,
        folderName: String,
    ): String? {
        val queryBuilder =
            StringBuilder()
                .append("name='")
                .append(escapeDriveQuery(folderName))
                .append("'")
                .append(" and mimeType='application/vnd.google-apps.folder'")
                .append(" and trashed=false")
        if (parentId != null) {
            queryBuilder.append(" and '").append(parentId).append("' in parents")
        }
        val request =
            Request
                .Builder()
                .url(
                    "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(
                        queryBuilder.toString(),
                        "UTF-8",
                    )}&fields=files(id,name)",
                ).addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }
        return null
    }

    private fun listDriveFilesInFolder(
        accessToken: String,
        folderId: String,
    ): List<DriveFileInfo> {
        val query = "'$folderId' in parents and trashed=false"
        val request =
            Request
                .Builder()
                .url(
                    "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                        "&fields=files(id,name,size,modifiedTime)&orderBy=modifiedTime desc&pageSize=200",
                ).addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
        val results = mutableListOf<DriveFileInfo>()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag(TAG).e("listDriveFilesInFolder failed: %d %s", response.code, response.message)
                return emptyList()
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            val files = json.optJSONArray("files") ?: return emptyList()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val id = f.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val name = f.optString("name").takeIf { it.isNotEmpty() } ?: continue
                val size = f.optString("size", "0").toLongOrNull() ?: 0L
                val modifiedMs =
                    f
                        .optString("modifiedTime")
                        .takeIf { it.isNotEmpty() }
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                        ?: 0L
                results += DriveFileInfo(id, name, size, modifiedMs)
            }
        }
        return results
    }

    private fun deleteDriveFile(
        accessToken: String,
        fileId: String,
    ): Boolean {
        val request =
            Request
                .Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 404) return true
            Timber.tag(TAG).w("deleteDriveFile %s failed: %d %s", fileId, response.code, response.message)
        }
        return false
    }

    private fun renameDriveFile(
        accessToken: String,
        fileId: String,
        newName: String,
    ): Boolean {
        val metadata = JSONObject().apply { put("name", newName) }
        val request =
            Request
                .Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name")
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(metadata.toString().toRequestBody("application/json".toMediaType()))
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) return true
            Timber.tag(TAG).w("renameDriveFile %s failed: %d %s", fileId, response.code, response.message)
        }
        return false
    }

    /**
     * Remove history entries older than [HISTORY_MAX_AGE_DAYS] or beyond [MAX_HISTORY_ENTRIES].
     */
    private fun pruneHistoryFolder(
        accessToken: String,
        folderId: String,
    ) {
        val all = listDriveFilesInFolder(accessToken, folderId)
        val parsed =
            all.mapNotNull { file ->
                val p = parseHistoryFileName(file.name) ?: return@mapNotNull null
                Triple(file.id, p.first, file)
            }
        val cutoff = System.currentTimeMillis() - HISTORY_MAX_AGE_DAYS * 24L * 60L * 60L * 1000L
        parsed
            .filter { it.second in 1L..cutoff }
            .forEach { runCatching { deleteDriveFile(accessToken, it.first) } }
        parsed
            .filter { it.second > cutoff }
            .sortedByDescending { it.second }
            .drop(MAX_HISTORY_ENTRIES)
            .forEach { runCatching { deleteDriveFile(accessToken, it.first) } }
    }

    /**
     * Find a file by name inside a specific folder.
     */
    private fun findDriveFile(
        accessToken: String,
        folderId: String,
        fileName: String,
    ): String? {
        val query = "name='${escapeDriveQuery(fileName)}' and '$folderId' in parents and trashed=false"
        val request =
            Request
                .Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }
        return null
    }

    /**
     * Create a new file on Google Drive inside the specified folder.
     */
    private fun createDriveFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        data: ByteArray,
    ): Boolean {
        val metadata =
            JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put(folderId))
            }

        val boundary = "winnative_boundary_${System.currentTimeMillis()}"
        val body = buildMultipartRelatedBody(boundary, metadata.toString(), data)

        val request =
            Request
                .Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Timber.tag(TAG).i("Created Drive file: %s (%d bytes)", fileName, data.size)
                return true
            }
            Timber.tag(TAG).e("Failed to create Drive file: %d %s", response.code, response.message)
        }
        return false
    }

    private fun buildMultipartRelatedBody(
        boundary: String,
        jsonMetadata: String,
        fileData: ByteArray,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val crlf = "\r\n"

        fun write(s: String) = baos.write(s.toByteArray(Charsets.UTF_8))

        write("--$boundary$crlf")
        write("Content-Type: application/json; charset=UTF-8$crlf")
        write(crlf)
        write(jsonMetadata)
        write(crlf)
        write("--$boundary$crlf")
        write("Content-Type: application/zip$crlf")
        write(crlf)
        baos.write(fileData)
        write(crlf)
        write("--$boundary--")

        return baos.toByteArray()
    }

    /**
     * Update an existing file on Google Drive (overwrite contents).
     */
    private fun updateDriveFile(
        accessToken: String,
        fileId: String,
        data: ByteArray,
    ): Boolean {
        val request =
            Request
                .Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(data.toRequestBody("application/zip".toMediaType()))
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Timber.tag(TAG).i("Updated Drive file: %s (%d bytes)", fileId, data.size)
                return true
            }
            Timber.tag(TAG).e("Failed to update Drive file: %d %s", response.code, response.message)
        }
        return false
    }

    /**
     * Download a file's content from Google Drive.
     */
    private fun downloadDriveFile(
        accessToken: String,
        fileId: String,
    ): ByteArray? {
        val request =
            Request
                .Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.bytes()
            }
            Timber.tag(TAG).e("Failed to download Drive file: %d %s", response.code, response.message)
        }
        return null
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

    private fun escapeDriveQuery(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
}
