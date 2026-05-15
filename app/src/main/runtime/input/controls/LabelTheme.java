package com.winlator.cmod.runtime.input.controls;

import android.graphics.Color;

/**
 * Label/color overlay applied at draw time for gamepad bindings.
 *
 * <p>DEFAULT — no overlay; element keeps its own text and {@code customColor}.
 *
 * <p>XBOX — A=green, B=red, X=blue, Y=yellow plus LT/LB/RT/RB/L3/R3 labels.
 *
 * <p>PLAYSTATION — Cross/Circle/Square/Triangle glyphs with PlayStation accent colors plus
 * L1/L2/R1/R2/L3/R3 labels.
 *
 * <p>Per-element {@code customColor} always wins over the theme so user edits aren't clobbered.
 */
public enum LabelTheme {
  DEFAULT,
  XBOX,
  PLAYSTATION;

  public static LabelTheme fromPreference(String name) {
    if (name == null) return DEFAULT;
    try {
      return LabelTheme.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT;
    }
  }

  public static String[] displayNames() {
    return new String[] {"Default", "Xbox", "PlayStation"};
  }

  /** Returns the override color for a binding, or {@code 0} if this theme doesn't override it. */
  public int colorFor(Binding binding) {
    if (this == DEFAULT || binding == null) return 0;
    switch (this) {
      case XBOX:
        switch (binding) {
          case GAMEPAD_BUTTON_A:
            return 0xFF22A348; // Xbox green
          case GAMEPAD_BUTTON_B:
            return 0xFFE13C3C; // Xbox red
          case GAMEPAD_BUTTON_X:
            return 0xFF1976D2; // Xbox blue
          case GAMEPAD_BUTTON_Y:
            return 0xFFE9B007; // Xbox yellow
          default:
            return 0;
        }
      case PLAYSTATION:
        switch (binding) {
          case GAMEPAD_BUTTON_A:
            return 0xFF5C8DEC; // Cross — blue
          case GAMEPAD_BUTTON_B:
            return 0xFFE34A4A; // Circle — red
          case GAMEPAD_BUTTON_X:
            return 0xFFD66ED1; // Square — pink/magenta
          case GAMEPAD_BUTTON_Y:
            return 0xFF34BFA0; // Triangle — teal
          default:
            return 0;
        }
      default:
        return 0;
    }
  }

  /** Returns the override label for a binding, or {@code null} if no override. */
  public String labelFor(Binding binding) {
    if (this == DEFAULT || binding == null) return null;
    switch (this) {
      case XBOX:
        switch (binding) {
          case GAMEPAD_BUTTON_A:
            return "A";
          case GAMEPAD_BUTTON_B:
            return "B";
          case GAMEPAD_BUTTON_X:
            return "X";
          case GAMEPAD_BUTTON_Y:
            return "Y";
          case GAMEPAD_BUTTON_L1:
            return "LB";
          case GAMEPAD_BUTTON_R1:
            return "RB";
          case GAMEPAD_BUTTON_L2:
            return "LT";
          case GAMEPAD_BUTTON_R2:
            return "RT";
          case GAMEPAD_BUTTON_L3:
            return "L3";
          case GAMEPAD_BUTTON_R3:
            return "R3";
          case GAMEPAD_BUTTON_START:
            return "≡";
          case GAMEPAD_BUTTON_SELECT:
            return "❐";
          default:
            return null;
        }
      case PLAYSTATION:
        switch (binding) {
          case GAMEPAD_BUTTON_A:
            return "✕";
          case GAMEPAD_BUTTON_B:
            return "○";
          case GAMEPAD_BUTTON_X:
            return "▢";
          case GAMEPAD_BUTTON_Y:
            return "△";
          case GAMEPAD_BUTTON_L1:
            return "L1";
          case GAMEPAD_BUTTON_R1:
            return "R1";
          case GAMEPAD_BUTTON_L2:
            return "L2";
          case GAMEPAD_BUTTON_R2:
            return "R2";
          case GAMEPAD_BUTTON_L3:
            return "L3";
          case GAMEPAD_BUTTON_R3:
            return "R3";
          case GAMEPAD_BUTTON_START:
            return "≡";
          case GAMEPAD_BUTTON_SELECT:
            return "❐";
          default:
            return null;
        }
      default:
        return null;
    }
  }

  /** Convenience for callers that don't want to deal with the 0 sentinel. */
  public boolean overridesColor(Binding binding) {
    return colorFor(binding) != 0;
  }

  /** Returns {@link Color#WHITE} as a neutral fallback when {@link #colorFor} returns 0. */
  public static int safeColor(int themedColor, int fallback) {
    return themedColor != 0 ? themedColor : fallback;
  }
}
