package com.winlator.cmod.feature.configs

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.system.LogManager
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helpers for the Export / Import buttons in the shortcut settings dialog.
 *
 * Export targets:
 *  - File: writes a JSON file to /sdcard/WinNative/configs/<slug>_<ts>.json
 *  - Community: uploads the same payload to Supabase via [ConfigRepository]
 *
 * Import targets:
 *  - File: caller opens an Android SAF picker; pass the picked Uri's contents back here
 *  - Community: caller navigates to BestConfigsScreen, which has its own import flow
 *
 * This module deliberately avoids holding Activity references — file IO is on the
 * caller's chosen dispatcher; the Supabase upload is suspending and runs on IO.
 */
object ConfigExportImport {
    private const val TAG = "ConfigExportImport"
    private const val EXPORT_SUBDIR = "configs"

    /** Returns the per-app exports directory, creating it if missing. */
    fun exportsDir(context: Context): File {
        val parent = LogManager.getLogsDir(context).parentFile
            ?: File(android.os.Environment.getExternalStorageDirectory(), "WinNative")
        val dir = File(parent, EXPORT_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Write the exported config JSON to a local file. Returns the file written.
     * The filename is `perf_<sanitized-game-name>_<yyyymmdd-HHmmss>.json`.
     */
    @Throws(Exception::class)
    fun exportToFile(context: Context, container: Container, shortcut: Shortcut): File {
        val json = ConfigSerializer.exportToJson(container, shortcut)
        val dir = exportsDir(context)
        val slug = sanitizeSlug(shortcut.name ?: "config")
        val ts = TIMESTAMP_FMT.format(Date())
        var file = File(dir, "config_${slug}_${ts}.json")
        var n = 1
        while (file.exists()) {
            file = File(dir, "config_${slug}_${ts}_$n.json")
            n++
        }
        file.writeText(json.toString(2), Charsets.UTF_8)
        Timber.tag(TAG).i("Exported config to ${file.absolutePath}")
        return file
    }

    /**
     * Upload the exported config to the community Supabase board.
     * Returns the new row id on success. Suspending; caller should be on Dispatchers.IO.
     */
    /** Maximum length of the user-supplied [customName] passed to [shareToCommunity]. */
    const val CUSTOM_NAME_MAX_LEN: Int = 25

    suspend fun shareToCommunity(
        context: Context,
        container: Container,
        shortcut: Shortcut,
        repository: ConfigRepository,
        customName: String? = null,
        notes: String? = null,
        perfSummary: JSONObject? = null,
    ): Result<String> {
        val config = ConfigSerializer.exportToJson(container, shortcut)
        val gameSource = shortcut.getExtra("game_source")?.takeIf { it.isNotBlank() } ?: "CUSTOM_GAME"
        val gameId = ConfigSerializer.gameIdForShortcut(shortcut, gameSource)
            ?: shortcut.name ?: "unknown"
        val gameName = shortcut.name ?: "Untitled"
        // glGetString(GL_RENDERER) only works on a thread with a current GLES context.
        // The settings dialog has none, so falling back to that produced empty values
        // and broke the "My GPU" filter on the Best Configs screen. GpuDetector reads
        // sysfs + Build properties — context-free, returns a usable model name.
        val gpuRenderer = GpuDetector.detect()
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val input = ConfigRepository.UploadConfigInput(
            gameSource = gameSource,
            gameId = gameId,
            gameName = gameName,
            customName = customName?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(CUSTOM_NAME_MAX_LEN),
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            socModel = socModel,
            gpuRenderer = gpuRenderer,
            androidRelease = Build.VERSION.RELEASE,
            androidApi = Build.VERSION.SDK_INT,
            appVersion = appVersion,
            configJson = config,
            perfSummary = perfSummary,
            notes = notes,
            includesSteamSubtab = gameSource == "STEAM",
        )
        return repository.uploadConfig(input)
    }

    /**
     * Read a file's contents (UTF-8) and apply it as a config to the target shortcut.
     * Caller is responsible for opening the SAF picker and getting the file bytes.
     */
    fun applyFromJsonString(
        json: String,
        container: Container,
        shortcut: Shortcut,
    ): ConfigSerializer.ApplyResult {
        val obj = JSONObject(json)
        return ConfigSerializer.applyToShortcut(obj, container, shortcut)
    }

    private fun sanitizeSlug(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifEmpty { "unknown" }

    private val TIMESTAMP_FMT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
}
