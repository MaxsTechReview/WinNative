#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <vector>

// CMsgClientRequestEncryptedAppTicket (EMsg 5526) / response (EMsg 5527).
// Field 2 `userdata` is omitted; Goldberg passes null userdata.
// Field 3 is a serialized EncryptedAppTicket sub-message. Goldberg expects
// base64 of these bytes in steam_settings/configs.user.ini `ticket=`.
// Field numbers match steammessages_clientserver.proto and emsg.steamd.

namespace wn_steam::pb {

struct CMsgClientRequestEncryptedAppTicket {
    uint32_t app_id = 0;   // 1 uint32

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CMsgClientRequestEncryptedAppTicketResponse {
    uint32_t             app_id  = 0;   // 1 uint32
    // proto2 default = 2 (EResult.Fail); a missing field is NOT 0/Invalid.
    int32_t              eresult = 2;   // 2 int32 [default = 2]
    // 3 EncryptedAppTicket, kept as the raw serialized sub-message.
    std::vector<uint8_t> encrypted_app_ticket;

    [[nodiscard]] static std::optional<CMsgClientRequestEncryptedAppTicketResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
