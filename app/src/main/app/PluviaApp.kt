package com.winlator.cmod.app
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.winlator.cmod.app.update.UpdateChecker
import com.winlator.cmod.feature.stores.gog.service.GOGAuthManager
import com.winlator.cmod.feature.stores.gog.service.GOGConstants
import com.winlator.cmod.feature.stores.steam.events.EventDispatcher
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.shared.android.RefreshRateUtils
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class PluviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        registerRefreshRateLifecycleCallbacks()

        // Replace Android's limited BouncyCastle provider with the full one
        // so that JavaSteam can use SHA-1 (and other algorithms) via the "BC" provider.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        // Register application context so secure Steam prefs can initialize lazily.
        PrefManager.install(this)
        GOGConstants.init(this)
        GOGAuthManager.updateLoginStatus(this)

        if (PrefManager.enableSteamLogs) {
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
        }

        if (UpdateChecker.isEnabled(this)) {
            UpdateChecker.refreshInstallTimestamp(this)
        }

        // Rotate logs on app cold start (.log → .old.log) so previous
        // session's logs are preserved until the next full launch.
        com.winlator.cmod.runtime.system.LogManager
            .rotateLogsOnAppStart(this)

        // Start Application debug logging if enabled (writes PID logcat
        // in real-time so crash data is persisted even on unexpected termination)
        com.winlator.cmod.runtime.system.LogManager
            .startAppLogging(this)

        // Initialize process-wide reactive network state
        com.winlator.cmod.app.service.NetworkMonitor
            .init(this)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PluviaApp", "CRASH in thread ${thread.name}", throwable)
        }
    }

    companion object {
        lateinit var instance: PluviaApp
            private set

        @Volatile
        var currentForegroundActivity: Activity? = null
            private set

        @JvmField
        val events = EventDispatcher()
    }

    private fun registerRefreshRateLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    if (shouldManageAppRefreshRate(activity)) {
                        RefreshRateUtils.onActivityCreated(activity)
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    currentForegroundActivity = activity
                    if (shouldManageAppRefreshRate(activity)) {
                        RefreshRateUtils.onActivityResumed(activity)
                    }
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {
                    if (currentForegroundActivity === activity) {
                        currentForegroundActivity = null
                    }
                }

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) {}

                override fun onActivityDestroyed(activity: Activity) {
                    if (shouldManageAppRefreshRate(activity)) {
                        RefreshRateUtils.onActivityDestroyed(activity)
                    }
                    if (currentForegroundActivity === activity) {
                        currentForegroundActivity = null
                    }
                }
            },
        )
    }

    private fun shouldManageAppRefreshRate(activity: Activity): Boolean {
        // Game windows own per-title refresh policy and should not inherit the global app override.
        return activity !is XServerDisplayActivity
    }
}
