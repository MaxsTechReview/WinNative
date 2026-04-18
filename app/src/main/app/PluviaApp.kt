package com.winlator.cmod.app
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.games.PlayGamesSdk
import com.winlator.cmod.app.service.DownloadService
import com.winlator.cmod.app.update.UpdateChecker
import com.winlator.cmod.feature.setup.SetupWizardActivity
import com.winlator.cmod.feature.stores.gog.service.GOGAuthManager
import com.winlator.cmod.feature.stores.gog.service.GOGConstants
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.steam.events.EventDispatcher
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.shared.android.RefreshRateUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class PluviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Start the app-wide teardown service early so it's alive to receive
        // onTaskRemoved from the very first swipe-away, regardless of which
        // Activity the user is in. Wrapped in try/catch because background
        // startService can throw IllegalStateException on API 26+ in some
        // app-init paths (ContentProvider-triggered Application starts).
        try {
            startService(android.content.Intent(this, com.winlator.cmod.runtime.display.SessionTeardownService::class.java))
        } catch (t: Throwable) {
            Log.w("PluviaApp", "Failed to start SessionTeardownService", t)
        }

        // Initialize Play Games Services SDK (v2)
        PlayGamesSdk.initialize(this)

        registerRefreshRateLifecycleCallbacks()

        // Replace Android's limited BouncyCastle provider with the full one
        // so that JavaSteam can use SHA-1 (and other algorithms) via the "BC" provider.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        // Init our datastore preferences.
        PrefManager.init(this)
        GOGConstants.init(this)
        GOGAuthManager.updateLoginStatus(this)

        if (PrefManager.enableSteamLogs) {
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
        }

        // Record install timestamp for update checker
        UpdateChecker.refreshInstallTimestamp(this)

        // Rotate logs on app cold start (.log → .old.log) so previous
        // session's logs are preserved until the next full launch.
        com.winlator.cmod.runtime.system.LogManager
            .rotateLogsOnAppStart(this)

        // Start Application debug logging if enabled (writes PID logcat
        // in real-time so crash data is persisted even on unexpected termination)
        com.winlator.cmod.runtime.system.LogManager
            .startAppLogging(this)

        DownloadService.populateDownloadService(this)

        // Initialize process-wide reactive network state
        com.winlator.cmod.app.service.NetworkMonitor
            .init(this)

        // Initialize database
        com.winlator.cmod.app.db.PluviaDatabase
            .init(this)

        CoroutineScope(Dispatchers.IO).launch {
            SteamService.repairInstalledMetadataFromDisk()
        }

        // Start SteamService only if setup is complete to avoid premature permission popups.
        // Guard each start: if the service is already alive (e.g. Android kept the process
        // hot across a quick swipe+relaunch), skip the redundant startForegroundService so
        // we don't re-enter its init path and fire a spurious null-intent onStartCommand.
        try {
            if (SetupWizardActivity.isSetupComplete(this)) {
                if (!SteamService.isRunning) {
                    val intent = android.content.Intent(this, com.winlator.cmod.feature.stores.steam.service.SteamService::class.java)
                    startForegroundService(intent)
                } else {
                    Log.d("PluviaApp", "SteamService already running — skipping startForegroundService")
                }
                if (GOGAuthManager.isLoggedIn(this)) {
                    if (!GOGService.isRunning) {
                        val gogIntent = android.content.Intent(this, GOGService::class.java)
                        startForegroundService(gogIntent)
                    } else {
                        Log.d("PluviaApp", "GOGService already running — skipping startForegroundService")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PluviaApp", "Failed to start SteamService", e)
        }

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
