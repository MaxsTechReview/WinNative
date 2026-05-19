#pragma once

#include <cstdint>
#include <vector>

// CMsgClientGamesPlayed (EMsg 742). Tells Steam which games the client is
// running — drives friends "now playing" presence and playtime accrual.
// Fire-and-forget: no response. Serialize-only here.
//
// Field numbers verified against the canonical
//   steammessages_clientserver.proto : CMsgClientGamesPlayed.

namespace wn_steam::pb {

// CMsgClientGamesPlayed.ProcessInfo — one process in the game's tree.
struct GamePlayedProcessInfo {
    uint32_t process_id        = 0;   // 1 uint32
    uint32_t process_id_parent = 0;   // 2 uint32
    bool     parent_is_steam   = false;  // 3 bool
};

// CMsgClientGamesPlayed.GamePlayed — one running game.
struct GamePlayedEntry {
    uint64_t game_id       = 0;   // 2  fixed64
    uint32_t process_id    = 0;   // 9  uint32
    uint32_t owner_id      = 0;   // 12 uint32
    uint32_t launch_source = 0;   // 21 uint32
    uint32_t game_build_id = 0;   // 26 uint32
    std::vector<GamePlayedProcessInfo> process_id_list;   // 32 repeated
};

struct CMsgClientGamesPlayed {
    std::vector<GamePlayedEntry> games_played;     // 1 repeated GamePlayed
    uint32_t                     client_os_type = 0;  // 2 uint32

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

}  // namespace wn_steam::pb
