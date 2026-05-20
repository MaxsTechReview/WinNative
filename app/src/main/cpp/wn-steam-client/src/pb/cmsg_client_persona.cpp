#include "wn_steam/pb/cmsg_client_persona.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CMsgClientRequestFriendData::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, persona_state_requested);
    for (uint64_t id : friends) {
        w.fixed64_field(2, id);
    }
    return out;
}

std::optional<PersonaStateFriend>
PersonaStateFriend::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    PersonaStateFriend m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.fixed64(); v) m.friendid = *v;
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.u32(); v) m.persona_state = *v;
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.u32(); v) m.game_played_app_id = *v;
                else return std::nullopt;
                break;
            case 15:
                if (auto v = r.string(); v) m.player_name = std::move(*v);
                else return std::nullopt;
                break;
            case 31: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.avatar_hash.assign(b->begin(), b->end());
                break;
            }
            case 55:
                if (auto v = r.string(); v) m.game_name = std::move(*v);
                else return std::nullopt;
                break;
            case 56:
                if (auto v = r.fixed64(); v) m.gameid = *v;
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::optional<CMsgClientPersonaState>
CMsgClientPersonaState::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientPersonaState m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        if (t->field_number == 2) {
            auto b = r.bytes();
            if (!b) return std::nullopt;
            auto f = PersonaStateFriend::deserialize(*b);
            if (!f) return std::nullopt;
            m.friends.push_back(std::move(*f));
        } else if (!r.skip(t->wire_type)) {
            return std::nullopt;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
