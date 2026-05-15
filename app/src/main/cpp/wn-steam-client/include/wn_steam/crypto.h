#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <span>
#include <vector>

namespace wn_steam {

// Fixed-size constants used throughout the encrypted channel layer.
constexpr size_t kSessionKeyLength = 32;  // AES-256
constexpr size_t kAesBlockBytes    = 16;
constexpr size_t kSha1Bytes        = 20;
constexpr size_t kSha256Bytes      = 32;
constexpr size_t kHmacKeyLength    = 16;  // HMAC key = first 16 bytes of session key

using SessionKey = std::array<uint8_t, kSessionKeyLength>;
using Sha1Digest = std::array<uint8_t, kSha1Bytes>;
using Sha256Digest = std::array<uint8_t, kSha256Bytes>;
using AesBlock   = std::array<uint8_t, kAesBlockBytes>;

// ---------------------------------------------------------------------------
// SecureSessionKey
// ---------------------------------------------------------------------------
//
// Wrapper around a SessionKey that zeroes its bytes (via OPENSSL_cleanse) on
// destruction. Use this for any long-lived storage of the session key — a
// plain std::array on the stack leaves key material in the dead frame until
// overwritten by some unrelated function, which is a passive-disclosure risk.
//
// Copy is deleted; move is OK and leaves the source zeroed.
struct SecureSessionKey {
    SessionKey bytes{};

    SecureSessionKey() = default;
    explicit SecureSessionKey(const SessionKey& src) : bytes(src) {}
    ~SecureSessionKey();

    SecureSessionKey(const SecureSessionKey&) = delete;
    SecureSessionKey& operator=(const SecureSessionKey&) = delete;

    SecureSessionKey(SecureSessionKey&& other) noexcept;
    SecureSessionKey& operator=(SecureSessionKey&& other) noexcept;
};

// ---------------------------------------------------------------------------
// Random
// ---------------------------------------------------------------------------

// Cryptographically secure random fill. Returns false only on RNG failure.
[[nodiscard]] bool secure_random_bytes(std::span<uint8_t> out) noexcept;

// Generates a fresh AES-256 session key. Returns nullopt on RNG failure —
// callers MUST handle that case rather than proceeding with a zero key.
[[nodiscard]] std::optional<SecureSessionKey> generate_session_key() noexcept;

// ---------------------------------------------------------------------------
// SHA-1 / SHA-256 / CRC32 / HMAC-SHA1
// ---------------------------------------------------------------------------

Sha1Digest   sha1(std::span<const uint8_t> data);
Sha256Digest sha256(std::span<const uint8_t> data);

// CRC32 (IEEE 802.3 / zlib). Used for ChannelEncryptResponse integrity field.
// Verified to match .NET's System.IO.Hashing.Crc32 (SteamKit2 master) — same
// polynomial 0x04C11DB7, init 0xFFFFFFFF, final XOR 0xFFFFFFFF.
uint32_t crc32(std::span<const uint8_t> data) noexcept;

// HMAC-SHA1 — full 20-byte tag. Returns nullopt only on internal OpenSSL
// failure (OOM); will not fail for valid SHA-1 inputs. Callers in the
// envelope layer MUST propagate failure rather than use a zero tag.
[[nodiscard]] std::optional<Sha1Digest> hmac_sha1(std::span<const uint8_t> key,
                                                  std::span<const uint8_t> data);

// ---------------------------------------------------------------------------
// AES-256
// ---------------------------------------------------------------------------

// AES-256-ECB single-block encrypt/decrypt with no padding. Steam uses ECB
// only to wrap the per-message IV at the front of each CBC payload; it is
// NOT used for bulk encryption anywhere in the protocol.
[[nodiscard]] bool aes256_ecb_encrypt_block(const SessionKey& key,
                                            const AesBlock& in,
                                            AesBlock& out) noexcept;
[[nodiscard]] bool aes256_ecb_decrypt_block(const SessionKey& key,
                                            const AesBlock& in,
                                            AesBlock& out) noexcept;

// AES-256-CBC with PKCS7 padding. Caller supplies the IV.
//
// IMPORTANT: post-handshake Steam messages do NOT use a plain random IV.
// `NetFilterEncryptionWithHMAC` (SteamKit2 master) generates the IV as:
//     iv[ 0..13] = HMAC-SHA1(sessionKey[0..16], random(3) || plaintext)[0..13]
//     iv[13..16] = 3 random bytes
// then ECB-encrypts that 16-byte IV with the session key and prepends it to
// the CBC ciphertext. This module exposes only the raw CBC primitive; the
// encrypted-channel layer (encrypted_channel.cpp, added in Phase 1E) is
// responsible for assembling the envelope.
std::optional<std::vector<uint8_t>> aes256_cbc_encrypt(
    const SessionKey& key, const AesBlock& iv, std::span<const uint8_t> plaintext);
std::optional<std::vector<uint8_t>> aes256_cbc_decrypt(
    const SessionKey& key, const AesBlock& iv, std::span<const uint8_t> ciphertext);

// ---------------------------------------------------------------------------
// RSA-OAEP-SHA1 encrypt
// ---------------------------------------------------------------------------
//
// Encrypt `plaintext` using Valve's RSA-1024 public key for the given
// universe (DER SubjectPublicKeyInfo from KeyDictionary). Padding is
// RSA-OAEP with SHA-1 for BOTH the MGF1 hash and the label hash; label
// is empty. This matches `RSAEncryptionPadding.OaepSHA1` in SteamKit2's
// `EnvelopeEncryptedConnection.HandleEncryptRequest`.
//
// IMPORTANT: the handshake caller is responsible for assembling the input
// blob as `tempSessionKey (32 bytes) ‖ randomChallenge (>=16 bytes from
// server)` — typically 48 bytes total — before calling this function.
// See SteamKit2's `HandleEncryptRequest` for the canonical assembly.
//
// Returns nullopt if:
//   - SPKI DER fails to parse
//   - plaintext exceeds OAEP-SHA1 capacity for the given modulus
//     (86 bytes for 1024-bit RSA = 128 - 2 - 2*20)
//   - OpenSSL encrypt step fails
//
// Output is exactly the RSA modulus size (128 bytes for the 1024-bit
// keys Valve currently uses).
std::optional<std::vector<uint8_t>> rsa_oaep_sha1_encrypt(
    std::span<const uint8_t> spki_der, std::span<const uint8_t> plaintext);

}  // namespace wn_steam
