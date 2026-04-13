package com.winlator.cmod.shared.android;

import android.content.IntentSender;
import androidx.annotation.NonNull;

public interface ActivityResultHost {
  void launchWallpaperImagePicker();

  void launchDriveAuthRequest(@NonNull IntentSender intentSender);
}
