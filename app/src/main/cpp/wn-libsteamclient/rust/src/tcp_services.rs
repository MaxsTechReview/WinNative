//! Port of `tcp_services.cpp`. Spawns one listener thread per service on
//! 127.0.0.1, accepting Wine bridge connections.
//!
//! Each accepted connection gets its own detached thread that frames
//! length-prefixed messages (4-byte LE length + body), or falls back to raw
//! logging if the first 4 bytes look unreasonable.

use crate::callbacks as cb;
use crate::log;
use crate::state;
use std::io::{Read, Write};
use std::net::{Ipv4Addr, SocketAddrV4, TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, AtomicI32, Ordering};
use std::sync::OnceLock;
use std::thread;

static START_ONCE: OnceLock<()> = OnceLock::new();
static ACCEPTED_COUNT: AtomicI32 = AtomicI32::new(0);
static ANY_BOUND: AtomicBool = AtomicBool::new(false);

fn parse_port(env_value: Option<String>, fallback: u16) -> u16 {
    let Some(value) = env_value.filter(|v| !v.is_empty()) else {
        return fallback;
    };
    let port_part = if let Some(colon) = value.rfind(':') {
        &value[colon + 1..]
    } else {
        &value[..]
    };
    match port_part.parse::<u32>() {
        Ok(p) if p > 0 && p <= 65535 => p as u16,
        _ => {
            log::log_warn(&format!(
                "tcp_services: malformed env value \"{}\", falling back to :{}",
                value, fallback
            ));
            fallback
        }
    }
}

fn bind_listener(port: u16, svc_name: &str) -> Option<TcpListener> {
    let addr = SocketAddrV4::new(Ipv4Addr::LOCALHOST, port);
    match TcpListener::bind(addr) {
        Ok(listener) => {
            log::log_info(&format!(
                "tcp_services[{}]: listening on 127.0.0.1:{}",
                svc_name, port
            ));
            Some(listener)
        }
        Err(e) => {
            log::log_error(&format!(
                "tcp_services[{}]: bind(127.0.0.1:{}) failed: {} \
                 (port likely already in use — prebuilt libsteamclient.so \
                 may still own it from a previous launch cycle)",
                svc_name, port, e
            ));
            None
        }
    }
}

fn hex_dump(buf: &[u8], max_bytes: usize) -> String {
    let shown = buf.len().min(max_bytes);
    let mut out = String::with_capacity(shown * 3 + 16);
    for (i, b) in buf.iter().take(shown).enumerate() {
        if i > 0 {
            out.push(' ');
        }
        out.push_str(&format!("{:02x}", b));
    }
    if shown < buf.len() {
        out.push_str(&format!(" ...(+{} B)", buf.len() - shown));
    }
    out
}

fn read_exact(stream: &mut TcpStream, n: usize) -> Option<Vec<u8>> {
    let mut buf = vec![0u8; n];
    match stream.read_exact(&mut buf) {
        Ok(()) => Some(buf),
        Err(_) => None,
    }
}

fn handle_connection(mut stream: TcpStream, svc_name: String) {
    let _ = stream.set_nodelay(true);
    let Some(header) = read_exact(&mut stream, 4) else {
        log::log_info(&format!(
            "tcp_services[{}]: conn closed before any header",
            svc_name
        ));
        return;
    };
    let first_len = u32::from_le_bytes([header[0], header[1], header[2], header[3]]);
    let framed = first_len > 0 && first_len <= 256 * 1024;

    if !framed {
        log::log_info(&format!(
            "tcp_services[{}]: conn raw-stream (first 4B={})",
            svc_name,
            hex_dump(&header, 96)
        ));
        let mut chunk = [0u8; 256];
        loop {
            match stream.read(&mut chunk) {
                Ok(0) => break,
                Ok(n) => {
                    log::log_info(&format!(
                        "tcp_services[{}]: raw {} B: {}",
                        svc_name,
                        n,
                        hex_dump(&chunk[..n], 96)
                    ));
                }
                Err(_) => break,
            }
        }
        log::log_info(&format!(
            "tcp_services[{}]: conn closed (raw mode)",
            svc_name
        ));
        return;
    }

    log::log_info(&format!(
        "tcp_services[{}]: conn framed-mode entry, first body={} B",
        svc_name, first_len
    ));
    let mut frame_idx = 0;
    let mut length = first_len;
    loop {
        let Some(body) = read_exact(&mut stream, length as usize) else {
            log::log_info(&format!(
                "tcp_services[{}]: EOF mid-frame {} (expected {} B)",
                svc_name, frame_idx, length
            ));
            break;
        };
        log::log_info(&format!(
            "tcp_services[{}]: frame[{}] {} B: {}",
            svc_name,
            frame_idx,
            length,
            hex_dump(&body, 96)
        ));
        frame_idx += 1;
        let Some(next) = read_exact(&mut stream, 4) else {
            log::log_info(&format!(
                "tcp_services[{}]: conn closed cleanly after {} frame(s)",
                svc_name, frame_idx
            ));
            break;
        };
        length = u32::from_le_bytes([next[0], next[1], next[2], next[3]]);
        if length == 0 || length > 1024 * 1024 {
            log::log_warn(&format!(
                "tcp_services[{}]: unreasonable next-frame length={} after {} frame(s) — closing",
                svc_name, length, frame_idx
            ));
            break;
        }
    }
    // Stream drops -> close.
    let _ = stream.shutdown(std::net::Shutdown::Both);
    let _ = stream.flush();
}

fn listener_loop(listener: TcpListener, svc_name: String) {
    loop {
        match listener.accept() {
            Ok((conn, peer)) => {
                let count = ACCEPTED_COUNT.fetch_add(1, Ordering::Relaxed) + 1;
                log::log_info(&format!(
                    "tcp_services[{}]: accepted from {}:{} (total={})",
                    svc_name,
                    peer.ip(),
                    peer.port(),
                    count
                ));
                let name = svc_name.clone();
                thread::spawn(move || handle_connection(conn, name));
            }
            Err(e) => {
                log::log_warn(&format!(
                    "tcp_services[{}]: accept() failed: {} — terminating loop \
                     (emitting IPCFailure_t kFailurePipeFail)",
                    svc_name, e
                ));
                let payload = cb::IPCFailure {
                    m_eFailureType: cb::K_FAILURE_PIPE_FAIL,
                    _pad: [0; 7],
                };
                let raw = unsafe { cb::as_bytes(&payload) };
                state::push_callback_bytes(
                    state::state().user.load(Ordering::SeqCst),
                    cb::K_IPC_FAILURE,
                    raw,
                );
                break;
            }
        }
    }
}

fn spawn_service(env_key: &str, fallback: u16, svc_name: &str) -> bool {
    let port = parse_port(std::env::var(env_key).ok(), fallback);
    let Some(listener) = bind_listener(port, svc_name) else {
        return false;
    };
    let name = svc_name.to_string();
    thread::spawn(move || listener_loop(listener, name));
    ANY_BOUND.store(true, Ordering::SeqCst);
    true
}

pub fn start_tcp_services() -> bool {
    START_ONCE.get_or_init(|| {
        let a = spawn_service("Steam3Master", 57343, "Steam3Master");
        let b = spawn_service("SteamClientService", 57344, "SteamClientService");
        log::log_info(&format!(
            "tcp_services: Steam3Master={} SteamClientService={}",
            if a { "OK" } else { "FAIL" },
            if b { "OK" } else { "FAIL" }
        ));
    });
    ANY_BOUND.load(Ordering::SeqCst)
}

pub fn accepted_connection_count() -> i32 {
    ACCEPTED_COUNT.load(Ordering::Relaxed)
}
