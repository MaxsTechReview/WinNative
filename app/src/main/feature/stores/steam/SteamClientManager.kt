package com.winlator.cmod.feature.stores.steam
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.wine.WineUtils
import com.winlator.cmod.shared.ui.toast.WinToast
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.io.TarCompressorUtils
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Manages ColdClient and Steamless support assets.
object SteamClientManager {
    private const val TAG = "SteamClientManager"
    private const val COMPONENTS_BASE_URL = "https://github.com/maxjivi05/Components/releases/download/Components"

    interface ShellCommandRunner {
        fun exec(command: String): String
    }

    @JvmStatic
    fun isColdClientInstalled(context: Context): Boolean {
        val imageFs = ImageFs.find(context)
        val loaderExe = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/steamclient_loader_x64.exe")
        val extraDll = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/extra_dlls/StubDRM64.dll")
        return loaderExe.exists() && loaderExe.length() > 0 && extraDll.exists() && extraDll.length() > 0
    }

    private fun downloadFile(
        urlStr: String,
        dest: File,
    ) {
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

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } >= 0) {
                        output.write(buf, 0, bytesRead)
                    }
                }
            }

            val total = conn.contentLength.toLong()
            if (total > 0 && dest.length() != total) {
                dest.delete()
                throw Exception("Incomplete download: ${dest.length()}/$total")
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadUrlsFor(fileName: String): Array<String> {
        val alternate =
            when (fileName) {
                "steam-token.tzst" -> "steam-token-r2.tzst"
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

    private fun ensureArchiveReady(
        context: Context,
        fileName: String,
        failureMessage: String,
    ): Boolean {
        val dest = File(context.filesDir, fileName)
        if (dest.exists() && dest.length() > 0) return true

        val tmp = File("${dest.absolutePath}.part")
        for (urlStr in downloadUrlsFor(fileName)) {
            try {
                Log.d(TAG, "Downloading $fileName from: $urlStr")
                downloadFile(urlStr, tmp)
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
            WinToast.show(context, failureMessage, Toast.LENGTH_LONG)
        }
        return false
    }

    @JvmStatic
    fun extractColdClientSupport(context: Context): Boolean {
        val imageFs = ImageFs.find(context)
        val expFile = File(context.filesDir, "experimental-drm.tzst")
        val stampFile = File(context.filesDir, "experimental-drm.version")

        // Refresh extracted ColdClient only when the bundled version changes.
        val bundledVersion = BuildConfig.COLD_CLIENT_VERSION
        val installedVersion = runCatching { stampFile.readText().trim() }.getOrNull()
        val outdated = installedVersion != bundledVersion

        if (isColdClientInstalled(context) && !outdated) return true

        if (outdated) {
            Log.i(TAG, "ColdClient version changed ($installedVersion -> $bundledVersion); refreshing")
            expFile.delete()
            // Remove stale injected DLLs before re-extraction.
            runCatching {
                File(
                    imageFs.rootDir,
                    "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/extra_dlls",
                ).deleteRecursively()
            }
        }

        if (!expFile.exists()) {
            try {
                context.assets.open("experimental-drm.tzst").use { input ->
                    FileOutputStream(expFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied bundled experimental-drm.tzst to filesDir ($bundledVersion)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled experimental-drm.tzst: ${e.message}")
                return false
            }
        }

        return try {
            TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                expFile,
                imageFs.rootDir,
                null,
            )
            runCatching { stampFile.writeText(bundledVersion) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ColdClient support archive: ${e.message}")
            false
        }
    }

    @JvmStatic
    fun ensureColdClientSupportReady(context: Context): Boolean = extractColdClientSupport(context)

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
                    rootDir,
                )
                chmodIfExists(generateInterfacesExe)
                chmodIfExists(steamlessCli)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract Steamless support assets", e)
                return false
            }
        }

        val monoMsi = ensureMonoMsi(context)
        if (monoMsi == null) {
            Log.w(TAG, "Mono MSI not available; Steamless may fail if .NET is needed")
        }

        return steamlessCli.exists() && generateInterfacesExe.exists()
    }

    @JvmStatic
    fun runSteamless(
        context: Context,
        exePath: String,
        shellRunner: ShellCommandRunner,
    ): Boolean {
        val rootDir = ImageFs.find(context).rootDir
        val steamlessCli = File(rootDir, "Steamless/Steamless.CLI.exe")
        val pluginsDir = File(rootDir, "Steamless/Plugins")
        if (!steamlessCli.exists()) {
            Log.e(TAG, "Steamless CLI not found at ${steamlessCli.path}")
            return false
        }
        if (!pluginsDir.exists() || pluginsDir.list().isNullOrEmpty()) {
            Log.e(TAG, "Steamless Plugins/ directory is missing or empty — cannot unpack")
            return false
        }

        var batchFile: File? = null
        try {
            val normalizedPath = exePath.replace('/', '\\')
            val hostExeFile = File(exePath)
            val windowsPath =
                when {
                    normalizedPath.matches(Regex("^[A-Za-z]:.*")) -> normalizedPath
                    hostExeFile.isAbsolute -> {
                        val absolutePath = hostExeFile.absolutePath
                        val mappedStoragePath =
                            if (absolutePath.startsWith("/storage/") || absolutePath.startsWith("/mnt/media_rw/")) {
                                WineUtils.tryGetDosPath(absolutePath)
                            } else {
                                null
                            }
                        mappedStoragePath ?: WineUtils.getWindowsPath(null, absolutePath)
                    }
                    else -> {
                        Log.e(TAG, "Steamless received a relative exe path without drive context: $exePath")
                        return false
                    }
                }

            batchFile = File(rootDir, "tmp/steamless_wrapper.bat")
            batchFile.parentFile?.mkdirs()
            val batchContent = "@echo off\r\n" +
                "z:\\Steamless\\Steamless.CLI.exe \"$windowsPath\"\r\n" +
                "echo STEAMLESS_EXIT_CODE=%ERRORLEVEL%\r\n"
            FileUtils.writeString(batchFile, batchContent)

            val command = "wine z:\\tmp\\steamless_wrapper.bat"
            val output = shellRunner.exec(command)
            Log.d(TAG, "Steamless CLI output: $output")

            val steamlessSuccess = output.lowercase().contains("successfully unpacked")

            val unixPath = exePath.replace('\\', '/')
            val mappedExe = WineUtils.getNativePath(ImageFs.find(context), windowsPath)
            val hostExe =
                when {
                    mappedExe != null -> mappedExe
                    File(unixPath).isAbsolute -> File(unixPath)
                    else -> {
                        Log.e(TAG, "Steamless exe path could not be resolved to a host file: exePath=$exePath windowsPath=$windowsPath")
                        return false
                    }
                }
            val unpackedExe = File(hostExe.parentFile, hostExe.name + ".unpacked.exe")
            val originalExe = File(hostExe.parentFile, hostExe.name + ".original.exe")

            if (steamlessSuccess && hostExe.exists() && unpackedExe.exists()) {
                if (!originalExe.exists()) {
                    Files.copy(hostExe.toPath(), originalExe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    Log.d(TAG, "Backed up original exe as ${originalExe.name}")
                }
                Files.copy(unpackedExe.toPath(), hostExe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Log.d(TAG, "Swapped exe with unpacked version")
                return true
            } else if (!steamlessSuccess && unpackedExe.exists()) {
                // Reuse a prior unpack when the CLI fails this run.
                if (!originalExe.exists() && hostExe.exists()) {
                    Files.copy(hostExe.toPath(), originalExe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                Files.copy(unpackedExe.toPath(), hostExe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Log.d(TAG, "Used existing .unpacked.exe from prior run")
                return true
            }
            Log.w(TAG, "Steamless did not produce .unpacked.exe (success=$steamlessSuccess)")
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

    // Detect the Mono version expected by the current Wine build.
    @JvmStatic
    @JvmOverloads
    fun detectRequiredMonoVersion(
        context: Context,
        containerWinePath: String? = null,
    ): String? {
        val imageFs = ImageFs.find(context)
        val contentsDir = File(context.filesDir, "contents")

        val candidates = mutableListOf<File>()

        if (containerWinePath != null) {
            val wineDir = File(containerWinePath)
            candidates.add(File(wineDir, "lib/wine/aarch64-windows/mscoree.dll"))
            candidates.add(File(wineDir, "lib/wine/x86_64-windows/mscoree.dll"))
            candidates.add(File(wineDir, "lib/wine/i386-windows/mscoree.dll"))
            Log.d(TAG, "Mono detection: prioritizing container Wine path: $containerWinePath")
        }

        for (typeDir in listOf(File(contentsDir, "Wine"), File(contentsDir, "Proton"))) {
            typeDir.listFiles()?.forEach { buildDir ->
                candidates.add(File(buildDir, "lib/wine/aarch64-windows/mscoree.dll"))
                candidates.add(File(buildDir, "lib/wine/x86_64-windows/mscoree.dll"))
                candidates.add(File(buildDir, "lib/wine/i386-windows/mscoree.dll"))
            }
        }

        val winePath = imageFs.winePath
        if (winePath != null) {
            candidates.add(File(winePath, "lib/wine/x86_64-windows/mscoree.dll"))
            candidates.add(File(winePath, "lib/wine/aarch64-windows/mscoree.dll"))
            candidates.add(File(winePath, "lib/wine/i386-windows/mscoree.dll"))
        }

        Log.d(TAG, "Mono detection: searching ${candidates.size} candidate mscoree.dll paths")
        for (c in candidates) {
            Log.d(TAG, "  candidate: ${c.path} (exists=${c.exists()})")
        }

        val mscoree =
            candidates.firstOrNull { it.exists() } ?: run {
                Log.w(TAG, "mscoree.dll not found in any Wine/Proton build")
                return null
            }
        Log.i(TAG, "Mono detection: using mscoree.dll at ${mscoree.path}")

        return extractMonoVersionFromDll(mscoree)
    }

    private fun extractMonoVersionFromDll(mscoree: File): String? =
        try {
            val bytes = mscoree.readBytes()

            val content = String(bytes, Charsets.ISO_8859_1)
            val pattern = Regex("wine-mono-(\\d+\\.\\d+\\.\\d+)")
            var match = pattern.find(content)
            if (match != null) {
                Log.d(TAG, "Mono version found via ISO-8859-1 wine-mono pattern")
            }

            if (match == null) {
                val content16 = String(bytes, Charsets.UTF_16LE)
                match = pattern.find(content16)
                if (match != null) {
                    Log.d(TAG, "Mono version found via UTF-16LE wine-mono pattern")
                }
            }

            // Some builds store a bare version after this marker.
            if (match == null) {
                val barePattern = Regex("found installed support package %s[\\n\\r]*\\x00(\\d+\\.\\d+\\.\\d+)\\x00")
                match = barePattern.find(content)
                if (match != null) {
                    Log.d(TAG, "Mono version found via bare version after 'support package' marker")
                }
            }

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

    // Return or download the Mono MSI for the current Wine build.
    @JvmStatic
    @JvmOverloads
    fun ensureMonoMsi(
        context: Context,
        containerWinePath: String? = null,
    ): File? {
        val version = detectRequiredMonoVersion(context, containerWinePath)
        if (version == null) {
            Log.w(TAG, "Cannot detect Mono version, skipping Mono install")
            return null
        }

        val msiName = "wine-mono-$version-x86.msi"
        val monoDir = File(ImageFs.find(context).rootDir, "opt/mono-gecko-offline")
        monoDir.mkdirs()
        val msiFile = File(monoDir, msiName)

        Log.i(TAG, "Required Mono version: $version (expected MSI: $msiName)")

        val existingFiles = monoDir.listFiles()
        if (existingFiles.isNullOrEmpty()) {
            Log.i(TAG, "Mono directory is empty, need to download $msiName")
        } else {
            Log.i(TAG, "Mono directory contents (${existingFiles.size} files):")
            existingFiles.forEach { f -> Log.i(TAG, "  ${f.name} (${f.length()} bytes)") }
        }

        val containerManager = ContainerManager(context)
        val usedVersions = mutableSetOf(version)
        for (c in containerManager.containers) {
            val v = c.getExtra("mono_version", null)
            if (v != null) usedVersions.add(v)
        }
        val monoMsiPattern = Regex("wine-mono-(\\d+\\.\\d+\\.\\d+)-x86\\.msi")
        monoDir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".msi.tmp")) {
                Log.i(TAG, "Removing leftover temp file: ${f.name}")
                f.delete()
            } else {
                val msiMatch = monoMsiPattern.matchEntire(f.name)
                if (msiMatch != null && msiMatch.groupValues[1] !in usedVersions) {
                    Log.i(TAG, "Removing unused Mono MSI: ${f.name} (no container needs v${msiMatch.groupValues[1]})")
                    f.delete()
                }
            }
        }

        if (msiFile.exists() && msiFile.length() > 0) {
            Log.i(TAG, "Mono MSI v$version already present and correct: ${msiFile.path} (${msiFile.length()} bytes)")
            chmodIfExists(msiFile)
            return msiFile
        }

        val (major, minor, patch) = version.split('.').map { it.toInt() }
        val candidates = mutableListOf<String>()
        candidates.add(version)
        for (p in (patch - 1) downTo 0) candidates.add("$major.$minor.$p")
        for (m in (minor - 1) downTo 0) candidates.add("$major.$m.0")

        var downloaded: File? = null
        for (candidate in candidates) {
            val candidateMsiName = "wine-mono-$candidate-x86.msi"
            val candidateMsiFile = File(monoDir, candidateMsiName)
            if (candidateMsiFile.exists() && candidateMsiFile.length() > 0) {
                Log.i(TAG, "Mono MSI v$candidate already on disk (substitute for v$version): ${candidateMsiFile.length()} B")
                chmodIfExists(candidateMsiFile)
                downloaded = candidateMsiFile
                break
            }
            val downloadUrl = "https://dl.winehq.org/wine/wine-mono/$candidate/$candidateMsiName"
            Log.i(TAG, "Trying Mono $candidate from $downloadUrl")
            try {
                val tmpFile = File(monoDir, "$candidateMsiName.tmp")
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                val code = connection.responseCode
                if (code != 200) {
                    Log.w(TAG, "Mono $candidate -> HTTP $code, trying next candidate")
                    connection.disconnect()
                    continue
                }
                connection.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }
                connection.disconnect()
                if (tmpFile.renameTo(candidateMsiFile)) {
                    chmodIfExists(candidateMsiFile)
                    Log.i(TAG, "Mono $candidate downloaded (${candidateMsiFile.length()} B)")
                    downloaded = candidateMsiFile
                    break
                } else {
                    Log.e(TAG, "Rename failed for $candidateMsiName.tmp")
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mono $candidate download error, trying next: ${e.message}")
            }
        }
        if (downloaded != null) return downloaded

        val anyExistingMsi = monoDir.listFiles()?.firstOrNull {
            it.name.matches(monoMsiPattern) && it.length() > 0
        }
        if (anyExistingMsi != null) {
            Log.w(TAG, "Mono $version not downloadable; falling back to existing ${anyExistingMsi.name}")
            chmodIfExists(anyExistingMsi)
            return anyExistingMsi
        }
        Log.e(TAG, "Mono $version download failed and no fallback MSI exists; game launch will lack .NET runtime")
        return null
    }

    // Return the Wine Z:\ path to the Mono MSI, downloading it if needed.
    @JvmStatic
    @JvmOverloads
    fun getMonoMsiWinePath(
        context: Context,
        containerWinePath: String? = null,
    ): String? {
        val msiFile = ensureMonoMsi(context, containerWinePath) ?: return null
        return "Z:\\opt\\mono-gecko-offline\\${msiFile.name}"
    }

    // Blocking Java wrapper for encrypted app tickets.
    @JvmStatic
    fun getEncryptedAppTicketBase64Blocking(appId: Int): String? {
        // CM round-trips can take tens of seconds; never block the main thread.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            Log.e(TAG, "getEncryptedAppTicketBase64Blocking called on the main thread — refusing")
            return null
        }
        return try {
            val service = com.winlator.cmod.feature.stores.steam.service.SteamService.instance ?: return null
            kotlinx.coroutines.runBlocking {
                service.getEncryptedAppTicketBase64(appId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get encrypted app ticket: ${e.message}")
            null
        }
    }

    @JvmStatic
    fun isSteamLoggedIn(): Boolean =
        try {
            val serviceClass = Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService")
            val companion = serviceClass.getField("Companion").get(null)!!
            val method = companion.javaClass.getMethod("isLoggedIn")
            method.invoke(companion) as Boolean
        } catch (e: Exception) {
            Log.d(TAG, "isLoggedIn check failed: ${e.message}")
            false
        }
}
