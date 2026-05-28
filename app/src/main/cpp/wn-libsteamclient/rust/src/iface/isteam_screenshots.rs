//! ISteamScreenshots — 9 slots (port of isteam_stubs.cpp:2294-2307).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance, This};
use core::ffi::{c_char, c_void};
use std::sync::atomic::{AtomicBool, Ordering};

const N: usize = 9;

static HOOKED: AtomicBool = AtomicBool::new(false);

unsafe extern "C" fn write_screenshot(
    _t: *mut This, _b: *const c_void, _n: u32, _w: i32, _h: i32,
) -> u32 { 0 }
unsafe extern "C" fn add_screenshot(
    _t: *mut This, _f: *const c_char, _l: *const c_char, _w: i32, _h: i32,
) -> u32 { 0 }
unsafe extern "C" fn trigger_screenshot(_t: *mut This) {}
unsafe extern "C" fn hook_screenshots(_t: *mut This, hooked: bool) {
    HOOKED.store(hooked, Ordering::SeqCst);
}
unsafe extern "C" fn is_screenshots_hooked(_t: *mut This) -> bool {
    HOOKED.load(Ordering::SeqCst)
}

pub fn instance() -> *mut c_void {
    let _ = noop_v as usize;
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0] = write_screenshot as usize;
        s[1] = add_screenshot as usize;
        s[2] = trigger_screenshot as usize;
        s[3] = hook_screenshots as usize;
        s[4] = noop_p as usize; // SetLocation -> bool false
        s[5] = noop_p as usize; // TagUser
        s[6] = noop_p as usize; // TagPublishedFile
        s[7] = is_screenshots_hooked as usize;
        s[8] = noop_p as usize; // AddVRScreenshotToLibrary -> u32 0
        assert_eq!(s.len(), N);
        s
    })
}
