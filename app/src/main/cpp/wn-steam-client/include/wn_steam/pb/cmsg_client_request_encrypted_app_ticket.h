#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <vector>

// CMsgClientRequestEncryptedAppTicket (EMsg 5526) / Response (EMsg 5527).
//
// Request: one uint32 app_id (field 2 `userdata` is intentionally omitted —
// the Goldberg GetEncryptedAppTicket flow passes null userdata).
//
// Response: uint32 app_id echo, int32 eresult (proto2 default 2 = Fail),
// and `encrypted_app_ticket` — an `EncryptedAppTicket` sub-message. We
// capture field 3 as its raw serialized bytes: base64 of exactly those
// bytes is what Goldberg's steam_settings/configs.user.ini `ticket=` value
// expects (matches GameNative's `EncryptedAppTicket.toByteArray()`).
//
// Field numbers verified against canonical JavaSteam
//   steammessages_clientserver.proto:304-313 and emsg.steamd:1402-1403.

namespace wn_steam::pb {

struct CMsgClientRequestEncryptedAppTicket {
    uint32_t app_id = 0;   // 1 uint32

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CMsgClientRequestEncryptedAppTicketResponse {
    uint32_t             app_id  = 0;   // 1 uint32
    // proto2 default = 2 (EResult.Fail); a missing field is NOT 0/Invalid.
    int32_t              eresult = 2;   // 2 int32 [default = 2]
    // 3 EncryptedAppTicket — kept as the raw serialized sub-message.
    std::vector<uint8_t> encrypted_app_ticket;

    [[nodiscard]] static std::optional<CMsgClientRequestEncryptedAppTicketResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
