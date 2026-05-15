#pragma once

#include <atomic>
#include <chrono>
#include <functional>
#include <memory>
#include <mutex>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "wn_steam/cmsg_protobuf_header.h"
#include "wn_steam/emsg.h"
#include "wn_steam/encrypted_channel.h"
#include "wn_steam/heartbeat.h"
#include "wn_steam/job_manager.h"
#include "wn_steam/transport.h"

namespace wn_steam {

// Top-level Steam CM client state.
enum class ClientState : uint8_t {
    Disconnected,    // no transport / no channel
    Connecting,      // transport / handshake in progress
    Connected,       // encrypted channel established, not yet logged on
    LoggedOn,        // CMsgClientLogonResponse OK
};

// CMClient owns the EncryptedChannel + JobManager + Heartbeat and routes
// inbound application messages to typed callbacks or job continuations.
//
// Thread model:
//   - Public methods are thread-safe.
//   - Callbacks fire on the transport's worker thread; do not block.
class CMClient {
public:
    using StateCallback = std::function<void(ClientState)>;
    using ClientMessageCallback = std::function<void(EMsg emsg,
                                                     const CMsgProtoBufHeader& header,
                                                     std::span<const uint8_t> body)>;

    CMClient();
    ~CMClient();

    CMClient(const CMClient&)            = delete;
    CMClient& operator=(const CMClient&) = delete;

    void set_ca_bundle_path(const std::string& path);

    // Connect to the given WSS URL. Returns false if already connecting/
    // connected. State callback fires as the handshake progresses.
    [[nodiscard]] bool connect(const std::string& url);

    void disconnect();

    [[nodiscard]] ClientState state() const noexcept { return state_.load(); }
    [[nodiscard]] uint64_t    steam_id() const noexcept { return steam_id_.load(); }
    [[nodiscard]] int32_t     session_id() const noexcept { return session_id_.load(); }

    // ---------------------------------------------------------------------
    // Unified service-method call. Builds:
    //   EMsg = ServiceMethodCallFromClient (authed) or
    //          ServiceMethodCallFromClientNonAuthed (pre-logon)
    //   header.target_job_name = method_name (e.g. "Authentication.GetPasswordRSAPublicKey#1")
    //   header.jobid_source    = freshly allocated
    //
    // The continuation fires when a matching ServiceMethodResponse arrives
    // (with the same jobid in jobid_target), or with synthetic_failure=true
    // on timeout/disconnect.
    // ---------------------------------------------------------------------
    void call_service_method(std::string_view method_name,
                             bool authed,
                             std::span<const uint8_t> request_body,
                             JobContinuation cb,
                             std::chrono::seconds timeout = std::chrono::seconds{30});

    // Send a typed client-message (proto-flagged EMsg with body). Used for
    // ClientHello, ClientLogon, ClientHeartBeat, ClientLogOff, etc.
    [[nodiscard]] bool send_proto_message(EMsg emsg,
                                          std::span<const uint8_t> body);

    // High-level helper: build and send a CMsgClientLogon with the given
    // refresh token. After the response is dispatched by inbound routing
    // (CMsgClientLogonResponse), CMClient transitions to LoggedOn and
    // starts the heartbeat automatically. Returns false if the channel is
    // not yet encrypted.
    [[nodiscard]] bool logon_with_refresh_token(
        const std::string& refresh_token,
        const std::string& account_name = "",
        uint64_t client_supplied_steam_id = 0);

    void set_on_state(StateCallback cb);
    void set_on_client_message(ClientMessageCallback cb);

private:
    void on_channel_connected();
    void on_channel_disconnected(ChannelDisconnectReason r, const std::string& detail);
    void on_channel_message(std::span<const uint8_t> bytes);

    void set_state_locked_(ClientState s);
    void route_inbound_(EMsg emsg, const CMsgProtoBufHeader& header,
                        std::span<const uint8_t> body);

    std::unique_ptr<EncryptedChannel> channel_;
    JobManager                        jobs_;
    Heartbeat                         heartbeat_;

    std::atomic<ClientState>          state_{ClientState::Disconnected};
    std::atomic<uint64_t>             steam_id_{0};
    std::atomic<int32_t>              session_id_{0};

    mutable std::mutex                cb_mu_;
    StateCallback                     on_state_;
    ClientMessageCallback             on_client_message_;
};

}  // namespace wn_steam
