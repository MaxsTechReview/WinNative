package com.winlator.cmod.steam.events

import com.winlator.cmod.steam.data.SteamFriend
import com.winlator.cmod.steam.enums.LoginResult

sealed interface SteamEvent<T> : Event<T> {
    data class Connected(val isAutoLoggingIn: Boolean) : SteamEvent<Unit>
    data class LoggedOut(val username: String?) : SteamEvent<Unit>
    data class LogonEnded(val username: String?, val loginResult: LoginResult, val message: String? = null) :
        SteamEvent<Unit>
    data class LogonStarted(val username: String?) : SteamEvent<Unit>
    data class PersonaStateReceived(val persona: SteamFriend) : SteamEvent<Unit>
    data class QrAuthEnded(val success: Boolean, val message: String? = null) : SteamEvent<Unit>
    data class QrChallengeReceived(val challengeUrl: String) : SteamEvent<Unit>
    data object QrCodeScanned : SteamEvent<Unit>

    data object ForceCloseApp : SteamEvent<Unit>
    data object Disconnected : SteamEvent<Unit>
    data object RemotelyDisconnected : SteamEvent<Unit>
}
