package com.winlator.cmod.runtime.input.controls;

/** Accent presets for the themed control styles; a per-element custom color always wins. */
public enum AccentTheme {
  MONO(0xFFE8ECF2),
  AZURE(0xFF4DA3FF),
  CYAN(0xFF37E1DC),
  MINT(0xFF4DE3B2),
  LIME(0xFFB9F542),
  GOLD(0xFFFFC14D),
  EMBER(0xFFFF9950),
  CRIMSON(0xFFFF4655),
  ROSE(0xFFFF6FB5),
  VIOLET(0xFFA78BFA);

  public final int accent;

  AccentTheme(int accent) {
    this.accent = accent;
  }

  public static AccentTheme fromPreference(String name) {
    if (name == null) return CYAN;
    try {
      return AccentTheme.valueOf(name);
    } catch (IllegalArgumentException e) {
      return CYAN;
    }
  }

  public static String[] displayNames() {
    return new String[] {
      "Mono", "Azure", "Cyan", "Mint", "Lime", "Gold", "Ember", "Crimson", "Rose", "Violet"
    };
  }
}
