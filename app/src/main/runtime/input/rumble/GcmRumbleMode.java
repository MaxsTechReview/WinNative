package com.winlator.cmod.runtime.input.rumble;

public enum GcmRumbleMode {
  /** Off; use the normal InputDevice/system vibrator. */
  DISABLED,
  /** Known GameSir models only: G8+ MFi (USB), X5s & X3 Pro (BLE). */
  KNOWN,
  /** Any GameSir device (experimental). */
  ALL;

  public static final String PREF_KEY = "gcm_rumble_mode";

  public static GcmRumbleMode fromPrefValue(String value) {
    if (value == null) return DISABLED;
    switch (value) {
      case "known":
        return KNOWN;
      case "all":
        return ALL;
      default:
        return DISABLED;
    }
  }

  public String toPrefValue() {
    switch (this) {
      case DISABLED:
        return "disabled";
      case ALL:
        return "all";
      default:
        return "known";
    }
  }
}
