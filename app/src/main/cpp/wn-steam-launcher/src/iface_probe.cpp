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

typedef int (*Flat_GetHSteamUser_t)(void*);
typedef bool (*Flat_BLoggedOn_t)(void*);
typedef unsigned long long (*Flat_GetSteamID_t)(void*);
typedef unsigned int (*Flat_GetAuthSessionTicket_t)(void*, void*, int, unsigned int*, void*);
typedef int (*Flat_GetEncryptedAppTicket_t)(void*, void*, int, unsigned int*);
typedef unsigned long long (*Flat_RequestEncryptedAppTicket_t)(void*, void*, int);
typedef void (*RunCallbacks_t)(void);
typedef int (*Flat_GetAppBuildId_t)(void*);
typedef bool (*Flat_BIsSubscribed_t)(void*);

static void probe_auth_ticket(HMODULE api, void* user, void* apps) {
    Flat_GetHSteamUser_t fGetUser =
        (Flat_GetHSteamUser_t) GetProcAddress(api, "SteamAPI_ISteamUser_GetHSteamUser");
    Flat_BLoggedOn_t fLoggedOn =
        (Flat_BLoggedOn_t) GetProcAddress(api, "SteamAPI_ISteamUser_BLoggedOn");
    Flat_GetSteamID_t fSteamId =
        (Flat_GetSteamID_t) GetProcAddress(api, "SteamAPI_ISteamUser_GetSteamID");
    Flat_GetAuthSessionTicket_t fTicket =
        (Flat_GetAuthSessionTicket_t) GetProcAddress(api, "SteamAPI_ISteamUser_GetAuthSessionTicket");
    Flat_GetEncryptedAppTicket_t fEnc =
        (Flat_GetEncryptedAppTicket_t) GetProcAddress(api, "SteamAPI_ISteamUser_GetEncryptedAppTicket");
    Flat_GetAppBuildId_t fBuildId =
        (Flat_GetAppBuildId_t) GetProcAddress(api, "SteamAPI_ISteamApps_GetAppBuildId");
    Flat_BIsSubscribed_t fSubscribed =
        (Flat_BIsSubscribed_t) GetProcAddress(api, "SteamAPI_ISteamApps_BIsSubscribed");

    if (apps && fBuildId) {
        plog("[wn-probe] version: ISteamApps::GetAppBuildId() = %d", fBuildId(apps));
    }
    if (apps && fSubscribed) {
        plog("[wn-probe] version: ISteamApps::BIsSubscribed() = %d", fSubscribed(apps) ? 1 : 0);
    }

    if (!user) {
        plog("[wn-probe] auth: no ISteamUser — cannot test the ticket the game presents");
        return;
    }
    if (!fGetUser || !fLoggedOn || !fSteamId || !fTicket) {
        plog("[wn-probe] auth: flat exports missing (GetHSteamUser=%p BLoggedOn=%p "
             "GetSteamID=%p GetAuthSessionTicket=%p)",
             (void*) fGetUser, (void*) fLoggedOn, (void*) fSteamId, (void*) fTicket);
        return;
    }

    plog("[wn-probe] auth anchors: GetHSteamUser=%d BLoggedOn=%d GetSteamID=%llu",
         fGetUser(user), fLoggedOn(user) ? 1 : 0, fSteamId(user));

    unsigned char ticket[2048];
    memset(ticket, 0, sizeof(ticket));
    unsigned int cb = 0;
    unsigned int h = fTicket(user, ticket, (int) sizeof(ticket), &cb, nullptr);
    plog("[wn-probe] auth: GetAuthSessionTicket -> handle=%u length=%u", h, cb);
    if (h == 0 || cb == 0) {
        plog("[wn-probe] auth: *** NO TICKET ISSUED *** — the game cannot authenticate "
             "to its own backend with this session");
    } else {
        char hex[97];
        unsigned int n = cb < 32 ? cb : 32;
        for (unsigned int i = 0; i < n; ++i) snprintf(hex + i * 3, 4, "%02x ", ticket[i]);
        hex[n * 3] = '\0';
        plog("[wn-probe] auth: ticket first %u bytes: %s", n, hex);
    }

    Flat_RequestEncryptedAppTicket_t fReqEnc =
        (Flat_RequestEncryptedAppTicket_t) GetProcAddress(api, "SteamAPI_ISteamUser_RequestEncryptedAppTicket");
    RunCallbacks_t fRun = (RunCallbacks_t) GetProcAddress(api, "SteamAPI_RunCallbacks");

    if (fEnc && fReqEnc) {
        unsigned long long call = fReqEnc(user, nullptr, 0);
        plog("[wn-probe] auth: RequestEncryptedAppTicket -> HSteamAPICall=%llu", call);
        unsigned char enc[4096];
        unsigned int cbEnc = 0;
        int rc = 0;
        int waited = 0;
        for (int i = 0; i < 200; ++i) {
            if (fRun) fRun();
            Sleep(100);
            waited += 100;
            memset(enc, 0, sizeof(enc));
            cbEnc = 0;
            rc = fEnc(user, enc, (int) sizeof(enc), &cbEnc);
            if (rc && cbEnc) break;
        }
        plog("[wn-probe] auth: GetEncryptedAppTicket after request -> rc=%d length=%u (%dms)",
             rc, cbEnc, waited);
        if (rc && cbEnc) {
            char hex[97];
            unsigned int n = cbEnc < 32 ? cbEnc : 32;
            for (unsigned int i = 0; i < n; ++i) snprintf(hex + i * 3, 4, "%02x ", enc[i]);
            hex[n * 3] = '\0';
            plog("[wn-probe] auth: encrypted app ticket first %u bytes: %s", n, hex);
            plog("[wn-probe] auth: encrypted app ticket OBTAINED — publisher-backend auth works");
        } else {
            plog("[wn-probe] auth: *** ENCRYPTED APP TICKET NOT ISSUED *** — this is how a Steam "
                 "game authenticates to a publisher backend");
        }
    }
}

static void import_debug_ca() {
    const char* path = "C:\\wn-mitm-ca.der";
    HANDLE f = CreateFileA(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                           FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) return;
    DWORD sz = GetFileSize(f, NULL);
    if (sz == 0 || sz > 65536) { CloseHandle(f); return; }
    unsigned char* buf = (unsigned char*) malloc(sz);
    DWORD got = 0;
    BOOL rd = ReadFile(f, buf, sz, &got, NULL);
    CloseHandle(f);
    if (!rd || got == 0) { free(buf); return; }

    const DWORD locations[2] = { CERT_SYSTEM_STORE_CURRENT_USER, CERT_SYSTEM_STORE_LOCAL_MACHINE };
    const char* names[2] = { "CURRENT_USER", "LOCAL_MACHINE" };
    for (int i = 0; i < 2; ++i) {
        HCERTSTORE store = CertOpenStore(CERT_STORE_PROV_SYSTEM_A, 0, 0, locations[i], "ROOT");
        if (!store) {
            plog("[wn-probe] mitm-ca: CertOpenStore(%s) failed GLE=%lu",
                 names[i], (unsigned long) GetLastError());
            continue;
        }
        BOOL ok = CertAddEncodedCertificateToStore(store, X509_ASN_ENCODING, buf, got,
                                                   CERT_STORE_ADD_REPLACE_EXISTING, NULL);
        plog("[wn-probe] mitm-ca: import into %s ROOT -> %s (GLE=%lu, %u bytes)",
             names[i], ok ? "ok" : "FAILED", (unsigned long) GetLastError(), (unsigned) got);
        CertCloseStore(store, 0);
    }
    free(buf);
}

int main(int argc, char** argv) {
    const char* gameDir = (argc > 1) ? argv[1] : "";
    const char* appId = (argc > 2) ? argv[2] : "";
    const char* logPath = (argc > 3) ? argv[3] : "C:\\wn-iface-probe.log";

    g_log = fopen(logPath, "w");
    import_debug_ca();
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

    probe_auth_ticket(api,
                      findIface(hUser, "SteamUser023"),
                      findIface(hUser, "STEAMAPPS_INTERFACE_VERSION008"));

    if (shutdown) shutdown();
    plog("[wn-probe] done");
    if (g_log) fclose(g_log);
    return 0;
}
