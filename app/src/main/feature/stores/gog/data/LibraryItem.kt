package com.winlator.cmod.feature.stores.gog.data
import com.winlator.cmod.feature.stores.steam.enums.GameSource

data class LibraryItem(
    val appId: String,
    val name: String,
    val gameSource: GameSource,
) {
    val gameId: Int
        get() =
            appId.substringAfterLast("_", appId).toIntOrNull()
                ?: appId.toIntOrNull()
                ?: appId.hashCode()
}
