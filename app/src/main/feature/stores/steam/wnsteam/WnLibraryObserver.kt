package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Fired by [WnSteamSession]'s native library store whenever its snapshot
 * changes (new license list ingested, PICS packages parsed, PICS apps
 * parsed, access tokens granted). Invoked on a native worker thread —
 * marshal to your own dispatcher and re-read the snapshot before touching UI.
 */
fun interface WnLibraryObserver {
    fun onLibraryChanged()
}
