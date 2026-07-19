package com.winlator.cmod.runtime.display.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.runtime.system.CPUStatus;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Locale;

/**
 * Mango Style HUD: performance overlay with a classic table layout (accent label
 * column, white right-aligned values with small units), frametime graph and
 * 1%/0.1% lows. Frame events are ring-buffered with zero allocation; text is
 * formatted at 2 Hz on a private stats thread into reusable buffers, so the
 * present path stays free of formatting, logging and garbage.
 */
public class MangoHudView extends View {
  public static final String PREF_ENABLED = "mango_hud_enabled";
  private static final String PREF_ELEMENTS = "mango_hud_elements";
  private static final String PREF_POS_X = "mango_hud_position_x";
  private static final String PREF_POS_Y = "mango_hud_position_y";
  private static final String PREF_HAS_POSITION = "mango_hud_has_position";
  private static final String PREF_SCALE_INDEX = "mango_hud_scale_index";
  private static final String PREF_ALPHA = "mango_hud_alpha";
  private static final String PREF_BG_ALPHA = "mango_hud_bg_alpha";
  private static final float DEFAULT_ALPHA = 1.0f;
  private static final float DEFAULT_BG_ALPHA = 0.5f;

  // Element indices (bitmask in PREF_ELEMENTS).
  public static final int EL_GPU_LOAD = 0;
  public static final int EL_GPU_TEMP = 1;
  public static final int EL_CPU_LOAD = 2;
  public static final int EL_CPU_TEMP = 3;
  public static final int EL_RAM = 4;
  public static final int EL_BATTERY = 5;
  public static final int EL_LOWS = 6;
  public static final int EL_GRAPH = 7;
  public static final int EL_ENGINE = 8;
  public static final int EL_VRAM = 9;
  public static final int ELEMENT_COUNT = 10;
  private static final int DEFAULT_ELEMENTS_MASK = 0x3DF; // all except battery

  // Overlay color palette.
  private static final int C_BG = 0x00020202;
  private static final int C_TEXT = 0xFFFFFFFF;
  private static final int C_GPU = 0xFF2E9762;
  private static final int C_CPU = 0xFF2E97CB;
  private static final int C_VRAM = 0xFFAD64C1;
  private static final int C_RAM = 0xFFC26693;
  private static final int C_BAT = 0xFFFF9078;
  private static final int C_ENGINE = 0xFFEB5B5B;
  private static final int C_GRAPH = 0xFF00FF00;
  private static final int C_OUTLINE = 0xFF000000;

  private static final float[] SCALE_STEPS = {0.75f, 1.0f, 1.25f, 1.5f};
  private static final float BASE_TEXT_DP = 14f;
  private static final long TICK_MS = 500L;
  private static final long HIDDEN_TICK_MS = 2000L;
  private static final long FALLBACK_SUPPRESSION_NS = 2000000000L;
  private static final long FPS_WINDOW_NS = 1000000000L;
  private static final long IDLE_TIMEOUT_NS = 1500000000L;
  private static final long GRAPH_REDRAW_MS = 100L;
  private static final int STAMP_CAPACITY = 1024;
  private static final int LOWS_CAPACITY = 10000;
  private static final int GRAPH_SAMPLES = 200;
  private static final float GRAPH_CEIL_MS = 50f;

  private final SharedPreferences preferences;
  private final BatteryManager batteryManager;
  private final IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
  private final float density;

  // ── frame rings, guarded by frameLock; written on the present thread ──
  private final Object frameLock = new Object();
  private final long[] stampsNano = new long[STAMP_CAPACITY];
  private int stampsStart, stampsCount;
  private final float[] lowsMs = new float[LOWS_CAPACITY];
  private int lowsIndex, lowsCount;
  private final float[] graphMs = new float[GRAPH_SAMPLES];
  private int graphIndex, graphCount;
  private long lastFrameNano, lastPrimaryNano;
  private float currentMs;
  private volatile long lastGraphInvalidate;

  // ── formatted cells + layout, guarded by uiLock ──
  private final Object uiLock = new Object();
  private final StringBuilder sbGpuLoad = new StringBuilder(8);
  private final StringBuilder sbGpuTemp = new StringBuilder(8);
  private final StringBuilder sbCpuLoad = new StringBuilder(8);
  private final StringBuilder sbCpuTemp = new StringBuilder(8);
  private final StringBuilder sbVram = new StringBuilder(8);
  private final StringBuilder sbRam = new StringBuilder(8);
  private final StringBuilder sbBatPct = new StringBuilder(8);
  private final StringBuilder sbBatW = new StringBuilder(8);
  private final StringBuilder sbFps = new StringBuilder(8);
  private final StringBuilder sbMs = new StringBuilder(8);
  private final StringBuilder sbAvg = new StringBuilder(8);
  private final StringBuilder sbLow1 = new StringBuilder(8);
  private final StringBuilder sbLow01 = new StringBuilder(8);
  private final StringBuilder sbMinMax = new StringBuilder(24);
  private String engineName = "VULKAN";
  private final boolean[] elements = new boolean[ELEMENT_COUNT];
  private int scaleIndex = 1;
  private float textAlpha = DEFAULT_ALPHA;
  private float bgAlpha = DEFAULT_BG_ALPHA;
  private int panelW = 1, panelH = 1;
  private float textSize, smallSize, rowH, smallRowH, baseline, smallBaseline;
  private float pad, charW, smallCharW, labelColW, graphH;

  private final Paint bgPaint = new Paint();
  private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint graphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] graphLines = new float[(GRAPH_SAMPLES - 1) * 4];
  private final float[] graphSnapshot = new float[GRAPH_SAMPLES];

  // ── stats thread state ──
  private volatile HandlerThread statsThread;
  private volatile Handler statsHandler;
  private final float[] lowsScratch = new float[LOWS_CAPACITY];
  private CPUStatus.AppCpuSample prevCpuSample;
  private boolean cpuWarmedUp;
  private int slowTickParity;
  private int gpuLoad = -1, gpuTemp = -1, cpuLoad = -1, cpuTemp = -1, batteryPct = -1;
  private float ramGib = -1f, vramGib = -1f, batteryWatts = -1f;
  private String[] gpuTempPaths;
  private String vramPath;
  private boolean vramPathSearched;
  // Self-reposting like FrameRating's stats loop: alive for the view's whole
  // attached life, independent of frame events, so a session where frames never
  // reach the HUD still shows live system stats. Near-free while hidden (one
  // visibility read per 2s, no stat work).
  private final Runnable tickRunnable = new Runnable() {
    @Override
    public void run() {
      Handler handler = statsHandler;
      if (handler == null) return;
      boolean visible = getVisibility() == VISIBLE;
      if (visible) tick();
      handler.postDelayed(this, visible ? TICK_MS : HIDDEN_TICK_MS);
    }
  };

  public MangoHudView(Context context) {
    super(context);
    this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    this.density = getResources().getDisplayMetrics().density;

    int mask = preferences.getInt(PREF_ELEMENTS, DEFAULT_ELEMENTS_MASK);
    for (int i = 0; i < ELEMENT_COUNT; i++) elements[i] = (mask & (1 << i)) != 0;
    this.scaleIndex = clampScaleIndex(preferences.getInt(PREF_SCALE_INDEX, 0));
    this.textAlpha = preferences.getFloat(PREF_ALPHA, DEFAULT_ALPHA);
    this.bgAlpha = preferences.getFloat(PREF_BG_ALPHA, DEFAULT_BG_ALPHA);

    Typeface mono = Typeface.MONOSPACE;
    bgPaint.setColor(C_BG);
    valuePaint.setTypeface(mono);
    valuePaint.setColor(C_TEXT);
    labelPaint.setTypeface(mono);
    smallPaint.setTypeface(mono);
    smallPaint.setColor(C_TEXT);
    outlinePaint.setTypeface(mono);
    outlinePaint.setColor(C_OUTLINE);
    outlinePaint.setStyle(Paint.Style.STROKE);
    graphPaint.setColor(C_GRAPH);
    graphPaint.setStyle(Paint.Style.STROKE);

    setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    synchronized (uiLock) {
      applyScaleLocked();
      applyAlphasLocked();
      computeLayoutLocked();
    }
    setVisibility(VISIBLE);
  }

  public static boolean[] elementsFromPrefs(SharedPreferences preferences) {
    int mask = preferences.getInt(PREF_ELEMENTS, DEFAULT_ELEMENTS_MASK);
    boolean[] out = new boolean[ELEMENT_COUNT];
    for (int i = 0; i < ELEMENT_COUNT; i++) out[i] = (mask & (1 << i)) != 0;
    return out;
  }

  public static void saveElement(SharedPreferences preferences, int index, boolean enabled) {
    if (index < 0 || index >= ELEMENT_COUNT) return;
    int mask = preferences.getInt(PREF_ELEMENTS, DEFAULT_ELEMENTS_MASK);
    mask = enabled ? mask | (1 << index) : mask & ~(1 << index);
    preferences.edit().putInt(PREF_ELEMENTS, mask).apply();
  }

  public static float alphaFromPrefs(SharedPreferences preferences) {
    return preferences.getFloat(PREF_ALPHA, DEFAULT_ALPHA);
  }

  public static float bgAlphaFromPrefs(SharedPreferences preferences) {
    return preferences.getFloat(PREF_BG_ALPHA, DEFAULT_BG_ALPHA);
  }

  public static void saveAlpha(SharedPreferences preferences, float alpha) {
    preferences.edit().putFloat(PREF_ALPHA, alpha).apply();
  }

  public static void saveBgAlpha(SharedPreferences preferences, float alpha) {
    preferences.edit().putFloat(PREF_BG_ALPHA, alpha).apply();
  }

  public void setTextAlphaValue(float alpha) {
    synchronized (uiLock) {
      textAlpha = alpha;
      applyAlphasLocked();
    }
    postInvalidate();
  }

  public void setBackgroundAlphaValue(float alpha) {
    synchronized (uiLock) {
      bgAlpha = alpha;
      applyAlphasLocked();
    }
    postInvalidate();
  }

  public void setElementEnabled(int index, boolean enabled) {
    if (index < 0 || index >= ELEMENT_COUNT) return;
    synchronized (uiLock) {
      elements[index] = enabled;
      computeLayoutLocked();
    }
    post(() -> {
      requestLayout();
      invalidate();
    });
  }

  public void setEngineName(String name) {
    if (name == null || name.isEmpty()) return;
    String upper = name.toUpperCase(Locale.US);
    if (upper.length() > 16) upper = upper.substring(0, 16);
    synchronized (uiLock) {
      engineName = upper;
      computeLayoutLocked();
    }
    post(() -> {
      requestLayout();
      invalidate();
    });
  }

  public void setHudVisible(boolean visible) {
    if (visible) {
      synchronized (frameLock) {
        resetFrameDataLocked();
      }
      setVisibility(VISIBLE);
      bringToFront();
      // Populate immediately instead of waiting out a hidden-interval delay.
      Handler handler = statsHandler;
      if (handler != null) {
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
      }
    } else {
      setVisibility(GONE);
    }
  }

  /** Present-path hook: one call per game frame; ring writes only, no allocation. */
  public void recordGameFrame(boolean primarySource) {
    if (getVisibility() != VISIBLE) return;
    long nowNano = System.nanoTime();
    synchronized (frameLock) {
      if (primarySource) {
        if (lastPrimaryNano == 0 || nowNano - lastPrimaryNano >= FALLBACK_SUPPRESSION_NS) {
          resetFrameDataLocked();
        }
        lastPrimaryNano = nowNano;
      } else if (lastPrimaryNano > 0 && nowNano - lastPrimaryNano < FALLBACK_SUPPRESSION_NS) {
        return;
      }

      if (lastFrameNano != 0) {
        float ms = (nowNano - lastFrameNano) / 1000000.0f;
        if (ms > 0f && ms < 1000f) {
          currentMs = ms;
          lowsMs[lowsIndex] = ms;
          lowsIndex = (lowsIndex + 1) % LOWS_CAPACITY;
          if (lowsCount < LOWS_CAPACITY) lowsCount++;
          graphMs[graphIndex] = ms;
          graphIndex = (graphIndex + 1) % GRAPH_SAMPLES;
          if (graphCount < GRAPH_SAMPLES) graphCount++;
        }
      }
      lastFrameNano = nowNano;

      int index = (stampsStart + stampsCount) % STAMP_CAPACITY;
      if (stampsCount == STAMP_CAPACITY) {
        stampsStart = (stampsStart + 1) % STAMP_CAPACITY;
      } else {
        stampsCount++;
      }
      stampsNano[index] = nowNano;
    }

    if (elements[EL_GRAPH]) {
      long time = SystemClock.elapsedRealtime();
      if (time - lastGraphInvalidate >= GRAPH_REDRAW_MS) {
        lastGraphInvalidate = time;
        postInvalidate();
      }
    }
  }

  private void resetFrameDataLocked() {
    stampsStart = 0;
    stampsCount = 0;
    lowsIndex = 0;
    lowsCount = 0;
    graphIndex = 0;
    graphCount = 0;
    lastFrameNano = 0;
    currentMs = 0f;
  }

  // ── stats thread ─────────────────────────────────────────────────

  private void startStats() {
    if (statsThread != null) return;
    statsThread = new HandlerThread("MangoHudStats");
    statsThread.start();
    statsHandler = new Handler(statsThread.getLooper());
    statsHandler.post(tickRunnable);
  }

  private void stopStats() {
    Handler handler = statsHandler;
    HandlerThread thread = statsThread;
    statsHandler = null;
    statsThread = null;
    if (handler != null) handler.removeCallbacksAndMessages(null);
    if (thread != null) {
      thread.quitSafely();
    }
    prevCpuSample = null;
    cpuWarmedUp = false;
  }

  private void tick() {
    long nowNano = System.nanoTime();
    float fps;
    float ms;
    int lowsN;
    float graphMin = 0f, graphMax = 0f;
    synchronized (frameLock) {
      // Trim the rolling FPS window here so the present path stays write-only.
      long oldest = nowNano - FPS_WINDOW_NS;
      while (stampsCount > 0 && stampsNano[stampsStart] < oldest) {
        stampsStart = (stampsStart + 1) % STAMP_CAPACITY;
        stampsCount--;
      }
      boolean idle = lastFrameNano == 0 || nowNano - lastFrameNano > IDLE_TIMEOUT_NS;
      if (idle || stampsCount <= 1) {
        fps = 0f;
      } else {
        long first = stampsNano[stampsStart];
        long last = stampsNano[(stampsStart + stampsCount - 1) % STAMP_CAPACITY];
        fps = last > first ? (stampsCount - 1) * 1000000000.0f / (last - first) : 0f;
      }
      ms = idle ? 0f : currentMs;
      lowsN = lowsCount;
      System.arraycopy(lowsMs, 0, lowsScratch, 0, lowsN);
      for (int i = 0; i < graphCount; i++) {
        float v = graphMs[i];
        if (i == 0) {
          graphMin = graphMax = v;
        } else {
          if (v < graphMin) graphMin = v;
          if (v > graphMax) graphMax = v;
        }
      }
    }

    // 1%/0.1% lows: percentile over the last 10k frametimes, converted to FPS.
    float avgFps = 0f, low1Fps = 0f, low01Fps = 0f;
    if (elements[EL_LOWS] && lowsN > 0) {
      Arrays.sort(lowsScratch, 0, lowsN);
      float sum = 0f;
      for (int i = 0; i < lowsN; i++) sum += lowsScratch[i];
      if (sum > 0f) avgFps = lowsN * 1000.0f / sum;
      low1Fps = percentileLowFps(lowsScratch, lowsN, 0.01f);
      low01Fps = percentileLowFps(lowsScratch, lowsN, 0.001f);
    }

    boolean slowTick = (slowTickParity++ & 1) == 0;
    if (elements[EL_GPU_LOAD]) readGpuLoad();
    if (elements[EL_CPU_LOAD]) readCpuLoad();
    if (slowTick) {
      if (elements[EL_GPU_TEMP]) gpuTemp = readGpuTempC();
      if (elements[EL_CPU_TEMP]) cpuTemp = CPUStatus.getCpuTempC();
      if (elements[EL_VRAM]) readVram();
      if (elements[EL_RAM]) readRam();
      if (elements[EL_BATTERY]) readBattery();
    }

    synchronized (uiLock) {
      formatInt(sbFps, Math.round(fps));
      formatMs(sbMs, ms);
      formatInt(sbGpuLoad, gpuLoad);
      formatInt(sbGpuTemp, gpuTemp);
      formatInt(sbCpuLoad, cpuLoad);
      formatInt(sbCpuTemp, cpuTemp);
      formatTenths(sbVram, vramGib);
      formatTenths(sbRam, ramGib);
      formatInt(sbBatPct, batteryPct);
      formatTenths(sbBatW, batteryWatts);
      formatInt(sbAvg, Math.round(avgFps));
      formatInt(sbLow1, Math.round(low1Fps));
      formatInt(sbLow01, Math.round(low01Fps));
      sbMinMax.setLength(0);
      sbMinMax.append("min:");
      appendTenths(sbMinMax, graphMin);
      sbMinMax.append(" max:");
      appendTenths(sbMinMax, graphMax);
      computeLayoutLocked();
    }

    int w = panelW, h = panelH;
    post(() -> {
      if (getMeasuredWidth() != w || getMeasuredHeight() != h) requestLayout();
      invalidate();
    });
  }

  private static float percentileLowFps(float[] sortedAsc, int n, float p) {
    int idx = (int) (p * n) - 1;
    if (idx < 0) idx = 0;
    float ft = sortedAsc[n - 1 - idx];
    return ft > 0f ? 1000.0f / ft : 0f;
  }

  // ── system stat readers (silent; sysfs paths mirror FrameRating) ──

  private void readGpuLoad() {
    int load = -1;
    try {
      File gpubusy = new File("/sys/class/kgsl/kgsl-3d0/gpubusy");
      if (gpubusy.exists() && gpubusy.canRead()) {
        try (BufferedReader reader = new BufferedReader(new FileReader(gpubusy))) {
          String line = reader.readLine();
          if (line != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
              long busy = Long.parseLong(parts[0]);
              long total = Long.parseLong(parts[1]);
              if (total > 0) load = (int) ((100 * busy) / total);
            }
          }
        }
      }
      if (load < 0) {
        String[] paths = {
          "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
          "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
          "/sys/class/misc/mali0/device/utilisation",
          "/sys/kernel/gpu/gpu_busy"
        };
        for (String path : paths) {
          File f = new File(path);
          if (f.exists() && f.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
              String line = reader.readLine();
              if (line != null) {
                load = Integer.parseInt(line.trim().replaceAll("[^0-9]", ""));
                break;
              }
            } catch (Exception ignored) {
            }
          }
        }
      }
    } catch (Exception ignored) {
    }
    if (load >= 0) gpuLoad = Math.min(load, 100);
  }

  private int readGpuTempC() {
    String[] paths = gpuTempPaths;
    if (paths == null) {
      paths = discoverGpuTempPaths();
      gpuTempPaths = paths;
    }
    for (String path : paths) {
      try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
        String line = reader.readLine();
        if (line != null) {
          int raw = Integer.parseInt(line.trim());
          int celsius = raw > 1000 ? (raw + 500) / 1000 : raw;
          if (celsius >= 1 && celsius <= 150) return celsius;
        }
      } catch (Exception ignored) {
      }
    }
    return -1;
  }

  private static String[] discoverGpuTempPaths() {
    java.util.ArrayList<String> found = new java.util.ArrayList<>();
    File kgsl = new File("/sys/class/kgsl/kgsl-3d0/temp");
    if (kgsl.exists() && kgsl.canRead()) found.add(kgsl.getAbsolutePath());
    File[] roots = {new File("/sys/class/thermal"), new File("/sys/devices/virtual/thermal")};
    for (File root : roots) {
      File[] zones = root.listFiles(
          (dir, name) -> name.startsWith("thermal_zone") && new File(dir, name).isDirectory());
      if (zones == null) continue;
      for (File zone : zones) {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(zone, "type")))) {
          String type = reader.readLine();
          if (type != null && type.trim().toLowerCase(Locale.US).contains("gpu")) {
            File temp = new File(zone, "temp");
            if (temp.exists() && temp.canRead()) found.add(temp.getAbsolutePath());
          }
        } catch (Exception ignored) {
        }
      }
    }
    return found.toArray(new String[0]);
  }

  private void readCpuLoad() {
    try {
      CPUStatus.AppCpuSample sample = CPUStatus.readAppCpuSample();
      int pct = -1;
      if (sample != null) {
        if (prevCpuSample != null) pct = sample.percentSince(prevCpuSample);
        prevCpuSample = sample;
      }
      if (pct >= 0) {
        cpuLoad = pct;
        cpuWarmedUp = true;
      } else if (!cpuWarmedUp) {
        int freq = CPUStatus.getClockFreqLoadPercent();
        if (freq >= 0) cpuLoad = freq;
      }
    } catch (Exception e) {
      cpuLoad = -1;
    }
  }

  private void readRam() {
    try {
      ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
      ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
      am.getMemoryInfo(mi);
      ramGib = (mi.totalMem - mi.availMem) / 1073741824.0f;
    } catch (Exception e) {
      ramGib = -1f;
    }
  }

  /** GPU memory is shared on Android; best-effort read of the GPU allocator total (bytes). */
  private void readVram() {
    if (!vramPathSearched) {
      vramPathSearched = true;
      String[] candidates = {
        "/sys/class/kgsl/kgsl/page_alloc",
        "/sys/class/kgsl/kgsl-3d0/page_alloc",
        "/sys/kernel/gpu/gpu_memory",
        "/proc/mali/memory_usage"
      };
      for (String path : candidates) {
        File f = new File(path);
        if (f.exists() && f.canRead()) {
          vramPath = path;
          break;
        }
      }
    }
    if (vramPath == null) {
      vramGib = -1f;
      return;
    }
    try (BufferedReader reader = new BufferedReader(new FileReader(vramPath))) {
      String line = reader.readLine();
      if (line != null) {
        String digits = line.trim().replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
          long bytes = Long.parseLong(digits);
          vramGib = bytes / 1073741824.0f;
          return;
        }
      }
      vramGib = -1f;
    } catch (Exception e) {
      vramGib = -1f;
    }
  }

  private void readBattery() {
    try {
      int pct = batteryManager != null
          ? batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
      batteryPct = pct > 0 ? pct : -1;
      long currentUa = batteryManager != null
          ? batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) : 0;
      float amps = -1f;
      if (currentUa != 0 && currentUa != Long.MIN_VALUE) {
        long abs = Math.abs(currentUa);
        amps = abs < 20000 ? abs / 1000.0f : abs / 1000000.0f;
      }
      Intent intent = getContext().registerReceiver(null, batteryFilter);
      int mv = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
      batteryWatts = mv > 0 && amps > 0f ? (mv / 1000.0f) * amps : -1f;
    } catch (Exception e) {
      batteryPct = -1;
      batteryWatts = -1f;
    }
  }

  // ── allocation-free formatting into reusable builders ──

  private static void formatInt(StringBuilder sb, int value) {
    sb.setLength(0);
    if (value < 0) {
      sb.append('-');
      return;
    }
    sb.append(value);
  }

  private static void formatTenths(StringBuilder sb, float value) {
    sb.setLength(0);
    if (value < 0f) {
      sb.append('-');
      return;
    }
    appendTenths(sb, value);
  }

  private static void formatMs(StringBuilder sb, float ms) {
    sb.setLength(0);
    if (ms <= 0f) {
      sb.append('-');
    } else if (ms >= 100f) {
      sb.append(Math.round(ms));
    } else {
      appendTenths(sb, ms);
    }
  }

  private static void appendTenths(StringBuilder sb, float value) {
    if (value < 0f) value = 0f;
    int tenths = Math.round(value * 10f);
    sb.append(tenths / 10).append('.').append(tenths % 10);
  }

  // ── layout & drawing ─────────────────────────────────────────────

  private void applyScaleLocked() {
    float scale = SCALE_STEPS[scaleIndex];
    textSize = BASE_TEXT_DP * density * scale;
    smallSize = textSize * 0.55f;
    valuePaint.setTextSize(textSize);
    labelPaint.setTextSize(textSize);
    smallPaint.setTextSize(smallSize);
    outlinePaint.setStrokeWidth(Math.max(1.5f, textSize * 0.08f));
    graphPaint.setStrokeWidth(Math.max(1.5f, textSize * 0.07f));
    Paint.FontMetrics fm = valuePaint.getFontMetrics();
    rowH = (fm.descent - fm.ascent) * 1.02f;
    baseline = -fm.ascent;
    Paint.FontMetrics sfm = smallPaint.getFontMetrics();
    smallRowH = (sfm.descent - sfm.ascent) * 1.1f;
    smallBaseline = -sfm.ascent;
    pad = textSize * 0.3f;
    charW = valuePaint.measureText("0");
    smallCharW = smallPaint.measureText("0");
    graphH = textSize * 2.0f;
  }

  private void applyAlphasLocked() {
    int bg = Math.round(Math.max(0f, Math.min(bgAlpha, 1f)) * 255f);
    bgPaint.setColor((bg << 24) | (C_BG & 0x00FFFFFF));
    int text = Math.round(Math.max(0f, Math.min(textAlpha, 1f)) * 255f);
    valuePaint.setAlpha(text);
    graphPaint.setAlpha(text);
    outlinePaint.setAlpha(text);
  }

  private int textAlphaInt() {
    return Math.round(Math.max(0f, Math.min(textAlpha, 1f)) * 255f);
  }

  private float statCellW(int unitChars) {
    return charW * 4 + smallCharW * (unitChars + 0.4f);
  }

  private void computeLayoutLocked() {
    labelColW = charW * 4.6f;
    if (elements[EL_ENGINE]) {
      labelColW = Math.max(labelColW, charW * (engineName.length() + 0.6f));
    }
    float w = 0f;
    int rows = 0;
    if (elements[EL_GPU_LOAD] || elements[EL_GPU_TEMP]) {
      rows++;
      float rw = labelColW
          + (elements[EL_GPU_LOAD] ? statCellW(1) : 0f)
          + (elements[EL_GPU_TEMP] ? statCellW(2) : 0f);
      w = Math.max(w, rw);
    }
    if (elements[EL_CPU_LOAD] || elements[EL_CPU_TEMP]) {
      rows++;
      float rw = labelColW
          + (elements[EL_CPU_LOAD] ? statCellW(1) : 0f)
          + (elements[EL_CPU_TEMP] ? statCellW(2) : 0f);
      w = Math.max(w, rw);
    }
    if (elements[EL_VRAM]) {
      rows++;
      w = Math.max(w, labelColW + statCellW(3));
    }
    if (elements[EL_RAM]) {
      rows++;
      w = Math.max(w, labelColW + statCellW(3));
    }
    if (elements[EL_BATTERY]) {
      rows++;
      w = Math.max(w, labelColW + statCellW(1) + statCellW(1));
    }
    rows++; // FPS row is the HUD core, always shown
    w = Math.max(w, labelColW + statCellW(3) + statCellW(2));
    if (elements[EL_LOWS]) {
      rows += 3;
      w = Math.max(w, labelColW + statCellW(3));
    }
    // Width must stay independent of live values (min/max digits) so the panel
    // never relayouts mid-session; the graph header just clips to this width.
    w = Math.max(w, charW * 13f);
    float h = rows * rowH;
    if (elements[EL_GRAPH]) {
      h += smallRowH + graphH + pad * 0.5f;
    }
    panelW = (int) Math.ceil(w + pad * 2);
    panelH = (int) Math.ceil(h + pad * 2);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    synchronized (uiLock) {
      setMeasuredDimension(panelW, panelH);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    synchronized (uiLock) {
      canvas.drawRect(0, 0, panelW, panelH, bgPaint);
      float y = pad;
      if (elements[EL_GPU_LOAD] || elements[EL_GPU_TEMP]) {
        float x = drawLabel(canvas, "GPU", C_GPU, y);
        if (elements[EL_GPU_LOAD]) x = drawStatCell(canvas, sbGpuLoad, "%", x, y);
        if (elements[EL_GPU_TEMP]) drawStatCell(canvas, sbGpuTemp, "°C", x, y);
        y += rowH;
      }
      if (elements[EL_CPU_LOAD] || elements[EL_CPU_TEMP]) {
        float x = drawLabel(canvas, "CPU", C_CPU, y);
        if (elements[EL_CPU_LOAD]) x = drawStatCell(canvas, sbCpuLoad, "%", x, y);
        if (elements[EL_CPU_TEMP]) drawStatCell(canvas, sbCpuTemp, "°C", x, y);
        y += rowH;
      }
      if (elements[EL_VRAM]) {
        float x = drawLabel(canvas, "VRAM", C_VRAM, y);
        drawStatCell(canvas, sbVram, "GiB", x, y);
        y += rowH;
      }
      if (elements[EL_RAM]) {
        float x = drawLabel(canvas, "RAM", C_RAM, y);
        drawStatCell(canvas, sbRam, "GiB", x, y);
        y += rowH;
      }
      if (elements[EL_BATTERY]) {
        float x = drawLabel(canvas, "BAT", C_BAT, y);
        x = drawStatCell(canvas, sbBatPct, "%", x, y);
        drawStatCell(canvas, sbBatW, "W", x, y);
        y += rowH;
      }
      {
        float x = elements[EL_ENGINE]
            ? drawLabel(canvas, engineName, C_ENGINE, y)
            : pad + labelColW;
        x = drawStatCell(canvas, sbFps, "FPS", x, y);
        drawStatCell(canvas, sbMs, "ms", x, y);
        y += rowH;
      }
      if (elements[EL_LOWS]) {
        float x = drawLabel(canvas, "AVG", C_TEXT, y);
        drawStatCell(canvas, sbAvg, "FPS", x, y);
        y += rowH;
        x = drawLabel(canvas, "1%", C_TEXT, y);
        drawStatCell(canvas, sbLow1, "FPS", x, y);
        y += rowH;
        x = drawLabel(canvas, "0.1%", C_TEXT, y);
        drawStatCell(canvas, sbLow01, "FPS", x, y);
        y += rowH;
      }
      if (elements[EL_GRAPH]) {
        float sy = y + smallBaseline;
        drawOutlinedSmall(canvas, "Frametime", 0, 9, pad, sy, C_ENGINE);
        float mmW = sbMinMax.length() * smallCharW;
        drawOutlinedSmall(canvas, sbMinMax, 0, sbMinMax.length(), panelW - pad - mmW, sy, C_TEXT);
        y += smallRowH + pad * 0.5f;
        drawGraph(canvas, pad, y, panelW - pad * 2, graphH);
      }
    }
  }

  private float drawLabel(Canvas canvas, CharSequence text, int color, float rowTop) {
    float y = rowTop + baseline;
    outlinePaint.setTextSize(textSize);
    canvas.drawText(text, 0, text.length(), pad, y, outlinePaint);
    labelPaint.setColor(color);
    labelPaint.setAlpha(textAlphaInt());
    canvas.drawText(text, 0, text.length(), pad, y, labelPaint);
    return pad + labelColW;
  }

  private float drawStatCell(Canvas canvas, StringBuilder value, String unit, float x, float rowTop) {
    float y = rowTop + baseline;
    float vx = x + charW * 4 - value.length() * charW;
    outlinePaint.setTextSize(textSize);
    canvas.drawText(value, 0, value.length(), vx, y, outlinePaint);
    canvas.drawText(value, 0, value.length(), vx, y, valuePaint);
    float ux = x + charW * 4 + smallCharW * 0.2f;
    drawOutlinedSmall(canvas, unit, 0, unit.length(), ux, y, C_TEXT);
    return x + statCellW(unit.length());
  }

  private void drawOutlinedSmall(Canvas canvas, CharSequence text, int start, int end,
      float x, float y, int color) {
    outlinePaint.setTextSize(smallSize);
    canvas.drawText(text, start, end, x, y, outlinePaint);
    smallPaint.setColor(color);
    smallPaint.setAlpha(textAlphaInt());
    canvas.drawText(text, start, end, x, y, smallPaint);
  }

  private void drawGraph(Canvas canvas, float left, float top, float width, float height) {
    int count;
    synchronized (frameLock) {
      count = graphCount;
      for (int i = 0; i < count; i++) {
        graphSnapshot[i] = graphMs[(graphIndex - count + i + GRAPH_SAMPLES) % GRAPH_SAMPLES];
      }
    }
    if (count < 2) return;
    float stepX = width / (GRAPH_SAMPLES - 1);
    float startX = left + (GRAPH_SAMPLES - count) * stepX;
    int segments = 0;
    float prevX = startX;
    float prevY = graphY(graphSnapshot[0], top, height);
    for (int i = 1; i < count; i++) {
      float cx = startX + i * stepX;
      float cy = graphY(graphSnapshot[i], top, height);
      int base = segments * 4;
      graphLines[base] = prevX;
      graphLines[base + 1] = prevY;
      graphLines[base + 2] = cx;
      graphLines[base + 3] = cy;
      segments++;
      prevX = cx;
      prevY = cy;
    }
    canvas.drawLines(graphLines, 0, segments * 4, graphPaint);
  }

  private static float graphY(float ms, float top, float height) {
    if (ms > GRAPH_CEIL_MS - 0.1f) ms = GRAPH_CEIL_MS - 0.1f;
    if (ms < 0f) ms = 0f;
    return top + height - (ms / GRAPH_CEIL_MS) * height;
  }

  // ── touch: drag to move, double-tap to cycle size ──

  private int activePointerId = -1;
  private float touchOffsetX, touchOffsetY, downRawX, downRawY;
  private float lastTapX, lastTapY;
  private long downTime, lastTapTime;
  private boolean dragging;
  private static final float TAP_SLOP = 20f;
  private static final float DOUBLE_TAP_SLOP = 48f;
  private static final long TAP_MS = 300L;

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getPointerCount() > 1) {
      activePointerId = -1;
      return false;
    }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        activePointerId = event.getPointerId(0);
        touchOffsetX = getX() - event.getRawX();
        touchOffsetY = getY() - event.getRawY();
        downRawX = event.getRawX();
        downRawY = event.getRawY();
        downTime = SystemClock.elapsedRealtime();
        dragging = false;
        bringToFront();
        return true;
      case MotionEvent.ACTION_MOVE:
        if (activePointerId != -1) {
          if (Math.abs(event.getRawX() - downRawX) > TAP_SLOP
              || Math.abs(event.getRawY() - downRawY) > TAP_SLOP) {
            dragging = true;
          }
          if (dragging) {
            setX(event.getRawX() + touchOffsetX);
            setY(event.getRawY() + touchOffsetY);
            clampToParentBounds();
          }
          return true;
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (activePointerId != -1) {
          long now = SystemClock.elapsedRealtime();
          if (dragging) {
            clampToParentBounds();
            preferences.edit()
                .putBoolean(PREF_HAS_POSITION, true)
                .putFloat(PREF_POS_X, getX())
                .putFloat(PREF_POS_Y, getY())
                .apply();
            lastTapTime = 0;
          } else if (now - downTime < 400) {
            boolean nearLastTap = Math.abs(downRawX - lastTapX) < DOUBLE_TAP_SLOP * density
                && Math.abs(downRawY - lastTapY) < DOUBLE_TAP_SLOP * density;
            if (lastTapTime != 0 && now - lastTapTime < TAP_MS && nearLastTap) {
              lastTapTime = 0;
              cycleScale();
            } else {
              lastTapTime = now;
              lastTapX = downRawX;
              lastTapY = downRawY;
            }
          }
          activePointerId = -1;
          return true;
        }
        return false;
    }
    return false;
  }

  private void cycleScale() {
    synchronized (uiLock) {
      scaleIndex = (scaleIndex + 1) % SCALE_STEPS.length;
      preferences.edit().putInt(PREF_SCALE_INDEX, scaleIndex).apply();
      applyScaleLocked();
      computeLayoutLocked();
    }
    requestLayout();
    invalidate();
    post(this::clampToParentBounds);
  }

  private static int clampScaleIndex(int index) {
    return Math.max(0, Math.min(index, SCALE_STEPS.length - 1));
  }

  private void clampToParentBounds() {
    View parentView = (View) getParent();
    if (parentView == null || parentView.getWidth() <= 0 || parentView.getHeight() <= 0
        || getWidth() <= 0 || getHeight() <= 0) {
      return;
    }
    float maxX = Math.max(0f, parentView.getWidth() - getWidth());
    float maxY = Math.max(0f, parentView.getHeight() - getHeight());
    setX(Math.max(0f, Math.min(getX(), maxX)));
    setY(Math.max(0f, Math.min(getY(), maxY)));
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    setElevation(1000.0f);
    if (preferences.getBoolean(PREF_HAS_POSITION, false)) {
      post(() -> {
        setX(preferences.getFloat(PREF_POS_X, getX()));
        setY(preferences.getFloat(PREF_POS_Y, getY()));
        clampToParentBounds();
      });
    }
    // Attached ⇒ loop running, no visibility condition — a view that renders is
    // always attached, so the HUD can never be on screen with a dead loop.
    startStats();
  }

  @Override
  protected void onDetachedFromWindow() {
    stopStats();
    super.onDetachedFromWindow();
  }
}
