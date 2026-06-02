
#include <windows.h>
#include <stdint.h>
#include <stddef.h>

#define WN_STEAMAPI_EXPORT __declspec(dllexport)

WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamAppList_GetNumInstalledApps(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamAppList_GetInstalledApps(void* self, void* pvecAppID, uint32_t cMax) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[1])(self, pvecAppID, cMax);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamAppList_GetAppName(void* self, uint32_t appId, void* pName, int cMaxName) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int);
    return ((Fn)vt[2])(self, appId, pName, cMaxName);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamAppList_GetAppInstallDir(void* self, uint32_t appId, void* pDir, int cMaxDir) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int);
    return ((Fn)vt[3])(self, appId, pDir, cMaxDir);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamAppList_GetAppBuildId(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[4])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsSubscribed(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsLowViolence(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsCybercafe(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsVACBanned(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[3])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamApps_GetCurrentGameLanguage(void* self) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*);
    return ((Fn)vt[4])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamApps_GetAvailableGameLanguages(void* self) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*);
    return ((Fn)vt[5])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsSubscribedApp(void* self, uint32_t appId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[6])(self, appId);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsDlcInstalled(void* self, uint32_t appId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[7])(self, appId);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamApps_GetEarliestPurchaseUnixTime(void* self, uint32_t app_id) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[8])(self, app_id);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsSubscribedFromFreeWeekend(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[9])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_GetDLCCount(void* self, uint32_t appId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[10])(self, appId);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BGetDLCDataByIndex(void* self, uint32_t appId, int iDLC, void* pAppID, void* pbAvailable, void* pchName, int cchNameBufferSize) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int, void*, void*, void*, int);
    return ((Fn)vt[11])(self, appId, iDLC, pAppID, pbAvailable, pchName, cchNameBufferSize);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamApps_InstallDLC(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[12])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamApps_UninstallDLC(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[13])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamApps_RequestAppProofOfPurchaseKey(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[14])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_GetCurrentBetaName(void* self, void* pchName, int cchNameBufferSize) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int);
    return ((Fn)vt[15])(self, pchName, cchNameBufferSize);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_MarkContentCorrupt(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamApps_GetInstalledDepots(void* self, uint32_t appID, void* pvecDepots, uint32_t cMaxDepots) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint32_t, void*, uint32_t);
    return ((Fn)vt[17])(self, appID, pvecDepots, cMaxDepots);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamApps_GetAppInstallDir(void* self, uint32_t appId, void* buf, uint32_t cap) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint32_t, void*, uint32_t);
    return ((Fn)vt[18])(self, appId, buf, cap);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsAppInstalled(void* self, uint32_t appId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[19])(self, appId);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamApps_GetAppOwner(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[20])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamApps_GetLaunchQueryParam(void* self, void* _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*);
    return ((Fn)vt[21])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_GetDlcDownloadProgress(void* self, uint32_t appID, void* pBytesDownloaded, void* pBytesTotal) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, void*);
    return ((Fn)vt[22])(self, appID, pBytesDownloaded, pBytesTotal);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_GetAppBuildId(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[23])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamApps_RequestAllProofOfPurchaseKeys(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[24])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamApps_GetFileDetails(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[25])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_GetLaunchCommandLine(void* self, void* buf, int cubMax) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int);
    return ((Fn)vt[26])(self, buf, cubMax);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsSubscribedFromFamilySharing(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[27])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_BIsTimedTrial(void* self, void* pcSecondsAllowed, void* pcSecondsPlayed) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[28])(self, pcSecondsAllowed, pcSecondsPlayed);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamApps_SetDlcContext(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[29])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamClient_CreateSteamPipe(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamClient_BReleaseSteamPipe(void* self, int pipe) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[1])(self, pipe);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamClient_ConnectToGlobalUser(void* self, int pipe) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[2])(self, pipe);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamClient_CreateLocalUser(void* self, void* pipe_inout, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[3])(self, pipe_inout, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamClient_ReleaseUser(void* self, int pipe, int user) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int, int);
    ((Fn)vt[4])(self, pipe, user);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamUser(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[5])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamGameServer(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[6])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamClient_SetLocalIPBinding(void* self, uint32_t _a0, uint16_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint16_t);
    ((Fn)vt[7])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamFriends(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[8])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamUtils(void* self, void* _a0, void* _a1) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*);
    return ((Fn)vt[9])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamMatchmaking(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[10])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamMatchmakingServers(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[11])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamGenericInterface(void* self, int _a0, int _a1, void* version) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[12])(self, _a0, _a1, version);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamUserStats(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[13])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamApps(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[14])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamNetworking(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[15])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamRemoteStorage(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[16])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamScreenshots(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[17])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamUGC(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[18])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamAppList(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[19])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamMusic(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[20])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamMusicRemote(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[21])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamHTMLSurface(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[22])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamClient_Set_SteamAPI_CPostAPIResultInProcess(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[23])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamClient_Remove_SteamAPI_CPostAPIResultInProcess(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[24])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamClient_Set_SteamAPI_CCheckCallbackRegisteredInProcess(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[25])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamInventory(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[26])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamVideo(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[27])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamParentalSettings(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[28])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamInput(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[29])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamParties(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[30])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamClient_GetISteamRemotePlay(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, void*);
    return ((Fn)vt[31])(self, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetPersonaName(void* self) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_SetPersonaName(void* self, void* pchPersonaName) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[1])(self, pchPersonaName);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetPersonaState(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendCount(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetFriendByIndex(void* self, int idx, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, void*);
    return ((Fn)vt[4])(self, idx, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendRelationship(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[5])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendPersonaState(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[6])(self, sid);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetFriendPersonaName(void* self, uint64_t sid) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[7])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendGamePlayed(void* self, uint64_t sid, void* pFriendGameInfo) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[8])(self, sid, pFriendGameInfo);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetFriendPersonaNameHistory(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[9])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendSteamLevel(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[10])(self, sid);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetPlayerNickname(void* self, uint64_t sid) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[11])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendsGroupCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[12])(self);
}
WN_STEAMAPI_EXPORT int16_t SteamAPI_ISteamFriends_GetFriendsGroupIDByIndex(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int16_t (*Fn)(void*, int);
    return ((Fn)vt[13])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetFriendsGroupName(void* self, int16_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int16_t);
    return ((Fn)vt[14])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendsGroupMembersCount(void* self, int16_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int16_t);
    return ((Fn)vt[15])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_GetFriendsGroupMembersList(void* self, int16_t _a0, void* _a1, int _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int16_t, void*, int);
    ((Fn)vt[16])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_HasFriend(void* self, uint64_t sid, int iFriendFlags) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[17])(self, sid, iFriendFlags);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetClanCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[18])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetClanByIndex(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int);
    return ((Fn)vt[19])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetClanName(void* self, uint64_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[20])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetClanTag(void* self, uint64_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[21])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetClanActivityCounts(void* self, uint64_t _a0, void* _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*, void*);
    return ((Fn)vt[22])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_DownloadClanActivityCounts(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*);
    return ((Fn)vt[23])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendCountFromSource(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[24])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetFriendFromSourceByIndex(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[25])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_IsUserInSource(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[26])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_SetInGameVoiceSpeaking(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, int);
    ((Fn)vt[27])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlay(void* self, void* dialog) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[28])(self, dialog);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlayToUser(void* self, void* dialog, uint64_t sid) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, uint64_t);
    ((Fn)vt[29])(self, dialog, sid);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlayToWebPage(void* self, void* url, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*);
    ((Fn)vt[30])(self, url, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlayToStore(void* self, uint32_t appid, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*);
    ((Fn)vt[31])(self, appid, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_SetPlayedWith(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[32])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlayInviteDialog(void* self, uint64_t lobby_sid) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[33])(self, lobby_sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetSmallFriendAvatar(void* self, uint64_t steamID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[34])(self, steamID);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetMediumFriendAvatar(void* self, uint64_t steamID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[35])(self, steamID);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetLargeFriendAvatar(void* self, uint64_t steamID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[36])(self, steamID);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_RequestUserInformation(void* self, uint64_t steamID, int bRequireNameOnly) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[37])(self, steamID, bRequireNameOnly);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_RequestClanOfficerList(void* self, uint64_t clanSid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[38])(self, clanSid);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetClanOwner(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[39])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetClanOfficerCount(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[40])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetClanOfficerByIndex(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[41])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamFriends_GetUserRestrictions(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[42])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_SetRichPresence(void* self, void* pchKey, void* pchValue) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[43])(self, pchKey, pchValue);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ClearRichPresence(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[44])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetFriendRichPresence(void* self, uint64_t steamID, void* pchKey) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[45])(self, steamID, pchKey);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendRichPresenceKeyCount(void* self, uint64_t steamID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[46])(self, steamID);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetFriendRichPresenceKeyByIndex(void* self, uint64_t steamID, int idx) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[47])(self, steamID, idx);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_RequestFriendRichPresence(void* self, uint64_t steamID) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[48])(self, steamID);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_InviteUserToGame(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[49])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetCoplayFriendCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[50])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetCoplayFriend(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int);
    return ((Fn)vt[51])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendCoplayTime(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[52])(self, _a0);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamFriends_GetFriendCoplayGame(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[53])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_JoinClanChatRoom(void* self, uint64_t clanSid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[54])(self, clanSid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_LeaveClanChatRoom(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[55])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetClanChatMemberCount(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[56])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetChatMemberByIndex(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[57])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_SendClanChatMessage(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[58])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetClanChatMessage(void* self, uint64_t _a0, int _a1, void* _a2, int _a3, void* _a4, void* _a5) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int, void*, int, void*, void*);
    return ((Fn)vt[59])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_IsClanChatAdmin(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[60])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_IsClanChatWindowOpenInSteam(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[61])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_OpenClanChatWindowInSteam(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[62])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_CloseClanChatWindowInSteam(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[63])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_SetListenForFriendsMessages(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[64])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_ReplyToFriendMessage(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[65])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetFriendMessage(void* self, uint64_t _a0, int _a1, void* _a2, int _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int, void*, int, void*);
    return ((Fn)vt[66])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_GetFollowerCount(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[67])(self, sid);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_IsFollowing(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[68])(self, sid);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_EnumerateFollowingList(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[69])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_IsClanPublic(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[70])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_IsClanOfficialGameGroup(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[71])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_GetNumChatsWithUnreadPriorityMessages(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[72])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlayRemotePlayTogetherInviteDialog(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[73])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_RegisterProtocolInOverlayBrowser(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[74])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamFriends_ActivateGameOverlayInviteDialogConnectString(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[75])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamFriends_RequestEquippedProfileItems(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[76])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamFriends_BHasEquippedProfileItem(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[77])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamFriends_GetProfileItemPropertyString(void* self, uint64_t _a0, int _a1, int _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t, int, int);
    return ((Fn)vt[78])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamFriends_GetProfileItemPropertyUint(void* self, uint64_t _a0, int _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t, int, int);
    return ((Fn)vt[79])(self, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetProduct(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[0])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetGameDescription(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[1])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetModDir(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetDedicatedServer(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_LogOn(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[4])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_LogOnAnonymous(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[5])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_LogOff(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[6])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_BLoggedOn(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[7])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_BSecure(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[8])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_GetSteamID(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[9])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_WasRestartRequested(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[10])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetMaxPlayerCount(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[11])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetBotPlayerCount(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[12])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetServerName(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[13])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetMapName(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[14])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetPasswordProtected(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[15])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetSpectatorPort(void* self, uint16_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint16_t);
    ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetSpectatorServerName(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[17])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_ClearAllKeyValues(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[18])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetKeyValue(void* self, void* _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*);
    ((Fn)vt[19])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetGameTags(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[20])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetGameData(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[21])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetRegion(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[22])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SetAdvertiseServerActive(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[23])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_GetAuthSessionTicket(void* self, void* _a0, int _a1, void* pcb, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, int, void*, void*);
    return ((Fn)vt[24])(self, _a0, _a1, pcb, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_BeginAuthSession(void* self, void* _a0, int _a1, uint64_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, uint64_t);
    return ((Fn)vt[25])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_EndAuthSession(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[26])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_CancelAuthTicket(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[27])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_UserHasLicenseForApp(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[28])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_RequestUserGroupStatus(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[29])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_GetGameplayStats(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[30])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_GetServerReputation(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[31])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_GetPublicIP(void* self, void* out) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[32])(self, out);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_HandleIncomingPacket(void* self, void* _a0, int _a1, uint32_t _a2, uint16_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, uint32_t, uint16_t);
    return ((Fn)vt[33])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_GetNextOutgoingPacket(void* self, void* _a0, int _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*, void*);
    return ((Fn)vt[34])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_AssociateWithClan(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[35])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_ComputeNewPlayerCompatibility(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[36])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_SendUserConnectAndAuthenticate_DEPRECATED(void* self, uint32_t _a0, void* _a1, uint32_t _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[37])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_CreateUnauthenticatedUserConnection(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[38])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamGameServer_SendUserDisconnect_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[39])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamGameServer_BUpdateUserData(void* self, uint64_t _a0, void* _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, uint32_t);
    return ((Fn)vt[40])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamGameServer_GetAuthTicketForWebApi(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[41])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamHTMLSurface_Init(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamHTMLSurface_Shutdown(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamHTMLSurface_CreateBrowser(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*);
    return ((Fn)vt[2])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_RemoveBrowser(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_LoadURL(void* self, uint32_t _a0, void* _a1, void* _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*, void*);
    ((Fn)vt[4])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetSize(void* self, uint32_t _a0, uint32_t _a1, uint32_t _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint32_t, uint32_t);
    ((Fn)vt[5])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_StopLoad(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[6])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_Reload(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[7])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_GoBack(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[8])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_GoForward(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[9])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_AddHeader(void* self, uint32_t _a0, void* _a1, void* _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*, void*);
    ((Fn)vt[10])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_ExecuteJavascript(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*);
    ((Fn)vt[11])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_MouseUp(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[12])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_MouseDown(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[13])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_MouseDoubleClick(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[14])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_MouseMove(void* self, uint32_t _a0, int _a1, int _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int, int);
    ((Fn)vt[15])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_MouseWheel(void* self, uint32_t _a0, int32_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int32_t);
    ((Fn)vt[16])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_KeyDown(void* self, uint32_t _a0, uint32_t _a1, int _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint32_t, int);
    ((Fn)vt[17])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_KeyUp(void* self, uint32_t _a0, uint32_t _a1, int _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint32_t, int);
    ((Fn)vt[18])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_KeyChar(void* self, uint32_t _a0, uint32_t _a1, int _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint32_t, int);
    ((Fn)vt[19])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetHorizontalScroll(void* self, uint32_t _a0, uint32_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint32_t);
    ((Fn)vt[20])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetVerticalScroll(void* self, uint32_t _a0, uint32_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint32_t);
    ((Fn)vt[21])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetKeyFocus(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[22])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_ViewSource(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[23])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_CopyToClipboard(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[24])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_PasteFromClipboard(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[25])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_Find(void* self, uint32_t _a0, void* _a1, int _a2, int _a3) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*, int, int);
    ((Fn)vt[26])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_StopFind(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[27])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_GetLinkAtPosition(void* self, uint32_t _a0, int _a1, int _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int, int);
    ((Fn)vt[28])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetCookie(void* self, void* _a0, void* _a1, void* _a2, void* _a3, uint32_t _a4, int _a5, int _a6) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*, void*, void*, uint32_t, int, int);
    ((Fn)vt[29])(self, _a0, _a1, _a2, _a3, _a4, _a5, _a6);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetPageScaleFactor(void* self, uint32_t _a0, float _a1, int _a2, int _a3) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, float, int, int);
    ((Fn)vt[30])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetBackgroundMode(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[31])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_SetDPIScalingFactor(void* self, uint32_t _a0, float _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, float);
    ((Fn)vt[32])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_OpenDeveloperTools(void* self, uint32_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t);
    ((Fn)vt[33])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_AllowStartRequest(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[34])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_JSDialogResponse(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, int);
    ((Fn)vt[35])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamHTMLSurface_FileLoadDialogResponse(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*);
    ((Fn)vt[36])(self, _a0, _a1);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_Init(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[0])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_Shutdown(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_SetInputActionManifestFilePath(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_RunFrame(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_BWaitForData(void* self, int _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, uint32_t);
    return ((Fn)vt[4])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_BNewDataAvailable(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[5])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetConnectedControllers(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[6])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_EnableDeviceCallbacks(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[7])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_EnableActionEventCallbacks(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[8])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInput_GetActionSetHandle(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[9])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_ActivateActionSet(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t);
    ((Fn)vt[10])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInput_GetCurrentActionSet(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[11])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_ActivateActionSetLayer(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t);
    ((Fn)vt[12])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_DeactivateActionSetLayer(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t);
    ((Fn)vt[13])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_DeactivateAllActionSetLayers(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[14])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetActiveActionSetLayers(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[15])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInput_GetDigitalActionHandle(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_GetDigitalActionData(void* self, uint64_t _a0, uint64_t _a1, void* outData) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t, void*);
    ((Fn)vt[17])(self, _a0, _a1, outData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetDigitalActionOrigins(void* self, uint64_t _a0, uint64_t _a1, uint64_t _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, uint64_t, void*);
    return ((Fn)vt[18])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetStringForDigitalActionName(void* self, uint64_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[19])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInput_GetAnalogActionHandle(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[20])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_GetAnalogActionData(void* self, uint64_t _a0, uint64_t _a1, void* outData) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t, void*);
    ((Fn)vt[21])(self, _a0, _a1, outData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetAnalogActionOrigins(void* self, uint64_t _a0, uint64_t _a1, uint64_t _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, uint64_t, void*);
    return ((Fn)vt[22])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetGlyphPNGForActionOrigin(void* self, int _a0, int _a1, uint32_t _a2) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, int, uint32_t);
    return ((Fn)vt[23])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetGlyphSVGForActionOrigin(void* self, int _a0, uint32_t _a1) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, uint32_t);
    return ((Fn)vt[24])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetGlyphForActionOrigin_Legacy(void* self, int _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int);
    return ((Fn)vt[25])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetStringForActionOrigin(void* self, int _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int);
    return ((Fn)vt[26])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetStringForAnalogActionName(void* self, uint64_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[27])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_StopAnalogActionMomentum(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t);
    ((Fn)vt[28])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_GetMotionData(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[29])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_TriggerVibration(void* self, uint64_t _a0, uint16_t _a1, uint16_t _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint16_t, uint16_t);
    ((Fn)vt[30])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_TriggerVibrationExtended(void* self, uint64_t _a0, uint16_t _a1, uint16_t _a2, uint16_t _a3, uint16_t _a4) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint16_t, uint16_t, uint16_t, uint16_t);
    ((Fn)vt[31])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_TriggerSimpleHapticEvent(void* self, uint64_t _a0, int _a1, uint8_t _a2, char _a3, uint8_t _a4, char _a5) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, int, uint8_t, char, uint8_t, char);
    ((Fn)vt[32])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_SetLEDColor(void* self, uint64_t _a0, uint8_t _a1, uint8_t _a2, uint8_t _a3, uint32_t _a4) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint8_t, uint8_t, uint8_t, uint32_t);
    ((Fn)vt[33])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_Legacy_TriggerHapticPulse(void* self, uint64_t _a0, int _a1, uint16_t _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, int, uint16_t);
    ((Fn)vt[34])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_Legacy_TriggerRepeatedHapticPulse(void* self, uint64_t _a0, int _a1, uint16_t _a2, uint16_t _a3, uint16_t _a4, uint32_t _a5) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, int, uint16_t, uint16_t, uint16_t, uint32_t);
    ((Fn)vt[35])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_ShowBindingPanel(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[36])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetInputTypeForHandle(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[37])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInput_GetControllerForGamepadIndex(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int);
    return ((Fn)vt[38])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetGamepadIndexForController(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[39])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetStringForXboxOrigin(void* self, int _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int);
    return ((Fn)vt[40])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamInput_GetGlyphForXboxOrigin(void* self, int _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int);
    return ((Fn)vt[41])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetActionOriginFromXboxOrigin(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[42])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_TranslateActionOrigin(void* self, int _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int);
    return ((Fn)vt[43])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInput_GetDeviceBindingRevision(void* self, uint64_t _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[44])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamInput_GetRemotePlaySessionID(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[45])(self, _a0);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamInput_GetSessionInputConfigurationSettings(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[46])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInput_SetDualSenseTriggerEffect(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, void*);
    ((Fn)vt[47])(self, _a0, _a1);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetResultStatus(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[0])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetResultItems(void* self, int _a0, void* _a1, void* pcb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*);
    return ((Fn)vt[1])(self, _a0, _a1, pcb);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetResultItemProperty(void* self, int _a0, uint32_t _a1, void* _a2, void* buf, void* cb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, uint32_t, void*, void*, void*);
    return ((Fn)vt[2])(self, _a0, _a1, _a2, buf, cb);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamInventory_GetResultTimestamp(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, int);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_CheckResultSteamID(void* self, int _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, uint64_t);
    return ((Fn)vt[4])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInventory_DestroyResult(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[5])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetAllItems(void* self, void* phRes) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[6])(self, phRes);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetItemsByID(void* self, void* phRes, void* _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t);
    return ((Fn)vt[7])(self, phRes, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_SerializeResult(void* self, int _a0, void* _a1, void* pcb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*);
    return ((Fn)vt[8])(self, _a0, _a1, pcb);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_DeserializeResult(void* self, void* phRes, void* _a1, uint32_t _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t, int);
    return ((Fn)vt[9])(self, phRes, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GenerateItems(void* self, void* phRes, void* _a1, void* _a2, uint32_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*, uint32_t);
    return ((Fn)vt[10])(self, phRes, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GrantPromoItems(void* self, void* phRes) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[11])(self, phRes);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_AddPromoItem(void* self, void* phRes, int32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int32_t);
    return ((Fn)vt[12])(self, phRes, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_AddPromoItems(void* self, void* phRes, void* _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t);
    return ((Fn)vt[13])(self, phRes, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_ConsumeItem(void* self, void* phRes, uint64_t _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint64_t, uint32_t);
    return ((Fn)vt[14])(self, phRes, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_ExchangeItems(void* self, void* phRes, void* _a1, void* _a2, uint32_t _a3, void* _a4, void* _a5, uint32_t _a6) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*, uint32_t, void*, void*, uint32_t);
    return ((Fn)vt[15])(self, phRes, _a1, _a2, _a3, _a4, _a5, _a6);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_TransferItemQuantity(void* self, void* phRes, uint64_t _a1, uint32_t _a2, uint64_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint64_t, uint32_t, uint64_t);
    return ((Fn)vt[16])(self, phRes, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamInventory_SendItemDropHeartbeat(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[17])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_TriggerItemDrop(void* self, void* phRes, int32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int32_t);
    return ((Fn)vt[18])(self, phRes, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_TradeItems(void* self, void* phRes, uint64_t _a1, void* _a2, void* _a3, uint32_t _a4, void* _a5, void* _a6, uint32_t _a7) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint64_t, void*, void*, uint32_t, void*, void*, uint32_t);
    return ((Fn)vt[19])(self, phRes, _a1, _a2, _a3, _a4, _a5, _a6, _a7);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_LoadItemDefinitions(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[20])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetItemDefinitionIDs(void* self, void* defs, void* pcb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[21])(self, defs, pcb);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetItemDefinitionProperty(void* self, int32_t iDef, void* propName, void* buf, void* cb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int32_t, void*, void*, void*);
    return ((Fn)vt[22])(self, iDef, propName, buf, cb);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInventory_RequestEligiblePromoItemDefinitionsIDs(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[23])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetEligiblePromoItemDefinitionIDs(void* self, uint64_t _a0, void* _a1, void* pcb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[24])(self, _a0, _a1, pcb);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInventory_StartPurchase(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[25])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInventory_RequestPrices(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[26])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamInventory_GetNumItemsWithPrices(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[27])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetItemsWithPrices(void* self, void* _a0, void* _a1, void* _a2, uint32_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*, uint32_t);
    return ((Fn)vt[28])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_GetItemPrice(void* self, int32_t _a0, void* p, void* bp) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int32_t, void*, void*);
    return ((Fn)vt[29])(self, _a0, p, bp);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamInventory_StartUpdateProperties(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[30])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_RemoveProperty(void* self, uint64_t _a0, uint64_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, void*);
    return ((Fn)vt[31])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_SetProperty_String(void* self, uint64_t _a0, uint64_t _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, void*, void*);
    return ((Fn)vt[32])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_SetProperty_Bool(void* self, uint64_t _a0, uint64_t _a1, void* _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, void*, int);
    return ((Fn)vt[33])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_SetProperty_Int64(void* self, uint64_t _a0, uint64_t _a1, void* _a2, int64_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, void*, int64_t);
    return ((Fn)vt[34])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_SetProperty_Float(void* self, uint64_t _a0, uint64_t _a1, void* _a2, float _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t, void*, float);
    return ((Fn)vt[35])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_SubmitUpdateProperties(void* self, uint64_t _a0, void* phRes) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[36])(self, _a0, phRes);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamInventory_InspectItem(void* self, void* phRes, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[37])(self, phRes, _a1);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetFavoriteGameCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetFavoriteGame(void* self, int _a0, void* _a1, void* _a2, void* _a3, void* _a4, void* _a5, void* _a6) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*, void*, void*, void*, void*);
    return ((Fn)vt[1])(self, _a0, _a1, _a2, _a3, _a4, _a5, _a6);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_AddFavoriteGame(void* self, uint32_t _a0, uint32_t _a1, uint16_t _a2, uint16_t _a3, uint32_t _a4, uint32_t _a5) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint32_t, uint16_t, uint16_t, uint32_t, uint32_t);
    return ((Fn)vt[2])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_RemoveFavoriteGame(void* self, uint32_t _a0, uint32_t _a1, uint16_t _a2, uint16_t _a3, uint32_t _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint32_t, uint16_t, uint16_t, uint32_t);
    return ((Fn)vt[3])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_RequestLobbyList(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[4])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListStringFilter(void* self, void* k, void* v, int cmp) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*, int);
    ((Fn)vt[5])(self, k, v, cmp);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListNumericalFilter(void* self, void* k, int v, int cmp) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, int, int);
    ((Fn)vt[6])(self, k, v, cmp);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListNearValueFilter(void* self, void* k, int v) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, int);
    ((Fn)vt[7])(self, k, v);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListFilterSlotsAvailable(void* self, int slots) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[8])(self, slots);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListDistanceFilter(void* self, int eDist) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[9])(self, eDist);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListResultCountFilter(void* self, int n) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[10])(self, n);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_AddRequestLobbyListCompatibleMembersFilter(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[11])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_GetLobbyByIndex(void* self, int idx) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int);
    return ((Fn)vt[12])(self, idx);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_CreateLobby(void* self, int eLobbyType, int maxMembers) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, int);
    return ((Fn)vt[13])(self, eLobbyType, maxMembers);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_JoinLobby(void* self, uint64_t lobbySid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[14])(self, lobbySid);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_LeaveLobby(void* self, uint64_t sid) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[15])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_InviteUserToLobby(void* self, uint64_t sid, uint64_t invitee) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[16])(self, sid, invitee);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetNumLobbyMembers(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[17])(self, sid);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_GetLobbyMemberByIndex(void* self, uint64_t sid, int idx) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[18])(self, sid, idx);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmaking_GetLobbyData(void* self, uint64_t sid, void* key) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[19])(self, sid, key);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyData(void* self, uint64_t sid, void* key, void* val) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[20])(self, sid, key, val);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyDataCount(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[21])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyDataByIndex(void* self, uint64_t sid, int idx, void* key, int kn, void* val, int vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int, void*, int, void*, int);
    return ((Fn)vt[22])(self, sid, idx, key, kn, val, vn);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_DeleteLobbyData(void* self, uint64_t sid, void* key) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[23])(self, sid, key);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmaking_GetLobbyMemberData(void* self, uint64_t sid, uint64_t member, void* key) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t, uint64_t, void*);
    return ((Fn)vt[24])(self, sid, member, key);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_SetLobbyMemberData(void* self, uint64_t sid, void* key, void* val) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, void*, void*);
    ((Fn)vt[25])(self, sid, key, val);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SendLobbyChatMsg(void* self, uint64_t sid, void* body, int n) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, int);
    return ((Fn)vt[26])(self, sid, body, n);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyChatEntry(void* self, uint64_t sid, int idx, void* speaker_out, void* body_out, int body_cap, void* chat_type_out) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int, void*, void*, int, void*);
    return ((Fn)vt[27])(self, sid, idx, speaker_out, body_out, body_cap, chat_type_out);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_RequestLobbyData(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[28])(self, sid);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmaking_SetLobbyGameServer(void* self, uint64_t sid, uint32_t ip, uint16_t port, uint64_t gs) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint32_t, uint16_t, uint64_t);
    ((Fn)vt[29])(self, sid, ip, port, gs);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyGameServer(void* self, uint64_t sid, void* ip, void* port, void* sid_out) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*, void*);
    return ((Fn)vt[30])(self, sid, ip, port, sid_out);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyMemberLimit(void* self, uint64_t sid, int max_members) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[31])(self, sid, max_members);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_GetLobbyMemberLimit(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[32])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyType(void* self, uint64_t sid, int eLobbyType) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[33])(self, sid, eLobbyType);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyJoinable(void* self, uint64_t sid, int joinable) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[34])(self, sid, joinable);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamMatchmaking_GetLobbyOwner(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[35])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLobbyOwner(void* self, uint64_t sid, uint64_t new_owner) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[36])(self, sid, new_owner);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmaking_SetLinkedLobby(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[37])(self, _a0, _a1);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestInternetServerList(void* self, uint32_t app, void* _a1, uint32_t n, void* _a3) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[0])(self, app, _a1, n, _a3);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestLANServerList(void* self, uint32_t app, void* _a1) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[1])(self, app, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestFriendsServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[2])(self, app, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestFavoritesServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[3])(self, app, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestHistoryServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[4])(self, app, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_RequestSpectatorServerList(void* self, uint32_t app, void* _a1, uint32_t _a2, void* _a3) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[5])(self, app, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_ReleaseRequest(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[6])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamMatchmakingServers_GetServerDetails(void* self, void* _a0, int _a1) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, int);
    return ((Fn)vt[7])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_CancelQuery(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[8])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_RefreshQuery(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[9])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_IsRefreshing(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[10])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_GetServerCount(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[11])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_RefreshServer(void* self, void* _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, int);
    ((Fn)vt[12])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_PingServer(void* self, uint32_t _a0, uint16_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint16_t, void*);
    return ((Fn)vt[13])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_PlayerDetails(void* self, uint32_t _a0, uint16_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint16_t, void*);
    return ((Fn)vt[14])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMatchmakingServers_ServerRules(void* self, uint32_t _a0, uint16_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint16_t, void*);
    return ((Fn)vt[15])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMatchmakingServers_CancelServerQuery(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[16])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusic_BIsEnabled(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusic_BIsPlaying(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusic_GetPlaybackStatus(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMusic_Play(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[3])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMusic_Pause(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[4])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMusic_PlayPrevious(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[5])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMusic_PlayNext(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[6])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamMusic_SetVolume(void* self, float _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, float);
    ((Fn)vt[7])(self, _a0);
}
WN_STEAMAPI_EXPORT float SteamAPI_ISteamMusic_GetVolume(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef float (*Fn)(void*);
    return ((Fn)vt[8])(self);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_RegisterSteamMusicRemote(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[0])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_DeregisterSteamMusicRemote(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_BIsCurrentMusicRemote(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_BActivationSuccess(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_SetDisplayName(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[4])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_SetPNGIcon_64x64(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[5])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_EnablePlayPrevious(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[6])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_EnablePlayNext(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[7])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_EnableShuffled(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[8])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_EnableLooped(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[9])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_EnableQueue(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[10])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_EnablePlaylists(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[11])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdatePlaybackStatus(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[12])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdateShuffled(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[13])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdateLooped(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[14])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdateVolume(void* self, float _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, float);
    return ((Fn)vt[15])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_CurrentEntryWillChange(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[16])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_CurrentEntryIsAvailable(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[17])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdateCurrentEntryText(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[18])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdateCurrentEntryElapsedSeconds(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[19])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_UpdateCurrentEntryCoverArt(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[20])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_CurrentEntryDidChange(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[21])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_QueueWillChange(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[22])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_ResetQueueEntries(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[23])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_SetQueueEntry(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int, void*);
    return ((Fn)vt[24])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_SetCurrentQueueEntry(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[25])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_QueueDidChange(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[26])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_PlaylistWillChange(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[27])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_ResetPlaylistEntries(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[28])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_SetPlaylistEntry(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int, void*);
    return ((Fn)vt[29])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_SetCurrentPlaylistEntry(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[30])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamMusicRemote_PlaylistDidChange(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[31])(self);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_SendP2PPacket(void* self, uint64_t sid, void* _a1, uint32_t n, void* _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, uint32_t, void*, void*);
    return ((Fn)vt[0])(self, sid, _a1, n, _a3, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_IsP2PPacketAvailable(void* self, void* pcub, int nChannel) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int);
    return ((Fn)vt[1])(self, pcub, nChannel);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_ReadP2PPacket(void* self, void* dest, uint32_t cubDest, void* pcub, void* sidOut, int nChannel) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t, void*, void*, int);
    return ((Fn)vt[2])(self, dest, cubDest, pcub, sidOut, nChannel);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_AcceptP2PSessionWithUser(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[3])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_CloseP2PSessionWithUser(void* self, uint64_t sid) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[4])(self, sid);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_CloseP2PChannelWithUser(void* self, uint64_t sid, int nChannel) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[5])(self, sid, nChannel);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_GetP2PSessionState(void* self, uint64_t sid, void* pState) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[6])(self, sid, pState);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_AllowP2PPacketRelay(void* self, int bAllow) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[7])(self, bAllow);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_CreateListenSocket(void* self, int _a0, uint32_t _a1, uint16_t _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, uint32_t, uint16_t, int);
    return ((Fn)vt[8])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_CreateP2PConnectionSocket(void* self, uint64_t _a0, int _a1, int _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int, int, int);
    return ((Fn)vt[9])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_CreateConnectionSocket(void* self, uint32_t _a0, uint16_t _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint16_t, int);
    return ((Fn)vt[10])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_DestroySocket(void* self, int _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int);
    return ((Fn)vt[11])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_DestroyListenSocket(void* self, int _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int);
    return ((Fn)vt[12])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_SendDataOnSocket(void* self, int _a0, void* _a1, uint32_t _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, uint32_t, int);
    return ((Fn)vt[13])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_IsDataAvailableOnSocket(void* self, int _a0, void* pcb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*);
    return ((Fn)vt[14])(self, _a0, pcb);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_RetrieveDataFromSocket(void* self, int _a0, void* _a1, uint32_t _a2, void* pcb) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, uint32_t, void*);
    return ((Fn)vt[15])(self, _a0, _a1, _a2, pcb);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_IsDataAvailable(void* self, int _a0, void* pcb, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*);
    return ((Fn)vt[16])(self, _a0, pcb, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_RetrieveData(void* self, int _a0, void* _a1, uint32_t _a2, void* pcb, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, uint32_t, void*, void*);
    return ((Fn)vt[17])(self, _a0, _a1, _a2, pcb, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_GetSocketInfo(void* self, int _a0, void* sid, void* status, void* ip, void* port, void* lsock) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*, void*, void*, void*);
    return ((Fn)vt[18])(self, _a0, sid, status, ip, port, lsock);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_GetListenSocketInfo(void* self, int _a0, void* ip, void* port) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*);
    return ((Fn)vt[19])(self, _a0, ip, port);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_GetSocketConnectionType(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[20])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworking_GetMaxPacketSize(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[21])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingMessages_SendMessageToUser(void* self, void* _a0, void* _a1, uint32_t _a2, int _a3, int _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t, int, int);
    return ((Fn)vt[0])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingMessages_ReceiveMessagesOnChannel(void* self, int _a0, void* _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, int);
    return ((Fn)vt[1])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingMessages_AcceptSessionWithUser(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingMessages_CloseSessionWithUser(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingMessages_CloseChannelWithUser(void* self, void* _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int);
    return ((Fn)vt[4])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingMessages_GetSessionConnectionInfo(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[5])(self, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_CreateListenSocketIP(void* self, void* _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[0])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_ConnectByIPAddress(void* self, void* _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[1])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_CreateListenSocketP2P(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, int, int, void*);
    return ((Fn)vt[2])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_ConnectP2P(void* self, void* _a0, int _a1, int _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, int, int, void*);
    return ((Fn)vt[3])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_AcceptConnection(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[4])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_CloseConnection(void* self, uint32_t _a0, int _a1, void* _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int, void*, int);
    return ((Fn)vt[5])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_CloseListenSocket(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[6])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_SetConnectionUserData(void* self, uint32_t _a0, int64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int64_t);
    return ((Fn)vt[7])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int64_t SteamAPI_ISteamNetworkingSockets_GetConnectionUserData(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[8])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingSockets_SetConnectionName(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, void*);
    ((Fn)vt[9])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetConnectionName(void* self, uint32_t _a0, void* buf, int cap) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int);
    return ((Fn)vt[10])(self, _a0, buf, cap);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_SendMessageToConnection(void* self, uint32_t _a0, void* _a1, uint32_t _a2, int _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, uint32_t, int, void*);
    return ((Fn)vt[11])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingSockets_SendMessages(void* self, int _a0, void* _a1, void* _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int, void*, void*);
    ((Fn)vt[12])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_FlushMessagesOnConnection(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[13])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnConnection(void* self, uint32_t _a0, void* _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int);
    return ((Fn)vt[14])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_CreatePollGroup(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[15])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_DestroyPollGroup(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_SetConnectionPollGroup(void* self, uint32_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint32_t);
    return ((Fn)vt[17])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnPollGroup(void* self, uint32_t _a0, void* _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int);
    return ((Fn)vt[18])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetConnectionInfo(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[19])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetConnectionRealTimeStatus(void* self, uint32_t _a0, void* _a1, int _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int, void*);
    return ((Fn)vt[20])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetDetailedConnectionStatus(void* self, uint32_t _a0, void* buf, int cap) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, int);
    return ((Fn)vt[21])(self, _a0, buf, cap);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetListenSocketAddress(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[22])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_CreateSocketPair(void* self, void* a, void* b, int _a2, void* _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, int, void*, void*);
    return ((Fn)vt[23])(self, a, b, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_ConfigureConnectionLanes(void* self, uint32_t _a0, int _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int, void*, void*);
    return ((Fn)vt[24])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetIdentity(void* self, void* pIdentity) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[25])(self, pIdentity);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_InitAuthentication(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[26])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetAuthenticationStatus(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[27])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_ReceivedRelayAuthTicket(void* self, void* _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[28])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_FindRelayAuthTicketForServer(void* self, void* _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[29])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_ConnectToHostedDedicatedServer(void* self, void* _a0, int _a1, int _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, int, int, void*);
    return ((Fn)vt[30])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint16_t SteamAPI_ISteamNetworkingSockets_GetHostedDedicatedServerPort(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint16_t (*Fn)(void*);
    return ((Fn)vt[31])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_GetHostedDedicatedServerPOPID(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[32])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetHostedDedicatedServerAddress(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[33])(self, _a0);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_CreateHostedDedicatedServerListenSocket(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, int, int, void*);
    return ((Fn)vt[34])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetGameCoordinatorServerLogin(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[35])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_ConnectP2PCustomSignaling(void* self, void* _a0, void* _a1, int _a2, int _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, void*, int, int, void*);
    return ((Fn)vt[36])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_ReceivedP2PCustomSignal(void* self, void* _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[37])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetCertificateRequest(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[38])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_SetCertificate(void* self, void* _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[39])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingSockets_ResetIdentity(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[40])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingSockets_RunCallbacks(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[41])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_BeginAsyncRequestFakeIP(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[42])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingSockets_GetFakeIP(void* self, int _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int, void*);
    ((Fn)vt[43])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamNetworkingSockets_CreateListenSocketP2PFakeIP(void* self, int _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, int, int, void*);
    return ((Fn)vt[44])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingSockets_GetRemoteFakeIPForConnection(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[45])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamNetworkingSockets_CreateFakeUDPPort(void* self, int _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int);
    return ((Fn)vt[46])(self, _a0);
}

WN_STEAMAPI_EXPORT void* SteamAPI_ISteamNetworkingUtils_AllocateMessage(void* self, int _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int);
    return ((Fn)vt[0])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingUtils_InitRelayNetworkAccess(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetRelayNetworkStatus(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT float SteamAPI_ISteamNetworkingUtils_GetLocalPingLocation(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef float (*Fn)(void*, void*);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_EstimatePingTimeBetweenTwoLocations(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[4])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_EstimatePingTimeFromLocalHost(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[5])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingUtils_ConvertPingLocationToString(void* self, void* _a0, void* buf, int cap) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*, int);
    ((Fn)vt[6])(self, _a0, buf, cap);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_ParsePingLocationString(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[7])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_CheckPingDataUpToDate(void* self, float _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, float);
    return ((Fn)vt[8])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetPingToDataCenter(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[9])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetDirectPingToPOP(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[10])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetPOPCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[11])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetPOPList(void* self, void* _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int);
    return ((Fn)vt[12])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int64_t SteamAPI_ISteamNetworkingUtils_GetLocalTimestamp(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int64_t (*Fn)(void*);
    return ((Fn)vt[13])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingUtils_SetDebugOutputFunction(void* self, int _a0, void* _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int, void*);
    ((Fn)vt[14])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_IsFakeIPv4(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[15])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetIPv4FakeIPType(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetRealIdentityForFakeIP(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[17])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetGlobalConfigValueInt32(void* self, int _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int);
    return ((Fn)vt[18])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetGlobalConfigValueFloat(void* self, int _a0, float _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, float);
    return ((Fn)vt[19])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetGlobalConfigValueString(void* self, int _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*);
    return ((Fn)vt[20])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetGlobalConfigValuePtr(void* self, int _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*);
    return ((Fn)vt[21])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetConnectionConfigValueInt32(void* self, uint32_t _a0, int _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int, int);
    return ((Fn)vt[22])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetConnectionConfigValueFloat(void* self, uint32_t _a0, int _a1, float _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int, float);
    return ((Fn)vt[23])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetConnectionConfigValueString(void* self, uint32_t _a0, int _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, int, void*);
    return ((Fn)vt[24])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetConfigValue(void* self, int _a0, int _a1, uint64_t _a2, int _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int, uint64_t, int, void*);
    return ((Fn)vt[25])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SetConfigValueStruct(void* self, void* _a0, int _a1, uint64_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, uint64_t);
    return ((Fn)vt[26])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_GetConfigValue(void* self, int _a0, int _a1, uint64_t _a2, void* _a3, void* _a4, void* _a5) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int, uint64_t, void*, void*, void*);
    return ((Fn)vt[27])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamNetworkingUtils_GetConfigValueInfo(void* self, int _a0, void* _a1, void* _a2, void* _a3) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, void*, void*, void*);
    return ((Fn)vt[28])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_IterateGenericEditableConfigValues(void* self, int _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int);
    return ((Fn)vt[29])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingUtils_SteamNetworkingIPAddr_ToString(void* self, void* pAddr, void* buf, uint32_t cap, int with_port) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*, uint32_t, int);
    ((Fn)vt[30])(self, pAddr, buf, cap, with_port);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SteamNetworkingIPAddr_ParseString(void* self, void* pAddr, void* s) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[31])(self, pAddr, s);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SteamNetworkingIPAddr_GetFakeIPType(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[32])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamNetworkingUtils_SteamNetworkingIdentity_ToString(void* self, void* pId, void* buf, uint32_t cap) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*, uint32_t);
    ((Fn)vt[33])(self, pId, buf, cap);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamNetworkingUtils_SteamNetworkingIdentity_ParseString(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[34])(self, _a0, _a1);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamParentalSettings_BIsParentalLockEnabled(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParentalSettings_BIsParentalLockLocked(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParentalSettings_BIsAppBlocked(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParentalSettings_BIsAppInBlockList(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParentalSettings_BIsFeatureBlocked(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[4])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParentalSettings_BIsFeatureInBlockList(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[5])(self, _a0);
}

WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamParties_GetNumActiveBeacons(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamParties_GetBeaconByIndex(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[1])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParties_GetBeaconDetails(void* self, uint64_t _a0, void* _a1, void* _a2, void* meta, int mn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*, void*, int);
    return ((Fn)vt[2])(self, _a0, _a1, _a2, meta, mn);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamParties_JoinParty(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParties_GetNumAvailableBeaconLocations(void* self, void* pNum) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[4])(self, pNum);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParties_GetAvailableBeaconLocations(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[5])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamParties_CreateBeacon(void* self, uint32_t _a0, void* _a1, int _a2, void* _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t, void*, int, void*, void*);
    return ((Fn)vt[6])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamParties_OnReservationCompleted(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t);
    ((Fn)vt[7])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamParties_CancelReservation(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint64_t);
    ((Fn)vt[8])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamParties_ChangeNumOpenSlots(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[9])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParties_DestroyBeacon(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[10])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamParties_GetBeaconLocationData(void* self, void* _a0, int _a1, void* str, int sn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*, int);
    return ((Fn)vt[11])(self, _a0, _a1, str, sn);
}

WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamRemotePlay_GetSessionCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamRemotePlay_GetSessionID(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, int);
    return ((Fn)vt[1])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemotePlay_GetSessionSteamID(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamRemotePlay_GetSessionClientName(void* self, uint32_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t);
    return ((Fn)vt[3])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemotePlay_GetSessionClientFormFactor(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[4])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemotePlay_BGetSessionClientResolution(void* self, uint32_t _a0, void* w, void* h) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, void*);
    return ((Fn)vt[5])(self, _a0, w, h);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemotePlay_BStartRemotePlayTogether(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[6])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemotePlay_BSendRemotePlayTogetherInvite(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[7])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileWrite(void* self, void* pchFile, void* pvData, int cubData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, int);
    return ((Fn)vt[0])(self, pchFile, pvData, cubData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileRead(void* self, void* pchFile, void* pvData, int cubDataToRead) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, int);
    return ((Fn)vt[1])(self, pchFile, pvData, cubDataToRead);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_FileWriteAsync(void* self, void* pchFile, void* pvData, uint32_t cubData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*, uint32_t);
    return ((Fn)vt[2])(self, pchFile, pvData, cubData);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_FileReadAsync(void* self, void* pchFile, uint32_t nOffset, uint32_t cubToRead) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, uint32_t, uint32_t);
    return ((Fn)vt[3])(self, pchFile, nOffset, cubToRead);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileReadAsyncComplete(void* self, uint64_t hCall, void* pvBuffer, uint32_t cubToRead) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, uint32_t);
    return ((Fn)vt[4])(self, hCall, pvBuffer, cubToRead);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileForget(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[5])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileDelete(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[6])(self, pchFile);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_FileShare(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[7])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_SetSyncPlatforms(void* self, void* pchFile, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[8])(self, pchFile, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_FileWriteStreamOpen(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[9])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileWriteStreamWriteChunk(void* self, uint64_t h, void* pvData, int cubData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, int);
    return ((Fn)vt[10])(self, h, pvData, cubData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileWriteStreamClose(void* self, uint64_t h) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[11])(self, h);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileWriteStreamCancel(void* self, uint64_t h) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[12])(self, h);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FileExists(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[13])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_FilePersisted(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[14])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_GetFileSize(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[15])(self, pchFile);
}
WN_STEAMAPI_EXPORT int64_t SteamAPI_ISteamRemoteStorage_GetFileTimestamp(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int64_t (*Fn)(void*, void*);
    return ((Fn)vt[16])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_GetSyncPlatforms(void* self, void* pchFile) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[17])(self, pchFile);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_GetFileCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[18])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamRemoteStorage_GetFileNameAndSize(void* self, int iFile, void* pnFileSizeInBytes) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, int, void*);
    return ((Fn)vt[19])(self, iFile, pnFileSizeInBytes);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamRemoteStorage_GetQuota(void* self, void* total, void* avail) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*, void*);
    ((Fn)vt[20])(self, total, avail);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_IsCloudEnabledForAccount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[21])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_IsCloudEnabledForApp(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[22])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamRemoteStorage_SetCloudEnabledForApp(void* self, int enabled) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[23])(self, enabled);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_UGCDownload(void* self, uint64_t hContent, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[24])(self, hContent, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_GetUGCDownloadProgress(void* self, uint64_t _a0, void* d, void* e) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[25])(self, _a0, d, e);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_GetUGCDetails(void* self, void* _a0, void* appID, void* ppchName, void* pcbFile, void* steamIDOwner) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*, void*, void*);
    return ((Fn)vt[26])(self, _a0, appID, ppchName, pcbFile, steamIDOwner);
}
WN_STEAMAPI_EXPORT int32_t SteamAPI_ISteamRemoteStorage_UGCRead(void* self, void* _a0, void* _a1, void* _a2, void* _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int32_t (*Fn)(void*, void*, void*, void*, void*, void*);
    return ((Fn)vt[27])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT int32_t SteamAPI_ISteamRemoteStorage_GetCachedUGCCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int32_t (*Fn)(void*);
    return ((Fn)vt[28])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_GetCachedUGCHandle(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[29])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_PublishWorkshopFile_DEPRECATED(void* self, void* _a0, void* _a1, uint32_t _a2, void* _a3, void* _a4, int _a5, void* _a6, void* _a7, int _a8) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*, uint32_t, void*, void*, int, void*, void*, int);
    return ((Fn)vt[30])(self, _a0, _a1, _a2, _a3, _a4, _a5, _a6, _a7, _a8);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_CreatePublishedFileUpdateRequest_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[31])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFileFile_DEPRECATED(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[32])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFilePreviewFile_DEPRECATED(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[33])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFileTitle_DEPRECATED(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[34])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFileDescription_DEPRECATED(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[35])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFileVisibility_DEPRECATED(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[36])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFileTags_DEPRECATED(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[37])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_CommitPublishedFileUpdate_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[38])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_GetPublishedFileDetails_DEPRECATED(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[39])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_DeletePublishedFile_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[40])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_EnumerateUserPublishedFiles_DEPRECATED(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[41])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_SubscribePublishedFile_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[42])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_EnumerateUserSubscribedFiles_DEPRECATED(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[43])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_UnsubscribePublishedFile_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[44])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_UpdatePublishedFileSetChangeDescription_DEPRECATED(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[45])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_GetPublishedItemVoteDetails_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[46])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_UpdateUserPublishedItemVote_DEPRECATED(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[47])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_GetUserPublishedItemVoteDetails_DEPRECATED(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[48])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_EnumerateUserSharedWorkshopFiles_DEPRECATED(void* self, uint64_t _a0, uint32_t _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint32_t, void*, void*);
    return ((Fn)vt[49])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_PublishVideo_DEPRECATED(void* self, int _a0, void* _a1, uint32_t _a2, void* _a3, void* _a4, uint32_t _a5, void* _a6) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, void*, uint32_t, void*, void*, uint32_t, void*);
    return ((Fn)vt[50])(self, _a0, _a1, _a2, _a3, _a4, _a5, _a6);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_SetUserPublishedFileAction_DEPRECATED(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[51])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_EnumeratePublishedFilesByUserAction_DEPRECATED(void* self, int _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, uint32_t);
    return ((Fn)vt[52])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_EnumeratePublishedWorkshopFiles_DEPRECATED(void* self, int _a0, uint32_t _a1, uint32_t _a2, uint32_t _a3, void* _a4, void* _a5) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, uint32_t, uint32_t, uint32_t, void*, void*);
    return ((Fn)vt[53])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamRemoteStorage_UGCDownloadToLocation(void* self, uint64_t hContent, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[54])(self, hContent, _a1, _a2);
}
WN_STEAMAPI_EXPORT int32_t SteamAPI_ISteamRemoteStorage_GetLocalFileChangeCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int32_t (*Fn)(void*);
    return ((Fn)vt[55])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamRemoteStorage_GetLocalFileChange(void* self, void* _a0, void* peChangeType, void* pePathType) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[56])(self, _a0, peChangeType, pePathType);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_BeginFileWriteBatch(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[57])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamRemoteStorage_EndFileWriteBatch(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[58])(self);
}

WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamScreenshots_WriteScreenshot(void* self, void* _a0, uint32_t _a1, int _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, uint32_t, int, int);
    return ((Fn)vt[0])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamScreenshots_AddScreenshotToLibrary(void* self, void* _a0, void* _a1, int _a2, int _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, void*, int, int);
    return ((Fn)vt[1])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamScreenshots_TriggerScreenshot(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamScreenshots_HookScreenshots(void* self, int hooked) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[3])(self, hooked);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamScreenshots_SetLocation(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[4])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamScreenshots_TagUser(void* self, uint32_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint64_t);
    return ((Fn)vt[5])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamScreenshots_TagPublishedFile(void* self, uint32_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, uint64_t);
    return ((Fn)vt[6])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamScreenshots_IsScreenshotsHooked(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[7])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamScreenshots_AddVRScreenshotToLibrary(void* self, int _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, int, void*, void*);
    return ((Fn)vt[8])(self, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_CreateQueryUserUGCRequest(void* self, uint32_t _a0, int _a1, int _a2, int _a3, uint32_t _a4, uint32_t _a5, uint32_t _a6) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t, int, int, int, uint32_t, uint32_t, uint32_t);
    return ((Fn)vt[0])(self, _a0, _a1, _a2, _a3, _a4, _a5, _a6);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_CreateQueryAllUGCRequest_Page(void* self, int _a0, int _a1, uint32_t _a2, uint32_t _a3, uint32_t _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, int, uint32_t, uint32_t, uint32_t);
    return ((Fn)vt[1])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_CreateQueryAllUGCRequest_Cursor(void* self, int _a0, int _a1, uint32_t _a2, uint32_t _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, int, int, uint32_t, uint32_t, void*);
    return ((Fn)vt[2])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_CreateQueryUGCDetailsRequest(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[3])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_SendQueryUGCRequest(void* self, uint64_t handle) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[4])(self, handle);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCResult(void* self, uint64_t _a0, uint32_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*);
    return ((Fn)vt[5])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetQueryUGCNumTags(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[6])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCTag(void* self, uint64_t _a0, uint32_t _a1, uint32_t _a2, void* v, uint32_t vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, uint32_t, void*, uint32_t);
    return ((Fn)vt[7])(self, _a0, _a1, _a2, v, vn);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCTagDisplayName(void* self, uint64_t _a0, uint32_t _a1, uint32_t _a2, void* v, uint32_t vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, uint32_t, void*, uint32_t);
    return ((Fn)vt[8])(self, _a0, _a1, _a2, v, vn);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCPreviewURL(void* self, uint64_t _a0, uint32_t _a1, void* v, uint32_t vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*, uint32_t);
    return ((Fn)vt[9])(self, _a0, _a1, v, vn);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCMetadata(void* self, uint64_t _a0, uint32_t _a1, void* v, uint32_t vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*, uint32_t);
    return ((Fn)vt[10])(self, _a0, _a1, v, vn);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCChildren(void* self, uint64_t _a0, uint32_t _a1, void* _a2, uint32_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*, uint32_t);
    return ((Fn)vt[11])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCStatistic(void* self, uint64_t _a0, uint32_t _a1, int _a2, void* out) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, int, void*);
    return ((Fn)vt[12])(self, _a0, _a1, _a2, out);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetQueryUGCNumAdditionalPreviews(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[13])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCAdditionalPreview(void* self, uint64_t _a0, uint32_t _a1, uint32_t _a2, void* url, uint32_t uns, void* orig, uint32_t os, void* _a7) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, uint32_t, void*, uint32_t, void*, uint32_t, void*);
    return ((Fn)vt[14])(self, _a0, _a1, _a2, url, uns, orig, os, _a7);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetQueryUGCNumKeyValueTags(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[15])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCKeyValueTagByIndex(void* self, uint64_t _a0, uint32_t _a1, uint32_t _a2, void* k, uint32_t kn, void* v, uint32_t vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, uint32_t, void*, uint32_t, void*, uint32_t);
    return ((Fn)vt[16])(self, _a0, _a1, _a2, k, kn, v, vn);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetQueryUGCKeyValueTagByName(void* self, uint64_t _a0, uint32_t _a1, void* _a2, void* v, uint32_t vn) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*, void*, uint32_t);
    return ((Fn)vt[17])(self, _a0, _a1, _a2, v, vn);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetQueryUGCContentDescriptors(void* self, uint64_t _a0, uint32_t _a1, void* _a2, uint32_t _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t, uint32_t, void*, uint32_t);
    return ((Fn)vt[18])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_ReleaseQueryUGCRequest(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[19])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddRequiredTag(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[20])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddRequiredTagGroup(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[21])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddExcludedTag(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[22])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnOnlyIDs(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[23])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnKeyValueTags(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[24])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnLongDescription(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[25])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnMetadata(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[26])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnChildren(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[27])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnAdditionalPreviews(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[28])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnTotalOnly(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[29])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetReturnPlaytimeStats(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[30])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetLanguage(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[31])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetAllowCachedResponse(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[32])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetCloudFileNameFilter(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[33])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetMatchAnyTag(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[34])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetSearchText(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[35])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetRankedByTrendDays(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[36])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetTimeCreatedDateRange(void* self, uint64_t _a0, uint32_t _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, uint32_t);
    return ((Fn)vt[37])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetTimeUpdatedDateRange(void* self, uint64_t _a0, uint32_t _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, uint32_t);
    return ((Fn)vt[38])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddRequiredKeyValueTag(void* self, uint64_t _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[39])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_RequestUGCDetails(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*);
    return ((Fn)vt[40])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_CreateItem(void* self, uint32_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t, int);
    return ((Fn)vt[41])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_StartItemUpdate(void* self, uint32_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t, uint64_t);
    return ((Fn)vt[42])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemTitle(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[43])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemDescription(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[44])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemUpdateLanguage(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[45])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemMetadata(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[46])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemVisibility(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[47])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemTags(void* self, uint64_t _a0, void* _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, int);
    return ((Fn)vt[48])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemContent(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[49])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetItemPreview(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[50])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_SetAllowLegacyUpload(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[51])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_RemoveAllItemKeyValueTags(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[52])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_RemoveItemKeyValueTags(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[53])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddItemKeyValueTag(void* self, uint64_t _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[54])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddItemPreviewFile(void* self, uint64_t _a0, void* _a1, int _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, int);
    return ((Fn)vt[55])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddItemPreviewVideo(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[56])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_UpdateItemPreviewFile(void* self, uint64_t _a0, uint32_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*);
    return ((Fn)vt[57])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_UpdateItemPreviewVideo(void* self, uint64_t _a0, uint32_t _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t, void*);
    return ((Fn)vt[58])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_RemoveItemPreview(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[59])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_AddContentDescriptor(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[60])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_RemoveContentDescriptor(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[61])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_SubmitItemUpdate(void* self, uint64_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[62])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetItemUpdateProgress(void* self, uint64_t _a0, void* bp, void* bt) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[63])(self, _a0, bp, bt);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_SetUserItemVote(void* self, uint64_t _a0, int _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, int);
    return ((Fn)vt[64])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_GetUserItemVote(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[65])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_AddItemToFavorites(void* self, uint32_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t, uint64_t);
    return ((Fn)vt[66])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_RemoveItemFromFavorites(void* self, uint32_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t, uint64_t);
    return ((Fn)vt[67])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_SubscribeItem(void* self, uint64_t publishedFileId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[68])(self, publishedFileId);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_UnsubscribeItem(void* self, uint64_t publishedFileId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[69])(self, publishedFileId);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetNumSubscribedItems(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[70])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetSubscribedItems(void* self, void* pIds, uint32_t cMax) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[71])(self, pIds, cMax);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetItemState(void* self, uint64_t publishedFileId) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[72])(self, publishedFileId);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetItemInstallInfo(void* self, uint64_t publishedFileId, void* bytes, void* folder, uint32_t fn, void* timestamp) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*, uint32_t, void*);
    return ((Fn)vt[73])(self, publishedFileId, bytes, folder, fn, timestamp);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_GetItemDownloadInfo(void* self, uint64_t publishedFileId, void* bd, void* bt) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[74])(self, publishedFileId, bd, bt);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_DownloadItem(void* self, uint64_t publishedFileId, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[75])(self, publishedFileId, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_BInitWorkshopForGameServer(void* self, uint32_t _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*);
    return ((Fn)vt[76])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUGC_SuspendDownloads(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[77])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_StartPlaytimeTracking(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[78])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_StopPlaytimeTracking(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[79])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_StopPlaytimeTrackingForAllItems(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[80])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_AddDependency(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[81])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_RemoveDependency(void* self, uint64_t _a0, uint64_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint64_t);
    return ((Fn)vt[82])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_AddAppDependency(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[83])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_RemoveAppDependency(void* self, uint64_t _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[84])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_GetAppDependencies(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[85])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_DeleteItem(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[86])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUGC_ShowWorkshopEULA(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[87])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUGC_GetWorkshopEULAStatus(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[88])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUGC_GetUserContentDescriptorPreferences(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[89])(self, _a0, _a1);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetHSteamUser(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BLoggedOn(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_GetSteamID(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_InitiateGameConnection_DEPRECATED(void* self, void* _a0, int _a1, uint64_t _a2, uint32_t _a3, uint16_t _a4, int _a5) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, uint64_t, uint32_t, uint16_t, int);
    return ((Fn)vt[3])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_TerminateGameConnection_DEPRECATED(void* self, uint32_t _a0, uint16_t _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint32_t, uint16_t);
    ((Fn)vt[4])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_TrackAppUsageEvent(void* self, uint64_t _a0, int _a1, void* _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, int, void*);
    ((Fn)vt[5])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetUserDataFolder(void* self, void* pchBuffer, int cubBuffer) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int);
    return ((Fn)vt[6])(self, pchBuffer, cubBuffer);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_StartVoiceRecording(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[7])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_StopVoiceRecording(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[8])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetAvailableVoice(void* self, void* _a0, void* _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t);
    return ((Fn)vt[9])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetVoice(void* self, int _a0, void* _a1, uint32_t _a2, void* _a3, int _a4, void* _a5, uint32_t _a6, void* _a7, uint32_t _a8) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, uint32_t, void*, int, void*, uint32_t, void*, uint32_t);
    return ((Fn)vt[10])(self, _a0, _a1, _a2, _a3, _a4, _a5, _a6, _a7, _a8);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_DecompressVoice(void* self, void* _a0, uint32_t _a1, void* _a2, uint32_t _a3, void* _a4, uint32_t _a5) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t, void*, uint32_t, void*, uint32_t);
    return ((Fn)vt[11])(self, _a0, _a1, _a2, _a3, _a4, _a5);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUser_GetVoiceOptimalSampleRate(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[12])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_GetAuthSessionTicket(void* self, void* buf, int maxLen, void* pcbTicket, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, int, void*, void*);
    return ((Fn)vt[13])(self, buf, maxLen, pcbTicket, _a3);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_GetAuthTicketForWebApi(void* self, void* pchIdentity) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[14])(self, pchIdentity);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BeginAuthSession(void* self, void* _a0, int cbTicket, uint64_t steamID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, uint64_t);
    return ((Fn)vt[15])(self, _a0, cbTicket, steamID);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_EndAuthSession(void* self, uint64_t _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_CancelAuthTicket(void* self, uint64_t hAuthTicket) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t);
    ((Fn)vt[17])(self, hAuthTicket);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_UserHasLicenseForApp(void* self, uint64_t steamID, uint32_t appID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, uint32_t);
    return ((Fn)vt[18])(self, steamID, appID);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BIsBehindNAT(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[19])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUser_AdvertiseGame(void* self, uint64_t _a0, uint32_t _a1, uint16_t _a2) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, uint64_t, uint32_t, uint16_t);
    ((Fn)vt[20])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_RequestEncryptedAppTicket(void* self, void* rgubData, int cbData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, int);
    return ((Fn)vt[21])(self, rgubData, cbData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetEncryptedAppTicket(void* self, void* buf, int cbMax, void* pcbTicket) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int, void*);
    return ((Fn)vt[22])(self, buf, cbMax, pcbTicket);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetGameBadgeLevel(void* self, int nSeries, int bFoil) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int);
    return ((Fn)vt[23])(self, nSeries, bFoil);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_GetPlayerSteamLevel(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[24])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_RequestStoreAuthURL(void* self, void* pchRedirectURL) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[25])(self, pchRedirectURL);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BIsPhoneVerified(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[26])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BIsTwoFactorEnabled(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[27])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BIsPhoneIdentifying(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[28])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BIsPhoneRequiringVerification(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[29])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_GetMarketEligibility(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[30])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUser_GetDurationControl(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[31])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUser_BSetDurationControlOnlineState(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[32])(self, _a0);
}

WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_RequestCurrentStats(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetStatInt(void* self, void* pchName, void* pData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[1])(self, pchName, pData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetStatFloat(void* self, void* pchName, void* pData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[2])(self, pchName, pData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_SetStatInt(void* self, void* pchName, int32_t nData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, int32_t);
    return ((Fn)vt[3])(self, pchName, nData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_SetStatFloat(void* self, void* pchName, float fData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, float);
    return ((Fn)vt[4])(self, pchName, fData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_UpdateAvgRateStat(void* self, void* pchName, float flCountThisSession, double dSessionLength) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, float, double);
    return ((Fn)vt[5])(self, pchName, flCountThisSession, dSessionLength);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetAchievement(void* self, void* pchName, void* pbAchieved) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[6])(self, pchName, pbAchieved);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_SetAchievement(void* self, void* pchName) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[7])(self, pchName);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_ClearAchievement(void* self, void* pchName) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[8])(self, pchName);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetAchievementAndUnlockTime(void* self, void* pchName, void* pbAchieved, void* punlockTime) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[9])(self, pchName, pbAchieved, punlockTime);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_StoreStats(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[10])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetAchievementIcon(void* self, void* pchName) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[11])(self, pchName);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamUserStats_GetAchievementDisplayAttribute(void* self, void* pchName, void* pchKey) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, void*, void*);
    return ((Fn)vt[12])(self, pchName, pchKey);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_IndicateAchievementProgress(void* self, void* pchName, uint32_t nCurProgress, uint32_t nMaxProgress) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t, uint32_t);
    return ((Fn)vt[13])(self, pchName, nCurProgress, nMaxProgress);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUserStats_GetNumAchievements(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[14])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamUserStats_GetAchievementName(void* self, uint32_t idx) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint32_t);
    return ((Fn)vt[15])(self, idx);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_RequestUserStats(void* self, uint64_t steamID) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t);
    return ((Fn)vt[16])(self, steamID);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetUserStatInt(void* self, uint64_t steamID, void* pchName, void* pData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[17])(self, steamID, pchName, pData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetUserStatFloat(void* self, uint64_t steamID, void* pchName, void* pData) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[18])(self, steamID, pchName, pData);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetUserAchievement(void* self, uint64_t steamID, void* pchName, void* pbAchieved) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[19])(self, steamID, pchName, pbAchieved);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetUserAchievementAndUnlockTime(void* self, uint64_t steamID, void* pchName, void* pbAchieved, void* punlockTime) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, void*, void*);
    return ((Fn)vt[20])(self, steamID, pchName, pbAchieved, punlockTime);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_ResetAllStats(void* self, int bAchievementsToo) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[21])(self, bAchievementsToo);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_FindOrCreateLeaderboard(void* self, void* _a0, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*, void*, void*);
    return ((Fn)vt[22])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_FindLeaderboard(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[23])(self, _a0);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamUserStats_GetLeaderboardName(void* self, uint64_t _a0) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*, uint64_t);
    return ((Fn)vt[24])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetLeaderboardEntryCount(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[25])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetLeaderboardSortMethod(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[26])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetLeaderboardDisplayType(void* self, uint64_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t);
    return ((Fn)vt[27])(self, _a0);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_DownloadLeaderboardEntries(void* self, uint64_t hLeaderboard, void* _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*, void*, void*);
    return ((Fn)vt[28])(self, hLeaderboard, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_DownloadLeaderboardEntriesForUsers(void* self, uint64_t hLeaderboard, void* _a1, void* _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*, void*);
    return ((Fn)vt[29])(self, hLeaderboard, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetDownloadedLeaderboardEntry(void* self, uint64_t _a0, int _a1, void* _a2, void* _a3, int _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, int, void*, void*, int);
    return ((Fn)vt[30])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_UploadLeaderboardScore(void* self, uint64_t hLeaderboard, void* _a1, int32_t score, void* _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*, int32_t, void*, void*);
    return ((Fn)vt[31])(self, hLeaderboard, _a1, score, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_AttachLeaderboardUGC(void* self, uint64_t hLeaderboard, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[32])(self, hLeaderboard, _a1);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_GetNumberOfCurrentPlayers(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[33])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_RequestGlobalAchievementPercentages(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*);
    return ((Fn)vt[34])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetMostAchievedAchievementInfo(void* self, void* _a0, uint32_t _a1, void* _a2, void* _a3) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t, void*, void*);
    return ((Fn)vt[35])(self, _a0, _a1, _a2, _a3);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetNextMostAchievedAchievementInfo(void* self, int _a0, void* _a1, uint32_t _a2, void* _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, uint32_t, void*, void*);
    return ((Fn)vt[36])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetAchievementAchievedPercent(void* self, void* _a0, void* p) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[37])(self, _a0, p);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUserStats_RequestGlobalStats(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[38])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetGlobalStatInt64(void* self, void* _a0, void* p) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[39])(self, _a0, p);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetGlobalStatDouble(void* self, void* _a0, void* p) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[40])(self, _a0, p);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetGlobalStatHistoryInt64(void* self, void* _a0, void* _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t);
    return ((Fn)vt[41])(self, _a0, _a1, _a2);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUserStats_GetGlobalStatHistoryDouble(void* self, void* _a0, void* _a1, uint32_t _a2) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, uint32_t);
    return ((Fn)vt[42])(self, _a0, _a1, _a2);
}

WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUtils_GetSecondsSinceAppActive(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[0])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUtils_GetSecondsSinceComputerActive(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[1])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetConnectedUniverse(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[2])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUtils_GetServerRealTime(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[3])(self);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamUtils_GetIPCountry(void* self) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*);
    return ((Fn)vt[4])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetImageSize(void* self, int iImage, void* pnWidth, void* pnHeight) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, void*);
    return ((Fn)vt[5])(self, iImage, pnWidth, pnHeight);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetImageRGBA(void* self, int iImage, void* pubDest, int nDestBufferSize) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, void*, int);
    return ((Fn)vt[6])(self, iImage, pubDest, nDestBufferSize);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetCSERIPPort(void* self, void* _a0, void* _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*);
    return ((Fn)vt[7])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT uint8_t SteamAPI_ISteamUtils_GetCurrentBatteryPower(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint8_t (*Fn)(void*);
    return ((Fn)vt[8])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUtils_GetAppID(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[9])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_SetOverlayNotificationPosition(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[10])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsAPICallCompleted(void* self, uint64_t hCall, void* pbFailed) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*);
    return ((Fn)vt[11])(self, hCall, pbFailed);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetAPICallFailureReason(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[12])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetAPICallResult(void* self, uint64_t hCall, void* pCallback, int cubCallback, int iCallbackExpected, void* pbFailed) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint64_t, void*, int, int, void*);
    return ((Fn)vt[13])(self, hCall, pCallback, cubCallback, iCallbackExpected, pbFailed);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_RunFrame(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[14])(self);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUtils_GetIPCCallCount(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[15])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_SetWarningMessageHook(void* self, void* _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, void*);
    ((Fn)vt[16])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsOverlayEnabled(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[17])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_BOverlayNeedsPresent(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[18])(self);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamUtils_CheckFileSignature(void* self, void* _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, void*);
    return ((Fn)vt[19])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_ShowGamepadTextInput(void* self, int _a0, int _a1, void* _a2, uint32_t _a3, void* _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int, void*, uint32_t, void*);
    return ((Fn)vt[20])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT uint32_t SteamAPI_ISteamUtils_GetEnteredGamepadTextLength(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint32_t (*Fn)(void*);
    return ((Fn)vt[21])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetEnteredGamepadTextInput(void* self, void* _a0, uint32_t _a1) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, uint32_t);
    return ((Fn)vt[22])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT void* SteamAPI_ISteamUtils_GetSteamUILanguage(void* self) {
    if (self == NULL) return NULL;
    void** vt = *(void***)self;
    typedef void* (*Fn)(void*);
    return ((Fn)vt[23])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsSteamRunningInVR(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[24])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_SetOverlayNotificationInset(void* self, int _a0, int _a1) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int, int);
    ((Fn)vt[25])(self, _a0, _a1);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsSteamInBigPictureMode(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[26])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_StartVRDashboard(void* self) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*);
    ((Fn)vt[27])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsVRHeadsetStreamingEnabled(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[28])(self);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_SetVRHeadsetStreamingEnabled(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[29])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsSteamChinaLauncher(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[30])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_InitFilterText(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t);
    return ((Fn)vt[31])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_FilterText(void* self, void* _a0, void* _a1, void* in, void* out, uint32_t outSize) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*, void*, void*, void*, uint32_t);
    return ((Fn)vt[32])(self, _a0, _a1, in, out, outSize);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_GetIPv6ConnectivityState(void* self, int _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int);
    return ((Fn)vt[33])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_IsSteamRunningOnSteamDeck(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[34])(self);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_ShowFloatingGamepadTextInput(void* self, int _a0, int _a1, int _a2, int _a3, int _a4) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, int, int, int, int, int);
    return ((Fn)vt[35])(self, _a0, _a1, _a2, _a3, _a4);
}
WN_STEAMAPI_EXPORT void SteamAPI_ISteamUtils_SetGameLauncherMode(void* self, int _a0) {
    if (self == NULL) return;
    void** vt = *(void***)self;
    typedef void (*Fn)(void*, int);
    ((Fn)vt[36])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamUtils_DismissFloatingGamepadTextInput(void* self) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*);
    return ((Fn)vt[37])(self);
}

WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamVideo_GetVideoURL_DEPRECATED(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[0])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamVideo_IsBroadcasting(void* self, void* pnNumViewers) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, void*);
    return ((Fn)vt[1])(self, pnNumViewers);
}
WN_STEAMAPI_EXPORT uint64_t SteamAPI_ISteamVideo_GetOPFSettings(void* self, uint32_t _a0) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef uint64_t (*Fn)(void*, uint32_t);
    return ((Fn)vt[2])(self, _a0);
}
WN_STEAMAPI_EXPORT int SteamAPI_ISteamVideo_GetOPFStringForApp(void* self, uint32_t _a0, void* buf, void* pnBufSize) {
    if (self == NULL) return 0;
    void** vt = *(void***)self;
    typedef int (*Fn)(void*, uint32_t, void*, void*);
    return ((Fn)vt[3])(self, _a0, buf, pnBufSize);
}
