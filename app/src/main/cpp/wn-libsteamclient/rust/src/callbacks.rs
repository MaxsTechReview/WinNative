//! Port of `wn_libsteamclient/callbacks.h`.
//!
//! Every payload is `#[repr(C)]` with compile-time size + offset checks via
//! `const _: () = assert!(...)`. Field order matches the C++ exactly.

#![allow(non_snake_case, non_camel_case_types)]

use core::mem::{offset_of, size_of};

// ---- Callback IDs ----------------------------------------------------------

pub const K_STEAM_SERVERS_CONNECTED: i32 = 101;
pub const K_STEAM_SERVER_CONNECT_FAILURE: i32 = 102;
pub const K_STEAM_SERVERS_DISCONNECTED: i32 = 103;
pub const K_IPC_FAILURE: i32 = 117;
pub const K_VALIDATE_AUTH_TICKET_RESPONSE: i32 = 143;
pub const K_ENCRYPTED_APP_TICKET_RESPONSE: i32 = 154;
pub const K_GET_AUTH_SESSION_TICKET_RESPONSE: i32 = 163;
pub const K_GET_TICKET_FOR_WEB_API_RESPONSE: i32 = 168;
pub const K_STORE_AUTH_URL_RESPONSE: i32 = 165;
pub const K_MARKET_ELIGIBILITY_RESPONSE: i32 = 166;
pub const K_DURATION_CONTROL: i32 = 167;

pub const K_STEAM_SHUTDOWN: i32 = 704;
pub const K_STEAM_API_CALL_COMPLETED: i32 = 703;
pub const K_CHECK_FILE_SIGNATURE: i32 = 705;
pub const K_LEADERBOARD_FIND_RESULT: i32 = 1104;
pub const K_LEADERBOARD_SCORES_DOWNLOADED: i32 = 1105;
pub const K_LEADERBOARD_SCORE_UPLOADED: i32 = 1106;
pub const K_NUMBER_OF_CURRENT_PLAYERS: i32 = 1107;
pub const K_GLOBAL_ACHIEVEMENT_PERCENTAGES: i32 = 1110;
pub const K_LEADERBOARD_UGC_SET: i32 = 1111;
pub const K_GLOBAL_STATS_RECEIVED: i32 = 1112;
pub const K_CLAN_OFFICER_LIST_RESPONSE: i32 = 1335;
pub const K_DOWNLOAD_CLAN_ACTIVITY_COUNTS_RESULT: i32 = 1341;
pub const K_JOIN_CLAN_CHAT_ROOM_COMPLETION: i32 = 1342;
pub const K_FRIENDS_GET_FOLLOWER_COUNT: i32 = 1344;
pub const K_FRIENDS_IS_FOLLOWING: i32 = 1345;
pub const K_FRIENDS_ENUMERATE_FOLLOWING_LIST: i32 = 1346;
pub const K_EQUIPPED_PROFILE_ITEMS: i32 = 1351;
pub const K_REMOTE_STORAGE_SUBSCRIBE_PUBLISHED_FILE: i32 = 1313;
pub const K_REMOTE_STORAGE_UNSUBSCRIBE_PUBLISHED_FILE: i32 = 1315;
pub const K_REMOTE_STORAGE_DOWNLOAD_UGC: i32 = 1317;
pub const K_STEAM_UGC_QUERY_COMPLETED: i32 = 3401;
pub const K_STEAM_UGC_REQUEST_UGC_DETAILS: i32 = 3402;
pub const K_STEAM_INVENTORY_ELIGIBLE_PROMO_ITEM_DEF_IDS: i32 = 4703;
pub const K_STEAM_INVENTORY_START_PURCHASE_RESULT: i32 = 4704;
pub const K_STEAM_INVENTORY_REQUEST_PRICES_RESULT: i32 = 4705;
pub const K_LOBBY_ENTER: i32 = 504;
pub const K_LOBBY_MATCH_LIST: i32 = 510;
pub const K_LOBBY_CREATED: i32 = 513;
pub const K_GAME_OVERLAY_ACTIVATED: i32 = 731;

pub const K_USER_STATS_RECEIVED: i32 = 1101;
pub const K_USER_STATS_STORED: i32 = 1102;
pub const K_USER_ACHIEVEMENT_STORED: i32 = 1103;

pub const K_PERSONA_STATE_CHANGE: i32 = 1304;
pub const K_SET_PERSONA_NAME_RESPONSE: i32 = 1332;
pub const K_AVATAR_IMAGE_LOADED: i32 = 1334;
pub const K_FRIEND_RICH_PRESENCE_UPDATE: i32 = 1336;

pub const K_REMOTE_STORAGE_APP_SYNCED_CLIENT: i32 = 1301;
pub const K_REMOTE_STORAGE_APP_SYNCED_SERVER: i32 = 1302;
pub const K_REMOTE_STORAGE_FILE_WRITE_ASYNC_COMPLETE: i32 = 1331;
pub const K_REMOTE_STORAGE_FILE_READ_ASYNC_COMPLETE: i32 = 1332;
pub const K_REMOTE_STORAGE_FILE_SHARE_RESULT: i32 = 1307;
pub const K_FILE_DETAILS_RESULT: i32 = 1063;

pub const K_PERSONA_CHANGE_NAME: i32 = 0x0001;
pub const K_PERSONA_CHANGE_STATUS: i32 = 0x0002;
pub const K_PERSONA_CHANGE_COME_ONLINE: i32 = 0x0004;
pub const K_PERSONA_CHANGE_GONE_OFFLINE: i32 = 0x0008;
pub const K_PERSONA_CHANGE_GAME_PLAYED: i32 = 0x0010;
pub const K_PERSONA_CHANGE_AVATAR: i32 = 0x0040;
pub const K_PERSONA_CHANGE_NAME_FIRST_SET: i32 = 0x0400;
pub const K_PERSONA_CHANGE_NICKNAME: i32 = 0x1000;

pub const K_FAILURE_FLUSHED_CALLBACK_QUEUE: u8 = 0;
pub const K_FAILURE_PIPE_FAIL: u8 = 1;

pub const K_ACHIEVEMENT_NAME_MAX: usize = 128;

// Convenience helper used across iface/* — produces a `&[u8]` view of `T`.
pub unsafe fn as_bytes<T>(t: &T) -> &[u8] {
    unsafe { std::slice::from_raw_parts(t as *const T as *const u8, std::mem::size_of::<T>()) }
}

// ---- Callback payload structs ---------------------------------------------

#[repr(C)]
#[derive(Default)]
pub struct UserStatsReceived {
    pub m_nGameID: u64,
    pub m_eResult: i32,
    pub _pad: u32,
    pub m_steamIDUser: u64,
}
const _: () = assert!(size_of::<UserStatsReceived>() == 24);
const _: () = assert!(offset_of!(UserStatsReceived, m_nGameID) == 0);
const _: () = assert!(offset_of!(UserStatsReceived, m_eResult) == 8);
const _: () = assert!(offset_of!(UserStatsReceived, m_steamIDUser) == 16);

#[repr(C)]
#[derive(Default)]
pub struct UserStatsStored {
    pub m_nGameID: u64,
    pub m_eResult: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<UserStatsStored>() == 16);
const _: () = assert!(offset_of!(UserStatsStored, m_nGameID) == 0);
const _: () = assert!(offset_of!(UserStatsStored, m_eResult) == 8);

#[repr(C)]
#[derive(Default)]
pub struct SteamServersConnected {
    pub _placeholder: u8,
}

#[repr(C)]
#[derive(Default)]
pub struct SteamServerConnectFailure {
    pub m_eResult: i32,
    pub m_bStillRetrying: bool,
    pub _pad: [u8; 3],
}
const _: () = assert!(size_of::<SteamServerConnectFailure>() == 8);
const _: () = assert!(offset_of!(SteamServerConnectFailure, m_eResult) == 0);
const _: () = assert!(offset_of!(SteamServerConnectFailure, m_bStillRetrying) == 4);

#[repr(C)]
#[derive(Default)]
pub struct IPCFailure {
    pub m_eFailureType: u8,
    pub _pad: [u8; 7],
}
const _: () = assert!(size_of::<IPCFailure>() == 8);
const _: () = assert!(offset_of!(IPCFailure, m_eFailureType) == 0);

#[repr(C)]
#[derive(Default)]
pub struct SteamShutdown {
    pub _placeholder: u8,
}

#[repr(C)]
#[derive(Default)]
pub struct GameOverlayActivated {
    pub m_bActive: bool,
    pub _pad: [u8; 7],
}
const _: () = assert!(size_of::<GameOverlayActivated>() == 8);
const _: () = assert!(offset_of!(GameOverlayActivated, m_bActive) == 0);

#[repr(C)]
#[derive(Default)]
pub struct EncryptedAppTicketResponse {
    pub m_eResult: i32,
}
const _: () = assert!(size_of::<EncryptedAppTicketResponse>() == 4);
const _: () = assert!(offset_of!(EncryptedAppTicketResponse, m_eResult) == 0);

#[repr(C)]
#[derive(Default)]
pub struct GetAuthSessionTicketResponse {
    pub m_hAuthTicket: u32,
    pub m_eResult: i32,
}
const _: () = assert!(size_of::<GetAuthSessionTicketResponse>() == 8);
const _: () = assert!(offset_of!(GetAuthSessionTicketResponse, m_hAuthTicket) == 0);
const _: () = assert!(offset_of!(GetAuthSessionTicketResponse, m_eResult) == 4);

#[repr(C)]
pub struct GetTicketForWebApiResponse {
    pub m_hAuthTicket: u32,
    pub m_eResult: i32,
    pub m_cubTicket: i32,
    pub m_rgubTicket: [u8; 2560],
}
const _: () = assert!(size_of::<GetTicketForWebApiResponse>() == 2572);
const _: () = assert!(offset_of!(GetTicketForWebApiResponse, m_hAuthTicket) == 0);
const _: () = assert!(offset_of!(GetTicketForWebApiResponse, m_eResult) == 4);
const _: () = assert!(offset_of!(GetTicketForWebApiResponse, m_cubTicket) == 8);
const _: () = assert!(offset_of!(GetTicketForWebApiResponse, m_rgubTicket) == 12);

#[repr(C)]
#[derive(Default)]
pub struct LeaderboardFindResult {
    pub m_hSteamLeaderboard: u64,
    pub m_bLeaderboardFound: u8,
    pub _pad: [u8; 7],
}
const _: () = assert!(size_of::<LeaderboardFindResult>() == 16);
const _: () = assert!(offset_of!(LeaderboardFindResult, m_hSteamLeaderboard) == 0);
const _: () = assert!(offset_of!(LeaderboardFindResult, m_bLeaderboardFound) == 8);

#[repr(C)]
#[derive(Default)]
pub struct LeaderboardScoresDownloaded {
    pub m_hSteamLeaderboard: u64,
    pub m_hSteamLeaderboardEntries: u64,
    pub m_cEntryCount: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<LeaderboardScoresDownloaded>() == 24);
const _: () = assert!(offset_of!(LeaderboardScoresDownloaded, m_hSteamLeaderboard) == 0);
const _: () = assert!(offset_of!(LeaderboardScoresDownloaded, m_hSteamLeaderboardEntries) == 8);
const _: () = assert!(offset_of!(LeaderboardScoresDownloaded, m_cEntryCount) == 16);

#[repr(C)]
#[derive(Default)]
pub struct LeaderboardScoreUploaded {
    pub m_bSuccess: u8,
    pub _pad0: [u8; 7],
    pub m_hSteamLeaderboard: u64,
    pub m_nScore: i32,
    pub m_bScoreChanged: u8,
    pub _pad1: [u8; 3],
    pub m_nGlobalRankNew: i32,
    pub m_nGlobalRankPrevious: i32,
}
const _: () = assert!(size_of::<LeaderboardScoreUploaded>() == 32);
const _: () = assert!(offset_of!(LeaderboardScoreUploaded, m_bSuccess) == 0);
const _: () = assert!(offset_of!(LeaderboardScoreUploaded, m_hSteamLeaderboard) == 8);
const _: () = assert!(offset_of!(LeaderboardScoreUploaded, m_nScore) == 16);
const _: () = assert!(offset_of!(LeaderboardScoreUploaded, m_bScoreChanged) == 20);
const _: () = assert!(offset_of!(LeaderboardScoreUploaded, m_nGlobalRankNew) == 24);
const _: () = assert!(offset_of!(LeaderboardScoreUploaded, m_nGlobalRankPrevious) == 28);

#[repr(C)]
#[derive(Default)]
pub struct NumberOfCurrentPlayers {
    pub m_bSuccess: u8,
    pub _pad0: [u8; 3],
    pub m_cPlayers: i32,
}
const _: () = assert!(size_of::<NumberOfCurrentPlayers>() == 8);
const _: () = assert!(offset_of!(NumberOfCurrentPlayers, m_bSuccess) == 0);
const _: () = assert!(offset_of!(NumberOfCurrentPlayers, m_cPlayers) == 4);

#[repr(C)]
#[derive(Default)]
pub struct GlobalAchievementPercentagesReady {
    pub m_nGameID: u64,
    pub m_eResult: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<GlobalAchievementPercentagesReady>() == 16);
const _: () = assert!(offset_of!(GlobalAchievementPercentagesReady, m_nGameID) == 0);
const _: () = assert!(offset_of!(GlobalAchievementPercentagesReady, m_eResult) == 8);

#[repr(C)]
#[derive(Default)]
pub struct LeaderboardUGCSet {
    pub m_eResult: i32,
    pub _pad: u32,
    pub m_hSteamLeaderboard: u64,
}
const _: () = assert!(size_of::<LeaderboardUGCSet>() == 16);
const _: () = assert!(offset_of!(LeaderboardUGCSet, m_eResult) == 0);
const _: () = assert!(offset_of!(LeaderboardUGCSet, m_hSteamLeaderboard) == 8);

#[repr(C)]
#[derive(Default)]
pub struct GlobalStatsReceived {
    pub m_nGameID: u64,
    pub m_eResult: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<GlobalStatsReceived>() == 16);
const _: () = assert!(offset_of!(GlobalStatsReceived, m_nGameID) == 0);
const _: () = assert!(offset_of!(GlobalStatsReceived, m_eResult) == 8);

#[repr(C)]
#[derive(Default)]
pub struct LobbyMatchList {
    pub m_nLobbiesMatching: u32,
}
const _: () = assert!(size_of::<LobbyMatchList>() == 4);
const _: () = assert!(offset_of!(LobbyMatchList, m_nLobbiesMatching) == 0);

#[repr(C)]
#[derive(Default)]
pub struct LobbyCreated {
    pub m_eResult: i32,
    pub _pad: u32,
    pub m_ulSteamIDLobby: u64,
}
const _: () = assert!(size_of::<LobbyCreated>() == 16);
const _: () = assert!(offset_of!(LobbyCreated, m_eResult) == 0);
const _: () = assert!(offset_of!(LobbyCreated, m_ulSteamIDLobby) == 8);

#[repr(C)]
#[derive(Default)]
pub struct LobbyEnter {
    pub m_ulSteamIDLobby: u64,
    pub m_rgfChatPermissions: u32,
    pub m_bLocked: u8,
    pub _pad: [u8; 3],
    pub m_EChatRoomEnterResponse: u32,
    pub _trail: u32,
}
const _: () = assert!(size_of::<LobbyEnter>() == 24);
const _: () = assert!(offset_of!(LobbyEnter, m_ulSteamIDLobby) == 0);
const _: () = assert!(offset_of!(LobbyEnter, m_rgfChatPermissions) == 8);
const _: () = assert!(offset_of!(LobbyEnter, m_bLocked) == 12);
const _: () = assert!(offset_of!(LobbyEnter, m_EChatRoomEnterResponse) == 16);

#[repr(C)]
#[derive(Default)]
pub struct SteamInventoryEligiblePromoItemDefIDs {
    pub m_result: i32,
    pub _pad0: u32,
    pub m_steamID: u64,
    pub m_numEligiblePromoItemDefs: i32,
    pub m_bCachedData: u8,
    pub _pad1: [u8; 3],
}
const _: () = assert!(size_of::<SteamInventoryEligiblePromoItemDefIDs>() == 24);
const _: () = assert!(offset_of!(SteamInventoryEligiblePromoItemDefIDs, m_result) == 0);
const _: () = assert!(offset_of!(SteamInventoryEligiblePromoItemDefIDs, m_steamID) == 8);
const _: () = assert!(
    offset_of!(SteamInventoryEligiblePromoItemDefIDs, m_numEligiblePromoItemDefs) == 16
);
const _: () = assert!(offset_of!(SteamInventoryEligiblePromoItemDefIDs, m_bCachedData) == 20);

#[repr(C)]
#[derive(Default)]
pub struct SteamInventoryStartPurchaseResult {
    pub m_result: i32,
    pub _pad: u32,
    pub m_ulOrderID: u64,
    pub m_ulTransID: u64,
}
const _: () = assert!(size_of::<SteamInventoryStartPurchaseResult>() == 24);
const _: () = assert!(offset_of!(SteamInventoryStartPurchaseResult, m_result) == 0);
const _: () = assert!(offset_of!(SteamInventoryStartPurchaseResult, m_ulOrderID) == 8);
const _: () = assert!(offset_of!(SteamInventoryStartPurchaseResult, m_ulTransID) == 16);

#[repr(C)]
#[derive(Default)]
pub struct SteamInventoryRequestPricesResult {
    pub m_result: i32,
    pub m_rgchCurrency: [u8; 4],
}
const _: () = assert!(size_of::<SteamInventoryRequestPricesResult>() == 8);
const _: () = assert!(offset_of!(SteamInventoryRequestPricesResult, m_result) == 0);
const _: () = assert!(offset_of!(SteamInventoryRequestPricesResult, m_rgchCurrency) == 4);

#[repr(C)]
#[derive(Default)]
pub struct ClanOfficerListResponse {
    pub m_steamIDClan: u64,
    pub m_cOfficers: i32,
    pub m_bSuccess: u8,
    pub _pad: [u8; 3],
}
const _: () = assert!(size_of::<ClanOfficerListResponse>() == 16);
const _: () = assert!(offset_of!(ClanOfficerListResponse, m_steamIDClan) == 0);
const _: () = assert!(offset_of!(ClanOfficerListResponse, m_cOfficers) == 8);
const _: () = assert!(offset_of!(ClanOfficerListResponse, m_bSuccess) == 12);

#[repr(C)]
#[derive(Default)]
pub struct DownloadClanActivityCountsResult {
    pub m_bSuccess: u8,
}
const _: () = assert!(size_of::<DownloadClanActivityCountsResult>() == 1);

#[repr(C)]
#[derive(Default)]
pub struct JoinClanChatRoomCompletionResult {
    pub m_steamIDClanChat: u64,
    pub m_eChatRoomEnterResponse: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<JoinClanChatRoomCompletionResult>() == 16);
const _: () = assert!(offset_of!(JoinClanChatRoomCompletionResult, m_steamIDClanChat) == 0);
const _: () = assert!(offset_of!(JoinClanChatRoomCompletionResult, m_eChatRoomEnterResponse) == 8);

#[repr(C)]
#[derive(Default)]
pub struct EquippedProfileItems {
    pub m_eResult: i32,
    pub _pad0: u32,
    pub m_steamID: u64,
    pub m_bHasAnimatedAvatar: u8,
    pub m_bHasAvatarFrame: u8,
    pub m_bHasProfileModifier: u8,
    pub m_bHasProfileBackground: u8,
    pub m_bHasMiniProfileBackground: u8,
    pub _pad1: [u8; 3],
}
const _: () = assert!(size_of::<EquippedProfileItems>() == 24);
const _: () = assert!(offset_of!(EquippedProfileItems, m_eResult) == 0);
const _: () = assert!(offset_of!(EquippedProfileItems, m_steamID) == 8);
const _: () = assert!(offset_of!(EquippedProfileItems, m_bHasAnimatedAvatar) == 16);
const _: () = assert!(offset_of!(EquippedProfileItems, m_bHasAvatarFrame) == 17);
const _: () = assert!(offset_of!(EquippedProfileItems, m_bHasProfileModifier) == 18);
const _: () = assert!(offset_of!(EquippedProfileItems, m_bHasProfileBackground) == 19);
const _: () = assert!(offset_of!(EquippedProfileItems, m_bHasMiniProfileBackground) == 20);

#[repr(C)]
#[derive(Default)]
pub struct FriendsGetFollowerCount {
    pub m_eResult: i32,
    pub _pad0: u32,
    pub m_steamID: u64,
    pub m_nCount: i32,
    pub _pad1: u32,
}
const _: () = assert!(size_of::<FriendsGetFollowerCount>() == 24);
const _: () = assert!(offset_of!(FriendsGetFollowerCount, m_eResult) == 0);
const _: () = assert!(offset_of!(FriendsGetFollowerCount, m_steamID) == 8);
const _: () = assert!(offset_of!(FriendsGetFollowerCount, m_nCount) == 16);

#[repr(C)]
#[derive(Default)]
pub struct FriendsIsFollowing {
    pub m_eResult: i32,
    pub _pad0: u32,
    pub m_steamID: u64,
    pub m_bIsFollowing: u8,
    pub _pad1: [u8; 7],
}
const _: () = assert!(size_of::<FriendsIsFollowing>() == 24);
const _: () = assert!(offset_of!(FriendsIsFollowing, m_eResult) == 0);
const _: () = assert!(offset_of!(FriendsIsFollowing, m_steamID) == 8);
const _: () = assert!(offset_of!(FriendsIsFollowing, m_bIsFollowing) == 16);

#[repr(C)]
pub struct FriendsEnumerateFollowingList {
    pub m_eResult: i32,
    pub _pad: u32,
    pub m_rgSteamID: [u64; 50],
    pub m_nResultsReturned: i32,
    pub m_nTotalResultCount: i32,
}
const _: () = assert!(size_of::<FriendsEnumerateFollowingList>() == 416);
const _: () = assert!(offset_of!(FriendsEnumerateFollowingList, m_eResult) == 0);
const _: () = assert!(offset_of!(FriendsEnumerateFollowingList, m_rgSteamID) == 8);
const _: () = assert!(offset_of!(FriendsEnumerateFollowingList, m_nResultsReturned) == 408);
const _: () = assert!(offset_of!(FriendsEnumerateFollowingList, m_nTotalResultCount) == 412);

#[repr(C)]
pub struct RemoteStorageDownloadUGCResult {
    pub m_eResult: i32,
    pub _pad0: u32,
    pub m_hFile: u64,
    pub m_nAppID: u32,
    pub m_nSizeInBytes: i32,
    pub m_pchFileName: [u8; 260],
    pub _pad1: u32,
    pub m_ulSteamIDOwner: u64,
}
const _: () = assert!(size_of::<RemoteStorageDownloadUGCResult>() == 296);
const _: () = assert!(offset_of!(RemoteStorageDownloadUGCResult, m_eResult) == 0);
const _: () = assert!(offset_of!(RemoteStorageDownloadUGCResult, m_hFile) == 8);
const _: () = assert!(offset_of!(RemoteStorageDownloadUGCResult, m_nAppID) == 16);
const _: () = assert!(offset_of!(RemoteStorageDownloadUGCResult, m_nSizeInBytes) == 20);
const _: () = assert!(offset_of!(RemoteStorageDownloadUGCResult, m_pchFileName) == 24);
const _: () = assert!(offset_of!(RemoteStorageDownloadUGCResult, m_ulSteamIDOwner) == 288);

#[repr(C)]
#[derive(Default)]
pub struct RemoteStorageSubscribePublishedFileResult {
    pub m_eResult: i32,
    pub _pad: u32,
    pub m_nPublishedFileId: u64,
}
const _: () = assert!(size_of::<RemoteStorageSubscribePublishedFileResult>() == 16);

#[repr(C)]
#[derive(Default)]
pub struct RemoteStorageUnsubscribePublishedFileResult {
    pub m_eResult: i32,
    pub _pad: u32,
    pub m_nPublishedFileId: u64,
}
const _: () = assert!(size_of::<RemoteStorageUnsubscribePublishedFileResult>() == 16);

#[repr(C)]
pub struct SteamUGCQueryCompleted {
    pub m_handle: u64,
    pub m_eResult: i32,
    pub m_unNumResultsReturned: u32,
    pub m_unTotalMatchingResults: u32,
    pub m_bCachedData: u8,
    pub m_rgchNextCursor: [u8; 256],
    pub _pad: [u8; 3],
}
const _: () = assert!(size_of::<SteamUGCQueryCompleted>() == 280);
const _: () = assert!(offset_of!(SteamUGCQueryCompleted, m_handle) == 0);
const _: () = assert!(offset_of!(SteamUGCQueryCompleted, m_eResult) == 8);
const _: () = assert!(offset_of!(SteamUGCQueryCompleted, m_unNumResultsReturned) == 12);
const _: () = assert!(offset_of!(SteamUGCQueryCompleted, m_unTotalMatchingResults) == 16);
const _: () = assert!(offset_of!(SteamUGCQueryCompleted, m_bCachedData) == 20);
const _: () = assert!(offset_of!(SteamUGCQueryCompleted, m_rgchNextCursor) == 21);

#[repr(C)]
#[derive(Default)]
pub struct SteamUGCRequestUGCDetailsResultMinimal {
    pub m_eResult: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<SteamUGCRequestUGCDetailsResultMinimal>() == 8);

#[repr(C)]
#[derive(Default)]
pub struct SteamAPICallCompleted {
    pub m_hAsyncCall: u64,
    pub m_iCallback: i32,
    pub m_cubParam: u32,
}
const _: () = assert!(size_of::<SteamAPICallCompleted>() == 16);
const _: () = assert!(offset_of!(SteamAPICallCompleted, m_hAsyncCall) == 0);
const _: () = assert!(offset_of!(SteamAPICallCompleted, m_iCallback) == 8);
const _: () = assert!(offset_of!(SteamAPICallCompleted, m_cubParam) == 12);

#[repr(C)]
#[derive(Default)]
pub struct CheckFileSignature {
    pub m_eCheckFileSignature: i32,
}
const _: () = assert!(size_of::<CheckFileSignature>() == 4);

#[repr(C)]
pub struct StoreAuthURLResponse {
    pub m_szURL: [u8; 512],
}
const _: () = assert!(size_of::<StoreAuthURLResponse>() == 512);

#[repr(C)]
#[derive(Default)]
pub struct MarketEligibilityResponse {
    pub m_bAllowed: bool,
    pub _pad0: [u8; 3],
    pub m_eNotAllowedReason: i32,
    pub m_rtAllowedAtTime: u32,
    pub m_cdaySteamGuardRequiredDays: i32,
    pub m_cdayNewDeviceCooldown: i32,
}
const _: () = assert!(size_of::<MarketEligibilityResponse>() == 20);
const _: () = assert!(offset_of!(MarketEligibilityResponse, m_bAllowed) == 0);
const _: () = assert!(offset_of!(MarketEligibilityResponse, m_eNotAllowedReason) == 4);
const _: () = assert!(offset_of!(MarketEligibilityResponse, m_rtAllowedAtTime) == 8);
const _: () = assert!(offset_of!(MarketEligibilityResponse, m_cdaySteamGuardRequiredDays) == 12);
const _: () = assert!(offset_of!(MarketEligibilityResponse, m_cdayNewDeviceCooldown) == 16);

#[repr(C)]
#[derive(Default)]
pub struct DurationControl {
    pub m_eResult: i32,
    pub m_appid: u32,
    pub m_bApplicable: bool,
    pub _pad0: [u8; 3],
    pub m_csecsLast5h: i32,
    pub m_progress: i32,
    pub m_notification: i32,
    pub m_csecsToday: i32,
    pub m_csecsRemaining: i32,
}
const _: () = assert!(size_of::<DurationControl>() == 32);
const _: () = assert!(offset_of!(DurationControl, m_eResult) == 0);
const _: () = assert!(offset_of!(DurationControl, m_appid) == 4);
const _: () = assert!(offset_of!(DurationControl, m_bApplicable) == 8);
const _: () = assert!(offset_of!(DurationControl, m_csecsLast5h) == 12);
const _: () = assert!(offset_of!(DurationControl, m_progress) == 16);
const _: () = assert!(offset_of!(DurationControl, m_notification) == 20);
const _: () = assert!(offset_of!(DurationControl, m_csecsToday) == 24);
const _: () = assert!(offset_of!(DurationControl, m_csecsRemaining) == 28);

#[repr(C)]
#[derive(Default)]
pub struct ValidateAuthTicketResponse {
    pub m_SteamID: u64,
    pub m_eAuthSessionResponse: i32,
    pub _pad: u32,
    pub m_OwnerSteamID: u64,
}
const _: () = assert!(size_of::<ValidateAuthTicketResponse>() == 24);
const _: () = assert!(offset_of!(ValidateAuthTicketResponse, m_SteamID) == 0);
const _: () = assert!(offset_of!(ValidateAuthTicketResponse, m_eAuthSessionResponse) == 8);
const _: () = assert!(offset_of!(ValidateAuthTicketResponse, m_OwnerSteamID) == 16);

#[repr(C)]
#[derive(Default)]
pub struct SteamServersDisconnected {
    pub m_eResult: i32,
}
const _: () = assert!(size_of::<SteamServersDisconnected>() == 4);
const _: () = assert!(offset_of!(SteamServersDisconnected, m_eResult) == 0);

#[repr(C)]
#[derive(Default)]
pub struct SetPersonaNameResponse {
    pub m_bSuccess: bool,
    pub m_bLocalSuccess: bool,
    pub _pad: [u8; 2],
    pub m_result: i32,
}
const _: () = assert!(size_of::<SetPersonaNameResponse>() == 8);
const _: () = assert!(offset_of!(SetPersonaNameResponse, m_bSuccess) == 0);
const _: () = assert!(offset_of!(SetPersonaNameResponse, m_bLocalSuccess) == 1);
const _: () = assert!(offset_of!(SetPersonaNameResponse, m_result) == 4);

#[repr(C)]
#[derive(Default)]
pub struct RemoteStorageAppSyncedClient {
    pub m_nAppID: u32,
    pub m_eResult: i32,
    pub m_unNumDownloads: i32,
}
const _: () = assert!(size_of::<RemoteStorageAppSyncedClient>() == 12);

#[repr(C)]
#[derive(Default)]
pub struct RemoteStorageFileWriteAsyncComplete {
    pub m_eResult: i32,
}
const _: () = assert!(size_of::<RemoteStorageFileWriteAsyncComplete>() == 4);

#[repr(C)]
#[derive(Default)]
pub struct RemoteStorageFileReadAsyncComplete {
    pub m_hFileReadAsync: u64,
    pub m_eResult: i32,
    pub m_nOffset: u32,
    pub m_cubRead: u32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<RemoteStorageFileReadAsyncComplete>() == 24);
const _: () = assert!(offset_of!(RemoteStorageFileReadAsyncComplete, m_hFileReadAsync) == 0);
const _: () = assert!(offset_of!(RemoteStorageFileReadAsyncComplete, m_eResult) == 8);
const _: () = assert!(offset_of!(RemoteStorageFileReadAsyncComplete, m_nOffset) == 12);
const _: () = assert!(offset_of!(RemoteStorageFileReadAsyncComplete, m_cubRead) == 16);

#[repr(C)]
pub struct RemoteStorageFileShareResult {
    pub m_eResult: i32,
    pub _pad0: u32,
    pub m_hFile: u64,
    pub m_rgchFilename: [u8; 260],
    pub _pad1: [u8; 4],
}
const _: () = assert!(size_of::<RemoteStorageFileShareResult>() == 280);
const _: () = assert!(offset_of!(RemoteStorageFileShareResult, m_eResult) == 0);
const _: () = assert!(offset_of!(RemoteStorageFileShareResult, m_hFile) == 8);
const _: () = assert!(offset_of!(RemoteStorageFileShareResult, m_rgchFilename) == 16);

#[repr(C)]
#[derive(Default)]
pub struct FileDetailsResult {
    pub m_eResult: i32,
    pub _pad0: u32,
    pub m_ulFileSize: u64,
    pub m_FileSHA: [u8; 20],
    pub m_unFlags: u32,
}
const _: () = assert!(size_of::<FileDetailsResult>() == 40);
const _: () = assert!(offset_of!(FileDetailsResult, m_eResult) == 0);
const _: () = assert!(offset_of!(FileDetailsResult, m_ulFileSize) == 8);
const _: () = assert!(offset_of!(FileDetailsResult, m_FileSHA) == 16);
const _: () = assert!(offset_of!(FileDetailsResult, m_unFlags) == 36);

#[repr(C)]
#[derive(Default)]
pub struct PersonaStateChange {
    pub m_ulSteamID: u64,
    pub m_nChangeFlags: i32,
    pub _pad: u32,
}
const _: () = assert!(size_of::<PersonaStateChange>() == 16);
const _: () = assert!(offset_of!(PersonaStateChange, m_ulSteamID) == 0);
const _: () = assert!(offset_of!(PersonaStateChange, m_nChangeFlags) == 8);

#[repr(C)]
#[derive(Default)]
pub struct AvatarImageLoaded {
    pub m_steamID: u64,
    pub m_iImage: i32,
    pub m_iWide: i32,
    pub m_iTall: i32,
}
const _: () = assert!(size_of::<AvatarImageLoaded>() == 24);
const _: () = assert!(offset_of!(AvatarImageLoaded, m_steamID) == 0);
const _: () = assert!(offset_of!(AvatarImageLoaded, m_iImage) == 8);
const _: () = assert!(offset_of!(AvatarImageLoaded, m_iWide) == 12);
const _: () = assert!(offset_of!(AvatarImageLoaded, m_iTall) == 16);

#[repr(C)]
#[derive(Default)]
pub struct FriendRichPresenceUpdate {
    pub m_steamIDFriend: u64,
    pub m_nAppID: u32,
}
const _: () = assert!(size_of::<FriendRichPresenceUpdate>() == 16);
const _: () = assert!(offset_of!(FriendRichPresenceUpdate, m_steamIDFriend) == 0);
const _: () = assert!(offset_of!(FriendRichPresenceUpdate, m_nAppID) == 8);

#[repr(C)]
pub struct UserAchievementStored {
    pub m_nGameID: u64,
    pub m_bGroupAchievement: bool,
    pub m_rgchAchievementName: [u8; K_ACHIEVEMENT_NAME_MAX],
    pub m_nCurProgress: u32,
    pub m_nMaxProgress: u32,
}
const _: () = assert!(size_of::<UserAchievementStored>() == 152);
const _: () = assert!(offset_of!(UserAchievementStored, m_nGameID) == 0);
const _: () = assert!(offset_of!(UserAchievementStored, m_bGroupAchievement) == 8);
const _: () = assert!(offset_of!(UserAchievementStored, m_rgchAchievementName) == 9);
const _: () = assert!(offset_of!(UserAchievementStored, m_nCurProgress) == 140);
const _: () = assert!(offset_of!(UserAchievementStored, m_nMaxProgress) == 144);

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn callback_payload_sizes_match_static_asserts() {
        // The const _: () = assert!(...) lines above already enforce sizes
        // at compile time; this test merely keeps a runtime witness so
        // mistakes surface in cargo test output.
        assert_eq!(size_of::<UserStatsReceived>(), 24);
        assert_eq!(size_of::<UserAchievementStored>(), 152);
        assert_eq!(size_of::<GetTicketForWebApiResponse>(), 2572);
        assert_eq!(size_of::<FriendsEnumerateFollowingList>(), 416);
    }
}
