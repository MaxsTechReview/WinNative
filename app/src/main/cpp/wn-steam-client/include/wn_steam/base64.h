#pragma once

#include <cstdint>
#include <span>
#include <string>

namespace wn_steam {

// Standard base64 (RFC 4648), with `+`/`/` alphabet and `=` padding.
// Used for the `encrypted_password` field of
// `CAuthentication_BeginAuthSessionViaCredentials_Request`, which Steam
// expects as base64(RSA(password, PKCS1v15)).
[[nodiscard]] std::string base64_encode(std::span<const uint8_t> bytes);

}  // namespace wn_steam
