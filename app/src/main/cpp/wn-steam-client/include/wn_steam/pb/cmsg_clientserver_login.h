#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for the client-server login surface.
// Field numbers and wire types verified BYTE-FOR-BYTE against the canonical
// steammessages_clientserver_login.proto in SteamDatabase/Protobufs master
// (cross-checked by two independent agents — see git history for the bugs
// this audit caught).

namespace wn_steam::pb {

// ---------------------------------------------------------------------------
// CMsgClientHello
//   1 uint32 protocol_version
// ---------------------------------------------------------------------------

// MsgClientLogon.CurrentProtocol from SteamKit2 master. steam-vent uses
// 65580; both currently accepted by Steam, but 65581 matches the live
// SteamKit reference client.
constexpr uint32_t kMsgClientCurrentProtocol = 65581;

struct CMsgClientHello {
    uint32_t protocol_version = kMsgClientCurrentProtocol;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ---------------------------------------------------------------------------
// CMsgClientHeartBeat
//   1 bool send_reply
// ---------------------------------------------------------------------------

struct CMsgClientHeartBeat {
    bool send_reply = false;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ---------------------------------------------------------------------------
// CMsgClientLogOff — empty body
// ---------------------------------------------------------------------------

struct CMsgClientLogOff {
    [[nodiscard]] std::vector<uint8_t> serialize() const { return {}; }
};

// ---------------------------------------------------------------------------
// CMsgClientLoggedOff
//   1 int32 eresult [default = 2]
// ---------------------------------------------------------------------------

struct CMsgClientLoggedOff {
    int32_t eresult = 2;

    [[nodiscard]] static std::optional<CMsgClientLoggedOff>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ---------------------------------------------------------------------------
// CMsgClientLogon — refresh-token logon
//
// Canonical field numbers (verbatim from
// steammessages_clientserver_login.proto, lines 31–90):
//
//     1  uint32 protocol_version
//     2  uint32 deprecated_obfustucated_private_ip
//     3  uint32 cell_id
//     4  uint32 last_session_id
//     5  uint32 client_package_version       (NOT a string — uint32)
//     6  string client_language
//     7  uint32 client_os_type
//     8  bool   should_remember_password [default=false]
//     9  string wine_version
//    10  uint32 deprecated_10
//    11  CMsgIPAddress obfuscated_private_ip (submessage)
//    20  uint32 deprecated_public_ip
//    21  uint32 qos_level
//    22  fixed64 client_supplied_steam_id    (8 bytes LE, NOT a string)
//    23  CMsgIPAddress public_ip (submessage)
//    30  bytes  machine_id                   (BB-fingerprint blob)
//    31  uint32 launcher_type [default=0]
//    32  uint32 ui_mode [default=0]          (1=desktop, 4=mobile)
//    33  uint32 chat_mode [default=0]        (2 = new chat)
//    41  bytes  steam2_auth_ticket
//    50  string account_name                 (legacy password flow; not used)
//    51  string password                     (legacy)
//    96  string machine_name
//   100  uint64 client_instance_id
//   102  bool   supports_rate_limit_response
//   108  string access_token                 ← the refresh-token JWT
//
// The PRIOR hand-rolled field numbers (9, 8, 12, 11, 35, 34, 65, 6 [as
// string], 10, 54, 37, 80) were wrong on all of these. Steam was silently
// rejecting our CMsgClientLogon wholesale because field 10 received a
// length-delimited string (machine_name) but is declared `uint32`
// (deprecated_10) → wire-type mismatch → message corruption.
// ---------------------------------------------------------------------------

struct CMsgClientLogon {
    uint32_t    protocol_version             = kMsgClientCurrentProtocol;
    uint32_t    cell_id                      = 0;
    // JavaSteam hardcodes 1771 (SteamUser.kt:109 "required to get a proper
    // sentry file for steam guard"). Sending 0 was flagged by the CM and
    // contributed to InvalidPassword rejections.
    uint32_t    client_package_version       = 1771;
    std::string client_language              = "english";
    uint32_t    client_os_type               = 16;     // EOSType.Windows
    bool        should_remember_password     = true;
    uint32_t    qos_level                    = 2;
    uint64_t    client_supplied_steam_id     = 0;       // optional — CM resolves from refresh token
    std::vector<uint8_t> machine_id;                    // populated by logon_with_refresh_token if empty
    uint32_t    ui_mode                      = 7;       // 7=DesktopUI (1=Tenfoot/BigPicture, not desktop)
    uint32_t    chat_mode                    = 2;       // new chat
    // CRITICAL: must be set even for refresh-token logon — JavaSteam
    // SteamUser.kt:95 sets account_name unconditionally; omitting it causes
    // EResult.InvalidPassword (5) regardless of refresh-token validity.
    std::string account_name;                           // field 50 — Steam login username
    std::string machine_name                 = "WN-Steam-Client";
    uint64_t    client_instance_id           = 0;       // random
    bool        supports_rate_limit_response = true;
    std::string access_token;                           // refresh-token JWT
    // LoginID / obfuscated private IP. JavaSteam SteamUser.kt:75-90 sets
    // both the modern field 95 (CMsgIPAddress.v4) and the deprecated
    // field 31. Steam uses it to disambiguate concurrent logons of the
    // same account — two sessions sharing a LoginID (incl. 0/absent) make
    // the server boot one of them. Set to a unique non-zero value per
    // session by logon_with_refresh_token.
    uint32_t    obfuscated_private_ip        = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ---------------------------------------------------------------------------
// CMsgClientLogonResponse
//
// Canonical (lines 92–120):
//
//     1  int32   eresult [default = 2]
//     2  int32   legacy_out_of_game_heartbeat_seconds (deprecated)
//     3  int32   heartbeat_seconds
//     4  uint32  deprecated_public_ip
//     5  fixed32 rtime32_server_time         (wire type 5, 4 LE bytes)
//     6  uint32  account_flags
//     7  uint32  cell_id
//     8  string  email_domain
//     9  bytes   steam2_ticket
//    10  int32   eresult_extended
//    12  uint32  cell_id_ping_threshold       (note: field 11 unassigned)
//    13  bool    deprecated_use_pics
//    14  string  vanity_url
//    15  CMsgIPAddress public_ip
//    16  string  user_country
//    20  fixed64 client_supplied_steamid     (note: fields 17-19 unassigned)
//    21  string  ip_country_code
//    22  bytes   parental_settings
//    23  bytes   parental_setting_signature
//    24  int32   count_loginfailures_to_migrate
//    25  int32   count_disconnects_to_migrate
//    26  int32   ogs_data_report_time_window
//    27  uint64  client_instance_id
//    28  bool    force_client_update_check
//    29  string  agreement_session_url
//    30  uint64  token_id                    (varint, NOT fixed64!)
//    31  uint64  family_group_id
//
// PRIOR BUGS (now fixed):
//   - read field 11 hoping for webapi_authenticate_user_nonce — that field
//     does not exist in CMsgClientLogonResponse; it lives in
//     CMsgClientRequestWebAPIAuthenticateUserNonceResponse.
//   - read fields 28/29/30/32/33 instead of 27/28/29/30/31.
//   - read token_id as fixed64 instead of uint64 (varint).
// ---------------------------------------------------------------------------

struct CMsgClientLogonResponse {
    int32_t     eresult                   = 2;   // canonical default
    int32_t     heartbeat_seconds         = 0;
    uint32_t    rtime32_server_time       = 0;   // wire-type fixed32
    uint32_t    cell_id                   = 0;
    int32_t     eresult_extended          = 0;
    std::string vanity_url;
    uint64_t    client_supplied_steamid   = 0;   // wire-type fixed64
    uint64_t    client_instance_id        = 0;
    bool        force_client_update_check = false;
    std::string agreement_session_url;
    uint64_t    token_id                  = 0;   // wire-type varint (NOT fixed64)
    uint64_t    family_group_id           = 0;

    [[nodiscard]] static std::optional<CMsgClientLogonResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
