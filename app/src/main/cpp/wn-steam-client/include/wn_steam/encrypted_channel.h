#pragma once

#include <atomic>
#include <functional>
#include <memory>
#include <mutex>
#include <span>
#include <string>
#include <vector>

#include "wn_steam/crypto.h"
#include "wn_steam/euniverse.h"
#include "wn_steam/transport.h"

namespace wn_steam {

// State of the encrypted channel state machine, mirroring SteamKit2's
// `EnvelopeEncryptedConnection`.
enum class ChannelState : uint8_t {
    Disconnected,    // not connected to anything
    Connected,       // transport up, awaiting server's ChannelEncryptRequest
    Challenged,      // received Request, sent Response, awaiting Result
    Encrypted,       // handshake OK; all messages now use HMAC-AES-CBC envelope
    Closing,         // disconnect in progress
};

// Why the channel terminated. Distinct from transport-level reasons because
// the channel also fails on protocol errors (bad universe, decrypt failure,
// HMAC mismatch).
enum class ChannelDisconnectReason : uint8_t {
    UserInitiated,
    TransportError,           // underlying transport reported disconnect
    HandshakeProtocolError,   // malformed or unexpected handshake message
    HandshakeFailed,          // server returned non-OK in ChannelEncryptResult
    EnvelopeDecryptFailed,    // post-handshake envelope failed to decrypt
    HmacMismatch,             // post-handshake HMAC didn't match
};

// EncryptedChannel wraps a Transport and runs the ChannelEncrypt* handshake
// + post-handshake `NetFilterEncryptionWithHMAC` envelope on top of it.
//
// The upper layer (CMClient) sees only decrypted application-layer message
// bytes via `set_on_message` after the channel reaches Encrypted state.
// Before that, no application messages are delivered.
//
// Thread model: callbacks fire on the underlying Transport's worker thread.
class EncryptedChannel {
public:
    using MessageCallback      = std::function<void(std::span<const uint8_t>)>;
    using ConnectedCallback    = std::function<void()>;
    using DisconnectedCallback = std::function<void(ChannelDisconnectReason,
                                                    const std::string& detail)>;

    explicit EncryptedChannel(std::unique_ptr<Transport> transport);
    ~EncryptedChannel();

    EncryptedChannel(const EncryptedChannel&)            = delete;
    EncryptedChannel& operator=(const EncryptedChannel&) = delete;

    // Connect via the underlying transport and run the handshake. Returns
    // false if a connection is already in progress or open. Callbacks must
    // be installed first.
    [[nodiscard]] bool connect(const std::string& url);

    // Send a single application-layer message. Encrypts under the negotiated
    // session key and ships through the transport as one binary frame. Fails
    // (returns false) before the channel reaches Encrypted state.
    [[nodiscard]] bool send(std::span<const uint8_t> plaintext);

    // Initiates an orderly close.
    void disconnect();

    [[nodiscard]] ChannelState state() const noexcept { return state_.load(); }
    [[nodiscard]] EUniverse    universe() const noexcept { return negotiated_universe_; }

    // Forward to the underlying transport. See Transport::set_ca_bundle_path.
    void set_ca_bundle_path(const std::string& path) {
        if (transport_) transport_->set_ca_bundle_path(path);
    }

    // Callbacks must be set before connect(). They fire on the underlying
    // transport's worker thread; do not block in them.
    void set_on_message(MessageCallback cb);
    void set_on_connected(ConnectedCallback cb);
    void set_on_disconnected(DisconnectedCallback cb);

    // Test-only hook so unit tests can inject a fixed session key. Production
    // callers must not touch this — RNG handles it during the handshake.
    void test_only_inject_session_key(const SessionKey& k) { test_injected_key_ = k; }

private:
    // ---- handshake step handlers (called on transport worker thread) ----
    void on_transport_message(std::span<const uint8_t> bytes);
    void on_transport_connected();
    void on_transport_disconnected(TransportDisconnectReason r, const std::string& detail);

    bool handle_channel_encrypt_request(std::span<const uint8_t> body);
    bool handle_channel_encrypt_result(std::span<const uint8_t> body);

    // ---- envelope encrypt / decrypt (HMAC-IV-in-ECB) ----
    [[nodiscard]] std::optional<std::vector<uint8_t>>
    encrypt_envelope(std::span<const uint8_t> plaintext) const;

    [[nodiscard]] std::optional<std::vector<uint8_t>>
    decrypt_envelope(std::span<const uint8_t> wire) const;

    void fail_channel(ChannelDisconnectReason r, std::string detail);

    std::unique_ptr<Transport>  transport_;
    std::atomic<ChannelState>   state_{ChannelState::Disconnected};

    // Session-key material is held inside SecureSessionKey so it zeroes on
    // destruction. `hmac_key_` is the first 16 bytes of the session key.
    SecureSessionKey            session_key_;
    std::array<uint8_t, kHmacKeyLength> hmac_key_{};
    EUniverse                   negotiated_universe_ = EUniverse::Invalid;

    // Optional fixed key for unit tests.
    std::optional<SessionKey>   test_injected_key_;

    mutable std::mutex          cb_mu_;
    MessageCallback             on_message_;
    ConnectedCallback           on_connected_;
    DisconnectedCallback        on_disconnected_;
};

}  // namespace wn_steam
