#include "wn_steam/pb/cmsg_client_pics.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

namespace {

// Repeated uint32 fields in PICS messages may arrive packed (length-delimited
// blob of concatenated varints) OR unpacked (one tag per element, each a
// bare varint). A robust parser handles both — appends one element when the
// wire type is varint, or iterates a sub-buffer of varints when length-delim.
[[nodiscard]] bool read_repeated_uint32(proto::Reader& r,
                                        proto::WireType wt,
                                        std::vector<uint32_t>& out) noexcept {
    if (wt == proto::WireType::Varint) {
        auto v = r.u32();
        if (!v) return false;
        out.push_back(*v);
        return true;
    }
    if (wt == proto::WireType::LengthDelimited) {
        auto bytes = r.bytes();
        if (!bytes) return false;
        proto::Reader sub(*bytes);
        while (!sub.eof()) {
            auto v = sub.varint();
            if (!v) return false;
            out.push_back(static_cast<uint32_t>(*v));
        }
        return true;
    }
    return false;
}

// Submessage parsers — each takes a body sub-span and returns true on success.

[[nodiscard]] bool parse_package_token(std::span<const uint8_t> body,
                                       PicsPackageToken& m) noexcept {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) return r.ok();
        switch (t->field_number) {
            case 1: if (auto v = r.u32(); v) m.packageid    = *v; else return false; break;
            case 2: if (auto v = r.u64(); v) m.access_token = *v; else return false; break;
            default: if (!r.skip(t->wire_type)) return false; break;
        }
    }
    return true;
}

[[nodiscard]] bool parse_app_token(std::span<const uint8_t> body,
                                   PicsAppToken& m) noexcept {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) return r.ok();
        switch (t->field_number) {
            case 1: if (auto v = r.u32(); v) m.appid        = *v; else return false; break;
            case 2: if (auto v = r.u64(); v) m.access_token = *v; else return false; break;
            default: if (!r.skip(t->wire_type)) return false; break;
        }
    }
    return true;
}

[[nodiscard]] bool parse_app_info_resp(std::span<const uint8_t> body,
                                       PicsAppInfoResp& m) noexcept {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) return r.ok();
        switch (t->field_number) {
            case 1: if (auto v = r.u32();     v) m.appid         = *v; else return false; break;
            case 2: if (auto v = r.u32();     v) m.change_number = *v; else return false; break;
            case 3: if (auto v = r.boolean(); v) m.missing_token = *v; else return false; break;
            case 4: {
                auto b = r.bytes(); if (!b) return false;
                m.sha.assign(b->begin(), b->end());
                break;
            }
            case 5: {
                auto b = r.bytes(); if (!b) return false;
                m.buffer.assign(b->begin(), b->end());
                break;
            }
            case 6: if (auto v = r.boolean(); v) m.only_public  = *v; else return false; break;
            case 7: if (auto v = r.u32();     v) m.size         = *v; else return false; break;
            default: if (!r.skip(t->wire_type)) return false; break;
        }
    }
    return true;
}

[[nodiscard]] bool parse_package_info_resp(std::span<const uint8_t> body,
                                           PicsPackageInfoResp& m) noexcept {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) return r.ok();
        switch (t->field_number) {
            case 1: if (auto v = r.u32();     v) m.packageid     = *v; else return false; break;
            case 2: if (auto v = r.u32();     v) m.change_number = *v; else return false; break;
            case 3: if (auto v = r.boolean(); v) m.missing_token = *v; else return false; break;
            case 4: {
                auto b = r.bytes(); if (!b) return false;
                m.sha.assign(b->begin(), b->end());
                break;
            }
            case 5: {
                auto b = r.bytes(); if (!b) return false;
                // NOTE: package buffer has a 4-byte LE packageid PREFIX before
                // the KeyValues binary. We store the buffer as-is here — the
                // VDF parser will strip the prefix when reading.
                m.buffer.assign(b->begin(), b->end());
                break;
            }
            case 6: if (auto v = r.u32();     v) m.size          = *v; else return false; break;
            default: if (!r.skip(t->wire_type)) return false; break;
        }
    }
    return true;
}

}  // namespace

// ---------------------------------------------------------------------------
// CMsgClientPICSAccessTokenRequest
// ---------------------------------------------------------------------------

std::vector<uint8_t> CMsgClientPICSAccessTokenRequest::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    // Emit unpacked (one tag per element). Steam accepts both packed and
    // unpacked; unpacked is simpler and lets us reuse uint32_field_force.
    for (uint32_t id : packageids) {
        w.tag(1, proto::WireType::Varint);
        w.varint(id);
    }
    for (uint32_t id : appids) {
        w.tag(2, proto::WireType::Varint);
        w.varint(id);
    }
    return out;
}

namespace {
// PackageChange and AppChange share an identical layout:
//   1 uint32 id, 2 uint32 change_number, 3 bool needs_token.
struct PicsChangeRaw {
    uint32_t id            = 0;
    uint32_t change_number = 0;
    bool     needs_token   = false;
};
std::optional<PicsChangeRaw> parse_pics_change(std::span<const uint8_t> body) {
    proto::Reader r(body);
    PicsChangeRaw m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u32(); v) m.id = *v;
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.u32(); v) m.change_number = *v;
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.boolean(); v) m.needs_token = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}
}  // namespace

std::vector<uint8_t> CMsgClientPICSChangesSinceRequest::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    // Force-emit: since_change_number = 0 is a meaningful value (the full-state
    // poll cursor), so it must go on the wire, not be dropped as a default.
    w.uint32_field_force(1, since_change_number);
    w.bool_field_force(2, send_app_info_changes);
    w.bool_field_force(3, send_package_info_changes);
    return out;
}

std::optional<CMsgClientPICSChangesSinceResponse>
CMsgClientPICSChangesSinceResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientPICSChangesSinceResponse m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u32(); v) m.current_change_number = *v;
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.u32(); v) m.since_change_number = *v;
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.boolean(); v) m.force_full_update = *v;
                else return std::nullopt;
                break;
            case 4: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto c = parse_pics_change(*b);
                if (!c) return std::nullopt;
                m.package_changes.push_back({c->id, c->change_number, c->needs_token});
                break;
            }
            case 5: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto c = parse_pics_change(*b);
                if (!c) return std::nullopt;
                m.app_changes.push_back({c->id, c->change_number, c->needs_token});
                break;
            }
            case 6:
                if (auto v = r.boolean(); v) m.force_full_app_update = *v;
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.boolean(); v) m.force_full_package_update = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

// ---------------------------------------------------------------------------
// CMsgClientPICSAccessTokenResponse
// ---------------------------------------------------------------------------

std::optional<CMsgClientPICSAccessTokenResponse>
CMsgClientPICSAccessTokenResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientPICSAccessTokenResponse m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1: {
                auto sub = r.bytes(); if (!sub) return std::nullopt;
                PicsPackageToken pt;
                if (!parse_package_token(*sub, pt)) return std::nullopt;
                m.package_access_tokens.push_back(pt);
                break;
            }
            case 2:
                if (!read_repeated_uint32(r, t->wire_type, m.package_denied_tokens))
                    return std::nullopt;
                break;
            case 3: {
                auto sub = r.bytes(); if (!sub) return std::nullopt;
                PicsAppToken at;
                if (!parse_app_token(*sub, at)) return std::nullopt;
                m.app_access_tokens.push_back(at);
                break;
            }
            case 4:
                if (!read_repeated_uint32(r, t->wire_type, m.app_denied_tokens))
                    return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

// ---------------------------------------------------------------------------
// CMsgClientPICSProductInfoRequest
// ---------------------------------------------------------------------------

std::vector<uint8_t> CMsgClientPICSProductInfoRequest::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);

    // packages (field 1) — submessage per element
    for (const auto& p : packages) {
        std::vector<uint8_t> sub;
        proto::Writer sw(sub);
        sw.uint32_field(1, p.packageid);
        sw.uint64_field(2, p.access_token);
        w.submessage_field(1, sub);
    }
    // apps (field 2) — submessage per element
    for (const auto& a : apps) {
        std::vector<uint8_t> sub;
        proto::Writer sw(sub);
        sw.uint32_field(1, a.appid);
        sw.uint64_field(2, a.access_token);
        sw.bool_field(  3, a.only_public_obsolete);
        w.submessage_field(2, sub);
    }
    w.bool_field(   3, meta_data_only);
    w.uint32_field( 4, num_prev_failed);
    w.uint32_field( 6, sequence_number);
    w.bool_field(   7, single_response);
    return out;
}

// ---------------------------------------------------------------------------
// CMsgClientPICSProductInfoResponse
// ---------------------------------------------------------------------------

std::optional<CMsgClientPICSProductInfoResponse>
CMsgClientPICSProductInfoResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientPICSProductInfoResponse m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1: {
                auto sub = r.bytes(); if (!sub) return std::nullopt;
                PicsAppInfoResp a;
                if (!parse_app_info_resp(*sub, a)) return std::nullopt;
                m.apps.push_back(std::move(a));
                break;
            }
            case 2:
                if (!read_repeated_uint32(r, t->wire_type, m.unknown_appids))
                    return std::nullopt;
                break;
            case 3: {
                auto sub = r.bytes(); if (!sub) return std::nullopt;
                PicsPackageInfoResp p;
                if (!parse_package_info_resp(*sub, p)) return std::nullopt;
                m.packages.push_back(std::move(p));
                break;
            }
            case 4:
                if (!read_repeated_uint32(r, t->wire_type, m.unknown_packageids))
                    return std::nullopt;
                break;
            case 5: if (auto v = r.boolean(); v) m.meta_data_only   = *v; else return std::nullopt; break;
            case 6: if (auto v = r.boolean(); v) m.response_pending = *v; else return std::nullopt; break;
            case 7: if (auto v = r.u32();     v) m.http_min_size    = *v; else return std::nullopt; break;
            case 8: if (auto v = r.string();  v) m.http_host        = std::move(*v); else return std::nullopt; break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
