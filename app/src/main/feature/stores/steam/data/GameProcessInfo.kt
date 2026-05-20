package com.winlator.cmod.feature.stores.steam.data

data class GameProcessInfo(
    val appId: Int,
    val branch: String = "public",
    val processes: List<AppProcessInfo>,
)
