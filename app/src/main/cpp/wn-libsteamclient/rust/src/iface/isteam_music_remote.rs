//! ISteamMusicRemote — 32 slots (isteam_stubs.cpp:3374-3408).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 32;

// Every slot returns bool; noop_p (x0 = null) decodes as `false` on AArch64.
pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let _ = noop_v as usize;
        let s = vec![noop_p as usize; N];
        assert_eq!(s.len(), N);
        s
    })
}
