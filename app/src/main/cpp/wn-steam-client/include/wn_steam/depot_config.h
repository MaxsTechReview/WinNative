#pragma once

#include <cstdint>
#include <map>
#include <mutex>
#include <optional>
#include <set>
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

// Phase 9 — DepotProgressStore (per-depot resume progress).
//
// depot.config only records *whole-depot* completion, so a download paused
// (or killed) mid-depot has no finer-grained record: on resume write_depot
// re-reads and re-hashes EVERY chunk of EVERY file of that depot — the
// "verifying files" pass — which is O(full install size) every resume and
// is what makes large games appear stuck on "Verifying".
//
// DepotProgressStore records, per (depotId, manifestId), the set of manifest
// file indices whose bytes are fully written AND fsync'd to disk. A resumed
// write_depot can then skip a completed file outright instead of re-hashing
// it. Stored next to depot.config as:
//
//   <config_dir>/<depotId>_<manifestId>.progress
//
// in a compact binary form (magic, version, count, then count×uint32). A
// missing or corrupt file simply means "nothing known done" — write_depot
// then falls back to full on-disk validation. So this is always safe: at
// worst it costs the old behaviour, it can never cause data loss.
//
// File indices are stable for a given manifest: ContentManifest::parse()
// yields manifest.files in a deterministic order from the cached manifest
// blob, and the sidecar is keyed by manifest id, so the index recorded on
// one run refers to the same file on the next.
//
// mark_file_done() is thread-safe (the download workers call it
// concurrently); flush() serialises and persists durably.
class DepotProgressStore {
public:
    // Construct + load the sidecar for (depot_id, manifest_id). A missing or
    // unreadable file yields an empty (but usable) store.
    DepotProgressStore(const std::string& config_dir,
                       uint32_t depot_id, uint64_t manifest_id);

    DepotProgressStore(const DepotProgressStore&)            = delete;
    DepotProgressStore& operator=(const DepotProgressStore&) = delete;

    // True iff `file_index` was recorded fully written on a previous run.
    [[nodiscard]] bool is_file_done(uint32_t file_index) const;

    // Record a file as fully written. In-memory only — call flush() to
    // persist. Safe to call from multiple threads.
    void mark_file_done(uint32_t file_index);

    [[nodiscard]] size_t done_count() const;

    // Atomically persist the current set (temp file + fsync + rename + dir
    // fsync). Cheap to call repeatedly; a no-op when nothing changed since
    // the last flush.
    bool flush() const;

    // Delete the sidecar from disk + clear memory. Called once the depot is
    // recorded fully installed in depot.config (the sidecar is then stale).
    void discard();

    // Delete a sidecar without constructing a live store — used to clear a
    // stale sidecar when a fresh (non-resume) download starts.
    static void remove(const std::string& config_dir,
                        uint32_t depot_id, uint64_t manifest_id);

private:
    static std::string sidecar_path(const std::string& config_dir,
                                    uint32_t depot_id, uint64_t manifest_id);

    std::string             path_;
    mutable std::mutex      mtx_;
    std::set<uint32_t>      done_;
    // done_'s size at the last successful flush() — lets flush() skip the
    // write when nothing changed.
    mutable size_t          flushed_count_ = 0;
};

}  // namespace wn_steam
