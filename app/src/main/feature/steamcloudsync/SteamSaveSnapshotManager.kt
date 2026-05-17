package com.winlator.cmod.feature.steamcloudsync

import android.app.Activity
import android.content.Context
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.enums.SaveLocation
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupHistoryEntry
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupStorage
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.MAX_HISTORY_ENTRIES
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.HISTORY_MAX_AGE_DAYS
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.MAX_HISTORY_LABEL_LENGTH
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager.sanitizeHistoryLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local rolling-snapshot history for Steam Cloud saves.
 *
 * Steam Cloud does not store a server-side version history — `CCloud_EnumerateUserFiles`
 * returns exactly one record per logical filename. This object compensates by capturing
 * a local zip snapshot of the live save directories after each successful sync, keyed by
 * appid. Snapshots are surfaced through the same `BackupHistoryEntry` shape used for the
 * Google Play Saved Games backend (with `storage = STEAM_LOCAL`) so the existing
 * `SaveHistorySection` UI can render them.
 *
 * Storage layout:
 *
 *     <context.filesDir>/save_history/steam/<appId>/<entryId>/
 *         save.zip          — zipped save sources (atomic move from .tmp on commit)
 *         metadata.json     — { schema, entryId, appId, timestampMs, origin, sizeBytes,
 *                               sha256, label, sources[], storage }
 *
 * `entryId` is `<yyyyMMddTHHmmss>_<rand6>` so directory order = chronological order.
 * `metadata.json` is the commit marker — the save.zip is written first, atomically moved
 * into place, then metadata.json is written. Entries whose metadata.json fails to parse
 * are skipped at list-time.
 */
object SteamSaveSnapshotManager {
    private const val TAG = "SteamSaveSnapshot"
    private const val ROOT_DIR_NAME = "save_history"
    private const val STEAM_SUBDIR = "steam"
    private const val SAVE_FILE_NAME = "save.zip"
    private const val SAVE_TMP_NAME = "save.zip.tmp"
    private const val META_FILE_NAME = "metadata.json"
    private const val SCHEMA_VERSION = 1

    /** Per-game cap on total compressed bytes across all snapshots — bounds disk usage. */
    private const val MAX_TOTAL_BYTES_PER_GAME: Long = 500L * 1024L * 1024L

    private val saveIdRandom = SecureRandom()
    private val mutexes = ConcurrentHashMap<Int, Mutex>()

    private fun mutexFor(appId: Int): Mutex = mutexes.getOrPut(appId) { Mutex() }

    private fun rootDir(context: Context): File = File(context.filesDir, "$ROOT_DIR_NAME/$STEAM_SUBDIR")

    private fun appDir(context: Context, appId: Int): File = File(rootDir(context), appId.toString())

    private fun entryDir(context: Context, appId: Int, entryId: String): File = File(appDir(context, appId), entryId)

    /**
     * Snapshot the current on-disk save state for [appId] into a new history entry.
     *
     * SHA-256-deduped against the most recent existing snapshot: if the live content
     * hash matches, the write is skipped to avoid burning slots on idempotent re-syncs
     * (e.g. cold-launch re-downloads that produce identical bytes).
     *
     * Best-effort: never throws to the caller. Returns true on a successful write.
     */
    suspend fun recordSnapshot(
        context: Context,
        appId: Int,
        origin: BackupOrigin,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                mutexFor(appId).withLock {
                    captureSnapshotLocked(context, appId, origin)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "recordSnapshot: outer failure for appId=%d", appId)
                false
            }
        }

    /**
     * Capture-snapshot core; caller MUST already hold `mutexFor(appId)`. Returns true on a
     * successful write; false on dedup-skip or write failure.
     */
    private fun captureSnapshotLocked(context: Context, appId: Int, origin: BackupOrigin): Boolean {
        cleanupPartialEntries(context, appId)
        val sources = enumerateSaveSources(context, appId)
        if (sources.isEmpty()) {
            Timber.tag(TAG).i("captureSnapshotLocked: no save sources for appId=%d", appId)
            return false
        }

        val liveHash = sha256OfSources(sources)
        val existing = listEntriesInternal(context, appId)
        val newest = existing.firstOrNull()
        if (newest != null && newest.sha256 == liveHash) {
            Timber.tag(TAG).d(
                "captureSnapshotLocked: live SHA matches newest (entry=%s); skipping",
                newest.entryId,
            )
            return false
        }

        val createdAtMs = System.currentTimeMillis()
        val entryId = buildEntryId(createdAtMs)
        val target = entryDir(context, appId, entryId)
        if (!target.mkdirs() && !target.isDirectory) {
            Timber.tag(TAG).e("captureSnapshotLocked: failed to create %s", target)
            return false
        }

        val tmpZip = File(target, SAVE_TMP_NAME)
        val finalZip = File(target, SAVE_FILE_NAME)
        val metaFile = File(target, META_FILE_NAME)
        return try {
            val totalUncompressed = zipSourcesToFile(sources, tmpZip)
            Files.move(
                tmpZip.toPath(),
                finalZip.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            val compressed = finalZip.length()
            val meta =
                SnapshotMeta(
                    schema = SCHEMA_VERSION,
                    entryId = entryId,
                    appId = appId,
                    timestampMs = createdAtMs,
                    origin = origin,
                    uncompressedSize = totalUncompressed,
                    compressedSize = compressed,
                    sha256 = liveHash,
                    label = null,
                    sources = sources.map { it.zipRoot },
                    storage = BackupStorage.STEAM_LOCAL.name,
                )
            writeMetadataAtomic(metaFile, meta)
            Timber.tag(TAG).i(
                "captureSnapshotLocked: wrote %s (%d bytes compressed) for appId=%d",
                entryId,
                compressed,
                appId,
            )
            pruneSnapshotsInternal(context, appId, pinEntryId = entryId)
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "captureSnapshotLocked: write failed; cleaning up partial entry")
            runCatching { target.deleteRecursively() }
            false
        }
    }

    /** Return the up-to-30 newest snapshots for [appId], newest-first. */
    suspend fun listHistory(context: Context, appId: Int): List<BackupHistoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                mutexFor(appId).withLock {
                    cleanupPartialEntries(context, appId)
                    pruneSnapshotsInternal(context, appId, pinEntryId = null)
                    listEntriesInternal(context, appId).map { it.toBackupHistoryEntry() }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "listHistory failed for appId=%d", appId)
                emptyList()
            }
        }

    /**
     * Restore [entryId] for [appId]: unzip the save.zip into the live save dirs, then
     * fire a Steam Cloud upload so the rolled-back state becomes the canonical cloud
     * copy. Caller should ensure the game is NOT currently running for [appId] — we
     * surface a warning but don't block.
     */
    suspend fun restoreFromEntry(
        activity: Activity,
        appId: Int,
        entryId: String,
        containerHint: Container? = null,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                mutexFor(appId).withLock {
                    val context = activity.applicationContext
                    val entryDir = entryDir(context, appId, entryId)
                    val zipFile = File(entryDir, SAVE_FILE_NAME)
                    val metaFile = File(entryDir, META_FILE_NAME)
                    if (!zipFile.exists() || !metaFile.exists()) {
                        return@withLock BackupResult(false, "Snapshot is missing.")
                    }
                    val meta = readMetadata(metaFile)
                        ?: return@withLock BackupResult(false, "Snapshot metadata is unreadable.")
                    if (meta.sha256.isNotEmpty()) {
                        val actual = sha256OfFile(zipFile)
                        // The stored sha256 is over the SOURCE FILES, not the zip — we compare
                        // post-extract below. Here we just sanity-check the zip is non-empty.
                        if (actual.isEmpty()) {
                            Timber.tag(TAG).w("restoreFromEntry: snapshot file hash empty")
                        }
                    }

                    if (resolveAccountId() == 0L) {
                        return@withLock BackupResult(false, "Sign in to Steam before restoring.")
                    }
                    // forRestore=true so we get the resolved target paths even when the live
                    // save dir is empty / missing — restore must mkdir + extract into those.
                    val sources = enumerateSaveSources(context, appId, forRestore = true, containerHint = containerHint)
                    if (sources.isEmpty()) {
                        return@withLock BackupResult(false, "Cannot determine save directory for this game.")
                    }
                    // Make sure all target dirs exist before extraction.
                    sources.forEach { it.localDir.mkdirs() }
                    extractZipToSources(zipFile, sources)

                    // Push the restored state to Steam Cloud so the next launch is consistent.
                    val uploadOk = uploadLocalToSteam(context, appId)
                    if (uploadOk) {
                        BackupResult(true, "Save restored and pushed to Steam Cloud.")
                    } else {
                        BackupResult(true, "Save restored locally; Steam Cloud upload deferred.")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "restoreFromEntry failed for appId=%d entry=%s", appId, entryId)
                BackupResult(false, "Restore failed: ${e.message}")
            }
        }

    /**
     * Import user-supplied save files into the live Steam save directory for [appId].
     *
     * Steam's `store.steampowered.com/account/remotestorageapp` serves individual files with
     * `Content-Disposition: attachment; filename="<basename>"` — the browser strips the
     * relative path, so a file logically at `saves/slot1.dat` lands on disk as `slot1.dat`.
     * Multi-file games (Stardew Valley, RimWorld, etc.) need the subdirectory restored or
     * the game won't load the save.
     *
     * Strategy: query Steam's current cloud file list via [SteamService.getTrackedCloudSaveFiles]
     * and match each imported file's basename against the listing. Unique match → reconstruct
     * the canonical path (PathType.root + relative subdir + filename). No match → fall back to
     * SteamUserData/<basename>. Ambiguous match → skip with a warning.
     *
     * After all files are placed, take a snapshot for history and push to Steam Cloud.
     */
    suspend fun importSnapshotFromFiles(
        activity: Activity,
        appId: Int,
        uris: List<android.net.Uri>,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                if (uris.isEmpty()) return@withContext BackupResult(false, "No files selected.")
                mutexFor(appId).withLock {
                    val context = activity.applicationContext
                    val accountId = resolveAccountId()
                    if (accountId == 0L) {
                        return@withLock BackupResult(false, "Sign in to Steam before importing.")
                    }
                    val resolver = context.contentResolver
                    val prefixResolver = steamPrefixResolver(context, appId)

                    // Cloud listing for basename → canonical-path reconstruction. If the local
                    // cache is empty (user never ran a Sync), we can't restore subdirectories for
                    // multi-file games. Refuse rather than silently flatten — putting Stardew /
                    // RimWorld save files at the SteamUserData root would corrupt them.
                    val cloudFiles = SteamService.getTrackedCloudSaveFiles(appId).orEmpty()
                    if (cloudFiles.isEmpty()) {
                        return@withLock BackupResult(
                            false,
                            "No cloud file list available yet — tap 'Sync from Steam Cloud' first so path reconstruction can work.",
                        )
                    }
                    val byBasename = cloudFiles.groupBy { it.filename }

                    val fallbackDir = File(PathType.SteamUserData.toAbsPath(context, appId, accountId))
                    fallbackDir.mkdirs()
                    // Build a "containment root" so reconstructed target paths can be sanity-checked.
                    // SteamUserData lives under the wine prefix's drive_c; that's the tightest bound
                    // we can express without knowing every game's possible UFS paths in advance.
                    val containmentRoot = runCatching {
                        File(prefixResolver("SteamUserData")).canonicalFile.parentFile?.parentFile?.canonicalPath
                            ?: File(prefixResolver("SteamUserData")).canonicalPath
                    }.getOrNull()

                    var written = 0
                    var pathReconstructed = 0
                    var fallback = 0
                    var skippedAmbiguous = 0
                    var skippedEscape = 0
                    val skipNotes = mutableListOf<String>()

                    for (uri in uris) {
                        val displayName = queryDisplayName(resolver, uri)
                        if (displayName == null) {
                            skipNotes += "Skipped a file with no resolvable name."
                            continue
                        }
                        val sanitizedBase = displayName.substringAfterLast('/').substringAfterLast('\\')
                        if (sanitizedBase.isEmpty() || sanitizedBase.contains("..")) {
                            skipNotes += "Skipped suspicious name: $sanitizedBase"
                            continue
                        }
                        val matches = byBasename[sanitizedBase].orEmpty()
                        val targetFile: File
                        val isReconstructed: Boolean
                        when {
                            matches.size == 1 -> {
                                targetFile = matches.first().getAbsPath(prefixResolver).toFile()
                                isReconstructed = true
                            }
                            matches.size > 1 -> {
                                skippedAmbiguous++
                                skipNotes += "Skipped ambiguous file: $sanitizedBase"
                                continue
                            }
                            else -> {
                                targetFile = File(fallbackDir, sanitizedBase)
                                isReconstructed = false
                            }
                        }
                        // Defensive path-containment check — refuse to write outside the wine prefix
                        // even if the cloud listing is somehow malformed.
                        val canonicalTarget = runCatching { targetFile.canonicalPath }.getOrNull()
                        if (containmentRoot != null && canonicalTarget != null &&
                            !canonicalTarget.startsWith(containmentRoot + File.separator) &&
                            canonicalTarget != containmentRoot
                        ) {
                            skippedEscape++
                            skipNotes += "Refused (outside prefix): $sanitizedBase"
                            continue
                        }
                        targetFile.parentFile?.mkdirs()
                        try {
                            val input = resolver.openInputStream(uri)
                            if (input == null) {
                                skipNotes += "Could not open: $sanitizedBase"
                                continue
                            }
                            input.use { src ->
                                FileOutputStream(targetFile).use { output ->
                                    val buf = ByteArray(8192)
                                    var len: Int
                                    while (src.read(buf).also { len = it } > 0) {
                                        output.write(buf, 0, len)
                                    }
                                }
                            }
                            written++
                            if (isReconstructed) pathReconstructed++ else fallback++
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(e, "importSnapshotFromFiles: failed to copy %s", sanitizedBase)
                            skipNotes += "Failed to write $sanitizedBase"
                        }
                    }

                    if (written == 0) {
                        val detail =
                            if (skipNotes.isEmpty()) "No files were written." else skipNotes.joinToString("; ").take(200)
                        return@withLock BackupResult(false, detail)
                    }

                    captureSnapshotLocked(context, appId, BackupOrigin.MANUAL)
                    // Try the upload up to 2x — if Steam reports `InProgress` we retry once after
                    // a short delay so a concurrent background sync doesn't silently strand the
                    // imported state out of cloud.
                    var uploadOk = uploadLocalToSteam(context, appId)
                    if (!uploadOk) {
                        kotlinx.coroutines.delay(1_500)
                        uploadOk = uploadLocalToSteam(context, appId)
                    }

                    val parts = mutableListOf("Imported $written file(s)")
                    if (pathReconstructed > 0) parts += "$pathReconstructed restored to original location"
                    if (fallback > 0) parts += "$fallback placed at SteamUserData root (path unknown)"
                    if (skippedAmbiguous > 0) parts += "$skippedAmbiguous ambiguous and skipped"
                    if (skippedEscape > 0) parts += "$skippedEscape refused (outside prefix)"
                    parts += if (uploadOk) "pushed to Steam Cloud" else "Steam Cloud upload deferred (tap Sync from Steam Cloud to retry)"
                    BackupResult(true, parts.joinToString(", ") + ".")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "importSnapshotFromFiles failed for appId=%d", appId)
                BackupResult(false, "Import failed: ${e.message}")
            }
        }

    /** Pull the SAF display name (basename) for [uri]. Returns null if not resolvable. */
    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? {
        val cursor = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull() ?: return uri.lastPathSegment
        return cursor.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else {
                null
            }
        } ?: uri.lastPathSegment
    }

    /** Update the user-visible label on a snapshot. */
    suspend fun renameEntry(
        context: Context,
        appId: Int,
        entryId: String,
        newLabel: String?,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                mutexFor(appId).withLock {
                    val metaFile = File(entryDir(context, appId, entryId), META_FILE_NAME)
                    val meta = readMetadata(metaFile)
                        ?: return@withLock BackupResult(false, "Snapshot metadata is missing.")
                    val cleanLabel = sanitizeHistoryLabel(newLabel)
                    val updated = meta.copy(label = cleanLabel)
                    writeMetadataAtomic(metaFile, updated)
                    BackupResult(true, if (cleanLabel.isNullOrEmpty()) "Label cleared." else "Renamed.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "renameEntry failed for appId=%d entry=%s", appId, entryId)
                BackupResult(false, "Rename failed: ${e.message}")
            }
        }

    // ── Internal: enumeration / metadata ──

    /**
     * Live snapshot sources for [appId] — SteamUserData plus any UFS-declared Windows patterns.
     *
     * [forRestore]=false: only include sources that currently exist and are non-empty (capture mode).
     * [forRestore]=true:  return all resolved target paths regardless of existence/emptiness, so
     * a restore can mkdir + extract into a wiped save directory.
     */
    private fun enumerateSaveSources(
        context: Context,
        appId: Int,
        forRestore: Boolean = false,
        containerHint: Container? = null,
    ): List<SaveSource> {
        // Match SteamCloudSyncHelper.steamPrefixResolver — every PathType.toAbsPath call
        // for Steam reads/writes resolves through the global `home/xuser` symlink, so we
        // must point it at this game's container before reading local sources or
        // writing restored files. Without this, a snapshot-restore would write into the
        // last-active container's wineprefix instead of the target Steam game's.
        // Prefer the caller-provided container; appId-based fallback isn't appId-aware
        // when a default x86 container preference is set (returns the default for every
        // Steam game).
        activateContainerForCloudOp(context, appId, containerHint)

        val accountId = resolveAccountId()
        val sources = linkedMapOf<String, SaveSource>()

        val userDataPath = PathType.SteamUserData.toAbsPath(context, appId, accountId)
        val userDataDir = File(userDataPath)
        if (forRestore || (userDataDir.exists() && (userDataDir.listFiles()?.isNotEmpty() == true))) {
            sources[PathType.SteamUserData.name] = SaveSource(PathType.SteamUserData.name, userDataDir)
        }

        val appInfo = SteamService.getAppInfoOf(appId)
        appInfo
            ?.ufs
            ?.saveFilePatterns
            .orEmpty()
            .filter { it.root.isWindows && it.root != PathType.SteamUserData }
            .forEach { pattern ->
                val baseAbs = pattern.root.toAbsPath(context, appId, accountId)
                val candidate = File(Paths.get(baseAbs, pattern.substitutedPath).toString())
                if (forRestore || (candidate.exists() && (candidate.listFiles()?.isNotEmpty() == true))) {
                    val zipRoot = "${pattern.root.name}/${pattern.substitutedPath.trim('/').replace('/', '_')}"
                        .ifEmpty { pattern.root.name }
                    sources.putIfAbsent(zipRoot, SaveSource(zipRoot, candidate))
                }
            }

        return sources.values.toList()
    }

    private fun resolveAccountId(): Long =
        SteamService.userSteamId?.accountID?.toLong()
            ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
            ?: 0L

    /**
     * Push the local save state up to Steam Cloud. Forces overwrite via
     * `overrideLocalChangeNumber = -1` so Steam doesn't reject the rollback as stale.
     */
    private suspend fun uploadLocalToSteam(context: Context, appId: Int): Boolean {
        return try {
            val resolver = steamPrefixResolver(context, appId)
            val info =
                SteamService
                    .forceSyncUserFiles(
                        appId = appId,
                        prefixToPath = resolver,
                        preferredSave = SaveLocation.Local,
                        overrideLocalChangeNumber = -1,
                    ).await()
            val result = info?.syncResult?.name
            Timber.tag(TAG).i("uploadLocalToSteam appId=%d result=%s", appId, result)
            result == "Success" || result == "UpToDate"
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "uploadLocalToSteam failed for appId=%d", appId)
            false
        }
    }

    private fun steamPrefixResolver(
        context: Context,
        appId: Int,
        containerHint: Container? = null,
    ): (String) -> String {
        // See enumerateSaveSources — `home/xuser` symlink must point at this game's
        // container before paths resolve, otherwise upload/download lands in the wrong
        // wineprefix.
        activateContainerForCloudOp(context, appId, containerHint)

        val accountId =
            SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L
        return { pathTypeName ->
            val type = runCatching { PathType.valueOf(pathTypeName) }.getOrNull() ?: PathType.SteamUserData
            type.toAbsPath(context, appId, accountId)
        }
    }

    private fun activateContainerForCloudOp(
        context: Context,
        appId: Int,
        containerHint: Container?,
    ) {
        val target =
            containerHint
                ?: ContainerUtils.getUsableContainerOrNull(context, appId.toString())
                ?: return
        runCatching {
            ContainerManager(context).activateContainer(target)
        }.onFailure { Timber.tag(TAG).w(it, "Failed to activate container id=%d", target.id) }
    }

    private fun listEntriesInternal(context: Context, appId: Int): List<SnapshotMeta> {
        val dir = appDir(context, appId)
        if (!dir.isDirectory) return emptyList()
        return dir
            .listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { sub ->
                val meta = readMetadata(File(sub, META_FILE_NAME)) ?: return@mapNotNull null
                if (!File(sub, SAVE_FILE_NAME).exists()) return@mapNotNull null
                meta
            }
            ?.sortedByDescending { it.timestampMs }
            ?: emptyList()
    }

    /** Apply count, age, and total-bytes caps. Pinned entry (just-written) survives this pass. */
    private fun pruneSnapshotsInternal(context: Context, appId: Int, pinEntryId: String?) {
        val entries = listEntriesInternal(context, appId).toMutableList()
        val now = System.currentTimeMillis()
        val ageCutoff = now - HISTORY_MAX_AGE_DAYS * 24L * 60L * 60L * 1000L
        val toDelete = linkedSetOf<String>()

        // Age cap.
        entries.filter { it.timestampMs in 1L..ageCutoff && it.entryId != pinEntryId }
            .forEach { toDelete += it.entryId }

        val survivors = entries.filter { it.entryId !in toDelete }
        // Count cap (oldest beyond MAX_HISTORY_ENTRIES drop).
        survivors
            .drop(MAX_HISTORY_ENTRIES)
            .filter { it.entryId != pinEntryId }
            .forEach { toDelete += it.entryId }

        // Size cap: drop oldest until total bytes <= MAX_TOTAL_BYTES_PER_GAME (excluding pin).
        val survivorsAfterCount = survivors.filter { it.entryId !in toDelete }
        var total = survivorsAfterCount.sumOf { it.compressedSize }
        survivorsAfterCount
            .reversed() // oldest first
            .forEach { entry ->
                if (total <= MAX_TOTAL_BYTES_PER_GAME) return@forEach
                if (entry.entryId == pinEntryId) return@forEach
                toDelete += entry.entryId
                total -= entry.compressedSize
            }

        toDelete.forEach { id ->
            val target = entryDir(context, appId, id)
            runCatching { target.deleteRecursively() }
                .onFailure { Timber.tag(TAG).w(it, "Failed to prune %s", target) }
        }
    }

    /** Sweep any leftover `.tmp` files from a previous interrupted write. */
    private fun cleanupPartialEntries(context: Context, appId: Int) {
        val dir = appDir(context, appId)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { sub ->
            if (!sub.isDirectory) return@forEach
            val tmp = File(sub, SAVE_TMP_NAME)
            if (tmp.exists()) {
                runCatching { tmp.delete() }
            }
            val finalZip = File(sub, SAVE_FILE_NAME)
            val meta = File(sub, META_FILE_NAME)
            // If the metadata is missing/unreadable, the entry never committed — purge.
            if (!meta.exists() || readMetadata(meta) == null || !finalZip.exists()) {
                runCatching { sub.deleteRecursively() }
            }
        }
    }

    private fun readMetadata(metaFile: File): SnapshotMeta? {
        if (!metaFile.isFile) return null
        return try {
            val o = JSONObject(metaFile.readText(Charsets.UTF_8))
            val sourcesArr = o.optJSONArray("sources") ?: JSONArray()
            val sources = (0 until sourcesArr.length()).map { sourcesArr.getString(it) }
            SnapshotMeta(
                schema = o.optInt("schema", 1),
                entryId = o.getString("entryId"),
                appId = o.getInt("appId"),
                timestampMs = o.getLong("timestampMs"),
                origin = BackupOrigin.fromTag(o.optString("origin")) ?: BackupOrigin.AUTO,
                uncompressedSize = o.optLong("uncompressedSize", 0L),
                compressedSize = o.optLong("compressedSize", 0L),
                sha256 = o.optString("sha256", ""),
                label = if (o.has("label") && !o.isNull("label")) o.getString("label").takeIf { it.isNotEmpty() } else null,
                sources = sources,
                storage = o.optString("storage", BackupStorage.STEAM_LOCAL.name),
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse %s", metaFile)
            null
        }
    }

    private fun writeMetadataAtomic(metaFile: File, meta: SnapshotMeta) {
        val tmp = File(metaFile.parentFile, "${metaFile.name}.tmp")
        val payload =
            JSONObject().apply {
                put("schema", meta.schema)
                put("entryId", meta.entryId)
                put("appId", meta.appId)
                put("timestampMs", meta.timestampMs)
                put("origin", meta.origin.tag)
                put("uncompressedSize", meta.uncompressedSize)
                put("compressedSize", meta.compressedSize)
                put("sha256", meta.sha256)
                if (!meta.label.isNullOrEmpty()) put("label", meta.label)
                put("sources", JSONArray(meta.sources))
                put("storage", meta.storage)
            }.toString()
        tmp.writeText(payload, Charsets.UTF_8)
        Files.move(
            tmp.toPath(),
            metaFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    // ── Zip helpers ──

    private fun zipSourcesToFile(sources: List<SaveSource>, outFile: File): Long {
        var bytes = 0L
        FileOutputStream(outFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                sources.forEach { src ->
                    val zipRoot = src.zipRoot.trimEnd('/')
                    if (zipRoot.isEmpty()) return@forEach
                    zos.putNextEntry(ZipEntry("$zipRoot/"))
                    zos.closeEntry()
                    bytes += zipDirRecursive(zos, src.localDir, zipRoot)
                }
            }
        }
        return bytes
    }

    private fun zipDirRecursive(zos: ZipOutputStream, dir: File, baseName: String): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var written = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            val entryName = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
            if (child.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
                written += zipDirRecursive(zos, child, entryName)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(child).use { fis ->
                    val buf = ByteArray(8192)
                    var len: Int
                    while (fis.read(buf).also { len = it } > 0) {
                        zos.write(buf, 0, len)
                        written += len
                    }
                }
                zos.closeEntry()
            }
        }
        return written
    }

    private fun extractZipToSources(zipFile: File, sources: List<SaveSource>) {
        val sortedSources = sources.sortedByDescending { it.zipRoot.length }
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val name = entry!!.name
                val source =
                    sortedSources.firstOrNull {
                        name == "${it.zipRoot}/" || name.startsWith("${it.zipRoot}/")
                    }
                if (source == null) {
                    zis.closeEntry()
                    continue
                }
                val rel = name.removePrefix(source.zipRoot).removePrefix("/")
                if (rel.isEmpty()) {
                    source.localDir.mkdirs()
                    zis.closeEntry()
                    continue
                }
                val out = File(source.localDir, rel)
                if (!out.canonicalPath.startsWith(source.localDir.canonicalPath + File.separator) &&
                    out.canonicalPath != source.localDir.canonicalPath
                ) {
                    throw SecurityException("Zip entry tries to escape target directory")
                }
                if (entry!!.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
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

    // ── Hashing ──

    /** SHA-256 over sources' files, sorted by zipRoot then relative path. Stable across runs. */
    private fun sha256OfSources(sources: List<SaveSource>): String {
        val md = MessageDigest.getInstance("SHA-256")
        sources
            .sortedBy { it.zipRoot }
            .forEach { src -> hashDir(md, src.localDir, src.zipRoot) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hashDir(md: MessageDigest, dir: File, baseName: String) {
        if (!dir.exists() || !dir.isDirectory) return
        val children = dir.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            val name = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
            if (child.isDirectory) {
                md.update(("D:$name\n").toByteArray(Charsets.UTF_8))
                hashDir(md, child, name)
            } else {
                md.update(("F:$name:${child.length()}\n").toByteArray(Charsets.UTF_8))
                FileInputStream(child).use { fis ->
                    val buf = ByteArray(8192)
                    var len: Int
                    while (fis.read(buf).also { len = it } > 0) {
                        md.update(buf, 0, len)
                    }
                }
            }
        }
    }

    private fun sha256OfFile(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var len: Int
            while (fis.read(buf).also { len = it } > 0) {
                md.update(buf, 0, len)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ── Naming ──

    private fun buildEntryId(timestampMs: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val ts = fmt.format(Date(timestampMs))
        val rand = StringBuilder(6)
        repeat(6) { rand.append(('a' + saveIdRandom.nextInt(26))) }
        return "${ts}_$rand"
    }

    // ── Data model (private) ──

    private data class SaveSource(val zipRoot: String, val localDir: File)

    private data class SnapshotMeta(
        val schema: Int,
        val entryId: String,
        val appId: Int,
        val timestampMs: Long,
        val origin: BackupOrigin,
        val uncompressedSize: Long,
        val compressedSize: Long,
        val sha256: String,
        val label: String?,
        val sources: List<String>,
        val storage: String,
    ) {
        fun toBackupHistoryEntry(): BackupHistoryEntry =
            BackupHistoryEntry(
                fileId = entryId,
                fileName = entryId,
                timestampMs = timestampMs,
                origin = origin,
                sizeBytes = uncompressedSize.coerceAtLeast(0L),
                label = label?.take(MAX_HISTORY_LABEL_LENGTH),
                storage = BackupStorage.STEAM_LOCAL,
            )
    }
}
