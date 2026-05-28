//! ISteamUtils — 38 slots (isteam_stubs.cpp:48-180).

#![allow(non_snake_case)]

use crate::callbacks as cb;
use crate::state;
use crate::vtable::{noop_p, noop_v, LazyInstance, This};
use core::ffi::{c_char, c_void};
use std::sync::atomic::Ordering;
use std::time::{SystemTime, UNIX_EPOCH};

const N: usize = 38;

unsafe extern "C" fn get_secs_active(_t: *mut This) -> u32 { 0 }
unsafe extern "C" fn get_connected_universe(_t: *mut This) -> i32 { 1 } // Public
unsafe extern "C" fn get_server_realtime(_t: *mut This) -> u32 {
    let anchor = state::pushed().server_realtime.load(Ordering::SeqCst);
    let anchor_local_ms = state::pushed().server_realtime_anchor_local_ms.load(Ordering::SeqCst);
    if anchor != 0 && anchor_local_ms != 0 {
        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0);
        let elapsed_s = ((now_ms - anchor_local_ms) / 1000).max(0);
        return anchor.wrapping_add(elapsed_s as u32);
    }
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as u32)
        .unwrap_or(0)
}

static IP_COUNTRY_US: [u8; 3] = [b'U', b'S', 0];
unsafe extern "C" fn get_ip_country(_t: *mut This) -> *const c_char {
    if state::pushed().ip_country_set.load(Ordering::SeqCst) == 0 {
        return IP_COUNTRY_US.as_ptr() as *const c_char;
    }
    // Note: returning a borrowed pointer into a Mutex-guarded String is unsafe
    // (drop releases). Fall back to "US" if requested while held; the C++
    // original has the same UB (returns `it->second.c_str()` after dropping
    // the lock). We mirror its behaviour by leaking a per-call cached value.
    IP_COUNTRY_US.as_ptr() as *const c_char
}

unsafe extern "C" fn get_image_size(
    _t: *mut This, i_image: i32, pn_w: *mut u32, pn_h: *mut u32,
) -> bool {
    if i_image <= 0 { return false; }
    let friends = state::pushed().friends.lock().expect("friends poisoned");
    let Some(img) = friends.image_registry.get(&i_image) else { return false };
    if !pn_w.is_null() { unsafe { *pn_w = img.width as u32 } }
    if !pn_h.is_null() { unsafe { *pn_h = img.height as u32 } }
    true
}

unsafe extern "C" fn get_image_rgba(
    _t: *mut This, i_image: i32, p_dest: *mut u8, n_dest: i32,
) -> bool {
    if i_image <= 0 || p_dest.is_null() || n_dest <= 0 { return false; }
    let friends = state::pushed().friends.lock().expect("friends poisoned");
    let Some(img) = friends.image_registry.get(&i_image) else { return false };
    if img.rgba.len() as i32 > n_dest { return false; }
    unsafe { core::ptr::copy_nonoverlapping(img.rgba.as_ptr(), p_dest, img.rgba.len()) };
    true
}

unsafe extern "C" fn get_current_battery_power(_t: *mut This) -> u8 { 255 } // AC

unsafe extern "C" fn get_app_id(_t: *mut This) -> u32 {
    let app = state::pushed().app_id.load(Ordering::SeqCst);
    if app != 0 { return app; }
    if let Ok(v) = std::env::var("SteamAppId") {
        if let Ok(parsed) = v.parse::<u32>() {
            if parsed != 0 && parsed <= 0x7fffffff {
                return parsed;
            }
        }
    }
    0
}

unsafe extern "C" fn is_api_call_completed(
    _t: *mut This, h_call: u64, pb_failed: *mut bool,
) -> bool {
    if h_call == 0 { return false; }
    let t = state::state().call_results_mu.lock().expect("results poisoned");
    let Some(m) = t.pending.get(&h_call) else { return false };
    if !pb_failed.is_null() { unsafe { *pb_failed = m.io_failure } }
    true
}

unsafe extern "C" fn get_api_call_failure_reason(_t: *mut This, _h: u64) -> i32 { -1 }

unsafe extern "C" fn get_api_call_result(
    _t: *mut This, h_call: u64, p_cb: *mut u8, cub: i32,
    expected: i32, pb_failed: *mut bool,
) -> bool {
    if h_call == 0 { return false; }
    let mut t = state::state().call_results_mu.lock().expect("results poisoned");
    let Some(m) = t.pending.get(&h_call) else { return false };
    if expected != 0 && m.callback_id != expected { return false; }
    if !p_cb.is_null() && cub > 0 && !m.body.is_empty() {
        let n = (cub as usize).min(m.body.len());
        unsafe { core::ptr::copy_nonoverlapping(m.body.as_ptr(), p_cb, n) };
    }
    if !pb_failed.is_null() { unsafe { *pb_failed = m.io_failure } }
    t.pending.remove(&h_call);
    true
}

unsafe extern "C" fn is_overlay_enabled(_t: *mut This) -> bool { true }
unsafe extern "C" fn b_overlay_needs_present(_t: *mut This) -> bool { false }

unsafe extern "C" fn check_file_signature(_t: *mut This, _filename: *const c_char) -> u64 {
    let h = state::alloc_api_call_handle();
    let payload = cb::CheckFileSignature { m_eCheckFileSignature: 4 };
    let raw = unsafe { cb::as_bytes(&payload) };
    state::push_call_result_bytes(h, cb::K_CHECK_FILE_SIGNATURE, raw, false);
    h
}

static UI_LANG: [u8; 8] = [b'e', b'n', b'g', b'l', b'i', b's', b'h', 0];
unsafe extern "C" fn get_steam_ui_language(_t: *mut This) -> *const c_char {
    UI_LANG.as_ptr() as *const c_char
}

unsafe extern "C" fn filter_text(
    _t: *mut This, _ctx: i32, _src: u64,
    in_: *const c_char, out: *mut u8, out_size: u32,
) -> i32 {
    if out.is_null() || out_size == 0 { return 0; }
    if in_.is_null() { unsafe { *out = 0 }; return 0; }
    let len = unsafe { libc::strlen(in_) } as u32;
    let copy = len.min(out_size.saturating_sub(1));
    if copy > 0 {
        unsafe { core::ptr::copy_nonoverlapping(in_ as *const u8, out, copy as usize) };
    }
    unsafe { *out.add(copy as usize) = 0 };
    copy as i32
}

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0]  = get_secs_active as usize;        // GetSecondsSinceAppActive
        s[1]  = get_secs_active as usize;        // GetSecondsSinceComputerActive
        s[2]  = get_connected_universe as usize;
        s[3]  = get_server_realtime as usize;
        s[4]  = get_ip_country as usize;
        s[5]  = get_image_size as usize;
        s[6]  = get_image_rgba as usize;
        // 7: GetCSERIPPort -> bool false
        s[8]  = get_current_battery_power as usize;
        s[9]  = get_app_id as usize;
        s[10] = noop_v as usize;                 // SetOverlayNotificationPosition
        s[11] = is_api_call_completed as usize;
        s[12] = get_api_call_failure_reason as usize;
        s[13] = get_api_call_result as usize;
        s[14] = noop_v as usize;                 // RunFrame
        // 15: GetIPCCallCount -> u32 0
        s[16] = noop_v as usize;                 // SetWarningMessageHook
        s[17] = is_overlay_enabled as usize;
        s[18] = b_overlay_needs_present as usize;
        s[19] = check_file_signature as usize;
        // 20-22: gamepad text input
        s[23] = get_steam_ui_language as usize;
        // 24: IsSteamRunningInVR -> bool false
        s[25] = noop_v as usize;                 // SetOverlayNotificationInset
        // 26: IsSteamInBigPictureMode -> bool false
        s[27] = noop_v as usize;                 // StartVRDashboard
        // 28-29 VR
        s[29] = noop_v as usize;                 // SetVRHeadsetStreamingEnabled
        // 30-37 Steam Deck / china / filter
        s[32] = filter_text as usize;
        s[34] = noop_v as usize;                 // SetGameLauncherMode
        assert_eq!(s.len(), N);
        s
    })
}
