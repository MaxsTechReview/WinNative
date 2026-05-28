//! ISteamAppList — 5 slots (isteam_stubs.cpp:2322-2365).

#![allow(non_snake_case)]

use crate::state;
use crate::vtable::{noop_p, LazyInstance, This};
use core::ffi::c_void;

const N: usize = 5;

unsafe extern "C" fn get_num_installed_apps(_t: *mut This) -> u32 {
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    apps.installed_apps.len() as u32
}

unsafe extern "C" fn get_installed_apps(_t: *mut This, p_vec: *mut u32, c_max: u32) -> u32 {
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    let total = apps.installed_apps.len() as u32;
    let copy = total.min(c_max);
    if !p_vec.is_null() && copy > 0 {
        let mut i = 0;
        for &id in apps.installed_apps.iter() {
            if i >= copy { break; }
            unsafe { *p_vec.add(i as usize) = id };
            i += 1;
        }
    }
    copy
}

unsafe extern "C" fn get_app_name(_t: *mut This, app_id: u32, p_name: *mut u8, c_max: i32) -> i32 {
    if p_name.is_null() || c_max <= 0 { return 0; }
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    let Some(n) = apps.app_names.get(&app_id).cloned() else {
        unsafe { *p_name = 0 };
        return 0;
    };
    let copy = (n.len() as i32).min(c_max - 1);
    if copy > 0 {
        unsafe { core::ptr::copy_nonoverlapping(n.as_ptr(), p_name, copy as usize) };
    }
    unsafe { *p_name.add(copy as usize) = 0 };
    copy
}

unsafe extern "C" fn get_app_install_dir(_t: *mut This, app_id: u32, p_dir: *mut u8, c_max: i32) -> i32 {
    if p_dir.is_null() || c_max <= 0 { return 0; }
    let apps = state::pushed().apps.lock().expect("apps poisoned");
    let Some(d) = apps.app_install_dirs.get(&app_id).cloned() else {
        unsafe { *p_dir = 0 };
        return 0;
    };
    let copy = (d.len() as i32).min(c_max - 1);
    if copy > 0 {
        unsafe { core::ptr::copy_nonoverlapping(d.as_ptr(), p_dir, copy as usize) };
    }
    unsafe { *p_dir.add(copy as usize) = 0 };
    copy
}

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[0] = get_num_installed_apps as usize;
        s[1] = get_installed_apps as usize;
        s[2] = get_app_name as usize;
        s[3] = get_app_install_dir as usize;
        s[4] = noop_p as usize; // GetAppBuildId -> i32 0
        assert_eq!(s.len(), N);
        s
    })
}
