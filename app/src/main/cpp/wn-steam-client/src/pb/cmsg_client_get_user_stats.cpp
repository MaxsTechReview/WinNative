#include "wn_steam/pb/cmsg_client_get_user_stats.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CMsgClientGetUserStats::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    // game_id and steam_id_for_user are fixed64 on the wire (NOT varint) —
    // see steammessages_clientserver_userstats.proto.
    w.fixed64_field(1, game_id);
    w.fixed64_field(4, steam_id_for_user);
    return out;
}

// Parse one CMsgClientGetUserStatsResponse.Achievement_Blocks sub-message.
//   1 uint32          achievement_id
//   2 repeated fixed32 unlock_time  — proto2 default is UNPACKED (each
//     element a separate field-2/Fixed32 tag), but a server that packs it
//     would send one field-2/LengthDelimited blob; handle both.
static std::optional<UserStatsAchievementBlock>
parse_achievement_block(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    UserStatsAchievementBlock blk;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        if (t->field_number == 1) {
            if (auto v = r.u32(); v) blk.achievement_id = *v;
            else return std::nullopt;
        } else if (t->field_number == 2 &&
                   t->wire_type == proto::WireType::Fixed32) {
            if (auto v = r.fixed32(); v) blk.unlock_time.push_back(*v);
            else return std::nullopt;
        } else if (t->field_number == 2 &&
                   t->wire_type == proto::WireType::LengthDelimited) {
            auto packed = r.bytes();
            if (!packed) return std::nullopt;
            proto::Reader pr(*packed);
            while (!pr.eof()) {
                auto v = pr.fixed32();
                if (!v) return std::nullopt;
                blk.unlock_time.push_back(*v);
            }
        } else if (!r.skip(t->wire_type)) {
            return std::nullopt;
        }
    }
    return blk;
}

std::optional<CMsgClientGetUserStatsResponse>
CMsgClientGetUserStatsResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientGetUserStatsResponse m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 2:
                // int32 on the wire is a plain varint (two's complement).
                if (auto v = r.u64(); v)
                    m.eresult = static_cast<int32_t>(static_cast<uint32_t>(*v));
                else
                    return std::nullopt;
                break;
            case 3:
                if (auto v = r.u32(); v) m.crc_stats = *v;
                else return std::nullopt;
                break;
            case 4: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.schema.assign(b->begin(), b->end());
                break;
            }
            case 6: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto blk = parse_achievement_block(*b);
                if (!blk) return std::nullopt;
                m.achievement_blocks.push_back(std::move(*blk));
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
