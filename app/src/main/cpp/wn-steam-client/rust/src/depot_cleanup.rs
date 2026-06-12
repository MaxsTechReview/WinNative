use crate::content_manifest::ContentManifest;
use crate::depot_config::{atomic_write_synced, DepotConfigStore, INVALID_MANIFEST_ID};
use crate::depot_downloader::ResolvedDepotSpec;
use crate::depot_writer::DEPOT_FILE_FLAG_DIRECTORY;
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};

const STALE_CLEANUP_SUFFIX: &str = ".stalecleanup";
const FILELIST_SUFFIX: &str = ".filelist";
const FILELIST_HEADER: &str = "WNFL1";

fn cleanup_log(message: &str) {
    crate::jni::android_log("WnSteamDepotCleanup", message);
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FileEntry {
    pub name: String,
    pub is_dir: bool,
}

pub fn stale_cleanup_marker_name(depot_id: u32, manifest_id: u64) -> String {
    format!("{depot_id}_{manifest_id}{STALE_CLEANUP_SUFFIX}")
}

pub fn stale_cleanup_marker_path(
    config_dir: impl AsRef<Path>,
    depot_id: u32,
    manifest_id: u64,
) -> PathBuf {
    config_dir
        .as_ref()
        .join(stale_cleanup_marker_name(depot_id, manifest_id))
}

pub fn filelist_sidecar_path(
    config_dir: impl AsRef<Path>,
    depot_id: u32,
    manifest_id: u64,
) -> PathBuf {
    config_dir
        .as_ref()
        .join(format!("{depot_id}_{manifest_id}{FILELIST_SUFFIX}"))
}

/// Persists a manifest's decrypted file list next to its cache. The stale-file
/// pass reads these instead of the raw manifests, so it never needs depot keys
/// for depots outside the current download operation (narrowed updates and
/// per-app DLC batches only carry keys for their own depots).
pub fn write_filelist_sidecar(
    config_dir: impl AsRef<Path>,
    depot_id: u32,
    manifest_id: u64,
    manifest: &ContentManifest,
) -> bool {
    let mut blob = String::with_capacity(manifest.files.len() * 32 + 8);
    blob.push_str(FILELIST_HEADER);
    blob.push('\n');
    for file in &manifest.files {
        if file.filename.contains('\n') || file.filename.contains('\r') {
            continue;
        }
        let kind = if (file.flags & DEPOT_FILE_FLAG_DIRECTORY) != 0 {
            'D'
        } else {
            'F'
        };
        blob.push(kind);
        blob.push(' ');
        blob.push_str(&file.filename);
        blob.push('\n');
    }
    atomic_write_synced(
        &filelist_sidecar_path(config_dir, depot_id, manifest_id),
        blob.as_bytes(),
    )
}

/// Writes the sidecar for an already-installed depot that predates sidecars,
/// using the cached manifest and this operation's key. No-op when the sidecar
/// exists or the manifest cache is unreadable.
pub fn backfill_filelist_sidecar(config_dir: &Path, depot: &ResolvedDepotSpec) {
    if filelist_sidecar_path(config_dir, depot.depot_id, depot.manifest_id).is_file() {
        return;
    }
    if let Some(manifest) = load_manifest(
        config_dir,
        depot.depot_id,
        depot.manifest_id,
        &depot.depot_key,
    ) {
        let _ = write_filelist_sidecar(config_dir, depot.depot_id, depot.manifest_id, &manifest);
    }
}

fn read_filelist_sidecar(
    config_dir: &Path,
    depot_id: u32,
    manifest_id: u64,
) -> Option<Vec<FileEntry>> {
    let blob = fs::read_to_string(filelist_sidecar_path(config_dir, depot_id, manifest_id)).ok()?;
    let mut lines = blob.lines();
    if lines.next() != Some(FILELIST_HEADER) {
        return None;
    }
    let mut entries = Vec::new();
    for line in lines {
        let (kind, name) = match (line.get(..2), line.get(2..)) {
            (Some("F "), Some(name)) => (false, name),
            (Some("D "), Some(name)) => (true, name),
            _ => continue,
        };
        entries.push(FileEntry {
            name: name.to_string(),
            is_dir: kind,
        });
    }
    Some(entries)
}

/// Records that a depot is about to move off `old_manifest_id`, so the files
/// the old manifest installed can be diffed away once the new build is fully
/// on disk. The marker survives pause/cancel — cleanup only ever runs after a
/// fully successful download, and a leftover marker is retried then.
pub fn record_pending_cleanup(
    config_dir: impl AsRef<Path>,
    depot_id: u32,
    old_manifest_id: u64,
    new_manifest_id: u64,
) -> bool {
    if old_manifest_id == 0
        || old_manifest_id == INVALID_MANIFEST_ID
        || old_manifest_id == new_manifest_id
    {
        return false;
    }
    let path = stale_cleanup_marker_path(config_dir, depot_id, old_manifest_id);
    let Some(parent) = path.parent() else {
        return false;
    };
    if fs::create_dir_all(parent).is_err() {
        return false;
    }
    fs::write(path, old_manifest_id.to_string()).is_ok()
}

pub fn pending_cleanup_markers(config_dir: impl AsRef<Path>) -> Vec<(u32, u64)> {
    let Ok(entries) = fs::read_dir(config_dir.as_ref()) else {
        return Vec::new();
    };
    let mut markers = BTreeSet::new();
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(name) = name.to_str() else {
            continue;
        };
        let Some(stem) = name.strip_suffix(STALE_CLEANUP_SUFFIX) else {
            continue;
        };
        let mut parts = stem.split('_');
        let (Some(depot), Some(gid), None) = (parts.next(), parts.next(), parts.next()) else {
            continue;
        };
        if let (Ok(depot_id), Ok(manifest_id)) = (depot.parse::<u32>(), gid.parse::<u64>()) {
            markers.insert((depot_id, manifest_id));
        }
    }
    markers.into_iter().collect()
}

fn remove_cleanup_marker(config_dir: impl AsRef<Path>, depot_id: u32, manifest_id: u64) {
    let _ = fs::remove_file(stale_cleanup_marker_path(config_dir, depot_id, manifest_id));
}

/// Deletes game files that the previous manifests installed but no current
/// manifest references — the gap left when a branch switch (or an update that
/// removes files) only ever writes new content.
///
/// Safety model: deletion candidates come exclusively from the OLD manifest of
/// a depot with a pending marker, minus the union of every currently-installed
/// manifest's files (read from filelist sidecars, falling back to cached
/// manifests when this operation holds the depot's key). Files WinNative
/// itself adds (steam_settings/, .DepotDownloader/, *.original.exe backups,
/// saves) appear in no manifest and can never become candidates. The union is
/// best-effort: an installed depot whose file list is unreadable (legacy
/// install predating sidecars, key not in this op) is logged and skipped —
/// the per-depot old-minus-new diff itself never depends on it, which matches
/// the reference DepotDownloader behaviour while sidecars close the gap on
/// every download going forward.
///
/// Known model limitation: a download cancelled mid-write leaves no committed
/// gid to diff against on the next switch, so files unique to the aborted
/// build are not reclaimed (depot.config holds INVALID for it; the prior
/// marker resolves as already-current).
///
/// Returns the number of files deleted.
pub fn run_stale_file_cleanup(
    install_dir: &str,
    config_dir: &Path,
    depots: &[ResolvedDepotSpec],
) -> u32 {
    let markers = pending_cleanup_markers(config_dir);
    if markers.is_empty() {
        return 0;
    }

    let keys: BTreeMap<u32, &[u8]> = depots
        .iter()
        .map(|depot| (depot.depot_id, depot.depot_key.as_slice()))
        .collect();
    let cfg = DepotConfigStore::load(config_dir);

    let mut keep = BTreeSet::new();
    for (depot_id, manifest_id) in cfg.installed_entries() {
        if manifest_id == INVALID_MANIFEST_ID {
            cleanup_log(&format!(
                "cleanup: depot {depot_id} still in progress, deferring stale-file pass"
            ));
            return 0;
        }
        match load_file_entries(
            config_dir,
            depot_id,
            manifest_id,
            keys.get(&depot_id).copied(),
        ) {
            Some(entries) => {
                for entry in &entries {
                    keep.insert(normalized_key(&entry.name));
                }
            }
            None => cleanup_log(&format!(
                "cleanup: no file list for installed depot {depot_id}_{manifest_id}; \
                 protecting with remaining manifests only"
            )),
        }
    }

    let install_root = Path::new(install_dir);
    let mut deleted = 0u32;
    for (depot_id, old_gid) in markers {
        if cfg.installed_manifest(depot_id) == old_gid {
            // Marker for the build that is (again) current — nothing stale.
            remove_cleanup_marker(config_dir, depot_id, old_gid);
            continue;
        }
        let Some(entries) =
            load_file_entries(config_dir, depot_id, old_gid, keys.get(&depot_id).copied())
        else {
            if filelist_sidecar_path(config_dir, depot_id, old_gid).is_file()
                || config_dir
                    .join(format!("{depot_id}_{old_gid}.manifest"))
                    .is_file()
            {
                // The data is on disk but this op can't read it (no key yet);
                // a later op that carries the key finishes the job.
                cleanup_log(&format!(
                    "cleanup: old file list {depot_id}_{old_gid} unreadable in this op, deferring"
                ));
            } else {
                cleanup_log(&format!(
                    "cleanup: old file list {depot_id}_{old_gid} is gone, dropping marker"
                ));
                remove_cleanup_marker(config_dir, depot_id, old_gid);
            }
            continue;
        };

        let mut dirs = BTreeSet::new();
        for entry in &entries {
            let key = normalized_key(&entry.name);
            if keep.contains(&key) {
                continue;
            }
            let Some(parts) = sanitized_components(&entry.name) else {
                cleanup_log(&format!(
                    "cleanup: refusing unsafe manifest path '{}'",
                    entry.name
                ));
                continue;
            };
            if has_symlinked_ancestor(install_root, &parts) {
                cleanup_log(&format!(
                    "cleanup: '{}' is behind a symlinked directory, skipping",
                    entry.name
                ));
                continue;
            }
            let mut path = install_root.to_path_buf();
            path.extend(&parts);
            if entry.is_dir {
                dirs.insert(path);
                continue;
            }
            if delete_stale_file(&path) {
                cleanup_log(&format!(
                    "cleanup: deleted '{}' (depot {depot_id}, gone after manifest {old_gid})",
                    path.display()
                ));
                deleted += 1;
                // Steamless backs up patched exes as "<name>.original.exe";
                // restoreOriginalExecutable would resurrect a deleted exe from
                // an orphaned backup, so the backup goes with its primary —
                // but only an exe's backup, and never one a current manifest
                // legitimately ships.
                if key.ends_with(".exe") && !keep.contains(&format!("{key}.original.exe")) {
                    let backup = sibling_original_backup(&path);
                    if delete_stale_file(&backup) {
                        cleanup_log(&format!("cleanup: deleted backup '{}'", backup.display()));
                        deleted += 1;
                    }
                }
                if let Some(parent) = path.parent() {
                    if parent != install_root {
                        dirs.insert(parent.to_path_buf());
                    }
                }
            }
        }
        for dir in dirs.iter().rev() {
            prune_empty_dirs_up(install_root, dir);
        }
        remove_cleanup_marker(config_dir, depot_id, old_gid);
    }
    if deleted > 0 {
        cleanup_log(&format!(
            "cleanup: removed {deleted} stale file(s) under '{install_dir}'"
        ));
    }
    deleted
}

fn load_manifest(
    config_dir: &Path,
    depot_id: u32,
    manifest_id: u64,
    depot_key: &[u8],
) -> Option<ContentManifest> {
    let path = config_dir.join(format!("{depot_id}_{manifest_id}.manifest"));
    let raw = fs::read(path).ok()?;
    if raw.is_empty() {
        return None;
    }
    let mut manifest = ContentManifest::parse(&raw)?;
    manifest.decrypt_filenames(depot_key).then_some(manifest)
}

/// Sidecar first (key-independent), then the cached manifest when this
/// operation holds the depot key.
fn load_file_entries(
    config_dir: &Path,
    depot_id: u32,
    manifest_id: u64,
    depot_key: Option<&[u8]>,
) -> Option<Vec<FileEntry>> {
    if let Some(entries) = read_filelist_sidecar(config_dir, depot_id, manifest_id) {
        return Some(entries);
    }
    let manifest = load_manifest(config_dir, depot_id, manifest_id, depot_key?)?;
    Some(
        manifest
            .files
            .iter()
            .map(|file| FileEntry {
                name: file.filename.clone(),
                is_dir: (file.flags & DEPOT_FILE_FLAG_DIRECTORY) != 0,
            })
            .collect(),
    )
}

/// Canonical comparison key: separators are already '/' after
/// decrypt_filenames; "."/empty components are dropped (the writer accepts
/// "./a" and "a//b" spellings) and case is folded so both sides of the
/// old-minus-current diff normalize identically.
fn normalized_key(rel: &str) -> String {
    let mut key = String::with_capacity(rel.len());
    for part in rel.split('/') {
        if part.is_empty() || part == "." {
            continue;
        }
        if !key.is_empty() {
            key.push('/');
        }
        for byte in part.bytes() {
            key.push(byte.to_ascii_lowercase() as char);
        }
    }
    key
}

/// Path components safe to delete under the install dir: plain relative
/// paths only; "."/empty components are dropped to mirror normalized_key.
fn sanitized_components(rel: &str) -> Option<Vec<&str>> {
    if rel.starts_with('/') || rel.contains(':') {
        return None;
    }
    let mut parts = Vec::new();
    for part in rel.split('/') {
        if part.is_empty() || part == "." {
            continue;
        }
        if part == ".." || part.bytes().any(|b| b.is_ascii_control()) {
            return None;
        }
        parts.push(part);
    }
    if parts.is_empty() || parts[0].eq_ignore_ascii_case(".DepotDownloader") {
        return None;
    }
    Some(parts)
}

fn delete_stale_file(path: &Path) -> bool {
    let Ok(meta) = fs::symlink_metadata(path) else {
        return false;
    };
    if meta.is_dir() {
        // Manifest called it a file but the disk has a directory — leave it.
        return false;
    }
    fs::remove_file(path).is_ok()
}

fn sibling_original_backup(path: &Path) -> PathBuf {
    let mut name = path.as_os_str().to_os_string();
    name.push(".original.exe");
    PathBuf::from(name)
}

/// Manifest-created symlinks may target arbitrary paths; deleting through one
/// would escape the install dir, so candidates behind a symlinked directory
/// are left alone.
fn has_symlinked_ancestor(install_root: &Path, parts: &[&str]) -> bool {
    let mut current = install_root.to_path_buf();
    for part in &parts[..parts.len().saturating_sub(1)] {
        current.push(part);
        let is_symlink = fs::symlink_metadata(&current)
            .map(|meta| meta.file_type().is_symlink())
            .unwrap_or(false);
        if is_symlink {
            return true;
        }
    }
    false
}

fn prune_empty_dirs_up(install_root: &Path, start: &Path) {
    let mut current = start;
    while current != install_root && current.starts_with(install_root) {
        if fs::remove_dir(current).is_err() {
            break;
        }
        let Some(parent) = current.parent() else {
            break;
        };
        current = parent;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::content_manifest::{END_OF_MANIFEST_MAGIC, METADATA_MAGIC, PAYLOAD_MAGIC};
    use crate::depot_writer::DEPOT_FILE_FLAG_EXECUTABLE;
    use crate::proto_wire::Writer;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp_dir(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "wnsteam_cleanup_{name}_{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn raw_manifest(depot_id: u32, manifest_id: u64, files: &[(&str, u32)]) -> Vec<u8> {
        let mut payload = Vec::new();
        for (filename, flags) in files {
            let mut file_body = Vec::new();
            {
                let mut writer = Writer::new(&mut file_body);
                writer.string_field(1, filename);
                writer.uint64_field(2, 1);
                writer.uint32_field(3, *flags);
            }
            Writer::new(&mut payload).submessage_field(1, &file_body);
        }

        let mut metadata = Vec::new();
        {
            let mut writer = Writer::new(&mut metadata);
            writer.uint32_field(1, depot_id);
            writer.uint64_field(2, manifest_id);
            writer.bool_field_force(4, false);
        }

        let mut raw = Vec::new();
        push_section(&mut raw, PAYLOAD_MAGIC, &payload);
        push_section(&mut raw, METADATA_MAGIC, &metadata);
        raw.extend_from_slice(&END_OF_MANIFEST_MAGIC.to_le_bytes());
        raw
    }

    fn push_section(out: &mut Vec<u8>, magic: u32, body: &[u8]) {
        out.extend_from_slice(&magic.to_le_bytes());
        out.extend_from_slice(&(body.len() as u32).to_le_bytes());
        out.extend_from_slice(body);
    }

    fn write_manifest(config_dir: &Path, depot_id: u32, manifest_id: u64, files: &[(&str, u32)]) {
        fs::write(
            config_dir.join(format!("{depot_id}_{manifest_id}.manifest")),
            raw_manifest(depot_id, manifest_id, files),
        )
        .unwrap();
    }

    fn write_sidecar(config_dir: &Path, depot_id: u32, manifest_id: u64, files: &[(&str, u32)]) {
        let manifest = ContentManifest::parse(&raw_manifest(depot_id, manifest_id, files)).unwrap();
        assert!(write_filelist_sidecar(
            config_dir,
            depot_id,
            manifest_id,
            &manifest
        ));
    }

    fn touch(install: &Path, rel: &str) {
        let path = install.join(rel);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, b"x").unwrap();
    }

    fn spec(depot_id: u32, manifest_id: u64) -> ResolvedDepotSpec {
        ResolvedDepotSpec {
            depot_id,
            manifest_id,
            depot_key: vec![1u8; 32],
            manifest_request_code: 0,
        }
    }

    fn config_dir(install: &Path) -> PathBuf {
        let dir = install.join(".DepotDownloader");
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn install_current(config_dir: &Path, depot_id: u32, manifest_id: u64) {
        let mut cfg = DepotConfigStore::load(config_dir);
        cfg.finish_depot(depot_id, manifest_id);
    }

    #[test]
    fn marker_recording_skips_noop_transitions() {
        let dir = temp_dir("marker_noop");
        assert!(!record_pending_cleanup(&dir, 100, 0, 555));
        assert!(!record_pending_cleanup(&dir, 100, INVALID_MANIFEST_ID, 555));
        assert!(!record_pending_cleanup(&dir, 100, 555, 555));
        assert!(pending_cleanup_markers(&dir).is_empty());

        assert!(record_pending_cleanup(&dir, 100, 444, 555));
        assert_eq!(pending_cleanup_markers(&dir), vec![(100, 444)]);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn filelist_sidecar_roundtrips_files_and_dirs() {
        let dir = temp_dir("sidecar_roundtrip");
        write_sidecar(
            &dir,
            100,
            555,
            &[("bin", DEPOT_FILE_FLAG_DIRECTORY), ("bin/game.exe", 0)],
        );
        let entries = read_filelist_sidecar(&dir, 100, 555).unwrap();
        assert_eq!(
            entries,
            vec![
                FileEntry {
                    name: "bin".into(),
                    is_dir: true
                },
                FileEntry {
                    name: "bin/game.exe".into(),
                    is_dir: false
                },
            ]
        );
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn deletes_files_dropped_by_new_manifest_and_prunes_dirs() {
        let install = temp_dir("branch_switch");
        let config = config_dir(&install);
        write_manifest(
            &config,
            100,
            444,
            &[
                ("bin", DEPOT_FILE_FLAG_DIRECTORY),
                ("bin/old", DEPOT_FILE_FLAG_DIRECTORY),
                ("bin/old/legacy.dll", 0),
                ("bin/game.exe", DEPOT_FILE_FLAG_EXECUTABLE),
                ("data.pak", 0),
            ],
        );
        write_manifest(
            &config,
            100,
            555,
            &[
                ("bin", DEPOT_FILE_FLAG_DIRECTORY),
                ("bin/game.exe", DEPOT_FILE_FLAG_EXECUTABLE),
                ("data.pak", 0),
            ],
        );
        install_current(&config, 100, 555);
        touch(&install, "bin/old/legacy.dll");
        touch(&install, "bin/game.exe");
        touch(&install, "data.pak");
        touch(&install, "steam_settings/configs.app.ini");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 1);
        assert!(!install.join("bin/old/legacy.dll").exists());
        assert!(!install.join("bin/old").exists());
        assert!(install.join("bin/game.exe").exists());
        assert!(install.join("data.pak").exists());
        assert!(install.join("steam_settings/configs.app.ini").exists());
        assert!(install.exists());
        assert!(pending_cleanup_markers(&config).is_empty());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn narrowed_update_cleans_via_sidecars_without_other_depots_keys() {
        // A branch-switch update op carries only the changed depot; the other
        // installed depot is represented by its sidecar alone.
        let install = temp_dir("narrowed_update");
        let config = config_dir(&install);
        write_sidecar(&config, 100, 444, &[("shared.dat", 0), ("only_old.dat", 0)]);
        write_sidecar(&config, 100, 555, &[("core.dat", 0)]);
        write_sidecar(&config, 200, 777, &[("Shared.dat", 0)]);
        install_current(&config, 100, 555);
        install_current(&config, 200, 777);
        touch(&install, "shared.dat");
        touch(&install, "only_old.dat");
        touch(&install, "core.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 1);
        assert!(
            install.join("shared.dat").exists(),
            "depot 200 still ships it"
        );
        assert!(!install.join("only_old.dat").exists());
        assert!(install.join("core.dat").exists());
        assert!(pending_cleanup_markers(&config).is_empty());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn unreadable_old_list_with_data_on_disk_defers_marker() {
        let install = temp_dir("defer_unreadable_old");
        let config = config_dir(&install);
        write_manifest(&config, 100, 444, &[("only_old.dat", 0)]);
        write_sidecar(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        touch(&install, "only_old.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        // Old manifest exists but this op has no key for depot 100 (and no
        // old sidecar) → defer, keep the marker for an op that has the key.
        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(200, 777)]);
        assert_eq!(deleted, 0);
        assert!(install.join("only_old.dat").exists());
        assert_eq!(pending_cleanup_markers(&config), vec![(100, 444)]);

        // Once the data is gone entirely the marker can never act → dropped.
        fs::remove_file(config.join("100_444.manifest")).unwrap();
        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(200, 777)]);
        assert_eq!(deleted, 0);
        assert!(pending_cleanup_markers(&config).is_empty());
        assert!(install.join("only_old.dat").exists());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn unreadable_installed_list_degrades_to_best_effort() {
        let install = temp_dir("best_effort_union");
        let config = config_dir(&install);
        write_sidecar(&config, 100, 444, &[("only_old.dat", 0)]);
        write_sidecar(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        install_current(&config, 200, 777); // no sidecar, no key in op
        touch(&install, "only_old.dat");
        touch(&install, "core.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 1);
        assert!(!install.join("only_old.dat").exists());
        assert!(install.join("core.dat").exists());
        assert!(pending_cleanup_markers(&config).is_empty());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn in_progress_depot_defers_whole_pass() {
        let install = temp_dir("in_progress_defer");
        let config = config_dir(&install);
        write_sidecar(&config, 100, 444, &[("only_old.dat", 0)]);
        write_sidecar(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        let mut cfg = DepotConfigStore::load(&config);
        cfg.begin_depot(200);
        touch(&install, "only_old.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 0);
        assert!(install.join("only_old.dat").exists());
        assert_eq!(pending_cleanup_markers(&config), vec![(100, 444)]);
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn normalization_matches_dotted_and_doubled_separator_spellings() {
        assert_eq!(normalized_key("./Bin//Game.EXE"), "bin/game.exe");
        assert_eq!(normalized_key("bin/game.exe"), "bin/game.exe");

        // Old spelled plainly, new spelled with "./" — still the same file.
        let install = temp_dir("normalized_keep");
        let config = config_dir(&install);
        write_sidecar(&config, 100, 444, &[("bin/x.dll", 0), ("only_old.dat", 0)]);
        write_sidecar(&config, 100, 555, &[("./bin//x.dll", 0)]);
        install_current(&config, 100, 555);
        touch(&install, "bin/x.dll");
        touch(&install, "only_old.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);
        assert_eq!(deleted, 1);
        assert!(install.join("bin/x.dll").exists());
        assert!(!install.join("only_old.dat").exists());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn rejects_unsafe_manifest_paths() {
        assert_eq!(
            sanitized_components("bin/game.exe"),
            Some(vec!["bin", "game.exe"])
        );
        assert_eq!(
            sanitized_components("./bin//game.exe"),
            Some(vec!["bin", "game.exe"])
        );
        assert_eq!(sanitized_components(""), None);
        assert_eq!(sanitized_components("."), None);
        assert_eq!(sanitized_components("/etc/passwd"), None);
        assert_eq!(sanitized_components("../outside.dat"), None);
        assert_eq!(sanitized_components("bin/../../outside.dat"), None);
        assert_eq!(sanitized_components("c:/windows/system32"), None);
        assert_eq!(sanitized_components(".DepotDownloader/depot.config"), None);
        assert_eq!(sanitized_components(".depotdownloader/depot.config"), None);
        assert_eq!(sanitized_components("bad\nname"), None);
    }

    #[test]
    fn deletes_steamless_backup_only_for_unkept_exe_primaries() {
        let install = temp_dir("steamless_backup");
        let config = config_dir(&install);
        write_manifest(
            &config,
            100,
            444,
            &[("old.exe", 0), ("game.exe", 0), ("data.pak", 0)],
        );
        write_manifest(&config, 100, 555, &[("game.exe", 0)]);
        install_current(&config, 100, 555);
        touch(&install, "old.exe");
        touch(&install, "old.exe.original.exe");
        touch(&install, "game.exe");
        touch(&install, "game.exe.original.exe");
        touch(&install, "data.pak");
        touch(&install, "data.pak.original.exe"); // not an exe primary
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 3); // old.exe + its backup + data.pak
        assert!(!install.join("old.exe").exists());
        assert!(!install.join("old.exe.original.exe").exists());
        assert!(install.join("game.exe").exists());
        assert!(install.join("game.exe.original.exe").exists());
        assert!(!install.join("data.pak").exists());
        assert!(
            install.join("data.pak.original.exe").exists(),
            "backup deletion is exe-only"
        );
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn keeps_backup_shipped_by_current_manifest() {
        let install = temp_dir("kept_backup");
        let config = config_dir(&install);
        write_manifest(&config, 100, 444, &[("tool.exe", 0)]);
        write_manifest(
            &config,
            100,
            555,
            &[("tool.exe.original.exe", 0), ("core.dat", 0)],
        );
        install_current(&config, 100, 555);
        touch(&install, "tool.exe");
        touch(&install, "tool.exe.original.exe");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 1);
        assert!(!install.join("tool.exe").exists());
        assert!(
            install.join("tool.exe.original.exe").exists(),
            "current manifest ships this exact name"
        );
        let _ = fs::remove_dir_all(&install);
    }

    #[cfg(unix)]
    #[test]
    fn skips_candidates_behind_symlinked_directories() {
        let install = temp_dir("symlink_ancestor");
        let config = config_dir(&install);
        let outside = temp_dir("symlink_target");
        fs::write(outside.join("precious.dat"), b"keep").unwrap();
        std::os::unix::fs::symlink(&outside, install.join("link")).unwrap();

        write_manifest(&config, 100, 444, &[("link/precious.dat", 0)]);
        write_manifest(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 0);
        assert!(outside.join("precious.dat").exists());
        assert!(install.join("link").exists());
        let _ = fs::remove_dir_all(&install);
        let _ = fs::remove_dir_all(&outside);
    }

    #[test]
    fn marker_for_current_manifest_is_dropped_without_deletions() {
        let install = temp_dir("marker_current");
        let config = config_dir(&install);
        write_manifest(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        touch(&install, "core.dat");
        fs::write(stale_cleanup_marker_path(&config, 100, 555), "555").unwrap();

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 0);
        assert!(install.join("core.dat").exists());
        assert!(pending_cleanup_markers(&config).is_empty());
        let _ = fs::remove_dir_all(&install);
    }
}
