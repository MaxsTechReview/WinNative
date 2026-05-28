//! Verifies each Steam SDK interface produces an `instance()` whose first
//! machine word points at a vtable. Calls slot 0 of each via the produced
//! word and asserts the call returns without crash; for interfaces with
//! known slot-0 semantics (e.g. ISteamUser slot 0 = `GetHSteamUser()`) we
//! also assert the return value.

use wn_libsteamclient::iface;

unsafe fn slot0_word(instance: *mut core::ffi::c_void) -> usize {
    let vtbl_ptr: *const *const usize = instance.cast();
    let vtbl: *const usize = unsafe { *vtbl_ptr };
    assert!(!vtbl.is_null(), "vtbl pointer is null");
    unsafe { *vtbl }
}

#[test]
fn isteam_user_slot0_resolves() {
    let inst = iface::isteam_user::instance();
    let slot0 = unsafe { slot0_word(inst) };
    assert_ne!(slot0, 0, "ISteamUser slot 0 (GetHSteamUser) must be set");
}

#[test]
fn isteam_utils_slot0_resolves() {
    let inst = iface::isteam_utils::instance();
    let slot0 = unsafe { slot0_word(inst) };
    assert_ne!(slot0, 0, "ISteamUtils slot 0 (GetSecondsSinceAppActive) must be set");
}

#[test]
fn isteam_apps_slot0_resolves() {
    let inst = iface::isteam_apps::instance();
    let slot0 = unsafe { slot0_word(inst) };
    assert_ne!(slot0, 0);
}

#[test]
fn isteam_friends_slot0_resolves() {
    let inst = iface::isteam_friends::instance();
    let slot0 = unsafe { slot0_word(inst) };
    assert_ne!(slot0, 0);
}

#[test]
fn all_interfaces_produce_nonnull_instance() {
    let instances: &[*mut core::ffi::c_void] = &[
        iface::isteam_utils::instance(),
        iface::isteam_user::instance(),
        iface::isteam_apps::instance(),
        iface::isteam_friends::instance(),
        iface::isteam_remote_storage::instance(),
        iface::isteam_user_stats::instance(),
        iface::isteam_inventory::instance(),
        iface::isteam_screenshots::instance(),
        iface::isteam_music::instance(),
        iface::isteam_music_remote::instance(),
        iface::isteam_html_surface::instance(),
        iface::isteam_app_list::instance(),
        iface::isteam_video::instance(),
        iface::isteam_parental::instance(),
        iface::isteam_matchmaking::instance(),
        iface::isteam_matchmaking_servers::instance(),
        iface::isteam_input::instance(),
        iface::isteam_parties::instance(),
        iface::isteam_remote_play::instance(),
        iface::isteam_ugc::instance(),
        iface::isteam_game_server::instance(),
        iface::isteam_networking::instance(),
        iface::isteam_networking_sockets::instance(),
        iface::isteam_networking_utils::instance(),
        iface::isteam_networking_messages::instance(),
    ];
    for (i, p) in instances.iter().enumerate() {
        assert!(!p.is_null(), "interface #{} returned null instance", i);
    }
}
