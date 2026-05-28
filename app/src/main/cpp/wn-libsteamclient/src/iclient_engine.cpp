
#include "wn_libsteamclient/runtime_state.h"

#include <android/log.h>
#include <cstdint>
#include <cstring>

namespace wn_libsteamclient {

#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "WnLibSteamClient", __VA_ARGS__)
#define WN_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  "WnLibSteamClient", __VA_ARGS__)


class IClientUserImpl {
public:
    virtual int      GetHSteamUser()                                 { return state().user.load(); } // 0  / 0x00

    virtual void     SetSteamID(uint64_t sid)                        {                                // 1  / 0x08
        pushed().steam_id.store(sid);
        pushed().account_id.store(static_cast<uint32_t>(sid & 0xFFFFFFFFu));
        WN_LOGI("IClientUser.SetSteamID(%llu)",
                static_cast<unsigned long long>(sid));
    }

    virtual void  _slot02()  {}   // 2  / 0x010
    virtual void  _slot03()  {}   // 3  / 0x018
    virtual void  _slot04()  {}   // 4  / 0x020
    virtual void  _slot05()  {}   // 5  / 0x028
    virtual void  _slot06()  {}   // 6  / 0x030
    virtual void  _slot07()  {}   // 7  / 0x038
    virtual void  _slot08()  {}   // 8  / 0x040
    virtual void  _slot09()  {}   // 9  / 0x048
    virtual void  _slot10()  {}   // 10 / 0x050
    virtual void  _slot11()  {}   // 11 / 0x058
    virtual void  _slot12()  {}   // 12 / 0x060
    virtual void  _slot13()  {}   // 13 / 0x068
    virtual void  _slot14()  {}   // 14 / 0x070
    virtual void  _slot15()  {}   // 15 / 0x078
    virtual void  _slot16()  {}   // 16 / 0x080
    virtual void  _slot17()  {}   // 17 / 0x088
    virtual void  _slot18()  {}   // 18 / 0x090
    virtual void  _slot19()  {}   // 19 / 0x098
    virtual void  _slot20()  {}   // 20 / 0x0A0
    virtual void  _slot21()  {}   // 21 / 0x0A8
    virtual void  _slot22()  {}   // 22 / 0x0B0
    virtual void  _slot23()  {}   // 23 / 0x0B8
    virtual void  _slot24()  {}   // 24 / 0x0C0
    virtual void  _slot25()  {}   // 25 / 0x0C8
    virtual void  _slot26()  {}   // 26 / 0x0D0
    virtual void  _slot27()  {}   // 27 / 0x0D8
    virtual void  _slot28()  {}   // 28 / 0x0E0
    virtual void  _slot29()  {}   // 29 / 0x0E8
    virtual void  _slot30()  {}   // 30 / 0x0F0
    virtual void  _slot31()  {}   // 31 / 0x0F8
    virtual void  _slot32()  {}   // 32 / 0x100
    virtual void  _slot33()  {}   // 33 / 0x108
    virtual void  _slot34()  {}   // 34 / 0x110
    virtual void  _slot35()  {}   // 35 / 0x118
    virtual void  _slot36()  {}   // 36 / 0x120
    virtual void  _slot37()  {}   // 37 / 0x128
    virtual void  _slot38()  {}   // 38 / 0x130
    virtual void  _slot39()  {}   // 39 / 0x138
    virtual void  _slot40()  {}   // 40 / 0x140
    virtual void  _slot41()  {}   // 41 / 0x148
    virtual void  _slot42()  {}   // 42 / 0x150
    virtual void  _slot43()  {}   // 43 / 0x158
    virtual void  _slot44()  {}   // 44 / 0x160
    virtual void  _slot45()  {}   // 45 / 0x168
    virtual void  _slot46()  {}   // 46 / 0x170
    virtual void  _slot47()  {}   // 47 / 0x178
    virtual void  _slot48()  {}   // 48 / 0x180

    virtual bool     IsAccountLoggedIn(const char* account) {                                    // 49 / 0x188
        WN_LOGI("IClientUser.IsAccountLoggedIn(%s) -> 0 (no persisted session yet)",
                account ? account : "(null)");
        return false;
    }

    virtual void     SetAccount(const char* account, const char* /*password*/, int /*remember*/) { // 50 / 0x190
        WN_LOGI("IClientUser.SetAccount(%s)", account ? account : "(null)");
    }

    virtual void  _slot51()  {}   // 51 / 0x198
    virtual void  _slot52()  {}   // 52 / 0x1A0
    virtual void  _slot53()  {}   // 53 / 0x1A8

    virtual bool     SetLoginInformation(const char* account,
                                         const char* /*password*/,
                                         int /*remember*/) {                                     // 54 / 0x1B0
        WN_LOGI("IClientUser.SetLoginInformation(%s, \"\", *)",
                account ? account : "(null)");
        return true;
    }

    virtual void  _slot55()  {}   // 55 / 0x1B8

    virtual void     LogonWithRefreshToken(const char* token, const char* account) {              // 56 / 0x1C0
        WN_LOGI("IClientUser.LogonWithRefreshToken(token=%zu bytes, account=%s)",
                token ? std::strlen(token) : 0,
                account ? account : "(null)");
        set_logged_on(true);
    }
};



class IClientEngineImpl {
public:
    virtual void* _slot00()                          { return nullptr; }  // 0  / 0x00
    virtual void* _slot01()                          { return nullptr; }  // 1
    virtual void* _slot02()                          { return nullptr; }  // 2
    virtual void* _slot03()                          { return nullptr; }  // 3
    virtual void* _slot04()                          { return nullptr; }  // 4
    virtual void* _slot05()                          { return nullptr; }  // 5
    virtual void* _slot06()                          { return nullptr; }  // 6
    virtual void* _slot07()                          { return nullptr; }  // 7

    virtual void* GetIClientUser(int /*user*/, int /*pipe*/);             // 8

    virtual void* _slot09()  { return nullptr; }
    virtual void* _slot10()  { return nullptr; }
    virtual void* _slot11()  { return nullptr; }
    virtual void* _slot12()  { return nullptr; }
    virtual void* _slot13()  { return nullptr; }
    virtual void* _slot14()  { return nullptr; }
    virtual void* _slot15()  { return nullptr; }
    virtual void* _slot16()  { return nullptr; }
    virtual void* _slot17()  { return nullptr; }
    virtual void* _slot18()  { return nullptr; }
    virtual void* _slot19()  { return nullptr; }
    virtual void* _slot20()  { return nullptr; }
    virtual void* _slot21()  { return nullptr; }
    virtual void* _slot22()  { return nullptr; }
    virtual void* _slot23()  { return nullptr; }
    virtual void* _slot24()  { return nullptr; }
    virtual void* _slot25()  { return nullptr; }
    virtual void* _slot26()  { return nullptr; }
    virtual void* _slot27()  { return nullptr; }
    virtual void* _slot28()  { return nullptr; }
    virtual void* _slot29()  { return nullptr; }
    virtual void* _slot30()  { return nullptr; }
    virtual void* _slot31()  { return nullptr; }
    virtual void* _slot32()  { return nullptr; }
    virtual void* _slot33()  { return nullptr; }
    virtual void* _slot34()  { return nullptr; }
    virtual void* _slot35()  { return nullptr; }
    virtual void* _slot36()  { return nullptr; }
    virtual void* _slot37()  { return nullptr; }
    virtual void* _slot38()  { return nullptr; }
    virtual void* _slot39()  { return nullptr; }
    virtual void* _slot40()  { return nullptr; }
    virtual void* _slot41()  { return nullptr; }
    virtual void* _slot42()  { return nullptr; }
    virtual void* _slot43()  { return nullptr; }
    virtual void* _slot44()  { return nullptr; }
    virtual void* _slot45()  { return nullptr; }
    virtual void* _slot46()  { return nullptr; }
    virtual void* _slot47()  { return nullptr; }
    virtual void* _slot48()  { return nullptr; }
    virtual void* _slot49()  { return nullptr; }
    virtual void* _slot50()  { return nullptr; }
    virtual void* _slot51()  { return nullptr; }
    virtual void* _slot52()  { return nullptr; }
    virtual void* _slot53()  { return nullptr; }
    virtual void* _slot54()  { return nullptr; }
    virtual void* _slot55()  { return nullptr; }
    virtual void* _slot56()  { return nullptr; }
    virtual void* _slot57()  { return nullptr; }
    virtual void* _slot58()  { return nullptr; }
    virtual void* _slot59()  { return nullptr; }
    virtual void* _slot60()  { return nullptr; }
    virtual void* _slot61()  { return nullptr; }
    virtual void* _slot62()  { return nullptr; }
    virtual void* _slot63()  { return nullptr; }
    virtual void* _slot64()  { return nullptr; }
    virtual void* _slot65()  { return nullptr; }
    virtual void* _slot66()  { return nullptr; }
    virtual void* _slot67()  { return nullptr; }
    virtual void* _slot68()  { return nullptr; }
    virtual void* _slot69()  { return nullptr; }
    virtual void* _slot70()  { return nullptr; }
    virtual void* _slot71()  { return nullptr; }
    virtual void* _slot72()  { return nullptr; }
    virtual void* _slot73()  { return nullptr; }
    virtual void* _slot74()  { return nullptr; }
    virtual void* _slot75()  { return nullptr; }
    virtual void* _slot76()  { return nullptr; }
    virtual void* _slot77()  { return nullptr; }
    virtual void* _slot78()  { return nullptr; }
    virtual void* _slot79()  { return nullptr; }
};


static IClientUserImpl   g_client_user;
static IClientEngineImpl g_client_engine;

void* IClientEngineImpl::GetIClientUser(int /*user*/, int /*pipe*/) {
    return &g_client_user;
}

extern "C" void* wn_get_iclient_engine() {
    return &g_client_engine;
}

}  // namespace wn_libsteamclient
