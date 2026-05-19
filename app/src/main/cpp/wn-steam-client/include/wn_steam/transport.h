#pragma once

#include <cstdint>
#include <functional>
#include <span>
#include <string>
#include <vector>

namespace wn_steam {

// Transport lifecycle states. Mirrors SteamKit2's `Connection` states.
enum class TransportState : uint8_t {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
};

// Why a transport disconnected. `UserInitiated` means the local caller
// closed the connection; everything else means a remote / network failure
// that the upstream client should treat as a reason to reconnect.
enum class TransportDisconnectReason : uint8_t {
    UserInitiated,
    RemoteClose,
    TlsHandshakeFailed,
    NetworkError,
    HandshakeTimeout,
    Unknown,
};

// Abstract base for any byte-oriented transport to a Steam CM. The two
// implementations in Phase 1 are:
//   - WsConnection (IXWebSocket-backed, used for production WSS endpoints)
//   - InMemoryConnection (used by unit tests to play recorded byte streams)
class Transport {
public:
    using MessageCallback    = std::function<void(std::span<const uint8_t>)>;
    using ConnectedCallback  = std::function<void()>;
    using DisconnectedCallback = std::function<void(TransportDisconnectReason,
                                                    const std::string& detail)>;

    Transport() = default;
    virtual ~Transport() = default;

    Transport(const Transport&) = delete;
    Transport& operator=(const Transport&) = delete;

    // Begin connecting to `url` (e.g. "wss://cm-01.cm.steampowered.com:443/cmsocket/").
    // Returns false if a connection is already in progress or open. Connect
    // is asynchronous; on success the `on_connected` callback fires later
    // on the transport's internal thread.
    [[nodiscard]] virtual bool connect(const std::string& url) = 0;

    // Send a binary frame. Returns false if the transport is not Connected.
    // Frames are sent as a single WebSocket binary message; the caller does
    // NOT need to chunk.
    [[nodiscard]] virtual bool send(std::span<const uint8_t> data) = 0;

    // Initiates an orderly close. The disconnect callback fires after the
    // close handshake completes.
    virtual void disconnect() = 0;

    [[nodiscard]] virtual TransportState state() const = 0;

    // Callbacks must be set before connect(); changing them after connect
    // is undefined behavior. Each fires on the transport's internal thread
    // — do not block in them.
    virtual void set_on_message(MessageCallback cb)        = 0;
    virtual void set_on_connected(ConnectedCallback cb)    = 0;
    virtual void set_on_disconnected(DisconnectedCallback cb) = 0;

    // Optional TLS configuration hook. Only meaningful for transports that
    // do TLS themselves (WsConnection). Path is a single PEM bundle file
    // (Android's CA store directory is not accepted by IXWebSocket/OpenSSL
    // here — extract Mozilla cacert.pem from app assets first). Empty path
    // means "use transport default", which fails on Android. Default impl
    // ignores the call.
    virtual void set_ca_bundle_path(const std::string& /*path*/) {}
};

}  // namespace wn_steam
