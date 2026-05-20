#pragma once

#include <cstdint>
#include <functional>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "wn_steam/pb/cmsg_client_license_list.h"
#include "wn_steam/pb/cmsg_client_pics.h"

// WnLibraryStore — the source of truth for "what does the user own".
//
// Lifecycle:
//   1. ClientLicenseList arrives → ingest_license_list(...) — every
//      package the user has any kind of grant on, with the per-package
//      access_token Steam ships in the License entry.
//   2. CMClient's library-populate orchestrator drives PICS:
//      • get_pending_package_pics_request() → list of (packageid, token)
//        we haven't fetched product info for yet
//      • ingest_package_pics(...) — for each returned PICS package buffer
//        we parse the VDF and extract appids + depotids, creating
//        OwnedApp stubs we then need to fetch app PICS for
//      • get_pending_app_pics_request() → list of (appid, token=0)
//      • ingest_app_pics(...) — parses app VDF (common.name, common.type,
//        extended.listofdlc, common.parent), fills OwnedApp fields
//      • get_pending_app_access_token_request() — apps with missing_token
//        set, which need a token before we can re-request PICS

namespace wn_steam {

struct OwnedPackage {
    uint32_t              package_id    = 0;
    uint64_t              access_token  = 0;   // from License OR from PICS access-token grant
    int32_t               change_number = 0;   // from PICS — used to skip refetch on unchanged
    uint32_t              license_flags = 0;   // from License (cancelled/expired/borrowed/etc.)
    uint32_t              license_type  = 0;   // ELicenseType
    bool                  pics_fetched  = false;
    std::vector<uint32_t> app_ids;             // extracted from package VDF "appids"
    std::vector<uint32_t> depot_ids;           // extracted from package VDF "depotids"
};

struct OwnedApp {
    uint32_t              app_id            = 0;
    uint32_t              change_number     = 0;
    std::string           name;
    std::string           sort_as;           // optional alternate sort key
    std::string           type;              // "Game" / "DLC" / "Tool" / "Application" / "Demo" / ...
    std::string           os_list;           // CSV "windows,linux,macos"
    uint32_t              parent_app_id     = 0;   // for DLC: the game it belongs to
    std::vector<uint32_t> dlc_app_ids;       // for games: known DLC children (from extended.listofdlc)
    std::vector<uint32_t> source_package_ids;// which packages grant this app
    bool                  pics_fetched      = false;
    bool                  missing_token     = false;   // PICS reported missing_token → need access-token req
    uint64_t              access_token      = 0;
};

class WnLibraryStore {
public:
    using SnapshotObserver = std::function<void()>;

    // Ingest the post-logon ClientLicenseList push.
    void ingest_license_list(const pb::CMsgClientLicenseList& msg);

    // Build the next batch of PICS product-info requests for packages we
    // haven't fetched yet. Caller passes the result through CMClient and
    // routes the response back via ingest_package_pics_response.
    [[nodiscard]] std::vector<pb::PicsPackageInfoReq>
    get_pending_package_pics_request(size_t max_count = 256) const;

    // Same for app PICS. Skips apps with missing_token=true (those need an
    // access-token request first).
    [[nodiscard]] std::vector<pb::PicsAppInfoReq>
    get_pending_app_pics_request(size_t max_count = 256) const;

    // App IDs that PICS told us require an access token. Caller wires the
    // result of pics_get_access_tokens through ingest_app_access_tokens.
    [[nodiscard]] std::vector<uint32_t>
    get_apps_needing_access_token() const;

    // PICS package responses — parse buffer, fill app_ids/depot_ids, create
    // stub OwnedApp entries for any appids we don't yet know about.
    void ingest_package_pics_response(const pb::CMsgClientPICSProductInfoResponse& resp);

    // PICS app responses — parse buffer, fill name/type/parent/dlc.
    void ingest_app_pics_response(const pb::CMsgClientPICSProductInfoResponse& resp);

    // PICS access-token grants for apps that previously had missing_token=true.
    void ingest_app_access_tokens(const pb::CMsgClientPICSAccessTokenResponse& resp);

    // Snapshot accessors — return copies (the store may mutate concurrently).
    // `apps()` returns EVERY tracked app, including parent stubs the user
    // doesn't actually own (we keep them so DLC the user owns can resolve
    // its parent name + child relationships). For "what the user actually
    // owns" use owned_apps() — it filters on source_package_ids not empty.
    [[nodiscard]] std::vector<OwnedPackage> packages() const;
    [[nodiscard]] std::vector<OwnedApp>     apps() const;
    [[nodiscard]] std::vector<OwnedApp>     owned_apps() const;
    [[nodiscard]] std::optional<OwnedApp>   find_app(uint32_t app_id) const;
    [[nodiscard]] size_t                    package_count() const;
    [[nodiscard]] size_t                    app_count() const;
    [[nodiscard]] size_t                    owned_app_count() const;

    // JSON snapshot for JNI/Kotlin consumption. Shape:
    //   {
    //     "packages": [ {"id":12345,"flags":0,"license_type":1,
    //                    "access_token":"...","change_number":42}, ... ],
    //     "owned_apps": [ {"id":220,"name":"Half-Life 2","type":"Game",
    //                      "parent":0,"dlc":[420,500,...],
    //                      "src_packages":[12345], "access_token":"0"}, ... ],
    //     "all_apps_count": 683,
    //     "owned_apps_count": 412
    //   }
    // The `owned_apps` array is filtered (source_package_ids not empty);
    // parent-stub apps are excluded. The full set is reachable via the
    // C++ `apps()` accessor but isn't part of the JSON snapshot since the
    // UI never wants to render those.
    [[nodiscard]] std::string snapshot_json() const;

    // Fires after every successful ingest. Used by the upper layers (Kotlin
    // facade in Phase 4.4c) to push fresh snapshots into the UI.
    void set_observer(SnapshotObserver obs);

private:
    void notify_();   // fires observer without holding the lock

    mutable std::mutex                          mu_;
    std::unordered_map<uint32_t, OwnedPackage>  packages_;
    std::unordered_map<uint32_t, OwnedApp>      apps_;

    std::mutex                                  obs_mu_;
    SnapshotObserver                            observer_;
};

}  // namespace wn_steam
