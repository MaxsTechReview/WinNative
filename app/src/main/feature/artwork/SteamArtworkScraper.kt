package com.winlator.cmod.feature.artwork

import java.io.File
import java.io.IOException
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.core.net.toUri
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.winlator.cmod.runtime.content.Downloader

class SteamArtworkScraper(private val context: Context) : ArtworkScraper() {

    private val client = OkHttpClient()
    private val baseSteamArtworkUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"
    private suspend fun getGameId(gameName: String): Int? =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = String.format("""
                    {
                      "asset_type": "grid",
                      "term": "%s",
                      "offset": 0,
                      "filters": {
                        "styles": [
                          "all"
                        ],
                        "dimensions": [
                          "all"
                        ],
                        "type": [
                          "all"
                        ],
                        "order": "score_desc"
                      }
                    }
                """.trimIndent(), gameName)
                val request = Request.Builder()
                    .url("https://www.steamgriddb.com/api/public/search/main/games")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "WinNative/1.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Content-Type", "application/json")
                    .header("Referer", "https://www.steamgriddb.com/search/grids")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val json = JSONObject(response.body.string())
                    if (!json.getBoolean("success"))
                        throw IOException("Unexpected code $response")
                    val gameId = json.optJSONObject("data")?.optJSONArray("games")?.optJSONObject(0)?.optJSONObject("game")?.getInt("id")
                    gameId?.let {
                        return@withContext gameId
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    // Steam nests some filenames under image2x/image and stores others flat.
    private fun steamAssetUrl(metadata: JSONObject?, assetKey: String, baseUrl: String?): String? {
        if (metadata == null || baseUrl == null) return null
        val asset = metadata.optJSONObject(assetKey) ?: return null
        val filename = asset.optJSONObject("image2x")?.optString("english")?.ifBlank { null }
            ?: asset.optJSONObject("image")?.optString("english")?.ifBlank { null }
            ?: asset.optString("english").ifBlank { null }
            ?: return null
        return String.format("%s/%s", baseUrl, filename)
    }

    private fun uploadedAssetUrl(data: JSONObject?, key: String): String? =
        data?.optJSONObject(key)?.optJSONObject("asset")?.optString("url")?.ifBlank { null }

    // Community uploads, used when a title carries no official art of that shape.
    private fun uploadedAssets(gameId: Int): JSONObject? =
        try {
            val request = Request.Builder()
                .url(String.format("https://www.steamgriddb.com/api/public/game/%s/home", gameId.toString()))
                .header("User-Agent", "WinNative/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://www.steamgriddb.com/game/")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else JSONObject(response.body.string()).optJSONObject("data")
            }
        } catch (e: Exception) {
            null
        }

    private fun uploadedByShape(assets: JSONObject?, listKey: String, portrait: Boolean): String? {
        val list = assets?.optJSONArray(listKey) ?: return null
        for (i in 0 until list.length()) {
            val asset = list.optJSONObject(i) ?: continue
            if (asset.optBoolean("nsfw") || asset.optBoolean("humor")) continue
            val width = asset.optInt("width")
            val height = asset.optInt("height")
            if (width <= 0 || height <= 0) continue
            if (portrait != (height > width)) continue
            val url = asset.optString("url").ifBlank { null } ?: continue
            return url
        }
        return null
    }

    private suspend fun downloadGameAssets(gameName: String): MutableMap<String, File> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, File>()
            try {
                val gameId = getGameId(gameName) ?: return@withContext results
                val storageDir = File(context.cacheDir, "artwork_scrape").apply { if (!exists()) mkdirs() }
                val storagePath = File(storageDir, gameId.toString()).absolutePath
                val request = Request.Builder()
                    .url(String.format("https://www.steamgriddb.com/api/public/game/%s", gameId.toString()))
                    .header("User-Agent", "WinNative/1.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://www.steamgriddb.com/game/")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("TE", "trailers")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val json = JSONObject(response.body.string())
                    if (!json.getBoolean("success"))
                        throw IOException("Status not successful $response")
                    val data = json.optJSONObject("data")
                    val steam = data?.optJSONObject("platforms")?.optJSONObject("steam")
                    val steamGameId = steam?.optString("id")?.ifBlank { null }
                    val metadata = steam?.optJSONObject("metadata")
                    val steamArtworkUrl =
                        steamGameId?.let { String.format("%s/%s", baseSteamArtworkUrl, it) }

                    // Community uploads only cost a request when official art is missing.
                    var uploadedCache: JSONObject? = null
                    var uploadedFetched = false
                    fun uploaded(): JSONObject? {
                        if (!uploadedFetched) {
                            uploadedFetched = true
                            uploadedCache = uploadedAssets(gameId)
                        }
                        return uploadedCache
                    }

                    val urls = LinkedHashMap<String, String>()
                    // Background: wide banner.
                    val heroUrl = steamAssetUrl(metadata, "library_hero_full", steamArtworkUrl)
                        ?: uploadedByShape(uploaded(), "heroes", portrait = false)
                        ?: uploadedAssetUrl(data, "header")
                    if (heroUrl != null) urls["hero"] = heroUrl
                    // Carousel: tall capsule.
                    val carouselUrl = steamAssetUrl(metadata, "library_capsule_full", steamArtworkUrl)
                        ?: uploadedByShape(uploaded(), "grids", portrait = true)
                    if (carouselUrl != null) urls["carousel"] = carouselUrl
                    // Grid and list: wide header.
                    val headerUrl = steamAssetUrl(metadata, "header_image_full", steamArtworkUrl)
                        ?: uploadedByShape(uploaded(), "grids", portrait = false)
                        ?: uploadedAssetUrl(data, "header")
                    if (headerUrl != null) {
                        urls["grid"] = headerUrl
                        urls["list"] = headerUrl
                    }

                    urls.forEach { (key, url) ->
                        val name = url.substringAfterLast('/').substringBefore('?')
                        val fileHandle = File(String.format("%s_%s_%s", storagePath, key, name))
                        if (Downloader.downloadFile(url, fileHandle, null))
                            results[key] = fileHandle
                    }
                    return@withContext results
                }
            } catch (e: Exception) {
                return@withContext results
            }
        }

    override suspend fun getGameArtwork(gameName: String): MutableMap<String, File> =
        withContext(Dispatchers.IO) {
            return@withContext downloadGameAssets(gameName)
        }
    }
