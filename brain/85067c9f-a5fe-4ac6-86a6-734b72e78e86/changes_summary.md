# Custom Proton Fix & Pipetto Integration Summary

## Critical Bug Fixed: "Unable to install system files" for Custom Protons

### Root Cause
When creating a container with a custom-installed Proton (e.g., one downloaded from the Components section), the system failed because:

1. **`WineInfo.fromIdentifier()` couldn't parse custom proton identifiers** — Custom protons use entry names like `Proton-9.0-1` (uppercase "P", version code suffix instead of architecture). The regex `^(wine|proton)\-([0-9\.]+)\-?([0-9\.]+)?\-(x86|x86_64|arm64ec)$` requires lowercase and an architecture suffix, so custom protons never matched.

2. **Fallback used wrong path** — When the regex failed, `WineInfo` fell back to `MAIN_WINE_VERSION` with the default path (`/opt/proton-9.0-x86_64`), which is wrong for custom protons that are installed in a different directory.

3. **`ContainerManager.extractContainerPatternFile()` used wrong source for prefix pack** — The old code used `wineInfo.path` (which was wrong due to issue #1) to find the `prefixPack.txz` file, so the extraction always failed.

### Files Changed

#### 1. `ContainerManager.java` — Container pattern extraction fix
- **Primary fix**: When asset extraction fails, now uses `ContentsManager.getInstallDir()` directly to find the prefix pack instead of relying on `wineInfo.path`
- **Secondary fallback**: Also tries `wineInfo.path` as a secondary fallback for bundled non-asset protons
- **Last resort**: Falls back to `container_pattern_common.tzst` if all other sources fail
- **Resilience**: `extractCommonDlls` failure no longer aborts the entire container creation

#### 2. `WineInfo.java` — Identifier parsing fix
- Now attempts case-insensitive regex matching first
- For custom protons with version codes (e.g., `Proton-9.0-1`), constructs a normalized identifier (`proton-9.0-x86_64`) for regex matching
- **New fallback path**: When regex fails but a valid `ContentProfile` exists, constructs `WineInfo` directly from profile metadata instead of falling back to `MAIN_WINE_VERSION`
- Detects arm64ec from profile's `wineLibPath` when available

## Pipetto Controller/Input Fixes Status

All Pipetto controller fixes were **already integrated** in prior sessions:

| Fix | Status |
|-----|--------|
| Per-controller `remappedState` in `ExternalController` | ✅ Already present |
| `handleInputEvent(controller, ...)` signatures in `InputControlsView` | ✅ Already present |
| `processTriggerInput()` method | ✅ Already present |
| `handleStickInput()` method | ✅ Already present |
| `sendGamepadState(controller)` per-device in `WinHandler` | ✅ Already present |
| State copying fix (no longer merging virtual/physical state) | ✅ Already present |
| Null safety in `InputControlsManager` | ✅ Already present |
| Action bar null check in `InputControlsFragment` | ✅ Already present |
| Virtual gamepad detection in `ControlsProfile` | ✅ Already present |
