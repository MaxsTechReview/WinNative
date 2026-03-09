package com.winlator.cmod.service

import android.content.Context
import com.winlator.cmod.utils.StorageUtils
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.epic.service.EpicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object DownloadService {
    private var lastUpdateTime: Long = 0
    private var downloadDirectoryApps: MutableList<String>? = null
    var baseDataDirPath: String = ""
        private set
    var baseCacheDirPath: String = ""
        private set
    var baseExternalAppDirPath: String = ""
        private set
    var appContext: Context? = null
        private set

    fun populateDownloadService(context: Context) {
        appContext = context.applicationContext
        baseDataDirPath = context.dataDir.path
        baseCacheDirPath = context.cacheDir.path
        val extFiles = context.getExternalFilesDir(null)
        baseExternalAppDirPath = extFiles?.parentFile?.path ?: ""
    }

    fun getAllDownloads(): List<Pair<String, com.winlator.cmod.steam.data.DownloadInfo>> {
        val list = mutableListOf<Pair<String, com.winlator.cmod.steam.data.DownloadInfo>>()
        SteamService.getAllDownloads().forEach { (id, info) -> list.add("STEAM_$id" to info) }
        EpicService.getAllDownloads().forEach { (id, info) -> list.add("EPIC_$id" to info) }
        return list
    }

    fun getSizeFromStoreDisplay (appId: Int): String {
        val depots = SteamService.getDownloadableDepots(appId)
        val installBytes = depots.values.sumOf { it.manifests["public"]?.size ?: 0L }
        return StorageUtils.formatBinarySize(installBytes)
    }

    suspend fun getSizeOnDiskDisplay (appId: Int, setResult: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            if (SteamService.isAppInstalled(appId)) {
                val appSizeText = StorageUtils.formatBinarySize(
                    StorageUtils.getFolderSize(SteamService.getAppDirPath(appId))
                )

                Timber.d("Finding $appId size on disk $appSizeText")
                setResult(appSizeText)
            }
        }
    }
}
