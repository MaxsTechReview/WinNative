#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Hand-coded protobuf wrappers for steammessages_player.steamclient.proto —
// the Player unified-service surface. One method is needed:
// Player.GetOwnedGames#1, which lists a user's owned games (the data behind
// SteamService's owned-games / friend-library lookups). Private libraries
// come back as an empty list.
//
// Field numbers verified against JavaSteam 1.8.x
//   src/main/proto/.../steammessages_player.steamclient.proto

namespace wn_steam::pb {

// Player.GetOwnedGames#1 request.
//   1 uint64 steamid
//   2 bool   include_appinfo
//   3 bool   include_played_free_games
//   5 bool   include_free_sub
//   8 bool   include_extended_appinfo
struct CPlayer_GetOwnedGames_Request {
    uint64_t steamid                   = 0;
    bool     include_appinfo           = false;
    bool     include_played_free_games = false;
    bool     include_free_sub          = false;
    bool     include_extended_appinfo  = false;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// One owned game (CPlayer_GetOwnedGames_Response.Game). Only the fields
// SteamService's OwnedGames data class consumes are decoded; the rest
// (playtime_windows/mac/linux, has_dlc, content_descriptorids, …) are skipped.
//   1  int32  appid
//   2  string name
//   3  int32  playtime_2weeks
//   4  int32  playtime_forever
//   5  string img_icon_url
//   11 uint32 rtime_last_played
//   13 string sort_as
struct CPlayer_OwnedGame {
    int32_t     appid             = 0;
    std::string name;
    int32_t     playtime_2weeks   = 0;
    int32_t     playtime_forever  = 0;
    std::string img_icon_url;
    uint32_t    rtime_last_played = 0;
    std::string sort_as;

    [[nodiscard]] static std::optional<CPlayer_OwnedGame>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// Player.GetOwnedGames#1 response.
//   1 uint32 game_count
//   2 repeated Game games
struct CPlayer_GetOwnedGames_Response {
    uint32_t                       game_count = 0;
    std::vector<CPlayer_OwnedGame> games;

    [[nodiscard]] static std::optional<CPlayer_GetOwnedGames_Response>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
