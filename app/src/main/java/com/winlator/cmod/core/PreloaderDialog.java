package com.winlator.cmod.core;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.compose.ui.platform.ComposeView;

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
        }

        ComposeView composeView = new ComposeView(activity);
        composeView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        PreloaderDialogContentKt.setupPreloaderComposeView(composeView, composeState, activity);
        dialog.setContentView(composeView);
    }

    public synchronized void show(int textResId) {
        show(textResId, true);
    }

    public synchronized void show(int textResId, boolean indeterminate) {
        if (dialog == null) create();
        composeState.setText(activity.getString(textResId));
        composeState.setIndeterminate(indeterminate);
        if (!indeterminate) {
            composeState.setProgress(0);
        }
        if (!isShowing()) dialog.show();
    }

    public synchronized void show(String text) {
        if (dialog == null) create();
        composeState.setText(text);
        composeState.setIndeterminate(true);
        if (!isShowing()) dialog.show();
    }

    public synchronized void setProgress(int percent) {
        if (dialog == null) return;
        composeState.setProgress(percent);
    }

    public void setProgressOnUiThread(final int percent) {
        activity.runOnUiThread(() -> setProgress(percent));
    }

    public void showOnUiThread(final int textResId) {
        activity.runOnUiThread(() -> show(textResId));
    }

    public void showOnUiThread(final String text) {
        activity.runOnUiThread(() -> show(text));
    }

    public synchronized void close() {
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
        catch (Exception e) {}
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
