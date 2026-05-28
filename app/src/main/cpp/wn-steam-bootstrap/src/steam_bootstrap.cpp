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
#include <chrono>
#include <condition_variable>
#include <map>
#include <mutex>
#include <set>
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
//          slot 49 (offset 0x188): bool IsAccountLoggedIn(account)
//          slot 54 (offset 0x1B0): SetLoginInformation(account, password, remember)
//          slot 56 (offset 0x1C0): LogonWithRefreshToken(refreshToken, account)
//          slot 50 (offset 0x190): SetAccount(account, 1)        [already-logged path]
//          slot 1  (offset 0x08): SetSteamID(steamId64)
//   6.  Poll loop, up to 100×100ms=10s:
//          drain pending callbacks via Steam_BGetCallback/FreeLastCallback
//          if Steam_BLoggedOn(pipe, user) — DONE
struct State {
    ~State() {
        pump_running.store(false, std::memory_order_release);
        if (pump_thread.joinable()) pump_thread.join();
    }

    std::mutex   mu;
    void*        lsc_handle  = nullptr;   // libsteamclient.so dlopen handle
    bool         initialized = false;
    bool         shutting_down = false;

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

    void* steamclient_iface     = nullptr;    // SteamClient020
    void* isteam_user           = nullptr;    // SteamUser023
    void* isteam_utils          = nullptr;    // SteamUtils010
    void* isteam_userstats      = nullptr;    // STEAMUSERSTATS_INTERFACE_VERSION013
    void* isteam_apps           = nullptr;    // ISteamApps (best matching ver)
    void* isteam_remotestorage  = nullptr;    // ISteamRemoteStorage (best ver)
    void* isteam_friends        = nullptr;    // ISteamFriends (best matching ver)
    const char* isteam_apps_ver = nullptr;
    const char* isteam_rs_ver   = nullptr;
    const char* isteam_friends_ver = nullptr;

    uint64_t cached_steam_id = 0;

    // Every env key nativeInit setenv()'d, recorded so nativeShutdown
    // can unsetenv() them. Otherwise a bionic→real-Steam mode switch in
    // the same process inherits leaked WINESTEAMCLIENTPATH / Steam3Master /
    // SteamUser etc. into the real-Steam wine subprocess.
    std::vector<std::string> applied_env_keys;

    // Persistent callback pump. libsteamclient.so's logon — and every
    // later Steam API round-trip the game makes — is message-driven: it
    // only advances while something drains Steam_BGetCallback. start()'s
    // 10s poll does that during init, but the session must keep being
    // pumped while initialized. nativeShutdown stops and joins the thread
    // before clearing the libsteamclient function pointers.
    std::atomic<bool> pump_running{false};
    std::thread       pump_thread;

    std::condition_variable                  cv_callback;
    std::set<int>                            subscribed_ids;
    std::map<int, std::vector<uint8_t>>      received_callbacks;
};
State g_state;

// IClientEngine vtable slots we call (offsets in bytes; aarch64 ABI has
// 8-byte fn ptrs). All confirmed by Ghidra decomp of reference bootstrap.
constexpr int kVtClientEngine_GetIClientUser = 0x40;   // returns sub-iface

constexpr int kVtClientUser_SetSteamID          = 0x08;
constexpr int kVtClientUser_IsAccountLoggedIn   = 0x188; // bool IsAccountLoggedIn(const char*)
constexpr int kVtClientUser_SetAccount          = 0x190; // (already-logged path)
constexpr int kVtClientUser_SetLoginInformation = 0x1B0; // (account, "", remember)
constexpr int kVtClientUser_LogonWithRefresh    = 0x1C0; // 2-arg (refreshToken, account)

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
    while (g_state.pump_running.load(std::memory_order_acquire)) {
        if (g_state.fn_Steam_BGetCallback && g_state.fn_Steam_FreeLastCallback) {
            while (g_state.fn_Steam_BGetCallback(g_state.pipe, cb_buf)) {
                int   cb_id   = *reinterpret_cast<int*>(cb_buf + 4);
                void* cb_data = *reinterpret_cast<void**>(cb_buf + 8);
                int   cb_size = *reinterpret_cast<int*>(cb_buf + 16);
                if (cb_logged < 120) {
                    LOGI("pump callback id=%d size=%d", cb_id, cb_size);
                    ++cb_logged;
                }
                bool wake = false;
                {
                    std::lock_guard<std::mutex> lk(g_state.mu);
                    if (g_state.subscribed_ids.count(cb_id)) {
                        auto& slot = g_state.received_callbacks[cb_id];
                        if (cb_data && cb_size > 0) {
                            slot.assign(
                                reinterpret_cast<const uint8_t*>(cb_data),
                                reinterpret_cast<const uint8_t*>(cb_data) + cb_size);
                        } else {
                            slot.clear();
                        }
                        wake = true;
                    }
                }
                if (wake) g_state.cv_callback.notify_all();
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

std::string android_files_dir(JNIEnv* env, jobject context) {
    if (!context) return {};
    jclass ctxCls = env->GetObjectClass(context);
    if (!ctxCls) return {};
    jmethodID mGetFilesDir =
        env->GetMethodID(ctxCls, "getFilesDir", "()Ljava/io/File;");
    env->DeleteLocalRef(ctxCls);
    if (!mGetFilesDir) return {};
    jobject fileObj = env->CallObjectMethod(context, mGetFilesDir);
    if (!fileObj) return {};
    std::string out;
    jclass fileCls = env->GetObjectClass(fileObj);
    if (fileCls) {
        jmethodID mGetPath =
            env->GetMethodID(fileCls, "getAbsolutePath", "()Ljava/lang/String;");
        if (mGetPath) {
            jstring js = static_cast<jstring>(
                env->CallObjectMethod(fileObj, mGetPath));
            out = jstr(env, js);
            if (js) env->DeleteLocalRef(js);
        }
        env->DeleteLocalRef(fileCls);
    }
    env->DeleteLocalRef(fileObj);
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
        JNIEnv* env, jclass /*cls*/, jobject context,
        jstring jlibPath, jstring jhome,
        jstring jsteam3Master, jstring jsteamClientService,
        jobjectArray jextraEnv,
        jstring jaccountName, jstring jrefreshToken, jlong jsteamId64,
        jint jappId) {
    std::unique_lock<std::mutex> lk(g_state.mu);
    if (g_state.shutting_down) {
        LOGW("nativeInit: shutdown in progress");
        return -6;
    }
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
    g_state.cached_steam_id = steamId;

    LOGI("nativeInit: libPath=%s home=%s steam3Master=%s "
         "steamClientService=%s user=%s tokenLen=%zu steamId=%llu",
         libPath.c_str(), home.c_str(), s3m.c_str(), scs.c_str(),
         user.c_str(), token.size(),
         static_cast<unsigned long long>(steamId));

    // ---- setenv pass BEFORE dlopen ----
    // applied_env_keys accumulates every key we setenv() in this init so
    // nativeShutdown can unsetenv() them on teardown.
    g_state.applied_env_keys.clear();
    apply_extra_env(env, jextraEnv);
    if (!home.empty()) {
        ::setenv("HOME", home.c_str(), 1);
        g_state.applied_env_keys.emplace_back("HOME");
        std::string state_dir = "/tmp";
        if (home.size() >= 5 && home.compare(home.size() - 5, 5, "/home") == 0) {
            state_dir = home.substr(0, home.size() - 4) + "tmp";
        } else if (auto slash = home.rfind("/home/"); slash != std::string::npos) {
            state_dir = home.substr(0, slash) + "/tmp";
        }
        ::mkdir(state_dir.c_str(), 0755);  // best-effort; ok if exists
        ::setenv("WN_STATE_DIR", state_dir.c_str(), 1);
        g_state.applied_env_keys.emplace_back("WN_STATE_DIR");
        LOGI("nativeInit: WN_STATE_DIR=%s", state_dir.c_str());
    }
    if (!s3m.empty()) {
        ::setenv("Steam3Master", s3m.c_str(), 1);
        g_state.applied_env_keys.emplace_back("Steam3Master");
    }
    if (jappId > 0) {
        char app_buf[16];
        std::snprintf(app_buf, sizeof(app_buf), "%d", static_cast<int>(jappId));
        ::setenv("SteamAppId",  app_buf, 1);
        ::setenv("SteamGameId", app_buf, 1);
        g_state.applied_env_keys.emplace_back("SteamAppId");
        g_state.applied_env_keys.emplace_back("SteamGameId");
        LOGI("nativeInit: SteamAppId=%s (caller-supplied)", app_buf);
    } else {
        LOGI("nativeInit: appId=0 — no SteamAppId env set "
             "(library/prewarm mode; ISteamApps/ISteamRemoteStorage/"
             "ISteamUserStats will instantiate as null)");
    }
    if (!scs.empty()) {
        ::setenv("SteamClientService", scs.c_str(), 1);
        g_state.applied_env_keys.emplace_back("SteamClientService");
    }

    if (!::getenv("STEAM_SSL_CERT_FILE")) {
        const std::string files_dir = android_files_dir(env, context);
        if (!files_dir.empty()) {
            const std::string ca = files_dir + "/wnsteam_cacert.pem";
            struct stat st{};
            if (::stat(ca.c_str(), &st) == 0 && st.st_size > 0) {
                ::setenv("STEAM_SSL_CERT_FILE", ca.c_str(), 1);
                g_state.applied_env_keys.emplace_back("STEAM_SSL_CERT_FILE");
                LOGI("setenv STEAM_SSL_CERT_FILE=%s", ca.c_str());
            } else {
                LOGW("STEAM_SSL_CERT_FILE not set: %s missing/empty — call "
                     "CaBundleExtractor.ensureBundle() before start() or "
                     "libsteamclient.so TLS logon may fail", ca.c_str());
            }
        }
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
    //
    typedef void* (*SteamService_StartThread_fn)(const char*);
    auto svc_start = reinterpret_cast<SteamService_StartThread_fn>(
        ::dlsym(RTLD_DEFAULT, "SteamService_StartThread"));
    if (svc_start) {
        void* svc_ctx = svc_start("SteamClientService");
        LOGI("SteamService_StartThread(\"SteamClientService\") -> %p", svc_ctx);
    } else {
        LOGW("SteamService_StartThread symbol not in global namespace — "
             "preload may have failed; wine guest may crash on CreateInterface");
    }

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
    //   CreateInterface("CLIENTENGINE_INTERFACE_VERSION005", &err)  → engine
    //   IClientUser_vt:
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
                LOGI("IClientUser.IsAccountLoggedIn(%s) = %d", user.c_str(),
                     already ? 1 : 0);

                bool auto_logged_on = false;
                if (already && g_state.fn_Steam_BLoggedOn) {
                    constexpr int kAutoPollMax = 30;          // 30 × 100ms = 3s
                    for (int i = 0; i < kAutoPollMax; ++i) {
                        if (g_state.fn_Steam_BLoggedOn(g_state.pipe, g_state.user)) {
                            auto_logged_on = true;
                            LOGI("Auto-logon from cached session OK after %dx100ms — "
                                 "skipping forced LogonWithRefreshToken (avoids the "
                                 "stale-token AccessDenied)", i + 1);
                            break;
                        }
                        ::usleep(100 * 1000);
                    }
                }
                using SetSteamIDFn = void (*)(void*, uint64_t);
                auto set_sid = reinterpret_cast<SetSteamIDFn>(
                    iuser_vt[kVtClientUser_SetSteamID / 8]);
                if (auto_logged_on) {
                    set_sid(iuser, steamId);
                    LOGI("IClientUser.SetSteamID(%llu) called (post auto-logon)",
                         static_cast<unsigned long long>(steamId));
                } else {
                    LOGI("No cached session usable; driving forced "
                         "LogonWithRefreshToken with the stored token "
                         "(account-known=%d)", already ? 1 : 0);

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

                    set_sid(iuser, steamId);
                    LOGI("IClientUser.SetSteamID(%llu) called",
                         static_cast<unsigned long long>(steamId));
                }
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
    if (!g_state.pump_running.exchange(true, std::memory_order_acq_rel)) {
        if (g_state.pump_thread.joinable()) g_state.pump_thread.join();
        g_state.pump_thread = std::thread(callback_pump_loop);
        LOGI("callback pump thread started (libsteamclient session kept live)");
        using StartFn = void (*)(void);
        auto start = reinterpret_cast<StartFn>(
            ::dlsym(g_state.lsc_handle, "wn_cm_bridge_start_state_sync_poller"));
        if (start != nullptr) {
            start();
            LOGI("cross-process state-sync poller started");
        } else {
            LOGW("wn_cm_bridge_start_state_sync_poller not found: %s",
                 ::dlerror());
        }
    }

    {
        int sc_err = 0;
        void* steamclient = g_state.fn_CreateInterface("SteamClient020", &sc_err);
        LOGI("Stage2: CreateInterface(SteamClient020) -> %p err=%d logged_on=%d",
             steamclient, sc_err, logged_on ? 1 : 0);
        if (steamclient && sc_err == 0) {
            g_state.steamclient_iface     = nullptr;
            g_state.isteam_user           = nullptr;
            g_state.isteam_utils          = nullptr;
            g_state.isteam_userstats      = nullptr;
            g_state.isteam_apps           = nullptr;
            g_state.isteam_remotestorage  = nullptr;
            g_state.isteam_friends        = nullptr;
            g_state.isteam_apps_ver       = nullptr;
            g_state.isteam_rs_ver         = nullptr;
            g_state.isteam_friends_ver    = nullptr;

            g_state.steamclient_iface = steamclient;
            long* sc_vt = *reinterpret_cast<long**>(steamclient);

            using GetUtilsFn = void* (*)(void*, int, const char*);
            auto get_utils = reinterpret_cast<GetUtilsFn>(sc_vt[9]);
            g_state.isteam_utils = get_utils(steamclient, pipe_out, "SteamUtils010");
            LOGI("Stage2: ISteamClient.GetISteamUtils(SteamUtils010) -> %p",
                 g_state.isteam_utils);

            using GetUserFn = void* (*)(void*, int, int, const char*);
            auto get_user = reinterpret_cast<GetUserFn>(sc_vt[5]);
            g_state.isteam_user = get_user(steamclient, user_h, pipe_out,
                                           "SteamUser023");
            LOGI("Stage2: ISteamClient.GetISteamUser(SteamUser023) -> %p",
                 g_state.isteam_user);

            using GetStatsFn = void* (*)(void*, int, int, const char*);
            auto get_stats = reinterpret_cast<GetStatsFn>(sc_vt[13]);
            g_state.isteam_userstats = get_stats(
                steamclient, user_h, pipe_out,
                "STEAMUSERSTATS_INTERFACE_VERSION013");
            LOGI("Stage2: ISteamClient.GetISteamUserStats(v013) -> %p",
                 g_state.isteam_userstats);

            using GetAppsFn = void* (*)(void*, int, int, const char*);
            auto get_apps = reinterpret_cast<GetAppsFn>(sc_vt[14]);
            for (const char* v : {"STEAMAPPS_INTERFACE_VERSION009",
                                  "STEAMAPPS_INTERFACE_VERSION008",
                                  "STEAMAPPS_INTERFACE_VERSION007"}) {
                void* o = get_apps(steamclient, user_h, pipe_out, v);
                LOGI("Stage2:   GetISteamApps(\"%s\") -> %p", v, o);
                if (o) {
                    g_state.isteam_apps     = o;
                    g_state.isteam_apps_ver = v;
                    break;
                }
            }
            LOGI("Stage2: ISteamClient.GetISteamApps WINNER -> %p ver=%s",
                 g_state.isteam_apps,
                 g_state.isteam_apps_ver ? g_state.isteam_apps_ver : "(none)");

            using GetRSFn = void* (*)(void*, int, int, const char*);
            auto get_rs = reinterpret_cast<GetRSFn>(sc_vt[16]);
            for (const char* v : {"STEAMREMOTESTORAGE_INTERFACE_VERSION016",
                                  "STEAMREMOTESTORAGE_INTERFACE_VERSION014"}) {
                void* o = get_rs(steamclient, user_h, pipe_out, v);
                LOGI("Stage2:   GetISteamRemoteStorage(\"%s\") -> %p", v, o);
                if (o) {
                    g_state.isteam_remotestorage = o;
                    g_state.isteam_rs_ver        = v;
                    break;
                }
            }
            LOGI("Stage2: ISteamClient.GetISteamRemoteStorage WINNER -> %p ver=%s",
                 g_state.isteam_remotestorage,
                 g_state.isteam_rs_ver ? g_state.isteam_rs_ver : "(none)");

            using GetFriendsFn = void* (*)(void*, int, int, const char*);
            auto get_friends = reinterpret_cast<GetFriendsFn>(sc_vt[8]);
            for (const char* v : {"SteamFriends017",
                                  "SteamFriends018",
                                  "SteamFriends015"}) {
                void* o = get_friends(steamclient, user_h, pipe_out, v);
                LOGI("Stage2:   GetISteamFriends(\"%s\") -> %p", v, o);
                if (o) {
                    g_state.isteam_friends     = o;
                    g_state.isteam_friends_ver = v;
                    break;
                }
            }
            LOGI("Stage2: ISteamClient.GetISteamFriends WINNER -> %p ver=%s",
                 g_state.isteam_friends,
                 g_state.isteam_friends_ver ? g_state.isteam_friends_ver : "(none)");

            if (!logged_on && g_state.isteam_userstats) {
                LOGW("Stage2: NULLING isteam_userstats (got non-null %p but "
                     "logon failed — half-baked iface would SIGSEGV)",
                     g_state.isteam_userstats);
                g_state.isteam_userstats = nullptr;
            }

            if (g_state.isteam_apps) {
                long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
                using BIsSubAppFn = bool (*)(void*, unsigned int);
                auto is_sub_app = reinterpret_cast<BIsSubAppFn>(apps_vt[6]);
                const unsigned probe = jappId > 0
                    ? static_cast<unsigned>(jappId)
                    : 242760u;
                bool owns = is_sub_app(g_state.isteam_apps, probe);
                LOGI("Stage2: ISteamApps.BIsSubscribedApp(%u) = %d", probe, owns ? 1 : 0);
            }
            if (g_state.isteam_user) {
                long* u_vt = *reinterpret_cast<long**>(g_state.isteam_user);
                using GetSteamIDFn = uint64_t (*)(void*);
                auto get_sid = reinterpret_cast<GetSteamIDFn>(u_vt[2]);
                uint64_t live_sid = get_sid(g_state.isteam_user);
                LOGI("Stage2: ISteamUser.GetSteamID() = %llu (prefmgr=%llu, "
                     "match=%d)",
                     static_cast<unsigned long long>(live_sid),
                     static_cast<unsigned long long>(g_state.cached_steam_id),
                     live_sid == g_state.cached_steam_id ? 1 : 0);
            }
            if (g_state.isteam_utils) {
                long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
                using GetAppIDFn = uint32_t (*)(void*);
                auto get_app_id = reinterpret_cast<GetAppIDFn>(utils_vt[9]);
                uint32_t live_app = get_app_id(g_state.isteam_utils);
                LOGI("Stage2: ISteamUtils.GetAppID() = %u", live_app);
            }
            if (g_state.isteam_friends) {
                long* fr_vt = *reinterpret_cast<long**>(g_state.isteam_friends);
                using NameFn  = const char* (*)(void*);
                using StateFn = int (*)(void*);
                using CountFn = int (*)(void*, int);
                auto get_name  = reinterpret_cast<NameFn>(fr_vt[0]);
                auto get_state = reinterpret_cast<StateFn>(fr_vt[2]);
                auto get_count = reinterpret_cast<CountFn>(fr_vt[3]);
                const char* name = get_name(g_state.isteam_friends);
                int         st   = get_state(g_state.isteam_friends);
                int         fcount = get_count(g_state.isteam_friends, 0x4);
                LOGI("Stage2: ISteamFriends.GetPersonaName=\"%s\" "
                     "GetPersonaState=%d GetFriendCount(immediate)=%d",
                     name ? name : "(null)", st, fcount);
            }
            if (g_state.isteam_remotestorage) {
                long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
                using GetCountFn   = int  (*)(void*);
                using GetQuotaFn   = void (*)(void*, uint64_t*, uint64_t*);
                using CloudAcctFn  = bool (*)(void*);
                using CloudAppFn   = bool (*)(void*);
                auto get_count    = reinterpret_cast<GetCountFn>(rs_vt[18]);
                auto get_quota    = reinterpret_cast<GetQuotaFn>(rs_vt[20]);
                auto cloud_acct   = reinterpret_cast<CloudAcctFn>(rs_vt[21]);
                auto cloud_app    = reinterpret_cast<CloudAppFn>(rs_vt[22]);
                int files = get_count(g_state.isteam_remotestorage);
                uint64_t total = 0, avail = 0;
                get_quota(g_state.isteam_remotestorage, &total, &avail);
                bool on_acct = cloud_acct(g_state.isteam_remotestorage);
                bool on_app  = cloud_app(g_state.isteam_remotestorage);
                LOGI("Stage2: ISteamRemoteStorage cloud_account=%d cloud_app=%d "
                     "file_count=%d quota_total=%llu avail=%llu",
                     on_acct ? 1 : 0, on_app ? 1 : 0, files,
                     static_cast<unsigned long long>(total),
                     static_cast<unsigned long long>(avail));
            }
            if (g_state.isteam_userstats && logged_on) {
                long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
                using ReqFn       = bool     (*)(void*);
                using GetNumAchFn = uint32_t (*)(void*);
                auto req_stats = reinterpret_cast<ReqFn>(us_vt[0]);
                auto get_n     = reinterpret_cast<GetNumAchFn>(us_vt[14]);
                constexpr int kUserStatsReceived = 1101;
                g_state.subscribed_ids.insert(kUserStatsReceived);
                g_state.received_callbacks.erase(kUserStatsReceived);
                bool req_ok = req_stats(g_state.isteam_userstats);
                LOGI("Stage2: ISteamUserStats.RequestCurrentStats() = %d",
                     req_ok ? 1 : 0);
                auto deadline = std::chrono::steady_clock::now()
                              + std::chrono::seconds(3);
                g_state.cv_callback.wait_until(lk, deadline, [&] {
                    return g_state.received_callbacks.count(kUserStatsReceived) > 0;
                });
                uint32_t n = get_n(g_state.isteam_userstats);
                LOGI("Stage2: ISteamUserStats.GetNumAchievements() = %u "
                     "(UserStatsReceived_t %s)",
                     n,
                     g_state.received_callbacks.count(kUserStatsReceived)
                         ? "arrived" : "TIMEOUT");
            }
        }
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

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeGetSteamId(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized) return 0;
    return static_cast<jlong>(g_state.cached_steam_id);
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeBIsSubscribedApp(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized) {
        LOGW("nativeBIsSubscribedApp(%d): not initialized", appId);
        return JNI_FALSE;
    }
    if (!g_state.isteam_apps) {
        LOGW("nativeBIsSubscribedApp(%d): ISteamApps not cached "
             "(auth-gated — needs a logged-on session)", appId);
        return JNI_FALSE;
    }
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using BIsSubAppFn = bool (*)(void*, unsigned int);
    auto is_sub_app = reinterpret_cast<BIsSubAppFn>(apps_vt[6]);
    const bool owns = is_sub_app(g_state.isteam_apps,
                                 static_cast<unsigned int>(appId));
    return owns ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsBIsAppInstalled(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return JNI_FALSE;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using BIsInstFn = bool (*)(void*, unsigned int);
    auto is_inst = reinterpret_cast<BIsInstFn>(apps_vt[19]);
    return is_inst(g_state.isteam_apps,
                   static_cast<unsigned int>(appId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetAppInstallDir(
        JNIEnv* env, jclass /*cls*/, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return nullptr;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using GetDirFn = uint32_t (*)(void*, unsigned int, char*, uint32_t);
    auto get_dir = reinterpret_cast<GetDirFn>(apps_vt[18]);
    char buf[1024] = {0};
    uint32_t n = get_dir(g_state.isteam_apps,
                         static_cast<unsigned int>(appId),
                         buf, sizeof(buf));
    if (n == 0) return nullptr;
    return env->NewStringUTF(buf);
}

JNIEXPORT jintArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetInstalledDepots(
        JNIEnv* env, jclass /*cls*/, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return env->NewIntArray(0);
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using GetDepFn = uint32_t (*)(void*, unsigned int, unsigned int*, uint32_t);
    auto get_dep = reinterpret_cast<GetDepFn>(apps_vt[17]);
    unsigned int depots[64] = {0};
    uint32_t n = get_dep(g_state.isteam_apps,
                         static_cast<unsigned int>(appId),
                         depots, 64);
    if (n > 64) n = 64;
    jintArray out = env->NewIntArray(static_cast<jsize>(n));
    if (!out) return nullptr;
    if (n > 0) {
        env->SetIntArrayRegion(out, 0, static_cast<jsize>(n),
                               reinterpret_cast<const jint*>(depots));
    }
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetCurrentGameLanguage(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return nullptr;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using GetLangFn = const char* (*)(void*);
    auto get_lang = reinterpret_cast<GetLangFn>(apps_vt[4]);
    const char* v = get_lang(g_state.isteam_apps);
    return (v && *v) ? env->NewStringUTF(v) : nullptr;
}


JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsBIsDlcInstalled(
        JNIEnv* /*env*/, jclass /*cls*/, jint dlcAppId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return JNI_FALSE;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using DlcInstFn = bool (*)(void*, unsigned int);
    auto dlc_inst = reinterpret_cast<DlcInstFn>(apps_vt[7]);
    return dlc_inst(g_state.isteam_apps,
                    static_cast<unsigned int>(dlcAppId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetEarliestPurchaseUnixTime(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return 0;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using PurchaseFn = uint32_t (*)(void*, unsigned int);
    auto purchase = reinterpret_cast<PurchaseFn>(apps_vt[8]);
    return static_cast<jint>(purchase(g_state.isteam_apps,
                                      static_cast<unsigned int>(appId)));
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetDLCCount(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return 0;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using DlcCountFn = int (*)(void*, unsigned int);
    auto dlc_count = reinterpret_cast<DlcCountFn>(apps_vt[10]);
    return static_cast<jint>(dlc_count(g_state.isteam_apps,
                                       static_cast<unsigned int>(appId)));
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetAppOwner(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return 0;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using GetOwnerFn = uint64_t (*)(void*);
    auto get_owner = reinterpret_cast<GetOwnerFn>(apps_vt[20]);
    return static_cast<jlong>(get_owner(g_state.isteam_apps));
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsBIsSubscribedFromFamilySharing(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return JNI_FALSE;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using FamShareFn = bool (*)(void*);
    auto fam_share = reinterpret_cast<FamShareFn>(apps_vt[27]);
    return fam_share(g_state.isteam_apps) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamAppsGetAppBuildId(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_apps) return 0;
    long* apps_vt = *reinterpret_cast<long**>(g_state.isteam_apps);
    using BuildIdFn = int (*)(void*);
    auto build_id = reinterpret_cast<BuildIdFn>(apps_vt[23]);
    return static_cast<jint>(build_id(g_state.isteam_apps));
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserBLoggedOn(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_user) return JNI_FALSE;
    long* u_vt = *reinterpret_cast<long**>(g_state.isteam_user);
    using BLoggedFn = bool (*)(void*);
    auto bl = reinterpret_cast<BLoggedFn>(u_vt[1]);
    return bl(g_state.isteam_user) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserHasLicenseForApp(
        JNIEnv* /*env*/, jclass /*cls*/, jlong steamId64, jint appId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_user) return 2; // no-auth
    long* u_vt = *reinterpret_cast<long**>(g_state.isteam_user);
    using HasLicFn = int (*)(void*, uint64_t, unsigned int);
    auto hl = reinterpret_cast<HasLicFn>(u_vt[18]);
    return static_cast<jint>(hl(g_state.isteam_user,
                                static_cast<uint64_t>(steamId64),
                                static_cast<unsigned int>(appId)));
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserGetSteamID(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_user) return 0;
    long* u_vt = *reinterpret_cast<long**>(g_state.isteam_user);
    using GetSteamIDFn = uint64_t (*)(void*);
    auto get_sid = reinterpret_cast<GetSteamIDFn>(u_vt[2]);
    return static_cast<jlong>(get_sid(g_state.isteam_user));
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetAppID(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_utils) return 0;
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using GetAppIDFn = uint32_t (*)(void*);
    auto get_app_id = reinterpret_cast<GetAppIDFn>(utils_vt[9]);
    return static_cast<jint>(get_app_id(g_state.isteam_utils));
}


JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetServerRealTime(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_utils) return 0;
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using TimeFn = uint32_t (*)(void*);
    auto get_time = reinterpret_cast<TimeFn>(utils_vt[3]);
    return static_cast<jint>(get_time(g_state.isteam_utils));
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetIPCountry(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_utils) return nullptr;
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using CountryFn = const char* (*)(void*);
    auto get_country = reinterpret_cast<CountryFn>(utils_vt[4]);
    const char* v = get_country(g_state.isteam_utils);
    return (v && *v) ? env->NewStringUTF(v) : nullptr;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetSteamUILanguage(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_utils) return nullptr;
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using LangFn = const char* (*)(void*);
    auto get_lang = reinterpret_cast<LangFn>(utils_vt[23]);
    const char* v = get_lang(g_state.isteam_utils);
    return (v && *v) ? env->NewStringUTF(v) : nullptr;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetCurrentBatteryPower(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_utils) return 255;  // assume AC
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using BatteryFn = uint8_t (*)(void*);
    auto get_battery = reinterpret_cast<BatteryFn>(utils_vt[8]);
    return static_cast<jint>(get_battery(g_state.isteam_utils));
}

JNIEXPORT jintArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetImageSize(
        JNIEnv* env, jclass /*cls*/, jint imageHandle) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    jintArray out = env->NewIntArray(2);
    if (!out) return nullptr;
    if (!g_state.initialized || !g_state.isteam_utils || imageHandle <= 0) return out;
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using SizeFn = bool (*)(void*, int, uint32_t*, uint32_t*);
    auto get_size = reinterpret_cast<SizeFn>(utils_vt[5]);
    uint32_t w = 0, h = 0;
    if (get_size(g_state.isteam_utils, imageHandle, &w, &h)) {
        jint values[2] = { static_cast<jint>(w), static_cast<jint>(h) };
        env->SetIntArrayRegion(out, 0, 2, values);
    }
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUtilsGetImageRGBA(
        JNIEnv* env, jclass /*cls*/, jint imageHandle, jbyteArray outRgba) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_utils || !outRgba || imageHandle <= 0) {
        return JNI_FALSE;
    }
    jsize n = env->GetArrayLength(outRgba);
    if (n <= 0) return JNI_FALSE;
    long* utils_vt = *reinterpret_cast<long**>(g_state.isteam_utils);
    using RGBAFn = bool (*)(void*, int, uint8_t*, int);
    auto get_rgba = reinterpret_cast<RGBAFn>(utils_vt[6]);
    jbyte* buf = env->GetByteArrayElements(outRgba, nullptr);
    if (!buf) return JNI_FALSE;
    bool ok = get_rgba(g_state.isteam_utils, imageHandle,
                       reinterpret_cast<uint8_t*>(buf), static_cast<int>(n));
    env->ReleaseByteArrayElements(outRgba, buf, 0);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageGetFileCount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage) return 0;
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using GetCountFn = int (*)(void*);
    auto get_count = reinterpret_cast<GetCountFn>(rs_vt[18]);
    return static_cast<jint>(get_count(g_state.isteam_remotestorage));
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageIsCloudEnabledForAccount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage) return JNI_FALSE;
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using CloudFn = bool (*)(void*);
    auto cloud = reinterpret_cast<CloudFn>(rs_vt[21]);
    return cloud(g_state.isteam_remotestorage) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageIsCloudEnabledForApp(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage) return JNI_FALSE;
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using CloudFn = bool (*)(void*);
    auto cloud = reinterpret_cast<CloudFn>(rs_vt[22]);
    return cloud(g_state.isteam_remotestorage) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageGetQuota(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    jlongArray out = env->NewLongArray(2);
    if (!out) return nullptr;
    if (g_state.initialized && g_state.isteam_remotestorage) {
        long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
        using GetQuotaFn = void (*)(void*, uint64_t*, uint64_t*);
        auto get_quota = reinterpret_cast<GetQuotaFn>(rs_vt[20]);
        uint64_t total = 0, avail = 0;
        get_quota(g_state.isteam_remotestorage, &total, &avail);
        jlong values[2] = { static_cast<jlong>(total), static_cast<jlong>(avail) };
        env->SetLongArrayRegion(out, 0, 2, values);
    }
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsRequestCurrentStats(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats) return JNI_FALSE;
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using ReqFn = bool (*)(void*);
    auto req = reinterpret_cast<ReqFn>(us_vt[0]);
    return req(g_state.isteam_userstats) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsGetNumAchievements(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats) return 0;
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using GetNumFn = uint32_t (*)(void*);
    auto n = reinterpret_cast<GetNumFn>(us_vt[14]);
    return static_cast<jint>(n(g_state.isteam_userstats));
}


JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageListFiles(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    jclass strCls = env->FindClass("java/lang/String");
    if (!g_state.initialized || !g_state.isteam_remotestorage) {
        return env->NewObjectArray(0, strCls, nullptr);
    }
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using GetCountFn = int (*)(void*);
    using GetNameSizeFn = const char* (*)(void*, int, int32_t*);
    auto get_count = reinterpret_cast<GetCountFn>(rs_vt[18]);
    auto get_ns    = reinterpret_cast<GetNameSizeFn>(rs_vt[19]);
    int n = get_count(g_state.isteam_remotestorage);
    if (n < 0) n = 0;
    jobjectArray out = env->NewObjectArray(n, strCls, nullptr);
    if (!out) return nullptr;
    for (int i = 0; i < n; ++i) {
        int32_t size = 0;
        const char* name = get_ns(g_state.isteam_remotestorage, i, &size);
        if (!name) name = "";
        char buf[1024];
        std::snprintf(buf, sizeof(buf), "%s\t%d", name, size);
        jstring js = env->NewStringUTF(buf);
        env->SetObjectArrayElement(out, i, js);
        env->DeleteLocalRef(js);
    }
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageFileExists(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using ExistsFn = bool (*)(void*, const char*);
    auto exists = reinterpret_cast<ExistsFn>(rs_vt[13]);
    return exists(g_state.isteam_remotestorage, name.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageFileRead(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage || !jname) return nullptr;
    std::string name = jstr(env, jname);
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using GetSizeFn = int32_t (*)(void*, const char*);
    using FileReadFn = int32_t (*)(void*, const char*, void*, int32_t);
    auto get_size  = reinterpret_cast<GetSizeFn>(rs_vt[15]);
    auto file_read = reinterpret_cast<FileReadFn>(rs_vt[1]);
    int32_t size = get_size(g_state.isteam_remotestorage, name.c_str());
    if (size <= 0) return nullptr;
    jbyteArray out = env->NewByteArray(size);
    if (!out) return nullptr;
    jbyte* buf = env->GetByteArrayElements(out, nullptr);
    if (!buf) return nullptr;
    int32_t read = file_read(g_state.isteam_remotestorage, name.c_str(),
                             buf, size);
    env->ReleaseByteArrayElements(out, buf, 0);
    if (read != size) {
        return nullptr;
    }
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageFileWrite(
        JNIEnv* env, jclass /*cls*/, jstring jname, jbyteArray jdata) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage || !jname || !jdata) {
        return JNI_FALSE;
    }
    std::string name = jstr(env, jname);
    jsize n = env->GetArrayLength(jdata);
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using FileWriteFn = bool (*)(void*, const char*, const void*, int32_t);
    auto file_write = reinterpret_cast<FileWriteFn>(rs_vt[0]);
    jbyte* buf = env->GetByteArrayElements(jdata, nullptr);
    if (!buf) return JNI_FALSE;
    bool ok = file_write(g_state.isteam_remotestorage, name.c_str(),
                         buf, static_cast<int32_t>(n));
    env->ReleaseByteArrayElements(jdata, buf, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageFileDelete(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using DeleteFn = bool (*)(void*, const char*);
    auto file_del = reinterpret_cast<DeleteFn>(rs_vt[6]);
    return file_del(g_state.isteam_remotestorage, name.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamRemoteStorageSetCloudEnabledForApp(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean enabled) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_remotestorage) return;
    long* rs_vt = *reinterpret_cast<long**>(g_state.isteam_remotestorage);
    using SetEnabledFn = void (*)(void*, bool);
    auto set_en = reinterpret_cast<SetEnabledFn>(rs_vt[23]);
    set_en(g_state.isteam_remotestorage, enabled == JNI_TRUE);
}


JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsListAchievements(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    jclass strCls = env->FindClass("java/lang/String");
    if (!g_state.initialized || !g_state.isteam_userstats) {
        return env->NewObjectArray(0, strCls, nullptr);
    }
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using GetNumFn  = uint32_t (*)(void*);
    using GetNameFn = const char* (*)(void*, uint32_t);
    auto get_n    = reinterpret_cast<GetNumFn>(us_vt[14]);
    auto get_name = reinterpret_cast<GetNameFn>(us_vt[15]);
    uint32_t n = get_n(g_state.isteam_userstats);
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(n), strCls, nullptr);
    if (!out) return nullptr;
    for (uint32_t i = 0; i < n; ++i) {
        const char* name = get_name(g_state.isteam_userstats, i);
        if (!name) name = "";
        jstring js = env->NewStringUTF(name);
        env->SetObjectArrayElement(out, static_cast<jsize>(i), js);
        env->DeleteLocalRef(js);
    }
    return out;
}

JNIEXPORT jintArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsGetAchievementAndUnlockTime(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    jintArray out = env->NewIntArray(2);
    if (!out) return nullptr;
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return out;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using GetAchUtFn = bool (*)(void*, const char*, bool*, uint32_t*);
    auto get_aut = reinterpret_cast<GetAchUtFn>(us_vt[9]);
    bool achieved = false;
    uint32_t unlock_time = 0;
    bool ok = get_aut(g_state.isteam_userstats, name.c_str(),
                      &achieved, &unlock_time);
    if (!ok) return out;
    jint values[2] = { achieved ? 1 : 0, static_cast<jint>(unlock_time) };
    env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsSetAchievement(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using SetAchFn = bool (*)(void*, const char*);
    auto set_ach = reinterpret_cast<SetAchFn>(us_vt[7]);
    return set_ach(g_state.isteam_userstats, name.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsClearAchievement(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using ClrAchFn = bool (*)(void*, const char*);
    auto clr_ach = reinterpret_cast<ClrAchFn>(us_vt[8]);
    return clr_ach(g_state.isteam_userstats, name.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsStoreStats(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats) return JNI_FALSE;
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using StoreFn = bool (*)(void*);
    auto store = reinterpret_cast<StoreFn>(us_vt[10]);
    return store(g_state.isteam_userstats) ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsGetStatInt(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return 0;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using GetStatIFn = bool (*)(void*, const char*, int32_t*);
    auto get_stat = reinterpret_cast<GetStatIFn>(us_vt[1]);
    int32_t data = 0;
    if (!get_stat(g_state.isteam_userstats, name.c_str(), &data)) return 0;
    return static_cast<jint>(data);
}

JNIEXPORT jfloat JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsGetStatFloat(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return 0.0f;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using GetStatFFn = bool (*)(void*, const char*, float*);
    auto get_stat = reinterpret_cast<GetStatFFn>(us_vt[2]);
    float data = 0.0f;
    if (!get_stat(g_state.isteam_userstats, name.c_str(), &data)) return 0.0f;
    return static_cast<jfloat>(data);
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsSetStatInt(
        JNIEnv* env, jclass /*cls*/, jstring jname, jint jdata) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using SetStatIFn = bool (*)(void*, const char*, int32_t);
    auto set_stat = reinterpret_cast<SetStatIFn>(us_vt[3]);
    return set_stat(g_state.isteam_userstats, name.c_str(),
                    static_cast<int32_t>(jdata)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsSetStatFloat(
        JNIEnv* env, jclass /*cls*/, jstring jname, jfloat jdata) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using SetStatFFn = bool (*)(void*, const char*, float);
    auto set_stat = reinterpret_cast<SetStatFFn>(us_vt[4]);
    return set_stat(g_state.isteam_userstats, name.c_str(),
                    static_cast<float>(jdata)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsUpdateAvgRateStat(
        JNIEnv* env, jclass /*cls*/, jstring jname,
        jfloat jcountThisSession, jdouble jsessionLength) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return JNI_FALSE;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using UpdateRateFn = bool (*)(void*, const char*, float, double);
    auto upd = reinterpret_cast<UpdateRateFn>(us_vt[5]);
    return upd(g_state.isteam_userstats, name.c_str(),
               static_cast<float>(jcountThisSession),
               static_cast<double>(jsessionLength)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsGetAchievementDisplayAttribute(
        JNIEnv* env, jclass /*cls*/, jstring jname, jstring jkey) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname || !jkey) return nullptr;
    std::string name = jstr(env, jname);
    std::string key  = jstr(env, jkey);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using GetAttrFn = const char* (*)(void*, const char*, const char*);
    auto get_attr = reinterpret_cast<GetAttrFn>(us_vt[12]);
    const char* v = get_attr(g_state.isteam_userstats, name.c_str(), key.c_str());
    if (!v || !*v) return nullptr;
    return env->NewStringUTF(v);
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamUserStatsGetAchievementIcon(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_userstats || !jname) return 0;
    std::string name = jstr(env, jname);
    long* us_vt = *reinterpret_cast<long**>(g_state.isteam_userstats);
    using IconFn = int (*)(void*, const char*);
    auto icon = reinterpret_cast<IconFn>(us_vt[11]);
    return static_cast<jint>(icon(g_state.isteam_userstats, name.c_str()));
}


JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamFriendsGetPersonaName(
        JNIEnv* env, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_friends) return nullptr;
    long* vt = *reinterpret_cast<long**>(g_state.isteam_friends);
    using NameFn = const char* (*)(void*);
    auto get_name = reinterpret_cast<NameFn>(vt[0]);
    const char* n = get_name(g_state.isteam_friends);
    return (n && *n) ? env->NewStringUTF(n) : nullptr;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamFriendsGetPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_friends) return 0;  // 0=Offline
    long* vt = *reinterpret_cast<long**>(g_state.isteam_friends);
    using StateFn = int (*)(void*);
    auto get_state = reinterpret_cast<StateFn>(vt[2]);
    return static_cast<jint>(get_state(g_state.isteam_friends));
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamFriendsGetFriendCount(
        JNIEnv* /*env*/, jclass /*cls*/, jint flags) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_friends) return 0;
    long* vt = *reinterpret_cast<long**>(g_state.isteam_friends);
    using CountFn = int (*)(void*, int);
    auto get_count = reinterpret_cast<CountFn>(vt[3]);
    return static_cast<jint>(get_count(g_state.isteam_friends,
                                       static_cast<int>(flags)));
}

JNIEXPORT jlongArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamFriendsListFriends(
        JNIEnv* env, jclass /*cls*/, jint flags) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_friends) return env->NewLongArray(0);
    long* vt = *reinterpret_cast<long**>(g_state.isteam_friends);
    using CountFn = int (*)(void*, int);
    using ByIdxFn = uint64_t (*)(void*, int, int);
    auto get_count = reinterpret_cast<CountFn>(vt[3]);
    auto get_byidx = reinterpret_cast<ByIdxFn>(vt[4]);
    int n = get_count(g_state.isteam_friends, static_cast<int>(flags));
    if (n < 0) n = 0;
    jlongArray out = env->NewLongArray(n);
    if (!out || n == 0) return out;
    for (int i = 0; i < n; ++i) {
        uint64_t sid = get_byidx(g_state.isteam_friends, i,
                                 static_cast<int>(flags));
        jlong v = static_cast<jlong>(sid);
        env->SetLongArrayRegion(out, i, 1, &v);
    }
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamFriendsGetFriendPersonaName(
        JNIEnv* env, jclass /*cls*/, jlong steamId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_friends) return nullptr;
    long* vt = *reinterpret_cast<long**>(g_state.isteam_friends);
    using FNameFn = const char* (*)(void*, uint64_t);
    auto get_fname = reinterpret_cast<FNameFn>(vt[7]);
    const char* n = get_fname(g_state.isteam_friends,
                              static_cast<uint64_t>(steamId));
    return (n && *n) ? env->NewStringUTF(n) : nullptr;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeISteamFriendsGetFriendPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/, jlong steamId) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    if (!g_state.initialized || !g_state.isteam_friends) return 0;
    long* vt = *reinterpret_cast<long**>(g_state.isteam_friends);
    using FStateFn = int (*)(void*, uint64_t);
    auto get_fstate = reinterpret_cast<FStateFn>(vt[6]);
    return static_cast<jint>(get_fstate(g_state.isteam_friends,
                                        static_cast<uint64_t>(steamId)));
}


JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeSubscribeCallback(
        JNIEnv* /*env*/, jclass /*cls*/, jint id) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    g_state.subscribed_ids.insert(static_cast<int>(id));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeUnsubscribeCallback(
        JNIEnv* /*env*/, jclass /*cls*/, jint id) {
    std::lock_guard<std::mutex> lk(g_state.mu);
    g_state.subscribed_ids.erase(static_cast<int>(id));
    g_state.received_callbacks.erase(static_cast<int>(id));
}

JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeAwaitCallback(
        JNIEnv* env, jclass /*cls*/, jint id, jint timeoutMs) {
    std::unique_lock<std::mutex> lk(g_state.mu);
    const int cb_id = static_cast<int>(id);
    g_state.subscribed_ids.insert(cb_id);
    auto deadline = std::chrono::steady_clock::now()
                  + std::chrono::milliseconds(std::max<jint>(0, timeoutMs));
    bool got = g_state.cv_callback.wait_until(lk, deadline, [&] {
        return g_state.received_callbacks.count(cb_id) > 0
            || !g_state.initialized
            || g_state.shutting_down;
    });
    if (!got) return nullptr;
    auto it = g_state.received_callbacks.find(cb_id);
    if (it == g_state.received_callbacks.end()) return nullptr;
    std::vector<uint8_t> payload = std::move(it->second);
    g_state.received_callbacks.erase(it);
    lk.unlock();
    jbyteArray out = env->NewByteArray(static_cast<jsize>(payload.size()));
    if (!out) return nullptr;
    if (!payload.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(payload.size()),
                                reinterpret_cast<const jbyte*>(payload.data()));
    }
    return out;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativeShutdown(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::thread pump_thread;
    void* lsc_handle = nullptr;
    {
        std::lock_guard<std::mutex> lk(g_state.mu);
        if (!g_state.initialized) return;
        g_state.shutting_down = true;
        // Make concurrent JNI entry points fail fast while the pump thread is
        // being joined outside the global lock.
        g_state.initialized = false;
        g_state.pump_running.store(false, std::memory_order_release);
        lsc_handle = g_state.lsc_handle;
        if (g_state.pump_thread.joinable()) {
            pump_thread = std::move(g_state.pump_thread);
        }
        g_state.cv_callback.notify_all();
    }
    if (pump_thread.joinable()) pump_thread.join();
    if (lsc_handle) {
        using StopFn = void (*)(void);
        ::dlerror();
        auto stop = reinterpret_cast<StopFn>(
            ::dlsym(lsc_handle, "wn_cm_bridge_stop_state_sync_poller"));
        if (stop != nullptr) {
            stop();
            LOGI("cross-process state-sync poller stopped");
        } else {
            const char* err = ::dlerror();
            LOGW("wn_cm_bridge_stop_state_sync_poller not found: %s",
                 err ? err : "symbol missing");
        }
    }

    std::lock_guard<std::mutex> lk(g_state.mu);
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
    g_state.steamclient_iface     = nullptr;
    g_state.isteam_user           = nullptr;
    g_state.isteam_utils          = nullptr;
    g_state.isteam_userstats      = nullptr;
    g_state.isteam_apps           = nullptr;
    g_state.isteam_remotestorage  = nullptr;
    g_state.isteam_friends        = nullptr;
    g_state.isteam_apps_ver       = nullptr;
    g_state.isteam_rs_ver         = nullptr;
    g_state.isteam_friends_ver    = nullptr;
    g_state.cached_steam_id       = 0;
    g_state.subscribed_ids.clear();
    g_state.received_callbacks.clear();
    g_state.cv_callback.notify_all();
    g_state.fn_CreateInterface              = nullptr;
    g_state.fn_Steam_CreateGlobalUser       = nullptr;
    g_state.fn_Steam_BLoggedOn              = nullptr;
    g_state.fn_Steam_LogOff                 = nullptr;
    g_state.fn_Steam_BGetCallback           = nullptr;
    g_state.fn_Steam_FreeLastCallback       = nullptr;
    g_state.fn_Breakpad_SteamSetAppID       = nullptr;
    g_state.shutting_down = false;
    LOGI("nativeShutdown done");
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamBootstrap_nativePrepareApp(
        JNIEnv* env, jclass /*cls*/, jintArray jappIds) {
    // Phase 8b.6+: drive ISteamApps via the IClientEngine sub-interface to
    // warm libsteamclient.so's own PICS cache for the given appids. For
    // now we log and let the Rust wnsteam runtime's own prepareApp (Phase 4.5)
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
