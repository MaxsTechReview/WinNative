package com.winlator.cmod.shared.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.app.service.DownloadService
import com.winlator.cmod.feature.stores.epic.service.EpicService
import com.winlator.cmod.feature.stores.epic.service.EpicTokenRefreshWorker
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.steam.events.AndroidEvent
import com.winlator.cmod.feature.stores.steam.service.SteamService
import timber.log.Timber

object AppTerminationHelper {
    @JvmStatic
    fun stopManagedServices(
        context: Context,
        reason: String,
    ) {
        val appContext = context.applicationContext
        Timber.i("Stopping managed services for app shutdown (%s)", reason)

        // Stop active transfers without deleting partial download state.
        runCatching { DownloadService.pauseAll() }
            .onFailure { Timber.w(it, "Failed to pause downloads during shutdown") }

        runCatching { EpicTokenRefreshWorker.cancel(appContext) }
            .onFailure { Timber.w(it, "Failed to cancel Epic refresh worker during shutdown") }

        runCatching { PluviaApp.events.emit(AndroidEvent.EndProcess) }
            .onFailure { Timber.w(it, "Failed to emit EndProcess during shutdown") }

        runCatching { SteamService.stop() }
            .onFailure { Timber.w(it, "Failed to stop SteamService during shutdown") }
        runCatching { EpicService.stop() }
            .onFailure { Timber.w(it, "Failed to stop EpicService during shutdown") }
        runCatching { GOGService.stop() }
            .onFailure { Timber.w(it, "Failed to stop GOGService during shutdown") }

        stopServiceSafely<SteamService>(appContext)
        stopServiceSafely<EpicService>(appContext)
        stopServiceSafely<GOGService>(appContext)
    }

    @JvmStatic
    fun exitApplication(
        activity: Activity,
        reason: String,
    ) {
        stopManagedServices(activity, reason)
        activity.finishAffinity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.finishAndRemoveTask()
        }
        Process.killProcess(Process.myPid())
    }

    private inline fun <reified T> stopServiceSafely(context: Context) {
        runCatching { context.stopService(Intent(context, T::class.java)) }
            .onFailure { Timber.w(it, "Failed to stop ${T::class.java.simpleName} via context.stopService()") }
    }
}
