#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Phase 5.3a — depot content manifest.
//
// A depot manifest, once unzipped (the CDN serves it as a ZIP — see the
// Phase 5.3b fetch path), is a sequence of magic-prefixed sections:
//
//   [u32 LE magic][u32 LE length][length bytes of protobuf]   ...repeated...
//   [u32 LE ENDOFMANIFEST magic]
//
// Three section types carry protobuf bodies (any order):
//   PAYLOAD   — ContentManifestPayload  (repeated FileMapping)
//   METADATA  — ContentManifestMetadata
//   SIGNATURE — ContentManifestSignature
//
// Field numbers + magic constants verified against JavaSteam 1.8.x
//   src/main/proto/.../content_manifest.proto
//   src/main/java/in/dragonbra/javasteam/types/DepotManifest.kt
//
// Manifest filenames are AES-encrypted with the depot key when
// metadata.filenames_encrypted is set; call decrypt_filenames() with the
// 32-byte depot key (from CMsgClientGetDepotDecryptionKeyResponse) to
// recover the cleartext paths.

namespace wn_steam {

struct ContentManifest {
    // 4-byte little-endian section magics (DepotManifest.kt:38-41).
    static constexpr uint32_t kPayloadMagic       = 0x71F617D0u;
    static constexpr uint32_t kMetadataMagic      = 0x1F4812BEu;
    static constexpr uint32_t kSignatureMagic     = 0x1B81B817u;
    static constexpr uint32_t kEndOfManifestMagic = 0x32C415ABu;

    // One chunk of a file. `sha` is the 20-byte SHA1 that also forms the
    // CDN chunk URL (hex-encoded). `crc` is an Adler32 (despite the name)
    // over the *decompressed* chunk — verified after download.
    struct ChunkData {
        std::vector<uint8_t> sha;            // 1 bytes (20)
        uint32_t             crc           = 0;  // 2 fixed32 (Adler32)
        uint64_t             offset        = 0;  // 3 uint64 — offset within the file
        uint32_t             cb_original   = 0;  // 4 uint32 — decompressed size
        uint32_t             cb_compressed = 0;  // 5 uint32 — on-CDN size
    };

    // One file (or directory / symlink) in the depot.
    struct FileMapping {
        std::string            filename;       // 1 string (encrypted until decrypt)
        uint64_t               size  = 0;      // 2 uint64
        uint32_t               flags = 0;      // 3 uint32 (EDepotFileFlag)
        std::vector<uint8_t>   sha_filename;   // 4 bytes
        std::vector<uint8_t>   sha_content;    // 5 bytes
        std::vector<ChunkData> chunks;         // 6 repeated
        std::string            linktarget;     // 7 string (symlink target)
    };

    struct Metadata {
        uint32_t depot_id           = 0;     // 1 uint32
        uint64_t gid_manifest       = 0;     // 2 uint64
        uint32_t creation_time      = 0;     // 3 uint32 (epoch seconds)
        bool     filenames_encrypted = false;// 4 bool
        uint64_t cb_disk_original   = 0;     // 5 uint64
        uint64_t cb_disk_compressed = 0;     // 6 uint64
        uint32_t unique_chunks      = 0;     // 7 uint32
        uint32_t crc_encrypted      = 0;     // 8 uint32
        uint32_t crc_clear          = 0;     // 9 uint32
    };

    Metadata                 metadata;
    std::vector<FileMapping> files;       // ContentManifestPayload.mappings
    std::vector<uint8_t>     signature;   // ContentManifestSignature.signature

    // Parse a raw (already-unzipped) manifest blob. nullopt if a section is
    // malformed or PAYLOAD/METADATA are missing.
    [[nodiscard]] static std::optional<ContentManifest>
    parse(std::span<const uint8_t> raw) noexcept;

    // Decrypt every FileMapping filename / linktarget in place using the
    // 32-byte depot key. No-op (returns true) when filenames_encrypted is
    // false. Returns false on a bad key length or a decrypt failure.
    // On success, metadata.filenames_encrypted is cleared.
    [[nodiscard]] bool decrypt_filenames(std::span<const uint8_t> depot_key);
};

}  // namespace wn_steam
