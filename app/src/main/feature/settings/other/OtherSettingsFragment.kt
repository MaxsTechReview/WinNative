/* Settings > Other fragment — hosts OtherSettingsScreen via ComposeView.
 * Mirrors the ScrollView wrapper used in StoresFragment / DebugFragment. */
package com.winlator.cmod.feature.settings
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.app.config.SettingsConfig
import com.winlator.cmod.app.update.UpdateChecker
import com.winlator.cmod.runtime.audio.midi.MidiManager
import com.winlator.cmod.runtime.display.environment.ImageFsInstaller
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.android.LocaleHelper
import com.winlator.cmod.shared.android.RefreshRateUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.ui.dialog.ContentDialog
import com.winlator.cmod.shared.ui.dialog.PreloaderDialog
import java.io.File

class OtherSettingsFragment : Fragment() {
    private lateinit var preferences: SharedPreferences
    private var uiState by mutableStateOf(OtherSettingsState())

    // Activity result launchers
    private val winlatorPathLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri ?: return@registerForActivityResult
            persistUriPermission(uri)
            preferences.edit { putString("winlator_path_uri", uri.toString()) }
            refresh()
        }

    private val shortcutExportPathLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri ?: return@registerForActivityResult
            persistUriPermission(uri)
            preferences.edit { putString("shortcuts_export_path_uri", uri.toString()) }
            refresh()
        }

    private val installSoundFontLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            installSoundFont(uri)
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.common_ui_other)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        refresh()

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
                        OtherSettingsScreen(
                            state = uiState,
                            onCheckForUpdatesChanged = { checked ->
                                preferences.edit { putBoolean("check_for_updates", checked) }
                                if (checked) {
                                    UpdateChecker.startBackgroundLoop(ctx)
                                } else {
                                    UpdateChecker.stopBackgroundLoop()
                                }
                                refresh()
                            },
                            onCheckForUpdatesNow = {
                                val started = UpdateChecker.checkForUpdateManual(ctx)
                                if (started) {
                                    AppUtils.showToast(ctx, R.string.settings_other_checking_for_updates)
                                } else {
                                    val seconds = UpdateChecker.manualCheckCooldownSeconds()
                                    AppUtils.showToast(
                                        ctx,
                                        getString(R.string.settings_other_update_check_cooldown, seconds),
                                    )
                                }
                            },
                            onLanguageSelected = { index ->
                                val currentIndex =
                                    LocaleHelper.indexForTag(
                                        LocaleHelper.getAppliedLanguageTag(),
                                    )
                                if (index != currentIndex) {
                                    LocaleHelper.applyLanguageTag(LocaleHelper.tagForIndex(index))
                                    // AppCompatDelegate recreates attached activities automatically.
                                }
                            },
                            onRefreshRateSelected = { index ->
                                val hz =
                                    RefreshRateUtils.parseRefreshRateLabel(
                                        uiState.refreshRateLabels.getOrNull(index),
                                    )
                                preferences.edit { putInt("refresh_rate_override", hz) }
                                if (isAdded) RefreshRateUtils.applyPreferredRefreshRate(requireActivity())
                                refresh()
                            },
                            onSoundFontSelected = { index ->
                                // Selection is display-only; no persistence in legacy code.
                                uiState = uiState.copy(soundFontIndex = index)
                            },
                            onInstallSoundFont = {
                                val intent =
                                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "*/*"
                                    }
                                installSoundFontLauncher.launch(intent)
                            },
                            onRemoveSoundFont = { removeSelectedSoundFont() },
                            onPickWinlatorPath = { winlatorPathLauncher.launch(null) },
                            onPickShortcutExportPath = { shortcutExportPathLauncher.launch(null) },
                            onCursorSpeedChanged = { percent ->
                                preferences.edit { putFloat("cursor_speed", percent / 100f) }
                                refresh()
                            },
                            onUseDRI3Changed = { checked ->
                                preferences.edit { putBoolean("use_dri3", checked) }
                                refresh()
                            },
                            onCursorLockChanged = { checked ->
                                preferences.edit { putBoolean("cursor_lock", checked) }
                                refresh()
                            },
                            onXinputDisabledChanged = { checked ->
                                preferences.edit { putBoolean("xinput_toggle", checked) }
                                refresh()
                            },
                            onEnableFileProviderChanged = { checked ->
                                preferences.edit { putBoolean("enable_file_provider", checked) }
                                AppUtils.showToast(ctx, R.string.settings_general_take_effect_next_startup)
                                refresh()
                            },
                            onOpenInBrowserChanged = { checked ->
                                preferences.edit { putBoolean("open_with_android_browser", checked) }
                                refresh()
                            },
                            onShareClipboardChanged = { checked ->
                                preferences.edit { putBoolean("share_android_clipboard", checked) }
                                refresh()
                            },
                            onReinstallImagefs = { startImagefsReinstall() },
                        )
                    }
                }
            }

        // View-level ScrollView gives Compose a bounded height, mirroring StoresFragment.
        // Do NOT add fillMaxSize/verticalScroll inside OtherSettingsScreen while this is here.
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
            setBackgroundColor(android.graphics.Color.parseColor("#0F0F12"))
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

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Helpers
    private fun refresh() {
        val ctx = context ?: return

        // Language entries: "System default" + native names, and currently-applied index
        val languageLabels =
            buildList {
                add(getString(R.string.settings_other_language_system_default))
                addAll(LocaleHelper.NATIVE_LANGUAGE_NAMES)
            }
        val languageIndex = LocaleHelper.indexForTag(LocaleHelper.getAppliedLanguageTag())

        // Refresh rate entries + saved selection
        val refreshRateLabels =
            RefreshRateUtils.buildRefreshRateEntryLabels(
                requireActivity(),
                getString(R.string.settings_general_refresh_rate_auto_max),
            )
        val savedRate = preferences.getInt("refresh_rate_override", 0)
        val refreshRateIndex =
            if (savedRate == 0) {
                0
            } else {
                val target = "$savedRate Hz"
                refreshRateLabels.indexOfFirst { it == target }.coerceAtLeast(0)
            }

        // Sound font files
        val soundFontFiles = loadSoundFontFiles(ctx)
        val soundFontIndex = uiState.soundFontIndex.coerceIn(0, (soundFontFiles.size - 1).coerceAtLeast(0))

        // Paths
        val winlatorPath =
            resolvePathString(
                preferences.getString("winlator_path_uri", null),
                SettingsConfig.DEFAULT_WINLATOR_PATH,
                ctx,
            )
        val shortcutExportPath =
            resolvePathString(
                preferences.getString("shortcuts_export_path_uri", null),
                SettingsConfig.DEFAULT_SHORTCUT_EXPORT_PATH,
                ctx,
            )

        uiState =
            OtherSettingsState(
                checkForUpdates = preferences.getBoolean("check_for_updates", true),
                languageLabels = languageLabels,
                languageIndex = languageIndex,
                refreshRateLabels = refreshRateLabels,
                refreshRateIndex = refreshRateIndex,
                soundFontFiles = soundFontFiles,
                soundFontIndex = soundFontIndex,
                winlatorPath = winlatorPath,
                shortcutExportPath = shortcutExportPath,
                cursorSpeedPercent =
                    (preferences.getFloat("cursor_speed", 1.0f) * 100)
                        .toInt()
                        .coerceIn(10, 200),
                useDRI3 = preferences.getBoolean("use_dri3", true),
                cursorLock = preferences.getBoolean("cursor_lock", false),
                xinputDisabled = preferences.getBoolean("xinput_toggle", false),
                enableFileProvider = preferences.getBoolean("enable_file_provider", true),
                openInBrowser = preferences.getBoolean("open_with_android_browser", false),
                shareClipboard = preferences.getBoolean("share_android_clipboard", false),
                imagefsInstallProgress = uiState.imagefsInstallProgress,
            )
    }

    private fun loadSoundFontFiles(ctx: Context): List<String> {
        val files = mutableListOf(MidiManager.DEFAULT_SF2_FILE)
        MidiManager.getSoundFontDir(ctx).listFiles()?.forEach { file ->
            if (file.isFile && file.name != MidiManager.DEFAULT_SF2_FILE) {
                files += file.name
            }
        }
        return files
    }

    private fun resolvePathString(
        uriStr: String?,
        fallback: String,
        ctx: Context,
    ): String {
        if (uriStr.isNullOrEmpty()) return fallback
        return try {
            val uri = Uri.parse(uriStr)
            FileUtils.getFilePathFromUri(ctx, uri) ?: uriStr
        } catch (_: Exception) {
            uriStr
        }
    }

    private fun persistUriPermission(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            AppUtils.showToast(
                context,
                getString(R.string.settings_other_persistable_permission_failed, e.message ?: ""),
            )
        }
    }

    private fun startImagefsReinstall() {
        val act = activity ?: return
        // Seed the dialog immediately at 0% so the Compose progress dialog shows up.
        uiState = uiState.copy(imagefsInstallProgress = 0)
        val main = Handler(Looper.getMainLooper())
        ImageFsInstaller.installFromAssets(
            act,
            object : ImageFsInstaller.ProgressListener {
                override fun onProgress(percent: Int) {
                    main.post {
                        uiState = uiState.copy(imagefsInstallProgress = percent.coerceIn(0, 100))
                    }
                }

                override fun onFinished(success: Boolean) {
                    main.post {
                        uiState = uiState.copy(imagefsInstallProgress = null)
                    }
                }
            },
        )
    }

    private fun installSoundFont(uri: Uri) {
        val ctx = context ?: return
        val dialog = PreloaderDialog(requireActivity())
        dialog.showOnUiThread(R.string.settings_audio_installing_soundfont)
        MidiManager.installSF2File(
            ctx,
            uri,
            object : MidiManager.OnSoundFontInstalledCallback {
                override fun onSuccess() {
                    dialog.closeOnUiThread()
                    requireActivity().runOnUiThread {
                        ContentDialog.alert(ctx, R.string.settings_audio_sound_font_installed_success, null)
                        refresh()
                    }
                }

                override fun onFailed(reason: Int) {
                    dialog.closeOnUiThread()
                    val resId =
                        when (reason) {
                            MidiManager.ERROR_BADFORMAT -> R.string.settings_audio_sound_font_bad_format
                            MidiManager.ERROR_EXIST -> R.string.settings_audio_sound_font_already_exist
                            else -> R.string.settings_audio_sound_font_installed_failed
                        }
                    requireActivity().runOnUiThread {
                        ContentDialog.alert(ctx, resId, null)
                    }
                }
            },
        )
    }

    private fun removeSelectedSoundFont() {
        val ctx = context ?: return
        val idx = uiState.soundFontIndex
        if (idx == 0) {
            AppUtils.showToast(ctx, R.string.settings_audio_cannot_remove_default)
            return
        }
        val fileName = uiState.soundFontFiles.getOrNull(idx) ?: return
        ContentDialog.confirm(ctx, R.string.settings_audio_confirm_remove_sound_font) {
            if (MidiManager.removeSF2File(ctx, fileName)) {
                AppUtils.showToast(ctx, R.string.settings_audio_sound_font_removed_success)
                uiState = uiState.copy(soundFontIndex = 0)
                refresh()
            } else {
                AppUtils.showToast(ctx, R.string.settings_audio_sound_font_removed_failed)
            }
        }
    }

    companion object {
        /**
         * Build refresh rate entries for per-game shortcut spinners.
         * Includes "Default (Global)" as the first entry.
         */
        @JvmStatic
        fun buildRefreshRateEntries(activity: Activity): List<String> =
            RefreshRateUtils.buildRefreshRateEntryLabels(
                activity,
                activity.getString(R.string.container_config_refresh_rate_default_global),
            )
    }
}
