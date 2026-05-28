//! ISteamNetworkingMessages — 6 slots (isteam_stubs.cpp:3741-3749).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 6;

// All 6 slots return non-void scalars; noop_p suffices.
pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let _ = noop_v as usize;
        let s = vec![noop_p as usize; N];
        assert_eq!(s.len(), N);
        s
    })
}
