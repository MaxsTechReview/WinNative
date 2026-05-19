#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <vector>

// CMsgClientGetDepotDecryptionKey (EMsg 1202) / Response (EMsg 1203).
//
// The depot decryption key is the AES-256 key used to decrypt a depot's
// content manifest AND every chunk downloaded from the CDN for that depot.
// It is the foundational primitive of the Phase 5 download path: nothing
// from the CDN is usable without it.
//
// Request: depot_id + app_id (the owning app, for the entitlement check).
// Response: eresult (proto2 default 2=Fail), depot_id echo, and
// depot_encryption_key — 32 raw bytes on success.
//
// Field numbers verified against canonical
//   JavaSteam steammessages_clientserver_2.proto (CMsgClientGetDepot
//   DecryptionKey / ...Response) and SteamKit2 master.

namespace wn_steam::pb {

struct CMsgClientGetDepotDecryptionKey {
    uint32_t depot_id = 0;   // 1 uint32
    uint32_t app_id   = 0;   // 2 uint32

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

struct CMsgClientGetDepotDecryptionKeyResponse {
    // proto2 default = 2 (EResult.Fail) — a missing field is NOT 0/Invalid.
    uint32_t              eresult  = 2;   // 1 int32 [default = 2]
    uint32_t              depot_id = 0;   // 2 uint32
    std::vector<uint8_t>  depot_encryption_key;  // 3 bytes — AES-256 (32 bytes)

    [[nodiscard]] static std::optional<CMsgClientGetDepotDecryptionKeyResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
