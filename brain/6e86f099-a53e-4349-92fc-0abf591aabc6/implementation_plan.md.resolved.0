# Integrating Vivsi1's Controller Improvements

## Goal Description
The objective is to integrate the controller improvements from the `Vivsi1/winlatorvv` fork into the `WinNative` application. This involves changes to input device handling, mouse movement (relative/absolute) toggling, touchpad view support, and controller deadzone/sensitivity processing.

## Proposed Changes

### Input Controls and Layouts
#### [MODIFY] [ExternalController.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/inputcontrols/ExternalController.java)
- Remove the `remappedState` field and any logic separating physical from virtual remapped states, simplifying it back to a single `state`.
- Update `getCenteredAxis` to incorporate Vivsi1's square deadzone logic (`useSquareDeadzoneLeft`, `useSquareDeadzoneRight`) and sensitivity adjustments.
- Retain our trigger processing enhancements if they don't strongly conflict.

#### [MODIFY] [InputControlsManager.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/inputcontrols/InputControlsManager.java)
- Update `exportProfile` to export to `Environment.DIRECTORY_DOWNLOADS + "/Winlator/profiles/"` instead of the standard Winlator path setting.

#### [MODIFY] [InputControlsView.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/widget/InputControlsView.java)
- Simplify `handleInputEvent(Binding binding, boolean isActionDown, float offset)` to not differentiate between `ExternalController` sources when updating state.
- Update `run()` in the mouse movement thread to check `xServer.isRelativeMouseMovement()`.

#### [MODIFY] [TouchpadView.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/widget/TouchpadView.java)
- Replace `isForceMouseControl` references with `isRelativeMouseMovement()` when processing touch events (TouchDown, TouchMove, TouchUp) to determine whether to trigger `mouseEvent` via WinHandler or `injectPointerMove`/`injectPointerButton...` via XServer.
- Simplify middle/secondary button injection.

### XServer and WinHandler Core
#### [MODIFY] [XServer.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/xserver/XServer.java) (Inside cmod/xserver or xserver)
- Add `simulateTouchScreen` flag and getters/setters.
- Update `isForceMouseControl` logic and potentially remove it if `simulateTouchScreen` and `relativeMouseMovement` cover it.

#### [MODIFY] [WinHandler.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/winhandler/WinHandler.java)
- Revert `sendGamepadState(ExternalController controller)` logic if present.
- Simplify `sendGamepadState()` back to using the unified gamepad profile state.

### UI and Settings
#### [MODIFY] [SettingsFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/SettingsFragment.java)
- Remove `CBXTouchscreenToggle` (Touchscreen Mode) and `CBForceMouseControl` from general settings.

#### [MODIFY] [XServerDisplayActivity.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java)
- Add `isRelativeMouseMovement` persistence and extraction from Container/Shortcut intent data.
- Update `setupUI` to apply `setRelativeMouseMovement` directly or with a slight delay post-startup.
- Update `dispatchGenericMotionEvent` to use `xServer.isRelativeMouseMovement()` for primary/secondary/tertiary buttons and scroll wheel input (using `WinHandler.mouseEvent` vs `XServer.inject`).

#### [MODIFY] [ContainerDetailFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/ContainerDetailFragment.java)
- Add `CBRelativeMouseMovement` to UI for toggling relative mouse movement per container.

#### [MODIFY] [ShortcutSettingsDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/ShortcutSettingsDialog.java)
- Add `CBTouchscreenMode` (Simulate Touch Screen) to Shortcut settings parsing and saving.

#### [MODIFY] Layout files
- Modify `input_controls_dialog.xml`, `settings_fragment.xml`, and `shortcut_settings_dialog.xml` to match Vivsi1's UI layout for relative mouse movement and touchscreen mode flags.

## Verification Plan

### Automated Tests
- Build the APK using `assembleDebug` to verify no compile-time errors occur.

### Manual Verification
1. Open WinNative and go to Container Settings. Verify that "Relative Mouse Movement" is available.
2. Go to Global Settings. Verify that Touchscreen Mode is moved to Shortcuts/Input Controls.
3. Launch a container with an external controller connected (Bluetooth or USB).
4. Verify that the controller inputs are detected and registered correctly.
5. Verify that touchpad/mouse movement works correctly depending on the Relative Mouse Movement toggle.
