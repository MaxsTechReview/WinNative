
#pragma once

#include <atomic>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace wn_libsteamclient {

using HSteamPipe = int;
using HSteamUser = int;

struct CallbackMsg {
    int                   user;
    int                   id;
    std::vector<uint8_t>  body;
};

struct PushedState {
    std::atomic<uint64_t>   steam_id{0};
    std::atomic<uint32_t>   account_id{0};
    std::atomic<int>        persona_state{0};    // EPersonaState (0=Offline … 7=Invisible)
    std::atomic<uint32_t>   app_id{0};            // for ISteamUtils.GetAppID
    std::atomic<int>        ip_country_set{0};
    std::atomic<uint32_t>   server_realtime{0};       // unix epoch reported by CM at the anchor moment
    std::atomic<int64_t>    server_realtime_anchor_local_ms{0}; // local steady_clock::now() ms when server_realtime was captured
    std::string             persona_name;        // guarded by state_mutex()
    std::string             ip_country;          // guarded by state_mutex()
    std::string             ui_language;         // guarded by state_mutex()

    std::unordered_set<uint32_t>           owned_apps;
    std::unordered_set<uint32_t>           installed_apps;
    std::unordered_map<uint32_t,std::string> app_install_dirs;
    std::unordered_map<uint32_t,std::string> app_current_beta;
    struct DlProgress {
        uint64_t bytes_downloaded = 0;
        uint64_t bytes_total      = 0;
    };
    std::unordered_map<uint32_t,DlProgress> app_dl_progress;
    std::unordered_map<uint32_t,std::string> app_cloud_remote_dirs;
    std::unordered_set<uint32_t> app_low_violence;
    std::unordered_set<uint32_t> app_vac_banned;
    std::atomic<bool>   account_phone_verified{false};
    std::atomic<bool>   account_two_factor_enabled{false};
    std::atomic<bool>   account_phone_identifying{false};
    std::atomic<bool>   account_phone_requires_verification{false};
    std::unordered_set<uint32_t>           apps_marked_corrupt;

    struct WorkshopItemInfo {
        std::string install_dir;        // absolute Windows path or wine guest path
        uint64_t    size_bytes = 0;     // total disk footprint for ISteamUGC slot 73 bytes
        uint32_t    timestamp  = 0;     // unix32 last-update — slot 73 timestamp
        bool        installed  = true;  // currently we only ever push installed entries
    };
    std::unordered_map<uint32_t, std::unordered_map<uint64_t, WorkshopItemInfo>>
        subscribed_workshop_items;

    std::unordered_map<uint32_t,
        std::unordered_map<int32_t, std::unordered_map<std::string, std::string>>>
        inventory_item_defs;

    struct LobbyMember {
        std::string                                      persona_name;
        std::unordered_map<std::string, std::string>     data;
    };
    struct LobbyState {
        uint32_t                                         app_id        = 0;
        uint64_t                                         owner_sid     = 0;
        int32_t                                          max_members   = 0;
        int32_t                                          lobby_type    = 0;
        int32_t                                          lobby_flags   = 0;
        bool                                             joinable      = true;
        uint32_t                                         game_server_ip   = 0;
        uint16_t                                         game_server_port = 0;
        uint64_t                                         game_server_sid  = 0;
        std::unordered_map<std::string, std::string>     data;
        std::unordered_map<uint64_t, LobbyMember>        members;
    };
    std::unordered_map<uint64_t, LobbyState> active_lobbies;
    std::vector<uint64_t>                    lobby_match_list;

    struct LobbyChatEntry {
        uint64_t                sender_sid;
        uint8_t                 chat_type;     // EChatEntryType (1=ChatMsg)
        std::vector<uint8_t>    body;
    };
    std::unordered_map<uint64_t, std::vector<LobbyChatEntry>>
        lobby_chat_buffer;

    struct P2PSessionState {
        uint64_t  last_session_error   = 0;  // EP2PSessionError on close
        bool      connection_active    = false;
        bool      connecting           = false;
        uint32_t  bytes_queued_for_send = 0;
        uint32_t  remote_ip            = 0;  // little-endian / IPv4
        uint16_t  remote_port          = 0;
        bool      using_relay          = false;
    };
    std::unordered_map<uint64_t, P2PSessionState> active_p2p_sessions;

    struct P2PInboundPacket {
        uint64_t              sender_sid;
        int32_t               channel;
        std::vector<uint8_t>  body;
    };
    std::unordered_map<int32_t, std::deque<P2PInboundPacket>> p2p_inbound_queue;
    std::atomic<bool> p2p_relay_allowed{true};

    struct OverlayRequest {
        std::string kind;   // "webpage" | "store" | "user" | "invite" | "dialog"
        std::string arg1;   // URL / dialog name
        uint64_t    sid     = 0;   // user SID / lobby SID (depending on kind)
        uint32_t    app_id  = 0;   // store appid
    };
    std::deque<OverlayRequest> overlay_request_queue;
    std::unordered_map<uint64_t,int32_t>   friend_steam_levels;
    std::unordered_map<uint64_t,std::string> player_nicknames;
    std::atomic<int32_t>                   self_player_level{0};
    std::unordered_map<int32_t,int32_t>    self_game_badges;

    struct LicenseEntry {
        uint32_t package_id    = 0;
        uint32_t owner_id      = 0;       // AccountID; != self_account → family-shared
        uint32_t time_created  = 0;       // unix32 of purchase
        uint32_t license_type  = 0;       // ELicenseType
        uint32_t flags         = 0;       // ELicenseFlags bitfield
        int32_t  change_number = 0;       // PICS change_number on this package
        int32_t  minute_limit  = 0;       // 0 = unlimited; >0 = timed-trial cap (minutes)
        int32_t  minutes_used  = 0;       // current playtime against minute_limit
    };
    std::unordered_map<uint32_t,LicenseEntry> licenses;

    std::unordered_map<uint32_t,std::vector<uint32_t>> app_source_packages;
    struct DlcEntry {
        uint32_t    app_id;
        std::string name;
        bool        available = true;  // DLC currently purchasable
    };
    std::unordered_map<uint32_t,std::vector<DlcEntry>> app_dlcs;
    std::unordered_map<uint32_t,std::vector<uint32_t>> app_installed_depots;
    std::unordered_map<uint32_t,std::string>          app_names;
    std::unordered_map<uint32_t,uint32_t>             app_build_ids;
    std::vector<uint64_t>                  friends;
    std::unordered_map<uint64_t,std::string> friend_persona_names;
    std::unordered_map<uint64_t,uint32_t>  friend_persona_states;
    std::unordered_map<uint64_t,uint32_t>  friend_game_played_app;

    using RichPresenceMap = std::vector<std::pair<std::string,std::string>>;
    std::unordered_map<uint64_t, RichPresenceMap> rich_presence;

    struct ImageEntry {
        int32_t              width  = 0;
        int32_t              height = 0;
        std::vector<uint8_t> rgba;  // size = width*height*4
    };
    std::unordered_map<int32_t, ImageEntry>  image_registry;
    struct FriendAvatarHandles {
        int32_t small  = 0;
        int32_t medium = 0;
        int32_t large  = 0;
    };
    std::unordered_map<uint64_t, FriendAvatarHandles> friend_avatars;
    int32_t next_image_handle = 1;

    std::unordered_map<uint64_t, std::vector<uint8_t>> friend_avatar_hashes;

    std::atomic<bool>     cloud_enabled_account{true};
    std::atomic<bool>     cloud_enabled_app{false};
    std::atomic<uint64_t> cloud_quota_total{0};
    std::atomic<uint64_t> cloud_quota_available{0};
    struct CloudFileEntry {
        std::string name;
        int32_t     size      = 0;   // ISteamRemoteStorage uses int32 here
        int64_t     timestamp = 0;   // unix seconds
    };
    std::vector<CloudFileEntry>            cloud_files;

    struct AchievementEntry {
        std::string  api_name;       // internal name ("ACH_FIRST_BLOOD")
        std::unordered_map<std::string, std::string> display_names;
        std::unordered_map<std::string, std::string> descriptions;
        std::string  icon;           // icon URL or empty
        bool         hidden        = false;
        bool         achieved      = false;
        uint32_t     unlock_time   = 0;   // unix seconds
        int32_t      icon_handle   = 0;   // synthetic id for slot-11
        bool         pending_store = false;
        int32_t      block_id      = -1;
        int32_t      bit_index     = 0;
    };
    std::vector<AchievementEntry>          achievements;
    std::unordered_map<std::string,size_t> achievement_index;
    std::unordered_map<std::string,int32_t> stats_int;
    std::unordered_map<std::string,float>   stats_float;
    std::unordered_map<std::string,uint32_t> stat_name_to_id;
    std::unordered_set<std::string>         dirty_stats_int;
    std::unordered_set<std::string>         dirty_stats_float;

    struct AvgRateAccum {
        double total_count = 0.0;
        double total_time  = 0.0;
    };
    std::unordered_map<std::string,AvgRateAccum> stats_avg_rate;
    std::atomic<bool>                      stats_ready{false};

    std::atomic<bool>                      overlay_active{false};

    struct AuthTicket {
        uint32_t               h_ticket;       // returned to caller
        uint32_t               app_id;
        std::vector<uint8_t>   body;
    };
    std::atomic<uint32_t>                  next_auth_ticket_handle{1};
    std::unordered_map<uint32_t, AuthTicket> auth_tickets;

    std::string                            launch_command_line;
    std::atomic<bool>                      app_is_family_shared{false};

    std::unordered_map<uint32_t, std::vector<uint8_t>> encrypted_app_tickets;
    std::atomic<int32_t>                   encrypted_app_ticket_eresult{0};
};

PushedState& pushed();

struct CallResultMsg {
    uint64_t              h_call;
    int                   callback_id;
    bool                  io_failure;
    std::vector<uint8_t>  body;
};

struct State {
    std::atomic<HSteamPipe> pipe{0};
    std::atomic<HSteamUser> user{0};

    std::atomic<bool>       logged_on{false};
    std::atomic<bool>       connected{false};

    std::mutex              callback_mu;
    std::deque<CallbackMsg> callback_queue;
    std::vector<uint8_t>    last_param;

    std::mutex                                    call_results_mu;
    std::unordered_map<uint64_t, CallResultMsg>   call_results_pending;
    uint64_t                                      next_api_call_handle = 1;
};

State& state();
std::mutex& state_mutex();

HSteamPipe alloc_pipe();
bool       release_pipe(HSteamPipe pipe);
HSteamUser alloc_global_user(HSteamPipe pipe);
void       release_user(HSteamPipe pipe, HSteamUser user);

void push_callback(int user, int id, const void* data, size_t n);

void push_call_result(uint64_t h_call, int callback_id,
                      const void* data, size_t n, bool io_failure);

uint64_t alloc_api_call_handle();

void set_logged_on(bool logged_on, int eresult_on_disconnect = 6);

}  // namespace wn_libsteamclient
