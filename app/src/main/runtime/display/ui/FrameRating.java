package com.winlator.cmod.runtime.display.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.system.CPUStatus;
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
  public static final String PREF_HUD_SCALE = "hud_scale";
  public static final String PREF_HUD_ALPHA = "hud_alpha";
  public static final String PREF_HUD_ELEMENTS = "hud_elements";
  public static final String PREF_HUD_ANCHOR = "hud_anchor";

  // Anchor positions for the long-press position menu
  private static final int ANCHOR_NONE = -1;
  private static final int ANCHOR_TOP_LEFT = 0;
  private static final int ANCHOR_TOP_CENTER = 1;
  private static final int ANCHOR_TOP_RIGHT = 2;
  private static final int ANCHOR_BOTTOM_LEFT = 3;
  private static final int ANCHOR_BOTTOM_CENTER = 4;
  private static final int ANCHOR_BOTTOM_RIGHT = 5;
  private static final int ANCHOR_LEFT_CENTER = 6;
  private static final int ANCHOR_RIGHT_CENTER = 7;
  private int currentAnchor = ANCHOR_NONE;
  private PopupWindow positionPopup;
  private ViewTreeObserver.OnGlobalLayoutListener parentLayoutListener;
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
  private volatile float currentMs;
  private boolean enableBattTemp;
  private boolean enableCpu;
  private boolean enableRam;
  private boolean enableFps;
  private boolean enableGpu;
  private boolean enableGraph;
  private boolean enableRenderer;
  private volatile FrameObserver frameObserver;
  private int gpuFailCount;
  private volatile int gpuLoad;

  /**
   * Listener for raw per-present frame events. Fires on the X server render thread on
   * every call to {@link #update()} regardless of HUD visibility — that way perf
   * recording and leaderboard stats still work when the HUD is hidden. Implementations
   * must be cheap (a single atomic op + array write is the budget).
   */
  public interface FrameObserver {
    void onFramePresent(long nanoTime);
  }

  /**
   * Install or remove the frame observer. Passing null clears it. Replacing an
   * existing observer is allowed (last-writer-wins); this is intentional for cases
   * where the activity replaces the FrameRating instance mid-session.
   */
  public void setFrameObserver(FrameObserver observer) {
    this.frameObserver = observer;
  }
  private FrametimeGraphView graphView;
  private boolean isNativeActive;
  private boolean isStatsRunning;
  private volatile boolean isCharging;
  private volatile float lastFPS;
  private volatile long lastFrameNano;
  private long lastPrimaryFrameNano;
  private long lastGraphRedraw;
  private long lastHudRedraw;
  private volatile String ramText;
  private final long[] frameTimesNano = new long[MAX_FRAME_SAMPLES];
  private int frameTimesStart;
  private int frameTimesCount;
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
  private static final long FALLBACK_SUPPRESSION_NS = 2000000000L;
  private static final long FPS_CALC_INTERVAL_NS = 1000000000L;
  private static final long HUD_REFRESH_MS = 1000L;
  private static final long MIN_FRAME_INTERVAL_NS = 1000000L;
  private static final int MAX_FRAME_SAMPLES = 1024;

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

  public FrameRating(
      Context context, HashMap graphicsDriverConfig, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    this.lastGraphRedraw = 0L;
    this.lastFrameNano = 0L;
    this.lastPrimaryFrameNano = 0L;
    this.lastHudRedraw = 0L;
    this.frameTimesStart = 0;
    this.frameTimesCount = 0;
    this.lastFPS = 0.0f;
    this.currentMs = 0.0f;
    this.enableFps = true;
    this.enableGraph = true;
    this.enableGpu = true;
    this.enableCpu = true;
    this.enableRam = true;
    this.enableBattTemp = true;
    this.enableRenderer = true;
    this.cpuPercent = -1;
    this.gpuLoad = -1;
    this.batteryWatts = -1.0f;
    this.cpuTemp = -1;
    this.ramText = "N/A";
    this.rendererName = "Vulkan";
    this.gpuName = null;
    this.canReadGpu = true;
    this.canReadCpu = true;
    this.canReadBatt = true;
    this.gpuFailCount = 0;
    this.cpuFailCount = 0;
    this.battFailCount = 0;
    this.lastGoodGpuLoad = -1;
    this.lastGoodGpuTime = 0;
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
    this.isNativeActive = this.preferences.getBoolean("use_dri3", true);
    this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    setOrientation(LinearLayout.HORIZONTAL);
    setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
      this.tvRenderer.setText("Vulkan");
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
    this.statsRunnable =
        new Runnable() {
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
    this.uiRefreshRunnable =
        new Runnable() {
          @Override
          public void run() {
            if (isStatsRunning) {
              FrameRating.this.run();
              uiRefreshHandler.postDelayed(this, HUD_REFRESH_MS);
            }
          }
        };
    this.uiRefreshHandler.postDelayed(this.uiRefreshRunnable, HUD_REFRESH_MS);
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
    installParentLayoutListener();
    removeCallbacks(this);
    post(this);
    startStatsUpdate();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    removeCallbacks(this);
    removeParentLayoutListener();
    dismissPositionPopup();
    stopStatsUpdate();
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (changed) {
      post(
          () -> {
            if (currentAnchor != ANCHOR_NONE) {
              applyAnchor(currentAnchor, false);
            } else {
              clampToParentBounds(this);
            }
          });
    }
  }

  private void installParentLayoutListener() {
    final View parentView = (View) getParent();
    if (parentView == null) {
      return;
    }
    removeParentLayoutListener();
    this.parentLayoutListener =
        () -> {
          if (currentAnchor != ANCHOR_NONE) {
            applyAnchor(currentAnchor, false);
          } else {
            clampToParentBounds(this);
          }
        };
    parentView.getViewTreeObserver().addOnGlobalLayoutListener(this.parentLayoutListener);
  }

  private void removeParentLayoutListener() {
    if (this.parentLayoutListener == null) {
      return;
    }
    View parentView = (View) getParent();
    if (parentView != null) {
      parentView.getViewTreeObserver().removeOnGlobalLayoutListener(this.parentLayoutListener);
    }
    this.parentLayoutListener = null;
  }

  // ── Touch: tap cycles display mode, drag moves HUD, long-press shows menu ──
  private void setupTapAndDragListener() {
    setOnTouchListener(
        new View.OnTouchListener() {
          private int activePointerId = -1;
          private float dX, dY;
          private float downRawX, downRawY;
          private long downTime;
          private boolean isDragging = false;
          private boolean longPressFired = false;
          private final Handler longPressHandler = new Handler(Looper.getMainLooper());
          private final Runnable longPressRunnable =
              new Runnable() {
                @Override
                public void run() {
                  if (!isDragging && activePointerId != -1) {
                    longPressFired = true;
                    showPositionMenu();
                  }
                }
              };
          private static final float TAP_SLOP = 20f;
          private static final long LONG_PRESS_MS = 500L;

          @Override
          public boolean onTouch(View view, MotionEvent event) {
            if (event.getPointerCount() > 1) {
              this.activePointerId = -1;
              this.longPressHandler.removeCallbacks(this.longPressRunnable);
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
                this.longPressFired = false;
                view.bringToFront();
                this.longPressHandler.removeCallbacks(this.longPressRunnable);
                this.longPressHandler.postDelayed(this.longPressRunnable, LONG_PRESS_MS);
                return true;
              case MotionEvent.ACTION_MOVE:
                if (this.activePointerId != -1) {
                  float dx = Math.abs(event.getRawX() - this.downRawX);
                  float dy = Math.abs(event.getRawY() - this.downRawY);
                  if (dx > TAP_SLOP || dy > TAP_SLOP) {
                    this.isDragging = true;
                    this.longPressHandler.removeCallbacks(this.longPressRunnable);
                  }
                  if (this.isDragging && !this.longPressFired) {
                    view.setX(event.getRawX() + this.dX);
                    view.setY(event.getRawY() + this.dY);
                    clampToParentBounds(view);
                  }
                  return true;
                }
                break;
              case MotionEvent.ACTION_UP:
              case MotionEvent.ACTION_CANCEL:
                this.longPressHandler.removeCallbacks(this.longPressRunnable);
                if (this.activePointerId != -1) {
                  long elapsed = SystemClock.elapsedRealtime() - this.downTime;
                  if (this.longPressFired) {
                    // Menu was shown — consume the up event
                  } else if (!this.isDragging && elapsed < 400) {
                    // Short tap → cycle display mode
                    cycleDisplayMode();
                  } else if (this.isDragging) {
                    clampToParentBounds(view);
                    persistPosition(view.getX(), view.getY());
                    // Manual drag clears any anchor lock
                    currentAnchor = ANCHOR_NONE;
                    preferences.edit().putInt(PREF_HUD_ANCHOR, ANCHOR_NONE).apply();
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
    this.currentAnchor = this.preferences.getInt(PREF_HUD_ANCHOR, ANCHOR_NONE);
  }

  private void restorePersistedPosition() {
    if (!this.preferences.getBoolean(PREF_HUD_HAS_POSITION, false)) {
      return;
    }
    post(
        () -> {
          setX(this.preferences.getFloat(PREF_HUD_POS_X, getX()));
          setY(this.preferences.getFloat(PREF_HUD_POS_Y, getY()));
          clampToParentBounds(this);
        });
  }

  private void persistPosition(float x, float y) {
    this.preferences
        .edit()
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
    if (parentView.getWidth() <= 0
        || parentView.getHeight() <= 0
        || view.getWidth() <= 0
        || view.getHeight() <= 0) {
      return;
    }

    // Scale pivot is (0,0) — visible bounds extend by width*scaleX, height*scaleY
    float scaledW = view.getWidth() * Math.max(view.getScaleX(), 0.01f);
    float scaledH = view.getHeight() * Math.max(view.getScaleY(), 0.01f);
    float maxX = Math.max(0f, parentView.getWidth() - scaledW);
    float maxY = Math.max(0f, parentView.getHeight() - scaledH);
    view.setX(Math.max(0f, Math.min(view.getX(), maxX)));
    view.setY(Math.max(0f, Math.min(view.getY(), maxY)));
  }

  // ── Long-press position menu ─────────────────────────────────────
  private int dp(float v) {
    return (int)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }

  private void showPositionMenu() {
    if (!isAttachedToWindow()) {
      return;
    }
    dismissPositionPopup();

    int surface = androidx.core.content.ContextCompat.getColor(context, R.color.settings_popup_surface);
    int edge = androidx.core.content.ContextCompat.getColor(context, R.color.settings_popup_surface_edge);
    int textColor = androidx.core.content.ContextCompat.getColor(context, R.color.settings_text_primary);
    int rippleColor = 0x33A0C8FF;

    GradientDrawable bg = new GradientDrawable();
    bg.setColor(surface);
    bg.setCornerRadius(dp(10));
    bg.setStroke(dp(1), edge);

    LinearLayout menuLayout = new LinearLayout(context);
    menuLayout.setOrientation(LinearLayout.VERTICAL);
    menuLayout.setBackground(bg);
    menuLayout.setPadding(dp(5), dp(5), dp(5), dp(5));
    menuLayout.setElevation(dp(8));

    TextView header = new TextView(context);
    header.setText(R.string.hud_position_menu_title);
    header.setTextColor(textColor);
    header.setAlpha(0.7f);
    header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
    header.setGravity(Gravity.CENTER_HORIZONTAL);
    header.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));
    header.setPadding(dp(4), dp(1), dp(4), dp(4));
    menuLayout.addView(header);

    GridLayout grid = new GridLayout(context);
    grid.setColumnCount(3);
    grid.setRowCount(3);

    final int cellSize = dp(36);
    final int cellMargin = dp(1);
    final int iconPadding = dp(7);

    int[][] cells = {
      {ANCHOR_TOP_LEFT,    R.drawable.ic_hud_arrow_north_west},
      {ANCHOR_TOP_CENTER,  R.drawable.ic_hud_arrow_north},
      {ANCHOR_TOP_RIGHT,   R.drawable.ic_hud_arrow_north_east},
      {ANCHOR_LEFT_CENTER, R.drawable.ic_hud_arrow_west},
      {-1,                 0}, // empty middle
      {ANCHOR_RIGHT_CENTER,R.drawable.ic_hud_arrow_east},
      {ANCHOR_BOTTOM_LEFT, R.drawable.ic_hud_arrow_south_west},
      {ANCHOR_BOTTOM_CENTER,R.drawable.ic_hud_arrow_south},
      {ANCHOR_BOTTOM_RIGHT,R.drawable.ic_hud_arrow_south_east},
    };

    for (int[] cell : cells) {
      final int anchor = cell[0];
      final int iconRes = cell[1];

      GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
      lp.width = cellSize;
      lp.height = cellSize;
      lp.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);

      if (anchor == -1) {
        View placeholder = new View(context);
        placeholder.setLayoutParams(lp);
        grid.addView(placeholder);
        continue;
      }

      ImageView item = new ImageView(context);
      item.setLayoutParams(lp);
      item.setImageResource(iconRes);
      item.setScaleType(ImageView.ScaleType.FIT_CENTER);
      item.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
      item.setBackground(buildItemRipple(rippleColor));
      item.setClickable(true);
      item.setFocusable(true);
      item.setContentDescription(getResources().getString(labelForAnchor(anchor)));
      item.setOnClickListener(
          v -> {
            applyAnchor(anchor, true);
            dismissPositionPopup();
          });
      grid.addView(item);
    }

    menuLayout.addView(grid);

    PopupWindow popup = new PopupWindow(menuLayout,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true);
    popup.setOutsideTouchable(true);
    popup.setElevation(dp(8));
    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
    this.positionPopup = popup;

    menuLayout.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int popupW = menuLayout.getMeasuredWidth();
    int popupH = menuLayout.getMeasuredHeight();

    View parentView = (View) getParent();
    int parentW = parentView != null ? parentView.getWidth() : popupW;
    int parentH = parentView != null ? parentView.getHeight() : popupH;
    int[] parentLoc = new int[2];
    if (parentView != null) parentView.getLocationOnScreen(parentLoc);

    int hudCenterX = (int) (getX() + (getWidth() * getScaleX()) / 2f);
    int hudCenterY = (int) (getY() + (getHeight() * getScaleY()) / 2f);
    int x = Math.max(dp(8), Math.min(hudCenterX - popupW / 2, parentW - popupW - dp(8)));
    int y = Math.max(dp(8), Math.min(hudCenterY - popupH / 2, parentH - popupH - dp(8)));

    popup.showAtLocation(parentView != null ? parentView : this, Gravity.NO_GRAVITY,
        parentLoc[0] + x, parentLoc[1] + y);
  }

  private int labelForAnchor(int anchor) {
    switch (anchor) {
      case ANCHOR_TOP_LEFT: return R.string.hud_position_top_left;
      case ANCHOR_TOP_CENTER: return R.string.hud_position_top_center;
      case ANCHOR_TOP_RIGHT: return R.string.hud_position_top_right;
      case ANCHOR_LEFT_CENTER: return R.string.hud_position_left_center;
      case ANCHOR_RIGHT_CENTER: return R.string.hud_position_right_center;
      case ANCHOR_BOTTOM_LEFT: return R.string.hud_position_bottom_left;
      case ANCHOR_BOTTOM_CENTER: return R.string.hud_position_bottom_center;
      case ANCHOR_BOTTOM_RIGHT: return R.string.hud_position_bottom_right;
      default: return R.string.hud_position_menu_title;
    }
  }

  private android.graphics.drawable.Drawable buildItemRipple(int rippleColor) {
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(0xFFFFFFFF);
    mask.setCornerRadius(dp(8));
    return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
  }

  private void dismissPositionPopup() {
    if (this.positionPopup != null) {
      try {
        this.positionPopup.dismiss();
      } catch (Exception ignored) {
      }
      this.positionPopup = null;
    }
  }

  private void applyAnchor(int anchor, boolean persist) {
    View parentView = (View) getParent();
    if (parentView == null
        || parentView.getWidth() <= 0
        || parentView.getHeight() <= 0
        || getWidth() <= 0
        || getHeight() <= 0) {
      // Defer until we have valid dimensions
      this.currentAnchor = anchor;
      if (persist) {
        preferences.edit().putInt(PREF_HUD_ANCHOR, anchor).apply();
      }
      post(
          () -> {
            if (getWidth() > 0 && getHeight() > 0) {
              applyAnchor(anchor, false);
            }
          });
      return;
    }

    float scaledW = getWidth() * Math.max(getScaleX(), 0.01f);
    float scaledH = getHeight() * Math.max(getScaleY(), 0.01f);
    float maxX = Math.max(0f, parentView.getWidth() - scaledW);
    float maxY = Math.max(0f, parentView.getHeight() - scaledH);
    float centerX = Math.max(0f, (parentView.getWidth() - scaledW) / 2f);
    float centerY = Math.max(0f, (parentView.getHeight() - scaledH) / 2f);

    float targetX, targetY;
    switch (anchor) {
      case ANCHOR_TOP_LEFT:
        targetX = 0f;
        targetY = 0f;
        break;
      case ANCHOR_TOP_CENTER:
        targetX = centerX;
        targetY = 0f;
        break;
      case ANCHOR_TOP_RIGHT:
        targetX = maxX;
        targetY = 0f;
        break;
      case ANCHOR_LEFT_CENTER:
        targetX = 0f;
        targetY = centerY;
        break;
      case ANCHOR_RIGHT_CENTER:
        targetX = maxX;
        targetY = centerY;
        break;
      case ANCHOR_BOTTOM_LEFT:
        targetX = 0f;
        targetY = maxY;
        break;
      case ANCHOR_BOTTOM_CENTER:
        targetX = centerX;
        targetY = maxY;
        break;
      case ANCHOR_BOTTOM_RIGHT:
        targetX = maxX;
        targetY = maxY;
        break;
      default:
        return;
    }

    targetX = Math.max(0f, Math.min(targetX, maxX));
    targetY = Math.max(0f, Math.min(targetY, maxY));
    setX(targetX);
    setY(targetY);
    this.currentAnchor = anchor;
    persistPosition(targetX, targetY);
    if (persist) {
      preferences.edit().putInt(PREF_HUD_ANCHOR, anchor).apply();
    }
  }

  private void applyDisplayMode() {
    boolean horizontal;
    boolean showBackdrop;
    switch (displayMode) {
      case 0:
        horizontal = true;
        showBackdrop = false;
        break;
      case 1:
        horizontal = true;
        showBackdrop = true;
        break;
      case 2:
        horizontal = false;
        showBackdrop = false;
        break;
      case 3:
        horizontal = false;
        showBackdrop = true;
        break;
      default:
        horizontal = true;
        showBackdrop = false;
        break;
    }
    setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    setGravity(horizontal ? android.view.Gravity.CENTER_VERTICAL : android.view.Gravity.START);
    setBackground(showBackdrop ? backdropDrawable : null);
    setPadding(
        showBackdrop ? 8 : 2, showBackdrop ? 6 : 2, showBackdrop ? 8 : 2, showBackdrop ? 6 : 2);

    int graphW = (int) (50 * getResources().getDisplayMetrics().density);
    int graphH = (int) (14 * getResources().getDisplayMetrics().density);

    View[] views = {
      tvRenderer,
      sep0,
      tvGpuLoad,
      sep1,
      tvCpu,
      sep2,
      tvRam,
      sep3,
      tvBat,
      sep4,
      tvTemp,
      sep5,
      tvFpsBig,
      graphContainer
    };
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
    if (r.contains("vkd3d")) {
      this.rendererName = "VKD3D";
    } else if (r.contains("dxvk")) {
      this.rendererName = "DXVK";
    } else if (r.contains("turnip")) {
      this.rendererName = "Turnip";
    } else if (r.contains("zink")) {
      this.rendererName = "Zink";
    } else if (r.contains("llvmpipe") || r.contains("software")) {
      this.rendererName = "Software";
    } else if (r.contains("vulkan")) {
      this.rendererName = "Vulkan";
    } else if (r.contains("opengl")) {
      this.rendererName = "OpenGL";
    } else {
      this.rendererName =
          renderer
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
      post(
          new Runnable() {
            @Override
            public void run() {
              updateRendererText();
            }
          });
    }
  }

  private void updateRendererText() {
    if (this.tvRenderer != null) {
      String text = this.rendererName + (this.isNativeActive ? "+" : "");
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
      clean =
          clean.replaceAll("(?i)Wrapper\\((.*)\\)", "$1").replaceAll("(?i)Wrapper\\s*", "").trim();

      if (!clean.isEmpty()) {
        this.gpuName = clean;
        post(this::updateRendererText);
      }
    } else {
      this.gpuName = null;
      detectGpuNameFromSysfs();
    }
  }

  /** Attempt to detect GPU name from sysfs (works for Qualcomm Adreno). */
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

  public synchronized void reset() {
    this.lastFrameNano = 0L;
    this.lastPrimaryFrameNano = 0L;
    this.lastHudRedraw = 0L;
    this.frameTimesStart = 0;
    this.frameTimesCount = 0;
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

  public void setHudAlpha(float alpha) {
    setAlpha(alpha);
  }

  public void setHudScale(float scale) {
    setScaleX(scale);
    setScaleY(scale);
    setPivotX(0);
    setPivotY(0);
    this.preferences.edit().putFloat(PREF_HUD_SCALE, scale).apply();
  }

  public void setDualSeriesBattery(boolean dualSeriesBattery) {
    this.dualSeriesBattery = dualSeriesBattery;
    this.preferences.edit().putBoolean(PREF_HUD_DUAL_SERIES_BATTERY, dualSeriesBattery).apply();
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
        this.enableCpu = visible;
        if (this.tvCpu != null) this.tvCpu.setVisibility(v);
        break;
      case 4:
        this.enableRam = visible;
        if (this.tvRam != null) this.tvRam.setVisibility(v);
        break;
      case 5:
        this.enableBattTemp = visible;
        if (this.tvBat != null) this.tvBat.setVisibility(v);
        if (this.tvTemp != null) this.tvTemp.setVisibility(v);
        break;
      case 6:
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

    if (sep0 != null)
      sep0.setVisibility(
          vRen && (vGpu || vCpu || vRam || vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
    if (sep1 != null)
      sep1.setVisibility(vGpu && (vCpu || vRam || vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
    if (sep2 != null)
      sep2.setVisibility(vCpu && (vRam || vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
    if (sep3 != null) sep3.setVisibility(vRam && (vBat || vTmp || vFps) ? View.VISIBLE : View.GONE);
    if (sep4 != null) sep4.setVisibility(vBat && (vTmp || vFps) ? View.VISIBLE : View.GONE);
    if (sep5 != null) sep5.setVisibility(vTmp && vFps ? View.VISIBLE : View.GONE);
  }

  /** Called when the guest submits a new frame to the X presentation path. */
  public void recordGameFrame(boolean primarySource, int serial) {
    // Notify observer before any visibility gating so perf recording / leaderboard stats keep
    // working when the HUD is hidden. Cheap path; observer is typically a single AtomicLong
    // increment plus an ArrayList add.
    FrameObserver obs = this.frameObserver;
    if (obs != null) {
      obs.onFramePresent(System.nanoTime());
    }
    if (getVisibility() != View.VISIBLE) {
      return;
    }
    long nowNano = System.nanoTime();

    synchronized (this) {
      if (primarySource) {
        if (this.lastPrimaryFrameNano == 0
            || nowNano - this.lastPrimaryFrameNano >= FALLBACK_SUPPRESSION_NS) {
          this.lastFrameNano = 0L;
          this.frameTimesStart = 0;
          this.frameTimesCount = 0;
        }
        this.lastPrimaryFrameNano = nowNano;
      } else if (this.lastPrimaryFrameNano > 0
          && nowNano - this.lastPrimaryFrameNano < FALLBACK_SUPPRESSION_NS) {
        return;
      }

      if (this.lastFrameNano > 0 && nowNano - this.lastFrameNano < MIN_FRAME_INTERVAL_NS) {
        return;
      }
      if (this.lastFrameNano == 0) {
        this.lastFrameNano = nowNano;
      }
      float ms = (nowNano - this.lastFrameNano) / 1000000.0f;
      this.lastFrameNano = nowNano;

      long time = SystemClock.elapsedRealtime();
      appendFrameTimeLocked(nowNano);
      trimFrameTimesLocked(nowNano - FPS_CALC_INTERVAL_NS);
      updateRollingFpsLocked();
      boolean shouldRedrawHud = false;
      if (time - this.lastHudRedraw >= HUD_REFRESH_MS) shouldRedrawHud = true;

      if (ms > 0.0f && ms < 500.0f) {
        this.currentMs = ms;
      }
      if (this.enableGraph && ms > 0.0f && ms < 500.0f && time - this.lastGraphRedraw >= 50) {
        if (this.graphView != null) {
          this.graphView.addFrame(ms);
          this.graphView.postInvalidate();
        }
        this.lastGraphRedraw = time;
      }

      if (!shouldRedrawHud && time - this.lastHudRedraw < HUD_REFRESH_MS) {
        return;
      }
      this.lastHudRedraw = time;
    }
    post(this);
  }

  public void recordGameFrame() {
    recordGameFrame(false, 0);
  }

  private void appendFrameTimeLocked(long nowNano) {
    int index = (this.frameTimesStart + this.frameTimesCount) % MAX_FRAME_SAMPLES;
    if (this.frameTimesCount == MAX_FRAME_SAMPLES) {
      this.frameTimesStart = (this.frameTimesStart + 1) % MAX_FRAME_SAMPLES;
      index = (this.frameTimesStart + this.frameTimesCount - 1) % MAX_FRAME_SAMPLES;
    } else {
      this.frameTimesCount++;
    }
    this.frameTimesNano[index] = nowNano;
  }

  private void trimFrameTimesLocked(long oldestAllowedNano) {
    while (this.frameTimesCount > 0
        && this.frameTimesNano[this.frameTimesStart] < oldestAllowedNano) {
      this.frameTimesStart = (this.frameTimesStart + 1) % MAX_FRAME_SAMPLES;
      this.frameTimesCount--;
    }
  }

  private void updateRollingFpsLocked() {
    if (this.frameTimesCount <= 1) {
      this.lastFPS = 0.0f;
      return;
    }

    long first = this.frameTimesNano[this.frameTimesStart];
    int lastIndex = (this.frameTimesStart + this.frameTimesCount - 1) % MAX_FRAME_SAMPLES;
    long last = this.frameTimesNano[lastIndex];
    long elapsedNano = last - first;
    this.lastFPS =
        elapsedNano > 0 ? ((this.frameTimesCount - 1) * 1000000000.0f) / elapsedNano : 0.0f;
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
        } catch (Exception ignored) {
        }
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
            if (total != 0) return (int) ((100 * busy) / total);
          }
        }
      } catch (Exception ignored) {
      }
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
    if (this.enableCpu && this.canReadCpu) {
      try {
        short[] clocks = CPUStatus.getCurrentClockSpeeds();
        if (clocks != null && clocks.length > 0) {
          long cur = 0;
          long max = 0;
          for (int i = 0; i < clocks.length; i++) {
            cur += clocks[i];
            max += CPUStatus.getMaxClockSpeed(i);
          }
          if (max > 0) {
            this.cpuPercent = (int) ((cur * 100) / max);
            this.cpuFailCount = 0;
          }
        }
      } catch (Exception e) {
        this.cpuPercent = -1;
        this.cpuFailCount++;
      }
    }
    if (this.enableRam) {
      try {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        this.ramText = ((100 * used) / mi.totalMem) + "%";
      } catch (Exception e) {
        this.ramText = "N/A";
      }
    }
    if (this.enableBattTemp && this.canReadBatt) {
      try {
        float amps = getBatteryCurrentAmps();
        Intent intent =
            context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int mv = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
        if (mv > 0 && amps > 0.0f) this.batteryWatts = (mv / 1000.0f) * amps;
        else this.batteryWatts = -1.0f;
        if (intent != null) {
          int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
          if (temp > 0) this.cpuTemp = temp / 10;
          else this.cpuTemp = -1;

          int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
          this.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        this.battFailCount = 0;
      } catch (Exception e) {
        this.batteryWatts = -1.0f;
        this.cpuTemp = -1;
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
      synchronized (this) {
        this.lastFPS = 0.0f;
        this.currentMs = 0.0f;
        this.frameTimesStart = 0;
        this.frameTimesCount = 0;
      }
    }

    if (this.enableGpu && this.tvGpuLoad != null) {
      SpannableStringBuilder b = new SpannableStringBuilder();
      append(b, "GPU ", this.C_GPU);
      append(b, this.gpuLoad >= 0 ? this.gpuLoad + "%" : "N/A", this.C_VALUE);
      this.tvGpuLoad.setText(b);
      this.tvGpuLoad.setVisibility(View.VISIBLE);
    } else if (this.tvGpuLoad != null) this.tvGpuLoad.setVisibility(View.GONE);

    if (this.enableCpu && this.tvCpu != null) {
      SpannableStringBuilder b = new SpannableStringBuilder();
      append(b, "CPU ", this.C_CPU);
      append(b, this.cpuPercent >= 0 ? this.cpuPercent + "%" : "N/A", this.C_VALUE);
      this.tvCpu.setText(b);
      this.tvCpu.setVisibility(View.VISIBLE);
    } else if (this.tvCpu != null) {
      this.tvCpu.setVisibility(View.GONE);
    }

    if (this.enableRam && this.tvRam != null) {
      SpannableStringBuilder b = new SpannableStringBuilder();
      append(b, "RAM ", this.C_RAM);
      append(b, this.ramText, this.C_VALUE);
      this.tvRam.setText(b);
      this.tvRam.setVisibility(View.VISIBLE);
    } else if (this.tvRam != null) {
      this.tvRam.setVisibility(View.GONE);
    }

    if (this.enableBattTemp) {
      if (this.tvBat != null) {
        float displayedBatteryWatts =
            this.batteryWatts >= 0.0f && this.dualSeriesBattery
                ? this.batteryWatts * 2.0f
                : this.batteryWatts;
        SpannableStringBuilder b = new SpannableStringBuilder();
        append(b, "BAT ", this.C_BAT);
        if (this.isCharging) {
          append(b, "CHRG", this.C_FPS_OK);
        } else {
          append(
              b,
              displayedBatteryWatts >= 0.0f
                  ? String.format(Locale.US, "%.1fW", displayedBatteryWatts)
                  : "N/A",
              this.C_VALUE);
        }
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
      float w = getWidth();
      float h = getHeight();
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
