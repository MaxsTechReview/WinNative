//! ISteamUser — 33 slots (isteam_stubs.cpp:182-491).
//!
//! Slot 0..2 (GetHSteamUser/BLoggedOn/GetSteamID) carry real state reads;
//! voice slots and auth ticket slots are stubbed to OK returns to keep
//! consumers happy. Encrypted-app-ticket flow is preserved enough for the
//! callback path.

#![allow(non_snake_case)]

use crate::callbacks as cb;
use crate::state;
use crate::vtable::{noop_p, noop_v, LazyInstance, This};
use core::ffi::c_void;
use std::sync::atomic::Ordering;

const N: usize = 33;

unsafe extern "C" fn get_hsteam_user(_t: *mut This) -> i32 {
    state::state().user.load(Ordering::SeqCst)
}
unsafe extern "C" fn b_logged_on(_t: *mut This) -> bool {
    state::state().logged_on.load(Ordering::SeqCst)
}
unsafe extern "C" fn get_steam_id(_t: *mut This) -> u64 {
    state::pushed().steam_id.load(Ordering::SeqCst)
}
unsafe extern "C" fn get_voice_optimal_rate(_t: *mut This) -> u32 { 11025 }
unsafe extern "C" fn b_is_behind_nat(_t: *mut This) -> bool { true }
unsafe extern "C" fn user_has_license(_t: *mut This, steam_id: u64, app_id: u32) -> i32 {
    if app_id == 0 { return 2; }
    let self_id = state::pushed().steam_id.load(Ordering::SeqCst);
    if steam_id != 0 && steam_id == self_id {
        let apps = state::pushed().apps.lock().expect("apps poisoned");
        return if apps.owned_apps.contains(&app_id) { 0 } else { 1 };
    }
    2 // NoAuth
}

unsafe extern "C" fn begin_auth_session(
    _t: *mut This, _ticket: *const c_void, _cb_ticket: i32, steam_id: u64,
) -> i32 {
    let payload = cb::ValidateAuthTicketResponse {
        m_SteamID: steam_id,
        m_eAuthSessionResponse: 0,
        _pad: 0,
        m_OwnerSteamID: steam_id,
    };
    let raw = unsafe { cb::as_bytes(&payload) };
    state::push_callback_bytes(
        state::state().user.load(Ordering::SeqCst),
        cb::K_VALIDATE_AUTH_TICKET_RESPONSE,
        raw,
    );
    0
}

unsafe extern "C" fn cancel_auth_ticket(_t: *mut This, h: u64) {
    let mut auth = state::pushed().auth.lock().expect("auth poisoned");
    auth.auth_tickets.remove(&(h as u32));
}

unsafe extern "C" fn b_is_phone_verified(_t: *mut This) -> bool {
    state::pushed().account_phone_verified.load(Ordering::SeqCst)
}
unsafe extern "C" fn b_is_two_factor_enabled(_t: *mut This) -> bool {
    state::pushed().account_two_factor_enabled.load(Ordering::SeqCst)
}
unsafe extern "C" fn b_is_phone_identifying(_t: *mut This) -> bool {
    state::pushed().account_phone_identifying.load(Ordering::SeqCst)
}
unsafe extern "C" fn b_is_phone_requires_verification(_t: *mut This) -> bool {
    state::pushed().account_phone_requires_verification.load(Ordering::SeqCst)
}
unsafe extern "C" fn get_player_steam_level(_t: *mut This) -> i32 {
    state::pushed().self_player_level.load(Ordering::SeqCst)
}

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0] = get_hsteam_user as usize;
        s[1] = b_logged_on as usize;
        s[2] = get_steam_id as usize;
        // 3: InitiateGameConnection_DEPRECATED -> int 0
        s[4] = noop_v as usize;  // TerminateGameConnection_DEPRECATED
        s[5] = noop_v as usize;  // TrackAppUsageEvent
        // 6: GetUserDataFolder -> bool false (default noop_p ok)
        s[7] = noop_v as usize;  // StartVoiceRecording
        s[8] = noop_v as usize;  // StopVoiceRecording
        // 9-11: voice -> int 0
        s[12] = get_voice_optimal_rate as usize;
        // 13: GetAuthSessionTicket -> u64 0 (synthetic ticket disabled in stub form)
        // 14: GetAuthTicketForWebApi -> u64 0
        s[15] = begin_auth_session as usize;
        s[16] = noop_v as usize; // EndAuthSession
        s[17] = cancel_auth_ticket as usize;
        s[18] = user_has_license as usize;
        s[19] = b_is_behind_nat as usize;
        s[20] = noop_v as usize; // AdvertiseGame
        // 21: RequestEncryptedAppTicket -> u64 0
        // 22: GetEncryptedAppTicket -> bool false
        // 23: GetGameBadgeLevel -> i32 0
        s[24] = get_player_steam_level as usize;
        // 25: RequestStoreAuthURL -> u64 0
        s[26] = b_is_phone_verified as usize;
        s[27] = b_is_two_factor_enabled as usize;
        s[28] = b_is_phone_identifying as usize;
        s[29] = b_is_phone_requires_verification as usize;
        // 30: GetMarketEligibility -> u64 0
        // 31: GetDurationControl -> u64 0
        // 32: BSetDurationControlOnlineState -> bool false (caller doesn't care)
        assert_eq!(s.len(), N);
        s
    })
}
