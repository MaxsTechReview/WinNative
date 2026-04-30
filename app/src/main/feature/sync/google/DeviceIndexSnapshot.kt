package com.winlator.cmod.feature.sync.google

import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Cross-device index of which devices have backed up Steam/Epic/GOG tokens to PGS.
 *
 * Stored at the account-wide snapshot name [SNAPSHOT_NAME]. Body is gzipped JSON.
 * Carries no secrets — only `(deviceId, label, lastModifiedMs, updateNonce)` per
 * device. The picker UI on a fresh install reads this to show the user which
 * devices have backups they could restore from.
 *
 * Conflict resolution (multiple devices write nearly-simultaneously):
 *   - Open with RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED.
 *   - On any leftover SnapshotConflict, both versions are decoded and the entry
 *     lists are merged via [merge], which is monotonic and commutative
 *     (CRDT-style) so both devices converge on the same result regardless of
 *     resolution order.
 */
internal object DeviceIndexSnapshot {
    private const val TAG = "DeviceIndex"
    const val SNAPSHOT_NAME = "sl_index_v1"
    private const val VERSION = 1

    data class Entry(
        val deviceId: String,
        val label: String,
        val lastModifiedMs: Long,
        /** 8-hex-char nonce regenerated per write; deterministic tie-break in [merge]. */
        val updateNonce: String,
    )

    data class Index(
        val version: Int,
        val devices: List<Entry>,
    )

    /**
     * Monotonic merge: union of (deviceId → entry), keeping the entry with the
     * largest `lastModifiedMs`; ties broken by lex-smaller `updateNonce` then
     * `label`. Result is independent of input order.
     */
    fun merge(a: List<Entry>, b: List<Entry>): List<Entry> =
        (a + b)
            .groupBy { it.deviceId }
            .map { (_, entries) ->
                entries.maxWithOrNull(
                    compareBy<Entry> { it.lastModifiedMs }
                        .thenByDescending { it.updateNonce }   // smaller nonce wins (we negate via Descending)
                        .thenByDescending { it.label }
                )!!
            }

    fun newNonce(): String {
        val bytes = ByteArray(4)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Read the index from PGS; returns empty index if the snapshot is missing. */
    suspend fun read(client: SnapshotsClient): Index {
        val snapshot = PgsSnapshotIO.openSnapshot(client, SNAPSHOT_NAME, createIfMissing = false)
            ?: return Index(version = VERSION, devices = emptyList())
        val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
        return try {
            val bytes = snapshot.snapshotContents.readFully()
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            if (bytes.isEmpty()) Index(version = VERSION, devices = emptyList()) else decode(bytes)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "read failed")
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            Index(version = VERSION, devices = emptyList())
        } finally {
            PgsSnapshotIO.closeQuietly(pfd)
        }
    }

    /**
     * Read-modify-write the index, merging in [entry] and replacing any prior
     * row for the same `deviceId` with the new one (the new write always
     * supersedes for this device). Returns true on success.
     */
    suspend fun upsert(client: SnapshotsClient, entry: Entry): Boolean {
        val current = read(client)
        val withoutSelf = current.devices.filterNot { it.deviceId == entry.deviceId }
        val updated = current.copy(devices = withoutSelf + entry)
        return write(client, updated)
    }

    suspend fun write(client: SnapshotsClient, index: Index): Boolean {
        val bytes = encode(index)
        val snapshot = PgsSnapshotIO.openSnapshot(client, SNAPSHOT_NAME, createIfMissing = true)
            ?: return false
        val pfd = PgsSnapshotIO.snapshotParcelFileDescriptor(snapshot.snapshotContents)
        return try {
            if (!snapshot.snapshotContents.writeBytes(bytes)) {
                runCatching { Tasks.await(client.discardAndClose(snapshot)) }
                Timber.tag(TAG).e("writeBytes failed (%d bytes)", bytes.size)
                return false
            }
            val md = SnapshotMetadataChange.Builder()
                .setDescription("Device index (${index.devices.size} devices)")
                .setProgressValue(index.devices.size.toLong())
                .build()
            Tasks.await(client.commitAndClose(snapshot, md))
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "write failed")
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            false
        } finally {
            PgsSnapshotIO.closeQuietly(pfd)
        }
    }

    fun encode(index: Index): ByteArray {
        val arr = JSONArray()
        index.devices.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("deviceId", e.deviceId)
                    put("label", e.label)
                    put("lastModifiedMs", e.lastModifiedMs)
                    put("updateNonce", e.updateNonce)
                },
            )
        }
        val obj = JSONObject().apply {
            put("version", index.version)
            put("devices", arr)
        }
        val raw = obj.toString().toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(raw) }
        return baos.toByteArray()
    }

    fun decode(bytes: ByteArray): Index {
        return try {
            val raw = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            val obj = JSONObject(String(raw, StandardCharsets.UTF_8))
            val version = obj.optInt("version", VERSION)
            val arr = obj.optJSONArray("devices") ?: JSONArray()
            val devices = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("deviceId").takeIf { it.isNotEmpty() } ?: continue
                devices += Entry(
                    deviceId = id,
                    label = o.optString("label", ""),
                    lastModifiedMs = o.optLong("lastModifiedMs", 0L),
                    updateNonce = o.optString("updateNonce", ""),
                )
            }
            Index(version = version, devices = devices)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "decode failed")
            Index(version = VERSION, devices = emptyList())
        }
    }
}
