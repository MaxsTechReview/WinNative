//! Port of `api_entry.cpp` — the flat `Steam_*`, `SteamAPI_*`,
//! `SteamGameServer_*`, `Breakpad_*` C-ABI entry points.

#![allow(non_snake_case)]

use crate::callback_registry as registry;
use crate::callbacks as cb;
use crate::client;
use crate::log;
use crate::state::{self, CallbackMsg, CallResultMsg};
use core::ffi::{c_char, c_void};
use std::sync::atomic::{AtomicBool, Ordering};

// ---- Steam_* ---------------------------------------------------------------

#[no_mangle]
pub extern "C" fn Steam_CreateSteamPipe() -> i32 {
    let mut pipe = state::alloc_pipe();
    if pipe == 0 {
        pipe = state::state().pipe.load(Ordering::SeqCst);
    }
    log::log_info(&format!("Steam_CreateSteamPipe() -> {}", pipe));
    pipe
}

#[no_mangle]
pub extern "C" fn Steam_BReleaseSteamPipe(pipe: i32) -> bool {
    let h_user = state::state().user.load(Ordering::SeqCst);
    state::push_callback(h_user, cb::K_STEAM_SHUTDOWN, std::ptr::null(), 0);
    let ok = state::release_pipe(pipe);
    log::log_info(&format!(
        "Steam_BReleaseSteamPipe({}) -> {} (SteamShutdown_t emitted)",
        pipe,
        if ok { 1 } else { 0 }
    ));
    ok
}

#[no_mangle]
pub extern "C" fn Steam_ConnectToGlobalUser(pipe: i32) -> i32 {
    let user = state::alloc_global_user(pipe);
    log::log_info(&format!(
        "Steam_ConnectToGlobalUser(pipe={}) -> {}",
        pipe, user
    ));
    user
}

#[no_mangle]
pub extern "C" fn Steam_CreateGlobalUser(pipe_inout: *mut i32) -> i32 {
    if pipe_inout.is_null() {
        return 0;
    }
    let mut pipe = state::alloc_pipe();
    if pipe == 0 {
        pipe = state::state().pipe.load(Ordering::SeqCst);
    }
    let user = state::alloc_global_user(pipe);
    log::log_info(&format!(
        "Steam_CreateGlobalUser(*pipe={}) -> user={}",
        pipe, user
    ));
    user
}

#[no_mangle]
pub extern "C" fn Steam_CreateLocalUser(pipe_inout: *mut i32, _account_type: i32) -> i32 {
    Steam_CreateGlobalUser(pipe_inout)
}

#[no_mangle]
pub extern "C" fn Steam_ReleaseUser(pipe: i32, user: i32) {
    state::release_user(pipe, user);
    log::log_info(&format!("Steam_ReleaseUser(pipe={}, user={})", pipe, user));
}

#[no_mangle]
pub extern "C" fn Steam_BLoggedOn(pipe: i32, user: i32) -> bool {
    let s = state::state();
    if pipe == 0 || user == 0 {
        return false;
    }
    if s.pipe.load(Ordering::SeqCst) != pipe || s.user.load(Ordering::SeqCst) != user {
        return false;
    }
    s.logged_on.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn Steam_BConnected(pipe: i32, user: i32) -> bool {
    let s = state::state();
    if pipe == 0 || user == 0 {
        return false;
    }
    if s.pipe.load(Ordering::SeqCst) != pipe || s.user.load(Ordering::SeqCst) != user {
        return false;
    }
    s.connected.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn Steam_LogOn(pipe: i32, user: i32, _steamid: u64) {
    let s = state::state();
    if s.pipe.load(Ordering::SeqCst) == pipe && s.user.load(Ordering::SeqCst) == user {
        s.logged_on.store(true, Ordering::SeqCst);
        s.connected.store(true, Ordering::SeqCst);
    }
}

#[no_mangle]
pub extern "C" fn Steam_LogOff(pipe: i32, user: i32) {
    let s = state::state();
    if s.pipe.load(Ordering::SeqCst) == pipe && s.user.load(Ordering::SeqCst) == user {
        s.logged_on.store(false, Ordering::SeqCst);
        s.connected.store(false, Ordering::SeqCst);
    }
    log::log_info(&format!("Steam_LogOff(pipe={}, user={})", pipe, user));
}

#[no_mangle]
pub unsafe extern "C" fn Steam_BGetCallback(_pipe: i32, cb_msg: *mut u8) -> bool {
    if cb_msg.is_null() {
        return false;
    }
    let s = state::state();
    let mut q = s.callback_mu.lock().expect("callback queue poisoned");
    if q.queue.is_empty() {
        return false;
    }
    let msg = q.queue.pop_front().unwrap();
    q.last_param = msg.body;

    let h_user = msg.user;
    let i_cb = msg.id;
    let pub_param: *const u8 = if q.last_param.is_empty() {
        core::ptr::null()
    } else {
        q.last_param.as_ptr()
    };
    let cub_param: i32 = q.last_param.len() as i32;
    unsafe {
        core::ptr::copy_nonoverlapping(
            &h_user as *const i32 as *const u8,
            cb_msg.add(0),
            4,
        );
        core::ptr::copy_nonoverlapping(
            &i_cb as *const i32 as *const u8,
            cb_msg.add(4),
            4,
        );
        core::ptr::copy_nonoverlapping(
            &pub_param as *const *const u8 as *const u8,
            cb_msg.add(8),
            core::mem::size_of::<*const u8>(),
        );
        core::ptr::copy_nonoverlapping(
            &cub_param as *const i32 as *const u8,
            cb_msg.add(16),
            4,
        );
    }
    true
}

#[no_mangle]
pub extern "C" fn Steam_FreeLastCallback(_pipe: i32) {
    let s = state::state();
    let mut q = s.callback_mu.lock().expect("callback queue poisoned");
    q.last_param.clear();
    q.last_param.shrink_to_fit();
}

#[no_mangle]
pub unsafe extern "C" fn Steam_GetAPICallResult(
    _pipe: i32,
    h_call: u64,
    p_callback: *mut u8,
    cub_callback: i32,
    i_callback_expected: i32,
    pb_failed: *mut bool,
) -> bool {
    if h_call == 0 {
        return false;
    }
    let s = state::state();
    let mut t = s.call_results_mu.lock().expect("call results poisoned");
    let Some(msg) = t.pending.get(&h_call) else {
        return false;
    };
    if i_callback_expected != 0 && msg.callback_id != i_callback_expected {
        return false;
    }
    let body_len = msg.body.len();
    if !p_callback.is_null() && cub_callback > 0 && !msg.body.is_empty() {
        let n = (cub_callback as usize).min(body_len);
        unsafe {
            core::ptr::copy_nonoverlapping(msg.body.as_ptr(), p_callback, n);
        }
    }
    if !pb_failed.is_null() {
        unsafe { *pb_failed = msg.io_failure };
    }
    t.pending.remove(&h_call);
    true
}

#[no_mangle]
pub unsafe extern "C" fn Steam_IsAPICallCompleted(
    _pipe: i32,
    h_call: u64,
    pb_failed: *mut bool,
) -> bool {
    if h_call == 0 {
        return false;
    }
    let s = state::state();
    let t = s.call_results_mu.lock().expect("call results poisoned");
    let Some(msg) = t.pending.get(&h_call) else {
        return false;
    };
    if !pb_failed.is_null() {
        unsafe { *pb_failed = msg.io_failure };
    }
    true
}

#[no_mangle]
pub extern "C" fn Steam_IsKnownInterface(_pszInterfaceName: *const c_char) -> bool {
    false
}

#[no_mangle]
pub unsafe extern "C" fn Steam_NotifyMissingInterface(_pipe: i32, iface: *const c_char) {
    let name = if iface.is_null() {
        "(null)".to_string()
    } else {
        let cstr = unsafe { core::ffi::CStr::from_ptr(iface) };
        cstr.to_string_lossy().into_owned()
    };
    log::log_warn(&format!("Steam_NotifyMissingInterface: {}", name));
}

#[no_mangle]
pub extern "C" fn Steam_SetLocalIPBinding(_ip: i32, _port: i32) {}

#[no_mangle]
pub extern "C" fn Steam_ReleaseThreadLocalMemory(_b_thread_exit: i32) {}

#[no_mangle]
pub extern "C" fn Steam_GetGSHandle(_pipe: i32, _user: i32) -> i32 {
    0
}

#[no_mangle]
pub extern "C" fn Steam_InitiateGameConnection(
    _pipe: i32,
    _user: i32,
    _p_auth_blob: *mut c_void,
    _cb_max_auth_blob: i32,
    _steam_id_game_server: u64,
    _un_ip_server: u32,
    _us_port_server: u16,
    _b_secure: bool,
) -> bool {
    false
}

#[no_mangle]
pub extern "C" fn Steam_TerminateGameConnection(
    _pipe: i32,
    _user: i32,
    _un_ip_server: u32,
    _us_port_server: u16,
) {
}

// Game server flat exports (stubs).
#[no_mangle] pub extern "C" fn Steam_GSBLoggedOn(_a: i32, _b: i32) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSBSecure(_a: i32, _b: i32) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSGetSteamID(_a: i32, _b: i32) -> u64 { 0 }
#[no_mangle] pub extern "C" fn Steam_GSLogOff(_a: i32, _b: i32) {}
#[no_mangle] pub extern "C" fn Steam_GSLogOn(
    _a: i32, _b: i32, _c: u64, _d: u32, _e: u16, _f: u16, _g: i32, _h: bool,
) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSRemoveUserConnect(_a: i32, _b: i32, _c: u64) {}
#[no_mangle] pub extern "C" fn Steam_GSSendSteam2UserConnect(
    _a: i32, _b: i32, _c: u64, _d: u32, _e: u32, _f: u16, _g: *const c_void, _h: i32,
) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSSendSteam3UserConnect(
    _a: i32, _b: i32, _c: u64, _d: u32, _e: *const c_void, _f: i32,
) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSSendUserDisconnect(_a: i32, _b: i32, _c: u64, _d: u32) {}
#[no_mangle] pub extern "C" fn Steam_GSSendUserStatusResponse(
    _a: i32, _b: i32, _c: u64, _d: i32, _e: *const c_void, _f: i32,
) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSSetServerType(
    _a: i32, _b: i32, _c: u32, _d: u32, _e: u16, _f: u16, _g: u16,
    _h: *const c_char, _i: *const c_char, _j: bool,
) {}
#[no_mangle] pub extern "C" fn Steam_GSSetSpawnCount(_a: i32, _b: i32, _c: u32) {}
#[no_mangle] pub extern "C" fn Steam_GSUpdateStatus(
    _a: i32, _b: i32, _c: i32, _d: i32, _e: i32,
    _f: *const c_char, _g: *const c_char, _h: *const c_char,
) -> bool { false }
#[no_mangle] pub extern "C" fn Steam_GSGetSteam2GetEncryptionKeyToSendToNewClient(
    _a: i32, _b: i32, _c: *mut c_void, _d: *mut u32, _e: u32,
) -> bool { false }

// ---- SteamAPI_* ------------------------------------------------------------

#[no_mangle]
pub extern "C" fn SteamAPI_RestartAppIfNecessary(un_own_app_id: u32) -> bool {
    log::log_info(&format!(
        "SteamAPI_RestartAppIfNecessary(appId={}) -> false (no restart needed)",
        un_own_app_id
    ));
    false
}

#[no_mangle]
pub extern "C" fn SteamAPI_Init() -> bool {
    let mut pipe = state::alloc_pipe();
    if pipe == 0 {
        pipe = state::state().pipe.load(Ordering::SeqCst);
    }
    let user = state::alloc_global_user(pipe);
    log::log_info(&format!(
        "SteamAPI_Init() -> true (pipe={} user={})",
        pipe, user
    ));
    true
}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_InitEx(p_out_err_msg: *mut c_char) -> i32 {
    SteamAPI_Init();
    if !p_out_err_msg.is_null() {
        unsafe { *p_out_err_msg = 0 };
    }
    0 // k_ESteamAPIInitResult_OK
}

#[no_mangle]
pub extern "C" fn SteamAPI_Shutdown() {
    let pipe = state::state().pipe.load(Ordering::SeqCst);
    if pipe != 0 {
        Steam_BReleaseSteamPipe(pipe);
    }
    log::log_info("SteamAPI_Shutdown()");
}

#[no_mangle]
pub extern "C" fn SteamAPI_IsSteamRunning() -> bool {
    true
}

#[no_mangle]
pub extern "C" fn SteamAPI_GetHSteamPipe() -> i32 {
    state::state().pipe.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn SteamAPI_GetHSteamUser() -> i32 {
    state::state().user.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn SteamAPI_ReleaseCurrentThreadMemory() {}

#[no_mangle]
pub extern "C" fn SteamAPI_SetTryCatchCallbacks(_b: bool) {}

#[no_mangle]
pub extern "C" fn SteamAPI_WriteMiniDump(_a: u32, _b: *mut c_void, _c: u32) {}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_RegisterCallback(p_callback: *mut c_void, i_callback: i32) {
    registry::register_callback(p_callback as usize, i_callback);
}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_UnregisterCallback(p_callback: *mut c_void) {
    registry::unregister_callback(p_callback as usize);
}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_RegisterCallResult(p_callback: *mut c_void, h_api_call: u64) {
    registry::register_call_result(p_callback as usize, h_api_call);
}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_UnregisterCallResult(p_callback: *mut c_void, h_api_call: u64) {
    registry::unregister_call_result(p_callback as usize, h_api_call);
}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_RunCallbacks() {
    let s = state::state();

    // Drain callback queue and dispatch each.
    loop {
        let msg = {
            let mut q = s.callback_mu.lock().expect("callback queue poisoned");
            if q.queue.is_empty() {
                break;
            }
            q.queue.pop_front().unwrap()
        };
        let cbs = registry::find_callbacks(msg.id);
        let payload: *mut c_void = if msg.body.is_empty() {
            core::ptr::null_mut()
        } else {
            msg.body.as_ptr() as *mut c_void
        };
        for cb_ptr in cbs {
            unsafe {
                run_callback_slot0(cb_ptr as *mut c_void, payload);
            }
        }
    }

    // Drain call-result table for hCalls that have registered callbacks.
    struct PendingDispatch {
        h_call: u64,
        io_failure: bool,
        body: Vec<u8>,
        cbs: Vec<usize>,
    }
    let mut to_dispatch: Vec<PendingDispatch> = Vec::new();
    {
        let mut t = s.call_results_mu.lock().expect("call results poisoned");
        let keys: Vec<u64> = t.pending.keys().copied().collect();
        for k in keys {
            let cbs = registry::find_call_result_cbs(k);
            if cbs.is_empty() {
                continue;
            }
            let m = t.pending.remove(&k).unwrap();
            to_dispatch.push(PendingDispatch {
                h_call: m.h_call,
                io_failure: m.io_failure,
                body: m.body,
                cbs,
            });
        }
    }
    for d in to_dispatch {
        let payload: *mut c_void = if d.body.is_empty() {
            core::ptr::null_mut()
        } else {
            d.body.as_ptr() as *mut c_void
        };
        for cb_ptr in d.cbs {
            unsafe {
                run_call_result_slot1(cb_ptr as *mut c_void, payload, d.io_failure, d.h_call);
            }
        }
    }
}

/// Read vtable[0] of a `CCallbackBase`-shaped object and call it.
/// The C++ side relies on vtable pointer at offset 0 of the object.
unsafe fn run_callback_slot0(this: *mut c_void, payload: *mut c_void) {
    type RunFn = unsafe extern "C" fn(*mut c_void, *mut c_void);
    unsafe {
        let vtable_ptr = *(this as *const *const usize);
        let slot0 = *vtable_ptr;
        let run: RunFn = core::mem::transmute(slot0);
        run(this, payload);
    }
}

unsafe fn run_call_result_slot1(
    this: *mut c_void,
    payload: *mut c_void,
    io_failure: bool,
    h_steam_api_call: u64,
) {
    type RunResultFn =
        unsafe extern "C" fn(*mut c_void, *mut c_void, bool, u64);
    unsafe {
        let vtable_ptr = *(this as *const *const usize);
        let slot1 = *vtable_ptr.add(1);
        let run: RunResultFn = core::mem::transmute(slot1);
        run(this, payload, io_failure, h_steam_api_call);
    }
}

#[no_mangle]
pub extern "C" fn SteamAPI_GetSteamInstallPath() -> *const c_char {
    core::ptr::null()
}

#[no_mangle]
pub extern "C" fn SteamClient() -> *mut c_void {
    let mut err: i32 = 0;
    let v = b"SteamClient020\0";
    unsafe { CreateInterface(v.as_ptr() as *const c_char, &mut err) }
}

// ---- SteamGameServer_* -----------------------------------------------------

#[no_mangle]
pub extern "C" fn SteamGameServer_Init(
    _un_ip: u32,
    _us_game_port: u16,
    _us_query_port: u16,
    _e_server_mode: i32,
    _pch_version_string: *const c_char,
) -> bool {
    log::log_info("SteamGameServer_Init -> false (game-server mode not implemented)");
    false
}

#[no_mangle] pub extern "C" fn SteamGameServer_Shutdown() {}
#[no_mangle] pub extern "C" fn SteamGameServer_BSecure() -> bool { false }
#[no_mangle] pub extern "C" fn SteamGameServer_GetSteamID() -> u64 { 0 }
#[no_mangle] pub extern "C" fn SteamGameServer_GetHSteamPipe() -> i32 { 0 }
#[no_mangle] pub extern "C" fn SteamGameServer_GetHSteamUser() -> i32 { 0 }
#[no_mangle] pub extern "C" fn SteamGameServer_RunCallbacks() {}

// ---- Breakpad_* (no-op stubs) ---------------------------------------------

#[no_mangle] pub extern "C" fn Breakpad_SteamMiniDumpInit(
    _a: u32, _b: *const c_char, _c: *const c_char,
) {}
#[no_mangle] pub extern "C" fn Breakpad_SteamSendMiniDump(
    _a: *mut c_void, _b: u32, _c: *const c_char,
) {}
#[no_mangle] pub extern "C" fn Breakpad_SteamSetAppID(_a: u32) {}
#[no_mangle] pub extern "C" fn Breakpad_SteamSetSteamID(_a: u64) {}
#[no_mangle] pub extern "C" fn Breakpad_SteamWriteMiniDumpSetComment(_a: *const c_char) {}
#[no_mangle] pub extern "C" fn Breakpad_SteamWriteMiniDumpUsingExceptionInfoWithBuildId(
    _a: u32, _b: *mut c_void, _c: u32,
) {}

// ---- SteamAPI_ManualDispatch_* --------------------------------------------

#[repr(C)]
struct CallbackMsgWire {
    h_steam_user: i32,
    i_callback: i32,
    pub_param: *mut u8,
    cub_param: i32,
    _pad: i32,
}
const _: () = assert!(std::mem::size_of::<CallbackMsgWire>() == 24);

static MANUAL_DISPATCH_ACTIVE: AtomicBool = AtomicBool::new(false);

#[no_mangle]
pub extern "C" fn SteamAPI_ManualDispatch_Init() {
    MANUAL_DISPATCH_ACTIVE.store(true, Ordering::Release);
    log::log_info("SteamAPI_ManualDispatch_Init() — manual callback dispatch armed");
}

#[no_mangle]
pub extern "C" fn SteamAPI_ManualDispatch_RunFrame(_h_steam_pipe: i32) {}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_ManualDispatch_GetNextCallback(
    _h_steam_pipe: i32,
    p_msg_out: *mut c_void,
) -> bool {
    if p_msg_out.is_null() {
        return false;
    }
    let s = state::state();
    let (msg, last_param_ptr, last_param_len) = {
        let mut q = s.callback_mu.lock().expect("callback queue poisoned");
        if q.queue.is_empty() {
            return false;
        }
        let m = q.queue.pop_front().unwrap();
        q.last_param = m.body.clone();
        let ptr = if q.last_param.is_empty() {
            core::ptr::null_mut()
        } else {
            q.last_param.as_mut_ptr()
        };
        let len = q.last_param.len() as i32;
        (m, ptr, len)
    };
    let out = p_msg_out as *mut CallbackMsgWire;
    unsafe {
        (*out).h_steam_user = msg.user;
        (*out).i_callback = msg.id;
        (*out).pub_param = last_param_ptr;
        (*out).cub_param = last_param_len;
        (*out)._pad = 0;
    }
    true
}

#[no_mangle]
pub extern "C" fn SteamAPI_ManualDispatch_FreeLastCallback(_h_steam_pipe: i32) {
    let s = state::state();
    let mut q = s.callback_mu.lock().expect("callback queue poisoned");
    q.last_param.clear();
}

#[no_mangle]
pub unsafe extern "C" fn SteamAPI_ManualDispatch_GetAPICallResult(
    _h_steam_pipe: i32,
    h_call: u64,
    p_callback: *mut u8,
    cb_callback: i32,
    i_callback_expected: i32,
    pb_failed: *mut bool,
) -> bool {
    let s = state::state();
    let mut t = s.call_results_mu.lock().expect("call results poisoned");
    let Some(msg) = t.pending.remove(&h_call) else {
        return false;
    };
    if i_callback_expected != 0 && msg.callback_id != i_callback_expected {
        let got = msg.callback_id;
        let h = msg.h_call;
        t.pending.insert(h, msg);
        log::log_info(&format!(
            "ManualDispatch_GetAPICallResult: hCall=0x{:x} callback mismatch \
             (expected={} got={}) — re-queueing",
            h_call, i_callback_expected, got
        ));
        return false;
    }
    if !p_callback.is_null() && cb_callback > 0 && !msg.body.is_empty() {
        let n = (cb_callback as usize).min(msg.body.len());
        unsafe {
            core::ptr::copy_nonoverlapping(msg.body.as_ptr(), p_callback, n);
        }
    }
    if !pb_failed.is_null() {
        unsafe { *pb_failed = msg.io_failure };
    }
    let _ = (CallbackMsg::default(), CallResultMsg::default());
    true
}

// ---- CreateInterface (defined in client.rs but accessed here) -------------

unsafe extern "C" {
    fn CreateInterface(version_name: *const c_char, return_code: *mut i32) -> *mut c_void;
}

/// Force-link the `client::` module so `CreateInterface` is present.
pub fn _force_link_client() {
    let _ = client::dispatch_create_interface as usize;
}
