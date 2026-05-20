#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <vector>

// CMsgClientGetAppOwnershipTicket (EMsg 857) / Response (EMsg 858).
//
// Request: one uint32 field carrying the app_id.
// Response: int32 eresult (default 2=Fail in proto2), uint32 app_id echo,
// bytes ticket — the opaque ownership-ticket blob that Wine's
// lsteamclient.dll returns to game code on SteamUser()->GetAppOwnership
// Ticket(appid). The blob's internal structure (steamid, app_id,
// timestamps, signature) is parsed by Steam libraries downstream; we
// treat it as bytes here.
//
// Field numbers verified against canonical
//   JavaSteam/src/main/proto/.../steammessages_clientserver.proto:65-73

namespace wn_steam::pb {

struct CMsgClientGetAppOwnershipTicket {
    uint32_t app_id = 0;   // 1 uint32

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CMsgClientGetAppOwnershipTicketResponse {
    // proto2 default = 2 (EResult.Fail) — caller must NOT treat a missing
    // field as 0/Invalid. We match the default explicitly.
    uint32_t              eresult = 2;   // 1 uint32 [default = 2]
    uint32_t              app_id  = 0;   // 2 uint32
    std::vector<uint8_t>  ticket;        // 3 bytes — opaque

    [[nodiscard]] static std::optional<CMsgClientGetAppOwnershipTicketResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
