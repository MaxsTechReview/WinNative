package com.winlator.cmod.runtime.system
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import com.winlator.cmod.app.config.SettingsConfig
import com.winlator.cmod.shared.io.FileUtils
import java.io.Closeable
import java.io.File

object LogManager {
    private const val TAG = "LogManager"
    private var logcatProcess: Process? = null
    private var appLogProcess: Process? = null

    /**
     * Returns the external logs directory: /sdcard/WinNative/logs
     * Falls back to cache dir if external storage is unavailable.
     */
    @JvmStatic
    fun getLogsDir(context: Context): File {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val customUri = prefs.getString("winlator_path_uri", null)
        val baseDir =
            if (customUri != null) {
                val customPath = FileUtils.getFilePathFromUri(context, android.net.Uri.parse(customUri))
                if (customPath != null) File(customPath) else File(SettingsConfig.DEFAULT_WINLATOR_PATH)
            } else {
                File(SettingsConfig.DEFAULT_WINLATOR_PATH)
            }
        val dir = File(baseDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isAnyLoggingEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean("enable_wine_debug", false) ||
            prefs.getBoolean("enable_box64_logs", false) ||
            prefs.getBoolean("enable_fexcore_logs", false) ||
            prefs.getBoolean("enable_steam_logs", false) ||
            prefs.getBoolean("enable_input_logs", false) ||
            prefs.getBoolean("enable_download_logs", false) ||
            prefs.getBoolean("enable_app_debug", false)
    }

    fun updateLoggingState(context: Context) {
        if (!isAnyLoggingEnabled(context)) {
            stopLogging()
        }
    }

    // ── Log Rotation ──────────────────────────────────────────────────

    /**
     * Call when the app starts fresh (not just returning from a game).
     * Renames all `.log` files to `.old.log` so the previous session's
     * logs are preserved until the next full launch.
     */
    @JvmStatic
    fun rotateLogsOnAppStart(context: Context) {
        val logsDir = getLogsDir(context)
        // Delete any existing .old.log files first
        logsDir.listFiles()?.filter { it.name.endsWith(".old.log") }?.forEach { it.delete() }
        // Rename current .log → .old.log
        logsDir.listFiles()?.filter { it.name.endsWith(".log") && !it.name.endsWith(".old.log") }?.forEach { file ->
            val oldName = file.name.replace(".log", ".old.log")
            file.renameTo(File(logsDir, oldName))
        }
    }

    /**
     * Call when starting a new game/container session.
     * Deletes ALL old logs (.old.log) and clears current .log files
     * so fresh logs are captured for this run.
     */
    @JvmStatic
    fun prepareForNewSession(context: Context) {
        val logsDir = getLogsDir(context)
        // Remove .old.log files
        logsDir.listFiles()?.filter { it.name.endsWith(".old.log") }?.forEach { it.delete() }
        // Remove current .log files (new session will create fresh ones)
        logsDir.listFiles()?.filter { it.name.endsWith(".log") }?.forEach { it.delete() }
    }

    // ── Wine/Box64 Logcat Capture ────────────────────────────────────

    fun startLogging(context: Context) {
        if (!isAnyLoggingEnabled(context)) {
            stopLogging()
            return
        }

        val logsDir = getLogsDir(context)
        val logFile = File(logsDir, "logcat.log")

        try {
            stopLogcat()
            runBlockingLogcatCommand(arrayOf("logcat", "-c"))
            logcatProcess =
                Runtime.getRuntime().exec(
                    arrayOf("logcat", "-f", logFile.absolutePath, "*:D"),
                )
            closeProcessStdin(logcatProcess)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logcat: ${e.message}")
        }
    }

    fun stopLogging() {
        stopLogcat()
        stopAppLogging()
    }

    private fun stopLogcat() {
        try {
            logcatProcess?.let(::destroyProcess)
            logcatProcess = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop logcat: ${e.message}")
        }
    }

    fun clearLogs(context: Context) {
        val logsDir = getLogsDir(context)
        logsDir.listFiles()?.forEach { it.delete() }
    }

    // ── Application Debug (PID logcat) ───────────────────────────────

    /**
     * Starts capturing logs for the WinNative application process (by PID).
     * Writes to `application.log` in real-time so that crash data is
     * persisted even if the process terminates unexpectedly.
     */
    @JvmStatic
    fun startAppLogging(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("enable_app_debug", false)) return

        val logsDir = getLogsDir(context)
        val logFile = File(logsDir, "application.log")

        try {
            stopAppLogging()
            val pid = android.os.Process.myPid()
            // Filter by PID and capture warnings, errors, and fatal messages
            appLogProcess =
                Runtime.getRuntime().exec(
                    arrayOf("logcat", "-f", logFile.absolutePath, "--pid=$pid", "*:W"),
                )
            closeProcessStdin(appLogProcess)
            Log.i(TAG, "Application debug logging started (PID=$pid)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start application logging: ${e.message}")
        }
    }

    @JvmStatic
    fun stopAppLogging() {
        try {
            appLogProcess?.let(::destroyProcess)
            appLogProcess = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop application logging: ${e.message}")
        }
    }

    private fun runBlockingLogcatCommand(command: Array<String>) {
        val process = Runtime.getRuntime().exec(command)
        try {
            process.waitFor()
        } finally {
            destroyProcess(process)
        }
    }

    private fun destroyProcess(process: Process) {
        closeProcessStdin(process)
        closeQuietly(process.inputStream)
        closeQuietly(process.errorStream)
        process.destroy()
    }

    private fun closeProcessStdin(process: Process?) {
        closeQuietly(process?.outputStream)
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }

    /**
     * Collects all shareable log files (both .log and .old.log).
     */
    @JvmStatic
    fun getShareableLogFiles(context: Context): Array<File> {
        val logsDir = getLogsDir(context)
        return logsDir
            .listFiles()
            ?.filter {
                it.isFile && (it.name.endsWith(".log") || it.name.endsWith(".old.log") || it.name.endsWith(".txt"))
            }?.toTypedArray() ?: emptyArray()
    }
}
