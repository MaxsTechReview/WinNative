package com.winlator.cmod;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.box64.Box64PresetManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;

import com.winlator.cmod.contentdialog.AddEnvVarDialog;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog;
import com.winlator.cmod.contentdialog.WineD3DConfigDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.fexcore.FEXCoreManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.utils.IconFileUtils;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.widget.ColorPickerView;
import com.winlator.cmod.widget.EnvVarsView;
import com.winlator.cmod.widget.ImagePickerView;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xserver.XKeycode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ContainerDetailFragment extends Fragment {

    private static final String TAG = "FileUtils";

    private ContainerManager manager;
    private ContentsManager contentsManager;
    private final int containerId;
    private Shortcut shortcut;
    private Container container;
    private PreloaderDialog preloaderDialog;
    private JSONArray gpuCards;
    private Callback<String> openDirectoryCallback;
    private Callback<String> openFileCallback;
    private Callback<String> openIconFileCallback;
    private int createShortcutForAppId = 0;
    private String createShortcutForAppName = "";
    private String createShortcutForSource = "STEAM";
    private String createShortcutForGogId = "";

    private boolean isDarkMode;

    private ImageFs imageFs;

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
    }

    public ContainerDetailFragment(int containerId, int createShortcutForAppId, String createShortcutForAppName) {
        this(containerId, createShortcutForAppId, createShortcutForAppName, "STEAM");
    }

    public ContainerDetailFragment(int containerId, int createShortcutForAppId, String createShortcutForAppName, String source) {
        this(containerId, createShortcutForAppId, createShortcutForAppName, source, "");
    }

    public ContainerDetailFragment(int containerId, int createShortcutForAppId, String createShortcutForAppName, String source, String gogId) {
        this.containerId = containerId;
        this.createShortcutForAppId = createShortcutForAppId;
        this.createShortcutForAppName = createShortcutForAppName;
        this.createShortcutForSource = source != null ? source : "STEAM";
        this.createShortcutForGogId = gogId != null ? gogId : "";
    }

    public ContainerDetailFragment(Shortcut shortcut) {
        this.shortcut = shortcut;
        this.containerId = shortcut != null && shortcut.container != null ? shortcut.container.id : 0;
    }

    private static final String[] SDL2_ENV_VARS = {
            "SDL_JOYSTICK_WGI=0",
            "SDL_XINPUT_ENABLED=1",
            "SDL_JOYSTICK_RAWINPUT=0",
            "SDL_JOYSTICK_HIDAPI=1",
            "SDL_DIRECTINPUT_ENABLED=0",
            "SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1",
            "SDL_HINT_FORCE_RAISEWINDOW=0",
            "SDL_ALLOW_TOPMOST=0",
            "SDL_MOUSE_FOCUS_CLICKTHROUGH=1"
    };

    public static <T> ArrayAdapter<T> createThemedAdapter(Context context, java.util.List<T> items) {
        ArrayAdapter<T> adapter = new ArrayAdapter<>(context, R.layout.spinner_item_themed, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_themed);
        return adapter;
    }

    public static ArrayAdapter<String> createThemedAdapter(Context context, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.spinner_item_themed, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_themed);
        return adapter;
    }

    public static void applyThemedAdapter(Spinner spinner, int arrayResId) {
        Context context = spinner.getContext();
        String[] items = context.getResources().getStringArray(arrayResId);
        int selectedPos = spinner.getSelectedItemPosition();
        spinner.setAdapter(createThemedAdapter(context, items));
        if (selectedPos >= 0 && selectedPos < items.length) spinner.setSelection(selectedPos);
        applyPopupBackground(spinner);
    }

    public static void applyPopupBackground(Spinner spinner) {
        spinner.setPopupBackgroundResource(R.drawable.content_popup_menu_background);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());

        try {
            gpuCards = new JSONArray(FileUtils.readString(getContext(), "gpu_cards.json"));
        }
        catch (JSONException e) {}
    }

    private void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        if (textView == null) return;
        if (isDarkMode) {
            // Apply dark mode-specific attributes
            textView.setTextColor(Color.parseColor("#cccccc")); // Set text color to #cccccc
            textView.setBackgroundResource(R.color.window_background_color_dark); // Set dark background color
        } else {
            // Apply light mode-specific attributes (original FieldSetLabel)
            textView.setTextColor(Color.parseColor("#bdbdbd")); // Set text color to #bdbdbd
            textView.setBackgroundResource(R.color.window_background_color); // Set light background color
        }
    }


    private void applyDynamicStyles(View view, boolean isDarkMode) {


        // Update Spinners
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        if (sScreenSize != null) sScreenSize.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        if (sWineVersion != null) sWineVersion.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        if (sGraphicsDriver != null) sGraphicsDriver.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        if (sDXWrapper != null) sDXWrapper.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        if (sAudioDriver != null) sAudioDriver.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        if (sEmulator64 != null) sEmulator64.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        if (sEmulator != null) sEmulator.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        if (sMIDISoundFont != null) sMIDISoundFont.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        // Update Wine Configuration Tab Spinner styles
        // Desktop
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        if (sDesktopTheme != null) sDesktopTheme.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        if (sDesktopBackgroundType != null) sDesktopBackgroundType.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        if (sMouseWarpOverride != null) sMouseWarpOverride.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        // Win Components
        // Handled in createWinComponentsTab

        // Update Advanced Tab Spinner styles
        Spinner SDInputType = view.findViewById(R.id.SDInputType);
        if (SDInputType != null) SDInputType.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        if (sBox64Preset != null) sBox64Preset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        if (sBox64Version != null) sBox64Version.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        if (sFEXCoreVersion != null) sFEXCoreVersion.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        if (sFEXCorePreset != null) sFEXCorePreset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        if (sStartupSelection != null) sStartupSelection.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
    }

    private void applyDynamicStylesRecursively(View view, boolean isDarkMode) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                applyDynamicStylesRecursively(child, isDarkMode);
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if ("desktop".equals(textView.getText().toString())) { // Check for specific text if needed
                textView.setTextAppearance(getContext(), isDarkMode ? R.style.FieldSetLabel_Dark : R.style.FieldSetLabel);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                String path = uri != null ? FileUtils.getFilePathFromUri(getContext(), uri) : null;
                String fileName = uri != null ? FileUtils.getUriFileName(getContext(), uri) : null;

                if (openIconFileCallback != null) {
                    boolean validIcon = (path != null && isSupportedIconFile(path))
                            || (fileName != null && isSupportedIconFile(fileName));
                    if (validIcon && path != null) {
                        openIconFileCallback.call(path);
                    } else {
                        Toast.makeText(getContext(), R.string.select_valid_icon_file, Toast.LENGTH_SHORT).show();
                    }
                } else if (path != null && path.toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
                    if (openFileCallback != null) {
                        openFileCallback.call(path);
                    }
                } else if (path != null && fileName != null && fileName.toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
                    if (openFileCallback != null) {
                        openFileCallback.call(path);
                    }
                } else {
                    Toast.makeText(getContext(), R.string.select_valid_exe_file, Toast.LENGTH_SHORT).show();
                }
            }
            openFileCallback = null;
            openIconFileCallback = null;
        } else if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                Log.d(TAG, "URI obtained in onActivityResult: " + uri.toString());
                String path = FileUtils.getFilePathFromUri(getContext(), uri);
                Log.d(TAG, "File path in onActivityResult: " + path);
                if (path != null) {
                    if (openDirectoryCallback != null) {
                        openDirectoryCallback.call(path);
                    }
                } else {
                    Toast.makeText(getContext(), "Invalid directory selected", Toast.LENGTH_SHORT).show();
                }
            }
            openDirectoryCallback = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Activity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);
            }
        }

    }

    public boolean isEditMode() {
        return container != null || shortcut != null;
    }

    public boolean isShortcutMode() {
        return shortcut != null;
    }

    private boolean isCreateShortcutMode() {
        return createShortcutForAppId > 0 || ("GOG".equals(createShortcutForSource) && !createShortcutForGogId.isEmpty());
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        final View view;
        try {
            view = inflater.inflate(R.layout.container_detail_fragment, root, false);
        } catch (Throwable e) {
            Log.e(TAG, "FATAL: Failed to inflate container_detail_fragment layout", e);
            AppUtils.showToast(context, "Error: could not load container settings screen");
            View fallback = new FrameLayout(context);
            if (getActivity() != null) getActivity().onBackPressed();
            return fallback;
        }
        try {
        Log.d(TAG, "onCreateView: layout inflated");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Determine if dark mode is enabled
        isDarkMode = preferences.getBoolean("dark_mode", true); // Adjust this based on how you store theme info

        // Apply dynamic styles
        applyDynamicStyles(view, isDarkMode);

        // Apply dynamic styles recursively
//        applyDynamicStylesRecursively(view, isDarkMode);

        Log.d(TAG, "onCreateView: step 1 - creating ContainerManager");
        manager = new ContainerManager(context);
        
        if (shortcut != null) {
            container = shortcut.container;
        } else {
            container = containerId > 0 ? manager.getContainerById(containerId) : null;
        }
        
        Log.d(TAG, "onCreateView: step 2 - creating ContentsManager");
        contentsManager = new ContentsManager(context);
        contentsManager.syncContents();
        Log.d(TAG, "onCreateView: step 3 - contentsManager synced");

        final EditText etName = view.findViewById(R.id.ETName);
        final View llLaunchExe = view.findViewById(R.id.LLLaunchExe);
        final TextView btSelectIcon = view.findViewById(R.id.BTSelectIcon);
        final TextView btRevertIcon = view.findViewById(R.id.BTRevertIcon);
        final TextView btSelectExe = view.findViewById(R.id.BTSelectExe);
        final boolean showLaunchExeSelector = isShortcutMode() || isCreateShortcutMode();
        final String[] selectedExePath = new String[]{resolveInitialLaunchExePath()};
        final String existingCustomLibraryIconPath = isShortcutMode()
                ? shortcut.getExtra("customLibraryIconPath", shortcut.getExtra("customCoverArtPath"))
                : "";
        final String[] selectedIconPath = new String[]{existingCustomLibraryIconPath};

        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);

        // Ensure the Wine version layout is visible
        final LinearLayout llWineVersion = view.findViewById(R.id.LLWineVersion);
        llWineVersion.setVisibility(View.VISIBLE);

        // Set container name and graphics driver version based on mode
        if (isShortcutMode()) {
            etName.setText(shortcut.name);
            etName.setEnabled(false); // Can't rename shortcut here
        } else if (isEditMode() && container != null) {
            etName.setText(container.getName());
        } else if (isCreateShortcutMode()) {
            etName.setText(createShortcutForAppName);
        } else {
            etName.setText(getString(R.string.container) + "-" + manager.getNextContainerId());
        }

        llLaunchExe.setVisibility(showLaunchExeSelector ? View.VISIBLE : View.GONE);
        if (showLaunchExeSelector) {
            updateSelectIconButton(btSelectIcon, selectedIconPath[0]);
            updateRevertIconButton(btRevertIcon, selectedIconPath[0]);
            btSelectIcon.setOnClickListener((v) -> {
                openFileCallback = null;
                openIconFileCallback = (path) -> {
                    selectedIconPath[0] = path;
                    updateSelectIconButton(btSelectIcon, path);
                    updateRevertIconButton(btRevertIcon, path);
                };

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/png", "image/x-icon", "image/vnd.microsoft.icon"});
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
            });
            btRevertIcon.setOnClickListener((v) -> {
                selectedIconPath[0] = "";
                updateSelectIconButton(btSelectIcon, selectedIconPath[0]);
                updateRevertIconButton(btRevertIcon, selectedIconPath[0]);
                Toast.makeText(getContext(), R.string.revert_icon, Toast.LENGTH_SHORT).show();
            });

            updateLaunchExeButton(btSelectExe, selectedExePath[0]);
            btSelectExe.setOnClickListener((v) -> {
                openIconFileCallback = null;
                openFileCallback = (path) -> {
                    selectedExePath[0] = path;
                    updateLaunchExeButton(btSelectExe, path);
                };

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
            });
        }

        final Spinner sBox64Version = view.findViewById(R.id.SBox64Version);

        if (isDarkMode) {
            applyDarkMode(view);
        }

        Log.d(TAG, "onCreateView: step 4 - loading wine version spinner");
        loadWineVersionSpinner(view, sWineVersion, sBox64Version);
        Log.d(TAG, "onCreateView: step 5 - wine version spinner loaded");

        loadScreenSizeSpinner(view, isShortcutMode() ? shortcut.getExtra("screenSize", container != null ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE) : (isEditMode() && container != null ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE));

        final Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        
        final Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);

        final View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(isShortcutMode() ? shortcut.getExtra("dxwrapperConfig", container != null ? container.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG) : (isEditMode() && container != null ? container.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG));

        final View vGraphicsDriverConfig = view.findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(isShortcutMode() ? shortcut.getExtra("graphicsDriverConfig", container != null ? container.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG) : (isEditMode() && container != null ? container.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG));

        Log.d(TAG, "onCreateView: step 6 - loading graphics driver spinner");
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
                isShortcutMode() ? shortcut.getExtra("graphicsDriver", container != null ? container.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER) : (isEditMode() && container != null ? container.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER),
                isShortcutMode() ? shortcut.getExtra("dxwrapper", container != null ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER) : (isEditMode() && container != null ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER));
        Log.d(TAG, "onCreateView: step 7 - graphics driver spinner loaded");

        view.findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        applyThemedAdapter(sAudioDriver, R.array.audio_driver_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, isShortcutMode() ? shortcut.getExtra("audioDriver", container != null ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER) : (isEditMode() && container != null ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER));

        final Spinner sEmulator = view.findViewById(R.id.SEmulator);
        applyThemedAdapter(sEmulator, R.array.emulator_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, isShortcutMode() ? shortcut.getExtra("emulator", container != null ? container.getEmulator() : Container.DEFAULT_EMULATOR) : (isEditMode() && container != null ? container.getEmulator() : Container.DEFAULT_EMULATOR));

        final Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        applyThemedAdapter(sEmulator64, R.array.emulator_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, isShortcutMode() ? shortcut.getExtra("emulator64", container != null ? container.getEmulator64() : Container.DEFAULT_EMULATOR64) : (isEditMode() && container != null ? container.getEmulator64() : Container.DEFAULT_EMULATOR64));

        final View box64Frame = view.findViewById(R.id.box64Frame);
        final View fexcoreFrame = view.findViewById(R.id.fexcoreFrame);

        AdapterView.OnItemSelectedListener emulatorListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                updateEmulatorFrames(view, sEmulator, sEmulator64);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        sEmulator.setOnItemSelectedListener(emulatorListener);
        sEmulator64.setOnItemSelectedListener(emulatorListener);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        MidiManager.loadSFSpinner(sMIDISoundFont);
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, isShortcutMode() ? shortcut.getExtra("midiSoundFont", container != null ? container.getMIDISoundFont() : "") : (isEditMode() && container != null ? container.getMIDISoundFont() : ""));

        final CompoundButton cbShowFPS = view.findViewById(R.id.CBShowFPS);
        cbShowFPS.setChecked(isShortcutMode() ? shortcut.getExtra("showFPS", container != null && container.isShowFPS() ? "1" : "0").equals("1") : (isEditMode() && container != null && container.isShowFPS()));

        final CompoundButton cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        cbFullscreenStretched.setChecked(isShortcutMode() ? shortcut.getExtra("fullscreenStretched", container != null && container.isFullscreenStretched() ? "1" : "0").equals("1") : (isEditMode() && container != null && container.isFullscreenStretched()));

        // Existing declarations of UI components and variables
        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.enable_xinput_and_dinput_same_time, null);
        final CompoundButton cbEnableXInput = view.findViewById(R.id.CBEnableXInput);
        final CompoundButton cbEnableDInput = view.findViewById(R.id.CBEnableDInput);
        final CompoundButton cbExclusiveInput = view.findViewById(R.id.CBExclusiveInput);
        final View llExclusiveInput = view.findViewById(R.id.LLExclusiveInput);
        final View llDInputType = view.findViewById(R.id.LLDinputMapperType);
        final View btHelpXInput = view.findViewById(R.id.BTXInputHelp);
        final View btHelpDInput = view.findViewById(R.id.BTDInputHelp);
        final Spinner SDInputType = view.findViewById(R.id.SDInputType);
        applyThemedAdapter(SDInputType, R.array.dinput_mapper_type_entries);

        // Check if we are in edit mode to set input type accordingly
        int inputType = isShortcutMode() ? Integer.parseInt(shortcut.getExtra("inputType", String.valueOf(container != null ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE))) : (isEditMode() && container != null ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE);

        // New logic for enabling XInput and DInput
        cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
        cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);

        cbEnableDInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llDInputType.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked && cbEnableXInput.isChecked())
                showInputWarning.run();
        });

        cbEnableXInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && cbEnableDInput.isChecked())
                showInputWarning.run();
        });

        SDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
        llDInputType.setVisibility(cbEnableDInput.isChecked() ? View.VISIBLE : View.GONE);

        if (cbExclusiveInput != null && llExclusiveInput != null) {
            boolean exclusiveInputEnabled = isShortcutMode()
                    ? "1".equals(shortcut.getExtra("disableXinput", "0"))
                    : preferences.getBoolean("xinput_toggle", false);
            cbExclusiveInput.setChecked(exclusiveInputEnabled);
            llExclusiveInput.setVisibility(View.VISIBLE);

            Runnable applyExclusiveInputUiState = () -> {
                boolean exclusiveOn = cbExclusiveInput.isChecked();
                if (!exclusiveOn) {
                    // Ludashi behavior: with Exclusive Input OFF, keep both APIs ON and locked.
                    cbEnableXInput.setChecked(true);
                    cbEnableDInput.setChecked(true);
                }
                cbEnableXInput.setEnabled(exclusiveOn);
                cbEnableDInput.setEnabled(exclusiveOn);
                llDInputType.setVisibility(cbEnableDInput.isChecked() ? View.VISIBLE : View.GONE);
            };

            cbExclusiveInput.setOnCheckedChangeListener((buttonView, isChecked) -> applyExclusiveInputUiState.run());
            applyExclusiveInputUiState.run();
        }

        btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_xinput));
        btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_dinput));

        final CompoundButton cbSdl2Toggle = view.findViewById(R.id.CBSdl2Toggle);
        String envVarsValue = isShortcutMode() ? shortcut.getExtra("envVars", container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS) : (isEditMode() && container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS);
        cbSdl2Toggle.setChecked(envVarsValue.contains("SDL_XINPUT_ENABLED=1"));

        final EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        Locale systemLocal = Locale.getDefault();
        etLC_ALL.setText(isShortcutMode() ? shortcut.getExtra("lc_all", container != null ? container.getLC_ALL() : "") : (isEditMode() && container != null ? container.getLC_ALL() : systemLocal.getLanguage() + '_' + systemLocal.getCountry() + ".UTF-8"));

        final View btShowLCALL = view.findViewById(R.id.BTShowLCALL);
        btShowLCALL.setOnClickListener(v -> {
            Context themedContext = new android.view.ContextThemeWrapper(context, R.style.ThemeOverlay_ContentPopupMenu);
            PopupMenu popupMenu = new PopupMenu(themedContext, v);
            String[] lcs = getResources().getStringArray(R.array.some_lc_all);
            for (int i = 0; i < lcs.length; i++)
                popupMenu.getMenu().add(Menu.NONE, i, Menu.NONE, lcs[i]);
            popupMenu.setOnMenuItemClickListener(item -> {
                etLC_ALL.setText(item.toString() + ".UTF-8");
                return true;
            });
            popupMenu.show();
        });

        final Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        applyThemedAdapter(sStartupSelection, R.array.startup_selection_entries);
        byte previousStartupSelection = isShortcutMode() ? (byte)Integer.parseInt(shortcut.getExtra("startupSelection", String.valueOf(container != null ? container.getStartupSelection() : -1))) : (isEditMode() && container != null ? container.getStartupSelection() : -1);
        sStartupSelection.setSelection(previousStartupSelection != -1 ? previousStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Box64PresetManager.loadSpinner("box64", sBox64Preset, isShortcutMode() ? shortcut.getExtra("box64Preset", container != null ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.COMPATIBILITY)) : (isEditMode() && container != null ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.COMPATIBILITY)));

        final Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion, isShortcutMode() ? shortcut.getExtra("fexcoreVersion", container != null ? container.getFEXCoreVersion() : DefaultVersion.FEXCORE) : (isEditMode() && container != null ? container.getFEXCoreVersion() : DefaultVersion.FEXCORE));

        final Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        FEXCorePresetManager.loadSpinner(sFEXCorePreset, isShortcutMode() ? shortcut.getExtra("fexcorePreset", container != null ? container.getFEXCorePreset() : preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE)) : (isEditMode() && container != null ? container.getFEXCorePreset() : preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE)));

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(isShortcutMode() ? shortcut.getExtra("cpuList", container != null ? container.getCPUList(true) : Container.getFallbackCPUList()) : (isEditMode() && container != null ? container.getCPUList(true) : Container.getFallbackCPUList()));
        cpuListViewWoW64.setCheckedCPUList(isShortcutMode() ? shortcut.getExtra("cpuListWoW64", container != null ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64()) : (isEditMode() && container != null ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64()));

        final Spinner sPrimaryController = view.findViewById(R.id.SPrimaryController);
        applyThemedAdapter(sPrimaryController, R.array.xr_controllers);
        sPrimaryController.setSelection(isShortcutMode() ? Integer.parseInt(shortcut.getExtra("primaryController", String.valueOf(container != null ? container.getPrimaryController() : 1))) : (isEditMode() && container != null ? container.getPrimaryController() : 1));
        setControllerMapping(view.findViewById(R.id.SButtonA), Container.XrControllerMapping.BUTTON_A, XKeycode.KEY_A.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonB), Container.XrControllerMapping.BUTTON_B, XKeycode.KEY_B.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonX), Container.XrControllerMapping.BUTTON_X, XKeycode.KEY_X.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonY), Container.XrControllerMapping.BUTTON_Y, XKeycode.KEY_Y.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonGrip), Container.XrControllerMapping.BUTTON_GRIP, XKeycode.KEY_SPACE.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonTrigger), Container.XrControllerMapping.BUTTON_TRIGGER, XKeycode.KEY_ENTER.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickUp), Container.XrControllerMapping.THUMBSTICK_UP, XKeycode.KEY_UP.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickDown), Container.XrControllerMapping.THUMBSTICK_DOWN, XKeycode.KEY_DOWN.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickLeft), Container.XrControllerMapping.THUMBSTICK_LEFT, XKeycode.KEY_LEFT.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickRight), Container.XrControllerMapping.THUMBSTICK_RIGHT, XKeycode.KEY_RIGHT.ordinal());

        createWineConfigurationTab(view);
        final EnvVarsView envVarsView = createEnvVarsTab(view);
        createWinComponentsTab(view, isShortcutMode() ? shortcut.getExtra("wincomponents", container != null ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS) : (isEditMode() && container != null ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS));
        createDrivesTab(view);

        setupExpandableSections(view);

        // Set up confirm button with press animation
        View btnConfirm = view.findViewById(R.id.BTConfirm);
        btnConfirm.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    break;
            }
            return false;
        });
        btnConfirm.setOnClickListener((v) -> {
            try {
                // Capture and set container properties based on UI inputs
                String name = etName.getText().toString();
                String screenSize = getScreenSize(view);
                String envVars = envVarsView.getEnvVars();
                String graphicsDriver = sGraphicsDriver.getSelectedItem() != null ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()) : Container.DEFAULT_GRAPHICS_DRIVER;
                String graphicsDriverConfig = vGraphicsDriverConfig.getTag() != null ? vGraphicsDriverConfig.getTag().toString() : "";
                HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
                if (config.get("version") == null || config.get("version").isEmpty()) {
                    String defaultVersion;
                    try {
                        defaultVersion = GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context) ? DefaultVersion.WRAPPER_ADRENO : DefaultVersion.WRAPPER;
                    } catch (Throwable e) {
                        Log.w(TAG, "Error checking driver support for default version", e);
                        defaultVersion = DefaultVersion.WRAPPER;
                    }
                    config.put("version", defaultVersion);
                    graphicsDriverConfig = GraphicsDriverConfigDialog.toGraphicsDriverConfig(config);
                }
                String dxwrapper = sDXWrapper.getSelectedItem() != null ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()) : Container.DEFAULT_DXWRAPPER;
                String dxwrapperConfig = vDXWrapperConfig.getTag() != null ? vDXWrapperConfig.getTag().toString() : "";
                String audioDriver = sAudioDriver.getSelectedItem() != null ? StringUtils.parseIdentifier(sAudioDriver.getSelectedItem()) : Container.DEFAULT_AUDIO_DRIVER;
                String emulator = sEmulator.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator.getSelectedItem()) : Container.DEFAULT_EMULATOR;
                String emulator64 = sEmulator64.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator64.getSelectedItem()) : Container.DEFAULT_EMULATOR64;
                String wincomponents = getWinComponents(view);
                String drives = getDrives(view);
                boolean showFPS = cbShowFPS.isChecked();
                boolean fullscreenStretched = cbFullscreenStretched.isChecked();
                String cpuList = cpuListView.getCheckedCPUListAsString();
                String cpuListWoW64 = cpuListViewWoW64.getCheckedCPUListAsString();
                byte startupSelection = (byte) Math.max(0, sStartupSelection.getSelectedItemPosition());
                String box64Version = sBox64Version.getSelectedItem() != null ? sBox64Version.getSelectedItem().toString() : DefaultVersion.BOX64;
                String fexcoreVersion = sFEXCoreVersion.getSelectedItem() != null ? sFEXCoreVersion.getSelectedItem().toString() : DefaultVersion.FEXCORE;
                String fexcorePreset = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
                String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
                String desktopTheme = getDesktopTheme(view);
                // Capture missing properties
                String midiSoundFont = (sMIDISoundFont.getSelectedItemPosition() <= 0 || sMIDISoundFont.getSelectedItem() == null) ? "" : sMIDISoundFont.getSelectedItem().toString();
                String lc_all = etLC_ALL.getText().toString();
                int primaryController = Math.max(0, sPrimaryController.getSelectedItemPosition());
                String controllerMapping = getControllerMapping(view);

                // Define final input type
                int finalInputType = 0;
                finalInputType |= cbEnableXInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
                finalInputType |= cbEnableDInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
                int rawDInputPos = SDInputType.getSelectedItemPosition();
                finalInputType |= (rawDInputPos <= 0) ? WinHandler.FLAG_DINPUT_MAPPER_STANDARD : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;

                // Handle SDL2 environment variables based on the toggle state
                if (cbSdl2Toggle.isChecked()) {
                    // Add SDL2 environment variables if the toggle is enabled
                    for (String envVar : SDL2_ENV_VARS) {
                        if (!envVars.contains(envVar)) {
                            envVars += (envVars.isEmpty() ? "" : " ") + envVar;
                        }
                    }
                } else {
                    // Remove SDL2 environment variables if the toggle is disabled
                    for (String envVar : SDL2_ENV_VARS) {
                        envVars = envVars.replace(envVar, "").replaceAll("\\s{2,}", " ").trim();
                    }
                }

                String customLibraryIconPath = existingCustomLibraryIconPath != null ? existingCustomLibraryIconPath : "";
                if (showLaunchExeSelector) {
                    String requestedIconPath = selectedIconPath[0] != null ? selectedIconPath[0].trim() : "";
                    if (requestedIconPath.isEmpty()) {
                        customLibraryIconPath = "";
                    } else if (isShortcutMode()
                            && requestedIconPath.equals(existingCustomLibraryIconPath)
                            && new File(requestedIconPath).isFile()) {
                        customLibraryIconPath = requestedIconPath;
                    } else {
                        String iconStorageId = buildShortcutLibraryIconId(
                                isShortcutMode() ? shortcut.name : createShortcutForAppName,
                                isShortcutMode() ? shortcut : null,
                                createShortcutForSource,
                                createShortcutForAppId,
                                createShortcutForGogId
                        );
                        customLibraryIconPath = persistShortcutLibraryIcon(requestedIconPath, iconStorageId);
                        if (customLibraryIconPath.isEmpty()) {
                            Toast.makeText(context, R.string.select_valid_icon_file, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedIconPath[0] = customLibraryIconPath;
                        updateSelectIconButton(btSelectIcon, customLibraryIconPath);
                        updateRevertIconButton(btRevertIcon, customLibraryIconPath);
                    }
                }

                if (isShortcutMode()) {
                    String gameSource = shortcut.getExtra("game_source", createShortcutForSource);
                    if (showLaunchExeSelector && selectedExePath[0] != null && !selectedExePath[0].isEmpty()) {
                        shortcut.putExtra("launch_exe_path", selectedExePath[0]);
                        if ("CUSTOM".equals(gameSource)) {
                            shortcut.putExtra("custom_exe", selectedExePath[0]);
                        }
                        rewriteShortcutExecLine(shortcut.file, buildExecCommandForSource(gameSource, shortcutAppId(), shortcutGogId(), shortcut, selectedExePath[0]));
                    }

                    // Save overrides to Shortcut extraData
                    shortcut.putExtra("screenSize", screenSize);
                    shortcut.putExtra("envVars", envVars);
                    shortcut.putExtra("cpuList", cpuList);
                    shortcut.putExtra("cpuListWoW64", cpuListWoW64);
                    shortcut.putExtra("graphicsDriver", graphicsDriver);
                    shortcut.putExtra("graphicsDriverConfig", graphicsDriverConfig);
                    shortcut.putExtra("dxwrapper", dxwrapper);
                    shortcut.putExtra("dxwrapperConfig", dxwrapperConfig);
                    shortcut.putExtra("audioDriver", audioDriver);
                    shortcut.putExtra("emulator", emulator);
                    shortcut.putExtra("emulator64", emulator64);
                    shortcut.putExtra("wincomponents", wincomponents);
                    shortcut.putExtra("drives", drives);
                    shortcut.putExtra("showFPS", showFPS ? "1" : "0");
                    shortcut.putExtra("fullscreenStretched", fullscreenStretched ? "1" : "0");
                    shortcut.putExtra("inputType", String.valueOf(finalInputType));
                    if (cbExclusiveInput != null) {
                        shortcut.putExtra("disableXinput", cbExclusiveInput.isChecked() ? "1" : null);
                    }
                    shortcut.putExtra("startupSelection", String.valueOf(startupSelection));
                    shortcut.putExtra("box64Version", box64Version);
                    shortcut.putExtra("box64Preset", box64Preset);
                    shortcut.putExtra("fexcoreVersion", fexcoreVersion);
                    shortcut.putExtra("fexcorePreset", fexcorePreset);
                    shortcut.putExtra("desktopTheme", desktopTheme);
                    shortcut.putExtra("midiSoundFont", midiSoundFont);
                    shortcut.putExtra("lc_all", lc_all);
                    shortcut.putExtra("primaryController", String.valueOf(primaryController));
                    shortcut.putExtra("controllerMapping", controllerMapping);
                    shortcut.putExtra("customLibraryIconPath", customLibraryIconPath.isEmpty() ? null : customLibraryIconPath);
                    shortcut.putExtra("customCoverArtPath", customLibraryIconPath.isEmpty() ? null : customLibraryIconPath);
                    
                    // Handle container_id override
                    boolean saved = false;
                    if (sWineVersion.getSelectedItem() != null) {
                        String selection = sWineVersion.getSelectedItem().toString();
                        if (selection.startsWith("Container: ")) {
                            String cname = selection.substring(11);
                            for (Container c : manager.getContainers()) {
                                if (c.getName().equals(cname)) {
                                    shortcut.putExtra("container_id", String.valueOf(c.id));
                                    shortcut.putExtra("wineVersion", c.getWineVersion());
                                    shortcut.saveData();
                                    saved = true;

                                    if (c.id != shortcut.container.id) {                                        // Move the file physically to the new container's desktop
                                        java.io.File newDesktopDir = c.getDesktopDir();
                                        if (!newDesktopDir.exists()) newDesktopDir.mkdirs();
                                        java.io.File newShortcutFile = new java.io.File(newDesktopDir, shortcut.file.getName());
                                        com.winlator.cmod.core.FileUtils.copy(shortcut.file, newShortcutFile);
                                        shortcut.file.delete();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    if (!saved) {
                        shortcut.saveData();
                    }

                    getActivity().onBackPressed();                } else if (isEditMode()) {
                    // Update existing container properties
                    container.setName(name);
                    container.setScreenSize(screenSize);
                    container.setEnvVars(envVars);
                    container.setCPUList(cpuList);
                    container.setCPUListWoW64(cpuListWoW64);
                    container.setGraphicsDriver(graphicsDriver);
                    container.setGraphicsDriverConfig(graphicsDriverConfig);
                    container.setDXWrapper(dxwrapper);
                    container.setDXWrapperConfig(dxwrapperConfig);
                    container.setAudioDriver(audioDriver);
                    container.setEmulator(emulator);
                    container.setEmulator64(emulator64);
                    container.setWinComponents(wincomponents);
                    container.setDrives(drives);
                    container.setShowFPS(showFPS);
                    container.setFullscreenStretched(fullscreenStretched);
                    container.setInputType(finalInputType);
                    container.setStartupSelection(startupSelection);
                    container.setBox64Version(box64Version);
                    container.setBox64Preset(box64Preset);
                    container.setFEXCoreVersion(fexcoreVersion);
                    container.setFEXCorePreset(fexcorePreset);
                    container.setDesktopTheme(desktopTheme);
                    container.setMidiSoundFont(midiSoundFont);
                    container.setLC_ALL(lc_all);
                    container.setPrimaryController(primaryController);
                    container.setControllerMapping(controllerMapping);
                    container.saveData();
                    if (cbExclusiveInput != null) {
                        preferences.edit().putBoolean("xinput_toggle", cbExclusiveInput.isChecked()).apply();
                    }
                    saveWineRegistryKeys(view);
                    getActivity().onBackPressed();
                } else if (isCreateShortcutMode()) {
                    JSONObject data = new JSONObject();
                    data.put("screenSize", screenSize);
                    data.put("envVars", envVars);
                    data.put("cpuList", cpuList);
                    data.put("cpuListWoW64", cpuListWoW64);
                    data.put("graphicsDriver", graphicsDriver);
                    data.put("graphicsDriverConfig", graphicsDriverConfig);
                    data.put("dxwrapper", dxwrapper);
                    data.put("dxwrapperConfig", dxwrapperConfig);
                    data.put("audioDriver", audioDriver);
                    data.put("emulator", emulator);
                    data.put("emulator64", emulator64);
                    data.put("wincomponents", wincomponents);
                    data.put("drives", drives);
                    data.put("showFPS", showFPS);
                    data.put("fullscreenStretched", fullscreenStretched);
                    data.put("inputType", finalInputType);
                    data.put("startupSelection", startupSelection);
                    data.put("box64Version", box64Version);
                    data.put("box64Preset", box64Preset);
                    data.put("fexcoreVersion", fexcoreVersion);
                    data.put("fexcorePreset", fexcorePreset);
                    data.put("desktopTheme", desktopTheme);
                    String selectedWineStr = sWineVersion.getSelectedItem() != null ? sWineVersion.getSelectedItem().toString() : WineInfo.MAIN_WINE_VERSION.identifier();
                    // Resolve container name to actual wine version
                    String finalWineVersion = selectedWineStr;
                    if (selectedWineStr.startsWith("Container: ")) {
                        String cname = selectedWineStr.substring("Container: ".length());
                        for (Container c : manager.getContainers()) {
                            if (c.getName().equals(cname)) {
                                finalWineVersion = c.getWineVersion();
                                break;
                            }
                        }
                    }
                    data.put("wineVersion", finalWineVersion);
                    data.put("midiSoundFont", midiSoundFont);
                    data.put("lc_all", lc_all);
                    data.put("primaryController", primaryController);
                    data.put("controllerMapping", controllerMapping);
                    if (!customLibraryIconPath.isEmpty()) {
                        data.put("customLibraryIconPath", customLibraryIconPath);
                        data.put("customCoverArtPath", customLibraryIconPath);
                    }
                    if (showLaunchExeSelector && selectedExePath[0] != null && !selectedExePath[0].isEmpty()) {
                        data.put("launchExePath", selectedExePath[0]);
                        if ("EPIC".equals(createShortcutForSource) || "GOG".equals(createShortcutForSource)) {
                            String gameInstallPath = resolveBaseGameDirectory(createShortcutForSource, createShortcutForAppId, createShortcutForGogId, null, selectedExePath[0]);
                            if (!gameInstallPath.isEmpty()) {
                                data.put("game_install_path", gameInstallPath);
                            }
                        }
                    }

                    // Start by picking the selected container
                    Container targetContainer = null;
                    if (sWineVersion.getSelectedItem() != null) {
                        String selection = sWineVersion.getSelectedItem().toString();
                        if (selection.startsWith("Container: ")) {
                            String cname = selection.substring(11);
                            for (Container c : manager.getContainers()) {
                                if (c.getName().equals(cname)) {
                                    targetContainer = c;
                                    break;
                                }
                            }
                        }
                    }

                    // Fallback to searching for matching version if somehow they selected a raw version
                    if (targetContainer == null) {
                        for (Container c : manager.getContainers()) {
                            if (c.getWineVersion().equals(finalWineVersion)) {
                                targetContainer = c;
                                break;
                            }
                        }
                    }

                    if (targetContainer != null) {
                        try {
                            createShortcutOnContainer(targetContainer, data);
                            getActivity().onBackPressed();
                        } catch (Exception ex) {
                            AppUtils.showToast(context, "Error creating shortcut: " + ex.getMessage());
                        }
                    } else {
                        data.put("name", "Container-" + manager.getNextContainerId());
                        preloaderDialog.show(R.string.creating_container);
                        
                        File imageFsRoot = new File(context.getFilesDir(), "imagefs");
                        imageFs = ImageFs.find(imageFsRoot);

                        manager.createContainerAsync(data, contentsManager, (newContainer) -> {
                            if (newContainer != null) {
                                try {
                                    createShortcutOnContainer(newContainer, data);
                                } catch (Exception ex) {
                                    AppUtils.showToast(context, "Error creating shortcut: " + ex.getMessage());
                                }
                            } else {
                                AppUtils.showToast(context, R.string.unable_to_install_system_files);
                            }
                            preloaderDialog.close();
                            if (getActivity() != null) {
                                getActivity().onBackPressed();
                            }
                        });
                    }
                } else {
                    // Create new container with specified properties
                    JSONObject data = new JSONObject();
                    data.put("name", name);
                    data.put("screenSize", screenSize);
                    data.put("envVars", envVars);
                    data.put("cpuList", cpuList);
                    data.put("cpuListWoW64", cpuListWoW64);
                    data.put("graphicsDriver", graphicsDriver);
                    data.put("graphicsDriverConfig", graphicsDriverConfig);
                    data.put("dxwrapper", dxwrapper);
                    data.put("dxwrapperConfig", dxwrapperConfig);
                    data.put("audioDriver", audioDriver);
                    data.put("emulator", emulator);
                    data.put("emulator64", emulator64);
                    data.put("wincomponents", wincomponents);
                    data.put("drives", drives);
                    data.put("showFPS", showFPS);
                    data.put("fullscreenStretched", fullscreenStretched);
                    data.put("inputType", finalInputType);
                    data.put("startupSelection", startupSelection);
                    data.put("box64Version", box64Version);
                    data.put("box64Preset", box64Preset);
                    data.put("fexcoreVersion", fexcoreVersion);
                    data.put("fexcorePreset", fexcorePreset);
                    data.put("desktopTheme", desktopTheme);
                    String selectedWineStr = sWineVersion.getSelectedItem() != null ? sWineVersion.getSelectedItem().toString() : WineInfo.MAIN_WINE_VERSION.identifier();
                    // Resolve container name to actual wine version
                    if (selectedWineStr.startsWith("Container: ")) {
                        String cname = selectedWineStr.substring("Container: ".length());
                        for (Container c : manager.getContainers()) {
                            if (c.getName().equals(cname)) {
                                selectedWineStr = c.getWineVersion();
                                break;
                            }
                        }
                    }
                    data.put("wineVersion", selectedWineStr);
                    data.put("midiSoundFont", midiSoundFont);
                    data.put("lc_all", lc_all);
                    data.put("primaryController", primaryController);
                    data.put("controllerMapping", controllerMapping);

                    preloaderDialog.show(R.string.creating_container);

                    // Initialize ImageFs
                    File imageFsRoot = new File(context.getFilesDir(), "imagefs");
                    imageFs = ImageFs.find(imageFsRoot);


                    manager.createContainerAsync(data, contentsManager, (newContainer) -> {
                        if (newContainer != null) {
                            this.container = newContainer;
                            saveWineRegistryKeys(view);
                        } else {
                            AppUtils.showToast(context, R.string.unable_to_install_system_files);
                        }
                        preloaderDialog.close();
                        if (getActivity() != null) {
                            getActivity().onBackPressed();
                        }
                    });
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error saving container data", e);
                AppUtils.showToast(context, "Error: " + e.getMessage());
            } catch (Throwable e) {
                Log.e(TAG, "Unexpected error saving container", e);
                AppUtils.showToast(context, "Unexpected error: " + e.getMessage());
            }
        });
        Log.d(TAG, "onCreateView: completed successfully");
        return view;
        } catch (Throwable e) {
            Log.e(TAG, "FATAL: Error in onCreateView setup", e);
            try {
                AppUtils.showToast(context, "Error loading container settings: " + e.getMessage());
            } catch (Throwable ignored) {}
            if (getActivity() != null) {
                try { getActivity().onBackPressed(); } catch (Throwable ignored) {}
            }
            return view;
        }
    }

    private void saveWineRegistryKeys(View view) {
        if (container == null || container.getRootDir() == null) return;
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        if (!userRegFile.exists()) return;
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            if (sMouseWarpOverride != null && sMouseWarpOverride.getSelectedItem() != null) {
                registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", sMouseWarpOverride.getSelectedItem().toString().toLowerCase(Locale.ENGLISH));
            }
        }
    }

    private void setupExpandableSections(View view) {
        final int[][] sections = {
            { R.id.LLHeaderWineConfiguration, R.id.LLTabWineConfiguration, R.id.IVChevronWineConfiguration },
            { R.id.LLHeaderWinComponents, R.id.LLTabWinComponents, R.id.IVChevronWinComponents },
            { R.id.LLHeaderEnvVars, R.id.LLTabEnvVars, R.id.IVChevronEnvVars },
            { R.id.LLHeaderDrives, R.id.LLTabDrives, R.id.IVChevronDrives },
            { R.id.LLHeaderAdvanced, R.id.LLTabAdvanced, R.id.IVChevronAdvanced },
            { R.id.LLHeaderXR, R.id.LLTabXR, R.id.IVChevronXR },
        };

        for (int[] section : sections) {
            View header = view.findViewById(section[0]);
            View content = view.findViewById(section[1]);
            ImageView chevron = view.findViewById(section[2]);

            // Hide XR section if not supported
            if (section[0] == R.id.LLHeaderXR && !XrActivity.isSupported()) {
                View card = header.getParent() instanceof View ? (View) header.getParent() : header;
                card.setVisibility(View.GONE);
                continue;
            }

            header.setOnClickListener(v -> {
                boolean isExpanded = content.getVisibility() == View.VISIBLE;
                chevron.animate().rotation(isExpanded ? 0f : 90f).setDuration(200).start();
                if (isExpanded) {
                    animateCollapse(content);
                } else {
                    animateExpand(content);
                }
            });
        }
    }

    private static void animateExpand(View view) {
        view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int targetHeight = view.getMeasuredHeight();
        view.getLayoutParams().height = 0;
        view.setVisibility(View.VISIBLE);
        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        animator.setDuration(250);
        animator.addUpdateListener(a -> {
            view.getLayoutParams().height = (int) a.getAnimatedValue();
            view.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.requestLayout();
            }
        });
        animator.start();
    }

    private static void animateCollapse(View view) {
        int initialHeight = view.getMeasuredHeight();
        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
        animator.setDuration(200);
        animator.addUpdateListener(a -> {
            view.getLayoutParams().height = (int) a.getAnimatedValue();
            view.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                view.setVisibility(View.GONE);
                view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        });
        animator.start();
    }

    private void createWineConfigurationTab(View view) {
        Context context = getContext();

        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(isEditMode() && container != null ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME);
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        applyThemedAdapter(sDesktopTheme, R.array.desktop_theme_entries);
        sDesktopTheme.setSelection(desktopTheme.theme.ordinal());
        final ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        final ColorPickerView cpvDesktopBackgroundColor = view.findViewById(R.id.CPVDesktopBackgroundColor);
        cpvDesktopBackgroundColor.setColor(desktopTheme.backgroundColor);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        applyThemedAdapter(sDesktopBackgroundType, R.array.desktop_background_type_entries);
        sDesktopBackgroundType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[position];
                ipvDesktopBackgroundImage.setVisibility(View.GONE);
                cpvDesktopBackgroundColor.setVisibility(View.GONE);

                if (type == WineThemeManager.BackgroundType.IMAGE) {
                    ipvDesktopBackgroundImage.setVisibility(View.VISIBLE);
                }
                else if (type == WineThemeManager.BackgroundType.COLOR) {
                    cpvDesktopBackgroundColor.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sDesktopBackgroundType.setSelection(desktopTheme.backgroundType.ordinal());

        List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable), context.getString(R.string.force));
        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        sMouseWarpOverride.setAdapter(createThemedAdapter(context, mouseWarpOverrideList));
        applyPopupBackground(sMouseWarpOverride);

        File containerDir = isEditMode() && container != null ? container.getRootDir() : null;
        if (containerDir != null) {
            File userRegFile = new File(containerDir, ".wine/user.reg");
            if (userRegFile.exists()) {
                try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                    AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable"));
                }
            }
        }
    }

    private void loadGPUNameSpinner(Spinner spinner, int selectedDeviceID) {
        List<String> values = new ArrayList<>();
        int selectedPosition = 0;

        try {
            for (int i = 0; i < gpuCards.length(); i++) {
                JSONObject item = gpuCards.getJSONObject(i);
                if (item.getInt("deviceID") == selectedDeviceID) selectedPosition = i;
                values.add(item.getString("name"));
            }
        }
        catch (JSONException e) {}

        spinner.setAdapter(createThemedAdapter(getContext(), values));
        spinner.setSelection(selectedPosition);
        applyPopupBackground(spinner);
    }

    public static String getScreenSize(View view) {
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        if (sScreenSize.getSelectedItem() == null) return Container.DEFAULT_SCREEN_SIZE;
        String value = sScreenSize.getSelectedItem().toString();
        if (value.equalsIgnoreCase("custom")) {
            value = Container.DEFAULT_SCREEN_SIZE;
            String strWidth = ((EditText)view.findViewById(R.id.ETScreenWidth)).getText().toString().trim();
            String strHeight = ((EditText)view.findViewById(R.id.ETScreenHeight)).getText().toString().trim();
            if (strWidth.matches("[0-9]+") && strHeight.matches("[0-9]+")) {
                int width = Integer.parseInt(strWidth);
                int height = Integer.parseInt(strHeight);
                if ((width % 2) == 0 && (height % 2) == 0) return width+"x"+height;
            }
        }
        return StringUtils.parseIdentifier(value);
    }

    private String getDesktopTheme(View view) {
        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        int typePos = sDesktopBackgroundType.getSelectedItemPosition();
        if (typePos < 0) typePos = 0;
        WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[typePos];
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        ColorPickerView cpvDesktopBackground = view.findViewById(R.id.CPVDesktopBackgroundColor);
        int themePos = sDesktopTheme.getSelectedItemPosition();
        if (themePos < 0) themePos = 0;
        WineThemeManager.Theme theme = WineThemeManager.Theme.values()[themePos];

        String desktopTheme = theme+","+type+","+cpvDesktopBackground.getColorAsString();
        if (type == WineThemeManager.BackgroundType.IMAGE) {
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(getContext());
            desktopTheme += ","+(userWallpaperFile.isFile() ? userWallpaperFile.lastModified() : "0");
        }
        return desktopTheme;
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        applyThemedAdapter(sScreenSize, R.array.screen_size_entries);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            AppUtils.setSpinnerSelectionFromValue(sScreenSize, "custom");
            String[] screenSize = selectedValue.split("x");
            ((EditText) view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText) view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    // New method: Adds support for the GraphicsDriverConfigDialog
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig, String selectedGraphicsDriver, String selectedDXWrapper) {
        final Context context = sGraphicsDriver.getContext();

        // Update the spinner with the available graphics driver options
        updateGraphicsDriverSpinner(context, sGraphicsDriver);

        Runnable update = () -> {
            String graphicsDriver = sGraphicsDriver.getSelectedItem() != null ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()) : Container.DEFAULT_GRAPHICS_DRIVER;

            // Update the DXWrapper spinner
            ArrayList<String> items = new ArrayList<>();
            for (String value : context.getResources().getStringArray(R.array.dxwrapper_entries)) {
                items.add(value);
            }
            sDXWrapper.setAdapter(createThemedAdapter(context, items));
            applyPopupBackground(sDXWrapper);
            AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, selectedDXWrapper);

            vGraphicsDriverConfig.setOnClickListener((v) -> {
                GraphicsDriverConfigDialog.showSafe(vGraphicsDriverConfig, graphicsDriver, null);
            });
            vGraphicsDriverConfig.setVisibility(View.VISIBLE);
        };

        sGraphicsDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set the spinner's initial selection
        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
    }

    public static void setupDXWrapperSpinner(final Spinner sDXWrapper, final View vDXWrapperConfig, boolean isARM64EC) {
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String dxwrapper = sDXWrapper.getSelectedItem() != null ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()) : "";
                if (dxwrapper.contains("dxvk")) {
                    vDXWrapperConfig.setOnClickListener((v) -> {
                        try {
                            (new DXVKConfigDialog(vDXWrapperConfig, isARM64EC)).show();
                        } catch (Throwable e) {
                            Log.e(TAG, "Error opening DXVKConfigDialog", e);
                        }
                    });
                } else {
                    vDXWrapperConfig.setOnClickListener((v) -> {
                        try {
                            (new WineD3DConfigDialog(vDXWrapperConfig)).show();
                        } catch (Throwable e) {
                            Log.e(TAG, "Error opening WineD3DConfigDialog", e);
                        }
                    });
                }
                vDXWrapperConfig.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        sDXWrapper.setOnItemSelectedListener(listener);

        int selectedPosition = sDXWrapper.getSelectedItemPosition();
        if (selectedPosition >= 0) {
            listener.onItemSelected(
                    sDXWrapper,
                    sDXWrapper.getSelectedView(),
                    selectedPosition,
                    sDXWrapper.getSelectedItemId()
            );
        }
    }

    public static String getWinComponents(View view) {
        ViewGroup parent = view.findViewById(R.id.LLTabWinComponents);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass(parent, Spinner.class, views);
        String[] wincomponents = new String[views.size()];

        for (int i = 0; i < views.size(); i++) {
            Spinner spinner = (Spinner)views.get(i);
            wincomponents[i] = spinner.getTag()+"="+spinner.getSelectedItemPosition();
        }
        return String.join(",", wincomponents);
    }

    public void createWinComponentsTab(View view, String wincomponents) {
        Context context = view.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            ViewGroup parent = wincomponent[0].startsWith("direct") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView)itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, wincomponent[0]));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            applyThemedAdapter(spinner, R.array.wincomponent_entries);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(wincomponent[0]);

            parent.addView(itemView);

        }
    }

    public static void createWinComponentsTabFromShortcut(ShortcutSettingsDialog dialog, View view, String wincomponents, boolean isDarkMode) {
        Context context = dialog.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            ViewGroup parent = wincomponent[0].startsWith("direct") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView) itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, wincomponent[0]));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            applyThemedAdapter(spinner, R.array.wincomponent_entries);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(wincomponent[0]);

            parent.addView(itemView);
        }

        // Notify that the views are ready
        dialog.onWinComponentsViewsAdded(isDarkMode);
    }

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);

        String envVarsValue;
        if (isShortcutMode() && shortcut != null) {
            envVarsValue = shortcut.getExtra(
                    "envVars",
                    container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS
            );
        } else if (isEditMode() && container != null) {
            envVarsValue = container.getEnvVars();
        } else {
            envVarsValue = Container.DEFAULT_ENV_VARS;
        }

        envVarsView.setEnvVars(new EnvVars(envVarsValue));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        return envVarsView;
    }

    private String getDrives(View view) {
        LinearLayout parent = view.findViewById(R.id.LLDrives);
        String drives = "";

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Spinner spinner = child.findViewById(R.id.Spinner);
            EditText editText = child.findViewById(R.id.EditText);
            String path = editText.getText().toString().trim();
            if (!path.isEmpty()) drives += spinner.getSelectedItem()+path;
        }
        return drives;
    }

    private void createDrivesTab(View view) {
        final Context context = getContext();

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        LayoutInflater inflater = LayoutInflater.from(context);
        final String drives = isEditMode() && container != null ? container.getDrives() : Container.DEFAULT_DRIVES;
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<String[]> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(createThemedAdapter(context, driveLetters));
            applyPopupBackground(spinner);
            AppUtils.setSpinnerSelectionFromValue(spinner, drive[0]+":");
            spinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

            final EditText editText = itemView.findViewById(R.id.EditText);
            editText.setText(drive[1]);

            itemView.findViewById(R.id.BTSearch).setOnClickListener((v) -> {
                openDirectoryCallback = (path) -> {
                    drive[1] = path;
                    editText.setText(path);
                };
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStorageDirectory()));
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_DIRECTORY_REQUEST_CODE);
            });

            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                parent.removeView(itemView);
                if (parent.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
            parent.addView(itemView);

            // Hide empty text view if there are items
            emptyTextView.setVisibility(View.GONE);
        };
        for (String[] drive : Container.drivesIterator(drives)) addItem.call(drive);

        view.findViewById(R.id.BTAddDrive).setOnClickListener((v) -> {
            if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
            final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
            addItem.call(new String[]{nextDriveLetter, ""});
        });

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    // Helper method to apply dark theme to EditText
    private void applyDarkThemeToEditText(EditText editText) {
        if (isDarkMode) {
            editText.setTextColor(Color.WHITE);
            editText.setHintTextColor(Color.GRAY);
        } else {
            editText.setTextColor(Color.BLACK);
            editText.setHintTextColor(Color.GRAY);
            editText.setBackgroundResource(R.drawable.edit_text);
        }
    }

    // Helper method to apply dark theme to buttons or other clickable views
    private void applyDarkThemeToButton(View button) {

    }

    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, Spinner sBox64Version) {
        final Context context = getContext();
        // Allow changing container in shortcut/per-game settings mode; 
        // only lock it when editing a container directly 
        sWineVersion.setEnabled(!isEditMode() || isShortcutMode());
//
        sWineVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                View fexcoreFL = view.findViewById(R.id.fexcoreFrame);
                Spinner sEmulator = view.findViewById(R.id.SEmulator);
                Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
                Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
                View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
                
                String selectedWineStr = sWineVersion.getSelectedItem() != null ? sWineVersion.getSelectedItem().toString() : WineInfo.MAIN_WINE_VERSION.identifier();
                
                // In shortcut/per-game mode, the spinner shows "Container: Name" instead of wine version IDs.
                // We need to resolve the actual container's wine version to correctly detect ARM64EC.
                if (selectedWineStr.startsWith("Container: ")) {
                    String containerName = selectedWineStr.substring("Container: ".length());
                    for (Container c : manager.getContainers()) {
                        if (c.getName().equals(containerName)) {
                            selectedWineStr = c.getWineVersion();
                            Log.d(TAG, "Resolved container '" + containerName + "' to wine version: " + selectedWineStr);
                            break;
                        }
                    }
                }
                
                WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, selectedWineStr);
                
                sEmulator.setEnabled(false);
                sEmulator64.setEnabled(false);
                
                if (wineInfo.isArm64EC()) {
                    fexcoreFL.setVisibility(View.VISIBLE);
                    // Arm64EC containers MUST use FEXCore
                    sEmulator.setSelection(0); // FEXCore
                    sEmulator64.setSelection(0); // FEXCore
                    Log.d(TAG, "Arm64EC wine selected: forcing FEXCore for both emulators");
                }
                else {
                    fexcoreFL.setVisibility(View.GONE);
                    // x86_64 containers MUST use Box64
                    sEmulator.setSelection(1); // Box64
                    sEmulator64.setSelection(1); // Box64
                    Log.d(TAG, "x86_64 wine selected: forcing Box64 for both emulators");
                }
                
                // Trigger the emulator frames update
                updateEmulatorFrames(view, sEmulator, sEmulator64);
                loadBox64VersionSpinner(context, container, contentsManager, sBox64Version, wineInfo.isArm64EC());
                // Re-apply shortcut's box64Version override if in shortcut mode
                if (isShortcutMode() && shortcut != null) {
                    String shortcutBox64 = shortcut.getExtra("box64Version", "");
                    if (!shortcutBox64.isEmpty()) {
                        AppUtils.setSpinnerSelectionFromValue(sBox64Version, shortcutBox64);
                    }
                }
                setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, wineInfo.isArm64EC());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        ArrayList<String> wineVersions = new ArrayList<>();
        
        if (isShortcutMode() || createShortcutForAppId > 0) {
            // When editing/creating game settings, ONLY list containers
            for (Container c : manager.getContainers()) {
                wineVersions.add("Container: " + c.getName());
            }
        } else {
            String[] versions = getResources().getStringArray(R.array.wine_entries);
            wineVersions.addAll(Arrays.asList(versions));
            for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE))
                wineVersions.add(ContentsManager.getEntryName(profile));
            for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON))                                                      
                wineVersions.add(ContentsManager.getEntryName(profile));
        }

        if (wineVersions.isEmpty()) {
            sWineVersion.setVisibility(View.GONE);
            TextView tvNoWine = new TextView(context);
            tvNoWine.setText(R.string.download_in_components);
            tvNoWine.setTextColor(getResources().getColor(R.color.settings_text_secondary));
            tvNoWine.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter));
            tvNoWine.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            int pad = (int)(8 * context.getResources().getDisplayMetrics().density);
            tvNoWine.setPadding(0, pad, 0, 0);
            ((android.view.ViewGroup) sWineVersion.getParent()).addView(tvNoWine);
            return;
        }
        sWineVersion.setAdapter(createThemedAdapter(context, wineVersions));
        applyPopupBackground(sWineVersion);

        if (isShortcutMode()) {
            String containerIdStr = shortcut.getExtra("container_id");
            if (!containerIdStr.isEmpty()) {
                try {
                    Container currentSC = manager.getContainerById(Integer.parseInt(containerIdStr));
                    if (currentSC != null) {
                        AppUtils.setSpinnerSelectionFromValue(sWineVersion, "Container: " + currentSC.getName());
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing error
                }
            }
        } else if (isEditMode() && container != null) {
            AppUtils.setSpinnerSelectionFromValue(sWineVersion, container.getWineVersion());
        }
    }

    public String getControllerMapping(View view) {
        //The order has to be the same like Container.XrControllerMapping
        int[] ids = {
                R.id.SButtonA, R.id.SButtonB, R.id.SButtonX, R.id.SButtonY, R.id.SButtonGrip, R.id.SButtonTrigger,
                R.id.SThumbstickUp, R.id.SThumbstickDown, R.id.SThumbstickLeft, R.id.SThumbstickRight
        };
        byte[] controllerMapping = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) {
            int index =  ((Spinner)view.findViewById(ids[i])).getSelectedItemPosition();
            byte value = (index >= 0) ? XKeycode.values()[index].id : 0;
            controllerMapping[i] = value;
        }
        return new String(controllerMapping);
    }

    public void setControllerMapping(Spinner spinner, Container.XrControllerMapping mapping, int defaultValue) {
        XKeycode[] values = XKeycode.values();
        ArrayList<String> array = new ArrayList<>();
        for (XKeycode value : values) {
            array.add(value.name());
        }
        spinner.setAdapter(createThemedAdapter(spinner.getContext(), array));
        applyPopupBackground(spinner);

        byte keycode = isEditMode() && container != null ? container.getControllerMapping(mapping) : (byte) defaultValue;
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].id == keycode) {
                index = i;
                break;
            }
        }
        spinner.setSelection(isEditMode() && (index != 0) ? index : defaultValue);
    }

    private void applyDarkMode(View view) {
        // This is a simplified version of applyDarkMode.
        // It should recursively visit all views and apply dark mode colors/backgrounds.
        int sectionLabelColor = getResources().getColor(R.color.settings_text_secondary);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass((ViewGroup) view, TextView.class, views);
        for (View v : views) {
            TextView tv = (TextView) v;
            // Preserve section label color (OtherSettingsSectionLabel style)
            if (tv.getCurrentTextColor() == sectionLabelColor) continue;
            tv.setTextColor(Color.WHITE);
        }

        views.clear();
        AppUtils.findViewsWithClass((ViewGroup) view, EditText.class, views);
        for (View v : views) {
            applyDarkThemeToEditText((EditText) v);
        }

        views.clear();
        AppUtils.findViewsWithClass((ViewGroup) view, CompoundButton.class, views);
        for (View v : views) {
            ((CompoundButton)v).setTextColor(Color.WHITE);
        }
    }

    private void applyFieldSeparatorStyle(View view, boolean isDarkMode) {
        // Implement if needed, or leave as stub
    }

    public static void updateGraphicsDriverSpinner(Context context, Spinner spinner) {
        String[] originalItems = context.getResources().getStringArray(R.array.graphics_driver_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));
        
        // Set the adapter with the combined list
        spinner.setAdapter(createThemedAdapter(context, itemList));
        applyPopupBackground(spinner);
    }

    public static void loadBox64VersionSpinner(Context context, Container container, ContentsManager manager, Spinner spinner, boolean isArm64EC) {
        List<String> itemList;
        if (isArm64EC) {
            String[] originalItems = context.getResources().getStringArray(R.array.wowbox64_version_entries);
            itemList = new ArrayList<>(Arrays.asList(originalItems));
        }
        else {
            String[] originalItems = context.getResources().getStringArray(R.array.box64_version_entries);
            itemList = new ArrayList<>(Arrays.asList(originalItems));
        }
        if (!isArm64EC) {
            for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_BOX64)) {
                String entryName = ContentsManager.getEntryName(profile);
                int firstDashIndex = entryName.indexOf('-');
                itemList.add(entryName.substring(firstDashIndex + 1));
            }
        } else {
            for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64)) {
                String entryName = ContentsManager.getEntryName(profile);
                int firstDashIndex = entryName.indexOf('-');
                itemList.add(entryName.substring(firstDashIndex + 1));
            }
        }
        spinner.setAdapter(createThemedAdapter(context, itemList));
        applyPopupBackground(spinner);
        if (container != null)
            AppUtils.setSpinnerSelectionFromValue(spinner, container.getBox64Version());
        else
            AppUtils.setSpinnerSelectionFromValue(spinner, (isArm64EC) ? DefaultVersion.WOWBOX64 : DefaultVersion.BOX64);
    }

    private void updateEmulatorFrames(View view, Spinner sEmulator, Spinner sEmulator64) {
        View box64Frame = view.findViewById(R.id.box64Frame);
        View fexcoreFrame = view.findViewById(R.id.fexcoreFrame);

        String emulator32 = sEmulator.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator.getSelectedItem()) : "";
        String emulator64 = sEmulator64.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator64.getSelectedItem()) : "";

        boolean useBox64 = emulator32.equalsIgnoreCase("box64") || emulator64.equalsIgnoreCase("box64");
        boolean useFexcore = emulator32.equalsIgnoreCase("fexcore") || emulator64.equalsIgnoreCase("fexcore");

        box64Frame.setVisibility(useBox64 ? View.VISIBLE : View.GONE);
        fexcoreFrame.setVisibility(useFexcore ? View.VISIBLE : View.GONE);
    }

    private void updateLaunchExeButton(TextView button, String fullPath) {
        if (button == null) return;
        button.setText(fullPath == null || fullPath.isEmpty() ? getString(R.string.select_exe) : fullPath);
    }

    private void updateSelectIconButton(TextView button, String fullPath) {
        if (button == null) return;
        button.setText(fullPath == null || fullPath.isEmpty() ? getString(R.string.select_icon) : fullPath);
    }

    private void updateRevertIconButton(View button, String selectedPath) {
        if (button == null) return;
        boolean hasCustomIcon = selectedPath != null && !selectedPath.trim().isEmpty();
        button.setEnabled(hasCustomIcon);
        button.setAlpha(hasCustomIcon ? 1f : 0.5f);
    }

    private boolean isSupportedIconFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ENGLISH);
        return lower.endsWith(".png") || lower.endsWith(".ico");
    }

    private String buildShortcutLibraryIconId(String name, @Nullable Shortcut shortcut, String source, int appId, @Nullable String gogId) {
        String baseId = "";
        if (shortcut != null) {
            baseId = shortcut.getExtra("uuid");
            if (baseId.isEmpty()) baseId = shortcut.name;
        }

        if (baseId.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            if (source != null && !source.isEmpty()) builder.append(source);
            if (appId > 0) builder.append("_").append(appId);
            if (gogId != null && !gogId.isEmpty()) builder.append("_").append(gogId);
            if (name != null && !name.isEmpty()) builder.append("_").append(name);
            baseId = builder.toString();
        }

        if (baseId.isEmpty()) baseId = UUID.randomUUID().toString();
        return baseId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String persistShortcutLibraryIcon(String sourcePath, String iconId) {
        if (sourcePath == null || sourcePath.isEmpty()) return "";

        File sourceFile = new File(sourcePath);
        if (!sourceFile.isFile()) return "";

        Bitmap iconBitmap = IconFileUtils.decodeImageOrIco(sourceFile);
        if (iconBitmap == null) return "";

        Context context = getContext();
        if (context == null) return "";

        File iconDir = new File(context.getFilesDir(), "library_icons");
        if (!iconDir.exists()) iconDir.mkdirs();

        File outputFile = new File(iconDir, iconId + ".png");
        return FileUtils.saveBitmapToFile(iconBitmap, outputFile) ? outputFile.getAbsolutePath() : "";
    }

    private int shortcutAppId() {
        if (!isShortcutMode()) return createShortcutForAppId;
        String appId = shortcut.getExtra("app_id");
        if (appId.isEmpty()) return 0;
        try {
            return Integer.parseInt(appId);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String shortcutGogId() {
        if (!isShortcutMode()) return createShortcutForGogId;
        return shortcut.getExtra("gog_id");
    }

    private String resolveInitialLaunchExePath() {
        if (!isShortcutMode()) return "";

        String storedPath = shortcut.getExtra("launch_exe_path");
        if (!storedPath.isEmpty()) return storedPath;

        String gameSource = shortcut.getExtra("game_source", createShortcutForSource);
        if ("CUSTOM".equals(gameSource)) {
            String customExe = shortcut.getExtra("custom_exe");
            if (!customExe.isEmpty()) return customExe;
        }

        return resolveAbsolutePathFromShortcut(gameSource, shortcutAppId(), shortcutGogId(), shortcut, shortcut.path);
    }

    private String resolveAbsolutePathFromShortcut(String gameSource, int appId, @Nullable String gogId, @Nullable Shortcut shortcut, @Nullable String shortcutPath) {
        if (shortcutPath == null || shortcutPath.isEmpty()) return "";

        if ("C:\\Program Files (x86)\\Steam\\steamclient_loader_x64.exe".equalsIgnoreCase(shortcutPath)) {
            return "";
        }

        if (shortcutPath.regionMatches(true, 0, "A:\\", 0, 3)) {
            String baseDir = resolveBaseGameDirectory(gameSource, appId, gogId, shortcut, "");
            if (baseDir.isEmpty()) return "";
            String relativePath = shortcutPath.substring(3).replace("\\", File.separator);
            return new File(baseDir, relativePath).getAbsolutePath();
        }

        if (shortcutPath.regionMatches(true, 0, "Z:\\", 0, 3)) {
            String hostPath = shortcutPath.substring(2).replace("\\", File.separator);
            if (!hostPath.startsWith(File.separator)) {
                hostPath = File.separator + hostPath;
            }
            return hostPath;
        }

        return "";
    }

    private String resolveBaseGameDirectory(String gameSource, int appId, @Nullable String gogId, @Nullable Shortcut shortcut, @Nullable String fallbackExePath) {
        if ("STEAM".equals(gameSource) && appId > 0) {
            String installPath = SteamBridge.getAppDirPath(appId);
            if (installPath != null && !installPath.isEmpty()) return installPath;
        } else if ("EPIC".equals(gameSource) && appId > 0) {
            if (shortcut != null) {
                String installPath = shortcut.getExtra("game_install_path");
                if (!installPath.isEmpty()) return installPath;
            }

            com.winlator.cmod.epic.data.EpicGame epicGame = com.winlator.cmod.epic.service.EpicService.Companion.getEpicGameOf(appId);
            if (epicGame != null) {
                String installPath = epicGame.getInstallPath();
                if ((installPath == null || installPath.isEmpty()) && getContext() != null) {
                    installPath = com.winlator.cmod.epic.service.EpicConstants.INSTANCE.getGameInstallPath(getContext(), epicGame.getAppName());
                }
                if (installPath != null && !installPath.isEmpty()) return installPath;
            }
        } else if ("GOG".equals(gameSource)) {
            if (shortcut != null) {
                String installPath = shortcut.getExtra("game_install_path");
                if (!installPath.isEmpty()) return installPath;
            }

            String effectiveGogId = shortcut != null ? shortcut.getExtra("gog_id") : gogId;
            if (effectiveGogId != null && !effectiveGogId.isEmpty()) {
                com.winlator.cmod.gog.data.GOGGame gogGame = com.winlator.cmod.gog.service.GOGService.Companion.getGOGGameOf(effectiveGogId);
                if (gogGame != null) {
                    String installPath = gogGame.getInstallPath();
                    if ((installPath == null || installPath.isEmpty()) && gogGame.getTitle() != null && !gogGame.getTitle().isEmpty()) {
                        installPath = com.winlator.cmod.gog.service.GOGConstants.INSTANCE.getGameInstallPath(gogGame.getTitle());
                    }
                    if (installPath != null && !installPath.isEmpty()) return installPath;
                }
            }

            if (createShortcutForAppName != null && !createShortcutForAppName.isEmpty()) {
                String installPath = com.winlator.cmod.gog.service.GOGConstants.INSTANCE.getGameInstallPath(createShortcutForAppName);
                if (installPath != null && !installPath.isEmpty()) return installPath;
            }
        } else if ("CUSTOM".equals(gameSource)) {
            if (shortcut != null) {
                String gameFolder = shortcut.getExtra("custom_game_folder");
                if (!gameFolder.isEmpty()) return gameFolder;
            }
        }

        if (fallbackExePath != null && !fallbackExePath.isEmpty()) {
            File parentFile = new File(fallbackExePath).getParentFile();
            if (parentFile != null) return parentFile.getAbsolutePath();
        }

        return "";
    }

    private String buildExecCommandForSource(String gameSource, int appId, @Nullable String gogId, @Nullable Shortcut shortcut, @Nullable String selectedExePath) {
        if (selectedExePath == null || selectedExePath.isEmpty()) {
            if ("EPIC".equals(gameSource) || "GOG".equals(gameSource)) {
                return "wine \"A:\\\\\"";
            }
            return "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\"";
        }

        File exeFile = new File(selectedExePath);
        String absoluteExePath = exeFile.getAbsolutePath();
        String baseDir = resolveBaseGameDirectory(gameSource, appId, gogId, shortcut, absoluteExePath);
        String normalizedBaseDir = baseDir.isEmpty() ? "" : StringUtils.removeEndSlash(new File(baseDir).getAbsolutePath());
        String normalizedExePath = absoluteExePath;
        String winPath;

        if (!normalizedBaseDir.isEmpty() && (normalizedExePath.equals(normalizedBaseDir) || normalizedExePath.startsWith(normalizedBaseDir + File.separator))) {
            String relativePath = FileUtils.toRelativePath(normalizedBaseDir, normalizedExePath).replace("/", "\\");
            while (relativePath.startsWith("\\")) relativePath = relativePath.substring(1);
            winPath = "A:\\" + relativePath;
        } else {
            String hostPath = normalizedExePath.replace("/", "\\");
            if (!hostPath.startsWith("\\")) hostPath = "\\" + hostPath;
            winPath = "Z:" + hostPath;
        }

        return "wine \"" + winPath + "\"";
    }

    private void rewriteShortcutExecLine(File shortcutFile, String execCommand) {
        List<String> lines = FileUtils.readLines(shortcutFile);
        StringBuilder content = new StringBuilder();
        boolean execReplaced = false;
        boolean insertedBeforeExtraData = false;

        for (String line : lines) {
            if (line.startsWith("Exec=")) {
                content.append("Exec=").append(execCommand).append("\n");
                execReplaced = true;
                continue;
            }

            if (!execReplaced && line.startsWith("[Extra Data]")) {
                content.append("Exec=").append(execCommand).append("\n");
                execReplaced = true;
                insertedBeforeExtraData = true;
            }

            content.append(line).append("\n");
        }

        if (!execReplaced) {
            if (!insertedBeforeExtraData && content.length() > 0 && content.charAt(content.length() - 1) != '\n') {
                content.append("\n");
            }
            content.append("Exec=").append(execCommand).append("\n");
        }

        FileUtils.writeString(shortcutFile, content.toString());
    }

    private void createShortcutOnContainer(Container container, JSONObject data) throws Exception {
        File desktopDir = container.getDesktopDir();
        if (!desktopDir.exists()) desktopDir.mkdirs();

        File shortcutFile = new File(desktopDir, createShortcutForAppName + ".desktop");
        StringBuilder content = new StringBuilder();
        content.append("[Desktop Entry]\n");
        content.append("Type=Application\n");
        content.append("Name=").append(createShortcutForAppName).append("\n");
        String execCommand = buildExecCommandForSource(createShortcutForSource, createShortcutForAppId, createShortcutForGogId, null, data.optString("launchExePath", ""));
        content.append("Exec=").append(execCommand).append("\n");
        if (createShortcutForSource.equals("EPIC")) {
            content.append("Icon=epic_icon_").append(createShortcutForAppId).append("\n");
        } else if (createShortcutForSource.equals("GOG")) {
            content.append("Icon=gog_icon_").append(createShortcutForGogId).append("\n");
        } else {
            content.append("Icon=steam_icon_").append(createShortcutForAppId).append("\n");
        }
        content.append("\n[Extra Data]\n");
        content.append("game_source=").append(createShortcutForSource).append("\n");
        content.append("app_id=").append(createShortcutForAppId).append("\n");
        if (createShortcutForSource.equals("GOG")) {
            content.append("gog_id=").append(createShortcutForGogId).append("\n");
        }
        content.append("container_id=").append(container.id).append("\n");

        // Write all additional overrides from data as extra data
        java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.equals("name") && !key.equals("wineVersion")) {
                content.append(key).append("=").append(data.getString(key)).append("\n");
            }
        }

        FileUtils.writeString(shortcutFile, content.toString());
    }
}


