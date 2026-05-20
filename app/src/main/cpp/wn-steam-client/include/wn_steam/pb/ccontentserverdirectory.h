#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_contentsystem.steamclient
// .proto — the IContentServerDirectory unified-service surface used by the
// Phase 5 depot download path.
//
// Two service methods:
//   ContentServerDirectory.GetManifestRequestCode#1
//       returns the uint64 "request code" that must be appended to a
//       manifest CDN URL (path .../manifest/<gid>/5/<code>).
//   ContentServerDirectory.GetServersForSteamPipe#1
//       returns the list of CDN content servers to download from.
//
// Field numbers verified against JavaSteam 1.8.x
//   src/main/proto/.../steammessages_contentsystem.steamclient.proto
// and SteamContent.kt / ContentServerDirectoryService.kt.

namespace wn_steam::pb {

// ContentServerDirectory.GetManifestRequestCode#1 request.
//   1 uint32 app_id
//   2 uint32 depot_id
//   3 uint64 manifest_id
//   4 string app_branch
//   5 string branch_password_hash
// JavaSteam clears app_branch + branch_password_hash when the branch is
// "public" (case-insensitive); we replicate that in CMClient.
struct CContentServerDirectory_GetManifestRequestCode_Request {
    uint32_t    app_id      = 0;
    uint32_t    depot_id    = 0;
    uint64_t    manifest_id = 0;
    std::string app_branch;
    std::string branch_password_hash;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// Response: 1 uint64 manifest_request_code. May legitimately be 0 ("not
// granted") — the caller treats 0 as "fetch the manifest without a code".
struct CContentServerDirectory_GetManifestRequestCode_Response {
    uint64_t manifest_request_code = 0;

    [[nodiscard]] static std::optional<CContentServerDirectory_GetManifestRequestCode_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// CContentServerDirectory_ServerInfo — one CDN content server.
// Port is NOT in the proto: JavaSteam derives 443 when https_support ==
// "mandatory", otherwise 80 (see ContentServerDirectoryService.kt).
struct CContentServerDirectory_ServerInfo {
    std::string           type;                          // 1
    int32_t               source_id              = 0;    // 2
    int32_t               cell_id                = 0;    // 3
    int32_t               load                   = 0;    // 4
    float                 weighted_load          = 0.f;  // 5  (fixed32)
    int32_t               num_entries_in_client_list = 0;// 6
    bool                  steam_china_only       = false;// 7
    std::string           host;                          // 8
    std::string           vhost;                         // 9
    bool                  use_as_proxy           = false;// 10
    std::string           proxy_request_path_template;   // 11
    std::string           https_support;                 // 12
    std::vector<uint32_t> allowed_app_ids;               // 13 (repeated)
    uint32_t              priority_class         = 0;    // 15

    // true when https_support == "mandatory" (case-insensitive).
    [[nodiscard]] bool use_https() const noexcept;
    // 443 for https, 80 otherwise — derived, not from the wire.
    [[nodiscard]] uint16_t port() const noexcept { return use_https() ? 443 : 80; }

    [[nodiscard]] static std::optional<CContentServerDirectory_ServerInfo>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ContentServerDirectory.GetServersForSteamPipe#1 request.
//   1 uint32 cell_id
//   2 uint32 max_servers  [default = 20]
struct CContentServerDirectory_GetServersForSteamPipe_Request {
    uint32_t cell_id     = 0;
    uint32_t max_servers = 20;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// Response: 1 repeated ServerInfo servers, 2 bool no_change.
struct CContentServerDirectory_GetServersForSteamPipe_Response {
    std::vector<CContentServerDirectory_ServerInfo> servers;
    bool                                            no_change = false;

    [[nodiscard]] static std::optional<CContentServerDirectory_GetServersForSteamPipe_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
