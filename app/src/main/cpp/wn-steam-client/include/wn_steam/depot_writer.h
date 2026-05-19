#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <span>
#include <string>
#include <string_view>

#include "wn_steam/cdn_client.h"
#include "wn_steam/content_manifest.h"
#include "wn_steam/pb/ccontentserverdirectory.h"

// Phase 5.5a — depot file writer.
//
// Takes a fully parsed + filename-decrypted ContentManifest and materialises
// every file it describes under `target_dir`, downloading each chunk from
// the CDN, decrypting + decompressing + verifying it (process_depot_chunk),
// and writing it at the correct file offset.
//
// Files install FLAT into target_dir (no game-name subfolder) — this is one
// of WinNative's strict download rules; the steamapps/common symlink + ACF
// are produced later at launch time, not here.
//
// EDepotFileFlag bits (SteamKit2 EDepotFileFlag): Executable = 32,
// Directory = 64, Symlink = 512. A file whose `linktarget` is non-empty is
// a symlink regardless of the flag.

namespace wn_steam {

struct DepotWriteResult {
    uint64_t    files_written = 0;
    uint64_t    bytes_written = 0;   // sum of decompressed chunk bytes
    std::string error;               // empty on success

    [[nodiscard]] bool ok() const noexcept { return error.empty(); }
};

// done/total are decompressed byte counts; total = sum of every file size.
// `verifying` is true while bytes advance from on-disk validation (the verify
// hot path) and false while they advance from an actual CDN download — so the
// UI can show "Verifying" vs "Downloading" accurately.
using DepotWriteProgress =
    std::function<void(uint64_t done, uint64_t total, bool verifying)>;

// Write all files of `manifest` under `target_dir`. `manifest` must already
// have had decrypt_filenames() applied. `depot_key` is the 32-byte AES key.
// Chunks are fetched from `server` via `cdn`. Stops at the first hard error.
//
// `cancel` (optional) is polled before every file and before every chunk
// fetch; when it becomes true the write aborts promptly with a "cancelled"
// error so a paused / cancelled download stops the native worker.
//
// `max_workers` is the number of parallel chunk-download workers. Each
// worker owns its own keep-alive CdnConnection, so N workers means N
// concurrent HTTPS connections to the CDN. Clamped to [1, 64] and never
// exceeds the outstanding chunk count. This is what the user-facing
// "Download Speed" setting maps to (8 / 16 / 24 / 32).
[[nodiscard]] DepotWriteResult write_depot(
    const ContentManifest& manifest,
    std::span<const uint8_t> depot_key,
    CdnClient& cdn,
    const pb::CContentServerDirectory_ServerInfo& server,
    const std::string& target_dir,
    std::string_view cdn_auth_token = {},
    const DepotWriteProgress& progress = {},
    const std::atomic<bool>* cancel = nullptr,
    unsigned max_workers = 8);

}  // namespace wn_steam
