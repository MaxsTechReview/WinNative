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
    val uploadRoot: PathType = root,
    val uploadPath: String = path,
) {
    val prefix: String
        get() {
            val pathForPrefix =
                when {
                    path.isBlank() || path == "." -> ""
                    else -> path
                }
            return "%${root.name}%$pathForPrefix"
                .replace("{64BitSteamID}", SteamUtils.getSteamId64().toString())
                .replace("{Steam3AccountID}", SteamUtils.getSteam3AccountId().toString())
        }

    val substitutedPath: String
        get() =
            when {
                path.isBlank() || path == "." -> ""
                else -> path
            }.replace("{64BitSteamID}", SteamUtils.getSteamId64().toString())
                .replace("{Steam3AccountID}", SteamUtils.getSteam3AccountId().toString())
                .replace("\\", File.separator)
}
