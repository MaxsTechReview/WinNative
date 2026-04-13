package com.winlator.cmod.runtime.compat.gamefixes.helpers
import com.winlator.cmod.feature.stores.gog.service.GOGConstants
import com.winlator.cmod.feature.stores.gog.service.GOGService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File

object GogDependencyFixHelper {
    private const val TAG = "GogDependencyFixHelper"

    fun ensureDependencies(
        gameId: String,
        dependencyIds: List<String>,
        installPath: String,
    ) {
        if (dependencyIds.isEmpty()) return

        val installDir = File(installPath)
        if (!installDir.isDirectory) return
        if (isSatisfied(installDir, dependencyIds)) return

        val downloadManager = GOGService.getInstance()?.gogDownloadManager
        if (downloadManager == null) {
            Timber.tag(TAG).w("GOG service not available for dependency fix")
            return
        }

        val supportDir = File(installDir, "_CommonRedist")
        runBlocking(Dispatchers.IO) {
            val result =
                downloadManager.downloadDependenciesWithProgress(
                    gameId = gameId,
                    dependencies = dependencyIds,
                    gameDir = installDir,
                    supportDir = supportDir,
                )
            if (result.isFailure) {
                Timber.tag(TAG).w(result.exceptionOrNull(), "Failed to download dependencies for $gameId")
            }
        }
    }

    private fun isSatisfied(
        installDir: File,
        dependencyIds: List<String>,
    ): Boolean {
        val commonRedist = File(installDir, "_CommonRedist")
        val pathMap = GOGConstants.GOG_DEPENDENCY_INSTALLED_PATH
        return dependencyIds.all { id -> pathMap[id]?.let { File(commonRedist, it).exists() } == true }
    }
}
