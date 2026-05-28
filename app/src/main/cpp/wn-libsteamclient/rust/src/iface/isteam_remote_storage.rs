//! ISteamRemoteStorage — 59 slots (isteam_stubs.cpp:1246-1698).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 59;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[5]  = noop_v as usize;  // various forget/delete operations
        s[20] = noop_v as usize;  // GetQuota (void return in newer SDKs)
        s[21] = noop_v as usize;
        s[24] = noop_v as usize;  // SetCloudEnabledForApp
        assert_eq!(s.len(), N);
        s
    })
}
