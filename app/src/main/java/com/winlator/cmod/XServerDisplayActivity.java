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
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineRequestHandler;
import com.winlator.cmod.core.WineStartMenuCreator;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.core.WineUtils;
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
import com.winlator.cmod.renderer.effects.CRTEffect;
import com.winlator.cmod.renderer.effects.ColorEffect;
import com.winlator.cmod.renderer.effects.FXAAEffect;
import com.winlator.cmod.renderer.effects.NTSCCombinedEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.widget.FrameRating;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.LogView;
import com.winlator.cmod.widget.MagnifierView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.TaskManagerDialog;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.XEnvironment;
import com.winlator.cmod.xenvironment.components.ALSAServerComponent;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.xenvironment.components.PulseAudioComponent;
import com.winlator.cmod.xenvironment.components.SteamClientComponent;
import com.winlator.cmod.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.cmod.xenvironment.components.XServerComponent;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends AppCompatActivity {
    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    public static int NOTIFICATION_ID = -1;
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

    private boolean isDarkMode;
    private boolean enableLogsMenu;

    private String screenEffectProfile;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    private EnvVars overrideEnvVars;

    private void createNotifcationChannel() {
        String name = "Winlator";
        String description = "Winlator XServer Messages";
        int importance = NotificationManager.IMPORTANCE_HIGH;
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

    private float pickHighestRefreshRate() {
    	android.view.Display display = getWindowManager().getDefaultDisplay();
    	android.view.Display.Mode[] modes = display.getSupportedModes();
    	
    	float maxRefresh = 0f;
    	
    	for (android.view.Display.Mode mode : modes) {
			if (mode.getRefreshRate() > maxRefresh)
    	    	maxRefresh = mode.getRefreshRate();
    	}

    	Log.d("XServerDisplayActivity", "Picking refresh rate " + maxRefresh);

    	return maxRefresh;
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

        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = pickHighestRefreshRate();
        getWindow().setAttributes(params);
        
        setContentView(R.layout.xserver_display_activity);

        // Initialize ControllerManager for multi-controller support
        ControllerManager.getInstance().init(this);

        preloaderDialog = new PreloaderDialog(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        cursorLock = preferences.getBoolean("cursor_lock", false);

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

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));

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

        taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));

        if (shortcut != null) {
            taskAffinityMask = (short) ProcessHelper.getAffinityMask(shortcut.getExtra("cpuList", container.getCPUList(true)));
            taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(shortcut.getExtra("cpuListWoW64", container.getCPUListWoW64(true)));
        }

        // Determine the class name for the startup workarounds
        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("appVersion").isEmpty();

        wineVersion = container.getWineVersion();
        // Override wine version from per-game shortcut settings if available
        if (shortcut != null) {
            String shortcutWineVersion = shortcut.getExtra("wineVersion");
            if (shortcutWineVersion != null && !shortcutWineVersion.isEmpty()) {
                Log.d("XServerDisplayActivity", "Overriding wine version from shortcut: " + shortcutWineVersion);
                wineVersion = shortcutWineVersion;
            }
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
                    String shortcutWineVersion = shortcut.getExtra("wineVersion");
                    if (shortcutWineVersion != null && !shortcutWineVersion.isEmpty()) {
                        Log.d("XServerDisplayActivity", "Overriding wine version from shortcut: " + shortcutWineVersion);
                        wineVersion = shortcutWineVersion;
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

            graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            screenSize = shortcut.getExtra("screenSize", container.getScreenSize());
            lc_all = shortcut.getExtra("lc_all", container.getLC_ALL());
            midiSoundFont = shortcut.getExtra("midiSoundFont", container.getMIDISoundFont());
            String inputType = shortcut.getExtra("inputType");
            if (!inputType.isEmpty()) winHandler.setInputType((byte)Integer.parseInt(inputType));
            String xinputDisabledString = shortcut.getExtra("disableXinput", "false");
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
            
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));
        } else {
            startupSelection = String.valueOf(container.getStartupSelection());
        }

        this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

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
                    
                if (frameRatingWindowId == window.id) frameRating.update();
            }
           
            @Override
            public void onMapWindow(Window window) {
                // Log the class name of the mapped window
                Log.d("XServerDisplayActivity", "onMapWindow: Mapping window: " + window.getClassName());
                assignTaskAffinity(window);
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                String name = (property != null) ? property.nameAsString() : "";
                Log.d("XServerDisplayActivity", "onModifyWindowProperty: Changed property " + name + " for window " + window.id);
                changeFrameRatingVisibility(window, property);
            }    

            @Override
            public void onDestroyWindow(Window window) {
                Log.d("XServerDisplayActivity", "onDestroyWindow: Destroying window " + window.getClassName());
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
                .setContentTitle("Winlator")
                .setContentText("Winlator is running, do not kill or swipe this notification")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    private void exit() {
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

        String gameSource = shortcut.getExtra("game_source");
        if ("STEAM".equals(gameSource)) {
            syncSteamCloudOnExit(onComplete);
            return;
        }

        if ("GOG".equals(gameSource)) {
            syncGogCloudOnExit(onComplete);
            return;
        }

        onComplete.run();
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
                isRelativeMouseMovement,
                isMouseDisabled,
                isPaused,
                !XrActivity.isEnabled(this),
                enableLogsMenu
        );
        XServerDrawerMenuKt.setupXServerDrawerComposeView(
                navigationComposeView,
                state,
                this,
                itemId -> handleDrawerAction(itemId)
        );
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private boolean handleDrawerAction(int itemId) {
        final GLRenderer renderer = xServerView.getRenderer();
        switch (itemId) {
            case R.id.main_menu_keyboard:
                AppUtils.showKeyboard(this);
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_input_controls:
                showInputControlsDialog();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_relative_mouse_movement:
                isRelativeMouseMovement = !isRelativeMouseMovement;
                drawerLayout.closeDrawers();
                xServer.setRelativeMouseMovement(isRelativeMouseMovement);
                break;
            case R.id.main_menu_disable_mouse:
                isMouseDisabled = !isMouseDisabled;
                touchpadView.setMouseEnabled(!isMouseDisabled);
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_toggle_fullscreen:
                renderer.toggleFullscreen();
                drawerLayout.closeDrawers();
                touchpadView.toggleFullscreen();
                break;
            case R.id.main_menu_pause:
                if (isPaused) {
                    ProcessHelper.resumeAllWineProcesses();
                }
                else {
                    ProcessHelper.pauseAllWineProcesses();
                }
                isPaused = !isPaused;
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_pip_mode:
                enterPictureInPictureMode();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_task_manager:
                new TaskManagerDialog(this).show();
                drawerLayout.closeDrawers();
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
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_screen_effects:
                Log.d("ScreenEffectDialog", "Initializing ScreenEffectDialog");
                ScreenEffectDialog screenEffectDialog = new ScreenEffectDialog(this);
                screenEffectDialog.setOnConfirmCallback(() -> {
                    Log.d("ScreenEffectDialog", "Confirm callback triggered. About to apply effects.");
                    GLRenderer currentRenderer = xServerView.getRenderer();
                    ColorEffect colorEffect = (ColorEffect) currentRenderer.getEffectComposer().getEffect(ColorEffect.class);
                    FXAAEffect fxaaEffect = (FXAAEffect) currentRenderer.getEffectComposer().getEffect(FXAAEffect.class);
                    CRTEffect crtEffect = (CRTEffect) currentRenderer.getEffectComposer().getEffect(CRTEffect.class);
                    ToonEffect toonEffect = (ToonEffect) currentRenderer.getEffectComposer().getEffect(ToonEffect.class);
                    NTSCCombinedEffect ntscEffect = (NTSCCombinedEffect) currentRenderer.getEffectComposer().getEffect(NTSCCombinedEffect.class);

                    // Check if effects are null before applying
                    Log.d("ScreenEffectDialog", "ColorEffect: " + (colorEffect != null));
                    Log.d("ScreenEffectDialog", "FXAAEffect: " + (fxaaEffect != null));
                    Log.d("ScreenEffectDialog", "CRTEffect: " + (crtEffect != null));
                    Log.d("ScreenEffectDialog", "ToonEffect: " + (toonEffect != null));
                    Log.d("ScreenEffectDialog", "NTSCCombinedEffect: " + (ntscEffect != null));

                    Log.d("ScreenEffectDialog", "Calling applyEffects()");
                    screenEffectDialog.applyEffects(colorEffect, currentRenderer, fxaaEffect, crtEffect, toonEffect, ntscEffect);
                    Log.d("ScreenEffectDialog", "applyEffects() called.");
                });
                Log.d("ScreenEffectDialog", "Showing ScreenEffectDialog");
                screenEffectDialog.show();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_logs:
                debugDialog.show();
                drawerLayout.closeDrawers();
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
        ensureWinePrefixReady();

        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("imgVersion").equals(imgVersion)) {
            applyGeneralPatches(container);
            container.putExtra("appVersion", appVersion);
            container.putExtra("imgVersion", imgVersion);
            containerDataChanged = true;
        }

        String dxwrapper = shortcut != null ? shortcut.getExtra("dxwrapper", this.dxwrapper) : this.dxwrapper;

        if (dxwrapper.contains("dxvk")) {
            String dxwrapperConfig = shortcut != null ? shortcut.getExtra("dxwrapperConfig", this.dxwrapperConfig.toString()) : this.dxwrapperConfig.toString();
            KeyValueSet currentDXWrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);
            String dxvkWrapper = "dxvk-" + currentDXWrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + currentDXWrapperConfig.get("vkd3dVersion");
            String ddrawrapper = currentDXWrapperConfig.get("ddrawrapper");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper;
        }

        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {
            extractDXWrapperFiles(dxwrapper);
            container.putExtra("dxwrapper", dxwrapper);
            containerDataChanged = true;
        }

        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        // Ensure Steam client files are present (download + extract if needed) for Steam games
        boolean isSteamGame = shortcut != null && "STEAM".equals(shortcut.getExtra("game_source"));
        if (container.isLaunchRealSteam() || isSteamGame) {
            Log.d("XServerDisplayActivity", "Ensuring Steam client is ready (isSteamGame=" + isSteamGame + ")...");
            boolean steamReady = SteamBridge.ensureSteamReady(this);
            Log.d("XServerDisplayActivity", "Steam client ready: " + steamReady);
            
            // Replace the game's steam_api DLLs and set up steam_settings for auth
            if (isSteamGame) {
                try {
                    int appId = Integer.parseInt(shortcut.getExtra("app_id"));
                    String gameInstallPath = SteamBridge.getAppDirPath(appId);
                    File gameDir = new File(gameInstallPath);
                    
                    if (gameDir.exists()) {
                        if (container.isUseLegacyDRM()) {
                            // Legacy DRM mode: replace steam_api.dll with steampipe stubs
                            // This only works for offline/LAN - stubs can't do real auth
                            replaceSteamApiDlls(gameDir);
                            setupSteamSettings(gameDir, appId);
                            Log.d("XServerDisplayActivity", "Legacy DRM Steam setup complete for appId=" + appId);
                        } else {
                            // ColdClientLoader mode (default, online play):
                            // 1. MUST RESTORE the game's actual DLLs if they were previously replaced by TCP stubs
                            restoreSteamApiDlls(gameDir);
                            
                            // 2. Write steam_settings/ next to steamclient.dll in the Steam directory
                            // The Goldberg steamclient DLLs read from this location
                            File steamDir = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam");
                            steamDir.mkdirs();
                            setupSteamSettingsForColdClient(steamDir, appId);
                            
                            // 3. Write ColdClientLoader.ini with game exe path
                            String gameExeWinPath = findGameExeWinPath(appId, gameDir);
                            if (gameExeWinPath != null) {
                                writeColdClientIniForLaunch(appId, gameExeWinPath);
                                Log.d("XServerDisplayActivity", "ColdClientLoader setup complete: exe=" + gameExeWinPath + " appId=" + appId);
                            }
                        }
                        // Common setup for both modes
                        setupSteamEnvironment(appId, gameDir);
                        Log.d("XServerDisplayActivity", "Full Steam setup complete for appId=" + appId);
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

        String desktopTheme = shortcut != null ? shortcut.getExtra("desktopTheme", container.getDesktopTheme()) : container.getDesktopTheme();
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
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));
        else
            startupSelection = String.valueOf(container.getStartupSelection());

        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, Byte.parseByte(startupSelection) != Container.STARTUP_SELECTION_NORMAL);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }
        
        extractInputDLLs();

        if (containerDataChanged) container.saveData();
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        // Set environment variables
        envVars.put("LC_ALL", lc_all);
        envVars.put("WINEPREFIX", imageFs.wineprefix);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsConfig.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty()
                ? "+" + wineDebugChannels.replace(",", ",+")
                : "-all"
        );

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

            envVars.putAll(container.getEnvVars());

            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));

            if (!envVars.has("WINEESYNC")) {
                envVars.put("WINEESYNC", "1");
            }

            ArrayList<String> bindingPaths = new ArrayList<>();
            String drives = shortcut != null ? shortcut.getExtra("drives", container.getDrives()) : container.getDrives();
            for (String[] drive : Container.drivesIterator(drives)) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            guestProgramLauncherComponent.setBox64Preset(
                    shortcut != null
                            ? shortcut.getExtra("box64Preset", container.getBox64Preset())
                            : container.getBox64Preset()
            );

            guestProgramLauncherComponent.setFEXCorePreset(
                    shortcut != null
                            ? shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())
                            : container.getFEXCorePreset()
            );
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
        if (shortcut != null && "STEAM".equals(shortcut.getExtra("game_source"))) {
            Log.d("XServerDisplayActivity", "Adding SteamClientComponent for Steam game");
            environment.addComponent(new SteamClientComponent());
        }

        // Pass final envVars to the launcher
        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> {
            Log.d("XServerDisplayActivity", "Guest process terminated with status: " + status);
            exit();
        });

        // Add the launcher to our environment
        environment.addComponent(guestProgramLauncherComponent);

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
            frameRating.setVisibility(View.GONE);
            rootView.addView(frameRating);
        }

        // Get the fullscreen stretched extra from the shortcut if available
        String shortcutFullscreenStretched = shortcut != null ? shortcut.getExtra("fullscreenStretched") : null;

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

    private void ensureWinePrefixReady() {
        if (container == null || wineInfo == null) return;

        File containerDir = container.getRootDir();
        boolean prefixInvalid = !WineUtils.isPrefixValid(containerDir);
        String storedPrefixArch = container.getExtra("wineprefixArch");
        boolean archMismatch = !storedPrefixArch.isEmpty() && !storedPrefixArch.equalsIgnoreCase(wineInfo.getArch());
        boolean prefixNeedsUpdate = "t".equalsIgnoreCase(container.getExtra("wineprefixNeedsUpdate"));

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

    private void showInputControlsDialog() {
        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);
        dialog.setTitle(R.string.input_controls);
        dialog.setIcon(R.drawable.icon_input_controls);

        final Spinner sProfile = dialog.findViewById(R.id.SProfile);

        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        
        float density = getResources().getDisplayMetrics().density;
        com.winlator.cmod.widget.ChasingBorderDrawable animatedBorder = new com.winlator.cmod.widget.ChasingBorderDrawable(16f, 1.5f, density);
        android.graphics.drawable.GradientDrawable solidBg = new android.graphics.drawable.GradientDrawable();
        solidBg.setColor(android.graphics.Color.parseColor("#171A1C"));
        solidBg.setCornerRadius(16f * density);
        android.graphics.drawable.Drawable[] layers = {solidBg, animatedBorder};
        android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(layers);
        dialog.getContentView().setBackground(layerDrawable);
        dialog.getContentView().setClipToOutline(true);

        sProfile.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- "+getString(R.string.disabled)+" --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                    selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));
            sProfile.setSelection(selectedPosition);
        };
        loadProfileSpinner.run();

        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);
        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());

        final CheckBox cbEnableTimeout = dialog.findViewById(R.id.CBEnableTimeout);
        cbEnableTimeout.setChecked(preferences.getBoolean("touchscreen_timeout_enabled", false));

        final CheckBox cbEnableHaptics = dialog.findViewById(R.id.CBEnableHaptics);
        cbEnableHaptics.setChecked(preferences.getBoolean("touchscreen_haptics_enabled", false));

        // Auto-enable Show Touchscreen Controls when a profile is selected
        sProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    cbShowTouchscreenControls.setChecked(true);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        final Runnable updateProfile = () -> {
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles(true).get(position - 1));
            }
            else hideInputControls();
        };

        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {
            int position = sProfile.getSelectedItemPosition();
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

        dialog.setOnConfirmCallback(() -> {
            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());
            boolean isTimeoutEnabled = cbEnableTimeout.isChecked();
            boolean isHapticsEnabled = cbEnableHaptics.isChecked();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("show_touchscreen_controls_enabled", cbShowTouchscreenControls.isChecked());
            editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
            editor.apply();

            if (isTimeoutEnabled) {
                startTouchscreenTimeout(); // Start the timeout functionality if enabled
            } else {
                touchpadView.setOnTouchListener(null); // Disable the listener if timeout is disabled
            }
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles(true).get(position - 1));
            }
            else hideInputControls();
            updateProfile.run();
        });

        dialog.setOnCancelCallback(updateProfile::run);

        dialog.setCanceledOnTouchOutside(false);
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
            return selectedProfile;
        }

        ControlsProfile virtualFallback = findFirstVirtualProfile();
        if (virtualFallback != null) {
            int virtualIndex = profiles.indexOf(virtualFallback);
            if (virtualIndex >= 0) {
                preferences.edit().putInt("selected_profile_index", virtualIndex).apply();
            }
            return virtualFallback;
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

        File rootDir = imageFs.getRootDir();

        if (dxwrapper.contains("dxvk")) {
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
            String version = dxwrapperConfig.get("version");
            if (version.equals("1.11.1-sarek")) {
                Log.d("GraphicsDriverExtraction", "Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass");
                envVars.put("WRAPPER_NO_PATCH_OPCONSTCOMP", "1");
            }
        }
        else {
            WineD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
        }

        boolean useDRI3 = preferences.getBoolean("use_dri3", true);
        if (!useDRI3) {
            envVars.put("MESA_VK_WSI_DEBUG", "sw");
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
        Log.d("XServerDA", "dispatchGenericMotionEvent source=0x" + Integer.toHexString(event.getSource()) + " deviceId=" + event.getDeviceId() + " action=" + event.getAction());
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
        Log.d("XServerDA", "dispatchKeyEvent keyCode=" + event.getKeyCode() + " deviceId=" + event.getDeviceId() + " source=0x" + Integer.toHexString(event.getSource()) + " action=" + event.getAction());

        // Handle the PlayStation or Xbox Home button to open the drawer
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE || event.getKeyCode() == KeyEvent.KEYCODE_HOME || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT) {
                boolean handled = inputControlsView.onKeyEvent(event) || (winHandler != null && winHandler.onKeyEvent(event)) && (xServer != null && xServer.keyboard.onKeyEvent(event));
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
            String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();

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
    private void mountADriveOnContainer(Container c, String gamePath) {
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
                boolean hasCustomSteamExe = path != null &&
                        !path.isEmpty() &&
                        !path.equalsIgnoreCase("C:\\Program Files (x86)\\Steam\\steamclient_loader_x64.exe");

                if (hasCustomSteamExe) {
                    int lastBackslash = path.lastIndexOf("\\");
                    if (lastBackslash >= 0) {
                        String dir = path.substring(0, lastBackslash);
                        if (dir.endsWith(":")) dir += "\\";
                        String file = path.substring(lastBackslash + 1);

                        File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, dir);
                        if (nativeDir != null && nativeDir.exists()) {
                            launcherComponent.setWorkingDir(nativeDir);
                            Log.d("XServerDisplayActivity", "Set native working dir for Steam override: " + nativeDir.getPath());
                        }

                        if (wineInfo != null && wineInfo.isArm64EC()) {
                            args = "\"" + path + "\"";
                        } else {
                            args = "/dir \"" + dir + "\" \"" + file + "\"";
                        }
                    } else {
                        args = "\"" + path + "\"";
                    }
                } else if (!container.isUseLegacyDRM()) {
                    args = "/dir \"C:\\Program Files (x86)\\Steam\" \"steamclient_loader_x64.exe\"";
                    File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, "C:\\Program Files (x86)\\Steam");
                    if (nativeDir != null && nativeDir.exists()) launcherComponent.setWorkingDir(nativeDir);
                } else {
                    String gameExeWinPath = findGameExeWinPath(appId, new File(SteamBridge.getAppDirPath(appId)));
                    if (gameExeWinPath != null) {
                        int lastBackslash = gameExeWinPath.lastIndexOf("\\");
                        if (lastBackslash >= 0) {
                            String dir = gameExeWinPath.substring(0, lastBackslash);
                            if (dir.endsWith(":")) dir += "\\";
                            String file = gameExeWinPath.substring(lastBackslash + 1);
                            
                            File nativeDir = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, dir);
                            if (nativeDir != null && nativeDir.exists()) {
                                launcherComponent.setWorkingDir(nativeDir);
                                Log.d("XServerDisplayActivity", "Set native working dir for Steam process: " + nativeDir.getPath());
                            }
                            
                            if (wineInfo != null && wineInfo.isArm64EC()) {
                                args = "\"" + gameExeWinPath + "\"";
                            } else {
                                args = "/dir " + StringUtils.escapeDOSPath(dir) + " \"" + file + "\"";
                            }
                        } else {
                            args = "\"" + gameExeWinPath + "\"";
                        }
                    } else {
                        args = "\"wfm.exe\"";
                    }
                }
            } else if (gameSource.equals("EPIC") || gameSource.equals("GOG")) {
                String extraArgs = getIntent().getStringExtra("extra_exec_args");
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
                String extraArgs = shortcut.getExtra("execArgs");
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
     * Writes ColdClientLoader.ini with the correct game exe path for the Goldberg Steam emulator.
     * @param appId Steam app ID
     * @param gameExeWinPath Windows-style path to the game exe (e.g. A:\Among Us.exe)
     */
    private void writeColdClientIniForLaunch(int appId, String gameExeWinPath) {
        File iniFile = new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/ColdClientLoader.ini");
        iniFile.getParentFile().mkdirs();
        
        // Determine the exe directory for ExeRunDir
        String exeRunDir = "";
        int lastBackslash = gameExeWinPath.lastIndexOf("\\");
        if (lastBackslash >= 0) {
            exeRunDir = gameExeWinPath.substring(0, lastBackslash);
        }
        
        String iniContent = "[SteamClient]\n" +
                "\n" +
                "Exe=" + gameExeWinPath + "\n" +
                "ExeRunDir=" + exeRunDir + "\n" +
                "ExeCommandLine=\n" +
                "AppId=" + appId + "\n" +
                "\n" +
                "# path to the steamclient dlls\n" +
                "SteamClientDll=steamclient.dll\n" +
                "SteamClient64Dll=steamclient64.dll\n" +
                "\n" +
                "[Injection]\n" +
                "IgnoreLoaderArchDifference=1\n";
        
        FileUtils.writeString(iniFile, iniContent);
        Log.d("XServerDisplayActivity", "Wrote ColdClientLoader.ini: Exe=" + gameExeWinPath + " AppId=" + appId);
    }
    
    /**
     * Sets up steam_settings/ next to steamclient.dll in the Steam directory.
     * This is for the ColdClientLoader approach where Goldberg steamclient DLLs
     * read auth config from this location, NOT from next to the game's steam_api.dll.
     */
    private void setupSteamSettingsForColdClient(File steamDir, int appId) {
        // Get Steam user info from PluviaPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("PluviaPreferences", MODE_PRIVATE);
        String accountName = prefs.getString("user_name", "Player");
        long steamId64Long = prefs.getLong("steam_user_steam_id_64", 0L);
        String accountSteamId = steamId64Long > 0 ? String.valueOf(steamId64Long) : "76561198000000000";
        long accountId = steamId64Long > 0 ? (steamId64Long & 0xFFFFFFFFL) : 0L;
        String language = container.getExtra("containerLanguage", "english");
        if (language == null || language.isEmpty()) language = "english";
        
        // Get encrypted app ticket for online auth
        String ticketBase64 = null;
        try {
            ticketBase64 = SteamBridge.getEncryptedAppTicketBase64(appId);
            if (ticketBase64 != null) {
                Log.d("XServerDisplayActivity", "Got encrypted app ticket for ColdClient appId=" + appId);
            } else {
                Log.w("XServerDisplayActivity", "No encrypted app ticket for ColdClient appId=" + appId);
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to get encrypted app ticket for ColdClient", e);
        }
        
        // Create steam_settings directory next to steamclient.dll
        File settingsDir = new File(steamDir, "steam_settings");
        settingsDir.mkdirs();
        
        // steam_appid.txt
        FileUtils.writeString(new File(settingsDir, "steam_appid.txt"), String.valueOf(appId));
        FileUtils.writeString(new File(steamDir, "steam_appid.txt"), String.valueOf(appId));
        
        // configs.user.ini - account info + language + ticket
        StringBuilder userIni = new StringBuilder();
        userIni.append("[user::general]\n");
        userIni.append("account_name=").append(accountName).append("\n");
        userIni.append("account_steamid=").append(accountSteamId).append("\n");
        userIni.append("language=").append(language).append("\n");
        if (ticketBase64 != null && !ticketBase64.isEmpty()) {
            userIni.append("ticket=").append(ticketBase64).append("\n");
        }
        userIni.append("\n[user::saves]\n");
        userIni.append("local_save_path=C:\\Program Files (x86)\\Steam\\userdata\\").append(accountId).append("\n");
        
        FileUtils.writeString(new File(settingsDir, "configs.user.ini"), userIni.toString());
        
        // configs.main.ini - connectivity (enable online)
        String mainIni = "[main::connectivity]\n" +
                "disable_lan_only=1\n" +
                "[main::general]\n" +
                "enable_overlay=0\n";
        FileUtils.writeString(new File(settingsDir, "configs.main.ini"), mainIni);
        
        // configs.app.ini - DLC settings and cloud saves
        String appIni = "[app::dlcs]\n" +
                "unlock_all=1\n\n" +
                "[app::cloud_save::general]\n" +
                "create_default_dir=1\n" +
                "create_specific_dirs=1\n";
        FileUtils.writeString(new File(settingsDir, "configs.app.ini"), appIni);
        
        Log.d("XServerDisplayActivity", "Created ColdClient steam_settings in " + steamDir.getAbsolutePath() + 
                " (user=" + accountName + ", id=" + accountSteamId + ", ticket=" + (ticketBase64 != null ? "yes" : "no") + ")");
    }
    
    /**
     * Finds the game exe and returns its Windows-style path (e.g. A:\path\game.exe).
     * Uses shortcut.path if available, otherwise auto-detects from game install directory.
     */
    private String findGameExeWinPath(int appId, File gameDir) {
        // Check if shortcut already has a valid Windows path
        if (shortcut != null && shortcut.path != null && !shortcut.path.isEmpty() 
                && !shortcut.path.contains("steamclient_loader") 
                && shortcut.path.contains("\\")) {
            
            String safePath = shortcut.path;
            if (safePath.matches("^[A-Z]:[^\\\\/].*")) {
                safePath = safePath.substring(0, 2) + "\\" + safePath.substring(2);
            }
            return safePath;
        }
        
        // Auto-detect from game install directory
        String gameInstallPath = gameDir.getAbsolutePath();
        File exeFile = findGameExe(gameDir);
        if (exeFile != null) {
            String relativePath = exeFile.getAbsolutePath()
                    .substring(gameInstallPath.length());
            if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            return "A:\\" + relativePath.replace("/", "\\");
        }
        
        return null;
    }

    /**
     * Replaces all steam_api.dll and steam_api64.dll in the game directory
     * with our steampipe versions that communicate with the SteamPipeServer.
     * Backs up originals with .original suffix.
     */
    private void replaceSteamApiDlls(File gameDir) {
        if (gameDir == null || !gameDir.exists()) return;
        
        File[] files = gameDir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                replaceSteamApiDlls(file); // Recurse into subdirectories
            } else {
                String name = file.getName().toLowerCase();
                String assetName = null;
                
                if (name.equals("steam_api.dll")) {
                    assetName = "steampipe/steam_api.dll";
                } else if (name.equals("steam_api64.dll")) {
                    assetName = "steampipe/steam_api64.dll";
                }
                
                if (assetName != null) {
                    try {
                        // Backup original if not already backed up
                        File backup = new File(file.getParent(), file.getName() + ".original");
                        if (!backup.exists()) {
                            FileUtils.copy(file, backup);
                            Log.d("XServerDisplayActivity", "Backed up original: " + file.getName());
                        }
                        
                        // Replace with steampipe version
                        file.delete();
                        file.createNewFile();
                        try (java.io.InputStream is = getAssets().open(assetName);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) >= 0) {
                                fos.write(buf, 0, len);
                            }
                        }
                        Log.d("XServerDisplayActivity", "Replaced " + file.getName() + " with steampipe version at " + file.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e("XServerDisplayActivity", "Failed to replace " + file.getName(), e);
                    }
                }
            }
        }
    }

    /**
     * Restores the original steam_api.dll and steam_api64.dll in the game directory.
     * Required if a game was previously launched in Legacy DRM mode (which swaps them with stubs),
     * but is now being launched in ColdClientLoader mode (which requires the real DLLs).
     */
    private void restoreSteamApiDlls(File gameDir) {
        if (gameDir == null || !gameDir.exists()) return;
        
        File[] files = gameDir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                restoreSteamApiDlls(file);
            } else {
                String name = file.getName().toLowerCase();
                if (name.equals("steam_api.dll.original") || name.equals("steam_api64.dll.original")) {
                    try {
                        String originalName = file.getName().substring(0, file.getName().length() - ".original".length());
                        File target = new File(file.getParent(), originalName);
                        
                        if (target.exists()) target.delete();
                        FileUtils.copy(file, target);
                        
                        Log.d("XServerDisplayActivity", "Restored original " + originalName + " from backup");
                    } catch (Exception e) {
                        Log.e("XServerDisplayActivity", "Failed to restore " + file.getName(), e);
                    }
                }
            }
        }
    }


    public XServer getXServer() {
        return xServer;
    }

    /**
     * Creates the steam_settings directory next to each steam_api DLL with config files.
     * This is what the Goldberg/steampipe emulator reads for auth, language, connectivity, etc.
     * Matches GameNative's ensureSteamSettings approach.
     */
    private void setupSteamSettings(File gameDir, int appId) {
        if (gameDir == null || !gameDir.exists()) return;
        
        // Get Steam user info from PluviaPreferences (set during Steam login via PrefManager)
        android.content.SharedPreferences prefs = getSharedPreferences("PluviaPreferences", MODE_PRIVATE);
        String accountName = prefs.getString("user_name", "Player");
        long steamId64Long = prefs.getLong("steam_user_steam_id_64", 0L);
        String accountSteamId = steamId64Long > 0 ? String.valueOf(steamId64Long) : "76561198000000000";
        String language = container.getExtra("containerLanguage", "english");
        if (language == null || language.isEmpty()) language = "english";
        
        // Get encrypted app ticket for online auth (requires active Steam login)
        String ticketBase64 = null;
        try {
            ticketBase64 = SteamBridge.getEncryptedAppTicketBase64(appId);
            if (ticketBase64 != null) {
                Log.d("XServerDisplayActivity", "Got encrypted app ticket for appId=" + appId + " (length=" + ticketBase64.length() + ")");
            } else {
                Log.w("XServerDisplayActivity", "No encrypted app ticket available for appId=" + appId + " (Steam not logged in?)");
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to get encrypted app ticket", e);
        }
        
        // Find all directories containing steam_api DLLs and set up settings there
        setupSteamSettingsInDir(gameDir, appId, accountName, accountSteamId, language, ticketBase64);
    }
    
    private void setupSteamSettingsInDir(File dir, int appId, String accountName, String accountSteamId, String language, String ticketBase64) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        boolean hasSteamDll = false;
        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.equals("steam_api.dll") || name.equals("steam_api64.dll")) {
                    hasSteamDll = true;
                    // Generate steam_interfaces.txt from the backed-up original DLL
                    generateSteamInterfacesFile(dir, f.getName());
                }
            }
        }
        
        if (hasSteamDll) {
            // Write steam_appid.txt in this directory
            FileUtils.writeString(new File(dir, "steam_appid.txt"), String.valueOf(appId));
            
            // Create steam_settings directory
            File settingsDir = new File(dir, "steam_settings");
            settingsDir.mkdirs();
            
            // steam_appid.txt in settings dir too
            FileUtils.writeString(new File(settingsDir, "steam_appid.txt"), String.valueOf(appId));
            
            // configs.user.ini - account info + language + ticket
            StringBuilder userIni = new StringBuilder();
            userIni.append("[user::general]\n");
            userIni.append("account_name=").append(accountName).append("\n");
            userIni.append("account_steamid=").append(accountSteamId).append("\n");
            userIni.append("language=").append(language).append("\n");
            if (ticketBase64 != null && !ticketBase64.isEmpty()) {
                userIni.append("ticket=").append(ticketBase64).append("\n");
            }
            FileUtils.writeString(new File(settingsDir, "configs.user.ini"), userIni.toString());
            
            // configs.main.ini - connectivity (enable online)
            String mainIni = "[main::connectivity]\n" +
                    "disable_lan_only=1\n";
            FileUtils.writeString(new File(settingsDir, "configs.main.ini"), mainIni);
            
            // configs.app.ini - DLC settings
            String appIni = "[app::dlcs]\n" +
                    "unlock_all=1\n";
            FileUtils.writeString(new File(settingsDir, "configs.app.ini"), appIni);
            
            Log.d("XServerDisplayActivity", "Created steam_settings in " + dir.getAbsolutePath() + 
                    " (user=" + accountName + ", id=" + accountSteamId + ", lang=" + language + 
                    ", ticket=" + (ticketBase64 != null ? "yes" : "no") + ")");
        }
        
        // Recurse into subdirectories
        for (File f : files) {
            if (f.isDirectory() && !f.getName().equals("steam_settings")) {
                setupSteamSettingsInDir(f, appId, accountName, accountSteamId, language, ticketBase64);
            }
        }
    }
    
    /**
     * Generates steam_interfaces.txt by scanning the backed-up original DLL for
     * Steam interface version strings (e.g., SteamUser023, SteamApps008).
     * Matches GameNative's generateInterfacesFile approach.
     */
    private void generateSteamInterfacesFile(File dir, String dllName) {
        File interfacesFile = new File(dir, "steam_interfaces.txt");
        if (interfacesFile.exists()) return; // Already generated
        
        // Look for the .original backup
        File originalDll = new File(dir, dllName + ".original");
        if (!originalDll.exists()) return;
        
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(originalDll.toPath());
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
            // Flush final string
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
                Log.d("XServerDisplayActivity", "Generated steam_interfaces.txt with " + interfaces.size() + " interfaces");
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to generate steam_interfaces.txt", e);
        }
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
            
            // Create steam.cfg to prevent Steam bootstrap/update
            File steamCfg = new File(steamDir, "steam.cfg");
            if (!steamCfg.exists()) {
                FileUtils.writeString(steamCfg, "BootStrapperInhibitAll=Enable\nBootStrapperForceSelfUpdate=False\n");
            }
            
            // Create steamapps/common directory and symlink
            File steamappsDir = new File(steamDir, "steamapps");
            File commonDir = new File(steamappsDir, "common");
            commonDir.mkdirs();
            
            String gameName = gameDir.getName();
            File steamGameLink = new File(commonDir, gameName);
            if (!steamGameLink.exists()) {
                try {
                    java.nio.file.Files.createSymbolicLink(
                            steamGameLink.toPath(), gameDir.toPath());
                    Log.d("XServerDisplayActivity", "Created symlink: " + steamGameLink + " -> " + gameDir);
                } catch (Exception e) {
                    Log.w("XServerDisplayActivity", "Failed to create symlink, copying instead", e);
                }
            }
            
            // Create ACF manifest so Steam sees the game as installed
            String acfContent = "\"AppState\"\n" +
                    "{\n" +
                    "\t\"appid\"\t\t\"" + appId + "\"\n" +
                    "\t\"Universe\"\t\t\"1\"\n" +
                    "\t\"name\"\t\t\"" + gameName + "\"\n" +
                    "\t\"StateFlags\"\t\t\"4\"\n" +
                    "\t\"installdir\"\t\t\"" + gameName + "\"\n" +
                    "\t\"LastUpdated\"\t\t\"" + (System.currentTimeMillis() / 1000) + "\"\n" +
                    "\t\"BytesToDownload\"\t\t\"0\"\n" +
                    "\t\"BytesDownloaded\"\t\t\"0\"\n" +
                    "\t\"AutoUpdateBehavior\"\t\t\"0\"\n" +
                    "}\n";
            FileUtils.writeString(new File(steamappsDir, "appmanifest_" + appId + ".acf"), acfContent);
            
            // Set up Wine registry for Steam paths
            File userReg = new File(winePrefix, ".wine/user.reg");
            if (userReg.exists()) {
                try {
                    WineRegistryEditor reg = new WineRegistryEditor(userReg);
                    reg.setStringValue("Software\\Valve\\Steam", "AutoLoginUser", 
                            getSharedPreferences("PluviaPreferences", MODE_PRIVATE).getString("user_name", "Player"));
                    reg.setStringValue("Software\\Valve\\Steam", "SteamExe", 
                            "C:\\Program Files (x86)\\Steam\\steam.exe");
                    reg.setStringValue("Software\\Valve\\Steam", "SteamPath", 
                            "C:\\Program Files (x86)\\Steam");
                    reg.setStringValue("Software\\Valve\\Steam", "InstallPath", 
                            "C:\\Program Files (x86)\\Steam");
                    reg.close();
                    Log.d("XServerDisplayActivity", "Set Steam registry entries");
                } catch (Exception e) {
                    Log.w("XServerDisplayActivity", "Failed to set Steam registry entries", e);
                }
            }
            
            // Create config/loginusers.vdf
            android.content.SharedPreferences prefs = getSharedPreferences("PluviaPreferences", MODE_PRIVATE);
            String accountName = prefs.getString("user_name", "Player");
            long steamIdLong = prefs.getLong("steam_user_steam_id_64", 0L);
            String steamId64 = steamIdLong > 0 ? String.valueOf(steamIdLong) : "76561198000000000";
            File configDir = new File(steamDir, "config");
            configDir.mkdirs();
            String loginVdf = "\"users\"\n{\n\t\"" + steamId64 + "\"\n\t{\n" +
                    "\t\t\"AccountName\"\t\t\"" + accountName + "\"\n" +
                    "\t\t\"PersonaName\"\t\t\"" + accountName + "\"\n" +
                    "\t\t\"MostRecent\"\t\t\"1\"\n" +
                    "\t\t\"RememberPassword\"\t\t\"1\"\n" +
                    "\t\t\"Timestamp\"\t\t\"" + (System.currentTimeMillis() / 1000) + "\"\n" +
                    "\t}\n}\n";
            FileUtils.writeString(new File(configDir, "loginusers.vdf"), loginVdf);
            
            Log.d("XServerDisplayActivity", "Steam environment setup complete");
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to setup Steam environment", e);
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
        if (frameRating == null) return;

        if (property != null) {
            if (frameRatingWindowId == -1 && property.nameAsString().contains("_MESA_DRV")) {
                frameRatingWindowId = window.id;
                Log.d("XServerDisplayActivity", "Showing hud for Window " + window.getName());
                frameRating.update();
            }
            if (property.nameAsString().contains("_MESA_DRV_ENGINE_NAME")) {
                runOnUiThread(() -> frameRating.setRenderer(property.toString()));
            }
            if (property.nameAsString().contains("_MESA_DRV_GPU_NAME")) {
                runOnUiThread(() -> frameRating.setGpuName(property.toString()));
            }
        }
        else if (frameRatingWindowId != -1) {
            frameRatingWindowId = -1;
            Log.d("XServerDisplayActivity", "Hiding hud for Window " + window.getName());
            runOnUiThread(() -> frameRating.setVisibility(View.GONE));
            runOnUiThread(() -> frameRating.reset());
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
