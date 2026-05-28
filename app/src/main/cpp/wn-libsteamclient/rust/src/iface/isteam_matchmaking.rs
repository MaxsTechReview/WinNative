//! ISteamMatchmaking — 38 slots (isteam_stubs.cpp:2455-2951).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 38;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[5]  = noop_v as usize;  // AddRequestLobbyListStringFilter
        s[6]  = noop_v as usize;  // AddRequestLobbyListNumericalFilter
        s[7]  = noop_v as usize;  // AddRequestLobbyListNearValueFilter
        s[8]  = noop_v as usize;  // AddRequestLobbyListFilterSlotsAvailable
        s[9]  = noop_v as usize;  // AddRequestLobbyListDistanceFilter
        s[10] = noop_v as usize;  // AddRequestLobbyListResultCountFilter
        s[11] = noop_v as usize;  // AddRequestLobbyListCompatibleMembersFilter
        assert_eq!(s.len(), N);
        s
    })
}
