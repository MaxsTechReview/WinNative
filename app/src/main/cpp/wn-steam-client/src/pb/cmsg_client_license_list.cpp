#include "wn_steam/pb/cmsg_client_license_list.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::optional<License>
License::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    License m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u32(); v) m.package_id = *v;
                else return std::nullopt;
                break;
            case 2:
                if (t->wire_type != proto::WireType::Fixed32) return std::nullopt;
                if (auto v = r.fixed32(); v) m.time_created = *v;
                else return std::nullopt;
                break;
            case 3:
                if (t->wire_type != proto::WireType::Fixed32) return std::nullopt;
                if (auto v = r.fixed32(); v) m.time_next_process = *v;
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.i32(); v) m.minute_limit = *v;
                else return std::nullopt;
                break;
            case 5:
                if (auto v = r.i32(); v) m.minutes_used = *v;
                else return std::nullopt;
                break;
            case 6:
                if (auto v = r.u32(); v) m.payment_method = *v;
                else return std::nullopt;
                break;
            case 7:
                if (auto v = r.u32(); v) m.flags = *v;
                else return std::nullopt;
                break;
            case 8:
                if (auto v = r.string(); v) m.purchase_country_code = std::move(*v);
                else return std::nullopt;
                break;
            case 9:
                if (auto v = r.u32(); v) m.license_type = *v;
                else return std::nullopt;
                break;
            case 10:
                if (auto v = r.i32(); v) m.territory_code = *v;
                else return std::nullopt;
                break;
            case 11:
                if (auto v = r.i32(); v) m.change_number = *v;
                else return std::nullopt;
                break;
            case 12:
                if (auto v = r.u32(); v) m.owner_id = *v;
                else return std::nullopt;
                break;
            case 13:
                if (auto v = r.u32(); v) m.initial_period = *v;
                else return std::nullopt;
                break;
            case 14:
                if (auto v = r.u32(); v) m.initial_time_unit = *v;
                else return std::nullopt;
                break;
            case 15:
                if (auto v = r.u32(); v) m.renewal_period = *v;
                else return std::nullopt;
                break;
            case 16:  // 2-byte tag (0x80 0x01)
                if (auto v = r.u32(); v) m.renewal_time_unit = *v;
                else return std::nullopt;
                break;
            case 17:  // 2-byte tag (0x88 0x01)
                if (auto v = r.u64(); v) m.access_token = *v;
                else return std::nullopt;
                break;
            case 18:  // 2-byte tag (0x90 0x01)
                if (auto v = r.u32(); v) m.master_package_id = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::optional<CMsgClientLicenseList>
CMsgClientLicenseList::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientLicenseList m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:  // eresult
                if (auto v = r.i32(); v) m.eresult = *v;
                else return std::nullopt;
                break;
            case 2: {  // repeated License
                auto sub = r.bytes();
                if (!sub) return std::nullopt;
                auto lic = License::deserialize(*sub);
                if (!lic) return std::nullopt;
                m.licenses.push_back(std::move(*lic));
                break;
            }
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
