//! ISteamHTMLSurface — 37 slots (isteam_stubs.cpp:3410-3449).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 37;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        // Slots 0,1 return bool (false), slot 2 returns uint64_t (0). Slots 3-36 are void.
        s[3]  = noop_v as usize; // <slot 3>  RemoveBrowser
        s[4]  = noop_v as usize; // <slot 4>  LoadURL
        s[5]  = noop_v as usize; // <slot 5>  SetSize
        s[6]  = noop_v as usize; // <slot 6>  StopLoad
        s[7]  = noop_v as usize; // <slot 7>  Reload
        s[8]  = noop_v as usize; // <slot 8>  GoBack
        s[9]  = noop_v as usize; // <slot 9>  GoForward
        s[10] = noop_v as usize; // <slot 10> AddHeader
        s[11] = noop_v as usize; // <slot 11> ExecuteJavascript
        s[12] = noop_v as usize; // <slot 12> MouseUp
        s[13] = noop_v as usize; // <slot 13> MouseDown
        s[14] = noop_v as usize; // <slot 14> MouseDoubleClick
        s[15] = noop_v as usize; // <slot 15> MouseMove
        s[16] = noop_v as usize; // <slot 16> MouseWheel
        s[17] = noop_v as usize; // <slot 17> KeyDown
        s[18] = noop_v as usize; // <slot 18> KeyUp
        s[19] = noop_v as usize; // <slot 19> KeyChar
        s[20] = noop_v as usize; // <slot 20> SetHorizontalScroll
        s[21] = noop_v as usize; // <slot 21> SetVerticalScroll
        s[22] = noop_v as usize; // <slot 22> SetKeyFocus
        s[23] = noop_v as usize; // <slot 23> ViewSource
        s[24] = noop_v as usize; // <slot 24> CopyToClipboard
        s[25] = noop_v as usize; // <slot 25> PasteFromClipboard
        s[26] = noop_v as usize; // <slot 26> Find
        s[27] = noop_v as usize; // <slot 27> StopFind
        s[28] = noop_v as usize; // <slot 28> GetLinkAtPosition
        s[29] = noop_v as usize; // <slot 29> SetCookie
        s[30] = noop_v as usize; // <slot 30> SetPageScaleFactor
        s[31] = noop_v as usize; // <slot 31> SetBackgroundMode
        s[32] = noop_v as usize; // <slot 32> SetDPIScalingFactor
        s[33] = noop_v as usize; // <slot 33> OpenDeveloperTools
        s[34] = noop_v as usize; // <slot 34> AllowStartRequest
        s[35] = noop_v as usize; // <slot 35> JSDialogResponse
        s[36] = noop_v as usize; // <slot 36> FileLoadDialogResponse
        assert_eq!(s.len(), N);
        s
    })
}
