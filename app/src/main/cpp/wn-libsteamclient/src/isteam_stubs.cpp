
#include "wn_libsteamclient/runtime_state.h"
#include "wn_libsteamclient/callbacks.h"
#include "wn_steam/cm_bridge.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <cstdio>
#include <cstdlib>     // std::getenv, std::strtoul — GetAppID env fallback
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace lsc_cb = wn_libsteamclient::callbacks;

namespace wn_libsteamclient {

static std::mutex& async_read_mu() {
    static std::mutex m;
    return m;
}
static std::unordered_map<uint64_t, std::vector<uint8_t>>& async_read_buffers() {
    static std::unordered_map<uint64_t, std::vector<uint8_t>> m;
    return m;
}

struct StreamSlot {
    int         fd        = -1;
    std::string tempPath;
    std::string finalPath;
    std::string name;     // original pchFile passed to Open
    int64_t     bytes     = 0;
};
static std::mutex& stream_mu() { static std::mutex m; return m; }
static std::unordered_map<uint64_t, StreamSlot>& streams() {
    static std::unordered_map<uint64_t, StreamSlot> m;
    return m;
}

class ISteamUtilsStub {
public:
    virtual uint32_t GetSecondsSinceAppActive()              { return 0; }                // 0
    virtual uint32_t GetSecondsSinceComputerActive()         { return 0; }                // 1
    virtual int      GetConnectedUniverse()                  { return 1; /*Public*/ }     // 2
    virtual uint32_t GetServerRealTime()                     {                              // 3
        auto anchor   = pushed().server_realtime.load();
        auto anchor_local_ms = pushed().server_realtime_anchor_local_ms.load();
        if (anchor != 0 && anchor_local_ms != 0) {
            const auto now = std::chrono::steady_clock::now();
            const auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                now.time_since_epoch()).count();
            auto elapsed_s = (now_ms - anchor_local_ms) / 1000;
            if (elapsed_s < 0) elapsed_s = 0;  // clock went backwards
            return static_cast<uint32_t>(anchor + static_cast<uint32_t>(elapsed_s));
        }
        return static_cast<uint32_t>(::time(nullptr));
    }
    virtual const char* GetIPCountry()                       {                              // 4
        auto& p = pushed();
        if (p.ip_country_set.load() == 0) return "US";
        return p.ip_country.c_str();
    }
    virtual bool GetImageSize(int iImage, uint32_t* pnWidth, uint32_t* pnHeight) {
        if (iImage <= 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().image_registry.find(iImage);
        if (it == pushed().image_registry.end()) return false;
        if (pnWidth)  *pnWidth  = static_cast<uint32_t>(it->second.width);
        if (pnHeight) *pnHeight = static_cast<uint32_t>(it->second.height);
        return true;
    }
    virtual bool GetImageRGBA(int iImage, uint8_t* pubDest, int nDestBufferSize) {
        if (iImage <= 0 || !pubDest || nDestBufferSize <= 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().image_registry.find(iImage);
        if (it == pushed().image_registry.end()) return false;
        const auto& img = it->second;
        if (static_cast<int>(img.rgba.size()) > nDestBufferSize) return false;
        std::memcpy(pubDest, img.rgba.data(), img.rgba.size());
        return true;
    }
    virtual bool     GetCSERIPPort(uint32_t*, uint16_t*)     { return false; }            // 7
    virtual uint8_t  GetCurrentBatteryPower()                { return 255; /*AC*/ }       // 8
    virtual uint32_t GetAppID() {
        uint32_t app = pushed().app_id.load();
        if (app != 0) return app;
        const char* env = std::getenv("SteamAppId");
        if (env && *env) {
            char* end = nullptr;
            unsigned long v = std::strtoul(env, &end, 10);
            if (end != env && v != 0 && v <= 0x7fffffffu) {
                return static_cast<uint32_t>(v);
            }
        }
        return 0;
    } // 9
    virtual void     SetOverlayNotificationPosition(int)     {}                           // 10
    virtual bool     IsAPICallCompleted(uint64_t hCall, bool* pbFailed) {
        if (hCall == 0) return false;
        auto& s = state();
        std::lock_guard<std::mutex> lk(s.call_results_mu);
        auto it = s.call_results_pending.find(hCall);
        if (it == s.call_results_pending.end()) return false;
        if (pbFailed) *pbFailed = it->second.io_failure;
        return true;
    }
    virtual int      GetAPICallFailureReason(uint64_t /*hCall*/)       { return -1; }
    virtual bool     GetAPICallResult(uint64_t hCall, void* pCallback,
                                       int cubCallback, int iCallbackExpected,
                                       bool* pbFailed) {
        if (hCall == 0) return false;
        auto& s = state();
        std::lock_guard<std::mutex> lk(s.call_results_mu);
        auto it = s.call_results_pending.find(hCall);
        if (it == s.call_results_pending.end()) return false;
        const auto& msg = it->second;
        if (iCallbackExpected != 0 && msg.callback_id != iCallbackExpected) {
            return false;  // keep the entry — caller asked wrong type
        }
        if (pCallback && cubCallback > 0 && !msg.body.empty()) {
            size_t n = std::min<size_t>(static_cast<size_t>(cubCallback), msg.body.size());
            std::memcpy(pCallback, msg.body.data(), n);
        }
        if (pbFailed) *pbFailed = msg.io_failure;
        s.call_results_pending.erase(it);
        return true;
    }
    virtual void     RunFrame()                              {}                           // 14
    virtual uint32_t GetIPCCallCount()                       { return 0; }                // 15
    virtual void     SetWarningMessageHook(void*)            {}                           // 16
    virtual bool     IsOverlayEnabled()                      { return true; }             // 17
    virtual bool     BOverlayNeedsPresent()                  { return false; }            // 18
    virtual uint64_t CheckFileSignature(const char* /*pszFileName*/) {
        uint64_t hCall = alloc_api_call_handle();
        lsc_cb::CheckFileSignature cb{};
        cb.m_eCheckFileSignature = 4; // NoSignaturesFoundForThisFile
        push_call_result(hCall, lsc_cb::kCheckFileSignature,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual bool     ShowGamepadTextInput(int, int, const char*, uint32_t, const char*) { return false; }       // 20
    virtual uint32_t GetEnteredGamepadTextLength()           { return 0; }                // 21
    virtual bool     GetEnteredGamepadTextInput(char*, uint32_t) { return false; }        // 22
    virtual const char* GetSteamUILanguage()                 {                              // 23
        auto& p = pushed();
        if (p.ui_language.empty()) return "english";
        return p.ui_language.c_str();
    }
    virtual bool     IsSteamRunningInVR()                    { return false; }            // 24
    virtual void     SetOverlayNotificationInset(int, int)   {}                           // 25
    virtual bool     IsSteamInBigPictureMode()               { return false; }            // 26
    virtual void     StartVRDashboard()                      {}                           // 27
    virtual bool     IsVRHeadsetStreamingEnabled()           { return false; }            // 28
    virtual void     SetVRHeadsetStreamingEnabled(bool)      {}                           // 29
    virtual bool     IsSteamChinaLauncher()                  { return false; }
    virtual bool     InitFilterText(uint32_t)                { return false; }
    virtual int      FilterText(int /*eContext*/, uint64_t /*srcSid*/,
                                  const char* in, char* out, uint32_t outSize) {
        if (!out || outSize == 0) return 0;
        if (!in)  { out[0] = '\0'; return 0; }
        uint32_t n = static_cast<uint32_t>(std::strlen(in));
        uint32_t copy = std::min<uint32_t>(n, outSize - 1);
        if (copy > 0) std::memcpy(out, in, copy);
        out[copy] = '\0';
        return static_cast<int>(copy);
    }
    virtual int      GetIPv6ConnectivityState(int)           { return 0; }
    virtual bool     IsSteamRunningOnSteamDeck()             { return false; }
    virtual bool     ShowFloatingGamepadTextInput(int, int, int, int, int) { return false; }
    virtual void     SetGameLauncherMode(bool)               {}
    virtual bool     DismissFloatingGamepadTextInput()       { return false; }
};

class ISteamUserStub {
public:
    virtual int       GetHSteamUser()                                { return state().user.load(); } // 0
    virtual bool      BLoggedOn()                                    { return state().logged_on.load(); } // 1
    virtual uint64_t  GetSteamID()                                   { return pushed().steam_id.load(); } // 2
    virtual int       InitiateGameConnection_DEPRECATED(void*, int, uint64_t, uint32_t, uint16_t, bool) { return 0; } // 3
    virtual void      TerminateGameConnection_DEPRECATED(uint32_t, uint16_t) {} // 4
    virtual void      TrackAppUsageEvent(uint64_t, int, const char*) {}              // 5
    virtual bool      GetUserDataFolder(char* pchBuffer, int cubBuffer) {
        if (!pchBuffer || cubBuffer <= 0) return false;
        uint32_t app = pushed().app_id.load();
        if (app == 0) return false;
        std::string ud;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().app_cloud_remote_dirs.find(app);
            if (it == pushed().app_cloud_remote_dirs.end()) return false;
            ud = it->second;
        }
        if (ud.size() > 7 && ud.compare(ud.size() - 7, 7, "/remote") == 0) {
            ud.resize(ud.size() - 7);
        } else if (ud.size() > 8 && ud.compare(ud.size() - 8, 8, "/remote/") == 0) {
            ud.resize(ud.size() - 8);
        }
        size_t copy = std::min<size_t>(ud.size(),
                                       static_cast<size_t>(cubBuffer - 1));
        std::memcpy(pchBuffer, ud.data(), copy);
        pchBuffer[copy] = '\0';
        return true;
    }
    virtual void      StartVoiceRecording()                          {}              // 7
    virtual void      StopVoiceRecording()                           {}              // 8
    virtual int       GetAvailableVoice(uint32_t*, uint32_t*, uint32_t) { return 0; }// 9
    virtual int       GetVoice(bool, void*, uint32_t, uint32_t*, bool, void*, uint32_t, uint32_t*, uint32_t) { return 0; } // 10
    virtual int       DecompressVoice(const void*, uint32_t, void*, uint32_t, uint32_t*, uint32_t) { return 0; } // 11
    virtual uint32_t  GetVoiceOptimalSampleRate()                    { return 11025; } // 12
    virtual uint64_t  GetAuthSessionTicket(void* buf, int maxLen,
                                            uint32_t* pcbTicket,
                                            const void* /*pSteamNetworkingIdentity*/) {
        uint32_t h = pushed().next_auth_ticket_handle.fetch_add(1);
        if (h == 0) h = pushed().next_auth_ticket_handle.fetch_add(1);  // skip 0
        uint32_t app_id = pushed().app_id.load();
        std::vector<uint8_t> body;
        bool cm_backed = false;
        if (app_id != 0) {
            size_t need = 0;
            wn_cm_get_cached_app_ownership_ticket(app_id, nullptr, 0, &need);
            if (need > 0 && need <= 16 * 1024) {  // sanity-cap at 16KB
                std::vector<uint8_t> ownership(need);
                size_t got = 0;
                if (wn_cm_get_cached_app_ownership_ticket(
                        app_id, ownership.data(), ownership.size(), &got)
                    && got == need) {
                    body.reserve(24 + ownership.size());
                    body.resize(24, 0);
                    auto put_u32 = [&](size_t off, uint32_t v) {
                        body[off + 0] = static_cast<uint8_t>(v       & 0xFF);
                        body[off + 1] = static_cast<uint8_t>((v >> 8)  & 0xFF);
                        body[off + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
                        body[off + 3] = static_cast<uint8_t>((v >> 24) & 0xFF);
                    };
                    put_u32(0,  20);                            // fixed prefix
                    put_u32(4,  0);                             // padding
                    put_u32(8,  0);                             // padding
                    put_u32(12, h);                             // ConnectionID
                    put_u32(16, static_cast<uint32_t>(::time(nullptr)));
                    put_u32(20, 1);                             // ConnectionCount
                    body.insert(body.end(), ownership.begin(), ownership.end());
                    cm_backed = true;
                }
            }
        }
        if (!cm_backed) {
            body.assign(32, 0);
            body[0] = 'W'; body[1] = 'N'; body[2] = 'A'; body[3] = 'T';
            std::memcpy(body.data() + 4,  &h, sizeof(h));
            uint64_t sid = pushed().steam_id.load();
            std::memcpy(body.data() + 8,  &sid, sizeof(sid));
            uint64_t ts = static_cast<uint64_t>(::time(nullptr));
            std::memcpy(body.data() + 16, &ts, sizeof(ts));
        }
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            pushed().auth_tickets[h] = {h, app_id, body};
        }
        if (buf && maxLen > 0) {
            uint32_t copy = std::min<uint32_t>(body.size(), static_cast<uint32_t>(maxLen));
            std::memcpy(buf, body.data(), copy);
        }
        if (pcbTicket) *pcbTicket = static_cast<uint32_t>(body.size());
        lsc_cb::GetAuthSessionTicketResponse cb{};
        cb.m_hAuthTicket = h;
        cb.m_eResult     = 1;  // k_EResultOK
        push_callback(state().user.load(),
                      lsc_cb::kGetAuthSessionTicketResponse,
                      &cb, sizeof(cb));
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "GetAuthSessionTicket(app=%u) → h=%u size=%zu (%s)",
            app_id, h, body.size(), cm_backed ? "CM-backed" : "synthetic");
        return h;
    }
    virtual uint64_t  GetAuthTicketForWebApi(const char* pchIdentity) {
        uint32_t app_id = pushed().app_id.load();
        std::vector<uint8_t> ownership;
        if (app_id != 0) {
            size_t need = 0;
            wn_cm_get_cached_app_ownership_ticket(app_id, nullptr, 0, &need);
            if (need > 0 && need <= 16 * 1024) {
                ownership.resize(need);
                size_t got = 0;
                if (!wn_cm_get_cached_app_ownership_ticket(
                        app_id, ownership.data(), ownership.size(), &got)
                    || got != need) {
                    ownership.clear();
                }
            }
        }
        bool have_cm = !ownership.empty();
        uint32_t h = static_cast<uint32_t>(alloc_api_call_handle() & 0xFFFFFFFF);
        if (h == 0) h = static_cast<uint32_t>(alloc_api_call_handle() & 0xFFFFFFFF);
        std::vector<uint8_t> body;
        if (have_cm) {
            body.reserve(24 + ownership.size());
            body.resize(24, 0);
            auto put_u32 = [&](size_t off, uint32_t v) {
                body[off + 0] = static_cast<uint8_t>(v       & 0xFF);
                body[off + 1] = static_cast<uint8_t>((v >> 8)  & 0xFF);
                body[off + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
                body[off + 3] = static_cast<uint8_t>((v >> 24) & 0xFF);
            };
            put_u32(0,  20);                            // fixed prefix
            put_u32(12, h);                             // ConnectionID
            put_u32(16, static_cast<uint32_t>(::time(nullptr)));
            put_u32(20, 1);                             // ConnectionCount
            body.insert(body.end(), ownership.begin(), ownership.end());
        } else {
            body.assign(32, 0);
            body[0] = 'W'; body[1] = 'N'; body[2] = 'A'; body[3] = 'W';
            std::memcpy(body.data() + 4,  &h, sizeof(h));
            uint64_t sid = pushed().steam_id.load();
            std::memcpy(body.data() + 8,  &sid, sizeof(sid));
            uint64_t ts = static_cast<uint64_t>(::time(nullptr));
            std::memcpy(body.data() + 16, &ts, sizeof(ts));
        }
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            pushed().auth_tickets[h] = {h, app_id, body};
        }
        lsc_cb::GetTicketForWebApiResponse cb{};
        cb.m_hAuthTicket = h;
        cb.m_eResult     = 1; // k_EResultOK
        size_t copy = std::min<size_t>(body.size(), sizeof(cb.m_rgubTicket));
        cb.m_cubTicket = static_cast<int32_t>(copy);
        std::memcpy(cb.m_rgubTicket, body.data(), copy);
        push_callback(state().user.load(),
                      lsc_cb::kGetTicketForWebApiResponse,
                      &cb, sizeof(cb));
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "GetAuthTicketForWebApi(identity=\"%s\") → h=%u size=%zu (%s)",
            pchIdentity ? pchIdentity : "(null)", h, body.size(),
            have_cm ? "CM-backed" : "synthetic");
        return h;
    }
    virtual int       BeginAuthSession(const void* /*ticket*/, int cbTicket,
                                        uint64_t steamID) {
        lsc_cb::ValidateAuthTicketResponse cb{};
        cb.m_SteamID              = steamID;
        cb.m_eAuthSessionResponse = 0;  // k_EAuthSessionResponseOK
        cb.m_OwnerSteamID         = steamID;  // owner == user when not family-shared
        push_callback(state().user.load(),
                      lsc_cb::kValidateAuthTicketResponse,
                      &cb, sizeof(cb));
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "BeginAuthSession(cbTicket=%d, steamID=%llu) -> OK (synthetic validation)",
            cbTicket, static_cast<unsigned long long>(steamID));
        return 0;
    }
    virtual void      EndAuthSession(uint64_t)                       {}
    virtual void      CancelAuthTicket(uint64_t hAuthTicket) {
        std::lock_guard<std::mutex> lk(state_mutex());
        pushed().auth_tickets.erase(static_cast<uint32_t>(hAuthTicket));
    }
    virtual int       UserHasLicenseForApp(uint64_t steamID, uint32_t appID) {
        if (appID == 0) return 2;
        uint64_t self = pushed().steam_id.load();
        if (steamID != 0 && steamID == self) {
            std::lock_guard<std::mutex> lk(state_mutex());
            return pushed().owned_apps.count(appID) > 0 ? 0 : 1;
        }
        return 2; /*NoAuth — we can't speak for other users*/
    }
    virtual bool      BIsBehindNAT()                                 { return true; }  // 19
    virtual void      AdvertiseGame(uint64_t, uint32_t, uint16_t)    {}              // 20
    virtual uint64_t  RequestEncryptedAppTicket(void* rgubData, int cbData) {
        uint32_t app = pushed().app_id.load();
        uint64_t h   = alloc_api_call_handle();
        bool     have_real_bytes = false;
        std::vector<uint8_t> body;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().encrypted_app_tickets.find(app);
            if (it != pushed().encrypted_app_tickets.end() && !it->second.empty()) {
                body = it->second;
                have_real_bytes = true;
            } else {
                body.resize(32);
                std::memcpy(body.data(),      "WNETKT\0\0\0\0\0\0\0\0\0\0", 16);
                std::memcpy(body.data() + 16, &app, sizeof(app));
                uint32_t h32 = static_cast<uint32_t>(h);
                std::memcpy(body.data() + 20, &h32, sizeof(h32));
                uint64_t sid = pushed().steam_id.load();
                std::memcpy(body.data() + 24, &sid, sizeof(sid));
                pushed().encrypted_app_tickets[app] = body;
            }
        }
        int32_t eresult = have_real_bytes ? 1 : 1;  // SDK contract: synthetic returns OK
        pushed().encrypted_app_ticket_eresult.store(eresult);
        (void)rgubData; (void)cbData;
        lsc_cb::EncryptedAppTicketResponse cb{};
        cb.m_eResult = eresult;
        push_call_result(h, lsc_cb::kEncryptedAppTicketResponse,
                         &cb, sizeof(cb), /*io_failure=*/false);
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "RequestEncryptedAppTicket(app=%u) -> hCall=%llu (body=%zu B, %s)",
            app, static_cast<unsigned long long>(h), body.size(),
            have_real_bytes ? "real" : "synthetic");
        return h;
    }
    virtual bool      GetEncryptedAppTicket(void* buf, int cbMax, uint32_t* pcbTicket) {
        uint32_t app = pushed().app_id.load();
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().encrypted_app_tickets.find(app);
        if (it == pushed().encrypted_app_tickets.end() || it->second.empty()) {
            if (pcbTicket) *pcbTicket = 0;
            return false;
        }
        const auto& body = it->second;
        uint32_t copy = std::min<uint32_t>(body.size(),
                                            static_cast<uint32_t>(std::max(0, cbMax)));
        if (buf && copy > 0) std::memcpy(buf, body.data(), copy);
        if (pcbTicket) *pcbTicket = static_cast<uint32_t>(body.size());
        return true;
    }
    virtual int       GetGameBadgeLevel(int nSeries, bool bFoil) {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return 0;
        int32_t key = (static_cast<int32_t>(app) & 0x0FFFFFFF)
                    | ((nSeries & 0x07) << 28)
                    | (bFoil ? (1 << 31) : 0);
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().self_game_badges.find(key);
        return it == pushed().self_game_badges.end() ? 0 : it->second;
    }
    virtual int       GetPlayerSteamLevel() {
        return pushed().self_player_level.load();
    }
    virtual uint64_t RequestStoreAuthURL(const char* pchRedirectURL) {
        uint64_t hCall = alloc_api_call_handle();
        lsc_cb::StoreAuthURLResponse cb{};
        const char* redirect = pchRedirectURL ? pchRedirectURL : "";
        std::snprintf(cb.m_szURL, sizeof(cb.m_szURL),
                      "https://store.steampowered.com/login/?redir=%s",
                      redirect);
        push_call_result(hCall, lsc_cb::kStoreAuthURLResponse,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual bool      BIsPhoneVerified() {
        return pushed().account_phone_verified.load();
    }
    virtual bool      BIsTwoFactorEnabled() {
        return pushed().account_two_factor_enabled.load();
    }
    virtual bool      BIsPhoneIdentifying() {
        return pushed().account_phone_identifying.load();
    }
    virtual bool      BIsPhoneRequiringVerification() {
        return pushed().account_phone_requires_verification.load();
    }
    virtual uint64_t GetMarketEligibility() {
        uint64_t hCall = alloc_api_call_handle();
        lsc_cb::MarketEligibilityResponse cb{};
        bool twoFA = pushed().account_two_factor_enabled.load();
        bool phone = pushed().account_phone_verified.load();
        cb.m_bAllowed = (twoFA && phone);
        cb.m_eNotAllowedReason             = cb.m_bAllowed ? 0 : 2;
        cb.m_rtAllowedAtTime               = 0;
        cb.m_cdaySteamGuardRequiredDays    = cb.m_bAllowed ? 0 : 15;
        cb.m_cdayNewDeviceCooldown         = 0;
        push_call_result(hCall, lsc_cb::kMarketEligibilityResponse,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual uint64_t GetDurationControl() {
        uint64_t hCall = alloc_api_call_handle();
        lsc_cb::DurationControl cb{};
        cb.m_eResult        = 1; // k_EResultOK
        cb.m_appid          = pushed().app_id.load();
        cb.m_bApplicable    = false; // non-CN
        cb.m_csecsLast5h    = 0;
        cb.m_progress       = 0;     // k_EDurationControlProgress_Full
        cb.m_notification   = 0;     // k_EDurationControlNotification_None
        cb.m_csecsToday     = 0;
        cb.m_csecsRemaining = 0;
        push_call_result(hCall, lsc_cb::kDurationControl,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual bool      BSetDurationControlOnlineState(int /*state*/)  { return true; }  // 32
};

class ISteamAppsStub {
public:
    static bool env_app_id_matches(uint32_t app) {
        if (app == 0) return false;
        const char* env = std::getenv("SteamAppId");
        if (!env || !*env) return false;
        char* end = nullptr;
        unsigned long v = std::strtoul(env, &end, 10);
        return (end != env && v == app);
    }

    virtual bool      BIsSubscribed() {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return false;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            if (pushed().owned_apps.count(app) > 0) return true;
        }
        return env_app_id_matches(app);
    }
    virtual bool      BIsLowViolence() {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        return pushed().app_low_violence.count(app) > 0;
    }
    virtual bool      BIsCybercafe()                                 { return false; } // 2
    virtual bool      BIsVACBanned() {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        return pushed().app_vac_banned.count(app) > 0;
    }
    virtual const char* GetCurrentGameLanguage() {
        static thread_local std::string tls_lang;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            tls_lang = pushed().ui_language;
        }
        if (tls_lang.empty()) tls_lang = "english";
        return tls_lang.c_str();
    }
    virtual const char* GetAvailableGameLanguages()                  { return "english"; } // 5
    virtual bool      BIsSubscribedApp(uint32_t appId)               {                 // 6
        {
            auto& p = pushed();
            std::lock_guard<std::mutex> lk(state_mutex());
            if (p.owned_apps.count(appId) > 0) return true;
        }
        return env_app_id_matches(appId);
    }
    virtual bool      BIsDlcInstalled(uint32_t appId)                {                 // 7
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        if (p.installed_apps.count(appId) > 0 &&
            p.owned_apps.count(appId) > 0) {
            return true;
        }
        if (p.owned_apps.count(appId) == 0) return false;
        for (const auto& kv : p.app_dlcs) {
            for (const auto& d : kv.second) {
                if (d.app_id == appId &&
                    p.installed_apps.count(kv.first) > 0) {
                    return true;
                }
            }
        }
        return false;
    }
    virtual uint32_t GetEarliestPurchaseUnixTime(uint32_t app_id) {
        if (app_id == 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto pit = pushed().app_source_packages.find(app_id);
        if (pit == pushed().app_source_packages.end()) return 0;
        uint32_t earliest = 0;
        for (uint32_t pkg : pit->second) {
            auto lit = pushed().licenses.find(pkg);
            if (lit == pushed().licenses.end()) continue;
            uint32_t t = lit->second.time_created;
            if (t == 0) continue;
            if (earliest == 0 || t < earliest) earliest = t;
        }
        return earliest;
    }
    virtual bool BIsSubscribedFromFreeWeekend() {
        uint32_t app_id = pushed().app_id.load();
        if (app_id == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto pit = pushed().app_source_packages.find(app_id);
        if (pit == pushed().app_source_packages.end()) return false;
        for (uint32_t pkg : pit->second) {
            auto lit = pushed().licenses.find(pkg);
            if (lit == pushed().licenses.end()) continue;
            if (lit->second.license_type == 11 /*FreeWeekend*/) return true;
        }
        return false;
    }
    virtual int       GetDLCCount(uint32_t appId) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_dlcs.find(appId);
        return it == pushed().app_dlcs.end() ? 0 : static_cast<int>(it->second.size());
    }
    virtual bool      BGetDLCDataByIndex(uint32_t appId, int iDLC,
                                          uint32_t* pAppID, bool* pbAvailable,
                                          char* pchName, int cchNameBufferSize) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_dlcs.find(appId);
        if (it == pushed().app_dlcs.end()) return false;
        const auto& dlcs = it->second;
        if (iDLC < 0 || static_cast<size_t>(iDLC) >= dlcs.size()) return false;
        const auto& d = dlcs[static_cast<size_t>(iDLC)];
        if (pAppID)      *pAppID      = d.app_id;
        if (pbAvailable) *pbAvailable = d.available;
        if (pchName && cchNameBufferSize > 0) {
            int copy = std::min<int>(static_cast<int>(d.name.size()),
                                      cchNameBufferSize - 1);
            if (copy > 0) std::memcpy(pchName, d.name.data(), copy);
            pchName[copy] = '\0';
        }
        return true;
    }
    virtual void      InstallDLC(uint32_t)                           {}                // 12
    virtual void      UninstallDLC(uint32_t)                         {}                // 13
    virtual void      RequestAppProofOfPurchaseKey(uint32_t)         {}                // 14
    virtual bool      GetCurrentBetaName(char* pchName, int cchNameBufferSize) {
        if (!pchName || cchNameBufferSize <= 0) return false;
        uint32_t app_id = pushed().app_id.load();
        if (app_id == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_current_beta.find(app_id);
        if (it == pushed().app_current_beta.end()) return false;
        const std::string& name = it->second;
        if (name.empty()) return false;
        size_t copy = std::min<size_t>(name.size(),
                                       static_cast<size_t>(cchNameBufferSize - 1));
        std::memcpy(pchName, name.data(), copy);
        pchName[copy] = '\0';
        return true;
    }
    virtual bool      MarkContentCorrupt(bool /*bMissingFilesOnly*/) {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        pushed().apps_marked_corrupt.insert(app);
        return true;
    }
    virtual uint32_t  GetInstalledDepots(uint32_t appID, uint32_t* pvecDepots,
                                          uint32_t cMaxDepots) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_installed_depots.find(appID);
        if (it == pushed().app_installed_depots.end()) return 0;
        const auto& depots = it->second;
        uint32_t copy = std::min<uint32_t>(static_cast<uint32_t>(depots.size()),
                                            cMaxDepots);
        if (pvecDepots && copy > 0) {
            for (uint32_t i = 0; i < copy; ++i) pvecDepots[i] = depots[i];
        }
        return copy;
    }
    virtual uint32_t  GetAppInstallDir(uint32_t appId, char* buf, uint32_t cap) {       // 18
        if (!buf || cap == 0) return 0;
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = p.app_install_dirs.find(appId);
        if (it == p.app_install_dirs.end()) {
            buf[0] = '\0';
            return 0;
        }
        const std::string& d = it->second;
        uint32_t n = static_cast<uint32_t>(d.size());
        uint32_t copy = (n + 1 < cap) ? n : cap - 1;
        std::memcpy(buf, d.data(), copy);
        buf[copy] = '\0';
        return n + 1;  // documented: returns length INCLUDING null
    }
    virtual bool      BIsAppInstalled(uint32_t appId)                {                 // 19
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        return p.installed_apps.count(appId) > 0;
    }
    virtual uint64_t GetAppOwner() {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return 0;
        uint64_t self_sid = pushed().steam_id.load();
        if (self_sid == 0) return 0;
        uint32_t self_account = static_cast<uint32_t>(self_sid & 0xFFFFFFFFu);
        std::lock_guard<std::mutex> lk(state_mutex());
        bool in_owned = pushed().owned_apps.count(app) > 0;
        auto pit = pushed().app_source_packages.find(app);
        bool has_pkgs = (pit != pushed().app_source_packages.end());
        if (!in_owned && !has_pkgs) return 0;
        if (!has_pkgs) return self_sid;  // owned but no pkg map — assume self
        uint32_t fallback_owner = 0;
        bool has_self_match     = false;
        for (uint32_t pkg : pit->second) {
            auto lit = pushed().licenses.find(pkg);
            if (lit == pushed().licenses.end()) continue;
            if (lit->second.owner_id == self_account) {
                has_self_match = true;
                break;
            }
            if (fallback_owner == 0) fallback_owner = lit->second.owner_id;
        }
        if (has_self_match) return self_sid;
        if (fallback_owner != 0) {
            return 0x0110000100000000ULL |
                   static_cast<uint64_t>(fallback_owner);
        }
        return self_sid;
    }
    virtual const char* GetLaunchQueryParam(const char*)             { return ""; }    // 21
    virtual bool      GetDlcDownloadProgress(uint32_t appID, uint64_t* pBytesDownloaded,
                                              uint64_t* pBytesTotal) {
        if (appID == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_dl_progress.find(appID);
        if (it == pushed().app_dl_progress.end()) return false;
        if (it->second.bytes_total == 0) return false;
        if (pBytesDownloaded) *pBytesDownloaded = it->second.bytes_downloaded;
        if (pBytesTotal)      *pBytesTotal      = it->second.bytes_total;
        return true;
    }
    virtual int       GetAppBuildId() {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_build_ids.find(app);
        return it == pushed().app_build_ids.end() ? 0 : static_cast<int>(it->second);
    }
    virtual void      RequestAllProofOfPurchaseKeys()                {}                // 24
    virtual uint64_t GetFileDetails(const char* pchFile) {
        uint64_t hCall = alloc_api_call_handle();
        lsc_cb::FileDetailsResult cb{};
        uint32_t app = pushed().app_id.load();
        std::string base;
        if (app != 0 && pchFile && *pchFile) {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().app_install_dirs.find(app);
            if (it != pushed().app_install_dirs.end()) base = it->second;
        }
        if (base.empty()) {
            cb.m_eResult = 9; // k_EResultFileNotFound
            push_call_result(hCall, lsc_cb::kFileDetailsResult,
                             &cb, sizeof(cb), /*io_failure=*/false);
            return hCall;
        }
        std::string fname(pchFile);
        if (fname.find("..") != std::string::npos || fname[0] == '/') {
            cb.m_eResult = 9;
            push_call_result(hCall, lsc_cb::kFileDetailsResult,
                             &cb, sizeof(cb), /*io_failure=*/false);
            return hCall;
        }
        std::string path = base;
        if (!path.empty() && path.back() != '/') path.push_back('/');
        path.append(fname);
        int fd = ::open(path.c_str(), O_RDONLY);
        if (fd < 0) {
            cb.m_eResult = 9;
            push_call_result(hCall, lsc_cb::kFileDetailsResult,
                             &cb, sizeof(cb), /*io_failure=*/false);
            return hCall;
        }
        struct stat st {};
        if (::fstat(fd, &st) != 0 || st.st_size > 64LL * 1024 * 1024) {
            ::close(fd);
            cb.m_eResult = 2; // k_EResultFail
            push_call_result(hCall, lsc_cb::kFileDetailsResult,
                             &cb, sizeof(cb), /*io_failure=*/false);
            return hCall;
        }
        uint64_t h64 = 0xcbf29ce484222325ULL;
        uint8_t buf[8192];
        ssize_t n;
        while ((n = ::read(fd, buf, sizeof(buf))) > 0) {
            for (ssize_t i = 0; i < n; ++i) {
                h64 ^= buf[i];
                h64 *= 0x100000001b3ULL;
            }
        }
        ::close(fd);
        cb.m_eResult    = 1; // k_EResultOK
        cb.m_ulFileSize = static_cast<uint64_t>(st.st_size);
        std::memcpy(cb.m_FileSHA + 0,  &h64, sizeof(h64));
        uint64_t h64_rot = (h64 << 32) | (h64 >> 32);
        std::memcpy(cb.m_FileSHA + 8,  &h64_rot, sizeof(h64_rot));
        uint32_t h32 = static_cast<uint32_t>(h64 ^ h64_rot);
        std::memcpy(cb.m_FileSHA + 16, &h32, sizeof(h32));
        cb.m_unFlags = 0;
        push_call_result(hCall, lsc_cb::kFileDetailsResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual int       GetLaunchCommandLine(char* buf, int cubMax) {
        if (!buf || cubMax <= 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        const std::string& cl = pushed().launch_command_line;
        int n = static_cast<int>(cl.size());
        int copy = std::min(n, cubMax - 1);
        if (copy > 0) std::memcpy(buf, cl.data(), copy);
        buf[copy] = '\0';
        return copy;
    }
    virtual bool BIsSubscribedFromFamilySharing() {
        uint32_t app_id = pushed().app_id.load();
        if (app_id == 0) return pushed().app_is_family_shared.load();
        uint64_t sid = pushed().steam_id.load();
        if (sid == 0) return pushed().app_is_family_shared.load();
        uint32_t self_account = static_cast<uint32_t>(sid & 0xFFFFFFFFu);
        std::lock_guard<std::mutex> lk(state_mutex());
        auto pit = pushed().app_source_packages.find(app_id);
        if (pit == pushed().app_source_packages.end()) {
            return pushed().app_is_family_shared.load();
        }
        bool any_license_match = false;
        bool any_self_owned    = false;
        for (uint32_t pkg : pit->second) {
            auto lit = pushed().licenses.find(pkg);
            if (lit == pushed().licenses.end()) continue;
            any_license_match = true;
            if (lit->second.owner_id == self_account) {
                any_self_owned = true;
                break;
            }
        }
        if (!any_license_match) {
            return pushed().app_is_family_shared.load();
        }
        return !any_self_owned;
    }
    virtual bool BIsTimedTrial(uint32_t* pcSecondsAllowed,
                                uint32_t* pcSecondsPlayed) {
        uint32_t app = pushed().app_id.load();
        if (app == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto pit = pushed().app_source_packages.find(app);
        if (pit == pushed().app_source_packages.end()) return false;
        for (uint32_t pkg : pit->second) {
            auto lit = pushed().licenses.find(pkg);
            if (lit == pushed().licenses.end()) continue;
            if (lit->second.minute_limit > 0) {
                if (pcSecondsAllowed) {
                    *pcSecondsAllowed = static_cast<uint32_t>(
                        lit->second.minute_limit * 60);
                }
                if (pcSecondsPlayed) {
                    *pcSecondsPlayed = static_cast<uint32_t>(
                        std::max(0, lit->second.minutes_used) * 60);
                }
                return true;
            }
        }
        return false;
    }
    virtual bool      SetDlcContext(uint32_t /*appID*/)              { return true; }  // 29
};

class ISteamFriendsStub {
public:
    virtual const char* GetPersonaName()                             {                    // 0
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        return p.persona_name.empty() ? "Player" : p.persona_name.c_str();
    }
    virtual uint64_t  SetPersonaName(const char* pchPersonaName) {
        if (!pchPersonaName) return 0;
        uint64_t h = alloc_api_call_handle();
        std::string name(pchPersonaName);
        uint64_t self;
        bool name_changed;
        int current_state;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            name_changed = (pushed().persona_name != name);
            pushed().persona_name = name;  // copy: keep one for the bridge call below
            self = pushed().steam_id.load();
            current_state = pushed().persona_state.load();
        }
        if (name_changed && self != 0) {
            lsc_cb::PersonaStateChange psc{};
            psc.m_ulSteamID    = self;
            psc.m_nChangeFlags = lsc_cb::kPersonaChangeName;
            push_callback(state().user.load(),
                          lsc_cb::kPersonaStateChange, &psc, sizeof(psc));
        }
        wn_cm_set_persona_name(name.c_str(),
                               current_state > 0 ? current_state : 1);
        lsc_cb::SetPersonaNameResponse resp{};
        resp.m_bSuccess      = state().logged_on.load();
        resp.m_bLocalSuccess = true;
        resp.m_result        = state().logged_on.load() ? 1 : 6;  // OK / NoConnection
        push_call_result(h, lsc_cb::kSetPersonaNameResponse,
                         &resp, sizeof(resp), /*io_failure=*/false);
        return h;
    }
    virtual int       GetPersonaState()                              { return pushed().persona_state.load(); } // 2
    virtual int       GetFriendCount(int /*flags*/)                  {                    // 3
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        return static_cast<int>(p.friends.size());
    }
    virtual uint64_t  GetFriendByIndex(int idx, int /*flags*/)       {                    // 4
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        if (idx < 0 || static_cast<size_t>(idx) >= p.friends.size()) return 0;
        return p.friends[idx];
    }
    virtual int       GetFriendRelationship(uint64_t sid) {
        if (sid == 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (uint64_t f : pushed().friends) {
            if (f == sid) return 3 /*Friend*/;
        }
        return 0 /*None*/;
    }
    virtual int       GetFriendPersonaState(uint64_t sid) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().friend_persona_states.find(sid);
        return it == pushed().friend_persona_states.end() ? 0 : static_cast<int>(it->second);
    }
    virtual const char* GetFriendPersonaName(uint64_t sid)           {                    // 7
        auto& p = pushed();
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = p.friend_persona_names.find(sid);
        return it == p.friend_persona_names.end() ? "" : it->second.c_str();
    }
    virtual bool      GetFriendGamePlayed(uint64_t sid, void* pFriendGameInfo) {
        if (!pFriendGameInfo) return false;
        std::memset(pFriendGameInfo, 0, 24);
        uint32_t app;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().friend_game_played_app.find(sid);
            if (it == pushed().friend_game_played_app.end()) return false;
            app = it->second;
        }
        if (app == 0) return false;
        uint64_t gameID = static_cast<uint64_t>(app);
        std::memcpy(pFriendGameInfo, &gameID, sizeof(gameID));
        return true;
    }
    virtual const char* GetFriendPersonaNameHistory(uint64_t, int)   { return ""; }      // 9
    virtual int       GetFriendSteamLevel(uint64_t sid) {
        if (sid == 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().friend_steam_levels.find(sid);
        return it == pushed().friend_steam_levels.end() ? 0 : it->second;
    }
    virtual const char* GetPlayerNickname(uint64_t sid) {
        if (sid == 0) return nullptr;
        static thread_local std::string tls;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().player_nicknames.find(sid);
            if (it == pushed().player_nicknames.end()) return nullptr;
            tls = it->second;
        }
        return tls.empty() ? nullptr : tls.c_str();
    }
    virtual int       GetFriendsGroupCount()                         { return 0; }       // 12
    virtual int16_t   GetFriendsGroupIDByIndex(int)                  { return 0; }       // 13
    virtual const char* GetFriendsGroupName(int16_t)                 { return ""; }      // 14
    virtual int       GetFriendsGroupMembersCount(int16_t)           { return 0; }       // 15
    virtual void      GetFriendsGroupMembersList(int16_t, uint64_t*, int) {}              // 16
    virtual bool      HasFriend(uint64_t sid, int iFriendFlags) {
        if (sid == 0) return false;
        constexpr int kImmediate = 0x10;
        if ((iFriendFlags & kImmediate) == 0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (uint64_t f : pushed().friends) {
            if (f == sid) return true;
        }
        return false;
    }
    virtual int       GetClanCount()                                 { return 0; }       // 18
    virtual uint64_t  GetClanByIndex(int)                            { return 0; }       // 19
    virtual const char* GetClanName(uint64_t)                        { return ""; }      // 20
    virtual const char* GetClanTag(uint64_t)                         { return ""; }      // 21
    virtual bool      GetClanActivityCounts(uint64_t, int*, int*, int*) { return false; }// 22
    virtual uint64_t DownloadClanActivityCounts(uint64_t* /*clans*/, int /*n*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::DownloadClanActivityCountsResult cb{};
        cb.m_bSuccess = 0; // no real clan data
        push_call_result(h, lsc_cb::kDownloadClanActivityCountsResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual int       GetFriendCountFromSource(uint64_t)             { return 0; }       // 24
    virtual uint64_t  GetFriendFromSourceByIndex(uint64_t, int)      { return 0; }       // 25
    virtual bool      IsUserInSource(uint64_t, uint64_t)             { return false; }   // 26
    virtual void      SetInGameVoiceSpeaking(uint64_t, bool)         {}                  // 27
    static void enqueue_overlay(PushedState::OverlayRequest req) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto& q = pushed().overlay_request_queue;
        if (q.size() >= 32) q.pop_front();
        q.push_back(std::move(req));
    }
    virtual void      ActivateGameOverlay(const char* dialog) {                          // 28
        PushedState::OverlayRequest r;
        r.kind = "dialog";
        r.arg1 = dialog ? dialog : "";
        enqueue_overlay(std::move(r));
    }
    virtual void      ActivateGameOverlayToUser(const char* dialog, uint64_t sid) {       // 29
        PushedState::OverlayRequest r;
        r.kind = "user";
        r.arg1 = dialog ? dialog : "";
        r.sid  = sid;
        enqueue_overlay(std::move(r));
    }
    virtual void      ActivateGameOverlayToWebPage(const char* url, int /*mode*/) {       // 30
        if (!url || !*url) return;
        PushedState::OverlayRequest r;
        r.kind = "webpage";
        r.arg1 = url;
        enqueue_overlay(std::move(r));
    }
    virtual void      ActivateGameOverlayToStore(uint32_t appid, int /*flag*/) {          // 31
        PushedState::OverlayRequest r;
        r.kind   = "store";
        r.app_id = appid;
        enqueue_overlay(std::move(r));
    }
    virtual void      SetPlayedWith(uint64_t)                        {}                  // 32
    virtual void      ActivateGameOverlayInviteDialog(uint64_t lobby_sid) {                // 33
        PushedState::OverlayRequest r;
        r.kind = "invite";
        r.sid  = lobby_sid;
        enqueue_overlay(std::move(r));
    }
    virtual int GetSmallFriendAvatar(uint64_t steamID) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().friend_avatars.find(steamID);
        return (it == pushed().friend_avatars.end()) ? 0 : it->second.small;
    }
    virtual int GetMediumFriendAvatar(uint64_t steamID) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().friend_avatars.find(steamID);
        return (it == pushed().friend_avatars.end()) ? 0 : it->second.medium;
    }
    virtual int GetLargeFriendAvatar(uint64_t steamID) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().friend_avatars.find(steamID);
        return (it == pushed().friend_avatars.end()) ? 0 : it->second.large;
    }
    virtual bool RequestUserInformation(uint64_t steamID, bool bRequireNameOnly) {
        if (steamID == 0) return false;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().friend_persona_names.find(steamID);
            if (it != pushed().friend_persona_names.end() && !it->second.empty()) {
                return false;
            }
        }
        int32_t flags = bRequireNameOnly ? 0x01 : 0x47;  // PlayerName / std set
        wn_cm_request_user_info(steamID, flags);
        return true;
    }
    virtual uint64_t RequestClanOfficerList(uint64_t clanSid) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::ClanOfficerListResponse cb{};
        cb.m_steamIDClan = clanSid;
        cb.m_cOfficers   = 0;
        cb.m_bSuccess    = 0;
        push_call_result(h, lsc_cb::kClanOfficerListResponse,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t  GetClanOwner(uint64_t)                         { return 0; }       // 39
    virtual int       GetClanOfficerCount(uint64_t)                  { return 0; }
    virtual uint64_t  GetClanOfficerByIndex(uint64_t, int)           { return 0; }
    virtual uint32_t  GetUserRestrictions()                          { return 0; }
    virtual bool SetRichPresence(const char* pchKey, const char* pchValue) {
        if (!pchKey || !*pchKey) return false;
        uint64_t self = pushed().steam_id.load();
        if (self == 0) return false;
        std::vector<std::string> keys, values;
        uint32_t app_id;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto& rp = pushed().rich_presence[self];
            auto it = std::find_if(rp.begin(), rp.end(),
                [&](const auto& kv) { return kv.first == pchKey; });
            if (!pchValue || !*pchValue) {
                if (it != rp.end()) rp.erase(it);
            } else if (it == rp.end()) {
                rp.emplace_back(pchKey, pchValue);
            } else {
                it->second = pchValue;
            }
            keys.reserve(rp.size());
            values.reserve(rp.size());
            for (const auto& kv : rp) {
                keys.push_back(kv.first);
                values.push_back(kv.second);
            }
            app_id = pushed().app_id.load();
        }
        std::vector<const char*> ck(keys.size()), cv(values.size());
        for (size_t i = 0; i < keys.size(); ++i) {
            ck[i] = keys[i].c_str();
            cv[i] = values[i].c_str();
        }
        wn_cm_set_rich_presence(app_id,
                                 ck.empty() ? nullptr : ck.data(),
                                 cv.empty() ? nullptr : cv.data(),
                                 ck.size());
        lsc_cb::FriendRichPresenceUpdate ev{};
        ev.m_steamIDFriend = self;
        ev.m_nAppID        = app_id;
        push_callback(state().user.load(),
                      lsc_cb::kFriendRichPresenceUpdate,
                      &ev, sizeof(ev));
        return true;
    }
    virtual void ClearRichPresence() {
        uint64_t self = pushed().steam_id.load();
        if (self == 0) return;
        uint32_t app_id;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            pushed().rich_presence.erase(self);
            app_id = pushed().app_id.load();
        }
        wn_cm_set_rich_presence(app_id, nullptr, nullptr, 0);
        lsc_cb::FriendRichPresenceUpdate ev{};
        ev.m_steamIDFriend = self;
        ev.m_nAppID        = app_id;
        push_callback(state().user.load(),
                      lsc_cb::kFriendRichPresenceUpdate,
                      &ev, sizeof(ev));
    }
    virtual const char* GetFriendRichPresence(uint64_t steamID, const char* pchKey) {
        static thread_local std::string tls_rp;
        tls_rp.clear();
        if (!pchKey) return "";
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().rich_presence.find(steamID);
        if (it == pushed().rich_presence.end()) return "";
        auto kv = std::find_if(it->second.begin(), it->second.end(),
            [&](const auto& p) { return p.first == pchKey; });
        if (kv == it->second.end()) return "";
        tls_rp = kv->second;
        return tls_rp.c_str();
    }
    virtual int GetFriendRichPresenceKeyCount(uint64_t steamID) {
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().rich_presence.find(steamID);
        if (it == pushed().rich_presence.end()) return 0;
        return static_cast<int>(it->second.size());
    }
    virtual const char* GetFriendRichPresenceKeyByIndex(uint64_t steamID, int idx) {
        static thread_local std::string tls_key;
        tls_key.clear();
        if (idx < 0) return "";
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().rich_presence.find(steamID);
        if (it == pushed().rich_presence.end()) return "";
        if (static_cast<size_t>(idx) >= it->second.size()) return "";
        tls_key = it->second[idx].first;
        return tls_key.c_str();
    }
    virtual void RequestFriendRichPresence(uint64_t steamID) {
        if (steamID == 0) return;
        wn_cm_request_user_info(steamID, 0x800);  // RichPresence flag bit
        lsc_cb::FriendRichPresenceUpdate ev{};
        ev.m_steamIDFriend = steamID;
        ev.m_nAppID        = pushed().app_id.load();
        push_callback(state().user.load(),
                      lsc_cb::kFriendRichPresenceUpdate,
                      &ev, sizeof(ev));
    }
    virtual bool      InviteUserToGame(uint64_t, const char*)        { return false; }
    virtual int       GetCoplayFriendCount()                         { return 0; }
    virtual uint64_t  GetCoplayFriend(int)                           { return 0; }
    virtual int       GetFriendCoplayTime(uint64_t)                  { return 0; }
    virtual uint32_t  GetFriendCoplayGame(uint64_t)                  { return 0; }
    virtual uint64_t JoinClanChatRoom(uint64_t clanSid) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::JoinClanChatRoomCompletionResult cb{};
        cb.m_steamIDClanChat        = clanSid;
        cb.m_eChatRoomEnterResponse = 2; // k_EChatRoomEnterResponseError
        push_call_result(h, lsc_cb::kJoinClanChatRoomCompletion,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      LeaveClanChatRoom(uint64_t)                    { return false; }
    virtual int       GetClanChatMemberCount(uint64_t)               { return 0; }
    virtual uint64_t  GetChatMemberByIndex(uint64_t, int)            { return 0; }
    virtual bool      SendClanChatMessage(uint64_t, const char*)     { return false; }
    virtual int       GetClanChatMessage(uint64_t, int, void*, int, int*, uint64_t*) { return 0; }
    virtual bool      IsClanChatAdmin(uint64_t, uint64_t)            { return false; }
    virtual bool      IsClanChatWindowOpenInSteam(uint64_t)          { return false; }
    virtual bool      OpenClanChatWindowInSteam(uint64_t)            { return false; }
    virtual bool      CloseClanChatWindowInSteam(uint64_t)           { return false; }
    virtual bool      SetListenForFriendsMessages(bool)              { return false; }
    virtual bool      ReplyToFriendMessage(uint64_t, const char*)    { return false; }
    virtual int       GetFriendMessage(uint64_t, int, void*, int, int*) { return 0; }
    virtual uint64_t GetFollowerCount(uint64_t sid) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::FriendsGetFollowerCount cb{};
        cb.m_eResult = 2; // k_EResultFail
        cb.m_steamID = sid;
        cb.m_nCount  = 0;
        push_call_result(h, lsc_cb::kFriendsGetFollowerCount,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t IsFollowing(uint64_t sid) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::FriendsIsFollowing cb{};
        cb.m_eResult      = 2;
        cb.m_steamID      = sid;
        cb.m_bIsFollowing = 0;
        push_call_result(h, lsc_cb::kFriendsIsFollowing,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t EnumerateFollowingList(uint32_t /*unStartIndex*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::FriendsEnumerateFollowingList cb{};
        cb.m_eResult           = 2;
        cb.m_nResultsReturned  = 0;
        cb.m_nTotalResultCount = 0;
        push_call_result(h, lsc_cb::kFriendsEnumerateFollowingList,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      IsClanPublic(uint64_t)                         { return false; }
    virtual bool      IsClanOfficialGameGroup(uint64_t)              { return false; }
    virtual int       GetNumChatsWithUnreadPriorityMessages()        { return 0; }
    virtual void      ActivateGameOverlayRemotePlayTogetherInviteDialog(uint64_t) {}
    virtual bool      RegisterProtocolInOverlayBrowser(const char*)  { return false; }
    virtual void      ActivateGameOverlayInviteDialogConnectString(const char*) {}
    virtual uint64_t RequestEquippedProfileItems(uint64_t sid) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::EquippedProfileItems cb{};
        cb.m_eResult                    = 2; // k_EResultFail
        cb.m_steamID                    = sid;
        cb.m_bHasAnimatedAvatar         = 0;
        cb.m_bHasAvatarFrame            = 0;
        cb.m_bHasProfileModifier        = 0;
        cb.m_bHasProfileBackground      = 0;
        cb.m_bHasMiniProfileBackground  = 0;
        push_call_result(h, lsc_cb::kEquippedProfileItems,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      BHasEquippedProfileItem(uint64_t, int)         { return false; }
    virtual const char* GetProfileItemPropertyString(uint64_t, int, int) { return ""; }
    virtual uint32_t  GetProfileItemPropertyUint(uint64_t, int, int) { return 0; }
};

class ISteamRemoteStorageStub {
public:
    static std::string resolve_cloud_path(const char* pchFile) {
        if (!pchFile || !*pchFile) return {};
        for (const char* p = pchFile; *p; ++p) {
            if (*p == '\\') return {};
        }
        if (pchFile[0] == '/') return {};
        std::string fname(pchFile);
        if (fname.find("..") != std::string::npos) return {};
        uint32_t app = pushed().app_id.load();
        if (app == 0) return {};
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_cloud_remote_dirs.find(app);
        if (it == pushed().app_cloud_remote_dirs.end()) return {};
        std::string out = it->second;
        if (!out.empty() && out.back() != '/') out.push_back('/');
        out.append(fname);
        return out;
    }
    virtual bool FileWrite(const char* pchFile, const void* pvData, int cubData) {
        if (!pvData || cubData <= 0) return false;
        std::string path = resolve_cloud_path(pchFile);
        if (path.empty()) return false;
        size_t slash = path.find_last_of('/');
        if (slash != std::string::npos) {
            std::string dir = path.substr(0, slash);
            mkdir(dir.c_str(), 0755);
        }
        int fd = ::open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd < 0) return false;
        ssize_t total = 0;
        const char* p = static_cast<const char*>(pvData);
        while (total < cubData) {
            ssize_t n = ::write(fd, p + total, cubData - total);
            if (n < 0) {
                ::close(fd);
                ::unlink(path.c_str());
                return false;
            }
            total += n;
        }
        ::close(fd);
        std::lock_guard<std::mutex> lk(state_mutex());
        auto& files = pushed().cloud_files;
        std::string name(pchFile);
        bool patched = false;
        for (auto& f : files) {
            if (f.name == name) {
                f.size      = static_cast<int32_t>(cubData);
                f.timestamp = static_cast<int64_t>(::time(nullptr));
                patched = true;
                break;
            }
        }
        if (!patched) {
            wn_libsteamclient::PushedState::CloudFileEntry e;
            e.name      = std::move(name);
            e.size      = static_cast<int32_t>(cubData);
            e.timestamp = static_cast<int64_t>(::time(nullptr));
            files.push_back(std::move(e));
        }
        return true;
    }
    virtual int FileRead(const char* pchFile, void* pvData, int cubDataToRead) {
        if (!pvData || cubDataToRead <= 0) return 0;
        std::string path = resolve_cloud_path(pchFile);
        if (path.empty()) return 0;
        int fd = ::open(path.c_str(), O_RDONLY);
        if (fd < 0) return 0;
        ssize_t total = 0;
        char* p = static_cast<char*>(pvData);
        while (total < cubDataToRead) {
            ssize_t n = ::read(fd, p + total, cubDataToRead - total);
            if (n < 0) { ::close(fd); return 0; }
            if (n == 0) break;  // EOF
            total += n;
        }
        ::close(fd);
        return static_cast<int>(total);
    }
    virtual uint64_t FileWriteAsync(const char* pchFile, const void* pvData, uint32_t cubData) {
        if (!pvData || cubData == 0) return 0;
        std::string path = resolve_cloud_path(pchFile);
        if (path.empty()) return 0;
        bool ok = FileWrite(pchFile, pvData, static_cast<int>(cubData));
        uint64_t hCall = alloc_api_call_handle();
        wn_libsteamclient::callbacks::RemoteStorageFileWriteAsyncComplete cb{};
        cb.m_eResult = ok ? 1 /*k_EResultOK*/ : 2 /*k_EResultFail*/;
        push_call_result(hCall,
                         lsc_cb::kRemoteStorageFileWriteAsyncComplete,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual uint64_t FileReadAsync(const char* pchFile, uint32_t nOffset, uint32_t cubToRead) {
        if (cubToRead == 0) return 0;
        std::string path = resolve_cloud_path(pchFile);
        if (path.empty()) return 0;
        int fd = ::open(path.c_str(), O_RDONLY);
        if (fd < 0) return 0;
        if (nOffset > 0 && ::lseek(fd, nOffset, SEEK_SET) == (off_t)-1) {
            ::close(fd);
            return 0;
        }
        std::vector<uint8_t> buf(cubToRead);
        ssize_t total = 0;
        while (total < (ssize_t)cubToRead) {
            ssize_t n = ::read(fd, buf.data() + total, cubToRead - total);
            if (n < 0) { ::close(fd); return 0; }
            if (n == 0) break;
            total += n;
        }
        ::close(fd);
        buf.resize(total);
        uint64_t hCall = alloc_api_call_handle();
        {
            std::lock_guard<std::mutex> lk(async_read_mu());
            async_read_buffers()[hCall] = std::move(buf);
        }
        wn_libsteamclient::callbacks::RemoteStorageFileReadAsyncComplete cb{};
        cb.m_hFileReadAsync = hCall;
        cb.m_eResult        = 1 /*k_EResultOK*/;
        cb.m_nOffset        = nOffset;
        cb.m_cubRead        = static_cast<uint32_t>(total);
        push_call_result(hCall,
                         lsc_cb::kRemoteStorageFileReadAsyncComplete,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual bool FileReadAsyncComplete(uint64_t hCall, void* pvBuffer, uint32_t cubToRead) {
        if (!pvBuffer || cubToRead == 0 || hCall == 0) return false;
        std::lock_guard<std::mutex> lk(async_read_mu());
        auto& m = async_read_buffers();
        auto it = m.find(hCall);
        if (it == m.end()) return false;
        const auto& buf = it->second;
        if (cubToRead < buf.size()) {
            return false;
        }
        std::memcpy(pvBuffer, buf.data(), buf.size());
        m.erase(it);
        return true;
    }
    virtual bool FileForget(const char* pchFile) {
        if (!pchFile || !*pchFile) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto& files = pushed().cloud_files;
        std::string name(pchFile);
        bool found = false;
        for (auto it = files.begin(); it != files.end(); ) {
            if (it->name == name) { it = files.erase(it); found = true; }
            else ++it;
        }
        return found;
    }
    virtual bool FileDelete(const char* pchFile) {
        std::string path = resolve_cloud_path(pchFile);
        if (path.empty()) return false;
        int rc = ::unlink(path.c_str());
        std::lock_guard<std::mutex> lk(state_mutex());
        auto& files = pushed().cloud_files;
        std::string name(pchFile);
        for (auto it = files.begin(); it != files.end(); ) {
            if (it->name == name) it = files.erase(it);
            else ++it;
        }
        return rc == 0;
    }
    virtual uint64_t FileShare(const char* pchFile) {
        if (!pchFile || !*pchFile) return 0;
        uint64_t hCall = alloc_api_call_handle();
        lsc_cb::RemoteStorageFileShareResult cb{};
        std::strncpy(cb.m_rgchFilename, pchFile, sizeof(cb.m_rgchFilename) - 1);
        bool found = false;
        int32_t size = 0;
        int64_t ts   = 0;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            for (const auto& f : pushed().cloud_files) {
                if (f.name == pchFile) {
                    found = true;
                    size  = f.size;
                    ts    = f.timestamp;
                    break;
                }
            }
        }
        if (!found) {
            cb.m_eResult = 9; // k_EResultFileNotFound
            cb.m_hFile   = 0;
        } else {
            cb.m_eResult = 1; // k_EResultOK
            uint64_t h = 0xcbf29ce484222325ULL; // FNV-1a 64 seed
            auto mix = [&](const void* d, size_t n) {
                const uint8_t* p = static_cast<const uint8_t*>(d);
                for (size_t i = 0; i < n; ++i) {
                    h ^= p[i];
                    h *= 0x100000001b3ULL;
                }
            };
            uint32_t app = pushed().app_id.load();
            mix(&app,  sizeof(app));
            mix(pchFile, std::strlen(pchFile));
            mix(&size, sizeof(size));
            mix(&ts,   sizeof(ts));
            cb.m_hFile = h | (1ULL << 63);  // set hi bit so it never collides with hCall
        }
        push_call_result(hCall, lsc_cb::kRemoteStorageFileShareResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return hCall;
    }
    virtual bool      SetSyncPlatforms(const char* pchFile, int /*ePlatform*/) {
        if (!pchFile || !*pchFile) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (const auto& f : pushed().cloud_files) {
            if (f.name == pchFile) return true;
        }
        return false;
    }
    virtual uint64_t FileWriteStreamOpen(const char* pchFile) {
        std::string finalPath = resolve_cloud_path(pchFile);
        if (finalPath.empty()) return 0;
        size_t slash = finalPath.find_last_of('/');
        if (slash != std::string::npos) {
            mkdir(finalPath.substr(0, slash).c_str(), 0755);
        }
        uint64_t h = alloc_api_call_handle();
        char suffix[64];
        std::snprintf(suffix, sizeof(suffix), ".wnstream-%llu",
                      static_cast<unsigned long long>(h));
        std::string tempPath = finalPath + suffix;
        int fd = ::open(tempPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd < 0) return 0;
        std::lock_guard<std::mutex> lk(stream_mu());
        StreamSlot s;
        s.fd        = fd;
        s.tempPath  = std::move(tempPath);
        s.finalPath = std::move(finalPath);
        s.name      = std::string(pchFile);
        s.bytes     = 0;
        streams()[h] = std::move(s);
        return h;
    }
    virtual bool FileWriteStreamWriteChunk(uint64_t h, const void* pvData, int cubData) {
        if (!pvData || cubData <= 0) return false;
        std::lock_guard<std::mutex> lk(stream_mu());
        auto it = streams().find(h);
        if (it == streams().end()) return false;
        const char* p = static_cast<const char*>(pvData);
        int total = 0;
        while (total < cubData) {
            ssize_t n = ::write(it->second.fd, p + total, cubData - total);
            if (n < 0) {
                return false;
            }
            total += n;
        }
        it->second.bytes += total;
        return true;
    }
    virtual bool FileWriteStreamClose(uint64_t h) {
        StreamSlot slot;
        {
            std::lock_guard<std::mutex> lk(stream_mu());
            auto it = streams().find(h);
            if (it == streams().end()) return false;
            slot = std::move(it->second);
            streams().erase(it);
        }
        ::fsync(slot.fd);
        ::close(slot.fd);
        if (::rename(slot.tempPath.c_str(), slot.finalPath.c_str()) != 0) {
            ::unlink(slot.tempPath.c_str());
            return false;
        }
        std::lock_guard<std::mutex> lk(state_mutex());
        auto& files = pushed().cloud_files;
        bool patched = false;
        for (auto& f : files) {
            if (f.name == slot.name) {
                f.size      = static_cast<int32_t>(slot.bytes);
                f.timestamp = static_cast<int64_t>(::time(nullptr));
                patched = true;
                break;
            }
        }
        if (!patched) {
            wn_libsteamclient::PushedState::CloudFileEntry e;
            e.name      = slot.name;
            e.size      = static_cast<int32_t>(slot.bytes);
            e.timestamp = static_cast<int64_t>(::time(nullptr));
            files.push_back(std::move(e));
        }
        return true;
    }
    virtual bool FileWriteStreamCancel(uint64_t h) {
        std::lock_guard<std::mutex> lk(stream_mu());
        auto it = streams().find(h);
        if (it == streams().end()) return false;
        ::close(it->second.fd);
        ::unlink(it->second.tempPath.c_str());
        streams().erase(it);
        return true;
    }

    virtual bool      FileExists(const char* pchFile) {
        if (!pchFile || !*pchFile) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (const auto& f : pushed().cloud_files) {
            if (f.name == pchFile) return true;
        }
        return false;
    }
    virtual bool FilePersisted(const char* pchFile) {
        if (!pchFile || !*pchFile) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (const auto& f : pushed().cloud_files) {
            if (f.name == pchFile) return true;
        }
        return false;
    }

    virtual int       GetFileSize(const char* pchFile) {
        if (!pchFile || !*pchFile) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (const auto& f : pushed().cloud_files) {
            if (f.name == pchFile) return static_cast<int>(f.size);
        }
        return 0;
    }
    virtual int64_t   GetFileTimestamp(const char* pchFile) {
        if (!pchFile || !*pchFile) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (const auto& f : pushed().cloud_files) {
            if (f.name == pchFile) return f.timestamp;
        }
        return 0;
    }
    virtual int       GetSyncPlatforms(const char* pchFile) {
        if (!pchFile || !*pchFile) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        for (const auto& f : pushed().cloud_files) {
            if (f.name == pchFile) return -1; // k_ERemoteStoragePlatformAll
        }
        return 0;
    }

    virtual int       GetFileCount() {
        std::lock_guard<std::mutex> lk(state_mutex());
        return static_cast<int>(pushed().cloud_files.size());
    }
    virtual const char* GetFileNameAndSize(int iFile, int32_t* pnFileSizeInBytes) {
        static thread_local std::string tls_name;
        tls_name.clear();
        int32_t size = 0;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            const auto& files = pushed().cloud_files;
            if (iFile >= 0 && static_cast<size_t>(iFile) < files.size()) {
                tls_name = files[iFile].name;
                size = files[iFile].size;
            }
        }
        if (pnFileSizeInBytes) *pnFileSizeInBytes = size;
        return tls_name.c_str();
    }
    virtual void      GetQuota(uint64_t* total, uint64_t* avail) {
        if (total) *total = pushed().cloud_quota_total.load();
        if (avail) *avail = pushed().cloud_quota_available.load();
    }
    virtual bool      IsCloudEnabledForAccount() {
        return pushed().cloud_enabled_account.load();
    }
    virtual bool      IsCloudEnabledForApp() {
        return pushed().cloud_enabled_app.load();
    }
    virtual void      SetCloudEnabledForApp(bool enabled) {
        pushed().cloud_enabled_app.store(enabled);
    }
    virtual uint64_t UGCDownload(uint64_t hContent, uint32_t /*priority*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::RemoteStorageDownloadUGCResult cb{};
        cb.m_eResult        = 2; // k_EResultFail — no UGC backend
        cb.m_hFile          = hContent;
        cb.m_nAppID         = pushed().app_id.load();
        cb.m_nSizeInBytes   = 0;
        cb.m_pchFileName[0] = '\0';
        cb.m_ulSteamIDOwner = 0;
        push_call_result(h, lsc_cb::kRemoteStorageDownloadUGC,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      GetUGCDownloadProgress(uint64_t, int32_t* d, int32_t* e) {
        if (d) *d = 0; if (e) *e = 0; return false;
    }
    virtual bool      GetUGCDetails(uint64_t /*content*/, uint32_t* appID,
                                     char** ppchName, int32_t* pcbFile, uint64_t* steamIDOwner) {
        if (appID) *appID = 0;
        if (ppchName) *ppchName = nullptr;
        if (pcbFile) *pcbFile = 0;
        if (steamIDOwner) *steamIDOwner = 0;
        return false;
    }
    virtual int32_t   UGCRead(uint64_t /*content*/, void* /*buf*/, int32_t /*cubData*/,
                               uint32_t /*offset*/, int /*action*/) { return 0; }
    virtual int32_t   GetCachedUGCCount()                            { return 0; }
    virtual uint64_t  GetCachedUGCHandle(int32_t /*idx*/)            { return 0; }
    virtual uint64_t  PublishWorkshopFile_DEPRECATED(const char*, const char*, uint32_t, const char*, const char*, int, void*, void*, int) { return 0; }
    virtual uint64_t  CreatePublishedFileUpdateRequest_DEPRECATED(uint64_t) { return 0; }
    virtual bool      UpdatePublishedFileFile_DEPRECATED(uint64_t, const char*)            { return false; }
    virtual bool      UpdatePublishedFilePreviewFile_DEPRECATED(uint64_t, const char*)     { return false; }
    virtual bool      UpdatePublishedFileTitle_DEPRECATED(uint64_t, const char*)           { return false; }
    virtual bool      UpdatePublishedFileDescription_DEPRECATED(uint64_t, const char*)     { return false; }
    virtual bool      UpdatePublishedFileVisibility_DEPRECATED(uint64_t, int)              { return false; }
    virtual bool      UpdatePublishedFileTags_DEPRECATED(uint64_t, void*)                  { return false; }
    virtual uint64_t  CommitPublishedFileUpdate_DEPRECATED(uint64_t)                       { return 0; }
    virtual uint64_t  GetPublishedFileDetails_DEPRECATED(uint64_t, uint32_t)               { return 0; }
    virtual uint64_t  DeletePublishedFile_DEPRECATED(uint64_t)                             { return 0; }
    virtual uint64_t  EnumerateUserPublishedFiles_DEPRECATED(uint32_t)                     { return 0; }
    virtual uint64_t  SubscribePublishedFile_DEPRECATED(uint64_t)                          { return 0; }
    virtual uint64_t  EnumerateUserSubscribedFiles_DEPRECATED(uint32_t)                    { return 0; }
    virtual uint64_t  UnsubscribePublishedFile_DEPRECATED(uint64_t)                        { return 0; }
    virtual bool      UpdatePublishedFileSetChangeDescription_DEPRECATED(uint64_t, const char*) { return false; }
    virtual uint64_t  GetPublishedItemVoteDetails_DEPRECATED(uint64_t)                     { return 0; }
    virtual uint64_t  UpdateUserPublishedItemVote_DEPRECATED(uint64_t, bool)               { return 0; }
    virtual uint64_t  GetUserPublishedItemVoteDetails_DEPRECATED(uint64_t)                 { return 0; }
    virtual uint64_t  EnumerateUserSharedWorkshopFiles_DEPRECATED(uint64_t, uint32_t, void*, void*) { return 0; }
    virtual uint64_t  PublishVideo_DEPRECATED(int, const char*, uint32_t, const char*, const char*, uint32_t, void*) { return 0; }
    virtual uint64_t  SetUserPublishedFileAction_DEPRECATED(uint64_t, int)                 { return 0; }
    virtual uint64_t  EnumeratePublishedFilesByUserAction_DEPRECATED(int, uint32_t)        { return 0; }
    virtual uint64_t  EnumeratePublishedWorkshopFiles_DEPRECATED(int, uint32_t, uint32_t, uint32_t, void*, void*) { return 0; }
    virtual uint64_t UGCDownloadToLocation(uint64_t hContent, const char* /*location*/, uint32_t /*priority*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::RemoteStorageDownloadUGCResult cb{};
        cb.m_eResult        = 2;
        cb.m_hFile          = hContent;
        cb.m_nAppID         = pushed().app_id.load();
        cb.m_nSizeInBytes   = 0;
        cb.m_pchFileName[0] = '\0';
        cb.m_ulSteamIDOwner = 0;
        push_call_result(h, lsc_cb::kRemoteStorageDownloadUGC,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual int32_t   GetLocalFileChangeCount()                                            { return 0; }
    virtual const char* GetLocalFileChange(int32_t /*idx*/, int* peChangeType, int* pePathType) {
        if (peChangeType) *peChangeType = 0;
        if (pePathType)   *pePathType   = 0;
        return "";
    }
    virtual bool      BeginFileWriteBatch()                                                { return true; }
    virtual bool      EndFileWriteBatch()                                                  { return true; }
};

class ISteamUserStatsStub {
public:
    virtual bool RequestCurrentStats() {
        if (!state().logged_on.load()) return false;
        if (pushed().stats_ready.load()) {
            lsc_cb::UserStatsReceived payload{};
            payload.m_nGameID     = static_cast<uint64_t>(pushed().app_id.load());
            payload.m_eResult     = 1;  // k_EResultOK
            payload.m_steamIDUser = pushed().steam_id.load();
            push_callback(state().user.load(),
                          lsc_cb::kUserStatsReceived,
                          &payload, sizeof(payload));
        }
        return true;
    }
    virtual bool GetStatInt(const char* pchName, int32_t* pData) {
        if (!pchName || !pData) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().stats_int.find(pchName);
        if (it == pushed().stats_int.end()) return false;
        return true;
    }
    virtual bool GetStatFloat(const char* pchName, float* pData) {
        if (!pchName || !pData) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().stats_float.find(pchName);
        if (it == pushed().stats_float.end()) return false;
        return true;
    }
    virtual bool SetStatInt(const char* pchName, int32_t nData) {
        if (!pchName) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        pushed().stats_int[pchName] = nData;
        pushed().dirty_stats_int.insert(pchName);
        return true;
    }
    virtual bool SetStatFloat(const char* pchName, float fData) {
        if (!pchName) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        pushed().stats_float[pchName] = fData;
        pushed().dirty_stats_float.insert(pchName);
        return true;
    }
    virtual bool UpdateAvgRateStat(const char* pchName,
                                    float flCountThisSession,
                                    double dSessionLength) {
        if (!pchName || dSessionLength <= 0.0) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto& acc = pushed().stats_avg_rate[pchName];
        acc.total_count += static_cast<double>(flCountThisSession);
        acc.total_time  += dSessionLength;
        if (acc.total_time > 0.0) {
            pushed().stats_float[pchName] =
                static_cast<float>(acc.total_count / acc.total_time);
        }
        pushed().dirty_stats_float.insert(pchName);
        return true;
    }
    virtual bool GetAchievement(const char* pchName, bool* pbAchieved) {
        if (!pchName || !pbAchieved) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().achievement_index.find(pchName);
        if (it == pushed().achievement_index.end()) return false;
        return true;
    }
    virtual bool SetAchievement(const char* pchName) {
        if (!pchName) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().achievement_index.find(pchName);
        if (it == pushed().achievement_index.end()) return false;
        auto& a = pushed().achievements[it->second];
        if (!a.achieved) {
            a.achieved      = true;
            a.unlock_time   = static_cast<uint32_t>(::time(nullptr));
            a.pending_store = true;
        }
        return true;
    }
    virtual bool ClearAchievement(const char* pchName) {
        if (!pchName) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().achievement_index.find(pchName);
        if (it == pushed().achievement_index.end()) return false;
        auto& a = pushed().achievements[it->second];
        bool was = a.achieved;
        a.achieved      = false;
        a.unlock_time   = 0;
        if (was) a.pending_store = true;
        return true;
    }
    virtual bool GetAchievementAndUnlockTime(const char* pchName,
                                             bool* pbAchieved,
                                             uint32_t* punlockTime) {
        if (!pchName) return false;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().achievement_index.find(pchName);
        if (it == pushed().achievement_index.end()) return false;
        const auto& a = pushed().achievements[it->second];
        if (pbAchieved)   *pbAchieved   = a.achieved;
        if (punlockTime)  *punlockTime  = a.unlock_time;
        return true;
    }
    virtual bool StoreStats() {
        bool ready = pushed().stats_ready.load();
        uint32_t app_id = pushed().app_id.load();
        uint64_t game_id = static_cast<uint64_t>(app_id);

        struct DirtyAch { std::string name; int32_t block_id; int32_t bit_index; bool achieved; };
        std::vector<DirtyAch> dirty;
        std::unordered_map<std::string, int32_t> stats_int_snapshot;
        std::vector<std::tuple<uint32_t, uint32_t>> dirty_stat_uploads;
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            for (auto& a : pushed().achievements) {
                if (a.pending_store) {
                    dirty.push_back(DirtyAch{a.api_name, a.block_id,
                                              a.bit_index, a.achieved});
                    a.pending_store = false;
                }
            }
            stats_int_snapshot = pushed().stats_int;
            for (const auto& name : pushed().dirty_stats_int) {
                auto idIt = pushed().stat_name_to_id.find(name);
                if (idIt == pushed().stat_name_to_id.end()) continue;
                auto vIt = pushed().stats_int.find(name);
                uint32_t v = (vIt != pushed().stats_int.end())
                             ? static_cast<uint32_t>(vIt->second) : 0u;
                dirty_stat_uploads.emplace_back(idIt->second, v);
            }
            for (const auto& name : pushed().dirty_stats_float) {
                auto idIt = pushed().stat_name_to_id.find(name);
                if (idIt == pushed().stat_name_to_id.end()) continue;
                auto vIt = pushed().stats_float.find(name);
                uint32_t bits = 0;
                if (vIt != pushed().stats_float.end()) {
                    float f = vIt->second;
                    std::memcpy(&bits, &f, sizeof(bits));
                }
                dirty_stat_uploads.emplace_back(idIt->second, bits);
            }
            pushed().dirty_stats_int.clear();
            pushed().dirty_stats_float.clear();
        }

        for (const auto& d : dirty) {
            lsc_cb::UserAchievementStored ach{};
            ach.m_nGameID           = game_id;
            ach.m_bGroupAchievement = false;
            std::strncpy(ach.m_rgchAchievementName,
                         d.name.c_str(),
                         lsc_cb::kAchievementNameMax - 1);
            ach.m_rgchAchievementName[lsc_cb::kAchievementNameMax - 1] = '\0';
            ach.m_nCurProgress = 0;
            ach.m_nMaxProgress = 0;
            push_callback(state().user.load(),
                          lsc_cb::kUserAchievementStored,
                          &ach, sizeof(ach));
        }

        if (app_id != 0 && (!dirty.empty() || !dirty_stat_uploads.empty())) {
            std::unordered_map<uint32_t, uint32_t> stat_id_to_value;
            for (const auto& [id, v] : dirty_stat_uploads) {
                stat_id_to_value[id] = v;
            }
            for (const auto& d : dirty) {
                if (d.block_id < 0) continue;
                uint32_t stat_id = static_cast<uint32_t>(d.block_id);
                auto it = stat_id_to_value.find(stat_id);
                if (it == stat_id_to_value.end()) {
                    char key[16];
                    std::snprintf(key, sizeof(key), "%u", stat_id);
                    auto sit = stats_int_snapshot.find(key);
                    uint32_t cur = (sit != stats_int_snapshot.end())
                                   ? static_cast<uint32_t>(sit->second)
                                   : 0u;
                    it = stat_id_to_value.emplace(stat_id, cur).first;
                }
                if (d.bit_index >= 0 && d.bit_index < 32) {
                    uint32_t mask = 1u << static_cast<uint32_t>(d.bit_index);
                    if (d.achieved) it->second |= mask;
                    else            it->second &= ~mask;
                }
            }
            if (!stat_id_to_value.empty()) {
                std::vector<uint32_t> ids;
                std::vector<uint32_t> vals;
                ids.reserve(stat_id_to_value.size());
                vals.reserve(stat_id_to_value.size());
                for (auto& [k, v] : stat_id_to_value) {
                    ids.push_back(k);
                    vals.push_back(v);
                }
                wn_cm_store_user_stats(app_id, /*crc_stats=*/0,
                                       ids.data(), vals.data(),
                                       ids.size());
                __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
                    "StoreStats: pushed %zu stat(s) to Steam (app=%u, "
                    "ach_dirty=%zu, stat_dirty=%zu)",
                    ids.size(), app_id, dirty.size(), dirty_stat_uploads.size());
            }
        }

        lsc_cb::UserStatsStored payload{};
        payload.m_nGameID = game_id;
        payload.m_eResult = ready ? 1 : 2;
        push_callback(state().user.load(),
                      lsc_cb::kUserStatsStored,
                      &payload, sizeof(payload));
        return ready;
    }
    virtual int GetAchievementIcon(const char* pchName) {
        if (!pchName) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().achievement_index.find(pchName);
        if (it == pushed().achievement_index.end()) return 0;
        return pushed().achievements[it->second].icon_handle;
    }
    virtual const char* GetAchievementDisplayAttribute(const char* pchName,
                                                       const char* pchKey) {
        static thread_local std::string tls_attr;
        tls_attr.clear();
        if (!pchName || !pchKey) return "";
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().achievement_index.find(pchName);
        if (it == pushed().achievement_index.end()) return "";
        const auto& a = pushed().achievements[it->second];

        auto pick_locale = [&](const std::unordered_map<std::string, std::string>& m)
                -> const std::string& {
            static const std::string kEmpty;
            const std::string& ui = pushed().ui_language;
            if (!ui.empty()) {
                auto h = m.find(ui);
                if (h != m.end() && !h->second.empty()) return h->second;
            }
            auto h = m.find("english");
            if (h != m.end() && !h->second.empty()) return h->second;
            for (const auto& kv : m) {
                if (!kv.second.empty()) return kv.second;
            }
            return kEmpty;
        };

        if (std::strcmp(pchKey, "name") == 0)         tls_attr = pick_locale(a.display_names);
        else if (std::strcmp(pchKey, "desc") == 0)    tls_attr = pick_locale(a.descriptions);
        else if (std::strcmp(pchKey, "hidden") == 0)  tls_attr = a.hidden ? "1" : "0";
        return tls_attr.c_str();
    }
    virtual bool IndicateAchievementProgress(const char* pchName,
                                             uint32_t nCurProgress,
                                             uint32_t nMaxProgress) {
        if (!pchName) return false;
        uint64_t game_id = static_cast<uint64_t>(pushed().app_id.load());
        {
            std::lock_guard<std::mutex> lk(state_mutex());
            auto it = pushed().achievement_index.find(pchName);
            if (it == pushed().achievement_index.end()) return false;
            if (pushed().achievements[it->second].achieved) return false;
        }
        lsc_cb::UserAchievementStored ach{};
        ach.m_nGameID           = game_id;
        ach.m_bGroupAchievement = false;
        std::strncpy(ach.m_rgchAchievementName, pchName,
                     lsc_cb::kAchievementNameMax - 1);
        ach.m_rgchAchievementName[lsc_cb::kAchievementNameMax - 1] = '\0';
        ach.m_nCurProgress = nCurProgress;
        ach.m_nMaxProgress = nMaxProgress;
        push_callback(state().user.load(),
                      lsc_cb::kUserAchievementStored,
                      &ach, sizeof(ach));
        return true;
    }
    virtual uint32_t GetNumAchievements() {
        std::lock_guard<std::mutex> lk(state_mutex());
        return static_cast<uint32_t>(pushed().achievements.size());
    }
    virtual const char* GetAchievementName(uint32_t idx) {
        static thread_local std::string tls_name;
        tls_name.clear();
        std::lock_guard<std::mutex> lk(state_mutex());
        const auto& a = pushed().achievements;
        if (idx < a.size()) tls_name = a[idx].api_name;
        return tls_name.c_str();
    }
    virtual uint64_t RequestUserStats(uint64_t steamID) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::UserStatsReceived payload{};
        payload.m_nGameID     = static_cast<uint64_t>(pushed().app_id.load());
        payload.m_eResult     = 6;  // k_EResultNoConnection — we never asked
        payload.m_steamIDUser = steamID;
        push_call_result(h, lsc_cb::kUserStatsReceived,
                         &payload, sizeof(payload), /*io_failure=*/false);
        return h;
    }
    virtual bool GetUserStatInt(uint64_t steamID, const char* pchName, int32_t* pData) {
        if (!pchName || !pData) return false;
        if (steamID != pushed().steam_id.load()) return false;
        return GetStatInt(pchName, pData);
    }
    virtual bool GetUserStatFloat(uint64_t steamID, const char* pchName, float* pData) {
        if (!pchName || !pData) return false;
        if (steamID != pushed().steam_id.load()) return false;
        return GetStatFloat(pchName, pData);
    }
    virtual bool GetUserAchievement(uint64_t steamID, const char* pchName, bool* pbAchieved) {
        if (steamID != pushed().steam_id.load()) return false;
        return GetAchievement(pchName, pbAchieved);
    }
    virtual bool GetUserAchievementAndUnlockTime(uint64_t steamID, const char* pchName,
                                                  bool* pbAchieved, uint32_t* punlockTime) {
        if (steamID != pushed().steam_id.load()) return false;
        return GetAchievementAndUnlockTime(pchName, pbAchieved, punlockTime);
    }
    virtual bool ResetAllStats(bool bAchievementsToo) {
        std::lock_guard<std::mutex> lk(state_mutex());
        for (auto& [name, _] : pushed().stats_int) {
            pushed().dirty_stats_int.insert(name);
        }
        pushed().stats_int.clear();
        for (auto& [name, _] : pushed().stats_float) {
            pushed().dirty_stats_float.insert(name);
        }
        pushed().stats_float.clear();
        if (bAchievementsToo) {
            for (auto& a : pushed().achievements) {
                bool was = a.achieved;
                a.achieved    = false;
                a.unlock_time = 0;
                if (was) a.pending_store = true;
            }
        }
        return true;
    }
    virtual uint64_t FindOrCreateLeaderboard(const char* /*name*/,
                                              int /*sortMethod*/, int /*displayType*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::LeaderboardFindResult cb{};
        cb.m_hSteamLeaderboard  = 0;
        cb.m_bLeaderboardFound  = 0;
        push_call_result(h, lsc_cb::kLeaderboardFindResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t FindLeaderboard(const char* /*name*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::LeaderboardFindResult cb{};
        cb.m_hSteamLeaderboard  = 0;
        cb.m_bLeaderboardFound  = 0;
        push_call_result(h, lsc_cb::kLeaderboardFindResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual const char* GetLeaderboardName(uint64_t)                 { return ""; }
    virtual int       GetLeaderboardEntryCount(uint64_t)             { return 0; }
    virtual int       GetLeaderboardSortMethod(uint64_t)             { return 0; }
    virtual int       GetLeaderboardDisplayType(uint64_t)            { return 0; }
    virtual uint64_t DownloadLeaderboardEntries(uint64_t hLeaderboard,
                                                  int /*eRange*/, int /*rangeStart*/,
                                                  int /*rangeEnd*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::LeaderboardScoresDownloaded cb{};
        cb.m_hSteamLeaderboard        = hLeaderboard;
        cb.m_hSteamLeaderboardEntries = 0;
        cb.m_cEntryCount              = 0;
        push_call_result(h, lsc_cb::kLeaderboardScoresDownloaded,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t DownloadLeaderboardEntriesForUsers(uint64_t hLeaderboard,
                                                          uint64_t* /*pUsers*/,
                                                          int /*cUsers*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::LeaderboardScoresDownloaded cb{};
        cb.m_hSteamLeaderboard        = hLeaderboard;
        cb.m_hSteamLeaderboardEntries = 0;
        cb.m_cEntryCount              = 0;
        push_call_result(h, lsc_cb::kLeaderboardScoresDownloaded,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      GetDownloadedLeaderboardEntry(uint64_t, int, void*, int32_t*, int) { return false; }
    virtual uint64_t UploadLeaderboardScore(uint64_t hLeaderboard,
                                             int /*method*/, int32_t score,
                                             const int32_t* /*details*/,
                                             int /*cDetails*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::LeaderboardScoreUploaded cb{};
        cb.m_bSuccess            = 0; // server-side store not implemented
        cb.m_hSteamLeaderboard   = hLeaderboard;
        cb.m_nScore              = score;
        cb.m_bScoreChanged       = 0;
        cb.m_nGlobalRankNew      = 0;
        cb.m_nGlobalRankPrevious = 0;
        push_call_result(h, lsc_cb::kLeaderboardScoreUploaded,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t AttachLeaderboardUGC(uint64_t hLeaderboard, uint64_t /*hUGC*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::LeaderboardUGCSet cb{};
        cb.m_eResult           = 2; // k_EResultFail
        cb.m_hSteamLeaderboard = hLeaderboard;
        push_call_result(h, lsc_cb::kLeaderboardUGCSet,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t GetNumberOfCurrentPlayers() {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::NumberOfCurrentPlayers cb{};
        cb.m_bSuccess = 1;
        cb.m_cPlayers = 0;
        push_call_result(h, lsc_cb::kNumberOfCurrentPlayers,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t RequestGlobalAchievementPercentages() {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::GlobalAchievementPercentagesReady cb{};
        cb.m_nGameID = static_cast<uint64_t>(pushed().app_id.load());
        cb.m_eResult = 2; // k_EResultFail
        push_call_result(h, lsc_cb::kGlobalAchievementPercentages,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual int       GetMostAchievedAchievementInfo(char*, uint32_t, float*, bool*) { return -1; }
    virtual int       GetNextMostAchievedAchievementInfo(int, char*, uint32_t, float*, bool*) { return -1; }
    virtual bool      GetAchievementAchievedPercent(const char*, float* p) {
        if (p) *p = 0.0f;
        return false;
    }
    virtual uint64_t RequestGlobalStats(int /*historicalDays*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::GlobalStatsReceived cb{};
        cb.m_nGameID = static_cast<uint64_t>(pushed().app_id.load());
        cb.m_eResult = 2; // k_EResultFail
        push_call_result(h, lsc_cb::kGlobalStatsReceived,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      GetGlobalStatInt64(const char*, int64_t* p)    { if (p) *p = 0; return false; }
    virtual bool      GetGlobalStatDouble(const char*, double* p)    { if (p) *p = 0.0; return false; }
    virtual int       GetGlobalStatHistoryInt64(const char*, int64_t*, uint32_t) { return 0; }
    virtual int       GetGlobalStatHistoryDouble(const char*, double*, uint32_t) { return 0; }
};

class ISteamInventoryStub {
public:
    virtual int       GetResultStatus(int /*resultHandle*/)         { return 8; /*InvalidParam*/ }
    virtual bool      GetResultItems(int, void*, uint32_t* pcb)     { if (pcb) *pcb = 0; return false; }
    virtual bool      GetResultItemProperty(int, uint32_t, const char*, char* buf, uint32_t* cb) {
        if (buf && cb && *cb > 0) buf[0] = '\0';
        if (cb) *cb = 0;
        return false;
    }
    virtual uint32_t  GetResultTimestamp(int)                       { return 0; }
    virtual bool      CheckResultSteamID(int, uint64_t)             { return false; }
    virtual void      DestroyResult(int)                            {}
    virtual bool      GetAllItems(int* phRes)                       { if (phRes) *phRes = -1; return false; }
    virtual bool      GetItemsByID(int* phRes, const uint64_t*, uint32_t) { if (phRes) *phRes = -1; return false; }
    virtual bool      SerializeResult(int, void*, uint32_t* pcb)    { if (pcb) *pcb = 0; return false; }
    virtual bool      DeserializeResult(int* phRes, const void*, uint32_t, bool) { if (phRes) *phRes = -1; return false; }
    virtual bool      GenerateItems(int* phRes, const int32_t*, const uint32_t*, uint32_t) { if (phRes) *phRes = -1; return false; }
    virtual bool      GrantPromoItems(int* phRes)                   { if (phRes) *phRes = -1; return false; }
    virtual bool      AddPromoItem(int* phRes, int32_t)             { if (phRes) *phRes = -1; return false; }
    virtual bool      AddPromoItems(int* phRes, const int32_t*, uint32_t) { if (phRes) *phRes = -1; return false; }
    virtual bool      ConsumeItem(int* phRes, uint64_t, uint32_t)   { if (phRes) *phRes = -1; return false; }
    virtual bool      ExchangeItems(int* phRes, const int32_t*, const uint32_t*, uint32_t,
                                     const uint64_t*, const uint32_t*, uint32_t) {
        if (phRes) *phRes = -1; return false;
    }
    virtual bool      TransferItemQuantity(int* phRes, uint64_t, uint32_t, uint64_t) { if (phRes) *phRes = -1; return false; }
    virtual void      SendItemDropHeartbeat()                       {}
    virtual bool      TriggerItemDrop(int* phRes, int32_t)          { if (phRes) *phRes = -1; return false; }
    virtual bool      TradeItems(int* phRes, uint64_t, const uint64_t*, const uint32_t*,
                                  uint32_t, const uint64_t*, const uint32_t*, uint32_t) {
        if (phRes) *phRes = -1; return false;
    }
    virtual bool      LoadItemDefinitions() {
        push_callback(state().user.load(), /*kSteamInventoryDefinitionUpdate*/ 4707,
                      nullptr, 0);
        return true;
    }
    virtual bool      GetItemDefinitionIDs(int32_t* defs, uint32_t* pcb) {
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().inventory_item_defs.find(app);
        if (it == pushed().inventory_item_defs.end()) {
            if (pcb) *pcb = 0;
            return true;
        }
        const auto& table = it->second;
        if (!defs) {
            if (pcb) *pcb = static_cast<uint32_t>(table.size());
            return true;
        }
        const uint32_t cap = pcb ? *pcb : 0;
        uint32_t n = 0;
        for (const auto& kv : table) {
            if (n >= cap) break;
            defs[n++] = kv.first;
        }
        if (pcb) *pcb = n;
        return true;
    }
    virtual bool      GetItemDefinitionProperty(int32_t iDef, const char* propName,
                                                 char* buf, uint32_t* cb) {
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto ait = pushed().inventory_item_defs.find(app);
        if (ait == pushed().inventory_item_defs.end()) {
            if (buf && cb && *cb > 0) buf[0] = '\0';
            if (cb) *cb = 0;
            return false;
        }
        auto dit = ait->second.find(iDef);
        if (dit == ait->second.end()) {
            if (buf && cb && *cb > 0) buf[0] = '\0';
            if (cb) *cb = 0;
            return false;
        }
        std::string value;
        if (!propName || propName[0] == '\0') {
            for (const auto& kv : dit->second) {
                if (!value.empty()) value.push_back(',');
                value.append(kv.first);
            }
        } else {
            auto pit = dit->second.find(propName);
            if (pit == dit->second.end()) {
                if (buf && cb && *cb > 0) buf[0] = '\0';
                if (cb) *cb = 0;
                return false;
            }
            value = pit->second;
        }
        const uint32_t needed = static_cast<uint32_t>(value.size()) + 1; // include NUL
        const uint32_t cap    = cb ? *cb : 0;
        if (buf && cap > 0) {
            const uint32_t copy = (needed <= cap ? needed : cap) - 1;
            std::memcpy(buf, value.data(), copy);
            buf[copy] = '\0';
        }
        if (cb) *cb = needed;
        return true;
    }
    virtual uint64_t RequestEligiblePromoItemDefinitionsIDs(uint64_t sid) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::SteamInventoryEligiblePromoItemDefIDs cb{};
        cb.m_result                   = 2; // k_EResultFail
        cb.m_steamID                  = sid;
        cb.m_numEligiblePromoItemDefs = 0;
        cb.m_bCachedData              = 0;
        push_call_result(h, lsc_cb::kSteamInventoryEligiblePromoItemDefIDs,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      GetEligiblePromoItemDefinitionIDs(uint64_t, int32_t*, uint32_t* pcb) { if (pcb) *pcb = 0; return false; }
    virtual uint64_t StartPurchase(const int32_t* /*defs*/, const uint32_t* /*qtys*/, uint32_t /*n*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::SteamInventoryStartPurchaseResult cb{};
        cb.m_result     = 2;
        cb.m_ulOrderID  = 0;
        cb.m_ulTransID  = 0;
        push_call_result(h, lsc_cb::kSteamInventoryStartPurchaseResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t RequestPrices() {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::SteamInventoryRequestPricesResult cb{};
        cb.m_result = 2;
        cb.m_rgchCurrency[0] = 'U';
        cb.m_rgchCurrency[1] = 'S';
        cb.m_rgchCurrency[2] = 'D';
        cb.m_rgchCurrency[3] = '\0';
        push_call_result(h, lsc_cb::kSteamInventoryRequestPricesResult,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint32_t  GetNumItemsWithPrices()                       { return 0; }
    virtual bool      GetItemsWithPrices(int32_t*, uint64_t*, uint64_t*, uint32_t) { return false; }
    virtual bool      GetItemPrice(int32_t, uint64_t* p, uint64_t* bp) {
        if (p) *p = 0; if (bp) *bp = 0; return false;
    }
    virtual uint64_t  StartUpdateProperties()                       { return 0; }
    virtual bool      RemoveProperty(uint64_t, uint64_t, const char*) { return false; }
    virtual bool      SetProperty_String(uint64_t, uint64_t, const char*, const char*) { return false; }
    virtual bool      SetProperty_Bool  (uint64_t, uint64_t, const char*, bool)        { return false; }
    virtual bool      SetProperty_Int64 (uint64_t, uint64_t, const char*, int64_t)     { return false; }
    virtual bool      SetProperty_Float (uint64_t, uint64_t, const char*, float)       { return false; }
    virtual bool      SubmitUpdateProperties(uint64_t, int* phRes)  { if (phRes) *phRes = -1; return false; }
    virtual bool      InspectItem(int* phRes, const char*)          { if (phRes) *phRes = -1; return false; }
};

class ISteamScreenshotsStub {
public:
    virtual uint32_t  WriteScreenshot(const void*, uint32_t, int, int) { return 0; }
    virtual uint32_t  AddScreenshotToLibrary(const char*, const char*, int, int) { return 0; }
    virtual void      TriggerScreenshot()                            {}
    virtual void      HookScreenshots(bool hooked)                   { hooked_.store(hooked); }
    virtual bool      SetLocation(uint32_t, const char*)             { return false; }
    virtual bool      TagUser(uint32_t, uint64_t)                    { return false; }
    virtual bool      TagPublishedFile(uint32_t, uint64_t)           { return false; }
    virtual bool      IsScreenshotsHooked()                          { return hooked_.load(); }
    virtual uint32_t  AddVRScreenshotToLibrary(int, const char*, const char*) { return 0; }
private:
    std::atomic<bool> hooked_{false};
};

class ISteamMusicStub {
public:
    virtual bool      BIsEnabled()                                   { return false; }
    virtual bool      BIsPlaying()                                   { return false; }
    virtual int       GetPlaybackStatus()                            { return 0; }
    virtual void      Play()                                         {}
    virtual void      Pause()                                        {}
    virtual void      PlayPrevious()                                 {}
    virtual void      PlayNext()                                     {}
    virtual void      SetVolume(float)                               {}
    virtual float     GetVolume()                                    { return 0.0f; }
};

class ISteamAppListStub {
public:
    virtual uint32_t  GetNumInstalledApps() {
        std::lock_guard<std::mutex> lk(state_mutex());
        return static_cast<uint32_t>(pushed().installed_apps.size());
    }
    virtual uint32_t  GetInstalledApps(uint32_t* pvecAppID, uint32_t cMax) {
        std::lock_guard<std::mutex> lk(state_mutex());
        const auto& set = pushed().installed_apps;
        uint32_t total = static_cast<uint32_t>(set.size());
        uint32_t copy  = std::min<uint32_t>(total, cMax);
        if (pvecAppID && copy > 0) {
            uint32_t i = 0;
            for (uint32_t id : set) {
                if (i >= copy) break;
                pvecAppID[i++] = id;
            }
        }
        return copy;
    }
    virtual int       GetAppName(uint32_t appId, char* pName, int cMaxName) {
        if (!pName || cMaxName <= 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_names.find(appId);
        if (it == pushed().app_names.end()) { pName[0] = '\0'; return 0; }
        const std::string& n = it->second;
        int copy = std::min<int>(static_cast<int>(n.size()), cMaxName - 1);
        if (copy > 0) std::memcpy(pName, n.data(), copy);
        pName[copy] = '\0';
        return copy;
    }
    virtual int       GetAppInstallDir(uint32_t appId, char* pDir, int cMaxDir) {
        if (!pDir || cMaxDir <= 0) return 0;
        std::lock_guard<std::mutex> lk(state_mutex());
        auto it = pushed().app_install_dirs.find(appId);
        if (it == pushed().app_install_dirs.end()) { pDir[0] = '\0'; return 0; }
        const std::string& d = it->second;
        int copy = std::min<int>(static_cast<int>(d.size()), cMaxDir - 1);
        if (copy > 0) std::memcpy(pDir, d.data(), copy);
        pDir[copy] = '\0';
        return copy;
    }
    virtual int       GetAppBuildId(uint32_t)                        { return 0; }
};

class ISteamVideoStub {
public:
    virtual uint64_t  GetVideoURL_DEPRECATED(uint32_t)               { return 0; }
    virtual bool      IsBroadcasting(int* pnNumViewers) {
        if (pnNumViewers) *pnNumViewers = 0;
        return false;
    }
    virtual uint64_t  GetOPFSettings(uint32_t)                       { return 0; }
    virtual bool      GetOPFStringForApp(uint32_t, char* buf, int32_t* pnBufSize) {
        if (buf && pnBufSize && *pnBufSize > 0) buf[0] = '\0';
        if (pnBufSize) *pnBufSize = 0;
        return false;
    }
};

class ISteamParentalSettingsStub {
public:
    virtual bool      BIsParentalLockEnabled()                       { return false; }
    virtual bool      BIsParentalLockLocked()                        { return false; }
    virtual bool      BIsAppBlocked(uint32_t)                        { return false; }
    virtual bool      BIsAppInBlockList(uint32_t)                    { return false; }
    virtual bool      BIsFeatureBlocked(int)                         { return false; }
    virtual bool      BIsFeatureInBlockList(int)                     { return false; }
};

class ISteamMatchmakingServersStub {
public:
    static void* fake_handle() {
        return reinterpret_cast<void*>(uintptr_t{1});
    }
    virtual void*     RequestInternetServerList(uint32_t app, void**, uint32_t n, void*) {
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmakingServers.RequestInternetServerList app=%u nFilters=%u",
            app, n);
        return fake_handle();
    }
    virtual void*     RequestLANServerList(uint32_t app, void*) {
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmakingServers.RequestLANServerList app=%u", app);
        return fake_handle();
    }
    virtual void*     RequestFriendsServerList(uint32_t app, void**, uint32_t, void*) {
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmakingServers.RequestFriendsServerList app=%u", app);
        return fake_handle();
    }
    virtual void*     RequestFavoritesServerList(uint32_t app, void**, uint32_t, void*) {
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmakingServers.RequestFavoritesServerList app=%u", app);
        return fake_handle();
    }
    virtual void*     RequestHistoryServerList(uint32_t app, void**, uint32_t, void*) {
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmakingServers.RequestHistoryServerList app=%u", app);
        return fake_handle();
    }
    virtual void*     RequestSpectatorServerList(uint32_t app, void**, uint32_t, void*) {
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmakingServers.RequestSpectatorServerList app=%u", app);
        return fake_handle();
    }
    virtual void      ReleaseRequest(void*)                          {}
    virtual void*     GetServerDetails(void*, int)                   { return nullptr; }
    virtual void      CancelQuery(void*)                             {}
    virtual void      RefreshQuery(void*)                            {}
    virtual bool      IsRefreshing(void*)                            { return false; }
    virtual int       GetServerCount(void*)                          { return 0; }
    virtual void      RefreshServer(void*, int)                      {}
    virtual int       PingServer(uint32_t, uint16_t, void*)          { return -1; /*HSERVERQUERY_INVALID*/ }
    virtual int       PlayerDetails(uint32_t, uint16_t, void*)       { return -1; }
    virtual int       ServerRules(uint32_t, uint16_t, void*)         { return -1; }
    virtual void      CancelServerQuery(int)                         {}
};

struct PendingLobbyFilters {
    struct Entry {
        std::string key;
        std::string value;
        int32_t     comparison  = 0;
        int32_t     filter_type = 0;
    };
    std::vector<Entry> entries;
    int32_t            num_results = 50;
    int32_t            distance    = 1;   // k_ELobbyDistanceFilterDefault
};

static thread_local PendingLobbyFilters tls_lobby_filters;

class ISteamMatchmakingStub {
public:
    virtual int       GetFavoriteGameCount()                         { return 0; }
    virtual bool      GetFavoriteGame(int, uint32_t*, uint32_t*, uint16_t*,
                                       uint16_t*, uint32_t*, uint32_t*) { return false; }
    virtual int       AddFavoriteGame(uint32_t, uint32_t, uint16_t,
                                       uint16_t, uint32_t, uint32_t)   { return -1; }
    virtual bool      RemoveFavoriteGame(uint32_t, uint32_t, uint16_t,
                                          uint16_t, uint32_t)          { return false; }
    virtual uint64_t RequestLobbyList() {
        uint64_t h = alloc_api_call_handle();
        PendingLobbyFilters f = std::move(tls_lobby_filters);
        tls_lobby_filters = PendingLobbyFilters{};
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmaking.RequestLobbyList hCall=0x%llx app=%u filters=%zu",
            (unsigned long long)h, pushed().app_id.load(),
            f.entries.size());

        std::vector<std::string> keys_storage, values_storage;
        std::vector<const char*> keys, values;
        std::vector<int32_t>     comparisons, types;
        keys_storage.reserve(f.entries.size());
        values_storage.reserve(f.entries.size());
        keys.reserve(f.entries.size());
        values.reserve(f.entries.size());
        comparisons.reserve(f.entries.size());
        types.reserve(f.entries.size());
        for (auto& e : f.entries) {
            keys_storage.push_back(std::move(e.key));
            values_storage.push_back(std::move(e.value));
            keys.push_back(keys_storage.back().c_str());
            values.push_back(values_storage.back().c_str());
            comparisons.push_back(e.comparison);
            types.push_back(e.filter_type);
        }

        const uint32_t app = pushed().app_id.load();
        bool dispatched = wn_cm_lobby_get_list(
            h,
            app,
            f.num_results,
            keys.empty() ? nullptr : keys.data(),
            values.empty() ? nullptr : values.data(),
            comparisons.empty() ? nullptr : comparisons.data(),
            types.empty() ? nullptr : types.data(),
            keys.size(),
            [](uint64_t hCall, int32_t eresult,
               const WnCmLobbyEntry* lobbies, size_t count) {
                std::vector<uint64_t> sids;
                sids.reserve(count);
                if (lobbies && eresult >= 0) {
                    auto guard = std::lock_guard{state_mutex()};
                    for (size_t i = 0; i < count; ++i) {
                        sids.push_back(lobbies[i].steam_id);
                        auto& L = pushed().active_lobbies[lobbies[i].steam_id];
                        L.max_members = lobbies[i].max_members;
                    }
                    pushed().lobby_match_list = sids;
                }
                lsc_cb::LobbyMatchList cb{};
                cb.m_nLobbiesMatching = static_cast<uint32_t>(sids.size());
                push_call_result(hCall, lsc_cb::kLobbyMatchList,
                                  &cb, sizeof(cb),
                                  /*io_failure=*/(eresult < 0));
            });
        if (!dispatched) {
            lsc_cb::LobbyMatchList cb{};
            cb.m_nLobbiesMatching = 0;
            push_call_result(h, lsc_cb::kLobbyMatchList,
                             &cb, sizeof(cb), /*io_failure=*/true);
        }
        return h;
    }
    virtual void AddRequestLobbyListStringFilter(const char* k, const char* v, int cmp) {
        if (!k || !v) return;
        tls_lobby_filters.entries.push_back({k, v, cmp, /*String*/ 0});
    }
    virtual void AddRequestLobbyListNumericalFilter(const char* k, int v, int cmp) {
        if (!k) return;
        tls_lobby_filters.entries.push_back({k, std::to_string(v), cmp, /*Numerical*/ 1});
    }
    virtual void AddRequestLobbyListNearValueFilter(const char* k, int v) {
        if (!k) return;
        tls_lobby_filters.entries.push_back({k, std::to_string(v), /*cmp*/ 0, /*NearValue*/ 3});
    }
    virtual void AddRequestLobbyListFilterSlotsAvailable(int slots) {
        tls_lobby_filters.entries.push_back({"", std::to_string(slots), 0, /*SlotsAvail*/ 2});
    }
    virtual void AddRequestLobbyListDistanceFilter(int eDist) {
        tls_lobby_filters.distance = eDist;
        tls_lobby_filters.entries.push_back({"", std::to_string(eDist), 0, /*Distance*/ 4});
    }
    virtual void AddRequestLobbyListResultCountFilter(int n) {
        if (n > 0) tls_lobby_filters.num_results = n;
    }
    virtual void AddRequestLobbyListCompatibleMembersFilter(uint64_t /*sid*/) {}
    virtual uint64_t GetLobbyByIndex(int idx) {
        if (idx < 0) return 0;
        auto guard = std::lock_guard{state_mutex()};
        const auto& v = pushed().lobby_match_list;
        if (static_cast<size_t>(idx) >= v.size()) return 0;
        return v[idx];
    }
    virtual uint64_t CreateLobby(int eLobbyType, int maxMembers) {
        const uint64_t h = alloc_api_call_handle();
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmaking.CreateLobby hCall=0x%llx type=%d maxMembers=%d",
            (unsigned long long)h, eLobbyType, maxMembers);
        bool dispatched = wn_cm_lobby_create(
            h, pushed().app_id.load(),
            static_cast<int32_t>(eLobbyType),
            static_cast<int32_t>(maxMembers > 0 ? maxMembers : 4),
            [](uint64_t hCall, int32_t eresult, uint64_t lobby_sid) {
                lsc_cb::LobbyCreated cb{};
                cb.m_eResult        = (eresult > 0) ? eresult : 2; // synthetic fail → Fail
                cb.m_ulSteamIDLobby = lobby_sid;
                push_call_result(hCall, lsc_cb::kLobbyCreated,
                                 &cb, sizeof(cb),
                                 /*io_failure=*/(eresult < 0));
                if (cb.m_eResult == 1 && lobby_sid != 0) {
                    lsc_cb::LobbyEnter le{};
                    le.m_ulSteamIDLobby         = lobby_sid;
                    le.m_rgfChatPermissions     = 0;
                    le.m_bLocked                = 0;
                    le.m_EChatRoomEnterResponse = 1; // Success
                    push_callback(state().user.load(),
                                  lsc_cb::kLobbyEnter, &le, sizeof(le));
                }
            });
        if (!dispatched) {
            lsc_cb::LobbyCreated cb{};
            cb.m_eResult        = 2; // k_EResultFail
            cb.m_ulSteamIDLobby = 0;
            push_call_result(h, lsc_cb::kLobbyCreated,
                             &cb, sizeof(cb), /*io_failure=*/true);
        }
        return h;
    }
    virtual uint64_t JoinLobby(uint64_t lobbySid) {
        const uint64_t h = alloc_api_call_handle();
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmaking.JoinLobby hCall=0x%llx lobby=0x%llx",
            (unsigned long long)h, (unsigned long long)lobbySid);
        bool dispatched = wn_cm_lobby_join(
            h, pushed().app_id.load(), lobbySid,
            [](uint64_t hCall, int32_t chat_resp, uint64_t lobby_sid) {
                lsc_cb::LobbyEnter cb{};
                cb.m_ulSteamIDLobby         = lobby_sid;
                cb.m_rgfChatPermissions     = 0;
                cb.m_bLocked                = 0;
                cb.m_EChatRoomEnterResponse = (chat_resp > 0) ? chat_resp : 2;
                push_call_result(hCall, lsc_cb::kLobbyEnter,
                                 &cb, sizeof(cb),
                                 /*io_failure=*/(chat_resp < 0));
            });
        if (!dispatched) {
            lsc_cb::LobbyEnter cb{};
            cb.m_ulSteamIDLobby         = lobbySid;
            cb.m_EChatRoomEnterResponse = 2; // Error
            push_call_result(h, lsc_cb::kLobbyEnter,
                             &cb, sizeof(cb), /*io_failure=*/true);
        }
        return h;
    }
    virtual void LeaveLobby(uint64_t sid) {
        if (sid == 0) return;
        __android_log_print(ANDROID_LOG_INFO, "WnLibSteamClient",
            "ISteamMatchmaking.LeaveLobby lobby=0x%llx",
            (unsigned long long)sid);
        wn_cm_lobby_leave(pushed().app_id.load(), sid);
        auto guard = std::lock_guard{state_mutex()};
        pushed().active_lobbies.erase(sid);
    }
    virtual bool      InviteUserToLobby(uint64_t sid, uint64_t invitee) {
        if (sid == 0 || invitee == 0) return false;
        {
            auto guard = std::lock_guard{state_mutex()};
            if (pushed().active_lobbies.find(sid)
                    == pushed().active_lobbies.end()) {
                return false;
            }
        }
        return wn_cm_lobby_invite_user(pushed().app_id.load(), sid, invitee);
    }
    virtual int GetNumLobbyMembers(uint64_t sid) {
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return 0;
        return static_cast<int>(it->second.members.size());
    }
    virtual uint64_t GetLobbyMemberByIndex(uint64_t sid, int idx) {
        if (idx < 0) return 0;
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return 0;
        int n = 0;
        for (const auto& kv : it->second.members) {
            if (n++ == idx) return kv.first;
        }
        return 0;
    }
    virtual const char* GetLobbyData(uint64_t sid, const char* key) {
        static thread_local std::string tls;
        if (!key) { tls.clear(); return tls.c_str(); }
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) { tls.clear(); return tls.c_str(); }
        auto kt = it->second.data.find(key);
        tls = (kt == it->second.data.end()) ? std::string{} : kt->second;
        return tls.c_str();
    }
    virtual bool SetLobbyData(uint64_t sid, const char* key, const char* val) {
        if (sid == 0 || !key) return false;
        std::string blob;
        int32_t max_members = 0;
        int32_t lobby_type  = 0;
        int32_t lobby_flags = 0;
        {
            auto guard = std::lock_guard{state_mutex()};
            auto& L = pushed().active_lobbies[sid];
            L.data[key] = val ? val : "";
            max_members = L.max_members;
            lobby_type  = L.lobby_type;
            lobby_flags = L.lobby_flags;
            for (const auto& kv : L.data) {
                blob.append(kv.first);
                blob.push_back('\0');
                blob.append(kv.second);
                blob.push_back('\0');
            }
            blob.push_back('\0');  // double-null terminator
        }
        const uint64_t h = alloc_api_call_handle();
        wn_cm_lobby_set_data(h, pushed().app_id.load(), sid,
                             /*steam_id_member=*/0,
                             reinterpret_cast<const uint8_t*>(blob.data()),
                             blob.size(),
                             max_members, lobby_type, lobby_flags,
                             [](uint64_t /*hCall*/, int32_t /*eresult*/) {
                             });
        return true;
    }
    virtual int GetLobbyDataCount(uint64_t sid) {
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return 0;
        return static_cast<int>(it->second.data.size());
    }
    virtual bool GetLobbyDataByIndex(uint64_t sid, int idx, char* key, int kn,
                                      char* val, int vn) {
        if (key && kn > 0) key[0] = '\0';
        if (val && vn > 0) val[0] = '\0';
        if (idx < 0) return false;
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return false;
        int n = 0;
        for (const auto& kv : it->second.data) {
            if (n++ != idx) continue;
            if (key && kn > 0) {
                const auto cc = (kv.first.size() < static_cast<size_t>(kn - 1)
                                  ? kv.first.size() : static_cast<size_t>(kn - 1));
                std::memcpy(key, kv.first.data(), cc);
                key[cc] = '\0';
            }
            if (val && vn > 0) {
                const auto cc = (kv.second.size() < static_cast<size_t>(vn - 1)
                                  ? kv.second.size() : static_cast<size_t>(vn - 1));
                std::memcpy(val, kv.second.data(), cc);
                val[cc] = '\0';
            }
            return true;
        }
        return false;
    }
    virtual bool DeleteLobbyData(uint64_t sid, const char* key) {
        if (sid == 0 || !key) return false;
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return false;
        return it->second.data.erase(key) > 0;
    }
    virtual const char* GetLobbyMemberData(uint64_t sid, uint64_t member, const char* key) {
        static thread_local std::string tls;
        tls.clear();
        if (!key) return tls.c_str();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return tls.c_str();
        auto mt = it->second.members.find(member);
        if (mt == it->second.members.end()) return tls.c_str();
        auto kt = mt->second.data.find(key);
        if (kt == mt->second.data.end()) return tls.c_str();
        tls = kt->second;
        return tls.c_str();
    }
    virtual void      SetLobbyMemberData(uint64_t sid, const char* key,
                                         const char* val) {
        if (sid == 0 || !key) return;
        const uint64_t self = pushed().steam_id.load();
        if (self == 0) return;
        std::string blob;
        int32_t max_members = 0;
        int32_t lobby_type  = 0;
        int32_t lobby_flags = 0;
        {
            auto guard = std::lock_guard{state_mutex()};
            auto& L = pushed().active_lobbies[sid];
            auto& M = L.members[self];
            if (val && *val) M.data[key] = val;
            else             M.data.erase(key);
            max_members = L.max_members;
            lobby_type  = L.lobby_type;
            lobby_flags = L.lobby_flags;
            for (const auto& kv : M.data) {
                blob.append(kv.first);
                blob.push_back('\0');
                blob.append(kv.second);
                blob.push_back('\0');
            }
            blob.push_back('\0');
        }
        const uint64_t h = alloc_api_call_handle();
        wn_cm_lobby_set_data(h, pushed().app_id.load(), sid,
                             /*steam_id_member=*/self,
                             reinterpret_cast<const uint8_t*>(blob.data()),
                             blob.size(),
                             max_members, lobby_type, lobby_flags,
                             [](uint64_t /*hCall*/, int32_t /*eresult*/) {
                             });
    }
    virtual bool      SendLobbyChatMsg(uint64_t sid, const void* body, int n) {
        if (sid == 0 || !body || n <= 0) return false;
        return wn_cm_lobby_send_chat(pushed().app_id.load(), sid,
                                     static_cast<const uint8_t*>(body),
                                     static_cast<size_t>(n));
    }
    virtual int       GetLobbyChatEntry(uint64_t sid, int idx,
                                         uint64_t* speaker_out,
                                         void* body_out, int body_cap,
                                         int* chat_type_out) {
        if (speaker_out)   *speaker_out   = 0;
        if (chat_type_out) *chat_type_out = 0;
        if (sid == 0 || idx < 0 || !body_out || body_cap <= 0) return 0;
        auto guard = std::lock_guard{state_mutex()};
        auto bt = pushed().lobby_chat_buffer.find(sid);
        if (bt == pushed().lobby_chat_buffer.end()) return 0;
        const auto& ring = bt->second;
        if (static_cast<size_t>(idx) >= ring.size()) return 0;
        const auto& e = ring[static_cast<size_t>(idx)];
        if (speaker_out)   *speaker_out   = e.sender_sid;
        if (chat_type_out) *chat_type_out = e.chat_type;
        const int n = static_cast<int>(
            std::min<size_t>(e.body.size(),
                              static_cast<size_t>(body_cap)));
        if (n > 0) std::memcpy(body_out, e.body.data(),
                               static_cast<size_t>(n));
        return n;
    }
    virtual bool      RequestLobbyData(uint64_t sid) {
        if (sid == 0) return false;
        bool have = false;
        {
            auto guard = std::lock_guard{state_mutex()};
            have = pushed().active_lobbies.find(sid) !=
                   pushed().active_lobbies.end();
        }
        struct LobbyDataUpdate {
            uint64_t lobby;
            uint64_t member;
            uint8_t  success;
            uint8_t  _pad[7];
        };
        LobbyDataUpdate cb{};
        cb.lobby   = sid;
        cb.member  = sid;
        cb.success = have ? 1 : 0;
        push_callback(state().user.load(), /*kLobbyDataUpdate*/ 505,
                      &cb, sizeof(cb));
        return true;
    }
    virtual void SetLobbyGameServer(uint64_t sid, uint32_t ip, uint16_t port, uint64_t gs) {
        if (sid == 0) return;
        auto guard = std::lock_guard{state_mutex()};
        auto& L = pushed().active_lobbies[sid];
        L.game_server_ip   = ip;
        L.game_server_port = port;
        L.game_server_sid  = gs;
    }
    virtual bool GetLobbyGameServer(uint64_t sid, uint32_t* ip,
                                     uint16_t* port, uint64_t* sid_out) {
        if (ip) *ip = 0; if (port) *port = 0; if (sid_out) *sid_out = 0;
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return false;
        if (it->second.game_server_sid == 0 && it->second.game_server_ip == 0) return false;
        if (ip)      *ip      = it->second.game_server_ip;
        if (port)    *port    = it->second.game_server_port;
        if (sid_out) *sid_out = it->second.game_server_sid;
        return true;
    }
    virtual bool SetLobbyMemberLimit(uint64_t sid, int max_members) {
        if (sid == 0 || max_members <= 0) return false;
        auto guard = std::lock_guard{state_mutex()};
        auto& L = pushed().active_lobbies[sid];
        L.max_members = max_members;
        return true;
    }
    virtual int GetLobbyMemberLimit(uint64_t sid) {
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return 0;
        return it->second.max_members;
    }
    virtual bool      SetLobbyType(uint64_t sid, int eLobbyType) {
        if (sid == 0) return false;
        std::string blob;
        int32_t max_members = 0;
        int32_t lobby_flags = 0;
        {
            auto guard = std::lock_guard{state_mutex()};
            auto it = pushed().active_lobbies.find(sid);
            if (it == pushed().active_lobbies.end()) return false;
            auto& L = it->second;
            if (L.owner_sid != pushed().steam_id.load()) return false;
            L.lobby_type = eLobbyType;
            max_members  = L.max_members;
            lobby_flags  = L.lobby_flags;
            for (const auto& kv : L.data) {
                blob.append(kv.first); blob.push_back('\0');
                blob.append(kv.second); blob.push_back('\0');
            }
            blob.push_back('\0');
        }
        const uint64_t h = alloc_api_call_handle();
        wn_cm_lobby_set_data(h, pushed().app_id.load(), sid, /*member=*/0,
                             reinterpret_cast<const uint8_t*>(blob.data()),
                             blob.size(),
                             max_members, eLobbyType, lobby_flags,
                             [](uint64_t, int32_t) {});
        return true;
    }
    virtual bool      SetLobbyJoinable(uint64_t sid, bool joinable) {
        if (sid == 0) return false;
        std::string blob;
        int32_t max_members = 0;
        int32_t lobby_type  = 0;
        int32_t new_flags   = 0;
        {
            auto guard = std::lock_guard{state_mutex()};
            auto it = pushed().active_lobbies.find(sid);
            if (it == pushed().active_lobbies.end()) return false;
            auto& L = it->second;
            if (L.owner_sid != pushed().steam_id.load()) return false;
            L.joinable = joinable;
            new_flags = joinable
                        ? (L.lobby_flags & ~0x1)   // clear "non-joinable" bit
                        : (L.lobby_flags |  0x1);  // set it
            L.lobby_flags = new_flags;
            max_members   = L.max_members;
            lobby_type    = L.lobby_type;
            for (const auto& kv : L.data) {
                blob.append(kv.first); blob.push_back('\0');
                blob.append(kv.second); blob.push_back('\0');
            }
            blob.push_back('\0');
        }
        const uint64_t h = alloc_api_call_handle();
        wn_cm_lobby_set_data(h, pushed().app_id.load(), sid, /*member=*/0,
                             reinterpret_cast<const uint8_t*>(blob.data()),
                             blob.size(),
                             max_members, lobby_type, new_flags,
                             [](uint64_t, int32_t) {});
        return true;
    }
    virtual uint64_t GetLobbyOwner(uint64_t sid) {
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_lobbies.find(sid);
        if (it == pushed().active_lobbies.end()) return 0;
        return it->second.owner_sid;
    }
    virtual bool      SetLobbyOwner(uint64_t sid, uint64_t new_owner) {
        if (sid == 0 || new_owner == 0) return false;
        {
            auto guard = std::lock_guard{state_mutex()};
            auto it = pushed().active_lobbies.find(sid);
            if (it == pushed().active_lobbies.end()) return false;
            if (it->second.owner_sid != pushed().steam_id.load()) return false;
            it->second.owner_sid = new_owner;
        }
        const uint64_t h = alloc_api_call_handle();
        return wn_cm_lobby_set_owner(h, pushed().app_id.load(), sid,
                                      new_owner,
                                      [](uint64_t, int32_t) {});
    }
    virtual bool      SetLinkedLobby(uint64_t, uint64_t)              { return false; }
};

class ISteamNetworkingStub {
public:
    virtual bool SendP2PPacket(uint64_t sid, const void* /*data*/,
                                uint32_t n, int /*eP2PSendType*/,
                                int /*nChannel*/) {
        if (sid == 0) return false;
        auto guard = std::lock_guard{state_mutex()};
        auto& s = pushed().active_p2p_sessions[sid];
        if (!s.connection_active && !s.connecting) {
            s.connecting           = true;
            s.using_relay          = pushed().p2p_relay_allowed.load();
        }
        s.bytes_queued_for_send += n;
        return true;
    }
    virtual bool IsP2PPacketAvailable(uint32_t* pcub, int nChannel) {
        auto guard = std::lock_guard{state_mutex()};
        auto& q = pushed().p2p_inbound_queue[nChannel];
        if (q.empty()) { if (pcub) *pcub = 0; return false; }
        if (pcub) *pcub = static_cast<uint32_t>(q.front().body.size());
        return true;
    }
    virtual bool ReadP2PPacket(void* dest, uint32_t cubDest,
                                uint32_t* pcub, uint64_t* sidOut, int nChannel) {
        if (pcub) *pcub = 0;
        if (sidOut) *sidOut = 0;
        auto guard = std::lock_guard{state_mutex()};
        auto& q = pushed().p2p_inbound_queue[nChannel];
        if (q.empty()) return false;
        auto& pkt = q.front();
        if (sidOut) *sidOut = pkt.sender_sid;
        const auto copy = static_cast<uint32_t>(
            pkt.body.size() < cubDest ? pkt.body.size() : cubDest);
        if (dest && copy > 0) std::memcpy(dest, pkt.body.data(), copy);
        if (pcub) *pcub = copy;
        q.pop_front();
        return true;
    }
    virtual bool AcceptP2PSessionWithUser(uint64_t sid) {
        if (sid == 0) return false;
        auto guard = std::lock_guard{state_mutex()};
        auto& s = pushed().active_p2p_sessions[sid];
        s.connection_active = true;
        s.connecting        = false;
        s.last_session_error = 0;
        return true;
    }
    virtual bool CloseP2PSessionWithUser(uint64_t sid) {
        if (sid == 0) return false;
        auto guard = std::lock_guard{state_mutex()};
        if (!pushed().active_p2p_sessions.erase(sid)) return false;
        for (auto& kv : pushed().p2p_inbound_queue) {
            auto& q = kv.second;
            q.erase(std::remove_if(q.begin(), q.end(),
                [sid](const PushedState::P2PInboundPacket& p) {
                    return p.sender_sid == sid;
                }),
                q.end());
        }
        return true;
    }
    virtual bool CloseP2PChannelWithUser(uint64_t sid, int nChannel) {
        if (sid == 0) return false;
        auto guard = std::lock_guard{state_mutex()};
        auto& q = pushed().p2p_inbound_queue[nChannel];
        const auto before = q.size();
        q.erase(std::remove_if(q.begin(), q.end(),
            [sid](const PushedState::P2PInboundPacket& p) {
                return p.sender_sid == sid;
            }),
            q.end());
        return q.size() != before;
    }
    virtual bool GetP2PSessionState(uint64_t sid, void* pState) {
        if (!pState) return false;
        struct P2PSessionStateWire {
            uint8_t  m_bConnectionActive;
            uint8_t  m_bConnecting;
            uint8_t  m_eP2PSessionError;
            uint8_t  m_bUsingRelay;
            int32_t  m_nBytesQueuedForSend;
            int32_t  m_nPacketsQueuedForSend;
            uint32_t m_nRemoteIP;
            uint16_t m_nRemotePort;
            uint16_t _pad;
        };
        auto* out = reinterpret_cast<P2PSessionStateWire*>(pState);
        std::memset(out, 0, sizeof(P2PSessionStateWire));
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().active_p2p_sessions.find(sid);
        if (it == pushed().active_p2p_sessions.end()) return false;
        const auto& s = it->second;
        out->m_bConnectionActive     = s.connection_active ? 1 : 0;
        out->m_bConnecting           = s.connecting ? 1 : 0;
        out->m_eP2PSessionError      = static_cast<uint8_t>(s.last_session_error);
        out->m_bUsingRelay           = s.using_relay ? 1 : 0;
        out->m_nBytesQueuedForSend   = static_cast<int32_t>(s.bytes_queued_for_send);
        out->m_nPacketsQueuedForSend = s.bytes_queued_for_send > 0 ? 1 : 0;
        out->m_nRemoteIP             = s.remote_ip;
        out->m_nRemotePort           = s.remote_port;
        return true;
    }
    virtual bool AllowP2PPacketRelay(bool bAllow) {
        pushed().p2p_relay_allowed.store(bAllow);
        return true;
    }

    virtual int       CreateListenSocket(int, uint32_t, uint16_t, bool) { return -1; }
    virtual int       CreateP2PConnectionSocket(uint64_t, int, int, bool) { return -1; }
    virtual int       CreateConnectionSocket(uint32_t, uint16_t, int)  { return -1; }
    virtual bool      DestroySocket(int, bool)                       { return false; }
    virtual bool      DestroyListenSocket(int, bool)                 { return false; }
    virtual bool      SendDataOnSocket(int, void*, uint32_t, bool)   { return false; }
    virtual bool      IsDataAvailableOnSocket(int, uint32_t* pcb) {
        if (pcb) *pcb = 0;
        return false;
    }
    virtual bool      RetrieveDataFromSocket(int, void*, uint32_t, uint32_t* pcb) {
        if (pcb) *pcb = 0;
        return false;
    }
    virtual bool      IsDataAvailable(int, uint32_t* pcb, int*) {
        if (pcb) *pcb = 0;
        return false;
    }
    virtual bool      RetrieveData(int, void*, uint32_t, uint32_t* pcb, int*) {
        if (pcb) *pcb = 0;
        return false;
    }
    virtual bool      GetSocketInfo(int, uint64_t* sid, int* status,
                                     uint32_t* ip, uint16_t* port, int* lsock) {
        if (sid)    *sid    = 0;
        if (status) *status = 0;
        if (ip)     *ip     = 0;
        if (port)   *port   = 0;
        if (lsock)  *lsock  = -1;
        return false;
    }
    virtual bool      GetListenSocketInfo(int, uint32_t* ip, uint16_t* port) {
        if (ip)   *ip   = 0;
        if (port) *port = 0;
        return false;
    }
    virtual int       GetSocketConnectionType(int)                   { return 0; }
    virtual int       GetMaxPacketSize(int)                          { return 0; }
};

class ISteamUGCStub {
public:
    virtual uint64_t  CreateQueryUserUGCRequest(uint32_t, int, int, int, uint32_t, uint32_t, uint32_t) { return 0; }
    virtual uint64_t  CreateQueryAllUGCRequest_Page(int, int, uint32_t, uint32_t, uint32_t) { return 0; }
    virtual uint64_t  CreateQueryAllUGCRequest_Cursor(int, int, uint32_t, uint32_t, const char*) { return 0; }
    virtual uint64_t  CreateQueryUGCDetailsRequest(const uint64_t*, uint32_t) { return 0; }
    virtual uint64_t SendQueryUGCRequest(uint64_t handle) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::SteamUGCQueryCompleted cb{};
        cb.m_handle                 = handle;
        cb.m_eResult                = 1; // k_EResultOK — empty success
        cb.m_unNumResultsReturned   = 0;
        cb.m_unTotalMatchingResults = 0;
        cb.m_bCachedData            = 0;
        cb.m_rgchNextCursor[0]      = '\0';
        push_call_result(h, lsc_cb::kSteamUGCQueryCompleted,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual bool      GetQueryUGCResult(uint64_t, uint32_t, void*)   { return false; }
    virtual uint32_t  GetQueryUGCNumTags(uint64_t, uint32_t)         { return 0; }
    virtual bool      GetQueryUGCTag(uint64_t, uint32_t, uint32_t, char* v, uint32_t vn) { if (v && vn) v[0] = '\0'; return false; }
    virtual bool      GetQueryUGCTagDisplayName(uint64_t, uint32_t, uint32_t, char* v, uint32_t vn) { if (v && vn) v[0] = '\0'; return false; }
    virtual bool      GetQueryUGCPreviewURL(uint64_t, uint32_t, char* v, uint32_t vn) { if (v && vn) v[0] = '\0'; return false; }
    virtual bool      GetQueryUGCMetadata(uint64_t, uint32_t, char* v, uint32_t vn) { if (v && vn) v[0] = '\0'; return false; }
    virtual bool      GetQueryUGCChildren(uint64_t, uint32_t, uint64_t*, uint32_t) { return false; }
    virtual bool      GetQueryUGCStatistic(uint64_t, uint32_t, int, uint64_t* out) { if (out) *out = 0; return false; }
    virtual uint32_t  GetQueryUGCNumAdditionalPreviews(uint64_t, uint32_t) { return 0; }
    virtual bool      GetQueryUGCAdditionalPreview(uint64_t, uint32_t, uint32_t, char* url, uint32_t uns, char* orig, uint32_t os, int*) {
        if (url && uns) url[0] = '\0';
        if (orig && os) orig[0] = '\0';
        return false;
    }
    virtual uint32_t  GetQueryUGCNumKeyValueTags(uint64_t, uint32_t) { return 0; }
    virtual bool      GetQueryUGCKeyValueTagByIndex(uint64_t, uint32_t, uint32_t, char* k, uint32_t kn, char* v, uint32_t vn) {
        if (k && kn) k[0] = '\0';
        if (v && vn) v[0] = '\0';
        return false;
    }
    virtual bool      GetQueryUGCKeyValueTagByName(uint64_t, uint32_t, const char*, char* v, uint32_t vn) {
        if (v && vn) v[0] = '\0';
        return false;
    }
    virtual uint32_t  GetQueryUGCContentDescriptors(uint64_t, uint32_t, int*, uint32_t) { return 0; }
    virtual bool      ReleaseQueryUGCRequest(uint64_t)               { return false; }
    virtual bool      AddRequiredTag(uint64_t, const char*)          { return false; }
    virtual bool      AddRequiredTagGroup(uint64_t, const void*)     { return false; }
    virtual bool      AddExcludedTag(uint64_t, const char*)          { return false; }
    virtual bool      SetReturnOnlyIDs(uint64_t, bool)               { return false; }
    virtual bool      SetReturnKeyValueTags(uint64_t, bool)          { return false; }
    virtual bool      SetReturnLongDescription(uint64_t, bool)       { return false; }
    virtual bool      SetReturnMetadata(uint64_t, bool)              { return false; }
    virtual bool      SetReturnChildren(uint64_t, bool)              { return false; }
    virtual bool      SetReturnAdditionalPreviews(uint64_t, bool)    { return false; }
    virtual bool      SetReturnTotalOnly(uint64_t, bool)             { return false; }
    virtual bool      SetReturnPlaytimeStats(uint64_t, uint32_t)     { return false; }
    virtual bool      SetLanguage(uint64_t, const char*)             { return false; }
    virtual bool      SetAllowCachedResponse(uint64_t, uint32_t)     { return false; }
    virtual bool      SetCloudFileNameFilter(uint64_t, const char*)  { return false; }
    virtual bool      SetMatchAnyTag(uint64_t, bool)                 { return false; }
    virtual bool      SetSearchText(uint64_t, const char*)           { return false; }
    virtual bool      SetRankedByTrendDays(uint64_t, uint32_t)       { return false; }
    virtual bool      SetTimeCreatedDateRange(uint64_t, uint32_t, uint32_t) { return false; }
    virtual bool      SetTimeUpdatedDateRange(uint64_t, uint32_t, uint32_t) { return false; }
    virtual bool      AddRequiredKeyValueTag(uint64_t, const char*, const char*) { return false; }
    virtual uint64_t RequestUGCDetails(uint64_t /*publishedFileId*/, uint32_t /*maxAgeSeconds*/) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::SteamUGCRequestUGCDetailsResultMinimal cb{};
        cb.m_eResult = 2; // k_EResultFail
        push_call_result(h, lsc_cb::kSteamUGCRequestUGCDetails,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t  CreateItem(uint32_t, int)                      { return 0; }
    virtual uint64_t  StartItemUpdate(uint32_t, uint64_t)            { return 0; }
    virtual bool      SetItemTitle(uint64_t, const char*)            { return false; }
    virtual bool      SetItemDescription(uint64_t, const char*)      { return false; }
    virtual bool      SetItemUpdateLanguage(uint64_t, const char*)   { return false; }
    virtual bool      SetItemMetadata(uint64_t, const char*)         { return false; }
    virtual bool      SetItemVisibility(uint64_t, int)               { return false; }
    virtual bool      SetItemTags(uint64_t, const void*, bool)       { return false; }
    virtual bool      SetItemContent(uint64_t, const char*)          { return false; }
    virtual bool      SetItemPreview(uint64_t, const char*)          { return false; }
    virtual bool      SetAllowLegacyUpload(uint64_t, bool)           { return false; }
    virtual bool      RemoveAllItemKeyValueTags(uint64_t)            { return false; }
    virtual bool      RemoveItemKeyValueTags(uint64_t, const char*)  { return false; }
    virtual bool      AddItemKeyValueTag(uint64_t, const char*, const char*) { return false; }
    virtual bool      AddItemPreviewFile(uint64_t, const char*, int) { return false; }
    virtual bool      AddItemPreviewVideo(uint64_t, const char*)     { return false; }
    virtual bool      UpdateItemPreviewFile(uint64_t, uint32_t, const char*) { return false; }
    virtual bool      UpdateItemPreviewVideo(uint64_t, uint32_t, const char*) { return false; }
    virtual bool      RemoveItemPreview(uint64_t, uint32_t)          { return false; }
    virtual bool      AddContentDescriptor(uint64_t, int)            { return false; }
    virtual bool      RemoveContentDescriptor(uint64_t, int)         { return false; }
    virtual uint64_t  SubmitItemUpdate(uint64_t, const char*)        { return 0; }
    virtual int       GetItemUpdateProgress(uint64_t, uint64_t* bp, uint64_t* bt) {
        if (bp) *bp = 0;
        if (bt) *bt = 0;
        return 0;
    }
    virtual uint64_t  SetUserItemVote(uint64_t, bool)                { return 0; }
    virtual uint64_t  GetUserItemVote(uint64_t)                      { return 0; }
    virtual uint64_t  AddItemToFavorites(uint32_t, uint64_t)         { return 0; }
    virtual uint64_t  RemoveItemFromFavorites(uint32_t, uint64_t)    { return 0; }
    virtual uint64_t SubscribeItem(uint64_t publishedFileId) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::RemoteStorageSubscribePublishedFileResult cb{};
        cb.m_eResult          = 2; // k_EResultFail (no UGC backend)
        cb.m_nPublishedFileId = publishedFileId;
        push_call_result(h, lsc_cb::kRemoteStorageSubscribePublishedFile,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint64_t UnsubscribeItem(uint64_t publishedFileId) {
        uint64_t h = alloc_api_call_handle();
        lsc_cb::RemoteStorageUnsubscribePublishedFileResult cb{};
        cb.m_eResult          = 2; // k_EResultFail
        cb.m_nPublishedFileId = publishedFileId;
        push_call_result(h, lsc_cb::kRemoteStorageUnsubscribePublishedFile,
                         &cb, sizeof(cb), /*io_failure=*/false);
        return h;
    }
    virtual uint32_t  GetNumSubscribedItems() {
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().subscribed_workshop_items.find(app);
        if (it == pushed().subscribed_workshop_items.end()) return 0;
        return static_cast<uint32_t>(it->second.size());
    }
    virtual uint32_t  GetSubscribedItems(uint64_t* pIds, uint32_t cMax) {
        if (!pIds || cMax == 0) return 0;
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().subscribed_workshop_items.find(app);
        if (it == pushed().subscribed_workshop_items.end()) return 0;
        uint32_t n = 0;
        for (const auto& kv : it->second) {
            if (n >= cMax) break;
            pIds[n++] = kv.first;
        }
        return n;
    }
    virtual uint32_t  GetItemState(uint64_t publishedFileId) {
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().subscribed_workshop_items.find(app);
        if (it == pushed().subscribed_workshop_items.end()) return 0;
        auto jt = it->second.find(publishedFileId);
        if (jt == it->second.end() || !jt->second.installed) return 0;
        return /*k_EItemStateSubscribed*/ 1u | /*k_EItemStateInstalled*/ 4u;
    }
    virtual bool      GetItemInstallInfo(uint64_t publishedFileId,
                                          uint64_t* bytes,
                                          char* folder, uint32_t fn,
                                          uint32_t* timestamp) {
        if (bytes) *bytes = 0;
        if (folder && fn) folder[0] = '\0';
        if (timestamp) *timestamp = 0;
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().subscribed_workshop_items.find(app);
        if (it == pushed().subscribed_workshop_items.end()) return false;
        auto jt = it->second.find(publishedFileId);
        if (jt == it->second.end() || !jt->second.installed) return false;
        if (bytes)     *bytes     = jt->second.size_bytes;
        if (timestamp) *timestamp = jt->second.timestamp;
        if (folder && fn) {
            const auto& src = jt->second.install_dir;
            const auto copy = (src.size() < fn ? src.size() : fn - 1);
            std::memcpy(folder, src.data(), copy);
            folder[copy] = '\0';
        }
        return true;
    }
    virtual bool      GetItemDownloadInfo(uint64_t publishedFileId, uint64_t* bd, uint64_t* bt) {
        if (bd) *bd = 0;
        if (bt) *bt = 0;
        const auto app = pushed().app_id.load();
        auto guard = std::lock_guard{state_mutex()};
        auto it = pushed().subscribed_workshop_items.find(app);
        if (it == pushed().subscribed_workshop_items.end()) return false;
        auto jt = it->second.find(publishedFileId);
        if (jt == it->second.end() || !jt->second.installed) return false;
        if (bd) *bd = jt->second.size_bytes;
        if (bt) *bt = jt->second.size_bytes;
        return true;
    }
    virtual bool      DownloadItem(uint64_t publishedFileId, bool /*bHighPriority*/) {
        const auto app = pushed().app_id.load();
        bool installed = false;
        {
            auto guard = std::lock_guard{state_mutex()};
            auto it = pushed().subscribed_workshop_items.find(app);
            if (it != pushed().subscribed_workshop_items.end()) {
                auto jt = it->second.find(publishedFileId);
                if (jt != it->second.end() && jt->second.installed) installed = true;
            }
        }
        if (!installed) return false;
        struct DownloadItemResult { uint32_t app_id; uint64_t pfid; int32_t eResult; };
        struct ItemInstalled      { uint32_t app_id; uint64_t pfid; };
        DownloadItemResult dr{ app, publishedFileId, /*k_EResultOK*/ 1 };
        ItemInstalled      ii{ app, publishedFileId };
        push_callback(state().user.load(), 3406, &dr, sizeof(dr));
        push_callback(state().user.load(), 3414, &ii, sizeof(ii));
        return true;
    }
    virtual bool      BInitWorkshopForGameServer(uint32_t, const char*) { return false; }
    virtual void      SuspendDownloads(bool)                         {}
    virtual uint64_t  StartPlaytimeTracking(uint64_t*, uint32_t)     { return 0; }
    virtual uint64_t  StopPlaytimeTracking(uint64_t*, uint32_t)      { return 0; }
    virtual uint64_t  StopPlaytimeTrackingForAllItems()              { return 0; }
    virtual uint64_t  AddDependency(uint64_t, uint64_t)              { return 0; }
    virtual uint64_t  RemoveDependency(uint64_t, uint64_t)           { return 0; }
    virtual uint64_t  AddAppDependency(uint64_t, uint32_t)           { return 0; }
    virtual uint64_t  RemoveAppDependency(uint64_t, uint32_t)        { return 0; }
    virtual uint64_t  GetAppDependencies(uint64_t)                   { return 0; }
    virtual uint64_t  DeleteItem(uint64_t)                           { return 0; }
    virtual bool      ShowWorkshopEULA()                             { return false; }
    virtual uint64_t  GetWorkshopEULAStatus()                        { return 0; }
    virtual uint32_t  GetUserContentDescriptorPreferences(int*, uint32_t) { return 0; }
};

class ISteamGameServerStub {
public:
    virtual void      SetProduct(const char*)                        {}
    virtual void      SetGameDescription(const char*)                {}
    virtual void      SetModDir(const char*)                         {}
    virtual void      SetDedicatedServer(bool)                       {}
    virtual void      LogOn(const char*)                             {}
    virtual void      LogOnAnonymous()                               {}
    virtual void      LogOff()                                       {}
    virtual bool      BLoggedOn()                                    { return false; }
    virtual bool      BSecure()                                      { return false; }
    virtual uint64_t  GetSteamID()                                   { return 0; }
    virtual bool      WasRestartRequested()                          { return false; }
    virtual void      SetMaxPlayerCount(int)                         {}
    virtual void      SetBotPlayerCount(int)                         {}
    virtual void      SetServerName(const char*)                     {}
    virtual void      SetMapName(const char*)                        {}
    virtual void      SetPasswordProtected(bool)                     {}
    virtual void      SetSpectatorPort(uint16_t)                     {}
    virtual void      SetSpectatorServerName(const char*)            {}
    virtual void      ClearAllKeyValues()                            {}
    virtual void      SetKeyValue(const char*, const char*)          {}
    virtual void      SetGameTags(const char*)                       {}
    virtual void      SetGameData(const char*)                       {}
    virtual void      SetRegion(const char*)                         {}
    virtual void      SetAdvertiseServerActive(bool)                 {}
    virtual uint64_t  GetAuthSessionTicket(void*, int, uint32_t* pcb, const void*) {
        if (pcb) *pcb = 0;
        return 0;  // k_HAuthTicketInvalid
    }
    virtual int       BeginAuthSession(const void*, int, uint64_t)   { return 5; /*ServerNotConnectedToSteam*/ }
    virtual void      EndAuthSession(uint64_t)                       {}
    virtual void      CancelAuthTicket(uint64_t)                     {}
    virtual int       UserHasLicenseForApp(uint64_t, uint32_t)       { return 2; /*NoAuth*/ }
    virtual bool      RequestUserGroupStatus(uint64_t, uint64_t)     { return false; }
    virtual void      GetGameplayStats()                             {}
    virtual uint64_t  GetServerReputation()                          { return 0; }
    virtual void      GetPublicIP(void* out) {
        if (out) std::memset(out, 0, 16);
    }
    virtual bool      HandleIncomingPacket(const void*, int, uint32_t, uint16_t) { return false; }
    virtual int       GetNextOutgoingPacket(void*, int, uint32_t*, uint16_t*) { return 0; }
    virtual uint64_t  AssociateWithClan(uint64_t)                    { return 0; }
    virtual uint64_t  ComputeNewPlayerCompatibility(uint64_t)        { return 0; }
    virtual bool      SendUserConnectAndAuthenticate_DEPRECATED(uint32_t, const void*, uint32_t, uint64_t*) { return false; }
    virtual uint64_t  CreateUnauthenticatedUserConnection()          { return 0; }
    virtual void      SendUserDisconnect_DEPRECATED(uint64_t)        {}
    virtual bool      BUpdateUserData(uint64_t, const char*, uint32_t) { return false; }
    virtual uint64_t  GetAuthTicketForWebApi(const char*)            { return 0; }
};

class ISteamMusicRemoteStub {
public:
    virtual bool      RegisterSteamMusicRemote(const char*)          { return false; }
    virtual bool      DeregisterSteamMusicRemote()                   { return false; }
    virtual bool      BIsCurrentMusicRemote()                        { return false; }
    virtual bool      BActivationSuccess(bool)                       { return false; }
    virtual bool      SetDisplayName(const char*)                    { return false; }
    virtual bool      SetPNGIcon_64x64(void*, uint32_t)              { return false; }
    virtual bool      EnablePlayPrevious(bool)                       { return false; }
    virtual bool      EnablePlayNext(bool)                           { return false; }
    virtual bool      EnableShuffled(bool)                           { return false; }
    virtual bool      EnableLooped(bool)                             { return false; }
    virtual bool      EnableQueue(bool)                              { return false; }
    virtual bool      EnablePlaylists(bool)                          { return false; }
    virtual bool      UpdatePlaybackStatus(int)                      { return false; }
    virtual bool      UpdateShuffled(bool)                           { return false; }
    virtual bool      UpdateLooped(bool)                             { return false; }
    virtual bool      UpdateVolume(float)                            { return false; }
    virtual bool      CurrentEntryWillChange()                       { return false; }
    virtual bool      CurrentEntryIsAvailable(bool)                  { return false; }
    virtual bool      UpdateCurrentEntryText(const char*)            { return false; }
    virtual bool      UpdateCurrentEntryElapsedSeconds(int)          { return false; }
    virtual bool      UpdateCurrentEntryCoverArt(void*, uint32_t)    { return false; }
    virtual bool      CurrentEntryDidChange()                        { return false; }
    virtual bool      QueueWillChange()                              { return false; }
    virtual bool      ResetQueueEntries()                            { return false; }
    virtual bool      SetQueueEntry(int, int, const char*)           { return false; }
    virtual bool      SetCurrentQueueEntry(int)                      { return false; }
    virtual bool      QueueDidChange()                               { return false; }
    virtual bool      PlaylistWillChange()                           { return false; }
    virtual bool      ResetPlaylistEntries()                         { return false; }
    virtual bool      SetPlaylistEntry(int, int, const char*)        { return false; }
    virtual bool      SetCurrentPlaylistEntry(int)                   { return false; }
    virtual bool      PlaylistDidChange()                            { return false; }
};

class ISteamHTMLSurfaceStub {
public:
    virtual bool      Init()                                         { return false; }
    virtual bool      Shutdown()                                     { return false; }
    virtual uint64_t  CreateBrowser(const char*, const char*)        { return 0; }
    virtual void      RemoveBrowser(uint32_t)                        {}
    virtual void      LoadURL(uint32_t, const char*, const char*)    {}
    virtual void      SetSize(uint32_t, uint32_t, uint32_t)          {}
    virtual void      StopLoad(uint32_t)                             {}
    virtual void      Reload(uint32_t)                               {}
    virtual void      GoBack(uint32_t)                               {}
    virtual void      GoForward(uint32_t)                            {}
    virtual void      AddHeader(uint32_t, const char*, const char*)  {}
    virtual void      ExecuteJavascript(uint32_t, const char*)       {}
    virtual void      MouseUp(uint32_t, int)                         {}
    virtual void      MouseDown(uint32_t, int)                       {}
    virtual void      MouseDoubleClick(uint32_t, int)                {}
    virtual void      MouseMove(uint32_t, int, int)                  {}
    virtual void      MouseWheel(uint32_t, int32_t)                  {}
    virtual void      KeyDown(uint32_t, uint32_t, int)               {}
    virtual void      KeyUp(uint32_t, uint32_t, int)                 {}
    virtual void      KeyChar(uint32_t, uint32_t, int)               {}
    virtual void      SetHorizontalScroll(uint32_t, uint32_t)        {}
    virtual void      SetVerticalScroll(uint32_t, uint32_t)          {}
    virtual void      SetKeyFocus(uint32_t, bool)                    {}
    virtual void      ViewSource(uint32_t)                           {}
    virtual void      CopyToClipboard(uint32_t)                      {}
    virtual void      PasteFromClipboard(uint32_t)                   {}
    virtual void      Find(uint32_t, const char*, bool, bool)        {}
    virtual void      StopFind(uint32_t)                             {}
    virtual void      GetLinkAtPosition(uint32_t, int, int)          {}
    virtual void      SetCookie(const char*, const char*, const char*, const char*, uint32_t, bool, bool) {}
    virtual void      SetPageScaleFactor(uint32_t, float, int, int)  {}
    virtual void      SetBackgroundMode(uint32_t, bool)              {}
    virtual void      SetDPIScalingFactor(uint32_t, float)           {}
    virtual void      OpenDeveloperTools(uint32_t)                   {}
    virtual void      AllowStartRequest(uint32_t, bool)              {}
    virtual void      JSDialogResponse(uint32_t, bool)               {}
    virtual void      FileLoadDialogResponse(uint32_t, const char**) {}
};

class ISteamInputStub {
public:
    virtual bool      Init(bool)                                     { return false; }
    virtual bool      Shutdown()                                     { return false; }
    virtual bool      SetInputActionManifestFilePath(const char*)    { return false; }
    virtual void      RunFrame(bool)                                 {}
    virtual bool      BWaitForData(bool, uint32_t)                   { return false; }
    virtual bool      BNewDataAvailable()                            { return false; }
    virtual int       GetConnectedControllers(uint64_t*)             { return 0; }
    virtual void      EnableDeviceCallbacks()                        {}
    virtual void      EnableActionEventCallbacks(void*)              {}
    virtual uint64_t  GetActionSetHandle(const char*)                { return 0; }
    virtual void      ActivateActionSet(uint64_t, uint64_t)          {}
    virtual uint64_t  GetCurrentActionSet(uint64_t)                  { return 0; }
    virtual void      ActivateActionSetLayer(uint64_t, uint64_t)     {}
    virtual void      DeactivateActionSetLayer(uint64_t, uint64_t)   {}
    virtual void      DeactivateAllActionSetLayers(uint64_t)         {}
    virtual int       GetActiveActionSetLayers(uint64_t, uint64_t*)  { return 0; }
    virtual uint64_t  GetDigitalActionHandle(const char*)            { return 0; }
    virtual void      GetDigitalActionData(uint64_t, uint64_t, void* outData) {
        if (outData) std::memset(outData, 0, 2);
    }
    virtual int       GetDigitalActionOrigins(uint64_t, uint64_t, uint64_t, int*) { return 0; }
    virtual const char* GetStringForDigitalActionName(uint64_t)      { return ""; }
    virtual uint64_t  GetAnalogActionHandle(const char*)             { return 0; }
    virtual void      GetAnalogActionData(uint64_t, uint64_t, void* outData) {
        if (outData) std::memset(outData, 0, 16);
    }
    virtual int       GetAnalogActionOrigins(uint64_t, uint64_t, uint64_t, int*) { return 0; }
    virtual const char* GetGlyphPNGForActionOrigin(int, int, uint32_t) { return ""; }
    virtual const char* GetGlyphSVGForActionOrigin(int, uint32_t)    { return ""; }
    virtual const char* GetGlyphForActionOrigin_Legacy(int)          { return ""; }
    virtual const char* GetStringForActionOrigin(int)                { return ""; }
    virtual const char* GetStringForAnalogActionName(uint64_t)       { return ""; }
    virtual void      StopAnalogActionMomentum(uint64_t, uint64_t)   {}
    virtual void      GetMotionData(uint64_t)                        {}
    virtual void      TriggerVibration(uint64_t, uint16_t, uint16_t) {}
    virtual void      TriggerVibrationExtended(uint64_t, uint16_t, uint16_t, uint16_t, uint16_t) {}
    virtual void      TriggerSimpleHapticEvent(uint64_t, int, uint8_t, char, uint8_t, char) {}
    virtual void      SetLEDColor(uint64_t, uint8_t, uint8_t, uint8_t, uint32_t) {}
    virtual void      Legacy_TriggerHapticPulse(uint64_t, int, uint16_t) {}
    virtual void      Legacy_TriggerRepeatedHapticPulse(uint64_t, int, uint16_t, uint16_t, uint16_t, uint32_t) {}
    virtual bool      ShowBindingPanel(uint64_t)                     { return false; }
    virtual int       GetInputTypeForHandle(uint64_t)                { return 0; /*ESteamInputType_Unknown*/ }
    virtual uint64_t  GetControllerForGamepadIndex(int)              { return 0; }
    virtual int       GetGamepadIndexForController(uint64_t)         { return -1; }
    virtual const char* GetStringForXboxOrigin(int)                  { return ""; }
    virtual const char* GetGlyphForXboxOrigin(int)                   { return ""; }
    virtual int       GetActionOriginFromXboxOrigin(uint64_t, int)   { return 0; }
    virtual int       TranslateActionOrigin(int, int)                { return 0; }
    virtual bool      GetDeviceBindingRevision(uint64_t, int*, int*) { return false; }
    virtual uint32_t  GetRemotePlaySessionID(uint64_t)               { return 0; }
    virtual uint32_t  GetSessionInputConfigurationSettings()         { return 0; }
    virtual void      SetDualSenseTriggerEffect(uint64_t, const void*) {}
};

class ISteamPartiesStub {
public:
    virtual uint32_t  GetNumActiveBeacons()                          { return 0; }
    virtual uint64_t  GetBeaconByIndex(uint32_t)                     { return 0; }
    virtual bool      GetBeaconDetails(uint64_t, uint64_t*, void*, char* meta, int mn) {
        if (meta && mn > 0) meta[0] = '\0';
        return false;
    }
    virtual uint64_t  JoinParty(uint64_t)                            { return 0; }
    virtual bool      GetNumAvailableBeaconLocations(uint32_t* pNum) {
        if (pNum) *pNum = 0;
        return false;
    }
    virtual bool      GetAvailableBeaconLocations(void*, uint32_t)   { return false; }
    virtual uint64_t  CreateBeacon(uint32_t, void*, int, const char*, const char*) { return 0; }
    virtual void      OnReservationCompleted(uint64_t, uint64_t)     {}
    virtual void      CancelReservation(uint64_t, uint64_t)          {}
    virtual uint64_t  ChangeNumOpenSlots(uint64_t, uint32_t)         { return 0; }
    virtual bool      DestroyBeacon(uint64_t)                        { return false; }
    virtual bool      GetBeaconLocationData(void*, int, char* str, int sn) {
        if (str && sn > 0) str[0] = '\0';
        return false;
    }
};

class ISteamRemotePlayStub {
public:
    virtual uint32_t  GetSessionCount()                              { return 0; }
    virtual uint32_t  GetSessionID(int)                              { return 0; }
    virtual uint64_t  GetSessionSteamID(uint32_t)                    { return 0; }
    virtual const char* GetSessionClientName(uint32_t)               { return ""; }
    virtual int       GetSessionClientFormFactor(uint32_t)           { return 0; }
    virtual bool      BGetSessionClientResolution(uint32_t, int* w, int* h) {
        if (w) *w = 0;
        if (h) *h = 0;
        return false;
    }
    virtual bool      BStartRemotePlayTogether(bool)                 { return false; }
    virtual bool      BSendRemotePlayTogetherInvite(uint64_t)        { return false; }
};

class ISteamNetworkingSocketsStub {
public:
    virtual uint32_t CreateListenSocketIP(const void* /*pSteamNetworkingIPAddr*/,
                                          int, const void*)                          { return 0; }
    virtual uint32_t ConnectByIPAddress(const void*, int, const void*)               { return 0; }
    virtual uint32_t CreateListenSocketP2P(int, int, const void*)                    { return 0; }
    virtual uint32_t ConnectP2P(const void* /*identityRemote*/, int, int, const void*) { return 0; }
    virtual int      AcceptConnection(uint32_t)                                      { return 3; }
    virtual bool     CloseConnection(uint32_t, int, const char*, bool)               { return false; }
    virtual bool     CloseListenSocket(uint32_t)                                     { return false; }
    virtual bool     SetConnectionUserData(uint32_t, int64_t)                        { return false; }
    virtual int64_t  GetConnectionUserData(uint32_t)                                 { return -1; }
    virtual void     SetConnectionName(uint32_t, const char*)                        {}
    virtual bool     GetConnectionName(uint32_t, char* buf, int cap) {
        if (buf && cap > 0) buf[0] = '\0';
        return false;
    }
    virtual int      SendMessageToConnection(uint32_t, const void*, uint32_t, int, int64_t*) { return 3; }
    virtual void     SendMessages(int, const void* const*, int64_t*)                 {}
    virtual int      FlushMessagesOnConnection(uint32_t)                             { return 3; }
    virtual int      ReceiveMessagesOnConnection(uint32_t, void** /*ppOutMessages*/, int) { return 0; }
    virtual uint32_t CreatePollGroup()                                               { return 0; }
    virtual bool     DestroyPollGroup(uint32_t)                                      { return false; }
    virtual bool     SetConnectionPollGroup(uint32_t, uint32_t)                      { return false; }
    virtual int      ReceiveMessagesOnPollGroup(uint32_t, void**, int)               { return 0; }
    virtual bool     GetConnectionInfo(uint32_t, void*)                              { return false; }
    virtual int      GetConnectionRealTimeStatus(uint32_t, void*, int, void*)        { return 3; }
    virtual int      GetDetailedConnectionStatus(uint32_t, char* buf, int cap) {
        if (buf && cap > 0) buf[0] = '\0';
        return -1;
    }
    virtual bool     GetListenSocketAddress(uint32_t, void*)                         { return false; }
    virtual bool     CreateSocketPair(uint32_t* a, uint32_t* b, bool, const void*, const void*) {
        if (a) *a = 0;
        if (b) *b = 0;
        return false;
    }
    virtual int      ConfigureConnectionLanes(uint32_t, int, const int*, const uint16_t*) { return 3; }
    virtual bool     GetIdentity(void* pIdentity) {
        if (!pIdentity) return false;
        const uint64_t sid = pushed().steam_id.load();
        if (sid == 0) return false;
        struct NetIdentitySteamIDPrefix {
            int32_t  e_type;
            int32_t  cb_size;
            uint64_t steam_id64;
        };
        std::memset(pIdentity, 0, 136);
        auto* out = reinterpret_cast<NetIdentitySteamIDPrefix*>(pIdentity);
        out->e_type     = 16;  // k_ESteamNetworkingIdentityType_SteamID
        out->cb_size    = sizeof(uint64_t);
        out->steam_id64 = sid;
        return true;
    }
    virtual int      InitAuthentication()                                            { return -102; }
    virtual int      GetAuthenticationStatus(void*)                                  { return -102; }
    virtual bool     ReceivedRelayAuthTicket(const void*, int, void*)                { return false; }
    virtual int      FindRelayAuthTicketForServer(const void*, int, void*)           { return 0; }
    virtual uint32_t ConnectToHostedDedicatedServer(const void*, int, int, const void*) { return 0; }
    virtual uint16_t GetHostedDedicatedServerPort()                                  { return 0; }
    virtual uint32_t GetHostedDedicatedServerPOPID()                                 { return 0; }
    virtual int      GetHostedDedicatedServerAddress(void*)                          { return 3; }
    virtual uint32_t CreateHostedDedicatedServerListenSocket(int, int, const void*)  { return 0; }
    virtual int      GetGameCoordinatorServerLogin(void*, int*, void*)               { return 3; }
    virtual uint32_t ConnectP2PCustomSignaling(void*, const void*, int, int, const void*) { return 0; }
    virtual bool     ReceivedP2PCustomSignal(const void*, int, void*)                { return false; }
    virtual bool     GetCertificateRequest(int*, void*, void*)                       { return false; }
    virtual bool     SetCertificate(const void*, int, void*)                         { return false; }
    virtual void     ResetIdentity(const void*)                                      {}
    virtual void     RunCallbacks()                                                  {}
    virtual bool     BeginAsyncRequestFakeIP(int)                                    { return false; }
    virtual void     GetFakeIP(int, void*)                                           {}
    virtual uint32_t CreateListenSocketP2PFakeIP(int, int, const void*)              { return 0; }
    virtual int      GetRemoteFakeIPForConnection(uint32_t, void*)                   { return 3; }
    virtual void*    CreateFakeUDPPort(int)                                          { return nullptr; }
};

class ISteamNetworkingUtilsStub {
public:
    virtual void*    AllocateMessage(int)                                            { return nullptr; }
    virtual void     InitRelayNetworkAccess()                                        {}
    virtual int      GetRelayNetworkStatus(void*)                                    { return -102; }
    virtual float    GetLocalPingLocation(void*)                                     { return -1.0f; }
    virtual int      EstimatePingTimeBetweenTwoLocations(const void*, const void*)   { return -1; }
    virtual int      EstimatePingTimeFromLocalHost(const void*)                      { return -1; }
    virtual void     ConvertPingLocationToString(const void*, char* buf, int cap) {
        if (buf && cap > 0) buf[0] = '\0';
    }
    virtual bool     ParsePingLocationString(const char*, void*)                     { return false; }
    virtual bool     CheckPingDataUpToDate(float)                                    { return true; }
    virtual int      GetPingToDataCenter(uint32_t, uint32_t*)                        { return -1; }
    virtual int      GetDirectPingToPOP(uint32_t)                                    { return -1; }
    virtual int      GetPOPCount()                                                   { return 0; }
    virtual int      GetPOPList(uint32_t*, int)                                      { return 0; }
    virtual int64_t  GetLocalTimestamp() {
        return std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    }
    virtual void     SetDebugOutputFunction(int, void*)                              {}
    virtual bool     IsFakeIPv4(uint32_t)                                            { return false; }
    virtual int      GetIPv4FakeIPType(uint32_t)                                     { return 0; }
    virtual int      GetRealIdentityForFakeIP(const void*, void*)                    { return 3; }
    virtual bool     SetGlobalConfigValueInt32(int, int)                             { return false; }
    virtual bool     SetGlobalConfigValueFloat(int, float)                           { return false; }
    virtual bool     SetGlobalConfigValueString(int, const char*)                    { return false; }
    virtual bool     SetGlobalConfigValuePtr(int, void*)                             { return false; }
    virtual bool     SetConnectionConfigValueInt32(uint32_t, int, int)               { return false; }
    virtual bool     SetConnectionConfigValueFloat(uint32_t, int, float)             { return false; }
    virtual bool     SetConnectionConfigValueString(uint32_t, int, const char*)      { return false; }
    virtual bool     SetConfigValue(int, int, intptr_t, int, const void*)            { return false; }
    virtual bool     SetConfigValueStruct(const void*, int, intptr_t)                { return false; }
    virtual int      GetConfigValue(int, int, intptr_t, int*, void*, uint64_t*)      { return -1; }
    virtual const char* GetConfigValueInfo(int, int*, int*, int*)                    { return nullptr; }
    virtual int      IterateGenericEditableConfigValues(int, bool)                   { return 0; }
    virtual void     SteamNetworkingIPAddr_ToString(const void* pAddr,
                                                     char* buf, uint32_t cap,
                                                     bool with_port) {
        if (!buf || cap == 0) return;
        buf[0] = '\0';
        if (!pAddr) return;
        const auto* p = reinterpret_cast<const uint8_t*>(pAddr);
        bool v4mapped = true;
        for (int i = 0; i < 10; ++i) if (p[i] != 0) { v4mapped = false; break; }
        if (v4mapped && (p[10] != 0xff || p[11] != 0xff)) v4mapped = false;
        const uint16_t port_be = (uint16_t(p[16]) << 8) | uint16_t(p[17]);
        if (v4mapped) {
            if (with_port) {
                std::snprintf(buf, cap, "%u.%u.%u.%u:%u",
                              p[12], p[13], p[14], p[15], port_be);
            } else {
                std::snprintf(buf, cap, "%u.%u.%u.%u",
                              p[12], p[13], p[14], p[15]);
            }
        } else {
            if (with_port) {
                std::snprintf(buf, cap,
                    "[%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x]:%u",
                    p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7],
                    p[8], p[9], p[10], p[11], p[12], p[13], p[14], p[15],
                    port_be);
            } else {
                std::snprintf(buf, cap,
                    "%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x",
                    p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7],
                    p[8], p[9], p[10], p[11], p[12], p[13], p[14], p[15]);
            }
        }
    }
    virtual bool     SteamNetworkingIPAddr_ParseString(void* pAddr, const char* s) {
        if (!pAddr || !s || !*s) return false;
        unsigned a=0,b=0,c=0,d=0,port=0;
        int matched = std::sscanf(s, "%u.%u.%u.%u:%u", &a, &b, &c, &d, &port);
        if (matched < 4 || a>255 || b>255 || c>255 || d>255 || port>65535) {
            matched = std::sscanf(s, "%u.%u.%u.%u", &a, &b, &c, &d);
            if (matched < 4 || a>255 || b>255 || c>255 || d>255) return false;
            port = 0;
        }
        auto* out = reinterpret_cast<uint8_t*>(pAddr);
        std::memset(out, 0, 18);
        out[10] = 0xff;
        out[11] = 0xff;
        out[12] = static_cast<uint8_t>(a);
        out[13] = static_cast<uint8_t>(b);
        out[14] = static_cast<uint8_t>(c);
        out[15] = static_cast<uint8_t>(d);
        out[16] = static_cast<uint8_t>((port >> 8) & 0xff);
        out[17] = static_cast<uint8_t>(port & 0xff);
        return true;
    }
    virtual int      SteamNetworkingIPAddr_GetFakeIPType(const void*)                { return 0; }
    virtual void     SteamNetworkingIdentity_ToString(const void* pId, char* buf, uint32_t cap) {
        if (!buf || cap == 0) return;
        buf[0] = '\0';
        if (!pId) return;
        struct NetIdentityHead {
            int32_t  e_type;
            int32_t  cb_size;
            uint64_t steam_id64;  // SteamID variant
        };
        const auto* h = reinterpret_cast<const NetIdentityHead*>(pId);
        if (h->e_type == 16 /*SteamID*/) {
            std::snprintf(buf, cap, "steamid:%llu",
                          static_cast<unsigned long long>(h->steam_id64));
        } else if (h->e_type == 1 /*IPAddress*/) {
            char tmp[64] = {};
            SteamNetworkingIPAddr_ToString(
                reinterpret_cast<const uint8_t*>(pId) + 8, tmp, sizeof(tmp), true);
            std::snprintf(buf, cap, "ip:%s", tmp);
        }
    }
    virtual bool     SteamNetworkingIdentity_ParseString(void*, const char*)         { return false; }
};

class ISteamNetworkingMessagesStub {
public:
    virtual int  SendMessageToUser(const void*, const void*, uint32_t, int, int) { return 3; }
    virtual int  ReceiveMessagesOnChannel(int, void**, int)                      { return 0; }
    virtual bool AcceptSessionWithUser(const void*)                              { return false; }
    virtual bool CloseSessionWithUser(const void*)                               { return false; }
    virtual bool CloseChannelWithUser(const void*, int)                          { return false; }
    virtual int  GetSessionConnectionInfo(const void*, void*, void*)             { return 0; }
};


static ISteamUtilsStub        g_steam_utils;
static ISteamUserStub         g_steam_user;
static ISteamAppsStub         g_steam_apps;
static ISteamFriendsStub      g_steam_friends;
static ISteamRemoteStorageStub g_steam_remote_storage;
static ISteamUserStatsStub    g_steam_user_stats;
static ISteamInventoryStub    g_steam_inventory;
static ISteamScreenshotsStub  g_steam_screenshots;
static ISteamMusicStub        g_steam_music;
static ISteamAppListStub      g_steam_app_list;
static ISteamVideoStub        g_steam_video;
static ISteamParentalSettingsStub g_steam_parental;
static ISteamMatchmakingServersStub g_steam_matchmaking_servers;
static ISteamMatchmakingStub  g_steam_matchmaking;
static ISteamNetworkingStub   g_steam_networking;
static ISteamUGCStub          g_steam_ugc;
static ISteamGameServerStub   g_steam_game_server;
static ISteamMusicRemoteStub  g_steam_music_remote;
static ISteamHTMLSurfaceStub  g_steam_html_surface;
static ISteamInputStub        g_steam_input;
static ISteamPartiesStub      g_steam_parties;
static ISteamRemotePlayStub   g_steam_remote_play;
static ISteamNetworkingSocketsStub  g_steam_networking_sockets;
static ISteamNetworkingUtilsStub    g_steam_networking_utils;
static ISteamNetworkingMessagesStub g_steam_networking_messages;

extern "C" void* wn_get_isteam_utils()         { return &g_steam_utils; }
extern "C" void* wn_get_isteam_user()          { return &g_steam_user; }
extern "C" void* wn_get_isteam_apps()          { return &g_steam_apps; }
extern "C" void* wn_get_isteam_friends()       { return &g_steam_friends; }
extern "C" void* wn_get_isteam_remote_storage(){ return &g_steam_remote_storage; }
extern "C" void* wn_get_isteam_user_stats()    { return &g_steam_user_stats; }
extern "C" void* wn_get_isteam_inventory()     { return &g_steam_inventory; }
extern "C" void* wn_get_isteam_screenshots()   { return &g_steam_screenshots; }
extern "C" void* wn_get_isteam_music()         { return &g_steam_music; }
extern "C" void* wn_get_isteam_app_list()      { return &g_steam_app_list; }
extern "C" void* wn_get_isteam_video()         { return &g_steam_video; }
extern "C" void* wn_get_isteam_parental()      { return &g_steam_parental; }
extern "C" void* wn_get_isteam_matchmaking_servers() { return &g_steam_matchmaking_servers; }
extern "C" void* wn_get_isteam_matchmaking() { return &g_steam_matchmaking; }
extern "C" void* wn_get_isteam_networking()  { return &g_steam_networking; }
extern "C" void* wn_get_isteam_ugc()         { return &g_steam_ugc; }
extern "C" void* wn_get_isteam_game_server() { return &g_steam_game_server; }
extern "C" void* wn_get_isteam_music_remote() { return &g_steam_music_remote; }
extern "C" void* wn_get_isteam_html_surface() { return &g_steam_html_surface; }
extern "C" void* wn_get_isteam_input()        { return &g_steam_input; }
extern "C" void* wn_get_isteam_parties()      { return &g_steam_parties; }
extern "C" void* wn_get_isteam_remote_play()  { return &g_steam_remote_play; }
extern "C" void* wn_get_isteam_networking_sockets()  { return &g_steam_networking_sockets; }
extern "C" void* wn_get_isteam_networking_utils()    { return &g_steam_networking_utils; }
extern "C" void* wn_get_isteam_networking_messages() { return &g_steam_networking_messages; }

}  // namespace wn_libsteamclient
