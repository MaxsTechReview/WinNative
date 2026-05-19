#include "wn_steam/proto_wire.h"

namespace wn_steam::proto {

// ---------------------------------------------------------------------------
// Writer
// ---------------------------------------------------------------------------

void Writer::varint(uint64_t v) {
    while (v >= 0x80) {
        out_.push_back(static_cast<uint8_t>(v) | 0x80u);
        v >>= 7;
    }
    out_.push_back(static_cast<uint8_t>(v));
}

// ---------------------------------------------------------------------------
// Reader
// ---------------------------------------------------------------------------

std::optional<uint64_t> Reader::varint() noexcept {
    uint64_t result = 0;
    for (size_t i = 0; i < kMaxVarintBytes; ++i) {
        if (pos_ >= buf_.size()) { ok_ = false; return std::nullopt; }
        const uint8_t b = buf_[pos_++];
        result |= static_cast<uint64_t>(b & 0x7Fu) << (i * 7);
        if ((b & 0x80u) == 0) return result;
    }
    // 10 bytes read with continuation still set — malformed.
    ok_ = false;
    return std::nullopt;
}

std::optional<Reader::Tag> Reader::next_tag() noexcept {
    if (eof()) return std::nullopt;
    auto t = varint();
    if (!t) return std::nullopt;
    const uint32_t raw = static_cast<uint32_t>(*t);
    const auto wt = static_cast<WireType>(raw & 0x7u);
    const int    fn = static_cast<int>(raw >> 3);
    if (wt == WireType::StartGroup || wt == WireType::EndGroup) {
        // Group encoding has been deprecated since proto2 days; Steam
        // protos do not use it. Reject so we don't silently drop bytes.
        ok_ = false;
        return std::nullopt;
    }
    if (fn <= 0) {
        ok_ = false;
        return std::nullopt;
    }
    return Tag{fn, wt};
}

bool Reader::skip(WireType wt) noexcept {
    switch (wt) {
        case WireType::Varint: {
            auto v = varint();
            return v.has_value();
        }
        case WireType::Fixed64: {
            if (pos_ + 8 > buf_.size()) { ok_ = false; return false; }
            pos_ += 8;
            return true;
        }
        case WireType::Fixed32: {
            if (pos_ + 4 > buf_.size()) { ok_ = false; return false; }
            pos_ += 4;
            return true;
        }
        case WireType::LengthDelimited: {
            auto len = varint();
            if (!len) return false;
            // Overflow-safe bounds check: *len is an untrusted 64-bit varint,
            // so `pos_ + *len` can wrap. pos_ <= buf_.size() is an invariant
            // (every advance is bounds-checked), so the subtraction is safe.
            if (*len > buf_.size() - pos_) { ok_ = false; return false; }
            pos_ += *len;
            return true;
        }
        case WireType::StartGroup:
        case WireType::EndGroup:
        default:
            ok_ = false;
            return false;
    }
}

std::optional<uint32_t> Reader::fixed32() noexcept {
    if (pos_ + 4 > buf_.size()) { ok_ = false; return std::nullopt; }
    uint32_t v = 0;
    v |= static_cast<uint32_t>(buf_[pos_ + 0]);
    v |= static_cast<uint32_t>(buf_[pos_ + 1]) << 8;
    v |= static_cast<uint32_t>(buf_[pos_ + 2]) << 16;
    v |= static_cast<uint32_t>(buf_[pos_ + 3]) << 24;
    pos_ += 4;
    return v;
}

std::optional<uint64_t> Reader::fixed64() noexcept {
    if (pos_ + 8 > buf_.size()) { ok_ = false; return std::nullopt; }
    uint64_t v = 0;
    for (int i = 0; i < 8; ++i) {
        v |= static_cast<uint64_t>(buf_[pos_ + i]) << (i * 8);
    }
    pos_ += 8;
    return v;
}

std::optional<std::span<const uint8_t>> Reader::bytes() noexcept {
    auto len = varint();
    if (!len) return std::nullopt;
    // Overflow-safe bounds check (see skip()): *len is an untrusted 64-bit
    // varint; `pos_ + *len` can wrap. pos_ <= buf_.size() always holds.
    if (*len > buf_.size() - pos_) { ok_ = false; return std::nullopt; }
    auto out = buf_.subspan(pos_, *len);
    pos_ += *len;
    return out;
}

}  // namespace wn_steam::proto
