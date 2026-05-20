#pragma once

#include <chrono>
#include <cstdint>
#include <mutex>
#include <optional>
#include <unordered_map>
#include <vector>

// WnTicketCache — per-app ownership ticket store.
//
// Holds the opaque blob returned by EMsg 858 (ClientGetAppOwnershipTicket
// Response). Wine's lsteamclient.dll calls SteamUser()->GetAppOwnership
// Ticket(appid) and expects a buffer back; this is that buffer. Tickets
// expire (Steam re-issues them periodically, often every couple of hours)
// — we record the fetch time so callers can decide when to refresh.

namespace wn_steam {

struct OwnedAppTicket {
    uint32_t                 app_id      = 0;
    std::vector<uint8_t>     ticket;          // opaque, signed by Valve
    uint32_t                 eresult     = 0; // 1 = OK
    std::chrono::steady_clock::time_point fetched_at{};
};

class WnTicketCache {
public:
    void store(uint32_t app_id, uint32_t eresult, std::vector<uint8_t> ticket);
    [[nodiscard]] std::optional<OwnedAppTicket> get(uint32_t app_id) const;
    [[nodiscard]] bool has(uint32_t app_id) const;
    [[nodiscard]] size_t size() const;
    void clear();

private:
    mutable std::mutex                            mu_;
    std::unordered_map<uint32_t, OwnedAppTicket>  cache_;
};

}  // namespace wn_steam
