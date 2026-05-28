//! Tiny wrapper around `__android_log_write` for the "WnLibSteamClient" tag.

#[cfg(target_os = "android")]
#[link(name = "log")]
unsafe extern "C" {
    fn __android_log_write(prio: i32, tag: *const u8, text: *const u8) -> i32;
}

const ANDROID_LOG_INFO: i32 = 4;
const ANDROID_LOG_WARN: i32 = 5;
const ANDROID_LOG_ERROR: i32 = 6;

#[cfg(target_os = "android")]
fn log_at(prio: i32, message: &str) {
    let tag = b"WnLibSteamClient\0";
    let sanitized = message.replace('\0', " ");
    let mut bytes = sanitized.into_bytes();
    bytes.push(0);
    unsafe {
        __android_log_write(prio, tag.as_ptr(), bytes.as_ptr());
    }
}

#[cfg(not(target_os = "android"))]
fn log_at(_prio: i32, _message: &str) {}

pub fn log_info(message: &str) {
    log_at(ANDROID_LOG_INFO, message);
}

pub fn log_warn(message: &str) {
    log_at(ANDROID_LOG_WARN, message);
}

pub fn log_error(message: &str) {
    log_at(ANDROID_LOG_ERROR, message);
}
