// IMPORTANT: the package path below is hard-bound to the JNI symbol name
// in app/src/main/cpp/wn-steam-client/jni/wn_steam_jni.cpp
// (Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamClient_...).
// Do NOT move this file or rename the package without updating that file.
package com.winlator.cmod.feature.stores.steam.wnsteam

import timber.log.Timber

/**
 * JVM-side entry point to the native wn-steam-client library.
 *
 * Phase 0: only [version] is wired through. Real Steam protocol surface
 * (connect, login, PICS, CDN, cloud) lands in subsequent phases.
 */
object WnSteamClient {

    @Volatile
    private var loaded: Boolean = false

    /**
     * Load libwnsteam.so. Safe to call repeatedly; the first successful call
     * latches and all subsequent calls are no-ops. If [System.loadLibrary]
     * throws, [loaded] stays false and the next call will retry — appropriate
     * for transient packaging issues seen during install.
     */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("wnsteam")
            loaded = true
            Timber.tag(TAG).i("libwnsteam.so loaded, native version = %s", nativeVersion())
        }
    }

    /** Native library semver string, e.g. "0.1.0". Loads the library on first call. */
    fun version(): String {
        ensureLoaded()
        return nativeVersion()
    }

    @JvmStatic
    private external fun nativeVersion(): String

    private const val TAG = "WnSteamClient"
}
