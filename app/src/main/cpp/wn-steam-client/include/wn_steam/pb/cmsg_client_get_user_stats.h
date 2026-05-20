#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <vector>

// CMsgClientGetUserStats (EMsg 818) / Response (EMsg 819).
//
// Request: game_id (fixed64) = the appid; steam_id_for_user (fixed64) = the
// logged-on user's full SteamID. crc_stats / schema_local_version are left
// zero so Steam returns the complete schema rather than an incremental diff.
//
// Response: int32 eresult (default 2=Fail in proto2), and `schema` — the
// binary-VDF UserGameStatsSchema blob for the app. That blob is what
// Kotlin's StatsAchievementsGenerator turns into Goldberg's
// achievements.json + stats.json. We treat it as opaque bytes here.
//
// Field numbers verified against the canonical Steam
//   steammessages_clientserver_userstats.proto:
//     CMsgClientGetUserStats          { game_id=1 fixed64, crc_stats=2,
//                                       schema_local_version=3,
//                                       steam_id_for_user=4 fixed64 }
//     CMsgClientGetUserStatsResponse  { game_id=1, eresult=2 [default=2],
//                                       crc_stats=3, schema=4 bytes,
//                                       stats=5, achievement_blocks=6 }

namespace wn_steam::pb {

struct CMsgClientGetUserStats {
    uint64_t game_id           = 0;   // 1 fixed64 — appid
    uint64_t steam_id_for_user = 0;   // 4 fixed64 — full SteamID

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// CMsgClientGetUserStatsResponse.Achievement_Blocks — one 32-achievement
// "block". unlock_time[i] is the unlock timestamp of the achievement at
// bit i (0 = still locked). unlock_time is `repeated fixed32` on the wire.
struct UserStatsAchievementBlock {
    uint32_t              achievement_id = 0;   // 1 uint32
    std::vector<uint32_t> unlock_time;          // 2 repeated fixed32
};

struct CMsgClientGetUserStatsResponse {
    // proto2 default = 2 (EResult.Fail) — a missing field is NOT 0/Invalid.
    int32_t               eresult   = 2;   // 2 int32 [default = 2]
    uint32_t              crc_stats = 0;   // 3 uint32
    std::vector<uint8_t>  schema;          // 4 bytes — binary-VDF schema
    std::vector<UserStatsAchievementBlock> achievement_blocks;  // 6 repeated

    [[nodiscard]] static std::optional<CMsgClientGetUserStatsResponse>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
