package com.winlator.cmod.feature.steamcloudsync

import android.app.Activity
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.runtime.container.Shortcut
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
        statusSink.show("Cloud Sync Uploading...")

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

    private fun isRetryable(message: String?): Boolean {
        if (message.isNullOrEmpty()) return true
        return !message.lowercase().contains("offline")
    }
}
