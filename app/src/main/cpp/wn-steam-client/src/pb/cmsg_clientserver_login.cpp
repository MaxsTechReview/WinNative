#include "wn_steam/pb/cmsg_clientserver_login.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

// ---------------------------------------------------------------------------
// CMsgClientHello
// ---------------------------------------------------------------------------

std::vector<uint8_t> CMsgClientHello::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, protocol_version);
    return out;
}

// ---------------------------------------------------------------------------
// CMsgClientHeartBeat
// ---------------------------------------------------------------------------

std::vector<uint8_t> CMsgClientHeartBeat::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.bool_field(1, send_reply);
    return out;
}

// ---------------------------------------------------------------------------
// CMsgClientLoggedOff
// ---------------------------------------------------------------------------

std::optional<CMsgClientLoggedOff>
CMsgClientLoggedOff::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientLoggedOff m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.i32(); v) m.eresult = *v; else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

// ---------------------------------------------------------------------------
// CMsgClientLogon — uses CANONICAL field numbers verified against
// steammessages_clientserver_login.proto. See the header for the full table.
// ---------------------------------------------------------------------------

std::vector<uint8_t> CMsgClientLogon::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);

    w.uint32_field(  1, protocol_version);
    w.uint32_field(  3, cell_id);
    w.uint32_field(  5, client_package_version);
    w.string_field(  6, client_language);
    w.uint32_field(  7, client_os_type);
    w.bool_field(    8, should_remember_password);
    w.uint32_field( 21, qos_level);

    // Field 22 fixed64 — emit only when non-zero (CM resolves SteamID from
    // the refresh token if omitted).
    if (client_supplied_steam_id != 0) {
        w.tag(22, proto::WireType::Fixed64);
        for (int i = 0; i < 8; ++i) {
            out.push_back(static_cast<uint8_t>(client_supplied_steam_id >> (i * 8)));
        }
    }

    w.bytes_field(  30, std::span<const uint8_t>(machine_id.data(), machine_id.size()));

    // LoginID. JavaSteam SteamUser.kt:75-90 sets the modern field 95
    // (CMsgIPAddress { uint32 v4 = 1; }) and mirrors it onto the deprecated
    // field 31. Steam disambiguates concurrent same-account logons by this
    // value, so it must be present and unique per session.
    if (obfuscated_private_ip != 0) {
        w.uint32_field(31, obfuscated_private_ip);          // deprecated mirror
        std::vector<uint8_t> ip_msg;                        // CMsgIPAddress
        proto::Writer ipw(ip_msg);
        ipw.uint32_field(1, obfuscated_private_ip);         // v4
        w.bytes_field(95, std::span<const uint8_t>(ip_msg.data(), ip_msg.size()));
    }

    w.uint32_field( 32, ui_mode);
    w.uint32_field( 33, chat_mode);
    // Field 50 — account_name. JavaSteam sets this unconditionally even on
    // refresh-token logon; Steam uses it to cross-check the JWT subject.
    w.string_field( 50, account_name);
    w.string_field( 96, machine_name);
    w.uint64_field(100, client_instance_id);
    w.bool_field(  102, supports_rate_limit_response);
    w.string_field(108, access_token);

    return out;
}

// ---------------------------------------------------------------------------
// CMsgClientLogonResponse — uses CANONICAL field numbers (one-shift bug
// for fields 27–31 fixed, token_id type fixed: varint not fixed64,
// webapi_authenticate_user_nonce field removed — doesn't exist in this msg).
// ---------------------------------------------------------------------------

std::optional<CMsgClientLogonResponse>
CMsgClientLogonResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientLogonResponse m;

    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:  // eresult
                if (auto v = r.i32(); v) m.eresult = *v; else return std::nullopt;
                break;
            case 3:  // heartbeat_seconds
                if (auto v = r.i32(); v) m.heartbeat_seconds = *v; else return std::nullopt;
                break;
            case 5:  // rtime32_server_time (fixed32)
                if (t->wire_type != proto::WireType::Fixed32) return std::nullopt;
                if (auto v = r.fixed32(); v) m.rtime32_server_time = *v;
                else return std::nullopt;
                break;
            case 7:  // cell_id
                if (auto v = r.u32(); v) m.cell_id = *v; else return std::nullopt;
                break;
            case 10: // eresult_extended
                if (auto v = r.i32(); v) m.eresult_extended = *v; else return std::nullopt;
                break;
            case 14: // vanity_url
                if (auto v = r.string(); v) m.vanity_url = std::move(*v);
                else return std::nullopt;
                break;
            case 20: // client_supplied_steamid (fixed64)
                if (t->wire_type != proto::WireType::Fixed64) return std::nullopt;
                if (auto v = r.fixed64(); v) m.client_supplied_steamid = *v;
                else return std::nullopt;
                break;
            case 27: // client_instance_id
                if (auto v = r.u64(); v) m.client_instance_id = *v;
                else return std::nullopt;
                break;
            case 28: // force_client_update_check
                if (auto v = r.boolean(); v) m.force_client_update_check = *v;
                else return std::nullopt;
                break;
            case 29: // agreement_session_url
                if (auto v = r.string(); v) m.agreement_session_url = std::move(*v);
                else return std::nullopt;
                break;
            case 30: // token_id (uint64 VARINT — not fixed64!)
                if (auto v = r.u64(); v) m.token_id = *v;
                else return std::nullopt;
                break;
            case 31: // family_group_id
                if (auto v = r.u64(); v) m.family_group_id = *v;
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
