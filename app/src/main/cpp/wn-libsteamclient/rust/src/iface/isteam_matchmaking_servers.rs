//! ISteamMatchmakingServers — 17 slots (isteam_stubs.cpp:2392-2439).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance, This};
use core::ffi::c_void;

const N: usize = 17;

fn fake_handle() -> *mut c_void { 1usize as *mut c_void }

unsafe extern "C" fn request_servers(
    _t: *mut This, _app: u32, _f: *mut *mut c_void, _n: u32, _cb: *mut c_void,
) -> *mut c_void { fake_handle() }
unsafe extern "C" fn ping_server(
    _t: *mut This, _ip: u32, _port: u16, _cb: *mut c_void,
) -> i32 { -1 }

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0] = request_servers as usize; // RequestInternetServerList
        s[1] = request_servers as usize; // RequestLANServerList (sig slightly diff but x0 return)
        s[2] = request_servers as usize; // RequestFriendsServerList
        s[3] = request_servers as usize; // RequestFavoritesServerList
        s[4] = request_servers as usize; // RequestHistoryServerList
        s[5] = request_servers as usize; // RequestSpectatorServerList
        s[6] = noop_v as usize;          // ReleaseRequest
        s[7] = noop_p as usize;          // GetServerDetails -> void*
        s[8] = noop_v as usize;          // CancelQuery
        s[9] = noop_v as usize;          // RefreshQuery
        s[10] = noop_p as usize;         // IsRefreshing -> bool
        s[11] = noop_p as usize;         // GetServerCount -> int
        s[12] = noop_v as usize;         // RefreshServer
        s[13] = ping_server as usize;    // PingServer -> int(-1)
        s[14] = ping_server as usize;    // PlayerDetails
        s[15] = ping_server as usize;    // ServerRules
        s[16] = noop_v as usize;         // CancelServerQuery
        assert_eq!(s.len(), N);
        s
    })
}
