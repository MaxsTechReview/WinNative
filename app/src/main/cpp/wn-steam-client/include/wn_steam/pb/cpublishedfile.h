#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_publishedfile.steamclient.proto
// — the `PublishedFile` unified service. We use exactly one method:
//   PublishedFile.GetUserFiles#1
//       With type="mysubscriptions" it returns the caller's subscribed Steam
//       Workshop / UGC items for an app, paged, with the full
//       PublishedFileDetails inline — no separate GetDetails round-trip needed.
//
// Field numbers verified against SteamDatabase/SteamTracking
//   Protobufs/steammessages_publishedfile.steamclient.proto

namespace wn_steam::pb {

// PublishedFile.GetUserFiles#1 request (subset — only the fields we set).
//   1  fixed64 steamid
//   2  uint32  appid
//   4  uint32  page          (1-based)
//   5  uint32  numperpage
//   6  string  type          ("mysubscriptions")
//   14 uint32  filetype
struct CPublishedFile_GetUserFiles_Request {
    uint64_t    steamid    = 0;
    uint32_t    appid      = 0;
    uint32_t    page       = 1;
    uint32_t    numperpage = 1;
    std::string type;
    uint32_t    filetype   = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// One subscribed Workshop item — subset of PublishedFileDetails.
//   1  uint32  result
//   2  uint64  publishedfileid
//   5  uint32  consumer_appid
//   7  string  filename
//   8  uint64  file_size
//   10 string  file_url        (legacy web-hosted content, when present)
//   11 string  preview_url     (thumbnail image)
//   14 fixed64 hcontent_file   (content manifest GID for depot-backed items)
//   16 string  title
//   20 uint32  time_updated
struct PublishedFileDetails {
    uint32_t    result          = 0;
    uint64_t    publishedfileid = 0;
    uint32_t    consumer_appid  = 0;
    std::string filename;
    uint64_t    file_size       = 0;
    std::string file_url;
    std::string preview_url;
    uint64_t    hcontent_file   = 0;
    std::string title;
    uint32_t    time_updated    = 0;

    [[nodiscard]] static std::optional<PublishedFileDetails>
    parse(std::span<const uint8_t> body) noexcept;
};

// PublishedFile.GetUserFiles#1 response.
//   1  uint32  total
//   2  uint32  startindex
//   3  repeated PublishedFileDetails publishedfiledetails
struct CPublishedFile_GetUserFiles_Response {
    uint32_t                          total      = 0;
    uint32_t                          startindex = 0;
    std::vector<PublishedFileDetails> publishedfiledetails;

    [[nodiscard]] static std::optional<CPublishedFile_GetUserFiles_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
