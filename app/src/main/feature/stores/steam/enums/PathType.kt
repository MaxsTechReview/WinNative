package com.winlator.cmod.feature.stores.steam.enums
import android.content.Context
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
import com.winlator.cmod.runtime.display.environment.ImageFs
import timber.log.Timber
import java.nio.file.Paths

enum class PathType {
    GameInstall,
    SteamUserData,
    WinMyDocuments,
    WinAppDataLocal,
    WinAppDataLocalLow,
    WinAppDataRoaming,
    WinSavedGames,
    LinuxHome,
    LinuxXdgDataHome,
    LinuxXdgConfigHome,
    MacHome,
    MacAppSupport,
    None,
    Root,
    ;

    fun toAbsPath(
        context: Context,
        appId: Int,
        accountId: Long,
    ): String {
        val imageFs = ImageFs.find(context)
        val rootDir = imageFs.rootDir.absolutePath
        val winePrefix = ImageFs.WINEPREFIX
        val user = ImageFs.USER

        val path =
            when (this) {
                GameInstall -> {
                    SteamService.getAppDirPath(appId)
                }

                SteamUserData -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/Program Files (x86)/Steam/userdata/$accountId/$appId/remote",
                        ).toString()
                }

                WinMyDocuments -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/users/",
                            user,
                            "Documents/",
                        ).toString()
                }

                WinAppDataLocal -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/users/",
                            user,
                            "AppData/Local/",
                        ).toString()
                }

                WinAppDataLocalLow -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/users/",
                            user,
                            "AppData/LocalLow/",
                        ).toString()
                }

                WinAppDataRoaming -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/users/",
                            user,
                            "AppData/Roaming/",
                        ).toString()
                }

                WinSavedGames -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/users/",
                            user,
                            "Saved Games/",
                        ).toString()
                }

                Root -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/users/",
                            user,
                            "",
                        ).toString()
                }

                else -> {
                    Timber.e("Did not recognize or unsupported path type $this")
                    SteamService.getAppDirPath(appId)
                }
            }
        return if (!path.endsWith("/")) "$path/" else path
    }

    val isWindows: Boolean
        get() =
            when (this) {
                GameInstall,
                SteamUserData,
                WinMyDocuments,
                WinAppDataLocal,
                WinAppDataLocalLow,
                WinAppDataRoaming,
                WinSavedGames,
                -> true

                else -> false
            }

    companion object {
        val DEFAULT = SteamUserData

        fun resolveGOGPathVariables(
            location: String,
            installPath: String,
        ): String {
            var resolved = location
            val variableMap =
                mapOf(
                    "INSTALL" to installPath,
                    "SAVED_GAMES" to "%USERPROFILE%/Saved Games",
                    "APPLICATION_DATA_LOCAL" to "%LOCALAPPDATA%",
                    "APPLICATION_DATA_LOCAL_LOW" to "%APPDATA%\\..\\LocalLow",
                    "APPLICATION_DATA_ROAMING" to "%APPDATA%",
                    "DOCUMENTS" to "%USERPROFILE%\\Documents",
                )
            val pattern = Regex("<\\?(\\w+)\\?>")
            pattern.findAll(resolved).forEach { match ->
                val replacement = variableMap[match.groupValues[1]]
                if (replacement != null) {
                    resolved = resolved.replace(match.value, replacement)
                }
            }
            return resolved
        }

        fun toAbsPathForGOG(
            context: Context,
            gogWindowsPath: String,
            appId: String? = null,
        ): String {
            val imageFs = ImageFs.find(context)
            val user = ImageFs.USER
            val rootDir =
                if (appId != null) {
                    ContainerUtils.getUsableContainerOrNull(context, appId)?.rootDir?.absolutePath
                        ?: imageFs.rootDir.absolutePath
                } else {
                    imageFs.rootDir.absolutePath
                }
            val winePrefix = if (appId != null) ".wine" else ImageFs.WINEPREFIX

            var mappedPath = gogWindowsPath
            mappedPath =
                mappedPath
                    .replace(
                        "%USERPROFILE%/Saved Games",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "Saved Games/").toString(),
                    ).replace(
                        "%USERPROFILE%\\Saved Games",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "Saved Games/").toString(),
                    ).replace(
                        "%USERPROFILE%/Documents",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "Documents/").toString(),
                    ).replace(
                        "%USERPROFILE%\\Documents",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "Documents/").toString(),
                    )
            mappedPath =
                mappedPath
                    .replace(
                        "%LOCALAPPDATA%",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "AppData/Local/").toString(),
                    ).replace(
                        "%APPDATA%",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "AppData/Roaming/").toString(),
                    ).replace(
                        "%USERPROFILE%",
                        Paths.get(rootDir, winePrefix, "drive_c/users/", user, "").toString(),
                    )

            return mappedPath.replace("\\", "/")
        }

        fun from(keyValue: String?): PathType =
            when (keyValue?.lowercase()) {
                "%${GameInstall.name.lowercase()}%",
                GameInstall.name.lowercase(),
                -> {
                    GameInstall
                }

                "%${SteamUserData.name.lowercase()}%",
                SteamUserData.name.lowercase(),
                -> {
                    SteamUserData
                }

                "%${WinMyDocuments.name.lowercase()}%",
                WinMyDocuments.name.lowercase(),
                -> {
                    WinMyDocuments
                }

                "%${WinAppDataLocal.name.lowercase()}%",
                WinAppDataLocal.name.lowercase(),
                -> {
                    WinAppDataLocal
                }

                "%${WinAppDataLocalLow.name.lowercase()}%",
                WinAppDataLocalLow.name.lowercase(),
                -> {
                    WinAppDataLocalLow
                }

                "%${WinAppDataRoaming.name.lowercase()}%",
                WinAppDataRoaming.name.lowercase(),
                -> {
                    WinAppDataRoaming
                }

                "%${WinSavedGames.name.lowercase()}%",
                WinSavedGames.name.lowercase(),
                -> {
                    WinSavedGames
                }

                "%${LinuxHome.name.lowercase()}%",
                LinuxHome.name.lowercase(),
                -> {
                    LinuxHome
                }

                "%${LinuxXdgDataHome.name.lowercase()}%",
                LinuxXdgDataHome.name.lowercase(),
                -> {
                    LinuxXdgDataHome
                }

                "%${LinuxXdgConfigHome.name.lowercase()}%",
                LinuxXdgConfigHome.name.lowercase(),
                -> {
                    LinuxXdgConfigHome
                }

                "%${MacHome.name.lowercase()}%",
                MacHome.name.lowercase(),
                -> {
                    MacHome
                }

                "%${MacAppSupport.name.lowercase()}%",
                MacAppSupport.name.lowercase(),
                -> {
                    MacAppSupport
                }

                "%ROOT_MOD%",
                "ROOT_MOD",
                -> {
                    Root
                }

                else -> {
                    if (keyValue != null) {
                        Timber.w("Could not identify $keyValue as PathType")
                    }
                    None
                }
            }
    }
}
