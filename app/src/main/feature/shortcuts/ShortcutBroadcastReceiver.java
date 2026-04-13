package com.winlator.cmod.feature.shortcuts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.winlator.cmod.BuildConfig;
import com.winlator.cmod.R;
import com.winlator.cmod.app.shell.UnifiedActivity;
import com.winlator.cmod.shared.android.AppUtils;

public class ShortcutBroadcastReceiver extends BroadcastReceiver {

  private static final String LOG_TAG = "ShortcutBroadcastReceiver";
  public static final String ACTION_SHORTCUT_ADDED = BuildConfig.APPLICATION_ID + ".SHORTCUT_ADDED";
  public static final String ACTION_PIN_SHORTCUT_RESULT =
      BuildConfig.APPLICATION_ID + ".PIN_SHORTCUT_RESULT";

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (ACTION_PIN_SHORTCUT_RESULT.equals(action)) {
      Log.d(LOG_TAG, "Pinned shortcut confirmed by launcher.");
      AppUtils.showToast(context, R.string.shortcuts_list_added);
      UnifiedActivity.Companion.refreshLibrary();
      return;
    }

    if (ACTION_SHORTCUT_ADDED.equals(action)) {
      boolean isShortcutAdded = intent.getBooleanExtra("shortcut_added", false);
      if (isShortcutAdded) {
        Log.d(LOG_TAG, "Shortcut metadata changed, refreshing library.");
        UnifiedActivity.Companion.refreshLibrary();
      } else {
        Log.d(LOG_TAG, "Shortcut addition failed.");
        AppUtils.showToast(context, R.string.shortcuts_list_failed_add);
      }
      return;
    }

    Log.d(LOG_TAG, "Unexpected broadcast action received.");
  }
}
