package com.winlator.cmod.feature.stores.steam.wnsteam

import android.content.Context
import android.util.Log

// JNI facade that boots libsteamclient.so for Wine's lsteamclient.dll.
object WnSteamBootstrap {

    private const val TAG = "WnSteamBootstrap"

    @Volatile private var initialized = false

    init {
        try {
            System.loadLibrary("wnsteambootstrap")
        } catch (t: UnsatisfiedLinkError) {
            // Optional in some variants.
            Log.w(TAG, "libwnsteambootstrap.so not found in jniLibs: ${t.message}")
        }
    }

    /**
     * Initialize libsteamclient.so. Returns 0 on success, or a negative error.
     * [extraEnv] is applied before dlopen for module-init reads.
     */
    @Synchronized
    fun start(
        context: Context,
        libPath: String,
        home: String,
        steam3Master: String,
        steamClientService: String,
        extraEnv: Array<String>,
        accountName: String?,
        refreshToken: String?,
        steamId64: Long,
    ): Int {
        if (initialized) {
            Log.i(TAG, "start: already initialized")
            return 0
        }
        val rc = try {
            nativeInit(context, libPath, home, steam3Master, steamClientService,
                       extraEnv, accountName, refreshToken, steamId64)
        } catch (t: UnsatisfiedLinkError) {
            Log.w(TAG, "nativeInit unavailable: ${t.message}")
            return -100
        }
        if (rc == 0) initialized = true
        Log.i(TAG, "start rc=$rc initialized=$initialized")
        return rc
    }

    @Synchronized
    fun stop() {
        if (!initialized) return
        try { nativeShutdown() } catch (_: UnsatisfiedLinkError) {}
        initialized = false
        Log.i(TAG, "stop done")
    }

    // Pre-warm libsteamclient.so's PICS cache for the game and DLC.
    fun prepareApp(parentAppId: Int, dlcAppIds: IntArray) {
        if (!initialized) return
        val all = IntArray(1 + dlcAppIds.size).also {
            it[0] = parentAppId
            System.arraycopy(dlcAppIds, 0, it, 1, dlcAppIds.size)
        }
        try { nativePrepareApp(all) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setCloudEnabled(appId: Int, enabled: Boolean) {
        if (!initialized) return
        try { nativeSetCloudEnabled(appId, enabled) } catch (_: UnsatisfiedLinkError) {}
    }

    // Whether libsteamclient.so reports a logged-on user.
    fun isLoggedOn(): Boolean {
        if (!initialized) return false
        return try { nativeIsLoggedOn() } catch (_: UnsatisfiedLinkError) { false }
    }

    // SteamID64 from libsteamclient.so, or 0 when not logged on.
    fun steamId(): Long {
        if (!initialized) return 0
        return try { nativeGetSteamId() } catch (_: UnsatisfiedLinkError) { 0L }
    }

    @JvmStatic private external fun nativeInit(
        context: Context,
        libPath: String,
        home: String,
        steam3Master: String,
        steamClientService: String,
        extraEnv: Array<String>,
        accountName: String?,
        refreshToken: String?,
        steamId64: Long,
    ): Int
    @JvmStatic private external fun nativeShutdown()
    @JvmStatic private external fun nativePrepareApp(appIds: IntArray)
    @JvmStatic private external fun nativeSetCloudEnabled(appId: Int, enabled: Boolean)
    @JvmStatic private external fun nativeIsLoggedOn(): Boolean
    @JvmStatic private external fun nativeGetSteamId(): Long
}
