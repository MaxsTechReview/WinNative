package com.winlator.cmod.runtime.content

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.shared.io.NativeContentIO
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Native-backed component download helper with WinNative.dev-first URL resolution.
 *
 * Large file transfers use [NativeContentIO] / libcurl. OkHttp is retained only for small metadata
 * requests such as remote JSON and WinNative.dev directory listings.
 */
object Downloader {
    private const val TAG = "Downloader"
    private const val WINNATIVE_ROOT = "https://WinNative.dev/Downloads/"
    private const val MAX_CRAWL_DEPTH = 10
    private const val FILE_MAP_CACHE_NAME = "winnative_file_map_v1.txt"
    private const val FILE_MAP_CACHE_HEADER_PREFIX = "# timestamp="
    private const val FILE_MAP_CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    private const val NATIVE_CA_BUNDLE_NAME = "native_curl_cacert.pem"
    private const val SYSTEM_CA_DIR = "/system/etc/security/cacerts"

    private val metadataClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private val hrefPattern = Pattern.compile("href=\"([^\"?]+)\"")
    private val fileMap = ConcurrentHashMap<String, String>()
    private val mapLock = Any()

    @Volatile private var fileMapReady = false
    @Volatile private var logEnabledCached = false
    @Volatile private var logEnabledResolved = false
    @Volatile private var nativeCaBundlePath: String? = null

    fun interface DownloadListener {
        fun onProgress(
            downloadedBytes: Long,
            totalBytes: Long,
        )
    }

    @JvmStatic
    fun downloadFileWinNativeFirst(
        originalUrl: String,
        file: File,
        listener: DownloadListener?,
    ): Boolean {
        val filename = extractFilename(originalUrl)
        if (filename != null) {
            ensureFileMap()
            val winUrl = fileMap[filename.lowercase(Locale.ROOT)]
            if (winUrl != null) {
                if (logEnabled()) Log.d(TAG, "WinNative URL resolved: $winUrl")
                if (downloadFile(winUrl, file, listener)) {
                    if (logEnabled()) Log.d(TAG, "Download succeeded from WinNative.dev")
                    return true
                }
                if (logEnabled()) Log.w(TAG, "WinNative download failed, falling back to: $originalUrl")
                file.delete()
            } else if (logEnabled()) {
                Log.d(TAG, "File not found on WinNative.dev, using original: $originalUrl")
            }
        }
        return downloadFile(originalUrl, file, listener)
    }

    @JvmStatic
    fun resolveWinNativeUrl(filename: String?): String? {
        if (filename == null) return null
        ensureFileMap()
        return fileMap[filename.lowercase(Locale.ROOT)]
    }

    @JvmStatic
    fun resolveWinNativeUrl(contentType: String?, filename: String?): String? = resolveWinNativeUrl(filename)

    @JvmStatic
    fun clearFileMap() {
        synchronized(mapLock) {
            fileMap.clear()
            fileMapReady = false
            fileMapCacheFile()?.let {
                if (it.exists() && !it.delete() && logEnabled()) {
                    Log.w(TAG, "Unable to delete WinNative file map cache: ${it.absolutePath}")
                }
            }
        }
    }

    @JvmStatic
    @Deprecated("Use clearFileMap() instead.", ReplaceWith("clearFileMap()"))
    fun clearDirectoryCache() = clearFileMap()

    @JvmStatic
    fun ensureFileMap() {
        if (fileMapReady) return
        synchronized(mapLock) {
            if (fileMapReady) return
            refreshLogEnabled()
            if (!loadFileMapFromDisk()) {
                buildFileMap()
                persistFileMap()
            }
            fileMapReady = true
        }
    }

    private fun extractFilename(url: String?): String? {
        if (url == null) return null
        return try {
            val path = URL(url).path
            path.substringAfterLast('/').takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            val pathOnly = url.substringBefore('?')
            pathOnly.substringAfterLast('/').takeIf { it.isNotEmpty() }
        }
    }

    @JvmStatic
    fun downloadFile(
        address: String,
        file: File,
        listener: DownloadListener?,
    ): Boolean {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                if (logEnabled()) Log.w(TAG, "Unable to create download directory: ${parent.absolutePath}")
                return false
            }

            if (NativeContentIO.downloadFile(address, file, ensureNativeCaBundle(), listener)) {
                return true
            }
        } catch (e: Throwable) {
            if (logEnabled()) Log.w(TAG, "Download failed for $address", e)
        }

        if (file.exists() && !file.delete() && logEnabled()) {
            Log.w(TAG, "Unable to delete partial download: ${file.absolutePath}")
        }
        return false
    }

    @JvmStatic
    fun fetchContentLength(address: String?): Long {
        if (address.isNullOrEmpty()) return -1L
        return try {
            NativeContentIO.fetchContentLength(address, ensureNativeCaBundle())
        } catch (e: Throwable) {
            if (logEnabled()) Log.w(TAG, "Native HEAD failed for $address", e)
            -1L
        }
    }

    @JvmStatic
    fun downloadString(address: String): String? {
        val request = Request.Builder().url(address).build()
        return try {
            metadataClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} for $address")
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            if (logEnabled()) Log.w(TAG, "String download failed for $address", e)
            null
        }
    }

    private fun buildFileMap() {
        if (logEnabled()) Log.d(TAG, "Building WinNative file map from $WINNATIVE_ROOT")
        val start = System.currentTimeMillis()
        fileMap.clear()
        crawlDirectory(WINNATIVE_ROOT, 0)
        if (logEnabled()) {
            Log.d(TAG, "WinNative file map built: ${fileMap.size} files in ${System.currentTimeMillis() - start}ms")
        }
    }

    private fun crawlDirectory(
        dirUrl: String,
        depth: Int,
    ) {
        if (depth > MAX_CRAWL_DEPTH) return
        val html = downloadString(dirUrl) ?: return
        val matcher = hrefPattern.matcher(html)
        val subdirs = ArrayList<String>()

        while (matcher.find()) {
            val href = matcher.group(1) ?: continue
            if (href.startsWith("/") || href.startsWith("..") || href.startsWith("?") || href.startsWith("http")) {
                continue
            }

            if (href.endsWith("/")) {
                subdirs.add(dirUrl + href)
            } else {
                fileMap.putIfAbsent(href.lowercase(Locale.ROOT), dirUrl + href)
            }
        }

        subdirs.forEach { crawlDirectory(it, depth + 1) }
    }

    private fun loadFileMapFromDisk(): Boolean {
        val cacheFile = fileMapCacheFile()
        if (cacheFile?.isFile != true) return false

        fileMap.clear()
        try {
            BufferedReader(InputStreamReader(FileInputStream(cacheFile), StandardCharsets.UTF_8)).use { reader ->
                val header = reader.readLine()
                if (header == null || !header.startsWith(FILE_MAP_CACHE_HEADER_PREFIX)) return false

                val timestamp = header.removePrefix(FILE_MAP_CACHE_HEADER_PREFIX).trim().toLong()
                if (System.currentTimeMillis() - timestamp > FILE_MAP_CACHE_TTL_MS) {
                    if (!cacheFile.delete() && logEnabled()) {
                        Log.w(TAG, "Unable to delete expired WinNative file map cache")
                    }
                    return false
                }

                reader.lineSequence().forEach { line ->
                    val separator = line.indexOf('\t')
                    if (separator > 0 && separator < line.length - 1) {
                        fileMap.putIfAbsent(line.substring(0, separator), line.substring(separator + 1))
                    }
                }
            }
        } catch (e: Exception) {
            if (logEnabled()) Log.w(TAG, "Failed to load WinNative file map cache", e)
            fileMap.clear()
            return false
        }

        if (fileMap.isEmpty()) return false
        if (logEnabled()) Log.d(TAG, "Loaded WinNative file map cache: ${fileMap.size} files")
        return true
    }

    private fun persistFileMap() {
        if (fileMap.isEmpty()) return
        val cacheFile = fileMapCacheFile() ?: return
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")

        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(tempFile), StandardCharsets.UTF_8)).use { writer ->
                writer.write(FILE_MAP_CACHE_HEADER_PREFIX)
                writer.write(System.currentTimeMillis().toString())
                writer.newLine()

                fileMap.forEach { (key, value) ->
                    if (value.isNotEmpty()) {
                        writer.write(key)
                        writer.write('\t'.code)
                        writer.write(value)
                        writer.newLine()
                    }
                }
            }
        } catch (e: Exception) {
            if (logEnabled()) Log.w(TAG, "Failed to persist WinNative file map cache", e)
            tempFile.delete()
            return
        }

        if (cacheFile.exists() && !cacheFile.delete() && logEnabled()) {
            Log.w(TAG, "Unable to replace existing WinNative file map cache")
        }
        if (!tempFile.renameTo(cacheFile) && logEnabled()) {
            Log.w(TAG, "Unable to finalize WinNative file map cache write")
        }
    }

    private fun ensureNativeCaBundle(): String {
        nativeCaBundlePath?.let { return it }

        synchronized(this) {
            nativeCaBundlePath?.let { return it }
            val context = appContext()
            if (context == null) {
                nativeCaBundlePath = ""
                return ""
            }

            val out = File(context.filesDir, NATIVE_CA_BUNDLE_NAME)
            if (out.isFile && out.length() > 1024L) {
                nativeCaBundlePath = out.absolutePath
                return out.absolutePath
            }

            val certs = File(SYSTEM_CA_DIR).listFiles { file -> file.isFile && file.name.endsWith(".0") }
            if (certs.isNullOrEmpty()) {
                if (logEnabled()) Log.w(TAG, "No Android CA certificates found for native curl")
                nativeCaBundlePath = ""
                return ""
            }

            val tmp = File(out.parentFile, "${out.name}.tmp")
            try {
                BufferedWriter(OutputStreamWriter(FileOutputStream(tmp), StandardCharsets.UTF_8)).use { writer ->
                    certs.forEach { cert ->
                        try {
                            BufferedReader(InputStreamReader(FileInputStream(cert), StandardCharsets.UTF_8)).use { reader ->
                                reader.lineSequence().forEach { line ->
                                    writer.write(line)
                                    writer.newLine()
                                }
                                writer.newLine()
                            }
                        } catch (e: Exception) {
                            if (logEnabled()) Log.w(TAG, "Skipped CA certificate: ${cert.name}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (logEnabled()) Log.w(TAG, "Failed to create native curl CA bundle", e)
                tmp.delete()
                nativeCaBundlePath = ""
                return ""
            }

            if (out.exists() && !out.delete() && logEnabled()) {
                Log.w(TAG, "Unable to replace native curl CA bundle")
            }
            nativeCaBundlePath = if (tmp.renameTo(out)) out.absolutePath else ""
            return nativeCaBundlePath.orEmpty()
        }
    }

    private fun refreshLogEnabled() {
        logEnabledResolved = false
    }

    private fun logEnabled(): Boolean {
        if (logEnabledResolved) return logEnabledCached
        return try {
            val context = appContext() ?: return false
            logEnabledCached =
                PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean("enable_download_logs", false)
            logEnabledResolved = true
            logEnabledCached
        } catch (_: Exception) {
            false
        }
    }

    private fun fileMapCacheFile(): File? = appContext()?.let { File(it.filesDir, FILE_MAP_CACHE_NAME) }

    private fun appContext(): Context? =
        try {
            PluviaApp.instance.applicationContext
        } catch (_: Exception) {
            null
        }
}
