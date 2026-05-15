#include "wn_steam/pb/cauthentication.h"

#include <cstring>  // std::memcpy for float deserialization

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

// ---------------------------------------------------------------------------
// CAuthentication_DeviceDetails
// ---------------------------------------------------------------------------

std::vector<uint8_t> CAuthentication_DeviceDetails::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.string_field(1, device_friendly_name);
    w.int32_field( 2, static_cast<int32_t>(platform_type));
    w.int32_field( 3, os_type);
    w.uint32_field(4, gaming_device_type);
    w.uint32_field(5, client_count);
    w.bytes_field( 6, std::span<const uint8_t>(machine_id.data(), machine_id.size()));
    return out;
}

// ---------------------------------------------------------------------------
// CAuthentication_GetPasswordRSAPublicKey
// ---------------------------------------------------------------------------

std::vector<uint8_t>
CAuthentication_GetPasswordRSAPublicKey_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.string_field(1, account_name);
    return out;
}

std::optional<CAuthentication_GetPasswordRSAPublicKey_Response>
CAuthentication_GetPasswordRSAPublicKey_Response::deserialize(
    std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CAuthentication_GetPasswordRSAPublicKey_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.string(); v) m.publickey_mod = std::move(*v);
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.publickey_exp = std::move(*v);
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.u64(); v) m.timestamp = *v;
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
// CAuthentication_BeginAuthSessionViaCredentials_Request
// ---------------------------------------------------------------------------

std::vector<uint8_t>
CAuthentication_BeginAuthSessionViaCredentials_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);

    // Field numbers verified against the canonical
    // steammessages_auth.steamclient.proto on SteamDatabase/Protobufs.
    // The earlier numbering (3↔4 off-by-one, 7↔8 SWAPPED) was wrong; Steam
    // silently returned a 7-byte stub response (just `interval=5.0` and
    // an empty `extended_error_message`) for every credentials attempt.
    //   1  device_friendly_name (legacy, not emitted)
    //   2  account_name
    //   3  encrypted_password
    //   4  encryption_timestamp
    //   5  remember_login (deprecated, not emitted)
    //   6  platform_type (legacy, not emitted — set inside device_details)
    //   7  persistence
    //   8  website_id
    //   9  device_details
    //   10 guard_data
    //   11 language
    //   12 qos_level
    w.string_field(2, account_name);
    w.string_field(3, encrypted_password);
    w.uint64_field(4, encryption_timestamp);
    w.int32_field( 7, static_cast<int32_t>(persistence));
    w.string_field(8, website_id);

    auto dd_body = device_details.serialize();
    if (!dd_body.empty()) {
        w.submessage_field(9, std::span<const uint8_t>(dd_body.data(), dd_body.size()));
    }

    w.string_field(10, guard_data);
    w.uint32_field(11, language);
    w.int32_field( 12, qos_level);
    return out;
}

// ---------------------------------------------------------------------------
// CAuthentication_AllowedConfirmation
// ---------------------------------------------------------------------------

std::optional<CAuthentication_AllowedConfirmation>
CAuthentication_AllowedConfirmation::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CAuthentication_AllowedConfirmation m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.i32(); v)
                    m.confirmation_type = static_cast<EAuthSessionGuardType>(*v);
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.associated_message = std::move(*v);
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
// CAuthentication_BeginAuthSessionViaCredentials_Response
// ---------------------------------------------------------------------------

std::optional<CAuthentication_BeginAuthSessionViaCredentials_Response>
CAuthentication_BeginAuthSessionViaCredentials_Response::deserialize(
    std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CAuthentication_BeginAuthSessionViaCredentials_Response m;

    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u64(); v) m.client_id = *v; else return std::nullopt;
                break;
            case 2:
                if (auto v = r.bytes(); v) m.request_id.assign(v->begin(), v->end());
                else return std::nullopt;
                break;
            case 3:
                if (t->wire_type != proto::WireType::Fixed32) return std::nullopt;
                if (auto v = r.fixed32(); v) {
                    // Reinterpret 4 LE bytes as IEEE 754 float
                    float f;
                    uint32_t u = *v;
                    std::memcpy(&f, &u, sizeof(float));
                    m.interval = f;
                } else return std::nullopt;
                break;
            case 4: {
                auto sub = r.bytes();
                if (!sub) return std::nullopt;
                auto ac = CAuthentication_AllowedConfirmation::deserialize(*sub);
                if (!ac) return std::nullopt;
                m.allowed_confirmations.push_back(std::move(*ac));
                break;
            }
            case 5:
                if (auto v = r.u64(); v) m.steamid = *v; else return std::nullopt;
                break;
            case 6:
                if (auto v = r.string(); v) m.weak_token = std::move(*v);
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.string(); v) m.agreement_session_url = std::move(*v);
                else return std::nullopt;
                break;
            case 8:
                if (auto v = r.string(); v) m.extended_error_message = std::move(*v);
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
// CAuthentication_PollAuthSessionStatus
// ---------------------------------------------------------------------------

std::vector<uint8_t>
CAuthentication_PollAuthSessionStatus_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint64_field(1, client_id);
    w.bytes_field( 2, std::span<const uint8_t>(request_id.data(), request_id.size()));
    if (token_to_revoke != 0) {
        w.tag(3, proto::WireType::Fixed64);
        for (int i = 0; i < 8; ++i) out.push_back(static_cast<uint8_t>(token_to_revoke >> (i * 8)));
    }
    return out;
}

std::optional<CAuthentication_PollAuthSessionStatus_Response>
CAuthentication_PollAuthSessionStatus_Response::deserialize(
    std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CAuthentication_PollAuthSessionStatus_Response m;

    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u64(); v) m.new_client_id = *v; else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.new_challenge_url = std::move(*v);
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.string(); v) m.refresh_token = std::move(*v);
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.string(); v) m.access_token = std::move(*v);
                else return std::nullopt;
                break;
            case 5:
                if (auto v = r.boolean(); v) m.had_remote_interaction = *v;
                else return std::nullopt;
                break;
            case 6:
                if (auto v = r.string(); v) m.account_name = std::move(*v);
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.string(); v) m.new_guard_data = std::move(*v);
                else return std::nullopt;
                break;
            case 8:
                if (auto v = r.string(); v) m.agreement_session_url = std::move(*v);
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
// CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request
// ---------------------------------------------------------------------------

std::vector<uint8_t>
CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint64_field(1, client_id);
    if (steamid != 0) {
        w.tag(2, proto::WireType::Fixed64);
        for (int i = 0; i < 8; ++i) out.push_back(static_cast<uint8_t>(steamid >> (i * 8)));
    }
    w.string_field(3, code);
    w.int32_field( 4, static_cast<int32_t>(code_type));
    return out;
}

// ---------------------------------------------------------------------------
// CAuthentication_BeginAuthSessionViaQR
// ---------------------------------------------------------------------------

std::vector<uint8_t>
CAuthentication_BeginAuthSessionViaQR_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.string_field(1, device_friendly_name);
    w.int32_field( 2, static_cast<int32_t>(platform_type));
    auto dd_body = device_details.serialize();
    if (!dd_body.empty()) {
        w.submessage_field(3, std::span<const uint8_t>(dd_body.data(), dd_body.size()));
    }
    w.string_field(4, website_id);
    return out;
}

std::optional<CAuthentication_BeginAuthSessionViaQR_Response>
CAuthentication_BeginAuthSessionViaQR_Response::deserialize(
    std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CAuthentication_BeginAuthSessionViaQR_Response m;

    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u64(); v) m.client_id = *v; else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.challenge_url = std::move(*v);
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.bytes(); v) m.request_id.assign(v->begin(), v->end());
                else return std::nullopt;
                break;
            case 4:
                if (t->wire_type != proto::WireType::Fixed32) return std::nullopt;
                if (auto v = r.fixed32(); v) {
                    float f;
                    uint32_t u = *v;
                    std::memcpy(&f, &u, sizeof(float));
                    m.interval = f;
                } else return std::nullopt;
                break;
            case 5: {
                auto sub = r.bytes();
                if (!sub) return std::nullopt;
                auto ac = CAuthentication_AllowedConfirmation::deserialize(*sub);
                if (!ac) return std::nullopt;
                m.allowed_confirmations.push_back(std::move(*ac));
                break;
            }
            case 6:
                if (auto v = r.i32(); v) m.version = *v; else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
