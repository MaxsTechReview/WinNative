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
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.UUID;

public class ContainerDetailFragment extends Fragment {

    private static final String TAG = "FileUtils";
    private static final String EXTRA_USE_CONTAINER_DEFAULTS = "use_container_defaults";
    private static final String[] SHORTCUT_SETTING_OVERRIDE_KEYS = {
            "screenSize", "envVars", "cpuList", "cpuListWoW64", "graphicsDriver", "graphicsDriverConfig",
            "dxwrapper", "dxwrapperConfig", "audioDriver", "emulator", "emulator64", "wincomponents",
            "drives", "showFPS", "fullscreenStretched", "inputType", "disableXinput",
            "startupSelection", "box64Version", "box64Preset", "fexcoreVersion", "fexcorePreset",
            "desktopTheme", "midiSoundFont", "lc_all",
            "launchRealSteam", "useLegacyDRM", "useSteamInput",
            "steamType", "forceDlc", "steamOfflineMode", "unpackFiles"
    };

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
    private int hostSidebarPreviousVisibility = View.VISIBLE;
    private boolean hostSidebarVisibilityCaptured = false;
    private final HashMap<TextView, String> labelOriginalText = new HashMap<>();
    private Runnable perGameRefreshIndicators;

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

    private static int parseIntSafely(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        }
        catch (Exception ignored) {
            return fallback;
        }
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

    private void applyFieldSetLabelStyle(TextView textView) {
        if (textView == null) return;
        textView.setTextColor(Color.parseColor("#cccccc"));
        textView.setBackgroundResource(R.color.window_background_color_dark);
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

    private void applyDynamicStylesRecursively(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                applyDynamicStylesRecursively(child);
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if ("desktop".equals(textView.getText().toString())) {
                textView.setTextAppearance(getContext(), R.style.FieldSetLabel_Dark);
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
        hideHostSidebar();
        Activity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);
            }
        }

    }

    @Override
    public void onDestroyView() {
        perGameRefreshIndicators = null;
        restoreHostSidebar();
        super.onDestroyView();
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

    private boolean isPerGameSettingsMode() {
        return isShortcutMode() || isCreateShortcutMode();
    }

    private Container getInitialPerGameContainer() {
        if (manager == null) return container;
        if (isShortcutMode()) return getShortcutSettingsContainer();
        if (!isCreateShortcutMode()) return container;
        if (container != null) return container;
        Context context = getContext();
        return context != null ? SetupWizardActivity.getPreferredGameContainer(context, manager) : null;
    }

    private Container getSelectedPerGameContainer(@Nullable Spinner sWineVersion) {
        if (!isPerGameSettingsMode()) return container;

        Container selectedContainer = resolveSelectedShortcutContainer(sWineVersion);
        if (selectedContainer != null) return selectedContainer;

        if (isShortcutMode()) {
            Container currentShortcutContainer = getShortcutSettingsContainer();
            if (currentShortcutContainer != null) return currentShortcutContainer;
        }

        if (container != null) return container;
        Context context = getContext();
        return context != null && manager != null ? SetupWizardActivity.getPreferredGameContainer(context, manager) : null;
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

    private Container resolveSelectedShortcutContainer(Spinner sWineVersion) {
        if (sWineVersion == null || sWineVersion.getSelectedItem() == null || manager == null) return null;
        String selection = sWineVersion.getSelectedItem().toString();
        if (!selection.startsWith("Container: ")) return null;
        String containerName = selection.substring("Container: ".length());
        for (Container item : manager.getContainers()) {
            if (item.getName().equals(containerName)) {
                return item;
            }
        }
        return null;
    }

    private void clearShortcutSettingOverrides(Shortcut shortcut) {
        if (shortcut == null) return;
        for (String key : SHORTCUT_SETTING_OVERRIDE_KEYS) {
            shortcut.putExtra(key, null);
        }
    }

    private void resetShortcutToContainerDefaults(Shortcut shortcut, Container selectedShortcutContainer) {
        if (shortcut == null || selectedShortcutContainer == null) return;

        boolean containerChanged = selectedShortcutContainer.id != shortcut.container.id;
        clearShortcutSettingOverrides(shortcut);
        shortcut.putExtra(EXTRA_USE_CONTAINER_DEFAULTS, "1");
        shortcut.putExtra("wineVersion", null);
        shortcut.putExtra("container_id", String.valueOf(selectedShortcutContainer.id));
        shortcut.putExtra("cloud_force_download", containerChanged ? "1" : null);
        shortcut.saveData();

        if (containerChanged) {
            File newDesktopDir = selectedShortcutContainer.getDesktopDir();
            if (!newDesktopDir.exists()) newDesktopDir.mkdirs();
            File newShortcutFile = new File(newDesktopDir, shortcut.file.getName());
            FileUtils.copy(shortcut.file, newShortcutFile);
            shortcut.file.delete();
        }
    }

    private Container getShortcutSettingsContainer() {
        if (!isShortcutMode() || shortcut == null) return container;

        int fallbackContainerId = container != null ? container.id : 0;
        int selectedContainerId = parseIntSafely(shortcut.getExtra("container_id"), fallbackContainerId);
        if (selectedContainerId == 0 || manager == null) return container;

        Container selectedContainer = manager.getContainerById(selectedContainerId);
        return selectedContainer != null ? selectedContainer : container;
    }

    private String getShortcutSettingValue(String key, String containerValue) {
        if (!isShortcutMode() || shortcut == null) return containerValue;
        return shortcut.getSettingExtra(key, containerValue);
    }

    private boolean getShortcutSettingEnabled(String key, boolean containerValue) {
        return "1".equals(getShortcutSettingValue(key, containerValue ? "1" : "0"));
    }

    private int getShortcutSettingInt(String key, int containerValue) {
        return parseIntSafely(getShortcutSettingValue(key, String.valueOf(containerValue)), containerValue);
    }

    private static String normalizeShortcutValue(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean) return (Boolean) value ? "1" : "0";
        return String.valueOf(value);
    }

    private static boolean valuesDiffer(Object currentValue, Object containerValue) {
        return !normalizeShortcutValue(currentValue).equals(normalizeShortcutValue(containerValue));
    }

    private static void appendShortcutExtra(StringBuilder content, String key, String value) {
        content.append(key).append("=").append(value).append("\n");
    }

    private void trackLabel(TextView label) {
        if (label != null && !labelOriginalText.containsKey(label)) {
            labelOriginalText.put(label, label.getText().toString().replaceFirst("^\\*\\s*", ""));
        }
    }

    private void markIfChanged(TextView label, String currentValue, String containerDefault) {
        if (label == null) return;
        String originalText = labelOriginalText.get(label);
        if (originalText == null) {
            originalText = label.getText().toString().replaceFirst("^\\*\\s*", "");
            labelOriginalText.put(label, originalText);
        }
        label.setText(valuesDiffer(currentValue, containerDefault) ? "* " + originalText : originalText);
    }

    private void markSpinnerIfChanged(View contentView, int spinnerId, String containerDefault) {
        Spinner spinner = contentView.findViewById(spinnerId);
        if (spinner == null) return;
        TextView label = findLabelForView(spinner, contentView.findViewById(R.id.LLContent));
        String current = spinner.getSelectedItem() != null ? StringUtils.parseIdentifier(spinner.getSelectedItem()) : "";
        markIfChanged(label, current, containerDefault);
    }

    private void markSpinnerValueIfChanged(View contentView, int spinnerId, String containerDefault) {
        Spinner spinner = contentView.findViewById(spinnerId);
        if (spinner == null) return;
        TextView label = findLabelForView(spinner, contentView.findViewById(R.id.LLContent));
        String current = spinner.getSelectedItem() != null ? spinner.getSelectedItem().toString() : "";
        markIfChanged(label, current, containerDefault);
    }

    private void markSpinnerPositionIfChanged(View contentView, int spinnerId, String containerDefault) {
        Spinner spinner = contentView.findViewById(spinnerId);
        if (spinner == null) return;
        TextView label = findLabelForView(spinner, contentView.findViewById(R.id.LLContent));
        markIfChanged(label, String.valueOf(Math.max(spinner.getSelectedItemPosition(), 0)), containerDefault);
    }

    private boolean appendShortcutOverrideIfNeeded(StringBuilder content, Container targetContainer,
                                                   String key, Object currentValue, Object containerValue,
                                                   boolean[] hasOverrides) {
        if (!valuesDiffer(currentValue, containerValue)) return false;
        appendShortcutExtra(content, key, normalizeShortcutValue(currentValue));
        hasOverrides[0] = true;
        return true;
    }

    private boolean shortcutSettingsMatchContainerDefaults(HashMap<String, String> snapshot, Container targetContainer, String gameSource) {
        if (snapshot == null || targetContainer == null) return false;

        if (valuesDiffer(snapshot.get("screenSize"), targetContainer.getScreenSize())) return false;
        if (valuesDiffer(snapshot.get("envVars"), targetContainer.getEnvVars())) return false;
        if (valuesDiffer(snapshot.get("cpuList"), targetContainer.getCPUList(true))) return false;
        if (valuesDiffer(snapshot.get("cpuListWoW64"), targetContainer.getCPUListWoW64(true))) return false;
        if (valuesDiffer(snapshot.get("graphicsDriver"), targetContainer.getGraphicsDriver())) return false;
        if (valuesDiffer(snapshot.get("graphicsDriverConfig"), targetContainer.getGraphicsDriverConfig())) return false;
        if (valuesDiffer(snapshot.get("dxwrapper"), targetContainer.getDXWrapper())) return false;
        if (valuesDiffer(snapshot.get("dxwrapperConfig"), targetContainer.getDXWrapperConfig())) return false;
        if (valuesDiffer(snapshot.get("audioDriver"), targetContainer.getAudioDriver())) return false;
        if (valuesDiffer(snapshot.get("emulator"), targetContainer.getEmulator())) return false;
        if (valuesDiffer(snapshot.get("emulator64"), targetContainer.getEmulator64())) return false;
        if (valuesDiffer(snapshot.get("wincomponents"), targetContainer.getWinComponents())) return false;
        if (valuesDiffer(snapshot.get("drives"), targetContainer.getDrives())) return false;
        if (valuesDiffer(snapshot.get("showFPS"), targetContainer.isShowFPS())) return false;
        if (valuesDiffer(snapshot.get("fullscreenStretched"), targetContainer.isFullscreenStretched())) return false;
        if (valuesDiffer(snapshot.get("inputType"), targetContainer.getInputType())) return false;
        if (valuesDiffer(snapshot.get("disableXinput"), "")) return false;
        if (valuesDiffer(snapshot.get("startupSelection"), targetContainer.getStartupSelection())) return false;
        if (valuesDiffer(snapshot.get("box64Version"), targetContainer.getBox64Version())) return false;
        if (valuesDiffer(snapshot.get("box64Preset"), targetContainer.getBox64Preset())) return false;
        if (valuesDiffer(snapshot.get("fexcoreVersion"), targetContainer.getFEXCoreVersion())) return false;
        if (valuesDiffer(snapshot.get("fexcorePreset"), targetContainer.getFEXCorePreset())) return false;
        if (valuesDiffer(snapshot.get("desktopTheme"), targetContainer.getDesktopTheme())) return false;
        if (valuesDiffer(snapshot.get("midiSoundFont"), targetContainer.getMIDISoundFont())) return false;
        if (valuesDiffer(snapshot.get("lc_all"), targetContainer.getLC_ALL())) return false;
        if ("STEAM".equals(gameSource)) {
            if (valuesDiffer(snapshot.get("useLegacyDRM"), targetContainer.isUseLegacyDRM())) return false;
            if (valuesDiffer(snapshot.get("launchRealSteam"), targetContainer.isLaunchRealSteam())) return false;
            if (valuesDiffer(snapshot.get("useSteamInput"), targetContainer.getExtra("useSteamInput", "0"))) return false;
            if (valuesDiffer(snapshot.get("steamType"), targetContainer.getSteamType())) return false;
            if (valuesDiffer(snapshot.get("forceDlc"), targetContainer.isForceDlc())) return false;
            if (valuesDiffer(snapshot.get("steamOfflineMode"), targetContainer.isSteamOfflineMode())) return false;
            if (valuesDiffer(snapshot.get("unpackFiles"), targetContainer.isUnpackFiles())) return false;
        }

        return true;
    }

    private HashMap<String, String> collectShortcutSettingsSnapshot(
            View view,
            EnvVarsView envVarsView,
            View vGraphicsDriverConfig,
            View vDXWrapperConfig
    ) {
        Context context = getContext();
        HashMap<String, String> snapshot = new HashMap<>();

        String screenSize = getScreenSize(view);
        String envVars = envVarsView.getEnvVars();

        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        String graphicsDriver = sGraphicsDriver.getSelectedItem() != null
                ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem())
                : Container.DEFAULT_GRAPHICS_DRIVER;

        String graphicsDriverConfig = vGraphicsDriverConfig.getTag() != null
                ? vGraphicsDriverConfig.getTag().toString()
                : "";
        HashMap<String, String> graphicsConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        if (graphicsConfig.get("version") == null || graphicsConfig.get("version").isEmpty()) {
            String defaultVersion;
            try {
                defaultVersion = GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context)
                        ? DefaultVersion.WRAPPER_ADRENO
                        : DefaultVersion.WRAPPER;
            } catch (Throwable e) {
                defaultVersion = DefaultVersion.WRAPPER;
            }
            graphicsConfig.put("version", defaultVersion);
            graphicsDriverConfig = GraphicsDriverConfigDialog.toGraphicsDriverConfig(graphicsConfig);
        }

        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        String dxwrapper = sDXWrapper.getSelectedItem() != null
                ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem())
                : Container.DEFAULT_DXWRAPPER;
        String dxwrapperConfig = vDXWrapperConfig.getTag() != null
                ? vDXWrapperConfig.getTag().toString()
                : "";

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        String audioDriver = sAudioDriver.getSelectedItem() != null
                ? StringUtils.parseIdentifier(sAudioDriver.getSelectedItem())
                : Container.DEFAULT_AUDIO_DRIVER;

        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        String emulator = sEmulator.getSelectedItem() != null
                ? StringUtils.parseIdentifier(sEmulator.getSelectedItem())
                : Container.DEFAULT_EMULATOR;

        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        String emulator64 = sEmulator64.getSelectedItem() != null
                ? StringUtils.parseIdentifier(sEmulator64.getSelectedItem())
                : Container.DEFAULT_EMULATOR64;

        String wincomponents = getWinComponents(view);
        String drives = getDrives(view);

        CompoundButton cbShowFPS = view.findViewById(R.id.CBShowFPS);
        CompoundButton cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        CompoundButton cbUseLegacyDRM = view.findViewById(R.id.CBUseLegacyDRM);
        CompoundButton cbLaunchRealSteam = view.findViewById(R.id.CBLaunchRealSteam);

        CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        byte startupSelection = (byte)Math.max(0, sStartupSelection.getSelectedItemPosition());

        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        String box64Version = sBox64Version.getSelectedItem() != null
                ? sBox64Version.getSelectedItem().toString()
                : DefaultVersion.BOX64;

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);

        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        String fexcoreVersion = sFEXCoreVersion.getSelectedItem() != null
                ? sFEXCoreVersion.getSelectedItem().toString()
                : DefaultVersion.FEXCORE;

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        String fexcorePreset = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        String midiSoundFont = (sMIDISoundFont.getSelectedItemPosition() <= 0 || sMIDISoundFont.getSelectedItem() == null)
                ? ""
                : sMIDISoundFont.getSelectedItem().toString();

        EditText etLC_ALL = view.findViewById(R.id.ETlcall);

        CompoundButton cbEnableXInput = view.findViewById(R.id.CBEnableXInput);
        CompoundButton cbEnableDInput = view.findViewById(R.id.CBEnableDInput);
        Spinner sDInputType = view.findViewById(R.id.SDInputType);
        CompoundButton cbExclusiveInput = view.findViewById(R.id.CBExclusiveInput);
        int inputType = 0;
        inputType |= cbEnableXInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
        inputType |= cbEnableDInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
        inputType |= (sDInputType.getSelectedItemPosition() <= 0)
                ? WinHandler.FLAG_DINPUT_MAPPER_STANDARD
                : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;

        CompoundButton cbSdl2Toggle = view.findViewById(R.id.CBSdl2Toggle);
        if (cbSdl2Toggle.isChecked()) {
            for (String envVar : SDL2_ENV_VARS) {
                if (!envVars.contains(envVar)) {
                    envVars += (envVars.isEmpty() ? "" : " ") + envVar;
                }
            }
        } else {
            for (String envVar : SDL2_ENV_VARS) {
                envVars = envVars.replace(envVar, "").replaceAll("\\s{2,}", " ").trim();
            }
        }

        snapshot.put("screenSize", screenSize);
        snapshot.put("envVars", envVars);
        snapshot.put("cpuList", cpuListView.getCheckedCPUListAsString());
        snapshot.put("cpuListWoW64", cpuListViewWoW64.getCheckedCPUListAsString());
        snapshot.put("graphicsDriver", graphicsDriver);
        snapshot.put("graphicsDriverConfig", graphicsDriverConfig);
        snapshot.put("dxwrapper", dxwrapper);
        snapshot.put("dxwrapperConfig", dxwrapperConfig);
        snapshot.put("audioDriver", audioDriver);
        snapshot.put("emulator", emulator);
        snapshot.put("emulator64", emulator64);
        snapshot.put("wincomponents", wincomponents);
        snapshot.put("drives", drives);
        snapshot.put("showFPS", cbShowFPS.isChecked() ? "1" : "0");
        snapshot.put("fullscreenStretched", cbFullscreenStretched.isChecked() ? "1" : "0");
        snapshot.put("inputType", String.valueOf(inputType));
        snapshot.put("disableXinput", cbExclusiveInput != null && cbExclusiveInput.isChecked() ? "1" : "");
        snapshot.put("startupSelection", String.valueOf(startupSelection));
        snapshot.put("box64Version", box64Version);
        snapshot.put("box64Preset", box64Preset);
        snapshot.put("fexcoreVersion", fexcoreVersion);
        snapshot.put("fexcorePreset", fexcorePreset);
        snapshot.put("desktopTheme", getDesktopTheme(view));
        snapshot.put("midiSoundFont", midiSoundFont);
        snapshot.put("lc_all", etLC_ALL.getText().toString());
        snapshot.put("useLegacyDRM", cbUseLegacyDRM != null && cbUseLegacyDRM.isChecked() ? "1" : "0");
        snapshot.put("launchRealSteam", cbLaunchRealSteam != null && cbLaunchRealSteam.isChecked() ? "1" : "0");
        CompoundButton cbUseSteamInput = view.findViewById(R.id.CBUseSteamInput);
        snapshot.put("useSteamInput", cbUseSteamInput != null && cbUseSteamInput.isChecked() ? "1" : "0");
        Spinner sSteamType = view.findViewById(R.id.SSteamType);
        snapshot.put("steamType", sSteamType != null ? getSelectedSteamType(sSteamType) : Container.STEAM_TYPE_NORMAL);
        CompoundButton cbForceDlc = view.findViewById(R.id.CBForceDlc);
        snapshot.put("forceDlc", cbForceDlc != null && cbForceDlc.isChecked() ? "1" : "0");
        CompoundButton cbSteamOfflineMode = view.findViewById(R.id.CBSteamOfflineMode);
        snapshot.put("steamOfflineMode", cbSteamOfflineMode != null && cbSteamOfflineMode.isChecked() ? "1" : "0");
        CompoundButton cbUnpackFiles = view.findViewById(R.id.CBUnpackFiles);
        snapshot.put("unpackFiles", cbUnpackFiles != null && cbUnpackFiles.isChecked() ? "1" : "0");
        return snapshot;
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
        
        if (shortcut != null) {
            container = shortcut.container;
        } else {
            container = containerId > 0 ? manager.getContainerById(containerId) : null;
        }
        final Container settingsContainer = isPerGameSettingsMode() ? getInitialPerGameContainer() : container;
        
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
        final Runnable[] refreshIndicatorsRef = new Runnable[1];

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

        applyDarkMode(view);

        Log.d(TAG, "onCreateView: step 4 - loading wine version spinner");
        loadWineVersionSpinner(view, sWineVersion, sBox64Version, () -> runIndicatorRefresh(refreshIndicatorsRef));
        Log.d(TAG, "onCreateView: step 5 - wine version spinner loaded");

        loadScreenSizeSpinner(view, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("screenSize", settingsContainer != null ? settingsContainer.getScreenSize() : Container.DEFAULT_SCREEN_SIZE)
                    : (settingsContainer != null ? settingsContainer.getScreenSize() : Container.DEFAULT_SCREEN_SIZE))
                : (isEditMode() && container != null ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE),
                () -> runIndicatorRefresh(refreshIndicatorsRef));

        final Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        
        final Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);

        final View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("dxwrapperConfig", settingsContainer != null ? settingsContainer.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG)
                    : (settingsContainer != null ? settingsContainer.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG))
                : (isEditMode() && container != null ? container.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG));
        Log.d(TAG, "Initial DXVK config mode=" +
                (isShortcutMode() ? "shortcut" : (isEditMode() ? "container-edit" : "create-shortcut")) +
                " value='" + vDXWrapperConfig.getTag() + "'");

        final View vGraphicsDriverConfig = view.findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("graphicsDriverConfig", settingsContainer != null ? settingsContainer.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG)
                    : (settingsContainer != null ? settingsContainer.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG))
                : (isEditMode() && container != null ? container.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG));

        Log.d(TAG, "onCreateView: step 6 - loading graphics driver spinner");
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
                isPerGameSettingsMode()
                        ? (isShortcutMode()
                            ? getShortcutSettingValue("graphicsDriver", settingsContainer != null ? settingsContainer.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER)
                            : (settingsContainer != null ? settingsContainer.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER))
                        : (isEditMode() && container != null ? container.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER),
                isPerGameSettingsMode()
                        ? (isShortcutMode()
                            ? getShortcutSettingValue("dxwrapper", settingsContainer != null ? settingsContainer.getDXWrapper() : Container.DEFAULT_DXWRAPPER)
                            : (settingsContainer != null ? settingsContainer.getDXWrapper() : Container.DEFAULT_DXWRAPPER))
                        : (isEditMode() && container != null ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER),
                () -> runIndicatorRefresh(refreshIndicatorsRef));
        String initialWineVersion = settingsContainer != null ? settingsContainer.getWineVersion() : WineInfo.MAIN_WINE_VERSION.identifier();
        setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, WineInfo.fromIdentifier(context, contentsManager, initialWineVersion).isArm64EC(),
                () -> runIndicatorRefresh(refreshIndicatorsRef));
        Log.d(TAG, "onCreateView: step 7 - graphics driver spinner loaded");

        view.findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        applyThemedAdapter(sAudioDriver, R.array.audio_driver_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("audioDriver", settingsContainer != null ? settingsContainer.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER)
                    : (settingsContainer != null ? settingsContainer.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER))
                : (isEditMode() && container != null ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER));

        final Spinner sEmulator = view.findViewById(R.id.SEmulator);
        applyThemedAdapter(sEmulator, R.array.emulator_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("emulator", settingsContainer != null ? settingsContainer.getEmulator() : Container.DEFAULT_EMULATOR)
                    : (settingsContainer != null ? settingsContainer.getEmulator() : Container.DEFAULT_EMULATOR))
                : (isEditMode() && container != null ? container.getEmulator() : Container.DEFAULT_EMULATOR));

        final Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        applyThemedAdapter(sEmulator64, R.array.emulator_entries);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("emulator64", settingsContainer != null ? settingsContainer.getEmulator64() : Container.DEFAULT_EMULATOR64)
                    : (settingsContainer != null ? settingsContainer.getEmulator64() : Container.DEFAULT_EMULATOR64))
                : (isEditMode() && container != null ? container.getEmulator64() : Container.DEFAULT_EMULATOR64));

        final View box64Frame = view.findViewById(R.id.box64Frame);
        final View fexcoreFrame = view.findViewById(R.id.fexcoreFrame);

        AdapterView.OnItemSelectedListener emulatorListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                updateEmulatorFrames(view, sEmulator, sEmulator64);
                runIndicatorRefresh(refreshIndicatorsRef);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        sEmulator.setOnItemSelectedListener(emulatorListener);
        sEmulator64.setOnItemSelectedListener(emulatorListener);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        MidiManager.loadSFSpinner(sMIDISoundFont);
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("midiSoundFont", settingsContainer != null ? settingsContainer.getMIDISoundFont() : "")
                    : (settingsContainer != null ? settingsContainer.getMIDISoundFont() : ""))
                : (isEditMode() && container != null ? container.getMIDISoundFont() : ""));

        final CompoundButton cbShowFPS = view.findViewById(R.id.CBShowFPS);
        cbShowFPS.setChecked(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingEnabled("showFPS", settingsContainer != null && settingsContainer.isShowFPS())
                    : settingsContainer != null && settingsContainer.isShowFPS())
                : (isEditMode() && container != null && container.isShowFPS()));

        final CompoundButton cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        cbFullscreenStretched.setChecked(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingEnabled("fullscreenStretched", settingsContainer != null && settingsContainer.isFullscreenStretched())
                    : settingsContainer != null && settingsContainer.isFullscreenStretched())
                : (isEditMode() && container != null && container.isFullscreenStretched()));

        String activeGameSource = isShortcutMode() ? shortcut.getExtra("game_source", createShortcutForSource) : createShortcutForSource;
        boolean showSteamSettings = "STEAM".equals(activeGameSource) && (isShortcutMode() || isCreateShortcutMode());
        final View llSteamSettings = view.findViewById(R.id.LLSteamSettings);
        llSteamSettings.setVisibility(showSteamSettings ? View.VISIBLE : View.GONE);
        final CompoundButton cbUseLegacyDRM = view.findViewById(R.id.CBUseLegacyDRM);
        final CompoundButton cbLaunchRealSteam = view.findViewById(R.id.CBLaunchRealSteam);
        final CompoundButton cbUseSteamInput = view.findViewById(R.id.CBUseSteamInput);
        boolean defaultUseLegacyDRM = settingsContainer != null && settingsContainer.isUseLegacyDRM();
        boolean defaultLaunchRealSteam = settingsContainer != null && settingsContainer.isLaunchRealSteam();
        boolean defaultUseSteamInput = settingsContainer != null && "1".equals(settingsContainer.getExtra("useSteamInput", "0"));
        cbUseLegacyDRM.setChecked(
                isPerGameSettingsMode() && isShortcutMode()
                        ? getShortcutSettingEnabled("useLegacyDRM", defaultUseLegacyDRM)
                        : defaultUseLegacyDRM
        );
        cbLaunchRealSteam.setChecked(
                isPerGameSettingsMode() && isShortcutMode()
                        ? getShortcutSettingEnabled("launchRealSteam", defaultLaunchRealSteam)
                        : defaultLaunchRealSteam
        );
        cbUseSteamInput.setChecked(
                isPerGameSettingsMode() && isShortcutMode()
                        ? getShortcutSettingEnabled("useSteamInput", defaultUseSteamInput)
                        : defaultUseSteamInput
        );

        final Spinner sSteamType = view.findViewById(R.id.SSteamType);
        applyThemedAdapter(sSteamType, R.array.steam_type_entries);
        String defaultSteamType = settingsContainer != null ? settingsContainer.getSteamType() : Container.STEAM_TYPE_NORMAL;
        if (isPerGameSettingsMode() && isShortcutMode()) {
            defaultSteamType = getShortcutSettingValue("steamType", defaultSteamType);
        }
        int steamTypeIndex = Container.STEAM_TYPE_ULTRALIGHT.equals(defaultSteamType) ? 2
                : Container.STEAM_TYPE_LIGHT.equals(defaultSteamType) ? 1 : 0;
        sSteamType.setSelection(steamTypeIndex);

        final CompoundButton cbForceDlc = view.findViewById(R.id.CBForceDlc);
        boolean defaultForceDlc = settingsContainer != null && settingsContainer.isForceDlc();
        cbForceDlc.setChecked(
                isPerGameSettingsMode() && isShortcutMode()
                        ? getShortcutSettingEnabled("forceDlc", defaultForceDlc)
                        : defaultForceDlc
        );

        final CompoundButton cbSteamOfflineMode = view.findViewById(R.id.CBSteamOfflineMode);
        boolean defaultSteamOfflineMode = settingsContainer != null && settingsContainer.isSteamOfflineMode();
        cbSteamOfflineMode.setChecked(
                isPerGameSettingsMode() && isShortcutMode()
                        ? getShortcutSettingEnabled("steamOfflineMode", defaultSteamOfflineMode)
                        : defaultSteamOfflineMode
        );

        final CompoundButton cbUnpackFiles = view.findViewById(R.id.CBUnpackFiles);
        boolean defaultUnpackFiles = settingsContainer != null && settingsContainer.isUnpackFiles();
        cbUnpackFiles.setChecked(
                isPerGameSettingsMode() && isShortcutMode()
                        ? getShortcutSettingEnabled("unpackFiles", defaultUnpackFiles)
                        : defaultUnpackFiles
        );

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
        int inputType = isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingInt("inputType", settingsContainer != null ? settingsContainer.getInputType() : WinHandler.DEFAULT_INPUT_TYPE)
                    : (settingsContainer != null ? settingsContainer.getInputType() : WinHandler.DEFAULT_INPUT_TYPE))
                : (isEditMode() && container != null ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE);

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
            boolean exclusiveInputEnabled = isPerGameSettingsMode()
                    ? (isShortcutMode() ? getShortcutSettingEnabled("disableXinput", false) : false)
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
        String envVarsValue = isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("envVars", settingsContainer != null ? settingsContainer.getEnvVars() : Container.DEFAULT_ENV_VARS)
                    : (settingsContainer != null ? settingsContainer.getEnvVars() : Container.DEFAULT_ENV_VARS))
                : (isEditMode() && container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS);
        cbSdl2Toggle.setChecked(envVarsValue.contains("SDL_XINPUT_ENABLED=1"));

        final EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        Locale systemLocal = Locale.getDefault();
        etLC_ALL.setText(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("lc_all", settingsContainer != null ? settingsContainer.getLC_ALL() : "")
                    : (settingsContainer != null ? settingsContainer.getLC_ALL() : ""))
                : (isEditMode() && container != null ? container.getLC_ALL() : systemLocal.getLanguage() + '_' + systemLocal.getCountry() + ".UTF-8"));

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
        byte previousStartupSelection = isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? (byte)getShortcutSettingInt("startupSelection", settingsContainer != null ? settingsContainer.getStartupSelection() : -1)
                    : (byte)(settingsContainer != null ? settingsContainer.getStartupSelection() : -1))
                : (isEditMode() && container != null ? container.getStartupSelection() : -1);
        sStartupSelection.setSelection(previousStartupSelection != -1 ? previousStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Box64PresetManager.loadSpinner("box64", sBox64Preset, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("box64Preset", settingsContainer != null ? settingsContainer.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.COMPATIBILITY))
                    : (settingsContainer != null ? settingsContainer.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.COMPATIBILITY)))
                : (isEditMode() && container != null ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.COMPATIBILITY)));

        final Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("fexcoreVersion", settingsContainer != null ? settingsContainer.getFEXCoreVersion() : DefaultVersion.FEXCORE)
                    : (settingsContainer != null ? settingsContainer.getFEXCoreVersion() : DefaultVersion.FEXCORE))
                : (isEditMode() && container != null ? container.getFEXCoreVersion() : DefaultVersion.FEXCORE));

        final Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        FEXCorePresetManager.loadSpinner(sFEXCorePreset, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("fexcorePreset", settingsContainer != null ? settingsContainer.getFEXCorePreset() : preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE))
                    : (settingsContainer != null ? settingsContainer.getFEXCorePreset() : preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE)))
                : (isEditMode() && container != null ? container.getFEXCorePreset() : preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE)));

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("cpuList", settingsContainer != null ? settingsContainer.getCPUList(true) : Container.getFallbackCPUList())
                    : (settingsContainer != null ? settingsContainer.getCPUList(true) : Container.getFallbackCPUList()))
                : (isEditMode() && container != null ? container.getCPUList(true) : Container.getFallbackCPUList()));
        cpuListViewWoW64.setCheckedCPUList(isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("cpuListWoW64", settingsContainer != null ? settingsContainer.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64())
                    : (settingsContainer != null ? settingsContainer.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64()))
                : (isEditMode() && container != null ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64()));

        createWineConfigurationTab(view);
        final EnvVarsView envVarsView = createEnvVarsTab(view);
        createWinComponentsTab(view, isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("wincomponents", settingsContainer != null ? settingsContainer.getWinComponents() : Container.DEFAULT_WINCOMPONENTS)
                    : (settingsContainer != null ? settingsContainer.getWinComponents() : Container.DEFAULT_WINCOMPONENTS))
                : (isEditMode() && container != null ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS));
        createDrivesTab(view);
        if (isPerGameSettingsMode()) {
            refreshIndicatorsRef[0] = () -> {
                Container comparisonContainer = getSelectedPerGameContainer(sWineVersion);
                if (comparisonContainer != null) {
                    refreshPerGameChangeIndicators(view, comparisonContainer);
                }
            };
            perGameRefreshIndicators = refreshIndicatorsRef[0];
            setupPerGameChangeIndicators(view, sWineVersion, refreshIndicatorsRef[0]);
            runIndicatorRefresh(refreshIndicatorsRef);
        }

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
            btnReset.setVisibility(isPerGameSettingsMode() ? View.VISIBLE : View.GONE);
            btnReset.setOnTouchListener((v, event) -> {
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
            btnReset.setOnClickListener((v) -> {
                Container selectedShortcutContainer = getSelectedPerGameContainer(sWineVersion);
                if (selectedShortcutContainer == null) return;

                ContentDialog dialog = new ContentDialog(context);
                dialog.setTitle("Reset Per-Game Settings");
                dialog.setMessage("Reset this game to use the selected container defaults?");
                Container finalSelectedShortcutContainer = selectedShortcutContainer;
                dialog.setOnConfirmCallback(() -> {
                    updateUIWithContainerSettings(view, finalSelectedShortcutContainer);
                    runIndicatorRefresh(refreshIndicatorsRef);
                    Toast.makeText(context, "Per-game settings reset to selected container defaults", Toast.LENGTH_SHORT).show();
                });
                dialog.show();
            });
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
                Log.d(TAG, "Confirm clicked mode=" +
                        (isShortcutMode() ? "shortcut" : (isEditMode() ? "container-edit" : "create-shortcut")) +
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
                boolean useLegacyDRM = cbUseLegacyDRM.isChecked();
                boolean launchRealSteam = cbLaunchRealSteam.isChecked();
                boolean useSteamInput = cbUseSteamInput.isChecked();
                String steamType = sSteamType.getSelectedItemPosition() == 2 ? Container.STEAM_TYPE_ULTRALIGHT
                        : sSteamType.getSelectedItemPosition() == 1 ? Container.STEAM_TYPE_LIGHT
                        : Container.STEAM_TYPE_NORMAL;
                boolean forceDlc = cbForceDlc.isChecked();
                boolean steamOfflineMode = cbSteamOfflineMode.isChecked();
                boolean unpackFiles = cbUnpackFiles.isChecked();

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
                    Container selectedShortcutContainer = resolveSelectedShortcutContainer(sWineVersion);
                    HashMap<String, String> currentShortcutSettings =
                            collectShortcutSettingsSnapshot(view, envVarsView, vGraphicsDriverConfig, vDXWrapperConfig);

                    String gameSource = shortcut.getExtra("game_source", createShortcutForSource);
                    boolean keepUsingContainerDefaults = selectedShortcutContainer != null
                            && shortcutSettingsMatchContainerDefaults(currentShortcutSettings, selectedShortcutContainer, gameSource);
                    if (showLaunchExeSelector && selectedExePath[0] != null && !selectedExePath[0].isEmpty()) {
                        shortcut.putExtra("launch_exe_path", selectedExePath[0]);
                        if ("CUSTOM".equals(gameSource)) {
                            shortcut.putExtra("custom_exe", selectedExePath[0]);
                        }
                        rewriteShortcutExecLine(shortcut.file, buildExecCommandForSource(gameSource, shortcutAppId(), shortcutGogId(), shortcut, selectedExePath[0], launchRealSteam));
                    }

                    shortcut.putExtra("customLibraryIconPath", customLibraryIconPath.isEmpty() ? null : customLibraryIconPath);
                    shortcut.putExtra("customCoverArtPath", customLibraryIconPath.isEmpty() ? null : customLibraryIconPath);
                    if ("STEAM".equals(gameSource)) {
                        rewriteShortcutExecLine(shortcut.file, buildExecCommandForSource(gameSource, shortcutAppId(), shortcutGogId(), shortcut, selectedExePath[0], launchRealSteam));
                    }

                    if (keepUsingContainerDefaults) {
                        clearShortcutSettingOverrides(shortcut);
                        shortcut.putExtra(EXTRA_USE_CONTAINER_DEFAULTS, "1");
                    } else {
                        shortcut.putExtra("screenSize", screenSize);
                        shortcut.putExtra("envVars", envVars);
                        shortcut.putExtra("cpuList", cpuList);
                        shortcut.putExtra("cpuListWoW64", cpuListWoW64);
                        shortcut.putExtra("graphicsDriver", graphicsDriver);
                        shortcut.putExtra("graphicsDriverConfig", graphicsDriverConfig);
                        shortcut.putExtra("dxwrapper", dxwrapper);
                        shortcut.putExtra("dxwrapperConfig", dxwrapperConfig);
                        Log.d(TAG, "Saving shortcut dxwrapperConfig shortcut='" + shortcut.name +
                                "' containerId=" + shortcut.container.id + " value='" + dxwrapperConfig + "'");
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
                        if ("STEAM".equals(gameSource)) {
                            shortcut.putExtra("useLegacyDRM", useLegacyDRM ? "1" : "0");
                            shortcut.putExtra("launchRealSteam", launchRealSteam ? "1" : "0");
                            shortcut.putExtra("useSteamInput", useSteamInput ? "1" : "0");
                            shortcut.putExtra("steamType", steamType);
                            shortcut.putExtra("forceDlc", forceDlc ? "1" : "0");
                            shortcut.putExtra("steamOfflineMode", steamOfflineMode ? "1" : "0");
                            shortcut.putExtra("unpackFiles", unpackFiles ? "1" : "0");
                        } else {
                            shortcut.putExtra("useLegacyDRM", null);
                            shortcut.putExtra("launchRealSteam", null);
                            shortcut.putExtra("useSteamInput", null);
                            shortcut.putExtra("steamType", null);
                            shortcut.putExtra("forceDlc", null);
                            shortcut.putExtra("steamOfflineMode", null);
                            shortcut.putExtra("unpackFiles", null);
                        }
                        shortcut.putExtra(EXTRA_USE_CONTAINER_DEFAULTS, "0");
                    }
                    
                    // Handle container_id override
                    boolean saved = false;
                    if (selectedShortcutContainer != null) {
                        boolean containerChanged = selectedShortcutContainer.id != shortcut.container.id;
                        shortcut.putExtra("container_id", String.valueOf(selectedShortcutContainer.id));
                        shortcut.putExtra("wineVersion", null);
                        shortcut.putExtra("cloud_force_download", containerChanged ? "1" : null);
                        shortcut.saveData();
                        saved = true;

                        if (containerChanged) {
                            java.io.File newDesktopDir = selectedShortcutContainer.getDesktopDir();
                            if (!newDesktopDir.exists()) newDesktopDir.mkdirs();
                            java.io.File newShortcutFile = new java.io.File(newDesktopDir, shortcut.file.getName());
                            com.winlator.cmod.core.FileUtils.copy(shortcut.file, newShortcutFile);
                            shortcut.file.delete();
                        }
                    }
                    if (!saved) {
                        Log.d(TAG, "Persist shortcut.saveData shortcut='" + shortcut.name +
                                "' containerId=" + shortcut.container.id +
                                "' finalDxwrapperConfig='" + shortcut.getExtra("dxwrapperConfig") + "'");
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
                    Log.d(TAG, "Persist container.saveData containerId=" + container.id +
                            " finalDxwrapperConfig='" + container.getDXWrapperConfig() + "'");
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
                    data.put("disableXinput", cbExclusiveInput != null && cbExclusiveInput.isChecked() ? "1" : "");
                    data.put("startupSelection", startupSelection);
                    data.put("box64Version", box64Version);
                    data.put("box64Preset", box64Preset);
                    data.put("fexcoreVersion", fexcoreVersion);
                    data.put("fexcorePreset", fexcorePreset);
                    data.put("desktopTheme", desktopTheme);
                    if ("STEAM".equals(createShortcutForSource)) {
                        data.put("useLegacyDRM", useLegacyDRM ? "1" : "0");
                        data.put("launchRealSteam", launchRealSteam ? "1" : "0");
                        data.put("useSteamInput", useSteamInput ? "1" : "0");
                    }
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
        Container settingsContainer = isShortcutMode()
                ? getShortcutSettingsContainer()
                : (isCreateShortcutMode() ? getInitialPerGameContainer() : container);

        String desktopThemeValue = isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("desktopTheme", settingsContainer != null ? settingsContainer.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME)
                    : (settingsContainer != null ? settingsContainer.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME))
                : (isEditMode() && container != null ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME);
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

        List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable), context.getString(R.string.force));
        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        sMouseWarpOverride.setAdapter(createThemedAdapter(context, mouseWarpOverrideList));
        applyPopupBackground(sMouseWarpOverride);

        File containerDir = settingsContainer != null ? settingsContainer.getRootDir() : null;
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
        loadScreenSizeSpinner(view, selectedValue, null);
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue, @Nullable Runnable onChanged) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        applyThemedAdapter(sScreenSize, R.array.screen_size_entries);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
                if (onChanged != null) onChanged.run();
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
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig, selectedGraphicsDriver, selectedDXWrapper, null);
    }

    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig,
                                          String selectedGraphicsDriver, String selectedDXWrapper, @Nullable Runnable onChanged) {
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
                if (onChanged != null) onChanged.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set the spinner's initial selection
        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
        if (onChanged != null) onChanged.run();
    }

    public static void setupDXWrapperSpinner(final Spinner sDXWrapper, final View vDXWrapperConfig, boolean isARM64EC) {
        setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, isARM64EC, null);
    }

    public static void setupDXWrapperSpinner(final Spinner sDXWrapper, final View vDXWrapperConfig, boolean isARM64EC,
                                             @Nullable Runnable onChanged) {
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
                if (onChanged != null) onChanged.run();
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

    public static void createWinComponentsTabFromShortcut(ShortcutSettingsDialog dialog, View view, String wincomponents) {
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
        dialog.onWinComponentsViewsAdded();
    }

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        final Container settingsContainer = isShortcutMode()
                ? getShortcutSettingsContainer()
                : (isCreateShortcutMode() ? getInitialPerGameContainer() : container);

        String envVarsValue;
        if (isShortcutMode() && shortcut != null) {
            envVarsValue = getShortcutSettingValue(
                    "envVars",
                    settingsContainer != null ? settingsContainer.getEnvVars() : Container.DEFAULT_ENV_VARS
            );
        } else if (isCreateShortcutMode()) {
            envVarsValue = settingsContainer != null ? settingsContainer.getEnvVars() : Container.DEFAULT_ENV_VARS;
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
        final Container settingsContainer = isShortcutMode()
                ? getShortcutSettingsContainer()
                : (isCreateShortcutMode() ? getInitialPerGameContainer() : container);

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        LayoutInflater inflater = LayoutInflater.from(context);
        final String drives = isPerGameSettingsMode()
                ? (isShortcutMode()
                    ? getShortcutSettingValue("drives", settingsContainer != null ? settingsContainer.getDrives() : Container.DEFAULT_DRIVES)
                    : (settingsContainer != null ? settingsContainer.getDrives() : Container.DEFAULT_DRIVES))
                : (isEditMode() && container != null ? container.getDrives() : Container.DEFAULT_DRIVES);
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
        loadWineVersionSpinner(view, sWineVersion, sBox64Version, null);
    }

    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, Spinner sBox64Version, @Nullable Runnable onChanged) {
        final Context context = getContext();
        // Allow changing container in shortcut/per-game settings mode; 
        // only lock it when editing a container directly 
        sWineVersion.setEnabled(!isEditMode() || isPerGameSettingsMode());

        final boolean[] isInitialWineSelection = {true};
        sWineVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                View fexcoreFL = view.findViewById(R.id.fexcoreFrame);
                Spinner sEmulator = view.findViewById(R.id.SEmulator);
                Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
                Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
                View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
                
                String selectedWineStr = sWineVersion.getSelectedItem() != null ? sWineVersion.getSelectedItem().toString() : WineInfo.MAIN_WINE_VERSION.identifier();
                
                Container selectedContainer = null;
                // In shortcut/per-game mode, the spinner shows "Container: Name" instead of wine version IDs.
                // We need to resolve the actual container's wine version to correctly detect ARM64EC.
                if (selectedWineStr.startsWith("Container: ")) {
                    String containerName = selectedWineStr.substring("Container: ".length());
                    for (Container c : manager.getContainers()) {
                        if (c.getName().equals(containerName)) {
                            selectedWineStr = c.getWineVersion();
                            selectedContainer = c;
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
                Container box64Container = selectedContainer != null ? selectedContainer : container;
                loadBox64VersionSpinner(context, box64Container, contentsManager, sBox64Version, wineInfo.isArm64EC());
                // Re-apply shortcut's box64Version override if in shortcut mode
                if (isShortcutMode() && shortcut != null && isInitialWineSelection[0] && !shortcut.usesContainerDefaults()) {
                    String shortcutBox64 = shortcut.getExtra("box64Version", "");
                    if (!shortcutBox64.isEmpty()) {
                        AppUtils.setSpinnerSelectionFromValue(sBox64Version, shortcutBox64);
                    }
                }
                setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, wineInfo.isArm64EC());

                // In per-game mode, the selected container is the active defaults source.
                if (selectedContainer != null && isPerGameSettingsMode()
                        && (!isInitialWineSelection[0] || isCreateShortcutMode())) {
                    updateUIWithContainerSettings(view, selectedContainer);
                }

                if (onChanged != null) onChanged.run();

                isInitialWineSelection[0] = false;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        ArrayList<String> wineVersions = new ArrayList<>();
        
        if (isPerGameSettingsMode()) {
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
            Container currentSC = getShortcutSettingsContainer();
            if (currentSC != null) {
                AppUtils.setSpinnerSelectionFromValue(sWineVersion, "Container: " + currentSC.getName());
            }
        } else if (isCreateShortcutMode()) {
            Container initialContainer = getInitialPerGameContainer();
            if (initialContainer != null) {
                AppUtils.setSpinnerSelectionFromValue(sWineVersion, "Container: " + initialContainer.getName());
            }
        } else if (isEditMode() && container != null) {
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

    private void updateUIWithContainerSettings(View view, Container c) {
        loadScreenSizeSpinner(view, c.getScreenSize(), null);

        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        if (sGraphicsDriver != null) AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, c.getGraphicsDriver());

        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        if (sDXWrapper != null) AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, c.getDXWrapper());

        View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
        if (vDXWrapperConfig != null) vDXWrapperConfig.setTag(c.getDXWrapperConfig());

        View vGraphicsDriverConfig = view.findViewById(R.id.BTGraphicsDriverConfig);
        if (vGraphicsDriverConfig != null) vGraphicsDriverConfig.setTag(c.getGraphicsDriverConfig());

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        if (sAudioDriver != null) AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, c.getAudioDriver());

        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        if (sBox64Version != null) AppUtils.setSpinnerSelectionFromValue(sBox64Version, c.getBox64Version());
        
        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        if (sFEXCoreVersion != null) AppUtils.setSpinnerSelectionFromValue(sFEXCoreVersion, c.getFEXCoreVersion());

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        if (sBox64Preset != null) AppUtils.setSpinnerSelectionFromIdentifier(sBox64Preset, c.getBox64Preset());

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        if (sFEXCorePreset != null) AppUtils.setSpinnerSelectionFromIdentifier(sFEXCorePreset, c.getFEXCorePreset());

        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        if (sEmulator != null) AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, c.getEmulator());

        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        if (sEmulator64 != null) AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, c.getEmulator64());
        if (sEmulator != null && sEmulator64 != null) updateEmulatorFrames(view, sEmulator, sEmulator64);

        CompoundButton cbShowFPS = view.findViewById(R.id.CBShowFPS);
        if (cbShowFPS != null) cbShowFPS.setChecked(c.isShowFPS());

        CompoundButton cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        if (cbFullscreenStretched != null) cbFullscreenStretched.setChecked(c.isFullscreenStretched());

        CompoundButton cbLaunchRealSteam = view.findViewById(R.id.CBLaunchRealSteam);
        if (cbLaunchRealSteam != null) cbLaunchRealSteam.setChecked(c.isLaunchRealSteam());

        CompoundButton cbUseLegacyDRM = view.findViewById(R.id.CBUseLegacyDRM);
        if (cbUseLegacyDRM != null) cbUseLegacyDRM.setChecked(c.isUseLegacyDRM());

        CompoundButton cbUseSteamInput = view.findViewById(R.id.CBUseSteamInput);
        if (cbUseSteamInput != null) cbUseSteamInput.setChecked("1".equals(c.getExtra("useSteamInput", "0")));

        Spinner sSteamType = view.findViewById(R.id.SSteamType);
        if (sSteamType != null) {
            String st = c.getSteamType();
            int stIdx = Container.STEAM_TYPE_ULTRALIGHT.equals(st) ? 2 : Container.STEAM_TYPE_LIGHT.equals(st) ? 1 : 0;
            sSteamType.setSelection(stIdx);
        }

        CompoundButton cbForceDlc = view.findViewById(R.id.CBForceDlc);
        if (cbForceDlc != null) cbForceDlc.setChecked(c.isForceDlc());

        CompoundButton cbSteamOfflineMode = view.findViewById(R.id.CBSteamOfflineMode);
        if (cbSteamOfflineMode != null) cbSteamOfflineMode.setChecked(c.isSteamOfflineMode());

        CompoundButton cbUnpackFiles = view.findViewById(R.id.CBUnpackFiles);
        if (cbUnpackFiles != null) cbUnpackFiles.setChecked(c.isUnpackFiles());

        Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        if (sStartupSelection != null) {
            int startupSelection = c.getStartupSelection();
            sStartupSelection.setSelection(startupSelection != -1 ? startupSelection : Container.STARTUP_SELECTION_ESSENTIAL);
        }

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        if (sMIDISoundFont != null) AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, c.getMIDISoundFont());

        EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        if (etLC_ALL != null) etLC_ALL.setText(c.getLC_ALL());

        CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        if (cpuListView != null) cpuListView.setCheckedCPUList(c.getCPUList(true));

        CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);
        if (cpuListViewWoW64 != null) cpuListViewWoW64.setCheckedCPUList(c.getCPUListWoW64(true));

        applyInputTypeToUi(view, c.getInputType(), false);
        EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        if (envVarsView != null) {
            envVarsView.setEnvVars(new EnvVars(c.getEnvVars()));
            CompoundButton cbSdl2Toggle = view.findViewById(R.id.CBSdl2Toggle);
            if (cbSdl2Toggle != null) cbSdl2Toggle.setChecked(c.getEnvVars().contains("SDL_XINPUT_ENABLED=1"));
        }

        // Update wincomponents by removing old views and re-adding
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        if (tabView != null) {
            ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
            ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);
            if (directxSectionView != null) directxSectionView.removeAllViews();
            if (generalSectionView != null) generalSectionView.removeAllViews();
            createWinComponentsTab(view, c.getWinComponents());
        }

        populateDrivesTab(view, c.getDrives());
        applyWineConfigurationSettings(view, c);
        if (perGameRefreshIndicators != null) {
            attachWinComponentsIndicatorListeners(view, perGameRefreshIndicators);
        }
    }

    private void applyWineConfigurationSettings(View view, Container settingsContainer) {
        if (settingsContainer == null) return;

        String desktopThemeValue = settingsContainer.getDesktopTheme();
        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(desktopThemeValue);

        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        if (sDesktopTheme != null) sDesktopTheme.setSelection(desktopTheme.theme.ordinal());

        ColorPickerView cpvDesktopBackgroundColor = view.findViewById(R.id.CPVDesktopBackgroundColor);
        if (cpvDesktopBackgroundColor != null) cpvDesktopBackgroundColor.setColor(desktopTheme.backgroundColor);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        if (sDesktopBackgroundType != null) sDesktopBackgroundType.setSelection(desktopTheme.backgroundType.ordinal());

        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        if (sMouseWarpOverride != null) {
            File userRegFile = new File(settingsContainer.getRootDir(), ".wine/user.reg");
            if (userRegFile.exists()) {
                try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                    AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride,
                            registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable"));
                }
            } else {
                AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, "disable");
            }
        }
    }

    private void applyInputTypeToUi(View view, int inputType, boolean exclusiveInputEnabled) {
        CompoundButton cbEnableXInput = view.findViewById(R.id.CBEnableXInput);
        CompoundButton cbEnableDInput = view.findViewById(R.id.CBEnableDInput);
        CompoundButton cbExclusiveInput = view.findViewById(R.id.CBExclusiveInput);
        View llDInputType = view.findViewById(R.id.LLDinputMapperType);
        Spinner sDInputType = view.findViewById(R.id.SDInputType);

        if (cbEnableXInput != null) {
            cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
            cbEnableXInput.setEnabled(exclusiveInputEnabled);
        }
        if (cbEnableDInput != null) {
            cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);
            cbEnableDInput.setEnabled(exclusiveInputEnabled);
        }
        if (cbExclusiveInput != null) cbExclusiveInput.setChecked(exclusiveInputEnabled);
        if (sDInputType != null) {
            sDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
        }
        if (llDInputType != null && cbEnableDInput != null) {
            llDInputType.setVisibility(cbEnableDInput.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    private void populateDrivesTab(View view, String drives) {
        final Context context = getContext();
        if (context == null) return;

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        if (parent == null || emptyTextView == null) return;

        parent.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char) (i + 68)) + ":";

        Callback<String[]> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(createThemedAdapter(context, driveLetters));
            applyPopupBackground(spinner);
            AppUtils.setSpinnerSelectionFromValue(spinner, drive[0] + ":");
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
            emptyTextView.setVisibility(View.GONE);
        };

        for (String[] drive : Container.drivesIterator(drives)) addItem.call(drive);

        View addDriveButton = view.findViewById(R.id.BTAddDrive);
        if (addDriveButton != null) {
            addDriveButton.setOnClickListener((v) -> {
                if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
                final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
                addItem.call(new String[]{nextDriveLetter, ""});
            });
        }

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private void runIndicatorRefresh(Runnable[] refreshIndicatorsRef) {
        if (refreshIndicatorsRef != null && refreshIndicatorsRef.length > 0 && refreshIndicatorsRef[0] != null) {
            refreshIndicatorsRef[0].run();
        }
    }

    private void setupPerGameChangeIndicators(View contentView, Spinner sWineVersion, Runnable refreshIndicators) {
        if (contentView == null || refreshIndicators == null) return;

        attachRefreshOnSelection(contentView.findViewById(R.id.SAudioDriver), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SMIDISoundFont), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SBox64Version), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SBox64Preset), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SFEXCoreVersion), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SFEXCorePreset), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SStartupSelection), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SSteamType), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SDInputType), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SDesktopTheme), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SMouseWarpOverride), refreshIndicators);

        attachRefreshOnClick(contentView.findViewById(R.id.CBShowFPS), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBFullscreenStretched), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBUseLegacyDRM), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBLaunchRealSteam), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBUseSteamInput), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBForceDlc), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBSteamOfflineMode), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBUnpackFiles), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBEnableXInput), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBEnableDInput), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBExclusiveInput), refreshIndicators);
        attachRefreshOnClick(contentView.findViewById(R.id.CBSdl2Toggle), refreshIndicators);

        attachRefreshOnTextChanged(contentView.findViewById(R.id.ETScreenWidth), refreshIndicators);
        attachRefreshOnTextChanged(contentView.findViewById(R.id.ETScreenHeight), refreshIndicators);
        attachRefreshOnTextChanged(contentView.findViewById(R.id.ETlcall), refreshIndicators);

        View addEnvVarButton = contentView.findViewById(R.id.BTAddEnvVar);
        if (addEnvVarButton != null) addEnvVarButton.setOnClickListener((v) -> {
            EnvVarsView envVarsView = contentView.findViewById(R.id.EnvVarsView);
            new AddEnvVarDialog(contentView.getContext(), envVarsView).show();
            refreshIndicators.run();
        });

        attachWinComponentsIndicatorListeners(contentView, refreshIndicators);
    }

    private void attachRefreshOnSelection(@Nullable Spinner spinner, Runnable refreshIndicators) {
        if (spinner == null) return;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshIndicators.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void attachRefreshOnClick(@Nullable View view, Runnable refreshIndicators) {
        if (view == null) return;
        view.setOnClickListener((v) -> refreshIndicators.run());
    }

    private void attachRefreshOnTextChanged(@Nullable EditText editText, Runnable refreshIndicators) {
        if (editText == null) return;
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshIndicators.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void attachWinComponentsIndicatorListeners(View contentView, Runnable refreshIndicators) {
        ViewGroup tabView = contentView.findViewById(R.id.LLTabWinComponents);
        if (tabView == null) return;

        ArrayList<View> spinnerViews = new ArrayList<>();
        AppUtils.findViewsWithClass(tabView, Spinner.class, spinnerViews);
        for (View spinnerView : spinnerViews) {
            Spinner spinner = (Spinner) spinnerView;
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    refreshIndicators.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void refreshPerGameChangeIndicators(View contentView, Container comparisonContainer) {
        if (contentView == null || comparisonContainer == null) return;

        ViewGroup llContent = contentView.findViewById(R.id.LLContent);
        if (llContent == null) return;

        markIfChanged(findLabelForView(contentView.findViewById(R.id.SScreenSize), llContent),
                getScreenSize(contentView),
                comparisonContainer.getScreenSize());

        TextView graphicsDriverLabel = findLabelForView(contentView.findViewById(R.id.SGraphicsDriver), llContent);
        if (graphicsDriverLabel != null) {
            trackLabel(graphicsDriverLabel);
            Spinner sGraphicsDriver = contentView.findViewById(R.id.SGraphicsDriver);
            String graphicsDriver = sGraphicsDriver != null && sGraphicsDriver.getSelectedItem() != null
                    ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()) : "";
            View graphicsConfigButton = contentView.findViewById(R.id.BTGraphicsDriverConfig);
            String graphicsDriverConfig = graphicsConfigButton != null && graphicsConfigButton.getTag() != null
                    ? graphicsConfigButton.getTag().toString() : "";
            boolean graphicsChanged = valuesDiffer(graphicsDriver, comparisonContainer.getGraphicsDriver())
                    || valuesDiffer(graphicsDriverConfig, comparisonContainer.getGraphicsDriverConfig());
            markIfChanged(graphicsDriverLabel, graphicsChanged ? "1" : "", "");
        }

        TextView dxWrapperLabel = findLabelForView(contentView.findViewById(R.id.SDXWrapper), llContent);
        if (dxWrapperLabel != null) {
            trackLabel(dxWrapperLabel);
            Spinner sDXWrapper = contentView.findViewById(R.id.SDXWrapper);
            String dxwrapper = sDXWrapper != null && sDXWrapper.getSelectedItem() != null
                    ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()) : "";
            View dxWrapperConfigButton = contentView.findViewById(R.id.BTDXWrapperConfig);
            String dxWrapperConfig = dxWrapperConfigButton != null && dxWrapperConfigButton.getTag() != null
                    ? dxWrapperConfigButton.getTag().toString() : "";
            boolean dxWrapperChanged = valuesDiffer(dxwrapper, comparisonContainer.getDXWrapper())
                    || valuesDiffer(dxWrapperConfig, comparisonContainer.getDXWrapperConfig());
            markIfChanged(dxWrapperLabel, dxWrapperChanged ? "1" : "", "");
        }

        markSpinnerIfChanged(contentView, R.id.SAudioDriver, comparisonContainer.getAudioDriver());
        markSpinnerValueIfChanged(contentView, R.id.SMIDISoundFont, comparisonContainer.getMIDISoundFont());
        markSpinnerIfChanged(contentView, R.id.SEmulator, comparisonContainer.getEmulator());
        markSpinnerIfChanged(contentView, R.id.SEmulator64, comparisonContainer.getEmulator64());
        markSpinnerValueIfChanged(contentView, R.id.SBox64Version, comparisonContainer.getBox64Version());
        markIfChanged(findLabelForView(contentView.findViewById(R.id.SBox64Preset), llContent),
                Box64PresetManager.getSpinnerSelectedId((Spinner) contentView.findViewById(R.id.SBox64Preset)),
                comparisonContainer.getBox64Preset());
        markSpinnerValueIfChanged(contentView, R.id.SFEXCoreVersion, comparisonContainer.getFEXCoreVersion());
        markIfChanged(findLabelForView(contentView.findViewById(R.id.SFEXCorePreset), llContent),
                FEXCorePresetManager.getSpinnerSelectedId((Spinner) contentView.findViewById(R.id.SFEXCorePreset)),
                comparisonContainer.getFEXCorePreset());
        markSpinnerPositionIfChanged(contentView, R.id.SStartupSelection, String.valueOf(comparisonContainer.getStartupSelection()));
        markIfChanged(findLabelForView(contentView.findViewById(R.id.ETlcall), llContent),
                ((EditText) contentView.findViewById(R.id.ETlcall)).getText().toString(),
                comparisonContainer.getLC_ALL());
        markIfChanged(findLabelForView(contentView.findViewById(R.id.EnvVarsView), llContent),
                ((EnvVarsView) contentView.findViewById(R.id.EnvVarsView)).getEnvVars(),
                comparisonContainer.getEnvVars());
        markIfChanged(findLabelForView(contentView.findViewById(R.id.LLDrives), llContent),
                getDrives(contentView),
                comparisonContainer.getDrives());
        markIfChanged(findLabelForView(contentView.findViewById(R.id.CPUListView), llContent),
                ((CPUListView) contentView.findViewById(R.id.CPUListView)).getCheckedCPUListAsString(),
                comparisonContainer.getCPUList(true));
        markIfChanged(findLabelForView(contentView.findViewById(R.id.CPUListViewWoW64), llContent),
                ((CPUListView) contentView.findViewById(R.id.CPUListViewWoW64)).getCheckedCPUListAsString(),
                comparisonContainer.getCPUListWoW64(true));
        markIfChanged(findLabelForView(contentView.findViewById(R.id.SDesktopTheme), llContent),
                getDesktopTheme(contentView),
                comparisonContainer.getDesktopTheme());
        markSpinnerPositionIfChanged(contentView, R.id.SMouseWarpOverride, getMouseWarpDefault(comparisonContainer));

        CompoundButton cbShowFPS = contentView.findViewById(R.id.CBShowFPS);
        if (cbShowFPS != null) {
            trackLabel((TextView) cbShowFPS);
            markIfChanged((TextView) cbShowFPS, cbShowFPS.isChecked() ? "1" : "0", comparisonContainer.isShowFPS() ? "1" : "0");
        }

        CompoundButton cbFullscreenStretched = contentView.findViewById(R.id.CBFullscreenStretched);
        if (cbFullscreenStretched != null) {
            trackLabel((TextView) cbFullscreenStretched);
            markIfChanged((TextView) cbFullscreenStretched, cbFullscreenStretched.isChecked() ? "1" : "0",
                    comparisonContainer.isFullscreenStretched() ? "1" : "0");
        }

        markInputIndicators(contentView, comparisonContainer);
        markSteamIndicators(contentView, comparisonContainer);
        refreshWinComponentsIndicators(contentView, comparisonContainer);
    }

    private void markInputIndicators(View contentView, Container comparisonContainer) {
        CompoundButton cbEnableXInput = contentView.findViewById(R.id.CBEnableXInput);
        CompoundButton cbEnableDInput = contentView.findViewById(R.id.CBEnableDInput);
        CompoundButton cbExclusiveInput = contentView.findViewById(R.id.CBExclusiveInput);
        Spinner sDInputType = contentView.findViewById(R.id.SDInputType);
        int containerInputType = comparisonContainer.getInputType();

        if (cbEnableXInput != null) {
            trackLabel((TextView) cbEnableXInput);
            markIfChanged((TextView) cbEnableXInput,
                    cbEnableXInput.isChecked() ? "1" : "0",
                    (containerInputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT ? "1" : "0");
        }
        if (cbEnableDInput != null) {
            trackLabel((TextView) cbEnableDInput);
            markIfChanged((TextView) cbEnableDInput,
                    cbEnableDInput.isChecked() ? "1" : "0",
                    (containerInputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT ? "1" : "0");
        }
        if (sDInputType != null) {
            ViewGroup llContent = contentView.findViewById(R.id.LLContent);
            markIfChanged(findLabelForView(sDInputType, llContent),
                    String.valueOf(sDInputType.getSelectedItemPosition()),
                    ((containerInputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? "0" : "1");
        }
        if (cbExclusiveInput != null) {
            trackLabel((TextView) cbExclusiveInput);
            markIfChanged((TextView) cbExclusiveInput, cbExclusiveInput.isChecked() ? "1" : "0", "0");
        }
    }

    private void markSteamIndicators(View contentView, Container comparisonContainer) {
        CompoundButton cbUseLegacyDRM = contentView.findViewById(R.id.CBUseLegacyDRM);
        CompoundButton cbLaunchRealSteam = contentView.findViewById(R.id.CBLaunchRealSteam);
        CompoundButton cbUseSteamInput = contentView.findViewById(R.id.CBUseSteamInput);
        Spinner sSteamType = contentView.findViewById(R.id.SSteamType);
        CompoundButton cbForceDlc = contentView.findViewById(R.id.CBForceDlc);
        CompoundButton cbSteamOfflineMode = contentView.findViewById(R.id.CBSteamOfflineMode);
        CompoundButton cbUnpackFiles = contentView.findViewById(R.id.CBUnpackFiles);

        if (cbUseLegacyDRM != null) {
            trackLabel((TextView) cbUseLegacyDRM);
            markIfChanged((TextView) cbUseLegacyDRM, cbUseLegacyDRM.isChecked() ? "1" : "0",
                    comparisonContainer.isUseLegacyDRM() ? "1" : "0");
        }
        if (cbLaunchRealSteam != null) {
            trackLabel((TextView) cbLaunchRealSteam);
            markIfChanged((TextView) cbLaunchRealSteam, cbLaunchRealSteam.isChecked() ? "1" : "0",
                    comparisonContainer.isLaunchRealSteam() ? "1" : "0");
        }
        if (cbUseSteamInput != null) {
            trackLabel((TextView) cbUseSteamInput);
            markIfChanged((TextView) cbUseSteamInput, cbUseSteamInput.isChecked() ? "1" : "0",
                    comparisonContainer.getExtra("useSteamInput", "0"));
        }
        if (sSteamType != null) {
            ViewGroup llContent = contentView.findViewById(R.id.LLContent);
            markIfChanged(findLabelForView(sSteamType, llContent), getSelectedSteamType(sSteamType), comparisonContainer.getSteamType());
        }
        if (cbForceDlc != null) {
            trackLabel((TextView) cbForceDlc);
            markIfChanged((TextView) cbForceDlc, cbForceDlc.isChecked() ? "1" : "0",
                    comparisonContainer.isForceDlc() ? "1" : "0");
        }
        if (cbSteamOfflineMode != null) {
            trackLabel((TextView) cbSteamOfflineMode);
            markIfChanged((TextView) cbSteamOfflineMode, cbSteamOfflineMode.isChecked() ? "1" : "0",
                    comparisonContainer.isSteamOfflineMode() ? "1" : "0");
        }
        if (cbUnpackFiles != null) {
            trackLabel((TextView) cbUnpackFiles);
            markIfChanged((TextView) cbUnpackFiles, cbUnpackFiles.isChecked() ? "1" : "0",
                    comparisonContainer.isUnpackFiles() ? "1" : "0");
        }
    }

    private void refreshWinComponentsIndicators(View contentView, Container comparisonContainer) {
        HashMap<String, String> containerDefaults = new HashMap<>();
        for (String[] wincomponent : new KeyValueSet(comparisonContainer.getWinComponents())) {
            containerDefaults.put(wincomponent[0], wincomponent[1]);
        }

        ViewGroup tabView = contentView.findViewById(R.id.LLTabWinComponents);
        if (tabView == null) return;

        ArrayList<View> spinnerViews = new ArrayList<>();
        AppUtils.findViewsWithClass(tabView, Spinner.class, spinnerViews);
        for (View spinnerView : spinnerViews) {
            Spinner spinner = (Spinner) spinnerView;
            TextView label = null;
            if (spinner.getParent() instanceof ViewGroup) {
                label = ((ViewGroup) spinner.getParent()).findViewById(R.id.TextView);
            }
            if (label == null) continue;
            trackLabel(label);
            String key = spinner.getTag() != null ? spinner.getTag().toString() : "";
            markIfChanged(label, String.valueOf(spinner.getSelectedItemPosition()), containerDefaults.get(key));
        }
    }

    private String getSelectedSteamType(Spinner spinner) {
        int selection = spinner != null ? spinner.getSelectedItemPosition() : 0;
        return selection == 2 ? Container.STEAM_TYPE_ULTRALIGHT
                : selection == 1 ? Container.STEAM_TYPE_LIGHT
                : Container.STEAM_TYPE_NORMAL;
    }

    private String getMouseWarpDefault(Container comparisonContainer) {
        File userRegFile = new File(comparisonContainer.getRootDir(), ".wine/user.reg");
        if (userRegFile.exists()) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                return registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable");
            }
        }
        return "disable";
    }

    private TextView findLabelForView(View target, ViewGroup root) {
        if (target == null || root == null || target.getParent() == null) return null;
        ViewGroup directParent = (ViewGroup) target.getParent();

        while (directParent != null && directParent.getParent() != root && directParent.getParent() instanceof ViewGroup) {
            target = directParent;
            directParent = (ViewGroup) directParent.getParent();
        }

        if (directParent == null) return null;

        ViewGroup containerParent = (ViewGroup) directParent.getParent();
        if (containerParent == null) containerParent = directParent;

        int idx = -1;
        for (int i = 0; i < containerParent.getChildCount(); i++) {
            if (containerParent.getChildAt(i) == target || containerParent.getChildAt(i) == directParent) {
                idx = i;
                break;
            }
        }

        for (int i = idx - 1; i >= 0; i--) {
            View child = containerParent.getChildAt(i);
            if (child instanceof TextView) return (TextView) child;
            if (child instanceof ViewGroup) {
                ArrayList<View> textViews = new ArrayList<>();
                AppUtils.findViewsWithClass((ViewGroup) child, TextView.class, textViews);
                if (!textViews.isEmpty()) return (TextView) textViews.get(textViews.size() - 1);
            }
        }
        return null;
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

        if ("C:\\Program Files (x86)\\Steam\\steamclient_loader_x64.exe".equalsIgnoreCase(shortcutPath)
                || "C:\\Program Files (x86)\\Steam\\steam.exe".equalsIgnoreCase(shortcutPath)) {
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

    private String buildExecCommandForSource(String gameSource, int appId, @Nullable String gogId, @Nullable Shortcut shortcut, @Nullable String selectedExePath, boolean launchRealSteam) {
        if ("STEAM".equals(gameSource)) {
            if (launchRealSteam) {
                return "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steam.exe\"";
            }
            return "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\"";
        }

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
        String execCommand = buildExecCommandForSource(
                createShortcutForSource,
                createShortcutForAppId,
                createShortcutForGogId,
                null,
                data.optString("launchExePath", ""),
                "1".equals(data.optString("launchRealSteam", "0"))
        );
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
        boolean[] hasOverrides = {false};

        appendShortcutOverrideIfNeeded(content, container, "screenSize", data.opt("screenSize"), container.getScreenSize(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "envVars", data.opt("envVars"), container.getEnvVars(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "cpuList", data.opt("cpuList"), container.getCPUList(true), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "cpuListWoW64", data.opt("cpuListWoW64"), container.getCPUListWoW64(true), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "graphicsDriver", data.opt("graphicsDriver"), container.getGraphicsDriver(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "graphicsDriverConfig", data.opt("graphicsDriverConfig"), container.getGraphicsDriverConfig(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "dxwrapper", data.opt("dxwrapper"), container.getDXWrapper(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "dxwrapperConfig", data.opt("dxwrapperConfig"), container.getDXWrapperConfig(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "audioDriver", data.opt("audioDriver"), container.getAudioDriver(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "emulator", data.opt("emulator"), container.getEmulator(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "emulator64", data.opt("emulator64"), container.getEmulator64(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "wincomponents", data.opt("wincomponents"), container.getWinComponents(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "drives", data.opt("drives"), container.getDrives(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "showFPS", data.opt("showFPS"), container.isShowFPS(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "fullscreenStretched", data.opt("fullscreenStretched"), container.isFullscreenStretched(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "inputType", data.opt("inputType"), container.getInputType(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "disableXinput", data.opt("disableXinput"), "", hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "startupSelection", data.opt("startupSelection"), container.getStartupSelection(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "box64Version", data.opt("box64Version"), container.getBox64Version(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "box64Preset", data.opt("box64Preset"), container.getBox64Preset(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "fexcoreVersion", data.opt("fexcoreVersion"), container.getFEXCoreVersion(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "fexcorePreset", data.opt("fexcorePreset"), container.getFEXCorePreset(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "desktopTheme", data.opt("desktopTheme"), container.getDesktopTheme(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "midiSoundFont", data.opt("midiSoundFont"), container.getMIDISoundFont(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "lc_all", data.opt("lc_all"), container.getLC_ALL(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "useLegacyDRM", data.opt("useLegacyDRM"), container.isUseLegacyDRM(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "launchRealSteam", data.opt("launchRealSteam"), container.isLaunchRealSteam(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "useSteamInput", data.opt("useSteamInput"), container.getExtra("useSteamInput", "0"), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "steamType", data.opt("steamType"), container.getSteamType(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "forceDlc", data.opt("forceDlc"), container.isForceDlc(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "steamOfflineMode", data.opt("steamOfflineMode"), container.isSteamOfflineMode(), hasOverrides);
        appendShortcutOverrideIfNeeded(content, container, "unpackFiles", data.opt("unpackFiles"), container.isUnpackFiles(), hasOverrides);

        java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (Arrays.asList(SHORTCUT_SETTING_OVERRIDE_KEYS).contains(key) || key.equals("name") || key.equals("wineVersion")) {
                continue;
            }
            appendShortcutExtra(content, key, normalizeShortcutValue(data.opt(key)));
        }

        appendShortcutExtra(content, EXTRA_USE_CONTAINER_DEFAULTS, hasOverrides[0] ? "0" : "1");

        FileUtils.writeString(shortcutFile, content.toString());
    }
}


