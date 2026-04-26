package com.winlator.cmod.feature.settings
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.feature.setup.SetupWizardActivity
import com.winlator.cmod.runtime.content.AdrenotoolsManager
import com.winlator.cmod.runtime.content.Downloader
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DriversFragment : Fragment() {
    private lateinit var adrenotoolsManager: AdrenotoolsManager
    private lateinit var preferences: SharedPreferences

    private var driversState by mutableStateOf(DriversState())

    private val releasesBySource = linkedMapOf<String, List<DriverReleaseItem>>()
    private var sources = mutableListOf<DriverRepo>()
    private var installedDrivers: List<InstalledDriverItem> = emptyList()
    private var installedAssetNames: Set<String> = emptySet()
    private var expandedSourceApiUrl: String? = null
    private var expandedReleaseId: Long? = null
    private var loadingSourceApiUrl: String? = null
    private var downloadProgress: DownloadProgress? = null

    private val driverPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { installDriverPackage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adrenotoolsManager = AdrenotoolsManager(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.settings_drivers_manager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        loadRepos()
        refreshInstalledDrivers()
        publishState()

        return ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WinNativeTheme(
                    colorScheme =
                        darkColorScheme(
                            primary = Color(0xFF1A9FFF),
                            background = Color(0xFF141B24),
                            surface = Color(0xFF1E252E),
                        ),
                ) {
                    DriversScreen(
                        state = driversState,
                        onInstallFromFile = {
                            driverPicker.launch(arrayOf("*/*"))
                        },
                        onSourceTapped = { source -> onSourceSelected(source) },
                        onReleaseTapped = { release ->
                            expandedReleaseId = if (expandedReleaseId == release.id) null else release.id
                            publishState()
                        },
                        onDownloadAsset = { asset -> downloadReleaseAsset(asset) },
                        onRemoveDriver = { driver ->
                            adrenotoolsManager.removeDriver(driver.id)
                            refreshInstalledDrivers()
                            publishState()
                        },
                        onRepoAdded = { name, apiUrl ->
                            val normalized = normalizeRepoInput(name, apiUrl)
                            sources.add(normalized)
                            saveRepos()
                            publishState()
                        },
                        onRepoUpdated = { index, name, apiUrl ->
                            if (index in sources.indices) {
                                val normalized = normalizeRepoInput(name, apiUrl)
                                sources[index] = normalized
                                releasesBySource.remove(sources[index].apiUrl)
                                saveRepos()
                                publishState()
                            }
                        },
                        onRepoDeleted = { index ->
                            if (index in sources.indices) {
                                val removed = sources.removeAt(index)
                                releasesBySource.remove(removed.apiUrl)
                                if (expandedSourceApiUrl == removed.apiUrl) expandedSourceApiUrl = null
                                if (loadingSourceApiUrl == removed.apiUrl) loadingSourceApiUrl = null
                                saveRepos()
                                publishState()
                            }
                        },
                        onRestoreDefaultRepos = { restoreDefaultRepos() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledDrivers()
        publishState()
    }

    private fun publishState() {
        val existingApiUrls = sources.map { it.apiUrl }.toHashSet()
        val hasMissingDefaults = defaultRepoList().any { it.apiUrl !in existingApiUrls }
        driversState =
            DriversState(
                installedDrivers = installedDrivers,
                sources = sources.toList(),
                releasesBySource = releasesBySource.toMap(),
                expandedSourceApiUrl = expandedSourceApiUrl,
                expandedReleaseId = expandedReleaseId,
                loadingSourceApiUrl = loadingSourceApiUrl,
                hasMissingDefaults = hasMissingDefaults,
                installedAssetNames = installedAssetNames,
                downloadProgress = downloadProgress,
            )
    }

    private fun refreshInstalledDrivers() {
        val driverIds = adrenotoolsManager.enumarateInstalledDrivers()
        installedDrivers =
            driverIds
                .map { driverId ->
                    InstalledDriverItem(
                        id = driverId,
                        name = adrenotoolsManager.getDriverName(driverId).ifBlank { driverId },
                        version = adrenotoolsManager.getDriverVersion(driverId),
                    )
                }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        installedAssetNames =
            driverIds
                .mapNotNull { id -> adrenotoolsManager.getSourceAsset(id).takeIf { it.isNotBlank() } }
                .toSet()
    }

    private fun defaultRepoList(): List<DriverRepo> =
        listOf(
            DriverRepo(name = GITHUB_REPO_NAME, repoUrl = GITHUB_REPO_URL, apiUrl = GITHUB_API_URL),
            DriverRepo(
                name = WHITEBELYASH_REPO_NAME,
                repoUrl = WHITEBELYASH_REPO_URL,
                apiUrl = WHITEBELYASH_API_URL,
            ),
        )

    private fun loadRepos() {
        val jsonStr = preferences.getString("custom_driver_repos", null)
        val newSources = mutableListOf<DriverRepo>()

        if (jsonStr == null) {
            newSources.addAll(defaultRepoList())
        } else {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    newSources.add(
                        DriverRepo(
                            name = obj.optString("name", "Unknown Repo"),
                            repoUrl = obj.optString("repoUrl", ""),
                            apiUrl = obj.optString("apiUrl", ""),
                        ),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sources = newSources
    }

    private fun restoreDefaultRepos() {
        val existingApiUrls = sources.map { it.apiUrl }.toHashSet()
        var added = 0
        defaultRepoList().forEach { default ->
            if (default.apiUrl !in existingApiUrls) {
                sources.add(default)
                added += 1
            }
        }
        if (added > 0) {
            saveRepos()
            publishState()
        } else {
            AppUtils.showToast(requireContext(), "Default repositories already present")
        }
    }

    private fun saveRepos() {
        try {
            val array = JSONArray()
            sources.forEach { source ->
                val obj = JSONObject()
                obj.put("name", source.name)
                obj.put("repoUrl", source.repoUrl)
                obj.put("apiUrl", source.apiUrl)
                array.put(obj)
            }
            preferences
                .edit()
                .putString("custom_driver_repos", array.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeRepoInput(
        name: String,
        rawUrl: String,
    ): DriverRepo {
        var url = rawUrl
        if (url.startsWith("https://github.com/") && !url.contains("api.github.com")) {
            url = url.replace("https://github.com/", "https://api.github.com/repos/")
            if (!url.endsWith("/releases")) {
                url = "$url/releases"
            }
        }
        val repoUrl = url.replace("api.github.com/repos", "github.com")
        return DriverRepo(name = name, repoUrl = repoUrl, apiUrl = url)
    }

    private fun onSourceSelected(source: DriverRepo) {
        if (loadingSourceApiUrl == source.apiUrl) return

        if (expandedSourceApiUrl == source.apiUrl) {
            expandedSourceApiUrl = null
            expandedReleaseId = null
            publishState()
            return
        }

        expandedSourceApiUrl = source.apiUrl
        expandedReleaseId = null

        if (releasesBySource.containsKey(source.apiUrl)) {
            publishState()
            return
        }

        loadingSourceApiUrl = source.apiUrl
        publishState()

        viewLifecycleOwner.lifecycleScope.launch {
            val releases =
                withContext(Dispatchers.IO) {
                    runCatching { fetchGithubReleases(source) }.getOrElse { emptyList() }
                }

            if (!isAdded || view == null) return@launch

            if (loadingSourceApiUrl == source.apiUrl) {
                loadingSourceApiUrl = null
            }
            releasesBySource[source.apiUrl] = releases
            publishState()

            if (releases.isEmpty()) {
                AppUtils.showToast(requireContext(), R.string.settings_drivers_repo_fetch_failed)
            }
        }
    }

    private fun fetchGithubReleases(source: DriverRepo): List<DriverReleaseItem> {
        val connection =
            (URL(source.apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "WinNative")
            }

        return connection.useResponse { responseText ->
            val json = JSONArray(responseText)
            buildList {
                for (index in 0 until json.length()) {
                    val releaseObject = json.optJSONObject(index) ?: continue
                    val assets = releaseObject.optJSONArray("assets").toZipAssets()
                    if (assets.isEmpty()) continue

                    val tagName = releaseObject.optString("tag_name")
                    val releaseName = releaseObject.optString("name").ifBlank { tagName }
                    val publishedAt = releaseObject.optString("published_at")
                    val releaseNotes = releaseObject.optString("body").toReleaseNotes()

                    add(
                        DriverReleaseItem(
                            id = releaseObject.optLong("id"),
                            title = releaseName.ifBlank { getString(R.string.common_ui_unnamed) },
                            subtitle = buildReleaseSubtitle(tagName, publishedAt, assets.size),
                            notes = releaseNotes,
                            assets = assets,
                        ),
                    )
                }
            }
        }
    }

    private fun JSONArray?.toZipAssets(): List<DriverAssetItem> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val assetObject = optJSONObject(index) ?: continue
                val assetName = assetObject.optString("name")
                val downloadUrl = assetObject.optString("browser_download_url")
                if (!assetName.lowercase(Locale.getDefault()).endsWith(".zip") || downloadUrl.isBlank()) continue

                add(
                    DriverAssetItem(
                        id = assetObject.optLong("id"),
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeLabel = formatBytes(assetObject.optLong("size")),
                    ),
                )
            }
        }
    }

    private fun buildReleaseSubtitle(
        tagName: String,
        publishedAt: String,
        assetCount: Int,
    ): String {
        val parts = mutableListOf<String>()
        if (tagName.isNotBlank()) parts += tagName
        if (publishedAt.isNotBlank()) {
            runCatching {
                val formattedDate =
                    OffsetDateTime.parse(publishedAt).format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
                    )
                parts += formattedDate
            }
        }
        parts += resources.getQuantityString(R.plurals.github_repo_asset_count, assetCount, assetCount)
        return parts.joinToString("  •  ")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return getString(R.string.settings_drivers_repo_unknown_size)
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.toInt()} ${units[unitIndex]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }

    private fun String.toReleaseNotes(): String =
        lineSequence()
            .map { line -> line.trim() }
            .dropWhile { it.isBlank() }
            .map { line ->
                line
                    .removePrefix("#")
                    .removePrefix("##")
                    .removePrefix("###")
                    .removePrefix("- [ ] ")
                    .removePrefix("- [x] ")
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .trim()
            }.fold(mutableListOf<String>()) { acc, line ->
                if (line.isBlank()) {
                    if (acc.isNotEmpty() && acc.last().isNotBlank()) acc += ""
                } else {
                    acc += line
                }
                acc
            }.joinToString("\n")
            .trim()

    private fun downloadReleaseAsset(asset: DriverAssetItem) {
        if (asset.name in installedAssetNames) return
        if (downloadProgress != null) return

        val downloadTitle = getString(R.string.settings_content_downloading_title)
        downloadProgress =
            DownloadProgress(
                title = downloadTitle,
                assetName = asset.name,
                progress = 0f,
                indeterminate = true,
            )
        publishState()

        viewLifecycleOwner.lifecycleScope.launch {
            val output = File(requireContext().cacheDir, "driver_${System.currentTimeMillis()}.zip")
            val success =
                withContext(Dispatchers.IO) {
                    Downloader.downloadFile(asset.downloadUrl, output) { downloadedBytes, totalBytes ->
                        if (totalBytes <= 0L) {
                            updateDownloadProgress(
                                DownloadProgress(
                                    title = downloadTitle,
                                    assetName = asset.name,
                                    indeterminate = true,
                                ),
                            )
                            return@downloadFile
                        }
                        val fraction =
                            (downloadedBytes.toFloat() / totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                        updateDownloadProgress(
                            DownloadProgress(
                                title = downloadTitle,
                                assetName = asset.name,
                                progress = fraction,
                                indeterminate = false,
                            ),
                        )
                    }
                }

            if (!isAdded || view == null) {
                output.delete()
                clearDownloadProgress()
                return@launch
            }

            if (!success) {
                output.delete()
                clearDownloadProgress()
                AppUtils.showToast(requireContext(), R.string.settings_drivers_repo_download_failed)
                return@launch
            }

            installDriverPackage(
                uri = Uri.fromFile(output),
                sourceAssetName = asset.name,
                onComplete = { output.delete() },
            )
        }
    }

    private fun installDriverPackage(
        uri: Uri,
        sourceAssetName: String? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        val installTitle = getString(R.string.settings_drivers_install)
        val installMessage = getString(R.string.settings_content_preparing_package)
        updateDownloadProgress(
            DownloadProgress(
                title = installTitle,
                assetName = sourceAssetName ?: installMessage,
                indeterminate = true,
            ),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val installedDriverId =
                withContext(Dispatchers.IO) {
                    runCatching {
                        adrenotoolsManager.installDriver(uri, sourceAssetName)
                    }.getOrDefault("")
                }

            if (!isAdded || view == null) {
                clearDownloadProgress()
                onComplete?.invoke()
                return@launch
            }

            clearDownloadProgress()
            onComplete?.invoke()

            if (installedDriverId.isBlank()) {
                AppUtils.showToast(requireContext(), R.string.settings_drivers_install_failed)
                return@launch
            }

            SetupWizardActivity.recordInstalledDriver(requireContext(), installedDriverId)
            refreshInstalledDrivers()
            publishState()
        }
    }

    private fun updateDownloadProgress(progress: DownloadProgress) {
        downloadProgress = progress
        publishState()
    }

    private fun clearDownloadProgress() {
        downloadProgress = null
        publishState()
    }

    companion object {
        private const val GITHUB_REPO_NAME = "StevenMXZ/freedreno_turnip-CI"
        private const val GITHUB_REPO_URL = "https://github.com/StevenMXZ/freedreno_turnip-CI/releases"
        private const val GITHUB_API_URL = "https://api.github.com/repos/StevenMXZ/freedreno_turnip-CI/releases"

        private const val WHITEBELYASH_REPO_NAME = "whitebelyash/freedreno_turnip-CI"
        private const val WHITEBELYASH_REPO_URL = "https://github.com/whitebelyash/freedreno_turnip-CI/releases"
        private const val WHITEBELYASH_API_URL = "https://api.github.com/repos/whitebelyash/freedreno_turnip-CI/releases"
    }
}

private inline fun <T> HttpURLConnection.useResponse(block: (String) -> T): T =
    try {
        val inputStream = if (responseCode in 200..299) inputStream else errorStream
        val body = inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            throw IllegalStateException(body.ifBlank { "GitHub request failed with HTTP $responseCode" })
        }
        block(body)
    } finally {
        disconnect()
    }
