#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

namespace wn_steam {

// Standard base64 (RFC 4648), with `+`/`/` alphabet and `=` padding.
// Used for the `encrypted_password` field of
// `CAuthentication_BeginAuthSessionViaCredentials_Request`, which Steam
// expects as base64(RSA(password, PKCS1v15)).
[[nodiscard]] std::string base64_encode(std::span<const uint8_t> bytes);

// Standard base64 decode. Accepts the `+`/`/` alphabet; also tolerates the
// URL-safe `-`/`_` variants and ignores ASCII whitespace, so it decodes
// Steam depot-manifest encrypted filenames directly. `=` padding optional.
// Returns nullopt on an invalid character or a malformed length.
[[nodiscard]] std::optional<std::vector<uint8_t>> base64_decode(std::string_view s);

}  // namespace wn_steam
