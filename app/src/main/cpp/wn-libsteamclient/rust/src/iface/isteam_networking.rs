//! ISteamNetworking — 22 slots (isteam_stubs.cpp:2953-3098).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 22;

// All 22 slots return non-void scalars/bool; noop_p suffices for every slot.
pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let _ = noop_v as usize;
        let s = vec![noop_p as usize; N];
        assert_eq!(s.len(), N);
        s
    })
}
