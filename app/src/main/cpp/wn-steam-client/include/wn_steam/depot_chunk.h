#pragma once

#include <cstdint>
#include <span>
#include <string>
#include <vector>

// Phase 5.4 — depot chunk decrypt + decompress + verify.
//
// A chunk fetched from the CDN (CdnClient::fetch_chunk) is:
//   1. AES-encrypted with the depot key — same scheme as manifest
//      filenames: the first 16 bytes are an ECB-wrapped IV, the rest is
//      AES-256-CBC/PKCS7.
//   2. Compressed. The decrypted buffer's leading bytes select the codec:
//        "VSZa"      -> VZSTD  (raw zstd stream in a Steam header/footer)
//        "VZa"       -> VZip   (LZMA1 — Phase 5.4b, not yet supported)
//        "PK\x03\x04"-> PKZip  (deflate, single entry)
//   3. Verified — Adler32 of the decompressed bytes must equal the
//      manifest ChunkData.crc, and the length must equal cb_original.
//
// Verified against JavaSteam steam/cdn/DepotChunk.kt + VZstdUtil.kt.

namespace wn_steam {

struct DepotChunkResult {
    std::vector<uint8_t> data;    // decompressed, verified chunk bytes
    std::string          error;  // empty on success

    [[nodiscard]] bool ok() const noexcept { return error.empty(); }
};

// Decrypt, decompress and verify one depot chunk.
//   raw           — encrypted chunk exactly as downloaded from the CDN.
//   depot_key     — 32-byte AES-256 depot key.
//   expected_crc  — manifest ChunkData.crc (an Adler32, despite the name).
//   expected_size — manifest ChunkData.cb_original (decompressed size).
[[nodiscard]] DepotChunkResult process_depot_chunk(
    std::span<const uint8_t> raw,
    std::span<const uint8_t> depot_key,
    uint32_t expected_crc,
    uint32_t expected_size);

}  // namespace wn_steam
