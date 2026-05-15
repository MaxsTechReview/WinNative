#pragma once

namespace wn_steam {

struct Version {
    int major;
    int minor;
    int patch;
    const char* string;
};

const Version& version() noexcept;

}  // namespace wn_steam
