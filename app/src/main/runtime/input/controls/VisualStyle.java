package com.winlator.cmod.runtime.input.controls;

/** Rendering styles for the on-screen controls; enum order is the picker order and drawing branches on this in {@link ControlElement#draw}. */
public enum VisualStyle {
  SLATE,
  GAMEHUB,
  HALO,
  GLINT,
  SHADOW,
  RETICLE,
  NEON,
  LUMINA,
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
    return new String[] {"Slate", "Glass", "Halo", "Glint", "Shadow", "Reticle", "Neon", "Lumina", "Original"};
  }
}
