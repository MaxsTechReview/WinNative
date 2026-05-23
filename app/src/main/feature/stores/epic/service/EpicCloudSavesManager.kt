package com.winlator.cmod.feature.stores.epic.service
import android.content.Context
import android.util.Log
import com.winlator.cmod.app.db.PluviaDatabase
import com.winlator.cmod.feature.stores.epic.data.EpicGame
import com.winlator.cmod.feature.stores.epic.service.manifest.EpicManifest
import com.winlator.cmod.feature.stores.steam.utils.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.zip.GZIPInputStream

/**
 * Manages Epic Cloud Saves - downloading and uploading save files
 *
 * Epic uses a manifest-based chunked format (similar to game downloads):
 * - Manifest files contain metadata and chunk references
 * - Save files are split into compressed chunks
 * - Chunks are deduplicated via GUID/hash
 */
object EpicCloudSavesManager {
    // Synchronization to prevent duplicate concurrent syncs
    private val syncMutex = Mutex()
    private data class SyncScope(
        val appId: Int,
        val containerId: Int?,
    )

    private val activeSyncs = mutableSetOf<Int>()
    private val recentSuccessfulUploads = mutableMapOf<SyncScope, Long>()
    private const val SAVE_TIMESTAMP_EQUAL_TOLERANCE_MS = 60_000L
    private const val DUPLICATE_UPLOAD_SUPPRESSION_MS = 120_000L
    private const val ANDROID_LOG_TAG = "EpicCloudSaves"

    // Data classes for API responses
    data class CloudSaveFiles(
        val files: Map<String, CloudFileInfo>,
    )

    private val baseCloudSyncUrl = "https://datastorage-public-service-liveegs.live.use1a.on.epicgames.com"

    private val httpClient = Net.http

    data class CloudFileInfo(
        val hash: String,
        val lastModified: String,
        val readLink: String?,
        val writeLink: String?,
    )

    data class CloudSaveHistoryEntry(
        val manifestPath: String,
        val timestampMs: Long,
        val fileCount: Int,
        val sizeBytes: Long,
    )

    enum class SyncAction {
        UPLOAD,
        DOWNLOAD,
        CONFLICT,
        NONE,
    }

    /**
     * Lightweight probe: checks whether cloud saves need syncing for [appId]
     * without downloading or uploading any files.
     *
     * @return `true` if a sync (download, upload, or conflict resolution) is
     *         needed, `false` if saves are already up-to-date.
     */
    suspend fun needsSync(
        context: Context,
        appId: Int,
    ): Boolean = needsSync(context, appId, null)

    suspend fun needsSync(
        context: Context,
        appId: Int,
        targetContainerId: Int?,
    ): Boolean = getPendingSyncAction(context, appId, targetContainerId) != SyncAction.NONE

    suspend fun getPendingSyncAction(
        context: Context,
        appId: Int,
    ): SyncAction = getPendingSyncAction(context, appId, null)

    suspend fun getPendingSyncAction(
        context: Context,
        appId: Int,
        targetContainerId: Int?,
    ): SyncAction {
        val game = getEpicGame(context, appId) ?: return SyncAction.NONE
        if (!game.cloudSaveEnabled) return SyncAction.NONE
        val credentials = EpicAuthManager.getStoredCredentials(context)
        if (credentials.isFailure) return SyncAction.NONE
        val creds = credentials.getOrNull() ?: return SyncAction.NONE
        return determineSyncAction(context, creds.accountId, game, "probe", targetContainerId)
    }

    suspend fun getPendingExitSyncAction(
        context: Context,
        appId: Int,
    ): SyncAction = getPendingExitSyncAction(context, appId, null)

    suspend fun getPendingExitSyncAction(
        context: Context,
        appId: Int,
        targetContainerId: Int?,
    ): SyncAction {
        val game = getEpicGame(context, appId) ?: return SyncAction.NONE
        if (!game.cloudSaveEnabled) return SyncAction.NONE
        val credentials = EpicAuthManager.getStoredCredentials(context)
        if (credentials.isFailure) return SyncAction.NONE
        val creds = credentials.getOrNull() ?: return SyncAction.NONE
        return determineSyncAction(context, creds.accountId, game, "exit_upload", targetContainerId)
    }

    suspend fun getResolvedSaveDirectory(
        context: Context,
        appId: Int,
    ): File? = getResolvedSaveDirectory(context, appId, null)

    suspend fun getResolvedSaveDirectory(
        context: Context,
        appId: Int,
        targetContainerId: Int?,
    ): File? =
        withContext(Dispatchers.IO) {
            val game = getEpicGame(context, appId) ?: return@withContext null
            if (!game.cloudSaveEnabled) return@withContext null
            val credentials = EpicAuthManager.getStoredCredentials(context)
            if (credentials.isFailure) return@withContext null
            val creds = credentials.getOrNull() ?: return@withContext null
            resolveSaveDirectory(context, game, creds.accountId, targetContainerId)
        }

    /**
     * Startup restore pass: if an installed Epic game has cloud saves enabled and
     * no local files in the resolved save directory, pull the latest cloud save
     * before the user launches it.
     */
    suspend fun restoreCloudSavesIfLocalMissing(
        context: Context,
        appId: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val game = getEpicGame(context, appId) ?: return@withContext false
            if (!game.cloudSaveEnabled) return@withContext false
            val credentials = EpicAuthManager.getStoredCredentials(context).getOrNull() ?: return@withContext false
            val saveDir = resolveSaveDirectory(context, game, credentials.accountId)
            if (saveDir.hasAnySaveFile()) {
                Timber.tag("Epic").d("[Cloud Saves] Startup restore skipped for ${game.title}: local saves already exist")
                return@withContext true
            }

            val cloudSaves =
                listCloudSaves(game.appName, context)
                    .getOrElse {
                        Timber.tag("Epic").w(it, "[Cloud Saves] Startup restore failed to list saves for ${game.title}")
                        return@withContext false
                    }
            if (findLatestManifest(cloudSaves.files) == null) {
                Timber.tag("Epic").d("[Cloud Saves] Startup restore found no cloud manifest for ${game.title}")
                return@withContext false
            }

            Timber.tag("Epic").i("[Cloud Saves] Startup restore downloading Epic cloud saves for ${game.title}")
            syncCloudSaves(context, appId, "download")
        }

    suspend fun listCloudSaveHistory(
        context: Context,
        appId: Int,
    ): List<CloudSaveHistoryEntry> =
        withContext(Dispatchers.IO) {
            val game = getEpicGame(context, appId) ?: return@withContext emptyList()
            if (!game.cloudSaveEnabled) return@withContext emptyList()

            val cloudSaves =
                listCloudSaves(game.appName, context)
                    .getOrElse {
                        Timber.tag("Epic").w(it, "[Cloud Saves] Failed to list history for ${game.title}")
                        return@withContext emptyList()
                    }

            val manifests =
                cloudSaves.files.entries
                    .filter { it.key.endsWith(".manifest") }
                    .sortedByDescending { parseManifestTimestamp(it.key, it.value) }
                    .take(30)

            manifests.map { (manifestPath, manifestInfo) ->
                val manifest =
                    manifestInfo.readLink
                        ?.let { downloadFile(it).getOrNull() }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { bytes ->
                            runCatching { EpicManifest.readAll(bytes) }
                                .onFailure {
                                    Timber.tag("Epic").w(it, "[Cloud Saves] Failed to parse history manifest $manifestPath")
                                }.getOrNull()
                        }
                val files = manifest?.fileManifestList?.elements.orEmpty()
                CloudSaveHistoryEntry(
                    manifestPath = manifestPath,
                    timestampMs = parseManifestTimestamp(manifestPath, manifestInfo),
                    fileCount = files.size,
                    sizeBytes = files.sumOf { it.fileSize },
                )
            }
        }

    suspend fun restoreCloudSaveHistoryEntry(
        context: Context,
        appId: Int,
        manifestPath: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val credentials = EpicAuthManager.getStoredCredentials(context).getOrNull() ?: return@withContext false
            downloadSaves(context, appId, credentials.accountId, manifestPath)
        }

    /**
     * Pre-flight for the game-exit upload path: returns `true` only when an upload
     * could plausibly succeed — the game opts into cloud saves (Epic catalog
     * provides a `CloudSaveFolder` template, [EpicGame.cloudSaveEnabled] = true),
     * the user is signed in, and the resolved save directory contains at least
     * one file to push.
     *
     * Without this guard, [XServerDisplayActivity.runExitUploadWithRetries] hits
     * an immediate `false` from [syncCloudSaves] for any Epic game that doesn't
     * support cloud saves (most don't — it's an opt-in catalog flag) or for any
     * fresh install with no saves yet, and then runs the full retry-with-backoff
     * loop showing the user "Cloud Sync Checking… Retry 3/3" for a permanent
     * no-op.
     *
     * Non-suspend so Java callers (the activity is .java) can call it directly.
     */
    @JvmStatic
    fun canAttemptExitUpload(
        context: Context,
        appId: Int,
    ): Boolean = canAttemptExitUpload(context, appId, null)

    @JvmStatic
    fun canAttemptExitUpload(
        context: Context,
        appId: Int,
        targetContainerId: Int?,
    ): Boolean =
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            try {
                val game = getEpicGame(context, appId) ?: return@runBlocking false
                if (!game.cloudSaveEnabled) {
                    Timber.tag("Epic").i("[Cloud Saves] Skip exit upload: ${game.title} does not opt into Epic cloud saves")
                    return@runBlocking false
                }
                val credentialsResult = EpicAuthManager.getStoredCredentials(context)
                if (credentialsResult.isFailure) {
                    Timber.tag("Epic").i("[Cloud Saves] Skip exit upload: not signed in to Epic")
                    return@runBlocking false
                }
                val creds = credentialsResult.getOrNull() ?: return@runBlocking false
                val saveDir = resolveSaveDirectory(context, game, creds.accountId, targetContainerId)
                if (saveDir == null || !saveDir.exists()) {
                    Timber.tag("Epic").i("[Cloud Saves] Skip exit upload: no save directory yet for ${game.title}")
                    return@runBlocking false
                }
                if (!saveDir.hasAnySaveFile()) {
                    Timber.tag("Epic").i("[Cloud Saves] Skip exit upload: save directory empty for ${game.title}")
                    return@runBlocking false
                }
                true
            } catch (e: Exception) {
                Timber.tag("Epic").w(e, "[Cloud Saves] canAttemptExitUpload threw, skipping")
                false
            }
        }

    /**
     * Sync cloud saves for a game (bidirectional sync with conflict detection)
     * preferredAction = download -> Force downloads all files and overwrites current files
     * preferredAction = upload -> Force uploads all files
     * preferredAction = exit_upload -> Timestamp check and upload only if local saves are newer
     * preferredAction = "auto" -> Timestamp check and uploads/downloads the files pending on the timestamp resolution
     * @param preferredAction "download", "upload", "exit_upload", or "auto" (default)
     */
    suspend fun syncCloudSaves(
        context: Context,
        appId: Int,
        preferredAction: String = "auto",
    ): Boolean = syncCloudSaves(context, appId, preferredAction, null)

    suspend fun syncCloudSaves(
        context: Context,
        appId: Int,
        preferredAction: String,
        targetContainerId: Int?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            // Check if sync is already in progress for this appId
            val forceUpload = preferredAction.equals("upload", ignoreCase = true)
            val uploadRequest =
                forceUpload || preferredAction.equals("exit_upload", ignoreCase = true)
            val syncScope = syncScope(appId, targetContainerId)
            syncMutex.withLock {
                if (forceUpload && wasUploadRecentlyCompletedLocked(syncScope)) {
                    Timber.tag("Epic").i("[Cloud Saves] Recent upload already completed for $syncScope, skipping duplicate upload")
                    return@withContext true
                }
                if (activeSyncs.contains(appId)) {
                    Timber.tag("Epic").w("[Cloud Saves] Sync already in progress for $appId, skipping duplicate request")
                    return@withContext uploadRequest
                }
                activeSyncs.add(appId)
            }

            try {
                Timber.tag("Epic").i("[Cloud Saves] Starting sync for $appId (action: $preferredAction, container: ${syncScope.containerId ?: "auto"})")

                // Get game info to retrieve appName
                val game = getEpicGame(context, appId)
                if (game == null) {
                    Timber.tag("Epic").e("[Cloud Saves] Game not found: $appId")
                    return@withContext false
                }

                // Check if game supports cloud saves
                if (!game.cloudSaveEnabled) {
                    Timber.tag("Epic").w("[Cloud Saves] Game does not support cloud saves: ${game.title}")
                    return@withContext false
                }

                // Get credentials and validate.
                val credentials = EpicAuthManager.getStoredCredentials(context)
                if (credentials.isFailure) {
                    Timber.tag("Epic").e("[Cloud Saves] Not logged in to Epic: ${credentials.exceptionOrNull()?.message}")
                    return@withContext false
                }

                val creds = credentials.getOrNull()!!
                Timber.tag("Epic").d("[Cloud Saves] Using account: ${creds.accountId} (${creds.displayName})")

                //  Determine sync action - Upload,Download, Conflict or none
                val action = determineSyncAction(context, creds.accountId, game, preferredAction, targetContainerId)

                Timber.tag("Epic").i("[Cloud Saves] Sync action determined: $action")

                // Execute the action
                val result =
                    when (action) {
                        SyncAction.DOWNLOAD -> {
                            downloadSaves(context, appId, creds.accountId, targetContainerId = targetContainerId)
                        }

                        SyncAction.UPLOAD -> {
                            uploadSaves(context, creds.accountId, game, targetContainerId = targetContainerId)
                        }

                        SyncAction.CONFLICT -> {
                            Timber.tag("Epic").w("[Cloud Saves] Conflict detected - resolving via timestamp comparison")
                            resolveConflict(context, creds.accountId, game, targetContainerId)
                        }

                        SyncAction.NONE -> {
                            Timber.tag("Epic").i("[Cloud Saves] No sync needed")
                            true
                        }
                    }

                if (result) {
                    Timber.tag("Epic").i("[Cloud Saves] Sync completed successfully")
                }

                result
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Sync failed")
                false
            } finally {
                // Always remove from active syncs when done
                syncMutex.withLock {
                    activeSyncs.remove(appId)
                }
            }
        }

    /**
     * Determine what sync action to take based on local and cloud state
     */
    private suspend fun determineSyncAction(
        context: Context,
        accountId: String,
        game: com.winlator.cmod.feature.stores.epic.data.EpicGame,
        preferredAction: String,
        targetContainerId: Int?,
    ): SyncAction =
        withContext(Dispatchers.IO) {
            try {
                val uploadOnly = preferredAction.equals("exit_upload", ignoreCase = true)
                val probeOnly = preferredAction.equals("probe", ignoreCase = true)

                // Force action if requested
                if (preferredAction.equals("download", ignoreCase = true)) return@withContext SyncAction.DOWNLOAD
                if (preferredAction.equals("upload", ignoreCase = true)) return@withContext SyncAction.UPLOAD

                // Check local save directory
                val saveDir = resolveSaveDirectory(context, game, accountId, targetContainerId)
                val hasLocalFiles = saveDir.hasAnySaveFile()

                // Check cloud saves
                val cloudSavesResult = listCloudSaves(game.appName, context)
                if (cloudSavesResult.isFailure) {
                    Timber.tag("Epic").w("[Cloud Saves] Failed to list cloud saves")
                    if (probeOnly) return@withContext SyncAction.NONE
                    return@withContext if (hasLocalFiles) SyncAction.UPLOAD else SyncAction.NONE
                }

                val cloudSaves = cloudSavesResult.getOrNull()!!
                val hasCloudFiles = cloudSaves.files.isNotEmpty()

                // Simple cases
                when {
                    hasLocalFiles && !hasCloudFiles -> return@withContext SyncAction.UPLOAD
                    !hasLocalFiles && hasCloudFiles -> return@withContext if (uploadOnly) SyncAction.NONE else SyncAction.DOWNLOAD
                    !hasLocalFiles && !hasCloudFiles -> return@withContext SyncAction.NONE
                }

                // Both local and cloud have files - compare timestamps
                val (manifestPath, manifestInfo) =
                    findLatestManifest(cloudSaves.files) ?: run {
                        Timber.tag("Epic").w("[Cloud Saves] No manifest in cloud, will upload")
                        return@withContext SyncAction.UPLOAD
                    }

                val cloudTimestamp = manifestInfo.lastModified
                val cloudTimestampMillis = parseManifestTimestamp(manifestPath, manifestInfo)
                val lastSyncTimestamp = getSyncTimestamp(context, game.id)
                val lastSyncTimestampMillis = lastSyncTimestamp?.let { parseTimestamp(it) }?.takeIf { it > 0L }

                // Get local newest file timestamp
                val localNewestTimestamp =
                    newestLocalSaveTimestamp(saveDir)

                Timber
                    .tag("Epic")
                    .d(
                        "[Cloud Saves] Timestamp compare: " +
                            "local=${formatTimestampForLog(localNewestTimestamp)}, " +
                            "cloud=${formatTimestampForLog(cloudTimestampMillis)} ($cloudTimestamp), " +
                            "lastSync=${formatTimestampForLog(lastSyncTimestampMillis)}",
                    )
                Log.i(
                    ANDROID_LOG_TAG,
                    "Timestamp compare appId=${game.id} local=${formatTimestampForLog(localNewestTimestamp)} " +
                        "cloud=${formatTimestampForLog(cloudTimestampMillis)} remoteLastModified=$cloudTimestamp " +
                        "lastSync=${formatTimestampForLog(lastSyncTimestampMillis)} " +
                        "newestLocalFile=${newestLocalSaveFile(saveDir)?.absolutePath}",
                )

                val action =
                    when {
                        localNewestTimestamp == null -> SyncAction.DOWNLOAD
                        timestampsAreClose(localNewestTimestamp, cloudTimestampMillis) -> SyncAction.NONE
                        lastSyncTimestampMillis == null -> SyncAction.CONFLICT
                        timestampChangedSince(localNewestTimestamp, lastSyncTimestampMillis) &&
                            timestampChangedSince(cloudTimestampMillis, lastSyncTimestampMillis) -> SyncAction.CONFLICT
                        timestampChangedSince(localNewestTimestamp, lastSyncTimestampMillis) -> SyncAction.UPLOAD
                        timestampChangedSince(cloudTimestampMillis, lastSyncTimestampMillis) -> SyncAction.DOWNLOAD
                        else -> SyncAction.NONE
                    }
                val resolvedAction =
                    if (uploadOnly && action != SyncAction.UPLOAD) {
                        SyncAction.NONE
                    } else {
                        action
                    }
                Log.i(ANDROID_LOG_TAG, "Sync action appId=${game.id}: $resolvedAction")
                resolvedAction
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Error determining sync action")
                SyncAction.NONE
            }
        }

    /**
     * Parse Epic timestamp string to milliseconds
     */
    private fun parseTimestamp(timestamp: String): Long =
        try {
            val instant = Instant.parse(timestamp)
            instant.toEpochMilli()
        } catch (e: Exception) {
            Timber.tag("Epic").w(e, "[Cloud Saves] Failed to parse timestamp: $timestamp")
            0L
        }

    private fun formatTimestampForLog(timestampMs: Long?): String =
        if (timestampMs == null || timestampMs <= 0L) {
            "none"
        } else {
            "${Instant.ofEpochMilli(timestampMs)} [$timestampMs]"
        }

    private fun timestampsAreClose(
        firstTimestampMs: Long,
        secondTimestampMs: Long,
    ): Boolean =
        kotlin.math.abs(firstTimestampMs - secondTimestampMs) < SAVE_TIMESTAMP_EQUAL_TOLERANCE_MS

    private fun timestampChangedSince(
        timestampMs: Long,
        baselineTimestampMs: Long,
    ): Boolean = timestampMs - baselineTimestampMs > SAVE_TIMESTAMP_EQUAL_TOLERANCE_MS

    private suspend fun getEpicGame(
        context: Context,
        appId: Int,
    ): EpicGame? =
        withContext(Dispatchers.IO) {
            EpicService.getEpicGameOf(appId)
                ?: runCatching {
                    PluviaDatabase
                        .getInstance(context.applicationContext)
                        .epicGameDao()
                        .getById(appId)
                }.onFailure {
                    Timber.tag("Epic").w(it, "[Cloud Saves] Failed to load Epic game $appId from database")
                }.getOrNull()
        }

    private fun File?.hasAnySaveFile(): Boolean = this?.exists() == true && walkTopDown().any { it.isFile }

    private fun newestLocalSaveTimestamp(saveDir: File?): Long? =
        newestLocalSaveFile(saveDir)?.lastModified()

    private fun newestLocalSaveFile(saveDir: File?): File? =
        saveDir
            ?.takeIf { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

    private fun parseManifestTimestamp(
        manifestPath: String,
        manifestInfo: CloudFileInfo,
    ): Long {
        val manifestName = manifestPath.substringAfterLast("/")
        val fromName =
            runCatching {
                java.time.LocalDateTime
                    .parse(
                        manifestName.removeSuffix(".manifest"),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd-HH.mm.ss"),
                    ).toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli()
            }.getOrNull()
        if (fromName != null) return fromName
        return parseTimestamp(manifestInfo.lastModified)
    }

    // List available cloud saves
    private suspend fun listCloudSaves(
        appName: String,
        context: Context,
    ): Result<CloudSaveFiles> =
        withContext(Dispatchers.IO) {
            try {
                // Get global Epic credentials (will auto-refresh if expired)
                val credentialsResult = EpicAuthManager.getStoredCredentials(context)
                if (credentialsResult.isFailure) {
                    return@withContext Result.failure(Exception("Not logged in to Epic"))
                }

                val credentials = credentialsResult.getOrNull()!!
                val accountId = credentials.accountId
                val accessToken = credentials.accessToken

                Timber.tag("Epic").d("[Cloud Saves] Listing saves for $appName (account: $accountId)")

                val request =
                    Request
                        .Builder()
                        .url("$baseCloudSyncUrl/api/v1/access/egstore/savesync/$accountId/$appName/")
                        .header("Authorization", "Bearer $accessToken")
                        .get()
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Timber.tag("Epic").e("[Cloud Saves] List failed HTTP ${response.code}: ${responseBody.take(500)}")
                        return@withContext Result.failure(Exception("Failed to list cloud saves: ${response.code}"))
                    }

                    val json = org.json.JSONObject(responseBody.ifEmpty { "{}" })
                    val filesJson = json.optJSONObject("files") ?: org.json.JSONObject()

                    val files = mutableMapOf<String, CloudFileInfo>()
                    filesJson.keys().forEach { key ->
                        val fileJson = filesJson.getJSONObject(key)
                        files[key] =
                            CloudFileInfo(
                                hash = fileJson.optString("hash", ""),
                                lastModified = fileJson.optString("lastModified", ""),
                                // optString returns "" for missing/null values; normalise
                                // back to null so downstream `?.let` / null-checks work.
                                readLink = fileJson.optString("readLink").ifEmpty { null },
                                writeLink = fileJson.optString("writeLink").ifEmpty { null },
                            )
                    }

                    Result.success(CloudSaveFiles(files))
                }
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "Failed to list cloud saves")
                Result.failure(e)
            }
        }

    // Download a single file
    private suspend fun downloadFile(readLink: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(readLink)
                        .get()
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use {
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                    }

                    val data = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty response"))

                    if (data.isEmpty()) {
                        Timber.tag("Epic").w("[Cloud Saves] Downloaded file is empty (0 bytes)")
                    }

                    Result.success(data)
                }
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "Failed to download file")
                Result.failure(e)
            }
        }

    // Find the latest manifest
    private fun findLatestManifest(files: Map<String, CloudFileInfo>): Pair<String, CloudFileInfo>? =
        files.entries
            .filter { it.key.endsWith(".manifest") }
            .maxByOrNull { parseManifestTimestamp(it.key, it.value) }
            ?.toPair()

    /**
     * Resolve conflict by comparing timestamps and selectively uploading/downloading
     */
    private suspend fun resolveConflict(
        context: Context,
        accountId: String,
        game: EpicGame,
        targetContainerId: Int?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Timber.tag("Epic").i("[Cloud Saves] Starting conflict resolution for ${game.id}")

                // 1. Get local save directory and files
                val saveDir =
                    resolveSaveDirectory(context, game, accountId, targetContainerId) ?: run {
                        Timber.tag("Epic").e("[Cloud Saves] Failed to resolve save directory")
                        return@withContext false
                    }

                val localFiles = mutableMapOf<String, Long>()
                if (saveDir.exists()) {
                    saveDir
                        .walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val relativePath = file.relativeTo(saveDir).path.replace("\\", "/")
                            localFiles[relativePath] = file.lastModified()
                        }
                }

                Timber.tag("Epic").i("[Cloud Saves] Found ${localFiles.size} local files")

                // 2. Get cloud files and their timestamps
                val cloudSavesResult = listCloudSaves(game.appName, context)
                if (cloudSavesResult.isFailure) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to list cloud saves")
                    return@withContext false
                }

                val cloudSaves = cloudSavesResult.getOrNull()!!
                val (manifestPath, manifestInfo) =
                    findLatestManifest(cloudSaves.files) ?: run {
                        Timber.tag("Epic").w("[Cloud Saves] No manifest in cloud, uploading all local files")
                        return@withContext uploadSaves(context, accountId, game, targetContainerId = targetContainerId)
                    }

                // 3. Download and parse manifest to get cloud file list with timestamps
                val manifestData = downloadFile(manifestInfo.readLink ?: return@withContext false)
                if (manifestData.isFailure) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to download manifest")
                    return@withContext false
                }

                val manifestBytes = manifestData.getOrNull()!!

                // Validate manifest is not empty
                if (manifestBytes.isEmpty()) {
                    Timber.tag("Epic").w("[Cloud Saves] Cloud manifest is empty, uploading all local files")
                    return@withContext uploadSaves(context, accountId, game, targetContainerId = targetContainerId)
                }

                val manifest =
                    try {
                        EpicManifest.readAll(manifestBytes)
                    } catch (e: Exception) {
                        Timber.tag("Epic").e(e, "[Cloud Saves] Failed to parse manifest (size: ${manifestBytes.size} bytes)")
                        // If manifest is corrupt, upload our local version
                        Timber.tag("Epic").w("[Cloud Saves] Manifest parse failed, uploading local files")
                        return@withContext uploadSaves(context, accountId, game, targetContainerId = targetContainerId)
                    }

                // Epic's savesync manifests carry only a single manifest-level `lastModified`
                // — there is no per-file mtime in the binary manifest format. Every cloud
                // file therefore shares the same timestamp from our perspective, and the
                // best we can do is decide whether each LOCAL file changed after the last
                // cloud snapshot was taken.
                val cloudManifestMtime = parseTimestamp(manifestInfo.lastModified)
                val cloudFileNames =
                    manifest.fileManifestList
                        ?.elements
                        ?.map { it.filename }
                        ?.toSet()
                        ?: emptySet()

                Timber.tag("Epic").i("[Cloud Saves] Found ${cloudFileNames.size} cloud files (manifest mtime $cloudManifestMtime)")

                // 4. Decide what to upload/download. Conflict-resolution semantics:
                //    - file in both: if local mtime > manifest mtime → local was edited locally,
                //      preserve it by uploading. Otherwise pull cloud copy down so any cloud-side
                //      change since the last sync isn't lost when the subsequent full upload runs.
                //    - file local-only: upload (was created here).
                //    - file cloud-only: download (was created elsewhere; would be deleted by upload otherwise).
                val toUpload = mutableListOf<String>()
                val toDownload = mutableListOf<String>()

                val commonPaths = localFiles.keys.intersect(cloudFileNames)
                commonPaths.forEach { path ->
                    val localTime = localFiles[path]!!
                    if (localTime > cloudManifestMtime) {
                        Timber.tag("Epic").i("[Cloud Saves] Local file changed since cloud snapshot: $path (local=$localTime > cloud=$cloudManifestMtime)")
                        toUpload.add(path)
                    } else {
                        Timber.tag("Epic").d("[Cloud Saves] Local file unchanged since cloud snapshot, refreshing from cloud: $path")
                        toDownload.add(path)
                    }
                }

                (localFiles.keys - commonPaths).forEach { path ->
                    Timber.tag("Epic").i("[Cloud Saves] File only exists locally: $path")
                    toUpload.add(path)
                }

                (cloudFileNames - commonPaths).forEach { path ->
                    Timber.tag("Epic").i("[Cloud Saves] File only exists in cloud: $path")
                    toDownload.add(path)
                }

                // 5. Execute downloads first (so we have all cloud files locally before uploading)
                var downloadSuccess = true
                if (toDownload.isNotEmpty()) {
                    Timber.tag("Epic").i("[Cloud Saves] Downloading ${toDownload.size} files based on timestamp comparison")

                    // Download the required chunks and reconstruct files
                    val chunks = mutableMapOf<String, ByteArray>()
                    val pathPrefix = manifestPath.split("/", limit = 4).take(3).joinToString("/")

                    Timber.tag("Epic").d("[Cloud Saves] Manifest path: $manifestPath")
                    Timber.tag("Epic").d("[Cloud Saves] Path prefix: $pathPrefix")
                    Timber.tag("Epic").d("[Cloud Saves] Available cloud files: ${cloudSaves.files.keys.take(10)}")

                    manifest.chunkDataList?.elements?.forEach { chunkInfo ->
                        try {
                            val chunkPath = "$pathPrefix/${chunkInfo.getPath()}"
                            Timber.tag("Epic").d("[Cloud Saves] Looking for chunk at: $chunkPath")
                            val chunkFile = cloudSaves.files[chunkPath]

                            if (chunkFile?.readLink == null) {
                                Timber.tag("Epic").w("[Cloud Saves] Chunk not found in cloud: $chunkPath")
                                downloadSuccess = false
                                return@forEach
                            }

                            Timber.tag("Epic").d("[Cloud Saves] Downloading chunk: ${chunkInfo.getPath()}")
                            val chunkData = downloadFile(chunkFile.readLink)
                            if (chunkData.isSuccess) {
                                val chunkBytes = chunkData.getOrNull()!!
                                val decompressedData = decompressChunk(chunkBytes)
                                chunks[chunkInfo.guidStr] = decompressedData
                            }
                        } catch (e: Exception) {
                            Timber.tag("Epic").e(e, "[Cloud Saves] Error processing chunk: ${chunkInfo.getPath()}")
                        }
                    }

                    // Reconstruct only the files we need to download
                    val saveDirCanonical = saveDir.canonicalPath
                    manifest.fileManifestList?.elements?.forEach { fileManifest ->
                        if (toDownload.contains(fileManifest.filename)) {
                            try {
                                val outputFile = File(saveDir, fileManifest.filename)
                                if (!outputFile.canonicalPath.startsWith(saveDirCanonical + File.separator) &&
                                    outputFile.canonicalPath != saveDirCanonical
                                ) {
                                    Timber.tag("Epic").w("[Cloud Saves] Skipping path traversal: ${fileManifest.filename}")
                                    downloadSuccess = false
                                    return@forEach
                                }
                                outputFile.parentFile?.mkdirs()

                                Timber.tag("Epic").d("[Cloud Saves] Reconstructing file: ${fileManifest.filename}")

                                outputFile.outputStream().use { output ->
                                    fileManifest.chunkParts.forEach { chunkPart ->
                                        val chunkData = chunks[chunkPart.guidStr]
                                        if (chunkData == null) {
                                            Timber
                                                .tag(
                                                    "Epic",
                                                ).e("[Cloud Saves] Chunk missing for ${fileManifest.filename}: ${chunkPart.guidStr}")
                                            downloadSuccess = false
                                        } else {
                                            val partData =
                                                chunkData.copyOfRange(
                                                    chunkPart.offset.toInt(),
                                                    (chunkPart.offset + chunkPart.size).toInt(),
                                                )
                                            output.write(partData)
                                        }
                                    }
                                }

                                Timber.tag("Epic").i("[Cloud Saves] Downloaded: ${fileManifest.filename}")
                            } catch (e: Exception) {
                                Timber.tag("Epic").e(e, "[Cloud Saves] Failed to reconstruct file: ${fileManifest.filename}")
                                downloadSuccess = false
                            }
                        }
                    }
                }

                // 6. Execute uploads
                var uploadSuccess = true
                if (toUpload.isNotEmpty()) {
                    Timber.tag("Epic").i("[Cloud Saves] Uploading ${toUpload.size} files based on timestamp comparison")
                    // ! Upload ALL local files, to ensure the manifest is correct with save-state
                    uploadSuccess = uploadSaves(context, accountId, game, targetContainerId = targetContainerId)
                }

                // 7. Update sync timestamp if both operations succeeded
                if (downloadSuccess && uploadSuccess) {
                    val timestamp =
                        java.time.Instant
                            .now()
                            .toString()
                    setSyncTimestamp(context, game.id, timestamp)
                    Timber.tag("Epic").i("[Cloud Saves] Conflict resolution complete")
                    return@withContext true
                }

                false
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Conflict resolution failed")
                false
            }
        }

    // Download saves flow
    private suspend fun downloadSaves(
        context: Context,
        appId: Int,
        accountId: String,
        requestedManifestPath: String? = null,
        targetContainerId: Int? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Timber.tag("Epic").i("[Cloud Saves] Starting download for $appId")

                // 1. Get game info
                val game = getEpicGame(context, appId)
                if (game?.cloudSaveEnabled != true) {
                    Timber.tag("Epic").w("[Cloud Saves] Game does not support cloud saves")
                    return@withContext false
                }

                // 2. List cloud saves
                val cloudSavesResult = listCloudSaves(game.appName, context)
                if (cloudSavesResult.isFailure) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to list saves: ${cloudSavesResult.exceptionOrNull()?.message}")
                    return@withContext false
                }

                val cloudSaves = cloudSavesResult.getOrNull()!!
                if (cloudSaves.files.isEmpty()) {
                    Timber.tag("Epic").i("[Cloud Saves] No cloud saves found")
                    return@withContext false
                }

                // 3. Find the requested manifest, or fall back to latest.
                val selectedManifest =
                    requestedManifestPath
                        ?.takeIf { it.endsWith(".manifest") }
                        ?.let { path ->
                            cloudSaves.files[path]?.let { path to it } ?: run {
                                Timber.tag("Epic").w("[Cloud Saves] Requested manifest not found in cloud saves: $path")
                                return@withContext false
                            }
                        }
                        ?: findLatestManifest(cloudSaves.files)
                val (manifestPath, manifestInfo) =
                    selectedManifest ?: run {
                        Timber.tag("Epic").w("[Cloud Saves] No manifest found in cloud saves")
                        return@withContext false
                    }

                Timber.tag("Epic").i("[Cloud Saves] Found manifest: $manifestPath (${manifestInfo.lastModified})")

                // Always reconcile when the caller asked us to download — the prior
                // "lastSync >= cloudTimestamp" short-circuit silently skipped explicit
                // re-downloads (and reported success) once a sync timestamp existed,
                // leaving deleted/corrupted local saves in place.

                // 5. Download manifest
                val manifestData = downloadFile(manifestInfo.readLink ?: return@withContext false)
                if (manifestData.isFailure) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to download manifest")
                    return@withContext false
                }

                // 6. Parse manifest
                val manifestBytes = manifestData.getOrNull()!!

                // Validate manifest is not empty
                if (manifestBytes.isEmpty()) {
                    Timber.tag("Epic").e("[Cloud Saves] Downloaded manifest is empty")
                    return@withContext false
                }

                val manifest =
                    try {
                        EpicManifest.readAll(manifestBytes)
                    } catch (e: Exception) {
                        Timber.tag("Epic").e(e, "[Cloud Saves] Failed to parse manifest (size: ${manifestBytes.size} bytes)")
                        return@withContext false
                    }

                Timber.tag("Epic").i("[Cloud Saves] Manifest parsed: ${manifest.fileManifestList?.elements?.size ?: 0} files")

                // 7. Download chunks referenced in manifest
                val chunks = mutableMapOf<String, ByteArray>()
                val pathPrefix = manifestPath.split("/", limit = 4).take(3).joinToString("/")
                Timber.tag("Epic").d("[Cloud Saves] Path prefix derived from manifest key: $pathPrefix")

                var missingChunks = 0
                manifest.chunkDataList?.elements?.forEach { chunkInfo ->
                    try {
                        // Get chunk path using ChunkInfo's getPath method
                        val chunkPath = "$pathPrefix/${chunkInfo.getPath()}"
                        val chunkFile = cloudSaves.files[chunkPath]

                        if (chunkFile?.readLink == null) {
                            missingChunks++
                            Timber.tag("Epic").w("[Cloud Saves] Chunk not found in cloud: $chunkPath")
                            if (missingChunks == 1) {
                                // Log the actual cloud keys once so a path-prefix mismatch is
                                // diagnosable from a user-supplied logcat without code changes.
                                Timber
                                    .tag("Epic")
                                    .w("[Cloud Saves] Available cloud keys (first 5): ${cloudSaves.files.keys.take(5)}")
                            }
                            return@forEach
                        }

                        Timber.tag("Epic").d("[Cloud Saves] Downloading chunk: ${chunkInfo.getPath()}")
                        val chunkData = downloadFile(chunkFile.readLink)
                        if (chunkData.isSuccess) {
                            // Decompress and extract chunk data
                            val chunkBytes = chunkData.getOrNull()!!
                            val decompressedData = decompressChunk(chunkBytes)
                            chunks[chunkInfo.guidStr] = decompressedData
                            Timber.tag("Epic").d("[Cloud Saves] Chunk downloaded: ${chunkInfo.guidStr} (${decompressedData.size} bytes)")
                        } else {
                            Timber.tag("Epic").e("[Cloud Saves] Failed to download chunk: ${chunkInfo.getPath()}")
                        }
                    } catch (e: Exception) {
                        Timber.tag("Epic").e(e, "[Cloud Saves] Error processing chunk: ${chunkInfo.getPath()}")
                    }
                }

                if (chunks.isEmpty()) {
                    Timber.tag("Epic").e("[Cloud Saves] No chunks were downloaded, aborting")
                    return@withContext false
                }

                // 8. Reconstruct files from chunks
                val saveDir =
                    resolveSaveDirectory(context, game, accountId, targetContainerId) ?: run {
                        Timber.tag("Epic").e("[Cloud Saves] Failed to resolve save directory")
                        return@withContext false
                    }

                saveDir.mkdirs()

                var downloadedFiles = 0
                var failedFiles = 0
                val expectedFiles = manifest.fileManifestList?.elements?.size ?: 0
                val manifestTimestamp = parseManifestTimestamp(manifestPath, manifestInfo)

                val saveDirCanonical = saveDir.canonicalPath
                manifest.fileManifestList?.elements?.forEach { fileManifest ->
                    val outputFile = File(saveDir, fileManifest.filename)
                    // Defence-in-depth: a hostile or malformed manifest could embed a
                    // filename like "../../etc/whatever" and trick us into writing files
                    // outside the save directory. canonicalPath collapses any "../" so we
                    // can sanity-check containment.
                    if (!outputFile.canonicalPath.startsWith(saveDirCanonical + File.separator) &&
                        outputFile.canonicalPath != saveDirCanonical
                    ) {
                        Timber.tag("Epic").w("[Cloud Saves] Skipping path traversal: ${fileManifest.filename}")
                        failedFiles++
                        return@forEach
                    }
                    val tempFile = File(outputFile.parentFile, "${outputFile.name}.partial")
                    try {
                        outputFile.parentFile?.mkdirs()

                        Timber.tag("Epic").d("[Cloud Saves] Reconstructing file: ${fileManifest.filename}")

                        // Stream into a sibling .partial first so we never leave a half-written
                        // file in place when a chunk is missing — Epic's own client treats a
                        // partial save as corrupt and abandoning the rename leaves the previous
                        // good copy untouched.
                        var fileOk = true
                        tempFile.outputStream().use { output ->
                            for (chunkPart in fileManifest.chunkParts) {
                                val chunkData = chunks[chunkPart.guidStr]
                                if (chunkData == null) {
                                    Timber
                                        .tag("Epic")
                                        .e("[Cloud Saves] Chunk missing for ${fileManifest.filename}: ${chunkPart.guidStr}")
                                    fileOk = false
                                    break
                                }
                                val partEnd = (chunkPart.offset + chunkPart.size).toInt()
                                if (partEnd > chunkData.size) {
                                    Timber
                                        .tag("Epic")
                                        .e(
                                            "[Cloud Saves] Chunk part out of range for ${fileManifest.filename}: " +
                                                "offset=${chunkPart.offset} size=${chunkPart.size} chunk=${chunkData.size}",
                                        )
                                    fileOk = false
                                    break
                                }
                                output.write(chunkData.copyOfRange(chunkPart.offset.toInt(), partEnd))
                            }
                        }

                        if (fileOk && tempFile.renameTo(outputFile)) {
                            if (manifestTimestamp > 0L) outputFile.setLastModified(manifestTimestamp)
                            downloadedFiles++
                            Timber.tag("Epic").i("[Cloud Saves] Reconstructed: ${fileManifest.filename} (${outputFile.length()} bytes)")
                        } else {
                            failedFiles++
                            tempFile.delete()
                            Timber.tag("Epic").e("[Cloud Saves] Discarded partial: ${fileManifest.filename}")
                        }
                    } catch (e: Exception) {
                        failedFiles++
                        tempFile.delete()
                        Timber.tag("Epic").e(e, "[Cloud Saves] Failed to reconstruct file: ${fileManifest.filename}")
                    }
                }

                if (failedFiles > 0) {
                    Timber
                        .tag("Epic")
                        .e("[Cloud Saves] Download incomplete: $failedFiles/$expectedFiles files failed (missing chunks: $missingChunks)")
                    return@withContext false
                }

                // 9. Update sync timestamp
                setSyncTimestamp(context, appId, manifestInfo.lastModified)

                Timber.tag("Epic").i("[Cloud Saves] Download complete: $downloadedFiles/$expectedFiles files reconstructed")
                downloadedFiles > 0
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Download failed")
                false
            }
        }

    // Upload saves flow
    private suspend fun uploadSaves(
        context: Context,
        accountId: String,
        game: EpicGame,
        fileList: List<String>? = null, // Optional: only upload specific files
        targetContainerId: Int? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val syncScope = syncScope(game.id, targetContainerId)
                if (wasUploadRecentlyCompleted(syncScope)) {
                    Timber.tag("Epic").i("[Cloud Saves] Recent upload already completed for $syncScope, skipping duplicate upload")
                    return@withContext true
                }

                Timber.tag("Epic").i("[Cloud Saves] Starting upload for ${game.id}")
                Log.i(ANDROID_LOG_TAG, "Starting upload appId=${game.id}")

                // 1. Get local save directory
                val saveDir =
                    resolveSaveDirectory(context, game, accountId, targetContainerId) ?: run {
                        Timber.tag("Epic").e("[Cloud Saves] Failed to resolve save directory")
                        return@withContext false
                    }

                if (!saveDir.hasAnySaveFile()) {
                    Timber.tag("Epic").w("[Cloud Saves] No local saves to upload")
                    return@withContext false
                }

                // 2. Package save files into chunks and manifest
                if (fileList != null) {
                    Timber.tag("Epic").i("[Cloud Saves] Packaging ${fileList.size} specific files from: ${saveDir.absolutePath}")
                } else {
                    Timber.tag("Epic").i("[Cloud Saves] Packaging all save files from: ${saveDir.absolutePath}")
                }
                val packagedFiles = packageSaveFiles(saveDir, game, accountId, fileList)
                if (packagedFiles.isEmpty()) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to package save files")
                    return@withContext false
                }

                // Filter out empty files (just log them, don't fail the upload)
                val emptyFiles = packagedFiles.filter { it.value.isEmpty() }
                if (emptyFiles.isNotEmpty()) {
                    Timber
                        .tag(
                            "Epic",
                        ).w("[Cloud Saves] Skipping ${emptyFiles.size} empty packaged files: ${emptyFiles.keys.joinToString()}")
                }

                // Only upload non-empty files
                val nonEmptyFiles = packagedFiles.filterValues { it.isNotEmpty() }
                if (nonEmptyFiles.isEmpty()) {
                    Timber.tag("Epic").e("[Cloud Saves] No valid files to upload after filtering empty files")
                    return@withContext false
                }

                val manifestEntry = nonEmptyFiles.entries.find { it.key.endsWith(".manifest") }
                if (manifestEntry == null) {
                    Timber.tag("Epic").e("[Cloud Saves] No manifest was packaged for upload")
                    return@withContext false
                }

                // 3. Request write links for all files
                val fileNames = nonEmptyFiles.keys.toList()
                val writeLinks = requestWriteLinks(context, game.appName, fileNames)
                if (writeLinks.isEmpty()) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to get write links")
                    return@withContext false
                }

                // 4. Upload chunks
                var uploadedChunks = 0
                var failedUploads = 0
                nonEmptyFiles.forEach { (fileName, fileData) ->
                    if (!fileName.endsWith(".manifest")) {
                        val writeLink = writeLinks[fileName]
                        if (writeLink != null) {
                            Timber.tag("Epic").d("[Cloud Saves] Uploading chunk: $fileName (${fileData.size} bytes)")
                            val result = uploadFile(writeLink, fileData)
                            if (result.isSuccess) {
                                uploadedChunks++
                                Timber.tag("Epic").i("[Cloud Saves] Uploaded chunk: $fileName (${fileData.size} bytes)")
                            } else {
                                failedUploads++
                                Timber
                                    .tag(
                                        "Epic",
                                    ).e("[Cloud Saves] Failed to upload chunk: $fileName - ${result.exceptionOrNull()?.message}")
                            }
                        } else {
                            failedUploads++
                            Timber.tag("Epic").e("[Cloud Saves] Missing write link for chunk: $fileName")
                        }
                    }
                }

                if (failedUploads > 0) {
                    Timber.tag("Epic").e("[Cloud Saves] Aborting upload: $failedUploads chunk(s) failed before manifest upload")
                    return@withContext false
                }

                // 5. Upload manifest last
                val writeLink = writeLinks[manifestEntry.key]
                if (writeLink != null) {
                    Timber.tag("Epic").d("[Cloud Saves] Uploading manifest: ${manifestEntry.key} (${manifestEntry.value.size} bytes)")
                    val result = uploadFile(writeLink, manifestEntry.value)
                    if (result.isSuccess) {
                        Timber
                            .tag(
                                "Epic",
                            ).i("[Cloud Saves] Uploaded manifest: ${manifestEntry.key} (${manifestEntry.value.size} bytes)")

                        // Update sync timestamp
                        val timestamp =
                            java.time.Instant
                                .now()
                                .toString()
                        setSyncTimestamp(context, game.id, timestamp)
                        markUploadCompleted(syncScope)

                        Timber.tag("Epic").i("[Cloud Saves] Upload complete: $uploadedChunks chunks uploaded")
                        Log.i(ANDROID_LOG_TAG, "Upload complete appId=${game.id} manifest=${manifestEntry.key} chunks=$uploadedChunks")
                        return@withContext true
                    } else {
                        Timber.tag("Epic").e("[Cloud Saves] Failed to upload manifest: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Timber.tag("Epic").e("[Cloud Saves] Missing write link for manifest: ${manifestEntry.key}")
                }

                false
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Upload failed")
                false
            }
        }

    private fun syncScope(
        appId: Int,
        targetContainerId: Int?,
    ): SyncScope = SyncScope(appId, targetContainerId?.takeIf { it > 0 })

    private suspend fun wasUploadRecentlyCompleted(syncScope: SyncScope): Boolean =
        syncMutex.withLock {
            wasUploadRecentlyCompletedLocked(syncScope)
        }

    private fun wasUploadRecentlyCompletedLocked(syncScope: SyncScope): Boolean {
        val now = System.currentTimeMillis()
        recentSuccessfulUploads.entries.removeAll { now - it.value > DUPLICATE_UPLOAD_SUPPRESSION_MS }
        val lastUploadAt = recentSuccessfulUploads[syncScope] ?: return false
        return now - lastUploadAt <= DUPLICATE_UPLOAD_SUPPRESSION_MS
    }

    private suspend fun markUploadCompleted(syncScope: SyncScope) {
        syncMutex.withLock {
            recentSuccessfulUploads[syncScope] = System.currentTimeMillis()
        }
    }

    // Request write links for files
    private suspend fun requestWriteLinks(
        context: Context,
        appName: String,
        fileNames: List<String>,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val credentialsResult = EpicAuthManager.getStoredCredentials(context)
                if (credentialsResult.isFailure) {
                    return@withContext emptyMap()
                }

                val credentials = credentialsResult.getOrNull()!!
                val accountId = credentials.accountId
                val accessToken = credentials.accessToken

                Timber.tag("Epic").d("[Cloud Saves] Requesting write links for ${fileNames.size} files")

                // Log the file names being requested
                fileNames.forEach { name ->
                    Timber.tag("Epic").d("[Cloud Saves] Requesting write link for: $name")
                }

                // Request write links for all files at once
                // API expects a BulkLinkRequest object with a "files" array property
                val requestJson =
                    JSONObject().apply {
                        put("files", JSONArray(fileNames))
                    }
                val requestBody = requestJson.toString()
                Timber.tag("Epic").d("[Cloud Saves] Request body: $requestBody")

                val request =
                    Request
                        .Builder()
                        .url("$baseCloudSyncUrl/api/v1/access/egstore/savesync/$accountId/$appName/")
                        .header("Authorization", "Bearer $accessToken")
                        .header("Content-Type", "application/json")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()

                val response = httpClient.newCall(request).execute()

                Timber.tag("Epic").d("[Cloud Saves] Response code: ${response.code}")

                val responseBody =
                    try {
                        response.body?.string() ?: ""
                    } catch (e: Exception) {
                        Timber.tag("Epic").e(e, "[Cloud Saves] Failed to read response body")
                        ""
                    }

                response.close()

                if (!response.isSuccessful) {
                    Timber.tag("Epic").e("[Cloud Saves] Failed to request write links: ${response.code}")
                    Timber.tag("Epic").e("[Cloud Saves] Response body: $responseBody")
                    return@withContext emptyMap()
                }

                try {
                    val json = JSONObject(responseBody.ifEmpty { "{}" })
                    val filesJson = json.optJSONObject("files") ?: JSONObject()

                    val writeLinks = mutableMapOf<String, String>()
                    filesJson.keys().forEach { key ->
                        val fileJson = filesJson.getJSONObject(key)
                        val writeLink = fileJson.optString("writeLink")
                        if (writeLink.isNotEmpty()) {
                            writeLinks[key] = writeLink
                        }
                    }

                    Timber.tag("Epic").i("[Cloud Saves] Received ${writeLinks.size} write links")
                    writeLinks
                } catch (e: Exception) {
                    Timber.tag("Epic").e(e, "[Cloud Saves] Failed to parse write links response")
                    Timber.tag("Epic").e("[Cloud Saves] Response was: $responseBody")
                    emptyMap()
                }
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Failed to request write links")
                emptyMap()
            }
        }

    // Upload a single file
    private suspend fun uploadFile(
        writeLink: String,
        data: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Don't set a Content-Type — these are presigned URLs and any explicit
                // value must match what was specified when the URL was signed. Legendary
                // sends raw bytes with no Content-Type and gets accepted; forcing
                // application/octet-stream here can produce 403 SignatureDoesNotMatch
                // when Epic's signing layer hashed a different (or empty) Content-Type.
                val request =
                    Request
                        .Builder()
                        .url(writeLink)
                        .put(data.toRequestBody(null))
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use {
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Upload failed: ${response.code}"))
                    }
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Timber.tag("Epic").e(e, "[Cloud Saves] Failed to upload file")
                Result.failure(e)
            }
        }

    // Package save files into chunks and manifest
    private fun packageSaveFiles(
        saveDir: File,
        game: EpicGame,
        accountId: String,
        fileList: List<String>? = null, // Optional: only package specific files
    ): Map<String, ByteArray> {
        try {
            Timber.tag("Epic").i("[Cloud Saves] Packaging files from: ${saveDir.absolutePath}")

            val includeFilters = parseCloudFilterList(game.cloudIncludeList)
            val excludeFilters = parseCloudFilterList(game.cloudExcludeList)

            val allFiles =
                saveDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .toList()
            Timber.tag("Epic").i("[Cloud Saves] Found ${allFiles.size} file(s) under resolved save root before filters")

            // Filter to only requested files if fileList is provided
            val filteredFiles =
                allFiles.filter { file ->
                        val relativePath = file.relativeTo(saveDir).path.replace("\\", "/")
                        if (fileList != null && !fileList.contains(relativePath)) {
                            return@filter false
                        }
                        if (includeFilters.isNotEmpty() && !filenameMatchesAny(relativePath, includeFilters)) {
                            Timber.tag("Epic").d("[Cloud Saves] Excluding $relativePath (does not match include filter)")
                            return@filter false
                        }
                        if (excludeFilters.isNotEmpty() && filenameMatchesAny(relativePath, excludeFilters)) {
                            Timber.tag("Epic").d("[Cloud Saves] Excluding $relativePath (matches exclude filter)")
                            return@filter false
                        }
                        Timber.tag("Epic").d("[Cloud Saves] Including file: $relativePath")
                        true
                    }.sortedBy { it.relativeTo(saveDir).path.lowercase() }

            val files =
                if (filteredFiles.isEmpty() && fileList == null && allFiles.isNotEmpty() && (includeFilters.isNotEmpty() || excludeFilters.isNotEmpty())) {
                    Timber
                        .tag("Epic")
                        .w("[Cloud Saves] Epic filters matched no files; falling back to all files under resolved save root")
                    allFiles.sortedBy { it.relativeTo(saveDir).path.lowercase() }
                } else {
                    filteredFiles
                }

            if (files.isEmpty()) {
                Timber.tag("Epic").w("[Cloud Saves] No files found to package")
                return emptyMap()
            }

            Timber.tag("Epic").i("[Cloud Saves] Found ${files.size} files to package")

            val packagedFiles = mutableMapOf<String, ByteArray>()
            val chunks = mutableListOf<com.winlator.cmod.feature.stores.epic.service.manifest.ChunkInfo>()
            val fileManifests = mutableListOf<com.winlator.cmod.feature.stores.epic.service.manifest.FileManifest>()

            var chunkNum = 0
            var currentChunkData = mutableListOf<Byte>()
            // currentChunkGuid tracks the single GUID shared by the current in-flight chunk's
            // ChunkParts AND the ChunkInfo/chunk-file header — all three must be identical.
            var currentChunkGuid = generateGuid()
            val chunkSize = 1024 * 1024 // 1 MB chunks

            // Process each file
            files.forEach { file ->
                try {
                    val relativePath = file.relativeTo(saveDir).path.replace("\\", "/")

                    // Skip empty files
                    if (file.length() == 0L) {
                        Timber.tag("Epic").w("[Cloud Saves] Skipping empty file: $relativePath")
                        return@forEach
                    }

                    Timber.tag("Epic").d("[Cloud Saves] Processing file: $relativePath (${file.length()} bytes)")

                    val fileManifest =
                        com.winlator.cmod.feature.stores.epic.service.manifest
                            .FileManifest()
                    fileManifest.filename = relativePath
                    fileManifest.fileSize = file.length()

                    val fileData = file.readBytes()
                    val fileHash =
                        java.security.MessageDigest
                            .getInstance("SHA-1")
                            .digest(fileData)
                    fileManifest.hash = fileHash

                    var fileOffset = 0L

                    // Split file into chunk parts
                    while (fileOffset < fileData.size) {
                        // Check if we need to finalize current chunk
                        if (currentChunkData.size >= chunkSize) {
                            val chunk = finalizeChunk(currentChunkData.toByteArray(), currentChunkGuid, chunkNum++, packagedFiles)
                            chunks.add(chunk)
                            currentChunkData.clear()
                            // Fresh GUID for the next chunk
                            currentChunkGuid = generateGuid()
                        }

                        val offset = currentChunkData.size
                        val partFileOffset = fileOffset

                        val remainingInChunk = (chunkSize - currentChunkData.size).coerceAtMost((fileData.size - fileOffset).toInt())
                        val size = remainingInChunk

                        // Add data to current chunk
                        currentChunkData.addAll(
                            fileData.sliceArray(fileOffset.toInt() until (fileOffset + remainingInChunk).toInt()).toList(),
                        )

                        // Create chunk part — uses the same GUID as the ChunkInfo that will be
                        // created by finalizeChunk() for this chunk buffer.
                        val chunkPart =
                            com.winlator.cmod.feature.stores.epic.service.manifest.ChunkPart(
                                guid = currentChunkGuid,
                                offset = offset,
                                size = size,
                                fileOffset = partFileOffset,
                            )

                        fileManifest.chunkParts.add(chunkPart)
                        fileOffset += remainingInChunk
                    }

                    fileManifests.add(fileManifest)
                } catch (e: Exception) {
                    Timber.tag("Epic").e(e, "[Cloud Saves] Failed to process file: ${file.name}")
                }
            }

            // Finalize last chunk if it has data
            if (currentChunkData.isNotEmpty()) {
                val chunk = finalizeChunk(currentChunkData.toByteArray(), currentChunkGuid, chunkNum++, packagedFiles)
                chunks.add(chunk)
            }

            // Build a single buildVersion string and reuse it for both the manifest
            // metadata and the upload filename — taking two independent snapshots of
            // LocalDateTime.now() allowed the clock to roll a second between them and
            // ship a manifest whose filename disagreed with its embedded build_version.
            val buildVersion =
                java.time.LocalDateTime
                    .now(java.time.ZoneOffset.UTC)
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("yyyy.MM.dd-HH.mm.ss"),
                    )

            // Create manifest
            val manifest = createManifest(game, accountId, chunks, fileManifests, buildVersion)
            val manifestData = manifest.serialize()

            val manifestName = "manifests/$buildVersion.manifest"
            packagedFiles[manifestName] = manifestData

            Timber.tag("Epic").i("[Cloud Saves] Packaged ${fileManifests.size} files into ${chunks.size} chunks")
            return packagedFiles
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "[Cloud Saves] Failed to package save files")
            return emptyMap()
        }
    }

    // Finalize a chunk (compress and store)
    // guid must be the same IntArray already assigned to all ChunkParts that reference this chunk,
    // so that ChunkPart.guidStr == ChunkInfo.guidStr == the GUID in the chunk file header.
    private fun finalizeChunk(
        data: ByteArray,
        guid: IntArray,
        chunkNum: Int,
        packagedFiles: MutableMap<String, ByteArray>,
    ): com.winlator.cmod.feature.stores.epic.service.manifest.ChunkInfo {
        // Pad to 1 MiB and compute hashes over the padded buffer — this is what Legendary does
        // (`chunk.py:65-67` `data` setter pads, then `chunk.hash`/`chunk.sha_hash` are computed
        // lazily over the padded `self.data`). `ChunkInfo.windowSize` and the chunk header's
        // `uncompressedSize` must report the padded length so any other Epic client that does
        // verify hashes sees a self-consistent chunk file.
        val paddedData =
            if (data.size < 1024 * 1024) {
                data + ByteArray(1024 * 1024 - data.size)
            } else {
                data
            }

        val shaHash =
            java.security.MessageDigest
                .getInstance("SHA-1")
                .digest(paddedData)
        val rollingHash = calculateRollingHash(paddedData)

        // Compute groupNum exactly as Legendary does:
        // group_num = crc32(struct.pack('<IIII', *guid)) & 0xffffffff) % 100
        val guidBytes = ByteArray(16)
        val guidBuf =
            java.nio.ByteBuffer
                .wrap(guidBytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        guid.forEach { guidBuf.putInt(it) }
        val crc32 = java.util.zip.CRC32()
        crc32.update(guidBytes)
        val groupNum = (crc32.value % 100).toInt()

        // Create chunk info with the caller-supplied GUID (same as the ChunkParts)
        val chunkInfo =
            com.winlator.cmod.feature.stores.epic.service.manifest
                .ChunkInfo()
        chunkInfo.guid = guid
        chunkInfo.hash = rollingHash
        chunkInfo.shaHash = shaHash
        chunkInfo.groupNum = groupNum
        chunkInfo.windowSize = paddedData.size

        // Compress chunk — pass guid so the header GUID matches the CDL entry
        val compressedData = compressChunk(paddedData, guid, rollingHash, shaHash)
        chunkInfo.fileSize = compressedData.size.toLong()

        // Store chunk data under its canonical path
        val chunkPath = chunkInfo.getPath()
        packagedFiles[chunkPath] = compressedData

        Timber
            .tag(
                "Epic",
            ).d("[Cloud Saves] Finalized chunk #$chunkNum: ${chunkInfo.guidStr} groupNum=$groupNum (${compressedData.size} bytes)")

        return chunkInfo
    }

    // Compress chunk data with the Epic binary chunk header.
    // 66-byte header:
    //   magic(4) + version(4) + headerSize(4) + compressedSize(4)
    //   + guid(16) + hash(8) + storedAs(1)
    //   + shaHash(20) + hashType(1) + uncompressedSize(4)   ← header version 2+3 fields
    //   = 66 bytes
    // guid/rollingHash/shaHash must already be computed by the caller (finalizeChunk) so that
    // the values written into the header are identical to what is stored in the CDL entry.
    internal fun compressChunk(
        data: ByteArray,
        guid: IntArray,
        rollingHash: ULong,
        shaHash: ByteArray,
    ): ByteArray {
        // Compress payload
        val compressed = java.io.ByteArrayOutputStream()
        java.util.zip
            .DeflaterOutputStream(compressed)
            .use { it.write(data) }
        val compressedData = compressed.toByteArray()

        // hardcode headersize to 66 as required
        val headerSize = 66
        val buffer =
            java.nio.ByteBuffer
                .allocate(headerSize + compressedData.size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(0xB1FE3AA2.toInt()) // magic
        buffer.putInt(3) // header_version (we always write v3)
        buffer.putInt(headerSize) // header_size = 66 (hardcoded, not back-filled)
        buffer.putInt(compressedData.size) // compressed_size

        // GUID — must match ChunkInfo.guid and every ChunkPart.guid referencing this chunk
        guid.forEach { buffer.putInt(it) }

        // Rolling hash — must match ChunkInfo.hash
        buffer.putLong(rollingHash.toLong())

        buffer.put(0x1.toByte()) // stored_as = compressed
        // Header version 2: sha_hash + hash_type
        buffer.put(shaHash) // SHA-1 of uncompressed data (20 bytes)
        buffer.put(0x3.toByte()) // hash_type = 0x3 (both rolling + sha)
        // Header version 3: uncompressed_size
        buffer.putInt(data.size) // uncompressed_size (1 MiB for full chunks)

        buffer.put(compressedData)

        return buffer.array().copyOf(buffer.position())
    }

    // Generate a random GUID (4 integers)
    private fun generateGuid(): IntArray {
        val random = java.security.SecureRandom()
        return IntArray(4) { random.nextInt() }
    }

    private fun parseCloudFilterList(raw: String): List<String> =
        raw
            .split(",")
            .map { it.trim().replace("\\", "/") }
            .filter { it.isNotEmpty() }

    private fun filenameMatchesAny(
        filename: String,
        patterns: List<String>,
    ): Boolean = patterns.any { pattern -> filenameMatchesPattern(filename, pattern) }

    private fun filenameMatchesPattern(
        filename: String,
        pattern: String,
    ): Boolean {
        if (pattern.endsWith("/") && filename.startsWith(pattern)) return true
        if (filename.endsWith(pattern)) return true
        if (!pattern.contains("*") && !pattern.contains("?")) return false

        val regex =
            buildString {
                append("^")
                pattern.forEach { ch ->
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append(".")
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
                append("$")
            }.toRegex()
        return regex.matches(filename)
    }

    /**
     * CRC-64-ECMA variant lookup table
     * Polynomial: 0xC96C5795D7870F42
     * Table built identically to Legendary's _init():
     *   for i in 0..255:
     *     for _ in 0..7: if i&1 -> i = (i>>1) ^ poly  else i >>= 1
     */
    private val ROLLING_HASH_TABLE: LongArray =
        run {
            val poly = 0xC96C5795D7870F42uL
            LongArray(256) { seed ->
                var v = seed.toULong()
                repeat(8) {
                    v = if ((v and 1uL) != 0uL) (v shr 1) xor poly else v shr 1
                }
                v.toLong()
            }
        }

    /**
     * Epic Games rolling hash — exact port of Legendary's get_hash() in rolling_hash.py:
     *   h = 0
     *   for each byte i: h = ((h << 1 | h >> 63) ^ table[data[i]]) & 0xffffffffffffffff
     */
    internal fun calculateRollingHash(data: ByteArray): ULong {
        var h = 0uL
        for (byte in data) {
            val tableVal = ROLLING_HASH_TABLE[byte.toInt() and 0xFF].toULong()
            h = ((h shl 1) or (h shr 63)) xor tableVal
        }
        return h
    }

    // Create manifest
    private fun createManifest(
        game: EpicGame,
        accountId: String,
        chunks: List<com.winlator.cmod.feature.stores.epic.service.manifest.ChunkInfo>,
        fileManifests: List<com.winlator.cmod.feature.stores.epic.service.manifest.FileManifest>,
        buildVersion: String,
    ): com.winlator.cmod.feature.stores.epic.service.manifest.EpicManifest {
        val manifest =
            com.winlator.cmod.feature.stores.epic.service.manifest
                .BinaryManifest()

        // Meta
        manifest.meta =
            com.winlator.cmod.feature.stores.epic.service.manifest
                .ManifestMeta()
        manifest.meta!!.appName = "${game.appName}$accountId"
        manifest.meta!!.buildVersion = buildVersion

        // Custom fields
        manifest.customFields =
            com.winlator.cmod.feature.stores.epic.service.manifest
                .CustomFields()
        manifest.customFields!!["CloudSaveFolder"] = game.saveFolder

        // Chunks
        manifest.chunkDataList =
            com.winlator.cmod.feature.stores.epic.service.manifest
                .ChunkDataList()
        manifest.chunkDataList!!.elements.addAll(chunks)

        // Files
        manifest.fileManifestList =
            com.winlator.cmod.feature.stores.epic.service.manifest
                .FileManifestList()
        manifest.fileManifestList!!.elements.addAll(fileManifests)

        return manifest
    }

    // Resolve save directory path
    private fun resolveSaveDirectory(
        context: Context,
        game: EpicGame,
        accountId: String,
        targetContainerId: Int? = null,
    ): File? {
        val cloudSaveFolder = game.saveFolder.ifEmpty { return null }

        val container = resolveEpicContainer(context, game.id, targetContainerId) ?: return null
        val winePrefix = File(container.rootDir, ".wine").absolutePath
        val user = "xuser"

        Timber.tag("Epic").d("[Cloud Saves] Using Wine prefix: $winePrefix (container ${container.id})")

        // Resolve path variables used by Epic Games (case-insensitive)
        val pathVars =
            mutableMapOf<String, String>(
                "{epicid}" to accountId,
                "{installdir}" to (game.installPath.ifEmpty { EpicConstants.getGameInstallPath(context, game.appName) }),
                "{appname}" to game.appName,
            )

        // Map to Wine prefix paths (like GOG does)
        // Check for both proper casing (AppData) and legacy lowercase (appdata)
        val usersPath = File(winePrefix, "drive_c/users/$user")
        val appDataDir =
            when {
                File(usersPath, "AppData").exists() -> "AppData"
                File(usersPath, "appdata").exists() -> "appdata"
                File(usersPath, "appData").exists() -> "appData"
                else -> "AppData" // Default to proper Windows casing
            }

        Timber.tag("Epic").d("[Cloud Saves] Using AppData directory name: $appDataDir")

        val localAppDataPath = File(winePrefix, "drive_c/users/$user/$appDataDir/Local").absolutePath
        val localLowAppDataPath = File(winePrefix, "drive_c/users/$user/$appDataDir/LocalLow").absolutePath
        val roamingAppDataPath = File(winePrefix, "drive_c/users/$user/$appDataDir/Roaming").absolutePath
        val documentsPath = File(winePrefix, "drive_c/users/$user/Documents").absolutePath
        val savedGamesPath = File(winePrefix, "drive_c/users/$user/Saved Games").absolutePath
        val userProfilePath = File(winePrefix, "drive_c/users/$user").absolutePath

        // Counter-intuitive but matches the canonical Legendary mapping at `core.py:892`
        // and `core.py:961` (`'{appdata}': '%LOCALAPPDATA%'` and `wine_folders['Local AppData']`).
        // Epic's catalog templates use `{appdata}` to mean the Local AppData directory, not
        // Windows's `%APPDATA%` (Roaming) which the name might suggest.
        pathVars["{appdata}"] = localAppDataPath
        pathVars["{localappdata}"] = localAppDataPath
        pathVars["{locallow}"] = localLowAppDataPath
        pathVars["{roamingappdata}"] = roamingAppDataPath
        pathVars["{roaming}"] = roamingAppDataPath
        pathVars["{userdir}"] = documentsPath
        pathVars["{usersavedgames}"] = savedGamesPath
        pathVars["{userprofile}"] = userProfilePath
        pathVars["%localappdata%"] = localAppDataPath
        pathVars["%appdata%"] = roamingAppDataPath
        pathVars["%userprofile%"] = userProfilePath
        pathVars["%homepath%"] = userProfilePath

        // Normalize path separators first
        var resolvedPath = cloudSaveFolder.replace("\\", "/")

        val legacyPathLower = resolvedPath.lowercase()
        resolvedPath =
            when {
                "locallow/" in legacyPathLower -> {
                    val suffix = resolvedPath.substring(legacyPathLower.indexOf("locallow/") + "locallow/".length)
                    "{locallow}/$suffix"
                }
                "roaming/" in legacyPathLower -> {
                    val suffix = resolvedPath.substring(legacyPathLower.indexOf("roaming/") + "roaming/".length)
                    "{roaming}/$suffix"
                }
                else -> resolvedPath
            }

        Timber.tag("Epic").d("[Cloud Saves] Before variable replacement: $resolvedPath")

        // Replace variables (case-insensitive)
        pathVars.forEach { (key, value) ->
            val before = resolvedPath
            resolvedPath = resolvedPath.replace(key, value, ignoreCase = true)
            if (before != resolvedPath) {
                Timber.tag("Epic").d("[Cloud Saves] Replaced $key with $value")
            }
        }

        Timber.tag("Epic").d("[Cloud Saves] After variable replacement: $resolvedPath")

        val windowsUserPathPattern = Regex("^[A-Za-z]:/Users/[^/]+/", RegexOption.IGNORE_CASE)
        if (windowsUserPathPattern.containsMatchIn(resolvedPath)) {
            resolvedPath = resolvedPath.replace(windowsUserPathPattern, userProfilePath.replace("\\", "/") + "/")
            Timber.tag("Epic").d("[Cloud Saves] Rebased Windows user path to Wine prefix: $resolvedPath")
        }

        // Manually resolve ../ and ./ in the path (don't use canonicalPath as it can fail/change paths)
        val pathParts = resolvedPath.split("/").toMutableList()
        val normalizedParts = mutableListOf<String>()

        for (part in pathParts) {
            when {
                part == ".." && normalizedParts.isNotEmpty() && normalizedParts.last() != ".." -> {
                    // Go up one directory
                    normalizedParts.removeAt(normalizedParts.lastIndex)
                }

                part != "." && part.isNotEmpty() -> {
                    // Add non-empty, non-current-dir parts
                    normalizedParts.add(part)
                }
                // Skip "." and empty parts
            }
        }

        val finalPath = resolveExistingPathCaseInsensitive(File(normalizedParts.joinToString("/")))

        Timber.tag("Epic").d("[Cloud Saves] Scanning path: ${finalPath.absolutePath}")
        Timber.tag("Epic").d("[Cloud Saves] Path exists: ${finalPath.exists()}")

        if (finalPath.exists()) {
            val allContents = finalPath.listFiles() ?: emptyArray()
            Timber.tag("Epic").d("[Cloud Saves] Total items in path: ${allContents.size}")

            allContents.forEach { item ->
                if (item.isDirectory) {
                    val filesInSubdir = item.walkTopDown().count { it.isFile }
                    Timber.tag("Epic").d("[Cloud Saves]   DIR: ${item.name}/ ($filesInSubdir files recursively)")
                } else {
                    Timber.tag("Epic").d("[Cloud Saves]   FILE: ${item.name} (${item.length()} bytes)")
                }
            }
        } else {
            Timber.tag("Epic").w("[Cloud Saves] Path does not exist!")
        }

        Timber.tag("Epic").d("[Cloud Saves] Path resolution:")
        Timber.tag("Epic").d("[Cloud Saves]   Original: $cloudSaveFolder")
        Timber.tag("Epic").d("[Cloud Saves]   Resolved: ${finalPath.absolutePath}")

        return finalPath
    }

    private fun resolveEpicContainer(
        context: Context,
        appId: Int,
        targetContainerId: Int? = null,
    ) = runCatching {
        val containerManager = com.winlator.cmod.runtime.container.ContainerManager(context)
        val explicitContainerId = targetContainerId?.takeIf { it > 0 }
        if (explicitContainerId != null) {
            val explicitContainer = containerManager.getContainerById(explicitContainerId)
            if (explicitContainer == null) {
                Timber.tag("Epic").w("[Cloud Saves] Explicit container $explicitContainerId not found for Epic appId=$appId")
                return@runCatching null
            }
            if (!com.winlator.cmod.feature.setup.SetupWizardActivity.isContainerUsable(context, explicitContainer)) {
                Timber.tag("Epic").w("[Cloud Saves] Explicit container $explicitContainerId is not usable for Epic appId=$appId")
                return@runCatching null
            }
            return@runCatching explicitContainer
        }

        // Prefer a real Epic shortcut, but only fall back to the default preferred
        // container when no explicit launch container was supplied.
        containerManager
            .loadShortcuts()
            .firstOrNull {
                it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == appId.toString()
            }?.container
            ?.takeIf {
                com.winlator.cmod.feature.setup.SetupWizardActivity.isContainerUsable(context, it)
            }
            ?: com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
                .getUsableContainerOrNull(context, "EPIC_$appId")
    }.onFailure {
        Timber.tag("Epic").w(it, "[Cloud Saves] Failed to resolve shortcut container for Epic appId=$appId")
    }.getOrNull()

    private fun resolveExistingPathCaseInsensitive(path: File): File {
        if (path.exists()) return path

        val absolute = path.absoluteFile
        val parts = absolute.path.split(File.separatorChar, '/', '\\').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return path

        var current =
            if (absolute.path.startsWith(File.separator)) {
                File(File.separator)
            } else {
                File(parts.first()).also {
                    if (it.exists()) return@also
                }
            }
        val startIndex = if (absolute.path.startsWith(File.separator)) 0 else 1

        for (index in startIndex until parts.size) {
            val part = parts[index]
            val direct = File(current, part)
            current =
                if (direct.exists()) {
                    direct
                } else {
                    current.listFiles()?.firstOrNull { it.name.equals(part, ignoreCase = true) }
                        ?: direct
                }
        }

        return current
    }

    private fun getSyncTimestamp(
        context: Context,
        appId: Int,
    ): String? {
        val prefs = context.getSharedPreferences("epic_cloud_saves", Context.MODE_PRIVATE)
        return prefs.getString("sync_timestamp_$appId", null)
    }

    private fun setSyncTimestamp(
        context: Context,
        appId: Int,
        timestamp: String,
    ) {
        val prefs = context.getSharedPreferences("epic_cloud_saves", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_timestamp_$appId", timestamp).apply()
    }

    /**
     * Decompress data if it's GZIP compressed, otherwise return as-is
     */
    private fun decompressIfNeeded(data: ByteArray): ByteArray =
        try {
            // Check for GZIP magic bytes (0x1f 0x8b)
            if (data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
                java.io.ByteArrayInputStream(data).use { inputStream ->
                    GZIPInputStream(inputStream).use { gzipStream ->
                        gzipStream.readBytes()
                    }
                }
            } else {
                data
            }
        } catch (e: Exception) {
            Timber.tag("Epic").w(e, "[Cloud Saves] Failed to decompress, using raw data")
            data
        }

    /**
     * Decompress a binary chunk file — matches Legendary's Chunk.read() + Chunk.data property.
     *
     * Header layout (little-endian):
     *   magic(4) + headerVersion(4) + headerSize(4) + compressedSize(4)
     *   + guid(16) + hash(8) + storedAs(1)
     *   [v2+] + shaHash(20) + hashType(1)
     *   [v3+] + uncompressedSize(4)
     *   Total for v3 = 66 bytes
     *
     * The payload starts at offset headerSize (not computed — read from the header).
     */
    internal fun decompressChunk(chunkBytes: ByteArray): ByteArray {
        return try {
            val buffer =
                java.nio.ByteBuffer
                    .wrap(chunkBytes)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)

            val magic = buffer.int
            if (magic != 0xB1FE3AA2.toInt()) {
                Timber.tag("Epic").w("[Cloud Saves] Invalid chunk magic: ${"%08X".format(magic)}, trying direct decompress")
                return decompressIfNeeded(chunkBytes)
            }

            val headerVersion = buffer.int
            val headerSize = buffer.int // payload starts at this offset from file start
            val compressedSize = buffer.int

            // guid(16) + hash(8)
            buffer.position(buffer.position() + 24)

            val storedAs = buffer.get().toInt()
            val isCompressed = (storedAs and 0x1) != 0

            // v2: shaHash(20) + hashType(1) = 21 bytes
            if (headerVersion >= 2) buffer.position(buffer.position() + 21)
            // v3: uncompressedSize(4)
            if (headerVersion >= 3) buffer.position(buffer.position() + 4)

            // Seek to headerSize regardless, in case of unknown future header fields
            buffer.position(headerSize)

            val data = ByteArray(chunkBytes.size - headerSize)
            buffer.get(data)

            if (isCompressed) {
                try {
                    java.io.ByteArrayInputStream(data).use { inputStream ->
                        java.util.zip
                            .InflaterInputStream(inputStream)
                            .use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    Timber.tag("Epic").w(e, "[Cloud Saves] Failed to inflate chunk, returning raw payload")
                    data
                }
            } else {
                data
            }
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "[Cloud Saves] Failed to parse chunk header, trying direct decompress")
            decompressIfNeeded(chunkBytes)
        }
    }
}
