package com.winlator.cmod.feature.library

import android.content.Context
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.display.lsfg.LosslessScaling
import java.io.File

object LosslessAutoImport {
    const val STEAM_APP_ID = 993090

    private const val DLL_NAME = "Lossless.dll"
    private const val INSTALL_DIR_NAME = "Lossless Scaling"

    fun findDll(context: Context): File? {
        for (dir in steamCandidateDirs()) {
            val dll = File(dir, DLL_NAME)
            if (dll.isFile && dll.canRead()) return dll
        }
        return runCatching {
            LosslessScaling.findInContainers(ContainerManager(context).containers).firstOrNull()
        }.getOrNull()
    }

    fun importIfNeeded(context: Context): Int {
        if (LosslessScaling.isInstalled(context)) return LosslessScaling.STATUS_OK
        val dll = findDll(context) ?: return LosslessScaling.STATUS_NOT_INSTALLED
        return LosslessScaling.installFrom(context, dll)
    }

    private fun steamCandidateDirs(): List<File> {
        val dirs = LinkedHashSet<File>()

        runCatching { SteamService.getInstalledApp(STEAM_APP_ID)?.installPath }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { dirs += File(it) }

        runCatching { SteamService.getAppDirPath(STEAM_APP_ID) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { dirs += File(it) }

        runCatching { SteamService.allInstallPaths }
            .getOrDefault(emptyList())
            .forEach { base -> if (base.isNotBlank()) dirs += File(base, INSTALL_DIR_NAME) }

        runCatching { SteamService.defaultAppInstallPath }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { dirs += File(it, INSTALL_DIR_NAME) }

        return dirs.toList()
    }
}
