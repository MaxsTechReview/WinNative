#include "wn_steam/depot_config.h"

#include <android/log.h>
#include <nlohmann/json.hpp>

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

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

// fsync the directory that contains `path`, so a rename() into it is itself
// durable. Without this, a temp+rename can still be lost on a crash: the
// rename's directory-entry update lives only in the page cache.
void fsync_parent_dir(const std::string& path) {
    const size_t slash = path.rfind('/');
    const std::string dir =
        (slash == std::string::npos || slash == 0) ? std::string("/")
                                                   : path.substr(0, slash);
    int fd = ::open(dir.c_str(), O_RDONLY | O_DIRECTORY);
    if (fd >= 0) {
        ::fsync(fd);
        ::close(fd);
    }
}

// Atomically write `bytes` to `final_path`: write a sibling .tmp, fsync it,
// rename it into place, then fsync the directory. Returns false on any I/O
// error. Shared by depot.config and the depot progress sidecar.
bool atomic_write_synced(const std::string& final_path,
                         const std::string& bytes) {
    const std::string tmp_path = final_path + ".tmp";
    int fd = ::open(tmp_path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        WN_LOGE("atomic_write: open %s: %s", tmp_path.c_str(),
                std::strerror(errno));
        return false;
    }
    size_t off = 0;
    while (off < bytes.size()) {
        ssize_t w = ::write(fd, bytes.data() + off, bytes.size() - off);
        if (w <= 0) {
            WN_LOGE("atomic_write: write %s: %s", tmp_path.c_str(),
                    std::strerror(errno));
            ::close(fd);
            ::remove(tmp_path.c_str());
            return false;
        }
        off += static_cast<size_t>(w);
    }
    // Flush the file's data to stable storage BEFORE the rename — otherwise a
    // crash can rename a still-empty/partial temp file over the good one.
    if (::fsync(fd) != 0) {
        WN_LOGE("atomic_write: fsync %s: %s", tmp_path.c_str(),
                std::strerror(errno));
        ::close(fd);
        ::remove(tmp_path.c_str());
        return false;
    }
    ::close(fd);
    if (std::rename(tmp_path.c_str(), final_path.c_str()) != 0) {
        WN_LOGE("atomic_write: rename %s: %s", final_path.c_str(),
                std::strerror(errno));
        ::remove(tmp_path.c_str());
        return false;
    }
    fsync_parent_dir(final_path);
    return true;
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

    // Write atomically AND durably (temp file → fsync → rename → dir fsync),
    // so neither a crash mid-write nor an Android low-memory kill can leave a
    // truncated depot.config — a truncated/empty one parses as "nothing
    // installed" and forces a full re-verify of the whole game on resume.
    // 2-space pretty-print to match JavaSteam's prettyPrint=true output.
    return atomic_write_synced(config_path(), j.dump(2));
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

// ── DepotProgressStore ────────────────────────────────────────────────────

namespace {
// Sidecar binary header: 4-byte magic + uint32 version. Bumping the version
// makes an older build's sidecar parse as empty (safe — full re-verify).
constexpr char     kProgressMagic[4] = {'W', 'N', 'D', 'P'};
constexpr uint32_t kProgressVersion  = 1;

void put_u32(std::string& out, uint32_t v) {
    out.push_back(static_cast<char>(v & 0xFF));
    out.push_back(static_cast<char>((v >> 8) & 0xFF));
    out.push_back(static_cast<char>((v >> 16) & 0xFF));
    out.push_back(static_cast<char>((v >> 24) & 0xFF));
}
uint32_t get_u32(const unsigned char* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}
}  // namespace

std::string DepotProgressStore::sidecar_path(const std::string& config_dir,
                                             uint32_t depot_id,
                                             uint64_t manifest_id) {
    return config_dir + "/" + std::to_string(depot_id) + "_"
         + std::to_string(manifest_id) + ".progress";
}

DepotProgressStore::DepotProgressStore(const std::string& config_dir,
                                       uint32_t depot_id,
                                       uint64_t manifest_id)
    : path_(sidecar_path(config_dir, depot_id, manifest_id)) {
    std::ifstream in(path_, std::ios::binary | std::ios::ate);
    if (!in) return;   // absent — start empty
    const std::streamsize sz = in.tellg();
    // Header is 12 bytes (magic + version + count).
    if (sz < 12) return;
    in.seekg(0);
    std::vector<unsigned char> buf(static_cast<size_t>(sz));
    if (!in.read(reinterpret_cast<char*>(buf.data()), sz)) return;

    if (std::memcmp(buf.data(), kProgressMagic, 4) != 0) {
        WN_LOGE("progress sidecar %s: bad magic — ignoring", path_.c_str());
        return;
    }
    if (get_u32(buf.data() + 4) != kProgressVersion) return;
    const uint32_t count = get_u32(buf.data() + 8);
    // Reject a header that claims more entries than the file can hold.
    if (12u + static_cast<uint64_t>(count) * 4u != static_cast<uint64_t>(sz)) {
        WN_LOGE("progress sidecar %s: size mismatch — ignoring", path_.c_str());
        return;
    }
    for (uint32_t i = 0; i < count; ++i) {
        done_.insert(get_u32(buf.data() + 12 + static_cast<size_t>(i) * 4));
    }
    flushed_count_ = done_.size();
    WN_LOGI("progress sidecar loaded: %s — %zu file(s) done",
            path_.c_str(), done_.size());
}

bool DepotProgressStore::is_file_done(uint32_t file_index) const {
    std::lock_guard<std::mutex> lk(mtx_);
    return done_.find(file_index) != done_.end();
}

void DepotProgressStore::mark_file_done(uint32_t file_index) {
    std::lock_guard<std::mutex> lk(mtx_);
    done_.insert(file_index);
}

size_t DepotProgressStore::done_count() const {
    std::lock_guard<std::mutex> lk(mtx_);
    return done_.size();
}

bool DepotProgressStore::flush() const {
    std::lock_guard<std::mutex> lk(mtx_);
    if (done_.size() == flushed_count_) return true;   // nothing new
    std::string blob;
    blob.reserve(12 + done_.size() * 4);
    blob.append(kProgressMagic, 4);
    put_u32(blob, kProgressVersion);
    put_u32(blob, static_cast<uint32_t>(done_.size()));
    for (uint32_t idx : done_) put_u32(blob, idx);
    if (!atomic_write_synced(path_, blob)) return false;
    flushed_count_ = done_.size();
    return true;
}

void DepotProgressStore::discard() {
    std::lock_guard<std::mutex> lk(mtx_);
    done_.clear();
    flushed_count_ = 0;
    if (std::remove(path_.c_str()) != 0 && errno != ENOENT) {
        WN_LOGE("progress sidecar: remove failed: %s", std::strerror(errno));
    }
}

void DepotProgressStore::remove(const std::string& config_dir,
                                uint32_t depot_id, uint64_t manifest_id) {
    const std::string p = sidecar_path(config_dir, depot_id, manifest_id);
    if (std::remove(p.c_str()) != 0 && errno != ENOENT) {
        WN_LOGE("progress sidecar: remove failed: %s", std::strerror(errno));
    }
}

}  // namespace wn_steam
