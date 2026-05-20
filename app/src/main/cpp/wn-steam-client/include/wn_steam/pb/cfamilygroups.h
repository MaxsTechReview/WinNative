#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_familygroups.steamclient.proto
// — the FamilyGroups unified-service surface used to enumerate the local
// user's Steam Family (formerly "family sharing") members.
//
// One method is needed: FamilyGroups.GetFamilyGroup#1. Given a family group
// id (handed to us in CMsgClientLogonResponse.family_group_id), it returns the
// group name and its member SteamIDs. SteamService turns the members into
// account IDs for the family-shared-library check.
//
// Field numbers verified against JavaSteam 1.8.x
//   src/main/proto/.../steammessages_familygroups.steamclient.proto

namespace wn_steam::pb {

// FamilyGroups.GetFamilyGroup#1 request.
//   1 uint64 family_groupid
struct CFamilyGroups_GetFamilyGroup_Request {
    uint64_t family_groupid = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// FamilyGroupMember — one member of a family group. We only consume the
// SteamID; role / join-time / cooldown fields are skipped.
//   1 fixed64 steamid
struct FamilyGroupMember {
    uint64_t steamid = 0;

    [[nodiscard]] static std::optional<FamilyGroupMember>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// FamilyGroups.GetFamilyGroup#1 response.
//   1 string   name
//   2 repeated FamilyGroupMember members
struct CFamilyGroups_GetFamilyGroup_Response {
    std::string                    name;
    std::vector<FamilyGroupMember> members;

    [[nodiscard]] static std::optional<CFamilyGroups_GetFamilyGroup_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
