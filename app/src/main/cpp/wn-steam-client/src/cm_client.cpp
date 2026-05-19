#include "wn_steam/cm_client.h"

#include <android/log.h>

#include <cctype>
#include <zlib.h>

#include "wn_steam/pb/cmsg_client_get_app_ownership_ticket.h"
#include "wn_steam/pb/cmsg_client_license_list.h"
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
    family_group_id_.store(0);
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

bool CMClient::send_proto_message(EMsg emsg, std::span<const uint8_t> body,
                                  uint32_t routing_appid) {
    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.routing_appid    = routing_appid;
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

    // client_instance_id + obfuscated_private_ip (LoginID): random per
    // session so concurrent same-account logons (e.g. our session alongside
    // a JavaSteam session, or two of our own) don't collide and boot each
    // other. Steam keys duplicate-session detection on the LoginID.
    auto k = generate_session_key();
    if (k) {
        const auto& b = k->bytes;
        uint64_t r = 0;
        for (int i = 0; i < 8; ++i) r |= static_cast<uint64_t>(b[i]) << (i * 8);
        if (r == 0) r = 1;
        msg.client_instance_id = r;

        uint32_t login_id = 0;
        for (int i = 8; i < 12; ++i)
            login_id |= static_cast<uint32_t>(b[i]) << ((i - 8) * 8);
        if (login_id == 0) login_id = 0x57'4E'53'01;  // "WNS\x01" fallback
        msg.obfuscated_private_ip = login_id;
    }
    return send_proto_message(EMsg::ClientLogon, msg.serialize());
}

// ---------------------------------------------------------------------------
// PICS
// ---------------------------------------------------------------------------

void CMClient::pics_get_access_tokens(std::vector<uint32_t> packageids,
                                      std::vector<uint32_t> appids,
                                      PicsAccessTokenCallback cb,
                                      std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientPICSAccessTokenRequest req;
    req.packageids = std::move(packageids);
    req.appids     = std::move(appids);

    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, [cb = std::move(cb)](JobResult r) {
        if (r.synthetic_failure || r.eresult <= 0) {
            cb(std::nullopt);
            return;
        }
        cb(pb::CMsgClientPICSAccessTokenResponse::deserialize(r.body));
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientPICSAccessTokenRequest, hdr,
                                      req.serialize());
    WN_LOGI("outbound PICS access tokens jobid=0x%llx packages=%zu apps=%zu",
            static_cast<unsigned long long>(job_id),
            req.packageids.size(), req.appids.size());
    if (!channel_->send(wire)) {
        WN_LOGE("PICS access-token send failed");
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

void CMClient::pics_get_changes_since(uint32_t since_change_number,
                                      PicsChangesSinceCallback cb,
                                      std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientPICSChangesSinceRequest req;
    req.since_change_number = since_change_number;

    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, [since_change_number, cb = std::move(cb)](JobResult r) {
        if (r.synthetic_failure) {
            if (cb) cb(std::nullopt);
            return;
        }
        auto resp = pb::CMsgClientPICSChangesSinceResponse::deserialize(r.body);
        if (!resp) {
            WN_LOGE("PICS changes-since: parse failed (%zu bytes)", r.body.size());
            if (cb) cb(std::nullopt);
            return;
        }
        WN_LOGI("PICS changes-since: since=%u current=%u apps=%zu packages=%zu full=%d",
                since_change_number, resp->current_change_number,
                resp->app_changes.size(), resp->package_changes.size(),
                static_cast<int>(resp->force_full_update));
        if (cb) cb(std::move(resp));
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientPICSChangesSinceRequest, hdr,
                                      req.serialize());
    WN_LOGI("outbound PICS changes-since jobid=0x%llx since=%u",
            static_cast<unsigned long long>(job_id), since_change_number);
    if (!channel_->send(wire)) {
        WN_LOGE("PICS changes-since send failed");
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

void CMClient::pics_get_product_info(std::vector<pb::PicsPackageInfoReq> packages,
                                     std::vector<pb::PicsAppInfoReq> apps,
                                     bool meta_data_only,
                                     PicsProductInfoCallback cb,
                                     std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientPICSProductInfoRequest req;
    req.packages       = std::move(packages);
    req.apps           = std::move(apps);
    req.meta_data_only = meta_data_only;

    const uint64_t job_id = jobs_.next_job_id();

    // Register the accumulator BEFORE sending — response can race the return.
    {
        std::lock_guard<std::mutex> lk(pics_mu_);
        pics_pending_[job_id] = PicsAggregate{{}, std::move(cb)};
    }

    // Also track in JobManager purely so that timeout / disconnect can
    // synthetically deliver a failure. The continuation here just clears
    // the accumulator entry and fires the user callback with nullopt.
    jobs_.track(job_id, [this, job_id](JobResult r) {
        if (!r.synthetic_failure) return;  // real responses come via route_inbound_
        PicsProductInfoCallback cb;
        {
            std::lock_guard<std::mutex> lk(pics_mu_);
            auto it = pics_pending_.find(job_id);
            if (it == pics_pending_.end()) return;
            cb = std::move(it->second.cb);
            pics_pending_.erase(it);
        }
        if (cb) cb(std::nullopt);
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientPICSProductInfoRequest, hdr,
                                      req.serialize());
    WN_LOGI("outbound PICS product info jobid=0x%llx packages=%zu apps=%zu meta=%d",
            static_cast<unsigned long long>(job_id),
            req.packages.size(), req.apps.size(),
            meta_data_only ? 1 : 0);
    if (!channel_->send(wire)) {
        WN_LOGE("PICS product-info send failed");
        // Drain the entry and fire user callback synchronously.
        PicsProductInfoCallback cb_local;
        {
            std::lock_guard<std::mutex> lk(pics_mu_);
            auto it = pics_pending_.find(job_id);
            if (it != pics_pending_.end()) {
                cb_local = std::move(it->second.cb);
                pics_pending_.erase(it);
            }
        }
        if (cb_local) cb_local(std::nullopt);
    }
}

void CMClient::get_app_ownership_ticket(uint32_t app_id,
                                         AppOwnershipTicketCallback cb,
                                         std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientGetAppOwnershipTicket req;
    req.app_id = app_id;

    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, [this, app_id, cb = std::move(cb)](JobResult r) {
        if (r.synthetic_failure) {
            if (cb) cb(std::nullopt);
            return;
        }
        auto resp = pb::CMsgClientGetAppOwnershipTicketResponse::deserialize(r.body);
        if (!resp) {
            WN_LOGE("ownership ticket: parse failed for app %u (%zu bytes)",
                    app_id, r.body.size());
            if (cb) cb(std::nullopt);
            return;
        }
        // Cache on success. eresult 1 = OK; anything else (2=Fail, etc.) we
        // still surface to the caller, but don't pollute the cache with
        // empty tickets.
        if (resp->eresult == 1 && !resp->ticket.empty()) {
            tickets_.store(resp->app_id, resp->eresult, resp->ticket);
            WN_LOGI("ownership ticket: cached %u bytes for app %u",
                    static_cast<unsigned>(resp->ticket.size()), resp->app_id);
        } else {
            WN_LOGI("ownership ticket: app %u eresult=%u ticket=%zu bytes (not cached)",
                    app_id, resp->eresult, resp->ticket.size());
        }
        if (cb) cb(std::move(resp));
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientGetAppOwnershipTicket, hdr,
                                      req.serialize());
    WN_LOGI("outbound ownership ticket request: app=%u jobid=0x%llx",
            app_id, static_cast<unsigned long long>(job_id));
    if (!channel_->send(wire)) {
        WN_LOGE("ownership ticket: channel send failed for app %u", app_id);
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

void CMClient::request_encrypted_app_ticket(uint32_t app_id,
                                            EncryptedAppTicketCallback cb,
                                            std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientRequestEncryptedAppTicket req;
    req.app_id = app_id;

    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, [app_id, cb = std::move(cb)](JobResult r) {
        if (r.synthetic_failure) {
            if (cb) cb(std::nullopt);
            return;
        }
        auto resp =
            pb::CMsgClientRequestEncryptedAppTicketResponse::deserialize(r.body);
        if (!resp) {
            WN_LOGE("encrypted app ticket: parse failed for app %u (%zu bytes)",
                    app_id, r.body.size());
            if (cb) cb(std::nullopt);
            return;
        }
        WN_LOGI("encrypted app ticket: app %u eresult=%d ticket=%zu bytes",
                app_id, resp->eresult, resp->encrypted_app_ticket.size());
        if (cb) cb(std::move(resp));
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientRequestEncryptedAppTicket, hdr,
                                      req.serialize());
    WN_LOGI("outbound encrypted app ticket request: app=%u jobid=0x%llx",
            app_id, static_cast<unsigned long long>(job_id));
    if (!channel_->send(wire)) {
        WN_LOGE("encrypted app ticket: channel send failed for app %u", app_id);
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

void CMClient::get_user_stats(uint32_t app_id,
                              UserStatsCallback cb,
                              std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientGetUserStats req;
    req.game_id           = app_id;
    req.steam_id_for_user = steam_id_.load();

    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, [app_id, cb = std::move(cb)](JobResult r) {
        if (r.synthetic_failure) {
            if (cb) cb(std::nullopt);
            return;
        }
        auto resp = pb::CMsgClientGetUserStatsResponse::deserialize(r.body);
        if (!resp) {
            WN_LOGE("user stats: parse failed for app %u (%zu bytes)",
                    app_id, r.body.size());
            if (cb) cb(std::nullopt);
            return;
        }
        WN_LOGI("user stats: app %u eresult=%d schema=%zu bytes",
                app_id, resp->eresult, resp->schema.size());
        if (cb) cb(std::move(resp));
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientGetUserStats, hdr,
                                      req.serialize());
    WN_LOGI("outbound user stats request: app=%u jobid=0x%llx",
            app_id, static_cast<unsigned long long>(job_id));
    if (!channel_->send(wire)) {
        WN_LOGE("user stats: channel send failed for app %u", app_id);
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

void CMClient::store_user_stats(
        uint32_t app_id, uint64_t steam_id, uint32_t crc_stats,
        const std::vector<std::pair<uint32_t, uint32_t>>& stats) {
    if (state_.load() != ClientState::LoggedOn) {
        WN_LOGI("store_user_stats: not logged on, dropping");
        return;
    }
    pb::CMsgClientStoreUserStats2 msg;
    msg.game_id         = app_id;
    msg.settor_steam_id = steam_id;
    msg.settee_steam_id = steam_id;
    msg.crc_stats       = crc_stats;
    msg.stats.reserve(stats.size());
    for (const auto& [id, val] : stats) {
        msg.stats.push_back(pb::CMsgClientStoreUserStats2::Stat{id, val});
    }
    // routing_appid lets Steam's GS backend route the write to this app.
    if (send_proto_message(EMsg::ClientStoreUserStats2, msg.serialize(), app_id)) {
        WN_LOGI("store_user_stats: sent app=%u stats=%zu crc=%u",
                app_id, stats.size(), crc_stats);
    } else {
        WN_LOGE("store_user_stats: send failed for app %u", app_id);
    }
}

void CMClient::get_depot_decryption_key(uint32_t depot_id, uint32_t app_id,
                                         DepotDecryptionKeyCallback cb,
                                         std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CMsgClientGetDepotDecryptionKey req;
    req.depot_id = depot_id;
    req.app_id   = app_id;

    const uint64_t job_id = jobs_.next_job_id();
    jobs_.track(job_id, [depot_id, cb = std::move(cb)](JobResult r) {
        if (r.synthetic_failure) {
            if (cb) cb(std::nullopt);
            return;
        }
        auto resp = pb::CMsgClientGetDepotDecryptionKeyResponse::deserialize(r.body);
        if (!resp) {
            WN_LOGE("depot key: parse failed for depot %u (%zu bytes)",
                    depot_id, r.body.size());
            if (cb) cb(std::nullopt);
            return;
        }
        // eresult 1 = OK; the AES-256 key is 32 bytes. A non-OK result is
        // still surfaced (e.g. 15 = AccessDenied for an unowned depot) so
        // the caller can tell "not entitled" from "transport failure".
        WN_LOGI("depot key: depot %u eresult=%u key=%zu bytes",
                resp->depot_id, resp->eresult, resp->depot_encryption_key.size());
        if (cb) cb(std::move(resp));
    }, timeout);

    CMsgProtoBufHeader hdr;
    hdr.steamid          = steam_id_.load();
    hdr.client_sessionid = session_id_.load();
    hdr.jobid_source     = job_id;
    hdr.jobid_target     = kInvalidJobId;
    auto wire = encode_proto_envelope(EMsg::ClientGetDepotDecryptionKey, hdr,
                                      req.serialize());
    WN_LOGI("outbound depot key request: depot=%u app=%u jobid=0x%llx",
            depot_id, app_id, static_cast<unsigned long long>(job_id));
    if (!channel_->send(wire)) {
        WN_LOGE("depot key: channel send failed for depot %u", depot_id);
        jobs_.deliver(job_id, -1, "channel send failed", {});
    }
}

void CMClient::get_manifest_request_code(uint32_t app_id, uint32_t depot_id,
                                          uint64_t manifest_id, std::string branch,
                                          ManifestRequestCodeCallback cb,
                                          std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CContentServerDirectory_GetManifestRequestCode_Request req;
    req.app_id      = app_id;
    req.depot_id    = depot_id;
    req.manifest_id = manifest_id;
    // JavaSteam (SteamContent.getManifestRequestCode) sends app_branch only
    // for a non-public branch. Lowercase-compare; empty == public.
    std::string lower;
    lower.reserve(branch.size());
    for (char c : branch) lower.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    if (!lower.empty() && lower != "public") {
        req.app_branch = std::move(branch);
    }

    call_service_method(
        "ContentServerDirectory.GetManifestRequestCode#1",
        /*authed=*/true,
        req.serialize(),
        [depot_id, manifest_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("manifest request code: depot %u gid %llu failed eresult=%d",
                        depot_id, static_cast<unsigned long long>(manifest_id), r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CContentServerDirectory_GetManifestRequestCode_Response
                            ::deserialize(r.body);
            if (!resp) {
                WN_LOGE("manifest request code: parse failed (%zu bytes)", r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("manifest request code: depot %u gid %llu -> code %llu",
                    depot_id, static_cast<unsigned long long>(manifest_id),
                    static_cast<unsigned long long>(resp->manifest_request_code));
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::get_cdn_servers(uint32_t cell_id, CdnServersCallback cb,
                                std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CContentServerDirectory_GetServersForSteamPipe_Request req;
    req.cell_id = cell_id;

    call_service_method(
        "ContentServerDirectory.GetServersForSteamPipe#1",
        /*authed=*/true,
        req.serialize(),
        [cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cdn servers: request failed eresult=%d", r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CContentServerDirectory_GetServersForSteamPipe_Response
                            ::deserialize(r.body);
            if (!resp) {
                WN_LOGE("cdn servers: parse failed (%zu bytes)", r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("cdn servers: %zu server(s)", resp->servers.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_get_app_file_changelist(uint32_t app_id,
                                             uint64_t synced_change_number,
                                             CloudFileChangelistCallback cb,
                                             std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CCloud_GetAppFileChangelist_Request req;
    req.appid                = app_id;
    req.synced_change_number = synced_change_number;

    call_service_method(
        "Cloud.GetAppFileChangelist#1",
        /*authed=*/true,
        req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cloud changelist: app %u failed eresult=%d",
                        app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CCloud_GetAppFileChangelist_Response::deserialize(r.body);
            if (!resp) {
                WN_LOGE("cloud changelist: parse failed for app %u (%zu bytes)",
                        app_id, r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("cloud changelist: app %u change=%llu files=%zu",
                    app_id,
                    static_cast<unsigned long long>(resp->current_change_number),
                    resp->files.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::inventory_get_item_def_meta(uint32_t app_id,
                                           ItemDefMetaCallback cb,
                                           std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CInventory_GetItemDefMeta_Request req;
    req.appid = app_id;

    call_service_method(
        "Inventory.GetItemDefMeta#1",
        /*authed=*/true,
        req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("itemdef meta: app %u failed eresult=%d",
                        app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CInventory_GetItemDefMeta_Response::deserialize(r.body);
            if (!resp) {
                WN_LOGE("itemdef meta: parse failed for app %u (%zu bytes)",
                        app_id, r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("itemdef meta: app %u modified=%u digest=%s",
                    app_id, resp->modified,
                    resp->digest.empty() ? "<empty>" : resp->digest.c_str());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::published_file_get_subscribed(uint32_t app_id, uint32_t page,
                                             uint32_t num_per_page,
                                             PublishedFileUserFilesCallback cb,
                                             std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CPublishedFile_GetUserFiles_Request req;
    req.steamid    = steam_id_.load();
    req.appid      = app_id;
    req.page       = page;
    req.numperpage = num_per_page;
    req.type       = "mysubscriptions";
    req.filetype   = 0xFFFFFFFFu;  // any Workshop file type

    call_service_method(
        "PublishedFile.GetUserFiles#1",
        /*authed=*/true,
        req.serialize(),
        [app_id, page, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("workshop subs: app %u page %u failed eresult=%d",
                        app_id, page, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp =
                pb::CPublishedFile_GetUserFiles_Response::deserialize(r.body);
            if (!resp) {
                WN_LOGE("workshop subs: parse failed app %u page %u (%zu bytes)",
                        app_id, page, r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("workshop subs: app %u page %u total=%u items=%zu",
                    app_id, page, resp->total,
                    resp->publishedfiledetails.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_get_file_download_info(uint32_t app_id, std::string filename,
                                            CloudFileDownloadCallback cb,
                                            std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CCloud_ClientFileDownload_Request req;
    req.appid    = app_id;
    req.filename = std::move(filename);
    req.realm    = 1;   // global Steam realm — set explicitly, not via the
                        // struct initializer, so a future refactor can't drop it.

    call_service_method(
        "Cloud.ClientFileDownload#1",
        /*authed=*/true,
        req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cloud download info: app %u failed eresult=%d",
                        app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CCloud_ClientFileDownload_Response::deserialize(r.body);
            if (!resp) {
                WN_LOGE("cloud download info: parse failed for app %u (%zu bytes)",
                        app_id, r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("cloud download info: app %u host=%s size=%u https=%d enc=%d",
                    app_id, resp->url_host.c_str(), resp->raw_file_size,
                    static_cast<int>(resp->use_https),
                    static_cast<int>(resp->encrypted));
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_begin_app_upload_batch(uint32_t app_id, std::string machine_name,
                                            std::vector<std::string> files_to_upload,
                                            std::vector<std::string> files_to_delete,
                                            uint64_t client_id,
                                            CloudBeginBatchCallback cb,
                                            std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CCloud_BeginAppUploadBatch_Request req;
    req.appid           = app_id;
    req.machine_name    = std::move(machine_name);
    req.files_to_upload = std::move(files_to_upload);
    req.files_to_delete = std::move(files_to_delete);
    req.client_id       = client_id;

    call_service_method(
        "Cloud.BeginAppUploadBatch#1", /*authed=*/true, req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cloud begin batch: app %u failed eresult=%d", app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CCloud_BeginAppUploadBatch_Response::deserialize(r.body);
            if (!resp) { if (cb) cb(std::nullopt); return; }
            WN_LOGI("cloud begin batch: app %u batch_id=%llu", app_id,
                    static_cast<unsigned long long>(resp->batch_id));
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_begin_file_upload(uint32_t app_id, std::string filename,
                                       uint32_t file_size, uint32_t raw_file_size,
                                       std::vector<uint8_t> file_sha, uint64_t time_stamp,
                                       uint64_t upload_batch_id,
                                       CloudBeginFileUploadCallback cb,
                                       std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CCloud_ClientBeginFileUpload_Request req;
    req.appid           = app_id;
    req.filename        = std::move(filename);
    req.file_size       = file_size;
    req.raw_file_size   = raw_file_size;
    req.file_sha        = std::move(file_sha);
    req.time_stamp      = time_stamp;
    req.upload_batch_id = upload_batch_id;

    call_service_method(
        "Cloud.ClientBeginFileUpload#1", /*authed=*/true, req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cloud begin file upload: app %u failed eresult=%d", app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CCloud_ClientBeginFileUpload_Response::deserialize(r.body);
            if (!resp) { if (cb) cb(std::nullopt); return; }
            WN_LOGI("cloud begin file upload: app %u blocks=%zu", app_id,
                    resp->block_requests.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_commit_file_upload(bool transfer_succeeded, uint32_t app_id,
                                        std::vector<uint8_t> file_sha, std::string filename,
                                        CloudCommitFileUploadCallback cb,
                                        std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CCloud_ClientCommitFileUpload_Request req;
    req.transfer_succeeded = transfer_succeeded;
    req.appid              = app_id;
    req.file_sha           = std::move(file_sha);
    req.filename           = std::move(filename);

    call_service_method(
        "Cloud.ClientCommitFileUpload#1", /*authed=*/true, req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cloud commit upload: app %u failed eresult=%d", app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CCloud_ClientCommitFileUpload_Response::deserialize(r.body);
            if (!resp) { if (cb) cb(std::nullopt); return; }
            WN_LOGI("cloud commit upload: app %u committed=%d", app_id,
                    static_cast<int>(resp->file_committed));
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_complete_app_upload_batch(uint32_t app_id, uint64_t batch_id,
                                               uint32_t batch_eresult,
                                               CloudCompleteBatchCallback cb,
                                               std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(false);
        return;
    }
    pb::CCloud_CompleteAppUploadBatch_Request req;
    req.appid         = app_id;
    req.batch_id      = batch_id;
    req.batch_eresult = batch_eresult;

    call_service_method(
        "Cloud.CompleteAppUploadBatchBlocking#1", /*authed=*/true, req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            const bool ok = !r.synthetic_failure && r.eresult == 1;
            WN_LOGI("cloud complete batch: app %u ok=%d eresult=%d",
                    app_id, static_cast<int>(ok), r.eresult);
            if (cb) cb(ok);
        },
        timeout);
}

void CMClient::notify_games_played(const pb::CMsgClientGamesPlayed& msg) {
    if (state_.load() != ClientState::LoggedOn) {
        WN_LOGI("notify_games_played: not logged on, dropping");
        return;
    }
    if (send_proto_message(EMsg::ClientGamesPlayedWithDataBlob, msg.serialize())) {
        WN_LOGI("notify_games_played: %zu game(s) reported", msg.games_played.size());
    } else {
        WN_LOGE("notify_games_played: send failed");
    }
}

void CMClient::cloud_signal_app_launch_intent(uint32_t app_id, uint64_t client_id,
                                              std::string machine_name,
                                              bool ignore_pending_operations,
                                              int32_t os_type,
                                              CloudAppLaunchIntentCallback cb,
                                              std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CCloud_AppLaunchIntent_Request req;
    req.appid                     = app_id;
    req.client_id                 = client_id;
    req.machine_name              = std::move(machine_name);
    req.ignore_pending_operations = ignore_pending_operations;
    req.os_type                   = os_type;

    call_service_method(
        "Cloud.SignalAppLaunchIntent#1", /*authed=*/true, req.serialize(),
        [app_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("cloud launch intent: app %u failed eresult=%d", app_id, r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CCloud_AppLaunchIntent_Response::deserialize(r.body);
            if (!resp) { if (cb) cb(std::nullopt); return; }
            WN_LOGI("cloud launch intent: app %u pending_ops=%zu",
                    app_id, resp->pending_operation_codes.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::cloud_signal_app_exit_sync_done(uint32_t app_id, uint64_t client_id,
                                               bool uploads_completed,
                                               bool uploads_required) {
    if (state_.load() != ClientState::LoggedOn) return;
    pb::CCloud_AppExitSyncDone_Notification req;
    req.appid             = app_id;
    req.client_id         = client_id;
    req.uploads_completed = uploads_completed;
    req.uploads_required  = uploads_required;
    // Notification — no meaningful response; the tracked job simply times
    // out harmlessly.
    call_service_method(
        "Cloud.SignalAppExitSyncDone#1", /*authed=*/true, req.serialize(),
        [app_id](JobResult /*r*/) {
            WN_LOGI("cloud exit sync done: app %u signalled", app_id);
        });
}

void CMClient::set_persona_state(uint32_t persona_state) {
    if (state_.load() != ClientState::LoggedOn) {
        WN_LOGI("set_persona_state: not logged on, dropping");
        return;
    }
    pb::CMsgClientChangeStatus msg;
    msg.persona_state = persona_state;
    if (send_proto_message(EMsg::ClientChangeStatus, msg.serialize())) {
        WN_LOGI("set_persona_state: sent (state=%u)", persona_state);
    } else {
        WN_LOGE("set_persona_state: send failed");
    }
}

void CMClient::request_user_persona() {
    if (state_.load() != ClientState::LoggedOn) {
        WN_LOGI("request_user_persona: not logged on, dropping");
        return;
    }
    pb::CMsgClientRequestFriendData req;
    req.persona_state_requested = 0xFFFF;   // request all standard fields
    req.friends.push_back(steam_id_.load());
    if (send_proto_message(EMsg::ClientRequestFriendData, req.serialize())) {
        WN_LOGI("request_user_persona: requested for steamid=%llu",
                static_cast<unsigned long long>(steam_id_.load()));
    } else {
        WN_LOGE("request_user_persona: send failed");
    }
}

std::optional<pb::PersonaStateFriend> CMClient::self_persona() const {
    std::lock_guard<std::mutex> lk(persona_mu_);
    return self_persona_;
}

std::vector<pb::License> CMClient::license_list() const {
    std::lock_guard<std::mutex> lk(license_mu_);
    return license_list_;
}

void CMClient::get_family_group(uint64_t family_group_id,
                                FamilyGroupCallback cb,
                                std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CFamilyGroups_GetFamilyGroup_Request req;
    req.family_groupid = family_group_id;

    call_service_method(
        "FamilyGroups.GetFamilyGroup#1",
        /*authed=*/true,
        req.serialize(),
        [family_group_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("get_family_group: group %llu failed eresult=%d",
                        static_cast<unsigned long long>(family_group_id),
                        r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CFamilyGroups_GetFamilyGroup_Response::deserialize(r.body);
            if (!resp) {
                WN_LOGE("get_family_group: parse failed (%zu bytes)", r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("get_family_group: '%s' members=%zu",
                    resp->name.c_str(), resp->members.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::get_owned_games(uint64_t steam_id, OwnedGamesCallback cb,
                               std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(std::nullopt);
        return;
    }
    pb::CPlayer_GetOwnedGames_Request req;
    req.steamid                   = steam_id;
    req.include_appinfo           = true;
    req.include_played_free_games = true;
    req.include_free_sub          = true;
    req.include_extended_appinfo  = true;

    call_service_method(
        "Player.GetOwnedGames#1",
        /*authed=*/true,
        req.serialize(),
        [steam_id, cb = std::move(cb)](JobResult r) {
            if (r.synthetic_failure || r.eresult != 1) {
                WN_LOGE("get_owned_games: steamid %llu failed eresult=%d",
                        static_cast<unsigned long long>(steam_id), r.eresult);
                if (cb) cb(std::nullopt);
                return;
            }
            auto resp = pb::CPlayer_GetOwnedGames_Response::deserialize(r.body);
            if (!resp) {
                WN_LOGE("get_owned_games: parse failed (%zu bytes)", r.body.size());
                if (cb) cb(std::nullopt);
                return;
            }
            WN_LOGI("get_owned_games: steamid %llu games=%zu",
                    static_cast<unsigned long long>(steam_id),
                    resp->games.size());
            if (cb) cb(std::move(resp));
        },
        timeout);
}

void CMClient::kick_playing_session(bool only_stop_game) {
    if (state_.load() != ClientState::LoggedOn) {
        WN_LOGI("kick_playing_session: not logged on, dropping");
        return;
    }
    pb::CMsgClientKickPlayingSession msg;
    msg.only_stop_game = only_stop_game;
    if (send_proto_message(EMsg::ClientKickPlayingSession, msg.serialize())) {
        WN_LOGI("kick_playing_session: sent (only_stop_game=%d)",
                static_cast<int>(only_stop_game));
    } else {
        WN_LOGE("kick_playing_session: send failed");
    }
}

void CMClient::prepare_app(uint32_t app_id,
                            std::vector<uint32_t> dlc_app_ids,
                            PrepareAppCallback cb,
                            std::chrono::seconds timeout) {
    if (state_.load() != ClientState::LoggedOn) {
        if (cb) cb(false, "not logged on");
        return;
    }

    // Assemble the full appid set: parent + DLC, de-duplicated.
    std::vector<uint32_t> all_ids;
    all_ids.reserve(1 + dlc_app_ids.size());
    if (app_id != 0) all_ids.push_back(app_id);
    for (uint32_t d : dlc_app_ids) {
        if (d == 0 || d == app_id) continue;
        if (std::find(all_ids.begin(), all_ids.end(), d) != all_ids.end()) continue;
        all_ids.push_back(d);
    }
    if (all_ids.empty()) {
        if (cb) cb(true, "");
        return;
    }

    WN_LOGI("prepare_app(%u): pre-warming %zu app(s) (1 parent + %zu DLC)",
            app_id, all_ids.size(), all_ids.size() - 1);

    // Two-phase: ensure access tokens first (anything PICS told us was
    // restricted), then fetch product info. We don't trust whatever's
    // already in the store for these specific ids — force a refresh so
    // the data Wine reads is as fresh as possible right before launch.
    auto missing_tokens = std::vector<uint32_t>{};
    for (uint32_t id : all_ids) {
        auto entry = library_.find_app(id);
        if (entry && entry->missing_token && entry->access_token == 0) {
            missing_tokens.push_back(id);
        }
    }

    // Step B: request product info with whatever tokens we have, then
    // ingest the result. Lambda captures the full id list so we can verify
    // each is now cached before reporting success.
    auto do_product_info = [this, all_ids, app_id, cb = std::move(cb), timeout]() mutable {
        std::vector<pb::PicsAppInfoReq> req;
        req.reserve(all_ids.size());
        for (uint32_t id : all_ids) {
            uint64_t token = 0;
            auto e = library_.find_app(id);
            if (e) token = e->access_token;
            req.push_back(pb::PicsAppInfoReq{id, token, false});
        }
        pics_get_product_info({}, std::move(req), /*meta_data_only=*/false,
            [this, all_ids = std::move(all_ids), app_id,
             cb = std::move(cb), timeout](std::optional<pb::CMsgClientPICSProductInfoResponse> resp) mutable {
                if (!resp) {
                    WN_LOGE("prepare_app(%u): PICS product info failed", app_id);
                    if (cb) cb(false, "PICS product info failed");
                    return;
                }
                library_.ingest_app_pics_response(*resp);
                size_t ready = 0;
                for (uint32_t id : all_ids) {
                    auto e = library_.find_app(id);
                    if (e && (e->pics_fetched || !e->name.empty())) ++ready;
                }
                WN_LOGI("prepare_app(%u): PICS ready (%zu/%zu apps cached); "
                        "fetching ownership tickets",
                        app_id, ready, all_ids.size());

                // Step C — fetch an ownership ticket per id. We issue them
                // concurrently (each is an independent request/response pair
                // via JobManager); a shared counter fires the final user cb
                // when all are done (success or failure — partial caches are
                // still useful for the games that need them).
                struct Counter {
                    std::atomic<size_t> remaining;
                    std::atomic<size_t> ok{0};
                    AppOwnershipTicketCallback nop = nullptr;
                    PrepareAppCallback final_cb;
                    uint32_t parent_app_id;
                    size_t   total;
                };
                auto counter = std::make_shared<Counter>();
                counter->remaining = all_ids.size();
                counter->total     = all_ids.size();
                counter->parent_app_id = app_id;
                counter->final_cb  = std::move(cb);

                for (uint32_t id : all_ids) {
                    get_app_ownership_ticket(id,
                        [counter](std::optional<pb::CMsgClientGetAppOwnershipTicketResponse> r) {
                            if (r && r->eresult == 1 && !r->ticket.empty()) {
                                counter->ok.fetch_add(1);
                            }
                            if (counter->remaining.fetch_sub(1) == 1) {
                                size_t ok = counter->ok.load();
                                WN_LOGI("prepare_app(%u): ownership tickets %zu/%zu OK",
                                        counter->parent_app_id, ok, counter->total);
                                if (counter->final_cb) {
                                    counter->final_cb(true, "");
                                }
                            }
                        }, timeout);
                }
            }, timeout);
    };

    if (missing_tokens.empty()) {
        do_product_info();
        return;
    }
    WN_LOGI("prepare_app(%u): requesting access tokens for %zu restricted apps",
            app_id, missing_tokens.size());
    pics_get_access_tokens({}, std::move(missing_tokens),
        [this, do_product_info = std::move(do_product_info), app_id](
            std::optional<pb::CMsgClientPICSAccessTokenResponse> resp) mutable {
            if (resp) library_.ingest_app_access_tokens(*resp);
            else WN_LOGE("prepare_app(%u): access-token request failed; continuing anyway", app_id);
            do_product_info();
        }, timeout);
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
    family_group_id_.store(0);
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

        case EMsg::ClientPICSAccessTokenResponse:
        case EMsg::ClientPICSChangesSinceResponse:
        case EMsg::ClientGetAppOwnershipTicketResponse:
        case EMsg::ClientRequestEncryptedAppTicketResponse:
        case EMsg::ClientGetUserStatsResponse:
        case EMsg::ClientGetDepotDecryptionKeyResponse:
            // Single-shot response — JobManager handles routing + parse.
            jobs_.deliver(header.jobid_target,
                          header.eresult,
                          header.error_message,
                          body);
            break;

        case EMsg::ClientPICSProductInfoResponse: {
            // Multi-part: merge into the per-job accumulator; only fire the
            // user callback when response_pending is false/absent.
            auto resp = pb::CMsgClientPICSProductInfoResponse::deserialize(body);
            if (!resp) {
                WN_LOGE("PICS product-info parse failed (%zu bytes)", body.size());
                // Drain the entry and fire failure.
                PicsProductInfoCallback cb;
                {
                    std::lock_guard<std::mutex> lk(pics_mu_);
                    auto it = pics_pending_.find(header.jobid_target);
                    if (it != pics_pending_.end()) {
                        cb = std::move(it->second.cb);
                        pics_pending_.erase(it);
                    }
                }
                if (cb) cb(std::nullopt);
                break;
            }
            PicsProductInfoCallback final_cb;
            pb::CMsgClientPICSProductInfoResponse merged;
            {
                std::lock_guard<std::mutex> lk(pics_mu_);
                auto it = pics_pending_.find(header.jobid_target);
                if (it == pics_pending_.end()) {
                    // Late response after timeout — silently drop.
                    WN_LOGI("PICS product-info: unknown jobid_target=0x%llx (timed out?)",
                            static_cast<unsigned long long>(header.jobid_target));
                    break;
                }
                auto& acc = it->second.acc;
                acc.apps.insert(acc.apps.end(),
                                std::make_move_iterator(resp->apps.begin()),
                                std::make_move_iterator(resp->apps.end()));
                acc.packages.insert(acc.packages.end(),
                                    std::make_move_iterator(resp->packages.begin()),
                                    std::make_move_iterator(resp->packages.end()));
                acc.unknown_appids.insert(acc.unknown_appids.end(),
                                          resp->unknown_appids.begin(),
                                          resp->unknown_appids.end());
                acc.unknown_packageids.insert(acc.unknown_packageids.end(),
                                              resp->unknown_packageids.begin(),
                                              resp->unknown_packageids.end());
                if (resp->http_min_size > 0) acc.http_min_size = resp->http_min_size;
                if (!resp->http_host.empty()) acc.http_host    = resp->http_host;
                acc.meta_data_only = resp->meta_data_only;

                if (resp->response_pending) {
                    WN_LOGI("PICS product-info partial: jobid=0x%llx +apps=%zu "
                            "+packages=%zu (pending more)",
                            static_cast<unsigned long long>(header.jobid_target),
                            resp->apps.size(), resp->packages.size());
                    break;
                }
                // Final part — move callback + merged accumulator out, drop entry,
                // then fire outside the lock to avoid re-entrancy hazards.
                final_cb = std::move(it->second.cb);
                merged   = std::move(it->second.acc);
                pics_pending_.erase(it);
            }
            WN_LOGI("PICS product-info final: apps=%zu packages=%zu unknown_apps=%zu "
                    "unknown_packages=%zu http_min_size=%u",
                    merged.apps.size(), merged.packages.size(),
                    merged.unknown_appids.size(), merged.unknown_packageids.size(),
                    merged.http_min_size);
            if (final_cb) final_cb(std::move(merged));
            break;
        }

        case EMsg::ClientLogonResponse: {
            auto resp = pb::CMsgClientLogonResponse::deserialize(body);
            if (!resp) {
                WN_LOGE("CMsgClientLogonResponse parse failed");
                return;
            }
            if (resp->eresult == 1 /* EResult.OK */) {
                steam_id_.store(resp->client_supplied_steamid);
                family_group_id_.store(resp->family_group_id);
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
            // Surface the server-supplied EResult so a mid-session logoff is
            // diagnosable (6 = LoggedInElsewhere, 84 = TryAnotherCM, etc.).
            if (auto off = pb::CMsgClientLoggedOff::deserialize(body)) {
                WN_LOGE("ClientLoggedOff: emsg=%d eresult=%d — session ended",
                        static_cast<int>(emsg), off->eresult);
            } else {
                WN_LOGE("ClientLoggedOff: emsg=%d (eresult parse failed, "
                        "%zu bytes) — session ended",
                        static_cast<int>(emsg), body.size());
            }
            heartbeat_.stop();
            steam_id_.store(0);
            session_id_.store(0);
            family_group_id_.store(0);
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
        case EMsg::ClientPersonaState: {
            // Server-pushed persona updates; cache the entry for our own
            // SteamID so self_persona() can surface name/avatar/game.
            auto resp = pb::CMsgClientPersonaState::deserialize(body);
            if (resp) {
                const uint64_t self = steam_id_.load();
                for (auto& f : resp->friends) {
                    if (f.friendid == self) {
                        WN_LOGI("persona state: self name='%s' app=%u",
                                f.player_name.c_str(), f.game_played_app_id);
                        std::lock_guard<std::mutex> lk(persona_mu_);
                        self_persona_ = std::move(f);
                        break;
                    }
                }
            }
            break;
        }

        case EMsg::ClientLicenseList: {
            auto msg = pb::CMsgClientLicenseList::deserialize(body);
            if (!msg) {
                WN_LOGE("CMsgClientLicenseList parse failed (%zu bytes)", body.size());
            } else {
                WN_LOGI("ClientLicenseList: eresult=%d licenses=%zu",
                        msg->eresult, msg->licenses.size());
                {
                    std::lock_guard<std::mutex> lk(license_mu_);
                    license_list_ = msg->licenses;
                }
                library_.ingest_license_list(*msg);
                // Kick off the populate pipeline. Each PICS response feeds the
                // next batch via library_populate_step_ until the store is
                // saturated. Skipped on download-only sessions, where the
                // crawl is wasted work and floods the CM right when the
                // download needs the channel for depot keys.
                if (auto_populate_library_.load()) {
                    library_populate_step_();
                } else {
                    WN_LOGI("library populate: skipped (auto-populate disabled "
                            "for this session)");
                }
            }
            ClientMessageCallback cb;
            { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_client_message_; }
            safe_invoke(cb, emsg, header, body);
            break;
        }

        case EMsg::ClientPlayingSessionState: {
            // Server-pushed: cache whether playing is currently blocked so
            // is_playing_blocked() / kickPlayingSession can read it back.
            auto msg = pb::CMsgClientPlayingSessionState::deserialize(body);
            if (msg) {
                playing_blocked_.store(msg->playing_blocked);
                WN_LOGI("playing session state: blocked=%d app=%u",
                        msg->playing_blocked ? 1 : 0, msg->playing_app);
            } else {
                WN_LOGE("CMsgClientPlayingSessionState parse failed (%zu bytes)",
                        body.size());
            }
            ClientMessageCallback cb;
            { std::lock_guard<std::mutex> lk(cb_mu_); cb = on_client_message_; }
            safe_invoke(cb, emsg, header, body);
            break;
        }

        case EMsg::ClientAccountInfo:
        case EMsg::ClientEmailAddrInfo:
        case EMsg::ClientFriendsList: {
            const char* name =
                (emsg == EMsg::ClientAccountInfo)  ? "ClientAccountInfo"  :
                (emsg == EMsg::ClientEmailAddrInfo)? "ClientEmailAddrInfo":
                                                     "ClientFriendsList";
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

// ---------------------------------------------------------------------------
// Library populate orchestrator
//
// State machine driven by polling the store between PICS round-trips:
//   1. Packages without pics_fetched=true → batch ClientPICSProductInfo.
//      Response feeds ingest_package_pics_response, which extracts appids
//      and creates app stubs. We then recurse → step 2.
//   2. Apps with missing_token=true and access_token=0 → batch
//      ClientPICSAccessToken. Response feeds ingest_app_access_tokens,
//      which clears missing_token. Recurse → step 3.
//   3. Apps without pics_fetched=true → batch ClientPICSProductInfo for
//      apps. Response feeds ingest_app_pics_response which fills the
//      name/type/parent/dlc fields. Recurse — picks up any DLC apps the
//      game declared via extended.listofdlc.
//   4. Nothing pending — log a summary and stop.
//
// Batches are capped at 256 items per request so multi-part responses are
// occasional rather than the norm. Each PICS round-trip schedules the next
// step from inside its callback, so the orchestrator is fully event-driven
// — no polling thread, no condvars.
// ---------------------------------------------------------------------------
void CMClient::library_populate_step_() {
    // Step 1: packages
    auto pkg_batch = library_.get_pending_package_pics_request();
    if (!pkg_batch.empty()) {
        WN_LOGI("library populate: requesting PICS for %zu packages "
                "(pkgs_known=%zu apps_known=%zu)",
                pkg_batch.size(), library_.package_count(), library_.app_count());
        pics_get_product_info(std::move(pkg_batch), {}, /*meta_data_only=*/false,
            [this](std::optional<pb::CMsgClientPICSProductInfoResponse> resp) {
                if (!resp) {
                    WN_LOGE("library populate: package PICS failed");
                    return;
                }
                library_.ingest_package_pics_response(*resp);
                library_populate_step_();   // chain
            });
        return;
    }

    // Step 2: app access tokens
    auto tok_batch = library_.get_apps_needing_access_token();
    if (!tok_batch.empty()) {
        WN_LOGI("library populate: requesting access tokens for %zu apps",
                tok_batch.size());
        pics_get_access_tokens({}, std::move(tok_batch),
            [this](std::optional<pb::CMsgClientPICSAccessTokenResponse> resp) {
                if (!resp) {
                    WN_LOGE("library populate: app access-token request failed");
                    return;
                }
                library_.ingest_app_access_tokens(*resp);
                library_populate_step_();
            });
        return;
    }

    // Step 3: apps
    auto app_batch = library_.get_pending_app_pics_request();
    if (!app_batch.empty()) {
        WN_LOGI("library populate: requesting PICS for %zu apps", app_batch.size());
        pics_get_product_info({}, std::move(app_batch), /*meta_data_only=*/false,
            [this](std::optional<pb::CMsgClientPICSProductInfoResponse> resp) {
                if (!resp) {
                    WN_LOGE("library populate: app PICS failed");
                    return;
                }
                library_.ingest_app_pics_response(*resp);
                library_populate_step_();
            });
        return;
    }

    // Step 4: done.
    //   Total apps  = everything we've tracked (incl. parent stubs added to
    //                 anchor DLC the user owns of games they don't).
    //   Owned apps  = apps with at least one source_package_id — i.e. some
    //                 license the user holds grants this app. This is the
    //                 "real library" the UI should display.
    size_t packages   = library_.package_count();
    size_t apps_total = library_.app_count();
    size_t apps_owned = library_.owned_app_count();
    WN_LOGI("library populate complete: %zu packages, %zu apps (%zu truly owned, "
            "%zu parent-stubs for owned-DLC)",
            packages, apps_total, apps_owned, apps_total - apps_owned);

    // Counts by type, OWNED only — so the breakdown reflects what the user
    // would actually see in a library UI.
    auto owned = library_.owned_apps();
    size_t games = 0, dlc = 0, demo = 0, tool = 0, other = 0;
    for (const auto& a : owned) {
        if      (a.type == "Game" || a.type == "game")          ++games;
        else if (a.type == "DLC"  || a.type == "dlc")           ++dlc;
        else if (a.type == "Demo" || a.type == "demo")          ++demo;
        else if (a.type == "Tool" || a.type == "tool")          ++tool;
        else                                                    ++other;
    }
    WN_LOGI("  owned breakdown: games=%zu dlc=%zu demo=%zu tool=%zu other=%zu",
            games, dlc, demo, tool, other);

    // Sample: first 10 OWNED games (type=Game) with their source package(s).
    auto pkgs_snap = library_.packages();
    std::unordered_map<uint32_t, OwnedPackage> pkg_by_id;
    pkg_by_id.reserve(pkgs_snap.size());
    for (auto& p : pkgs_snap) pkg_by_id[p.package_id] = std::move(p);
    int shown = 0;
    for (const auto& a : owned) {
        if (a.type != "Game" && a.type != "game") continue;
        std::string pkg_summary;
        for (uint32_t pid : a.source_package_ids) {
            auto it = pkg_by_id.find(pid);
            if (it == pkg_by_id.end()) {
                pkg_summary += " " + std::to_string(pid);
            } else {
                char buf[96];
                std::snprintf(buf, sizeof(buf),
                              " %u(t=%u f=0x%x)",
                              pid, it->second.license_type, it->second.license_flags);
                pkg_summary += buf;
            }
        }
        WN_LOGI("  [%u] '%s' dlc=%zu src_pkgs:%s",
                a.app_id, a.name.c_str(), a.dlc_app_ids.size(),
                pkg_summary.c_str());
        if (++shown >= 10) break;
    }
}

}  // namespace wn_steam
