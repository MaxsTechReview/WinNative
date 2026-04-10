/* Components screen UI: tabs, installed/available content lists, downloads, and package installs. */
package com.winlator.cmod

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.contentdialog.ContentInfoDialog
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.contents.Downloader
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.ContentTransferDialog
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.databinding.ContentListItemBinding
import com.winlator.cmod.databinding.ContentSectionHeaderItemBinding
import com.winlator.cmod.databinding.ContentsFragmentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ContentsFragment : Fragment() {
    private var _binding: ContentsFragmentBinding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var manager: ContentsManager
    private lateinit var contentAdapter: ContentItemAdapter
    private val contentTabs = linkedMapOf<ContentProfile.ContentType, TextView>()

    private var currentContentType = ContentProfile.ContentType.CONTENT_TYPE_WINE

    private val contentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val transferDialog = ContentTransferDialog(requireActivity())
                transferDialog.show(
                    getString(R.string.settings_content_installing_title),
                    getString(R.string.settings_content_preparing_package),
                    indeterminate = true
                )
                installSelectedContent(
                    it,
                    transferDialog,
                    getString(R.string.settings_content_installed_success)
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
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ContentsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.settings_content_components)

        contentAdapter = ContentItemAdapter()
        binding.RecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = contentAdapter
        }

        setupContentTypeTabs()

        binding.BTInstallContent.setOnClickListener {
            val dialog = ContentDialog(requireContext())
            dialog.setIcon(R.drawable.ic_content_notice)
            dialog.setTitle(R.string.settings_content_install)
            dialog.setMessage(buildInstallConfirmMessage())
            dialog.setOnConfirmCallback {
                contentPicker.launch(arrayOf("*/*"))
            }
            dialog.show()
        }

        selectContentType(currentContentType, animateScroll = false)
    }

    override fun onResume() {
        super.onResume()
        refreshRemoteProfiles()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CONTENT_TYPE, currentContentType.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.RecyclerView.adapter = null
        contentTabs.clear()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        context?.cacheDir?.let(FileUtils::clear)
        super.onDestroy()
    }

    private fun setupContentTypeTabs() {
        binding.LLContentTypeTabs.removeAllViews()
        contentTabs.clear()

        val inflater = LayoutInflater.from(requireContext())
        ContentProfile.ContentType.values().forEach { type ->
            val tab = inflater.inflate(
                R.layout.content_type_tab_item,
                binding.LLContentTypeTabs,
                false
            ) as TextView

            tab.text = type.toString()
            tab.setOnClickListener { selectContentType(type) }

            binding.LLContentTypeTabs.addView(tab)
            contentTabs[type] = tab
        }
    }

    private fun selectContentType(
        type: ContentProfile.ContentType,
        animateScroll: Boolean = true
    ) {
        currentContentType = type
        updateInstallAction(type)
        contentTabs.forEach { (tabType, tabView) ->
            tabView.isSelected = tabType == type
        }

        if (animateScroll) {
            val selectedTab = contentTabs[type] ?: return
            binding.HSVContentTypeNav.post {
                val scrollX =
                    (selectedTab.left - (binding.HSVContentTypeNav.width - selectedTab.width) / 2)
                        .coerceAtLeast(0)
                binding.HSVContentTypeNav.smoothScrollTo(scrollX, 0)
            }
        }

        loadContentList(scrollToTop = true)
    }

    private fun updateInstallAction(type: ContentProfile.ContentType) {
        binding.BTInstallContent.contentDescription =
            "${getString(R.string.settings_content_install)} ${type}"
    }

    private fun refreshRemoteProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = context ?: return@launch
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val contentsUrl = preferences.getString(
                    "downloadable_contents_url",
                    ContentsManager.REMOTE_PROFILES
                ) ?: ContentsManager.REMOTE_PROFILES

                val json = withContext(Dispatchers.IO) {
                    Downloader.downloadString(contentsUrl)
                } ?: return@launch

                withContext(Dispatchers.IO) {
                    manager.setRemoteProfiles(json)
                }

                if (isAdded && _binding != null) {
                    loadContentList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh remote profiles.", e)
            }
        }
    }

    private fun installSelectedContent(
        uri: Uri,
        transferDialog: ContentTransferDialog,
        completionMessage: String,
        sourceRemoteUrl: String? = null
    ) {
        val callback = object : ContentsManager.OnInstallFinishedCallback {
            private var isExtracting = true
            private var extractedProfile: ContentProfile? = null

            override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                val conflictingProfile = extractedProfile
                if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST) {
                    conflictingProfile?.let { profile ->
                        if (sourceRemoteUrl != null) {
                            manager.registerRemoteProfileAlias(sourceRemoteUrl, profile)
                        }
                        manager.syncContents()
                    }
                }

                val msgId = when (reason) {
                    ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.settings_content_file_cannot_be_recognized
                    ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.settings_content_profile_not_found
                    ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.settings_content_profile_cannot_be_recognized
                    ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.settings_content_is_incomplete
                    ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.settings_content_cannot_be_trusted
                    else -> R.string.settings_content_unable_to_install
                }

                activity?.runOnUiThread {
                    transferDialog.dismiss()
                    if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST && conflictingProfile != null) {
                        showConflictingContentDialog(conflictingProfile)
                    } else {
                        ContentDialog.alert(
                            requireContext(),
                            getString(R.string.settings_content_install_failed) + ": " + getString(msgId),
                            null
                        )
                    }
                }
            }

            override fun onSucceed(profile: ContentProfile) {
                if (isExtracting) {
                    isExtracting = false
                    extractedProfile = profile
                    // Transition from extracting to installing phase
                    transferDialog.update(
                        getString(R.string.settings_content_installing_title),
                        profile.verName,
                        indeterminate = true
                    )
                    manager.finishInstallContent(profile, this)
                    return
                }

                transferDialog.dismiss()
                activity?.runOnUiThread {
                    if (sourceRemoteUrl != null) {
                        manager.registerRemoteProfileAlias(sourceRemoteUrl, profile)
                    }
                    AppUtils.showToast(requireContext(), completionMessage)
                    manager.syncContents()
                    selectContentType(profile.type)

                    // Automatically create a container if it's Wine or Proton
                    if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE || profile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON) {
                        val containerManager = com.winlator.cmod.container.ContainerManager(requireContext())
                        
                        // Clean up the container name
                        var desiredName = profile.verName
                            .replace("winlator", "", ignoreCase = true)
                            .replace("wine", "", ignoreCase = true)
                            .replace(Regex("[^a-zA-Z0-9.\\-]"), " ")
                            .trim()
                            .replace(Regex("\\s+"), " ")
                        
                        if (desiredName.isEmpty()) desiredName = "Container"
                        
                        // Ensure unique name
                        var uniqueName = desiredName
                        var counter = 2
                        while (containerManager.containers.any { it.name.equals(uniqueName, ignoreCase = true) }) {
                            uniqueName = "$desiredName $counter"
                            counter++
                        }

                        val data = org.json.JSONObject().apply {
                            put("name", uniqueName)
                            put("wineVersion", ContentsManager.getEntryName(profile))
                        }

                        val preloaderDialog = com.winlator.cmod.core.PreloaderDialog(activity)
                        preloaderDialog.show(R.string.containers_list_creating)
                        
                        containerManager.createContainerAsync(data, manager) { newContainer ->
                            preloaderDialog.close()
                            if (newContainer != null) {
                                AppUtils.showToast(requireContext(), "Created container: $uniqueName")
                            }
                        }
                    }
                }
            }
        }

        // Progress listener that updates the dialog during extraction
        val extractionProgress = ContentsManager.OnExtractionProgressListener { filesExtracted, currentFileName ->
            transferDialog.update(
                getString(R.string.settings_content_extracting_title),
                getString(R.string.settings_content_extracting_detail, filesExtracted),
                indeterminate = true
            )
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { manager.extraContentFile(uri, callback, extractionProgress) }
                .onFailure {
                    activity?.runOnUiThread {
                        transferDialog.dismiss()
                        AppUtils.showToast(requireContext(), R.string.input_controls_editor_unable_to_import)
                    }
                }
        }
    }

    private fun loadContentList(scrollToTop: Boolean = false) {
        val profiles = manager.getProfiles(currentContentType).orEmpty()
        binding.TVEmptyText.isVisible = profiles.isEmpty()
        binding.RecyclerView.isVisible = profiles.isNotEmpty()
        contentAdapter.submitList(buildContentRows(profiles)) {
            if (_binding != null && scrollToTop) {
                binding.RecyclerView.stopScroll()
                binding.RecyclerView.scrollToPosition(0)
            }
        }
    }

    private fun buildContentRows(profiles: List<ContentProfile>): List<ContentRow> {
        if (profiles.isEmpty()) {
            return emptyList()
        }

        val installed = profiles.filter { it.isInstalled }
            .sortedWith(compareByDescending<ContentProfile> { it.isInstalled }.thenBy { it.verName.lowercase() }.thenByDescending { it.verCode })
        val available = profiles.filterNot { it.isInstalled }
            .sortedWith(compareBy<ContentProfile> { it.verName.lowercase() }.thenByDescending { it.verCode })

        return buildList {
            if (installed.isNotEmpty()) {
                add(ContentRow.Header(R.string.common_ui_installed))
                installed.forEach { add(ContentRow.Item(it)) }
            }

            if (available.isNotEmpty()) {
                add(ContentRow.Header(R.string.common_ui_available))
                available.forEach { add(ContentRow.Item(it)) }
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
                conflictingPath
            )
        )
        dialog.findViewById<View>(R.id.BTCancel).isVisible = false
        dialog.show()
    }

    private fun buildInstallConfirmMessage(): CharSequence {
        val message = getString(R.string.settings_content_install_confirm_message)
        val builder = SpannableStringBuilder(message)

        listOf(".wcp", "xz", "zst").forEach { token ->
            var startIndex = message.indexOf(token)
            while (startIndex >= 0) {
                builder.setSpan(
                    TypefaceSpan("monospace"),
                    startIndex,
                    startIndex + token.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = message.indexOf(token, startIndex + token.length)
            }
        }

        return builder
    }

    private inner class ContentItemAdapter :
        ListAdapter<ContentRow, RecyclerView.ViewHolder>(DiffCallback) {

        override fun getItemViewType(position: Int): Int =
            when (getItem(position)) {
                is ContentRow.Header -> VIEW_TYPE_HEADER
                is ContentRow.Item -> VIEW_TYPE_ITEM
            }

        inner class HeaderViewHolder(
            private val itemBinding: ContentSectionHeaderItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(header: ContentRow.Header) {
                itemBinding.TVSectionTitle.setText(header.titleResId)
            }
        }

        inner class ContentViewHolder(
            private val itemBinding: ContentListItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(profile: ContentProfile) {
                itemBinding.IVIcon.setImageResource(iconFor(profile.type))
                itemBinding.TVVersionName.text =
                    getString(R.string.common_ui_version) + ": " + profile.verName
                itemBinding.TVVersionCode.text =
                    getString(R.string.settings_content_version_code) + ": " + profile.verCode

                itemBinding.Progress.isVisible = false
                itemBinding.BTMenu.isVisible = profile.isInstalled
                itemBinding.BTDownload.isVisible = !profile.isInstalled && profile.remoteUrl != null

                itemBinding.BTMenu.setOnClickListener(null)
                itemBinding.BTDownload.setOnClickListener(null)

                if (profile.isInstalled) {
                    itemBinding.BTMenu.setOnClickListener {
                        showContentMenu(profile, itemBinding.BTMenu)
                    }
                } else if (profile.remoteUrl != null) {
                    itemBinding.BTDownload.setOnClickListener {
                        downloadRemoteContent(profile)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val itemBinding = ContentSectionHeaderItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                    HeaderViewHolder(itemBinding)
                }

                else -> {
                    val itemBinding = ContentListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                    ContentViewHolder(itemBinding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is ContentRow.Header -> (holder as HeaderViewHolder).bind(row)
                is ContentRow.Item -> (holder as ContentViewHolder).bind(row.profile)
            }
        }
    }

    private fun showContentMenu(profile: ContentProfile, anchor: View) {
        val popupContext = ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_ContentPopupMenu)
        val popupMenu = PopupMenu(
            popupContext,
            anchor,
            Gravity.END,
            0,
            R.style.Widget_ContentPopupMenu
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setForceShowIcon(true)
        }
        popupMenu.inflate(R.menu.content_popup_menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.content_info -> {
                    ContentInfoDialog(requireContext(), profile).show()
                    true
                }

                R.id.remove_content -> {
                    ContentDialog.confirm(
                        requireContext(),
                        R.string.settings_content_confirm_remove
                    ) {
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
                                    containerInUse
                                ),
                                null
                            )
                        } else {
                            manager.removeContent(profile)
                            manager.syncContents()
                            loadContentList(scrollToTop = true)
                        }
                    }
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun downloadRemoteContent(profile: ContentProfile) {
        val remoteUrl = profile.remoteUrl ?: return
        val transferDialog = ContentTransferDialog(requireActivity())
        transferDialog.show(
            getString(R.string.settings_content_downloading_title),
            profile.verName
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val output = File(requireContext().cacheDir, "temp_${System.currentTimeMillis()}")
            val success = withContext(Dispatchers.IO) {
                Downloader.downloadFileWinNativeFirst(remoteUrl, output) { downloadedBytes, totalBytes ->
                    if (totalBytes <= 0L) {
                        transferDialog.update(
                            getString(R.string.settings_content_downloading_title),
                            profile.verName,
                            indeterminate = true
                        )
                        return@downloadFileWinNativeFirst
                    }
                    val progressUnits = ((downloadedBytes * ContentTransferDialog.PROGRESS_SCALE) / totalBytes)
                        .toInt()
                        .coerceIn(0, ContentTransferDialog.PROGRESS_SCALE)
                    transferDialog.update(
                        getString(R.string.settings_content_downloading_title),
                        profile.verName,
                        progress = progressUnits
                    )
                }
            }

            if (!isAdded || _binding == null) {
                output.delete()
                transferDialog.dismiss()
                return@launch
            }

            if (success) {
                // Transition: show "Extracting..." before starting the install phase
                transferDialog.update(
                    getString(R.string.settings_content_extracting_title),
                    profile.verName,
                    indeterminate = true
                )
                installSelectedContent(
                    Uri.parse(output.absolutePath),
                    transferDialog,
                    getString(R.string.settings_content_download_complete),
                    remoteUrl
                )
            } else if (isAdded) {
                transferDialog.dismiss()
                AppUtils.showToast(requireContext(), "Download failed.")
            }
        }
    }

    private fun iconFor(type: ContentProfile.ContentType): Int =
        when (type) {
            ContentProfile.ContentType.CONTENT_TYPE_WINE,
            ContentProfile.ContentType.CONTENT_TYPE_PROTON -> R.drawable.icon_wine

            ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            ContentProfile.ContentType.CONTENT_TYPE_VKD3D -> R.drawable.ic_drivers

            ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE -> R.drawable.icon_cpu
        }

    companion object {
        private const val STATE_CONTENT_TYPE = "state_content_type"
        private const val TAG = "ContentsFragment"
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<ContentRow>() {
            override fun areItemsTheSame(
                oldItem: ContentRow,
                newItem: ContentRow
            ): Boolean {
                return when {
                    oldItem is ContentRow.Header && newItem is ContentRow.Header -> {
                        oldItem.titleResId == newItem.titleResId
                    }

                    oldItem is ContentRow.Item && newItem is ContentRow.Item -> {
                        oldItem.profile.type == newItem.profile.type &&
                            oldItem.profile.verName == newItem.profile.verName &&
                            oldItem.profile.verCode == newItem.profile.verCode
                    }

                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: ContentRow,
                newItem: ContentRow
            ): Boolean {
                return when {
                    oldItem is ContentRow.Header && newItem is ContentRow.Header -> {
                        oldItem.titleResId == newItem.titleResId
                    }

                    oldItem is ContentRow.Item && newItem is ContentRow.Item -> {
                        oldItem.profile.type == newItem.profile.type &&
                            oldItem.profile.verName == newItem.profile.verName &&
                            oldItem.profile.verCode == newItem.profile.verCode &&
                            oldItem.profile.remoteUrl == newItem.profile.remoteUrl &&
                            oldItem.profile.isInstalled == newItem.profile.isInstalled
                    }

                    else -> false
                }
            }
        }
    }

    private sealed interface ContentRow {
        data class Header(val titleResId: Int) : ContentRow
        data class Item(val profile: ContentProfile) : ContentRow
    }
}
