#pragma once

#include <cstdint>
#include <map>
#include <optional>
#include <string>

// Phase 5.5b — DepotConfigStore.
//
// Mirrors JavaSteam DepotDownloader's `.DepotDownloader/depot.config` — the
// per-install JSON file that records which manifest of each depot is fully
// installed on disk:
//
//   { "installedManifestIDs": { "<depotId>": <manifestId>, ... } }
//
// Strict semantics WinNative depends on (see the Phase 5 download rules):
//   * Before a depot's files are (re)written, its entry is set to
//     kInvalidManifestId — so an interrupted download cannot later be
//     mistaken for "installed".
//   * Only after every file of the depot is written + verified is the
//     entry restored to the real manifest id.
//   * The whole file is deleted when an install lacks the COMPLETE marker
//     (a stale config would let a resume skip validation) — the caller
//     does this via discard(); see SteamService's rule.
//
// Steam's depot manifest ids are uint64; JavaSteam uses Long.MAX_VALUE as
// the "not installed / in progress" sentinel.

namespace wn_steam {

class DepotConfigStore {
public:
    // JavaSteam DepotDownloader.INVALID_MANIFEST_ID == Long.MAX_VALUE.
    static constexpr uint64_t kInvalidManifestId = 0x7FFFFFFFFFFFFFFFull;

    // Load (or start empty) the depot.config for an install whose
    // DepotDownloader config dir is `config_dir` (…/.DepotDownloader).
    [[nodiscard]] static DepotConfigStore load(std::string config_dir);

    // installedManifestIDs[depot_id], or 0 when absent.
    [[nodiscard]] uint64_t installed_manifest(uint32_t depot_id) const;

    // True iff the depot is recorded as fully installed at exactly
    // `manifest_id` (and not the in-progress sentinel).
    [[nodiscard]] bool is_installed(uint32_t depot_id, uint64_t manifest_id) const;

    // Mark a depot as in-progress (sentinel) and persist immediately.
    [[nodiscard]] bool begin_depot(uint32_t depot_id);

    // Mark a depot fully installed at `manifest_id` and persist immediately.
    [[nodiscard]] bool finish_depot(uint32_t depot_id, uint64_t manifest_id);

    // Delete depot.config from disk and clear the in-memory map. Used when
    // starting a download that has no COMPLETE marker.
    void discard();

    // Absolute path of the cached manifest blob for a depot+manifest:
    //   <config_dir>/<depotId>_<manifestId>.manifest
    [[nodiscard]] std::string manifest_cache_path(uint32_t depot_id,
                                                  uint64_t manifest_id) const;

    [[nodiscard]] const std::string& config_dir() const noexcept { return config_dir_; }

private:
    explicit DepotConfigStore(std::string config_dir)
        : config_dir_(std::move(config_dir)) {}

    [[nodiscard]] bool save() const;
    [[nodiscard]] std::string config_path() const;

    std::string                       config_dir_;
    std::map<uint32_t, uint64_t>       installed_;  // depotId -> manifestId
};

}  // namespace wn_steam
