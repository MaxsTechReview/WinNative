package com.armsx2

import com.armsx2.runtime.MainActivityRuntime

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ShaderRepo {

    private const val TAG = "ShaderRepo"

    const val SHADER_DIR = "shaders"

    data class ShaderSource(
        val name: String,
        val url: String,
        val id: String,
        val description: String,
        val requiresPack: String? = null,
    )

    private val SHADER_SOURCES = listOf(
        ShaderSource(
            name = "RetroArch · Slang Shaders",
            url = "https://buildbot.libretro.com/assets/frontend/shaders_slang.zip",
            id = "shaders_slang",
            description = "libretro buildbot · ~51 MB",
        ),
        ShaderSource(
            name = "Retro Crisis · GDV-NTSC",
            url = "https://github.com/RetroCrisis/Retro-Crisis-GDV-NTSC/releases/download/20260321/Retro.Crisis.GDV-NTSC.2026.03.21.zip",
            id = "retro_crisis_gdv_ntsc",
            description = "666 presets · ~0.4 MB",
            requiresPack = "shaders_slang",
        ),
    )

    fun sources(): List<ShaderSource> = SHADER_SOURCES

    fun baseSources(): List<ShaderSource> = SHADER_SOURCES.filter { it.requiresPack == null }

    fun companionSources(): List<ShaderSource> = SHADER_SOURCES.filter { it.requiresPack != null }

    fun requirementMet(context: Context, source: ShaderSource): Boolean {
        val needs = source.requiresPack ?: return true
        return File(shadersRoot(context), needs).isDirectory
    }

    data class InstalledPack(
        val id: String,
        val name: String,
        val presetCount: Int,
        val dir: File,
    )

    fun shadersRoot(context: Context): File =
        File(MainActivityRuntime.assetCopyRoot(context), SHADER_DIR).apply { mkdirs() }

    const val USER_PRESET_DIR = "My Presets"

    fun userPresetDir(context: Context): File =
        File(shadersRoot(context), USER_PRESET_DIR).apply { mkdirs() }

    fun listInstalled(context: Context): List<InstalledPack> {
        val root = shadersRoot(context)
        val dirs = root.listFiles { f ->
            f.isDirectory && !f.name.startsWith(".") && f.name != USER_PRESET_DIR
        } ?: return emptyList()
        return dirs.map { dir ->
            InstalledPack(
                id = dir.name,
                name = SHADER_SOURCES.firstOrNull { it.id == dir.name }?.name ?: dir.name,
                presetCount = countPresets(dir),
                dir = dir,
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun delete(pack: InstalledPack) {
        pack.dir.deleteRecursively()
    }

    private const val MAX_IMPORT_DEPTH = 12

    private fun importId(ctx: Context, raw: String): String {
        val base = raw.substringAfterLast('/')
            .removeSuffix(".zip").removeSuffix(".ZIP")
            .replace(Regex("[^A-Za-z0-9 _.-]"), "_")
            .trim()
            .ifEmpty { "shader pack" }
        val root = shadersRoot(ctx)
        if (!File(root, base).exists()) return base
        var i = 2
        while (File(root, "$base ($i)").exists()) i++
        return "$base ($i)"
    }

    private fun keepIfPresets(target: File, id: String): String? {
        if (countPresets(target) > 0) return id
        target.deleteRecursively()
        return null
    }

    fun importFromZip(ctx: Context, zipUri: Uri): String? {
        val id = importId(ctx, DocumentFile.fromSingleUri(ctx, zipUri)?.name ?: "shader pack")
        val target = File(shadersRoot(ctx), id)
        val staged = File(ctx.cacheDir, "shaderpack-import-$id.zip")
        return try {
            ctx.contentResolver.openInputStream(zipUri)?.use { ins ->
                staged.outputStream().use { ins.copyTo(it) }
            } ?: return null
            target.mkdirs()
            if (extract(staged, target, null) { false }) keepIfPresets(target, id)
            else { target.deleteRecursively(); null }
        } catch (t: Throwable) {
            Log.w(TAG, "zip import failed", t)
            target.deleteRecursively()
            null
        } finally {
            staged.delete()
        }
    }

    fun importFromTree(ctx: Context, treeUri: Uri): String? {
        val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return null
        val id = importId(ctx, tree.name ?: "shader pack")
        val target = File(shadersRoot(ctx), id)
        return try {
            copyTree(ctx, tree, target, 0)
            keepIfPresets(target, id)
        } catch (t: Throwable) {
            Log.w(TAG, "folder import failed", t)
            target.deleteRecursively()
            null
        }
    }

    private fun copyTree(ctx: Context, dir: DocumentFile, dest: File, depth: Int) {
        if (depth > MAX_IMPORT_DEPTH) return
        dest.mkdirs()
        val destGuard = dest.canonicalPath + File.separator
        for (df in dir.listFiles()) {
            val name = df.name?.takeIf { it.isNotEmpty() && !it.startsWith(".") } ?: continue
            val out = File(dest, name)
            if (!out.canonicalPath.startsWith(destGuard)) {
                Log.w(TAG, "import: rejecting entry '$name'")
                continue
            }
            if (df.isDirectory) {
                copyTree(ctx, df, out, depth + 1)
            } else {
                runCatching {
                    ctx.contentResolver.openInputStream(df.uri)?.use { ins ->
                        out.outputStream().use { ins.copyTo(it) }
                    }
                }.onFailure { out.delete() }
            }
        }
    }

    private fun countPresets(dir: File): Int =
        runCatching {
            dir.walkTopDown().count { it.isFile && it.extension.equals("slangp", ignoreCase = true) }
        }.getOrDefault(0)

    fun download(
        context: Context,
        source: ShaderSource,
        onDownload: ((Long, Long) -> Unit)? = null,
        onExtract: ((Int, Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
    ): InstalledPack? {
        val staging = File(MainActivityRuntime.assetCopyRoot(context), ".shaderpacks-tmp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val tmpZip = File(staging, "${source.id}.zip")
        val tmpDir = File(staging, source.id).apply { mkdirs() }

        try {
            if (!requirementMet(context, source)) {
                Log.w(TAG, "install: ${source.id} needs '${source.requiresPack}' installed first")
                return null
            }
            if (!fetchToFile(source.url, tmpZip, onDownload, isCancelled)) return null
            if (isCancelled()) return null
            if (!extract(tmpZip, tmpDir, onExtract, isCancelled)) return null
            if (isCancelled()) return null

            val presets = countPresets(tmpDir)
            if (presets == 0) {
                Log.w(TAG, "install: ${source.id} extracted 0 .slangp presets")
                return null
            }

            val targetDir: File
            if (source.requiresPack != null) {
                targetDir = shadersRoot(context)
                mergeInto(tmpDir, targetDir)
            } else {
                targetDir = File(shadersRoot(context), source.id)
                if (targetDir.exists()) targetDir.deleteRecursively()
                if (!tmpDir.renameTo(targetDir)) {
                    Log.w(TAG, "install: rename $tmpDir -> $targetDir failed")
                    return null
                }
            }
            Log.i(TAG, "install: ${source.id} -> $targetDir ($presets presets)")
            return InstalledPack(source.id, source.name, presets, targetDir)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun fetchToFile(
        url: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)?,
        isCancelled: () -> Boolean,
    ): Boolean {
        val userAgent = "ARMSX2/" + runCatching {
            NativeApp.getBuildVersion()
        }.getOrNull().orEmpty().ifEmpty { "dev" }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "fetch: $url -> status=$code")
                return false
            }
            val total = conn.contentLengthLong

            var read = 0L
            var reported = 0L
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) return false
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read - reported >= PROGRESS_BYTES_STEP) {
                            reported = read
                            onProgress?.invoke(read, total)
                        }
                    }
                }
            }
            onProgress?.invoke(read, total)
            return read > 0
        } catch (e: Exception) {
            Log.w(TAG, "fetch: $url failed: ${e.message}")
            return false
        } finally {
            conn?.disconnect()
        }
    }

    private fun extract(
        zip: File,
        targetDir: File,
        onProgress: ((Int, Int) -> Unit)?,
        isCancelled: () -> Boolean,
    ): Boolean {
        val targetCanonical = targetDir.canonicalPath
        val guardPrefix = targetCanonical + File.separator

        try {
            ZipFile(zip).use { zf ->
                val entries = zf.entries().toList().filterNot { isJunkEntry(it.name) }
                val total = entries.size
                val strip = commonRootPrefix(entries)
                var done = 0
                var reported = 0

                for (entry in entries) {
                    if (isCancelled()) return false
                    done++
                    val name = entry.name.removePrefix(strip)
                    if (name.isEmpty()) continue

                    val outFile = File(targetDir, name)
                    val canonical = outFile.canonicalPath
                    if (canonical != targetCanonical && !canonical.startsWith(guardPrefix)) {
                        Log.w(TAG, "extract: rejecting zip-slip entry '${entry.name}' -> $canonical")
                        return false
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { out -> input.copyTo(out) }
                        }
                    }

                    if (done - reported >= PROGRESS_ENTRY_STEP || done == total) {
                        reported = done
                        onProgress?.invoke(done, total)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "extract: failed: ${e.message}")
            return false
        }
    }

    private fun commonRootPrefix(entries: List<ZipEntry>): String {
        val first = entries.firstOrNull()?.name ?: return ""
        val slash = first.indexOf('/')
        if (slash <= 0) return ""
        val prefix = first.substring(0, slash + 1)
        return if (entries.all { it.name.startsWith(prefix) }) prefix else ""
    }

    private fun isJunkEntry(name: String): Boolean =
        name.startsWith("__MACOSX/") ||
            name.substringAfterLast('/') == ".DS_Store" ||
            name.substringAfterLast('/').startsWith("._")

    private fun mergeInto(src: File, dst: File) {
        dst.mkdirs()
        src.listFiles()?.forEach { child ->
            val target = File(dst, child.name)
            if (child.isDirectory) {
                mergeInto(child, target)
            } else {
                if (target.exists()) target.delete()
                if (!child.renameTo(target)) {
                    child.copyTo(target, overwrite = true)
                    child.delete()
                }
            }
        }
    }

    private const val PROGRESS_BYTES_STEP = 256L * 1024L
    private const val PROGRESS_ENTRY_STEP = 64
}
