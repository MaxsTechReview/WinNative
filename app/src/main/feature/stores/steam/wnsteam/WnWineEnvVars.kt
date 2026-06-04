package com.winlator.cmod.feature.stores.steam.wnsteam

// Builds the Steam launch environment consumed by Wine's lsteamclient.dll.
object WnWineEnvVars {

    data class Inputs(
        val steamId64: Long,
        val accountName: String,
        val appId: Int,
        val ownedDlcAppIds: List<Int>,
        val steamRootLinux: String,
        val breakpadDir: String,
        val steam3MasterHostPort: String = "127.0.0.1:57343",
        val steamClientServiceHostPort: String = "127.0.0.1:57344",
        // Absolute Linux path to libsteamclient.so for lsteamclient.dll.
        val libSteamClientSoPath: String,
    )

    fun build(input: Inputs): Map<String, String> {
        val v = LinkedHashMap<String, String>(24)
        // Both loader variants use the same Android libsteamclient.so.
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
        // lsteamclient.dll parses this on commas.
        v["OWNED_DLCS"]            = input.ownedDlcAppIds.joinToString(",")
        return v
    }
}
