package com.winlator.cmod.feature.stores.steam.enums
import android.content.Context
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.ContainerUtils
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.display.environment.ImageFs
import timber.log.Timber
import java.io.File
import java.nio.file.Paths

enum class PathType {
    GameInstall,
    SteamUserData,
    WinMyDocuments,
    WinAppDataLocal,
    WinAppDataLocalLow,
    WinAppDataRoaming,
    WinSavedGames,
    WinProgramData,
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

                WinProgramData -> {
                    Paths
                        .get(
                            rootDir,
                            winePrefix,
                            "drive_c/ProgramData/",
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
                WinProgramData,
                Root,
                -> true

                else -> false
            }

    val isSupportedSteamCloudRoot: Boolean
        get() = isWindows || this == Root

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
            targetContainerId: Int? = null,
        ): String {
            val imageFs = ImageFs.find(context)
            val explicitContainer =
                targetContainerId
                    ?.takeIf { it > 0 }
                    ?.let { runCatching { ContainerManager(context).getContainerById(it) }.getOrNull() }
            val rootDir =
                when {
                    explicitContainer != null -> explicitContainer.rootDir.absolutePath
                    appId != null ->
                        ContainerUtils.getUsableContainerOrNull(context, appId)?.rootDir?.absolutePath
                            ?: imageFs.rootDir.absolutePath
                    else -> imageFs.rootDir.absolutePath
                }
            val winePrefix = if (explicitContainer != null || appId != null) ".wine" else ImageFs.WINEPREFIX

            // Walk the on-disk prefix case-insensitively — older Wine prefixes use
            // lowercase `appdata`, and scripts can create non-`xuser` profile dirs.
            val usersDir = resolveChildIgnoreCase(File(Paths.get(rootDir, winePrefix, "drive_c").toString()), "users")
            val userDir = resolveUserProfileDir(usersDir)
            val userProfile = userDir.absolutePath
            val appDataDir = resolveChildIgnoreCase(userDir, "AppData").absolutePath
            val savedGames = resolveChildIgnoreCase(userDir, "Saved Games").absolutePath
            val documents = resolveChildIgnoreCase(userDir, "Documents").absolutePath
            val localAppData = resolveChildIgnoreCase(File(appDataDir), "Local").absolutePath
            val roamingAppData = resolveChildIgnoreCase(File(appDataDir), "Roaming").absolutePath
            val publicDir = resolveChildIgnoreCase(usersDir, "Public").absolutePath

            // Longer keys first so `%USERPROFILE%\Saved Games` isn't chopped by `%USERPROFILE%`.
            var mappedPath = gogWindowsPath
            val replacements =
                listOf(
                    "%USERPROFILE%/Saved Games" to "$savedGames/",
                    "%USERPROFILE%\\Saved Games" to "$savedGames/",
                    "%USERPROFILE%/Documents" to "$documents/",
                    "%USERPROFILE%\\Documents" to "$documents/",
                    "%PUBLIC%" to "$publicDir/",
                    "%LOCALAPPDATA%" to "$localAppData/",
                    "%APPDATA%" to "$roamingAppData/",
                    "%USERPROFILE%" to "$userProfile/",
                )
            for ((token, value) in replacements) {
                mappedPath = mappedPath.replace(token, value, ignoreCase = true)
            }

            return mappedPath.replace("\\", "/")
        }

        private fun resolveChildIgnoreCase(
            parent: File,
            name: String,
        ): File =
            parent
                .listFiles()
                ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: File(parent, name)

        private fun resolveUserProfileDir(usersDir: File): File {
            val direct = File(usersDir, ImageFs.USER)
            if (direct.exists()) return direct
            val caseInsensitive =
                usersDir
                    .listFiles { f -> f.isDirectory }
                    ?.firstOrNull { it.name.equals(ImageFs.USER, ignoreCase = true) }
            if (caseInsensitive != null) return caseInsensitive
            val firstNonSystem =
                usersDir
                    .listFiles { f -> f.isDirectory }
                    ?.firstOrNull { it.name.lowercase() !in setOf("public", "all users", "default", "default user") }
            return firstNonSystem ?: direct
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
                "steamuserbasestorage",
                "%steamuserbasestorage%",
                -> {
                    SteamUserData
                }

                "%${WinMyDocuments.name.lowercase()}%",
                WinMyDocuments.name.lowercase(),
                "steamclouddocuments",
                "%steamclouddocuments%",
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

                "%${WinProgramData.name.lowercase()}%",
                WinProgramData.name.lowercase(),
                -> {
                    WinProgramData
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

                "%root_mod%",
                "root_mod",
                "windowshome",
                "%windowshome%",
                "%${Root.name.lowercase()}%",
                Root.name.lowercase(),
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
