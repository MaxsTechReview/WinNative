/* Components screen — Jetpack Compose host.
 * Hosts ComponentsScreen; orchestrates install / download / remove flows. */
package com.winlator.cmod.feature.settings
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.content.Downloader
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.io.StorageUtils
import com.winlator.cmod.shared.ui.dialog.ContentDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ContentsFragment : Fragment() {
    private lateinit var manager: ContentsManager

    private var componentsState by mutableStateOf(ComponentsState())
    private var currentContentType = ContentProfile.ContentType.CONTENT_TYPE_WINE

    private var profilesByKey = emptyMap<String, ContentProfile>()

    private val remoteSizeCache = mutableMapOf<String, Long>()
    private val remoteSizeFetchesInFlight = mutableSetOf<String>()
    private val installedSizeCache = mutableMapOf<String, Long>()
    private val installedSizeFetchesInFlight = mutableSetOf<String>()

    private var downloadProgress: ComponentsDownloadProgress? = null

    private var autoCreateContainer = true

    private val contentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                updateDownloadProgress(
                    title = getString(R.string.settings_content_installing_title),
                    message = getString(R.string.settings_content_preparing_package),
                    indeterminate = true,
                )
                installSelectedContent(
                    it,
                    getString(R.string.settings_content_installed_success),
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ContentsManager(requireContext())
        manager.syncContents()

        savedInstanceState
            ?.getString(STATE_CONTENT_TYPE)
            ?.let(ContentProfile.ContentType::getTypeByName)
            ?.let { currentContentType = it }

        autoCreateContainer =
            PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(PREF_AUTO_CREATE_CONTAINER, true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        publishState()

        val composeView =
            ComposeView(ctx).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    MaterialTheme(
                        colorScheme =
                            darkColorScheme(
                                primary = Color(0xFF1A9FFF),
                                background = Color(0xFF141B24),
                                surface = Color(0xFF1E252E),
                            ),
                    ) {
                        ComponentsScreen(
                            state = componentsState,
                            onTypeSelected = { type -> selectContentType(type) },
                            onInstallFromFile = { promptInstallFromFile() },
                            onDownloadItem = { item ->
                                profilesByKey[item.key]?.let { downloadRemoteContent(it) }
                            },
                            onRemoveItem = { item ->
                                profilesByKey[item.key]?.let { onRemoveRequested(it) }
                            },
                            onToggleAutoCreateContainer = { enabled ->
                                autoCreateContainer = enabled
                                PreferenceManager
                                    .getDefaultSharedPreferences(requireContext())
                                    .edit()
                                    .putBoolean(PREF_AUTO_CREATE_CONTAINER, enabled)
                                    .apply()
                                publishState()
                            },
                        )
                    }
                }
            }

        val density = resources.displayMetrics.density
        val scrollView =
            ScrollView(ctx).apply {
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                scrollBarSize = (3 * density).toInt()
                isScrollbarFadingEnabled = true
                scrollBarDefaultDelayBeforeFade = 400
                scrollBarFadeDuration = 250
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setVerticalScrollbarThumbDrawable(
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(android.graphics.Color.argb(100, 26, 159, 255))
                            cornerRadius = 4 * density
                        },
                    )
                }
                addView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

        return FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#18181D"))
            addView(
                scrollView,
                FrameLayout
                    .LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ).apply { marginEnd = (10 * density).toInt() },
            )
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.settings_content_components)
    }

    override fun onResume() {
        super.onResume()
        refreshRemoteProfiles()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CONTENT_TYPE, currentContentType.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        context?.cacheDir?.let(FileUtils::clear)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // State management
    // ------------------------------------------------------------------

    private fun selectContentType(type: ContentProfile.ContentType) {
        if (type == currentContentType) return
        currentContentType = type
        publishState()
    }

    private fun publishState() {
        val profiles = manager.getProfiles(currentContentType).orEmpty()

        val installed =
            profiles
                .filter { it.isInstalled }
                .sortedWith(
                    compareByDescending<ContentProfile> { it.isInstalled }
                        .thenBy { it.verName.lowercase() }
                        .thenByDescending { it.verCode },
                )
        val available =
            profiles
                .filterNot { it.isInstalled }
                .sortedWith(
                    compareBy<ContentProfile> { it.verName.lowercase() }
                        .thenByDescending { it.verCode },
                )

        val keyedProfiles = linkedMapOf<String, ContentProfile>()
        val installedItems =
            installed.map { profile ->
                val item = profile.toItem()
                keyedProfiles[item.key] = profile
                item
            }
        val availableItems =
            available.map { profile ->
                val item = profile.toItem()
                keyedProfiles[item.key] = profile
                item
            }

        profilesByKey = keyedProfiles
        componentsState =
            ComponentsState(
                currentType = currentContentType,
                installed = installedItems,
                available = availableItems,
                downloadProgress = downloadProgress,
                autoCreateContainer = autoCreateContainer,
            )

        scheduleRemoteSizeFetches(availableItems)
        scheduleInstalledSizeFetches(installedItems)
    }

    private fun updateDownloadProgress(
        title: String,
        message: String,
        progress: Float? = null,
        indeterminate: Boolean = false,
    ) {
        val next =
            ComponentsDownloadProgress(
                title = title,
                message = message,
                progress = progress ?: 0f,
                indeterminate = indeterminate || progress == null,
            )
        runOnMain {
            downloadProgress = next
            publishState()
        }
    }

    private fun clearDownloadProgress() {
        runOnMain {
            downloadProgress = null
            publishState()
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        val act = activity
        if (act != null && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            act?.runOnUiThread { block() }
        }
    }

    private fun ContentProfile.toItem(): ComponentItem {
        val installedSuffix = if (isInstalled) "1" else "0"
        val cachedSize =
            if (isInstalled) {
                installedSizeCache[ContentsManager.getInstallDir(requireContext(), this).absolutePath]
            } else {
                remoteUrl?.let { remoteSizeCache[it] }
            }
        return ComponentItem(
            key = "$type:$verName:$verCode:$installedSuffix:${remoteUrl ?: ""}",
            type = type,
            verName = verName,
            isInstalled = isInstalled,
            hasRemote = remoteUrl != null,
            sizeBytes = cachedSize,
        )
    }

    private fun scheduleRemoteSizeFetches(items: List<ComponentItem>) {
        val urlsToFetch =
            items
                .mapNotNull { item -> profilesByKey[item.key]?.remoteUrl }
                .filter { url -> url !in remoteSizeCache && url !in remoteSizeFetchesInFlight }
                .distinct()

        if (urlsToFetch.isEmpty()) return

        remoteSizeFetchesInFlight.addAll(urlsToFetch)

        viewLifecycleOwner.lifecycleScope.launch {
            urlsToFetch.forEach { url ->
                val size =
                    withContext(Dispatchers.IO) {
                        Downloader.fetchContentLength(url)
                    }
                if (!isAdded || view == null) return@launch
                remoteSizeCache[url] = size
                remoteSizeFetchesInFlight.remove(url)
                publishState()
            }
        }
    }

    private fun scheduleInstalledSizeFetches(items: List<ComponentItem>) {
        val installDirsToFetch =
            items
                .mapNotNull { item -> profilesByKey[item.key] }
                .map { profile -> ContentsManager.getInstallDir(requireContext(), profile).absolutePath }
                .filter { path -> path !in installedSizeCache && path !in installedSizeFetchesInFlight }
                .distinct()

        if (installDirsToFetch.isEmpty()) return

        installedSizeFetchesInFlight.addAll(installDirsToFetch)

        viewLifecycleOwner.lifecycleScope.launch {
            installDirsToFetch.forEach { installDir ->
                val size =
                    withContext(Dispatchers.IO) {
                        StorageUtils.getFolderSize(installDir)
                    }
                if (!isAdded || view == null) return@launch
                installedSizeCache[installDir] = size
                installedSizeFetchesInFlight.remove(installDir)
                publishState()
            }
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun promptInstallFromFile() {
        contentPicker.launch(arrayOf("*/*"))
    }

    private fun onRemoveRequested(profile: ContentProfile) {
        var containerInUse: String? = null
        if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE ||
            profile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON
        ) {
            val containerManager = ContainerManager(requireContext())
            containerManager.containers.forEach { container ->
                if (container.wineVersion == ContentsManager.getEntryName(profile)) {
                    containerInUse = container.name
                    return@forEach
                }
            }
        }

        if (containerInUse != null) {
            ContentDialog.alert(
                requireContext(),
                getString(
                    R.string.settings_content_unable_to_remove_in_use,
                    containerInUse,
                ),
                null,
            )
            return
        }

        manager.removeContent(profile)
        manager.syncContents()
        publishState()
    }

    private fun refreshRemoteProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = context ?: return@launch
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val contentsUrl =
                    preferences.getString(
                        "downloadable_contents_url",
                        ContentsManager.REMOTE_PROFILES,
                    ) ?: ContentsManager.REMOTE_PROFILES

                val json =
                    withContext(Dispatchers.IO) {
                        Downloader.downloadString(contentsUrl)
                    } ?: return@launch

                withContext(Dispatchers.IO) {
                    manager.setRemoteProfiles(json)
                }

                if (isAdded && view != null) {
                    publishState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh remote profiles.", e)
            }
        }
    }

    private fun installSelectedContent(
        uri: Uri,
        completionMessage: String,
        sourceRemoteUrl: String? = null,
    ) {
        val callback =
            object : ContentsManager.OnInstallFinishedCallback {
                private var isExtracting = true
                private var extractedProfile: ContentProfile? = null

                override fun onFailed(
                    reason: ContentsManager.InstallFailedReason,
                    e: Exception?,
                ) {
                    val conflictingProfile = extractedProfile
                    if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST) {
                        conflictingProfile?.let { profile ->
                            if (sourceRemoteUrl != null) {
                                manager.registerRemoteProfileAlias(sourceRemoteUrl, profile)
                            }
                            manager.syncContents()
                        }
                    }

                    val msgId =
                        when (reason) {
                            ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.settings_content_file_cannot_be_recognized
                            ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.settings_content_profile_not_found
                            ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.settings_content_profile_cannot_be_recognized
                            ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.settings_content_is_incomplete
                            ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.settings_content_cannot_be_trusted
                            else -> R.string.settings_content_unable_to_install
                        }

                    runOnMain {
                        clearDownloadProgress()
                        if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST && conflictingProfile != null) {
                            showConflictingContentDialog(conflictingProfile)
                        } else {
                            ContentDialog.alert(
                                requireContext(),
                                getString(R.string.settings_content_install_failed) + ": " + getString(msgId),
                                null,
                            )
                        }
                    }
                }

                override fun onSucceed(profile: ContentProfile) {
                    if (isExtracting) {
                        isExtracting = false
                        extractedProfile = profile
                        updateDownloadProgress(
                            title = getString(R.string.settings_content_installing_title),
                            message = profile.verName,
                            indeterminate = true,
                        )
                        manager.finishInstallContent(profile, this)
                        return
                    }

                    clearDownloadProgress()
                    runOnMain {
                        if (sourceRemoteUrl != null) {
                            manager.registerRemoteProfileAlias(sourceRemoteUrl, profile)
                        }
                        AppUtils.showToast(requireContext(), completionMessage)
                        manager.syncContents()
                        currentContentType = profile.type
                        publishState()

                        if (autoCreateContainer &&
                            (
                                profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE ||
                                    profile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON
                            )
                        ) {
                            val containerManager = ContainerManager(requireContext())

                            var desiredName =
                                profile.verName
                                    .replace("winlator", "", ignoreCase = true)
                                    .replace("wine", "", ignoreCase = true)
                                    .replace(Regex("[^a-zA-Z0-9.\\-]"), " ")
                                    .trim()
                                    .replace(Regex("\\s+"), " ")

                            if (desiredName.isEmpty()) desiredName = getString(R.string.common_ui_container)

                            var uniqueName = desiredName
                            var counter = 2
                            while (containerManager.containers.any { it.name.equals(uniqueName, ignoreCase = true) }) {
                                uniqueName = "$desiredName $counter"
                                counter++
                            }

                            val data =
                                org.json.JSONObject().apply {
                                    put("name", uniqueName)
                                    put("wineVersion", ContentsManager.getEntryName(profile))
                                }

                            val preloaderDialog =
                                com.winlator.cmod.shared.ui.dialog
                                    .PreloaderDialog(activity)
                            preloaderDialog.show(R.string.containers_list_creating)

                            containerManager.createContainerAsync(data, manager) { newContainer ->
                                preloaderDialog.close()
                                if (newContainer != null) {
                                    AppUtils.showToast(
                                        requireContext(),
                                        getString(R.string.settings_content_container_created, uniqueName),
                                    )
                                }
                            }
                        }
                    }
                }
            }

        val extractionProgress =
            ContentsManager.OnExtractionProgressListener { filesExtracted, _ ->
                updateDownloadProgress(
                    title = getString(R.string.settings_content_extracting_title),
                    message = getString(R.string.settings_content_extracting_detail, filesExtracted),
                    indeterminate = true,
                )
            }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { manager.extraContentFile(uri, callback, extractionProgress) }
                .onFailure {
                    runOnMain {
                        clearDownloadProgress()
                        AppUtils.showToast(requireContext(), R.string.input_controls_editor_unable_to_import)
                    }
                }
        }
    }

    private fun showConflictingContentDialog(profile: ContentProfile) {
        val conflictingPath = ContentsManager.getInstallDir(requireContext(), profile).absolutePath
        val dialog = ContentDialog(requireContext())
        dialog.setTitle(R.string.settings_content_conflicting_title)
        dialog.setMessage(
            getString(
                R.string.settings_content_conflicting_message,
                conflictingPath,
            ),
        )
        dialog.findViewById<View>(R.id.BTCancel).isVisible = false
        dialog.show()
    }

    private fun downloadRemoteContent(profile: ContentProfile) {
        val remoteUrl = profile.remoteUrl ?: return
        updateDownloadProgress(
            title = getString(R.string.settings_content_downloading_title),
            message = profile.verName,
            indeterminate = true,
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val output = File(requireContext().cacheDir, "temp_${System.currentTimeMillis()}")
            val success =
                withContext(Dispatchers.IO) {
                    Downloader.downloadFileWinNativeFirst(remoteUrl, output) { downloadedBytes, totalBytes ->
                        if (totalBytes <= 0L) {
                            updateDownloadProgress(
                                title = getString(R.string.settings_content_downloading_title),
                                message = profile.verName,
                                indeterminate = true,
                            )
                            return@downloadFileWinNativeFirst
                        }
                        val fraction =
                            (downloadedBytes.toFloat() / totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                        updateDownloadProgress(
                            title = getString(R.string.settings_content_downloading_title),
                            message = profile.verName,
                            progress = fraction,
                        )
                    }
                }

            if (!isAdded || view == null) {
                output.delete()
                clearDownloadProgress()
                return@launch
            }

            if (success) {
                updateDownloadProgress(
                    title = getString(R.string.settings_content_extracting_title),
                    message = profile.verName,
                    indeterminate = true,
                )
                installSelectedContent(
                    Uri.parse(output.absolutePath),
                    getString(R.string.settings_content_download_complete),
                    remoteUrl,
                )
            } else if (isAdded) {
                clearDownloadProgress()
                AppUtils.showToast(requireContext(), R.string.settings_content_download_failed)
            }
        }
    }

    companion object {
        private const val STATE_CONTENT_TYPE = "state_content_type"
        private const val TAG = "ContentsFragment"
        private const val PREF_AUTO_CREATE_CONTAINER = "components_auto_create_container"
    }
}
