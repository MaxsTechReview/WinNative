#include "wn_steam/cm_client.h"

#include <android/log.h>

#include <zlib.h>

#include "wn_steam/pb/cmsg_clientserver_login.h"
#include "wn_steam/proto_envelope.h"
#include "wn_steam/proto_wire.h"
#include "wn_steam/wire_format.h"
#include "wn_steam/ws_connection.h"

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamCM";
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

template <typename Cb, typename... Args>
void safe_invoke(Cb& cb, Args&&... args) {
    if (!cb) return;
    try { cb(std::forward<Args>(args)...); }
    catch (const std::exception& e) { WN_LOGE("client callback threw: %s", e.what()); }
    catch (...) { WN_LOGE("client callback threw unknown"); }
}

// CMsgMulti envelope. Steam wraps almost all responses in this even when
// there's only one inner message. Fields:
//   1 uint32 size_unzipped (varint) — non-zero ⇒ message_body is gzip'd
//   2 bytes  message_body  — sequence of [u32 LE length][message bytes] records
struct CMsgMulti {
    uint32_t              size_unzipped = 0;
    std::vector<uint8_t>  message_body;
};

bool parse_cmsg_multi(std::span<const uint8_t> body, CMsgMulti& out) {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) return r.ok();
        switch (t->field_number) {
            case 1:
                if (auto v = r.u32(); v) out.size_unzipped = *v; else return false;
                break;
            case 2:
                if (auto v = r.bytes(); v) {
                    out.message_body.assign(v->begin(), v->end());
                } else return false;
                break;
            default:
                if (!r.skip(t->wire_type)) return false;
                break;
        }
    }
    return true;
}

// Inflate a gzip-wrapped buffer using zlib (already linked for CRC32).
// `expected_size` is a hint; the loop grows the buffer as needed if zero
// or if Steam under-estimated.
std::vector<uint8_t> gunzip(std::span<const uint8_t> compressed,
                            size_t expected_size) {
    std::vector<uint8_t> out;
    out.resize(expected_size > 0 ? expected_size
                                 : std::max<size_t>(compressed.size() * 4, 1024));

    z_stream zs{};
    // 15 = max window bits; +32 enables auto-detection of gzip/zlib wrapper.
    if (inflateInit2(&zs, 15 + 32) != Z_OK) {
        WN_LOGE("inflateInit2 failed");
        return {};
    }
    zs.next_in   = const_cast<Bytef*>(compressed.data());
    zs.avail_in  = static_cast<uInt>(compressed.size());
    zs.next_out  = out.data();
    zs.avail_out = static_cast<uInt>(out.size());

    int ret;
    while (true) {
        ret = inflate(&zs, Z_NO_FLUSH);
        if (ret == Z_STREAM_END) break;
        if (ret != Z_OK) {
            WN_LOGE("inflate failed rc=%d (avail_out=%u total_out=%lu)",
                    ret, zs.avail_out, static_cast<unsigned long>(zs.total_out));
            inflateEnd(&zs);
            return {};
        }
        if (zs.avail_out == 0) {
            const size_t old_size = out.size();
            out.resize(old_size * 2);
            zs.next_out  = out.data() + old_size;
            zs.avail_out = static_cast<uInt>(out.size() - old_size);
        }
    }
    out.resize(zs.total_out);
    inflateEnd(&zs);
    return out;
}
}  // namespace

CMClient::CMClient() {
    auto ws = std::make_unique<WsConnection>();
    channel_ = std::make_unique<EncryptedChannel>(std::move(ws));
    channel_->set_on_connected([this]() { on_channel_connected(); });
    channel_->set_on_disconnected(
        [this](ChannelDisconnectReason r, const std::string& d) {
            on_channel_disconnected(r, d);
        });
    channel_->set_on_message(
        [this](std::span<const uint8_t> bytes) { on_channel_message(bytes); });
}

CMClient::~CMClient() {
    disconnect();
}

void CMClient::set_ca_bundle_path(const std::string& path) {
    if (channel_) channel_->set_ca_bundle_path(path);
}

bool CMClient::connect(const std::string& url) {
    auto expected = ClientState::Disconnected;
    if (!state_.compare_exchange_strong(expected, ClientState::Connecting)) {
        return false;
    }
    set_state_locked_(ClientState::Connecting);
    if (!channel_->connect(url)) {
        set_state_locked_(ClientState::Disconnected);
        return false;
    }
    return true;
}

void CMClient::disconnect() {
    heartbeat_.stop();
    if (channel_) channel_->disconnect();
    jobs_.fail_all("CMClient disconnected");
    set_state_locked_(ClientState::Disconnected);
    steam_id_.store(0);
    session_id_.store(0);
}

void CMClient::call_service_method(std::string_view method_name,
                                   bool authed,
                                   std::span<const uint8_t> request_body,
                                   JobContinuation cb,
                                   std::chrono::seconds timeout) {
    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, std::move(cb), timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    hdr.target_job_name.assign(method_name.begin(), method_name.end());

    const EMsg outbound = authed ? EMsg::ServiceMethodCallFromClient
                                 : EMsg::ServiceMethodCallFromClientNonAuthed;
    WN_LOGI("outbound service_method=\"%.*s\" authed=%d jobid_source=0x%llx body=%zu bytes",
            static_cast<int>(method_name.size()), method_name.data(),
            authed ? 1 : 0,
            static_cast<unsigned long long>(job_id),
            request_body.size());
    auto wire = encode_proto_envelope(outbound, hdr, request_body);
    if (!channel_->send(wire)) {
        WN_LOGE("channel->send failed for service method \"%.*s\"",
                static_cast<int>(method_name.size()), method_name.data());
        // Synthetically fail this job so the continuation fires.
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

bool CMClient::send_proto_message(EMsg emsg, std::span<const uint8_t> body) {
    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    // Pre-logon, the steam_id_ atomic is 0. For ClientLogon Steam rejects a
    // zero header steamid with EResult.InvalidPassword (5) regardless of how
    // valid the refresh token is. JavaSteam/SteamKit send the placeholder
    // "anonymous Individual desktop" SteamID (universe=Public, type=Individual,
    // instance=Desktop=1, accountId=0) → 0x0110000100000000. Steam echoes the
    // real SteamID back in ClientLogonResponse.client_supplied_steamid.
    if (emsg == EMsg::ClientLogon && hdr.steamid == 0) {
        hdr.steamid = 0x0110000100000000ULL;
    }
    auto wire = encode_proto_envelope(emsg, hdr, body);
    return channel_->send(wire);
}

bool CMClient::logon_with_refresh_token(const std::string& refresh_token,
                                         const std::string& account_name,
                                         uint64_t client_supplied_steam_id) {
    if (state_.load() != ClientState::Connected) return false;
    pb::CMsgClientLogon msg;
    msg.access_token             = refresh_token;
    msg.client_supplied_steam_id = client_supplied_steam_id;  // 0 → omitted on wire
    msg.account_name             = account_name;             // REQUIRED — see field 50 note
    // Steam dislikes empty machine_id on user logon. JavaSteam HardwareUtils
    // falls back to the literal ASCII "JavaSteam-SerialNumber" when no OS
    // serial is available; we send our own constant marker.
    static constexpr const char kMachineIdMarker[] = "WN-Steam-Client";
    msg.machine_id.assign(kMachineIdMarker,
                          kMachineIdMarker + sizeof(kMachineIdMarker) - 1);

    // client_instance_id: random non-zero u64 so concurrent sessions from
    // the same account don't collide.
    auto k = generate_session_key();
    if (k) {
        const auto& b = k->bytes;
        uint64_t r = 0;
        for (int i = 0; i < 8; ++i) r |= static_cast<uint64_t>(b[i]) << (i * 8);
        if (r == 0) r = 1;
        msg.client_instance_id = r;
    }
    return send_proto_message(EMsg::ClientLogon, msg.serialize());
}

void CMClient::set_on_state(StateCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_state_ = std::move(cb);
}

void CMClient::set_on_client_message(ClientMessageCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_client_message_ = std::move(cb);
}

// ---------------------------------------------------------------------------
// Channel callbacks
// ---------------------------------------------------------------------------

void CMClient::on_channel_connected() {
    set_state_locked_(ClientState::Connected);
    WN_LOGI("encrypted channel up; sending ClientHello");
    pb::CMsgClientHello hello;
    send_proto_message(EMsg::ClientHello, hello.serialize());
}

void CMClient::on_channel_disconnected(ChannelDisconnectReason r, const std::string& detail) {
    (void)r;
    WN_LOGI("channel disconnected: %s", detail.c_str());
    heartbeat_.stop();
    jobs_.fail_all("channel disconnected: " + detail);
    steam_id_.store(0);
    session_id_.store(0);
    set_state_locked_(ClientState::Disconnected);
}

void CMClient::on_channel_message(std::span<const uint8_t> bytes) {
    // Most application-layer messages on WSS are protobuf-flagged. Try
    // that path first.
    auto env = decode_proto_envelope(bytes);
    if (env) {
        route_inbound_(env->emsg, env->header, env->body);
        return;
    }

    // Non-proto fallback: Steam still ships some legacy struct messages
    // over WSS even though the official clients don't need them. The most
    // common is `ChannelEncryptRequest` (sent right after the WS opens
    // alongside our `ClientHello`). The modern SteamKit / JavaSteam /
    // steam-vent clients silently ignore these because the actual app-
    // layer encryption is handled by TLS. We do the same.
    if (bytes.size() >= 4) {
        const uint32_t raw_emsg = wire::read_u32_le(bytes.data());
        if (!emsg_has_proto_flag(raw_emsg)) {
            const EMsg legacy = emsg_strip_proto_flag(raw_emsg);
            switch (legacy) {
                case EMsg::ChannelEncryptRequest:
                case EMsg::ChannelEncryptResponse:
                case EMsg::ChannelEncryptResult:
                    WN_LOGI("ignored legacy %u-byte ChannelEncrypt* message on WSS "
                            "(emsg=%u — TLS handles encryption)",
                            static_cast<unsigned>(bytes.size()),
                            static_cast<unsigned>(legacy));
                    return;
                default:
                    WN_LOGE("unexpected non-proto inbound emsg=%u, size=%zu (dropping)",
                            static_cast<unsigned>(legacy), bytes.size());
                    return;
            }
        }
    }

    WN_LOGE("decode_proto_envelope failed (size=%zu)", bytes.size());
}

// ---------------------------------------------------------------------------
// Inbound routing
// ---------------------------------------------------------------------------

void CMClient::route_inbound_(EMsg emsg,
                              const CMsgProtoBufHeader& header,
                              std::span<const uint8_t> body) {
    WN_LOGI("inbound emsg=%u eresult=%d jobid_target=0x%llx "
            "target_job_name=\"%s\" body=%zu bytes",
            static_cast<unsigned>(emsg),
            header.eresult,
            static_cast<unsigned long long>(header.jobid_target),
            header.target_job_name.c_str(),
            body.size());

    // Diagnostic: dump first 32 bytes of EVERY inbound body — small cost
    // for the visibility, lets us decode ClientLogonResponse rejections,
    // post-logon pushes, anything else without a code rebuild.
    if (!body.empty()) {
        char hex[3 * 32 + 1];
        size_t n = std::min<size_t>(body.size(), 32);
        size_t off = 0;
        for (size_t i = 0; i < n; ++i) {
            off += static_cast<size_t>(std::snprintf(hex + off, sizeof(hex) - off,
                                                     "%02x ", body[i]));
        }
        WN_LOGI("  body[0..%zu]: %s", n, hex);
    }

    switch (emsg) {
        case EMsg::Multi: {
            // Steam wraps virtually every response in a CMsgMulti envelope,
            // even when there is only a single inner message. The body is
            // a `[u32 length][message bytes]` record stream, optionally
            // gzip-compressed when size_unzipped > 0. We must inflate,
            // split, and recursively re-dispatch each inner message
            // through this same router.
            CMsgMulti multi;
            if (!parse_cmsg_multi(body, multi)) {
                WN_LOGE("CMsgMulti parse failed");
                return;
            }
            std::vector<uint8_t> unzipped;
            std::span<const uint8_t> records;
            if (multi.size_unzipped > 0) {
                unzipped = gunzip(multi.message_body, multi.size_unzipped);
                if (unzipped.empty()) {
                    WN_LOGE("CMsgMulti: gunzip yielded empty payload "
                            "(size_unzipped=%u, compressed=%zu bytes)",
                            multi.size_unzipped, multi.message_body.size());
                    return;
                }
                records = unzipped;
            } else {
                records = multi.message_body;
            }
            size_t off = 0;
            int dispatched = 0;
            while (off + 4 <= records.size()) {
                const uint32_t inner_len = wire::read_u32_le(records.data() + off);
                off += 4;
                if (inner_len == 0 || off + inner_len > records.size()) {
                    WN_LOGE("CMsgMulti: malformed inner record at offset %zu "
                            "(len=%u, remaining=%zu)",
                            off - 4, inner_len, records.size() - off);
                    break;
                }
                on_channel_message(records.subspan(off, inner_len));
                ++dispatched;
                off += inner_len;
            }
            WN_LOGI("CMsgMulti: dispatched %d inner messages", dispatched);
            break;
        }

        case EMsg::ServiceMethodResponse:
            jobs_.deliver(header.jobid_target,
                          header.eresult,
                          header.error_message,
                          body);
            break;

        case EMsg::ClientLogonResponse: {
            auto resp = pb::CMsgClientLogonResponse::deserialize(body);
            if (!resp) {
                WN_LOGE("CMsgClientLogonResponse parse failed");
                return;
            }
            if (resp->eresult == 1 /* EResult.OK */) {
                steam_id_.store(resp->client_supplied_steamid);
                // session_id is set in the response header, not the body.
                session_id_.store(header.client_sessionid);
                set_state_locked_(ClientState::LoggedOn);
                if (resp->heartbeat_seconds > 0) {
                    heartbeat_.start(
                        std::chrono::seconds(resp->heartbeat_seconds),
                        [this]() {
                            pb::CMsgClientHeartBeat hb;
                            send_proto_message(EMsg::ClientHeartBeat, hb.serialize());
                        });
                }
            }
            ClientMessageCallback cb;
            { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_client_message_; }
            safe_invoke(cb, emsg, header, body);
            break;
        }

        case EMsg::ClientLoggedOff:
        case EMsg::ClientServerUnavailable: {
            heartbeat_.stop();
            steam_id_.store(0);
            session_id_.store(0);
            ClientMessageCallback cb;
            { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_client_message_; }
            safe_invoke(cb, emsg, header, body);
            break;
        }

        // Server-pushed post-logon messages — Phase 3a decodes them as
        // opaque protobuf bodies (logs above already printed the EMsg +
        // size + first 32 bytes for ServiceMethodResponse). For other
        // pushes the upstream Kotlin observer logs them generically. We
        // tag them here so the log explicitly names them.
        case EMsg::ClientAccountInfo:
        case EMsg::ClientEmailAddrInfo:
        case EMsg::ClientLicenseList:
        case EMsg::ClientFriendsList:
        case EMsg::ClientPersonaState: {
            const char* name =
                (emsg == EMsg::ClientAccountInfo)  ? "ClientAccountInfo"  :
                (emsg == EMsg::ClientEmailAddrInfo)? "ClientEmailAddrInfo":
                (emsg == EMsg::ClientLicenseList)  ? "ClientLicenseList"  :
                (emsg == EMsg::ClientFriendsList)  ? "ClientFriendsList"  :
                                                     "ClientPersonaState";
            WN_LOGI("post-logon push: %s (%u bytes) — Phase 4+ will parse this; "
                    "for now forwarding to upstream observer for visibility",
                    name, static_cast<unsigned>(body.size()));
            ClientMessageCallback cb;
            { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_client_message_; }
            safe_invoke(cb, emsg, header, body);
            break;
        }

        default: {
            ClientMessageCallback cb;
            { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_client_message_; }
            safe_invoke(cb, emsg, header, body);
            break;
        }
    }
}

void CMClient::set_state_locked_(ClientState s) {
    state_.store(s);
    StateCallback cb;
    { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_state_; }
    safe_invoke(cb, s);
}

}  // namespace wn_steam
