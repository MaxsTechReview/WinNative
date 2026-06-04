#include "clean_shutdown.h"

#include <windows.h>
#include <tlhelp32.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

namespace {

using Steam_LogOff_fn = void (*)(int, int);
using Steam_ReleaseUser_fn = void (*)(int, int);
using Steam_BReleaseSteamPipe_fn = bool (*)(int);
using Steam_BLoggedOn_fn = bool (*)(int, int);
using Steam_BGetCallback_fn = bool (*)(int, void*);
using Steam_FreeLastCallback_fn = void (*)(int);

Steam_LogOff_fn g_logoff = nullptr;
Steam_ReleaseUser_fn g_release_user = nullptr;
Steam_BReleaseSteamPipe_fn g_release_pipe = nullptr;
Steam_BLoggedOn_fn g_bloggedon = nullptr;
Steam_BGetCallback_fn g_bgetcallback = nullptr;
Steam_FreeLastCallback_fn g_freelastcallback = nullptr;

int g_pipe = 0;
int g_user = 0;
char g_log_path[MAX_PATH] = {0};
char g_game_exe[260] = {0};

void* g_cs_engine = nullptr;
int g_cs_hUser = 0;
int g_cs_hPipe = 0;
unsigned int g_cs_appId = 0;

constexpr int kVtEngine_GetIClientRemoteStorage = 0xC0;
constexpr int kVtRS_GetSyncState        = 0x240;
constexpr int kVtRS_BeginAppSync        = 0x270;
constexpr int kVtRS_IsAppSyncInProgress = 0x278;

bool cs_is_exec_ptr(void* p) {
    if (!p) return false;
    MEMORY_BASIC_INFORMATION mbi;
    if (VirtualQuery(p, &mbi, sizeof(mbi)) == 0) return false;
    if (mbi.State != MEM_COMMIT) return false;
    DWORD x = mbi.Protect & 0xFF;
    return x == PAGE_EXECUTE || x == PAGE_EXECUTE_READ ||
           x == PAGE_EXECUTE_READWRITE || x == PAGE_EXECUTE_WRITECOPY;
}

int kill_processes_by_name(const char* exeName) {
    if (!exeName || !exeName[0]) return 0;
    HANDLE snap = ::CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    int killed = 0;
    if (::Process32First(snap, &pe)) {
        do {
            if (wn_game_image_matches(pe.szExeFile, exeName)) {
                HANDLE h = ::OpenProcess(PROCESS_TERMINATE, FALSE, pe.th32ProcessID);
                if (h) {
                    if (::TerminateProcess(h, 0)) killed++;
                    ::CloseHandle(h);
                }
            }
        } while (::Process32Next(snap, &pe));
    }
    ::CloseHandle(snap);
    return killed;
}

int count_processes_by_name(const char* exeName) {
    if (!exeName || !exeName[0]) return 0;
    HANDLE snap = ::CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    int n = 0;
    if (::Process32First(snap, &pe)) {
        do {
            if (wn_game_image_matches(pe.szExeFile, exeName)) n++;
        } while (::Process32Next(snap, &pe));
    }
    ::CloseHandle(snap);
    return n;
}

std::vector<DWORD> g_close_pids;

BOOL CALLBACK close_enum_proc(HWND hwnd, LPARAM lp) {
    DWORD pid = 0;
    ::GetWindowThreadProcessId(hwnd, &pid);
    for (DWORD p : g_close_pids) {
        if (p == pid) {
            ::PostMessageA(hwnd, WM_CLOSE, 0, 0);
            break;
        }
    }
    return TRUE;
}

int graceful_close_game(const char* exeName) {
    g_close_pids.clear();
    HANDLE snap = ::CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32 pe;
        pe.dwSize = sizeof(pe);
        if (::Process32First(snap, &pe)) {
            do {
                if (wn_game_image_matches(pe.szExeFile, exeName)) {
                    g_close_pids.push_back(pe.th32ProcessID);
                }
            } while (::Process32Next(snap, &pe));
        }
        ::CloseHandle(snap);
    }
    if (!g_close_pids.empty()) {
        ::EnumWindows(close_enum_proc, 0);
    }
    return (int) g_close_pids.size();
}

void (*g_log_fn)(const char* line) = nullptr;

std::atomic<bool> g_armed{false};
std::atomic<bool> g_done{false};
std::atomic<bool> g_teardown_complete{false};
std::atomic<bool> g_watch_run{false};

constexpr const char* kSentinelPath = "C:\\wn-launcher.shutdown";

void wn_log(const char* msg) {
    char line[512];
    std::snprintf(line, sizeof(line), "[wn-launcher] %s", msg);

    if (g_log_fn) {
        g_log_fn(line);
        return;
    }
    if (g_log_path[0] == '\0') return;
    FILE* f = std::fopen(g_log_path, "a");
    if (!f) return;
    std::fprintf(f, "%s\n", line);
    std::fclose(f);
}

void teardown(const char* reason) {
    bool expected = false;
    if (!g_done.compare_exchange_strong(expected, true)) return;

    char buf[256];
    std::snprintf(buf, sizeof(buf),
                  "clean-shutdown teardown begin (reason=%s pipe=%d user=%d)",
                  reason ? reason : "?", g_pipe, g_user);
    wn_log(buf);

    if (g_game_exe[0] && g_pipe != 0) {
        int targeted = graceful_close_game(g_game_exe);
        if (targeted == 0) {
            wn_log("game already exited — skipping graceful-close wait");
        } else {
            std::snprintf(buf, sizeof(buf),
                          "graceful close \"%s\" (WM_CLOSE to %d game process(es)); "
                          "waiting for clean SteamAPI_Shutdown", g_game_exe, targeted);
            wn_log(buf);

            const int kMaxWaitMs = 3000;
            int waited = 0;
            while (waited < kMaxWaitMs && count_processes_by_name(g_game_exe) > 0) {
                if (g_bgetcallback && g_freelastcallback) {
                    char cb[64];
                    while (g_bgetcallback(g_pipe, cb)) g_freelastcallback(g_pipe);
                }
                ::Sleep(100);
                waited += 100;
            }
            bool gone = count_processes_by_name(g_game_exe) == 0;
            if (gone) {
                std::snprintf(buf, sizeof(buf),
                              "game \"%s\" exited gracefully after %dms — steamclient "
                              "should have emitted games-played([])", g_game_exe, waited);
                wn_log(buf);
            } else {
                int killed = kill_processes_by_name(g_game_exe);
                std::snprintf(buf, sizeof(buf),
                              "game \"%s\" ignored WM_CLOSE for %dms — hard-killed %d "
                              "(games-played reap may be delayed)",
                              g_game_exe, waited, killed);
                wn_log(buf);
            }
            if (g_bgetcallback && g_freelastcallback) {
                char cb[64];
                for (int i = 0; i < 4; ++i) {
                    while (g_bgetcallback(g_pipe, cb)) g_freelastcallback(g_pipe);
                    ::Sleep(100);
                }
            } else {
                ::Sleep(400);
            }
            wn_log("games-played reap window elapsed");
        }
    }

    if (g_cs_engine && g_cs_appId != 0) {
        wn_launcher_cloud_sync(g_cs_engine, g_cs_hUser, g_cs_hPipe, g_cs_appId, 2, 4, 15000);
    }

    if (g_logoff && g_user != 0 && g_pipe != 0) {
        g_logoff(g_pipe, g_user);
        wn_log("Steam_LogOff sent");

        const int kMinMs = 300, kMaxMs = 700, kStepMs = 100;
        int waited = 0;
        bool loggedOff = false;
        while (waited < kMaxMs) {
            ::Sleep(kStepMs);
            waited += kStepMs;
            if (g_bloggedon && !g_bloggedon(g_pipe, g_user)) {
                loggedOff = true;
                if (waited >= kMinMs) break;
            }
        }
        std::snprintf(buf, sizeof(buf),
                      "logoff flush wait done (%dms, BLoggedOn=%s)",
                      waited, loggedOff ? "false(logged-off)" : "true/unknown");
        wn_log(buf);
    }
    if (g_release_user && g_user != 0 && g_pipe != 0) {
        g_release_user(g_pipe, g_user);
        wn_log("Steam_ReleaseUser done");
    }
    if (g_release_pipe && g_pipe != 0) {
        bool ok = g_release_pipe(g_pipe);
        std::snprintf(buf, sizeof(buf), "Steam_BReleaseSteamPipe -> %d", ok ? 1 : 0);
        wn_log(buf);
    }

    wn_log("clean logoff complete");

    ::DeleteFileA(kSentinelPath);

    g_teardown_complete.store(true);
}

BOOL WINAPI ctrl_handler(DWORD type) {
    switch (type) {
        case CTRL_CLOSE_EVENT:
        case CTRL_LOGOFF_EVENT:
        case CTRL_SHUTDOWN_EVENT:
        case CTRL_C_EVENT:
        case CTRL_BREAK_EVENT:
            teardown("console-ctrl");
            return TRUE;
        default:
            return FALSE;
    }
}

void watch_loop() {
    while (g_watch_run.load()) {
        if (::GetFileAttributesA(kSentinelPath) != INVALID_FILE_ATTRIBUTES) {
            teardown("sentinel");
            g_watch_run.store(false);
            ::ExitProcess(0);
            return;
        }
        ::Sleep(150);
    }
}

}

extern "C" void wn_launcher_set_log_sink(void (*log_fn)(const char* line)) {
    g_log_fn = log_fn;
}

extern "C" void wn_launcher_set_game_exe(const char* exeName) {
    if (exeName && exeName[0]) {
        std::snprintf(g_game_exe, sizeof(g_game_exe), "%s", exeName);
    } else {
        g_game_exe[0] = '\0';
    }
}

extern "C" void wn_launcher_arm_clean_shutdown(void* hSteamClient, int pipe,
                                               int user, const char* logPath) {
    bool expected = false;
    if (!g_armed.compare_exchange_strong(expected, true)) return;

    g_pipe = pipe;
    g_user = user;
    if (logPath && logPath[0]) {
        std::snprintf(g_log_path, sizeof(g_log_path), "%s", logPath);
    }

    HMODULE h = reinterpret_cast<HMODULE>(hSteamClient);
    if (h) {
        g_logoff = reinterpret_cast<Steam_LogOff_fn>(
            ::GetProcAddress(h, "Steam_LogOff"));
        g_release_user = reinterpret_cast<Steam_ReleaseUser_fn>(
            ::GetProcAddress(h, "Steam_ReleaseUser"));
        g_release_pipe = reinterpret_cast<Steam_BReleaseSteamPipe_fn>(
            ::GetProcAddress(h, "Steam_BReleaseSteamPipe"));
        g_bloggedon = reinterpret_cast<Steam_BLoggedOn_fn>(
            ::GetProcAddress(h, "Steam_BLoggedOn"));
        g_bgetcallback = reinterpret_cast<Steam_BGetCallback_fn>(
            ::GetProcAddress(h, "Steam_BGetCallback"));
        g_freelastcallback = reinterpret_cast<Steam_FreeLastCallback_fn>(
            ::GetProcAddress(h, "Steam_FreeLastCallback"));
    }

    char buf[256];
    std::snprintf(buf, sizeof(buf),
                  "clean-shutdown armed (pipe=%d user=%d logoff=%p releaseUser=%p "
                  "releasePipe=%p bLoggedOn=%p sentinel=%s)",
                  pipe, user, reinterpret_cast<void*>(g_logoff),
                  reinterpret_cast<void*>(g_release_user),
                  reinterpret_cast<void*>(g_release_pipe),
                  reinterpret_cast<void*>(g_bloggedon), kSentinelPath);
    wn_log(buf);

    ::SetConsoleCtrlHandler(ctrl_handler, TRUE);

    ::DeleteFileA(kSentinelPath);

    g_watch_run.store(true);
    std::thread(watch_loop).detach();
}

extern "C" void wn_launcher_set_cloud_context(void* engine, int hUser, int hPipe,
                                              unsigned int appId) {
    g_cs_engine = engine;
    g_cs_hUser = hUser;
    g_cs_hPipe = hPipe;
    g_cs_appId = appId;
}

extern "C" int wn_launcher_cloud_sync(void* engine, int hUser, int hPipe,
                                      unsigned int appId, int cmd, int flags, int timeoutMs) {
    if (!engine || appId == 0) return -1;
    void** engine_vt = *reinterpret_cast<void***>(engine);
    void* getRsP = engine_vt[kVtEngine_GetIClientRemoteStorage / 8];
    if (!cs_is_exec_ptr(getRsP)) {
        wn_log("[wn-launcher] cloud: GetIClientRemoteStorage slot not executable — skipping sync");
        return -1;
    }
    using GetRsFn = void* (*)(void*, int, int);
    void* rs = reinterpret_cast<GetRsFn>(getRsP)(engine, hUser, hPipe);
    if (!rs) {
        wn_log("[wn-launcher] cloud: IClientRemoteStorage null — skipping sync");
        return -1;
    }
    void** rs_vt = *reinterpret_cast<void***>(rs);
    void* beginP  = rs_vt[kVtRS_BeginAppSync / 8];
    void* inProgP = rs_vt[kVtRS_IsAppSyncInProgress / 8];
    void* stateP  = rs_vt[kVtRS_GetSyncState / 8];
    if (!cs_is_exec_ptr(beginP) || !cs_is_exec_ptr(inProgP) || !cs_is_exec_ptr(stateP)) {
        wn_log("[wn-launcher] cloud: RemoteStorage slot(s) not executable — skipping sync");
        return -1;
    }
    using BeginFn  = bool (*)(void*, unsigned int, int, int);
    using InProgFn = bool (*)(void*, unsigned int);
    using StateFn  = int  (*)(void*, unsigned int);

    char buf[176];
    int finalState = -1;
    for (int attempt = 1; attempt <= 3; ++attempt) {
        bool started = reinterpret_cast<BeginFn>(beginP)(rs, appId, cmd, flags);
        std::snprintf(buf, sizeof(buf),
            "[wn-launcher] cloud: BeginAppSync(app=%u cmd=%d flags=%d) attempt %d -> %d",
            appId, cmd, flags, attempt, started ? 1 : 0);
        wn_log(buf);
        int waited = 0;
        while (reinterpret_cast<InProgFn>(inProgP)(rs, appId) && waited < timeoutMs) {
            if (g_bgetcallback && g_freelastcallback) {
                char cb[64];
                while (g_bgetcallback(g_pipe, cb)) g_freelastcallback(g_pipe);
            }
            ::Sleep(10);
            waited += 10;
        }
        finalState = reinterpret_cast<StateFn>(stateP)(rs, appId);
        std::snprintf(buf, sizeof(buf),
            "[wn-launcher] cloud: sync settled (state=%d after %dms)", finalState, waited);
        wn_log(buf);
        if (finalState == 1 || finalState == 0 || finalState == 6) break;
    }
    if (finalState == 6) {
        wn_log("[wn-launcher] cloud: CONFLICT (state 6) — not auto-resolving; leaving saves intact");
    }
    return finalState;
}

extern "C" void wn_launcher_clean_shutdown_now(const char* reason) {
    teardown(reason ? reason : "explicit");
}

extern "C" void wn_launcher_wait_clean_shutdown(int maxMs) {
    int waited = 0;
    while (g_done.load() && !g_teardown_complete.load() && waited < maxMs) {
        ::Sleep(50);
        waited += 50;
    }
}
