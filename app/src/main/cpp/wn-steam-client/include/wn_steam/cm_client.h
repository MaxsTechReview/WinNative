#pragma once

#include <atomic>
#include <chrono>
#include <functional>
#include <memory>
#include <mutex>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "wn_steam/cmsg_protobuf_header.h"
#include "wn_steam/emsg.h"
#include "wn_steam/encrypted_channel.h"
#include "wn_steam/heartbeat.h"
#include "wn_steam/job_manager.h"
#include "wn_steam/pb/ccloud.h"
#include "wn_steam/pb/cinventory.h"
#include "wn_steam/pb/cpublishedfile.h"
#include "wn_steam/pb/ccontentserverdirectory.h"
#include "wn_steam/pb/cfamilygroups.h"
#include "wn_steam/pb/cplayer.h"
#include "wn_steam/pb/cmsg_client_get_app_ownership_ticket.h"
#include "wn_steam/pb/cmsg_client_request_encrypted_app_ticket.h"
#include "wn_steam/pb/cmsg_client_get_user_stats.h"
#include "wn_steam/pb/cmsg_client_store_user_stats.h"
#include "wn_steam/pb/cmsg_client_games_played.h"
#include "wn_steam/pb/cmsg_client_kick_playing_session.h"
#include "wn_steam/pb/cmsg_client_change_status.h"
#include "wn_steam/pb/cmsg_client_persona.h"
#include "wn_steam/pb/cmsg_client_playing_session_state.h"
#include "wn_steam/pb/cmsg_client_get_depot_decryption_key.h"
#include "wn_steam/pb/cmsg_client_pics.h"
#include "wn_steam/transport.h"
#include "wn_steam/wine_bridge.h"
#include "wn_steam/wn_library_store.h"
#include "wn_steam/wn_ticket_cache.h"
#include <optional>
#include <unordered_map>

namespace wn_steam {

enum class ClientState : uint8_t {
    Disconnected,    // no transport / no channel
    Connecting,      // transport / handshake in progress
    Connected,       // encrypted channel established, not yet logged on
    LoggedOn,        // CMsgClientLogonResponse OK
};

// Thread-safe Steam CM client.
// Callbacks fire on the transport worker thread; do not block them.
class CMClient {
public:
    using StateCallback = std::function<void(ClientState)>;
    using ClientMessageCallback = std::function<void(EMsg emsg,
                                                     const CMsgProtoBufHeader& header,
                                                     std::span<const uint8_t> body)>;

    CMClient();
    ~CMClient();

    CMClient(const CMClient&)            = delete;
    CMClient& operator=(const CMClient&) = delete;

    void set_ca_bundle_path(const std::string& path);

    [[nodiscard]] bool connect(const std::string& url);

    void disconnect();

    [[nodiscard]] ClientState state() const noexcept { return state_.load(); }
    [[nodiscard]] uint64_t    steam_id() const noexcept { return steam_id_.load(); }
    [[nodiscard]] int32_t     session_id() const noexcept { return session_id_.load(); }
    [[nodiscard]] uint64_t    family_group_id() const noexcept { return family_group_id_.load(); }

    // Continuation fires on response, timeout, or disconnect.
    void call_service_method(std::string_view method_name,
                             bool authed,
                             std::span<const uint8_t> request_body,
                             JobContinuation cb,
                             std::chrono::seconds timeout = std::chrono::seconds{30});

    // routing_appid routes app-scoped messages to the right Steam backend.
    [[nodiscard]] bool send_proto_message(EMsg emsg,
                                          std::span<const uint8_t> body,
                                          uint32_t routing_appid = 0);

    [[nodiscard]] bool logon_with_refresh_token(
        const std::string& refresh_token,
        const std::string& account_name = "",
        uint64_t client_supplied_steam_id = 0);

    using PicsAccessTokenCallback =
        std::function<void(std::optional<pb::CMsgClientPICSAccessTokenResponse>)>;
    using PicsProductInfoCallback =
        std::function<void(std::optional<pb::CMsgClientPICSProductInfoResponse>)>;

    // Required for non-public package/app info.
    void pics_get_access_tokens(std::vector<uint32_t> packageids,
                                std::vector<uint32_t> appids,
                                PicsAccessTokenCallback cb,
                                std::chrono::seconds timeout = std::chrono::seconds{30});

    // Multi-part PICS responses are merged before the callback fires.
    void pics_get_product_info(std::vector<pb::PicsPackageInfoReq> packages,
                               std::vector<pb::PicsAppInfoReq> apps,
                               bool meta_data_only,
                               PicsProductInfoCallback cb,
                               std::chrono::seconds timeout = std::chrono::seconds{60});

    using PicsChangesSinceCallback =
        std::function<void(std::optional<pb::CMsgClientPICSChangesSinceResponse>)>;
    void pics_get_changes_since(uint32_t since_change_number,
                                PicsChangesSinceCallback cb,
                                std::chrono::seconds timeout = std::chrono::seconds{30});

    // Pre-warm app, DLC, and tickets before launch.
    using PrepareAppCallback = std::function<void(bool ok, std::string error)>;
    void prepare_app(uint32_t app_id,
                     std::vector<uint32_t> dlc_app_ids,
                     PrepareAppCallback cb,
                     std::chrono::seconds timeout = std::chrono::seconds{30});

    void notify_games_played(const pb::CMsgClientGamesPlayed& msg);

    void kick_playing_session(bool only_stop_game);

    void set_persona_state(uint32_t persona_state);

    // Persona reply is server-pushed and cached.
    void request_user_persona();

    [[nodiscard]] std::optional<pb::PersonaStateFriend> self_persona() const;

    [[nodiscard]] bool is_playing_blocked() const noexcept {
        return playing_blocked_.load();
    }

    // Makes the next wait observe a post-kick server push, not stale state.
    void mark_playing_blocked() noexcept { playing_blocked_.store(true); }

    // Cached post-logon licenses; empty until the push arrives.
    [[nodiscard]] std::vector<pb::License> license_list() const;

    using FamilyGroupCallback = std::function<void(
        std::optional<pb::CFamilyGroups_GetFamilyGroup_Response>)>;
    void get_family_group(uint64_t family_group_id, FamilyGroupCallback cb,
                          std::chrono::seconds timeout = std::chrono::seconds{30});

    // Private libraries return an empty games list.
    using OwnedGamesCallback = std::function<void(
        std::optional<pb::CPlayer_GetOwnedGames_Response>)>;
    void get_owned_games(uint64_t steam_id, OwnedGamesCallback cb,
                         std::chrono::seconds timeout = std::chrono::seconds{30});

    void set_on_state(StateCallback cb);
    void set_on_client_message(ClientMessageCallback cb);

    // Disable for download-only sessions before logon.
    void set_auto_populate_library(bool enabled) noexcept {
        auto_populate_library_.store(enabled);
    }

    [[nodiscard]] WnLibraryStore& library() noexcept { return library_; }
    [[nodiscard]] const WnLibraryStore& library() const noexcept { return library_; }

    [[nodiscard]] WnTicketCache& tickets() noexcept { return tickets_; }
    [[nodiscard]] const WnTicketCache& tickets() const noexcept { return tickets_; }

    // Steam3Master + SteamClientService listeners for lsteamclient.dll.
    [[nodiscard]] WineBridge& wine_bridge() noexcept { return wine_bridge_; }
    [[nodiscard]] const WineBridge& wine_bridge() const noexcept { return wine_bridge_; }

    // Cached on success.
    using AppOwnershipTicketCallback =
        std::function<void(std::optional<pb::CMsgClientGetAppOwnershipTicketResponse>)>;
    void get_app_ownership_ticket(uint32_t app_id,
                                  AppOwnershipTicketCallback cb,
                                  std::chrono::seconds timeout = std::chrono::seconds{30});

    // Used for Goldberg online auth.
    using EncryptedAppTicketCallback =
        std::function<void(std::optional<pb::CMsgClientRequestEncryptedAppTicketResponse>)>;
    void request_encrypted_app_ticket(uint32_t app_id,
                                      EncryptedAppTicketCallback cb,
                                      std::chrono::seconds timeout = std::chrono::seconds{30});

    // Non-OK empty schemas still reach the caller for diagnostics.
    using UserStatsCallback =
        std::function<void(std::optional<pb::CMsgClientGetUserStatsResponse>)>;
    void get_user_stats(uint32_t app_id,
                        UserStatsCallback cb,
                        std::chrono::seconds timeout = std::chrono::seconds{30});

    // Fire-and-forget stat or achievement write-back.
    void store_user_stats(uint32_t app_id, uint64_t steam_id,
                          uint32_t crc_stats,
                          const std::vector<std::pair<uint32_t, uint32_t>>& stats);

    // Non-OK results still reach the caller for entitlement diagnostics.
    using DepotDecryptionKeyCallback =
        std::function<void(std::optional<pb::CMsgClientGetDepotDecryptionKeyResponse>)>;
    void get_depot_decryption_key(uint32_t depot_id, uint32_t app_id,
                                  DepotDecryptionKeyCallback cb,
                                  std::chrono::seconds timeout = std::chrono::seconds{30});

    // Public/empty branches omit app_branch to match JavaSteam.
    using ManifestRequestCodeCallback = std::function<void(
        std::optional<pb::CContentServerDirectory_GetManifestRequestCode_Response>)>;
    void get_manifest_request_code(uint32_t app_id, uint32_t depot_id,
                                   uint64_t manifest_id, std::string branch,
                                   ManifestRequestCodeCallback cb,
                                   std::chrono::seconds timeout = std::chrono::seconds{30});

    // cell_id 0 lets Steam pick.
    using CdnServersCallback = std::function<void(
        std::optional<pb::CContentServerDirectory_GetServersForSteamPipe_Response>)>;
    void get_cdn_servers(uint32_t cell_id, CdnServersCallback cb,
                         std::chrono::seconds timeout = std::chrono::seconds{30});

    // Pass synced_change_number=0 for a full cloud restore listing.
    using CloudFileChangelistCallback = std::function<void(
        std::optional<pb::CCloud_GetAppFileChangelist_Response>)>;
    void cloud_get_app_file_changelist(uint32_t app_id,
                                       uint64_t synced_change_number,
                                       CloudFileChangelistCallback cb,
                                       std::chrono::seconds timeout = std::chrono::seconds{30});

    // Item-definition digest for steam_settings/items.json.
    using ItemDefMetaCallback = std::function<void(
        std::optional<pb::CInventory_GetItemDefMeta_Response>)>;
    void inventory_get_item_def_meta(uint32_t app_id,
                                     ItemDefMetaCallback cb,
                                     std::chrono::seconds timeout = std::chrono::seconds{30});

    // One page of subscriptions; caller paginates using `total`.
    using PublishedFileUserFilesCallback = std::function<void(
        std::optional<pb::CPublishedFile_GetUserFiles_Response>)>;
    void published_file_get_subscribed(uint32_t app_id, uint32_t page,
                                       uint32_t num_per_page,
                                       PublishedFileUserFilesCallback cb,
                                       std::chrono::seconds timeout = std::chrono::seconds{30});

    // Resolves the HTTP URL and headers for a cloud file body.
    using CloudFileDownloadCallback = std::function<void(
        std::optional<pb::CCloud_ClientFileDownload_Response>)>;
    void cloud_get_file_download_info(uint32_t app_id, std::string filename,
                                      CloudFileDownloadCallback cb,
                                      std::chrono::seconds timeout = std::chrono::seconds{30});

    // Cloud upload is caller-driven: open batch, upload files, commit, complete.
    using CloudBeginBatchCallback = std::function<void(
        std::optional<pb::CCloud_BeginAppUploadBatch_Response>)>;
    void cloud_begin_app_upload_batch(uint32_t app_id, std::string machine_name,
                                      std::vector<std::string> files_to_upload,
                                      std::vector<std::string> files_to_delete,
                                      uint64_t client_id,
                                      CloudBeginBatchCallback cb,
                                      std::chrono::seconds timeout = std::chrono::seconds{30});

    using CloudBeginFileUploadCallback = std::function<void(
        std::optional<pb::CCloud_ClientBeginFileUpload_Response>)>;
    void cloud_begin_file_upload(uint32_t app_id, std::string filename,
                                 uint32_t file_size, uint32_t raw_file_size,
                                 std::vector<uint8_t> file_sha, uint64_t time_stamp,
                                 uint64_t upload_batch_id,
                                 CloudBeginFileUploadCallback cb,
                                 std::chrono::seconds timeout = std::chrono::seconds{30});

    using CloudCommitFileUploadCallback = std::function<void(
        std::optional<pb::CCloud_ClientCommitFileUpload_Response>)>;
    void cloud_commit_file_upload(bool transfer_succeeded, uint32_t app_id,
                                  std::vector<uint8_t> file_sha, std::string filename,
                                  CloudCommitFileUploadCallback cb,
                                  std::chrono::seconds timeout = std::chrono::seconds{30});

    using CloudCompleteBatchCallback = std::function<void(bool ok)>;
    void cloud_complete_app_upload_batch(uint32_t app_id, uint64_t batch_id,
                                         uint32_t batch_eresult,
                                         CloudCompleteBatchCallback cb,
                                         std::chrono::seconds timeout = std::chrono::seconds{30});

    // Empty pending-op list means clear to launch.
    using CloudAppLaunchIntentCallback = std::function<void(
        std::optional<pb::CCloud_AppLaunchIntent_Response>)>;
    void cloud_signal_app_launch_intent(uint32_t app_id, uint64_t client_id,
                                        std::string machine_name,
                                        bool ignore_pending_operations,
                                        int32_t os_type,
                                        CloudAppLaunchIntentCallback cb,
                                        std::chrono::seconds timeout = std::chrono::seconds{30});

    void cloud_signal_app_exit_sync_done(uint32_t app_id, uint64_t client_id,
                                         bool uploads_completed,
                                         bool uploads_required);

private:
    void on_channel_connected();
    void on_channel_disconnected(ChannelDisconnectReason r, const std::string& detail);
    void on_channel_message(std::span<const uint8_t> bytes);

    void set_state_locked_(ClientState s);
    void route_inbound_(EMsg emsg, const CMsgProtoBufHeader& header,
                        std::span<const uint8_t> body);

    void library_populate_step_();

    std::unique_ptr<EncryptedChannel> channel_;
    JobManager                        jobs_;
    Heartbeat                         heartbeat_;

    std::atomic<ClientState>          state_{ClientState::Disconnected};
    std::atomic<uint64_t>             steam_id_{0};
    std::atomic<int32_t>              session_id_{0};
    std::atomic<uint64_t>             family_group_id_{0};
    std::atomic<bool>                 auto_populate_library_{true};
    std::atomic<bool>                 playing_blocked_{false};

    mutable std::mutex                cb_mu_;
    StateCallback                     on_state_;
    ClientMessageCallback             on_client_message_;

    // Holds merged PICS product-info responses until the final part arrives.
    struct PicsAggregate {
        pb::CMsgClientPICSProductInfoResponse acc;
        PicsProductInfoCallback               cb;
    };
    mutable std::mutex                                pics_mu_;
    std::unordered_map<uint64_t, PicsAggregate>       pics_pending_;

    WnLibraryStore                                    library_;
    WnTicketCache                                     tickets_;
    WineBridge                                        wine_bridge_;

    mutable std::mutex                                persona_mu_;
    std::optional<pb::PersonaStateFriend>             self_persona_;

    mutable std::mutex                                license_mu_;
    std::vector<pb::License>                          license_list_;
};

}  // namespace wn_steam
