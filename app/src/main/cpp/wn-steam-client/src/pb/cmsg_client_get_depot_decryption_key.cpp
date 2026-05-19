#include "wn_steam/pb/cmsg_client_get_depot_decryption_key.h"

#include "wn_steam/proto_wire.h"

namespace wn_steam::pb {

std::vector<uint8_t> CMsgClientGetDepotDecryptionKey::serialize() const {
    std::vector<uint8_t> out;
    proto::Writer w(out);
    w.uint32_field(1, depot_id);
    w.uint32_field(2, app_id);
    return out;
}

std::optional<CMsgClientGetDepotDecryptionKeyResponse>
CMsgClientGetDepotDecryptionKeyResponse::deserialize(std::span<const uint8_t> body) noexcept {
    proto::Reader r(body);
    CMsgClientGetDepotDecryptionKeyResponse m;
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
                if (auto v = r.u32(); v) m.depot_id = *v;
                else return std::nullopt;
                break;
            case 3: {
                auto b = r.bytes();
                if (!b) return std::nullopt;
                m.depot_encryption_key.assign(b->begin(), b->end());
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
