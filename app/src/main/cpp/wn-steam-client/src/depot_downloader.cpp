#include "wn_steam/depot_downloader.h"

#include <android/log.h>
#include <sys/stat.h>

#include <fstream>
#include <future>
#include <optional>
#include <span>

#include "wn_steam/cdn_client.h"
#include "wn_steam/cm_client.h"
#include "wn_steam/content_manifest.h"
#include "wn_steam/depot_config.h"
#include "wn_steam/depot_writer.h"

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamDepotDL";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

DepotDownloadResult fail(std::string msg) {
    DepotDownloadResult r;
    r.success = false;
    r.error   = std::move(msg);
    WN_LOGE("%s", r.error.c_str());
    return r;
}

// Read a whole file into memory; nullopt if it can't be opened.
std::optional<std::vector<uint8_t>> read_file(const std::string& path) {
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in) return std::nullopt;
    const std::streamsize sz = in.tellg();
    if (sz < 0) return std::nullopt;
    in.seekg(0);
    std::vector<uint8_t> buf(static_cast<size_t>(sz));
    if (sz > 0 && !in.read(reinterpret_cast<char*>(buf.data()), sz)) {
        return std::nullopt;
    }
    return buf;
}

bool write_file(const std::string& path, std::span<const uint8_t> data) {
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) return false;
    if (!data.empty()) {
        out.write(reinterpret_cast<const char*>(data.data()),
                  static_cast<std::streamsize>(data.size()));
    }
    return static_cast<bool>(out);
}
}  // namespace

DepotDownloader::DepotDownloader(CMClient& cm, std::string ca_bundle_path)
    : cm_(cm), ca_bundle_path_(std::move(ca_bundle_path)) {}

DepotDownloadResult DepotDownloader::download(uint32_t app_id,
                                             std::vector<DepotSpec> depots,
                                             std::string branch,
                                             std::string install_dir,
                                             bool fresh,
                                             DepotProgressCallback progress,
                                             const std::atomic<bool>* cancel,
                                             unsigned max_workers) {
    if (install_dir.empty()) return fail("download: empty install dir");
    if (depots.empty())      return fail("download: no depots");

    const auto cancelled = [cancel]() { return cancel && cancel->load(); };

    const std::string config_dir = install_dir + "/.DepotDownloader";
    ::mkdir(install_dir.c_str(), 0755);
    ::mkdir(config_dir.c_str(), 0755);

    // Strict rule: a fresh download (no COMPLETE marker) must not trust a
    // stale depot.config — discard it so every depot is re-validated.
    DepotConfigStore cfg = DepotConfigStore::load(config_dir);
    if (fresh) cfg.discard();

    CdnClient cdn(ca_bundle_path_);

    // --- resolve the CDN content-server list once -----------------------
    pb::CContentServerDirectory_ServerInfo server;
    {
        std::promise<std::optional<pb::CContentServerDirectory_GetServersForSteamPipe_Response>> p;
        auto fut = p.get_future();
        cm_.get_cdn_servers(0, [&p](auto resp) { p.set_value(std::move(resp)); });
        auto resp = fut.get();
        if (!resp || resp->servers.empty()) {
            return fail("download: no CDN servers available");
        }
        // First non-China server (download path is global content).
        bool picked = false;
        for (auto& s : resp->servers) {
            if (!s.steam_china_only && !s.host.empty()) {
                server = s;
                picked = true;
                break;
            }
        }
        if (!picked) return fail("download: no usable CDN server");
        WN_LOGI("cdn server: %s (https=%d)",
                (server.vhost.empty() ? server.host : server.vhost).c_str(),
                server.use_https() ? 1 : 0);
    }

    DepotDownloadResult result;
    result.success = true;
    const uint32_t depots_total = static_cast<uint32_t>(depots.size());

    for (const auto& d : depots) {
        if (cancelled()) return fail("download: cancelled");
        // Resume: skip a depot already recorded installed at this manifest.
        if (!fresh && cfg.is_installed(d.depot_id, d.manifest_id)) {
            WN_LOGI("depot %u already installed at manifest %llu — skipping",
                    d.depot_id, static_cast<unsigned long long>(d.manifest_id));
            ++result.depots_skipped;
            continue;
        }

        // depot.config: mark in-progress BEFORE any file is written.
        if (!cfg.begin_depot(d.depot_id)) {
            return fail("download: depot.config begin failed for depot "
                        + std::to_string(d.depot_id));
        }

        // --- depot decryption key ---------------------------------------
        std::vector<uint8_t> depot_key;
        {
            std::promise<std::optional<pb::CMsgClientGetDepotDecryptionKeyResponse>> p;
            auto fut = p.get_future();
            cm_.get_depot_decryption_key(d.depot_id, app_id,
                                         [&p](auto r) { p.set_value(std::move(r)); });
            auto r = fut.get();
            if (!r || r->eresult != 1 || r->depot_encryption_key.size() != 32) {
                return fail("download: depot key unavailable for depot "
                            + std::to_string(d.depot_id));
            }
            depot_key = std::move(r->depot_encryption_key);
        }

        // --- manifest: cache, else fetch from the CDN -------------------
        const std::string cache_path =
            cfg.manifest_cache_path(d.depot_id, d.manifest_id);
        std::vector<uint8_t> raw_manifest;
        if (auto cached = read_file(cache_path); cached && !cached->empty()) {
            raw_manifest = std::move(*cached);
        } else {
            uint64_t request_code = 0;
            {
                std::promise<std::optional<
                    pb::CContentServerDirectory_GetManifestRequestCode_Response>> p;
                auto fut = p.get_future();
                cm_.get_manifest_request_code(app_id, d.depot_id, d.manifest_id,
                                              branch,
                                              [&p](auto r) { p.set_value(std::move(r)); });
                auto r = fut.get();
                if (r) request_code = r->manifest_request_code;
                // A zero code is legal; fetch_manifest then omits the segment.
            }
            auto m = cdn.fetch_manifest(server, d.depot_id, d.manifest_id,
                                        request_code);
            if (!m.ok()) {
                return fail("download: manifest fetch failed for depot "
                            + std::to_string(d.depot_id) + ": " + m.error);
            }
            raw_manifest = std::move(m.raw_manifest);
            if (!write_file(cache_path, raw_manifest)) {
                WN_LOGE("depot %u: manifest cache write failed (non-fatal)",
                        d.depot_id);
            }
        }

        // --- parse + decrypt --------------------------------------------
        auto manifest = ContentManifest::parse(raw_manifest);
        if (!manifest) {
            return fail("download: manifest parse failed for depot "
                        + std::to_string(d.depot_id));
        }
        if (!manifest->decrypt_filenames(depot_key)) {
            return fail("download: filename decryption failed for depot "
                        + std::to_string(d.depot_id));
        }

        // --- write files ------------------------------------------------
        const uint32_t depots_done = result.depots_completed;
        auto write_res = write_depot(
            *manifest, depot_key, cdn, server, install_dir, /*cdn_auth_token=*/{},
            [&](uint64_t done, uint64_t total, bool verifying) {
                if (progress) {
                    DepotDownloadProgress pr;
                    pr.depot_id     = d.depot_id;
                    pr.depot_done   = done;
                    pr.depot_total  = total;
                    pr.depots_done  = depots_done;
                    pr.depots_total = depots_total;
                    pr.verifying    = verifying;
                    progress(pr);
                }
            },
            cancel, max_workers);
        if (!write_res.ok()) {
            return fail("download: depot " + std::to_string(d.depot_id)
                        + " write failed: " + write_res.error);
        }

        // depot.config: mark fully installed only AFTER every file landed.
        if (!cfg.finish_depot(d.depot_id, d.manifest_id)) {
            return fail("download: depot.config finish failed for depot "
                        + std::to_string(d.depot_id));
        }
        result.bytes_written += write_res.bytes_written;
        ++result.depots_completed;
        WN_LOGI("depot %u complete (%llu bytes); %u/%u depots done",
                d.depot_id,
                static_cast<unsigned long long>(write_res.bytes_written),
                result.depots_completed + result.depots_skipped, depots_total);
    }

    WN_LOGI("download complete: app %u — %u depot(s) written, %u skipped, %llu bytes",
            app_id, result.depots_completed, result.depots_skipped,
            static_cast<unsigned long long>(result.bytes_written));
    return result;
}

}  // namespace wn_steam
