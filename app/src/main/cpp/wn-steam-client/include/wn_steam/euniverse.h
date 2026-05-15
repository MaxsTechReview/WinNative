#pragma once

#include <cstdint>

namespace wn_steam {

// Mirrors SteamKit's EUniverse. Values match the wire protocol — do not renumber.
enum class EUniverse : uint32_t {
    Invalid  = 0,
    Public   = 1,
    Beta     = 2,
    Internal = 3,
    Dev      = 4,
    // RC = 5 was removed by Valve years ago and is intentionally omitted.
    // `Max` is the exclusive upper bound used for range checks; not a valid
    // universe value itself. Do not pass it to KeyDictionary or RSA layers.
    Max      = 5,
};

}  // namespace wn_steam
