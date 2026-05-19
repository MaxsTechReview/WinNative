#pragma once

#include <cstdint>
#include <vector>

// CMsgClientKickPlayingSession (EMsg 9601). Tells Steam to release this
// account's *other* active playing session (another device) so this client
// can take over. Fire-and-forget; no response. Serialize-only.
//
//   1 bool only_stop_game  — true: just stop the game, keep the session;
//                            false: kick the whole session.

namespace wn_steam::pb {

struct CMsgClientKickPlayingSession {
    bool only_stop_game = false;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

}  // namespace wn_steam::pb
