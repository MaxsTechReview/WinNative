#include "wn_steam/pb/cmsg_client_get_app_ownership_ticket.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CMsgClientGetAppOwnershipTicket::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, app_id);
    return out;
}

std::optional<CMsgClientGetAppOwnershipTicketResponse>
CMsgClientGetAppOwnershipTicketResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientGetAppOwnershipTicketResponse m;
    while (!r.eof()) {
        auto t = r.next_tag();
        if (!t) {
            if (!r.ok()) return std::nullopt;
            break;
        }
        switch (t->field_number) {
            case 1:
                if (auto v = r.u32(); v) m.eresult = *v;
                else return std::nullopt;
                break;
            case 2:
                if (auto v = r.u32(); v) m.app_id = *v;
                else return std::nullopt;
                break;
            case 3: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.ticket.assign(b->begin(), b->end());
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
