#include "wn_steam/cdn_client.h"

#include <android/log.h>
#include <curl/curl.h>
#include <zlib.h>

#include <algorithm>

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamCdn";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

// "Valve/Steam HTTP Client 1.0" — matches what the official client sends;
// some CDN edges are picky about a missing/odd User-Agent.
constexpr const char* kUserAgent = "Valve/Steam HTTP Client 1.0";

size_t curl_write_cb(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* body = static_cast<std::vector<uint8_t>*>(userdata);
    const size_t n = size * nmemb;
    body->insert(body->end(), ptr, ptr + n);
    return n;
}

// Little-endian readers over a ZIP local file header.
uint16_t rd_u16(std::span<const uint8_t> b, size_t off) {
    return static_cast<uint16_t>(b[off]) |
           (static_cast<uint16_t>(b[off + 1]) << 8);
}
uint32_t rd_u32(std::span<const uint8_t> b, size_t off) {
    return static_cast<uint32_t>(b[off]) |
           (static_cast<uint32_t>(b[off + 1]) << 8) |
           (static_cast<uint32_t>(b[off + 2]) << 16) |
           (static_cast<uint32_t>(b[off + 3]) << 24);
}

// Raw-deflate inflate (ZIP entries carry no zlib/gzip wrapper).
std::optional<std::vector<uint8_t>> raw_inflate(std::span<const uint8_t> in,
                                                size_t expected) {
    std::vector<uint8_t> out;
    out.resize(expected > 0 ? expected : std::max<size_t>(in.size() * 4, 1024));

    z_stream zs{};
    if (inflateInit2(&zs, -15) != Z_OK) {   // -15 = raw deflate
        WN_LOGE("inflateInit2 failed");
        return std::nullopt;
    }
    zs.next_in   = const_cast<Bytef*>(in.data());
    zs.avail_in  = static_cast<uInt>(in.size());
    zs.next_out  = out.data();
    zs.avail_out = static_cast<uInt>(out.size());

    while (true) {
        int ret = inflate(&zs, Z_NO_FLUSH);
        if (ret == Z_STREAM_END) break;
        if (ret != Z_OK) {
            WN_LOGE("inflate rc=%d", ret);
            inflateEnd(&zs);
            return std::nullopt;
        }
        if (zs.avail_out == 0) {
            const size_t old = out.size();
            out.resize(old * 2);
            zs.next_out  = out.data() + old;
            zs.avail_out = static_cast<uInt>(out.size() - old);
        }
    }
    out.resize(zs.total_out);
    inflateEnd(&zs);
    return out;
}

}  // namespace

CdnClient::CdnClient(std::string ca_bundle_path)
    : ca_bundle_path_(std::move(ca_bundle_path)) {
    curl_global_init(CURL_GLOBAL_DEFAULT);   // idempotent; cleanup is shared-process
}

std::optional<std::vector<uint8_t>>
CdnClient::unzip_first_entry(std::span<const uint8_t> zip) noexcept {
    // ZIP local file header: 30 fixed bytes, signature 0x04034b50.
    if (zip.size() < 30) return std::nullopt;
    if (rd_u32(zip, 0) != 0x04034b50u) return std::nullopt;

    const uint16_t flags       = rd_u16(zip, 6);
    const uint16_t method      = rd_u16(zip, 8);
    const uint32_t comp_size   = rd_u32(zip, 18);
    const uint32_t uncomp_size = rd_u32(zip, 22);
    const uint16_t name_len    = rd_u16(zip, 26);
    const uint16_t extra_len   = rd_u16(zip, 28);

    // Bit 3 = sizes live in a trailing data descriptor, not the header.
    // Steam's server-generated manifest zips never use this; reject rather
    // than silently mis-read.
    if (flags & 0x08) {
        WN_LOGE("zip: streaming data-descriptor entries unsupported");
        return std::nullopt;
    }

    const size_t data_off = 30u + name_len + extra_len;
    if (data_off + comp_size > zip.size()) {
        WN_LOGE("zip: entry data overruns archive");
        return std::nullopt;
    }
    std::span<const uint8_t> data = zip.subspan(data_off, comp_size);

    if (method == 0) {                       // stored
        return std::vector<uint8_t>(data.begin(), data.end());
    }
    if (method == 8) {                       // deflate
        return raw_inflate(data, uncomp_size);
    }
    WN_LOGE("zip: unsupported compression method %u", method);
    return std::nullopt;
}

CdnManifestResult CdnClient::fetch_manifest(
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, uint64_t manifest_id, uint64_t request_code,
        std::string_view cdn_auth_token, std::chrono::seconds timeout) {
    CdnManifestResult result;

    const std::string& host = !server.vhost.empty() ? server.vhost : server.host;
    if (host.empty()) {
        result.error = "cdn server has no host";
        return result;
    }

    // <scheme>://<host>:<port>/depot/<id>/manifest/<gid>/5[/<code>][?<token>]
    std::string url;
    url.reserve(160);
    url += server.use_https() ? "https://" : "http://";
    url += host;
    url += ':';
    url += std::to_string(server.port());
    url += "/depot/";
    url += std::to_string(depot_id);
    url += "/manifest/";
    url += std::to_string(manifest_id);
    url += "/5";
    if (request_code != 0) {
        url += '/';
        url += std::to_string(request_code);
    }
    if (!cdn_auth_token.empty()) {
        url += '?';
        // token may already start with '?'; trim a leading one.
        url.append(cdn_auth_token.front() == '?' ? cdn_auth_token.substr(1)
                                                  : cdn_auth_token);
    }

    CURL* curl = curl_easy_init();
    if (!curl) {
        result.error = "curl_easy_init failed";
        return result;
    }

    std::vector<uint8_t> body;
    curl_easy_setopt(curl, CURLOPT_URL,            url.c_str());
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION,  curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA,      &body);
    curl_easy_setopt(curl, CURLOPT_USERAGENT,      kUserAgent);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT,        static_cast<long>(timeout.count()));
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 15L);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS,     1L);
    if (server.use_https()) {
        if (!ca_bundle_path_.empty()) {
            curl_easy_setopt(curl, CURLOPT_CAINFO, ca_bundle_path_.c_str());
        }
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    }

    const CURLcode rc = curl_easy_perform(curl);
    long http_status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_status);
    curl_off_t content_length = -1;
    curl_easy_getinfo(curl, CURLINFO_CONTENT_LENGTH_DOWNLOAD_T, &content_length);
    result.http_status = static_cast<int>(http_status);
    curl_easy_cleanup(curl);

    if (rc != CURLE_OK) {
        result.error = std::string("curl_easy_perform: ") + curl_easy_strerror(rc);
        WN_LOGE("manifest fetch failed: %s (host=%s)", result.error.c_str(), host.c_str());
        return result;
    }
    if (http_status != 200) {
        result.error = "non-200 HTTP status";
        WN_LOGE("manifest fetch HTTP %ld depot=%u gid=%llu host=%s",
                http_status, depot_id,
                static_cast<unsigned long long>(manifest_id), host.c_str());
        return result;
    }
    // Reject a truncated body: when the server declared a Content-Length the
    // received byte count must match it exactly. A connection dropped
    // mid-transfer can still return CURLE_OK with a short body, which would
    // otherwise hand a partial manifest to ContentManifest::parse.
    if (content_length >= 0 &&
        static_cast<curl_off_t>(body.size()) != content_length) {
        result.error = "manifest body truncated (length mismatch)";
        WN_LOGE("manifest length mismatch depot=%u: got %zu expected %lld",
                depot_id, body.size(), static_cast<long long>(content_length));
        return result;
    }

    auto unzipped = unzip_first_entry(body);
    if (!unzipped) {
        result.error = "manifest unzip failed";
        WN_LOGE("manifest unzip failed depot=%u gid=%llu (%zu bytes)",
                depot_id, static_cast<unsigned long long>(manifest_id), body.size());
        return result;
    }
    result.raw_manifest = std::move(*unzipped);
    WN_LOGI("manifest fetched: depot=%u gid=%llu %zu bytes (unzipped)",
            depot_id, static_cast<unsigned long long>(manifest_id),
            result.raw_manifest.size());
    return result;
}

std::optional<std::vector<uint8_t>>
CdnClient::fetch_item_def_archive(uint32_t app_id, std::string_view digest,
                                  std::chrono::seconds timeout) {
    CURL* curl = curl_easy_init();
    if (!curl) {
        WN_LOGE("itemdef archive: curl_easy_init failed");
        return std::nullopt;
    }

    // URL-encode the digest defensively (it is a hash string, but be safe).
    char* esc = curl_easy_escape(curl, digest.data(),
                                 static_cast<int>(digest.size()));
    std::string url =
        "https://api.steampowered.com/IGameInventory/GetItemDefArchive/v1/"
        "?appid=" + std::to_string(app_id) +
        "&digest=" + (esc ? std::string(esc) : std::string(digest));
    if (esc) curl_free(esc);

    std::vector<uint8_t> body;
    curl_easy_setopt(curl, CURLOPT_URL,             url.c_str());
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION,   curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA,       &body);
    curl_easy_setopt(curl, CURLOPT_USERAGENT,       kUserAgent);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT,         static_cast<long>(timeout.count()));
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT,  15L);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION,  1L);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS,      1L);
    curl_easy_setopt(curl, CURLOPT_ACCEPT_ENCODING, "");  // transparent gzip
    if (!ca_bundle_path_.empty()) {
        curl_easy_setopt(curl, CURLOPT_CAINFO, ca_bundle_path_.c_str());
    }
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);

    const CURLcode rc = curl_easy_perform(curl);
    long http_status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_status);
    curl_easy_cleanup(curl);

    if (rc != CURLE_OK) {
        WN_LOGE("itemdef archive: curl_easy_perform: %s", curl_easy_strerror(rc));
        return std::nullopt;
    }
    if (http_status != 200) {
        WN_LOGE("itemdef archive: HTTP %ld for app %u", http_status, app_id);
        return std::nullopt;
    }
    // The archive body carries a trailing NUL that breaks JSON parsing.
    if (!body.empty() && body.back() == 0) body.pop_back();
    WN_LOGI("itemdef archive: app %u — %zu bytes", app_id, body.size());
    return body;
}

// ── chunk-fetch helpers (shared by the throwaway + keep-alive paths) ──────
namespace {

// Lowercase hex-encode a byte span (the chunk SHA1 for the CDN URL path).
std::string hex_encode(std::span<const uint8_t> bytes) {
    static constexpr char kHex[] = "0123456789abcdef";
    std::string out;
    out.reserve(bytes.size() * 2);
    for (uint8_t b : bytes) {
        out.push_back(kHex[b >> 4]);
        out.push_back(kHex[b & 0x0F]);
    }
    return out;
}

// <scheme>://<host>:<port>/depot/<id>/chunk/<sha-hex>[?<token>]
std::string build_chunk_url(const pb::CContentServerDirectory_ServerInfo& server,
                            const std::string& host, uint32_t depot_id,
                            const std::string& sha_hex,
                            std::string_view cdn_auth_token) {
    std::string url;
    url.reserve(128);
    url += server.use_https() ? "https://" : "http://";
    url += host;
    url += ':';
    url += std::to_string(server.port());
    url += "/depot/";
    url += std::to_string(depot_id);
    url += "/chunk/";
    url += sha_hex;
    if (!cdn_auth_token.empty()) {
        url += '?';
        url.append(cdn_auth_token.front() == '?' ? cdn_auth_token.substr(1)
                                                  : cdn_auth_token);
    }
    return url;
}

// Configure `curl` for a chunk GET and perform it. The handle is NOT reset
// afterwards, so a persistent handle keeps its keep-alive connection cache
// for the next call — that is the whole point of CdnConnection.
CdnChunkResult do_fetch_chunk(CURL* curl, const std::string& ca_bundle,
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, std::span<const uint8_t> chunk_sha,
        std::string_view cdn_auth_token, std::chrono::seconds timeout) {
    CdnChunkResult result;
    if (!curl) { result.error = "curl handle null"; return result; }

    const std::string& host = !server.vhost.empty() ? server.vhost : server.host;
    if (host.empty())      { result.error = "cdn server has no host"; return result; }
    if (chunk_sha.empty()) { result.error = "empty chunk sha";        return result; }

    const std::string sha_hex = hex_encode(chunk_sha);
    const std::string url =
        build_chunk_url(server, host, depot_id, sha_hex, cdn_auth_token);

    std::vector<uint8_t> body;
    curl_easy_setopt(curl, CURLOPT_URL,            url.c_str());
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION,  curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA,      &body);
    curl_easy_setopt(curl, CURLOPT_USERAGENT,      kUserAgent);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT,        static_cast<long>(timeout.count()));
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 15L);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS,     1L);
    if (server.use_https()) {
        if (!ca_bundle.empty()) {
            curl_easy_setopt(curl, CURLOPT_CAINFO, ca_bundle.c_str());
        }
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    }

    const CURLcode rc = curl_easy_perform(curl);
    long http_status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_status);
    curl_off_t content_length = -1;
    curl_easy_getinfo(curl, CURLINFO_CONTENT_LENGTH_DOWNLOAD_T, &content_length);
    result.http_status = static_cast<int>(http_status);

    if (rc != CURLE_OK) {
        result.error = std::string("curl_easy_perform: ") + curl_easy_strerror(rc);
        WN_LOGE("chunk fetch failed: %s (host=%s)", result.error.c_str(), host.c_str());
        return result;
    }
    if (http_status != 200) {
        result.error = "non-200 HTTP status";
        WN_LOGE("chunk fetch HTTP %ld depot=%u chunk=%s", http_status, depot_id,
                sha_hex.c_str());
        return result;
    }
    // Reject a truncated body before it reaches the chunk decryptor — a short
    // body that still returned CURLE_OK must not be treated as a valid chunk.
    // (The Adler-32 check downstream also catches this; this fails faster.)
    if (content_length >= 0 &&
        static_cast<curl_off_t>(body.size()) != content_length) {
        result.error = "chunk body truncated (length mismatch)";
        WN_LOGE("chunk length mismatch depot=%u chunk=%s: got %zu expected %lld",
                depot_id, sha_hex.c_str(), body.size(),
                static_cast<long long>(content_length));
        return result;
    }
    result.data = std::move(body);
    return result;
}

}  // namespace

// ── CdnConnection — persistent keep-alive handle ──────────────────────────
CdnConnection::CdnConnection() : handle_(nullptr) {
    curl_global_init(CURL_GLOBAL_DEFAULT);   // idempotent
    handle_ = curl_easy_init();
}

CdnConnection::~CdnConnection() {
    if (handle_) curl_easy_cleanup(static_cast<CURL*>(handle_));
}

CdnConnection::CdnConnection(CdnConnection&& other) noexcept
    : handle_(other.handle_) {
    other.handle_ = nullptr;
}

CdnConnection& CdnConnection::operator=(CdnConnection&& other) noexcept {
    if (this != &other) {
        if (handle_) curl_easy_cleanup(static_cast<CURL*>(handle_));
        handle_       = other.handle_;
        other.handle_ = nullptr;
    }
    return *this;
}

CdnChunkResult CdnClient::fetch_chunk(
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, std::span<const uint8_t> chunk_sha,
        std::string_view cdn_auth_token, std::chrono::seconds timeout) {
    CURL* curl = curl_easy_init();
    if (!curl) {
        CdnChunkResult r;
        r.error = "curl_easy_init failed";
        return r;
    }
    CdnChunkResult r = do_fetch_chunk(curl, ca_bundle_path_, server, depot_id,
                                      chunk_sha, cdn_auth_token, timeout);
    curl_easy_cleanup(curl);
    return r;
}

CdnChunkResult CdnClient::fetch_chunk(
        CdnConnection& conn,
        const pb::CContentServerDirectory_ServerInfo& server,
        uint32_t depot_id, std::span<const uint8_t> chunk_sha,
        std::string_view cdn_auth_token, std::chrono::seconds timeout) {
    if (!conn.valid()) {
        CdnChunkResult r;
        r.error = "cdn connection invalid";
        return r;
    }
    return do_fetch_chunk(static_cast<CURL*>(conn.handle_), ca_bundle_path_,
                          server, depot_id, chunk_sha, cdn_auth_token, timeout);
}

}  // namespace wn_steam
