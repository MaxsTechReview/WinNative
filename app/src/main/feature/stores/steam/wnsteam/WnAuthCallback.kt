package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Fired by [WnSteamSession] when an auth session finishes (success or
 * failure). Invoked on a native worker thread — marshal to your own
 * dispatcher before touching UI.
 */
fun interface WnAuthCallback {
    fun onAuthResult(result: WnAuthResult)
}
