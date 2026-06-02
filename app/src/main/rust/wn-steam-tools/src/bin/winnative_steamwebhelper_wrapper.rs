#[cfg(unix)]
mod unix_impl {
    use std::env;
    use std::ffi::{CString, OsString};
    use std::fs::{self, OpenOptions};
    use std::os::fd::AsRawFd;
    use std::os::unix::ffi::{OsStrExt, OsStringExt};
    use std::os::unix::fs::OpenOptionsExt;
    use std::path::{Path, PathBuf};

    const PRELOAD_LIB: &str = "/lib/aarch64-linux-gnu/libwinnative-steamwebhelper-preload.so";

    fn redirect_webhelper_log() {
        let path = env::var_os("WINNATIVE_STEAMWEBHELPER_LOG")
            .filter(|v| !v.is_empty())
            .map(PathBuf::from)
            .or_else(|| {
                env::var_os("HOME")
                    .filter(|v| !v.is_empty())
                    .map(|home| Path::new(&home).join(".steam/steam/logs/steamwebhelper.log"))
            })
            .unwrap_or_else(|| PathBuf::from("/tmp/winnative-steamwebhelper.log"));

        let Ok(file) = OpenOptions::new()
            .create(true)
            .append(true)
            .mode(0o600)
            .open(path)
        else {
            return;
        };

        unsafe {
            libc::dup2(file.as_raw_fd(), libc::STDOUT_FILENO);
            libc::dup2(file.as_raw_fd(), libc::STDERR_FILENO);
        }
    }

    fn arg_has_prefix(args: &[OsString], prefix: &str) -> bool {
        args.iter()
            .skip(1)
            .any(|arg| arg.as_bytes().starts_with(prefix.as_bytes()))
    }

    fn arg_exists(args: &[OsString], needle: &str) -> bool {
        args.iter()
            .skip(1)
            .any(|arg| arg.as_bytes() == needle.as_bytes())
    }

    fn output_has_arg(args: &[OsString], needle: &str) -> bool {
        args.iter().any(|arg| arg.as_bytes() == needle.as_bytes())
    }

    fn find_arg_value<'a>(args: &'a [OsString], prefix: &str) -> Option<&'a [u8]> {
        args.iter().skip(1).find_map(|arg| {
            let bytes = arg.as_bytes();
            bytes
                .starts_with(prefix.as_bytes())
                .then(|| &bytes[prefix.len()..])
        })
    }

    fn unlink_path_and_sidecar(path: &Path) {
        let _ = fs::remove_file(path);
        let mut sidecar = path.as_os_str().as_bytes().to_vec();
        sidecar.extend_from_slice(b".winnative-readlink-target");
        let _ = fs::remove_file(PathBuf::from(OsString::from_vec(sidecar)));
    }

    fn cleanup_process_singleton_state(args: &[OsString]) {
        let Some(cache_dir) = find_arg_value(args, "-cachedir=") else {
            return;
        };
        if cache_dir.is_empty() {
            return;
        }
        let cache_dir = PathBuf::from(OsString::from_vec(cache_dir.to_vec()));
        for name in ["SingletonCookie", "SingletonLock", "SingletonSocket"] {
            unlink_path_and_sidecar(&cache_dir.join(name));
        }
        eprintln!(
            "winnative steamwebhelper wrapper: cleared ProcessSingleton state in {}",
            cache_dir.display()
        );
    }

    fn is_visible_ui_launch() -> bool {
        env::var_os("WINNATIVE_STEAM_VISIBLE_UI").as_deref() == Some("1".as_ref())
    }

    fn prepend_ld_preload(library: &str) {
        match env::var("LD_PRELOAD") {
            Ok(old) if old.contains(library) => {}
            Ok(old) if !old.is_empty() => env::set_var("LD_PRELOAD", format!("{library}:{old}")),
            _ => env::set_var("LD_PRELOAD", library),
        }
    }

    fn should_skip_visible_ui_arg(arg: &[u8]) -> bool {
        matches!(
            arg,
            b"--disable-gpu"
                | b"--disable-gpu-compositing"
                | b"--disable-gpu-rasterization"
                | b"--disable-gpu-sandbox"
                | b"--single-process"
                | b"--ignore-gpu-blocklist"
                | b"--valve-enable-site-isolation"
                | b"--disable-software-rasterizer"
        )
    }

    fn rewrite_visible_ui_arg(arg: &[u8]) -> Option<Option<OsString>> {
        const ENABLE: &[u8] = b"--enable-features=";
        const DISABLE: &[u8] = b"--disable-features=";

        if let Some(features) = arg.strip_prefix(ENABLE) {
            let kept = features
                .split(|b| *b == b',')
                .filter(|feature| *feature != b"V4L2VideoDecode")
                .collect::<Vec<_>>();
            if kept.is_empty() {
                return Some(None);
            }
            let mut out = ENABLE.to_vec();
            for (idx, feature) in kept.iter().enumerate() {
                if idx > 0 {
                    out.push(b',');
                }
                out.extend_from_slice(feature);
            }
            return Some(Some(OsString::from_vec(out)));
        }

        if arg.starts_with(DISABLE) {
            if arg
                .split(|b| *b == b',')
                .any(|feature| feature == b"NotReachedIsFatal")
            {
                return None;
            }
            let mut out = arg.to_vec();
            out.extend_from_slice(b",NotReachedIsFatal");
            return Some(Some(OsString::from_vec(out)));
        }

        None
    }

    fn executable(path: &Path) -> bool {
        use std::os::unix::fs::PermissionsExt;
        path.metadata()
            .map(|meta| meta.permissions().mode() & 0o111 != 0)
            .unwrap_or(false)
    }

    fn set_webhelper_affinity() {
        let mut mask = [0usize; 16];
        for cpu in 1usize..=6 {
            let bits = usize::BITS as usize;
            mask[cpu / bits] |= 1usize << (cpu % bits);
        }
        unsafe {
            libc::sched_setaffinity(
                0,
                std::mem::size_of_val(&mask),
                mask.as_ptr().cast::<libc::cpu_set_t>(),
            );
        }
    }

    fn cstring(value: &OsString) -> Option<CString> {
        CString::new(value.as_bytes()).ok()
    }

    fn execv_os(target: &Path, args: &[OsString]) {
        let Some(target_c) = CString::new(target.as_os_str().as_bytes()).ok() else {
            return;
        };
        let cstrings = args.iter().filter_map(cstring).collect::<Vec<_>>();
        let mut ptrs = cstrings
            .iter()
            .map(|s| s.as_ptr())
            .collect::<Vec<*const libc::c_char>>();
        ptrs.push(std::ptr::null());
        unsafe {
            libc::execv(target_c.as_ptr(), ptrs.as_ptr());
        }
    }

    pub fn run() {
        let original_args = env::args_os().collect::<Vec<_>>();
        let top_level_webhelper = !arg_has_prefix(&original_args, "--type=");
        let visible_ui = is_visible_ui_launch();

        let current_exe = env::current_exe().ok();
        let dir = current_exe
            .as_deref()
            .and_then(Path::parent)
            .map(Path::to_path_buf)
            .unwrap_or_else(|| PathBuf::from("."));
        let _ = env::set_current_dir(&dir);

        let wrapper_path = dir.join("steamwebhelper.winnative-real");
        let preferred_target = dir.join("steamwebhelper.winnative-real.bin");
        let target = if executable(&preferred_target) {
            preferred_target
        } else {
            wrapper_path.clone()
        };

        if env::var_os("DISPLAY").is_none_or(|v| v.is_empty()) {
            env::set_var("DISPLAY", ":0");
        }
        env::set_var("XKB_DISABLE", "1");
        env::set_var("LIBGL_KOPPER_DISABLE", "true");
        prepend_ld_preload(PRELOAD_LIB);
        set_webhelper_affinity();
        redirect_webhelper_log();

        if top_level_webhelper {
            cleanup_process_singleton_state(&original_args);
        }

        let common_extra_args = [
            "--no-sandbox",
            "--disable-seccomp-filter-sandbox",
            "--disable-setuid-sandbox",
            "--no-xshm",
            "--winhttp-proxy-resolver",
        ];
        let hidden_extra_args = [
            "--single-process",
            "--disable-gpu",
            "--disable-gpu-compositing",
            "--disable-gpu-rasterization",
            "--disable-gpu-sandbox",
        ];
        let visible_extra_args = [
            "--single-process",
            "--disable-gpu",
            "--disable-gpu-compositing",
            "--disable-breakpad",
            "--disable-crash-reporter",
            "--enable-logging=stderr",
            "--v=1",
            "--log-file=/opt/steam-arm64/client/logs/cef_log.txt",
        ];
        let visible_child_extra_args = [
            "--disable-breakpad",
            "--disable-crash-reporter",
            "--enable-logging=stderr",
            "--v=1",
            "--log-file=/opt/steam-arm64/client/logs/cef_log.txt",
        ];

        let mut child_args = Vec::<OsString>::new();
        child_args.push(wrapper_path.as_os_str().to_os_string());
        for arg in original_args.iter().skip(1) {
            if visible_ui && should_skip_visible_ui_arg(arg.as_bytes()) {
                continue;
            }
            if visible_ui {
                if let Some(rewritten) = rewrite_visible_ui_arg(arg.as_bytes()) {
                    if let Some(rewritten) = rewritten {
                        child_args.push(rewritten);
                    }
                    continue;
                }
            }
            child_args.push(arg.clone());
        }

        if top_level_webhelper {
            for arg in common_extra_args {
                if !arg_exists(&original_args, arg) {
                    child_args.push(arg.into());
                }
            }
            if !arg_has_prefix(&original_args, "--browser-subprocess-path=") {
                child_args.push(
                    format!(
                        "--browser-subprocess-path={}/steamwebhelper.winnative-real",
                        dir.display()
                    )
                    .into(),
                );
            }
            let extra = if visible_ui {
                &visible_extra_args[..]
            } else {
                &hidden_extra_args[..]
            };
            for arg in extra {
                if !output_has_arg(&child_args, arg) {
                    child_args.push((*arg).into());
                }
            }
        }

        if visible_ui
            && !top_level_webhelper
            && !arg_exists(&original_args, "--type=crashpad-handler")
        {
            for arg in visible_child_extra_args {
                if !output_has_arg(&child_args, arg) {
                    child_args.push(arg.into());
                }
            }
        }

        eprintln!(
        "winnative steamwebhelper wrapper: target={} wrapper_path={} top_level={} visible_ui={} argc_in={} argc_out={}",
        target.display(),
        wrapper_path.display(),
        top_level_webhelper as i32,
        visible_ui as i32,
        original_args.len(),
        child_args.len()
    );
        eprintln!(
            "winnative steamwebhelper wrapper: LD_PRELOAD={}",
            env::var("LD_PRELOAD").unwrap_or_default()
        );
        for (idx, arg) in child_args.iter().enumerate() {
            eprintln!(
                "winnative steamwebhelper argv[{idx}]={}",
                arg.to_string_lossy()
            );
        }

        execv_os(&target, &child_args);

        let mut fallback_args = original_args;
        if let Some(first) = fallback_args.first_mut() {
            *first = wrapper_path.as_os_str().to_os_string();
        }
        execv_os(&target, &fallback_args);
        eprintln!(
            "winnative steamwebhelper wrapper: exec {} failed: {}",
            target.display(),
            std::io::Error::last_os_error()
        );
        std::process::exit(126);
    }
}

#[cfg(unix)]
fn main() {
    unix_impl::run();
}

#[cfg(not(unix))]
fn main() {
    eprintln!("winnative steamwebhelper wrapper is only supported on Unix targets");
    std::process::exit(126);
}
