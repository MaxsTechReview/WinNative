package com.winlator.cmod.contentdialog;



import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
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
    private final ShortcutsFragment fragment;
    private final Shortcut shortcut;
    private InputControlsManager inputControlsManager;
    private TextView tvGraphicsDriverVersion;
    private String box64Version;

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
        if (originalText == null) originalText = label.getText().toString().replace(" *", "");
        boolean changed = currentValue != null && !currentValue.equals(containerDefault);
        label.setText(changed ? originalText + " *" : originalText);
    }

    private void markSpinnerIfChanged(Spinner spinner, TextView label, String containerDefault) {
        if (spinner == null || label == null) return;
        String current = spinner.getSelectedItem() != null ? StringUtils.parseIdentifier(spinner.getSelectedItem()) : "";
        markIfChanged(label, current, containerDefault);
    }

    private void createContentView() {
        final Context context = fragment.getContext();
        inputControlsManager = new InputControlsManager(context);
        LinearLayout llContent = findViewById(R.id.LLContent);
        llContent.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);

        applyDynamicStyles(findViewById(R.id.LLContent));

        // Initialize the turnip version TextView
        tvGraphicsDriverVersion = findViewById(R.id.TVGraphicsDriverVersion);

        final Container container = shortcut.container;

        final EditText etName = findViewById(R.id.ETName);
        etName.setText(shortcut.name);

        findViewById(R.id.BTAddToHomeScreen).setOnClickListener((v) -> {
            boolean requested = fragment.addShortcutToScreen(shortcut);
            if (!requested)
                Toast.makeText(context, context.getString(R.string.library_failed_to_create_shortcut, shortcut.name), Toast.LENGTH_SHORT).show();
        });

        final EditText etExecArgs = findViewById(R.id.ETExecArgs);
        etExecArgs.setText(shortcut.getExtra("execArgs"));

        ContainerDetailFragment containerDetailFragment = new ContainerDetailFragment(shortcut.container.id);

        loadScreenSizeSpinner(getContentView(), shortcut.getExtra("screenSize", container.getScreenSize()));


        final Spinner sGraphicsDriver = findViewById(R.id.SGraphicsDriver);
        
        final Spinner sDXWrapper = findViewById(R.id.SDXWrapper);

        final Spinner sBox64Version = findViewById(R.id.SBox64Version);
        
        final ContentsManager contentsManager = new ContentsManager(context);

        final View vGraphicsDriverConfig = findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig()));

        final View vDXWrapperConfig = findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig()));

        findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        final Spinner sAudioDriver = findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, shortcut.getExtra("audioDriver", container.getAudioDriver()));
        final Spinner sEmulator = findViewById(R.id.SEmulator);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, shortcut.getExtra("emulator", container.getEmulator()));
        final Spinner sEmulator64 = findViewById(R.id.SEmulator64);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, shortcut.getExtra("emulator64", container.getEmulator64()));

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
                    sBox64Version, sEmulator, sEmulator64, sFEXCoreVersion);
                // After spinners are populated, set up change-tracking listeners
                setupChangeIndicators(sGraphicsDriver, sDXWrapper, sAudioDriver, sEmulator, sEmulator64,
                        sBox64Version, sFEXCoreVersion, sFEXCorePreset, sBox64Preset, container);
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
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        sEmulator.setOnItemSelectedListener(emulatorListener);
        sEmulator64.setOnItemSelectedListener(emulatorListener);

        final Spinner sMIDISoundFont = findViewById(R.id.SMIDISoundFont);
        MidiManager.loadSFSpinner(sMIDISoundFont);
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, shortcut.getExtra("midiSoundFont", container.getMIDISoundFont()));

        // Per-game Refresh Rate spinner
        final Spinner sRefreshRate = findViewById(R.id.SRefreshRate);
        loadShortcutRefreshRateSpinner(sRefreshRate, shortcut.getExtra("refreshRate", "0"));

        final EditText etLC_ALL = findViewById(R.id.ETlcall);
        etLC_ALL.setText(shortcut.getExtra("lc_all", container.getLC_ALL()));

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
        boolean fullscreenStretched = shortcut.getExtra("fullscreenStretched", container.isFullscreenStretched() ? "1" : "0").equals("1");
        cbFullscreenStretched.setChecked(fullscreenStretched);

        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.enable_xinput_and_dinput_same_time, null);
        final CheckBox cbEnableXInput = findViewById(R.id.CBEnableXInput);
        final CheckBox cbEnableDInput = findViewById(R.id.CBEnableDInput);
        final View llDInputType = findViewById(R.id.LLDinputMapperType);
        final View btHelpXInput = findViewById(R.id.BTXInputHelp);
        final View btHelpDInput = findViewById(R.id.BTDInputHelp);
        Spinner SDInputType = findViewById(R.id.SDInputType);
        int inputType = Integer.parseInt(shortcut.getExtra("inputType", String.valueOf(container.getInputType())));

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
        btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_xinput));
        btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_dinput));
        SDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
        llDInputType.setVisibility(cbEnableDInput.isChecked()?View.VISIBLE:View.GONE);

        Box64PresetManager.loadSpinner("box64", sBox64Preset, shortcut.getExtra("box64Preset", container.getBox64Preset()));

        FEXCorePresetManager.loadSpinner(sFEXCorePreset, shortcut.getExtra("fexcorePreset", container.getFEXCorePreset()));

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
                shortcut.getExtra("wincomponents", container.getWinComponents()));

        final EnvVarsView envVarsView = createEnvVarsTab();

        AppUtils.setupTabLayout(getContentView(), R.id.TabLayout, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabAdvanced);

        final Spinner sStartupSelection = findViewById(R.id.SStartupSelection);
        sStartupSelection.setSelection(Integer.parseInt(shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()))));

        // --- Reset Button: resets all per-game settings back to container defaults ---
        Button resetButton = getContentView().findViewById(R.id.BTReset);
        resetButton.setVisibility(View.VISIBLE);
        resetButton.setOnClickListener(v -> {
            // Reset all spinners/checkboxes to container defaults
            AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, container.getGraphicsDriver());
            vGraphicsDriverConfig.setTag(container.getGraphicsDriverConfig());
            AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, container.getDXWrapper());
            vDXWrapperConfig.setTag(container.getDXWrapperConfig());
            AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, container.getAudioDriver());
            AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, container.getEmulator());
            AppUtils.setSpinnerSelectionFromIdentifier(sEmulator64, container.getEmulator64());
            AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, container.getMIDISoundFont());
            etLC_ALL.setText(container.getLC_ALL());
            cbFullscreenStretched.setChecked(container.isFullscreenStretched());

            int containerInputType = container.getInputType();
            cbEnableXInput.setChecked((containerInputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) != 0);
            cbEnableDInput.setChecked((containerInputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) != 0);
            SDInputType.setSelection(((containerInputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) != 0) ? 0 : 1);

            Box64PresetManager.loadSpinner("box64", sBox64Preset, container.getBox64Preset());
            FEXCorePresetManager.loadSpinner(sFEXCorePreset, container.getFEXCorePreset());
            AppUtils.setSpinnerSelectionFromValue(sBox64Version, container.getBox64Version());
            sStartupSelection.setSelection(container.getStartupSelection());

            cbDisabledXInput.setChecked(false);
            cbSimTouchScreen.setChecked(false);

            // Reset the screen size
            loadScreenSizeSpinner(getContentView(), container.getScreenSize());

            // Reset refresh rate to default (global)
            loadShortcutRefreshRateSpinner(sRefreshRate, "0");

            // Update all change indicators to remove *
            for (HashMap.Entry<TextView, String> entry : labelOriginalText.entrySet()) {
                entry.getKey().setText(entry.getValue());
            }

            Toast.makeText(context, "Settings reset to container defaults", Toast.LENGTH_SHORT).show();
        });

        TabLayout tabLayout = findViewById(R.id.TabLayout);

        tabLayout.setBackgroundResource(R.drawable.tab_layout_background_dark);

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
        cpuListView.setCheckedCPUList(shortcut.getExtra("cpuList", shortcut.container.getCPUList(true)));

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


                shortcut.putExtra("inputType", String.valueOf(finalInputType));

                boolean disabledXInput = cbDisabledXInput.isChecked();
                shortcut.putExtra("disableXinput", disabledXInput ? "1" : null);

                boolean touchscreenMode = cbSimTouchScreen.isChecked();
                shortcut.putExtra("simTouchScreen", touchscreenMode ? "1" : "0");

                String execArgs = etExecArgs.getText().toString();
                shortcut.putExtra("execArgs", !execArgs.isEmpty() ? execArgs : null);
                shortcut.putExtra("screenSize", screenSize);
                shortcut.putExtra("graphicsDriver", graphicsDriver);
                shortcut.putExtra("graphicsDriverConfig", graphicsDriverConfig);
                shortcut.putExtra("dxwrapper", dxwrapper);
                shortcut.putExtra("dxwrapperConfig", dxwrapperConfig);
                shortcut.putExtra("audioDriver", audioDriver);
                shortcut.putExtra("emulator", emulator);
                shortcut.putExtra("emulator64", emulator64);
                shortcut.putExtra("midiSoundFont", midiSoundFont);
                shortcut.putExtra("lc_all", lc_all);

                shortcut.putExtra("fullscreenStretched", cbFullscreenStretched.isChecked() ? "1" : null);

                String wincomponents = containerDetailFragment.getWinComponents(getContentView());
                shortcut.putExtra("wincomponents", wincomponents);

                String envVars = envVarsView.getEnvVars();
                shortcut.putExtra("envVars", !envVars.isEmpty() ? envVars : null);

                String fexcoreVersion = sFEXCoreVersion.getSelectedItem().toString();
                shortcut.putExtra("fexcoreVersion", fexcoreVersion);

                String fexcorePreset = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
                shortcut.putExtra("fexcorePreset", fexcorePreset);

                // Save box64Version from spinner (was previously missing!)
                String selectedBox64Version = sBox64Version.getSelectedItem() != null
                        ? sBox64Version.getSelectedItem().toString() : "";
                shortcut.putExtra("box64Version", selectedBox64Version);

                String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
                shortcut.putExtra("box64Preset", box64Preset);

                byte startupSelection = (byte)sStartupSelection.getSelectedItemPosition();
                shortcut.putExtra("startupSelection", String.valueOf(startupSelection));

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
                shortcut.putExtra("cpuList", cpuList);

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
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);

        applyDarkThemeToEditText(view.findViewById(R.id.ETScreenWidth));
        applyDarkThemeToEditText(view.findViewById(R.id.ETScreenHeight));


        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
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
        String envVarsValue = shortcut.getExtra(
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
        values.add(context.getString(R.string.none));

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
        ContentDialog.alert(context, R.string.enable_xinput_and_dinput_same_time, null);
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
    
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig, String selectedGraphicsDriver, String selectedDXWrapper) {
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

    private void populateContentsSpinners(Context context, ContentsManager contentsManager,
            Spinner sGraphicsDriver, Spinner sDXWrapper, View vGraphicsDriverConfig, View vDXWrapperConfig,
            Spinner sBox64Version, Spinner sEmulator, Spinner sEmulator64, Spinner sFEXCoreVersion) {
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
            shortcut.getExtra("graphicsDriver", shortcut.container.getGraphicsDriver()),
            shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper()));

        FrameLayout fexcoreFL = findViewById(R.id.fexcoreFrame);
        String wineVersionStr = shortcut.getExtra("wineVersion", shortcut.container.getWineVersion());
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

        ContainerDetailFragment.setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig, wineInfo.isArm64EC());
        loadBox64VersionSpinner(context, contentsManager, sBox64Version, wineInfo.isArm64EC());

        String currentBox64Version = shortcut.getExtra("box64Version", shortcut.container.getBox64Version());
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
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion,
            shortcut.getExtra("fexcoreVersion", shortcut.container.getFEXCoreVersion()));
    }

    /**
     * Set up change-indicator listeners on all per-game setting spinners/controls.
     * When a user changes a spinner or checkbox away from the container default,
     * the corresponding label gets a ' *' suffix.
     */
    private void setupChangeIndicators(Spinner sGraphicsDriver, Spinner sDXWrapper, Spinner sAudioDriver,
            Spinner sEmulator, Spinner sEmulator64, Spinner sBox64Version,
            Spinner sFEXCoreVersion, Spinner sFEXCorePreset, Spinner sBox64Preset,
            Container container) {
        // Find all the label TextViews in the shortcut_settings_dialog layout
        // These are the direct TextViews that precede each spinner
        View contentView = getContentView();
        LinearLayout llContent = contentView.findViewById(R.id.LLContent);
        if (llContent == null) return;

        // Attach change listeners to key spinners
        attachSpinnerChangeIndicator(sGraphicsDriver, "Graphics Driver", container.getGraphicsDriver(), llContent);
        attachSpinnerChangeIndicator(sDXWrapper, "DX Wrapper", container.getDXWrapper(), llContent);
        attachSpinnerChangeIndicator(sAudioDriver, "Audio Driver", container.getAudioDriver(), llContent);
    }

    /**
     * Find the label preceding a spinner in the layout and set up a listener
     * to mark it with '*' when the selection differs from the container default.
     */
    private void attachSpinnerChangeIndicator(Spinner spinner, String fallbackLabel, String containerDefault, ViewGroup parent) {
        if (spinner == null) return;

        // Find the label by looking for the TextView that is a sibling or ancestor-sibling of this spinner
        TextView label = findLabelForView(spinner, parent);
        if (label != null) {
            trackLabel(label);
            // Set initial state
            markSpinnerIfChanged(spinner, label, containerDefault);
        }

        final TextView finalLabel = label;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (finalLabel != null) {
                    markSpinnerIfChanged(spinner, finalLabel, containerDefault);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
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
