#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

namespace wn_steam {

// Mirrors steammessages_base.proto:CMsgProtoBufHeader. Only the fields
// our Phase 2 surface reads or writes are exposed — the proto has dozens
// more (transport metadata, A/B testing, etc.) that we skip on the way
// in and never emit on the way out. Field numbers below match the
// canonical .proto exactly so a future swap to libprotobuf-lite is a
// drop-in replacement.
//
// Field numbers (steammessages_base.proto):
//    1  fixed64 steamid
//    2  int32   client_sessionid
//    3  uint32  routing_appid
//    10 fixed64 jobid_source           default 0xFFFFFFFFFFFFFFFF
//    11 fixed64 jobid_target           default 0xFFFFFFFFFFFFFFFF
//    12 string  target_job_name        (Unified Messages: "Service.Method#1")
//    13 int32   seq_num
//    14 int32   eresult                default 2 (Invalid)
//    15 string  error_message
//    16 uint32  ip
//    17 uint32  auth_account_flags
//    18 uint32  token_source
//    19 bool    admin_spoofing_user
//    20 int32   transport_error
//    21 uint64  messageid
//    22 uint32  publisher_group_id
//    23 uint32  sysid
//    24 int64   trace_tag
//    25 uint32  webapi_key_id
//    26 bool    is_from_external_source
//    27 repeated uint32 forwarded_for_addresses_v6
//    28 uint32  ip_obfuscated
//    29 uint32  realm
//    30 int32   timeout_ms
//    32 string  debug_source
//    33 uint32  debug_source_string_index
//    34 uint64  token_id
//    35 group(?) routing_gc — deprecated, ignored
//    36 uint32  session_disposition

constexpr uint64_t kInvalidJobId = static_cast<uint64_t>(-1);

struct CMsgProtoBufHeader {
    uint64_t    steamid          = 0;
    int32_t     client_sessionid = 0;
    uint32_t    routing_appid    = 0;
    uint64_t    jobid_source     = kInvalidJobId;
    uint64_t    jobid_target     = kInvalidJobId;
    std::string target_job_name;       // for Unified Messages calls
    // Sentinel default: -1 (no valid EResult uses negative values). Lets
    // us distinguish "Steam didn't set eresult on the wire" (-1) from
    // "Steam sent eresult=2" (genuine Fail). Steam typically OMITS the
    // field on success — defaulting to a real EResult code is misleading
    // and was causing every service-method response to look like a failure.
    int32_t     eresult          = -1;
    std::string error_message;
    uint32_t    realm            = 0;
    uint64_t    messageid        = 0;
    uint64_t    token_id         = 0;

    void serialize(std::vector<uint8_t>& out) const;

    // Parses a CMsgProtoBufHeader from `bytes` (which must be exactly the
    // header sub-buffer, NOT a stream). Unknown fields are skipped.
    [[nodiscard]] static std::optional<CMsgProtoBufHeader>
    deserialize(std::span<const uint8_t> bytes) noexcept;
};

}  // namespace wn_steam
