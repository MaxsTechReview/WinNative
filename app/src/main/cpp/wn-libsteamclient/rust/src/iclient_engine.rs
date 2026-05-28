//! Port of `iclient_engine.cpp` — IClientUser (57 slots) and IClientEngine
//! (80 slots). Most slots are `_slotNN` placeholders; only a handful carry
//! real behaviour.

#![allow(non_snake_case)]

use crate::log;
use crate::state;
use crate::vtable::{noop_p, noop_v, LazyInstance, This};
use core::ffi::{c_char, c_void};
use std::sync::atomic::Ordering;

// ---- IClientUser (57 slots) -----------------------------------------------

unsafe extern "C" fn icu_get_hsteam_user(_t: *mut This) -> i32 {
    state::state().user.load(Ordering::SeqCst)
}
unsafe extern "C" fn icu_set_steam_id(_t: *mut This, sid: u64) {
    state::pushed().steam_id.store(sid, Ordering::SeqCst);
    state::pushed()
        .account_id
        .store((sid & 0xFFFFFFFF) as u32, Ordering::SeqCst);
    log::log_info(&format!("IClientUser.SetSteamID({})", sid));
}
unsafe extern "C" fn icu_is_account_logged_in(_t: *mut This, account: *const c_char) -> bool {
    let name = if account.is_null() {
        "(null)".to_string()
    } else {
        unsafe { core::ffi::CStr::from_ptr(account) }
            .to_string_lossy()
            .into_owned()
    };
    log::log_info(&format!(
        "IClientUser.IsAccountLoggedIn({}) -> 0 (no persisted session yet)",
        name
    ));
    false
}
unsafe extern "C" fn icu_set_account(
    _t: *mut This, account: *const c_char, _password: *const c_char, _remember: i32,
) {
    let name = if account.is_null() {
        "(null)".to_string()
    } else {
        unsafe { core::ffi::CStr::from_ptr(account) }
            .to_string_lossy()
            .into_owned()
    };
    log::log_info(&format!("IClientUser.SetAccount({})", name));
}
unsafe extern "C" fn icu_set_login_info(
    _t: *mut This, account: *const c_char, _password: *const c_char, _remember: i32,
) -> bool {
    let name = if account.is_null() {
        "(null)".to_string()
    } else {
        unsafe { core::ffi::CStr::from_ptr(account) }
            .to_string_lossy()
            .into_owned()
    };
    log::log_info(&format!("IClientUser.SetLoginInformation({}, \"\", *)", name));
    true
}
unsafe extern "C" fn icu_logon_refresh(_t: *mut This, token: *const c_char, account: *const c_char) {
    let token_len = if token.is_null() { 0 } else { unsafe { libc::strlen(token) } };
    let name = if account.is_null() {
        "(null)".to_string()
    } else {
        unsafe { core::ffi::CStr::from_ptr(account) }
            .to_string_lossy()
            .into_owned()
    };
    log::log_info(&format!(
        "IClientUser.LogonWithRefreshToken(token={} bytes, account={})",
        token_len, name
    ));
    state::set_logged_on(true, 6);
}

pub fn iclient_user_instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_v as usize; 57];
        s[0] = icu_get_hsteam_user as usize;
        s[1] = icu_set_steam_id as usize;
        s[49] = icu_is_account_logged_in as usize;
        s[50] = icu_set_account as usize;
        s[54] = icu_set_login_info as usize;
        s[56] = icu_logon_refresh as usize;
        s
    })
}

// ---- IClientEngine (80 slots) ---------------------------------------------

unsafe extern "C" fn ice_get_iclient_user(_t: *mut This, _u: i32, _p: i32) -> *mut c_void {
    iclient_user_instance()
}

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; 80];
        s[8] = ice_get_iclient_user as usize;
        s
    })
}
