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
import com.winlator.cmod.feature.stores.steam.service.SteamService
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
import java.io.File
import java.security.Security

@HiltAndroidApp
class PluviaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Some devices (notably several Xiaomi/HyperOS builds) don't expose
        // libjpeg.so on the default Bionic dlopen search path, which causes
        // any downstream native lib that calls dlopen("libjpeg.so") to fail
        // with "library not found". Eagerly pin the system copy into the
        // global namespace at startup so subsequent dlopens resolve it.
        preloadSystemLibraries()

        registerRefreshRateLifecycleCallbacks()

        // Replace Android's limited BouncyCastle provider with the full one
        // so that JavaSteam can use SHA-1 (and other algorithms) via the "BC" provider.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        // Register application context so secure Steam prefs can initialize lazily.
        PrefManager.install(this)
        GOGConstants.init(this)

        // Eagerly initialize the Play Games SDK so store-login sync can rehydrate its
        // silent auth state before the Google settings screen asks for remote state.
        // The call itself is synchronous-fast; the real auth work happens off-thread.
        com.winlator.cmod.feature.sync.google.PlayGamesBootstrap.ensureInitialized(this)

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

        // Count of started (visible) activities — the app is in the
        // foreground while this is > 0. Mutated only on the main thread.
        @Volatile
        private var startedActivityCount = 0

        // Count of live XServerDisplayActivity instances (created and not yet
        // destroyed) — i.e. a game session exists, possibly backgrounded.
        @Volatile
        private var gameActivityCount = 0

        /** True while a game window exists — keeps the Steam session awake. */
        fun isGameSessionActive(): Boolean = gameActivityCount > 0
    }

    private fun preloadSystemLibraries() {
        val is64 = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val candidates = if (is64) {
            listOf("/system/lib64/libjpeg.so", "/system/lib/libjpeg.so")
        } else {
            listOf("/system/lib/libjpeg.so", "/system/lib64/libjpeg.so")
        }
        for (path in candidates) {
            if (!File(path).exists()) continue
            try {
                System.load(path)
                Log.i("PluviaApp", "Preloaded $path")
                return
            } catch (t: Throwable) {
                Log.w("PluviaApp", "Preload $path failed: ${t.message}")
            }
        }
    }

    private fun registerRefreshRateLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    if (activity is XServerDisplayActivity) {
                        gameActivityCount++
                    }
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

                override fun onActivityStarted(activity: Activity) {
                    // 0 -> 1 means the app just entered the foreground.
                    if (startedActivityCount++ == 0) {
                        SteamService.onAppForegrounded()
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    if (currentForegroundActivity === activity) {
                        currentForegroundActivity = null
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    // 1 -> 0 means the app just went to the background.
                    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                    if (startedActivityCount == 0) {
                        SteamService.onAppBackgrounded()
                    }
                }

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
                    if (activity is XServerDisplayActivity) {
                        gameActivityCount = (gameActivityCount - 1).coerceAtLeast(0)
                        // A game session ending while the app is already
                        // backgrounded should let the Steam session sleep —
                        // re-evaluate the suspend decision now.
                        if (gameActivityCount == 0 && startedActivityCount == 0) {
                            SteamService.onAppBackgrounded()
                        }
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
