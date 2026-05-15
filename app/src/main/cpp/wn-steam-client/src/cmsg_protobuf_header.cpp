#include "wn_steam/cmsg_protobuf_header.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam {

void CMsgProtoBufHeader::serialize(std::vector<uint8_t>& out) const {
    proto::Writer w(out);

    // 1 fixed64 steamid
    if (steamid != 0) {
        w.tag(1, proto::WireType::Fixed64);
        // fixed64 inline (no helper for arbitrary fixed64 here — Writer
        // exposes fixed64_field but that skips zero-by-default; we already
        // checked).
        for (int i = 0; i < 8; ++i) out.push_back(static_cast<uint8_t>(steamid >> (i * 8)));
    }

    // 2 int32 client_sessionid
    w.int32_field(2, client_sessionid);

    // 3 uint32 routing_appid
    w.uint32_field(3, routing_appid);

    // 10 fixed64 jobid_source — emit only when SET. The earlier comment
    // claiming "always emit" was wrong; SteamKit2's protobuf-net codegen
    // uses ShouldSerialize() which returns false when the field is null,
    // and JavaSteam's protobuf-java builder only emits fields the caller
    // explicitly set. The wire convention is "field absent = no job",
    // not "field present + sentinel value = no job".
    if (jobid_source != kInvalidJobId) {
        w.tag(10, proto::WireType::Fixed64);
        for (int i = 0; i < 8; ++i) out.push_back(static_cast<uint8_t>(jobid_source >> (i * 8)));
    }

    // 11 fixed64 jobid_target — same rule. CRITICAL: emitting this on an
    // outbound service-method call causes Steam to interpret the message
    // as a *reply* to a (nonexistent) job 0xFFFF..., which it silently
    // rejects with a stub response containing only `interval=5.0` and an
    // empty `extended_error_message`. This was the silent-fail bug for
    // BeginAuthSessionViaCredentials.
    if (jobid_target != kInvalidJobId) {
        w.tag(11, proto::WireType::Fixed64);
        for (int i = 0; i < 8; ++i) out.push_back(static_cast<uint8_t>(jobid_target >> (i * 8)));
    }

    // 12 string target_job_name
    w.string_field(12, target_job_name);

    // 14 int32 eresult — emit only on responses that explicitly set it.
    // Clients (us) normally never set eresult on outbound, so this branch
    // is a no-op in practice (default = -1, our sentinel for "unset").
    if (eresult >= 0) {
        w.tag(14, proto::WireType::Varint);
        w.varint(static_cast<uint64_t>(static_cast<int64_t>(eresult)));
    }

    // 15 string error_message
    w.string_field(15, error_message);

    // 29 uint32 realm
    w.uint32_field(29, realm);

    // 21 uint64 messageid
    w.uint64_field(21, messageid);

    // 34 uint64 token_id
    w.uint64_field(34, token_id);
}

std::optional<CMsgProtoBufHeader>
CMsgProtoBufHeader::deserialize(std::span<const uint8_t> bytes) noexcept {
    proto::Reader r(bytes);
    CMsgProtoBufHeader h;

    while (!r.eof()) {
        auto tag = r.next_tag();
        if (!tag) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (tag->field_number) {
            case 1:  // steamid (fixed64)
                if (tag->wire_type != proto::WireType::Fixed64) return std::nullopt;
                if (auto v = r.fixed64(); v) h.steamid = *v; else return std::nullopt;
                break;
            case 2:  // client_sessionid (int32 varint)
                if (auto v = r.i32(); v) h.client_sessionid = *v; else return std::nullopt;
                break;
            case 3:  // routing_appid (uint32 varint)
                if (auto v = r.u32(); v) h.routing_appid = *v; else return std::nullopt;
                break;
            case 10: // jobid_source (fixed64)
                if (tag->wire_type != proto::WireType::Fixed64) return std::nullopt;
                if (auto v = r.fixed64(); v) h.jobid_source = *v; else return std::nullopt;
                break;
            case 11: // jobid_target (fixed64)
                if (tag->wire_type != proto::WireType::Fixed64) return std::nullopt;
                if (auto v = r.fixed64(); v) h.jobid_target = *v; else return std::nullopt;
                break;
            case 12: // target_job_name (string)
                if (auto v = r.string(); v) h.target_job_name = std::move(*v); else return std::nullopt;
                break;
            case 14: // eresult (int32 varint)
                if (auto v = r.i32(); v) h.eresult = *v; else return std::nullopt;
                break;
            case 15: // error_message (string)
                if (auto v = r.string(); v) h.error_message = std::move(*v); else return std::nullopt;
                break;
            case 21: // messageid (uint64 varint)
                if (auto v = r.u64(); v) h.messageid = *v; else return std::nullopt;
                break;
            case 29: // realm (uint32 varint)
                if (auto v = r.u32(); v) h.realm = *v; else return std::nullopt;
                break;
            case 34: // token_id (uint64 varint)
                if (auto v = r.u64(); v) h.token_id = *v; else return std::nullopt;
                break;
            default:
                if (!r.skip(tag->wire_type)) return std::nullopt;
                break;
        }
    }
    return h;
}

}  // namespace wn_steam
