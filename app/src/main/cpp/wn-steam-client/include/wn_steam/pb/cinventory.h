#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_inventory.steamclient.proto —
// the `Inventory` unified service. We use exactly one method:
//   Inventory.GetItemDefMeta#1
//       returns the current item-definition digest for an app. The digest is
//       then used to download the item-def archive over plain HTTPS
//       (api.steampowered.com/IGameInventory/GetItemDefArchive/v1), which the
//       Goldberg/gbe_fork emulator consumes as steam_settings/items.json.
//
// Field numbers verified against SteamDatabase/SteamTracking
//   Protobufs/steammessages_inventory.steamclient.proto

namespace wn_steam::pb {

// Inventory.GetItemDefMeta#1 request.
//   1 uint32 appid
struct CInventory_GetItemDefMeta_Request {
    uint32_t appid = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// Inventory.GetItemDefMeta#1 response.
//   1 uint32 modified   (unix timestamp of the last itemdef change)
//   2 string digest     (content hash identifying the current archive)
struct CInventory_GetItemDefMeta_Response {
    uint32_t    modified = 0;
    std::string digest;

    [[nodiscard]] static std::optional<CInventory_GetItemDefMeta_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
