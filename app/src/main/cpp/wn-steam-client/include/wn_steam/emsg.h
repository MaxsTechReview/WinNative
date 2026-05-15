#pragma once

#include <cstdint>

namespace wn_steam {

// Subset of Steam's EMsg enum needed by Phase 1+2. Full enum (~2000 values)
// is generated from SteamKit's `emsg.steamd` and is too large to vendor
// wholesale — we add values as the protocol surface grows. Numeric values
// match SteamKit2 master.
//
// IMPORTANT: the high bit (0x80000000) is a wire-level flag, not part of
// the enum:
//   bit set   => CMsgProtoBufHeader + protobuf body
//   bit clear => MsgHdr or ExtendedClientMsgHdr + struct body
// Strip it via `static_cast<EMsg>(raw & kEMsgMask)` after reading from
// the wire, and re-OR it onto outbound protobuf messages.
enum class EMsg : uint32_t {
    Invalid                    = 0,
    Multi                      = 1,
    ChannelEncryptRequest      = 1303,
    ChannelEncryptResponse     = 1304,
    ChannelEncryptResult       = 1305,

    // Client login / session lifecycle
    ClientHello                = 9805,   // verified against SteamKit/JavaSteam/steam-vent
    ClientLogon                = 5514,
    ClientLogonResponse        = 751,
    ClientLogOff               = 706,
    ClientLoggedOff            = 757,
    ClientHeartBeat            = 703,
    ClientSessionToken         = 850,
    ClientServerUnavailable    = 5500,  // NOT protobuf — legacy struct; see route_inbound_

    // Server-pushed messages that arrive immediately after ClientLogonResponse.
    // Phase 3a decodes them as opaque protobuf bodies (just logs them) so we
    // have wire traces for the Phase 4 PICS / friends / persona work.
    ClientPersonaState         = 766,
    ClientFriendsList          = 767,
    ClientAccountInfo          = 768,
    ClientEmailAddrInfo        = 779,
    ClientLicenseList          = 780,

    // Unified messaging (modern auth, service methods)
    ServiceMethodCallFromClient          = 151,
    ServiceMethodResponse                = 147,
    ServiceMethodSendToClient            = 152,
    ServiceMethodCallFromClientNonAuthed = 9804,
};

constexpr uint32_t kEMsgProtoFlag = 0x80000000u;
constexpr uint32_t kEMsgMask      = 0x7FFFFFFFu;

[[nodiscard]] constexpr bool emsg_has_proto_flag(uint32_t raw) noexcept {
    return (raw & kEMsgProtoFlag) != 0;
}

[[nodiscard]] constexpr EMsg emsg_strip_proto_flag(uint32_t raw) noexcept {
    return static_cast<EMsg>(raw & kEMsgMask);
}

[[nodiscard]] constexpr uint32_t emsg_with_proto_flag(EMsg msg) noexcept {
    return static_cast<uint32_t>(msg) | kEMsgProtoFlag;
}

}  // namespace wn_steam
