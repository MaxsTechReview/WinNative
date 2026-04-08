package com.winlator.cmod;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.widget.XServerView;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class XServerDisplayActivity extends AppCompatActivity {
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private WinHandler winHandler;
    private ImageFs imageFs;
    private SharedPreferences preferences;
    private Handler timeoutHandler;
    private Runnable hideControlsRunnable;
    private final AtomicBoolean exitRequested = new AtomicBoolean(false);
    private XServer xServer;
    private XServerView xServerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.timeoutHandler = new Handler(Looper.getMainLooper());
        setupUI();
        setupInput();
    }

    private void setupUI() {
        // Reference app setup logic for UI
    }

    private void setupInput() {
        this.winHandler = new WinHandler(this);
        this.imageFs = ImageFs.find(this);
        File tmpDir = new File(imageFs.getRootDir(), "tmp");
        tmpDir.mkdirs();
        this.winHandler.setFakeInputPath(tmpDir.getAbsolutePath());
        this.hideControlsRunnable = () -> {
            boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
            if (isTimeoutEnabled && inputControlsView != null) {
                inputControlsView.setVisibility(View.GONE);
            }
        };
        for (int i = 0; i < 4; i++) {
            try {
                String name = (i == 0) ? "gamepad.mem" : "gamepad" + i + ".mem";
                File memFile = new File(tmpDir, name);
                if (!memFile.exists()) {
                    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(memFile, "rw")) {
                        raf.setLength(64);
                    }
                }
            } catch (Exception e) {}
        }
    }

    public void startTouchscreenTimeout() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            if (preferences.getBoolean("touchscreen_timeout_enabled", false)) {
                timeoutHandler.postDelayed(hideControlsRunnable, 5000L);
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (winHandler != null && winHandler.onGenericMotionEvent(event)) return true;
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (winHandler != null && winHandler.onKeyEvent(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (winHandler != null && winHandler.onKeyEvent(event)) return true;
        return super.onKeyUp(keyCode, event);
    }

    public XServer getXServer() { return xServer; }
    public XServerView getXServerView() { return xServerView; }
    public InputControlsView getInputControlsView() { return inputControlsView; }
    public WinHandler getWinHandler() { return winHandler; }
}
