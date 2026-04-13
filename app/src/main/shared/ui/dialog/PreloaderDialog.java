package com.winlator.cmod.shared.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class PreloaderDialog {
  private final Activity activity;
  private Dialog dialog;
  private final Handler uiHandler = new Handler(Looper.getMainLooper());
  private final PreloaderDialogState composeState = new PreloaderDialogState();

  public PreloaderDialog(Activity activity) {
    this.activity = activity;
  }

  private void create() {
    if (dialog != null) return;
    dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.setCancelable(false);
    dialog.setCanceledOnTouchOutside(false);

    Window window = dialog.getWindow();
    if (window != null) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
      window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
      // Edge-to-edge: hide system bars
      WindowCompat.setDecorFitsSystemWindows(window, false);
      window.setFlags(
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
      View decorView = window.getDecorView();
      WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
      controller.hide(WindowInsetsCompat.Type.systemBars());
      controller.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    ComposeView composeView = new ComposeView(activity);
    composeView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    PreloaderDialogContentKt.setupPreloaderComposeView(composeView, composeState, activity);
    dialog.setContentView(composeView);
  }

  private void clearLaunchMetadata() {
    composeState.setGameName("");
    composeState.setPlatform("");
    composeState.setContainerName("");
    composeState.setStableLaunchLayout(false);
  }

  public synchronized void show(int textResId) {
    show(textResId, true);
  }

  public synchronized void show(int textResId, boolean indeterminate) {
    if (dialog == null) create();
    clearLaunchMetadata();
    composeState.setText(activity.getString(textResId));
    composeState.setIndeterminate(indeterminate);
    if (!indeterminate) {
      composeState.setProgress(0);
    }
    if (!isShowing()) dialog.show();
  }

  public synchronized void show(String text) {
    if (dialog == null) create();
    clearLaunchMetadata();
    composeState.setText(text);
    composeState.setIndeterminate(true);
    if (!isShowing()) dialog.show();
  }

  public synchronized void show(
      String text, String gameName, String platform, String containerName) {
    if (dialog == null) create();
    composeState.setText(text);
    composeState.setGameName(gameName != null ? gameName : "");
    composeState.setPlatform(platform != null ? platform : "");
    composeState.setContainerName(containerName != null ? containerName : "");
    composeState.setStableLaunchLayout(true);
    composeState.setIndeterminate(true);
    if (!isShowing()) dialog.show();
  }

  public synchronized void show(
      int textResId, String gameName, String platform, String containerName) {
    show(activity.getString(textResId), gameName, platform, containerName);
  }

  public synchronized void setProgress(int percent) {
    if (dialog == null) return;
    composeState.setProgress(percent);
  }

  public synchronized void setIndeterminate(boolean indeterminate) {
    if (dialog == null) return;
    composeState.setIndeterminate(indeterminate);
  }

  public void setProgressOnUiThread(final int percent) {
    activity.runOnUiThread(() -> setProgress(percent));
  }

  public void setIndeterminateOnUiThread(final boolean indeterminate) {
    activity.runOnUiThread(() -> setIndeterminate(indeterminate));
  }

  public void showOnUiThread(final int textResId) {
    activity.runOnUiThread(() -> show(textResId));
  }

  public void showOnUiThread(final String text) {
    activity.runOnUiThread(() -> show(text));
  }

  public void showOnUiThread(
      final String text, final String gameName, final String platform, final String containerName) {
    activity.runOnUiThread(() -> show(text, gameName, platform, containerName));
  }

  public void showProgressOnUiThread(
      final String text,
      final String gameName,
      final String platform,
      final String containerName,
      final int percent) {
    activity.runOnUiThread(
        () -> {
          show(text, gameName, platform, containerName);
          composeState.setIndeterminate(false);
          composeState.setProgress(percent);
        });
  }

  public void setStepOnUiThread(final String step) {
    activity.runOnUiThread(
        () -> {
          if (dialog != null) composeState.setText(step);
        });
  }

  public void setStepOnUiThread(final int stepResId) {
    setStepOnUiThread(activity.getString(stepResId));
  }

  public void setStepOnUiThread(final int stepResId, Object... formatArgs) {
    setStepOnUiThread(activity.getString(stepResId, formatArgs));
  }

  public synchronized void close() {
    try {
      if (dialog != null) {
        dialog.dismiss();
      }
    } catch (Exception e) {
    }
  }

  public synchronized void closeWithDelay(long delayMs) {
    uiHandler.postDelayed(this::close, delayMs);
  }

  public void closeOnUiThread() {
    activity.runOnUiThread(this::close);
  }

  public boolean isShowing() {
    return dialog != null && dialog.isShowing();
  }
}
