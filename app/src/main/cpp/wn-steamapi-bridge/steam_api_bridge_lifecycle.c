#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define WN_STEAMAPI_EXPORT __declspec(dllexport)

static HMODULE g_wine_bridge = NULL;
static HMODULE g_gbe = NULL;
static int32_t g_pipe = 0;
static int32_t g_user = 0;
static int g_inited = 0;
static void* g_steam_client = NULL;

typedef void* (*CreateInterface_fn)(const char*, int*);
static CreateInterface_fn g_CreateInterface = NULL;

static int wnb_logging_enabled(void) {
    static volatile LONG cached = -1;
    LONG v = cached;
    if (v == -1) {
        const char* e = getenv("WNB_LOG");
        v = (e && e[0] && e[0] != '0') ? 1 : 0;
        cached = v;
    }
    return (int)v;
}

static void wnb_log(const char* msg) {
    if (!wnb_logging_enabled()) return;
    FILE* f = fopen("C:\\wnb.log", "a");
    if (f) { fputs(msg, f); fputs("\n", f); fclose(f); }
}

static CRITICAL_SECTION g_init_cs;
static volatile LONG g_init_cs_state = 0;

static void init_lock(void) {
    if (InterlockedCompareExchange(&g_init_cs_state, 1, 0) == 0) {
        InitializeCriticalSection(&g_init_cs);
        InterlockedExchange(&g_init_cs_state, 2);
    }
    while (g_init_cs_state != 2) Sleep(0);
    EnterCriticalSection(&g_init_cs);
}

static void init_unlock(void) {
    LeaveCriticalSection(&g_init_cs);
}

static int load_wine_bridge(void) {
    if (g_wine_bridge != NULL) return g_steam_client != NULL;
    wnb_log("[lifecycle] load_wine_bridge: LoadLibrary(steamclient64.dll)");
    g_wine_bridge = LoadLibraryA("steamclient64.dll");
    if (!g_wine_bridge) {
        wnb_log("[lifecycle] LoadLibrary(steamclient64.dll) FAILED");
        return 0;
    }
    wnb_log("[lifecycle] load_wine_bridge: GetProcAddress(CreateInterface)");
    g_CreateInterface = (CreateInterface_fn)GetProcAddress(g_wine_bridge, "CreateInterface");
    if (!g_CreateInterface) {
        wnb_log("[lifecycle] steamclient64.dll missing CreateInterface");
        return 0;
    }
    char buf[160];
    snprintf(buf, sizeof(buf), "[lifecycle] CreateInterface fn=%p — calling SteamClient020",
             (void*)g_CreateInterface);
    wnb_log(buf);
    int code = 0;
    g_steam_client = g_CreateInterface("SteamClient020", &code);
    snprintf(buf, sizeof(buf), "[lifecycle] CreateInterface(SteamClient020) -> %p code=%d",
             g_steam_client, code);
    wnb_log(buf);
    if (!g_steam_client) {
        g_steam_client = g_CreateInterface("SteamClient019", &code);
        snprintf(buf, sizeof(buf), "[lifecycle] CreateInterface(SteamClient019) -> %p", g_steam_client);
        wnb_log(buf);
    }
    if (!g_steam_client) {
        g_steam_client = g_CreateInterface("SteamClient017", &code);
        snprintf(buf, sizeof(buf), "[lifecycle] CreateInterface(SteamClient017) -> %p", g_steam_client);
        wnb_log(buf);
    }
    if (!g_steam_client) {
        wnb_log("[lifecycle] all CreateInterface attempts NULL");
        return 0;
    }
    snprintf(buf, sizeof(buf),
             "[lifecycle] wine bridge init OK: client=%p (skipping CreateSteamPipe/ConnectToGlobalUser)",
             g_steam_client);
    wnb_log(buf);
    return 1;
}

static int gbe_init(void) {
    if (g_gbe != NULL) return 1;
    g_gbe = LoadLibraryA("original_steam_api64.dll");
    if (!g_gbe) {
        wnb_log("[lifecycle] LoadLibrary(original_steam_api64.dll) FAILED");
        return 0;
    }
    typedef int (*Init_fn)(void);
    Init_fn p = (Init_fn)GetProcAddress(g_gbe, "SteamAPI_Init");
    if (p) {
        int rc = p();
        char log[80];
        snprintf(log, sizeof(log), "[lifecycle] gbe SteamAPI_Init -> %d", rc);
        wnb_log(log);
        return rc;
    }
    return 0;
}

WN_STEAMAPI_EXPORT int SteamAPI_Init(void) {
    if (g_inited) return 1;
    init_lock();
    if (!g_inited) {
        wnb_log("[lifecycle] SteamAPI_Init called");
        gbe_init();
        g_inited = 1;
    }
    init_unlock();
    return 1;
}

WN_STEAMAPI_EXPORT int SteamAPI_InitSafe(void) { return SteamAPI_Init(); }

WN_STEAMAPI_EXPORT int SteamAPI_InitFlat(void* p_outErrMsg) {
    (void)p_outErrMsg;
    return SteamAPI_Init() ? 0 : 2;
}

WN_STEAMAPI_EXPORT void SteamAPI_Shutdown(void) {
    wnb_log("[lifecycle] SteamAPI_Shutdown");
    if (g_gbe) {
        typedef void (*Sht_fn)(void);
        Sht_fn p = (Sht_fn)GetProcAddress(g_gbe, "SteamAPI_Shutdown");
        if (p) p();
    }
    g_inited = 0;
}

WN_STEAMAPI_EXPORT int SteamAPI_IsSteamRunning(void) { return 1; }

WN_STEAMAPI_EXPORT int SteamAPI_GetHSteamPipe(void) {
    if (!g_inited) SteamAPI_Init();
    if (g_pipe != 0) return g_pipe;
    if (g_gbe) {
        typedef int (*P_fn)(void);
        P_fn p = (P_fn)GetProcAddress(g_gbe, "SteamAPI_GetHSteamPipe");
        if (p) return p();
    }
    return 0;
}

WN_STEAMAPI_EXPORT int SteamAPI_GetHSteamUser(void) {
    if (!g_inited) SteamAPI_Init();
    if (g_user != 0) return g_user;
    if (g_gbe) {
        typedef int (*P_fn)(void);
        P_fn p = (P_fn)GetProcAddress(g_gbe, "SteamAPI_GetHSteamUser");
        if (p) return p();
    }
    return 0;
}

WN_STEAMAPI_EXPORT int SteamAPI_RestartAppIfNecessary(uint32_t unOwnAppID) {
    (void)unOwnAppID;
    return 0;
}

extern void* get_our_matchmaking(void);
extern void* get_our_matchmaking_servers(void);

static void* WINAPI thunk_GetISteamMatchmaking(
        void* self, int hSteamUser, int hSteamPipe, const char* pchVersion) {
    (void)self; (void)hSteamUser; (void)hSteamPipe; (void)pchVersion;
    static int logged = 0;
    if (!logged) {
        wnb_log("[lifecycle] vtable thunk: GetISteamMatchmaking -> Valve client");
        logged = 1;
    }
    return get_our_matchmaking();
}

static void* WINAPI thunk_GetISteamMatchmakingServers(
        void* self, int hSteamUser, int hSteamPipe, const char* pchVersion) {
    (void)self; (void)hSteamUser; (void)hSteamPipe; (void)pchVersion;
    static int logged = 0;
    if (!logged) {
        wnb_log("[lifecycle] vtable thunk: GetISteamMatchmakingServers -> Valve client");
        logged = 1;
    }
    return get_our_matchmaking_servers();
}

static void hook_gbe_matchmaking_slots(void* gbe_client) {
    static int hooked = 0;
    if (hooked || gbe_client == NULL) return;

    void** vt = *(void***)gbe_client;
    void* orig10 = vt[10];
    void* orig11 = vt[11];

    DWORD old_prot = 0;
    if (!VirtualProtect(&vt[10], sizeof(void*) * 2,
                        PAGE_EXECUTE_READWRITE, &old_prot)) {
        wnb_log("[lifecycle] hook: VirtualProtect WRITE failed; cannot redirect matchmaking");
        return;
    }
    vt[10] = (void*)thunk_GetISteamMatchmaking;
    vt[11] = (void*)thunk_GetISteamMatchmakingServers;
    DWORD restored_prot = 0;
    VirtualProtect(&vt[10], sizeof(void*) * 2, old_prot, &restored_prot);

    char buf[160];
    snprintf(buf, sizeof(buf),
             "[lifecycle] hook: patched gbe ISteamClient vt[10] (was %p -> %p) "
             "vt[11] (was %p -> %p)",
             orig10, vt[10], orig11, vt[11]);
    wnb_log(buf);
    hooked = 1;
}

WN_STEAMAPI_EXPORT void* SteamClient(void) {
    if (!g_inited) SteamAPI_Init();

    if (g_gbe) {
        typedef void* (*SC_fn)(void);
        SC_fn p = (SC_fn)GetProcAddress(g_gbe, "SteamClient");
        if (p) {
            void* gbe_client = p();
            hook_gbe_matchmaking_slots(gbe_client);  /* one-shot */
            static int logged = 0;
            if (!logged) {
                wnb_log("[lifecycle] SteamClient() -> gbe (matchmaking slots patched to Valve)");
                logged = 1;
            }
            return gbe_client;
        }
    }
    return NULL;
}
