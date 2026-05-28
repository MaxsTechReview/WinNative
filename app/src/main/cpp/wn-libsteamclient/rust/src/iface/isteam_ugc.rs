//! ISteamUGC — 90 slots (isteam_stubs.cpp:3100-3321).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 90;

pub fn instance() -> *mut c_void {
    let _ = noop_v as usize;
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let s = vec![noop_p as usize; N];
        assert_eq!(s.len(), N);
        s
    })
}
