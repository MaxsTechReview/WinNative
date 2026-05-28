//! Declarations of `wn_cm_*` C-ABI symbols exported by `libwnsteam.so`.
//!
//! These mirror `include/wn_steam/cm_bridge.h` byte-for-byte and resolve at
//! dynamic-link time (CMakeLists links `wnsteam`). Pointers stored as `*const`
//! (the C++ side never mutates them).

#![allow(non_snake_case, non_camel_case_types)]

use core::ffi::{c_char, c_void};

#[repr(C)]
pub struct WnCmRichPresenceKV {
    pub key: *const c_char,
    pub value: *const c_char,
}

#[repr(C)]
pub struct WnCmPersonaEvent {
    pub sid: u64,
    pub persona_state: u32,
    pub game_played_app: u32,
    pub name: *const c_char,
    pub avatar_hash: *const u8,
    pub avatar_hash_len: usize,
    pub rp_pairs: *const WnCmRichPresenceKV,
    pub rp_count: usize,
}

#[repr(C)]
pub struct WnCmLicenseEntry {
    pub package_id: u32,
    pub owner_id: u32,
    pub time_created: u32,
    pub license_type: u32,
    pub flags: u32,
    pub change_number: i32,
    pub minute_limit: i32,
    pub minutes_used: i32,
}

#[repr(C)]
pub struct WnCmAccountInfo {
    pub persona_name: *const c_char,
    pub persona_name_len: usize,
    pub ip_country: *const c_char,
    pub ip_country_len: usize,
    pub two_factor_enabled: bool,
    pub phone_verified: bool,
    pub phone_identifying: bool,
    pub phone_requires_verification: bool,
}

#[repr(C)]
pub struct WnCmLobbyEntry {
    pub steam_id: u64,
    pub max_members: i32,
    pub num_members: i32,
    pub lobby_type: i32,
    pub lobby_flags: i32,
    pub ping_ms: i32,
    pub weight: i64,
    pub distance: f32,
}

#[repr(C)]
pub struct WnCmLobbyMember {
    pub steam_id: u64,
    pub persona_name: *const c_char,
    pub metadata_bytes: *const u8,
    pub metadata_len: usize,
}

#[repr(C)]
pub struct WnCmLobbyData {
    pub steam_id_lobby: u64,
    pub steam_id_owner: u64,
    pub app_id: u32,
    pub max_members: i32,
    pub num_members: i32,
    pub lobby_type: i32,
    pub lobby_flags: i32,
    pub metadata_bytes: *const u8,
    pub metadata_len: usize,
    pub members: *const WnCmLobbyMember,
    pub member_count: usize,
}

pub type WnCmPersonaObserverFn = extern "C" fn(*const WnCmPersonaEvent);
pub type WnCmLogonStateObserverFn = extern "C" fn(bool);
pub type WnCmFriendsListObserverFn = extern "C" fn(*const u64, usize);
pub type WnCmLicenseListObserverFn = extern "C" fn(*const WnCmLicenseEntry, usize);
pub type WnCmAccountInfoObserverFn = extern "C" fn(*const WnCmAccountInfo);
pub type WnCmServerRealTimeObserverFn = extern "C" fn(u32);
pub type WnCmLobbyListCb =
    extern "C" fn(u64, i32, *const WnCmLobbyEntry, usize);
pub type WnCmLobbyDataObserverFn = extern "C" fn(*const WnCmLobbyData);
pub type WnCmLobbyCreatedCb = extern "C" fn(u64, i32, u64);
pub type WnCmLobbyJoinedCb = extern "C" fn(u64, i32, u64);
pub type WnCmLobbySetDataCb = extern "C" fn(u64, i32);
pub type WnCmLobbySetOwnerCb = extern "C" fn(u64, i32);
pub type WnCmLobbyChatMsgObserverFn = extern "C" fn(u64, u64, *const u8, usize);
pub type WnCmLobbyMembershipObserverFn =
    extern "C" fn(i32, u64, u64, *const c_char);

// The actual symbols live in `libwnsteam.so` on Android. For host builds
// (`cargo test`) `mod host_stubs` below provides `#[no_mangle]` no-op
// implementations under the same names so the link succeeds.
unsafe extern "C" {
    pub fn wn_cm_set_persona_state(persona_state: i32) -> bool;
    pub fn wn_cm_set_persona_name(name: *const c_char, persona_state: i32) -> bool;
    pub fn wn_cm_request_user_info(steam_id: u64, flags: i32) -> bool;
    pub fn wn_cm_request_user_info_bulk(
        sids: *const u64,
        count: usize,
        flags: i32,
    ) -> bool;
    pub fn wn_cm_get_cached_app_ownership_ticket(
        app_id: u32,
        out_buf: *mut u8,
        max_len: usize,
        out_len: *mut usize,
    ) -> bool;
    pub fn wn_cm_bridge_inject_test_ownership_ticket(
        app_id: u32,
        bytes: *const u8,
        len: usize,
    ) -> bool;
    pub fn wn_cm_notify_games_played(app_id: u32) -> bool;
    pub fn wn_cm_set_rich_presence(
        app_id: u32,
        keys: *const *const c_char,
        values: *const *const c_char,
        count: usize,
    ) -> bool;
    pub fn wn_cm_store_user_stats(
        app_id: u32,
        crc_stats: u32,
        stat_ids: *const u32,
        stat_values: *const u32,
        count: usize,
    ) -> bool;

    pub fn wn_cm_bridge_register_persona_observer(fn_: WnCmPersonaObserverFn);
    pub fn wn_cm_bridge_dispatch_persona(ev: *const WnCmPersonaEvent);

    pub fn wn_cm_bridge_register_logon_state_observer(fn_: WnCmLogonStateObserverFn);
    pub fn wn_cm_bridge_dispatch_logon_state(logged_on: bool);
    pub fn wn_cm_bridge_inject_test_logon_state(logged_on: bool);

    pub fn wn_cm_bridge_register_friends_list_observer(fn_: WnCmFriendsListObserverFn);
    pub fn wn_cm_bridge_dispatch_friends_list(sids: *const u64, count: usize);
    pub fn wn_cm_bridge_inject_test_friends_list(sids: *const u64, count: usize);

    pub fn wn_cm_bridge_register_license_list_observer(fn_: WnCmLicenseListObserverFn);
    pub fn wn_cm_bridge_dispatch_license_list(
        licenses: *const WnCmLicenseEntry,
        count: usize,
    );
    pub fn wn_cm_bridge_inject_test_license_list(
        licenses: *const WnCmLicenseEntry,
        count: usize,
    );

    pub fn wn_cm_bridge_register_account_info_observer(fn_: WnCmAccountInfoObserverFn);
    pub fn wn_cm_bridge_dispatch_account_info(info: *const WnCmAccountInfo);
    pub fn wn_cm_bridge_inject_test_account_info(info: *const WnCmAccountInfo);

    pub fn wn_cm_bridge_register_server_realtime_observer(
        fn_: WnCmServerRealTimeObserverFn,
    );
    pub fn wn_cm_bridge_dispatch_server_realtime(server_realtime: u32);

    pub fn wn_cm_lobby_get_list(
        h_call: u64,
        app_id: u32,
        num_lobbies_requested: i32,
        filter_keys: *const *const c_char,
        filter_values: *const *const c_char,
        filter_comparisons: *const i32,
        filter_types: *const i32,
        filter_count: usize,
        cb: WnCmLobbyListCb,
    ) -> bool;

    pub fn wn_cm_bridge_register_lobby_data_observer(fn_: WnCmLobbyDataObserverFn);

    pub fn wn_cm_lobby_create(
        h_call: u64,
        app_id: u32,
        lobby_type: i32,
        max_members: i32,
        cb: WnCmLobbyCreatedCb,
    ) -> bool;

    pub fn wn_cm_lobby_join(
        h_call: u64,
        app_id: u32,
        lobby_sid: u64,
        cb: WnCmLobbyJoinedCb,
    ) -> bool;

    pub fn wn_cm_lobby_leave(app_id: u32, lobby_sid: u64) -> bool;

    pub fn wn_cm_lobby_set_data(
        h_call: u64,
        app_id: u32,
        lobby_sid: u64,
        steam_id_member: u64,
        metadata: *const u8,
        metadata_len: usize,
        max_members: i32,
        lobby_type: i32,
        lobby_flags: i32,
        cb: WnCmLobbySetDataCb,
    ) -> bool;

    pub fn wn_cm_lobby_send_chat(
        app_id: u32,
        lobby_sid: u64,
        data: *const u8,
        n: usize,
    ) -> bool;

    pub fn wn_cm_lobby_set_owner(
        h_call: u64,
        app_id: u32,
        lobby_sid: u64,
        new_owner_sid: u64,
        cb: WnCmLobbySetOwnerCb,
    ) -> bool;

    pub fn wn_cm_lobby_invite_user(app_id: u32, lobby_sid: u64, invitee_sid: u64) -> bool;

    pub fn wn_cm_bridge_register_lobby_chat_msg_observer(
        fn_: WnCmLobbyChatMsgObserverFn,
    );

    pub fn wn_cm_bridge_register_lobby_membership_observer(
        fn_: WnCmLobbyMembershipObserverFn,
    );

    pub fn wn_cm_bridge_start_state_sync_poller();
    pub fn wn_cm_bridge_stop_state_sync_poller();
}

// ---- Host-side stubs for `cargo test` -------------------------------------
//
// On a non-Android target the symbols above don't exist (they live in
// `libwnsteam.so`). The crate still has to link — both as an `rlib` for the
// integration tests and as a `cdylib` if anyone ever builds it on the host.
// Provide no-op stubs so the link succeeds; they are unreachable in real
// builds because everything goes through the Android `extern` block.

#[cfg(not(target_os = "android"))]
mod host_stubs {
    use super::*;

    #[no_mangle] pub extern "C" fn wn_cm_set_persona_state(_: i32) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_set_persona_name(_: *const c_char, _: i32) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_request_user_info(_: u64, _: i32) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_request_user_info_bulk(_: *const u64, _: usize, _: i32) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_get_cached_app_ownership_ticket(
        _: u32, _: *mut u8, _: usize, _: *mut usize,
    ) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_bridge_inject_test_ownership_ticket(
        _: u32, _: *const u8, _: usize,
    ) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_notify_games_played(_: u32) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_set_rich_presence(
        _: u32, _: *const *const c_char, _: *const *const c_char, _: usize,
    ) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_store_user_stats(
        _: u32, _: u32, _: *const u32, _: *const u32, _: usize,
    ) -> bool { false }

    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_persona_observer(_: WnCmPersonaObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_dispatch_persona(_: *const WnCmPersonaEvent) {}

    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_logon_state_observer(_: WnCmLogonStateObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_dispatch_logon_state(_: bool) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_inject_test_logon_state(_: bool) {}

    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_friends_list_observer(_: WnCmFriendsListObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_dispatch_friends_list(_: *const u64, _: usize) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_inject_test_friends_list(_: *const u64, _: usize) {}

    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_license_list_observer(_: WnCmLicenseListObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_dispatch_license_list(_: *const WnCmLicenseEntry, _: usize) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_inject_test_license_list(_: *const WnCmLicenseEntry, _: usize) {}

    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_account_info_observer(_: WnCmAccountInfoObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_dispatch_account_info(_: *const WnCmAccountInfo) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_inject_test_account_info(_: *const WnCmAccountInfo) {}

    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_server_realtime_observer(_: WnCmServerRealTimeObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_dispatch_server_realtime(_: u32) {}

    #[no_mangle] pub extern "C" fn wn_cm_lobby_get_list(
        _: u64, _: u32, _: i32, _: *const *const c_char, _: *const *const c_char,
        _: *const i32, _: *const i32, _: usize, _: WnCmLobbyListCb,
    ) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_lobby_data_observer(_: WnCmLobbyDataObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_lobby_create(_: u64, _: u32, _: i32, _: i32, _: WnCmLobbyCreatedCb) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_lobby_join(_: u64, _: u32, _: u64, _: WnCmLobbyJoinedCb) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_lobby_leave(_: u32, _: u64) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_lobby_set_data(
        _: u64, _: u32, _: u64, _: u64, _: *const u8, _: usize,
        _: i32, _: i32, _: i32, _: WnCmLobbySetDataCb,
    ) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_lobby_send_chat(_: u32, _: u64, _: *const u8, _: usize) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_lobby_set_owner(_: u64, _: u32, _: u64, _: u64, _: WnCmLobbySetOwnerCb) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_lobby_invite_user(_: u32, _: u64, _: u64) -> bool { false }
    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_lobby_chat_msg_observer(_: WnCmLobbyChatMsgObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_register_lobby_membership_observer(_: WnCmLobbyMembershipObserverFn) {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_start_state_sync_poller() {}
    #[no_mangle] pub extern "C" fn wn_cm_bridge_stop_state_sync_poller() {}
}

/// Single load-bearing reference to keep the bridge crate import from being
/// silently elided by LTO when only used through observers.
pub static DUMMY: usize = 0;

/// Helper: cast `*const c_void` to `*mut c_void` for FFI.
pub fn nul_cvoid() -> *mut c_void {
    core::ptr::null_mut()
}
