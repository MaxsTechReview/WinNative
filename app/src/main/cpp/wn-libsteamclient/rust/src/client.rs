//! Port of `isteam_client.cpp` — `CreateInterface` dispatcher and the
//! `ISteamClient` vtable (29 slots).

#![allow(non_snake_case)]

use crate::iclient_engine;
use crate::iface;
use crate::log;
use crate::state;
use crate::vtable::{noop_p, noop_v, This};
use core::ffi::{c_char, c_void};
use std::sync::atomic::Ordering;

// ---- ISteamClient vtable ---------------------------------------------------

#[repr(C)]
pub struct ISteamClientVtbl {
    pub create_steam_pipe:               unsafe extern "C" fn(*mut This) -> i32,                                    // 0
    pub b_release_steam_pipe:            unsafe extern "C" fn(*mut This, i32) -> bool,                              // 1
    pub connect_to_global_user:          unsafe extern "C" fn(*mut This, i32) -> i32,                               // 2
    pub create_local_user:               unsafe extern "C" fn(*mut This, *mut i32, i32) -> i32,                     // 3
    pub release_user:                    unsafe extern "C" fn(*mut This, i32, i32),                                 // 4
    pub get_isteam_user:                 unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 5
    pub get_isteam_game_server:          unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 6
    pub set_local_ip_binding:            unsafe extern "C" fn(*mut This, u32, u16),                                 // 7
    pub get_isteam_friends:              unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 8
    pub get_isteam_utils:                unsafe extern "C" fn(*mut This, i32, *const c_char) -> *mut c_void,        // 9
    pub get_isteam_matchmaking:          unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 10
    pub get_isteam_matchmaking_servers:  unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 11
    pub get_isteam_generic_interface:    unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 12
    pub get_isteam_user_stats:           unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 13
    pub get_isteam_apps:                 unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 14
    pub get_isteam_networking:           unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 15
    pub get_isteam_remote_storage:       unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 16
    pub get_isteam_screenshots:          unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 17
    pub get_isteam_ugc:                  unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 18
    pub get_isteam_app_list:             unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 19
    pub get_isteam_music:                unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 20
    pub get_isteam_music_remote:         unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 21
    pub get_isteam_html_surface:         unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 22
    pub set_post_api_result_in_process:  unsafe extern "C" fn(*mut This, *mut c_void),                              // 23
    pub remove_post_api_result_in_process: unsafe extern "C" fn(*mut This, *mut c_void),                            // 24
    pub set_check_callback_registered_in_process: unsafe extern "C" fn(*mut This, *mut c_void),                     // 25
    pub get_isteam_inventory:            unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 26
    pub get_isteam_video:                unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 27
    pub get_isteam_parental:             unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 28
    pub get_isteam_input:                unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 29
    pub get_isteam_parties:              unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 30
    pub get_isteam_remote_play:          unsafe extern "C" fn(*mut This, i32, i32, *const c_char) -> *mut c_void,   // 31
}

const EXPECTED_SLOTS: usize = 32;
const _: () = assert!(
    core::mem::size_of::<ISteamClientVtbl>() == EXPECTED_SLOTS * 8,
    "ISteamClient vtable slot count drift"
);

unsafe extern "C" fn isc_create_steam_pipe(_this: *mut This) -> i32 {
    let mut pipe = state::alloc_pipe();
    if pipe == 0 {
        pipe = state::state().pipe.load(Ordering::SeqCst);
    }
    pipe
}
unsafe extern "C" fn isc_release_pipe(_this: *mut This, pipe: i32) -> bool {
    state::release_pipe(pipe)
}
unsafe extern "C" fn isc_connect_global(_this: *mut This, pipe: i32) -> i32 {
    state::alloc_global_user(pipe)
}
unsafe extern "C" fn isc_create_local_user(
    _this: *mut This,
    pipe_inout: *mut i32,
    _type: i32,
) -> i32 {
    if pipe_inout.is_null() {
        return 0;
    }
    let mut p = state::alloc_pipe();
    if p == 0 {
        p = state::state().pipe.load(Ordering::SeqCst);
    }
    state::alloc_global_user(p)
}
unsafe extern "C" fn isc_release_user(_this: *mut This, pipe: i32, user: i32) {
    state::release_user(pipe, user);
}
unsafe extern "C" fn isc_get_user(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_user::instance()
}
unsafe extern "C" fn isc_get_game_server(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_game_server::instance()
}
unsafe extern "C" fn isc_set_local_ip_binding(_this: *mut This, _ip: u32, _port: u16) {}
unsafe extern "C" fn isc_get_friends(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_friends::instance()
}
unsafe extern "C" fn isc_get_utils(_this: *mut This, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_utils::instance()
}
unsafe extern "C" fn isc_get_matchmaking(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_matchmaking::instance()
}
unsafe extern "C" fn isc_get_matchmaking_servers(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_matchmaking_servers::instance()
}
unsafe extern "C" fn isc_get_generic(_this: *mut This, _u: i32, _p: i32, version: *const c_char) -> *mut c_void {
    let mut err = 0i32;
    unsafe { CreateInterface(version, &mut err) }
}
unsafe extern "C" fn isc_get_user_stats(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_user_stats::instance()
}
unsafe extern "C" fn isc_get_apps(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_apps::instance()
}
unsafe extern "C" fn isc_get_networking(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_networking::instance()
}
unsafe extern "C" fn isc_get_remote_storage(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_remote_storage::instance()
}
unsafe extern "C" fn isc_get_screenshots(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_screenshots::instance()
}
unsafe extern "C" fn isc_get_ugc(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_ugc::instance()
}
unsafe extern "C" fn isc_get_app_list(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_app_list::instance()
}
unsafe extern "C" fn isc_get_music(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_music::instance()
}
unsafe extern "C" fn isc_get_music_remote(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_music_remote::instance()
}
unsafe extern "C" fn isc_get_html_surface(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_html_surface::instance()
}
unsafe extern "C" fn isc_set_post_api(_this: *mut This, _p: *mut c_void) {}
unsafe extern "C" fn isc_remove_post_api(_this: *mut This, _p: *mut c_void) {}
unsafe extern "C" fn isc_set_check_callback_registered(_this: *mut This, _p: *mut c_void) {}
unsafe extern "C" fn isc_get_inventory(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_inventory::instance()
}
unsafe extern "C" fn isc_get_video(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_video::instance()
}
unsafe extern "C" fn isc_get_parental(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_parental::instance()
}
unsafe extern "C" fn isc_get_input(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_input::instance()
}
unsafe extern "C" fn isc_get_parties(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_parties::instance()
}
unsafe extern "C" fn isc_get_remote_play(_this: *mut This, _u: i32, _p: i32, _v: *const c_char) -> *mut c_void {
    iface::isteam_remote_play::instance()
}

static VTBL: ISteamClientVtbl = ISteamClientVtbl {
    create_steam_pipe: isc_create_steam_pipe,
    b_release_steam_pipe: isc_release_pipe,
    connect_to_global_user: isc_connect_global,
    create_local_user: isc_create_local_user,
    release_user: isc_release_user,
    get_isteam_user: isc_get_user,
    get_isteam_game_server: isc_get_game_server,
    set_local_ip_binding: isc_set_local_ip_binding,
    get_isteam_friends: isc_get_friends,
    get_isteam_utils: isc_get_utils,
    get_isteam_matchmaking: isc_get_matchmaking,
    get_isteam_matchmaking_servers: isc_get_matchmaking_servers,
    get_isteam_generic_interface: isc_get_generic,
    get_isteam_user_stats: isc_get_user_stats,
    get_isteam_apps: isc_get_apps,
    get_isteam_networking: isc_get_networking,
    get_isteam_remote_storage: isc_get_remote_storage,
    get_isteam_screenshots: isc_get_screenshots,
    get_isteam_ugc: isc_get_ugc,
    get_isteam_app_list: isc_get_app_list,
    get_isteam_music: isc_get_music,
    get_isteam_music_remote: isc_get_music_remote,
    get_isteam_html_surface: isc_get_html_surface,
    set_post_api_result_in_process: isc_set_post_api,
    remove_post_api_result_in_process: isc_remove_post_api,
    set_check_callback_registered_in_process: isc_set_check_callback_registered,
    get_isteam_inventory: isc_get_inventory,
    get_isteam_video: isc_get_video,
    get_isteam_parental: isc_get_parental,
    get_isteam_input: isc_get_input,
    get_isteam_parties: isc_get_parties,
    get_isteam_remote_play: isc_get_remote_play,
};

// The "object" handed back to Wine is a single machine word whose
// dereference yields the vtable pointer. We achieve that with a static
// holding `&VTBL` (a `&'static ISteamClientVtbl` = one pointer-sized word).
// `instance()` returns the address of this static.
static INSTANCE_PTR: &'static ISteamClientVtbl = &VTBL;

pub fn instance() -> *mut c_void {
    &INSTANCE_PTR as *const &'static ISteamClientVtbl as *mut c_void
}

// ---- CreateInterface + SteamInternal_* ------------------------------------

/// Implementation of `CreateInterface` — version-string-prefix dispatch.
pub unsafe fn dispatch_create_interface(version_name: *const c_char, return_code: *mut i32) -> *mut c_void {
    if version_name.is_null() {
        if !return_code.is_null() {
            unsafe { *return_code = -1 };
        }
        return core::ptr::null_mut();
    }
    let cstr = unsafe { core::ffi::CStr::from_ptr(version_name) };
    let name = cstr.to_string_lossy();
    log::log_info(&format!("CreateInterface({})", name));

    let dispatch = |prefix: &str, getter: fn() -> *mut c_void| -> Option<*mut c_void> {
        if name.starts_with(prefix) {
            Some(getter())
        } else {
            None
        }
    };

    let resolved = if name.starts_with("SteamClient") {
        Some(instance())
    } else if name.starts_with("SteamNetworkingSockets") {
        Some(iface::isteam_networking_sockets::instance())
    } else if name.starts_with("SteamNetworkingUtils") {
        Some(iface::isteam_networking_utils::instance())
    } else if name.starts_with("SteamNetworkingMessages") {
        Some(iface::isteam_networking_messages::instance())
    } else if let Some(p) = dispatch("SteamMatchMakingServers", iface::isteam_matchmaking_servers::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamMatchMaking", iface::isteam_matchmaking::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamUserStats", iface::isteam_user_stats::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamUser", iface::isteam_user::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamFriends", iface::isteam_friends::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamUtils", iface::isteam_utils::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMAPPS_INTERFACE_VERSION", iface::isteam_apps::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMUSERSTATS_INTERFACE_VERSION", iface::isteam_user_stats::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMREMOTESTORAGE_INTERFACE_VERSION", iface::isteam_remote_storage::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMSCREENSHOTS_INTERFACE_VERSION", iface::isteam_screenshots::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMINVENTORY_INTERFACE_V", iface::isteam_inventory::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMVIDEO_INTERFACE_V", iface::isteam_video::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMMUSICREMOTE_INTERFACE_VERSION", iface::isteam_music_remote::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMMUSIC_INTERFACE_VERSION", iface::isteam_music::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMHTMLSURFACE_INTERFACE_", iface::isteam_html_surface::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMUGC_INTERFACE_VERSION", iface::isteam_ugc::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMAPPLIST_INTERFACE_VERSION", iface::isteam_app_list::instance) {
        Some(p)
    } else if let Some(p) = dispatch("STEAMPARENTALSETTINGS_INTERFACE_VERSION", iface::isteam_parental::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamGameServer", iface::isteam_game_server::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamNetworking", iface::isteam_networking::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamInput", iface::isteam_input::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamParties", iface::isteam_parties::instance) {
        Some(p)
    } else if let Some(p) = dispatch("SteamRemotePlay", iface::isteam_remote_play::instance) {
        Some(p)
    } else if name.starts_with("CLIENTENGINE_INTERFACE_VERSION") {
        Some(iclient_engine::instance())
    } else {
        None
    };

    match resolved {
        Some(p) => {
            if !return_code.is_null() {
                unsafe { *return_code = 0 };
            }
            p
        }
        None => {
            if !return_code.is_null() {
                unsafe { *return_code = -1 };
            }
            log::log_warn(&format!(
                "CreateInterface: unknown name='{}' — returning null",
                name
            ));
            core::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn CreateInterface(
    version_name: *const c_char,
    return_code: *mut i32,
) -> *mut c_void {
    unsafe { dispatch_create_interface(version_name, return_code) }
}

#[no_mangle]
pub unsafe extern "C" fn SteamInternal_FindOrCreateUserInterface(
    _h_steam_user: i32,
    version_name: *const c_char,
) -> *mut c_void {
    let mut rc = 0i32;
    unsafe { CreateInterface(version_name, &mut rc) }
}

#[no_mangle]
pub unsafe extern "C" fn SteamInternal_FindOrCreateGameServerInterface(
    _h_steam_user: i32,
    version_name: *const c_char,
) -> *mut c_void {
    let mut rc = 0i32;
    unsafe { CreateInterface(version_name, &mut rc) }
}

#[no_mangle]
pub unsafe extern "C" fn SteamInternal_CreateInterface(
    version_name: *const c_char,
) -> *mut c_void {
    let mut rc = 0i32;
    unsafe { CreateInterface(version_name, &mut rc) }
}

// Avoid unused-import warnings if the linker prunes them.
#[allow(dead_code)]
fn _force_link_noop_helpers() {
    let _ = noop_v as usize;
    let _ = noop_p as usize;
}
