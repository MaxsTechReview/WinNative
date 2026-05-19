#pragma once

#include <chrono>
#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "wn_steam/pb/ccontentserverdirectory.h"

// Phase 5.3b — CDN HTTP client.
//
// Downloads depot content from a Steam CDN content server (one of the
// CContentServerDirectory_ServerInfo entries returned by
// CMClient::get_cdn_servers). The CDN serves manifests as a ZIP; this
// client fetches and unzips them so the raw bytes can be handed to
// ContentManifest::parse().
//
// URL form (verified against JavaSteam steam/cdn/Client.kt buildCommand):
//   <http|https>://<vhost|host>:<port>/depot/<depotId>/manifest/<gid>/5
//   ...with /<requestCode> appended when the code is non-zero, and the
//   cdn auth token appended as the URL query when present.
//
// Synchronous + blocking — call off the UI thread.

namespace wn_steam {

struct CdnManifestResult {
    std::vector<uint8_t> raw_manifest;   // unzipped; feed to ContentManifest::parse
    std::string          error;          // empty on success
    int                  http_status = 0;

    [[nodiscard]] bool ok() const noexcept {
        return error.empty() && !raw_manifest.empty();
    }
};

struct CdnChunkResult {
    std::vector<uint8_t> data;           // raw (still encrypted) chunk bytes
    std::string          error;          // empty on success
    int                  http_status = 0;

    [[nodiscard]] bool ok() const noexcept {
        return error.empty() && !data.empty();
    }
};

// A reusable CDN HTTP connection — wraps a persistent libcurl easy handle
// so a sequence of fetch_chunk() calls reuse one TCP + TLS connection
// (HTTP keep-alive) instead of paying a fresh DNS + handshake per chunk.
// That handshake cost otherwise dominates throughput: depot chunks are at
// most ~1 MiB, so a multi-GB game is tens of thousands of tiny GETs.
//
// NOT thread-safe — a single libcurl easy handle may only be used by one
// thread at a time. Each parallel download worker must own its own.
class CdnConnection {
public:
    CdnConnection();
    ~CdnConnection();

    CdnConnection(const CdnConnection&)            = delete;
    CdnConnection& operator=(const CdnConnection&) = delete;
    CdnConnection(CdnConnection&& other) noexcept;
    CdnConnection& operator=(CdnConnection&& other) noexcept;

    [[nodiscard]] bool valid() const noexcept { return handle_ != nullptr; }

private:
    friend class CdnClient;
    void* handle_;   // CURL* — void* keeps <curl/curl.h> out of this header
};

class CdnClient {
public:
    // ca_bundle_path: single-file PEM bundle for HTTPS verification (same
    // file SteamDirectoryClient uses, produced by CaBundleExtractor). May
    // be empty for plain-HTTP-only servers.
    explicit CdnClient(std::string ca_bundle_path);

    CdnClient(const CdnClient&)            = delete;
    CdnClient& operator=(const CdnClient&) = delete;

    // GET + unzip a depot manifest from `server`. request_code 0 ⇒ omit
    // the code path segment. cdn_auth_token, when set, is the URL query
    // string (e.g. "token=..."); appended verbatim after '?'.
    [[nodiscard]] CdnManifestResult fetch_manifest(
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, uint64_t manifest_id, uint64_t request_code,
        std::string_view cdn_auth_token = {},
        std::chrono::seconds timeout = std::chrono::seconds{30});

    // GET a single depot chunk. `chunk_sha` is the 20-byte chunk SHA1 from
    // the manifest (ContentManifest::ChunkData::sha); it is hex-encoded into
    // the URL: /depot/<depotId>/chunk/<sha-hex>. The returned bytes are
    // still AES-encrypted + compressed — feed them to process_depot_chunk().
    [[nodiscard]] CdnChunkResult fetch_chunk(
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, std::span<const uint8_t> chunk_sha,
        std::string_view cdn_auth_token = {},
        std::chrono::seconds timeout = std::chrono::seconds{30});

    // Same as fetch_chunk() above, but performed over `conn`'s persistent
    // keep-alive connection instead of a throwaway handle. Use this from
    // the parallel download workers — give each worker its own
    // CdnConnection. A single CdnConnection must not be shared across
    // threads concurrently.
    [[nodiscard]] CdnChunkResult fetch_chunk(
        CdnConnection& conn,
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, std::span<const uint8_t> chunk_sha,
        std::string_view cdn_auth_token = {},
        std::chrono::seconds timeout = std::chrono::seconds{30});

    // Extract the first entry of a ZIP archive (stored or deflate). The
    // Steam CDN wraps each manifest in a single-entry ZIP. Exposed for
    // testing / reuse. nullopt on a malformed archive.
    [[nodiscard]] static std::optional<std::vector<uint8_t>>
    unzip_first_entry(std::span<const uint8_t> zip) noexcept;

    // GET the Steam Inventory item-definition archive for `app_id`, using the
    // digest from Inventory.GetItemDefMeta. Hits the keyless web endpoint
    //   https://api.steampowered.com/IGameInventory/GetItemDefArchive/v1/
    // The body is the raw item-def JSON array; a trailing NUL byte, if
    // present, is stripped. nullopt on transport / non-200 failure; an empty
    // vector means the app exposes no item definitions.
    [[nodiscard]] std::optional<std::vector<uint8_t>> fetch_item_def_archive(
        uint32_t app_id, std::string_view digest,
        std::chrono::seconds timeout = std::chrono::seconds{30});

private:
    std::string ca_bundle_path_;
};

}  // namespace wn_steam
