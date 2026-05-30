
#include "wn_libsteamclient/runtime_state.h"
#include "wn_libsteamclient/callbacks.h"
#include "wn_libsteamclient/callback_registry.h"
#include "wn_libsteamclient/tcp_services.h"
#include "wn_steam/cm_bridge.h"

#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace cb = wn_libsteamclient::callbacks;
namespace lsc = wn_libsteamclient;

namespace {
void emit_persona_state_change(uint64_t steam_id, int32_t flags) {
    if (steam_id == 0) return;
    cb::PersonaStateChange payload{};
    payload.m_ulSteamID    = steam_id;
    payload.m_nChangeFlags = flags;
    lsc::push_callback(lsc::state().user.load(),
                       cb::kPersonaStateChange,
                       &payload, sizeof(payload));
}

void on_persona_event(const WnCmPersonaEvent* ev) {
    if (!ev || ev->sid == 0) return;
    int32_t flags = 0;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        const bool is_self = (ev->sid == p.steam_id.load());
        if (ev->name && ev->name[0]) {
            if (is_self) {
                if (p.persona_name != ev->name) {
                    p.persona_name = ev->name;
                    flags |= cb::kPersonaChangeName;
                }
            } else {
                std::string& slot = p.friend_persona_names[ev->sid];
                if (slot != ev->name) {
                    slot = ev->name;
                    flags |= cb::kPersonaChangeName;
                }
            }
        }
        if (ev->persona_state != UINT32_MAX) {
            uint32_t prev;
            if (is_self) {
                prev = static_cast<uint32_t>(p.persona_state.load());
            } else {
                auto it = p.friend_persona_states.find(ev->sid);
                prev = (it == p.friend_persona_states.end()) ? 0 : it->second;
            }
            if (prev != ev->persona_state) {
                if (is_self) {
                    p.persona_state.store(static_cast<int>(ev->persona_state));
                } else {
                    p.friend_persona_states[ev->sid] = ev->persona_state;
                }
                flags |= cb::kPersonaChangeStatus;
                if (prev == 0 && ev->persona_state != 0) {
                    flags |= cb::kPersonaChangeComeOnline;
                }
                if (prev != 0 && ev->persona_state == 0) {
                    flags |= cb::kPersonaChangeGoneOffline;
                }
            }
        }
        {
            uint32_t& slot = p.friend_game_played_app[ev->sid];
            if (slot != ev->game_played_app) {
                slot = ev->game_played_app;
                flags |= cb::kPersonaChangeGamePlayed;
            }
        }
        if (ev->avatar_hash && ev->avatar_hash_len > 0) {
            std::vector<uint8_t> hash(
                ev->avatar_hash, ev->avatar_hash + ev->avatar_hash_len);
            auto& slot = p.friend_avatar_hashes[ev->sid];
            if (slot != hash) {
                slot = std::move(hash);
                flags |= cb::kPersonaChangeAvatar;
            }
        }
        if (ev->rp_pairs && ev->rp_count > 0) {
            std::vector<std::pair<std::string, std::string>> fresh;
            fresh.reserve(ev->rp_count);
            for (size_t i = 0; i < ev->rp_count; ++i) {
                const auto& kv = ev->rp_pairs[i];
                fresh.emplace_back(
                    kv.key   ? kv.key   : "",
                    kv.value ? kv.value : "");
            }
            auto& slot = p.rich_presence[ev->sid];
            if (slot != fresh) {
                slot = std::move(fresh);
            }
        }
    }
    if (flags != 0) emit_persona_state_change(ev->sid, flags);
    if (ev->rp_pairs && ev->rp_count > 0) {
        cb::FriendRichPresenceUpdate rp{};
        rp.m_steamIDFriend = ev->sid;
        rp.m_nAppID        = lsc::pushed().app_id.load();
        lsc::push_callback(lsc::state().user.load(),
                           cb::kFriendRichPresenceUpdate,
                           &rp, sizeof(rp));
    }
}

__attribute__((constructor))
void register_persona_observer() {
    wn_cm_bridge_register_persona_observer(&on_persona_event);
}

void on_logon_state_event(bool logged_on) {
    lsc::set_logged_on(logged_on);
}

__attribute__((constructor))
void register_logon_state_observer() {
    wn_cm_bridge_register_logon_state_observer(&on_logon_state_event);
}

void on_friends_list_event(const uint64_t* sids, size_t count) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.friends.clear();
    if (sids && count > 0) {
        p.friends.reserve(count);
        for (size_t i = 0; i < count; ++i) {
            if (sids[i] != 0) p.friends.push_back(sids[i]);
        }
    }
    __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
        "friends-list observer: %zu mutual friend(s) mirrored", p.friends.size());
}

__attribute__((constructor))
void register_friends_list_observer() {
    wn_cm_bridge_register_friends_list_observer(&on_friends_list_event);
}

void on_license_list_event(const WnCmLicenseEntry* licenses, size_t count) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.licenses.clear();
    if (licenses && count > 0) {
        p.licenses.reserve(count);
        for (size_t i = 0; i < count; ++i) {
            const auto& src = licenses[i];
            if (src.package_id == 0) continue;
            p.licenses[src.package_id] = lsc::PushedState::LicenseEntry{
                src.package_id,
                src.owner_id,
                src.time_created,
                src.license_type,
                src.flags,
                src.change_number,
                src.minute_limit,
                src.minutes_used,
            };
        }
    }
    __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
        "license-list observer: %zu license(s) mirrored", p.licenses.size());
}

__attribute__((constructor))
void register_license_list_observer() {
    wn_cm_bridge_register_license_list_observer(&on_license_list_event);
}

void on_account_info_event(const WnCmAccountInfo* info) {
    if (!info) return;
    auto& p = lsc::pushed();
    p.account_two_factor_enabled.store(info->two_factor_enabled);
    p.account_phone_verified.store(info->phone_verified);
    p.account_phone_identifying.store(info->phone_identifying);
    p.account_phone_requires_verification.store(info->phone_requires_verification);

    if (info->persona_name && info->persona_name_len > 0) {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        p.persona_name.assign(info->persona_name, info->persona_name_len);
    }
    if (info->ip_country && info->ip_country_len > 0) {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        p.ip_country.assign(info->ip_country, info->ip_country_len);
        p.ip_country_set.store(1);
    }

    __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
        "account-info observer: persona='%.*s' ip='%.*s' 2FA=%d phone_v=%d phone_id=%d phone_nv=%d",
        static_cast<int>(info->persona_name_len), info->persona_name ? info->persona_name : "",
        static_cast<int>(info->ip_country_len),   info->ip_country   ? info->ip_country   : "",
        info->two_factor_enabled, info->phone_verified,
        info->phone_identifying, info->phone_requires_verification);
}

__attribute__((constructor))
void register_account_info_observer() {
    wn_cm_bridge_register_account_info_observer(&on_account_info_event);
}

void on_lobby_data_event(const WnCmLobbyData* data) {
    if (!data) return;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto& L = p.active_lobbies[data->steam_id_lobby];
        L.app_id      = data->app_id;
        L.owner_sid   = data->steam_id_owner;
        L.max_members = data->max_members;
        L.lobby_type  = data->lobby_type;
        L.lobby_flags = data->lobby_flags;
        L.members.clear();
        for (size_t i = 0; i < data->member_count; ++i) {
            const auto& m = data->members[i];
            auto& mb = L.members[m.steam_id];
            if (m.persona_name) mb.persona_name = m.persona_name;
            if (m.metadata_bytes && m.metadata_len > 0) {
                mb.data["__raw_metadata"] = std::string(
                    reinterpret_cast<const char*>(m.metadata_bytes),
                    m.metadata_len);
            }
        }
    }
    struct LobbyDataUpdate { uint64_t lobby; uint64_t member; uint8_t success; uint8_t _pad[7]; };
    LobbyDataUpdate cb{};
    cb.lobby   = data->steam_id_lobby;
    cb.member  = 0;
    cb.success = 1;
    lsc::push_callback(lsc::state().user.load(), /*kLobbyDataUpdate*/ 505,
                       &cb, sizeof(cb));
}

__attribute__((constructor))
void register_lobby_data_observer() {
    wn_cm_bridge_register_lobby_data_observer(&on_lobby_data_event);
}

void on_lobby_chat_msg_event(uint64_t lobby_sid, uint64_t sender_sid,
                              const uint8_t* data, size_t n) {
    if (lobby_sid == 0) return;
    uint32_t chat_id = 0;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto& ring = p.lobby_chat_buffer[lobby_sid];
        if (ring.size() >= 1024) ring.erase(ring.begin());
        lsc::PushedState::LobbyChatEntry e;
        e.sender_sid = sender_sid;
        e.chat_type  = 1; // EChatEntryType::ChatMsg
        if (data && n > 0) e.body.assign(data, data + n);
        ring.push_back(std::move(e));
        chat_id = static_cast<uint32_t>(ring.size() - 1);
    }
    struct LobbyChatMsg {
        uint64_t lobby;
        uint64_t user;
        uint8_t  chat_type;
        uint8_t  _pad[3];
        uint32_t chat_id;
    };
    LobbyChatMsg cb{};
    cb.lobby     = lobby_sid;
    cb.user      = sender_sid;
    cb.chat_type = 1;
    cb.chat_id   = chat_id;
    lsc::push_callback(lsc::state().user.load(), /*kLobbyChatMsg*/ 507,
                       &cb, sizeof(cb));
}

__attribute__((constructor))
void register_lobby_chat_msg_observer() {
    wn_cm_bridge_register_lobby_chat_msg_observer(&on_lobby_chat_msg_event);
}

void on_lobby_membership_event(int32_t joined,
                                uint64_t lobby_sid,
                                uint64_t user_sid,
                                const char* persona_name) {
    if (lobby_sid == 0 || user_sid == 0) return;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto& L = p.active_lobbies[lobby_sid];
        if (joined) {
            auto& mb = L.members[user_sid];
            if (persona_name) mb.persona_name = persona_name;
        } else {
            L.members.erase(user_sid);
        }
    }
    struct LobbyChatUpdate {
        uint64_t lobby;
        uint64_t user_changed;
        uint64_t making_change;
        uint32_t state_change;
        uint32_t _pad;
    };
    LobbyChatUpdate cb{};
    cb.lobby         = lobby_sid;
    cb.user_changed  = user_sid;
    cb.making_change = user_sid;  // best effort — we don't know admin
    cb.state_change  = joined ? 0x1u : 0x2u;
    lsc::push_callback(lsc::state().user.load(), /*kLobbyChatUpdate*/ 506,
                       &cb, sizeof(cb));
}

__attribute__((constructor))
void register_lobby_membership_observer() {
    wn_cm_bridge_register_lobby_membership_observer(&on_lobby_membership_event);
}

void on_server_realtime_event(uint32_t server_realtime) {
    if (server_realtime == 0) return;
    auto& p = lsc::pushed();
    const auto now = std::chrono::steady_clock::now();
    const auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    p.server_realtime.store(server_realtime);
    p.server_realtime_anchor_local_ms.store(static_cast<int64_t>(now_ms));
    __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
        "server-realtime observer: %u (anchored at local %lld ms)",
        server_realtime, static_cast<long long>(now_ms));
}

__attribute__((constructor))
void register_server_realtime_observer() {
    wn_cm_bridge_register_server_realtime_observer(&on_server_realtime_event);
}
}  // namespace

#define WN_TAG "WnLibSteamClient"
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO, WN_TAG, __VA_ARGS__)

namespace {
std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (!c) return {};
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetSteamId(
        JNIEnv* /*env*/, jclass /*cls*/, jlong steamId64) {
    auto& p = lsc::pushed();
    p.steam_id.store(static_cast<uint64_t>(steamId64));
    p.account_id.store(static_cast<uint32_t>(static_cast<uint64_t>(steamId64) & 0xFFFFFFFFu));
    WN_LOGI("set_steam_id(%llu)",
            static_cast<unsigned long long>(steamId64));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetLoggedOn(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean loggedOn) {
    bool now = (loggedOn == JNI_TRUE);
    bool prev = lsc::state().logged_on.load();
    lsc::set_logged_on(now);
    WN_LOGI("set_logged_on(%d) prev=%d emitted_cb=%d",
            now ? 1 : 0, prev ? 1 : 0, (now != prev) ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetPersonaName(
        JNIEnv* env, jclass /*cls*/, jstring jname) {
    auto& p = lsc::pushed();
    std::string name = jstr(env, jname);
    uint64_t self;
    bool changed;
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        changed = (p.persona_name != name);
        p.persona_name = std::move(name);
        self = p.steam_id.load();
    }
    if (changed) {
        emit_persona_state_change(self, cb::kPersonaChangeName);
    }
    WN_LOGI("set_persona_name(\"%s\") changed=%d", p.persona_name.c_str(),
            changed ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/, jint state) {
    auto& p = lsc::pushed();
    int prev = p.persona_state.exchange(static_cast<int>(state));
    if (prev == state) return;
    int32_t flags = cb::kPersonaChangeStatus;
    if (prev == 0 && state != 0) flags |= cb::kPersonaChangeComeOnline;
    if (prev != 0 && state == 0) flags |= cb::kPersonaChangeGoneOffline;
    emit_persona_state_change(p.steam_id.load(), flags);
    wn_cm_set_persona_state(state);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppId(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    uint32_t app = static_cast<uint32_t>(appId);
    uint32_t prev = lsc::pushed().app_id.exchange(app);
    if (prev == app) return;
    wn_cm_notify_games_played(app);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetIPCountry(
        JNIEnv* env, jclass /*cls*/, jstring jcc) {
    auto& p = lsc::pushed();
    std::string cc = jstr(env, jcc);
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.ip_country = std::move(cc);
    p.ip_country_set.store(1);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetUiLanguage(
        JNIEnv* env, jclass /*cls*/, jstring jlang) {
    auto& p = lsc::pushed();
    std::string lang = jstr(env, jlang);
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.ui_language = std::move(lang);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetOwnedApps(
        JNIEnv* env, jclass /*cls*/, jintArray appIds) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.owned_apps.clear();
    if (!appIds) return;
    jsize n = env->GetArrayLength(appIds);
    if (n <= 0) return;
    jint* arr = env->GetIntArrayElements(appIds, nullptr);
    if (!arr) return;
    p.owned_apps.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        if (arr[i] > 0) p.owned_apps.insert(static_cast<uint32_t>(arr[i]));
    }
    env->ReleaseIntArrayElements(appIds, arr, JNI_ABORT);
    WN_LOGI("set_owned_apps: %zu entries", p.owned_apps.size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetInstalledApps(
        JNIEnv* env, jclass /*cls*/, jintArray appIds) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.installed_apps.clear();
    if (!appIds) return;
    jsize n = env->GetArrayLength(appIds);
    if (n <= 0) return;
    jint* arr = env->GetIntArrayElements(appIds, nullptr);
    if (!arr) return;
    p.installed_apps.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        if (arr[i] > 0) p.installed_apps.insert(static_cast<uint32_t>(arr[i]));
    }
    env->ReleaseIntArrayElements(appIds, arr, JNI_ABORT);
    WN_LOGI("set_installed_apps: %zu entries", p.installed_apps.size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppInstallDir(
        JNIEnv* env, jclass /*cls*/, jint appId, jstring jdir) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::string dir = jstr(env, jdir);
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (dir.empty()) {
        p.app_install_dirs.erase(static_cast<uint32_t>(appId));
    } else {
        p.app_install_dirs[static_cast<uint32_t>(appId)] = std::move(dir);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendsList(
        JNIEnv* env, jclass /*cls*/, jlongArray steamIds) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.friends.clear();
    if (!steamIds) return;
    jsize n = env->GetArrayLength(steamIds);
    if (n <= 0) return;
    jlong* arr = env->GetLongArrayElements(steamIds, nullptr);
    if (!arr) return;
    p.friends.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        if (arr[i] != 0) p.friends.push_back(static_cast<uint64_t>(arr[i]));
    }
    env->ReleaseLongArrayElements(steamIds, arr, JNI_ABORT);
    WN_LOGI("set_friends_list: %zu entries", p.friends.size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppBuildId(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId, jint buildId) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (buildId <= 0) {
        p.app_build_ids.erase(static_cast<uint32_t>(appId));
    } else {
        p.app_build_ids[static_cast<uint32_t>(appId)] =
            static_cast<uint32_t>(buildId);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppNames(
        JNIEnv* env, jclass /*cls*/, jintArray appIds, jobjectArray names) {
    if (!appIds || !names) return;
    jsize n = env->GetArrayLength(appIds);
    if (n <= 0 || env->GetArrayLength(names) != n) return;
    jint* ids = env->GetIntArrayElements(appIds, nullptr);
    if (!ids) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    size_t set_count = 0, clear_count = 0;
    for (jsize i = 0; i < n; ++i) {
        if (ids[i] <= 0) continue;
        auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(names, i));
        if (!js) {
            p.app_names.erase(static_cast<uint32_t>(ids[i]));
            ++clear_count;
            continue;
        }
        const char* c = env->GetStringUTFChars(js, nullptr);
        if (c && *c) {
            p.app_names[static_cast<uint32_t>(ids[i])] = c;
            ++set_count;
        } else {
            p.app_names.erase(static_cast<uint32_t>(ids[i]));
            ++clear_count;
        }
        if (c) env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
    }
    env->ReleaseIntArrayElements(appIds, ids, JNI_ABORT);
    WN_LOGI("set_app_names: set=%zu clear=%zu total=%zu",
            set_count, clear_count, p.app_names.size());
}

extern "C" void* wn_get_isteam_apps();
extern "C" void* wn_get_isteam_remote_storage();
extern "C" void* wn_get_isteam_user();
extern "C" void* wn_get_isteam_friends();
extern "C" void* wn_get_isteam_utils();

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectAccountInfo(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean twoFA, jboolean phoneV,
        jboolean phoneId, jboolean phoneNV) {
    WnCmAccountInfo info{};
    info.two_factor_enabled = twoFA == JNI_TRUE;
    info.phone_verified     = phoneV == JNI_TRUE;
    info.phone_identifying  = phoneId == JNI_TRUE;
    info.phone_requires_verification = phoneNV == JNI_TRUE;
    wn_cm_bridge_inject_test_account_info(&info);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAccountFlag(
        JNIEnv* /*env*/, jclass /*cls*/, jint flagKind, jboolean on) {
    auto& p = lsc::pushed();
    switch (flagKind) {
        case 0: p.account_phone_verified.store(on); break;
        case 1: p.account_two_factor_enabled.store(on); break;
        case 2: p.account_phone_identifying.store(on); break;
        case 3: p.account_phone_requires_verification.store(on); break;
        default: break;
    }
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUserBool(
        JNIEnv* /*env*/, jclass /*cls*/, jint slot) {
    if (slot < 26 || slot > 29) return JNI_FALSE;
    void* obj = wn_get_isteam_user();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[slot]);
    return fn(obj) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetPlayerNickname(
        JNIEnv* env, jclass /*cls*/, jlong sid, jstring jNickname) {
    if (sid == 0) return;
    auto& p = lsc::pushed();
    bool changed = false;
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        const uint64_t key = static_cast<uint64_t>(sid);
        if (!jNickname) {
            changed = (p.player_nicknames.erase(key) > 0);
        } else {
            const char* c = env->GetStringUTFChars(jNickname, nullptr);
            if (!c || *c == '\0') {
                if (c) env->ReleaseStringUTFChars(jNickname, c);
                changed = (p.player_nicknames.erase(key) > 0);
            } else {
                std::string newName(c);
                env->ReleaseStringUTFChars(jNickname, c);
                auto it = p.player_nicknames.find(key);
                if (it == p.player_nicknames.end()) {
                    p.player_nicknames[key] = std::move(newName);
                    changed = true;
                } else if (it->second != newName) {
                    it->second = std::move(newName);
                    changed = true;
                }
            }
        }
    }
    if (changed) {
        emit_persona_state_change(static_cast<uint64_t>(sid),
                                  cb::kPersonaChangeNickname);
    }
}
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetPlayerNickname(
        JNIEnv* env, jclass /*cls*/, jlong sid) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return nullptr;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = const char* (*)(void*, uint64_t);
    auto fn = reinterpret_cast<Fn>(vt[11]);
    const char* nick = fn(obj, static_cast<uint64_t>(sid));
    return nick ? env->NewStringUTF(nick) : nullptr;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCheckFileSignature(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_utils();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[19]);
    const char* c = jName ? env->GetStringUTFChars(jName, nullptr) : nullptr;
    uint64_t h = fn(obj, c);
    if (c) env->ReleaseStringUTFChars(jName, c);
    return static_cast<jlong>(h);
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedSteamId(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return static_cast<jlong>(lsc::pushed().steam_id.load());
}
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedPersonaName(
        JNIEnv* env, jclass /*cls*/) {
    std::string name;
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        name = lsc::pushed().persona_name;
    }
    return env->NewStringUTF(name.c_str());
}
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedIpCountry(
        JNIEnv* env, jclass /*cls*/) {
    std::string c;
    if (lsc::pushed().ip_country_set.load() != 0) {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        c = lsc::pushed().ip_country;
    }
    return env->NewStringUTF(c.c_str());
}
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedUiLanguage(
        JNIEnv* env, jclass /*cls*/) {
    std::string s;
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        s = lsc::pushed().ui_language;
    }
    return env->NewStringUTF(s.c_str());
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedServerRealTime(
        JNIEnv* /*env*/, jclass /*cls*/) {
    auto anchor   = lsc::pushed().server_realtime.load();
    auto anchor_local_ms = lsc::pushed().server_realtime_anchor_local_ms.load();
    if (anchor == 0 || anchor_local_ms == 0) return 0;
    const auto now = std::chrono::steady_clock::now();
    const auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    auto elapsed_s = (now_ms - anchor_local_ms) / 1000;
    if (elapsed_s < 0) elapsed_s = 0;
    return static_cast<jint>(anchor + static_cast<uint32_t>(elapsed_s));
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return static_cast<jint>(lsc::pushed().persona_state.load());
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedLoggedOn(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return lsc::state().logged_on.load() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedAppId(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return static_cast<jint>(lsc::pushed().app_id.load());
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedOwnedAppCount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    return static_cast<jint>(lsc::pushed().owned_apps.size());
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedInstalledAppCount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    return static_cast<jint>(lsc::pushed().installed_apps.size());
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedFriendCount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    return static_cast<jint>(lsc::pushed().friends.size());
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedFirstFriend(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto& fs = lsc::pushed().friends;
    return fs.empty() ? 0L : static_cast<jlong>(fs.front());
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedCloudFileCount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    return static_cast<jint>(lsc::pushed().cloud_files.size());
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedCloudEnabledAccount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return lsc::pushed().cloud_enabled_account.load() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedCloudEnabledApp(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return lsc::pushed().cloud_enabled_app.load() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedEncryptedAppTicketSize(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    if (appId <= 0) return 0;
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto it = lsc::pushed().encrypted_app_tickets.find(
        static_cast<uint32_t>(appId));
    if (it == lsc::pushed().encrypted_app_tickets.end()) return 0;
    return static_cast<jint>(it->second.size());
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileShare(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[7]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    uint64_t h = fn(obj, name);
    env->ReleaseStringUTFChars(jName, name);
    return static_cast<jlong>(h);
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticAppsGetFileDetails(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_apps();
    if (!obj || !jName) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[25]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    uint64_t h = fn(obj, name);
    env->ReleaseStringUTFChars(jName, name);
    return static_cast<jlong>(h);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetSelfPlayerLevel(
        JNIEnv* /*env*/, jclass /*cls*/, jint level) {
    lsc::pushed().self_player_level.store(level < 0 ? 0 : level);
}
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetSelfGameBadge(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId, jint nSeries,
        jboolean bFoil, jint tier) {
    if (appId <= 0) return;
    int32_t key = (static_cast<int32_t>(appId) & 0x0FFFFFFF)
                | ((nSeries & 0x07) << 28)
                | (bFoil ? (1 << 31) : 0);
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (tier < 0) p.self_game_badges.erase(key);
    else          p.self_game_badges[key] = tier;
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetPlayerSteamLevel(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = int (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[24]);
    return fn(obj);
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetGameBadgeLevel(
        JNIEnv* /*env*/, jclass /*cls*/, jint nSeries, jboolean bFoil) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = int (*)(void*, int, bool);
    auto fn = reinterpret_cast<Fn>(vt[23]);
    return fn(obj, nSeries, bFoil ? true : false);
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestStoreAuthURL(
        JNIEnv* env, jclass /*cls*/, jstring jRedirect) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[25]);
    const char* c = jRedirect ? env->GetStringUTFChars(jRedirect, nullptr) : nullptr;
    uint64_t h = fn(obj, c);
    if (c) env->ReleaseStringUTFChars(jRedirect, c);
    return static_cast<jlong>(h);
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetMarketEligibility(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[30]);
    return static_cast<jlong>(fn(obj));
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDurationControl(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[31]);
    return static_cast<jlong>(fn(obj));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendSteamLevel(
        JNIEnv* /*env*/, jclass /*cls*/, jlong sid, jint level) {
    if (sid == 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (level < 0) p.friend_steam_levels.erase(static_cast<uint64_t>(sid));
    else           p.friend_steam_levels[static_cast<uint64_t>(sid)] = level;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeIsAppMarkedCorrupt(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    if (appId <= 0) return JNI_FALSE;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    return p.apps_marked_corrupt.count(static_cast<uint32_t>(appId)) > 0
           ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeClearAppCorruptFlag(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.apps_marked_corrupt.erase(static_cast<uint32_t>(appId));
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUserHasLicense(
        JNIEnv* /*env*/, jclass /*cls*/, jlong sid, jint appId) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 2;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = int (*)(void*, uint64_t, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[18]);
    return fn(obj, static_cast<uint64_t>(sid), static_cast<uint32_t>(appId));
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticMarkContentCorrupt(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean missingOnly) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, bool);
    auto fn = reinterpret_cast<Fn>(vt[16]);
    return fn(obj, missingOnly ? true : false) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendSteamLevel(
        JNIEnv* /*env*/, jclass /*cls*/, jlong sid) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = int (*)(void*, uint64_t);
    auto fn = reinterpret_cast<Fn>(vt[10]);
    return fn(obj, static_cast<uint64_t>(sid));
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetAuthTicketForWebApi(
        JNIEnv* env, jclass /*cls*/, jstring jIdentity) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[14]);
    const char* c = jIdentity ? env->GetStringUTFChars(jIdentity, nullptr) : nullptr;
    uint64_t h = fn(obj, c);
    if (c) env->ReleaseStringUTFChars(jIdentity, c);
    return static_cast<jlong>(h);
}
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendRelationship(
        JNIEnv* /*env*/, jclass /*cls*/, jlong sid) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = int (*)(void*, uint64_t);
    auto fn = reinterpret_cast<Fn>(vt[5]);
    return fn(obj, static_cast<uint64_t>(sid));
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticHasFriend(
        JNIEnv* /*env*/, jclass /*cls*/, jlong sid, jint flags) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint64_t, int);
    auto fn = reinterpret_cast<Fn>(vt[17]);
    return fn(obj, static_cast<uint64_t>(sid), flags) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetUserDataFolder(
        JNIEnv* env, jclass /*cls*/) {
    void* obj = wn_get_isteam_user();
    if (!obj) return nullptr;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, char*, int);
    auto fn = reinterpret_cast<Fn>(vt[6]);
    char buf[512];
    buf[0] = '\0';
    if (!fn(obj, buf, sizeof(buf))) return nullptr;
    return env->NewStringUTF(buf);
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetDurationControl(
        JNIEnv* /*env*/, jclass /*cls*/, jint state) {
    void* obj = wn_get_isteam_user();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, int);
    auto fn = reinterpret_cast<Fn>(vt[32]);
    return fn(obj, state) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppFlag(
        JNIEnv* /*env*/, jclass /*cls*/, jint flagKind, jint appId, jboolean on) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto& set = (flagKind == 0) ? p.app_low_violence
                                 : p.app_vac_banned;
    if (on) set.insert(static_cast<uint32_t>(appId));
    else    set.erase(static_cast<uint32_t>(appId));
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticAppsBool(
        JNIEnv* /*env*/, jclass /*cls*/, jint slot) {
    void* obj = wn_get_isteam_apps();
    if (!obj || slot < 0 || slot > 3) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[slot]);
    return fn(obj) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetDlcContext(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[29]);
    return fn(obj, static_cast<uint32_t>(appId)) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileForget(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[5]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    bool ok = fn(obj, name);
    env->ReleaseStringUTFChars(jName, name);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFilePersisted(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[14]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    bool ok = fn(obj, name);
    env->ReleaseStringUTFChars(jName, name);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppCloudRemoteDir(
        JNIEnv* env, jclass /*cls*/, jint appId, jstring jPath) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!jPath) {
        p.app_cloud_remote_dirs.erase(static_cast<uint32_t>(appId));
        return;
    }
    const char* c = env->GetStringUTFChars(jPath, nullptr);
    if (!c || *c == '\0') {
        if (c) env->ReleaseStringUTFChars(jPath, c);
        p.app_cloud_remote_dirs.erase(static_cast<uint32_t>(appId));
        return;
    }
    p.app_cloud_remote_dirs[static_cast<uint32_t>(appId)] = std::string(c);
    env->ReleaseStringUTFChars(jPath, c);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppCurrentBeta(
        JNIEnv* env, jclass /*cls*/, jint appId, jstring jBranch) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!jBranch) {
        p.app_current_beta.erase(static_cast<uint32_t>(appId));
        return;
    }
    const char* c = env->GetStringUTFChars(jBranch, nullptr);
    if (!c || *c == '\0') {
        if (c) env->ReleaseStringUTFChars(jBranch, c);
        p.app_current_beta.erase(static_cast<uint32_t>(appId));
        return;
    }
    p.app_current_beta[static_cast<uint32_t>(appId)] = std::string(c);
    env->ReleaseStringUTFChars(jBranch, c);
}

extern "C" void* wn_get_isteam_apps();

extern "C" void* wn_get_isteam_remote_storage();
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileWrite(
        JNIEnv* env, jclass /*cls*/, jstring jName, jbyteArray jData) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName || !jData) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, const char*, const void*, int);
    auto fn = reinterpret_cast<Fn>(vt[0]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    jsize len = env->GetArrayLength(jData);
    jbyte* buf = env->GetByteArrayElements(jData, nullptr);
    bool ok = fn(obj, name, buf, static_cast<int>(len));
    env->ReleaseByteArrayElements(jData, buf, JNI_ABORT);
    env->ReleaseStringUTFChars(jName, name);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileRead(
        JNIEnv* env, jclass /*cls*/, jstring jName, jint maxBytes) {
    if (!jName || maxBytes <= 0) return nullptr;
    void* obj = wn_get_isteam_remote_storage();
    if (!obj) return nullptr;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = int (*)(void*, const char*, void*, int);
    auto fn = reinterpret_cast<Fn>(vt[1]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    std::vector<char> buf(static_cast<size_t>(maxBytes));
    int n = fn(obj, name, buf.data(), maxBytes);
    env->ReleaseStringUTFChars(jName, name);
    if (n <= 0) return nullptr;
    jbyteArray out = env->NewByteArray(n);
    env->SetByteArrayRegion(out, 0, n, reinterpret_cast<jbyte*>(buf.data()));
    return out;
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamOpen(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[9]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    uint64_t h = fn(obj, name);
    env->ReleaseStringUTFChars(jName, name);
    return static_cast<jlong>(h);
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamWriteChunk(
        JNIEnv* env, jclass /*cls*/, jlong hStream, jbyteArray jData) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jData) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint64_t, const void*, int);
    auto fn = reinterpret_cast<Fn>(vt[10]);
    jsize len = env->GetArrayLength(jData);
    jbyte* buf = env->GetByteArrayElements(jData, nullptr);
    bool ok = fn(obj, static_cast<uint64_t>(hStream), buf, static_cast<int>(len));
    env->ReleaseByteArrayElements(jData, buf, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamClose(
        JNIEnv* /*env*/, jclass /*cls*/, jlong hStream) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint64_t);
    auto fn = reinterpret_cast<Fn>(vt[11]);
    return fn(obj, static_cast<uint64_t>(hStream)) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamCancel(
        JNIEnv* /*env*/, jclass /*cls*/, jlong hStream) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint64_t);
    auto fn = reinterpret_cast<Fn>(vt[12]);
    return fn(obj, static_cast<uint64_t>(hStream)) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileWriteAsync(
        JNIEnv* env, jclass /*cls*/, jstring jName, jbyteArray jData) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName || !jData) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*, const void*, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[2]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    jsize len = env->GetArrayLength(jData);
    jbyte* buf = env->GetByteArrayElements(jData, nullptr);
    uint64_t h = fn(obj, name, buf, static_cast<uint32_t>(len));
    env->ReleaseByteArrayElements(jData, buf, JNI_ABORT);
    env->ReleaseStringUTFChars(jName, name);
    return static_cast<jlong>(h);
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileReadAsync(
        JNIEnv* env, jclass /*cls*/, jstring jName, jint nOffset, jint cubToRead) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName || cubToRead <= 0) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*, const char*, uint32_t, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[3]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    uint64_t h = fn(obj, name, static_cast<uint32_t>(nOffset),
                   static_cast<uint32_t>(cubToRead));
    env->ReleaseStringUTFChars(jName, name);
    return static_cast<jlong>(h);
}
JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileReadAsyncComplete(
        JNIEnv* env, jclass /*cls*/, jlong hCall, jint cubToRead) {
    if (hCall == 0 || cubToRead <= 0) return nullptr;
    void* obj = wn_get_isteam_remote_storage();
    if (!obj) return nullptr;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint64_t, void*, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[4]);
    std::vector<char> buf(static_cast<size_t>(cubToRead));
    bool ok = fn(obj, static_cast<uint64_t>(hCall), buf.data(),
                 static_cast<uint32_t>(cubToRead));
    if (!ok) return nullptr;
    jbyteArray out = env->NewByteArray(cubToRead);
    env->SetByteArrayRegion(out, 0, cubToRead, reinterpret_cast<jbyte*>(buf.data()));
    return out;
}
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileDelete(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    void* obj = wn_get_isteam_remote_storage();
    if (!obj || !jName) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, const char*);
    auto fn = reinterpret_cast<Fn>(vt[6]);
    const char* name = env->GetStringUTFChars(jName, nullptr);
    bool ok = fn(obj, name);
    env->ReleaseStringUTFChars(jName, name);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetCurrentBetaName(
        JNIEnv* env, jclass /*cls*/) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return nullptr;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, char*, int);
    auto fn = reinterpret_cast<Fn>(vt[15]);
    char buf[128];
    buf[0] = '\0';
    if (!fn(obj, buf, sizeof(buf))) return nullptr;
    return env->NewStringUTF(buf);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppDownloadProgress(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId,
        jlong bytesDownloaded, jlong bytesTotal) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (bytesTotal <= 0) {
        p.app_dl_progress.erase(static_cast<uint32_t>(appId));
        return;
    }
    auto& e = p.app_dl_progress[static_cast<uint32_t>(appId)];
    e.bytes_downloaded = static_cast<uint64_t>(std::max<jlong>(0, bytesDownloaded));
    e.bytes_total      = static_cast<uint64_t>(bytesTotal);
}

static uint64_t s_diag_dl_downloaded = 0;
static uint64_t s_diag_dl_total      = 0;
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDlcDownloadProgress(
        JNIEnv* /*env*/, jclass /*cls*/, jint appId) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint32_t, uint64_t*, uint64_t*);
    auto fn = reinterpret_cast<Fn>(vt[22]);
    s_diag_dl_downloaded = 0;
    s_diag_dl_total      = 0;
    return fn(obj, static_cast<uint32_t>(appId),
              &s_diag_dl_downloaded, &s_diag_dl_total) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDlcDownloadProgressBytes(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return static_cast<jlong>(s_diag_dl_downloaded);
}
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDlcDownloadProgressTotal(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return static_cast<jlong>(s_diag_dl_total);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppInstalledDepots(
        JNIEnv* env, jclass /*cls*/, jint appId, jintArray depotIds) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!depotIds) {
        p.app_installed_depots.erase(static_cast<uint32_t>(appId));
        return;
    }
    jsize n = env->GetArrayLength(depotIds);
    if (n <= 0) {
        p.app_installed_depots.erase(static_cast<uint32_t>(appId));
        return;
    }
    jint* arr = env->GetIntArrayElements(depotIds, nullptr);
    std::vector<uint32_t> depots;
    depots.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        if (arr[i] > 0) depots.push_back(static_cast<uint32_t>(arr[i]));
    }
    env->ReleaseIntArrayElements(depotIds, arr, JNI_ABORT);
    p.app_installed_depots[static_cast<uint32_t>(appId)] = std::move(depots);
    WN_LOGI("set_app_installed_depots: app=%d count=%zu",
            appId, p.app_installed_depots[static_cast<uint32_t>(appId)].size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppDlcs(
        JNIEnv* env, jclass /*cls*/, jint parentAppId,
        jintArray dlcAppIds, jobjectArray dlcNames, jbooleanArray available) {
    if (parentAppId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!dlcAppIds) {
        p.app_dlcs.erase(static_cast<uint32_t>(parentAppId));
        return;
    }
    jsize n = env->GetArrayLength(dlcAppIds);
    if (n <= 0) {
        p.app_dlcs.erase(static_cast<uint32_t>(parentAppId));
        return;
    }
    jint*     ids = env->GetIntArrayElements(dlcAppIds, nullptr);
    jboolean* av  = (available && env->GetArrayLength(available) == n)
                        ? env->GetBooleanArrayElements(available, nullptr) : nullptr;
    auto read_name = [&](jsize i) -> std::string {
        if (!dlcNames || env->GetArrayLength(dlcNames) <= i) return {};
        auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(dlcNames, i));
        if (!js) return {};
        const char* c = env->GetStringUTFChars(js, nullptr);
        std::string out = c ? c : "";
        if (c) env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
        return out;
    };
    std::vector<wn_libsteamclient::PushedState::DlcEntry> entries;
    entries.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        if (ids[i] <= 0) continue;
        wn_libsteamclient::PushedState::DlcEntry e;
        e.app_id    = static_cast<uint32_t>(ids[i]);
        e.name      = read_name(i);
        e.available = av ? (av[i] == JNI_TRUE) : true;
        entries.push_back(std::move(e));
    }
    env->ReleaseIntArrayElements(dlcAppIds, ids, JNI_ABORT);
    if (av) env->ReleaseBooleanArrayElements(available, av, JNI_ABORT);
    p.app_dlcs[static_cast<uint32_t>(parentAppId)] = std::move(entries);
    WN_LOGI("set_app_dlcs: parent=%d count=%zu",
            parentAppId, p.app_dlcs[static_cast<uint32_t>(parentAppId)].size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppWorkshopItems(
        JNIEnv* env, jclass /*cls*/, jint appId,
        jlongArray publishedFileIds, jobjectArray installDirs,
        jlongArray sizesBytes, jlongArray timestamps) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!publishedFileIds) {
        p.subscribed_workshop_items.erase(static_cast<uint32_t>(appId));
        return;
    }
    jsize n = env->GetArrayLength(publishedFileIds);
    if (n <= 0) {
        p.subscribed_workshop_items.erase(static_cast<uint32_t>(appId));
        return;
    }
    jlong* ids   = env->GetLongArrayElements(publishedFileIds, nullptr);
    jlong* sizes = (sizesBytes && env->GetArrayLength(sizesBytes) == n)
                       ? env->GetLongArrayElements(sizesBytes, nullptr) : nullptr;
    jlong* tims  = (timestamps && env->GetArrayLength(timestamps) == n)
                       ? env->GetLongArrayElements(timestamps, nullptr) : nullptr;
    auto read_str = [&](jsize i) -> std::string {
        if (!installDirs || env->GetArrayLength(installDirs) <= i) return {};
        auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(installDirs, i));
        if (!js) return {};
        const char* c = env->GetStringUTFChars(js, nullptr);
        std::string out = c ? c : "";
        if (c) env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
        return out;
    };
    std::unordered_map<uint64_t, wn_libsteamclient::PushedState::WorkshopItemInfo> items;
    items.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        if (ids[i] <= 0) continue;
        wn_libsteamclient::PushedState::WorkshopItemInfo info;
        info.install_dir = read_str(i);
        info.size_bytes  = sizes ? static_cast<uint64_t>(sizes[i]) : 0u;
        info.timestamp   = tims  ? static_cast<uint32_t>(tims[i]) : 0u;
        info.installed   = true;
        items.emplace(static_cast<uint64_t>(ids[i]), std::move(info));
    }
    env->ReleaseLongArrayElements(publishedFileIds, ids, JNI_ABORT);
    if (sizes) env->ReleaseLongArrayElements(sizesBytes, sizes, JNI_ABORT);
    if (tims)  env->ReleaseLongArrayElements(timestamps, tims,  JNI_ABORT);
    if (items.empty()) {
        p.subscribed_workshop_items.erase(static_cast<uint32_t>(appId));
    } else {
        p.subscribed_workshop_items[static_cast<uint32_t>(appId)] = std::move(items);
    }
    WN_LOGI("set_app_workshop_items: app=%d count=%zu", appId,
            p.subscribed_workshop_items[static_cast<uint32_t>(appId)].size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetInventoryItemDefs(
        JNIEnv* env, jclass /*cls*/, jint appId,
        jintArray defIds, jintArray propCountsPerDef,
        jobjectArray propKeys, jobjectArray propVals) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!defIds) {
        p.inventory_item_defs.erase(static_cast<uint32_t>(appId));
        return;
    }
    jsize n = env->GetArrayLength(defIds);
    if (n <= 0) {
        p.inventory_item_defs.erase(static_cast<uint32_t>(appId));
        return;
    }
    if (!propCountsPerDef || env->GetArrayLength(propCountsPerDef) != n) return;
    jint* ids    = env->GetIntArrayElements(defIds, nullptr);
    jint* counts = env->GetIntArrayElements(propCountsPerDef, nullptr);
    auto read_str = [&](jobjectArray arr, jsize i) -> std::string {
        if (!arr || env->GetArrayLength(arr) <= i) return {};
        auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(arr, i));
        if (!js) return {};
        const char* c = env->GetStringUTFChars(js, nullptr);
        std::string out = c ? c : "";
        if (c) env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
        return out;
    };
    std::unordered_map<int32_t, std::unordered_map<std::string, std::string>> table;
    table.reserve(n);
    jsize cursor = 0;
    for (jsize i = 0; i < n; ++i) {
        if (ids[i] <= 0) { cursor += counts[i]; continue; }
        std::unordered_map<std::string, std::string> props;
        const jsize end = cursor + counts[i];
        props.reserve(counts[i]);
        for (jsize j = cursor; j < end; ++j) {
            auto k = read_str(propKeys, j);
            if (k.empty()) continue;
            props.emplace(std::move(k), read_str(propVals, j));
        }
        cursor = end;
        table.emplace(static_cast<int32_t>(ids[i]), std::move(props));
    }
    env->ReleaseIntArrayElements(defIds, ids, JNI_ABORT);
    env->ReleaseIntArrayElements(propCountsPerDef, counts, JNI_ABORT);
    if (table.empty()) {
        p.inventory_item_defs.erase(static_cast<uint32_t>(appId));
    } else {
        p.inventory_item_defs[static_cast<uint32_t>(appId)] = std::move(table);
    }
    WN_LOGI("set_inventory_item_defs: app=%d count=%zu", appId,
            p.inventory_item_defs[static_cast<uint32_t>(appId)].size());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/, jlong steamId64, jint state) {
    if (steamId64 == 0) return;
    int32_t flags = 0;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto sid = static_cast<uint64_t>(steamId64);
        if (state < 0) {
            auto it = p.friend_persona_states.find(sid);
            if (it != p.friend_persona_states.end() && it->second != 0) {
                flags = cb::kPersonaChangeStatus | cb::kPersonaChangeGoneOffline;
            }
            p.friend_persona_states.erase(sid);
        } else {
            uint32_t prev = 0;
            bool prev_known = false;
            auto it = p.friend_persona_states.find(sid);
            if (it != p.friend_persona_states.end()) {
                prev = it->second;
                prev_known = true;
            }
            p.friend_persona_states[sid] = static_cast<uint32_t>(state);
            if (!prev_known || prev != static_cast<uint32_t>(state)) {
                flags = cb::kPersonaChangeStatus;
                if (prev == 0 && state != 0) flags |= cb::kPersonaChangeComeOnline;
                if (prev != 0 && state == 0) flags |= cb::kPersonaChangeGoneOffline;
            }
        }
    }
    if (flags != 0) emit_persona_state_change(static_cast<uint64_t>(steamId64), flags);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendGamePlayed(
        JNIEnv* /*env*/, jclass /*cls*/, jlong steamId64, jint appId) {
    if (steamId64 == 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (appId <= 0) {
        p.friend_game_played_app.erase(static_cast<uint64_t>(steamId64));
    } else {
        p.friend_game_played_app[static_cast<uint64_t>(steamId64)] =
            static_cast<uint32_t>(appId);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendPersonaName(
        JNIEnv* env, jclass /*cls*/, jlong steamId64, jstring jname) {
    if (steamId64 == 0) return;
    auto& p = lsc::pushed();
    std::string name = jstr(env, jname);
    bool changed = false;
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        const uint64_t sid = static_cast<uint64_t>(steamId64);
        if (name.empty()) {
            changed = (p.friend_persona_names.erase(sid) > 0);
        } else {
            auto it = p.friend_persona_names.find(sid);
            changed = (it == p.friend_persona_names.end() || it->second != name);
            p.friend_persona_names[sid] = std::move(name);
        }
    }
    if (changed) {
        emit_persona_state_change(static_cast<uint64_t>(steamId64),
                                  cb::kPersonaChangeName);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetLaunchCommandLine(
        JNIEnv* env, jclass /*cls*/, jstring jcli) {
    std::string cl = jstr(env, jcli);
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.launch_command_line = std::move(cl);
    WN_LOGI("set_launch_command_line(\"%s\")", p.launch_command_line.c_str());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppFamilyShared(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean familyShared) {
    lsc::pushed().app_is_family_shared.store(familyShared == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetEncryptedAppTicket(
        JNIEnv* env, jclass /*cls*/, jint appId, jbyteArray body, jint eresult) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    if (!body) {
        p.encrypted_app_tickets.erase(static_cast<uint32_t>(appId));
    } else {
        jsize n = env->GetArrayLength(body);
        if (n <= 0) {
            p.encrypted_app_tickets.erase(static_cast<uint32_t>(appId));
        } else {
            std::vector<uint8_t> buf(n);
            env->GetByteArrayRegion(body, 0, n, reinterpret_cast<jbyte*>(buf.data()));
            p.encrypted_app_tickets[static_cast<uint32_t>(appId)] = std::move(buf);
        }
    }
    p.encrypted_app_ticket_eresult.store(static_cast<int32_t>(eresult));
    WN_LOGI("set_encrypted_app_ticket: app=%d bytes=%d eresult=%d",
            appId, body ? env->GetArrayLength(body) : 0, eresult);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeReportLogonFailure(
        JNIEnv* /*env*/, jclass /*cls*/, jint eresult, jboolean stillRetrying) {
    cb::SteamServerConnectFailure payload{};
    payload.m_eResult        = static_cast<int32_t>(eresult);
    payload.m_bStillRetrying = (stillRetrying == JNI_TRUE);
    lsc::push_callback(lsc::state().user.load(),
                       cb::kSteamServerConnectFailure,
                       &payload, sizeof(payload));
    WN_LOGI("report_logon_failure: eresult=%d stillRetrying=%d "
            "(SteamServerConnectFailure_t emitted)",
            eresult, payload.m_bStillRetrying ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetServerRealTime(
        JNIEnv* /*env*/, jclass /*cls*/, jint serverRealTimeUnix) {
    auto& p = lsc::pushed();
    const auto now = std::chrono::steady_clock::now();
    const auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    p.server_realtime.store(static_cast<uint32_t>(serverRealTimeUnix));
    p.server_realtime_anchor_local_ms.store(static_cast<int64_t>(now_ms));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudEnabledForAccount(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean enabled) {
    lsc::pushed().cloud_enabled_account.store(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudEnabledForApp(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean enabled) {
    lsc::pushed().cloud_enabled_app.store(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudQuota(
        JNIEnv* /*env*/, jclass /*cls*/, jlong totalBytes, jlong availBytes) {
    auto& p = lsc::pushed();
    p.cloud_quota_total.store(static_cast<uint64_t>(totalBytes));
    p.cloud_quota_available.store(static_cast<uint64_t>(availBytes));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudFiles(
        JNIEnv* env, jclass /*cls*/, jobjectArray names, jintArray sizes,
        jlongArray timestamps) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.cloud_files.clear();
    if (!names || !sizes || !timestamps) return;
    jsize n  = env->GetArrayLength(names);
    jsize ns = env->GetArrayLength(sizes);
    jsize nt = env->GetArrayLength(timestamps);
    if (n <= 0 || n != ns || n != nt) return;
    jint*  sbuf = env->GetIntArrayElements(sizes, nullptr);
    jlong* tbuf = env->GetLongArrayElements(timestamps, nullptr);
    if (!sbuf || !tbuf) {
        if (sbuf) env->ReleaseIntArrayElements(sizes, sbuf, JNI_ABORT);
        if (tbuf) env->ReleaseLongArrayElements(timestamps, tbuf, JNI_ABORT);
        return;
    }
    p.cloud_files.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        auto jname = reinterpret_cast<jstring>(env->GetObjectArrayElement(names, i));
        std::string name;
        if (jname) {
            const char* c = env->GetStringUTFChars(jname, nullptr);
            if (c) { name = c; env->ReleaseStringUTFChars(jname, c); }
            env->DeleteLocalRef(jname);
        }
        if (name.empty()) continue;
        wn_libsteamclient::PushedState::CloudFileEntry e;
        e.name      = std::move(name);
        e.size      = static_cast<int32_t>(sbuf[i]);
        e.timestamp = static_cast<int64_t>(tbuf[i]);
        p.cloud_files.push_back(std::move(e));
    }
    env->ReleaseIntArrayElements(sizes, sbuf, JNI_ABORT);
    env->ReleaseLongArrayElements(timestamps, tbuf, JNI_ABORT);
    size_t pushed_count = p.cloud_files.size();
    uint32_t app = p.app_id.load();
    if (app != 0) {
        cb::RemoteStorageAppSyncedClient payload{};
        payload.m_nAppID         = app;
        payload.m_eResult        = pushed_count > 0 ? 1 : 2;  // OK / Fail
        payload.m_unNumDownloads = static_cast<int32_t>(pushed_count);
        lsc::push_callback(lsc::state().user.load(),
                           cb::kRemoteStorageAppSyncedClient,
                           &payload, sizeof(payload));
    }
    WN_LOGI("set_cloud_files: %zu entries app=%u (cb emitted=%d)",
            pushed_count, app, app != 0 ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAchievementSchema(
        JNIEnv* env, jclass /*cls*/, jobjectArray apiNames,
        jobjectArray displayNames, jobjectArray descriptions,
        jobjectArray icons, jbooleanArray hidden) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.achievements.clear();
    p.achievement_index.clear();
    p.dirty_stats_int.clear();
    p.dirty_stats_float.clear();
    if (!apiNames) { p.stats_ready.store(true); return; }
    jsize n  = env->GetArrayLength(apiNames);
    jsize nd = displayNames ? env->GetArrayLength(displayNames) : 0;
    jsize nx = descriptions ? env->GetArrayLength(descriptions) : 0;
    jsize ni = icons        ? env->GetArrayLength(icons)        : 0;
    jsize nh = hidden       ? env->GetArrayLength(hidden)       : 0;
    if (n <= 0) { p.stats_ready.store(true); return; }
    jboolean* hbuf = (hidden && nh == n) ? env->GetBooleanArrayElements(hidden, nullptr) : nullptr;
    p.achievements.reserve(n);
    p.achievement_index.reserve(n);
    auto read = [&](jobjectArray arr, jsize len, jsize i) -> std::string {
        if (!arr || i >= len) return {};
        auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(arr, i));
        if (!js) return {};
        const char* c = env->GetStringUTFChars(js, nullptr);
        std::string out = c ? c : "";
        if (c) env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
        return out;
    };
    for (jsize i = 0; i < n; ++i) {
        wn_libsteamclient::PushedState::AchievementEntry e;
        e.api_name     = read(apiNames,     n,  i);
        if (e.api_name.empty()) continue;
        std::string dn = read(displayNames, nd, i);
        std::string ds = read(descriptions, nx, i);
        if (!dn.empty()) e.display_names.emplace("english", std::move(dn));
        if (!ds.empty()) e.descriptions.emplace("english", std::move(ds));
        e.icon         = read(icons,        ni, i);
        e.hidden       = hbuf && hbuf[i] == JNI_TRUE;
        e.icon_handle  = static_cast<int32_t>(p.achievements.size()) + 1;  // non-zero
        p.achievement_index.emplace(e.api_name, p.achievements.size());
        p.achievements.push_back(std::move(e));
    }
    if (hbuf) env->ReleaseBooleanArrayElements(hidden, hbuf, JNI_ABORT);
    p.stats_ready.store(true);
    size_t pushed_count = p.achievements.size();
    cb::UserStatsReceived payload{};
    payload.m_nGameID      = static_cast<uint64_t>(p.app_id.load());
    payload.m_eResult      = pushed_count > 0 ? 1 : 2;  // 1=k_EResultOK, 2=k_EResultFail
    payload.m_steamIDUser  = p.steam_id.load();
    int h_user = lsc::state().user.load();
    lsc::push_callback(h_user, cb::kUserStatsReceived, &payload, sizeof(payload));
    WN_LOGI("set_achievement_schema: %zu entries (UserStatsReceived_t emitted "
            "user=%d game=%llu eresult=%d)",
            pushed_count, h_user,
            static_cast<unsigned long long>(payload.m_nGameID),
            payload.m_eResult);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetStatIds(
        JNIEnv* env, jclass /*cls*/, jobjectArray jNames, jintArray jIds) {
    if (!jNames || !jIds) return;
    jsize n = env->GetArrayLength(jNames);
    if (n <= 0 || env->GetArrayLength(jIds) != n) return;
    jint* ids = env->GetIntArrayElements(jIds, nullptr);
    auto& p = lsc::pushed();
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        p.stat_name_to_id.clear();
        for (jsize i = 0; i < n; ++i) {
            jstring js = static_cast<jstring>(env->GetObjectArrayElement(jNames, i));
            if (!js) continue;
            const char* c = env->GetStringUTFChars(js, nullptr);
            if (c && *c && ids[i] >= 0) {
                p.stat_name_to_id[c] = static_cast<uint32_t>(ids[i]);
            }
            if (c) env->ReleaseStringUTFChars(js, c);
            env->DeleteLocalRef(js);
        }
    }
    env->ReleaseIntArrayElements(jIds, ids, JNI_ABORT);
    WN_LOGI("set_stat_ids: %d entries", n);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAchievementBlockBits(
        JNIEnv* env, jclass /*cls*/, jobjectArray apiNames,
        jintArray blockIds, jintArray bitIndices) {
    if (!apiNames || !blockIds || !bitIndices) return;
    jsize n = env->GetArrayLength(apiNames);
    if (n <= 0) return;
    if (env->GetArrayLength(blockIds) != n ||
        env->GetArrayLength(bitIndices) != n) {
        WN_LOGI("set_achievement_block_bits: array length mismatch (n=%d) — ignoring", n);
        return;
    }
    jint* blocks = env->GetIntArrayElements(blockIds, nullptr);
    jint* bits   = env->GetIntArrayElements(bitIndices, nullptr);
    auto& p = lsc::pushed();
    size_t applied = 0;
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        for (jsize i = 0; i < n; ++i) {
            jstring js = static_cast<jstring>(env->GetObjectArrayElement(apiNames, i));
            if (!js) continue;
            const char* c = env->GetStringUTFChars(js, nullptr);
            if (!c) { env->DeleteLocalRef(js); continue; }
            auto it = p.achievement_index.find(c);
            if (it != p.achievement_index.end() && it->second < p.achievements.size()) {
                auto& ach = p.achievements[it->second];
                ach.block_id  = blocks[i];
                ach.bit_index = bits[i];
                ++applied;
            }
            env->ReleaseStringUTFChars(js, c);
            env->DeleteLocalRef(js);
        }
    }
    env->ReleaseIntArrayElements(blockIds, blocks, JNI_ABORT);
    env->ReleaseIntArrayElements(bitIndices, bits, JNI_ABORT);
    WN_LOGI("set_achievement_block_bits: applied %zu / %d", applied, n);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeAddAchievementLocale(
        JNIEnv* env, jclass /*cls*/, jstring jApiName, jstring jLocale,
        jstring jDisplayName, jstring jDescription) {
    if (!jApiName || !jLocale) return;
    std::string api    = jstr(env, jApiName);
    std::string locale = jstr(env, jLocale);
    std::string dn     = jstr(env, jDisplayName);
    std::string ds     = jstr(env, jDescription);
    if (api.empty() || locale.empty() || (dn.empty() && ds.empty())) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto it = p.achievement_index.find(api);
    if (it == p.achievement_index.end()) return;
    auto& a = p.achievements[it->second];
    if (!dn.empty()) a.display_names[locale] = std::move(dn);
    if (!ds.empty()) a.descriptions[locale]  = std::move(ds);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAchievementProgress(
        JNIEnv* env, jclass /*cls*/, jstring jApiName, jboolean achieved,
        jint unlockTimeUnix) {
    if (!jApiName) return;
    std::string name = jstr(env, jApiName);
    if (name.empty()) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto it = p.achievement_index.find(name);
    if (it == p.achievement_index.end()) return;
    auto& a = p.achievements[it->second];
    a.achieved    = (achieved == JNI_TRUE);
    a.unlock_time = static_cast<uint32_t>(unlockTimeUnix);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetStatInt(
        JNIEnv* env, jclass /*cls*/, jstring jName, jint value) {
    if (!jName) return;
    std::string name = jstr(env, jName);
    if (name.empty()) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.stats_int[std::move(name)] = static_cast<int32_t>(value);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetStatFloat(
        JNIEnv* env, jclass /*cls*/, jstring jName, jfloat value) {
    if (!jName) return;
    std::string name = jstr(env, jName);
    if (name.empty()) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    p.stats_float[std::move(name)] = value;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticAchievementCount(
        JNIEnv* /*env*/, jclass /*cls*/) {
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    return static_cast<jint>(lsc::pushed().achievements.size());
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCallbackDepth(
        JNIEnv* /*env*/, jclass /*cls*/) {
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.callback_mu);
    return static_cast<jint>(s.callback_queue.size());
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticTcpAccepted(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return static_cast<jint>(lsc::accepted_connection_count());
}

extern "C" void* wn_get_isteam_utils();
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUtilsGetAPICallResult(
        JNIEnv* /*env*/, jclass /*cls*/, jint iCallback, jint eresultIn) {
    uint64_t h = lsc::alloc_api_call_handle();
    int32_t body = eresultIn;
    lsc::push_call_result(h, static_cast<int>(iCallback),
                          &body, sizeof(body), /*io_failure=*/false);
    void* obj = wn_get_isteam_utils();
    if (!obj) return -1;
    long* vt = *reinterpret_cast<long**>(obj);
    using GetResultFn = bool (*)(void*, uint64_t, void*, int, int, bool*);
    auto getr = reinterpret_cast<GetResultFn>(vt[13]);
    int32_t out = -1;
    bool failed = false;
    bool ok = getr(obj, h, &out, sizeof(out), static_cast<int>(iCallback), &failed);
    return ok ? out : -1;
}

extern "C" void* wn_get_isteam_user();
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestEncryptedAppTicket(
        JNIEnv* env, jclass /*cls*/, jbyteArray outBody) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using ReqFn = uint64_t (*)(void*, void*, int);
    auto req = reinterpret_cast<ReqFn>(vt[21]);
    uint64_t h = req(obj, nullptr, 0);
    using GetFn = bool (*)(void*, void*, int, uint32_t*);
    auto get = reinterpret_cast<GetFn>(vt[22]);
    uint8_t scratch[128] = {0};
    uint32_t actual = 0;
    bool ok = get(obj, scratch, sizeof(scratch), &actual);
    if (ok && outBody && env->GetArrayLength(outBody) >= static_cast<jsize>(actual)) {
        env->SetByteArrayRegion(outBody, 0, static_cast<jsize>(actual),
                                reinterpret_cast<const jbyte*>(scratch));
    }
    return static_cast<jlong>(h);
}

extern "C" void* wn_get_isteam_user();
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetAuthTicket(
        JNIEnv* env, jclass /*cls*/, jbyteArray jbuf) {
    void* obj = wn_get_isteam_user();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using GetTicketFn = uint64_t (*)(void*, void*, int, uint32_t*, const void*);
    auto get_ticket = reinterpret_cast<GetTicketFn>(vt[13]);
    uint8_t  scratch[64] = {0};
    uint32_t actual = 0;
    uint64_t h = get_ticket(obj, scratch, sizeof(scratch), &actual, nullptr);
    if (jbuf && env->GetArrayLength(jbuf) >= static_cast<jsize>(actual)) {
        env->SetByteArrayRegion(jbuf, 0, static_cast<jsize>(actual),
                                reinterpret_cast<const jbyte*>(scratch));
    }
    return static_cast<jint>(h);
}

extern "C" __attribute__((visibility("default")))
void SteamAPI_RunCallbacks(void);

namespace {

struct DiagnosticCallback {
    void**       vptr;
    uint8_t      flags;
    uint8_t      _pad0[3];
    int32_t      iCallback;
    int32_t      runs;          // bumped on each Run()
    int32_t      last_user;     // copied from msg payload[0] (m_nGameID low 32)
    int32_t      last_eresult;  // copied from msg payload offset 8 (m_eResult)
};

void diagnostic_run(DiagnosticCallback* self, void* payload) {
    if (!self) return;
    ++self->runs;
    if (payload) {
        self->last_user    = static_cast<int32_t>(
            *reinterpret_cast<const uint32_t*>(payload));
        self->last_eresult = *reinterpret_cast<const int32_t*>(
            static_cast<const uint8_t*>(payload) + 8);
    }
}

void diagnostic_run_result(DiagnosticCallback*, void*, bool, uint64_t) {}
int  diagnostic_get_size(DiagnosticCallback*) { return 24; }  // sizeof(UserStatsReceived_t)

void* const kDiagnosticVtable[] = {
    reinterpret_cast<void*>(&diagnostic_run),
    reinterpret_cast<void*>(&diagnostic_run_result),
    reinterpret_cast<void*>(&diagnostic_get_size),
};

DiagnosticCallback g_diagnostic_cb;
bool g_diagnostic_registered = false;

}  // namespace

namespace {
struct DiagnosticCallResultCb {
    void**       vptr;
    uint8_t      flags;
    uint8_t      _pad0[3];
    int32_t      iCallback;
    int32_t      runs;
    uint64_t     last_h_call;
    int32_t      last_io_failure;
    int32_t      last_eresult;
};

void diag_cr_run(DiagnosticCallResultCb*, void*) {}  // slot 0 unused for CCallResult
void diag_cr_run_result(DiagnosticCallResultCb* self, void* payload,
                        bool ioFailure, uint64_t hCall) {
    if (!self) return;
    ++self->runs;
    self->last_h_call    = hCall;
    self->last_io_failure = ioFailure ? 1 : 0;
    if (payload) {
        self->last_eresult = *reinterpret_cast<const int32_t*>(payload);
    }
}
int  diag_cr_get_size(DiagnosticCallResultCb*) { return 0; }

void* const kDiagnosticCallResultVtable[] = {
    reinterpret_cast<void*>(&diag_cr_run),
    reinterpret_cast<void*>(&diag_cr_run_result),
    reinterpret_cast<void*>(&diag_cr_get_size),
};

DiagnosticCallResultCb g_diag_cr_cb;
}  // namespace

extern "C" __attribute__((visibility("default")))
void SteamAPI_RunCallbacks(void);
extern "C" __attribute__((visibility("default")))
void SteamAPI_RegisterCallResult(void*, uint64_t);

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticPushAndDrainCallResult(
        JNIEnv* /*env*/, jclass /*cls*/, jint callbackId, jint eresult) {
    uint64_t h_call = lsc::alloc_api_call_handle();
    int32_t  body_eresult = eresult;
    lsc::push_call_result(h_call, static_cast<int>(callbackId),
                          &body_eresult, sizeof(body_eresult),
                          /*io_failure=*/false);

    g_diag_cr_cb.vptr      = const_cast<void**>(kDiagnosticCallResultVtable);
    g_diag_cr_cb.flags     = 0;
    g_diag_cr_cb.iCallback = static_cast<int32_t>(callbackId);
    g_diag_cr_cb.runs           = 0;
    g_diag_cr_cb.last_h_call    = 0;
    g_diag_cr_cb.last_io_failure = -1;
    g_diag_cr_cb.last_eresult   = 0;
    SteamAPI_RegisterCallResult(&g_diag_cr_cb, h_call);

    SteamAPI_RunCallbacks();

    return (static_cast<jlong>(g_diag_cr_cb.runs) << 32) |
           (static_cast<jlong>(g_diag_cr_cb.last_eresult) & 0xFFFFFFFFL);
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRegisterAndDrain(
        JNIEnv* /*env*/, jclass /*cls*/, jint iCallback) {
    g_diagnostic_cb.vptr      = const_cast<void**>(kDiagnosticVtable);
    g_diagnostic_cb.flags     = 0;
    g_diagnostic_cb.iCallback = static_cast<int32_t>(iCallback);
    g_diagnostic_cb.runs         = 0;
    g_diagnostic_cb.last_user    = 0;
    g_diagnostic_cb.last_eresult = 0;
    if (!g_diagnostic_registered) {
        lsc::register_callback(&g_diagnostic_cb, static_cast<int>(iCallback));
        g_diagnostic_registered = true;
    }
    SteamAPI_RunCallbacks();
    return g_diagnostic_cb.runs;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticShutdownPipe(
        JNIEnv* /*env*/, jclass /*cls*/) {
    int pipe = lsc::state().pipe.load();
    if (pipe == 0) return JNI_FALSE;
    namespace cb = wn_libsteamclient::callbacks;
    cb::SteamShutdown sd{};
    lsc::push_callback(lsc::state().user.load(),
                       cb::kSteamShutdown, &sd, 0);
    bool ok = lsc::release_pipe(pipe);
    (void)lsc::alloc_pipe();
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" void* wn_get_isteam_user_stats();
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticStoreStats(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_user_stats();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using StoreFn = bool (*)(void*);
    auto fn = reinterpret_cast<StoreFn>(vt[10]);
    return fn(obj) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetAchievement(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    if (!jName) return JNI_FALSE;
    void* obj = wn_get_isteam_user_stats();
    if (!obj) return JNI_FALSE;
    std::string name = jstr(env, jName);
    long* vt = *reinterpret_cast<long**>(obj);
    using SetFn = bool (*)(void*, const char*);
    auto fn = reinterpret_cast<SetFn>(vt[7]);
    return fn(obj, name.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticIndicateAchievementProgress(
        JNIEnv* env, jclass /*cls*/, jstring jName, jint cur, jint max) {
    if (!jName) return JNI_FALSE;
    void* obj = wn_get_isteam_user_stats();
    if (!obj) return JNI_FALSE;
    std::string name = jstr(env, jName);
    long* vt = *reinterpret_cast<long**>(obj);
    using IndFn = bool (*)(void*, const char*, uint32_t, uint32_t);
    auto fn = reinterpret_cast<IndFn>(vt[13]);
    return fn(obj, name.c_str(),
              static_cast<uint32_t>(cur),
              static_cast<uint32_t>(max)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendRichPresence(
        JNIEnv* env, jclass /*cls*/, jlong jSteamId, jstring jKey, jstring jValue) {
    uint64_t steam_id = static_cast<uint64_t>(jSteamId);
    if (steam_id == 0 || !jKey) return;
    std::string key   = jstr(env, jKey);
    std::string value = jstr(env, jValue);
    if (key.empty()) return;
    auto& p = lsc::pushed();
    {
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto& rp = p.rich_presence[steam_id];
        auto it = std::find_if(rp.begin(), rp.end(),
            [&](const auto& kv) { return kv.first == key; });
        if (value.empty()) {
            if (it != rp.end()) rp.erase(it);
        } else if (it == rp.end()) {
            rp.emplace_back(std::move(key), std::move(value));
        } else {
            it->second = std::move(value);
        }
    }
    cb::FriendRichPresenceUpdate ev{};
    ev.m_steamIDFriend = steam_id;
    ev.m_nAppID        = p.app_id.load();
    lsc::push_callback(lsc::state().user.load(),
                       cb::kFriendRichPresenceUpdate,
                       &ev, sizeof(ev));
}

extern "C" void* wn_get_isteam_friends();
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetPersonaName(
        JNIEnv* env, jclass /*cls*/, jstring jName) {
    if (!jName) return 0;
    void* obj = wn_get_isteam_friends();
    if (!obj) return 0;
    std::string name = jstr(env, jName);
    long* vt = *reinterpret_cast<long**>(obj);
    using SetFn = uint64_t (*)(void*, const char*);
    auto fn = reinterpret_cast<SetFn>(vt[1]);
    return static_cast<jlong>(fn(obj, name.c_str()));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestFriendRichPresence(
        JNIEnv* /*env*/, jclass /*cls*/, jlong jSteamId) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return;
    long* vt = *reinterpret_cast<long**>(obj);
    using ReqFn = void (*)(void*, uint64_t);
    auto fn = reinterpret_cast<ReqFn>(vt[48]);
    fn(obj, static_cast<uint64_t>(jSteamId));
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestUserInformation(
        JNIEnv* /*env*/, jclass /*cls*/, jlong jSteamId, jboolean jNameOnly) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using ReqFn = bool (*)(void*, uint64_t, bool);
    auto fn = reinterpret_cast<ReqFn>(vt[37]);
    return fn(obj, static_cast<uint64_t>(jSteamId), jNameOnly == JNI_TRUE)
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectLogonState(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean jLoggedOn) {
    wn_cm_bridge_inject_test_logon_state(jLoggedOn == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectLicenseList(
        JNIEnv* env, jclass /*cls*/, jintArray jPackageIds, jintArray jOwnerIds) {
    if (!jPackageIds) {
        wn_cm_bridge_inject_test_license_list(nullptr, 0);
        return;
    }
    jsize n = env->GetArrayLength(jPackageIds);
    if (n <= 0) {
        wn_cm_bridge_inject_test_license_list(nullptr, 0);
        return;
    }
    jint* pkg_ids = env->GetIntArrayElements(jPackageIds, nullptr);
    jint* own_ids = jOwnerIds ? env->GetIntArrayElements(jOwnerIds, nullptr) : nullptr;
    if (!pkg_ids) return;
    std::vector<WnCmLicenseEntry> entries;
    entries.reserve(static_cast<size_t>(n));
    uint32_t now = static_cast<uint32_t>(::time(nullptr));
    for (jsize i = 0; i < n; ++i) {
        WnCmLicenseEntry e{};
        e.package_id   = static_cast<uint32_t>(pkg_ids[i]);
        e.owner_id     = (own_ids && i < env->GetArrayLength(jOwnerIds))
                            ? static_cast<uint32_t>(own_ids[i]) : 0u;
        e.time_created = now;
        entries.push_back(e);
    }
    wn_cm_bridge_inject_test_license_list(entries.data(), entries.size());
    env->ReleaseIntArrayElements(jPackageIds, pkg_ids, JNI_ABORT);
    if (own_ids) env->ReleaseIntArrayElements(jOwnerIds, own_ids, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetLicenseOwner(
        JNIEnv* /*env*/, jclass /*cls*/, jint jPackageId) {
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto it = p.licenses.find(static_cast<uint32_t>(jPackageId));
    if (it == p.licenses.end()) return -1;
    return static_cast<jint>(it->second.owner_id);
}

extern "C" void* wn_get_isteam_apps();
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetEarliestPurchaseUnixTime(
        JNIEnv* /*env*/, jclass /*cls*/, jint jAppId) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint32_t (*)(void*, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[8]);
    return static_cast<jint>(fn(obj, static_cast<uint32_t>(jAppId)));
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsSubscribedFromFreeWeekend(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[9]);
    return fn(obj) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsSubscribedFromFamilySharing(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[27]);
    return fn(obj) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUpdateAvgRateStat(
        JNIEnv* env, jclass /*cls*/, jstring jName,
        jfloat jCountThisSession, jdouble jSessionLength) {
    if (!jName) return 0.0f;
    void* obj = wn_get_isteam_user_stats();
    if (!obj) return 0.0f;
    std::string name = jstr(env, jName);
    long* vt = *reinterpret_cast<long**>(obj);
    using UpdateFn = bool (*)(void*, const char*, float, double);
    auto upd = reinterpret_cast<UpdateFn>(vt[5]);
    if (!upd(obj, name.c_str(), jCountThisSession, jSessionLength)) return 0.0f;
    using GetFn = bool (*)(void*, const char*, float*);
    auto get = reinterpret_cast<GetFn>(vt[2]);
    float out = 0.0f;
    get(obj, name.c_str(), &out);
    return static_cast<jfloat>(out);
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetAppOwner(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = uint64_t (*)(void*);
    auto fn = reinterpret_cast<Fn>(vt[20]);
    return static_cast<jlong>(fn(obj));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectTrialLicense(
        JNIEnv* /*env*/, jclass /*cls*/, jint jPackageId,
        jint jMinuteLimit, jint jMinutesUsed) {
    if (jPackageId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto& slot = p.licenses[static_cast<uint32_t>(jPackageId)];
    slot.package_id    = static_cast<uint32_t>(jPackageId);
    slot.minute_limit  = jMinuteLimit;
    slot.minutes_used  = jMinutesUsed;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsDlcInstalled(
        JNIEnv* /*env*/, jclass /*cls*/, jint jAppId) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return JNI_FALSE;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint32_t);
    auto fn = reinterpret_cast<Fn>(vt[7]);
    return fn(obj, static_cast<uint32_t>(jAppId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsTimedTrial(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_apps();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using Fn = bool (*)(void*, uint32_t*, uint32_t*);
    auto fn = reinterpret_cast<Fn>(vt[28]);
    uint32_t allowed = 0, played = 0;
    bool ok = fn(obj, &allowed, &played);
    if (!ok) return 0;
    return (1LL << 63) |
           (static_cast<int64_t>(allowed) << 32) |
           static_cast<int64_t>(played);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppSourcePackages(
        JNIEnv* env, jclass /*cls*/, jint appId, jintArray packageIds) {
    if (appId <= 0) return;
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto key = static_cast<uint32_t>(appId);
    if (!packageIds) {
        p.app_source_packages.erase(key);
        return;
    }
    jsize n = env->GetArrayLength(packageIds);
    if (n <= 0) {
        p.app_source_packages.erase(key);
        return;
    }
    jint* arr = env->GetIntArrayElements(packageIds, nullptr);
    if (!arr) return;
    std::vector<uint32_t> pkgs;
    pkgs.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        if (arr[i] > 0) pkgs.push_back(static_cast<uint32_t>(arr[i]));
    }
    env->ReleaseIntArrayElements(packageIds, arr, JNI_ABORT);
    if (pkgs.empty()) {
        p.app_source_packages.erase(key);
    } else {
        p.app_source_packages[key] = std::move(pkgs);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectFriendsList(
        JNIEnv* env, jclass /*cls*/, jlongArray jSids) {
    if (!jSids) {
        wn_cm_bridge_inject_test_friends_list(nullptr, 0);
        return;
    }
    jsize n = env->GetArrayLength(jSids);
    if (n <= 0) {
        wn_cm_bridge_inject_test_friends_list(nullptr, 0);
        return;
    }
    jlong* arr = env->GetLongArrayElements(jSids, nullptr);
    if (!arr) return;
    static_assert(sizeof(jlong) == sizeof(uint64_t), "jlong/uint64 size mismatch");
    wn_cm_bridge_inject_test_friends_list(
        reinterpret_cast<const uint64_t*>(arr), static_cast<size_t>(n));
    env->ReleaseLongArrayElements(jSids, arr, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectPersonaEvent(
        JNIEnv* env, jclass /*cls*/,
        jlong jSteamId,
        jint  jPersonaState,
        jint  jGameAppId,
        jstring jName,
        jbyteArray jAvatarHash,
        jobjectArray jRpKeys,
        jobjectArray jRpValues) {
    WnCmPersonaEvent ev{};
    ev.sid              = static_cast<uint64_t>(jSteamId);
    ev.persona_state    = (jPersonaState < 0) ? UINT32_MAX
                                              : static_cast<uint32_t>(jPersonaState);
    ev.game_played_app  = (jGameAppId <= 0) ? 0 : static_cast<uint32_t>(jGameAppId);

    std::string name_storage;
    if (jName) {
        name_storage = jstr(env, jName);
        if (!name_storage.empty()) ev.name = name_storage.c_str();
    }

    std::vector<uint8_t> hash_storage;
    if (jAvatarHash) {
        jsize n = env->GetArrayLength(jAvatarHash);
        if (n > 0) {
            hash_storage.resize(static_cast<size_t>(n));
            env->GetByteArrayRegion(jAvatarHash, 0, n,
                reinterpret_cast<jbyte*>(hash_storage.data()));
            ev.avatar_hash     = hash_storage.data();
            ev.avatar_hash_len = hash_storage.size();
        }
    }

    std::vector<std::string> key_storage, value_storage;
    std::vector<WnCmRichPresenceKV> rp_kv;
    if (jRpKeys && jRpValues) {
        jsize kn = env->GetArrayLength(jRpKeys);
        jsize vn = env->GetArrayLength(jRpValues);
        jsize count = std::min(kn, vn);
        key_storage.reserve(count);
        value_storage.reserve(count);
        rp_kv.reserve(count);
        for (jsize i = 0; i < count; ++i) {
            auto k_obj = reinterpret_cast<jstring>(env->GetObjectArrayElement(jRpKeys, i));
            auto v_obj = reinterpret_cast<jstring>(env->GetObjectArrayElement(jRpValues, i));
            key_storage.push_back(jstr(env, k_obj));
            value_storage.push_back(jstr(env, v_obj));
            if (k_obj) env->DeleteLocalRef(k_obj);
            if (v_obj) env->DeleteLocalRef(v_obj);
            rp_kv.push_back({key_storage.back().c_str(),
                             value_storage.back().c_str()});
        }
        if (!rp_kv.empty()) {
            ev.rp_pairs = rp_kv.data();
            ev.rp_count = rp_kv.size();
        }
    }

    wn_cm_bridge_dispatch_persona(&ev);
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectOwnershipTicket(
        JNIEnv* env, jclass /*cls*/, jint jAppId, jbyteArray jBytes) {
    if (jAppId <= 0 || !jBytes) return JNI_FALSE;
    jsize n = env->GetArrayLength(jBytes);
    if (n <= 0) return JNI_FALSE;
    std::vector<uint8_t> tmp(static_cast<size_t>(n));
    env->GetByteArrayRegion(jBytes, 0, n, reinterpret_cast<jbyte*>(tmp.data()));
    return wn_cm_bridge_inject_test_ownership_ticket(
        static_cast<uint32_t>(jAppId), tmp.data(), tmp.size())
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetCachedOwnershipTicket(
        JNIEnv* env, jclass /*cls*/, jint jAppId, jbyteArray jOut) {
    if (jAppId <= 0) return 0;
    size_t out_len = 0;
    if (!jOut) {
        wn_cm_get_cached_app_ownership_ticket(static_cast<uint32_t>(jAppId),
                                              nullptr, 0, &out_len);
        return static_cast<jint>(out_len);
    }
    jsize max = env->GetArrayLength(jOut);
    std::vector<uint8_t> tmp(static_cast<size_t>(max));
    bool ok = wn_cm_get_cached_app_ownership_ticket(
        static_cast<uint32_t>(jAppId),
        tmp.data(), static_cast<size_t>(max), &out_len);
    if (!ok) return static_cast<jint>(out_len);  // 0 = miss; >0 = need bigger buf
    env->SetByteArrayRegion(jOut, 0, static_cast<jsize>(out_len),
                            reinterpret_cast<jbyte*>(tmp.data()));
    return static_cast<jint>(out_len);
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestUserInfoBulk(
        JNIEnv* env, jclass /*cls*/, jlongArray jSids, jint jFlags) {
    if (!jSids) return JNI_FALSE;
    jsize n = env->GetArrayLength(jSids);
    if (n <= 0) return JNI_FALSE;
    jlong* arr = env->GetLongArrayElements(jSids, nullptr);
    if (!arr) return JNI_FALSE;
    static_assert(sizeof(jlong) == sizeof(uint64_t), "jlong/uint64 size mismatch");
    bool ok = wn_cm_request_user_info_bulk(reinterpret_cast<const uint64_t*>(arr),
                                            static_cast<size_t>(n),
                                            static_cast<int32_t>(jFlags));
    env->ReleaseLongArrayElements(jSids, arr, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticClearRichPresence(
        JNIEnv* /*env*/, jclass /*cls*/) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return;
    long* vt = *reinterpret_cast<long**>(obj);
    using ClrFn = void (*)(void*);
    auto fn = reinterpret_cast<ClrFn>(vt[44]);
    fn(obj);
}

extern "C" void* wn_get_isteam_friends();
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetRichPresence(
        JNIEnv* env, jclass /*cls*/, jstring jKey, jstring jValue) {
    void* obj = wn_get_isteam_friends();
    if (!obj || !jKey) return JNI_FALSE;
    std::string key   = jstr(env, jKey);
    std::string value = jstr(env, jValue);
    long* vt = *reinterpret_cast<long**>(obj);
    using SetFn = bool (*)(void*, const char*, const char*);
    auto fn = reinterpret_cast<SetFn>(vt[43]);
    return fn(obj, key.c_str(),
              value.empty() ? nullptr : value.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/, jlong jSteamId) {
    uint64_t sid = static_cast<uint64_t>(jSteamId);
    auto& p = lsc::pushed();
    std::lock_guard<std::mutex> lk(lsc::state_mutex());
    auto it = p.friend_persona_states.find(sid);
    if (it == p.friend_persona_states.end()) return -1;
    return static_cast<jint>(it->second);
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRichPresenceKeyCount(
        JNIEnv* /*env*/, jclass /*cls*/, jlong jSteamId) {
    void* obj = wn_get_isteam_friends();
    if (!obj) return 0;
    long* vt = *reinterpret_cast<long**>(obj);
    using CountFn = int (*)(void*, uint64_t);
    auto fn = reinterpret_cast<CountFn>(vt[46]);
    return fn(obj, static_cast<uint64_t>(jSteamId));
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendAvatarHash(
        JNIEnv* env, jclass /*cls*/, jlong jSteamId, jbyteArray jHash) {
    uint64_t sid = static_cast<uint64_t>(jSteamId);
    if (sid == 0) return;
    bool changed = false;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto& slot = p.friend_avatar_hashes[sid];
        if (!jHash) {
            if (!slot.empty()) { slot.clear(); changed = true; }
        } else {
            jsize n = env->GetArrayLength(jHash);
            std::vector<uint8_t> bytes(static_cast<size_t>(n));
            if (n > 0) {
                env->GetByteArrayRegion(jHash, 0, n,
                    reinterpret_cast<jbyte*>(bytes.data()));
            }
            if (bytes != slot) {
                slot = std::move(bytes);
                changed = true;
            }
        }
    }
    if (!changed) return;
    cb::PersonaStateChange payload{};
    payload.m_ulSteamID    = sid;
    payload.m_nChangeFlags = cb::kPersonaChangeAvatar;
    lsc::push_callback(lsc::state().user.load(),
                       cb::kPersonaStateChange,
                       &payload, sizeof(payload));
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendAvatarHashHex(
        JNIEnv* env, jclass /*cls*/, jlong jSteamId) {
    uint64_t sid = static_cast<uint64_t>(jSteamId);
    std::string hex;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        auto it = p.friend_avatar_hashes.find(sid);
        if (it != p.friend_avatar_hashes.end()) {
            static constexpr char kHex[] = "0123456789abcdef";
            hex.reserve(it->second.size() * 2);
            for (uint8_t b : it->second) {
                hex.push_back(kHex[(b >> 4) & 0xF]);
                hex.push_back(kHex[b & 0xF]);
            }
        }
    }
    return env->NewStringUTF(hex.c_str());
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativePushFriendAvatar(
        JNIEnv* env, jclass /*cls*/,
        jlong jSteamId, jint jTier, jint jWidth, jint jHeight, jbyteArray jRgba) {
    if (jSteamId == 0 || jWidth <= 0 || jHeight <= 0 || !jRgba) return 0;
    if (jTier < 0 || jTier > 2) return 0;
    jsize n = env->GetArrayLength(jRgba);
    int expected = jWidth * jHeight * 4;
    if (n != expected) return 0;

    int32_t handle;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        handle = p.next_image_handle++;
        auto& img = p.image_registry[handle];
        img.width  = jWidth;
        img.height = jHeight;
        img.rgba.resize(static_cast<size_t>(n));
        env->GetByteArrayRegion(jRgba, 0, n,
            reinterpret_cast<jbyte*>(img.rgba.data()));
        auto& a = p.friend_avatars[static_cast<uint64_t>(jSteamId)];
        switch (jTier) {
            case 0: a.small  = handle; break;
            case 1: a.medium = handle; break;
            case 2: a.large  = handle; break;
        }
    }
    cb::AvatarImageLoaded ev{};
    ev.m_steamID = static_cast<uint64_t>(jSteamId);
    ev.m_iImage  = handle;
    ev.m_iWide   = jWidth;
    ev.m_iTall   = jHeight;
    lsc::push_callback(lsc::state().user.load(),
                       cb::kAvatarImageLoaded,
                       &ev, sizeof(ev));
    return handle;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetTieredAvatarSize(
        JNIEnv* /*env*/, jclass /*cls*/, jlong jSteamId, jint jTier) {
    if (jTier < 0 || jTier > 2) return 0;
    void* friends = wn_get_isteam_friends();
    if (!friends) return 0;
    int slot = 34 + jTier;  // 34=small, 35=medium, 36=large
    long* vt_f = *reinterpret_cast<long**>(friends);
    using GetAv = int (*)(void*, uint64_t);
    auto get_av = reinterpret_cast<GetAv>(vt_f[slot]);
    int handle = get_av(friends, static_cast<uint64_t>(jSteamId));
    if (handle <= 0) return 0;
    void* utils = wn_get_isteam_utils();
    if (!utils) return (static_cast<int64_t>(handle) << 32);
    long* vt_u = *reinterpret_cast<long**>(utils);
    using SizeFn = bool (*)(void*, int, uint32_t*, uint32_t*);
    auto fn_size = reinterpret_cast<SizeFn>(vt_u[5]);
    uint32_t w = 0, h = 0;
    if (!fn_size(utils, handle, &w, &h)) return (static_cast<int64_t>(handle) << 32);
    uint32_t lo = (w << 16) | (h & 0xFFFF);
    return (static_cast<int64_t>(handle) << 32) | lo;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetSmallAvatarSize(
        JNIEnv* /*env*/, jclass /*cls*/, jlong jSteamId) {
    void* friends = wn_get_isteam_friends();
    if (!friends) return 0;
    long* vt_f = *reinterpret_cast<long**>(friends);
    using GetAv = int (*)(void*, uint64_t);
    auto get_small = reinterpret_cast<GetAv>(vt_f[34]);
    int handle = get_small(friends, static_cast<uint64_t>(jSteamId));
    if (handle <= 0) return 0;
    void* utils = wn_get_isteam_utils();
    if (!utils) return (static_cast<int64_t>(handle) << 32);
    long* vt_u = *reinterpret_cast<long**>(utils);
    using SizeFn = bool (*)(void*, int, uint32_t*, uint32_t*);
    auto fn_size = reinterpret_cast<SizeFn>(vt_u[5]);
    uint32_t w = 0, h = 0;
    bool ok = fn_size(utils, handle, &w, &h);
    if (!ok) return (static_cast<int64_t>(handle) << 32);
    uint32_t lo = (w << 16) | (h & 0xFFFF);
    return (static_cast<int64_t>(handle) << 32) | lo;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetImageRGBA(
        JNIEnv* env, jclass /*cls*/, jint jHandle, jbyteArray jOut) {
    if (jHandle <= 0 || !jOut) return 0;
    void* utils = wn_get_isteam_utils();
    if (!utils) return 0;
    jsize n = env->GetArrayLength(jOut);
    std::vector<uint8_t> tmp(static_cast<size_t>(n));
    long* vt = *reinterpret_cast<long**>(utils);
    using RgbaFn = bool (*)(void*, int, uint8_t*, int);
    auto fn = reinterpret_cast<RgbaFn>(vt[6]);
    if (!fn(utils, jHandle, tmp.data(), n)) return 0;
    env->SetByteArrayRegion(jOut, 0, n, reinterpret_cast<jbyte*>(tmp.data()));
    return n;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetGameOverlayActive(
        JNIEnv* /*env*/, jclass /*cls*/, jboolean jActive) {
    bool active = (jActive == JNI_TRUE);
    auto& p = lsc::pushed();
    bool prev = p.overlay_active.exchange(active);
    if (prev == active) return;
    cb::GameOverlayActivated ev{};
    ev.m_bActive = active;
    lsc::push_callback(lsc::state().user.load(),
                       cb::kGameOverlayActivated,
                       &ev, sizeof(ev));
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativePollOverlayRequest(
        JNIEnv* env, jclass /*cls*/) {
    lsc::PushedState::OverlayRequest r;
    {
        auto& p = lsc::pushed();
        std::lock_guard<std::mutex> lk(lsc::state_mutex());
        if (p.overlay_request_queue.empty()) return nullptr;
        r = std::move(p.overlay_request_queue.front());
        p.overlay_request_queue.pop_front();
    }
    char buf[512];
    int n = std::snprintf(buf, sizeof(buf),
        "%s\x01%s\x01%llu\x01%u",
        r.kind.c_str(),
        r.arg1.c_str(),
        static_cast<unsigned long long>(r.sid),
        r.app_id);
    if (n <= 0) return nullptr;
    return env->NewStringUTF(buf);
}

}  // extern "C"
