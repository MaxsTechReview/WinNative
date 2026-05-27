
#include "wn_steam/cm_bridge.h"
#include "wn_steam/cm_client.h"

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <dirent.h>
#include <fstream>
#include <mutex>
#include <pthread.h>
#include <shared_mutex>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>
#include <vector>

#define WN_BRIDGE_TAG "wn-cm-bridge"
#define WN_BRIDGE_LOGI(...) __android_log_print(ANDROID_LOG_INFO, WN_BRIDGE_TAG, __VA_ARGS__)

namespace {

std::shared_mutex& bridge_mu() {
    static std::shared_mutex m;
    return m;
}

std::weak_ptr<wn_steam::CMClient>& active_slot() {
    static std::weak_ptr<wn_steam::CMClient> w;
    return w;
}

std::shared_ptr<wn_steam::CMClient> snapshot_active() {
    std::shared_lock<std::shared_mutex> lk(bridge_mu());
    return active_slot().lock();
}

}  // namespace

namespace wn_steam {

void wn_cm_bridge_set_active(std::shared_ptr<CMClient> client) {
    std::unique_lock<std::shared_mutex> lk(bridge_mu());
    active_slot() = client;
}

void wn_cm_bridge_clear_active() {
    std::unique_lock<std::shared_mutex> lk(bridge_mu());
    active_slot().reset();
}

}  // namespace wn_steam

extern "C" {

bool wn_cm_set_persona_state(int32_t persona_state) {
    if (persona_state < 0) return false;
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "set_persona_state(%d): no active CMClient — dropped", persona_state);
        return false;
    }
    client->set_persona_state(static_cast<uint32_t>(persona_state));
    WN_BRIDGE_LOGI("set_persona_state(%d): → live CMClient", persona_state);
    return true;
}

bool wn_cm_set_persona_name(const char* name, int32_t persona_state) {
    if (!name || !*name) return false;
    if (persona_state < 0) persona_state = 1;  // default Online — CM requires non-negative
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "set_persona_name(\"%s\"): no active CMClient — dropped", name);
        return false;
    }
    client->set_persona_name(std::string(name), static_cast<uint32_t>(persona_state));
    WN_BRIDGE_LOGI("set_persona_name(\"%s\", %d): → live CMClient", name, persona_state);
    return true;
}

bool wn_cm_request_user_info(uint64_t steam_id, int32_t flags) {
    if (steam_id == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "request_user_info(%llu): no active CMClient — dropped",
            (unsigned long long)steam_id);
        return false;
    }
    uint32_t f = (flags <= 0) ? 0x47u : static_cast<uint32_t>(flags);
    client->request_friend_personas({steam_id}, f);
    WN_BRIDGE_LOGI("request_user_info(%llu, flags=0x%x): → live CMClient",
                   (unsigned long long)steam_id, f);
    return true;
}

bool wn_cm_set_rich_presence(uint32_t app_id,
                              const char* const* keys,
                              const char* const* values,
                              size_t count) {
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "set_rich_presence(app=%u, %zu keys): no active CMClient", app_id, count);
        return false;
    }
    std::vector<wn_steam::pb::CPlayer_SetRichPresence_KV> kv;
    kv.reserve(count);
    for (size_t i = 0; i < count; ++i) {
        wn_steam::pb::CPlayer_SetRichPresence_KV entry;
        if (keys   && keys[i])   entry.key   = keys[i];
        if (values && values[i]) entry.value = values[i];
        if (entry.key.empty()) continue;  // empty key would corrupt the proto
        kv.push_back(std::move(entry));
    }
    client->set_rich_presence(app_id, kv);
    WN_BRIDGE_LOGI("set_rich_presence(app=%u, %zu keys): → live CMClient",
                   app_id, kv.size());
    return true;
}

bool wn_cm_store_user_stats(uint32_t app_id,
                             uint32_t crc_stats,
                             const uint32_t* stat_ids,
                             const uint32_t* stat_values,
                             size_t count) {
    if (app_id == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "store_user_stats(%u): no active CMClient", app_id);
        return false;
    }
    uint64_t self_sid = client->steam_id();
    if (self_sid == 0) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "store_user_stats(%u): self steam_id=0, not yet logged on", app_id);
        return false;
    }
    std::vector<std::pair<uint32_t, uint32_t>> stats;
    stats.reserve(count);
    if (stat_ids && stat_values) {
        for (size_t i = 0; i < count; ++i) {
            stats.emplace_back(stat_ids[i], stat_values[i]);
        }
    }
    client->store_user_stats(app_id, self_sid, crc_stats, stats);
    WN_BRIDGE_LOGI("store_user_stats(%u): %zu stat(s), crc=%u, sid=%llu",
                   app_id, stats.size(), crc_stats,
                   static_cast<unsigned long long>(self_sid));
    return true;
}

bool wn_cm_notify_games_played(uint32_t app_id) {
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "notify_games_played(%u): no active CMClient", app_id);
        return false;
    }
    wn_steam::pb::CMsgClientGamesPlayed msg;
    if (app_id != 0) {
        wn_steam::pb::GamePlayedEntry entry;
        entry.game_id = static_cast<uint64_t>(app_id);
        msg.games_played.push_back(std::move(entry));
    }
    msg.client_os_type = 0;  // Steam fills this for us; 0 is the default
    client->notify_games_played(msg);
    WN_BRIDGE_LOGI("notify_games_played(%u): %s",
                   app_id, app_id == 0 ? "cleared" : "broadcasting");
    return true;
}

bool wn_cm_bridge_inject_test_ownership_ticket(uint32_t app_id,
                                                 const uint8_t* bytes,
                                                 size_t len) {
    if (app_id == 0 || !bytes || len == 0) return false;
    auto client = snapshot_active();
    if (!client) return false;
    std::vector<uint8_t> v(bytes, bytes + len);
    client->tickets().store(app_id, 1 /*eresult OK*/, std::move(v));
    WN_BRIDGE_LOGI("inject_test_ownership_ticket(app=%u, %zu bytes): stored",
                   app_id, len);
    return true;
}

bool wn_cm_get_cached_app_ownership_ticket(uint32_t app_id,
                                            uint8_t* out_buf,
                                            size_t max_len,
                                            size_t* out_len) {
    if (app_id == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "get_cached_app_ownership_ticket(%u): no active CMClient", app_id);
        return false;
    }
    auto entry = client->tickets().get(app_id);
    if (!entry || entry->eresult != 1 /*OK*/ || entry->ticket.empty()) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "get_cached_app_ownership_ticket(%u): cache miss", app_id);
        if (out_len) *out_len = 0;
        return false;
    }
    size_t actual = entry->ticket.size();
    if (out_len) *out_len = actual;
    if (!out_buf || max_len == 0) return false;          // caller wanted size only
    if (actual > max_len) {
        return false;
    }
    std::memcpy(out_buf, entry->ticket.data(), actual);
    WN_BRIDGE_LOGI("get_cached_app_ownership_ticket(%u): %zu bytes copied",
                   app_id, actual);
    return true;
}

namespace {
std::atomic<WnCmPersonaObserverFn> g_persona_observer{nullptr};
}  // namespace

void wn_cm_bridge_register_persona_observer(WnCmPersonaObserverFn fn) {
    g_persona_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("persona_observer registered: %p", reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_dispatch_persona(const WnCmPersonaEvent* ev) {
    if (!ev) return;
    auto fn = g_persona_observer.load(std::memory_order_acquire);
    if (!fn) return;
    fn(ev);
}

namespace {
std::atomic<WnCmLogonStateObserverFn> g_logon_state_observer{nullptr};
}  // namespace

void wn_cm_bridge_register_logon_state_observer(WnCmLogonStateObserverFn fn) {
    g_logon_state_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("logon_state_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_dispatch_logon_state(bool logged_on) {
    auto fn = g_logon_state_observer.load(std::memory_order_acquire);
    if (!fn) return;
    fn(logged_on);
}

void wn_cm_bridge_inject_test_logon_state(bool logged_on) {
    WN_BRIDGE_LOGI("inject_test_logon_state(%s)", logged_on ? "true" : "false");
    wn_cm_bridge_dispatch_logon_state(logged_on);
}

namespace {
std::atomic<WnCmFriendsListObserverFn> g_friends_list_observer{nullptr};
}  // namespace

void wn_cm_bridge_register_friends_list_observer(WnCmFriendsListObserverFn fn) {
    g_friends_list_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("friends_list_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_dispatch_friends_list(const uint64_t* sids, size_t count) {
    auto fn = g_friends_list_observer.load(std::memory_order_acquire);
    if (!fn) return;
    fn(sids, count);
}

void wn_cm_bridge_inject_test_friends_list(const uint64_t* sids, size_t count) {
    WN_BRIDGE_LOGI("inject_test_friends_list(count=%zu)", count);
    wn_cm_bridge_dispatch_friends_list(sids, count);
}

namespace {
std::atomic<WnCmLicenseListObserverFn> g_license_list_observer{nullptr};
}  // namespace

void wn_cm_bridge_register_license_list_observer(WnCmLicenseListObserverFn fn) {
    g_license_list_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("license_list_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_dispatch_license_list(const WnCmLicenseEntry* licenses,
                                          size_t count) {
    auto fn = g_license_list_observer.load(std::memory_order_acquire);
    if (!fn) return;
    fn(licenses, count);
}

void wn_cm_bridge_inject_test_license_list(const WnCmLicenseEntry* licenses,
                                             size_t count) {
    WN_BRIDGE_LOGI("inject_test_license_list(count=%zu)", count);
    wn_cm_bridge_dispatch_license_list(licenses, count);
}

namespace {
std::atomic<WnCmAccountInfoObserverFn>    g_account_info_observer{nullptr};
std::atomic<WnCmServerRealTimeObserverFn> g_server_realtime_observer{nullptr};
}  // namespace

void wn_cm_bridge_register_account_info_observer(WnCmAccountInfoObserverFn fn) {
    g_account_info_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("account_info_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_dispatch_account_info(const WnCmAccountInfo* info) {
    auto fn = g_account_info_observer.load(std::memory_order_acquire);
    if (!fn || !info) return;
    fn(info);
}

void wn_cm_bridge_register_server_realtime_observer(WnCmServerRealTimeObserverFn fn) {
    g_server_realtime_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("server_realtime_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_dispatch_server_realtime(uint32_t server_realtime) {
    auto fn = g_server_realtime_observer.load(std::memory_order_acquire);
    if (!fn || server_realtime == 0) return;
    fn(server_realtime);
}

void wn_cm_bridge_inject_test_account_info(const WnCmAccountInfo* info) {
    if (!info) return;
    WN_BRIDGE_LOGI("inject_test_account_info(persona='%.*s' ip='%.*s' "
                   "2FA=%d phone_v=%d phone_id=%d phone_nv=%d)",
                   static_cast<int>(info->persona_name_len),
                   info->persona_name ? info->persona_name : "",
                   static_cast<int>(info->ip_country_len),
                   info->ip_country ? info->ip_country : "",
                   info->two_factor_enabled, info->phone_verified,
                   info->phone_identifying, info->phone_requires_verification);
    wn_cm_bridge_dispatch_account_info(info);
}

bool wn_cm_request_user_info_bulk(const uint64_t* sids, size_t count, int32_t flags) {
    if (!sids || count == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        __android_log_print(ANDROID_LOG_DEBUG, WN_BRIDGE_TAG,
            "request_user_info_bulk(count=%zu): no active CMClient — dropped",
            count);
        return false;
    }
    std::vector<uint64_t> v;
    v.reserve(count);
    for (size_t i = 0; i < count; ++i) {
        if (sids[i] != 0) v.push_back(sids[i]);
    }
    if (v.empty()) return false;
    uint32_t f = (flags <= 0) ? 0x47u : static_cast<uint32_t>(flags);
    client->request_friend_personas(v, f);
    WN_BRIDGE_LOGI("request_user_info_bulk(count=%zu, flags=0x%x): → live CMClient",
                   v.size(), f);
    return true;
}


static std::atomic<WnCmLobbyDataObserverFn> g_lobby_data_observer{nullptr};

void wn_cm_bridge_register_lobby_data_observer(WnCmLobbyDataObserverFn fn) {
    g_lobby_data_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("lobby_data_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

static std::string wn_state_dir() {
    const char* d = std::getenv("WN_STATE_DIR");
    if (d && *d) return std::string(d);
    return "/tmp";
}

static void write_lobby_list_to_file(uint32_t app_id,
                                     int32_t eresult,
                                     const WnCmLobbyEntry* entries,
                                     size_t count) {
    std::string dir = wn_state_dir();
    std::string final_path = dir + "/wn_lobby_" + std::to_string(app_id) + ".txt";
    std::string tmp_path   = final_path + ".tmp";
    {
        std::ofstream f(tmp_path, std::ios::trunc);
        if (!f) return;  // dir may not exist; that's ok, just skip.
        f << "app_id " << app_id << "\n";
        f << "fetched " << static_cast<long long>(std::time(nullptr)) << "\n";
        f << "eresult " << eresult << "\n";
        for (size_t i = 0; i < count; ++i) {
            f << "lobby " << entries[i].steam_id
              << " " << entries[i].max_members << "\n";
        }
    }
    std::rename(tmp_path.c_str(), final_path.c_str());
    WN_BRIDGE_LOGI("lobby_get_list(%u): wrote %s lobbies=%zu eresult=%d",
                   app_id, final_path.c_str(), count, eresult);
}

typedef void (*WnbDispatchCallbackFn)(int iCallback, const void* data, size_t data_size);
typedef void (*WnbDispatchCallResultFn)(uint64_t hAPICall, int io_failure,
                                        const void* data, size_t data_size);
static std::atomic<WnbDispatchCallbackFn>   g_wnb_dispatch_cb{nullptr};
static std::atomic<WnbDispatchCallResultFn> g_wnb_dispatch_cr{nullptr};

static void load_wnb_dispatch_pointers(void) {
    if (g_wnb_dispatch_cb.load(std::memory_order_acquire) != nullptr) return;
    std::string path = wn_state_dir() + "/wnb_ptrs.txt";
    std::ifstream f(path);
    if (!f) return;
    std::string line;
    while (std::getline(f, line)) {
        std::istringstream ls(line);
        std::string tag;
        unsigned long long addr = 0;
        if (!(ls >> tag >> addr)) continue;
        if (tag == "dispatch_callback") {
            g_wnb_dispatch_cb.store(
                reinterpret_cast<WnbDispatchCallbackFn>(addr),
                std::memory_order_release);
        } else if (tag == "dispatch_call_result") {
            g_wnb_dispatch_cr.store(
                reinterpret_cast<WnbDispatchCallResultFn>(addr),
                std::memory_order_release);
        }
    }
}

static bool parse_lobby_state_file(const std::string& path,
                                   int32_t* out_eresult,
                                   std::vector<WnCmLobbyEntry>* out_lobbies) {
    std::ifstream f(path);
    if (!f) return false;
    out_lobbies->clear();
    std::string line;
    while (std::getline(f, line)) {
        std::istringstream ls(line);
        std::string tag;
        if (!(ls >> tag)) continue;
        if (tag == "eresult") {
            ls >> *out_eresult;
        } else if (tag == "lobby") {
            uint64_t sid = 0;
            int32_t max_members = 0;
            ls >> sid >> max_members;
            if (sid != 0) {
                WnCmLobbyEntry e{};
                e.steam_id = sid;
                e.max_members = max_members;
                out_lobbies->push_back(e);
            }
        }
    }
    return true;
}

static void write_lobby_request_file(uint32_t app_id) {
    std::string dir = wn_state_dir();
    std::string final_path = dir + "/wn_lobby_req_" + std::to_string(app_id) + ".txt";
    std::string tmp_path   = final_path + ".tmp";
    {
        std::ofstream f(tmp_path, std::ios::trunc);
        if (!f) return;
        f << "app_id " << app_id << "\n";
        f << "requested " << static_cast<long long>(std::time(nullptr)) << "\n";
    }
    std::rename(tmp_path.c_str(), final_path.c_str());
}

static void dispatch_lobby_list_to_wnb(uint64_t hCall,
                                       int32_t eresult,
                                       size_t lobby_count) {
    load_wnb_dispatch_pointers();
    auto disp = g_wnb_dispatch_cr.load(std::memory_order_acquire);
    if (disp == nullptr) return;
    struct LobbyMatchList_t {
        uint32_t m_nLobbiesMatching;
    } lml{};
    lml.m_nLobbiesMatching = static_cast<uint32_t>(lobby_count);
    int io_fail = (eresult < 0) ? 1 : 0;
    disp(hCall, io_fail, &lml, sizeof(lml));
    WN_BRIDGE_LOGI("dispatch_lobby_list_to_wnb: hCall=0x%llx count=%zu io_fail=%d",
                   (unsigned long long)hCall, lobby_count, io_fail);
}

static bool try_lobby_list_from_file(uint64_t hCall,
                                      uint32_t app_id,
                                      WnCmLobbyListCb cb,
                                      const std::atomic<bool>* keep_running = nullptr) {
    std::string dir = wn_state_dir();
    std::string path = dir + "/wn_lobby_" + std::to_string(app_id) + ".txt";
    auto should_stop = [keep_running]() {
        return keep_running && !keep_running->load(std::memory_order_acquire);
    };

    auto try_parse_and_emit = [&]() -> bool {
        int32_t eresult = 0;
        std::vector<WnCmLobbyEntry> lobbies;
        if (!parse_lobby_state_file(path, &eresult, &lobbies)) return false;
        WN_BRIDGE_LOGI("lobby_get_list(%u): file-fallback %s lobbies=%zu eresult=%d",
                       app_id, path.c_str(), lobbies.size(), eresult);
        cb(hCall, eresult,
           lobbies.empty() ? nullptr : lobbies.data(),
           lobbies.size());
        dispatch_lobby_list_to_wnb(hCall, eresult, lobbies.size());
        return true;
    };

    struct stat st{};
    if (::stat(path.c_str(), &st) == 0) {
        time_t now = std::time(nullptr);
        if (now - st.st_mtime < 30) {
            return try_parse_and_emit();
        }
    }

    if (should_stop()) return false;
    write_lobby_request_file(app_id);
    constexpr int kMaxAttempts = 30;  // 30 * 100ms = 3s
    for (int i = 0; i < kMaxAttempts; ++i) {
        if (should_stop()) {
            WN_BRIDGE_LOGI("lobby_get_list(%u): file-fallback cancelled",
                           app_id);
            return false;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (should_stop()) {
            WN_BRIDGE_LOGI("lobby_get_list(%u): file-fallback cancelled",
                           app_id);
            return false;
        }
        if (::stat(path.c_str(), &st) == 0
                && std::time(nullptr) - st.st_mtime < 30) {
            return try_parse_and_emit();
        }
    }
    WN_BRIDGE_LOGI("lobby_get_list(%u): request-file timeout — synthetic empty",
                   app_id);
    cb(hCall, /*eresult=*/1, nullptr, 0);  // success-but-empty
    dispatch_lobby_list_to_wnb(hCall, /*eresult=*/1, /*lobby_count=*/0);
    return true;
}

static void state_sync_poller_loop();
static bool wn_cm_lobby_get_list_internal(uint64_t hCall,
                                          uint32_t app_id,
                                          int32_t num_lobbies_requested,
                                          const char* const* filter_keys,
                                          const char* const* filter_values,
                                          const int32_t* filter_comparisons,
                                          const int32_t* filter_types,
                                          size_t filter_count,
                                          WnCmLobbyListCb cb,
                                          const std::atomic<bool>* keep_running);

struct StateSyncPoller {
    ~StateSyncPoller() { stop(); }

    void start() {
        std::thread to_join;
        {
            std::lock_guard<std::mutex> lk(mu);
            if (running.exchange(true, std::memory_order_acq_rel)) return;
            if (worker.joinable()) to_join = std::move(worker);
        }
        if (to_join.joinable()) to_join.join();
        {
            std::lock_guard<std::mutex> lk(mu);
            if (running.load(std::memory_order_acquire)) {
                worker = std::thread(state_sync_poller_loop);
            }
        }
    }

    void stop() {
        std::thread to_join;
        {
            std::lock_guard<std::mutex> lk(mu);
            const bool was_running = running.exchange(false, std::memory_order_acq_rel);
            if (!was_running && !worker.joinable()) return;
            if (worker.joinable()) to_join = std::move(worker);
        }
        cv.notify_all();
        if (to_join.joinable()) to_join.join();
    }

    std::atomic<bool>        running{false};
    std::mutex               mu;
    std::condition_variable  cv;
    std::thread              worker;
};

static StateSyncPoller& state_sync_poller() {
    static StateSyncPoller poller;
    return poller;
}

static void state_sync_poller_loop() {
    pthread_setname_np(pthread_self(), "wn-state-sync");
    WN_BRIDGE_LOGI("state-sync poller started");
    StateSyncPoller& poller = state_sync_poller();
    while (poller.running.load(std::memory_order_acquire)) {
        {
            std::unique_lock<std::mutex> lk(poller.mu);
            if (poller.cv.wait_for(lk, std::chrono::seconds(1), [&poller]() {
                    return !poller.running.load(std::memory_order_acquire);
                })) {
                break;
            }
        }
        if (!poller.running.load(std::memory_order_acquire)) break;
        std::string dir = wn_state_dir();
        DIR* d = ::opendir(dir.c_str());
        if (!d) continue;
        struct dirent* ent;
        while ((ent = ::readdir(d)) != nullptr) {
            std::string name = ent->d_name;
            if (name.rfind("wn_lobby_req_", 0) != 0) continue;
            if (name.size() < 20 || name.substr(name.size() - 4) != ".txt") continue;
            std::string app_str = name.substr(13, name.size() - 13 - 4);
            uint32_t app_id = 0;
            try { app_id = static_cast<uint32_t>(std::stoul(app_str)); }
            catch (...) { continue; }
            if (app_id == 0) continue;
            std::string req_path = dir + "/" + name;
            int unlink_rc = ::unlink(req_path.c_str());
            int unlink_errno = errno;
            WN_BRIDGE_LOGI("state-sync: fulfilling request for app=%u unlink=%d/%d",
                           app_id, unlink_rc, unlink_rc == 0 ? 0 : unlink_errno);
            bool dispatched = wn_cm_lobby_get_list_internal(
                /*hCall=*/0, app_id, /*num_lobbies_requested=*/0,
                nullptr, nullptr, nullptr, nullptr, 0,
                [](uint64_t, int32_t, const WnCmLobbyEntry*, size_t) {},
                &poller.running);
            if (!dispatched) {
                WN_BRIDGE_LOGI("state-sync: dispatch FAILED for app=%u (no active CMClient?)",
                               app_id);
            }
        }
        ::closedir(d);
    }
    WN_BRIDGE_LOGI("state-sync poller stopped");
}

void wn_cm_bridge_start_state_sync_poller(void) {
    state_sync_poller().start();
}

void wn_cm_bridge_stop_state_sync_poller(void) {
    state_sync_poller().stop();
}

static bool wn_cm_lobby_get_list_internal(uint64_t hCall,
                                          uint32_t app_id,
                                          int32_t num_lobbies_requested,
                                          const char* const* filter_keys,
                                          const char* const* filter_values,
                                          const int32_t* filter_comparisons,
                                          const int32_t* filter_types,
                                          size_t filter_count,
                                          WnCmLobbyListCb cb,
                                          const std::atomic<bool>* keep_running) {
    if (app_id == 0 || !cb) return false;
    auto client = snapshot_active();
    if (!client) {
        if (try_lobby_list_from_file(hCall, app_id, cb, keep_running)) {
            return true;
        }
        WN_BRIDGE_LOGI("lobby_get_list(%u): no active CMClient + no state file",
                       app_id);
        return false;
    }

    std::vector<wn_steam::pb::CMsgClientMMSGetLobbyListFilter> filters;
    filters.reserve(filter_count);
    for (size_t i = 0; i < filter_count; ++i) {
        wn_steam::pb::CMsgClientMMSGetLobbyListFilter f;
        f.key         = (filter_keys   && filter_keys[i])   ? filter_keys[i]   : "";
        f.value       = (filter_values && filter_values[i]) ? filter_values[i] : "";
        f.comparision = filter_comparisons ? filter_comparisons[i] : 0;
        f.filter_type = filter_types       ? filter_types[i]       : 0;
        filters.push_back(std::move(f));
    }

    client->set_lobby_data_observer([](const wn_steam::pb::CMsgClientMMSLobbyData& msg) {
        auto fn = g_lobby_data_observer.load(std::memory_order_acquire);
        if (!fn) return;
        std::vector<WnCmLobbyMember> members;
        members.reserve(msg.members.size());
        for (const auto& m : msg.members) {
            WnCmLobbyMember bm{};
            bm.steam_id      = m.steam_id;
            bm.persona_name  = m.persona_name.c_str();
            bm.metadata_bytes= m.metadata.data();
            bm.metadata_len  = m.metadata.size();
            members.push_back(bm);
        }
        WnCmLobbyData out{};
        out.steam_id_lobby = msg.steam_id_lobby;
        out.steam_id_owner = msg.steam_id_owner;
        out.app_id         = msg.app_id;
        out.max_members    = msg.max_members;
        out.num_members    = msg.num_members;
        out.lobby_type     = msg.lobby_type;
        out.lobby_flags    = msg.lobby_flags;
        out.metadata_bytes = msg.metadata.data();
        out.metadata_len   = msg.metadata.size();
        out.members        = members.data();
        out.member_count   = members.size();
        fn(&out);
    });

    client->lobby_get_list(
        app_id,
        std::move(filters),
        num_lobbies_requested,
        [hCall, app_id, cb](std::optional<wn_steam::pb::CMsgClientMMSGetLobbyListResponse> resp) {
            if (!resp) {
                write_lobby_list_to_file(app_id, -1, nullptr, 0);
                cb(hCall, /*synthetic-failure*/ -1, nullptr, 0);
                return;
            }
            std::vector<WnCmLobbyEntry> entries;
            entries.reserve(resp->lobbies.size());
            for (const auto& L : resp->lobbies) {
                WnCmLobbyEntry e{};
                e.steam_id    = L.steam_id;
                e.max_members = L.max_members;
                e.num_members = L.num_members;
                e.lobby_type  = L.lobby_type;
                e.lobby_flags = L.lobby_flags;
                e.ping_ms     = L.ping;
                e.weight      = L.weight;
                e.distance    = L.distance;
                entries.push_back(e);
            }
            write_lobby_list_to_file(app_id, resp->eresult,
                                     entries.empty() ? nullptr : entries.data(),
                                     entries.size());
            cb(hCall, resp->eresult,
               entries.empty() ? nullptr : entries.data(),
               entries.size());
        },
        std::chrono::seconds{30});

    WN_BRIDGE_LOGI("lobby_get_list(app=%u, num=%d, filters=%zu): dispatched",
                   app_id, num_lobbies_requested, filter_count);
    return true;
}

bool wn_cm_lobby_get_list(uint64_t hCall,
                          uint32_t app_id,
                          int32_t num_lobbies_requested,
                          const char* const* filter_keys,
                          const char* const* filter_values,
                          const int32_t* filter_comparisons,
                          const int32_t* filter_types,
                          size_t filter_count,
                          WnCmLobbyListCb cb) {
    return wn_cm_lobby_get_list_internal(
        hCall, app_id, num_lobbies_requested,
        filter_keys, filter_values, filter_comparisons, filter_types,
        filter_count, cb, nullptr);
}

bool wn_cm_lobby_create(uint64_t hCall,
                        uint32_t app_id,
                        int32_t lobby_type,
                        int32_t max_members,
                        WnCmLobbyCreatedCb cb) {
    if (!cb || app_id == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_create(%u): no active CMClient", app_id);
        return false;
    }
    uint64_t self_sid = client->steam_id();
    client->lobby_create(
        app_id, lobby_type, max_members,
        [hCall, cb, self_sid, app_id, lobby_type, max_members](
                std::optional<wn_steam::pb::CMsgClientMMSCreateLobbyResponse> resp) {
            if (!resp) {
                cb(hCall, /*synthetic-failure*/ -1, 0);
                return;
            }
            if (resp->eresult == 1 && resp->steam_id_lobby != 0) {
                auto fn = g_lobby_data_observer.load(std::memory_order_acquire);
                if (fn) {
                    WnCmLobbyMember self{};
                    self.steam_id = self_sid;
                    self.persona_name = "";
                    self.metadata_bytes = nullptr;
                    self.metadata_len = 0;
                    WnCmLobbyData out{};
                    out.steam_id_lobby = resp->steam_id_lobby;
                    out.steam_id_owner = self_sid;
                    out.app_id         = app_id;
                    out.max_members    = max_members > 0 ? max_members : 4;
                    out.num_members    = 1;
                    out.lobby_type     = lobby_type;
                    out.lobby_flags    = 0;
                    out.members        = &self;
                    out.member_count   = 1;
                    fn(&out);
                }
            }
            cb(hCall, resp->eresult, resp->steam_id_lobby);
        },
        std::chrono::seconds{30});
    WN_BRIDGE_LOGI("lobby_create(app=%u, type=%d, max=%d): dispatched",
                   app_id, lobby_type, max_members);
    return true;
}

bool wn_cm_lobby_join(uint64_t hCall,
                      uint32_t app_id,
                      uint64_t lobby_sid,
                      WnCmLobbyJoinedCb cb) {
    if (!cb || lobby_sid == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_join(0x%llx): no active CMClient",
                       static_cast<unsigned long long>(lobby_sid));
        return false;
    }
    client->lobby_join(
        app_id, lobby_sid,
        [hCall, cb, lobby_sid](
                std::optional<wn_steam::pb::CMsgClientMMSJoinLobbyResponse> resp) {
            if (!resp) {
                cb(hCall, /*synthetic-failure*/ -1, lobby_sid);
                return;
            }
            if (resp->chat_room_enter_response == 1 /*Success*/) {
                auto fn = g_lobby_data_observer.load(std::memory_order_acquire);
                if (fn) {
                    std::vector<WnCmLobbyMember> members;
                    members.reserve(resp->members.size());
                    for (const auto& m : resp->members) {
                        WnCmLobbyMember bm{};
                        bm.steam_id       = m.steam_id;
                        bm.persona_name   = m.persona_name.c_str();
                        bm.metadata_bytes = m.metadata.data();
                        bm.metadata_len   = m.metadata.size();
                        members.push_back(bm);
                    }
                    WnCmLobbyData out{};
                    out.steam_id_lobby = resp->steam_id_lobby;
                    out.steam_id_owner = resp->steam_id_owner;
                    out.app_id         = resp->app_id;
                    out.max_members    = resp->max_members;
                    out.num_members    = static_cast<int32_t>(members.size());
                    out.lobby_type     = resp->lobby_type;
                    out.lobby_flags    = resp->lobby_flags;
                    out.metadata_bytes = resp->metadata.data();
                    out.metadata_len   = resp->metadata.size();
                    out.members        = members.data();
                    out.member_count   = members.size();
                    fn(&out);
                }
            }
            cb(hCall, resp->chat_room_enter_response, resp->steam_id_lobby);
        },
        std::chrono::seconds{30});
    WN_BRIDGE_LOGI("lobby_join(app=%u, lobby=0x%llx): dispatched",
                   app_id, static_cast<unsigned long long>(lobby_sid));
    return true;
}

bool wn_cm_lobby_leave(uint32_t app_id, uint64_t lobby_sid) {
    if (lobby_sid == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_leave(0x%llx): no active CMClient",
                       static_cast<unsigned long long>(lobby_sid));
        return false;
    }
    client->lobby_leave(app_id, lobby_sid);
    return true;
}

static std::atomic<WnCmLobbyChatMsgObserverFn>     g_lobby_chat_msg_observer{nullptr};
static std::atomic<WnCmLobbyMembershipObserverFn>  g_lobby_membership_observer{nullptr};

void wn_cm_bridge_register_lobby_chat_msg_observer(WnCmLobbyChatMsgObserverFn fn) {
    g_lobby_chat_msg_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("lobby_chat_msg_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

void wn_cm_bridge_register_lobby_membership_observer(WnCmLobbyMembershipObserverFn fn) {
    g_lobby_membership_observer.store(fn, std::memory_order_release);
    WN_BRIDGE_LOGI("lobby_membership_observer registered: %p",
                   reinterpret_cast<void*>(fn));
}

bool wn_cm_lobby_send_chat(uint32_t app_id, uint64_t lobby_sid,
                           const uint8_t* data, size_t n) {
    if (lobby_sid == 0 || !data || n == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_send_chat(0x%llx): no active CMClient",
                       static_cast<unsigned long long>(lobby_sid));
        return false;
    }
    static std::once_flag once;
    std::call_once(once, [&]() {
        auto client2 = client;
        client2->set_lobby_chat_msg_observer(
            [](const wn_steam::pb::CMsgClientMMSLobbyChatMsg& m) {
                auto fn = g_lobby_chat_msg_observer.load(std::memory_order_acquire);
                if (!fn) return;
                fn(m.steam_id_lobby, m.steam_id_sender,
                   m.lobby_message.data(), m.lobby_message.size());
            });
        client2->set_lobby_membership_observer(
            [](bool joined,
               const wn_steam::pb::CMsgClientMMSUserJoinedOrLeftLobby& m) {
                auto fn = g_lobby_membership_observer.load(std::memory_order_acquire);
                if (!fn) return;
                fn(joined ? 1 : 0, m.steam_id_lobby, m.steam_id_user,
                   m.persona_name.c_str());
            });
    });
    std::vector<uint8_t> blob(data, data + n);
    client->lobby_send_chat(app_id, lobby_sid, std::move(blob));
    return true;
}

bool wn_cm_lobby_set_data(uint64_t hCall,
                          uint32_t app_id,
                          uint64_t lobby_sid,
                          uint64_t steam_id_member,
                          const uint8_t* metadata, size_t metadata_len,
                          int32_t max_members, int32_t lobby_type,
                          int32_t lobby_flags,
                          WnCmLobbySetDataCb cb) {
    if (lobby_sid == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_set_data(0x%llx): no active CMClient",
                       static_cast<unsigned long long>(lobby_sid));
        return false;
    }
    std::vector<uint8_t> blob;
    if (metadata && metadata_len > 0) {
        blob.assign(metadata, metadata + metadata_len);
    }
    client->lobby_set_data(
        app_id, lobby_sid, steam_id_member,
        std::move(blob), max_members, lobby_type, lobby_flags,
        [hCall, cb](std::optional<wn_steam::pb::CMsgClientMMSSetLobbyDataResponse> resp) {
            if (!cb) return;
            if (!resp) { cb(hCall, /*synthetic*/ -1); return; }
            cb(hCall, resp->eresult);
        },
        std::chrono::seconds{30});
    WN_BRIDGE_LOGI("lobby_set_data(app=%u, lobby=0x%llx, member=0x%llx, meta=%zuB)",
                   app_id, static_cast<unsigned long long>(lobby_sid),
                   static_cast<unsigned long long>(steam_id_member), metadata_len);
    return true;
}

bool wn_cm_lobby_set_owner(uint64_t hCall,
                           uint32_t app_id,
                           uint64_t lobby_sid,
                           uint64_t new_owner_sid,
                           WnCmLobbySetOwnerCb cb) {
    if (lobby_sid == 0 || new_owner_sid == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_set_owner(0x%llx): no active CMClient",
                       static_cast<unsigned long long>(lobby_sid));
        return false;
    }
    client->lobby_set_owner(
        app_id, lobby_sid, new_owner_sid,
        [hCall, cb](std::optional<wn_steam::pb::CMsgClientMMSSetLobbyOwnerResponse> resp) {
            if (!cb) return;
            if (!resp) { cb(hCall, /*synthetic*/ -1); return; }
            cb(hCall, resp->eresult);
        },
        std::chrono::seconds{30});
    WN_BRIDGE_LOGI("lobby_set_owner(app=%u, lobby=0x%llx, new_owner=0x%llx)",
                   app_id, static_cast<unsigned long long>(lobby_sid),
                   static_cast<unsigned long long>(new_owner_sid));
    return true;
}

bool wn_cm_lobby_invite_user(uint32_t app_id,
                             uint64_t lobby_sid,
                             uint64_t invitee_sid) {
    if (lobby_sid == 0 || invitee_sid == 0) return false;
    auto client = snapshot_active();
    if (!client) {
        WN_BRIDGE_LOGI("lobby_invite_user(0x%llx): no active CMClient",
                       static_cast<unsigned long long>(lobby_sid));
        return false;
    }
    client->lobby_invite_user(app_id, lobby_sid, invitee_sid);
    WN_BRIDGE_LOGI("lobby_invite_user(app=%u, lobby=0x%llx, invitee=0x%llx)",
                   app_id, static_cast<unsigned long long>(lobby_sid),
                   static_cast<unsigned long long>(invitee_sid));
    return true;
}

}  // extern "C"
