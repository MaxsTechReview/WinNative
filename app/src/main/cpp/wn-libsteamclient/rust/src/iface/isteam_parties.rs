//! ISteamParties — 12 slots (isteam_stubs.cpp:3507-3530).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 12;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[7] = noop_v as usize; // <slot 7> OnReservationCompleted
        s[8] = noop_v as usize; // <slot 8> CancelReservation
        assert_eq!(s.len(), N);
        s
    })
}
