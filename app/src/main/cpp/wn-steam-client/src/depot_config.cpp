#include "wn_steam/depot_config.h"

#include <android/log.h>
#include <nlohmann/json.hpp>

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sys/stat.h>

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamDepotCfg";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

// mkdir -p of a single path (config_dir is one level: …/.DepotDownloader).
void ensure_dir(const std::string& dir) {
    if (dir.empty()) return;
    if (::mkdir(dir.c_str(), 0755) != 0 && errno != EEXIST) {
        WN_LOGE("mkdir(%s): %s", dir.c_str(), std::strerror(errno));
    }
}
}  // namespace

std::string DepotConfigStore::config_path() const {
    return config_dir_ + "/depot.config";
}

std::string DepotConfigStore::manifest_cache_path(uint32_t depot_id,
                                                  uint64_t manifest_id) const {
    return config_dir_ + "/" + std::to_string(depot_id) + "_"
         + std::to_string(manifest_id) + ".manifest";
}

DepotConfigStore DepotConfigStore::load(std::string config_dir) {
    DepotConfigStore store(std::move(config_dir));
    std::ifstream in(store.config_path(), std::ios::binary);
    if (!in) return store;   // absent — start empty

    try {
        nlohmann::json j;
        in >> j;
        if (j.contains("installedManifestIDs") && j["installedManifestIDs"].is_object()) {
            for (auto it = j["installedManifestIDs"].begin();
                 it != j["installedManifestIDs"].end(); ++it) {
                // depot id is the (string) JSON key; manifest id the value.
                uint32_t depot = static_cast<uint32_t>(std::stoul(it.key()));
                store.installed_[depot] = it.value().get<uint64_t>();
            }
        }
    } catch (const std::exception& e) {
        WN_LOGE("depot.config parse failed (%s) — starting empty", e.what());
        store.installed_.clear();
    }
    return store;
}

bool DepotConfigStore::save() const {
    ensure_dir(config_dir_);
    nlohmann::json j;
    nlohmann::json ids = nlohmann::json::object();
    for (const auto& [depot, manifest] : installed_) {
        ids[std::to_string(depot)] = manifest;
    }
    j["installedManifestIDs"] = std::move(ids);

    // Write to a temp file then rename, so a crash mid-write can't leave a
    // truncated depot.config.
    const std::string final_path = config_path();
    const std::string tmp_path   = final_path + ".tmp";
    {
        std::ofstream out(tmp_path, std::ios::binary | std::ios::trunc);
        if (!out) {
            WN_LOGE("depot.config: cannot open %s", tmp_path.c_str());
            return false;
        }
        // 2-space pretty-print to match JavaSteam's prettyPrint=true output.
        out << j.dump(2);
        if (!out) {
            WN_LOGE("depot.config: write failed");
            return false;
        }
    }
    if (std::rename(tmp_path.c_str(), final_path.c_str()) != 0) {
        WN_LOGE("depot.config: rename failed: %s", std::strerror(errno));
        std::remove(tmp_path.c_str());
        return false;
    }
    return true;
}

uint64_t DepotConfigStore::installed_manifest(uint32_t depot_id) const {
    auto it = installed_.find(depot_id);
    return it != installed_.end() ? it->second : 0;
}

bool DepotConfigStore::is_installed(uint32_t depot_id, uint64_t manifest_id) const {
    auto it = installed_.find(depot_id);
    return it != installed_.end()
        && it->second == manifest_id
        && it->second != kInvalidManifestId;
}

bool DepotConfigStore::begin_depot(uint32_t depot_id) {
    installed_[depot_id] = kInvalidManifestId;
    return save();
}

bool DepotConfigStore::finish_depot(uint32_t depot_id, uint64_t manifest_id) {
    installed_[depot_id] = manifest_id;
    if (!save()) return false;
    WN_LOGI("depot %u marked installed at manifest %llu",
            depot_id, static_cast<unsigned long long>(manifest_id));
    return true;
}

void DepotConfigStore::discard() {
    installed_.clear();
    if (std::remove(config_path().c_str()) != 0 && errno != ENOENT) {
        WN_LOGE("depot.config: remove failed: %s", std::strerror(errno));
    }
}

}  // namespace wn_steam
