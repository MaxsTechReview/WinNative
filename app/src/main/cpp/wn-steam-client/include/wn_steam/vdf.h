#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <span>
#include <string>
#include <variant>
#include <vector>

// Binary VDF (KeyValues binary form) — Valve's nested key/value tree as
// stored inside PICS product-info buffers and depot manifests.
//
// Wire format: a recursive stream of typed records.
//   <type:u8> <key:cstr> <value>
//   …repeated until <type=0x08 End> closes the current subobject…
//
// Type tags (the subset Steam actually uses on PICS):
//   0x00  None       (subobject — recurse until 0x08)
//   0x01  String     (null-terminated UTF-8)
//   0x02  Int32
//   0x03  Float32
//   0x04  Pointer    (uint32 — rare; treated as Uint32)
//   0x05  WideString (UTF-16 LE, null-terminated)
//   0x06  Color      (uint32 RGBA)
//   0x07  UInt64
//   0x08  End        (subobject terminator — no key, no value)
//   0x09  Int64
//   0x0B  AlternateEnd (legacy terminator seen on some older streams)
//
// PICS specifics handled by the wrappers in cm_client/pb:
//   • App-info buffer starts directly at the root subobject (root key is
//     the appid as ASCII decimal — first byte is therefore 0x00 None).
//   • Package-info buffer has a 4-byte LE packageid PREFIX before the
//     KeyValues stream. Caller must strip the prefix or use the dedicated
//     parse_binary_package() helper below.

namespace wn_steam::vdf {

class KVNode;
using KVNodePtr = std::unique_ptr<KVNode>;

class KVNode {
public:
    using Children = std::vector<KVNodePtr>;
    using Value    = std::variant<std::monostate,    // None (subobject — has children_)
                                  std::string,        // String
                                  int32_t,            // Int32
                                  float,              // Float32
                                  uint32_t,           // Pointer / Color
                                  std::u16string,     // WideString (UTF-16 LE)
                                  uint64_t,           // UInt64
                                  int64_t>;           // Int64

    KVNode() = default;
    explicit KVNode(std::string name) : name_(std::move(name)) {}

    [[nodiscard]] const std::string& name()  const noexcept { return name_; }
    [[nodiscard]] const Value&       value() const noexcept { return value_; }
    [[nodiscard]] const Children&    children() const noexcept { return children_; }

    [[nodiscard]] bool is_object() const noexcept {
        return std::holds_alternative<std::monostate>(value_);
    }

    // Case-INSENSITIVE child lookup (Steam stores keys as e.g. "Common" or
    // "common" interchangeably across messages). Returns nullptr if absent.
    [[nodiscard]] const KVNode* child(std::string_view key) const noexcept;
    [[nodiscard]] const KVNode* operator[](std::string_view key) const noexcept {
        return child(key);
    }

    // Value-extraction helpers — return defaults if the node holds a
    // different type. String accessors coerce numeric types via to_string.
    [[nodiscard]] std::string  as_string(std::string_view fallback = {}) const;
    [[nodiscard]] int64_t      as_int(int64_t fallback = 0) const noexcept;
    [[nodiscard]] uint64_t     as_uint(uint64_t fallback = 0) const noexcept;
    [[nodiscard]] bool         as_bool(bool fallback = false) const noexcept;

    // Mutators used by the parser.
    void set_name(std::string n) { name_ = std::move(n); }
    void set_value(Value v)      { value_ = std::move(v); }
    void add_child(KVNodePtr c)  { children_.push_back(std::move(c)); }

private:
    std::string name_;
    Value       value_;
    Children    children_;
};

// Parse a binary VDF stream starting at the root subobject. Returns nullptr
// on malformed input. The returned node's `name` is the key of the root
// record (e.g. "12345" for an app-info buffer where 12345 is the appid).
[[nodiscard]] KVNodePtr parse_binary(std::span<const uint8_t> body) noexcept;

// Parse a text VDF (the human-readable quoted-string form), e.g.
//     "appinfo"
//     {
//         "appid"  "578080"
//         "common" { "name" "PUBG: ..." "type" "Game" ... }
//     }
// PICS app-info buffers arrive in this form. Comments (// ... and /* ... */)
// are accepted and skipped.
[[nodiscard]] KVNodePtr parse_text(std::span<const uint8_t> body) noexcept;

// Sniff the buffer's first non-whitespace byte and dispatch to parse_text
// (when it's `"`) or parse_binary (otherwise — a valid binary type tag is
// 0x00-0x09 or 0x0B). Use this when you don't know in advance which form
// the server will return — PICS app responses are text, package responses
// are binary, depot manifests are binary, etc.
[[nodiscard]] KVNodePtr parse_auto(std::span<const uint8_t> body) noexcept;

// Parse a PICS package-info buffer (which carries a 4-byte LE packageid
// prefix before the KeyValues stream). Returns the root KVNode with
// name = the parsed package's name field, plus the extracted packageid
// passed back via the out-param.
[[nodiscard]] KVNodePtr parse_binary_package(std::span<const uint8_t> body,
                                             uint32_t* out_packageid_prefix = nullptr) noexcept;

}  // namespace wn_steam::vdf
