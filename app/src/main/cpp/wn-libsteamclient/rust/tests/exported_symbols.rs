//! Compares `exports.lds` against the snapshotted `exported_symbols.golden`.
//!
//! This is a host-side text-level check that the linker version script is
//! kept in sync with the documented public surface. The actual `.so` symbol
//! diff is part of the on-device verification step.

use std::collections::BTreeSet;
use std::fs;

#[test]
fn exports_lds_covers_golden_set() {
    let lds = fs::read_to_string(concat!(env!("CARGO_MANIFEST_DIR"), "/exports.lds"))
        .expect("exports.lds present");
    let golden = fs::read_to_string(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/tests/exported_symbols.golden"
    ))
    .expect("golden file present");

    let names: BTreeSet<&str> = golden
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty())
        .collect();

    let mut missing: Vec<&str> = Vec::new();
    for n in &names {
        if !lds_pattern_matches(&lds, n) {
            missing.push(n);
        }
    }
    assert!(
        missing.is_empty(),
        "exports.lds does not match these golden symbols: {:?}",
        missing
    );
}

fn lds_pattern_matches(lds: &str, sym: &str) -> bool {
    for line in lds.lines() {
        let line = line.trim().trim_end_matches(';');
        if line.is_empty() {
            continue;
        }
        if line == sym {
            return true;
        }
        if let Some(prefix) = line.strip_suffix('*') {
            if sym.starts_with(prefix) {
                return true;
            }
        }
    }
    false
}
