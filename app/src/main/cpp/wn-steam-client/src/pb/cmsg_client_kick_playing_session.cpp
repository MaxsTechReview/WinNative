#include "wn_steam/pb/cmsg_client_kick_playing_session.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CMsgClientKickPlayingSession::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    // only_stop_game is a meaningful flag — force-emit so a `false` (kick the
    // whole session) is explicit on the wire rather than dropped as default.
    w.bool_field_force(1, only_stop_game);
    return out;
}

}  // namespace wn_steam::pb
