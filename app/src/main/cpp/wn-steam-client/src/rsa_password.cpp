#include "wn_steam/rsa_password.h"

#include <android/log.h>

#include <cctype>
#include <openssl/bn.h>
#include <openssl/rsa.h>
#include <openssl/evp.h>

namespace wn_steam {

namespace {

constexpr const char* kLogTag = "WnSteamRSA";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

std::optional<std::vector<uint8_t>> hex_decode(std::string_view hex) {
    if (hex.size() % 2 != 0) return std::nullopt;
    std::vector<uint8_t> out;
    out.reserve(hex.size() / 2);
    auto nibble = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        return -1;
    };
    for (size_t i = 0; i < hex.size(); i += 2) {
        int hi = nibble(hex[i]);
        int lo = nibble(hex[i + 1]);
        if (hi < 0 || lo < 0) return std::nullopt;
        out.push_back(static_cast<uint8_t>((hi << 4) | lo));
    }
    return out;
}

}  // namespace

std::optional<std::vector<uint8_t>>
rsa_pkcs1v15_encrypt_password_with_hex_key(std::string_view password,
                                           std::string_view publickey_mod_hex,
                                           std::string_view publickey_exp_hex) {
    auto mod = hex_decode(publickey_mod_hex);
    if (!mod) { WN_LOGE("publickey_mod hex decode failed"); return std::nullopt; }
    auto exp = hex_decode(publickey_exp_hex);
    if (!exp) { WN_LOGE("publickey_exp hex decode failed"); return std::nullopt; }

    BIGNUM* n = BN_bin2bn(mod->data(), static_cast<int>(mod->size()), nullptr);
    BIGNUM* e = BN_bin2bn(exp->data(), static_cast<int>(exp->size()), nullptr);
    if (!n || !e) {
        BN_free(n); BN_free(e);
        WN_LOGE("BN_bin2bn failed");
        return std::nullopt;
    }

    RSA* rsa = RSA_new();
    if (!rsa) { BN_free(n); BN_free(e); return std::nullopt; }
    if (RSA_set0_key(rsa, n, e, nullptr) != 1) {
        RSA_free(rsa);  // takes ownership of n,e on success only
        BN_free(n);
        BN_free(e);
        WN_LOGE("RSA_set0_key failed");
        return std::nullopt;
    }

    const int rsa_size = RSA_size(rsa);
    std::vector<uint8_t> ciphertext(static_cast<size_t>(rsa_size));

    const int rc = RSA_public_encrypt(
        static_cast<int>(password.size()),
        reinterpret_cast<const unsigned char*>(password.data()),
        ciphertext.data(),
        rsa,
        RSA_PKCS1_PADDING);
    RSA_free(rsa);

    if (rc <= 0) {
        WN_LOGE("RSA_public_encrypt failed (rc=%d)", rc);
        return std::nullopt;
    }
    ciphertext.resize(static_cast<size_t>(rc));
    return ciphertext;
}

}  // namespace wn_steam
