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
import com.winlator.cmod.feature.stores.steam.chat.ChatOverlayService
import com.winlator.cmod.feature.stores.steam.events.AndroidEvent
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import timber.log.Timber

object AppTerminationHelper {
    @JvmStatic
    @JvmOverloads
    fun stopManagedServices(
        context: Context,
        reason: String,
        forceStopChat: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val keepChatAlive = !forceStopChat && PrefManager.chatStayRunningOnExit
        Timber.i("Stopping managed services for app shutdown (%s), keepChatAlive=%b", reason, keepChatAlive)

        // Don't pauseAll() here: DOWNLOADING statuses persist and auto-resume next launch; pausing would force manual resume.
        runCatching {
            com.winlator.cmod.app.service.download.DownloadCoordinator.onAppExit()
        }.onFailure { Timber.w(it, "Failed to notify DownloadCoordinator during shutdown") }

        runCatching { DownloadService.clearCompletedDownloadsBlocking() }
            .onFailure { Timber.w(it, "Failed to clear completed download history during shutdown") }

        runCatching { EpicTokenRefreshWorker.cancel(appContext) }
            .onFailure { Timber.w(it, "Failed to cancel Epic refresh worker during shutdown") }

        if (!keepChatAlive) {
            runCatching { PluviaApp.events.emit(AndroidEvent.EndProcess) }
                .onFailure { Timber.w(it, "Failed to emit EndProcess during shutdown") }

            runCatching { SteamService.stop() }
                .onFailure { Timber.w(it, "Failed to stop SteamService during shutdown") }
        }
        runCatching { EpicService.stop() }
            .onFailure { Timber.w(it, "Failed to stop EpicService during shutdown") }
        runCatching { GOGService.stop() }
            .onFailure { Timber.w(it, "Failed to stop GOGService during shutdown") }

        if (!keepChatAlive) {
            stopServiceSafely<SteamService>(appContext)
            runCatching { ChatOverlayService.stop(appContext) }
                .onFailure { Timber.w(it, "Failed to stop ChatOverlayService during shutdown") }
        }
        stopServiceSafely<EpicService>(appContext)
        stopServiceSafely<GOGService>(appContext)
    }

    @JvmStatic
    fun exitApplication(
        activity: Activity,
        reason: String,
    ) {
        val keepChatAlive = PrefManager.chatStayRunningOnExit
        stopManagedServices(activity, reason)
        activity.finishAffinity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.finishAndRemoveTask()
        }
        if (!keepChatAlive) {
            Process.killProcess(Process.myPid())
        }
    }

    private inline fun <reified T> stopServiceSafely(context: Context) {
        runCatching { context.stopService(Intent(context, T::class.java)) }
            .onFailure { Timber.w(it, "Failed to stop ${T::class.java.simpleName} via context.stopService()") }
    }
}
