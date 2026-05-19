#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace wn_steam {

// Encrypts a UTF-8 password with the user-specific RSA public key returned
// by `Authentication.GetPasswordRSAPublicKey`. Uses PKCS#1 v1.5 padding
// (NOT OAEP — the response-blob handshake in Phase 1 uses OAEP-SHA1; the
// modern login flow uses PKCS1v15 for the password). Matches SteamKit2's
// `Authentication.cs` behavior.
//
// `publickey_mod` and `publickey_exp` are uppercase hex strings as the
// service returns them (e.g. mod = 128-byte modulus as 256 hex chars,
// exp = "010001"). Returns nullopt on RSA failure.
[[nodiscard]] std::optional<std::vector<uint8_t>>
rsa_pkcs1v15_encrypt_password_with_hex_key(
    std::string_view password,
    std::string_view publickey_mod_hex,
    std::string_view publickey_exp_hex);

}  // namespace wn_steam
