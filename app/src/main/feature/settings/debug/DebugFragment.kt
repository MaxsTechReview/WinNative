// Settings > Debug fragment — hosts DebugScreen via ComposeView.
package com.winlator.cmod.feature.settings
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.app.config.SettingsConfig
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.io.AssetPaths
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.util.ArrayUtils
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DebugFragment : Fragment() {
    private lateinit var preferences: SharedPreferences
    private var debugState by mutableStateOf(DebugState())
    private var wineChannelOptions: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        wineChannelOptions = loadWineChannelOptions(ctx)
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
                        DebugScreen(
                            state = debugState,
                            wineChannelOptions = wineChannelOptions,
                            onAppDebugChanged = { checked ->
                                preferences.edit { putBoolean("enable_app_debug", checked) }
                                if (checked) {
                                    com.winlator.cmod.runtime.system.LogManager
                                        .startAppLogging(ctx)
                                } else {
                                    com.winlator.cmod.runtime.system.LogManager
                                        .stopAppLogging()
                                    com.winlator.cmod.runtime.system.LogManager
                                        .updateLoggingState(ctx)
                                }
                                refresh()
                            },
                            onWineDebugChanged = { checked ->
                                preferences.edit { putBoolean("enable_wine_debug", checked) }
                                com.winlator.cmod.runtime.system.LogManager
                                    .updateLoggingState(ctx)
                                refresh()
                            },
                            onWineChannelsChanged = { channels ->
                                preferences.edit { putString("wine_debug_channels", channels.joinToString(",")) }
                                refresh()
                            },
                            onResetWineChannels = {
                                val defaults =
                                    SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS
                                        .split(",")
                                        .filter { it.isNotBlank() }
                                preferences.edit { putString("wine_debug_channels", defaults.joinToString(",")) }
                                refresh()
                            },
                            onRemoveWineChannel = { channel ->
                                val remaining = debugState.wineChannels.filterNot { it == channel }
                                preferences.edit { putString("wine_debug_channels", remaining.joinToString(",")) }
                                refresh()
                            },
                            onBox64LogsChanged = { checked ->
                                preferences.edit { putBoolean("enable_box64_logs", checked) }
                                com.winlator.cmod.runtime.system.LogManager
                                    .updateLoggingState(ctx)
                                refresh()
                            },
                            onFexcoreLogsChanged = { checked ->
                                preferences.edit { putBoolean("enable_fexcore_logs", checked) }
                                com.winlator.cmod.runtime.system.LogManager
                                    .updateLoggingState(ctx)
                                refresh()
                            },
                            onSteamLogsChanged = { checked ->
                                com.winlator.cmod.feature.stores.steam.utils.PrefManager.enableSteamLogs = checked
                                if (checked &&
                                    timber.log.Timber
                                        .forest()
                                        .isEmpty()
                                ) {
                                    timber.log.Timber.plant(timber.log.Timber.DebugTree())
                                }
                                com.winlator.cmod.runtime.system.LogManager
                                    .updateLoggingState(ctx)
                                refresh()
                            },
                            onInputLogsChanged = { checked ->
                                preferences.edit { putBoolean("enable_input_logs", checked) }
                                com.winlator.cmod.runtime.system.LogManager
                                    .updateLoggingState(ctx)
                                refresh()
                            },
                            onDownloadLogsChanged = { checked ->
                                preferences.edit { putBoolean("enable_download_logs", checked) }
                                com.winlator.cmod.runtime.system.LogManager
                                    .updateLoggingState(ctx)
                                refresh()
                            },
                            onShareLogs = { shareLogs() },
                        )
                    }
                }
            }

        // View-level ScrollView gives Compose a bounded height, mirroring StoresFragment.
        // Do NOT add fillMaxSize/verticalScroll inside DebugScreen while this is here.
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

    private fun refresh() {
        val channels =
            preferences
                .getString(
                    "wine_debug_channels",
                    SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS,
                )?.split(",")
                ?.filter { it.isNotBlank() } ?: emptyList()
        debugState =
            DebugState(
                appDebug = preferences.getBoolean("enable_app_debug", false),
                wineDebug = preferences.getBoolean("enable_wine_debug", false),
                wineChannels = channels,
                box64Logs = preferences.getBoolean("enable_box64_logs", false),
                fexcoreLogs = preferences.getBoolean("enable_fexcore_logs", false),
                steamLogs = com.winlator.cmod.feature.stores.steam.utils.PrefManager.enableSteamLogs,
                inputLogs = preferences.getBoolean("enable_input_logs", false),
                downloadLogs = preferences.getBoolean("enable_download_logs", false),
            )
    }

    private fun loadWineChannelOptions(ctx: android.content.Context): List<String> {
        val jsonArray =
            runCatching {
                JSONArray(FileUtils.readString(ctx, AssetPaths.WINE_DEBUG_CHANNELS))
            }.getOrNull() ?: return emptyList()
        return ArrayUtils.toStringArray(jsonArray).toList()
    }

    private fun shareLogs() {
        val ctx = requireContext()
        val files =
            com.winlator.cmod.runtime.system.LogManager
                .getShareableLogFiles(ctx)

        if (files.isEmpty()) {
            AppUtils.showToast(ctx, R.string.settings_debug_no_logs_available)
            return
        }

        try {
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val zipFile = File(ctx.cacheDir, "winnative_logs_$timestamp.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { file ->
                    if (file.isFile) {
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            lastSharedLogFile = zipFile

            Handler(Looper.getMainLooper()).postDelayed({
                if (zipFile.exists() && lastSharedLogFile == zipFile) {
                    zipFile.delete()
                    if (lastSharedLogFile == zipFile) lastSharedLogFile = null
                }
            }, 3 * 60 * 1000)

            val authority = "${ctx.packageName}.tileprovider"
            val uri = FileProvider.getUriForFile(ctx, authority, zipFile)
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_debug_logs_subject, timestamp))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_debug_share_logs)))

            Handler(Looper.getMainLooper()).postDelayed({ cleanupSharedLogs() }, 3 * 60 * 1000L)
        } catch (e: Exception) {
            AppUtils.showToast(ctx, getString(R.string.settings_debug_capture_failed, e.message ?: ""))
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
