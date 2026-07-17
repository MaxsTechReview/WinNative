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
    private val steamArtworkUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/"

    private suspend fun downloadGameAssets(gameId: Int): GameArtworkInfo? =
        withContext(Dispatchers.IO) {
            try {
                Environment.getDownloadCacheDirectory()
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
                    val gameName = json.optJSONObject("data")?.optJSONObject("game")?.getString("name") ?: ""

                    metadata?.let {
                        val gameListFilename = metadata.optJSONObject("header_image_full")?.getString("english")
                        var gameCardFilename = metadata.optJSONObject("library_hero_full")?.optJSONObject("image2x")?.getString("english")
                        if (gameCardFilename == null) {
                            gameCardFilename = metadata.optJSONObject("library_hero_full")?.optJSONObject("image")?.getString("english")
                        }
                        var gameGridFilename = metadata.optJSONObject("library_capsule_full")?.optJSONObject("image2x")?.getString("english")
                        if (gameGridFilename == null) {
                            gameGridFilename = metadata.optJSONObject("library_capsule_full")?.optJSONObject("image")?.getString("english")
                        }

                        val gameCardUrl = String.format("%s/%s/%s", steamArtworkUrl, steamGameId, gameCardFilename)
                        val gameListUrl = String.format("%s/%s/%s", steamArtworkUrl, steamGameId, gameListFilename)
                        val gameGridUrl = String.format("%s/%s/%s", steamArtworkUrl, steamGameId, gameGridFilename)

                        val cardFile = File(String.format("%s_%s", storagePath, gameCardFilename))
                        val gridFile = File(String.format("%s_%s", storagePath, gameGridFilename))
                        val listFile = File(String.format("%s_%s", storagePath, gameListFilename))

                        val cardDownload = Downloader.downloadFile(gameCardUrl, cardFile, null)
                        val gridDownload = Downloader.downloadFile(gameListUrl, listFile, null)
                        val listDownload = Downloader.downloadFile(gameGridUrl, gridFile, null)

                        if (gridDownload && listDownload && cardDownload) {
                            return@withContext GameArtworkInfo(
                                gameId,
                                gameName,
                                "steam",
                                cardFile,
                                gridFile,
                                gridFile,
                                listFile
                            )
                        }
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

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

    override suspend fun getGameArtwork(gameName: String): GameArtworkInfo? =
        withContext(Dispatchers.IO) {
            try {
                val gameId = getGameId(gameName)
                gameId?.let {
                    return@withContext downloadGameAssets(gameId)
                }
            } catch (e: Exception) {
                null
            }
        }
    }