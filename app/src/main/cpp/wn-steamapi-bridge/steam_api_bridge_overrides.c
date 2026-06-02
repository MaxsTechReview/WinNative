
#include <windows.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>

#define WN_STEAMAPI_EXPORT __declspec(dllexport)

static void wnb_log_once(const char* name) {
    static const char* once_names[64];
    static int once_count = 0;
    for (int i = 0; i < once_count; ++i) {
        if (once_names[i] == name) return;
    }
    if (once_count < 64) {
        once_names[once_count++] = name;
    }
    FILE* f = fopen("C:\\wnb.log", "a");
    if (f) {
        fputs(name, f);
        fputc('\n', f);
        fclose(f);
    }
}

typedef void* (*CreateInterface_fn)(const char* pchVersion, int* pCode);
static CreateInterface_fn g_create_interface = NULL;
static void* g_steam_client = NULL;
static HMODULE g_steamclient_module = NULL;

typedef unsigned char (*Steam_BGetCallback_fn)(int hpipe, void* pmsg);
typedef void          (*Steam_FreeLastCallback_fn)(int hpipe);
typedef unsigned char (*Steam_GetAPICallResult_fn)(int hpipe,
                                          unsigned long long hcall,
                                          void* pcb, int cb,
                                          int icb_expected,
                                          unsigned char* pbfailed);
static Steam_BGetCallback_fn      g_steam_bgetcallback     = NULL;
static Steam_FreeLastCallback_fn  g_steam_freelastcallback = NULL;
static Steam_GetAPICallResult_fn  g_steam_getapicallresult = NULL;

extern void wnb_dispatch_callback(int iCallback, const void* data, size_t data_size);
extern void wnb_dispatch_call_result(unsigned long long hAPICall, int io_failure,
                                     const void* data, size_t data_size);
static int g_steam_pipe = 0;
static int g_steam_user = 0;

extern void wnb_publish_dispatch_pointers(void);

static void wnb_resolver_log(const char* msg) {
    FILE* f = fopen("C:\\wnb.log", "a");
    if (f) { fputs(msg, f); fputc('\n', f); fclose(f); }
}

static void* resolve_steam_client(void) {
    if (g_steam_client != NULL) return g_steam_client;
    wnb_publish_dispatch_pointers();
    SetDllDirectoryA("C:\\Program Files (x86)\\Steam");
    HMODULE sc = LoadLibraryExA(
            "C:\\Program Files (x86)\\Steam\\steamclient64.dll",
            NULL, LOAD_WITH_ALTERED_SEARCH_PATH);
    if (sc == NULL) {
        wnb_resolver_log("[wnb] LoadLibraryEx(Valve steamclient64.dll) "
                         "failed — falling back to bare name (gbe stub)");
        sc = LoadLibraryA("steamclient64.dll");
    }
    if (sc == NULL) {
        wnb_resolver_log("[wnb] LoadLibrary(steamclient64.dll) failed");
        return NULL;
    }
    g_steamclient_module = sc;
    if (g_create_interface == NULL) {
        g_create_interface = (CreateInterface_fn)GetProcAddress(sc, "CreateInterface");
        if (g_create_interface == NULL) {
            wnb_resolver_log("[wnb] steamclient64.dll missing CreateInterface");
            return NULL;
        }
    }
    g_steam_bgetcallback = (Steam_BGetCallback_fn)
            GetProcAddress(sc, "Steam_BGetCallback");
    g_steam_freelastcallback = (Steam_FreeLastCallback_fn)
            GetProcAddress(sc, "Steam_FreeLastCallback");
    g_steam_getapicallresult = (Steam_GetAPICallResult_fn)
            GetProcAddress(sc, "Steam_GetAPICallResult");
    {
        char buf[160];
        snprintf(buf, sizeof(buf),
                 "[wnb] callback-pump exports: BGetCallback=%p "
                 "FreeLastCallback=%p GetAPICallResult=%p",
                 (void*)g_steam_bgetcallback,
                 (void*)g_steam_freelastcallback,
                 (void*)g_steam_getapicallresult);
        wnb_resolver_log(buf);
    }
    int code = 0;
    g_steam_client = g_create_interface("SteamClient020", &code);
    if (g_steam_client == NULL) g_steam_client = g_create_interface("SteamClient019", &code);
    if (g_steam_client == NULL) g_steam_client = g_create_interface("SteamClient017", &code);
    if (g_steam_client == NULL) {
        wnb_resolver_log("[wnb] CreateInterface(SteamClient0XX) returned NULL");
        return NULL;
    }

    {
        void** vt = *(void***)g_steam_client;
        typedef int (*CreateSteamPipe_fn)(void*);
        typedef int (*ConnectToGlobalUser_fn)(void*, int);
        g_steam_pipe = ((CreateSteamPipe_fn)vt[0])(g_steam_client);
        if (g_steam_pipe != 0) {
            g_steam_user = ((ConnectToGlobalUser_fn)vt[2])(
                    g_steam_client, g_steam_pipe);
        }
        char buf[128];
        snprintf(buf, sizeof(buf),
                 "[wnb] Valve ISteamClient: pipe=%d user=%d",
                 g_steam_pipe, g_steam_user);
        wnb_resolver_log(buf);
        if (g_steam_pipe == 0 || g_steam_user == 0) {
            wnb_resolver_log("[wnb] WARNING: pipe/user handshake failed; "
                             "falling back to 1,1 (matchmaking may be empty)");
            if (g_steam_pipe == 0) g_steam_pipe = 1;
            if (g_steam_user == 0) g_steam_user = 1;
        }
    }
    return g_steam_client;
}

static void* resolve_interface(int slot, const char* version) {
    void* client = resolve_steam_client();
    if (client == NULL) return NULL;
    void** vt = *(void***)client;
    typedef void* (*GetIface_fn)(void*, int, int, const char*);
    void* iface = ((GetIface_fn)vt[slot])(
            client, g_steam_user, g_steam_pipe, version);
    char buf[128];
    snprintf(buf, sizeof(buf),
             "[wnb] resolve_interface slot=%d ver=%s pipe=%d user=%d -> %p",
             slot, version, g_steam_pipe, g_steam_user, iface);
    wnb_resolver_log(buf);
    return iface;
}

void wnb_pump_valve_callbacks(void) {
    if (g_steam_client == NULL) return;          /* resolver not run yet */
    if (g_steam_bgetcallback == NULL || g_steam_freelastcallback == NULL) return;

    struct CallbackMsg { int hUser; int iCallback; void* pubParam; int cubParam; };
    struct CallbackMsg msg;
    int guard = 0;
    while (guard++ < 512 && g_steam_bgetcallback(g_steam_pipe, &msg)) {
        if (msg.iCallback == 703 /* SteamAPICallCompleted_t */) {
            struct ApiCallDone {
                unsigned long long hAsyncCall;
                int      iCallback;
                unsigned cubParam;
            };
            if (msg.pubParam != NULL && g_steam_getapicallresult != NULL) {
                struct ApiCallDone cc = *(struct ApiCallDone*)msg.pubParam;
                unsigned char payload[2048];
                int sz = (int)(cc.cubParam < sizeof(payload)
                               ? cc.cubParam : sizeof(payload));
                unsigned char failed = 0;
                if (g_steam_getapicallresult(g_steam_pipe, cc.hAsyncCall,
                                             payload, sz, cc.iCallback,
                                             &failed)) {
                    char b[160];
                    snprintf(b, sizeof(b),
                             "[wnb] pump: call-result hCall=%llu cb=%d "
                             "sz=%d failed=%d -> dispatch",
                             cc.hAsyncCall, cc.iCallback, sz, failed);
                    wnb_resolver_log(b);
                    wnb_dispatch_call_result(cc.hAsyncCall, failed,
                                             payload, (size_t)sz);
                }
            }
        } else {
            char b[128];
            snprintf(b, sizeof(b),
                     "[wnb] pump: callback id=%d sz=%d -> dispatch",
                     msg.iCallback, msg.cubParam);
            wnb_resolver_log(b);
            wnb_dispatch_callback(msg.iCallback, msg.pubParam,
                                  (size_t)msg.cubParam);
        }
        g_steam_freelastcallback(g_steam_pipe);
    }
}

static void* g_our_matchmaking = NULL;
static void* g_our_matchmaking_servers = NULL;

void* get_our_matchmaking(void) {
    if (g_our_matchmaking == NULL)
        g_our_matchmaking = resolve_interface(10, "SteamMatchMaking009");
    return g_our_matchmaking;
}

void* get_our_matchmaking_servers(void) {
    if (g_our_matchmaking_servers == NULL)
        g_our_matchmaking_servers = resolve_interface(11, "SteamMatchMakingServers002");
    return g_our_matchmaking_servers;
}


WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetFavoriteGameCount(void* self) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetFavoriteGameCount");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(mm);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetFavoriteGame(void* self, int _a0, void* _a1, void* _a2, void* _a3, void* _a4, void* _a5, void* _a6) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetFavoriteGame");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, int, void*, void*, void*, void*, void*, void*);
    return ((Fn)vt[1])(mm, _a0, _a1, _a2, _a3, _a4, _a5, _a6);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_AddFavoriteGame(void* self, uint32_t _a0, uint32_t _a1, uint16_t _a2, uint16_t _a3, uint32_t _a4, uint32_t _a5) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddFavoriteGame");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint32_t, uint32_t, uint16_t, uint16_t, uint32_t, uint32_t);
    return ((Fn)vt[2])(mm, _a0, _a1, _a2, _a3, _a4, _a5);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_RemoveFavoriteGame(void* self, uint32_t _a0, uint32_t _a1, uint16_t _a2, uint16_t _a3, uint32_t _a4) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_RemoveFavoriteGame");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint32_t, uint32_t, uint16_t, uint16_t, uint32_t);
    return ((Fn)vt[3])(mm, _a0, _a1, _a2, _a3, _a4);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_RequestLobbyList(void* self) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_RequestLobbyList");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[4])(mm);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListStringFilter(void* self, void* k, void* v, int cmp) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListStringFilter");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*, void*, int);
    ((Fn)vt[5])(mm, k, v, cmp);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListNumericalFilter(void* self, void* k, int v, int cmp) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListNumericalFilter");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*, int, int);
    ((Fn)vt[6])(mm, k, v, cmp);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListNearValueFilter(void* self, void* k, int v) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListNearValueFilter");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*, int);
    ((Fn)vt[7])(mm, k, v);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListFilterSlotsAvailable(void* self, int slots) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListFilterSlotsAvailable");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[8])(mm, slots);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListDistanceFilter(void* self, int eDist) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListDistanceFilter");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[9])(mm, eDist);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListResultCountFilter(void* self, int n) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListResultCountFilter");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[10])(mm, n);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListCompatibleMembersFilter(void* self, void* _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_AddRequestLobbyListCompatibleMembersFilter");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[11])(mm, _a0);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_GetLobbyByIndex(void* self, int idx) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyByIndex");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef uint64_t (*Fn)(void*, int);
    return ((Fn)vt[12])(mm, idx);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_CreateLobby(void* self, int eLobbyType, int maxMembers) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_CreateLobby");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef uint64_t (*Fn)(void*, int, int);
    return ((Fn)vt[13])(mm, eLobbyType, maxMembers);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_JoinLobby(void* self, uint64_t lobbySid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_JoinLobby");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[14])(mm, lobbySid);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_LeaveLobby(void* self, uint64_t sid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_LeaveLobby");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[15])(mm, sid);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_InviteUserToLobby(void* self, uint64_t sid, uint64_t invitee) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_InviteUserToLobby");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[16])(mm, sid, invitee);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetNumLobbyMembers(void* self, uint64_t sid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetNumLobbyMembers");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[17])(mm, sid);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_GetLobbyMemberByIndex(void* self, uint64_t sid, int idx) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyMemberByIndex");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[18])(mm, sid, idx);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmaking_GetLobbyData(void* self, uint64_t sid, void* key) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyData");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[19])(mm, sid, key);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyData(void* self, uint64_t sid, void* key, void* val) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyData");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[20])(mm, sid, key, val);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyDataCount(void* self, uint64_t sid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyDataCount");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[21])(mm, sid);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyDataByIndex(void* self, uint64_t sid, int idx, void* key, int kn, void* val, int vn) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyDataByIndex");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, int, void*, int, void*, int);
    return ((Fn)vt[22])(mm, sid, idx, key, kn, val, vn);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_DeleteLobbyData(void* self, uint64_t sid, void* key) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_DeleteLobbyData");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[23])(mm, sid, key);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmaking_GetLobbyMemberData(void* self, uint64_t sid, uint64_t member, void* key) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyMemberData");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint64_t, uint64_t, void*);
    return ((Fn)vt[24])(mm, sid, member, key);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_SetLobbyMemberData(void* self, uint64_t sid, void* key, void* val) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyMemberData");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, uint64_t, void*, void*);
    ((Fn)vt[25])(mm, sid, key, val);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SendLobbyChatMsg(void* self, uint64_t sid, void* body, int n) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SendLobbyChatMsg");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, void*, int);
    return ((Fn)vt[26])(mm, sid, body, n);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyChatEntry(void* self, uint64_t sid, int idx, void* speaker_out, void* body_out, int body_cap, void* chat_type_out) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyChatEntry");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, int, void*, void*, int, void*);
    return ((Fn)vt[27])(mm, sid, idx, speaker_out, body_out, body_cap, chat_type_out);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_RequestLobbyData(void* self, uint64_t sid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_RequestLobbyData");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[28])(mm, sid);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_SetLobbyGameServer(void* self, uint64_t sid, uint32_t ip, uint16_t port, uint64_t gs) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyGameServer");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, uint64_t, uint32_t, uint16_t, uint64_t);
    ((Fn)vt[29])(mm, sid, ip, port, gs);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyGameServer(void* self, uint64_t sid, void* ip, void* port, void* sid_out) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyGameServer");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, void*, void*, void*);
    return ((Fn)vt[30])(mm, sid, ip, port, sid_out);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyMemberLimit(void* self, uint64_t sid, int max_members) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyMemberLimit");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[31])(mm, sid, max_members);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyMemberLimit(void* self, uint64_t sid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyMemberLimit");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[32])(mm, sid);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyType(void* self, uint64_t sid, int eLobbyType) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyType");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[33])(mm, sid, eLobbyType);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyJoinable(void* self, uint64_t sid, int joinable) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyJoinable");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[34])(mm, sid, joinable);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_GetLobbyOwner(void* self, uint64_t sid) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_GetLobbyOwner");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[35])(mm, sid);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyOwner(void* self, uint64_t sid, uint64_t new_owner) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLobbyOwner");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[36])(mm, sid, new_owner);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLinkedLobby(void* self, uint64_t _a0, uint64_t _a1) {
    wnb_log_once("SteamAPI_ISteamMatchmaking_SetLinkedLobby");
    (void)self;
    void* mm = get_our_matchmaking();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[37])(mm, _a0, _a1);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestInternetServerList(void* self, uint32_t app, void* _a1, uint32_t n, void* _a3) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RequestInternetServerList");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[0])(mm, app, _a1, n, _a3);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestLANServerList(void* self, uint32_t app, void* _a1) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RequestLANServerList");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[1])(mm, app, _a1);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestFriendsServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RequestFriendsServerList");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[2])(mm, app, _a1, _a2, _a3);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestFavoritesServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RequestFavoritesServerList");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[3])(mm, app, _a1, _a2, _a3);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestHistoryServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RequestHistoryServerList");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[4])(mm, app, _a1, _a2, _a3);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestSpectatorServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RequestSpectatorServerList");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[5])(mm, app, _a1, _a2, _a3);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_ReleaseRequest(void* self, void* _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_ReleaseRequest");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[6])(mm, _a0);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_GetServerDetails(void* self, void* _a0, int _a1) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_GetServerDetails");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return NULL;
    void** vt = *(void***)mm;
    typedef void* (*Fn)(void*, void*, int);
    return ((Fn)vt[7])(mm, _a0, _a1);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_CancelQuery(void* self, void* _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_CancelQuery");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[8])(mm, _a0);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_RefreshQuery(void* self, void* _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RefreshQuery");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[9])(mm, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_IsRefreshing(void* self, void* _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_IsRefreshing");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[10])(mm, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_GetServerCount(void* self, void* _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_GetServerCount");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[11])(mm, _a0);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_RefreshServer(void* self, void* _a0, int _a1) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_RefreshServer");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, void*, int);
    ((Fn)vt[12])(mm, _a0, _a1);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_PingServer(void* self, uint32_t _a0, uint16_t _a1, void* _a2) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_PingServer");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint32_t, uint16_t, void*);
    return ((Fn)vt[13])(mm, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_PlayerDetails(void* self, uint32_t _a0, uint16_t _a1, void* _a2) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_PlayerDetails");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint32_t, uint16_t, void*);
    return ((Fn)vt[14])(mm, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_ServerRules(void* self, uint32_t _a0, uint16_t _a1, void* _a2) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_ServerRules");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return 0;
    void** vt = *(void***)mm;
    typedef int (*Fn)(void*, uint32_t, uint16_t, void*);
    return ((Fn)vt[15])(mm, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_CancelServerQuery(void* self, int _a0) {
    wnb_log_once("SteamAPI_ISteamMatchmakingServers_CancelServerQuery");
    (void)self;
    void* mm = get_our_matchmaking_servers();
    if (mm == NULL) return;
    void** vt = *(void***)mm;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[16])(mm, _a0);
}
