package com.winlator.cmod.runtime.system;

import android.content.Context;
import androidx.preference.PreferenceManager;

public final class ApplicationLogGate {
  private static volatile boolean enabled;

  private ApplicationLogGate() {}

  public static void refresh(Context context) {
    if (context == null) return;
    enabled =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("enable_app_debug", false);
  }

  public static void setEnabled(boolean value) {
    enabled = value;
  }

  public static boolean isEnabled() {
    return enabled;
  }
}
