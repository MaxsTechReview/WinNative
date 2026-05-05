package com.winlator.cmod.runtime.input.rumble;

import android.view.InputDevice;

public interface GamepadRumbleDriver {
  String getName();

  boolean supports(InputDevice device, GcmRumbleMode mode);

  void setMode(GcmRumbleMode mode);

  void requestPermissionIfNeeded();

  boolean rumble(int slot, int strong, int weak);
}
