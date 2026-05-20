#include "wn_steam/depot_downloader.h"

#include <android/log.h>
#include <sys/stat.h>

#include <chrono>
#include <fstream>
#include <future>
#include <optional>
#include <span>
#include <thread>
#include <vector>
#include <cstdio>

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

// Fetch a depot manifest, retrying transient failures with backoff and
// rotating across the CDN server list. A manifest GET that drops mid-stream
// or hits a flaky edge no longer fails the whole download on the first try.
CdnManifestResult fetch_manifest_with_retry(
        CdnClient& cdn,
        const std::vector<pb::CContentServerDirectory_ServerInfo>& servers,
        uint32_t depot_id, uint64_t manifest_id, uint64_t request_code,
        const std::atomic<bool>* cancel) {
    constexpr unsigned kAttempts = 5;
    CdnManifestResult last;
    for (unsigned attempt = 0; attempt < kAttempts; ++attempt) {
        if (cancel && cancel->load()) {
            last.error = "cancelled";
            return last;
        }
        if (attempt > 0) {
            unsigned ms = 300u << (attempt - 1);
            if (ms > 4000u) ms = 4000u;
            std::this_thread::sleep_for(std::chrono::milliseconds(ms));
        }
        const auto& srv = servers[attempt % servers.size()];
        last = cdn.fetch_manifest(srv, depot_id, manifest_id, request_code);
        if (last.ok()) return last;
        WN_LOGE("manifest fetch attempt %u/%u failed (depot %u): %s",
                attempt + 1, kAttempts, depot_id, last.error.c_str());
    }
    return last;
}

// Clean-pause marker — written when a depot was paused after a full A2
// validation, so the next resume can trust on-disk byte ranges without
// re-checksumming them. Distinct from the per-file DepotProgressStore
// sidecar: cleanpause says "the whole depot is in a known-good state",
// the sidecar says "these specific files are durable".
std::string clean_pause_marker_path(const std::string& config_dir,
                                    uint32_t depot_id,
                                    uint64_t manifest_id) {
    return config_dir + "/" + std::to_string(depot_id) + "_"
         + std::to_string(manifest_id) + ".cleanpause";
}

bool has_clean_pause_marker(const DepotConfigStore& cfg,
                            uint32_t depot_id,
                            uint64_t manifest_id) {
    std::ifstream in(clean_pause_marker_path(
        cfg.config_dir(), depot_id, manifest_id), std::ios::binary);
    return static_cast<bool>(in);
}

void write_clean_pause_marker(const DepotConfigStore& cfg,
                              uint32_t depot_id,
                              uint64_t manifest_id) {
    std::ofstream out(clean_pause_marker_path(
        cfg.config_dir(), depot_id, manifest_id), std::ios::binary | std::ios::trunc);
    if (out) out << manifest_id;
}

void remove_clean_pause_marker(const DepotConfigStore& cfg,
                               uint32_t depot_id,
                               uint64_t manifest_id) {
    std::remove(clean_pause_marker_path(
        cfg.config_dir(), depot_id, manifest_id).c_str());
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
    if (fresh) {
        cfg.discard();
        // A fresh (non-resume) download must not trust stale per-file resume
        // sidecars or clean-pause markers either — drop them so every file
        // is re-examined.
        for (const auto& d : depots) {
            DepotProgressStore::remove(config_dir, d.depot_id, d.manifest_id);
            remove_clean_pause_marker(cfg, d.depot_id, d.manifest_id);
        }
    }

    CdnClient cdn(ca_bundle_path_);

    // --- resolve the CDN content-server list once -----------------------
    // Keep ALL usable servers, not just the first: write_depot's chunk
    // retry and the manifest fetch fail over across this list, so one dead
    // or throttled CDN edge can't sink the whole download.
    std::vector<pb::CContentServerDirectory_ServerInfo> servers;
    {
        std::promise<std::optional<pb::CContentServerDirectory_GetServersForSteamPipe_Response>> p;
        auto fut = p.get_future();
        cm_.get_cdn_servers(0, [&p](auto resp) { p.set_value(std::move(resp)); });
        auto resp = fut.get();
        if (!resp || resp->servers.empty()) {
            return fail("download: no CDN servers available");
        }
        // Non-China servers only (download path is global content).
        for (auto& s : resp->servers) {
            if (!s.steam_china_only && !s.host.empty()) {
                servers.push_back(s);
            }
        }
        if (servers.empty()) return fail("download: no usable CDN server");
        WN_LOGI("cdn servers: %zu usable (primary %s, https=%d)",
                servers.size(),
                (servers[0].vhost.empty() ? servers[0].host
                                          : servers[0].vhost).c_str(),
                servers[0].use_https() ? 1 : 0);
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

        const bool trust_existing_chunks =
            !fresh && has_clean_pause_marker(cfg, d.depot_id, d.manifest_id);

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
            auto m = fetch_manifest_with_retry(cdn, servers, d.depot_id,
                                               d.manifest_id, request_code,
                                               cancel);
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
        // The per-file resume sidecar: lets a paused/interrupted depot resume
        // without re-hashing every file of the whole depot on the next run.
        const uint32_t depots_done = result.depots_completed;
        DepotProgressStore progress_store(config_dir, d.depot_id,
                                          d.manifest_id);
        auto write_res = write_depot(
            *manifest, depot_key, cdn, servers, install_dir, /*cdn_auth_token=*/{},
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
            cancel,
            max_workers,
            trust_existing_chunks,
            [&cfg, depot_id = d.depot_id, manifest_id = d.manifest_id]() {
                remove_clean_pause_marker(cfg, depot_id, manifest_id);
            },
            &progress_store);
        if (!write_res.ok()) {
            if (cancelled() && write_res.resume_trust_safe) {
                write_clean_pause_marker(cfg, d.depot_id, d.manifest_id);
            }
            return fail("download: depot " + std::to_string(d.depot_id)
                        + " write failed: " + write_res.error);
        }

        // depot.config: mark fully installed only AFTER every file landed.
        if (!cfg.finish_depot(d.depot_id, d.manifest_id)) {
            return fail("download: depot.config finish failed for depot "
                        + std::to_string(d.depot_id));
        }
        // The depot is now recorded fully installed in depot.config — both
        // the per-file sidecar and the clean-pause marker are redundant.
        progress_store.discard();
        remove_clean_pause_marker(cfg, d.depot_id, d.manifest_id);
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
