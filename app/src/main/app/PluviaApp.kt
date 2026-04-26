package com.winlator.cmod.app
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.winlator.cmod.app.db.PluviaDatabase
import com.winlator.cmod.app.update.UpdateChecker
import com.winlator.cmod.feature.stores.gog.service.GOGAuthManager
import com.winlator.cmod.feature.stores.gog.service.GOGConstants
import com.winlator.cmod.feature.stores.steam.events.EventDispatcher
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.shared.android.RefreshRateUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class PluviaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

        // Initialize process-wide reactive network state
        com.winlator.cmod.app.service.NetworkMonitor
            .init(this)
        scheduleColdStartWarmups()

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

    private fun scheduleColdStartWarmups() {
        appScope.launch {
            // Release the main thread for Activity launch and first Compose work.
            withContext(Dispatchers.IO) {
                GOGAuthManager.updateLoginStatus(this@PluviaApp)

                // Pre-warm encrypted preferences off the UI thread so launcher auth checks
                // are less likely to pay MasterKey/EncryptedSharedPreferences startup cost.
                val steamLogsEnabled =
                    runCatching {
                        PrefManager.init(this@PluviaApp)
                        PrefManager.libraryLayoutMode
                        PrefManager.enableSteamLogs
                    }.getOrElse {
                        Log.e("PluviaApp", "PrefManager warmup failed", it)
                        false
                    }

                if (UpdateChecker.isEnabled(this@PluviaApp)) {
                    UpdateChecker.refreshInstallTimestamp(this@PluviaApp)
                }

                runCatching { PluviaDatabase.init(this@PluviaApp) }
                    .onFailure { Log.e("PluviaApp", "Database warmup failed", it) }

                // Initialize the cross-store DownloadCoordinator and auto-resume any
                // downloads that were running when the app was killed. PAUSED downloads
                // stay PAUSED; DOWNLOADING ones are demoted to QUEUED and dispatched as
                // store services start.
                runCatching {
                    val db = PluviaDatabase.getInstance(this@PluviaApp)
                    com.winlator.cmod.app.service.download.DownloadCoordinator.init(db)
                    com.winlator.cmod.app.service.download.DownloadCoordinator
                        .attemptStartupRestoration()
                }.onFailure { Log.e("PluviaApp", "DownloadCoordinator startup failed", it) }

                com.winlator.cmod.runtime.system.LogManager
                    .rotateLogsOnAppStart(this@PluviaApp)
                com.winlator.cmod.runtime.system.LogManager
                    .startAppLogging(this@PluviaApp)

                if (steamLogsEnabled) {
                    withContext(Dispatchers.Main.immediate) {
                        if (timber.log.Timber.forest().none { it is timber.log.Timber.DebugTree }) {
                            timber.log.Timber.plant(timber.log.Timber.DebugTree())
                        }
                    }
                }
            }
        }
    }
}
