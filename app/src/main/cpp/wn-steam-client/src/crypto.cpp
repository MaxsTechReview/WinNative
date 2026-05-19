#include "wn_steam/crypto.h"

#include <cstring>

#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>
#include <openssl/rsa.h>
#include <openssl/sha.h>
#include <openssl/x509.h>

#include <zlib.h>

namespace wn_steam {
namespace {

// RAII for EVP_CIPHER_CTX*.
struct CipherCtx {
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    CipherCtx() = default;
    ~CipherCtx() { if (ctx) EVP_CIPHER_CTX_free(ctx); }
    CipherCtx(const CipherCtx&) = delete;
    CipherCtx& operator=(const CipherCtx&) = delete;
};

// RAII for EVP_PKEY*.
struct PKeyHolder {
    EVP_PKEY* pkey = nullptr;
    ~PKeyHolder() { if (pkey) EVP_PKEY_free(pkey); }
    PKeyHolder() = default;
    PKeyHolder(const PKeyHolder&) = delete;
    PKeyHolder& operator=(const PKeyHolder&) = delete;
};

// RAII for EVP_PKEY_CTX*.
struct PKeyCtxHolder {
    EVP_PKEY_CTX* ctx = nullptr;
    ~PKeyCtxHolder() { if (ctx) EVP_PKEY_CTX_free(ctx); }
    PKeyCtxHolder() = default;
    PKeyCtxHolder(const PKeyCtxHolder&) = delete;
    PKeyCtxHolder& operator=(const PKeyCtxHolder&) = delete;
};

}  // namespace

// ---------------------------------------------------------------------------
// SecureSessionKey
// ---------------------------------------------------------------------------

SecureSessionKey::~SecureSessionKey() {
    OPENSSL_cleanse(bytes.data(), bytes.size());
}

SecureSessionKey::SecureSessionKey(SecureSessionKey&& other) noexcept
    : bytes(other.bytes) {
    OPENSSL_cleanse(other.bytes.data(), other.bytes.size());
}

SecureSessionKey& SecureSessionKey::operator=(SecureSessionKey&& other) noexcept {
    if (this != &other) {
        OPENSSL_cleanse(bytes.data(), bytes.size());
        bytes = other.bytes;
        OPENSSL_cleanse(other.bytes.data(), other.bytes.size());
    }
    return *this;
}

// ---------------------------------------------------------------------------
// Random
// ---------------------------------------------------------------------------

bool secure_random_bytes(std::span<uint8_t> out) noexcept {
    if (out.empty()) return true;
    return RAND_bytes(out.data(), static_cast<int>(out.size())) == 1;
}

std::optional<SecureSessionKey> generate_session_key() noexcept {
    SecureSessionKey k;
    if (!secure_random_bytes(k.bytes)) return std::nullopt;
    return k;
}

// ---------------------------------------------------------------------------
// Hashes
// ---------------------------------------------------------------------------

Sha1Digest sha1(std::span<const uint8_t> data) {
    Sha1Digest out{};
    SHA1(data.data(), data.size(), out.data());
    return out;
}

Sha256Digest sha256(std::span<const uint8_t> data) {
    Sha256Digest out{};
    SHA256(data.data(), data.size(), out.data());
    return out;
}

uint32_t crc32(std::span<const uint8_t> data) noexcept {
    // zlib's crc32 expects unsigned long start and bytes. Tiny inputs only
    // (handshake CRC payload is ~128 bytes); uInt cast is safe.
    uLong c = ::crc32(0L, Z_NULL, 0);
    return static_cast<uint32_t>(::crc32(c, data.data(), static_cast<uInt>(data.size())));
}

std::optional<Sha1Digest> hmac_sha1(std::span<const uint8_t> key,
                                    std::span<const uint8_t> data) {
    Sha1Digest out{};
    unsigned int len = 0;
    if (HMAC(EVP_sha1(),
             key.data(), static_cast<int>(key.size()),
             data.data(), data.size(),
             out.data(), &len) == nullptr) {
        return std::nullopt;
    }
    return out;
}

// ---------------------------------------------------------------------------
// AES-256-ECB single block
// ---------------------------------------------------------------------------

bool aes256_ecb_encrypt_block(const SessionKey& key,
                              const AesBlock& in,
                              AesBlock& out) noexcept {
    CipherCtx c;
    if (!c.ctx) return false;
    if (EVP_EncryptInit_ex(c.ctx, EVP_aes_256_ecb(), nullptr,
                           key.data(), nullptr) != 1) return false;
    if (EVP_CIPHER_CTX_set_padding(c.ctx, 0) != 1) return false;

    int outlen = 0;
    if (EVP_EncryptUpdate(c.ctx, out.data(), &outlen,
                          in.data(), static_cast<int>(in.size())) != 1) return false;

    // With padding disabled and a full block in, Final_ex must produce zero
    // output. Use a separate scratch buffer to keep this provably free of
    // any one-past-the-end write into `out`.
    uint8_t scratch[kAesBlockBytes];
    int finlen = 0;
    if (EVP_EncryptFinal_ex(c.ctx, scratch, &finlen) != 1) return false;
    if (finlen != 0) return false;

    return outlen == static_cast<int>(kAesBlockBytes);
}

bool aes256_ecb_decrypt_block(const SessionKey& key,
                              const AesBlock& in,
                              AesBlock& out) noexcept {
    CipherCtx c;
    if (!c.ctx) return false;
    if (EVP_DecryptInit_ex(c.ctx, EVP_aes_256_ecb(), nullptr,
                           key.data(), nullptr) != 1) return false;
    if (EVP_CIPHER_CTX_set_padding(c.ctx, 0) != 1) return false;

    int outlen = 0;
    if (EVP_DecryptUpdate(c.ctx, out.data(), &outlen,
                          in.data(), static_cast<int>(in.size())) != 1) return false;

    uint8_t scratch[kAesBlockBytes];
    int finlen = 0;
    if (EVP_DecryptFinal_ex(c.ctx, scratch, &finlen) != 1) return false;
    if (finlen != 0) return false;

    return outlen == static_cast<int>(kAesBlockBytes);
}

// ---------------------------------------------------------------------------
// AES-256-CBC with PKCS7 padding
// ---------------------------------------------------------------------------

std::optional<std::vector<uint8_t>> aes256_cbc_encrypt(
    const SessionKey& key, const AesBlock& iv, std::span<const uint8_t> plaintext) {

    CipherCtx c;
    if (!c.ctx) return std::nullopt;
    if (EVP_EncryptInit_ex(c.ctx, EVP_aes_256_cbc(), nullptr,
                           key.data(), iv.data()) != 1) return std::nullopt;
    // PKCS7 padding is default (1); explicit for clarity.
    if (EVP_CIPHER_CTX_set_padding(c.ctx, 1) != 1) return std::nullopt;

    // Worst case: plaintext.size() + one full block of padding.
    std::vector<uint8_t> out(plaintext.size() + kAesBlockBytes);

    int outlen = 0;
    if (EVP_EncryptUpdate(c.ctx, out.data(), &outlen,
                          plaintext.data(),
                          static_cast<int>(plaintext.size())) != 1) return std::nullopt;
    int finlen = 0;
    if (EVP_EncryptFinal_ex(c.ctx, out.data() + outlen, &finlen) != 1) return std::nullopt;

    out.resize(static_cast<size_t>(outlen + finlen));
    return out;
}

std::optional<std::vector<uint8_t>> aes256_cbc_decrypt(
    const SessionKey& key, const AesBlock& iv, std::span<const uint8_t> ciphertext) {

    if (ciphertext.empty() || (ciphertext.size() % kAesBlockBytes) != 0) {
        return std::nullopt;
    }

    CipherCtx c;
    if (!c.ctx) return std::nullopt;
    if (EVP_DecryptInit_ex(c.ctx, EVP_aes_256_cbc(), nullptr,
                           key.data(), iv.data()) != 1) return std::nullopt;
    if (EVP_CIPHER_CTX_set_padding(c.ctx, 1) != 1) return std::nullopt;

    std::vector<uint8_t> out(ciphertext.size());

    int outlen = 0;
    if (EVP_DecryptUpdate(c.ctx, out.data(), &outlen,
                          ciphertext.data(),
                          static_cast<int>(ciphertext.size())) != 1) return std::nullopt;
    int finlen = 0;
    if (EVP_DecryptFinal_ex(c.ctx, out.data() + outlen, &finlen) != 1) return std::nullopt;

    out.resize(static_cast<size_t>(outlen + finlen));
    return out;
}

// ---------------------------------------------------------------------------
// RSA-OAEP-SHA1 encrypt
// ---------------------------------------------------------------------------

std::optional<std::vector<uint8_t>> rsa_oaep_sha1_encrypt(
    std::span<const uint8_t> spki_der, std::span<const uint8_t> plaintext) {

    // Import the SPKI blob into an EVP_PKEY.
    const unsigned char* p = spki_der.data();
    PKeyHolder pkey;
    pkey.pkey = d2i_PUBKEY(nullptr, &p, static_cast<long>(spki_der.size()));
    if (!pkey.pkey) return std::nullopt;

    // Proactive plaintext-size check. OAEP-SHA1 max = modulus_size - 2 - 2*20.
    // For Valve's 1024-bit keys: 128 - 42 = 86. Defensive — OpenSSL would
    // reject anyway, but we want a clear failure point in our own code path.
    const size_t modulus = static_cast<size_t>(EVP_PKEY_size(pkey.pkey));
    if (modulus < 2 + 2 * kSha1Bytes) return std::nullopt;
    const size_t max_pt = modulus - 2 - 2 * kSha1Bytes;
    if (plaintext.size() > max_pt) return std::nullopt;

    PKeyCtxHolder ctx;
    ctx.ctx = EVP_PKEY_CTX_new(pkey.pkey, nullptr);
    if (!ctx.ctx) return std::nullopt;

    if (EVP_PKEY_encrypt_init(ctx.ctx) <= 0) return std::nullopt;
    if (EVP_PKEY_CTX_set_rsa_padding(ctx.ctx, RSA_PKCS1_OAEP_PADDING) <= 0) return std::nullopt;
    if (EVP_PKEY_CTX_set_rsa_oaep_md(ctx.ctx, EVP_sha1()) <= 0) return std::nullopt;
    if (EVP_PKEY_CTX_set_rsa_mgf1_md(ctx.ctx, EVP_sha1()) <= 0) return std::nullopt;

    // First call: ask for ciphertext length.
    size_t outlen = 0;
    if (EVP_PKEY_encrypt(ctx.ctx, nullptr, &outlen,
                         plaintext.data(), plaintext.size()) <= 0) return std::nullopt;

    std::vector<uint8_t> ciphertext(outlen);
    if (EVP_PKEY_encrypt(ctx.ctx, ciphertext.data(), &outlen,
                         plaintext.data(), plaintext.size()) <= 0) return std::nullopt;

    ciphertext.resize(outlen);
    return ciphertext;
}

}  // namespace wn_steam
