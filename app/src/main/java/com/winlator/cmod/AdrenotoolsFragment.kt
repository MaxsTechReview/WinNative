package com.winlator.cmod

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contents.Downloader
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.ContentTransferDialog
import com.winlator.cmod.databinding.AdrenotoolsAssetListItemBinding
import com.winlator.cmod.databinding.AdrenotoolsFragmentBinding
import com.winlator.cmod.databinding.AdrenotoolsReleaseListItemBinding
import com.winlator.cmod.databinding.ContentListItemBinding
import com.winlator.cmod.databinding.ContentSectionHeaderItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class AdrenotoolsFragment : Fragment() {
    private var _binding: AdrenotoolsFragmentBinding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var adrenotoolsManager: AdrenotoolsManager
    private lateinit var driverAdapter: DriverRowAdapter

    private val githubSources = listOf(
        GithubSourceDefinition(
            name = GITHUB_REPO_NAME,
            repoUrl = GITHUB_REPO_URL,
            apiUrl = GITHUB_API_URL
        )
    )
    private var installedDrivers: List<InstalledDriver> = emptyList()
    private val releasesBySource = linkedMapOf<String, List<GithubRelease>>()
    private var expandedSourceApiUrl: String? = null
    private var loadingSourceApiUrl: String? = null
    private var expandedReleaseId: Long? = null

    private val driverPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                installDriverPackage(it, getString(R.string.driver_installed_success))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adrenotoolsManager = AdrenotoolsManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AdrenotoolsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.driver_manager)

        driverAdapter = DriverRowAdapter()
        binding.RecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = driverAdapter
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        binding.BTInstallDriver.setOnClickListener {
            ContentDialog.confirm(
                requireContext(),
                getString(R.string.install_drivers_message) + " " + getString(R.string.install_drivers_warning)
            ) {
                driverPicker.launch(arrayOf("*/*"))
            }
        }

        refreshInstalledDrivers()
        submitRows()
    }

    override fun onDestroyView() {
        binding.RecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun refreshInstalledDrivers() {
        installedDrivers = adrenotoolsManager.enumarateInstalledDrivers()
            .map { driverId ->
                InstalledDriver(
                    id = driverId,
                    name = adrenotoolsManager.getDriverName(driverId).ifBlank { driverId },
                    version = adrenotoolsManager.getDriverVersion(driverId)
                )
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun buildRows(): List<DriverRow> {
        val rows = mutableListOf<DriverRow>()
        if (installedDrivers.isNotEmpty()) {
            rows += DriverRow.Header(R.string.installed)
            rows += installedDrivers.map { DriverRow.Installed(it) }
        }

        rows += DriverRow.Header(R.string.github_repos)
        githubSources.forEach { source ->
            val sourceReleases = releasesBySource[source.apiUrl].orEmpty()
            val isExpanded = expandedSourceApiUrl == source.apiUrl
            val isLoading = loadingSourceApiUrl == source.apiUrl
            rows += DriverRow.Source(
                GithubSource(
                    name = source.name,
                    url = source.repoUrl,
                    apiUrl = source.apiUrl,
                    status = when {
                        isLoading -> getString(R.string.github_repo_loading_releases)
                        sourceReleases.isNotEmpty() -> getString(R.string.github_repo_release_count, sourceReleases.size)
                        else -> getString(R.string.github_repo_tap_to_load)
                    },
                    expanded = isExpanded,
                    loading = isLoading
                )
            )

            if (isExpanded) {
                if (sourceReleases.isEmpty() && !isLoading) {
                    rows += DriverRow.Header(R.string.available)
                    rows += DriverRow.Empty(getString(R.string.github_repo_no_release_assets))
                } else if (sourceReleases.isNotEmpty()) {
                    rows += DriverRow.Header(R.string.available)
                    rows += sourceReleases.map { release ->
                        DriverRow.Release(
                            release = release,
                            expanded = expandedReleaseId == release.id
                        )
                    }
                }
            }
        }

        return rows
    }

    private fun submitRows() {
        val rows = buildRows()
        binding.TVEmptyText.isVisible = rows.isEmpty()
        binding.RecyclerView.isVisible = rows.isNotEmpty()
        driverAdapter.submitList(rows)
    }

    private fun onSourceSelected(source: GithubSource) {
        if (loadingSourceApiUrl == source.apiUrl) {
            return
        }

        if (expandedSourceApiUrl == source.apiUrl) {
            expandedSourceApiUrl = null
            expandedReleaseId = null
            submitRows()
            return
        }

        expandedSourceApiUrl = source.apiUrl
        expandedReleaseId = null

        if (releasesBySource.containsKey(source.apiUrl)) {
            submitRows()
            return
        }

        loadingSourceApiUrl = source.apiUrl
        submitRows()

        viewLifecycleOwner.lifecycleScope.launch {
            val releases = withContext(Dispatchers.IO) {
                runCatching { fetchGithubReleases(source) }
                    .getOrElse { emptyList() }
            }

            if (!isAdded || _binding == null) {
                return@launch
            }

            if (loadingSourceApiUrl == source.apiUrl) {
                loadingSourceApiUrl = null
            }
            releasesBySource[source.apiUrl] = releases
            submitRows()

            if (releases.isEmpty()) {
                AppUtils.showToast(requireContext(), R.string.github_repo_fetch_failed)
            }
        }
    }

    private fun onReleaseSelected(release: GithubRelease) {
        expandedReleaseId = if (expandedReleaseId == release.id) null else release.id
        submitRows()
    }

    private fun fetchGithubReleases(source: GithubSource): List<GithubRelease> {
        val connection = (URL(source.apiUrl).openConnection() as HttpURLConnection).apply {
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
                    if (assets.isEmpty()) {
                        continue
                    }

                    val tagName = releaseObject.optString("tag_name")
                    val releaseName = releaseObject.optString("name").ifBlank { tagName }
                    val publishedAt = releaseObject.optString("published_at")
                    val releaseNotes = releaseObject.optString("body").toReleaseNotes()

                    add(
                        GithubRelease(
                            id = releaseObject.optLong("id"),
                            title = releaseName.ifBlank { getString(R.string.unnamed) },
                            subtitle = buildReleaseSubtitle(tagName, publishedAt, assets.size),
                            notes = releaseNotes,
                            assets = assets
                        )
                    )
                }
            }
        }
    }

    private fun JSONArray?.toZipAssets(): List<GithubAsset> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val assetObject = optJSONObject(index) ?: continue
                val assetName = assetObject.optString("name")
                val downloadUrl = assetObject.optString("browser_download_url")
                if (!assetName.lowercase(Locale.getDefault()).endsWith(".zip") || downloadUrl.isBlank()) {
                    continue
                }

                add(
                    GithubAsset(
                        id = assetObject.optLong("id"),
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeLabel = formatBytes(assetObject.optLong("size"))
                    )
                )
            }
        }
    }

    private fun buildReleaseSubtitle(tagName: String, publishedAt: String, assetCount: Int): String {
        val subtitleParts = mutableListOf<String>()
        if (tagName.isNotBlank()) {
            subtitleParts += tagName
        }

        if (publishedAt.isNotBlank()) {
            runCatching {
                val formattedDate = OffsetDateTime.parse(publishedAt).format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                )
                subtitleParts += formattedDate
            }
        }

        subtitleParts += resources.getQuantityString(R.plurals.github_repo_asset_count, assetCount, assetCount)
        return subtitleParts.joinToString("  •  ")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return getString(R.string.github_repo_unknown_size)
        }

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

    private fun String.toReleaseNotes(): String {
        return lineSequence()
            .map { line -> line.trim() }
            .dropWhile { it.isBlank() }
            .map { line ->
                line.removePrefix("#")
                    .removePrefix("##")
                    .removePrefix("###")
                    .removePrefix("- [ ] ")
                    .removePrefix("- [x] ")
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .trim()
            }
            .fold(mutableListOf<String>()) { acc, line ->
                if (line.isBlank()) {
                    if (acc.isNotEmpty() && acc.last().isNotBlank()) {
                        acc += ""
                    }
                } else {
                    acc += line
                }
                acc
            }
            .joinToString("\n")
            .trim()
    }

    private fun downloadReleaseAsset(asset: GithubAsset) {
        val transferDialog = ContentTransferDialog(requireActivity())
        transferDialog.show(
            getString(R.string.contents_downloading_title),
            asset.name
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val output = File(requireContext().cacheDir, "driver_${System.currentTimeMillis()}.zip")
            val success = withContext(Dispatchers.IO) {
                Downloader.downloadFile(asset.downloadUrl, output) { downloadedBytes, totalBytes ->
                    if (totalBytes <= 0L) {
                        transferDialog.update(
                            getString(R.string.contents_downloading_title),
                            asset.name,
                            indeterminate = true
                        )
                        return@downloadFile
                    }

                    val progressUnits = ((downloadedBytes * ContentTransferDialog.PROGRESS_SCALE) / totalBytes)
                        .toInt()
                        .coerceIn(0, ContentTransferDialog.PROGRESS_SCALE)
                    transferDialog.update(
                        getString(R.string.contents_downloading_title),
                        asset.name,
                        progress = progressUnits
                    )
                }
            }

            if (!isAdded || _binding == null) {
                output.delete()
                transferDialog.dismiss()
                return@launch
            }

            if (!success) {
                output.delete()
                transferDialog.dismiss()
                AppUtils.showToast(requireContext(), R.string.github_repo_download_failed)
                return@launch
            }

            installDriverPackage(Uri.fromFile(output), getString(R.string.driver_installed_success), transferDialog) {
                output.delete()
            }
        }
    }

    private fun installDriverPackage(
        uri: Uri,
        successMessage: String,
        existingDialog: ContentTransferDialog? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val transferDialog = existingDialog ?: ContentTransferDialog(requireActivity()).apply {
            show(
                getString(R.string.install_drivers),
                getString(R.string.contents_preparing_package),
                indeterminate = true
            )
        }

        if (existingDialog != null) {
            existingDialog.update(
                getString(R.string.install_drivers),
                getString(R.string.contents_preparing_package),
                progress = ContentTransferDialog.PROGRESS_SCALE
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val installedDriverId = withContext(Dispatchers.IO) {
                runCatching { adrenotoolsManager.installDriver(uri) }
                    .getOrDefault("")
            }

            if (!isAdded || _binding == null) {
                transferDialog.dismiss()
                onComplete?.invoke()
                return@launch
            }

            transferDialog.dismiss()
            onComplete?.invoke()

            if (installedDriverId.isBlank()) {
                AppUtils.showToast(requireContext(), R.string.driver_install_failed)
                return@launch
            }

            AppUtils.showToast(requireContext(), successMessage)
            refreshInstalledDrivers()
            submitRows()
        }
    }

    private fun showDriverMenu(driver: InstalledDriver, anchor: View) {
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

        popupMenu.inflate(R.menu.driver_popup_menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.remove_driver -> {
                    ContentDialog.confirm(
                        requireContext(),
                        R.string.do_you_want_to_remove_this_driver
                    ) {
                        adrenotoolsManager.removeDriver(driver.id)
                        refreshInstalledDrivers()
                        submitRows()
                    }
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private inner class DriverRowAdapter :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var items: List<DriverRow> = emptyList()
            private set

        fun submitList(newItems: List<DriverRow>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = items.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return DiffCallback.areItemsTheSame(items[oldItemPosition], newItems[newItemPosition])
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return DiffCallback.areContentsTheSame(items[oldItemPosition], newItems[newItemPosition])
                }
            })
            items = newItems
            diffResult.dispatchUpdatesTo(this)
        }

        fun getItem(position: Int): DriverRow = items[position]

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int =
            when (getItem(position)) {
                is DriverRow.Header -> VIEW_TYPE_HEADER
                is DriverRow.Installed -> VIEW_TYPE_INSTALLED
                is DriverRow.Source -> VIEW_TYPE_SOURCE
                is DriverRow.Release -> VIEW_TYPE_RELEASE
                is DriverRow.Empty -> VIEW_TYPE_EMPTY
            }

        inner class HeaderViewHolder(
            private val itemBinding: ContentSectionHeaderItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(header: DriverRow.Header) {
                itemBinding.TVSectionTitle.setText(header.titleResId)
            }
        }

        inner class InstalledViewHolder(
            private val itemBinding: ContentListItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(driver: InstalledDriver) {
                itemBinding.IVIcon.setImageResource(R.drawable.ic_drivers)
                itemBinding.TVVersionName.text = driver.name
                itemBinding.TVVersionCode.text = driver.version.ifBlank {
                    getString(R.string.github_repo_no_version)
                }
                itemBinding.Progress.isVisible = false
                itemBinding.BTDownload.isVisible = false
                itemBinding.BTMenu.isVisible = true
                itemBinding.BTMenu.rotation = 0f
                itemBinding.BTMenu.setOnClickListener {
                    showDriverMenu(driver, itemBinding.BTMenu)
                }
            }
        }

        inner class SourceViewHolder(
            private val itemBinding: ContentListItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(source: GithubSource) {
                val tagState = itemBinding.BTMenu.getTag() as? Pair<*, *>
                val isSameSource = tagState != null && tagState.first == source.apiUrl
                val previousExpanded = tagState?.second as? Boolean

                itemBinding.IVIcon.setImageResource(R.drawable.ic_content_linked)
                itemBinding.TVVersionName.text = source.name
                itemBinding.TVVersionCode.text = source.status
                itemBinding.BTDownload.isVisible = false
                itemBinding.Progress.isVisible = source.loading
                itemBinding.BTMenu.isVisible = true

                updateMenuRotation(source, isSameSource, previousExpanded)

                val clickListener = View.OnClickListener {
                    onSourceSelected(source)
                }
                itemBinding.root.setOnClickListener(clickListener)
                itemBinding.BTMenu.setOnClickListener(clickListener)
            }

            private fun updateMenuRotation(source: GithubSource, isSameSource: Boolean, previousExpanded: Boolean?) {
                val targetRotation = if (source.expanded) 90f else 0f

                if (isSameSource && previousExpanded == source.expanded) {
                    return
                }

                itemBinding.BTMenu.animate().cancel()

                if (!isSameSource || previousExpanded == null) {
                    itemBinding.BTMenu.rotation = targetRotation
                } else {
                    itemBinding.BTMenu.animate()
                        .rotation(targetRotation)
                        .setDuration(220L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }

                itemBinding.BTMenu.setTag(Pair(source.apiUrl, source.expanded))
            }
        }

        inner class ReleaseViewHolder(
            private val itemBinding: AdrenotoolsReleaseListItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(release: GithubRelease, expanded: Boolean) {
                val tagState = itemBinding.IVExpandState.getTag() as? Pair<*, *>
                val isSameRelease = tagState != null && tagState.first == release.id
                val previousExpanded = tagState?.second as? Boolean

                if (isSameRelease && previousExpanded != null && previousExpanded != expanded) {
                    val transition = androidx.transition.TransitionSet().apply {
                        ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
                        addTransition(androidx.transition.ChangeBounds())
                        addTransition(androidx.transition.Fade())
                        duration = 200L
                        interpolator = AccelerateDecelerateInterpolator()
                    }
                    androidx.transition.TransitionManager.beginDelayedTransition(
                        itemBinding.root as ViewGroup, transition
                    )
                }

                updateChevronRotation(expanded, release.id, isSameRelease, previousExpanded)
                itemBinding.TVTitle.text = release.title
                itemBinding.TVSubtitle.text = release.subtitle
                itemBinding.TVNotes.isVisible = expanded && release.notes.isNotBlank()
                itemBinding.TVNotes.text = release.notes
                itemBinding.LLAssets.isVisible = expanded

                if (!isSameRelease || itemBinding.LLAssets.childCount != release.assets.size) {
                    itemBinding.LLAssets.removeAllViews()
                    release.assets.forEach { asset ->
                        itemBinding.LLAssets.addView(createAssetRow(itemBinding.LLAssets, asset))
                    }
                }

                val clickListener = View.OnClickListener {
                    onReleaseSelected(release)
                }
                itemBinding.root.setOnClickListener(clickListener)
                itemBinding.HeaderRow.setOnClickListener(clickListener)
                itemBinding.IVExpandState.setOnClickListener(clickListener)
            }

            private fun updateChevronRotation(expanded: Boolean, releaseId: Long, isSameRelease: Boolean, previousExpanded: Boolean?) {
                val targetRotation = if (expanded) 90f else 0f

                if (isSameRelease && previousExpanded == expanded) {
                    return
                }

                itemBinding.IVExpandState.animate().cancel()

                if (!isSameRelease || previousExpanded == null) {
                    itemBinding.IVExpandState.rotation = targetRotation
                } else {
                    itemBinding.IVExpandState.animate()
                        .rotation(targetRotation)
                        .setDuration(220L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }

                itemBinding.IVExpandState.setTag(Pair(releaseId, expanded))
            }

            private fun createAssetRow(parent: ViewGroup, asset: GithubAsset): View {
                val assetBinding = AdrenotoolsAssetListItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                assetBinding.TVAssetName.text = asset.name
                assetBinding.TVAssetInfo.text = asset.sizeLabel
                assetBinding.TVAssetInfo.isVisible = asset.sizeLabel.isNotBlank()
                assetBinding.BTDownload.setOnClickListener {
                    downloadReleaseAsset(asset)
                }
                assetBinding.root.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.github_asset_button_spacing)
                }
                return assetBinding.root
            }
        }

        inner class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(message: String) {
                (itemView as TextView).text = message
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> HeaderViewHolder(
                    ContentSectionHeaderItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )

                VIEW_TYPE_SOURCE -> SourceViewHolder(
                    ContentListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )

                VIEW_TYPE_RELEASE -> ReleaseViewHolder(
                    AdrenotoolsReleaseListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )

                VIEW_TYPE_EMPTY -> {
                    val emptyView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.content_section_header_item, parent, false) as TextView
                    EmptyViewHolder(emptyView)
                }

                else -> InstalledViewHolder(
                    ContentListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is DriverRow.Header -> (holder as HeaderViewHolder).bind(row)
                is DriverRow.Installed -> (holder as InstalledViewHolder).bind(row.driver)
                is DriverRow.Source -> (holder as SourceViewHolder).bind(row.source)
                is DriverRow.Release -> (holder as ReleaseViewHolder).bind(row.release, row.expanded)
                is DriverRow.Empty -> (holder as EmptyViewHolder).bind(row.message)
            }
        }
    }

    private sealed interface DriverRow {
        data class Header(val titleResId: Int) : DriverRow
        data class Installed(val driver: InstalledDriver) : DriverRow
        data class Source(val source: GithubSource) : DriverRow
        data class Release(val release: GithubRelease, val expanded: Boolean) : DriverRow
        data class Empty(val message: String) : DriverRow
    }

    private data class InstalledDriver(
        val id: String,
        val name: String,
        val version: String
    )

    private data class GithubSource(
        val name: String,
        val url: String,
        val apiUrl: String,
        val status: String,
        val expanded: Boolean,
        val loading: Boolean
    )

    private data class GithubSourceDefinition(
        val name: String,
        val repoUrl: String,
        val apiUrl: String
    )

    private data class GithubRelease(
        val id: Long,
        val title: String,
        val subtitle: String,
        val notes: String,
        val assets: List<GithubAsset>
    )

    private data class GithubAsset(
        val id: Long,
        val name: String,
        val downloadUrl: String,
        val sizeLabel: String
    )

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_INSTALLED = 1
        private const val VIEW_TYPE_SOURCE = 2
        private const val VIEW_TYPE_RELEASE = 3
        private const val VIEW_TYPE_EMPTY = 4

        private const val GITHUB_REPO_NAME = "StevenMXZ/freedreno_turnip-CI"
        private const val GITHUB_REPO_URL = "https://github.com/StevenMXZ/freedreno_turnip-CI/releases"
        private const val GITHUB_API_URL = "https://api.github.com/repos/StevenMXZ/freedreno_turnip-CI/releases"

        private val DiffCallback = object : DiffUtil.ItemCallback<DriverRow>() {
            override fun areItemsTheSame(oldItem: DriverRow, newItem: DriverRow): Boolean {
                return when {
                    oldItem is DriverRow.Header && newItem is DriverRow.Header -> oldItem.titleResId == newItem.titleResId
                    oldItem is DriverRow.Installed && newItem is DriverRow.Installed -> oldItem.driver.id == newItem.driver.id
                    oldItem is DriverRow.Source && newItem is DriverRow.Source -> oldItem.source.name == newItem.source.name
                    oldItem is DriverRow.Release && newItem is DriverRow.Release -> oldItem.release.id == newItem.release.id
                    oldItem is DriverRow.Empty && newItem is DriverRow.Empty -> oldItem.message == newItem.message
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: DriverRow, newItem: DriverRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}

private inline fun <T> HttpURLConnection.useResponse(block: (String) -> T): T {
    return try {
        val inputStream = if (responseCode in 200..299) inputStream else errorStream
        val body = inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            throw IllegalStateException(body.ifBlank { "GitHub request failed with HTTP $responseCode" })
        }
        block(body)
    } finally {
        disconnect()
    }
}