package com.winlator.cmod.steam

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.TarCompressorUtils
import com.winlator.cmod.xenvironment.ImageFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

/**
 * Manages Steam client download, extraction, and Steamless DRM patching.
 */
object SteamClientManager {
    private const val TAG = "SteamClientManager"
    private const val COMPONENTS_BASE_URL = "https://github.com/maxjivi05/Components/releases/download/Components"

    interface DownloadProgressListener {
        fun onProgress(progress: Float)
        fun onComplete(success: Boolean, error: String?)
    }

    interface ShellCommandRunner {
        fun exec(command: String): String
    }

    @JvmStatic
    fun isSteamDownloaded(context: Context): Boolean {
        val steamFile = File(context.filesDir, "steam.tzst")
        return steamFile.exists() && steamFile.length() > 0
    }

    @JvmStatic
    fun isSteamInstalled(context: Context): Boolean {
        val imageFs = ImageFs.find(context)
        val steamExe = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/steam.exe")
        val steamClient = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/steamclient.dll")
        val steamClient64 = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/steamclient64.dll")
        return steamExe.exists() && steamClient.exists() && steamClient64.exists()
    }

    @JvmStatic
    fun isColdClientInstalled(context: Context): Boolean {
        val imageFs = ImageFs.find(context)
        val loaderExe = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/steamclient_loader_x64.exe")
        val stubDrm = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/extra_dlls/StubDRM64.dll")
        return loaderExe.exists() && loaderExe.length() > 0 && stubDrm.exists() && stubDrm.length() > 0
    }

    @JvmStatic
    fun downloadSteam(context: Context, listener: DownloadProgressListener?) {
        thread(name = "SteamDownloader") {
            val dest = File(context.filesDir, "steam.tzst")
            val tmp = File("${dest.absolutePath}.part")
            var success = false
            var error: String? = null

            val urls = downloadUrlsFor("steam.tzst")
            for (urlStr in urls) {
                try {
                    Log.d(TAG, "Attempting download from: $urlStr")
                    downloadFile(urlStr, tmp, listener)

                    if (tmp.exists() && tmp.length() > 0) {
                        if (!tmp.renameTo(dest)) {
                            Files.copy(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            tmp.delete()
                        }
                        success = true
                        Log.d(TAG, "Steam download completed: ${dest.length()} bytes")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Download failed from $urlStr: ${e.message}")
                    error = e.message
                    tmp.delete()
                }
            }

            if (!success) {
                val finalError = error ?: "All download sources failed"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Steam download failed: $finalError. Try disabling VPN.", Toast.LENGTH_LONG).show()
                }
            }

            listener?.let { l ->
                Handler(Looper.getMainLooper()).post {
                    l.onComplete(success, error)
                }
            }
        }
    }

    private fun downloadFile(urlStr: String, dest: File, listener: DownloadProgressListener?) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode")
            }

            val total = conn.contentLength.toLong()
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } >= 0) {
                        output.write(buf, 0, bytesRead)
                        downloaded += bytesRead
                        if (listener != null && total > 0) {
                            val progress = downloaded.toFloat() / total
                            Handler(Looper.getMainLooper()).post { listener.onProgress(progress) }
                        }
                    }
                }
            }

            if (total > 0 && dest.length() != total) {
                dest.delete()
                throw Exception("Incomplete download: ${dest.length()}/$total")
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadUrlsFor(fileName: String): Array<String> {
        val alternate = when (fileName) {
            "steam-token.tzst" -> "steam-token-r2.tzst"
            "experimental-drm-20260116.tzst" -> "experimental-drm-20260116-r2.tzst"
            else -> null
        }
        return if (alternate != null) {
            arrayOf(
                "$COMPONENTS_BASE_URL/$fileName",
                "$COMPONENTS_BASE_URL/$alternate",
            )
        } else {
            arrayOf("$COMPONENTS_BASE_URL/$fileName")
        }
    }

    private fun ensureArchiveReady(context: Context, fileName: String, failureMessage: String): Boolean {
        val dest = File(context.filesDir, fileName)
        if (dest.exists() && dest.length() > 0) return true

        val tmp = File("${dest.absolutePath}.part")
        for (urlStr in downloadUrlsFor(fileName)) {
            try {
                Log.d(TAG, "Downloading $fileName from: $urlStr")
                downloadFile(urlStr, tmp, null)
                if (tmp.exists() && tmp.length() > 0) {
                    if (!tmp.renameTo(dest)) {
                        Files.copy(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        tmp.delete()
                    }
                    Log.d(TAG, "Download completed for $fileName: ${dest.length()} bytes")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download failed from $urlStr: ${e.message}")
                tmp.delete()
            }
        }

        Log.e(TAG, "Failed to download $fileName from all sources")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
        }
        return false
    }

    @JvmStatic
    fun extractSteam(context: Context): Boolean {
        if (isSteamInstalled(context)) return true

        val steamFile = File(context.filesDir, "steam.tzst")
        if (!steamFile.exists()) return false

        val imageFs = ImageFs.find(context)
        return try {
            TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                steamFile,
                imageFs.rootDir,
                null
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract steam archive: ${e.message}")
            false
        }
    }

    @JvmStatic
    fun forceExtractSteam(context: Context): Boolean {
        val steamFile = File(context.filesDir, "steam.tzst")
        if (!steamFile.exists()) return false

        val imageFs = ImageFs.find(context)
        return try {
            TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                steamFile,
                imageFs.rootDir,
                null
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force extract steam archive: ${e.message}")
            false
        }
    }

    @JvmStatic
    fun extractColdClientSupport(context: Context): Boolean {
        if (isColdClientInstalled(context)) return true

        val expFile = File(context.filesDir, "experimental-drm-20260116.tzst")
        if (!expFile.exists()) return false

        val imageFs = ImageFs.find(context)
        return try {
            TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                expFile,
                imageFs.rootDir,
                null
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ColdClient support archive: ${e.message}")
            false
        }
    }

    /**
     * Ensures Steam client files are ready: downloads steam.tzst if missing,
     * then extracts it. This is a blocking call and should be run from a worker thread.
     * @return true if Steam client is ready to use
     */
    @JvmStatic
    fun ensureSteamReady(context: Context): Boolean {
        // Already installed?
        if (isSteamInstalled(context)) {
            Log.d(TAG, "Steam client already installed")
            return true
        }

        // Need to download?
        if (!isSteamDownloaded(context)) {
            Log.d(TAG, "Steam files not found, downloading...")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Downloading Steam client...", Toast.LENGTH_SHORT).show()
            }

            if (!ensureArchiveReady(context, "steam.tzst", "Failed to download Steam client")) {
                return false
            }
        }

        // Extract
        Log.d(TAG, "Extracting steam files...")
        val success = extractSteam(context)
        if (success) {
            Log.d(TAG, "Steam client extracted successfully")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Steam client ready", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Failed to extract steam.tzst")
        }
        return success
    }

    @JvmStatic
    fun ensureRealSteamSupportReady(context: Context): Boolean {
        return ensureArchiveReady(context, "steam-token.tzst", "Failed to download Steam token helper")
    }

    @JvmStatic
    fun ensureColdClientSupportReady(context: Context): Boolean {
        if (!ensureArchiveReady(context, "experimental-drm-20260116.tzst", "Failed to download Steam DRM support")) {
            return false
        }
        return extractColdClientSupport(context)
    }

    @JvmStatic
    fun ensureSteamlessSupportReady(context: Context): Boolean {
        val rootDir = ImageFs.find(context).rootDir
        val steamlessCli = File(rootDir, "Steamless/Steamless.CLI.exe")
        val generateInterfacesExe = File(rootDir, "generate_interfaces_file.exe")

        if (!steamlessCli.exists() || !generateInterfacesExe.exists()) {
            try {
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD,
                    context,
                    "extras.tzst",
                    rootDir
                )
                chmodIfExists(generateInterfacesExe)
                chmodIfExists(steamlessCli)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract Steamless support assets", e)
                return false
            }
        }

        // Ensure the correct Mono MSI is available (downloads if needed)
        val monoMsi = ensureMonoMsi(context)
        if (monoMsi == null) {
            Log.w(TAG, "Mono MSI not available; Steamless may fail if .NET is needed")
        }

        return steamlessCli.exists() && generateInterfacesExe.exists()
    }

    @JvmStatic
    fun runSteamless(context: Context, exePath: String, shellRunner: ShellCommandRunner): Boolean {
        val rootDir = ImageFs.find(context).rootDir
        val steamlessCli = File(rootDir, "Steamless/Steamless.CLI.exe")
        if (!steamlessCli.exists()) return false

        var batchFile: File? = null
        try {
            val normalizedPath = exePath.replace('/', '\\')
            val windowsPath = "A:\\$normalizedPath"

            batchFile = File(rootDir, "tmp/steamless_wrapper.bat")
            batchFile.parentFile?.mkdirs()
            FileUtils.writeString(batchFile, "@echo off\r\nz:\\Steamless\\Steamless.CLI.exe \"$windowsPath\"\r\n")

            val command = "wine z:\\tmp\\steamless_wrapper.bat"
            Log.d(TAG, "Steamless output: ${shellRunner.exec(command)}")

            val unixPath = exePath.replace('\\', '/')
            val wineprefix = File(rootDir, ImageFs.WINEPREFIX)
            val exe = File(wineprefix, "dosdevices/a:/$unixPath")
            val unpackedExe = File(wineprefix, "dosdevices/a:/$unixPath.unpacked.exe")
            val originalExe = File(wineprefix, "dosdevices/a:/$unixPath.original.exe")

            if (exe.exists() && unpackedExe.exists()) {
                if (!originalExe.exists()) {
                    Files.copy(exe.toPath(), originalExe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                Files.copy(unpackedExe.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error running Steamless", e)
            return false
        } finally {
            batchFile?.delete()
        }
    }

    private fun chmodIfExists(file: File) {
        if (file.exists()) {
            FileUtils.chmod(file, 493)
        }
    }

    /**
     * Detects the Mono version expected by the current Wine build by scanning
     * mscoree.dll for the version string pattern (e.g. "9.3.0", "10.4.1").
     * Returns null if the version cannot be determined.
     */
    @JvmStatic
    fun detectRequiredMonoVersion(context: Context): String? {
        val imageFs = ImageFs.find(context)
        val winePath = imageFs.winePath ?: return null

        // Try x86_64-windows first (64-bit Wine), then i386-windows (32-bit)
        val candidates = listOf(
            File(winePath, "lib/wine/x86_64-windows/mscoree.dll"),
            File(winePath, "lib/wine/aarch64-windows/mscoree.dll"),
            File(winePath, "lib/wine/i386-windows/mscoree.dll")
        )

        val mscoree = candidates.firstOrNull { it.exists() } ?: run {
            Log.w(TAG, "mscoree.dll not found in Wine build at $winePath")
            return null
        }

        return try {
            // Read the DLL binary and search for version pattern "wine-mono-X.Y.Z"
            val bytes = mscoree.readBytes()
            val content = String(bytes, Charsets.ISO_8859_1)
            val pattern = Regex("wine-mono-(\\d+\\.\\d+\\.\\d+)")
            val match = pattern.find(content)
            if (match != null) {
                val version = match.groupValues[1]
                Log.i(TAG, "Detected required Mono version: $version from ${mscoree.path}")
                version
            } else {
                Log.w(TAG, "Could not find Mono version string in ${mscoree.path}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading mscoree.dll", e)
            null
        }
    }

    /**
     * Returns the path to the correct Mono MSI for the current Wine build.
     * Downloads it from dl.winehq.org if not already present.
     * Returns null if version detection fails or download fails.
     */
    @JvmStatic
    fun ensureMonoMsi(context: Context): File? {
        val version = detectRequiredMonoVersion(context)
        if (version == null) {
            Log.w(TAG, "Cannot detect Mono version, falling back to bundled MSI")
            // Fall back to whatever MSI exists in the mono directory
            val monoDir = File(ImageFs.find(context).rootDir, "opt/mono-gecko-offline")
            val existing = monoDir.listFiles()?.firstOrNull {
                it.name.startsWith("wine-mono-") && it.name.endsWith("-x86.msi")
            }
            return existing
        }

        val msiName = "wine-mono-$version-x86.msi"
        val monoDir = File(ImageFs.find(context).rootDir, "opt/mono-gecko-offline")
        monoDir.mkdirs()
        val msiFile = File(monoDir, msiName)

        if (msiFile.exists() && msiFile.length() > 0) {
            Log.d(TAG, "Mono MSI already present: ${msiFile.path}")
            chmodIfExists(msiFile)
            return msiFile
        }

        // Clean up any old Mono MSIs
        monoDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("wine-mono-") && f.name.endsWith("-x86.msi") && f.name != msiName) {
                Log.i(TAG, "Removing outdated Mono MSI: ${f.name}")
                f.delete()
            }
        }

        // Download the correct version
        val downloadUrl = "https://dl.winehq.org/wine/wine-mono/$version/$msiName"
        Log.i(TAG, "Downloading Mono $version from $downloadUrl")

        return try {
            val tmpFile = File(monoDir, "$msiName.tmp")
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true

            if (connection.responseCode != 200) {
                Log.e(TAG, "Failed to download Mono MSI: HTTP ${connection.responseCode}")
                connection.disconnect()
                return null
            }

            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            connection.disconnect()

            // Rename tmp to final
            if (tmpFile.renameTo(msiFile)) {
                chmodIfExists(msiFile)
                Log.i(TAG, "Mono $version downloaded successfully (${msiFile.length()} bytes)")
                msiFile
            } else {
                Log.e(TAG, "Failed to rename tmp file to $msiName")
                tmpFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Mono $version", e)
            null
        }
    }

    /**
     * Returns the Wine Z:\ path to the Mono MSI for use in msiexec commands.
     * Ensures the correct version is downloaded first.
     */
    @JvmStatic
    fun getMonoMsiWinePath(context: Context): String? {
        val msiFile = ensureMonoMsi(context) ?: return null
        return "Z:\\opt\\mono-gecko-offline\\${msiFile.name}"
    }

    /**
     * Get encrypted app ticket as base64, blocking wrapper for Java callers.
     * Returns null if not logged in or ticket unavailable.
     */
    @JvmStatic
    fun getEncryptedAppTicketBase64Blocking(appId: Int): String? {
        return try {
            val service = com.winlator.cmod.steam.service.SteamService.instance ?: return null
            kotlinx.coroutines.runBlocking {
                service.getEncryptedAppTicketBase64(appId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get encrypted app ticket: ${e.message}")
            null
        }
    }

    /**
     * Check if user is currently logged into Steam.
     */
    @JvmStatic
    fun isSteamLoggedIn(): Boolean {
        return try {
            val serviceClass = Class.forName("com.winlator.cmod.steam.service.SteamService")
            val companion = serviceClass.getField("Companion").get(null)!!
            val method = companion.javaClass.getMethod("isLoggedIn")
            method.invoke(companion) as Boolean
        } catch (e: Exception) {
            Log.d(TAG, "isLoggedIn check failed: ${e.message}")
            false
        }
    }
}
