package com.winlator.cmod.feature.stores.steam.events

sealed interface AndroidEvent<T> : Event<T> {
    data object EndProcess : AndroidEvent<Unit>

    data class DownloadPausedDueToConnectivity(
        val appId: Int,
    ) : AndroidEvent<Unit>

    data class DownloadStatusChanged(
        val appId: Int,
        val isDownloading: Boolean,
    ) : AndroidEvent<Unit>

    data class LibraryInstallStatusChanged(
        val appId: Int,
    ) : AndroidEvent<Unit>

    data object LibraryArtworkChanged : AndroidEvent<Unit>
}
