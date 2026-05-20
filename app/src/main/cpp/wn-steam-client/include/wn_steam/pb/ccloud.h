#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_cloud.steamclient.proto —
// the Cloud unified-service surface used by the Phase 6 cloud-save path.
//
// Modern Steam Cloud uses the `Cloud` service (unified messaging over
// ServiceMethodCallFromClient), NOT the legacy ClientUFS* messages. The
// restore foundation is one method:
//   Cloud.GetAppFileChangelist#1
//       lists the app's remote cloud files (name, sha, size, timestamp)
//       relative to a synced change number; synced_change_number=0 returns
//       the complete list.
//
// Field numbers verified against JavaSteam 1.8.x
//   src/main/proto/.../steammessages_cloud.steamclient.proto

namespace wn_steam::pb {

// Cloud.GetAppFileChangelist#1 request.
//   1 uint32 appid
//   2 uint64 synced_change_number   (0 → full list)
struct CCloud_GetAppFileChangelist_Request {
    uint32_t appid                = 0;
    uint64_t synced_change_number = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// CCloud_AppFileInfo — one remote cloud file.
//   1 string file_name
//   2 bytes  sha_file              (SHA-1 of the file content)
//   3 uint64 time_stamp            (unix seconds)
//   4 uint32 raw_file_size
//   5 enum   persist_state         (0 = Persisted, 1 = Forgotten, 2 = Deleted)
//   6 uint32 platforms_to_sync
//   7 uint32 path_prefix_index     (index into Response.path_prefixes)
//   8 uint32 machine_name_index    (index into Response.machine_names)
struct CCloud_AppFileInfo {
    std::string          file_name;
    std::vector<uint8_t> sha_file;
    uint64_t             time_stamp         = 0;
    uint32_t             raw_file_size      = 0;
    int32_t              persist_state      = 0;
    uint32_t             platforms_to_sync  = 0;
    uint32_t             path_prefix_index  = 0;
    uint32_t             machine_name_index = 0;

    [[nodiscard]] static std::optional<CCloud_AppFileInfo>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.GetAppFileChangelist#1 response.
//   1 uint64 current_change_number
//   2 repeated CCloud_AppFileInfo files
//   3 bool   is_only_delta
//   4 repeated string path_prefixes
//   5 repeated string machine_names
//   6 uint64 app_buildid_hwm
struct CCloud_GetAppFileChangelist_Response {
    uint64_t                        current_change_number = 0;
    std::vector<CCloud_AppFileInfo>  files;
    bool                            is_only_delta = false;
    std::vector<std::string>        path_prefixes;
    std::vector<std::string>        machine_names;
    uint64_t                        app_buildid_hwm = 0;

    [[nodiscard]] static std::optional<CCloud_GetAppFileChangelist_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.ClientFileDownload#1 request.
//   1 uint32 appid
//   2 string filename       (the remote cloud file name from AppFileInfo)
//   3 uint32 realm           (1 = global Steam realm)
struct CCloud_ClientFileDownload_Request {
    uint32_t    appid = 0;
    std::string filename;
    uint32_t    realm = 1;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// One HTTP header to replay when fetching the file body (field 10, nested).
//   1 string name
//   2 string value
struct CCloud_HTTPHeader {
    std::string name;
    std::string value;

    [[nodiscard]] static std::optional<CCloud_HTTPHeader>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.ClientFileDownload#1 response. The file body is NOT in this message —
// it must be fetched over HTTP(S) from <use_https?https:http>://<url_host>
// <url_path>, replaying request_headers. `encrypted` true means the fetched
// bytes are still AES-encrypted (needs the client encryption key).
//   1 uint32 appid
//   2 uint32 file_size
//   3 uint32 raw_file_size
//   4 bytes  sha_file
//   5 uint64 time_stamp
//   6 bool   is_explicit_delete
//   7 string url_host
//   8 string url_path
//   9 bool   use_https
//  10 repeated HTTPHeaders request_headers
//  11 bool   encrypted
struct CCloud_ClientFileDownload_Response {
    uint32_t                       file_size     = 0;
    uint32_t                       raw_file_size = 0;
    std::vector<uint8_t>           sha_file;
    uint64_t                       time_stamp    = 0;
    bool                           is_explicit_delete = false;
    std::string                    url_host;
    std::string                    url_path;
    bool                           use_https     = false;
    std::vector<CCloud_HTTPHeader>  request_headers;
    bool                           encrypted     = false;

    [[nodiscard]] static std::optional<CCloud_ClientFileDownload_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// ---------------------------------------------------------------------------
// Upload path (cloud backup): BeginAppUploadBatch → ClientBeginFileUpload →
// HTTP PUT blocks → ClientCommitFileUpload → CompleteAppUploadBatchBlocking.
// ---------------------------------------------------------------------------

// Cloud.BeginAppUploadBatch#1 request.
//   1 uint32 appid
//   2 string machine_name
//   3 repeated string files_to_upload
//   4 repeated string files_to_delete
//   5 uint64 client_id
//   6 uint64 app_build_id
struct CCloud_BeginAppUploadBatch_Request {
    uint32_t                 appid = 0;
    std::string              machine_name;
    std::vector<std::string> files_to_upload;
    std::vector<std::string> files_to_delete;
    uint64_t                 client_id    = 0;
    uint64_t                 app_build_id = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// Response: 1 uint64 batch_id, 4 uint64 app_change_number.
struct CCloud_BeginAppUploadBatch_Response {
    uint64_t batch_id          = 0;
    uint64_t app_change_number = 0;

    [[nodiscard]] static std::optional<CCloud_BeginAppUploadBatch_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.ClientBeginFileUpload#1 request.
//   1 uint32 appid          2 uint32 file_size      3 uint32 raw_file_size
//   4 bytes  file_sha       5 uint64 time_stamp     6 string filename
//  13 uint64 upload_batch_id
struct CCloud_ClientBeginFileUpload_Request {
    uint32_t             appid           = 0;
    uint32_t             file_size       = 0;
    uint32_t             raw_file_size   = 0;
    std::vector<uint8_t> file_sha;
    uint64_t             time_stamp      = 0;
    std::string          filename;
    uint64_t             upload_batch_id = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ClientCloudFileUploadBlockDetails — one PUT block of an upload.
//   1 string url_host   2 string url_path   3 bool use_https
//   4 int32  http_method
//   5 repeated HTTPHeaders request_headers
//   6 uint64 block_offset   7 uint32 block_length
//   8 bytes  explicit_body_data   9 bool may_parallelize
struct CCloud_UploadBlockDetails {
    std::string                    url_host;
    std::string                    url_path;
    bool                           use_https   = false;
    int32_t                        http_method = 0;
    std::vector<CCloud_HTTPHeader>  request_headers;
    uint64_t                       block_offset = 0;
    uint32_t                       block_length = 0;
    std::vector<uint8_t>           explicit_body_data;
    bool                           may_parallelize = false;

    [[nodiscard]] static std::optional<CCloud_UploadBlockDetails>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.ClientBeginFileUpload#1 response.
//   1 bool encrypt_file
//   2 repeated ClientCloudFileUploadBlockDetails block_requests
struct CCloud_ClientBeginFileUpload_Response {
    bool                                  encrypt_file = false;
    std::vector<CCloud_UploadBlockDetails> block_requests;

    [[nodiscard]] static std::optional<CCloud_ClientBeginFileUpload_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.ClientCommitFileUpload#1 request.
//   1 bool transfer_succeeded   2 uint32 appid
//   3 bytes file_sha            4 string filename
struct CCloud_ClientCommitFileUpload_Request {
    bool                 transfer_succeeded = false;
    uint32_t             appid = 0;
    std::vector<uint8_t> file_sha;
    std::string          filename;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// Response: 1 bool file_committed.
struct CCloud_ClientCommitFileUpload_Response {
    bool file_committed = false;

    [[nodiscard]] static std::optional<CCloud_ClientCommitFileUpload_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.CompleteAppUploadBatchBlocking#1 request.
//   1 uint32 appid   2 uint64 batch_id   3 uint32 batch_eresult
struct CCloud_CompleteAppUploadBatch_Request {
    uint32_t appid         = 0;
    uint64_t batch_id      = 0;
    uint32_t batch_eresult = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// ---------------------------------------------------------------------------
// App-session signalling: Cloud.SignalAppLaunchIntent / SignalAppExitSyncDone.
// ---------------------------------------------------------------------------

// Cloud.SignalAppLaunchIntent#1 request.
//   1 uint32 appid   2 uint64 client_id   3 string machine_name
//   4 bool   ignore_pending_operations   5 int32 os_type
struct CCloud_AppLaunchIntent_Request {
    uint32_t    appid                     = 0;
    uint64_t    client_id                 = 0;
    std::string machine_name;
    bool        ignore_pending_operations = false;
    int32_t     os_type                   = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// Cloud.SignalAppLaunchIntent#1 response.
//   1 repeated CCloud_PendingRemoteOperation pending_remote_operations
// Each PendingRemoteOperation's field 1 is `operation` (ECloudPendingRemote
// Operation: 0 None, 1 AppSessionActive, 2 UploadInProgress, 3 UploadPending,
// 4 AppSessionSuspended) — the only field the caller needs.
struct CCloud_AppLaunchIntent_Response {
    std::vector<int32_t> pending_operation_codes;

    [[nodiscard]] static std::optional<CCloud_AppLaunchIntent_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Cloud.SignalAppExitSyncDone#1 notification.
//   1 uint32 appid   2 uint64 client_id
//   3 bool   uploads_completed   4 bool uploads_required
struct CCloud_AppExitSyncDone_Notification {
    uint32_t appid             = 0;
    uint64_t client_id         = 0;
    bool     uploads_completed = false;
    bool     uploads_required  = false;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

}  // namespace wn_steam::pb
