use crate::content_manifest::{ContentManifest, FileMapping};
use crate::depot_config::{DepotConfigStore, INVALID_MANIFEST_ID};
use crate::depot_downloader::ResolvedDepotSpec;
use crate::depot_writer::DEPOT_FILE_FLAG_DIRECTORY;
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};

const STALE_CLEANUP_SUFFIX: &str = ".stalecleanup";

fn cleanup_log(message: &str) {
    crate::jni::android_log("WnSteamDepotCleanup", message);
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
/// manifest of the app. Files WinNative itself adds (steam_settings/,
/// .DepotDownloader/, *.original.exe backups, saves) appear in no manifest and
/// can never become candidates. If the keep-union cannot be built completely
/// (missing depot key or unreadable cached manifest), the whole pass aborts
/// and markers are kept for a later attempt.
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

    let keys: BTreeMap<u32, &Vec<u8>> = depots
        .iter()
        .map(|depot| (depot.depot_id, &depot.depot_key))
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
        let Some(key) = keys.get(&depot_id) else {
            cleanup_log(&format!(
                "cleanup: no key for installed depot {depot_id}, deferring stale-file pass"
            ));
            return 0;
        };
        let Some(files) = load_manifest_files(config_dir, depot_id, manifest_id, key) else {
            cleanup_log(&format!(
                "cleanup: cannot read manifest {depot_id}_{manifest_id}, deferring stale-file pass"
            ));
            return 0;
        };
        for file in &files {
            keep.insert(file.filename.to_ascii_lowercase());
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
        let Some(key) = keys.get(&depot_id) else {
            cleanup_log(&format!(
                "cleanup: no key for depot {depot_id}, dropping marker {depot_id}_{old_gid}"
            ));
            remove_cleanup_marker(config_dir, depot_id, old_gid);
            continue;
        };
        let Some(files) = load_manifest_files(config_dir, depot_id, old_gid, key) else {
            cleanup_log(&format!(
                "cleanup: old manifest {depot_id}_{old_gid} unavailable, dropping marker"
            ));
            remove_cleanup_marker(config_dir, depot_id, old_gid);
            continue;
        };

        let mut dirs = BTreeSet::new();
        for file in &files {
            if keep.contains(&file.filename.to_ascii_lowercase()) {
                continue;
            }
            let Some(rel) = sanitized_relative(&file.filename) else {
                cleanup_log(&format!(
                    "cleanup: refusing unsafe manifest path '{}'",
                    file.filename
                ));
                continue;
            };
            let path = install_root.join(rel);
            if (file.flags & DEPOT_FILE_FLAG_DIRECTORY) != 0 {
                dirs.insert(path);
                continue;
            }
            if delete_stale_file(&path) {
                cleanup_log(&format!(
                    "cleanup: deleted '{}' (depot {depot_id}, gone after manifest {old_gid})",
                    path.display()
                ));
                deleted += 1;
            }
            // Steamless backs up patched exes as "<name>.original.exe";
            // restoreOriginalExecutable would resurrect a deleted exe from an
            // orphaned backup, so the backup goes with its primary.
            let backup = sibling_original_backup(&path);
            if delete_stale_file(&backup) {
                cleanup_log(&format!("cleanup: deleted backup '{}'", backup.display()));
                deleted += 1;
            }
            if let Some(parent) = path.parent() {
                if parent != install_root {
                    dirs.insert(parent.to_path_buf());
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

fn load_manifest_files(
    config_dir: &Path,
    depot_id: u32,
    manifest_id: u64,
    depot_key: &[u8],
) -> Option<Vec<FileMapping>> {
    let path = config_dir.join(format!("{depot_id}_{manifest_id}.manifest"));
    let raw = fs::read(path).ok()?;
    if raw.is_empty() {
        return None;
    }
    let mut manifest = ContentManifest::parse(&raw)?;
    manifest
        .decrypt_filenames(depot_key)
        .then_some(manifest.files)
}

/// Manifest paths are normalized to '/' separators by decrypt_filenames; only
/// plain relative paths that stay inside the install dir are accepted.
fn sanitized_relative(rel: &str) -> Option<&str> {
    if rel.is_empty() || rel.starts_with('/') || rel.contains(':') {
        return None;
    }
    let mut components = rel.split('/');
    if components
        .clone()
        .any(|part| part.is_empty() || part == "." || part == "..")
    {
        return None;
    }
    if components
        .next()
        .is_some_and(|first| first.eq_ignore_ascii_case(".DepotDownloader"))
    {
        return None;
    }
    Some(rel)
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
    fn keeps_files_that_moved_to_another_depot() {
        let install = temp_dir("multi_depot");
        let config = config_dir(&install);
        write_manifest(&config, 100, 444, &[("shared.dat", 0), ("only_old.dat", 0)]);
        write_manifest(&config, 100, 555, &[("core.dat", 0)]);
        write_manifest(&config, 200, 777, &[("Shared.dat", 0)]);
        install_current(&config, 100, 555);
        install_current(&config, 200, 777);
        touch(&install, "shared.dat");
        touch(&install, "only_old.dat");
        touch(&install, "core.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(
            install.to_str().unwrap(),
            &config,
            &[spec(100, 555), spec(200, 777)],
        );

        assert_eq!(deleted, 1);
        assert!(install.join("shared.dat").exists(), "moved depots keep file");
        assert!(!install.join("only_old.dat").exists());
        assert!(install.join("core.dat").exists());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn missing_old_manifest_drops_marker_without_deleting() {
        let install = temp_dir("missing_old");
        let config = config_dir(&install);
        write_manifest(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        touch(&install, "core.dat");
        touch(&install, "mystery.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 0);
        assert!(install.join("mystery.dat").exists());
        assert!(pending_cleanup_markers(&config).is_empty());
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn incomplete_keep_union_defers_and_keeps_marker() {
        let install = temp_dir("incomplete_union");
        let config = config_dir(&install);
        write_manifest(&config, 100, 444, &[("only_old.dat", 0)]);
        write_manifest(&config, 100, 555, &[("core.dat", 0)]);
        install_current(&config, 100, 555);
        install_current(&config, 200, 777); // installed depot with no cached manifest
        touch(&install, "only_old.dat");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        // Depot 200's manifest cache is missing → whole pass defers.
        let deleted = run_stale_file_cleanup(
            install.to_str().unwrap(),
            &config,
            &[spec(100, 555), spec(200, 777)],
        );
        assert_eq!(deleted, 0);
        assert!(install.join("only_old.dat").exists());
        assert_eq!(pending_cleanup_markers(&config), vec![(100, 444)]);

        // Same when the key for an installed depot is absent from this op.
        write_manifest(&config, 200, 777, &[("dlc.dat", 0)]);
        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);
        assert_eq!(deleted, 0);
        assert_eq!(pending_cleanup_markers(&config), vec![(100, 444)]);
        let _ = fs::remove_dir_all(&install);
    }

    #[test]
    fn rejects_unsafe_manifest_paths() {
        assert_eq!(sanitized_relative("bin/game.exe"), Some("bin/game.exe"));
        assert_eq!(sanitized_relative(""), None);
        assert_eq!(sanitized_relative("/etc/passwd"), None);
        assert_eq!(sanitized_relative("../outside.dat"), None);
        assert_eq!(sanitized_relative("bin/../../outside.dat"), None);
        assert_eq!(sanitized_relative("bin//double.dat"), None);
        assert_eq!(sanitized_relative("c:/windows/system32"), None);
        assert_eq!(sanitized_relative(".DepotDownloader/depot.config"), None);
        assert_eq!(sanitized_relative(".depotdownloader/depot.config"), None);
    }

    #[test]
    fn deletes_steamless_backup_with_its_primary() {
        let install = temp_dir("steamless_backup");
        let config = config_dir(&install);
        write_manifest(&config, 100, 444, &[("old.exe", 0), ("game.exe", 0)]);
        write_manifest(&config, 100, 555, &[("game.exe", 0)]);
        install_current(&config, 100, 555);
        touch(&install, "old.exe");
        touch(&install, "old.exe.original.exe");
        touch(&install, "game.exe");
        touch(&install, "game.exe.original.exe");
        assert!(record_pending_cleanup(&config, 100, 444, 555));

        let deleted = run_stale_file_cleanup(install.to_str().unwrap(), &config, &[spec(100, 555)]);

        assert_eq!(deleted, 2);
        assert!(!install.join("old.exe").exists());
        assert!(!install.join("old.exe.original.exe").exists());
        assert!(install.join("game.exe").exists());
        assert!(install.join("game.exe.original.exe").exists());
        let _ = fs::remove_dir_all(&install);
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
