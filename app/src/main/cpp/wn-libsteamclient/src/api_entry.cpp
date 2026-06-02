
#include "wn_libsteamclient/runtime_state.h"
#include "wn_libsteamclient/callbacks.h"
#include "wn_libsteamclient/callback_registry.h"

#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <vector>

namespace lsc = wn_libsteamclient;

#define WN_TAG  "WnLibSteamClient"
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  WN_TAG, __VA_ARGS__)
#define WN_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  WN_TAG, __VA_ARGS__)
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, WN_TAG, __VA_ARGS__)

extern "C" __attribute__((visibility("default")))
void* CreateInterface(const char* version_name, int* return_code);


extern "C" __attribute__((visibility("default")))
int Steam_CreateSteamPipe(void) {
    auto pipe = lsc::alloc_pipe();
    if (pipe == 0) {
        pipe = lsc::state().pipe.load();
    }
    WN_LOGI("Steam_CreateSteamPipe() -> %d", pipe);
    return pipe;
}

extern "C" __attribute__((visibility("default")))
bool Steam_BReleaseSteamPipe(int pipe) {
    int h_user = lsc::state().user.load();
    namespace cb = wn_libsteamclient::callbacks;
    cb::SteamShutdown sd_payload{};
    lsc::push_callback(h_user, cb::kSteamShutdown, &sd_payload, 0);
    bool ok = lsc::release_pipe(pipe);
    WN_LOGI("Steam_BReleaseSteamPipe(%d) -> %d (SteamShutdown_t emitted)",
            pipe, ok ? 1 : 0);
    return ok;
}

extern "C" __attribute__((visibility("default")))
int Steam_ConnectToGlobalUser(int pipe) {
    auto user = lsc::alloc_global_user(pipe);
    WN_LOGI("Steam_ConnectToGlobalUser(pipe=%d) -> %d", pipe, user);
    return user;
}

extern "C" __attribute__((visibility("default")))
int Steam_CreateGlobalUser(int* pipe_inout) {
    if (!pipe_inout) return 0;
    int pipe = lsc::alloc_pipe();
    if (pipe == 0) pipe = lsc::state().pipe.load();   // already exists; reuse
    int user = lsc::alloc_global_user(pipe);
    WN_LOGI("Steam_CreateGlobalUser(*pipe=%d) -> user=%d", pipe, user);
    return user;
}

extern "C" __attribute__((visibility("default")))
int Steam_CreateLocalUser(int* pipe_inout, int /*account_type*/) {
    return Steam_CreateGlobalUser(pipe_inout);
}

extern "C" __attribute__((visibility("default")))
void Steam_ReleaseUser(int pipe, int user) {
    lsc::release_user(pipe, user);
    WN_LOGI("Steam_ReleaseUser(pipe=%d, user=%d)", pipe, user);
}


extern "C" __attribute__((visibility("default")))
bool Steam_BLoggedOn(int pipe, int user) {
    auto& s = lsc::state();
    if (pipe == 0 || user == 0) return false;
    if (s.pipe.load() != pipe || s.user.load() != user) return false;
    return s.logged_on.load();
}

extern "C" __attribute__((visibility("default")))
bool Steam_BConnected(int pipe, int user) {
    auto& s = lsc::state();
    if (pipe == 0 || user == 0) return false;
    if (s.pipe.load() != pipe || s.user.load() != user) return false;
    return s.connected.load();
}

extern "C" __attribute__((visibility("default")))
void Steam_LogOn(int pipe, int user, uint64_t /*steamid*/) {
    auto& s = lsc::state();
    if (s.pipe.load() == pipe && s.user.load() == user) {
        s.logged_on.store(true);
        s.connected.store(true);
    }
}

extern "C" __attribute__((visibility("default")))
void Steam_LogOff(int pipe, int user) {
    auto& s = lsc::state();
    if (s.pipe.load() == pipe && s.user.load() == user) {
        s.logged_on.store(false);
        s.connected.store(false);
    }
    WN_LOGI("Steam_LogOff(pipe=%d, user=%d)", pipe, user);
}


extern "C" __attribute__((visibility("default")))
bool Steam_BGetCallback(int pipe, void* cb_msg) {
    if (!cb_msg) return false;
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.callback_mu);
    if (s.callback_queue.empty()) return false;
    lsc::CallbackMsg msg = std::move(s.callback_queue.front());
    s.callback_queue.pop_front();
    s.last_param = std::move(msg.body);
    auto* dst = static_cast<uint8_t*>(cb_msg);
    int  h_user   = msg.user;
    int  i_cb     = msg.id;
    void* pubParam = s.last_param.empty() ? nullptr : s.last_param.data();
    int  cubParam = static_cast<int>(s.last_param.size());
    std::memcpy(dst +  0, &h_user,   sizeof(int));
    std::memcpy(dst +  4, &i_cb,     sizeof(int));
    std::memcpy(dst +  8, &pubParam, sizeof(void*));
    std::memcpy(dst + 16, &cubParam, sizeof(int));
    return true;
}

extern "C" __attribute__((visibility("default")))
void Steam_FreeLastCallback(int /*pipe*/) {
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.callback_mu);
    s.last_param.clear();
    s.last_param.shrink_to_fit();
}

extern "C" __attribute__((visibility("default")))
bool Steam_GetAPICallResult(int /*pipe*/, uint64_t hCall,
                            void* pCallback, int cubCallback,
                            int iCallbackExpected, bool* pbFailed) {
    if (hCall == 0) return false;
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.call_results_mu);
    auto it = s.call_results_pending.find(hCall);
    if (it == s.call_results_pending.end()) return false;
    const auto& msg = it->second;
    if (iCallbackExpected != 0 && msg.callback_id != iCallbackExpected) {
        return false;
    }
    if (pCallback && cubCallback > 0 && !msg.body.empty()) {
        size_t n = std::min<size_t>(static_cast<size_t>(cubCallback), msg.body.size());
        std::memcpy(pCallback, msg.body.data(), n);
    }
    if (pbFailed) *pbFailed = msg.io_failure;
    s.call_results_pending.erase(it);
    return true;
}

extern "C" __attribute__((visibility("default")))
bool Steam_IsAPICallCompleted(int /*pipe*/, uint64_t hCall, bool* pbFailed) {
    if (hCall == 0) return false;
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.call_results_mu);
    auto it = s.call_results_pending.find(hCall);
    if (it == s.call_results_pending.end()) return false;
    if (pbFailed) *pbFailed = it->second.io_failure;
    return true;
}


extern "C" __attribute__((visibility("default")))
bool Steam_IsKnownInterface(const char* /*pszInterfaceName*/) {
    return false;
}

extern "C" __attribute__((visibility("default")))
void Steam_NotifyMissingInterface(int /*pipe*/, const char* iface) {
    WN_LOGW("Steam_NotifyMissingInterface: %s", iface ? iface : "(null)");
}

extern "C" __attribute__((visibility("default")))
void Steam_SetLocalIPBinding(int /*ip*/, int /*port*/) {}

extern "C" __attribute__((visibility("default")))
void Steam_ReleaseThreadLocalMemory(int /*bThreadExit*/) {}

extern "C" __attribute__((visibility("default")))
int Steam_GetGSHandle(int /*pipe*/, int /*user*/) { return 0; }

extern "C" __attribute__((visibility("default")))
bool Steam_InitiateGameConnection(int /*pipe*/, int /*user*/,
                                  void* /*pAuthBlob*/, int /*cbMaxAuthBlob*/,
                                  uint64_t /*steamIDGameServer*/,
                                  uint32_t /*unIPServer*/,
                                  uint16_t /*usPortServer*/,
                                  bool /*bSecure*/) {
    return false;
}

extern "C" __attribute__((visibility("default")))
void Steam_TerminateGameConnection(int /*pipe*/, int /*user*/,
                                   uint32_t /*unIPServer*/,
                                   uint16_t /*usPortServer*/) {}


extern "C" __attribute__((visibility("default"))) bool   Steam_GSBLoggedOn  (int, int) { return false; }
extern "C" __attribute__((visibility("default"))) bool   Steam_GSBSecure   (int, int) { return false; }
extern "C" __attribute__((visibility("default"))) uint64_t Steam_GSGetSteamID(int, int) { return 0; }
extern "C" __attribute__((visibility("default"))) void   Steam_GSLogOff    (int, int) {}
extern "C" __attribute__((visibility("default"))) bool   Steam_GSLogOn     (int, int, uint64_t, uint32_t, uint16_t, uint16_t, int, bool) { return false; }
extern "C" __attribute__((visibility("default"))) void   Steam_GSRemoveUserConnect       (int, int, uint64_t) {}
extern "C" __attribute__((visibility("default"))) bool   Steam_GSSendSteam2UserConnect   (int, int, uint64_t, uint32_t, uint32_t, uint16_t, const void*, int) { return false; }
extern "C" __attribute__((visibility("default"))) bool   Steam_GSSendSteam3UserConnect   (int, int, uint64_t, uint32_t, const void*, int) { return false; }
extern "C" __attribute__((visibility("default"))) void   Steam_GSSendUserDisconnect      (int, int, uint64_t, uint32_t) {}
extern "C" __attribute__((visibility("default"))) bool   Steam_GSSendUserStatusResponse  (int, int, uint64_t, int, const void*, int) { return false; }
extern "C" __attribute__((visibility("default"))) void   Steam_GSSetServerType           (int, int, uint32_t, uint32_t, uint16_t, uint16_t, uint16_t, const char*, const char*, bool) {}
extern "C" __attribute__((visibility("default"))) void   Steam_GSSetSpawnCount           (int, int, uint32_t) {}
extern "C" __attribute__((visibility("default"))) bool   Steam_GSUpdateStatus            (int, int, int, int, int, const char*, const char*, const char*) { return false; }
extern "C" __attribute__((visibility("default"))) bool   Steam_GSGetSteam2GetEncryptionKeyToSendToNewClient(int, int, void*, uint32_t*, uint32_t) { return false; }


extern "C" __attribute__((visibility("default")))
bool SteamAPI_RestartAppIfNecessary(uint32_t unOwnAppID) {
    WN_LOGI("SteamAPI_RestartAppIfNecessary(appId=%u) -> false (no restart needed)",
            static_cast<unsigned>(unOwnAppID));
    return false;
}

extern "C" __attribute__((visibility("default")))
bool SteamAPI_Init(void) {
    int pipe = lsc::alloc_pipe();
    if (pipe == 0) pipe = lsc::state().pipe.load();
    int user = lsc::alloc_global_user(pipe);
    (void)user;
    WN_LOGI("SteamAPI_Init() -> true (pipe=%d user=%d)", pipe, user);
    return true;
}

extern "C" __attribute__((visibility("default")))
int SteamAPI_InitEx(char* p_outErrMsg) {
    SteamAPI_Init();
    if (p_outErrMsg) p_outErrMsg[0] = '\0';
    return 0;  // k_ESteamAPIInitResult_OK
}

extern "C" __attribute__((visibility("default")))
void SteamAPI_Shutdown(void) {
    int pipe = lsc::state().pipe.load();
    if (pipe != 0) {
        Steam_BReleaseSteamPipe(pipe);
    }
    WN_LOGI("SteamAPI_Shutdown()");
}

extern "C" __attribute__((visibility("default")))
bool SteamAPI_IsSteamRunning(void) { return true; }

extern "C" __attribute__((visibility("default")))
int SteamAPI_GetHSteamPipe(void) { return lsc::state().pipe.load(); }
extern "C" __attribute__((visibility("default")))
int SteamAPI_GetHSteamUser(void) { return lsc::state().user.load(); }

extern "C" __attribute__((visibility("default")))
void SteamAPI_ReleaseCurrentThreadMemory(void) {}

extern "C" __attribute__((visibility("default")))
void SteamAPI_SetTryCatchCallbacks(bool /*bTryCatchCallbacks*/) {}

extern "C" __attribute__((visibility("default")))
void SteamAPI_WriteMiniDump(uint32_t /*uStructuredExceptionCode*/,
                            void*    /*pvExceptionInfo*/,
                            uint32_t /*uBuildID*/) {}

extern "C" __attribute__((visibility("default")))
void SteamAPI_RegisterCallback(void* pCallback, int iCallback) {
    lsc::register_callback(pCallback, iCallback);
}
extern "C" __attribute__((visibility("default")))
void SteamAPI_UnregisterCallback(void* pCallback) {
    lsc::unregister_callback(pCallback);
}
extern "C" __attribute__((visibility("default")))
void SteamAPI_RegisterCallResult(void* pCallback, uint64_t hAPICall) {
    lsc::register_call_result(pCallback, hAPICall);
}
extern "C" __attribute__((visibility("default")))
void SteamAPI_UnregisterCallResult(void* pCallback, uint64_t hAPICall) {
    lsc::unregister_call_result(pCallback, hAPICall);
}

extern "C" __attribute__((visibility("default")))
void SteamAPI_RunCallbacks(void) {
    auto& s = lsc::state();

    for (;;) {
        lsc::CallbackMsg msg;
        {
            std::lock_guard<std::mutex> lk(s.callback_mu);
            if (s.callback_queue.empty()) break;
            msg = std::move(s.callback_queue.front());
            s.callback_queue.pop_front();
        }
        auto cbs = lsc::find_callbacks(msg.id);
        for (void* cb : cbs) {
            using RunFn = void (*)(void* /*this*/, void* /*pvParam*/);
            void* payload = msg.body.empty() ? nullptr : msg.body.data();
            long** vtable_ptr = reinterpret_cast<long**>(cb);
            long*  vtable     = *vtable_ptr;
            auto   run        = reinterpret_cast<RunFn>(vtable[0]);
            run(cb, payload);
        }
    }

    struct PendingDispatch {
        uint64_t              h_call;
        bool                  io_failure;
        std::vector<uint8_t>  body;
        std::vector<void*>    cbs;
    };
    std::vector<PendingDispatch> to_dispatch;
    {
        std::lock_guard<std::mutex> lk(s.call_results_mu);
        for (auto it = s.call_results_pending.begin();
             it != s.call_results_pending.end(); ) {
            auto cbs = lsc::find_call_result_cbs(it->first);
            if (cbs.empty()) {
                ++it;
                continue;
            }
            to_dispatch.push_back({
                it->first, it->second.io_failure,
                std::move(it->second.body), std::move(cbs)});
            it = s.call_results_pending.erase(it);
        }
    }
    for (auto& d : to_dispatch) {
        void* payload = d.body.empty() ? nullptr : d.body.data();
        for (void* cb : d.cbs) {
            using RunResultFn = void (*)(void* /*this*/, void* /*pvParam*/,
                                         bool /*bIOFailure*/,
                                         uint64_t /*hSteamAPICall*/);
            long** vtable_ptr = reinterpret_cast<long**>(cb);
            long*  vtable     = *vtable_ptr;
            auto   run        = reinterpret_cast<RunResultFn>(vtable[1]);
            run(cb, payload, d.io_failure, d.h_call);
        }
    }
}

extern "C" __attribute__((visibility("default")))
const char* SteamAPI_GetSteamInstallPath(void) { return nullptr; }

extern "C" __attribute__((visibility("default")))
void* SteamClient(void) {
    int err = 0;
    return CreateInterface("SteamClient020", &err);
}

extern "C" __attribute__((visibility("default")))
bool SteamGameServer_Init(uint32_t /*unIP*/, uint16_t /*usGamePort*/,
                          uint16_t /*usQueryPort*/, int /*eServerMode*/,
                          const char* /*pchVersionString*/) {
    WN_LOGI("SteamGameServer_Init -> false (game-server mode not implemented)");
    return false;
}
extern "C" __attribute__((visibility("default")))
void SteamGameServer_Shutdown(void) {}
extern "C" __attribute__((visibility("default")))
bool SteamGameServer_BSecure(void) { return false; }
extern "C" __attribute__((visibility("default")))
uint64_t SteamGameServer_GetSteamID(void) { return 0; }
extern "C" __attribute__((visibility("default")))
int SteamGameServer_GetHSteamPipe(void) { return 0; }
extern "C" __attribute__((visibility("default")))
int SteamGameServer_GetHSteamUser(void) { return 0; }
extern "C" __attribute__((visibility("default")))
void SteamGameServer_RunCallbacks(void) {}

namespace {
struct CallbackMsgWire {
    int32_t   h_steam_user;
    int32_t   i_callback;
    uint8_t*  pub_param;
    int32_t   cub_param;
    int32_t   _pad;
};
static_assert(sizeof(CallbackMsgWire) == 24, "CallbackMsg_t must be 24B");

std::atomic<bool> g_manual_dispatch_active{false};
}  // namespace

extern "C" __attribute__((visibility("default")))
void Breakpad_SteamMiniDumpInit(uint32_t /*unAppID*/,
                                 const char* /*pchVersion*/,
                                 const char* /*pchDate*/) {}
extern "C" __attribute__((visibility("default")))
void Breakpad_SteamSendMiniDump(void* /*pvException*/,
                                 uint32_t /*ulSeconds*/,
                                 const char* /*pchAssertMsg*/) {}
extern "C" __attribute__((visibility("default")))
void Breakpad_SteamSetAppID(uint32_t /*unAppID*/) {}
extern "C" __attribute__((visibility("default")))
void Breakpad_SteamSetSteamID(uint64_t /*ulSteamID*/) {}
extern "C" __attribute__((visibility("default")))
void Breakpad_SteamWriteMiniDumpSetComment(const char* /*pchMsg*/) {}
extern "C" __attribute__((visibility("default")))
void Breakpad_SteamWriteMiniDumpUsingExceptionInfoWithBuildId(
        unsigned int /*uStructuredExceptionCode*/,
        void* /*pExceptionInfo*/,
        unsigned int /*uBuildID*/) {}

extern "C" __attribute__((visibility("default")))
void SteamAPI_ManualDispatch_Init(void) {
    g_manual_dispatch_active.store(true, std::memory_order_release);
    WN_LOGI("SteamAPI_ManualDispatch_Init() — manual callback dispatch armed");
}

extern "C" __attribute__((visibility("default")))
void SteamAPI_ManualDispatch_RunFrame(int /*hSteamPipe*/) {
}

extern "C" __attribute__((visibility("default")))
bool SteamAPI_ManualDispatch_GetNextCallback(int /*hSteamPipe*/, void* p_msg_out) {
    if (!p_msg_out) return false;
    auto& s = lsc::state();
    lsc::CallbackMsg msg;
    {
        std::lock_guard<std::mutex> lk(s.callback_mu);
        if (s.callback_queue.empty()) return false;
        msg = std::move(s.callback_queue.front());
        s.callback_queue.pop_front();
        s.last_param = std::move(msg.body);
    }
    auto* out = static_cast<CallbackMsgWire*>(p_msg_out);
    out->h_steam_user = msg.user;
    out->i_callback   = msg.id;
    out->pub_param    = s.last_param.empty() ? nullptr : s.last_param.data();
    out->cub_param    = static_cast<int32_t>(s.last_param.size());
    out->_pad         = 0;
    return true;
}

extern "C" __attribute__((visibility("default")))
void SteamAPI_ManualDispatch_FreeLastCallback(int /*hSteamPipe*/) {
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.callback_mu);
    s.last_param.clear();
}

extern "C" __attribute__((visibility("default")))
bool SteamAPI_ManualDispatch_GetAPICallResult(int /*hSteamPipe*/,
                                               uint64_t hCall,
                                               void* p_callback,
                                               int cb_callback,
                                               int i_callback_expected,
                                               bool* pb_failed) {
    auto& s = lsc::state();
    std::lock_guard<std::mutex> lk(s.call_results_mu);
    auto it = s.call_results_pending.find(hCall);
    if (it == s.call_results_pending.end()) return false;
    auto msg = std::move(it->second);
    s.call_results_pending.erase(it);
    if (i_callback_expected != 0 && msg.callback_id != i_callback_expected) {
        const int got = msg.callback_id;
        s.call_results_pending[hCall] = std::move(msg);
        WN_LOGI("ManualDispatch_GetAPICallResult: hCall=0x%llx callback mismatch "
                "(expected=%d got=%d) — re-queueing",
                (unsigned long long)hCall, i_callback_expected, got);
        return false;
    }
    if (p_callback && cb_callback > 0 && !msg.body.empty()) {
        const int n = std::min<int>(cb_callback, static_cast<int>(msg.body.size()));
        std::memcpy(p_callback, msg.body.data(), static_cast<size_t>(n));
    }
    if (pb_failed) *pb_failed = msg.io_failure;
    return true;
}
