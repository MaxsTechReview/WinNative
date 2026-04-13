package com.winlator.cmod.feature.stores.steam.enums

data class SpecialGameSaveMapping(
    val appId: Int,
    val pathType: PathType,
    val sourceRelativePath: String,
    val targetRelativePath: String,
    val description: String,
) {
    companion object {
        val registry =
            listOf(
                SpecialGameSaveMapping(
                    appId = 2680010,
                    pathType = PathType.WinAppDataLocal,
                    sourceRelativePath = "The First Berserker Khazan/Saved/SaveGames/{64BitSteamID}",
                    targetRelativePath = "BBQ/Saved/SaveGames",
                    description = "The First Berserker Khazan",
                ),
            )
    }
}
