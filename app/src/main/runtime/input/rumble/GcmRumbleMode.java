package com.winlator.cmod.runtime.input.rumble;

public enum GcmRumbleMode {
  /** GCM vibration disabled — system/InputDevice vibrator used as usual. */
  DISABLED,

  /**
   * GCM vibration for known devices only: GameSir G8+ MFi (USB), GameSir G8 SE (USB), GameSir X5s
   * (BLE).
   */
  KNOWN,

  /**
   * GCM vibration for any device with GameSir VID (USB: 0x3537) or any BLE device whose name
   * contains "GameSir" / "Gamesir". Experimental — may not work on all models.
   */
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
