#include "wn_steam/encrypted_channel.h"

#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <exception>

#include "wn_steam/handshake_messages.h"
#include "wn_steam/key_dictionary.h"
#include "wn_steam/wire_format.h"

namespace wn_steam {

namespace {

constexpr const char* kLogTag = "WnSteamCh";

#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)
#define WN_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)

// Constant-time byte compare. Used for HMAC tag comparison so a malformed
// envelope can't be distinguished by timing.
bool ct_equal(std::span<const uint8_t> a, std::span<const uint8_t> b) noexcept {
    if (a.size() != b.size()) return false;
    uint8_t diff = 0;
    for (size_t i = 0; i < a.size(); ++i) diff |= a[i] ^ b[i];
    return diff == 0;
}

template <typename Cb, typename... Args>
void safe_invoke(Cb& cb, Args&&... args) {
    if (!cb) return;
    try {
        cb(std::forward<Args>(args)...);
    } catch (const std::exception& e) {
        WN_LOGE("channel callback threw: %s", e.what());
    } catch (...) {
        WN_LOGE("channel callback threw unknown exception");
    }
}

}  // namespace

// ---------------------------------------------------------------------------

EncryptedChannel::EncryptedChannel(std::unique_ptr<Transport> transport)
    : transport_(std::move(transport)) {
    // Wire the transport callbacks to our handlers. The transport's
    // worker thread invokes these — see thread model in the header.
    transport_->set_on_connected([this]() { on_transport_connected(); });
    transport_->set_on_disconnected(
        [this](TransportDisconnectReason r, const std::string& detail) {
            on_transport_disconnected(r, detail);
        });
    transport_->set_on_message(
        [this](std::span<const uint8_t> bytes) { on_transport_message(bytes); });
}

EncryptedChannel::~EncryptedChannel() {
    disconnect();
}

bool EncryptedChannel::connect(const std::string& url) {
    ChannelState expected = ChannelState::Disconnected;
    if (!state_.compare_exchange_strong(expected, ChannelState::Connected)) {
        // The Connected state here means "transport-connecting"; we set it
        // before the actual transport returns so a re-entrant connect call
        // is rejected.
        return false;
    }
    if (!transport_->connect(url)) {
        state_.store(ChannelState::Disconnected);
        return false;
    }
    return true;
}

bool EncryptedChannel::send(std::span<const uint8_t> plaintext) {
    if (state_.load() != ChannelState::Encrypted) return false;
    // WSS path: send plaintext directly. TLS handles encryption at the
    // transport layer; Steam does NOT expect an app-layer AES envelope
    // on WebSocket connections. The encrypt_envelope() helper remains
    // available for a future raw-TCP path.
    return transport_->send(plaintext);
}

void EncryptedChannel::disconnect() {
    ChannelState s = state_.load();
    if (s == ChannelState::Disconnected) return;
    state_.store(ChannelState::Closing);
    transport_->disconnect();
}

void EncryptedChannel::set_on_message(MessageCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_message_ = std::move(cb);
}
void EncryptedChannel::set_on_connected(ConnectedCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_connected_ = std::move(cb);
}
void EncryptedChannel::set_on_disconnected(DisconnectedCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_disconnected_ = std::move(cb);
}

// ---------------------------------------------------------------------------
// Transport callbacks
// ---------------------------------------------------------------------------

void EncryptedChannel::on_transport_connected() {
    // WSS transport is BARE — Steam does NOT do the
    // ChannelEncryptRequest/Response/Result handshake over WebSockets.
    // TLS already encrypts the channel, so there is no app-layer
    // encryption negotiation. Verified against JavaSteam, steam-vent,
    // node-steam-user, and SteamKit2 — all four skip 1303/1304/1305 on
    // WSS and treat the WebSocket as a bare framed transport. The first
    // application message must come from the client (typically
    // CMsgClientHello or CMsgClientLogon).
    //
    // We transition straight to the Encrypted state (which now just
    // means "ready to send/receive Steam-protocol protobufs without an
    // app-layer AES envelope") and fire on_connected_ so CMClient can
    // send the opening frame.
    state_.store(ChannelState::Encrypted);
    WN_LOGI("WSS transport up — skipping ChannelEncrypt handshake (TLS-encrypted bare channel)");

    ConnectedCallback cb;
    {
        std::lock_guard<std::mutex> lk(cb_mu_);
        cb = on_connected_;
    }
    safe_invoke(cb);
}

void EncryptedChannel::on_transport_disconnected(TransportDisconnectReason r,
                                                 const std::string& detail) {
    state_.store(ChannelState::Disconnected);
    DisconnectedCallback cb;
    {
        std::lock_guard<std::mutex> lk(cb_mu_);
        cb = on_disconnected_;
    }
    const auto reason = (r == TransportDisconnectReason::UserInitiated)
        ? ChannelDisconnectReason::UserInitiated
        : ChannelDisconnectReason::TransportError;
    safe_invoke(cb, reason, detail);
}

void EncryptedChannel::on_transport_message(std::span<const uint8_t> bytes) {
    const ChannelState s = state_.load();
    WN_LOGD("rx %zu bytes (state=%d)", bytes.size(), static_cast<int>(s));

    // WSS path: bytes are plaintext Steam-protocol frames (the wire
    // envelope is [u32 emsg|proto_flag][u32 hdr_len][CMsgProtoBufHeader]
    // [body]). Pass through to the upper layer (CMClient) which parses
    // ProtoEnvelope and routes by EMsg.
    //
    // The handshake (ChannelEncryptRequest/Response/Result) is not used
    // on WSS — see on_transport_connected above. The encrypt_envelope /
    // decrypt_envelope helpers remain in this file for a future raw-TCP
    // implementation.
    if (s != ChannelState::Encrypted) {
        WN_LOGE("inbound message in unexpected state %d", static_cast<int>(s));
        return;
    }

    MessageCallback cb;
    {
        std::lock_guard<std::mutex> lk(cb_mu_);
        cb = on_message_;
    }
    safe_invoke(cb, bytes);
}

// ---------------------------------------------------------------------------
// Handshake handlers
// ---------------------------------------------------------------------------

bool EncryptedChannel::handle_channel_encrypt_request(std::span<const uint8_t> body) {
    auto req = MsgChannelEncryptRequest::deserialize_body(body);
    if (!req) return false;
    negotiated_universe_ = req->universe;

    auto spki = get_universe_public_key(req->universe);
    if (spki.empty()) {
        WN_LOGE("no public key for universe=%u", static_cast<uint32_t>(req->universe));
        return false;
    }

    // 1. Generate (or inject for test) the session key.
    SessionKey raw_key;
    if (test_injected_key_.has_value()) {
        raw_key = *test_injected_key_;
    } else {
        auto k = generate_session_key();
        if (!k) {
            WN_LOGE("RNG failure during session-key generation");
            return false;
        }
        raw_key = k->bytes;
    }

    // 2. Assemble plaintext for the RSA envelope: tempSessionKey || challenge.
    std::vector<uint8_t> rsa_input;
    rsa_input.reserve(kSessionKeyLength + req->challenge.size());
    rsa_input.insert(rsa_input.end(), raw_key.begin(), raw_key.end());
    rsa_input.insert(rsa_input.end(), req->challenge.begin(), req->challenge.end());

    auto rsa_ct = rsa_oaep_sha1_encrypt(spki, rsa_input);
    if (!rsa_ct) {
        WN_LOGE("RSA-OAEP encrypt failed");
        return false;
    }
    if (rsa_ct->size() != kRsa1024CipherBytes) {
        WN_LOGE("RSA ciphertext unexpected size=%zu", rsa_ct->size());
        return false;
    }

    // 3. Build and send MsgChannelEncryptResponse.
    MsgChannelEncryptResponse resp;
    std::memcpy(resp.encrypted_handshake_blob.data(), rsa_ct->data(),
                kRsa1024CipherBytes);
    resp.key_crc       = crc32(*rsa_ct);
    resp.unknown_zero  = 0;

    std::vector<uint8_t> out;
    out.reserve(kMsgHdrBytes + kChannelEncryptResponseBodyBytes);
    MsgHdr hdr;
    hdr.msg = EMsg::ChannelEncryptResponse;
    hdr.serialize(out);
    resp.serialize_body(out);

    if (!transport_->send(out)) {
        WN_LOGE("transport->send failed for ChannelEncryptResponse");
        return false;
    }

    // 4. Commit the negotiated key now (we'll use it once Result arrives).
    session_key_  = SecureSessionKey(raw_key);
    std::copy_n(raw_key.begin(), kHmacKeyLength, hmac_key_.begin());

    state_.store(ChannelState::Challenged);
    return true;
}

bool EncryptedChannel::handle_channel_encrypt_result(std::span<const uint8_t> body) {
    auto res = MsgChannelEncryptResult::deserialize_body(body);
    if (!res) return false;
    // EResult.OK == 1 (matches SteamKit's enum).
    if (res->result != 1) {
        WN_LOGE("server EResult for handshake = %u", res->result);
        return false;
    }

    state_.store(ChannelState::Encrypted);
    WN_LOGI("encrypted channel established (universe=%u)",
            static_cast<uint32_t>(negotiated_universe_));

    ConnectedCallback cb;
    {
        std::lock_guard<std::mutex> lk(cb_mu_);
        cb = on_connected_;
    }
    safe_invoke(cb);
    return true;
}

void EncryptedChannel::fail_channel(ChannelDisconnectReason r, std::string detail) {
    state_.store(ChannelState::Closing);
    DisconnectedCallback cb;
    {
        std::lock_guard<std::mutex> lk(cb_mu_);
        cb = on_disconnected_;
    }
    transport_->disconnect();
    safe_invoke(cb, r, std::move(detail));
    state_.store(ChannelState::Disconnected);
}

// ---------------------------------------------------------------------------
// Envelope encrypt / decrypt (NetFilterEncryptionWithHMAC)
//
// Outbound envelope:
//   random_iv      = 13 random bytes
//   hmac_input     = random_iv || plaintext
//   hmac_full      = HMAC-SHA1(hmac_key_, hmac_input)         (20 bytes)
//   iv_plaintext   = hmac_full[0..3] || random_iv             (16 bytes)
//   iv_ciphertext  = AES-256-ECB(session_key, iv_plaintext)
//   body_cipher    = AES-256-CBC(session_key, iv_plaintext, plaintext, PKCS7)
//   on_wire        = iv_ciphertext || body_cipher
//
// Inbound: reverse — verify HMAC in constant time before returning plaintext.
// ---------------------------------------------------------------------------

std::optional<std::vector<uint8_t>>
EncryptedChannel::encrypt_envelope(std::span<const uint8_t> plaintext) const {
    // 13 random bytes
    std::array<uint8_t, kAesBlockBytes - 4> random_iv{};
    if (!secure_random_bytes(random_iv)) return std::nullopt;

    // hmac_input = random_iv || plaintext
    std::vector<uint8_t> hmac_input;
    hmac_input.reserve(random_iv.size() + plaintext.size());
    hmac_input.insert(hmac_input.end(), random_iv.begin(), random_iv.end());
    hmac_input.insert(hmac_input.end(), plaintext.begin(), plaintext.end());

    auto hmac_full = hmac_sha1(hmac_key_, hmac_input);
    if (!hmac_full) return std::nullopt;

    // iv_plaintext = hmac[0..3] || random_iv
    AesBlock iv_plaintext{};
    std::copy_n(hmac_full->begin(), 4, iv_plaintext.begin());
    std::copy_n(random_iv.begin(), random_iv.size(), iv_plaintext.begin() + 4);

    // iv_ciphertext = AES-256-ECB(session_key, iv_plaintext)
    AesBlock iv_ciphertext{};
    if (!aes256_ecb_encrypt_block(session_key_.bytes, iv_plaintext, iv_ciphertext))
        return std::nullopt;

    // body = AES-256-CBC(session_key, iv_plaintext, plaintext, PKCS7)
    auto body = aes256_cbc_encrypt(session_key_.bytes, iv_plaintext, plaintext);
    if (!body) return std::nullopt;

    std::vector<uint8_t> out;
    out.reserve(kAesBlockBytes + body->size());
    out.insert(out.end(), iv_ciphertext.begin(), iv_ciphertext.end());
    out.insert(out.end(), body->begin(), body->end());
    return out;
}

std::optional<std::vector<uint8_t>>
EncryptedChannel::decrypt_envelope(std::span<const uint8_t> wire) const {
    if (wire.size() < kAesBlockBytes + kAesBlockBytes) return std::nullopt;

    AesBlock iv_ciphertext{};
    std::copy_n(wire.begin(), kAesBlockBytes, iv_ciphertext.begin());

    AesBlock iv_plaintext{};
    if (!aes256_ecb_decrypt_block(session_key_.bytes, iv_ciphertext, iv_plaintext))
        return std::nullopt;

    auto body_ct = wire.subspan(kAesBlockBytes);
    auto plaintext = aes256_cbc_decrypt(session_key_.bytes, iv_plaintext, body_ct);
    if (!plaintext) return std::nullopt;

    // Verify HMAC. hmac_input = iv_plaintext[4..16] || plaintext (13 + N).
    std::vector<uint8_t> hmac_input;
    hmac_input.reserve(13 + plaintext->size());
    hmac_input.insert(hmac_input.end(),
                      iv_plaintext.begin() + 4, iv_plaintext.end());
    hmac_input.insert(hmac_input.end(), plaintext->begin(), plaintext->end());

    auto hmac_full = hmac_sha1(hmac_key_, hmac_input);
    if (!hmac_full) return std::nullopt;

    std::span<const uint8_t> hmac_actual(iv_plaintext.data(), 4);
    std::span<const uint8_t> hmac_expect(hmac_full->data(), 4);
    if (!ct_equal(hmac_actual, hmac_expect)) {
        return std::nullopt;  // HMAC mismatch — caller treats as channel kill
    }
    return std::move(*plaintext);
}

}  // namespace wn_steam
