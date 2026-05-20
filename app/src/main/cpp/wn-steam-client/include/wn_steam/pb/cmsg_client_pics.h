#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// PICS — Product Info Cache. Four messages:
//   8903 CMsgClientPICSProductInfoRequest
//   8904 CMsgClientPICSProductInfoResponse
//   8905 CMsgClientPICSAccessTokenRequest
//   8906 CMsgClientPICSAccessTokenResponse
//
// Field numbers verified against JavaSteam's canonical
//   src/main/proto/.../steammessages_clientserver_appinfo.proto:60-133
//
// Wire-format gotchas baked into the parser:
//   • Repeated uint32 fields (packageids/appids in the request, denied lists
//     in the response, unknown_* in the product-info response) may arrive
//     PACKED or UNPACKED depending on the sender. Our parser handles both.
//   • CMsgClientPICSProductInfoResponse.packages[].buffer carries a 4-byte
//     little-endian packageid PREFIX before the KeyValues binary blob.
//     CMsgClientPICSProductInfoResponse.apps[].buffer does NOT — it starts
//     directly at the KeyValues root. Easy to corrupt if you parse the
//     package buffer like the app buffer.
//   • Large responses fall back to HTTP CDN: response_pending=true means
//     keep accumulating until you see a final message with the flag absent.
//     http_min_size + http_host indicate which buffers are externally hosted
//     (the inline `buffer` field will be empty for those entries).

namespace wn_steam::pb {

// ===========================================================================
// CMsgClientPICSChangesSinceRequest (EMsg 8901)
// ===========================================================================
// Polls PICS for everything that changed since a known change number. The
// continuous app-metadata refresh loop sends this; the response lists the
// apps/packages whose change number advanced (re-fetch those via product
// info). force_full_update means the local cache is too stale — re-crawl.

struct CMsgClientPICSChangesSinceRequest {
    uint32_t since_change_number       = 0;      // 1 uint32
    bool     send_app_info_changes     = true;   // 2 bool
    bool     send_package_info_changes = true;   // 3 bool

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ===========================================================================
// CMsgClientPICSChangesSinceResponse (EMsg 8902)
// ===========================================================================

struct PicsAppChange {
    uint32_t appid         = 0;       // 1 uint32
    uint32_t change_number = 0;       // 2 uint32
    bool     needs_token   = false;   // 3 bool
};

struct PicsPackageChange {
    uint32_t packageid     = 0;       // 1 uint32
    uint32_t change_number = 0;       // 2 uint32
    bool     needs_token   = false;   // 3 bool
};

struct CMsgClientPICSChangesSinceResponse {
    uint32_t current_change_number = 0;   // 1 uint32
    uint32_t since_change_number   = 0;   // 2 uint32
    bool     force_full_update     = false;   // 3 bool
    std::vector<PicsPackageChange> package_changes;   // 4 repeated
    std::vector<PicsAppChange>     app_changes;       // 5 repeated
    bool     force_full_app_update     = false;   // 6 bool
    bool     force_full_package_update = false;   // 7 bool

    [[nodiscard]] static std::optional<CMsgClientPICSChangesSinceResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ===========================================================================
// CMsgClientPICSAccessTokenRequest (EMsg 8905)
// ===========================================================================

struct CMsgClientPICSAccessTokenRequest {
    std::vector<uint32_t> packageids;   // 1 repeated uint32
    std::vector<uint32_t> appids;       // 2 repeated uint32

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ===========================================================================
// CMsgClientPICSAccessTokenResponse (EMsg 8906)
// ===========================================================================

struct PicsPackageToken {
    uint32_t packageid    = 0;   // 1 uint32
    uint64_t access_token = 0;   // 2 uint64
};

struct PicsAppToken {
    uint32_t appid        = 0;   // 1 uint32
    uint64_t access_token = 0;   // 2 uint64
};

struct CMsgClientPICSAccessTokenResponse {
    std::vector<PicsPackageToken> package_access_tokens;   // 1 repeated PackageToken
    std::vector<uint32_t>         package_denied_tokens;   // 2 repeated uint32
    std::vector<PicsAppToken>     app_access_tokens;       // 3 repeated AppToken
    std::vector<uint32_t>         app_denied_tokens;       // 4 repeated uint32

    [[nodiscard]] static std::optional<CMsgClientPICSAccessTokenResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ===========================================================================
// CMsgClientPICSProductInfoRequest (EMsg 8903)
// ===========================================================================

struct PicsAppInfoReq {
    uint32_t appid                = 0;       // 1 uint32
    uint64_t access_token         = 0;       // 2 uint64 (from prior AccessTokenResponse)
    bool     only_public_obsolete = false;   // 3 bool — legacy; modern clients send false
};

struct PicsPackageInfoReq {
    uint32_t packageid    = 0;   // 1 uint32
    uint64_t access_token = 0;   // 2 uint64 (from prior AccessTokenResponse, or License.access_token)
};

struct CMsgClientPICSProductInfoRequest {
    std::vector<PicsPackageInfoReq> packages;          // 1 repeated PackageInfo
    std::vector<PicsAppInfoReq>     apps;              // 2 repeated AppInfo
    bool     meta_data_only   = false;   // 3 bool — set true to get only change_number/missing_token, no VDF body
    uint32_t num_prev_failed  = 0;       // 4 uint32 — how many times this request has been retried
    // Field 5 OBSOLETE_supports_package_tokens — not emitted (deprecated)
    uint32_t sequence_number  = 0;       // 6 uint32 — client-side request ordering
    bool     single_response  = false;   // 7 bool — set true to disable streaming continuation

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ===========================================================================
// CMsgClientPICSProductInfoResponse (EMsg 8904)
// ===========================================================================

struct PicsAppInfoResp {
    uint32_t appid                = 0;   // 1 uint32
    uint32_t change_number        = 0;   // 2 uint32
    bool     missing_token        = false;  // 3 bool — true ⇒ retry with an access_token
    std::vector<uint8_t> sha;             // 4 bytes — SHA-1 of buffer (20 bytes)
    std::vector<uint8_t> buffer;          // 5 bytes — KeyValues binary; empty if HTTP-hosted or meta_data_only
    bool     only_public          = false;  // 6 bool — true ⇒ only public section returned
    uint32_t size                 = 0;   // 7 uint32 — size of buffer (or of CDN-hosted blob if HTTP fallback)
};

struct PicsPackageInfoResp {
    uint32_t packageid     = 0;   // 1 uint32
    uint32_t change_number = 0;   // 2 uint32
    bool     missing_token = false;  // 3 bool
    std::vector<uint8_t> sha;       // 4 bytes — SHA-1
    std::vector<uint8_t> buffer;    // 5 bytes — 4-byte LE packageid PREFIX, then KeyValues binary
    uint32_t size          = 0;   // 6 uint32
};

struct CMsgClientPICSProductInfoResponse {
    std::vector<PicsAppInfoResp>     apps;                   // 1 repeated AppInfo
    std::vector<uint32_t>            unknown_appids;         // 2 repeated uint32 — deleted/invalid
    std::vector<PicsPackageInfoResp> packages;               // 3 repeated PackageInfo
    std::vector<uint32_t>            unknown_packageids;     // 4 repeated uint32
    bool        meta_data_only      = false;   // 5 bool — echo of request flag
    bool        response_pending    = false;   // 6 bool — true ⇒ more messages will follow with the same jobid_target
    uint32_t    http_min_size       = 0;       // 7 uint32 — payloads ≥ this are hosted externally (buffer field empty)
    std::string http_host;                     // 8 string — CDN host to fetch oversized buffers from

    [[nodiscard]] static std::optional<CMsgClientPICSProductInfoResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
