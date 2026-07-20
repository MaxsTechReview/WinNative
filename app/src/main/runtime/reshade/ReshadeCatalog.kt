package com.winlator.cmod.runtime.reshade

import android.content.Context
import android.util.Log
import com.winlator.cmod.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * One downloadable ReShade effect from the LIVE catalog (reshade.json). The catalog index lives on the
 * winlator-contents repo (raw file); each effect's archive is a GitHub RELEASE ASSET referenced by the
 * entry's explicit "url" field. The index URL is not hardcoded here — it comes from
 * BuildConfig.RESHADE_CATALOG_URL so a flavor/branch can repoint it without a code change.
 *
 * Each archive is a zstd-compressed tar (.tzst) whose tar contains the folder "<id>/..." (the .fx plus
 * its co-located .fxh includes and any textures). It extracts into the ReShade ROOT drop-in folder
 * (getReshadeDir → getExternalFilesDir/ReShade/) → yielding ReShade/<id>/, the exact dir
 * ReshadeManager's scanner reads. [id] is the drop-in subfolder name (uniqueness key).
 *
 * SCOPE: the catalog only adds BROWSE + DOWNLOAD of effect FOLDERS. Per-uniform parameter tuning and
 * the in-game toggle still need the live-reload patched vkBasalt layer (follow-up PR) — see
 * ReshadeConfigWriter's STOCK-LAYER SCOPE note.
 *
 * LIVE reshade.json SCHEMA (exact published field names):
 *
 *   {
 *     "schemaVersion": 1,
 *     "mirrorBase": "https://github.com/.../releases/download/reshade-v1/",
 *     "effects": [
 *       {
 *         "id": "Technicolor",            // drop-in subfolder name (uniqueness key)
 *         "name": "Technicolor",          // display label
 *         "description": "Technicolor — prod80",
 *         "category": "Color/Tone",
 *         "author": "prod80",
 *         "license": "MIT",
 *         "url": "https://github.com/.../releases/download/reshade-v1/Technicolor.tzst",
 *         "file_size": "5332",            // bytes, as a string
 *         "file_checksum": "5532...AB",   // UPPERCASE MD5 of the .tzst — verified after download
 *         "version": 1
 *       }
 *     ]
 *   }
 */
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

    /** Index URL is build-config driven (per-flavor/branch repointable), not a hardcoded const. */
    val url: String get() = BuildConfig.RESHADE_CATALOG_URL

    /** Where the catalog list came from, so the UI can tell the user whether it's live or cached. */
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

    /** Network-first, then offline cache. On a successful fetch the raw JSON is cached to
     *  filesDir/reshade_catalog.json for next time. On network failure the cached JSON (if any) is
     *  parsed instead, so the full list still renders offline. Returns NONE (empty) only when there's
     *  neither network nor a cache — the picker then falls back to scanning the drop-in folder. */
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
            // Prefer the explicit url; fall back to mirrorBase + id + ".tzst".
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
