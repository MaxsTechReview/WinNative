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

        // Initialize Play Games Services SDK (v2) eagerly so auto sign-in fires at app launch.
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

        // Start SteamService only if setup is complete to avoid premature permission popups
        try {
            if (SetupWizardActivity.isSetupComplete(this)) {
                val intent = android.content.Intent(this, com.winlator.cmod.feature.stores.steam.service.SteamService::class.java)
                startForegroundService(intent)
                if (GOGAuthManager.isLoggedIn(this)) {
                    val gogIntent = android.content.Intent(this, GOGService::class.java)
                    startForegroundService(gogIntent)
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
