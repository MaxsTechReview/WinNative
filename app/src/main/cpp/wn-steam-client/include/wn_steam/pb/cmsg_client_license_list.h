#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// CMsgClientLicenseList (EMsg 780, ClientLicenseList).
//
// Pushed to the client immediately after a successful ClientLogonResponse.
// Carries the full list of "licenses" — Steam's term for the user's
// subscription/package grants. Each License entry points at a Steam
// `package_id` (a.k.a. SubID), which in turn (via PICS) lists the
// concrete `app_id`s the user owns.
//
// Canonical field numbers verified BYTE-FOR-BYTE against the proto at
//   JavaSteam/src/main/proto/.../steammessages_clientserver.proto:169-193
// (CMsgClientLicenseList + inner License submessage).
//
// Wire-format gotchas baked into the parser:
//   • fields 16-18 use a 2-byte varint tag (field_number << 3 ≥ 128)
//   • `time_created` / `time_next_process` are fixed32, NOT varint
//   • `licenses` is a repeated submessage — proto2 always emits one
//     tag-prefixed length-delimited entry per element (never packed)

namespace wn_steam::pb {

struct License {
    uint32_t    package_id            = 0;   //  1 uint32
    uint32_t    time_created          = 0;   //  2 fixed32 (unix epoch seconds)
    uint32_t    time_next_process     = 0;   //  3 fixed32
    int32_t     minute_limit          = 0;   //  4 int32 (0 = unlimited)
    int32_t     minutes_used          = 0;   //  5 int32
    uint32_t    payment_method        = 0;   //  6 uint32 (EPaymentMethod)
    uint32_t    flags                 = 0;   //  7 uint32 (ELicenseFlags bitfield)
    std::string purchase_country_code;       //  8 string (ISO-3166)
    uint32_t    license_type          = 0;   //  9 uint32 (ELicenseType)
    int32_t     territory_code        = 0;   // 10 int32
    int32_t     change_number         = 0;   // 11 int32 (last PICS change_number on this package)
    uint32_t    owner_id              = 0;   // 12 uint32 (AccountID of purchaser; differs for family-share)
    uint32_t    initial_period        = 0;   // 13 uint32
    uint32_t    initial_time_unit     = 0;   // 14 uint32 (ETimeUnit)
    uint32_t    renewal_period        = 0;   // 15 uint32
    uint32_t    renewal_time_unit     = 0;   // 16 uint32
    uint64_t    access_token          = 0;   // 17 uint64 (PICS access token for restricted packages)
    uint32_t    master_package_id     = 0;   // 18 uint32 (non-zero ⇒ this is a sub-package)

    [[nodiscard]] static std::optional<License>
    deserialize(std::span<const uint8_t> body) noexcept;
};

struct CMsgClientLicenseList {
    // Default 2 (EResult.NoConnection) matches proto2 [default = 2]. Steam
    // sends 1 (EResult.OK) for the actual push.
    int32_t              eresult = 2;       // 1 int32
    std::vector<License> licenses;          // 2 repeated License (non-packed)

    [[nodiscard]] static std::optional<CMsgClientLicenseList>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
