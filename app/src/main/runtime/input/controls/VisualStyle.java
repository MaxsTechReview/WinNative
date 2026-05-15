package com.winlator.cmod.runtime.input.controls;

/**
 * Visual rendering style for on-screen virtual gamepad elements.
 *
 * <p>ORIGINAL preserves the historic Winlator look (translucent white strokes, lightly filled when
 * engaged). GAMEHUB mimics the dark glass aesthetic used by the GameHub launcher: dark translucent
 * fill, light white rim, brighter rim and inner fill when pressed, with a soft drop shadow.
 *
 * <p>The actual drawing branches on this enum inside {@link ControlElement#draw}.
 */
public enum VisualStyle {
  ORIGINAL,
  GAMEHUB;

  public static VisualStyle fromPreference(String name) {
    if (name == null) return ORIGINAL;
    try {
      return VisualStyle.valueOf(name);
    } catch (IllegalArgumentException e) {
      return ORIGINAL;
    }
  }

  public static String[] displayNames() {
    return new String[] {"Original", "GameHub"};
  }
}
