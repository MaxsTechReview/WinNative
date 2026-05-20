#include "wn_steam/proto_envelope.h"

#include "wn_steam/wire_format.h"

namespace wn_steam {

std::vector<uint8_t> encode_proto_envelope(EMsg emsg,
                                           const CMsgProtoBufHeader& header,
                                           std::span<const uint8_t> body) {
    std::vector<uint8_t> hdr_bytes;
    hdr_bytes.reserve(64);
    header.serialize(hdr_bytes);

    std::vector<uint8_t> out;
    out.reserve(kProtoEnvelopePrefixBytes + hdr_bytes.size() + body.size());

    wire::Writer w(out);
    w.u32(emsg_with_proto_flag(emsg));
    w.u32(static_cast<uint32_t>(hdr_bytes.size()));
    w.bytes(std::span<const uint8_t>(hdr_bytes.data(), hdr_bytes.size()));
    w.bytes(body);
    return out;
}

std::optional<ProtoEnvelope>
decode_proto_envelope(std::span<const uint8_t> wire) noexcept {
    if (wire.size() < kProtoEnvelopePrefixBytes) return std::nullopt;

    wire::Reader r(wire);
    const uint32_t raw_emsg = r.u32();
    const uint32_t hdr_len  = r.u32();
    if (!r.ok()) return std::nullopt;

    if (!emsg_has_proto_flag(raw_emsg)) return std::nullopt;
    if (r.remaining() < hdr_len)        return std::nullopt;

    auto hdr_bytes = r.bytes(hdr_len);
    if (!r.ok()) return std::nullopt;

    auto hdr = CMsgProtoBufHeader::deserialize(hdr_bytes);
    if (!hdr) return std::nullopt;

    ProtoEnvelope env;
    env.emsg   = emsg_strip_proto_flag(raw_emsg);
    env.header = std::move(*hdr);

    const size_t body_off = r.position();
    env.body.assign(wire.begin() + body_off, wire.end());
    return env;
}

}  // namespace wn_steam
