package com.winlator.cmod.feature.sync.google
import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Tasks
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
    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS_NAME = "google_store_login_sync"
    private const val KEY_GOOGLE_SYNC_ENABLED = "google_sync_enabled"
    private const val AUTH_SESSION_RETRY_COUNT = 5
    private const val AUTH_SESSION_RETRY_DELAY_MS = 750L

    const val REQUEST_CODE_DRIVE_AUTH = 9002

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    enum class GameSource { STEAM, EPIC, GOG }

    data class BackupResult(
        val success: Boolean,
        val message: String,
    )

    private data class SaveBackupSource(
        val zipRoot: String,
        val localDir: File,
        val exactFiles: List<File>? = null,
    )

    /**
     * Backup a game's cloud save to Google Drive.
     */
    suspend fun backupToGoogle(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext

                // Auth check
                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled. Enable it in Settings > Google first.")
                }
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games. Please sign in first.")
                }

                val accessToken = getDriveAccessToken(activity)
                if (accessToken == null) {
                    return@withContext BackupResult(false, "Google Drive authorization required. Please try again after granting access.")
                }

                performBackup(activity, accessToken, gameSource, gameId, gameName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Backup failed for $gameSource/$gameId")
                BackupResult(false, "Backup failed: ${e.message}")
            }
        }

    private suspend fun performBackup(
        activity: Activity,
        accessToken: String,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult {
        val context = activity.applicationContext

        // Step 1: Ensure cloud saves are synced down to local
        val syncOk = syncDownFromProvider(context, gameSource, gameId)
        if (!syncOk) {
            return BackupResult(false, "Failed to download save from ${gameSource.name}. Saves may not exist or service is unavailable.")
        }

        // Step 2: Locate and zip local save files
        val saveSources = getLocalSaveSources(context, gameSource, gameId, forRestore = false)
        if (saveSources.isEmpty()) {
            return BackupResult(false, "No local save files found for this game.")
        }

        val zipBytes = zipSaveSources(saveSources)
        if (zipBytes.isEmpty()) {
            return BackupResult(false, "Save files are empty.")
        }

        // Step 3: Upload to Google Drive
        val fileName = buildDriveFileName(gameSource, gameId, gameName)
        val folderId = getOrCreateGameBackupsFolder(accessToken)
        if (folderId == null) {
            return BackupResult(false, "Failed to create WinNative/Games folder on Google Drive.")
        }

        val existingFileId = findDriveFile(accessToken, folderId, fileName)
        val uploaded =
            if (existingFileId != null) {
                updateDriveFile(accessToken, existingFileId, zipBytes)
            } else {
                createDriveFile(accessToken, folderId, fileName, zipBytes)
            }

        return if (uploaded) {
            Timber.tag(TAG).i("Drive upload complete: $fileName (${zipBytes.size} bytes)")
            BackupResult(true, "Cloud save backed up to Google Drive.")
        } else {
            BackupResult(false, "Failed to upload to Google Drive.")
        }
    }

    /**
     * Auto-backup: zips the local save directory and uploads to Google Drive.
     * Unlike [backupToGoogle], this does NOT download from the store provider first —
     * it assumes the local save was just pushed to the store and mirrors it directly.
     */
    suspend fun autoBackupToGoogle(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext

                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled.")
                }
                if (!isAutoBackupEnabled(context)) {
                    return@withContext BackupResult(false, "Auto backup is not enabled.")
                }
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games.")
                }

                val accessToken =
                    getDriveAccessToken(activity)
                        ?: return@withContext BackupResult(false, "Google Drive authorization required.")

                // Go straight to zipping local saves — no syncDownFromProvider
                val saveSources = getLocalSaveSources(context, gameSource, gameId, forRestore = false)
                if (saveSources.isEmpty()) {
                    return@withContext BackupResult(false, "No local save files found for auto backup.")
                }

                val zipBytes = zipSaveSources(saveSources)
                if (zipBytes.isEmpty()) {
                    return@withContext BackupResult(false, "Save files are empty.")
                }

                val fileName = buildDriveFileName(gameSource, gameId, gameName)
                val folderId =
                    getOrCreateGameBackupsFolder(accessToken)
                        ?: return@withContext BackupResult(false, "Failed to create WinNative/Games folder on Google Drive.")

                val existingFileId = findDriveFile(accessToken, folderId, fileName)
                val uploaded =
                    if (existingFileId != null) {
                        updateDriveFile(accessToken, existingFileId, zipBytes)
                    } else {
                        createDriveFile(accessToken, folderId, fileName, zipBytes)
                    }

                if (uploaded) {
                    Timber.tag(TAG).i("Auto backup complete: $fileName (${zipBytes.size} bytes)")
                    BackupResult(true, "Auto backup to Google Drive complete.")
                } else {
                    BackupResult(false, "Failed to upload auto backup to Google Drive.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Auto backup failed for $gameSource/$gameId")
                BackupResult(false, "Auto backup failed: ${e.message}")
            }
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
     * Restore a game's cloud save from Google Drive.
     */
    suspend fun restoreFromGoogle(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val context = activity.applicationContext

                // Auth check
                if (!isGoogleSyncEnabled(context)) {
                    return@withContext BackupResult(false, "Google sync is not enabled. Enable it in Settings > Google first.")
                }
                if (!awaitAuthenticatedSession(activity)) {
                    return@withContext BackupResult(false, "Not signed in to Google Play Games. Please sign in first.")
                }

                val accessToken = getDriveAccessToken(activity)
                if (accessToken == null) {
                    return@withContext BackupResult(false, "Google Drive authorization required. Please try again after granting access.")
                }

                performRestore(activity, accessToken, gameSource, gameId, gameName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Restore failed for $gameSource/$gameId")
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    private suspend fun performRestore(
        activity: Activity,
        accessToken: String,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
    ): BackupResult {
        val context = activity.applicationContext
        val fileName = buildDriveFileName(gameSource, gameId, gameName)

        // Step 1: Find and download from Google Drive
        val folderId = getOrCreateGameBackupsFolder(accessToken)
        if (folderId == null) {
            return BackupResult(false, "Failed to access WinNative/Games folder on Google Drive.")
        }

        val fileId = findDriveFile(accessToken, folderId, fileName)
        if (fileId == null) {
            return BackupResult(false, "No backup found on Google Drive for this game.")
        }

        val zipBytes = downloadDriveFile(accessToken, fileId)
        if (zipBytes == null || zipBytes.isEmpty()) {
            return BackupResult(false, "Downloaded backup is empty.")
        }

        Timber.tag(TAG).i("Drive download complete: $fileName (${zipBytes.size} bytes)")

        // Step 2: Unzip to local save directory
        val saveSources = getLocalSaveSources(context, gameSource, gameId, forRestore = true)
        if (saveSources.isEmpty()) {
            return BackupResult(false, "Cannot determine save directory for this game.")
        }
        unzipToSources(zipBytes, saveSources)

        // Step 3: Upload back to provider
        val uploadOk = syncUpToProvider(context, gameSource, gameId)
        if (!uploadOk) {
            return BackupResult(
                false,
                "Save files restored locally, but failed to upload to ${gameSource.name}. You can try syncing manually.",
            )
        }

        return BackupResult(true, "Cloud save restored from Google Drive and uploaded to ${gameSource.name}.")
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

    // ── Auth helpers ──

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isGoogleSyncEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_GOOGLE_SYNC_ENABLED, false)

    private suspend fun isAuthenticatedBlocking(activity: Activity): Boolean =
        try {
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

    private suspend fun awaitAuthenticatedSession(activity: Activity): Boolean {
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
