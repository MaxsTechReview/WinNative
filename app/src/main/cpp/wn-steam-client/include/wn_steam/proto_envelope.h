#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

#include "wn_steam/cmsg_protobuf_header.h"
#include "wn_steam/emsg.h"

namespace wn_steam {

// On-wire format of one protobuf-flagged Steam message:
//
//   [u32 emsg_with_proto_flag]    // EMsg | 0x80000000
//   [u32 header_len]              // length of the CMsgProtoBufHeader bytes
//   [CMsgProtoBufHeader bytes]
//   [protobuf body bytes]
//
// Reading is the inverse. The body bytes are returned by `decode` for the
// caller to deserialize as the specific service-method or client-message
// proto.

constexpr size_t kProtoEnvelopePrefixBytes = 8;  // two u32s

struct ProtoEnvelope {
    EMsg                emsg = EMsg::Invalid;  // proto flag stripped
    CMsgProtoBufHeader  header;
    std::vector<uint8_t> body;
};

// Encode envelope on the wire. Caller fills `emsg`, `header`, `body`.
// Output is a fresh vector ready for `EncryptedChannel::send`.
[[nodiscard]] std::vector<uint8_t> encode_proto_envelope(
    EMsg emsg,
    const CMsgProtoBufHeader& header,
    std::span<const uint8_t> body);

// Decode an inbound buffer that the channel has already decrypted. Returns
// nullopt if the prefix or header is malformed. The EMsg must carry the
// proto flag — non-proto wire messages (e.g. Multi-wrapped ChannelEncrypt*)
// are NOT handled here.
[[nodiscard]] std::optional<ProtoEnvelope> decode_proto_envelope(
    std::span<const uint8_t> wire) noexcept;

}  // namespace wn_steam
