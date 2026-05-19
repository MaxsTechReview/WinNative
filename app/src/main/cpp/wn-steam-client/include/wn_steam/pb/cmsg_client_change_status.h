#pragma once

#include <cstdint>
#include <vector>

// CMsgClientChangeStatus (EMsg 716). Publishes the client's persona state
// (online/offline/away/…) so Steam friends see it. Fire-and-forget; no
// response. Serialize-only.
//
//   1 uint32 persona_state  (EPersonaState: 0 Offline, 1 Online, 2 Busy,
//                            3 Away, 4 Snooze, 5 LookingToTrade, 6 LookingToPlay)

namespace wn_steam::pb {

struct CMsgClientChangeStatus {
    uint32_t persona_state = 0;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

}  // namespace wn_steam::pb
