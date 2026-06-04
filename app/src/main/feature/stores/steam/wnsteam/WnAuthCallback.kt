package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Fired by [WnSteamSession] when an auth session has an auth update.
 * QR login can emit a remote-approval hint before the final success or
 * failure result arrives. Invoked on a native worker thread — marshal
 * to your own dispatcher before touching UI.
 */
fun interface WnAuthCallback {
    fun onAuthResult(result: WnAuthResult)
}
