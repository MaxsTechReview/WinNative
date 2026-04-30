package com.winlator.cmod.feature.sync.google

import android.app.Activity
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotContents
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Shared Play Games Saved Games (Snapshots) plumbing used by both the store-token
 * sync (CloudSyncManager) and the game-save backup (GameSaveBackupManager).
 *
 * No behavior change vs. the prior in-line helpers in CloudSyncManager — same
 * retry counts, same conflict-resolve loop, same error classification.
 */
internal object PgsSnapshotIO {
    private const val TAG = "PgsSnapshotIO"

    const val AUTH_SESSION_RETRY_COUNT = 5
    const val AUTH_SESSION_RETRY_DELAY_MS = 750L

    fun isActivityValidForPlayGames(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val state = (activity as? LifecycleOwner)?.lifecycle?.currentState
        return state?.isAtLeast(Lifecycle.State.STARTED) ?: true
    }

    suspend fun freshSnapshotsClient(activity: Activity): SnapshotsClient? {
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

    suspend fun isAuthenticatedBlocking(activity: Activity): Boolean {
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

    suspend fun awaitAuthenticatedSession(activity: Activity): Boolean {
        if (!isActivityValidForPlayGames(activity)) return false
        PlayGamesBootstrap.ensureInitialized(activity)
        repeat(AUTH_SESSION_RETRY_COUNT) { attempt ->
            if (isAuthenticatedBlocking(activity)) return true
            if (attempt < AUTH_SESSION_RETRY_COUNT - 1) {
                delay(AUTH_SESSION_RETRY_DELAY_MS)
            }
        }
        return false
    }

    /**
     * Open a snapshot by name with conflict-resolution and a transient sign-in retry.
     *
     * Returns null when [createIfMissing] is false and the snapshot does not exist.
     * Throws on hard errors after the retry budget is exhausted.
     */
    suspend fun openSnapshot(
        client: SnapshotsClient,
        name: String,
        createIfMissing: Boolean,
    ): Snapshot? {
        repeat(AUTH_SESSION_RETRY_COUNT) { attempt ->
            try {
                Timber.tag(TAG).d(
                    "SnapshotsClient.open(name=%s, createIfMissing=%s, attempt=%d)",
                    name,
                    createIfMissing,
                    attempt + 1,
                )
                val result =
                    Tasks.await(
                        client.open(
                            name,
                            createIfMissing,
                            SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED,
                        ),
                    )
                if (!result.isConflict) return result.data
                val resolved = resolveSnapshotConflict(client, name, result.conflict) ?: return null
                return resolved
            } catch (error: Exception) {
                if (!createIfMissing && isMissingSnapshotError(error)) {
                    Timber.tag(TAG).d("No existing snapshot %s: %s", name, error.message)
                    return null
                }
                if (isSignInRequiredError(error) && attempt < AUTH_SESSION_RETRY_COUNT - 1) {
                    Timber.tag(TAG).w(
                        error,
                        "Snapshot open hit transient sign-in state; retrying in %d ms",
                        AUTH_SESSION_RETRY_DELAY_MS,
                    )
                    delay(AUTH_SESSION_RETRY_DELAY_MS)
                    return@repeat
                }
                Timber.tag(TAG).e(error, "Snapshot open failed for createIfMissing=%s", createIfMissing)
                throw error
            }
        }
        return null
    }

    private suspend fun resolveSnapshotConflict(
        client: SnapshotsClient,
        name: String,
        conflict: SnapshotsClient.SnapshotConflict?,
    ): Snapshot? {
        if (conflict == null) return null
        var pending: SnapshotsClient.SnapshotConflict? = conflict
        while (pending != null) {
            val candidates =
                listOfNotNull(pending.snapshot, pending.conflictingSnapshot)
            if (candidates.isEmpty()) return null
            val chosen =
                candidates.maxByOrNull { it.metadata.lastModifiedTimestamp } ?: return null
            Timber.tag(TAG).w(
                "Snapshot conflict for %s; resolving with lastModified=%d",
                name,
                chosen.metadata.lastModifiedTimestamp,
            )
            val resolved = Tasks.await(client.resolveConflict(pending.conflictId, chosen))
            if (!resolved.isConflict) return resolved.data
            pending = resolved.conflict
        }
        return null
    }

    /**
     * List every snapshot the current user has for this app. Returns `null` on error.
     *
     * The buffer is closed before returning; callers receive copies of metadata
     * fields they need (name, last-modified, etc.) so we don't leak the buffer.
     */
    suspend fun loadAllSnapshotMetadata(client: SnapshotsClient, forceReload: Boolean): List<SnapshotInfo>? {
        return try {
            val annotated = Tasks.await(client.load(forceReload))
            val buffer = annotated.get() ?: return emptyList()
            try {
                val out = ArrayList<SnapshotInfo>(buffer.count)
                for (i in 0 until buffer.count) {
                    val md: SnapshotMetadata = buffer.get(i)
                    out += SnapshotInfo(
                        snapshotId = md.snapshotId,
                        uniqueName = md.uniqueName,
                        lastModifiedTimestamp = md.lastModifiedTimestamp,
                    )
                }
                out
            } finally {
                buffer.release()
            }
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "loadAllSnapshotMetadata failed")
            null
        }
    }

    /**
     * Delete a snapshot by opening it (read-only) just to obtain a metadata handle,
     * then issuing client.delete(). Returns true on success, false on any failure.
     *
     * The Snapshots v2 API only exposes delete(SnapshotMetadata), so we have to round-trip
     * through open() to materialize the metadata. createIfMissing=false ensures a missing
     * target is treated as already-deleted.
     */
    suspend fun deleteSnapshotByName(client: SnapshotsClient, name: String): Boolean {
        return try {
            val snapshot = openSnapshot(client, name, createIfMissing = false) ?: return true
            val md = snapshot.metadata
            // Discard the open handle before deleting (open() leaves us holding the contents)
            runCatching { Tasks.await(client.discardAndClose(snapshot)) }
            Tasks.await(client.delete(md))
            true
        } catch (error: Exception) {
            if (isMissingSnapshotError(error)) return true
            Timber.tag(TAG).w(error, "deleteSnapshotByName(%s) failed", name)
            false
        }
    }

    fun snapshotParcelFileDescriptor(contents: SnapshotContents?): ParcelFileDescriptor? =
        runCatching { contents?.parcelFileDescriptor }.getOrNull()

    fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        try {
            descriptor?.close()
        } catch (_: Exception) {
        }
    }

    fun isMissingSnapshotError(error: Throwable): Boolean {
        val apiStatusCode =
            generateSequence(error) { it.cause }
                .filterIsInstance<ApiException>()
                .map { it.statusCode }
                .firstOrNull()
        val rawMessage =
            generateSequence(error) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
        return apiStatusCode == 26504 ||
            "snapshot_not_found" in rawMessage ||
            "snapshot not found" in rawMessage ||
            "no snapshot" in rawMessage
    }

    fun isSignInRequiredError(error: Throwable): Boolean {
        val apiStatusCode =
            generateSequence(error) { it.cause }
                .filterIsInstance<ApiException>()
                .map { it.statusCode }
                .firstOrNull()
        val rawMessage =
            generateSequence(error) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
        return apiStatusCode == CommonStatusCodes.SIGN_IN_REQUIRED ||
            "sign_in_required" in rawMessage ||
            "statuscode=4" in rawMessage
    }

    /** Small data carrier so callers don't have to keep the SnapshotMetadataBuffer alive. */
    data class SnapshotInfo(
        val snapshotId: String?,
        val uniqueName: String,
        val lastModifiedTimestamp: Long,
    )
}
