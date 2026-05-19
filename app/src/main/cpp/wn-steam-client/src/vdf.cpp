#include "wn_steam/vdf.h"

#include <algorithm>
#include <cstring>
#include <cctype>
#include <charconv>

namespace wn_steam::vdf {

namespace {

constexpr uint8_t kTypeNone        = 0x00;
constexpr uint8_t kTypeString      = 0x01;
constexpr uint8_t kTypeInt32       = 0x02;
constexpr uint8_t kTypeFloat32     = 0x03;
constexpr uint8_t kTypePointer     = 0x04;
constexpr uint8_t kTypeWideString  = 0x05;
constexpr uint8_t kTypeColor       = 0x06;
constexpr uint8_t kTypeUInt64      = 0x07;
constexpr uint8_t kTypeEnd         = 0x08;
constexpr uint8_t kTypeInt64       = 0x09;
constexpr uint8_t kTypeEndAlt      = 0x0B;

[[nodiscard]] bool iequals(std::string_view a, std::string_view b) noexcept {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); ++i) {
        if (std::tolower(static_cast<unsigned char>(a[i])) !=
            std::tolower(static_cast<unsigned char>(b[i]))) {
            return false;
        }
    }
    return true;
}

// Walking cursor over the binary stream. Bounds-checked: any out-of-range
// read sets `ok_ = false` and returns a zero/empty value so the caller can
// drop the partial result.
class Cursor {
public:
    explicit Cursor(std::span<const uint8_t> buf) noexcept : buf_(buf) {}

    [[nodiscard]] bool ok()  const noexcept { return ok_; }
    [[nodiscard]] bool eof() const noexcept { return pos_ >= buf_.size(); }

    [[nodiscard]] uint8_t read_u8() noexcept {
        if (pos_ + 1 > buf_.size()) { ok_ = false; return 0; }
        return buf_[pos_++];
    }
    [[nodiscard]] uint32_t read_u32_le() noexcept {
        if (pos_ + 4 > buf_.size()) { ok_ = false; return 0; }
        uint32_t v = static_cast<uint32_t>(buf_[pos_])
                   | (static_cast<uint32_t>(buf_[pos_ + 1]) << 8)
                   | (static_cast<uint32_t>(buf_[pos_ + 2]) << 16)
                   | (static_cast<uint32_t>(buf_[pos_ + 3]) << 24);
        pos_ += 4;
        return v;
    }
    [[nodiscard]] uint64_t read_u64_le() noexcept {
        uint64_t lo = read_u32_le();
        uint64_t hi = read_u32_le();
        return lo | (hi << 32);
    }
    [[nodiscard]] float read_f32_le() noexcept {
        uint32_t bits = read_u32_le();
        float out;
        std::memcpy(&out, &bits, sizeof(out));
        return out;
    }
    [[nodiscard]] std::string read_cstring() noexcept {
        std::string out;
        while (pos_ < buf_.size()) {
            uint8_t b = buf_[pos_++];
            if (b == 0) return out;
            out.push_back(static_cast<char>(b));
        }
        ok_ = false;
        return out;
    }
    [[nodiscard]] std::u16string read_wide_cstring() noexcept {
        std::u16string out;
        while (pos_ + 1 < buf_.size()) {
            uint16_t u = static_cast<uint16_t>(buf_[pos_])
                       | (static_cast<uint16_t>(buf_[pos_ + 1]) << 8);
            pos_ += 2;
            if (u == 0) return out;
            out.push_back(static_cast<char16_t>(u));
        }
        ok_ = false;
        return out;
    }
    void skip(size_t n) noexcept {
        if (pos_ + n > buf_.size()) { ok_ = false; return; }
        pos_ += n;
    }

private:
    std::span<const uint8_t> buf_;
    size_t                   pos_ = 0;
    bool                     ok_  = true;
};

// Parse a single record's value (type-tag already consumed). The key has
// also already been read. On a None tag the caller passes a freshly created
// node so children get appended directly.
[[nodiscard]] bool parse_value(Cursor& c, uint8_t type, KVNode& node) {
    switch (type) {
        case kTypeNone: {
            // Subobject — recurse, appending children until we hit End.
            while (c.ok()) {
                uint8_t inner_type = c.read_u8();
                if (!c.ok()) return false;
                if (inner_type == kTypeEnd || inner_type == kTypeEndAlt) return true;
                auto child = std::make_unique<KVNode>(c.read_cstring());
                if (!c.ok()) return false;
                if (!parse_value(c, inner_type, *child)) return false;
                node.add_child(std::move(child));
            }
            return false;
        }
        case kTypeString:
            node.set_value(c.read_cstring());
            return c.ok();
        case kTypeInt32:
            node.set_value(static_cast<int32_t>(c.read_u32_le()));
            return c.ok();
        case kTypeFloat32:
            node.set_value(c.read_f32_le());
            return c.ok();
        case kTypePointer:
        case kTypeColor:
            node.set_value(c.read_u32_le());
            return c.ok();
        case kTypeWideString:
            node.set_value(c.read_wide_cstring());
            return c.ok();
        case kTypeUInt64:
            node.set_value(c.read_u64_le());
            return c.ok();
        case kTypeInt64:
            node.set_value(static_cast<int64_t>(c.read_u64_le()));
            return c.ok();
        default:
            // Unknown type — corrupt or version we don't yet understand.
            return false;
    }
}

}  // namespace

const KVNode* KVNode::child(std::string_view key) const noexcept {
    for (const auto& c : children_) {
        if (iequals(c->name(), key)) return c.get();
    }
    return nullptr;
}

std::string KVNode::as_string(std::string_view fallback) const {
    return std::visit(
        [&fallback](auto&& v) -> std::string {
            using T = std::decay_t<decltype(v)>;
            if constexpr (std::is_same_v<T, std::string>)        return v;
            else if constexpr (std::is_same_v<T, int32_t>)       return std::to_string(v);
            else if constexpr (std::is_same_v<T, int64_t>)       return std::to_string(v);
            else if constexpr (std::is_same_v<T, uint32_t>)      return std::to_string(v);
            else if constexpr (std::is_same_v<T, uint64_t>)      return std::to_string(v);
            else if constexpr (std::is_same_v<T, float>)         return std::to_string(v);
            else                                                  return std::string(fallback);
        },
        value_);
}

int64_t KVNode::as_int(int64_t fallback) const noexcept {
    return std::visit(
        [fallback](auto&& v) -> int64_t {
            using T = std::decay_t<decltype(v)>;
            if constexpr (std::is_same_v<T, int32_t>)  return v;
            else if constexpr (std::is_same_v<T, int64_t>)  return v;
            else if constexpr (std::is_same_v<T, uint32_t>) return static_cast<int64_t>(v);
            else if constexpr (std::is_same_v<T, uint64_t>) return static_cast<int64_t>(v);
            else if constexpr (std::is_same_v<T, std::string>) {
                int64_t out = 0;
                auto* first = v.data();
                auto* last  = v.data() + v.size();
                auto r = std::from_chars(first, last, out);
                return (r.ec == std::errc{}) ? out : fallback;
            }
            else return fallback;
        },
        value_);
}

uint64_t KVNode::as_uint(uint64_t fallback) const noexcept {
    return std::visit(
        [fallback](auto&& v) -> uint64_t {
            using T = std::decay_t<decltype(v)>;
            if constexpr (std::is_same_v<T, uint32_t>) return v;
            else if constexpr (std::is_same_v<T, uint64_t>) return v;
            else if constexpr (std::is_same_v<T, int32_t>)  return static_cast<uint64_t>(v);
            else if constexpr (std::is_same_v<T, int64_t>)  return static_cast<uint64_t>(v);
            else if constexpr (std::is_same_v<T, std::string>) {
                uint64_t out = 0;
                auto* first = v.data();
                auto* last  = v.data() + v.size();
                auto r = std::from_chars(first, last, out);
                return (r.ec == std::errc{}) ? out : fallback;
            }
            else return fallback;
        },
        value_);
}

bool KVNode::as_bool(bool fallback) const noexcept {
    return std::visit(
        [fallback](auto&& v) -> bool {
            using T = std::decay_t<decltype(v)>;
            if constexpr (std::is_same_v<T, int32_t>  || std::is_same_v<T, int64_t> ||
                          std::is_same_v<T, uint32_t> || std::is_same_v<T, uint64_t>) {
                return v != 0;
            } else if constexpr (std::is_same_v<T, std::string>) {
                if (v == "1" || iequals(v, "true") || iequals(v, "yes")) return true;
                if (v == "0" || iequals(v, "false") || iequals(v, "no")) return false;
                return fallback;
            } else {
                return fallback;
            }
        },
        value_);
}

// =====================================================================
// Text VDF
// =====================================================================
namespace {

struct TextCursor {
    std::span<const uint8_t> buf;
    size_t pos = 0;
    bool   ok  = true;

    [[nodiscard]] bool eof() const noexcept { return pos >= buf.size(); }
    [[nodiscard]] char peek() const noexcept {
        return pos < buf.size() ? static_cast<char>(buf[pos]) : '\0';
    }
    char next() noexcept {
        return pos < buf.size() ? static_cast<char>(buf[pos++]) : '\0';
    }
    void advance() noexcept { if (pos < buf.size()) ++pos; }

    void skip_ws_and_comments() noexcept {
        while (pos < buf.size()) {
            char c = static_cast<char>(buf[pos]);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { ++pos; continue; }
            // Line comment: // ... \n
            if (c == '/' && pos + 1 < buf.size() && buf[pos + 1] == '/') {
                pos += 2;
                while (pos < buf.size() && buf[pos] != '\n') ++pos;
                continue;
            }
            // Block comment: /* ... */
            if (c == '/' && pos + 1 < buf.size() && buf[pos + 1] == '*') {
                pos += 2;
                while (pos + 1 < buf.size() &&
                       !(buf[pos] == '*' && buf[pos + 1] == '/')) {
                    ++pos;
                }
                pos = std::min(buf.size(), pos + 2);
                continue;
            }
            break;
        }
    }

    // Read one token: either a quoted string (supporting common backslash
    // escapes \n \t \r \\ \") or an unquoted identifier (continues until
    // whitespace, brace, or quote). Returns nullopt at EOF.
    [[nodiscard]] std::optional<std::string> read_token() noexcept {
        skip_ws_and_comments();
        if (eof()) return std::nullopt;
        if (peek() == '"') {
            advance();
            std::string out;
            while (!eof()) {
                char c = next();
                if (c == '"') return out;
                if (c == '\\' && !eof()) {
                    char esc = next();
                    switch (esc) {
                        case 'n':  out.push_back('\n'); break;
                        case 't':  out.push_back('\t'); break;
                        case 'r':  out.push_back('\r'); break;
                        case '\\': out.push_back('\\'); break;
                        case '"':  out.push_back('"');  break;
                        default:   out.push_back(esc);  break;
                    }
                } else {
                    out.push_back(c);
                }
            }
            ok = false;  // unterminated string
            return std::nullopt;
        }
        std::string out;
        while (!eof()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
                c == '{' || c == '}' || c == '"') break;
            out.push_back(next());
        }
        if (out.empty()) return std::nullopt;
        return out;
    }
};

// Parse the body of an object until `}` or EOF.
[[nodiscard]] bool parse_text_object(TextCursor& c, KVNode& parent) noexcept {
    while (true) {
        c.skip_ws_and_comments();
        if (c.eof()) return true;
        if (c.peek() == '}') { c.advance(); return true; }
        auto key = c.read_token();
        if (!key) return c.ok;
        c.skip_ws_and_comments();
        if (c.eof()) return false;
        auto child = std::make_unique<KVNode>(std::move(*key));
        if (c.peek() == '{') {
            c.advance();
            if (!parse_text_object(c, *child)) return false;
        } else {
            auto val = c.read_token();
            if (!val) return false;
            child->set_value(std::move(*val));
        }
        parent.add_child(std::move(child));
    }
}

}  // namespace

KVNodePtr parse_text(std::span<const uint8_t> body) noexcept {
    TextCursor c{body, 0, true};
    c.skip_ws_and_comments();
    if (c.eof()) return nullptr;
    auto key = c.read_token();
    if (!key) return nullptr;
    c.skip_ws_and_comments();
    if (c.peek() != '{') return nullptr;
    c.advance();
    auto root = std::make_unique<KVNode>(std::move(*key));
    if (!parse_text_object(c, *root)) return nullptr;
    return root;
}

KVNodePtr parse_auto(std::span<const uint8_t> body) noexcept {
    if (body.empty()) return nullptr;
    size_t i = 0;
    while (i < body.size() &&
           (body[i] == ' ' || body[i] == '\t' ||
            body[i] == '\n' || body[i] == '\r')) ++i;
    if (i >= body.size()) return nullptr;
    if (body[i] == '"') return parse_text(body);
    return parse_binary(body);
}

KVNodePtr parse_binary(std::span<const uint8_t> body) noexcept {
    if (body.empty()) return nullptr;
    Cursor c(body);
    uint8_t type = c.read_u8();
    if (!c.ok()) return nullptr;
    auto root = std::make_unique<KVNode>(c.read_cstring());
    if (!c.ok()) return nullptr;
    if (!parse_value(c, type, *root)) return nullptr;
    return root;
}

KVNodePtr parse_binary_package(std::span<const uint8_t> body,
                               uint32_t* out_packageid_prefix) noexcept {
    if (body.size() < 4) return nullptr;
    uint32_t prefix = static_cast<uint32_t>(body[0])
                    | (static_cast<uint32_t>(body[1]) << 8)
                    | (static_cast<uint32_t>(body[2]) << 16)
                    | (static_cast<uint32_t>(body[3]) << 24);
    if (out_packageid_prefix) *out_packageid_prefix = prefix;
    return parse_binary(body.subspan(4));
}

}  // namespace wn_steam::vdf
