package com.winlator.cmod.feature.artwork

import java.io.File
import java.io.IOException
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Environment
import androidx.core.net.toUri
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.winlator.cmod.runtime.content.Downloader

class SteamArtworkScraper() : ArtworkScraper() {

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
                    val gameId = json.optJSONObject("data")?.optJSONArray("games")?.getJSONObject(0)?.optJSONObject("game")?.getInt("id")
                    gameId?.let {
                        return@withContext gameId
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    private suspend fun downloadGameAssets(gameName: String): MutableMap<String, File> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, File>()
            try {
                val gameId = getGameId(gameName)
                val storagePath = String.format("%s/WinNative/%s", Environment.getExternalStorageDirectory().toString(), gameId)
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
                    val steamGameId = steam?.getString("id")
                    val metadata = steam?.optJSONObject("metadata")
                    metadata?.let {
                        val steamArtworkUrl =
                            String.format("%s/%s", baseSteamArtworkUrl, steamGameId)
                        val assets = mapOf(
                            "hero" to "library_hero_full",
                            "grid" to "library_capsule_full",
                            "carousel" to "header_image_full",
                            "list" to "header_image_full"
                        )
                        assets.forEach { (key, value) ->
                            var filename: String? = null
                            runCatching {
                                filename = metadata.optJSONObject(value)?.getString("english")
                            }.onFailure {
                                filename = metadata.optJSONObject(value)?.optJSONObject("image")
                                    ?.getString("english")
                            }
                            if (filename == null) return@forEach
                            val fileUrl = String.format("%s/%s", steamArtworkUrl, filename)
                            val fileHandle =
                                File(String.format("%s_%s", storagePath, filename.replace("/", "")))
                            if (Downloader.downloadFile(fileUrl, fileHandle, null))
                                results[key] = fileHandle
                        }
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