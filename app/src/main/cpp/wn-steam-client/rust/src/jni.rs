use crate::auth_session::{
    auth_result_from_poll, build_credentials_begin_request, build_guard_code_request,
    build_password_rsa_request, build_poll_request, build_qr_begin_request,
    choose_guard_confirmation, job_error, pending_credentials_from_begin_response,
    pending_qr_from_begin_response, sleep_slices, AuthSessionResult, CredentialsAuthConfig,
    QrAuthConfig,
};
use crate::cm_bridge;
use crate::cm_client::{CMClientCore, ClientState, OutboundProtoMessage, OutboundServiceCall};
use crate::cm_runtime::CMClientRuntime;
use crate::emsg::EMsg;
use crate::encrypted_channel::{ChannelDisconnectReason, EncryptedChannel};
use crate::version;
use crate::wine_bridge::{WineBridge, WineBridgeConfig};
use crate::ws_connection::WsConnection;
use jni::objects::{
    GlobalRef, JByteArray, JClass, JIntArray, JLongArray, JObject, JString, JValue,
};
use jni::sys::{
    jboolean, jbyteArray, jint, jlong, jlongArray, jstring, JNI_FALSE, JNI_TRUE, JNI_VERSION_1_6,
};
use jni::{JNIEnv, JavaVM};
use serde_json::json;
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc;
use std::sync::OnceLock;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

struct WnConnectionHandle {
    channel: EncryptedChannel,
    observer: Mutex<Option<GlobalRef>>,
}

struct WnSteamSessionHandle {
    core: Arc<CMClientCore>,
    ca_bundle_path: String,
    auto_populate_library: bool,
    wine_bridge: WineBridge,
    runtime: Mutex<Option<Arc<CMClientRuntime>>>,
    login_cancel: Mutex<Option<Arc<AtomicBool>>>,
    download_cancel: Arc<AtomicBool>,
    outbound_wires: Mutex<Vec<Vec<u8>>>,
    state_observer: Mutex<Option<GlobalRef>>,
    library_observer: Mutex<Option<GlobalRef>>,
}

impl WnConnectionHandle {
    fn new() -> Self {
        Self {
            channel: EncryptedChannel::new(Box::new(WsConnection::new())),
            observer: Mutex::new(None),
        }
    }
}

impl WnSteamSessionHandle {
    fn new() -> Self {
        let core = Arc::new(CMClientCore::default());
        cm_bridge::set_active_core(Arc::clone(&core));
        Self {
            core,
            ca_bundle_path: String::new(),
            auto_populate_library: true,
            wine_bridge: WineBridge::default(),
            runtime: Mutex::new(None),
            login_cancel: Mutex::new(None),
            download_cancel: Arc::new(AtomicBool::new(false)),
            outbound_wires: Mutex::new(Vec::new()),
            state_observer: Mutex::new(None),
            library_observer: Mutex::new(None),
        }
    }

    fn set_state(&self, env: &mut JNIEnv, state: ClientState) {
        self.core.set_state(state);
        self.notify_state(env, state);
    }

    fn notify_state(&self, env: &mut JNIEnv, state: ClientState) {
        let observer = self
            .state_observer
            .lock()
            .expect("session state observer poisoned")
            .as_ref()
            .cloned();
        let Some(observer) = observer else {
            return;
        };
        let _ = env.call_method(
            observer.as_obj(),
            "onStateChanged",
            "(I)V",
            &[JValue::Int(state as jint)],
        );
        if env.exception_check().unwrap_or(false) {
            let _ = env.exception_clear();
        }
    }

    fn enqueue_proto(&self, message: Option<OutboundProtoMessage>) -> bool {
        let Some(message) = message else {
            return false;
        };
        self.enqueue_wire(message.wire)
    }

    fn enqueue_wire(&self, wire: Vec<u8>) -> bool {
        if wire.is_empty() {
            return false;
        }
        self.core.enqueue_wire(wire.clone());
        self.outbound_wires
            .lock()
            .expect("session outbound queue poisoned")
            .push(wire);
        if let Some(runtime) = self
            .runtime
            .lock()
            .expect("session runtime poisoned")
            .as_ref()
        {
            runtime.flush_outbound();
        }
        true
    }

    fn runtime(&self) -> Arc<CMClientRuntime> {
        let mut slot = self.runtime.lock().expect("session runtime poisoned");
        if let Some(runtime) = slot.as_ref() {
            return Arc::clone(runtime);
        }
        let runtime = CMClientRuntime::new(Arc::clone(&self.core), Box::new(WsConnection::new()));
        if !self.ca_bundle_path.is_empty() {
            runtime.set_ca_bundle_path(&self.ca_bundle_path);
        }
        *slot = Some(Arc::clone(&runtime));
        runtime
    }

    fn connected_runtime(&self) -> Option<Arc<CMClientRuntime>> {
        let runtime = self
            .runtime
            .lock()
            .expect("session runtime poisoned")
            .as_ref()
            .cloned()?;
        matches!(
            self.core.state(),
            ClientState::Connected | ClientState::LoggedOn
        )
        .then_some(runtime)
    }

    fn begin_login_cancel(&self) -> Arc<AtomicBool> {
        let cancel = Arc::new(AtomicBool::new(false));
        let mut slot = self.login_cancel.lock().expect("session login poisoned");
        if let Some(previous) = slot.replace(Arc::clone(&cancel)) {
            previous.store(true, Ordering::Relaxed);
        }
        cancel
    }

    fn cancel_login(&self) {
        if let Some(cancel) = self
            .login_cancel
            .lock()
            .expect("session login poisoned")
            .take()
        {
            cancel.store(true, Ordering::Relaxed);
        }
    }
}

fn to_handle(handle: Box<WnConnectionHandle>) -> jlong {
    Box::into_raw(handle) as isize as jlong
}

fn to_session_handle(handle: Box<WnSteamSessionHandle>) -> jlong {
    Box::into_raw(handle) as isize as jlong
}

unsafe fn from_handle_mut(handle: jlong) -> Option<&'static mut WnConnectionHandle> {
    if handle == 0 {
        return None;
    }
    unsafe { (handle as *mut WnConnectionHandle).as_mut() }
}

unsafe fn from_session_handle_mut(handle: jlong) -> Option<&'static mut WnSteamSessionHandle> {
    if handle == 0 {
        return None;
    }
    unsafe { (handle as *mut WnSteamSessionHandle).as_mut() }
}

unsafe fn drop_handle(handle: jlong) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut WnConnectionHandle));
        }
    }
}

unsafe fn drop_session_handle(handle: jlong) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut WnSteamSessionHandle));
        }
        cm_bridge::clear_active_core();
    }
}

static JVM: OnceLock<JavaVM> = OnceLock::new();

fn new_string_or_null(env: &mut JNIEnv, value: &str) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _reserved: *mut std::ffi::c_void) -> jint {
    let _ = JVM.set(vm);
    JNI_VERSION_1_6
}

fn dispatch_connection_connected(observer: &GlobalRef) {
    let Some(vm) = JVM.get() else {
        return;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return;
    };
    let _ = env.call_method(observer.as_obj(), "onConnected", "()V", &[]);
    clear_pending_exception(&mut env);
}

fn dispatch_connection_disconnected(
    observer: &GlobalRef,
    reason: ChannelDisconnectReason,
    detail: &str,
) {
    let Some(vm) = JVM.get() else {
        return;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return;
    };
    let Ok(detail) = env.new_string(detail) else {
        clear_pending_exception(&mut env);
        return;
    };
    let detail_obj = JObject::from(detail);
    let _ = env.call_method(
        observer.as_obj(),
        "onDisconnected",
        "(ILjava/lang/String;)V",
        &[JValue::Int(reason as jint), JValue::Object(&detail_obj)],
    );
    clear_pending_exception(&mut env);
}

fn dispatch_connection_message(observer: &GlobalRef, bytes: &[u8]) {
    let Some(vm) = JVM.get() else {
        return;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return;
    };
    let Ok(array) = env.byte_array_from_slice(bytes) else {
        clear_pending_exception(&mut env);
        return;
    };
    let array_obj = JObject::from(array);
    let _ = env.call_method(
        observer.as_obj(),
        "onMessage",
        "([B)V",
        &[JValue::Object(&array_obj)],
    );
    clear_pending_exception(&mut env);
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamClient_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    new_string_or_null(&mut env, version::version().string)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    to_handle(Box::new(WnConnectionHandle::new()))
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { drop_handle(handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeSetCaBundlePath(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    path: JString,
) {
    let Some(handle) = (unsafe { from_handle_mut(handle) }) else {
        return;
    };
    let Ok(path) = env.get_string(&path) else {
        return;
    };
    handle.channel.set_ca_bundle_path(&path.to_string_lossy());
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeSetObserver(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    observer: JObject,
) {
    let Some(handle) = (unsafe { from_handle_mut(handle) }) else {
        return;
    };
    let observer = if observer.is_null() {
        None
    } else {
        env.new_global_ref(observer).ok()
    };
    *handle
        .observer
        .lock()
        .expect("connection observer poisoned") = observer.clone();

    let Some(observer) = observer else {
        handle.channel.set_on_connected(|| {});
        handle.channel.set_on_disconnected(|_, _| {});
        handle.channel.set_on_message(|_| {});
        return;
    };

    let connected_observer = observer.clone();
    handle.channel.set_on_connected(move || {
        dispatch_connection_connected(&connected_observer);
    });
    let disconnected_observer = observer.clone();
    handle.channel.set_on_disconnected(move |reason, detail| {
        dispatch_connection_disconnected(&disconnected_observer, reason, detail);
    });
    handle.channel.set_on_message(move |bytes| {
        dispatch_connection_message(&observer, bytes);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeConnect(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
) -> jboolean {
    let Some(handle) = (unsafe { from_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let Ok(url) = env.get_string(&url) else {
        return JNI_FALSE;
    };
    if handle.channel.connect(&url.to_string_lossy()) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeDisconnect(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_handle_mut(handle) } {
        handle.channel.disconnect();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeSend(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    data: JByteArray,
) -> jboolean {
    let Some(handle) = (unsafe { from_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let Ok(bytes) = env.convert_byte_array(data) else {
        return JNI_FALSE;
    };
    if handle.channel.send(&bytes) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

pub fn empty_byte_array(env: JNIEnv) -> jbyteArray {
    env.new_byte_array(0)
        .map(|arr| arr.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn byte_array_or_null(env: &JNIEnv, bytes: &[u8]) -> jbyteArray {
    env.byte_array_from_slice(bytes)
        .map(|arr| arr.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn jstring_to_string(env: &mut JNIEnv, value: &JString) -> Option<String> {
    env.get_string(value)
        .ok()
        .map(|s| s.to_string_lossy().into_owned())
}

fn int_array_to_u32_vec(env: &JNIEnv, array: &JIntArray) -> Vec<u32> {
    let len = env.get_array_length(array).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut values = vec![0i32; len as usize];
    if env.get_int_array_region(array, 0, &mut values).is_err() {
        return Vec::new();
    }
    values.into_iter().map(|value| value as u32).collect()
}

fn long_array_to_u64_vec(env: &JNIEnv, array: &JLongArray) -> Vec<u64> {
    let len = env.get_array_length(array).unwrap_or(0);
    if len <= 0 {
        return Vec::new();
    }
    let mut values = vec![0i64; len as usize];
    if env.get_long_array_region(array, 0, &mut values).is_err() {
        return Vec::new();
    }
    values.into_iter().map(|value| value as u64).collect()
}

fn parse_u32_lines(value: &str) -> Vec<u32> {
    value
        .lines()
        .filter_map(|line| line.trim().parse::<u32>().ok())
        .collect()
}

fn parse_u64_lines(value: &str) -> Vec<u64> {
    value
        .lines()
        .filter_map(|line| line.trim().parse::<u64>().ok())
        .collect()
}

fn split_nonempty_lines(value: &str) -> Vec<String> {
    value
        .lines()
        .filter(|line| !line.is_empty())
        .map(ToOwned::to_owned)
        .collect()
}

fn kvnode_to_json_value(node: &crate::vdf::KVNode) -> serde_json::Value {
    if node.is_object() {
        let mut object = serde_json::Map::new();
        for child in &node.children {
            object.insert(child.name.clone(), kvnode_to_json_value(child));
        }
        serde_json::Value::Object(object)
    } else {
        json!(node.as_string(""))
    }
}

fn decode_hex(value: &str) -> Option<Vec<u8>> {
    let compact = value.trim();
    if !compact.len().is_multiple_of(2) {
        return None;
    }
    let mut out = Vec::with_capacity(compact.len() / 2);
    let bytes = compact.as_bytes();
    for pair in bytes.chunks_exact(2) {
        let hi = (pair[0] as char).to_digit(16)?;
        let lo = (pair[1] as char).to_digit(16)?;
        out.push(((hi << 4) | lo) as u8);
    }
    Some(out)
}

fn clear_pending_exception(env: &mut JNIEnv) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn call_auth_result_failure(env: &mut JNIEnv, callback: &JObject, code: jint, message: &str) {
    if callback.is_null() {
        return;
    }
    let Ok(error) = env.new_string(message) else {
        clear_pending_exception(env);
        return;
    };
    let Ok(empty) = env.new_string("") else {
        clear_pending_exception(env);
        return;
    };
    let error_obj = JObject::from(error);
    let empty_obj = JObject::from(empty);
    let Ok(result) = env.new_object(
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnAuthResult",
        "(ZILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZLjava/lang/String;)V",
        &[
            JValue::Bool(JNI_FALSE),
            JValue::Int(code),
            JValue::Object(&error_obj),
            JValue::Object(&empty_obj),
            JValue::Object(&empty_obj),
            JValue::Object(&empty_obj),
            JValue::Object(&empty_obj),
            JValue::Long(0),
            JValue::Bool(JNI_FALSE),
            JValue::Object(&empty_obj),
        ],
    ) else {
        clear_pending_exception(env);
        return;
    };
    let _ = env.call_method(
        callback,
        "onAuthResult",
        "(Lcom/winlator/cmod/feature/stores/steam/wnsteam/WnAuthResult;)V",
        &[JValue::Object(&result)],
    );
    clear_pending_exception(env);
}

fn dispatch_auth_result(callback: &GlobalRef, result: AuthSessionResult) {
    let Some(vm) = JVM.get() else {
        return;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return;
    };
    call_auth_result(&mut env, callback.as_obj(), &result);
}

fn call_auth_result(env: &mut JNIEnv, callback: &JObject, result: &AuthSessionResult) {
    if callback.is_null() {
        return;
    }
    let Ok(error_message) = env.new_string(&result.error_message) else {
        clear_pending_exception(env);
        return;
    };
    let Ok(account_name) = env.new_string(&result.account_name) else {
        clear_pending_exception(env);
        return;
    };
    let Ok(refresh_token) = env.new_string(&result.refresh_token) else {
        clear_pending_exception(env);
        return;
    };
    let Ok(access_token) = env.new_string(&result.access_token) else {
        clear_pending_exception(env);
        return;
    };
    let Ok(new_guard_data) = env.new_string(&result.new_guard_data) else {
        clear_pending_exception(env);
        return;
    };
    let Ok(agreement_session_url) = env.new_string(&result.agreement_session_url) else {
        clear_pending_exception(env);
        return;
    };
    let error_message = JObject::from(error_message);
    let account_name = JObject::from(account_name);
    let refresh_token = JObject::from(refresh_token);
    let access_token = JObject::from(access_token);
    let new_guard_data = JObject::from(new_guard_data);
    let agreement_session_url = JObject::from(agreement_session_url);
    let Ok(auth_result) = env.new_object(
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnAuthResult",
        "(ZILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZLjava/lang/String;)V",
        &[
            JValue::Bool(if result.success { JNI_TRUE } else { JNI_FALSE }),
            JValue::Int(result.eresult),
            JValue::Object(&error_message),
            JValue::Object(&account_name),
            JValue::Object(&refresh_token),
            JValue::Object(&access_token),
            JValue::Object(&new_guard_data),
            JValue::Long(result.steamid as jlong),
            JValue::Bool(if result.had_remote_interaction {
                JNI_TRUE
            } else {
                JNI_FALSE
            }),
            JValue::Object(&agreement_session_url),
        ],
    ) else {
        clear_pending_exception(env);
        return;
    };
    let _ = env.call_method(
        callback,
        "onAuthResult",
        "(Lcom/winlator/cmod/feature/stores/steam/wnsteam/WnAuthResult;)V",
        &[JValue::Object(&auth_result)],
    );
    clear_pending_exception(env);
}

fn dispatch_qr_challenge(callback: &GlobalRef, url: &str) {
    let Some(vm) = JVM.get() else {
        return;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return;
    };
    let Ok(url) = env.new_string(url) else {
        clear_pending_exception(&mut env);
        return;
    };
    let url = JObject::from(url);
    let _ = env.call_method(
        callback.as_obj(),
        "onQrChallengeUrl",
        "(Ljava/lang/String;)V",
        &[JValue::Object(&url)],
    );
    clear_pending_exception(&mut env);
}

fn authenticator_accept_device_confirmation(authenticator: &GlobalRef) -> bool {
    let Some(vm) = JVM.get() else {
        return false;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return false;
    };
    let Ok(future) = env.call_method(
        authenticator.as_obj(),
        "acceptDeviceConfirmation",
        "()Ljava/util/concurrent/CompletableFuture;",
        &[],
    ) else {
        clear_pending_exception(&mut env);
        return false;
    };
    let Ok(future) = future.l() else {
        clear_pending_exception(&mut env);
        return false;
    };
    if future.is_null() {
        return false;
    }
    let Ok(result) = env.call_method(&future, "get", "()Ljava/lang/Object;", &[]) else {
        clear_pending_exception(&mut env);
        return false;
    };
    let Ok(result) = result.l() else {
        clear_pending_exception(&mut env);
        return false;
    };
    if result.is_null() {
        return false;
    }
    let Ok(value) = env.call_method(&result, "booleanValue", "()Z", &[]) else {
        clear_pending_exception(&mut env);
        return false;
    };
    value.z().unwrap_or(false)
}

fn future_string_from_method(
    env: &mut JNIEnv,
    target: &JObject,
    method: &str,
    signature: &str,
    args: &[JValue],
) -> Option<String> {
    let future = env
        .call_method(target, method, signature, args)
        .ok()?
        .l()
        .ok()?;
    if future.is_null() {
        return None;
    }
    let result = env
        .call_method(&future, "get", "()Ljava/lang/Object;", &[])
        .ok()?
        .l()
        .ok()?;
    if result.is_null() {
        return None;
    }
    let result = JString::from(result);
    jstring_to_string(env, &result)
}

fn authenticator_get_device_code(
    authenticator: &GlobalRef,
    previous_code_was_incorrect: bool,
) -> Option<String> {
    let vm = JVM.get()?;
    let mut env = vm.attach_current_thread_as_daemon().ok()?;
    let value = future_string_from_method(
        &mut env,
        authenticator.as_obj(),
        "getDeviceCode",
        "(Z)Ljava/util/concurrent/CompletableFuture;",
        &[JValue::Bool(if previous_code_was_incorrect {
            JNI_TRUE
        } else {
            JNI_FALSE
        })],
    );
    clear_pending_exception(&mut env);
    value
}

fn authenticator_get_email_code(
    authenticator: &GlobalRef,
    email: &str,
    previous_code_was_incorrect: bool,
) -> Option<String> {
    let vm = JVM.get()?;
    let mut env = vm.attach_current_thread_as_daemon().ok()?;
    let email = env.new_string(email).ok()?;
    let email_obj = JObject::from(email);
    let value = future_string_from_method(
        &mut env,
        authenticator.as_obj(),
        "getEmailCode",
        "(Ljava/lang/String;Z)Ljava/util/concurrent/CompletableFuture;",
        &[
            JValue::Object(&email_obj),
            JValue::Bool(if previous_code_was_incorrect {
                JNI_TRUE
            } else {
                JNI_FALSE
            }),
        ],
    );
    clear_pending_exception(&mut env);
    value
}

fn submit_non_authed_service_call<F>(
    runtime: &Arc<CMClientRuntime>,
    method: &str,
    body: Vec<u8>,
    callback: F,
) -> bool
where
    F: FnOnce(crate::job_manager::JobResult) + Send + 'static,
{
    let job_id = runtime.next_job_id();
    runtime.track_job(job_id, callback, None);
    let wire = runtime
        .core()
        .build_service_method_call(method, false, job_id, &body);
    if !runtime.core().enqueue_wire(wire) {
        return false;
    }
    runtime.flush_outbound();
    true
}

fn request_authed_service_body<F>(
    runtime: &Arc<CMClientRuntime>,
    timeout: Duration,
    build: F,
) -> Option<Vec<u8>>
where
    F: FnOnce(&CMClientCore, u64) -> Option<OutboundServiceCall>,
{
    if runtime.core().state() != ClientState::LoggedOn {
        return None;
    }
    let job_id = runtime.next_job_id();
    let call = build(runtime.core(), job_id)?;
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let body = (!job.synthetic_failure && job.eresult == 1).then_some(job.body);
            let _ = tx.send(body);
        },
        Some(timeout),
    );
    if !runtime.core().enqueue_wire(call.wire) {
        return None;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).ok().flatten()
}

fn request_authed_service_success<F>(
    runtime: &Arc<CMClientRuntime>,
    timeout: Duration,
    build: F,
) -> bool
where
    F: FnOnce(&CMClientCore, u64) -> Option<OutboundServiceCall>,
{
    if runtime.core().state() != ClientState::LoggedOn {
        return false;
    }
    let job_id = runtime.next_job_id();
    let Some(call) = build(runtime.core(), job_id) else {
        return false;
    };
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let _ = tx.send(!job.synthetic_failure && job.eresult == 1);
        },
        Some(timeout),
    );
    if !runtime.core().enqueue_wire(call.wire) {
        return false;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).unwrap_or(false)
}

fn request_service_method_body(
    runtime: &Arc<CMClientRuntime>,
    method: &str,
    authed: bool,
    body: Vec<u8>,
    timeout: Duration,
) -> Option<Vec<u8>> {
    let job_id = runtime.next_job_id();
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let body = (!job.synthetic_failure && job.eresult == 1).then_some(job.body);
            let _ = tx.send(body);
        },
        Some(timeout),
    );
    let wire = runtime
        .core()
        .build_service_method_call(method, authed, job_id, &body);
    if !runtime.core().enqueue_wire(wire) {
        return None;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).ok().flatten()
}

fn request_user_stats_response(
    runtime: &Arc<CMClientRuntime>,
    app_id: u32,
    timeout: Duration,
) -> Option<crate::pb::cmsg_client_get_user_stats::CMsgClientGetUserStatsResponse> {
    if app_id == 0 || runtime.core().state() != ClientState::LoggedOn {
        return None;
    }
    let job_id = runtime.next_job_id();
    let message = runtime.core().build_job_proto_message(
        EMsg::CLIENT_GET_USER_STATS,
        job_id,
        crate::pb::cmsg_client_get_user_stats::CMsgClientGetUserStats {
            game_id: app_id as u64,
            steam_id_for_user: runtime.core().steam_id(),
        }
        .serialize(),
        0,
    )?;
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let response = if job.synthetic_failure {
                None
            } else {
                crate::pb::cmsg_client_get_user_stats::CMsgClientGetUserStatsResponse::deserialize(
                    &job.body,
                )
            };
            let _ = tx.send(response);
        },
        Some(timeout),
    );
    if !runtime.core().enqueue_wire(message.wire) {
        return None;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).ok().flatten()
}

fn request_proto_body<F>(
    runtime: &Arc<CMClientRuntime>,
    timeout: Duration,
    build: F,
) -> Option<Vec<u8>>
where
    F: FnOnce(&CMClientCore, u64) -> Option<OutboundProtoMessage>,
{
    if runtime.core().state() != ClientState::LoggedOn {
        return None;
    }
    let job_id = runtime.next_job_id();
    let message = build(runtime.core(), job_id)?;
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let body = (!job.synthetic_failure && job.eresult > 0).then_some(job.body);
            let _ = tx.send(body);
        },
        Some(timeout),
    );
    if !runtime.core().enqueue_wire(message.wire) {
        return None;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).ok().flatten()
}

fn request_pics_product_info(
    runtime: &Arc<CMClientRuntime>,
    packages: Vec<crate::pb::cmsg_client_pics::PicsPackageInfoReq>,
    apps: Vec<crate::pb::cmsg_client_pics::PicsAppInfoReq>,
    meta_data_only: bool,
    timeout: Duration,
) -> Option<crate::pb::cmsg_client_pics::CMsgClientPICSProductInfoResponse> {
    let body = request_proto_body(runtime, timeout, |core, job_id| {
        core.build_pics_product_info(packages, apps, meta_data_only, job_id)
    })?;
    crate::pb::cmsg_client_pics::CMsgClientPICSProductInfoResponse::deserialize(&body)
}

fn request_app_ownership_ticket(
    runtime: &Arc<CMClientRuntime>,
    app_id: u32,
    timeout: Duration,
) -> Option<crate::pb::cmsg_client_get_app_ownership_ticket::CMsgClientGetAppOwnershipTicketResponse>
{
    let body = request_proto_body(runtime, timeout, |core, job_id| {
        core.build_get_app_ownership_ticket(app_id, job_id)
    })?;
    let response =
        crate::pb::cmsg_client_get_app_ownership_ticket::CMsgClientGetAppOwnershipTicketResponse::deserialize(&body)?;
    if response.eresult == 1 && !response.ticket.is_empty() {
        runtime
            .core()
            .tickets()
            .store(app_id, response.eresult, response.ticket.clone());
    }
    Some(response)
}

fn request_cdn_servers(
    runtime: &Arc<CMClientRuntime>,
    timeout: Duration,
) -> Option<Vec<crate::pb::ccontentserverdirectory::CContentServerDirectoryServerInfo>> {
    let body = request_authed_service_body(runtime, timeout, |core, job_id| {
        core.build_get_cdn_servers_call(0, job_id)
    })?;
    crate::pb::ccontentserverdirectory::CContentServerDirectoryGetServersForSteamPipeResponse::deserialize(&body)
        .map(|response| response.servers)
}

fn request_manifest_request_code(
    runtime: &Arc<CMClientRuntime>,
    app_id: u32,
    depot_id: u32,
    manifest_id: u64,
    branch: &str,
    timeout: Duration,
) -> Option<u64> {
    let body = request_authed_service_body(runtime, timeout, |core, job_id| {
        core.build_manifest_request_code_call(app_id, depot_id, manifest_id, branch, job_id)
    })?;
    crate::pb::ccontentserverdirectory::CContentServerDirectoryGetManifestRequestCodeResponse::deserialize(&body)
        .map(|response| response.manifest_request_code)
}

fn request_depot_key(
    runtime: &Arc<CMClientRuntime>,
    app_id: u32,
    depot_id: u32,
    timeout: Duration,
) -> Option<Vec<u8>> {
    let body = request_proto_body(runtime, timeout, |core, job_id| {
        core.build_get_depot_decryption_key(depot_id, app_id, job_id)
    })?;
    let response =
        crate::pb::cmsg_client_get_depot_decryption_key::CMsgClientGetDepotDecryptionKeyResponse::deserialize(&body)?;
    (response.eresult == 1 && response.depot_encryption_key.len() == 32)
        .then_some(response.depot_encryption_key)
}

fn request_item_def_digest(
    runtime: &Arc<CMClientRuntime>,
    app_id: u32,
    timeout: Duration,
) -> Option<String> {
    if app_id == 0 || runtime.core().state() != ClientState::LoggedOn {
        return None;
    }
    let job_id = runtime.next_job_id();
    let call = runtime
        .core()
        .build_inventory_item_def_meta_call(app_id, job_id)?;
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let digest = if job.synthetic_failure || job.eresult != 1 {
                None
            } else {
                crate::pb::cinventory::CInventoryGetItemDefMetaResponse::deserialize(&job.body)
                    .and_then(|response| (!response.digest.is_empty()).then_some(response.digest))
            };
            let _ = tx.send(digest);
        },
        Some(timeout),
    );
    if !runtime.core().enqueue_wire(call.wire) {
        return None;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).ok().flatten()
}

fn request_subscribed_workshop_page(
    runtime: &Arc<CMClientRuntime>,
    app_id: u32,
    page: u32,
    page_size: u32,
    timeout: Duration,
) -> Option<crate::pb::cpublishedfile::CPublishedFileGetUserFilesResponse> {
    if app_id == 0 || page == 0 || page_size == 0 || runtime.core().state() != ClientState::LoggedOn
    {
        return None;
    }
    let job_id = runtime.next_job_id();
    let call = runtime
        .core()
        .build_published_file_subscribed_call(app_id, page, page_size, job_id)?;
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let response = if job.synthetic_failure || job.eresult != 1 {
                None
            } else {
                crate::pb::cpublishedfile::CPublishedFileGetUserFilesResponse::deserialize(
                    &job.body,
                )
            };
            let _ = tx.send(response);
        },
        Some(timeout),
    );
    if !runtime.core().enqueue_wire(call.wire) {
        return None;
    }
    runtime.flush_outbound();
    rx.recv_timeout(timeout).ok().flatten()
}

struct QrPollState {
    runtime: Arc<CMClientRuntime>,
    qr_callback: GlobalRef,
    result_callback: GlobalRef,
    cancel: Arc<AtomicBool>,
    client_id: u64,
    request_id: Vec<u8>,
    poll_interval_seconds: f32,
    last_challenge_url: Arc<Mutex<String>>,
}

fn start_qr_poll_loop(state: QrPollState) {
    thread::spawn(move || {
        let interval = Duration::from_secs_f32(state.poll_interval_seconds.max(0.25));
        for slice in sleep_slices(interval, Duration::from_millis(100)) {
            if state.cancel.load(Ordering::Relaxed) {
                return;
            }
            thread::sleep(slice);
        }
        if state.cancel.load(Ordering::Relaxed) {
            return;
        }

        let request = build_poll_request(state.client_id, state.request_id.clone()).serialize();
        let runtime_for_callback = Arc::clone(&state.runtime);
        let qr_for_callback = state.qr_callback.clone();
        let result_for_callback = state.result_callback.clone();
        let cancel_for_callback = Arc::clone(&state.cancel);
        let last_url_for_callback = Arc::clone(&state.last_challenge_url);
        let client_id = state.client_id;
        let request_id = state.request_id.clone();
        let poll_interval_seconds = state.poll_interval_seconds;
        if !submit_non_authed_service_call(
            &state.runtime,
            "Authentication.PollAuthSessionStatus#1",
            request,
            move |job| {
                if cancel_for_callback.load(Ordering::Relaxed) {
                    return;
                }
                if job.synthetic_failure || job.eresult != 1 {
                    dispatch_auth_result(
                        &result_for_callback,
                        job_error(job, "PollAuthSessionStatus failed"),
                    );
                    return;
                }
                let Some(resp) =
                    crate::pb::cauthentication::PollAuthSessionStatusResponse::deserialize(
                        &job.body,
                    )
                else {
                    dispatch_auth_result(
                        &result_for_callback,
                        AuthSessionResult {
                            error_message: "Poll response parse failed".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                };
                let next_client_id = if resp.new_client_id != 0 {
                    resp.new_client_id
                } else {
                    client_id
                };
                if let Some(challenge) = {
                    let mut last = last_url_for_callback
                        .lock()
                        .expect("qr challenge url poisoned");
                    crate::auth_session::take_qr_challenge_update(&mut last, &resp)
                } {
                    dispatch_qr_challenge(&qr_for_callback, &challenge);
                }
                if let Some(result) = auth_result_from_poll(resp, 0) {
                    dispatch_auth_result(&result_for_callback, result);
                    return;
                }
                start_qr_poll_loop(QrPollState {
                    runtime: runtime_for_callback,
                    qr_callback: qr_for_callback,
                    result_callback: result_for_callback,
                    cancel: cancel_for_callback,
                    client_id: next_client_id,
                    request_id,
                    poll_interval_seconds,
                    last_challenge_url: last_url_for_callback,
                });
            },
        ) {
            dispatch_auth_result(
                &state.result_callback,
                AuthSessionResult {
                    error_message: "PollAuthSessionStatus enqueue failed".to_string(),
                    ..Default::default()
                },
            );
        }
    });
}

fn call_prepare_result(env: &mut JNIEnv, callback: &JObject, ok: bool, error: &str) {
    if callback.is_null() {
        return;
    }
    let Ok(error) = env.new_string(error) else {
        clear_pending_exception(env);
        return;
    };
    let error_obj = JObject::from(error);
    let _ = env.call_method(
        callback,
        "onPrepareResult",
        "(ZLjava/lang/String;)V",
        &[
            JValue::Bool(if ok { JNI_TRUE } else { JNI_FALSE }),
            JValue::Object(&error_obj),
        ],
    );
    clear_pending_exception(env);
}

fn call_download_complete_result(
    env: &mut JNIEnv,
    listener: &JObject,
    success: bool,
    error: &str,
    bytes_written: u64,
    depots_completed: u32,
    depots_skipped: u32,
) {
    if listener.is_null() {
        return;
    }
    let Ok(error) = env.new_string(error) else {
        clear_pending_exception(env);
        return;
    };
    let error_obj = JObject::from(error);
    let _ = env.call_method(
        listener,
        "onComplete",
        "(ZLjava/lang/String;JII)V",
        &[
            JValue::Bool(if success { JNI_TRUE } else { JNI_FALSE }),
            JValue::Object(&error_obj),
            JValue::Long(bytes_written as jlong),
            JValue::Int(depots_completed as jint),
            JValue::Int(depots_skipped as jint),
        ],
    );
    clear_pending_exception(env);
}

fn call_download_complete(env: &mut JNIEnv, listener: &JObject, error: &str) {
    call_download_complete_result(env, listener, false, error, 0, 0, 0);
}

fn dispatch_download_complete(
    listener: GlobalRef,
    result: crate::depot_downloader::DepotDownloadResult,
) {
    let Some(vm) = JVM.get() else {
        return;
    };
    let Ok(mut env) = vm.attach_current_thread_as_daemon() else {
        return;
    };
    let listener_obj = listener.as_obj();
    call_download_complete_result(
        &mut env,
        listener_obj,
        result.success,
        &result.error,
        result.bytes_written,
        result.depots_completed,
        result.depots_skipped,
    );
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativePickCmUrl(
    mut env: JNIEnv,
    _class: JClass,
    _ca_bundle_path: JString,
) -> jstring {
    let url = crate::cm_server_list::hardcoded_fallback_servers()
        .into_iter()
        .find_map(|server| {
            let url = server.websocket_url();
            (!url.is_empty()).then_some(url)
        })
        .unwrap_or_default();
    new_string_or_null(&mut env, &url)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    to_session_handle(Box::new(WnSteamSessionHandle::new()))
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { drop_session_handle(handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetCaBundlePath(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    path: JString,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    if let Ok(path) = env.get_string(&path) {
        handle.ca_bundle_path = path.to_string_lossy().into_owned();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetAutoPopulateLibrary(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    enabled: jboolean,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.auto_populate_library = enabled != JNI_FALSE;
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeConnect(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
) -> jboolean {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let Some(url) = jstring_to_string(&mut env, &url) else {
        return JNI_FALSE;
    };
    if url.is_empty() {
        return JNI_FALSE;
    }
    let runtime = handle.runtime();
    if !runtime.connect(&url) {
        return JNI_FALSE;
    }
    handle.set_state(&mut env, ClientState::Connected);
    let hello = handle.core.build_client_hello();
    handle.enqueue_wire(hello.wire);
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDisconnect(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        if let Some(runtime) = handle
            .runtime
            .lock()
            .expect("session runtime poisoned")
            .take()
        {
            runtime.disconnect();
        }
        handle.set_state(&mut env, ClientState::Disconnected);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeLogOffAndDisconnect(
    env: JNIEnv,
    class: JClass,
    handle: jlong,
    _flush_ms: jint,
) {
    if let Some(handle_ref) = unsafe { from_session_handle_mut(handle) } {
        handle_ref.enqueue_proto(handle_ref.core.build_logoff());
    }
    Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDisconnect(
        env, class, handle,
    );
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeIsPlayingBlocked(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    if handle.core.is_playing_blocked() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeMarkPlayingBlocked(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.core.mark_playing_blocked();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeState(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ClientState::Disconnected as jint;
    };
    handle.core.state() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSteamId(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return 0;
    };
    handle.core.steam_id() as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeFamilyGroupId(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return 0;
    };
    handle.core.family_group_id() as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetLibrarySnapshot(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return new_string_or_null(&mut env, "{}");
    };
    new_string_or_null(&mut env, &handle.core.library().snapshot_json())
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStartWineBridge(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    steam3_port: jint,
    client_service_port: jint,
) -> jboolean {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let mut config = WineBridgeConfig::default();
    if steam3_port > 0 {
        config.steam3_port = steam3_port as u16;
    }
    if client_service_port > 0 {
        config.client_svc_port = client_service_port as u16;
    }
    if handle.wine_bridge.start(config) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStopWineBridge(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.wine_bridge.stop();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeWineBridgeLastError(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return new_string_or_null(&mut env, "");
    };
    new_string_or_null(&mut env, &handle.wine_bridge.last_error())
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetAppOwnershipTicket(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
) -> jbyteArray {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(ticket) = handle.core.tickets().get(app_id as u32) else {
        return ptr::null_mut();
    };
    if ticket.eresult != 1 || ticket.ticket.is_empty() {
        return ptr::null_mut();
    }
    byte_array_or_null(&env, &ticket.ticket)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetLicenseList(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let licenses = handle.core.license_list();
    let value = json!(licenses
        .iter()
        .map(|license| json!({
            "package_id": license.package_id,
            "owner_id": license.owner_id,
            "time_created": license.time_created,
            "license_type": license.license_type,
            "flags": license.flags,
            "change_number": license.change_number,
            "minute_limit": license.minute_limit,
            "minutes_used": license.minutes_used,
        }))
        .collect::<Vec<_>>())
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetFriendsList(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlongArray {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let friends = handle
        .core
        .friends_list()
        .into_iter()
        .map(|sid| sid as jlong)
        .collect::<Vec<_>>();
    let Ok(array) = env.new_long_array(friends.len() as i32) else {
        return ptr::null_mut();
    };
    if env.set_long_array_region(&array, 0, &friends).is_err() {
        return ptr::null_mut();
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetFriendPersonas(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return new_string_or_null(&mut env, "[]");
    };
    let value = json!(handle
        .core
        .friend_personas()
        .iter()
        .map(|persona| json!({
            "sid": persona.sid.to_string(),
            "player_name": persona.player_name,
            "persona_state": persona.persona_state,
            "game_played_app_id": persona.game_played_app_id,
            "avatar_hash": crate::cdn_client::hex_encode(&persona.avatar_hash),
        }))
        .collect::<Vec<_>>())
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetSelfPersona(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(persona) = handle.core.self_persona() else {
        return ptr::null_mut();
    };
    let value = json!({
        "sid": persona.friendid.to_string(),
        "player_name": persona.player_name,
        "persona_state": persona.persona_state,
        "game_played_app_id": persona.game_played_app_id,
        "avatar_hash": crate::cdn_client::hex_encode(&persona.avatar_hash),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetPersonaState(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    persona_state: jint,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.enqueue_proto(handle.core.build_set_persona_state(persona_state as u32));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetPersonaName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    name: JString,
    persona_state: jint,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let Ok(name) = env.get_string(&name) else {
        return;
    };
    let name = name.to_string_lossy().into_owned();
    handle.enqueue_proto(
        handle
            .core
            .build_set_persona_name(name, persona_state as u32),
    );
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeRequestUserPersona(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.enqueue_proto(handle.core.build_request_user_persona());
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeRequestFriendPersonas(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    steam_ids: JLongArray,
    flags: jint,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let len = env.get_array_length(&steam_ids).unwrap_or(0);
    if len <= 0 {
        return;
    }
    let mut ids = vec![0i64; len as usize];
    if env.get_long_array_region(&steam_ids, 0, &mut ids).is_err() {
        return;
    }
    let ids = ids.into_iter().map(|id| id as u64).collect::<Vec<_>>();
    handle.enqueue_proto(
        handle
            .core
            .build_request_friend_personas(&ids, flags as u32),
    );
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeNotifyGamesPlayed(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    games_json: JString,
    _client_os_type: jint,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let Ok(games_json) = env.get_string(&games_json) else {
        return;
    };
    let app_id = serde_json::from_str::<serde_json::Value>(&games_json.to_string_lossy())
        .ok()
        .and_then(|value| value.as_array().and_then(|arr| arr.first().cloned()))
        .and_then(|first| first.get("app_id").or_else(|| first.get("appid")).cloned())
        .and_then(|value| value.as_u64())
        .unwrap_or(0) as u32;
    handle.enqueue_proto(handle.core.build_notify_games_played(app_id));
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeKickPlayingSession(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    only_stop_game: jboolean,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.enqueue_proto(
            handle
                .core
                .build_kick_playing_session(only_stop_game != JNI_FALSE),
        );
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStoreUserStats(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    steam_id: jlong,
    crc_stats: jint,
    stat_ids: JIntArray,
    stat_values: JIntArray,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let len = env
        .get_array_length(&stat_ids)
        .unwrap_or(0)
        .min(env.get_array_length(&stat_values).unwrap_or(0));
    if len < 0 {
        return;
    }
    let mut ids = vec![0i32; len as usize];
    let mut values = vec![0i32; len as usize];
    if env.get_int_array_region(&stat_ids, 0, &mut ids).is_err()
        || env
            .get_int_array_region(&stat_values, 0, &mut values)
            .is_err()
    {
        return;
    }
    let stats = ids
        .into_iter()
        .zip(values)
        .map(|(id, value)| (id as u32, value as u32))
        .collect::<Vec<_>>();
    handle.enqueue_proto(handle.core.build_store_user_stats(
        app_id as u32,
        steam_id as u64,
        crc_stats as u32,
        &stats,
    ));
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetStateObserver(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    observer: JObject,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let mut slot = handle
        .state_observer
        .lock()
        .expect("session state observer poisoned");
    *slot = if observer.is_null() {
        None
    } else {
        env.new_global_ref(observer).ok()
    };
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetLibraryObserver(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    observer: JObject,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let mut slot = handle
        .library_observer
        .lock()
        .expect("session library observer poisoned");
    *slot = if observer.is_null() {
        None
    } else {
        env.new_global_ref(observer).ok()
    };
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeRenewRefreshToken(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    current_token: JString,
    steam_id64: jlong,
    timeout_ms: jint,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    if handle.core.state() != ClientState::LoggedOn {
        return ptr::null_mut();
    }
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(current_token) = jstring_to_string(&mut env, &current_token) else {
        return ptr::null_mut();
    };
    if current_token.is_empty() || steam_id64 == 0 {
        return ptr::null_mut();
    }

    let request = crate::pb::cauthentication::AccessTokenGenerateForAppRequest {
        refresh_token: current_token,
        steamid: steam_id64 as u64,
        renewal_type: crate::pb::cauthentication::EAuthTokenRenewalType::Allow,
    }
    .serialize();
    let job_id = runtime.next_job_id();
    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let token = if !job.synthetic_failure && job.eresult == 1 {
                crate::pb::cauthentication::AccessTokenGenerateForAppResponse::deserialize(
                    &job.body,
                )
                .map(|resp| resp.refresh_token)
                .unwrap_or_default()
            } else {
                String::new()
            };
            let _ = tx.send(token);
        },
        Some(Duration::from_millis(timeout_ms.max(1) as u64)),
    );
    let wire = runtime.core().build_service_method_call(
        "Authentication.GenerateAccessTokenForApp#1",
        true,
        job_id,
        &request,
    );
    if !runtime.core().enqueue_wire(wire) {
        return ptr::null_mut();
    }
    runtime.flush_outbound();

    let wait = Duration::from_millis(timeout_ms.max(0) as u64);
    let Ok(token) = rx.recv_timeout(wait) else {
        return ptr::null_mut();
    };
    if token.is_empty() {
        return ptr::null_mut();
    }
    new_string_or_null(&mut env, &token)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStartLoginWithCredentials(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    username: JString,
    password: JString,
    persistent_session: jboolean,
    authenticator: JObject,
    callback: JObject,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        call_auth_result_failure(&mut env, &callback, 2, "session closed");
        return;
    };
    let Some(runtime) = handle.connected_runtime() else {
        call_auth_result_failure(&mut env, &callback, 2, "not connected");
        return;
    };
    let Some(username) = jstring_to_string(&mut env, &username) else {
        call_auth_result_failure(&mut env, &callback, 2, "missing username");
        return;
    };
    let Some(password) = jstring_to_string(&mut env, &password) else {
        call_auth_result_failure(&mut env, &callback, 2, "missing password");
        return;
    };
    let Ok(callback) = env.new_global_ref(callback) else {
        clear_pending_exception(&mut env);
        return;
    };
    let authenticator = if authenticator.is_null() {
        None
    } else {
        env.new_global_ref(authenticator).ok()
    };
    let cancel = handle.begin_login_cancel();
    thread::spawn(move || {
        let timeout = Duration::from_secs(30);
        let mut config = CredentialsAuthConfig {
            username,
            password,
            persistent_session: persistent_session != JNI_FALSE,
            ..Default::default()
        };
        let Some(key_body) = request_service_method_body(
            &runtime,
            "Authentication.GetPasswordRSAPublicKey#1",
            false,
            build_password_rsa_request(&config).serialize(),
            timeout,
        ) else {
            dispatch_auth_result(
                &callback,
                AuthSessionResult {
                    error_message: "GetPasswordRSAPublicKey failed".to_string(),
                    ..Default::default()
                },
            );
            return;
        };
        let Some(key) =
            crate::pb::cauthentication::GetPasswordRsaPublicKeyResponse::deserialize(&key_body)
        else {
            dispatch_auth_result(
                &callback,
                AuthSessionResult {
                    error_message: "GetPasswordRSAPublicKey parse failed".to_string(),
                    ..Default::default()
                },
            );
            return;
        };
        let begin_request = match build_credentials_begin_request(&mut config, &key) {
            Ok(request) => request,
            Err(result) => {
                dispatch_auth_result(&callback, result);
                return;
            }
        };
        let Some(begin_body) = request_service_method_body(
            &runtime,
            "Authentication.BeginAuthSessionViaCredentials#1",
            false,
            begin_request.serialize(),
            timeout,
        ) else {
            dispatch_auth_result(
                &callback,
                AuthSessionResult {
                    error_message: "BeginAuthSessionViaCredentials failed".to_string(),
                    ..Default::default()
                },
            );
            return;
        };
        let Some(begin_response) =
            crate::pb::cauthentication::BeginAuthSessionViaCredentialsResponse::deserialize(
                &begin_body,
            )
        else {
            dispatch_auth_result(
                &callback,
                AuthSessionResult {
                    error_message: "BeginAuthSessionViaCredentials parse failed".to_string(),
                    ..Default::default()
                },
            );
            return;
        };
        let pending = match pending_credentials_from_begin_response(begin_response) {
            Ok(pending) => pending,
            Err(result) => {
                dispatch_auth_result(&callback, result);
                return;
            }
        };
        let chosen_guard = choose_guard_confirmation(&pending.allowed_confirmations);
        match chosen_guard {
            crate::pb::cauthentication::EAuthSessionGuardType::DeviceConfirmation => {
                if let Some(authenticator) = authenticator.as_ref() {
                    if !authenticator_accept_device_confirmation(authenticator) {
                        dispatch_auth_result(
                            &callback,
                            AuthSessionResult {
                                error_message: "device confirmation was not accepted".to_string(),
                                ..Default::default()
                            },
                        );
                        return;
                    }
                }
            }
            crate::pb::cauthentication::EAuthSessionGuardType::DeviceCode => {
                let Some(authenticator) = authenticator.as_ref() else {
                    dispatch_auth_result(
                        &callback,
                        AuthSessionResult {
                            error_message: "Steam Guard device code required".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                };
                let Some(code) = authenticator_get_device_code(authenticator, false) else {
                    dispatch_auth_result(
                        &callback,
                        AuthSessionResult {
                            error_message: "Steam Guard device code was not provided".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                };
                let update = build_guard_code_request(
                    pending.client_id,
                    pending.steamid,
                    chosen_guard,
                    code,
                );
                if request_service_method_body(
                    &runtime,
                    "Authentication.UpdateAuthSessionWithSteamGuardCode#1",
                    false,
                    update.serialize(),
                    timeout,
                )
                .is_none()
                {
                    dispatch_auth_result(
                        &callback,
                        AuthSessionResult {
                            error_message: "UpdateAuthSessionWithSteamGuardCode failed".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                }
            }
            crate::pb::cauthentication::EAuthSessionGuardType::EmailCode => {
                let Some(authenticator) = authenticator.as_ref() else {
                    dispatch_auth_result(
                        &callback,
                        AuthSessionResult {
                            error_message: "Steam Guard email code required".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                };
                let email = pending
                    .allowed_confirmations
                    .iter()
                    .find(|confirmation| confirmation.confirmation_type == chosen_guard)
                    .map(|confirmation| confirmation.associated_message.as_str())
                    .unwrap_or_default();
                let Some(code) = authenticator_get_email_code(authenticator, email, false) else {
                    dispatch_auth_result(
                        &callback,
                        AuthSessionResult {
                            error_message: "Steam Guard email code was not provided".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                };
                let update = build_guard_code_request(
                    pending.client_id,
                    pending.steamid,
                    chosen_guard,
                    code,
                );
                if request_service_method_body(
                    &runtime,
                    "Authentication.UpdateAuthSessionWithSteamGuardCode#1",
                    false,
                    update.serialize(),
                    timeout,
                )
                .is_none()
                {
                    dispatch_auth_result(
                        &callback,
                        AuthSessionResult {
                            error_message: "UpdateAuthSessionWithSteamGuardCode failed".to_string(),
                            ..Default::default()
                        },
                    );
                    return;
                }
            }
            _ => {}
        }

        let poll_interval = Duration::from_secs_f32(pending.poll_interval_seconds.max(0.25));
        loop {
            for slice in sleep_slices(poll_interval, Duration::from_millis(100)) {
                if cancel.load(Ordering::Relaxed) {
                    return;
                }
                thread::sleep(slice);
            }
            if cancel.load(Ordering::Relaxed) {
                return;
            }
            let Some(poll_body) = request_service_method_body(
                &runtime,
                "Authentication.PollAuthSessionStatus#1",
                false,
                build_poll_request(pending.client_id, pending.request_id.clone()).serialize(),
                timeout,
            ) else {
                continue;
            };
            let Some(resp) =
                crate::pb::cauthentication::PollAuthSessionStatusResponse::deserialize(&poll_body)
            else {
                continue;
            };
            if let Some(result) = auth_result_from_poll(resp, pending.steamid) {
                dispatch_auth_result(&callback, result);
                return;
            }
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStartLoginWithQr(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    qr_callback: JObject,
    result_callback: JObject,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        call_auth_result_failure(&mut env, &result_callback, 2, "session closed");
        return;
    };
    let Some(runtime) = handle.connected_runtime() else {
        call_auth_result_failure(
            &mut env,
            &result_callback,
            2,
            "Rust QR auth transport is not connected",
        );
        return;
    };
    let Ok(qr_callback) = env.new_global_ref(qr_callback) else {
        clear_pending_exception(&mut env);
        call_auth_result_failure(&mut env, &result_callback, 2, "QR callback unavailable");
        return;
    };
    let Ok(result_callback) = env.new_global_ref(result_callback) else {
        clear_pending_exception(&mut env);
        return;
    };
    let cancel = handle.begin_login_cancel();
    let request = build_qr_begin_request(&QrAuthConfig::default()).serialize();
    let runtime_for_callback = Arc::clone(&runtime);
    let qr_for_callback = qr_callback.clone();
    let result_for_callback = result_callback.clone();
    let cancel_for_callback = Arc::clone(&cancel);
    if !submit_non_authed_service_call(
        &runtime,
        "Authentication.BeginAuthSessionViaQR#1",
        request,
        move |job| {
            if cancel_for_callback.load(Ordering::Relaxed) {
                return;
            }
            if job.synthetic_failure || job.eresult != 1 {
                dispatch_auth_result(
                    &result_for_callback,
                    job_error(job, "BeginAuthSessionViaQR failed"),
                );
                return;
            }
            let Some(resp) =
                crate::pb::cauthentication::BeginAuthSessionViaQrResponse::deserialize(&job.body)
            else {
                dispatch_auth_result(
                    &result_for_callback,
                    AuthSessionResult {
                        error_message: "QR response parse failed".to_string(),
                        ..Default::default()
                    },
                );
                return;
            };
            let pending = pending_qr_from_begin_response(resp);
            if pending.client_id == 0 || pending.request_id.is_empty() {
                dispatch_auth_result(
                    &result_for_callback,
                    AuthSessionResult {
                        eresult: 5,
                        error_message: "Steam rejected the QR auth session".to_string(),
                        ..Default::default()
                    },
                );
                return;
            }
            dispatch_qr_challenge(&qr_for_callback, &pending.challenge_url);
            start_qr_poll_loop(QrPollState {
                runtime: runtime_for_callback,
                qr_callback: qr_for_callback,
                result_callback: result_for_callback,
                cancel: cancel_for_callback,
                client_id: pending.client_id,
                request_id: pending.request_id,
                poll_interval_seconds: pending.poll_interval_seconds,
                last_challenge_url: Arc::new(Mutex::new(pending.challenge_url)),
            });
        },
    ) {
        dispatch_auth_result(
            &result_callback,
            AuthSessionResult {
                error_message: "BeginAuthSessionViaQR enqueue failed".to_string(),
                ..Default::default()
            },
        );
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCancelLogin(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.cancel_login();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeLogonWithRefreshToken(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    refresh_token: JString,
    account_name: JString,
    steam_id: jlong,
) -> jboolean {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let Some(refresh_token) = jstring_to_string(&mut env, &refresh_token) else {
        return JNI_FALSE;
    };
    let account_name = jstring_to_string(&mut env, &account_name).unwrap_or_default();
    if refresh_token.is_empty() || account_name.is_empty() {
        return JNI_FALSE;
    }
    if handle.enqueue_proto(handle.core.build_logon_with_refresh_token(
        refresh_token,
        account_name,
        steam_id as u64,
    )) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativePrepareApp(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    dlc_app_ids: JIntArray,
    callback: JObject,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        call_prepare_result(&mut env, &callback, false, "session closed");
        return;
    };
    if callback.is_null() {
        return;
    }
    let Some(runtime) = handle.connected_runtime() else {
        call_prepare_result(&mut env, &callback, false, "not connected");
        return;
    };
    let dlc_app_ids = int_array_to_u32_vec(&env, &dlc_app_ids);
    let all_ids = CMClientCore::prepare_app_ids(app_id as u32, &dlc_app_ids);
    if all_ids.is_empty() {
        call_prepare_result(&mut env, &callback, true, "");
        return;
    }

    let missing_tokens = handle.core.prepare_app_missing_token_ids(&all_ids);
    if !missing_tokens.is_empty() {
        if let Some(body) = request_proto_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_pics_access_tokens(Vec::new(), missing_tokens, job_id)
        }) {
            if let Some(response) =
                crate::pb::cmsg_client_pics::CMsgClientPICSAccessTokenResponse::deserialize(&body)
            {
                handle.core.library().ingest_app_access_tokens(&response);
            }
        }
    }

    let apps = handle.core.prepare_app_pics_requests(&all_ids);
    let Some(response) =
        request_pics_product_info(&runtime, Vec::new(), apps, false, Duration::from_secs(30))
    else {
        call_prepare_result(&mut env, &callback, false, "PICS product info failed");
        return;
    };
    handle.core.library().ingest_app_pics_response(&response);

    for app_id in all_ids {
        let _ = request_app_ownership_ticket(&runtime, app_id, Duration::from_secs(30));
    }
    call_prepare_result(&mut env, &callback, true, "");
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDownloadApp(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    depot_ids: JIntArray,
    manifest_ids: JLongArray,
    branch: JString,
    install_dir: JString,
    fresh: jboolean,
    ca_bundle_path: JString,
    max_workers: jint,
    listener: JObject,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        call_download_complete(&mut env, &listener, "session closed");
        return;
    };
    if listener.is_null() {
        return;
    }
    let Some(runtime) = handle.connected_runtime() else {
        call_download_complete(&mut env, &listener, "not connected");
        return;
    };
    let depot_ids = int_array_to_u32_vec(&env, &depot_ids);
    let manifest_ids = long_array_to_u64_vec(&env, &manifest_ids);
    if depot_ids.len() != manifest_ids.len() {
        call_download_complete(&mut env, &listener, "depot/manifest array length mismatch");
        return;
    }
    let specs = depot_ids
        .into_iter()
        .zip(manifest_ids)
        .map(
            |(depot_id, manifest_id)| crate::depot_downloader::DepotSpec {
                depot_id,
                manifest_id,
            },
        )
        .collect::<Vec<_>>();
    let install_dir = jstring_to_string(&mut env, &install_dir).unwrap_or_default();
    if crate::depot_downloader::validate_download_inputs(&install_dir, &specs).is_err() {
        call_download_complete(&mut env, &listener, "invalid download request");
        return;
    }
    let branch = jstring_to_string(&mut env, &branch).unwrap_or_else(|| "public".to_string());
    let ca_bundle_path = jstring_to_string(&mut env, &ca_bundle_path).unwrap_or_default();
    let Ok(listener) = env.new_global_ref(&listener) else {
        call_download_complete(&mut env, &listener, "listener ref failed");
        return;
    };
    let app_id = app_id.max(0) as u32;
    let fresh = fresh != JNI_FALSE;
    let max_workers = max_workers.max(1) as u32;
    handle.download_cancel.store(false, Ordering::Relaxed);
    let download_cancel = Arc::clone(&handle.download_cancel);

    thread::spawn(move || {
        let timeout = Duration::from_secs(30);
        if download_cancel.load(Ordering::Relaxed) {
            dispatch_download_complete(
                listener,
                crate::depot_downloader::DepotDownloadResult::fail("cancelled"),
            );
            return;
        }
        let Some(servers) = request_cdn_servers(&runtime, timeout) else {
            dispatch_download_complete(
                listener.clone(),
                crate::depot_downloader::DepotDownloadResult::fail(
                    "download: CDN server request failed",
                ),
            );
            return;
        };
        let mut resolved = Vec::with_capacity(specs.len());
        for spec in specs {
            if download_cancel.load(Ordering::Relaxed) {
                dispatch_download_complete(
                    listener,
                    crate::depot_downloader::DepotDownloadResult::fail("cancelled"),
                );
                return;
            }
            let Some(depot_key) = request_depot_key(&runtime, app_id, spec.depot_id, timeout)
            else {
                dispatch_download_complete(
                    listener.clone(),
                    crate::depot_downloader::DepotDownloadResult::fail(format!(
                        "download: depot key unavailable for depot {}",
                        spec.depot_id
                    )),
                );
                return;
            };
            if download_cancel.load(Ordering::Relaxed) {
                dispatch_download_complete(
                    listener,
                    crate::depot_downloader::DepotDownloadResult::fail("cancelled"),
                );
                return;
            }
            let Some(manifest_request_code) = request_manifest_request_code(
                &runtime,
                app_id,
                spec.depot_id,
                spec.manifest_id,
                &branch,
                timeout,
            ) else {
                dispatch_download_complete(
                    listener.clone(),
                    crate::depot_downloader::DepotDownloadResult::fail(format!(
                        "download: manifest request code unavailable for depot {}",
                        spec.depot_id
                    )),
                );
                return;
            };
            resolved.push(crate::depot_downloader::ResolvedDepotSpec {
                depot_id: spec.depot_id,
                manifest_id: spec.manifest_id,
                depot_key,
                manifest_request_code,
            });
        }
        let result = crate::depot_downloader::download_resolved_depots_with_cancel(
            &install_dir,
            &resolved,
            &servers,
            &ca_bundle_path,
            fresh,
            max_workers,
            Some(download_cancel.as_ref()),
        );
        dispatch_download_complete(listener, result);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCancelDownload(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(handle) = unsafe { from_session_handle_mut(handle) } {
        handle.download_cancel.store(true, Ordering::Relaxed);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeRequestEncryptedAppTicket(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
) -> jbyteArray {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    if app_id <= 0 || handle.core.state() != ClientState::LoggedOn {
        return ptr::null_mut();
    }
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };

    let app_id = app_id as u32;
    let job_id = runtime.next_job_id();
    let Some(message) = runtime.core().build_job_proto_message(
        EMsg::CLIENT_REQUEST_ENCRYPTED_APP_TICKET,
        job_id,
        crate::pb::cmsg_client_request_encrypted_app_ticket::CMsgClientRequestEncryptedAppTicket {
            app_id,
        }
        .serialize(),
        app_id,
    ) else {
        return ptr::null_mut();
    };

    let (tx, rx) = mpsc::channel();
    runtime.track_job(
        job_id,
        move |job| {
            let ticket = if job.synthetic_failure {
                Vec::new()
            } else {
                crate::pb::cmsg_client_request_encrypted_app_ticket::CMsgClientRequestEncryptedAppTicketResponse::deserialize(&job.body)
                    .map(|response| response.encrypted_app_ticket)
                    .unwrap_or_default()
            };
            let _ = tx.send(ticket);
        },
        Some(Duration::from_secs(30)),
    );
    if !runtime.core().enqueue_wire(message.wire) {
        return ptr::null_mut();
    }
    runtime.flush_outbound();

    let Ok(ticket) = rx.recv_timeout(Duration::from_secs(30)) else {
        return ptr::null_mut();
    };
    if ticket.is_empty() {
        return ptr::null_mut();
    }
    byte_array_or_null(&env, &ticket)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetUserStatsSchema(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
) -> jbyteArray {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(response) =
        request_user_stats_response(&runtime, app_id.max(0) as u32, Duration::from_secs(30))
    else {
        return ptr::null_mut();
    };
    if response.schema.is_empty() {
        return ptr::null_mut();
    }
    byte_array_or_null(&env, &response.schema)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetUserStatsFull(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(response) =
        request_user_stats_response(&runtime, app_id.max(0) as u32, Duration::from_secs(30))
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "eresult": response.eresult,
        "crcStats": response.crc_stats,
        "schema": crate::cdn_client::hex_encode(&response.schema),
        "achievementBlocks": response.achievement_blocks.iter().map(|block| json!({
            "achievementId": block.achievement_id,
            "unlockTimes": block.unlock_time,
        })).collect::<Vec<_>>(),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetItemDefArchive(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    ca_bundle_path: JString,
) -> jbyteArray {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    let Some(digest) = request_item_def_digest(&runtime, app_id, Duration::from_secs(30)) else {
        return ptr::null_mut();
    };
    let ca_bundle_path = jstring_to_string(&mut env, &ca_bundle_path).unwrap_or_default();
    let Some(body) = crate::cdn_client::CdnClient::new(ca_bundle_path).fetch_item_def_archive(
        app_id,
        &digest,
        Duration::from_secs(30),
    ) else {
        return ptr::null_mut();
    };
    byte_array_or_null(&env, &body)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetSubscribedWorkshopItems(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    const PAGE_SIZE: u32 = 100;
    const MAX_PAGES: u32 = 50;
    let mut total = 0u32;
    let mut all = Vec::new();
    for page in 1..=MAX_PAGES {
        let Some(response) = request_subscribed_workshop_page(
            &runtime,
            app_id,
            page,
            PAGE_SIZE,
            Duration::from_secs(30),
        ) else {
            return ptr::null_mut();
        };
        if page == 1 {
            total = response.total;
        }
        let count = response.publishedfiledetails.len();
        all.extend(response.publishedfiledetails);
        if count == 0 || (total != 0 && all.len() >= total as usize) {
            break;
        }
    }
    let value = json!(all
        .iter()
        .map(|detail| json!({
            "publishedFileId": detail.publishedfileid,
            "appId": if detail.consumer_appid != 0 { detail.consumer_appid } else { app_id },
            "title": detail.title,
            "fileName": detail.filename,
            "fileUrl": detail.file_url,
            "previewUrl": detail.preview_url,
            "fileSizeBytes": detail.file_size,
            "hcontentFile": detail.hcontent_file,
            "timeUpdated": detail.time_updated,
        }))
        .collect::<Vec<_>>())
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDownloadWorkshopItem(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    manifest_id: jlong,
    install_dir: JString,
    ca_bundle_path: JString,
    max_workers: jint,
) -> jlong {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return -1;
    };
    let Some(runtime) = handle.connected_runtime() else {
        return -1;
    };
    let install_dir = jstring_to_string(&mut env, &install_dir).unwrap_or_default();
    if install_dir.is_empty() || app_id <= 0 || manifest_id <= 0 {
        return -1;
    }
    let ca_bundle_path = jstring_to_string(&mut env, &ca_bundle_path).unwrap_or_default();
    let app_id = app_id as u32;
    let manifest_id = manifest_id as u64;
    let timeout = Duration::from_secs(30);
    let Some(servers) = request_cdn_servers(&runtime, timeout) else {
        return -1;
    };
    let Some(depot_key) = request_depot_key(&runtime, app_id, app_id, timeout) else {
        return -1;
    };
    let Some(manifest_request_code) =
        request_manifest_request_code(&runtime, app_id, app_id, manifest_id, "public", timeout)
    else {
        return -1;
    };
    let spec = crate::depot_downloader::ResolvedDepotSpec {
        depot_id: app_id,
        manifest_id,
        depot_key,
        manifest_request_code,
    };
    let result = crate::depot_downloader::download_resolved_depots(
        &install_dir,
        &[spec],
        &servers,
        &ca_bundle_path,
        true,
        max_workers.max(1) as u32,
    );
    if result.success {
        result.bytes_written as jlong
    } else {
        -1
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetCloudFileList(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_app_file_changelist_call(app_id, 0, job_id)
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::ccloud::CCloudGetAppFileChangelistResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "currentChangeNumber": response.current_change_number,
        "pathPrefixes": response.path_prefixes,
        "machineNames": response.machine_names,
        "files": response.files.iter().map(|file| json!({
            "fileName": file.file_name,
            "sha": crate::cdn_client::hex_encode(&file.sha_file),
            "timestamp": file.time_stamp,
            "size": file.raw_file_size,
            "persistState": file.persist_state,
            "pathPrefixIndex": file.path_prefix_index,
            "machineNameIndex": file.machine_name_index,
        })).collect::<Vec<_>>(),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetCloudUserQuota(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlongArray {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_user_quota_call(job_id)
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::ccloud::CCloudGetUserQuotaResponse::deserialize(&body) else {
        return ptr::null_mut();
    };
    let Ok(array) = env.new_long_array(2) else {
        return ptr::null_mut();
    };
    let values = [response.total_bytes as jlong, response.used_bytes as jlong];
    if env.set_long_array_region(&array, 0, &values).is_err() {
        return ptr::null_mut();
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetCloudDownloadInfo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    filename: JString,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    let Some(filename) = jstring_to_string(&mut env, &filename) else {
        return ptr::null_mut();
    };
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_file_download_info_call(app_id, filename, job_id)
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::ccloud::CCloudClientFileDownloadResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    if response.url_host.is_empty() {
        return ptr::null_mut();
    }
    let value = json!({
        "fileSize": response.file_size,
        "rawFileSize": response.raw_file_size,
        "sha": crate::cdn_client::hex_encode(&response.sha_file),
        "timestamp": response.time_stamp,
        "urlHost": response.url_host,
        "urlPath": response.url_path,
        "useHttps": response.use_https,
        "encrypted": response.encrypted,
        "headers": response.request_headers.iter().map(|header| json!({
            "name": header.name,
            "value": header.value,
        })).collect::<Vec<_>>(),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudBeginUploadBatch(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    files: JString,
    files_to_delete: JString,
    client_id: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    let files = split_nonempty_lines(&jstring_to_string(&mut env, &files).unwrap_or_default());
    let files_to_delete =
        split_nonempty_lines(&jstring_to_string(&mut env, &files_to_delete).unwrap_or_default());
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_begin_app_upload_batch_call(
                app_id,
                String::new(),
                files,
                files_to_delete,
                client_id as u64,
                job_id,
            )
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::ccloud::CCloudBeginAppUploadBatchResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "batchId": response.batch_id,
        "appChangeNumber": response.app_change_number,
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudBeginFileUpload(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    filename: JString,
    file_size: jint,
    raw_file_size: jint,
    sha_hex: JString,
    timestamp: jlong,
    batch_id: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(filename) = jstring_to_string(&mut env, &filename) else {
        return ptr::null_mut();
    };
    let Some(sha_hex) = jstring_to_string(&mut env, &sha_hex) else {
        return ptr::null_mut();
    };
    let Some(file_sha) = decode_hex(&sha_hex) else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_begin_file_upload_call(
                app_id,
                filename,
                file_size as u32,
                raw_file_size as u32,
                file_sha,
                timestamp as u64,
                batch_id as u64,
                job_id,
            )
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::ccloud::CCloudClientBeginFileUploadResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "encryptFile": response.encrypt_file,
        "blocks": response.block_requests.iter().map(|block| json!({
            "urlHost": block.url_host,
            "urlPath": block.url_path,
            "useHttps": block.use_https,
            "httpMethod": block.http_method,
            "blockOffset": block.block_offset,
            "blockLength": block.block_length,
            "mayParallelize": block.may_parallelize,
            "headers": block.request_headers.iter().map(|header| json!({
                "name": header.name,
                "value": header.value,
            })).collect::<Vec<_>>(),
        })).collect::<Vec<_>>(),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudCommitFileUpload(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    transfer_succeeded: jboolean,
    app_id: jint,
    sha_hex: JString,
    filename: JString,
) -> jboolean {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let Some(runtime) = handle.connected_runtime() else {
        return JNI_FALSE;
    };
    let Some(sha_hex) = jstring_to_string(&mut env, &sha_hex) else {
        return JNI_FALSE;
    };
    let Some(filename) = jstring_to_string(&mut env, &filename) else {
        return JNI_FALSE;
    };
    let Some(file_sha) = decode_hex(&sha_hex) else {
        return JNI_FALSE;
    };
    let app_id = app_id.max(0) as u32;
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_commit_file_upload_call(
                transfer_succeeded != JNI_FALSE,
                app_id,
                file_sha,
                filename,
                job_id,
            )
        })
    else {
        return JNI_FALSE;
    };
    if crate::pb::ccloud::CCloudClientCommitFileUploadResponse::deserialize(&body)
        .map(|response| response.file_committed)
        .unwrap_or(false)
    {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudCompleteUploadBatch(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    batch_id: jlong,
    batch_eresult: jint,
) -> jboolean {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return JNI_FALSE;
    };
    let Some(runtime) = handle.connected_runtime() else {
        return JNI_FALSE;
    };
    if request_authed_service_success(&runtime, Duration::from_secs(30), |core, job_id| {
        core.build_cloud_complete_app_upload_batch_call(
            app_id.max(0) as u32,
            batch_id as u64,
            batch_eresult as u32,
            job_id,
        )
    }) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsChangesSince(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    since_change_number: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(body) = request_proto_body(&runtime, Duration::from_secs(30), |core, job_id| {
        core.build_pics_changes_since(since_change_number as u32, job_id)
    }) else {
        return ptr::null_mut();
    };
    let Some(response) =
        crate::pb::cmsg_client_pics::CMsgClientPICSChangesSinceResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "currentChangeNumber": response.current_change_number,
        "forceFullUpdate": response.force_full_update,
        "apps": response.app_changes.iter().map(|app| json!({
            "appid": app.appid,
            "changeNumber": app.change_number,
            "needsToken": app.needs_token,
        })).collect::<Vec<_>>(),
        "packages": response.package_changes.iter().map(|package| json!({
            "packageid": package.packageid,
            "changeNumber": package.change_number,
            "needsToken": package.needs_token,
        })).collect::<Vec<_>>(),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsAppInfo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    access_token: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_id = app_id.max(0) as u32;
    let app = crate::pb::cmsg_client_pics::PicsAppInfoReq {
        appid: app_id,
        access_token: access_token as u64,
        only_public_obsolete: false,
    };
    let Some(response) = request_pics_product_info(
        &runtime,
        Vec::new(),
        vec![app],
        false,
        Duration::from_secs(30),
    ) else {
        return ptr::null_mut();
    };
    for app in response.apps {
        if app.appid != app_id || app.buffer.is_empty() {
            continue;
        }
        let Some(root) = crate::vdf::parse_auto(&app.buffer) else {
            return ptr::null_mut();
        };
        let appinfo = if root.name.eq_ignore_ascii_case("appinfo") {
            &root
        } else {
            root.child("appinfo").unwrap_or(&root)
        };
        let value = json!({
            "changeNumber": app.change_number,
            "appinfo": kvnode_to_json_value(appinfo),
        })
        .to_string();
        return new_string_or_null(&mut env, &value);
    }
    ptr::null_mut()
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsAccessTokens(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_ids: JString,
    package_ids: JString,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_ids = parse_u32_lines(&jstring_to_string(&mut env, &app_ids).unwrap_or_default());
    let package_ids =
        parse_u32_lines(&jstring_to_string(&mut env, &package_ids).unwrap_or_default());
    let Some(body) = request_proto_body(&runtime, Duration::from_secs(30), |core, job_id| {
        core.build_pics_access_tokens(package_ids, app_ids, job_id)
    }) else {
        return ptr::null_mut();
    };
    let Some(response) =
        crate::pb::cmsg_client_pics::CMsgClientPICSAccessTokenResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let app_tokens = response
        .app_access_tokens
        .iter()
        .map(|token| {
            (
                token.appid.to_string(),
                json!(token.access_token.to_string()),
            )
        })
        .collect::<serde_json::Map<_, _>>();
    let package_tokens = response
        .package_access_tokens
        .iter()
        .map(|token| {
            (
                token.packageid.to_string(),
                json!(token.access_token.to_string()),
            )
        })
        .collect::<serde_json::Map<_, _>>();
    let value = json!({
        "appTokens": app_tokens,
        "packageTokens": package_tokens,
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsAppProductInfo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_ids: JString,
    tokens: JString,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let app_ids = parse_u32_lines(&jstring_to_string(&mut env, &app_ids).unwrap_or_default());
    let tokens = parse_u64_lines(&jstring_to_string(&mut env, &tokens).unwrap_or_default());
    let apps = app_ids
        .into_iter()
        .enumerate()
        .map(
            |(index, appid)| crate::pb::cmsg_client_pics::PicsAppInfoReq {
                appid,
                access_token: tokens.get(index).copied().unwrap_or_default(),
                only_public_obsolete: false,
            },
        )
        .collect::<Vec<_>>();
    let Some(response) =
        request_pics_product_info(&runtime, Vec::new(), apps, false, Duration::from_secs(30))
    else {
        return ptr::null_mut();
    };
    let mut apps = Vec::new();
    for app in response.apps {
        if app.buffer.is_empty() {
            continue;
        }
        let Some(root) = crate::vdf::parse_auto(&app.buffer) else {
            continue;
        };
        let appinfo = if root.name.eq_ignore_ascii_case("appinfo") {
            &root
        } else {
            root.child("appinfo").unwrap_or(&root)
        };
        apps.push(json!({
            "appid": app.appid,
            "changeNumber": app.change_number,
            "appinfo": kvnode_to_json_value(appinfo),
        }));
    }
    new_string_or_null(&mut env, &json!(apps).to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsPackageInfo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    package_ids: JString,
    tokens: JString,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let package_ids =
        parse_u32_lines(&jstring_to_string(&mut env, &package_ids).unwrap_or_default());
    let tokens = parse_u64_lines(&jstring_to_string(&mut env, &tokens).unwrap_or_default());
    let packages = package_ids
        .into_iter()
        .enumerate()
        .map(
            |(index, packageid)| crate::pb::cmsg_client_pics::PicsPackageInfoReq {
                packageid,
                access_token: tokens.get(index).copied().unwrap_or_default(),
            },
        )
        .collect::<Vec<_>>();
    let Some(response) = request_pics_product_info(
        &runtime,
        packages,
        Vec::new(),
        false,
        Duration::from_secs(30),
    ) else {
        return ptr::null_mut();
    };
    let mut packages = Vec::new();
    for package in response.packages {
        if package.buffer.is_empty() {
            continue;
        }
        let Some((_package_id, root)) = crate::vdf::parse_binary_package(&package.buffer) else {
            continue;
        };
        let appids = root
            .child("appids")
            .map(|node| {
                node.children
                    .iter()
                    .map(|child| child.as_uint(0))
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        let depotids = root
            .child("depotids")
            .map(|node| {
                node.children
                    .iter()
                    .map(|child| child.as_uint(0))
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        packages.push(json!({
            "packageid": package.packageid,
            "changeNumber": package.change_number,
            "appids": appids,
            "depotids": depotids,
        }));
    }
    new_string_or_null(&mut env, &json!(packages).to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetFamilyGroup(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    family_group_id: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_family_group_call(family_group_id as u64, job_id)
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) =
        crate::pb::cfamilygroups::CFamilyGroupsGetFamilyGroupResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "name": response.name,
        "members": response.members.iter().map(|member| member.steamid).collect::<Vec<_>>(),
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetOwnedGames(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    steam_id: jlong,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_owned_games_call(steam_id as u64, job_id)
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::cplayer::CPlayerGetOwnedGamesResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!(response
        .games
        .iter()
        .map(|game| json!({
            "appId": game.appid,
            "name": game.name,
            "playtimeTwoWeeks": game.playtime_2weeks,
            "playtimeForever": game.playtime_forever,
            "imgIconUrl": game.img_icon_url,
            "sortAs": game.sort_as,
            "rtimeLastPlayed": game.rtime_last_played,
        }))
        .collect::<Vec<_>>())
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSignalAppLaunchIntent(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    client_id: jlong,
    machine_name: JString,
    ignore_pending: jboolean,
    os_type: jint,
) -> jstring {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return ptr::null_mut();
    };
    let Some(runtime) = handle.connected_runtime() else {
        return ptr::null_mut();
    };
    let machine_name = jstring_to_string(&mut env, &machine_name).unwrap_or_default();
    let Some(body) =
        request_authed_service_body(&runtime, Duration::from_secs(30), |core, job_id| {
            core.build_cloud_launch_intent_call(
                app_id.max(0) as u32,
                client_id as u64,
                machine_name,
                ignore_pending != JNI_FALSE,
                os_type,
                job_id,
            )
        })
    else {
        return ptr::null_mut();
    };
    let Some(response) = crate::pb::ccloud::CCloudAppLaunchIntentResponse::deserialize(&body)
    else {
        return ptr::null_mut();
    };
    let value = json!({
        "pendingOps": response.pending_operation_codes,
    })
    .to_string();
    new_string_or_null(&mut env, &value)
}

#[no_mangle]
pub extern "system" fn Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSignalAppExitSyncDone(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    app_id: jint,
    client_id: jlong,
    uploads_completed: jboolean,
    uploads_required: jboolean,
) {
    let Some(handle) = (unsafe { from_session_handle_mut(handle) }) else {
        return;
    };
    let Some(runtime) = handle.connected_runtime() else {
        return;
    };
    let _ = request_authed_service_success(&runtime, Duration::from_secs(5), |core, job_id| {
        core.build_cloud_exit_sync_done_call(
            app_id.max(0) as u32,
            client_id as u64,
            uploads_completed != JNI_FALSE,
            uploads_required != JNI_FALSE,
            job_id,
        )
    });
}
