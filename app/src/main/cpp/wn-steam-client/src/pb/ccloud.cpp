#include "wn_steam/pb/ccloud.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CCloud_GetAppFileChangelist_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    w.uint64_field(2, synced_change_number);
    return out;
}

std::optional<CCloud_AppFileInfo>
CCloud_AppFileInfo::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_AppFileInfo m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.string(); v) m.file_name = std::move(*v);
                else return std::nullopt;
                break;
            case 2: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.sha_file.assign(b->begin(), b->end());
                break;
            }
            case 3:
                if (auto v = r.u64(); v) m.time_stamp = *v;
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.u32(); v) m.raw_file_size = *v;
                else return std::nullopt;
                break;
            case 5:
                // enum on the wire is a plain varint.
                if (auto v = r.u64(); v)
                    m.persist_state = static_cast<int32_t>(static_cast<uint32_t>(*v));
                else return std::nullopt;
                break;
            case 6:
                if (auto v = r.u32(); v) m.platforms_to_sync = *v;
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.u32(); v) m.path_prefix_index = *v;
                else return std::nullopt;
                break;
            case 8:
                if (auto v = r.u32(); v) m.machine_name_index = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::optional<CCloud_GetAppFileChangelist_Response>
CCloud_GetAppFileChangelist_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_GetAppFileChangelist_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u64(); v) m.current_change_number = *v;
                else return std::nullopt;
                break;
            case 2: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto f = CCloud_AppFileInfo::deserialize(*b);
                if (!f) return std::nullopt;
                m.files.push_back(std::move(*f));
                break;
            }
            case 3:
                if (auto v = r.boolean(); v) m.is_only_delta = *v;
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.string(); v) m.path_prefixes.push_back(std::move(*v));
                else return std::nullopt;
                break;
            case 5:
                if (auto v = r.string(); v) m.machine_names.push_back(std::move(*v));
                else return std::nullopt;
                break;
            case 6:
                if (auto v = r.u64(); v) m.app_buildid_hwm = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::vector<uint8_t> CCloud_ClientFileDownload_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    w.string_field(2, filename);
    w.uint32_field(3, realm);
    return out;
}

std::optional<CCloud_HTTPHeader>
CCloud_HTTPHeader::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_HTTPHeader m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.string(); v) m.name = std::move(*v);
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.value = std::move(*v);
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::optional<CCloud_ClientFileDownload_Response>
CCloud_ClientFileDownload_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_ClientFileDownload_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:  // appid — echoed, unused
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
            case 2:
                if (auto v = r.u32(); v) m.file_size = *v;
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.u32(); v) m.raw_file_size = *v;
                else return std::nullopt;
                break;
            case 4: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.sha_file.assign(b->begin(), b->end());
                break;
            }
            case 5:
                if (auto v = r.u64(); v) m.time_stamp = *v;
                else return std::nullopt;
                break;
            case 6:
                if (auto v = r.boolean(); v) m.is_explicit_delete = *v;
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.string(); v) m.url_host = std::move(*v);
                else return std::nullopt;
                break;
            case 8:
                if (auto v = r.string(); v) m.url_path = std::move(*v);
                else return std::nullopt;
                break;
            case 9:
                if (auto v = r.boolean(); v) m.use_https = *v;
                else return std::nullopt;
                break;
            case 10: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto hdr = CCloud_HTTPHeader::deserialize(*b);
                if (!hdr) return std::nullopt;
                m.request_headers.push_back(std::move(*hdr));
                break;
            }
            case 11:
                if (auto v = r.boolean(); v) m.encrypted = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

// --------------------------------------------------------------------------
// Upload path.
// --------------------------------------------------------------------------

std::vector<uint8_t> CCloud_BeginAppUploadBatch_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    if (!machine_name.empty()) w.string_field(2, machine_name);
    for (const auto& f : files_to_upload) w.string_field(3, f);
    for (const auto& f : files_to_delete) w.string_field(4, f);
    w.uint64_field(5, client_id);
    w.uint64_field(6, app_build_id);
    return out;
}

std::optional<CCloud_BeginAppUploadBatch_Response>
CCloud_BeginAppUploadBatch_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_BeginAppUploadBatch_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u64(); v) m.batch_id = *v;
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.u64(); v) m.app_change_number = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::vector<uint8_t> CCloud_ClientBeginFileUpload_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    w.uint32_field(2, file_size);
    w.uint32_field(3, raw_file_size);
    if (!file_sha.empty()) w.bytes_field(4, file_sha);
    w.uint64_field(5, time_stamp);
    if (!filename.empty()) w.string_field(6, filename);
    w.uint64_field(13, upload_batch_id);
    return out;
}

std::optional<CCloud_UploadBlockDetails>
CCloud_UploadBlockDetails::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_UploadBlockDetails m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.string(); v) m.url_host = std::move(*v);
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.url_path = std::move(*v);
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.boolean(); v) m.use_https = *v;
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.u64(); v)
                    m.http_method = static_cast<int32_t>(static_cast<uint32_t>(*v));
                else return std::nullopt;
                break;
            case 5: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto hdr = CCloud_HTTPHeader::deserialize(*b);
                if (!hdr) return std::nullopt;
                m.request_headers.push_back(std::move(*hdr));
                break;
            }
            case 6:
                if (auto v = r.u64(); v) m.block_offset = *v;
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.u32(); v) m.block_length = *v;
                else return std::nullopt;
                break;
            case 8: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.explicit_body_data.assign(b->begin(), b->end());
                break;
            }
            case 9:
                if (auto v = r.boolean(); v) m.may_parallelize = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::optional<CCloud_ClientBeginFileUpload_Response>
CCloud_ClientBeginFileUpload_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_ClientBeginFileUpload_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.boolean(); v) m.encrypt_file = *v;
                else return std::nullopt;
                break;
            case 2: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto blk = CCloud_UploadBlockDetails::deserialize(*b);
                if (!blk) return std::nullopt;
                m.block_requests.push_back(std::move(*blk));
                break;
            }
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::vector<uint8_t> CCloud_ClientCommitFileUpload_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.bool_field_force(1, transfer_succeeded);
    w.uint32_field(2, appid);
    if (!file_sha.empty()) w.bytes_field(3, file_sha);
    if (!filename.empty()) w.string_field(4, filename);
    return out;
}

std::optional<CCloud_ClientCommitFileUpload_Response>
CCloud_ClientCommitFileUpload_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_ClientCommitFileUpload_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.boolean(); v) m.file_committed = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::vector<uint8_t> CCloud_CompleteAppUploadBatch_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    w.uint64_field(2, batch_id);
    w.uint32_field(3, batch_eresult);
    return out;
}

std::vector<uint8_t> CCloud_AppLaunchIntent_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    w.uint64_field(2, client_id);
    if (!machine_name.empty()) w.string_field(3, machine_name);
    w.bool_field(4, ignore_pending_operations);
    w.int32_field(5, os_type);
    return out;
}

std::optional<CCloud_AppLaunchIntent_Response>
CCloud_AppLaunchIntent_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CCloud_AppLaunchIntent_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        if (t->field_number == 1) {
            // repeated CCloud_PendingRemoteOperation — extract field 1 (operation).
            auto b = r.bytes();
            if (!b) return std::nullopt;
            proto::Reader sub(*b);
            int32_t op = 0;
            while (!sub.eof()) {
                auto st = sub.next_tag();
                if (!st) { if (!sub.ok()) return std::nullopt; break; }
                if (st->field_number == 1) {
                    if (auto v = sub.u64(); v)
                        op = static_cast<int32_t>(static_cast<uint32_t>(*v));
                    else return std::nullopt;
                } else if (!sub.skip(st->wire_type)) {
                    return std::nullopt;
                }
            }
            m.pending_operation_codes.push_back(op);
        } else if (!r.skip(t->wire_type)) {
            return std::nullopt;
        }
    }
    return m;
}

std::vector<uint8_t> CCloud_AppExitSyncDone_Notification::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, appid);
    w.uint64_field(2, client_id);
    w.bool_field(3, uploads_completed);
    w.bool_field(4, uploads_required);
    return out;
}

}  // namespace wn_steam::pb
