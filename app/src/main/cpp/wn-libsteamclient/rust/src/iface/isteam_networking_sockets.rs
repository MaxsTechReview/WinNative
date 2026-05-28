//! ISteamNetworkingSockets — 47 slots (isteam_stubs.cpp:3548-3623).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 47;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[9]  = noop_v as usize; // <slot 9>  SetConnectionName
        s[12] = noop_v as usize; // <slot 12> SendMessages
        s[40] = noop_v as usize; // <slot 40> ResetIdentity
        s[41] = noop_v as usize; // <slot 41> RunCallbacks
        s[43] = noop_v as usize; // <slot 43> GetFakeIP
        assert_eq!(s.len(), N);
        s
    })
}
