package com.winlator.cmod.feature.steamcloudsync

import android.app.Activity
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.runtime.container.Shortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

object SteamExitCloudSync {
    fun interface StatusSink {
        fun show(text: String)
    }

    fun interface ResultCallback {
        fun onComplete(result: Result)
    }

    data class Result(
        val success: Boolean,
        val message: String,
        val retryable: Boolean,
    )

    @JvmStatic
    fun syncOnExit(
        activity: Activity,
        shortcut: Shortcut?,
        statusSink: StatusSink,
        callback: ResultCallback,
    ) {
        if (shortcut?.getExtra("game_source") != "STEAM") {
            callback.onComplete(Result(success = true, message = "", retryable = false))
            return
        }

        val appId = shortcut.getExtra("app_id").toIntOrNull()
        if (appId == null) {
            callback.onComplete(Result(success = false, message = "Invalid Steam app id.", retryable = false))
            return
        }

        Timber.tag("SteamExitCloudSync").d("Syncing Steam cloud saves for appId=%d", appId)
        statusSink.show(activity.getString(R.string.preloader_uploading_cloud))

        try {
            SteamService.syncCloudOnExit(
                activity,
                appId,
                object : SteamService.Companion.CloudSyncCallback {
                    override fun onProgress(
                        message: String,
                        progress: Float,
                    ) {
                        val percent = (progress * 100).toInt()
                        activity.runOnUiThread {
                            statusSink.show("$message ($percent%)")
                        }
                    }

                    override fun onComplete(
                        success: Boolean,
                        message: String,
                    ) {
                        if (success) {
                            // Capture a rollback snapshot of the just-uploaded state. Best-effort —
                            // fires on a detached IO scope so we don't delay the exit dialog.
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    SteamSaveSnapshotManager.recordSnapshot(
                                        activity.applicationContext,
                                        appId,
                                        GameSaveBackupManager.BackupOrigin.AUTO,
                                    )
                                }.onFailure {
                                    Timber.tag("SteamExitCloudSync").w(
                                        it,
                                        "recordSnapshot failed for appId=%d",
                                        appId,
                                    )
                                }
                            }
                        }
                        callback.onComplete(
                            Result(
                                success = success,
                                message = message,
                                retryable = isRetryable(message),
                            ),
                        )
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("SteamExitCloudSync").w(e, "Failed to initiate Steam cloud sync")
            callback.onComplete(Result(success = false, message = e.message ?: "Steam cloud sync failed.", retryable = true))
        }
    }

    /**
     * A failure is retryable only when re-running the sync stands a chance of succeeding.
     *
     * Steam protocol (per `steammessages_cloud.steamclient.proto` + EResult conventions):
     *  - `RemoteFileConflict` (Conflict)    — needs user intervention via the launch-time
     *                                          dialog. Retry will keep hitting the same Conflict.
     *  - `Busy` / `InProgress` / pending    — transient lock; we let the outer retry handle it.
     *  - `AccessDenied` / auth issues       — need re-auth; pointless to retry the same call.
     *  - `Offline` / no connection          — bounded retry won't help on a tap-out cycle.
     *  - `UpdateFail` / `DownloadFail`      — transient network/server issue; worth retrying.
     *  - Any other transient (Timeout, etc) — retry with backoff.
     *
     * Match strings produced by [SteamService.closeApp]'s discriminating `lastErrorMessage`.
     */
    private fun isRetryable(message: String?): Boolean {
        if (message.isNullOrEmpty()) return true
        val lower = message.lowercase()
        return when {
            lower.contains("conflict") -> false
            lower.contains("pending") -> false
            lower.contains("access denied") -> false
            lower.contains("not signed in") -> false
            lower.contains("logged in elsewhere") -> false
            lower.contains("offline") -> false
            else -> true
        }
    }
}
