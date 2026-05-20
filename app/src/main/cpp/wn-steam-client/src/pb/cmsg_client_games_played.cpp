#include "wn_steam/pb/cmsg_client_games_played.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

namespace {
std::vector<uint8_t> serialize_process_info(const GamePlayedProcessInfo& pi) {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    // proto2 message — 0/false are meaningful (a pid can be 0); force-emit so
    // the process tree isn't silently dropped (matches JavaSteam's builder).
    w.uint32_field_force(1, pi.process_id);
    w.uint32_field_force(2, pi.process_id_parent);
    w.bool_field_force(3, pi.parent_is_steam);
    return out;
}

std::vector<uint8_t> serialize_game_played(const GamePlayedEntry& g) {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    if (g.game_id != 0) w.fixed64_field(2, g.game_id);
    w.uint32_field_force(9, g.process_id);
    w.uint32_field_force(12, g.owner_id);
    w.uint32_field_force(21, g.launch_source);
    w.uint32_field_force(26, g.game_build_id);
    for (const auto& pi : g.process_id_list) {
        w.submessage_field(32, serialize_process_info(pi));
    }
    return out;
}
}  // namespace

std::vector<uint8_t> CMsgClientGamesPlayed::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    for (const auto& g : games_played) {
        w.submessage_field(1, serialize_game_played(g));
    }
    w.uint32_field(2, client_os_type);
    return out;
}

}  // namespace wn_steam::pb
