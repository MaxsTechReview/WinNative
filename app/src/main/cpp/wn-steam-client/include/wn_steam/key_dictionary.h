#pragma once

#include <cstddef>
#include <cstdint>
#include <span>

#include "wn_steam/euniverse.h"

namespace wn_steam {

// Returns a view over the DER-encoded SubjectPublicKeyInfo for Valve's
// 1024-bit RSA public key for the given universe. Bytes match
// SteamKit2's `KeyDictionary` and are imported with
// `d2i_PUBKEY()` (OpenSSL) — the same format ImportSubjectPublicKeyInfo
// accepts in .NET.
//
// Returns an empty span for EUniverse::Invalid, EUniverse::Max, or any
// value outside the known set.
std::span<const uint8_t> get_universe_public_key(EUniverse universe) noexcept;

}  // namespace wn_steam
