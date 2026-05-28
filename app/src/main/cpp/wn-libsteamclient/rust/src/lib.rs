//! Rust implementation of WinNative's `libsteamclient.so`.
//!
//! Hosts Valve's Steam SDK ABI surface for Wine's `lsteamclient.dll` peer.
//! Forwards CM-side work to `libwnsteam.so` via the `wn_cm_*` Rust→Rust
//! C-ABI bridge declared in `bridge.rs`.

#![allow(clippy::missing_safety_doc, clippy::result_large_err)]
#![allow(clippy::too_many_arguments)]

pub mod api_entry;
pub mod bridge;
pub mod callback_registry;
pub mod callbacks;
pub mod client;
pub mod iclient_engine;
pub mod iface;
pub mod jni_pushed_state;
pub mod log;
pub mod state;
pub mod tcp_services;
pub mod vtable;

use jni::sys::{jint, JNI_VERSION_1_6};
use jni::JavaVM;
use std::ffi::c_void;
use std::sync::OnceLock;

pub(crate) static JVM: OnceLock<JavaVM> = OnceLock::new();

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _reserved: *mut c_void) -> jint {
    let _ = JVM.set(vm);
    JNI_VERSION_1_6
}

/// Runs at .so load. Mirrors the C++ `__attribute__((constructor))` hooks:
/// seeds state from env and installs CM bridge observers so logon/persona/
/// friends/account/server-realtime updates pump straight into our callback
/// queue and pushed state.
#[ctor::ctor]
fn so_loaded() {
    state::seed_from_env();
    jni_pushed_state::register_observers();
}
