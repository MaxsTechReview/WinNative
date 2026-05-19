#include "wn_steam/version.h"

#ifndef WN_STEAM_CLIENT_VERSION_MAJOR
#error "WN_STEAM_CLIENT_VERSION_MAJOR not defined — build must come through CMake"
#endif

namespace wn_steam {

const Version& version() noexcept {
    static constexpr Version v{
        WN_STEAM_CLIENT_VERSION_MAJOR,
        WN_STEAM_CLIENT_VERSION_MINOR,
        WN_STEAM_CLIENT_VERSION_PATCH,
        WN_STEAM_CLIENT_VERSION_STRING,
    };
    return v;
}

}  // namespace wn_steam
