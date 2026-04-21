package com.winlator.cmod.app.service
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.feature.stores.epic.service.EpicService
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.shared.io.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object DownloadService {
    private var lastUpdateTime: Long = 0
    private var downloadDirectoryApps: MutableList<String>? = null
    @Volatile
    private var initialized = false

    private var _baseDataDirPath: String = ""
    private var _baseCacheDirPath: String = ""
    private var _baseExternalAppDirPath: String = ""
    private var _externalVolumePaths: List<String> = emptyList()
    private var _appContext: Context? = null

    var baseDataDirPath: String
        get() {
            ensureInitialized()
            return _baseDataDirPath
        }
        private set(value) {
            _baseDataDirPath = value
        }

    var baseCacheDirPath: String
        get() {
            ensureInitialized()
            return _baseCacheDirPath
        }
        private set(value) {
            _baseCacheDirPath = value
        }

    var baseExternalAppDirPath: String
        get() {
            ensureInitialized()
            return _baseExternalAppDirPath
        }
        private set(value) {
            _baseExternalAppDirPath = value
        }

    var externalVolumePaths: List<String>
        get() {
            ensureInitialized()
            return _externalVolumePaths
        }
        private set(value) {
            _externalVolumePaths = value
        }

    var appContext: Context?
        get() {
            ensureInitialized()
            return _appContext
        }
        private set(value) {
            _appContext = value
        }

    fun populateDownloadService(context: Context) {
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context? = null) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val resolvedContext =
                context?.applicationContext
                    ?: _appContext
                    ?: runCatching { PluviaApp.instance.applicationContext }.getOrNull()
                    ?: throw IllegalStateException("DownloadService used before application startup")

            appContext = resolvedContext
            baseDataDirPath = resolvedContext.dataDir.path
            baseCacheDirPath = resolvedContext.cacheDir.path
            val extFiles = resolvedContext.getExternalFilesDir(null)
            baseExternalAppDirPath = extFiles?.parentFile?.path ?: ""

            val storageManager = resolvedContext.getSystemService(StorageManager::class.java)
            externalVolumePaths =
                resolvedContext
                    .getExternalFilesDirs(null)
                    .filterNotNull()
                    .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
                    .filter { storageManager?.getStorageVolume(it)?.isPrimary != true }
                    .map { it.absolutePath }
                    .distinct()

            initialized = true
        }
    }

    fun getAllDownloads(): List<Pair<String, com.winlator.cmod.feature.stores.steam.data.DownloadInfo>> {
        val list = mutableListOf<Pair<String, com.winlator.cmod.feature.stores.steam.data.DownloadInfo>>()
        SteamService.getAllDownloads().forEach { (id, info) -> list.add("STEAM_$id" to info) }
        EpicService.getAllDownloads().forEach { (id, info) -> list.add("EPIC_$id" to info) }
        GOGService.getAllDownloads().forEach { (id, info) -> list.add("GOG_$id" to info) }
        return list
    }

    fun pauseAll() {
        SteamService.pauseAll()
        EpicService.pauseAll()
        GOGService.pauseAll()
    }

    fun pauseDownload(id: String) {
        when {
            id.startsWith("STEAM_") -> {
                val appId = id.removePrefix("STEAM_").toIntOrNull() ?: return
                SteamService.pauseDownload(appId)
            }

            id.startsWith("EPIC_") -> {
                val appId = id.removePrefix("EPIC_").toIntOrNull() ?: return
                EpicService.pauseDownload(appId)
            }

            id.startsWith("GOG_") -> {
                val gameId = id.removePrefix("GOG_")
                GOGService.pauseDownload(gameId)
            }
        }
    }

    fun resumeAll() {
        SteamService.resumeAll()
        EpicService.resumeAll()
        GOGService.resumeAll()
    }

    fun resumeDownload(id: String) {
        when {
            id.startsWith("STEAM_") -> {
                val appId = id.removePrefix("STEAM_").toIntOrNull() ?: return
                SteamService.resumeDownload(appId)
            }

            id.startsWith("EPIC_") -> {
                val appId = id.removePrefix("EPIC_").toIntOrNull() ?: return
                EpicService.resumeDownload(appId)
            }

            id.startsWith("GOG_") -> {
                val gameId = id.removePrefix("GOG_")
                GOGService.resumeDownload(gameId)
            }
        }
    }

    fun cancelAll() {
        SteamService.cancelAll()
        EpicService.cancelAll()
        GOGService.cancelAll()
    }

    fun clearCompletedDownloads() {
        SteamService.clearCompletedDownloads()
        EpicService.clearCompletedDownloads()
        GOGService.clearCompletedDownloads()
    }

    fun cancelDownload(id: String) {
        when {
            id.startsWith("STEAM_") -> {
                val appId = id.removePrefix("STEAM_").toIntOrNull() ?: return
                SteamService.cancelDownload(appId)
            }

            id.startsWith("EPIC_") -> {
                val appId = id.removePrefix("EPIC_").toIntOrNull() ?: return
                EpicService.cancelDownload(appId)
            }

            id.startsWith("GOG_") -> {
                val gameId = id.removePrefix("GOG_")
                GOGService.cancelDownload(gameId)
            }
        }
    }

    fun getSizeFromStoreDisplay(appId: Int): String {
        val depots = SteamService.getDownloadableDepots(appId)
        val installBytes = depots.values.sumOf { it.manifests["public"]?.size ?: 0L }
        return StorageUtils.formatBinarySize(installBytes)
    }

    suspend fun getSizeOnDiskDisplay(
        appId: Int,
        setResult: (String) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            if (SteamService.isAppInstalled(appId)) {
                val appSizeText =
                    StorageUtils.formatBinarySize(
                        StorageUtils.getFolderSize(SteamService.getAppDirPath(appId)),
                    )

                Timber.d("Finding $appId size on disk $appSizeText")
                setResult(appSizeText)
            }
        }
    }
}
