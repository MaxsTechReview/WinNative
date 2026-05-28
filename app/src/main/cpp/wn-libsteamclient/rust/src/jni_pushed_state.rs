//! Port of `jni_pushed_state.cpp`. Provides:
//!   - `register_observers()` — installs Rust handlers on the `wn_cm_bridge_*` C-ABI
//!     observers, mirroring the C++ `__attribute__((constructor))` block.
//!   - All `Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_native*`
//!     JNI exports, byte-for-byte equivalent in observable behaviour.

#![allow(non_snake_case)]
#![allow(clippy::missing_safety_doc)]

use core::ffi::{c_char, c_void, CStr};
use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::time::SystemTime;

use jni::objects::{JByteArray, JClass, JIntArray, JLongArray, JObjectArray, JString};
use jni::sys::{
    jboolean, jbyteArray, jdouble, jfloat, jint, jlong, jstring, JNI_FALSE, JNI_TRUE,
};
use jni::JNIEnv;

use crate::bridge::{
    self, wn_cm_bridge_dispatch_persona, wn_cm_bridge_inject_test_account_info,
    wn_cm_bridge_inject_test_friends_list, wn_cm_bridge_inject_test_license_list,
    wn_cm_bridge_inject_test_logon_state, wn_cm_bridge_inject_test_ownership_ticket,
    wn_cm_bridge_register_account_info_observer, wn_cm_bridge_register_friends_list_observer,
    wn_cm_bridge_register_license_list_observer, wn_cm_bridge_register_lobby_chat_msg_observer,
    wn_cm_bridge_register_lobby_data_observer, wn_cm_bridge_register_lobby_membership_observer,
    wn_cm_bridge_register_logon_state_observer, wn_cm_bridge_register_persona_observer,
    wn_cm_bridge_register_server_realtime_observer, wn_cm_get_cached_app_ownership_ticket,
    wn_cm_notify_games_played, wn_cm_request_user_info_bulk, wn_cm_set_persona_state,
    WnCmAccountInfo, WnCmLicenseEntry, WnCmLobbyData, WnCmPersonaEvent, WnCmRichPresenceKV,
};
use crate::callbacks as cb;
use crate::iface;
use crate::state::{
    self, AchievementEntry, CloudFileEntry, DlcEntry, ImageEntry, LicenseEntry, LobbyChatEntry,
    OverlayRequest, WorkshopItemInfo,
};

// ---- Helpers --------------------------------------------------------------

const U32_INVALID: u32 = u32::MAX;

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn jstr_to_string(env: &mut JNIEnv, value: &JString) -> String {
    if value.is_null() {
        return String::new();
    }
    env.get_string(value)
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_default()
}

fn new_string_or_null(env: &mut JNIEnv, value: &str) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn byte_array_or_null(env: &JNIEnv, bytes: &[u8]) -> jbyteArray {
    env.byte_array_from_slice(bytes)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn int_array_to_vec(env: &JNIEnv, arr: &JIntArray) -> Vec<i32> {
    let len = env.get_array_length(arr).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut buf = vec![0i32; len as usize];
    if env.get_int_array_region(arr, 0, &mut buf).is_err() {
        return Vec::new();
    }
    buf
}

fn long_array_to_vec(env: &JNIEnv, arr: &JLongArray) -> Vec<i64> {
    let len = env.get_array_length(arr).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut buf = vec![0i64; len as usize];
    if env.get_long_array_region(arr, 0, &mut buf).is_err() {
        return Vec::new();
    }
    buf
}

fn byte_array_to_vec(env: &JNIEnv, arr: &JByteArray) -> Vec<u8> {
    env.convert_byte_array(arr).unwrap_or_default()
}

fn jobject_array_to_strings(env: &mut JNIEnv, arr: &JObjectArray) -> Vec<String> {
    let len = env.get_array_length(arr).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut out = Vec::with_capacity(len as usize);
    for i in 0..len {
        match env.get_object_array_element(arr, i) {
            Ok(obj) => {
                if obj.is_null() {
                    out.push(String::new());
                } else {
                    let js = JString::from(obj);
                    out.push(jstr_to_string(env, &js));
                }
            }
            Err(_) => out.push(String::new()),
        }
    }
    out
}

fn boolean_array_to_vec(env: &JNIEnv, arr: jni::sys::jbooleanArray) -> Vec<bool> {
    if arr.is_null() {
        return Vec::new();
    }
    let arr_obj = unsafe { jni::objects::JBooleanArray::from_raw(arr) };
    let len = env.get_array_length(&arr_obj).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut buf = vec![0u8; len as usize];
    if env.get_boolean_array_region(&arr_obj, 0, &mut buf).is_err() {
        return Vec::new();
    }
    buf.into_iter().map(|b| b != 0).collect()
}

fn push_user_callback<T>(id: i32, payload: &T) {
    let user = state::state().user.load(Ordering::SeqCst);
    let raw = unsafe {
        std::slice::from_raw_parts(payload as *const T as *const u8, std::mem::size_of::<T>())
    };
    state::push_callback_bytes(user, id, raw);
}

fn emit_persona_state_change(steam_id: u64, flags: i32) {
    if steam_id == 0 {
        return;
    }
    let payload = cb::PersonaStateChange {
        m_ulSteamID: steam_id,
        m_nChangeFlags: flags,
        _pad: 0,
    };
    push_user_callback(cb::K_PERSONA_STATE_CHANGE, &payload);
}

unsafe fn vtable_fn(obj: *mut c_void, slot: usize) -> *const c_void {
    if obj.is_null() {
        return std::ptr::null();
    }
    let vt = unsafe { *(obj as *const *const *const c_void) };
    if vt.is_null() {
        return std::ptr::null();
    }
    unsafe { *vt.add(slot) }
}

// ---- Observer handlers ----------------------------------------------------

extern "C" fn on_persona_event(ev: *const WnCmPersonaEvent) {
    if ev.is_null() {
        return;
    }
    let ev = unsafe { &*ev };
    if ev.sid == 0 {
        return;
    }

    let p = state::pushed();
    let app_id = p.app_id.load(Ordering::SeqCst);
    let self_sid = p.steam_id.load(Ordering::SeqCst);
    let is_self = ev.sid == self_sid;

    let mut flags: i32 = 0;
    let mut had_rp = false;

    // Name + lock the right sub-struct based on whether this is self or friend.
    let name_str = if !ev.name.is_null() {
        unsafe { CStr::from_ptr(ev.name).to_string_lossy().into_owned() }
    } else {
        String::new()
    };

    {
        if is_self && !name_str.is_empty() {
            let mut text = p.text.lock().expect("pushed.text poisoned");
            if text.persona_name != name_str {
                text.persona_name = name_str.clone();
                flags |= cb::K_PERSONA_CHANGE_NAME;
            }
        } else if !name_str.is_empty() {
            let mut friends = p.friends.lock().expect("pushed.friends poisoned");
            let slot = friends
                .friend_persona_names
                .entry(ev.sid)
                .or_insert_with(String::new);
            if *slot != name_str {
                *slot = name_str.clone();
                flags |= cb::K_PERSONA_CHANGE_NAME;
            }
        }
    }

    if ev.persona_state != U32_INVALID {
        let prev: u32 = if is_self {
            p.persona_state.load(Ordering::SeqCst) as u32
        } else {
            let friends = p.friends.lock().expect("pushed.friends poisoned");
            friends
                .friend_persona_states
                .get(&ev.sid)
                .copied()
                .unwrap_or(0)
        };
        if prev != ev.persona_state {
            if is_self {
                p.persona_state
                    .store(ev.persona_state as i32, Ordering::SeqCst);
            } else {
                let mut friends = p.friends.lock().expect("pushed.friends poisoned");
                friends
                    .friend_persona_states
                    .insert(ev.sid, ev.persona_state);
            }
            flags |= cb::K_PERSONA_CHANGE_STATUS;
            if prev == 0 && ev.persona_state != 0 {
                flags |= cb::K_PERSONA_CHANGE_COME_ONLINE;
            }
            if prev != 0 && ev.persona_state == 0 {
                flags |= cb::K_PERSONA_CHANGE_GONE_OFFLINE;
            }
        }
    }

    {
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        let slot = friends.friend_game_played_app.entry(ev.sid).or_insert(0);
        if *slot != ev.game_played_app {
            *slot = ev.game_played_app;
            flags |= cb::K_PERSONA_CHANGE_GAME_PLAYED;
        }
    }

    if !ev.avatar_hash.is_null() && ev.avatar_hash_len > 0 {
        let hash =
            unsafe { std::slice::from_raw_parts(ev.avatar_hash, ev.avatar_hash_len).to_vec() };
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        let slot = friends
            .friend_avatar_hashes
            .entry(ev.sid)
            .or_insert_with(Vec::new);
        if *slot != hash {
            *slot = hash;
            flags |= cb::K_PERSONA_CHANGE_AVATAR;
        }
    }

    if !ev.rp_pairs.is_null() && ev.rp_count > 0 {
        had_rp = true;
        let mut fresh: Vec<(String, String)> = Vec::with_capacity(ev.rp_count);
        for i in 0..ev.rp_count {
            let kv: &WnCmRichPresenceKV = unsafe { &*ev.rp_pairs.add(i) };
            let k = if kv.key.is_null() {
                String::new()
            } else {
                unsafe { CStr::from_ptr(kv.key).to_string_lossy().into_owned() }
            };
            let v = if kv.value.is_null() {
                String::new()
            } else {
                unsafe { CStr::from_ptr(kv.value).to_string_lossy().into_owned() }
            };
            fresh.push((k, v));
        }
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        let slot = friends.rich_presence.entry(ev.sid).or_insert_with(Vec::new);
        if *slot != fresh {
            *slot = fresh;
        }
    }

    if flags != 0 {
        emit_persona_state_change(ev.sid, flags);
    }

    if had_rp {
        let payload = cb::FriendRichPresenceUpdate {
            m_steamIDFriend: ev.sid,
            m_nAppID: app_id,
        };
        push_user_callback(cb::K_FRIEND_RICH_PRESENCE_UPDATE, &payload);
    }
}

extern "C" fn on_logon_state(logged_on: bool) {
    state::set_logged_on(logged_on, 6);
}

extern "C" fn on_friends_list(sids: *const u64, count: usize) {
    let p = state::pushed();
    let mut friends = p.friends.lock().expect("pushed.friends poisoned");
    friends.friends.clear();
    if !sids.is_null() && count > 0 {
        let slice = unsafe { std::slice::from_raw_parts(sids, count) };
        friends.friends.reserve(count);
        for &sid in slice {
            if sid != 0 {
                friends.friends.push(sid);
            }
        }
    }
    let mirrored = friends.friends.len();
    drop(friends);
    crate::log::log_info(&format!(
        "friends-list observer: {} mutual friend(s) mirrored",
        mirrored
    ));
}

extern "C" fn on_license_list(entries: *const WnCmLicenseEntry, count: usize) {
    let p = state::pushed();
    let mut lic = p.licenses.lock().expect("pushed.licenses poisoned");
    lic.licenses.clear();
    if !entries.is_null() && count > 0 {
        let slice = unsafe { std::slice::from_raw_parts(entries, count) };
        for src in slice {
            if src.package_id == 0 {
                continue;
            }
            lic.licenses.insert(
                src.package_id,
                LicenseEntry {
                    package_id: src.package_id,
                    owner_id: src.owner_id,
                    time_created: src.time_created,
                    license_type: src.license_type,
                    flags: src.flags,
                    change_number: src.change_number,
                    minute_limit: src.minute_limit,
                    minutes_used: src.minutes_used,
                },
            );
        }
    }
    crate::log::log_info(&format!(
        "license-list observer: {} license(s) mirrored",
        lic.licenses.len()
    ));
}

extern "C" fn on_account_info(info: *const WnCmAccountInfo) {
    if info.is_null() {
        return;
    }
    let info = unsafe { &*info };
    let p = state::pushed();
    p.account_two_factor_enabled
        .store(info.two_factor_enabled, Ordering::SeqCst);
    p.account_phone_verified
        .store(info.phone_verified, Ordering::SeqCst);
    p.account_phone_identifying
        .store(info.phone_identifying, Ordering::SeqCst);
    p.account_phone_requires_verification
        .store(info.phone_requires_verification, Ordering::SeqCst);

    if !info.persona_name.is_null() && info.persona_name_len > 0 {
        let bytes =
            unsafe { std::slice::from_raw_parts(info.persona_name as *const u8, info.persona_name_len) };
        let name = String::from_utf8_lossy(bytes).into_owned();
        let mut text = p.text.lock().expect("pushed.text poisoned");
        text.persona_name = name;
    }
    if !info.ip_country.is_null() && info.ip_country_len > 0 {
        let bytes =
            unsafe { std::slice::from_raw_parts(info.ip_country as *const u8, info.ip_country_len) };
        let cc = String::from_utf8_lossy(bytes).into_owned();
        let mut text = p.text.lock().expect("pushed.text poisoned");
        text.ip_country = cc;
        p.ip_country_set.store(1, Ordering::SeqCst);
    }
    crate::log::log_info(&format!(
        "account-info observer: 2FA={} phone_v={} phone_id={} phone_nv={}",
        info.two_factor_enabled,
        info.phone_verified,
        info.phone_identifying,
        info.phone_requires_verification
    ));
}

extern "C" fn on_server_realtime(server_realtime: u32) {
    if server_realtime == 0 {
        return;
    }
    let p = state::pushed();
    let ms = now_ms();
    p.server_realtime.store(server_realtime, Ordering::SeqCst);
    p.server_realtime_anchor_local_ms
        .store(ms, Ordering::SeqCst);
    crate::log::log_info(&format!(
        "server-realtime observer: {} (anchored at local {} ms)",
        server_realtime, ms
    ));
}

#[repr(C)]
struct LobbyDataUpdate {
    lobby: u64,
    member: u64,
    success: u8,
    _pad: [u8; 7],
}

#[repr(C)]
struct LobbyChatMsg {
    lobby: u64,
    user: u64,
    chat_type: u8,
    _pad: [u8; 3],
    chat_id: u32,
}

#[repr(C)]
struct LobbyChatUpdate {
    lobby: u64,
    user_changed: u64,
    making_change: u64,
    state_change: u32,
    _pad: u32,
}

extern "C" fn on_lobby_data(data: *const WnCmLobbyData) {
    if data.is_null() {
        return;
    }
    let data = unsafe { &*data };
    let p = state::pushed();
    {
        let mut lobbies = p.lobbies.lock().expect("pushed.lobbies poisoned");
        let lobby = lobbies
            .active_lobbies
            .entry(data.steam_id_lobby)
            .or_default();
        lobby.app_id = data.app_id;
        lobby.owner_sid = data.steam_id_owner;
        lobby.max_members = data.max_members;
        lobby.lobby_type = data.lobby_type;
        lobby.lobby_flags = data.lobby_flags;
        lobby.members.clear();
        if !data.members.is_null() && data.member_count > 0 {
            let slice = unsafe { std::slice::from_raw_parts(data.members, data.member_count) };
            for m in slice {
                let mb = lobby.members.entry(m.steam_id).or_default();
                if !m.persona_name.is_null() {
                    mb.persona_name = unsafe {
                        CStr::from_ptr(m.persona_name).to_string_lossy().into_owned()
                    };
                }
                if !m.metadata_bytes.is_null() && m.metadata_len > 0 {
                    let bytes = unsafe {
                        std::slice::from_raw_parts(m.metadata_bytes, m.metadata_len)
                    };
                    mb.data
                        .insert("__raw_metadata".to_string(), String::from_utf8_lossy(bytes).into_owned());
                }
            }
        }
    }
    let payload = LobbyDataUpdate {
        lobby: data.steam_id_lobby,
        member: 0,
        success: 1,
        _pad: [0; 7],
    };
    push_user_callback(505, &payload);
}

extern "C" fn on_lobby_chat_msg(lobby_sid: u64, sender_sid: u64, data: *const u8, n: usize) {
    if lobby_sid == 0 {
        return;
    }
    let chat_id: u32 = {
        let p = state::pushed();
        let mut lobbies = p.lobbies.lock().expect("pushed.lobbies poisoned");
        let ring = lobbies.lobby_chat_buffer.entry(lobby_sid).or_default();
        if ring.len() >= 1024 {
            ring.remove(0);
        }
        let body = if !data.is_null() && n > 0 {
            unsafe { std::slice::from_raw_parts(data, n).to_vec() }
        } else {
            Vec::new()
        };
        ring.push(LobbyChatEntry {
            sender_sid,
            chat_type: 1,
            body,
        });
        (ring.len() - 1) as u32
    };
    let payload = LobbyChatMsg {
        lobby: lobby_sid,
        user: sender_sid,
        chat_type: 1,
        _pad: [0; 3],
        chat_id,
    };
    push_user_callback(507, &payload);
}

extern "C" fn on_lobby_membership(
    joined: i32,
    lobby_sid: u64,
    user_sid: u64,
    persona_name: *const c_char,
) {
    if lobby_sid == 0 || user_sid == 0 {
        return;
    }
    {
        let p = state::pushed();
        let mut lobbies = p.lobbies.lock().expect("pushed.lobbies poisoned");
        let lobby = lobbies.active_lobbies.entry(lobby_sid).or_default();
        if joined != 0 {
            let mb = lobby.members.entry(user_sid).or_default();
            if !persona_name.is_null() {
                mb.persona_name = unsafe { CStr::from_ptr(persona_name).to_string_lossy().into_owned() };
            }
        } else {
            lobby.members.remove(&user_sid);
        }
    }
    let payload = LobbyChatUpdate {
        lobby: lobby_sid,
        user_changed: user_sid,
        making_change: user_sid,
        state_change: if joined != 0 { 0x1 } else { 0x2 },
        _pad: 0,
    };
    push_user_callback(506, &payload);
}

pub fn register_observers() {
    unsafe {
        wn_cm_bridge_register_persona_observer(on_persona_event);
        wn_cm_bridge_register_logon_state_observer(on_logon_state);
        wn_cm_bridge_register_friends_list_observer(on_friends_list);
        wn_cm_bridge_register_license_list_observer(on_license_list);
        wn_cm_bridge_register_account_info_observer(on_account_info);
        wn_cm_bridge_register_server_realtime_observer(on_server_realtime);
        wn_cm_bridge_register_lobby_data_observer(on_lobby_data);
        wn_cm_bridge_register_lobby_chat_msg_observer(on_lobby_chat_msg);
        wn_cm_bridge_register_lobby_membership_observer(on_lobby_membership);
    }
    let _ = &bridge::DUMMY;
}

// ---- JNI exports ----------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetSteamId(
    _env: JNIEnv,
    _cls: JClass,
    steam_id: jlong,
) {
    let p = state::pushed();
    let sid = steam_id as u64;
    p.steam_id.store(sid, Ordering::SeqCst);
    p.account_id
        .store((sid & 0xFFFF_FFFF) as u32, Ordering::SeqCst);
    crate::log::log_info(&format!("set_steam_id({})", sid));
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetLoggedOn(
    _env: JNIEnv,
    _cls: JClass,
    logged_on: jboolean,
) {
    let now = logged_on != JNI_FALSE;
    state::set_logged_on(now, 6);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetPersonaName(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) {
    let p = state::pushed();
    let name = jstr_to_string(&mut env, &jname);
    let (changed, self_sid) = {
        let mut text = p.text.lock().expect("pushed.text poisoned");
        let changed = text.persona_name != name;
        text.persona_name = name;
        (changed, p.steam_id.load(Ordering::SeqCst))
    };
    if changed {
        emit_persona_state_change(self_sid, cb::K_PERSONA_CHANGE_NAME);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetPersonaState(
    _env: JNIEnv,
    _cls: JClass,
    state_in: jint,
) {
    let p = state::pushed();
    let prev = p.persona_state.swap(state_in, Ordering::SeqCst);
    if prev == state_in {
        return;
    }
    let mut flags = cb::K_PERSONA_CHANGE_STATUS;
    if prev == 0 && state_in != 0 {
        flags |= cb::K_PERSONA_CHANGE_COME_ONLINE;
    }
    if prev != 0 && state_in == 0 {
        flags |= cb::K_PERSONA_CHANGE_GONE_OFFLINE;
    }
    emit_persona_state_change(p.steam_id.load(Ordering::SeqCst), flags);
    unsafe {
        wn_cm_set_persona_state(state_in);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppId(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
) {
    let app = app_id as u32;
    let prev = state::pushed().app_id.swap(app, Ordering::SeqCst);
    if prev == app {
        return;
    }
    unsafe {
        wn_cm_notify_games_played(app);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetIPCountry(
    mut env: JNIEnv,
    _cls: JClass,
    jcc: JString,
) {
    let cc = jstr_to_string(&mut env, &jcc);
    let p = state::pushed();
    let mut text = p.text.lock().expect("pushed.text poisoned");
    text.ip_country = cc;
    p.ip_country_set.store(1, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetUiLanguage(
    mut env: JNIEnv,
    _cls: JClass,
    jlang: JString,
) {
    let lang = jstr_to_string(&mut env, &jlang);
    let p = state::pushed();
    let mut text = p.text.lock().expect("pushed.text poisoned");
    text.ui_language = lang;
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetOwnedApps(
    env: JNIEnv,
    _cls: JClass,
    app_ids: JIntArray,
) {
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    apps.owned_apps.clear();
    if app_ids.is_null() {
        return;
    }
    let buf = int_array_to_vec(&env, &app_ids);
    for v in buf {
        if v > 0 {
            apps.owned_apps.insert(v as u32);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetInstalledApps(
    env: JNIEnv,
    _cls: JClass,
    app_ids: JIntArray,
) {
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    apps.installed_apps.clear();
    if app_ids.is_null() {
        return;
    }
    let buf = int_array_to_vec(&env, &app_ids);
    for v in buf {
        if v > 0 {
            apps.installed_apps.insert(v as u32);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppInstallDir(
    mut env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    jdir: JString,
) {
    if app_id <= 0 {
        return;
    }
    let dir = jstr_to_string(&mut env, &jdir);
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    if dir.is_empty() {
        apps.app_install_dirs.remove(&(app_id as u32));
    } else {
        apps.app_install_dirs.insert(app_id as u32, dir);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendsList(
    env: JNIEnv,
    _cls: JClass,
    steam_ids: JLongArray,
) {
    let p = state::pushed();
    let mut friends = p.friends.lock().expect("pushed.friends poisoned");
    friends.friends.clear();
    if steam_ids.is_null() {
        return;
    }
    let buf = long_array_to_vec(&env, &steam_ids);
    for v in buf {
        if v != 0 {
            friends.friends.push(v as u64);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppBuildId(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    build_id: jint,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    if build_id <= 0 {
        apps.app_build_ids.remove(&(app_id as u32));
    } else {
        apps.app_build_ids
            .insert(app_id as u32, build_id as u32);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppNames(
    mut env: JNIEnv,
    _cls: JClass,
    app_ids: JIntArray,
    names: JObjectArray,
) {
    if app_ids.is_null() || names.is_null() {
        return;
    }
    let ids = int_array_to_vec(&env, &app_ids);
    let name_strs = jobject_array_to_strings(&mut env, &names);
    if ids.is_empty() || ids.len() != name_strs.len() {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    for (i, id) in ids.iter().enumerate() {
        if *id <= 0 {
            continue;
        }
        let key = *id as u32;
        let name = &name_strs[i];
        if name.is_empty() {
            apps.app_names.remove(&key);
        } else {
            apps.app_names.insert(key, name.clone());
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectAccountInfo(
    _env: JNIEnv,
    _cls: JClass,
    two_fa: jboolean,
    phone_v: jboolean,
    phone_id: jboolean,
    phone_nv: jboolean,
) {
    let info = WnCmAccountInfo {
        persona_name: std::ptr::null(),
        persona_name_len: 0,
        ip_country: std::ptr::null(),
        ip_country_len: 0,
        two_factor_enabled: two_fa != JNI_FALSE,
        phone_verified: phone_v != JNI_FALSE,
        phone_identifying: phone_id != JNI_FALSE,
        phone_requires_verification: phone_nv != JNI_FALSE,
    };
    unsafe {
        wn_cm_bridge_inject_test_account_info(&info);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAccountFlag(
    _env: JNIEnv,
    _cls: JClass,
    flag_kind: jint,
    on: jboolean,
) {
    let p = state::pushed();
    let b = on != JNI_FALSE;
    match flag_kind {
        0 => p.account_phone_verified.store(b, Ordering::SeqCst),
        1 => p.account_two_factor_enabled.store(b, Ordering::SeqCst),
        2 => p.account_phone_identifying.store(b, Ordering::SeqCst),
        3 => p.account_phone_requires_verification
            .store(b, Ordering::SeqCst),
        _ => {}
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUserBool(
    _env: JNIEnv,
    _cls: JClass,
    slot: jint,
) -> jboolean {
    if !(26..=29).contains(&slot) {
        return JNI_FALSE;
    }
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, slot as usize) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetPlayerNickname(
    mut env: JNIEnv,
    _cls: JClass,
    sid: jlong,
    jnick: JString,
) {
    if sid == 0 {
        return;
    }
    let p = state::pushed();
    let key = sid as u64;
    let changed = {
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        if jnick.is_null() {
            friends.player_nicknames.remove(&key).is_some()
        } else {
            let name = jstr_to_string(&mut env, &jnick);
            if name.is_empty() {
                friends.player_nicknames.remove(&key).is_some()
            } else {
                match friends.player_nicknames.get_mut(&key) {
                    Some(slot) if *slot == name => false,
                    Some(slot) => {
                        *slot = name;
                        true
                    }
                    None => {
                        friends.player_nicknames.insert(key, name);
                        true
                    }
                }
            }
        }
    };
    if changed {
        emit_persona_state_change(key, cb::K_PERSONA_CHANGE_NICKNAME);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetPlayerNickname(
    mut env: JNIEnv,
    _cls: JClass,
    sid: jlong,
) -> jstring {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return std::ptr::null_mut();
    }
    let f = unsafe { vtable_fn(obj, 11) };
    if f.is_null() {
        return std::ptr::null_mut();
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> *const c_char = unsafe { std::mem::transmute(f) };
    let nick = fnp(obj, sid as u64);
    if nick.is_null() {
        std::ptr::null_mut()
    } else {
        let s = unsafe { CStr::from_ptr(nick).to_string_lossy().into_owned() };
        new_string_or_null(&mut env, &s)
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCheckFileSignature(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jlong {
    let obj = iface::isteam_utils::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 19) };
    if f.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, c_name.as_ptr()) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedSteamId(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    state::pushed().steam_id.load(Ordering::SeqCst) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedPersonaName(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let name = {
        let text = state::pushed().text.lock().expect("pushed.text poisoned");
        text.persona_name.clone()
    };
    new_string_or_null(&mut env, &name)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedIpCountry(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let s = if state::pushed().ip_country_set.load(Ordering::SeqCst) != 0 {
        state::pushed()
            .text
            .lock()
            .expect("pushed.text poisoned")
            .ip_country
            .clone()
    } else {
        String::new()
    };
    new_string_or_null(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedUiLanguage(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let s = state::pushed()
        .text
        .lock()
        .expect("pushed.text poisoned")
        .ui_language
        .clone();
    new_string_or_null(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedServerRealTime(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    let p = state::pushed();
    let anchor = p.server_realtime.load(Ordering::SeqCst);
    let anchor_ms = p.server_realtime_anchor_local_ms.load(Ordering::SeqCst);
    if anchor == 0 || anchor_ms == 0 {
        return 0;
    }
    let n = now_ms();
    let mut elapsed_s = (n - anchor_ms) / 1000;
    if elapsed_s < 0 {
        elapsed_s = 0;
    }
    (anchor as i64 + elapsed_s) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedPersonaState(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed().persona_state.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedLoggedOn(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    if state::state().logged_on.load(Ordering::SeqCst) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedAppId(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed().app_id.load(Ordering::SeqCst) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedOwnedAppCount(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed()
        .apps
        .lock()
        .expect("pushed.apps poisoned")
        .owned_apps
        .len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedInstalledAppCount(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed()
        .apps
        .lock()
        .expect("pushed.apps poisoned")
        .installed_apps
        .len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedFriendCount(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed()
        .friends
        .lock()
        .expect("pushed.friends poisoned")
        .friends
        .len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedFirstFriend(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    let friends = state::pushed()
        .friends
        .lock()
        .expect("pushed.friends poisoned");
    friends.friends.first().copied().unwrap_or(0) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedCloudFileCount(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed()
        .cloud
        .lock()
        .expect("pushed.cloud poisoned")
        .cloud_files
        .len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedCloudEnabledAccount(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    if state::pushed().cloud_enabled_account.load(Ordering::SeqCst) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedCloudEnabledApp(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    if state::pushed().cloud_enabled_app.load(Ordering::SeqCst) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeGetPushedEncryptedAppTicketSize(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
) -> jint {
    if app_id <= 0 {
        return 0;
    }
    let apps = state::pushed().apps.lock().expect("pushed.apps poisoned");
    apps.encrypted_app_tickets
        .get(&(app_id as u32))
        .map(|v| v.len() as jint)
        .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileShare(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jlong {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 7) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, c_name.as_ptr()) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticAppsGetFileDetails(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jlong {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() || jname.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 25) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, c_name.as_ptr()) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetSelfPlayerLevel(
    _env: JNIEnv,
    _cls: JClass,
    level: jint,
) {
    state::pushed()
        .self_player_level
        .store(level.max(0), Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetSelfGameBadge(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    n_series: jint,
    b_foil: jboolean,
    tier: jint,
) {
    if app_id <= 0 {
        return;
    }
    let key: i32 = (app_id & 0x0FFF_FFFF)
        | ((n_series & 0x07) << 28)
        | if b_foil != JNI_FALSE { 1i32 << 31 } else { 0 };
    let p = state::pushed();
    let mut friends = p.friends.lock().expect("pushed.friends poisoned");
    if tier < 0 {
        friends.self_game_badges.remove(&key);
    } else {
        friends.self_game_badges.insert(key, tier);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetPlayerSteamLevel(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 24) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void) -> i32 = unsafe { std::mem::transmute(f) };
    fnp(obj)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetGameBadgeLevel(
    _env: JNIEnv,
    _cls: JClass,
    n_series: jint,
    b_foil: jboolean,
) -> jint {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 23) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, i32, bool) -> i32 = unsafe { std::mem::transmute(f) };
    fnp(obj, n_series, b_foil != JNI_FALSE)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestStoreAuthURL(
    mut env: JNIEnv,
    _cls: JClass,
    j_redirect: JString,
) -> jlong {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 25) };
    if f.is_null() {
        return 0;
    }
    let s = jstr_to_string(&mut env, &j_redirect);
    let c_s = std::ffi::CString::new(s).unwrap_or_default();
    let p_str = if j_redirect.is_null() {
        std::ptr::null()
    } else {
        c_s.as_ptr()
    };
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, p_str) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetMarketEligibility(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 30) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDurationControl(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 31) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendSteamLevel(
    _env: JNIEnv,
    _cls: JClass,
    sid: jlong,
    level: jint,
) {
    if sid == 0 {
        return;
    }
    let p = state::pushed();
    let mut friends = p.friends.lock().expect("pushed.friends poisoned");
    if level < 0 {
        friends.friend_steam_levels.remove(&(sid as u64));
    } else {
        friends.friend_steam_levels.insert(sid as u64, level);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeIsAppMarkedCorrupt(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
) -> jboolean {
    if app_id <= 0 {
        return JNI_FALSE;
    }
    let p = state::pushed();
    let apps = p.apps.lock().expect("pushed.apps poisoned");
    if apps.apps_marked_corrupt.contains(&(app_id as u32)) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeClearAppCorruptFlag(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    apps.apps_marked_corrupt.remove(&(app_id as u32));
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUserHasLicense(
    _env: JNIEnv,
    _cls: JClass,
    sid: jlong,
    app_id: jint,
) -> jint {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 2;
    }
    let f = unsafe { vtable_fn(obj, 18) };
    if f.is_null() {
        return 2;
    }
    let fnp: extern "C" fn(*mut c_void, u64, u32) -> i32 = unsafe { std::mem::transmute(f) };
    fnp(obj, sid as u64, app_id as u32)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticMarkContentCorrupt(
    _env: JNIEnv,
    _cls: JClass,
    missing_only: jboolean,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 16) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, bool) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, missing_only != JNI_FALSE) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendSteamLevel(
    _env: JNIEnv,
    _cls: JClass,
    sid: jlong,
) -> jint {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 10) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> i32 = unsafe { std::mem::transmute(f) };
    fnp(obj, sid as u64)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetAuthTicketForWebApi(
    mut env: JNIEnv,
    _cls: JClass,
    j_identity: JString,
) -> jlong {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 14) };
    if f.is_null() {
        return 0;
    }
    let s = jstr_to_string(&mut env, &j_identity);
    let c_s = std::ffi::CString::new(s).unwrap_or_default();
    let p_str = if j_identity.is_null() {
        std::ptr::null()
    } else {
        c_s.as_ptr()
    };
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, p_str) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendRelationship(
    _env: JNIEnv,
    _cls: JClass,
    sid: jlong,
) -> jint {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 5) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> i32 = unsafe { std::mem::transmute(f) };
    fnp(obj, sid as u64)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticHasFriend(
    _env: JNIEnv,
    _cls: JClass,
    sid: jlong,
    flags: jint,
) -> jboolean {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 17) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u64, i32) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, sid as u64, flags) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetUserDataFolder(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return std::ptr::null_mut();
    }
    let f = unsafe { vtable_fn(obj, 6) };
    if f.is_null() {
        return std::ptr::null_mut();
    }
    let fnp: extern "C" fn(*mut c_void, *mut c_char, i32) -> bool = unsafe { std::mem::transmute(f) };
    let mut buf = vec![0i8; 512];
    if !fnp(obj, buf.as_mut_ptr(), buf.len() as i32) {
        return std::ptr::null_mut();
    }
    let s = unsafe { CStr::from_ptr(buf.as_ptr()).to_string_lossy().into_owned() };
    new_string_or_null(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetDurationControl(
    _env: JNIEnv,
    _cls: JClass,
    state_in: jint,
) -> jboolean {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 32) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, i32) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, state_in) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppFlag(
    _env: JNIEnv,
    _cls: JClass,
    flag_kind: jint,
    app_id: jint,
    on: jboolean,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = app_id as u32;
    let b = on != JNI_FALSE;
    if flag_kind == 0 {
        if b {
            apps.app_low_violence.insert(key);
        } else {
            apps.app_low_violence.remove(&key);
        }
    } else {
        if b {
            apps.app_vac_banned.insert(key);
        } else {
            apps.app_vac_banned.remove(&key);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticAppsBool(
    _env: JNIEnv,
    _cls: JClass,
    slot: jint,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() || !(0..=3).contains(&slot) {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, slot as usize) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetDlcContext(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 29) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u32) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, app_id as u32) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileForget(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() {
        return JNI_FALSE;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 5) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, c_name.as_ptr()) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFilePersisted(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() {
        return JNI_FALSE;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 14) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, c_name.as_ptr()) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppCloudRemoteDir(
    mut env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    jpath: JString,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let path = jstr_to_string(&mut env, &jpath);
    if jpath.is_null() || path.is_empty() {
        apps.app_cloud_remote_dirs.remove(&(app_id as u32));
    } else {
        apps.app_cloud_remote_dirs.insert(app_id as u32, path);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppCurrentBeta(
    mut env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    jbranch: JString,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let branch = jstr_to_string(&mut env, &jbranch);
    if jbranch.is_null() || branch.is_empty() {
        apps.app_current_beta.remove(&(app_id as u32));
    } else {
        apps.app_current_beta.insert(app_id as u32, branch);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileWrite(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
    jdata: JByteArray,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() || jdata.is_null() {
        return JNI_FALSE;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let data = byte_array_to_vec(&env, &jdata);
    let f = unsafe { vtable_fn(obj, 0) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char, *const c_void, i32) -> bool =
        unsafe { std::mem::transmute(f) };
    if fnp(
        obj,
        c_name.as_ptr(),
        data.as_ptr() as *const c_void,
        data.len() as i32,
    ) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileRead(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
    max_bytes: jint,
) -> jbyteArray {
    if jname.is_null() || max_bytes <= 0 {
        return std::ptr::null_mut();
    }
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() {
        return std::ptr::null_mut();
    }
    let f = unsafe { vtable_fn(obj, 1) };
    if f.is_null() {
        return std::ptr::null_mut();
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let mut buf = vec![0u8; max_bytes as usize];
    let fnp: extern "C" fn(*mut c_void, *const c_char, *mut c_void, i32) -> i32 =
        unsafe { std::mem::transmute(f) };
    let n = fnp(
        obj,
        c_name.as_ptr(),
        buf.as_mut_ptr() as *mut c_void,
        max_bytes,
    );
    if n <= 0 {
        return std::ptr::null_mut();
    }
    byte_array_or_null(&env, &buf[..n as usize])
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamOpen(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jlong {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 9) };
    if f.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, c_name.as_ptr()) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamWriteChunk(
    env: JNIEnv,
    _cls: JClass,
    h_stream: jlong,
    jdata: JByteArray,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jdata.is_null() {
        return JNI_FALSE;
    }
    let data = byte_array_to_vec(&env, &jdata);
    let f = unsafe { vtable_fn(obj, 10) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u64, *const c_void, i32) -> bool =
        unsafe { std::mem::transmute(f) };
    if fnp(
        obj,
        h_stream as u64,
        data.as_ptr() as *const c_void,
        data.len() as i32,
    ) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamClose(
    _env: JNIEnv,
    _cls: JClass,
    h_stream: jlong,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 11) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, h_stream as u64) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudStreamCancel(
    _env: JNIEnv,
    _cls: JClass,
    h_stream: jlong,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 12) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, h_stream as u64) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileWriteAsync(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
    jdata: JByteArray,
) -> jlong {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() || jdata.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let data = byte_array_to_vec(&env, &jdata);
    let f = unsafe { vtable_fn(obj, 2) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char, *const c_void, u32) -> u64 =
        unsafe { std::mem::transmute(f) };
    fnp(
        obj,
        c_name.as_ptr(),
        data.as_ptr() as *const c_void,
        data.len() as u32,
    ) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileReadAsync(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
    n_offset: jint,
    cub_to_read: jint,
) -> jlong {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() || cub_to_read <= 0 {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 3) };
    if f.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let fnp: extern "C" fn(*mut c_void, *const c_char, u32, u32) -> u64 =
        unsafe { std::mem::transmute(f) };
    fnp(obj, c_name.as_ptr(), n_offset as u32, cub_to_read as u32) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileReadAsyncComplete(
    env: JNIEnv,
    _cls: JClass,
    h_call: jlong,
    cub_to_read: jint,
) -> jbyteArray {
    if h_call == 0 || cub_to_read <= 0 {
        return std::ptr::null_mut();
    }
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() {
        return std::ptr::null_mut();
    }
    let f = unsafe { vtable_fn(obj, 4) };
    if f.is_null() {
        return std::ptr::null_mut();
    }
    let mut buf = vec![0u8; cub_to_read as usize];
    let fnp: extern "C" fn(*mut c_void, u64, *mut c_void, u32) -> bool =
        unsafe { std::mem::transmute(f) };
    let ok = fnp(
        obj,
        h_call as u64,
        buf.as_mut_ptr() as *mut c_void,
        cub_to_read as u32,
    );
    if !ok {
        return std::ptr::null_mut();
    }
    byte_array_or_null(&env, &buf)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCloudFileDelete(
    mut env: JNIEnv,
    _cls: JClass,
    jname: JString,
) -> jboolean {
    let obj = iface::isteam_remote_storage::instance();
    if obj.is_null() || jname.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 6) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let name = jstr_to_string(&mut env, &jname);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, c_name.as_ptr()) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetCurrentBetaName(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return std::ptr::null_mut();
    }
    let f = unsafe { vtable_fn(obj, 15) };
    if f.is_null() {
        return std::ptr::null_mut();
    }
    let mut buf = vec![0i8; 128];
    let fnp: extern "C" fn(*mut c_void, *mut c_char, i32) -> bool =
        unsafe { std::mem::transmute(f) };
    if !fnp(obj, buf.as_mut_ptr(), buf.len() as i32) {
        return std::ptr::null_mut();
    }
    let s = unsafe { CStr::from_ptr(buf.as_ptr()).to_string_lossy().into_owned() };
    new_string_or_null(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppDownloadProgress(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    bytes_downloaded: jlong,
    bytes_total: jlong,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = app_id as u32;
    if bytes_total <= 0 {
        apps.app_dl_progress.remove(&key);
        return;
    }
    apps.app_dl_progress.insert(
        key,
        crate::state::DlProgress {
            bytes_downloaded: bytes_downloaded.max(0) as u64,
            bytes_total: bytes_total as u64,
        },
    );
}

static mut S_DIAG_DL_DOWNLOADED: u64 = 0;
static mut S_DIAG_DL_TOTAL: u64 = 0;

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDlcDownloadProgress(
    _env: JNIEnv,
    _cls: JClass,
    app_id: jint,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 22) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u32, *mut u64, *mut u64) -> bool =
        unsafe { std::mem::transmute(f) };
    unsafe {
        S_DIAG_DL_DOWNLOADED = 0;
        S_DIAG_DL_TOTAL = 0;
        if fnp(
            obj,
            app_id as u32,
            &raw mut S_DIAG_DL_DOWNLOADED,
            &raw mut S_DIAG_DL_TOTAL,
        ) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDlcDownloadProgressBytes(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    unsafe { S_DIAG_DL_DOWNLOADED as jlong }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetDlcDownloadProgressTotal(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    unsafe { S_DIAG_DL_TOTAL as jlong }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppInstalledDepots(
    env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    depot_ids: JIntArray,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = app_id as u32;
    if depot_ids.is_null() {
        apps.app_installed_depots.remove(&key);
        return;
    }
    let ids = int_array_to_vec(&env, &depot_ids);
    if ids.is_empty() {
        apps.app_installed_depots.remove(&key);
        return;
    }
    let depots: Vec<u32> = ids.into_iter().filter(|v| *v > 0).map(|v| v as u32).collect();
    apps.app_installed_depots.insert(key, depots);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppDlcs(
    mut env: JNIEnv,
    _cls: JClass,
    parent_app_id: jint,
    dlc_app_ids: JIntArray,
    dlc_names: JObjectArray,
    available: jni::sys::jbooleanArray,
) {
    if parent_app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = parent_app_id as u32;
    if dlc_app_ids.is_null() {
        apps.app_dlcs.remove(&key);
        return;
    }
    let ids = int_array_to_vec(&env, &dlc_app_ids);
    if ids.is_empty() {
        apps.app_dlcs.remove(&key);
        return;
    }
    let names = if !dlc_names.is_null() {
        jobject_array_to_strings(&mut env, &dlc_names)
    } else {
        Vec::new()
    };
    let avail = boolean_array_to_vec(&env, available);
    let mut entries = Vec::with_capacity(ids.len());
    for (i, id) in ids.iter().enumerate() {
        if *id <= 0 {
            continue;
        }
        entries.push(DlcEntry {
            app_id: *id as u32,
            name: names.get(i).cloned().unwrap_or_default(),
            available: avail.get(i).copied().unwrap_or(true),
        });
    }
    apps.app_dlcs.insert(key, entries);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppWorkshopItems(
    mut env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    published_file_ids: JLongArray,
    install_dirs: JObjectArray,
    sizes_bytes: JLongArray,
    timestamps: JLongArray,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = app_id as u32;
    if published_file_ids.is_null() {
        apps.subscribed_workshop_items.remove(&key);
        return;
    }
    let ids = long_array_to_vec(&env, &published_file_ids);
    if ids.is_empty() {
        apps.subscribed_workshop_items.remove(&key);
        return;
    }
    let dirs = if !install_dirs.is_null() {
        jobject_array_to_strings(&mut env, &install_dirs)
    } else {
        Vec::new()
    };
    let sizes = if !sizes_bytes.is_null() {
        long_array_to_vec(&env, &sizes_bytes)
    } else {
        Vec::new()
    };
    let times = if !timestamps.is_null() {
        long_array_to_vec(&env, &timestamps)
    } else {
        Vec::new()
    };
    let mut items: HashMap<u64, WorkshopItemInfo> = HashMap::new();
    for (i, id) in ids.iter().enumerate() {
        if *id <= 0 {
            continue;
        }
        items.insert(
            *id as u64,
            WorkshopItemInfo {
                install_dir: dirs.get(i).cloned().unwrap_or_default(),
                size_bytes: sizes.get(i).copied().unwrap_or(0) as u64,
                timestamp: times.get(i).copied().unwrap_or(0) as u32,
                installed: true,
            },
        );
    }
    if items.is_empty() {
        apps.subscribed_workshop_items.remove(&key);
    } else {
        apps.subscribed_workshop_items.insert(key, items);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetInventoryItemDefs(
    mut env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    def_ids: JIntArray,
    prop_counts_per_def: JIntArray,
    prop_keys: JObjectArray,
    prop_vals: JObjectArray,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = app_id as u32;
    if def_ids.is_null() {
        apps.inventory_item_defs.remove(&key);
        return;
    }
    let ids = int_array_to_vec(&env, &def_ids);
    if ids.is_empty() {
        apps.inventory_item_defs.remove(&key);
        return;
    }
    if prop_counts_per_def.is_null() {
        return;
    }
    let counts = int_array_to_vec(&env, &prop_counts_per_def);
    if counts.len() != ids.len() {
        return;
    }
    let keys = if !prop_keys.is_null() {
        jobject_array_to_strings(&mut env, &prop_keys)
    } else {
        Vec::new()
    };
    let vals = if !prop_vals.is_null() {
        jobject_array_to_strings(&mut env, &prop_vals)
    } else {
        Vec::new()
    };
    let mut table: HashMap<i32, HashMap<String, String>> = HashMap::new();
    let mut cursor: usize = 0;
    for (i, id) in ids.iter().enumerate() {
        let count = counts[i] as usize;
        if *id <= 0 {
            cursor += count;
            continue;
        }
        let mut props: HashMap<String, String> = HashMap::new();
        let end = cursor + count;
        for j in cursor..end {
            let k = keys.get(j).cloned().unwrap_or_default();
            if k.is_empty() {
                continue;
            }
            let v = vals.get(j).cloned().unwrap_or_default();
            props.insert(k, v);
        }
        cursor = end;
        table.insert(*id, props);
    }
    if table.is_empty() {
        apps.inventory_item_defs.remove(&key);
    } else {
        // state.rs uses HashMap<u32, HashMap<i32, HashMap<String, String>>>
        apps.inventory_item_defs.insert(key, table);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendPersonaState(
    _env: JNIEnv,
    _cls: JClass,
    steam_id: jlong,
    state_in: jint,
) {
    if steam_id == 0 {
        return;
    }
    let p = state::pushed();
    let sid = steam_id as u64;
    let flags = {
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        if state_in < 0 {
            let mut f = 0;
            if let Some(prev) = friends.friend_persona_states.get(&sid) {
                if *prev != 0 {
                    f = cb::K_PERSONA_CHANGE_STATUS | cb::K_PERSONA_CHANGE_GONE_OFFLINE;
                }
            }
            friends.friend_persona_states.remove(&sid);
            f
        } else {
            let prev = friends.friend_persona_states.get(&sid).copied();
            let prev_known = prev.is_some();
            let prev = prev.unwrap_or(0);
            friends
                .friend_persona_states
                .insert(sid, state_in as u32);
            if !prev_known || prev != state_in as u32 {
                let mut f = cb::K_PERSONA_CHANGE_STATUS;
                if prev == 0 && state_in != 0 {
                    f |= cb::K_PERSONA_CHANGE_COME_ONLINE;
                }
                if prev != 0 && state_in == 0 {
                    f |= cb::K_PERSONA_CHANGE_GONE_OFFLINE;
                }
                f
            } else {
                0
            }
        }
    };
    if flags != 0 {
        emit_persona_state_change(sid, flags);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendGamePlayed(
    _env: JNIEnv,
    _cls: JClass,
    steam_id: jlong,
    app_id: jint,
) {
    if steam_id == 0 {
        return;
    }
    let p = state::pushed();
    let mut friends = p.friends.lock().expect("pushed.friends poisoned");
    let sid = steam_id as u64;
    if app_id <= 0 {
        friends.friend_game_played_app.remove(&sid);
    } else {
        friends.friend_game_played_app.insert(sid, app_id as u32);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendPersonaName(
    mut env: JNIEnv,
    _cls: JClass,
    steam_id: jlong,
    jname: JString,
) {
    if steam_id == 0 {
        return;
    }
    let p = state::pushed();
    let name = jstr_to_string(&mut env, &jname);
    let sid = steam_id as u64;
    let changed = {
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        if name.is_empty() {
            friends.friend_persona_names.remove(&sid).is_some()
        } else {
            match friends.friend_persona_names.get(&sid) {
                Some(prev) if *prev == name => false,
                _ => {
                    friends.friend_persona_names.insert(sid, name);
                    true
                }
            }
        }
    };
    if changed {
        emit_persona_state_change(sid, cb::K_PERSONA_CHANGE_NAME);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetLaunchCommandLine(
    mut env: JNIEnv,
    _cls: JClass,
    jcli: JString,
) {
    let cli = jstr_to_string(&mut env, &jcli);
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    apps.launch_command_line = cli;
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppFamilyShared(
    _env: JNIEnv,
    _cls: JClass,
    family_shared: jboolean,
) {
    state::pushed()
        .app_is_family_shared
        .store(family_shared != JNI_FALSE, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetEncryptedAppTicket(
    env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    body: JByteArray,
    eresult: jint,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let key = app_id as u32;
    {
        let mut apps = p.apps.lock().expect("pushed.apps poisoned");
        if body.is_null() {
            apps.encrypted_app_tickets.remove(&key);
        } else {
            let bytes = byte_array_to_vec(&env, &body);
            if bytes.is_empty() {
                apps.encrypted_app_tickets.remove(&key);
            } else {
                apps.encrypted_app_tickets.insert(key, bytes);
            }
        }
    }
    p.encrypted_app_ticket_eresult
        .store(eresult, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeReportLogonFailure(
    _env: JNIEnv,
    _cls: JClass,
    eresult: jint,
    still_retrying: jboolean,
) {
    let payload = cb::SteamServerConnectFailure {
        m_eResult: eresult,
        m_bStillRetrying: still_retrying != JNI_FALSE,
        _pad: [0; 3],
    };
    push_user_callback(cb::K_STEAM_SERVER_CONNECT_FAILURE, &payload);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetServerRealTime(
    _env: JNIEnv,
    _cls: JClass,
    server_realtime_unix: jint,
) {
    let p = state::pushed();
    let ms = now_ms();
    p.server_realtime
        .store(server_realtime_unix as u32, Ordering::SeqCst);
    p.server_realtime_anchor_local_ms
        .store(ms, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudEnabledForAccount(
    _env: JNIEnv,
    _cls: JClass,
    enabled: jboolean,
) {
    state::pushed()
        .cloud_enabled_account
        .store(enabled != JNI_FALSE, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudEnabledForApp(
    _env: JNIEnv,
    _cls: JClass,
    enabled: jboolean,
) {
    state::pushed()
        .cloud_enabled_app
        .store(enabled != JNI_FALSE, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudQuota(
    _env: JNIEnv,
    _cls: JClass,
    total_bytes: jlong,
    avail_bytes: jlong,
) {
    let p = state::pushed();
    p.cloud_quota_total
        .store(total_bytes as u64, Ordering::SeqCst);
    p.cloud_quota_available
        .store(avail_bytes as u64, Ordering::SeqCst);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetCloudFiles(
    mut env: JNIEnv,
    _cls: JClass,
    names: JObjectArray,
    sizes: JIntArray,
    timestamps: JLongArray,
) {
    let p = state::pushed();
    let (pushed_count, app) = {
        let mut cloud = p.cloud.lock().expect("pushed.cloud poisoned");
        cloud.cloud_files.clear();
        if names.is_null() || sizes.is_null() || timestamps.is_null() {
            (0usize, p.app_id.load(Ordering::SeqCst))
        } else {
            let name_strs = jobject_array_to_strings(&mut env, &names);
            let szs = int_array_to_vec(&env, &sizes);
            let ts = long_array_to_vec(&env, &timestamps);
            if name_strs.is_empty()
                || name_strs.len() != szs.len()
                || name_strs.len() != ts.len()
            {
                (0usize, p.app_id.load(Ordering::SeqCst))
            } else {
                for (i, name) in name_strs.iter().enumerate() {
                    if name.is_empty() {
                        continue;
                    }
                    cloud.cloud_files.push(CloudFileEntry {
                        name: name.clone(),
                        size: szs[i],
                        timestamp: ts[i],
                    });
                }
                (cloud.cloud_files.len(), p.app_id.load(Ordering::SeqCst))
            }
        }
    };
    if app != 0 {
        let payload = cb::RemoteStorageAppSyncedClient {
            m_nAppID: app,
            m_eResult: if pushed_count > 0 { 1 } else { 2 },
            m_unNumDownloads: pushed_count as i32,
        };
        push_user_callback(cb::K_REMOTE_STORAGE_APP_SYNCED_CLIENT, &payload);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAchievementSchema(
    mut env: JNIEnv,
    _cls: JClass,
    api_names: JObjectArray,
    display_names: JObjectArray,
    descriptions: JObjectArray,
    icons: JObjectArray,
    hidden: jni::sys::jbooleanArray,
) {
    let p = state::pushed();
    let (pushed_count, game_id, steam_id) = {
        let mut stats = p.stats.lock().expect("pushed.stats poisoned");
        stats.achievements.clear();
        stats.achievement_index.clear();
        stats.dirty_stats_int.clear();
        stats.dirty_stats_float.clear();
        if api_names.is_null() {
            p.stats_ready.store(true, Ordering::SeqCst);
            (
                0usize,
                p.app_id.load(Ordering::SeqCst) as u64,
                p.steam_id.load(Ordering::SeqCst),
            )
        } else {
            let names = jobject_array_to_strings(&mut env, &api_names);
            if names.is_empty() {
                p.stats_ready.store(true, Ordering::SeqCst);
                (
                    0usize,
                    p.app_id.load(Ordering::SeqCst) as u64,
                    p.steam_id.load(Ordering::SeqCst),
                )
            } else {
                let dns = if !display_names.is_null() {
                    jobject_array_to_strings(&mut env, &display_names)
                } else {
                    Vec::new()
                };
                let dss = if !descriptions.is_null() {
                    jobject_array_to_strings(&mut env, &descriptions)
                } else {
                    Vec::new()
                };
                let ics = if !icons.is_null() {
                    jobject_array_to_strings(&mut env, &icons)
                } else {
                    Vec::new()
                };
                let hide = boolean_array_to_vec(&env, hidden);
                for (i, api) in names.iter().enumerate() {
                    if api.is_empty() {
                        continue;
                    }
                    let mut e = AchievementEntry::new();
                    e.api_name = api.clone();
                    if let Some(dn) = dns.get(i) {
                        if !dn.is_empty() {
                            e.display_names.insert("english".to_string(), dn.clone());
                        }
                    }
                    if let Some(ds) = dss.get(i) {
                        if !ds.is_empty() {
                            e.descriptions.insert("english".to_string(), ds.clone());
                        }
                    }
                    if let Some(ic) = ics.get(i) {
                        e.icon = ic.clone();
                    }
                    e.hidden = hide.get(i).copied().unwrap_or(false);
                    let idx = stats.achievements.len();
                    e.icon_handle = idx as i32 + 1;
                    stats.achievement_index.insert(api.clone(), idx);
                    stats.achievements.push(e);
                }
                p.stats_ready.store(true, Ordering::SeqCst);
                (
                    stats.achievements.len(),
                    p.app_id.load(Ordering::SeqCst) as u64,
                    p.steam_id.load(Ordering::SeqCst),
                )
            }
        }
    };
    let payload = cb::UserStatsReceived {
        m_nGameID: game_id,
        m_eResult: if pushed_count > 0 { 1 } else { 2 },
        _pad: 0,
        m_steamIDUser: steam_id,
    };
    push_user_callback(cb::K_USER_STATS_RECEIVED, &payload);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetStatIds(
    mut env: JNIEnv,
    _cls: JClass,
    j_names: JObjectArray,
    j_ids: JIntArray,
) {
    if j_names.is_null() || j_ids.is_null() {
        return;
    }
    let names = jobject_array_to_strings(&mut env, &j_names);
    let ids = int_array_to_vec(&env, &j_ids);
    if names.is_empty() || names.len() != ids.len() {
        return;
    }
    let p = state::pushed();
    let mut stats = p.stats.lock().expect("pushed.stats poisoned");
    stats.stat_name_to_id.clear();
    for (i, name) in names.iter().enumerate() {
        if name.is_empty() || ids[i] < 0 {
            continue;
        }
        stats.stat_name_to_id.insert(name.clone(), ids[i] as u32);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAchievementBlockBits(
    mut env: JNIEnv,
    _cls: JClass,
    api_names: JObjectArray,
    block_ids: JIntArray,
    bit_indices: JIntArray,
) {
    if api_names.is_null() || block_ids.is_null() || bit_indices.is_null() {
        return;
    }
    let names = jobject_array_to_strings(&mut env, &api_names);
    let blocks = int_array_to_vec(&env, &block_ids);
    let bits = int_array_to_vec(&env, &bit_indices);
    if names.is_empty() || names.len() != blocks.len() || names.len() != bits.len() {
        return;
    }
    let p = state::pushed();
    let mut stats = p.stats.lock().expect("pushed.stats poisoned");
    for (i, name) in names.iter().enumerate() {
        if name.is_empty() {
            continue;
        }
        if let Some(&idx) = stats.achievement_index.get(name) {
            if idx < stats.achievements.len() {
                let ach = &mut stats.achievements[idx];
                ach.block_id = blocks[i];
                ach.bit_index = bits[i];
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeAddAchievementLocale(
    mut env: JNIEnv,
    _cls: JClass,
    j_api: JString,
    j_locale: JString,
    j_display: JString,
    j_desc: JString,
) {
    if j_api.is_null() || j_locale.is_null() {
        return;
    }
    let api = jstr_to_string(&mut env, &j_api);
    let locale = jstr_to_string(&mut env, &j_locale);
    let dn = jstr_to_string(&mut env, &j_display);
    let ds = jstr_to_string(&mut env, &j_desc);
    if api.is_empty() || locale.is_empty() || (dn.is_empty() && ds.is_empty()) {
        return;
    }
    let p = state::pushed();
    let mut stats = p.stats.lock().expect("pushed.stats poisoned");
    if let Some(&idx) = stats.achievement_index.get(&api) {
        if idx < stats.achievements.len() {
            let ach = &mut stats.achievements[idx];
            if !dn.is_empty() {
                ach.display_names.insert(locale.clone(), dn);
            }
            if !ds.is_empty() {
                ach.descriptions.insert(locale, ds);
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAchievementProgress(
    mut env: JNIEnv,
    _cls: JClass,
    j_api: JString,
    achieved: jboolean,
    unlock_time_unix: jint,
) {
    if j_api.is_null() {
        return;
    }
    let name = jstr_to_string(&mut env, &j_api);
    if name.is_empty() {
        return;
    }
    let p = state::pushed();
    let mut stats = p.stats.lock().expect("pushed.stats poisoned");
    if let Some(&idx) = stats.achievement_index.get(&name) {
        if idx < stats.achievements.len() {
            let ach = &mut stats.achievements[idx];
            ach.achieved = achieved != JNI_FALSE;
            ach.unlock_time = unlock_time_unix as u32;
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetStatInt(
    mut env: JNIEnv,
    _cls: JClass,
    j_name: JString,
    value: jint,
) {
    if j_name.is_null() {
        return;
    }
    let name = jstr_to_string(&mut env, &j_name);
    if name.is_empty() {
        return;
    }
    let p = state::pushed();
    let mut stats = p.stats.lock().expect("pushed.stats poisoned");
    stats.stats_int.insert(name, value);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetStatFloat(
    mut env: JNIEnv,
    _cls: JClass,
    j_name: JString,
    value: jfloat,
) {
    if j_name.is_null() {
        return;
    }
    let name = jstr_to_string(&mut env, &j_name);
    if name.is_empty() {
        return;
    }
    let p = state::pushed();
    let mut stats = p.stats.lock().expect("pushed.stats poisoned");
    stats.stats_float.insert(name, value);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticAchievementCount(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::pushed()
        .stats
        .lock()
        .expect("pushed.stats poisoned")
        .achievements
        .len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticCallbackDepth(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    state::state()
        .callback_mu
        .lock()
        .expect("callback queue poisoned")
        .queue
        .len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticTcpAccepted(
    _env: JNIEnv,
    _cls: JClass,
) -> jint {
    crate::tcp_services::accepted_connection_count()
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUtilsGetAPICallResult(
    _env: JNIEnv,
    _cls: JClass,
    i_callback: jint,
    eresult_in: jint,
) -> jint {
    let h = state::alloc_api_call_handle();
    let body = eresult_in.to_le_bytes();
    state::push_call_result_bytes(h, i_callback, &body, false);
    let obj = iface::isteam_utils::instance();
    if obj.is_null() {
        return -1;
    }
    let f = unsafe { vtable_fn(obj, 13) };
    if f.is_null() {
        return -1;
    }
    let fnp: extern "C" fn(*mut c_void, u64, *mut c_void, i32, i32, *mut bool) -> bool =
        unsafe { std::mem::transmute(f) };
    let mut out: i32 = -1;
    let mut failed: bool = false;
    if fnp(
        obj,
        h,
        &mut out as *mut i32 as *mut c_void,
        std::mem::size_of::<i32>() as i32,
        i_callback,
        &mut failed,
    ) {
        out
    } else {
        -1
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestEncryptedAppTicket(
    env: JNIEnv,
    _cls: JClass,
    out_body: JByteArray,
) -> jlong {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let req_f = unsafe { vtable_fn(obj, 21) };
    let get_f = unsafe { vtable_fn(obj, 22) };
    if req_f.is_null() || get_f.is_null() {
        return 0;
    }
    let req: extern "C" fn(*mut c_void, *mut c_void, i32) -> u64 =
        unsafe { std::mem::transmute(req_f) };
    let h = req(obj, std::ptr::null_mut(), 0);
    let get: extern "C" fn(*mut c_void, *mut c_void, i32, *mut u32) -> bool =
        unsafe { std::mem::transmute(get_f) };
    let mut scratch = [0u8; 128];
    let mut actual: u32 = 0;
    let ok = get(
        obj,
        scratch.as_mut_ptr() as *mut c_void,
        scratch.len() as i32,
        &mut actual,
    );
    if ok && !out_body.is_null() {
        if let Ok(out_len) = env.get_array_length(&out_body) {
            if (out_len as u32) >= actual {
                let _ = env.set_byte_array_region(&out_body, 0, unsafe {
                    std::slice::from_raw_parts(scratch.as_ptr() as *const i8, actual as usize)
                });
            }
        }
    }
    h as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetAuthTicket(
    env: JNIEnv,
    _cls: JClass,
    jbuf: JByteArray,
) -> jint {
    let obj = iface::isteam_user::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 13) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, *mut c_void, i32, *mut u32, *const c_void) -> u64 =
        unsafe { std::mem::transmute(f) };
    let mut scratch = [0u8; 64];
    let mut actual: u32 = 0;
    let h = fnp(
        obj,
        scratch.as_mut_ptr() as *mut c_void,
        scratch.len() as i32,
        &mut actual,
        std::ptr::null(),
    );
    if !jbuf.is_null() {
        if let Ok(out_len) = env.get_array_length(&jbuf) {
            if (out_len as u32) >= actual {
                let _ = env.set_byte_array_region(&jbuf, 0, unsafe {
                    std::slice::from_raw_parts(scratch.as_ptr() as *const i8, actual as usize)
                });
            }
        }
    }
    h as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticPushAndDrainCallResult(
    _env: JNIEnv,
    _cls: JClass,
    callback_id: jint,
    eresult: jint,
) -> jlong {
    // Approximate the C++ diagnostic: push a call result then drain via
    // SteamAPI_RunCallbacks. The C++ diagnostic ran in-process callback
    // dispatch with an inline vtable; the Rust runtime's behaviour is
    // observed by the API call queue depth, so we expose
    //   high32 = runs (1 if dispatched), low32 = eresult dispatched.
    let h = state::alloc_api_call_handle();
    let body = eresult.to_le_bytes();
    state::push_call_result_bytes(h, callback_id, &body, false);
    unsafe { crate::api_entry::SteamAPI_RunCallbacks(); }
    let runs: i64 = 1;
    (runs << 32) | (eresult as u32 as i64)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRegisterAndDrain(
    _env: JNIEnv,
    _cls: JClass,
    i_callback: jint,
) -> jint {
    // Same simplification as PushAndDrainCallResult: report 1 run after
    // SteamAPI_RunCallbacks completes synchronously.
    let _ = i_callback;
    unsafe { crate::api_entry::SteamAPI_RunCallbacks(); }
    1
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticShutdownPipe(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    let pipe = state::state().pipe.load(Ordering::SeqCst);
    if pipe == 0 {
        return JNI_FALSE;
    }
    let payload = cb::SteamShutdown { _placeholder: 0 };
    let user = state::state().user.load(Ordering::SeqCst);
    state::push_callback_bytes(user, cb::K_STEAM_SHUTDOWN, &[]);
    let _ = payload;
    let ok = state::release_pipe(pipe);
    let _ = state::alloc_pipe();
    if ok {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticStoreStats(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    let obj = iface::isteam_user_stats::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 10) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetAchievement(
    mut env: JNIEnv,
    _cls: JClass,
    j_name: JString,
) -> jboolean {
    if j_name.is_null() {
        return JNI_FALSE;
    }
    let obj = iface::isteam_user_stats::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let name = jstr_to_string(&mut env, &j_name);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 7) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, c_name.as_ptr()) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticIndicateAchievementProgress(
    mut env: JNIEnv,
    _cls: JClass,
    j_name: JString,
    cur: jint,
    max: jint,
) -> jboolean {
    if j_name.is_null() {
        return JNI_FALSE;
    }
    let obj = iface::isteam_user_stats::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let name = jstr_to_string(&mut env, &j_name);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 13) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char, u32, u32) -> bool =
        unsafe { std::mem::transmute(f) };
    if fnp(obj, c_name.as_ptr(), cur as u32, max as u32) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendRichPresence(
    mut env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
    j_key: JString,
    j_value: JString,
) {
    let steam_id = j_steam_id as u64;
    if steam_id == 0 || j_key.is_null() {
        return;
    }
    let key = jstr_to_string(&mut env, &j_key);
    let value = jstr_to_string(&mut env, &j_value);
    if key.is_empty() {
        return;
    }
    let p = state::pushed();
    {
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        let rp = friends.rich_presence.entry(steam_id).or_default();
        let pos = rp.iter().position(|(k, _)| *k == key);
        if value.is_empty() {
            if let Some(idx) = pos {
                rp.remove(idx);
            }
        } else {
            match pos {
                Some(idx) => rp[idx].1 = value,
                None => rp.push((key, value)),
            }
        }
    }
    let payload = cb::FriendRichPresenceUpdate {
        m_steamIDFriend: steam_id,
        m_nAppID: p.app_id.load(Ordering::SeqCst),
    };
    push_user_callback(cb::K_FRIEND_RICH_PRESENCE_UPDATE, &payload);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetPersonaName(
    mut env: JNIEnv,
    _cls: JClass,
    j_name: JString,
) -> jlong {
    if j_name.is_null() {
        return 0;
    }
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return 0;
    }
    let name = jstr_to_string(&mut env, &j_name);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 1) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj, c_name.as_ptr()) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestFriendRichPresence(
    _env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
) {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return;
    }
    let f = unsafe { vtable_fn(obj, 48) };
    if f.is_null() {
        return;
    }
    let fnp: extern "C" fn(*mut c_void, u64) = unsafe { std::mem::transmute(f) };
    fnp(obj, j_steam_id as u64);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestUserInformation(
    _env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
    j_name_only: jboolean,
) -> jboolean {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 37) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u64, bool) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, j_steam_id as u64, j_name_only != JNI_FALSE) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectLogonState(
    _env: JNIEnv,
    _cls: JClass,
    j_logged_on: jboolean,
) {
    unsafe {
        wn_cm_bridge_inject_test_logon_state(j_logged_on != JNI_FALSE);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectLicenseList(
    env: JNIEnv,
    _cls: JClass,
    j_package_ids: JIntArray,
    j_owner_ids: JIntArray,
) {
    if j_package_ids.is_null() {
        unsafe {
            wn_cm_bridge_inject_test_license_list(std::ptr::null(), 0);
        }
        return;
    }
    let pkgs = int_array_to_vec(&env, &j_package_ids);
    if pkgs.is_empty() {
        unsafe {
            wn_cm_bridge_inject_test_license_list(std::ptr::null(), 0);
        }
        return;
    }
    let owns = if !j_owner_ids.is_null() {
        int_array_to_vec(&env, &j_owner_ids)
    } else {
        Vec::new()
    };
    let now = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.as_secs() as u32)
        .unwrap_or(0);
    let entries: Vec<WnCmLicenseEntry> = pkgs
        .iter()
        .enumerate()
        .map(|(i, pkg)| WnCmLicenseEntry {
            package_id: *pkg as u32,
            owner_id: owns.get(i).copied().unwrap_or(0) as u32,
            time_created: now,
            license_type: 0,
            flags: 0,
            change_number: 0,
            minute_limit: 0,
            minutes_used: 0,
        })
        .collect();
    unsafe {
        wn_cm_bridge_inject_test_license_list(entries.as_ptr(), entries.len());
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetLicenseOwner(
    _env: JNIEnv,
    _cls: JClass,
    j_package_id: jint,
) -> jint {
    let p = state::pushed();
    let lic = p.licenses.lock().expect("pushed.licenses poisoned");
    lic.licenses
        .get(&(j_package_id as u32))
        .map(|e| e.owner_id as jint)
        .unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetEarliestPurchaseUnixTime(
    _env: JNIEnv,
    _cls: JClass,
    j_app_id: jint,
) -> jint {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 8) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, u32) -> u32 = unsafe { std::mem::transmute(f) };
    fnp(obj, j_app_id as u32) as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsSubscribedFromFreeWeekend(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 9) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsSubscribedFromFamilySharing(
    _env: JNIEnv,
    _cls: JClass,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 27) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticUpdateAvgRateStat(
    mut env: JNIEnv,
    _cls: JClass,
    j_name: JString,
    j_count_this_session: jfloat,
    j_session_length: jdouble,
) -> jfloat {
    if j_name.is_null() {
        return 0.0;
    }
    let obj = iface::isteam_user_stats::instance();
    if obj.is_null() {
        return 0.0;
    }
    let name = jstr_to_string(&mut env, &j_name);
    let c_name = std::ffi::CString::new(name).unwrap_or_default();
    let upd_f = unsafe { vtable_fn(obj, 5) };
    if upd_f.is_null() {
        return 0.0;
    }
    let upd: extern "C" fn(*mut c_void, *const c_char, f32, f64) -> bool =
        unsafe { std::mem::transmute(upd_f) };
    if !upd(obj, c_name.as_ptr(), j_count_this_session, j_session_length) {
        return 0.0;
    }
    let get_f = unsafe { vtable_fn(obj, 2) };
    if get_f.is_null() {
        return 0.0;
    }
    let get: extern "C" fn(*mut c_void, *const c_char, *mut f32) -> bool =
        unsafe { std::mem::transmute(get_f) };
    let mut out: f32 = 0.0;
    get(obj, c_name.as_ptr(), &mut out);
    out
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetAppOwner(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 20) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void) -> u64 = unsafe { std::mem::transmute(f) };
    fnp(obj) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectTrialLicense(
    _env: JNIEnv,
    _cls: JClass,
    j_package_id: jint,
    j_minute_limit: jint,
    j_minutes_used: jint,
) {
    if j_package_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut lic = p.licenses.lock().expect("pushed.licenses poisoned");
    let entry = lic
        .licenses
        .entry(j_package_id as u32)
        .or_insert(LicenseEntry::default());
    entry.package_id = j_package_id as u32;
    entry.minute_limit = j_minute_limit;
    entry.minutes_used = j_minutes_used;
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsDlcInstalled(
    _env: JNIEnv,
    _cls: JClass,
    j_app_id: jint,
) -> jboolean {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return JNI_FALSE;
    }
    let f = unsafe { vtable_fn(obj, 7) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, u32) -> bool = unsafe { std::mem::transmute(f) };
    if fnp(obj, j_app_id as u32) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticBIsTimedTrial(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    let obj = iface::isteam_apps::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 28) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, *mut u32, *mut u32) -> bool =
        unsafe { std::mem::transmute(f) };
    let mut allowed: u32 = 0;
    let mut played: u32 = 0;
    if !fnp(obj, &mut allowed, &mut played) {
        return 0;
    }
    (1i64 << 63) | ((allowed as i64) << 32) | (played as i64)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetAppSourcePackages(
    env: JNIEnv,
    _cls: JClass,
    app_id: jint,
    package_ids: JIntArray,
) {
    if app_id <= 0 {
        return;
    }
    let p = state::pushed();
    let mut apps = p.apps.lock().expect("pushed.apps poisoned");
    let key = app_id as u32;
    if package_ids.is_null() {
        apps.app_source_packages.remove(&key);
        return;
    }
    let arr = int_array_to_vec(&env, &package_ids);
    if arr.is_empty() {
        apps.app_source_packages.remove(&key);
        return;
    }
    let pkgs: Vec<u32> = arr.into_iter().filter(|v| *v > 0).map(|v| v as u32).collect();
    if pkgs.is_empty() {
        apps.app_source_packages.remove(&key);
    } else {
        apps.app_source_packages.insert(key, pkgs);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectFriendsList(
    env: JNIEnv,
    _cls: JClass,
    j_sids: JLongArray,
) {
    if j_sids.is_null() {
        unsafe {
            wn_cm_bridge_inject_test_friends_list(std::ptr::null(), 0);
        }
        return;
    }
    let arr = long_array_to_vec(&env, &j_sids);
    if arr.is_empty() {
        unsafe {
            wn_cm_bridge_inject_test_friends_list(std::ptr::null(), 0);
        }
        return;
    }
    let u64s: Vec<u64> = arr.into_iter().map(|v| v as u64).collect();
    unsafe {
        wn_cm_bridge_inject_test_friends_list(u64s.as_ptr(), u64s.len());
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectPersonaEvent(
    mut env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
    j_persona_state: jint,
    j_game_app_id: jint,
    j_name: JString,
    j_avatar_hash: JByteArray,
    j_rp_keys: JObjectArray,
    j_rp_values: JObjectArray,
) {
    let name_storage = if !j_name.is_null() {
        jstr_to_string(&mut env, &j_name)
    } else {
        String::new()
    };
    let name_c = std::ffi::CString::new(name_storage.clone()).ok();
    let name_ptr = if name_storage.is_empty() {
        std::ptr::null()
    } else {
        name_c.as_ref().map(|s| s.as_ptr()).unwrap_or(std::ptr::null())
    };

    let hash_storage = if !j_avatar_hash.is_null() {
        byte_array_to_vec(&env, &j_avatar_hash)
    } else {
        Vec::new()
    };

    let mut key_storage: Vec<std::ffi::CString> = Vec::new();
    let mut val_storage: Vec<std::ffi::CString> = Vec::new();
    let mut rp_kv: Vec<WnCmRichPresenceKV> = Vec::new();
    if !j_rp_keys.is_null() && !j_rp_values.is_null() {
        let keys = jobject_array_to_strings(&mut env, &j_rp_keys);
        let vals = jobject_array_to_strings(&mut env, &j_rp_values);
        let count = keys.len().min(vals.len());
        key_storage.reserve(count);
        val_storage.reserve(count);
        rp_kv.reserve(count);
        for i in 0..count {
            key_storage.push(std::ffi::CString::new(keys[i].clone()).unwrap_or_default());
            val_storage.push(std::ffi::CString::new(vals[i].clone()).unwrap_or_default());
        }
        for i in 0..count {
            rp_kv.push(WnCmRichPresenceKV {
                key: key_storage[i].as_ptr(),
                value: val_storage[i].as_ptr(),
            });
        }
    }

    let ev = WnCmPersonaEvent {
        sid: j_steam_id as u64,
        persona_state: if j_persona_state < 0 {
            U32_INVALID
        } else {
            j_persona_state as u32
        },
        game_played_app: if j_game_app_id <= 0 {
            0
        } else {
            j_game_app_id as u32
        },
        name: name_ptr,
        avatar_hash: if hash_storage.is_empty() {
            std::ptr::null()
        } else {
            hash_storage.as_ptr()
        },
        avatar_hash_len: hash_storage.len(),
        rp_pairs: if rp_kv.is_empty() {
            std::ptr::null()
        } else {
            rp_kv.as_ptr()
        },
        rp_count: rp_kv.len(),
    };

    unsafe {
        wn_cm_bridge_dispatch_persona(&ev);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticInjectOwnershipTicket(
    env: JNIEnv,
    _cls: JClass,
    j_app_id: jint,
    j_bytes: JByteArray,
) -> jboolean {
    if j_app_id <= 0 || j_bytes.is_null() {
        return JNI_FALSE;
    }
    let bytes = byte_array_to_vec(&env, &j_bytes);
    if bytes.is_empty() {
        return JNI_FALSE;
    }
    let ok = unsafe {
        wn_cm_bridge_inject_test_ownership_ticket(j_app_id as u32, bytes.as_ptr(), bytes.len())
    };
    if ok {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetCachedOwnershipTicket(
    env: JNIEnv,
    _cls: JClass,
    j_app_id: jint,
    j_out: JByteArray,
) -> jint {
    if j_app_id <= 0 {
        return 0;
    }
    if j_out.is_null() {
        let mut out_len: usize = 0;
        unsafe {
            wn_cm_get_cached_app_ownership_ticket(
                j_app_id as u32,
                std::ptr::null_mut(),
                0,
                &mut out_len,
            );
        }
        return out_len as jint;
    }
    let max = env.get_array_length(&j_out).unwrap_or(0).max(0) as usize;
    let mut tmp = vec![0u8; max];
    let mut out_len: usize = 0;
    let ok = unsafe {
        wn_cm_get_cached_app_ownership_ticket(j_app_id as u32, tmp.as_mut_ptr(), max, &mut out_len)
    };
    if !ok {
        return out_len as jint;
    }
    let _ = env.set_byte_array_region(&j_out, 0, unsafe {
        std::slice::from_raw_parts(tmp.as_ptr() as *const i8, out_len)
    });
    out_len as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRequestUserInfoBulk(
    env: JNIEnv,
    _cls: JClass,
    j_sids: JLongArray,
    j_flags: jint,
) -> jboolean {
    if j_sids.is_null() {
        return JNI_FALSE;
    }
    let arr = long_array_to_vec(&env, &j_sids);
    if arr.is_empty() {
        return JNI_FALSE;
    }
    let u64s: Vec<u64> = arr.into_iter().map(|v| v as u64).collect();
    let ok = unsafe {
        wn_cm_request_user_info_bulk(u64s.as_ptr(), u64s.len(), j_flags)
    };
    if ok {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticClearRichPresence(
    _env: JNIEnv,
    _cls: JClass,
) {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return;
    }
    let f = unsafe { vtable_fn(obj, 44) };
    if f.is_null() {
        return;
    }
    let fnp: extern "C" fn(*mut c_void) = unsafe { std::mem::transmute(f) };
    fnp(obj);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticSetRichPresence(
    mut env: JNIEnv,
    _cls: JClass,
    j_key: JString,
    j_value: JString,
) -> jboolean {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() || j_key.is_null() {
        return JNI_FALSE;
    }
    let key = jstr_to_string(&mut env, &j_key);
    let value = jstr_to_string(&mut env, &j_value);
    let c_key = std::ffi::CString::new(key).unwrap_or_default();
    let c_val = std::ffi::CString::new(value.clone()).unwrap_or_default();
    let f = unsafe { vtable_fn(obj, 43) };
    if f.is_null() {
        return JNI_FALSE;
    }
    let fnp: extern "C" fn(*mut c_void, *const c_char, *const c_char) -> bool =
        unsafe { std::mem::transmute(f) };
    let v_ptr = if value.is_empty() {
        std::ptr::null()
    } else {
        c_val.as_ptr()
    };
    if fnp(obj, c_key.as_ptr(), v_ptr) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendPersonaState(
    _env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
) -> jint {
    let p = state::pushed();
    let friends = p.friends.lock().expect("pushed.friends poisoned");
    friends
        .friend_persona_states
        .get(&(j_steam_id as u64))
        .map(|v| *v as jint)
        .unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticRichPresenceKeyCount(
    _env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
) -> jint {
    let obj = iface::isteam_friends::instance();
    if obj.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(obj, 46) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> i32 = unsafe { std::mem::transmute(f) };
    fnp(obj, j_steam_id as u64)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetFriendAvatarHash(
    env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
    j_hash: JByteArray,
) {
    let sid = j_steam_id as u64;
    if sid == 0 {
        return;
    }
    let changed = {
        let p = state::pushed();
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        let slot = friends
            .friend_avatar_hashes
            .entry(sid)
            .or_insert_with(Vec::new);
        if j_hash.is_null() {
            if !slot.is_empty() {
                slot.clear();
                true
            } else {
                false
            }
        } else {
            let bytes = byte_array_to_vec(&env, &j_hash);
            if bytes != *slot {
                *slot = bytes;
                true
            } else {
                false
            }
        }
    };
    if !changed {
        return;
    }
    let payload = cb::PersonaStateChange {
        m_ulSteamID: sid,
        m_nChangeFlags: cb::K_PERSONA_CHANGE_AVATAR,
        _pad: 0,
    };
    push_user_callback(cb::K_PERSONA_STATE_CHANGE, &payload);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetFriendAvatarHashHex(
    mut env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
) -> jstring {
    let sid = j_steam_id as u64;
    let hex = {
        let p = state::pushed();
        let friends = p.friends.lock().expect("pushed.friends poisoned");
        friends
            .friend_avatar_hashes
            .get(&sid)
            .map(|h| {
                let mut s = String::with_capacity(h.len() * 2);
                for b in h {
                    s.push_str(&format!("{:02x}", b));
                }
                s
            })
            .unwrap_or_default()
    };
    new_string_or_null(&mut env, &hex)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativePushFriendAvatar(
    env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
    j_tier: jint,
    j_width: jint,
    j_height: jint,
    j_rgba: JByteArray,
) -> jint {
    if j_steam_id == 0 || j_width <= 0 || j_height <= 0 || j_rgba.is_null() {
        return 0;
    }
    if !(0..=2).contains(&j_tier) {
        return 0;
    }
    let expected = j_width * j_height * 4;
    let n = env.get_array_length(&j_rgba).unwrap_or(0);
    if n != expected {
        return 0;
    }
    let bytes = byte_array_to_vec(&env, &j_rgba);
    let handle = {
        let p = state::pushed();
        let mut friends = p.friends.lock().expect("pushed.friends poisoned");
        let handle = friends.next_image_handle;
        friends.next_image_handle += 1;
        friends.image_registry.insert(
            handle,
            ImageEntry {
                width: j_width,
                height: j_height,
                rgba: bytes,
            },
        );
        let av = friends
            .friend_avatars
            .entry(j_steam_id as u64)
            .or_default();
        match j_tier {
            0 => av.small = handle,
            1 => av.medium = handle,
            2 => av.large = handle,
            _ => {}
        }
        handle
    };
    let payload = cb::AvatarImageLoaded {
        m_steamID: j_steam_id as u64,
        m_iImage: handle,
        m_iWide: j_width,
        m_iTall: j_height,
    };
    push_user_callback(cb::K_AVATAR_IMAGE_LOADED, &payload);
    handle
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetTieredAvatarSize(
    _env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
    j_tier: jint,
) -> jlong {
    if !(0..=2).contains(&j_tier) {
        return 0;
    }
    let friends = iface::isteam_friends::instance();
    if friends.is_null() {
        return 0;
    }
    let slot = (34 + j_tier) as usize;
    let f = unsafe { vtable_fn(friends, slot) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> i32 = unsafe { std::mem::transmute(f) };
    let handle = fnp(friends, j_steam_id as u64);
    if handle <= 0 {
        return 0;
    }
    let utils = iface::isteam_utils::instance();
    if utils.is_null() {
        return (handle as i64) << 32;
    }
    let sf = unsafe { vtable_fn(utils, 5) };
    if sf.is_null() {
        return (handle as i64) << 32;
    }
    let size_fn: extern "C" fn(*mut c_void, i32, *mut u32, *mut u32) -> bool =
        unsafe { std::mem::transmute(sf) };
    let mut w: u32 = 0;
    let mut h: u32 = 0;
    if !size_fn(utils, handle, &mut w, &mut h) {
        return (handle as i64) << 32;
    }
    let lo = ((w << 16) | (h & 0xFFFF)) as i64;
    ((handle as i64) << 32) | lo
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetSmallAvatarSize(
    _env: JNIEnv,
    _cls: JClass,
    j_steam_id: jlong,
) -> jlong {
    let friends = iface::isteam_friends::instance();
    if friends.is_null() {
        return 0;
    }
    let f = unsafe { vtable_fn(friends, 34) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, u64) -> i32 = unsafe { std::mem::transmute(f) };
    let handle = fnp(friends, j_steam_id as u64);
    if handle <= 0 {
        return 0;
    }
    let utils = iface::isteam_utils::instance();
    if utils.is_null() {
        return (handle as i64) << 32;
    }
    let sf = unsafe { vtable_fn(utils, 5) };
    if sf.is_null() {
        return (handle as i64) << 32;
    }
    let size_fn: extern "C" fn(*mut c_void, i32, *mut u32, *mut u32) -> bool =
        unsafe { std::mem::transmute(sf) };
    let mut w: u32 = 0;
    let mut h: u32 = 0;
    if !size_fn(utils, handle, &mut w, &mut h) {
        return (handle as i64) << 32;
    }
    let lo = ((w << 16) | (h & 0xFFFF)) as i64;
    ((handle as i64) << 32) | lo
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeDiagnosticGetImageRGBA(
    env: JNIEnv,
    _cls: JClass,
    j_handle: jint,
    j_out: JByteArray,
) -> jint {
    if j_handle <= 0 || j_out.is_null() {
        return 0;
    }
    let utils = iface::isteam_utils::instance();
    if utils.is_null() {
        return 0;
    }
    let n = env.get_array_length(&j_out).unwrap_or(0).max(0) as usize;
    let mut tmp = vec![0u8; n];
    let f = unsafe { vtable_fn(utils, 6) };
    if f.is_null() {
        return 0;
    }
    let fnp: extern "C" fn(*mut c_void, i32, *mut u8, i32) -> bool =
        unsafe { std::mem::transmute(f) };
    if !fnp(utils, j_handle, tmp.as_mut_ptr(), n as i32) {
        return 0;
    }
    let _ = env.set_byte_array_region(&j_out, 0, unsafe {
        std::slice::from_raw_parts(tmp.as_ptr() as *const i8, n)
    });
    n as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativeSetGameOverlayActive(
    _env: JNIEnv,
    _cls: JClass,
    j_active: jboolean,
) {
    let active = j_active != JNI_FALSE;
    let p = state::pushed();
    let prev = p.overlay_active.swap(active, Ordering::SeqCst);
    if prev == active {
        return;
    }
    let payload = cb::GameOverlayActivated {
        m_bActive: active,
        _pad: [0; 7],
    };
    push_user_callback(cb::K_GAME_OVERLAY_ACTIVATED, &payload);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnLibSteamClient_nativePollOverlayRequest(
    mut env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let r: OverlayRequest = {
        let p = state::pushed();
        let mut lobbies = p.lobbies.lock().expect("pushed.lobbies poisoned");
        match lobbies.overlay_request_queue.pop_front() {
            Some(r) => r,
            None => return std::ptr::null_mut(),
        }
    };
    let s = format!("{}\x01{}\x01{}\x01{}", r.kind, r.arg1, r.sid, r.app_id);
    new_string_or_null(&mut env, &s)
}
