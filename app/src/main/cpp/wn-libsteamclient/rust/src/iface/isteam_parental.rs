//! ISteamParentalSettings — 6 slots (isteam_stubs.cpp:2382-2390).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, LazyInstance};
use core::ffi::c_void;

const N: usize = 6;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let s = vec![noop_p as usize; N];
        assert_eq!(s.len(), N);
        s
    })
}
