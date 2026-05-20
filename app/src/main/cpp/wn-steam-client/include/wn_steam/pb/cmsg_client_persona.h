#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <vector>

// Persona messages:
//   CMsgClientRequestFriendData (EMsg 815) — request persona data for a set
//     of SteamIDs (we use it for the local user only).
//   CMsgClientPersonaState      (EMsg 766) — server-pushed persona updates;
//     we parse only the fields the local-persona display needs.
//
// Field numbers verified against steammessages_clientserver_friends.proto.

namespace wn_steam::pb {

// CMsgClientRequestFriendData.
//   1 uint32 persona_state_requested   2 repeated fixed64 friends
struct CMsgClientRequestFriendData {
    uint32_t              persona_state_requested = 0;
    std::vector<uint64_t> friends;

    [[nodiscard]] std::vector<uint8_t> serialize() const;
};

// CMsgClientPersonaState.Friend — only the fields the UI needs.
//   1 fixed64 friendid          2 uint32 persona_state
//   3 uint32 game_played_app_id 15 string player_name
//  31 bytes  avatar_hash        55 string game_name
//  56 fixed64 gameid
struct PersonaStateFriend {
    uint64_t             friendid           = 0;
    uint32_t             persona_state      = 0;
    uint32_t             game_played_app_id = 0;
    std::string          player_name;
    std::vector<uint8_t> avatar_hash;
    std::string          game_name;
    uint64_t             gameid             = 0;

    [[nodiscard]] static std::optional<PersonaStateFriend>
    deserialize(std::span<const uint8_t> body) noexcept;
};

// CMsgClientPersonaState.   2 repeated Friend friends
struct CMsgClientPersonaState {
    std::vector<PersonaStateFriend> friends;

    [[nodiscard]] static std::optional<CMsgClientPersonaState>
    deserialize(std::span<const uint8_t> body) noexcept;
};

}  // namespace wn_steam::pb
