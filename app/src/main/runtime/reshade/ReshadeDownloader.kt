package com.winlator.cmod.runtime.reshade

import android.content.Context
import android.util.Log
import com.winlator.cmod.shared.io.TarCompressorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Downloads a catalog effect archive into the drop-in folder, where ReshadeManager's scanner picks it up. */
object ReshadeDownloader {
    private const val TAG = "ReshadeDownloader"

    enum class Phase { DOWNLOAD, EXTRACT }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Download + verify + extract [entry] into the drop-in folder. progress(phase, 0..1). Runs on IO. */
    suspend fun install(
        context: Context,
        entry: ReshadeCatalogEntry,
        progress: (Phase, Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "reshade_dl").apply { mkdirs() }
        val archive = File(cacheDir, "${safe(entry.id)}.tzst")
        try {
            if (!download(entry.url, archive) { f -> progress(Phase.DOWNLOAD, f.coerceIn(0f, 1f)) }) {
                Log.w(TAG, "download failed: ${entry.url}")
                return@withContext false
            }
            if (entry.checksum.isNotBlank()) {
                val actual = md5Upper(archive)
                if (!actual.equals(entry.checksum, ignoreCase = true)) {
                    Log.w(TAG, "checksum mismatch for ${entry.id}: expected ${entry.checksum} got $actual")
                    return@withContext false
                }
            }
            progress(Phase.EXTRACT, 0f)
            // The tar carries "<id>/...", so extract straight into the ReShade root → ReShade/<id>/.
            val reshadeRoot = ReshadeManager.getReshadeDir(context)
            File(reshadeRoot, entry.id).takeIf { it.isDirectory }?.deleteRecursively()
            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, archive, reshadeRoot)) {
                Log.w(TAG, "extract failed: $archive")
                return@withContext false
            }
            progress(Phase.EXTRACT, 1f)
            val ok = ReshadeManager.findFxFile(File(reshadeRoot, entry.id)) != null
            if (!ok) Log.w(TAG, "no .fx after install for ${entry.id}")
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "install failed for ${entry.id}", t)
            false
        } finally {
            archive.delete()
        }
    }

    private fun download(target: String, dest: File, onProgress: (Float) -> Unit): Boolean {
        val req = Request.Builder().url(target).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body ?: return false
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
        onProgress(1f)
        return dest.isFile && dest.length() > 0
    }

    private fun md5Upper(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02X".format(it) }
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
