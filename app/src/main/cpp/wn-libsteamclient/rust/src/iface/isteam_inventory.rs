//! ISteamInventory — 38 slots (isteam_stubs.cpp:2145-2292).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 38;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[5]  = noop_v as usize; // <slot 5>  DestroyResult
        s[17] = noop_v as usize; // <slot 17> SendItemDropHeartbeat
        assert_eq!(s.len(), N);
        s
    })
}
