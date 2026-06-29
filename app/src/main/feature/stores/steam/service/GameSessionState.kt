package com.winlator.cmod.feature.stores.steam.service

import android.content.Context
import com.winlator.cmod.feature.stores.steam.chat.ChatOverlayService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager

object GameSessionState {
    @JvmStatic
    @Volatile
    var inGame: Boolean = false
        private set

    @JvmStatic
    fun setInGame(context: Context, value: Boolean) {
        inGame = value
        runCatching {
            if (value && !PrefManager.chatInGameEnabled) {
                ChatOverlayService.stop(context)
            } else {
                ChatOverlayService.start(context)
            }
        }
    }
}
