# Advanced Container & Steam Integration

This plan outlines the next set of features and bug fixes requested: revamping the Game Settings UI to operate on existing containers, fixing the remaining x86_64 "Starting..." boot hang, and porting GameNative's Steamless DRM and Steam Client logic into the app.

## Proposed Changes

### 1. Game Settings UI Overhaul

**Problem:** When users click "Game Settings" on a game, the "Wine Version" dropdown lists raw Wine/Proton packages. Saving this creates a new container or modifies a generic one. The user wants the Game Settings "Wine Version" dropdown to actually list *existing user-created containers*, allowing them to bind a game to a specific container and override its settings.

#### [MODIFY] [ContainerDetailFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/ContainerDetailFragment.java)
- Update `loadWineVersionSpinner`: If `isShortcutMode()` is true, clear the `wineVersions` array from listing standard `wine_entries` and instead *only* list existing `manager.getContainers()`.
- Update `saveContainerData`: When saving a Shortcut, parse the selected container from `sWineVersion`, assign the `shortcut.container_id` to it, and do not attempt to run `manager.createContainerAsync()` or search for matching Wine versions. Simply update the Shortcut file so that the game now boots using the selected Container.

### 2. x86_64 Boot Hang Fix

**Problem:** Standard `x86_64` containers are hanging at "Starting..." while `arm64ec` containers boot fine. The previous fixes enabled WoW64 hook DLLs, but `x86_64` containers use `Box86`/`Box64` directly to wrap traditional Wine, unlike `Arm64EC` which uses WoW64/FEX. The execution flow is getting stuck.

#### [MODIFY] [GuestProgramLauncherComponent.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/xenvironment/components/GuestProgramLauncherComponent.java)
- Investigate the `box64` command generation for `x86_64` containers. The logic `imageFs.getBinDir() + "/box64 " + guestExecutable` might be hanging or missing environment variables (`BOX64_LOG`, `BOX64_DYNAREC_LOG`).
- Verify `wine_startmenu.json` and `vulkan` configurations.
- Re-check `/home/xuser/tmp` mounts and shared memory permissions which historically caused hangs.

### 3. Steam Client & Steamless DRM Integration

**Problem:** The app needs to support installing `steam.tzst` and implementing Steamless/Goldberg DLL replacement for Steam games, just like GameNative.

#### [NEW] `SteamService.kt` integration
- Port `SteamService.downloadSteam`, `fetchFileWithFallback`, and `downloadImageFsPatches` from GameNative to download `steam.tzst` and `imagefs_patches_gamenative.tzst`.

#### [MODIFY] [XServerDisplayActivity.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java) / [XServerScreen.kt](file:///home/max/Build/Emulator/Reference/GameNative-Performance/app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt)
- Add logic in Wine initialization to detect if `container.isLaunchRealSteam` is true, and if so, extract `steam.tzst` into `C:\Program Files (x86)\Steam`.
- Port GameNative's DRM handling: If `isUseLegacyDRM` is enabled and `orig_dll_path.txt` isn't handled:
  - Create `steamless_wrapper.bat` in `z:\\tmp\\` to run `Steamless.CLI.exe` on the game executable.
  - Generate the `steam_interfaces.txt` mapping for Goldberg Emulator.
- To implement this cleanly in Java/existing Kotlin structure, we'll adapt the `unpackExecutableFile` function from `XServerScreen.kt` and invoke it before launching the primary `guestExecutable`.

## Verification Plan
1. Check that "Game Settings" for a game correctly lists only your created containers.
2. Select a container, save, and verify the game boots inside that container using the settings applied.
3. Boot an x86_64 container and verify it reaches the desktop.
4. Download Steam components and verify Steam launches cleanly.
