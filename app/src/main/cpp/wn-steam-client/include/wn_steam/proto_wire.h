#pragma once

// Hand-rolled protobuf wire-format primitives for the bounded Phase-2 set
// of messages (auth flow, ClientLogon, heartbeat). Scales to ~30 message
// types comfortably; beyond that we'll likely swap to libprotobuf-lite.
//
// Wire format reference: https://protobuf.dev/programming-guides/encoding/
//   tag           = (field_number << 3) | wire_type
//   wire types    = 0 varint, 1 fixed64, 2 length-delimited, 5 fixed32
//   varint        = LEB128 (low 7 bits per byte, MSB = continuation)
//   sint32/sint64 = ZigZag-encoded varint
//   string/bytes/sub-message = length-delimited
//
// Both writer and reader are byte-oriented and do not own buffers. The
// caller manages the output vector. Endianness on the wire: protobuf is
// always little-endian for fixed32/fixed64 fields.

#include <cstdint>
#include <cstddef>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

namespace wn_steam::proto {

enum class WireType : uint8_t {
    Varint           = 0,
    Fixed64          = 1,
    LengthDelimited  = 2,
    StartGroup       = 3,  // deprecated by protobuf; we error on it
    EndGroup         = 4,  // deprecated
    Fixed32          = 5,
};

// Maximum bytes a 64-bit varint can occupy: ceil(64/7) = 10.
constexpr size_t kMaxVarintBytes = 10;

inline constexpr uint64_t zigzag_encode_i64(int64_t v) noexcept {
    return (static_cast<uint64_t>(v) << 1) ^
           static_cast<uint64_t>(v >> 63);
}
inline constexpr int64_t zigzag_decode_i64(uint64_t v) noexcept {
    return static_cast<int64_t>((v >> 1) ^ -static_cast<int64_t>(v & 1));
}
inline constexpr uint32_t zigzag_encode_i32(int32_t v) noexcept {
    return (static_cast<uint32_t>(v) << 1) ^
           static_cast<uint32_t>(v >> 31);
}
inline constexpr int32_t zigzag_decode_i32(uint32_t v) noexcept {
    return static_cast<int32_t>((v >> 1) ^ -static_cast<int32_t>(v & 1));
}

inline constexpr uint32_t make_tag(int field_number, WireType wt) noexcept {
    return (static_cast<uint32_t>(field_number) << 3) | static_cast<uint32_t>(wt);
}

// ---------------------------------------------------------------------------
// Writer
// ---------------------------------------------------------------------------

class Writer {
public:
    explicit Writer(std::vector<uint8_t>& out) noexcept : out_(out) {}

    // Low-level: append a varint to the buffer.
    void varint(uint64_t v);

    // Tag emit.
    void tag(int field_number, WireType wt) { varint(make_tag(field_number, wt)); }

    // Typed-field helpers. All skip the field if value is the proto3
    // default (0 / empty) — callers that explicitly want to send a zero
    // should use the `*_force` variants below.
    void uint32_field(int field_number, uint32_t v) {
        if (v == 0) return;
        tag(field_number, WireType::Varint);
        varint(static_cast<uint64_t>(v));
    }
    void uint64_field(int field_number, uint64_t v) {
        if (v == 0) return;
        tag(field_number, WireType::Varint);
        varint(v);
    }
    void int32_field(int field_number, int32_t v) {
        if (v == 0) return;
        tag(field_number, WireType::Varint);
        // proto encodes int32 as a 10-byte varint when negative (sign-extend).
        varint(static_cast<uint64_t>(static_cast<int64_t>(v)));
    }
    void int64_field(int field_number, int64_t v) {
        if (v == 0) return;
        tag(field_number, WireType::Varint);
        varint(static_cast<uint64_t>(v));
    }
    void bool_field(int field_number, bool v) {
        if (!v) return;
        tag(field_number, WireType::Varint);
        varint(1);
    }
    void fixed32_field(int field_number, uint32_t v) {
        if (v == 0) return;
        tag(field_number, WireType::Fixed32);
        out_.push_back(static_cast<uint8_t>(v));
        out_.push_back(static_cast<uint8_t>(v >> 8));
        out_.push_back(static_cast<uint8_t>(v >> 16));
        out_.push_back(static_cast<uint8_t>(v >> 24));
    }
    void fixed64_field(int field_number, uint64_t v) {
        if (v == 0) return;
        tag(field_number, WireType::Fixed64);
        for (int i = 0; i < 8; ++i) out_.push_back(static_cast<uint8_t>(v >> (i * 8)));
    }
    void string_field(int field_number, std::string_view s) {
        if (s.empty()) return;
        tag(field_number, WireType::LengthDelimited);
        varint(s.size());
        out_.insert(out_.end(), s.begin(), s.end());
    }
    void bytes_field(int field_number, std::span<const uint8_t> b) {
        if (b.empty()) return;
        tag(field_number, WireType::LengthDelimited);
        varint(b.size());
        out_.insert(out_.end(), b.begin(), b.end());
    }
    // Embed a nested message (already-serialized body) under one tag.
    void submessage_field(int field_number, std::span<const uint8_t> body) {
        tag(field_number, WireType::LengthDelimited);
        varint(body.size());
        out_.insert(out_.end(), body.begin(), body.end());
    }

    // Force-emit variants for required-on-the-wire fields that may legitimately
    // be zero (rare in proto3 but appears in some Steam headers).
    void uint32_field_force(int field_number, uint32_t v) {
        tag(field_number, WireType::Varint);
        varint(static_cast<uint64_t>(v));
    }
    void bool_field_force(int field_number, bool v) {
        tag(field_number, WireType::Varint);
        varint(v ? 1 : 0);
    }

private:
    std::vector<uint8_t>& out_;
};

// ---------------------------------------------------------------------------
// Reader — single-pass tag-by-tag walker
// ---------------------------------------------------------------------------

class Reader {
public:
    explicit Reader(std::span<const uint8_t> buf) noexcept : buf_(buf), pos_(0) {}

    [[nodiscard]] bool ok() const noexcept { return ok_; }
    [[nodiscard]] bool eof() const noexcept { return pos_ >= buf_.size(); }
    [[nodiscard]] size_t position() const noexcept { return pos_; }

    // Read the next tag. Returns nullopt at EOF or on parse error.
    // Field number and wire type are returned together.
    struct Tag {
        int      field_number;
        WireType wire_type;
    };
    [[nodiscard]] std::optional<Tag> next_tag() noexcept;

    // Decode a varint at the cursor. Advances on success.
    [[nodiscard]] std::optional<uint64_t> varint() noexcept;

    // Skip a field of the given wire type, advancing the cursor past it.
    [[nodiscard]] bool skip(WireType wt) noexcept;

    // Typed reads. Each MUST be called after a matching next_tag().
    [[nodiscard]] std::optional<uint32_t> u32() noexcept {
        auto v = varint();
        return v ? std::optional<uint32_t>(static_cast<uint32_t>(*v)) : std::nullopt;
    }
    [[nodiscard]] std::optional<uint64_t> u64() noexcept { return varint(); }
    [[nodiscard]] std::optional<int32_t>  i32() noexcept {
        auto v = varint();
        return v ? std::optional<int32_t>(static_cast<int32_t>(*v)) : std::nullopt;
    }
    [[nodiscard]] std::optional<int64_t>  i64() noexcept {
        auto v = varint();
        return v ? std::optional<int64_t>(static_cast<int64_t>(*v)) : std::nullopt;
    }
    [[nodiscard]] std::optional<bool>     boolean() noexcept {
        auto v = varint();
        return v ? std::optional<bool>(*v != 0) : std::nullopt;
    }
    [[nodiscard]] std::optional<uint32_t> fixed32() noexcept;
    [[nodiscard]] std::optional<uint64_t> fixed64() noexcept;
    // Length-delimited reads return a sub-span aliasing the underlying buffer.
    [[nodiscard]] std::optional<std::span<const uint8_t>> bytes() noexcept;
    [[nodiscard]] std::optional<std::string> string() noexcept {
        auto b = bytes();
        if (!b) return std::nullopt;
        return std::string(reinterpret_cast<const char*>(b->data()), b->size());
    }

private:
    std::span<const uint8_t> buf_;
    size_t pos_;
    bool   ok_ = true;
};

}  // namespace wn_steam::proto
