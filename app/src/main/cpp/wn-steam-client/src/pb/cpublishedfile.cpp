#include "wn_steam/pb/cpublishedfile.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

namespace {

// PublishedFileDetails is a large message (75+ fields) and only a handful are
// read here. Every case guards the tag's wire type before a typed read: a
// field whose wire type does not match the schema is skipped rather than read,
// so a single schema drift degrades gracefully instead of desyncing the
// cursor and corrupting the whole message.
using proto::WireType;

}  // namespace

std::vector<uint8_t> CPublishedFile_GetUserFiles_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.fixed64_field(1, steamid);
    w.uint32_field(2, appid);
    w.uint32_field(4, page);
    w.uint32_field(5, numperpage);
    w.string_field(6, type);
    w.uint32_field(14, filetype);
    return out;
}

std::optional<PublishedFileDetails>
PublishedFileDetails::parse(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    PublishedFileDetails m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        bool handled = false;
        switch (t->field_number) {
            case 1:  // result (varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u32(); v) m.result = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 2:  // publishedfileid (uint64 varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u64(); v) m.publishedfileid = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 5:  // consumer_appid (varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u32(); v) m.consumer_appid = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 7:  // filename (string)
                if (t->wire_type == WireType::LengthDelimited) {
                    if (auto v = r.string(); v) m.filename = std::move(*v);
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 8:  // file_size (uint64 varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u64(); v) m.file_size = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 10:  // file_url (string)
                if (t->wire_type == WireType::LengthDelimited) {
                    if (auto v = r.string(); v) m.file_url = std::move(*v);
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 11:  // preview_url (string)
                if (t->wire_type == WireType::LengthDelimited) {
                    if (auto v = r.string(); v) m.preview_url = std::move(*v);
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 14:  // hcontent_file (fixed64)
                if (t->wire_type == WireType::Fixed64) {
                    if (auto v = r.fixed64(); v) m.hcontent_file = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 16:  // title (string)
                if (t->wire_type == WireType::LengthDelimited) {
                    if (auto v = r.string(); v) m.title = std::move(*v);
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 20:  // time_updated (varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u32(); v) m.time_updated = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            default:
                break;
        }
        if (!handled) {
            if (!r.skip(t->wire_type)) return std::nullopt;
        }
    }
    return m;
}

std::optional<CPublishedFile_GetUserFiles_Response>
CPublishedFile_GetUserFiles_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CPublishedFile_GetUserFiles_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        bool handled = false;
        switch (t->field_number) {
            case 1:  // total (varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u32(); v) m.total = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 2:  // startindex (varint)
                if (t->wire_type == WireType::Varint) {
                    if (auto v = r.u32(); v) m.startindex = *v;
                    else return std::nullopt;
                    handled = true;
                }
                break;
            case 3:  // publishedfiledetails (repeated message)
                if (t->wire_type == WireType::LengthDelimited) {
                    auto b = r.bytes();
                    if (!b) return std::nullopt;
                    auto d = PublishedFileDetails::parse(*b);
                    if (!d) return std::nullopt;
                    m.publishedfiledetails.push_back(std::move(*d));
                    handled = true;
                }
                break;
            default:
                break;
        }
        if (!handled) {
            if (!r.skip(t->wire_type)) return std::nullopt;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
