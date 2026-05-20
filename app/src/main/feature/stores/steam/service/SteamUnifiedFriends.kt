package com.winlator.cmod.feature.stores.steam.service

import com.winlator.cmod.feature.stores.steam.data.OwnedGames
import org.json.JSONArray
import timber.log.Timber

/**
 * Owned-games lookup. Phase 9: this used to wrap JavaSteam's `Player`
 * unified-messaging service; it now goes through the in-house C++
 * WN-Steam-Client ([SteamService.withWnSession] → Player.GetOwnedGames#1).
 *
 * The [service] parameter is kept for call-site compatibility but is no
 * longer needed — the C++ session is reached via the SteamService companion.
 */
class SteamUnifiedFriends(
    @Suppress("UNUSED_PARAMETER") service: SteamService,
) : AutoCloseable {

    override fun close() {}

    /**
     * Gets a list of games that the user owns. If the library is private, or
     * the request fails, the list is empty (mirrors the old JavaSteam path).
     *
     * @param steamID the user's full 64-bit SteamID.
     */
    suspend fun getOwnedGames(steamID: Long): List<OwnedGames> {
        val json = SteamService.withWnSession { session ->
            session.getOwnedGames(steamID)
        }
        if (json == null) {
            Timber.w("Unable to get owned games!")
            return emptyList()
        }
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                OwnedGames(
                    appId = o.optInt("appId"),
                    name = o.optString("name"),
                    playtimeTwoWeeks = o.optInt("playtimeTwoWeeks"),
                    playtimeForever = o.optInt("playtimeForever"),
                    imgIconUrl = o.optString("imgIconUrl"),
                    sortAs = o.optString("sortAs"),
                    rtimeLastPlayed = o.optInt("rtimeLastPlayed"),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing owned games")
            emptyList()
        }
    }
}
