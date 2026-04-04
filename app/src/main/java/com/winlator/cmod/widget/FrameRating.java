package com.winlator.cmod.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.core.CPUStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Locale;

public class FrameRating extends LinearLayout implements Runnable {
    private static final String TAG = "FrameRating";
    public static final String PREF_HUD_DISPLAY_MODE = "hud_display_mode";
    public static final String PREF_HUD_POS_X = "hud_position_x";
    public static final String PREF_HUD_POS_Y = "hud_position_y";
    public static final String PREF_HUD_HAS_POSITION = "hud_has_position";
    public static final String PREF_HUD_DUAL_SERIES_BATTERY = "hud_dual_series_battery";
    private final int C_BAT;
    private final int C_CPU;
    private final int C_DIVISOR;
    private final int C_FPS_OK;
    private final int C_GPU;
    private final int C_RAM;
    private final int C_TEMP;
    private final int C_VALUE;
    private int battFailCount;
    private BatteryManager batteryManager;
    private volatile float batteryWatts;
    private boolean canReadBatt;
    private boolean canReadCpu;
    private boolean canReadGpu;
    private Context context;
    private int cpuFailCount;
    private volatile int cpuPercent;
    private volatile int cpuTemp;
    private float currentMs;
    private boolean enableBattTemp;
    private boolean enableCpuRam;
    private boolean enableFps;
    private boolean enableGpu;
    private boolean enableGraph;
    private boolean enableRenderer;
    private int frameCount;
    private int gpuFailCount;
    private volatile int gpuLoad;
    private FrametimeGraphView graphView;
    private boolean isNativeActive;
    private boolean isStatsRunning;
    private float lastFPS;
    private long lastFrameNano;
    private long lastGraphRedraw;
    private long lastTime;
    private volatile String ramText;
    private String rendererName;
    private String gpuName;
    private final View sep0, sep1, sep2, sep3, sep4, sep5;
    private final TextView tvRenderer;
    private final TextView tvGpuLoad;
    private final TextView tvCpu;
    private final TextView tvRam;
    private final TextView tvBat;
    private final TextView tvTemp;
    private final TextView tvFpsBig;
    private final FrameLayout graphContainer;
    private Handler statsHandler;
    private Runnable statsRunnable;
    private HandlerThread statsThread;
    private Handler uiRefreshHandler;
    private Runnable uiRefreshRunnable;
    private final SharedPreferences preferences;

    // ── GPU load caching (prevents N/A flickering from transient sysfs failures)
    private int lastGoodGpuLoad = -1;
    private long lastGoodGpuTime = 0;
    private static final long GPU_CACHE_DURATION_MS = 5000;

    // ── Tap-cycle display modes ──────────────────────────────────────
    // Mode 0: horizontal, no backdrop
    // Mode 1: horizontal, 50% shadow backdrop
    // Mode 2: vertical, 50% shadow backdrop
    // Mode 3: vertical, no backdrop
    private int displayMode = 0;
    private static final int MODE_COUNT = 4;
    private GradientDrawable backdropDrawable;
    private boolean dualSeriesBattery;

    public FrameRating(Context context, HashMap graphicsDriverConfig) {
        this(context, graphicsDriverConfig, null);
    }

    public FrameRating(Context context, HashMap graphicsDriverConfig, AttributeSet attrs) {
        this(context, graphicsDriverConfig, attrs, 0);
    }

    public FrameRating(Context context, HashMap graphicsDriverConfig, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.lastTime = 0L;
        this.lastGraphRedraw = 0L;
        this.lastFrameNano = 0L;
        this.frameCount = 0;
        this.lastFPS = 0.0f;
        this.currentMs = 0.0f;
        this.enableFps = true;
        this.enableGraph = true;
        this.enableGpu = true;
        this.enableCpuRam = true;
        this.enableBattTemp = true;
        this.enableRenderer = true;
        this.cpuPercent = -1;
        this.gpuLoad = -1;
        this.batteryWatts = -1.0f;
        this.cpuTemp = -1;
        this.ramText = "N/A";
        this.rendererName = "OpenGL";
        this.gpuName = null;
        this.canReadGpu = true;
        this.canReadCpu = true;
        this.canReadBatt = true;
        this.gpuFailCount = 0;
        this.cpuFailCount = 0;
        this.battFailCount = 0;
        this.lastGoodGpuLoad = -1;
        this.lastGoodGpuTime = 0;
        this.isNativeActive = false;
        this.isStatsRunning = false;
        this.C_VALUE = Color.parseColor("#FFFFFF");
        this.C_CPU = Color.parseColor("#FFAB91");
        this.C_RAM = Color.parseColor("#90CAF9");
        this.C_BAT = Color.parseColor("#EF5350");
        this.C_TEMP = Color.parseColor("#EF5350");
        this.C_GPU = Color.parseColor("#E040FB");
        this.C_FPS_OK = Color.parseColor("#76FF03");
        this.C_DIVISOR = Color.parseColor("#616161");
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        setOrientation(LinearLayout.HORIZONTAL);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setBackgroundColor(0);
        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, true);
        this.tvRenderer = view.findViewById(R.id.TVRenderer);
        this.tvGpuLoad = view.findViewById(R.id.TVGpuLoad);
        this.tvCpu = view.findViewById(R.id.TVCpu);
        this.tvRam = view.findViewById(R.id.TVRam);
        this.tvBat = view.findViewById(R.id.TVBat);
        this.tvTemp = view.findViewById(R.id.TVTemp);
        this.tvFpsBig = view.findViewById(R.id.TVFpsBig);
        this.graphContainer = view.findViewById(R.id.FLGraphContainer);
        this.sep0 = view.findViewById(R.id.Sep0);
        this.sep1 = view.findViewById(R.id.Sep1);
        this.sep2 = view.findViewById(R.id.Sep2);
        this.sep3 = view.findViewById(R.id.Sep3);
        this.sep4 = view.findViewById(R.id.Sep4);
        this.sep5 = view.findViewById(R.id.Sep5);
        this.graphView = new FrametimeGraphView(context);
        if (this.graphContainer != null) {
            this.graphContainer.addView(this.graphView);
        }
        if (this.tvRenderer != null) {
            this.tvRenderer.setText("OpenGL");
        }
        if (this.tvFpsBig != null) {
            this.tvFpsBig.setText("60");
        }

        // Create backdrop drawable (rounded, semi-transparent black)
        this.backdropDrawable = new GradientDrawable();
        this.backdropDrawable.setColor(0x80000000); // 50% black
        this.backdropDrawable.setCornerRadius(8f);

        loadPersistedHudPreferences();
        applyDisplayMode();

        // Detect GPU name from sysfs on init
        if (this.gpuName == null) {
            detectGpuNameFromSysfs();
        }

        setupTapAndDragListener();
        initStatsThread();
    }

    private void initStatsThread() {
        this.statsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStatsRunning) {
                    calculateStats();
                    if (statsHandler != null) {
                        statsHandler.postDelayed(this, 1000L);
                    }
                }
            }
        };
    }



    private void startStatsUpdate() {
        if (this.isStatsRunning) {
            return;
        }
        this.isStatsRunning = true;
        this.statsThread = new HandlerThread("HardwareStatsThread");
        this.statsThread.start();
        this.statsHandler = new Handler(this.statsThread.getLooper());
        this.statsHandler.post(this.statsRunnable);

        // Independent UI refresh timer — ensures hardware stats always refresh
        // on screen even if update() is not called (e.g. frameRatingWindowId mismatch).
        this.uiRefreshHandler = new Handler(android.os.Looper.getMainLooper());
        this.uiRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStatsRunning) {
                    FrameRating.this.run();
                    uiRefreshHandler.postDelayed(this, 500L);
                }
            }
        };
        this.uiRefreshHandler.postDelayed(this.uiRefreshRunnable, 500L);
    }

    private void stopStatsUpdate() {
        this.isStatsRunning = false;
        if (this.statsHandler != null) {
            this.statsHandler.removeCallbacks(this.statsRunnable);
        }
        if (this.statsThread != null) {
            this.statsThread.quitSafely();
            this.statsThread = null;
            this.statsHandler = null;
        }
        if (this.uiRefreshHandler != null) {
            this.uiRefreshHandler.removeCallbacks(this.uiRefreshRunnable);
            this.uiRefreshHandler = null;
            this.uiRefreshRunnable = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bringToFront();
        setElevation(1000.0f);
        restorePersistedPosition();
        removeCallbacks(this);
        post(this);
        startStatsUpdate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(this);
        stopStatsUpdate();
    }

    // ── Touch: tap cycles display mode, drag moves HUD ───────────────
    private void setupTapAndDragListener() {
        setOnTouchListener(new View.OnTouchListener() {
            private int activePointerId = -1;
            private float dX, dY;
            private float downRawX, downRawY;
            private long downTime;
            private boolean isDragging = false;
            private static final float TAP_SLOP = 20f;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getPointerCount() > 1) {
                    this.activePointerId = -1;
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        this.activePointerId = event.getPointerId(0);
                        this.dX = view.getX() - event.getRawX();
                        this.dY = view.getY() - event.getRawY();
                        this.downRawX = event.getRawX();
                        this.downRawY = event.getRawY();
                        this.downTime = SystemClock.elapsedRealtime();
                        this.isDragging = false;
                        view.bringToFront();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (this.activePointerId != -1) {
                            float dx = Math.abs(event.getRawX() - this.downRawX);
                            float dy = Math.abs(event.getRawY() - this.downRawY);
                            if (dx > TAP_SLOP || dy > TAP_SLOP) {
                                this.isDragging = true;
                            }
                            if (this.isDragging) {
                                view.setX(event.getRawX() + this.dX);
                                view.setY(event.getRawY() + this.dY);
                                clampToParentBounds(view);
                            }
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (this.activePointerId != -1) {
                            long elapsed = SystemClock.elapsedRealtime() - this.downTime;
                            if (!this.isDragging && elapsed < 400) {
                                // Short tap → cycle display mode
                                cycleDisplayMode();
                            } else if (this.isDragging) {
                                clampToParentBounds(view);
                                persistPosition(view.getX(), view.getY());
                            }
                            this.activePointerId = -1;
                            return true;
                        }
                        return false;
                }
                return false;
            }
        });
    }

    private void cycleDisplayMode() {
        displayMode = (displayMode + 1) % MODE_COUNT;
        this.preferences.edit().putInt(PREF_HUD_DISPLAY_MODE, displayMode).apply();
        post(this::applyDisplayMode);
    }

    private void loadPersistedHudPreferences() {
        this.displayMode = this.preferences.getInt(PREF_HUD_DISPLAY_MODE, 0);
        this.dualSeriesBattery = this.preferences.getBoolean(PREF_HUD_DUAL_SERIES_BATTERY, false);
    }

    private void restorePersistedPosition() {
        if (!this.preferences.getBoolean(PREF_HUD_HAS_POSITION, false)) {
            return;
        }
        post(() -> {
            setX(this.preferences.getFloat(PREF_HUD_POS_X, getX()));
            setY(this.preferences.getFloat(PREF_HUD_POS_Y, getY()));
            clampToParentBounds(this);
        });
    }

    private void persistPosition(float x, float y) {
        this.preferences.edit()
                .putBoolean(PREF_HUD_HAS_POSITION, true)
                .putFloat(PREF_HUD_POS_X, x)
                .putFloat(PREF_HUD_POS_Y, y)
                .apply();
    }

    private void clampToParentBounds(View view) {
        View parentView = (View) view.getParent();
        if (parentView == null) {
            return;
        }
        if (parentView.getWidth() <= 0 || parentView.getHeight() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }

        float maxX = Math.max(0f, parentView.getWidth() - view.getWidth());
        float maxY = Math.max(0f, parentView.getHeight() - view.getHeight());
        view.setX(Math.max(0f, Math.min(view.getX(), maxX)));
        view.setY(Math.max(0f, Math.min(view.getY(), maxY)));
    }

    private void applyDisplayMode() {
        boolean horizontal;
        boolean showBackdrop;
        switch (displayMode) {
            case 0: horizontal = true;  showBackdrop = false; break;
            case 1: horizontal = true;  showBackdrop = true;  break;
            case 2: horizontal = false; showBackdrop = false; break;
            case 3: horizontal = false; showBackdrop = true;  break;
            default: horizontal = true; showBackdrop = false; break;
        }
        setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        setGravity(horizontal ? android.view.Gravity.CENTER_VERTICAL : android.view.Gravity.START);
        setBackground(showBackdrop ? backdropDrawable : null);
        setPadding(showBackdrop ? 8 : 2, showBackdrop ? 6 : 2, showBackdrop ? 8 : 2, showBackdrop ? 6 : 2);

        int graphW = (int)(50 * getResources().getDisplayMetrics().density);
        int graphH = (int)(14 * getResources().getDisplayMetrics().density);

        View[] views = {tvRenderer, sep0, tvGpuLoad, sep1, tvCpu, sep2, tvRam, sep3, tvBat, sep4, tvTemp, sep5, tvFpsBig, graphContainer};
        for (View v : views) {
            if (v != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
                if (v == graphContainer) {
                    lp.width = graphW;
                    lp.height = graphH;
                    lp.setMargins(horizontal ? 4 : 0, horizontal ? 0 : 4, 0, 0);
                } else {
                    lp.width = LayoutParams.WRAP_CONTENT;
                    lp.height = LayoutParams.WRAP_CONTENT;
                    lp.setMargins(0, 0, 0, 0);
                }
                v.setLayoutParams(lp);
            }
        }

        updateSeparators(horizontal);
        requestLayout();
    }

    public void setRenderer(String renderer) {
        if (renderer == null) {
            return;
        }
        String r = renderer.toLowerCase();
        if (r.contains("turnip")) {
            this.rendererName = "Turnip";
        } else if (r.contains("vkd3d")) {
            this.rendererName = "VKD3D";
        } else if (r.contains("dxvk")) {
            this.rendererName = "DXVK";
        } else if (r.contains("virgl")) {
            this.rendererName = "VirGL";
        } else if (r.contains("zink")) {
            this.rendererName = "Zink";
        } else if (r.contains("llvmpipe") || r.contains("software")) {
            this.rendererName = "Software";
        } else if (r.contains("vulkan")) {
            this.rendererName = "Vulkan";
        } else if (r.contains("opengl")) {
            this.rendererName = "OpenGL";
        } else {
            this.rendererName = renderer
                .replaceAll("(?i).*Wrapper\\s*", "")
                .replaceAll("(?i)\\s*\\(Wrapper\\)", "")
                .replaceAll("(?i)\\s*Wrapper", "")
                .trim();
        }
        updateRendererText();
    }

    public void setIsNative(boolean isNative) {
        if (this.isNativeActive != isNative) {
            this.isNativeActive = isNative;
            post(new Runnable() {
                @Override
                public void run() {
                    updateRendererText();
                }
            });
        }
    }

    private void updateRendererText() {
        if (this.tvRenderer != null) {
            String text = (this.isNativeActive ? "+" : "") + this.rendererName;
            this.tvRenderer.setText(text);
            this.tvRenderer.setVisibility(this.enableRenderer ? View.VISIBLE : View.GONE);
            updateSeparators(getOrientation() == LinearLayout.HORIZONTAL);
        }
    }

    public void setGpuName(String name) {
        if (name != null && !name.isEmpty()) {
            // Clean up property format "name = value" and remove quotes
            String clean = name.contains("=") ? name.substring(name.indexOf("=") + 1).trim() : name;
            clean = clean.replace("\"", "").replace("'", "").trim();
            
            // Remove redundant "Wrapper(...)" or "Wrapper " prefix/suffix
            clean = clean.replaceAll("(?i)Wrapper\\((.*)\\)", "$1")
                        .replaceAll("(?i)Wrapper\\s*", "")
                        .trim();

            if (!clean.isEmpty()) {
                this.gpuName = clean;
                post(this::updateRendererText);
            }
        } else {
            this.gpuName = null;
            detectGpuNameFromSysfs();
        }
    }

    /**
     * Attempt to detect GPU name from sysfs (works for Qualcomm Adreno).
     */
    private void detectGpuNameFromSysfs() {
        try {
            // Adreno: read the GPU model from kgsl
            File gpuModel = new File("/sys/class/kgsl/kgsl-3d0/gpu_model");
            if (gpuModel.exists() && gpuModel.canRead()) {
                try (BufferedReader r = new BufferedReader(new FileReader(gpuModel))) {
                    String line = r.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        this.gpuName = "Adreno " + line.trim();
                        return;
                    }
                }
            }
            // Try /sys/kernel/gpu/gpu_model (some Qualcomm kernels)
            File gpuModel2 = new File("/sys/kernel/gpu/gpu_model");
            if (gpuModel2.exists() && gpuModel2.canRead()) {
                try (BufferedReader r = new BufferedReader(new FileReader(gpuModel2))) {
                    String line = r.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        this.gpuName = line.trim();
                        return;
                    }
                }
            }
            // Mali: check /sys/class/misc/mali0 existence
            File mali = new File("/sys/class/misc/mali0");
            if (mali.exists()) {
                this.gpuName = "Mali";
                return;
            }
            // PowerVR
            File pvr = new File("/sys/kernel/debug/pvr");
            if (pvr.exists()) {
                this.gpuName = "PowerVR";
            }
            if (this.gpuName != null) {
                post(this::updateRendererText);
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not detect GPU from sysfs: " + e.getMessage());
        }
    }

    public void reset() {
        this.frameCount = 0;
        this.lastTime = 0L;
        this.lastFrameNano = 0L;
        this.lastFPS = 0.0f;
        this.currentMs = 0.0f;
        post(this);
    }

    public void setGpuLoad(int load) {
        this.gpuLoad = load;
    }

    public void setLayoutOrientation(boolean z) {
        setOrientation(z ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        updateSeparators(z);
        requestLayout();
    }

    public void setHudScale(float scale) {
        setScaleX(scale);
        setScaleY(scale);
    }

    public void setHudAlpha(float alpha) {
        setAlpha(alpha);
    }

    public void setDualSeriesBattery(boolean enabled) {
        this.dualSeriesBattery = enabled;
        this.preferences.edit().putBoolean(PREF_HUD_DUAL_SERIES_BATTERY, enabled).apply();
        post(this);
    }

    public void toggleElement(int elementIndex, boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        switch (elementIndex) {
            case 0:
                this.enableFps = visible;
                if (this.tvFpsBig != null) {
                    this.tvFpsBig.setVisibility(v);
                }
                break;
            case 1:
                this.enableRenderer = visible;
                if (this.tvRenderer != null) {
                    this.tvRenderer.setVisibility(v);
                }
                break;
            case 2:
                this.enableGpu = visible;
                if (this.tvGpuLoad != null) {
                    this.tvGpuLoad.setVisibility(v);
                }
                break;
            case 3:
                this.enableCpuRam = visible;
                if (this.tvCpu != null) this.tvCpu.setVisibility(v);
                if (this.tvRam != null) this.tvRam.setVisibility(v);
                break;
            case 4:
                this.enableBattTemp = visible;
                if (this.tvBat != null) this.tvBat.setVisibility(v);
                if (this.tvTemp != null) this.tvTemp.setVisibility(v);
                break;
            case 5:
                this.enableGraph = visible;
                if (this.graphContainer != null) {
                    this.graphContainer.setVisibility(v);
                }
                break;
        }
        updateSeparators(getOrientation() == LinearLayout.HORIZONTAL);
    }

        private void updateSeparators(boolean horizontal) {
        if (!horizontal) {
            View[] seps = {sep0, sep1, sep2, sep3, sep4, sep5};
            for (View s : seps) if (s != null) s.setVisibility(View.GONE);
            return;
        }

        boolean vRen = tvRenderer != null && tvRenderer.getVisibility() == View.VISIBLE;
        boolean vGpu = tvGpuLoad != null && tvGpuLoad.getVisibility() == View.VISIBLE;
        boolean vCpu = tvCpu != null && tvCpu.getVisibility() == View.VISIBLE;
        boolean vRam = tvRam != null && tvRam.getVisibility() == View.VISIBLE;
        boolean vBat = tvBat != null && tvBat.getVisibility() == View.VISIBLE;
        boolean vTmp = tvTemp != null && tvTemp.getVisibility() == View.VISIBLE;
        boolean vFps = tvFpsBig != null && tvFpsBig.getVisibility() == View.VISIBLE;

        if (sep0 != null) sep0.setVisibility(vRen && (vGpu || vCpu || vRam || vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
        if (sep1 != null) sep1.setVisibility(vGpu && (vCpu || vRam || vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
        if (sep2 != null) sep2.setVisibility(vCpu && (vRam || vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
        if (sep3 != null) sep3.setVisibility(vRam && (vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
        if (sep4 != null) sep4.setVisibility(vBat && (vTmp || vFps) ? View.VISIBLE : View.GONE);
        if (sep5 != null) sep5.setVisibility(vTmp && vFps ? View.VISIBLE : View.GONE);
    }

    /**
     * Called by the X server rendering loop for each application window content change.
     * This is the primary FPS source — counts actual game frame updates.
     */
    public void update() {
        if (getVisibility() != View.VISIBLE) {
            return;
        }
        if (this.lastTime == 0) {
            this.lastTime = SystemClock.elapsedRealtime();
        }
        long time = SystemClock.elapsedRealtime();
        if (time >= this.lastTime + 500) {
            this.lastFPS = (this.frameCount * 1000f) / (time - this.lastTime);
            post(this);
            this.lastTime = time;
            this.frameCount = 0;
        }
        this.frameCount++;
        long nowNano = System.nanoTime();
        if (this.lastFrameNano == 0) {
            this.lastFrameNano = nowNano;
        }
        float ms = (nowNano - this.lastFrameNano) / 1000000.0f;
        this.lastFrameNano = nowNano;
        if (this.enableGraph && ms > 0.0f && ms < 500.0f) {
            this.currentMs = ms;
            if (time - this.lastGraphRedraw >= 50) {
                if (this.graphView != null) {
                    this.graphView.addFrame(ms);
                    this.graphView.postInvalidate();
                }
                this.lastGraphRedraw = time;
            }
        } else if (!this.enableGraph && ms > 0.0f && ms < 500.0f) {
            this.currentMs = ms;
        }
    }

    private long readSysFs(String path) {
        try {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null) {
                        return Long.parseLong(line.trim());
                    }
                }
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private float getBatteryCurrentAmps() {
        long currentRaw = 0;
        if (this.batteryManager != null) {
            currentRaw = this.batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }
        if (currentRaw == 0 || currentRaw == Long.MIN_VALUE) {
            currentRaw = readSysFs("/sys/class/power_supply/battery/current_now");
        }
        if (currentRaw == 0 || currentRaw == Long.MIN_VALUE) {
            currentRaw = readSysFs("/sys/class/power_supply/bms/current_now");
        }
        if (currentRaw == 0 || currentRaw == Long.MIN_VALUE) {
            return -1.0f;
        }
        long currentAbs = Math.abs(currentRaw);
        if (currentAbs < 20000) {
            return currentAbs / 1000.0f;
        }
        return currentAbs / 1000000.0f;
    }

    private int calculateGPULoad() throws Exception {
        File[] gpuFiles = {
            new File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"),
            new File("/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load"),
            new File("/sys/class/misc/mali0/device/utilisation")
        };

        for (File f : gpuFiles) {
            if (f.exists() && f.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null) {
                        return Integer.parseInt(line.trim().replaceAll("[^0-9]", ""));
                    }
                } catch (Exception ignored) {}
            }
        }

        File gpubusy = new File("/sys/class/kgsl/kgsl-3d0/gpubusy");
        if (gpubusy.exists() && gpubusy.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(gpubusy))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        long busy = Long.parseLong(parts[0]);
                        long total = Long.parseLong(parts[1]);
                        if (total != 0) return (int)((100 * busy) / total);
                    }
                }
            } catch (Exception ignored) {}
        }
        throw new Exception("Failed to read GPU usage.");
    }

    private void calculateStats() {
        if (this.enableGpu && this.canReadGpu) {
            try {
                int load = calculateGPULoad();
                this.gpuLoad = load;
                this.lastGoodGpuLoad = load;
                this.lastGoodGpuTime = SystemClock.elapsedRealtime();
                this.gpuFailCount = 0;
            } catch (Exception e) {
                // Use cached value if recent enough, otherwise show -1
                long elapsed = SystemClock.elapsedRealtime() - this.lastGoodGpuTime;
                if (this.lastGoodGpuLoad >= 0 && elapsed < GPU_CACHE_DURATION_MS) {
                    this.gpuLoad = this.lastGoodGpuLoad;
                } else {
                    this.gpuLoad = -1;
                }
                this.gpuFailCount++;
            }
        }
        if (this.enableCpuRam) {
            if (this.canReadCpu) {
                try {
                    short[] clocks = CPUStatus.getCurrentClockSpeeds();
                    if (clocks != null && clocks.length > 0) {
                        long cur = 0; long max = 0;
                        for (int i = 0; i < clocks.length; i++) {
                            cur += clocks[i];
                            max += CPUStatus.getMaxClockSpeed(i);
                        }
                        if (max > 0) {
                            this.cpuPercent = (int)((cur * 100) / max);
                            this.cpuFailCount = 0;
                        }
                    }
                } catch (Exception e) {
                    this.cpuPercent = -1;
                    this.cpuFailCount++;
                }
            }
            try {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                ((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
                long used = mi.totalMem - mi.availMem;
                this.ramText = ((100 * used) / mi.totalMem) + "%";
            } catch (Exception e) { this.ramText = "N/A"; }
        }
        if (this.enableBattTemp && this.canReadBatt) {
            try {
                float amps = getBatteryCurrentAmps();
                Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int mv = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
                if (mv > 0 && amps > 0.0f) this.batteryWatts = (mv / 1000.0f) * amps;
                else this.batteryWatts = -1.0f;
                if (intent != null) {
                    int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    if (temp > 0) this.cpuTemp = temp / 10;
                    else this.cpuTemp = -1;
                }
                this.battFailCount = 0;
            } catch (Exception e) {
                this.batteryWatts = -1.0f; this.cpuTemp = -1;
                this.battFailCount++;
            }
        }
    }

    @Override
    public void run() {
        if (getVisibility() != View.VISIBLE) return;

        // Watchdog: reset FPS if no frames arrived for > 1.5s
        long nowNano = System.nanoTime();
        if (this.lastFrameNano > 0 && nowNano - this.lastFrameNano > 1500000000L) {
            this.lastFPS = 0.0f;
            this.currentMs = 0.0f;
        }
        
        if (this.enableGpu && this.tvGpuLoad != null) {
            SpannableStringBuilder b = new SpannableStringBuilder();
            append(b, "GPU ", this.C_GPU);
            append(b, this.gpuLoad >= 0 ? this.gpuLoad + "%" : "N/A", this.C_VALUE);
            this.tvGpuLoad.setText(b);
            this.tvGpuLoad.setVisibility(View.VISIBLE);
        } else if (this.tvGpuLoad != null) this.tvGpuLoad.setVisibility(View.GONE);

        if (this.enableCpuRam) {
            if (this.tvCpu != null) {
                SpannableStringBuilder b = new SpannableStringBuilder();
                append(b, "CPU ", this.C_CPU);
                append(b, this.cpuPercent >= 0 ? this.cpuPercent + "%" : "N/A", this.C_VALUE);
                this.tvCpu.setText(b);
                this.tvCpu.setVisibility(View.VISIBLE);
            }
            if (this.tvRam != null) {
                SpannableStringBuilder b = new SpannableStringBuilder();
                append(b, "RAM ", this.C_RAM);
                append(b, this.ramText, this.C_VALUE);
                this.tvRam.setText(b);
                this.tvRam.setVisibility(View.VISIBLE);
            }
        } else {
            if (this.tvCpu != null) this.tvCpu.setVisibility(View.GONE);
            if (this.tvRam != null) this.tvRam.setVisibility(View.GONE);
        }

        if (this.enableBattTemp) {
            if (this.tvBat != null) {
                float displayedBatteryWatts = this.batteryWatts >= 0.0f && this.dualSeriesBattery
                        ? this.batteryWatts * 2.0f
                        : this.batteryWatts;
                SpannableStringBuilder b = new SpannableStringBuilder();
                append(b, "BAT ", this.C_BAT);
                append(b, displayedBatteryWatts >= 0.0f ? String.format(Locale.US, "%.1fW", displayedBatteryWatts) : "N/A", this.C_VALUE);
                this.tvBat.setText(b);
                this.tvBat.setVisibility(View.VISIBLE);
            }
            if (this.tvTemp != null) {
                SpannableStringBuilder b = new SpannableStringBuilder();
                append(b, "TMP ", this.C_TEMP);
                append(b, this.cpuTemp >= 0 ? this.cpuTemp + "°C" : "N/A", this.C_VALUE);
                this.tvTemp.setText(b);
                this.tvTemp.setVisibility(View.VISIBLE);
            }
        } else {
            if (this.tvBat != null) this.tvBat.setVisibility(View.GONE);
            if (this.tvTemp != null) this.tvTemp.setVisibility(View.GONE);
        }

        if (this.enableFps && this.tvFpsBig != null) {
            this.tvFpsBig.setText(String.format(Locale.US, "%.0f", this.lastFPS));
            this.tvFpsBig.setTextColor(this.C_FPS_OK);
            this.tvFpsBig.setVisibility(View.VISIBLE);
        } else if (this.tvFpsBig != null) this.tvFpsBig.setVisibility(View.GONE);
        
        if (getOrientation() == LinearLayout.HORIZONTAL) updateSeparators(true);
    }

    private void append(SpannableStringBuilder b, String t, int c) {
        int start = b.length();
        b.append(t);
        b.setSpan(new ForegroundColorSpan(c), start, b.length(), 33);
    }

    private class FrametimeGraphView extends View {
        private final int MAX_SAMPLES = 60;
        private final float[] history = new float[MAX_SAMPLES];
        private int historyIndex = 0;
        private int historySize = 0;
        private final Paint paintLine;
        private final Path path = new Path();

        public FrametimeGraphView(Context context) {
            super(context);
            this.paintLine = new Paint();
            this.paintLine.setColor(C_FPS_OK);
            this.paintLine.setStrokeWidth(1.5f);
            this.paintLine.setStyle(Paint.Style.STROKE);
            this.paintLine.setAntiAlias(true);
            setBackgroundColor(0);
        }

        public void addFrame(float ms) {
            this.history[this.historyIndex] = Math.min(ms, 66.6f);
            this.historyIndex = (this.historyIndex + 1) % MAX_SAMPLES;
            if (this.historySize < MAX_SAMPLES) this.historySize++;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (this.historySize < 2) return;
            float w = getWidth(); float h = getHeight();
            float step = w / (MAX_SAMPLES - 1);
            this.path.reset();
            int start = (this.historyIndex - this.historySize + MAX_SAMPLES) % MAX_SAMPLES;
            float yStart = h - ((this.history[start] / 40.0f) * h);
            this.path.moveTo(0.0f, Math.max(0.0f, yStart));
            for (int i = 1; i < this.historySize; i++) {
                int idx = (start + i) % MAX_SAMPLES;
                float y = h - ((this.history[idx] / 40.0f) * h);
                this.path.lineTo(i * step, Math.max(0.0f, y));
            }
            canvas.drawPath(this.path, this.paintLine);
        }
    }
}
