
#include <windows.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>

#define WN_STEAMAPI_EXPORT __declspec(dllexport)

extern void* get_our_matchmaking(void);
extern void* get_our_matchmaking_servers(void);

static int g_logged_matchmaking = 0;
static int g_logged_matchmaking_servers = 0;
static void wnb_marker(const char* msg) {
    FILE* f = fopen("C:\\wnb.log", "a");
    if (f) { fputs(msg, f); fputs("\n", f); fclose(f); }
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamMatchmaking(
        void* instancePtr,
        int hSteamUser,
        int hSteamPipe,
        const char* pchVersion) {
    (void)instancePtr; (void)hSteamUser; (void)hSteamPipe; (void)pchVersion;
    if (!g_logged_matchmaking) {
        wnb_marker("SteamAPI_ISteamClient_GetISteamMatchmaking: flat-C hook -> libsteamclient.so");
        g_logged_matchmaking = 1;
    }
    return get_our_matchmaking();
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamMatchmakingServers(
        void* instancePtr,
        int hSteamUser,
        int hSteamPipe,
        const char* pchVersion) {
    (void)instancePtr; (void)hSteamUser; (void)hSteamPipe; (void)pchVersion;
    if (!g_logged_matchmaking_servers) {
        wnb_marker("SteamAPI_ISteamClient_GetISteamMatchmakingServers: flat-C hook -> libsteamclient.so");
        g_logged_matchmaking_servers = 1;
    }
    return get_our_matchmaking_servers();
}

WN_STEAMAPI_EXPORT void* SteamMatchmaking(void) {
    static int logged = 0;
    if (!logged) {
        wnb_marker("SteamMatchmaking(): bare global -> Steam Launcher Valve client");
        logged = 1;
    }
    return get_our_matchmaking();
}

WN_STEAMAPI_EXPORT void* SteamMatchmakingServers(void) {
    static int logged = 0;
    if (!logged) {
        wnb_marker("SteamMatchmakingServers(): bare global -> Steam Launcher Valve client");
        logged = 1;
    }
    return get_our_matchmaking_servers();
}

WN_STEAMAPI_EXPORT void* SteamAPI_SteamMatchmaking_v009(void) {
    return get_our_matchmaking();
}

WN_STEAMAPI_EXPORT void* SteamAPI_SteamMatchmakingServers_v002(void) {
    return get_our_matchmaking_servers();
}
