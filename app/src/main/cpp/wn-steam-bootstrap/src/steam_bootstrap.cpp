// wn-steam-bootstrap — JNI bridge that loads Valve's native
// libsteamclient.so in our Android process so Wine's lsteamclient.dll
// (running inside the Proton prefix) has a peer to talk to over the
// Steam3Master / SteamClientService TCP listeners that libsteamclient.so
// stands up internally when it sees those env vars.
//
// JNI surface (mirrors the Kotlin object WnSteamBootstrap.kt):
//
//   nativeInit(context, libPath, home, steam3Master, steamClientService,
//              extraEnv[], accountName, refreshToken, steamId64) → int
//     0           on success
//     -1          binary missing at libPath
//     -2          dlopen failed (see logcat for dlerror)
//     -3          CreateInterface failed (libsteamclient.so present but
//                 didn't expose the expected SteamClient020 interface)
//     -4          steam pipe / global user setup failed
//
//   nativeShutdown()                  — tear down the pipe, ReleaseUser
//   nativePrepareApp(parent, dlcs[])  — kick GetAppOwnershipTicket warmups
//   nativeSetCloudEnabled(app, on)    — toggle per-app cloud sync
//
// Implementation notes:
//   • setenv() runs BEFORE dlopen() — libsteamclient.so reads the
//     IPC endpoint env vars (Steam3Master / SteamClientService) at module
//     init and binds the listening sockets there. Setting them later is a
//     no-op.
//   • RTLD_GLOBAL on the dlopen so the loaded .so's exports are visible
//     to anything else that might later dlopen it (Wine's loader paths
//     occasionally do).
//   • All steam-side calls are guarded by g_sc != nullptr — if the
//     libsteamclient.so binary isn't on disk yet (we don't bundle it),
//     init returns -1 cleanly and Prepare/Shutdown are no-ops. This lets
//     the rest of the launcher proceed (game will run without
//     online-play, DLC checks fail soft) instead of crashing.
//   • SteamWorks vtable layouts are taken from the public SDK 1.59
//     headers (steam_iface.h in this module). Drift between SDK versions
//     would crash on virtual call — pin via interface version strings.

#include <jni.h>

#include <android/log.h>

#include <dlfcn.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include "steam_iface.h"

namespace {

constexpr const char* kLogTag = "WnSteamBoot";
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

// State held for the lifetime of the loaded libsteamclient.so.
//
// Verified by decompiling the reference embedded-Steam bootstrap with
// Ghidra (libsteambootstrap.so nativeInit at 0x14a8). The correct dance:
//
//   1.  dlopen(libsteamclient.so, RTLD_NOW). Don't bother with explicit
//       SteamService_StartThread — libsteamclient.so handles steamservice
//       lifecycle internally (it dlopens steamservice.so itself when it
//       needs it; we just preload it so the loader namespace knows it).
//   2.  Steam_CreateGlobalUser(&pipe_out) — flat C function. Creates the
//       Steam pipe AND connects a global user in one call. Returns the
//       user handle and fills the pipe out-param. This is the correct
//       entry point on Android — Steam_CreateSteamPipe() returns 0
//       because it expects the legacy fork/exec helper-child model.
//   3.  CreateInterface("CLIENTENGINE_INTERFACE_VERSION005", &err) → engine
//       This is the internal Valve IClientEngine. Public SteamClient020
//       interface is unsuitable for the refresh-token login path.
//   4.  engine->vtable[8](user, pipe) → IClientUser sub-interface
//   5.  Login dance on IClientUser vtable:
//          slot 49 (offset 0x188): bool IsAccountLoggedIn(account)
//          slot 54 (offset 0x1B0): SetLoginInformation(account, password, remember)
//          slot 56 (offset 0x1C0): LogonWithRefreshToken(refreshToken, account)
//          slot 50 (offset 0x190): SetAccount(account, 1)        [already-logged path]
//          slot 1  (offset 0x08): SetSteamID(steamId64)
//   6.  Poll loop, up to 100×100ms=10s:
//          drain pending callbacks via Steam_BGetCallback/FreeLastCallback
//          if Steam_BLoggedOn(pipe, user) — DONE
//
// Method-name guesses above are slot-by-slot reasoning from the call
// arity and the SteamWorks SDK; exact names may differ but the slot
// numbers match what the reference bootstrap dispatches to.
struct State {
    std::mutex   mu;
    void*        lsc_handle  = nullptr;   // libsteamclient.so dlopen handle
    bool         initialized = false;

    // libsteamclient.so flat C entry points (resolved via dlsym).
    wnsteambs::CreateInterfaceFn fn_CreateInterface = nullptr;
    int   (*fn_Steam_CreateGlobalUser)(int* pipe_inout)= nullptr;
    bool  (*fn_Steam_BLoggedOn)(int pipe, int user)    = nullptr;
    void  (*fn_Steam_LogOff)(int pipe, int user)       = nullptr;
    bool  (*fn_Steam_BGetCallback)(int pipe, void* cb) = nullptr;
    void  (*fn_Steam_FreeLastCallback)(int pipe)       = nullptr;
    void  (*fn_Breakpad_SteamSetAppID)(unsigned app_id)= nullptr;

    // Live pipe + global user handles.
    int pipe = 0;
    int user = 0;

    // Cached IClientUser pointer (returned from IClientEngine vtable[8]).
    // We hold it across the session so prepareApp / setCloudEnabled
    // can route through the same sub-interface if needed. Owned by
    // libsteamclient.so — do NOT free.
    void* iclient_user = nullptr;

    // Every env key nativeInit setenv()'d, recorded so nativeShutdown
    // can unsetenv() them. Otherwise a bionic→real-Steam mode switch in
    // the same process inherits leaked WINESTEAMCLIENTPATH / Steam3Master /
    // SteamUser etc. into the real-Steam wine subprocess.
    std::vector<std::string> applied_env_keys;

    // Persistent callback pump. libsteamclient.so's logon — and every
    // later Steam API round-trip the game makes — is message-driven: it
    // only advances while something drains Steam_BGetCallback. start()'s
    // 10s poll does that during init, but the session must keep being
    // pumped for the whole process lifetime, so a detached thread takes
    // over once start()'s poll loop finishes.
    std::atomic<bool> pump_running{false};
};
State g_state;

// IClientEngine vtable slots we call (offsets in bytes; aarch64 ABI has
// 8-byte fn ptrs). All confirmed by Ghidra decomp of reference bootstrap.
constexpr int kVtClientEngine_GetIClientUser = 0x40;   // returns sub-iface

// IClientUser vtable slots — sizes match aarch64 ABI 8-byte slots.
constexpr int kVtClientUser_SetSteamID        = 0x08;
constexpr int kVtClientUser_IsAccountLoggedIn = 0x188; // bool IsAccountLoggedIn(const char*)
constexpr int kVtClientUser_SetAccount        = 0x190; // (already-logged path)
constexpr int kVtClientUser_SetLoginInformation = 0x1B0;
constexpr int kVtClientUser_LogonWithRefresh  = 0x1C0;

// Persistent callback-pump loop. Runs on a detached thread for the
// lifetime of the process: libsteamclient.so only advances its logon
// state machine — and processes every later Steam API response — while
// Steam_BGetCallback is being drained. start()'s init poll does this for
// the first 10s; this thread takes over so the session does not stall
// the moment start() returns (the bug that left Bionic games offline).
void callback_pump_loop() {
    char cb_buf[64] = {0};   // CallbackMsg_t header; 64 is safe headroom
    bool announced_logon = false;
    int  ticks           = 0;
    int  cb_logged       = 0;
    while (g_state.pump_running.load(std::memory_order_relaxed)) {
        if (g_state.fn_Steam_BGetCallback && g_state.fn_Steam_FreeLastCallback) {
            while (g_state.fn_Steam_BGetCallback(g_state.pipe, cb_buf)) {
                if (cb_logged < 120) {
                    LOGI("pump callback id=%d", *reinterpret_cast<int*>(cb_buf + 4));
                    ++cb_logged;
                }
                g_state.fn_Steam_FreeLastCallback(g_state.pipe);
            }
        }
        // Announce the logon transition so the launch log shows whether
        // the session ever authenticates after start() returns.
        if (!announced_logon && g_state.fn_Steam_BLoggedOn &&
            g_state.fn_Steam_BLoggedOn(g_state.pipe, g_state.user)) {
            announced_logon = true;
            LOGI("callback pump: session is now LOGGED ON (after %d ticks)",
                 ticks);
        }
        ++ticks;
        ::usleep(20 * 1000);   // ~50Hz, matching the Steam client's tick
    }
}

// Helper: pull a UTF-8 String from a jstring without leaking on the
// throw-path. Returns empty string for null.
std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (!c) return {};
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

// Apply a Java String[] of "KEY=value" pairs (or alternating key/value
// slots, depending on the Kotlin convention we settled on) via setenv().
// We accept both shapes: if a slot contains '=' we split on the first one,
// otherwise we pair slot i (key) with slot i+1 (value).
void apply_extra_env(JNIEnv* env, jobjectArray array) {
    if (!array) return;
    jsize n = env->GetArrayLength(array);
    for (jsize i = 0; i < n; ++i) {
        jstring slot = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (!slot) continue;
        std::string s = jstr(env, slot);
        env->DeleteLocalRef(slot);
        if (s.empty()) continue;
        auto eq = s.find('=');
        if (eq != std::string::npos && eq > 0) {
            std::string k = s.substr(0, eq);
            std::string v = s.substr(eq + 1);
            ::setenv(k.c_str(), v.c_str(), /*overwrite*/ 1);
            g_state.applied_env_keys.push_back(k);
            LOGI("setenv %s=%s", k.c_str(), v.c_str());
        }
    }
}

// Try to dlopen the staged libsteamclient.so at libPath. Returns null on
// failure — caller logs and bails. RTLD_GLOBAL so subsequent dlopens
// (Wine's loader poking around our process) can see its exports.
void* try_dlopen(const std::string& libPath) {
    if (::access(libPath.c_str(), R_OK) != 0) {
        LOGW("libsteamclient.so not present at %s; skipping native load. "
             "Online-play / overlay features will be unavailable; basic "
             "launch paths still work via our PICS / ticket cache.",
             libPath.c_str());
        return nullptr;
    }
    // RTLD_NOW resolves all symbols up front so we crash here (with a
    // diagnostic) instead of crashing later on a missing virtual.
    void* h = ::dlopen(libPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        LOGE("dlopen(%s) failed: %s", libPath.c_str(), ::dlerror());
    }
    return h;
}

// libsteamclient.so does NOT statically NEED its siblings — readelf shows
// only libandroid/liblog/libm/libdl/libc in its NEEDED list. It dlopens
// steamservice.so, libsteamnetworkingsockets.so, libtier0_s.so and
// libvstdlib_s.so internally. On Android the app's restricted linker
// namespace won't find them under /data/.../imagefs/usr/lib/ unless we
// either (a) set LD_LIBRARY_PATH (honored by Bionic's loader inside the
// app namespace) or (b) preload them with RTLD_GLOBAL so they're already
// in the global symbol table when libsteamclient.so calls dlopen.
//
// Belt + suspenders: do both. Sibling preload order matters — tier0 first
// (base layer), then vstdlib (depends on tier0), then networking sockets +
// steamservice (depend on the lower two). Each failure is logged but
// non-fatal: libsteamclient.so might still init in a degraded mode.
void preload_steam_runtime_siblings(const std::string& lib_dir) {
    constexpr const char* kSiblings[] = {
        "libtier0_s.so",
        "libvstdlib_s.so",
        "libsteamnetworkingsockets.so",
        "steamservice.so",
    };
    for (const char* name : kSiblings) {
        std::string path = lib_dir + "/" + name;
        if (::access(path.c_str(), R_OK) != 0) {
            LOGW("preload skip: %s not present", path.c_str());
            continue;
        }
        void* h = ::dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (h) {
            LOGI("preload OK:  %s", path.c_str());
        } else {
            LOGW("preload FAIL: %s — %s", path.c_str(), ::dlerror());
        }
    }
}

// Return the directory portion of a path (everything before the last '/').
// Falls back to "." when no slash is found.
std::string dirname_of(const std::string& path) {
    auto slash = path.rfind('/');
    if (slash == std::string::npos) return ".";
    return path.substr(0, slash);
}

// Create a directory + every missing parent. mkdir(2) doesn't recurse; we
// walk the path char-by-char and mkdir each component. Existing dirs are
// not an error. Failures other than EEXIST are logged.
void mkdir_p(const std::string& path, mode_t mode) {
    std::string acc;
    acc.reserve(path.size());
    for (size_t i = 0; i <= path.size(); ++i) {
        if (i == path.size() || path[i] == '/') {
            if (acc.empty()) { if (i < path.size()) acc.push_back(path[i]); continue; }
            if (::mkdir(acc.c_str(), mode) != 0 && errno != EEXIST) {
                LOGW("mkdir(%s) failed: %s", acc.c_str(), std::strerror(errno));
            }
        }
        if (i < path.size()) acc.push_back(path[i]);
    }
}

// Stage the Steam config dir + empty config/local VDFs. libsteamclient.so's
// CreateSteamPipe path stats <HOME>/Steam/config/{config,local}.vdf at
// init; missing files cause it to bail without an obvious error. Other
// embedded-Steam bootstraps symlink session files in from a persistence
// dir — we just create empty stubs so the stat succeeds. Whatever ends up
// in them is governed by libsteamclient.so itself once it starts writing.
void stage_steam_config_dir(const std::string& home) {
    if (home.empty()) return;
    const std::string steam_dir  = home + "/Steam";
    const std::string config_dir = steam_dir + "/config";
    mkdir_p(steam_dir,  0755);
    mkdir_p(config_dir, 0755);
    for (const char* name : {"config.vdf", "local.vdf"}) {
        std::string p = config_dir + "/" + name;
        struct stat st{};
        if (::stat(p.c_str(), &st) == 0) continue;   // already there
        int fd = ::open(p.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, 0644);
        if (fd < 0) {
            LOGW("create %s failed: %s", p.c_str(), std::strerror(errno));
        } else {
            ::close(fd);
            LOGI("staged empty %s", p.c_str());
        }
    }
}

}  // namespace

// =============================================================================
// JNI entry points — names match Kotlin's @JvmStatic external fun convention
// =============================================================================
extern "C" {

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeInit(
        JNIEnv* env, jclass /*cls*/, jobject /*context*/,
        jstring jlibPath, jstring jhome,
        jstring jsteam3Master, jstring jsteamClientService,
        jobjectArray jextraEnv,
        jstring jaccountName, jstring jrefreshToken, jlong jsteamId64) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (g_state.initialized) {
        LOGI("nativeInit: already initialized (lsc=%p pipe=%d user=%d)",
             g_state.lsc_handle, g_state.pipe, g_state.user);
        return 0;
    }

    const std::string libPath  = jstr(env, jlibPath);
    const std::string home     = jstr(env, jhome);
    const std::string s3m      = jstr(env, jsteam3Master);
    const std::string scs      = jstr(env, jsteamClientService);
    const std::string user     = jstr(env, jaccountName);
    const std::string token    = jstr(env, jrefreshToken);
    const uint64_t    steamId  = static_cast<uint64_t>(jsteamId64);

    LOGI("nativeInit: libPath=%s home=%s steam3Master=%s "
         "steamClientService=%s user=%s tokenLen=%zu steamId=%llu",
         libPath.c_str(), home.c_str(), s3m.c_str(), scs.c_str(),
         user.c_str(), token.size(),
         static_cast<unsigned long long>(steamId));

    // ---- setenv pass BEFORE dlopen ----
    // applied_env_keys accumulates every key we setenv() in this init so
    // nativeShutdown can unsetenv() them on teardown. Without this, switching
    // from Bionic to Launch Steam Client mode within a single process leaks
    // WINESTEAMCLIENTPATH / Steam3Master / SteamUser etc. into the real-Steam
    // wine subprocess and confuses steam.exe.
    g_state.applied_env_keys.clear();
    apply_extra_env(env, jextraEnv);
    if (!home.empty()) {
        ::setenv("HOME", home.c_str(), 1);
        g_state.applied_env_keys.emplace_back("HOME");
    }
    if (!s3m.empty()) {
        ::setenv("Steam3Master", s3m.c_str(), 1);
        g_state.applied_env_keys.emplace_back("Steam3Master");
    }
    if (!scs.empty()) {
        ::setenv("SteamClientService", scs.c_str(), 1);
        g_state.applied_env_keys.emplace_back("SteamClientService");
    }

    // LD_LIBRARY_PATH — honored by Bionic for in-namespace dlopens. The
    // path is the parent of libPath (everything libsteamclient.so will
    // dlopen lives next to it).
    const std::string lib_dir = dirname_of(libPath);
    ::setenv("LD_LIBRARY_PATH", lib_dir.c_str(), 1);
    g_state.applied_env_keys.emplace_back("LD_LIBRARY_PATH");
    LOGI("setenv LD_LIBRARY_PATH=%s", lib_dir.c_str());

    // Preload runtime siblings with RTLD_GLOBAL so libsteamclient.so's
    // later internal dlopens find them in the global symbol namespace.
    preload_steam_runtime_siblings(lib_dir);

    // Stage <HOME>/Steam/config/{config,local}.vdf as empty files.
    // libsteamclient.so's CreateSteamPipe path stat's these and bails
    // without an obvious error when missing. Other embedded-Steam
    // bootstraps symlink real session-state files in; empty stubs are
    // enough for the stat to succeed on a fresh launch.
    stage_steam_config_dir(home);

    // -------------------------------------------------------------------
    // STEP 1 — dlopen libsteamclient.so + resolve flat C entry points.
    //
    // libsteamclient.so handles steamservice lifecycle internally; we
    // don't need to call SteamService_StartThread explicitly. The
    // preload of steamservice.so (RTLD_GLOBAL) above is enough — the
    // linker namespace has the symbols ready when libsteamclient.so
    // dlopens it.
    // -------------------------------------------------------------------
    void* lsc = try_dlopen(libPath);
    if (!lsc) return -1;
    LOGI("dlopen(libsteamclient.so) OK handle=%p", lsc);

    g_state.fn_CreateInterface = reinterpret_cast<wnsteambs::CreateInterfaceFn>(
        ::dlsym(lsc, "CreateInterface"));
    g_state.fn_Steam_CreateGlobalUser = reinterpret_cast<int(*)(int*)>(
        ::dlsym(lsc, "Steam_CreateGlobalUser"));
    g_state.fn_Steam_BLoggedOn = reinterpret_cast<bool(*)(int, int)>(
        ::dlsym(lsc, "Steam_BLoggedOn"));
    g_state.fn_Steam_LogOff = reinterpret_cast<void(*)(int, int)>(
        ::dlsym(lsc, "Steam_LogOff"));
    g_state.fn_Steam_BGetCallback = reinterpret_cast<bool(*)(int, void*)>(
        ::dlsym(lsc, "Steam_BGetCallback"));
    g_state.fn_Steam_FreeLastCallback = reinterpret_cast<void(*)(int)>(
        ::dlsym(lsc, "Steam_FreeLastCallback"));
    g_state.fn_Breakpad_SteamSetAppID = reinterpret_cast<void(*)(unsigned)>(
        ::dlsym(lsc, "Breakpad_SteamSetAppID"));

    if (!g_state.fn_CreateInterface || !g_state.fn_Steam_CreateGlobalUser) {
        LOGE("dlsym of required entry points failed: CreateInterface=%p "
             "Steam_CreateGlobalUser=%p",
             reinterpret_cast<void*>(g_state.fn_CreateInterface),
             reinterpret_cast<void*>(g_state.fn_Steam_CreateGlobalUser));
        return -3;
    }
    LOGI("dlsym OK: CreateInterface=%p Steam_CreateGlobalUser=%p "
         "Steam_BLoggedOn=%p Steam_BGetCallback=%p",
         reinterpret_cast<void*>(g_state.fn_CreateInterface),
         reinterpret_cast<void*>(g_state.fn_Steam_CreateGlobalUser),
         reinterpret_cast<void*>(g_state.fn_Steam_BLoggedOn),
         reinterpret_cast<void*>(g_state.fn_Steam_BGetCallback));

    if (g_state.fn_Breakpad_SteamSetAppID) {
        g_state.fn_Breakpad_SteamSetAppID(0);
    }

    // -------------------------------------------------------------------
    // STEP 2 — Steam_CreateGlobalUser. Creates the Steam pipe AND
    // connects the global user in one call. This is the correct entry
    // point on Android; Steam_CreateSteamPipe()'s legacy fork/exec
    // helper-child path returns 0 here.
    // -------------------------------------------------------------------
    int pipe_out = 0;
    int user_h   = g_state.fn_Steam_CreateGlobalUser(&pipe_out);
    if (user_h == 0 || pipe_out == 0) {
        LOGE("Steam_CreateGlobalUser failed: user=%d pipe_out=%d",
             user_h, pipe_out);
        return -4;
    }
    LOGI("Steam_CreateGlobalUser OK pipe=%d user=%d", pipe_out, user_h);

    g_state.lsc_handle  = lsc;
    g_state.pipe        = pipe_out;
    g_state.user        = user_h;
    g_state.initialized = true;

    // -------------------------------------------------------------------
    // STEP 3 — Refresh-token login via IClientEngine (optional).
    //
    // From Ghidra decomp of the reference bootstrap:
    //   CreateInterface("CLIENTENGINE_INTERFACE_VERSION005", &err)  → engine
    //   engine_vt[8] (offset 0x40): GetIClientUser(user, pipe)      → IClientUser
    //   IClientUser_vt:
    //     [49] offset 0x188:  bool IsAccountLoggedIn(const char*)
    //     [54] offset 0x1B0:  SetLoginInformation(account, password, remember)
    //     [56] offset 0x1C0:  LogonWithRefreshToken(refreshToken, account)
    //     [50] offset 0x190:  SetAccount(account, 1)        (cached path)
    //     [1]  offset 0x08:   SetSteamID(steamId64)
    //
    // If we don't have credentials, libsteamclient.so will sit at
    // "connected but not logged on" — Wine IPC still functions for the
    // non-authenticated SteamWorks calls.
    // -------------------------------------------------------------------
    if (!user.empty() && !token.empty() && steamId != 0) {
        int err = 0;
        void* engine = g_state.fn_CreateInterface(
            "CLIENTENGINE_INTERFACE_VERSION005", &err);
        if (engine && err == 0) {
            // vtable pointer is the first 8 bytes of the object.
            long* engine_vt = *reinterpret_cast<long**>(engine);

            // engine_vt[8] = GetIClientUser(user, pipe).
            using GetIClientUserFn = void* (*)(void*, int, int);
            auto get_iclient_user = reinterpret_cast<GetIClientUserFn>(
                engine_vt[kVtClientEngine_GetIClientUser / 8]);
            void* iuser = get_iclient_user(engine, user_h, pipe_out);
            LOGI("IClientEngine.GetIClientUser(user=%d, pipe=%d) -> %p",
                 user_h, pipe_out, iuser);

            if (iuser) {
                g_state.iclient_user = iuser;
                long* iuser_vt = *reinterpret_cast<long**>(iuser);

                using IsAccountLoggedInFn = bool (*)(void*, const char*);
                auto is_logged = reinterpret_cast<IsAccountLoggedInFn>(
                    iuser_vt[kVtClientUser_IsAccountLoggedIn / 8]);
                bool already = is_logged(iuser, user.c_str());
                LOGI("IClientUser.IsAccountLoggedIn(%s) = %d (note: this "
                     "reports whether the account is KNOWN, not actively "
                     "authenticated — we drive LogonWithRefreshToken either "
                     "way to ensure a fresh authenticated session)",
                     user.c_str(), already ? 1 : 0);

                // ALWAYS run SetLoginInformation + LogonWithRefreshToken.
                // The reference bootstrap branched on IsAccountLoggedIn and
                // skipped the logon when "already" was true; on Android we
                // never have valid cached session state across process
                // restarts (we stage empty config.vdf/local.vdf), so the
                // "already known" return is misleading and we must
                // actively re-authenticate every session.
                using SetLoginInfoFn = void (*)(void*, const char*, const char*, int);
                auto set_login = reinterpret_cast<SetLoginInfoFn>(
                    iuser_vt[kVtClientUser_SetLoginInformation / 8]);
                set_login(iuser, user.c_str(), "", 1);
                LOGI("IClientUser.SetLoginInformation(%s, \"\", 1) called",
                     user.c_str());

                using LogonRefreshFn = void (*)(void*, const char*, const char*);
                auto logon = reinterpret_cast<LogonRefreshFn>(
                    iuser_vt[kVtClientUser_LogonWithRefresh / 8]);
                logon(iuser, token.c_str(), user.c_str());
                LOGI("IClientUser.LogonWithRefreshToken called (token=%zu bytes)",
                     token.size());

                using SetSteamIDFn = void (*)(void*, uint64_t);
                auto set_sid = reinterpret_cast<SetSteamIDFn>(
                    iuser_vt[kVtClientUser_SetSteamID / 8]);
                set_sid(iuser, steamId);
                LOGI("IClientUser.SetSteamID(%llu) called",
                     static_cast<unsigned long long>(steamId));
            }
        } else {
            LOGW("CreateInterface(CLIENTENGINE_INTERFACE_VERSION005) -> %p (err=%d)",
                 engine, err);
        }
    } else {
        LOGI("no credentials provided - skipping refresh-token login");
    }

    // -------------------------------------------------------------------
    // STEP 4 — Poll Steam_BLoggedOn until logged on (up to 10s). Drain
    // pending callbacks between polls so libsteamclient.so can process
    // server responses + state transitions.
    // -------------------------------------------------------------------
    constexpr int kMaxPolls = 100;
    constexpr int kPollUsec = 100 * 1000;   // 100ms each
    bool logged_on = false;
    int  polls = 0;
    char cb_buf[64] = {0};   // CCallbackBase header; 64 is safe
    for (; polls < kMaxPolls; ++polls) {
        if (g_state.fn_Steam_BGetCallback && g_state.fn_Steam_FreeLastCallback) {
            while (g_state.fn_Steam_BGetCallback(pipe_out, cb_buf)) {
                // CallbackMsg_t: m_hSteamUser@0, m_iCallback@4. The id tells
                // us where the logon stalls (101 SteamServersConnected,
                // 102 SteamServerConnectFailure, 113 SteamServersDisconnected,
                // 3 LogonResponse-class, etc.).
                int cb_id = *reinterpret_cast<int*>(cb_buf + 4);
                // CallbackMsg_t: m_pubParam (the typed payload) @ offset 8.
                // id 102 = SteamServerConnectFailure_t { EResult m_eResult;
                // bool m_bStillRetrying; } — its EResult says network (3 =
                // NoConnection / 16 = Timeout) vs auth (5 InvalidPassword,
                // 6 LoggedInElsewhere, 7 InvalidProtocolVer, 65 Expired...).
                if (cb_id == 102) {
                    void* p = *reinterpret_cast<void**>(cb_buf + 8);
                    LOGE("init-poll: SteamServerConnectFailure EResult=%d retrying=%d",
                         p ? *reinterpret_cast<int*>(p) : -1,
                         p ? *reinterpret_cast<unsigned char*>(
                                 reinterpret_cast<char*>(p) + 4) : 0);
                } else {
                    LOGI("init-poll callback id=%d", cb_id);
                }
                g_state.fn_Steam_FreeLastCallback(pipe_out);
            }
        }
        if (g_state.fn_Steam_BLoggedOn &&
            g_state.fn_Steam_BLoggedOn(pipe_out, user_h)) {
            logged_on = true;
            break;
        }
        ::usleep(kPollUsec);
    }
    LOGI("Steam_BLoggedOn poll: logged_on=%d after %dx100ms",
         logged_on ? 1 : 0, polls);

    // Hand off to the persistent callback pump. The init poll above ran
    // single-threaded; only now that it has finished is it safe to let a
    // background thread own Steam_BGetCallback. Without this the logon
    // stalls the moment start() returns — Bionic games launch but never
    // authenticate (online play / DLC stay broken).
    if (!g_state.pump_running.exchange(true)) {
        std::thread(callback_pump_loop).detach();
        LOGI("callback pump thread started (libsteamclient session kept live)");
    }

    return 0;
}

// True iff libsteamclient.so is loaded AND Steam_BLoggedOn reports the
// pipe+user as authenticated. Cheap synchronous call — safe from any thread.
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeIsLoggedOn(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.fn_Steam_BLoggedOn) return JNI_FALSE;
    return g_state.fn_Steam_BLoggedOn(g_state.pipe, g_state.user)
        ? JNI_TRUE : JNI_FALSE;
}

// SteamID64 is set by the launcher during init; we don't currently query
// libsteamclient.so for it after the fact (the IClientUser path would
// be additional vtable RE). Return the cached value if we have one.
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeGetSteamId(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    // We don't cache steamId yet; return 0. Kotlin can read from PrefManager.
    return 0;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeShutdown(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized) return;
    // Tear down in reverse order of init: log off the user, drop the
    // pipe. We don't dlclose libsteamclient.so — it leaves background
    // threads that crash on unload (the same pattern every embedded
    // Steam launcher we surveyed follows).
    if (g_state.fn_Steam_LogOff && g_state.user != 0 && g_state.pipe != 0) {
        g_state.fn_Steam_LogOff(g_state.pipe, g_state.user);
    }
    // Roll back every env var nativeInit set. The Android process outlives
    // a single wine launch; without this pass a subsequent Launch-Steam-Client
    // (real Steam) launch in the same process inherits the bionic env keys
    // (WINESTEAMCLIENTPATH, Steam3Master, SteamUser, …) and steam.exe gets
    // confused / hits "Steam installation problem".
    for (const auto& k : g_state.applied_env_keys) {
        ::unsetenv(k.c_str());
        LOGI("unsetenv %s", k.c_str());
    }
    g_state.applied_env_keys.clear();
    g_state.lsc_handle = nullptr;
    g_state.pipe       = 0;
    g_state.user       = 0;
    g_state.iclient_user                    = nullptr;
    g_state.fn_CreateInterface              = nullptr;
    g_state.fn_Steam_CreateGlobalUser       = nullptr;
    g_state.fn_Steam_BLoggedOn              = nullptr;
    g_state.fn_Steam_LogOff                 = nullptr;
    g_state.fn_Steam_BGetCallback           = nullptr;
    g_state.fn_Steam_FreeLastCallback       = nullptr;
    g_state.fn_Breakpad_SteamSetAppID       = nullptr;
    g_state.initialized = false;
    LOGI("nativeShutdown done");
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativePrepareApp(
        JNIEnv* env, jclass /*cls*/, jintArray jappIds) {
    // Phase 8b.6+: drive ISteamApps via the IClientEngine sub-interface to
    // warm libsteamclient.so's own PICS cache for the given appids. For
    // now we log and let the wn-steam-client's own prepareApp (Phase 4.5)
    // do the heavy lifting.
    if (!jappIds) return;
    jsize n = env->GetArrayLength(jappIds);
    LOGI("nativePrepareApp: %d ids (passed through to log; not yet wired "
         "to libsteamclient.so PICS)", n);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeSetCloudEnabled(
        JNIEnv* /*env*/, jclass /*cls*/, jint app_id, jboolean enabled) {
    // Phase 8b.6+: route through IClientRemoteStorage. Vtable slot needs
    // additional RE; deferred.
    LOGI("setCloudEnabled(app=%d, on=%d) — not yet wired",
         app_id, enabled ? 1 : 0);
}

}  // extern "C"
