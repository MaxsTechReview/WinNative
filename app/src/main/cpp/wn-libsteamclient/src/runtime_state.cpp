#include "wn_libsteamclient/runtime_state.h"
#include "wn_libsteamclient/callbacks.h"
#include "wn_libsteamclient/tcp_services.h"

#include <android/log.h>
#include <cstdlib>
#include <cstring>

namespace wn_libsteamclient {

namespace {

void seed_state_from_env_once() {
    static std::once_flag flag;
    std::call_once(flag, []() {
        const char* env_sid = std::getenv("STEAMID");
        const char* env_app = std::getenv("SteamAppId");
        const char* env_usr = std::getenv("SteamUser");
        uint64_t sid = 0;
        if (env_sid && *env_sid) {
            sid = std::strtoull(env_sid, nullptr, 10);
        }
        uint32_t app = 0;
        if (env_app && *env_app) {
            app = static_cast<uint32_t>(std::strtoul(env_app, nullptr, 10));
        }
        if (sid != 0) {
            pushed().steam_id.store(sid);
            state().user.store(1);
            state().logged_on.store(true);
            state().connected.store(true);
            __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
                "guest seed: STEAMID=%llu logged_on=true",
                static_cast<unsigned long long>(sid));
        }
        if (app != 0) {
            pushed().app_id.store(app);
        }
        if (env_usr && *env_usr) {
            std::lock_guard<std::mutex> lk(state_mutex());
            if (pushed().persona_name.empty()) {
                pushed().persona_name = env_usr;
            }
        }
    });
}

__attribute__((constructor))
static void wn_libsteamclient_so_loaded() {
    seed_state_from_env_once();
}

}  // namespace

namespace {
State        g_state_singleton;
PushedState  g_pushed_singleton;
std::mutex   g_state_mutex_singleton;
}  // namespace

State&       state()       { return g_state_singleton; }
std::mutex&  state_mutex() { return g_state_mutex_singleton; }
PushedState& pushed()      { return g_pushed_singleton; }

HSteamPipe alloc_pipe() {
    start_tcp_services();
    seed_state_from_env_once();
    std::lock_guard<std::mutex> lk(state_mutex());
    auto& s = state();
    HSteamPipe cur = s.pipe.load();
    if (cur != 0) return 0;  // already allocated; caller can read s.pipe
    s.pipe.store(1);
    return 1;
}

bool release_pipe(HSteamPipe pipe) {
    set_logged_on(false);
    std::lock_guard<std::mutex> lk(state_mutex());
    auto& s = state();
    if (pipe == 0 || s.pipe.load() != pipe) return false;
    s.pipe.store(0);
    s.user.store(0);
    return true;
}

HSteamUser alloc_global_user(HSteamPipe pipe) {
    std::lock_guard<std::mutex> lk(state_mutex());
    auto& s = state();
    if (pipe == 0 || s.pipe.load() != pipe) return 0;
    HSteamUser cur = s.user.load();
    if (cur != 0) return cur;  // idempotent: same global user across calls
    s.user.store(1);
    return 1;
}

void release_user(HSteamPipe pipe, HSteamUser user) {
    std::lock_guard<std::mutex> lk(state_mutex());
    auto& s = state();
    if (pipe == 0 || user == 0) return;
    if (s.pipe.load() != pipe || s.user.load() != user) return;
    s.user.store(0);
    s.logged_on.store(false);
}

void push_callback(int user, int id, const void* data, size_t n) {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.callback_mu);
    CallbackMsg m;
    m.user = user;
    m.id   = id;
    if (data && n > 0) {
        m.body.assign(static_cast<const uint8_t*>(data),
                      static_cast<const uint8_t*>(data) + n);
    }
    s.callback_queue.push_back(std::move(m));
}

uint64_t alloc_api_call_handle() {
    auto& s = state();
    std::lock_guard<std::mutex> lk(s.call_results_mu);
    uint64_t h = s.next_api_call_handle++;
    if (s.next_api_call_handle == 0) s.next_api_call_handle = 1;
    return h;
}

void push_call_result(uint64_t h_call, int callback_id,
                      const void* data, size_t n, bool io_failure) {
    if (h_call == 0) return;
    auto& s = state();
    CallResultMsg m;
    m.h_call      = h_call;
    m.callback_id = callback_id;
    m.io_failure  = io_failure;
    if (data && n > 0) {
        m.body.assign(static_cast<const uint8_t*>(data),
                      static_cast<const uint8_t*>(data) + n);
    }
    {
        std::lock_guard<std::mutex> lk(s.call_results_mu);
        s.call_results_pending[h_call] = std::move(m);
    }
    callbacks::SteamAPICallCompleted ev{};
    ev.m_hAsyncCall = h_call;
    ev.m_iCallback  = callback_id;
    ev.m_cubParam   = static_cast<uint32_t>(n);
    push_callback(s.user.load(),
                  callbacks::kSteamAPICallCompleted,
                  &ev, sizeof(ev));
}

void set_logged_on(bool logged_on, int eresult_on_disconnect) {
    auto& s = state();
    bool prev = s.logged_on.exchange(logged_on);
    s.connected.store(logged_on);
    if (prev == logged_on) return;  // idempotent — no transition
    int h_user = s.user.load();
    if (logged_on) {
        push_callback(h_user, callbacks::kSteamServersConnected, nullptr, 0);
    } else {
        callbacks::SteamServersDisconnected payload{};
        payload.m_eResult = eresult_on_disconnect;
        push_callback(h_user, callbacks::kSteamServersDisconnected,
                      &payload, sizeof(payload));
    }
}

}  // namespace wn_libsteamclient
