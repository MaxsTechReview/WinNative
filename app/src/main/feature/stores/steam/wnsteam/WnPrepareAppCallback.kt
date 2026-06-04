package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Fired by [WnSteamSession.prepareApp] when the pre-warm finishes. Called on
 * a native worker thread — marshal to your own dispatcher before touching UI.
 *
 * @param ok    true if all requested apps are cached and ready for Wine.
 * @param error empty when ok=true; otherwise a short diagnostic string.
 */
fun interface WnPrepareAppCallback {
    fun onPrepareResult(ok: Boolean, error: String)
}
