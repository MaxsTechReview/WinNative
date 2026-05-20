#pragma once

// Minimal pure-C++ shim for the subset of the SteamWorks ABI we need to call
// into libsteamclient.so. The Valve SDK headers (isteamclient.h etc.) are
// publicly distributed but pull in a large transitive include graph and a
// dependency on `steam_api.h` which expects to be linked against
// libsteam_api.so. We don't want that — we want to load libsteamclient.so
// directly and call its CreateInterface() factory.
//
// So we redeclare just the vtables we touch, in the order they appear in
// Valve's published headers, using opaque pointer types where we don't care
// about the contents. ABI MUST match Valve's published headers for the
// interface versions named below — any layout drift will crash on call.
//
// Reference: SteamWorks SDK 1.59 (the latest publicly downloadable as of
// commit time). Interface version strings are matched against Valve's own
// implementation by the CreateInterface factory; pinning to the version
// strings below means "use this exact ABI or fail" — much safer than just
// asking for the newest.
//
// What we use the interfaces for:
//   ISteamClient   — pipe creation + per-interface lookup
//   ISteamUser     — pre-logon SetLoginInformation + LogOn driving so
//                    libsteamclient.so reaches LoggedOn state and Wine's
//                    lsteamclient.dll can issue authed IPC against it
//   ISteamApps     — GetAppOwnershipTicket pre-warm (PrepareApp path)
//   ISteamRemoteStorage — per-app SetCloudEnabledForApp toggle

#include <cstdint>

namespace wnsteambs {

using HSteamPipe = int32_t;
using HSteamUser = int32_t;

enum EAccountType : int {
    k_EAccountTypeInvalid           = 0,
    k_EAccountTypeIndividual        = 1,
    k_EAccountTypeMultiseat         = 2,
    k_EAccountTypeGameServer        = 3,
    k_EAccountTypeAnonGameServer    = 4,
    k_EAccountTypePending           = 5,
    k_EAccountTypeContentServer     = 6,
    k_EAccountTypeClan              = 7,
    k_EAccountTypeChat              = 8,
    k_EAccountTypeConsoleUser       = 9,
    k_EAccountTypeAnonUser          = 10,
};

// Interface version strings — these MUST match what libsteamclient.so
// implements for CreateInterface to return a non-null pointer.
constexpr const char* kSteamClient020 = "SteamClient020";
constexpr const char* kSteamUser023   = "SteamUser023";
constexpr const char* kSteamApps008   = "STEAMAPPS_INTERFACE_VERSION008";
constexpr const char* kSteamRemoteStorage016 = "STEAMREMOTESTORAGE_INTERFACE_VERSION016";

// CreateInterface signature exported by libsteamclient.so. Returns a
// pointer to a vtable-only object (Steam interfaces have no data members
// the caller needs to peek at).
using CreateInterfaceFn = void* (*)(const char* version, int* returnCode);

// ISteamClient — only the methods we actually call. The rest of the vtable
// is irrelevant (we treat the object as opaque between virtual calls).
class ISteamClient {
public:
    virtual HSteamPipe CreateSteamPipe() = 0;
    virtual bool       BReleaseSteamPipe(HSteamPipe pipe) = 0;
    virtual HSteamUser ConnectToGlobalUser(HSteamPipe pipe) = 0;
    virtual HSteamUser CreateLocalUser(HSteamPipe* pipe_out, EAccountType type) = 0;
    virtual void       ReleaseUser(HSteamPipe pipe, HSteamUser user) = 0;
    virtual void*      GetISteamUser(HSteamUser user, HSteamPipe pipe, const char* version) = 0;
    virtual void*      GetISteamGameServer(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void       SetLocalIPBinding(uint32_t ip, uint16_t port) = 0;
    virtual void*      GetISteamFriends(HSteamUser user, HSteamPipe pipe, const char* version) = 0;
    virtual void*      GetISteamUtils(HSteamPipe pipe, const char* version) = 0;
    virtual void*      GetISteamMatchmaking(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void*      GetISteamMatchmakingServers(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void*      GetISteamGenericInterface(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void*      GetISteamUserStats(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void*      GetISteamGameServerStats(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void*      GetISteamApps(HSteamUser user, HSteamPipe pipe, const char* version) = 0;
    virtual void*      GetISteamNetworking(HSteamUser, HSteamPipe, const char*) = 0;
    virtual void*      GetISteamRemoteStorage(HSteamUser user, HSteamPipe pipe, const char* version) = 0;
    // ... rest of vtable is unused by us; we never call past this point so
    // mismatches in later slots are harmless.
};

// ISteamUser — drive logon with a refresh token. The exact method names
// here track the SteamWorks SDK public header for SteamUser023; some method
// signatures depend on private headers and aren't reproduced verbatim.
// For 8b.2's *scaffolding* commit we only need GetSteamID + LoggedOn poll;
// the real logon driving (SetLoginInformation + LogOn) requires private
// vtable entries and lives in the Phase 8b.3 commit where we'll bring in
// the SteamWorks SDK header subset.
class ISteamUser {
public:
    virtual HSteamUser GetHSteamUser() = 0;
    virtual bool       BLoggedOn()     = 0;
    virtual uint64_t   GetSteamID()    = 0;
    // ... 30+ more slots; unused by 8b.2.
};

// ISteamApps — GetAppOwnershipTicket synchronous prewarm.
class ISteamApps {
public:
    virtual bool BIsSubscribed() = 0;
    virtual bool BIsLowViolence() = 0;
    virtual bool BIsCybercafe()  = 0;
    virtual bool BIsVACBanned()  = 0;
    virtual const char* GetCurrentGameLanguage() = 0;
    virtual const char* GetAvailableGameLanguages() = 0;
    virtual bool BIsSubscribedApp(uint32_t app_id) = 0;
    virtual bool BIsDlcInstalled(uint32_t app_id) = 0;
    virtual uint32_t GetEarliestPurchaseUnixTime(uint32_t app_id) = 0;
    virtual bool BIsSubscribedFromFreeWeekend() = 0;
    virtual int  GetDLCCount(uint32_t app_id) = 0;
    virtual bool BGetDLCDataByIndex(uint32_t app_id, int index, uint32_t* app_id_out, bool* avail_out, char* name_out, int name_size) = 0;
    virtual void InstallDLC(uint32_t app_id) = 0;
    virtual void UninstallDLC(uint32_t app_id) = 0;
    virtual void RequestAppProofOfPurchaseKey(uint32_t app_id) = 0;
    virtual bool GetCurrentBetaName(char* name_out, int name_size) = 0;
    virtual bool MarkContentCorrupt(bool missing_files_only) = 0;
    virtual uint32_t GetInstalledDepots(uint32_t app_id, uint32_t* depots_out, uint32_t max_depots) = 0;
    virtual uint32_t GetAppInstallDir(uint32_t app_id, char* folder_out, uint32_t folder_size) = 0;
    virtual bool BIsAppInstalled(uint32_t app_id) = 0;
    // ... GetAppOwnershipTicket is further down; we'll lock the offset in 8b.3.
};

// ISteamRemoteStorage — SetCloudEnabledForApp lives near the top of the
// vtable in v016 but the exact slot needs the SDK header subset in 8b.3.
class ISteamRemoteStorage {};

}  // namespace wnsteambs
