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
 * this is now built entirely on the in-house C++ WN-Steam-Client —
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
     *  - `Sequence.any {  }` short-circuits on the first divergence.
     *  - We compute the local SHA only on files whose size already matches.
     */
    /**
     * Group persisted cloud files by the local path they resolve to.
     *
     * Steam Cloud can hold DUPLICATE / stale entries for one logical save file under different
     * prefixes or filenames — e.g. Monster Hunter Rise lists both `win64_save/X` and
     * `%SteamUserData%win64_save/X` (plus token-in-filename leftovers from earlier bad uploads),
     * all mapping to the same on-disk file. Collapsing by resolved local path lets the downloader
     * fetch each file once (any working variant satisfies it) and the conflict/verification checks
     * treat a path as in-sync when the local file matches ANY variant — instead of failing the
     * whole sync on a redundant phantom entry the CDN no longer has.
     *
     * Unresolvable entries (null local path) are dropped: they're malformed leftovers, not real
     * saves, and must not wedge the sync into a permanent "conflict".
     */
    private fun groupPersistedCloudFilesByLocalPath(
        response: CloudFileChangeList,
        prefixToPath: (String) -> String,
        cloudRouting: CloudPathRouting?,
    ): LinkedHashMap<Path, MutableList<CloudFileInfo>> {
        val groups = LinkedHashMap<Path, MutableList<CloudFileInfo>>()
        response.files
            .asSequence()
            .filter { it.persistState == PERSIST_STATE_PERSISTED }
            .forEach { cloudFile ->
                val local =
                    resolveCloudFileLocalPath(cloudFile, response, prefixToPath, cloudRouting)
                        ?.toAbsolutePath()
                        ?.normalize()
                if (local == null) {
                    Timber.d("ConflictProbe: cloud file %s has no resolvable local path → ignoring", cloudFile.filename)
                    return@forEach
                }
                groups.getOrPut(local) { mutableListOf() }.add(cloudFile)
            }
        return groups
    }

    /** True if a local file matches ANY of the cloud variants mapped to its path (size + SHA-1). */
    private fun localMatchesAnyVariant(localPath: Path, variants: List<CloudFileInfo>): Boolean {
        if (!Files.exists(localPath)) return false
        val localSize = runCatching { Files.size(localPath) }.getOrNull() ?: return false
        val localSha = runCatching { streamingSha(localPath) }.getOrNull() ?: return false
        return variants.any { it.rawFileSize == localSize && localSha.contentEquals(it.shaFile) }
    }

    fun cloudContentDiffersFromLocal(
        response: CloudFileChangeList,
        prefixToPath: (String) -> String,
        appInfo: SteamApp? = null,
    ): Boolean {
        val cloudRouting = appInfo?.let { buildCloudPathRouting(it, prefixToPath) }
        val groups = groupPersistedCloudFilesByLocalPath(response, prefixToPath, cloudRouting)
        return groups.any { (localPath, variants) ->
            val matches = localMatchesAnyVariant(localPath, variants)
            if (!matches) {
                Timber.d("ConflictProbe: %s does not match any cloud variant → diverges", localPath)
            }
            !matches
        }
    }

    // Delegates to the canonical resolver so the conflict check and remotecache.vdf writer
    // resolve a cloud file to the EXACT path the downloader writes it to.
    private fun resolveLocalPathForCloudFile(
        cloudFile: CloudFileInfo,
        response: CloudFileChangeList,
        prefixToPath: (String) -> String,
        cloudRouting: CloudPathRouting?,
    ): Path? = resolveCloudFileLocalPath(cloudFile, response, prefixToPath, cloudRouting)

    private fun steamUserDataSubpath(prefix: String): String? {
        val normalized = prefix.replace('\\', '/').trimStart('/')
        if (normalized.equals("remote", ignoreCase = true) || normalized.equals("remote/", ignoreCase = true)) {
            return ""
        }
        if (normalized.startsWith("remote/", ignoreCase = true)) {
            return normalized.substring("remote/".length).trimStart('/')
        }
        return null
    }

    private fun steamUserDataCacheName(
        cloudFile: CloudFileInfo,
        response: CloudFileChangeList,
    ): String? {
        val prefix =
            if (cloudFile.pathPrefixIndex >= 0 && cloudFile.pathPrefixIndex < response.pathPrefixes.size) {
                response.pathPrefixes[cloudFile.pathPrefixIndex]
            } else {
                ""
            }

        val token = "%${PathType.SteamUserData.name}%"
        val filename =
            if (cloudFile.filename.startsWith(token)) {
                cloudFile.filename.removePrefix(token).trimStart('/', '\\')
            } else {
                cloudFile.filename
            }.replace('\\', '/').trimStart('/')

        val subpath =
            steamUserDataSubpath(prefix)
                ?: if (prefix.contains(token, ignoreCase = true)) {
                    prefix.substringAfter(token).trimStart('/', '\\')
                } else if (prefix.isBlank()) {
                    ""
                } else {
                    return null
                }

        val normalizedSubpath = subpath.replace('\\', '/').trim('/', '\\')
        val cacheName =
            if (normalizedSubpath.isEmpty()) {
                filename
            } else {
                "$normalizedSubpath/$filename"
            }.trimStart('/')

        if (cacheName.isBlank() || cacheName.contains("..")) return null
        return cacheName
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

    data class CloudFileChangeList(
        val currentChangeNumber: Long,
        val pathPrefixes: List<String>,
        val machineNames: List<String>,
        val files: List<CloudFileInfo>,
    ) {
        val isOnlyDelta: Boolean = false
    }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.isEmpty()) return ByteArray(0)
        if (hex.length % 2 != 0 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            Timber.w("hexToBytes: malformed input (len=%d, sample='%s') — returning empty array",
                hex.length, hex.take(16))
            return ByteArray(0)
        }
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

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
                    val cloudPrefix = substituteSteamIds("${cloudToken(pattern.uploadRoot)}${pattern.uploadPath}").trimEnd('/', '\\')
                    cloudPrefix to Paths.get(prefixToPath(pattern.root.name), pattern.substitutedPath).pathString
                }

        return CloudPathRouting(rootAliases, exactPrefixTargets)
    }

    // ── Canonical cloud-file → local-path resolution ──
    //
    // SINGLE source of truth shared by the downloader, the post-download hash verification,
    // the launch/probe conflict checks, and the remotecache.vdf writer. Previously the
    // downloader and the conflict check used two different mappings, so for games that use
    // uploadRoot/uploadPath (rootoverrides) aliases, ROOT_MOD prefixes, or %GameInstall%
    // routing the download landed in path A while the check read path B — leaving the save
    // "different from cloud" forever and re-prompting the conflict on every launch even after
    // the user picked "Use Cloud". Resolving every consumer through one function guarantees we
    // verify exactly the file we wrote.

    private fun pathTypePairsFor(
        fileList: CloudFileChangeList,
        prefixToPath: (String) -> String,
        cloudRouting: CloudPathRouting,
    ): List<Pair<String, String>> =
        fileList.pathPrefixes
            .map { prefix ->
                var matchResults = findPlaceholderWithin(prefix).map { it.value }.toList()
                val bare = if (prefix.startsWith("ROOT_MOD")) listOf("ROOT_MOD") else emptyList()
                if (matchResults.isEmpty()) {
                    matchResults = List(1) { PathType.DEFAULT.name }
                }
                matchResults + bare
            }.flatten()
            .distinct()
            .map { placeholder ->
                val localRootName = cloudRouting.localRootByCloudToken[placeholder] ?: placeholder
                val root = PathType.from(localRootName)
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

    private fun parseRemotePath(prefix: String): RemotePath {
        val steamUserDataSubpath = steamUserDataSubpath(prefix)
        if (steamUserDataSubpath != null) {
            return RemotePath(PathType.SteamUserData, steamUserDataSubpath)
        }
        val token =
            when {
                prefix.startsWith("ROOT_MOD", ignoreCase = true) -> "ROOT_MOD"
                else -> findPlaceholderWithin(prefix).firstOrNull()?.value
            }
        val root = token?.let { PathType.from(it) } ?: PathType.DEFAULT
        val withoutRoot =
            when {
                token == null -> prefix
                prefix.startsWith("ROOT_MOD", ignoreCase = true) -> prefix.substring("ROOT_MOD".length)
                else -> prefix.removePrefix(token)
            }.trimStart('/', '\\')
        return RemotePath(root, if (withoutRoot == ".") "" else withoutRoot)
    }

    private fun convertPrefixes(
        fileList: CloudFileChangeList,
        prefixToPath: (String) -> String,
        cloudRouting: CloudPathRouting,
    ): List<String> {
        val pathTypePairs = pathTypePairsFor(fileList, prefixToPath, cloudRouting)
        return fileList.pathPrefixes.map { prefix ->
            steamUserDataSubpath(prefix)?.let { subpath ->
                return@map if (subpath.isEmpty()) {
                    prefixToPath(PathType.SteamUserData.name)
                } else {
                    Paths.get(prefixToPath(PathType.SteamUserData.name), subpath).toString()
                }
            }

            var modified = prefix
            val prefixContainsNoPlaceholder = findPlaceholderWithin(prefix).none()
            if (prefixContainsNoPlaceholder) {
                modified = Paths.get(PathType.DEFAULT.name, prefix).pathString
            }
            pathTypePairs.forEach {
                modified = modified.replace(it.first, it.second)
            }
            if (modified == prefix) {
                modified = Paths.get(prefixToPath(PathType.DEFAULT.name), modified).toString()
            }
            modified
        }
    }

    /**
     * Resolve a cloud file to the absolute local path it belongs at — the exact path the
     * downloader writes to. This is the canonical mapping; all conflict/verification consumers
     * must route through it so we never check a different location than we wrote.
     */
    private fun resolveCloudFileLocalPath(
        file: CloudFileInfo,
        fileList: CloudFileChangeList,
        prefixToPath: (String) -> String,
        cloudRouting: CloudPathRouting?,
    ): Path? {
        val routing = cloudRouting ?: CloudPathRouting(emptyMap(), emptyMap())

        // Steam sometimes returns prefix="" and filename="%GameInstall%save0.dat" instead of
        // splitting correctly — route the embedded token the same way the downloader does.
        val gameInstallPrefix = "%${PathType.GameInstall.name}%"
        if (file.filename.startsWith(gameInstallPrefix)) {
            val stripped = file.filename.removePrefix(gameInstallPrefix).trimStart('/', '\\')
            return runCatching {
                routing.localPathByCloudPrefix[gameInstallPrefix]?.let { Paths.get(it, stripped) }
                    ?: Paths.get(prefixToPath(PathType.GameInstall.name), stripped)
            }.getOrNull()
        }

        val defaultConvertedPrefixes = convertPrefixes(fileList, prefixToPath, routing)
        val convertedPrefixes =
            fileList.pathPrefixes.mapIndexed { index, prefix ->
                routing.localPathByCloudPrefix[prefix.trimEnd('/')]
                    ?: defaultConvertedPrefixes.getOrElse(index) { prefixToPath(PathType.DEFAULT.name) }
            }

        return runCatching {
            if (file.pathPrefixIndex in fileList.pathPrefixes.indices) {
                Paths.get(convertedPrefixes[file.pathPrefixIndex], file.filename)
            } else {
                // No referenced prefix → default path.
                Paths.get(prefixToPath(PathType.DEFAULT.name), file.filename)
            }
        }.getOrNull()
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

    private fun ByteArray.toLowerHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun vdfEscape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun writeSteamRemoteCacheVdf(
        appInfo: SteamApp,
        response: CloudFileChangeList,
        prefixToPath: (String) -> String,
        cloudRouting: CloudPathRouting?,
    ) {
        val steamUserDataRemoteRoot =
            runCatching {
                Paths.get(prefixToPath(PathType.SteamUserData.name)).toAbsolutePath().normalize()
            }.getOrNull() ?: return
        val appUserDataDir = steamUserDataRemoteRoot.parent ?: return

        val entries =
            response.files
                .asSequence()
                .filter { it.persistState == PERSIST_STATE_PERSISTED }
                .mapNotNull { cloudFile ->
                    val cacheName = steamUserDataCacheName(cloudFile, response) ?: return@mapNotNull null
                    val localPath =
                        resolveLocalPathForCloudFile(cloudFile, response, prefixToPath, cloudRouting)
                            ?.toAbsolutePath()
                            ?.normalize()
                            ?: return@mapNotNull null
                    if (!localPath.startsWith(steamUserDataRemoteRoot) || !Files.exists(localPath)) {
                        return@mapNotNull null
                    }
                    val size = runCatching { Files.size(localPath) }.getOrNull() ?: return@mapNotNull null
                    val timestampSeconds =
                        if (cloudFile.timestamp > 0L) {
                            cloudFile.timestamp / 1000L
                        } else {
                            runCatching { Files.getLastModifiedTime(localPath).toMillis() / 1000L }
                                .getOrDefault(0L)
                        }
                    val sha =
                        if (cloudFile.shaFile.isNotEmpty()) {
                            cloudFile.shaFile.toLowerHex()
                        } else {
                            runCatching { streamingSha(localPath).toLowerHex() }.getOrDefault("")
                        }
                    RemoteCacheEntry(cacheName, size, timestampSeconds, sha)
                }
                .toList()

        if (entries.isEmpty()) return

        writeSteamRemoteCacheVdfEntries(appInfo.id, response.currentChangeNumber, appUserDataDir, entries)
    }

    private data class RemoteCacheEntry(
        val name: String,
        val size: Long,
        val timestampSeconds: Long,
        val sha: String,
    )

    private fun writeSteamRemoteCacheVdfFromLocalFiles(
        appInfo: SteamApp,
        changeNumber: Long,
        userFiles: List<UserFileInfo>,
        prefixToPath: (String) -> String,
    ) {
        val steamUserDataRemoteRoot =
            runCatching {
                Paths.get(prefixToPath(PathType.SteamUserData.name)).toAbsolutePath().normalize()
            }.getOrNull() ?: return
        val appUserDataDir = steamUserDataRemoteRoot.parent ?: return

        val entries =
            userFiles
                .asSequence()
                .filter { it.cloudRoot == PathType.SteamUserData }
                .mapNotNull { file ->
                    val localPath =
                        runCatching { file.getAbsPath(prefixToPath).toAbsolutePath().normalize() }
                            .getOrNull()
                            ?: return@mapNotNull null
                    if (!localPath.startsWith(steamUserDataRemoteRoot) || !Files.exists(localPath)) {
                        return@mapNotNull null
                    }
                    val cloudSubdir =
                        file.cloudPath
                            .takeUnless { it.isBlank() || it == "." }
                            ?.replace('\\', '/')
                            ?.trim('/', '\\')
                            .orEmpty()
                    val cacheName =
                        if (cloudSubdir.isEmpty()) {
                            file.filename
                        } else {
                            "$cloudSubdir/${file.filename}"
                        }.replace('\\', '/').trimStart('/')
                    if (cacheName.isBlank() || cacheName.contains("..")) return@mapNotNull null
                    val size = runCatching { Files.size(localPath) }.getOrNull() ?: return@mapNotNull null
                    RemoteCacheEntry(
                        name = cacheName,
                        size = size,
                        timestampSeconds = file.timestamp / 1000L,
                        sha = file.sha.toLowerHex(),
                    )
                }
                .toList()

        if (entries.isEmpty()) return

        writeSteamRemoteCacheVdfEntries(appInfo.id, changeNumber, appUserDataDir, entries)
    }

    private fun writeSteamRemoteCacheVdfEntries(
        appId: Int,
        changeNumber: Long,
        appUserDataDir: Path,
        entries: List<RemoteCacheEntry>,
    ) {
        val content =
            buildString {
                append('"').append(appId).append('"').append('\n')
                append("{\n")
                append("\t\"ChangeNumber\"\t\t\"").append(changeNumber).append("\"\n")
                append("\t\"OSType\"\t\t\"0\"\n")
                entries.forEach { entry ->
                    append("\t\"").append(vdfEscape(entry.name)).append("\"\n")
                    append("\t{\n")
                    append("\t\t\"root\"\t\t\"0\"\n")
                    append("\t\t\"size\"\t\t\"").append(entry.size).append("\"\n")
                    append("\t\t\"localtime\"\t\t\"").append(entry.timestampSeconds).append("\"\n")
                    append("\t\t\"time\"\t\t\"").append(entry.timestampSeconds).append("\"\n")
                    append("\t\t\"remotetime\"\t\t\"").append(entry.timestampSeconds).append("\"\n")
                    append("\t\t\"sha\"\t\t\"").append(entry.sha).append("\"\n")
                    append("\t\t\"syncstate\"\t\t\"1\"\n")
                    append("\t\t\"persiststate\"\t\t\"0\"\n")
                    append("\t\t\"platformstosync2\"\t\t\"-1\"\n")
                    append("\t}\n")
                }
                append("}\n")
            }

        val remoteCacheFile = appUserDataDir.resolve("remotecache.vdf")
        val tmp = appUserDataDir.resolve("remotecache.vdf.tmp")
        runCatching {
            Files.createDirectories(appUserDataDir)
            Files.write(tmp, content.toByteArray(Charsets.UTF_8))
            try {
                Files.move(tmp, remoteCacheFile, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, remoteCacheFile, StandardCopyOption.REPLACE_EXISTING)
            }
            Timber.i(
                "Wrote Steam remotecache.vdf for app $appId: ${entries.size} file(s) at $remoteCacheFile",
            )
        }.onFailure { e ->
            runCatching { Files.deleteIfExists(tmp) }
            Timber.w(e, "Failed writing Steam remotecache.vdf for app $appId")
        }
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

            val getFullFilePath: (CloudFileInfo, CloudFileChangeList) -> Path? = { file, fileList ->
                val remotePath = getFileRemotePath(file, fileList)
                if (!remotePath.root.isSupportedSteamCloudRoot) {
                    Timber.w(
                        "Unrecognized Steam cloud file root %s: %s — resolving via canonical path mapping",
                        remotePath.root,
                        getFilePrefixPath(file, fileList),
                    )
                }
                // Single source of truth — identical to the conflict check / remotecache writer.
                resolveCloudFileLocalPath(file, fileList, prefixToPath, cloudRouting)
            }

            val getDownloadSafetyRoot: (CloudFileInfo, CloudFileChangeList) -> Path = { file, fileList ->
                val gameInstallPrefix = "%${PathType.GameInstall.name}%"
                if (file.filename.startsWith(gameInstallPrefix)) {
                    val mapped = cloudRouting.localPathByCloudPrefix[gameInstallPrefix]
                    Paths.get(mapped ?: prefixToPath(PathType.GameInstall.name))
                } else if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                    val prefix = fileList.pathPrefixes[file.pathPrefixIndex]
                    val mapped = cloudRouting.localPathByCloudPrefix[prefix.trimEnd('/', '\\')]
                    Paths.get(mapped ?: convertPrefixes(fileList, prefixToPath, cloudRouting)[file.pathPrefixIndex])
                } else {
                    Paths.get(prefixToPath(PathType.DEFAULT.name))
                }
            }

            val getFilesDiff: (List<UserFileInfo>, List<UserFileInfo>) -> Pair<Boolean, FileChanges> = { currentFiles, oldFiles ->
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

            // Post-download verification: every cloud file's bytes must be present locally.
            // Deduped by resolved local path with match-ANY semantics so the redundant/stale
            // duplicate entries Steam Cloud keeps for one logical file (different prefixes/names)
            // can't make a fully-restored save look like it still conflicts.
            val hasHashConflicts: (Map<String, List<UserFileInfo>>, CloudFileChangeList) -> Boolean =
                { _, fileList ->
                    groupPersistedCloudFilesByLocalPath(fileList, prefixToPath, cloudRouting)
                        .any { (localPath, variants) -> !localMatchesAnyVariant(localPath, variants) }
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
                    // Dedup by resolved local path: a logical save file Steam lists under multiple
                    // prefixes/names is downloaded ONCE. We try each cloud-name variant until one
                    // fetches successfully, so a redundant/stale entry the CDN no longer has can't
                    // fail the whole sync. filesDownloaded counts UNIQUE local paths written and is
                    // compared against the same unique-path total by the caller.
                    val groups = groupPersistedCloudFilesByLocalPath(fileList, prefixToPath, cloudRouting)
                    val totalFiles = groups.size

                    groups.forEach { (targetPath, variants) ->
                        var wrote = false
                        for (file in variants) {
                            if (wrote) break
                            val prefixedPath = getFilePrefixPath(file, fileList)
                            val rootBase =
                                getDownloadSafetyRoot(file, fileList)
                                    .toAbsolutePath()
                                    .normalize()
                            if (!targetPath.startsWith(rootBase)) {
                                Timber.e(
                                    "Refusing path-traversal target outside save root: %s (root=%s, prefixedPath=%s)",
                                    targetPath,
                                    rootBase,
                                    prefixedPath,
                                )
                                continue
                            }

                            onProgress?.invoke("Downloading ${file.filename}", -1f)
                            val wnBytes =
                                SteamService.withWnSession {
                                    it.downloadCloudFile(appInfo.id, prefixedPath)
                                }
                            if (wnBytes == null) {
                                Timber.w(
                                    "Cloud download failed for ${file.filename} ($prefixedPath); trying next variant",
                                )
                                continue
                            }

                            val tmpPath =
                                targetPath.resolveSibling(
                                    targetPath.fileName.toString() + DOWNLOAD_TMP_SUFFIX,
                                )
                            try {
                                targetPath.parent?.let { Files.createDirectories(it) }
                                Files.deleteIfExists(tmpPath)
                                FileOutputStream(tmpPath.toString()).use { fs ->
                                    fs.write(wnBytes)
                                    try {
                                        fs.fd.sync()
                                    } catch (e: Exception) {
                                        Timber.w(e, "fsync failed for %s; continuing", tmpPath)
                                    }
                                }
                                try {
                                    Files.move(tmpPath, targetPath, StandardCopyOption.ATOMIC_MOVE)
                                } catch (_: Exception) {
                                    Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                                }
                                try {
                                    val mtimeMs = if (file.timestamp > 0) file.timestamp else 0L
                                    java.nio.file.Files.setLastModifiedTime(
                                        targetPath,
                                        java.nio.file.attribute.FileTime.fromMillis(mtimeMs),
                                    )
                                } catch (e: Exception) {
                                    Timber.d(e, "cloud download: failed to set mtime for ${file.filename}")
                                }
                                filesDownloaded++
                                bytesDownloaded += wnBytes.size.toLong()
                                wrote = true
                                onProgress?.invoke("Downloading ${file.filename}", 1f)
                                Timber.i(
                                    "cloud restore via wn-steam-client: ${file.filename} (${wnBytes.size} bytes) -> $targetPath",
                                )
                            } catch (e: Exception) {
                                Timber.w(e, "Could not write $targetPath; preserving existing local file")
                                try {
                                    Files.deleteIfExists(tmpPath)
                                } catch (_: Exception) {
                                    // best-effort
                                }
                            }
                        }
                        if (!wrote) {
                            Timber.w("No cloud variant could be downloaded for %s; preserving existing local file", targetPath)
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
                            .filter { Files.exists(it.second.getAbsPath(prefixToPath)) }

                    val totalFiles = filesToUpload.size
                    val finalFileCount = managedFiles.size

                    // Guard against an empty/transient local scan wiping the whole cloud copy.
                    if (filesToUpload.isEmpty() && filesToDelete.isNotEmpty() && managedFiles.isEmpty()) {
                        Timber.e(
                            "Refusing to delete all ${filesToDelete.size} cloud file(s) for ${appInfo.id}: " +
                                "no local save files found; preserving cloud saves",
                        )
                        return@async UserFilesUploadResult(false, 0, 0, 0)
                    }

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
                    writeSteamRemoteCacheVdf(appInfo, appFileListChange, prefixToPath, cloudRouting)

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

                            // Count UNIQUE local paths to download — Steam Cloud's duplicate/stale
                            // entries for one logical file must not inflate the target and wedge the
                            // sync into permanent DownloadFail (the Monster Hunter Rise symptom).
                            val cloudTargetPaths =
                                groupPersistedCloudFilesByLocalPath(appFileListChange, prefixToPath, cloudRouting).keys
                            val expectedDownloads = cloudTargetPaths.size
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
                                            // Safety: never delete a local file the cloud actually has
                                            // under SOME variant name (guards against the duplicate-
                                            // prefix entries making a kept file look "removed remotely").
                                            val abs =
                                                runCatching { it.getAbsPath(prefixToPath).toAbsolutePath().normalize() }
                                                    .getOrNull()
                                            if (abs != null && abs in cloudTargetPaths) return@forEach
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

                            writeSteamRemoteCacheVdf(appInfo, appFileListChange, prefixToPath, cloudRouting)

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
                                writeSteamRemoteCacheVdfFromLocalFiles(
                                    appInfo,
                                    uploadResult.appChangeNumber,
                                    allLocalUserFiles,
                                    prefixToPath,
                                )
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
                                            // A real Steam conflict requires the local and cloud
                                            // FILE CONTENT to actually diverge — not merely a
                                            // change-number mismatch. The change number can drift
                                            // (e.g. an upload's batch change number lags the next
                                            // GetAppFileChangelist, or the persisted baseline is
                                            // lost) while every file is byte-identical, which used
                                            // to surface a phantom conflict dialog on every launch.
                                            // Steam itself compares file SHAs; if both sides hold
                                            // the same bytes there is nothing to resolve. Mirror the
                                            // launch-probe hardening here: only prompt on genuine
                                            // content divergence, otherwise silently reconcile the
                                            // baseline so the false conflict cannot recur.
                                            val contentDiffers =
                                                cloudContentDiffersFromLocal(appFileListChange, prefixToPath, appInfo)
                                            if (contentDiffers) {
                                                syncResult = SyncResult.Conflict
                                                remoteTimestamp = appFileListChange.files.map { it.timestamp }.maxOrNull() ?: 0L
                                                localTimestamp = allLocalUserFiles.map { it.timestamp }.maxOrNull() ?: 0L
                                            } else {
                                                Timber.i(
                                                    "Change number differs ($effectiveLocalAppChangeNumber -> $cloudAppChangeNumber) " +
                                                        "but local and cloud content are identical; reconciling baseline (no real conflict)",
                                                )
                                                writeSteamRemoteCacheVdf(appInfo, appFileListChange, prefixToPath, cloudRouting)
                                                with(steamInstance) {
                                                    db.withTransaction {
                                                        fileChangeListsDao.insert(appInfo.id, allLocalUserFiles)
                                                        changeNumbersDao.insert(appInfo.id, cloudAppChangeNumber)
                                                    }
                                                }
                                                syncResult = SyncResult.UpToDate
                                            }
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
