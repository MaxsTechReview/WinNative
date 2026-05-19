#include "wn_steam/cm_server_list.h"

#include <algorithm>

namespace wn_steam {

CmServerList::CmServerList() : bad_memory_(kDefaultBadMemory) {}

CmServerList::CmServerList(std::chrono::minutes bad_memory)
    : bad_memory_(bad_memory) {}

void CmServerList::replace_all(std::span<const CmServer> servers) {
    std::lock_guard<std::mutex> lk(mu_);
    entries_.clear();
    entries_.reserve(servers.size());
    for (const auto& s : servers) {
        entries_.push_back(Entry{s, ServerQuality::Good, {}});
    }
    next_index_ = 0;
}

void CmServerList::add(const CmServer& server) {
    std::lock_guard<std::mutex> lk(mu_);
    entries_.push_back(Entry{server, ServerQuality::Good, {}});
}

size_t CmServerList::size() const {
    std::lock_guard<std::mutex> lk(mu_);
    return entries_.size();
}

void CmServerList::promote_expired_locked() {
    const auto now = std::chrono::steady_clock::now();
    for (auto& e : entries_) {
        if (e.quality == ServerQuality::Bad && now - e.marked_bad_at >= bad_memory_) {
            e.quality = ServerQuality::Good;
            e.marked_bad_at = {};
        }
    }
}

std::optional<CmServer> CmServerList::next_good() {
    std::lock_guard<std::mutex> lk(mu_);
    if (entries_.empty()) return std::nullopt;

    promote_expired_locked();

    const size_t n = entries_.size();
    for (size_t i = 0; i < n; ++i) {
        const size_t idx = (next_index_ + i) % n;
        if (entries_[idx].quality == ServerQuality::Good) {
            next_index_ = (idx + 1) % n;
            return entries_[idx].server;
        }
    }
    return std::nullopt;  // every server is currently Bad
}

void CmServerList::mark_bad(std::string_view endpoint) {
    std::lock_guard<std::mutex> lk(mu_);
    for (auto& e : entries_) {
        if (e.server.endpoint == endpoint) {
            e.quality = ServerQuality::Bad;
            e.marked_bad_at = std::chrono::steady_clock::now();
            return;
        }
    }
}

void CmServerList::mark_good(std::string_view endpoint) {
    std::lock_guard<std::mutex> lk(mu_);
    for (auto& e : entries_) {
        if (e.server.endpoint == endpoint) {
            e.quality = ServerQuality::Good;
            e.marked_bad_at = {};
            return;
        }
    }
}

void CmServerList::reset_quality() {
    std::lock_guard<std::mutex> lk(mu_);
    for (auto& e : entries_) {
        e.quality = ServerQuality::Good;
        e.marked_bad_at = {};
    }
}

// ---------------------------------------------------------------------------
// SteamKit-style hardcoded fallback list. Used only when Steam Directory is
// unreachable. These hosts are advertised by Steam as long-lived WebSocket
// CMs; pick a handful across geographies so a first-launch user without
// network for the directory call still has something to try.
//
// Source of names: cross-referenced against ValvePython/steam's
// `steam.client.cm.bootstrap_cm_list` and JavaSteam's `SmartCMServerList`
// hard defaults (cmp1-sea1.steamserver.net:443 etc.). Steam rotates these
// over years; treat the list as best-effort.
// ---------------------------------------------------------------------------

std::vector<CmServer> hardcoded_fallback_servers() {
    // Steam advertises CM endpoints under the steamserver.net zone, in the
    // pattern `ext{N}-{dc}.steamserver.net:443` for WSS. Verified via real
    // GetCMListForConnect responses (see SteamKit2 + node-steam-user logs).
    // The previous *.cm.steampowered.com names did NOT resolve on tested
    // devices. These do.
    static constexpr struct {
        const char* host;
        const char* dc;
    } kFallback[] = {
        {"ext1-sea1.steamserver.net", "sea1"},
        {"ext2-sea1.steamserver.net", "sea1"},
        {"ext1-iad1.steamserver.net", "iad1"},
        {"ext2-iad1.steamserver.net", "iad1"},
        {"ext1-fra1.steamserver.net", "fra1"},
        {"ext2-fra1.steamserver.net", "fra1"},
        {"ext1-lax1.steamserver.net", "lax1"},
        {"ext1-sgp1.steamserver.net", "sgp1"},
    };

    std::vector<CmServer> out;
    out.reserve(sizeof(kFallback) / sizeof(kFallback[0]));
    for (const auto& f : kFallback) {
        CmServer s;
        s.host        = f.host;
        s.port        = 443;
        s.endpoint    = std::string(f.host) + ":443";
        s.transport   = CmTransport::WebSocket;
        s.realm       = "steamglobal";
        s.datacenter  = f.dc;
        s.load        = 0;       // unknown
        s.weighted_load = 0.0f;
        out.push_back(std::move(s));
    }
    return out;
}

}  // namespace wn_steam
