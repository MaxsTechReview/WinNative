#include "wn_steam/base64.h"

namespace wn_steam {

namespace {
constexpr char kAlphabet[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
}

std::string base64_encode(std::span<const uint8_t> bytes) {
    std::string out;
    if (bytes.empty()) return out;
    out.reserve(((bytes.size() + 2) / 3) * 4);

    size_t i = 0;
    const size_t n = bytes.size();
    while (i + 3 <= n) {
        uint32_t v = (static_cast<uint32_t>(bytes[i])     << 16) |
                     (static_cast<uint32_t>(bytes[i + 1]) <<  8) |
                      static_cast<uint32_t>(bytes[i + 2]);
        out.push_back(kAlphabet[(v >> 18) & 0x3F]);
        out.push_back(kAlphabet[(v >> 12) & 0x3F]);
        out.push_back(kAlphabet[(v >>  6) & 0x3F]);
        out.push_back(kAlphabet[ v        & 0x3F]);
        i += 3;
    }

    const size_t rem = n - i;
    if (rem == 1) {
        uint32_t v = static_cast<uint32_t>(bytes[i]) << 16;
        out.push_back(kAlphabet[(v >> 18) & 0x3F]);
        out.push_back(kAlphabet[(v >> 12) & 0x3F]);
        out.push_back('=');
        out.push_back('=');
    } else if (rem == 2) {
        uint32_t v = (static_cast<uint32_t>(bytes[i])     << 16) |
                     (static_cast<uint32_t>(bytes[i + 1]) <<  8);
        out.push_back(kAlphabet[(v >> 18) & 0x3F]);
        out.push_back(kAlphabet[(v >> 12) & 0x3F]);
        out.push_back(kAlphabet[(v >>  6) & 0x3F]);
        out.push_back('=');
    }
    return out;
}

}  // namespace wn_steam
