//! ISteamMusic — 9 slots (isteam_stubs.cpp:2309-2320).

#![allow(non_snake_case)]

use crate::vtable::{noop_f, noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 9;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let s = vec![
            noop_p as usize, // BIsEnabled
            noop_p as usize, // BIsPlaying
            noop_p as usize, // GetPlaybackStatus
            noop_v as usize, // Play
            noop_v as usize, // Pause
            noop_v as usize, // PlayPrevious
            noop_v as usize, // PlayNext
            noop_v as usize, // SetVolume
            noop_f as usize, // GetVolume -> float
        ];
        assert_eq!(s.len(), N);
        s
    })
}
