#include "wn_steam/pb/cfamilygroups.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CFamilyGroups_GetFamilyGroup_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint64_field(1, family_groupid);
    return out;
}

std::optional<FamilyGroupMember>
FamilyGroupMember::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    FamilyGroupMember m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        if (t->field_number == 1) {
            if (auto v = r.fixed64(); v) m.steamid = *v;
            else return std::nullopt;
        } else if (!r.skip(t->wire_type)) {
            return std::nullopt;
        }
    }
    return m;
}

std::optional<CFamilyGroups_GetFamilyGroup_Response>
CFamilyGroups_GetFamilyGroup_Response::deserialize(
        std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CFamilyGroups_GetFamilyGroup_Response m;
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
            case 2: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto member = FamilyGroupMember::deserialize(*b);
                if (!member) return std::nullopt;
                m.members.push_back(std::move(*member));
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
