#pragma once

#include <cstdint>
#include <optional>
#include <span>

// CMsgClientPlayingSessionState (EMsg 9600) — server-pushed. Tells the client
// whether playing is currently blocked (another logged-on session of this
// account holds the playing slot) and, if so, which app.
//
// Field numbers verified against the canonical Steam
//   steammessages_clientserver_2.proto:
//     CMsgClientPlayingSessionState { playing_blocked=2 bool,
//                                     playing_app=3 uint32 }

namespace wn_steam::pb {

struct CMsgClientPlayingSessionState {
    bool     playing_blocked = false;   // 2 bool
    uint32_t playing_app     = 0;       // 3 uint32

    [[nodiscard]] static std::optional<CMsgClientPlayingSessionState>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
