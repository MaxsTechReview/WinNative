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
    // CMsgClientGamesPlayed travels on ClientGamesPlayedWithDataBlob (5410),
    // NOT the legacy struct EMsg 742 — matches JavaSteam's SteamApps.
    ClientGamesPlayedWithDataBlob = 5410,   // presence/playtime — fire-and-forget
    ClientKickPlayingSession   = 9601,  // release this account's other playing session
    ClientChangeStatus         = 716,   // publish persona (online/offline) state
    ClientRequestFriendData    = 815,   // request persona data for SteamIDs
    // ClientPersonaState (766) — server-pushed persona updates; already
    // declared below in the server-pushed-messages block.
    ClientSessionToken         = 850,
    ClientServerUnavailable    = 5500,  // NOT protobuf — legacy struct; see route_inbound_

    // Server-pushed messages that arrive immediately after ClientLogonResponse.
    // Phase 3a decodes them as opaque protobuf bodies (just logs them) so we
    // have wire traces for the Phase 4 PICS / friends / persona work.
    ClientPersonaState         = 766,
    ClientFriendsList          = 767,
    // Server-pushed: tells the client whether playing is currently blocked
    // (another session holds the playing slot — Family Sharing / "account in
    // use elsewhere"). Verified against JavaSteam emsg.steamd.
    ClientPlayingSessionState  = 9600,
    ClientAccountInfo          = 768,
    ClientEmailAddrInfo        = 779,
    ClientLicenseList          = 780,

    // PICS — Product Info Cache. Verified against JavaSteam EMsg.java:2818-2824.
    // Sent proto-flagged like ServiceMethodCallFromClient (header is
    // CMsgProtoBufHeader; request carries jobid_source, response echoes
    // jobid_target for routing).
    ClientPICSChangesSinceRequest  = 8901,
    ClientPICSChangesSinceResponse = 8902,
    ClientPICSProductInfoRequest   = 8903,
    ClientPICSProductInfoResponse  = 8904,
    ClientPICSAccessTokenRequest   = 8905,
    ClientPICSAccessTokenResponse  = 8906,

    // App ownership tickets — the opaque blob Wine's lsteamclient.dll
    // returns from SteamUser()->GetAppOwnershipTicket(appid). Used by
    // games for DRM / online-play entitlement checks. Single-shot
    // request/response keyed on jobid_target. Verified against
    // JavaSteam EMsg.java:600,602.
    ClientGetAppOwnershipTicket         = 857,
    ClientGetAppOwnershipTicketResponse = 858,

    // Encrypted app ticket — RequestEncryptedAppTicket. The response's
    // EncryptedAppTicket sub-message (base64'd) is what Goldberg's
    // steam_settings/configs.user.ini `ticket=` consumes for online auth.
    // Verified against JavaSteam emsg.steamd:1402-1403.
    ClientRequestEncryptedAppTicket         = 5526,
    ClientRequestEncryptedAppTicketResponse = 5527,

    // Depot decryption keys — the AES-256 key for a depot's content
    // manifest and chunks. Phase 5 (CDN downloads) foundation; single-shot
    // request/response keyed on jobid_target. Verified against JavaSteam
    // emsg.steamd:1326-1327 — the values ARE 5438/5439, NOT 1202/1203.
    // (1202 is a different message; sending it makes Steam boot the
    // session with EResult.InvalidProtocolVer the instant it arrives.)
    ClientGetDepotDecryptionKey         = 5438,
    ClientGetDepotDecryptionKeyResponse = 5439,

    // User-stats / achievement schema — CMsgClientGetUserStats. The
    // response's `schema` bytes are the binary-VDF UserGameStatsSchema for
    // the app; Kotlin's StatsAchievementsGenerator turns it into Goldberg's
    // achievements.json + stats.json. Single-shot, keyed on jobid_target.
    // Verified against SteamKit EMsg — 818/819.
    ClientGetUserStats         = 818,
    ClientGetUserStatsResponse = 819,

    // Write achievement / stat unlocks back to Steam — CMsgClientStoreUserStats2.
    // Fire-and-forget (no response is consumed); the header carries
    // routing_appid so Steam's GS backend routes it to the right app.
    // Verified against JavaSteam emsg.steamd:1353.
    ClientStoreUserStats2      = 5466,

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
