package com.winlator.cmod.contentdialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.OtherSettingsFragment
import com.winlator.cmod.R
import com.winlator.cmod.SetupWizardActivity
import com.winlator.cmod.ShortcutsFragment
import com.winlator.cmod.box64.Box64Preset
import com.winlator.cmod.box64.Box64PresetManager
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.EnvVars
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.KeyValueSet
import com.winlator.cmod.core.RefreshRateUtils
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.fexcore.FEXCoreManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.midi.MidiManager
import com.winlator.cmod.winhandler.WinHandler
import java.io.File
import java.lang.reflect.Field
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.Executors

class ShortcutSettingsComposeDialog private constructor(
    private val activity: Activity,
    private val shortcut: Shortcut,
    private val fragment: ShortcutsFragment?
) {
    private val context: Context = activity

    constructor(fragment: ShortcutsFragment, shortcut: Shortcut) :
        this(fragment.requireActivity(), shortcut, fragment)

    constructor(activity: Activity, shortcut: Shortcut) :
        this(activity, shortcut, null)
    private val dialog: Dialog
    private val state = GameSettingsStateHolder()

    // Java interop references
    private var inputControlsManager: InputControlsManager = InputControlsManager(context)
    private var contentsManager: ContentsManager = ContentsManager(context)
    private var isArm64EC = false


    // Preset ID lists (parallel to display name lists)
    private var box64PresetIds = mutableListOf<String>()
    private var fexcorePresetIds = mutableListOf<String>()

    // SDL2 Compatibility env vars — must match ContainerDetailFragment.SDL2_ENV_VARS.
    private val sdl2EnvVars = listOf(
        "SDL_JOYSTICK_WGI" to "0",
        "SDL_XINPUT_ENABLED" to "1",
        "SDL_JOYSTICK_RAWINPUT" to "0",
        "SDL_JOYSTICK_HIDAPI" to "1",
        "SDL_DIRECTINPUT_ENABLED" to "0",
        "SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS" to "1",
        "SDL_HINT_FORCE_RAISEWINDOW" to "0",
        "SDL_ALLOW_TOPMOST" to "0",
        "SDL_MOUSE_FOCUS_CLICKTHROUGH" to "1"
    )

    // Container list for container selection
    private var containerList = mutableListOf<Container>()

    // File picker launcher for Select EXE
    private val exePickerLauncher: ActivityResultLauncher<Array<String>>? =
        (activity as? ComponentActivity)?.activityResultRegistry?.register(
            "shortcut_exe_picker",
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) return@register
            val path = FileUtils.getFilePathFromUri(context, uri) ?: return@register
            val fileName = FileUtils.getUriFileName(context, uri)
            val isExe = path.lowercase().endsWith(".exe") ||
                (fileName != null && fileName.lowercase().endsWith(".exe"))
            if (isExe) {
                state.launchExePath.value = path
            } else {
                Toast.makeText(context, R.string.common_ui_select_valid_exe_file, Toast.LENGTH_SHORT).show()
            }
        }

    init {
        dialog = Dialog(activity, R.style.ContentDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setOwnerActivity(activity)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setDimAmount(0.5f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                // Blur-behind is applied in show() post-attach to avoid flicker.
            }
        }

        loadInitialData()
        loadResourceArrays()

        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewTreeLifecycleOwner(activity as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)
            setContent {
                GameSettingsContent(
                    state = state,
                    callbacks = createCallbacks()
                )
            }
        }
        dialog.setContentView(composeView)

        // Auto-dismiss when activity is destroyed
        (activity as LifecycleOwner).lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (dialog.isShowing) dialog.dismiss()
            }
        })

        // Load content-dependent data on background thread
        loadContentsAsync()
    }

    private fun createCallbacks(): GameSettingsCallbacks {
        return object : GameSettingsCallbacks {
            override fun onConfirm() {
                saveSettings()
                dismiss()
            }

            override fun onDismiss() {
                dismiss()
            }

            override fun onGraphicsDriverConfig() {
                // Now handled inline by GraphicsDriverConfigCard
            }

            override fun onDxWrapperConfig() {
                // Now handled inline by DXVKConfigCard
            }

            override fun onAddEnvVar() {
                // Now handled by Compose AddEnvVarDialog in VariablesSection
            }

            override fun onAddToHomeScreen() {
                val requested = if (fragment != null) {
                    fragment.addShortcutToScreen(shortcut)
                } else {
                    addShortcutToScreen(shortcut)
                }
                if (!requested) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.library_games_failed_to_create_shortcut,
                            shortcut.name
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRemoveEnvVar(index: Int) {
                val currentVars = state.envVars.value.toMutableList()
                if (index in currentVars.indices) {
                    currentVars.removeAt(index)
                    state.envVars.value = currentVars
                }
            }

            override fun onGfxDriverVersionChanged(versionIndex: Int) {
                loadExtensionsForVersion(versionIndex)
                // Update version display
                val versions = state.gfxDriverVersionEntries.value
                state.graphicsDriverVersion.value = versions.getOrElse(versionIndex) { "" }
            }

            override fun onDxvkVkd3dVersionChanged(versionIndex: Int) {
                handleDxvkVkd3dVersionChanged(versionIndex)
            }

            override fun onContainerChanged(containerIndex: Int) {
                handleContainerChanged(containerIndex)
            }

            override fun onEmulatorChanged() {
                updateEmulatorFrameVisibility()
            }

            override fun onSelectExe() {
                exePickerLauncher?.launch(arrayOf("*/*"))
            }

            override fun onUpdateWinComponent(isDirectX: Boolean, index: Int, newValue: Int) {
                if (isDirectX) {
                    val components = state.directXComponents.value.toMutableList()
                    if (index in components.indices) {
                        components[index] = components[index].copy(selectedIndex = newValue)
                        state.directXComponents.value = components
                    }
                } else {
                    val components = state.generalComponents.value.toMutableList()
                    if (index in components.indices) {
                        components[index] = components[index].copy(selectedIndex = newValue)
                        state.generalComponents.value = components
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Data Loading
    // ------------------------------------------------------------------

    private fun loadInitialData() {
        val container = shortcut.container

        // General
        state.name.value = shortcut.name
        state.launchExePath.value = resolveInitialLaunchExePath()

        // Input
        val inputType = Integer.parseInt(
            getShortcutSetting("inputType", container.getInputType().toString())
        )
        state.enableXInput.value =
            (inputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) == WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        state.enableDInput.value =
            (inputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) == WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
        state.selectedDInputMapperType.intValue =
            if ((inputType and WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()) 0 else 1
        state.disableXInput.value = shortcut.getExtra("disableXinput", "0") == "1"
        state.simTouchScreen.value = shortcut.getExtra("simTouchScreen", "0") == "1"

        // Display - Show FPS
        state.showFPS.value = getShortcutSetting(
            "showFPS", if (container.isShowFPS) "1" else "0"
        ) == "1"

        // Steam options
        val gameSource = shortcut.getExtra("game_source", "")
        state.isSteamGame.value = gameSource == "STEAM" || gameSource == "steam"
        if (state.isSteamGame.value) {
            state.useColdClient.value = getShortcutSetting(
                "useColdClient", if (container.isUseColdClient) "1" else "0") == "1"
            state.launchRealSteam.value = getShortcutSetting(
                "launchRealSteam", if (container.isLaunchRealSteam) "1" else "0") == "1"
            state.useSteamInput.value = shortcut.getExtra("useSteamInput", "0") == "1"
            state.forceDlc.value = getShortcutSetting(
                "forceDlc", if (container.isForceDlc) "1" else "0") == "1"
            state.steamOfflineMode.value = getShortcutSetting(
                "steamOfflineMode", if (container.isSteamOfflineMode) "1" else "0") == "1"
            state.unpackFiles.value = getShortcutSetting(
                "unpackFiles", if (container.isUnpackFiles) "1" else "0") == "1"
        }

        // Desktop Theme
        val desktopTheme = getShortcutSetting("desktopTheme", container.getDesktopTheme())
        // Will be used when entries are loaded

        // Advanced - System
        state.execArgs.value = shortcut.getExtra("execArgs", "")
        val fullscreenStretched = getShortcutSetting(
            "fullscreenStretched",
            if (container.isFullscreenStretched) "1" else "0"
        )
        state.fullscreenStretched.value = fullscreenStretched == "1"

        // LC_ALL
        state.lcAll.value = getShortcutSetting("lc_all", container.getLC_ALL())

        // CPU Affinity
        val cpuList = getShortcutSetting("cpuList", container.getCPUList(true))
        val cpuCount = Runtime.getRuntime().availableProcessors()
        state.cpuCount.intValue = cpuCount
        val checked = MutableList(cpuCount) { true }
        if (cpuList.isNotEmpty()) {
            // Reset all to false, then enable specified CPUs
            for (i in checked.indices) checked[i] = false
            cpuList.split(",").forEach { cpuStr ->
                val idx = cpuStr.trim().replace("CPU", "").toIntOrNull()
                if (idx != null && idx in checked.indices) checked[idx] = true
            }
        }
        state.cpuChecked.value = checked

        // CPU Affinity (32-bit / WoW64)
        val cpuListWoW64 = getShortcutSetting("cpuListWoW64", container.getCPUListWoW64(true))
        val checkedWoW64 = MutableList(cpuCount) { true }
        if (cpuListWoW64.isNotEmpty()) {
            for (i in checkedWoW64.indices) checkedWoW64[i] = false
            cpuListWoW64.split(",").forEach { cpuStr ->
                val idx = cpuStr.trim().replace("CPU", "").toIntOrNull()
                if (idx != null && idx in checkedWoW64.indices) checkedWoW64[idx] = true
            }
        }
        state.cpuCheckedWoW64.value = checkedWoW64

        // Win Components
        loadWinComponents()

        // Env Vars
        loadEnvVars()
    }

    private fun loadResourceArrays() {
        val container = shortcut.container

        // Screen sizes
        val screenSizeArr =
            context.resources.getStringArray(R.array.screen_size_entries).toList()
        state.screenSizeEntries.value = screenSizeArr
        val screenSize = getShortcutSetting("screenSize", container.getScreenSize())
        selectScreenSize(screenSize)

        // Container selection
        loadContainerList()

        // Refresh rate
        try {
            val refreshEntries = OtherSettingsFragment.buildRefreshRateEntries(activity)
            state.refreshRateEntries.value = refreshEntries
            val savedRate = shortcut.getExtra("refreshRate", "0")
            if (savedRate.isNullOrEmpty() || savedRate == "0") {
                state.selectedRefreshRate.intValue = 0
            } else {
                val target = "$savedRate Hz"
                val idx = refreshEntries.indexOfFirst { it == target }
                state.selectedRefreshRate.intValue = if (idx >= 0) idx else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading refresh rate entries", e)
        }

        // Graphics driver (basic entries - will be updated after contents sync)
        val graphicsDriverArr =
            context.resources.getStringArray(R.array.graphics_driver_entries).toList()
        state.graphicsDriverEntries.value = graphicsDriverArr
        selectByIdentifier(
            graphicsDriverArr,
            getShortcutSetting("graphicsDriver", container.getGraphicsDriver()),
            state.selectedGraphicsDriver
        )

        // DX Wrapper
        val dxWrapperArr =
            context.resources.getStringArray(R.array.dxwrapper_entries).toList()
        state.dxWrapperEntries.value = dxWrapperArr
        selectByIdentifier(
            dxWrapperArr,
            getShortcutSetting("dxwrapper", container.getDXWrapper()),
            state.selectedDxWrapper
        )

        // Audio driver
        val audioDriverArr =
            context.resources.getStringArray(R.array.audio_driver_entries).toList()
        state.audioDriverEntries.value = audioDriverArr
        selectByIdentifier(
            audioDriverArr,
            getShortcutSetting("audioDriver", container.getAudioDriver()),
            state.selectedAudioDriver
        )

        // MIDI sound fonts
        loadMidiSoundFonts()

        // Detect wine arch synchronously so filtered emulator dropdowns
        // render before the async content sync.
        val emulatorArr =
            context.resources.getStringArray(R.array.emulator_entries).toList()
        state.emulatorEntries.value = emulatorArr

        val wineVersionStr = if (shortcut.usesContainerDefaults())
            container.getWineVersion()
        else shortcut.getExtra("wineVersion", container.getWineVersion())
        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersionStr)
        isArm64EC = wineInfo.isArm64EC
        state.wineVersionDisplay.value = formatWineVersionDisplay(wineInfo)

        rebuildEmulatorLists()
        selectByIdentifier(
            state.emulator32Entries.value,
            getShortcutSetting("emulator", container.getEmulator()),
            state.selectedEmulator
        )
        selectByIdentifier(
            state.emulator64Entries.value,
            getShortcutSetting("emulator64", container.getEmulator64()),
            state.selectedEmulator64
        )

        // Locales
        val locales = context.resources.getStringArray(R.array.some_lc_all).toList()
        state.localeOptions.value = locales

        // Win component entries
        val winCompEntries =
            context.resources.getStringArray(R.array.wincomponent_entries).toList()
        state.winComponentEntries.value = winCompEntries

        // DInput mapper type entries
        val dInputArr =
            context.resources.getStringArray(R.array.dinput_mapper_type_entries).toList()
        state.dInputMapperTypeEntries.value = dInputArr

        // Startup selection
        val startupArr =
            context.resources.getStringArray(R.array.startup_selection_entries).toList()
        state.startupSelectionEntries.value = startupArr
        state.selectedStartupSelection.intValue = Integer.parseInt(
            getShortcutSetting(
                "startupSelection",
                container.getStartupSelection().toString()
            )
        ).coerceIn(0, startupArr.size - 1)

        // Controls profiles
        loadControlsProfiles()

        // Box64 presets
        loadBox64Presets()

        // FEXCore presets
        loadFexcorePresets()

        // Graphics driver configuration (inline card state)
        loadGraphicsDriverConfigState()

        // Desktop theme entries
        val desktopThemeArr =
            context.resources.getStringArray(R.array.desktop_theme_entries).toList()
        state.desktopThemeEntries.value = desktopThemeArr
        // Desktop theme is stored as compound "THEME,TYPE,COLOR" — extract theme name
        val savedDesktopTheme = getShortcutSetting("desktopTheme", container.getDesktopTheme())
        val themePart = savedDesktopTheme.split(",").firstOrNull()?.trim() ?: ""
        // Match case-insensitively: enum is "LIGHT"/"DARK", entries are "Light"/"Dark"
        val themeIdx = desktopThemeArr.indexOfFirst { it.equals(themePart, ignoreCase = true) }
        state.selectedDesktopTheme.intValue = if (themeIdx >= 0) themeIdx else 0

        // Steam type entries
        if (state.isSteamGame.value) {
            val steamTypeArr =
                context.resources.getStringArray(R.array.steam_type_entries).toList()
            state.steamTypeEntries.value = steamTypeArr
            val savedSteamType = getShortcutSetting("steamType", container.getSteamType())
            selectByValue(steamTypeArr, savedSteamType, state.selectedSteamType)
        }

        // Show Box64/FEXCore frames based on saved emulator selection immediately,
        // before the async content sync runs
        updateEmulatorFrameVisibility()
    }

    private fun loadContentsAsync() {
        Executors.newSingleThreadExecutor().execute {
            try {
                contentsManager.syncContents()
                activity.runOnUiThread {
                    try {
                        populateContentsDependentData()
                    } finally {
                        state.isLoaded.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing contents", e)
                activity.runOnUiThread {
                    state.isLoaded.value = true
                }
            }
        }
    }

    private fun populateContentsDependentData() {
        val container = shortcut.container
        val wineVersionStr = if (shortcut.usesContainerDefaults())
            container.getWineVersion()
        else shortcut.getExtra("wineVersion", container.getWineVersion())

        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersionStr)
        val archChanged = isArm64EC != wineInfo.isArm64EC
        isArm64EC = wineInfo.isArm64EC
        state.wineVersionDisplay.value = formatWineVersionDisplay(wineInfo)

        rebuildEmulatorLists()
        state.emulatorsEnabled.value = true

        // Arch flipped after async sync resolved the wine profile — re-apply
        // the shortcut's saved emulator against the rebuilt lists.
        if (archChanged) {
            selectByIdentifier(
                state.emulator32Entries.value,
                getShortcutSetting("emulator", container.getEmulator()),
                state.selectedEmulator
            )
            selectByIdentifier(
                state.emulator64Entries.value,
                getShortcutSetting("emulator64", container.getEmulator64()),
                state.selectedEmulator64
            )
        }

        loadBox64Versions()
        loadFexcoreVersions()
        updateEmulatorFrameVisibility()
        loadDxvkConfigState()
        loadWineD3DConfigState()
    }

    // ------------------------------------------------------------------
    // Helper load methods
    // ------------------------------------------------------------------

    private fun loadMidiSoundFonts() {
        // Use a temporary Spinner to leverage MidiManager.loadSFSpinner
        val tempSpinner = android.widget.Spinner(context)
        MidiManager.loadSFSpinner(tempSpinner)
        val adapter = tempSpinner.adapter
        val filesName = mutableListOf<String>()
        if (adapter != null) {
            for (i in 0 until adapter.count) {
                filesName.add(adapter.getItem(i).toString())
            }
        }

        state.midiSoundFontEntries.value = filesName
        val savedFont = getShortcutSetting("midiSoundFont", shortcut.container.getMIDISoundFont())
        if (savedFont.isEmpty()) {
            state.selectedMidiSoundFont.intValue = 0
        } else {
            val idx = filesName.indexOfFirst { it == savedFont }
            state.selectedMidiSoundFont.intValue = if (idx >= 0) idx else 0
        }
    }

    private fun loadContainerList() {
        try {
            val manager = ContainerManager(context)
            containerList.clear()
            containerList.addAll(manager.getContainers())
            val names = containerList.map { it.getName() }
            state.containerEntries.value = names

            // Select the current container
            val currentContainerId = shortcut.container.id
            val idx = containerList.indexOfFirst { it.id == currentContainerId }
            state.selectedContainer.intValue = if (idx >= 0) idx else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error loading container list", e)
        }
    }

    private fun loadControlsProfiles() {
        val profiles = inputControlsManager.getProfiles(true)
        val values = mutableListOf(context.getString(R.string.common_ui_none))
        for (profile in profiles) values.add(profile.getName())
        state.controlsProfileEntries.value = values

        val selectedId =
            Integer.parseInt(shortcut.getExtra("controlsProfile", "0"))
        var selectedPos = 0
        for (i in profiles.indices) {
            if (profiles[i].id == selectedId) {
                selectedPos = i + 1
                break
            }
        }
        state.selectedControlsProfile.intValue = selectedPos
    }

    // Shortcut extras apply only to the shortcut's own container.
    private fun shouldUseShortcutOverrides(container: Container): Boolean =
        container === shortcut.container

    private fun loadBox64Presets(container: Container = shortcut.container) {
        val presets = Box64PresetManager.getPresets("box64", context)
        val names = mutableListOf<String>()
        val ids = mutableListOf<String>()
        for (preset in presets) {
            names.add(preset.name)
            ids.add(preset.id)
        }
        state.box64PresetEntries.value = names
        box64PresetIds = ids

        val savedPreset = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("box64Preset", container.getBox64Preset())
        else
            container.getBox64Preset()
        val idx = ids.indexOfFirst { it == savedPreset }
        state.selectedBox64Preset.intValue = if (idx >= 0) idx else 0
    }

    private fun loadFexcorePresets(container: Container = shortcut.container) {
        val presets = FEXCorePresetManager.getPresets(context)
        val names = mutableListOf<String>()
        val ids = mutableListOf<String>()
        for (preset in presets) {
            names.add(preset.name)
            ids.add(preset.id)
        }
        state.fexcorePresetEntries.value = names
        fexcorePresetIds = ids

        val savedPreset = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("fexcorePreset", container.getFEXCorePreset())
        else
            container.getFEXCorePreset()
        val idx = ids.indexOfFirst { it == savedPreset }
        state.selectedFexcorePreset.intValue = if (idx >= 0) idx else 0
    }

    private fun loadBox64Versions(container: Container = shortcut.container) {
        val itemList: MutableList<String> = if (isArm64EC) {
            context.resources.getStringArray(R.array.wowbox64_version_entries).toMutableList()
        } else {
            context.resources.getStringArray(R.array.box64_version_entries).toMutableList()
        }

        val profileType = if (isArm64EC)
            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        else ContentProfile.ContentType.CONTENT_TYPE_BOX64

        for (profile in contentsManager.getProfiles(profileType)) {
            val entryName = ContentsManager.getEntryName(profile)
            val firstDash = entryName.indexOf('-')
            if (firstDash >= 0) itemList.add(entryName.substring(firstDash + 1))
        }

        state.box64VersionEntries.value = itemList

        val currentVersion = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("box64Version", container.getBox64Version())
        else
            container.getBox64Version()
        if (currentVersion != null) {
            selectByValue(itemList, currentVersion, state.selectedBox64Version)
        } else {
            val default = if (isArm64EC) DefaultVersion.WOWBOX64 else DefaultVersion.BOX64
            selectByValue(itemList, default, state.selectedBox64Version)
        }

        // Show/hide Box64 frame
        updateEmulatorFrameVisibility()
    }

    private fun loadFexcoreVersions(container: Container = shortcut.container) {
        val items = mutableListOf<String>()
        val defaultEntries =
            context.resources.getStringArray(R.array.fexcore_version_entries)
        items.addAll(defaultEntries)

        for (profile in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)) {
            val entryName = ContentsManager.getEntryName(profile)
            val firstDash = entryName.indexOf('-')
            if (firstDash >= 0) items.add(entryName.substring(firstDash + 1))
        }

        state.fexcoreVersionEntries.value = items
        val savedVersion = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("fexcoreVersion", container.getFEXCoreVersion())
        else
            container.getFEXCoreVersion()
        selectByValue(items, savedVersion, state.selectedFexcoreVersion)
    }

    private fun updateEmulatorFrameVisibility() {
        val emulator32Entries = state.emulator32Entries.value
        val emulator64Entries = state.emulator64Entries.value
        val emulator32 = if (state.selectedEmulator.intValue in emulator32Entries.indices)
            StringUtils.parseIdentifier(emulator32Entries[state.selectedEmulator.intValue]) else ""
        val emulator64 = if (state.selectedEmulator64.intValue in emulator64Entries.indices)
            StringUtils.parseIdentifier(emulator64Entries[state.selectedEmulator64.intValue]) else ""

        // Wowbox64 reuses Box64 presets.
        val usesWowbox64 = emulator32.equals("wowbox64", true) || emulator64.equals("wowbox64", true)

        state.showBox64Frame.value =
            emulator32.equals("box64", true) || emulator64.equals("box64", true) || usesWowbox64
        state.showFexcoreFrame.value =
            emulator32.equals("fexcore", true) || emulator64.equals("fexcore", true)
    }

    private fun formatWineVersionDisplay(wineInfo: WineInfo): String {
        val base = wineInfo.toString()
        val archLabel = when (wineInfo.arch?.lowercase()) {
            "arm64ec" -> "ARM64EC"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> wineInfo.arch ?: ""
        }
        return if (archLabel.isNotEmpty()) "$base ($archLabel)" else base
    }

    // ARM64EC → 64=FEXCore, 32=FEXCore|Wowbox64. x86_64 → 64=Box64, 32=Wowbox64.
    private fun rebuildEmulatorLists() {
        val fullList = state.emulatorEntries.value
        fun entryById(id: String): String? = fullList.firstOrNull {
            StringUtils.parseIdentifier(it).equals(id, ignoreCase = true)
        }

        val prev32 = state.emulator32Entries.value
        val prev64 = state.emulator64Entries.value
        val prev32Id = prev32.getOrNull(state.selectedEmulator.intValue)
            ?.let { StringUtils.parseIdentifier(it) } ?: ""
        val prev64Id = prev64.getOrNull(state.selectedEmulator64.intValue)
            ?.let { StringUtils.parseIdentifier(it) } ?: ""

        if (isArm64EC) {
            state.emulator64Entries.value = listOfNotNull(entryById("fexcore"))
            state.emulator32Entries.value =
                listOfNotNull(entryById("fexcore"), entryById("wowbox64"))
        } else {
            state.emulator64Entries.value = listOfNotNull(entryById("box64"))
            state.emulator32Entries.value = listOfNotNull(entryById("wowbox64"))
        }

        val new32 = state.emulator32Entries.value
        val new32Idx = new32.indexOfFirst {
            StringUtils.parseIdentifier(it).equals(prev32Id, ignoreCase = true)
        }
        state.selectedEmulator.intValue = if (new32Idx >= 0) new32Idx else 0

        val new64 = state.emulator64Entries.value
        val new64Idx = new64.indexOfFirst {
            StringUtils.parseIdentifier(it).equals(prev64Id, ignoreCase = true)
        }
        state.selectedEmulator64.intValue = if (new64Idx >= 0) new64Idx else 0
    }

    private fun loadWinComponents() {
        val container = shortcut.container
        val wincomponentsStr =
            getShortcutSetting("wincomponents", container.getWinComponents())
        val directX = mutableListOf<WinComponentItem>()
        val general = mutableListOf<WinComponentItem>()

        for (component in KeyValueSet(wincomponentsStr)) {
            val key = component[0]
            val value = component[1]
            val label = StringUtils.getString(context, key) ?: key
            val selectedIdx = try {
                Integer.parseInt(value)
            } catch (e: NumberFormatException) {
                0
            }
            val item = WinComponentItem(key, label, selectedIdx)
            if (key.startsWith("direct")) {
                directX.add(item)
            } else {
                general.add(item)
            }
        }

        state.directXComponents.value = directX
        state.generalComponents.value = general
    }

    private fun loadEnvVars() {
        val container = shortcut.container
        val envVarsStr = getShortcutSetting(
            "envVars",
            container?.getEnvVars() ?: Container.DEFAULT_ENV_VARS
        )
        val envVars = EnvVars(envVarsStr)
        val items = mutableListOf<EnvVarItem>()
        for (key in envVars) {
            items.add(EnvVarItem(key, envVars.get(key)))
        }
        state.envVars.value = items

        // Hide SDL2 keys from the user-visible list when the toggle is on.
        state.sdl2Compatibility.value = envVars.get("SDL_XINPUT_ENABLED") == "1"
        if (state.sdl2Compatibility.value) {
            state.envVars.value = items.filterNot { item ->
                sdl2EnvVars.any { it.first == item.key }
            }
        }

        // Exclusive Input off → force both APIs on and lock them.
        if (!state.disableXInput.value) {
            state.enableXInput.value = true
            state.enableDInput.value = true
        }
    }

    private fun selectScreenSize(screenSize: String) {
        val entries = state.screenSizeEntries.value
        // Try to match by identifier
        val idx = entries.indexOfFirst {
            StringUtils.parseIdentifier(it) == StringUtils.parseIdentifier(screenSize)
        }
        if (idx >= 0) {
            state.selectedScreenSize.intValue = idx
        } else {
            // Custom screen size
            state.selectedScreenSize.intValue = 0 // "Custom" is at index 0
            val parts = screenSize.split("x")
            if (parts.size == 2) {
                state.customWidth.value = parts[0]
                state.customHeight.value = parts[1]
            }
        }
    }

    // ------------------------------------------------------------------
    // Save Settings
    // ------------------------------------------------------------------

    private fun saveSettings() {
        // Compare against the target container (post-switch) so unchanged
        // values aren't written as overrides.
        val selectedContainerIdxEarly = state.selectedContainer.intValue
        val container: Container = if (selectedContainerIdxEarly in containerList.indices)
            containerList[selectedContainerIdxEarly]
        else
            shortcut.container
        val name = state.name.value.trim()
        val nameChanged = shortcut.name != name && name.isNotEmpty()

        if (nameChanged) {
            renameShortcut(name)
        }

        val renamingSuccess =
            !nameChanged || File(shortcut.file.parent, "$name.desktop").exists()

        if (renamingSuccess) {
            var hasContainerOverride = false

            // Screen size
            val screenSize = getScreenSizeFromState()
            hasContainerOverride =
                hasContainerOverride or saveOverride("screenSize", screenSize, container.getScreenSize())

            // Graphics driver
            val graphicsDriver = getIdentifierFromEntries(
                state.graphicsDriverEntries.value, state.selectedGraphicsDriver.intValue
            )
            hasContainerOverride =
                hasContainerOverride or saveOverride("graphicsDriver", graphicsDriver, container.getGraphicsDriver())

            val graphicsDriverConfig = buildGraphicsDriverConfigFromState()
            hasContainerOverride = hasContainerOverride or saveOverride(
                "graphicsDriverConfig", graphicsDriverConfig, container.getGraphicsDriverConfig()
            )

            // DX Wrapper
            val dxwrapper = getIdentifierFromEntries(
                state.dxWrapperEntries.value, state.selectedDxWrapper.intValue
            )
            hasContainerOverride =
                hasContainerOverride or saveOverride("dxwrapper", dxwrapper, container.getDXWrapper())

            val dxwrapperConfig = if (dxwrapper.contains("dxvk"))
                buildDxvkConfigFromState() else buildWineD3DConfigFromState()
            hasContainerOverride = hasContainerOverride or saveOverride(
                "dxwrapperConfig", dxwrapperConfig, container.getDXWrapperConfig()
            )

            // Audio
            val audioDriver = getIdentifierFromEntries(
                state.audioDriverEntries.value, state.selectedAudioDriver.intValue
            )
            hasContainerOverride =
                hasContainerOverride or saveOverride("audioDriver", audioDriver, container.getAudioDriver())

            // Emulators
            val emulator = getIdentifierFromEntries(
                state.emulator32Entries.value, state.selectedEmulator.intValue
            )
            val emulator64 = getIdentifierFromEntries(
                state.emulator64Entries.value, state.selectedEmulator64.intValue
            )
            hasContainerOverride =
                hasContainerOverride or saveOverride("emulator", emulator, container.getEmulator())
            hasContainerOverride =
                hasContainerOverride or saveOverride("emulator64", emulator64, container.getEmulator64())

            // MIDI
            val midiSoundFontEntries = state.midiSoundFontEntries.value
            val midiIdx = state.selectedMidiSoundFont.intValue
            val midiSoundFont =
                if (midiIdx <= 0 || midiIdx >= midiSoundFontEntries.size) ""
                else midiSoundFontEntries[midiIdx]
            hasContainerOverride =
                hasContainerOverride or saveOverride("midiSoundFont", midiSoundFont, container.getMIDISoundFont())

            // LC_ALL
            hasContainerOverride =
                hasContainerOverride or saveOverride("lc_all", state.lcAll.value, container.getLC_ALL())

            // Fullscreen stretched
            hasContainerOverride = hasContainerOverride or saveOverride(
                "fullscreenStretched",
                if (state.fullscreenStretched.value) "1" else "0",
                if (container.isFullscreenStretched) "1" else "0"
            )

            // Win components
            val wincomponents = buildWinComponentsString()
            hasContainerOverride =
                hasContainerOverride or saveOverride("wincomponents", wincomponents, container.getWinComponents())

            // Env vars
            val envVarsStr = buildEnvVarsString()
            hasContainerOverride =
                hasContainerOverride or saveOverride("envVars", envVarsStr, container.getEnvVars())

            // FEXCore
            val fexcoreVersionEntries = state.fexcoreVersionEntries.value
            val fexcoreVersionIdx = state.selectedFexcoreVersion.intValue
            val fexcoreVersion =
                if (fexcoreVersionIdx in fexcoreVersionEntries.indices) fexcoreVersionEntries[fexcoreVersionIdx] else ""
            hasContainerOverride = hasContainerOverride or saveOverride(
                "fexcoreVersion", fexcoreVersion, container.getFEXCoreVersion()
            )

            val fexcorePreset =
                if (state.selectedFexcorePreset.intValue in fexcorePresetIds.indices)
                    fexcorePresetIds[state.selectedFexcorePreset.intValue]
                else FEXCorePreset.COMPATIBILITY
            hasContainerOverride = hasContainerOverride or saveOverride(
                "fexcorePreset", fexcorePreset, container.getFEXCorePreset()
            )

            // Box64
            val box64VersionEntries = state.box64VersionEntries.value
            val box64VersionIdx = state.selectedBox64Version.intValue
            val box64Version =
                if (box64VersionIdx in box64VersionEntries.indices) box64VersionEntries[box64VersionIdx] else ""
            hasContainerOverride = hasContainerOverride or saveOverride(
                "box64Version", box64Version, container.getBox64Version()
            )

            val box64Preset =
                if (state.selectedBox64Preset.intValue in box64PresetIds.indices)
                    box64PresetIds[state.selectedBox64Preset.intValue]
                else Box64Preset.COMPATIBILITY
            hasContainerOverride = hasContainerOverride or saveOverride(
                "box64Preset", box64Preset, container.getBox64Preset()
            )

            // Startup selection
            val startupSelection = state.selectedStartupSelection.intValue
            hasContainerOverride = hasContainerOverride or saveOverride(
                "startupSelection",
                startupSelection.toString(),
                container.getStartupSelection().toInt().toString()
            )

            // Controls profile
            val profiles = inputControlsManager.getProfiles(true)
            val controlsProfile =
                if (state.selectedControlsProfile.intValue > 0)
                    profiles[state.selectedControlsProfile.intValue - 1].id
                else 0
            shortcut.putExtra(
                "controlsProfile",
                if (controlsProfile > 0) controlsProfile.toString() else null
            )

            // CPU list
            val cpuList = buildCpuListString(state.cpuChecked.value)
            hasContainerOverride =
                hasContainerOverride or saveOverride("cpuList", cpuList, container.getCPUList(true))

            // CPU list (WoW64)
            val cpuListWoW64 = buildCpuListString(state.cpuCheckedWoW64.value)
            hasContainerOverride =
                hasContainerOverride or saveOverride("cpuListWoW64", cpuListWoW64, container.getCPUListWoW64(true))

            // Input type
            var finalInputType = 0
            if (state.enableXInput.value) finalInputType =
                finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
            if (state.enableDInput.value) finalInputType =
                finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
            finalInputType = finalInputType or (
                if (state.selectedDInputMapperType.intValue == 0)
                    WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()
                else WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt()
            )
            hasContainerOverride = hasContainerOverride or saveOverride(
                "inputType",
                finalInputType.toString(),
                container.getInputType().toString()
            )

            // Exclusive Input — flip hasContainerOverride so runtime's
            // getShortcutSetting doesn't mask the extra via container-defaults.
            val disableXinputValue = if (state.disableXInput.value) "1" else null
            shortcut.putExtra("disableXinput", disableXinputValue)
            if (disableXinputValue != null) hasContainerOverride = true

            // Touchscreen mode
            shortcut.putExtra(
                "simTouchScreen",
                if (state.simTouchScreen.value) "1" else "0"
            )

            // Launch EXE path
            val launchExePath = state.launchExePath.value
            if (launchExePath.isNotEmpty()) {
                shortcut.putExtra("launch_exe_path", launchExePath)
                val gameSource = shortcut.getExtra("game_source", "")
                if (gameSource == "CUSTOM") {
                    shortcut.putExtra("custom_exe", launchExePath)
                }
            }

            // Exec args
            val execArgs = state.execArgs.value
            hasContainerOverride = hasContainerOverride or saveOverride(
                "execArgs", execArgs, container.getExecArgs()
            )

            // Refresh rate
            val refreshRateEntries = state.refreshRateEntries.value
            val refreshIdx = state.selectedRefreshRate.intValue
            if (refreshIdx in refreshRateEntries.indices) {
                val selectedRate =
                    RefreshRateUtils.parseRefreshRateLabel(refreshRateEntries[refreshIdx])
                if (selectedRate <= 0) {
                    shortcut.putExtra("refreshRate", null)
                } else {
                    shortcut.putExtra("refreshRate", selectedRate.toString())
                }
            }

            // Show FPS
            hasContainerOverride = hasContainerOverride or saveOverride(
                "showFPS",
                if (state.showFPS.value) "1" else "0",
                if (container.isShowFPS) "1" else "0"
            )

            // Desktop Theme — stored as compound "THEME,TYPE,COLOR" string
            if (state.desktopThemeEntries.value.isNotEmpty()) {
                val desktopThemeEntries = state.desktopThemeEntries.value
                val dtIdx = state.selectedDesktopTheme.intValue
                val selectedLabel = if (dtIdx in desktopThemeEntries.indices) desktopThemeEntries[dtIdx] else ""
                val themeName = selectedLabel.uppercase()
                // Preserve existing compound value, only replace the theme portion
                val existing = getShortcutSetting("desktopTheme", container.getDesktopTheme())
                val parts = existing.split(",").toMutableList()
                if (parts.isNotEmpty()) parts[0] = themeName
                val desktopTheme = parts.joinToString(",")
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "desktopTheme", desktopTheme, container.getDesktopTheme()
                )
            }

            // Steam options
            if (state.isSteamGame.value) {
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "useColdClient",
                    if (state.useColdClient.value) "1" else "0",
                    if (container.isUseColdClient) "1" else "0"
                )
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "launchRealSteam",
                    if (state.launchRealSteam.value) "1" else "0",
                    if (container.isLaunchRealSteam) "1" else "0"
                )
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "useSteamInput",
                    if (state.useSteamInput.value) "1" else "0",
                    container.getExtra("useSteamInput", "0")
                )
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "forceDlc",
                    if (state.forceDlc.value) "1" else "0",
                    if (container.isForceDlc) "1" else "0"
                )
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "steamOfflineMode",
                    if (state.steamOfflineMode.value) "1" else "0",
                    if (container.isSteamOfflineMode) "1" else "0"
                )
                hasContainerOverride = hasContainerOverride or saveOverride(
                    "unpackFiles",
                    if (state.unpackFiles.value) "1" else "0",
                    if (container.isUnpackFiles) "1" else "0"
                )

                val steamTypeEntries = state.steamTypeEntries.value
                val stIdx = state.selectedSteamType.intValue
                if (stIdx in steamTypeEntries.indices) {
                    hasContainerOverride = hasContainerOverride or saveOverride(
                        "steamType",
                        steamTypeEntries[stIdx],
                        container.getSteamType()
                    )
                }
            }

            // Container defaults flag
            shortcut.putExtra(
                EXTRA_USE_CONTAINER_DEFAULTS,
                if (hasContainerOverride) "0" else "1"
            )

            Log.d(
                TAG,
                "Saving shortcut name='${shortcut.name}' path='${shortcut.path}'" +
                    " usesContainerDefaults=${if (hasContainerOverride) "0" else "1"}" +
                    " box64Preset='${shortcut.getExtra("box64Preset")}'" +
                    " fexcorePreset='${shortcut.getExtra("fexcorePreset")}'" +
                    " wineVersion='${shortcut.getExtra("wineVersion")}'" +
                    " graphicsDriver='${shortcut.getExtra("graphicsDriver")}'" +
                    " graphicsDriverConfig='${shortcut.getExtra("graphicsDriverConfig")}'" +
                    " dxwrapper='${shortcut.getExtra("dxwrapper")}'" +
                    " dxwrapperConfig='${shortcut.getExtra("dxwrapperConfig")}'" +
                    " audioDriver='${shortcut.getExtra("audioDriver")}'" +
                    " emulator='${shortcut.getExtra("emulator")}'" +
                    " screenSize='${shortcut.getExtra("screenSize")}'" +
                    " startupSelection='${shortcut.getExtra("startupSelection")}'" +
                    " envVars='${shortcut.getExtra("envVars")}'" +
                    " cpuList='${shortcut.getExtra("cpuList")}'" +
                    " cpuListWoW64='${shortcut.getExtra("cpuListWoW64")}'"
            )

            // Container change
            val originalContainer = shortcut.container
            if (container.id != originalContainer.id) {
                shortcut.putExtra("container_id", container.id.toString())
                shortcut.putExtra("cloud_force_download", "1")
                shortcut.saveData()

                val newDesktopDir = container.getDesktopDir()
                if (!newDesktopDir.exists()) newDesktopDir.mkdirs()
                val newShortcutFile = File(newDesktopDir, shortcut.file.name)
                com.winlator.cmod.core.FileUtils.copy(shortcut.file, newShortcutFile)
                shortcut.file.delete()
            } else {
                shortcut.saveData()
            }
        }
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    private fun addShortcutToScreen(shortcut: Shortcut): Boolean {
        if (shortcut.getExtra("uuid").isEmpty()) shortcut.genUUID()
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        val shortcutIcon = if (shortcut.icon != null)
            Icon.createWithBitmap(shortcut.icon)
        else Icon.createWithResource(context, R.drawable.icon_shortcut)

        val intent = Intent(context, XServerDisplayActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("container_id", shortcut.container.id)
            putExtra("shortcut_path", shortcut.file.path)
        }
        val info = ShortcutInfo.Builder(context, shortcut.getExtra("uuid"))
            .setShortLabel(shortcut.name)
            .setLongLabel(shortcut.name)
            .setIcon(shortcutIcon)
            .setIntent(intent)
            .build()
        return shortcutManager.requestPinShortcut(info, null)
    }

    private fun resolveInitialLaunchExePath(): String {
        val storedPath = shortcut.getExtra("launch_exe_path")
        if (storedPath.isNotEmpty()) return storedPath

        val gameSource = shortcut.getExtra("game_source", "")
        if (gameSource == "CUSTOM") {
            val customExe = shortcut.getExtra("custom_exe")
            if (customExe.isNotEmpty()) return customExe
        }

        return ""
    }

    private fun getShortcutSetting(key: String, containerValue: String): String {
        return shortcut.getSettingExtra(key, containerValue)
    }

    private fun getIdentifierFromEntries(entries: List<String>, index: Int): String {
        return if (index in entries.indices) StringUtils.parseIdentifier(entries[index]) else ""
    }

    private fun selectByIdentifier(
        entries: List<String>,
        identifier: String,
        target: androidx.compose.runtime.MutableIntState
    ) {
        val idx =
            entries.indexOfFirst { StringUtils.parseIdentifier(it) == identifier }
        target.intValue = if (idx >= 0) idx else 0
    }

    private fun selectByValue(
        entries: List<String>,
        value: String,
        target: androidx.compose.runtime.MutableIntState
    ) {
        val idx = entries.indexOfFirst { it == value }
        target.intValue = if (idx >= 0) idx else 0
    }

    private fun saveOverride(
        extraName: String,
        newValue: String,
        containerValue: String
    ): Boolean {
        val normNew = newValue ?: ""
        val normContainer = containerValue ?: ""
        return if (normNew != normContainer) {
            shortcut.putExtra(extraName, normNew)
            true
        } else {
            shortcut.putExtra(extraName, null)
            false
        }
    }

    private fun getScreenSizeFromState(): String {
        val entries = state.screenSizeEntries.value
        val selectedIdx = state.selectedScreenSize.intValue
        if (selectedIdx !in entries.indices) return Container.DEFAULT_SCREEN_SIZE

        val selectedValue = entries[selectedIdx]
        return if (selectedValue.equals("custom", ignoreCase = true)) {
            val w = state.customWidth.value.trim()
            val h = state.customHeight.value.trim()
            if (w.matches(Regex("[0-9]+")) && h.matches(Regex("[0-9]+"))) {
                // Ensure even numbers
                val width = (w.toInt() / 2) * 2
                val height = (h.toInt() / 2) * 2
                "${width}x${height}"
            } else {
                Container.DEFAULT_SCREEN_SIZE
            }
        } else {
            StringUtils.parseIdentifier(selectedValue)
        }
    }

    private fun buildWinComponentsString(): String {
        val parts = mutableListOf<String>()
        for (comp in state.directXComponents.value) {
            parts.add("${comp.key}=${comp.selectedIndex}")
        }
        for (comp in state.generalComponents.value) {
            parts.add("${comp.key}=${comp.selectedIndex}")
        }
        return parts.joinToString(",")
    }

    private fun buildEnvVarsString(): String {
        // Keep the SDL2 keys in sync with the toggle.
        val sdl2Keys = sdl2EnvVars.map { it.first }.toSet()
        val filtered = state.envVars.value.filterNot { it.key in sdl2Keys }
        val merged = if (state.sdl2Compatibility.value) {
            filtered + sdl2EnvVars.map { EnvVarItem(it.first, it.second) }
        } else {
            filtered
        }
        return merged.joinToString(" ") { "${it.key}=${it.value}" }
    }

    private fun buildCpuListString(checked: List<Boolean>): String {
        val allChecked = checked.all { it }
        if (allChecked) return "" // empty means all cores
        return checked.mapIndexedNotNull { i, isChecked ->
            if (isChecked) "$i" else null
        }.joinToString(",")
    }

    private fun buildGraphicsDriverConfigFromState(): String {
        val vulkanVersion = state.gfxVulkanVersionEntries.value.getOrElse(state.gfxSelectedVulkanVersion.intValue) { "1.3" }
        val version = state.gfxDriverVersionEntries.value.getOrElse(state.gfxSelectedDriverVersion.intValue) { "" }
        val blacklisted = state.gfxBlacklistedExtensions.value.joinToString(",")
        val gpuName = state.gfxGpuNameEntries.value.getOrElse(state.gfxSelectedGpuName.intValue) { "Device" }
        val maxDeviceMemory = StringUtils.parseNumber(
            state.gfxMaxDeviceMemoryEntries.value.getOrElse(state.gfxSelectedMaxDeviceMemory.intValue) { "0" }
        )
        val presentMode = state.gfxPresentModeEntries.value.getOrElse(state.gfxSelectedPresentMode.intValue) { "mailbox" }
        val syncFrame = if (state.gfxSyncFrame.value) "1" else "0"
        val disablePresentWait = if (state.gfxDisablePresentWait.value) "1" else "0"
        val resourceType = state.gfxResourceTypeEntries.value.getOrElse(state.gfxSelectedResourceType.intValue) { "auto" }
        val bcnEmulation = state.gfxBcnEmulationEntries.value.getOrElse(state.gfxSelectedBcnEmulation.intValue) { "auto" }
        val bcnEmulationType = state.gfxBcnEmulationTypeEntries.value.getOrElse(state.gfxSelectedBcnEmulationType.intValue) { "compute" }
        val bcnEmulationCache = state.gfxBcnEmulationCacheEntries.value.getOrElse(state.gfxSelectedBcnEmulationCache.intValue) { "0" }

        return "vulkanVersion=$vulkanVersion;version=$version;blacklistedExtensions=$blacklisted;" +
                "maxDeviceMemory=$maxDeviceMemory;presentMode=$presentMode;syncFrame=$syncFrame;" +
                "disablePresentWait=$disablePresentWait;resourceType=$resourceType;" +
                "bcnEmulation=$bcnEmulation;bcnEmulationType=$bcnEmulationType;" +
                "bcnEmulationCache=$bcnEmulationCache;gpuName=$gpuName"
    }

    private fun buildDxvkConfigFromState(): String {
        val entries = state.dxvkVersionEntries.value
        val idx = state.dxvkSelectedVersion.intValue
        val version = if (idx in entries.indices) entries[idx] else DefaultVersion.DXVK
        val framerate = StringUtils.parseNumber(
            state.dxvkFramerateEntries.value.getOrElse(state.dxvkSelectedFramerate.intValue) { "0" }
        )
        val isGplAsync = version.contains("gplasync")
        val isAsync = version.contains("async")
        val async = if (state.dxvkAsync.value && (isAsync || isGplAsync)) "1" else "0"
        val asyncCache = if (state.dxvkAsyncCache.value && isGplAsync) "1" else "0"

        val vkd3dEntries = state.dxvkVkd3dVersionEntries.value
        val vkd3dIdx = state.dxvkSelectedVkd3dVersion.intValue
        val vkd3dVersion = if (vkd3dIdx in vkd3dEntries.indices) vkd3dEntries[vkd3dIdx] else "None"

        val vkd3dLevel = state.dxvkVkd3dFeatureLevelEntries.value.getOrElse(state.dxvkSelectedVkd3dFeatureLevel.intValue) { "12_0" }
        val ddrawWrapper = StringUtils.parseIdentifier(
            state.dxvkDdrawWrapperEntries.value.getOrElse(state.dxvkSelectedDdrawWrapper.intValue) { Container.DEFAULT_DDRAWRAPPER }
        )

        return "version=$version,framerate=$framerate,async=$async,asyncCache=$asyncCache," +
                "vkd3dVersion=$vkd3dVersion,vkd3dLevel=$vkd3dLevel,ddrawrapper=$ddrawWrapper"
    }

    private fun loadGraphicsDriverConfigState(container: Container = shortcut.container) {
        val configStr = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("graphicsDriverConfig", container.getGraphicsDriverConfig())
        else
            container.getGraphicsDriverConfig()
        val config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(configStr)

        // Load dropdown entries from resource arrays
        state.gfxVulkanVersionEntries.value = context.resources.getStringArray(R.array.vulkan_version_entries).toList()
        state.gfxMaxDeviceMemoryEntries.value = context.resources.getStringArray(R.array.device_memory_entries).toList()
        state.gfxPresentModeEntries.value = context.resources.getStringArray(R.array.present_mode_entries).toList()
        state.gfxResourceTypeEntries.value = context.resources.getStringArray(R.array.resource_type_entries).toList()
        state.gfxBcnEmulationEntries.value = context.resources.getStringArray(R.array.bcn_emulation_entries).toList()
        state.gfxBcnEmulationTypeEntries.value = context.resources.getStringArray(R.array.bcn_emulation_type_entries).toList()
        state.gfxBcnEmulationCacheEntries.value = context.resources.getStringArray(R.array.bcn_emulation_cache_entries).toList()

        // Load GPU names
        val gpuNames = mutableListOf("Device")
        try {
            val gpuNameList = com.winlator.cmod.core.FileUtils.readString(context, "gpu_cards.json")
            if (!gpuNameList.isNullOrEmpty()) {
                val jarray = org.json.JSONArray(gpuNameList)
                for (i in 0 until jarray.length()) {
                    gpuNames.add(jarray.getJSONObject(i).getString("name"))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading gpu_cards.json", e)
        }
        state.gfxGpuNameEntries.value = gpuNames

        // Load driver versions (will be populated after contents sync)
        loadGraphicsDriverVersions(container)

        // Set selections from config
        selectByValue(state.gfxVulkanVersionEntries.value, config["vulkanVersion"] ?: "1.3", state.gfxSelectedVulkanVersion)
        selectByValue(state.gfxGpuNameEntries.value, config["gpuName"] ?: "Device", state.gfxSelectedGpuName)
        selectByNumber(state.gfxMaxDeviceMemoryEntries.value, config["maxDeviceMemory"] ?: "0", state.gfxSelectedMaxDeviceMemory)
        selectByValue(state.gfxPresentModeEntries.value, config["presentMode"] ?: "mailbox", state.gfxSelectedPresentMode)
        selectByValue(state.gfxResourceTypeEntries.value, config["resourceType"] ?: "auto", state.gfxSelectedResourceType)
        selectByValue(state.gfxBcnEmulationEntries.value, config["bcnEmulation"] ?: "auto", state.gfxSelectedBcnEmulation)
        selectByValue(state.gfxBcnEmulationTypeEntries.value, config["bcnEmulationType"] ?: "compute", state.gfxSelectedBcnEmulationType)
        selectByValue(state.gfxBcnEmulationCacheEntries.value, config["bcnEmulationCache"] ?: "0", state.gfxSelectedBcnEmulationCache)

        state.gfxSyncFrame.value = config["syncFrame"] == "1"
        state.gfxDisablePresentWait.value = config["disablePresentWait"] == "1"

        // Update version display
        state.graphicsDriverVersion.value = config["version"] ?: ""
    }

    private fun loadGraphicsDriverVersions(container: Container = shortcut.container) {
        val versions = mutableListOf<String>()
        try {
            val defaults = context.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries)
            for (ver in defaults) {
                try {
                    if (com.winlator.cmod.core.GPUInformation.isDriverSupported(ver, context))
                        versions.add(ver)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error checking driver support: $ver", e)
                }
            }
            try {
                val adrenoManager = com.winlator.cmod.contents.AdrenotoolsManager(context)
                val installed = adrenoManager.enumarateInstalledDrivers()
                if (installed != null) versions.addAll(installed)
            } catch (e: Throwable) {
                Log.w(TAG, "Error loading Adrenotools drivers", e)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading wrapper versions", e)
        }
        if (versions.isEmpty()) versions.add("System")

        state.gfxDriverVersionEntries.value = versions

        // Set initial selection from config
        val configStr = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("graphicsDriverConfig", container.getGraphicsDriverConfig())
        else
            container.getGraphicsDriverConfig()
        val config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(configStr)
        val initialVersion = config["version"] ?: ""
        if (initialVersion.isNotEmpty()) {
            val idx = versions.indexOfFirst { it.equals(initialVersion, ignoreCase = true) }
            if (idx >= 0) state.gfxSelectedDriverVersion.intValue = idx
        }

        // Load extensions for the currently selected version
        loadExtensionsForVersion(state.gfxSelectedDriverVersion.intValue)
    }

    private fun loadExtensionsForVersion(versionIndex: Int) {
        val versions = state.gfxDriverVersionEntries.value
        val version = versions.getOrElse(versionIndex) { return }
        try {
            val extensions = com.winlator.cmod.core.GPUInformation.enumerateExtensions(version, context)
            if (extensions != null) {
                state.gfxAvailableExtensions.value = extensions.toList()

                // On initial load, set blacklisted from config; on version change, clear blacklist
                val configStr = getShortcutSetting("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig())
                val config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(configStr)
                val savedVersion = config["version"] ?: ""
                if (version == savedVersion) {
                    val bl = config["blacklistedExtensions"] ?: ""
                    state.gfxBlacklistedExtensions.value = if (bl.isNotEmpty()) bl.split(",").toSet() else emptySet()
                } else {
                    state.gfxBlacklistedExtensions.value = emptySet()
                }
            } else {
                state.gfxAvailableExtensions.value = emptyList()
                state.gfxBlacklistedExtensions.value = emptySet()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading extensions for $version", e)
            state.gfxAvailableExtensions.value = emptyList()
            state.gfxBlacklistedExtensions.value = emptySet()
        }
    }

    private fun loadDxvkConfigState(container: Container = shortcut.container) {
        val configStr = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("dxwrapperConfig", container.getDXWrapperConfig())
        else
            container.getDXWrapperConfig()
        val config = DXVKConfigDialog.parseConfig(configStr)

        // Feature levels
        state.dxvkVkd3dFeatureLevelEntries.value = DXVKConfigDialog.VKD3D_FEATURE_LEVEL.toList()

        // DDraw wrapper and framerate from resources
        state.dxvkDdrawWrapperEntries.value = context.resources.getStringArray(R.array.ddrawrapper_entries).toList()
        state.dxvkFramerateEntries.value = context.resources.getStringArray(R.array.dxvk_framerate_entries).toList()

        // Load DXVK versions
        loadDxvkVersions(container)

        // Load VKD3D versions
        loadVkd3dVersions(container)

        // Set selections from config
        selectByIdentifier(state.dxvkVkd3dFeatureLevelEntries.value, config.get("vkd3dLevel"), state.dxvkSelectedVkd3dFeatureLevel)
        selectByIdentifier(state.dxvkDdrawWrapperEntries.value, config.get("ddrawrapper"), state.dxvkSelectedDdrawWrapper)
        selectByIdentifier(state.dxvkFramerateEntries.value, config.get("framerate"), state.dxvkSelectedFramerate)

        state.dxvkAsync.value = config.get("async") == "1"
        state.dxvkAsyncCache.value = config.get("asyncCache") == "1"
    }

    private fun loadDxvkVersions(container: Container = shortcut.container) {
        val originalItems = context.resources.getStringArray(R.array.dxvk_version_entries).toMutableList()

        for (profile in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK)) {
            val entryName = ContentsManager.getEntryName(profile)
            val firstDash = entryName.indexOf('-')
            originalItems.add(entryName.substring(firstDash + 1))
        }

        // Remove arm64ec items if not applicable
        if (!isArm64EC) {
            originalItems.removeAll { it.contains("arm64ec") }
        }

        state.dxvkVersionEntries.value = originalItems

        // Set selection from config
        val configStr = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("dxwrapperConfig", container.getDXWrapperConfig())
        else
            container.getDXWrapperConfig()
        val config = DXVKConfigDialog.parseConfig(configStr)
        selectByIdentifier(originalItems, config.get("version"), state.dxvkSelectedVersion)
    }

    private fun loadVkd3dVersions(container: Container = shortcut.container) {
        val items = mutableListOf<String>()
        val predefined = context.resources.getStringArray(R.array.vkd3d_version_entries)
        items.addAll(predefined)

        // Build identifiers matching VKD3DVersionItem format: "verName-verCode" for profiles
        for (profile in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D)) {
            val identifier = profile.verName + "-" + profile.verCode
            items.add(identifier)
        }

        state.dxvkVkd3dVersionEntries.value = items

        // Set selection from config
        val configStr = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("dxwrapperConfig", container.getDXWrapperConfig())
        else
            container.getDXWrapperConfig()
        val config = DXVKConfigDialog.parseConfig(configStr)
        selectByIdentifier(items, config.get("vkd3dVersion"), state.dxvkSelectedVkd3dVersion)
    }

    private fun selectByNumber(
        entries: List<String>,
        number: String,
        target: androidx.compose.runtime.MutableIntState
    ) {
        val idx = entries.indexOfFirst {
            StringUtils.parseNumber(it) == number
        }
        target.intValue = if (idx >= 0) idx else 0
    }

    private fun handleDxvkVkd3dVersionChanged(versionIndex: Int) {
        val vkd3dEntries = state.dxvkVkd3dVersionEntries.value
        val selectedVkd3d = if (versionIndex in vkd3dEntries.indices) vkd3dEntries[versionIndex] else "None"

        if (selectedVkd3d != "None") {
            // Filter DXVK versions to major >= 2
            val allVersions = state.dxvkVersionEntries.value
            val semver = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
            val filtered = allVersions.filter { v ->
                val match = semver.find(v)
                if (match != null) {
                    val major = match.groupValues[1].toIntOrNull() ?: 0
                    major >= 2
                } else true
            }
            state.dxvkVersionEntries.value = filtered

            // Re-select current or default
            val currentDxvk = state.dxvkVersionEntries.value.getOrElse(state.dxvkSelectedVersion.intValue) { "" }
            val curMajor = semver.find(currentDxvk)?.groupValues?.get(1)?.toIntOrNull()
            if (curMajor != null && curMajor >= 2) {
                selectByIdentifier(filtered, currentDxvk, state.dxvkSelectedVersion)
            } else {
                selectByIdentifier(filtered, DefaultVersion.DXVK, state.dxvkSelectedVersion)
            }
        } else {
            // Reload all DXVK versions
            loadDxvkVersions()
        }
    }

    private fun handleContainerChanged(containerIndex: Int) {
        if (containerIndex !in containerList.indices) return
        val newContainer = containerList[containerIndex]

        val wineVersionStr = newContainer.getWineVersion()
        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersionStr)
        isArm64EC = wineInfo.isArm64EC
        state.wineVersionDisplay.value = formatWineVersionDisplay(wineInfo)
        rebuildEmulatorLists()

        selectByIdentifier(
            state.emulator32Entries.value,
            newContainer.getEmulator(),
            state.selectedEmulator
        )
        selectByIdentifier(
            state.emulator64Entries.value,
            newContainer.getEmulator64(),
            state.selectedEmulator64
        )

        state.emulatorsEnabled.value = true

        loadBox64Versions(newContainer)
        loadFexcoreVersions(newContainer)
        loadBox64Presets(newContainer)
        loadFexcorePresets(newContainer)
        updateEmulatorFrameVisibility()

        // Reset container-derived state to the new container. Shortcut-only
        // fields (name, launchExePath, execArgs, refreshRate, controlsProfile,
        // disableXInput, simTouchScreen) travel with the shortcut and are not
        // touched here.
        applyContainerDefaultsToState(newContainer)
        loadGraphicsDriverConfigState(newContainer)
        loadDxvkConfigState(newContainer)
        loadWineD3DConfigState(newContainer)
    }

    private fun applyContainerDefaultsToState(container: Container) {
        selectScreenSize(container.getScreenSize())

        selectByIdentifier(
            state.graphicsDriverEntries.value,
            container.getGraphicsDriver(),
            state.selectedGraphicsDriver
        )
        selectByIdentifier(
            state.dxWrapperEntries.value,
            container.getDXWrapper(),
            state.selectedDxWrapper
        )
        selectByIdentifier(
            state.audioDriverEntries.value,
            container.getAudioDriver(),
            state.selectedAudioDriver
        )

        val midiFont = container.getMIDISoundFont()
        val midiEntries = state.midiSoundFontEntries.value
        if (midiFont.isEmpty()) {
            state.selectedMidiSoundFont.intValue = 0
        } else {
            val idx = midiEntries.indexOfFirst { it == midiFont }
            state.selectedMidiSoundFont.intValue = if (idx >= 0) idx else 0
        }

        state.lcAll.value = container.getLC_ALL()
        state.fullscreenStretched.value = container.isFullscreenStretched
        state.showFPS.value = container.isShowFPS

        val startupEntries = state.startupSelectionEntries.value
        state.selectedStartupSelection.intValue = container.getStartupSelection().toInt()
            .coerceIn(0, (startupEntries.size - 1).coerceAtLeast(0))

        // Desktop theme is stored as compound "THEME,TYPE,COLOR".
        val desktopThemeArr = state.desktopThemeEntries.value
        if (desktopThemeArr.isNotEmpty()) {
            val themePart = container.getDesktopTheme().split(",").firstOrNull()?.trim() ?: ""
            val themeIdx = desktopThemeArr.indexOfFirst { it.equals(themePart, ignoreCase = true) }
            state.selectedDesktopTheme.intValue = if (themeIdx >= 0) themeIdx else 0
        }

        val directX = mutableListOf<WinComponentItem>()
        val general = mutableListOf<WinComponentItem>()
        for (component in KeyValueSet(container.getWinComponents())) {
            val key = component[0]
            val value = component[1]
            val label = StringUtils.getString(context, key) ?: key
            val selectedIdx = try { Integer.parseInt(value) } catch (e: NumberFormatException) { 0 }
            val item = WinComponentItem(key, label, selectedIdx)
            if (key.startsWith("direct")) directX.add(item) else general.add(item)
        }
        state.directXComponents.value = directX
        state.generalComponents.value = general

        val envVars = EnvVars(container.getEnvVars() ?: Container.DEFAULT_ENV_VARS)
        val items = mutableListOf<EnvVarItem>()
        for (key in envVars) items.add(EnvVarItem(key, envVars.get(key)))
        state.sdl2Compatibility.value = envVars.get("SDL_XINPUT_ENABLED") == "1"
        state.envVars.value = if (state.sdl2Compatibility.value) {
            items.filterNot { item -> sdl2EnvVars.any { it.first == item.key } }
        } else items

        val cpuCount = state.cpuCount.intValue
        state.cpuChecked.value = parseCpuList(container.getCPUList(true), cpuCount)
        state.cpuCheckedWoW64.value = parseCpuList(container.getCPUListWoW64(true), cpuCount)

        val inputType = container.getInputType().toInt()
        state.enableXInput.value =
            (inputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) == WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        state.enableDInput.value =
            (inputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) == WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
        state.selectedDInputMapperType.intValue =
            if ((inputType and WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()) 0 else 1
        // Exclusive Input off forces both APIs on.
        if (!state.disableXInput.value) {
            state.enableXInput.value = true
            state.enableDInput.value = true
        }

        if (state.isSteamGame.value) {
            state.useColdClient.value = container.isUseColdClient
            state.launchRealSteam.value = container.isLaunchRealSteam
            state.forceDlc.value = container.isForceDlc
            state.steamOfflineMode.value = container.isSteamOfflineMode
            state.unpackFiles.value = container.isUnpackFiles
            state.useSteamInput.value = container.getExtra("useSteamInput", "0") == "1"
            val steamTypeArr = state.steamTypeEntries.value
            if (steamTypeArr.isNotEmpty()) {
                selectByValue(steamTypeArr, container.getSteamType(), state.selectedSteamType)
            }
        }
    }

    private fun parseCpuList(cpuList: String, cpuCount: Int): List<Boolean> {
        val checked = MutableList(cpuCount) { true }
        if (cpuList.isNotEmpty()) {
            for (i in checked.indices) checked[i] = false
            cpuList.split(",").forEach { cpuStr ->
                val idx = cpuStr.trim().replace("CPU", "").toIntOrNull()
                if (idx != null && idx in checked.indices) checked[idx] = true
            }
        }
        return checked
    }

    private fun loadWineD3DConfigState(container: Container = shortcut.container) {
        val configStr = if (shouldUseShortcutOverrides(container))
            getShortcutSetting("dxwrapperConfig", container.getDXWrapperConfig())
        else
            container.getDXWrapperConfig()
        val config = WineD3DConfigDialog.parseConfig(configStr)

        // Video memory size from resources
        state.wined3dVideoMemorySizeEntries.value =
            context.resources.getStringArray(R.array.video_memory_size_entries).toList()

        // GPU names from gpu_cards.json
        val gpuNames = mutableListOf<String>()
        try {
            val gpuNameList = com.winlator.cmod.core.FileUtils.readString(context, "gpu_cards.json")
            if (!gpuNameList.isNullOrEmpty()) {
                val jarray = org.json.JSONArray(gpuNameList)
                for (i in 0 until jarray.length()) {
                    gpuNames.add(jarray.getJSONObject(i).getString("name"))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading gpu_cards.json for WineD3D", e)
        }
        state.wined3dGpuNameEntries.value = gpuNames

        // Set selections from config
        state.wined3dSelectedCsmt.intValue = if (config.get("csmt") == "3") 0 else 1
        state.wined3dSelectedStrictShaderMath.intValue = if (config.get("strict_shader_math") == "1") 0 else 1
        selectByValue(state.wined3dOffscreenRenderingModeEntries.value, config.get("OffscreenRenderingMode"), state.wined3dSelectedOffscreenRenderingMode)
        selectByValue(state.wined3dGpuNameEntries.value, config.get("gpuName"), state.wined3dSelectedGpuName)
        selectByValue(state.wined3dRendererEntries.value, config.get("renderer"), state.wined3dSelectedRenderer)
        selectByNumber(state.wined3dVideoMemorySizeEntries.value, config.get("videoMemorySize"), state.wined3dSelectedVideoMemorySize)
    }

    private fun buildWineD3DConfigFromState(): String {
        val csmt = if (state.wined3dSelectedCsmt.intValue == 0) "3" else "0"
        val gpuName = state.wined3dGpuNameEntries.value.getOrElse(state.wined3dSelectedGpuName.intValue) { "" }
        val videoMemorySize = StringUtils.parseNumber(
            state.wined3dVideoMemorySizeEntries.value.getOrElse(state.wined3dSelectedVideoMemorySize.intValue) { "0" }
        )
        val strictShaderMath = if (state.wined3dSelectedStrictShaderMath.intValue == 0) "1" else "0"
        val offscreenRenderingMode = state.wined3dOffscreenRenderingModeEntries.value.getOrElse(
            state.wined3dSelectedOffscreenRenderingMode.intValue
        ) { "fbo" }
        val renderer = state.wined3dRendererEntries.value.getOrElse(state.wined3dSelectedRenderer.intValue) { "gl" }

        return "csmt=$csmt,gpuName=$gpuName,videoMemorySize=$videoMemorySize," +
                "strict_shader_math=$strictShaderMath,OffscreenRenderingMode=$offscreenRenderingMode," +
                "renderer=$renderer"
    }

    private fun renameShortcut(newName: String) {
        val parent = shortcut.file.parentFile
        val oldDesktopFile = shortcut.file
        val newDesktopFile = File(parent, "$newName.desktop")

        if (!newDesktopFile.isFile && oldDesktopFile.renameTo(newDesktopFile)) {
            try {
                val fileField = Shortcut::class.java.getDeclaredField("file")
                fileField.isAccessible = true
                fileField.set(shortcut, newDesktopFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating shortcut file reference", e)
            }

            if (oldDesktopFile.exists()) {
                oldDesktopFile.delete()
            }
        }

        val linkFile = File(parent, "${shortcut.name}.lnk")
        if (linkFile.isFile) {
            val newLinkFile = File(parent, "$newName.lnk")
            if (!newLinkFile.isFile) linkFile.renameTo(newLinkFile)
        }

        fragment?.loadShortcutsList()
        fragment?.updateShortcutOnScreen(
            newName, newName, shortcut.container.id,
            File(parent, "$newName.desktop").absolutePath,
            Icon.createWithBitmap(shortcut.icon),
            shortcut.getExtra("uuid")
        )
    }

    // ------------------------------------------------------------------
    // Show / Dismiss
    // ------------------------------------------------------------------

    fun show() {
        dialog.show()
        dialog.window?.apply {
            val dm = activity.resources.displayMetrics
            val screenWidthDp = dm.widthPixels / dm.density
            val dialogWidthDp = screenWidthDp * 0.88f
            // When the usable width is below the content's compact-layout breakpoint
            // (720dp in GameSettingsContent), the Compose UI switches to stacked tabs
            // with a bottom action bar, which needs more vertical room than the sidebar
            // layout. Give the dialog near-full-height whenever compact layout will
            // kick in; keep the roomier sidebar layout at a comfortable 88% otherwise.
            val isCompactLayout = dialogWidthDp < 720f
            if (screenWidthDp < 600f) {
                // Small screen: most of the display with a comfortable margin.
                val dialogWidth = (dm.widthPixels * 0.96f).toInt()
                val dialogHeight = (dm.heightPixels * 0.90f).toInt()
                setLayout(dialogWidth, dialogHeight)
            } else {
                val dialogWidth = (dm.widthPixels * 0.88f).toInt()
                val heightFactor = if (isCompactLayout) 0.90f else 0.88f
                val dialogHeight = (dm.heightPixels * heightFactor).toInt()
                setLayout(dialogWidth, dialogHeight)
            }

            // Post-attach blur: set flag + radius in one setAttributes call so
            // WindowManager applies them atomically (otherwise blur can flicker).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val params = attributes
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 10
                attributes = params
            }
        }
    }

    fun dismiss() {
        AppUtils.hideKeyboard(activity)
        exePickerLauncher?.unregister()
        dialog.dismiss()
    }

    companion object {
        private const val TAG = "ShortcutSettingsCompose"
        private const val EXTRA_USE_CONTAINER_DEFAULTS = "use_container_defaults"

        /**
         * Creates a minimal `.desktop` file on the preferred game container and returns a
         * [Shortcut] pointing at it. Used when the user taps Settings on a library game
         * that has no shortcut yet. The shortcut is persisted to disk immediately; if the
         * user dismisses the dialog without saving, the file remains (and shows up in the
         * Shortcuts tab from then on).
         *
         * @param source one of "STEAM", "EPIC", "GOG"
         * @param appId  numeric app id (for GOG use the pseudo id)
         * @param gogId  GOG id string — required when `source == "GOG"`, ignored otherwise
         */
        @JvmStatic
        fun createLibraryShortcut(
            context: Context,
            containerManager: ContainerManager,
            source: String,
            appId: Int,
            gogId: String?,
            appName: String,
        ): Shortcut? {
            val container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)
            if (container == null) {
                SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                return null
            }
            val desktopDir = container.desktopDir
            if (!desktopDir.exists()) desktopDir.mkdirs()
            val safeName = appName.replace("/", "_").replace("\\", "_")
            val shortcutFile = File(desktopDir, "$safeName.desktop")
            val iconKey = when (source) {
                "STEAM" -> "steam_icon_$appId"
                "EPIC" -> "epic_icon_$appId"
                "GOG" -> "gog_icon_$gogId"
                else -> ""
            }
            val exec = if (source == "STEAM") {
                "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\""
            } else {
                "wine \"A:\\\\\""
            }
            val sb = StringBuilder()
            sb.append("[Desktop Entry]\n")
            sb.append("Type=Application\n")
            sb.append("Name=$appName\n")
            sb.append("Exec=$exec\n")
            sb.append("Icon=$iconKey\n")
            sb.append("\n[Extra Data]\n")
            sb.append("game_source=$source\n")
            sb.append("app_id=$appId\n")
            if (source == "GOG" && !gogId.isNullOrEmpty()) {
                sb.append("gog_id=$gogId\n")
            }
            sb.append("container_id=${container.id}\n")
            sb.append("use_container_defaults=1\n")
            FileUtils.writeString(shortcutFile, sb.toString())
            return Shortcut(container, shortcutFile)
        }
    }
}
