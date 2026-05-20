#include "wn_steam/pb/cmsg_client_playing_session_state.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::optional<CMsgClientPlayingSessionState>
CMsgClientPlayingSessionState::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientPlayingSessionState m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 2:
                if (auto v = r.boolean(); v) m.playing_blocked = *v;
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.u32(); v) m.playing_app = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
