package com.winlator.cmod.feature.stores.steam.data

data class SteamChatMessage(
    val fromSelf: Boolean,
    val text: String,
    val timestamp: Int,
    val ordinal: Int = 0,
)
