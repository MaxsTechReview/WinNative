#include "wn_steam/wn_ticket_cache.h"

namespace wn_steam {

void WnTicketCache::store(uint32_t app_id, uint32_t eresult,
                          std::vector<uint8_t> ticket) {
    std::lock_guard<std::mutex> lk(mu_);
    auto& slot      = cache_[app_id];
    slot.app_id     = app_id;
    slot.eresult    = eresult;
    slot.ticket     = std::move(ticket);
    slot.fetched_at = std::chrono::steady_clock::now();
}

std::optional<OwnedAppTicket> WnTicketCache::get(uint32_t app_id) const {
    std::lock_guard<std::mutex> lk(mu_);
    auto it = cache_.find(app_id);
    if (it == cache_.end()) return std::nullopt;
    return it->second;
}

bool WnTicketCache::has(uint32_t app_id) const {
    std::lock_guard<std::mutex> lk(mu_);
    return cache_.find(app_id) != cache_.end();
}

size_t WnTicketCache::size() const {
    std::lock_guard<std::mutex> lk(mu_);
    return cache_.size();
}

void WnTicketCache::clear() {
    std::lock_guard<std::mutex> lk(mu_);
    cache_.clear();
}

}  // namespace wn_steam
