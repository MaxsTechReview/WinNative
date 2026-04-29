package com.winlator.cmod.runtime.display;

import static com.winlator.cmod.shared.android.AppUtils.showToast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import com.winlator.cmod.BuildConfig;
import com.winlator.cmod.feature.stores.steam.enums.Marker;
import com.winlator.cmod.feature.stores.steam.utils.MarkerUtils;
import com.winlator.cmod.feature.stores.steam.utils.PrefManager;
import com.winlator.cmod.feature.stores.steam.utils.SteamUtils;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.R;
import com.winlator.cmod.app.config.SettingsConfig;
import com.winlator.cmod.app.shell.UnifiedActivity;
import com.winlator.cmod.app.update.UpdateChecker;
import com.winlator.cmod.feature.settings.DebugFragment;
import com.winlator.cmod.feature.setup.SetupWizardActivity;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.container.ContainerManager;
import com.winlator.cmod.runtime.container.Shortcut;
import com.winlator.cmod.shared.ui.dialog.ContentDialog;
import com.winlator.cmod.feature.settings.DebugDialog;
import com.winlator.cmod.feature.settings.DXVKConfigUtils;
import com.winlator.cmod.feature.settings.GraphicsDriverConfigUtils;
import com.winlator.cmod.feature.shortcuts.ShortcutsFragment;
import com.winlator.cmod.feature.sync.CloudSyncConflictDialog;
import com.winlator.cmod.feature.sync.CloudSyncConflictTimestamps;
import com.winlator.cmod.feature.sync.CloudSyncHelper;
import com.winlator.cmod.feature.stores.steam.ui.SteamClientDownloadFailureDialog;
import com.winlator.cmod.feature.settings.WineD3DConfigUtils;
import com.winlator.cmod.runtime.compat.SteamBridge;
import com.winlator.cmod.runtime.content.ContentProfile;
import com.winlator.cmod.runtime.content.ContentsManager;
import com.winlator.cmod.runtime.content.AdrenotoolsManager;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.android.AppTerminationHelper;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.runtime.system.GPUInformation;
import com.winlator.cmod.shared.util.KeyValueSet;
import com.winlator.cmod.shared.util.Callback;
import com.winlator.cmod.shared.util.OnExtractFileListener;
import com.winlator.cmod.shared.ui.dialog.PreloaderDialog;
import com.winlator.cmod.runtime.system.ProcessHelper;
import com.winlator.cmod.shared.android.RefreshRateUtils;
import com.winlator.cmod.shared.util.StringUtils;
import com.winlator.cmod.shared.io.TarCompressorUtils;
import com.winlator.cmod.runtime.display.renderer.EffectComposer;
import com.winlator.cmod.runtime.display.renderer.effects.CRTEffect;
import com.winlator.cmod.runtime.display.renderer.effects.FSREffect;
import com.winlator.cmod.runtime.display.renderer.effects.HDREffect;
import com.winlator.cmod.runtime.display.renderer.effects.NaturalEffect;
import com.winlator.cmod.runtime.wine.WineInfo;
import com.winlator.cmod.runtime.wine.WineRegistryEditor;
import com.winlator.cmod.runtime.wine.WineRequestHandler;
import com.winlator.cmod.runtime.wine.WineStartMenuCreator;
import com.winlator.cmod.runtime.wine.WineThemeManager;
import com.winlator.cmod.runtime.wine.WineUtils;
import com.winlator.cmod.runtime.compat.fexcore.FEXCoreManager;
import com.winlator.cmod.runtime.compat.gamefixes.GameFixes;
import com.winlator.cmod.runtime.audio.alsaserver.ALSAClient;
import com.winlator.cmod.runtime.input.ControllerAssignmentDialog;
import com.winlator.cmod.runtime.input.InputControlsDialog;
import com.winlator.cmod.runtime.input.controls.ControlsProfile;
import com.winlator.cmod.runtime.input.controls.ControllerManager;
import com.winlator.cmod.runtime.input.controls.ExternalController;
import com.winlator.cmod.runtime.input.controls.InputControlsManager;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.shared.math.XForm;
import com.winlator.cmod.runtime.audio.midi.MidiHandler;
import com.winlator.cmod.runtime.audio.midi.MidiManager;
import com.winlator.cmod.runtime.display.renderer.GLRenderer;
import com.winlator.cmod.runtime.display.ui.FrameRating;
import com.winlator.cmod.runtime.display.ui.MagnifierView;
import com.winlator.cmod.runtime.display.ui.XServerView;
import com.winlator.cmod.shared.android.FixedFontScaleAppCompatActivity;
import com.winlator.cmod.runtime.input.ui.InputControlsView;
import com.winlator.cmod.runtime.input.ui.TouchpadView;
import com.winlator.cmod.runtime.system.ui.LogView;
import com.winlator.cmod.runtime.display.winhandler.MouseEventFlags;
import com.winlator.cmod.runtime.display.winhandler.OnGetProcessInfoListener;
import com.winlator.cmod.runtime.display.winhandler.ProcessInfo;
import com.winlator.cmod.runtime.display.winhandler.TaskManagerDialog;
import com.winlator.cmod.runtime.display.winhandler.WinHandler;
import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.runtime.display.environment.XEnvironment;
import com.winlator.cmod.feature.stores.steam.SteamClientManager;
import com.winlator.cmod.runtime.display.environment.components.ALSAServerComponent;
import com.winlator.cmod.runtime.display.environment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.runtime.display.environment.components.PulseAudioComponent;
import com.winlator.cmod.runtime.display.environment.components.SteamClientComponent;
import com.winlator.cmod.runtime.display.environment.components.SysVSharedMemoryComponent;
import com.winlator.cmod.runtime.display.environment.components.XServerComponent;
import com.winlator.cmod.runtime.display.xserver.Atom;
import com.winlator.cmod.runtime.display.xserver.Pointer;
import com.winlator.cmod.runtime.display.xserver.Property;
import com.winlator.cmod.runtime.display.xserver.ScreenInfo;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.WindowManager;
import com.winlator.cmod.runtime.display.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends FixedFontScaleAppCompatActivity {
    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    public static int NOTIFICATION_ID = -1;
    private static final long STEAM_TERMINATION_GRACE_MS = 10000L;
    private static final long STEAM_TERMINATION_POLL_MS = 1000L;
    private static final long STEAM_PROCESS_RESPONSE_TIMEOUT_MS = 2000L;
    private static final long STEAM_TERMINATION_TIMEOUT_MS = 30000L;
    private static final String STEAM_REGISTRY_KEY = "Software\\Valve\\Steam";
    private static final String STEAM_ROOT_PATH = "C:\\Program Files (x86)\\Steam";
    private static final String STEAM_EXE_PATH = STEAM_ROOT_PATH + "\\steam.exe";
    private static final String D8VK_ASSET_PATH = "dxwrapper/d8vk-1.0.tzst";
    private static final String STEAM_USER_REGISTRY_BACKUP_FILE = "steam_registry_backup.reg";
    private static final String STEAM_SYSTEM_REGISTRY_BACKUP_FILE = "steam_system_registry_backup.reg";
    private static final String STEAM_CLIENT_STORE_RELATIVE_PATH = ".shared/steam-client-store";
    private static final String COLDCLIENT_STORE_RELATIVE_PATH = ".shared/coldclient-store";
    private static final String PREVIOUS_STEAM_CLIENT_STORE_RELATIVE_PATH = ".steam-client-store";
    private static final String PREVIOUS_CONTAINER_STEAM_CLIENT_STORE_RELATIVE_PATH = ".wine/.steam-client-store";
    private static final String LEGACY_STEAM_CLIENT_STORE_RELATIVE_PATH = ".wine/drive_c/WinNative/SteamClient";
    public static final String EXTRA_LAUNCHED_FROM_PINNED_SHORTCUT = "launched_from_pinned_shortcut";

    // Real Steam launch flags. Keep this minimal set; CEF-workaround flags (-no-cef-sandbox,
    // -cef-single-process, -no-browser) all made things worse in testing (V8 proxy errors
    // under single-process, dead flag on modern Steam, etc.).
    //   -silent -vgui -tcp -nobigpicture -nofriendsui -nochatui -nointro -applaunch <id>
    private static final String[] STEAM_SYSTEM_REGISTRY_KEYS = new String[] {
            "Software\\Classes\\steam",
            "Software\\Wow6432Node\\Valve\\Steam"
    };
    private static final String[] STEAM_REGISTRY_LINE_PATTERNS = new String[] {
            "\"sourcemodinstallpath\"",
            "\"steamexe\"",
            "\"steampath\"",
            "\"steamclientdll\"",
            "\"steamclientdll64\"",
            "winnative\\\\steamclient",
            "winnative/steamclient",
            ".shared\\\\steam-client-store",
            ".shared/steam-client-store",
            "steamclient_loader_x64.exe",
            "steamclient_loader_x32.exe"
    };

    private static final HashSet<String> STEAM_EXIT_ALLOWLIST = new HashSet<>(Arrays.asList(
            "wineserver",
            "services",
            "start",
            "winhandler",
            "tabtip",
            "explorer",
            "winedevice",
            "svchost",
            "rpcss",
            "plugplay",
            "wineboot",
            "winemenubuilder",
            "conhost",
            "rundll32",
            "cmd"
    ));
    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private ContainerManager containerManager;
    protected Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private ImageFs imageFs;
    private FrameRating frameRating = null;
    private boolean effectiveShowFPS = false;
    private int runtimeFpsLimit = 0;
    private String lastRendererName = "OpenGL";
    private String lastGpuName = null;
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    private boolean launchedFromPinnedShortcut = false;
    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;
    private HashMap<String, String> graphicsDriverConfig;
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String emulator = Container.DEFAULT_EMULATOR;
    private String wineVersion = WineInfo.MAIN_WINE_VERSION.identifier();
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private KeyValueSet dxwrapperConfig;
    private String startupSelection;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private boolean firstTimeBoot = false;
    private SharedPreferences preferences;
    private boolean isMouseDisabled = false;
    private OnExtractFileListener onExtractFileListener;
    private WinHandler winHandler;
    private WineRequestHandler wineRequestHandler;
    private float globalCursorSpeed = 1.0f;
    private MagnifierView magnifierView;
    private DebugDialog debugDialog;
    private int taskAffinityMask = 0;
    private int taskAffinityMaskWoW64 = 0;
    private int frameRatingWindowId = -1;
    private boolean cursorLock; // Flag to track if pointer capture was requested
    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private boolean navigationFocused = false;
    private MidiHandler midiHandler;
    private String midiSoundFont = "";
    private String lc_all = "";
    PreloaderDialog preloaderDialog = null;
    private Runnable configChangedCallback = null;
    private boolean isPaused = false;
    private boolean isRelativeMouseMovement = false;
    private boolean isNativeRenderingEnabled = true;

    private float hudTransparency = 1.0f;
    private float hudScale = 1.0f;
    private boolean[] hudElements = new boolean[]{true, true, true, true, true, true};
    private boolean dualSeriesBattery = false;
    private boolean hudCardExpanded = false;
    private boolean screenEffectsCardExpanded = false;
    private boolean fsrEnabled = false;
    private int fsrMode = 0;
    private int fsrSharpness = 100;
    private int colorProfile = 0;
    private boolean gyroscopeCardExpanded = false;
    private XServerDrawerStateHolder drawerStateHolder;
    private XServerDrawerActionListener drawerActionListener;

    // Inside the XServerDisplayActivity class
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private ExternalController controller;

    // Playtime stats tracking
    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private String cachedDisplayName = "";
    private String cachedPlatform = "";
    private String cachedContainerLabel = "";
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    private static final long SAVE_INTERVAL_MS = 1000;
    private static final int EXIT_CLOUD_UPLOAD_MAX_ATTEMPTS = 3;
    private static final long EXIT_CLOUD_UPLOAD_RETRY_DELAY_MS = 1000L;

    private Handler  timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    // Real Steam watchdog: flipped true once an application window registers with the X server.
    // The 75s watchdog timer reads this to decide whether steam.exe hung without producing a game window.
    private final java.util.concurrent.atomic.AtomicBoolean firstAppWindowAppeared =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private Runnable realSteamWatchdogRunnable;
    private final AtomicBoolean exitRequested = new AtomicBoolean(false);
    private final AtomicBoolean steamExitWatchRunning = new AtomicBoolean(false);
    private final AtomicBoolean activityDestroyed = new AtomicBoolean(false);
    private final AtomicBoolean sessionCleanupStarted = new AtomicBoolean(false);
    private final AtomicBoolean switchLaunchInProgress = new AtomicBoolean(false);

    private boolean isDarkMode;
    private boolean enableLogsMenu;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    private EnvVars overrideEnvVars;

    private Runnable controllerAutoSwitchRunnable;

    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                float gyroX = event.values[0]; // Rotation around the X-axis
                float gyroY = event.values[1]; // Rotation around the Y-axis

                winHandler.updateGyroData(gyroX, gyroY); // Send gyro data to WinHandler
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // No action needed
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if ("gyro_enabled".equals(key) || "mouse_gyro_enabled".equals(key)) {
            boolean gyroEnabled = sharedPreferences.getBoolean("gyro_enabled", false);
            if (gyroEnabled) {
                sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
            } else {
                sensorManager.unregisterListener(gyroListener);
            }
        }
    };

    private void createNotifcationChannel() {
        String name = "WinNative";
        String description = getString(R.string.session_xserver_notification_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (configChangedCallback != null) {
            configChangedCallback.run();
            configChangedCallback = null;
        }
    }

    /**
     * Returns the effective display refresh rate override.
     * Priority: per-game shortcut > global setting > 0 (meaning use device max).
     */
    private int getRefreshRateOverride() {
        int perGameRate = getPerGameRefreshRateOverride();
        return perGameRate > 0 ? perGameRate : getGlobalRefreshRateOverride();
    }

    private int getPerGameRefreshRateOverride() {
        if (shortcut == null) return 0;
        return parsePositiveInt(shortcut.getExtra("refreshRate", ""));
    }

    private int getGlobalRefreshRateOverride() {
        if (preferences == null) return 0;
        return Math.max(0, preferences.getInt("refresh_rate_override", 0));
    }

    /**
     * Per-game settings always win over the global refresh rate when determining DXVK frame limit.
     * Returns 0 (no override) when no explicit user preference is set, matching Ludashi behavior.
     * The old code fell back to the device's max refresh rate which always injected
     * dxgi.syncInterval=0 and DXVK_FRAME_RATE, interfering with VKD3D frame pacing
     * and causing significant FPS drops in DX12 games.
     */
    private int getDxvkFrameRateOverride() {
        int perGameRate = getPerGameRefreshRateOverride();
        if (perGameRate > 0) {
            return perGameRate;
        }

        int globalRate = getGlobalRefreshRateOverride();
        if (globalRate > 0) {
            return globalRate;
        }
        return 0;
    }

    private int parsePositiveInt(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(parsed, 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean shortcutUsesContainerDefaults() {
        return shortcut != null && shortcut.usesContainerDefaults();
    }

    private String getShortcutSetting(String key, String containerValue) {
        return shortcut != null ? shortcut.getSettingExtra(key, containerValue) : containerValue;
    }

    private String getShortcutWineVersionOverride() {
        if (shortcut == null || shortcutUsesContainerDefaults()) return "";
        return shortcut.getExtra("wineVersion");
    }

    private void applyPreferredRefreshRate() {
        Runnable applyRefresh = () -> {
            if (isFinishing() || isDestroyed()) return;

            RefreshRateUtils.applyPreferredRefreshRate(this, getRefreshRateOverride(), runtimeFpsLimit);
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyRefresh.run();
        } else {
            runOnUiThread(applyRefresh);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;

        String incomingShortcutPath = intent.getStringExtra("shortcut_path");
        String incomingShortcutUuid = intent.getStringExtra("shortcut_uuid");
        int incomingContainerId = intent.getIntExtra("container_id", 0);
        String currentShortcutPath = shortcut != null ? shortcut.file.getAbsolutePath() : "";
        String currentShortcutUuid = shortcut != null ? shortcut.getExtra("uuid") : "";
        int currentContainerId = container != null ? container.id : 0;

        // Ensure all later getIntent() reads use the latest launch intent.
        setIntent(intent);
        launchedFromPinnedShortcut = isPinnedShortcutLaunchIntent(intent);

        boolean shortcutChanged = incomingShortcutPath != null
                && !incomingShortcutPath.isEmpty()
                && !incomingShortcutPath.equals(currentShortcutPath);
        boolean shortcutUuidChanged = incomingShortcutUuid != null
                && !incomingShortcutUuid.isEmpty()
                && !incomingShortcutUuid.equals(currentShortcutUuid);
        boolean containerChanged = incomingContainerId != 0 && incomingContainerId != currentContainerId;

        if (shortcutChanged || shortcutUuidChanged || containerChanged) {
            Log.d("XServerDisplayActivity", "onNewIntent: launch target changed, cleaning up before recreation");
            switchLaunchTargetAfterCleanup(intent);
        }
    }

    private void switchLaunchTargetAfterCleanup(Intent intent) {
        if (!switchLaunchInProgress.compareAndSet(false, true)) {
            Log.d("XServerDisplayActivity", "Switch launch already in progress; ignoring duplicate target intent");
            return;
        }

        Intent relaunchIntent = new Intent(intent);
        relaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        setIntent(relaunchIntent);
        exitRequested.set(true);

        if (preloaderDialog != null) {
            preloaderDialog.showOnUiThread(getString(R.string.preloader_initializing));
        }

        new Thread(() -> {
            performForcedSessionCleanup("switch launch target");
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    Log.w("XServerDisplayActivity", "Switch cleanup finished after activity was destroyed");
                    return;
                }
                setIntent(relaunchIntent);
                recreate();
            });
        }, "XServerSwitchCleanup").start();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
        // Clean up any shared debug logs and prepare for fresh session logging
        DebugFragment.Companion.cleanupSharedLogs();
        com.winlator.cmod.runtime.system.LogManager.prepareForNewSession(this);

        // Initialize preferences early so pickHighestRefreshRate can read global override
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        applyPreferredRefreshRate();
        launchedFromPinnedShortcut = isPinnedShortcutLaunchIntent(getIntent());
        
        setContentView(R.layout.xserver_display_activity);

        // Initialize ControllerManager for multi-controller support
        ControllerManager.getInstance().init(this);

        preloaderDialog = new PreloaderDialog(this);

        cursorLock = preferences.getBoolean("cursor_lock", false);
        dualSeriesBattery = preferences.getBoolean(FrameRating.PREF_HUD_DUAL_SERIES_BATTERY, false);

        // Check for Dark Mode
        isDarkMode = preferences.getBoolean("dark_mode", false);

        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        // Initialize the WinHandler after context is set up
        winHandler = new WinHandler(this);
        winHandler.initializeController();
        controller = winHandler.getCurrentController();

        if (isOpenWithAndroidBrowser || isShareAndroidClipboard)
            wineRequestHandler = new WineRequestHandler(this);

        if (controller != null) {
            int triggerType = preferences.getInt("trigger_type", ExternalController.TRIGGER_IS_AXIS); // Default to TRIGGER_IS_AXIS
            controller.setTriggerType((byte) triggerType); // Cast to byte if needed
        }



        // Check if xinputDisabled extra is passed
        boolean xinputDisabledFromShortcut = false;




        // Initialize SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        preferences.registerOnSharedPreferenceChangeListener(prefListener);

        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", false);

        if (gyroEnabled) {
            // Register the sensor event listener
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }



        // Record the start time
        startTime = System.currentTimeMillis();

        // Initialize handler for periodic saving
        handler = new Handler(Looper.getMainLooper());

        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);


        // Handler and Runnable to manage mouse cursor auto-hide
        hideControlsRunnable = () -> {
            if (!isMouseDisabled && xServer != null && xServer.getRenderer() != null
                    && xServer.getRenderer().isCursorVisible()) {
                xServer.getRenderer().setCursorVisible(false);
                Log.d("XServerDisplayActivity", "Mouse cursor hidden after inactivity.");
            }
        };


        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            WindowInsetsCompat compatInsets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets, view);
            WindowInsetsCompat clearedInsets = new WindowInsetsCompat.Builder(compatInsets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                    .build();
            android.view.WindowInsets platformInsets = clearedInsets.toWindowInsets();
            return platformInsets != null ? platformInsets : windowInsets;
        });
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        ComposeView navigationComposeView = findViewById(R.id.NavigationComposeView);
        enableLogsMenu = preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box64_logs", false);
        isNativeRenderingEnabled = preferences.getBoolean("use_dri3", true);
        navigationComposeView.setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_ARROW));
        navigationComposeView.setOnFocusChangeListener((v, hasFocus) -> navigationFocused = hasFocus);
        renderDrawerMenu();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleNavigationBackPressed();
            }
        });
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                renderDrawerMenu();
                navigationComposeView.requestFocus();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (hudCardExpanded) {
                    hudCardExpanded = false;
                    renderDrawerMenu();
                }
            }
        });

        imageFs = ImageFs.find(this);
        GuestProgramLauncherComponent.ensureImageFsNativeLibrary(this, imageFs, "libfakeinput.so");
        GuestProgramLauncherComponent.ensureImageFsNativeLibrary(this, imageFs, "libandroid-sysvshm.so");
        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {
            for (int i = 0; i < 4; i++) {
                File eventFile = new File(devInputDir, "event" + i);
                if (eventFile.exists()) {
                    eventFile.delete();
                }
            }
        }
        winHandler.setFakeInputPath(devInputDir.getAbsolutePath());

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));
        loadHUDSettings();
        loadScreenEffectsSettings();

        // Determine launch target from intent extras and URI fallback.
        int containerId = getIntent().getIntExtra("container_id", 0);
        String shortcutPath = getIntent().getStringExtra("shortcut_path");
        String shortcutUuid = getIntent().getStringExtra("shortcut_uuid");
        int shortcutPathHash = getIntent().getIntExtra("shortcut_path_hash", 0);

        android.net.Uri launchData = getIntent().getData();
        if (launchData != null) {
            try {
                String uriUuid = launchData.getQueryParameter("uuid");
                String uriContainer = launchData.getQueryParameter("container");
                String uriHash = launchData.getQueryParameter("hash");

                if ((shortcutUuid == null || shortcutUuid.isEmpty()) && uriUuid != null && !uriUuid.isEmpty()) {
                    shortcutUuid = uriUuid;
                }
                if (containerId == 0 && uriContainer != null && !uriContainer.isEmpty()) {
                    try {
                        containerId = Integer.parseInt(uriContainer);
                    } catch (NumberFormatException ignored) {}
                }
                if (shortcutPathHash == 0 && uriHash != null && !uriHash.isEmpty()) {
                    try {
                        shortcutPathHash = Integer.parseInt(uriHash);
                    } catch (NumberFormatException ignored) {}
                }
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Failed to parse shortcut URI fallback", e);
            }
        }

        Shortcut resolvedShortcut = null;
        if (shortcutUuid != null && !shortcutUuid.isEmpty()) {
            resolvedShortcut = findShortcutByUuid(shortcutUuid, containerId);
        }
        if (resolvedShortcut == null && shortcutPathHash != 0) {
            resolvedShortcut = findShortcutByPathHash(shortcutPathHash, containerId);
        }
        if (resolvedShortcut == null && shortcutPath != null && !shortcutPath.isEmpty()) {
            resolvedShortcut = findShortcutByAbsolutePath(shortcutPath, containerId);
        }
        if (resolvedShortcut != null) {
            shortcutPath = resolvedShortcut.file.getAbsolutePath();
            containerId = resolvedShortcut.container.id;
            Log.d("XServerDisplayActivity", "Resolved launch target from shortcut identity: " + shortcutPath + " (container " + containerId + ")");
        } else {
            File shortcutPathFile = (shortcutPath != null && !shortcutPath.isEmpty()) ? new File(shortcutPath) : null;
            boolean hasUsablePath = shortcutPathFile != null && shortcutPathFile.isFile();
            if (!hasUsablePath) {
                Log.w("XServerDisplayActivity", "Shortcut path from intent is not usable and no shortcut identity match was found");
                boolean launchedFromShortcutIdentity = (shortcutUuid != null && !shortcutUuid.isEmpty())
                        || shortcutPathHash != 0
                        || (shortcutPath != null && !shortcutPath.isEmpty());
                if (launchedFromShortcutIdentity) {
                    disableUnavailablePinnedShortcut(containerId, shortcutUuid, shortcutPath, shortcutPathHash);
                    showToast(this, R.string.shortcuts_list_not_available);
                    finish();
                    return;
                }
            }
        }

        // Log shortcut identity data
        Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);
        Log.d("XServerDisplayActivity", "Shortcut UUID: " + shortcutUuid + ", pathHash=" + shortcutPathHash);
        Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);
        if (containerId == 0) {
            Log.d("XServerDisplayActivity", "Container ID is 0, attempting to parse from .desktop file");
            // Proceed with .desktop file parsing
        }


        // If container_id is 0, read from the .desktop file
        if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {
            File shortcutFile = new File(shortcutPath);
            containerId = parseContainerIdFromDesktopFile(shortcutFile);
            Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);
        }

        // Initialize playtime tracking
        playtimePrefs = getSharedPreferences("playtime_stats", MODE_PRIVATE);
        shortcutName = getIntent().getStringExtra("shortcut_name");

        // Ensure shortcutPath is not null before proceeding
        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            if (shortcutName == null || shortcutName.isEmpty()) {
                shortcutName = parseShortcutNameFromDesktopFile(new File(shortcutPath));
                Log.d("XServerDisplayActivity", "Parsed Shortcut Name from .desktop file: " + shortcutName);
            }
        } else {
            Log.d("XServerDisplayActivity", "No shortcut path provided, skipping shortcut parsing.");
        }

        // Sanitize shortcutName to match the library sorting key format.
        // The library uses LIBRARY_NAME_SANITIZE_REGEX = "[^A-Za-z0-9 _-]" to strip
        // special characters before looking up playtime stats. We must apply the
        // same sanitization here so the written keys match the read keys.
        if (shortcutName != null) {
            shortcutName = shortcutName.replaceAll("[^A-Za-z0-9 _-]", "");
        }

        // Increment play count at the start of a session
        incrementPlayCount();

        // Log the final container_id
        Log.d("XServerDisplayActivity", "Final Container ID: " + containerId);

        // Retrieve the container and check if it's null
        container = containerManager.getContainerById(containerId);

        if (container == null) {
            Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
            finish();  // Gracefully exit the activity to avoid crashing
            return;
        }

        containerManager.activateContainer(container);

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            shortcut = new Shortcut(container, new File(shortcutPath));
        }

        int numControllers = 1;
        if (shortcut != null) {
            try {
                numControllers = Integer.parseInt(shortcut.getExtra("numControllers", "1"));
            } catch (NumberFormatException e) {
                numControllers = 1;
            }
        }
        numControllers = Math.max(1, Math.min(numControllers, 4));
        for (int i = 0; i < numControllers; i++) {
            try {
                new File(devInputDir, "event" + i).createNewFile();
            } catch (Exception e) {
            }
        }

        String containerCpuList = container.getCPUList(true);
        String containerCpuListWoW64 = container.getCPUListWoW64(true);
        String effectiveCpuList = containerCpuList;
        String effectiveCpuListWoW64 = containerCpuListWoW64;
        taskAffinityMask = ProcessHelper.getAffinityMask(containerCpuList);
        taskAffinityMaskWoW64 = ProcessHelper.getAffinityMask(containerCpuListWoW64);

        String rawShortcutCpuList = "";
        String rawShortcutCpuListWoW64 = "";
        if (shortcut != null) {
            boolean cpuShortcutUsesDefaults = shortcutUsesContainerDefaults();
            rawShortcutCpuList = cpuShortcutUsesDefaults ? "" : shortcut.getExtra("cpuList");
            rawShortcutCpuListWoW64 = cpuShortcutUsesDefaults ? "" : shortcut.getExtra("cpuListWoW64");
            effectiveCpuList = getShortcutSetting("cpuList", containerCpuList);
            effectiveCpuListWoW64 = getShortcutSetting("cpuListWoW64", containerCpuListWoW64);
            taskAffinityMask = ProcessHelper.getAffinityMask(effectiveCpuList);
            taskAffinityMaskWoW64 = ProcessHelper.getAffinityMask(effectiveCpuListWoW64);
        }
        Log.d("XServerDisplayActivity", "CPUList source=shortcutOrContainer shortcutRaw='" +
                rawShortcutCpuList + "' container='" + containerCpuList +
                "' effective='" + effectiveCpuList + "' affinityMask=0x" +
                Integer.toHexString(taskAffinityMask & 0xFFFF));
        Log.d("XServerDisplayActivity", "CPUListWoW64 source=shortcutOrContainer shortcutRaw='" +
                rawShortcutCpuListWoW64 + "' container='" + containerCpuListWoW64 +
                "' effective='" + effectiveCpuListWoW64 + "' affinityMask=0x" +
                Integer.toHexString(taskAffinityMaskWoW64 & 0xFFFF));

        // Determine the class name for the startup workarounds
        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("appVersion").isEmpty();

        String containerWineVersion = container.getWineVersion();
        wineVersion = containerWineVersion;
        // Override wine version from per-game shortcut settings if available
        String rawShortcutWineVersion = "";
        if (shortcut != null) {
            String shortcutWineVersion = getShortcutWineVersionOverride();
            rawShortcutWineVersion = shortcutWineVersion != null ? shortcutWineVersion : "";
            if (shortcutWineVersion != null && !shortcutWineVersion.isEmpty()) {
                wineVersion = shortcutWineVersion;
            }
        }
        Log.d("XServerDisplayActivity", "WineVersion source=shortcutOrContainer shortcutRaw='" +
                rawShortcutWineVersion + "' container='" + containerWineVersion +
                "' effective='" + wineVersion + "'");
        if (!ensureRequestedWineVersionInstalled()) {
            return;
        }
        wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);

        imageFs.setWinePath(wineInfo.path);

        ProcessHelper.removeAllDebugCallbacks();
        if (enableLogsMenu) {
            LogView.setFilename(getExecutable());
            ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        }

        graphicsDriver = container.getGraphicsDriver();
        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        audioDriver = container.getAudioDriver();
        emulator = container.getEmulator();
        midiSoundFont = container.getMIDISoundFont();
        dxwrapper = container.getDXWrapper();
        String dxwrapperConfig = container.getDXWrapperConfig();
        screenSize = container.getScreenSize();
        winHandler.setInputType((byte) container.getInputType());
        lc_all = container.getLC_ALL();

        // Log the entire intent to verify the extras
        Intent intent = getIntent();
        Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());

        if (shortcut != null) {
            String containerIdOverride = shortcut.getExtra("container_id");
            if (!containerIdOverride.isEmpty()) {
                int newContainerId = Integer.parseInt(containerIdOverride);
                if (newContainerId != container.id) {
                    container = containerManager.getContainerById(newContainerId);
                    if (container == null) {
                        Log.e("XServerDisplayActivity", "Failed to retrieve overridden container with ID: " + newContainerId);
                        finish();
                        return;
                    }
                    containerManager.activateContainer(container);
                    Log.d("XServerDisplayActivity", "Container overridden to ID: " + newContainerId);

                    // RE-EVALUATE wineVersion and wineInfo after container override!
                    String reevalContainerWineVersion = container.getWineVersion();
                    wineVersion = reevalContainerWineVersion;
                    String shortcutWineVersion = getShortcutWineVersionOverride();
                    String reevalRawShortcutWineVersion = shortcutWineVersion != null ? shortcutWineVersion : "";
                    if (shortcutWineVersion != null && !shortcutWineVersion.isEmpty()) {
                        wineVersion = shortcutWineVersion;
                    }
                    Log.d("XServerDisplayActivity", "WineVersion (post container-override) source=shortcutOrContainer shortcutRaw='" +
                            reevalRawShortcutWineVersion + "' container='" + reevalContainerWineVersion +
                            "' effective='" + wineVersion + "'");
                    if (!ensureRequestedWineVersionInstalled()) {
                        return;
                    }
                    wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);
                    imageFs.setWinePath(wineInfo.path);
                }
            }

            // Refresh stored host install paths for shortcut-based launches.
            String gameSource = shortcut.getExtra("game_source");
            if ("STEAM".equals(gameSource)) {
                String appIdStr = shortcut.getExtra("app_id");
                if (!appIdStr.isEmpty()) {
                    String gameInstallPath = resolveSteamGameInstallPath(Integer.parseInt(appIdStr));
                    if (new File(gameInstallPath).exists()) {
                        shortcut.putExtra("game_install_path", gameInstallPath);
                        shortcut.saveData();
                    }
                }
            } else if ("EPIC".equals(gameSource)) {
                String gameInstallPath = shortcut.getExtra("game_install_path");
                // Fallback: resolve install path from Epic service if missing from shortcut
                if (gameInstallPath.isEmpty()) {
                    String appIdStr = shortcut.getExtra("app_id");
                    if (!appIdStr.isEmpty()) {
                        try {
                            com.winlator.cmod.feature.stores.epic.data.EpicGame epicGame = com.winlator.cmod.feature.stores.epic.service.EpicService.Companion.getEpicGameOf(Integer.parseInt(appIdStr));
                            if (epicGame != null) {
                                String resolved = epicGame.getInstallPath();
                                if (resolved == null || resolved.isEmpty()) {
                                    resolved = com.winlator.cmod.feature.stores.epic.service.EpicConstants.INSTANCE.getGameInstallPath(this, epicGame.getAppName());
                                }
                                if (resolved != null && !resolved.isEmpty()) {
                                    gameInstallPath = resolved;
                                    // Persist so future launches don't need this fallback
                                    shortcut.putExtra("game_install_path", gameInstallPath);
                                    shortcut.saveData();
                                    Log.d("XServerDisplayActivity", "Resolved missing Epic install path from service: " + gameInstallPath);
                                }
                            }
                        } catch (Exception e) {
                            Log.e("XServerDisplayActivity", "Failed to resolve Epic install path from app_id", e);
                        }
                    }
                }
                if (!gameInstallPath.isEmpty() && new File(gameInstallPath).exists()) {
                    shortcut.putExtra("game_install_path", gameInstallPath);
                    shortcut.saveData();
                } else {
                    Log.e("XServerDisplayActivity", "EPIC install path missing or invalid: '" + gameInstallPath + "'");
                }
            } else if ("GOG".equals(gameSource)) {
                String gameInstallPath = shortcut.getExtra("game_install_path");
                if (gameInstallPath.isEmpty()) {
                    String gogId = shortcut.getExtra("gog_id");
                    if (!gogId.isEmpty()) {
                        try {
                            com.winlator.cmod.feature.stores.gog.data.GOGGame gogGame = com.winlator.cmod.feature.stores.gog.service.GOGService.Companion.getGOGGameOf(gogId);
                            if (gogGame != null) {
                                String resolved = gogGame.getInstallPath();
                                if (resolved == null || resolved.isEmpty()) {
                                    resolved = com.winlator.cmod.feature.stores.gog.service.GOGConstants.INSTANCE.getGameInstallPath(gogGame.getTitle());
                                }
                                if (resolved != null && !resolved.isEmpty()) {
                                    gameInstallPath = resolved;
                                    shortcut.putExtra("game_install_path", gameInstallPath);
                                    shortcut.saveData();
                                }
                            }
                        } catch (Exception e) {
                            Log.e("XServerDisplayActivity", "Failed to resolve GOG install path", e);
                        }
                    }
                }
                if (!gameInstallPath.isEmpty() && new File(gameInstallPath).exists()) {
                    shortcut.putExtra("game_install_path", gameInstallPath);
                    shortcut.saveData();
                } else {
                    Log.e("XServerDisplayActivity", "GOG install path missing or invalid: '" + gameInstallPath + "'");
                }
            } else if ("CUSTOM".equals(gameSource)) {
                String customMountPath = resolveCustomMountPath(shortcut);
                if (!customMountPath.isEmpty() && new File(customMountPath).isDirectory()) {
                    if (shortcut.getExtra("custom_game_folder").isEmpty() || shortcut.getExtra("game_install_path").isEmpty()) {
                        if (shortcut.getExtra("custom_game_folder").isEmpty()) {
                            shortcut.putExtra("custom_game_folder", customMountPath);
                        }
                        if (shortcut.getExtra("game_install_path").isEmpty()) {
                            shortcut.putExtra("game_install_path", customMountPath);
                        }
                        shortcut.saveData();
                    }
                } else {
                    Log.w("XServerDisplayActivity", "CUSTOM mount path missing/invalid. custom_game_folder='"
                            + shortcut.getExtra("custom_game_folder") + "' launch_exe_path='"
                            + shortcut.getExtra("launch_exe_path") + "' custom_exe='"
                            + shortcut.getExtra("custom_exe") + "' shortcut.path='" + shortcut.path + "'");
                }
            }

            boolean shortcutUsesDefaults = shortcutUsesContainerDefaults();
            String rawShortcutGraphicsDriver = shortcutUsesDefaults ? "" : shortcut.getExtra("graphicsDriver");
            String rawShortcutGraphicsDriverConfig = shortcutUsesDefaults ? "" : shortcut.getExtra("graphicsDriverConfig");
            String rawShortcutAudioDriver = shortcutUsesDefaults ? "" : shortcut.getExtra("audioDriver");
            String rawShortcutEmulator = shortcutUsesDefaults ? "" : shortcut.getExtra("emulator");
            String rawShortcutDxwrapper = shortcutUsesDefaults ? "" : shortcut.getExtra("dxwrapper");

            graphicsDriver = getShortcutSetting("graphicsDriver", container.getGraphicsDriver());
            graphicsDriverConfig = getShortcutSetting("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = getShortcutSetting("audioDriver", container.getAudioDriver());
            emulator = getShortcutSetting("emulator", container.getEmulator());
            dxwrapper = getShortcutSetting("dxwrapper", container.getDXWrapper());
            String rawShortcutDxwrapperConfig = shortcutUsesDefaults ? "" : shortcut.getExtra("dxwrapperConfig");
            dxwrapperConfig = getShortcutSetting("dxwrapperConfig", container.getDXWrapperConfig());

            Log.d("XServerDisplayActivity", "GraphicsDriver source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutGraphicsDriver + "' container='" + container.getGraphicsDriver() +
                    "' effective='" + graphicsDriver + "'");
            Log.d("XServerDisplayActivity", "GraphicsDriverConfig source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutGraphicsDriverConfig + "' container='" + container.getGraphicsDriverConfig() +
                    "' effective='" + graphicsDriverConfig + "'");
            Log.d("XServerDisplayActivity", "AudioDriver source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutAudioDriver + "' container='" + container.getAudioDriver() +
                    "' effective='" + audioDriver + "'");
            Log.d("XServerDisplayActivity", "Emulator source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutEmulator + "' container='" + container.getEmulator() +
                    "' effective='" + emulator + "'");
            Log.d("XServerDisplayActivity", "DXWrapper (version) source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutDxwrapper + "' container='" + container.getDXWrapper() +
                    "' effective='" + dxwrapper + "'");
            Log.d("XServerDisplayActivity", "DXVK launch config source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutDxwrapperConfig + "' container='" + container.getDXWrapperConfig() +
                    "' effective='" + dxwrapperConfig + "'");
            String rawShortcutScreenSize = shortcutUsesDefaults ? "" : shortcut.getExtra("screenSize");
            String rawShortcutLcAll = shortcutUsesDefaults ? "" : shortcut.getExtra("lc_all");
            String rawShortcutMidiSoundFont = shortcutUsesDefaults ? "" : shortcut.getExtra("midiSoundFont");
            String rawShortcutStartupSelection = shortcutUsesDefaults ? "" : shortcut.getExtra("startupSelection");

            screenSize = getShortcutSetting("screenSize", container.getScreenSize());
            lc_all = getShortcutSetting("lc_all", container.getLC_ALL());
            midiSoundFont = getShortcutSetting("midiSoundFont", container.getMIDISoundFont());

            Log.d("XServerDisplayActivity", "ScreenSize source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutScreenSize + "' container='" + container.getScreenSize() +
                    "' effective='" + screenSize + "'");
            Log.d("XServerDisplayActivity", "LC_ALL source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutLcAll + "' container='" + container.getLC_ALL() +
                    "' effective='" + lc_all + "'");
            Log.d("XServerDisplayActivity", "MIDISoundFont source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutMidiSoundFont + "' container='" + container.getMIDISoundFont() +
                    "' effective='" + midiSoundFont + "'");

            String inputType = shortcutUsesDefaults ? "" : shortcut.getExtra("inputType");
            if (!inputType.isEmpty()) winHandler.setInputType((byte)Integer.parseInt(inputType));
            String xinputDisabledString = getShortcutSetting("disableXinput", "false");
            xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);
            // Pass the value to WinHandler
            winHandler.setXInputDisabled(xinputDisabledFromShortcut);
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);

            startupSelection = getShortcutSetting("startupSelection", String.valueOf(container.getStartupSelection()));
            Log.d("XServerDisplayActivity", "StartupSelection source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutStartupSelection + "' container='" + container.getStartupSelection() +
                    "' effective='" + startupSelection + "'");
            // Per-game refresh rate override is read in getRefreshRateOverride()
        } else {
            startupSelection = String.valueOf(container.getStartupSelection());
            Log.d("XServerDisplayActivity", "StartupSelection source=container (no shortcut) effective='" +
                    startupSelection + "'");
        }

        this.graphicsDriverConfig = GraphicsDriverConfigUtils.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigUtils.parseConfig(dxwrapperConfig);
        Log.i("XServerDisplayActivity", "Launch DX wrapper selected: dxwrapper='" +
                dxwrapper + "' dxvkVersion='" + this.dxwrapperConfig.get("version") +
                "' vkd3dVersion='" + this.dxwrapperConfig.get("vkd3dVersion") +
                "' ddrawrapper='" + this.dxwrapperConfig.get("ddrawrapper") + "'");
        applyPreferredRefreshRate();

        if (!wineInfo.isWin64()) {
            onExtractFileListener = (file, size) -> {
                String path = file.getPath();
                if (path.contains("system32/")) return null;
                return new File(path.replace("syswow64/", "system32/"));
            };
        }

        cachedPlatform = shortcut != null ? shortcut.getExtra("game_source") : "";
        if (cachedPlatform == null) cachedPlatform = "";
        cachedDisplayName = (shortcutName != null && !shortcutName.isEmpty())
                ? shortcutName
                : getString(R.string.preloader_default_name);
        cachedContainerLabel = container != null ? container.getName() : "";
        showLaunchPreloader(getString(R.string.preloader_initializing));

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize), isNativeRenderingEnabled);
        xServer.setWinHandler(winHandler);

        boolean[] winStarted = {false};

        // Add the OnWindowModificationListener for dynamic workarounds
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    if (!isMouseDisabled) {
                        touchpadView.setMouseEnabled(true);
                    } else {
                        xServerView.getRenderer().setCursorVisible(false);
                    }
                    preloaderDialog.closeOnUiThread();
                    winStarted[0] = true;
                    firstAppWindowAppeared.set(true);
                    cancelRealSteamWatchdog();
                }
                if (frameRating != null && frameRatingWindowId == window.id) {
                    frameRating.update();
                }
            }
           
            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                changeFrameRatingVisibility(window, property);
            }    

            @Override
            public void onDestroyWindow(Window window) {
                changeFrameRatingVisibility(window, null);
            }
        });

        if (!midiSoundFont.equals("")) {
            InputStream in = null;
            InputStream finalIn = in;
            MidiManager.OnMidiLoadedCallback callback = new MidiManager.OnMidiLoadedCallback() {
                @Override
                public void onSuccess(SF2Soundbank soundbank) {
                    midiHandler = new MidiHandler();
                    midiHandler.setSoundBank(soundbank);
                    midiHandler.start();
                }

                @Override
                public void onFailed(Exception e) {
                    try {
                        finalIn.close();
                    } catch (Exception e2) {}
                }
            };
            try {
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    in = getAssets().open(MidiManager.SF2_ASSETS_DIR + "/" + midiSoundFont);
                    MidiManager.load(in, callback);
                } else
                    MidiManager.load(new File(MidiManager.getSoundFontDir(this), midiSoundFont), callback);
            } catch (Exception e) {}
        }

        // Check if a profile is defined by the shortcut
        String controlsProfile = shortcut != null ? shortcut.getExtra("controlsProfile", "") : "";

        createNotifcationChannel();

        Intent notificationIntent = new Intent(this, XServerDisplayActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ab_gear_0011)
                .setContentTitle("WinNative")
                .setContentText(getString(R.string.session_xserver_notification_content))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());

        Runnable runnable = () -> {
            setupUI();
            if (controlsProfile.isEmpty()) {
                // No profile defined, run the simulated dialog confirmation for input controls
                simulateConfirmInputControlsDialog();
            }
            Executors.newSingleThreadExecutor().execute(() -> {
                // Cancel any pending post-game update check since we're launching a new game
                UpdateChecker.INSTANCE.cancelPostGameCheck();

                if (shortcut != null) {
                    if (isCloudSyncEnabledForShortcut() && !CloudSyncHelper.isOfflineMode(shortcut)) {
                        CloudSyncHelper.forceDownloadOnContainerSwap(this, shortcut);

                        // Cloud save sync on every store-game launch
                        if (CloudSyncHelper.isStoreGame(shortcut)) {
                            if (!CloudSyncHelper.hasLocalCloudSaves(this, shortcut)) {
                                // First launch — download silently
                                showLaunchPreloader(getString(R.string.preloader_downloading_cloud));
                                CloudSyncHelper.downloadCloudSaves(this, shortcut);
                                showLaunchPreloader(getString(R.string.preloader_initializing));
                            } else if (CloudSyncHelper.cloudSavesDiffer(this, shortcut)) {
                                // Cloud differs from local — ask the user what to do
                                final CountDownLatch dialogLatch = new CountDownLatch(1);
                                final boolean[] useCloud = {false};
                                final boolean[] keepBackup = {false};
                                final CloudSyncConflictTimestamps timestamps = CloudSyncHelper.getConflictTimestamps(this, shortcut);
                                runOnUiThread(() -> {
                                    CloudSyncConflictDialog.show(
                                        XServerDisplayActivity.this,
                                        timestamps,
                                        (boolean keep) -> {
                                            useCloud[0] = true;
                                            keepBackup[0] = keep;
                                            dialogLatch.countDown();
                                        },
                                        (boolean keep) -> {
                                            useCloud[0] = false;
                                            keepBackup[0] = keep;
                                            dialogLatch.countDown();
                                        }
                                    );
                                });
                                try {
                                    dialogLatch.await();
                                } catch (InterruptedException ignored) {}

                                if (useCloud[0]) {
                                    // Archive the local save that's about to be overwritten
                                    if (keepBackup[0]) {
                                        try {
                                            backupDiscardedSaveForShortcut(shortcut,
                                                com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin.LOCAL);
                                        } catch (Throwable t) {
                                            android.util.Log.w("CloudSync", "Pre-overwrite local backup failed", t);
                                        }
                                    }
                                    showLaunchPreloader(getString(R.string.preloader_syncing_cloud));
                                    CloudSyncHelper.downloadCloudSaves(this, shortcut);
                                    showLaunchPreloader(getString(R.string.preloader_initializing));
                                }
                                // Keep Local: cloud version is about to be overwritten on next exit.
                                // Capturing it would require a separate provider download — deferred for now.
                            }
                        }
                    }
                }
                setupWineSystemFiles();
                extractGraphicsDriverFiles();
                changeWineAudioDriver();
                try {
                    setupXEnvironment();
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        };

        if (xServer.screenInfo.height > xServer.screenInfo.width) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            configChangedCallback = runnable;
        } else
              runnable.run();
    }

    // Method to parse container_id from .desktop file
    private int parseContainerIdFromDesktopFile(File desktopFile) {
        int containerId = 0;
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("container_id:") || trimmed.startsWith("container_id=")) {
                        int sep = trimmed.indexOf(':');
                        if (sep == -1) sep = trimmed.indexOf('=');
                        if (sep != -1 && sep + 1 < trimmed.length()) {
                            containerId = Integer.parseInt(trimmed.substring(sep + 1).trim());
                            break;
                        }
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);
            }
        }
        return containerId;
    }

    @Nullable
    private Shortcut findShortcutByUuid(String uuid, int preferredContainerId) {
        if (uuid == null || uuid.isEmpty() || containerManager == null) return null;
        try {
            Shortcut fallback = null;
            for (Shortcut sc : containerManager.loadShortcuts()) {
                if (!uuid.equals(sc.getExtra("uuid"))) continue;
                if (preferredContainerId > 0 && sc.container != null && sc.container.id == preferredContainerId) {
                    return sc;
                }
                if (fallback == null) fallback = sc;
            }
            return fallback;
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to resolve shortcut by uuid: " + uuid, e);
        }
        return null;
    }

    @Nullable
    private Shortcut findShortcutByPathHash(int pathHash, int preferredContainerId) {
        if (pathHash == 0 || containerManager == null) return null;
        try {
            Shortcut fallback = null;
            for (Shortcut sc : containerManager.loadShortcuts()) {
                if (sc.file == null || sc.file.getAbsolutePath().hashCode() != pathHash) continue;
                if (preferredContainerId > 0 && sc.container != null && sc.container.id == preferredContainerId) {
                    return sc;
                }
                if (fallback == null) fallback = sc;
            }
            return fallback;
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to resolve shortcut by path hash: " + pathHash, e);
        }
        return null;
    }

    @Nullable
    private Shortcut findShortcutByAbsolutePath(String absolutePath, int preferredContainerId) {
        if (absolutePath == null || absolutePath.isEmpty() || containerManager == null) return null;
        try {
            Shortcut fallback = null;
            for (Shortcut sc : containerManager.loadShortcuts()) {
                if (sc.file == null) continue;
                if (!absolutePath.equals(sc.file.getAbsolutePath())) continue;
                if (preferredContainerId > 0 && sc.container != null && sc.container.id == preferredContainerId) {
                    return sc;
                }
                if (fallback == null) fallback = sc;
            }
            return fallback;
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to resolve shortcut by absolute path", e);
        }
        return null;
    }

    private void disableUnavailablePinnedShortcut(int containerId, @Nullable String shortcutUuid, @Nullable String shortcutPath, int shortcutPathHash) {
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (shortcutManager == null) return;

        ArrayList<String> shortcutIds = ShortcutsFragment.buildPinnedShortcutIds(containerId, shortcutUuid, shortcutPath);
        if ((shortcutPath == null || shortcutPath.isEmpty()) && shortcutUuid != null && !shortcutUuid.isEmpty()
                && containerId > 0 && shortcutPathHash != 0) {
            shortcutIds.add(
                    "shortcut_" + containerId + "_" + shortcutUuid + "_" + Integer.toUnsignedString(shortcutPathHash, 16)
            );
        }
        if (shortcutIds.isEmpty()) return;

        try {
            shortcutManager.disableShortcuts(shortcutIds, getString(R.string.shortcuts_list_not_available));
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to disable unavailable pinned shortcut", e);
        }

        try {
            shortcutManager.removeDynamicShortcuts(shortcutIds);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to remove dynamic shortcut metadata", e);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                shortcutManager.removeLongLivedShortcuts(shortcutIds);
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to remove long-lived shortcut metadata", e);
            }
        }
    }

    private String resolveCustomMountPath(@NonNull Shortcut shortcut) {
        String customGameFolder = shortcut.getExtra("custom_game_folder");
        if (!customGameFolder.isEmpty() && new File(customGameFolder).isDirectory()) {
            return customGameFolder;
        }

        String gameInstallPath = shortcut.getExtra("game_install_path");
        if (!gameInstallPath.isEmpty() && new File(gameInstallPath).isDirectory()) {
            return gameInstallPath;
        }

        String launchExePath = shortcut.getExtra("launch_exe_path");
        String inferredFromLaunchExe = inferCustomMountPathFromExe(shortcut.path, launchExePath);
        if (!inferredFromLaunchExe.isEmpty()) return inferredFromLaunchExe;

        String customExePath = shortcut.getExtra("custom_exe");
        String inferredFromCustomExe = inferCustomMountPathFromExe(shortcut.path, customExePath);
        if (!inferredFromCustomExe.isEmpty()) return inferredFromCustomExe;

        return "";
    }

    private String inferCustomMountPathFromExe(String shortcutWinPath, String hostExePath) {
        if (hostExePath == null || hostExePath.isEmpty()) return "";
        File hostExeFile = new File(hostExePath);

        if (hostExeFile.isDirectory()) return hostExeFile.getAbsolutePath();
        if (!hostExeFile.isFile()) return "";

        if (shortcutWinPath != null && !shortcutWinPath.isEmpty()) {
            String normalizedWinPath = shortcutWinPath.replace("/", "\\");
            if (normalizedWinPath.matches("^[A-Za-z]:\\\\.*")) {
                String relativeWinPath = normalizedWinPath.substring(3);
                while (relativeWinPath.startsWith("\\")) relativeWinPath = relativeWinPath.substring(1);
                if (!relativeWinPath.isEmpty()) {
                    String relativeFsPath = relativeWinPath.replace("\\", File.separator);
                    String normalizedHostExe = hostExeFile.getAbsolutePath().replace("\\", File.separator);
                    if (normalizedHostExe.endsWith(relativeFsPath)) {
                        String root = normalizedHostExe.substring(0, normalizedHostExe.length() - relativeFsPath.length());
                        while (root.endsWith(File.separator)) {
                            root = root.substring(0, root.length() - 1);
                        }
                        if (!root.isEmpty() && new File(root).isDirectory()) {
                            return root;
                        }
                    }
                }
            }
        }

        File parent = hostExeFile.getParentFile();
        return (parent != null && parent.isDirectory()) ? parent.getAbsolutePath() : "";
    }

    private String resolveCustomExecutableWinPath(@NonNull Shortcut shortcut) {
        String customGameFolder = resolveCustomMountPath(shortcut);
        String customExe = shortcut.getExtra("custom_exe");
        if (customExe != null && !customExe.isEmpty()) {
            File customExeFile = new File(customExe);
            if (customExeFile.isFile()) {
                return mapCustomExecutableWinPath(customGameFolder, customExeFile);
            }
            return WineUtils.hostPathToRootWinePath(container, customExeFile.getAbsolutePath());
        }

        String launchExePath = shortcut.getExtra("launch_exe_path");
        if (launchExePath != null && !launchExePath.isEmpty()) {
            File launchExeFile = new File(launchExePath);
            if (launchExeFile.isFile()) {
                return mapCustomExecutableWinPath(customGameFolder, launchExeFile);
            }
            return WineUtils.hostPathToRootWinePath(container, launchExeFile.getAbsolutePath());
        }

        if (!customGameFolder.isEmpty()) {
            File exeFile = findGameExe(new File(customGameFolder));
            if (exeFile != null) {
                if ((shortcut.getExtra("launch_exe_path") == null || shortcut.getExtra("launch_exe_path").isEmpty())) {
                    shortcut.putExtra("launch_exe_path", exeFile.getAbsolutePath());
                    shortcut.saveData();
                }
                return mapCustomExecutableWinPath(customGameFolder, exeFile);
            }
        }
        return shortcut.path;
    }

    private String mapCustomExecutableWinPath(String customGameFolder, @NonNull File exeFile) {
        if (container != null && customGameFolder != null && !customGameFolder.isEmpty()) {
            String mappedPath =
                    WineUtils.getDriveCGameWindowsPath(
                            container, "CUSTOM", customGameFolder, exeFile.getAbsolutePath());
            if (mappedPath != null && !mappedPath.isEmpty()) {
                return mappedPath;
            }
        }
        return WineUtils.hostPathToRootWinePath(container, exeFile.getAbsolutePath());
    }

    private void updateShortcutExecLine(@NonNull String windowsPath) {
        if (shortcut == null) return;

        String execLine = "Exec=wine \"" + windowsPath + "\"";
        StringBuilder content = new StringBuilder();
        boolean replaced = false;
        for (String line : FileUtils.readLines(shortcut.file)) {
            if (line.startsWith("Exec=")) {
                content.append(execLine).append("\n");
                replaced = true;
            } else {
                content.append(line).append("\n");
            }
        }
        if (!replaced) {
            content.append(execLine).append("\n");
        }
        FileUtils.writeString(shortcut.file, content.toString());
    }

    private String repairStoreExecutableWinPath(String source, String gameInstallPath, String currentPath) {
        if (container == null
                || source == null
                || source.isEmpty()
                || gameInstallPath == null
                || gameInstallPath.isEmpty()
                || currentPath == null
                || currentPath.isEmpty()) {
            return currentPath;
        }

        String relativePath = extractRelativeDriveCGameExecutablePath(currentPath, source);
        if (relativePath == null || relativePath.isEmpty()) return currentPath;

        File nativeExe = new File(gameInstallPath, relativePath.replace("\\", File.separator));
        if (!nativeExe.isFile()) return currentPath;

        String repairedPath =
                WineUtils.getDriveCGameWindowsPath(
                        container, source, gameInstallPath, nativeExe.getAbsolutePath());
        if (repairedPath == null || repairedPath.isEmpty() || repairedPath.equals(currentPath)) {
            return currentPath;
        }

        updateShortcutExecLine(repairedPath);
        return repairedPath;
    }

    private String extractRelativeDriveCGameExecutablePath(String windowsPath, String source) {
        String prefix = "C:\\WinNative\\Games\\" + source + "\\";
        if (!windowsPath.regionMatches(true, 0, prefix, 0, prefix.length())) return null;

        String remainder = windowsPath.substring(prefix.length());
        int aliasSeparator = remainder.indexOf("\\");
        if (aliasSeparator < 0 || aliasSeparator + 1 >= remainder.length()) return null;
        return remainder.substring(aliasSeparator + 1);
    }

    private String getActiveGameDirectoryPath() {
        if (shortcut == null) return null;

        String[] candidatePaths = new String[] {
                shortcut.getExtra("game_install_path"),
                shortcut.getExtra("custom_game_folder"),
                shortcut.getExtra("custom_mount_path")
        };

        for (String candidatePath : candidatePaths) {
            if (candidatePath == null || candidatePath.isEmpty()) continue;
            return new File(candidatePath).getAbsolutePath();
        }

        return null;
    }

    private String resolveSteamGameInstallPath(int appId) {
        if (shortcut != null) {
            String shortcutInstallPath = shortcut.getExtra("game_install_path");
            if (shortcutInstallPath != null && !shortcutInstallPath.isEmpty()) {
                File shortcutInstallDir = new File(shortcutInstallPath);
                if (shortcutInstallDir.isDirectory()) {
                    return getCanonicalPathOrAbsolute(shortcutInstallDir);
                }
            }
        }

        String serviceInstallPath = SteamBridge.getAppDirPath(appId);
        if (serviceInstallPath == null || serviceInstallPath.isEmpty()) return serviceInstallPath;

        File serviceInstallDir = new File(serviceInstallPath);
        if (serviceInstallDir.isDirectory() && shortcut != null) {
            String shortcutInstallPath = shortcut.getExtra("game_install_path");
            String canonicalInstallPath = getCanonicalPathOrAbsolute(serviceInstallDir);
            if (!canonicalInstallPath.equals(shortcutInstallPath)) {
                shortcut.putExtra("game_install_path", canonicalInstallPath);
                shortcut.saveData();
            }
        }
        return getCanonicalPathOrAbsolute(serviceInstallDir);
    }

    private boolean parseBoolean(String value) {
        // Return true for "true", "1", "yes" (case-insensitive)
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        // Return false for any other value, including "false", "0", "no"
        return false;
    }

    // Inside XServerDisplayActivity class
    private void handleCapturedPointer(MotionEvent event) {
        if (isMouseDisabled) {
            return;
        }
        if (xServer.getRenderer() != null) {
            xServer.getRenderer().setCursorVisible(true);
        }
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            timeoutHandler.postDelayed(hideControlsRunnable, 5000);
        }
        boolean handled = false;

        int actionButton = event.getActionButton();
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS:
                if (actionButton == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0);
                    else
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                } else if (actionButton == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0);
                    else
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                } else if (actionButton == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MIDDLEDOWN, 0, 0, 0);
                    else
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE); // Add this line for middle mouse button press
                }
                handled = true;
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                if (actionButton == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0);
                    else
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                } else if (actionButton == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0);
                    else
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                } else if (actionButton == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MIDDLEUP, 0, 0, 0);
                    else
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE); // Add this line for middle mouse button release
                }
                handled = true;
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                float[] transformedPoint = XForm.transformPoint(xform, event.getX(), event.getY());
                if (xServer.isRelativeMouseMovement())
                    xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, (int)transformedPoint[0], (int)transformedPoint[1], 0);
                else
                    xServer.injectPointerMoveDelta((int)transformedPoint[0], (int)transformedPoint[1]);
                handled = true;
                break;
            case MotionEvent.ACTION_SCROLL:
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY <= -1.0f) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int)scrollY * 270);
                    else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN);
                    }
                } else if (scrollY >= 1.0f) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.WHEEL, 0, 0,(int)scrollY * 270);
                    else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP);
                    }
                }
                handled = true;
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyPreferredRefreshRate();
        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", false);

        if (gyroEnabled) {
            // Re-register the sensor listener when the activity is resumed
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        boolean cleaningUp = exitRequested.get() || sessionCleanupStarted.get() || activityDestroyed.get();

        if (!cleaningUp && environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        if (inputControlsView != null && touchpadView != null) {
            ControlsProfile activeProfile = inputControlsView.getProfile();
            if (activeProfile == null) activeProfile = resolvePreferredStartupProfile();
            if (activeProfile != null) showInputControls(activeProfile);
            else startTouchscreenTimeout();
        }
        startTime = System.currentTimeMillis();
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
        if (!cleaningUp) {
            ProcessHelper.resumeAllWineProcesses();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", false);

        if (gyroEnabled) {
            // Unregister the sensor listener when the activity is paused
            sensorManager.unregisterListener(gyroListener);
        }

        // Check if we are entering Picture-in-Picture mode
        boolean cleaningUp = exitRequested.get() || sessionCleanupStarted.get() || activityDestroyed.get();

        if (!cleaningUp && !isInPictureInPictureMode()) {
            // Only pause environment and xServerView if not in PiP mode
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }
        }

        if (touchpadView != null) {
            touchpadView.resetInputState();
        }
        if (inputControlsView != null) {
            inputControlsView.cancelActiveTouches();
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
        if (!cleaningUp) {
            ProcessHelper.pauseAllWineProcesses();
        }
    }


    private void savePlaytimeData() {
        savePlaytimeData(false);
    }

    private void savePlaytimeData(boolean synchronous) {
        long endTime = System.currentTimeMillis();
        long playtime = endTime - startTime;

        // Ensure that playtime is not negative
        if (playtime < 0) {
            playtime = 0;
        }

        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playtimeKey = shortcutName + "_playtime";

        // Accumulate the playtime into totalPlaytime
        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;
        editor.putLong(playtimeKey, totalPlaytime);
        if (synchronous) {
            editor.commit();
        } else {
            editor.apply();
        }

        // Reset startTime to the current time for the next interval
        startTime = System.currentTimeMillis();
    }


    private void incrementPlayCount() {
        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playCountKey = shortcutName + "_play_count";
        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;
        editor.putInt(playCountKey, playCount);
        editor.putLong(shortcutName + "_last_played", System.currentTimeMillis());
        editor.apply();
    }

    private boolean isSteamShortcut() {
        return shortcut != null && "STEAM".equals(shortcut.getExtra("game_source"));
    }

    private String normalizeProcessName(String name) {
        if (name == null) return "";

        String normalized = name.trim().replace("\"", "");
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex + 1 < normalized.length()) {
            normalized = normalized.substring(slashIndex + 1);
        }

        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".exe")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    @Nullable
    private ArrayList<ProcessInfo> captureWinHandlerProcessSnapshot() {
        if (winHandler == null) return null;

        final CountDownLatch latch = new CountDownLatch(1);
        final Object snapshotLock = new Object();
        final ArrayList<ProcessInfo> currentList = new ArrayList<>();
        final int[] expectedCount = {0};
        final OnGetProcessInfoListener previousListener = winHandler.getOnGetProcessInfoListener();

        OnGetProcessInfoListener listener = (index, count, processInfo) -> {
            if (previousListener != null) {
                previousListener.onGetProcessInfo(index, count, processInfo);
            }

            synchronized (snapshotLock) {
                if (count == 0 && processInfo == null) {
                    latch.countDown();
                    return;
                }

                if (index == 0) {
                    currentList.clear();
                    expectedCount[0] = count;
                }

                if (processInfo != null) {
                    currentList.add(processInfo);
                }

                if (expectedCount[0] == 0 || currentList.size() >= expectedCount[0]) {
                    latch.countDown();
                }
            }
        };

        winHandler.setOnGetProcessInfoListener(listener);
        try {
            winHandler.listProcesses();
            if (!latch.await(STEAM_PROCESS_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w("XServerDisplayActivity", "Timed out waiting for WinHandler process snapshot");
                return null;
            }

            synchronized (snapshotLock) {
                return new ArrayList<>(currentList);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w("XServerDisplayActivity", "Interrupted while waiting for WinHandler process snapshot", e);
            return null;
        } finally {
            winHandler.setOnGetProcessInfoListener(previousListener);
        }
    }

    private boolean shouldWatchSteamTermination(int status) {
        if (!isSteamShortcut() || winHandler == null) return false;

        if (!steamExitWatchRunning.compareAndSet(false, true)) {
            Log.d("XServerDisplayActivity", "Steam exit watch already running; ignoring duplicate termination callback");
            return true;
        }

        Log.d("XServerDisplayActivity",
                "Steam wrapper terminated with status " + status + "; watching Wine processes before exiting");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long lastNonCoreSeenAt = -1L;

                while (!exitRequested.get()
                        && !activityDestroyed.get()
                        && System.currentTimeMillis() - startTime < STEAM_TERMINATION_TIMEOUT_MS) {
                    ArrayList<ProcessInfo> snapshot = captureWinHandlerProcessSnapshot();
                    if (snapshot != null) {
                        ArrayList<String> activeNames = new ArrayList<>();
                        boolean hasNonCoreProcess = false;

                        for (ProcessInfo processInfo : snapshot) {
                            String normalized = normalizeProcessName(processInfo.name);
                            if (normalized.isEmpty()) continue;

                            activeNames.add(normalized);
                            if (!STEAM_EXIT_ALLOWLIST.contains(normalized)) {
                                hasNonCoreProcess = true;
                            }
                        }

                        Log.d("XServerDisplayActivity", "Steam exit watch snapshot: " + activeNames);

                        long now = System.currentTimeMillis();
                        if (hasNonCoreProcess) {
                            lastNonCoreSeenAt = now;
                        } else if (lastNonCoreSeenAt > 0L && now - lastNonCoreSeenAt >= STEAM_TERMINATION_POLL_MS) {
                            Log.d("XServerDisplayActivity", "Steam/game processes drained; exiting session");
                            requestExitOnUiThread("steam/game processes drained");
                            return;
                        } else if (lastNonCoreSeenAt < 0L && now - startTime >= STEAM_TERMINATION_GRACE_MS) {
                            Log.d("XServerDisplayActivity",
                                    "No non-core Steam/game process appeared after wrapper exit; exiting session");
                            requestExitOnUiThread("steam wrapper exited without spawning a game");
                            return;
                        }
                    }

                    Thread.sleep(STEAM_TERMINATION_POLL_MS);
                }

                if (!exitRequested.get() && !activityDestroyed.get()) {
                    Log.d("XServerDisplayActivity", "Steam exit watch timed out; exiting session");
                    requestExitOnUiThread("steam exit watch timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w("XServerDisplayActivity", "Steam exit watch interrupted", e);
                if (!exitRequested.get() && !activityDestroyed.get()) {
                    requestExitOnUiThread("steam exit watch interrupted");
                }
            } finally {
                steamExitWatchRunning.set(false);
            }
        });

        return true;
    }

    private void cleanupLingeringSessionProcesses(String reason) {
        ArrayList<String> before = ProcessHelper.listRunningWineProcesses();
        if (before.isEmpty()) return;

        Log.w("XServerDisplayActivity", "Cleaning lingering session processes before " + reason + ": "
                + ProcessHelper.listRunningWineProcessDetails());
        ArrayList<String> remaining = ProcessHelper.terminateSessionProcessesAndWait(2000, true);
        ProcessHelper.drainDeadChildren("pre-launch cleanup");
        ProcessHelper.scheduleDeadChildReapSweep("pre-launch cleanup", 2000, 200);
        if (!remaining.isEmpty()) {
            Log.e("XServerDisplayActivity", "Session cleanup still has remaining processes after " + reason + ": "
                    + ProcessHelper.listRunningWineProcessDetails());
        } else {
            Log.i("XServerDisplayActivity", "No lingering session processes remain after " + reason);
        }
    }

    private void requestExitOnUiThread(String reason) {
        runOnUiThread(() -> {
            if (activityDestroyed.get() || isFinishing() || isDestroyed()) {
                Log.d("XServerDisplayActivity", "Skipping exit request after teardown: " + reason);
                return;
            }
            exit();
        });
    }

    private boolean beginSessionCleanup(String trigger) {
        if (sessionCleanupStarted.compareAndSet(false, true)) {
            Log.d("XServerDisplayActivity", "Starting session cleanup from " + trigger);
            return true;
        }
        Log.d("XServerDisplayActivity", "Session cleanup already in progress; ignoring " + trigger);
        return false;
    }

    private void cleanupActivityCallbacks(String trigger) {
        activityDestroyed.set(true);

        try {
            if (preferences != null) {
                preferences.unregisterOnSharedPreferenceChangeListener(prefListener);
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to unregister preference listener during " + trigger, e);
        }

        try {
            cancelRealSteamWatchdog();
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to cancel Steam watchdog during " + trigger, e);
        }

        try {
            if (handler != null) {
                if (savePlaytimeRunnable != null) {
                    handler.removeCallbacks(savePlaytimeRunnable);
                }
                if (controllerAutoSwitchRunnable != null) {
                    handler.removeCallbacks(controllerAutoSwitchRunnable);
                }
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to remove handler callbacks during " + trigger, e);
        }

        try {
            if (timeoutHandler != null && hideControlsRunnable != null) {
                timeoutHandler.removeCallbacks(hideControlsRunnable);
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to remove pointer timeout during " + trigger, e);
        }

        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(gyroListener);
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to unregister sensor listener during " + trigger, e);
        }

        try {
            if (touchpadView != null) {
                touchpadView.resetInputState();
                touchpadView.releasePointerCapture();
                touchpadView.setOnCapturedPointerListener(null);
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to release pointer capture during " + trigger, e);
        }

        try {
            if (inputControlsView != null) {
                inputControlsView.cancelActiveTouches();
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to cancel active touches during " + trigger, e);
        }
    }

    private void stopXServer(String trigger) {
        try {
            if (xServer != null) {
                xServer.stop();
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to stop XServer during " + trigger, e);
        }
    }

    private void performForcedSessionCleanup(String trigger) {
        if (!beginSessionCleanup(trigger)) {
            Log.d("XServerLeakCheck", "Forced session cleanup already ran; skipping duplicate request from " + trigger);
            return;
        }

        Log.w("XServerLeakCheck", "Starting forced session cleanup from " + trigger);
        Log.d("XServerLeakCheck", "Forced cleanup initial process snapshot: "
                + ProcessHelper.listRunningWineProcessDetails());
        try {
            if (playtimePrefs != null) {
                savePlaytimeData(true);
            }
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to flush playtime during forced cleanup", e);
        }
        cleanupActivityCallbacks("forced cleanup (" + trigger + ")");

        try {
            AppTerminationHelper.stopManagedServices(getApplicationContext(), "xserver_forced_cleanup_" + trigger);
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to stop managed services during forced cleanup", e);
        }

        try {
            if (preloaderDialog != null) preloaderDialog.close();
        } catch (Exception e) {
            Log.w("XServerLeakCheck", "Failed to close preloader during forced cleanup", e);
        }

        try {
            if (midiHandler != null) {
                midiHandler.stop();
                midiHandler = null;
            }
        } catch (Exception e) {
            Log.e("XServerLeakCheck", "Failed to stop MidiHandler during forced cleanup", e);
        }

        try {
            if (winHandler != null) {
                winHandler.stop();
                winHandler = null;
            }
        } catch (Exception e) {
            Log.e("XServerLeakCheck", "Failed to stop WinHandler during forced cleanup", e);
        }

        try {
            if (wineRequestHandler != null) {
                wineRequestHandler.stop();
                wineRequestHandler = null;
            }
        } catch (Exception e) {
            Log.e("XServerLeakCheck", "Failed to stop WineRequestHandler during forced cleanup", e);
        }

        ArrayList<String> remaining = ProcessHelper.terminateSessionProcessesAndWait(2000, true);
        ProcessHelper.drainDeadChildren("forced cleanup (" + trigger + ")");
        ProcessHelper.scheduleDeadChildReapSweep("forced cleanup (" + trigger + ")", 4000, 200);

        try {
            if (environment != null) {
                environment.stopEnvironmentComponents();
                environment = null;
            }
        } catch (Exception e) {
            Log.e("XServerLeakCheck", "Failed to stop environment during forced cleanup", e);
        }

        stopXServer("forced cleanup (" + trigger + ")");
        xServer = null;
        xServerView = null;

        if (remaining.isEmpty()) {
            Log.i("XServerLeakCheck", "Forced session cleanup finished cleanly after " + trigger);
        } else {
            Log.e("XServerLeakCheck", "Remaining leaked session processes after forced cleanup from " + trigger + ": "
                    + ProcessHelper.listRunningWineProcessDetails());
        }
        Log.d("XServerLeakCheck", "Forced cleanup final process snapshot: "
                + ProcessHelper.listRunningWineProcessDetails());
    }

    private void exit() {
        if (activityDestroyed.get() || isFinishing() || isDestroyed()) {
            Log.d("XServerDisplayActivity", "Ignoring exit() on torn-down activity");
            return;
        }
        if (!exitRequested.compareAndSet(false, true)) {
            Log.d("XServerDisplayActivity", "Exit already in progress; ignoring duplicate request");
            return;
        }

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        if (shortcutName != null && !shortcutName.isEmpty()) {
            preloaderDialog.showOnUiThread("Closing " + shortcutName + "...");
        } else {
            preloaderDialog.showOnUiThread("Closing Container...");
        }
        
        // Sync store cloud saves before shutting down
        syncStoreCloudOnExit(() -> {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!beginSessionCleanup("exit")) {
                        return;
                    }
                    savePlaytimeData(true);
                    cleanupActivityCallbacks("exit");
                    if (midiHandler != null) midiHandler.stop();
                    if (winHandler != null) winHandler.stop();
                    if (wineRequestHandler != null) wineRequestHandler.stop();
                    /* Gracefully terminate all running wine processes first, so ALSA/audio
                     * threads are no longer fed data before we tear down their sockets. */
                    ArrayList<String> remaining = ProcessHelper.terminateSessionProcessesAndWait(2000, true);
                    ProcessHelper.drainDeadChildren("activity exit cleanup");
                    ProcessHelper.scheduleDeadChildReapSweep("activity exit cleanup", 4000, 200);
                    if (!remaining.isEmpty()) {
                        Log.e("XServerDisplayActivity", "Exit cleanup still has remaining session processes: " + remaining);
                    }
                    /* Now safe to tear down environment components (ALSA, PulseAudio, XServer, etc.)
                     * since Wine processes are no longer writing to their sockets. */
                    if (environment != null) {
                        environment.stopEnvironmentComponents();
                        environment = null;
                    }
                    Log.d("XServerDisplayActivity", "Process snapshot after environment stop: "
                            + ProcessHelper.listRunningWineProcessDetails());
                    stopXServer("exit");
                    winHandler = null;
                    wineRequestHandler = null;
                    midiHandler = null;
                    xServer = null;
                    xServerView = null;
                    if (preloaderDialog != null && preloaderDialog.isShowing()) preloaderDialog.closeOnUiThread();
                    closeAfterSessionExit();
                }
            }, 1000);
        });
    }

    private void closeAfterSessionExit() {
        if (launchedFromPinnedShortcut) {
            AppTerminationHelper.exitApplication(this, "shortcut_session_exit");
            return;
        }

        returnToUnifiedActivity();
    }

    private void returnToUnifiedActivity() {
        Intent intent = new Intent(this, UnifiedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private boolean isPinnedShortcutLaunchIntent(@Nullable Intent intent) {
        if (intent == null) return false;
        if (intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_PINNED_SHORTCUT, false)) return true;
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return false;

        android.net.Uri data = intent.getData();
        return data != null
                && "winnative".equals(data.getScheme())
                && BuildConfig.APPLICATION_ID.equals(data.getAuthority())
                && data.getPathSegments().contains("shortcut");
    }
    
    /**
     * Syncs cloud saves for supported stores when exiting a game.
     */
    private void syncStoreCloudOnExit(Runnable onComplete) {
        if (shortcut == null) {
            onComplete.run();
            return;
        }

        if (!isCloudSyncEnabledForShortcut() || com.winlator.cmod.feature.sync.CloudSyncHelper.isOfflineMode(shortcut)) {
            onComplete.run();
            return;
        }

        // Wrap onComplete to chain auto backup to Google Drive after store sync finishes
        Runnable afterStoreSync = () -> runAutoBackupIfEnabled(onComplete);

        String gameSource = shortcut.getExtra("game_source");
        if ("STEAM".equals(gameSource)) {
            syncSteamCloudOnExit(afterStoreSync);
            return;
        }

        if ("EPIC".equals(gameSource)) {
            syncEpicCloudOnExit(afterStoreSync);
            return;
        }

        if ("GOG".equals(gameSource)) {
            syncGogCloudOnExit(afterStoreSync);
            return;
        }

        onComplete.run();
    }

    private interface ExitUploadAction {
        void start(ExitUploadCallback callback);
    }

    private interface ExitUploadCallback {
        void onComplete(ExitUploadResult result);
    }

    private interface ExitUploadBlockingAction {
        ExitUploadResult run() throws Exception;
    }

    private static final class ExitUploadResult {
        final boolean success;
        @NonNull final String message;
        final boolean retryable;

        ExitUploadResult(boolean success, @Nullable String message, boolean retryable) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.retryable = retryable;
        }
    }

    private void runExitUploadWithRetries(
            String uploadName,
            String retryStatusMessage,
            ExitUploadAction action,
            Runnable onComplete) {
        runExitUploadWithRetries(uploadName, retryStatusMessage, 1, action, onComplete);
    }

    private void runExitUploadWithRetries(
            String uploadName,
            String retryStatusMessage,
            int attempt,
            ExitUploadAction action,
            Runnable onComplete) {
        if (activityDestroyed.get() || isFinishing() || isDestroyed()) {
            Log.w("XServerDisplayActivity", "Skipping " + uploadName + " because activity is already torn down");
            onComplete.run();
            return;
        }

        try {
            action.start(result -> {
                if (result.success) {
                    if (result.message.isEmpty()) {
                        Log.i("XServerDisplayActivity", uploadName + " succeeded on attempt " + attempt);
                    } else {
                        Log.i(
                                "XServerDisplayActivity",
                                uploadName + " succeeded on attempt " + attempt + ": " + result.message);
                    }
                    onComplete.run();
                    return;
                }

                String failureMessage = result.message.isEmpty() ? "No error message provided." : result.message;
                if (result.retryable && attempt < EXIT_CLOUD_UPLOAD_MAX_ATTEMPTS) {
                    int nextAttempt = attempt + 1;
                    long delayMs = EXIT_CLOUD_UPLOAD_RETRY_DELAY_MS * attempt;
                    Log.w(
                            "XServerDisplayActivity",
                            uploadName
                                    + " failed on attempt "
                                    + attempt
                                    + "/"
                                    + EXIT_CLOUD_UPLOAD_MAX_ATTEMPTS
                                    + ": "
                                    + failureMessage
                                    + ". Retrying in "
                                    + delayMs
                                    + "ms.");
                    if (preloaderDialog != null && retryStatusMessage != null && !retryStatusMessage.isEmpty()) {
                        preloaderDialog.showOnUiThread(
                                retryStatusMessage + " Retry " + nextAttempt + "/" + EXIT_CLOUD_UPLOAD_MAX_ATTEMPTS + "...");
                    }
                    Handler activeHandler = handler != null ? handler : new Handler(Looper.getMainLooper());
                    activeHandler.postDelayed(
                            () -> runExitUploadWithRetries(uploadName, retryStatusMessage, nextAttempt, action, onComplete),
                            delayMs);
                    return;
                }

                if (result.retryable) {
                    Log.e(
                            "XServerDisplayActivity",
                            uploadName
                                    + " failed after "
                                    + EXIT_CLOUD_UPLOAD_MAX_ATTEMPTS
                                    + " attempts: "
                                    + failureMessage);
                } else {
                    Log.w("XServerDisplayActivity", uploadName + " failed without retry: " + failureMessage);
                }
                onComplete.run();
            });
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", uploadName + " threw before upload could start", e);
            onComplete.run();
        }
    }

    private void runBlockingExitUpload(
            String workerName,
            ExitUploadBlockingAction action,
            ExitUploadCallback callback) {
        new Thread(() -> {
            ExitUploadResult result;
            try {
                result = action.run();
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : (workerName + " failed");
                result = new ExitUploadResult(false, message, true);
            }

            if (activityDestroyed.get() || isFinishing() || isDestroyed()) {
                Log.w("XServerDisplayActivity", workerName + " finished after activity teardown");
                return;
            }

            final ExitUploadResult finalResult = result;
            runOnUiThread(() -> callback.onComplete(finalResult));
        }, workerName).start();
    }

    private boolean isRetryableSteamExitSyncMessage(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return true;
        }
        String normalized = message.toLowerCase(java.util.Locale.US);
        return !normalized.contains("offline");
    }

    private boolean isRetryableGoogleDriveBackupMessage(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return true;
        }
        String normalized = message.toLowerCase(java.util.Locale.US);
        return !normalized.contains("not enabled")
                && !normalized.contains("not signed in")
                && !normalized.contains("authorization required")
                && !normalized.contains("no local save files")
                && !normalized.contains("save files are empty")
                && !normalized.contains("cannot determine save directory");
    }

    /**
     * If Cloud Sync Auto Backup is enabled, zips the local save and uploads to Google Drive.
     * Reuses GameSaveBackupManager but skips downloading from the store provider.
     */
    private void runAutoBackupIfEnabled(Runnable onComplete) {
        if (shortcut == null) {
            onComplete.run();
            return;
        }

        if (!isCloudSyncEnabledForShortcut() || com.winlator.cmod.feature.sync.CloudSyncHelper.isOfflineMode(shortcut)) {
            onComplete.run();
            return;
        }

        if (!com.winlator.cmod.feature.sync.google.GameSaveBackupManager.INSTANCE.isAutoBackupEnabled(this)) {
            onComplete.run();
            return;
        }

        String gameSource = shortcut.getExtra("game_source");
        String gameId;
        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource source;

        if ("STEAM".equals(gameSource)) {
            gameId = shortcut.getExtra("app_id");
            source = com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.STEAM;
        } else if ("EPIC".equals(gameSource)) {
            gameId = shortcut.getExtra("app_id");
            source = com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.EPIC;
        } else if ("GOG".equals(gameSource)) {
            gameId = shortcut.getExtra("gog_id");
            source = com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.GOG;
        } else {
            onComplete.run();
            return;
        }

        if (gameId == null || gameId.isEmpty()) {
            onComplete.run();
            return;
        }

        String gameName = shortcutName != null && !shortcutName.isEmpty() ? shortcutName : (shortcut.name != null ? shortcut.name : "Unknown");

        Log.d("XServerDisplayActivity", "Starting auto backup to Google Drive for " + gameSource + "/" + gameId);
        preloaderDialog.showOnUiThread("Backing up save to Google Drive...");

        runExitUploadWithRetries(
                "Google Drive auto backup",
                "Backing up save to Google Drive...",
                callback -> runBlockingExitUpload(
                        "GoogleDriveExitBackup",
                        () -> {
                            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult result =
                                    (com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult)
                                            kotlinx.coroutines.BuildersKt.runBlocking(
                                                    kotlinx.coroutines.Dispatchers.getIO(),
                                                    (scope, continuation) ->
                                                            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.INSTANCE.autoBackupToGoogle(
                                                                    this,
                                                                    source,
                                                                    gameId,
                                                                    gameName,
                                                                    continuation));
                            return new ExitUploadResult(
                                    result.getSuccess(),
                                    result.getMessage(),
                                    isRetryableGoogleDriveBackupMessage(result.getMessage()));
                        },
                        callback),
                onComplete);
    }

    private boolean isCloudSyncEnabledForShortcut() {
        return shortcut == null || !"1".equals(shortcut.getExtra("cloud_sync_disabled", "0"));
    }

    /**
     * Synchronously zip the current local save and upload it to the game's Save History folder.
     * Called when the user is about to overwrite local with a cloud version and opted in to
     * keeping a backup of the replaced side.
     */
    private void backupDiscardedSaveForShortcut(
        com.winlator.cmod.runtime.container.Shortcut shortcut,
        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupOrigin origin
    ) {
        if (shortcut == null) return;
        String gameSource = shortcut.getExtra("game_source");
        String gameId;
        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource source;
        if ("STEAM".equals(gameSource)) {
            gameId = shortcut.getExtra("app_id");
            source = com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.STEAM;
        } else if ("EPIC".equals(gameSource)) {
            gameId = shortcut.getExtra("app_id");
            source = com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.EPIC;
        } else if ("GOG".equals(gameSource)) {
            gameId = shortcut.getExtra("gog_id");
            source = com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.GOG;
        } else {
            return;
        }
        if (gameId == null || gameId.isEmpty()) return;

        String gameName = shortcut.name != null ? shortcut.name : "Unknown";
        final String gameIdFinal = gameId;
        final com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource sourceFinal = source;
        final String gameNameFinal = gameName;

        try {
            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult result =
                (com.winlator.cmod.feature.sync.google.GameSaveBackupManager.BackupResult) kotlinx.coroutines.BuildersKt.runBlocking(
                    kotlinx.coroutines.Dispatchers.getIO(),
                    (scope, continuation) ->
                        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.INSTANCE.backupDiscardedSave(
                            this, sourceFinal, gameIdFinal, gameNameFinal, origin, continuation
                        )
                );
            android.util.Log.i("CloudSync", "Discarded save backup: " + result.getMessage());
        } catch (Exception e) {
            android.util.Log.w("CloudSync", "Failed to back up discarded save", e);
        }
    }

    /**
     * Syncs Steam cloud saves when exiting a Steam game.
     * Calls SteamService.closeApp() which runs SteamAutoCloud.syncUserFiles()
     * to upload modified save files to Steam Cloud.
     */
    private void syncSteamCloudOnExit(Runnable onComplete) {
        boolean isSteamGame = "STEAM".equals(shortcut.getExtra("game_source"));
        if (!isSteamGame) {
            onComplete.run();
            return;
        }
        
        try {
            int appId = Integer.parseInt(shortcut.getExtra("app_id"));
            Log.d("XServerDisplayActivity", "Syncing Steam cloud saves for appId=" + appId);
            
            preloaderDialog.showOnUiThread("Cloud Sync Uploading...");

            runExitUploadWithRetries(
                    "Steam cloud sync for appId=" + appId,
                    "Cloud Sync Uploading...",
                    callback ->
                            com.winlator.cmod.feature.stores.steam.service.SteamService.syncCloudOnExit(
                                    this,
                                    appId,
                                    new com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.CloudSyncCallback() {
                                        @Override
                                        public void onProgress(String message, float progress) {
                                            runOnUiThread(() -> {
                                                int pct = (int) (progress * 100);
                                                preloaderDialog.showOnUiThread(message + " (" + pct + "%)");
                                            });
                                        }

                                        @Override
                                        public void onComplete(boolean success, String message) {
                                            callback.onComplete(
                                                    new ExitUploadResult(
                                                            success,
                                                            message,
                                                            isRetryableSteamExitSyncMessage(message)));
                                        }
                                    }),
                    onComplete);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to initiate Steam cloud sync", e);
            onComplete.run();
        }
    }

    private void syncEpicCloudOnExit(Runnable onComplete) {
        String appIdStr = shortcut.getExtra("app_id");
        if (appIdStr == null || appIdStr.isEmpty()) {
            onComplete.run();
            return;
        }

        try {
            int appId = Integer.parseInt(appIdStr);
            Log.d("XServerDisplayActivity", "Syncing Epic cloud saves for appId=" + appId);
            preloaderDialog.showOnUiThread("Cloud Sync Uploading...");

            runExitUploadWithRetries(
                    "Epic cloud sync for appId=" + appId,
                    "Cloud Sync Uploading...",
                    callback -> runBlockingExitUpload(
                            "EpicExitCloudSync",
                            () -> {
                                Boolean syncSuccess = (Boolean) kotlinx.coroutines.BuildersKt.runBlocking(
                                        kotlinx.coroutines.Dispatchers.getIO(),
                                        (scope, continuation) -> com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager.INSTANCE.syncCloudSaves(
                                                this,
                                                appId,
                                                "upload",
                                                continuation
                                        )
                                );
                                boolean success = Boolean.TRUE.equals(syncSuccess);
                                return new ExitUploadResult(
                                        success,
                                        success
                                                ? "Epic cloud upload completed."
                                                : "Epic cloud upload reported failure.",
                                        true);
                            },
                            callback),
                    onComplete);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to parse Epic app_id for cloud sync", e);
            onComplete.run();
        }
    }

    private void syncGogCloudOnExit(Runnable onComplete) {
        String gogId = shortcut.getExtra("gog_id");
        if (gogId == null || gogId.isEmpty()) {
            onComplete.run();
            return;
        }

        Log.d("XServerDisplayActivity", "Syncing GOG cloud saves for gogId=" + gogId);
        preloaderDialog.showOnUiThread("Cloud Sync Uploading...");

        runExitUploadWithRetries(
                "GOG cloud sync for gogId=" + gogId,
                "Cloud Sync Uploading...",
                callback -> runBlockingExitUpload(
                        "GogExitCloudSync",
                        () -> {
                            Boolean syncSuccess = (Boolean) kotlinx.coroutines.BuildersKt.runBlocking(
                                    kotlinx.coroutines.Dispatchers.getIO(),
                                    (scope, continuation) -> com.winlator.cmod.feature.stores.gog.service.GOGService.Companion.syncCloudSaves(
                                            this,
                                            "GOG_" + gogId,
                                            "upload",
                                            continuation
                                    )
                            );
                            boolean success = Boolean.TRUE.equals(syncSuccess);
                            return new ExitUploadResult(
                                    success,
                                    success
                                            ? "GOG cloud upload completed."
                                            : "GOG cloud upload reported failure.",
                                    true);
                        },
                        callback),
                onComplete);
    }

    private void showLaunchPreloader(String text) {
        if (preloaderDialog == null) return;
        preloaderDialog.showOnUiThread(text, cachedDisplayName, cachedPlatform, cachedContainerLabel);
    }

    private void showLaunchPreloaderProgress(String text, int percent) {
        if (preloaderDialog == null) return;
        preloaderDialog.showProgressOnUiThread(
                text,
                cachedDisplayName,
                cachedPlatform,
                cachedContainerLabel,
                Math.max(0, Math.min(100, percent))
        );
    }

    @Override
    protected void onDestroy() {
        activityDestroyed.set(true);
        if (preloaderDialog != null) {
            preloaderDialog.close();
        }
        super.onDestroy();
        // Schedule a deferred update check 10 s after game exit
        if (!switchLaunchInProgress.get()) {
            UpdateChecker.INSTANCE.schedulePostGameCheck(this);
        }

        if (!sessionCleanupStarted.get()) {
            performForcedSessionCleanup("onDestroy");
        }

        // Leak detection: log warnings if resources were not cleaned up by exit()
        String tag = "XServerLeakCheck";
        if (!exitRequested.get()) {
            Log.w(tag, "onDestroy called without exit() — activity may have been killed by system");
        }
        ArrayList<String> remainingProcesses = ProcessHelper.listRunningWineProcesses();
        if (!remainingProcesses.isEmpty()) {
            Log.e(tag, "Wine processes still running: " + ProcessHelper.listRunningWineProcessDetails());
        } else {
            Log.i(tag, "No Wine/session processes remain at onDestroy leak check");
        }
        if (environment != null) {
            Log.w(tag, "Environment not null — components may not have been stopped");
        }
        if (winHandler != null && winHandler.getSocket() != null && !winHandler.getSocket().isClosed()) {
            Log.e(tag, "WinHandler socket still open");
        }
        if (wineRequestHandler != null && wineRequestHandler.getServerSocket() != null && !wineRequestHandler.getServerSocket().isClosed()) {
            Log.e(tag, "WineRequestHandler server socket still open");
        }
        if (midiHandler != null && midiHandler.getSocket() != null && !midiHandler.getSocket().isClosed()) {
            Log.e(tag, "MidiHandler socket still open");
        }
    }

    private boolean isCustomShortcut() {
        return shortcut != null
                && "CUSTOM".equals(shortcut.getExtra("game_source", "CUSTOM"))
                && !isSteamShortcut();
    }

    private boolean isRealSteamLaunchEnabledForShortcut() {
        if (!isSteamShortcut()) {
            return false;
        }
        return shortcut != null
                ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                : container != null && container.isLaunchRealSteam();
    }

    private boolean isColdClientEnabledForShortcut() {
        if (!isSteamShortcut()) return false;
        if (isRealSteamLaunchEnabledForShortcut()) return false; // mutually exclusive
        return shortcut != null
                ? parseBoolean(getShortcutSetting("useColdClient", container.isUseColdClient() ? "1" : "0"))
                : container != null && container.isUseColdClient();
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
        if (!sessionCleanupStarted.get() && isFinishing() && !isChangingConfigurations()) {
            performForcedSessionCleanup("onStop finishing");
        }
    }

    private void handleNavigationBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                renderDrawerMenu();
                drawerLayout.openDrawer(GravityCompat.START);
            }
            else drawerLayout.closeDrawers();
        }
    }

    private String currentGyroActivatorLabel() {
        String[] names = getResources().getStringArray(R.array.button_options);
        int[] keycodes = getResources().getIntArray(R.array.button_keycodes);
        int currentKeycode = preferences.getInt("gyro_trigger_button", KeyEvent.KEYCODE_BUTTON_L1);
        int index = -1;
        for (int i = 0; i < keycodes.length; i++) {
            if (keycodes[i] == currentKeycode) {
                index = i;
                break;
            }
        }
        if (index == -1) index = 6; // Default to L1
        return index < names.length ? names[index] : names[0];
    }

    private void showGyroActivatorPicker() {
        final ComposeView dialogComposeView = findViewById(R.id.DialogComposeView);
        final ComposeView navigationComposeView = findViewById(R.id.NavigationComposeView);
        if (dialogComposeView == null) return;

        String[] names = getResources().getStringArray(R.array.button_options);
        int[] keycodes = getResources().getIntArray(R.array.button_keycodes);

        dialogComposeView.setVisibility(View.VISIBLE);
        dialogComposeView.setFocusable(true);
        dialogComposeView.setClickable(true);
        
        XServerDrawerMenuKt.setupGyroActivatorDialog(
            dialogComposeView,
            currentGyroActivatorLabel(),
            names,
            keycodes,
            () -> {
                dialogComposeView.setVisibility(View.GONE);
                dialogComposeView.setContent(null);
                dialogComposeView.setFocusable(false);
                dialogComposeView.setClickable(false);
                dialogComposeView.clearFocus();
                if (navigationComposeView != null) navigationComposeView.requestFocus();
                return kotlin.Unit.INSTANCE;
            },
            (keycode) -> {
                preferences.edit().putInt("gyro_trigger_button", keycode).apply();
                dialogComposeView.setVisibility(View.GONE);
                dialogComposeView.setContent(null);
                dialogComposeView.setFocusable(false);
                dialogComposeView.setClickable(false);
                dialogComposeView.clearFocus();
                if (navigationComposeView != null) navigationComposeView.requestFocus();
                renderDrawerMenu();
                return kotlin.Unit.INSTANCE;
            }
        );
    }

    private void renderDrawerMenu() {
        ComposeView navigationComposeView = findViewById(R.id.NavigationComposeView);
        if (navigationComposeView == null) return;

        XServerDrawerState state = XServerDrawerMenuKt.buildXServerDrawerState(
                this,
                isRelativeMouseMovement,
                isMouseDisabled,
                frameRating != null && frameRating.getVisibility() == View.VISIBLE,
                isPaused,
                true,
                enableLogsMenu,
                isNativeRenderingEnabled,
                getString(R.string.session_xserver_native_rendering),
                getString(isNativeRenderingEnabled ? R.string.session_xserver_native_rendering_enabled : R.string.session_xserver_native_rendering_disabled),
                hudTransparency,
                hudScale,
                hudElements,
                dualSeriesBattery,
                hudCardExpanded,
                preferences.getBoolean("gyro_enabled", false),
                preferences.getInt("gyro_mode", 0),
                currentGyroActivatorLabel(),
                preferences.getBoolean("process_gyro_with_left_trigger", false),
                preferences.getBoolean("mouse_gyro_enabled", false),
                preferences.getFloat("gyro_mouse_scale", 50.0f),
                preferences.getFloat("gyro_x_sensitivity", 1.0f),
                preferences.getFloat("gyro_y_sensitivity", 1.0f),
                preferences.getFloat("gyro_smoothing", 0.9f),
                preferences.getFloat("gyro_deadzone", 0.05f),
                preferences.getBoolean("invert_gyro_x", false),
                preferences.getBoolean("invert_gyro_y", false),
                gyroscopeCardExpanded,
                xServerView != null ? xServerView.getRenderer().getFpsLimit() : 0,
                screenEffectsCardExpanded,
                fsrEnabled,
                fsrMode,
                fsrSharpness,
                colorProfile
        );

        if (drawerActionListener == null) {
            drawerActionListener = new XServerDrawerActionListener() {
                    @Override
                    public void onActionSelected(int itemId) {
                        handleDrawerAction(itemId);
                    }

                    @Override
                    public void onHUDElementToggled(int index, boolean enabled) {
                        hudElements[index] = enabled;
                        if (frameRating != null) frameRating.toggleElement(index, enabled);
                        saveHUDSettings();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onHUDTransparencyChanged(float transparency) {
                        hudTransparency = transparency;
                        if (frameRating != null) frameRating.setHudAlpha(transparency);
                        saveHUDSettings();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onHUDScaleChanged(float scale) {
                        hudScale = scale;
                        if (frameRating != null) frameRating.setHudScale(scale);
                        saveHUDSettings();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onDualSeriesBatteryChanged(boolean enabled) {
                        dualSeriesBattery = enabled;
                        preferences.edit().putBoolean(FrameRating.PREF_HUD_DUAL_SERIES_BATTERY, enabled).apply();
                        if (frameRating != null) frameRating.setDualSeriesBattery(enabled);
                        renderDrawerMenu();
                    }

                    @Override
                    public void onHUDCardExpandedChanged(boolean expanded) {
                        hudCardExpanded = expanded;
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroscopeEnabledChanged(boolean enabled) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("gyro_enabled", enabled);
                        editor.apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroscopeModeSelected(int mode) {
                        preferences.edit().putInt("gyro_mode", mode).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroscopeActivatorClick() {
                        showGyroActivatorPicker();
                    }

                    @Override
                    public void onRightStickGyroChanged(boolean enabled) {
                        preferences.edit().putBoolean("process_gyro_with_left_trigger", enabled).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroMouseEnabledChanged(boolean enabled) {
                        preferences.edit().putBoolean("mouse_gyro_enabled", enabled).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroMouseScaleChanged(float scale) {
                        preferences.edit().putFloat("gyro_mouse_scale", scale).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroXSensitivityChanged(float sensitivity) {
                        preferences.edit().putFloat("gyro_x_sensitivity", sensitivity).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroYSensitivityChanged(float sensitivity) {
                        preferences.edit().putFloat("gyro_y_sensitivity", sensitivity).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroSmoothingChanged(float smoothing) {
                        preferences.edit().putFloat("gyro_smoothing", smoothing).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroDeadzoneChanged(float deadzone) {
                        preferences.edit().putFloat("gyro_deadzone", deadzone).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onInvertGyroXChanged(boolean enabled) {
                        preferences.edit().putBoolean("invert_gyro_x", enabled).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onInvertGyroYChanged(boolean enabled) {
                        preferences.edit().putBoolean("invert_gyro_y", enabled).apply();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onGyroscopeCardExpandedChanged(boolean expanded) {
                        gyroscopeCardExpanded = expanded;
                        renderDrawerMenu();
                    }

                    @Override
                    public void onFPSLimitChanged(int limit) {
                        runtimeFpsLimit = Math.max(0, limit);
                        if (xServerView != null) {
                            xServerView.getRenderer().setFpsLimit(runtimeFpsLimit);
                        }
                        applyPreferredRefreshRate();
                        if (shortcut != null) {
                            shortcut.putExtra("fpsLimit", runtimeFpsLimit > 0 ? String.valueOf(runtimeFpsLimit) : null);
                            shortcut.saveData();
                        }
                        renderDrawerMenu();
                    }

                    @Override
                    public void onScreenEffectsCardExpandedChanged(boolean expanded) {
                        screenEffectsCardExpanded = expanded;
                        renderDrawerMenu();
                    }

                    @Override
                    public void onFSREnabledChanged(boolean enabled) {
                        fsrEnabled = enabled;
                        preferences.edit().putBoolean("fsr_enabled", enabled).apply();
                        applyScreenEffects();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onFSRModeSelected(int mode) {
                        fsrMode = mode;
                        preferences.edit().putInt("fsr_mode", mode).apply();
                        applyScreenEffects();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onFSRSharpnessChanged(int sharpness) {
                        fsrSharpness = sharpness;
                        preferences.edit().putInt("fsr_sharpness", sharpness).apply();
                        applyScreenEffects();
                        renderDrawerMenu();
                    }

                    @Override
                    public void onColorProfileSelected(int profile) {
                        colorProfile = profile;
                        preferences.edit().putInt("color_profile", profile).apply();
                        applyScreenEffects();
                        renderDrawerMenu();
                    }
                };
        }

        if (drawerStateHolder == null) {
            drawerStateHolder = new XServerDrawerStateHolder(state);
            XServerDrawerMenuKt.setupXServerDrawerComposeView(
                    navigationComposeView,
                    drawerStateHolder,
                    this,
                    drawerActionListener
            );
            return;
        }

        drawerStateHolder.setState(state);
    }

    private void applyScreenEffects() {
        GLRenderer renderer = xServerView != null ? xServerView.getRenderer() : null;
        if (renderer == null) return;
        EffectComposer composer = renderer.getEffectComposer();
        if (composer == null) return;

        // Handle FSR
        FSREffect fsr = composer.getEffect(FSREffect.class);
        if (fsrEnabled) {
            if (fsr == null) {
                fsr = new FSREffect();
                composer.addEffect(fsr);
            }
            fsr.setMode(fsrMode);
            fsr.setLevel((fsrSharpness / 25.0f) + 1.0f);
        } else if (fsr != null) {
            composer.removeEffect(fsr);
        }

        // Handle Color Profiles
        composer.removeEffect(composer.getEffect(HDREffect.class));
        composer.removeEffect(composer.getEffect(NaturalEffect.class));
        composer.removeEffect(composer.getEffect(CRTEffect.class));

        switch (colorProfile) {
            case 1: // HDR
                composer.addEffect(new HDREffect());
                break;
            case 2: // Natural
                composer.addEffect(new NaturalEffect());
                break;
            case 3: // CRT Effect
                composer.addEffect(new CRTEffect());
                break;
        }
    }

    private void loadScreenEffectsSettings() {
        if (preferences == null) return;
        fsrEnabled = preferences.getBoolean("fsr_enabled", false);
        fsrMode = preferences.getInt("fsr_mode", 0);
        fsrSharpness = preferences.getInt("fsr_sharpness", 100);
        colorProfile = preferences.getInt("color_profile", 0);
    }

    private void loadHUDSettings() {
        if (container == null) return;
        String json = container.getExtra("hudSettings");
        if (json != null && !json.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(json);
                hudTransparency = (float) obj.optDouble("transparency", 1.0);
                hudScale = (float) obj.optDouble("scale", 1.0);
                hudElements[0] = obj.optBoolean("showFPS", true);
                hudElements[1] = obj.optBoolean("showRenderer", true);
                hudElements[2] = obj.optBoolean("showGPU", true);
                hudElements[3] = obj.optBoolean("showCpuRam", true);
                hudElements[4] = obj.optBoolean("showBattTemp", true);
                hudElements[5] = obj.optBoolean("showGraph", true);
            } catch (JSONException e) {
                Log.e("XServerDisplayActivity", "Failed to load HUD settings", e);
            }
        }
    }

    private void saveHUDSettings() {
        if (container == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("transparency", hudTransparency);
            obj.put("scale", hudScale);
            obj.put("showFPS", hudElements[0]);
            obj.put("showRenderer", hudElements[1]);
            obj.put("showGPU", hudElements[2]);
            obj.put("showCpuRam", hudElements[3]);
            obj.put("showBattTemp", hudElements[4]);
            obj.put("showGraph", hudElements[5]);
            container.putExtra("hudSettings", obj.toString());
            container.saveData();
        } catch (JSONException e) {
            Log.e("XServerDisplayActivity", "Failed to save HUD settings", e);
        }
    }

    private void applyHUDSettings() {
        if (frameRating != null) {
            frameRating.setHudAlpha(hudTransparency);
            frameRating.setHudScale(hudScale);
            frameRating.setDualSeriesBattery(dualSeriesBattery);
            frameRating.setIsNative(isNativeRenderingEnabled);
            for (int i = 0; i < hudElements.length; i++) {
                frameRating.toggleElement(i, hudElements[i]);
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private boolean handleDrawerAction(int itemId) {
        final GLRenderer renderer = xServerView.getRenderer();
        switch (itemId) {
            case R.id.main_menu_gyroscope_reset:
                if (winHandler != null) {
                    winHandler.updateGyroData(0, 0); // This effectively resets it
                }
                break;
            case R.id.main_menu_keyboard:
                AppUtils.showKeyboard(this);
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_input_controls:
                showInputControlsDialog();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_controller_manager:
                ControllerAssignmentDialog.show(this, winHandler);
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_fps_monitor:
                if (frameRating == null) {
                    FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
                    frameRating = new FrameRating(this, graphicsDriverConfig);
                    frameRating.setRenderer(lastRendererName);
                    if (lastGpuName != null) frameRating.setGpuName(lastGpuName);
                    frameRating.setVisibility(View.GONE);
                    applyHUDSettings();
                    rootView.addView(frameRating);
                }
                boolean isFpsVisible = frameRating.getVisibility() == View.VISIBLE;
                boolean becomingVisible = !isFpsVisible;
                frameRating.setVisibility(becomingVisible ? View.VISIBLE : View.GONE);
                if (becomingVisible) {
                    syncFrameRatingWithExistingWindows();
                    applyHUDSettings();
                }
                updateHUDRenderMode();
                
                // Save FPS monitor state globally so it persists across all games/containers
                preferences.edit().putBoolean("fps_monitor_enabled", becomingVisible).apply();
                effectiveShowFPS = becomingVisible;
                renderDrawerMenu();
                break;
            case R.id.main_menu_relative_mouse_movement:
                isRelativeMouseMovement = !isRelativeMouseMovement;
                xServer.setRelativeMouseMovement(isRelativeMouseMovement);
                renderDrawerMenu();
                break;
            case R.id.main_menu_disable_mouse:
                isMouseDisabled = !isMouseDisabled;
                touchpadView.setMouseEnabled(!isMouseDisabled);
                renderDrawerMenu();
                break;
            case R.id.main_menu_toggle_fullscreen:
                renderer.toggleFullscreen();
                touchpadView.toggleFullscreen();
                renderDrawerMenu();
                break;
            case R.id.main_menu_pause:
                if (isPaused) {
                    ProcessHelper.resumeAllWineProcesses();
                }
                else {
                    ProcessHelper.pauseAllWineProcesses();
                }
                isPaused = !isPaused;
                renderDrawerMenu();
                break;
            case R.id.main_menu_pip_mode:
                enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().build());
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_task_manager:
                new TaskManagerDialog(this).show();
                break;
            case R.id.main_menu_magnifier:
                if (isNativeRenderingEnabled) {
                    showToast(this, getString(R.string.session_drawer_magnifier_disabled_native_subtitle));
                    renderDrawerMenu();
                    break;
                }
                if (magnifierView == null) {
                    FrameLayout container = findViewById(R.id.FLXServerDisplay);
                    magnifierView = new MagnifierView(this);
                    magnifierView.setZoomButtonCallback(value -> {
                        renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                        magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    });
                    magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    magnifierView.setHideButtonCallback(() -> {
                        container.removeView(magnifierView);
                        magnifierView = null;
                    });
                    container.addView(magnifierView);
                }
                renderDrawerMenu();
                break;
            case R.id.main_menu_logs:
                debugDialog.show();
                break;
            case R.id.main_menu_native_rendering:
                isNativeRenderingEnabled = !isNativeRenderingEnabled;
                preferences.edit().putBoolean("use_dri3", isNativeRenderingEnabled).apply();
                if (frameRating != null) frameRating.setIsNative(isNativeRenderingEnabled);
                renderDrawerMenu();
                showToast(this, getString(isNativeRenderingEnabled
                    ? R.string.session_xserver_native_rendering_enabled_toast
                    : R.string.session_xserver_native_rendering_disabled_toast));
                break;
            case R.id.main_menu_exit:
                drawerLayout.closeDrawers();
                exit();
                break;
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && cursorLock) {
            touchpadView.requestPointerCapture();
            touchpadView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                @Override
                public boolean onCapturedPointer(View view, MotionEvent event) {
                    handleCapturedPointer(event);
                    return true;
                }
            });
        }
        else if (!hasFocus) {
            if (touchpadView != null) {
                touchpadView.resetInputState();
            }
            if (inputControlsView != null) {
                inputControlsView.cancelActiveTouches();
            }
            touchpadView.releasePointerCapture();
            touchpadView.setOnCapturedPointerListener(null);
        }
    }

    private void cancelMousePointerTimeout() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }
    }

    private boolean isPointerMotionEvent(MotionEvent event) {
        int source = event.getSource();
        boolean isPointerClass =
                (source & InputDevice.SOURCE_CLASS_POINTER) == InputDevice.SOURCE_CLASS_POINTER;
        return isPointerClass && !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN);
    }

    private boolean isControllerMotionEvent(MotionEvent event) {
        int source = event.getSource();
        boolean isGamepad =
                (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        boolean isJoystick =
                (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        return (isGamepad || isJoystick) && !isPointerMotionEvent(event);
    }

    private void setupWineSystemFiles() {
        Log.d("ContainerLaunch", "=== setupWineSystemFiles START === container=" + container.id +
                " wine=" + wineVersion + " arch=" + (wineInfo != null ? wineInfo.getArch() : "null") +
                " rootDir=" + container.getRootDir().getAbsolutePath());

        ensureWinePrefixReady();

        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("imgVersion").equals(imgVersion)) {
            Log.d("ContainerLaunch", "Version mismatch, applying general patches (app=" + appVersion + " img=" + imgVersion + ")");
            applyGeneralPatches(container);
            container.putExtra("appVersion", appVersion);
            container.putExtra("imgVersion", imgVersion);
            firstTimeBoot = true; // force wincomponent DLLs re-extraction on app update
            containerDataChanged = true;
        }

        // Check after applyGeneralPatches — container_pattern_common.tzst provides these files
        ensureWinePrefixEssentialFiles();

        String dxwrapper = shortcut != null ? getShortcutSetting("dxwrapper", this.dxwrapper) : this.dxwrapper;

        if (dxwrapper.contains("dxvk")) {
            String dxwrapperConfig = shortcut != null ? getShortcutSetting("dxwrapperConfig", this.dxwrapperConfig.toString()) : this.dxwrapperConfig.toString();
            KeyValueSet currentDXWrapperConfig = DXVKConfigUtils.parseConfig(dxwrapperConfig);
            String dxvkWrapper = "dxvk-" + currentDXWrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + currentDXWrapperConfig.get("vkd3dVersion");
            String ddrawrapper = currentDXWrapperConfig.get("ddrawrapper");
            Log.i("XServerDisplayActivity", "Launch DX wrapper files selected: dxvk='" +
                    dxvkWrapper + "' vkd3d='" + vkd3dWrapper + "' ddrawrapper='" +
                    ddrawrapper + "'");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper;
        }

        String wincomponents = shortcut != null ? getShortcutSetting("wincomponents", container.getWinComponents()) : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents")) || firstTimeBoot) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        if (!dxwrapper.equals(container.getExtra("dxwrapper")) || firstTimeBoot) {
            extractDXWrapperFiles(dxwrapper);
            container.putExtra("dxwrapper", dxwrapper);
            containerDataChanged = true;
        }

        // Ensure Steam client files are present (download + extract if needed) for Steam games
        boolean isSteamGame = isSteamShortcut();
        boolean isCustomGame = isCustomShortcut();
        boolean launchRealSteamSetup = isRealSteamLaunchEnabledForShortcut();
        boolean coldClientSetup = isColdClientEnabledForShortcut();

        // Point the container's Steam symlink at the correct sidecar:
        //   - Launch Steam Client → .shared/steam-client-store/ (pristine real Steam)
        //   - Cold Client         → .shared/coldclient-store/   (Goldberg stubs + loader)
        //   - Custom Games        → no Steam dir exposed
        // The two stores are completely separate — ColdClient never contaminates Real Steam.
        if (isSteamGame || launchRealSteamSetup) {
            setSteamClientVisibility(true, coldClientSetup);
        } else if (isCustomGame) {
            setSteamClientVisibility(false);
        }

        if (launchRealSteamSetup) {
            Log.d("XServerDisplayActivity", "Ensuring real Steam client is ready...");
            boolean steamReady = false;
            while (!steamReady) {
                steamReady = SteamBridge.ensureSteamReady(this);
                Log.d("XServerDisplayActivity", "Steam client ready: " + steamReady);
                if (!steamReady) {
                    boolean shouldRetry = promptSteamClientDownloadRetry();
                    if (!shouldRetry) {
                        closeLaunchAttempt();
                        return;
                    }
                    preloaderDialog.showOnUiThread("Retrying Steam client download...");
                }
            }

            SteamBridge.ensureRealSteamSupportReady(this);

            // Verify essential Steam client DLLs exist in the wine prefix
            verifySteamClientFiles(false);
        } else if (isSteamGame) {
            Log.d("XServerDisplayActivity", "Real Steam client disabled; preparing ColdClient support only");
            SteamBridge.ensureColdClientSupportReady(this);

            // Verify ColdClient loader/support files without downloading or extracting steam.tzst.
            verifySteamClientFiles(true);
        }

        if (launchRealSteamSetup || isSteamGame) {
            // Replace the game's steam_api DLLs and set up steam_settings for auth
            if (isSteamGame) {
                try {
                    int appId = Integer.parseInt(shortcut.getExtra("app_id"));
                    String gameInstallPath = resolveSteamGameInstallPath(appId);
                    File gameDir = new File(gameInstallPath);
                    String language = container.getExtra("containerLanguage", "english");
                    if (language == null || language.isEmpty()) language = "english";
                    boolean isOfflineMode = shortcut != null
                            ? parseBoolean(getShortcutSetting("steamOfflineMode", container.isSteamOfflineMode() ? "1" : "0"))
                            : container.isSteamOfflineMode();
                    boolean forceDlc = shortcut != null
                            ? parseBoolean(getShortcutSetting("forceDlc", container.isForceDlc() ? "1" : "0"))
                            : container.isForceDlc();
                    boolean useSteamInput = shortcut != null
                            ? parseBoolean(getShortcutSetting("useSteamInput", container.getExtra("useSteamInput", "0")))
                            : parseBoolean(container.getExtra("useSteamInput", "0"));
                    boolean unpackFiles = shortcut != null
                            ? parseBoolean(getShortcutSetting("unpackFiles", container.isUnpackFiles() ? "1" : "0"))
                            : container.isUnpackFiles();
                    boolean runtimePatcher = shortcut != null
                            ? parseBoolean(getShortcutSetting("runtimePatcher", container.isRuntimePatcher() ? "1" : "0"))
                            : container.isRuntimePatcher();

                    // Get encrypted app ticket once for all setup
                    String ticketBase64 = null;
                    try {
                        ticketBase64 = SteamBridge.getEncryptedAppTicketBase64(appId);
                    } catch (Exception e) {
                        Log.w("XServerDisplayActivity", "Failed to get encrypted app ticket", e);
                    }

                    if (gameDir.exists()) {
                        boolean useColdClient = shortcut != null
                                ? parseBoolean(getShortcutSetting("useColdClient", container.isUseColdClient() ? "1" : "0"))
                                : container.isUseColdClient();
                        
                        if (launchRealSteamSetup) {
                            // ── Real Steam Mode ──────────────────────────────────────────────────
                            // The shared Steam store (.shared/steam-client-store/) is permanently
                            // pristine because ColdClient writes to its own sidecar. No need to
                            // undo DLL swaps in the Steam dir — just undo the GAME-side swaps
                            // (steam_api DLL and the exe) that Goldberg/Steamless applied.
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_COLDCLIENT_USED);
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DRM_PATCHED);
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DRM_UNPACK_CHECKED);

                            // Clean up a side-effect of an old "MoveSteamExe" hack: if a game
                            // dir still has a local steam.exe copy, real Steam's integrity
                            // check crashes.
                            File copiedSteamExe = new File(gameInstallPath, "steam.exe");
                            if (copiedSteamExe.exists()) {
                                copiedSteamExe.delete();
                                Log.d("XServerDisplayActivity",
                                        "Real Steam Setup: Purged orphaned local steam.exe from "
                                                + copiedSteamExe.getAbsolutePath());
                            }
                            cleanupEmbeddedSteamRuntime(gameDir);

                            // Undo Goldberg's steam_api replacement in the game dir and restore
                            // the original .exe if Steamless had swapped in an unpacked copy.
                            restoreSteamApiDlls(gameDir);
                            SteamUtils.restoreOriginalExecutable(this, appId);

                            Log.d("XServerDisplayActivity",
                                    "Real Steam Setup: Game-side state restored for appId=" + appId);
                        } else if (useColdClient) {
                            // ── ColdClient launcher mode ──────
                            // ColdClientLoader handles Steam emulation by loading Goldberg's
                            // steamclient.dll/steamclient64.dll. The game's ORIGINAL steam_api.dll
                            // must stay intact — ColdClientLoader routes its calls through the
                            // emulated steamclient.

                            // Remove conflicting Goldberg markers/state
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_RESTORED);

                            // Populate the ColdClient sidecar store on first use. This is fully
                            // decoupled from the pristine Real Steam store — ColdClient's Goldberg
                            // stubs + loader live in .shared/coldclient-store/, not in the real
                            // Steam dir, so switching back to Launch Steam Client later doesn't
                            // need to restore anything.
                            //
                            // The container's Steam symlink was already pointed at the coldclient
                            // store by setSteamClientVisibility(true, coldClientMode=true) above,
                            // so any writes through drive_c/Program Files (x86)/Steam land in the
                            // sidecar automatically.
                            boolean sidecarReady = ensureColdClientStore();
                            if (!sidecarReady) {
                                Log.w("XServerDisplayActivity", "ColdClient sidecar store not ready — loader/Goldberg stubs missing");
                            }

                            // Restore game-side state (steam_api DLLs + exe) once per-prefix
                            boolean coldClientProvisioned =
                                    MarkerUtils.INSTANCE.hasMarker(gameInstallPath, Marker.STEAM_COLDCLIENT_USED);
                            if (!coldClientProvisioned) {
                                SteamUtils.putBackSteamDlls(gameInstallPath);
                                SteamUtils.restoreUnpackedExecutable(this, appId);
                                generateSteamInterfacesForGame(gameDir);
                            } else {
                                Log.d("XServerDisplayActivity", "ColdClient prefix already provisioned for appId=" + appId);
                            }

                            // Per-launch config: picks up any toggles the user changed between launches
                            File steamDir = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
                            steamDir.mkdirs();
                            SteamUtils.writeCompleteSettingsDir(steamDir, appId, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);
                            SteamUtils.enrichSteamSettings(this, appId, new File(steamDir, "steam_settings"));
                            setupSteamSettingsForAllDirs(gameDir, appId, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);

                            // Ensure steamapps/common symlink exists — ColdClientLoader's ExeRunDir depends on it
                            File steamappsDir = new File(steamDir, "steamapps");
                            new File(steamappsDir, "common").mkdirs();
                            WineUtils.ensureSteamappsCommonSymlink(container, gameInstallPath);

                            // Write ColdClientLoader.ini using robust relative exe resolution
                            String relativeExeForIni = resolveRelativeGameExe(appId, gameInstallPath);
                            if (!relativeExeForIni.isEmpty()) {
                                String gameDirNameForIni = new File(gameInstallPath).getName();
                                writeColdClientIniDirect(appId, gameDirNameForIni, relativeExeForIni, runtimePatcher);
                                Log.d("XServerDisplayActivity", "ColdClient INI: exe=" + relativeExeForIni);
                            } else {
                                Log.w("XServerDisplayActivity", "Could not find game exe for ColdClient INI, appId=" + appId);
                            }

                            MarkerUtils.INSTANCE.addMarker(gameInstallPath, Marker.STEAM_COLDCLIENT_USED);
                        } else {
                            // ── Goldberg mode (default) ─────────────────────────
                            // Replace steam_api DLLs with Goldberg/steampipe stubs.
                            // Guard with STEAM_DLL_REPLACED marker so we never double-replace.

                            // Restore ColdClient artifacts if switching from ColdClient mode
                            if (MarkerUtils.INSTANCE.hasMarker(gameInstallPath, Marker.STEAM_COLDCLIENT_USED)) {
                                SteamUtils.restoreSteamclientFiles(this, appId);
                                MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_COLDCLIENT_USED);
                                Log.d("XServerDisplayActivity", "Restored steamclient DLLs from prior ColdClient mode");
                            }

                            if (!MarkerUtils.INSTANCE.hasMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED)) {
                                MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_RESTORED);

                                // Replace steam_api*.dll with steampipe stubs
                                replaceSteamApiDlls(gameDir, gameInstallPath, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);

                                // Restore the appropriate exe based on Legacy DRM toggle.
                                if (unpackFiles) {
                                    SteamUtils.restoreUnpackedExecutable(this, appId);
                                } else {
                                    SteamUtils.restoreOriginalExecutable(this, appId);
                                }

                                // FIX #4: Restore original steamclient*.dll (undo any prior ColdClient injection)
                                SteamUtils.restoreSteamclientFiles(this, appId);

                                // FIX #8: Generate achievements.json and save-location symlinks
                                SteamUtils.enrichSteamSettings(this, appId,
                                        new File(gameInstallPath, "steam_settings"));

                                MarkerUtils.INSTANCE.addMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                                Log.d("XServerDisplayActivity", "Goldberg Steam setup complete for appId=" + appId);
                            } else {
                                // DLLs already replaced; verify they actually exist.
                                // If a game never had steam_api DLLs (e.g. Monster Hunter Stories),
                                // the marker was set but no DLLs were ever placed. Re-run injection.
                                boolean hasSteamApiDll = hasSteamApiDllInTree(gameDir);
                                if (!hasSteamApiDll) {
                                    Log.w("XServerDisplayActivity",
                                            "STEAM_DLL_REPLACED marker set but no steam_api DLL found — clearing marker and re-injecting");
                                    MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                                    replaceSteamApiDlls(gameDir, gameInstallPath, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);
                                    MarkerUtils.INSTANCE.addMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                                } else {
                                    // DLLs genuinely exist; just refresh steam_settings in case ticket changed
                                    setupSteamSettingsForAllDirs(gameDir, appId, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);
                                }
                                // FIX #8: Refresh achievements/save locations too
                                SteamUtils.enrichSteamSettings(this, appId,
                                        new File(gameInstallPath, "steam_settings"));
                                // Ensure experimental steamclient stubs exist (may be missing from older installs)
                                copySteamclientStubs(gameDir);
                                Log.d("XServerDisplayActivity", "Goldberg: refreshed steam_settings for appId=" + appId);
                            }
                        }

                        // Common setup for both modes
                        setupSteamEnvironment(appId, gameDir);

                        // Sync achievements from Goldberg back to Steam (best-effort)
                        SteamUtils.syncGoldbergAchievementsAndStats(this, appId);

                        // Do not clone the full Steam runtime into the game directory for Steam titles.
                        // A copied GameDir/Steam tree can override the intended global Steam root
                        // or per-game Goldberg stubs and break repeated launches, especially on
                        // titles with custom Steam loaders.
                        cleanupEmbeddedSteamRuntime(gameDir);

                        Log.d("XServerDisplayActivity", "Steam environment physical readiness verified for appId=" + appId);
                    }
                } catch (Exception e) {
                    Log.e("XServerDisplayActivity", "Failed to set up Steam environment", e);
                }
            }
        }
        // Custom Games run unmodified — no Steam directory, no ini cleanup, no DLL/settings
        // injection. Anything the user's patch (Goldberg, OnlineFix, CreamAPI, etc.) ships
        // inside the game directory is its own and stays intact.

        String desktopTheme = shortcut != null ? getShortcutSetting("desktopTheme", container.getDesktopTheme()) : container.getDesktopTheme();
        if (!(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container, getActiveGameDirectoryPath(), isSteamShortcut());

        int inputType = container.getInputType();
        if (shortcut != null) {
            String shortcutInputType = shortcut.getExtra("inputType");
            if (!shortcutInputType.isEmpty()) {
                inputType = Byte.parseByte(shortcutInputType);
            }
        }
        boolean dinputEnabled = (inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT;
        boolean exclusiveXInput = false;
        if (shortcut != null) {
            String extra = shortcut.getExtra("exclusiveXInput");
            if (!extra.isEmpty()) {
                exclusiveXInput = extra.equals("1");
            }
        }
        // Proton-version flips repair controller detection because they rebuild the prefix and
        // restore controller DllOverrides. Keep that correction narrow and explicit here.
        WineUtils.ensureControllerDllOverrides(container);
        WineUtils.setJoystickRegistryKeys(container, dinputEnabled, exclusiveXInput);
        WineUtils.ensureWinebusConfig(container);

        if (shortcut != null)
            startupSelection = getShortcutSetting("startupSelection", String.valueOf(container.getStartupSelection()));
        else
            startupSelection = String.valueOf(container.getStartupSelection());

        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, startupSelection);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }
        if (containerDataChanged) {
            Log.d("XServerDisplayActivity", "Saving container data id=" + container.id +
                    " dxwrapperConfigField='" + container.getDXWrapperConfig() +
                    "' dxwrapperExtra='" + container.getExtra("dxwrapper") + "'");
            container.saveData();
        }
        Log.d("ContainerLaunch", "=== setupWineSystemFiles END === container=" + container.id + " firstTimeBoot=" + firstTimeBoot);
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {
        cleanupLingeringSessionProcesses("new launch");

        // Set environment variables
        envVars.put("LC_ALL", lc_all);
        String winePrefix = (shortcut != null && container != null && shortcut.path != null && shortcut.path.matches("^[cC]:.*")) ? new File(container.getRootDir(), ".wine").getAbsolutePath() : imageFs.wineprefix;
        envVars.put("WINEPREFIX", winePrefix);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS);
        String wineDebugValue;
        if (enableWineDebug && !wineDebugChannels.isEmpty()) {
            wineDebugValue = "+" + wineDebugChannels.replace(",", ",+");
        } else {
            wineDebugValue = "-all";
        }
        envVars.put("WINEDEBUG", wineDebugValue);

        // Clear any temporary directory
        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());


        guestProgramLauncherComponent = new GuestProgramLauncherComponent(
                contentsManager,
                contentsManager.getProfileByEntryName(wineVersion),
                shortcut
        );

        // Additional container checks and environment configuration
        if (container != null) {
                if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {
                    winHandler.killProcess("services.exe");
                }
                guestProgramLauncherComponent.setContainer(this.container);
                guestProgramLauncherComponent.setWineInfo(this.wineInfo);

                // P1: Wire steamType for box64rc preset selection (Normal/Light/Ultralight)
                String steamType = isSteamShortcut()
                        ? (shortcut != null ? getShortcutSetting("steamType", container.getSteamType()) : container.getSteamType())
                        : Container.STEAM_TYPE_NORMAL;
                guestProgramLauncherComponent.setSteamType(steamType);
                GameFixes.applyForLaunch(container, shortcut);

                String wineStartCmd = getWineStartCommand(guestProgramLauncherComponent);
                String guestExecutable;
            
            // Use wine explorer for all containers - GuestProgramLauncherComponent handles
            // the architecture difference (winePath for arm64ec, box64 for x86_64)
            guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + wineStartCmd;

            Log.d("XServerDisplayActivity", "=== GAME LAUNCH DEBUG ===");
            Log.d("XServerDisplayActivity", "Wine start command: " + wineStartCmd);
            Log.d("XServerDisplayActivity", "Full guest executable: " + guestExecutable);
            Log.d("XServerDisplayActivity", "Wine info: " + wineInfo.identifier() + " arch=" + wineInfo.getArch());
            Log.d("XServerDisplayActivity", "Container drives: " + container.getDrives());
            if (shortcut != null) {
                Log.d("XServerDisplayActivity", "Shortcut path: " + shortcut.path);
                Log.d("XServerDisplayActivity", "Shortcut game_source: " + shortcut.getExtra("game_source"));
                Log.d("XServerDisplayActivity", "Shortcut app_id: " + shortcut.getExtra("app_id"));
            }

            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            String rawShortcutEnvVars = (shortcut != null && !shortcutUsesContainerDefaults())
                    ? shortcut.getExtra("envVars") : "";
            String effectiveCustomEnvVars = shortcut != null
                    ? getShortcutSetting("envVars", container.getEnvVars())
                    : container.getEnvVars();
            Log.d("XServerDisplayActivity", "Custom envVars source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutEnvVars + "' container='" + container.getEnvVars() +
                    "' effective='" + effectiveCustomEnvVars + "'");
            envVars.putAll(effectiveCustomEnvVars);

            // Normalize synchronization environment variables (NTSync / ESync).
            // This auto-detects NTSync and falls back to ESync when unavailable.
            normalizeSyncEnvVars(envVars);

            ArrayList<String> bindingPaths = new ArrayList<>();
            String drives = shortcut != null ? getShortcutSetting("drives", container.getDrives()) : container.getDrives();
            for (String[] drive : Container.drivesIterator(drives)) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            String rawShortcutBox64Preset = (shortcut != null && !shortcutUsesContainerDefaults())
                    ? shortcut.getExtra("box64Preset") : "";
            String effectiveBox64Preset = shortcut != null
                    ? getShortcutSetting("box64Preset", container.getBox64Preset())
                    : container.getBox64Preset();
            Log.d("XServerDisplayActivity", "Box64 preset source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutBox64Preset + "' container='" + container.getBox64Preset() +
                    "' effective='" + effectiveBox64Preset + "'");
            guestProgramLauncherComponent.setBox64Preset(effectiveBox64Preset);

            String rawShortcutFEXCorePreset = (shortcut != null && !shortcutUsesContainerDefaults())
                    ? shortcut.getExtra("fexcorePreset") : "";
            String effectiveFEXCorePreset = shortcut != null
                    ? getShortcutSetting("fexcorePreset", container.getFEXCorePreset())
                    : container.getFEXCorePreset();
            Log.d("XServerDisplayActivity", "FEXCore preset source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutFEXCorePreset + "' container='" + container.getFEXCorePreset() +
                    "' effective='" + effectiveFEXCorePreset + "'");
            guestProgramLauncherComponent.setFEXCorePreset(effectiveFEXCorePreset);

                // P2: Wire preUnpack callback for Mono, redistributables, and Steamless DRM
                // This runs after box64/Wine is ready but before the game exe launches.
                // Always wired for Steam games so per-container prerequisites install correctly.
                boolean isSteamGameForUnpack = shortcut != null && "STEAM".equals(shortcut.getExtra("game_source"));
                if (isSteamGameForUnpack) {
                    guestProgramLauncherComponent.setPreUnpack(() -> {
                        try {
                            boolean currentLaunchRealSteam = shortcut != null
                                    ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                                    : container.isLaunchRealSteam();
                            if (currentLaunchRealSteam) {
                                return;
                            }

                            boolean currentUnpackFiles = shortcut != null
                                    ? parseBoolean(getShortcutSetting("unpackFiles", container.isUnpackFiles() ? "1" : "0"))
                                    : container.isUnpackFiles();
                            runPreGameSetup(
                                    guestProgramLauncherComponent,
                                    container.isNeedsUnpacking(),
                                    currentUnpackFiles,
                                    false);
                        } catch (Exception e) {
                            Log.e("XServerDisplayActivity", "preUnpack failed", e);
                        }
                    });
                }
        }

        // Merge overrideEnvVars if present
        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars.clear(); // Clear overrideEnvVars as per smali logic
        }

        // Create our overall XEnvironment with various components
        environment = new XEnvironment(this, imageFs);
        environment.addComponent(
                new SysVSharedMemoryComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)
                )
        );
        environment.addComponent(
                new XServerComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)
                )
        );

        // Audio driver logic
        if (audioDriver.equals("alsa")) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", "true");
            environment.addComponent(
                    new ALSAServerComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH),
                            ALSAClient.Options.fromEnvVars(envVars)
                    )
            );
        } else if (audioDriver.equals("pulseaudio")) {
            envVars.put("PULSE_SERVER", rootPath + UnixSocketConfig.PULSE_SERVER_PATH);
            environment.addComponent(
                    new PulseAudioComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)
                    )
            );
        }

        // Add Steam client component for Steam games (Goldberg emulator support)
        boolean launchRealSteamMode = shortcut != null
                ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                : (container != null && container.isLaunchRealSteam());
        if (shortcut != null && "STEAM".equals(shortcut.getExtra("game_source")) && !launchRealSteamMode) {
            Log.d("XServerDisplayActivity", "Adding SteamClientComponent for Steam game");
            environment.addComponent(new SteamClientComponent());
        }

        // Pass final envVars to the launcher
        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> {
            Log.d("XServerDisplayActivity", "Guest process terminated with status: " + status);

            // Keep A:\Steam persistence for Android 16 testing
            // User expressly requested: "don't remove the A:\Steam\ Folder unless the next game has the toggle off to not move it."
            // Removed MoveSteamExe cleanup hook from termination callback.

            if (shouldWatchSteamTermination(status)) {
                return;
            }

            exit();
        });

        // Add the launcher to our environment
        environment.addComponent(guestProgramLauncherComponent);

        FEXCoreManager.ensureAppConfigOverrides(this);

        // Set up Steam token login for real Steam mode (must be after guestProgramLauncherComponent is added)
        if (container != null && launchRealSteamMode) {
            try {
                String steamId64 = String.valueOf(com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE.getSteamUserSteamId64());
                String username = com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE.getUsername();
                String refreshToken = com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE.getRefreshToken();
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    com.winlator.cmod.feature.stores.steam.utils.SteamTokenLogin tokenLogin =
                            new com.winlator.cmod.feature.stores.steam.utils.SteamTokenLogin(
                                    steamId64, username, refreshToken, imageFs, guestProgramLauncherComponent);
                    tokenLogin.setupSteamFiles(true);
                    Log.d("XServerDisplayActivity", "SteamTokenLogin set up for real Steam mode");
                }
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to set up SteamTokenLogin", e);
            }
        }

        // Reserve controller slots before Wine starts so connected pads are
        // visible immediately without waiting for a first input event.
        winHandler.preAssignConnectedControllers();

        // Start all environment components (XServer, Audio, etc.)
        environment.startEnvironmentComponents();

        // Start the WinHandler
        winHandler.start();

        if (wineRequestHandler != null) wineRequestHandler.start();

        // Reset dxwrapper config
        dxwrapperConfig = null;
        
    }

    private void createWrapperScript(String path, String content) {
        File scriptFile = new File(path);
        FileUtils.writeString(scriptFile, content);
        scriptFile.setExecutable(true);
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        final GLRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);
        renderer.setNativeMode(isNativeRenderingEnabled);

        if (shortcut != null) {
            renderer.setUnviewableWMClasses("explorer.exe");
            String savedFpsLimit = shortcut.getExtra("fpsLimit", "0");
            try {
                runtimeFpsLimit = Integer.parseInt(savedFpsLimit);
                renderer.setFpsLimit(runtimeFpsLimit);
            } catch (NumberFormatException e) {
                runtimeFpsLimit = 0;
            }
        }

        applyScreenEffects();
        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        touchpadView = new TouchpadView(this, xServer, timeoutHandler, hideControlsRunnable);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMouseEnabled(!isMouseDisabled);
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                renderDrawerMenu();
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this, timeoutHandler, hideControlsRunnable);
        inputControlsView.setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
        applyTouchscreenOverlayPreference();
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);


        // FPS monitor is a global sticky preference - persists across all games/containers
        effectiveShowFPS = preferences.getBoolean("fps_monitor_enabled", false);
        if (effectiveShowFPS) {
            frameRating = new FrameRating(this, graphicsDriverConfig);
            frameRating.setRenderer(lastRendererName);
            if (lastGpuName != null) frameRating.setGpuName(lastGpuName);
            frameRating.setVisibility(View.VISIBLE);
            applyHUDSettings();
            updateHUDRenderMode();
            rootView.addView(frameRating);
        }

        // Use getShortcutSetting for proper fallback to container defaults
        boolean shouldStretch = "1".equals(getShortcutSetting("fullscreenStretched",
                container != null && container.isFullscreenStretched() ? "1" : "0"));

        if (shouldStretch) {
            // Toggle fullscreen mode based on the final decision
            renderer.toggleFullscreen();
            touchpadView.toggleFullscreen();
        }

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null) showInputControls(profile);
            }

            String simTouchScreen = shortcut.getExtra("simTouchScreen");
            touchpadView.setSimTouchScreen(simTouchScreen.equals("1"));
        }

        startTouchscreenTimeout();

        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);
    }



    private ActivityResultLauncher<Intent> controlsEditorActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (editInputControlsCallback != null) {
                    editInputControlsCallback.run();
                    editInputControlsCallback = null;
                }
            }
    );

    private String parseShortcutNameFromDesktopFile(File desktopFile) {
        String shortcutName = "";
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Name=")) {
                        shortcutName = line.split("=")[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("XServerDisplayActivity", "Error reading shortcut name from .desktop file", e);
            }
        }
        return shortcutName;
    }

    private void setTextColorForDialog(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                // If the child is a ViewGroup, recursively apply the color
                setTextColorForDialog((ViewGroup) child, color);
            } else if (child instanceof TextView) {
                // If the child is a TextView, set its text color
                ((TextView) child).setTextColor(color);
            }
        }
    }

    private void ensureWinePrefixReady() {
        if (container == null || wineInfo == null) return;

        File containerDir = container.getRootDir();
        boolean prefixInvalid = !WineUtils.isPrefixValid(containerDir);
        String storedPrefixArch = container.getExtra("wineprefixArch");
        boolean archMismatch = !storedPrefixArch.isEmpty() && !storedPrefixArch.equalsIgnoreCase(wineInfo.getArch());
        boolean prefixNeedsUpdate = "t".equalsIgnoreCase(container.getExtra("wineprefixNeedsUpdate"));
        Log.d("ContainerLaunch", "ensureWinePrefixReady: prefixInvalid=" + prefixInvalid +
                " archMismatch=" + archMismatch + " storedArch=" + storedPrefixArch +
                " targetArch=" + wineInfo.getArch() + " needsUpdate=" + prefixNeedsUpdate);

        if (!prefixInvalid && !archMismatch && !prefixNeedsUpdate) {
            if (storedPrefixArch.isEmpty()) {
                container.putExtra("wineprefixArch", wineInfo.getArch());
                container.putExtra("wineprefixNeedsUpdate", null);
                container.saveData();
            }
            return;
        }

        Log.w("XServerDisplayActivity", "Repairing Wine prefix for container " + container.id +
                " invalid=" + prefixInvalid +
                " archMismatch=" + archMismatch +
                " storedArch=" + storedPrefixArch +
                " targetArch=" + wineInfo.getArch() +
                " needsUpdate=" + prefixNeedsUpdate);

        boolean repaired = containerManager.repairContainerWinePrefix(container, wineVersion, contentsManager, onExtractFileListener);
        if (repaired) {
            firstTimeBoot = true;
            Log.i("XServerDisplayActivity", "Wine prefix repaired successfully for container " + container.id);
        } else {
            Log.e("XServerDisplayActivity", "Wine prefix repair failed for container " + container.id);
        }
    }

    private void ensureWinePrefixEssentialFiles() {
        if (container == null) return;
        File containerWindowsDir = new File(container.getRootDir(), ".wine/drive_c/windows");
        String[] essentialFiles = {"winhandler.exe", "wfm.exe"};

        StringBuilder status = new StringBuilder("ensureWinePrefixEssentialFiles:");
        boolean anyMissing = false;
        for (String filename : essentialFiles) {
            boolean exists = new File(containerWindowsDir, filename).exists();
            status.append(" ").append(filename).append("=").append(exists);
            if (!exists) anyMissing = true;
        }
        Log.d("ContainerLaunch", status.toString());

        if (anyMissing) {
            // Try to find the files from another container that has them
            File homeDir = new File(imageFs.getRootDir(), "home");
            File[] homeDirs = homeDir.listFiles();
            File sourceWindowsDir = null;
            if (homeDirs != null) {
                Log.d("ContainerLaunch", "Searching " + homeDirs.length + " dirs in home/ for essential files");
                for (File dir : homeDirs) {
                    if (!dir.isDirectory()) continue;
                    // Skip the active xuser symlink and the current container itself
                    if (dir.getName().equals(ImageFs.USER)) continue;
                    if (dir.getAbsolutePath().equals(container.getRootDir().getAbsolutePath())) continue;
                    File candidate = new File(dir, ".wine/drive_c/windows");
                    if (new File(candidate, "winhandler.exe").exists()) {
                        sourceWindowsDir = candidate;
                        Log.d("ContainerLaunch", "Found essential files source: " + dir.getName());
                        break;
                    }
                }
            }

            if (sourceWindowsDir != null) {
                for (String filename : essentialFiles) {
                    File dest = new File(containerWindowsDir, filename);
                    if (!dest.exists()) {
                        File source = new File(sourceWindowsDir, filename);
                        if (source.exists()) {
                            Log.d("ContainerLaunch", "Copying " + filename + " from " + sourceWindowsDir.getParent());
                            FileUtils.copy(source, dest);
                        }
                    }
                }
            } else {
                // No other container has the files — extract from container_pattern_common.tzst
                Log.w("ContainerLaunch", "No source container found, extracting from container_pattern_common.tzst");
                containerWindowsDir.mkdirs();
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                        "container_pattern_common.tzst", imageFs.getRootDir(), onExtractFileListener);
                for (String filename : essentialFiles) {
                    Log.d("ContainerLaunch", filename + " exists after extraction: " + new File(containerWindowsDir, filename).exists());
                }
            }
        }
    }

    private boolean ensureRequestedWineVersionInstalled() {
        if (SetupWizardActivity.isWineVersionInstalled(this, wineVersion)) {
            return true;
        }
        Log.e("XServerDisplayActivity", "Requested Wine/Proton is not installed: " + wineVersion);
        SetupWizardActivity.promptToInstallWineOrCreateContainer(this, wineVersion);
        finish();
        return false;
    }

    private boolean promptSteamClientDownloadRetry() {
        final CountDownLatch dialogLatch = new CountDownLatch(1);
        final boolean[] retry = {false};

        runOnUiThread(() -> {
            if (preloaderDialog != null && preloaderDialog.isShowing()) {
                preloaderDialog.close();
            }
            SteamClientDownloadFailureDialog.show(
                    this,
                    "Steam Client Download Failed",
                    "WinNative couldn't download the Steam client files needed for this launch.\n\nRetry will try the download again. Close will cancel this launch and return to your library.",
                    () -> {
                        retry[0] = true;
                        dialogLatch.countDown();
                    },
                    () -> {
                        retry[0] = false;
                        dialogLatch.countDown();
                    }
            );
        });

        try {
            dialogLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return retry[0];
    }

    private void closeLaunchAttempt() {
        runOnUiThread(() -> {
            if (preloaderDialog != null && preloaderDialog.isShowing()) {
                preloaderDialog.close();
            }
            if (launchedFromPinnedShortcut) {
                AppTerminationHelper.exitApplication(this, "shortcut_launch_cancelled");
                return;
            }
            Intent intent = new Intent(this, UnifiedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }



    private void showInputControlsDialog() {
        final InputControlsDialog dialog = new InputControlsDialog(this);

        // Load profile list
        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- "+getString(R.string.common_ui_disabled)+" --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                    selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }
            dialog.getProfileNames().setValue(profileItems);
            dialog.getSelectedProfileIndex().setIntValue(selectedPosition);
        };
        loadProfileSpinner.run();

        // Initialize checkbox states
        dialog.getShowTouchscreenControls().setValue(preferences.getBoolean("show_touchscreen_controls_enabled", false));
        dialog.getOverlayOpacity().setValue(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        dialog.getTouchscreenHaptics().setValue(preferences.getBoolean("touchscreen_haptics_enabled", false));
        dialog.getGamepadVibration().setValue(preferences.getBoolean(ControllerManager.PREF_VIBRATION_GLOBAL, true));

        final Runnable updateProfile = () -> {
            int position = dialog.getSelectedProfileIndex().getIntValue();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles(true).get(position - 1));
            }
            else hideInputControls();
        };

        // Settings button callback
        dialog.setOnSettingsClickCallback(() -> {
            int position = dialog.getSelectedProfileIndex().getIntValue();
            Intent intent = new Intent(this, UnifiedActivity.class);
            intent.putExtra("edit_input_controls", true);
            intent.putExtra("selected_profile_id", position > 0 ? inputControlsManager.getProfiles(true).get(position - 1).id : 0);
            editInputControlsCallback = () -> {
                hideInputControls();
                inputControlsManager.loadProfiles(true);
                loadProfileSpinner.run();
                updateProfile.run();
            };
            controlsEditorActivityResultLauncher.launch(intent);
        });

        // Confirm callback
        dialog.setOnConfirmCallback(() -> {
            inputControlsView.setShowTouchscreenControls(dialog.getShowTouchscreenControls().getValue());
            float overlayOpacity = dialog.getOverlayOpacity().getValue();
            inputControlsView.setOverlayOpacity(overlayOpacity);
            boolean isHapticsEnabled = dialog.getTouchscreenHaptics().getValue();
            boolean isGamepadVibrationEnabled = dialog.getGamepadVibration().getValue();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("show_touchscreen_controls_enabled", dialog.getShowTouchscreenControls().getValue());
            editor.putFloat("overlay_opacity", overlayOpacity);
            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
            editor.putBoolean(ControllerManager.PREF_VIBRATION_GLOBAL, isGamepadVibrationEnabled);
            editor.apply();
            if (winHandler != null) {
                winHandler.setGlobalVibrationEnabled(isGamepadVibrationEnabled);
            }
            touchpadView.setOnTouchListener(null);
            updateProfile.run();
        });

        dialog.setOnCancelCallback(updateProfile::run);

        dialog.show();
    }

    private ControlsProfile findFirstVirtualProfile() {
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        for (ControlsProfile profile : profiles) {
            if (profile != null && profile.isVirtualGamepad()) return profile;
        }
        return null;
    }

    private boolean hasActiveTouchscreenProfile() {
        return inputControlsView != null && inputControlsView.getProfile() != null;
    }

    private void applyTouchscreenOverlayPreference() {
        if (inputControlsView == null || touchpadView == null) return;

        boolean showTouchscreenControls =
                preferences.getBoolean("show_touchscreen_controls_enabled", false);
        inputControlsView.setShowTouchscreenControls(showTouchscreenControls);
    }

    private void persistSelectedProfile(ControlsProfile profile) {
        SharedPreferences.Editor editor = preferences.edit();
        if (profile != null) {
            int selectedProfileIndex = -1;
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile storedProfile = profiles.get(i);
                if (storedProfile != null && storedProfile.id == profile.id) {
                    selectedProfileIndex = i;
                    break;
                }
            }
            editor.putInt("selected_profile_id", profile.id);
            editor.putInt("selected_profile_index", selectedProfileIndex);
        } else {
            editor.remove("selected_profile_id");
            editor.putInt("selected_profile_index", -1);
        }
        editor.apply();
    }

    private ControlsProfile resolvePreferredStartupProfile() {
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        int selectedProfileId = preferences.getInt("selected_profile_id", 0);
        int selectedProfileIndex = preferences.getInt("selected_profile_index", -1);
        ControlsProfile selectedProfile =
                selectedProfileId != 0 ? inputControlsManager.getProfile(selectedProfileId) : null;

        if (selectedProfile == null
                && selectedProfileIndex >= 0
                && selectedProfileIndex < profiles.size()) {
            selectedProfile = profiles.get(selectedProfileIndex);
        }

        if (selectedProfile != null) {
            Log.d(
                    "XServerDisplayActivity",
                    "Resolved startup profile="
                            + selectedProfile.getName()
                            + " id="
                            + selectedProfile.id
                            + " virtual="
                            + selectedProfile.isVirtualGamepad());
        }
        return selectedProfile;
    }

    private void simulateConfirmInputControlsDialog() {
        // Simulate setting the relative mouse movement and touchscreen controls from preferences

        boolean isShowTouchscreenControls = preferences.getBoolean("show_touchscreen_controls_enabled", false); // default is false (hidden)
        inputControlsView.setShowTouchscreenControls(isShowTouchscreenControls);

        boolean isHapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", false);

        // Apply these settings as if the user confirmed the dialog
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
        editor.apply();

        ControlsProfile startupProfile = resolvePreferredStartupProfile();
        if (startupProfile != null) showInputControls(startupProfile);
        else hideInputControls();

        startTouchscreenTimeout();

        Log.d("XServerDisplayActivity", "Input controls simulated confirmation executed. startupProfile=" + (startupProfile != null ? startupProfile.getName() : "none"));

        controllerAutoSwitchRunnable = null;
    }

    private void startTouchscreenTimeout() {
        if (inputControlsView == null || touchpadView == null) return;
        touchpadView.setOnTouchListener(null);
        if (hasActiveTouchscreenProfile()) {
            inputControlsView.setVisibility(View.VISIBLE);
        }
    }

    private void showInputControls(ControlsProfile profile) {
        if (profile == null) {
            hideInputControls();
            return;
        }
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();
        inputControlsView.setProfile(profile);
        applyTouchscreenOverlayPreference();
        persistSelectedProfile(profile);
        Log.d("XServerDisplayActivity", "showInputControls: profile=" + profile.getName() + " id=" + profile.id + " virtual=" + profile.isVirtualGamepad());

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
        if (winHandler != null) {
            winHandler.sendGamepadState();
        }
        startTouchscreenTimeout();
    }

    private void hideInputControls() {
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);
        applyTouchscreenOverlayPreference();
        persistSelectedProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
        if (winHandler != null) {
            winHandler.sendGamepadState();
        }
        startTouchscreenTimeout();
    }

    private void extractGraphicsDriverFiles() {
        String adrenoToolsDriverId = graphicsDriverConfig.get("version");
        Log.i("GraphicsDriverExtraction", "Launch graphics driver selected: graphicsDriver='" +
                graphicsDriver + "' driverId='" + adrenoToolsDriverId + "'");

        // Re-apply refresh rate now that shortcut is loaded (per-game override may apply)
        applyPreferredRefreshRate();

        File rootDir = imageFs.getRootDir();

        if (dxwrapper.contains("dxvk")) {
            int refreshRateOverride = getDxvkFrameRateOverride();
            DXVKConfigUtils.setEnvVars(this, dxwrapperConfig, envVars, refreshRateOverride);
            String version = dxwrapperConfig.get("version");
            if (version.equals("1.11.1-sarek")) {
                Log.d("GraphicsDriverExtraction", "Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass");
                envVars.put("WRAPPER_NO_PATCH_OPCONSTCOMP", "1");
            }
        }
        else {
            WineD3DConfigUtils.setEnvVars(this, dxwrapperConfig, envVars);
        }

        envVars.put("GALLIUM_DRIVER", "zink");
        envVars.put("LIBGL_KOPPER_DISABLE", "true");

        if (firstTimeBoot) {
            Log.d("XServerDisplayActivity", "First time container boot, re-extracting libs");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/wrapper" + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "layers" + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/extra_libs" + ".tzst", rootDir);
            if (wineInfo != null && wineInfo.isArm64EC() && !GPUInformation.getRenderer(null, null).contains("Mali")) {
                try {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/zink_dlls.tzst", new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows"));
                } catch (Exception e) {
                    Log.w("XServerDisplayActivity", "zink_dlls.tzst not found or extraction failed", e);
                }
            }
        }

        if (adrenoToolsDriverId != null && !adrenoToolsDriverId.isEmpty()
                && !adrenoToolsDriverId.equals("System")) {
            AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
            String driverDisplayName = adrenotoolsManager.getDriverName(adrenoToolsDriverId);
            String driverVersion = adrenotoolsManager.getDriverVersion(adrenoToolsDriverId);
            String driverLibrary = adrenotoolsManager.getLibraryName(adrenoToolsDriverId);
            Log.i("GraphicsDriverExtraction", "Loading graphics/Turnip driver: id='" +
                    adrenoToolsDriverId + "' name='" + driverDisplayName +
                    "' version='" + driverVersion + "' library='" + driverLibrary + "'");
            adrenotoolsManager.setDriverById(envVars, imageFs, adrenoToolsDriverId);
            Log.i("GraphicsDriverExtraction", "Loaded graphics/Turnip driver env: id='" +
                    adrenoToolsDriverId + "' path=" +
                    envVars.get("ADRENOTOOLS_DRIVER_PATH") + " name=" +
                    envVars.get("ADRENOTOOLS_DRIVER_NAME") + " hooks=" +
                    envVars.get("ADRENOTOOLS_HOOKS_PATH"));
        } else {
            String gameSource = (shortcut != null) ? shortcut.getExtra("game_source") : "";
            Log.w("GraphicsDriverExtraction", "No Adrenotools driver applied (id='"
                    + adrenoToolsDriverId + "' graphicsDriver='" + graphicsDriver
                    + "' gameSource='" + gameSource + "') - system Vulkan driver will be used");
        }

        envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/wrapper_icd.aarch64.json");

        String vulkanVersion = graphicsDriverConfig.get("vulkanVersion");
        if (vulkanVersion == null) vulkanVersion = "1.3";
        try {
            String fullVkVersion = GPUInformation.getVulkanVersion(adrenoToolsDriverId, this);
            if (fullVkVersion != null && fullVkVersion.contains(".")) {
                String[] parts = fullVkVersion.split("\\.");
                if (parts.length >= 3) {
                    vulkanVersion = vulkanVersion + "." + parts[2];
                }
            }
        } catch (Throwable e) {
            Log.w("GraphicsDriverExtraction", "Error getting Vulkan version patch", e);
        }
        envVars.put("WRAPPER_VK_VERSION", vulkanVersion);

        String blacklistedExtensions = graphicsDriverConfig.get("blacklistedExtensions");
        envVars.put("WRAPPER_EXTENSION_BLACKLIST", blacklistedExtensions);

        String gpuName = graphicsDriverConfig.get("gpuName");
        String dxvkVersion = dxwrapperConfig.get("version");
        if (gpuName != null && !gpuName.equals("Device") && dxvkVersion != null && !dxvkVersion.equals("1.11.1-sarek")) {
            envVars.put("WRAPPER_DEVICE_NAME", gpuName);
            envVars.put("WRAPPER_DEVICE_ID", WineD3DConfigUtils.getDeviceIdFromGPUName(this, gpuName));
            envVars.put("WRAPPER_VENDOR_ID", WineD3DConfigUtils.getVendorIdFromGPUName(this, gpuName));
        }

        String maxDeviceMemory = graphicsDriverConfig.get("maxDeviceMemory");
        if (maxDeviceMemory != null && Integer.parseInt(maxDeviceMemory) > 0)
            envVars.put("WRAPPER_VMEM_MAX_SIZE", maxDeviceMemory);

        String presentMode = graphicsDriverConfig.get("presentMode");
        if (presentMode == null || presentMode.isEmpty()) presentMode = "mailbox";
        if (presentMode.contains("immediate")) {
            envVars.put("WRAPPER_MAX_IMAGE_COUNT", "1");
        }
        envVars.put("MESA_VK_WSI_PRESENT_MODE", presentMode);

        String resourceType = graphicsDriverConfig.get("resourceType");
        envVars.put("WRAPPER_RESOURCE_TYPE", resourceType);

        ArrayList<String> wsiDebugFlags = new ArrayList<>();
        if (!isNativeRenderingEnabled) {
            wsiDebugFlags.add("sw");
            envVars.put("LIBGL_DRI3_DISABLE", "1");
        }
        String syncFrame = graphicsDriverConfig.get("syncFrame");
        if ("1".equals(syncFrame)) {
            wsiDebugFlags.add("forcesync");
        }
        if (!wsiDebugFlags.isEmpty()) {
            envVars.put("MESA_VK_WSI_DEBUG", String.join(",", wsiDebugFlags));
        }
        Log.d("NativeRendering", "use_dri3=" + isNativeRenderingEnabled + " MESA_VK_WSI_DEBUG=" + envVars.get("MESA_VK_WSI_DEBUG"));

        String disablePresentWait = graphicsDriverConfig.get("disablePresentWait");
        envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", disablePresentWait);

        String bcnEmulation = graphicsDriverConfig.get("bcnEmulation");
        String bcnEmulationType = graphicsDriverConfig.get("bcnEmulationType");

        switch (bcnEmulation) {
            case "auto" -> {
                if ("compute".equals(bcnEmulationType)) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "1");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "3");
            }
            case "full" -> {
                if ("compute".equals(bcnEmulationType)) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "0");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "2");
            }
            case "none" -> envVars.put("WRAPPER_EMULATE_BCN", "0");
            default -> envVars.put("WRAPPER_EMULATE_BCN", "1");
        }

        String bcnEmulationCache = graphicsDriverConfig.get("bcnEmulationCache");
        envVars.put("WRAPPER_USE_BCN_CACHE", bcnEmulationCache);

    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        boolean handledByWinHandler = false;
        boolean handledByTouchpadView = false;

        if (isPointerMotionEvent(event) && touchpadView != null) {
            handledByTouchpadView = touchpadView.onExternalMouseEvent(event);
        }

        if (handledByTouchpadView) {
            return true;
        }

        if (isControllerMotionEvent(event)) {
            cancelMousePointerTimeout();
            if (touchpadView != null) {
                touchpadView.cancelMousePointerTimeout();
            }
            if (winHandler != null) {
                handledByWinHandler = winHandler.onGenericMotionEvent(event);
            }
            if (inputControlsView != null) {
                inputControlsView.onGenericMotionEvent(event);
            }
            if (handledByWinHandler) return true;
        }

        // Pass the event to the super method to ensure system-level handling
        boolean handledBySuper = super.dispatchGenericMotionEvent(event);

        // Combine the results: any handler consuming the event indicates it was handled
        return handledByWinHandler || handledByTouchpadView || handledBySuper;
    }


    private static final int RECAPTURE_DELAY_MS = 10000; // 10 seconds

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (ExternalController.isGameController(event.getDevice())) {
            cancelMousePointerTimeout();
            if (touchpadView != null) {
                touchpadView.cancelMousePointerTimeout();
            }
        }

        boolean handled = inputControlsView.onKeyEvent(event);
        if (winHandler != null) {
            handled |= winHandler.onKeyEvent(event);
        }
        if (xServer != null && xServer.keyboard != null) {
            handled |= xServer.keyboard.onKeyEvent(event);
        }

        if (handled) return true;

        if (event.getAction() == KeyEvent.ACTION_DOWN &&
                (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE ||
                 event.getKeyCode() == KeyEvent.KEYCODE_HOME ||
                 event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    private static final String TAG = "DXWrapperExtraction";

    private void extractDXWrapperFiles(String dxwrapper) {
        final String[] dlls = {"d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll", "d3d8.dll", "d3d9.dll", "dxgi.dll", "ddraw.dll", "d3dimm.dll"};

        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");

        if (dxwrapper.contains("dxvk")) {
            Log.d(TAG, "Extracting DXVK wrapper files, version: " + dxwrapper);

            String dxvkWrapper = dxwrapper.split(";")[0];
            String vkd3dWrapper = dxwrapper.split(";")[1];
            String ddrawrapper = dxwrapper.split(";")[2];
            
            ContentProfile dxvkProfile = contentsManager.getProfileByEntryName(dxvkWrapper);
            if (dxvkProfile != null) {
                Log.d(TAG, "Applying user-defined DXVK content profile: " + dxvkWrapper);
                contentsManager.applyContent(dxvkProfile);
                extractD8VKIfNeeded(dxvkWrapper, windowsDir);
            } else {
                Log.w(TAG, "DXVK content profile not installed; no bundled DXVK archive will be loaded: " + dxvkWrapper);
            }

            if (vkd3dWrapper.contains("None")) {
                Log.i(TAG, "Launch VKD3D selected: None; restoring original d3d12");
                restoreOriginalDllFiles(new String[]{"d3d12.dll", "d3d12core.dll"});
            }
            else {
                ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
                if (vkd3dProfile != null) {
                    Log.i(TAG, "Loading VKD3D content profile: " + vkd3dWrapper);
                    contentsManager.applyContent(vkd3dProfile);
                } else {
                    Log.w(TAG, "VKD3D content profile not installed; no bundled VKD3D archive will be loaded: " + vkd3dWrapper);
                }
            }

            Log.d(TAG, "Extracting nglide wrapper");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir, onExtractFileListener);

            if (ddrawrapper.equalsIgnoreCase("none") || ddrawrapper.contains("None")) {
                Log.d(TAG, "No DDRaw wrapper has been selected, restoring original ddraw files");
                restoreOriginalDllFiles(new String[]{ "ddraw.dll", "d3dimm.dll" });
            }
            else {
                if (ddrawrapper.equals("cnc-ddraw"))
                    envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");

                Log.d(TAG, "Extracting ddrawrapper " + ddrawrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst", windowsDir, onExtractFileListener);
            }

            Log.d(TAG, "Finished extraction of DXVK wrapper files, version: " + dxwrapper);
        } else if (dxwrapper.contains("wined3d")) {
            Log.d(TAG, "Restoring original DLL files for wined3d.");
            restoreOriginalDllFiles(dlls);
        }
    }

    private void extractD8VKIfNeeded(String dxvkWrapper, File windowsDir) {
        if (compareVersion(dxvkWrapper, "2.4") >= 0) return;

        Log.d(TAG, "Extracting d8vk as part of DXVK version " + dxvkWrapper);
        TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                this,
                D8VK_ASSET_PATH,
                windowsDir,
                onExtractFileListener
        );
    }

    private static int compareVersion(String varA, String varB) {
        int[] a = parseSemverLoose(varA);
        int[] b = parseSemverLoose(varB);

        if (a[0] != b[0]) return a[0] - b[0];
        if (a[1] != b[1]) return a[1] - b[1];
        return a[2] - b[2];
    }

    private static final Pattern SEMVER_LOOSE =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static int[] parseSemverLoose(String s) {
        if (s == null) return new int[]{0, 0, 0};

        Matcher m = SEMVER_LOOSE.matcher(s);

        String g1 = null, g2 = null, g3 = null;
        while (m.find()) {
            g1 = m.group(1);
            g2 = m.group(2);
            g3 = m.group(3);
        }

        if (g1 == null || g2 == null) {
            return new int[]{0, 0, 0};
        }

        int major = safeParseInt(g1);
        int minor = safeParseInt(g2);
        int patch = safeParseInt(g3);
        return new int[]{major, minor, patch};
    }

    private static int safeParseInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void extractWinComponentFiles() {
        Log.d("XServerDisplayActivity", "Extracting WinComponents");
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/windows");
        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/system.reg");

        try {
            String wincomponentsStr = FileUtils.readString(this, "wincomponents/wincomponents.json");
            JSONObject wincomponentsJSONObject = new JSONObject(wincomponentsStr != null ? wincomponentsStr : "{}");
            ArrayList<String> dlls = new ArrayList<>();
            String wincomponents = shortcut != null ? getShortcutSetting("wincomponents", container.getWinComponents()) : container.getWinComponents();

            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                if (wincomponent[1].equals(oldWinComponentsIter.next()[1]) && !firstTimeBoot) continue;
                String identifier = wincomponent[0];
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir, onExtractFileListener);
                }
                else {
                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
                    for (int i = 0; i < dlnames.length(); i++) {
                        String dlname = dlnames.getString(i);
                        dlls.add(!dlname.endsWith(".exe") ? dlname+".dll" : dlname);
                    }
                }
                Log.d("XServerDisplayActivity", "Setting wincomponent " + identifier + " to " + String.valueOf(useNative));
                WineUtils.overrideWinComponentDlls(this, container, identifier, useNative);
                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative, this);
            }

            if (!dlls.isEmpty()) restoreOriginalDllFiles(dlls.toArray(new String[0]));
        }
        catch (JSONException e) {}
    }

    private void restoreOriginalDllFiles(final String... dlls) {
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/windows");
        File system32dlls = null;
        File syswow64dlls = null;

        if (wineInfo.isArm64EC())
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/aarch64-windows");
        else
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/x86_64-windows");

        syswow64dlls = new File(imageFs.getWinePath() + "/lib/wine/i386-windows");


        for (String dll : dlls) {
            File srcFile = new File(system32dlls, dll);
            File dstFile = new File(windowsDir, "system32/" + dll);
            FileUtils.copy(srcFile, dstFile);
            srcFile = new File(syswow64dlls, dll);
            dstFile = new File(windowsDir, "syswow64/" + dll);
            FileUtils.copy(srcFile, dstFile);
        }
   }

    /**
     * Mount the A: drive on a container, pointing to the given game install path.
     * Removes any existing A: mapping first.
     */
    /**
     * Mount the A: drive on a container using an ephemeral dosdevices symlink.
     * This avoids polluting the container's persistent drives setting, so multiple
     * games sharing a container don't overwrite each other's A: mapping.
     */
    private String getWineStartCommand(GuestProgramLauncherComponent launcherComponent) {
        // Initialize overrideEnvVars if not already done
        EnvVars envVars = getOverrideEnvVars();

        // Define default arguments
        String args = "";

        if (shortcut != null) {
            String path = shortcut.path;
            String gameSource = shortcut.getExtra("game_source", "CUSTOM");
            Log.d("XServerDisplayActivity", "getWineStartCommand: gameSource=" + gameSource + " shortcut.path=" + path);

            // Normalize DOS paths like A:EOSBootstrapper.exe to A:\EOSBootstrapper.exe
            if (path != null && path.matches("^[A-Z]:[^\\\\/].*")) {
                path = path.substring(0, 2) + "\\" + path.substring(2);
            }

            if (gameSource.equals("STEAM")) {
                int appId = Integer.parseInt(shortcut.getExtra("app_id"));
                String steamExtraArgs = shortcut.getSettingExtra("execArgs", container.getExecArgs());
                steamExtraArgs = (steamExtraArgs != null && !steamExtraArgs.isEmpty()) ? " " + steamExtraArgs : "";

                boolean useColdClient = parseBoolean(getShortcutSetting("useColdClient", container.isUseColdClient() ? "1" : "0"));
                boolean launchRealSteam = parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"));

                // Pre-resolve: ensure game install path and steamapps/common symlink exist
                // before ANY launch mode so the Steam directory structure is always valid.
                String gameInstPath = resolveSteamGameInstallPath(appId);
                if (gameInstPath != null && new File(gameInstPath).exists()) {
                    WineUtils.ensureSteamappsCommonSymlink(container, gameInstPath);
                }

                // Resolve working dir through the container's actual filesystem path
                // (not through getNativePath which can resolve to a different prefix).
                File containerSteamDir = new File(container.getRootDir(),
                        ".wine/drive_c/Program Files (x86)/Steam");

                if (launchRealSteam) {
                    // Real Steam mode: launch steam.exe with -applaunch <appId> and let
                    // Steam handle the game launch normally (update check, cloud sync, exe
                    // spawn). Cloud pending-ops are cleared pre-launch via javasteam's
                    // signalAppLaunchIntent(ignorePendingOperations=true) (see
                    // setupSteamEnvironment). No env var / DLL override hacks — matches the
                    // reference implementation exactly.
                    if (containerSteamDir.exists()) launcherComponent.setWorkingDir(containerSteamDir);
                    args = "\"C:\\Program Files (x86)\\Steam\\steam.exe\" -silent -vgui -tcp "
                            + "-nobigpicture -nofriendsui -nochatui -nointro -applaunch " + appId;
                    Log.d("XServerDisplayActivity", "Real Steam launch via steam.exe for appId=" + appId);
                    scheduleRealSteamWatchdog(launcherComponent);
                } else if (useColdClient) {
                    // ColdClient mode: always use x64 loader — IgnoreLoaderArchDifference=1
                    // in ColdClientLoader.ini handles architecture differences.
                    // cwd goes to the actual game dir (not Steam root) so games that resolve
                    // assets/configs relative to cwd find them. The loader itself is path-agnostic;
                    // it reads ColdClientLoader.ini from the Steam dir by absolute Windows path.
                    File coldClientWorkDir = null;
                    String gameDirNameCC = (gameInstPath != null) ? new File(gameInstPath).getName() : "";
                    String relativeExeCC = resolveRelativeGameExe(appId, gameInstPath);
                    if (!gameDirNameCC.isEmpty()) {
                        File containerGameDirCC = new File(containerSteamDir, "steamapps/common/" + gameDirNameCC);
                        try { coldClientWorkDir = containerGameDirCC.getCanonicalFile(); }
                        catch (IOException e) { coldClientWorkDir = containerGameDirCC; }
                        if (!relativeExeCC.isEmpty()) {
                            String exeRelNativeCC = relativeExeCC.replace("\\", "/");
                            int lastSlashCC = exeRelNativeCC.lastIndexOf("/");
                            if (lastSlashCC > 0) {
                                File exeParentDirCC = new File(coldClientWorkDir, exeRelNativeCC.substring(0, lastSlashCC));
                                if (exeParentDirCC.exists()) coldClientWorkDir = exeParentDirCC;
                            }
                        }
                    }
                    if (coldClientWorkDir != null && coldClientWorkDir.exists()) {
                        launcherComponent.setWorkingDir(coldClientWorkDir);
                        Log.d("XServerDisplayActivity", "ColdClient working dir: " + coldClientWorkDir.getPath());
                    } else if (containerSteamDir.exists()) {
                        launcherComponent.setWorkingDir(containerSteamDir);
                        Log.w("XServerDisplayActivity", "ColdClient: game dir unresolved, falling back to Steam dir");
                    }
                    args = "\"C:\\Program Files (x86)\\Steam\\steamclient_loader_x64.exe\"";
                    Log.d("XServerDisplayActivity", "ColdClient launch via steamclient_loader_x64.exe for appId=" + appId);
                } else {
                    // Goldberg mode (default): launch game exe through the Steam directory
                    // structure. The symlink chain resolves automatically:
                    //   C:\...\Steam → shared store → steamapps/common/<Game> → actual game dir
                    // This avoids fragile drive-letter resolution and works for all games.
                    String gameDirName = (gameInstPath != null) ? new File(gameInstPath).getName() : "";
                    String relativeExe = resolveRelativeGameExe(appId, gameInstPath);

                    if (!relativeExe.isEmpty() && !gameDirName.isEmpty()) {
                        String steamGameExe = "C:\\Program Files (x86)\\Steam\\steamapps\\common\\"
                                + gameDirName + "\\" + relativeExe.replace("/", "\\");

                        // Set working dir via container filesystem path (follows symlinks to actual game dir)
                        File containerGameDir = new File(containerSteamDir, "steamapps/common/" + gameDirName);
                        File actualWorkDir;
                        try { actualWorkDir = containerGameDir.getCanonicalFile(); }
                        catch (IOException e) { actualWorkDir = containerGameDir; }
                        String exeRelNative = relativeExe.replace("\\", "/");
                        int lastSlash = exeRelNative.lastIndexOf("/");
                        if (lastSlash > 0) {
                            File exeParentDir = new File(actualWorkDir, exeRelNative.substring(0, lastSlash));
                            if (exeParentDir.exists()) actualWorkDir = exeParentDir;
                        }
                        if (actualWorkDir.exists()) {
                            launcherComponent.setWorkingDir(actualWorkDir);
                            Log.d("XServerDisplayActivity", "Goldberg working dir: " + actualWorkDir.getPath());
                        }

                        args = "\"" + steamGameExe + "\"" + steamExtraArgs;
                        int lastBackslash = steamGameExe.lastIndexOf("\\");
                        if (lastBackslash > 0) {
                            envVars.put("WINEPATH", steamGameExe.substring(0, lastBackslash));
                        }
                        Log.d("XServerDisplayActivity", "Goldberg launch: " + steamGameExe);
                    } else {
                        // Fallback: try direct drive-letter path via findGameExeWinPath
                        String gameExeWinPath = findGameExeWinPath(appId,
                                new File(gameInstPath != null ? gameInstPath : ""));
                        if (gameExeWinPath != null) {
                            int lastBackslash = gameExeWinPath.lastIndexOf("\\");
                            String dir = lastBackslash >= 0 ? gameExeWinPath.substring(0, lastBackslash) : "C:\\";
                            args = "\"" + gameExeWinPath + "\"" + steamExtraArgs;
                            envVars.put("WINEPATH", dir);
                            Log.d("XServerDisplayActivity", "Goldberg fallback launch: " + gameExeWinPath);
                        } else {
                            Log.e("XServerDisplayActivity", "Could not find game exe for appId=" + appId
                                    + " gameInstPath=" + gameInstPath + " relativeExe=" + relativeExe);
                            args = "\"wfm.exe\"";
                        }
                    }
                }
            } else if (gameSource.equals("EPIC") || gameSource.equals("GOG")) {
                String extraArgs = shortcut.getSettingExtra("execArgs", container.getExecArgs());
                if (extraArgs == null || extraArgs.isEmpty()) {
                    extraArgs = getIntent().getStringExtra("extra_exec_args");
                }
                extraArgs = (extraArgs != null && !extraArgs.isEmpty()) ? " " + extraArgs : "";
                String gameInstallPath = shortcut.getExtra("game_install_path");

                // Re-provision the per-container C:\WinNative\Games\<source>\<game> symlink in
                // the CURRENT container. Needed after a user changes the shortcut's container
                // via Settings — the symlink was only created in the original container, so the
                // healthy C:\WinNative\... Exec would otherwise resolve to nothing in the new
                // prefix. Mirrors Steam's ensureSteamappsCommonSymlink call above.
                String storeInstallPath = shortcut.getExtra("game_install_path");
                if (storeInstallPath != null && !storeInstallPath.isEmpty()
                        && new File(storeInstallPath).exists()) {
                    WineUtils.ensureDriveCGameSymlink(container, gameSource, storeInstallPath);
                }

                boolean needsAutoDetect = path == null || path.isEmpty()
                        || "D:\\".equals(path) || "D:\\\\".equals(path)
                        || "A:\\".equals(path) || "A:\\\\".equals(path);
                if (needsAutoDetect) {
                    if ((gameInstallPath == null || gameInstallPath.isEmpty()) && gameSource.equals("GOG")) {
                        String gogId = shortcut.getExtra("gog_id");
                        if (!gogId.isEmpty()) {
                            try {
                                com.winlator.cmod.feature.stores.gog.data.GOGGame gogGame = com.winlator.cmod.feature.stores.gog.service.GOGService.Companion.getGOGGameOf(gogId);
                                if (gogGame != null) {
                                    gameInstallPath = gogGame.getInstallPath();
                                    if ((gameInstallPath == null || gameInstallPath.isEmpty()) && gogGame.getTitle() != null && !gogGame.getTitle().isEmpty()) {
                                        gameInstallPath = com.winlator.cmod.feature.stores.gog.service.GOGConstants.INSTANCE.getGameInstallPath(gogGame.getTitle());
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("XServerDisplayActivity", "Failed to resolve GOG install path for auto-detect", e);
                            }
                        }
                    }

                    if (gameInstallPath != null && !gameInstallPath.isEmpty()) {
                        File gameDir = new File(gameInstallPath);
                        String detectedPath = findGameExeWinPath(0, gameDir);
                        if (detectedPath != null && !detectedPath.isEmpty()) {
                            path = detectedPath;
                            updateShortcutExecLine(detectedPath);
                        }
                    }
                }
                path = repairStoreExecutableWinPath(gameSource, gameInstallPath, path);
                
                String filename = path;
                String dir = null;
                
                if (path != null && path.contains("\\")) {
                    int lastBackslash = path.lastIndexOf("\\");
                    filename = path.substring(lastBackslash + 1);
                    dir = path.substring(0, lastBackslash);
                    if (dir.endsWith(":")) dir += "\\";
                } else if (path != null && path.contains(":")) {
                    filename = path.substring(path.indexOf(":") + 1);
                    dir = path.substring(0, path.indexOf(":") + 1) + "\\";
                }
                if ((dir == null || dir.isEmpty()) && gameInstallPath != null && !gameInstallPath.isEmpty()) {
                    dir = com.winlator.cmod.runtime.wine.WineUtils.hostPathToRootWinePath(container, gameInstallPath);
                    if (dir != null && dir.endsWith(":")) dir += "\\";
                }
                if (dir == null || dir.isEmpty()) {
                    dir = "F:\\";
                }

                File nativeDir = com.winlator.cmod.runtime.wine.WineUtils.getNativePath(imageFs, dir);
                if (nativeDir != null && nativeDir.exists()) {
                    launcherComponent.setWorkingDir(nativeDir);
                    Log.d("XServerDisplayActivity", "Set native working dir for store process: " + nativeDir.getPath());
                }

                if (wineInfo != null && wineInfo.isArm64EC()) {
                    String epicCommand = dir + (dir.endsWith("\\") ? "" : "\\") + filename;
                    args = "\"" + epicCommand + "\"" + extraArgs;
                } else {
                    // Avoid StringUtils.escapeDOSPath here as it might double-escape
                    args = "/dir \"" + dir + "\" \"" + filename + "\"" + extraArgs;
                }
                Log.d("XServerDisplayActivity", gameSource + " game launch: " + args);
            } else {
                // Custom shortcut
                String extraArgs = shortcut.getSettingExtra("execArgs", container.getExecArgs());
                extraArgs = (extraArgs != null && !extraArgs.isEmpty()) ? " " + extraArgs : "";
                String customResolvedPath = resolveCustomExecutableWinPath(shortcut);
                if (customResolvedPath != null && !customResolvedPath.isEmpty()) {
                    path = customResolvedPath;
                }

                if (path != null && (path.startsWith("explorer") || path.contains(" /desktop"))) {
                    return path + extraArgs;
                } else if (path != null) {
                    String nativeDirPath = getActiveGameDirectoryPath();
                    if (nativeDirPath != null) {
                        File nativeDir = new File(nativeDirPath);
                        launcherComponent.setWorkingDir(nativeDir);
                        Log.d("XServerDisplayActivity", "Set native working dir for Custom process: " + nativeDir.getPath());
                    } else {
                        int lastBackslash = path.lastIndexOf("\\");
                        if (lastBackslash >= 0) {
                            String dir = path.substring(0, lastBackslash);
                            if (dir.endsWith(":")) dir += "\\";

                            File nativeDir = com.winlator.cmod.runtime.wine.WineUtils.getNativePath(this.container, imageFs, dir);
                            if (nativeDir != null) {
                                launcherComponent.setWorkingDir(nativeDir);
                                Log.d("XServerDisplayActivity", "Set native working dir for Custom process: " + nativeDir.getPath());
                            }
                        }
                    }

                    args = "\"" + path + "\"" + extraArgs;
                } else {
                    args = "\"wfm.exe\"";
                }
            }
        } else {
            // No shortcut, check for override args or launch file manager
            if (envVars.has("EXTRA_EXEC_ARGS")) {
                args = envVars.get("EXTRA_EXEC_ARGS");
                envVars.remove("EXTRA_EXEC_ARGS");
            } else {
                args = "\"wfm.exe\"";
            }
        }

        // Apply winhandler.exe wrapper ONLY IF we have arguments for it and it's not already a command
        if (!args.isEmpty() && !args.startsWith("winhandler.exe") && !args.startsWith("explorer")) {
            return "winhandler.exe " + args;
        } else {
            return args;
        }
    }

    private String getExecutable() {
        String filename = "";
        if (shortcut != null) {
            filename = FileUtils.getName(shortcut.path);
        }
        else
            filename = "wfm.exe";
        return filename;
    }

    /**
     * Verifies essential Steam client files exist in the wine prefix.
     * The xuser symlink ensures extraction goes to the active container,
     * but if files are missing (e.g. after prefix repair), force re-extraction.
     */
    private boolean verifySteamClientFiles(boolean requireColdClientSupport) {
        File steamDir = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
        String[] criticalFiles = requireColdClientSupport
                ? new String[] {
                    "steam.exe",
                    "Steam.dll",
                    "steamclient.dll",
                    "steamclient64.dll",
                    "SteamUI.dll",
                    "steam.signatures",
                    "steamclient_loader_x64.exe",
                    "extra_dlls/steamclient_extra_x64.dll"
                }
                : new String[] {
                    "steam.exe",
                    "Steam.dll",
                    "steamclient.dll",
                    "steamclient64.dll",
                    "SteamUI.dll",
                    "steam.signatures",
                    "tier0_s.dll",
                    "tier0_s64.dll",
                    "vstdlib_s.dll",
                    "vstdlib_s64.dll"
                };

        boolean allPresent = areSteamFilesPresent(steamDir, criticalFiles);

        if (!allPresent) {
            Log.w("XServerDisplayActivity", "Steam client files missing in container, forcing re-extraction");
            // Force re-extraction by extracting the archive required by the active mode.
            // Real Steam mode uses steam.tzst. ColdClient mode uses only experimental-drm.tzst
            // so normal Steam game launches do not download or unpack the full Steam client.
            try {
                File steamFile = new File(getFilesDir(), "steam.tzst");
                File expFile = new File(getFilesDir(), "experimental-drm.tzst");
                if (!requireColdClientSupport && steamFile.exists()) {
                    com.winlator.cmod.shared.io.TarCompressorUtils.extract(
                            com.winlator.cmod.shared.io.TarCompressorUtils.Type.ZSTD,
                            steamFile, imageFs.getRootDir(), null);
                }
                if (requireColdClientSupport && expFile.exists()) {
                    com.winlator.cmod.shared.io.TarCompressorUtils.extract(
                            com.winlator.cmod.shared.io.TarCompressorUtils.Type.ZSTD,
                            expFile, imageFs.getRootDir(), null);
                }
                Log.d("XServerDisplayActivity", "Re-extracted Steam client files to container");
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Failed to re-extract Steam files", e);
            }

            allPresent = areSteamFilesPresent(steamDir, criticalFiles);
            if (!allPresent) {
                Log.e("XServerDisplayActivity", "Steam client verification still failed after re-extraction");
            }
        }
        return allPresent;
    }

    private boolean areSteamFilesPresent(File steamDir, String[] relativePaths) {
        if (!steamDir.exists()) return false;

        for (String relativePath : relativePaths) {
            File f = new File(steamDir, relativePath);
            if (!f.exists() || f.length() == 0) {
                Log.w("XServerDisplayActivity", "Missing Steam client file: " + relativePath);
                return false;
            }
        }
        return true;
    }

    /**
     * Generates steam_interfaces.txt for all steam_api DLLs in the game directory.
     * Used in ColdClient mode where the original DLLs remain but Goldberg steamclient
     * still needs to know which interfaces the game expects.
     */
    private void generateSteamInterfacesForGame(File gameDir) {
        if (gameDir == null || !gameDir.exists()) return;
        File[] files = gameDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && !file.getName().equals("steam_settings")) {
                generateSteamInterfacesForGame(file);
            } else if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.equals("steam_api.dll") || name.equals("steam_api64.dll")) {
                    // In ColdClient mode the DLL is the original (not replaced),
                    // so scan it directly for interface strings
                    generateSteamInterfacesFromDll(file.getParentFile(), file);
                }
            }
        }
    }

    /**
     * Generates steam_interfaces.txt by scanning a DLL (original or backup) for
     * Steam interface version strings (e.g., SteamUser023, SteamApps008).
     */
    private void generateSteamInterfacesFromDll(File dir, File dllFile) {
        File interfacesFile = new File(dir, "steam_interfaces.txt");
        if (interfacesFile.exists()) return;

        if (!dllFile.exists()) return;

        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(dllFile.toPath());
            java.util.TreeSet<String> interfaces = new java.util.TreeSet<>();

            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int ch = b & 0xFF;
                if (ch >= 0x20 && ch <= 0x7E) {
                    sb.append((char) ch);
                } else {
                    if (sb.length() >= 10) {
                        String candidate = sb.toString();
                        if (candidate.matches("^Steam[A-Za-z]+[0-9]{3}$")) {
                            interfaces.add(candidate);
                        }
                    }
                    sb.setLength(0);
                }
            }
            if (sb.length() >= 10) {
                String candidate = sb.toString();
                if (candidate.matches("^Steam[A-Za-z]+[0-9]{3}$")) {
                    interfaces.add(candidate);
                }
            }

            if (!interfaces.isEmpty()) {
                StringBuilder content = new StringBuilder();
                for (String iface : interfaces) {
                    content.append(iface).append("\n");
                }
                FileUtils.writeString(interfacesFile, content.toString());
                Log.d("XServerDisplayActivity", "Generated steam_interfaces.txt with " + interfaces.size() + " interfaces in " + dir.getName());
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to generate steam_interfaces.txt from " + dllFile.getName(), e);
        }
    }

    /**
     * Writes ColdClientLoader.ini from a known relative exe path and game directory name.
     * This avoids the fragile Windows-path-to-relative conversion in writeColdClientIniForLaunch.
     */
    private void writeColdClientIniDirect(int appId, String gameDirName, String relativeExe, boolean runtimePatcher) {
        File iniFile = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/ColdClientLoader.ini");
        iniFile.getParentFile().mkdirs();

        String exePath = "steamapps\\common\\" + gameDirName + "\\" + relativeExe.replace("/", "\\");
        String exeRunDir = exePath;
        int lastSep = exePath.lastIndexOf("\\");
        if (lastSep > 0) {
            exeRunDir = exePath.substring(0, lastSep);
        }

        String perGameExecArgs = shortcut != null ? shortcut.getSettingExtra("execArgs", container.getExecArgs()) : container.getExecArgs();
        String exeCommandLine = perGameExecArgs != null ? perGameExecArgs : "";

        // IgnoreLoaderArchDifference=1 is always needed so the x64 loader can spawn
        // x86 games. DllsToInjectFolder is only included when the user opts into the
        // Runtime DRM Patcher — that's when extra_dlls/steamclient_extra_{x32,x64}.dll
        // (Goldberg's runtime DRM patcher) is injected. Non-DRM games don't benefit
        // from injection and pay per-frame hook overhead, hence the opt-in toggle.
        StringBuilder injectionBuilder = new StringBuilder("[Injection]\nIgnoreLoaderArchDifference=1\n");
        if (runtimePatcher) {
            injectionBuilder.append("DllsToInjectFolder=extra_dlls\n");
        }
        String injectionSection = injectionBuilder.toString();

        String iniContent = "[SteamClient]\n" +
                "\n" +
                "Exe=" + exePath + "\n" +
                "ExeRunDir=" + exeRunDir + "\n" +
                "ExeCommandLine=" + exeCommandLine + "\n" +
                "AppId=" + appId + "\n" +
                "\n" +
                "# path to the steamclient dlls, both must be set, absolute paths or relative to the loader directory\n" +
                "SteamClientDll=steamclient.dll\n" +
                "SteamClient64Dll=steamclient64.dll\n" +
                "\n" +
                injectionSection;

        FileUtils.writeString(iniFile, iniContent);
        Log.d("XServerDisplayActivity",
                "Wrote ColdClientLoader.ini: Exe=" + exePath + " ExeRunDir=" + exeRunDir
                        + " AppId=" + appId + " runtimePatcher=" + runtimePatcher);
    }

    /**
     * Writes ColdClientLoader.ini with the correct game exe path for the Goldberg Steam emulator.
     * Uses a relative path through the steamapps/common symlink so the loader resolves correctly.
     * @param appId Steam app ID
     * @param gameExeWinPath Windows-style path to the game exe (e.g. A:\SubDir\game.exe)
     */
    private void writeColdClientIniForLaunch(int appId, String gameInstallPath, String gameExeWinPath, boolean runtimePatcher) {
        File iniFile = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/ColdClientLoader.ini");
        iniFile.getParentFile().mkdirs();

        String exePath;
        String gameDirName = new File(gameInstallPath).getName();

        if (gameExeWinPath != null) {
            String relativeExe = getRelativeGameExePath(gameExeWinPath, new File(gameInstallPath));
            exePath = "steamapps\\common\\" + gameDirName + "\\" + relativeExe;
        } else {
            exePath = "";
        }

        // ExeRunDir must match the executable's actual directory inside steamapps/common.
        // Some Steam games place the launch exe in a nested subdirectory; forcing the game root
        // causes the loader to start successfully but fail to hand off to the real process.
        String exeRunDir = "steamapps\\common\\" + gameDirName;
        if (!exePath.isEmpty()) {
            int lastSeparator = exePath.lastIndexOf("\\");
            if (lastSeparator > 0) {
                exeRunDir = exePath.substring(0, lastSeparator);
            }
        }

        String perGameExecArgs = shortcut != null ? shortcut.getSettingExtra("execArgs", container.getExecArgs()) : container.getExecArgs();
        String exeCommandLine = perGameExecArgs != null ? perGameExecArgs : "";

        // IgnoreLoaderArchDifference=1 is always needed so the x64 loader can spawn
        // x86 games. DllsToInjectFolder is only included when the user opts into the
        // Runtime DRM Patcher — that's when extra_dlls/steamclient_extra_{x32,x64}.dll
        // (Goldberg's runtime DRM patcher) is injected. Non-DRM games don't benefit
        // from injection and pay per-frame hook overhead, hence the opt-in toggle.
        StringBuilder injectionBuilder = new StringBuilder("[Injection]\nIgnoreLoaderArchDifference=1\n");
        if (runtimePatcher) {
            injectionBuilder.append("DllsToInjectFolder=extra_dlls\n");
        }
        String injectionSection = injectionBuilder.toString();

        String iniContent = "[SteamClient]\n" +
                "\n" +
                "Exe=" + exePath + "\n" +
                "ExeRunDir=" + exeRunDir + "\n" +
                "ExeCommandLine=" + exeCommandLine + "\n" +
                "AppId=" + appId + "\n" +
                "\n" +
                "# path to the steamclient dlls, both must be set, absolute paths or relative to the loader directory\n" +
                "SteamClientDll=steamclient.dll\n" +
                "SteamClient64Dll=steamclient64.dll\n" +
                "\n" +
                injectionSection;

        FileUtils.writeString(iniFile, iniContent);
        Log.d("XServerDisplayActivity", "Wrote ColdClientLoader.ini: Exe=" + exePath + " ExeRunDir=" + exeRunDir + " AppId=" + appId);

        // Also update any ColdClientLoader.ini inside the game directory's embedded Steam/ folder.
        // The experimental-drm extraction creates a full Steam directory inside the game folder
        // (e.g., Fallout New Vegas/Steam/) with its own ColdClientLoader.ini. If that copy has
        // a stale/empty ExeRunDir, the loader will pick it up and fail with "Application Load Error".
        if (gameInstallPath != null) {
            File gameSteamDir = new File(gameInstallPath, "Steam");
            File gameSteamIni = new File(gameSteamDir, "ColdClientLoader.ini");
            if (gameSteamDir.exists() && gameSteamIni.exists()) {
                FileUtils.writeString(gameSteamIni, iniContent);
                Log.d("XServerDisplayActivity", "Also updated ColdClientLoader.ini in game's Steam/ dir: " + gameSteamIni.getAbsolutePath());
            }
        }
    }
    
    private String getRelativeGameExePath(String gameExeWinPath, File gameDir) {
        if (gameExeWinPath == null || gameExeWinPath.isEmpty()) return "";

        File nativeGameExe = com.winlator.cmod.runtime.wine.WineUtils.getNativePath(imageFs, gameExeWinPath);
        if (nativeGameExe != null && gameDir != null) {
            String gameDirPath = getCanonicalPathOrAbsolute(gameDir);
            String nativePath = getCanonicalPathOrAbsolute(nativeGameExe);
            if (nativePath.equals(gameDirPath) || nativePath.startsWith(gameDirPath + File.separator)) {
                String relativePath = nativePath.substring(gameDirPath.length());
                if (relativePath.startsWith(File.separator)) relativePath = relativePath.substring(1);
                return relativePath.replace("/", "\\");
            }
        }

        String normalizedPath = gameExeWinPath.replace("/", "\\");
        if (normalizedPath.matches("^[A-Za-z]:\\\\.*")) {
            return normalizedPath.substring(3);
        }
        return normalizedPath;
    }

    /**
     * Resolves the game executable as a RELATIVE path within the game install directory.
     * Tries multiple strategies with caching so subsequent launches are fast.
     * Returns "" if no exe can be found.
     */
    private String resolveRelativeGameExe(int appId, String gameInstPath) {
        // Strategy 1: container.executablePath (cached from previous launch or container setup)
        String exePath = container.getExecutablePath();
        if (exePath != null && !exePath.isEmpty() && gameInstPath != null) {
            File test = new File(gameInstPath, exePath.replace("\\", "/"));
            if (test.isFile()) {
                Log.d("XServerDisplayActivity", "resolveRelativeGameExe: found via container.executablePath: " + exePath);
                return exePath;
            }
        }

        // Strategy 2: shortcut launch_exe_path
        if (shortcut != null) {
            String launchExe = shortcut.getExtra("launch_exe_path");
            if (launchExe != null && !launchExe.isEmpty() && gameInstPath != null) {
                File configuredLaunchExe = new File(launchExe);
                if (configuredLaunchExe.isAbsolute()) {
                    String configuredAbsolutePath = getCanonicalPathOrAbsolute(configuredLaunchExe);
                    String gameInstallPath = getCanonicalPathOrAbsolute(new File(gameInstPath));
                    String gameInstallPrefix = gameInstallPath.endsWith(File.separator)
                            ? gameInstallPath
                            : gameInstallPath + File.separator;
                    if (configuredLaunchExe.isFile()
                            && configuredAbsolutePath.startsWith(gameInstallPrefix)) {
                        String relative = configuredAbsolutePath.substring(gameInstallPrefix.length());
                        Log.d("XServerDisplayActivity", "resolveRelativeGameExe: normalized absolute shortcut.launch_exe_path: " + relative);
                        return relative;
                    }
                }

                File test = new File(gameInstPath, launchExe.replace("\\", "/"));
                if (test.isFile()) {
                    Log.d("XServerDisplayActivity", "resolveRelativeGameExe: found via shortcut.launch_exe_path: " + launchExe);
                    return launchExe;
                }
            }
        }

        // Strategy 3: SteamService.getInstalledExe (queries Steam app metadata)
        String steamExe = SteamBridge.getInstalledExe(appId);
        if (steamExe != null && !steamExe.isEmpty() && gameInstPath != null) {
            File test = new File(gameInstPath, steamExe.replace("\\", "/"));
            if (test.isFile()) {
                Log.d("XServerDisplayActivity", "resolveRelativeGameExe: found via SteamBridge.getInstalledExe: " + steamExe);
                container.setExecutablePath(steamExe);
                container.saveData();
                if (shortcut != null && (shortcut.getExtra("launch_exe_path") == null || shortcut.getExtra("launch_exe_path").isEmpty())) {
                    shortcut.putExtra("launch_exe_path", steamExe);
                    shortcut.saveData();
                }
                return steamExe;
            }
        }

        // Strategy 4: Auto-detect from game directory using BFS heuristic
        if (gameInstPath != null) {
            File gameDir = new File(gameInstPath);
            if (gameDir.exists()) {
                File detected = findGameExe(gameDir);
                if (detected != null) {
                    String absPath = getCanonicalPathOrAbsolute(detected);
                    String basePath = getCanonicalPathOrAbsolute(gameDir);
                    if (absPath.startsWith(basePath)) {
                        String relative = absPath.substring(basePath.length());
                        if (relative.startsWith(File.separator)) relative = relative.substring(1);
                        Log.d("XServerDisplayActivity", "resolveRelativeGameExe: auto-detected: " + relative);
                        container.setExecutablePath(relative);
                        container.saveData();
                        if (shortcut != null && (shortcut.getExtra("launch_exe_path") == null || shortcut.getExtra("launch_exe_path").isEmpty())) {
                            shortcut.putExtra("launch_exe_path", relative);
                            shortcut.saveData();
                        }
                        return relative;
                    }
                }
            }
        }

        Log.w("XServerDisplayActivity", "resolveRelativeGameExe: all strategies failed for appId=" + appId
                + " gameInstPath=" + gameInstPath);
        return "";
    }

    /**
     * Finds the game exe and returns its Windows-style mapped path.
     * Uses shortcut.path if available, otherwise auto-detects from game install directory.
     */
    private String findGameExeWinPath(int appId, File gameDir) {
        if (gameDir == null || !gameDir.exists()) return null;

        String gameInstallPath = getCanonicalPathOrAbsolute(gameDir);

        if (appId > 0) {
            String launchExePath = shortcut != null ? shortcut.getExtra("launch_exe_path") : "";
            if (launchExePath == null) launchExePath = "";

            String resolvedRelativePath = launchExePath;
            if (!resolvedRelativePath.isEmpty()) {
                File configuredFile = new File(resolvedRelativePath);
                if (configuredFile.isAbsolute()) {
                    String configuredAbsolutePath = getCanonicalPathOrAbsolute(configuredFile);
                    if (configuredAbsolutePath.equals(gameInstallPath) || configuredAbsolutePath.startsWith(gameInstallPath + File.separator)) {
                        resolvedRelativePath = configuredAbsolutePath.substring(gameInstallPath.length());
                        if (resolvedRelativePath.startsWith(File.separator)) {
                            resolvedRelativePath = resolvedRelativePath.substring(1);
                        }
                    } else {
                        resolvedRelativePath = "";
                    }
                }
            }

            if (resolvedRelativePath.isEmpty()) {
                resolvedRelativePath = SteamBridge.getInstalledExe(appId);
            }

            if (resolvedRelativePath != null && !resolvedRelativePath.isEmpty()) {
                if (shortcut != null && (shortcut.getExtra("launch_exe_path") == null || shortcut.getExtra("launch_exe_path").isEmpty())) {
                    shortcut.putExtra("launch_exe_path", resolvedRelativePath);
                    shortcut.saveData();
                }
                File resolvedExeFile = resolvePathCaseInsensitive(gameDir, resolvedRelativePath);
                if (resolvedExeFile != null && resolvedExeFile.isFile()) {
                    return com.winlator.cmod.runtime.wine.WineUtils.hostPathToRootWinePath(
                            container, getCanonicalPathOrAbsolute(resolvedExeFile));
                }
            }
        }

        if (shortcut != null && shortcut.path != null && !shortcut.path.isEmpty()
                && !shortcut.path.contains("steamclient_loader")
                && shortcut.path.contains("\\")) {
            String safePath = shortcut.path;
            if (safePath.matches("^[A-Z]:[^\\\\/].*")) {
                safePath = safePath.substring(0, 2) + "\\" + safePath.substring(2);
            }
            return safePath;
        }

        File exeFile = findGameExe(gameDir);
        if (exeFile != null) {
            return com.winlator.cmod.runtime.wine.WineUtils.hostPathToRootWinePath(container, exeFile.getAbsolutePath());
        }

        return null;
    }

    private String getCanonicalPathOrAbsolute(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private File resolvePathCaseInsensitive(File baseDir, String relativePath) {
        if (baseDir == null || relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        String normalizedPath = relativePath.replace('\\', '/');
        File directFile = new File(baseDir, normalizedPath);
        if (directFile.exists()) {
            return directFile;
        }

        File currentDir = baseDir;
        String[] segments = normalizedPath.split("/");
        for (String segment : segments) {
            if (segment == null || segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                currentDir = currentDir.getParentFile();
                if (currentDir == null) {
                    return null;
                }
                continue;
            }

            File[] entries = currentDir.listFiles();
            if (entries == null) {
                return null;
            }

            File matched = null;
            for (File entry : entries) {
                if (entry.getName().equalsIgnoreCase(segment)) {
                    matched = entry;
                    break;
                }
            }
            if (matched == null) {
                return null;
            }
            currentDir = matched;
        }

        return currentDir;
    }

    /**
     * Replaces all steam_api.dll / steam_api64.dll in the game directory tree with
     * steampipe stubs. Generates steam_interfaces.txt BEFORE replacing (from the original),
     * backs up originals as .orig, writes orig_dll_path.txt,
     * and calls writeCompleteSettingsDir next to each DLL found.
     */
    private void injectSteamApiIfMissing(File gameDir, String appDirPath, String language,
            boolean isOffline, boolean forceDlc, boolean useSteamInput, String ticketBase64, java.util.List<String> backupPaths) {
        Log.w("XServerDisplayActivity", "No steam_api DLLs found in game directory — injecting Goldberg steam_api next to game exe");
        try {
            // Find the game exe to determine architecture
            String exePath = shortcut != null ? shortcut.getExtra("launch_exe_path") : null;
            File gameExe = null;
            if (exePath != null && !exePath.isEmpty()) {
                File candidate = new File(exePath);
                if (!candidate.isAbsolute()) candidate = new File(gameDir, exePath);
                if (candidate.exists()) gameExe = candidate;
            }
            // Fallback: find the first .exe in the game root (skip crash reporters etc.)
            if (gameExe == null) {
                File[] rootFiles = gameDir.listFiles();
                if (rootFiles != null) {
                    for (File f : rootFiles) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".exe")
                                && !f.getName().toLowerCase().contains("crash")
                                && !f.getName().toLowerCase().contains("unins")
                                && !f.getName().toLowerCase().contains("redist")) {
                            gameExe = f;
                            break;
                        }
                    }
                }
            }

            if (gameExe != null && gameExe.exists()) {
                File exeDir = gameExe.getParentFile();
                boolean isX64 = isExe64Bit(gameExe);
                String dllName = isX64 ? "steam_api64.dll" : "steam_api.dll";
                String assetName = isX64 ? "steampipe/steam_api64.dll" : "steampipe/steam_api.dll";
                String stubAsset = isX64 ? "steampipe/steamclient64.dll" : "steampipe/steamclient.dll";
                String stubName = isX64 ? "steamclient64.dll" : "steamclient.dll";

                // Place Goldberg steam_api DLL next to the game exe
                File targetDll = new File(exeDir, dllName);
                if (!targetDll.exists()) {
                    try (InputStream is = getAssets().open(assetName);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(targetDll)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) >= 0) fos.write(buf, 0, len);
                    }
                    // Create empty .orig so restoreSteamApiDlls knows this was injected
                    new File(targetDll.getAbsolutePath() + ".orig").createNewFile();
                    Log.d("XServerDisplayActivity",
                            "Injected Goldberg " + dllName + " next to " + gameExe.getName());
                }

                // Also place the matching steamclient stub
                File stubFile = new File(exeDir, stubName);
                if (!stubFile.exists()) {
                    try (InputStream is = getAssets().open(stubAsset);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(stubFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) >= 0) fos.write(buf, 0, len);
                    }
                    Log.d("XServerDisplayActivity",
                            "Injected steamclient stub " + stubName + " next to " + gameExe.getName());
                }

                // Check for Capcom-style embedded Steam directory (e.g. Steam/steamclient64.dll).
                // They use explicit relative paths (LoadLibrary("Steam\\steamclient64.dll")), which bypasses
                // the standard directory search order. We must replace the file inside the Steam dir itself.
                File gameSteamDir = new File(exeDir, "Steam");
                if (gameSteamDir.exists() && gameSteamDir.isDirectory()) {
                    File embeddedClient = new File(gameSteamDir, stubName);
                    if (embeddedClient.exists()) {
                        File backupClient = new File(gameSteamDir, stubName + ".orig");
                        if (!backupClient.exists()) {
                            FileUtils.copy(embeddedClient, backupClient);
                        }
                        
                        embeddedClient.delete();
                        try (InputStream is = getAssets().open(stubAsset);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(embeddedClient)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) >= 0) fos.write(buf, 0, len);
                        }
                        Log.w("XServerDisplayActivity", "Intercepted explicit embedded Steam client: " + embeddedClient.getAbsolutePath());
                        
                        if (backupPaths != null && appDirPath != null) {
                            String relPath = backupClient.getAbsolutePath();
                            if (relPath.startsWith(appDirPath)) {
                                relPath = relPath.substring(appDirPath.length());
                                if (relPath.startsWith("/")) relPath = relPath.substring(1);
                            }
                            backupPaths.add(relPath);
                        }
                        
                        // Drop settings there too
                        SteamUtils.writeCompleteSettingsDir(gameSteamDir,
                                Integer.parseInt(shortcut.getExtra("app_id")),
                                language, isOffline, forceDlc, useSteamInput, ticketBase64);
                    }
                }

                // Write steam_settings next to the injected DLL
                SteamUtils.writeCompleteSettingsDir(exeDir,
                        Integer.parseInt(shortcut.getExtra("app_id")),
                        language, isOffline, forceDlc, useSteamInput, ticketBase64);

                // Track the injected DLL as a backup path so we can clean it up later
                if (backupPaths != null && appDirPath != null) {
                    String relPath = targetDll.getAbsolutePath();
                    if (relPath.startsWith(appDirPath)) {
                        relPath = relPath.substring(appDirPath.length());
                        if (relPath.startsWith("/")) relPath = relPath.substring(1);
                    }
                    backupPaths.add(relPath);
                }
            } else {
                Log.w("XServerDisplayActivity", "Could not find game exe to inject steam_api DLL");
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to inject steam_api DLL for no-DLL game", e);
        }
    }

    private void replaceSteamApiDlls(File gameDir, String appDirPath, String language,
            boolean isOffline, boolean forceDlc, boolean useSteamInput, String ticketBase64) {
        if (gameDir == null || !gameDir.exists()) return;

        java.util.List<String> backupPaths = new java.util.ArrayList<>();
        replaceSteamApiDllsRecursive(gameDir, appDirPath, language, isOffline, forceDlc,
                useSteamInput, ticketBase64, backupPaths);

        // ── Handle games with NO steam_api*.dll at all ──────────────────────────
        // Games like Monster Hunter Stories (MHST.exe) don't ship with steam_api64.dll.
        // They communicate with Steam through the embedded Steam/ directory's
        // steamclient64.dll directly. Without a steam_api64.dll for Goldberg to hook
        // into, the game fails with "Application Load Error 3:0000065432".
        if (backupPaths.isEmpty()) {
            injectSteamApiIfMissing(gameDir, appDirPath, language, isOffline, forceDlc, useSteamInput, ticketBase64, backupPaths);
        }

        // Write orig_dll_path.txt listing all .orig backup paths
        if (!backupPaths.isEmpty()) {
            try {
                java.util.Collections.sort(backupPaths);
                File origPathFile = new File(appDirPath, "orig_dll_path.txt");
                FileUtils.writeString(origPathFile, android.text.TextUtils.join(System.lineSeparator(), backupPaths));
                Log.d("XServerDisplayActivity", "Wrote " + backupPaths.size() + " DLL backup paths to orig_dll_path.txt");
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to write orig_dll_path.txt", e);
            }
        }
    }

    /**
     * Checks if any steam_api.dll or steam_api64.dll exists anywhere in the game directory tree.
     * Used to validate that the STEAM_DLL_REPLACED marker is not stale.
     */
    private boolean hasSteamApiDllInTree(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("steam_settings") && hasSteamApiDllInTree(file)) return true;
            } else {
                String name = file.getName().toLowerCase();
                if (name.equals("steam_api.dll") || name.equals("steam_api64.dll")) return true;
            }
        }
        return false;
    }

    /**
     * Reads the PE header of an exe to determine if it targets x64 (PE32+).
     * Returns true for x86_64/ARM64, false for x86/unknown.
     */
    private boolean isExe64Bit(File exeFile) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(exeFile, "r")) {
            // Read DOS header e_lfanew (offset to PE header) at offset 0x3C
            raf.seek(0x3C);
            int peOffset = Integer.reverseBytes(raf.readInt()); // little-endian
            // Read PE signature (4 bytes) + Machine field (2 bytes)
            raf.seek(peOffset + 4); // skip "PE\0\0"
            int machine = Short.reverseBytes(raf.readShort()) & 0xFFFF; // unsigned little-endian
            // 0x8664 = AMD64, 0xAA64 = ARM64
            return machine == 0x8664 || machine == 0xAA64;
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Could not determine exe architecture, assuming x64", e);
            return true; // Default to x64 as most modern Steam games are 64-bit
        }
    }

    private void replaceSteamApiDllsRecursive(File dir, String appDirPath, String language,
            boolean isOffline, boolean forceDlc, boolean useSteamInput, String ticketBase64,
            java.util.List<String> backupPaths) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        boolean hasSteamDll = false;
        for (File file : files) {
            if (file.isDirectory()) continue;
            String name = file.getName().toLowerCase();
            if (!name.equals("steam_api.dll") && !name.equals("steam_api64.dll")) continue;

            hasSteamDll = true;
            String assetName = name.equals("steam_api64.dll")
                    ? "steampipe/steam_api64.dll"
                    : "steampipe/steam_api.dll";

            try {
                // Generate steam_interfaces.txt BEFORE replacing (scans the original DLL)
                generateSteamInterfacesFromDll(dir, file);

                // Backup as .orig if not already done
                File backup = new File(file.getParent(), file.getName() + ".orig");
                if (!backup.exists()) {
                    FileUtils.copy(file, backup);
                    Log.d("XServerDisplayActivity", "Backed up original: " + file.getName() + " as .orig");
                }
                // Record relative backup path for orig_dll_path.txt
                String relPath = backup.getAbsolutePath();
                if (relPath.startsWith(appDirPath)) {
                    relPath = relPath.substring(appDirPath.length());
                    if (relPath.startsWith("/")) relPath = relPath.substring(1);
                }
                backupPaths.add(relPath);

                // Replace with steampipe version
                file.delete();
                file.createNewFile();
                try (InputStream is = getAssets().open(assetName);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) >= 0) fos.write(buf, 0, len);
                }
                Log.d("XServerDisplayActivity", "Replaced " + file.getName() + " at " + file.getAbsolutePath());

                // Copy the matching experimental steamclient stub next to the replaced DLL.
                // The experimental steam_api DLLs expect this stub to prevent the game from
                // loading the real steamclient.dll, which would cause the game to hang.
                String stubAsset = name.equals("steam_api64.dll")
                        ? "steampipe/steamclient64.dll"
                        : "steampipe/steamclient.dll";
                String stubName = name.equals("steam_api64.dll")
                        ? "steamclient64.dll"
                        : "steamclient.dll";
                File stubFile = new File(dir, stubName);
                if (!stubFile.exists()) {
                    try (InputStream stubIs = getAssets().open(stubAsset);
                         java.io.FileOutputStream stubFos = new java.io.FileOutputStream(stubFile)) {
                        byte[] stubBuf = new byte[8192];
                        int stubLen;
                        while ((stubLen = stubIs.read(stubBuf)) >= 0) stubFos.write(stubBuf, 0, stubLen);
                    }
                    Log.d("XServerDisplayActivity", "Copied steamclient stub " + stubName + " next to " + file.getName());
                }
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Failed to replace " + file.getName(), e);
            }
        }

        // Write complete steam_settings next to this dir if it contained a steam_api DLL
        if (hasSteamDll) {
            SteamUtils.writeCompleteSettingsDir(dir,
                    Integer.parseInt(shortcut.getExtra("app_id")),
                    language, isOffline, forceDlc, useSteamInput, ticketBase64);
        }

        // Recurse into subdirectories (skip steam_settings to avoid infinite recursion)
        for (File file : files) {
            if (file.isDirectory() && !file.getName().equals("steam_settings")) {
                replaceSteamApiDllsRecursive(file, appDirPath, language, isOffline, forceDlc,
                        useSteamInput, ticketBase64, backupPaths);
            }
        }
    }

    /**
     * Walks the game directory and calls writeCompleteSettingsDir next to every directory
     * that contains a steam_api.dll or steam_api64.dll.
     * Used in ColdClient mode where we don't replace DLLs but still need settings.
     */
    private void setupSteamSettingsForAllDirs(File dir, int appId, String language,
            boolean isOffline, boolean forceDlc, boolean useSteamInput, String ticketBase64) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        boolean hasSteamDll = false;
        for (File file : files) {
            if (!file.isDirectory()) {
                String name = file.getName().toLowerCase();
                if (name.equals("steam_api.dll") || name.equals("steam_api64.dll")) {
                    hasSteamDll = true;
                }
            }
        }

        if (hasSteamDll) {
            SteamUtils.writeCompleteSettingsDir(dir, appId, language, isOffline, forceDlc, useSteamInput, ticketBase64);
        }

        for (File file : files) {
            if (file.isDirectory() && !file.getName().equals("steam_settings")) {
                setupSteamSettingsForAllDirs(file, appId, language, isOffline, forceDlc, useSteamInput, ticketBase64);
            }
        }
    }

    /**
     * Ensures experimental steamclient stubs exist next to any replaced steam_api DLLs.
     * Needed for games where DLLs were replaced before the stubs were bundled.
     */
    private void copySteamclientStubs(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("steam_settings")) copySteamclientStubs(file);
                continue;
            }
            String name = file.getName().toLowerCase();
            if (!name.equals("steam_api.dll") && !name.equals("steam_api64.dll")) continue;

            String stubAsset = name.equals("steam_api64.dll")
                    ? "steampipe/steamclient64.dll" : "steampipe/steamclient.dll";
            String stubName = name.equals("steam_api64.dll")
                    ? "steamclient64.dll" : "steamclient.dll";
            File stubFile = new File(dir, stubName);
            if (!stubFile.exists()) {
                try (InputStream is = getAssets().open(stubAsset);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(stubFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) >= 0) fos.write(buf, 0, len);
                    Log.d("XServerDisplayActivity", "Copied missing steamclient stub " + stubName + " to " + dir.getAbsolutePath());
                } catch (Exception e) {
                    Log.e("XServerDisplayActivity", "Failed to copy steamclient stub " + stubName, e);
                }
            }
        }
    }

    /**
     * Restores the original steam_api.dll and steam_api64.dll in the game directory.
     * Required if a game was previously launched in Goldberg mode (which swaps them with stubs),
     * but is now being launched in ColdClient mode (which requires the real DLLs).
     * Backups are stored as .orig (e.g. steam_api64.dll.orig) by replaceSteamApiDllsRecursive.
     */
    private void restoreSteamApiDlls(File gameDir) {
        if (gameDir == null || !gameDir.exists()) return;

        File[] files = gameDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("steam_settings")) {
                    restoreSteamApiDlls(file);
                }
            } else {
                String name = file.getName().toLowerCase();
                // Backups are written as .orig
                if (name.equals("steam_api.dll.orig") || name.equals("steam_api64.dll.orig")) {
                    try {
                        String originalName = file.getName().substring(0, file.getName().length() - ".orig".length());
                        File target = new File(file.getParent(), originalName);

                        if (target.exists()) target.delete();
                        if (file.length() == 0) {
                            // 0-byte .orig means the file was injected (didn't exist originally).
                            // Leave it deleted, don't copy a 0-byte file.
                            Log.d("XServerDisplayActivity", "Removed injected target " + originalName);
                        } else {
                            FileUtils.copy(file, target);
                        }

                        // Remove the experimental steamclient stub that was placed alongside
                        String stubName = name.equals("steam_api64.dll.orig")
                                ? "steamclient64.dll" : "steamclient.dll";
                        File stub = new File(file.getParent(), stubName);
                        if (stub.exists() && stub.length() < 200_000) {
                            stub.delete();
                            Log.d("XServerDisplayActivity", "Removed steamclient stub " + stubName);
                        }

                        Log.d("XServerDisplayActivity", "Restored original " + originalName + " from .orig backup");
                    } catch (Exception e) {
                        Log.e("XServerDisplayActivity", "Failed to restore " + file.getName(), e);
                    }
                }
            }
        }
    }

    /**
     * Checks whether the /dev/ntsync device node exists and is accessible.
     * On many Android devices (especially custom kernels) the node may exist
     * but lack the permissions needed by the app to open it.
     */
    private boolean canAccessNtsyncDevice() {
        java.io.File ntsyncDev = new java.io.File("/dev/ntsync");
        if (!ntsyncDev.exists()) {
            Log.d("XServerDisplayActivity", "NTSync: /dev/ntsync does not exist");
            return false;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(ntsyncDev)) {
            Log.d("XServerDisplayActivity", "NTSync: /dev/ntsync is accessible");
            return true;
        } catch (Exception e) {
            Log.d("XServerDisplayActivity", "NTSync: /dev/ntsync exists but is not accessible: " + e.getMessage());
            return false;
        }
    }

    /**
     * Normalizes synchronization environment variables.
     *
     * NTSync and ESync are the two synchronization methods available on Android.
     * FSync is compiled out of the Android Wine build (futex_waitv not reliable
     * on Android bionic), so it is always disabled here.
     *
     * Priority logic:
     *
     * 1. If the user explicitly set WINEESYNC=1 (without any NTSync variable):
     *    bypass NTSync detection entirely, use ESync.
     *
     * 2. If the user explicitly set WINENTSYNC=1 or PROTON_USE_NTSYNC=1:
     *    try NTSync. If /dev/ntsync is not accessible, fall back to ESync
     *    automatically (never drop to plain wineserver sync).
     *
     * 3. If no sync variables are set at all:
     *    auto-detect NTSync — use it if /dev/ntsync is accessible,
     *    otherwise fall back to ESync.
     */
    private void normalizeSyncEnvVars(com.winlator.cmod.runtime.wine.EnvVars envVars) {
        boolean esyncExplicit = "1".equals(envVars.get("WINEESYNC"));
        boolean ntSyncExplicit = "1".equals(envVars.get("WINENTSYNC"))
                || "1".equals(envVars.get("PROTON_USE_NTSYNC"));

        // FSync is always disabled on Android (compiled out of Wine build).
        envVars.remove("WINEFSYNC");
        envVars.put("PROTON_NO_FSYNC", "1");

        if (esyncExplicit && !ntSyncExplicit) {
            // User explicitly chose ESync — honour that, skip NTSync detection.
            envVars.remove("WINENTSYNC");
            envVars.remove("PROTON_USE_NTSYNC");
            envVars.put("WINEESYNC", "1");
            envVars.remove("PROTON_NO_ESYNC");
            Log.d("XServerDisplayActivity",
                    "Sync: user selected ESync — using ESync, skipping NTSync detection");
            return;
        }

        // Either NTSync was explicitly requested, or no sync vars are set at all.
        // In both cases: try NTSync first, fall back to ESync if unavailable.
        if (canAccessNtsyncDevice()) {
            // NTSync available — enable it, disable ESync (mutually exclusive).
            envVars.put("WINENTSYNC", "1");
            envVars.put("PROTON_USE_NTSYNC", "1");
            envVars.remove("WINEESYNC");
            envVars.put("PROTON_NO_ESYNC", "1");
            Log.d("XServerDisplayActivity",
                    "Sync: NTSync enabled (/dev/ntsync accessible) — disabled ESync");
        } else {
            // NTSync not available — fall back to ESync automatically.
            envVars.remove("WINENTSYNC");
            envVars.remove("PROTON_USE_NTSYNC");
            envVars.put("WINEESYNC", "1");
            envVars.remove("PROTON_NO_ESYNC");
            if (ntSyncExplicit) {
                Log.w("XServerDisplayActivity",
                        "Sync: NTSync requested but /dev/ntsync not accessible — falling back to ESync");
            } else {
                Log.d("XServerDisplayActivity",
                        "Sync: NTSync not available (no /dev/ntsync) — using ESync");
            }
        }
    }

    /**
     * Runs all pre-game setup: Mono installation, redistributables, and Steamless.
     * Each step is tracked per-container via container extras, so switching containers
     * will correctly re-install only what's missing for each master container.
     *
     * @param launcher The guest program launcher for running Wine commands
     * @param needsUnpacking Whether Steamless DRM stripping is needed
     * @param unpackFiles Whether to scan for additional exes to unpack
     */
    private void runPreGameSetup(GuestProgramLauncherComponent launcher,
                                  boolean needsUnpacking, boolean unpackFiles, boolean launchRealSteam) {
        // Step 1: Install Wine Mono if not already installed in this container
        boolean monoReady = installMonoIfNeeded(launcher);

        // Step 2: Install redistributables for this game if not already done in this container
        installRedistributablesIfNeeded(launcher);

        // Step 3: Run Steamless DRM stripping.
        // Runtime DRM Patcher (extra_dlls) handles most games automatically when
        // enabled, but Steamless is needed as a fallback for stubborn SteamStub
        // variants. Single-exe unpacking runs by default for Steam games when
        // needsUnpacking is set; the "Unpack Files" toggle also enables multi-exe
        // scanning so additional exes/dlls in the game dir are unpacked too.
        if (launchRealSteam) {
            Log.d("XServerDisplayActivity",
                    "Skipping Steamless/unpack flow because Launch Steam Client is enabled");
            return;
        }
        if (!monoReady) {
            Log.w("XServerDisplayActivity", "Skipping Steamless — Mono not installed yet, will retry next launch");
            return;
        }
        if (isSteamUnpackAlreadyHandled()) {
            Log.d("XServerDisplayActivity", "Skipping Steamless/unpack check; executable already handled");
            return;
        }
        boolean unpackedExeExists = doesUnpackedExeExist();
        if (needsUnpacking || unpackFiles || !unpackedExeExists) {
            if (needsUnpacking || !unpackedExeExists) {
                runSteamlessOnExe(launcher);
            } else {
                // Steamless already ran on a prior launch. Ensure the unpacked exe is active
                // in case something (e.g. mode switch) restored the original.
                ensureUnpackedExeActive();
            }
        }
    }

    private boolean isSteamUnpackAlreadyHandled() {
        if (shortcut == null || !"STEAM".equals(shortcut.getExtra("game_source"))) return false;
        try {
            int appId = Integer.parseInt(shortcut.getExtra("app_id"));
            String gameInstallPath = resolveSteamGameInstallPath(appId);
            if (gameInstallPath == null || gameInstallPath.isEmpty()) return false;

            SteamExecutableInfo executableInfo = resolveSteamExecutableInfo(appId, gameInstallPath);
            if (executableInfo == null) return false;

            File unpackedExe = new File(gameInstallPath, executableInfo.relativePath + ".unpacked.exe");
            File originalExe = new File(gameInstallPath, executableInfo.relativePath + ".original.exe");
            if (MarkerUtils.INSTANCE.hasMarker(gameInstallPath, Marker.STEAM_DRM_PATCHED)
                    && unpackedExe.exists()
                    && originalExe.exists()) {
                ensureUnpackedExeActive();
                return true;
            }

            File checkedMarker = new File(gameInstallPath, Marker.STEAM_DRM_UNPACK_CHECKED.getFileName());
            String expectedSignature = buildSteamUnpackSignature(executableInfo);
            String actualSignature = checkedMarker.exists() ? FileUtils.readString(checkedMarker) : null;
            return expectedSignature.equals(actualSignature);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Steamless handled-state check failed", e);
            return false;
        }
    }

    private void markSteamUnpackChecked(int appId, String gameInstallPath, String executablePath) {
        try {
            File exe = new File(gameInstallPath, executablePath.replace('\\', '/'));
            if (!exe.exists()) return;
            SteamExecutableInfo executableInfo =
                    new SteamExecutableInfo(executablePath.replace('\\', '/'), exe);
            File checkedMarker = new File(gameInstallPath, Marker.STEAM_DRM_UNPACK_CHECKED.getFileName());
            FileUtils.writeString(checkedMarker, buildSteamUnpackSignature(executableInfo));
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to write Steamless checked marker", e);
        }
    }

    private SteamExecutableInfo resolveSteamExecutableInfo(int appId, String gameInstallPath) {
        String executablePath = container.getExecutablePath();
        if (executablePath == null || executablePath.isEmpty()) {
            executablePath = com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.getInstalledExe(appId);
        }
        if (executablePath == null || executablePath.isEmpty()) return null;

        String relativePath = executablePath.replace('\\', '/');
        File exe = new File(gameInstallPath, relativePath);
        if (!exe.exists()) return null;
        return new SteamExecutableInfo(relativePath, exe);
    }

    private String buildSteamUnpackSignature(SteamExecutableInfo executableInfo) {
        return executableInfo.relativePath + "\n"
                + executableInfo.file.length() + "\n"
                + executableInfo.file.lastModified() + "\n";
    }

    private static class SteamExecutableInfo {
        final String relativePath;
        final File file;

        SteamExecutableInfo(String relativePath, File file) {
            this.relativePath = relativePath;
            this.file = file;
        }
    }

    /**
     * If Steamless has previously unpacked the exe, ensure the unpacked version is the active one.
     * This handles cases where the original exe was restored (e.g. switching between modes)
     * but Steamless doesn't need to run again since .unpacked.exe already exists.
     */
    private void ensureUnpackedExeActive() {
        if (shortcut == null || !"STEAM".equals(shortcut.getExtra("game_source"))) return;
        try {
            int appId = Integer.parseInt(shortcut.getExtra("app_id"));
            String gameInstallPath = resolveSteamGameInstallPath(appId);
            if (gameInstallPath == null || gameInstallPath.isEmpty()) return;

            String executablePath = container.getExecutablePath();
            if (executablePath == null || executablePath.isEmpty()) {
                executablePath = com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.getInstalledExe(appId);
            }
            if (executablePath == null || executablePath.isEmpty()) return;

            String unixPath = executablePath.replace('\\', '/');
            File exe = new File(gameInstallPath, unixPath);
            File unpackedExe = new File(gameInstallPath, unixPath + ".unpacked.exe");
            File originalExe = new File(gameInstallPath, unixPath + ".original.exe");

            // If both the unpacked exe and the original backup exist, the unpack was valid.
            // Always re-apply the unpacked version — a prior mode switch may have restored
            // the original, and comparing solely by file size is unreliable (some SteamStub
            // variants produce unpacked files of identical size).
            if (unpackedExe.exists() && originalExe.exists()) {
                java.nio.file.Files.copy(unpackedExe.toPath(), exe.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d("XServerDisplayActivity", "Restored unpacked exe (was reverted by mode switch)");
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "ensureUnpackedExeActive failed", e);
        }
    }

    /**
     * Checks whether a .unpacked.exe exists for the current game's executable.
     */
    private boolean doesUnpackedExeExist() {
        if (shortcut == null || !"STEAM".equals(shortcut.getExtra("game_source"))) return false;
        try {
            int appId = Integer.parseInt(shortcut.getExtra("app_id"));
            String gameInstallPath = resolveSteamGameInstallPath(appId);
            if (gameInstallPath == null || gameInstallPath.isEmpty()) return false;

            String executablePath = container.getExecutablePath();
            if (executablePath == null || executablePath.isEmpty()) {
                executablePath = com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.getInstalledExe(appId);
            }
            if (executablePath == null || executablePath.isEmpty()) return false;

            String unixPath = executablePath.replace('\\', '/');
            File unpackedExe = new File(gameInstallPath, unixPath + ".unpacked.exe");
            return unpackedExe.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Installs Wine Mono in this container if not already installed.
     * Tracked via container extra "mono_installed" so each master container
     * only installs Mono once.
     */
    private boolean installMonoIfNeeded(GuestProgramLauncherComponent launcher) {
        String winePath = wineInfo != null ? wineInfo.path : null;

        // Detect the required Mono version for this container's Wine build
        String requiredVersion = SteamClientManager.detectRequiredMonoVersion(this, winePath);
        if (requiredVersion == null) {
            Log.w("XServerDisplayActivity", "Could not detect required Mono version, skipping");
            return false;
        }

        // Check if the correct version is already installed in this container
        String installedVersion = container.getExtra("mono_version", null);
        if (requiredVersion.equals(installedVersion)) {
            Log.d("XServerDisplayActivity", "Mono v" + installedVersion + " already installed in container " + container.id + ", skipping");
            return true;
        }

        // Version mismatch or not installed — need to (re)install
        if (installedVersion != null) {
            Log.w("XServerDisplayActivity", "Mono version mismatch in container " + container.id
                    + ": installed v" + installedVersion + " but need v" + requiredVersion + " — reinstalling");
        }

        // Ensure the correct MSI is downloaded
        String monoWinePath = SteamClientManager.getMonoMsiWinePath(this, winePath);
        if (monoWinePath == null) {
            Log.w("XServerDisplayActivity", "Mono MSI not available (no internet?), will retry next launch");
            return false;
        }

        try {
            Log.d("XServerDisplayActivity", "Installing Wine Mono v" + requiredVersion
                    + " (" + monoWinePath + ") in container " + container.id + "...");
            String monoCmd = "wine msiexec /i " + monoWinePath + " && wineserver -k";
            launcher.execShellCommand(monoCmd);
            container.putExtra("mono_installed", "true");
            container.putExtra("mono_version", requiredVersion);
            container.saveData();
            Log.d("XServerDisplayActivity", "Mono v" + requiredVersion + " installed in container " + container.id);
            return true;
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Mono msiexec failed, will retry next launch", e);
            return false;
        }
    }

    /**
     * Installs _CommonRedist redistributables for the current game if not already done.
     * Tracked per game+container via container extra "redist_<appId>" so each
     * master container installs redistributables independently.
     *
     * Uses SteamBridge.getAppDirPath() for custom download folder paths.
     */
    private void installRedistributablesIfNeeded(GuestProgramLauncherComponent launcher) {
        if (shortcut == null || !"STEAM".equals(shortcut.getExtra("game_source"))) return;

        int appId;
        try {
            appId = Integer.parseInt(shortcut.getExtra("app_id"));
        } catch (Exception e) {
            return;
        }

        String redistKey = "redist_" + appId;
        String redistInstalled = container.getExtra(redistKey, "false");
        if ("true".equals(redistInstalled)) {
            Log.d("XServerDisplayActivity", "Redistributables for appId=" + appId
                    + " already installed in container " + container.id + ", skipping");
            return;
        }

        // Find the game's _CommonRedist directory using custom download path
        String gameInstallPath = resolveSteamGameInstallPath(appId);
        if (gameInstallPath == null || gameInstallPath.isEmpty()) return;

        File commonRedistDir = new File(gameInstallPath, "_CommonRedist");
        if (!commonRedistDir.exists() || !commonRedistDir.isDirectory()) {
            Log.d("XServerDisplayActivity", "No _CommonRedist found for appId=" + appId
                    + " at " + commonRedistDir.getPath());
            // Mark as done even if no redist found (skip on future launches)
            container.putExtra(redistKey, "true");
            container.saveData();
            return;
        }

        Log.d("XServerDisplayActivity", "Installing redistributables for appId=" + appId
                + " in container " + container.id + "...");

        // Walk _CommonRedist subdirs and find installer executables
        // Typical structure: _CommonRedist/vcredist/2019/vc_redist.x64.exe
        //                    _CommonRedist/DirectX/Jun2010/DXSETUP.exe
        int installed = 0;
        try {
            File[] categories = commonRedistDir.listFiles();
            if (categories != null) {
                for (File category : categories) {
                    if (!category.isDirectory()) continue;
                    File[] versions = category.listFiles();
                    if (versions == null) continue;
                    for (File versionDir : versions) {
                        if (!versionDir.isDirectory()) continue;
                        // Find the main installer exe in this version dir
                        File[] exes = versionDir.listFiles((dir, name) ->
                                name.toLowerCase().endsWith(".exe"));
                        if (exes == null || exes.length == 0) continue;

                        for (File exe : exes) {
                            String exeName = exe.getName().toLowerCase();
                            // Skip known non-installer files
                            if (exeName.startsWith("unins") || exeName.equals("detect.exe")) continue;

                            String winPath = WineUtils.getWindowsPath(container, exe.getAbsolutePath());

                            try {
                                Log.d("XServerDisplayActivity", "Running redistributable: " + winPath);
                                // Run with /quiet /norestart flags for silent install
                                String cmd;
                                if (exeName.contains("dxsetup")) {
                                    cmd = "wine \"" + winPath + "\" /silent";
                                } else if (exeName.contains("vc_redist") || exeName.contains("vcredist")) {
                                    cmd = "wine \"" + winPath + "\" /quiet /norestart";
                                } else if (exeName.endsWith(".msi")) {
                                    cmd = "wine msiexec /i \"" + winPath + "\" /quiet /norestart";
                                } else {
                                    cmd = "wine \"" + winPath + "\" /quiet /norestart";
                                }
                                launcher.execShellCommand(cmd);
                                installed++;
                            } catch (Exception e) {
                                Log.w("XServerDisplayActivity",
                                        "Redistributable install failed: " + winPath, e);
                            }
                        }
                    }
                }
            }

            // Kill wineserver after installing redists
            if (installed > 0) {
                try {
                    launcher.execShellCommand("wineserver -k");
                } catch (Exception e) {
                    Log.w("XServerDisplayActivity", "wineserver -k failed after redist install", e);
                }
            }

            Log.d("XServerDisplayActivity", "Installed " + installed
                    + " redistributable(s) for appId=" + appId + " in container " + container.id);
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Redistributable installation failed", e);
        }

        // Mark as done for this game+container combo
        container.putExtra(redistKey, "true");
        container.saveData();
    }

    /**
     * P2: Runs Steamless DRM stripping on the game executable.
     * Called via the preUnpack callback after box64 is ready.
     *
     * Follows the official Steamless CLI flow (https://github.com/atom0s/Steamless):
     *   1. Loads unpacker plugins from Plugins/ subdirectory
     *   2. Each plugin attempts to detect and unpack SteamStub variants (v1.0-v3.1)
     *   3. On success, writes "{filename}.unpacked.exe" next to the original
     *   4. Returns exit code 0 on success, 1 on failure
     *
     * We pass --realign and --recalcchecksum to produce a valid PE post-unpack,
     * matching best-practice usage documented in Steamless.
     *
     * Wine Mono must be installed first (handled by installMonoIfNeeded() in the
     * pre-game setup flow) since Steamless.CLI.exe is a .NET Framework application.
     */
    private void runSteamlessOnExe(GuestProgramLauncherComponent launcher) {
        if (shortcut == null || !"STEAM".equals(shortcut.getExtra("game_source"))) return;
        int appId;
        try {
            appId = Integer.parseInt(shortcut.getExtra("app_id"));
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Invalid app_id for Steamless", e);
            return;
        }

        String gameInstallPath = resolveSteamGameInstallPath(appId);
        if (gameInstallPath == null || gameInstallPath.isEmpty()) return;

        // Extract Steamless.CLI.exe + Plugins/ if not present
        File steamlessDir = new File(imageFs.getRootDir(), "Steamless");
        File steamlessCli = new File(steamlessDir, "Steamless.CLI.exe");
        File pluginsDir = new File(steamlessDir, "Plugins");
        if (!steamlessCli.exists() || !pluginsDir.exists()) {
            try {
                steamlessDir.mkdirs();
                TarCompressorUtils.extract(
                        TarCompressorUtils.Type.ZSTD,
                        this, "extras.tzst", imageFs.getRootDir());
                // Ensure extracted binaries are executable
                com.winlator.cmod.shared.io.FileUtils.chmod(steamlessCli, 0755);
                Log.d("XServerDisplayActivity", "Extracted Steamless CLI + Plugins to " + steamlessDir);
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Failed to extract Steamless", e);
                return;
            }
        }

        // Validate Plugins directory has unpacker DLLs — Steamless CLI will fail
        // with "No plugins were loaded" if this directory is missing or empty
        if (!pluginsDir.exists() || pluginsDir.list() == null || pluginsDir.list().length == 0) {
            Log.e("XServerDisplayActivity", "Steamless Plugins/ directory is missing or empty — cannot unpack");
            return;
        }

        // Find the game executable and run Steamless on it
        String executablePath = container.getExecutablePath();
        if (executablePath == null || executablePath.isEmpty()) {
            executablePath = com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.getInstalledExe(appId);
        }
        if (executablePath == null || executablePath.isEmpty()) {
            Log.w("XServerDisplayActivity", "No executable path found for Steamless");
            return;
        }

        File batchFile = null;
        try {
            File hostExe = new File(gameInstallPath, executablePath.replace('\\', '/'));

            // Build Windows path for Steamless using the C:\WinNative\Games symlink
            // (most reliable — avoids drive-mapping gaps where hostPathToRootWinePath
            // skips the A: drive and falls through to Z:\).
            String windowsPath = com.winlator.cmod.runtime.wine.WineUtils.getDriveCGameWindowsPath(
                    container, "STEAM", gameInstallPath, hostExe.getAbsolutePath());
            if (windowsPath == null || windowsPath.isEmpty()) {
                // Fallback to generic drive-mapped path resolution
                windowsPath = com.winlator.cmod.runtime.wine.WineUtils.hostPathToRootWinePath(container, hostExe.getAbsolutePath());
            }
            Log.d("XServerDisplayActivity", "Steamless: resolved windowsPath=" + windowsPath
                    + " (hostExe=" + hostExe.getAbsolutePath() + ")");

            // Create batch file to handle paths with spaces.
            batchFile = new File(imageFs.getRootDir(), "tmp/steamless_wrapper.bat");
            batchFile.getParentFile().mkdirs();
            String batchContent = "@echo off\r\n"
                    + "z:\\Steamless\\Steamless.CLI.exe \"" + windowsPath + "\"\r\n"
                    + "echo STEAMLESS_EXIT_CODE=%ERRORLEVEL%\r\n";
            com.winlator.cmod.shared.io.FileUtils.writeString(batchFile, batchContent);

            Log.d("XServerDisplayActivity", "Steamless: running on " + windowsPath + " (exe=" + executablePath + ")");
            String slCmd = "wine z:\\tmp\\steamless_wrapper.bat";
            String slOutput = launcher.execShellCommand(slCmd);
            Log.d("XServerDisplayActivity", "Steamless CLI output: " + slOutput);

            // Steamless CLI prints "Successfully unpacked file!" on success (exit code 0).
            // If this string is absent, the unpacking failed — do NOT swap files.
            boolean steamlessSuccess = slOutput != null
                    && slOutput.toLowerCase().contains("successfully unpacked");

            // Handle file swap: .unpacked.exe -> exe, exe -> .original.exe
            String unixPath = executablePath.replace('\\', '/');
            File exe = new File(gameInstallPath, unixPath);
            File unpackedExe = new File(gameInstallPath, unixPath + ".unpacked.exe");
            File originalExe = new File(gameInstallPath, unixPath + ".original.exe");

            Log.d("XServerDisplayActivity", "Steamless: checking exe=" + exe.getAbsolutePath()
                    + " exists=" + exe.exists() + " unpacked=" + unpackedExe.getAbsolutePath()
                    + " exists=" + unpackedExe.exists() + " cliSuccess=" + steamlessSuccess);

            if (steamlessSuccess && exe.exists() && unpackedExe.exists()) {
                // Backup the original DRM-protected exe before overwriting
                if (!originalExe.exists()) {
                    java.nio.file.Files.copy(exe.toPath(), originalExe.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Log.d("XServerDisplayActivity", "Steamless: backed up original exe as " + originalExe.getName());
                }
                // Replace the active exe with the unpacked version
                java.nio.file.Files.copy(unpackedExe.toPath(), exe.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d("XServerDisplayActivity", "Steamless: swapped exe with unpacked version");

                // Set the STEAM_DRM_PATCHED marker so mode switches know we've patched
                com.winlator.cmod.feature.stores.steam.utils.MarkerUtils.INSTANCE.addMarker(
                        gameInstallPath, com.winlator.cmod.feature.stores.steam.enums.Marker.STEAM_DRM_PATCHED);

                // Unpacking succeeded — clear the flag and persist
                launcher.execShellCommand("wineserver -k");
                container.setNeedsUnpacking(false);
                container.saveData();
            } else if (!steamlessSuccess && !unpackedExe.exists()) {
                // Steamless ran but couldn't unpack. Distinguish between:
                //  - Definitive: "All unpackers failed" = game doesn't use SteamStub DRM.
                //    Stop retrying to avoid wasting ~5s on every launch.
                //  - Transient: Wine/Mono crash, missing deps, etc. = keep retrying.
                boolean allUnpackersFailed = slOutput != null
                        && slOutput.toLowerCase().contains("all unpackers failed");

                if (allUnpackersFailed) {
                    Log.w("XServerDisplayActivity",
                            "Steamless: game does not use SteamStub DRM (all unpackers failed). "
                            + "Disabling Legacy DRM for this game to avoid future overhead.");
                    launcher.execShellCommand("wineserver -k");
                    markSteamUnpackChecked(appId, gameInstallPath, executablePath);
                    container.setNeedsUnpacking(false);
                    container.saveData();
                } else {
                    Log.w("XServerDisplayActivity",
                            "Steamless: transient failure (CLI ran but no .unpacked.exe), will retry next launch");
                }
            } else if (!steamlessSuccess && unpackedExe.exists()) {
                // Edge case: .unpacked.exe exists from a prior run but CLI reported failure
                // this time (e.g., file was already unpacked). Use the existing unpacked file.
                if (!originalExe.exists() && exe.exists()) {
                    java.nio.file.Files.copy(exe.toPath(), originalExe.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                java.nio.file.Files.copy(unpackedExe.toPath(), exe.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d("XServerDisplayActivity", "Steamless: used existing .unpacked.exe from prior run");

                com.winlator.cmod.feature.stores.steam.utils.MarkerUtils.INSTANCE.addMarker(
                        gameInstallPath, com.winlator.cmod.feature.stores.steam.enums.Marker.STEAM_DRM_PATCHED);
                launcher.execShellCommand("wineserver -k");
                container.setNeedsUnpacking(false);
                container.saveData();
            }
        } catch (Exception e) {
            // Don't set needsUnpacking=false — allow retry on next launch
            Log.e("XServerDisplayActivity", "Steamless execution failed, will retry next launch", e);
        } finally {
            // Always clean up the batch wrapper
            if (batchFile != null && batchFile.exists()) batchFile.delete();
        }
    }

    public XServer getXServer() {
        return xServer;
    }

    /**
     * Generates steam_interfaces.txt by scanning the backed-up original DLL for
     * Steam interface version strings (e.g., SteamUser023, SteamApps008).
     * Checks .orig backup first, then falls back to the DLL itself.
     */
    private void generateSteamInterfacesFile(File dir, String dllName) {
        File interfacesFile = new File(dir, "steam_interfaces.txt");
        if (interfacesFile.exists()) return;

        // Prefer the .orig backup (original DLL before steampipe replacement)
        File dllToScan = new File(dir, dllName + ".orig");
        if (!dllToScan.exists()) {
            // Fall back to .original (legacy naming from older WinNative builds)
            dllToScan = new File(dir, dllName + ".original");
        }
        if (!dllToScan.exists()) {
            dllToScan = new File(dir, dllName);
        }
        if (!dllToScan.exists()) return;

        generateSteamInterfacesFromDll(dir, dllToScan);
    }
    
    private void setSteamClientVisibility(boolean visible) {
        setSteamClientVisibility(visible, false);
    }

    private void setSteamClientVisibility(boolean visible, boolean coldClientMode) {
        if (container == null) return;
        updateSteamDirectoryVisibility(visible, coldClientMode);
        updateSteamRegistryVisibility(visible);
    }

    private void updateSteamDirectoryVisibility(boolean visible) {
        updateSteamDirectoryVisibility(visible, false);
    }

    private void updateSteamDirectoryVisibility(boolean visible, boolean coldClientMode) {
        if (container == null) return;

        File steamLink = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
        File pristineSteamStore = getSharedSteamStore();
        File coldClientStore = getSharedColdClientStore();
        File target = coldClientMode ? coldClientStore : pristineSteamStore;
        File previousSteamStore = new File(imageFs.getRootDir(), PREVIOUS_STEAM_CLIENT_STORE_RELATIVE_PATH);
        File previousContainerSteamStore = new File(container.getRootDir(), PREVIOUS_CONTAINER_STEAM_CLIENT_STORE_RELATIVE_PATH);
        File legacySteamStore = new File(container.getRootDir(), LEGACY_STEAM_CLIENT_STORE_RELATIVE_PATH);

        try {
            moveSteamDirectoryIntoBackingStore(steamLink, pristineSteamStore);
            migrateLegacySteamStoreIfNeeded(previousSteamStore, pristineSteamStore);
            migrateLegacySteamStoreIfNeeded(previousContainerSteamStore, pristineSteamStore);
            migrateLegacySteamStoreIfNeeded(legacySteamStore, pristineSteamStore);

            if (visible) {
                if (!target.exists()) {
                    target.mkdirs();
                }
                // Unconditionally re-point the symlink. Cheap, and guarantees the target
                // matches the current mode (ColdClient sidecar vs. pristine Steam store).
                if (steamLink.exists()) {
                    FileUtils.delete(steamLink);
                }
                FileUtils.symlink(target, steamLink);
                Log.d("XServerDisplayActivity",
                        "Steam symlink → " + (coldClientMode ? "coldclient-store" : "steam-client-store")
                                + " at " + steamLink.getAbsolutePath());
            } else {
                if (steamLink.exists()) {
                    FileUtils.delete(steamLink);
                    Log.d("XServerDisplayActivity", "Removed visible Steam root for non-Steam launch");
                }
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Error updating Steam directory visibility", e);
        }
    }

    private File getSharedSteamStore() {
        if (imageFs != null) {
            return new File(imageFs.getRootDir(), STEAM_CLIENT_STORE_RELATIVE_PATH);
        }
        return new File(getFilesDir(), "imagefs/" + STEAM_CLIENT_STORE_RELATIVE_PATH);
    }

    private File getSharedColdClientStore() {
        if (imageFs != null) {
            return new File(imageFs.getRootDir(), COLDCLIENT_STORE_RELATIVE_PATH);
        }
        return new File(getFilesDir(), "imagefs/" + COLDCLIENT_STORE_RELATIVE_PATH);
    }

    /**
     * Populate the ColdClient sidecar store (.shared/coldclient-store/) by extracting
     * experimental-drm.tzst into it. Idempotent: returns true immediately if the loader
     * is already present.
     *
     * The archive is laid out for extraction to imageFs.rootDir with the Goldberg/loader
     * files under home/xuser/.wine/drive_c/Program Files (x86)/Steam/. Since the container's
     * Steam symlink is pointed at the coldclient store before this is called, extraction
     * writes through the symlink directly into the sidecar — keeping the real steam-client-store
     * pristine forever.
     */
    private boolean ensureColdClientStore() {
        File cstore = getSharedColdClientStore();
        File loader = new File(cstore, "steamclient_loader_x64.exe");
        File stub = new File(cstore, "steamclient64.dll");
        if (loader.exists() && loader.length() > 0 && stub.exists() && stub.length() > 0) {
            return true;
        }

        // Make sure the archive is on disk (downloaded from components if necessary)
        if (!SteamBridge.ensureColdClientSupportReady(this)) {
            Log.w("XServerDisplayActivity", "ensureColdClientStore: experimental-drm.tzst not available");
            return false;
        }
        File expFile = new File(getFilesDir(), "experimental-drm.tzst");
        if (!expFile.exists()) {
            Log.w("XServerDisplayActivity", "ensureColdClientStore: experimental-drm.tzst missing from filesDir");
            return false;
        }

        cstore.mkdirs();
        try {
            com.winlator.cmod.shared.io.TarCompressorUtils.extract(
                    com.winlator.cmod.shared.io.TarCompressorUtils.Type.ZSTD,
                    expFile, imageFs.getRootDir(), null);
            Log.d("XServerDisplayActivity",
                    "ensureColdClientStore: extracted experimental-drm.tzst into coldclient sidecar");
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "ensureColdClientStore: extraction failed", e);
            return false;
        }

        return loader.exists() && stub.exists();
    }

    private void migrateLegacySteamStoreIfNeeded(File legacySteamStore, File steamStore) {
        if (legacySteamStore == null || steamStore == null || !legacySteamStore.exists()) return;

        File parentDir = steamStore.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (!steamStore.exists()) {
            if (legacySteamStore.renameTo(steamStore)) {
                Log.d("XServerDisplayActivity", "Migrated legacy Steam backing store to hidden location");
                return;
            }

            if (!steamStore.mkdirs()) {
                Log.w("XServerDisplayActivity", "Failed to create hidden Steam backing store during legacy migration");
                return;
            }
        }

        if (!steamStore.isDirectory()) {
            Log.w("XServerDisplayActivity", "Hidden Steam backing store is not a directory");
            return;
        }

        if (!FileUtils.copy(legacySteamStore, steamStore)) {
            Log.w("XServerDisplayActivity", "Failed to copy legacy Steam backing store into hidden location");
            return;
        }

        if (FileUtils.delete(legacySteamStore)) {
            Log.d("XServerDisplayActivity", "Removed legacy Windows-visible Steam backing store");
        } else {
            Log.w("XServerDisplayActivity", "Failed to remove legacy Windows-visible Steam backing store");
        }
    }

    private void moveSteamDirectoryIntoBackingStore(File steamLink, File steamStore) {
        if (steamLink == null || steamStore == null) return;
        if (!steamLink.exists() || FileUtils.isSymlink(steamLink)) return;

        File parentDir = steamStore.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (!steamStore.exists()) {
            if (steamLink.renameTo(steamStore)) {
                Log.d("XServerDisplayActivity", "Migrated Steam directory to backing store: " + steamStore.getAbsolutePath());
                return;
            }
            Log.w("XServerDisplayActivity", "Failed to rename Steam directory into backing store, falling back to copy");
        }

        if (!steamStore.exists() && !steamStore.mkdirs()) {
            Log.w("XServerDisplayActivity", "Unable to create Steam backing store: " + steamStore.getAbsolutePath());
            return;
        }

        if (!steamStore.isDirectory()) {
            Log.w("XServerDisplayActivity", "Steam backing store is not a directory: " + steamStore.getAbsolutePath());
            return;
        }

        if (!FileUtils.copy(steamLink, steamStore)) {
            Log.w("XServerDisplayActivity", "Failed to copy Steam directory contents into backing store");
            return;
        }

        if (FileUtils.delete(steamLink)) {
            Log.d("XServerDisplayActivity", "Collapsed visible Steam directory into backing store");
        } else {
            Log.w("XServerDisplayActivity", "Failed to remove visible Steam directory after backing-store copy");
        }
    }

    private void updateSteamRegistryVisibility(boolean visible) {
        if (container == null) return;
        // container.getRootDir() already points at the per-container home dir
        // (.../imagefs/home/xuser-N). ImageFs.WINEPREFIX is the default absolute
        // path "/home/xuser/.wine" and concatenating the two produces a bogus
        // .../home/xuser-N/home/xuser/.wine path that never exists — writes
        // there throw ENOENT and silently swallow, making this whole routine a
        // no-op. The wine prefix lives directly at "<rootDir>/.wine".
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
        File userBackupFile = new File(container.getRootDir(), ".wine/" + STEAM_USER_REGISTRY_BACKUP_FILE);
        File systemBackupFile = new File(container.getRootDir(), ".wine/" + STEAM_SYSTEM_REGISTRY_BACKUP_FILE);
        if (!visible) {
            try {
                forceHideSteamRegistry(userRegFile, userBackupFile, STEAM_REGISTRY_KEY);
                forceHideSteamRegistry(systemRegFile, systemBackupFile, STEAM_SYSTEM_REGISTRY_KEYS);
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Error updating Steam registry visibility", e);
            }
            return;
        }

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            if (visible) {
                restoreRegistrySubtrees(userRegFile, userBackupFile, STEAM_REGISTRY_KEY);
                restoreRegistrySubtrees(systemRegFile, systemBackupFile, STEAM_SYSTEM_REGISTRY_KEYS);
                registryEditor.removeKey(STEAM_REGISTRY_KEY, true);
                String backupContent = userBackupFile.isFile() ? FileUtils.readString(userBackupFile) : null;
                if (backupContent != null && !backupContent.trim().isEmpty()) {
                    if (registryEditor.appendRawContent(backupContent)) {
                        Log.d("XServerDisplayActivity", "Restored Steam registry subtree from backup");
                    } else {
                        Log.w("XServerDisplayActivity", "Failed to restore Steam registry subtree from backup");
                    }
                } else {
                    registryEditor.setCreateKeyIfNotExist(true);
                    registryEditor.setStringValue(STEAM_REGISTRY_KEY, "SteamExe", STEAM_EXE_PATH);
                    registryEditor.setStringValue(STEAM_REGISTRY_KEY, "SteamPath", STEAM_ROOT_PATH);
                    registryEditor.setStringValue(STEAM_REGISTRY_KEY, "InstallPath", STEAM_ROOT_PATH);

                    String autoLoginUser = PrefManager.INSTANCE.getUsername();
                    if (autoLoginUser != null && !autoLoginUser.isEmpty()) {
                        registryEditor.setStringValue(STEAM_REGISTRY_KEY, "AutoLoginUser", autoLoginUser);
                    } else {
                        registryEditor.removeValue(STEAM_REGISTRY_KEY, "AutoLoginUser");
                    }
                    Log.d("XServerDisplayActivity", "Created default Steam registry subtree");
                }
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Error updating Steam registry visibility", e);
        }
    }

    private void forceHideSteamRegistry(File registryFile, File backupFile, String... keys) {
        String rawRegistry = FileUtils.readString(registryFile);
        if (rawRegistry == null) rawRegistry = "";

        String backupContent = extractRegistrySubtrees(rawRegistry, keys);
        if (!backupContent.trim().isEmpty()) {
            FileUtils.writeString(backupFile, backupContent.trim() + "\n");
            Log.d("XServerDisplayActivity", "Backed up Steam registry subtrees from " + registryFile.getName());
        }

        String sanitizedRegistry = sanitizeSteamRegistryContent(rawRegistry, keys);
        FileUtils.writeString(registryFile, sanitizedRegistry);
        Log.d("XServerDisplayActivity", "Force-sanitized Steam registry state in " + registryFile.getName());
    }

    private String sanitizeSteamRegistryContent(String registryContent, String... keys) {
        String sanitized = removeRegistrySubtrees(registryContent, keys);
        return scrubRegistryLinePatterns(sanitized, STEAM_REGISTRY_LINE_PATTERNS);
    }

    private String scrubRegistryLinePatterns(String content, String... patterns) {
        if (content == null || content.isEmpty() || patterns == null || patterns.length == 0) {
            return content != null ? content : "";
        }
        String[] lines = content.split("\n", -1);
        StringBuilder rebuilt = new StringBuilder();
        for (String line : lines) {
            boolean remove = false;
            String normalizedLine = line.toLowerCase(Locale.ROOT);
            for (String pattern : patterns) {
                if (normalizedLine.contains(pattern)) {
                    remove = true;
                    break;
                }
            }
            if (!remove) {
                rebuilt.append(line).append('\n');
            }
        }
        return rebuilt.toString();
    }

    private void hideRegistrySubtrees(File registryFile, File backupFile, String... keys) {
        String rawRegistry = FileUtils.readString(registryFile);
        if (rawRegistry == null) rawRegistry = "";

        String backupContent = extractRegistrySubtrees(rawRegistry, keys);
        if (!backupContent.trim().isEmpty()) {
            FileUtils.writeString(backupFile, backupContent.trim() + "\n");
            Log.d("XServerDisplayActivity", "Backed up Steam registry subtrees from " + registryFile.getName());
        }

        String strippedRegistry = removeRegistrySubtrees(rawRegistry, keys);
        if (!strippedRegistry.equals(rawRegistry)) {
            FileUtils.writeString(registryFile, strippedRegistry);
            Log.d("XServerDisplayActivity", "Removed Steam registry subtrees from " + registryFile.getName());
        } else {
            Log.d("XServerDisplayActivity", "Steam registry subtrees already hidden in " + registryFile.getName());
        }
    }

    private void restoreRegistrySubtrees(File registryFile, File backupFile, String... keys) {
        String rawRegistry = FileUtils.readString(registryFile);
        if (rawRegistry == null) rawRegistry = "";

        String strippedRegistry = removeRegistrySubtrees(rawRegistry, keys);
        if (!strippedRegistry.equals(rawRegistry)) {
            FileUtils.writeString(registryFile, strippedRegistry);
        }

        if (!backupFile.isFile()) return;
        String backupContent = FileUtils.readString(backupFile);
        if (backupContent == null || backupContent.trim().isEmpty()) return;

        String merged = FileUtils.readString(registryFile);
        if (merged == null) merged = "";
        if (!merged.endsWith("\n") && !merged.isEmpty()) merged += "\n";
        merged += backupContent.trim() + "\n";
        FileUtils.writeString(registryFile, merged);
    }

    private String extractRegistrySubtrees(String registryContent, String... keys) {
        if (registryContent == null || registryContent.isEmpty() || keys == null || keys.length == 0) {
            return "";
        }

        StringBuilder extracted = new StringBuilder();
        for (String key : keys) {
            String subtree = extractRegistrySubtree(registryContent, key);
            if (subtree != null && !subtree.trim().isEmpty()) {
                if (extracted.length() > 0 && extracted.charAt(extracted.length() - 1) != '\n') {
                    extracted.append('\n');
                }
                extracted.append(subtree.trim()).append('\n');
            }
        }
        return extracted.toString();
    }

    private String removeRegistrySubtrees(String registryContent, String... keys) {
        String updated = registryContent != null ? registryContent : "";
        if (keys == null) return updated;
        for (String key : keys) {
            updated = removeRegistrySubtree(updated, key);
        }
        return updated;
    }

    private String extractRegistrySubtree(String registryContent, String key) {
        if (registryContent == null || registryContent.isEmpty() || key == null || key.isEmpty()) {
            return "";
        }

        String escapedKey = key.replace("\\", "\\\\");
        String prefix = "[" + escapedKey;
        StringBuilder extracted = new StringBuilder();
        boolean capturing = false;
        String[] lines = registryContent.split("\n", -1);
        for (String line : lines) {
            if (line.startsWith("[")) {
                if (capturing && !line.startsWith(prefix)) {
                    break;
                }
                if (!capturing && line.startsWith(prefix)) {
                    capturing = true;
                }
            }
            if (capturing) {
                extracted.append(line).append('\n');
            }
        }
        return extracted.toString();
    }

    private String removeRegistrySubtree(String registryContent, String key) {
        if (registryContent == null || registryContent.isEmpty() || key == null || key.isEmpty()) {
            return registryContent != null ? registryContent : "";
        }

        String escapedKey = key.replace("\\", "\\\\");
        String prefix = "[" + escapedKey;
        StringBuilder rebuilt = new StringBuilder();
        boolean capturing = false;
        String[] lines = registryContent.split("\n", -1);
        for (String line : lines) {
            if (line.startsWith("[")) {
                if (capturing && !line.startsWith(prefix)) {
                    capturing = false;
                }
                if (!capturing && line.startsWith(prefix)) {
                    capturing = true;
                }
            }
            if (!capturing) {
                rebuilt.append(line).append('\n');
            }
        }
        return rebuilt.toString();
    }

    /**
     * Sets up the Steam environment: steamapps/common symlink, ACF manifest,
     * Wine registry entries for Steam paths, and steam.cfg for bootstrap inhibit.
     */
    private void setupSteamEnvironment(int appId, File gameDir) {
        try {
            File winePrefix = container.getRootDir();
            File steamDir = new File(winePrefix, ".wine/drive_c/Program Files (x86)/Steam");
            steamDir.mkdirs();
            boolean launchRealSteamMode = shortcut != null
                    ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                    : container.isLaunchRealSteam();

            if (launchRealSteamMode) {
                // Seed or heal the shared Steam client store before launch.
                // isSteamInstalled() only verifies file existence; isSharedSteamStorePristine()
                // adds a size check that catches a Goldberg-stub overwrite (the #1 cause of
                // Steam's "please reinstall" dialog after switching from ColdClient).
                boolean needsSeed = !SteamBridge.isSteamInstalled(this);
                boolean contaminated = !needsSeed && !SteamUtils.isSharedSteamStorePristine(this);
                if (needsSeed || contaminated) {
                    Log.d("XServerDisplayActivity",
                            "Real Steam mode: " + (contaminated
                                    ? "shared store contaminated (Goldberg stubs detected), force-extracting steam.tzst"
                                    : "seeding shared Steam client store (one-shot)"));
                    if (!SteamBridge.forceExtractSteam(this)) {
                        Log.w("XServerDisplayActivity", "Real Steam mode: failed to seed/heal Steam client store");
                    }
                }

                // Unconditionally inhibit Steam's bootstrapper. This is what keeps the client
                // from trying to self-update over the network on launch (the cause of the long
                // cryptnet hangs we've seen in Wine).
                File steamCfg = new File(steamDir, "steam.cfg");
                FileUtils.writeString(steamCfg, "BootStrapperInhibitAll=Enable\nBootStrapperForceSelfUpdate=False\n");

                // Seed the package/.installed completion markers so the Steam bootstrap stub,
                // if it runs at all, sees a finished install and doesn't try to re-download.
                File packageDir = new File(steamDir, "package");
                if (!packageDir.exists()) packageDir.mkdirs();
                for (String marker : new String[]{"steam_client_win32.installed", "steam_client_win64.installed"}) {
                    File mf = new File(packageDir, marker);
                    if (!mf.exists() || mf.length() == 0) {
                        FileUtils.writeString(mf, "1\n");
                    }
                }
            }

            File steamappsDir = new File(steamDir, "steamapps");
            File commonDir = new File(steamappsDir, "common");
            commonDir.mkdirs();
            WineUtils.ensureSteamappsCommonSymlink(container, gameDir.getAbsolutePath());

            // Create full ACF manifest via SteamUtils.createAppManifest which includes
            // InstalledDepots, buildId, SizeOnDisk, UserConfig/language.
            // Falls back gracefully if SteamService has no appInfo for this game.
            SteamUtils.createAppManifest(this, appId);

            // SteamUtils.createAppManifest writes the ACF to imageFs.wineprefix which may differ
            // from the container's Steam dir (which symlinks to .shared/steam-client-store).
            // Real Steam reads from the shared store, so ensure the ACF also exists there.
            File defaultAcf = new File(imageFs.getRootDir(),
                    ImageFs.WINEPREFIX + "/drive_c/Program Files (x86)/Steam/steamapps/appmanifest_" + appId + ".acf");
            File containerAcf = new File(steamappsDir, "appmanifest_" + appId + ".acf");
            if (defaultAcf.exists() && !containerAcf.exists()) {
                try {
                    java.nio.file.Files.copy(defaultAcf.toPath(), containerAcf.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Log.d("XServerDisplayActivity", "Copied ACF manifest to container steamapps dir");
                } catch (Exception e) {
                    Log.w("XServerDisplayActivity", "Failed to copy ACF to container steamapps", e);
                }
            }

            ensureSteamLibraryFoldersConfig(steamDir, steamappsDir);

            // Ensure the Steamworks Common Redistributables ACF always exists as a fallback
            // (createAppManifest only creates it when there are shared depots in the manifest).
            File steamworksAcf = new File(steamappsDir, "appmanifest_228980.acf");
            if (!steamworksAcf.exists()) {
                String steamworksAcfContent = "\"AppState\"\n" +
                        "{\n" +
                        "\t\"appid\"\t\t\"228980\"\n" +
                        "\t\"Universe\"\t\t\"1\"\n" +
                        "\t\"name\"\t\t\"Steamworks Common Redistributables\"\n" +
                        "\t\"StateFlags\"\t\t\"4\"\n" +
                        "\t\"installdir\"\t\t\"Steamworks Shared\"\n" +
                        "\t\"buildid\"\t\t\"1\"\n" +
                        "\t\"BytesToDownload\"\t\t\"0\"\n" +
                        "\t\"BytesDownloaded\"\t\t\"0\"\n" +
                        "}\n";
                FileUtils.writeString(steamworksAcf, steamworksAcfContent);
            }

            // Write loginusers.vdf with full OAuth tokens and set Wine registry for Steam paths.
            // autoLoginUserChanges uses SteamService for proper token format.
            try {
                SteamUtils.autoLoginUserChanges(imageFs);
                Log.d("XServerDisplayActivity", "autoLoginUserChanges complete");
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "autoLoginUserChanges failed, falling back", e);
            }

            // Skip first-time redistributable setup by marking them installed in system.reg
            skipFirstTimeSteamSetup(winePrefix);

            // Derive account info from encrypted PrefManager storage and refresh localconfig/acf.
            long steamIdLong = com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE.getSteamUserSteamId64();
            String steamId64 = steamIdLong > 0 ? String.valueOf(steamIdLong) : "76561198000000000";
            int steamAccountId = com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE.getSteamUserAccountId();
            String steamUserDataId = steamAccountId > 0 ? String.valueOf(steamAccountId) : steamId64;
            reconcileSteamUserdata(steamDir, steamUserDataId, steamId64);

            SteamUtils.updateOrModifyLocalConfig(imageFs, container, String.valueOf(appId), steamUserDataId);

            // Create lightweight Steam config to reduce resource usage
            setupLightweightSteamConfig(steamDir, steamUserDataId);

            // Pre-launch Steam Cloud handshake via javasteam. This is the key fix for
            // arm64ec Wine: the reference implementation calls beginLaunchApp BEFORE
            // invoking steam.exe, which triggers SteamAutoCloud.syncUserFiles + the
            // server-side signalAppLaunchIntent(ignorePendingOperations=true). That tells
            // Valve's servers "I'm launching, ignore/clear any pending ops from this
            // machine" — so when Steam's own AutoCloud runs at -applaunch time, it sees
            // no pending operations and skips the CEF-dependent conflict dialog.
            //
            // Runs on this background thread (setupSteamEnvironment is called off the
            // UI thread). Timeout guards against network-stall; failure falls through to
            // launch-as-before.
            if (launchRealSteamMode) {
                java.util.concurrent.CountDownLatch syncLatch = new java.util.concurrent.CountDownLatch(1);
                final int appIdForSync = appId;
                Thread preLaunchSync = new Thread(() -> {
                    try {
                        com.winlator.cmod.feature.stores.steam.service.SteamService.Companion
                                .beginLaunchAppBlocking(
                                        XServerDisplayActivity.this,
                                        appIdForSync,
                                        true, /* ignorePendingOperations */
                                        com.winlator.cmod.feature.stores.steam.enums.SaveLocation.None,
                                        false, /* isOffline */
                                        null /* callback */);
                        Log.d("XServerDisplayActivity",
                                "Pre-launch javasteam cloud sync complete for appId=" + appIdForSync);
                    } catch (Throwable t) {
                        Log.w("XServerDisplayActivity",
                                "Pre-launch javasteam cloud sync failed (continuing)", t);
                    } finally {
                        syncLatch.countDown();
                    }
                }, "SteamPreLaunchCloudSync");
                preLaunchSync.start();
                try {
                    if (!syncLatch.await(45, java.util.concurrent.TimeUnit.SECONDS)) {
                        Log.w("XServerDisplayActivity",
                                "Pre-launch javasteam cloud sync timed out after 45s, proceeding");
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Local metadata cleanup — after javasteam has synced, Steam may still have
            // stale local state from previous sessions. Wipe both the metadata cache and
            // the remote/ save directory so Steam re-reads cleanly from the sync results.
            if (launchRealSteamMode && steamAccountId > 0) {
                File userAppDir = new File(steamDir,
                        "userdata/" + steamAccountId + "/" + appId);
                File remoteCache = new File(userAppDir, "remotecache.vdf");
                if (remoteCache.exists() && remoteCache.delete()) {
                    Log.d("XServerDisplayActivity",
                            "Cleared remotecache.vdf for appId=" + appId);
                }
                File remoteSaves = new File(userAppDir, "remote");
                if (remoteSaves.exists() && remoteSaves.isDirectory()) {
                    if (FileUtils.delete(remoteSaves)) {
                        Log.d("XServerDisplayActivity",
                                "Cleared remote/ save directory for appId=" + appId
                                        + " — Steam will re-download from cloud");
                    } else {
                        Log.w("XServerDisplayActivity",
                                "Failed to clear remote/ save directory for appId=" + appId);
                    }
                }
            }

            Log.d("XServerDisplayActivity", "Steam environment setup complete for appId=" + appId);
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to setup Steam environment", e);
        }
    }

    /**
     * Creates lightweight Steam configuration files to reduce resource usage.
     * Disables community content, friends, and enables small mode.
     */
    private void setupLightweightSteamConfig(File steamDir, String steamId64) {
        try {
            File userDataPath = new File(steamDir, "userdata/" + steamId64);
            File configPath = new File(userDataPath, "config");
            File remotePath = new File(userDataPath, "7/remote");
            configPath.mkdirs();
            remotePath.mkdirs();

            File localConfigFile = new File(configPath, "localconfig.vdf");
            if (!localConfigFile.exists()) {
                String localConfigContent = "\"UserLocalConfigStore\"\n" +
                        "{\n" +
                        "  \"Software\"\n" +
                        "  {\n" +
                        "    \"Valve\"\n" +
                        "    {\n" +
                        "      \"Steam\"\n" +
                        "      {\n" +
                        "        \"SmallMode\"                      \"1\"\n" +
                        "        \"LibraryDisableCommunityContent\" \"1\"\n" +
                        "        \"LibraryLowBandwidthMode\"        \"1\"\n" +
                        "        \"LibraryLowPerfMode\"             \"1\"\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "  \"friends\"\n" +
                        "  {\n" +
                        "    \"SignIntoFriends\" \"0\"\n" +
                        "  }\n" +
                        "}\n";
                FileUtils.writeString(localConfigFile, localConfigContent);
            }

            File sharedConfigFile = new File(remotePath, "sharedconfig.vdf");
            if (!sharedConfigFile.exists()) {
                String sharedConfigContent = "\"UserRoamingConfigStore\"\n" +
                        "{\n" +
                        "  \"Software\"\n" +
                        "  {\n" +
                        "    \"Valve\"\n" +
                        "    {\n" +
                        "      \"Steam\"\n" +
                        "      {\n" +
                        "        \"SteamDefaultDialog\" \"#app_games\"\n" +
                        "        \"FriendsUI\"\n" +
                        "        {\n" +
                        "          \"FriendsUIJSON\" \"{\\\"bSignIntoFriends\\\":false,\\\"bAnimatedAvatars\\\":false,\\\"PersonaNotifications\\\":0,\\\"bDisableRoomEffects\\\":true}\"\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
                FileUtils.writeString(sharedConfigFile, sharedConfigContent);
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to setup lightweight Steam configuration", e);
        }
    }

    // Real Steam launch watchdog. If steam.exe hangs during startup (classic cryptnet/CRL
    // stall in Wine) the user sees a blank screen because -silent hides everything. This
    // schedules a timer that, if no application window has registered with the X server by
    // the deadline, kills wineserver and surfaces a toast.
    private static final long REAL_STEAM_WATCHDOG_MS = 75_000L;

    private void scheduleRealSteamWatchdog(GuestProgramLauncherComponent launcherComponent) {
        cancelRealSteamWatchdog();
        firstAppWindowAppeared.set(false);
        realSteamWatchdogRunnable = () -> {
            if (firstAppWindowAppeared.get()) return;
            Log.w("XServerDisplayActivity",
                    "Real Steam watchdog: no game window after " + (REAL_STEAM_WATCHDOG_MS / 1000)
                            + "s — assuming steam.exe hung, killing wineserver");
            try {
                if (launcherComponent != null) launcherComponent.execShellCommand("wineserver -k");
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Real Steam watchdog: wineserver -k failed", e);
            }
            runOnUiThread(() -> AppUtils.showToast(
                    XServerDisplayActivity.this,
                    "Steam client failed to start. Try toggling Launch Steam Client off.",
                    android.widget.Toast.LENGTH_LONG));
        };
        new Handler(Looper.getMainLooper()).postDelayed(realSteamWatchdogRunnable, REAL_STEAM_WATCHDOG_MS);
        Log.d("XServerDisplayActivity",
                "Real Steam watchdog: armed for " + (REAL_STEAM_WATCHDOG_MS / 1000) + "s");
    }

    private void cancelRealSteamWatchdog() {
        if (realSteamWatchdogRunnable != null) {
            new Handler(Looper.getMainLooper()).removeCallbacks(realSteamWatchdogRunnable);
            realSteamWatchdogRunnable = null;
        }
    }

    private void reconcileSteamUserdata(File steamDir, String steamUserDataId, String steamId64) {
        if (steamDir == null || !steamDir.exists() || steamUserDataId == null || steamUserDataId.isEmpty()) {
            return;
        }

        File userdataDir = new File(steamDir, "userdata");
        if (!userdataDir.exists()) userdataDir.mkdirs();

        File activeUserDir = new File(userdataDir, steamUserDataId);
        if (!activeUserDir.exists()) activeUserDir.mkdirs();

        String fallbackUserId = "76561198000000000";
        if (fallbackUserId.equals(steamUserDataId) || fallbackUserId.equals(steamId64)) {
            return;
        }

        File staleUserDir = new File(userdataDir, fallbackUserId);
        if (!staleUserDir.exists()) {
            return;
        }

        try {
            File staleLocalConfig = new File(staleUserDir, "config/localconfig.vdf");
            File activeLocalConfig = new File(activeUserDir, "config/localconfig.vdf");
            if (staleLocalConfig.exists() && !activeLocalConfig.exists()) {
                activeLocalConfig.getParentFile().mkdirs();
                FileUtils.copy(staleLocalConfig, activeLocalConfig);
            }

            File staleSharedConfig = new File(staleUserDir, "7/remote/sharedconfig.vdf");
            File activeSharedConfig = new File(activeUserDir, "7/remote/sharedconfig.vdf");
            if (staleSharedConfig.exists() && !activeSharedConfig.exists()) {
                activeSharedConfig.getParentFile().mkdirs();
                FileUtils.copy(staleSharedConfig, activeSharedConfig);
            }

            if (FileUtils.delete(staleUserDir)) {
                Log.d("XServerDisplayActivity",
                        "Removed stale fallback Steam userdata profile " + fallbackUserId + " in favor of " + steamUserDataId);
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to reconcile stale Steam userdata", e);
        }
    }

    private void ensureSteamLibraryFoldersConfig(File steamDir, File steamappsDir) {
        if (steamDir == null || steamappsDir == null) {
            return;
        }

        try {
            File configDir = new File(steamDir, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            java.util.Set<String> installedAppIds = new java.util.TreeSet<>();
            File[] manifests = steamappsDir.listFiles((dir, name) ->
                    name != null && name.startsWith("appmanifest_") && name.endsWith(".acf"));
            if (manifests != null) {
                for (File manifest : manifests) {
                    String name = manifest.getName();
                    String appId = name.substring("appmanifest_".length(), name.length() - ".acf".length());
                    if (!appId.isEmpty()) {
                        installedAppIds.add(appId);
                    }
                }
            }

            StringBuilder content = new StringBuilder();
            content.append("\"libraryfolders\"\n");
            content.append("{\n");
            content.append("\t\"0\"\n");
            content.append("\t{\n");
            content.append("\t\t\"path\"\t\t\"C:\\\\Program Files (x86)\\\\Steam\"\n");
            content.append("\t\t\"label\"\t\t\"\"\n");
            content.append("\t\t\"contentid\"\t\t\"0\"\n");
            content.append("\t\t\"totalsize\"\t\t\"0\"\n");
            content.append("\t\t\"update_clean_bytes_tally\"\t\t\"0\"\n");
            content.append("\t\t\"time_last_update_verified\"\t\t\"0\"\n");
            content.append("\t\t\"apps\"\n");
            content.append("\t\t{\n");
            for (String appId : installedAppIds) {
                content.append("\t\t\t\"").append(appId).append("\"\t\t\"0\"\n");
            }
            content.append("\t\t}\n");
            content.append("\t}\n");
            content.append("}\n");

            File libraryFolders = new File(configDir, "libraryfolders.vdf");
            FileUtils.writeString(libraryFolders, content.toString());
            Log.d("XServerDisplayActivity", "Updated Steam libraryfolders.vdf with " + installedAppIds.size() + " app(s)");
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to update Steam libraryfolders.vdf", e);
        }
    }

    private void copySteamRuntimeIntoGameDir(File gameDir) {
        File gameSteamDir = new File(gameDir, "Steam");
        if (gameSteamDir.exists()) {
            return;
        }

        try {
            gameSteamDir.mkdirs();
            File steamDirSrc = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
            File[] steamChildren = steamDirSrc.listFiles();
            if (steamChildren != null) {
                for (File child : steamChildren) {
                    String name = child.getName().toLowerCase();
                    if (name.equals("dumps") || name.equals("steamapps") || name.equals("userdata")) continue;

                    File targetChild = new File(gameSteamDir, child.getName());
                    com.winlator.cmod.shared.io.FileUtils.copy(child, targetChild);
                }
            }
            Log.d("XServerDisplayActivity", "Physically copied Steam client files to " + gameSteamDir.getAbsolutePath());
        } catch (Exception copyEx) {
            Log.e("XServerDisplayActivity", "Failed to copy Steam client files to game dir", copyEx);
        }
    }

    private void cleanupEmbeddedSteamRuntime(File gameDir) {
        File embeddedSteamDir = new File(gameDir, "Steam");
        if (!embeddedSteamDir.exists() || !embeddedSteamDir.isDirectory()) {
            return;
        }

        boolean looksLikeCopiedSteamRuntime =
                new File(embeddedSteamDir, "steam.exe").exists()
                || new File(embeddedSteamDir, "steamclient.dll").exists()
                || new File(embeddedSteamDir, "steamclient_loader_x64.exe").exists()
                || new File(embeddedSteamDir, "ColdClientLoader.ini").exists();
        if (!looksLikeCopiedSteamRuntime) {
            return;
        }

        try {
            if (FileUtils.delete(embeddedSteamDir)) {
                Log.d("XServerDisplayActivity", "Removed embedded Steam runtime from game directory " + embeddedSteamDir.getAbsolutePath());
            } else {
                Log.w("XServerDisplayActivity", "Failed to remove embedded Steam runtime from game directory " + embeddedSteamDir.getAbsolutePath());
            }
        } catch (Throwable e) {
            Log.w("XServerDisplayActivity", "Failed to remove embedded Steam runtime", e);
        }
    }

    /**
     * Marks common redistributables (DirectX, .NET, XNA, OpenAL) as already installed
     * in the system registry to prevent games from running first-time setup that would fail.
     */
    private void skipFirstTimeSteamSetup(File containerDir) {
        File systemRegFile = new File(containerDir, ".wine/system.reg");
        if (!systemRegFile.exists()) return;

        String[][] redistributables = {
            {"DirectX\\Jun2010", "DXSetup"},
            {".NET\\3.5", "3.5 SP1"},
            {".NET\\3.5 Client Profile", "3.5 Client Profile SP1"},
            {".NET\\4.0", "4.0"},
            {".NET\\4.0 Client Profile", "4.0 Client Profile"},
            {".NET\\4.5.1", "4.5.1"},
            {".NET\\4.5.2", "4.5.2"},
            {".NET\\4.6", "4.6"},
            {".NET\\4.6.1", "4.6.1"},
            {".NET\\4.6.2", "4.6.2"},
            {".NET\\4.7", "4.7"},
            {".NET\\4.7.1", "4.7.1"},
            {".NET\\4.7.2", "4.7.2"},
            {".NET\\4.8", "4.8"},
            {".NET\\4.8.1", "4.8.1"},
            {"XNA\\3.0", "3.0"},
            {"XNA\\3.1", "3.1"},
            {"XNA\\4.0", "4.0"},
            {"OpenAL\\2.0.7.0", "2.0.7.0"},
        };

        try (WineRegistryEditor reg = new WineRegistryEditor(systemRegFile)) {
            for (String[] entry : redistributables) {
                String regPath = "Software\\Valve\\Steam\\Apps\\CommonRedist\\" + entry[0];
                String regPathWow = "Software\\Wow6432Node\\Valve\\Steam\\Apps\\CommonRedist\\" + entry[0];
                reg.setDwordValue(regPath, entry[1], 1);
                reg.setDwordValue(regPathWow, entry[1], 1);
            }
            Log.d("XServerDisplayActivity", "Marked " + redistributables.length + " redistributables as installed");
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to set redistributable registry entries", e);
        }
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) {
            overrideEnvVars = new EnvVars();
        }
        return overrideEnvVars;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = imageFs.getRootDir();
            File userRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals("alsa")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                }
                else if (audioDriver.equals("pulseaudio")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = imageFs.getRootDir();
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "container_pattern_common.tzst", rootDir);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"));
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("graphicsDriver", null);
        container.putExtra("desktopTheme", null);
    }

    private void assignTaskAffinity(Window window) {
        if (taskAffinityMask == 0 && taskAffinityMaskWoW64 == 0) return;
        int processId = window.getProcessId();
        String className = window.getClassName();
        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

        if (processAffinity == 0) return;

        if (processId > 0) {
            winHandler.setProcessAffinity(processId, processAffinity);
        }
        else if (!className.isEmpty()) {
            winHandler.setProcessAffinity(window.getClassName(), processAffinity);
        }
    }

    private void changeFrameRatingVisibility(Window window, Property property) {
        if (property != null) {
            String propName = property.nameAsString();
            boolean isRendererProp = propName.contains("_MESA_DRV_ENGINE_NAME") || propName.contains("_UTIL_LAYER") || propName.contains("_MESA_DRV_RENDERER");

            if (isRendererProp || propName.contains("_MESA_DRV_GPU_NAME")) {
                syncFrameRatingWithExistingWindows();
                return;
            }

            if (frameRating != null && frameRatingWindowId == window.id) {
                if (effectiveShowFPS) {
                    if (propName.contains("_MESA_DRV") || propName.contains("_UTIL_LAYER")) {
                        frameRating.update();
                    }
                }
            }
        } else {
            // If window is being destroyed, sync/reset regardless of which window it was
            syncFrameRatingWithExistingWindows();
            if (frameRatingWindowId == -1 && !effectiveShowFPS) {
                Log.d("XServerDisplayActivity", "Hiding hud as no renderer windows remain.");
                if (frameRating != null) {
                    runOnUiThread(() -> {
                        frameRating.setVisibility(View.GONE);
                        frameRating.reset();
                    });
                }
            }
        }
    }

    private void syncFrameRatingWithExistingWindows() {
        if (xServer == null || frameRating == null) return;
        Window bestWindow = null;
        String bestRenderer = null;
        String bestGpu = null;
        int bestScore = -1;

        for (Window window : xServer.windowManager.getWindows()) {
            if (window.id == xServer.windowManager.rootWindow.id) continue;

            Property prop = window.getProperty(Atom.getId("_MESA_DRV_ENGINE_NAME"));
            if (prop == null) prop = window.getProperty(Atom.getId("_MESA_DRV_RENDERER"));
            if (prop == null) prop = window.getProperty(Atom.getId("_UTIL_LAYER"));

            if (prop != null) {
                boolean isApp = window.isApplicationWindow();
                boolean isMapped = window.attributes.isMapped();
                int area = window.getWidth() * window.getHeight();
                
                int score = 0;
                if (isApp) score += 100000000;
                if (isMapped) score += 10000000;
                
                String rName = prop.toString().toLowerCase();
                if (rName.contains("vkd3d")) {
                    score += 6000000;
                } else if (rName.contains("dxvk")) {
                    score += 5000000;
                } else if (rName.contains("vulkan") || rName.contains("turnip")) {
                    score += 4000000;
                }
                
                score += Math.min(area, 3000000);
                
                if (score > bestScore) {
                    bestScore = score;
                    bestWindow = window;
                    bestRenderer = prop.toString();
                    Property gpuProp = window.getProperty(Atom.getId("_MESA_DRV_GPU_NAME"));
                    bestGpu = gpuProp != null ? gpuProp.toString() : null;
                }
            }
        }

        if (bestWindow != null) {
            lastRendererName = bestRenderer;
            lastGpuName = bestGpu;
            frameRatingWindowId = bestWindow.id;
        } else {
            lastRendererName = "OpenGL";
            lastGpuName = null;
            frameRatingWindowId = -1;
        }

        runOnUiThread(() -> {
            frameRating.setRenderer(lastRendererName);
            frameRating.setGpuName(lastGpuName);
            updateHUDRenderMode();
        });
    }

    private void updateHUDRenderMode() {
        // Render mode is always CONTINUOUSLY for best game performance
    }

    /**
     * Find the primary game executable in a directory.
     * Uses breadth-first search to prefer root-level exes over deeply nested ones.
     */
    private File findGameExe(File dir) {
        if (dir == null || !dir.exists()) return null;
        
        // BFS: check each level fully before going deeper
        java.util.LinkedList<File[]> queue = new java.util.LinkedList<>();
        queue.add(new File[]{dir});
        int depth = 0;
        File fallbackExe = null;
        
        String[] exclusions = {"unins", "redist", "setup", "dotnet", "vcredist", 
                               "dxsetup", "helper", "crash", "ue4prereq", "dxwebsetup", "launcher"};
        
        while (!queue.isEmpty() && depth <= 4) {
            File[] currentDirs = queue.poll();
            java.util.List<File> nextDirs = new java.util.ArrayList<>();
            java.util.List<File> candidates = new java.util.ArrayList<>();
            
            for (File d : currentDirs) {
                File[] children = d.listFiles();
                if (children == null) continue;
                
                for (File f : children) {
                    if (f.isDirectory()) {
                        nextDirs.add(f);
                    } else if (f.getName().toLowerCase().endsWith(".exe")) {
                        String name = f.getName().toLowerCase();
                        boolean excluded = false;
                        for (String exclusion : exclusions) {
                            if (name.contains(exclusion)) {
                                excluded = true;
                                break;
                            }
                        }
                        if (!excluded) candidates.add(f);
                    }
                }
            }

            // Prefer 64-bit executable candidates at the current depth
            for (File cand : candidates) {
                if (cand.getName().toLowerCase().contains("64") || 
                    (cand.getParentFile() != null && cand.getParentFile().getName().toLowerCase().contains("64"))) {
                    return cand;
                }
            }

            // Collect the first valid candidate as a fallback
            if (fallbackExe == null && !candidates.isEmpty()) {
                fallbackExe = candidates.get(0);
            }
            
            if (!nextDirs.isEmpty()) queue.add(nextDirs.toArray(new File[0]));
            depth++;
        }
        return fallbackExe;
    }
}
