package com.winlator.cmod.runtime.input.controls;

/**
 * Visual rendering style for on-screen virtual gamepad elements. Enum order is the picker order.
 *
 * <p>SLATE (default) is a flat graphite surface with an accent hairline outline and a bloom glow
 * when pressed. GAMEHUB ("Glass") is a dark translucent glass: light rim, brighter rim and inner
 * fill when pressed, soft drop shadow. HALO keeps a quiet base and lights an inset accent ring on
 * press. GLINT is a neutral frame with a small accent indicator that sweeps into a full ring while
 * held. SHADOW is a soft dark filled look built from layered translucent strokes, with a
 * four-tile D-pad, whose bodies flood with the accent while pressed. ORIGINAL preserves the
 * historic look (translucent white strokes, lightly filled when engaged) and is listed last.
 *
 * <p>The actual drawing branches on this enum inside {@link ControlElement#draw}.
 */
public enum VisualStyle {
  SLATE,
  GAMEHUB,
  HALO,
  GLINT,
  SHADOW,
  ORIGINAL;

  public static VisualStyle fromPreference(String name) {
    if (name == null) return SLATE;
    try {
      return VisualStyle.valueOf(name);
    } catch (IllegalArgumentException e) {
      return SLATE;
    }
  }

  public static String[] displayNames() {
    return new String[] {"Slate", "Glass", "Halo", "Glint", "Shadow", "Original"};
  }
}
