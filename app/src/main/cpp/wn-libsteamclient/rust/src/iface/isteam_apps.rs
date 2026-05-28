//! ISteamApps — 30 slots (isteam_stubs.cpp:493-848).

#![allow(non_snake_case)]

use crate::state;
use crate::vtable::{noop_p, noop_v, LazyInstance, This};
use core::ffi::c_void;
use std::sync::atomic::Ordering;

const N: usize = 30;

unsafe extern "C" fn b_is_subscribed(_t: *mut This) -> bool { true }
unsafe extern "C" fn b_is_low_violence(_t: *mut This) -> bool { false }
unsafe extern "C" fn b_is_cybercafe(_t: *mut This) -> bool { false }
unsafe extern "C" fn b_is_vac_banned(_t: *mut This) -> bool { false }

unsafe extern "C" fn b_is_subscribed_app(_t: *mut This, app_id: u32) -> bool {
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    apps.owned_apps.contains(&app_id)
}

unsafe extern "C" fn b_is_dlc_installed(_t: *mut This, app_id: u32) -> bool {
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    apps.installed_apps.contains(&app_id)
}

unsafe extern "C" fn get_app_install_dir(
    _t: *mut This, app_id: u32, p_folder: *mut u8, folder_size: u32,
) -> u32 {
    if p_folder.is_null() || folder_size == 0 { return 0; }
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    let Some(d) = apps.app_install_dirs.get(&app_id).cloned() else {
        unsafe { *p_folder = 0 };
        return 0;
    };
    let copy = (d.len() as u32).min(folder_size.saturating_sub(1));
    if copy > 0 {
        unsafe { core::ptr::copy_nonoverlapping(d.as_ptr(), p_folder, copy as usize) };
    }
    unsafe { *p_folder.add(copy as usize) = 0 };
    d.len() as u32
}

unsafe extern "C" fn b_is_app_installed(_t: *mut This, app_id: u32) -> bool {
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    apps.installed_apps.contains(&app_id) || apps.owned_apps.contains(&app_id)
}

unsafe extern "C" fn get_app_owner(_t: *mut This) -> u64 {
    state::pushed().steam_id.load(Ordering::SeqCst)
}

unsafe extern "C" fn get_current_beta_name(
    _t: *mut This, p_name: *mut u8, n_buf_size: i32,
) -> bool {
    if p_name.is_null() || n_buf_size <= 0 { return false; }
    let app = state::pushed().app_id.load(Ordering::SeqCst);
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    let Some(b) = apps.app_current_beta.get(&app).cloned() else { return false };
    let copy = (b.len() as i32).min(n_buf_size - 1);
    if copy > 0 {
        unsafe { core::ptr::copy_nonoverlapping(b.as_ptr(), p_name, copy as usize) };
    }
    unsafe { *p_name.add(copy as usize) = 0 };
    !b.is_empty()
}

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0] = b_is_subscribed as usize;
        s[1] = b_is_low_violence as usize;
        s[2] = b_is_cybercafe as usize;
        s[3] = b_is_vac_banned as usize;
        // 4: GetCurrentGameLanguage -> *const c_char (noop_p returns null — caller treats as empty)
        // 5: GetAvailableGameLanguages -> *const c_char (null = empty)
        s[6] = b_is_subscribed_app as usize;
        s[7] = b_is_dlc_installed as usize;
        // 8: GetEarliestPurchaseUnixTime -> u32 0
        // 9: BIsSubscribedFromFreeWeekend -> bool false
        // 10: GetDLCCount -> i32 0
        // 11: BGetDLCDataByIndex -> bool false
        s[12] = noop_v as usize;            // InstallDLC
        s[13] = noop_v as usize;            // UninstallDLC
        s[14] = noop_v as usize;            // RequestAppProofOfPurchaseKey
        s[15] = get_current_beta_name as usize;
        // 16: MarkContentCorrupt -> bool true
        // 17: GetInstalledDepots -> u32 0
        s[18] = get_app_install_dir as usize;
        s[19] = b_is_app_installed as usize;
        s[20] = get_app_owner as usize;
        // 21: GetLaunchQueryParam -> *const c_char (null)
        // 22: GetDlcDownloadProgress -> bool false
        // 23: GetAppBuildId -> i32 0
        s[24] = noop_v as usize;            // RequestAllProofOfPurchaseKeys
        // 25: GetFileDetails -> u64 0
        // 26: GetLaunchCommandLine -> i32 0
        // 27: BIsSubscribedFromFamilySharing -> bool false
        // 28: BIsTimedTrial -> bool false
        // 29: SetDlcContext -> bool false
        assert_eq!(s.len(), N);
        s
    })
}
