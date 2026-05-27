
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
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

static std::string game_dir_of(const char* gameExePath) {
    if (!gameExePath || !*gameExePath) return {};
    std::string p(gameExePath);
    while (!p.empty() && (p.back() == '\\' || p.back() == '/')) p.pop_back();
    auto pos = p.find_last_of("\\/");
    if (pos == std::string::npos) return {};
    return p.substr(0, pos);
}

static bool redist_already_installed(const std::string& name, uint64_t size) {
    FILE* f = fopen(kRedistsMarkerPath, "r");
    if (!f) return false;
    char line[1024];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        if (line[0] == '#' || line[0] == '\n' || line[0] == '\0') continue;
        char* tab1 = strchr(line, '\t');
        if (!tab1) continue;
        *tab1 = '\0';  // terminate name at first tab so name == line compares only the filename
        char* tab2 = strchr(tab1 + 1, '\t');
        if (!tab2) continue;
        uint64_t lineSize = _strtoui64(tab1 + 1, nullptr, 10);
        if (name == line && lineSize == size) { found = true; break; }
    }
    fclose(f);
    return found;
}

static void mark_redist_installed(const std::string& name, uint64_t size,
                                  uint32_t exitCode) {
    bool needHeader = false;
    {
        FILE* probe = fopen(kRedistsMarkerPath, "r");
        if (!probe) needHeader = true; else fclose(probe);
    }
    FILE* f = fopen(kRedistsMarkerPath, "a");
    if (!f) {
        log_line("[wn-launcher] redist mark: cannot open %s for append (GLE=%lu)",
                 kRedistsMarkerPath, GetLastError());
        return;
    }
    if (needHeader) fprintf(f, "# wn-installed-redists v1\n");
    fprintf(f, "%s\t%llu\t%llu\t%lu\n", name.c_str(),
            (unsigned long long) size, (unsigned long long) time(nullptr),
            (unsigned long) exitCode);
    fclose(f);
}

static std::string to_lower(const std::string& s) {
    std::string r = s;
    for (auto& c : r) c = (char) tolower((unsigned char) c);
    return r;
}

static std::string silent_args_for(const std::string& filename) {
    std::string lower = to_lower(filename);
    if (lower == "dxsetup.exe") return "/silent";
    if (lower.rfind("vc_redist", 0) == 0) return "/quiet /norestart";
    if (lower.rfind("vcredist_", 0) == 0) return "/q /norestart";
    if (lower == "oalinst.exe") return "/silent";
    if (lower.find("physx") != std::string::npos) return "/quiet";
    if (lower.rfind("dotnetfx", 0) == 0
            || lower.rfind("dotnet", 0) == 0
            || lower.rfind("ndp", 0) == 0) {
        return "/q /norestart";
    }
    if (lower.find("prereqsetup") != std::string::npos
            || lower.find("ue4prereq") != std::string::npos
            || lower.find("ue5prereq") != std::string::npos) {
        return "/quiet /norestart";
    }
    if (lower == "setup.exe") return "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART";
    return "/quiet /norestart";
}

enum class RedistInstallResult { OK, FAILED, TIMEOUT };

static RedistInstallResult run_redist_installer(
        const std::filesystem::path& installer, uint32_t* outExitCode) {
    std::string fn = installer.filename().string();
    std::string args = silent_args_for(fn);
    std::string cmd;
    cmd.reserve(installer.string().size() + args.size() + 4);
    cmd += '"';
    cmd += installer.string();
    cmd += "\" ";
    cmd += args;

    log_line("[wn-launcher] redist install: spawning %s with args: %s",
             fn.c_str(), args.c_str());

    STARTUPINFOA si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi = {};
    std::string cwdStr = installer.parent_path().string();
    if (!CreateProcessA(
            installer.string().c_str(),
            cmd.data(),
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
                 "flag '%s' likely incorrect for this installer family); "
                 "killing process. Not marking as installed so the user "
                 "can retry once the right flag is added.",
                 fn.c_str(), args.c_str());
        TerminateProcess(pi.hProcess, 1);
        WaitForSingleObject(pi.hProcess, 5000);
        timedOut = true;
        exitCode = 0xFFFFFFFEu;  // sentinel for "killed by our timeout"
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    if (outExitCode) *outExitCode = exitCode;
    if (timedOut) {
        return RedistInstallResult::TIMEOUT;
    }
    bool ok = (exitCode == 0 || exitCode == 3010);
    log_line("[wn-launcher] redist install: %s exit=%lu (%s)",
             fn.c_str(), exitCode,
             ok ? "ok" : "fail (marking anyway — installer reported a "
                         "definitive non-zero status; treating as "
                         "\"tried, don't retry\" so the splash isn't "
                         "stuck re-running it every launch)");
    return ok ? RedistInstallResult::OK : RedistInstallResult::FAILED;
}

static void scan_and_install_redists(const char* gameExePath) {
    std::string gameDir = game_dir_of(gameExePath);
    if (gameDir.empty()) {
        log_line("[wn-launcher] redist scan: cannot derive game dir from \"%s\"",
                 gameExePath ? gameExePath : "(null)");
        return;
    }
    std::filesystem::path redistRoot = std::filesystem::path(gameDir) / "_CommonRedist";
    std::error_code ec;
    if (!std::filesystem::is_directory(redistRoot, ec)) {
        log_line("[wn-launcher] redist scan: no _CommonRedist at %s — skipping",
                 redistRoot.string().c_str());
        return;
    }
    log_line("[wn-launcher] redist scan: scanning %s", redistRoot.string().c_str());

    std::vector<std::filesystem::path> installers;
    for (auto it = std::filesystem::recursive_directory_iterator(
                redistRoot, std::filesystem::directory_options::skip_permission_denied, ec);
         it != std::filesystem::recursive_directory_iterator(); ++it) {
        if (ec) {
            log_line("[wn-launcher] redist scan: iteration error: %s",
                     ec.message().c_str());
            ec.clear();
            continue;
        }
        const auto& p = it->path();
        if (!it->is_regular_file(ec)) continue;
        std::string ext = p.extension().string();
        for (auto& c : ext) c = (char) tolower((unsigned char) c);
        if (ext != ".exe") continue;
        installers.push_back(p);
    }

    if (installers.empty()) {
        log_line("[wn-launcher] redist scan: 0 *.exe installers under %s",
                 redistRoot.string().c_str());
        return;
    }
    log_line("[wn-launcher] redist scan: found %zu installer(s)", installers.size());

    int installed = 0, skipped = 0, failedMarked = 0, timedOut = 0;
    int idx = 0;
    for (const auto& installer : installers) {
        ++idx;
        std::string name = installer.filename().string();
        uintmax_t sizeUm = std::filesystem::file_size(installer, ec);
        uint64_t size = ec ? 0 : (uint64_t) sizeUm;
        ec.clear();
        if (redist_already_installed(name, size)) {
            log_line("[wn-launcher] redistributable %s already installed in this "
                     "container, skipping (%d/%zu)",
                     name.c_str(), idx, installers.size());
            ++skipped;
            continue;
        }
        log_line("[wn-launcher] installing redistributable: %s (%d/%zu, %llu bytes)",
                 name.c_str(), idx, installers.size(),
                 (unsigned long long) size);
        uint32_t exitCode = 0;
        RedistInstallResult r = run_redist_installer(installer, &exitCode);
        switch (r) {
            case RedistInstallResult::OK:
                mark_redist_installed(name, size, exitCode);
                ++installed;
                break;
            case RedistInstallResult::FAILED:
                mark_redist_installed(name, size, exitCode);
                ++failedMarked;
                break;
            case RedistInstallResult::TIMEOUT:
                ++timedOut;
                break;
        }
    }
    log_line("[wn-launcher] redist scan: installed %d, skipped %d, "
             "failed-marked %d, timed-out-unmarked %d (of %zu total)",
             installed, skipped, failedMarked, timedOut, installers.size());
}

int main(int argc, char** argv) {
    setbuf(stderr, NULL);
    setbuf(stdout, NULL);
    open_log();
    log_line("[wn-launcher] Steam Launcher in-process Steam launcher starting (pid=%lu tid=%lu)",
             (unsigned long) GetCurrentProcessId(),
             (unsigned long) GetCurrentThreadId());

    if (argc < 2) {
        log_line("[wn-launcher] FATAL: missing game exe argv[1]");
        return 1;
    }
    const char* gameExe = argv[1];

    const char* token   = getenv("WN_STEAM_TOKEN");
    const char* user    = getenv("WN_STEAM_USERNAME");
    uint64_t    steamId = env_u64("WN_STEAM_STEAMID");
    uint32_t    appId   = (uint32_t) env_u64("WN_STEAM_APPID");
    bool        haveCreds = token && *token && user && *user && steamId != 0;
    log_line("[wn-launcher] game=\"%s\"", gameExe);
    log_line("[wn-launcher] creds: user=\"%s\" steamId=%llu tokenLen=%d appId=%u",
             user ? user : "(null)",
             (unsigned long long) steamId,
             token ? (int) strlen(token) : 0,
             appId);
    log_token_claims(token);

    uint32_t accId = (uint32_t)(steamId & 0xFFFFFFFFull);
    seed_active_process_registry(GetCurrentProcessId(), accId);

    stage_steam_config();

    stage_app_manifest(appId, gameExe);

    {
        const char* sslCert = getenv("STEAM_SSL_CERT_FILE");
        log_line("[wn-launcher] STEAM_SSL_CERT_FILE=%s",
                 (sslCert && *sslCert) ? sslCert : "(unset)");
    }

    {
        const char* probes[] = {
            "PROTON_DISABLE_LSTEAMCLIENT",
            "WINEDLLOVERRIDES",
            "WINEDEBUG",
            "WINEESYNC",
            "WINENTSYNC",
            "PROTON_USE_WOW64",
        };
        for (size_t i = 0; i < sizeof(probes)/sizeof(probes[0]); ++i) {
            const char* libc = getenv(probes[i]);
            char winv[256] = {0};
            DWORD wlen = GetEnvironmentVariableA(probes[i], winv, sizeof(winv));
            log_line("[wn-launcher] env probe: %-30s libc=%-10s win32=%s",
                     probes[i],
                     (libc && *libc) ? libc : "(unset)",
                     (wlen > 0 && wlen < sizeof(winv)) ? winv : "(unset)");
        }
    }

    const char* kSteamDir = "C:\\Program Files (x86)\\Steam";
    SetDllDirectoryA(kSteamDir);
    {
        struct Preload { const char* name; bool fullPath; };
        const Preload preloads[] = {
            { "msvcr120.dll", false }, { "msvcp120.dll", false },
            { "vcruntime140.dll", false }, { "msvcp140.dll", false },
            { "video64.dll", true }, { "SteamUI.dll", true },
        };
        for (const Preload& p : preloads) {
            HMODULE dm;
            if (p.fullPath) {
                char path[MAX_PATH];
                snprintf(path, sizeof(path), "%s\\%s", kSteamDir, p.name);
                dm = LoadLibraryExA(path, NULL, LOAD_WITH_ALTERED_SEARCH_PATH);
            } else {
                dm = LoadLibraryA(p.name);
            }
            if (dm) {
                log_line("[wn-launcher] preload %s: ok (%p)", p.name, dm);
            } else {
                log_line("[wn-launcher] preload %s: failed GLE=%lu (continuing)",
                         p.name, GetLastError());
            }
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
        { 0, "no flags" },
        { LOAD_LIBRARY_SEARCH_DEFAULT_DIRS, "SEARCH_DEFAULT_DIRS" },
        { LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_IGNORE_CODE_AUTHZ_LEVEL,
          "SEARCH_DEFAULT_DIRS + IGNORE_CFG" },
        { LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_SYSTEM32,
          "DLL_LOAD_DIR + SYSTEM32" },
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
        log_line("[wn-launcher] load strategy %d/%d (%s) FAILED, GLE=%lu",
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
            FreeLibrary(probe);
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
    Steam_BLoggedOn_fn        bLoggedOn =
        (Steam_BLoggedOn_fn) GetProcAddress(lsc, "Steam_BLoggedOn");
    Steam_BGetCallback_fn     bGetCallback =
        (Steam_BGetCallback_fn) GetProcAddress(lsc, "Steam_BGetCallback");
    Steam_FreeLastCallback_fn freeLastCallback =
        (Steam_FreeLastCallback_fn) GetProcAddress(lsc, "Steam_FreeLastCallback");
    Breakpad_SteamSetAppID_fn breakpadSetAppId =
        (Breakpad_SteamSetAppID_fn) GetProcAddress(lsc, "Breakpad_SteamSetAppID");

    if (!createInterface) {
        log_line("[wn-launcher] dlsym CreateInterface FAILED");
        return 3;
    }
    log_line("[wn-launcher] exports: CreateInterface=%p Steam_CreateGlobalUser=%p "
             "Steam_BLoggedOn=%p Steam_BGetCallback=%p",
             createInterface, createGlobalUser, bLoggedOn, bGetCallback);

    if (breakpadSetAppId) {
        breakpadSetAppId(appId);
    }

    int pipe = 0;
    int hUser = 0;
    if (createGlobalUser) {
        hUser = createGlobalUser(&pipe);
    } else {
        log_line("[wn-launcher] Steam_CreateGlobalUser missing; falling back "
                 "to ISteamClient.CreateSteamPipe (not implemented yet)");
        return 4;
    }
    if (hUser == 0 || pipe == 0) {
        log_line("[wn-launcher] Steam_CreateGlobalUser failed pipe=%d user=%d",
                 pipe, hUser);
        return 4;
    }
    log_line("[wn-launcher] Steam_CreateGlobalUser OK pipe=%d user=%d",
             pipe, hUser);

    void* engine = NULL;
    if (haveCreds) {
        int err = 0;
        engine = createInterface("CLIENTENGINE_INTERFACE_VERSION005", &err);
        if (!engine || err != 0) {
            log_line("[wn-launcher] CreateInterface(CLIENTENGINE_INTERFACE_VERSION005) "
                     "-> engine=%p err=%d", engine, err);
        } else {
            void** engine_vt = *(void***) engine;
            log_line("[wn-launcher] engine=%p vtable=%p", engine, engine_vt);
            for (int i = 0; i < 16; ++i) {
                log_line("[wn-launcher] engine_vt[%2d] @ +0x%02x = %p",
                         i, i * 8, engine_vt[i]);
            }

            typedef void* (WN_THISCALL *GetIClientUserFn)(void* self, int hUser, int hPipe);
            GetIClientUserFn getIClientUser = (GetIClientUserFn)
                engine_vt[kVtEngine_GetIClientUser / 8];
            void* iuser = getIClientUser(engine, hUser, pipe);
            log_line("[wn-launcher] IClientEngine.GetIClientUser -> %p", iuser);

            if (iuser) {
                void** iuser_vt = *(void***) iuser;
                for (int i = 0; i < 60; ++i) {
                    log_line("[wn-launcher] iuser_vt[%2d] @ +0x%02x = %p",
                             i, i * 8, iuser_vt[i]);
                }


                typedef bool (WN_THISCALL *BHasCachedCredsFn)(void* self, const char* user);
                BHasCachedCredsFn hasCached = (BHasCachedCredsFn)
                    iuser_vt[kVtUser_BHasCachedCreds / 8];
                log_line("[wn-launcher] BHasCachedCredentials(%s) = %d",
                         user, hasCached(iuser, user) ? 1 : 0);

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
                // 3s ceiling. The callback rarely fires in this window, but
                // steamclient's background PICS fetch needs the wall-clock
                // time to land — without it LaunchApp returns EAppUpdateError=9
                // MissingConfig for games like MonsterHunterRise that gate on
                // fresh appinfo.
                bool appInfoDone = false;
                int  waited = 0;
                for (int i = 0; i < 30 && !appInfoDone; ++i) {
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

    char cmdline[4096];
    int  cmdpos = 0;
    cmdpos += snprintf(cmdline + cmdpos, sizeof(cmdline) - cmdpos,
                       "\"%s\"", gameExe);
    for (int i = 2; i < argc; ++i) {
        cmdpos += snprintf(cmdline + cmdpos, sizeof(cmdline) - cmdpos,
                           " \"%s\"", argv[i]);
        if (cmdpos >= (int) sizeof(cmdline) - 2) break;
    }
    cmdline[sizeof(cmdline) - 1] = '\0';

    char gameCwd[MAX_PATH];
    strncpy(gameCwd, gameExe, sizeof(gameCwd) - 1);
    gameCwd[sizeof(gameCwd) - 1] = '\0';
    { char* sep = strrchr(gameCwd, '\\'); if (sep) *sep = '\0'; }
    const char* exeName = strrchr(gameExe, '\\');
    exeName = exeName ? exeName + 1 : gameExe;

    bool launchedViaApp = false;
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

            const int kMaxLaunchAttempts = 2;
            for (int attempt = 1; attempt <= kMaxLaunchAttempts && !launchedViaApp; ++attempt) {
                uint64_t apiCall = launchApp(appMgr, &gameId, 0, 300, "");
                log_line("[wn-launcher] IClientAppManager.LaunchApp(appId=%u) "
                         "attempt=%d/%d -> HSteamAPICall=0x%llx", appId,
                         attempt, kMaxLaunchAttempts,
                         (unsigned long long) apiCall);

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
                            int eAppError = *(int*)(buf + kLaunchResultErrorOffset);
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

            if (apiCall != 0) {
                // 30s per attempt — covers normal spawn time. A second LaunchApp
                // call is issued by the outer for-loop if the game exe never
                // appears (transient steamservice failure, DRM handshake retry).
                // CreateProcess fallback only fires after both attempts time out.
                for (int i = 0; i < 60 && !launchedViaApp; ++i) {
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
                    log_line("[wn-launcher] LaunchApp: \"%s\" is running (attempt %d/%d)",
                             exeName, attempt, kMaxLaunchAttempts);
                } else if (attempt < kMaxLaunchAttempts) {
                    log_line("[wn-launcher] LaunchApp attempt %d/%d: \"%s\" never "
                             "appeared — retrying LaunchApp", attempt,
                             kMaxLaunchAttempts, exeName);
                } else {
                    log_line("[wn-launcher] LaunchApp dispatched but \"%s\" never "
                             "appeared after %d attempts — falling back to CreateProcess",
                             exeName, kMaxLaunchAttempts);
                }
            } else if (attempt < kMaxLaunchAttempts) {
                log_line("[wn-launcher] LaunchApp returned a null call handle "
                         "(attempt %d/%d) — retrying", attempt, kMaxLaunchAttempts);
            } else {
                log_line("[wn-launcher] LaunchApp returned a null call handle "
                         "after %d attempts — falling back to CreateProcess",
                         kMaxLaunchAttempts);
            }
            }
        }
    }

    if (launchedViaApp) {
        log_line("[wn-launcher] watching \"%s\" for exit (LaunchApp path)", exeName);
        int absent = 0;
        while (absent < 4) {
            Sleep(1000);
            if (bGetCallback && freeLastCallback) {
                char cb[64];
                while (bGetCallback(pipe, cb)) freeLastCallback(pipe);
            }
            absent = (count_process_by_name(exeName) != 0) ? 0 : absent + 1;
        }
        log_line("[wn-launcher] game \"%s\" exited (LaunchApp path)", exeName);
        log_line("[wn-launcher] Steam Launcher shutdown");
        return 0;
    }

    log_line("[wn-launcher] launching (CreateProcess): %s", cmdline);
    log_line("[wn-launcher] cwd: %s", gameCwd);
    STARTUPINFOA si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_SHOWNORMAL;
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessA(NULL, cmdline, NULL, NULL, FALSE, 0, NULL,
                        gameCwd, &si, &pi)) {
        log_line("[wn-launcher] CreateProcess FAILED GLE=%lu", GetLastError());
        return 8;
    }
    log_line("[wn-launcher] game process started pid=%lu — waiting for exit",
             pi.dwProcessId);

    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD exitCode = 0;
    GetExitCodeProcess(pi.hProcess, &exitCode);
    log_line("[wn-launcher] game process exited exitCode=%lu", exitCode);

    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);


    log_line("[wn-launcher] Steam Launcher shutdown");
    return 0;
}
