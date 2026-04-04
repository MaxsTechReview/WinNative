package com.winlator.cmod;

import static com.winlator.cmod.core.AppUtils.showToast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.view.GravityCompat;
import com.winlator.cmod.steam.enums.Marker;
import com.winlator.cmod.steam.utils.MarkerUtils;
import com.winlator.cmod.steam.utils.SteamUtils;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.DebugDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.ScreenEffectDialog;
import com.winlator.cmod.contentdialog.WineD3DConfigDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.RefreshRateUtils;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineRequestHandler;
import com.winlator.cmod.core.WineStartMenuCreator;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.fexcore.FEXCoreManager;
import com.winlator.cmod.gamefixes.GameFixes;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ControllerManager;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.midi.MidiHandler;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.widget.FrameRating;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.LogView;
import com.winlator.cmod.CloudSyncHelper;
import com.winlator.cmod.widget.MagnifierView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.OnGetProcessInfoListener;
import com.winlator.cmod.winhandler.ProcessInfo;
import com.winlator.cmod.winhandler.TaskManagerDialog;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.XEnvironment;
import com.winlator.cmod.steam.SteamClientManager;
import com.winlator.cmod.xenvironment.components.ALSAServerComponent;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.xenvironment.components.PulseAudioComponent;
import com.winlator.cmod.xenvironment.components.SteamClientComponent;
import com.winlator.cmod.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.cmod.xenvironment.components.XServerComponent;
import com.winlator.cmod.xserver.Atom;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.ScreenInfo;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends AppCompatActivity {
    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    public static int NOTIFICATION_ID = -1;
    private static final long STEAM_TERMINATION_GRACE_MS = 10000L;
    private static final long STEAM_TERMINATION_POLL_MS = 1000L;
    private static final long STEAM_PROCESS_RESPONSE_TIMEOUT_MS = 2000L;
    private static final long STEAM_TERMINATION_TIMEOUT_MS = 30000L;
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
    private String lastRendererName = "OpenGL";
    private String lastGpuName = null;
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
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
    private short taskAffinityMask = 0;
    private short taskAffinityMaskWoW64 = 0;
    private int frameRatingWindowId = -1;
    private boolean cursorLock; // Flag to track if pointer capture was requested
    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private boolean navigationFocused = false;
    private MidiHandler midiHandler;
    private String midiSoundFont = "";
    private String lc_all = "";
    private String vkbasaltConfig = "";
    PreloaderDialog preloaderDialog = null;
    private Runnable configChangedCallback = null;
    private boolean isPaused = false;
    private boolean isRelativeMouseMovement = false;
    private boolean isNativeRenderingEnabled = true;

    private float hudTransparency = 1.0f;
    private boolean[] hudElements = new boolean[]{true, true, true, true, true, true};
    private boolean dualSeriesBattery = false;

    // Inside the XServerDisplayActivity class
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private ExternalController controller;

    // Playtime stats tracking
    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    private static final long SAVE_INTERVAL_MS = 1000;

    private Handler  timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private final AtomicBoolean exitRequested = new AtomicBoolean(false);
    private final AtomicBoolean steamExitWatchRunning = new AtomicBoolean(false);

    private boolean isDarkMode;
    private boolean enableLogsMenu;

    private String screenEffectProfile;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    private EnvVars overrideEnvVars;

    // Auto-switch controller profile support
    private android.hardware.input.InputManager inputDeviceManager;
    private Runnable controllerAutoSwitchRunnable;
    private final android.hardware.input.InputManager.InputDeviceListener inputDeviceListener =
            new android.hardware.input.InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
            if (device != null && ExternalController.isGameController(device)) {
                Log.d("XServerDisplayActivity", "Physical controller connected: " + device.getName());
            }
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            Log.d("XServerDisplayActivity", "Physical controller disconnected: deviceId=" + deviceId);
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            // No action needed
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

    private boolean hasPerGameDxvkFrameRateOverride() {
        if (shortcut == null || shortcutUsesContainerDefaults()) return false;

        String shortcutDxwrapperConfig = shortcut.getExtra("dxwrapperConfig");
        if (shortcutDxwrapperConfig.isEmpty()) return false;

        KeyValueSet perGameConfig = DXVKConfigDialog.parseConfig(shortcutDxwrapperConfig);
        return parsePositiveInt(perGameConfig.get("framerate")) > 0;
    }

    /**
     * Per-game settings always win over the global refresh rate when determining DXVK frame limit.
     */
    private int getDxvkFrameRateOverride() {
        int perGameRate = getPerGameRefreshRateOverride();
        if (perGameRate > 0) {
            return perGameRate;
        }
        if (hasPerGameDxvkFrameRateOverride()) {
            return 0;
        }

        int globalRate = getGlobalRefreshRateOverride();
        if (globalRate > 0) {
            return globalRate;
        }
        return RefreshRateUtils.getMaxSupportedRefreshRate(this);
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

            RefreshRateUtils.applyPreferredRefreshRate(this, getRefreshRateOverride());
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

        boolean shortcutChanged = incomingShortcutPath != null
                && !incomingShortcutPath.isEmpty()
                && !incomingShortcutPath.equals(currentShortcutPath);
        boolean shortcutUuidChanged = incomingShortcutUuid != null
                && !incomingShortcutUuid.isEmpty()
                && !incomingShortcutUuid.equals(currentShortcutUuid);
        boolean containerChanged = incomingContainerId != 0 && incomingContainerId != currentContainerId;

        if (shortcutChanged || shortcutUuidChanged || containerChanged) {
            Log.d("XServerDisplayActivity", "onNewIntent: launch target changed, recreating activity");
            recreate();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
        // Clean up any shared debug logs and prepare for fresh session logging
        DebugFragment.Companion.cleanupSharedLogs();
        com.winlator.cmod.core.LogManager.prepareForNewSession(this);

        // Initialize preferences early so pickHighestRefreshRate can read global override
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        applyPreferredRefreshRate();
        
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

        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);

        if (gyroEnabled) {
            // Register the sensor event listener
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }



        // Record the start time
        startTime = System.currentTimeMillis();

        // Initialize handler for periodic saving
        handler = new Handler(Looper.getMainLooper());

        // Register input device listener for controller connect/disconnect auto-switch
        inputDeviceManager = (android.hardware.input.InputManager) getSystemService(android.content.Context.INPUT_SERVICE);
        inputDeviceManager.registerInputDeviceListener(inputDeviceListener, handler);

        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);


        // Handler and Runnable to manage timeout for hiding controls

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", true);

        hideControlsRunnable = () -> {
            if (isTimeoutEnabled) {
                inputControlsView.setVisibility(View.GONE);
                Log.d("XServerDisplayActivity", "Touchscreen controls hidden after timeout.");
            }
        };


        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        ComposeView navigationComposeView = findViewById(R.id.NavigationComposeView);
        enableLogsMenu = preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box64_logs", false);
        isNativeRenderingEnabled = preferences.getBoolean("use_dri3", true);
        navigationComposeView.setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_ARROW));
        navigationComposeView.setOnFocusChangeListener((v, hasFocus) -> navigationFocused = hasFocus);
        renderDrawerMenu();
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                renderDrawerMenu();
                navigationComposeView.requestFocus();
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

        taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));

        if (shortcut != null) {
            taskAffinityMask = (short) ProcessHelper.getAffinityMask(getShortcutSetting("cpuList", container.getCPUList(true)));
            taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(getShortcutSetting("cpuListWoW64", container.getCPUListWoW64(true)));
        }

        // Determine the class name for the startup workarounds
        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("appVersion").isEmpty();

        wineVersion = container.getWineVersion();
        // Override wine version from per-game shortcut settings if available
        if (shortcut != null) {
            String shortcutWineVersion = getShortcutWineVersionOverride();
            if (shortcutWineVersion != null && !shortcutWineVersion.isEmpty()) {
                Log.d("XServerDisplayActivity", "Overriding wine version from shortcut: " + shortcutWineVersion);
                wineVersion = shortcutWineVersion;
            }
        }
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
                    wineVersion = container.getWineVersion();
                    String shortcutWineVersion = getShortcutWineVersionOverride();
                    if (shortcutWineVersion != null && !shortcutWineVersion.isEmpty()) {
                        Log.d("XServerDisplayActivity", "Overriding wine version from shortcut: " + shortcutWineVersion);
                        wineVersion = shortcutWineVersion;
                    }
                    if (!ensureRequestedWineVersionInstalled()) {
                        return;
                    }
                    wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);
                    imageFs.setWinePath(wineInfo.path);
                }
            }

            // Re-mount A: drive for Steam/Epic game shortcuts on the active container
            String gameSource = shortcut.getExtra("game_source");
            if ("STEAM".equals(gameSource)) {
                String appIdStr = shortcut.getExtra("app_id");
                if (!appIdStr.isEmpty()) {
                    String gameInstallPath = SteamBridge.getAppDirPath(Integer.parseInt(appIdStr));
                    if (new File(gameInstallPath).exists()) {
                        mountADriveOnContainer(container, gameInstallPath);
                        Log.d("XServerDisplayActivity", "Mounted A: drive to " + gameInstallPath + " on container " + container.id);
                    }
                }
            } else if ("EPIC".equals(gameSource)) {
                String gameInstallPath = shortcut.getExtra("game_install_path");
                // Fallback: resolve install path from Epic service if missing from shortcut
                if (gameInstallPath.isEmpty()) {
                    String appIdStr = shortcut.getExtra("app_id");
                    if (!appIdStr.isEmpty()) {
                        try {
                            com.winlator.cmod.epic.data.EpicGame epicGame = com.winlator.cmod.epic.service.EpicService.Companion.getEpicGameOf(Integer.parseInt(appIdStr));
                            if (epicGame != null) {
                                String resolved = epicGame.getInstallPath();
                                if (resolved == null || resolved.isEmpty()) {
                                    resolved = com.winlator.cmod.epic.service.EpicConstants.INSTANCE.getGameInstallPath(this, epicGame.getAppName());
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
                    mountADriveOnContainer(container, gameInstallPath);
                    Log.d("XServerDisplayActivity", "Mounted A: drive to " + gameInstallPath + " on container " + container.id);
                } else {
                    Log.e("XServerDisplayActivity", "EPIC install path missing or invalid: '" + gameInstallPath + "'");
                }
            } else if ("GOG".equals(gameSource)) {
                String gameInstallPath = shortcut.getExtra("game_install_path");
                if (gameInstallPath.isEmpty()) {
                    String gogId = shortcut.getExtra("gog_id");
                    if (!gogId.isEmpty()) {
                        try {
                            com.winlator.cmod.gog.data.GOGGame gogGame = com.winlator.cmod.gog.service.GOGService.Companion.getGOGGameOf(gogId);
                            if (gogGame != null) {
                                String resolved = gogGame.getInstallPath();
                                if (resolved == null || resolved.isEmpty()) {
                                    resolved = com.winlator.cmod.gog.service.GOGConstants.INSTANCE.getGameInstallPath(gogGame.getTitle());
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
                    mountADriveOnContainer(container, gameInstallPath);
                    Log.d("XServerDisplayActivity", "Mounted A: drive to " + gameInstallPath + " on container " + container.id);
                } else {
                    Log.e("XServerDisplayActivity", "GOG install path missing or invalid: '" + gameInstallPath + "'");
                }
            } else if ("CUSTOM".equals(gameSource)) {
                String customMountPath = resolveCustomMountPath(shortcut);
                if (!customMountPath.isEmpty() && new File(customMountPath).isDirectory()) {
                    mountADriveOnContainer(container, customMountPath);
                    Log.d("XServerDisplayActivity", "Mounted A: drive to custom game folder '" + customMountPath + "' on container " + container.id);

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

            graphicsDriver = getShortcutSetting("graphicsDriver", container.getGraphicsDriver());
            graphicsDriverConfig = getShortcutSetting("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = getShortcutSetting("audioDriver", container.getAudioDriver());
            emulator = getShortcutSetting("emulator", container.getEmulator());
            dxwrapper = getShortcutSetting("dxwrapper", container.getDXWrapper());
            String rawShortcutDxwrapperConfig = shortcutUsesContainerDefaults() ? "" : shortcut.getExtra("dxwrapperConfig");
            dxwrapperConfig = getShortcutSetting("dxwrapperConfig", container.getDXWrapperConfig());
            Log.d("XServerDisplayActivity", "DXVK launch config source=shortcutOrContainer shortcutRaw='" +
                    rawShortcutDxwrapperConfig + "' container='" + container.getDXWrapperConfig() +
                    "' effective='" + dxwrapperConfig + "'");
            screenSize = getShortcutSetting("screenSize", container.getScreenSize());
            lc_all = getShortcutSetting("lc_all", container.getLC_ALL());
            midiSoundFont = getShortcutSetting("midiSoundFont", container.getMIDISoundFont());
            String inputType = shortcutUsesContainerDefaults() ? "" : shortcut.getExtra("inputType");
            if (!inputType.isEmpty()) winHandler.setInputType((byte)Integer.parseInt(inputType));
            String xinputDisabledString = getShortcutSetting("disableXinput", "false");
            xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);
            // Pass the value to WinHandler
            winHandler.setXInputDisabled(xinputDisabledFromShortcut);
            String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");
            if (!sharpnessEffect.equals("None")) {
                double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));
                double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));
                vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness=" + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100  + ";" + "dlsDenoise=" + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";
            }
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);
            
            startupSelection = getShortcutSetting("startupSelection", String.valueOf(container.getStartupSelection()));
            // Per-game refresh rate override is read in getRefreshRateOverride()
        } else {
            startupSelection = String.valueOf(container.getStartupSelection());
        }

        // Normalize at runtime only. Do not persist here to avoid silently overwriting
        // the version selected in container/shortcut settings on every launch.
        String preNormalizedDxwrapperConfig = dxwrapperConfig;
        dxwrapperConfig = normalizeDxwrapperConfigForCurrentWine(dxwrapperConfig);
        Log.d("XServerDisplayActivity", "DXVK launch config normalized before='" +
                preNormalizedDxwrapperConfig + "' after='" + dxwrapperConfig + "'");

        this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);
        applyPreferredRefreshRate();

        if (!wineInfo.isWin64()) {
            onExtractFileListener = (file, size) -> {
                String path = file.getPath();
                if (path.contains("system32/")) return null;
                return new File(path.replace("syswow64/", "system32/"));
            };
        }

        if (shortcutName != null && !shortcutName.isEmpty()) {
            preloaderDialog.show("Starting " + shortcutName + "...");
        } else {
            preloaderDialog.show("Starting Container...");
        }

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);

        boolean[] winStarted = {false};

        // Add the OnWindowModificationListener for dynamic workarounds
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    xServerView.getRenderer().setCursorVisible(true);
                    preloaderDialog.closeOnUiThread();
                    winStarted[0] = true;
                }
            }
            
            @Override
            public void onFramePresented(Window window) {
                if (frameRating != null && frameRating.getVisibility() == View.VISIBLE) {
                    if (frameRatingWindowId != -1) {
                        if (window != null && window.id == frameRatingWindowId) {
                            frameRating.update();
                        }
                    } else if (window == null) {
                        frameRating.update();
                    }
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
                com.winlator.cmod.core.UpdateChecker.INSTANCE.cancelPostGameCheck();

                if (shortcut != null) {
                    if (isCloudSyncEnabledForShortcut()) {
                        CloudSyncHelper.forceDownloadOnContainerSwap(this, shortcut);

                        // Cloud save sync on every store-game launch
                        if (CloudSyncHelper.isStoreGame(shortcut)) {
                            if (!CloudSyncHelper.hasLocalCloudSaves(this, shortcut)) {
                                // First launch — download silently
                                preloaderDialog.showOnUiThread("Downloading Cloud Saves\u2026");
                                CloudSyncHelper.downloadCloudSaves(this, shortcut);
                                preloaderDialog.showOnUiThread("Starting " +
                                        (shortcutName != null ? shortcutName : "Container") + "...");
                            } else if (CloudSyncHelper.cloudSavesDiffer(this, shortcut)) {
                                // Cloud differs from local — ask the user what to do
                                final CountDownLatch dialogLatch = new CountDownLatch(1);
                                final boolean[] useCloud = {false};
                                runOnUiThread(() -> {
                                    new android.app.AlertDialog.Builder(XServerDisplayActivity.this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                                        .setTitle("Cloud Save Sync")
                                        .setMessage("A newer cloud save was detected for this title.\n\n" +
                                                "Would you like to sync the latest data from the cloud " +
                                                "(this will replace your local save), or continue with " +
                                                "your current local data?")
                                        .setPositiveButton("Sync from Cloud", (dialog, which) -> {
                                            useCloud[0] = true;
                                            dialogLatch.countDown();
                                        })
                                        .setNegativeButton("Keep Local Save", (dialog, which) -> {
                                            useCloud[0] = false;
                                            dialogLatch.countDown();
                                        })
                                        .setCancelable(false)
                                        .show();
                                });
                                try {
                                    dialogLatch.await();
                                } catch (InterruptedException ignored) {}

                                if (useCloud[0]) {
                                    preloaderDialog.showOnUiThread("Syncing Cloud Saves\u2026");
                                    CloudSyncHelper.downloadCloudSaves(this, shortcut);
                                    preloaderDialog.showOnUiThread("Starting " +
                                            (shortcutName != null ? shortcutName : "Container") + "...");
                                }
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        applyPreferredRefreshRate();
        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);

        if (gyroEnabled) {
            // Re-register the sensor listener when the activity is resumed
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        startTime = System.currentTimeMillis();
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
        ProcessHelper.resumeAllWineProcesses();
    }

    @Override
    public void onPause() {
        super.onPause();
        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);

        if (gyroEnabled) {
            // Unregister the sensor listener when the activity is paused
            sensorManager.unregisterListener(gyroListener);
        }

        // Check if we are entering Picture-in-Picture mode
        if (!isInPictureInPictureMode()) {
            // Only pause environment and xServerView if not in PiP mode
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
        ProcessHelper.pauseAllWineProcesses();
    }


    private void savePlaytimeData() {
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
        editor.apply();

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

                while (!exitRequested.get() && System.currentTimeMillis() - startTime < STEAM_TERMINATION_TIMEOUT_MS) {
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
                            runOnUiThread(this::exit);
                            return;
                        } else if (lastNonCoreSeenAt < 0L && now - startTime >= STEAM_TERMINATION_GRACE_MS) {
                            Log.d("XServerDisplayActivity",
                                    "No non-core Steam/game process appeared after wrapper exit; exiting session");
                            runOnUiThread(this::exit);
                            return;
                        }
                    }

                    Thread.sleep(STEAM_TERMINATION_POLL_MS);
                }

                if (!exitRequested.get()) {
                    Log.d("XServerDisplayActivity", "Steam exit watch timed out; exiting session");
                    runOnUiThread(this::exit);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w("XServerDisplayActivity", "Steam exit watch interrupted", e);
                if (!exitRequested.get()) {
                    runOnUiThread(this::exit);
                }
            } finally {
                steamExitWatchRunning.set(false);
            }
        });

        return true;
    }

    private void exit() {
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
                    savePlaytimeData(); // Save on destroy
                    handler.removeCallbacks(savePlaytimeRunnable);
                    if (midiHandler != null) midiHandler.stop();
                    // Unregister sensor listener to avoid memory leaks
                    if (sensorManager != null) sensorManager.unregisterListener(gyroListener);
                    if (environment != null) environment.stopEnvironmentComponents();
                    if (preloaderDialog != null && preloaderDialog.isShowing()) preloaderDialog.closeOnUiThread();
                    if (winHandler != null) winHandler.stop();
                    if (wineRequestHandler != null) wineRequestHandler.stop();
                    /* Gracefully terminate all running wine processes */
                    ProcessHelper.terminateAllWineProcesses();
                    /* Wait until all processes have gracefully terminated, forcefully killing them only after a certain amount of time */
                    long start = System.currentTimeMillis();
                    while (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed >= 1500) {
                            for (String process : ProcessHelper.listRunningWineProcesses()) {
                                try {
                                    ProcessHelper.killProcess(Integer.parseInt(process));
                                } catch (Exception e) {
                                    Log.e("XServerDisplayActivity", "Failed to kill process: " + process, e);
                                }
                            }
                            break;
                        }
                    }
                    preloaderDialog.closeOnUiThread();
                    AppUtils.restartApplication(getApplicationContext());
                }
            }, 1000);
        });
    }
    
    /**
     * Syncs cloud saves for supported stores when exiting a game.
     */
    private void syncStoreCloudOnExit(Runnable onComplete) {
        if (shortcut == null) {
            onComplete.run();
            return;
        }

        if (!isCloudSyncEnabledForShortcut()) {
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

    /**
     * If Cloud Sync Auto Backup is enabled, zips the local save and uploads to Google Drive.
     * Reuses GameSaveBackupManager but skips downloading from the store provider.
     */
    private void runAutoBackupIfEnabled(Runnable onComplete) {
        if (shortcut == null) {
            onComplete.run();
            return;
        }

        if (!isCloudSyncEnabledForShortcut()) {
            onComplete.run();
            return;
        }

        if (!com.winlator.cmod.google.GameSaveBackupManager.INSTANCE.isAutoBackupEnabled(this)) {
            onComplete.run();
            return;
        }

        String gameSource = shortcut.getExtra("game_source");
        String gameId;
        com.winlator.cmod.google.GameSaveBackupManager.GameSource source;

        if ("STEAM".equals(gameSource)) {
            gameId = shortcut.getExtra("app_id");
            source = com.winlator.cmod.google.GameSaveBackupManager.GameSource.STEAM;
        } else if ("EPIC".equals(gameSource)) {
            gameId = shortcut.getExtra("app_id");
            source = com.winlator.cmod.google.GameSaveBackupManager.GameSource.EPIC;
        } else if ("GOG".equals(gameSource)) {
            gameId = shortcut.getExtra("gog_id");
            source = com.winlator.cmod.google.GameSaveBackupManager.GameSource.GOG;
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

        new Thread(() -> {
            try {
                com.winlator.cmod.google.GameSaveBackupManager.BackupResult result =
                    (com.winlator.cmod.google.GameSaveBackupManager.BackupResult) kotlinx.coroutines.BuildersKt.runBlocking(
                        kotlinx.coroutines.Dispatchers.getIO(),
                        (scope, continuation) -> com.winlator.cmod.google.GameSaveBackupManager.INSTANCE.autoBackupToGoogle(
                            this,
                            source,
                            gameId,
                            gameName,
                            continuation
                        )
                    );
                Log.d("XServerDisplayActivity", "Auto backup result: " + result.getMessage());
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Auto backup to Google Drive failed", e);
            } finally {
                runOnUiThread(onComplete);
            }
        }).start();
    }

    private boolean isCloudSyncEnabledForShortcut() {
        return shortcut == null || !"1".equals(shortcut.getExtra("cloud_sync_disabled", "0"));
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
            
            com.winlator.cmod.steam.service.SteamService.Companion.CloudSyncCallback callback = new com.winlator.cmod.steam.service.SteamService.Companion.CloudSyncCallback() {
                @Override
                public void onProgress(String message, float progress) {
                    runOnUiThread(() -> {
                        int pct = (int)(progress * 100);
                        preloaderDialog.showOnUiThread(message + " (" + pct + "%)");
                    });
                }
                @Override
                public void onComplete() {
                    Log.d("XServerDisplayActivity", "Steam cloud sync complete for appId=" + appId);
                    onComplete.run();
                }
            };
            
            com.winlator.cmod.steam.service.SteamService.syncCloudOnExit(this, appId, callback);
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

            new Thread(() -> {
                try {
                    Boolean syncSuccess = (Boolean) kotlinx.coroutines.BuildersKt.runBlocking(
                            kotlinx.coroutines.Dispatchers.getIO(),
                            (scope, continuation) -> com.winlator.cmod.epic.service.EpicCloudSavesManager.INSTANCE.syncCloudSaves(
                                    this,
                                    appId,
                                    "upload",
                                    continuation
                            )
                    );
                    Log.d("XServerDisplayActivity", "Epic cloud sync complete for appId=" + appId + ", success=" + syncSuccess);
                } catch (Exception e) {
                    Log.w("XServerDisplayActivity", "Failed to initiate Epic cloud sync", e);
                } finally {
                    runOnUiThread(onComplete);
                }
            }).start();
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

        new Thread(() -> {
            try {
                Boolean syncSuccess = (Boolean) kotlinx.coroutines.BuildersKt.runBlocking(
                        kotlinx.coroutines.Dispatchers.getIO(),
                        (scope, continuation) -> com.winlator.cmod.gog.service.GOGService.Companion.syncCloudSaves(
                                this,
                                "GOG_" + gogId,
                                "upload",
                                continuation
                        )
                );
                Log.d("XServerDisplayActivity", "GOG cloud sync complete for gogId=" + gogId + ", success=" + syncSuccess);
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to initiate GOG cloud sync", e);
            } finally {
                runOnUiThread(onComplete);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Schedule a deferred update check 10 s after game exit
        com.winlator.cmod.core.UpdateChecker.INSTANCE.schedulePostGameCheck(this);
        if (inputDeviceManager != null) {
            inputDeviceManager.unregisterInputDeviceListener(inputDeviceListener);
        }
        if (handler != null && controllerAutoSwitchRunnable != null) {
            handler.removeCallbacks(controllerAutoSwitchRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                renderDrawerMenu();
                drawerLayout.openDrawer(GravityCompat.START);
            }
            else drawerLayout.closeDrawers();
        }
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
                hudElements,
                dualSeriesBattery
        );
        XServerDrawerMenuKt.setupXServerDrawerComposeView(
                navigationComposeView,
                state,
                this,
                new XServerDrawerActionListener() {
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
                    public void onDualSeriesBatteryChanged(boolean enabled) {
                        dualSeriesBattery = enabled;
                        preferences.edit().putBoolean(FrameRating.PREF_HUD_DUAL_SERIES_BATTERY, enabled).apply();
                        if (frameRating != null) frameRating.setDualSeriesBattery(enabled);
                        renderDrawerMenu();
                    }
                }
        );
    }

    private void loadHUDSettings() {
        if (container == null) return;
        String json = container.getExtra("hudSettings");
        if (json != null && !json.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(json);
                hudTransparency = (float) obj.optDouble("transparency", 1.0);
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
            frameRating.setDualSeriesBattery(dualSeriesBattery);
            for (int i = 0; i < hudElements.length; i++) {
                frameRating.toggleElement(i, hudElements[i]);
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private boolean handleDrawerAction(int itemId) {
        final GLRenderer renderer = xServerView.getRenderer();
        switch (itemId) {
            case R.id.main_menu_keyboard:
                AppUtils.showKeyboard(this);
                break;
            case R.id.main_menu_input_controls:
                showInputControlsDialog();
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
                
                if (container != null) {
                    container.setShowFPS(becomingVisible);
                    container.saveData();
                }
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
                enterPictureInPictureMode();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_task_manager:
                new TaskManagerDialog(this).show();
                break;
            case R.id.main_menu_magnifier:
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
            case R.id.main_menu_screen_effects:
                new ScreenEffectDialog(this).show();
                break;
            case R.id.main_menu_logs:
                debugDialog.show();
                break;
            case R.id.main_menu_native_rendering:
                isNativeRenderingEnabled = !isNativeRenderingEnabled;
                preferences.edit().putBoolean("use_dri3", isNativeRenderingEnabled).apply();
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
            touchpadView.releasePointerCapture();
            touchpadView.setOnCapturedPointerListener(null);
        }
    }

    private void extractInputDLLs() {
        String inputAsset = "input_dlls.tzst";
        File wineFolder = new File(imageFs.getWinePath() + "/lib/wine/");
        boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, inputAsset, wineFolder);
        if (!success)
            Log.d("XServerDisplayActivity", "Failed to extract input dlls");
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
            String preNormalizedDxwrapperConfig = dxwrapperConfig;
            dxwrapperConfig = normalizeDxwrapperConfigForCurrentWine(dxwrapperConfig);
            KeyValueSet currentDXWrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);
            String dxvkWrapper = "dxvk-" + currentDXWrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + currentDXWrapperConfig.get("vkd3dVersion");
            String ddrawrapper = currentDXWrapperConfig.get("ddrawrapper");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper;
            Log.d("XServerDisplayActivity", "DXVK setupWineSystemFiles config before='" +
                    preNormalizedDxwrapperConfig + "' after='" + dxwrapperConfig + "' wrapper='" + dxwrapper + "'");
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
        boolean isSteamGame = shortcut != null && "STEAM".equals(shortcut.getExtra("game_source"));
        boolean launchRealSteamSetup = shortcut != null
                ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                : container.isLaunchRealSteam();
        if (launchRealSteamSetup || isSteamGame) {
            Log.d("XServerDisplayActivity", "Ensuring Steam client is ready (isSteamGame=" + isSteamGame + ")...");
            boolean steamReady = SteamBridge.ensureSteamReady(this);
            Log.d("XServerDisplayActivity", "Steam client ready: " + steamReady);

            // Download and extract the experimental-drm file to provide steamclient_loader_x64.exe
            if (isSteamGame && !launchRealSteamSetup) {
                SteamBridge.ensureColdClientSupportReady(this);
            } else if (launchRealSteamSetup) {
                SteamBridge.ensureRealSteamSupportReady(this);
            }

            // Verify essential Steam client DLLs exist in the wine prefix
            verifySteamClientFiles(isSteamGame && !launchRealSteamSetup);

            // Replace the game's steam_api DLLs and set up steam_settings for auth
            if (isSteamGame) {
                try {
                    int appId = Integer.parseInt(shortcut.getExtra("app_id"));
                    String gameInstallPath = SteamBridge.getAppDirPath(appId);
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
                            // Completely bypass emulators. We just need to make sure the original
                            // Steam client files and original game files are pristine.
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_COLDCLIENT_USED);
                            MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DRM_PATCHED);

                            // Purge known ColdClientLoader emulator footprints which could trigger Steam's anti-tamper
                            File steamDirFile = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
                            String[] emulatorFootprints = {
                                "steamclient_loader_x64.exe", "steamclient_loader_x64.dll",
                                "steamclient_loader_x86.dll", "steamclient_loader.exe", "steamclient_loader.dll",
                                "ColdClientLoader.ini", "steam_interfaces.txt",
                                "extra_dlls/steamclient_extra_x32.dll", "extra_dlls/steamclient_extra_x64.dll"
                            };
                            for (String remnant : emulatorFootprints) {
                                File remnantFile = new File(steamDirFile, remnant);
                                if (remnantFile.exists()) {
                                    remnantFile.delete();
                                    Log.d("XServerDisplayActivity", "Real Steam Setup: Purged emulator leftover " + remnant);
                                }
                            }
                            File extraDllsDir = new File(steamDirFile, "extra_dlls");
                            if (extraDllsDir.isDirectory() && FileUtils.isEmpty(extraDllsDir)) {
                                extraDllsDir.delete();
                            }

                            // Purge any A:\steam.exe previously copied by MoveSteamExe hack
                            // If a game invokes an isolated steam.exe on A:\, it immediately crashes Steam's integrity checks.
                            File copiedSteamExe = new File(gameInstallPath, "steam.exe");
                            if (copiedSteamExe.exists()) {
                                copiedSteamExe.delete();
                                Log.d("XServerDisplayActivity", "Real Steam Setup: Purged orphaned local steam.exe from " + copiedSteamExe.getAbsolutePath());
                            }
                            cleanupEmbeddedSteamRuntime(gameDir);

                            // Restore original steamclient.dll (if ColdClient replaced it previously)
                            SteamUtils.restoreSteamclientFiles(this, appId);
                            // Restore original steam_api.dll (if Goldberg replaced it previously)
                            restoreSteamApiDlls(gameDir);
                            // Restore original .exe (if Steamless replaced it previously)
                            SteamUtils.restoreOriginalExecutable(this, appId);
                            prepareRealSteamBootstrap(steamDirFile);

                            Log.d("XServerDisplayActivity", "Real Steam Setup: Pristine environment restored for appId=" + appId);
                        } else if (useColdClient) {
                            // ── ColdClient launcher mode ──────────
                            // ColdClientLoader handles Steam emulation by loading Goldberg's
                            // steamclient.dll/steamclient64.dll. The game's ORIGINAL steam_api.dll
                            // must stay intact — ColdClientLoader routes its calls through the
                            // emulated steamclient. Do NOT replace steam_api.dll here (that's
                            // only for Goldberg mode which launches the game directly).
                            //
                            // Stage 1 (Steamless exe stripping) still runs in preUnpack after Wine is ready.

                            // Restore original DLLs if previously replaced by Goldberg mode
                            if (MarkerUtils.INSTANCE.hasMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED)) {
                                restoreSteamApiDlls(gameDir);
                                MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DLL_REPLACED);
                                MarkerUtils.INSTANCE.removeMarker(gameInstallPath, Marker.STEAM_DRM_PATCHED);
                                Log.d("XServerDisplayActivity", "Restored original steam_api DLLs for ColdClient mode");
                            } else {
                                // Pre-marker fallback: DLLs were replaced before the marker system
                                // existed. Detect by checking for .orig backups and restore them.
                                restoreSteamApiDlls(gameDir);
                            }

                            // When Legacy DRM toggle is off, restore the original exe so the runtime
                            // patcher can work on the real SteamStub instead of a Steamless leftover.
                            if (!unpackFiles) {
                                SteamUtils.restoreOriginalExecutable(this, appId);
                            }

                            // Backup steamclient DLLs before any modification
                            SteamUtils.backupSteamclientFiles(this, appId);

                            // ColdClient launcher setup (writes steam_settings in Steam dir + ColdClientLoader.ini)
                            File steamDir = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
                            steamDir.mkdirs();
                            SteamUtils.writeCompleteSettingsDir(steamDir, appId, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);
                            SteamUtils.enrichSteamSettings(this, appId, new File(steamDir, "steam_settings"));

                            // Write steam_settings next to the game's steam_api DLLs too
                            setupSteamSettingsForAllDirs(gameDir, appId, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);
                            SteamUtils.enrichSteamSettings(this, appId,
                                    new File(gameInstallPath, "steam_settings"));

                            // Generate steam_interfaces.txt from the original DLLs
                            generateSteamInterfacesForGame(gameDir);

                            // Write ColdClientLoader.ini with game exe path
                            String gameExeWinPath = findGameExeWinPath(appId, gameDir);
                            if (gameExeWinPath != null) {
                                writeColdClientIniForLaunch(appId, gameExeWinPath);
                                Log.d("XServerDisplayActivity", "ColdClient launcher configured for appId=" + appId);
                            } else {
                                Log.w("XServerDisplayActivity", "Could not find game exe for ColdClient, appId=" + appId);
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
                                // DLLs already replaced; still refresh steam_settings in case ticket changed
                                setupSteamSettingsForAllDirs(gameDir, appId, language, isOfflineMode, forceDlc, useSteamInput, ticketBase64);
                                // FIX #8: Refresh achievements/save locations too
                                SteamUtils.enrichSteamSettings(this, appId,
                                        new File(gameInstallPath, "steam_settings"));
                                // Ensure experimental steamclient stubs exist (may be missing from older installs)
                                copySteamclientStubs(gameDir);
                                Log.d("XServerDisplayActivity", "Goldberg: DLLs already replaced, refreshed steam_settings for appId=" + appId);
                            }
                        }

                        // Common setup for both modes
                        setupSteamEnvironment(appId, gameDir);

                        // Sync achievements from Goldberg back to Steam (best-effort)
                        SteamUtils.syncGoldbergAchievementsAndStats(this, appId);

                        if (launchRealSteamSetup) {
                            cleanupEmbeddedSteamRuntime(gameDir);
                        } else {
                            copySteamRuntimeIntoGameDir(gameDir);
                        }

                        Log.d("XServerDisplayActivity", "Steam environment physical readiness verified for appId=" + appId);
                    }
                } catch (Exception e) {
                    Log.e("XServerDisplayActivity", "Failed to set up Steam environment", e);
                }
            }
        } else {
            // Not a Steam game. Delete any lingering ColdClientLoader.ini so winhandler.exe doesn't get confused.
            File iniFile = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/ColdClientLoader.ini");
            if (iniFile.exists()) {
                iniFile.delete();
                Log.d("XServerDisplayActivity", "Deleted lingering ColdClientLoader.ini for non-Steam game");
            }
        }

        String desktopTheme = shortcut != null ? getShortcutSetting("desktopTheme", container.getDesktopTheme()) : container.getDesktopTheme();
        if (!(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container);

        // Keep Wine joystick defaults to avoid disabling DirectInput paths that some games need.
        // This aligns behavior with the known-working Ludashi baseline.

        if (shortcut != null)
            startupSelection = getShortcutSetting("startupSelection", String.valueOf(container.getStartupSelection()));
        else
            startupSelection = String.valueOf(container.getStartupSelection());

        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, startupSelection);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }
        
        extractInputDLLs();

        if (containerDataChanged) {
            Log.d("XServerDisplayActivity", "Saving container data id=" + container.id +
                    " dxwrapperConfigField='" + container.getDXWrapperConfig() +
                    "' dxwrapperExtra='" + container.getExtra("dxwrapper") + "'");
            container.saveData();
        }
        Log.d("ContainerLaunch", "=== setupWineSystemFiles END === container=" + container.id + " firstTimeBoot=" + firstTimeBoot);
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        // Set environment variables
        envVars.put("LC_ALL", lc_all);
        envVars.put("WINEPREFIX", imageFs.wineprefix);

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
                String steamType = shortcut != null
                        ? getShortcutSetting("steamType", container.getSteamType())
                        : container.getSteamType();
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

            envVars.putAll(shortcut != null ? getShortcutSetting("envVars", container.getEnvVars()) : container.getEnvVars());

            if (!envVars.has("WINEESYNC")) {
                envVars.put("WINEESYNC", "1");
            }

            ArrayList<String> bindingPaths = new ArrayList<>();
            String drives = shortcut != null ? getShortcutSetting("drives", container.getDrives()) : container.getDrives();
            for (String[] drive : Container.drivesIterator(drives)) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            guestProgramLauncherComponent.setBox64Preset(
                    shortcut != null
                            ? getShortcutSetting("box64Preset", container.getBox64Preset())
                            : container.getBox64Preset()
            );

            guestProgramLauncherComponent.setFEXCorePreset(
                    shortcut != null
                            ? getShortcutSetting("fexcorePreset", container.getFEXCorePreset())
                            : container.getFEXCorePreset()
            );

                // P2: Wire preUnpack callback for Mono, redistributables, and Steamless DRM
                // This runs after box64/Wine is ready but before the game exe launches.
                // Always wired for Steam games so per-container prerequisites install correctly.
                boolean isSteamGameForUnpack = shortcut != null && "STEAM".equals(shortcut.getExtra("game_source"));
                if (isSteamGameForUnpack) {
                    final boolean needsUnpacking = container.isNeedsUnpacking();
                    final boolean unpackFiles = shortcut != null
                            ? parseBoolean(getShortcutSetting("unpackFiles", container.isUnpackFiles() ? "1" : "0"))
                            : container.isUnpackFiles();
                    final boolean launchRealSteamForSetup = shortcut != null
                            ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                            : container.isLaunchRealSteam();
                    if (launchRealSteamForSetup) {
                        guestProgramLauncherComponent.setPreUnpack(null);
                    } else {
                        guestProgramLauncherComponent.setPreUnpack(() -> {
                            try {
                                runPreGameSetup(guestProgramLauncherComponent, needsUnpacking, unpackFiles, false);
                            } catch (Exception e) {
                                Log.e("XServerDisplayActivity", "preUnpack failed", e);
                            }
                        });
                    }
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
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)
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
                String steamId64 = String.valueOf(com.winlator.cmod.steam.utils.PrefManager.INSTANCE.getSteamUserSteamId64());
                String username = com.winlator.cmod.steam.utils.PrefManager.INSTANCE.getUsername();
                String refreshToken = com.winlator.cmod.steam.utils.PrefManager.INSTANCE.getRefreshToken();
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    com.winlator.cmod.steam.utils.SteamTokenLogin tokenLogin =
                            new com.winlator.cmod.steam.utils.SteamTokenLogin(
                                    steamId64, username, refreshToken, imageFs, guestProgramLauncherComponent);
                    tokenLogin.setupSteamFiles(true);
                    Log.d("XServerDisplayActivity", "SteamTokenLogin set up for real Steam mode");
                }
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to set up SteamTokenLogin", e);
            }
        }

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

        if (shortcut != null) {
            renderer.setUnviewableWMClasses("explorer.exe");
        }

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
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);


        startTouchscreenTimeout();

        // Inside onCreate(), after initializing controls
        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (isTimeoutEnabled) {
            startTouchscreenTimeout();
        }

        if (container != null && container.isShowFPS()) {
            frameRating = new FrameRating(this, graphicsDriverConfig);
            frameRating.setRenderer(lastRendererName);
            if (lastGpuName != null) frameRating.setGpuName(lastGpuName);
            frameRating.setVisibility(View.VISIBLE);
            rootView.addView(frameRating);
        }

        // Get the fullscreen stretched extra from the shortcut if available
        String shortcutFullscreenStretched = shortcut != null && !shortcutUsesContainerDefaults() ? shortcut.getExtra("fullscreenStretched") : null;

        // Proceed based on container and shortcut settings
        boolean shouldStretch = false;

        if (shortcut != null && shortcutFullscreenStretched != null) {
            // Shortcut exists and has a valid setting
            shouldStretch = shortcutFullscreenStretched.equals("1");
        } else if (container != null && container.isFullscreenStretched()) {
            // No shortcut or shortcut doesn't override, use the container's setting
            shouldStretch = true;
        }

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

    private String normalizeDxwrapperConfigForCurrentWine(String dxwrapperConfig) {
        KeyValueSet config = DXVKConfigDialog.parseConfig(dxwrapperConfig);
        boolean isArm64EC = wineInfo != null && wineInfo.isArm64EC();
        boolean changed = false;

        String normalizedDxvk = resolveInstalledGraphicsComponentVersion(
                config.get("version"),
                ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                isArm64EC
        );
        if (!normalizedDxvk.equals(config.get("version"))) {
            config.put("version", normalizedDxvk);
            changed = true;
        }

        String vkd3dVersion = config.get("vkd3dVersion");
        if (!vkd3dVersion.isEmpty() && !"None".equalsIgnoreCase(vkd3dVersion)) {
            String normalizedVkd3d = resolveInstalledGraphicsComponentVersion(
                    vkd3dVersion,
                    ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                    isArm64EC
            );
            if (!normalizedVkd3d.equals(vkd3dVersion)) {
                config.put("vkd3dVersion", normalizedVkd3d);
                changed = true;
            }
        }

        Log.d("XServerDisplayActivity", "normalizeDxwrapperConfigForCurrentWine input='" + dxwrapperConfig +
                "' output='" + (changed ? config.toString() : dxwrapperConfig) + "' arm64ec=" + isArm64EC +
                " changed=" + changed);
        return changed ? config.toString() : dxwrapperConfig;
    }

    private String resolveInstalledGraphicsComponentVersion(
            String currentVersion,
            ContentProfile.ContentType type,
            boolean isArm64EC
    ) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return currentVersion;
        }

        ContentProfile currentProfile = resolveInstalledGraphicsProfileByToken(type, currentVersion);
        if (currentProfile != null) {
            String resolvedToken = getContentVersionToken(currentProfile);
            if (isArm64ComponentVersion(resolvedToken) == isArm64EC) {
                Log.d("XServerDisplayActivity", "resolveInstalledGraphicsComponentVersion keep current type=" + type +
                        " current='" + currentVersion + "' resolvedToken='" + resolvedToken +
                        "' arm64ec=" + isArm64EC);
                return currentVersion;
            }

            String sameVersionVariant = findBestInstalledGraphicsToken(type, isArm64EC, currentProfile.verName);
            if (!sameVersionVariant.isEmpty()) {
                Log.d("XServerDisplayActivity", "resolveInstalledGraphicsComponentVersion switched same-name variant type=" + type +
                        " current='" + currentVersion + "' to='" + sameVersionVariant + "' arm64ec=" + isArm64EC);
                return sameVersionVariant;
            }
        } else if (isArm64ComponentVersion(currentVersion) == isArm64EC) {
            // Keep user-selected values (including built-in/default versions) untouched.
            Log.d("XServerDisplayActivity", "resolveInstalledGraphicsComponentVersion keep user token type=" + type +
                    " current='" + currentVersion + "' arm64ec=" + isArm64EC + " (no installed profile match)");
            return currentVersion;
        }

        String preferredProfileToken = findBestInstalledGraphicsToken(type, isArm64EC, null);
        if (!preferredProfileToken.isEmpty()) {
            Log.d("XServerDisplayActivity", "resolveInstalledGraphicsComponentVersion fallback preferred type=" + type +
                    " current='" + currentVersion + "' to='" + preferredProfileToken + "' arm64ec=" + isArm64EC);
            return preferredProfileToken;
        }
        Log.d("XServerDisplayActivity", "resolveInstalledGraphicsComponentVersion keep current (no fallback) type=" + type +
                " current='" + currentVersion + "' arm64ec=" + isArm64EC);
        return currentVersion;
    }

    private ContentProfile resolveInstalledGraphicsProfileByToken(
            ContentProfile.ContentType type,
            String versionToken
    ) {
        if (versionToken == null || versionToken.isEmpty()) return null;

        ContentProfile directMatch = contentsManager.getProfileByEntryName(type.toString() + "-" + versionToken);
        if (directMatch != null && directMatch.isInstalled) {
            return directMatch;
        }

        for (ContentProfile profile : contentsManager.getProfiles(type)) {
            if (!profile.isInstalled) continue;

            String profileToken = getContentVersionToken(profile);
            if (versionToken.equals(profileToken) || versionToken.equals(profile.verName)) {
                Log.d("XServerDisplayActivity", "resolveInstalledGraphicsProfileByToken matched type=" + type +
                        " token='" + versionToken + "' profileToken='" + profileToken +
                        "' verName='" + profile.verName + "' verCode=" + profile.verCode);
                return profile;
            }
        }
        Log.d("XServerDisplayActivity", "resolveInstalledGraphicsProfileByToken no match type=" + type +
                " token='" + versionToken + "'");
        return null;
    }

    private String findBestInstalledGraphicsToken(
            ContentProfile.ContentType type,
            boolean isArm64EC,
            @Nullable String preferredVersionName
    ) {
        ContentProfile preferredProfile = null;
        String normalizedPreferredName = normalizeGraphicsVersionName(preferredVersionName);

        for (ContentProfile profile : contentsManager.getProfiles(type)) {
            if (!profile.isInstalled) continue;

            String versionToken = getContentVersionToken(profile);
            if (isArm64ComponentVersion(versionToken) != isArm64EC) continue;

            if (normalizedPreferredName != null && !normalizedPreferredName.equals(profile.verName)) continue;

            if (preferredProfile == null ||
                    profile.verCode > preferredProfile.verCode ||
                    (profile.verCode == preferredProfile.verCode &&
                            profile.verName.compareToIgnoreCase(preferredProfile.verName) > 0)) {
                preferredProfile = profile;
            }
        }

        String selected = preferredProfile != null ? getContentVersionToken(preferredProfile) : "";
        Log.d("XServerDisplayActivity", "findBestInstalledGraphicsToken type=" + type +
                " preferredName='" + preferredVersionName + "' normalizedPreferredName='" + normalizedPreferredName +
                "' arm64ec=" + isArm64EC + " selected='" + selected + "'");
        return selected;
    }

    private String normalizeGraphicsVersionName(@Nullable String versionToken) {
        if (versionToken == null || versionToken.isEmpty()) return null;
        int lastDashIndex = versionToken.lastIndexOf('-');
        if (lastDashIndex <= 0 || lastDashIndex == versionToken.length() - 1) return versionToken;

        String suffix = versionToken.substring(lastDashIndex + 1);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return versionToken;
            }
        }
        return versionToken.substring(0, lastDashIndex);
    }

    private String getContentVersionToken(ContentProfile profile) {
        String entryName = ContentsManager.getEntryName(profile);
        int firstDashIndex = entryName.indexOf('-');
        return firstDashIndex >= 0 ? entryName.substring(firstDashIndex + 1) : profile.verName;
    }

    private boolean isArm64ComponentVersion(String version) {
        return version != null && version.toLowerCase().contains("arm64ec");
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

    private void showInputControlsDialog() {
        final com.winlator.cmod.contentdialog.InputControlsDialog dialog =
            new com.winlator.cmod.contentdialog.InputControlsDialog(this);

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
        dialog.getShowTouchscreenControls().setValue(inputControlsView.isShowTouchscreenControls());
        dialog.getTouchscreenTimeout().setValue(preferences.getBoolean("touchscreen_timeout_enabled", false));
        dialog.getTouchscreenHaptics().setValue(preferences.getBoolean("touchscreen_haptics_enabled", false));

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
            Intent intent = new Intent(this, MainActivity.class);
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
            boolean isTimeoutEnabled = dialog.getTouchscreenTimeout().getValue();
            boolean isHapticsEnabled = dialog.getTouchscreenHaptics().getValue();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("show_touchscreen_controls_enabled", dialog.getShowTouchscreenControls().getValue());
            editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
            editor.apply();

            if (isTimeoutEnabled) {
                startTouchscreenTimeout();
            } else {
                touchpadView.setOnTouchListener(null);
            }
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

    private ControlsProfile resolvePreferredStartupProfile() {
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        int selectedProfileIndex = preferences.getInt("selected_profile_index", -1);
        ControlsProfile selectedProfile = null;

        if (selectedProfileIndex >= 0 && selectedProfileIndex < profiles.size()) {
            selectedProfile = profiles.get(selectedProfileIndex);
        }

        if (selectedProfile != null && selectedProfile.isVirtualGamepad()) {
            Log.d("XServerDisplayActivity", "Skipping automatic startup for Virtual Gamepad profile=" + selectedProfile.getName());
            return null;
        }

        return selectedProfile;
    }

    private void simulateConfirmInputControlsDialog() {
        // Simulate setting the relative mouse movement and touchscreen controls from preferences

        boolean isShowTouchscreenControls = preferences.getBoolean("show_touchscreen_controls_enabled", false); // default is false (hidden)
        inputControlsView.setShowTouchscreenControls(isShowTouchscreenControls);

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        boolean isHapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", false);

        // Apply these settings as if the user confirmed the dialog
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
        editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
        editor.apply();

        ControlsProfile startupProfile = resolvePreferredStartupProfile();
        if (startupProfile != null) showInputControls(startupProfile);
        else hideInputControls();

        // Timeout logic should only apply if the controls are visible
        if (isTimeoutEnabled && inputControlsView.getVisibility() == View.VISIBLE) {
            startTouchscreenTimeout(); // Start timeout if enabled and controls are visible
        } else {
            touchpadView.setOnTouchListener(null); // Disable the timeout listener if not needed
        }

        Log.d("XServerDisplayActivity", "Input controls simulated confirmation executed. startupProfile=" + (startupProfile != null ? startupProfile.getName() : "none"));

        controllerAutoSwitchRunnable = null;
    }

    private void startTouchscreenTimeout() {
        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

        if (isTimeoutEnabled) {
            // Show controls initially and set up touch event listeners
            inputControlsView.setVisibility(View.VISIBLE);
            Log.d("XServerDisplayActivity", "Timeout is enabled, setting up timeout logic.");

            // Attach the OnTouchListener to reset the timeout on touch events
            touchpadView.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    // Reset the timeout on any touch event
                    //Log.d("XServerDisplayActivity", "Touch detected, resetting timeout.");

                    // Keep the controls visible
                    inputControlsView.setVisibility(View.VISIBLE);

                    // Remove any pending hide callbacks and reset the timeout
                    timeoutHandler.removeCallbacks(hideControlsRunnable);
                    timeoutHandler.postDelayed(hideControlsRunnable, 5000); // Reset timeout
                }

                return false; // Allow the touch event to propagate
            });

            // Reset the timeout when the controls are initially displayed
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            timeoutHandler.postDelayed(hideControlsRunnable, 5000); // Hide after 5 seconds of inactivity
        } else {
            // If timeout is disabled, keep the controls always visible
            Log.d("XServerDisplayActivity", "Timeout is disabled, controls will stay visible.");

            inputControlsView.setVisibility(View.VISIBLE); // Ensure controls are visible
            timeoutHandler.removeCallbacks(hideControlsRunnable); // Remove any existing hide callbacks
            touchpadView.setOnTouchListener(null); // Remove the touch listener
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
        Log.d("XServerDisplayActivity", "showInputControls: profile=" + profile.getName() + " id=" + profile.id + " virtual=" + profile.isVirtualGamepad());

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);

        inputControlsView.invalidate();
    }

    private void hideInputControls() {
        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
    }

    private void extractGraphicsDriverFiles() {
        String adrenoToolsDriverId = graphicsDriverConfig.get("version");

        Log.d("GraphicsDriverExtraction", "Adrenotools DriverID: " + adrenoToolsDriverId);

        // Re-apply refresh rate now that shortcut is loaded (per-game override may apply)
        applyPreferredRefreshRate();

        File rootDir = imageFs.getRootDir();

        if (dxwrapper.contains("dxvk")) {
            int refreshRateOverride = getDxvkFrameRateOverride();
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars, refreshRateOverride);
            String version = dxwrapperConfig.get("version");
            if (version.equals("1.11.1-sarek")) {
                Log.d("GraphicsDriverExtraction", "Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass");
                envVars.put("WRAPPER_NO_PATCH_OPCONSTCOMP", "1");
            }
        }
        else {
            WineD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
        }

        envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/wrapper_icd.aarch64.json");
        envVars.put("GALLIUM_DRIVER", "zink");

        if (firstTimeBoot) {
            Log.d("XServerDisplayActivity", "First time container boot, re-extracting libs");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/wrapper" + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "layers" + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/extra_libs" + ".tzst", rootDir);
        }

        if (adrenoToolsDriverId != null && !adrenoToolsDriverId.equals("System")) {
            AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
            adrenotoolsManager.setDriverById(envVars, imageFs, adrenoToolsDriverId);
        }

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
            envVars.put("WRAPPER_DEVICE_ID", WineD3DConfigDialog.getDeviceIdFromGPUName(this, gpuName));
            envVars.put("WRAPPER_VENDOR_ID", WineD3DConfigDialog.getVendorIdFromGPUName(this, gpuName));
        }

        String maxDeviceMemory = graphicsDriverConfig.get("maxDeviceMemory");
        if (maxDeviceMemory != null && Integer.parseInt(maxDeviceMemory) > 0)
            envVars.put("WRAPPER_VMEM_MAX_SIZE", maxDeviceMemory);
        
        String presentMode = graphicsDriverConfig.get("presentMode");
        if (presentMode != null && presentMode.contains("immediate")) {
            envVars.put("WRAPPER_MAX_IMAGE_COUNT", "1");
        }
        envVars.put("MESA_VK_WSI_PRESENT_MODE", presentMode);

        String resourceType = graphicsDriverConfig.get("resourceType");
        envVars.put("WRAPPER_RESOURCE_TYPE", resourceType);

        String syncFrame = graphicsDriverConfig.get("syncFrame");
        if ("1".equals(syncFrame))
            envVars.put("MESA_VK_WSI_DEBUG", "forcesync");

        if (!isNativeRenderingEnabled) {
            envVars.put("MESA_VK_WSI_DEBUG", "sw");
        }
        Log.d("NativeRendering", "use_dri3=" + isNativeRenderingEnabled + " MESA_VK_WSI_DEBUG=" + envVars.get("MESA_VK_WSI_DEBUG"));

        String disablePresentWait = graphicsDriverConfig.get("disablePresentWait");
        envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", disablePresentWait);

        String bcnEmulation = graphicsDriverConfig.get("bcnEmulation");
        String bcnEmulationType = graphicsDriverConfig.get("bcnEmulationType");

        switch (bcnEmulation) {
            case "auto" -> {
                int vendorId = 0;
                try { vendorId = GPUInformation.getVendorID(null, null); } catch (Throwable ignored) {}
                if (bcnEmulationType.equals("compute") && vendorId != 0x5143) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "1");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "3");
            }
            case "full" -> {
                int vendorId2 = 0;
                try { vendorId2 = GPUInformation.getVendorID(null, null); } catch (Throwable ignored) {}
                if (bcnEmulationType.equals("compute") && vendorId2 != 0x5143) {
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

        if (!vkbasaltConfig.isEmpty()) {
            envVars.put("ENABLE_VKBASALT", "1");
            envVars.put("VKBASALT_CONFIG", vkbasaltConfig);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        boolean handledByWinHandler = false;
        boolean handledByTouchpadView = false;

        // Let winHandler process the event if available
        if (winHandler != null) {
            handledByWinHandler = winHandler.onGenericMotionEvent(event);
            if (handledByWinHandler) {
                //Log.d("XServerDisplayActivity", "Event handled by winHandler");
            }
        }

        // Let touchpadView process the event if available
        if (touchpadView != null) {
            handledByTouchpadView = touchpadView.onExternalMouseEvent(event);
            if (handledByTouchpadView) {
                //Log.d("XServerDisplayActivity", "Event handled by touchpadView");
            }
        }

        // Pass the event to the super method to ensure system-level handling
        boolean handledBySuper = super.dispatchGenericMotionEvent(event);
        if (!handledBySuper) {
            //Log.d("XServerDisplayActivity", "Event not handled by super");
        }

        // Combine the results: any handler consuming the event indicates it was handled
        return handledByWinHandler || handledByTouchpadView || handledBySuper;
    }


    private static final int RECAPTURE_DELAY_MS = 10000; // 10 seconds

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Reserve only guide/home-style buttons for the drawer so Select/Back
        // remains available to the game and controller mapper.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE || event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
                onBackPressed();
                return true;
            }
        }

        // Fallback to existing input handling
        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||
                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
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
            } else {
                Log.d(TAG, "Extracting fallback DXVK .tzst archive: " + dxvkWrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxvkWrapper + ".tzst", windowsDir, onExtractFileListener);

                if (compareVersion(dxvkWrapper, "2.4") < 0) {
                    Log.d(TAG, "Extracting d8vk as part of DXVK version " + dxvkWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d8vk-" + DefaultVersion.D8VK + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            if (vkd3dWrapper.contains("None")) {
                Log.d(TAG, "No VKD3D has been selected, restoring original d3d12");
                restoreOriginalDllFiles(new String[]{"d3d12.dll", "d3d12core.dll"});
            }
            else {
                ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
                if (vkd3dProfile != null) {
                    Log.d(TAG, "Applying user-defined VKD3D content profile: " + vkd3dWrapper);
                    contentsManager.applyContent(vkd3dProfile);
                } else {
                    Log.d(TAG, "Extracting fallback VKD3D .tzst archive: " + vkd3dWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + vkd3dWrapper + ".tzst", windowsDir, onExtractFileListener);
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
    private void mountADriveOnContainer(Container c, String gamePath) {
        // P4: Ephemeral approach — create/update the dosdevices symlink directly
        // instead of persisting to container.setDrives() + container.saveData()
        if (gamePath == null || gamePath.isEmpty()) {
            Log.e("XServerDisplayActivity", "mountADriveOnContainer: gamePath is null/empty, skipping");
            return;
        }
        File gameDir = new File(gamePath);
        if (!gameDir.exists()) {
            Log.e("XServerDisplayActivity", "mountADriveOnContainer: gamePath does not exist: " + gamePath);
        }
        try {
            File dosdevices = new File(c.getRootDir(), ".wine/dosdevices");
            dosdevices.mkdirs();
            File aLink = new File(dosdevices, "a:");
            if (aLink.exists()) {
                aLink.delete();
            }
            java.nio.file.Files.createSymbolicLink(aLink.toPath(), gameDir.toPath());

            // Verify the symlink was created and resolves correctly
            boolean linkOk = java.nio.file.Files.isSymbolicLink(aLink.toPath());
            Log.d("XServerDisplayActivity", "Ephemeral A: drive symlink: " + aLink + " -> " + gamePath
                    + " (valid=" + linkOk + ", target_exists=" + gameDir.exists() + ")");

            // Also update in-memory drives for binding paths (but do NOT saveData)
            String currentDrives = c.getDrives() != null ? c.getDrives() : Container.DEFAULT_DRIVES;
            StringBuilder sb = new StringBuilder();
            for (String[] drive : Container.drivesIterator(currentDrives)) {
                if (!drive[0].equals("A")) {
                    sb.append(drive[0]).append(':').append(drive[1]);
                }
            }
            sb.append("A:").append(gamePath);
            c.setDrives(sb.toString());
            Log.d("XServerDisplayActivity", "In-memory drives updated with A: drive, total: " + sb);
            // NOTE: intentionally NOT calling c.saveData() — ephemeral per-launch only
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to create ephemeral A: drive symlink", e);
            // Fallback: persist to container (old behavior)
            String currentDrives = c.getDrives() != null ? c.getDrives() : Container.DEFAULT_DRIVES;
            StringBuilder sb = new StringBuilder();
            for (String[] drive : Container.drivesIterator(currentDrives)) {
                if (!drive[0].equals("A")) {
                    sb.append(drive[0]).append(':').append(drive[1]);
                }
            }
            sb.append("A:").append(gamePath);
            c.setDrives(sb.toString());
            c.saveData();
        }
    }

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

                if (launchRealSteam) {
                    // Real Steam mode: launch the actual Steam client with -applaunch
                    File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, "C:\\Program Files (x86)\\Steam");
                    if (nativeDir != null && nativeDir.exists()) launcherComponent.setWorkingDir(nativeDir);
                    args = "/dir \"C:\\Program Files (x86)\\Steam\" \"steam.exe\" -silent -vgui -tcp -nobigpicture -nofriendsui -nochatui -nointro -applaunch " + appId;
                    Log.d("XServerDisplayActivity", "Real Steam launch via steam.exe for appId=" + appId);
                } else if (useColdClient) {
                    // ColdClient mode: pick loader matching game exe architecture
                    File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, "C:\\Program Files (x86)\\Steam");
                    if (nativeDir != null && nativeDir.exists()) launcherComponent.setWorkingDir(nativeDir);
                    String gameInstPath = SteamBridge.getAppDirPath(appId);
                    String gameExe = findGameExeWinPath(appId, new File(gameInstPath));
                    File gameExeFile = gameExe != null ? new File(gameInstPath, gameExe.replace("A:\\", "").replace("\\", "/")) : null;
                    boolean use64BitLoader = gameExeFile == null || com.winlator.cmod.core.PEHelper.is64Bit(gameExeFile);
                    String loaderExe = use64BitLoader ? "steamclient_loader_x64.exe" : "steamclient_loader_x32.exe";
                    args = "/dir \"C:\\Program Files (x86)\\Steam\" \"" + loaderExe + "\"";
                    Log.d("XServerDisplayActivity", "ColdClient launch via " + loaderExe + " for appId=" + appId);
                } else {
                    // Goldberg mode (default): launch game exe from within Steam's directory structure
                    // using steamapps\common\<game> symlink, NOT from A: drive directly
                    String gameInstPathDir = SteamBridge.getAppDirPath(appId);
                    String gameDirName = new File(gameInstPathDir).getName();
                    String gameExeWinPath = findGameExeWinPath(appId, new File(gameInstPathDir));
                    
                    // Set working dir to the Steam directory
                    File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, "C:\\Program Files (x86)\\Steam");
                    if (nativeDir != null && nativeDir.exists()) launcherComponent.setWorkingDir(nativeDir);

                    if (gameExeWinPath != null) {
                        // Convert A:\relative\game.exe → steamapps\common\<gameName>\relative\game.exe
                        String relativeExe;
                        if (gameExeWinPath.startsWith("A:\\")) {
                            relativeExe = gameExeWinPath.substring(3);
                        } else {
                            relativeExe = gameExeWinPath.replace("/", "\\");
                        }
                        String steamExePath = "steamapps\\common\\" + gameDirName + "\\" + relativeExe;

                        // Extract just the filename and directory from the Steam-relative path
                        int lastBackslash = steamExePath.lastIndexOf("\\");
                        if (lastBackslash >= 0) {
                            String dir = "C:\\Program Files (x86)\\Steam\\" + steamExePath.substring(0, lastBackslash);
                            String file = steamExePath.substring(lastBackslash + 1);

                            File steamNativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, dir);
                            if (steamNativeDir != null && steamNativeDir.exists()) {
                                launcherComponent.setWorkingDir(steamNativeDir);
                                Log.d("XServerDisplayActivity", "Set native working dir for Steam process: " + steamNativeDir.getPath());
                            }

                            if (wineInfo != null && wineInfo.isArm64EC()) {
                                args = "\"" + dir + "\\" + file + "\"" + steamExtraArgs;
                            } else {
                                args = "/dir " + StringUtils.escapeDOSPath(dir) + " \"" + file + "\"" + steamExtraArgs;
                            }
                        } else {
                            args = "\"C:\\Program Files (x86)\\Steam\\" + steamExePath + "\"" + steamExtraArgs;
                        }
                        Log.d("XServerDisplayActivity", "Direct launch from Steam dir: " + args);
                    } else {
                        args = "\"wfm.exe\"";
                    }

                    // WINEPATH — point to the game directory within Steam structure
                    String steamGamePath = "C:\\Program Files (x86)\\Steam\\steamapps\\common\\" + gameDirName;
                    envVars.put("WINEPATH", steamGamePath);
                    Log.d("XServerDisplayActivity", "Set WINEPATH=" + steamGamePath + " for appId=" + appId);
                }
            } else if (gameSource.equals("EPIC") || gameSource.equals("GOG")) {
                String extraArgs = shortcut.getSettingExtra("execArgs", container.getExecArgs());
                if (extraArgs == null || extraArgs.isEmpty()) {
                    extraArgs = getIntent().getStringExtra("extra_exec_args");
                }
                extraArgs = (extraArgs != null && !extraArgs.isEmpty()) ? " " + extraArgs : "";

                boolean needsAutoDetect = path == null || path.isEmpty() || "A:\\".equals(path) || "A:\\\\".equals(path);
                if (needsAutoDetect) {
                    String gameInstallPath = shortcut.getExtra("game_install_path");
                    if ((gameInstallPath == null || gameInstallPath.isEmpty()) && gameSource.equals("GOG")) {
                        String gogId = shortcut.getExtra("gog_id");
                        if (!gogId.isEmpty()) {
                            try {
                                com.winlator.cmod.gog.data.GOGGame gogGame = com.winlator.cmod.gog.service.GOGService.Companion.getGOGGameOf(gogId);
                                if (gogGame != null) {
                                    gameInstallPath = gogGame.getInstallPath();
                                    if ((gameInstallPath == null || gameInstallPath.isEmpty()) && gogGame.getTitle() != null && !gogGame.getTitle().isEmpty()) {
                                        gameInstallPath = com.winlator.cmod.gog.service.GOGConstants.INSTANCE.getGameInstallPath(gogGame.getTitle());
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

                            String execLine = "Exec=wine \"" + detectedPath + "\"";
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
                    }
                }
                
                // Epic Games are always on A: drive. 
                String filename = path;
                String dir = "A:\\";
                
                if (path != null && path.contains("\\")) {
                    int lastBackslash = path.lastIndexOf("\\");
                    filename = path.substring(lastBackslash + 1);
                    dir = path.substring(0, lastBackslash);
                    if (dir.endsWith(":")) dir += "\\";
                } else if (path != null && path.contains(":")) {
                    filename = path.substring(path.indexOf(":") + 1);
                    dir = path.substring(0, path.indexOf(":") + 1) + "\\";
                }

                File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, dir);
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

                if (path != null && (path.startsWith("explorer") || path.contains(" /desktop"))) {
                    return path + extraArgs;
                } else if (path != null) {
                    int lastBackslash = path.lastIndexOf("\\");
                    if (lastBackslash >= 0) {
                        String dir = path.substring(0, lastBackslash);
                        if (dir.endsWith(":")) dir += "\\";
                        String file = path.substring(lastBackslash + 1);

                        File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, dir);
                        if (nativeDir != null && nativeDir.exists()) {
                            launcherComponent.setWorkingDir(nativeDir);
                            Log.d("XServerDisplayActivity", "Set native working dir for Custom process: " + nativeDir.getPath());
                        }

                        if (wineInfo != null && wineInfo.isArm64EC()) {
                            args = "\"" + path + "\"" + extraArgs;
                        } else {
                            args = "/dir \"" + dir + "\" \"" + file + "\"" + extraArgs;
                        }
                    } else {
                        args = "\"" + path + "\"" + extraArgs;
                    }
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
            // Force re-extraction by extracting both archives to imageFs root
            // The xuser symlink will direct files to the active container
            try {
                File steamFile = new File(getFilesDir(), "steam.tzst");
                File expFile = new File(getFilesDir(), "experimental-drm.tzst");
                if (steamFile.exists()) {
                    com.winlator.cmod.core.TarCompressorUtils.extract(
                            com.winlator.cmod.core.TarCompressorUtils.Type.ZSTD,
                            steamFile, imageFs.getRootDir(), null);
                }
                if (requireColdClientSupport && expFile.exists()) {
                    com.winlator.cmod.core.TarCompressorUtils.extract(
                            com.winlator.cmod.core.TarCompressorUtils.Type.ZSTD,
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
     * Writes ColdClientLoader.ini with the correct game exe path for the Goldberg Steam emulator.
     * Uses a relative path through the steamapps/common symlink so the loader resolves correctly.
     * @param appId Steam app ID
     * @param gameExeWinPath Windows-style path to the game exe (e.g. A:\SubDir\game.exe)
     */
    private void writeColdClientIniForLaunch(int appId, String gameExeWinPath) {
        File iniFile = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/ColdClientLoader.ini");
        iniFile.getParentFile().mkdirs();

        // Convert the A:\path to a relative path through steamapps\common\{gameName}\
        // The symlink created in createDosdevicesSymlinks() ensures this resolves to the real game dir
        String exePath;
        String gameInstallPath = SteamBridge.getAppDirPath(appId);
        String gameDirName = new File(gameInstallPath).getName();

        if (gameExeWinPath != null && gameExeWinPath.startsWith("A:\\")) {
            // Strip the A:\ prefix and prepend the Steam relative path
            String relativeExe = gameExeWinPath.substring(3); // Remove "A:\"
            exePath = "steamapps\\common\\" + gameDirName + "\\" + relativeExe;
        } else if (gameExeWinPath != null) {
            // Already a different format, use as-is but try to make relative
            exePath = "steamapps\\common\\" + gameDirName + "\\" + gameExeWinPath.replace("/", "\\");
        } else {
            exePath = "";
        }

        // ExeRunDir tells ColdClientLoader where to set the game's working directory.
        // Must point to the game's root inside steamapps/common so the exe can resolve
        // Steam client DLLs and the game's own dependencies. Without this, Steam returns
        // "Application Load Error 5:0000065434".
        String exeRunDir = "steamapps\\common\\" + gameDirName;

        String perGameExecArgs = shortcut != null ? shortcut.getSettingExtra("execArgs", container.getExecArgs()) : container.getExecArgs();
        String exeCommandLine = perGameExecArgs != null ? perGameExecArgs : "";

        String injectionSection = "[Injection]\nIgnoreLoaderArchDifference=1\nDllsToInjectFolder=extra_dlls\n";

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
    
    /**
     * Finds the game exe and returns its Windows-style path (e.g. A:\path\game.exe).
     * Uses shortcut.path if available, otherwise auto-detects from game install directory.
     */
    private String findGameExeWinPath(int appId, File gameDir) {
        if (gameDir == null || !gameDir.exists()) return null;

        String gameInstallPath = gameDir.getAbsolutePath();

        if (appId > 0) {
            String launchExePath = shortcut != null ? shortcut.getExtra("launch_exe_path") : "";
            if (launchExePath == null) launchExePath = "";

            String resolvedRelativePath = launchExePath;
            if (!resolvedRelativePath.isEmpty()) {
                File configuredFile = new File(resolvedRelativePath);
                if (configuredFile.isAbsolute()) {
                    String configuredAbsolutePath = configuredFile.getAbsolutePath();
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
                String normalizedRelativePath = resolvedRelativePath.replace("/", "\\");
                while (normalizedRelativePath.startsWith("\\")) {
                    normalizedRelativePath = normalizedRelativePath.substring(1);
                }
                if (!normalizedRelativePath.isEmpty()) {
                    return "A:\\" + normalizedRelativePath;
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
            String relativePath = exeFile.getAbsolutePath().substring(gameInstallPath.length());
            if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            return "A:\\" + relativePath.replace("/", "\\");
        }

        return null;
    }

    /**
     * Replaces all steam_api.dll / steam_api64.dll in the game directory tree with
     * steampipe stubs. Generates steam_interfaces.txt BEFORE replacing (from the original),
     * backs up originals as .orig (matching GameNative's convention), writes orig_dll_path.txt,
     * and calls writeCompleteSettingsDir next to each DLL found.
     */
    private void replaceSteamApiDlls(File gameDir, String appDirPath, String language,
            boolean isOffline, boolean forceDlc, boolean useSteamInput, String ticketBase64) {
        if (gameDir == null || !gameDir.exists()) return;

        java.util.List<String> backupPaths = new java.util.ArrayList<>();
        replaceSteamApiDllsRecursive(gameDir, appDirPath, language, isOffline, forceDlc,
                useSteamInput, ticketBase64, backupPaths);

        // Write orig_dll_path.txt listing all .orig backup paths (matches GameNative)
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

                // Backup as .orig (GameNative convention) if not already done
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
                // Backups are written as .orig (GameNative convention)
                if (name.equals("steam_api.dll.orig") || name.equals("steam_api64.dll.orig")) {
                    try {
                        String originalName = file.getName().substring(0, file.getName().length() - ".orig".length());
                        File target = new File(file.getParent(), originalName);

                        if (target.exists()) target.delete();
                        FileUtils.copy(file, target);

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

        // Step 3: Run Steamless DRM stripping only when the Legacy DRM toggle is ON.
        // ColdClient's runtime patcher (extra_dlls) handles most games automatically,
        // so Steamless is only needed as a fallback for stubborn SteamStub variants.
        if (launchRealSteam) {
            if (unpackFiles) {
                Log.d("XServerDisplayActivity",
                        "Skipping Steamless/unpack flow because Launch Steam Client is enabled");
            }
            return;
        }
        if (unpackFiles) {
            if (!monoReady) {
                Log.w("XServerDisplayActivity", "Skipping Steamless — Mono not installed yet, will retry next launch");
                return;
            }
            // Run Steamless if needed, or if the toggle was just enabled and no
            // .unpacked.exe exists yet (user turned Legacy DRM on for the first time).
            boolean unpackedExeExists = doesUnpackedExeExist();
            if (needsUnpacking || !unpackedExeExists) {
                runSteamlessOnExe(launcher);
            } else {
                // Steamless already ran on a prior launch. Ensure the unpacked exe is active
                // in case something (e.g. mode switch) restored the original.
                ensureUnpackedExeActive();
            }
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
            String gameInstallPath = SteamBridge.getAppDirPath(appId);
            if (gameInstallPath == null || gameInstallPath.isEmpty()) return;

            String executablePath = container.getExecutablePath();
            if (executablePath == null || executablePath.isEmpty()) {
                executablePath = com.winlator.cmod.steam.service.SteamService.Companion.getInstalledExe(appId);
            }
            if (executablePath == null || executablePath.isEmpty()) return;

            String unixPath = executablePath.replace('\\', '/');
            File exe = new File(gameInstallPath, unixPath);
            File unpackedExe = new File(gameInstallPath, unixPath + ".unpacked.exe");

            if (exe.exists() && unpackedExe.exists() && exe.length() != unpackedExe.length()) {
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
            String gameInstallPath = SteamBridge.getAppDirPath(appId);
            if (gameInstallPath == null || gameInstallPath.isEmpty()) return false;

            String executablePath = container.getExecutablePath();
            if (executablePath == null || executablePath.isEmpty()) {
                executablePath = com.winlator.cmod.steam.service.SteamService.Companion.getInstalledExe(appId);
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
        String gameInstallPath = SteamBridge.getAppDirPath(appId);
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

                            // Build the Wine path using A: drive (game is mounted there)
                            String relPath = exe.getAbsolutePath()
                                    .substring(gameInstallPath.length())
                                    .replace('/', '\\');
                            String winPath = "A:" + relPath;

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
     * Installs Wine Mono first (needed for .NET-based Steamless CLI),
     * then runs Steamless on the exe, and handles the file swap.
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

        String gameInstallPath = SteamBridge.getAppDirPath(appId);
        if (gameInstallPath == null || gameInstallPath.isEmpty()) return;

        // Mono is now installed by installMonoIfNeeded() in the pre-game setup flow.
        // No need to install it again here.

        // Extract Steamless.CLI.exe if not present
        File steamlessDir = new File(imageFs.getRootDir(), "Steamless");
        File steamlessCli = new File(steamlessDir, "Steamless.CLI.exe");
        if (!steamlessCli.exists()) {
            try {
                steamlessDir.mkdirs();
                TarCompressorUtils.extract(
                        TarCompressorUtils.Type.ZSTD,
                        this, "extras.tzst", imageFs.getRootDir());
                Log.d("XServerDisplayActivity", "Extracted Steamless CLI to " + steamlessDir);
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Failed to extract Steamless", e);
                return;
            }
        }

        // Find the game executable and run Steamless on it
        String executablePath = container.getExecutablePath();
        if (executablePath == null || executablePath.isEmpty()) {
            executablePath = com.winlator.cmod.steam.service.SteamService.Companion.getInstalledExe(appId);
        }
        if (executablePath == null || executablePath.isEmpty()) {
            Log.w("XServerDisplayActivity", "No executable path found for Steamless");
            return;
        }

        try {
            String normalizedPath = executablePath.replace('/', '\\');
            String windowsPath = "A:\\" + normalizedPath;

            // Create batch file to handle paths with spaces
            File batchFile = new File(imageFs.getRootDir(), "tmp/steamless_wrapper.bat");
            batchFile.getParentFile().mkdirs();
            com.winlator.cmod.core.FileUtils.writeString(batchFile,
                    "@echo off\r\nz:\\Steamless\\Steamless.CLI.exe \"" + windowsPath + "\"\r\n");

            Log.d("XServerDisplayActivity", "Steamless: running on " + windowsPath + " (exe=" + executablePath + ")");
            String slCmd = "wine z:\\tmp\\steamless_wrapper.bat";
            String slOutput = launcher.execShellCommand(slCmd);
            Log.d("XServerDisplayActivity", "Steamless Wine output: " + slOutput);
            batchFile.delete();

            // Handle file swap: .unpacked.exe -> exe, exe -> .original.exe
            String unixPath = executablePath.replace('\\', '/');
            File exe = new File(gameInstallPath, unixPath);
            File unpackedExe = new File(gameInstallPath, unixPath + ".unpacked.exe");
            File originalExe = new File(gameInstallPath, unixPath + ".original.exe");

            Log.d("XServerDisplayActivity", "Steamless: checking exe=" + exe.getAbsolutePath()
                    + " exists=" + exe.exists() + " unpacked=" + unpackedExe.getAbsolutePath()
                    + " exists=" + unpackedExe.exists());

            if (exe.exists() && unpackedExe.exists()) {
                if (!originalExe.exists()) {
                    java.nio.file.Files.copy(exe.toPath(), originalExe.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                java.nio.file.Files.copy(unpackedExe.toPath(), exe.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d("XServerDisplayActivity", "Steamless: swapped exe with unpacked version");

                // Only mark as done if unpacking actually succeeded
                launcher.execShellCommand("wineserver -k");
                container.setNeedsUnpacking(false);
                container.saveData();
            } else {
                Log.w("XServerDisplayActivity", "Steamless: unpacking failed, skipping for future launches");
                launcher.execShellCommand("wineserver -k");
                container.setNeedsUnpacking(false);
                container.saveData();
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Steamless execution failed, will retry next launch", e);
        }
    }

    public XServer getXServer() {
        return xServer;
    }

    /**
     * Generates steam_interfaces.txt by scanning the backed-up original DLL for
     * Steam interface version strings (e.g., SteamUser023, SteamApps008).
     * Checks .orig backup first (GameNative convention), then falls back to the DLL itself.
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
    
    /**
     * Sets up the Steam environment: steamapps/common symlink, ACF manifest,
     * Wine registry entries for Steam paths, and steam.cfg for bootstrap inhibit.
     * Matches GameNative's createAppManifest and autoLoginUserChanges approach.
     */
    private void setupSteamEnvironment(int appId, File gameDir) {
        try {
            File winePrefix = container.getRootDir();
            File steamDir = new File(winePrefix, ".wine/drive_c/Program Files (x86)/Steam");
            steamDir.mkdirs();
            boolean launchRealSteamMode = shortcut != null
                    ? parseBoolean(getShortcutSetting("launchRealSteam", container.isLaunchRealSteam() ? "1" : "0"))
                    : container.isLaunchRealSteam();
            boolean refreshedPackagedSteam = false;

            if (launchRealSteamMode) {
                Log.d("XServerDisplayActivity", "Real Steam mode: refreshing packaged Steam client before environment setup");
                refreshedPackagedSteam = SteamBridge.forceExtractSteam(this);
                if (!refreshedPackagedSteam) {
                    Log.w("XServerDisplayActivity", "Real Steam mode: failed to refresh packaged Steam client");
                }
            }

            // steam.cfg prevents the Steam bootstrapper from self-updating to potentially
            // incompatible versions. Only remove when user explicitly allows updates.
            File steamCfg = new File(steamDir, "steam.cfg");
            if (container.isAllowSteamUpdates()) {
                if (steamCfg.exists()) {
                    steamCfg.delete();
                    Log.d("XServerDisplayActivity", "Removed steam.cfg — user allows Steam updates");
                }
            } else {
                if (!steamCfg.exists()) {
                    // steam.cfg was missing — Steam may have self-updated to an incompatible version.
                    // Force re-extract the known-good packaged Steam client to restore it.
                    if (!refreshedPackagedSteam) {
                        Log.d("XServerDisplayActivity", "steam.cfg missing — forcing re-extraction of packaged Steam client");
                        refreshedPackagedSteam = SteamBridge.forceExtractSteam(this);
                    }
                }
                FileUtils.writeString(steamCfg, "BootStrapperInhibitAll=Enable\nBootStrapperForceSelfUpdate=False\n");
            }

            File steamappsDir = new File(steamDir, "steamapps");
            File commonDir = new File(steamappsDir, "common");
            commonDir.mkdirs();
            WineUtils.ensureSteamappsCommonSymlink(container, gameDir.getAbsolutePath());

            // FIX #7: Create full ACF manifest via SteamUtils.createAppManifest which includes
            // InstalledDepots, buildId, SizeOnDisk, UserConfig/language — matching GameNative.
            // Falls back gracefully if SteamService has no appInfo for this game.
            SteamUtils.createAppManifest(this, appId);
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
            // autoLoginUserChanges uses SteamService for proper token format (matching GameNative).
            try {
                SteamUtils.autoLoginUserChanges(imageFs);
                Log.d("XServerDisplayActivity", "autoLoginUserChanges complete");
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "autoLoginUserChanges failed, falling back", e);
            }

            // Skip first-time redistributable setup by marking them installed in system.reg
            skipFirstTimeSteamSetup(winePrefix);

            // Derive account info from encrypted PrefManager storage and refresh localconfig/acf.
            long steamIdLong = com.winlator.cmod.steam.utils.PrefManager.INSTANCE.getSteamUserSteamId64();
            String steamId64 = steamIdLong > 0 ? String.valueOf(steamIdLong) : "76561198000000000";
            int steamAccountId = com.winlator.cmod.steam.utils.PrefManager.INSTANCE.getSteamUserAccountId();
            String steamUserDataId = steamAccountId > 0 ? String.valueOf(steamAccountId) : steamId64;
            reconcileSteamUserdata(steamDir, steamUserDataId, steamId64);

            SteamUtils.updateOrModifyLocalConfig(imageFs, container, String.valueOf(appId), steamUserDataId);

            // Create lightweight Steam config to reduce resource usage
            setupLightweightSteamConfig(steamDir, steamUserDataId);

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

    private boolean isSteamBootstrapRepairNeeded(File steamDir) {
        File win32Manifest = new File(steamDir, "package/steam_client_win32.installed");
        File win64Manifest = new File(steamDir, "package/steam_client_win64.installed");
        return !win32Manifest.exists() || win32Manifest.length() == 0
                || !win64Manifest.exists() || win64Manifest.length() == 0;
    }

    private void prepareRealSteamBootstrap(File steamDir) {
        if (steamDir == null || !steamDir.exists()) return;

        if (isSteamBootstrapRepairNeeded(steamDir)) {
            File steamCfg = new File(steamDir, "steam.cfg");
            if (steamCfg.exists() && steamCfg.delete()) {
                Log.d("XServerDisplayActivity",
                        "Real Steam Setup: Removed steam.cfg to allow bootstrap repair of missing package files");
            }

            File packageDir = new File(steamDir, "package");
            if (!packageDir.exists()) {
                packageDir.mkdirs();
            }
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
                    com.winlator.cmod.core.FileUtils.copy(child, targetChild);
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
        if (taskAffinityMask == 0 || taskAffinityMaskWoW64 == 0) return;
        int processId = window.getProcessId();
        String className = window.getClassName();
        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

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

            if (isRendererProp) {
                lastRendererName = property.toString();
                if (frameRatingWindowId == -1 || window.isApplicationWindow()) {
                    frameRatingWindowId = window.id;
                }
            } else if (propName.contains("_MESA_DRV_GPU_NAME")) {
                lastGpuName = property.toString();
            }

            if (frameRating != null && frameRatingWindowId == window.id) {
                if (container == null || container.isShowFPS()) {
                    if (isRendererProp) {
                        runOnUiThread(() -> frameRating.setRenderer(lastRendererName));
                    } else if (propName.contains("_MESA_DRV_GPU_NAME")) {
                        runOnUiThread(() -> frameRating.setGpuName(lastGpuName));
                    }

                    if (propName.contains("_MESA_DRV") || propName.contains("_UTIL_LAYER")) {
                        frameRating.update();
                    }
                }
            }
        } else {
            // If window is being destroyed, sync/reset regardless of which window it was
            syncFrameRatingWithExistingWindows();
            if (frameRatingWindowId == -1 && (container == null || !container.isShowFPS())) {
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

        for (Window window : xServer.windowManager.getWindows()) {
            if (window.id == xServer.windowManager.rootWindow.id) continue;

            Property prop = window.getProperty(Atom.getId("_MESA_DRV_RENDERER"));
            if (prop == null) prop = window.getProperty(Atom.getId("_MESA_DRV_ENGINE_NAME"));
            if (prop == null) prop = window.getProperty(Atom.getId("_UTIL_LAYER"));

            if (prop != null) {
                boolean isApp = window.isApplicationWindow();
                boolean isMapped = window.attributes.isMapped();
                
                if (bestWindow == null || 
                   (isApp && !bestWindow.isApplicationWindow()) ||
                   (isMapped && !bestWindow.attributes.isMapped() && (isApp || !bestWindow.isApplicationWindow()))) {
                    bestWindow = window;
                    bestRenderer = prop.toString();
                    Property gpuProp = window.getProperty(Atom.getId("_MESA_DRV_GPU_NAME"));
                    bestGpu = gpuProp != null ? gpuProp.toString() : null;
                }
                
                if (isApp && isMapped) break;
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
        if (xServerView != null && frameRating != null) {
            boolean showFPS = frameRating.getVisibility() == View.VISIBLE;
            int mode = (showFPS && frameRatingWindowId == -1) ?
                        GLSurfaceView.RENDERMODE_CONTINUOUSLY :
                        GLSurfaceView.RENDERMODE_WHEN_DIRTY;
            if (xServerView.getRenderMode() != mode) {
                xServerView.setRenderMode(mode);
            }
        }
    }

    public String getScreenEffectProfile() {
        return screenEffectProfile;
    }

    public void setScreenEffectProfile(String screenEffectProfile) {
        this.screenEffectProfile = screenEffectProfile;
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
