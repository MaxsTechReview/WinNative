package com.armsx2

import com.armsx2.runtime.MainActivityRuntime

import android.content.Context
import android.net.Uri
import android.util.Log
import kr.co.iefriends.pcsx2.HttpClient
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object CustomDriver {

    private const val TAG = "CustomDriver"

    private data class DriverSource(
        val label: String,
        val releasesUrl: String,
        val idPrefix: String,
    )
    private val DRIVER_SOURCES = listOf(
        DriverSource(
            "AdrenoToolsDrivers",
            "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases",
            "",
        ),
        DriverSource(
            "MrPurple · purple-turnip",
            "https://api.github.com/repos/MrPurple666/purple-turnip/releases",
            "purpleturnip",
        ),
        DriverSource(
            "StevenMXZ · Adreno-Tools",
            "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases",
            "stevenmxz",
        ),
        DriverSource(
            "crueter · GameHub 8Elite",
            "https://api.github.com/repos/crueter/GameHub-8Elite-Drivers/releases",
            "gamehub8e",
        ),
        DriverSource(
            "PojavLauncherTeam · freedreno (A10)",
            "https://api.github.com/repos/PojavLauncherTeam/freedreno-builder/releases",
            "freedrenobuilder",
        ),
        DriverSource(
            "WearyConcern1165 · ExynosTools",
            "https://api.github.com/repos/WearyConcern1165/ExynosTools/releases",
            "exynostools",
        ),
    )

    private const val DEFAULT_LIBRARY_NAME = "libvulkan_freedreno.so"

    data class InstalledDriver(
        val id: String,
        val name: String,
        val description: String,
        val author: String,
        val vendor: String,
        val version: String,
        val libraryName: String,
        val driverDir: File,
    ) {
        val driverFile: File get() = File(driverDir, libraryName)
        val redirectDir: File get() = File(driverDir, "cache")
    }

    data class RemoteDriver(
        val id: String,
        val releaseName: String,
        val assetName: String,
        val tagName: String,
        val publishedAt: String,
        val assetUrl: String,
        val sizeBytes: Long,
        val source: String = "",
    )

    private fun driversRoot(context: Context): File =
        File(context.filesDir, "drivers").apply { mkdirs() }

    private fun winlatorDriversRoot(context: Context): File =
        File(context.filesDir, "contents/adrenotools")

    private fun parseDriverDir(dir: File): InstalledDriver? {
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) return null
        val text = runCatching { metaFile.readText() }.getOrNull() ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val libName = json.optString("libraryName").ifEmpty { DEFAULT_LIBRARY_NAME }
        if (!File(dir, libName).exists()) return null
        return InstalledDriver(
            id = dir.name,
            name = json.optString("name").ifEmpty { dir.name },
            description = json.optString("description"),
            author = json.optString("author"),
            vendor = json.optString("vendor"),
            version = json.optString("driverVersion").ifEmpty { json.optString("packageVersion") },
            libraryName = libName,
            driverDir = dir,
        )
    }

    fun listInstalled(context: Context): List<InstalledDriver> {
        val out = mutableListOf<InstalledDriver>()
        val seen = HashSet<String>()
        for (root in listOf(driversRoot(context), winlatorDriversRoot(context))) {
            val dirs = root.listFiles { f -> f.isDirectory } ?: continue
            for (dir in dirs) {
                val driver = parseDriverDir(dir) ?: continue
                if (seen.add(driver.id)) out += driver
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun delete(installed: InstalledDriver) {
        installed.driverDir.deleteRecursively()
    }

    fun fetchRemote(): List<RemoteDriver> {
        val userAgent = "ARMSX2/" + runCatching {
            NativeApp.getBuildVersion()
        }.getOrNull().orEmpty().ifEmpty { "dev" }
        val out = mutableListOf<RemoteDriver>()
        val seen = HashSet<String>()
        for (src in DRIVER_SOURCES) {
            for (rd in fetchSource(src, userAgent)) {
                if (seen.add(rd.id)) out += rd
            }
        }
        return out
    }

    private fun fetchSource(src: DriverSource, userAgent: String): List<RemoteDriver> {
        val resp = runCatching {
            HttpClient.doRequest(src.releasesUrl, "GET", null, userAgent, 15000)
        }.getOrNull() ?: return emptyList()
        if (resp.statusCode != 200 || resp.data.isEmpty()) {
            Log.w(TAG, "fetchRemote(${src.label}): status=${resp.statusCode}, size=${resp.data.size}")
            return emptyList()
        }

        val text = runCatching { String(resp.data, Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()

        val out = mutableListOf<RemoteDriver>()
        for (i in 0 until arr.length()) {
            val release = arr.optJSONObject(i) ?: continue
            val tag = release.optString("tag_name").ifEmpty { release.optString("name") }
            val releaseName = release.optString("name").ifEmpty { tag }
            val publishedAt = release.optString("published_at")
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val assetName = asset.optString("name")
                if (!assetName.endsWith(".zip", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isEmpty()) continue
                val size = asset.optLong("size", 0L)
                val idTag = if (src.idPrefix.isEmpty()) tag else "${src.idPrefix}-$tag"
                out += RemoteDriver(
                    id = makeId(idTag, assetName),
                    releaseName = releaseName,
                    assetName = assetName,
                    tagName = tag,
                    publishedAt = publishedAt,
                    assetUrl = url,
                    sizeBytes = size,
                    source = src.label,
                )
            }
        }
        return out
    }

    private fun makeId(tag: String, assetName: String): String {
        val base = assetName.removeSuffix(".zip").removeSuffix(".ZIP")
        val combined = if (tag.isNotEmpty()) "$tag-$base" else base
        return combined.replace(Regex("[^A-Za-z0-9._-]"), "_").lowercase()
    }

    fun download(
        context: Context,
        remote: RemoteDriver,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): InstalledDriver? {
        val userAgent = "ARMSX2/" + runCatching {
            NativeApp.getBuildVersion()
        }.getOrNull().orEmpty().ifEmpty { "dev" }

        val resp = runCatching {
            HttpClient.doRequest(remote.assetUrl, "GET", null, userAgent, 60_000)
        }.getOrNull() ?: return null
        if (resp.statusCode != 200 || resp.data.isEmpty()) {
            Log.w(TAG, "download: status=${resp.statusCode}, size=${resp.data.size}")
            return null
        }
        onProgress?.invoke(resp.data.size.toLong(), remote.sizeBytes.coerceAtLeast(resp.data.size.toLong()))

        return installFromStream(context, remote.id, resp.data.inputStream())
    }

    fun installFromUri(context: Context, uri: Uri): InstalledDriver? {
        val filename = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: "imported_driver.zip"
        val id = makeId("local", filename)
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            Log.w(TAG, "installFromUri: couldn't open $uri")
            return null
        }
        return stream.use { installFromStream(context, id, it) }
    }

    private fun installFromStream(context: Context, id: String, stream: InputStream): InstalledDriver? {
        val targetDir = File(driversRoot(context), id)
        val tmpDir = File(driversRoot(context), "$id.tmp").also {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }

        try {
            ZipInputStream(stream).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/")) {
                        Log.w(TAG, "install: skipping suspicious entry $name")
                        continue
                    }
                    val outName = name.substringAfterLast('/')
                    if (outName.isEmpty() || entry.isDirectory) continue
                    val outFile = File(tmpDir, outName)
                    FileOutputStream(outFile).use { fos ->
                        zin.copyTo(fos)
                    }
                    zin.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "install: extract failed: ${e.message}")
            tmpDir.deleteRecursively()
            return null
        }

        val meta = File(tmpDir, "meta.json")
        if (!meta.exists()) {
            val soFile = tmpDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }?.firstOrNull()
            if (soFile != null) {
                val synthLib = if (File(tmpDir, DEFAULT_LIBRARY_NAME).exists()) DEFAULT_LIBRARY_NAME else soFile.name
                runCatching {
                    meta.writeText(
                        JSONObject().apply {
                            put("schemaVersion", 1)
                            put("name", id)
                            put("description", "Turnip / freedreno (synthesized manifest)")
                            put("author", "freedreno-builder")
                            put("vendor", "Mesa")
                            put("driverVersion", "")
                            put("minApi", 24)
                            put("libraryName", synthLib)
                        }.toString()
                    )
                    Log.i(TAG, "install: synthesized meta.json ($synthLib)")
                }
            }
        }
        if (!meta.exists()) {
            Log.w(TAG, "install: zip missing meta.json")
            tmpDir.deleteRecursively()
            return null
        }
        val libName = runCatching {
            JSONObject(meta.readText()).optString("libraryName").ifEmpty { DEFAULT_LIBRARY_NAME }
        }.getOrDefault(DEFAULT_LIBRARY_NAME)
        if (!File(tmpDir, libName).exists()) {
            Log.w(TAG, "install: zip missing $libName")
            tmpDir.deleteRecursively()
            return null
        }

        if (targetDir.exists()) targetDir.deleteRecursively()
        if (!tmpDir.renameTo(targetDir)) {
            Log.w(TAG, "install: rename $tmpDir -> $targetDir failed")
            tmpDir.deleteRecursively()
            return null
        }
        File(targetDir, "cache").mkdirs()

        return listInstalled(context).firstOrNull { it.id == id }
    }

    fun applyToNative(context: Context, installed: InstalledDriver?) {
        if (installed == null) {
            NativeApp.setCustomVulkanDriver("", "", "", "")
            return
        }
        val driverDirPath = installed.driverDir.absolutePath + "/"
        val redirectDirPath = installed.redirectDir.apply { mkdirs() }.absolutePath + "/"
        val hookLibDir = context.applicationInfo.nativeLibraryDir
        NativeApp.setCustomVulkanDriver(
            driverDirPath, installed.libraryName, redirectDirPath, hookLibDir)
    }
}
