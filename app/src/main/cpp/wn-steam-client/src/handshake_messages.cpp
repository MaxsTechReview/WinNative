#include "wn_steam/handshake_messages.h"

#include "wn_steam/wire_format.h"

namespace wn_steam {

// ---------------------------------------------------------------------------
// MsgHdr
// ---------------------------------------------------------------------------

void MsgHdr::serialize(std::vector<uint8_t>& out) const {
    wire::Writer w(out);
    w.u32(static_cast<uint32_t>(msg));   // EMsg, no proto flag for these messages
    w.u64(target_job_id);
    w.u64(source_job_id);
}

std::optional<MsgHdr> MsgHdr::deserialize(std::span<const uint8_t> in,
                                          size_t& consumed) noexcept {
    if (in.size() < kMsgHdrBytes) return std::nullopt;

    wire::Reader r(in.subspan(0, kMsgHdrBytes));
    MsgHdr h;
    const uint32_t raw_msg = r.u32();
    // The 3 handshake messages MUST NOT carry the proto flag. Reject early
    // so a corrupt or surprise inbound doesn't get mis-routed.
    if (emsg_has_proto_flag(raw_msg)) return std::nullopt;
    h.msg           = emsg_strip_proto_flag(raw_msg);
    h.target_job_id = r.u64();
    h.source_job_id = r.u64();
    if (!r.ok()) return std::nullopt;
    consumed = kMsgHdrBytes;
    return h;
}

// ---------------------------------------------------------------------------
// MsgChannelEncryptRequest
// ---------------------------------------------------------------------------

std::optional<MsgChannelEncryptRequest>
MsgChannelEncryptRequest::deserialize_body(std::span<const uint8_t> body) noexcept {
    if (body.size() < kChannelEncryptRequestFixedBody) return std::nullopt;

    wire::Reader r(body);
    MsgChannelEncryptRequest msg;
    msg.protocol_version = r.u32();
    const uint32_t uni = r.u32();
    if (!r.ok()) return std::nullopt;

    // Reject obviously bogus universe values. Only Public/Beta/Internal/Dev
    // have published RSA keys; anything else is unhandlable.
    if (uni < static_cast<uint32_t>(EUniverse::Public) ||
        uni > static_cast<uint32_t>(EUniverse::Dev)) {
        return std::nullopt;
    }
    msg.universe = static_cast<EUniverse>(uni);

    // Anything past the fixed 8-byte prefix is the challenge nonce. Protocol
    // v0 had no challenge; v1+ has 16 bytes. We pass whatever is there
    // through; the handshake layer is responsible for "≥16 bytes ⇒ v1
    // semantics" decisions.
    auto rest = body.subspan(kChannelEncryptRequestFixedBody);
    msg.challenge.assign(rest.begin(), rest.end());
    return msg;
}

// ---------------------------------------------------------------------------
// MsgChannelEncryptResponse
// ---------------------------------------------------------------------------

void MsgChannelEncryptResponse::serialize_body(std::vector<uint8_t>& out) const {
    wire::Writer w(out);
    w.u32(protocol_version);
    w.u32(key_size);
    w.bytes(std::span<const uint8_t>(encrypted_handshake_blob.data(),
                                     encrypted_handshake_blob.size()));
    w.u32(key_crc);
    w.u32(unknown_zero);
}

// ---------------------------------------------------------------------------
// MsgChannelEncryptResult
// ---------------------------------------------------------------------------

std::optional<MsgChannelEncryptResult>
MsgChannelEncryptResult::deserialize_body(std::span<const uint8_t> body) noexcept {
    if (body.size() < kChannelEncryptResultBodyBytes) return std::nullopt;
    wire::Reader r(body);
    MsgChannelEncryptResult msg;
    msg.result = r.u32();
    if (!r.ok()) return std::nullopt;
    return msg;
}

}  // namespace wn_steam
