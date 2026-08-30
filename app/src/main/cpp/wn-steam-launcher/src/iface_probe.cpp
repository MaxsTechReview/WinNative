#include <windows.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

typedef bool (*SteamAPI_Init_t)();
typedef int (*SteamInternal_SteamAPI_Init_t)(const char*, char*);
typedef int (*SteamAPI_GetHSteamUser_t)();
typedef void* (*SteamInternal_FindOrCreateUserInterface_t)(int, const char*);
typedef void* (*SteamInternal_ContextInit_t)(void*);
typedef void (*SteamAPI_Shutdown_t)();

static FILE* g_log = nullptr;

static void plog(const char* fmt, ...) __attribute__((format(gnu_printf, 1, 2)));
static void plog(const char* fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    if (g_log) {
        fputs(buf, g_log);
        fputc('\n', g_log);
        fflush(g_log);
    }
}

static const char* const kInterfaces[] = {
    "STEAMAPPS_INTERFACE_VERSION008",
    "STEAMUSERSTATS_INTERFACE_VERSION012",
    "STEAMREMOTESTORAGE_INTERFACE_VERSION016",
    "STEAMUGC_INTERFACE_VERSION020",
    "STEAMHTTP_INTERFACE_VERSION003",
    "STEAMHTMLSURFACE_INTERFACE_VERSION_005",
    "STEAMSCREENSHOTS_INTERFACE_VERSION003",
    "STEAMREMOTEPLAY_INTERFACE_VERSION002",
    "STEAMMUSIC_INTERFACE_VERSION001",
    "STEAMMUSICREMOTE_INTERFACE_VERSION001",
    "STEAMPARENTALSETTINGS_INTERFACE_VERSION001",
    "STEAMUSER_INTERFACE_VERSION023",
    "STEAMFRIENDS_INTERFACE_VERSION017",
    "STEAMUTILS_INTERFACE_VERSION010",
    "STEAMNETWORKING_INTERFACE_VERSION006",
    "STEAMMATCHMAKING_INTERFACE_VERSION009",
};

int main(int argc, char** argv) {
    const char* gameDir = (argc > 1) ? argv[1] : "";
    const char* appId = (argc > 2) ? argv[2] : "";
    const char* logPath = (argc > 3) ? argv[3] : "C:\\wn-iface-probe.log";

    g_log = fopen(logPath, "w");
    plog("[wn-probe] start gameDir=\"%s\" appId=%s", gameDir, appId);

    if (appId && appId[0]) {
        SetEnvironmentVariableA("SteamAppId", appId);
        SetEnvironmentVariableA("SteamGameId", appId);
    }

    char dllPath[MAX_PATH];
    snprintf(dllPath, sizeof(dllPath), "%s\\steam_api64.dll", gameDir);
    HMODULE api = LoadLibraryA(dllPath);
    if (!api) {
        plog("[wn-probe] LoadLibrary(\"%s\") failed GLE=%lu; trying bare name",
             dllPath, (unsigned long) GetLastError());
        api = LoadLibraryA("steam_api64.dll");
    }
    if (!api) {
        plog("[wn-probe] FATAL: steam_api64.dll not loadable GLE=%lu",
             (unsigned long) GetLastError());
        return 2;
    }
    plog("[wn-probe] steam_api64.dll loaded at %p", (void*) api);

    SteamAPI_Init_t initLegacy =
        (SteamAPI_Init_t) GetProcAddress(api, "SteamAPI_Init");
    SteamInternal_SteamAPI_Init_t initInternal =
        (SteamInternal_SteamAPI_Init_t) GetProcAddress(api, "SteamInternal_SteamAPI_Init");
    SteamAPI_GetHSteamUser_t getUser =
        (SteamAPI_GetHSteamUser_t) GetProcAddress(api, "SteamAPI_GetHSteamUser");
    SteamInternal_FindOrCreateUserInterface_t findIface =
        (SteamInternal_FindOrCreateUserInterface_t)
            GetProcAddress(api, "SteamInternal_FindOrCreateUserInterface");
    SteamAPI_Shutdown_t shutdown =
        (SteamAPI_Shutdown_t) GetProcAddress(api, "SteamAPI_Shutdown");

    plog("[wn-probe] exports: SteamAPI_Init=%p SteamInternal_SteamAPI_Init=%p "
         "GetHSteamUser=%p FindOrCreateUserInterface=%p",
         (void*) initLegacy, (void*) initInternal, (void*) getUser, (void*) findIface);

    bool ok = false;
    if (initInternal) {
        char err[1024];
        memset(err, 0, sizeof(err));
        int rc = initInternal(nullptr, err);
        ok = (rc == 0);
        plog("[wn-probe] SteamInternal_SteamAPI_Init -> %d (%s) err=\"%s\"",
             rc, ok ? "OK" : "FAILED", err);
    } else if (initLegacy) {
        ok = initLegacy();
        plog("[wn-probe] SteamAPI_Init -> %s", ok ? "true" : "false");
    } else {
        plog("[wn-probe] FATAL: no init export found");
        return 3;
    }

    if (!ok) {
        plog("[wn-probe] SteamAPI init FAILED — this is what SteamAir would see");
        return 4;
    }

    int hUser = getUser ? getUser() : 0;
    plog("[wn-probe] HSteamUser=%d", hUser);

    if (!findIface) {
        plog("[wn-probe] FATAL: SteamInternal_FindOrCreateUserInterface missing");
        return 5;
    }

    int served = 0, missing = 0;
    for (size_t i = 0; i < sizeof(kInterfaces) / sizeof(kInterfaces[0]); ++i) {
        void* p = findIface(hUser, kInterfaces[i]);
        if (p) served++; else missing++;
        plog("[wn-probe] iface %-44s -> %s (%p)",
             kInterfaces[i], p ? "SERVED" : "*** NULL ***", p);
    }
    plog("[wn-probe] summary: served=%d missing=%d", served, missing);

    if (shutdown) shutdown();
    plog("[wn-probe] done");
    if (g_log) fclose(g_log);
    return 0;
}
