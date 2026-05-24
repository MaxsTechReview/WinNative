package com.winlator.cmod.feature.stores.gog.service
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GOGCloudSavesManager(
    private val context: Context,
) {
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    companion object {
        private const val CLOUD_STORAGE_BASE_URL = "https://cloudstorage.gog.com"
        private const val USER_AGENT =
            "GOGGalaxyCommunicationService/2.0.13.27 (Windows_32bit) dont_sync_marker/true installation_source/gog"
        private const val DELETION_MD5 = "aadd86936a80ee8a369579c3926f1b3c"
        private val GOG_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
    }

    enum class SyncAction {
        UPLOAD,
        DOWNLOAD,
        CONFLICT,
        NONE,
    }

    data class SyncFile(
        val relativePath: String,
        val absolutePath: String,
        var md5Hash: String? = null,
        var updateTime: String? = null,
        var updateTimestamp: Long? = null,
    ) {
        suspend fun calculateMetadata() =
            withContext(Dispatchers.IO) {
                try {
                    val file = File(absolutePath)
                    if (!file.exists() || !file.isFile) {
                        Timber.w("File does not exist: $absolutePath")
                        return@withContext
                    }

                    val timestamp = file.lastModified()
                    updateTimestamp = timestamp / 1000
                    updateTime =
                        Instant
                            .ofEpochSecond(updateTimestamp ?: 0L)
                            .atOffset(ZoneOffset.UTC)
                            .format(GOG_TIMESTAMP_FORMATTER)

                    // Hash matches the Etag GOG stores: md5 of gzip(raw, level=6, mtime=0).
                    val gzipped = gzipWithZeroMtime(file.readBytes())
                    md5Hash = MessageDigest.getInstance("MD5")
                        .digest(gzipped)
                        .joinToString("") { "%02x".format(it) }

                    Timber.d("Calculated metadata for $relativePath: md5=$md5Hash, timestamp=$updateTimestamp")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to calculate metadata for $absolutePath")
                }
            }
    }

    data class CloudFile(
        val relativePath: String,
        val md5Hash: String,
        val updateTime: String?,
        val updateTimestamp: Long?,
    ) {
        val isDeleted: Boolean
            get() = md5Hash == DELETION_MD5
    }

    private data class DownloadedObject(
        val bytes: ByteArray,
        val localLastModifiedMs: Long?,
    )

    data class SyncClassifier(
        val updatedLocal: List<SyncFile> = emptyList(),
        val updatedCloud: List<CloudFile> = emptyList(),
        val notExistingLocally: List<CloudFile> = emptyList(),
        val notExistingRemotely: List<SyncFile> = emptyList(),
    ) {
        fun determineAction(): SyncAction {
            val hasLocalChanges = updatedLocal.isNotEmpty() || notExistingRemotely.isNotEmpty()
            val hasCloudChanges = updatedCloud.isNotEmpty() || notExistingLocally.isNotEmpty()
            return when {
                !hasLocalChanges && hasCloudChanges -> SyncAction.DOWNLOAD
                hasLocalChanges && !hasCloudChanges -> SyncAction.UPLOAD
                !hasLocalChanges && !hasCloudChanges -> SyncAction.NONE
                else -> SyncAction.CONFLICT
            }
        }
    }

    suspend fun listCloudSaveFiles(
        dirname: String,
        clientId: String,
        clientSecret: String,
    ): List<CloudFile> =
        withContext(Dispatchers.IO) {
            if (clientSecret.isEmpty()) {
                Timber.tag("GOG-CloudSaves").w("Cannot list cloud files for '$dirname': missing clientSecret")
                return@withContext emptyList()
            }
            val credentials =
                GOGAuthManager.getGameCredentials(context, clientId, clientSecret).getOrNull() ?: run {
                    Timber.tag("GOG-CloudSaves").e("Failed to get game-specific credentials for cloud listing")
                    return@withContext emptyList()
                }
            getCloudFiles(credentials.userId, clientId, dirname, credentials.accessToken)
                .filter { !it.isDeleted }
        }

    suspend fun addCloudSaveFilesToZip(
        zip: ZipOutputStream,
        zipPrefix: String,
        dirname: String,
        clientId: String,
        clientSecret: String,
    ): Int =
        withContext(Dispatchers.IO) {
            if (clientSecret.isEmpty()) {
                Timber.tag("GOG-CloudSaves").w("Cannot export cloud files for '$dirname': missing clientSecret")
                return@withContext 0
            }
            val credentials =
                GOGAuthManager.getGameCredentials(context, clientId, clientSecret).getOrNull() ?: run {
                    Timber.tag("GOG-CloudSaves").e("Failed to get game-specific credentials for cloud export")
                    return@withContext 0
                }
            val cloudFiles = getCloudFiles(credentials.userId, clientId, dirname, credentials.accessToken).filter { !it.isDeleted }
            var exported = 0
            for (file in cloudFiles) {
                val downloaded = downloadObject(credentials.userId, clientId, dirname, file, credentials.accessToken) ?: continue
                val entryName = safeZipEntryName(zipPrefix, file.relativePath) ?: continue
                val entry =
                    ZipEntry(entryName).apply {
                        time = downloaded.localLastModifiedMs ?: file.updateTimestamp?.times(1000L) ?: System.currentTimeMillis()
                    }
                zip.putNextEntry(entry)
                zip.write(downloaded.bytes)
                zip.closeEntry()
                exported++
            }
            exported
        }

    suspend fun needsSync(
        localPath: String,
        dirname: String,
        clientId: String,
        clientSecret: String,
        lastSyncTimestamp: Long = 0,
    ): Boolean = determineSyncAction(localPath, dirname, clientId, clientSecret, lastSyncTimestamp) != SyncAction.NONE

    suspend fun determineSyncAction(
        localPath: String,
        dirname: String,
        clientId: String,
        clientSecret: String,
        lastSyncTimestamp: Long = 0,
        preferredAction: String = "auto",
    ): SyncAction =
        withContext(Dispatchers.IO) {
            if (clientSecret.isEmpty()) {
                Timber.tag("GOG-CloudSaves").w("Cannot probe cloud sync for '$dirname': missing clientSecret")
                return@withContext SyncAction.NONE
            }

            val syncDir = File(localPath)
            val localFiles = if (syncDir.exists()) scanLocalFiles(syncDir) else emptyList()
            val credentials =
                GOGAuthManager.getGameCredentials(context, clientId, clientSecret).getOrNull() ?: run {
                    Timber.tag("GOG-CloudSaves").e("Failed to get game-specific credentials for sync probe")
                    return@withContext SyncAction.NONE
                }
            val cloudFiles = getCloudFiles(credentials.userId, clientId, dirname, credentials.accessToken)
            val downloadableCloud = cloudFiles.filter { !it.isDeleted }

            if (preferredAction.equals("exit_upload", ignoreCase = true)) {
                if (localFiles.isEmpty()) return@withContext SyncAction.NONE
                if (cloudFiles.isEmpty()) return@withContext SyncAction.UPLOAD
                val cloudByPath = cloudFiles.associateBy { it.relativePath }
                val localPaths = localFiles.map { it.relativePath }.toSet()
                val staleCloudFilesMissingLocally =
                    cloudFiles.filter {
                        !it.isDeleted &&
                            it.relativePath !in localPaths &&
                            (it.updateTimestamp ?: Long.MAX_VALUE) <= lastSyncTimestamp
                    }
                val anyLocalNewer =
                        localFiles.any { local ->
                            val cloud = cloudByPath[local.relativePath]
                            val localTimestamp = local.updateTimestamp ?: 0L
                            when {
                                cloud == null -> localTimestamp > lastSyncTimestamp
                                cloud.isDeleted -> localTimestamp > (cloud.updateTimestamp ?: lastSyncTimestamp)
                                else -> localTimestamp > (cloud.updateTimestamp ?: 0L)
                            }
                        }
                return@withContext if (anyLocalNewer || staleCloudFilesMissingLocally.isNotEmpty()) {
                    SyncAction.UPLOAD
                } else {
                    SyncAction.NONE
                }
            }

            when {
                localFiles.isNotEmpty() && cloudFiles.isEmpty() -> SyncAction.UPLOAD
                localFiles.isEmpty() && downloadableCloud.isNotEmpty() -> SyncAction.DOWNLOAD
                localFiles.isEmpty() && cloudFiles.isEmpty() -> SyncAction.NONE
                else -> classifyFiles(localFiles, cloudFiles, lastSyncTimestamp).determineAction()
            }
        }

    suspend fun syncSaves(
        localPath: String,
        dirname: String,
        clientId: String,
        clientSecret: String,
        lastSyncTimestamp: Long = 0,
        preferredAction: String = "none",
    ): Long =
        withContext(Dispatchers.IO) {
            try {
                Timber.tag("GOG-CloudSaves").i("Starting sync for path: $localPath")
                Timber.tag("GOG-CloudSaves").i("Cloud dirname: $dirname")
                Timber.tag("GOG-CloudSaves").i("Cloud client ID: $clientId")
                Timber.tag("GOG-CloudSaves").i("Last sync timestamp: $lastSyncTimestamp")
                Timber.tag("GOG-CloudSaves").i("Preferred action: $preferredAction")

                val syncDir = File(localPath)
                if (!syncDir.exists()) {
                    Timber.tag("GOG-CloudSaves").i("Creating sync directory: $localPath")
                    syncDir.mkdirs()
                }

                val localFiles = scanLocalFiles(syncDir)
                Timber.tag("GOG-CloudSaves").i("Found ${localFiles.size} local file(s)")

                val credentials =
                    GOGAuthManager.getGameCredentials(context, clientId, clientSecret).getOrNull() ?: run {
                        Timber.tag("GOG-CloudSaves").e("Failed to get game-specific credentials")
                        return@withContext 0L
                    }
                Timber.tag("GOG-CloudSaves").d(
                    "Using game-specific credentials for userId: ${credentials.userId}, clientId: $clientId",
                )

                Timber.tag("GOG").d("[Cloud Saves] Fetching cloud file list for dirname: $dirname")
                val cloudFiles = getCloudFiles(credentials.userId, clientId, dirname, credentials.accessToken)
                Timber.tag("GOG").d("[Cloud Saves] Retrieved ${cloudFiles.size} total cloud files")
                val downloadableCloud = cloudFiles.filter { !it.isDeleted }
                Timber.tag("GOG").i(
                    "[Cloud Saves] Found ${downloadableCloud.size} downloadable cloud file(s) (excluding deleted)",
                )
                if (downloadableCloud.isNotEmpty()) {
                    downloadableCloud.forEach { file ->
                        Timber.tag("GOG").d(
                            "[Cloud Saves]   - Cloud file: ${file.relativePath} (md5: ${file.md5Hash}, modified: ${file.updateTime})",
                        )
                    }
                }

                // Exit-time upload: never download — must not overwrite a newer cloud save
                // made on another device with stale local data from this session.
                if (preferredAction.equals("exit_upload", ignoreCase = true)) {
                    if (localFiles.isEmpty()) {
                        Timber.tag("GOG-CloudSaves").i("[exit_upload] No local files for '$dirname'; skipping")
                        return@withContext currentTimestamp()
                    }
                    if (cloudFiles.isEmpty()) {
                        Timber.tag("GOG-CloudSaves").i(
                            "[exit_upload] Cloud empty for '$dirname'; uploading ${localFiles.size} file(s)",
                        )
                        localFiles.forEach { file ->
                            uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                        }
                        return@withContext currentTimestamp()
                    }
                    val cloudByPath = cloudFiles.associateBy { it.relativePath }
                    val localPaths = localFiles.map { it.relativePath }.toSet()
                    val toUpload =
                        localFiles.filter { local ->
                            val cloud = cloudByPath[local.relativePath]
                            val localTimestamp = local.updateTimestamp ?: 0L
                            when {
                                cloud == null -> localTimestamp > lastSyncTimestamp
                                cloud.isDeleted -> localTimestamp > (cloud.updateTimestamp ?: lastSyncTimestamp)
                                else -> localTimestamp > (cloud.updateTimestamp ?: 0L)
                            }
                        }
                    val toDelete =
                        cloudFiles.filter {
                            !it.isDeleted &&
                                it.relativePath !in localPaths &&
                                (it.updateTimestamp ?: Long.MAX_VALUE) <= lastSyncTimestamp
                        }
                    if (toUpload.isEmpty() && toDelete.isEmpty()) {
                        Timber.tag("GOG-CloudSaves").i(
                            "[exit_upload] Nothing local is newer; preserving last sync timestamp so newer cloud data is still detected",
                        )
                        return@withContext lastSyncTimestamp.coerceAtLeast(1L)
                    }
                    Timber.tag("GOG-CloudSaves").i(
                        "[exit_upload] Uploading ${toUpload.size} newer/new local file(s) and deleting ${toDelete.size} stale cloud file(s) for '$dirname'",
                    )
                    toUpload.forEach { file ->
                        uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                    }
                    toDelete.forEach { file ->
                        deleteCloudFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                    }
                    return@withContext currentTimestamp()
                }

                when {
                    localFiles.isNotEmpty() && cloudFiles.isEmpty() -> {
                        Timber.tag("GOG-CloudSaves").i("No files in cloud, uploading ${localFiles.size} file(s)")
                        localFiles.forEach { file ->
                            uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                        }
                        return@withContext currentTimestamp()
                    }

                    localFiles.isEmpty() && downloadableCloud.isNotEmpty() -> {
                        Timber.tag("GOG-CloudSaves").i("No files locally, downloading ${downloadableCloud.size} file(s)")
                        downloadableCloud.forEach { file ->
                            downloadFile(credentials.userId, clientId, dirname, file, syncDir, credentials.accessToken)
                        }
                        return@withContext currentTimestamp()
                    }

                    localFiles.isEmpty() && cloudFiles.isEmpty() -> {
                        Timber.tag("GOG-CloudSaves").i("No files locally or in cloud, nothing to sync")
                        return@withContext currentTimestamp()
                    }
                }

                if (preferredAction == "download" && downloadableCloud.isNotEmpty()) {
                    Timber.tag("GOG-CloudSaves").i(
                        "Forcing download of ${downloadableCloud.size} file(s) (user requested)",
                    )
                    downloadableCloud.forEach { file ->
                        downloadFile(credentials.userId, clientId, dirname, file, syncDir, credentials.accessToken)
                    }
                    deleteLocalFilesMissingFromCloud(localFiles, downloadableCloud)
                    pruneEmptyDirectories(syncDir)
                    return@withContext currentTimestamp()
                }

                if (preferredAction == "upload" && localFiles.isNotEmpty()) {
                    Timber.tag("GOG-CloudSaves").i("Forcing upload of ${localFiles.size} file(s) (user requested)")
                    localFiles.forEach { file ->
                        uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                    }
                    cloudFiles
                        .filter { !it.isDeleted && localFiles.none { local -> local.relativePath == it.relativePath } }
                        .forEach { file -> deleteCloudFile(credentials.userId, clientId, dirname, file, credentials.accessToken) }
                    return@withContext currentTimestamp()
                }

                val classifier = classifyFiles(localFiles, cloudFiles, lastSyncTimestamp)
                when (classifier.determineAction()) {
                    SyncAction.DOWNLOAD -> {
                        Timber.tag("GOG-CloudSaves").i(
                            "Downloading ${classifier.updatedCloud.size} updated cloud file(s)",
                        )
                        classifier.updatedCloud.forEach { file ->
                            if (!file.isDeleted) {
                                downloadFile(credentials.userId, clientId, dirname, file, syncDir, credentials.accessToken)
                            }
                        }
                        classifier.notExistingLocally.forEach { file ->
                            if (!file.isDeleted) {
                                downloadFile(credentials.userId, clientId, dirname, file, syncDir, credentials.accessToken)
                            }
                        }
                        deleteLocalFilesMissingFromCloud(classifier.notExistingRemotely, downloadableCloud)
                        pruneEmptyDirectories(syncDir)
                    }

                    SyncAction.UPLOAD -> {
                        Timber.tag("GOG-CloudSaves").i(
                            "Uploading ${classifier.updatedLocal.size} updated local file(s)",
                        )
                        classifier.updatedLocal.forEach { file ->
                            uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                        }
                        classifier.notExistingRemotely.forEach { file ->
                            uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                        }
                        classifier.notExistingLocally.forEach { file ->
                            if (!file.isDeleted) {
                                deleteCloudFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                            }
                        }
                    }

                    SyncAction.CONFLICT -> {
                        Timber.tag("GOG-CloudSaves").w("Sync conflict detected - comparing timestamps")

                        val localMap = classifier.updatedLocal.associateBy { it.relativePath }
                        val cloudMap = classifier.updatedCloud.associateBy { it.relativePath }

                        val toUpload = mutableListOf<SyncFile>()
                        val toDownload = mutableListOf<CloudFile>()

                        val commonPaths = localMap.keys.intersect(cloudMap.keys)
                        commonPaths.forEach { path ->
                            val localFile = localMap[path]!!
                            val cloudFile = cloudMap[path]!!

                            val localTime = localFile.updateTimestamp ?: 0L
                            val cloudTime = cloudFile.updateTimestamp ?: 0L

                            when {
                                localTime > cloudTime -> {
                                    Timber.tag("GOG-CloudSaves").i(
                                        "Local file is newer: $path (local: $localTime > cloud: $cloudTime)",
                                    )
                                    toUpload.add(localFile)
                                }

                                cloudTime > localTime -> {
                                    Timber.tag("GOG-CloudSaves").i(
                                        "Cloud file is newer: $path (cloud: $cloudTime > local: $localTime)",
                                    )
                                    toDownload.add(cloudFile)
                                }

                                else -> {
                                    Timber.tag("GOG-CloudSaves").w("Files have same timestamp, skipping: $path")
                                }
                            }
                        }

                        (localMap.keys - commonPaths).forEach { path ->
                            toUpload.add(localMap[path]!!)
                        }

                        (cloudMap.keys - commonPaths).forEach { path ->
                            toDownload.add(cloudMap[path]!!)
                        }

                        toUpload.addAll(classifier.notExistingRemotely)
                        toDownload.addAll(classifier.notExistingLocally.filter { !it.isDeleted })

                        if (toUpload.isNotEmpty()) {
                            Timber.tag("GOG-CloudSaves").i(
                                "Uploading ${toUpload.size} file(s) based on timestamp comparison",
                            )
                            toUpload.forEach { file ->
                                uploadFile(credentials.userId, clientId, dirname, file, credentials.accessToken)
                            }
                        }

                        if (toDownload.isNotEmpty()) {
                            Timber.tag("GOG-CloudSaves").i(
                                "Downloading ${toDownload.size} file(s) based on timestamp comparison",
                            )
                            toDownload.forEach { file ->
                                downloadFile(credentials.userId, clientId, dirname, file, syncDir, credentials.accessToken)
                            }
                        }
                    }

                    SyncAction.NONE -> {
                        Timber.tag("GOG-CloudSaves").i("No sync needed - files are up to date")
                    }
                }

                Timber.tag("GOG-CloudSaves").i("Sync completed successfully")
                currentTimestamp()
            } catch (e: Exception) {
                Timber.tag("GOG-CloudSaves").e(e, "Sync failed: ${e.message}")
                0L
            }
        }

    private suspend fun scanLocalFiles(directory: File): List<SyncFile> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<SyncFile>()

            fun scanRecursive(
                dir: File,
                basePath: String,
            ) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val relativePath =
                            file.absolutePath
                                .removePrefix(basePath)
                                .removePrefix("/")
                                .replace("\\", "/")
                        files.add(SyncFile(relativePath, file.absolutePath))
                    } else if (file.isDirectory) {
                        scanRecursive(file, basePath)
                    }
                }
            }

            scanRecursive(directory, directory.absolutePath)
            files.forEach { it.calculateMetadata() }
            files
        }

    private suspend fun getCloudFiles(
        userId: String,
        clientId: String,
        dirname: String,
        authToken: String,
    ): List<CloudFile> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$CLOUD_STORAGE_BASE_URL/v1/$userId/$clientId"
                Timber.tag("GOG").d("[Cloud Saves] API Request: GET $url (dirname filter: $dirname)")

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer $authToken")
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("X-Object-Meta-User-Agent", USER_AGENT)
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No response body"
                        Timber.tag("GOG").e("[Cloud Saves] Failed to fetch cloud files: HTTP ${response.code}")
                        Timber.tag("GOG").e("[Cloud Saves] Response body: $errorBody")
                        return@withContext emptyList()
                    }

                    val responseBody = response.body?.string() ?: ""
                    if (responseBody.isEmpty()) {
                        Timber.tag("GOG").d("[Cloud Saves] Empty response body from cloud storage API")
                        return@withContext emptyList()
                    }

                    val items =
                        try {
                            JSONArray(responseBody)
                        } catch (e: Exception) {
                            Timber.tag("GOG").e(e, "[Cloud Saves] Failed to parse JSON array response")
                            Timber.tag("GOG").e("[Cloud Saves] Response was: $responseBody")
                            return@withContext emptyList()
                        }

                    Timber.tag("GOG").d("[Cloud Saves] Found ${items.length()} total items in cloud storage")

                    val files = mutableListOf<CloudFile>()
                    for (i in 0 until items.length()) {
                        val fileObj = items.getJSONObject(i)
                        val name = fileObj.optString("name", "")
                        val hash = fileObj.optString("hash", "")
                        val lastModified = fileObj.optString("last_modified")

                        Timber.tag("GOG").d("[Cloud Saves]   Examining item $i: name='$name', dirname='$dirname'")

                        if (name.isNotEmpty() && hash.isNotEmpty() && name.startsWith("$dirname/")) {
                            val relativePath = name.removePrefix("$dirname/")
                            files.add(CloudFile(relativePath, hash, lastModified, parseGogTimestampMillis(lastModified)?.div(1000L)))
                            Timber.tag("GOG").d("[Cloud Saves]     Matched: relativePath='$relativePath'")
                        } else {
                            Timber.tag("GOG").d("[Cloud Saves]     Skipped (doesn't match dirname or missing data)")
                        }
                    }

                    Timber.tag("GOG").i("[Cloud Saves] Retrieved ${files.size} cloud files for dirname '$dirname'")
                    files
                }
            } catch (e: Exception) {
                Timber.tag("GOG-CloudSaves").e(e, "Failed to get cloud files")
                emptyList()
            }
        }

    private suspend fun uploadFile(
        userId: String,
        clientId: String,
        dirname: String,
        file: SyncFile,
        authToken: String,
    ) = withContext(Dispatchers.IO) {
        try {
            val localFile = File(file.absolutePath)
            val fileSize = localFile.length()

            Timber.tag("GOG-CloudSaves").i("Uploading: ${file.relativePath} ($fileSize bytes)")

            // GOG/Heroic wire format: gzip body (mtime=0), Etag = md5(gzipped), Content-Encoding: gzip.
            val gzipped = gzipWithZeroMtime(localFile.readBytes())
            val etag =
                MessageDigest.getInstance("MD5")
                    .digest(gzipped)
                    .joinToString("") { "%02x".format(it) }
            val requestBody = gzipped.toRequestBody("application/octet-stream".toMediaType())

            val url = buildObjectUrl(userId, clientId, dirname, file.relativePath)
            val requestBuilder =
                Request
                    .Builder()
                    .url(url)
                    .put(requestBody)
                    .header("Authorization", "Bearer $authToken")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Object-Meta-User-Agent", USER_AGENT)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "gzip")
                    .header("Etag", etag)

            file.updateTime?.let { timestamp ->
                requestBuilder.header("X-Object-Meta-LocalLastModified", timestamp)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use {
                if (response.isSuccessful) {
                    Timber.tag("GOG-CloudSaves").i(
                        "Successfully uploaded: ${file.relativePath} (gzipped ${gzipped.size}B, etag=$etag)",
                    )
                } else {
                    val errorBody = response.body?.string() ?: "No response body"
                    Timber.tag("GOG-CloudSaves").e("Failed to upload ${file.relativePath}: HTTP ${response.code}")
                    Timber.tag("GOG-CloudSaves").e("Upload error body: $errorBody")
                }
            }
        } catch (e: Exception) {
            Timber.tag("GOG-CloudSaves").e(e, "Failed to upload ${file.relativePath}")
        }
    }

    private suspend fun downloadFile(
        userId: String,
        clientId: String,
        dirname: String,
        file: CloudFile,
        syncDir: File,
        authToken: String,
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.tag("GOG-CloudSaves").i("Downloading: ${file.relativePath}")

            val downloaded = downloadObject(userId, clientId, dirname, file, authToken) ?: return@withContext

            val localFile = File(syncDir, file.relativePath)
            localFile.parentFile?.mkdirs()

            FileOutputStream(localFile).use { fos ->
                fos.write(downloaded.bytes)
            }

            localFile.setLastModified(downloaded.localLastModifiedMs ?: file.updateTimestamp?.times(1000L) ?: localFile.lastModified())

            Timber.tag("GOG-CloudSaves").i("Successfully downloaded: ${file.relativePath}")
        } catch (e: Exception) {
            Timber.tag("GOG-CloudSaves").e(e, "Failed to download ${file.relativePath}")
        }
    }

    private fun classifyFiles(
        localFiles: List<SyncFile>,
        cloudFiles: List<CloudFile>,
        timestamp: Long,
    ): SyncClassifier {
        val updatedLocal = mutableListOf<SyncFile>()
        val updatedCloud = mutableListOf<CloudFile>()
        val notExistingLocally = mutableListOf<CloudFile>()
        val notExistingRemotely = mutableListOf<SyncFile>()

        val localByPath = localFiles.associateBy { it.relativePath }
        val cloudByPath = cloudFiles.associateBy { it.relativePath }
        val localPaths = localByPath.keys
        val cloudPaths = cloudByPath.keys

        localFiles.forEach { file ->
            val cloudFile = cloudByPath[file.relativePath]

            if (cloudFile != null) {
                val localTimestamp = file.updateTimestamp ?: 0L
                val cloudTimestamp = cloudFile.updateTimestamp ?: 0L

                if (cloudFile.isDeleted) {
                    if (localTimestamp > cloudTimestamp) {
                        notExistingRemotely.add(file)
                    } else {
                        updatedCloud.add(cloudFile)
                    }
                    return@forEach
                }

                if (cloudFile.md5Hash.equals(file.md5Hash.orEmpty(), ignoreCase = true)) return@forEach

                when {
                    localTimestamp > cloudTimestamp -> updatedLocal.add(file)
                    cloudTimestamp > localTimestamp -> updatedCloud.add(cloudFile)
                    else -> {
                        updatedLocal.add(file)
                        updatedCloud.add(cloudFile)
                    }
                }
                return@forEach
            }

            if (file.relativePath !in cloudPaths) {
                notExistingRemotely.add(file)
            }
            val fileTimestamp = file.updateTimestamp
            if (fileTimestamp != null && fileTimestamp > timestamp) {
                updatedLocal.add(file)
            }
        }

        cloudFiles.forEach { file ->
            if (file.isDeleted) {
                val fileTimestamp = file.updateTimestamp
                if (file.relativePath in localPaths && fileTimestamp != null && fileTimestamp > timestamp) {
                    updatedCloud.add(file)
                }
                return@forEach
            }

            val localFile = localByPath[file.relativePath]
            val hashesMatch = localFile != null && file.md5Hash.equals(localFile.md5Hash.orEmpty(), ignoreCase = true)
            if (localFile != null || hashesMatch) return@forEach

            if (file.relativePath !in localPaths) {
                notExistingLocally.add(file)
            }
            val fileTimestamp = file.updateTimestamp
            if (fileTimestamp != null && fileTimestamp > timestamp) {
                updatedCloud.add(file)
            }
        }

        return SyncClassifier(updatedLocal, updatedCloud, notExistingLocally, notExistingRemotely)
    }

    private fun currentTimestamp(): Long = System.currentTimeMillis() / 1000

    private fun buildObjectUrl(
        userId: String,
        clientId: String,
        dirname: String,
        relativePath: String,
    ): okhttp3.HttpUrl {
        val builder =
            "$CLOUD_STORAGE_BASE_URL/v1"
                .toHttpUrl()
                .newBuilder()
                .addPathSegment(userId)
                .addPathSegment(clientId)
        // Split on "/" so addPathSegment percent-encodes each segment; passing the whole
        // relativePath would encode the "/" itself and the server returns 404.
        dirname.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }.forEach {
            builder.addPathSegment(it)
        }
        return builder.build()
    }

    private suspend fun downloadObject(
        userId: String,
        clientId: String,
        dirname: String,
        file: CloudFile,
        authToken: String,
    ): DownloadedObject? =
        withContext(Dispatchers.IO) {
            try {
                val url = buildObjectUrl(userId, clientId, dirname, file.relativePath)

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer $authToken")
                        .header("User-Agent", USER_AGENT)
                        .header("X-Object-Meta-User-Agent", USER_AGENT)
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No response body"
                        Timber.tag("GOG-CloudSaves").e("Failed to download ${file.relativePath}: HTTP ${response.code}")
                        Timber.tag("GOG-CloudSaves").e("Download error body: $errorBody")
                        return@withContext null
                    }

                    val bytes = response.body?.bytes() ?: return@withContext null
                    Timber.tag("GOG-CloudSaves").d("Downloaded ${bytes.size} bytes for ${file.relativePath}")
                    DownloadedObject(
                        bytes = gunzipIfNeeded(bytes),
                        localLastModifiedMs = parseGogTimestampMillis(response.header("X-Object-Meta-LocalLastModified")),
                    )
                }
            } catch (e: Exception) {
                Timber.tag("GOG-CloudSaves").e(e, "Failed to download ${file.relativePath}")
                null
            }
        }

    private suspend fun deleteCloudFile(
        userId: String,
        clientId: String,
        dirname: String,
        file: CloudFile,
        authToken: String,
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.tag("GOG-CloudSaves").i("Deleting cloud file: ${file.relativePath}")
            val request =
                Request
                    .Builder()
                    .url(buildObjectUrl(userId, clientId, dirname, file.relativePath))
                    .delete()
                    .header("Authorization", "Bearer $authToken")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Object-Meta-User-Agent", USER_AGENT)
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No response body"
                    Timber.tag("GOG-CloudSaves").e("Failed to delete cloud file ${file.relativePath}: HTTP ${response.code}")
                    Timber.tag("GOG-CloudSaves").e("Delete error body: $errorBody")
                }
            }
        } catch (e: Exception) {
            Timber.tag("GOG-CloudSaves").e(e, "Failed to delete cloud file ${file.relativePath}")
        }
    }

    private fun deleteLocalFilesMissingFromCloud(
        localFiles: List<SyncFile>,
        downloadableCloud: List<CloudFile>,
    ) {
        val cloudPaths = downloadableCloud.map { it.relativePath }.toSet()
        localFiles
            .filter { it.relativePath !in cloudPaths }
            .forEach { file ->
                runCatching {
                    val localFile = File(file.absolutePath)
                    if (localFile.exists() && localFile.isFile) {
                        Timber.tag("GOG-CloudSaves").i("Deleting local file missing from cloud: ${file.relativePath}")
                        localFile.delete()
                    }
                }.onFailure {
                    Timber.tag("GOG-CloudSaves").e(it, "Failed to delete local file ${file.relativePath}")
                }
            }
    }

    private fun pruneEmptyDirectories(root: File) {
        if (!root.exists() || !root.isDirectory) return
        root
            .walkBottomUp()
            .filter { it != root && it.isDirectory && it.listFiles()?.isEmpty() == true }
            .forEach { directory ->
                runCatching {
                    Timber.tag("GOG-CloudSaves").i("Deleting empty local cloud-save directory: ${directory.absolutePath}")
                    directory.delete()
                }.onFailure {
                    Timber.tag("GOG-CloudSaves").e(it, "Failed to delete empty directory ${directory.absolutePath}")
                }
            }
    }

    private fun safeZipEntryName(
        prefix: String,
        relativePath: String,
    ): String? {
        val parts =
            "$prefix/$relativePath"
                .replace('\\', '/')
                .split('/')
                .filter { it.isNotBlank() && it != "." && it != ".." }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("/")
    }
}

// Zero the gzip mtime bytes so the digest matches gogdl's `gzip.compress(raw, 6, mtime=0)`.
private fun gzipWithZeroMtime(raw: ByteArray): ByteArray {
    val sink = java.io.ByteArrayOutputStream()
    GZIPOutputStream(sink).use { it.write(raw) }
    val out = sink.toByteArray()
    if (out.size >= 8) {
        out[4] = 0; out[5] = 0; out[6] = 0; out[7] = 0
    }
    return out
}

private fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
    val isGzipped =
        bytes.size >= 2 &&
            bytes[0] == 0x1f.toByte() &&
            bytes[1] == 0x8b.toByte()
    if (!isGzipped) return bytes
    return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}

private fun parseGogTimestampMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
}
