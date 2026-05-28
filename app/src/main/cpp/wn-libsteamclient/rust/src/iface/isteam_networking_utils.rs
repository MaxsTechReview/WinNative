//! ISteamNetworkingUtils — 35 slots (isteam_stubs.cpp:3625-3739).

#![allow(non_snake_case)]

use crate::vtable::{noop_f, noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 35;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[1]  = noop_v as usize; // <slot 1>  InitRelayNetworkAccess
        s[3]  = noop_f as usize; // <slot 3>  GetLocalPingLocation -> float
        s[6]  = noop_v as usize; // <slot 6>  ConvertPingLocationToString
        s[14] = noop_v as usize; // <slot 14> SetDebugOutputFunction
        s[30] = noop_v as usize; // <slot 30> SteamNetworkingIPAddr_ToString
        s[33] = noop_v as usize; // <slot 33> SteamNetworkingIdentity_ToString
        assert_eq!(s.len(), N);
        s
    })
}
