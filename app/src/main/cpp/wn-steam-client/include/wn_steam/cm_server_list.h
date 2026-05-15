#pragma once

#include <chrono>
#include <mutex>
#include <optional>
#include <span>
#include <vector>

#include "wn_steam/cm_server.h"

namespace wn_steam {

// Per-server quality tracking, mirroring SteamKit's `SmartCMServerList`.
// A server marked Bad is skipped until `bad_memory` elapses since the mark.
enum class ServerQuality : uint8_t { Good, Bad };

// Thread-safe ordered list of CM servers. Servers can be added in bulk
// (e.g. from Steam Directory), marked good/bad after connection attempts,
// and queried via `next_good` to pick the best candidate for a new attempt.
//
// Order matches SteamKit's `SmartCMServerList`:
//   1. Good servers, by insertion order (caller controls priority)
//   2. Bad servers whose `bad_memory` has elapsed (auto-promoted to Good)
//   3. Bad servers still within `bad_memory` (skipped)
class CmServerList {
public:
    // Default "memory" interval for a Bad mark, matching JavaSteam/SteamKit.
    static constexpr std::chrono::minutes kDefaultBadMemory{5};

    CmServerList();
    explicit CmServerList(std::chrono::minutes bad_memory);

    // Replace the entire list. Any prior Good/Bad state is dropped.
    void replace_all(std::span<const CmServer> servers);

    // Append a single server with Good quality.
    void add(const CmServer& server);

    // Number of servers regardless of quality.
    [[nodiscard]] size_t size() const;

    // Best next server to try, or nullopt if the list is empty or every
    // server is currently marked Bad. Promotes expired Bad → Good before
    // returning.
    [[nodiscard]] std::optional<CmServer> next_good();

    // Mark a server (matched by endpoint string) Bad. No-op if not found.
    void mark_bad(std::string_view endpoint);

    // Mark a server Good (e.g. after a successful handshake).
    void mark_good(std::string_view endpoint);

    // Clear all Bad marks. Used after a network connectivity recovery.
    void reset_quality();

private:
    struct Entry {
        CmServer server;
        ServerQuality quality = ServerQuality::Good;
        std::chrono::steady_clock::time_point marked_bad_at{};
    };

    void promote_expired_locked();

    mutable std::mutex      mu_;
    std::vector<Entry>      entries_;
    std::chrono::minutes    bad_memory_;
    size_t                  next_index_ = 0;  // round-robin cursor for ties
};

// SteamKit's hardcoded fallback list, used only when Steam Directory is
// unreachable (e.g. first launch with no network during initial sync).
// Always WebSocket transport, realm "steamglobal".
[[nodiscard]] std::vector<CmServer> hardcoded_fallback_servers();

}  // namespace wn_steam
