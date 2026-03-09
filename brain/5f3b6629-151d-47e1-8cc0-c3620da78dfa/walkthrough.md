# Controller Input Migration — Walkthrough

## What Changed

Migrated the fake evdev controller input system from Vivsi1/winlator bionic to WinNative, replacing the broken UDP-based gamepad protocol with direct Linux input device emulation.

## Architecture

```
Android Gamepad → ExternalController → GamepadState
  → WinHandler.sendGamepadState() → FakeInputWriter.writeGamepadState()
  → writes evdev events to /dev/input/event{0-3}
  → libfakeinput.so (LD_PRELOAD) hooks open/ioctl/read
  → Wine sees real Linux gamepad devices
```

## Files Changed (6 total: 2 new, 4 modified)

### New Files

| File | Purpose |
|------|---------|
| [fakeinput.cpp](file:///home/max/Build/Emulator/WinNative/app/src/main/cpp/winlator/fakeinput.cpp) | Native LD_PRELOAD library — hooks `open`, `openat`, `ioctl`, `read`, `close` to redirect Wine's `/dev/input/event*` access to fake evdev files |
| [FakeInputWriter.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/inputcontrols/FakeInputWriter.java) | Writes Linux `input_event` structs (buttons, sticks, triggers, D-pad) to fake device files with change detection |

### Modified Files

| File | Changes |
|------|---------|
| [CMakeLists.txt](file:///home/max/Build/Emulator/WinNative/app/src/main/cpp/CMakeLists.txt) | Added `fakeinput` shared library build target linked against `log` and `dl` |
| [WinHandler.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/winhandler/WinHandler.java) | Rewritten with multi-controller support (4 slots), `FakeInputWriter` integration, auto-discovery via `InputManager`, slot assignment/release. Preserved gyro features. |
| [GuestProgramLauncherComponent.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/xenvironment/components/GuestProgramLauncherComponent.java) | Added: copy `libfakeinput.so` to imagefs, append to `LD_PRELOAD`, create `/dev/input` dir, set `FAKE_EVDEV_DIR` env var |
| [XServerDisplayActivity.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java) | Replaced `initializeController()` with `dev/input` cleanup + `setFakeInputPath()`. Removed unused `controller` field. |

## Key Diffs

### CMakeLists.txt
render_diffs(file:///home/max/Build/Emulator/WinNative/app/src/main/cpp/CMakeLists.txt)

### WinHandler.java
render_diffs(file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/winhandler/WinHandler.java)

### GuestProgramLauncherComponent.java
render_diffs(file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/xenvironment/components/GuestProgramLauncherComponent.java)

### XServerDisplayActivity.java
render_diffs(file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java)

## Preserved Features
- ✅ Gyro input (sensitivity, smoothing, deadzone, trigger activation)
- ✅ Deadzone/sensitivity settings per stick
- ✅ Square deadzone option
- ✅ Trigger type handling (axis/button/both)
- ✅ Xbox controller detection
- ✅ Virtual gamepad (on-screen controls)
- ✅ All UI unchanged

## Verification

- **Code verification**: All 6 files verified — new files match Vivsi1 source, modified files preserve WinNative features
- **Git stats**: 233 insertions, 270 deletions across 4 modified + 2 new files
- **Build**: `assembleDebug` blocked by Gradle dependency resolution (network issue in build environment — not a code issue). User should run `./gradlew assembleDebug` in their own environment.
