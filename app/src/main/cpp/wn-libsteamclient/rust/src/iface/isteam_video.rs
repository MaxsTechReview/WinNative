//! ISteamVideo — 4 slots (isteam_stubs.cpp:2367-2380).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, LazyInstance, This};
use core::ffi::c_void;

const N: usize = 4;

unsafe extern "C" fn is_broadcasting(_t: *mut This, p_num_viewers: *mut i32) -> bool {
    if !p_num_viewers.is_null() {
        unsafe { *p_num_viewers = 0 };
    }
    false
}
unsafe extern "C" fn get_opf_string(
    _t: *mut This, _app: u32, buf: *mut u8, pn_buf_size: *mut i32,
) -> bool {
    if !buf.is_null() && !pn_buf_size.is_null() && unsafe { *pn_buf_size } > 0 {
        unsafe { *buf = 0 };
    }
    if !pn_buf_size.is_null() {
        unsafe { *pn_buf_size = 0 };
    }
    false
}

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let s = vec![
            noop_p as usize,           // GetVideoURL_DEPRECATED -> u64
            is_broadcasting as usize,
            noop_p as usize,           // GetOPFSettings -> u64
            get_opf_string as usize,
        ];
        assert_eq!(s.len(), N);
        s
    })
}
