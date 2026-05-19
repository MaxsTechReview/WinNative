#include "wn_steam/wn_library_store.h"

#include <algorithm>
#include <charconv>
#include <sstream>
#include <utility>

#include "wn_steam/vdf.h"

namespace wn_steam {

namespace {

// PICS package VDF layout (root key = "<packageid>"):
//   <packageid> {
//     packageid <int>
//     billingtype <int>
//     licensetype <int>
//     status <int>
//     extended { ... }
//     appids { 0 <appid> 1 <appid> ... }
//     depotids { 0 <depotid> 1 <depotid> ... }
//     appitems { ... }
//   }
// The "0"/"1"/"2" subkeys under appids/depotids are sequential array indices,
// just like JavaSteam's KeyValue.toIntList() pattern.
void extract_uint32_array(const vdf::KVNode* parent, std::vector<uint32_t>& out) {
    if (!parent) return;
    for (const auto& c : parent->children()) {
        uint64_t v = c->as_uint(0);
        if (v != 0) out.push_back(static_cast<uint32_t>(v));
    }
}

// PICS app VDF layout (root key = "<appid>"):
//   <appid> {
//     appinfo {
//       appid <uint>
//       common {
//         name        "..."
//         sortas      "..."   (optional)
//         type        "Game" | "DLC" | "Tool" | ...
//         oslist      "windows,linux,macos"   (CSV)
//         parent      <appid>   (DLC only — the game it belongs to)
//       }
//       extended {
//         listofdlc   "123,456,789"   (CSV of DLC appids — game side)
//       }
//       depots { ... }
//       config { launch { 0 { executable "..." } ... } }
//     }
//   }
void parse_csv_appids(std::string_view csv, std::vector<uint32_t>& out) {
    size_t start = 0;
    while (start <= csv.size()) {
        size_t end = csv.find(',', start);
        if (end == std::string_view::npos) end = csv.size();
        // skip whitespace
        size_t a = start, b = end;
        while (a < b && std::isspace(static_cast<unsigned char>(csv[a]))) ++a;
        while (b > a && std::isspace(static_cast<unsigned char>(csv[b - 1]))) --b;
        if (b > a) {
            uint32_t v = 0;
            auto* first = csv.data() + a;
            auto* last  = csv.data() + b;
            auto r = std::from_chars(first, last, v);
            if (r.ec == std::errc{} && v != 0) out.push_back(v);
        }
        if (end == csv.size()) break;
        start = end + 1;
    }
}

}  // namespace

void WnLibraryStore::ingest_license_list(const pb::CMsgClientLicenseList& msg) {
    {
        std::lock_guard<std::mutex> lk(mu_);
        for (const auto& l : msg.licenses) {
            // The same package_id can legitimately appear multiple times
            // (e.g. different family-share licenses for the same SKU). We
            // keep the entry with the freshest access_token / change_number,
            // preferring direct ownership over borrowed.
            auto& slot = packages_[l.package_id];
            slot.package_id   = l.package_id;
            if (l.access_token != 0) slot.access_token = l.access_token;
            if (l.change_number > slot.change_number) slot.change_number = l.change_number;
            slot.license_flags = l.flags;
            slot.license_type  = l.license_type;
        }
    }
    notify_();
}

std::vector<pb::PicsPackageInfoReq>
WnLibraryStore::get_pending_package_pics_request(size_t max_count) const {
    std::vector<pb::PicsPackageInfoReq> out;
    std::lock_guard<std::mutex> lk(mu_);
    for (const auto& [id, p] : packages_) {
        if (p.pics_fetched) continue;
        out.push_back(pb::PicsPackageInfoReq{p.package_id, p.access_token});
        if (out.size() >= max_count) break;
    }
    return out;
}

std::vector<pb::PicsAppInfoReq>
WnLibraryStore::get_pending_app_pics_request(size_t max_count) const {
    std::vector<pb::PicsAppInfoReq> out;
    std::lock_guard<std::mutex> lk(mu_);
    for (const auto& [id, a] : apps_) {
        if (a.pics_fetched) continue;
        if (a.missing_token && a.access_token == 0) continue;
        out.push_back(pb::PicsAppInfoReq{a.app_id, a.access_token, false});
        if (out.size() >= max_count) break;
    }
    return out;
}

std::vector<uint32_t>
WnLibraryStore::get_apps_needing_access_token() const {
    std::vector<uint32_t> out;
    std::lock_guard<std::mutex> lk(mu_);
    for (const auto& [id, a] : apps_) {
        if (a.missing_token && a.access_token == 0) out.push_back(a.app_id);
    }
    return out;
}

void WnLibraryStore::ingest_package_pics_response(
        const pb::CMsgClientPICSProductInfoResponse& resp) {
    {
        std::lock_guard<std::mutex> lk(mu_);
        for (const auto& p : resp.packages) {
            auto& slot = packages_[p.packageid];
            slot.package_id    = p.packageid;
            slot.change_number = static_cast<int32_t>(p.change_number);
            slot.pics_fetched  = true;
            // Parse the VDF if a buffer is present (HTTP-hosted ones come
            // back empty; Phase 5 will fetch those from the CDN).
            if (!p.buffer.empty()) {
                uint32_t prefix_pkg = 0;
                auto root = vdf::parse_binary_package(p.buffer, &prefix_pkg);
                if (root) {
                    extract_uint32_array(root->child("appids"),   slot.app_ids);
                    extract_uint32_array(root->child("depotids"), slot.depot_ids);
                    // Seed app stubs for any appid we don't yet know.
                    for (uint32_t aid : slot.app_ids) {
                        auto& app = apps_[aid];
                        app.app_id = aid;
                        // Track which package(s) granted this app.
                        if (std::find(app.source_package_ids.begin(),
                                      app.source_package_ids.end(),
                                      p.packageid) == app.source_package_ids.end()) {
                            app.source_package_ids.push_back(p.packageid);
                        }
                    }
                }
            }
        }
        for (uint32_t unknown : resp.unknown_packageids) {
            auto it = packages_.find(unknown);
            if (it != packages_.end()) {
                it->second.pics_fetched = true;   // give up — Steam doesn't know it
            }
        }
    }
    notify_();
}

void WnLibraryStore::ingest_app_pics_response(
        const pb::CMsgClientPICSProductInfoResponse& resp) {
    {
        std::lock_guard<std::mutex> lk(mu_);
        for (const auto& a : resp.apps) {
            auto& app = apps_[a.appid];
            app.app_id        = a.appid;
            app.change_number = a.change_number;
            app.pics_fetched  = true;
            if (a.missing_token) {
                app.missing_token = true;
                app.pics_fetched  = false;   // re-fetch after access token grant
                continue;
            }
            app.missing_token = false;
            if (a.buffer.empty()) continue;
            // PICS app buffers arrive as TEXT VDF (e.g. `"appinfo" { "appid"
            // "578080" "common" { ... } }`), while package buffers and depot
            // manifests are binary — parse_auto sniffs the first non-whitespace
            // byte and dispatches accordingly.
            auto root = vdf::parse_auto(a.buffer);
            if (!root) continue;
            // For text-VDF app buffers the root node IS "appinfo"; for the
            // older binary form the root is the appid and "appinfo" is a
            // child. Handle both shapes.
            const vdf::KVNode* appinfo =
                (root->name() == "appinfo") ? root.get() : root->child("appinfo");
            if (!appinfo) appinfo = root.get();
            if (const auto* common = appinfo->child("common")) {
                app.name    = common->child("name")    ? common->child("name")->as_string()    : app.name;
                app.sort_as = common->child("sortas")  ? common->child("sortas")->as_string()  : app.sort_as;
                app.type    = common->child("type")    ? common->child("type")->as_string()    : app.type;
                app.os_list = common->child("oslist")  ? common->child("oslist")->as_string()  : app.os_list;
                if (const auto* parent = common->child("parent")) {
                    app.parent_app_id = static_cast<uint32_t>(parent->as_uint(0));
                }
            }
            if (const auto* extended = appinfo->child("extended")) {
                if (const auto* dlc_csv = extended->child("listofdlc")) {
                    app.dlc_app_ids.clear();
                    parse_csv_appids(dlc_csv->as_string(), app.dlc_app_ids);
                }
            }
            // DLC linkage: if this app declares a parent, make sure the
            // parent knows it as a child.
            if (app.parent_app_id != 0) {
                auto& parent = apps_[app.parent_app_id];
                parent.app_id = app.parent_app_id;
                if (std::find(parent.dlc_app_ids.begin(),
                              parent.dlc_app_ids.end(),
                              app.app_id) == parent.dlc_app_ids.end()) {
                    parent.dlc_app_ids.push_back(app.app_id);
                }
            }
        }
        for (uint32_t unknown : resp.unknown_appids) {
            auto it = apps_.find(unknown);
            if (it != apps_.end()) it->second.pics_fetched = true;
        }
    }
    notify_();
}

void WnLibraryStore::ingest_app_access_tokens(
        const pb::CMsgClientPICSAccessTokenResponse& resp) {
    {
        std::lock_guard<std::mutex> lk(mu_);
        for (const auto& at : resp.app_access_tokens) {
            auto it = apps_.find(at.appid);
            if (it == apps_.end()) {
                // Token grant for an app we don't track yet — create a stub.
                apps_[at.appid] = OwnedApp{at.appid, 0, {}, {}, {}, {}, 0, {}, {}, false, false, at.access_token};
            } else {
                it->second.access_token  = at.access_token;
                it->second.missing_token = false;
            }
        }
        // app_denied_tokens — Steam refused us a token. Mark the app fetched
        // (we've done all we can) so the populate loop doesn't spin.
        for (uint32_t denied : resp.app_denied_tokens) {
            auto it = apps_.find(denied);
            if (it != apps_.end()) {
                it->second.pics_fetched = true;
                it->second.missing_token = false;
            }
        }
    }
    notify_();
}

std::vector<OwnedPackage> WnLibraryStore::packages() const {
    std::lock_guard<std::mutex> lk(mu_);
    std::vector<OwnedPackage> out;
    out.reserve(packages_.size());
    for (const auto& [_, p] : packages_) out.push_back(p);
    return out;
}

std::vector<OwnedApp> WnLibraryStore::apps() const {
    std::lock_guard<std::mutex> lk(mu_);
    std::vector<OwnedApp> out;
    out.reserve(apps_.size());
    for (const auto& [_, a] : apps_) out.push_back(a);
    return out;
}

std::vector<OwnedApp> WnLibraryStore::owned_apps() const {
    std::lock_guard<std::mutex> lk(mu_);
    std::vector<OwnedApp> out;
    for (const auto& [_, a] : apps_) {
        if (!a.source_package_ids.empty()) out.push_back(a);
    }
    return out;
}

std::optional<OwnedApp> WnLibraryStore::find_app(uint32_t app_id) const {
    std::lock_guard<std::mutex> lk(mu_);
    auto it = apps_.find(app_id);
    if (it == apps_.end()) return std::nullopt;
    return it->second;
}

size_t WnLibraryStore::package_count() const {
    std::lock_guard<std::mutex> lk(mu_);
    return packages_.size();
}

size_t WnLibraryStore::app_count() const {
    std::lock_guard<std::mutex> lk(mu_);
    return apps_.size();
}

size_t WnLibraryStore::owned_app_count() const {
    std::lock_guard<std::mutex> lk(mu_);
    size_t n = 0;
    for (const auto& [_, a] : apps_) if (!a.source_package_ids.empty()) ++n;
    return n;
}

namespace {

// Hand-rolled JSON escaper. We don't pull in nlohmann::json for this — the
// snapshot is a one-shot marshalling format, and pulling json.hpp into the
// shared lib for a few hundred strings inflates binary size measurably.
void write_json_string(std::ostringstream& os, std::string_view s) {
    os.put('"');
    for (char c : s) {
        switch (c) {
            case '"':  os << "\\\""; break;
            case '\\': os << "\\\\"; break;
            case '\b': os << "\\b";  break;
            case '\f': os << "\\f";  break;
            case '\n': os << "\\n";  break;
            case '\r': os << "\\r";  break;
            case '\t': os << "\\t";  break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x",
                                  static_cast<unsigned>(static_cast<unsigned char>(c)));
                    os << buf;
                } else {
                    os.put(c);
                }
                break;
        }
    }
    os.put('"');
}

}  // namespace

std::string WnLibraryStore::snapshot_json() const {
    std::lock_guard<std::mutex> lk(mu_);
    std::ostringstream os;
    os << "{\"packages\":[";
    bool first = true;
    for (const auto& [_, p] : packages_) {
        if (!first) os.put(',');
        first = false;
        os << "{\"id\":"            << p.package_id
           << ",\"flags\":"         << p.license_flags
           << ",\"license_type\":"  << p.license_type
           << ",\"change_number\":" << p.change_number
           << ",\"access_token\":\""<< p.access_token << "\"}";
    }
    os << "],\"owned_apps\":[";
    first = true;
    size_t owned_count = 0;
    for (const auto& [_, a] : apps_) {
        if (a.source_package_ids.empty()) continue;
        if (!first) os.put(',');
        first = false;
        ++owned_count;
        os << "{\"id\":" << a.app_id
           << ",\"change_number\":" << a.change_number
           << ",\"name\":";    write_json_string(os, a.name);
        os << ",\"type\":";    write_json_string(os, a.type);
        os << ",\"sort_as\":"; write_json_string(os, a.sort_as);
        os << ",\"os_list\":"; write_json_string(os, a.os_list);
        os << ",\"parent\":"   << a.parent_app_id
           << ",\"access_token\":\"" << a.access_token << "\""
           << ",\"dlc\":[";
        for (size_t i = 0; i < a.dlc_app_ids.size(); ++i) {
            if (i > 0) os.put(',');
            os << a.dlc_app_ids[i];
        }
        os << "],\"src_packages\":[";
        for (size_t i = 0; i < a.source_package_ids.size(); ++i) {
            if (i > 0) os.put(',');
            os << a.source_package_ids[i];
        }
        os << "]}";
    }
    os << "],\"all_apps_count\":" << apps_.size()
       << ",\"owned_apps_count\":" << owned_count
       << "}";
    return os.str();
}

void WnLibraryStore::set_observer(SnapshotObserver obs) {
    std::lock_guard<std::mutex> lk(obs_mu_);
    observer_ = std::move(obs);
}

void WnLibraryStore::notify_() {
    SnapshotObserver cb;
    { std::lock_guard<std::mutex> lk(obs_mu_); cb = observer_; }
    if (cb) cb();
}

}  // namespace wn_steam
