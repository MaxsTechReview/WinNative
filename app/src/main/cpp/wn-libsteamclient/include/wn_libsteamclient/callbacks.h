#pragma once


#include <cstdint>
#include <cstddef>

namespace wn_libsteamclient::callbacks {

constexpr int kSteamServersConnected    = 101;
constexpr int kSteamServerConnectFailure = 102;
constexpr int kSteamServersDisconnected = 103;
constexpr int kIPCFailure               = 117;  // k_iSteamUserCallbacks + 17 — single-byte failure type
constexpr int kValidateAuthTicketResponse = 143; // k_iSteamUserCallbacks + 43
constexpr int kEncryptedAppTicketResponse = 154; // k_iSteamUserCallbacks + 54
constexpr int kGetAuthSessionTicketResponse = 163; // k_iSteamUserCallbacks + 63
constexpr int kGetTicketForWebApiResponse   = 168; // k_iSteamUserCallbacks + 68
constexpr int kStoreAuthURLResponse         = 165; // k_iSteamUserCallbacks + 65
constexpr int kMarketEligibilityResponse    = 166; // k_iSteamUserCallbacks + 66
constexpr int kDurationControl              = 167; // k_iSteamUserCallbacks + 67

constexpr int kSteamShutdown            = 704;  // k_iSteamUtilsCallbacks + 4 — empty marker
constexpr int kSteamAPICallCompleted    = 703;  // k_iSteamUtilsCallbacks + 3
constexpr int kCheckFileSignature       = 705;  // k_iSteamUtilsCallbacks + 5
constexpr int kLeaderboardFindResult         = 1104; // base + 4
constexpr int kLeaderboardScoresDownloaded   = 1105; // base + 5
constexpr int kLeaderboardScoreUploaded      = 1106; // base + 6
constexpr int kNumberOfCurrentPlayers        = 1107; // base + 7
constexpr int kGlobalAchievementPercentages  = 1110; // base + 10
constexpr int kLeaderboardUGCSet             = 1111; // base + 11
constexpr int kGlobalStatsReceived           = 1112; // base + 12
constexpr int kClanOfficerListResponse      = 1335; // base + 35
constexpr int kDownloadClanActivityCountsResult = 1341; // base + 41
constexpr int kJoinClanChatRoomCompletion   = 1342; // base + 42
constexpr int kFriendsGetFollowerCount      = 1344;
constexpr int kFriendsIsFollowing           = 1345;
constexpr int kFriendsEnumerateFollowingList = 1346;
constexpr int kEquippedProfileItems         = 1351; // base + 51
constexpr int kRemoteStorageSubscribePublishedFile   = 1313; // base + 13
constexpr int kRemoteStorageUnsubscribePublishedFile = 1315; // base + 15
constexpr int kRemoteStorageDownloadUGC              = 1317; // base + 17
constexpr int kSteamUGCQueryCompleted        = 3401; // base + 1
constexpr int kSteamUGCRequestUGCDetails     = 3402; // base + 2
constexpr int kSteamInventoryEligiblePromoItemDefIDs = 4703;
constexpr int kSteamInventoryStartPurchaseResult     = 4704;
constexpr int kSteamInventoryRequestPricesResult     = 4705;
constexpr int kLobbyEnter                    = 504; // base + 4
constexpr int kLobbyMatchList                = 510; // base + 10
constexpr int kLobbyCreated                  = 513; // base + 13
constexpr int kGameOverlayActivated     = 731;  // k_iSteamUtilsCallbacks + 31 — bool m_bActive

constexpr int kUserStatsReceived = 1101;
constexpr int kUserStatsStored   = 1102;
constexpr int kUserAchievementStored = 1103;

constexpr int kPersonaStateChange  = 1304;
constexpr int kSetPersonaNameResponse = 1332;
constexpr int kAvatarImageLoaded        = 1334;
constexpr int kFriendRichPresenceUpdate = 1336;

constexpr int kRemoteStorageAppSyncedClient = 1301;
constexpr int kRemoteStorageAppSyncedServer = 1302;
constexpr int kRemoteStorageFileWriteAsyncComplete = 1331; // base+31
constexpr int kRemoteStorageFileReadAsyncComplete  = 1332; // base+32
constexpr int kRemoteStorageFileShareResult        = 1307; // base+7
constexpr int kFileDetailsResult                   = 1063; // 1040 + 23

constexpr int kPersonaChangeName            = 0x0001;
constexpr int kPersonaChangeStatus          = 0x0002;
constexpr int kPersonaChangeComeOnline      = 0x0004;
constexpr int kPersonaChangeGoneOffline     = 0x0008;
constexpr int kPersonaChangeGamePlayed      = 0x0010;
constexpr int kPersonaChangeAvatar          = 0x0040;
constexpr int kPersonaChangeNameFirstSet    = 0x0400;
constexpr int kPersonaChangeNickname        = 0x1000;

struct UserStatsReceived {
    uint64_t m_nGameID;
    int32_t  m_eResult;      // EResult: 1 = OK, 2 = Fail
    uint32_t _pad;           // pack=8 → uint64 at offset 16
    uint64_t m_steamIDUser;
};
static_assert(sizeof(UserStatsReceived) == 24, "UserStatsReceived size");
static_assert(offsetof(UserStatsReceived, m_nGameID)     == 0,  "off m_nGameID");
static_assert(offsetof(UserStatsReceived, m_eResult)     == 8,  "off m_eResult");
static_assert(offsetof(UserStatsReceived, m_steamIDUser) == 16, "off m_steamIDUser");

struct UserStatsStored {
    uint64_t m_nGameID;
    int32_t  m_eResult;
    uint32_t _pad;           // pack=8 trailing pad
};
static_assert(sizeof(UserStatsStored) == 16, "UserStatsStored size");
static_assert(offsetof(UserStatsStored, m_nGameID) == 0, "off m_nGameID");
static_assert(offsetof(UserStatsStored, m_eResult) == 8, "off m_eResult");

struct SteamServersConnected {
    char _placeholder;
};

struct SteamServerConnectFailure {
    int32_t m_eResult;
    bool    m_bStillRetrying;
    uint8_t _pad[3];
};
static_assert(sizeof(SteamServerConnectFailure) == 8, "SteamServerConnectFailure size");
static_assert(offsetof(SteamServerConnectFailure, m_eResult)        == 0, "off m_eResult");
static_assert(offsetof(SteamServerConnectFailure, m_bStillRetrying) == 4, "off m_bStillRetrying");

struct IPCFailure {
    uint8_t m_eFailureType;
    uint8_t _pad[7];
};
static_assert(sizeof(IPCFailure) == 8, "IPCFailure size");
static_assert(offsetof(IPCFailure, m_eFailureType) == 0, "off m_eFailureType");
constexpr uint8_t kFailureFlushedCallbackQueue = 0;
constexpr uint8_t kFailurePipeFail             = 1;

struct SteamShutdown {
    char _placeholder;
};

struct GameOverlayActivated {
    bool   m_bActive;
    uint8_t _pad[7];
};
static_assert(sizeof(GameOverlayActivated) == 8, "GameOverlayActivated size");
static_assert(offsetof(GameOverlayActivated, m_bActive) == 0, "off m_bActive");

struct EncryptedAppTicketResponse {
    int32_t m_eResult;
};
static_assert(sizeof(EncryptedAppTicketResponse) == 4, "EncryptedAppTicketResponse size");
static_assert(offsetof(EncryptedAppTicketResponse, m_eResult) == 0, "off m_eResult");

struct GetAuthSessionTicketResponse {
    uint32_t m_hAuthTicket;
    int32_t  m_eResult;
};
static_assert(sizeof(GetAuthSessionTicketResponse) == 8, "GetAuthSessionTicketResponse size");
static_assert(offsetof(GetAuthSessionTicketResponse, m_hAuthTicket) == 0, "off m_hAuthTicket");
static_assert(offsetof(GetAuthSessionTicketResponse, m_eResult)     == 4, "off m_eResult");

struct GetTicketForWebApiResponse {
    uint32_t m_hAuthTicket;
    int32_t  m_eResult;
    int32_t  m_cubTicket;
    uint8_t  m_rgubTicket[2560];
};
static_assert(sizeof(GetTicketForWebApiResponse) == 2572,
              "GetTicketForWebApiResponse size");
static_assert(offsetof(GetTicketForWebApiResponse, m_hAuthTicket) == 0,  "off m_hAuthTicket");
static_assert(offsetof(GetTicketForWebApiResponse, m_eResult)     == 4,  "off m_eResult");
static_assert(offsetof(GetTicketForWebApiResponse, m_cubTicket)   == 8,  "off m_cubTicket");
static_assert(offsetof(GetTicketForWebApiResponse, m_rgubTicket)  == 12, "off m_rgubTicket");

struct LeaderboardFindResult {
    uint64_t m_hSteamLeaderboard;
    uint8_t  m_bLeaderboardFound;
    uint8_t  _pad[7];
};
static_assert(sizeof(LeaderboardFindResult) == 16, "LeaderboardFindResult size");
static_assert(offsetof(LeaderboardFindResult, m_hSteamLeaderboard) == 0, "off m_hSteamLeaderboard");
static_assert(offsetof(LeaderboardFindResult, m_bLeaderboardFound) == 8, "off m_bLeaderboardFound");

struct LeaderboardScoresDownloaded {
    uint64_t m_hSteamLeaderboard;
    uint64_t m_hSteamLeaderboardEntries;
    int32_t  m_cEntryCount;
    uint32_t _pad;
};
static_assert(sizeof(LeaderboardScoresDownloaded) == 24, "LeaderboardScoresDownloaded size");
static_assert(offsetof(LeaderboardScoresDownloaded, m_hSteamLeaderboard)        == 0,  "off m_hSteamLeaderboard");
static_assert(offsetof(LeaderboardScoresDownloaded, m_hSteamLeaderboardEntries) == 8,  "off m_hSteamLeaderboardEntries");
static_assert(offsetof(LeaderboardScoresDownloaded, m_cEntryCount)              == 16, "off m_cEntryCount");

struct LeaderboardScoreUploaded {
    uint8_t  m_bSuccess;
    uint8_t  _pad0[7];
    uint64_t m_hSteamLeaderboard;
    int32_t  m_nScore;
    uint8_t  m_bScoreChanged;
    uint8_t  _pad1[3];
    int32_t  m_nGlobalRankNew;
    int32_t  m_nGlobalRankPrevious;
};
static_assert(sizeof(LeaderboardScoreUploaded) == 32, "LeaderboardScoreUploaded size");
static_assert(offsetof(LeaderboardScoreUploaded, m_bSuccess)            == 0,  "off m_bSuccess");
static_assert(offsetof(LeaderboardScoreUploaded, m_hSteamLeaderboard)   == 8,  "off m_hSteamLeaderboard");
static_assert(offsetof(LeaderboardScoreUploaded, m_nScore)              == 16, "off m_nScore");
static_assert(offsetof(LeaderboardScoreUploaded, m_bScoreChanged)       == 20, "off m_bScoreChanged");
static_assert(offsetof(LeaderboardScoreUploaded, m_nGlobalRankNew)      == 24, "off m_nGlobalRankNew");
static_assert(offsetof(LeaderboardScoreUploaded, m_nGlobalRankPrevious) == 28, "off m_nGlobalRankPrevious");

struct NumberOfCurrentPlayers {
    uint8_t m_bSuccess;
    uint8_t _pad0[3];
    int32_t m_cPlayers;
};
static_assert(sizeof(NumberOfCurrentPlayers) == 8, "NumberOfCurrentPlayers size");
static_assert(offsetof(NumberOfCurrentPlayers, m_bSuccess) == 0, "off m_bSuccess");
static_assert(offsetof(NumberOfCurrentPlayers, m_cPlayers) == 4, "off m_cPlayers");

struct GlobalAchievementPercentagesReady {
    uint64_t m_nGameID;
    int32_t  m_eResult;
    uint32_t _pad;
};
static_assert(sizeof(GlobalAchievementPercentagesReady) == 16, "GlobalAchievementPercentagesReady size");
static_assert(offsetof(GlobalAchievementPercentagesReady, m_nGameID) == 0, "off m_nGameID");
static_assert(offsetof(GlobalAchievementPercentagesReady, m_eResult) == 8, "off m_eResult");

struct LeaderboardUGCSet {
    int32_t  m_eResult;
    uint32_t _pad;
    uint64_t m_hSteamLeaderboard;
};
static_assert(sizeof(LeaderboardUGCSet) == 16, "LeaderboardUGCSet size");
static_assert(offsetof(LeaderboardUGCSet, m_eResult)          == 0, "off m_eResult");
static_assert(offsetof(LeaderboardUGCSet, m_hSteamLeaderboard) == 8, "off m_hSteamLeaderboard");

struct GlobalStatsReceived {
    uint64_t m_nGameID;
    int32_t  m_eResult;
    uint32_t _pad;
};
static_assert(sizeof(GlobalStatsReceived) == 16, "GlobalStatsReceived size");
static_assert(offsetof(GlobalStatsReceived, m_nGameID) == 0, "off m_nGameID");
static_assert(offsetof(GlobalStatsReceived, m_eResult) == 8, "off m_eResult");

struct LobbyMatchList {
    uint32_t m_nLobbiesMatching;
};
static_assert(sizeof(LobbyMatchList) == 4, "LobbyMatchList size");
static_assert(offsetof(LobbyMatchList, m_nLobbiesMatching) == 0, "off m_nLobbiesMatching");

struct LobbyCreated {
    int32_t  m_eResult;
    uint32_t _pad;
    uint64_t m_ulSteamIDLobby;
};
static_assert(sizeof(LobbyCreated) == 16, "LobbyCreated size");
static_assert(offsetof(LobbyCreated, m_eResult)        == 0, "off m_eResult");
static_assert(offsetof(LobbyCreated, m_ulSteamIDLobby) == 8, "off m_ulSteamIDLobby");

struct LobbyEnter {
    uint64_t m_ulSteamIDLobby;
    uint32_t m_rgfChatPermissions;
    uint8_t  m_bLocked;
    uint8_t  _pad[3];
    uint32_t m_EChatRoomEnterResponse;
    uint32_t _trail;
};
static_assert(sizeof(LobbyEnter) == 24, "LobbyEnter size");
static_assert(offsetof(LobbyEnter, m_ulSteamIDLobby)         == 0,  "off m_ulSteamIDLobby");
static_assert(offsetof(LobbyEnter, m_rgfChatPermissions)     == 8,  "off m_rgfChatPermissions");
static_assert(offsetof(LobbyEnter, m_bLocked)                == 12, "off m_bLocked");
static_assert(offsetof(LobbyEnter, m_EChatRoomEnterResponse) == 16, "off m_EChatRoomEnterResponse");

struct SteamInventoryEligiblePromoItemDefIDs {
    int32_t  m_result;
    uint32_t _pad0;
    uint64_t m_steamID;
    int32_t  m_numEligiblePromoItemDefs;
    uint8_t  m_bCachedData;
    uint8_t  _pad1[3];
};
static_assert(sizeof(SteamInventoryEligiblePromoItemDefIDs) == 24,
              "SteamInventoryEligiblePromoItemDefIDs size");
static_assert(offsetof(SteamInventoryEligiblePromoItemDefIDs, m_result)                  == 0,  "off m_result");
static_assert(offsetof(SteamInventoryEligiblePromoItemDefIDs, m_steamID)                 == 8,  "off m_steamID");
static_assert(offsetof(SteamInventoryEligiblePromoItemDefIDs, m_numEligiblePromoItemDefs) == 16, "off m_numEligiblePromoItemDefs");
static_assert(offsetof(SteamInventoryEligiblePromoItemDefIDs, m_bCachedData)             == 20, "off m_bCachedData");

struct SteamInventoryStartPurchaseResult {
    int32_t  m_result;
    uint32_t _pad;
    uint64_t m_ulOrderID;
    uint64_t m_ulTransID;
};
static_assert(sizeof(SteamInventoryStartPurchaseResult) == 24,
              "SteamInventoryStartPurchaseResult size");
static_assert(offsetof(SteamInventoryStartPurchaseResult, m_result)    == 0,  "off m_result");
static_assert(offsetof(SteamInventoryStartPurchaseResult, m_ulOrderID) == 8,  "off m_ulOrderID");
static_assert(offsetof(SteamInventoryStartPurchaseResult, m_ulTransID) == 16, "off m_ulTransID");

struct SteamInventoryRequestPricesResult {
    int32_t m_result;
    char    m_rgchCurrency[4];
};
static_assert(sizeof(SteamInventoryRequestPricesResult) == 8,
              "SteamInventoryRequestPricesResult size");
static_assert(offsetof(SteamInventoryRequestPricesResult, m_result)       == 0, "off m_result");
static_assert(offsetof(SteamInventoryRequestPricesResult, m_rgchCurrency) == 4, "off m_rgchCurrency");

struct ClanOfficerListResponse {
    uint64_t m_steamIDClan;
    int32_t  m_cOfficers;
    uint8_t  m_bSuccess;
    uint8_t  _pad[3];
};
static_assert(sizeof(ClanOfficerListResponse) == 16,
              "ClanOfficerListResponse size");
static_assert(offsetof(ClanOfficerListResponse, m_steamIDClan) == 0, "off m_steamIDClan");
static_assert(offsetof(ClanOfficerListResponse, m_cOfficers)   == 8, "off m_cOfficers");
static_assert(offsetof(ClanOfficerListResponse, m_bSuccess)    == 12, "off m_bSuccess");

struct DownloadClanActivityCountsResult {
    uint8_t m_bSuccess;
};
static_assert(sizeof(DownloadClanActivityCountsResult) == 1,
              "DownloadClanActivityCountsResult size");

struct JoinClanChatRoomCompletionResult {
    uint64_t m_steamIDClanChat;
    int32_t  m_eChatRoomEnterResponse;
    uint32_t _pad;
};
static_assert(sizeof(JoinClanChatRoomCompletionResult) == 16,
              "JoinClanChatRoomCompletionResult size");
static_assert(offsetof(JoinClanChatRoomCompletionResult, m_steamIDClanChat)       == 0, "off m_steamIDClanChat");
static_assert(offsetof(JoinClanChatRoomCompletionResult, m_eChatRoomEnterResponse) == 8, "off m_eChatRoomEnterResponse");

struct EquippedProfileItems {
    int32_t  m_eResult;
    uint32_t _pad0;
    uint64_t m_steamID;
    uint8_t  m_bHasAnimatedAvatar;
    uint8_t  m_bHasAvatarFrame;
    uint8_t  m_bHasProfileModifier;
    uint8_t  m_bHasProfileBackground;
    uint8_t  m_bHasMiniProfileBackground;
    uint8_t  _pad1[3];
};
static_assert(sizeof(EquippedProfileItems) == 24,
              "EquippedProfileItems size");
static_assert(offsetof(EquippedProfileItems, m_eResult)                    == 0,  "off m_eResult");
static_assert(offsetof(EquippedProfileItems, m_steamID)                    == 8,  "off m_steamID");
static_assert(offsetof(EquippedProfileItems, m_bHasAnimatedAvatar)         == 16, "off m_bHasAnimatedAvatar");
static_assert(offsetof(EquippedProfileItems, m_bHasAvatarFrame)            == 17, "off m_bHasAvatarFrame");
static_assert(offsetof(EquippedProfileItems, m_bHasProfileModifier)        == 18, "off m_bHasProfileModifier");
static_assert(offsetof(EquippedProfileItems, m_bHasProfileBackground)      == 19, "off m_bHasProfileBackground");
static_assert(offsetof(EquippedProfileItems, m_bHasMiniProfileBackground)  == 20, "off m_bHasMiniProfileBackground");

struct FriendsGetFollowerCount {
    int32_t  m_eResult;
    uint32_t _pad0;
    uint64_t m_steamID;
    int32_t  m_nCount;
    uint32_t _pad1;
};
static_assert(sizeof(FriendsGetFollowerCount) == 24,
              "FriendsGetFollowerCount size");
static_assert(offsetof(FriendsGetFollowerCount, m_eResult) == 0, "off m_eResult");
static_assert(offsetof(FriendsGetFollowerCount, m_steamID) == 8, "off m_steamID");
static_assert(offsetof(FriendsGetFollowerCount, m_nCount)  == 16, "off m_nCount");

struct FriendsIsFollowing {
    int32_t  m_eResult;
    uint32_t _pad0;
    uint64_t m_steamID;
    uint8_t  m_bIsFollowing;
    uint8_t  _pad1[7];
};
static_assert(sizeof(FriendsIsFollowing) == 24,
              "FriendsIsFollowing size");
static_assert(offsetof(FriendsIsFollowing, m_eResult)       == 0, "off m_eResult");
static_assert(offsetof(FriendsIsFollowing, m_steamID)       == 8, "off m_steamID");
static_assert(offsetof(FriendsIsFollowing, m_bIsFollowing)  == 16, "off m_bIsFollowing");

struct FriendsEnumerateFollowingList {
    int32_t  m_eResult;
    uint32_t _pad;
    uint64_t m_rgSteamID[50];
    int32_t  m_nResultsReturned;
    int32_t  m_nTotalResultCount;
};
static_assert(sizeof(FriendsEnumerateFollowingList) == 416,
              "FriendsEnumerateFollowingList size");
static_assert(offsetof(FriendsEnumerateFollowingList, m_eResult)          == 0,   "off m_eResult");
static_assert(offsetof(FriendsEnumerateFollowingList, m_rgSteamID)        == 8,   "off m_rgSteamID");
static_assert(offsetof(FriendsEnumerateFollowingList, m_nResultsReturned) == 408, "off m_nResultsReturned");
static_assert(offsetof(FriendsEnumerateFollowingList, m_nTotalResultCount) == 412, "off m_nTotalResultCount");

struct RemoteStorageDownloadUGCResult {
    int32_t  m_eResult;
    uint32_t _pad0;
    uint64_t m_hFile;
    uint32_t m_nAppID;
    int32_t  m_nSizeInBytes;
    char     m_pchFileName[260];
    uint32_t _pad1;
    uint64_t m_ulSteamIDOwner;
};
static_assert(sizeof(RemoteStorageDownloadUGCResult) == 296,
              "RemoteStorageDownloadUGCResult size");
static_assert(offsetof(RemoteStorageDownloadUGCResult, m_eResult)        == 0,   "off m_eResult");
static_assert(offsetof(RemoteStorageDownloadUGCResult, m_hFile)          == 8,   "off m_hFile");
static_assert(offsetof(RemoteStorageDownloadUGCResult, m_nAppID)         == 16,  "off m_nAppID");
static_assert(offsetof(RemoteStorageDownloadUGCResult, m_nSizeInBytes)   == 20,  "off m_nSizeInBytes");
static_assert(offsetof(RemoteStorageDownloadUGCResult, m_pchFileName)    == 24,  "off m_pchFileName");
static_assert(offsetof(RemoteStorageDownloadUGCResult, m_ulSteamIDOwner) == 288, "off m_ulSteamIDOwner");

struct RemoteStorageSubscribePublishedFileResult {
    int32_t  m_eResult;
    uint32_t _pad;
    uint64_t m_nPublishedFileId;
};
static_assert(sizeof(RemoteStorageSubscribePublishedFileResult) == 16,
              "RemoteStorageSubscribePublishedFileResult size");
static_assert(offsetof(RemoteStorageSubscribePublishedFileResult, m_eResult) == 0,
              "off m_eResult");
static_assert(offsetof(RemoteStorageSubscribePublishedFileResult, m_nPublishedFileId) == 8,
              "off m_nPublishedFileId");

struct RemoteStorageUnsubscribePublishedFileResult {
    int32_t  m_eResult;
    uint32_t _pad;
    uint64_t m_nPublishedFileId;
};
static_assert(sizeof(RemoteStorageUnsubscribePublishedFileResult) == 16,
              "RemoteStorageUnsubscribePublishedFileResult size");
static_assert(offsetof(RemoteStorageUnsubscribePublishedFileResult, m_eResult) == 0,
              "off m_eResult");
static_assert(offsetof(RemoteStorageUnsubscribePublishedFileResult, m_nPublishedFileId) == 8,
              "off m_nPublishedFileId");

struct SteamUGCQueryCompleted {
    uint64_t m_handle;
    int32_t  m_eResult;
    uint32_t m_unNumResultsReturned;
    uint32_t m_unTotalMatchingResults;
    uint8_t  m_bCachedData;
    char     m_rgchNextCursor[256];
    uint8_t  _pad[3];
};
static_assert(sizeof(SteamUGCQueryCompleted) == 280,
              "SteamUGCQueryCompleted size");
static_assert(offsetof(SteamUGCQueryCompleted, m_handle)                 == 0,  "off m_handle");
static_assert(offsetof(SteamUGCQueryCompleted, m_eResult)                == 8,  "off m_eResult");
static_assert(offsetof(SteamUGCQueryCompleted, m_unNumResultsReturned)   == 12, "off m_unNumResultsReturned");
static_assert(offsetof(SteamUGCQueryCompleted, m_unTotalMatchingResults) == 16, "off m_unTotalMatchingResults");
static_assert(offsetof(SteamUGCQueryCompleted, m_bCachedData)            == 20, "off m_bCachedData");
static_assert(offsetof(SteamUGCQueryCompleted, m_rgchNextCursor)         == 21, "off m_rgchNextCursor");

struct SteamUGCRequestUGCDetailsResultMinimal {
    int32_t  m_eResult;
    uint32_t _pad;
};
static_assert(sizeof(SteamUGCRequestUGCDetailsResultMinimal) == 8,
              "SteamUGCRequestUGCDetailsResultMinimal size");

struct SteamAPICallCompleted {
    uint64_t m_hAsyncCall;
    int32_t  m_iCallback;
    uint32_t m_cubParam;
};
static_assert(sizeof(SteamAPICallCompleted) == 16,
              "SteamAPICallCompleted size");
static_assert(offsetof(SteamAPICallCompleted, m_hAsyncCall) == 0, "off m_hAsyncCall");
static_assert(offsetof(SteamAPICallCompleted, m_iCallback)  == 8, "off m_iCallback");
static_assert(offsetof(SteamAPICallCompleted, m_cubParam)   == 12, "off m_cubParam");

struct CheckFileSignature {
    int32_t m_eCheckFileSignature;
};
static_assert(sizeof(CheckFileSignature) == 4, "CheckFileSignature size");
static_assert(offsetof(CheckFileSignature, m_eCheckFileSignature) == 0,
              "off m_eCheckFileSignature");

struct StoreAuthURLResponse {
    char m_szURL[512];
};
static_assert(sizeof(StoreAuthURLResponse) == 512, "StoreAuthURLResponse size");
static_assert(offsetof(StoreAuthURLResponse, m_szURL) == 0, "off m_szURL");

struct MarketEligibilityResponse {
    bool     m_bAllowed;
    uint8_t  _pad0[3];
    int32_t  m_eNotAllowedReason;
    uint32_t m_rtAllowedAtTime;
    int32_t  m_cdaySteamGuardRequiredDays;
    int32_t  m_cdayNewDeviceCooldown;
};
static_assert(sizeof(MarketEligibilityResponse) == 20, "MarketEligibilityResponse size");
static_assert(offsetof(MarketEligibilityResponse, m_bAllowed)                   == 0,  "off m_bAllowed");
static_assert(offsetof(MarketEligibilityResponse, m_eNotAllowedReason)          == 4,  "off m_eNotAllowedReason");
static_assert(offsetof(MarketEligibilityResponse, m_rtAllowedAtTime)            == 8,  "off m_rtAllowedAtTime");
static_assert(offsetof(MarketEligibilityResponse, m_cdaySteamGuardRequiredDays) == 12, "off m_cdaySteamGuardRequiredDays");
static_assert(offsetof(MarketEligibilityResponse, m_cdayNewDeviceCooldown)      == 16, "off m_cdayNewDeviceCooldown");

struct DurationControl {
    int32_t  m_eResult;
    uint32_t m_appid;
    bool     m_bApplicable;
    uint8_t  _pad0[3];
    int32_t  m_csecsLast5h;
    int32_t  m_progress;
    int32_t  m_notification;
    int32_t  m_csecsToday;
    int32_t  m_csecsRemaining;
};
static_assert(sizeof(DurationControl) == 32, "DurationControl size");
static_assert(offsetof(DurationControl, m_eResult)        == 0,  "off m_eResult");
static_assert(offsetof(DurationControl, m_appid)          == 4,  "off m_appid");
static_assert(offsetof(DurationControl, m_bApplicable)    == 8,  "off m_bApplicable");
static_assert(offsetof(DurationControl, m_csecsLast5h)    == 12, "off m_csecsLast5h");
static_assert(offsetof(DurationControl, m_progress)       == 16, "off m_progress");
static_assert(offsetof(DurationControl, m_notification)   == 20, "off m_notification");
static_assert(offsetof(DurationControl, m_csecsToday)     == 24, "off m_csecsToday");
static_assert(offsetof(DurationControl, m_csecsRemaining) == 28, "off m_csecsRemaining");

struct ValidateAuthTicketResponse {
    uint64_t m_SteamID;
    int32_t  m_eAuthSessionResponse;
    uint32_t _pad;
    uint64_t m_OwnerSteamID;
};
static_assert(sizeof(ValidateAuthTicketResponse) == 24, "ValidateAuthTicketResponse size");
static_assert(offsetof(ValidateAuthTicketResponse, m_SteamID)              == 0,  "off m_SteamID");
static_assert(offsetof(ValidateAuthTicketResponse, m_eAuthSessionResponse) == 8,  "off m_eAuthSessionResponse");
static_assert(offsetof(ValidateAuthTicketResponse, m_OwnerSteamID)         == 16, "off m_OwnerSteamID");

struct SteamServersDisconnected {
    int32_t m_eResult;
};
static_assert(sizeof(SteamServersDisconnected) == 4, "SteamServersDisconnected size");
static_assert(offsetof(SteamServersDisconnected, m_eResult) == 0, "off m_eResult");

struct SetPersonaNameResponse {
    bool     m_bSuccess;
    bool     m_bLocalSuccess;
    uint8_t  _pad[2];
    int32_t  m_result;
};
static_assert(sizeof(SetPersonaNameResponse) == 8, "SetPersonaNameResponse size");
static_assert(offsetof(SetPersonaNameResponse, m_bSuccess)      == 0, "off m_bSuccess");
static_assert(offsetof(SetPersonaNameResponse, m_bLocalSuccess) == 1, "off m_bLocalSuccess");
static_assert(offsetof(SetPersonaNameResponse, m_result)        == 4, "off m_result");

struct RemoteStorageAppSyncedClient {
    uint32_t m_nAppID;
    int32_t  m_eResult;
    int32_t  m_unNumDownloads;
};
static_assert(sizeof(RemoteStorageAppSyncedClient) == 12,
              "RemoteStorageAppSyncedClient size");
static_assert(offsetof(RemoteStorageAppSyncedClient, m_nAppID)         == 0, "off m_nAppID");
static_assert(offsetof(RemoteStorageAppSyncedClient, m_eResult)        == 4, "off m_eResult");
static_assert(offsetof(RemoteStorageAppSyncedClient, m_unNumDownloads) == 8, "off m_unNumDownloads");

struct RemoteStorageFileWriteAsyncComplete {
    int32_t m_eResult;
};
static_assert(sizeof(RemoteStorageFileWriteAsyncComplete) == 4,
              "RemoteStorageFileWriteAsyncComplete size");
static_assert(offsetof(RemoteStorageFileWriteAsyncComplete, m_eResult) == 0,
              "off m_eResult");

struct RemoteStorageFileReadAsyncComplete {
    uint64_t m_hFileReadAsync;
    int32_t  m_eResult;
    uint32_t m_nOffset;
    uint32_t m_cubRead;
    uint32_t _pad;
};
static_assert(sizeof(RemoteStorageFileReadAsyncComplete) == 24,
              "RemoteStorageFileReadAsyncComplete size");
static_assert(offsetof(RemoteStorageFileReadAsyncComplete, m_hFileReadAsync) == 0,  "off m_hFileReadAsync");
static_assert(offsetof(RemoteStorageFileReadAsyncComplete, m_eResult)        == 8,  "off m_eResult");
static_assert(offsetof(RemoteStorageFileReadAsyncComplete, m_nOffset)        == 12, "off m_nOffset");
static_assert(offsetof(RemoteStorageFileReadAsyncComplete, m_cubRead)        == 16, "off m_cubRead");

struct RemoteStorageFileShareResult {
    int32_t  m_eResult;
    uint32_t _pad0;
    uint64_t m_hFile;
    char     m_rgchFilename[260];
    uint8_t  _pad1[4];
};
static_assert(sizeof(RemoteStorageFileShareResult) == 280,
              "RemoteStorageFileShareResult size");
static_assert(offsetof(RemoteStorageFileShareResult, m_eResult)      == 0,  "off m_eResult");
static_assert(offsetof(RemoteStorageFileShareResult, m_hFile)        == 8,  "off m_hFile");
static_assert(offsetof(RemoteStorageFileShareResult, m_rgchFilename) == 16, "off m_rgchFilename");

struct FileDetailsResult {
    int32_t  m_eResult;
    uint32_t _pad0;
    uint64_t m_ulFileSize;
    uint8_t  m_FileSHA[20];
    uint32_t m_unFlags;
};
static_assert(sizeof(FileDetailsResult) == 40,
              "FileDetailsResult size");
static_assert(offsetof(FileDetailsResult, m_eResult)    == 0,  "off m_eResult");
static_assert(offsetof(FileDetailsResult, m_ulFileSize) == 8,  "off m_ulFileSize");
static_assert(offsetof(FileDetailsResult, m_FileSHA)    == 16, "off m_FileSHA");
static_assert(offsetof(FileDetailsResult, m_unFlags)    == 36, "off m_unFlags");

struct PersonaStateChange {
    uint64_t m_ulSteamID;
    int32_t  m_nChangeFlags;
    uint32_t _pad;
};
static_assert(sizeof(PersonaStateChange) == 16, "PersonaStateChange size");
static_assert(offsetof(PersonaStateChange, m_ulSteamID)    == 0, "off m_ulSteamID");
static_assert(offsetof(PersonaStateChange, m_nChangeFlags) == 8, "off m_nChangeFlags");

struct AvatarImageLoaded {
    uint64_t m_steamID;
    int32_t  m_iImage;
    int32_t  m_iWide;
    int32_t  m_iTall;
};
static_assert(sizeof(AvatarImageLoaded) == 24, "AvatarImageLoaded size");
static_assert(offsetof(AvatarImageLoaded, m_steamID) == 0,  "off m_steamID");
static_assert(offsetof(AvatarImageLoaded, m_iImage)  == 8,  "off m_iImage");
static_assert(offsetof(AvatarImageLoaded, m_iWide)   == 12, "off m_iWide");
static_assert(offsetof(AvatarImageLoaded, m_iTall)   == 16, "off m_iTall");

struct FriendRichPresenceUpdate {
    uint64_t m_steamIDFriend;
    uint32_t m_nAppID;
};
static_assert(sizeof(FriendRichPresenceUpdate) == 16, "FriendRichPresenceUpdate size");
static_assert(offsetof(FriendRichPresenceUpdate, m_steamIDFriend) == 0, "off m_steamIDFriend");
static_assert(offsetof(FriendRichPresenceUpdate, m_nAppID)        == 8, "off m_nAppID");

constexpr size_t kAchievementNameMax = 128;
struct UserAchievementStored {
    uint64_t  m_nGameID;
    bool      m_bGroupAchievement;
    char      m_rgchAchievementName[kAchievementNameMax];
    uint32_t  m_nCurProgress;
    uint32_t  m_nMaxProgress;
};
static_assert(sizeof(UserAchievementStored) == 152, "UserAchievementStored size");
static_assert(offsetof(UserAchievementStored, m_nGameID)            == 0,   "off m_nGameID");
static_assert(offsetof(UserAchievementStored, m_bGroupAchievement)  == 8,   "off m_bGroupAchievement");
static_assert(offsetof(UserAchievementStored, m_rgchAchievementName)== 9,   "off m_rgchAchievementName");
static_assert(offsetof(UserAchievementStored, m_nCurProgress)       == 140, "off m_nCurProgress");
static_assert(offsetof(UserAchievementStored, m_nMaxProgress)       == 144, "off m_nMaxProgress");

}  // namespace wn_libsteamclient::callbacks
