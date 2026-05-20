#include "wn_steam/pb/ccontentserverdirectory.h"

#include <algorithm>
#include <cctype>
#include <cstring>

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

// ---------------------------------------------------------------------------
// GetManifestRequestCode
// ---------------------------------------------------------------------------

std::vector<uint8_t> CContentServerDirectory_GetManifestRequestCode_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, app_id);
    w.uint32_field(2, depot_id);
    w.uint64_field(3, manifest_id);
    if (!app_branch.empty())           w.string_field(4, app_branch);
    if (!branch_password_hash.empty()) w.string_field(5, branch_password_hash);
    return out;
}

std::optional<CContentServerDirectory_GetManifestRequestCode_Response>
CContentServerDirectory_GetManifestRequestCode_Response::deserialize(
        std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CContentServerDirectory_GetManifestRequestCode_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u64(); v) m.manifest_request_code = *v;
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
// ServerInfo
// ---------------------------------------------------------------------------

bool CContentServerDirectory_ServerInfo::use_https() const noexcept {
    std::string s = https_support;
    std::transform(s.begin(), s.end(), s.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return s == "mandatory";
}

std::optional<CContentServerDirectory_ServerInfo>
CContentServerDirectory_ServerInfo::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CContentServerDirectory_ServerInfo m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.string(); v) m.type = std::move(*v);
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.i32(); v) m.source_id = *v;
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.i32(); v) m.cell_id = *v;
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.i32(); v) m.load = *v;
                else return std::nullopt;
                break;
            case 5: {
                // float — fixed32 wire type; reinterpret the 4 raw bytes.
                auto v = r.fixed32();
                if (!v) return std::nullopt;
                uint32_t bits = *v;
                std::memcpy(&m.weighted_load, &bits, sizeof(float));
                break;
            }
            case 6:
                if (auto v = r.i32(); v) m.num_entries_in_client_list = *v;
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.boolean(); v) m.steam_china_only = *v;
                else return std::nullopt;
                break;
            case 8:
                if (auto v = r.string(); v) m.host = std::move(*v);
                else return std::nullopt;
                break;
            case 9:
                if (auto v = r.string(); v) m.vhost = std::move(*v);
                else return std::nullopt;
                break;
            case 10:
                if (auto v = r.boolean(); v) m.use_as_proxy = *v;
                else return std::nullopt;
                break;
            case 11:
                if (auto v = r.string(); v) m.proxy_request_path_template = std::move(*v);
                else return std::nullopt;
                break;
            case 12:
                if (auto v = r.string(); v) m.https_support = std::move(*v);
                else return std::nullopt;
                break;
            case 13:
                // repeated uint32 — packed (length-delimited) or unpacked
                // (one varint per occurrence). Handle both.
                if (t->wire_type == proto::WireType::LengthDelimited) {
                    auto b = r.bytes();
                    if (!b) return std::nullopt;
                    proto::Reader pr(*b);
                    while (!pr.eof()) {
                        auto pv = pr.varint();
                        if (!pv) {
                            if (!pr.ok()) return std::nullopt;
                            break;
                        }
                        m.allowed_app_ids.push_back(static_cast<uint32_t>(*pv));
                    }
                } else {
                    if (auto v = r.u32(); v) m.allowed_app_ids.push_back(*v);
                    else return std::nullopt;
                }
                break;
            case 15:
                if (auto v = r.u32(); v) m.priority_class = *v;
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
// GetServersForSteamPipe
// ---------------------------------------------------------------------------

std::vector<uint8_t> CContentServerDirectory_GetServersForSteamPipe_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, cell_id);
    // max_servers default is 20; uint32_field omits a zero, so a caller that
    // explicitly wants 0 cannot express it — irrelevant here (0 is invalid).
    w.uint32_field(2, max_servers);
    return out;
}

std::optional<CContentServerDirectory_GetServersForSteamPipe_Response>
CContentServerDirectory_GetServersForSteamPipe_Response::deserialize(
        std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CContentServerDirectory_GetServersForSteamPipe_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto srv = CContentServerDirectory_ServerInfo::deserialize(*b);
                if (!srv) return std::nullopt;
                m.servers.push_back(std::move(*srv));
                break;
            }
            case 2:
                if (auto v = r.boolean(); v) m.no_change = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
