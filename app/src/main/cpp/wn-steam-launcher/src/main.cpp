
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include "clean_shutdown.h"

#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <tlhelp32.h>
#include <filesystem>
#include <string>
#include <vector>

#ifndef LOAD_LIBRARY_SEARCH_SYSTEM32
#define LOAD_LIBRARY_SEARCH_SYSTEM32 0x00000800
#endif
#ifndef LOAD_LIBRARY_SEARCH_DEFAULT_DIRS
#define LOAD_LIBRARY_SEARCH_DEFAULT_DIRS 0x00001000
#endif
#ifndef LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR
#define LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR 0x00000100
#endif
#ifndef LOAD_IGNORE_CODE_AUTHZ_LEVEL
#define LOAD_IGNORE_CODE_AUTHZ_LEVEL 0x00000010
#endif

#ifdef __i386__
#define WN_THISCALL __thiscall
#else
#define WN_THISCALL
#endif

static const int kVtEngine_GetIClientUser   = 0x40;  // IClientEngine slot 8
static const int kVtUser_LogOn              = 0x08;  // slot  1: EResult LogOn(uint64 steamID)
static const int kVtUser_BLoggedOn          = 0x20;  // slot  4: bool BLoggedOn()
static const int kVtUser_GetSteamID         = 0x50;  // slot 10: CSteamID& GetSteamID(CSteamID& out)
static const int kVtUser_BHasCachedCreds    = 0x188; // slot 49: bool BHasCachedCredentials(const char*)
static const int kVtUser_SetLoginToken      = 0x1C0; // slot 56: EResult SetLoginToken(const char* token, const char* account)

static const int kVtEngine_GetIClientAppManager = 0x158; // IClientEngine slot 43
static const int kVtAppMgr_LaunchApp            = 0x10;  // IClientAppManager slot 2
static const int kVtAppMgr_RefreshAppInfo       = 0x298; // void RefreshAppInfo()
static const int kVtAppMgr_GetAppInstallState   = 0x20;  // int  GetAppInstallState(AppId_t)

static const int kVtEngine_GetIClientApps       = 0x88;  // slot 17: IClientApps*(hUser, hPipe)
static const int kVtApps_RequestAppInfoUpdate   = 0x38;  // slot 7:  bool(AppId_t* ids, int n)

static const int kVtEngine_GetIClientUtils       = 0x70;  // slot 14: IClientUtils*(HSteamPipe)
static const int kVtUtils_IsAPICallCompleted     = 0xB0;  // slot 22: bool(apiCall, *pbFailed)
static const int kVtUtils_GetAPICallFailureReason = 0xB8; // slot 23: int(apiCall)  ESteamAPICallFailure
static const int kVtUtils_GetAPICallResult       = 0xC0;  // slot 24: bool(apiCall, pCb, cubCb, iCbExpected, *pbFailed)

static const int kLaunchAppResultCallbackId    = 0x13610B;
static const int kLaunchAppResultSize          = 0x20C;
static const int kLaunchResultErrorOffset      = 0x8;     // int32 EAppUpdateError

typedef void* (*CreateInterfaceFn)(const char* version, int* returnCode);
typedef int   (*Steam_CreateGlobalUser_fn)(int* pipe_out);
typedef bool  (*Steam_BLoggedOn_fn)(int pipe, int user);
typedef bool  (*Steam_BGetCallback_fn)(int pipe, void* cb);
typedef void  (*Steam_FreeLastCallback_fn)(int pipe);
typedef void  (*Breakpad_SteamSetAppID_fn)(unsigned app_id);

static FILE* g_logFile = NULL;

static void open_log(void) {
    if (g_logFile) return;
    g_logFile = fopen("C:\\wn-launcher.log", "w");
    if (g_logFile) setvbuf(g_logFile, NULL, _IONBF, 0);
}

static void log_line(const char* fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf) - 2, fmt, ap);
    va_end(ap);
    if (n < 0) n = 0;
    if (n > (int)sizeof(buf) - 2) n = (int)sizeof(buf) - 2;
    buf[n] = '\n';
    buf[n + 1] = '\0';
    fputs(buf, stderr);
    OutputDebugStringA(buf);
    if (g_logFile) {
        fputs(buf, g_logFile);
    } else {
        // Fallback for any log_line invoked before open_log() (unlikely but safe).
        FILE* lf = fopen("C:\\wn-launcher.log", "a");
        if (lf) { fputs(buf, lf); fclose(lf); }
    }
}

// Sink handed to clean_shutdown.cpp so its [wn-launcher] markers ("clean-shutdown
// armed", "clean logoff complete", ...) go through this one open log handle. A
// separate fopen() inside clean_shutdown writes at a position our next log_line
// clobbers, which silently dropped those markers — and the Android close path
// keys off exactly those markers to run / confirm the clean-shutdown handshake.
static void clean_shutdown_log_sink(const char* line) {
    if (line) log_line("%s", line);
}

static uint64_t env_u64(const char* name) {
    const char* v = getenv(name);
    if (!v || !*v) return 0;
    return (uint64_t) _strtoui64(v, NULL, 10);
}

static int b64url_val(unsigned char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '-') return 62;
    if (c == '_') return 63;
    return -1;
}

static void log_token_claims(const char* token) {
    if (!token || !*token) { log_line("[wn-launcher] token: (empty)"); return; }
    const char* dot1 = strchr(token, '.');
    if (!dot1) { log_line("[wn-launcher] token: not a JWT (no '.')"); return; }
    const char* dot2 = strchr(dot1 + 1, '.');
    if (!dot2) { log_line("[wn-launcher] token: not a JWT (one '.')"); return; }
    size_t seglen = (size_t)(dot2 - (dot1 + 1));
    if (seglen == 0 || seglen > 2000) {
        log_line("[wn-launcher] token: payload segment size unusable (%zu)", seglen);
        return;
    }
    char out[1536];
    size_t op = 0;
    uint32_t acc = 0;
    int bits = 0;
    for (size_t i = 0; i < seglen && op < sizeof(out) - 1; ++i) {
        unsigned char c = (unsigned char) (dot1 + 1)[i];
        int v = b64url_val(c);
        if (v < 0) continue;  // skip '=' / stray
        acc = (acc << 6) | (uint32_t) v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out[op++] = (char)((acc >> bits) & 0xFF);
        }
    }
    out[op] = '\0';
    log_line("[wn-launcher] token JWT payload: %s", out);
}

static void seed_active_process_registry(uint32_t our_pid, uint32_t steam_account_id) {
    HKEY h = NULL;
    LONG rc = RegCreateKeyExA(HKEY_CURRENT_USER,
            "Software\\Valve\\Steam\\ActiveProcess",
            0, NULL, REG_OPTION_NON_VOLATILE, KEY_WRITE, NULL, &h, NULL);
    if (rc != ERROR_SUCCESS) {
        log_line("[wn-launcher] RegCreateKeyEx(ActiveProcess) failed rc=%ld", rc);
        return;
    }
    const char* clientDll   = "C:\\Program Files (x86)\\Steam\\steamclient.dll";
    const char* clientDll64 = "C:\\Program Files (x86)\\Steam\\steamclient64.dll";
    const char* installPath = "C:\\Program Files (x86)\\Steam";
    DWORD universe = 1;  // k_EUniversePublic
    DWORD pid_dw = (DWORD) our_pid;
    DWORD active_user = (DWORD) steam_account_id;
    RegSetValueExA(h, "SteamClientDll",   0, REG_SZ, (const BYTE*) clientDll,   (DWORD) strlen(clientDll)   + 1);
    RegSetValueExA(h, "SteamClientDll64", 0, REG_SZ, (const BYTE*) clientDll64, (DWORD) strlen(clientDll64) + 1);
    RegSetValueExA(h, "Universe",         0, REG_DWORD, (const BYTE*) &universe, sizeof(universe));
    RegSetValueExA(h, "pid",              0, REG_DWORD, (const BYTE*) &pid_dw,   sizeof(pid_dw));
    RegSetValueExA(h, "ActiveUser",       0, REG_DWORD, (const BYTE*) &active_user, sizeof(active_user));
    RegCloseKey(h);

    const char* appIdStr = getenv("WN_STEAM_APPID");
    if (appIdStr && *appIdStr) {
        char keyPath[256];
        snprintf(keyPath, sizeof(keyPath),
                 "Software\\Valve\\Steam\\Apps\\%s", appIdStr);
        HKEY h2 = NULL;
        if (RegCreateKeyExA(HKEY_CURRENT_USER, keyPath, 0, NULL,
                            REG_OPTION_NON_VOLATILE, KEY_WRITE, NULL, &h2, NULL) == ERROR_SUCCESS) {
            DWORD one = 1;
            DWORD zero = 0;
            RegSetValueExA(h2, "Installed", 0, REG_DWORD, (const BYTE*) &one,  sizeof(one));
            RegSetValueExA(h2, "Running",   0, REG_DWORD, (const BYTE*) &one,  sizeof(one));
            RegSetValueExA(h2, "Updating",  0, REG_DWORD, (const BYTE*) &zero, sizeof(zero));
            RegCloseKey(h2);
        }
    }
    {
        const char* steamFwd  = "c:/program files (x86)/steam";
        const char* steamExe  = "c:/program files (x86)/steam/steam.exe";
        const char* steamBack = "C:\\Program Files (x86)\\Steam";
        HKEY hk = NULL;
        if (RegCreateKeyExA(HKEY_CURRENT_USER, "Software\\Valve\\Steam", 0, NULL,
                REG_OPTION_NON_VOLATILE, KEY_WRITE, NULL, &hk, NULL) == ERROR_SUCCESS) {
            RegSetValueExA(hk, "SteamPath", 0, REG_SZ,
                           (const BYTE*) steamFwd, (DWORD) strlen(steamFwd) + 1);
            RegSetValueExA(hk, "SteamExe",  0, REG_SZ,
                           (const BYTE*) steamExe, (DWORD) strlen(steamExe) + 1);
            RegCloseKey(hk);
        }
        HKEY hm = NULL;
        if (RegCreateKeyExA(HKEY_LOCAL_MACHINE, "Software\\Valve\\Steam", 0, NULL,
                REG_OPTION_NON_VOLATILE, KEY_WRITE, NULL, &hm, NULL) == ERROR_SUCCESS) {
            RegSetValueExA(hm, "InstallPath", 0, REG_SZ,
                           (const BYTE*) steamBack, (DWORD) strlen(steamBack) + 1);
            RegSetValueExA(hm, "SteamPath",   0, REG_SZ,
                           (const BYTE*) steamFwd,  (DWORD) strlen(steamFwd) + 1);
            RegCloseKey(hm);
        }
        SetEnvironmentVariableA("SteamPath", steamBack);
    }

    log_line("[wn-launcher] HKCU ActiveProcess + Steam install registry seeded "
             "(pid=%u, activeUser=%u, SteamPath set)",
             our_pid, steam_account_id);
}

static void stage_steam_config(void) {
    const char* cfgDir = "C:\\Program Files (x86)\\Steam\\config";
    CreateDirectoryA(cfgDir, NULL);  // no-op if it already exists
    const char* files[2] = {
        "C:\\Program Files (x86)\\Steam\\config\\config.vdf",
        "C:\\Program Files (x86)\\Steam\\config\\local.vdf",
    };
    for (int i = 0; i < 2; ++i) {
        DWORD attr = GetFileAttributesA(files[i]);
        if (attr == INVALID_FILE_ATTRIBUTES) {
            HANDLE h = CreateFileA(files[i], GENERIC_WRITE, 0, NULL,
                                   CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
            if (h != INVALID_HANDLE_VALUE) {
                CloseHandle(h);
                log_line("[wn-launcher] staged empty %s", files[i]);
            }
        }
    }
}

static void stage_app_manifest(uint32_t appId, const char* gameExe) {
    if (appId == 0 || !gameExe) return;
    const char* marker = "\\steamapps\\common\\";
    size_t mlen = strlen(marker);
    const char* hit = NULL;
    for (const char* s = gameExe; *s; ++s) {
        if (_strnicmp(s, marker, mlen) == 0) { hit = s; break; }
    }
    if (!hit) {
        log_line("[wn-launcher] app manifest: game not under steamapps\\common "
                 "— skipping (LaunchApp may report not-installed)");
        return;
    }
    const char* dirStart = hit + mlen;
    const char* dirEnd = strchr(dirStart, '\\');
    if (!dirEnd || dirEnd == dirStart) return;
    char installdir[260];
    size_t n = (size_t)(dirEnd - dirStart);
    if (n >= sizeof(installdir)) return;
    memcpy(installdir, dirStart, n);
    installdir[n] = '\0';

    CreateDirectoryA("C:\\Program Files (x86)\\Steam\\steamapps", NULL);
    char acf[MAX_PATH];
    snprintf(acf, sizeof(acf),
             "C:\\Program Files (x86)\\Steam\\steamapps\\appmanifest_%u.acf",
             appId);
    FILE* f = fopen(acf, "w");
    if (!f) {
        log_line("[wn-launcher] app manifest: fopen(%s) failed", acf);
        return;
    }
    const char* owner = getenv("WN_STEAM_STEAMID");
    fprintf(f,
            "\"AppState\"\n"
            "{\n"
            "\t\"appid\"\t\t\"%u\"\n"
            "\t\"Universe\"\t\t\"1\"\n"
            "\t\"name\"\t\t\"%s\"\n"
            "\t\"StateFlags\"\t\t\"4\"\n"
            "\t\"installdir\"\t\t\"%s\"\n"
            "\t\"LastUpdated\"\t\t\"0\"\n"
            "\t\"SizeOnDisk\"\t\t\"0\"\n"
            "\t\"buildid\"\t\t\"0\"\n"
            "\t\"LastOwner\"\t\t\"%s\"\n"
            "\t\"InstalledDepots\"\n"
            "\t{\n"
            "\t}\n"
            "}\n",
            appId, installdir, installdir,
            (owner && *owner) ? owner : "0");
    fclose(f);
    log_line("[wn-launcher] app manifest staged: %s (installdir=\"%s\", StateFlags=4)",
             acf, installdir);
}

static int count_process_by_name(const char* exeName) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return -1;
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    int count = 0;
    if (Process32First(snap, &pe)) {
        do {
            if (_stricmp(pe.szExeFile, exeName) == 0) count++;
        } while (Process32Next(snap, &pe));
    }
    CloseHandle(snap);
    return count;
}

// Direct game launch used when the integrated IClientAppManager::LaunchApp path
// dispatches cleanly (EAppUpdateError=NoError) but never actually spawns the
// game — under Wine/Plan-W there is no real Steam client UI / reaper process to
// consume the launch request, so the app stays registered "launching" while the
// process never appears. We start it ourselves. This is safe with respect to the
// AlreadyRunning bug because the clean-shutdown arm (see clean_shutdown.cpp)
// sends a clean Steam_LogOff on close/game-exit, so the CM session is reaped
// instead of lingering ~40s. Logs the "game process started pid=" marker the
// Android-side WnLauncherStatusTailer treats as a terminal (launch-complete)
// signal. Returns true if the process was created.
static bool create_process_game(const char* gameExe, const char* exeName) {
    char cwd[MAX_PATH];
    snprintf(cwd, sizeof(cwd), "%s", gameExe);
    char* slash = strrchr(cwd, '\\');
    if (slash) *slash = '\0'; else cwd[0] = '\0';

    char cmd[MAX_PATH + 8];
    snprintf(cmd, sizeof(cmd), "\"%s\"", gameExe);

    STARTUPINFOA si;
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi;
    memset(&pi, 0, sizeof(pi));

    // Inherit the launcher's environment (SteamAppId / SteamClientLaunch / etc.
    // were already set via SetEnvironmentVariableA above) so the game's
    // SteamAPI_Init attaches to our logged-on steamclient session.
    BOOL ok = CreateProcessA(gameExe, cmd, NULL, NULL, FALSE,
                             0, NULL, cwd[0] ? cwd : NULL, &si, &pi);
    if (!ok) {
        log_line("[wn-launcher] CreateProcess fallback FAILED for \"%s\" (GLE=%lu)",
                 exeName, GetLastError());
        return false;
    }
    log_line("[wn-launcher] game process started pid=%lu via CreateProcess "
             "fallback (\"%s\")", (unsigned long) pi.dwProcessId, exeName);
    if (pi.hThread) CloseHandle(pi.hThread);
    if (pi.hProcess) CloseHandle(pi.hProcess);
    return true;
}

static void dump_loaded_modules(const char* when) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32,
                                           GetCurrentProcessId());
    if (snap == INVALID_HANDLE_VALUE) {
        log_line("[wn-launcher] modules(%s): CreateToolhelp32Snapshot failed GLE=%lu",
                 when, GetLastError());
        return;
    }
    MODULEENTRY32 me;
    me.dwSize = sizeof(me);
    int n = 0;
    if (Module32First(snap, &me)) {
        do {
            log_line("[wn-launcher] modules(%s): base=%p size=0x%lx name=%s path=%s",
                     when, me.modBaseAddr, (unsigned long) me.modBaseSize,
                     me.szModule, me.szExePath);
            n++;
        } while (Module32Next(snap, &me));
    }
    log_line("[wn-launcher] modules(%s): total=%d", when, n);
    CloseHandle(snap);
}

static LONG WINAPI launcher_unhandled_filter(EXCEPTION_POINTERS* info) {
    if (!info || !info->ExceptionRecord) return EXCEPTION_EXECUTE_HANDLER;
    const EXCEPTION_RECORD* er = info->ExceptionRecord;
    void* ip = er->ExceptionAddress;

    char modName[MAX_PATH] = {0};
    HMODULE faultMod = NULL;
    if (GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS
                           | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                           (LPCSTR)ip, &faultMod)) {
        GetModuleFileNameA(faultMod, modName, sizeof(modName));
    }

    char bytes[3 * 16 + 1] = {0};
    {
        MEMORY_BASIC_INFORMATION mbi;
        if (VirtualQuery(ip, &mbi, sizeof(mbi)) && mbi.State == MEM_COMMIT) {
            const unsigned char* p = (const unsigned char*)ip;
            int hp = 0;
            for (int i = 0; i < 16 && hp + 3 < (int)sizeof(bytes); ++i) {
                hp += snprintf(bytes + hp, sizeof(bytes) - hp, "%02x ", p[i]);
            }
        }
    }

    log_line("[wn-launcher] UEF: tid=%lu pid=%lu exc=0x%lx at %p mod='%s' bytes=%s",
             (unsigned long) GetCurrentThreadId(),
             (unsigned long) GetCurrentProcessId(),
             er->ExceptionCode, ip, modName[0] ? modName : "(unknown)", bytes);
    if (er->ExceptionCode == EXCEPTION_ACCESS_VIOLATION && er->NumberParameters >= 2) {
        const char* op = (er->ExceptionInformation[0] == 0) ? "read"
                       : (er->ExceptionInformation[0] == 1) ? "write"
                       : (er->ExceptionInformation[0] == 8) ? "DEP" : "?";
        log_line("[wn-launcher] UEF: AV %s fault_addr=0x%llx",
                 op, (unsigned long long) er->ExceptionInformation[1]);
    }

    {
        MEMORY_BASIC_INFORMATION mbi;
        if (VirtualQuery(ip, &mbi, sizeof(mbi))) {
            log_line("[wn-launcher] UEF: page base=%p size=0x%llx state=0x%lx "
                     "protect=0x%lx alloc_protect=0x%lx type=0x%lx",
                     mbi.BaseAddress, (unsigned long long) mbi.RegionSize,
                     mbi.State, mbi.Protect, mbi.AllocationProtect, mbi.Type);
        }
    }

    if (info->ContextRecord) {
        const CONTEXT* c = info->ContextRecord;
        log_line("[wn-launcher] UEF: ctx Rip=%llx Rsp=%llx Rbp=%llx",
                 (unsigned long long) c->Rip,
                 (unsigned long long) c->Rsp,
                 (unsigned long long) c->Rbp);
        log_line("[wn-launcher] UEF: ctx Rax=%llx Rcx=%llx Rdx=%llx Rbx=%llx",
                 (unsigned long long) c->Rax, (unsigned long long) c->Rcx,
                 (unsigned long long) c->Rdx, (unsigned long long) c->Rbx);
        log_line("[wn-launcher] UEF: ctx Rsi=%llx Rdi=%llx R8=%llx R9=%llx",
                 (unsigned long long) c->Rsi, (unsigned long long) c->Rdi,
                 (unsigned long long) c->R8,  (unsigned long long) c->R9);
        const uint64_t* sp = (const uint64_t*) c->Rsp;
        MEMORY_BASIC_INFORMATION smbi;
        if (sp && VirtualQuery((LPCVOID) sp, &smbi, sizeof(smbi))
            && smbi.State == MEM_COMMIT) {
            char chain[256]; int p = 0;
            for (int i = 0; i < 8; ++i) {
                p += snprintf(chain + p, sizeof(chain) - p, "%llx ",
                              (unsigned long long) sp[i]);
            }
            log_line("[wn-launcher] UEF: stack[0..7]=%s", chain);
        }
    }

    dump_loaded_modules("UEF");
    return EXCEPTION_EXECUTE_HANDLER;
}

static bool start_steam_client_service(void) {
    const char* kSvcName       = "Steam Client Service";
    const char* kSvcExe        = "C:\\Program Files (x86)\\Steam\\bin\\steamservice.exe";
    const char* kSvcBinPath    = "\"C:\\Program Files (x86)\\Steam\\bin\\steamservice.exe\" /RunAsService";

    DWORD attr = GetFileAttributesA(kSvcExe);
    if (attr == INVALID_FILE_ATTRIBUTES || (attr & FILE_ATTRIBUTE_DIRECTORY)) {
        log_line("[wn-launcher] steamservice: binary not present at %s — "
                 "LaunchApp's IPC queue will have no peer; will use "
                 "CreateProcess fallback", kSvcExe);
        return false;
    }
    log_line("[wn-launcher] steamservice: found %s", kSvcExe);

    SC_HANDLE scm = OpenSCManagerA(NULL, NULL, SC_MANAGER_ALL_ACCESS);
    if (!scm) {
        log_line("[wn-launcher] steamservice: OpenSCManager failed GLE=%lu",
                 GetLastError());
        return false;
    }

    SC_HANDLE svc = OpenServiceA(scm, kSvcName, SERVICE_ALL_ACCESS);
    if (!svc) {
        DWORD err = GetLastError();
        if (err == ERROR_SERVICE_DOES_NOT_EXIST) {
            log_line("[wn-launcher] steamservice: service missing — "
                     "installing as \"%s\"", kSvcName);
            svc = CreateServiceA(
                scm, kSvcName, kSvcName,
                SERVICE_ALL_ACCESS,
                SERVICE_WIN32_OWN_PROCESS,
                SERVICE_DEMAND_START,
                SERVICE_ERROR_NORMAL,
                kSvcBinPath,
                NULL, NULL, NULL, NULL, NULL);
            if (!svc) {
                log_line("[wn-launcher] steamservice: CreateService failed GLE=%lu",
                         GetLastError());
                CloseServiceHandle(scm);
                return false;
            }
            log_line("[wn-launcher] steamservice: service installed");
        } else {
            log_line("[wn-launcher] steamservice: OpenService failed GLE=%lu", err);
            CloseServiceHandle(scm);
            return false;
        }
    }

    SERVICE_STATUS status;
    memset(&status, 0, sizeof(status));
    QueryServiceStatus(svc, &status);
    log_line("[wn-launcher] steamservice: pre-start state=%lu", status.dwCurrentState);

    if (status.dwCurrentState != SERVICE_RUNNING) {
        if (!StartServiceA(svc, 0, NULL)) {
            DWORD err = GetLastError();
            if (err != ERROR_SERVICE_ALREADY_RUNNING) {
                log_line("[wn-launcher] steamservice: StartService failed GLE=%lu",
                         err);
                CloseServiceHandle(svc);
                CloseServiceHandle(scm);
                return false;
            }
        }
        int waited = 0;
        while (waited < 30000) {
            if (!QueryServiceStatus(svc, &status)) break;
            if (status.dwCurrentState == SERVICE_RUNNING ||
                status.dwCurrentState == SERVICE_STOPPED) break;
            Sleep(200);
            waited += 200;
        }
        log_line("[wn-launcher] steamservice: post-start state=%lu after %dms",
                 status.dwCurrentState, waited);
    }

    bool running = (status.dwCurrentState == SERVICE_RUNNING);
    CloseServiceHandle(svc);
    CloseServiceHandle(scm);
    return running;
}

static bool is_exec_ptr(void* p) {
    if (!p) return false;
    MEMORY_BASIC_INFORMATION mbi;
    if (VirtualQuery(p, &mbi, sizeof(mbi)) == 0) return false;
    if (mbi.State != MEM_COMMIT) return false;
    DWORD x = mbi.Protect & 0xFF;
    return x == PAGE_EXECUTE || x == PAGE_EXECUTE_READ ||
           x == PAGE_EXECUTE_READWRITE || x == PAGE_EXECUTE_WRITECOPY;
}

static const char* kRedistsMarkerPath = "C:\\wn-installed-redists.txt";

enum class RedistInstallResult {
    SKIPPED = 0,
    INSTALLED = 1,
    FAILED = 2,
    TIMED_OUT = 3,
};

static bool is_known_redist_installer(const std::filesystem::path& p) {
    if (!std::filesystem::is_regular_file(p)) return false;
    std::string name = p.filename().string();
    std::string ext  = p.extension().string();
    for (char& c : name) c = (char) std::tolower((unsigned char) c);
    for (char& c : ext)  c = (char) std::tolower((unsigned char) c);
    if (ext != ".exe" && ext != ".msi") return false;
    return name.find("vcredist") != std::string::npos ||
           name.find("vc_redist") != std::string::npos ||
           name.find("dxsetup") != std::string::npos ||
           name.find("directx") != std::string::npos ||
           name.find("physx") != std::string::npos ||
           name.find("oalinst") != std::string::npos ||
           name.find("openal") != std::string::npos ||
           name.find("dotnet") != std::string::npos ||
           name.find("ndp") != std::string::npos ||
           name.find("xna") != std::string::npos ||
           name.find("ue4prereq") != std::string::npos ||
           name.find("prereq") != std::string::npos ||
           name.find("redist") != std::string::npos;
}

static std::vector<std::filesystem::path> collect_redist_installers(const std::filesystem::path& gameExePath) {
    std::vector<std::filesystem::path> out;
    try {
        auto root = gameExePath.parent_path();
        if (root.empty()) return out;
        const std::vector<std::string> hotDirs = {
            "redist", "redists", "_redist", "redistributables", "installer",
            "installers", "support", "prereq", "prereqs", "commonredist",
        };
        for (auto it = std::filesystem::recursive_directory_iterator(root,
                     std::filesystem::directory_options::skip_permission_denied);
             it != std::filesystem::recursive_directory_iterator(); ++it) {
            const auto& p = it->path();
            if (it->is_directory()) {
                std::string lower = p.filename().string();
                for (char& c : lower) c = (char) std::tolower((unsigned char) c);
                bool keep = false;
                for (const auto& needle : hotDirs) {
                    if (lower.find(needle) != std::string::npos) { keep = true; break; }
                }
                if (!keep && p.parent_path() != root) {
                    it.disable_recursion_pending();
                }
                continue;
            }
            if (is_known_redist_installer(p)) out.push_back(p);
        }
    } catch (...) {}
    return out;
}

static bool marker_has_path(const std::string& line) {
    DWORD attr = GetFileAttributesA(line.c_str());
    return attr != INVALID_FILE_ATTRIBUTES;
}

static bool load_installed_redists(std::vector<std::string>& lines) {
    FILE* f = fopen(kRedistsMarkerPath, "r");
    if (!f) return false;
    char buf[MAX_PATH * 4];
    while (fgets(buf, sizeof(buf), f)) {
        size_t n = strlen(buf);
        while (n && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) buf[--n] = '\0';
        if (n) lines.emplace_back(buf);
    }
    fclose(f);
    return true;
}

static bool save_installed_redists(const std::vector<std::string>& lines) {
    FILE* f = fopen(kRedistsMarkerPath, "w");
    if (!f) return false;
    for (const auto& line : lines) fprintf(f, "%s\n", line.c_str());
    fclose(f);
    return true;
}

static bool marker_contains(const std::vector<std::string>& lines, const std::string& path) {
    for (const auto& line : lines) {
        if (_stricmp(line.c_str(), path.c_str()) == 0) return true;
    }
    return false;
}

static std::string redist_silent_args(const std::filesystem::path& installer) {
    std::string name = installer.filename().string();
    std::string ext  = installer.extension().string();
    for (char& c : name) c = (char) std::tolower((unsigned char) c);
    for (char& c : ext)  c = (char) std::tolower((unsigned char) c);
    if (ext == ".msi") return " /qn /norestart";
    if (name.find("dxsetup") != std::string::npos) return " /silent";
    if (name.find("ue4prereq") != std::string::npos) return " /quiet /norestart";
    if (name.find("physx") != std::string::npos) return " /quiet /norestart";
    return " /quiet /norestart";
}

static RedistInstallResult run_redist_installer(const std::filesystem::path& installer,
                                                DWORD* outExitCode) {
    std::string cmd = "\"" + installer.string() + "\"" + redist_silent_args(installer);
    std::vector<char> cmdVec(cmd.begin(), cmd.end());
    cmdVec.push_back('\0');

    STARTUPINFOA si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi = {};
    std::string cwdStr = installer.parent_path().string();
    if (!CreateProcessA(
            installer.string().c_str(),
            cmdVec.data(),
            nullptr, nullptr, FALSE,
            CREATE_NO_WINDOW,
            nullptr,
            cwdStr.empty() ? nullptr : cwdStr.c_str(),
            &si, &pi)) {
        log_line("[wn-launcher] redist install: CreateProcess failed for %s "
                  "(GLE=%lu)",
                  installer.string().c_str(), GetLastError());
        if (outExitCode) *outExitCode = 0xFFFFFFFFu;
        return RedistInstallResult::FAILED;
    }

    constexpr DWORD kPerInstallerTimeoutMs = 90 * 1000;
    DWORD waitResult = WaitForSingleObject(pi.hProcess, kPerInstallerTimeoutMs);
    DWORD exitCode = ~0u;
    bool timedOut = false;
    if (waitResult == WAIT_OBJECT_0) {
        GetExitCodeProcess(pi.hProcess, &exitCode);
    } else {
        log_line("[wn-launcher] redist install: %s — 90s timeout (silent "
                 "installer hung?)",
                 installer.filename().string().c_str());
        TerminateProcess(pi.hProcess, 1);
        WaitForSingleObject(pi.hProcess, 5000);
        timedOut = true;
        exitCode = 0xFFFFFFFEu;
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    if (outExitCode) *outExitCode = exitCode;
    if (timedOut) return RedistInstallResult::TIMED_OUT;
    return (exitCode == 0 || exitCode == 3010) ? RedistInstallResult::INSTALLED
                                               : RedistInstallResult::FAILED;
}

static void scan_and_install_redists(const char* gameExe) {
    if (!gameExe || !*gameExe) return;
    std::filesystem::path gamePath(gameExe);
    auto installers = collect_redist_installers(gamePath);
    if (installers.empty()) {
        log_line("[wn-launcher] redist scan: none found");
        return;
    }

    std::vector<std::string> marker;
    load_installed_redists(marker);

    int installed = 0, skipped = 0, failedMarked = 0, timedOut = 0;
    for (const auto& installer : installers) {
        std::string abs = installer.string();
        if (marker_contains(marker, abs)) {
            skipped++;
            continue;
        }
        DWORD exitCode = 0;
        log_line("[wn-launcher] redist install: %s%s",
                 installer.filename().string().c_str(),
                 redist_silent_args(installer).c_str());
        RedistInstallResult rc = run_redist_installer(installer, &exitCode);
        if (rc == RedistInstallResult::INSTALLED) {
            marker.push_back(abs);
            installed++;
            log_line("[wn-launcher] redist install: %s OK exit=%lu",
                     installer.filename().string().c_str(),
                     (unsigned long) exitCode);
        } else if (rc == RedistInstallResult::TIMED_OUT) {
            marker.push_back(abs);
            timedOut++;
            log_line("[wn-launcher] redist install: %s timed out — marking done "
                     "to avoid repeat hangs", installer.filename().string().c_str());
        } else {
            if (exitCode == 1638 || exitCode == 1603 || exitCode == 5100) {
                marker.push_back(abs);
                failedMarked++;
                log_line("[wn-launcher] redist install: %s exit=%lu — marking "
                         "done (already installed / not applicable)",
                         installer.filename().string().c_str(),
                         (unsigned long) exitCode);
            } else {
                log_line("[wn-launcher] redist install: %s FAILED exit=%lu",
                         installer.filename().string().c_str(),
                         (unsigned long) exitCode);
            }
        }
    }
    save_installed_redists(marker);
    log_line("[wn-launcher] redist scan done: installed %d, skipped %d, "
             "failed-marked %d, timed-out-unmarked %d (of %zu total)",
             installed, skipped, failedMarked, timedOut, installers.size());
}

int main(int argc, char** argv) {
    setbuf(stderr, NULL);
    setbuf(stdout, NULL);
    open_log();
    wn_launcher_set_log_sink(clean_shutdown_log_sink);
    log_line("[wn-launcher] Steam Launcher in-process Steam launcher starting (pid=%lu tid=%lu)",
             (unsigned long) GetCurrentProcessId(),
             (unsigned long) GetCurrentThreadId());

    const char* appIdStr = getenv("WN_STEAM_APPID");
    const char* user     = getenv("WN_STEAM_USERNAME");
    const char* token    = getenv("WN_STEAM_TOKEN");
    uint64_t    steamId  = env_u64("WN_STEAM_STEAMID");
    const char* gameExe  = (argc > 1) ? argv[1] : NULL;
    uint32_t    appId    = appIdStr ? (uint32_t) strtoul(appIdStr, NULL, 10) : 0;

    log_line("[wn-launcher] env appId=%u steamId=%llu user=%s exe=%s",
             appId,
             (unsigned long long) steamId,
             user ? user : "(null)",
             gameExe ? gameExe : "(null)");
    if (token && *token) {
        size_t tokenLen = strlen(token);
        log_line("[wn-launcher] token len=%zu prefix=%.*s suffix=%.*s",
                 tokenLen, tokenLen > 16 ? 16 : (int) tokenLen, token,
                 tokenLen > 12 ? 12 : (int) tokenLen,
                 tokenLen > 12 ? token + tokenLen - 12 : token);
        log_token_claims(token);
    } else {
        log_line("[wn-launcher] token missing");
    }
    if (argc <= 1 || !gameExe || !*gameExe) {
        log_line("[wn-launcher] no game exe passed on argv[1]");
        return 1;
    }

    const char* kSteamDir = "C:\\Program Files (x86)\\Steam";
    SetDllDirectoryA(kSteamDir);
    SetCurrentDirectoryA(kSteamDir);
    SetEnvironmentVariableA("SteamPath", kSteamDir);
    SetEnvironmentVariableA("SteamGameId", appIdStr ? appIdStr : "0");
    SetEnvironmentVariableA("SteamAppId",  appIdStr ? appIdStr : "0");
    SetEnvironmentVariableA("SteamUser",   user ? user : "");
    SetEnvironmentVariableA("Steam3Master", "127.0.0.1:27036");
    SetEnvironmentVariableA("SteamClientLaunch", "1");
    SetEnvironmentVariableA("SteamNoOverlayUIDrawing", "1");

    CreateDirectoryA("C:\\Program Files (x86)", NULL);
    CreateDirectoryA(kSteamDir, NULL);

    stage_steam_config();
    seed_active_process_registry(GetCurrentProcessId(), (uint32_t)(steamId & 0xFFFFFFFFu));
    stage_app_manifest(appId, gameExe);

    const char* preloadDlls[] = {
        "tier0_s64.dll",
        "vstdlib_s64.dll",
        "crashhandler64.dll",
        "steamservice.dll",
    };
    for (const char* dll : preloadDlls) {
        char path[MAX_PATH];
        snprintf(path, sizeof(path), "%s\\%s", kSteamDir, dll);
        HMODULE dm = LoadLibraryExA(path, NULL, LOAD_WITH_ALTERED_SEARCH_PATH);
        if (dm) {
            log_line("[wn-launcher] preload %s: ok (%p)", dll, dm);
        } else {
            log_line("[wn-launcher] preload %s: FAIL GLE=%lu", dll, GetLastError());
        }
    }

    log_line("[wn-launcher] preloads done; installing unhandled-exception filter");
    LPTOP_LEVEL_EXCEPTION_FILTER prevFilter =
        SetUnhandledExceptionFilter(launcher_unhandled_filter);
    log_line("[wn-launcher] UEF installed (prev=%p)", prevFilter);
    dump_loaded_modules("pre-LoadLibrary");

    char steamclientPath[MAX_PATH];
    snprintf(steamclientPath, sizeof(steamclientPath),
             "%s\\steamclient64.dll", kSteamDir);

    struct LoadAttempt { DWORD flags; const char* desc; };
    const LoadAttempt attempts[] = {
        { LOAD_WITH_ALTERED_SEARCH_PATH, "LOAD_WITH_ALTERED_SEARCH_PATH" },
        { LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS,
          "DLL_LOAD_DIR|DEFAULT_DIRS" },
        { LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_SYSTEM32,
          "DLL_LOAD_DIR|SYSTEM32" },
        { LOAD_IGNORE_CODE_AUTHZ_LEVEL | LOAD_WITH_ALTERED_SEARCH_PATH,
          "IGNORE_CODE_AUTHZ|ALTERED_SEARCH_PATH" },
    };

    const int kAttempts = (int)(sizeof(attempts) / sizeof(attempts[0]));
    HMODULE lsc = NULL;
    DWORD lastErr = 0;
    for (int i = 0; i < kAttempts && !lsc; i++) {
        lsc = LoadLibraryExA(steamclientPath, NULL, attempts[i].flags);
        if (lsc) {
            log_line("[wn-launcher] steamclient64.dll loaded at %p "
                     "(strategy %d/%d: %s)",
                     lsc, i + 1, kAttempts, attempts[i].desc);
            break;
        }
        lastErr = GetLastError();
        log_line("[wn-launcher] steamclient64.dll load fail strategy %d/%d (%s) "
                 "GLE=%lu",
                 i + 1, kAttempts, attempts[i].desc, lastErr);
        Sleep(50);
    }
    for (int round = 0; round < 3 && !lsc; round++) {
        log_line("[wn-launcher] steamclient64.dll cold-start retry "
                 "round %d/3 after 500ms", round + 1);
        Sleep(500);
        for (int i = 0; i < kAttempts && !lsc; i++) {
            lsc = LoadLibraryExA(steamclientPath, NULL, attempts[i].flags);
            if (!lsc) lastErr = GetLastError();
        }
        if (lsc) {
            log_line("[wn-launcher] steamclient64.dll loaded at %p "
                     "(retry round %d)", lsc, round + 1);
        }
    }
    if (!lsc) {
        lsc = LoadLibraryA(steamclientPath);
        if (lsc) {
            log_line("[wn-launcher] steamclient64.dll loaded at %p "
                     "(plain LoadLibraryA)", lsc);
        } else {
            lastErr = GetLastError();
        }
    }
    if (!lsc) {
        HMODULE probe = LoadLibraryExA(steamclientPath, NULL,
                                        LOAD_LIBRARY_AS_DATAFILE);
        if (probe) {
            log_line("[wn-launcher] diag: DATAFILE load OK — file is "
                     "well-formed; failure is in DllMain/runtime init");
        } else {
            log_line("[wn-launcher] diag: DATAFILE load also FAILED, GLE=%lu",
                     GetLastError());
        }
        log_line("[wn-launcher] LoadLibrary(%s) FAILED after all strategies, "
                 "last GLE=%lu", steamclientPath, lastErr);
        return 2;
    }

    CreateInterfaceFn createInterface =
        (CreateInterfaceFn) GetProcAddress(lsc, "CreateInterface");
    Steam_CreateGlobalUser_fn createGlobalUser =
        (Steam_CreateGlobalUser_fn) GetProcAddress(lsc, "Steam_CreateGlobalUser");
    Steam_BLoggedOn_fn bLoggedOn =
        (Steam_BLoggedOn_fn) GetProcAddress(lsc, "Steam_BLoggedOn");
    Steam_BGetCallback_fn bGetCallback =
        (Steam_BGetCallback_fn) GetProcAddress(lsc, "Steam_BGetCallback");
    Steam_FreeLastCallback_fn freeLastCallback =
        (Steam_FreeLastCallback_fn) GetProcAddress(lsc, "Steam_FreeLastCallback");
    Breakpad_SteamSetAppID_fn breakpadSetAppId =
        (Breakpad_SteamSetAppID_fn) GetProcAddress(lsc, "Breakpad_SteamSetAppID");

    log_line("[wn-launcher] exports CreateInterface=%p CreateGlobalUser=%p "
             "BLoggedOn=%p BGetCallback=%p FreeLastCallback=%p Breakpad=%p",
             (void*) createInterface, (void*) createGlobalUser, (void*) bLoggedOn,
             (void*) bGetCallback, (void*) freeLastCallback, (void*) breakpadSetAppId);

    if (!createInterface || !createGlobalUser) {
        log_line("[wn-launcher] required steamclient exports missing");
        return 3;
    }

    if (breakpadSetAppId && appId != 0) {
        breakpadSetAppId(appId);
        log_line("[wn-launcher] Breakpad_SteamSetAppID(%u)", appId);
    }

    int retCode = 0;
    void* engine = createInterface("CLIENTENGINE_INTERFACE_VERSION005", &retCode);
    log_line("[wn-launcher] CreateInterface(CLIENTENGINE_INTERFACE_VERSION005) -> %p rc=%d",
             engine, retCode);
    if (!engine) {
        engine = createInterface("CLIENTENGINE_INTERFACE_VERSION004", &retCode);
        log_line("[wn-launcher] CreateInterface(CLIENTENGINE_INTERFACE_VERSION004) -> %p rc=%d",
                 engine, retCode);
    }
    if (!engine) {
        log_line("[wn-launcher] failed to acquire IClientEngine");
        return 4;
    }

    int pipe = 0;
    int hUser = createGlobalUser(&pipe);
    log_line("[wn-launcher] Steam_CreateGlobalUser -> pipe=%d user=%d",
             pipe, hUser);
    if (pipe == 0 || hUser == 0) {
        log_line("[wn-launcher] invalid pipe/user from Steam_CreateGlobalUser");
        return 5;
    }

    if (user && *user && token && *token && steamId != 0) {
        void** engine_vt = *(void***) engine;
        typedef void* (WN_THISCALL *GetIClientUserFn)(void* self, int hUser, int hPipe, const char*);
        GetIClientUserFn getIClientUser = (GetIClientUserFn)
            engine_vt[kVtEngine_GetIClientUser / 8];
        void* iuser = getIClientUser(engine, hUser, pipe, "CLIENTUSER_INTERFACE_VERSION001");
        log_line("[wn-launcher] IClientEngine.GetIClientUser -> %p", iuser);
        if (iuser) {
            void** iuser_vt = *(void***) iuser;
            if (is_exec_ptr(iuser_vt[kVtUser_BHasCachedCreds / 8])) {
                typedef bool (WN_THISCALL *HasCachedCredsFn)(void* self, const char*);
                HasCachedCredsFn hasCachedCreds = (HasCachedCredsFn)
                    iuser_vt[kVtUser_BHasCachedCreds / 8];
                bool cached = hasCachedCreds(iuser, user);
                log_line("[wn-launcher] BHasCachedCredentials(%s) -> %d", user, cached ? 1 : 0);
            }
            if (is_exec_ptr(iuser_vt[kVtUser_SetLoginToken / 8])) {
                typedef int (WN_THISCALL *SetLoginTokenFn)(void* self, const char* token,
                                               const char* account);
                SetLoginTokenFn setLoginToken = (SetLoginTokenFn)
                    iuser_vt[kVtUser_SetLoginToken / 8];
                int tokRc = setLoginToken(iuser, token, user);
                log_line("[wn-launcher] SetLoginToken(tokenLen=%d, account=%s) -> %d",
                         (int) strlen(token), user, tokRc);

                typedef void* (WN_THISCALL *GetSteamIDFn)(void* self, void* outBuf);
                GetSteamIDFn getSteamID = (GetSteamIDFn)
                    iuser_vt[kVtUser_GetSteamID / 8];
                uint64_t outSid = 0;
                void* sidRet = getSteamID(iuser, &outSid);
                uint64_t logonSid = outSid;
                if (logonSid == 0 && sidRet) logonSid = *(uint64_t*) sidRet;
                if (logonSid == 0) {
                    logonSid = steamId;  // fall back to the env-supplied SteamID
                    log_line("[wn-launcher] GetSteamID returned 0 — falling back "
                             "to env steamId=%llu", (unsigned long long) steamId);
                } else {
                    log_line("[wn-launcher] GetSteamID -> %llu (env steamId=%llu)",
                             (unsigned long long) logonSid,
                             (unsigned long long) steamId);
                }

                typedef int (WN_THISCALL *LogOnFn)(void* self, uint64_t steamID);
                LogOnFn logOn = (LogOnFn) iuser_vt[kVtUser_LogOn / 8];
                int logonRc = logOn(iuser, logonSid);
                log_line("[wn-launcher] LogOn(%llu) -> EResult=%d "
                         "(1=OK 5=InvalidPassword 15=AccessDenied 16=Timeout 84=RateLimit)",
                         (unsigned long long) logonSid, logonRc);
                if (logonRc == 15) {
                    log_line("[wn-launcher] WARNING: LogOn returned AccessDenied "
                             "synchronously — credentials rejected pre-network");
                }
            }
        }
    } else {
        log_line("[wn-launcher] no creds — skipping refresh-token logon "
                 "(game may run in offline / no-auth mode)");
    }

    bool loggedOn = false;
    bool cleanShutdownArmed = false;
    bool sawConnected = false, sawConnFail = false;
    int  connFailEResult = 0;
    int  polls = 0;
    if (bLoggedOn) {
        const int kMaxPolls = 600;  // 600 * 100ms = 60s
        char cbBuf[64] = {0};
        for (; polls < kMaxPolls; ++polls) {
            if (bGetCallback && freeLastCallback) {
                while (bGetCallback(pipe, cbBuf)) {
                    int cbId = *(int*)(cbBuf + 4);
                    void* param = *(void**)(cbBuf + 8);
                    if (cbId == 101) {
                        sawConnected = true;
                        log_line("[wn-launcher] callback 101 SteamServersConnected");
                    } else if (cbId == 102) {
                        sawConnFail = true;
                        int er = param ? *(int*)param : -1;
                        connFailEResult = er;
                        log_line("[wn-launcher] callback 102 SteamServerConnectFailure "
                                 "EResult=%d (3=NoConnection 5=InvalidPassword "
                                 "15=AccessDenied 16=Timeout 84=RateLimit)", er);
                    } else if (cbId == 103) {
                        int er = param ? *(int*)param : -1;
                        log_line("[wn-launcher] callback 103 SteamServersDisconnected "
                                 "EResult=%d", er);
                    } else {
                        log_line("[wn-launcher] callback id=%d drained", cbId);
                    }
                    freeLastCallback(pipe);
                }
            }
            if (bLoggedOn(pipe, hUser)) {
                loggedOn = true;
                log_line("[wn-launcher] Steam_BLoggedOn=true after %dx100ms",
                         polls + 1);
                wn_launcher_arm_clean_shutdown(lsc, pipe, hUser, "C:\\wn-launcher.log");
                cleanShutdownArmed = true;
                break;
            }
            if (sawConnFail && (connFailEResult == 5 ||
                                connFailEResult == 15 ||
                                connFailEResult == 84)) {
                log_line("[wn-launcher] hard auth failure (EResult=%d) — "
                         "skipping remaining logon wait", connFailEResult);
                break;
            }
            Sleep(100);
        }
    }
    if (!loggedOn) {
        log_line("[wn-launcher] WARNING: Steam_BLoggedOn not true after %dx100ms "
                 "(sawConnected=%d sawConnFail=%d) — proceeding with game launch "
                 "anyway (game may end up in offline mode)",
                 polls, sawConnected ? 1 : 0, sawConnFail ? 1 : 0);
    }

    if (loggedOn && engine && appId != 0) {
        void** engine_vt = *(void***) engine;
        typedef void* (WN_THISCALL *GetIClientAppsFn)(void* self, int hUser, int hPipe);
        GetIClientAppsFn getApps = (GetIClientAppsFn)
            engine_vt[kVtEngine_GetIClientApps / 8];
        void* iApps = getApps(engine, hUser, pipe);
        log_line("[wn-launcher] IClientEngine.GetIClientApps -> %p", iApps);
        if (iApps) {
            void** apps_vt = *(void***) iApps;
            void* reqP = apps_vt[kVtApps_RequestAppInfoUpdate / 8];
            if (!is_exec_ptr(reqP)) {
                log_line("[wn-launcher] RequestAppInfoUpdate slot not executable — "
                         "skipping appinfo refresh");
            } else {
                typedef bool (WN_THISCALL *RequestAppInfoUpdateFn)(void* self,
                                                       uint32_t* appIds, int count);
                RequestAppInfoUpdateFn reqInfo = (RequestAppInfoUpdateFn) reqP;
                uint32_t appIds[1] = { appId };
                bool reqRc = reqInfo(iApps, appIds, 1);
                log_line("[wn-launcher] RequestAppInfoUpdate(appId=%u) -> %d",
                         appId, reqRc ? 1 : 0);
                // 1.5s ceiling: give steamclient's PICS fetch time to land, else
                // LaunchApp returns MissingConfig(9). Safe to keep short — the
                // dispatch below retries on MissingConfig; this only covers the
                // warm-cache case. (1003 callback rarely fires, so usually full wait.)
                bool appInfoDone = false;
                int  waited = 0;
                for (int i = 0; i < 15 && !appInfoDone; ++i) {
                    if (bGetCallback && freeLastCallback) {
                        char cb[64];
                        while (bGetCallback(pipe, cb)) {
                            if (*(int*)(cb + 4) == 1003) appInfoDone = true;
                            freeLastCallback(pipe);
                        }
                    }
                    if (!appInfoDone) { Sleep(100); waited += 100; }
                }
                log_line("[wn-launcher] AppInfoUpdateComplete_t %s after %dms",
                         appInfoDone ? "received" : "NOT received", waited);
            }
        }
    }

    if (loggedOn && engine && appId != 0) {
        void** engine_vt = *(void***) engine;
        typedef void* (WN_THISCALL *GetIfaceFn)(void* self, int hUser, int hPipe);
        void* appMgr = ((GetIfaceFn) engine_vt[kVtEngine_GetIClientAppManager / 8])
                           (engine, hUser, pipe);
        log_line("[wn-launcher] readiness: IClientAppManager=%p", appMgr);

        if (appMgr) {
            void** am_vt = *(void***) appMgr;
            void* refreshP = am_vt[kVtAppMgr_RefreshAppInfo / 8];
            void* stateP   = am_vt[kVtAppMgr_GetAppInstallState / 8];
            if (is_exec_ptr(refreshP)) {
                typedef void (WN_THISCALL *RefreshAppInfoFn)(void* self);
                ((RefreshAppInfoFn) refreshP)(appMgr);
                log_line("[wn-launcher] RefreshAppInfo() called");
            }
            if (is_exec_ptr(stateP)) {
                typedef int (WN_THISCALL *GetAppInstallStateFn)(void* self, uint32_t app);
                GetAppInstallStateFn getInstallState = (GetAppInstallStateFn) stateP;
                // 2s ceiling — the launcher itself wrote StateFlags=4 into
                // the .acf in stage_app_manifest, so RefreshAppInfo() above
                // should already make this poll return FullyInstalled on the
                // first iteration. The loop remains only to absorb a slow
                // appmanifest re-parse on the steamclient side.
                int st = 0;
                for (int i = 0; i < 20; ++i) {
                    st = getInstallState(appMgr, appId);
                    if (st & 4) break;
                    if (bGetCallback && freeLastCallback) {
                        char cb[64];
                        while (bGetCallback(pipe, cb)) freeLastCallback(pipe);
                    }
                    Sleep(100);
                }
                log_line("[wn-launcher] GetAppInstallState(appId=%u) = 0x%x (%s)",
                         appId, st,
                         (st & 4) ? "FullyInstalled"
                                  : "NOT installed — LaunchApp may no-op");
            }
        }
    }

    scan_and_install_redists(gameExe);

    bool svcRunning = start_steam_client_service();
    log_line("[wn-launcher] steamservice running: %d", svcRunning ? 1 : 0);

    const char* exeName = strrchr(gameExe, '\\');
    exeName = exeName ? exeName + 1 : gameExe;

    // Hand the game image name to the clean-shutdown module so its teardown can
    // stop the game before logging off — that exit is what makes steamclient
    // emit the games-played([]) reap, preventing AlreadyRunning on the next
    // launch (the clean Steam_LogOff alone does not clear it).
    wn_launcher_set_game_exe(exeName);

    bool launchedViaApp = false;
    bool launchedViaFallback = false;
    const char* launchFailureReason = "LaunchApp path unavailable";
    if (engine && appId != 0) {
        void** engine_vt = *(void***) engine;
        typedef void* (WN_THISCALL *GetIClientAppManagerFn)(void* self, int hUser, int hPipe);
        GetIClientAppManagerFn getAppMgr = (GetIClientAppManagerFn)
            engine_vt[kVtEngine_GetIClientAppManager / 8];
        void* appMgr = getAppMgr(engine, hUser, pipe);
        log_line("[wn-launcher] IClientEngine.GetIClientAppManager -> %p", appMgr);
        if (appMgr) {
            void** appMgr_vt = *(void***) appMgr;
            typedef uint64_t (WN_THISCALL *LaunchAppFn)(void* self, void* pGameId,
                                            uint32_t uLaunchOption,
                                            uint32_t eLaunchSource,
                                            const char* pszUserArgs);
            LaunchAppFn launchApp = (LaunchAppFn)
                appMgr_vt[kVtAppMgr_LaunchApp / 8];
            uint64_t gameId = (uint64_t)(appId & 0xFFFFFFu);

            // RefreshAppInfo() slot — re-primes appinfo between MissingConfig retries.
            void* refreshAppInfoP = appMgr_vt[kVtAppMgr_RefreshAppInfo / 8];

            // A cold launch often sees 1-2 MissingConfig(9) before PICS appinfo lands;
            // those retries are fast, so 5 attempts stays inside the 35s watchdog.
            const int kMaxLaunchAttempts = 5;
            for (int attempt = 1; attempt <= kMaxLaunchAttempts && !launchedViaApp; ++attempt) {
                uint64_t apiCall = launchApp(appMgr, &gameId, 0, 300, "");
                log_line("[wn-launcher] IClientAppManager.LaunchApp(appId=%u) "
                         "attempt=%d/%d -> HSteamAPICall=0x%llx", appId,
                         attempt, kMaxLaunchAttempts,
                         (unsigned long long) apiCall);

            int eAppError = -1;  // -1 = not polled / unknown
            if (apiCall != 0) {
                typedef void* (WN_THISCALL *GetIClientUtilsFn)(void* self, int hPipe);
                GetIClientUtilsFn getUtils = (GetIClientUtilsFn)
                    engine_vt[kVtEngine_GetIClientUtils / 8];
                void* utils = getUtils(engine, pipe);
                log_line("[wn-launcher] IClientEngine.GetIClientUtils -> %p", utils);
                if (utils) {
                    void** utils_vt = *(void***) utils;
                    void* isCompletedP = utils_vt[kVtUtils_IsAPICallCompleted / 8];
                    void* getResultP   = utils_vt[kVtUtils_GetAPICallResult / 8];
                    void* getReasonP   = utils_vt[kVtUtils_GetAPICallFailureReason / 8];
                    log_line("[wn-launcher] utils vt IsAPICallCompleted=%p "
                             "GetAPICallFailureReason=%p GetAPICallResult=%p",
                             isCompletedP, getReasonP, getResultP);
                    if (is_exec_ptr(isCompletedP) && is_exec_ptr(getResultP)) {
                        typedef bool (WN_THISCALL *IsAPICallCompletedFn)(void* self,
                                                       uint64_t apiCall, bool* pbFailed);
                        typedef int  (WN_THISCALL *GetFailureReasonFn)(void* self,
                                                       uint64_t apiCall);
                        typedef bool (WN_THISCALL *GetAPICallResultFn)(void* self,
                                                       uint64_t apiCall, void* pCb,
                                                       int cubCb, int iCbExpected,
                                                       bool* pbFailed);
                        IsAPICallCompletedFn isCompleted = (IsAPICallCompletedFn) isCompletedP;
                        GetFailureReasonFn   getReason   = (GetFailureReasonFn) getReasonP;
                        GetAPICallResultFn   getResult   = (GetAPICallResultFn) getResultP;

                        const int kPollMaxMs = 10000;
                        int  waited = 0;
                        bool failed = false;
                        bool completed = false;
                        while (waited < kPollMaxMs) {
                            failed = false;
                            completed = isCompleted(utils, apiCall, &failed);
                            if (completed) break;
                            if (bGetCallback && freeLastCallback) {
                                char cb[64];
                                while (bGetCallback(pipe, cb)) freeLastCallback(pipe);
                            }
                            Sleep(100);
                            waited += 100;
                        }
                        if (!completed) {
                            log_line("[wn-launcher] LaunchApp poll: TIMED OUT "
                                     "after %dms — job still pending", waited);
                        } else if (failed) {
                            int reason = is_exec_ptr(getReasonP) ? getReason(utils, apiCall) : -99;
                            log_line("[wn-launcher] LaunchApp poll: API CALL FAILED "
                                     "after %dms, reason=%d "
                                     "(-1=NoFailure 0=SteamGone 1=NetworkFailure "
                                     "2=InvalidHandle 3=MismatchedCallback)",
                                     waited, reason);
                        } else {
                            unsigned char buf[kLaunchAppResultSize];
                            memset(buf, 0, sizeof(buf));
                            bool resFailed = false;
                            bool got = getResult(utils, apiCall, buf,
                                                  kLaunchAppResultSize,
                                                  kLaunchAppResultCallbackId,
                                                  &resFailed);
                            eAppError = *(int*)(buf + kLaunchResultErrorOffset);
                            log_line("[wn-launcher] LaunchApp poll: COMPLETED in %dms "
                                     "got=%d resFailed=%d EAppUpdateError=%d "
                                     "(0=NoError 1=Unspecified 2=Paused 3=Cancelled "
                                     "4=Suspended 5=NoSubscription 6=NoConnection "
                                     "7=Timeout 8=MissingKey 9=MissingConfig "
                                     "0xE=AppLocked 0xF=OtherSessionPlaying "
                                     "0x10=AlreadyRunning 0x21=33 0x23=35 0x2D=45)",
                                     waited, got ? 1 : 0, resFailed ? 1 : 0, eAppError);
                            char hex[3 * 32 + 1];
                            int hp = 0;
                            for (int i = 0; i < 32; ++i) {
                                hp += snprintf(hex + hp, sizeof(hex) - hp, "%02x ", buf[i]);
                            }
                            log_line("[wn-launcher] LaunchApp result hex+0..32: %s", hex);
                        }
                    } else {
                        log_line("[wn-launcher] LaunchApp poll: IClientUtils vtable "
                                 "slots not executable — skipping poll");
                    }
                }
            }

            if (apiCall == 0) {
                if (attempt < kMaxLaunchAttempts) {
                    log_line("[wn-launcher] LaunchApp attempt %d/%d: \"%s\" never "
                             "appeared — null call handle, retrying LaunchApp",
                             attempt, kMaxLaunchAttempts, exeName);
                    Sleep(500);
                } else {
                    log_line("[wn-launcher] LaunchApp returned a null call handle "
                             "after %d attempts", kMaxLaunchAttempts);
                    launchFailureReason = "LaunchApp returned a null call handle";
                }
                continue;
            }

            if (eAppError == 9 /* MissingConfig */) {
                // Launch-config appinfo not landed yet — re-prime appinfo, let the
                // fetch settle, then retry fast (nothing was launched). The "never
                // appeared … retrying LaunchApp" wording disarms the Android watchdog.
                if (is_exec_ptr(refreshAppInfoP)) {
                    typedef void (WN_THISCALL *RefreshAppInfoFn)(void* self);
                    ((RefreshAppInfoFn) refreshAppInfoP)(appMgr);
                }
                log_line("[wn-launcher] LaunchApp attempt %d/%d: \"%s\" never "
                         "appeared — MissingConfig (appinfo not ready); refreshed "
                         "appinfo, retrying LaunchApp", attempt,
                         kMaxLaunchAttempts, exeName);
                for (int w = 0; w < 30; ++w) {  // ~3s of callback pumping
                    if (bGetCallback && freeLastCallback) {
                        char cb[64];
                        while (bGetCallback(pipe, cb)) freeLastCallback(pipe);
                    }
                    Sleep(100);
                }
            } else if (eAppError > 0 /* a real error, e.g. AlreadyRunning(0x10) */) {
                // Not retryable in-process (e.g. AlreadyRunning = a prior session's
                // games-played still live server-side) — go straight to the fallback.
                log_line("[wn-launcher] LaunchApp attempt %d/%d: \"%s\" never "
                         "appeared — EAppUpdateError=%d%s; not retryable in-process "
                         "— falling back", attempt, kMaxLaunchAttempts, exeName,
                         eAppError,
                         eAppError == 0x10
                             ? " (AlreadyRunning — prior session's games-played "
                               "registration still live server-side)"
                             : "");
                launchFailureReason = (eAppError == 0x10)
                    ? "LaunchApp returned AlreadyRunning (stale server session)"
                    : "LaunchApp returned a non-NoError EAppUpdateError";
                break;
            } else {
                // NoError(0)/indeterminate(-1): launch accepted. Wait for the game
                // WITHOUT re-dispatching — a second LaunchApp while one is pending
                // cancels the in-flight spawn under Wine.
                const int kGameAppearLoops = 40;  // 40 * 500ms = 20s
                log_line("[wn-launcher] LaunchApp dispatched (attempt %d/%d, "
                         "EAppUpdateError=%d); waiting up to %ds for \"%s\" to "
                         "appear (committed — no re-dispatch)",
                         attempt, kMaxLaunchAttempts, eAppError,
                         kGameAppearLoops / 2, exeName);
                for (int w = 0; w < kGameAppearLoops && !launchedViaApp; ++w) {
                    if (count_process_by_name(exeName) > 0) {
                        launchedViaApp = true;
                        break;
                    }
                    if (bGetCallback && freeLastCallback) {
                        char cb[64];
                        while (bGetCallback(pipe, cb)) freeLastCallback(pipe);
                    }
                    Sleep(500);
                }
                if (launchedViaApp) {
                    log_line("[wn-launcher] LaunchApp: \"%s\" is running "
                             "(attempt %d/%d)", exeName, attempt,
                             kMaxLaunchAttempts);
                } else {
                    log_line("[wn-launcher] LaunchApp attempt %d/%d: \"%s\" "
                             "accepted (EAppUpdateError=%d) but never spawned in "
                             "%ds — not re-dispatching (would cancel the pending "
                             "launch) — falling back", attempt, kMaxLaunchAttempts,
                             exeName, eAppError, kGameAppearLoops / 2);
                    launchFailureReason =
                        "LaunchApp accepted but the game never spawned";
                    break;
                }
            }
            }
        } else {
            launchFailureReason = "IClientAppManager was null";
        }
    } else {
        launchFailureReason = engine ? "appId was 0" : "IClientEngine was null";
    }

    // Integrated LaunchApp didn't bring the game up — start it directly. The
    // clean-shutdown arm above still sends a clean Steam_LogOff on close, so the
    // direct launch no longer leaves the app stuck "running" (the root cause of
    // the AlreadyRunning failure on the *next* launch). The log line below
    // carries the "LaunchApp dispatched" + "never appeared" + "falling back to
    // CreateProcess" markers the Android-side WnLauncherStatusTailer keys off to
    // disarm its post-dispatch watchdog and show the direct-launch phase.
    if (!launchedViaApp) {
        log_line("[wn-launcher] LaunchApp dispatched but \"%s\" never appeared "
                 "— falling back to CreateProcess (%s)",
                 exeName, launchFailureReason);
        launchedViaFallback = create_process_game(gameExe, exeName);
    }

    if (launchedViaApp || launchedViaFallback) {
        const char* path = launchedViaApp ? "LaunchApp path"
                                           : "CreateProcess fallback";
        log_line("[wn-launcher] watching \"%s\" for exit (%s)", exeName, path);
        // Declare exit after 2 consecutive absent polls (~2s) — tolerates a brief
        // gap while staying snappy (4 added ~4s of dead latency after quit).
        int absent = 0;
        while (absent < 2) {
            Sleep(1000);
            if (bGetCallback && freeLastCallback) {
                char cb[64];
                while (bGetCallback(pipe, cb)) freeLastCallback(pipe);
            }
            absent = (count_process_by_name(exeName) != 0) ? 0 : absent + 1;
        }
        log_line("[wn-launcher] game \"%s\" exited (%s)", exeName, path);
        if (cleanShutdownArmed) {
            wn_launcher_clean_shutdown_now("game-exit");
            // If the Android close path's sentinel already kicked off teardown on
            // the watcher thread, block here until it finishes so returning from
            // main() doesn't terminate the process mid-reap (which would cut the
            // games-played/logoff flush short and bring back AlreadyRunning).
            wn_launcher_wait_clean_shutdown(12000);
        }
        log_line("[wn-launcher] Steam Launcher shutdown");
        return 0;
    }

    log_line("[wn-launcher] could not start \"%s\" via LaunchApp or CreateProcess "
             "(%s)", exeName, launchFailureReason);
    if (cleanShutdownArmed) wn_launcher_clean_shutdown_now("launch-failed");
    return 9;
}
