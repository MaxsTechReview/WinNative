package com.winlator.cmod.runtime.content.component

import android.content.Context
import android.content.Intent
import android.util.Log
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.content.Downloader
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.wine.WineRegistryEditor
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.io.TarCompressorUtils
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Executes a HuggingFace component manifest (the `.yml` recipe) into a specific [Container].
 * Mirrors the step semantics of the reference dependency installer.
 *
 * Phase-3 scope: download -> archive_extract -> copy_dll/copy_file -> override_dll + registry.
 * Verbs that require a running Wine process (install_exe/msi, register_dll/regsvr32, CAB extraction,
 * font registration, set_windows) throw [InstallException] so the UI can report them clearly.
 *
 * Run on a background thread.
 */
class ComponentInstaller(
    private val context: Context,
    private val container: Container,
    private val componentName: String,
    private val manifestYaml: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(text: String)

        /** [fraction] in 0..1, or negative for indeterminate. */
        fun onProgress(fraction: Float)
    }

    class InstallException(
        message: String,
    ) : Exception(message)

    private val componentDir = File(context.cacheDir, "wn-components/$componentName")
    private val workingDir = File(componentDir, "installed") // "temp/" + extraction scratch
    private val driveC = File(container.rootDir, ".wine/drive_c")
    private val userReg = File(container.rootDir, ".wine/user.reg")
    private val systemReg = File(container.rootDir, ".wine/system.reg")

    @Suppress("UNCHECKED_CAST")
    fun run() {
        if (!driveC.isDirectory) {
            throw InstallException("Container isn't set up yet — boot it once first.")
        }
        componentDir.mkdirs()
        workingDir.mkdirs()

        val doc = Yaml().load<Map<String, Any?>>(manifestYaml)
            ?: throw InstallException("Empty manifest")
        val steps = (doc["Steps"] as? List<Map<String, Any?>>)
            ?: throw InstallException("Manifest has no steps")

        try {
            checkCancel()
            download(steps)
            checkCancel()
            extractArchives(steps)
            checkCancel()
            runSteps(steps)
            markInstalled()
        } finally {
            File(driveC, "wn-install").deleteRecursively()
        }
    }

    // Abort if the worker thread was interrupted (the install sheet was closed).
    private fun checkCancel() {
        if (Thread.currentThread().isInterrupted) throw InstallException("Cancelled")
    }

    private fun markInstalled() {
        val dir = File(container.rootDir, INSTALLED_DIR)
        dir.mkdirs()
        try {
            File(dir, componentName).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            Log.w(TAG, "couldn't write installed marker for $componentName", e)
        }
    }

    // ---- phase 1: download the referenced files ----
    private fun download(steps: List<Map<String, Any?>>) {
        val dlActions = setOf("download_archive", "install_exe", "install_msi", "cab_extract", "archive_extract")
        val toDownload = steps.filter { (it["action"] as? String) in dlActions && it["url"] is String }
        toDownload.forEachIndexed { i, step ->
            checkCancel()
            val url = step["url"] as String
            val name = (step["rename"] as? String) ?: (step["file_name"] as String)
            val dst = File(componentDir, name)
            val checksum = (step["file_checksum"] as? String)?.lowercase(Locale.ROOT)
            if (dst.isFile && dst.length() > 0 && (checksum == null || md5(dst) == checksum)) {
                return@forEachIndexed // already cached + verified
            }
            listener.onStatus("Downloading $name (${i + 1}/${toDownload.size})")
            listener.onProgress(0f)
            val ok =
                Downloader.downloadFile(url, dst) { done, total ->
                    if (total > 0) listener.onProgress(done.toFloat() / total)
                }
            if (!ok) throw InstallException("Download failed: $name")
            if (checksum != null && md5(dst) != checksum) {
                throw InstallException("Checksum mismatch: $name")
            }
        }
    }

    // ---- phase 2: extract archives into the working dir ----
    private fun extractArchives(steps: List<Map<String, Any?>>) {
        for (step in steps) {
            checkCancel()
            when (step["action"] as? String) {
                "archive_extract" -> {
                    val name = (step["rename"] as? String) ?: (step["file_name"] as String)
                    val src = File(componentDir, name)
                    val outDir = File(workingDir, name.substringBeforeLast("."))
                    outDir.mkdirs()
                    listener.onStatus("Extracting $name")
                    listener.onProgress(-1f)
                    when {
                        name.endsWith(".zip") -> extractZip(src, outDir)
                        name.endsWith(".tar.xz") || name.endsWith(".xz") ->
                            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, src, outDir)) {
                                throw InstallException("Extract failed: $name")
                            }
                        name.endsWith(".tar.zst") || name.endsWith(".zst") || name.endsWith(".tzst") ->
                            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, src, outDir)) {
                                throw InstallException("Extract failed: $name")
                            }
                        else -> throw InstallException("Unsupported archive format: $name")
                    }
                }

                "cab_extract" -> {
                    val name = (step["rename"] as? String) ?: (step["file_name"] as String)
                    val dest = resolveDest(step["dest"] as? String ?: "temp/")
                    listener.onStatus("Extracting $name")
                    listener.onProgress(-1f)
                    extractCab(resolveSource(name), dest, null)
                }

                "get_from_cab" -> {
                    val source = step["source"] as? String ?: continue
                    val pattern = step["file_name"] as? String
                    val dest = resolveDest(step["dest"] as? String ?: "temp/")
                    listener.onStatus("Extracting from CAB")
                    listener.onProgress(-1f)
                    for (cab in resolveCabSources(source)) extractCab(cab, dest, pattern)
                }
            }
        }
    }

    private fun extractZip(
        zip: File,
        outDir: File,
    ) {
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(outDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { o -> zis.copyTo(o) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Runs the bionic-native cabextract shipped in the system image, blocking until it finishes. */
    private fun extractCab(
        cab: File,
        destDir: File,
        pattern: String?,
    ) {
        if (!cab.isFile) throw InstallException("CAB not found: ${cab.name}")
        destDir.mkdirs()
        val rootDir = ImageFs.find(context).rootDir
        val cabextract = File(rootDir, "usr/bin/cabextract")
        if (!cabextract.isFile) throw InstallException("cabextract is missing from the system image.")
        FileUtils.chmod(cabextract, "755".toInt(8))

        val args = ArrayList<String>()
        args.add(cabextract.absolutePath)
        if (pattern != null) {
            args.add("-F")
            args.add(pattern)
        }
        args.add("-d")
        args.add(destDir.absolutePath)
        args.add(cab.absolutePath)

        val pb = ProcessBuilder(args)
        pb.environment()["LD_LIBRARY_PATH"] = File(rootDir, "usr/lib").absolutePath + ":/system/lib64"
        val devNull = File("/dev/null")
        pb.redirectOutput(ProcessBuilder.Redirect.to(devNull))
        pb.redirectError(ProcessBuilder.Redirect.to(devNull))
        val proc = pb.start()
        try {
            val exit = proc.waitFor()
            if (exit != 0) throw InstallException("CAB extract failed (exit $exit): ${cab.name}")
        } catch (e: InterruptedException) {
            proc.destroy()
            throw InstallException("Cancelled")
        } catch (e: java.io.IOException) {
            throw InstallException("Couldn't run cabextract: ${e.message}")
        }
    }

    /** Resolves a get_from_cab source, expanding a `*` glob in the filename to the matching cab files. */
    private fun resolveCabSources(source: String): List<File> {
        if (!source.contains("*")) return listOf(resolveSource(source))
        val resolved = resolveSource(source)
        val parent = resolved.parentFile ?: return emptyList()
        val nameGlob = globToRegex(resolved.name)
        return parent.listFiles()?.filter { it.isFile && nameGlob.matches(it.name) } ?: emptyList()
    }

    // ---- phase 3: apply the install steps ----
    private fun runSteps(steps: List<Map<String, Any?>>) {
        listener.onStatus("Installing into ${container.name}")
        listener.onProgress(-1f)
        for (step in steps) {
            checkCancel()
            when (val action = step["action"] as? String) {
                "download_archive", "archive_extract" -> {} // already handled
                "copy_dll", "copy_file", "link_dir" -> copyFiles(step)
                "override_dll" -> overrideDll(step)
                "set_register_key" -> setRegisterKey(step)
                "delete_dlls" -> deleteDlls(step)
                "install_exe" -> runInstaller(step, isMsi = false)
                "install_msi" -> runInstaller(step, isMsi = true)
                "register_font" -> registerFont(step)
                "replace_font" -> replaceFont(step)
                "install_fonts", "install_cab_fonts" -> installFonts(step)
                "register_dll" -> registerDlls(step)
                "uninstall" -> Log.w(TAG, "skipping uninstall for $componentName (installer handles replacement)")
                "set_windows", "use_windows" ->
                    throw InstallException("Needs '$action' — not supported yet.")
                else -> Log.w(TAG, "Unknown action: $action")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyFiles(step: Map<String, Any?>) {
        val name = step["file_name"] as? String ?: return
        val srcDir = resolveSource(step["url"] as? String ?: return)
        val dstDir = resolveDest(step["dest"] as? String ?: return)
        dstDir.mkdirs()
        if (name.contains("*")) {
            val regex = globToRegex(name)
            val files = srcDir.listFiles() ?: throw InstallException("Source not found: $srcDir")
            var copied = 0
            for (f in files) {
                if (f.isFile && regex.matches(f.name)) {
                    if (!FileUtils.copy(f, File(dstDir, f.name))) throw InstallException("Copy failed: ${f.name}")
                    copied++
                }
            }
            if (copied == 0) Log.w(TAG, "copy: no files matched '$name' in $srcDir")
        } else {
            val src = File(srcDir, name)
            if (!src.exists()) throw InstallException("Missing file: $name")
            if (!FileUtils.copy(src, File(dstDir, name))) throw InstallException("Copy failed: $name")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun overrideDll(step: Map<String, Any?>) {
        WineRegistryEditor(userReg).use { reg ->
            reg.setCreateKeyIfNotExist(true)
            val bundle = step["bundle"] as? List<Map<String, Any?>>
            if (bundle != null) {
                for (b in bundle) {
                    val dll = (b["value"] ?: continue).toString()
                    val data = (b["data"] ?: "native,builtin").toString()
                    reg.setStringValue(DLL_OVERRIDES, dll, data)
                }
            } else {
                val dll = (step["dll"] ?: return@use).toString()
                val type = (step["type"] ?: "native,builtin").toString()
                reg.setStringValue(DLL_OVERRIDES, dll, type)
            }
        }
    }

    private fun setRegisterKey(step: Map<String, Any?>) {
        val key = step["key"] as? String ?: return
        val value = step["value"] as? String ?: return
        val hklm = key.startsWith("HKLM", ignoreCase = true)
        val regFile = if (hklm) systemReg else userReg
        val subKey = key.substringAfter('\\').trimStart('\\')
        WineRegistryEditor(regFile).use { reg ->
            reg.setCreateKeyIfNotExist(true)
            when (step["type"] as? String) {
                "REG_DWORD" -> {
                    val data = step["data"]
                    val intVal = (data as? Int) ?: data?.toString()?.toIntOrNull() ?: 0
                    reg.setDwordValue(subKey, value, intVal)
                }
                "REG_SZ" -> reg.setStringValue(subKey, value, (step["data"] ?: "").toString())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deleteDlls(step: Map<String, Any?>) {
        val dir = resolveDest(step["dest"] as? String ?: return)
        val dlls = step["dlls"] as? List<String> ?: return
        for (d in dlls) File(dir, d).delete()
    }

    private fun registerFont(step: Map<String, Any?>) {
        val name = step["name"] as? String ?: return
        val file = step["file"] as? String ?: return
        WineRegistryEditor(systemReg).use { reg ->
            reg.setCreateKeyIfNotExist(true)
            reg.setStringValue("Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts", name, file)
        }
    }

    private fun replaceFont(step: Map<String, Any?>) {
        val font = step["font"] as? String ?: return
        val replacement = (step["replace"] as? List<*>)?.firstOrNull()?.toString() ?: return
        WineRegistryEditor(userReg).use { reg ->
            reg.setCreateKeyIfNotExist(true)
            reg.setStringValue("Software\\Wine\\Fonts\\Replacements", font, replacement)
        }
    }

    private fun installFonts(step: Map<String, Any?>) {
        val srcDir = resolveDest(step["url"] as? String ?: return)
        val fonts = step["fonts"] as? List<*> ?: return
        val fontsDir = File(driveC, "windows/Fonts")
        fontsDir.mkdirs()
        for (f in fonts) {
            val fn = f?.toString() ?: continue
            val src = File(srcDir, fn)
            if (src.isFile) FileUtils.copy(src, File(fontsDir, fn))
        }
    }

    /** Stages the installer into the container and runs it inside Wine (via the boot session), waiting. */
    private fun runInstaller(
        step: Map<String, Any?>,
        isMsi: Boolean,
    ) {
        val fileName = (step["rename"] as? String) ?: (step["file_name"] as String)
        val src = File(componentDir, fileName)
        if (!src.isFile) throw InstallException("Installer not found: $fileName")
        val stageDir = File(driveC, "wn-install")
        stageDir.mkdirs()
        val staged = File(stageDir, fileName)
        if (!FileUtils.copy(src, staged)) throw InstallException("Couldn't stage installer: $fileName")

        val winPath = "C:\\wn-install\\$fileName"
        val arguments = (step["arguments"] as? String).orEmpty()
        listener.onStatus("Running installer: $fileName")
        listener.onProgress(-1f)

        val bootExe: String
        val bootArgs: String
        if (isMsi) {
            bootExe = "C:\\windows\\system32\\msiexec.exe"
            bootArgs = "/i \"$winPath\" ${arguments.ifBlank { "/passive" }}".trim()
        } else {
            bootExe = winPath
            bootArgs = arguments
        }
        val code = launchInContainerAndWait(bootExe, bootArgs)
        // 0 = success, 3010 = success but reboot required (common for redists/MSI).
        if (code != 0 && code != 3010) throw InstallException("Installer exited with code $code: $fileName")
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerDlls(step: Map<String, Any?>) {
        val dlls = (step["dlls"] as? List<String>)?.filter { it.isNotBlank() } ?: return
        if (dlls.isEmpty()) return
        listener.onStatus("Registering ${dlls.size} DLL(s)")
        listener.onProgress(-1f)
        val batch = dlls.joinToString(" & ") { "regsvr32 /s $it" }
        val code = launchInContainerAndWait("C:\\windows\\system32\\cmd.exe", "/c \"$batch\"")
        // regsvr32 /s exit codes are unreliable; the DLLs are already placed + overridden, so don't hard-fail.
        if (code != 0) Log.w(TAG, "register_dll batch for $componentName exited $code")
    }

    /** Boots the container with the given Wine program (gated dependency mode) and blocks for its exit code. */
    private fun launchInContainerAndWait(
        bootExe: String,
        bootArgs: String,
    ): Int {
        DependencyInstallBridge.begin()
        val intent =
            Intent(context, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
                putExtra("boot_exe", bootExe)
                putExtra("boot_exe_args", bootArgs)
                putExtra("is_dependency_installer", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        return DependencyInstallBridge.await(INSTALL_TIMEOUT_MS)
            ?: throw InstallException("Installer timed out or the session was closed.")
    }

    // ---- path templates ----
    // q(): where a source file currently lives (downloads in componentDir, extracted under temp/=workingDir).
    private fun resolveSource(path: String): File {
        if (path.startsWith("temp/")) return File(workingDir, path.removePrefix("temp/"))
        val inComp = File(componentDir, path)
        return if (inComp.exists()) inComp else File(workingDir, path)
    }

    // l(): where a file should land. win32->syswow64, win64->system32, windows/->drive_c/windows, temp/->workingDir.
    private fun resolveDest(dest: String): File =
        when {
            dest.startsWith("temp/") -> File(workingDir, dest.removePrefix("temp/"))
            dest.startsWith("windows/") -> File(driveC, dest)
            dest == "win32" || dest.startsWith("win32/") ->
                File(File(driveC, "windows/syswow64"), dest.removePrefix("win32").trimStart('/'))
            dest == "win64" || dest.startsWith("win64/") ->
                File(File(driveC, "windows/system32"), dest.removePrefix("win64").trimStart('/'))
            else -> File(dest)
        }

    private fun globToRegex(glob: String): Regex {
        val p =
            buildString {
                for (c in glob) {
                    when (c) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> if (c.isLetterOrDigit() || c == '_' || c == '-') append(c) else append("\\$c")
                    }
                }
            }
        return Regex("^$p$", RegexOption.IGNORE_CASE)
    }

    private fun md5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ComponentInstaller"
        private const val DLL_OVERRIDES = "Software\\Wine\\DllOverrides"
        private const val INSTALL_TIMEOUT_MS = 20L * 60L * 1000L
        private const val INSTALLED_DIR = ".wn-components"

        /** Names of components already installed into [container] (persisted per container). */
        @JvmStatic
        fun installedComponents(container: Container): Set<String> {
            val dir = File(container.rootDir, INSTALLED_DIR)
            return dir.listFiles()?.filter { it.isFile }?.map { it.name }?.toSet() ?: emptySet()
        }
    }
}
