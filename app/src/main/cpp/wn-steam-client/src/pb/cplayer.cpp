#include "wn_steam/pb/cplayer.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CPlayer_GetOwnedGames_Request::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint64_field(1, steamid);
    w.bool_field(2, include_appinfo);
    w.bool_field(3, include_played_free_games);
    w.bool_field(5, include_free_sub);
    w.bool_field(8, include_extended_appinfo);
    return out;
}

std::optional<CPlayer_OwnedGame>
CPlayer_OwnedGame::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CPlayer_OwnedGame m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:   // appid (int32, never negative on the wire here)
                if (auto v = r.u32(); v) m.appid = static_cast<int32_t>(*v);
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.string(); v) m.name = std::move(*v);
                else return std::nullopt;
                break;
            case 3:
                if (auto v = r.u32(); v) m.playtime_2weeks = static_cast<int32_t>(*v);
                else return std::nullopt;
                break;
            case 4:
                if (auto v = r.u32(); v) m.playtime_forever = static_cast<int32_t>(*v);
                else return std::nullopt;
                break;
            case 5:
                if (auto v = r.string(); v) m.img_icon_url = std::move(*v);
                else return std::nullopt;
                break;
            case 11:
                if (auto v = r.u32(); v) m.rtime_last_played = *v;
                else return std::nullopt;
                break;
            case 13:
                if (auto v = r.string(); v) m.sort_as = std::move(*v);
                else return std::nullopt;
                break;
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

std::optional<CPlayer_GetOwnedGames_Response>
CPlayer_GetOwnedGames_Response::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CPlayer_GetOwnedGames_Response m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u32(); v) m.game_count = *v;
                else return std::nullopt;
                break;
            case 2: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                auto game = CPlayer_OwnedGame::deserialize(*b);
                if (!game) return std::nullopt;
                m.games.push_back(std::move(*game));
                break;
            }
            default:
                if (!r.skip(t->wire_type)) return std::nullopt;
                break;
        }
    }
    return m;
}

}  // namespace wn_steam::pb
