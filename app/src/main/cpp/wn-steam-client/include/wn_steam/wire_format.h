#pragma once

#include <cstdint>
#include <cstring>
#include <span>
#include <vector>

// Little-endian byte helpers for the Steam wire protocol. All multibyte
// integers on the wire are little-endian, matching SteamKit2's BinaryReader/
// BinaryWriter defaults on x86/arm64 hosts.

namespace wn_steam::wire {

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

[[nodiscard]] inline uint16_t read_u16_le(const uint8_t* p) noexcept {
    return static_cast<uint16_t>(p[0]) |
           (static_cast<uint16_t>(p[1]) << 8);
}

[[nodiscard]] inline uint32_t read_u32_le(const uint8_t* p) noexcept {
    return  static_cast<uint32_t>(p[0])         |
           (static_cast<uint32_t>(p[1]) <<  8)  |
           (static_cast<uint32_t>(p[2]) << 16)  |
           (static_cast<uint32_t>(p[3]) << 24);
}

[[nodiscard]] inline uint64_t read_u64_le(const uint8_t* p) noexcept {
    return  static_cast<uint64_t>(p[0])         |
           (static_cast<uint64_t>(p[1]) <<  8)  |
           (static_cast<uint64_t>(p[2]) << 16)  |
           (static_cast<uint64_t>(p[3]) << 24)  |
           (static_cast<uint64_t>(p[4]) << 32)  |
           (static_cast<uint64_t>(p[5]) << 40)  |
           (static_cast<uint64_t>(p[6]) << 48)  |
           (static_cast<uint64_t>(p[7]) << 56);
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------

inline void write_u16_le(uint8_t* p, uint16_t v) noexcept {
    p[0] = static_cast<uint8_t>(v);
    p[1] = static_cast<uint8_t>(v >> 8);
}

inline void write_u32_le(uint8_t* p, uint32_t v) noexcept {
    p[0] = static_cast<uint8_t>(v);
    p[1] = static_cast<uint8_t>(v >>  8);
    p[2] = static_cast<uint8_t>(v >> 16);
    p[3] = static_cast<uint8_t>(v >> 24);
}

inline void write_u64_le(uint8_t* p, uint64_t v) noexcept {
    p[0] = static_cast<uint8_t>(v);
    p[1] = static_cast<uint8_t>(v >>  8);
    p[2] = static_cast<uint8_t>(v >> 16);
    p[3] = static_cast<uint8_t>(v >> 24);
    p[4] = static_cast<uint8_t>(v >> 32);
    p[5] = static_cast<uint8_t>(v >> 40);
    p[6] = static_cast<uint8_t>(v >> 48);
    p[7] = static_cast<uint8_t>(v >> 56);
}

// ---------------------------------------------------------------------------
// Bounded reader — bumps a cursor with bounds checking
// ---------------------------------------------------------------------------

class Reader {
public:
    explicit Reader(std::span<const uint8_t> buf) noexcept
        : buf_(buf), pos_(0) {}

    [[nodiscard]] bool ok() const noexcept { return ok_; }
    [[nodiscard]] size_t position() const noexcept { return pos_; }
    [[nodiscard]] size_t remaining() const noexcept {
        return pos_ <= buf_.size() ? buf_.size() - pos_ : 0;
    }

    [[nodiscard]] uint16_t u16() noexcept {
        if (!check(2)) return 0;
        uint16_t v = read_u16_le(buf_.data() + pos_);
        pos_ += 2;
        return v;
    }

    [[nodiscard]] uint32_t u32() noexcept {
        if (!check(4)) return 0;
        uint32_t v = read_u32_le(buf_.data() + pos_);
        pos_ += 4;
        return v;
    }

    [[nodiscard]] uint64_t u64() noexcept {
        if (!check(8)) return 0;
        uint64_t v = read_u64_le(buf_.data() + pos_);
        pos_ += 8;
        return v;
    }

    // Returns a sub-span of `n` bytes from the current position, or an
    // empty span if there are not enough bytes left (also flips ok() false).
    // The returned span aliases the Reader's input buffer — copy the bytes
    // before the input goes out of scope if you need them to outlive it.
    [[nodiscard]] std::span<const uint8_t> bytes(size_t n) noexcept {
        if (!check(n)) return {};
        auto out = buf_.subspan(pos_, n);
        pos_ += n;
        return out;
    }

private:
    bool check(size_t n) noexcept {
        if (!ok_ || remaining() < n) { ok_ = false; return false; }
        return true;
    }

    std::span<const uint8_t> buf_;
    size_t pos_;
    bool ok_ = true;
};

// ---------------------------------------------------------------------------
// Bounded writer — appends to a vector
// ---------------------------------------------------------------------------

class Writer {
public:
    explicit Writer(std::vector<uint8_t>& out) noexcept : out_(out) {}

    void u16(uint16_t v) {
        size_t off = out_.size();
        out_.resize(off + 2);
        write_u16_le(out_.data() + off, v);
    }
    void u32(uint32_t v) {
        size_t off = out_.size();
        out_.resize(off + 4);
        write_u32_le(out_.data() + off, v);
    }
    void u64(uint64_t v) {
        size_t off = out_.size();
        out_.resize(off + 8);
        write_u64_le(out_.data() + off, v);
    }
    void bytes(std::span<const uint8_t> b) {
        out_.insert(out_.end(), b.begin(), b.end());
    }

private:
    std::vector<uint8_t>& out_;
};

}  // namespace wn_steam::wire
