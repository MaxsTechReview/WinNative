package com.winlator.cmod.feature.steamcloudsync
import androidx.room.withTransaction
import com.winlator.cmod.feature.stores.steam.data.PostSyncInfo
import com.winlator.cmod.feature.stores.steam.data.SaveFilePattern
import com.winlator.cmod.feature.stores.steam.data.SteamApp
import com.winlator.cmod.feature.stores.steam.data.UserFileInfo
import com.winlator.cmod.feature.stores.steam.data.UserFilesDownloadResult
import com.winlator.cmod.feature.stores.steam.data.UserFilesUploadResult
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.enums.SaveLocation
import com.winlator.cmod.feature.stores.steam.enums.SyncResult
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.FileUtils
import com.winlator.cmod.feature.stores.steam.utils.SteamUtils
import `in`.dragonbra.javasteam.enums.EPlatformType
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.Enums.ECloudStoragePersistState
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Date
import java.util.stream.Collectors
import java.util.zip.ZipInputStream
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.measureTime

/**
 * [Steam Auto Cloud](https://partner.steamgames.com/doc/features/cloud#steam_auto-cloud)
 */
object SteamAutoCloud {
    private const val MAX_CLOUD_FILE_SIZE_BYTES = 100L * 1024L * 1024L
    private const val DOWNLOAD_TMP_SUFFIX = ".steamtmp"

    /**
     * Per-Steam-protocol content-divergence check used by the launch-time conflict probe.
     *
     * Returns `true` if ANY persisted cloud file is missing locally OR has a differing
     * size/SHA-1. Returns `false` only when every cloud file is present locally with
     * matching size and SHA — which is the canonical "no conflict" condition per the
     * `steammessages_cloud.steamclient.proto` spec.
     *
     * Optimizations:
     *  - Size comparison is a free pre-filter (no SHA on obvious size delta).
     *  - `Sequence.any { … }` short-circuits on the first divergence — at most O(diverged-file-size).
     *  - We compute the local SHA only on files whose size already matches, so identical
     *    files cost two `Files.size` calls + a streamed SHA each.
     */
    fun cloudContentDiffersFromLocal(
        response: AppFileChangeList,
        prefixToPath: (String) -> String,
    ): Boolean {
        return response.files
            .asSequence()
            .filter { it.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStatePersisted }
            .any { cloudFile ->
                val localPath = resolveLocalPathForCloudFile(cloudFile, response, prefixToPath)
                if (localPath == null) {
                    Timber.d("ConflictProbe: cloud file %s has no local path → diverges", cloudFile.filename)
                    return@any true
                }
                if (!Files.exists(localPath)) {
                    Timber.d("ConflictProbe: cloud file %s missing locally → diverges", cloudFile.filename)
                    return@any true
                }
                val localSize =
                    try {
                        Files.size(localPath)
                    } catch (_: Exception) {
                        return@any true
                    }
                if (localSize != cloudFile.rawFileSize.toLong()) {
                    Timber.d(
                        "ConflictProbe: %s size mismatch (cloud=%d, local=%d) → diverges",
                        cloudFile.filename,
                        cloudFile.rawFileSize,
                        localSize,
                    )
                    return@any true
                }
                val localSha = runCatching { streamingSha(localPath) }.getOrNull()
                if (localSha == null) {
                    return@any true
                }
                val mismatched = !localSha.contentEquals(cloudFile.shaFile)
                if (mismatched) {
                    Timber.d("ConflictProbe: %s SHA mismatch → diverges", cloudFile.filename)
                }
                mismatched
            }
    }

    /**
     * Best-effort cloud-file → local-Path resolution for the content-divergence check.
     * Mirrors the simpler half of [getFullFilePath] (defined as a closure inside
     * [syncUserFiles]) without needing the full closure context. Handles the common
     * cases — SteamUserData-rooted files and `%GameInstall%`-prefixed filenames — and
     * falls back to `SteamUserData/<filename>` for unrecognized prefixes (matching the
     * download path's fallback behavior).
     */
    private fun resolveLocalPathForCloudFile(
        cloudFile: AppFileInfo,
        response: AppFileChangeList,
        prefixToPath: (String) -> String,
    ): Path? {
        val prefix =
            if (cloudFile.pathPrefixIndex >= 0 && cloudFile.pathPrefixIndex < response.pathPrefixes.size) {
                response.pathPrefixes[cloudFile.pathPrefixIndex]
            } else {
                ""
            }

        val gameInstallToken = "%${PathType.GameInstall.name}%"
        if (cloudFile.filename.startsWith(gameInstallToken)) {
            val stripped = cloudFile.filename.removePrefix(gameInstallToken).trimStart('/', '\\')
            return runCatching { Paths.get(prefixToPath(PathType.GameInstall.name), stripped) }.getOrNull()
        }

        val tokenMatch = findPlaceholderWithin(prefix).firstOrNull()?.value
        val rootName =
            if (tokenMatch != null) {
                tokenMatch.removePrefix("%").removeSuffix("%")
            } else {
                PathType.DEFAULT.name
            }
        val pathAfterRoot =
            if (tokenMatch != null) {
                prefix.removePrefix(tokenMatch).trimStart('/', '\\')
            } else {
                prefix
            }
        return runCatching {
            val baseDir = prefixToPath(rootName)
            if (pathAfterRoot.isEmpty()) {
                Paths.get(baseDir, cloudFile.filename)
            } else {
                Paths.get(baseDir, pathAfterRoot, cloudFile.filename)
            }
        }.getOrNull()
    }

    private data class FileChanges(
        val filesDeleted: List<UserFileInfo>,
        val filesModified: List<UserFileInfo>,
        val filesCreated: List<UserFileInfo>,
    )

    private data class RemotePath(
        val root: PathType,
        val path: String,
    )

    private data class CloudPathRouting(
        val localRootByCloudToken: Map<String, String>,
        val localPathByCloudPrefix: Map<String, String>,
    )

    private fun findPlaceholderWithin(aString: String): Sequence<MatchResult> = Regex("%\\w+%").findAll(aString)

    private fun cloudToken(root: PathType): String = "%${root.name}%"

    private fun substituteSteamIds(value: String): String =
        value
            .replace("{64BitSteamID}", SteamUtils.getSteamId64().toString())
            .replace("{Steam3AccountID}", SteamUtils.getSteam3AccountId().toString())

    private fun buildCloudPathRouting(
        appInfo: SteamApp,
        prefixToPath: (String) -> String,
    ): CloudPathRouting {
        val rootAliases =
            appInfo.ufs.saveFilePatterns
                .filter { it.uploadRoot != it.root }
                .associate { cloudToken(it.uploadRoot) to it.root.name }

        val exactPrefixTargets =
            appInfo.ufs.saveFilePatterns
                .filter { it.uploadPath != it.path }
                .associate { pattern ->
                    val cloudPrefix = substituteSteamIds("${cloudToken(pattern.uploadRoot)}${pattern.uploadPath}").trimEnd('/')
                    cloudPrefix to Paths.get(prefixToPath(pattern.root.name), pattern.substitutedPath).pathString
                }

        return CloudPathRouting(rootAliases, exactPrefixTargets)
    }

    private fun uploadNameFor(
        file: UserFileInfo,
        hasUfsPatterns: Boolean,
    ): String =
        if (file.root == PathType.SteamUserData || !hasUfsPatterns) {
            file.path + file.filename
        } else {
            file.prefixPath
        }

    private fun isCloudCandidateWithinLimit(
        path: Path,
        scanName: String,
    ): Boolean {
        val size =
            try {
                Files.size(path)
            } catch (_: Exception) {
                return false
            }

        if (size <= MAX_CLOUD_FILE_SIZE_BYTES) return true

        Timber.w(
            "Skipping oversize file in %s: %s (%d bytes > %d)",
            scanName,
            path,
            size,
            MAX_CLOUD_FILE_SIZE_BYTES,
        )
        return false
    }

    private fun scanCloudCandidates(
        basePath: Path,
        pattern: String,
        maxDepth: Int,
        scanName: String,
    ): List<Path> =
        FileUtils
            .findFilesRecursive(
                rootPath = basePath,
                pattern = pattern,
                maxDepth = maxDepth,
            ).use { stream ->
                stream
                    .filter { isCloudCandidateWithinLimit(it, scanName) }
                    .collect(Collectors.toList())
            }

    private fun pathToUserFile(
        root: PathType,
        basePath: Path,
        pathPrefix: String,
        file: Path,
        cloudRoot: PathType = root,
        cloudPath: String = pathPrefix,
    ): UserFileInfo {
        val sha = streamingSha(file)
        val relativePath = basePath.relativize(file).pathString

        Timber.i("Found ${file.pathString}\n\tin %${root.name}%$pathPrefix\n\twith sha [${sha.joinToString(", ")}]")

        return UserFileInfo(
            root,
            pathPrefix,
            relativePath,
            Files.getLastModifiedTime(file).toMillis(),
            sha,
            cloudRoot,
            cloudPath,
        )
    }

    private fun collectUfsPatternFiles(
        savePattern: SaveFilePattern,
        prefixToPath: (String) -> String,
    ): Pair<String, List<UserFileInfo>> {
        val basePath = Paths.get(prefixToPath(savePattern.root.toString()), savePattern.substitutedPath)

        Timber.i("Looking for saves in $basePath with pattern ${savePattern.pattern} (prefix ${savePattern.prefix})")

        val files =
            scanCloudCandidates(
                basePath = basePath,
                pattern = savePattern.pattern,
                maxDepth = if (savePattern.recursive != 0) -1 else 0,
                scanName = "UFS scan",
            ).map { file ->
                pathToUserFile(
                    root = savePattern.root,
                    basePath = basePath,
                    pathPrefix = savePattern.substitutedPath,
                    file = file,
                    cloudRoot = savePattern.uploadRoot,
                    cloudPath = savePattern.uploadPath,
                )
            }

        Timber.i("Found ${files.size} file(s) in $basePath for pattern ${savePattern.pattern}")

        return Paths.get(savePattern.prefix).pathString to files
    }

    private fun collectSteamUserDataFiles(prefixToPath: (String) -> String): Pair<String, List<UserFileInfo>> {
        val rootType = PathType.SteamUserData
        val basePath = Paths.get(prefixToPath(rootType.toString()))

        Timber.i("Scanning $basePath recursively (depth 5) under ${rootType.name}")

        val files =
            scanCloudCandidates(
                basePath = basePath,
                pattern = "*",
                maxDepth = 5,
                scanName = "SteamUserData scan",
            ).map { file ->
                pathToUserFile(
                    root = rootType,
                    basePath = basePath,
                    pathPrefix = "",
                    file = file,
                )
            }

        Timber.i("Found ${files.size} file(s) in $basePath for SteamUserData scan")

        return Paths.get("%${rootType.name}%").pathString to files
    }

    private inline fun InputStream.copyTo(
        out: OutputStream,
        bufferSize: Int = 8 * 1024,
        progress: (Long) -> Unit,
    ) {
        val buf = ByteArray(bufferSize)
        var bytesRead: Int
        var total = 0L
        while (read(buf).also { bytesRead = it } >= 0) {
            if (bytesRead == 0) continue
            out.write(buf, 0, bytesRead)
            total += bytesRead
            progress(total)
        }
    }

    /**
     * Stream a SHA-1 hash without loading the whole file into memory. Saves can be up
     * to 100 MB each and we hash every save twice per sync; readAllBytes blew up on
     * low-RAM Android devices.
     */
    private fun streamingSha(path: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) digest.update(buf, 0, n)
            }
        }
        return digest.digest()
    }

    fun syncUserFiles(
        appInfo: SteamApp,
        clientId: Long,
        steamInstance: SteamService,
        steamCloud: SteamCloud,
        preferredSave: SaveLocation = SaveLocation.None,
        parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        prefixToPath: (String) -> String,
        overrideLocalChangeNumber: Long? = null,
        onProgress: ((message: String, progress: Float) -> Unit)? = null,
    ): Deferred<PostSyncInfo?> =
        parentScope.async {
            val postSyncInfo: PostSyncInfo?

            Timber.i("Retrieving save files of ${appInfo.name}")

            val cloudRouting = buildCloudPathRouting(appInfo, prefixToPath)

            val getPathTypePairs: (AppFileChangeList) -> List<Pair<String, String>> = { fileList ->
                fileList.pathPrefixes
                    .map {
                        var matchResults = findPlaceholderWithin(it).map { it.value }.toList()
                        val bare = if (it.startsWith("ROOT_MOD")) listOf("ROOT_MOD") else emptyList()

                        Timber.i("Mapping prefix $it and found $matchResults")

                        if (matchResults.isEmpty()) {
                            matchResults = List(1) { PathType.DEFAULT.name }
                        }

                        matchResults + bare
                    }.flatten()
                    .distinct()
                    .map { placeholder ->
                        val localRootName = cloudRouting.localRootByCloudToken[placeholder] ?: placeholder
                        val root = PathType.from(localRootName)
                        // Don't silently drop unrecognized cloud-root tokens — if we did, the
                        // download path would skip files written to that prefix (silent
                        // "Use Cloud" failure) and the upload path would lose baseline entries.
                        // Fall back to PathType.DEFAULT (SteamUserData) so paths resolve to
                        // something writable; Steam Cloud platform filtering already guarantees
                        // we're only being handed Windows-applicable files.
                        val effectiveLocalRoot = if (root.isSupportedSteamCloudRoot) localRootName else PathType.DEFAULT.name
                        if (!root.isSupportedSteamCloudRoot) {
                            Timber.w(
                                "Unrecognized Steam cloud root '%s' in prefix mapping — defaulting to %s so files still resolve",
                                placeholder,
                                PathType.DEFAULT.name,
                            )
                        }
                        placeholder to prefixToPath(effectiveLocalRoot)
                    }
            }

            val parseRemotePath: (String) -> RemotePath = { prefix ->
                val token =
                    when {
                        prefix.startsWith("ROOT_MOD", ignoreCase = true) -> "ROOT_MOD"
                        else -> findPlaceholderWithin(prefix).firstOrNull()?.value
                    }
                val root = token?.let { PathType.from(it) } ?: PathType.DEFAULT
                val withoutRoot =
                    when {
                        token == null -> prefix
                        prefix.startsWith("ROOT_MOD", ignoreCase = true) ->
                            prefix.substring("ROOT_MOD".length)
                        else -> prefix.removePrefix(token)
                    }.trimStart('/', '\\')
                RemotePath(root, if (withoutRoot == ".") "" else withoutRoot)
            }

            val convertPrefixes: (AppFileChangeList) -> List<String> = { fileList ->
                val pathTypePairs = getPathTypePairs(fileList)

                fileList.pathPrefixes.map { prefix ->
                    var modified = prefix

                    val prefixContainsNoPlaceholder = findPlaceholderWithin(prefix).none()

                    if (prefixContainsNoPlaceholder) {
                        modified = Paths.get(PathType.DEFAULT.name, prefix).pathString
                    }

                    pathTypePairs.forEach {
                        modified = modified.replace(it.first, it.second)
                    }

                    // if the prefix has not been modified then there were no placeholders in it
                    // so we need to set it to point to the default path
                    if (modified == prefix) {
                        modified = Paths.get(prefixToPath(PathType.DEFAULT.name), modified).toString()
                    }

                    modified
                }
            }

            val getFilePrefix: (AppFileInfo, AppFileChangeList) -> String = { file, fileList ->
                if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                    Paths.get(fileList.pathPrefixes[file.pathPrefixIndex]).pathString
                } else {
                    ""
                }
            }

            val getFileRemotePath: (AppFileInfo, AppFileChangeList) -> RemotePath = { file, fileList ->
                if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                    parseRemotePath(fileList.pathPrefixes[file.pathPrefixIndex])
                } else if (file.filename.startsWith("%${PathType.GameInstall.name}%")) {
                    RemotePath(PathType.GameInstall, "")
                } else {
                    RemotePath(PathType.DEFAULT, "")
                }
            }

            val getFilePrefixPath: (AppFileInfo, AppFileChangeList) -> String = { file, fileList ->
                Paths.get(getFilePrefix(file, fileList), file.filename).pathString
            }

            val getFullFilePath: (AppFileInfo, AppFileChangeList) -> Path? = getFullFilePath@{ file, fileList ->
                val remotePath = getFileRemotePath(file, fileList)
                // Don't silently drop files whose root isn't in our known-Windows set —
                // that turned "Use Cloud" into a no-op (filesDownloaded=0 reported as Success).
                // For unrecognized roots, log loudly and let the download attempt resolve via
                // convertPrefixes (which now falls back to SteamUserData for unknown tokens).
                if (!remotePath.root.isSupportedSteamCloudRoot) {
                    Timber.w(
                        "Unrecognized Steam cloud file root %s: %s — attempting download via fallback path",
                        remotePath.root,
                        getFilePrefixPath(file, fileList),
                    )
                }

                val gameInstallPrefix = "%${PathType.GameInstall.name}%"
                if (file.filename.startsWith(gameInstallPrefix)) {
                    // Steam API sometimes returns prefix="" and filename="%GameInstall%save0.dat" instead of splitting correctly.
                    val stripped = file.filename.removePrefix(gameInstallPrefix).trimStart('/', '\\')
                    return@getFullFilePath cloudRouting.localPathByCloudPrefix[gameInstallPrefix]?.let {
                        Paths.get(it, stripped)
                    } ?: Paths.get(prefixToPath(PathType.GameInstall.name), stripped)
                }

                val defaultConvertedPrefixes = convertPrefixes(fileList)
                val convertedPrefixes =
                    fileList.pathPrefixes.mapIndexed { index, prefix ->
                        cloudRouting.localPathByCloudPrefix[prefix.trimEnd('/')] ?: defaultConvertedPrefixes[index]
                    }

                if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                    Paths.get(convertedPrefixes[file.pathPrefixIndex], file.filename)
                } else {
                    // if the file does not reference any prefix then we need to set it to the default path
                    Paths.get(prefixToPath(PathType.DEFAULT.name), file.filename)
                }
            }

            val getFilesDiff: (List<UserFileInfo>, List<UserFileInfo>) -> Pair<Boolean, FileChanges> = { currentFiles, oldFiles ->
                // Index by prefixPath so each diff bucket costs O(N+M) lookups instead of
                // O(N*M) — the two-list scan blew up sync time on games with hundreds of
                // mod or screenshot files.
                val oldByPath = oldFiles.associateBy { it.prefixPath }
                val currentByPath = currentFiles.associateBy { it.prefixPath }

                val newFiles = currentFiles.filter { it.prefixPath !in oldByPath }
                val deletedFiles = oldFiles.filter { it.prefixPath !in currentByPath }
                val modifiedFiles =
                    currentFiles.mapNotNull { current ->
                        val old = oldByPath[current.prefixPath] ?: return@mapNotNull null
                        Timber.i("Comparing SHA of ${old.prefixPath} and ${current.prefixPath}")
                        Timber.i("[${old.sha.joinToString(", ")}]\n[${current.sha.joinToString(", ")}]")
                        current.takeUnless { old.sha.contentEquals(current.sha) }
                    }

                val changesExist = newFiles.isNotEmpty() || deletedFiles.isNotEmpty() || modifiedFiles.isNotEmpty()

                changesExist to FileChanges(deletedFiles, modifiedFiles, newFiles)
            }

            val hasHashConflicts: (Map<String, List<UserFileInfo>>, AppFileChangeList) -> Boolean =
                { localUserFiles, fileList ->
                    // Build a per-prefix filename index once instead of scanning the
                    // whole list for every remote file.
                    val localByPrefixAndName: Map<String, Map<String, UserFileInfo>> =
                        localUserFiles.mapValues { (_, files) -> files.associateBy { it.filename } }

                    fileList.files.any { file ->
                        val remotePath = getFileRemotePath(file, fileList)
                        // Don't silently say "no conflict" for unsupported roots — that lets a
                        // post-download verify slip through even when the file truly didn't land.
                        // Log loudly and fall through to the normal SHA compare with the local map.
                        if (!remotePath.root.isSupportedSteamCloudRoot) {
                            Timber.w(
                                "Hash-validating cloud file with unrecognized root %s: %s",
                                remotePath.root,
                                file.filename,
                            )
                        }
                        val gameInstallPrefix = "%${PathType.GameInstall.name}%"
                        val remoteFilename =
                            if (remotePath.root == PathType.GameInstall && file.filename.startsWith(gameInstallPrefix)) {
                                file.filename.removePrefix(gameInstallPrefix)
                            } else {
                                file.filename
                            }
                        val prefix = getFilePrefix(file, fileList)
                        Timber.i("Checking for $prefix in ${localUserFiles.keys}")

                        val localMatch = localByPrefixAndName[prefix]?.get(remoteFilename) ?: return@any false
                        Timber.i("Comparing SHA of ${getFilePrefixPath(file, fileList)} and ${localMatch.prefixPath}")
                        Timber.i("[${file.shaFile.joinToString(", ")}]\n[${localMatch.sha.joinToString(", ")}]")

                        !file.shaFile.contentEquals(localMatch.sha)
                    }
                }

            val getLocalUserFilesAsPrefixMap: () -> Map<String, List<UserFileInfo>> = {
                val result = mutableMapOf<String, MutableList<UserFileInfo>>()

                appInfo.ufs.saveFilePatterns
                    .asSequence()
                    .filter { it.root.isWindows }
                    .filter { it.root != PathType.SteamUserData }
                    .map { collectUfsPatternFiles(it, prefixToPath) }
                    .forEach { (prefixKey, files) ->
                        result.getOrPut(prefixKey) { mutableListOf() }.addAll(files)
                    }

                val (userDataPrefix, userDataFiles) = collectSteamUserDataFiles(prefixToPath)
                if (userDataFiles.isNotEmpty()) {
                    result.getOrPut(userDataPrefix) { mutableListOf() }.addAll(userDataFiles)
                }

                result
            }

            val fileChangeListToUserFiles: (AppFileChangeList, Boolean) -> List<UserFileInfo> = { appFileListChange, includeDeleted ->
                appFileListChange.files
                    .filter {
                        if (includeDeleted) {
                            it.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStateDeleted
                        } else {
                            it.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStatePersisted
                        }
                    }.mapNotNull {
                        val remotePath = getFileRemotePath(it, appFileListChange)
                        // Don't drop unrecognized-root files from the baseline — that broke the
                        // exit-time diff (allLocalUserFiles vs baseline) and caused real changes
                        // to be missed → silent upload no-op. Log loudly and keep the entry; the
                        // root falls through to whatever PathType.from(...) returned.
                        if (!remotePath.root.isSupportedSteamCloudRoot) {
                            Timber.w(
                                "Including baseline cloud file with unrecognized root %s: %s",
                                remotePath.root,
                                it.filename,
                            )
                        }
                        val gameInstallPrefix = "%${PathType.GameInstall.name}%"
                        val filename =
                            if (remotePath.root == PathType.GameInstall && it.filename.startsWith(gameInstallPrefix)) {
                                it.filename.removePrefix(gameInstallPrefix)
                            } else {
                                it.filename
                            }
                        UserFileInfo(
                            root = remotePath.root,
                            path = remotePath.path,
                            filename = filename,
                            timestamp = it.timestamp.time,
                            sha = it.shaFile,
                        )
                    }
            }

            val buildUrl: (Boolean, String, String) -> String = { useHttps, urlHost, urlPath ->
                val scheme = if (useHttps) "https://" else "http://"
                "$scheme${urlHost}$urlPath"
            }

            val downloadFiles: (AppFileChangeList, CoroutineScope) -> Deferred<UserFilesDownloadResult> = { fileList, parentScope ->
                parentScope.async {
                    var filesDownloaded = 0
                    var bytesDownloaded = 0L
                    val filesToDownload =
                        fileList.files.filter {
                            it.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStatePersisted
                        }
                    val totalFiles = filesToDownload.size

                    filesToDownload.forEachIndexed { index, file ->
                        val prefixedPath = getFilePrefixPath(file, fileList)
                        val remotePathForFile = getFileRemotePath(file, fileList)
                        val actualFilePath = getFullFilePath(file, fileList)
                        if (actualFilePath == null) {
                            Timber.w("Skipping download for unsupported Steam cloud path $prefixedPath")
                            return@forEachIndexed
                        }

                        // Path-traversal guard: reject any cloud-supplied filename that resolves
                        // outside the prefix root. Steam is trusted, but a malformed entry
                        // (e.g. "..\\..\\system.reg") must never be allowed to overwrite Wine
                        // system files.
                        val rootBase =
                            Paths
                                .get(prefixToPath(remotePathForFile.root.toString()))
                                .toAbsolutePath()
                                .normalize()
                        val targetNormalized = actualFilePath.toAbsolutePath().normalize()
                        if (!targetNormalized.startsWith(rootBase)) {
                            Timber.e(
                                "Refusing path-traversal target outside save root: %s (root=%s, prefixedPath=%s)",
                                targetNormalized,
                                rootBase,
                                prefixedPath,
                            )
                            return@forEachIndexed
                        }

                        Timber.i("$prefixedPath -> $actualFilePath")

                        val fileDownloadInfo = steamCloud.clientFileDownload(appInfo.id, prefixedPath).await()

                        if (fileDownloadInfo.urlHost.isNotEmpty()) {
                            onProgress?.invoke("Downloading ${file.filename}", -1f)
                            val httpUrl =
                                with(fileDownloadInfo) {
                                    buildUrl(useHttps, urlHost, urlPath)
                                }

                            Timber.i("Downloading $httpUrl")

                            val headers =
                                Headers.headersOf(
                                    *fileDownloadInfo.requestHeaders
                                        .map { listOf(it.name, it.value) }
                                        .flatten()
                                        .toTypedArray(),
                                )

                            val request =
                                Request
                                    .Builder()
                                    .url(httpUrl)
                                    .headers(headers)
                                    .build()

                            val httpClient = steamInstance.steamClient!!.configuration.httpClient

                            val response =
                                withTimeout(SteamService.requestTimeout) {
                                    httpClient.newCall(request).execute()
                                }

                            response.use { downloadResponse ->
                                if (!downloadResponse.isSuccessful) {
                                    Timber.w("File download of $prefixedPath was unsuccessful")
                                    return@forEachIndexed
                                }

                                // Atomic write: stream into a sibling .steamtmp file, fsync, verify
                                // size, then rename into place. Prevents truncated/partial saves
                                // from being left in the destination if the stream aborts.
                                val tmpPath =
                                    actualFilePath.resolveSibling(actualFilePath.fileName.toString() + DOWNLOAD_TMP_SUFFIX)
                                val totalFileSize = fileDownloadInfo.rawFileSize.toLong()
                                var totalBytesRead = 0L
                                var lastReportedProgress = -1f
                                val progressThreshold = 0.01f // Update every 1%
                                var commitTmp = false

                                try {
                                    actualFilePath.parent?.let { parent -> Files.createDirectories(parent) }
                                    // Clean up any leftover tmp from a previously aborted run.
                                    try {
                                        Files.deleteIfExists(tmpPath)
                                    } catch (_: Exception) {
                                        // best-effort
                                    }

                                    val copyToFile: (InputStream) -> Unit = { input ->
                                        FileOutputStream(tmpPath.toString()).use { fs ->
                                            input.copyTo(fs, 8 * 1024) { bytesRead ->
                                                totalBytesRead = bytesRead
                                                if (totalFileSize > 0) {
                                                    val currentProgress = (totalBytesRead.toFloat() / totalFileSize).coerceIn(0f, 1f)
                                                    if (currentProgress - lastReportedProgress >= progressThreshold || currentProgress >= 1f) {
                                                        onProgress?.invoke("Downloading ${file.filename}", currentProgress)
                                                        lastReportedProgress = currentProgress
                                                    }
                                                }
                                            }
                                            // Force bytes to disk before the rename so a crash
                                            // between move and process exit can't leave the
                                            // destination pointing at unsynced pages.
                                            try {
                                                fs.fd.sync()
                                            } catch (e: Exception) {
                                                Timber.w(e, "fsync failed for %s; continuing", tmpPath)
                                            }
                                        }
                                    }

                                    withTimeout(SteamService.responseTimeout) {
                                        if (fileDownloadInfo.fileSize != fileDownloadInfo.rawFileSize) {
                                            downloadResponse.body?.byteStream()?.use { inputStream ->
                                                ZipInputStream(inputStream).use { zipInput ->
                                                    val entry = zipInput.nextEntry

                                                    if (entry == null) {
                                                        Timber.w("Downloaded user file $prefixedPath has no zip entries")
                                                        return@withTimeout
                                                    }

                                                    copyToFile(zipInput)

                                                    if (zipInput.nextEntry != null) {
                                                        throw IOException(
                                                            "Downloaded user file $prefixedPath has more than one zip entry",
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            downloadResponse.body?.byteStream()?.use { inputStream ->
                                                copyToFile(inputStream)
                                            }
                                        }

                                        if (totalBytesRead != totalFileSize) {
                                            throw IOException(
                                                "Truncated download for $prefixedPath: $totalBytesRead/$totalFileSize bytes",
                                            )
                                        }

                                        // Atomic publish into the destination. ATOMIC_MOVE is
                                        // not portable when combined with REPLACE_EXISTING (some
                                        // FS implementations throw UnsupportedOperationException),
                                        // so try ATOMIC_MOVE alone first; on Android's POSIX FS
                                        // rename(2) atomically replaces. Fall back to plain
                                        // REPLACE_EXISTING if the FS rejects ATOMIC_MOVE.
                                        try {
                                            Files.move(
                                                tmpPath,
                                                actualFilePath,
                                                StandardCopyOption.ATOMIC_MOVE,
                                            )
                                        } catch (_: Exception) {
                                            Files.move(
                                                tmpPath,
                                                actualFilePath,
                                                StandardCopyOption.REPLACE_EXISTING,
                                            )
                                        }
                                        commitTmp = true

                                        filesDownloaded++

                                        bytesDownloaded += fileDownloadInfo.fileSize
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Could not download $actualFilePath; preserving existing local file")
                                } finally {
                                    if (!commitTmp) {
                                        try {
                                            Files.deleteIfExists(tmpPath)
                                        } catch (_: Exception) {
                                            // best-effort
                                        }
                                    }
                                }
                            }
                        } else {
                            Timber.w("URL host of $prefixedPath was empty")
                        }
                    }

                    if (totalFiles > 0) {
                        onProgress?.invoke("Download complete", 1.0f)
                    }

                    UserFilesDownloadResult(filesDownloaded, bytesDownloaded)
                }
            }

            val uploadFiles: (FileChanges, List<UserFileInfo>, CoroutineScope) -> Deferred<UserFilesUploadResult> = { fileChanges, managedFiles, parentScope ->
                parentScope.async {
                    var filesUploaded = 0
                    var bytesUploaded = 0L

                    val hasUfsPatterns = appInfo.ufs.saveFilePatterns.isNotEmpty()
                    val cloudUploadName: (UserFileInfo) -> String = { uploadNameFor(it, hasUfsPatterns) }

                    val filesToDelete = fileChanges.filesDeleted.map(cloudUploadName)

                    val filesToUpload =
                        fileChanges.filesCreated
                            .union(fileChanges.filesModified)
                            .map { cloudUploadName(it) to it }
                            // Filter out entries whose files no longer exist at upload time
                            .filter { Files.exists(it.second.getAbsPath(prefixToPath)) }

                    val totalFiles = filesToUpload.size
                    val finalFileCount = managedFiles.size
                    if (appInfo.ufs.maxNumFiles > 0 && finalFileCount > appInfo.ufs.maxNumFiles) {
                        Timber.e(
                            "Steam cloud upload would exceed file count quota for ${appInfo.id}: " +
                                "$finalFileCount > ${appInfo.ufs.maxNumFiles}",
                        )
                        return@async UserFilesUploadResult(false, 0, 0, 0)
                    }

                    var totalManagedBytes = 0L
                    for (file in managedFiles) {
                        val absPath = file.getAbsPath(prefixToPath)
                        if (!Files.exists(absPath)) continue
                        val size = Files.size(absPath)
                        if (size > MAX_CLOUD_FILE_SIZE_BYTES) {
                            Timber.e(
                                "Steam cloud upload would exceed per-file limit for ${file.prefixPath}: " +
                                    "$size > $MAX_CLOUD_FILE_SIZE_BYTES",
                            )
                            return@async UserFilesUploadResult(false, 0, 0, 0)
                        }
                        totalManagedBytes += size
                    }

                    if (appInfo.ufs.quota > 0 && totalManagedBytes > appInfo.ufs.quota.toLong()) {
                        Timber.e(
                            "Steam cloud upload would exceed byte quota for ${appInfo.id}: " +
                                "$totalManagedBytes > ${appInfo.ufs.quota}",
                        )
                        return@async UserFilesUploadResult(false, 0, 0, 0)
                    }

                    Timber.i(
                        "Beginning app upload batch with ${filesToDelete.size} file(s) to delete " +
                            "and ${filesToUpload.size} file(s) to upload",
                    )

                    val uploadBatchResponse =
                        steamCloud
                            .beginAppUploadBatch(
                                appId = appInfo.id,
                                machineName = SteamUtils.getMachineName(steamInstance),
                                clientId = clientId,
                                filesToDelete = filesToDelete,
                                filesToUpload = filesToUpload.map { it.first },
                                // TODO: have branch be user selected and use that selection here
                                appBuildId = appInfo.branches["public"]?.buildId ?: 0,
                            ).await()

                    var uploadBatchSuccess = true

                    filesToUpload.map { it.second }.forEachIndexed { index, file ->
                        val absFilePath = file.getAbsPath(prefixToPath)

                        val fileSize =
                            try {
                                val size = Files.size(absFilePath)
                                if (size > Int.MAX_VALUE || size > MAX_CLOUD_FILE_SIZE_BYTES) {
                                    Timber.w("Skipping upload of ${file.prefixPath}: file is too large ($size bytes)")
                                    uploadBatchSuccess = false
                                    return@forEachIndexed
                                }
                                size.toInt()
                            } catch (e: Exception) {
                                Timber.w("Skipping upload of ${file.prefixPath}: ${e.javaClass.simpleName}: ${e.message}")
                                uploadBatchSuccess = false
                                return@forEachIndexed
                            }

                        Timber.i("Beginning upload of ${file.prefixPath} whose timestamp is ${file.timestamp}")

                        // Report start of upload
                        onProgress?.invoke("Uploading ${file.filename}", 0f)

                        val uploadInfo =
                            steamCloud
                                .beginFileUpload(
                                    appId = appInfo.id,
                                    filename = cloudUploadName(file),
                                    fileSize = fileSize,
                                    rawFileSize = fileSize,
                                    fileSha = file.sha,
                                    timestamp = Date(file.timestamp),
                                    uploadBatchId = uploadBatchResponse.batchID,
                                ).await()

                        var uploadFileSuccess = true
                        var bytesUploadedForFile = 0L
                        var lastReportedProgress = -1f
                        val progressThreshold = 0.01f // Update every 1% change

                        RandomAccessFile(absFilePath.pathString, "r").use { fs ->
                            uploadInfo.blockRequests.forEach { blockRequest ->
                                val httpUrl =
                                    buildUrl(
                                        blockRequest.useHttps,
                                        blockRequest.urlHost,
                                        blockRequest.urlPath,
                                    )

                                Timber.i("Uploading to $httpUrl")

                                val byteArray = ByteArray(blockRequest.blockLength)

                                try {
                                    fs.seek(blockRequest.blockOffset)
                                    fs.readFully(byteArray)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to read upload block for ${file.prefixPath}")
                                    uploadFileSuccess = false
                                    uploadBatchSuccess = false
                                    return@forEach
                                }

                                Timber.i("Read ${byteArray.size} byte(s) for block")

                                val mediaType =
                                    if (blockRequest.requestHeaders.any { it.name.equals("Content-Type", ignoreCase = true) }) {
                                        blockRequest.requestHeaders
                                            .first {
                                                it.name.equals(
                                                    "Content-Type",
                                                    ignoreCase = true,
                                                )
                                            }.value
                                            .toMediaTypeOrNull()
                                    } else {
                                        "application/octet-stream".toMediaTypeOrNull()
                                    }

                                val requestBody = byteArray.toRequestBody(mediaType)

                                val headers =
                                    Headers.headersOf(
                                        *blockRequest.requestHeaders
                                            .map { listOf(it.name, it.value) }
                                            .flatten()
                                            .toTypedArray(),
                                    )

                                val request =
                                    Request
                                        .Builder()
                                        .url(httpUrl)
                                        .put(requestBody)
                                        .headers(headers)
                                        .addHeader("Accept", "text/html,*/*;q=0.9")
                                        .addHeader("accept-encoding", "gzip,identity,*;q=0")
                                        .addHeader("accept-charset", "ISO-8859-1,utf-8,*;q=0.7")
                                        .addHeader("user-agent", "Valve/Steam HTTP Client 1.0")
                                        .build()

                                val httpClient = steamInstance.steamClient!!.configuration.httpClient

                                Timber.i("Sending request to ${request.url} using\n$request")

                                try {
                                    withTimeout(SteamService.requestTimeout) {
                                        val response = httpClient.newCall(request).execute()

                                        if (!response.isSuccessful) {
                                            Timber.w(
                                                "Failed to upload part of %s: %s, %s",
                                                file.prefixPath,
                                                response.message,
                                                response?.body.toString(),
                                            )

                                            uploadFileSuccess = false
                                            uploadBatchSuccess = false
                                        } else {
                                            // Update progress after successful block upload
                                            bytesUploadedForFile += blockRequest.blockLength
                                            if (fileSize > 0) {
                                                val currentProgress = (bytesUploadedForFile.toFloat() / fileSize).coerceIn(0f, 1f)
                                                // Only update if progress changed by at least 1% or we're at 100%
                                                if (currentProgress - lastReportedProgress >= progressThreshold || currentProgress >= 1f) {
                                                    onProgress?.invoke("Uploading ${file.filename}", currentProgress)
                                                    lastReportedProgress = currentProgress
                                                }
                                            }
                                        }
                                        response.close()
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Error uploading block")
                                    uploadFileSuccess = false
                                    uploadBatchSuccess = false
                                }
                            }
                        }

                        val commitSuccess =
                            steamCloud
                                .commitFileUpload(
                                    transferSucceeded = uploadFileSuccess,
                                    appId = appInfo.id,
                                    fileSha = file.sha,
                                    filename = cloudUploadName(file),
                                ).await()

                        Timber.i("File ${file.prefixPath} commit success: $commitSuccess")

                        uploadFileSuccess = uploadFileSuccess && commitSuccess
                        if (!commitSuccess) {
                            uploadBatchSuccess = false
                        }

                        if (uploadFileSuccess) {
                            filesUploaded++
                            bytesUploaded += fileSize
                        }
                    }

                    steamCloud
                        .completeAppUploadBatch(
                            appId = appInfo.id,
                            batchId = uploadBatchResponse.batchID,
                            batchEResult = if (uploadBatchSuccess) EResult.OK else EResult.Fail,
                        ).await()

                    if (totalFiles > 0) {
                        onProgress?.invoke("Upload complete", 1.0f)
                    }

                    UserFilesUploadResult(uploadBatchSuccess, uploadBatchResponse.appChangeNumber, filesUploaded, bytesUploaded)
                }
            }

            var syncResult = SyncResult.Success
            var remoteTimestamp = 0L
            var localTimestamp = 0L
            var uploadsRequired = false
            var uploadsCompleted = true

            // sync metrics
            var filesUploaded = 0
            var filesDownloaded = 0
            var filesDeleted = 0
            var filesManaged = 0
            var bytesUploaded = 0L
            var bytesDownloaded = 0L
            var microsecTotal = 0L
            var microsecInitCaches = 0L
            var microsecValidateState = 0L
            var microsecAcLaunch = 0L
            var microsecAcPrepUserFiles = 0L
            var microsecAcExit = 0L
            var microsecDeleteFiles = 0L
            var microsecDownloadFiles = 0L
            var microsecUploadFiles = 0L

            microsecTotal =
                measureTime {
                    val localAppChangeNumber =
                        overrideLocalChangeNumber ?: steamInstance.changeNumbersDao.getByAppId(appInfo.id)?.changeNumber ?: -1

                    val changeNumber = if (localAppChangeNumber >= 0) localAppChangeNumber else 0

                    // retrieve existing user files from local storage first so we can detect missing saves
                    val localUserFilesMap: Map<String, List<UserFileInfo>>
                    val allLocalUserFiles: List<UserFileInfo>

                    microsecInitCaches =
                        measureTime {
                            localUserFilesMap = getLocalUserFilesAsPrefixMap()
                            allLocalUserFiles = localUserFilesMap.map { it.value }.flatten()
                        }.inWholeMicroseconds

                    // If local saves are missing but we have a stored change number, request full file list
                    // (change number 0) instead of a delta, so the cloud returns all files for download
                    val effectiveChangeNumber =
                        if (allLocalUserFiles.isEmpty() && changeNumber > 0) {
                            Timber.w("No local saves found but stored changeNumber=$changeNumber; requesting full file list from cloud")
                            0
                        } else {
                            changeNumber
                        }

                    val appFileListChange = steamCloud.getAppFileListChange(appInfo.id, effectiveChangeNumber).await()

                    val cloudAppChangeNumber = appFileListChange.currentChangeNumber

                    Timber.i(
                        "AppChangeNumber: $localAppChangeNumber -> $cloudAppChangeNumber (requested with changeNumber=$effectiveChangeNumber)",
                    )

                    appFileListChange.printFileChangeList(appInfo)

                    val downloadUserFiles: (CoroutineScope) -> Deferred<PostSyncInfo?> = { parentScope ->
                        parentScope.async {
                            Timber.i("Downloading cloud user files")

                            val remoteUserFiles = fileChangeListToUserFiles(appFileListChange, false)
                            val deletedRemoteUserFiles = fileChangeListToUserFiles(appFileListChange, true)
                            val filesDeletedByCloud =
                                if (appFileListChange.isOnlyDelta) {
                                    deletedRemoteUserFiles
                                } else {
                                    getFilesDiff(remoteUserFiles, allLocalUserFiles).second.filesDeleted
                                }

                            // Download FIRST. Only delete local-only files once every cloud
                            // download has succeeded — a partial download must not leave the
                            // user with both the new bytes missing AND their old saves wiped.
                            val expectedDownloads = remoteUserFiles.size
                            microsecDownloadFiles =
                                measureTime {
                                    val downloadInfo = downloadFiles(appFileListChange, parentScope).await()
                                    filesDownloaded = downloadInfo.filesDownloaded
                                    bytesDownloaded = downloadInfo.bytesDownloaded
                                }.inWholeMicroseconds

                            val downloadsAllSucceeded = filesDownloaded >= expectedDownloads

                            microsecDeleteFiles =
                                measureTime {
                                    if (!downloadsAllSucceeded) {
                                        Timber.w(
                                            "Skipping ${filesDeletedByCloud.size} local delete(s): only " +
                                                "$filesDownloaded/$expectedDownloads cloud files downloaded successfully. " +
                                                "Local saves will be preserved until the next sync.",
                                        )
                                        filesDeleted = 0
                                    } else {
                                        var totalFilesDeleted = 0
                                        filesDeletedByCloud.forEach {
                                            val deleted = Files.deleteIfExists(it.getAbsPath(prefixToPath))
                                            if (deleted) totalFilesDeleted++
                                        }
                                        filesDeleted = totalFilesDeleted
                                    }
                                }.inWholeMicroseconds

                            if (!downloadsAllSucceeded) {
                                syncResult = SyncResult.DownloadFail
                                return@async PostSyncInfo(syncResult)
                            }

                            val updatedLocalFiles: Map<String, List<UserFileInfo>>
                            val hasLocalChanges: Boolean
                            microsecValidateState =
                                measureTime {
                                    updatedLocalFiles = getLocalUserFilesAsPrefixMap()
                                    hasLocalChanges = hasHashConflicts(updatedLocalFiles, appFileListChange)
                                    filesManaged = updatedLocalFiles.size
                                }.inWholeMicroseconds

                            if (hasLocalChanges) {
                                Timber.e(
                                    "Local hashes still differ from cloud after download " +
                                        "(downloaded=$filesDownloaded, expected=$expectedDownloads); aborting",
                                )

                                syncResult = SyncResult.DownloadFail

                                return@async PostSyncInfo(syncResult)
                            }

                            with(steamInstance) {
                                db.withTransaction {
                                    fileChangeListsDao.insert(appInfo.id, updatedLocalFiles.map { it.value }.flatten())
                                    changeNumbersDao.insert(appInfo.id, cloudAppChangeNumber)
                                }
                            }

                            return@async null
                        }
                    }

                    val uploadUserFiles: (CoroutineScope) -> Deferred<Unit> = { parentScope ->
                        parentScope.async {
                            Timber.i("Uploading local user files")

                            val fileChanges =
                                steamInstance.fileChangeListsDao.getByAppId(appInfo.id).let {
                                    val baseline =
                                        it?.userFileInfo
                                            ?: if (localAppChangeNumber < 0) {
                                                fileChangeListToUserFiles(appFileListChange, false)
                                            } else {
                                                emptyList()
                                            }
                                    val result = getFilesDiff(allLocalUserFiles, baseline)

                                    result.second
                                }

                            uploadsRequired =
                                fileChanges.filesCreated.isNotEmpty() ||
                                    fileChanges.filesModified.isNotEmpty() ||
                                    fileChanges.filesDeleted.isNotEmpty()

                            val uploadResult: UserFilesUploadResult

                            microsecUploadFiles =
                                measureTime {
                                    uploadResult = uploadFiles(fileChanges, allLocalUserFiles, parentScope).await()
                                    filesUploaded = uploadResult.filesUploaded
                                    bytesUploaded = uploadResult.bytesUploaded
                                    uploadsCompleted = !uploadsRequired || uploadResult.uploadBatchSuccess
                                }.inWholeMicroseconds

                            filesManaged = allLocalUserFiles.size

                            if (uploadResult.uploadBatchSuccess) {
                                with(steamInstance) {
                                    db.withTransaction {
                                        fileChangeListsDao.insert(appInfo.id, allLocalUserFiles)
                                        changeNumbersDao.insert(appInfo.id, uploadResult.appChangeNumber)
                                    }
                                }
                            } else {
                                syncResult = SyncResult.UpdateFail
                            }
                        }
                    }

                    val remoteHasFiles =
                        appFileListChange.files.any {
                            it.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStatePersisted
                        }
                    val localHasFiles = allLocalUserFiles.isNotEmpty()
                    val forcingDownloadMissingLocal = remoteHasFiles && !localHasFiles && cloudAppChangeNumber >= 0
                    val effectiveLocalAppChangeNumber =
                        if (forcingDownloadMissingLocal) {
                            Timber.w(
                                "Cloud has ${appFileListChange.files.size} file(s) but no local saves; forcing download (changeNumber=$cloudAppChangeNumber)",
                            )
                            -1
                        } else {
                            localAppChangeNumber
                        }

                    if (localAppChangeNumber < 0 && localHasFiles && !remoteHasFiles && preferredSave != SaveLocation.Remote) {
                        // First-sync upload is only safe when the cloud is *genuinely* empty.
                        // If currentChangeNumber > 0, the cloud has a prior history that the
                        // server omitted from this response (transient javasteam/network
                        // glitch). Uploading would silently overwrite real cloud data.
                        // Surface a conflict so the launcher can ask the user explicitly.
                        if (cloudAppChangeNumber > 0) {
                            Timber.w(
                                "Refusing blind upload: cloud changeNumber=$cloudAppChangeNumber but " +
                                    "returned no files. Treating as conflict so launcher can prompt the user.",
                            )
                            when (preferredSave) {
                                SaveLocation.Local -> {
                                    microsecAcExit =
                                        measureTime {
                                            uploadUserFiles(parentScope).await()
                                        }.inWholeMicroseconds
                                }
                                else -> {
                                    syncResult = SyncResult.Conflict
                                    remoteTimestamp = 0L
                                    localTimestamp =
                                        allLocalUserFiles.map { it.timestamp }.maxOrNull() ?: 0L
                                }
                            }
                        } else {
                            Timber.i("No previous Steam cloud baseline and no remote files; uploading existing local saves")
                            microsecAcExit =
                                measureTime {
                                    uploadUserFiles(parentScope).await()
                                }.inWholeMicroseconds
                        }
                    } else if (effectiveLocalAppChangeNumber < cloudAppChangeNumber) {
                        microsecAcLaunch =
                            measureTime {
                                var hasLocalChanges: Boolean

                                microsecAcPrepUserFiles =
                                    measureTime {
                                        hasLocalChanges =
                                            if (forcingDownloadMissingLocal) {
                                                false
                                            } else {
                                                val trackedFiles = steamInstance.fileChangeListsDao.getByAppId(appInfo.id)?.userFileInfo
                                                if (trackedFiles != null) {
                                                    getFilesDiff(allLocalUserFiles, trackedFiles).first
                                                } else {
                                                    localHasFiles
                                                }
                                            }
                                    }.inWholeMicroseconds

                                if (!hasLocalChanges) {
                                    Timber.i("No local changes but new cloud user files")

                                    downloadUserFiles(parentScope).await()?.let {
                                        return@async it
                                    }
                                } else {
                                    Timber.i("Found local changes and new cloud user files, conflict resolution...")

                                    when (preferredSave) {
                                        SaveLocation.Local -> {
                                            uploadUserFiles(parentScope).await()
                                        }

                                        SaveLocation.Remote -> {
                                            downloadUserFiles(parentScope).await()?.let {
                                                return@async it
                                            }
                                        }

                                        SaveLocation.None -> {
                                            syncResult = SyncResult.Conflict
                                            remoteTimestamp = appFileListChange.files.map { it.timestamp.time }.maxOrNull() ?: 0L
                                            localTimestamp = allLocalUserFiles.map { it.timestamp }.maxOrNull() ?: 0L
                                        }
                                    }
                                }
                            }.inWholeMicroseconds
                    } else if (effectiveLocalAppChangeNumber == cloudAppChangeNumber) {
                        microsecAcExit =
                            measureTime {
                                val hasLocalChanges =
                                    steamInstance.fileChangeListsDao
                                        .getByAppId(appInfo.id)
                                        ?.let {
                                            val result = getFilesDiff(allLocalUserFiles, it.userFileInfo)
                                            result.first
                                        } ?: localHasFiles

                                if (hasLocalChanges) {
                                    Timber.i("Found local changes and no new cloud user files")

                                    uploadUserFiles(parentScope).await()
                                } else {
                                    Timber.i("No local changes and no new cloud user files, doing nothing...")

                                    syncResult = SyncResult.UpToDate
                                }
                            }.inWholeMicroseconds
                    } else {
                        Timber.e("Local change number greater than cloud $localAppChangeNumber > $cloudAppChangeNumber")

                        syncResult = SyncResult.UnknownFail
                    }
                }.inWholeMicroseconds

            val microsecBuildSyncList =
                (microsecTotal - (microsecInitCaches + microsecValidateState + microsecAcLaunch + microsecAcExit))
                    .coerceAtLeast(
                        0L,
                    )

            steamCloud.appCloudSyncStats(
                appId = appInfo.id,
                platformType = EPlatformType.Android64,
                blockingAppLaunch = microsecAcLaunch > 0,
                filesUploaded = filesUploaded,
                filesDownloaded = filesDownloaded,
                filesDeleted = filesDeleted,
                bytesUploaded = bytesUploaded,
                bytesDownloaded = bytesDownloaded,
                microsecTotal = microsecTotal,
                microsecInitCaches = microsecInitCaches,
                microsecValidateState = microsecValidateState,
                microsecAcLaunch = microsecAcLaunch,
                microsecAcPrepUserFiles = microsecAcPrepUserFiles,
                microsecAcExit = microsecAcExit,
                microsecBuildSyncList = microsecBuildSyncList,
                microsecDeleteFiles = microsecDeleteFiles,
                microsecDownloadFiles = microsecDownloadFiles,
                microsecUploadFiles = microsecUploadFiles,
                filesManaged = filesManaged,
            )

            postSyncInfo =
                PostSyncInfo(
                    syncResult = syncResult,
                    remoteTimestamp = remoteTimestamp,
                    localTimestamp = localTimestamp,
                    uploadsRequired = uploadsRequired,
                    uploadsCompleted = uploadsCompleted,
                    filesUploaded = filesUploaded,
                    filesDownloaded = filesDownloaded,
                    filesDeleted = filesDeleted,
                    filesManaged = filesManaged,
                    bytesUploaded = bytesUploaded,
                    bytesDownloaded = bytesDownloaded,
                    microsecTotal = microsecTotal,
                    microsecInitCaches = microsecInitCaches,
                    microsecValidateState = microsecValidateState,
                    microsecAcLaunch = microsecAcLaunch,
                    microsecAcPrepUserFiles = microsecAcPrepUserFiles,
                    microsecAcExit = microsecAcExit,
                    microsecBuildSyncList = microsecBuildSyncList,
                    microsecDeleteFiles = microsecDeleteFiles,
                    microsecDownloadFiles = microsecDownloadFiles,
                    microsecUploadFiles = microsecUploadFiles,
                )

            postSyncInfo
        }

    private fun AppFileChangeList.printFileChangeList(appInfo: SteamApp) {
        with(this) {
            Timber.i(
                "GetAppFileListChange(${appInfo.id}):" +
                    "\n\tTotal Files: ${files.size}" +
                    "\n\tCurrent Change Number: $currentChangeNumber" +
                    "\n\tIs Only Delta: $isOnlyDelta" +
                    "\n\tApp BuildID Hwm: $appBuildIDHwm" +
                    "\n\tPath Prefixes: \n\t\t${pathPrefixes.joinToString("\n\t\t")}" +
                    "\n\tMachine Names: \n\t\t${machineNames.joinToString("\n\t\t")}" +
                    files.joinToString {
                        "\n\t${it.filename}:" +
                            "\n\t\tshaFile: ${it.shaFile}" +
                            "\n\t\ttimestamp: ${it.timestamp}" +
                            "\n\t\trawFileSize: ${it.rawFileSize}" +
                            "\n\t\tpersistState: ${it.persistState}" +
                            "\n\t\tplatformsToSync: ${it.platformsToSync}" +
                            "\n\t\tpathPrefixIndex: ${it.pathPrefixIndex}" +
                            "\n\t\tmachineNameIndex: ${it.machineNameIndex}"
                    },
            )
        }
    }
}
