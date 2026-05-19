#include "wn_steam/depot_chunk.h"

#include <android/log.h>
#include <lzma.h>
#include <zlib.h>
#include <zstd.h>

#include <algorithm>
#include <cstdlib>
#include <optional>

#include "wn_steam/cdn_client.h"
#include "wn_steam/crypto.h"

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamChunk";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

DepotChunkResult fail(std::string msg) {
    DepotChunkResult r;
    r.error = std::move(msg);
    WN_LOGE("%s", r.error.c_str());
    return r;
}

// Steam symmetric decrypt: [16-byte ECB-wrapped IV][AES-256-CBC/PKCS7 body].
// Identical to manifest-filename decryption.
std::optional<std::vector<uint8_t>>
steam_symmetric_decrypt(const SessionKey& key, std::span<const uint8_t> enc) {
    if (enc.size() < kAesBlockBytes * 2) return std::nullopt;
    AesBlock wrapped{}, iv{};
    std::copy_n(enc.begin(), kAesBlockBytes, wrapped.begin());
    if (!aes256_ecb_decrypt_block(key, wrapped, iv)) return std::nullopt;
    return aes256_cbc_decrypt(
        key, iv, enc.subspan(kAesBlockBytes, enc.size() - kAesBlockBytes));
}

// VZSTD: 8-byte header ("VSZa" + crc32), one raw zstd frame, then a small
// trailer. We locate the exact frame size with ZSTD_findFrameCompressedSize
// (so the trailer layout doesn't matter) and decompress to expected_size.
DepotChunkResult decompress_vzstd(std::span<const uint8_t> dec, uint32_t expected_size) {
    constexpr size_t kHeader = 8;
    if (dec.size() <= kHeader) return fail("vzstd: chunk too small");
    std::span<const uint8_t> payload = dec.subspan(kHeader);

    size_t frame_size = ZSTD_findFrameCompressedSize(payload.data(), payload.size());
    if (ZSTD_isError(frame_size) || frame_size == 0 || frame_size > payload.size()) {
        return fail("vzstd: bad zstd frame");
    }
    DepotChunkResult r;
    r.data.resize(expected_size);
    size_t got = ZSTD_decompress(r.data.data(), r.data.size(),
                                 payload.data(), frame_size);
    if (ZSTD_isError(got)) return fail(std::string("vzstd: ") + ZSTD_getErrorName(got));
    r.data.resize(got);
    return r;
}

// VZip: legacy Steam chunk codec — a raw LZMA1 stream in a Steam wrapper.
//   [0..2]   'V' 'Z' 'a'                       (magic + version)
//   [3..6]   uint32 source CRC
//   [7..11]  5-byte LZMA1 properties (lc/lp/pb + dict size)
//   [12..N]  raw LZMA1 stream (no end marker — output length is known)
//   last 10  uint32 output CRC, uint32 decompressed size, 'z' 'v'
DepotChunkResult decompress_vzip(std::span<const uint8_t> dec, uint32_t expected_size) {
    constexpr size_t kHeader = 3 + 4;   // magic + source CRC
    constexpr size_t kProps  = 5;
    constexpr size_t kFooter = 10;
    if (dec.size() < kHeader + kProps + kFooter)
        return fail("vzip: chunk too small");
    if (dec[dec.size() - 2] != 'z' || dec[dec.size() - 1] != 'v')
        return fail("vzip: bad footer");

    const uint8_t* props = dec.data() + kHeader;
    std::span<const uint8_t> comp =
        dec.subspan(kHeader + kProps,
                    dec.size() - kHeader - kProps - kFooter);

    // Build a one-filter raw LZMA1 chain from the 5-byte properties.
    lzma_filter filters[2];
    filters[0].id      = LZMA_FILTER_LZMA1;
    filters[0].options = nullptr;
    filters[1].id      = LZMA_VLI_UNKNOWN;
    if (lzma_properties_decode(&filters[0], nullptr, props, kProps) != LZMA_OK)
        return fail("vzip: bad LZMA properties");

    lzma_stream strm = LZMA_STREAM_INIT;
    if (lzma_raw_decoder(&strm, filters) != LZMA_OK) {
        std::free(filters[0].options);
        return fail("vzip: raw decoder init failed");
    }

    DepotChunkResult r;
    r.data.resize(expected_size);
    strm.next_in   = comp.data();
    strm.avail_in  = comp.size();
    strm.next_out  = r.data.data();
    strm.avail_out = r.data.size();
    lzma_ret ret = lzma_code(&strm, LZMA_FINISH);
    const size_t produced = r.data.size() - strm.avail_out;
    lzma_end(&strm);
    std::free(filters[0].options);

    // A raw LZMA1 stream has no end marker; when the (known-size) output
    // buffer fills, liblzma reports LZMA_OK / LZMA_STREAM_END / LZMA_BUF_ERROR.
    // The caller verifies size + Adler32, so accept any of those.
    if (ret != LZMA_OK && ret != LZMA_STREAM_END && ret != LZMA_BUF_ERROR)
        return fail("vzip: LZMA decode failed ("
                    + std::to_string(static_cast<int>(ret)) + ")");
    r.data.resize(produced);
    return r;
}

// Steam's chunk checksum (ContentManifest ChunkData.crc). It is an Adler-32
// variant — but Steam seeds it with a=0, NOT the standard Adler-32 a=1 that
// zlib's adler32() uses. Using zlib's adler32() makes every chunk fail to
// verify. Matches SteamKit2 Util.AdlerHash.
uint32_t steam_adler_hash(std::span<const uint8_t> data) {
    uint32_t a = 0, b = 0;
    for (uint8_t byte : data) {
        a = (a + byte) % 65521;
        b = (b + a)    % 65521;
    }
    return a | (b << 16);
}

}  // namespace

DepotChunkResult process_depot_chunk(std::span<const uint8_t> raw,
                                     std::span<const uint8_t> depot_key,
                                     uint32_t expected_crc,
                                     uint32_t expected_size) {
    if (depot_key.size() != kSessionKeyLength) return fail("chunk: bad depot key length");
    if (raw.empty()) return fail("chunk: empty");

    SessionKey key{};
    std::copy(depot_key.begin(), depot_key.end(), key.begin());

    auto dec = steam_symmetric_decrypt(key, raw);
    if (!dec || dec->empty()) return fail("chunk: AES decrypt failed");

    // Codec dispatch on the decrypted prefix.
    DepotChunkResult r;
    const auto& d = *dec;
    if (d.size() >= 4 && d[0] == 'V' && d[1] == 'S' && d[2] == 'Z' && d[3] == 'a') {
        r = decompress_vzstd(d, expected_size);
    } else if (d.size() >= 3 && d[0] == 'V' && d[1] == 'Z' && d[2] == 'a') {
        r = decompress_vzip(d, expected_size);
    } else if (d.size() >= 4 && d[0] == 'P' && d[1] == 'K' && d[2] == 0x03 && d[3] == 0x04) {
        auto unz = CdnClient::unzip_first_entry(d);
        if (!unz) return fail("chunk: PKZip decompress failed");
        r.data = std::move(*unz);
    } else {
        return fail("chunk: unrecognised compression header");
    }
    if (!r.ok()) return r;

    // Verify — length then Adler32 (Steam's ChunkData.crc is an Adler32).
    if (r.data.size() != expected_size) {
        return fail("chunk: size mismatch (" + std::to_string(r.data.size())
                    + " != " + std::to_string(expected_size) + ")");
    }
    if (steam_adler_hash(r.data) != expected_crc) {
        return fail("chunk: Adler32 mismatch");
    }
    return r;
}

}  // namespace wn_steam
