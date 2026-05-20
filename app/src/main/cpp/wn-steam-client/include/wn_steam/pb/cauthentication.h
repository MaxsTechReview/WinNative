#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_auth.steamclient.proto
// (the IAuthenticationService service-method surface that drives the
// modern login flow). Field numbers and enum values match the canonical
// schema verbatim — see proto_wire.h for layout.

namespace wn_steam::pb {

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class EAuthTokenPlatformType : int32_t {
    Unknown      = 0,
    SteamClient  = 1,
    WebBrowser   = 2,
    MobileApp    = 3,
};

enum class EAuthSessionGuardType : int32_t {
    Unknown            = 0,
    None               = 1,
    EmailCode          = 2,
    DeviceCode         = 3,
    DeviceConfirmation = 4,
    EmailConfirmation  = 5,
    MachineToken       = 6,
    LegacyMachineAuth  = 7,
};

enum class ESessionPersistence : int32_t {
    Invalid    = -1,
    Ephemeral  = 0,
    Persistent = 1,
};

// ---------------------------------------------------------------------------
// CAuthentication_DeviceDetails
//   1 string device_friendly_name
//   2 int32  platform_type  (EAuthTokenPlatformType)
//   3 int32  os_type        (EOSType)
//   4 uint32 gaming_device_type
//   5 uint32 client_count
//   6 bytes  machine_id
//   7 int32  app_type
// ---------------------------------------------------------------------------

struct CAuthentication_DeviceDetails {
    std::string             device_friendly_name;
    // SteamClient (1) matches what SteamKit2 and JavaSteam pretend to be by
    // default. We tried MobileApp (3) first — it gives auto-renewable
    // refresh tokens (per a 2025-04-30 protocol change) — but Steam
    // silently rejects MobileApp credentials without a real mobile-app
    // device fingerprint (machine_id, etc.). Treat us like the Steam
    // desktop client; the refresh token is good ~200 days.
    EAuthTokenPlatformType  platform_type = EAuthTokenPlatformType::SteamClient;
    int32_t                 os_type = 16;   // EOSType.Windows (matches SteamKit defaults)
    uint32_t                gaming_device_type = 0;
    uint32_t                client_count = 0;
    std::vector<uint8_t>    machine_id;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ---------------------------------------------------------------------------
// CAuthentication_GetPasswordRSAPublicKey
// ---------------------------------------------------------------------------

struct CAuthentication_GetPasswordRSAPublicKey_Request {
    std::string account_name;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CAuthentication_GetPasswordRSAPublicKey_Response {
    std::string publickey_mod;
    std::string publickey_exp;
    uint64_t    timestamp = 0;

    [[nodiscard]] static std::optional<CAuthentication_GetPasswordRSAPublicKey_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ---------------------------------------------------------------------------
// CAuthentication_BeginAuthSessionViaCredentials
//
// Request field numbers:
//   1  string  device_friendly_name      (legacy; modern uses device_details)
//   2  string  account_name
//   3  string  unused
//   4  string  encrypted_password        (base64)
//   5  uint64  encryption_timestamp
//   6  bool    remember_login             (deprecated; use persistence)
//   7  string  website_id
//   8  int32   persistence                (ESessionPersistence)
//   9  CAuthentication_DeviceDetails device_details
//   10 string  guard_data
//   11 uint32  language
//   12 int32   qos_level
//   13 string  platform_type             (string form of platform — confusion exists; numeric goes inside device_details)
// ---------------------------------------------------------------------------

struct CAuthentication_BeginAuthSessionViaCredentials_Request {
    std::string                   account_name;
    std::string                   encrypted_password;       // base64
    uint64_t                      encryption_timestamp = 0;
    // "Client" matches the website_id SteamKit2 uses when impersonating
    // the desktop client. Stick with this unless we re-attempt MobileApp.
    std::string                   website_id = "Client";
    ESessionPersistence           persistence = ESessionPersistence::Persistent;
    CAuthentication_DeviceDetails device_details;
    std::string                   guard_data;               // optional; suppresses Guard prompts on remembered device
    uint32_t                      language = 0;
    int32_t                       qos_level = 2;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CAuthentication_AllowedConfirmation {
    EAuthSessionGuardType  confirmation_type = EAuthSessionGuardType::Unknown;
    std::string            associated_message;

    [[nodiscard]] static std::optional<CAuthentication_AllowedConfirmation>
    deserialize(std::span<const uint8_t> body) noexcept;
};

struct CAuthentication_BeginAuthSessionViaCredentials_Response {
    uint64_t              client_id = 0;
    std::vector<uint8_t>  request_id;
    float                 interval = 5.0f;
    std::vector<CAuthentication_AllowedConfirmation> allowed_confirmations;
    uint64_t              steamid = 0;
    std::string           weak_token;
    std::string           agreement_session_url;
    std::string           extended_error_message;

    [[nodiscard]] static std::optional<CAuthentication_BeginAuthSessionViaCredentials_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ---------------------------------------------------------------------------
// CAuthentication_PollAuthSessionStatus
// ---------------------------------------------------------------------------

struct CAuthentication_PollAuthSessionStatus_Request {
    uint64_t              client_id = 0;
    std::vector<uint8_t>  request_id;
    uint64_t              token_to_revoke = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CAuthentication_PollAuthSessionStatus_Response {
    uint64_t      new_client_id = 0;
    std::string   new_challenge_url;
    std::string   refresh_token;
    std::string   access_token;
    bool          had_remote_interaction = false;
    std::string   account_name;
    std::string   new_guard_data;
    std::string   agreement_session_url;

    [[nodiscard]] static std::optional<CAuthentication_PollAuthSessionStatus_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ---------------------------------------------------------------------------
// CAuthentication_UpdateAuthSessionWithSteamGuardCode
// ---------------------------------------------------------------------------

struct CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request {
    uint64_t              client_id = 0;
    uint64_t              steamid = 0;
    std::string           code;
    EAuthSessionGuardType code_type = EAuthSessionGuardType::Unknown;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ---------------------------------------------------------------------------
// CAuthentication_BeginAuthSessionViaQR
// ---------------------------------------------------------------------------

struct CAuthentication_BeginAuthSessionViaQR_Request {
    std::string                   device_friendly_name;
    // QR works fine with MobileApp (the QR is meant to be scanned by the
    // Steam mobile app), so we keep MobileApp here. Only credentials
    // login requires SteamClient impersonation.
    EAuthTokenPlatformType        platform_type = EAuthTokenPlatformType::MobileApp;
    CAuthentication_DeviceDetails device_details;
    std::string                   website_id = "Mobile";

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CAuthentication_BeginAuthSessionViaQR_Response {
    uint64_t              client_id = 0;
    std::string           challenge_url;
    std::vector<uint8_t>  request_id;
    float                 interval = 5.0f;
    std::vector<CAuthentication_AllowedConfirmation> allowed_confirmations;
    int32_t               version = 0;

    [[nodiscard]] static std::optional<CAuthentication_BeginAuthSessionViaQR_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
