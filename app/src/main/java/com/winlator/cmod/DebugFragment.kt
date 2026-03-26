package com.winlator.cmod

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.core.ArrayUtils
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DebugFragment : Fragment() {
    private lateinit var preferences: SharedPreferences
    private lateinit var cbEnableWineDebug: CompoundButton
    private lateinit var cbEnableBox64Logs: CompoundButton
    private lateinit var cbEnableFexcoreLogs: CompoundButton
    private lateinit var cbEnableSteamLogs: CompoundButton
    private lateinit var cbEnableInputLogs: CompoundButton
    private lateinit var cbEnableDownloadLogs: CompoundButton
    private lateinit var wineDebugChannels: ArrayList<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.advanced_fragment, container, false)
        val context = requireContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val wineChannelSection = view.findViewById<View>(R.id.LLWineDebugChannelSection)

        cbEnableWineDebug = view.findViewById(R.id.CBEnableWineDebug)
        cbEnableWineDebug.isChecked = preferences.getBoolean("enable_wine_debug", false)
        updateWineChannelSection(wineChannelSection, cbEnableWineDebug.isChecked)
        cbEnableWineDebug.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit { putBoolean("enable_wine_debug", isChecked) }
            updateWineChannelSection(wineChannelSection, isChecked)
            com.winlator.cmod.core.LogManager.updateLoggingState(context)
        }

        wineDebugChannels = preferences.getString(
            "wine_debug_channels",
            SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS
        )?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toCollection(ArrayList())
            ?: arrayListOf()
        loadWineDebugChannels(view, wineDebugChannels)

        cbEnableBox64Logs = view.findViewById(R.id.CBEnableBox64Logs)
        cbEnableBox64Logs.isChecked = preferences.getBoolean("enable_box64_logs", false)
        cbEnableBox64Logs.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit { putBoolean("enable_box64_logs", isChecked) }
            com.winlator.cmod.core.LogManager.updateLoggingState(context)
        }

        cbEnableFexcoreLogs = view.findViewById(R.id.CBEnableFexcoreLogs)
        cbEnableFexcoreLogs.isChecked = preferences.getBoolean("enable_fexcore_logs", false)
        cbEnableFexcoreLogs.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit { putBoolean("enable_fexcore_logs", isChecked) }
            com.winlator.cmod.core.LogManager.updateLoggingState(context)
        }

        cbEnableSteamLogs = view.findViewById(R.id.CBEnableSteamLogs)
        cbEnableSteamLogs.isChecked = com.winlator.cmod.steam.utils.PrefManager.enableSteamLogs
        cbEnableSteamLogs.setOnCheckedChangeListener { _, isChecked ->
            com.winlator.cmod.steam.utils.PrefManager.enableSteamLogs = isChecked

            // Re-plant Timber if enabled during runtime
            if (isChecked && timber.log.Timber.forest().isEmpty()) {
                timber.log.Timber.plant(timber.log.Timber.DebugTree())
            }
            com.winlator.cmod.core.LogManager.updateLoggingState(context)
        }

        // Input Logs toggle (off by default)
        cbEnableInputLogs = view.findViewById(R.id.CBEnableInputLogs)
        cbEnableInputLogs.isChecked = preferences.getBoolean("enable_input_logs", false)
        cbEnableInputLogs.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit { putBoolean("enable_input_logs", isChecked) }
            com.winlator.cmod.core.LogManager.updateLoggingState(context)
        }

        // Download Logs toggle (off by default)
        cbEnableDownloadLogs = view.findViewById(R.id.CBEnableDownloadLogs)
        cbEnableDownloadLogs.isChecked = preferences.getBoolean("enable_download_logs", false)
        cbEnableDownloadLogs.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit { putBoolean("enable_download_logs", isChecked) }
            com.winlator.cmod.core.LogManager.updateLoggingState(context)
        }

        // Share Logs button
        val shareButton = view.findViewById<Button>(R.id.BTShareLogs)
        shareButton.setOnClickListener { shareLogs() }

        return view
    }

    private fun shareLogs() {
        val context = requireContext()
        val logsDir = com.winlator.cmod.core.LogManager.getLogsDir(context)
        val files = logsDir.listFiles()
        
        if (files == null || files.isEmpty()) {
            AppUtils.showToast(context, "No debug logs available. Enable debugging options first and launch a game.")
            return
        }

        try {
            // Create zip file in cache
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val zipFile = File(context.cacheDir, "winnative_logs_$timestamp.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { file ->
                    if (file.isFile) {
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            // Store reference for auto-cleanup
            lastSharedLogFile = zipFile

            // Delete shared zip after 3 minutes
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (zipFile.exists() && lastSharedLogFile == zipFile) {
                    zipFile.delete()
                    if (lastSharedLogFile == zipFile) lastSharedLogFile = null
                }
            }, 3 * 60 * 1000)

            // Share via Android share sheet
            val authority = "${context.packageName}.tileprovider"
            val uri = FileProvider.getUriForFile(context, authority, zipFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "WinNative Logs ($timestamp)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Logs"))

            // Auto-delete after 3 minutes
            Handler(Looper.getMainLooper()).postDelayed({
                cleanupSharedLogs()
            }, 3 * 60 * 1000L)

        } catch (e: Exception) {
            AppUtils.showToast(context, "Failed to capture logs: ${e.message}")
        }
    }

    private fun loadWineDebugChannels(view: View, debugChannels: ArrayList<String>) {
        val context = requireContext()
        val container = view.findViewById<LinearLayout>(R.id.LLWineDebugChannels)
        container.removeAllViews()

        val inflater = LayoutInflater.from(context)
        var itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false)
        itemView.findViewById<View>(R.id.TextView).visibility = View.GONE
        itemView.findViewById<View>(R.id.BTRemove).visibility = View.GONE

        val addButton = itemView.findViewById<View>(R.id.BTAdd)
        addButton.visibility = View.VISIBLE
        addButton.setOnClickListener {
            val jsonArray = runCatching {
                JSONArray(FileUtils.readString(context, "wine_debug_channels.json"))
            }.getOrNull()

            val items = ArrayUtils.toStringArray(jsonArray)
            showWineDebugChannelsDialog(context, items, debugChannels) { selectedChannels ->
                debugChannels.clear()
                debugChannels.addAll(selectedChannels)
                persistWineDebugChannels(debugChannels)
                loadWineDebugChannels(view, debugChannels)
            }
        }

        val resetButton = itemView.findViewById<View>(R.id.BTReset)
        resetButton.visibility = View.VISIBLE
        resetButton.setOnClickListener {
            debugChannels.clear()
            debugChannels.addAll(SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS.split(","))
            persistWineDebugChannels(debugChannels)
            loadWineDebugChannels(view, debugChannels)
        }
        container.addView(itemView)

        debugChannels.forEachIndexed { index, channel ->
            itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false)
            itemView.findViewById<TextView>(R.id.TextView).text = channel
            itemView.findViewById<View>(R.id.BTRemove).setOnClickListener {
                debugChannels.removeAt(index)
                persistWineDebugChannels(debugChannels)
                loadWineDebugChannels(view, debugChannels)
            }
            container.addView(itemView)
        }
    }

    private fun persistWineDebugChannels(debugChannels: List<String>) {
        preferences.edit {
            putString("wine_debug_channels", debugChannels.joinToString(","))
        }
    }

    private fun showWineDebugChannelsDialog(
        context: android.content.Context,
        items: Array<String>,
        selectedChannels: List<String>,
        onConfirm: (List<String>) -> Unit
    ) {
        val dialog = ContentDialog(context, R.layout.wine_debug_channel_dialog)
        dialog.setTitle(R.string.settings_debug_wine_debug_channel)
        shrinkWineDebugDialogChrome(dialog)

        val scrollView = dialog.findViewById<View>(R.id.SVDebugChannelGrid)
        scrollView.layoutParams.width = AppUtils.getPreferredDialogWidth(context)

        val grid = dialog.findViewById<GridLayout>(R.id.GLDebugChannelGrid)
        val inflater = LayoutInflater.from(context)
        val selectedIndices = items.mapIndexedNotNullTo(linkedSetOf()) { index, item ->
            index.takeIf { item in selectedChannels }
        }
        val horizontalSpacing = resources.getDimensionPixelSize(R.dimen.debug_channel_chip_spacing_horizontal)
        val verticalSpacing = resources.getDimensionPixelSize(R.dimen.debug_channel_chip_spacing_vertical)

        grid.removeAllViews()
        items.forEachIndexed { index, item ->
            val chip = inflater.inflate(
                R.layout.content_type_tab_item,
                grid,
                false
            ) as TextView
            chip.text = item
            chip.maxLines = 1
            chip.ellipsize = TextUtils.TruncateAt.END
            chip.isSelected = index in selectedIndices
            chip.setOnClickListener {
                if (!selectedIndices.add(index)) {
                    selectedIndices.remove(index)
                }
                chip.isSelected = index in selectedIndices
            }

            val layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(horizontalSpacing / 2, verticalSpacing / 2, horizontalSpacing / 2, verticalSpacing / 2)
            }
            chip.layoutParams = layoutParams
            grid.addView(chip)
        }

        dialog.setOnConfirmCallback {
            val result = items.filterIndexed { index, _ -> index in selectedIndices }
            onConfirm(result)
        }
        dialog.show()
    }

    private fun shrinkWineDebugDialogChrome(dialog: ContentDialog) {
        dialog.findViewById<TextView>(R.id.TVTitle)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)

        val compactHorizontalPadding = resources.getDimensionPixelSize(R.dimen.debug_channel_dialog_button_padding_horizontal)
        val compactVerticalPadding = resources.getDimensionPixelSize(R.dimen.debug_channel_dialog_button_padding_vertical)
        val compactMinWidth = resources.getDimensionPixelSize(R.dimen.debug_channel_dialog_button_min_width)

        listOf(R.id.BTCancel, R.id.BTConfirm).forEach { buttonId ->
            dialog.findViewById<Button>(buttonId)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minimumHeight = 0
                minHeight = 0
                minWidth = compactMinWidth
                setPadding(compactHorizontalPadding, compactVerticalPadding, compactHorizontalPadding, compactVerticalPadding)
                layoutParams = layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
        }
    }

    private fun updateWineChannelSection(section: View, enabled: Boolean) {
        setEnabledRecursive(section, enabled)
        section.alpha = if (enabled) 1f else 0.48f
    }

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setEnabledRecursive(view.getChildAt(index), enabled)
            }
        }
    }

    companion object {
        @Volatile
        var lastSharedLogFile: File? = null

        /** Call when starting a new game or after 3min timeout to clean up shared logs. */
        fun cleanupSharedLogs() {
            lastSharedLogFile?.let { file ->
                if (file.exists()) file.delete()
                lastSharedLogFile = null
            }
        }
    }
}
