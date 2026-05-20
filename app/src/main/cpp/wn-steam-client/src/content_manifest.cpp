#include "wn_steam/content_manifest.h"

#include <algorithm>
#include <cctype>

#include "wn_steam/base64.h"
#include "wn_steam/crypto.h"
#include "wn_steam/proto_wire.h"

namespace wn_steam {

namespace {

// Read a 4-byte little-endian uint32 at `pos`; advances `pos`. Returns
// false if fewer than 4 bytes remain.
bool read_u32_le(std::span<const uint8_t> buf, size_t& pos, uint32_t& out) {
    if (pos + 4 > buf.size()) return false;
    out = static_cast<uint32_t>(buf[pos])
        | (static_cast<uint32_t>(buf[pos + 1]) << 8)
        | (static_cast<uint32_t>(buf[pos + 2]) << 16)
        | (static_cast<uint32_t>(buf[pos + 3]) << 24);
    pos += 4;
    return true;
}

// ContentManifestPayload.FileMapping.ChunkData
std::optional<ContentManifest::ChunkData> parse_chunk(std::span<const uint8_t> body) {
    proto::Reader r(body);
    ContentManifest::ChunkData c;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) { if (!r.ok()) return std::nullopt; break; }
        switch (t->field_number) {
            case 1: { auto b = r.bytes(); if (!b) return std::nullopt;
                      c.sha.assign(b->begin(), b->end()); break; }
            case 2: { auto v = r.fixed32(); if (!v) return std::nullopt; c.crc = *v; break; }
            case 3: { auto v = r.u64(); if (!v) return std::nullopt; c.offset = *v; break; }
            case 4: { auto v = r.u32(); if (!v) return std::nullopt; c.cb_original = *v; break; }
            case 5: { auto v = r.u32(); if (!v) return std::nullopt; c.cb_compressed = *v; break; }
            default: if (!r.skip(t->wire_type)) return std::nullopt; break;
        }
    }
    return c;
}

// ContentManifestPayload.FileMapping
std::optional<ContentManifest::FileMapping> parse_file_mapping(std::span<const uint8_t> body) {
    proto::Reader r(body);
    ContentManifest::FileMapping f;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) { if (!r.ok()) return std::nullopt; break; }
        switch (t->field_number) {
            case 1: { auto v = r.string(); if (!v) return std::nullopt; f.filename = std::move(*v); break; }
            case 2: { auto v = r.u64(); if (!v) return std::nullopt; f.size = *v; break; }
            case 3: { auto v = r.u32(); if (!v) return std::nullopt; f.flags = *v; break; }
            case 4: { auto b = r.bytes(); if (!b) return std::nullopt;
                      f.sha_filename.assign(b->begin(), b->end()); break; }
            case 5: { auto b = r.bytes(); if (!b) return std::nullopt;
                      f.sha_content.assign(b->begin(), b->end()); break; }
            case 6: { auto b = r.bytes(); if (!b) return std::nullopt;
                      auto c = parse_chunk(*b); if (!c) return std::nullopt;
                      f.chunks.push_back(std::move(*c)); break; }
            case 7: { auto v = r.string(); if (!v) return std::nullopt; f.linktarget = std::move(*v); break; }
            default: if (!r.skip(t->wire_type)) return std::nullopt; break;
        }
    }
    return f;
}

// ContentManifestPayload — repeated FileMapping mappings = field 1.
bool parse_payload(std::span<const uint8_t> body, std::vector<ContentManifest::FileMapping>& out) {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) { if (!r.ok()) return false; break; }
        if (t->field_number == 1) {
            auto b = r.bytes();
            if (!b) return false;
            auto fm = parse_file_mapping(*b);
            if (!fm) return false;
            out.push_back(std::move(*fm));
        } else if (!r.skip(t->wire_type)) {
            return false;
        }
    }
    return true;
}

// ContentManifestMetadata
bool parse_metadata(std::span<const uint8_t> body, ContentManifest::Metadata& m) {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) { if (!r.ok()) return false; break; }
        switch (t->field_number) {
            case 1: { auto v = r.u32(); if (!v) return false; m.depot_id = *v; break; }
            case 2: { auto v = r.u64(); if (!v) return false; m.gid_manifest = *v; break; }
            case 3: { auto v = r.u32(); if (!v) return false; m.creation_time = *v; break; }
            case 4: { auto v = r.boolean(); if (!v) return false; m.filenames_encrypted = *v; break; }
            case 5: { auto v = r.u64(); if (!v) return false; m.cb_disk_original = *v; break; }
            case 6: { auto v = r.u64(); if (!v) return false; m.cb_disk_compressed = *v; break; }
            case 7: { auto v = r.u32(); if (!v) return false; m.unique_chunks = *v; break; }
            case 8: { auto v = r.u32(); if (!v) return false; m.crc_encrypted = *v; break; }
            case 9: { auto v = r.u32(); if (!v) return false; m.crc_clear = *v; break; }
            default: if (!r.skip(t->wire_type)) return false; break;
        }
    }
    return true;
}

// ContentManifestSignature — signature = field 1 bytes.
bool parse_signature(std::span<const uint8_t> body, std::vector<uint8_t>& out) {
    proto::Reader r(body);
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) { if (!r.ok()) return false; break; }
        if (t->field_number == 1) {
            auto b = r.bytes();
            if (!b) return false;
            out.assign(b->begin(), b->end());
        } else if (!r.skip(t->wire_type)) {
            return false;
        }
    }
    return true;
}

}  // namespace

std::optional<ContentManifest> ContentManifest::parse(std::span<const uint8_t> raw) noexcept {
    ContentManifest m;
    bool have_payload = false, have_metadata = false;
    size_t pos = 0;

    while (true) {
        uint32_t magic = 0;
        if (!read_u32_le(raw, pos, magic)) return std::nullopt;  // truncated
        if (magic == kEndOfManifestMagic) break;

        uint32_t len = 0;
        if (!read_u32_le(raw, pos, len)) return std::nullopt;
        if (pos + len > raw.size()) return std::nullopt;          // section overruns buffer
        std::span<const uint8_t> section = raw.subspan(pos, len);
        pos += len;

        switch (magic) {
            case kPayloadMagic:
                if (!parse_payload(section, m.files)) return std::nullopt;
                have_payload = true;
                break;
            case kMetadataMagic:
                if (!parse_metadata(section, m.metadata)) return std::nullopt;
                have_metadata = true;
                break;
            case kSignatureMagic:
                if (!parse_signature(section, m.signature)) return std::nullopt;
                break;
            default:
                return std::nullopt;  // unknown section magic — corrupt manifest
        }
    }

    if (!have_payload || !have_metadata) return std::nullopt;
    return m;
}

bool ContentManifest::decrypt_filenames(std::span<const uint8_t> depot_key) {
    if (metadata.filenames_encrypted) {
        if (depot_key.size() != kSessionKeyLength) return false;  // AES-256 key required

        SessionKey key{};
        std::copy(depot_key.begin(), depot_key.end(), key.begin());

        // Decrypt one Steam-encrypted name: base64 → [16-byte ECB-wrapped IV]
        // [AES-CBC body]. ECB-decrypt the IV, then CBC-decrypt the body.
        auto decrypt_name = [&](const std::string& enc,
                                std::string& out) -> bool {
            auto blob = base64_decode(enc);
            if (!blob || blob->size() < kAesBlockBytes * 2) return false;

            AesBlock wrapped_iv{}, iv{};
            std::copy_n(blob->begin(), kAesBlockBytes, wrapped_iv.begin());
            if (!aes256_ecb_decrypt_block(key, wrapped_iv, iv)) return false;

            std::span<const uint8_t> body(blob->data() + kAesBlockBytes,
                                          blob->size() - kAesBlockBytes);
            auto plain = aes256_cbc_decrypt(key, iv, body);
            if (!plain) return false;

            out.assign(plain->begin(), plain->end());
            // Steam pads decrypted names with a trailing NUL; trim it.
            if (!out.empty() && out.back() == '\0') out.pop_back();
            return true;
        };

        for (auto& f : files) {
            std::string clear;
            if (!decrypt_name(f.filename, clear)) return false;
            f.filename = std::move(clear);
            if (!f.linktarget.empty()) {
                std::string link;
                if (!decrypt_name(f.linktarget, link)) return false;
                f.linktarget = std::move(link);
            }
        }
        metadata.filenames_encrypted = false;
    }

    // Normalise Windows-style '\' separators to '/' — for BOTH freshly
    // decrypted names AND manifests that arrived with plaintext filenames
    // (older depots leave filenames_encrypted=false and never reach the
    // decrypt path). Android's FUSE-backed external storage rejects '\' in a
    // filename, so a depot whose names still carry backslashes would fail
    // write_depot's open()/mkdir() outright.
    for (auto& f : files) {
        std::replace(f.filename.begin(), f.filename.end(), '\\', '/');
        std::replace(f.linktarget.begin(), f.linktarget.end(), '\\', '/');
    }

    // Steam sorts the file list case-insensitively by name.
    std::sort(files.begin(), files.end(),
              [](const FileMapping& a, const FileMapping& b) {
        return std::lexicographical_compare(
            a.filename.begin(), a.filename.end(),
            b.filename.begin(), b.filename.end(),
            [](char x, char y) {
                return std::tolower(static_cast<unsigned char>(x))
                     < std::tolower(static_cast<unsigned char>(y));
            });
    });

    return true;
}

}  // namespace wn_steam
