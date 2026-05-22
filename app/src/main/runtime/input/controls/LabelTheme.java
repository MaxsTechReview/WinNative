package com.winlator.cmod.runtime.input.controls;

import android.graphics.Color;
import com.winlator.cmod.runtime.input.ui.InputControlsView;

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
    return new String[] {"Original", "Xbox", "PlayStation"};
  }

  public int colorFor(InputControlsView view, Binding binding) {
    if (this == DEFAULT || binding == null || view == null) return 0;
    InputControlsManager manager = view.getInputControlsManager();
    if (manager == null) return 0;
    
    int profileId = -1;
    if (this == XBOX) {
        profileId = InputControlsManager.LEGACY_XBOX_PROFILE_ID;
    } else if (this == PLAYSTATION) {
        profileId = InputControlsManager.LEGACY_PS_PROFILE_ID;
    }

    if (profileId != -1) {
        ControlsProfile p = manager.getProfile(profileId);
        if (p != null) {
            if (!p.isElementsLoaded()) p.loadElements(view);
            int color = p.findColorForBinding(binding);
            if (color != -1) return color;
        }
    }

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

  public boolean overridesColor(InputControlsView view, Binding binding) {
    return colorFor(view, binding) != 0;
  }

  public static int safeColor(int themedColor, int fallback) {
    return themedColor != 0 ? themedColor : fallback;
  }
}
