package com.winlator.cmod.runtime.system
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.Closeable
import java.io.File

object LogManager {
    private const val TAG = "LogManager"
    private var logcatProcess: Process? = null
    private var appLogProcess: Process? = null

    @JvmStatic
    fun getLogsDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
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

    @JvmStatic
    fun rotateLogsOnAppStart(context: Context) {
        if (!isAnyLoggingEnabled(context)) return
        val logsDir = getLogsDir(context)
        logsDir.listFiles()?.filter { it.name.endsWith(".old.log") }?.forEach { it.delete() }
        // Rename current .log → .old.log
        logsDir.listFiles()?.filter { it.name.endsWith(".log") && !it.name.endsWith(".old.log") }?.forEach { file ->
            val oldName = file.name.replace(".log", ".old.log")
            file.renameTo(File(logsDir, oldName))
        }
    }

    @JvmStatic
    fun prepareForNewSession(context: Context) {
        val logsDir = getLogsDir(context)
        logsDir.listFiles()?.filter { it.name.endsWith(".old.log") }?.forEach { it.delete() }
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

    @JvmStatic
    fun startAppLogging(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("enable_app_debug", false)) return

        val logsDir = getLogsDir(context)
        val logFile = File(logsDir, "application.log")

        try {
            stopAppLogging()
            val pid = android.os.Process.myPid()
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

    @JvmStatic
    fun getShareableLogFiles(context: Context): Array<File> {
        val logsDir = getLogsDir(context)
        return logsDir
            .listFiles()
            ?.filter {
                it.isFile && (it.name.endsWith(".log") || it.name.endsWith(".old.log") || it.name.endsWith(".txt"))
            }?.toTypedArray() ?: emptyArray()
    }

    /** Total size in bytes of all shareable log files (the same set that Share Logs collects). */
    @JvmStatic
    fun getShareableLogsSize(context: Context): Long = getShareableLogFiles(context).sumOf { it.length() }

    /** Deletes all shareable log files. Returns the number of files removed. */
    @JvmStatic
    fun deleteShareableLogs(context: Context): Int = getShareableLogFiles(context).count { it.delete() }
}
