// JNI symbols depend on this package path and class name.
package com.winlator.cmod.feature.stores.steam.wnsteam

import timber.log.Timber

// JVM-side entry point to the native wn-steam-client library.
object WnSteamClient {

    @Volatile
    private var loaded: Boolean = false

    // Load libwnsteam.so once; failed loads can be retried.
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("wnsteam")
            loaded = true
            Timber.tag(TAG).i("libwnsteam.so loaded, native version = %s", nativeVersion())
        }
    }

    // Native library semver string.
    fun version(): String {
        ensureLoaded()
        return nativeVersion()
    }

    @JvmStatic
    private external fun nativeVersion(): String

    private const val TAG = "WnSteamClient"
}
