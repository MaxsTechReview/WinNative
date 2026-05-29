#include "clean_shutdown.h"

#include <windows.h>
#include <tlhelp32.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

// The steamclient flat exports we drive on teardown — same signatures the
// Android-side bootstrap resolves in steam_bootstrap.cpp. The ABI is the Valve
// flat C entry-point ABI, identical across the Win64 steamclient64.dll and the
// Android libsteamclient.so.
namespace {

using Steam_LogOff_fn = void (*)(int /*pipe*/, int /*user*/);
using Steam_ReleaseUser_fn = void (*)(int /*pipe*/, int /*user*/);
using Steam_BReleaseSteamPipe_fn = bool (*)(int /*pipe*/);
using Steam_BLoggedOn_fn = bool (*)(int /*pipe*/, int /*user*/);
using Steam_BGetCallback_fn = bool (*)(int /*pipe*/, void* /*cb*/);
using Steam_FreeLastCallback_fn = void (*)(int /*pipe*/);

Steam_LogOff_fn g_logoff = nullptr;
Steam_ReleaseUser_fn g_release_user = nullptr;
Steam_BReleaseSteamPipe_fn g_release_pipe = nullptr;
Steam_BLoggedOn_fn g_bloggedon = nullptr;
Steam_BGetCallback_fn g_bgetcallback = nullptr;
Steam_FreeLastCallback_fn g_freelastcallback = nullptr;

int g_pipe = 0;
int g_user = 0;
char g_log_path[MAX_PATH] = {0};

// The game executable name (e.g. "Balls.exe") the launcher started for this
// session. On teardown we terminate it BEFORE logging off so steamclient sees
// its launched app exit and emits the games-played([]) that reaps the
// server-side "playing" registration — see the note in teardown().
char g_game_exe[260] = {0};

// Terminate every running process whose image name matches exeName. Returns the
// count terminated. Used so a steamclient that launched the game via
// IClientAppManager::LaunchApp observes the exit and clears games-played.
int kill_processes_by_name(const char* exeName) {
    if (!exeName || !exeName[0]) return 0;
    HANDLE snap = ::CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    int killed = 0;
    if (::Process32First(snap, &pe)) {
        do {
            if (_stricmp(pe.szExeFile, exeName) == 0) {
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

// Count running processes whose image name matches exeName.
int count_processes_by_name(const char* exeName) {
    if (!exeName || !exeName[0]) return 0;
    HANDLE snap = ::CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    int n = 0;
    if (::Process32First(snap, &pe)) {
        do {
            if (_stricmp(pe.szExeFile, exeName) == 0) n++;
        } while (::Process32Next(snap, &pe));
    }
    ::CloseHandle(snap);
    return n;
}

// PIDs of the game process(es), populated just before EnumWindows so the
// enum callback (a plain WNDENUMPROC, no captures) can match windows to them.
std::vector<DWORD> g_close_pids;

BOOL CALLBACK close_enum_proc(HWND hwnd, LPARAM lp) {
    DWORD pid = 0;
    ::GetWindowThreadProcessId(hwnd, &pid);
    for (DWORD p : g_close_pids) {
        if (p == pid) {
            // WM_CLOSE asks the app to quit gracefully (its window proc / engine
            // runs its normal shutdown, including SteamAPI_Shutdown) instead of
            // being SIGKILLed — which is what makes steamclient emit the
            // games-played([]) reap.
            ::PostMessageA(hwnd, WM_CLOSE, 0, 0);
            break;
        }
    }
    return TRUE;
}

// Ask the game to close gracefully by posting WM_CLOSE to all its top-level
// windows. Returns the number of game processes targeted.
int graceful_close_game(const char* exeName) {
    g_close_pids.clear();
    HANDLE snap = ::CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32 pe;
        pe.dwSize = sizeof(pe);
        if (::Process32First(snap, &pe)) {
            do {
                if (_stricmp(pe.szExeFile, exeName) == 0) {
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

// When set by the host launcher, all markers go through this sink (the
// launcher's single open log handle) instead of our own fopen — see the note in
// clean_shutdown.h. Receives a fully-formed "[wn-launcher] ..." line (no
// trailing newline; the sink adds it).
void (*g_log_fn)(const char* line) = nullptr;

std::atomic<bool> g_armed{false};
std::atomic<bool> g_done{false};
std::atomic<bool> g_teardown_complete{false};
std::atomic<bool> g_watch_run{false};

// Windows path the Android close path writes; mirrors C:\wn-launcher.log so it
// lands in the same Wine prefix drive_c the app reads back.
constexpr const char* kSentinelPath = "C:\\wn-launcher.shutdown";

void wn_log(const char* msg) {
    char line[512];
    std::snprintf(line, sizeof(line), "[wn-launcher] %s", msg);
    // Preferred: route through the launcher's own logger so the marker lands in
    // the same file at a consistent write position (a separate handle here gets
    // clobbered by the launcher's next write).
    if (g_log_fn) {
        g_log_fn(line);
        return;
    }
    // Standalone fallback: our own append handle.
    if (g_log_path[0] == '\0') return;
    FILE* f = std::fopen(g_log_path, "a");
    if (!f) return;
    std::fprintf(f, "%s\n", line);
    std::fclose(f);
}

void teardown(const char* reason) {
    // Run exactly once even if the ctrl handler and the watcher thread race.
    bool expected = false;
    if (!g_done.compare_exchange_strong(expected, true)) return;

    char buf[256];
    std::snprintf(buf, sizeof(buf),
                  "clean-shutdown teardown begin (reason=%s pipe=%d user=%d)",
                  reason ? reason : "?", g_pipe, g_user);
    wn_log(buf);

    // STEP 0 — close the game before logging off so this session ends cleanly and
    // the account can go fully offline (the offline window is what lets Steam reap
    // the games-played registration; see WN_PLANW_REAP_OFFLINE_MS on the Android
    // side). On a natural game-exit the game is already gone, so this is skipped.
    if (g_game_exe[0] && g_pipe != 0) {
        // WM_CLOSE first so the game runs SteamAPI_Shutdown (emits games-played([]));
        // hard-kill only as a fallback if it ignores WM_CLOSE.
        int targeted = graceful_close_game(g_game_exe);
        if (targeted == 0) {
            // Natural game-exit: game already gone (ran its own SteamAPI_Shutdown),
            // so skip the wait + settle and go straight to logoff — keeps it snappy.
            wn_log("game already exited — skipping graceful-close wait");
        } else {
            std::snprintf(buf, sizeof(buf),
                          "graceful close \"%s\" (WM_CLOSE to %d game process(es)); "
                          "waiting for clean SteamAPI_Shutdown", g_game_exe, targeted);
            wn_log(buf);

            // Force-close path only (game still running). The game exits on
            // WM_CLOSE in ~2.8s, so 3s covers it before the hard-kill fallback.
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
            // Brief settle to drain pending callbacks before logoff (the actual
            // reap is time-based, handled by the Android offline window).
            if (g_bgetcallback && g_freelastcallback) {
                char cb[64];
                for (int i = 0; i < 4; ++i) {  // ~0.4s
                    while (g_bgetcallback(g_pipe, cb)) g_freelastcallback(g_pipe);
                    ::Sleep(100);
                }
            } else {
                ::Sleep(400);
            }
            wn_log("games-played reap window elapsed");
        }
    }

    // Reverse order of init, mirroring WnSteamBootstrap.nativeShutdown: log off
    // the user (this is the CMsgClientLogOff that reaps the server session),
    // release the user, then drop the pipe.
    if (g_logoff && g_user != 0 && g_pipe != 0) {
        g_logoff(g_pipe, g_user);
        wn_log("Steam_LogOff sent");

        // Flush before releasing the pipe: Steam_LogOff only QUEUES the
        // CMsgClientLogOff onto steamclient's CM thread, and releasing the pipe
        // tears the socket down — release too early and the logoff never goes out.
        // Poll Steam_BLoggedOn as the flush signal; a short wait is enough (the
        // reap itself is time-based, handled by the Android offline window).
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

    // Remove the sentinel so a stale file can't immediately re-trigger.
    ::DeleteFileA(kSentinelPath);

    // Signal any thread blocked in wn_launcher_wait_clean_shutdown() that the
    // reap + logoff fully completed. main() waits on this after its game-watch
    // loop so it does not return (and exit the process) while the sentinel
    // watcher thread is still mid-teardown.
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
            // End the process so the Android side sees steam.exe disappear and
            // can stop waiting on the handshake.
            ::ExitProcess(0);
            return;
        }
        ::Sleep(150);
    }
}

}  // namespace

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
    if (!g_armed.compare_exchange_strong(expected, true)) return;  // arm once

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

    // Clear any stale sentinel left by a previous session before we watch.
    ::DeleteFileA(kSentinelPath);

    g_watch_run.store(true);
    std::thread(watch_loop).detach();
}

extern "C" void wn_launcher_clean_shutdown_now(const char* reason) {
    teardown(reason ? reason : "explicit");
}

extern "C" void wn_launcher_wait_clean_shutdown(int maxMs) {
    // Only block if a teardown is actually in flight (g_done set) but not yet
    // finished. If teardown never started, return immediately.
    int waited = 0;
    while (g_done.load() && !g_teardown_complete.load() && waited < maxMs) {
        ::Sleep(50);
        waited += 50;
    }
}
