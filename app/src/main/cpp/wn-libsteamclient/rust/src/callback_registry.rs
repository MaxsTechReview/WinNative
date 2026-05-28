//! Port of `wn_libsteamclient::callback_registry`. Two registries:
//! - `CALLBACK_REGISTRY` keyed by callback id (i32) for `SteamAPI_RegisterCallback`.
//! - `CALL_RESULT_REGISTRY` keyed by hCall (u64) for `SteamAPI_RegisterCallResult`.
//!
//! Pointers are stored as `usize` to satisfy `Send`/`Sync`. They are cast back
//! to `*mut c_void` at dispatch time.

use std::collections::HashMap;
use std::sync::Mutex;
use std::sync::OnceLock;

pub const K_CCALLBACK_BASE_FLAGS_OFFSET: usize = 8;
pub const K_CCALLBACK_BASE_ID_OFFSET: usize = 12;
pub const K_CALLBACK_FLAGS_REGISTERED: u8 = 0x01;
pub const K_CALLBACK_FLAGS_GAME_SERVER: u8 = 0x02;

static CALLBACK_REGISTRY: OnceLock<Mutex<HashMap<i32, Vec<usize>>>> = OnceLock::new();
static CALL_RESULT_REGISTRY: OnceLock<Mutex<HashMap<u64, Vec<usize>>>> = OnceLock::new();

fn cb_registry() -> &'static Mutex<HashMap<i32, Vec<usize>>> {
    CALLBACK_REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}

fn cr_registry() -> &'static Mutex<HashMap<u64, Vec<usize>>> {
    CALL_RESULT_REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}

pub fn register_callback(cb: usize, i_callback: i32) {
    if cb == 0 {
        return;
    }
    let mut g = cb_registry().lock().expect("cb registry poisoned");
    let bucket = g.entry(i_callback).or_default();
    if !bucket.iter().any(|&p| p == cb) {
        bucket.push(cb);
    }
}

pub fn unregister_callback(cb: usize) {
    if cb == 0 {
        return;
    }
    let mut g = cb_registry().lock().expect("cb registry poisoned");
    for bucket in g.values_mut() {
        bucket.retain(|&p| p != cb);
    }
}

pub fn find_callbacks(i_callback: i32) -> Vec<usize> {
    let g = cb_registry().lock().expect("cb registry poisoned");
    g.get(&i_callback).cloned().unwrap_or_default()
}

pub fn registry_size() -> usize {
    let g = cb_registry().lock().expect("cb registry poisoned");
    g.values().map(|v| v.len()).sum()
}

pub fn register_call_result(cb: usize, h_call: u64) {
    if cb == 0 || h_call == 0 {
        return;
    }
    let mut g = cr_registry().lock().expect("cr registry poisoned");
    let bucket = g.entry(h_call).or_default();
    if !bucket.iter().any(|&p| p == cb) {
        bucket.push(cb);
    }
}

pub fn unregister_call_result(cb: usize, h_call: u64) {
    if cb == 0 {
        return;
    }
    let mut g = cr_registry().lock().expect("cr registry poisoned");
    if h_call == 0 {
        for bucket in g.values_mut() {
            bucket.retain(|&p| p != cb);
        }
    } else if let Some(bucket) = g.get_mut(&h_call) {
        bucket.retain(|&p| p != cb);
    }
}

pub fn find_call_result_cbs(h_call: u64) -> Vec<usize> {
    let g = cr_registry().lock().expect("cr registry poisoned");
    g.get(&h_call).cloned().unwrap_or_default()
}

pub fn call_result_registry_size() -> usize {
    let g = cr_registry().lock().expect("cr registry poisoned");
    g.values().map(|v| v.len()).sum()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn register_unregister_roundtrip() {
        let p = 0x4000usize;
        register_callback(p, 42);
        assert!(find_callbacks(42).contains(&p));
        unregister_callback(p);
        assert!(!find_callbacks(42).contains(&p));
    }

    #[test]
    fn duplicate_registration_is_idempotent() {
        let p = 0x4100usize;
        register_callback(p, 43);
        register_callback(p, 43);
        assert_eq!(find_callbacks(43).iter().filter(|&&x| x == p).count(), 1);
        unregister_callback(p);
    }

    #[test]
    fn call_result_registry_roundtrip() {
        let p = 0x5000usize;
        register_call_result(p, 99);
        assert!(find_call_result_cbs(99).contains(&p));
        unregister_call_result(p, 99);
        assert!(!find_call_result_cbs(99).contains(&p));
    }
}
