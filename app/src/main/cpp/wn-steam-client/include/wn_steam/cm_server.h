#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace wn_steam {

// Transport flavor of a CM endpoint. Matches the `type` field in the
// ISteamDirectory/GetCMListForConnect response.
enum class CmTransport : uint8_t {
    Unknown    = 0,
    WebSocket  = 1,  // wss://<host>:<port>/cmsocket/   ← what we use
    Tcp        = 2,  // raw TCP, port 27015/27017/27018/27019
};

// One CM endpoint. SteamDirectory returns up to ~20 of these per call.
//
// `endpoint` is the raw "host:port" string from the API; `host` and `port`
// are parsed for convenience. For WebSocket transport, the WSS URL is
// `wss://<host>:<port>/cmsocket/`.
struct CmServer {
    std::string  endpoint;        // "cm-01-fra1.cm.steampowered.com:443"
    std::string  host;            // "cm-01-fra1.cm.steampowered.com"
    uint16_t     port = 0;
    CmTransport  transport = CmTransport::Unknown;
    std::string  realm;           // "steamglobal" / "steamchina"
    std::string  datacenter;      // "fra1", "sea1", ...
    int32_t      load = 0;        // Steam-reported load score; lower is better
    float        weighted_load = 0.0f;

    // Convenience: full WSS URL for this server. Empty if transport != WebSocket.
    [[nodiscard]] std::string websocket_url() const;
};

// Splits "host:port" into host/port. Returns false if format is unparsable
// or port is out of range. host/port are NOT modified on failure.
[[nodiscard]] bool parse_endpoint(std::string_view endpoint,
                                  std::string& host,
                                  uint16_t& port) noexcept;

}  // namespace wn_steam
