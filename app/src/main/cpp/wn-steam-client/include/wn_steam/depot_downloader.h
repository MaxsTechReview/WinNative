#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace wn_steam {

class CMClient;

// Phase 5.5b — depot download orchestrator.
//
// Ties the whole Phase 5 pipeline together. For each depot of an app it
// chains:
//   CMClient::get_depot_decryption_key   (depot AES key)
//   CMClient::get_manifest_request_code  (CDN manifest URL code)
//   CMClient::get_cdn_servers            (content server list, once)
//   CdnClient::fetch_manifest            (HTTP GET + unzip)
//   ContentManifest::parse / decrypt_filenames
//   write_depot                          (chunks -> files on disk)
// and records progress via DepotConfigStore (.DepotDownloader/depot.config).
//
// The CMClient calls are asynchronous; download() bridges them to a blocking
// sequence with std::promise/std::future, so it MUST run on a worker thread,
// never on the CM network thread.

struct DepotSpec {
    uint32_t depot_id    = 0;
    uint64_t manifest_id = 0;   // the manifest "gid" for the target branch
};

struct DepotDownloadProgress {
    uint32_t depot_id     = 0;
    uint64_t depot_done   = 0;  // decompressed bytes written for this depot
    uint64_t depot_total  = 0;  // total file bytes of this depot
    uint32_t depots_done  = 0;  // depots fully completed so far
    uint32_t depots_total = 0;
    bool     verifying    = false;  // true while validating on-disk content,
                                    // false while downloading from the CDN
};

struct DepotDownloadResult {
    bool        success          = false;
    std::string error;
    uint64_t    bytes_written    = 0;
    uint32_t    depots_completed = 0;
    uint32_t    depots_skipped   = 0;  // already-installed (resume)
};

using DepotProgressCallback = std::function<void(const DepotDownloadProgress&)>;

class DepotDownloader {
public:
    // `ca_bundle_path` is the PEM bundle for HTTPS CDN verification.
    DepotDownloader(CMClient& cm, std::string ca_bundle_path);

    DepotDownloader(const DepotDownloader&)            = delete;
    DepotDownloader& operator=(const DepotDownloader&) = delete;

    // Synchronous + blocking. Downloads every depot in `depots` for
    // `app_id` into `install_dir` (flat layout). `branch` selects the
    // manifest request-code branch ("public" for the default).
    //
    // `fresh` = true when the install has no COMPLETE marker: depot.config
    // is discarded so a stale entry can't make a resume skip validation.
    // When false, depots already recorded installed in depot.config are
    // skipped (resume).
    //
    // `cancel` (optional) is polled before each depot and before each chunk
    // fetch; setting it true aborts the download promptly (used for
    // pause / cancel from the Kotlin side).
    //
    // `max_workers` is the parallel chunk-download worker count, forwarded
    // to write_depot. It maps to the user's "Download Speed" setting
    // (8 / 16 / 24 / 32); write_depot clamps it to [1, 64].
    [[nodiscard]] DepotDownloadResult download(
        uint32_t app_id,
        std::vector<DepotSpec> depots,
        std::string branch,
        std::string install_dir,
        bool fresh,
        DepotProgressCallback progress = {},
        const std::atomic<bool>* cancel = nullptr,
        unsigned max_workers = 8);

private:
    CMClient&   cm_;
    std::string ca_bundle_path_;
};

}  // namespace wn_steam
