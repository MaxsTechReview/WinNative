#pragma once

#include <atomic>
#include <memory>
#include <mutex>
#include <string>

#include "wn_steam/transport.h"

namespace ix { class WebSocket; }

namespace wn_steam {

// IXWebSocket-backed Transport. One instance per CM connection; not designed
// for reuse after disconnect — construct a fresh one for each connection
// attempt. Internal worker thread is owned by IXWebSocket.
//
// Thread model:
//   - connect / send / disconnect are called from the caller's thread
//     (typically the client's I/O thread); they are safe to call from any
//     thread.
//   - on_message / on_connected / on_disconnected fire on IXWebSocket's
//     internal worker thread; DO NOT block in them.
//
// TLS:
//   IXWebSocket's OpenSSL backend reads the trust store from a single PEM
//   file passed via `caFile`. Android's CA store is a directory of hashed
//   PEMs under /system/etc/security/cacerts/, which OpenSSL's
//   SSL_CTX_load_verify_locations() does NOT accept as a `CAfile` argument.
//   The JNI/Kotlin side must therefore:
//     1. extract a bundled PEM (e.g. Mozilla cacert.pem) from app assets
//        into the app's files dir on first run, and
//     2. call set_ca_bundle_path(<path>) before connect().
//   If no path is set, the handshake will fall back to IXWebSocket's
//   "SYSTEM" default and fail on stock Android.
class WsConnection final : public Transport {
public:
    WsConnection();
    ~WsConnection() override;

    // Sets the absolute path to a single PEM trust bundle. Must be called
    // before connect(). Empty path = use IXWebSocket default (will fail on
    // Android — see class comment).
    void set_ca_bundle_path(const std::string& path) override;

    [[nodiscard]] bool connect(const std::string& url) override;
    [[nodiscard]] bool send(std::span<const uint8_t> data) override;
    void disconnect() override;
    [[nodiscard]] TransportState state() const override;

    void set_on_message(MessageCallback cb) override;
    void set_on_connected(ConnectedCallback cb) override;
    void set_on_disconnected(DisconnectedCallback cb) override;

private:
    std::unique_ptr<ix::WebSocket> ws_;
    std::atomic<TransportState>    state_{TransportState::Disconnected};
    std::atomic<bool>              user_initiated_close_{false};

    mutable std::mutex             cb_mu_;
    MessageCallback                on_message_;
    ConnectedCallback              on_connected_;
    DisconnectedCallback           on_disconnected_;

    std::string                    ca_bundle_path_;
};

}  // namespace wn_steam
