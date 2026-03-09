# Controller Input Issue Analysis

## Key Findings from Logcat

### 1. Touch Events ARE Reaching Android
The OS `PowerManagerService` shows continuous `event=touch` entries — meaning your screen touches **are** detected by Android.

### 2. **No `InputControlsView` Logs At All**
Despite `InputControlsView` having `Log.d()` calls in `onGenericMotionEvent`, `onTouchEvent`, and `dispatchGenericMotionEvent`, **NONE of these appear in the log**. This means the `InputControlsView` is **never receiving any events**.

### 3. The Root Cause: `InputControlsView` Starts as `View.GONE`

The `inputControlsView` is initialized as `GONE` at [line 1387](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java#L1387):
```java
inputControlsView.setVisibility(View.GONE);
```

It only becomes `VISIBLE` if:
- A shortcut has a `controlsProfile` extra set → [line 1426-1430](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java#L1425-L1430)
- `simulateConfirmInputControlsDialog()` finds a valid `selected_profile_index` in preferences → [line 1609-1614](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java#L1609-L1614)
- The user manually opens the Input Controls dialog from the drawer and selects a profile

### 4. The "Some Games" Mystery Explained
Games launched with a `controlsProfile` in their shortcut get touch controls. Games launched **without** a `controlsProfile` shortcut extra get **no touch controls** — the `InputControlsView` stays `GONE`.

### 5. App Crash
The app also crashed (`Process com.winlator.cmod (pid 14158) has died`) while a game (Dinkum.exe) was running. The `UnityCrashHandler64.exe` also died, suggesting the game itself crashed.

### 6. `dispatchGenericMotionEvent` Gap
In `XServerDisplayActivity.dispatchGenericMotionEvent()` ([line 1812-1841](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java#L1812-L1841)), motion events go to:
- `winHandler.onGenericMotionEvent()` — for XInput gamepad passthrough
- `touchpadView.onExternalMouseEvent()` — for mouse devices
- `super.dispatchGenericMotionEvent()` — system handling

But **it does NOT forward motion events to `inputControlsView`**! So even if a physical gamepad is connected and the `InputControlsView` has a profile, joystick motion events from the activity level never reach the `InputControlsView.onGenericMotionEvent()`.

## Summary of Bugs

| # | Bug | Impact |
|---|-----|--------|
| 1 | `inputControlsView` starts GONE and is never activated unless shortcut has `controlsProfile` or user manually opens dialog | **Touch controls don't work for games without a profile pre-assigned** |
| 2 | `dispatchGenericMotionEvent` doesn't forward events to `inputControlsView` | **Physical gamepad joystick events never reach the input controls binding system** |
| 3 | `simulateConfirmInputControlsDialog` defaults `show_touchscreen_controls_enabled` to `false` | Touch screen buttons are hidden even when profile is active |
