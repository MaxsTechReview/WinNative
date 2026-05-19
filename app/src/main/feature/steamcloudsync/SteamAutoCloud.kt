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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.stream.Collectors
import kotlin.io.path.pathString
import kotlin.time.measureTime

/**
 * [Steam Auto Cloud](https://partner.steamgames.com/doc/features/cloud#steam_auto-cloud)
 *
 * Phase 9: this is now built entirely on the in-house C++ WN-Steam-Client —
 * the cloud file list, downloads and uploads all flow through
 * [WnSteamSession]. No JavaSteam `SteamCloud` handler is involved.
 */
object SteamAutoCloud {
    private const val MAX_CLOUD_FILE_SIZE_BYTES = 100L * 1024L * 1024L
    private const val DOWNLOAD_TMP_SUFFIX = ".steamtmp"

    // CCloud_AppFileInfo persist_state values (ECloudStoragePersistState).
    private const val PERSIST_STATE_PERSISTED = 0
    private const val PERSIST_STATE_DELETED = 2

    /**
     * Per-Steam-protocol content-divergence check used by the launch-time conflict probe.
     *
     * Returns `true` if ANY persisted cloud file is missing locally OR has a differing
     * size/SHA-1. Returns `false` only when every cloud file is present locally with
     * matching size and SHA — the canonical "no conflict" condition.
     *
     * Optimizations:
     *  - Size comparison is a free pre-filter (no SHA on obvious size delta).
     *  - `Sequence.any { … }` short-circuits on the first divergence.
     *  - We compute the local SHA only on files whose size already matches.
     */
    fun cloudContentDiffersFromLocal(
        response: CloudFileChangeList,
        prefixToPath: (String) -> String,
    ): Boolean {
        return response.files
            .asSequence()
            .filter { it.persistState == PERSIST_STATE_PERSISTED }
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
                if (localSize != cloudFile.rawFileSize) {
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
     * Mirrors the simpler half of [getFullFilePath] (a closure inside [syncUserFiles])
     * without the full closure context. Handles the common cases — SteamUserData-rooted
     * files and `%GameInstall%`-prefixed filenames — and falls back to a DEFAULT-rooted
     * path for unrecognized prefixes.
     */
    private fun resolveLocalPathForCloudFile(
        cloudFile: CloudFileInfo,
        response: CloudFileChangeList,
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

    /**
     * A remote Steam Cloud file entry — the in-house replacement for
     * JavaSteam's `AppFileInfo`. [timestamp] is millis; [persistState] is a
     * raw `ECloudStoragePersistState` code (see [PERSIST_STATE_PERSISTED]).
     */
    data class CloudFileInfo(
        val filename: String,
        val shaFile: ByteArray,
        val timestamp: Long,
        val rawFileSize: Long,
        val persistState: Int,
        val pathPrefixIndex: Int,
        val machineNameIndex: Int,
    ) {
        /** True for a live file (persistState 0); false for forgotten/deleted. */
        val isPersisted: Boolean get() = persistState == PERSIST_STATE_PERSISTED
    }

    /**
     * A remote Steam Cloud changelist — the in-house replacement for
     * JavaSteam's `AppFileChangeList`. The C++ `getCloudFileList` always
     * requests the full snapshot (synced_change_number 0), so [isOnlyDelta]
     * is always false and deletions are derived by diffing against local.
     */
    data class CloudFileChangeList(
        val currentChangeNumber: Long,
        val pathPrefixes: List<String>,
        val machineNames: List<String>,
        val files: List<CloudFileInfo>,
    ) {
        val isOnlyDelta: Boolean = false
    }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.isEmpty() || hex.length % 2 != 0) return ByteArray(0)
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

    /**
     * Parse the JSON object string returned by `WnSteamSession.getCloudFileList`
     * into the in-house [CloudFileChangeList]. The native `timestamp` is in
     * unix seconds — scaled here to the millis the rest of the sync expects.
     */
    fun parseCloudFileChangeList(json: String): CloudFileChangeList {
        val obj = JSONObject(json)
        val prefixes =
            obj.optJSONArray("pathPrefixes")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()
        val machineNames =
            obj.optJSONArray("machineNames")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()
        val filesArr = obj.optJSONArray("files")
        val files =
            if (filesArr == null) {
                emptyList()
            } else {
                List(filesArr.length()) { i ->
                    val f = filesArr.getJSONObject(i)
                    CloudFileInfo(
                        filename = f.optString("fileName"),
                        shaFile = hexToBytes(f.optString("sha")),
                        timestamp = f.optLong("timestamp", 0L) * 1000L,
                        rawFileSize = f.optLong("size", 0L),
                        persistState = f.optInt("persistState", 0),
                        pathPrefixIndex = f.optInt("pathPrefixIndex", 0),
                        machineNameIndex = f.optInt("machineNameIndex", 0),
                    )
                }
            }
        return CloudFileChangeList(
            currentChangeNumber = obj.optLong("currentChangeNumber", 0L),
            pathPrefixes = prefixes,
            machineNames = machineNames,
            files = files,
        )
    }

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

            val getPathTypePairs: (CloudFileChangeList) -> List<Pair<String, String>> = { fileList ->
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

            val convertPrefixes: (CloudFileChangeList) -> List<String> = { fileList ->
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

            val getFilePrefix: (CloudFileInfo, CloudFileChangeList) -> String = { file, fileList ->
                if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                    Paths.get(fileList.pathPrefixes[file.pathPrefixIndex]).pathString
                } else {
                    ""
                }
            }

            val getFileRemotePath: (CloudFileInfo, CloudFileChangeList) -> RemotePath = { file, fileList ->
                if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                    parseRemotePath(fileList.pathPrefixes[file.pathPrefixIndex])
                } else if (file.filename.startsWith("%${PathType.GameInstall.name}%")) {
                    RemotePath(PathType.GameInstall, "")
                } else {
                    RemotePath(PathType.DEFAULT, "")
                }
            }

            val getFilePrefixPath: (CloudFileInfo, CloudFileChangeList) -> String = { file, fileList ->
                Paths.get(getFilePrefix(file, fileList), file.filename).pathString
            }

            val getFullFilePath: (CloudFileInfo, CloudFileChangeList) -> Path? = getFullFilePath@{ file, fileList ->
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

            val hasHashConflicts: (Map<String, List<UserFileInfo>>, CloudFileChangeList) -> Boolean =
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

            val fileChangeListToUserFiles: (CloudFileChangeList, Boolean) -> List<UserFileInfo> = { appFileListChange, includeDeleted ->
                appFileListChange.files
                    .filter {
                        if (includeDeleted) {
                            it.persistState == PERSIST_STATE_DELETED
                        } else {
                            it.persistState == PERSIST_STATE_PERSISTED
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
                            timestamp = it.timestamp,
                            sha = it.shaFile,
                        )
                    }
            }

            val downloadFiles: (CloudFileChangeList, CoroutineScope) -> Deferred<UserFilesDownloadResult> = { fileList, parentScope ->
                parentScope.async {
                    var filesDownloaded = 0
                    var bytesDownloaded = 0L
                    val filesToDownload =
                        fileList.files.filter { it.persistState == PERSIST_STATE_PERSISTED }
                    val totalFiles = filesToDownload.size

                    filesToDownload.forEach { file ->
                        val prefixedPath = getFilePrefixPath(file, fileList)
                        val remotePathForFile = getFileRemotePath(file, fileList)
                        val actualFilePath = getFullFilePath(file, fileList)
                        if (actualFilePath == null) {
                            Timber.w("Skipping download for unsupported Steam cloud path $prefixedPath")
                            return@forEach
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
                            return@forEach
                        }

                        Timber.i("$prefixedPath -> $actualFilePath")
                        onProgress?.invoke("Downloading ${file.filename}", -1f)

                        // Fetch the file body via the C++ WN-Steam-Client.
                        // downloadCloudFile resolves the URL, performs the
                        // HTTP(S) GET (replaying the server-supplied headers)
                        // and — when Steam served it compressed — unzips the
                        // body, so we always get ready-to-write bytes.
                        val wnBytes =
                            SteamService.withWnSession {
                                it.downloadCloudFile(appInfo.id, prefixedPath)
                            }
                        if (wnBytes == null) {
                            Timber.w(
                                "Cloud download failed for ${file.filename} ($prefixedPath); preserving existing local file",
                            )
                            return@forEach
                        }

                        // Atomic write: stream into a sibling .steamtmp file, fsync,
                        // then rename into place. Prevents a truncated/partial save
                        // from clobbering a good local file if the write aborts.
                        val tmpPath =
                            actualFilePath.resolveSibling(
                                actualFilePath.fileName.toString() + DOWNLOAD_TMP_SUFFIX,
                            )
                        try {
                            actualFilePath.parent?.let { Files.createDirectories(it) }
                            Files.deleteIfExists(tmpPath)
                            FileOutputStream(tmpPath.toString()).use { fs ->
                                fs.write(wnBytes)
                                // Force bytes to disk before the rename so a crash
                                // between move and process exit can't leave the
                                // destination pointing at unsynced pages.
                                try {
                                    fs.fd.sync()
                                } catch (e: Exception) {
                                    Timber.w(e, "fsync failed for %s; continuing", tmpPath)
                                }
                            }
                            // ATOMIC_MOVE is not portable when combined with
                            // REPLACE_EXISTING, so try it alone first; on Android's
                            // POSIX FS rename(2) atomically replaces. Fall back to
                            // plain REPLACE_EXISTING if the FS rejects ATOMIC_MOVE.
                            try {
                                Files.move(tmpPath, actualFilePath, StandardCopyOption.ATOMIC_MOVE)
                            } catch (_: Exception) {
                                Files.move(tmpPath, actualFilePath, StandardCopyOption.REPLACE_EXISTING)
                            }
                            filesDownloaded++
                            bytesDownloaded += wnBytes.size.toLong()
                            onProgress?.invoke("Downloading ${file.filename}", 1f)
                            Timber.i(
                                "cloud restore via wn-steam-client: ${file.filename} (${wnBytes.size} bytes)",
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "Could not write ${file.filename}; preserving existing local file")
                            try {
                                Files.deleteIfExists(tmpPath)
                            } catch (_: Exception) {
                                // best-effort
                            }
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

                    // The whole batch runs on the C++ WN-Steam-Client: begin →
                    // per-file (ClientBeginFileUpload + HTTP PUT + ClientCommit
                    // FileUpload) → CompleteAppUploadBatch. withWnSession returns
                    // null only if no logged-on session could be obtained.
                    val wnUploadResult =
                        SteamService.withWnSession { session ->
                            val batch =
                                session.beginCloudUploadBatch(
                                    appInfo.id,
                                    filesToUpload.map { it.first },
                                    filesToDelete,
                                    clientId,
                                ) ?: return@withWnSession null
                            var allOk = true
                            var uploaded = 0
                            var bytes = 0L
                            filesToUpload.forEach { (cloudName, file) ->
                                val data =
                                    try {
                                        Files.readAllBytes(file.getAbsPath(prefixToPath))
                                    } catch (e: Exception) {
                                        Timber.w(e, "wn cloud upload: cannot read ${file.prefixPath}")
                                        allOk = false
                                        return@forEach
                                    }
                                onProgress?.invoke("Uploading ${file.filename}", 0f)
                                val shaHex = file.sha.joinToString("") { "%02x".format(it) }
                                val ok =
                                    session.uploadCloudFile(
                                        appInfo.id,
                                        cloudName,
                                        data,
                                        shaHex,
                                        file.timestamp / 1000L, // millis → unix seconds
                                        batch.batchId,
                                    )
                                if (ok) {
                                    uploaded++
                                    bytes += data.size.toLong()
                                    onProgress?.invoke("Uploading ${file.filename}", 1f)
                                } else {
                                    allOk = false
                                }
                            }
                            val completed =
                                session.completeCloudUploadBatch(
                                    appInfo.id,
                                    batch.batchId,
                                    if (allOk) 1 else 2, // 1 = EResult.OK, 2 = Fail
                                )
                            Timber.i(
                                "cloud upload via wn-steam-client: batch=${batch.batchId} " +
                                    "uploaded=$uploaded/${filesToUpload.size} ok=$allOk completed=$completed",
                            )
                            UserFilesUploadResult(allOk && completed, batch.appChangeNumber, uploaded, bytes)
                        }

                    if (wnUploadResult != null) {
                        if (totalFiles > 0) {
                            onProgress?.invoke("Upload complete", 1.0f)
                        }
                        return@async wnUploadResult
                    }

                    Timber.e("Steam cloud upload failed: no logged-on session for app ${appInfo.id}")
                    UserFilesUploadResult(false, 0, 0, 0)
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

                    // retrieve existing user files from local storage first so we can detect missing saves
                    val localUserFilesMap: Map<String, List<UserFileInfo>>
                    val allLocalUserFiles: List<UserFileInfo>

                    microsecInitCaches =
                        measureTime {
                            localUserFilesMap = getLocalUserFilesAsPrefixMap()
                            allLocalUserFiles = localUserFilesMap.map { it.value }.flatten()
                        }.inWholeMicroseconds

                    // Fetch the full remote cloud file list via the C++
                    // WN-Steam-Client (Cloud.GetAppFileChangelist). The native
                    // call always requests the full snapshot, so deletions are
                    // derived by diffing the response against local state.
                    val wnFileListJson =
                        SteamService.withWnSession {
                            withContext(Dispatchers.IO) { it.getCloudFileList(appInfo.id) }
                        }
                    if (wnFileListJson == null) {
                        Timber.e("wn-steam-client: could not fetch cloud file list for app ${appInfo.id}")
                        syncResult = SyncResult.UnknownFail
                        return@async PostSyncInfo(syncResult)
                    }

                    val appFileListChange =
                        try {
                            parseCloudFileChangeList(wnFileListJson)
                        } catch (e: Exception) {
                            Timber.e(e, "wn-steam-client: malformed cloud file list for app ${appInfo.id}")
                            syncResult = SyncResult.UnknownFail
                            return@async PostSyncInfo(syncResult)
                        }

                    val cloudAppChangeNumber = appFileListChange.currentChangeNumber

                    Timber.i("AppChangeNumber: $localAppChangeNumber -> $cloudAppChangeNumber")

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
                        appFileListChange.files.any { it.persistState == PERSIST_STATE_PERSISTED }
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
                        // server omitted from this response (transient network glitch).
                        // Uploading would silently overwrite real cloud data. Surface a
                        // conflict so the launcher can ask the user explicitly.
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
                                            remoteTimestamp = appFileListChange.files.map { it.timestamp }.maxOrNull() ?: 0L
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

    private fun CloudFileChangeList.printFileChangeList(appInfo: SteamApp) {
        Timber.i(
            "GetAppFileListChange(${appInfo.id}):" +
                "\n\tTotal Files: ${files.size}" +
                "\n\tCurrent Change Number: $currentChangeNumber" +
                "\n\tIs Only Delta: $isOnlyDelta" +
                "\n\tPath Prefixes: \n\t\t${pathPrefixes.joinToString("\n\t\t")}" +
                "\n\tMachine Names: \n\t\t${machineNames.joinToString("\n\t\t")}" +
                files.joinToString {
                    "\n\t${it.filename}:" +
                        "\n\t\tshaFile: ${it.shaFile.joinToString(", ")}" +
                        "\n\t\ttimestamp: ${it.timestamp}" +
                        "\n\t\trawFileSize: ${it.rawFileSize}" +
                        "\n\t\tpersistState: ${it.persistState}" +
                        "\n\t\tpathPrefixIndex: ${it.pathPrefixIndex}" +
                        "\n\t\tmachineNameIndex: ${it.machineNameIndex}"
                },
        )
    }
}
