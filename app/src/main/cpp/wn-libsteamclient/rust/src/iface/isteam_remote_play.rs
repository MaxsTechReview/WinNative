//! ISteamRemotePlay — 8 slots (isteam_stubs.cpp:3532-3546).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 8;

// All 8 slots return non-void scalars/pointers; noop_p suffices for every slot.
pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let _ = noop_v as usize;
        let s = vec![noop_p as usize; N];
        assert_eq!(s.len(), N);
        s
    })
}
