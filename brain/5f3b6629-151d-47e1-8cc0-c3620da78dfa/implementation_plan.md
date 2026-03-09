# Controller Input Migration from Vivsi1/Winlator Bionic

Migrate the fake evdev controller input system from Vivsi1/winlator bionic branch to WinNative, replacing the broken UDP-based gamepad state protocol with the new fake evdev file approach while preserving WinNative's existing UI and extra features (gyro, deadzone, sensitivity).

## Architecture Overview

**Vivsi1's approach** (working):
```
Android Gamepad → ExternalController → GamepadState → WinHandler.sendGamepadState()
→ FakeInputWriter.writeGamepadState() → writes evdev events to /dev/input/event{0-3}
→ libfakeinput.so (LD_PRELOAD) hooks open/ioctl/read to redirect Wine to fake files
→ Wine sees real Linux gamepad devices
```

**WinNative's current approach** (broken):
```
Android Gamepad → ExternalController → GamepadState → WinHandler.sendGamepadState()
→ UDP packet via winhandler protocol → Wine winhandler.exe → XInput/DInput
```

The key difference: Vivsi1 creates fake Linux input devices that Wine reads directly via `evdev`, bypassing the UDP winhandler protocol entirely for gamepad input.

## User Review Required

> [!IMPORTANT]
> The existing gyro, deadzone, sensitivity, and square deadzone features in `ExternalController.java` will be **preserved** — they are applied before the gamepad state reaches `FakeInputWriter`. The touch screen controller (virtual gamepad) will also continue to work via the same `FakeInputWriter` path. No UI changes are made.

> [!WARNING]
> The old UDP-based `GET_GAMEPAD`/`GET_GAMEPAD_STATE` request-response protocol in `WinHandler` will be effectively **bypassed** for gamepad data (mouse/keyboard events still use UDP). The `gamepadClients` and `sendGamepadState()` over UDP are kept for backwards compatibility but the primary controller path becomes fake evdev.

## Proposed Changes

### Native C++ — Fake Input Hook Library

#### [NEW] [fakeinput.cpp](file:///home/max/Build/Emulator/WinNative/app/src/main/cpp/winlator/fakeinput.cpp)
- Copy Vivsi1's `fakeinput.cpp` verbatim — it's a standalone LD_PRELOAD library that hooks `open`, `openat`, `stat`, `fstat`, `ioctl`, `scandir`, `inotify_add_watch`, `close`, `read` to redirect `/dev/input/event*` to `$FAKE_EVDEV_DIR/event*` and emulate evdev ioctl responses (device ID, name, capabilities, abs info, etc.).

---

### CMake Build Configuration

#### [MODIFY] [CMakeLists.txt](file:///home/max/Build/Emulator/WinNative/app/src/main/cpp/CMakeLists.txt)
- Add the `fakeinput` shared library target after the `winlator` library, exactly as Vivsi1 does:
  - `add_library(fakeinput SHARED winlator/fakeinput.cpp)`
  - Link against `log` and `dl`
  - Set `-fvisibility=hidden` compile option

---

### Java — Fake Input Writer

#### [NEW] [FakeInputWriter.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/inputcontrols/FakeInputWriter.java)
- Copy Vivsi1's `FakeInputWriter.java` — writes Linux `input_event` structs (24 bytes: timestamp + type + code + value) to fake event files. Handles buttons, sticks, triggers, D-pad with change detection and SYN_REPORT batching.

---

### Java — WinHandler Multi-Controller Support

#### [MODIFY] [WinHandler.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/winhandler/WinHandler.java)

Key changes:
1. **Add `FakeInputWriter` array and multi-controller slot management** — fields for `writers[4]`, `deviceToSlot`, `usedSlots`, `fakeInputBasePath` (from Vivsi1)
2. **Add `InputManager` listener** for device disconnection → slot release
3. **Add `setFakeInputPath()`** — accepts the fake input directory path
4. **Rewrite `sendGamepadState()`** — write to `FakeInputWriter` instead of only UDP. Keep UDP as secondary path for legacy compatibility
5. **Add `sendGamepadState(ExternalController)`** overload that writes to fake evdev
6. **Rewrite `onGenericMotionEvent()`** and `onKeyEvent()`** — handle multi-controller (use `controllers` map instead of single `currentController`)
7. **Add `closeFakeInputWriter()`** — cleanup on stop
8. **Preserve all gyro functionality** — gyro still modifies thumbRX/thumbRY before writing state

---

### Java — GuestProgramLauncherComponent (libfakeinput.so + LD_PRELOAD)

#### [MODIFY] [GuestProgramLauncherComponent.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/xenvironment/components/GuestProgramLauncherComponent.java)

Add to `execGuestProgram()` after the existing `LD_PRELOAD` setup:
1. **Copy `libfakeinput.so`** from APK's native lib dir to imagefs lib dir (if not already there)
2. **Append** `libfakeinput.so` path to `LD_PRELOAD`
3. **Create `/dev/input` directory** and seed `event0` file
4. **Set `FAKE_EVDEV_DIR`** environment variable pointing to the dev/input dir

---

### Java — ExternalController (Minor Alignment)

#### [MODIFY] [ExternalController.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/inputcontrols/ExternalController.java)

- Keep all existing deadzone/sensitivity/gyro/square deadzone features
- Keep `triggerType` handling (WinNative's extra feature)
- No functional changes needed — the existing `state` object is already compatible with `FakeInputWriter.writeGamepadState()`

---

### Java — GamepadState (No Changes)

#### [KEEP] [GamepadState.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/inputcontrols/GamepadState.java)
- Already compatible — has `thumbLX/LY/RX/RY`, `triggerL/R`, `dpad[]`, `buttons`, `isPressed()`, `setPressed()` which are exactly what `FakeInputWriter.writeGamepadState()` reads.

---

### Assets — input_dlls.tzst

#### [KEEP] input_dlls.tzst
- Already present in WinNative and extracted via `extractInputDLLs()`. No changes needed.

## Verification Plan

### Build Verification
- Run `./gradlew assembleDebug` from `/home/max/Build/Emulator/WinNative` to verify the project builds successfully with the new `fakeinput` native library and all Java changes.

### Code Comparison Verification
- After implementation, diff every modified/new file against the Vivsi1 reference to confirm nothing is missing. Compare:
  - `fakeinput.cpp` — should match Vivsi1 exactly
  - `FakeInputWriter.java` — should match Vivsi1 exactly
  - `WinHandler.java` — verify all Vivsi1 multi-controller/FakeInputWriter code is present
  - `GuestProgramLauncherComponent.java` — verify libfakeinput.so copy, LD_PRELOAD, and FAKE_EVDEV_DIR setup
  - `CMakeLists.txt` — verify fakeinput library target is present

### Manual Testing (User)
- Install the debug APK on an Android device
- Connect a physical Bluetooth/USB controller
- Launch a game that supports controller input (e.g., a Steam game)
- Verify: all buttons, both sticks, triggers, and D-pad work correctly
- Verify: touch screen virtual gamepad still works
- Verify: no UI changes occur in the controller settings/bindings screens
