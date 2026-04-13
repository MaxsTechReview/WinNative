package com.winlator.cmod.feature.stores.steam.service
import com.winlator.cmod.feature.stores.steam.data.OwnedGames
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPlayerSteamclient
import `in`.dragonbra.javasteam.rpc.service.Player
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import kotlinx.coroutines.future.await
import timber.log.Timber

class SteamUnifiedFriends(
    service: SteamService,
) : AutoCloseable {
    private var unifiedMessages: SteamUnifiedMessages? = null

    private var player: Player? = null

    init {
        unifiedMessages = service.steamClient!!.getHandler<SteamUnifiedMessages>()

        player = unifiedMessages!!.createService(Player::class.java)
    }

    override fun close() {
        unifiedMessages = null
        player = null
    }

    /**
     * Gets a list of games that the user owns. If the library is private, it will be empty.
     */
    suspend fun getOwnedGames(steamID: Long): List<OwnedGames> {
        val request =
            SteammessagesPlayerSteamclient.CPlayer_GetOwnedGames_Request
                .newBuilder()
                .apply {
                    steamid = steamID
                    includePlayedFreeGames = true
                    includeFreeSub = true
                    includeAppinfo = true
                    includeExtendedAppinfo = true
                }.build()

        val result =
            try {
                player?.getOwnedGames(request)?.await()
            } catch (e: Exception) {
                Timber.e(e, "Error getting owned games")
                null
            }

        if (result == null || result.result != EResult.OK) {
            Timber.w("Unable to get owned games!")
            return emptyList()
        }

        val list =
            result.body.gamesList.map { game ->
                OwnedGames(
                    appId = game.appid,
                    name = game.name,
                    playtimeTwoWeeks = game.playtime2Weeks,
                    playtimeForever = game.playtimeForever,
                    imgIconUrl = game.imgIconUrl,
                    sortAs = game.sortAs,
                    rtimeLastPlayed = game.rtimeLastPlayed,
                )
            }

        if (list.size != result.body.gamesCount) {
            Timber.w("List was not the same as given")
        }

        return list
    }
}
