package com.winlator.cmod.feature.stores.steam.data

import com.winlator.cmod.feature.stores.steam.enums.EPersonaState

data class SteamFriendEntry(
    val steamId: Long,
    val name: String,
    val state: EPersonaState,
    val gameAppId: Int = 0,
    val gameName: String = "",
    val avatarHash: String = "",
    val connectString: String = "",
) {
    val isJoinable: Boolean
        get() = isPlayingGame && gameAppId > 0 && connectString.isNotBlank()

    val isOnline: Boolean
        get() = state.code() in 1..6

    val isPlayingGame: Boolean
        get() = isOnline && (gameAppId > 0 || gameName.isNotBlank())

    val avatarUrl: String?
        get() = avatarHash.takeIf { it.isNotBlank() }
            ?.let { "https://avatars.akamai.steamstatic.com/${it}_full.jpg" }

    // Game artwork for the app the friend is playing (Steam apps only).
    val gameCapsuleUrl: String?
        get() = gameAppId.takeIf { it > 0 }
            ?.let { "https://cdn.cloudflare.steamstatic.com/steam/apps/$it/capsule_231x87.jpg" }

    val gameHeaderUrl: String?
        get() = gameAppId.takeIf { it > 0 }
            ?.let { "https://cdn.cloudflare.steamstatic.com/steam/apps/$it/header.jpg" }
}
