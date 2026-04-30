package com.winlator.cmod.feature.sync.google

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Tasks

/**
 * Per-device backup of the user's Download Folder preferences (5 keys in PrefManager).
 * Stored at PGS snapshot `df_<deviceId>_v1` as gzipped JSON.
 *
 * Auto-backup is triggered by [PrefManager] setters for the 5 download-folder keys
 * via [scheduleAutoBackup] (60-second debounce, mirrors CloudSyncManager pattern).
 *
 * Auto-restore is triggered on Google sign-in completion: see [autoRestoreFromCloud].
 * Cross-device restore is intentionally NOT supported — paths are device-local
 * (`/storage/...`, SAF tree URIs, etc.) and rarely portable.
 */
internal object DownloadFolderSyncManager {
    private const val TAG = "DownloadFolderSync"
    private const val SNAPSHOT_PREFIX = "df_"
    private const val SNAPSHOT_SUFFIX = "_v1"

    // Mirrors CloudSyncManager.AUTO_BACKUP_MIN_INTERVAL_MS so a burst of folder
    // changes coalesces into one upload per minute at most.
    private const val AUTO_BACKUP_MIN_INTERVAL_MS = 60_000L
    @Volatile private var lastAutoBackupAttemptMs: Long = 0L

    // Suppress the auto-backup hook while we're populating prefs from the cloud.
    @Volatile private var restoreInProgress: Boolean = false

    private const val PREFS_GATE = "google_store_login_sync"
    private const val KEY_GOOGLE_SYNC_ENABLED = "google_sync_enabled"
    private const val KEY_PENDING_BACKUP = "download_folder_backup_pending"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val opMutex = Mutex()

    fun snapshotName(context: Context): String =
        "$SNAPSHOT_PREFIX${DeviceIdentity.deviceId(context)}$SNAPSHOT_SUFFIX"

    /**
     * Hook called by PrefManager setters when any of the 5 download-folder keys changes.
     * Fire-and-forget; debounces and defers to next foreground if no Activity is attached.
     */
    fun scheduleAutoBackup(context: Context) {
        if (restoreInProgress) return
        if (!isGoogleSyncEnabled(context)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoBackupAttemptMs < AUTO_BACKUP_MIN_INTERVAL_MS) {
            // Mark pending so the next foreground/credential-change can flush.
            gatePrefs(context).edit().putBoolean(KEY_PENDING_BACKUP, true).apply()
            return
        }
        lastAutoBackupAttemptMs = now

        val activity = com.winlator.cmod.app.shell.UnifiedActivity.currentActivity()
        if (activity == null) {
            gatePrefs(context).edit().putBoolean(KEY_PENDING_BACKUP, true).apply()
            return
        }

        scope.launch {
            if (performBackupUpload(activity)) {
                gatePrefs(context).edit().putBoolean(KEY_PENDING_BACKUP, false).apply()
            } else {
                gatePrefs(context).edit().putBoolean(KEY_PENDING_BACKUP, true).apply()
            }
        }
    }

    /** Drain a previously-deferred auto-backup on Activity foreground. */
    suspend fun flushPendingAutoBackup(activity: Activity) {
        if (!gatePrefs(activity).getBoolean(KEY_PENDING_BACKUP, false)) return
        if (!isGoogleSyncEnabled(activity)) {
            gatePrefs(activity).edit().putBoolean(KEY_PENDING_BACKUP, false).apply()
            return
        }
        if (performBackupUpload(activity)) {
            gatePrefs(activity).edit().putBoolean(KEY_PENDING_BACKUP, false).apply()
            lastAutoBackupAttemptMs = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Manual "Back up download folders now" entry point (settings UI button).
     * Returns a user-readable result message.
     */
    suspend fun backupNow(activity: Activity): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isGoogleSyncEnabled(activity)) return@withContext false to "Google sync is not enabled."
        if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) {
            return@withContext false to "Not signed in to Google Play Games."
        }
        val ok = performBackupUpload(activity)
        if (ok) true to "Download folders backed up." else false to "Backup failed."
    }

    /**
     * Auto-restore at Google sign-in time. Reads `df_<thisDeviceId>_v1` only;
     * never crosses devices because paths/URIs are device-local. Validates each
     * path before writing to PrefManager — paths that no longer exist on disk
     * are skipped to avoid blanking working settings.
     *
     * Returns the count of keys written (0 if no snapshot or all paths invalid).
     */
    suspend fun autoRestoreFromCloud(activity: Activity): Int = withContext(Dispatchers.IO) {
        if (!isGoogleSyncEnabled(activity)) return@withContext 0
        if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) return@withContext 0
        opMutex.withLock {
            try {
                val client = PgsSnapshotIO.freshSnapshotsClient(activity) ?: return@withContext 0
                val name = snapshotName(activity)
                val snapshot = PgsSnapshotIO.openSnapshot(client, name, createIfMissing = false)
                    ?: return@withContext 0
                val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
                val bytes = try {
                    val b = snapshot.snapshotContents.readFully()
                    runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                    b
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "read failed")
                    runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                    return@withContext 0
                } finally {
                    PgsSnapshotIO.closeQuietly(pfd)
                }
                if (bytes.isEmpty()) return@withContext 0

                val payload = decode(bytes) ?: return@withContext 0
                applyToPrefs(activity, payload)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "autoRestoreFromCloud failed")
                0
            }
        }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private suspend fun performBackupUpload(activity: Activity): Boolean = withContext(Dispatchers.IO) {
        opMutex.withLock {
            try {
                if (!PgsSnapshotIO.awaitAuthenticatedSession(activity)) return@withContext false
                val client = PgsSnapshotIO.freshSnapshotsClient(activity) ?: return@withContext false
                val name = snapshotName(activity)
                val payload = collectPrefs(activity)
                val bytes = encode(payload)

                val snapshot = PgsSnapshotIO.openSnapshot(client, name, createIfMissing = true)
                    ?: return@withContext false
                val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
                try {
                    if (!snapshot.snapshotContents.writeBytes(bytes)) {
                        runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                        Timber.tag(TAG).e("writeBytes failed (%d bytes)", bytes.size)
                        return@withContext false
                    }
                    val md = SnapshotMetadataChange.Builder()
                        .setDescription("Download folders for ${DeviceIdentity.deviceLabel(activity)}")
                        .setProgressValue(payload.entries.size.toLong())
                        .build()
                    Tasks.await(client.commitAndClose(snapshot, md))
                    Timber.tag(TAG).i("Backed up download folders to %s", name)
                    true
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "performBackupUpload failed")
                    runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                    false
                } finally {
                    PgsSnapshotIO.closeQuietly(pfd)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "performBackupUpload outer failure")
                false
            }
        }
    }

    private data class Payload(
        val version: Int,
        val entries: Map<String, String>,
    )

    private fun collectPrefs(context: Context): Payload {
        PrefManager.init(context)
        val entries = linkedMapOf<String, String>()
        entries["use_single_download_folder"] = PrefManager.useSingleDownloadFolder.toString()
        entries["default_download_folder"] = PrefManager.defaultDownloadFolder
        entries["steam_download_folder"] = PrefManager.steamDownloadFolder
        entries["epic_download_folder"] = PrefManager.epicDownloadFolder
        entries["gog_download_folder"] = PrefManager.gogDownloadFolder
        return Payload(version = 1, entries = entries)
    }

    /**
     * Write payload back to PrefManager, skipping path entries whose path doesn't
     * exist on this device. Returns the count of entries actually written.
     *
     * Sets [restoreInProgress] for the duration so the setter hooks don't trigger
     * a fresh auto-backup mid-restore.
     */
    private fun applyToPrefs(context: Context, payload: Payload): Int {
        PrefManager.init(context)
        val touched = mutableSetOf<String>()
        restoreInProgress = true
        try {
            payload.entries["use_single_download_folder"]?.let { v ->
                PrefManager.useSingleDownloadFolder = v.toBoolean()
                touched += "use_single_download_folder"
            }
            payload.entries["default_download_folder"]?.takeIf { pathExists(it) }?.let { v ->
                PrefManager.defaultDownloadFolder = v
                touched += "default_download_folder"
            }
            payload.entries["steam_download_folder"]?.takeIf { pathExists(it) }?.let { v ->
                PrefManager.steamDownloadFolder = v
                touched += "steam_download_folder"
            }
            payload.entries["epic_download_folder"]?.takeIf { pathExists(it) }?.let { v ->
                PrefManager.epicDownloadFolder = v
                touched += "epic_download_folder"
            }
            payload.entries["gog_download_folder"]?.takeIf { pathExists(it) }?.let { v ->
                PrefManager.gogDownloadFolder = v
                touched += "gog_download_folder"
            }
        } finally {
            restoreInProgress = false
        }
        Timber.tag(TAG).i("Restored %d download-folder pref(s): %s", touched.size, touched)
        return touched.size
    }

    /**
     * True if the given pref value points to something that exists on this device.
     * Empty string counts as "valid" (it just means unset). For `file://` URIs we
     * check the underlying File. For `content://` URIs we don't have a permission
     * grant after restore so we conservatively treat them as invalid (the user can
     * re-pick). Other values pass through.
     */
    private fun pathExists(value: String): Boolean {
        if (value.isBlank()) return true   // unset is fine to write
        return when {
            value.startsWith("file://") -> {
                val path = value.removePrefix("file://")
                java.io.File(path).exists()
            }
            value.startsWith("content://") -> false
            else -> java.io.File(value).exists() || value.length < 4 // small non-path values pass
        }
    }

    private fun encode(payload: Payload): ByteArray {
        val obj = JSONObject().apply {
            put("version", payload.version)
            val data = JSONObject()
            payload.entries.forEach { (k, v) -> data.put(k, v) }
            put("entries", data)
        }
        val raw = obj.toString().toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(raw) }
        return baos.toByteArray()
    }

    private fun decode(bytes: ByteArray): Payload? {
        return try {
            val raw = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            val obj = JSONObject(String(raw, StandardCharsets.UTF_8))
            val version = obj.optInt("version", 1)
            val data = obj.optJSONObject("entries") ?: JSONObject()
            val entries = linkedMapOf<String, String>()
            data.keys().forEach { k -> entries[k] = data.optString(k, "") }
            Payload(version = version, entries = entries)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "decode failed")
            null
        }
    }

    private fun gatePrefs(context: Context) =
        context.getSharedPreferences(PREFS_GATE, Context.MODE_PRIVATE)

    private fun isGoogleSyncEnabled(context: Context): Boolean =
        gatePrefs(context).getBoolean(KEY_GOOGLE_SYNC_ENABLED, false)
}
