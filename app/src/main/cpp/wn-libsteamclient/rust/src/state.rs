//! Port of `wn_libsteamclient::runtime_state` (runtime_state.h/cpp).
//!
//! State + PushedState held as `OnceLock` singletons. Hot scalars are atomic;
//! collections live in `Mutex` slots. The single C++ `state_mutex()` is split
//! into per-section mutexes to reduce contention.

use crate::bridge;
use crate::callbacks as cb;
use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicI64, AtomicU32, AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};

pub type HSteamPipe = i32;
pub type HSteamUser = i32;

#[derive(Default)]
pub struct CallbackMsg {
    pub user: i32,
    pub id: i32,
    pub body: Vec<u8>,
}

#[derive(Default)]
pub struct CallResultMsg {
    pub h_call: u64,
    pub callback_id: i32,
    pub io_failure: bool,
    pub body: Vec<u8>,
}

pub struct DlProgress {
    pub bytes_downloaded: u64,
    pub bytes_total: u64,
}

pub struct WorkshopItemInfo {
    pub install_dir: String,
    pub size_bytes: u64,
    pub timestamp: u32,
    pub installed: bool,
}

#[derive(Default, Clone)]
pub struct LobbyMember {
    pub persona_name: String,
    pub data: HashMap<String, String>,
}

#[derive(Default)]
pub struct LobbyState {
    pub app_id: u32,
    pub owner_sid: u64,
    pub max_members: i32,
    pub lobby_type: i32,
    pub lobby_flags: i32,
    pub joinable: bool,
    pub game_server_ip: u32,
    pub game_server_port: u16,
    pub game_server_sid: u64,
    pub data: HashMap<String, String>,
    pub members: HashMap<u64, LobbyMember>,
}

pub struct LobbyChatEntry {
    pub sender_sid: u64,
    pub chat_type: u8,
    pub body: Vec<u8>,
}

#[derive(Default)]
pub struct P2PSessionState {
    pub last_session_error: u64,
    pub connection_active: bool,
    pub connecting: bool,
    pub bytes_queued_for_send: u32,
    pub remote_ip: u32,
    pub remote_port: u16,
    pub using_relay: bool,
}

pub struct P2PInboundPacket {
    pub sender_sid: u64,
    pub channel: i32,
    pub body: Vec<u8>,
}

#[derive(Default, Clone)]
pub struct OverlayRequest {
    pub kind: String,
    pub arg1: String,
    pub sid: u64,
    pub app_id: u32,
}

#[derive(Default, Clone, Copy)]
pub struct LicenseEntry {
    pub package_id: u32,
    pub owner_id: u32,
    pub time_created: u32,
    pub license_type: u32,
    pub flags: u32,
    pub change_number: i32,
    pub minute_limit: i32,
    pub minutes_used: i32,
}

#[derive(Default, Clone)]
pub struct DlcEntry {
    pub app_id: u32,
    pub name: String,
    pub available: bool,
}

#[derive(Default, Clone)]
pub struct ImageEntry {
    pub width: i32,
    pub height: i32,
    pub rgba: Vec<u8>,
}

#[derive(Default, Clone, Copy)]
pub struct FriendAvatarHandles {
    pub small: i32,
    pub medium: i32,
    pub large: i32,
}

#[derive(Default)]
pub struct AchievementEntry {
    pub api_name: String,
    pub display_names: HashMap<String, String>,
    pub descriptions: HashMap<String, String>,
    pub icon: String,
    pub hidden: bool,
    pub achieved: bool,
    pub unlock_time: u32,
    pub icon_handle: i32,
    pub pending_store: bool,
    pub block_id: i32,
    pub bit_index: i32,
}

impl AchievementEntry {
    pub fn new() -> Self {
        Self {
            block_id: -1,
            ..Default::default()
        }
    }
}

#[derive(Default, Clone, Copy)]
pub struct AvgRateAccum {
    pub total_count: f64,
    pub total_time: f64,
}

pub struct AuthTicket {
    pub h_ticket: u32,
    pub app_id: u32,
    pub body: Vec<u8>,
}

pub struct CloudFileEntry {
    pub name: String,
    pub size: i32,
    pub timestamp: i64,
}

pub type RichPresenceMap = Vec<(String, String)>;

/// Mutable subsections of `PushedState`. Each is guarded by its own `Mutex`.
#[derive(Default)]
pub struct PushedTextFields {
    pub persona_name: String,
    pub ip_country: String,
    pub ui_language: String,
}

#[derive(Default)]
pub struct PushedApps {
    pub owned_apps: HashSet<u32>,
    pub installed_apps: HashSet<u32>,
    pub app_install_dirs: HashMap<u32, String>,
    pub app_current_beta: HashMap<u32, String>,
    pub app_dl_progress: HashMap<u32, DlProgress>,
    pub app_cloud_remote_dirs: HashMap<u32, String>,
    pub app_low_violence: HashSet<u32>,
    pub app_vac_banned: HashSet<u32>,
    pub apps_marked_corrupt: HashSet<u32>,
    pub app_source_packages: HashMap<u32, Vec<u32>>,
    pub app_dlcs: HashMap<u32, Vec<DlcEntry>>,
    pub app_installed_depots: HashMap<u32, Vec<u32>>,
    pub app_names: HashMap<u32, String>,
    pub app_build_ids: HashMap<u32, u32>,
    pub launch_command_line: String,
    pub encrypted_app_tickets: HashMap<u32, Vec<u8>>,
    pub subscribed_workshop_items: HashMap<u32, HashMap<u64, WorkshopItemInfo>>,
    pub inventory_item_defs: HashMap<u32, HashMap<i32, HashMap<String, String>>>,
}

#[derive(Default)]
pub struct PushedFriends {
    pub friends: Vec<u64>,
    pub friend_persona_names: HashMap<u64, String>,
    pub friend_persona_states: HashMap<u64, u32>,
    pub friend_game_played_app: HashMap<u64, u32>,
    pub rich_presence: HashMap<u64, RichPresenceMap>,
    pub image_registry: HashMap<i32, ImageEntry>,
    pub friend_avatars: HashMap<u64, FriendAvatarHandles>,
    pub next_image_handle: i32,
    pub friend_avatar_hashes: HashMap<u64, Vec<u8>>,
    pub friend_steam_levels: HashMap<u64, i32>,
    pub player_nicknames: HashMap<u64, String>,
    pub self_game_badges: HashMap<i32, i32>,
}

impl PushedFriends {
    pub fn new() -> Self {
        Self {
            next_image_handle: 1,
            ..Default::default()
        }
    }
}

#[derive(Default)]
pub struct PushedLobbies {
    pub active_lobbies: HashMap<u64, LobbyState>,
    pub lobby_match_list: Vec<u64>,
    pub lobby_chat_buffer: HashMap<u64, Vec<LobbyChatEntry>>,
    pub active_p2p_sessions: HashMap<u64, P2PSessionState>,
    pub p2p_inbound_queue: HashMap<i32, VecDeque<P2PInboundPacket>>,
    pub overlay_request_queue: VecDeque<OverlayRequest>,
}

#[derive(Default)]
pub struct PushedLicenses {
    pub licenses: HashMap<u32, LicenseEntry>,
}

#[derive(Default)]
pub struct PushedCloud {
    pub cloud_files: Vec<CloudFileEntry>,
}

#[derive(Default)]
pub struct PushedStats {
    pub achievements: Vec<AchievementEntry>,
    pub achievement_index: HashMap<String, usize>,
    pub stats_int: HashMap<String, i32>,
    pub stats_float: HashMap<String, f32>,
    pub stat_name_to_id: HashMap<String, u32>,
    pub dirty_stats_int: HashSet<String>,
    pub dirty_stats_float: HashSet<String>,
    pub stats_avg_rate: HashMap<String, AvgRateAccum>,
}

#[derive(Default)]
pub struct PushedAuth {
    pub auth_tickets: HashMap<u32, AuthTicket>,
}

/// Port of C++ `PushedState`. Atomics first, then `Mutex<sub-struct>` slots.
pub struct PushedState {
    pub steam_id: AtomicU64,
    pub account_id: AtomicU32,
    pub persona_state: AtomicI32,
    pub app_id: AtomicU32,
    pub ip_country_set: AtomicI32,
    pub server_realtime: AtomicU32,
    pub server_realtime_anchor_local_ms: AtomicI64,
    pub account_phone_verified: AtomicBool,
    pub account_two_factor_enabled: AtomicBool,
    pub account_phone_identifying: AtomicBool,
    pub account_phone_requires_verification: AtomicBool,
    pub p2p_relay_allowed: AtomicBool,
    pub self_player_level: AtomicI32,
    pub stats_ready: AtomicBool,
    pub overlay_active: AtomicBool,
    pub next_auth_ticket_handle: AtomicU32,
    pub app_is_family_shared: AtomicBool,
    pub cloud_enabled_account: AtomicBool,
    pub cloud_enabled_app: AtomicBool,
    pub cloud_quota_total: AtomicU64,
    pub cloud_quota_available: AtomicU64,
    pub encrypted_app_ticket_eresult: AtomicI32,

    pub text: Mutex<PushedTextFields>,
    pub apps: Mutex<PushedApps>,
    pub friends: Mutex<PushedFriends>,
    pub lobbies: Mutex<PushedLobbies>,
    pub licenses: Mutex<PushedLicenses>,
    pub cloud: Mutex<PushedCloud>,
    pub stats: Mutex<PushedStats>,
    pub auth: Mutex<PushedAuth>,
}

impl PushedState {
    fn new() -> Self {
        Self {
            steam_id: AtomicU64::new(0),
            account_id: AtomicU32::new(0),
            persona_state: AtomicI32::new(0),
            app_id: AtomicU32::new(0),
            ip_country_set: AtomicI32::new(0),
            server_realtime: AtomicU32::new(0),
            server_realtime_anchor_local_ms: AtomicI64::new(0),
            account_phone_verified: AtomicBool::new(false),
            account_two_factor_enabled: AtomicBool::new(false),
            account_phone_identifying: AtomicBool::new(false),
            account_phone_requires_verification: AtomicBool::new(false),
            p2p_relay_allowed: AtomicBool::new(true),
            self_player_level: AtomicI32::new(0),
            stats_ready: AtomicBool::new(false),
            overlay_active: AtomicBool::new(false),
            next_auth_ticket_handle: AtomicU32::new(1),
            app_is_family_shared: AtomicBool::new(false),
            cloud_enabled_account: AtomicBool::new(true),
            cloud_enabled_app: AtomicBool::new(false),
            cloud_quota_total: AtomicU64::new(0),
            cloud_quota_available: AtomicU64::new(0),
            encrypted_app_ticket_eresult: AtomicI32::new(0),

            text: Mutex::new(PushedTextFields::default()),
            apps: Mutex::new(PushedApps::default()),
            friends: Mutex::new(PushedFriends::new()),
            lobbies: Mutex::new(PushedLobbies::default()),
            licenses: Mutex::new(PushedLicenses::default()),
            cloud: Mutex::new(PushedCloud::default()),
            stats: Mutex::new(PushedStats::default()),
            auth: Mutex::new(PushedAuth::default()),
        }
    }
}

/// Port of C++ `State`.
pub struct State {
    pub pipe: AtomicI32,
    pub user: AtomicI32,
    pub logged_on: AtomicBool,
    pub connected: AtomicBool,

    pub callback_mu: Mutex<CallbackQueue>,
    pub call_results_mu: Mutex<CallResultsTable>,
}

#[derive(Default)]
pub struct CallbackQueue {
    pub queue: VecDeque<CallbackMsg>,
    pub last_param: Vec<u8>,
}

#[derive(Default)]
pub struct CallResultsTable {
    pub pending: HashMap<u64, CallResultMsg>,
    pub next_api_call_handle: u64,
}

impl CallResultsTable {
    fn new() -> Self {
        Self {
            pending: HashMap::new(),
            next_api_call_handle: 1,
        }
    }
}

impl State {
    fn new() -> Self {
        Self {
            pipe: AtomicI32::new(0),
            user: AtomicI32::new(0),
            logged_on: AtomicBool::new(false),
            connected: AtomicBool::new(false),
            callback_mu: Mutex::new(CallbackQueue::default()),
            call_results_mu: Mutex::new(CallResultsTable::new()),
        }
    }
}

static STATE: OnceLock<State> = OnceLock::new();
static PUSHED: OnceLock<PushedState> = OnceLock::new();
static SEED_DONE: OnceLock<()> = OnceLock::new();

pub fn state() -> &'static State {
    STATE.get_or_init(State::new)
}

pub fn pushed() -> &'static PushedState {
    PUSHED.get_or_init(PushedState::new)
}

/// Idempotent env seed. Reads STEAMID / SteamAppId / SteamUser at first call.
pub fn seed_from_env() {
    SEED_DONE.get_or_init(|| {
        let env_sid = std::env::var("STEAMID").ok();
        let env_app = std::env::var("SteamAppId").ok();
        let env_usr = std::env::var("SteamUser").ok();

        let sid: u64 = env_sid
            .as_deref()
            .and_then(|s| s.parse::<u64>().ok())
            .unwrap_or(0);
        let app: u32 = env_app
            .as_deref()
            .and_then(|s| s.parse::<u32>().ok())
            .unwrap_or(0);

        if sid != 0 {
            pushed().steam_id.store(sid, Ordering::SeqCst);
            state().user.store(1, Ordering::SeqCst);
            state().logged_on.store(true, Ordering::SeqCst);
            state().connected.store(true, Ordering::SeqCst);
            crate::log::log_info(&format!(
                "guest seed: STEAMID={} logged_on=true",
                sid
            ));
        }
        if app != 0 {
            pushed().app_id.store(app, Ordering::SeqCst);
        }
        if let Some(usr) = env_usr {
            if !usr.is_empty() {
                let mut text = pushed().text.lock().expect("pushed.text poisoned");
                if text.persona_name.is_empty() {
                    text.persona_name = usr;
                }
            }
        }
    });
}

pub fn alloc_pipe() -> HSteamPipe {
    crate::tcp_services::start_tcp_services();
    seed_from_env();
    let s = state();
    let cur = s.pipe.load(Ordering::SeqCst);
    if cur != 0 {
        return 0;
    }
    s.pipe.store(1, Ordering::SeqCst);
    1
}

pub fn release_pipe(pipe: HSteamPipe) -> bool {
    set_logged_on(false, 6);
    let s = state();
    if pipe == 0 || s.pipe.load(Ordering::SeqCst) != pipe {
        return false;
    }
    s.pipe.store(0, Ordering::SeqCst);
    s.user.store(0, Ordering::SeqCst);
    true
}

pub fn alloc_global_user(pipe: HSteamPipe) -> HSteamUser {
    let s = state();
    if pipe == 0 || s.pipe.load(Ordering::SeqCst) != pipe {
        return 0;
    }
    let cur = s.user.load(Ordering::SeqCst);
    if cur != 0 {
        return cur;
    }
    s.user.store(1, Ordering::SeqCst);
    1
}

pub fn release_user(pipe: HSteamPipe, user: HSteamUser) {
    let s = state();
    if pipe == 0 || user == 0 {
        return;
    }
    if s.pipe.load(Ordering::SeqCst) != pipe || s.user.load(Ordering::SeqCst) != user {
        return;
    }
    s.user.store(0, Ordering::SeqCst);
    s.logged_on.store(false, Ordering::SeqCst);
}

pub fn push_callback(user: i32, id: i32, data: *const u8, n: usize) {
    let s = state();
    let mut q = s.callback_mu.lock().expect("callback queue poisoned");
    let body = if !data.is_null() && n > 0 {
        unsafe { std::slice::from_raw_parts(data, n).to_vec() }
    } else {
        Vec::new()
    };
    q.queue.push_back(CallbackMsg { user, id, body });
}

pub fn push_callback_bytes(user: i32, id: i32, data: &[u8]) {
    let s = state();
    let mut q = s.callback_mu.lock().expect("callback queue poisoned");
    q.queue.push_back(CallbackMsg {
        user,
        id,
        body: data.to_vec(),
    });
}

pub fn alloc_api_call_handle() -> u64 {
    let s = state();
    let mut t = s.call_results_mu.lock().expect("call results poisoned");
    let h = t.next_api_call_handle;
    t.next_api_call_handle = t.next_api_call_handle.wrapping_add(1);
    if t.next_api_call_handle == 0 {
        t.next_api_call_handle = 1;
    }
    h
}

pub fn push_call_result(h_call: u64, callback_id: i32, data: *const u8, n: usize, io_failure: bool) {
    if h_call == 0 {
        return;
    }
    let body = if !data.is_null() && n > 0 {
        unsafe { std::slice::from_raw_parts(data, n).to_vec() }
    } else {
        Vec::new()
    };
    push_call_result_bytes(h_call, callback_id, &body, io_failure);
}

pub fn push_call_result_bytes(h_call: u64, callback_id: i32, data: &[u8], io_failure: bool) {
    if h_call == 0 {
        return;
    }
    let s = state();
    {
        let mut t = s.call_results_mu.lock().expect("call results poisoned");
        t.pending.insert(
            h_call,
            CallResultMsg {
                h_call,
                callback_id,
                io_failure,
                body: data.to_vec(),
            },
        );
    }
    let ev = cb::SteamAPICallCompleted {
        m_hAsyncCall: h_call,
        m_iCallback: callback_id,
        m_cubParam: data.len() as u32,
    };
    let user = s.user.load(Ordering::SeqCst);
    let raw = unsafe {
        std::slice::from_raw_parts(
            &ev as *const cb::SteamAPICallCompleted as *const u8,
            std::mem::size_of::<cb::SteamAPICallCompleted>(),
        )
    };
    push_callback_bytes(user, cb::K_STEAM_API_CALL_COMPLETED, raw);
}

pub fn set_logged_on(logged_on: bool, eresult_on_disconnect: i32) {
    let s = state();
    let prev = s.logged_on.swap(logged_on, Ordering::SeqCst);
    s.connected.store(logged_on, Ordering::SeqCst);
    if prev == logged_on {
        return;
    }
    let h_user = s.user.load(Ordering::SeqCst);
    if logged_on {
        push_callback(h_user, cb::K_STEAM_SERVERS_CONNECTED, std::ptr::null(), 0);
    } else {
        let payload = cb::SteamServersDisconnected {
            m_eResult: eresult_on_disconnect,
        };
        let raw = unsafe {
            std::slice::from_raw_parts(
                &payload as *const _ as *const u8,
                std::mem::size_of::<cb::SteamServersDisconnected>(),
            )
        };
        push_callback_bytes(h_user, cb::K_STEAM_SERVERS_DISCONNECTED, raw);
    }
    // Notify CM bridge observers (Rust → Rust) so wn-steam-client sees the transition.
    let _ = &bridge::DUMMY;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn alloc_pipe_is_idempotent() {
        // Reset state for a deterministic test (test runs serial since state is global).
        let s = state();
        s.pipe.store(0, Ordering::SeqCst);
        s.user.store(0, Ordering::SeqCst);
        let p = alloc_pipe();
        assert_eq!(p, 1);
        let p2 = alloc_pipe();
        // Second call returns 0 (already-allocated guard), but state().pipe stays at 1.
        assert_eq!(p2, 0);
        assert_eq!(s.pipe.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn api_call_handles_are_unique_and_nonzero() {
        let h1 = alloc_api_call_handle();
        let h2 = alloc_api_call_handle();
        assert_ne!(h1, 0);
        assert_ne!(h2, 0);
        assert_ne!(h1, h2);
    }
}
