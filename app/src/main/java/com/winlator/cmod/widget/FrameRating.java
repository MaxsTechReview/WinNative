package com.winlator.cmod.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

import com.winlator.cmod.R;
import com.winlator.cmod.core.CPUStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Locale;

public class FrameRating extends LinearLayout implements Runnable {
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
    private final FrameLayout graphContainer;
    private FrametimeGraphView graphView;
    private boolean isNativeActive;
    private boolean isStatsRunning;
    private float lastFPS;
    private long lastFrameNano;
    private long lastGraphRedraw;
    private long lastTime;
    private volatile String ramText;
    private String rendererName;
    private final View sep0;
    private final View sep1;
    private final View sep2;
    private final View sep3;
    private Handler statsHandler;
    private Runnable statsRunnable;
    private HandlerThread statsThread;
    private final TextView tvFpsBig;
    private final TextView tvGpuLoad;
    private final TextView tvHardwareStats;
    private final TextView tvRenderer;
    private final TextView tvWattsTemp;

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
        this.canReadGpu = true;
        this.canReadCpu = true;
        this.canReadBatt = true;
        this.gpuFailCount = 0;
        this.cpuFailCount = 0;
        this.battFailCount = 0;
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
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        setOrientation(LinearLayout.HORIZONTAL);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setBackgroundColor(0);
        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, true);
        this.tvRenderer = view.findViewById(R.id.TVRenderer);
        this.tvGpuLoad = view.findViewById(R.id.TVGpuLoad);
        this.tvHardwareStats = view.findViewById(R.id.TVHardwareStats);
        this.tvWattsTemp = view.findViewById(R.id.TVWattsTemp);
        this.tvFpsBig = view.findViewById(R.id.TVFpsBig);
        this.graphContainer = view.findViewById(R.id.FLGraphContainer);
        this.sep0 = view.findViewById(R.id.Sep0);
        this.sep1 = view.findViewById(R.id.Sep1);
        this.sep2 = view.findViewById(R.id.Sep2);
        this.sep3 = view.findViewById(R.id.Sep3);
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
        setupDragListener();
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bringToFront();
        setElevation(1000.0f);
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

    private void setupDragListener() {
        setOnTouchListener(new View.OnTouchListener() {
            private int activePointerId = -1;
            private float dX;
            private float dY;

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
                        view.bringToFront();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (this.activePointerId != -1) {
                            this.activePointerId = -1;
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (this.activePointerId != -1) {
                            view.setX(event.getRawX() + this.dX);
                            view.setY(event.getRawY() + this.dY);
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    public void setRenderer(String renderer) {
        if (renderer == null) {
            return;
        }
        if (renderer.contains("DXVK")) {
            this.rendererName = "DXVK";
        } else if (renderer.contains("Turnip")) {
            this.rendererName = "Turnip";
        } else if (renderer.contains("VirGL")) {
            this.rendererName = "VirGL";
        } else if (renderer.contains("llvmpipe")) {
            this.rendererName = "Software";
        } else {
            this.rendererName = renderer.replaceAll(".*Wrapper ", "").trim();
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
            this.tvRenderer.setText((this.isNativeActive ? "+" : "") + this.rendererName);
            this.tvRenderer.setVisibility(this.enableRenderer ? View.VISIBLE : View.GONE);
            updateSeparators(getOrientation() == LinearLayout.HORIZONTAL);
        }
    }

    public void setGpuName(String name) {
    }

    public void reset() {
        setRenderer("OpenGL");
        this.frameCount = 0;
        this.lastTime = 0L;
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
                if (this.tvHardwareStats != null) {
                    this.tvHardwareStats.setVisibility(v);
                }
                break;
            case 4:
                this.enableBattTemp = visible;
                if (this.tvWattsTemp != null) {
                    this.tvWattsTemp.setVisibility(v);
                }
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
        int i = View.GONE;
        if (!horizontal) {
            if (this.sep0 != null) this.sep0.setVisibility(View.GONE);
            if (this.sep1 != null) this.sep1.setVisibility(View.GONE);
            if (this.sep2 != null) this.sep2.setVisibility(View.GONE);
            if (this.sep3 != null) this.sep3.setVisibility(View.GONE);
            return;
        }
        boolean vRen = this.tvRenderer != null && this.tvRenderer.getVisibility() == View.VISIBLE;
        boolean vGpu = this.tvGpuLoad != null && this.tvGpuLoad.getVisibility() == View.VISIBLE;
        boolean vCpu = this.tvHardwareStats != null && this.tvHardwareStats.getVisibility() == View.VISIBLE;
        boolean vBat = this.tvWattsTemp != null && this.tvWattsTemp.getVisibility() == View.VISIBLE;
        boolean vFps = this.tvFpsBig != null && this.tvFpsBig.getVisibility() == View.VISIBLE;
        if (this.sep0 != null) {
            this.sep0.setVisibility((vRen && (vGpu || vCpu || vBat || vFps)) ? View.VISIBLE : View.GONE);
        }
        if (this.sep1 != null) {
            this.sep1.setVisibility((vGpu && (vCpu || vBat || vFps)) ? View.VISIBLE : View.GONE);
        }
        if (this.sep2 != null) {
            this.sep2.setVisibility((vCpu && (vBat || vFps)) ? View.VISIBLE : View.GONE);
        }
        if (this.sep3 != null) {
            this.sep3.setVisibility((vBat && vFps) ? View.VISIBLE : View.GONE);
        }
    }

    public void update() {
        if (getVisibility() != View.VISIBLE) {
            return;
        }
        if (this.lastTime == 0) {
            this.lastTime = SystemClock.elapsedRealtime();
        }
        long time = SystemClock.elapsedRealtime();
        if (time >= this.lastTime + 500) {
            this.lastFPS = (this.frameCount * 1000) / (time - this.lastTime);
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
                this.gpuLoad = calculateGPULoad();
                this.gpuFailCount = 0;
            } catch (Exception e) {
                this.gpuLoad = -1;
                this.gpuFailCount++;
                if (this.gpuFailCount > 5) this.canReadGpu = false;
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
                    if (this.cpuFailCount > 5) this.canReadCpu = false;
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
                if (this.battFailCount > 5) this.canReadBatt = false;
            }
        }
    }

    @Override
    public void run() {
        if (getVisibility() != View.VISIBLE) setVisibility(View.VISIBLE);
        if (this.enableGpu && this.tvGpuLoad != null) {
            SpannableStringBuilder b = new SpannableStringBuilder();
            append(b, "GPU ", this.C_GPU);
            append(b, this.gpuLoad >= 0 ? this.gpuLoad + "%" : "N/A", this.C_VALUE);
            this.tvGpuLoad.setText(b);
        }
        if (this.enableCpuRam && this.tvHardwareStats != null) {
            SpannableStringBuilder b1 = new SpannableStringBuilder();
            append(b1, "CPU ", this.C_CPU);
            append(b1, this.cpuPercent >= 0 ? this.cpuPercent + "% " : "N/A ", this.C_VALUE);
            appendDiv(b1);
            append(b1, "RAM ", this.C_RAM);
            append(b1, this.ramText, this.C_VALUE);
            this.tvHardwareStats.setText(b1);
        }
        if (this.enableBattTemp && this.tvWattsTemp != null) {
            SpannableStringBuilder b2 = new SpannableStringBuilder();
            append(b2, "BAT ", this.C_BAT);
            append(b2, this.batteryWatts >= 0.0f ? String.format(Locale.US, "%.1fW ", this.batteryWatts) : "N/A ", this.C_VALUE);
            appendDiv(b2);
            append(b2, "TMP ", this.C_TEMP);
            append(b2, this.cpuTemp >= 0 ? this.cpuTemp + "°C" : "N/A", this.C_VALUE);
            this.tvWattsTemp.setText(b2);
        }
        if (this.enableFps && this.tvFpsBig != null) {
            this.tvFpsBig.setText(String.format(Locale.US, "%.0f", this.lastFPS));
            this.tvFpsBig.setTextColor(this.C_FPS_OK);
        }
    }

    private void appendDiv(SpannableStringBuilder b) {
        int start = b.length();
        b.append(" | ");
        b.setSpan(new ForegroundColorSpan(this.C_DIVISOR), start, b.length(), 33);
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
