package com.winlator.cmod.contentdialog;



import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.cmod.ContainerDetailFragment;
import com.winlator.cmod.R;
import com.winlator.cmod.ShortcutsFragment;
import com.winlator.cmod.box64.Box64PresetManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.RefreshRateUtils;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.fexcore.FEXCoreManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.widget.ChasingBorderDrawable;
import com.winlator.cmod.widget.EnvVarsView;
import com.winlator.cmod.winhandler.WinHandler;

import com.winlator.cmod.OtherSettingsFragment;
import android.widget.Button;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ShortcutSettingsDialog extends ContentDialog {
    private static final String EXTRA_USE_CONTAINER_DEFAULTS = "use_container_defaults";
    private final ShortcutsFragment fragment;
    private final Shortcut shortcut;
    private InputControlsManager inputControlsManager;
    private TextView tvGraphicsDriverVersion;
    private String box64Version;
    private int currentSectionIndex = 0;
    private View[] sidebarButtons;
    private View[] sectionViews;

    // Map of setting label TextViews to their original (unmarked) text, for * indicator tracking
    private final HashMap<TextView, String> labelOriginalText = new HashMap<>();


    public ShortcutSettingsDialog(ShortcutsFragment fragment, Shortcut shortcut) {
        super(fragment.getContext(), R.layout.shortcut_settings_dialog);
        this.fragment = fragment;
        this.shortcut = shortcut;
        setTitle(shortcut.name);
        setIcon(R.drawable.icon_settings);

        // Initialize the ContentsManager
        ContainerManager containerManager = shortcut.container.getManager();

//        if (containerManager != null) {
//            this.contentsManager = new ContentsManager(containerManager.getContext());
//            this.contentsManager.syncTurnipContents();
//        } else {
//            Toast.makeText(fragment.getContext(), "Failed to initialize container manager. Please try again.", Toast.LENGTH_SHORT).show();
//            return;
//        }

        createContentView();
    }

    /**
     * Register a label TextView for change-indicator tracking.
     * Stores the original text so we can append/remove '*' later.
     */
    private void trackLabel(TextView label) {
        if (label != null && !labelOriginalText.containsKey(label)) {
            labelOriginalText.put(label, label.getText().toString());
        }
    }

    /**
     * Mark or unmark a label with '*' depending on whether the current value
     * differs from the container default.
     */
    private void markIfChanged(TextView label, String currentValue, String containerDefault) {
        if (label == null) return;
        String originalText = labelOriginalText.get(label);
        if (originalText == null) {
            originalText = label.getText().toString().replaceFirst("^\\*\\s*", "");
            labelOriginalText.put(label, originalText);
        }
        label.setText(valuesDiffer(currentValue, containerDefault) ? "* " + originalText : originalText);
    }

    private void markSpinnerIfChanged(Spinner spinner, TextView label, String containerDefault) {
        if (spinner == null || label == null) return;
        String current = spinner.getSelectedItem() != null ? StringUtils.parseIdentifier(spinner.getSelectedItem()) : "";
        markIfChanged(label, current, containerDefault);
    }

    private void markSpinnerValueIfChanged(Spinner spinner, TextView label, String containerDefault) {
        if (spinner == null || label == null) return;
        String current = spinner.getSelectedItem() != null ? spinner.getSelectedItem().toString() : "";
        markIfChanged(label, current, containerDefault);
    }

    private void markSpinnerPositionIfChanged(Spinner spinner, TextView label, String containerDefault) {
        if (spinner == null || label == null) return;
        markIfChanged(label, String.valueOf(Math.max(spinner.getSelectedItemPosition(), 0)), containerDefault);
    }

    private void createContentView() {
        final Context context = fragment.getContext();
        inputControlsManager = new InputControlsManager(context);

        // Size the dialog for the two-panel layout
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int dialogWidth = (int)(dm.widthPixels * 0.85f);
        int dialogHeight = (int)(dm.heightPixels * 0.85f);
        View rootLayout = findViewById(R.id.LLShortcutSettingsRoot);
        if (rootLayout != null) {
            ViewGroup.LayoutParams rootLp = rootLayout.getLayoutParams();
            rootLp.width = dialogWidth;
            rootLp.height = dialogHeight;
            rootLayout.setLayoutParams(rootLp);
        }

        // Hide the ContentDialog bottom bar since we have sidebar confirm
        View bottomBar = getContentView().findViewById(R.id.LLBottomBar);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);

        // Setup the sidebar navigation
        setupSidebarNavigation();

        applyDynamicStyles(findViewById(R.id.LLContent));

        // Initialize the turnip version TextView
        tvGraphicsDriverVersion = findViewById(R.id.TVGraphicsDriverVersion);

        final Container container = shortcut.container;

        final EditText etName = findViewById(R.id.ETName);
        etName.setText(shortcut.name);

        final Runnable[] refreshIndicatorsRef = new Runnable[1];

        findViewById(R.id.BTAddToHomeScreen).setOnClickListener((v) -> {
            boolean requested = fragment.addShortcutToScreen(shortcut);
            if (!requested)
                Toast.makeText(context, context.getString(R.string.library_games_failed_to_create_shortcut, shortcut.name), Toast.LENGTH_SHORT).show();
        });

        final EditText etExecArgs = findViewById(R.id.ETExecArgs);
        etExecArgs.setText(shortcut.getExtra("execArgs"));

        ContainerDetailFragment containerDetailFragment = new ContainerDetailFragment(shortcut.container.id);

        loadScreenSizeSpinner(getContentView(),
                getShortcutSettingValue("screenSize", container.getScreenSize()),
                () -> runIndicatorRefresh(refreshIndicatorsRef));


        final Spinner sGraphicsDriver = findViewById(R.id.SGraphicsDriver);
        
        final Spinner sDXWrapper = findViewById(R.id.SDXWrapper);

        final Spinner sBox64Version = findViewById(R.id.SBox64Version);
        
        final ContentsManager contentsManager = new ContentsManager(context);

        final View vGraphicsDriverConfig = findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(getShortcutSettingValue("graphicsDriverConfig", container.getGraphicsDriverConfig()));

        final View vDXWrapperConfig = findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(getShortcutSettingValue("dxwrapperConfig", container.getDXWrapperConfig()));

        findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.container_wine_dxwrapper_help_content));

        final Spinner sAudioDriver = findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, getShortcutSettingValue("audioDriver", container.getAudioDriver()));
        final Spinner sEmulator = findViewById(R.id.SEmulator);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, getShortcutSettingValue("emulator", container.getEmulator()));
        final Spinner sEmulator64 = findViewById(R.id.SEmulator64);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, getShortcutSettingValue("emulator64", container.getEmulator64()));

        final View box64Frame = findViewById(R.id.box64Frame);
        final View fexcoreFrame = findViewById(R.id.fexcoreFrame);

        final Spinner sFEXCoreVersion = findViewById(R.id.SFEXCoreVersion);
        final Spinner sFEXCorePreset = findViewById(R.id.SFEXCorePreset);
        final Spinner sBox64Preset = findViewById(R.id.SBox64Preset);

        // Sync contents off the main thread, then populate dependent spinners on UI thread
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            contentsManager.syncContents();
            sGraphicsDriver.post(() -> {
                populateContentsSpinners(context, contentsManager,
                    sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig, vDXWrapperConfig,
                    sBox64Version, sEmulator, sEmulator64, sFEXCoreVersion,
                    () -> runIndicatorRefresh(refreshIndicatorsRef));
                setupChangeIndicators(container, refreshIndicatorsRef);
            });
        });

        AdapterView.OnItemSelectedListener emulatorListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String emulator32 = sEmulator.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator.getSelectedItem()) : "";
                String emulator64Str = sEmulator64.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator64.getSelectedItem()) : "";

                boolean useBox64 = emulator32.equalsIgnoreCase("box64") || emulator64Str.equalsIgnoreCase("box64");
                boolean useFexcore = emulator32.equalsIgnoreCase("fexcore") || emulator64Str.equalsIgnoreCase("fexcore");

                box64Frame.setVisibility(useBox64 ? View.VISIBLE : View.GONE);
                fexcoreFrame.setVisibility(useFexcore ? View.VISIBLE : View.GONE);
                runIndicatorRefresh(refreshIndicatorsRef);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        sEmulator.setOnItemSelectedListener(emulatorListener);
        sEmulator64.setOnItemSelectedListener(emulatorListener);

        final Spinner sMIDISoundFont = findViewById(R.id.SMIDISoundFont);
        MidiManager.loadSFSpinner(sMIDISoundFont);
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, getShortcutSettingValue("midiSoundFont", container.getMIDISoundFont()));

        // Per-game Refresh Rate spinner
        final Spinner sRefreshRate = findViewById(R.id.SRefreshRate);
        loadShortcutRefreshRateSpinner(sRefreshRate, shortcut.getExtra("refreshRate", "0"));

        final EditText etLC_ALL = findViewById(R.id.ETlcall);
        etLC_ALL.setText(getShortcutSettingValue("lc_all", container.getLC_ALL()));

        final View btShowLCALL = findViewById(R.id.BTShowLCALL);
        btShowLCALL.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(context, v);
            String[] lcs = context.getResources().getStringArray(R.array.some_lc_all);
            for (int i = 0; i < lcs.length; i++)
                popupMenu.getMenu().add(Menu.NONE, i, Menu.NONE, lcs[i]);
            popupMenu.setOnMenuItemClickListener(item -> {
                etLC_ALL.setText(item.toString() + ".UTF-8");
                return true;
            });
            popupMenu.show();
        });

        // Non-contentsManager-dependent UI setup (runs immediately)
        final CheckBox cbFullscreenStretched =  findViewById(R.id.CBFullscreenStretched);
        boolean fullscreenStretched = getShortcutSettingValue("fullscreenStretched", container.isFullscreenStretched() ? "1" : "0").equals("1");
        cbFullscreenStretched.setChecked(fullscreenStretched);

        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.container_config_xinput_dinput_warning, null);
        final CheckBox cbEnableXInput = findViewById(R.id.CBEnableXInput);
        final CheckBox cbEnableDInput = findViewById(R.id.CBEnableDInput);
        final View llDInputType = findViewById(R.id.LLDinputMapperType);
        final View btHelpXInput = findViewById(R.id.BTXInputHelp);
        final View btHelpDInput = findViewById(R.id.BTDInputHelp);
        Spinner SDInputType = findViewById(R.id.SDInputType);
        int inputType = Integer.parseInt(getShortcutSettingValue("inputType", String.valueOf(container.getInputType())));

        cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
        cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);
        cbEnableDInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llDInputType.setVisibility(isChecked?View.VISIBLE:View.GONE);
            if (isChecked && cbEnableXInput.isChecked())
                showInputWarning.run();
        });
        cbEnableXInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && cbEnableDInput.isChecked())
                showInputWarning.run();
        });
        btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.container_config_help_xinput));
        btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.container_config_help_dinput));
        SDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
        llDInputType.setVisibility(cbEnableDInput.isChecked()?View.VISIBLE:View.GONE);

        Box64PresetManager.loadSpinner("box64", sBox64Preset, getShortcutSettingValue("box64Preset", container.getBox64Preset()));

        FEXCorePresetManager.loadSpinner(sFEXCorePreset, getShortcutSettingValue("fexcorePreset", container.getFEXCorePreset()));

        final Spinner sControlsProfile = findViewById(R.id.SControlsProfile);
        loadControlsProfileSpinner(sControlsProfile, shortcut.getExtra("controlsProfile", "0"));

        final CheckBox cbDisabledXInput = findViewById(R.id.CBDisabledXInput);
        // Set the initial value based on the shortcut extras
        boolean isXInputDisabled = shortcut.getExtra("disableXinput", "0").equals("1");
        cbDisabledXInput.setChecked(isXInputDisabled);

        final CheckBox cbSimTouchScreen = findViewById(R.id.CBTouchscreenMode);
        String isTouchScreenMode = shortcut.getExtra("simTouchScreen");
        cbSimTouchScreen.setChecked(isTouchScreenMode.equals("1") ? true : false);

        ContainerDetailFragment.createWinComponentsTabFromShortcut(this, getContentView(),
                getShortcutSettingValue("wincomponents", container.getWinComponents()));

        final EnvVarsView envVarsView = createEnvVarsTab();

        // Sections are now handled by sidebar navigation (no TabLayout)
        // Make tab content views visible since they're inside their own sections
        View llTabWinComponents = findViewById(R.id.LLTabWinComponents);
        if (llTabWinComponents != null) llTabWinComponents.setVisibility(View.VISIBLE);
        View llTabEnvVars = findViewById(R.id.LLTabEnvVars);
        if (llTabEnvVars != null) llTabEnvVars.setVisibility(View.VISIBLE);
        View llTabAdvanced = findViewById(R.id.LLTabAdvanced);
        if (llTabAdvanced != null) llTabAdvanced.setVisibility(View.VISIBLE);

        final Spinner sStartupSelection = findViewById(R.id.SStartupSelection);
        sStartupSelection.setSelection(Integer.parseInt(getShortcutSettingValue("startupSelection", String.valueOf(container.getStartupSelection()))));

        findViewById(R.id.BTExtraArgsMenu).setOnClickListener((v) -> {
            PopupMenu popupMenu = new PopupMenu(context, v);
            popupMenu.inflate(R.menu.extra_args_popup_menu);
            popupMenu.setOnMenuItemClickListener((menuItem) -> {
                String value = String.valueOf(menuItem.getTitle());
                String execArgs = etExecArgs.getText().toString();
                if (!execArgs.contains(value)) etExecArgs.setText(!execArgs.isEmpty() ? execArgs + " " + value : value);
                return true;
            });
            popupMenu.show();
        });



        final Spinner sSharpnessEffect = findViewById(R.id.SSharpnessEffect);
        final SeekBar sbSharpnessLevel = findViewById(R.id.SBSharpnessLevel);
        final SeekBar sbSharpnessDenoise = findViewById(R.id.SBSharpnessDenoise);
        final TextView tvSharpnessLevel = findViewById(R.id.TVSharpnessLevel);
        final TextView tvSharpnessDenoise = findViewById(R.id.TVSharpnessDenoise);

        AppUtils.setSpinnerSelectionFromValue(sSharpnessEffect, shortcut.getExtra("sharpnessEffect", "None"));

        sbSharpnessLevel.setProgress(Integer.parseInt(shortcut.getExtra("sharpnessLevel", "100")));
        tvSharpnessLevel.setText(shortcut.getExtra("sharpnessLevel", "100") + "%");
        sbSharpnessLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSharpnessLevel.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        sbSharpnessDenoise.setProgress(Integer.parseInt(shortcut.getExtra("sharpnessDenoise", "100")));
        tvSharpnessDenoise.setText(shortcut.getExtra("sharpnessDenoise", "100") + "%");
        sbSharpnessDenoise.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSharpnessDenoise.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final CPUListView cpuListView = findViewById(R.id.CPUListView);
        cpuListView.setCheckedCPUList(getShortcutSettingValue("cpuList", shortcut.container.getCPUList(true)));

        final Runnable refreshIndicators = () -> refreshChangeIndicators(container);
        refreshIndicatorsRef[0] = refreshIndicators;
        getContentView().post(refreshIndicators);

        setOnConfirmCallback(() -> {
            String name = etName.getText().toString().trim();
            boolean nameChanged = !shortcut.name.equals(name) && !name.isEmpty();

            // First, handle renaming if the name has changed
            if (nameChanged) {
                renameShortcut(name);
            }


            // Determine if renaming is needed
            boolean renamingSuccess = !nameChanged || new File(shortcut.file.getParent(), name + ".desktop").exists();

            if (renamingSuccess) {
                String graphicsDriver = sGraphicsDriver.getSelectedItem() != null ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()) : "";
                String graphicsDriverConfig = vGraphicsDriverConfig.getTag() != null ? vGraphicsDriverConfig.getTag().toString() : "";
                String dxwrapper = sDXWrapper.getSelectedItem() != null ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()) : "";
                String dxwrapperConfig = vDXWrapperConfig.getTag() != null ? vDXWrapperConfig.getTag().toString() : "";
                String audioDriver = sAudioDriver.getSelectedItem() != null ? StringUtils.parseIdentifier(sAudioDriver.getSelectedItem()) : "";
                String emulator = sEmulator.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator.getSelectedItem()) : "";
                String emulator64 = sEmulator64.getSelectedItem() != null ? StringUtils.parseIdentifier(sEmulator64.getSelectedItem()) : "";
                String lc_all = etLC_ALL.getText().toString();
                String midiSoundFont = sMIDISoundFont.getSelectedItemPosition() == 0 ? "" : sMIDISoundFont.getSelectedItem().toString();
                String screenSize = containerDetailFragment.getScreenSize(getContentView());

                int finalInputType = 0;
                finalInputType |= cbEnableXInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
                finalInputType |= cbEnableDInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
                finalInputType |= SDInputType.getSelectedItemPosition() == 0 ?  WinHandler.FLAG_DINPUT_MAPPER_STANDARD : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;

                boolean disabledXInput = cbDisabledXInput.isChecked();
                shortcut.putExtra("disableXinput", disabledXInput ? "1" : null);

                boolean touchscreenMode = cbSimTouchScreen.isChecked();
                shortcut.putExtra("simTouchScreen", touchscreenMode ? "1" : "0");

                String execArgs = etExecArgs.getText().toString();
                shortcut.putExtra("execArgs", !execArgs.isEmpty() ? execArgs : null);
                boolean hasContainerOverride = false;
                hasContainerOverride |= saveContainerOverride("screenSize", screenSize, container.getScreenSize());
                hasContainerOverride |= saveContainerOverride("graphicsDriver", graphicsDriver, container.getGraphicsDriver());
                hasContainerOverride |= saveContainerOverride("graphicsDriverConfig", graphicsDriverConfig, container.getGraphicsDriverConfig());
                hasContainerOverride |= saveContainerOverride("dxwrapper", dxwrapper, container.getDXWrapper());
                hasContainerOverride |= saveContainerOverride("dxwrapperConfig", dxwrapperConfig, container.getDXWrapperConfig());
                hasContainerOverride |= saveContainerOverride("audioDriver", audioDriver, container.getAudioDriver());
                hasContainerOverride |= saveContainerOverride("emulator", emulator, container.getEmulator());
                hasContainerOverride |= saveContainerOverride("emulator64", emulator64, container.getEmulator64());
                hasContainerOverride |= saveContainerOverride("midiSoundFont", midiSoundFont, container.getMIDISoundFont());
                hasContainerOverride |= saveContainerOverride("lc_all", lc_all, container.getLC_ALL());

                hasContainerOverride |= saveContainerOverride("fullscreenStretched",
                        cbFullscreenStretched.isChecked() ? "1" : "0",
                        container.isFullscreenStretched() ? "1" : "0");

                String wincomponents = containerDetailFragment.getWinComponents(getContentView());
                hasContainerOverride |= saveContainerOverride("wincomponents", wincomponents, container.getWinComponents());

                String envVars = envVarsView.getEnvVars();
                hasContainerOverride |= saveContainerOverride("envVars", envVars, container.getEnvVars());

                String fexcoreVersion = sFEXCoreVersion.getSelectedItem() != null ? sFEXCoreVersion.getSelectedItem().toString() : "";
                hasContainerOverride |= saveContainerOverride("fexcoreVersion", fexcoreVersion, container.getFEXCoreVersion());

                String fexcorePreset = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
                hasContainerOverride |= saveContainerOverride("fexcorePreset", fexcorePreset, container.getFEXCorePreset());

                String selectedBox64Version = sBox64Version.getSelectedItem() != null
                        ? sBox64Version.getSelectedItem().toString() : "";
                hasContainerOverride |= saveContainerOverride("box64Version", selectedBox64Version, container.getBox64Version());

                String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
                hasContainerOverride |= saveContainerOverride("box64Preset", box64Preset, container.getBox64Preset());

                byte startupSelection = (byte)sStartupSelection.getSelectedItemPosition();
                hasContainerOverride |= saveContainerOverride("startupSelection",
                        String.valueOf(startupSelection),
                        String.valueOf(container.getStartupSelection()));

                String sharpeningEffect = sSharpnessEffect.getSelectedItem().toString();
                String sharpeningLevel = String.valueOf(sbSharpnessLevel.getProgress());
                String sharpeningDenoise = String.valueOf(sbSharpnessDenoise.getProgress());
                shortcut.putExtra("sharpnessEffect", sharpeningEffect);
                shortcut.putExtra("sharpnessLevel", sharpeningLevel);
                shortcut.putExtra("sharpnessDenoise", sharpeningDenoise);

                ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
                int controlsProfile = sControlsProfile.getSelectedItemPosition() > 0 ? profiles.get(sControlsProfile.getSelectedItemPosition() - 1).id : 0;
                shortcut.putExtra("controlsProfile", controlsProfile > 0 ? String.valueOf(controlsProfile) : null);

                String cpuList = cpuListView.getCheckedCPUListAsString();
                hasContainerOverride |= saveContainerOverride("cpuList", cpuList, container.getCPUList(true));

                hasContainerOverride |= saveContainerOverride("inputType",
                        String.valueOf(finalInputType),
                        String.valueOf(container.getInputType()));
                shortcut.putExtra(EXTRA_USE_CONTAINER_DEFAULTS, hasContainerOverride ? "0" : "1");

                // Save per-game refresh rate
                if (sRefreshRate.getSelectedItem() != null) {
                    int selectedRate = RefreshRateUtils.parseRefreshRateLabel(sRefreshRate.getSelectedItem().toString());
                    if (selectedRate <= 0) {
                        shortcut.putExtra("refreshRate", null); // Remove override, use global
                    } else {
                        shortcut.putExtra("refreshRate", String.valueOf(selectedRate));
                    }
                }

                // Save all changes to the shortcut
                shortcut.saveData();
            }
        });

        // Wire up the sidebar confirm button
        View sidebarConfirm = findViewById(R.id.BTSidebarConfirm);
        if (sidebarConfirm != null) {
            sidebarConfirm.setOnClickListener(v -> {
                if (onConfirmCallback != null) onConfirmCallback.run();
                dismiss();
            });
        }
    }

    /**
     * Sets up the two-panel sidebar navigation with section toggling
     * and ChasingBorderDrawable selection highlighting.
     */
    private void setupSidebarNavigation() {
        // Sidebar button IDs (left panel)
        int[] sidebarButtonIds = {
            R.id.BTSectionGeneral,
            R.id.BTSectionDisplay,
            R.id.BTSectionAudio,
            R.id.BTSectionWine,
            R.id.BTSectionComponents,
            R.id.BTSectionVariables,
            R.id.BTSectionInput,
            R.id.BTSectionAdvanced
        };

        // Content section IDs (right panel)
        int[] sectionIds = {
            R.id.LLSectionGeneral,
            R.id.LLSectionDisplay,
            R.id.LLSectionAudio,
            R.id.LLSectionWine,
            R.id.LLSectionComponents,
            R.id.LLSectionVariables,
            R.id.LLSectionInput,
            R.id.LLSectionAdvanced
        };

        sidebarButtons = new View[sidebarButtonIds.length];
        sectionViews = new View[sectionIds.length];

        for (int i = 0; i < sidebarButtonIds.length; i++) {
            sidebarButtons[i] = findViewById(sidebarButtonIds[i]);
            sectionViews[i] = findViewById(sectionIds[i]);
        }

        // Set click listeners on each sidebar button
        for (int i = 0; i < sidebarButtons.length; i++) {
            final int index = i;
            if (sidebarButtons[i] != null) {
                sidebarButtons[i].setOnClickListener(v -> showSection(index));
            }
        }

        // Select the first section by default
        showSection(0);
    }

    /**
     * Shows the section at the given index, hides all others,
     * and applies the ChasingBorderDrawable to the selected sidebar button.
     */
    private void showSection(int index) {
        if (index < 0 || index >= sectionViews.length) return;
        Context context = fragment.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        currentSectionIndex = index;

        // Toggle section visibility
        for (int i = 0; i < sectionViews.length; i++) {
            if (sectionViews[i] != null) {
                sectionViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            }
        }

        // Update sidebar button highlighting
        for (int i = 0; i < sidebarButtons.length; i++) {
            View btn = sidebarButtons[i];
            if (btn == null) continue;

            if (i == index) {
                // Apply ChasingBorderDrawable to selected button (same as main settings nav)
                ChasingBorderDrawable border = new ChasingBorderDrawable(8f, 1.5f, density);
                btn.setBackground(border);
                border.setVisible(true, true);

                // Set text + icon to white
                if (btn instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) btn;
                    for (int c = 0; c < vg.getChildCount(); c++) {
                        View child = vg.getChildAt(c);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(0xFFFFFFFF);
                        } else if (child instanceof ImageView) {
                            ((ImageView) child).setColorFilter(0xFFFFFFFF);
                        }
                    }
                }
            } else {
                // Reset to inactive state
                btn.setBackground(null);

                if (btn instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) btn;
                    for (int c = 0; c < vg.getChildCount(); c++) {
                        View child = vg.getChildAt(c);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(0xFFB0BEC5);
                        } else if (child instanceof ImageView) {
                            ((ImageView) child).setColorFilter(0xFFB0BEC5);
                        }
                    }
                }
            }
        }

        // Scroll the right content area to the top when switching sections
        View scrollView = findViewById(R.id.SVContent);
        if (scrollView instanceof android.widget.ScrollView) {
            ((android.widget.ScrollView) scrollView).scrollTo(0, 0);
        }
    }

    // Utility method to apply styles to dynamically added TextViews based on their content
    private void applyFieldSetLabelStylesDynamically(ViewGroup rootView) {
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View child = rootView.getChildAt(i);
            if (child instanceof ViewGroup) {
                applyFieldSetLabelStylesDynamically((ViewGroup) child);
            } else if (child instanceof TextView) {
                TextView textView = (TextView) child;
                if (isFieldSetLabel(textView.getText().toString())) {
                    applyFieldSetLabelStyle(textView);
                }
            }
        }
    }

    // Method to check if the text content matches any fieldset label
    private boolean isFieldSetLabel(String text) {
        return text.equalsIgnoreCase("DirectX") ||
                text.equalsIgnoreCase("General") ||
                text.equalsIgnoreCase("Box64") ||
                text.equalsIgnoreCase("Input Controls") ||
                text.equalsIgnoreCase("Game Controller") ||
                text.equalsIgnoreCase("System");
    }

    public void onWinComponentsViewsAdded() {
        ViewGroup llContent = findViewById(R.id.LLContent);
        applyFieldSetLabelStylesDynamically(llContent);
    }


    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        loadScreenSizeSpinner(view, selectedValue, null);
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue, Runnable onChanged) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);

        applyDarkThemeToEditText(view.findViewById(R.id.ETScreenWidth));
        applyDarkThemeToEditText(view.findViewById(R.id.ETScreenHeight));


        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
                if (onChanged != null) onChanged.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            AppUtils.setSpinnerSelectionFromValue(sScreenSize, "custom");
            String[] screenSize = selectedValue.split("x");
            ((EditText)view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText)view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    private void applyDynamicStyles(View view) {

        // Update edit text
        EditText etName = view.findViewById(R.id.ETName);
        applyDarkThemeToEditText(etName);

        // Update Spinners
        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        Spinner sEmulatorSpinner = view.findViewById(R.id.SEmulator);
        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Spinner sControlsProfile = view.findViewById(R.id.SControlsProfile);
        Spinner sDInputType = view.findViewById(R.id.SDInputType);
        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        Spinner sStartupSelection = findViewById(R.id.SStartupSelection);
        Spinner sRefreshRate = view.findViewById(R.id.SRefreshRate);


        // Set dark mode background for spinners
        sGraphicsDriver.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sDXWrapper.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sAudioDriver.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sEmulatorSpinner.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sBox64Preset.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sControlsProfile.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sDInputType.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sMIDISoundFont.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sBox64Version.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sFEXCorePreset.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sFEXCoreVersion.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        sStartupSelection.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);
        if (sRefreshRate != null)
            sRefreshRate.setPopupBackgroundResource(R.drawable.content_dialog_background_dark);

//        EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        EditText etExecArgs = view.findViewById(R.id.ETExecArgs);

//        applyDarkThemeToEditText(etLC_ALL);
        applyDarkThemeToEditText(etExecArgs);

    }

    private void applyFieldSetLabelStyle(TextView textView) {
        textView.setTextColor(Color.parseColor("#cccccc"));
        textView.setBackgroundColor(Color.parseColor("#424242"));
    }

    private static void applyDarkThemeToEditText(EditText editText) {
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setBackgroundResource(R.drawable.edit_text_dark);
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value;
    }

    private String getShortcutSettingValue(String key, String containerValue) {
        return shortcut != null ? shortcut.getSettingExtra(key, containerValue) : containerValue;
    }

    private static boolean valuesDiffer(String currentValue, String containerDefault) {
        return !normalizeValue(currentValue).equals(normalizeValue(containerDefault));
    }

    private void runIndicatorRefresh(Runnable[] refreshIndicatorsRef) {
        if (refreshIndicatorsRef != null && refreshIndicatorsRef.length > 0 && refreshIndicatorsRef[0] != null) {
            refreshIndicatorsRef[0].run();
        }
    }

    private boolean saveContainerOverride(String extraName, String newValue, String containerValue) {
        if (valuesDiffer(newValue, containerValue)) {
            shortcut.putExtra(extraName, normalizeValue(newValue));
            return true;
        }
        shortcut.putExtra(extraName, null);
        return false;
    }

    private void updateExtra(String extraName, String containerValue, String newValue) {
        String extraValue = shortcut.getExtra(extraName);
        if (extraValue.isEmpty() && containerValue.equals(newValue))
            return;
        shortcut.putExtra(extraName, newValue);
    }

    private void renameShortcut(String newName) {
        File parent = shortcut.file.getParentFile();
        File oldDesktopFile = shortcut.file; // Reference to the old file
        File newDesktopFile = new File(parent, newName + ".desktop");

        // Rename the desktop file if the new one doesn't exist
        if (!newDesktopFile.isFile() && oldDesktopFile.renameTo(newDesktopFile)) {
            // Successfully renamed, update the shortcut's file reference
            updateShortcutFileReference(newDesktopFile); // New helper method

            // As a precaution, delete any remaining old file
            deleteOldFileIfExists(oldDesktopFile);
        }

        // Rename link file if applicable
        File linkFile = new File(parent, shortcut.name + ".lnk");
        if (linkFile.isFile()) {
            File newLinkFile = new File(parent, newName + ".lnk");
            if (!newLinkFile.isFile()) linkFile.renameTo(newLinkFile);
        }

        fragment.loadShortcutsList();
        fragment.updateShortcutOnScreen(newName, newName, shortcut.container.id, newDesktopFile.getAbsolutePath(),
                Icon.createWithBitmap(shortcut.icon), shortcut.getExtra("uuid"));
    }

    // Method to ensure no old file remains
    private void deleteOldFileIfExists(File oldFile) {
        if (oldFile.exists()) {
            if (!oldFile.delete()) {
                Log.e("ShortcutSettingsDialog", "Failed to delete old file: " + oldFile.getPath());
            }
        }
    }

    // Update the shortcut's file reference to ensure saveData() writes to the correct file
    private void updateShortcutFileReference(File newFile) {
        try {
            Field fileField = Shortcut.class.getDeclaredField("file");
            fileField.setAccessible(true);
            fileField.set(shortcut, newFile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e("ShortcutSettingsDialog", "Error updating shortcut file reference", e);
        }
    }


    private EnvVarsView createEnvVarsTab() {
        final View view = getContentView();
        final Context context = view.getContext();

        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        String envVarsValue = getShortcutSettingValue(
                "envVars",
                shortcut.container != null ? shortcut.container.getEnvVars() : Container.DEFAULT_ENV_VARS
        );
        envVarsView.setEnvVars(new EnvVars(envVarsValue));

        // Set the click listener for adding new environment variables
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) ->
                new AddEnvVarDialog(context, envVarsView).show()
        );

        return envVarsView;
    }

    private void loadControlsProfileSpinner(Spinner spinner, String selectedValue) {
        final Context context = fragment.getContext();
        final ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        ArrayList<String> values = new ArrayList<>();
        values.add(context.getString(R.string.common_ui_none));

        int selectedPosition = 0;
        int selectedId = Integer.parseInt(selectedValue);
        for (int i = 0; i < profiles.size(); i++) {
            ControlsProfile profile = profiles.get(i);
            if (profile.id == selectedId) selectedPosition = i + 1;
            values.add(profile.getName());
        }

        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(selectedPosition, false);
    }

    private void showInputWarning() {
        final Context context = fragment.getContext();
        ContentDialog.alert(context, R.string.container_config_xinput_dinput_warning, null);
    }

    /**
     * Populate the per-game refresh rate spinner.
     * First entry is "Default (Global)" which means use the global setting.
     */
    private void loadShortcutRefreshRateSpinner(Spinner spinner, String savedValue) {
        if (fragment.getActivity() == null) return;

        List<String> entries = OtherSettingsFragment.buildRefreshRateEntries(fragment.getActivity());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(fragment.getContext(), R.layout.spinner_item_themed, entries);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_themed);
        spinner.setAdapter(adapter);

        // Select saved value
        if (savedValue == null || savedValue.isEmpty() || savedValue.equals("0")) {
            spinner.setSelection(0); // Default (Global)
        } else {
            String target = savedValue + " Hz";
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).equals(target)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    public static void loadBox64VersionSpinner(Context context, ContentsManager manager, Spinner spinner, boolean isArm64EC) {
        List<String> itemList;
        if (isArm64EC)
            itemList = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.wowbox64_version_entries)));
        else
            itemList = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.box64_version_entries)));
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
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
    }
    
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig,
                                          String selectedGraphicsDriver, String selectedDXWrapper, Runnable onChange) {
        final Context context = sGraphicsDriver.getContext();
        
        ContainerDetailFragment.updateGraphicsDriverSpinner(context, sGraphicsDriver);
        
        final String[] dxwrapperEntries = context.getResources().getStringArray(R.array.dxwrapper_entries);
        
        // Build DXWrapper adapter once, not on every graphics driver change
        ArrayList<String> dxItems = new ArrayList<>(Arrays.asList(dxwrapperEntries));
        sDXWrapper.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, dxItems));
        AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, selectedDXWrapper);

        Runnable update = () -> {
            String graphicsDriver = sGraphicsDriver.getSelectedItem() != null ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()) : "";
            String graphicsDriverConfig = vGraphicsDriverConfig.getTag() != null ? vGraphicsDriverConfig.getTag().toString() : "";

            tvGraphicsDriverVersion.setText(GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig));

            vGraphicsDriverConfig.setOnClickListener((v) -> {
                GraphicsDriverConfigDialog.showSafe(vGraphicsDriverConfig, graphicsDriver, tvGraphicsDriverVersion);
            });
            if (onChange != null) onChange.run();
        };

        sGraphicsDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
    }

    private void setupDXWrapperSpinner(final Spinner sDXWrapper, final View vDXWrapperConfig, boolean isARM64EC, Runnable onChange) {
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String dxwrapper = sDXWrapper.getSelectedItem() != null ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()) : "";
                if (dxwrapper.contains("dxvk")) {
                    vDXWrapperConfig.setOnClickListener((v) -> {
                        try {
                            (new DXVKConfigDialog(vDXWrapperConfig, isARM64EC)).show();
                        } catch (Throwable e) {
                            Log.e("ShortcutSettingsDialog", "Error opening DXVKConfigDialog", e);
                        }
                    });
                } else {
                    vDXWrapperConfig.setOnClickListener((v) -> {
                        try {
                            (new WineD3DConfigDialog(vDXWrapperConfig)).show();
                        } catch (Throwable e) {
                            Log.e("ShortcutSettingsDialog", "Error opening WineD3DConfigDialog", e);
                        }
                    });
                }
                vDXWrapperConfig.setVisibility(View.VISIBLE);
                if (onChange != null) onChange.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        sDXWrapper.setOnItemSelectedListener(listener);

        int selectedPosition = sDXWrapper.getSelectedItemPosition();
        if (selectedPosition >= 0) {
            listener.onItemSelected(sDXWrapper, sDXWrapper.getSelectedView(), selectedPosition, sDXWrapper.getSelectedItemId());
        }
    }

    private void populateContentsSpinners(Context context, ContentsManager contentsManager,
            Spinner sGraphicsDriver, Spinner sDXWrapper, View vGraphicsDriverConfig, View vDXWrapperConfig,
            Spinner sBox64Version, Spinner sEmulator, Spinner sEmulator64, Spinner sFEXCoreVersion,
            Runnable onChange) {
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
            getShortcutSettingValue("graphicsDriver", shortcut.container.getGraphicsDriver()),
            getShortcutSettingValue("dxwrapper", shortcut.container.getDXWrapper()),
            onChange);

        FrameLayout fexcoreFL = findViewById(R.id.fexcoreFrame);
        String wineVersionStr = shortcut.usesContainerDefaults()
                ? shortcut.container.getWineVersion()
                : shortcut.getExtra("wineVersion", shortcut.container.getWineVersion());
        WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersionStr);
        if (wineInfo.isArm64EC()) {
            fexcoreFL.setVisibility(View.VISIBLE);
            sEmulator.setSelection(2); // Wowbox64 for 32-bit
            sEmulator64.setSelection(0); // FEXCore for 64-bit
            sEmulator.setEnabled(false);
            sEmulator64.setEnabled(false);
        } else {
            fexcoreFL.setVisibility(View.GONE);
            sEmulator.setSelection(1);
            sEmulator64.setSelection(1);
            sEmulator.setEnabled(false);
            sEmulator64.setEnabled(false);
        }

        setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, wineInfo.isArm64EC(), onChange);
        loadBox64VersionSpinner(context, contentsManager, sBox64Version, wineInfo.isArm64EC());

        String currentBox64Version = getShortcutSettingValue("box64Version", shortcut.container.getBox64Version());
        if (currentBox64Version != null) {
            AppUtils.setSpinnerSelectionFromValue(sBox64Version, currentBox64Version);
        } else {
            AppUtils.setSpinnerSelectionFromValue(sBox64Version, wineInfo.isArm64EC() ? DefaultVersion.WOWBOX64 : DefaultVersion.BOX64);
        }

        sBox64Version.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedVersion = parent.getItemAtPosition(position).toString();
                box64Version = selectedVersion;
                if (onChange != null) onChange.run();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion,
            getShortcutSettingValue("fexcoreVersion", shortcut.container.getFEXCoreVersion()));
        if (onChange != null) onChange.run();
    }

    private void setupChangeIndicators(Container container, Runnable[] refreshIndicatorsRef) {
        Runnable refreshIndicators = refreshIndicatorsRef != null && refreshIndicatorsRef.length > 0
                ? refreshIndicatorsRef[0] : null;
        View contentView = getContentView();
        if (contentView == null || refreshIndicators == null) return;

        attachRefreshOnSelection(contentView.findViewById(R.id.SAudioDriver), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SMIDISoundFont), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SFEXCoreVersion), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SFEXCorePreset), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SBox64Preset), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SStartupSelection), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SControlsProfile), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SRefreshRate), refreshIndicators);
        attachRefreshOnSelection(contentView.findViewById(R.id.SDInputType), refreshIndicators);

        attachRefreshOnCheckedChange(contentView.findViewById(R.id.CBFullscreenStretched), refreshIndicators);
        attachRefreshOnCheckedChange(contentView.findViewById(R.id.CBDisabledXInput), refreshIndicators);
        attachRefreshOnCheckedChange(contentView.findViewById(R.id.CBTouchscreenMode), refreshIndicators);
        attachRefreshOnCheckedChange(contentView.findViewById(R.id.CBEnableXInput), refreshIndicators);
        attachRefreshOnCheckedChange(contentView.findViewById(R.id.CBEnableDInput), refreshIndicators);

        attachRefreshOnTextChanged(contentView.findViewById(R.id.ETScreenWidth), refreshIndicators);
        attachRefreshOnTextChanged(contentView.findViewById(R.id.ETScreenHeight), refreshIndicators);
        attachRefreshOnTextChanged(contentView.findViewById(R.id.ETlcall), refreshIndicators);

        attachWinComponentsChangeIndicators(container, refreshIndicators);
        refreshIndicators.run();
    }

    private void attachRefreshOnSelection(Spinner spinner, Runnable refreshIndicators) {
        if (spinner == null || refreshIndicators == null) return;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshIndicators.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void attachRefreshOnCheckedChange(CompoundButton button, Runnable refreshIndicators) {
        if (button == null || refreshIndicators == null) return;
        button.setOnClickListener(v -> refreshIndicators.run());
    }

    private void attachRefreshOnTextChanged(EditText editText, Runnable refreshIndicators) {
        if (editText == null || refreshIndicators == null) return;
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

    private void attachWinComponentsChangeIndicators(Container container, Runnable refreshIndicators) {
        View contentView = getContentView();
        if (contentView == null) return;

        HashMap<String, String> containerDefaults = new HashMap<>();
        for (String[] wincomponent : new KeyValueSet(container.getWinComponents())) {
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
            if (label != null) trackLabel(label);
            String key = spinner.getTag() != null ? spinner.getTag().toString() : "";
            if (label != null) {
                markIfChanged(label, String.valueOf(spinner.getSelectedItemPosition()), containerDefaults.get(key));
            }
            Spinner finalSpinner = spinner;
            TextView finalLabel = label;
            String containerDefault = containerDefaults.get(key);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (finalLabel != null) {
                        markIfChanged(finalLabel, String.valueOf(finalSpinner.getSelectedItemPosition()), containerDefault);
                    }
                    refreshIndicators.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void refreshChangeIndicators(Container container) {
        View contentView = getContentView();
        LinearLayout llContent = contentView.findViewById(R.id.LLContent);
        if (llContent == null) return;

        markIfChanged(findLabelForView(contentView.findViewById(R.id.SScreenSize), llContent),
                ContainerDetailFragment.getScreenSize(contentView),
                container.getScreenSize());

        TextView refreshRateLabel = contentView.findViewById(R.id.TVRefreshRateLabel);
        Spinner sRefreshRate = contentView.findViewById(R.id.SRefreshRate);
        String refreshRate = "0";
        if (sRefreshRate != null && sRefreshRate.getSelectedItem() != null) {
            int selectedRate = RefreshRateUtils.parseRefreshRateLabel(sRefreshRate.getSelectedItem().toString());
            refreshRate = selectedRate > 0 ? String.valueOf(selectedRate) : "0";
        }
        trackLabel(refreshRateLabel);
        markIfChanged(refreshRateLabel, refreshRate, "0");

        Spinner sGraphicsDriver = contentView.findViewById(R.id.SGraphicsDriver);
        TextView graphicsDriverLabel = findLabelForView(sGraphicsDriver, llContent);
        if (graphicsDriverLabel != null) {
            trackLabel(graphicsDriverLabel);
            String graphicsDriver = sGraphicsDriver != null && sGraphicsDriver.getSelectedItem() != null
                    ? StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()) : "";
            View graphicsConfigButton = contentView.findViewById(R.id.BTGraphicsDriverConfig);
            String graphicsDriverConfig = graphicsConfigButton != null && graphicsConfigButton.getTag() != null
                    ? graphicsConfigButton.getTag().toString() : "";
            boolean graphicsChanged = valuesDiffer(graphicsDriver, container.getGraphicsDriver())
                    || valuesDiffer(graphicsDriverConfig, container.getGraphicsDriverConfig());
            markIfChanged(graphicsDriverLabel, graphicsChanged ? "1" : "", "");
        }

        Spinner sDXWrapper = contentView.findViewById(R.id.SDXWrapper);
        TextView dxWrapperLabel = findLabelForView(sDXWrapper, llContent);
        if (dxWrapperLabel != null) {
            trackLabel(dxWrapperLabel);
            String dxwrapper = sDXWrapper != null && sDXWrapper.getSelectedItem() != null
                    ? StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()) : "";
            View dxWrapperConfigButton = contentView.findViewById(R.id.BTDXWrapperConfig);
            String dxWrapperConfig = dxWrapperConfigButton != null && dxWrapperConfigButton.getTag() != null
                    ? dxWrapperConfigButton.getTag().toString() : "";
            boolean dxWrapperChanged = valuesDiffer(dxwrapper, container.getDXWrapper())
                    || valuesDiffer(dxWrapperConfig, container.getDXWrapperConfig());
            markIfChanged(dxWrapperLabel, dxWrapperChanged ? "1" : "", "");
        }

        markSpinnerIfChanged(contentView.findViewById(R.id.SAudioDriver),
                findLabelForView(contentView.findViewById(R.id.SAudioDriver), llContent),
                container.getAudioDriver());
        markSpinnerValueIfChanged(contentView.findViewById(R.id.SMIDISoundFont),
                findLabelForView(contentView.findViewById(R.id.SMIDISoundFont), llContent),
                container.getMIDISoundFont());
        markSpinnerIfChanged(contentView.findViewById(R.id.SEmulator64),
                findLabelForView(contentView.findViewById(R.id.SEmulator64), llContent),
                container.getEmulator64());
        markSpinnerIfChanged(contentView.findViewById(R.id.SEmulator),
                findLabelForView(contentView.findViewById(R.id.SEmulator), llContent),
                container.getEmulator());
        markIfChanged(findLabelForView(contentView.findViewById(R.id.ETlcall), llContent),
                ((EditText) contentView.findViewById(R.id.ETlcall)).getText().toString(),
                container.getLC_ALL());

        TextView envVarsLabel = contentView.findViewById(R.id.TVEnvVarsLabel);
        trackLabel(envVarsLabel);
        markIfChanged(envVarsLabel,
                ((EnvVarsView) contentView.findViewById(R.id.EnvVarsView)).getEnvVars(),
                container.getEnvVars());

        markSpinnerValueIfChanged(contentView.findViewById(R.id.SBox64Version),
                findLabelForView(contentView.findViewById(R.id.SBox64Version), llContent),
                container.getBox64Version());
        TextView box64PresetLabel = findLabelForView(contentView.findViewById(R.id.SBox64Preset), llContent);
        if (box64PresetLabel != null) {
            trackLabel(box64PresetLabel);
            markIfChanged(box64PresetLabel,
                    Box64PresetManager.getSpinnerSelectedId((Spinner) contentView.findViewById(R.id.SBox64Preset)),
                    container.getBox64Preset());
        }
        markSpinnerValueIfChanged(contentView.findViewById(R.id.SFEXCoreVersion),
                findLabelForView(contentView.findViewById(R.id.SFEXCoreVersion), llContent),
                container.getFEXCoreVersion());
        TextView fexcorePresetLabel = findLabelForView(contentView.findViewById(R.id.SFEXCorePreset), llContent);
        if (fexcorePresetLabel != null) {
            trackLabel(fexcorePresetLabel);
            markIfChanged(fexcorePresetLabel,
                    FEXCorePresetManager.getSpinnerSelectedId((Spinner) contentView.findViewById(R.id.SFEXCorePreset)),
                    container.getFEXCorePreset());
        }
        markSpinnerPositionIfChanged(contentView.findViewById(R.id.SStartupSelection),
                findLabelForView(contentView.findViewById(R.id.SStartupSelection), llContent),
                String.valueOf(container.getStartupSelection()));

        CheckBox cbFullscreenStretched = contentView.findViewById(R.id.CBFullscreenStretched);
        trackLabel(cbFullscreenStretched);
        markIfChanged(cbFullscreenStretched,
                cbFullscreenStretched.isChecked() ? "1" : "0",
                container.isFullscreenStretched() ? "1" : "0");

        Spinner sControlsProfile = contentView.findViewById(R.id.SControlsProfile);
        markSpinnerPositionIfChanged(sControlsProfile, findLabelForView(sControlsProfile, llContent), "0");

        CheckBox cbDisabledXInput = contentView.findViewById(R.id.CBDisabledXInput);
        trackLabel(cbDisabledXInput);
        markIfChanged(cbDisabledXInput, cbDisabledXInput.isChecked() ? "1" : "0", "0");

        CheckBox cbSimTouchScreen = contentView.findViewById(R.id.CBTouchscreenMode);
        trackLabel(cbSimTouchScreen);
        markIfChanged(cbSimTouchScreen, cbSimTouchScreen.isChecked() ? "1" : "0", "0");

        CheckBox cbEnableXInput = contentView.findViewById(R.id.CBEnableXInput);
        CheckBox cbEnableDInput = contentView.findViewById(R.id.CBEnableDInput);
        Spinner sDInputType = contentView.findViewById(R.id.SDInputType);
        int containerInputType = container.getInputType();
        trackLabel(cbEnableXInput);
        markIfChanged(cbEnableXInput,
                (cbEnableXInput.isChecked() ? "1" : "0"),
                (containerInputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT ? "1" : "0");
        trackLabel(cbEnableDInput);
        markIfChanged(cbEnableDInput,
                (cbEnableDInput.isChecked() ? "1" : "0"),
                (containerInputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT ? "1" : "0");
        markIfChanged(findLabelForView(sDInputType, llContent),
                String.valueOf(sDInputType.getSelectedItemPosition()),
                ((containerInputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? "0" : "1");

        markIfChanged(findLabelForView(contentView.findViewById(R.id.CPUListView), llContent),
                ((CPUListView) contentView.findViewById(R.id.CPUListView)).getCheckedCPUListAsString(),
                container.getCPUList(true));

        refreshWinComponentsIndicators(container);
    }

    private void refreshWinComponentsIndicators(Container container) {
        View contentView = getContentView();
        if (contentView == null) return;

        HashMap<String, String> containerDefaults = new HashMap<>();
        for (String[] wincomponent : new KeyValueSet(container.getWinComponents())) {
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

    /**
     * Walk backwards through siblings of a view's parent to find the nearest preceding TextView.
     */
    private TextView findLabelForView(View target, ViewGroup root) {
        if (target == null || target.getParent() == null) return null;
        ViewGroup directParent = (ViewGroup) target.getParent();

        // Walk up to find a parent that is a direct child of root (or of LLContent)
        while (directParent != null && directParent.getParent() != root && directParent.getParent() instanceof ViewGroup) {
            target = directParent;
            directParent = (ViewGroup) directParent.getParent();
        }

        if (directParent == null) return null;

        // Now look for preceding siblings
        ViewGroup containerParent = (ViewGroup) directParent.getParent();
        if (containerParent == null) containerParent = directParent;

        int idx = -1;
        for (int i = 0; i < containerParent.getChildCount(); i++) {
            if (containerParent.getChildAt(i) == target || containerParent.getChildAt(i) == directParent) {
                idx = i;
                break;
            }
        }

        // Search backwards for the nearest TextView
        for (int i = idx - 1; i >= 0; i--) {
            View child = containerParent.getChildAt(i);
            if (child instanceof TextView) return (TextView) child;
            // If it's a ViewGroup, check its last child
            if (child instanceof ViewGroup) {
                View found = findLastTextView((ViewGroup) child);
                if (found instanceof TextView) return (TextView) found;
            }
        }

        return null;
    }

    private TextView findLastTextView(ViewGroup group) {
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) return (TextView) child;
            if (child instanceof ViewGroup) {
                TextView found = findLastTextView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
