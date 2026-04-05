package com.winlator.cmod;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ScrollView;
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
import com.winlator.cmod.contentdialog.DXVKConfigDialog;

import com.winlator.cmod.contentdialog.AddEnvVarDialog;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
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
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.widget.ColorPickerView;
import com.winlator.cmod.widget.EnvVarsView;
import com.winlator.cmod.widget.ImagePickerView;
import com.winlator.cmod.widget.ChasingBorderDrawable;
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

public class ContainerDetailFragment extends Fragment {

    private static final String TAG = "FileUtils";

    private ContainerManager manager;
    private ContentsManager contentsManager;
    private final int containerId;
    private Container container;
    private PreloaderDialog preloaderDialog;
    private JSONArray gpuCards;
    private Callback<String> openDirectoryCallback;
    private int hostSidebarPreviousVisibility = View.VISIBLE;
    private boolean hostSidebarVisibilityCaptured = false;

    private ImageFs imageFs;

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
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

    private void applyDynamicStyles(View view) {


        // Update Spinners
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        if (sScreenSize != null) sScreenSize.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        if (sWineVersion != null) sWineVersion.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        if (sGraphicsDriver != null) sGraphicsDriver.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        if (sDXWrapper != null) sDXWrapper.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        if (sAudioDriver != null) sAudioDriver.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        if (sEmulator64 != null) sEmulator64.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        if (sEmulator != null) sEmulator.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        if (sMIDISoundFont != null) sMIDISoundFont.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        // Update Wine Configuration Tab Spinner styles
        // Desktop
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        if (sDesktopTheme != null) sDesktopTheme.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        if (sDesktopBackgroundType != null) sDesktopBackgroundType.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        if (sMouseWarpOverride != null) sMouseWarpOverride.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        // Win Components
        // Handled in createWinComponentsTab

        // Update Advanced Tab Spinner styles
        Spinner SDInputType = view.findViewById(R.id.SDInputType);
        if (SDInputType != null) SDInputType.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        if (sBox64Preset != null) sBox64Preset.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        if (sBox64Version != null) sBox64Version.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        if (sFEXCoreVersion != null) sFEXCoreVersion.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        if (sFEXCorePreset != null) sFEXCorePreset.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

        Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        if (sStartupSelection != null) sStartupSelection.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
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
        hideHostSidebar();
        Activity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(isEditMode() ? R.string.containers_list_edit : R.string.containers_list_new);
            }
        }

    }

    @Override
    public void onDestroyView() {
        restoreHostSidebar();
        super.onDestroyView();
    }

    public boolean isEditMode() {
        return container != null;
    }

    private void hideHostSidebar() {
        Activity activity = getActivity();
        if (activity == null) return;

        View hostSidebar = activity.findViewById(R.id.LLSidebar);
        if (hostSidebar == null) return;

        if (!hostSidebarVisibilityCaptured) {
            hostSidebarPreviousVisibility = hostSidebar.getVisibility();
            hostSidebarVisibilityCaptured = true;
        }
        hostSidebar.setVisibility(View.GONE);
    }

    private void restoreHostSidebar() {
        Activity activity = getActivity();
        if (activity == null || !hostSidebarVisibilityCaptured) return;

        View hostSidebar = activity.findViewById(R.id.LLSidebar);
        if (hostSidebar != null) {
            hostSidebar.setVisibility(hostSidebarPreviousVisibility);
        }
        hostSidebarVisibilityCaptured = false;
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

        // Apply dynamic styles
        applyDynamicStyles(view);

        // Apply dynamic styles recursively
//        applyDynamicStylesRecursively(view);

        Log.d(TAG, "onCreateView: step 1 - creating ContainerManager");
        manager = new ContainerManager(context);

        container = containerId > 0 ? manager.getContainerById(containerId) : null;

        Log.d(TAG, "onCreateView: step 2 - creating ContentsManager");
        contentsManager = new ContentsManager(context);
        contentsManager.syncContents();
        Log.d(TAG, "onCreateView: step 3 - contentsManager synced");

        final EditText etName = view.findViewById(R.id.ETName);
        final View llLaunchExe = view.findViewById(R.id.LLLaunchExe);

        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);

        // Ensure the Wine version layout is visible
        final LinearLayout llWineVersion = view.findViewById(R.id.LLWineVersion);
        llWineVersion.setVisibility(View.VISIBLE);

        if (container != null) {
            etName.setText(container.getName());
        } else {
            etName.setText(getString(R.string.common_ui_container) + "-" + manager.getNextContainerId());
        }

        llLaunchExe.setVisibility(View.GONE);

        final Spinner sBox64Version = view.findViewById(R.id.SBox64Version);

        applyDarkMode(view);

        Log.d(TAG, "onCreateView: step 4 - loading wine version spinner");
        loadWineVersionSpinner(view, sWineVersion, sBox64Version);
        Log.d(TAG, "onCreateView: step 5 - wine version spinner loaded");

        loadScreenSizeSpinner(view, container != null ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE);

        final Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);

        final Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);

        final View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(container != null ? container.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG);
        Log.d(TAG, "Initial DXVK config mode=" + (container != null ? "container-edit" : "container-create") +
                " value='" + vDXWrapperConfig.getTag() + "'");

        final View vGraphicsDriverConfig = view.findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(container != null ? container.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG);

        Log.d(TAG, "onCreateView: step 6 - loading graphics driver spinner");
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
                container != null ? container.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER,
                container != null ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER);
        String initialWineVersion = container != null ? container.getWineVersion() : WineInfo.MAIN_WINE_VERSION.identifier();
        setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, WineInfo.fromIdentifier(context, contentsManager, initialWineVersion).isArm64EC());
        Log.d(TAG, "onCreateView: step 7 - graphics driver spinner loaded");

        view.findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.container_wine_dxwrapper_help_content));

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        applyThemedAdapter(sAudioDriver, R.array.audio_driver_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, container != null ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER);

        final Spinner sEmulator = view.findViewById(R.id.SEmulator);
        applyThemedAdapter(sEmulator, R.array.emulator_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, container != null ? container.getEmulator() : Container.DEFAULT_EMULATOR);

        final Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        applyThemedAdapter(sEmulator64, R.array.emulator_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, container != null ? container.getEmulator64() : Container.DEFAULT_EMULATOR64);

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
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, container != null ? container.getMIDISoundFont() : "");

        final CompoundButton cbShowFPS = view.findViewById(R.id.CBShowFPS);
        cbShowFPS.setChecked(container != null && container.isShowFPS());

        final CompoundButton cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        cbFullscreenStretched.setChecked(container != null && container.isFullscreenStretched());

        final View llSteamSettings = view.findViewById(R.id.LLSteamSettings);
        llSteamSettings.setVisibility(View.GONE);
        final CompoundButton cbUseColdClient = view.findViewById(R.id.CBUseColdClient);
        final CompoundButton cbLaunchRealSteam = view.findViewById(R.id.CBLaunchRealSteam);
        final CompoundButton cbUseSteamInput = view.findViewById(R.id.CBUseSteamInput);
        cbUseColdClient.setChecked(container != null && container.isUseColdClient());
        cbLaunchRealSteam.setChecked(container != null && container.isLaunchRealSteam());
        cbUseSteamInput.setChecked(container != null && "1".equals(container.getExtra("useSteamInput", "0")));

        final Spinner sSteamType = view.findViewById(R.id.SSteamType);
        applyThemedAdapter(sSteamType, R.array.steam_type_entries);
        String defaultSteamType = container != null ? container.getSteamType() : Container.STEAM_TYPE_NORMAL;
        int steamTypeIndex = Container.STEAM_TYPE_ULTRALIGHT.equals(defaultSteamType) ? 2
                : Container.STEAM_TYPE_LIGHT.equals(defaultSteamType) ? 1 : 0;
        sSteamType.setSelection(steamTypeIndex);

        final CompoundButton cbForceDlc = view.findViewById(R.id.CBForceDlc);
        cbForceDlc.setChecked(container != null && container.isForceDlc());

        final CompoundButton cbSteamOfflineMode = view.findViewById(R.id.CBSteamOfflineMode);
        cbSteamOfflineMode.setChecked(container != null && container.isSteamOfflineMode());

        final CompoundButton cbUnpackFiles = view.findViewById(R.id.CBUnpackFiles);
        cbUnpackFiles.setChecked(container != null && container.isUnpackFiles());

        // Mutual exclusion: ColdClient and Legacy DRM cannot both be enabled
        cbUseColdClient.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && cbUnpackFiles.isChecked()) {
                cbUnpackFiles.setChecked(false);
            }
        });
        cbUnpackFiles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && cbUseColdClient.isChecked()) {
                cbUseColdClient.setChecked(false);
            }
        });

        // Existing declarations of UI components and variables
        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.container_config_xinput_dinput_warning, null);
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
        int inputType = container != null ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE;

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
            boolean exclusiveInputEnabled = preferences.getBoolean("xinput_toggle", false);
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

        btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.container_config_help_xinput));
        btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.container_config_help_dinput));

        final CompoundButton cbSdl2Toggle = view.findViewById(R.id.CBSdl2Toggle);
        String envVarsValue = container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS;
        cbSdl2Toggle.setChecked(envVarsValue.contains("SDL_XINPUT_ENABLED=1"));

        final EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        Locale systemLocal = Locale.getDefault();
        etLC_ALL.setText(container != null
                ? container.getLC_ALL()
                : systemLocal.getLanguage() + '_' + systemLocal.getCountry() + ".UTF-8");

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
        byte previousStartupSelection = container != null ? container.getStartupSelection() : -1;
        sStartupSelection.setSelection(previousStartupSelection != -1 ? previousStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Box64PresetManager.loadSpinner("box64", sBox64Preset, container != null
                ? container.getBox64Preset()
                : preferences.getString("box64_preset", Box64Preset.COMPATIBILITY));

        final Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion,
                container != null ? container.getFEXCoreVersion() : DefaultVersion.FEXCORE);

        final Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        FEXCorePresetManager.loadSpinner(sFEXCorePreset, container != null
                ? container.getFEXCorePreset()
                : preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE));

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(container != null ? container.getCPUList(true) : Container.getFallbackCPUList());
        cpuListViewWoW64.setCheckedCPUList(container != null ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64());

        // Exec arguments
        final EditText etExecArgs = view.findViewById(R.id.ETExecArgs);
        etExecArgs.setText(container != null ? container.getExecArgs() : "");
        etExecArgs.setOnEditorActionListener((v, actionId, event) -> {
            etExecArgs.clearFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etExecArgs.getWindowToken(), 0);
            return true;
        });

        // Dismiss keyboard and clear focus when tapping outside the EditText
        View scrollView = view.findViewById(R.id.SVContainerDetail);
        if (scrollView != null) {
            scrollView.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    View focused = v.findFocus();
                    if (focused instanceof EditText) {
                        android.graphics.Rect outRect = new android.graphics.Rect();
                        focused.getGlobalVisibleRect(outRect);
                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            focused.clearFocus();
                            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                        }
                    }
                }
                return false;
            });
        }

        final View btExtraArgsMenu = view.findViewById(R.id.BTExtraArgsMenu);
        btExtraArgsMenu.setOnClickListener(v -> {
            // Dismiss keyboard if open
            etExecArgs.clearFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etExecArgs.getWindowToken(), 0);
            Context themedContext = new android.view.ContextThemeWrapper(context, R.style.ThemeOverlay_ContentPopupMenu);
            PopupMenu popupMenu = new PopupMenu(themedContext, v);
            popupMenu.getMenuInflater().inflate(R.menu.extra_args_popup_menu, popupMenu.getMenu());

            // Bold the section header items
            Menu menu = popupMenu.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                android.view.MenuItem mi = menu.getItem(i);
                String title = mi.getTitle().toString();
                if (title.startsWith("──")) {
                    android.text.SpannableString styled = new android.text.SpannableString(title);
                    styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(), 0);
                    styled.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFFFFF), 0, title.length(), 0);
                    mi.setTitle(styled);
                }
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                String value = item.getTitle().toString();
                if (value.startsWith("──")) return false;
                String current = etExecArgs.getText().toString();
                if (!current.contains(value)) {
                    String newText = !current.isEmpty() ? current + " " + value : value;
                    etExecArgs.setText(newText);
                    etExecArgs.setSelection(newText.length());
                }
                item.setChecked(!item.isChecked());
                return false;
            });
            popupMenu.show();
        });

        createWineConfigurationTab(view);
        final EnvVarsView envVarsView = createEnvVarsTab(view);
        createWinComponentsTab(view, container != null ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS);
        createDrivesTab(view);

        setupExpandableSections(view);
        setupSidebarNavigation(view);

        // Auto-expand Win Components in the Windows tab
        View winComponentsContent = view.findViewById(R.id.LLTabWinComponents);
        if (winComponentsContent != null) {
            winComponentsContent.setVisibility(View.VISIBLE);
            winComponentsContent.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        ImageView winComponentsChevron = view.findViewById(R.id.IVChevronWinComponents);
        if (winComponentsChevron != null) {
            winComponentsChevron.setRotation(90f);
        }

        // Set up confirm button with press animation
        View btnReset = view.findViewById(R.id.BTSidebarReset);
        View btnConfirm = view.findViewById(R.id.BTSidebarConfirm);
        if (btnReset != null) {
            btnReset.setVisibility(View.GONE);
        }
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
                Log.d(TAG, "Confirm clicked mode=" + (container != null ? "container-edit" : "container-create") +
                        " dxwrapper='" + dxwrapper + "' dxwrapperConfig='" + dxwrapperConfig + "'");
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
                String steamType = sSteamType.getSelectedItemPosition() == 2 ? Container.STEAM_TYPE_ULTRALIGHT
                        : sSteamType.getSelectedItemPosition() == 1 ? Container.STEAM_TYPE_LIGHT
                        : Container.STEAM_TYPE_NORMAL;
                boolean forceDlc = cbForceDlc.isChecked();
                boolean steamOfflineMode = cbSteamOfflineMode.isChecked();
                boolean unpackFiles = cbUnpackFiles.isChecked();
                String execArgs = etExecArgs.getText().toString();

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

                if (container != null) {
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
                    Log.d(TAG, "Saving container dxwrapperConfig containerId=" + container.id +
                            " value='" + dxwrapperConfig + "'");
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
                    container.setSteamType(steamType);
                    container.setForceDlc(forceDlc);
                    container.setSteamOfflineMode(steamOfflineMode);
                    container.setUnpackFiles(unpackFiles);
                    container.setExecArgs(execArgs);
                    Log.d(TAG, "Persist container.saveData containerId=" + container.id +
                            " finalDxwrapperConfig='" + container.getDXWrapperConfig() + "'");
                    Log.d(TAG, "Saving container settings containerId=" + container.id +
                            " box64Preset='" + box64Preset + "' fexcorePreset='" + fexcorePreset +
                            "' box64Version='" + box64Version + "' fexcoreVersion='" + fexcoreVersion +
                            "' graphicsDriver='" + graphicsDriver + "' dxwrapper='" + dxwrapper +
                            "' audioDriver='" + audioDriver + "' emulator='" + emulator +
                            "' screenSize='" + screenSize + "' startupSelection=" + startupSelection +
                            " envVars='" + envVars + "' cpuList='" + cpuList +
                            "' cpuListWoW64='" + cpuListWoW64 + "'");
                    container.saveData();
                    if (cbExclusiveInput != null) {
                        preferences.edit().putBoolean("xinput_toggle", cbExclusiveInput.isChecked()).apply();
                    }
                    saveWineRegistryKeys(view);
                    getActivity().onBackPressed();
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

                    Log.d(TAG, "Creating new container with settings name='" + name +
                            "' wineVersion='" + selectedWineStr + "' box64Preset='" + box64Preset +
                            "' fexcorePreset='" + fexcorePreset + "' box64Version='" + box64Version +
                            "' fexcoreVersion='" + fexcoreVersion + "' graphicsDriver='" + graphicsDriver +
                            "' dxwrapper='" + dxwrapper + "' dxwrapperConfig='" + dxwrapperConfig +
                            "' audioDriver='" + audioDriver + "' emulator='" + emulator +
                            "' screenSize='" + screenSize + "' startupSelection=" + startupSelection +
                            " envVars='" + envVars + "' cpuList='" + cpuList +
                            "' cpuListWoW64='" + cpuListWoW64 + "'");

                    preloaderDialog.show(R.string.containers_list_creating);

                    // Initialize ImageFs
                    File imageFsRoot = new File(context.getFilesDir(), "imagefs");
                    imageFs = ImageFs.find(imageFsRoot);


                    manager.createContainerAsync(data, contentsManager, (newContainer) -> {
                        if (newContainer != null) {
                            this.container = newContainer;
                            saveWineRegistryKeys(view);
                        } else {
                            AppUtils.showToast(context, R.string.setup_wizard_unable_to_install_system_files);
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

    private void setupSidebarNavigation(View view) {
        int[] sidebarButtonIds = {
            R.id.BTSectionAV, R.id.BTSectionWine,
            R.id.BTSectionWindows, R.id.BTSectionInputs, R.id.BTSectionConfig
        };
        int[] sectionIds = {
            R.id.LLSectionAV, R.id.LLSectionWine,
            R.id.LLSectionWindows, R.id.LLSectionInputs, R.id.LLSectionConfig
        };

        View[] sidebarButtons = new View[sidebarButtonIds.length];
        View[] sectionViews = new View[sectionIds.length];

        for (int i = 0; i < sidebarButtonIds.length; i++) {
            sidebarButtons[i] = view.findViewById(sidebarButtonIds[i]);
            sectionViews[i] = view.findViewById(sectionIds[i]);
        }

        final ScrollView scrollView = view.findViewById(R.id.SVContainerDetail);

        for (int i = 0; i < sidebarButtons.length; i++) {
            final int index = i;
            if (sidebarButtons[i] != null) {
                sidebarButtons[i].setOnClickListener(v -> showSection(index, sidebarButtons, sectionViews, scrollView));
            }
        }

        // Show first section by default
        showSection(0, sidebarButtons, sectionViews, scrollView);
    }

    private void showSection(int selectedIndex, View[] sidebarButtons, View[] sectionViews, ScrollView scrollView) {
        for (int i = 0; i < sectionViews.length; i++) {
            if (sectionViews[i] != null) {
                sectionViews[i].setVisibility(i == selectedIndex ? View.VISIBLE : View.GONE);
            }
        }

        for (int i = 0; i < sidebarButtons.length; i++) {
            View btn = sidebarButtons[i];
            if (btn == null) continue;

            if (i == selectedIndex) {
                // Active: white text/icon + ChasingBorderDrawable
                float density = btn.getResources().getDisplayMetrics().density;
                ChasingBorderDrawable border = new ChasingBorderDrawable(12f, 1.5f, density);
                btn.setBackground(border);
                setButtonColors(btn, Color.WHITE);
            } else {
                // Inactive: gray text/icon, transparent bg
                btn.setBackground(null);
                setButtonColors(btn, Color.parseColor("#B0BEC5"));
            }
        }

        if (scrollView != null) {
            scrollView.post(() -> scrollView.scrollTo(0, 0));
        }
    }

    private void setButtonColors(View button, int color) {
        if (button instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) button;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                } else if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(color);
                }
            }
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
        };

        for (int[] section : sections) {
            View header = view.findViewById(section[0]);
            View content = view.findViewById(section[1]);
            ImageView chevron = view.findViewById(section[2]);

            header.setOnClickListener(v -> {
                // Clear focus from any child to prevent ScrollView from jumping
                View focused = view.findFocus();
                if (focused != null) focused.clearFocus();
                View scrollView = view.findViewById(R.id.SVContainerDetail);
                if (scrollView != null) scrollView.requestFocus();

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

        String desktopThemeValue = container != null ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME;
        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(desktopThemeValue);
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

        List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.common_ui_disable), context.getString(R.string.common_ui_enable), context.getString(R.string.common_ui_force));
        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        sMouseWarpOverride.setAdapter(createThemedAdapter(context, mouseWarpOverrideList));
        applyPopupBackground(sMouseWarpOverride);

        File containerDir = container != null ? container.getRootDir() : null;
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
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig,
                                          String selectedGraphicsDriver, String selectedDXWrapper) {
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

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);

        String envVarsValue = container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS;

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
        final String drives = container != null ? container.getDrives() : Container.DEFAULT_DRIVES;
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<String[]> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(createThemedAdapter(context, driveLetters));
            applyPopupBackground(spinner);
            AppUtils.setSpinnerSelectionFromValue(spinner, drive[0]+":");
            spinner.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

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

    private void applyDarkThemeToEditText(EditText editText) {
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
    }


    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, Spinner sBox64Version) {
        final Context context = getContext();
        // Lock wine version when editing an existing container; free when creating one.
        sWineVersion.setEnabled(container == null);

        sWineVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                View fexcoreFL = view.findViewById(R.id.fexcoreFrame);
                Spinner sEmulator = view.findViewById(R.id.SEmulator);
                Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
                Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
                View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);

                String selectedWineStr = sWineVersion.getSelectedItem() != null ? sWineVersion.getSelectedItem().toString() : WineInfo.MAIN_WINE_VERSION.identifier();

                WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, selectedWineStr);

                sEmulator.setEnabled(false);
                sEmulator64.setEnabled(false);

                if (wineInfo.isArm64EC()) {
                    fexcoreFL.setVisibility(View.VISIBLE);
                    // Arm64EC: 64-bit uses FEXCore, 32-bit uses Wowbox64
                    sEmulator.setSelection(2); // Wowbox64 for 32-bit
                    sEmulator64.setSelection(0); // FEXCore for 64-bit
                    Log.d(TAG, "Arm64EC wine selected: FEXCore for 64-bit, Wowbox64 for 32-bit");
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
                setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, wineInfo.isArm64EC());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        ArrayList<String> wineVersions = new ArrayList<>();

        String[] versions = getResources().getStringArray(R.array.wine_entries);
        wineVersions.addAll(Arrays.asList(versions));
        for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE))
            wineVersions.add(ContentsManager.getEntryName(profile));
        for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON))
            wineVersions.add(ContentsManager.getEntryName(profile));

        if (wineVersions.isEmpty()) {
            sWineVersion.setVisibility(View.GONE);
            TextView tvNoWine = new TextView(context);
            tvNoWine.setText(R.string.settings_content_download_in_components);
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

        if (container != null) {
            AppUtils.setSpinnerSelectionFromValue(sWineVersion, container.getWineVersion());
        }
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

        boolean useBox64 = emulator32.equalsIgnoreCase("box64") || emulator64.equalsIgnoreCase("box64")
                || emulator32.equalsIgnoreCase("wowbox64") || emulator64.equalsIgnoreCase("wowbox64");
        boolean useFexcore = emulator32.equalsIgnoreCase("fexcore") || emulator64.equalsIgnoreCase("fexcore");

        box64Frame.setVisibility(useBox64 ? View.VISIBLE : View.GONE);
        fexcoreFrame.setVisibility(useFexcore ? View.VISIBLE : View.GONE);
    }

}


