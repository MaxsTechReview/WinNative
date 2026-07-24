package com.winlator.cmod.runtime.reshade

import android.content.Context
import android.util.Log
import com.winlator.cmod.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** One effect entry from reshade.json; [url] points at a .tzst that extracts to ReShade/<id>/. */
data class ReshadeCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val author: String,
    val license: String,
    val url: String,
    val fileSize: Long,
    val checksum: String,   // UPPERCASE MD5 of the .tzst (may be blank → no verification)
    val version: Int,
)

object ReshadeCatalog {
    private const val TAG = "ReshadeCatalog"
    private const val CACHE_FILE = "reshade_catalog.json"

    val url: String get() = BuildConfig.RESHADE_CATALOG_URL

    enum class Source { NETWORK, CACHE, NONE }
    data class Result(val entries: List<ReshadeCatalogEntry>, val source: Source)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    /** Network-first, then the offline cache; NONE means neither was available. */
    fun loadCached(context: Context): Result {
        val json = downloadString(url)
        if (json != null) {
            val parsed = parse(json)
            if (parsed.isNotEmpty()) {
                runCatching { cacheFile(context).writeText(json) }
                    .onFailure { Log.w(TAG, "failed to cache catalog", it) }
                return Result(parsed, Source.NETWORK)
            }
        }
        val cache = cacheFile(context)
        if (cache.isFile) {
            val cached = runCatching { parse(cache.readText()) }.getOrNull()
            if (!cached.isNullOrEmpty()) return Result(cached, Source.CACHE)
        }
        return Result(emptyList(), Source.NONE)
    }

    private fun downloadString(target: String): String? = try {
        val req = Request.Builder().url(target).build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (t: Throwable) {
        Log.w(TAG, "catalog fetch failed: $target", t)
        null
    }

    private fun parse(json: String): List<ReshadeCatalogEntry> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val mirrorBase = root.optString("mirrorBase")
        val arr = root.optJSONArray("effects") ?: return emptyList()
        val out = ArrayList<ReshadeCatalogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { o.optString("name") }.trim()
            if (id.isEmpty()) continue
            val effectUrl = o.optString("url").ifBlank {
                if (mirrorBase.isBlank()) "" else mirrorBase.trimEnd('/') + "/" + id + ".tzst"
            }
            if (effectUrl.isEmpty()) continue
            out.add(
                ReshadeCatalogEntry(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    description = o.optString("description"),
                    category = o.optString("category").ifBlank { "Other" },
                    author = o.optString("author"),
                    license = o.optString("license"),
                    url = effectUrl,
                    fileSize = o.optString("file_size").toLongOrNull() ?: o.optLong("file_size", 0L),
                    checksum = o.optString("file_checksum").trim().uppercase(),
                    version = o.optInt("version", 1),
                )
            )
        }
        return out
    }
}
