package com.winlator.cmod.feature.stores.steam.wnsteam

import android.content.Context
import android.util.Log

/**
 * JNI facade over libwnsteambootstrap.so — our open-source equivalent of
 * the closed-source libsteambootstrap.so that other Wine-on-Android
 * launchers use. Loads Valve's native libsteamclient.so in this process
 * so Wine's lsteamclient.dll (inside our Proton prefix) has a peer to
 * talk to over the Steam3Master / SteamClientService TCP sockets that
 * libsteamclient.so binds when it sees those env vars.
 *
 * Lifecycle:
 *   1. Before exec'ing wine for a Steam game:
 *        WnSteamBootstrap.start(ctx, libPath, home, steam3Master,
 *                                steamClientService, extraEnv,
 *                                accountName, refreshToken, steamId64)
 *      → -1 = libsteamclient.so not on disk yet (online play / DLC
 *             checks will fall through; basic launch still works via
 *             our local PICS / ticket cache)
 *      → -2/-3/-4 = libsteamclient.so loaded but failed to set up
 *                   (see logcat for the dlerror or interface name)
 *      →  0 = ready; libsteamclient.so is hosting the TCP services
 *
 *   2. (optional, immediately after start): prepareApp(parent, dlcs)
 *      so libsteamclient.so warms its own PICS cache before Wine queries.
 *
 *   3. After the wine subprocess exits: stop()
 *
 * Threading: nativeInit / nativeShutdown serialize internally on a
 * mutex; safe to call from any thread. The other methods are no-ops
 * when not initialized.
 *
 * libsteamclient.so source:
 *   We do NOT bundle Valve's binary. The launcher provisions it at
 *   first run (Phase 8b.3) from a configurable URL into
 *   <imageFs.libDir>/libsteamclient.so. If absent, start() returns -1
 *   and the rest of this object short-circuits.
 */
object WnSteamBootstrap {

    private const val TAG = "WnSteamBootstrap"

    @Volatile private var initialized = false

    init {
        try {
            System.loadLibrary("wnsteambootstrap")
        } catch (t: UnsatisfiedLinkError) {
            // CMake build might not be enabled in all variants; surface a
            // warning rather than crashing import order.
            Log.w(TAG, "libwnsteambootstrap.so not found in jniLibs: ${t.message}")
        }
    }

    /**
     * Initialize libsteamclient.so and connect a Steam pipe + global user.
     * @param extraEnv array of "KEY=value" strings to setenv() BEFORE
     *                  dlopen — anything libsteamclient.so reads at
     *                  module init time has to be here.
     * @return 0 on success, negative error code otherwise.
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

    /**
     * Pre-warm libsteamclient.so's PICS cache for the game + DLC about to
     * launch. No-op when not initialized.
     */
    fun prepareApp(parentAppId: Int, dlcAppIds: IntArray) {
        if (!initialized) return
        val all = IntArray(1 + dlcAppIds.size).also {
            it[0] = parentAppId
            System.arraycopy(dlcAppIds, 0, it, 1, dlcAppIds.size)
        }
        try { nativePrepareApp(all) } catch (_: UnsatisfiedLinkError) {}
    }

    /** Toggle per-app cloud sync. No-op when not initialized. */
    fun setCloudEnabled(appId: Int, enabled: Boolean) {
        if (!initialized) return
        try { nativeSetCloudEnabled(appId, enabled) } catch (_: UnsatisfiedLinkError) {}
    }

    /**
     * Whether libsteamclient.so reports a logged-on user. False before
     * [start] succeeds, false when libsteamclient.so connects anonymously
     * (no cached credentials in the Wine prefix), true once authentication
     * has landed. Cheap synchronous read; safe from any thread.
     */
    fun isLoggedOn(): Boolean {
        if (!initialized) return false
        return try { nativeIsLoggedOn() } catch (_: UnsatisfiedLinkError) { false }
    }

    /**
     * SteamID64 libsteamclient.so reports for the current user, or 0 when
     * not logged on. Useful for verifying that bootstrap auth landed for
     * the same account our wn-steam-client session is using.
     */
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
