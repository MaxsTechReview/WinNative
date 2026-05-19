#include "wn_steam/pb/cmsg_client_store_user_stats.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CMsgClientStoreUserStats2::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.fixed64_field(1, game_id);
    w.fixed64_field(2, settor_steam_id);
    w.fixed64_field(3, settee_steam_id);
    // crc_stats is forced: the server validates it, and a genuine 0 must be
    // sent as present (not dropped as a proto3-style default).
    w.uint32_field_force(4, crc_stats);
    for (const auto& s : stats) {
        std::vector<uint8_t> sub;
        proto::Writer sw(sub);
        // Forced: a stat value of 0 is a meaningful "set to 0", and a stat_id
        // of 0 is a valid key — neither may be dropped.
        sw.uint32_field_force(1, s.stat_id);
        sw.uint32_field_force(2, s.stat_value);
        w.submessage_field(6, sub);
    }
    return out;
}

}  // namespace wn_steam::pb
