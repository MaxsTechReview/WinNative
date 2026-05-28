//! One module per Steam SDK interface. Each exposes `instance()` returning a
//! `*mut c_void` whose first machine word points at a `#[repr(C)]` vtable
//! struct matching the original C++ vtable slot layout exactly.

pub mod isteam_app_list;
pub mod isteam_apps;
pub mod isteam_friends;
pub mod isteam_game_server;
pub mod isteam_html_surface;
pub mod isteam_input;
pub mod isteam_inventory;
pub mod isteam_matchmaking;
pub mod isteam_matchmaking_servers;
pub mod isteam_music;
pub mod isteam_music_remote;
pub mod isteam_networking;
pub mod isteam_networking_messages;
pub mod isteam_networking_sockets;
pub mod isteam_networking_utils;
pub mod isteam_parental;
pub mod isteam_parties;
pub mod isteam_remote_play;
pub mod isteam_remote_storage;
pub mod isteam_screenshots;
pub mod isteam_ugc;
pub mod isteam_user;
pub mod isteam_user_stats;
pub mod isteam_utils;
pub mod isteam_video;
