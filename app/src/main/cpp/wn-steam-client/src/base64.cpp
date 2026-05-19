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

std::optional<std::vector<uint8_t>> base64_decode(std::string_view s) {
    // Map a base64 char to its 6-bit value; -1 = invalid, -2 = skip
    // (padding / whitespace). Accepts both standard (+/) and URL-safe (-_).
    auto value_of = [](char c) -> int {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+' || c == '-') return 62;
        if (c == '/' || c == '_') return 63;
        if (c == '=' || c == ' ' || c == '\t' || c == '\n' || c == '\r') return -2;
        return -1;
    };

    std::vector<uint8_t> out;
    out.reserve((s.size() / 4) * 3 + 3);
    uint32_t acc  = 0;
    int      bits = 0;
    for (char c : s) {
        int v = value_of(c);
        if (v == -2) continue;             // padding / whitespace
        if (v < 0)   return std::nullopt;  // invalid character
        acc  = (acc << 6) | static_cast<uint32_t>(v);
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out.push_back(static_cast<uint8_t>((acc >> bits) & 0xFF));
        }
    }
    return out;
}

}  // namespace wn_steam
