#pragma once

#include <chrono>
#include <optional>
#include <string>
#include <vector>

#include "wn_steam/cm_server.h"

namespace wn_steam {

// Result of a Steam Directory fetch. `servers` is empty iff the call failed
// (or returned no usable WebSocket entries). `error` carries a human-readable
// reason for the failure for logging.
struct SteamDirectoryResult {
    std::vector<CmServer> servers;
    std::string           error;
    int                   http_status = 0;
};

// Synchronous Steam Directory client. Calls
// https://api.steampowered.com/ISteamDirectory/GetCMListForConnect/v1/
// and parses the WebSocket entries. Other transport types are filtered out
// (Phase 1 supports only WebSocket).
//
// `cell_id` should be the last seen `cell_id` from a prior
// CMsgClientLogonResponse, or 0 to let Steam guess from request IP. Steam
// caches this server-side for ~24 h.
//
// `timeout` defaults to 10 seconds; this call BLOCKS the calling thread,
// so do not invoke from the UI thread.
//
// `user_agent` is the User-Agent header. Defaults to the official
// "Valve/Steam HTTP Client 1.0" string for parity with Valve's client.
class SteamDirectoryClient {
public:
    SteamDirectoryClient();
    ~SteamDirectoryClient();

    SteamDirectoryClient(const SteamDirectoryClient&) = delete;
    SteamDirectoryClient& operator=(const SteamDirectoryClient&) = delete;

    [[nodiscard]] SteamDirectoryResult fetch(
        uint32_t cell_id = 0,
        std::chrono::seconds timeout = std::chrono::seconds{10},
        std::string_view user_agent = kDefaultUserAgent,
        std::string_view ca_bundle_path = {});

    static constexpr std::string_view kDefaultUserAgent = "Valve/Steam HTTP Client 1.0";

private:
    // No state today; libcurl easy handle is created per-call. Future:
    // multi-handle pool for parallel CM probing.
};

}  // namespace wn_steam
