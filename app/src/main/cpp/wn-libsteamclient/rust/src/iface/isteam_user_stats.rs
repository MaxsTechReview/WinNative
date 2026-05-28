//! ISteamUserStats — 43 slots (isteam_stubs.cpp:1700-2143).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 43;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[15] = noop_v as usize;  // ResetAllStats-ish setters return bool, others void
        s[22] = noop_v as usize;  // various callbacks void slots
        assert_eq!(s.len(), N);
        s
    })
}
