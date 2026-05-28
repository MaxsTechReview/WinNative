
#pragma once

#include <cstdint>
#include <memory>

namespace wn_steam {
class CMClient;

void wn_cm_bridge_set_active(std::shared_ptr<CMClient> client);
void wn_cm_bridge_clear_active();

}  // namespace wn_steam

extern "C" {

__attribute__((visibility("default")))
bool wn_cm_set_persona_state(int32_t persona_state);

__attribute__((visibility("default")))
bool wn_cm_set_persona_name(const char* name, int32_t persona_state);

__attribute__((visibility("default")))
bool wn_cm_request_user_info(uint64_t steam_id, int32_t flags);

__attribute__((visibility("default")))
bool wn_cm_request_user_info_bulk(const uint64_t* sids, size_t count, int32_t flags);

__attribute__((visibility("default")))
bool wn_cm_get_cached_app_ownership_ticket(uint32_t app_id,
                                            uint8_t* out_buf,
                                            size_t max_len,
                                            size_t* out_len);

__attribute__((visibility("default")))
bool wn_cm_bridge_inject_test_ownership_ticket(uint32_t app_id,
                                                 const uint8_t* bytes,
                                                 size_t len);

__attribute__((visibility("default")))
bool wn_cm_notify_games_played(uint32_t app_id);

__attribute__((visibility("default")))
bool wn_cm_set_rich_presence(uint32_t app_id,
                              const char* const* keys,
                              const char* const* values,
                              size_t count);

__attribute__((visibility("default")))
bool wn_cm_store_user_stats(uint32_t app_id,
                              uint32_t crc_stats,
                              const uint32_t* stat_ids,
                              const uint32_t* stat_values,
                              size_t count);

struct WnCmRichPresenceKV {
    const char* key;
    const char* value;
};

struct WnCmPersonaEvent {
    uint64_t       sid;              // CSteamID64 (0 if message lacks one)
    uint32_t       persona_state;    // EPersonaState (0..7); UINT32_MAX = absent
    uint32_t       game_played_app;  // AppID; 0 = not in game
    const char*    name;             // UTF-8, null if absent
    const uint8_t* avatar_hash;      // SHA-1 (typically 20 bytes), null if absent
    size_t         avatar_hash_len;
    const WnCmRichPresenceKV* rp_pairs;
    size_t                    rp_count;
};

typedef void (*WnCmPersonaObserverFn)(const WnCmPersonaEvent*);

__attribute__((visibility("default")))
void wn_cm_bridge_register_persona_observer(WnCmPersonaObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_dispatch_persona(const WnCmPersonaEvent* ev);


typedef void (*WnCmLogonStateObserverFn)(bool logged_on);

__attribute__((visibility("default")))
void wn_cm_bridge_register_logon_state_observer(WnCmLogonStateObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_dispatch_logon_state(bool logged_on);

__attribute__((visibility("default")))
void wn_cm_bridge_inject_test_logon_state(bool logged_on);


typedef void (*WnCmFriendsListObserverFn)(const uint64_t* sids, size_t count);

__attribute__((visibility("default")))
void wn_cm_bridge_register_friends_list_observer(WnCmFriendsListObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_dispatch_friends_list(const uint64_t* sids, size_t count);

__attribute__((visibility("default")))
void wn_cm_bridge_inject_test_friends_list(const uint64_t* sids, size_t count);


struct WnCmLicenseEntry {
    uint32_t package_id;
    uint32_t owner_id;
    uint32_t time_created;
    uint32_t license_type;
    uint32_t flags;
    int32_t  change_number;
    int32_t  minute_limit;     // 0 = unlimited
    int32_t  minutes_used;
};

typedef void (*WnCmLicenseListObserverFn)(const WnCmLicenseEntry* licenses,
                                            size_t count);

__attribute__((visibility("default")))
void wn_cm_bridge_register_license_list_observer(WnCmLicenseListObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_dispatch_license_list(const WnCmLicenseEntry* licenses,
                                          size_t count);

__attribute__((visibility("default")))
void wn_cm_bridge_inject_test_license_list(const WnCmLicenseEntry* licenses,
                                             size_t count);


struct WnCmAccountInfo {
    const char* persona_name;       // may be NULL if not provided
    size_t      persona_name_len;
    const char* ip_country;         // may be NULL if not provided
    size_t      ip_country_len;
    bool        two_factor_enabled;
    bool        phone_verified;
    bool        phone_identifying;
    bool        phone_requires_verification;
};

typedef void (*WnCmAccountInfoObserverFn)(const WnCmAccountInfo* info);

__attribute__((visibility("default")))
void wn_cm_bridge_register_account_info_observer(WnCmAccountInfoObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_dispatch_account_info(const WnCmAccountInfo* info);

__attribute__((visibility("default")))
void wn_cm_bridge_inject_test_account_info(const WnCmAccountInfo* info);

typedef void (*WnCmServerRealTimeObserverFn)(uint32_t server_realtime);

__attribute__((visibility("default")))
void wn_cm_bridge_register_server_realtime_observer(WnCmServerRealTimeObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_dispatch_server_realtime(uint32_t server_realtime);


typedef struct WnCmLobbyEntry {
    uint64_t steam_id;
    int32_t  max_members;
    int32_t  num_members;
    int32_t  lobby_type;
    int32_t  lobby_flags;
    int32_t  ping_ms;
    int64_t  weight;
    float    distance;
} WnCmLobbyEntry;

typedef struct WnCmLobbyMember {
    uint64_t       steam_id;
    const char*    persona_name;     // UTF-8, valid only during callback
    const uint8_t* metadata_bytes;
    size_t         metadata_len;
} WnCmLobbyMember;

typedef struct WnCmLobbyData {
    uint64_t                steam_id_lobby;
    uint64_t                steam_id_owner;
    uint32_t                app_id;
    int32_t                 max_members;
    int32_t                 num_members;
    int32_t                 lobby_type;
    int32_t                 lobby_flags;
    const uint8_t*          metadata_bytes;
    size_t                  metadata_len;
    const WnCmLobbyMember*  members;
    size_t                  member_count;
} WnCmLobbyData;

typedef void (*WnCmLobbyListCb)(uint64_t hCall,
                                int32_t eresult,
                                const WnCmLobbyEntry* lobbies,
                                size_t count);

typedef void (*WnCmLobbyDataObserverFn)(const WnCmLobbyData* data);

__attribute__((visibility("default")))
bool wn_cm_lobby_get_list(uint64_t hCall,
                          uint32_t app_id,
                          int32_t num_lobbies_requested,
                          const char* const* filter_keys,
                          const char* const* filter_values,
                          const int32_t* filter_comparisons,
                          const int32_t* filter_types,
                          size_t filter_count,
                          WnCmLobbyListCb cb);

__attribute__((visibility("default")))
void wn_cm_bridge_register_lobby_data_observer(WnCmLobbyDataObserverFn fn);

typedef void (*WnCmLobbyCreatedCb)(uint64_t hCall,
                                   int32_t eresult,
                                   uint64_t lobby_sid);
__attribute__((visibility("default")))
bool wn_cm_lobby_create(uint64_t hCall,
                        uint32_t app_id,
                        int32_t lobby_type,
                        int32_t max_members,
                        WnCmLobbyCreatedCb cb);

typedef void (*WnCmLobbyJoinedCb)(uint64_t hCall,
                                  int32_t chat_room_enter_response,
                                  uint64_t lobby_sid);
__attribute__((visibility("default")))
bool wn_cm_lobby_join(uint64_t hCall,
                      uint32_t app_id,
                      uint64_t lobby_sid,
                      WnCmLobbyJoinedCb cb);

__attribute__((visibility("default")))
bool wn_cm_lobby_leave(uint32_t app_id, uint64_t lobby_sid);

typedef void (*WnCmLobbySetDataCb)(uint64_t hCall, int32_t eresult);
__attribute__((visibility("default")))
bool wn_cm_lobby_set_data(uint64_t hCall,
                          uint32_t app_id,
                          uint64_t lobby_sid,
                          uint64_t steam_id_member,
                          const uint8_t* metadata, size_t metadata_len,
                          int32_t max_members, int32_t lobby_type,
                          int32_t lobby_flags,
                          WnCmLobbySetDataCb cb);

__attribute__((visibility("default")))
bool wn_cm_lobby_send_chat(uint32_t app_id, uint64_t lobby_sid,
                           const uint8_t* data, size_t n);

typedef void (*WnCmLobbySetOwnerCb)(uint64_t hCall, int32_t eresult);
__attribute__((visibility("default")))
bool wn_cm_lobby_set_owner(uint64_t hCall,
                           uint32_t app_id,
                           uint64_t lobby_sid,
                           uint64_t new_owner_sid,
                           WnCmLobbySetOwnerCb cb);

__attribute__((visibility("default")))
bool wn_cm_lobby_invite_user(uint32_t app_id,
                             uint64_t lobby_sid,
                             uint64_t invitee_sid);

typedef void (*WnCmLobbyChatMsgObserverFn)(uint64_t lobby_sid,
                                           uint64_t sender_sid,
                                           const uint8_t* data,
                                           size_t n);
__attribute__((visibility("default")))
void wn_cm_bridge_register_lobby_chat_msg_observer(WnCmLobbyChatMsgObserverFn fn);

typedef void (*WnCmLobbyMembershipObserverFn)(int32_t joined /*1=joined,0=left*/,
                                               uint64_t lobby_sid,
                                               uint64_t user_sid,
                                               const char* persona_name);
__attribute__((visibility("default")))
void wn_cm_bridge_register_lobby_membership_observer(WnCmLobbyMembershipObserverFn fn);

__attribute__((visibility("default")))
void wn_cm_bridge_start_state_sync_poller(void);

__attribute__((visibility("default")))
void wn_cm_bridge_stop_state_sync_poller(void);

}  // extern "C"
