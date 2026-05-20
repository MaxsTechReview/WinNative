package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Canonical Steam environment variables Wine reads when launching a game
 * through lsteamclient.dll. Built per-launch from the running session
 * (steamID, account name) + the game we're about to start (appId, owned
 * DLC list). The returned map is meant to be merged into whatever env
 * the wine/box64 invocation already passes.
 *
 * Why each variable exists (compiled from Proton + lsteamclient.dll
 * sources):
 *
 *   WINESTEAMCLIENTPATH64
 *   WINESTEAMCLIENTPATH         absolute paths to libsteamclient.so —
 *                               lsteamclient.dll dlopens whichever the
 *                               game's bitness selects.
 *   Steam3Master                "host:port" of the SteamWorks main RPC
 *                               (our WnSteamSession.startWineBridge port).
 *   SteamClientService          same, for the secondary RPC.
 *   SteamUser / SteamAppUser    Steam login name — both names exist for
 *                               historical reasons; old code reads one,
 *                               new code reads the other.
 *   STEAMID                     SteamID64 as decimal string.
 *   SteamGameId / SteamAppId    AppID being launched.
 *   OWNED_DLCS                  comma-separated list of owned DLC appids
 *                               for the game — lsteamclient hands this
 *                               back from SteamApps()->BIsDlcInstalled.
 *   SteamPath                   Fake Windows Steam install path. Many
 *                               games hardcode "C:\\Program Files (x86)\\
 *                               Steam" for asset lookup; the path doesn't
 *                               need to exist, it just needs to be set.
 *   ValvePlatformMutex          Fake Wine-side mutex path (Steam's
 *                               singleton mutex). Same story — just a
 *                               marker for "Steam is running".
 *   SteamClientLaunch           "1" — signals lsteamclient that the launch
 *                               originated from a Steam client (vs the
 *                               game being started standalone).
 *   SteamEnv                    "1" — generic "this is a Steam env" flag.
 *   _STEAM_SETENV_MANAGER       "1" — tells Steam runtime not to scrub
 *                               our env on relaunch.
 *   STEAMVIDEOTOKEN             "1" — video/streaming feature gate.
 *   ENABLE_VK_LAYER_VALVE_steam_overlay_1 "0" — disable the Valve Vulkan
 *                               overlay (incompatible with our compositor).
 *   BREAKPAD_DUMP_LOCATION      where to dump crash minidumps; we use a
 *                               per-container subdir for cleanup.
 *   STEAM_BASE_FOLDER           Linux side of the Wine prefix's Steam root.
 */
object WnWineEnvVars {

    data class Inputs(
        /** SteamID64 (decimal). 0 = use whatever the session reports. */
        val steamId64: Long,
        /** Steam login name (account name from logon). */
        val accountName: String,
        /** App being launched. */
        val appId: Int,
        /** Owned DLC appids for [appId]. May be empty. */
        val ownedDlcAppIds: List<Int>,
        /** Linux-side Wine prefix Steam root (used for steamclient.so paths). */
        val steamRootLinux: String,
        /** Where Breakpad should write minidumps. */
        val breakpadDir: String,
        /** TCP "host:port" the WineBridge is listening on for Steam3Master. */
        val steam3MasterHostPort: String = "127.0.0.1:57343",
        /** Same for SteamClientService. */
        val steamClientServiceHostPort: String = "127.0.0.1:57344",
        /**
         * Absolute Linux filesystem path to libsteamclient.so. Wine's
         * lsteamclient.dll dlopens this path at init — has to be a file
         * that actually exists or Steam.exe crashes immediately (the
         * cause of 8b.8 launch failures was this var pointing at the
         * legacy <wineprefix>/Steam/linux64/ location which we don't
         * populate).
         */
        val libSteamClientSoPath: String,
    )

    fun build(input: Inputs): Map<String, String> {
        val v = LinkedHashMap<String, String>(24)
        // Both 32-bit and 64-bit Wine loaders look at the *_64 / no-suffix
        // variants. We only have a 64-bit Linux libsteamclient.so on Android,
        // so both env vars point at the same file. If a future build adds an
        // i386 libsteamclient.so we'll split these.
        v["WINESTEAMCLIENTPATH64"] = input.libSteamClientSoPath
        v["WINESTEAMCLIENTPATH"]   = input.libSteamClientSoPath
        v["_STEAM_SETENV_MANAGER"] = "1"
        v["BREAKPAD_DUMP_LOCATION"] = input.breakpadDir
        v["STEAM_BASE_FOLDER"]     = input.steamRootLinux
        v["ENABLE_VK_LAYER_VALVE_steam_overlay_1"] = "0"
        v["STEAMVIDEOTOKEN"]       = "1"
        v["Steam3Master"]          = input.steam3MasterHostPort
        v["SteamClientService"]    = input.steamClientServiceHostPort
        v["SteamUser"]             = input.accountName
        v["SteamAppUser"]          = input.accountName
        v["SteamClientLaunch"]     = "1"
        v["SteamEnv"]              = "1"
        v["SteamPath"]             = """C:\Program Files (x86)\Steam"""
        v["ValvePlatformMutex"]    = """c:\Program Files (x86)\Steam/"""
        v["STEAMID"]               = input.steamId64.toString()
        v["SteamGameId"]           = input.appId.toString()
        v["SteamAppId"]            = input.appId.toString()
        // Comma-separated, no spaces — lsteamclient.dll parses on ','.
        v["OWNED_DLCS"]            = input.ownedDlcAppIds.joinToString(",")
        return v
    }
}
