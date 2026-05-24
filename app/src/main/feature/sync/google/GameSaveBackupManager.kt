package com.winlator.cmod.feature.sync.google
import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Tasks
import com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore of per-game save files via Google Play Games **Saved Games (Snapshots)** API.
 *
 * Each backup is stored as a "manifest" snapshot plus N "part" snapshots:
 *
 *     wnsv_<src>_<gameKeyHash16>_<saveId>_m       — small JSON manifest, written last
 *     wnsv_<src>_<gameKeyHash16>_<saveId>_p000    — gzipped zip chunks, written first
 *     wnsv_<src>_<gameKeyHash16>_<saveId>_p001
 *     ...
 *
 * The manifest's existence is the commit point — a save is only valid when its
 * manifest is present and references existing parts. Listing/restore enumerate
 * manifests; orphan parts (no parent manifest) are GC'd after a 24-hour grace
 * window so an interrupted upload can resume.
 *
 * Steam is intentionally **not** stored here: Steam saves go through Steam Cloud
 * directly via [SteamCloudSyncHelper] / SteamService. All Steam-targeted entry
 * points in this object short-circuit; the existing cloud-saves UI in
 * [com.winlator.cmod.app.shell.UnifiedActivity.CloudSavesContent] already hides
 * Backup/Restore/History for Steam games via `steamManagedCloud`.
 *
 * Backwards-compat note (post-#308):
 *  - Public data classes/enums are still shared by the Steam local/cloud history UI.
 *  - Non-store Google save snapshot entry points have been retired; store-login
 *    sync continues through [CloudSyncManager].
 */
object GameSaveBackupManager {
    private const val TAG = "GameSaveBackup"
    private const val PREFS_NAME = "google_store_login_sync"

    /** Pref name preserved verbatim — flips true once Play Games sign-in succeeds. */
    private const val KEY_GOOGLE_DRIVE_CONNECTED = "google_drive_connected"
    private const val KEY_KEEP_REPLACED_BACKUP = "cloud_sync_keep_replaced_backup"
    private const val AUTH_SESSION_RETRY_COUNT = 5
    private const val AUTH_SESSION_RETRY_DELAY_MS = 750L

    /** Maximum number of history entries retained (and shown) per game. */
    const val MAX_HISTORY_ENTRIES = 30

    /** Entries older than this are pruned whenever history is listed or written. */
    const val HISTORY_MAX_AGE_DAYS = 30

    /** Hard ceiling on a save's compressed size — refuse to back up larger than this. */
    private const val MAX_COMPRESSED_BYTES: Long = 50L * 1024L * 1024L

    /** Defensive ceiling on the number of parts per save. */
    private const val MAX_PARTS = 99

    /** Reserve this many bytes inside getMaxDataSize() for snapshot envelope/metadata. */
    private const val PART_SIZE_HEADROOM_BYTES = 4 * 1024

    /** Floor used when getMaxDataSize() returns implausibly low values. */
    private const val MIN_PART_SIZE_BYTES = 1L * 1024 * 1024

    /** Orphan parts (parent manifest missing) younger than this are skipped during GC. */
    private const val ORPHAN_GRACE_MS: Long = 24L * 60L * 60L * 1000L

    /** Snapshot uniqueName prefix — short to leave room for hash + saveId in the 100-char limit. */
    private const val SNAPSHOT_PREFIX = "wnsv"

    /** Bound the conflict-resolution loop so two thrashing devices can't loop us forever. */
    private const val MAX_CONFLICT_RESOLVE_ATTEMPTS = 10

    const val REQUEST_CODE_DRIVE_AUTH = 9002 // legacy; some callers still pass this through onActivityResult.

    enum class GameSource(val code: Char) {
        STEAM('s'),
        EPIC('e'),
        GOG('g'),
        CUSTOM('c'),
    }

    /**
     * Backend storage for a [BackupHistoryEntry]. Used by `UnifiedActivity.CloudSavesContent`
     * to route Restore/Rename/Delete actions to the right manager and to hide actions that
     * don't apply (e.g. Delete is hidden for STEAM_LOCAL since Steam manages cloud retention).
     */
    enum class BackupStorage {
        /** Local rolling-snapshot capture (zipped to filesDir/save_history/steam/...). */
        STEAM_LOCAL,
        /**
         * Steam Cloud's CURRENT file listing, grouped into save sets by timestamp clusters.
         * Backed by `SteamCloudHistoryProvider`. Restore downloads the group's files via
         * `clientFileDownload` and writes to the resolved local paths. Steam Cloud has no
         * server-side version history — each "group" is files written within ~120s of each other.
         */
        STEAM_CLOUD,
        /**
         * Epic Games Store cloud-save manifests listed by savesync. Each upload writes a
         * timestamped manifest plus chunk files; selecting a row restores that manifest.
         */
        EPIC_CLOUD,
        /** GOG cloud's live file listing. Restore pulls full cloud state down. */
        GOG_CLOUD,
    }

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

    /**
     * A backed-up save in Google Play Saved Games.
     *
     * `fileId` holds the manifest snapshot's unique-name (formerly a Drive file ID — name
     * preserved for caller compatibility).
     */
    data class BackupHistoryEntry(
        val fileId: String,
        val fileName: String,
        val timestampMs: Long,
        val origin: BackupOrigin,
        val sizeBytes: Long,
        /** Optional user label. Persisted on the manifest snapshot's description field. */
        val label: String? = null,
        /** Which backend produced this entry. Defaults to local Steam history for legacy callers. */
        val storage: BackupStorage = BackupStorage.STEAM_LOCAL,
    )

    /** Max length of a user-provided history-entry label, after sanitization. */
    const val MAX_HISTORY_LABEL_LENGTH = 48

    /** Custom-game extra-data keys persisted on the Shortcut. */
    const val CUSTOM_SAVE_CONTAINER_ID_KEY = "customSaveContainerId"
    const val CUSTOM_SAVE_WINDOWS_PATH_KEY = "customSaveWindowsPath"

    /** Legacy upstream key — an Android absolute path to a single custom-game folder. */
    private const val LEGACY_CUSTOM_GAME_FOLDER_KEY = "custom_game_folder"

    private data class SaveBackupSource(
        val zipRoot: String,
        val localDir: File,
        val exactFiles: List<File>? = null,
    )

    /**
     * Parsed manifest payload — what gets written into the manifest snapshot's contents.
     */
    private data class Manifest(
        val schema: Int,
        val source: GameSource,
        val gameId: String,
        val gameName: String,
        val origin: BackupOrigin,
        val createdAtMs: Long,
        val uncompressedSize: Long,
        val compressedSize: Long,
        val sha256: String,
        val parts: List<String>,
        val windowsPath: String? = null,
    ) {
        fun toJson(): String =
            JSONObject().apply {
                put("schema", schema)
                put("source", source.name)
                put("gameId", gameId)
                put("gameName", gameName)
                put("origin", origin.name)
                put("createdAtMs", createdAtMs)
                put("uncompressedSize", uncompressedSize)
                put("compressedSize", compressedSize)
                put("sha256", sha256)
                put("parts", JSONArray(parts))
                if (windowsPath != null) put("windowsPath", windowsPath)
            }.toString()

        companion object {
            fun fromJson(json: String): Manifest? =
                try {
                    val o = JSONObject(json)
                    val parts = mutableListOf<String>()
                    val partsArr = o.getJSONArray("parts")
                    for (i in 0 until partsArr.length()) parts += partsArr.getString(i)
                    Manifest(
                        schema = o.optInt("schema", 1),
                        source = GameSource.valueOf(o.getString("source")),
                        gameId = o.getString("gameId"),
                        gameName = o.optString("gameName", ""),
                        origin = BackupOrigin.valueOf(o.optString("origin", BackupOrigin.MANUAL.name)),
                        createdAtMs = o.getLong("createdAtMs"),
                        uncompressedSize = o.optLong("uncompressedSize", -1),
                        compressedSize = o.optLong("compressedSize", -1),
                        sha256 = o.optString("sha256", ""),
                        parts = parts,
                        windowsPath = if (o.has("windowsPath")) o.getString("windowsPath") else null,
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to parse manifest JSON")
                    null
                }
        }
    }

    // ── Public API ──

    /** Pref name kept verbatim ("google_drive_connected") for upgrade compatibility — the backend is now PGS. */
    fun isDriveConnected(context: Context): Boolean = prefs(context).getBoolean(KEY_GOOGLE_DRIVE_CONNECTED, false)

    private fun setDriveConnected(context: Context, connected: Boolean) {
        prefs(context).edit().putBoolean(KEY_GOOGLE_DRIVE_CONNECTED, connected).apply()
    }

    fun isKeepReplacedBackupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_REPLACED_BACKUP, true)

    fun setKeepReplacedBackupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_REPLACED_BACKUP, enabled).apply()
    }

    /**
     * Snapshot the current local save files and write them to Save History as origin=[origin].
     * Central upload path for manual backup, auto backup, and conflict-resolution
     * "keep a copy of the replaced save" flows.
     *
     * The [authMode] parameter mirrors upstream's call sites: AUTO/list paths use SILENT
     * to avoid any UI prompts; manual paths use INTERACTIVE so the Play Games sign-in
     * sheet can appear if needed.
     */
    @JvmOverloads
    suspend fun backupDiscardedSave(
        activity: Activity,
        gameSource: GameSource,
        gameId: String,
        gameName: String,
        origin: BackupOrigin,
        authMode: GoogleAuthMode = GoogleAuthMode.INTERACTIVE,
        customSaveDir: File? = null,
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                if (gameSource != GameSource.STEAM) {
                    return@withContext BackupResult(false, "Google save backups are disabled.")
                }
                val appId = gameId.toIntOrNull()
                if (appId != null) {
                    val ok = com.winlator.cmod.feature.steamcloudsync.SteamSaveSnapshotManager
                        .recordSnapshot(activity.applicationContext, appId, origin)
                    return@withContext BackupResult(
                        ok || origin == BackupOrigin.LOCAL,
                        if (ok) "Local snapshot captured." else "No local save files found to snapshot.",
                    )
                }
                BackupResult(false, "Invalid Steam appId for snapshot.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "backupDiscardedSave failed for $gameSource/$gameId")
                BackupResult(false, "Failed to back up save: ${e.message}")
            }
        }

    // ── Custom-game helpers (used by the "Select Save Folder" picker UI) ──

    fun setCustomGameSavePath(shortcut: Shortcut, container: Container, windowsPath: String) {
        shortcut.putExtra(CUSTOM_SAVE_CONTAINER_ID_KEY, container.id.toString())
        shortcut.putExtra(CUSTOM_SAVE_WINDOWS_PATH_KEY, windowsPath)
        shortcut.saveData()
    }

    fun clearCustomGameSavePath(shortcut: Shortcut) {
        shortcut.putExtra(CUSTOM_SAVE_CONTAINER_ID_KEY, null)
        shortcut.putExtra(CUSTOM_SAVE_WINDOWS_PATH_KEY, null)
        shortcut.saveData()
    }

    fun getCustomGameSaveWindowsPath(shortcut: Shortcut): String? =
        shortcut.getExtra(CUSTOM_SAVE_WINDOWS_PATH_KEY)?.takeIf { it.isNotEmpty() }

    /** Build the `gameId` token used for custom games when calling the public backup API. */
    fun customGameId(shortcut: Shortcut): String {
        val containerId = shortcut.container?.id?.toString() ?: "0"
        val shortcutName = shortcut.file?.name ?: shortcut.name ?: "shortcut"
        return "$containerId:$shortcutName"
    }

    private fun customSaveWindowsPathFor(context: Context, gameId: String): String? {
        // Try our customGameId-encoded form first.
        parseCustomGameId(gameId)?.let { (cid, file) ->
            return findCustomShortcutByContainerAndFile(context, cid, file)
                ?.let(::getCustomGameSaveWindowsPath)
        }
        // Fall back to upstream's gameId conventions (app_id / custom_name / shortcut.name).
        return findCustomShortcutByGameId(context, gameId)
            ?.let(::getCustomGameSaveWindowsPath)
    }

    private fun resolveCustomSaveAndroidDir(
        context: Context,
        gameId: String,
        windowsPathFromManifest: String?,
    ): File? {
        val (containerIdOrNull, file) = parseCustomGameId(gameId)?.let { (cid, f) -> cid to f }
            ?: (null to null)

        val shortcut = if (containerIdOrNull != null && file != null) {
            findCustomShortcutByContainerAndFile(context, containerIdOrNull, file)
        } else {
            findCustomShortcutByGameId(context, gameId)
        }

        val container =
            shortcut?.container
                ?: containerIdOrNull?.let { id ->
                    ContainerManager(context).getContainers().firstOrNull { it.id == id }
                }
                ?: return null

        val winPath =
            windowsPathFromManifest
                ?: shortcut?.let(::getCustomGameSaveWindowsPath)
                ?: return null
        return WinePathUtils.windowsToAndroidFile(winPath, container)
    }

    private fun parseCustomGameId(gameId: String): Pair<Int, String>? {
        val sep = gameId.indexOf(':')
        if (sep <= 0 || sep == gameId.length - 1) return null
        val cid = gameId.substring(0, sep).toIntOrNull() ?: return null
        return cid to gameId.substring(sep + 1)
    }

    private fun findCustomShortcutByContainerAndFile(
        context: Context,
        containerId: Int,
        shortcutFile: String,
    ): Shortcut? =
        runCatching {
            ContainerManager(context)
                .loadShortcuts()
                .firstOrNull { it.container?.id == containerId && (it.file?.name == shortcutFile) }
        }.getOrNull()

    /** Mirrors upstream's lookup-by-gameId logic for backwards compatibility. */
    private fun findCustomShortcutByGameId(context: Context, gameId: String): Shortcut? =
        runCatching {
            ContainerManager(context).loadShortcuts().firstOrNull {
                it.getExtra("game_source") == "CUSTOM" &&
                    (
                        it.getExtra("app_id") == gameId ||
                            it.getExtra("custom_name") == gameId ||
                            it.name == gameId
                    )
            }
        }.getOrNull()

    // ── Save-source resolution ──

    private suspend fun getLocalSaveSources(
        context: Context,
        source: GameSource,
        gameId: String,
        customSaveDir: File?,
        forRestore: Boolean,
    ): List<SaveBackupSource> =
        when (source) {
            GameSource.STEAM -> emptyList() // Steam uses Steam Cloud — Google Saves unused.
            GameSource.EPIC -> getEpicSaveSources(context, gameId, forRestore)
            GameSource.GOG -> getGogSaveSources(context, gameId, forRestore)
            GameSource.CUSTOM -> getCustomSaveSources(context, gameId, customSaveDir, forRestore)
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

    /**
     * Custom-game save sources, resolved in priority order:
     *   1. Explicit `customSaveDir` argument (from the picker flow).
     *   2. `customSaveWindowsPath` extra (set by our "Select Save Folder" picker).
     *   3. Upstream's `custom_game_folder` extra (legacy absolute Android path).
     *   4. Wine prefix's `users/xuser/{Documents,Saved Games,AppData}` directories.
     */
    private fun getCustomSaveSources(
        context: Context,
        gameId: String,
        customSaveDir: File?,
        forRestore: Boolean,
    ): List<SaveBackupSource> {
        val sources = linkedMapOf<String, SaveBackupSource>()

        val pickerDir = customSaveDir ?: resolveCustomSaveAndroidDir(context, gameId, null)
        if (pickerDir != null && (forRestore || (pickerDir.exists() && !pickerDir.listFiles().isNullOrEmpty()))) {
            sources["custom/save"] = SaveBackupSource("custom/save", pickerDir)
            return sources.values.toList()
        }

        // Fall back to upstream's custom_game_folder + xuser dirs lookup.
        val shortcut =
            parseCustomGameId(gameId)?.let { (cid, f) ->
                findCustomShortcutByContainerAndFile(context, cid, f)
            } ?: findCustomShortcutByGameId(context, gameId)
            ?: return emptyList()

        val prefixDir = File(shortcut.container.rootDir, ".wine/drive_c/users/xuser")
        listOf("Documents", "Saved Games", "AppData").forEach { dirName ->
            val dir = File(prefixDir, dirName)
            if (forRestore || (dir.exists() && !dir.listFiles().isNullOrEmpty())) {
                sources["custom/$dirName"] = SaveBackupSource("custom/$dirName", dir)
            }
        }

        val customGameFolder =
            shortcut.getExtra(LEGACY_CUSTOM_GAME_FOLDER_KEY, "").takeIf { it.isNotBlank() }?.let(::File)
        if (customGameFolder != null &&
            (forRestore || (customGameFolder.exists() && !customGameFolder.listFiles().isNullOrEmpty()))
        ) {
            sources["custom/game_folder"] = SaveBackupSource("custom/game_folder", customGameFolder)
        }

        return sources.values.toList()
    }

    // ── Provider sync ──

    @Suppress("unused")
    private suspend fun syncDownFromProvider(
        context: Context,
        source: GameSource,
        gameId: String,
    ): Boolean {
        return try {
            when (source) {
                GameSource.STEAM -> false // Steam Cloud handled elsewhere.
                GameSource.EPIC -> {
                    val appId = gameId.toIntOrNull() ?: return false
                    EpicCloudSavesManager.syncCloudSaves(context, appId, "download")
                }
                GameSource.GOG -> GOGService.syncCloudSaves(context, "GOG_$gameId", "download")
                GameSource.CUSTOM -> false
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
                GameSource.STEAM -> false
                GameSource.EPIC -> {
                    val appId = gameId.toIntOrNull() ?: return false
                    EpicCloudSavesManager.syncCloudSaves(context, appId, "upload")
                }
                GameSource.GOG -> GOGService.syncCloudSaves(context, "GOG_$gameId", "upload")
                GameSource.CUSTOM -> false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "syncUpToProvider failed for $source/$gameId")
            false
        }
    }

    // ── Streaming gzip+zip → cache file (used by backup) ──

    private fun streamGzippedZipToFile(
        sources: List<SaveBackupSource>,
        outFile: File,
    ): Pair<Long, String> {
        val md = MessageDigest.getInstance("SHA-256")
        var uncompressed = 0L
        FileOutputStream(outFile).use { fos ->
            HashingOutputStream(fos, md).use { hashing ->
                GZIPOutputStream(hashing).use { gzip ->
                    val countingZip = CountingZipOutputStream(gzip)
                    countingZip.use { zos ->
                        sources.forEach { src ->
                            val zipRoot = src.zipRoot.trimEnd('/')
                            if (zipRoot.isEmpty()) return@forEach
                            zos.putNextEntry(ZipEntry("$zipRoot/"))
                            zos.closeEntry()
                            val exact = src.exactFiles?.filter { it.exists() }.orEmpty()
                            if (exact.isNotEmpty()) {
                                exact.forEach { file ->
                                    val rel =
                                        src.localDir
                                            .toPath()
                                            .relativize(file.toPath())
                                            .toString()
                                            .replace(File.separatorChar, '/')
                                    addFileToZip(zos, file, "$zipRoot/$rel")
                                }
                            } else if (src.localDir.exists()) {
                                zipDirRecursive(zos, src.localDir, zipRoot)
                            }
                        }
                    }
                    uncompressed = countingZip.bytesWritten
                }
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        return uncompressed to sha
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
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

    private fun zipDirRecursive(zos: ZipOutputStream, dir: File, baseName: String) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val entryName = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
            if (child.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
                zipDirRecursive(zos, child, entryName)
            } else {
                addFileToZip(zos, child, entryName)
            }
        }
    }

    private class HashingOutputStream(
        private val delegate: java.io.OutputStream,
        private val md: MessageDigest,
    ) : java.io.OutputStream() {
        override fun write(b: Int) {
            md.update(b.toByte())
            delegate.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            md.update(b, off, len)
            delegate.write(b, off, len)
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }

    private class CountingZipOutputStream(out: java.io.OutputStream) : ZipOutputStream(out) {
        var bytesWritten: Long = 0
            private set

        override fun write(b: ByteArray, off: Int, len: Int) {
            super.write(b, off, len)
            bytesWritten += len
        }

        override fun write(b: Int) {
            super.write(b)
            bytesWritten += 1
        }
    }

    // ── Snapshot upload / download / read / delete ──

    private suspend fun uploadParts(
        activity: Activity,
        client: SnapshotsClient,
        sourceFile: File,
        partNames: List<String>,
        partSize: Long,
    ): Boolean {
        // Allocate once and reuse — for a 50 MB save split into ~25 parts at ~3 MB each,
        // this avoids ~75 MB of transient allocations under GC pressure on exit-backup.
        val partBufferSize = partSize.toInt().coerceAtMost(Int.MAX_VALUE).coerceAtLeast(1)
        val part = ByteArray(partBufferSize)
        FileInputStream(sourceFile).use { fis ->
            for ((index, name) in partNames.withIndex()) {
                var off = 0
                while (off < part.size) {
                    val n = fis.read(part, off, part.size - off)
                    if (n <= 0) break
                    off += n
                }
                if (off == 0) {
                    Timber.tag(TAG).e("uploadParts: ran out of source bytes at part %d/%d", index, partNames.size)
                    return false
                }
                // writeBytes copies into the snapshot contents and Tasks.await blocks until
                // commit completes, so we can safely hand `part` directly on a full read and
                // only allocate a fresh array on the (at most one) partial last part.
                val data = if (off == part.size) part else part.copyOf(off)
                val ok =
                    writeSnapshot(
                        activity,
                        client,
                        uniqueName = name,
                        description = "WinNative save part ${index + 1}/${partNames.size} (do not select)",
                        playedTimeMs = 0L,
                        data = data,
                    )
                if (!ok) return false
            }
        }
        return true
    }

    private suspend fun downloadParts(
        client: SnapshotsClient,
        partNames: List<String>,
        outFile: File,
    ): Boolean {
        FileOutputStream(outFile).use { fos ->
            for (name in partNames) {
                val bytes = readSnapshotBytes(client, name) ?: return false
                fos.write(bytes)
            }
        }
        return true
    }

    private suspend fun writeSnapshot(
        activity: Activity,
        client: SnapshotsClient,
        uniqueName: String,
        description: String?,
        playedTimeMs: Long,
        data: ByteArray,
    ): Boolean {
        val snapshot = openSnapshot(activity, client, uniqueName, createIfMissing = true) ?: return false
        return try {
            if (!snapshot.snapshotContents.writeBytes(data)) {
                Timber.tag(TAG).e("writeBytes returned false for %s", uniqueName)
                runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                return false
            }
            val change =
                SnapshotMetadataChange.Builder()
                    .apply {
                        if (description != null) setDescription(description)
                        setPlayedTimeMillis(playedTimeMs)
                    }
                    .build()
            Tasks.await(client.commitAndClose(snapshot, change))
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "writeSnapshot failed for %s", uniqueName)
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            false
        }
    }

    private suspend fun readSnapshotBytes(client: SnapshotsClient, uniqueName: String): ByteArray? {
        return try {
            var result =
                Tasks.await(
                    client.open(
                        uniqueName,
                        false,
                        SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED,
                    ),
                )
            var conflictAttempts = 0
            while (result.isConflict && conflictAttempts < MAX_CONFLICT_RESOLVE_ATTEMPTS) {
                val chosen =
                    listOfNotNull(result.conflict?.snapshot, result.conflict?.conflictingSnapshot)
                        .maxByOrNull { it.metadata.lastModifiedTimestamp }
                        ?: return null
                result = Tasks.await(client.resolveConflict(result.conflict!!.conflictId, chosen))
                conflictAttempts++
            }
            if (result.isConflict) return null
            val snapshot = result.data ?: return null
            val bytes = snapshot.snapshotContents.readFully()
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            bytes
        } catch (e: Exception) {
            if (isMissingSnapshotError(e)) return null
            Timber.tag(TAG).w(e, "readSnapshotBytes failed for %s", uniqueName)
            null
        }
    }

    private suspend fun openSnapshot(
        @Suppress("UNUSED_PARAMETER") activity: Activity,
        client: SnapshotsClient,
        uniqueName: String,
        createIfMissing: Boolean,
    ): Snapshot? {
        repeat(AUTH_SESSION_RETRY_COUNT) { attempt ->
            try {
                var result =
                    Tasks.await(
                        client.open(
                            uniqueName,
                            createIfMissing,
                            SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED,
                        ),
                    )
                var conflictAttempts = 0
                while (result.isConflict && conflictAttempts < MAX_CONFLICT_RESOLVE_ATTEMPTS) {
                    val candidates = listOfNotNull(result.conflict?.snapshot, result.conflict?.conflictingSnapshot)
                    val chosen = candidates.maxByOrNull { it.metadata.lastModifiedTimestamp } ?: return null
                    result = Tasks.await(client.resolveConflict(result.conflict!!.conflictId, chosen))
                    conflictAttempts++
                }
                return if (result.isConflict) null else result.data
            } catch (e: Exception) {
                if (!createIfMissing && isMissingSnapshotError(e)) return null
                if (attempt < AUTH_SESSION_RETRY_COUNT - 1) {
                    Timber.tag(TAG).w(e, "openSnapshot %s failed; retrying", uniqueName)
                    delay(AUTH_SESSION_RETRY_DELAY_MS)
                    return@repeat
                }
                Timber.tag(TAG).e(e, "openSnapshot %s exhausted retries", uniqueName)
                throw e
            }
        }
        return null
    }

    private fun isMissingSnapshotError(error: Throwable): Boolean {
        val msg = error.message ?: return false
        return msg.contains("SNAPSHOT_NOT_FOUND", ignoreCase = true) ||
            msg.contains("status=4002", ignoreCase = true)
    }

    private suspend fun loadAllSnapshotsMetadata(client: SnapshotsClient): List<SnapshotMetaSummary> {
        val result =
            try {
                Tasks.await(client.load(false))
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Snapshots load failed")
                return emptyList()
            }
        val buffer = result.get() ?: return emptyList()
        val out = mutableListOf<SnapshotMetaSummary>()
        try {
            for (i in 0 until buffer.count) {
                val m: SnapshotMetadata = buffer[i] ?: continue
                out += SnapshotMetaSummary(
                    uniqueName = m.uniqueName ?: continue,
                    description = m.description,
                    lastModifiedTimestamp = m.lastModifiedTimestamp,
                )
            }
        } finally {
            buffer.release()
        }
        return out
    }

    private data class SnapshotMetaSummary(
        val uniqueName: String,
        val description: String?,
        val lastModifiedTimestamp: Long,
    )

    private suspend fun deleteSnapshotsByName(activity: Activity, names: Collection<String>) {
        if (names.isEmpty()) return
        val client = freshSnapshotsClient(activity) ?: return
        val summaries =
            try {
                // Force fresh so the rollback path sees just-written parts that haven't synced to local cache yet.
                val result = Tasks.await(client.load(true))
                val buffer = result.get() ?: return
                try {
                    val map = HashMap<String, SnapshotMetadata>()
                    for (i in 0 until buffer.count) {
                        val m = buffer[i] ?: continue
                        val n = m.uniqueName ?: continue
                        if (n in names) map[n] = m.freeze()
                    }
                    map
                } finally {
                    buffer.release()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "deleteSnapshotsByName: load failed")
                return
            }
        for ((_, meta) in summaries) {
            runCatching { Tasks.await(client.delete(meta)) }
                .onFailure { Timber.tag(TAG).w(it, "Snapshot delete failed for %s", meta.uniqueName) }
        }
    }

    private suspend fun deleteEntry(
        activity: Activity,
        client: SnapshotsClient,
        entry: BackupHistoryEntry,
    ): Boolean {
        val manifestBytes = readSnapshotBytes(client, entry.fileId)
        val manifest = manifestBytes?.let { Manifest.fromJson(String(it, Charsets.UTF_8)) }
        val partNames = manifest?.parts.orEmpty()
        if (partNames.isNotEmpty()) {
            deleteSnapshotsByName(activity, partNames)
        }
        deleteSnapshotsByName(activity, listOf(entry.fileId))
        return true
    }

    /** Google snapshot history is retired; retained as a no-op for legacy backup code paths. */
    private suspend fun pruneHistory(activity: Activity, gameSource: GameSource, gameId: String, gameName: String) {
        Unit
    }

    // ── Naming ──

    private fun manifestPrefix(source: GameSource, gameKey: String): String =
        "${SNAPSHOT_PREFIX}_${source.code}_${gameKey}_"

    private fun partPrefix(source: GameSource, gameKey: String): String =
        manifestPrefix(source, gameKey)

    private fun manifestUniqueName(source: GameSource, gameKey: String, saveId: String): String =
        "${SNAPSHOT_PREFIX}_${source.code}_${gameKey}_${saveId}_m"

    private fun partUniqueName(source: GameSource, gameKey: String, saveId: String, partIndex: Int): String =
        "${SNAPSHOT_PREFIX}_${source.code}_${gameKey}_${saveId}_p${"%03d".format(partIndex)}"

    private fun buildGameKeyHash(source: GameSource, gameId: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("${source.name}:$gameId".toByteArray(Charsets.UTF_8))
        val digest = md.digest()
        return digest.copyOfRange(0, 8).joinToString("") { "%02x".format(it) }
    }

    private val saveIdRandom = SecureRandom()

    private fun buildSaveId(timestampMs: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val ts = fmt.format(Date(timestampMs))
        val rand = StringBuilder(6)
        repeat(6) { rand.append(('a' + saveIdRandom.nextInt(26))) }
        return "${ts}_$rand"
    }

    private fun manifestDescription(origin: BackupOrigin, label: String?): String =
        if (label.isNullOrEmpty()) origin.tag else "${origin.tag}|$label"

    private fun parseLabelFromDescription(description: String?): String? {
        if (description.isNullOrEmpty()) return null
        val idx = description.indexOf('|')
        return if (idx >= 0 && idx < description.length - 1) description.substring(idx + 1) else null
    }

    fun sanitizeHistoryLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned =
            raw
                .replace(Regex("""[/\\:*?"<>|\r\n\t]"""), "")
                .trim()
                .take(MAX_HISTORY_LABEL_LENGTH)
        return cleaned.ifEmpty { null }
    }

    // ── Restore: extract ──

    private fun extractGzippedZipToSources(gzippedZipFile: File, sources: List<SaveBackupSource>) {
        val sortedSources = sources.sortedByDescending { it.zipRoot.length }
        FileInputStream(gzippedZipFile).use { fis ->
            GZIPInputStream(fis).use { gz ->
                ZipInputStream(gz).use { zis ->
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

    // ── Auth helpers ──

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isActivityValidForPlayGames(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val state = (activity as? LifecycleOwner)?.lifecycle?.currentState
        return state?.isAtLeast(Lifecycle.State.STARTED) ?: true
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

    /** Used by INTERACTIVE callers (manual backup, rename, delete, restore from history). */
    private suspend fun awaitAuthenticatedSession(activity: Activity): Boolean {
        if (!isActivityValidForPlayGames(activity)) return false
        PlayGamesBootstrap.ensureInitialized(activity)
        repeat(AUTH_SESSION_RETRY_COUNT) { attempt ->
            if (isAuthenticatedBlocking(activity)) return true
            // For INTERACTIVE, kick the sign-in client once on first miss.
            if (attempt == 0) {
                runCatching {
                    Tasks.await(PlayGames.getGamesSignInClient(activity).signIn(), 30, TimeUnit.SECONDS)
                }
            }
            if (attempt < AUTH_SESSION_RETRY_COUNT - 1) {
                delay(AUTH_SESSION_RETRY_DELAY_MS)
            }
        }
        return false
    }

    /**
     * Honors the GoogleAuthMode contract:
     *   - SILENT       single shot, no UI, no retry.
     *   - RESUME       silent retries to settle the SDK's background bootstrap. No UI.
     *   - INTERACTIVE  may launch the Play Games sign-in sheet.
     *
     * RESUME exists because on cold start `PlayGamesSdk.initialize` kicks off the silent
     * re-auth asynchronously, and `isAuthenticated.await()` can resolve `false` before
     * that background work lands. A short retry gives the SDK time to settle without
     * popping any UI. We only spend the retries when the user has previously connected
     * (per the on-disk pref) — a strong prior that silent auth SHOULD succeed.
     */
    private suspend fun ensureAuthenticated(activity: Activity, mode: GoogleAuthMode): Boolean {
        return when (mode) {
            GoogleAuthMode.SILENT -> isAuthenticatedBlocking(activity)
            GoogleAuthMode.RESUME -> awaitResumeAuth(activity)
            GoogleAuthMode.INTERACTIVE -> awaitAuthenticatedSession(activity)
        }
    }

    /** RESUME-mode auth: retry the silent check a few times to let the SDK settle. No signIn. */
    private suspend fun awaitResumeAuth(activity: Activity): Boolean {
        // No prior connection → don't pay retry cost; the user must explicitly Connect first.
        if (!isDriveConnected(activity.applicationContext)) {
            return isAuthenticatedBlocking(activity)
        }
        // Up to ~2.25s total (3 attempts × 750ms) — bounded enough to not feel like a hang
        // on a screen open, generous enough to cover slow-device cold starts.
        repeat(3) { attempt ->
            if (isAuthenticatedBlocking(activity)) return true
            if (attempt < 2) delay(AUTH_SESSION_RETRY_DELAY_MS)
        }
        return false
    }

    private suspend fun freshSnapshotsClient(activity: Activity): SnapshotsClient? {
        if (!isActivityValidForPlayGames(activity)) {
            Timber.tag(TAG).w(
                "Skipping snapshot client creation for %s because the activity is no longer active",
                activity::class.java.simpleName,
            )
            return null
        }
        PlayGamesBootstrap.ensureInitialized(activity)
        return PlayGames.getSnapshotsClient(activity)
    }
}
