package com.winlator.cmod.runtime.compat.gamefixes.helpers
import com.winlator.cmod.feature.stores.epic.service.EpicConstants
import com.winlator.cmod.feature.stores.epic.service.EpicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File

object EpicGameFixHelper {
    private const val TAG = "EpicGameFixHelper"

    fun getCatalogIdForAppId(appIdStr: String?): String? {
        val appId = appIdStr?.toIntOrNull() ?: return null
        val game = EpicService.getEpicGameOf(appId) ?: return null
        return game.catalogId.takeIf { it.isNotEmpty() }
    }

    fun getInstallPathForCatalog(catalogId: String?): String? {
        if (catalogId.isNullOrEmpty()) return null
        val service = EpicService.getInstance() ?: return null

        val game =
            runBlocking(Dispatchers.IO) {
                service.epicManager.getGameByCatalogId(catalogId)
            } ?: return null

        val installPath =
            game.installPath.ifEmpty {
                try {
                    EpicConstants.getGameInstallPath(service.applicationContext, game.appName)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to build default install path for $catalogId")
                    null
                }
            }

        return installPath?.takeIf { File(it).isDirectory }
    }
}
