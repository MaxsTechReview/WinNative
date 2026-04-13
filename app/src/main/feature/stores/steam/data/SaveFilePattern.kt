package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.enums.PathType
import com.winlator.cmod.feature.stores.steam.utils.SteamUtils
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class SaveFilePattern(
    val root: PathType,
    val path: String,
    val pattern: String,
    val recursive: Int = 0,
) {
    val prefix: String
        get() =
            "%${root.name}%$path"
                .replace("{64BitSteamID}", SteamUtils.getSteamId64().toString())
                .replace("{Steam3AccountID}", SteamUtils.getSteam3AccountId().toString())

    val substitutedPath: String
        get() =
            path
                .replace("{64BitSteamID}", SteamUtils.getSteamId64().toString())
                .replace("{Steam3AccountID}", SteamUtils.getSteam3AccountId().toString())
                .replace("\\", File.separator)
}
