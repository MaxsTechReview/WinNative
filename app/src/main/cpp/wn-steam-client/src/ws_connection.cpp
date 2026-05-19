#include "wn_steam/ws_connection.h"

#include <android/log.h>

#include <exception>

#include <ixwebsocket/IXWebSocket.h>
#include <ixwebsocket/IXWebSocketMessage.h>
#include <ixwebsocket/IXWebSocketMessageType.h>
#include <ixwebsocket/IXSocketTLSOptions.h>

namespace wn_steam {

namespace {

constexpr const char* kLogTag = "WnSteamWS";

#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

// Steam normal-close code; matches RFC 6455 §7.4.1 §1000.
constexpr uint16_t kNormalCloseCode = 1000;

TransportDisconnectReason map_close_reason(uint16_t close_code, bool tls_handshake_failed) {
    if (tls_handshake_failed)                       return TransportDisconnectReason::TlsHandshakeFailed;
    if (close_code >= 1000 && close_code < 1016)    return TransportDisconnectReason::RemoteClose;
    return TransportDisconnectReason::Unknown;
}

template <typename Cb, typename... Args>
void safe_invoke(Cb& cb, Args&&... args) {
    if (!cb) return;
    try {
        cb(std::forward<Args>(args)...);
    } catch (const std::exception& e) {
        WN_LOGE("user callback threw std::exception: %s", e.what());
    } catch (...) {
        WN_LOGE("user callback threw unknown exception");
    }
}

}  // namespace

WsConnection::WsConnection() : ws_(std::make_unique<ix::WebSocket>()) {
    // WS-level ping interval. This is NOT needed for keepalive — Steam CM
    // uses its own app-layer CMsgClientHeartBeat — but IXWebSocket reuses
    // this value as the transport poll() timeout: WebSocketTransport::poll()
    // sets `lastingTimeoutDelayInMs = _pingIntervalSecs` while the socket is
    // OPEN. A value of 0 therefore makes the transport worker thread call
    // poll() with a 0 ms timeout, so ppoll() returns instantly and the run
    // loop busy-spins a full CPU core (measured: 100% of one core, ~99 C).
    // Keep it positive so poll() actually blocks. 60 s = one tiny ping frame
    // per minute, which is negligible next to the ~9 s app heartbeat that
    // already wakes the radio far more often.
    ws_->setPingInterval(60);

    // Do NOT add a subprotocol — Steam rejects WebSocket upgrades that
    // include Sec-WebSocket-Protocol headers it did not request.
}

WsConnection::~WsConnection() {
    if (ws_) {
        user_initiated_close_.store(true);
        ws_->stop(kNormalCloseCode, "client closing");
    }
}

void WsConnection::set_ca_bundle_path(const std::string& path) {
    ca_bundle_path_ = path;
}

bool WsConnection::connect(const std::string& url) {
    TransportState expected = TransportState::Disconnected;
    if (!state_.compare_exchange_strong(expected, TransportState::Connecting)) {
        return false;
    }

    user_initiated_close_.store(false);
    WN_LOGI("connecting WSS: %s (caFile=%s)",
            url.c_str(),
            ca_bundle_path_.empty() ? "<unset>" : ca_bundle_path_.c_str());

    ws_->setUrl(url);
    ws_->disableAutomaticReconnection();  // CMClient owns the reconnect policy.

    // TLS configuration. IXWebSocket consumes a single PEM via `caFile`.
    // If the caller has populated ca_bundle_path_, point libssl at it;
    // otherwise IXWebSocket defaults to "SYSTEM" which on Android will
    // fail. We still issue the connect attempt so the failure is observable
    // via the disconnect callback rather than a silent state stuck in
    // Connecting.
    ix::SocketTLSOptions tls_opts;
    tls_opts.tls = true;
    if (!ca_bundle_path_.empty()) {
        tls_opts.caFile = ca_bundle_path_;
    }
    ws_->setTLSOptions(tls_opts);

    ws_->setOnMessageCallback([this](const ix::WebSocketMessagePtr& msg) {
        if (!msg) return;
        switch (msg->type) {
            case ix::WebSocketMessageType::Open: {
                state_.store(TransportState::Connected);
                ConnectedCallback cb;
                {
                    std::lock_guard<std::mutex> lk(cb_mu_);
                    cb = on_connected_;
                }
                safe_invoke(cb);
                break;
            }
            case ix::WebSocketMessageType::Message: {
                if (msg->binary) {
                    MessageCallback cb;
                    {
                        std::lock_guard<std::mutex> lk(cb_mu_);
                        cb = on_message_;
                    }
                    if (cb) {
                        const auto* p = reinterpret_cast<const uint8_t*>(msg->str.data());
                        safe_invoke(cb, std::span<const uint8_t>(p, msg->str.size()));
                    }
                } else {
                    WN_LOGE("dropped non-binary WS message (text frames are not part of the Steam CM protocol)");
                }
                break;
            }
            case ix::WebSocketMessageType::Close: {
                state_.store(TransportState::Disconnected);
                DisconnectedCallback cb;
                {
                    std::lock_guard<std::mutex> lk(cb_mu_);
                    cb = on_disconnected_;
                }
                const auto reason = user_initiated_close_.load()
                    ? TransportDisconnectReason::UserInitiated
                    : map_close_reason(msg->closeInfo.code, /*tls_handshake_failed=*/false);
                safe_invoke(cb, reason, msg->closeInfo.reason);
                break;
            }
            case ix::WebSocketMessageType::Error: {
                state_.store(TransportState::Disconnected);
                const bool tls_fail =
                    msg->errorInfo.reason.find("tls") != std::string::npos ||
                    msg->errorInfo.reason.find("TLS") != std::string::npos ||
                    msg->errorInfo.reason.find("SSL") != std::string::npos ||
                    msg->errorInfo.reason.find("certificate") != std::string::npos;
                DisconnectedCallback cb;
                {
                    std::lock_guard<std::mutex> lk(cb_mu_);
                    cb = on_disconnected_;
                }
                const auto reason = user_initiated_close_.load()
                    ? TransportDisconnectReason::UserInitiated
                    : map_close_reason(0, tls_fail);
                safe_invoke(cb, reason, msg->errorInfo.reason);
                break;
            }
            default:
                break;  // Ping/Pong/Fragment — not visible to upper layers.
        }
    });

    ws_->start();
    return true;
}

bool WsConnection::send(std::span<const uint8_t> data) {
    if (state_.load() != TransportState::Connected) return false;
    if (!ws_) return false;
    // IXWebSocket expects the payload as std::string; reinterpret-cast is
    // safe because std::string is contiguous and we treat it as a byte
    // container. IXWebSocket itself returns success=false if the socket
    // closed mid-call (handles the race against the worker thread's state
    // transitions), so no extra synchronization is needed here.
    const ix::WebSocketSendInfo info =
        ws_->sendBinary(std::string(reinterpret_cast<const char*>(data.data()),
                                    data.size()));
    return info.success;
}

void WsConnection::disconnect() {
    TransportState s = state_.load();
    if (s == TransportState::Disconnected) return;
    state_.store(TransportState::Disconnecting);
    user_initiated_close_.store(true);
    if (ws_) ws_->stop(kNormalCloseCode, "client disconnect");
    state_.store(TransportState::Disconnected);
}

TransportState WsConnection::state() const {
    return state_.load();
}

void WsConnection::set_on_message(MessageCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_message_ = std::move(cb);
}

void WsConnection::set_on_connected(ConnectedCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_connected_ = std::move(cb);
}

void WsConnection::set_on_disconnected(DisconnectedCallback cb) {
    std::lock_guard<std::mutex> lk(cb_mu_);
    on_disconnected_ = std::move(cb);
}

}  // namespace wn_steam
