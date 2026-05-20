#pragma once

#include <cstdint>
#include <vector>

// CMsgClientStoreUserStats2 (EMsg 5466) — write stat / achievement values
// back to Steam. Fire-and-forget; serialize-only (no response is consumed).
//
// Field numbers verified against
//   steammessages_clientserver_userstats.proto:
//     CMsgClientStoreUserStats2 { game_id=1 fixed64, settor_steam_id=2 fixed64,
//        settee_steam_id=3 fixed64, crc_stats=4 uint32, explicit_reset=5 bool,
//        stats=6 repeated Stats }
//     Stats { stat_id=1 uint32, stat_value=2 uint32 }

namespace wn_steam::pb {

struct CMsgClientStoreUserStats2 {
    struct Stat {
        uint32_t stat_id    = 0;
        uint32_t stat_value = 0;
    };

    uint64_t          game_id         = 0;   // 1 fixed64 — appid
    uint64_t          settor_steam_id = 0;   // 2 fixed64 — full SteamID
    uint64_t          settee_steam_id = 0;   // 3 fixed64 — full SteamID
    uint32_t          crc_stats       = 0;   // 4 uint32
    std::vector<Stat> stats;                 // 6 repeated Stats

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

}  // namespace wn_steam::pb
