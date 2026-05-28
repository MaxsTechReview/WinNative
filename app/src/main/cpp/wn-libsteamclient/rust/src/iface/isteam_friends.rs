//! ISteamFriends — 80 slots (isteam_stubs.cpp:850-1245). Mostly stubs.

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 80;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        // Void-returning slots (approximate — most slots in C++ here are getters
        // and queries that return values). Setters/notifiers are void.
        // The original C++ class declares the following void methods at these
        // approximate slot indices; keep a permissive set, callers tolerate
        // void-vs-bool mismatch for known stubbed slots.
        s[1]  = noop_v as usize;  // SetPersonaState
        s[12] = noop_v as usize;  // ActivateGameOverlay
        s[13] = noop_v as usize;  // ActivateGameOverlayToUser
        s[14] = noop_v as usize;  // ActivateGameOverlayToWebPage
        s[15] = noop_v as usize;  // ActivateGameOverlayToStore
        s[16] = noop_v as usize;  // SetPlayedWith
        s[17] = noop_v as usize;  // ActivateGameOverlayInviteDialog
        s[37] = noop_v as usize;  // ClearRichPresence
        s[40] = noop_v as usize;  // RequestFriendRichPresence
        s[45] = noop_v as usize;  // ActivateGameOverlayToWebPage variants
        s[58] = noop_v as usize;  // SetInGameVoiceSpeaking
        s[60] = noop_v as usize;  // ActivateGameOverlayRemotePlayTogetherInviteDialog
        assert_eq!(s.len(), N);
        s
    })
}
