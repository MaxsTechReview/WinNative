package com.winlator.cmod.feature.retro

import android.content.Context

enum class RetroOnlineMode {
    NONE,
    DEV9_ONLINE,
    LIBRETRO_NETPLAY,
    GAME_LINK,
    DOLPHIN_NETPLAY,
}

object RetroOnlineSupport {
    fun modeFor(systemId: String?): RetroOnlineMode =
        when (systemId) {
            RetroSystems.PS2.id -> RetroOnlineMode.DEV9_ONLINE
            RetroSystems.NES.id,
            RetroSystems.SNES.id,
            RetroSystems.GENESIS.id,
            RetroSystems.MASTER_SYSTEM.id,
            RetroSystems.GAME_GEAR.id,
            RetroSystems.N64.id,
            RetroSystems.PSX.id,
            -> RetroOnlineMode.LIBRETRO_NETPLAY
            RetroSystems.GAMEBOY.id,
            RetroSystems.GAMEBOY_COLOR.id,
            RetroSystems.GBA.id,
            -> RetroOnlineMode.GAME_LINK
            RetroSystems.GAMECUBE.id,
            RetroSystems.WII.id,
            -> RetroOnlineMode.DOLPHIN_NETPLAY
            else -> RetroOnlineMode.NONE
        }

    fun isGbaNetpacket(systemId: String?): Boolean = systemId == RetroSystems.GBA.id

    fun supportsDev9(systemId: String?): Boolean = modeFor(systemId) == RetroOnlineMode.DEV9_ONLINE

    fun supportsNetplayCore(systemId: String?): Boolean =
        modeFor(systemId) == RetroOnlineMode.LIBRETRO_NETPLAY

    fun supportsGameLink(systemId: String?): Boolean =
        modeFor(systemId) == RetroOnlineMode.GAME_LINK

    fun supportsDolphinNetplay(systemId: String?): Boolean =
        modeFor(systemId) == RetroOnlineMode.DOLPHIN_NETPLAY

    fun supportsMultiplayerUi(systemId: String?): Boolean =
        supportsNetplayCore(systemId) || supportsGameLink(systemId)

    fun supportsNetplay(context: Context, systemId: String?): Boolean {
        if (!supportsNetplayCore(systemId) && !supportsGameLink(systemId)) return false
        val system = RetroSystems.fromId(systemId) ?: return false
        if (system.isExternal || system.coreFileName.isBlank()) return false
        return RetroCoreManager.isCoreAvailable(context, system)
    }

    fun netplayFrontendReady(): Boolean = true
}
