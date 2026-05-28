use crate::cdn_client::CdnClient;
use crate::content_manifest::{ChunkData, ContentManifest};
use crate::depot_chunk::process_depot_chunk;
use crate::pb::ccontentserverdirectory::CContentServerDirectoryServerInfo;
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Component, Path};
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;

pub const DEPOT_FILE_FLAG_EXECUTABLE: u32 = 32;
pub const DEPOT_FILE_FLAG_DIRECTORY: u32 = 64;
pub const DEPOT_FILE_FLAG_SYMLINK: u32 = 512;
pub const MAX_CHUNK_ATTEMPTS: u32 = 5;
pub const SLOW_CHUNK_ROTATE_THRESHOLD_SECS: u64 = 8;
pub const SLOW_CHUNK_ROTATE_CONSECUTIVE_LIMIT: u32 = 3;

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct DepotWriteResult {
    pub files_written: u64,
    pub bytes_written: u64,
    pub resume_trust_safe: bool,
    pub error: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum DepotFileAction {
    Directory { path: String },
    Symlink { path: String, target: String },
    Regular { path: String, size: u64, mode: u32 },
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ChunkWriteJob {
    pub file_idx: u32,
    pub chunk_idx: u32,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct DepotWritePlan {
    pub total_bytes: u64,
    pub files_written: u64,
    pub actions: Vec<DepotFileAction>,
    pub chunk_jobs: Vec<ChunkWriteJob>,
    pub worker_count: u32,
}

#[derive(Clone, Copy, Debug)]
pub struct DepotWriteOptions<'a> {
    pub cdn_auth_token: &'a str,
    pub timeout: Duration,
    pub max_workers: u32,
    pub cancel: Option<&'a AtomicBool>,
}

impl Default for DepotWriteOptions<'_> {
    fn default() -> Self {
        Self {
            cdn_auth_token: "",
            timeout: CdnClient::default_timeout(),
            max_workers: 8,
            cancel: None,
        }
    }
}

impl DepotWriteResult {
    pub fn ok(&self) -> bool {
        self.error.is_empty()
    }

    pub fn success(files_written: u64, bytes_written: u64) -> Self {
        Self {
            files_written,
            bytes_written,
            resume_trust_safe: true,
            error: String::new(),
        }
    }

    pub fn fail(error: impl Into<String>, resume_trust_safe: bool) -> Self {
        Self {
            error: error.into(),
            resume_trust_safe,
            ..Default::default()
        }
    }
}

pub fn retry_backoff_millis(attempt: u32) -> u64 {
    if attempt == 0 {
        return 0;
    }
    (300u64 << (attempt - 1)).min(4000)
}

pub fn chunk_attempt_server_indices(
    start_server_index: usize,
    server_count: usize,
    attempts: u32,
) -> Vec<usize> {
    if server_count == 0 || attempts == 0 {
        return Vec::new();
    }
    let mut out = Vec::with_capacity(attempts as usize);
    let mut server_index = start_server_index % server_count;
    for attempt in 0..attempts {
        if attempt > 0 && server_count > 1 {
            server_index = (server_index + 1) % server_count;
        }
        out.push(server_index);
    }
    out
}

pub fn should_rotate_after_slow_chunks(consecutive_slow_chunks: u32, server_count: usize) -> bool {
    server_count > 1 && consecutive_slow_chunks >= SLOW_CHUNK_ROTATE_CONSECUTIVE_LIMIT
}

pub fn depot_adler_hash(data: &[u8]) -> u32 {
    const BLOCK: usize = 5552;
    let mut a = 0u32;
    let mut b = 0u32;
    for chunk in data.chunks(BLOCK) {
        for byte in chunk {
            a += *byte as u32;
            b += a;
        }
        a %= 65521;
        b %= 65521;
    }
    a | (b << 16)
}

pub fn path_is_safe(rel: &str) -> bool {
    if rel.is_empty() || rel.starts_with('/') || rel.starts_with('\\') {
        return false;
    }
    let path = Path::new(rel);
    if path.is_absolute() {
        return false;
    }
    path.components().all(|component| {
        matches!(component, Component::Normal(_)) || matches!(component, Component::CurDir)
    }) && !path
        .components()
        .any(|component| matches!(component, Component::ParentDir | Component::Prefix(_)))
}

pub fn clamp_worker_count(max_workers: u32, outstanding_chunks: usize) -> u32 {
    if outstanding_chunks == 0 {
        return 0;
    }
    let requested = if max_workers == 0 { 1 } else { max_workers };
    requested.min(64).min(outstanding_chunks as u32)
}

pub fn plan_depot_write(
    manifest: &ContentManifest,
    depot_key: &[u8],
    server_count: usize,
    target_dir: &str,
    max_workers: u32,
) -> Result<DepotWritePlan, DepotWriteResult> {
    if manifest.metadata.filenames_encrypted {
        return Err(DepotWriteResult::fail(
            "write_depot: manifest filenames are still encrypted",
            false,
        ));
    }
    if depot_key.len() != 32 {
        return Err(DepotWriteResult::fail(
            "write_depot: bad depot key length",
            false,
        ));
    }
    if server_count == 0 {
        return Err(DepotWriteResult::fail("write_depot: no CDN servers", false));
    }

    let mut plan = DepotWritePlan {
        total_bytes: manifest.files.iter().map(|file| file.size).sum(),
        ..Default::default()
    };

    for (file_idx, file) in manifest.files.iter().enumerate() {
        if !path_is_safe(&file.filename) {
            return Err(DepotWriteResult::fail(
                format!("write_depot: unsafe path '{}'", file.filename),
                false,
            ));
        }
        let path = join_target_path(target_dir, &file.filename);
        if !file.linktarget.is_empty() {
            plan.actions.push(DepotFileAction::Symlink {
                path,
                target: file.linktarget.clone(),
            });
            plan.files_written += 1;
            continue;
        }
        if (file.flags & DEPOT_FILE_FLAG_DIRECTORY) != 0 {
            plan.actions.push(DepotFileAction::Directory { path });
            continue;
        }
        let mode = if (file.flags & DEPOT_FILE_FLAG_EXECUTABLE) != 0 {
            0o755
        } else {
            0o644
        };
        plan.actions.push(DepotFileAction::Regular {
            path,
            size: file.size,
            mode,
        });
        plan.files_written += 1;
        for chunk_idx in 0..file.chunks.len() {
            plan.chunk_jobs.push(ChunkWriteJob {
                file_idx: file_idx as u32,
                chunk_idx: chunk_idx as u32,
            });
        }
    }
    plan.worker_count = clamp_worker_count(max_workers, plan.chunk_jobs.len());
    Ok(plan)
}

pub fn write_depot_sequential(
    manifest: &ContentManifest,
    depot_key: &[u8],
    cdn: &CdnClient,
    servers: &[CContentServerDirectoryServerInfo],
    target_dir: &str,
    options: DepotWriteOptions<'_>,
) -> DepotWriteResult {
    let plan = match plan_depot_write(
        manifest,
        depot_key,
        servers.len(),
        target_dir,
        options.max_workers,
    ) {
        Ok(plan) => plan,
        Err(error) => return error,
    };
    let layout = create_depot_layout(&plan);
    if !layout.ok() {
        return layout;
    }

    let mut bytes_written = 0u64;
    for (job_index, job) in plan.chunk_jobs.iter().enumerate() {
        if options
            .cancel
            .is_some_and(|cancel| cancel.load(Ordering::Relaxed))
        {
            return DepotWriteResult::fail("cancelled", true);
        }
        match fetch_process_write_chunk(
            cdn,
            servers,
            manifest,
            job.file_idx as usize,
            job.chunk_idx as usize,
            depot_key,
            target_dir,
            options.cdn_auth_token,
            job_index % servers.len(),
            options.timeout,
        ) {
            Ok(bytes) => bytes_written += bytes,
            Err(error) => return DepotWriteResult::fail(error, true),
        }
    }

    for file in &manifest.files {
        if options
            .cancel
            .is_some_and(|cancel| cancel.load(Ordering::Relaxed))
        {
            return DepotWriteResult::fail("cancelled", true);
        }
        if !file.linktarget.is_empty() || (file.flags & DEPOT_FILE_FLAG_DIRECTORY) != 0 {
            continue;
        }
        let path = join_target_path(target_dir, &file.filename);
        if let Err(error) = finalize_regular_file(path, file.size) {
            return DepotWriteResult::fail(error, true);
        }
    }

    DepotWriteResult {
        files_written: plan.files_written,
        bytes_written,
        resume_trust_safe: true,
        error: String::new(),
    }
}

pub fn create_depot_layout(plan: &DepotWritePlan) -> DepotWriteResult {
    for action in &plan.actions {
        let result = match action {
            DepotFileAction::Directory { path } => create_directory(path),
            DepotFileAction::Symlink { path, target } => create_symlink(path, target),
            DepotFileAction::Regular { path, mode, .. } => create_regular_file(path, *mode),
        };
        if let Err(error) = result {
            return DepotWriteResult::fail(error, false);
        }
    }
    DepotWriteResult {
        files_written: plan.files_written,
        bytes_written: 0,
        resume_trust_safe: true,
        error: String::new(),
    }
}

pub fn write_chunk_at(path: impl AsRef<Path>, offset: u64, data: &[u8]) -> Result<u64, String> {
    let path = path.as_ref();
    make_parent_dirs(path)?;
    let mut file = OpenOptions::new()
        .create(true)
        .write(true)
        .read(true)
        .truncate(false)
        .open(path)
        .map_err(|err| format!("write_depot: open '{}': {err}", path.display()))?;
    file.seek(SeekFrom::Start(offset))
        .map_err(|err| format!("write_depot: seek '{}': {err}", path.display()))?;
    file.write_all(data)
        .map_err(|err| format!("write_depot: write '{}': {err}", path.display()))?;
    Ok(data.len() as u64)
}

pub fn process_and_write_chunk(
    path: impl AsRef<Path>,
    chunk: &ChunkData,
    raw_chunk: &[u8],
    depot_key: &[u8],
) -> Result<u64, String> {
    let processed = process_depot_chunk(raw_chunk, depot_key, chunk.crc, chunk.cb_original);
    if !processed.ok() {
        return Err(format!("decode: {}", processed.error));
    }
    write_chunk_at(path, chunk.offset, &processed.data)
}

#[allow(clippy::too_many_arguments)]
pub fn fetch_process_write_chunk(
    cdn: &CdnClient,
    servers: &[CContentServerDirectoryServerInfo],
    manifest: &ContentManifest,
    file_idx: usize,
    chunk_idx: usize,
    depot_key: &[u8],
    target_dir: &str,
    cdn_auth_token: &str,
    start_server_index: usize,
    timeout: Duration,
) -> Result<u64, String> {
    if servers.is_empty() {
        return Err("write_depot: no CDN servers".to_string());
    }
    let file = manifest
        .files
        .get(file_idx)
        .ok_or_else(|| "write_depot: bad file index".to_string())?;
    let chunk = file
        .chunks
        .get(chunk_idx)
        .ok_or_else(|| "write_depot: bad chunk index".to_string())?;
    let path = join_target_path(target_dir, &file.filename);
    let mut last_error = String::new();
    for (attempt, server_idx) in
        chunk_attempt_server_indices(start_server_index, servers.len(), MAX_CHUNK_ATTEMPTS)
            .into_iter()
            .enumerate()
    {
        if attempt > 0 {
            thread::sleep(Duration::from_millis(retry_backoff_millis(attempt as u32)));
        }
        let fetched = cdn.fetch_chunk(
            &servers[server_idx],
            manifest.metadata.depot_id,
            &chunk.sha,
            cdn_auth_token,
            timeout,
        );
        if !fetched.ok() {
            last_error = fetched.error;
            continue;
        }
        match process_and_write_chunk(&path, chunk, &fetched.data, depot_key) {
            Ok(bytes) => return Ok(bytes),
            Err(error) => last_error = error,
        }
    }
    Err(format!(
        "write_depot: chunk for '{}' failed after {} attempts: {}",
        file.filename, MAX_CHUNK_ATTEMPTS, last_error
    ))
}

pub fn existing_chunk_matches(path: impl AsRef<Path>, chunk: &ChunkData) -> bool {
    let path = path.as_ref();
    let Ok(mut file) = File::open(path) else {
        return false;
    };
    let Ok(metadata) = file.metadata() else {
        return false;
    };
    let end = chunk.offset.saturating_add(chunk.cb_original as u64);
    if chunk.cb_original == 0 || metadata.len() < end {
        return false;
    }
    let mut buf = vec![0u8; chunk.cb_original as usize];
    if file.seek(SeekFrom::Start(chunk.offset)).is_err() || file.read_exact(&mut buf).is_err() {
        return false;
    }
    depot_adler_hash(&buf) == chunk.crc
}

pub fn sync_file(path: impl AsRef<Path>) -> bool {
    OpenOptions::new()
        .read(true)
        .write(true)
        .open(path)
        .and_then(|file| file.sync_all())
        .is_ok()
}

pub fn finalize_regular_file(path: impl AsRef<Path>, size: u64) -> Result<(), String> {
    let path = path.as_ref();
    let file = OpenOptions::new()
        .write(true)
        .open(path)
        .map_err(|err| format!("write_depot: final open '{}': {err}", path.display()))?;
    file.set_len(size)
        .map_err(|err| format!("write_depot: final truncate '{}': {err}", path.display()))?;
    file.sync_all()
        .map_err(|err| format!("write_depot: final sync '{}': {err}", path.display()))
}

fn join_target_path(target_dir: &str, rel: &str) -> String {
    if target_dir.ends_with('/') || target_dir.ends_with('\\') {
        format!("{target_dir}{rel}")
    } else {
        format!("{target_dir}/{rel}")
    }
}

fn create_directory(path: &str) -> Result<(), String> {
    fs::create_dir_all(path).map_err(|err| format!("write_depot: mkdir '{path}': {err}"))
}

fn create_regular_file(path: &str, mode: u32) -> Result<(), String> {
    let path_ref = Path::new(path);
    make_parent_dirs(path_ref)?;
    OpenOptions::new()
        .create(true)
        .write(true)
        .read(true)
        .truncate(false)
        .open(path_ref)
        .map_err(|err| format!("write_depot: open '{path}': {err}"))?;
    set_file_mode(path_ref, mode)
}

fn create_symlink(path: &str, target: &str) -> Result<(), String> {
    let path_ref = Path::new(path);
    make_parent_dirs(path_ref)?;
    if path_ref.exists() {
        fs::remove_file(path_ref).map_err(|err| format!("write_depot: unlink '{path}': {err}"))?;
    }
    create_platform_symlink(target, path_ref)
        .map_err(|err| format!("write_depot: symlink '{path}': {err}"))
}

fn make_parent_dirs(path: &Path) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        if !parent.as_os_str().is_empty() {
            fs::create_dir_all(parent)
                .map_err(|err| format!("write_depot: mkdir '{}': {err}", parent.display()))?;
        }
    }
    Ok(())
}

#[cfg(unix)]
fn create_platform_symlink(target: &str, path: &Path) -> std::io::Result<()> {
    std::os::unix::fs::symlink(target, path)
}

#[cfg(windows)]
fn create_platform_symlink(target: &str, path: &Path) -> std::io::Result<()> {
    std::os::windows::fs::symlink_file(target, path)
}

#[cfg(unix)]
fn set_file_mode(path: &Path, mode: u32) -> Result<(), String> {
    use std::os::unix::fs::PermissionsExt;
    let permissions = fs::Permissions::from_mode(mode);
    fs::set_permissions(path, permissions)
        .map_err(|err| format!("write_depot: chmod '{}': {err}", path.display()))
}

#[cfg(not(unix))]
fn set_file_mode(_path: &Path, _mode: u32) -> Result<(), String> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::{aes256_cbc_encrypt, aes256_ecb_encrypt_block, AES_BLOCK_BYTES};
    use std::path::PathBuf;

    #[test]
    fn depot_adler_uses_steam_zero_seed() {
        assert_eq!(depot_adler_hash(b""), 0);
        assert_eq!(depot_adler_hash(b"abc"), 0x024a_0126);
    }

    #[test]
    fn rejects_paths_that_escape_target() {
        assert!(path_is_safe("a/b/file.txt"));
        assert!(path_is_safe("./a/file.txt"));
        assert!(!path_is_safe(""));
        assert!(!path_is_safe("../file.txt"));
        assert!(!path_is_safe("a/../file.txt"));
        assert!(!path_is_safe("/abs/file.txt"));
    }

    #[test]
    fn retry_backoff_matches_cpp_caps() {
        assert_eq!(retry_backoff_millis(1), 300);
        assert_eq!(retry_backoff_millis(2), 600);
        assert_eq!(retry_backoff_millis(5), 4000);
        assert_eq!(MAX_CHUNK_ATTEMPTS, 5);
        assert_eq!(SLOW_CHUNK_ROTATE_THRESHOLD_SECS, 8);
    }

    #[test]
    fn chunk_retry_rotates_across_servers_like_cpp_worker() {
        assert_eq!(chunk_attempt_server_indices(0, 3, 5), [0, 1, 2, 0, 1]);
        assert_eq!(chunk_attempt_server_indices(2, 3, 5), [2, 0, 1, 2, 0]);
        assert_eq!(chunk_attempt_server_indices(0, 1, 5), [0, 0, 0, 0, 0]);
        assert!(chunk_attempt_server_indices(0, 0, 5).is_empty());
        assert!(!should_rotate_after_slow_chunks(2, 3));
        assert!(should_rotate_after_slow_chunks(3, 3));
        assert!(!should_rotate_after_slow_chunks(3, 1));
    }

    #[test]
    fn depot_plan_validates_inputs_and_enumerates_actions() {
        let manifest = ContentManifest {
            metadata: crate::content_manifest::Metadata {
                filenames_encrypted: false,
                ..Default::default()
            },
            files: vec![
                crate::content_manifest::FileMapping {
                    filename: "bin".into(),
                    flags: DEPOT_FILE_FLAG_DIRECTORY,
                    ..Default::default()
                },
                crate::content_manifest::FileMapping {
                    filename: "bin/game".into(),
                    size: 10,
                    flags: DEPOT_FILE_FLAG_EXECUTABLE,
                    chunks: vec![crate::content_manifest::ChunkData::default()],
                    ..Default::default()
                },
                crate::content_manifest::FileMapping {
                    filename: "link".into(),
                    linktarget: "bin/game".into(),
                    ..Default::default()
                },
            ],
            signature: Vec::new(),
        };

        let plan = plan_depot_write(&manifest, &[7u8; 32], 2, "/target", 99).unwrap();
        assert_eq!(plan.total_bytes, 10);
        assert_eq!(plan.files_written, 2);
        assert_eq!(plan.worker_count, 1);
        assert_eq!(
            plan.actions,
            [
                DepotFileAction::Directory {
                    path: "/target/bin".into()
                },
                DepotFileAction::Regular {
                    path: "/target/bin/game".into(),
                    size: 10,
                    mode: 0o755
                },
                DepotFileAction::Symlink {
                    path: "/target/link".into(),
                    target: "bin/game".into()
                }
            ]
        );
        assert_eq!(
            plan.chunk_jobs,
            [ChunkWriteJob {
                file_idx: 1,
                chunk_idx: 0
            }]
        );
    }

    #[test]
    fn depot_plan_rejects_cpp_error_cases() {
        let mut manifest = ContentManifest {
            files: vec![crate::content_manifest::FileMapping {
                filename: "../escape".into(),
                ..Default::default()
            }],
            ..Default::default()
        };
        assert_eq!(
            plan_depot_write(&manifest, &[0u8; 32], 1, "/target", 8)
                .unwrap_err()
                .error,
            "write_depot: unsafe path '../escape'"
        );
        manifest.files[0].filename = "ok".into();
        manifest.metadata.filenames_encrypted = true;
        assert_eq!(
            plan_depot_write(&manifest, &[0u8; 32], 1, "/target", 8)
                .unwrap_err()
                .error,
            "write_depot: manifest filenames are still encrypted"
        );
        manifest.metadata.filenames_encrypted = false;
        assert_eq!(
            plan_depot_write(&manifest, &[0u8; 31], 1, "/target", 8)
                .unwrap_err()
                .error,
            "write_depot: bad depot key length"
        );
        assert_eq!(
            plan_depot_write(&manifest, &[0u8; 32], 0, "/target", 8)
                .unwrap_err()
                .error,
            "write_depot: no CDN servers"
        );
    }

    #[test]
    fn worker_clamping_matches_cpp_limits() {
        assert_eq!(clamp_worker_count(0, 10), 1);
        assert_eq!(clamp_worker_count(128, 100), 64);
        assert_eq!(clamp_worker_count(8, 3), 3);
        assert_eq!(clamp_worker_count(8, 0), 0);
    }

    #[test]
    fn creates_layout_and_writes_chunks_at_offsets() {
        let dir = temp_dir("depot_writer_layout");
        let manifest = ContentManifest {
            metadata: crate::content_manifest::Metadata {
                filenames_encrypted: false,
                ..Default::default()
            },
            files: vec![
                crate::content_manifest::FileMapping {
                    filename: "bin".into(),
                    flags: DEPOT_FILE_FLAG_DIRECTORY,
                    ..Default::default()
                },
                crate::content_manifest::FileMapping {
                    filename: "bin/game.dat".into(),
                    size: 6,
                    chunks: vec![ChunkData {
                        offset: 2,
                        cb_original: 3,
                        crc: depot_adler_hash(b"abc"),
                        ..Default::default()
                    }],
                    ..Default::default()
                },
            ],
            signature: Vec::new(),
        };
        let plan = plan_depot_write(&manifest, &[1u8; 32], 1, dir.to_str().unwrap(), 4).unwrap();
        let result = create_depot_layout(&plan);
        assert!(result.ok(), "{}", result.error);
        let file = dir.join("bin/game.dat");
        assert!(file.exists());

        assert_eq!(write_chunk_at(&file, 2, b"abc").unwrap(), 3);
        assert!(existing_chunk_matches(&file, &manifest.files[1].chunks[0]));
        assert!(!existing_chunk_matches(
            &file,
            &ChunkData {
                offset: 2,
                cb_original: 3,
                crc: 1,
                ..Default::default()
            }
        ));
        assert!(sync_file(&file));
        finalize_regular_file(&file, 6).unwrap();
        assert_eq!(fs::metadata(&file).unwrap().len(), 6);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn process_and_write_chunk_decrypts_and_materializes_bytes() {
        let dir = temp_dir("depot_writer_process_chunk");
        let file = dir.join("content.bin");
        let key = [9u8; 32];
        let payload = b"materialized chunk";
        let raw = encrypted_stored_zip_chunk(&key, payload);
        let chunk = ChunkData {
            offset: 4,
            cb_original: payload.len() as u32,
            crc: depot_adler_hash(payload),
            ..Default::default()
        };

        assert_eq!(
            process_and_write_chunk(&file, &chunk, &raw, &key).unwrap(),
            payload.len() as u64
        );
        assert!(existing_chunk_matches(&file, &chunk));
        let mut bytes = Vec::new();
        File::open(&file).unwrap().read_to_end(&mut bytes).unwrap();
        assert_eq!(&bytes[4..], payload);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn sequential_write_handles_layout_only_manifest() {
        let dir = temp_dir("depot_writer_sequential_layout");
        let manifest = ContentManifest {
            metadata: crate::content_manifest::Metadata {
                filenames_encrypted: false,
                depot_id: 7,
                ..Default::default()
            },
            files: vec![
                crate::content_manifest::FileMapping {
                    filename: "empty.bin".into(),
                    size: 5,
                    ..Default::default()
                },
                crate::content_manifest::FileMapping {
                    filename: "folder".into(),
                    flags: DEPOT_FILE_FLAG_DIRECTORY,
                    ..Default::default()
                },
            ],
            signature: Vec::new(),
        };
        let server = CContentServerDirectoryServerInfo {
            host: "cdn.example".into(),
            https_support: "mandatory".into(),
            ..Default::default()
        };
        let result = write_depot_sequential(
            &manifest,
            &[3u8; 32],
            &CdnClient::new(""),
            &[server],
            dir.to_str().unwrap(),
            DepotWriteOptions::default(),
        );
        assert!(result.ok(), "{}", result.error);
        assert_eq!(result.files_written, 1);
        assert_eq!(result.bytes_written, 0);
        assert_eq!(fs::metadata(dir.join("empty.bin")).unwrap().len(), 5);
        assert!(dir.join("folder").is_dir());
        let _ = fs::remove_dir_all(&dir);
    }

    fn temp_dir(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "wnsteam_{name}_{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ))
    }

    fn encrypted_stored_zip_chunk(key: &[u8; 32], payload: &[u8]) -> Vec<u8> {
        let mut zip = Vec::new();
        zip.extend_from_slice(&0x0403_4b50u32.to_le_bytes());
        zip.extend_from_slice(&20u16.to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes());
        zip.extend_from_slice(&0u32.to_le_bytes());
        zip.extend_from_slice(&(payload.len() as u32).to_le_bytes());
        zip.extend_from_slice(&(payload.len() as u32).to_le_bytes());
        zip.extend_from_slice(&1u16.to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes());
        zip.extend_from_slice(b"x");
        zip.extend_from_slice(payload);

        let iv = [6u8; AES_BLOCK_BYTES];
        let wrapped = aes256_ecb_encrypt_block(key, &iv).unwrap();
        let body = aes256_cbc_encrypt(key, &iv, &zip).unwrap();
        let mut out = wrapped.to_vec();
        out.extend_from_slice(&body);
        out
    }
}
