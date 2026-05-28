//! ISteamGameServer — 42 slots (isteam_stubs.cpp:3323-3372).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 42;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0]  = noop_v as usize; // <slot 0>  SetProduct
        s[1]  = noop_v as usize; // <slot 1>  SetGameDescription
        s[2]  = noop_v as usize; // <slot 2>  SetModDir
        s[3]  = noop_v as usize; // <slot 3>  SetDedicatedServer
        s[4]  = noop_v as usize; // <slot 4>  LogOn
        s[5]  = noop_v as usize; // <slot 5>  LogOnAnonymous
        s[6]  = noop_v as usize; // <slot 6>  LogOff
        s[11] = noop_v as usize; // <slot 11> SetMaxPlayerCount
        s[12] = noop_v as usize; // <slot 12> SetBotPlayerCount
        s[13] = noop_v as usize; // <slot 13> SetServerName
        s[14] = noop_v as usize; // <slot 14> SetMapName
        s[15] = noop_v as usize; // <slot 15> SetPasswordProtected
        s[16] = noop_v as usize; // <slot 16> SetSpectatorPort
        s[17] = noop_v as usize; // <slot 17> SetSpectatorServerName
        s[18] = noop_v as usize; // <slot 18> ClearAllKeyValues
        s[19] = noop_v as usize; // <slot 19> SetKeyValue
        s[20] = noop_v as usize; // <slot 20> SetGameTags
        s[21] = noop_v as usize; // <slot 21> SetGameData
        s[22] = noop_v as usize; // <slot 22> SetRegion
        s[23] = noop_v as usize; // <slot 23> SetAdvertiseServerActive
        s[26] = noop_v as usize; // <slot 26> EndAuthSession
        s[27] = noop_v as usize; // <slot 27> CancelAuthTicket
        s[30] = noop_v as usize; // <slot 30> GetGameplayStats
        s[32] = noop_v as usize; // <slot 32> GetPublicIP
        s[39] = noop_v as usize; // <slot 39> SendUserDisconnect_DEPRECATED
        assert_eq!(s.len(), N);
        s
    })
}
