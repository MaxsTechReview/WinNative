#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <span>
#include <vector>

#include "wn_steam/emsg.h"
#include "wn_steam/euniverse.h"

namespace wn_steam {

// ---------------------------------------------------------------------------
// MsgHdr — the 20-byte non-proto header used for pre-encryption messages
// (ChannelEncryptRequest/Response/Result). Matches SteamKit2 `MsgHdr`.
// ---------------------------------------------------------------------------

constexpr uint64_t kInvalidJobID = static_cast<uint64_t>(-1);
constexpr size_t   kMsgHdrBytes  = 20;

struct MsgHdr {
    EMsg     msg           = EMsg::Invalid;
    uint64_t target_job_id = kInvalidJobID;
    uint64_t source_job_id = kInvalidJobID;

    // Serialize into the start of `out` (appended). Always writes 20 bytes.
    void serialize(std::vector<uint8_t>& out) const;

    // Deserialize 20 bytes from `in`. Returns nullopt on short input.
    // `consumed` set to 20 on success.
    [[nodiscard]] static std::optional<MsgHdr>
    deserialize(std::span<const uint8_t> in, size_t& consumed) noexcept;
};

// ---------------------------------------------------------------------------
// MsgChannelEncryptRequest (server → client, EMsg = 1303)
// Body: protocol_version (u32=1), universe (u32 EUniverse), then an optional
// trailing challenge nonce of >= 16 bytes the client must echo back in the
// RSA-encrypted handshake blob.
// ---------------------------------------------------------------------------

constexpr uint32_t kChannelEncryptProtocolVersion = 1;
constexpr size_t   kChannelEncryptRequestFixedBody = 8;   // 2 × u32

struct MsgChannelEncryptRequest {
    uint32_t  protocol_version = kChannelEncryptProtocolVersion;
    EUniverse universe         = EUniverse::Invalid;
    std::vector<uint8_t> challenge;  // empty for old protocol v0; modern v1 = 16 bytes

    [[nodiscard]] static std::optional<MsgChannelEncryptRequest>
    deserialize_body(std::span<const uint8_t> body) noexcept;
};

// ---------------------------------------------------------------------------
// MsgChannelEncryptResponse (client → server, EMsg = 1304)
//
// Wire body (all little-endian, no padding):
//   u32 protocol_version           (= 1)
//   u32 key_size                   (= 128 for 1024-bit RSA)
//   u8[128] encrypted_handshake_blob
//   u32 key_crc                    (CRC32 of encrypted_handshake_blob)
//   u32 unknown_zero               (always 0; SteamKit appends as payload
//                                    after the 8-byte structured prefix)
// Total body = 8 + 128 + 4 + 4 = 144 bytes.
//
// SteamKit's generated `MsgChannelEncryptResponse` class declares only the
// first two u32s as "structured" fields; the encrypted blob, CRC, and the
// trailing zero are appended to the message payload in
// `EnvelopeEncryptedConnection.HandleEncryptRequest`. We collapse the two
// halves into one serialize call for simplicity — on-wire bytes are
// identical (144 bytes).
// ---------------------------------------------------------------------------

constexpr size_t kRsa1024CipherBytes = 128;
constexpr size_t kChannelEncryptResponseBodyBytes = 144;

struct MsgChannelEncryptResponse {
    uint32_t protocol_version = kChannelEncryptProtocolVersion;
    uint32_t key_size         = static_cast<uint32_t>(kRsa1024CipherBytes);
    std::array<uint8_t, kRsa1024CipherBytes> encrypted_handshake_blob{};
    uint32_t key_crc          = 0;
    uint32_t unknown_zero     = 0;

    // Serialize the body (144 bytes) into `out` (appended). Header must be
    // emitted separately by the caller via MsgHdr::serialize.
    void serialize_body(std::vector<uint8_t>& out) const;
};

// ---------------------------------------------------------------------------
// MsgChannelEncryptResult (server → client, EMsg = 1305)
// Body: u32 EResult (1 = OK).
// ---------------------------------------------------------------------------

constexpr size_t kChannelEncryptResultBodyBytes = 4;

struct MsgChannelEncryptResult {
    uint32_t result = 0;

    [[nodiscard]] static std::optional<MsgChannelEncryptResult>
    deserialize_body(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam
