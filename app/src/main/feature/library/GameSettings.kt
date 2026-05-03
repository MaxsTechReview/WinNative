package com.winlator.cmod.feature.library
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.widget.EnvVarsView
import com.winlator.cmod.shared.ui.widget.chasingBorder
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Black / gray color scheme
// ---------------------------------------------------------------------------
private val BgDeep = Color(0xFF18181D)
private val SidebarBg = Color(0xFF18181D)
private val ContentBg = Color(0xFF18181D)
private val CardSurface = Color(0xFF1C1C2A)
private val CardBorder = Color(0xFF2A2A3A)
private val InputSurface = Color(0xFF171722)
private val InputBorder = Color(0xFF2A2A3A)
private val AccentBlue = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val TextDim = Color(0xFF6E7681)
private val DividerColor = Color(0xFF2A2A3A)
private val CheckBorder = Color(0xFF2A2A3A)
private val TrackInactive = Color(0xFF1C1C2A)
private val ChipSurface = Color(0xFF171722)
private val ChipBorder = Color(0xFF2A2A3A)
private val DangerRed = Color(0xFFFF6B6B)
private val WarningAmber = Color(0xFFFFB74D)
private val SelectableDriveLetters = ('D'..'Y').filter { it != 'E' }.map { "$it" }

private val SettingGroupCorner = 12.dp
private val SettingGroupPadding = 12.dp
private val SettingFieldCorner = 8.dp
private val SettingFieldHorizontalPadding = 12.dp
private val SettingFieldVerticalPadding = 8.dp
private val EnvVarControlHeight = 36.dp
private val SettingItemGap = 10.dp
private val SettingSectionGap = 12.dp
private val SettingTightGap = 4.dp
private val SettingIconSize = 18.dp
private val SettingControlIconSize = 16.dp
private val SettingLabelSize = 11.sp
private val SettingValueSize = 13.sp
private val SettingSectionLabelSize = 12.sp
private val SmartDropdownPressStartInset = 28.dp

@Composable
private fun rememberSmartDropdownOffset(): MutableState<DpOffset> =
    remember { mutableStateOf(DpOffset.Zero) }

@Composable
private fun Modifier.smartDropdownAnchor(
    enabled: Boolean = true,
    offset: MutableState<DpOffset>,
    onOpen: () -> Unit,
): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    return pointerInput(enabled, density, onOpen) {
        detectTapGestures { tapOffset ->
            offset.value =
                with(density) {
                    val tapX = tapOffset.x.toDp()
                    DpOffset(
                        if (tapX > SmartDropdownPressStartInset) tapX - SmartDropdownPressStartInset else 0.dp,
                        0.dp,
                    )
                }
            onOpen()
        }
    }
}

// ---------------------------------------------------------------------------
// Data classes
// ---------------------------------------------------------------------------
data class WinComponentItem(val key: String, val label: String, val selectedIndex: Int)
data class EnvVarItem(val key: String, val value: String)
data class ExtraArgGroup(val header: String, val args: List<String>)
data class DriveItem(
    val letter: String,
    val path: String,
    val canChangeLetter: Boolean = false,
)

// ---------------------------------------------------------------------------
// State holder
// ---------------------------------------------------------------------------
class GameSettingsStateHolder {
    val currentSection = mutableIntStateOf(0)

    // True when editing a Container directly; hides shortcut-only fields
    // and exposes wine version / mouse warp / drives / desktop background.
    val isContainerEditMode = mutableStateOf(false)
    // Wine version dropdown is editable only when creating a new container.
    val wineVersionEditable = mutableStateOf(false)

    // General
    val name = mutableStateOf("")
    val launchExePath = mutableStateOf("")
    val containerEntries = mutableStateOf<List<String>>(emptyList())
    val selectedContainer = mutableIntStateOf(0)
    val screenSizeEntries = mutableStateOf<List<String>>(emptyList())
    val selectedScreenSize = mutableIntStateOf(0)
    val customWidth = mutableStateOf("")
    val customHeight = mutableStateOf("")
    val gameCardArtworkSelected = mutableStateOf(false)
    val gameCardArtworkSummary = mutableStateOf("")
    val gridArtworkSelected = mutableStateOf(false)
    val gridArtworkSummary = mutableStateOf("")
    val carouselArtworkSelected = mutableStateOf(false)
    val carouselArtworkSummary = mutableStateOf("")
    val listArtworkSelected = mutableStateOf(false)
    val listArtworkSummary = mutableStateOf("")
    val refreshRateEntries = mutableStateOf<List<String>>(emptyList())
    val selectedRefreshRate = mutableIntStateOf(0)
    val fpsLimit = mutableIntStateOf(0)

    // Display
    val graphicsDriverEntries = mutableStateOf<List<String>>(emptyList())
    val selectedGraphicsDriver = mutableIntStateOf(0)
    val graphicsDriverVersion = mutableStateOf("")
    val dxWrapperEntries = mutableStateOf<List<String>>(emptyList())
    val selectedDxWrapper = mutableIntStateOf(0)

    // Graphics Driver Configuration (inline card)
    val gfxConfigExpanded = mutableStateOf(false)
    val gfxVulkanVersionEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedVulkanVersion = mutableIntStateOf(0)
    val gfxDriverVersionEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedDriverVersion = mutableIntStateOf(0)
    val gfxAvailableExtensions = mutableStateOf<List<String>>(emptyList())
    val gfxBlacklistedExtensions = mutableStateOf<Set<String>>(emptySet())
    val gfxGpuNameEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedGpuName = mutableIntStateOf(0)
    val gfxMaxDeviceMemoryEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedMaxDeviceMemory = mutableIntStateOf(0)
    val gfxPresentModeEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedPresentMode = mutableIntStateOf(0)
    val gfxResourceTypeEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedResourceType = mutableIntStateOf(0)
    val gfxBcnEmulationEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedBcnEmulation = mutableIntStateOf(0)
    val gfxBcnEmulationTypeEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedBcnEmulationType = mutableIntStateOf(0)
    val gfxBcnEmulationCacheEntries = mutableStateOf<List<String>>(emptyList())
    val gfxSelectedBcnEmulationCache = mutableIntStateOf(0)
    val gfxSyncFrame = mutableStateOf(false)
    val gfxDisablePresentWait = mutableStateOf(false)

    // DXVK Configuration (inline card)
    val dxvkConfigExpanded = mutableStateOf(false)
    val dxvkVkd3dVersionEntries = mutableStateOf<List<String>>(emptyList())
    val dxvkSelectedVkd3dVersion = mutableIntStateOf(0)
    val dxvkVkd3dFeatureLevelEntries = mutableStateOf<List<String>>(emptyList())
    val dxvkSelectedVkd3dFeatureLevel = mutableIntStateOf(0)
    val dxvkVersionEntries = mutableStateOf<List<String>>(emptyList())
    val dxvkSelectedVersion = mutableIntStateOf(0)
    val dxvkAsync = mutableStateOf(false)
    val dxvkAsyncCache = mutableStateOf(false)
    val dxvkDdrawWrapperEntries = mutableStateOf<List<String>>(emptyList())
    val dxvkSelectedDdrawWrapper = mutableIntStateOf(0)

    // WineD3D Configuration (inline card)
    val wined3dConfigExpanded = mutableStateOf(false)
    val wined3dCsmtEntries = mutableStateOf<List<String>>(emptyList())
    val wined3dSelectedCsmt = mutableIntStateOf(0)
    val wined3dGpuNameEntries = mutableStateOf<List<String>>(emptyList())
    val wined3dSelectedGpuName = mutableIntStateOf(0)
    val wined3dVideoMemorySizeEntries = mutableStateOf<List<String>>(emptyList())
    val wined3dSelectedVideoMemorySize = mutableIntStateOf(0)
    val wined3dStrictShaderMathEntries = mutableStateOf<List<String>>(emptyList())
    val wined3dSelectedStrictShaderMath = mutableIntStateOf(0)
    val wined3dOffscreenRenderingModeEntries = mutableStateOf(listOf("fbo", "backbuffer"))
    val wined3dSelectedOffscreenRenderingMode = mutableIntStateOf(0)
    val wined3dRendererEntries = mutableStateOf(listOf("gl", "vulkan", "gdi"))
    val wined3dSelectedRenderer = mutableIntStateOf(0)

    // Audio
    val audioDriverEntries = mutableStateOf<List<String>>(emptyList())
    val selectedAudioDriver = mutableIntStateOf(0)
    val midiSoundFontEntries = mutableStateOf<List<String>>(emptyList())
    val selectedMidiSoundFont = mutableIntStateOf(0)

    // Wine — emulator32/64Entries are wine-arch-filtered views of emulatorEntries
    // used by the 32/64-bit dropdowns; selectedEmulator indexes into
    // emulator32Entries, selectedEmulator64 into emulator64Entries.
    val emulatorEntries = mutableStateOf<List<String>>(emptyList())
    val emulator32Entries = mutableStateOf<List<String>>(emptyList())
    val emulator64Entries = mutableStateOf<List<String>>(emptyList())
    val selectedEmulator = mutableIntStateOf(0)
    val selectedEmulator64 = mutableIntStateOf(0)
    val wineVersionDisplay = mutableStateOf("")
    val emulatorsEnabled = mutableStateOf(true)
    val lcAll = mutableStateOf("")
    val localeOptions = mutableStateOf<List<String>>(emptyList())
    val desktopThemeEntries = mutableStateOf<List<String>>(emptyList())
    val selectedDesktopTheme = mutableIntStateOf(0)

    // Container-only fields. MouseWarpOverride is stored in .wine/user.reg.
    val wineVersionEntries = mutableStateOf<List<String>>(emptyList())
    val selectedWineVersion = mutableIntStateOf(0)
    val mouseWarpOverrideEntries = mutableStateOf<List<String>>(emptyList())
    val selectedMouseWarpOverride = mutableIntStateOf(0)
    val desktopBackgroundTypeEntries = mutableStateOf<List<String>>(emptyList())
    val selectedDesktopBackgroundType = mutableIntStateOf(0)
    val desktopBackgroundColor = mutableStateOf("#0277bd")
    val desktopWallpaperSelected = mutableStateOf(false)
    val drivesList = mutableStateOf<List<DriveItem>>(emptyList())
    // Exclusive Input is a global SharedPreferences flag ("xinput_toggle"),
    // not per-container — tracked here so InputSection can reuse its layout.
    val containerExclusiveInput = mutableStateOf(false)

    // Steam (visible only for Steam games)
    val isSteamGame = mutableStateOf(false)
    val useColdClient = mutableStateOf(false)
    val useSteamInput = mutableStateOf(false)
    val forceDlc = mutableStateOf(false)
    val steamOfflineMode = mutableStateOf(false)
    val unpackFiles = mutableStateOf(false)
    val runtimePatcher = mutableStateOf(false)
    val launchRealSteam = mutableStateOf(false)
    val steamTypeEntries = mutableStateOf<List<String>>(emptyList())
    val selectedSteamType = mutableIntStateOf(0)

    // Components
    val winComponentEntries = mutableStateOf<List<String>>(emptyList())
    val directXComponents = mutableStateOf<List<WinComponentItem>>(emptyList())
    val generalComponents = mutableStateOf<List<WinComponentItem>>(emptyList())

    // Variables
    val envVars = mutableStateOf<List<EnvVarItem>>(emptyList())

    // Input
    val controlsProfileEntries = mutableStateOf<List<String>>(emptyList())
    val selectedControlsProfile = mutableIntStateOf(0)
    val numControllersEntries = mutableStateOf<List<String>>(emptyList())
    val selectedNumControllers = mutableIntStateOf(0)
    val disableXInput = mutableStateOf(false)
    val simTouchScreen = mutableStateOf(false)
    val sdl2Compatibility = mutableStateOf(false)
    val enableXInput = mutableStateOf(false)
    val enableDInput = mutableStateOf(false)
    val dInputMapperTypeEntries = mutableStateOf<List<String>>(emptyList())
    val selectedDInputMapperType = mutableIntStateOf(0)

    // Advanced - Box64
    val showBox64Frame = mutableStateOf(false)
    val box64VersionEntries = mutableStateOf<List<String>>(emptyList())
    val selectedBox64Version = mutableIntStateOf(0)
    val box64PresetEntries = mutableStateOf<List<String>>(emptyList())
    val selectedBox64Preset = mutableIntStateOf(0)

    // Advanced - FEXCore
    val showFexcoreFrame = mutableStateOf(false)
    val fexcoreVersionEntries = mutableStateOf<List<String>>(emptyList())
    val selectedFexcoreVersion = mutableIntStateOf(0)
    val fexcorePresetEntries = mutableStateOf<List<String>>(emptyList())
    val selectedFexcorePreset = mutableIntStateOf(0)

    // Advanced - System
    val startupSelectionEntries = mutableStateOf<List<String>>(emptyList())
    val selectedStartupSelection = mutableIntStateOf(0)
    val execArgs = mutableStateOf("")
    val fullscreenStretched = mutableStateOf(false)

    // Advanced - CPU
    val cpuCount = mutableIntStateOf(Runtime.getRuntime().availableProcessors())
    val cpuChecked = mutableStateOf<List<Boolean>>(
        List(Runtime.getRuntime().availableProcessors()) { true }
    )
    val cpuCheckedWoW64 = mutableStateOf<List<Boolean>>(
        List(Runtime.getRuntime().availableProcessors()) { true }
    )

    // Advanced - Drives
    val drives = mutableStateOf("")

    // Loading state
    val isLoaded = mutableStateOf(false)
}

// ---------------------------------------------------------------------------
// Callbacks
// ---------------------------------------------------------------------------
interface GameSettingsCallbacks {
    fun onConfirm()
    fun onDismiss()
    fun onAddToHomeScreen()
    fun onPickGameCardArtwork() {}
    fun onRemoveGameCardArtwork() {}
    fun onPickGridArtwork() {}
    fun onRemoveGridArtwork() {}
    fun onPickCarouselArtwork() {}
    fun onRemoveCarouselArtwork() {}
    fun onPickListArtwork() {}
    fun onRemoveListArtwork() {}
    fun onOpenArtworkSource() {}
    fun onRemoveEnvVar(index: Int)
    fun onUpdateWinComponent(isDirectX: Boolean, index: Int, newValue: Int)
    fun onSelectExe() {}
    fun onGfxDriverVersionChanged(versionIndex: Int) {}
    fun onDxvkVersionChanged(versionIndex: Int) {}
    fun onDxvkVkd3dVersionChanged(versionIndex: Int) {}
    fun onContainerChanged(containerIndex: Int) {}
    fun onEmulatorChanged() {}
    fun onWineVersionChanged(versionIndex: Int) {}
    fun onAddDrive() {}
    fun onDriveLetterChanged(index: Int, newLetter: String) {}
    fun onRemoveDrive(index: Int) {}
    fun onPickDrivePath(index: Int) {}
    fun onPickWallpaper() {}
}

// ---------------------------------------------------------------------------
// Preset exec args
// ---------------------------------------------------------------------------
private val ExtraArgPresets = listOf(
    ExtraArgGroup(
        "Unity", listOf(
            "-force-d3d9", "-force-d3d11", "-force-d3d12", "-force-vulkan",
            "-force-glcore", "-force-gfx-direct", "-force-d3d11-singlethreaded",
            "-screen-fullscreen 0", "-screen-fullscreen 1", "-popupwindow", "-nolog"
        )
    ),
    ExtraArgGroup(
        "Unreal", listOf(
            "-WINDOWED", "-FULLSCREEN", "-dx11", "-dx12", "-vulkan",
            "-NOSPLASH", "-NOSOUND"
        )
    ),
    ExtraArgGroup(
        "Source", listOf(
            "-sw", "-novid", "-nojoy", "-console", "-nosound"
        )
    ),
    ExtraArgGroup(
        "General", listOf(
            "-windowed", "-fullscreen", "-nointro", "-skipvideos", "-novsync", "/d3d9"
        )
    )
)

// ---------------------------------------------------------------------------
// Sidebar section definitions
// ---------------------------------------------------------------------------
private data class SidebarSection(
    val icon: ImageVector,
    val labelResId: Int
)

// Section IDs (stable across dynamic lists)
private const val SEC_GENERAL = 0
private const val SEC_STEAM = 1
private const val SEC_DISPLAY = 2
private const val SEC_WINE = 4
private const val SEC_COMPONENTS = 5
private const val SEC_VARIABLES = 6
private const val SEC_INPUT = 7
private const val SEC_ADVANCED = 8

private fun buildSections(isSteam: Boolean): List<Pair<Int, SidebarSection>> {
    val list = mutableListOf<Pair<Int, SidebarSection>>()
    list += SEC_GENERAL to SidebarSection(Icons.Outlined.Tune, R.string.settings_general_title)
    if (isSteam) list += SEC_STEAM to SidebarSection(Icons.Outlined.Science, R.string.steam_section_title)
    list += SEC_DISPLAY to SidebarSection(Icons.Outlined.Monitor, R.string.common_ui_graphics)
    list += SEC_ADVANCED to SidebarSection(Icons.Outlined.Settings, R.string.common_ui_advanced)
    list += SEC_INPUT to SidebarSection(Icons.Outlined.SportsEsports, R.string.common_ui_input_controls)
    list += SEC_VARIABLES to SidebarSection(Icons.Outlined.Code, R.string.container_config_variables)
    list += SEC_WINE to SidebarSection(Icons.Outlined.Science, R.string.container_wine_title)
    list += SEC_COMPONENTS to SidebarSection(Icons.Outlined.Extension, R.string.settings_content_components)
    return list
}

// ===================================================================
// Main Content Composable
// ===================================================================
@Composable
fun GameSettingsContent(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    val isSteam by state.isSteamGame
    val sections = remember(isSteam) { buildSections(isSteam) }
    val selectedIdx by state.currentSection
    val currentSectionId = sections.getOrNull(selectedIdx)?.first ?: SEC_GENERAL
    val saveEnabled by state.isLoaded

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(BgDeep)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                title = state.name.value,
                sections = sections.map { it.second },
                currentIndex = selectedIdx,
                onSectionSelected = { state.currentSection.intValue = it },
                saveEnabled = saveEnabled,
                onSave = { callbacks.onConfirm() },
                onCancel = { callbacks.onDismiss() },
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(DividerColor)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(ContentBg)
            ) {
                SectionContent(currentSectionId, state, callbacks)
            }
        }
    }
}

@Composable
private fun SectionContent(
    sectionId: Int,
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    AnimatedContent(
        targetState = sectionId,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInHorizontally(
                animationSpec = tween(220)
            ) { direction * it / 6 } + fadeIn(tween(200)))
                .togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(180)
                    ) { -direction * it / 6 } + fadeOut(tween(120))
                )
        },
        label = "SectionTransition"
    ) { id ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            when (id) {
                SEC_GENERAL -> GeneralSection(state, callbacks)
                SEC_STEAM -> SteamSection(state)
                SEC_DISPLAY -> DisplaySection(state, callbacks)
                SEC_WINE -> WineSection(state, callbacks)
                SEC_COMPONENTS -> ComponentsSection(state, callbacks)
                SEC_VARIABLES -> VariablesSection(state, callbacks)
                SEC_INPUT -> InputSection(state)
                SEC_ADVANCED -> AdvancedSection(state, callbacks)
            }
            Spacer(Modifier.height(SettingSectionGap))
        }
    }
}

// ===================================================================
// Sidebar
// ===================================================================
@Composable
private fun Sidebar(
    title: String,
    sections: List<SidebarSection>,
    currentIndex: Int,
    onSectionSelected: (Int) -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SidebarBg)
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        // Header: shortcut/game title being edited
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = SettingLabelSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Sidebar items
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            sections.forEachIndexed { index, section ->
                SidebarItem(
                    icon = section.icon,
                    label = stringResource(section.labelResId),
                    isSelected = currentIndex == index,
                    onClick = { onSectionSelected(index) }
                )
            }
        }

        // Divider above the action buttons (matches the one under the title)
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor)
        )
        Spacer(Modifier.height(8.dp))

        // Cancel + Save buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .background(CardSurface)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.common_ui_cancel),
                    color = TextSecondary,
                    fontSize = SettingLabelSize,
                    fontWeight = FontWeight.Medium
                )
            }
            SaveButton(
                enabled = saveEnabled,
                onClick = onSave,
                height = 30.dp,
                corner = 8.dp,
                fontSize = SettingLabelSize,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    height: Dp,
    corner: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(corner))
            .border(
                1.dp,
                if (enabled) AccentBlue.copy(alpha = 0.5f) else CardBorder,
                RoundedCornerShape(corner)
            )
            .background(
                if (enabled) AccentBlue.copy(alpha = 0.1f) else CardSurface
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.common_ui_save),
            color = if (enabled) AccentBlue else TextDim,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .chasingBorder(isFocused = isSelected, cornerRadius = 8.dp, borderWidth = 2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) AccentBlue else TextDim,
                modifier = Modifier.size(SettingIconSize)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = SettingValueSize,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// ===================================================================
// Section 0: General
// ===================================================================
@Composable
private fun GeneralSection(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    val isContainer = state.isContainerEditMode.value

    @Composable
    fun ArtworkPickerRow(
        title: String,
        summary: String,
        selected: Boolean,
        onPick: () -> Unit,
        onRemove: () -> Unit,
    ) {
        @Composable
        fun ActionButton(
            text: String,
            tint: Color,
            onClick: () -> Unit,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.08f))
                    .border(1.dp, tint.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                    .clickable { onClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = text,
                    color = tint,
                    fontSize = SettingLabelSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingGroupCorner))
                .background(InputSurface)
                .border(1.dp, InputBorder, RoundedCornerShape(SettingGroupCorner))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = SettingValueSize,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = summary,
                            color = TextSecondary,
                            fontSize = SettingLabelSize,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        text =
                            stringResource(
                                if (selected) {
                                    R.string.shortcuts_library_artwork_change
                                } else {
                                    R.string.shortcuts_library_artwork_set
                                }
                            ),
                        tint = AccentBlue,
                        onClick = onPick
                    )

                    if (selected) {
                        ActionButton(
                            text = stringResource(R.string.common_ui_remove),
                            tint = DangerRed,
                            onClick = onRemove
                        )
                    }
                }
            }
        }
    }

    SettingGroup {
        // Name
        SettingTextField(
            label = stringResource(R.string.common_ui_name),
            value = state.name.value,
            onValueChange = { state.name.value = it }
        )

        if (!isContainer) {
            Spacer(Modifier.height(SettingItemGap))
            Text(
                stringResource(R.string.common_ui_select_exe),
                color = TextSecondary,
                fontSize = SettingLabelSize,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(SettingTightGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SettingFieldCorner))
                    .border(1.dp, InputBorder, RoundedCornerShape(SettingFieldCorner))
                    .background(InputSurface)
                    .clickable { callbacks.onSelectExe() }
                    .padding(horizontal = SettingFieldHorizontalPadding, vertical = SettingFieldVerticalPadding)
            ) {
                Text(
                    text = state.launchExePath.value.ifEmpty { stringResource(R.string.common_ui_select_exe) },
                    color = if (state.launchExePath.value.isEmpty()) TextDim else TextPrimary,
                    fontSize = SettingValueSize,
                    maxLines = 1
                )
            }
        }

        if (!isContainer && state.containerEntries.value.isNotEmpty()) {
            Spacer(Modifier.height(SettingItemGap))
            SettingDropdown(
                label = stringResource(R.string.shortcuts_list_select_a_container),
                entries = state.containerEntries.value,
                selectedIndex = state.selectedContainer.intValue,
                onSelected = {
                    state.selectedContainer.intValue = it
                    callbacks.onContainerChanged(it)
                }
            )
        }

        if (isContainer && state.wineVersionEntries.value.isNotEmpty()) {
            Spacer(Modifier.height(SettingItemGap))
            SettingDropdown(
                label = stringResource(R.string.container_wine_version),
                entries = state.wineVersionEntries.value,
                selectedIndex = state.selectedWineVersion.intValue,
                onSelected = {
                    state.selectedWineVersion.intValue = it
                    callbacks.onWineVersionChanged(it)
                },
                enabled = state.wineVersionEditable.value
            )
        }

        if (!isContainer) {
            Spacer(Modifier.height(SettingItemGap))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentBlue.copy(alpha = 0.08f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .clickable { callbacks.onAddToHomeScreen() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Home,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(SettingIconSize)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.shortcuts_list_add_to_home_screen),
                        color = AccentBlue,
                        fontSize = SettingValueSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (!isContainer) {
        Spacer(Modifier.height(SettingSectionGap))

        SettingGroup {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.shortcuts_library_artwork_title),
                    color = TextSecondary,
                    fontSize = SettingSectionLabelSize,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.08f))
                        .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .clickable { callbacks.onOpenArtworkSource() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(SettingIconSize)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.shortcuts_library_artwork_open_source),
                            color = AccentBlue,
                            fontSize = SettingValueSize,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(SettingItemGap))

            ArtworkPickerRow(
                title = stringResource(R.string.shortcuts_library_artwork_game_card_title),
                summary = state.gameCardArtworkSummary.value,
                selected = state.gameCardArtworkSelected.value,
                onPick = callbacks::onPickGameCardArtwork,
                onRemove = callbacks::onRemoveGameCardArtwork
            )

            Spacer(Modifier.height(SettingItemGap))

            ArtworkPickerRow(
                title = stringResource(R.string.shortcuts_library_artwork_grid_title),
                summary = state.gridArtworkSummary.value,
                selected = state.gridArtworkSelected.value,
                onPick = callbacks::onPickGridArtwork,
                onRemove = callbacks::onRemoveGridArtwork
            )

            Spacer(Modifier.height(SettingItemGap))

            ArtworkPickerRow(
                title = stringResource(R.string.shortcuts_library_artwork_carousel_title),
                summary = state.carouselArtworkSummary.value,
                selected = state.carouselArtworkSelected.value,
                onPick = callbacks::onPickCarouselArtwork,
                onRemove = callbacks::onRemoveCarouselArtwork
            )

            Spacer(Modifier.height(SettingItemGap))

            ArtworkPickerRow(
                title = stringResource(R.string.shortcuts_library_artwork_list_title),
                summary = state.listArtworkSummary.value,
                selected = state.listArtworkSelected.value,
                onPick = callbacks::onPickListArtwork,
                onRemove = callbacks::onRemoveListArtwork
            )
        }
    }

    Spacer(Modifier.height(SettingSectionGap))

    SettingGroup {
        // Screen Size
        SettingDropdown(
            label = stringResource(R.string.container_config_screen_size),
            entries = state.screenSizeEntries.value,
            selectedIndex = state.selectedScreenSize.intValue,
            onSelected = { state.selectedScreenSize.intValue = it }
        )

        // Custom resolution fields when "Custom" is selected (index 0)
        if (state.selectedScreenSize.intValue == 0) {
            Spacer(Modifier.height(SettingItemGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    SettingTextField(
                        label = stringResource(R.string.common_ui_width),
                        value = state.customWidth.value,
                        onValueChange = { state.customWidth.value = it },
                        keyboardType = KeyboardType.Number
                    )
                }
                Box(Modifier.weight(1f)) {
                    SettingTextField(
                        label = stringResource(R.string.common_ui_height),
                        value = state.customHeight.value,
                        onValueChange = { state.customHeight.value = it },
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }

        if (!isContainer) {
            Spacer(Modifier.height(SettingItemGap))
            SettingDropdown(
                label = stringResource(R.string.settings_general_refresh_rate),
                entries = state.refreshRateEntries.value,
                selectedIndex = state.selectedRefreshRate.intValue,
                onSelected = { state.selectedRefreshRate.intValue = it }
            )
        }
    }

    Spacer(Modifier.height(SettingSectionGap))

    // Sound
    SettingGroup {
        SettingDropdown(
            label = stringResource(R.string.container_config_audio_driver),
            entries = state.audioDriverEntries.value,
            selectedIndex = state.selectedAudioDriver.intValue,
            onSelected = { state.selectedAudioDriver.intValue = it }
        )

        Spacer(Modifier.height(SettingItemGap))

        SettingDropdown(
            label = stringResource(R.string.settings_audio_midi_sound_font),
            entries = state.midiSoundFontEntries.value,
            selectedIndex = state.selectedMidiSoundFont.intValue,
            onSelected = { state.selectedMidiSoundFont.intValue = it }
        )
    }

    if (!isContainer) {
        Spacer(Modifier.height(SettingSectionGap))
        SettingGroup {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "FPS Limiter",
                    color = TextPrimary,
                    fontSize = SettingValueSize,
                    fontWeight = FontWeight.SemiBold
                )
                val limits = listOf(0, 30, 45, 60, 90, 120)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    limits.forEach { limit ->
                        val isChecked = state.fpsLimit.intValue == limit
                        val bgColor = if (isChecked) AccentBlue.copy(alpha = 0.15f) else ChipSurface
                        val borderColor = if (isChecked) AccentBlue.copy(alpha = 0.4f) else ChipBorder
                        val textColor = if (isChecked) AccentBlue else TextDim

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable { state.fpsLimit.intValue = limit }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (limit == 0) "None" else "$limit",
                                color = textColor,
                                fontSize = SettingLabelSize,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===================================================================
// Section 1: Display
// ===================================================================
@Composable
private fun DisplaySection(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {

    SettingGroup {
        SettingDropdown(
            label = stringResource(R.string.container_graphics_driver),
            entries = state.graphicsDriverEntries.value,
            selectedIndex = state.selectedGraphicsDriver.intValue,
            onSelected = { state.selectedGraphicsDriver.intValue = it }
        )

        Spacer(Modifier.height(SettingSectionGap))

        SettingDropdown(
            label = stringResource(R.string.container_wine_dxwrapper),
            entries = state.dxWrapperEntries.value,
            selectedIndex = state.selectedDxWrapper.intValue,
            onSelected = { state.selectedDxWrapper.intValue = it }
        )
    }

    Spacer(Modifier.height(SettingItemGap))

    // Graphics Driver Configuration - expandable inline card
    GraphicsDriverConfigCard(state, callbacks)

    Spacer(Modifier.height(SettingItemGap))

    // Show DXVK or WineD3D config card based on selected DX wrapper
    val dxWrapperEntries = state.dxWrapperEntries.value
    val dxWrapperIdx = state.selectedDxWrapper.intValue
    val selectedDxWrapper = if (dxWrapperIdx in dxWrapperEntries.indices)
        com.winlator.cmod.shared.util.StringUtils.parseIdentifier(dxWrapperEntries[dxWrapperIdx])
    else ""

    if (selectedDxWrapper.contains("dxvk")) {
        DXVKConfigCard(state, callbacks)
    } else {
        WineD3DConfigCard(state)
    }

}

// ===================================================================
// Graphics Driver Configuration Card
// ===================================================================
@Composable
private fun GraphicsDriverConfigCard(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    val expanded by state.gfxConfigExpanded

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingGroupCorner))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(SettingGroupCorner))
    ) {
        // Header row - always visible, acts as expand/collapse toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.gfxConfigExpanded.value = !expanded }
                .padding(SettingGroupPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(SettingIconSize)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.container_graphics_configuration),
                color = TextPrimary,
                fontSize = SettingValueSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Version badge
            if (state.graphicsDriverVersion.value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        state.graphicsDriverVersion.value,
                        color = AccentBlue,
                        fontSize = SettingLabelSize,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(SettingIconSize)
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_vulkan_version),
                    entries = state.gfxVulkanVersionEntries.value,
                    selectedIndex = state.gfxSelectedVulkanVersion.intValue,
                    onSelected = { state.gfxSelectedVulkanVersion.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_version),
                    entries = state.gfxDriverVersionEntries.value,
                    selectedIndex = state.gfxSelectedDriverVersion.intValue,
                    onSelected = {
                        state.gfxSelectedDriverVersion.intValue = it
                        callbacks.onGfxDriverVersionChanged(it)
                    }
                )

                Spacer(Modifier.height(SettingItemGap))

                // Available Extensions (multi-select)
                ExtensionsMultiSelect(state)

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_gpu_name),
                    entries = state.gfxGpuNameEntries.value,
                    selectedIndex = state.gfxSelectedGpuName.intValue,
                    onSelected = { state.gfxSelectedGpuName.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_max_device_memory),
                    entries = state.gfxMaxDeviceMemoryEntries.value,
                    selectedIndex = state.gfxSelectedMaxDeviceMemory.intValue,
                    onSelected = { state.gfxSelectedMaxDeviceMemory.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_present_modes),
                    entries = state.gfxPresentModeEntries.value,
                    selectedIndex = state.gfxSelectedPresentMode.intValue,
                    onSelected = { state.gfxSelectedPresentMode.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_resource_type),
                    entries = state.gfxResourceTypeEntries.value,
                    selectedIndex = state.gfxSelectedResourceType.intValue,
                    onSelected = { state.gfxSelectedResourceType.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_bcn_emulation),
                    entries = state.gfxBcnEmulationEntries.value,
                    selectedIndex = state.gfxSelectedBcnEmulation.intValue,
                    onSelected = { state.gfxSelectedBcnEmulation.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_bcn_emulation_type),
                    entries = state.gfxBcnEmulationTypeEntries.value,
                    selectedIndex = state.gfxSelectedBcnEmulationType.intValue,
                    onSelected = { state.gfxSelectedBcnEmulationType.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_graphics_bcn_emulation_cache),
                    entries = state.gfxBcnEmulationCacheEntries.value,
                    selectedIndex = state.gfxSelectedBcnEmulationCache.intValue,
                    onSelected = { state.gfxSelectedBcnEmulationCache.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                // Toggles
                SettingCheckbox(
                    label = stringResource(R.string.container_graphics_sync_frame),
                    checked = state.gfxSyncFrame.value,
                    onCheckedChange = { state.gfxSyncFrame.value = it }
                )

                Spacer(Modifier.height(SettingTightGap))

                SettingCheckbox(
                    label = stringResource(R.string.container_graphics_disable_present_wait),
                    checked = state.gfxDisablePresentWait.value,
                    onCheckedChange = { state.gfxDisablePresentWait.value = it }
                )
            }
        }
    }
}

// ===================================================================
// Extensions multi-select
// ===================================================================
@Composable
private fun ExtensionsMultiSelect(state: GameSettingsStateHolder) {
    val extensions = state.gfxAvailableExtensions.value
    val blacklisted = state.gfxBlacklistedExtensions.value
    var showDialog by remember { mutableStateOf(false) }
    val enabledCount = extensions.size - blacklisted.size

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.container_graphics_available_extensions),
            color = TextSecondary,
            fontSize = SettingLabelSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(bottom = SettingTightGap)
        )

        // Summary button — opens popup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingFieldCorner))
                .background(InputSurface)
                .border(1.dp, InputBorder, RoundedCornerShape(SettingFieldCorner))
                .clickable(enabled = extensions.isNotEmpty()) { showDialog = true }
                .padding(horizontal = SettingFieldHorizontalPadding, vertical = SettingFieldVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (extensions.isEmpty()) "—"
                else stringResource(R.string.container_graphics_extensions_enabled_summary, enabledCount, extensions.size),
                color = TextPrimary,
                fontSize = SettingValueSize,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(SettingIconSize)
            )
        }
    }

    if (showDialog && extensions.isNotEmpty()) {
        ExtensionsPickerDialog(
            extensions = extensions,
            blacklisted = blacklisted,
            onToggle = { ext, enabled ->
                state.gfxBlacklistedExtensions.value = if (enabled) {
                    blacklisted - ext
                } else {
                    blacklisted + ext
                }
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ExtensionsPickerDialog(
    extensions: List<String>,
    blacklisted: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxHeight(0.70f)
                .clip(RoundedCornerShape(SettingGroupCorner))
                .background(BgDeep)
                .border(1.dp, CardBorder, RoundedCornerShape(SettingGroupCorner))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.container_graphics_available_extensions),
                    color = TextPrimary,
                    fontSize = SettingValueSize,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val enabledCount = extensions.size - blacklisted.size
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        stringResource(R.string.container_graphics_extensions_enabled_summary, enabledCount, extensions.size),
                        color = AccentBlue,
                        fontSize = SettingLabelSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))

            // Scrollable list — takes remaining space between header and button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                extensions.forEach { ext ->
                    val isEnabled = ext !in blacklisted
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggle(ext, !isEnabled) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isEnabled,
                            onCheckedChange = { onToggle(ext, it) },
                            modifier = Modifier.size(20.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentBlue,
                                uncheckedColor = CheckBorder,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            ext,
                            color = if (isEnabled) TextPrimary else TextDim,
                            fontSize = SettingValueSize,
                            maxLines = 1
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))

            // Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(android.R.string.ok),
                    color = AccentBlue,
                    fontSize = SettingValueSize,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ===================================================================
// DXVK Configuration Card
// ===================================================================
@Composable
private fun DXVKConfigCard(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    val expanded by state.dxvkConfigExpanded

    // Determine DXVK async support based on currently selected version
    val dxvkVersions = state.dxvkVersionEntries.value
    val selectedIdx = state.dxvkSelectedVersion.intValue
    val selectedVersion = if (selectedIdx in dxvkVersions.indices) dxvkVersions[selectedIdx] else ""
    val isGplAsync = selectedVersion.contains("gplasync")
    val isAsync = selectedVersion.contains("async")
    val asyncEnabled = isAsync || isGplAsync
    val asyncCacheEnabled = isGplAsync

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingGroupCorner))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(SettingGroupCorner))
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.dxvkConfigExpanded.value = !expanded }
                .padding(SettingGroupPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(SettingIconSize)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.container_wine_dxvk_config_title),
                color = TextPrimary,
                fontSize = SettingValueSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(SettingIconSize)
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_vkd3d_version),
                    entries = state.dxvkVkd3dVersionEntries.value,
                    selectedIndex = state.dxvkSelectedVkd3dVersion.intValue,
                    onSelected = {
                        state.dxvkSelectedVkd3dVersion.intValue = it
                        callbacks.onDxvkVkd3dVersionChanged(it)
                    }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_vkd3d_feature_level),
                    entries = state.dxvkVkd3dFeatureLevelEntries.value,
                    selectedIndex = state.dxvkSelectedVkd3dFeatureLevel.intValue,
                    onSelected = { state.dxvkSelectedVkd3dFeatureLevel.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_dxvk_version),
                    entries = state.dxvkVersionEntries.value,
                    selectedIndex = state.dxvkSelectedVersion.intValue,
                    onSelected = {
                        state.dxvkSelectedVersion.intValue = it
                        callbacks.onDxvkVersionChanged(it)
                    }
                )

                // Async toggle - greyed out when version doesn't support it
                Spacer(Modifier.height(SettingItemGap))
                Box(modifier = Modifier.alpha(if (asyncEnabled) 1f else 0.35f)) {
                    SettingCheckbox(
                        label = stringResource(R.string.container_wine_enabled_async),
                        checked = state.dxvkAsync.value && asyncEnabled,
                        onCheckedChange = { if (asyncEnabled) state.dxvkAsync.value = it }
                    )
                }

                // Async Cache toggle - greyed out when version doesn't support it
                Spacer(Modifier.height(SettingTightGap))
                Box(modifier = Modifier.alpha(if (asyncCacheEnabled) 1f else 0.35f)) {
                    SettingCheckbox(
                        label = stringResource(R.string.container_wine_enabled_async_cache),
                        checked = state.dxvkAsyncCache.value && asyncCacheEnabled,
                        onCheckedChange = { if (asyncCacheEnabled) state.dxvkAsyncCache.value = it }
                    )
                }

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_ddraw_wrapper),
                    entries = state.dxvkDdrawWrapperEntries.value,
                    selectedIndex = state.dxvkSelectedDdrawWrapper.intValue,
                    onSelected = { state.dxvkSelectedDdrawWrapper.intValue = it }
                )
            }
        }
    }
}

// ===================================================================
// WineD3D Configuration Card
// ===================================================================
@Composable
private fun WineD3DConfigCard(state: GameSettingsStateHolder) {
    val expanded by state.wined3dConfigExpanded

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingGroupCorner))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(SettingGroupCorner))
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.wined3dConfigExpanded.value = !expanded }
                .padding(SettingGroupPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(SettingIconSize)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.container_wine_wined3d_config_title),
                color = TextPrimary,
                fontSize = SettingValueSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(SettingIconSize)
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_csmt),
                    entries = state.wined3dCsmtEntries.value,
                    selectedIndex = state.wined3dSelectedCsmt.intValue,
                    onSelected = { state.wined3dSelectedCsmt.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_gpu_name),
                    entries = state.wined3dGpuNameEntries.value,
                    selectedIndex = state.wined3dSelectedGpuName.intValue,
                    onSelected = { state.wined3dSelectedGpuName.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_video_memory_size),
                    entries = state.wined3dVideoMemorySizeEntries.value,
                    selectedIndex = state.wined3dSelectedVideoMemorySize.intValue,
                    onSelected = { state.wined3dSelectedVideoMemorySize.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_strict_shader_math),
                    entries = state.wined3dStrictShaderMathEntries.value,
                    selectedIndex = state.wined3dSelectedStrictShaderMath.intValue,
                    onSelected = { state.wined3dSelectedStrictShaderMath.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_wine_offscreen_rendering_mode),
                    entries = state.wined3dOffscreenRenderingModeEntries.value,
                    selectedIndex = state.wined3dSelectedOffscreenRenderingMode.intValue,
                    onSelected = { state.wined3dSelectedOffscreenRenderingMode.intValue = it }
                )

                Spacer(Modifier.height(SettingItemGap))

                SettingDropdown(
                    label = stringResource(R.string.container_config_renderer),
                    entries = state.wined3dRendererEntries.value,
                    selectedIndex = state.wined3dSelectedRenderer.intValue,
                    onSelected = { state.wined3dSelectedRenderer.intValue = it }
                )
            }
        }
    }
}

// ===================================================================
// Section: Steam (conditional)
// ===================================================================
@Composable
private fun SteamSection(state: GameSettingsStateHolder) {

    SubsectionLabel(stringResource(R.string.steam_section_emulator))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_use_cold_client),
            checked = state.useColdClient.value,
            onCheckedChange = {
                state.useColdClient.value = it
                // Cold Client and Launch Steam Client are mutually exclusive —
                // they use different Steam DLL setups that can't coexist at runtime.
                if (it) state.launchRealSteam.value = false
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.shortcuts_properties_use_cold_client_description),
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(SettingItemGap))

        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_use_steam_input),
            checked = state.useSteamInput.value,
            onCheckedChange = { state.useSteamInput.value = it }
        )
        Spacer(Modifier.height(SettingItemGap))

        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_force_dlc),
            checked = state.forceDlc.value,
            onCheckedChange = { state.forceDlc.value = it }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.shortcuts_properties_force_dlc_description),
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(SettingItemGap))

        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_steam_offline_mode),
            checked = state.steamOfflineMode.value,
            onCheckedChange = { state.steamOfflineMode.value = it }
        )
        Spacer(Modifier.height(SettingItemGap))

        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_unpack_files),
            checked = state.unpackFiles.value,
            onCheckedChange = {
                state.unpackFiles.value = it
                // Unpack Files swaps the on-disk exe with a Steamless-stripped copy —
                // incompatible with the original-exe launch Real Steam does via -applaunch.
                if (it) state.launchRealSteam.value = false
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.shortcuts_properties_unpack_files_description),
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(SettingItemGap))

        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_runtime_patcher),
            checked = state.runtimePatcher.value,
            onCheckedChange = {
                state.runtimePatcher.value = it
                // Runtime DRM Patcher injects Goldberg DLLs into the game at launch —
                // Real Steam talks to the actual Steam client and doesn't want emulated
                // steamclient DLLs poking around in its address space.
                if (it) state.launchRealSteam.value = false
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.shortcuts_properties_runtime_patcher_description),
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }

    Spacer(Modifier.height(SettingItemGap))

    SubsectionLabel(stringResource(R.string.steam_section_real_client))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_launch_steam_client_beta),
            checked = state.launchRealSteam.value,
            onCheckedChange = {
                state.launchRealSteam.value = it
                // Launch Steam Client runs the game through the real Steam client's
                // -applaunch pipeline. Cold Client, Unpack Files, and Runtime DRM
                // Patcher all conflict with that path — disable when this one is on.
                if (it) {
                    state.useColdClient.value = false
                    state.unpackFiles.value = false
                    state.runtimePatcher.value = false
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.shortcuts_properties_launch_steam_client_description),
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(SettingItemGap))

        if (state.steamTypeEntries.value.isNotEmpty()) {
            SettingDropdown(
                label = stringResource(R.string.shortcuts_properties_steam_type),
                entries = state.steamTypeEntries.value,
                selectedIndex = state.selectedSteamType.intValue,
                onSelected = { state.selectedSteamType.intValue = it }
            )
        }
    }
}

// ===================================================================
// Section 3: Wine
// ===================================================================
@Composable
private fun WineSection(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    val isContainer = state.isContainerEditMode.value

    SettingGroup {
        // LC_ALL with locale picker. Emulator selection lives in Advanced.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) {
                SettingTextField(
                    label = stringResource(R.string.container_config_lc_all),
                    value = state.lcAll.value,
                    onValueChange = { state.lcAll.value = it }
                )
            }
            Spacer(Modifier.width(8.dp))
            var showLocalePicker by remember { mutableStateOf(false) }
            val localeMenuOffset = rememberSmartDropdownOffset()
            Box(
                modifier = Modifier.padding(top = 22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputSurface)
                        .border(1.dp, InputBorder, RoundedCornerShape(8.dp))
                        .smartDropdownAnchor(offset = localeMenuOffset) { showLocalePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showLocalePicker,
                    onDismissRequest = { showLocalePicker = false },
                    offset = localeMenuOffset.value,
                    shape = RoundedCornerShape(8.dp),
                    containerColor = CardSurface,
                    modifier = Modifier.height(300.dp)
                ) {
                    state.localeOptions.value.forEach { locale ->
                        DropdownMenuItem(
                            text = {
                                Text(locale, color = TextPrimary, fontSize = SettingValueSize)
                            },
                            onClick = {
                                state.lcAll.value = locale
                                showLocalePicker = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Desktop Theme
    if (state.desktopThemeEntries.value.isNotEmpty()) {
        Spacer(Modifier.height(SettingItemGap))
        SettingGroup {
            SettingDropdown(
                label = stringResource(R.string.settings_general_theme),
                entries = state.desktopThemeEntries.value,
                selectedIndex = state.selectedDesktopTheme.intValue,
                onSelected = { state.selectedDesktopTheme.intValue = it }
            )

            if (isContainer && state.desktopBackgroundTypeEntries.value.isNotEmpty()) {
                Spacer(Modifier.height(SettingItemGap))
                SettingDropdown(
                    label = stringResource(R.string.settings_general_background),
                    entries = state.desktopBackgroundTypeEntries.value,
                    selectedIndex = state.selectedDesktopBackgroundType.intValue,
                    onSelected = { state.selectedDesktopBackgroundType.intValue = it }
                )

                val typeEntries = state.desktopBackgroundTypeEntries.value
                val selectedType = typeEntries.getOrNull(state.selectedDesktopBackgroundType.intValue)
                    ?.lowercase() ?: ""
                when (selectedType) {
                    "color" -> {
                        Spacer(Modifier.height(SettingItemGap))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(Modifier.weight(1f)) {
                                SettingTextField(
                                    label = stringResource(R.string.settings_general_background_color_hex),
                                    value = state.desktopBackgroundColor.value,
                                    onValueChange = { state.desktopBackgroundColor.value = it }
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            val previewColor = remember(state.desktopBackgroundColor.value) {
                                runCatching {
                                    Color(android.graphics.Color.parseColor(state.desktopBackgroundColor.value))
                                }.getOrDefault(Color(0xFF0277BD))
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(previewColor)
                                    .border(1.dp, InputBorder, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                    "image" -> {
                        Spacer(Modifier.height(SettingItemGap))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(SettingFieldCorner))
                                .border(1.dp, InputBorder, RoundedCornerShape(SettingFieldCorner))
                                .background(InputSurface)
                                .clickable { callbacks.onPickWallpaper() }
                                .padding(horizontal = SettingFieldHorizontalPadding, vertical = SettingFieldVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (state.desktopWallpaperSelected.value) {
                                    stringResource(R.string.settings_general_wallpaper_selected)
                                } else {
                                    stringResource(R.string.settings_general_select_wallpaper)
                                },
                                color = if (state.desktopWallpaperSelected.value) TextPrimary else TextSecondary,
                                fontSize = SettingLabelSize,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.desktopWallpaperSelected.value) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isContainer && state.mouseWarpOverrideEntries.value.isNotEmpty()) {
        Spacer(Modifier.height(SettingItemGap))
        SettingGroup {
            SettingDropdown(
                label = stringResource(R.string.container_wine_mouse_warp_override),
                entries = state.mouseWarpOverrideEntries.value,
                selectedIndex = state.selectedMouseWarpOverride.intValue,
                onSelected = { state.selectedMouseWarpOverride.intValue = it }
            )
        }
    }
}

// ===================================================================
// Section 4: Components
// ===================================================================
@Composable
private fun ComponentsSection(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {

    // DirectX components
    if (state.directXComponents.value.isNotEmpty()) {
        SubsectionLabel(stringResource(R.string.container_wine_directx))
        Spacer(Modifier.height(8.dp))
        SettingGroup {
            state.directXComponents.value.forEachIndexed { index, component ->
                if (index > 0) Spacer(Modifier.height(SettingItemGap))
                SettingDropdown(
                    label = component.label,
                    entries = state.winComponentEntries.value,
                    selectedIndex = component.selectedIndex,
                    onSelected = { newVal ->
                        callbacks.onUpdateWinComponent(true, index, newVal)
                    }
                )
            }
        }
        Spacer(Modifier.height(SettingSectionGap))
    }

    // General components
    if (state.generalComponents.value.isNotEmpty()) {
        SubsectionLabel(stringResource(R.string.settings_general_title))
        Spacer(Modifier.height(8.dp))
        SettingGroup {
            state.generalComponents.value.forEachIndexed { index, component ->
                if (index > 0) Spacer(Modifier.height(SettingItemGap))
                SettingDropdown(
                    label = component.label,
                    entries = state.winComponentEntries.value,
                    selectedIndex = component.selectedIndex,
                    onSelected = { newVal ->
                        callbacks.onUpdateWinComponent(false, index, newVal)
                    }
                )
            }
        }
    }
}

// ===================================================================
// Section 5: Variables
// ===================================================================
private fun findKnownEnvVar(name: String): Array<String>? =
    EnvVarsView.knownEnvVars.firstOrNull { it[0] == name }

@Composable
private fun VariablesSection(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {
    val isContainer = state.isContainerEditMode.value
    val hasDraftEnvVar = state.envVars.value.any { it.key.isBlank() }

    if (isContainer) {
        SubsectionLabel(stringResource(R.string.container_config_variables))
        Spacer(Modifier.height(8.dp))
    }

    SettingGroup {
        if (state.envVars.value.isEmpty()) {
            Text(
                stringResource(R.string.common_ui_none),
                color = TextDim,
                fontSize = SettingValueSize,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        } else {
            state.envVars.value.forEachIndexed { index, envVar ->
                if (index > 0) {
                    Spacer(Modifier.height(1.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(DividerColor)
                    )
                    Spacer(Modifier.height(1.dp))
                }
                EnvVarRow(
                    name = envVar.key,
                    value = envVar.value,
                    excludeOtherNames = state.envVars.value
                        .filterIndexed { i, _ -> i != index }
                        .map { it.key }
                        .toSet(),
                    onNameChange = { newKey ->
                        val normalizedKey = newKey.trim()
                        val list = state.envVars.value.toMutableList()
                        val isUnique = normalizedKey.isEmpty() ||
                            state.envVars.value.none { it.key == normalizedKey }
                        if (index in list.indices && isUnique) {
                            list[index] = EnvVarItem(normalizedKey, envVar.value)
                            state.envVars.value = list
                        }
                    },
                    onValueChange = { v ->
                        val list = state.envVars.value.toMutableList()
                        list[index] = EnvVarItem(envVar.key, v)
                        state.envVars.value = list
                    },
                    onRemove = { callbacks.onRemoveEnvVar(index) }
                )
            }
        }

        Spacer(Modifier.height(SettingItemGap))

        // Add button
        if (!hasDraftEnvVar) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.08f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable {
                        state.envVars.value = state.envVars.value + EnvVarItem("", "")
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(SettingIconSize)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.common_ui_add),
                        color = AccentBlue,
                        fontSize = SettingValueSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (isContainer) {
        Spacer(Modifier.height(SettingSectionGap))
        SubsectionLabel(stringResource(R.string.container_config_drives))
        Spacer(Modifier.height(8.dp))
        SettingGroup {
            val drives = state.drivesList.value
            if (drives.isEmpty()) {
                Text(
                stringResource(R.string.common_ui_none),
                color = TextDim,
                fontSize = SettingValueSize,
                modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                drives.forEachIndexed { index, drive ->
                    val otherLetters =
                        drives
                            .mapIndexedNotNull { otherIndex, otherDrive ->
                                otherDrive.letter.takeUnless { otherIndex == index }?.uppercase()
                            }.toSet()
                    val availableLetters =
                        SelectableDriveLetters.filter { letter ->
                            letter.equals(drive.letter, ignoreCase = true) || letter !in otherLetters
                        }

                    if (index > 0) {
                        Spacer(Modifier.height(1.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        Spacer(Modifier.height(1.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DriveLetterSelector(
                            selectedLetter = drive.letter.uppercase(),
                            canChangeLetter = drive.canChangeLetter,
                            availableLetters = availableLetters,
                            onSelected = { callbacks.onDriveLetterChanged(index, it) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputSurface)
                                .border(1.dp, InputBorder, RoundedCornerShape(8.dp))
                                .clickable { callbacks.onPickDrivePath(index) }
                                .padding(horizontal = SettingFieldHorizontalPadding, vertical = SettingFieldVerticalPadding)
                        ) {
                            Text(
                                drive.path.ifEmpty { stringResource(R.string.common_ui_select_folder) },
                                color = if (drive.path.isEmpty()) TextDim else TextPrimary,
                                fontSize = SettingLabelSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DangerRed.copy(alpha = 0.1f))
                                .clickable { callbacks.onRemoveDrive(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(SettingControlIconSize)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(SettingItemGap))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.08f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable { callbacks.onAddDrive() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(SettingIconSize)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.common_ui_add),
                        color = AccentBlue,
                        fontSize = SettingValueSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveLetterSelector(
    selectedLetter: String,
    canChangeLetter: Boolean,
    availableLetters: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(selectedLetter, availableLetters) { mutableStateOf(false) }
    val menuOffset = rememberSmartDropdownOffset()
    val showDropdown = canChangeLetter && availableLetters.size > 1

    Box {
        Row(
            modifier =
                Modifier
                    .widthIn(min = 64.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBlue.copy(alpha = 0.1f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .smartDropdownAnchor(enabled = showDropdown, offset = menuOffset) { expanded = true }
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "$selectedLetter:",
                color = AccentBlue,
                fontSize = SettingValueSize,
                fontWeight = FontWeight.SemiBold,
            )
            if (showDropdown) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        DropdownMenu(
            expanded = showDropdown && expanded,
            onDismissRequest = { expanded = false },
            offset = menuOffset.value,
            shape = RoundedCornerShape(8.dp),
            containerColor = CardSurface,
            modifier = Modifier.widthIn(min = 88.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                availableLetters.forEach { letter ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$letter:",
                                color = if (letter == selectedLetter) AccentBlue else TextPrimary,
                                fontSize = SettingValueSize,
                                fontWeight =
                                    if (letter == selectedLetter) FontWeight.SemiBold
                                    else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelected(letter)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// ===================================================================
// Env Var row: name dropdown + type-aware value editor
// ===================================================================
@Composable
private fun EnvVarRow(
    name: String,
    value: String,
    excludeOtherNames: Set<String>,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    var nameMenuExpanded by remember { mutableStateOf(false) }
    val nameMenuOffset = rememberSmartDropdownOffset()
    var isCustomMode by remember(name) {
        mutableStateOf(name.isNotEmpty() && findKnownEnvVar(name) == null)
    }
    var customText by remember(name) { mutableStateOf(if (isCustomMode) name else "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name dropdown or custom text field
        Box(modifier = Modifier.weight(1.6f)) {
            if (isCustomMode) {
                // Custom mode: show editable text field for variable name
                BasicTextField(
                    value = customText,
                    onValueChange = { newText ->
                        customText = newText
                        onNameChange(newText.trim())
                    },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = SettingValueSize
                    ),
                    cursorBrush = SolidColor(AccentBlue),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EnvVarControlHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputSurface)
                        .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = SettingFieldHorizontalPadding),
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            if (customText.isEmpty()) {
                                Text(
                                    stringResource(R.string.container_config_new_env_var),
                                    color = TextDim,
                                    fontSize = SettingValueSize
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EnvVarControlHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputSurface)
                        .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .smartDropdownAnchor(offset = nameMenuOffset) { nameMenuExpanded = true }
                        .padding(horizontal = SettingFieldHorizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (name.isEmpty()) stringResource(R.string.container_config_new_env_var) else name,
                            color = if (name.isEmpty()) TextDim else TextPrimary,
                            fontSize = SettingValueSize,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(SettingControlIconSize)
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = nameMenuExpanded,
                onDismissRequest = { nameMenuExpanded = false },
                offset = nameMenuOffset.value,
                shape = RoundedCornerShape(8.dp),
                containerColor = CardSurface,
                modifier = Modifier
                    .height(360.dp)
                    .width(260.dp)
            ) {
                // "Custom" option at top — allows typing a variable name
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.common_ui_custom),
                            color = AccentBlue,
                            fontSize = SettingValueSize,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        isCustomMode = true
                        customText = ""
                        onNameChange("")
                        nameMenuExpanded = false
                    }
                )
                // Divider after Custom
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))

                // Sort: unselected vars in ABC order, then selected vars in ABC order
                val allKnown = EnvVarsView.knownEnvVars.map { it[0] }
                val unselected = allKnown
                    .filter { it !in excludeOtherNames && it != name }
                    .sortedBy { it.uppercase() }
                val selected = allKnown
                    .filter { it in excludeOtherNames }
                    .sortedBy { it.uppercase() }

                (unselected + selected).forEach { knownName ->
                    val disabled = knownName != name && knownName in excludeOtherNames
                    DropdownMenuItem(
                        enabled = !disabled,
                        text = {
                            Text(
                                knownName,
                                color = if (disabled) TextDim else TextPrimary,
                                fontSize = SettingValueSize
                            )
                        },
                        onClick = {
                            isCustomMode = false
                            customText = ""
                            onNameChange(knownName)
                            nameMenuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        // Value editor (type-aware)
        Box(modifier = Modifier.weight(1f)) {
            EnvVarValueEditor(
                name = name,
                value = value,
                onValueChange = onValueChange
            )
        }
        if (onRemove != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DangerRed.copy(alpha = 0.1f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(SettingControlIconSize)
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun EnvVarValueEditor(
    name: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val known = findKnownEnvVar(name)
    val type = known?.getOrNull(1) ?: "TEXT"
    when (type) {
        "CHECKBOX" -> {
            val off = known!![2]
            val on = known[3]
            val isOn = value == on || value == "1" || value == "true"
            EnvValueDropdown(
                current = if (isOn) on else off,
                options = listOf(off, on),
                onSelected = onValueChange
            )
        }
        "SELECT" -> {
            val options = known!!.drop(2)
            EnvValueDropdown(
                current = if (value.isEmpty()) options.firstOrNull() ?: "" else value,
                options = options,
                onSelected = onValueChange
            )
        }
        "SELECT_MULTIPLE" -> {
            val options = known!!.drop(2)
            EnvValueMultiDropdown(
                current = value,
                options = options,
                onChanged = onValueChange
            )
        }
        "NUMBER" -> EnvValueTextField(value, onValueChange, numeric = true)
        "DECIMAL" -> EnvValueTextField(value, onValueChange, decimal = true)
        else -> EnvValueTextField(value, onValueChange, numeric = false)
    }
}

@Composable
private fun EnvValueDropdown(
    current: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuOffset = rememberSmartDropdownOffset()
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EnvVarControlHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(InputSurface)
                .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .smartDropdownAnchor(offset = menuOffset) { expanded = true }
                .padding(horizontal = SettingFieldHorizontalPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current,
                    color = TextPrimary,
                    fontSize = SettingValueSize,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = menuOffset.value,
            shape = RoundedCornerShape(8.dp),
            containerColor = CardSurface,
            modifier = Modifier.width(220.dp)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(opt, color = TextPrimary, fontSize = SettingValueSize)
                    },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EnvValueMultiDropdown(
    current: String,
    options: List<String>,
    onChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuOffset = rememberSmartDropdownOffset()
    val selectedSet = remember(current) {
        current.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
    }
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EnvVarControlHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(InputSurface)
                .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .smartDropdownAnchor(offset = menuOffset) { expanded = true }
                .padding(horizontal = SettingFieldHorizontalPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selectedSet.isEmpty()) "—" else selectedSet.joinToString(","),
                    color = if (selectedSet.isEmpty()) TextDim else TextPrimary,
                    fontSize = SettingValueSize,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = menuOffset.value,
            shape = RoundedCornerShape(8.dp),
            containerColor = CardSurface,
            modifier = Modifier
                .height(320.dp)
                .width(260.dp)
        ) {
            options.forEach { opt ->
                val checked = opt in selectedSet
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentBlue,
                                    uncheckedColor = TextSecondary,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(opt, color = TextPrimary, fontSize = SettingValueSize)
                        }
                    },
                    onClick = {
                        if (checked) selectedSet.remove(opt) else selectedSet.add(opt)
                        onChanged(selectedSet.joinToString(","))
                    }
                )
            }
        }
    }
}

@Composable
private fun EnvValueTextField(
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
    decimal: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = TextPrimary, fontSize = SettingValueSize),
        cursorBrush = SolidColor(AccentBlue),
        singleLine = true,
        keyboardOptions = when {
            numeric -> KeyboardOptions(keyboardType = KeyboardType.Number)
            decimal -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
            else -> KeyboardOptions.Default
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(EnvVarControlHeight),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(InputSurface)
                    .border(1.dp, AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = SettingFieldHorizontalPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.common_ui_value), color = TextDim, fontSize = SettingValueSize)
                }
                innerTextField()
            }
        }
    )
}

// ===================================================================
// Section 6: Input
// ===================================================================
@Composable
private fun InputSection(state: GameSettingsStateHolder) {
    val isContainer = state.isContainerEditMode.value

    // Input Controls group
    SubsectionLabel(stringResource(R.string.common_ui_input_controls))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        if (!isContainer) {
            SettingDropdown(
                label = stringResource(R.string.common_ui_profile),
                entries = state.controlsProfileEntries.value,
                selectedIndex = state.selectedControlsProfile.intValue,
                onSelected = { state.selectedControlsProfile.intValue = it }
            )

            Spacer(Modifier.height(SettingItemGap))

            SettingDropdown(
                label = stringResource(R.string.num_controllers),
                entries = state.numControllersEntries.value,
                selectedIndex = state.selectedNumControllers.intValue,
                onSelected = { state.selectedNumControllers.intValue = it }
            )

            Spacer(Modifier.height(SettingItemGap))
        }

        // Exclusive Input — when off, XInput + DInput are both forced on and locked below.
        // Container mode backs it with the global "xinput_toggle" pref.
        val exclusiveChecked = if (isContainer) state.containerExclusiveInput.value
        else state.disableXInput.value
        SettingCheckbox(
            label = stringResource(R.string.shortcuts_properties_exclusive_input),
            checked = exclusiveChecked,
            onCheckedChange = { enabled ->
                if (isContainer) {
                    state.containerExclusiveInput.value = enabled
                } else {
                    state.disableXInput.value = enabled
                }
                if (!enabled) {
                    state.enableXInput.value = true
                    state.enableDInput.value = true
                }
            }
        )

        Spacer(Modifier.height(4.dp))

        SettingCheckbox(
            label = stringResource(R.string.container_config_sdl2_compatibility),
            checked = state.sdl2Compatibility.value,
            onCheckedChange = { state.sdl2Compatibility.value = it }
        )

        if (!isContainer) {
            Spacer(Modifier.height(4.dp))

            SettingCheckbox(
                label = stringResource(R.string.session_xserver_simulate_touch_screen),
                checked = state.simTouchScreen.value,
                onCheckedChange = { state.simTouchScreen.value = it }
            )
        }
    }

    Spacer(Modifier.height(SettingSectionGap))

    // Game Controller group
    SubsectionLabel(stringResource(R.string.session_gamepad_game_controller))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        // DInput Mapper Type (only visible when DInput enabled)
        if (state.enableDInput.value) {
            SettingDropdown(
                label = stringResource(R.string.container_config_directinput_mapper_type),
                entries = state.dInputMapperTypeEntries.value,
                selectedIndex = state.selectedDInputMapperType.intValue,
                onSelected = { state.selectedDInputMapperType.intValue = it }
            )
            Spacer(Modifier.height(SettingItemGap))
        }

        // Enable XInput with help — only toggleable when Exclusive Input is on.
        val inputApisLocked = if (isContainer) !state.containerExclusiveInput.value
        else !state.disableXInput.value
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                SettingCheckbox(
                    label = stringResource(R.string.container_config_enable_xinput),
                    checked = state.enableXInput.value,
                    onCheckedChange = { state.enableXInput.value = it },
                    enabled = !inputApisLocked
                )
            }
            var showXInputHelp by remember { mutableStateOf(false) }
            val xInputHelpOffset = rememberSmartDropdownOffset()
            Box {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(InputSurface)
                        .border(1.dp, InputBorder, RoundedCornerShape(6.dp))
                        .smartDropdownAnchor(offset = xInputHelpOffset) { showXInputHelp = !showXInputHelp },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showXInputHelp,
                    onDismissRequest = { showXInputHelp = false },
                    offset = xInputHelpOffset.value,
                    shape = RoundedCornerShape(8.dp),
                    containerColor = CardSurface,
                    modifier = Modifier
                        .padding(10.dp)
                        .width(280.dp)
                ) {
                    HtmlText(
                        stringResource(R.string.container_config_help_xinput),
                        color = TextPrimary,
                        fontSize = SettingLabelSize,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Enable DInput with help
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                SettingCheckbox(
                    label = stringResource(R.string.container_config_enable_dinput),
                    checked = state.enableDInput.value,
                    onCheckedChange = { state.enableDInput.value = it },
                    enabled = !inputApisLocked
                )
            }
            var showDInputHelp by remember { mutableStateOf(false) }
            val dInputHelpOffset = rememberSmartDropdownOffset()
            Box {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(InputSurface)
                        .border(1.dp, InputBorder, RoundedCornerShape(6.dp))
                        .smartDropdownAnchor(offset = dInputHelpOffset) { showDInputHelp = !showDInputHelp },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showDInputHelp,
                    onDismissRequest = { showDInputHelp = false },
                    offset = dInputHelpOffset.value,
                    shape = RoundedCornerShape(8.dp),
                    containerColor = CardSurface,
                    modifier = Modifier
                        .padding(10.dp)
                        .width(280.dp)
                ) {
                    HtmlText(
                        stringResource(R.string.container_config_help_dinput),
                        color = TextPrimary,
                        fontSize = SettingLabelSize,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Warning when both XInput and DInput enabled
        if (state.enableXInput.value && state.enableDInput.value) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarningAmber.copy(alpha = 0.08f))
                    .border(1.dp, WarningAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    stringResource(R.string.container_config_xinput_dinput_warning),
                    color = WarningAmber,
                    fontSize = SettingLabelSize,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ===================================================================
// Section 7: Advanced
// ===================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSection(
    state: GameSettingsStateHolder,
    callbacks: GameSettingsCallbacks
) {

    // Wine / Proton version (read-only) — only show on existing containers
    // where it's not editable. When creating a new container the user already
    // selects the Wine Version in the General tab.
    val wineVersionDisplay = state.wineVersionDisplay.value
    if (wineVersionDisplay.isNotEmpty() && !state.wineVersionEditable.value) {
        SubsectionLabel(stringResource(R.string.container_wine_version))
        Spacer(Modifier.height(8.dp))
        SettingGroup {
            Text(
                text = wineVersionDisplay,
                color = TextPrimary,
                fontSize = SettingValueSize,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(SettingSectionGap))
    }

    // Emulator selection (mirrors the Wine tab dropdowns)
    SubsectionLabel(stringResource(R.string.container_config_emulator_section))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        SettingDropdown(
            label = stringResource(R.string.container_config_emulator_64bit),
            entries = state.emulator64Entries.value,
            selectedIndex = state.selectedEmulator64.intValue,
            onSelected = {
                state.selectedEmulator64.intValue = it
                callbacks.onEmulatorChanged()
            },
            enabled = state.emulator64Entries.value.isNotEmpty()
        )
        Spacer(Modifier.height(SettingItemGap))
        SettingDropdown(
            label = stringResource(R.string.container_config_dll_emulator),
            entries = state.emulator32Entries.value,
            selectedIndex = state.selectedEmulator.intValue,
            onSelected = {
                state.selectedEmulator.intValue = it
                callbacks.onEmulatorChanged()
            },
            enabled = state.emulator32Entries.value.isNotEmpty()
        )
    }
    Spacer(Modifier.height(SettingSectionGap))

    // FEXCore — hidden when FEXCore isn't explicitly in either slot.
    if (state.showFexcoreFrame.value) {
        val fexcoreUsage = emulatorUsageLabel(state, setOf("fexcore"))
        EmulatorSectionHeader(stringResource(R.string.container_fexcore_config), fexcoreUsage)
        Spacer(Modifier.height(8.dp))
        SettingGroup {
            SettingDropdown(
                label = stringResource(R.string.container_fexcore_version),
                entries = state.fexcoreVersionEntries.value,
                selectedIndex = state.selectedFexcoreVersion.intValue,
                onSelected = { state.selectedFexcoreVersion.intValue = it }
            )
            Spacer(Modifier.height(SettingItemGap))
            SettingDropdown(
                label = stringResource(R.string.container_fexcore_preset),
                entries = state.fexcorePresetEntries.value,
                selectedIndex = state.selectedFexcorePreset.intValue,
                onSelected = { state.selectedFexcorePreset.intValue = it }
            )
        }
        Spacer(Modifier.height(SettingSectionGap))
    }

    // Box64 / Wowbox64 — title switches between Box64/Wowbox64/both based on selection.
    if (state.showBox64Frame.value) {
        val box64Usage = emulatorUsageLabel(state, setOf("box64", "wowbox64"))
        val box64Id32 = state.emulator32Entries.value
            .getOrNull(state.selectedEmulator.intValue)
            ?.let { com.winlator.cmod.shared.util.StringUtils.parseIdentifier(it) } ?: ""
        val box64Id64 = state.emulator64Entries.value
            .getOrNull(state.selectedEmulator64.intValue)
            ?.let { com.winlator.cmod.shared.util.StringUtils.parseIdentifier(it) } ?: ""
        val usesPlainBox64 = box64Id32 == "box64" || box64Id64 == "box64"
        val usesWowbox64 = box64Id32 == "wowbox64" || box64Id64 == "wowbox64"
        val box64Title = when {
            usesPlainBox64 && usesWowbox64 -> stringResource(R.string.container_box64_wowbox64_title)
            usesWowbox64 -> stringResource(R.string.container_wowbox64_title)
            else -> stringResource(R.string.container_box64_title)
        }
        EmulatorSectionHeader(box64Title, box64Usage)
        Spacer(Modifier.height(8.dp))
        SettingGroup {
            SettingDropdown(
                label = stringResource(R.string.container_box64_version),
                entries = state.box64VersionEntries.value,
                selectedIndex = state.selectedBox64Version.intValue,
                onSelected = { state.selectedBox64Version.intValue = it }
            )
            Spacer(Modifier.height(SettingItemGap))
            SettingDropdown(
                label = stringResource(R.string.container_box64_preset),
                entries = state.box64PresetEntries.value,
                selectedIndex = state.selectedBox64Preset.intValue,
                onSelected = { state.selectedBox64Preset.intValue = it }
            )
        }
        Spacer(Modifier.height(SettingSectionGap))
    }

    // System
    SubsectionLabel(stringResource(R.string.common_ui_system))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        SettingDropdown(
            label = stringResource(R.string.container_config_startup_selection),
            entries = state.startupSelectionEntries.value,
            selectedIndex = state.selectedStartupSelection.intValue,
            onSelected = { state.selectedStartupSelection.intValue = it }
        )

        Spacer(Modifier.height(SettingItemGap))

        // Exec Arguments with helper dropdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) {
                SettingTextField(
                    label = stringResource(R.string.shortcuts_properties_exec_arguments),
                    value = state.execArgs.value,
                    onValueChange = { state.execArgs.value = it }
                )
            }
            Spacer(Modifier.width(8.dp))
            ExecArgsHelper(
                onArgSelected = { arg ->
                    val current = state.execArgs.value
                    state.execArgs.value = if (current.isBlank()) arg
                    else "$current $arg"
                }
            )
        }

        Spacer(Modifier.height(SettingItemGap))

        SettingCheckbox(
            label = stringResource(R.string.session_display_fullscreen_stretched),
            checked = state.fullscreenStretched.value,
            onCheckedChange = { state.fullscreenStretched.value = it }
        )
    }

    Spacer(Modifier.height(SettingSectionGap))

    // CPU Affinity
    SubsectionLabel(stringResource(R.string.container_config_processor_affinity))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val checkedList = state.cpuChecked.value
            for (i in 0 until state.cpuCount.intValue) {
                val isChecked = checkedList.getOrElse(i) { true }
                CpuChip(
                    index = i,
                    isChecked = isChecked,
                    onClick = {
                        // Block unchecking the last remaining core: zero-selected
                        // and all-selected would otherwise serialize identically,
                        // and runtime skips affinity for a zero mask.
                        val wouldLeaveNone = isChecked && checkedList.count { it } <= 1
                        if (!wouldLeaveNone) {
                            val mutable = checkedList.toMutableList()
                            mutable[i] = !isChecked
                            state.cpuChecked.value = mutable
                        }
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(SettingSectionGap))

    // CPU Affinity (32-bit apps)
    SubsectionLabel(stringResource(R.string.container_config_processor_affinity_32bit))
    Spacer(Modifier.height(8.dp))
    SettingGroup {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val checkedList = state.cpuCheckedWoW64.value
            for (i in 0 until state.cpuCount.intValue) {
                val isChecked = checkedList.getOrElse(i) { true }
                CpuChip(
                    index = i,
                    isChecked = isChecked,
                    onClick = {
                        val wouldLeaveNone = isChecked && checkedList.count { it } <= 1
                        if (!wouldLeaveNone) {
                            val mutable = checkedList.toMutableList()
                            mutable[i] = !isChecked
                            state.cpuCheckedWoW64.value = mutable
                        }
                    }
                )
            }
        }
    }
}

// ===================================================================
// Exec Args Helper Dropdown
// ===================================================================
@Composable
private fun ExecArgsHelper(onArgSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuOffset = rememberSmartDropdownOffset()

    Box(modifier = Modifier.padding(top = 22.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(InputSurface)
                .border(1.dp, InputBorder, RoundedCornerShape(8.dp))
                .smartDropdownAnchor(offset = menuOffset) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = menuOffset.value,
            shape = RoundedCornerShape(8.dp),
            containerColor = CardSurface,
            modifier = Modifier
                .height(360.dp)
                .width(240.dp)
        ) {
            ExtraArgPresets.forEach { group ->
                // Group header
                DropdownMenuItem(
                    text = {
                        Text(
                            group.header,
                            color = AccentBlue,
                            fontSize = SettingLabelSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                group.args.forEach { arg ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                arg,
                                color = TextPrimary,
                                fontSize = SettingValueSize
                            )
                        },
                        onClick = {
                            onArgSelected(arg)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ===================================================================
// CPU Chip
// ===================================================================
@Composable
private fun CpuChip(
    index: Int,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isChecked) AccentBlue.copy(alpha = 0.15f) else ChipSurface
    val borderColor = if (isChecked) AccentBlue.copy(alpha = 0.4f) else ChipBorder
    val textColor = if (isChecked) AccentBlue else TextDim

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "CPU $index",
            color = textColor,
            fontSize = SettingLabelSize,
            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ===================================================================
// Reusable Components
// ===================================================================

@Composable
private fun HtmlText(
    html: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit
) {
    val spanned = remember(html) {
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
    }
    val annotated = remember(spanned) {
        buildAnnotatedString {
            val str = spanned.toString().trim()
            append(str)
            for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span).coerceAtMost(str.length)
                if (start >= str.length) continue
                when (span) {
                    is android.text.style.StyleSpan -> {
                        when (span.style) {
                            android.graphics.Typeface.BOLD ->
                                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                            android.graphics.Typeface.ITALIC ->
                                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                            android.graphics.Typeface.BOLD_ITALIC ->
                                addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                        }
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}

@Composable
private fun SubsectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = SettingSectionLabelSize,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
}

// Returns the architecture badge for the slots currently using one of [ids].
@Composable
private fun emulatorUsageLabel(
    state: GameSettingsStateHolder,
    ids: Set<String>
): String? {
    val entries32 = state.emulator32Entries.value
    val entries64 = state.emulator64Entries.value
    val id32 = entries32.getOrNull(state.selectedEmulator.intValue)?.lowercase() ?: ""
    val id64 = entries64.getOrNull(state.selectedEmulator64.intValue)?.lowercase() ?: ""
    val used32 = id32 in ids
    val used64 = id64 in ids
    return when {
        used32 && used64 -> stringResource(R.string.common_ui_64_bit_and_32_bit)
        used64 -> stringResource(R.string.common_ui_64_bit)
        used32 -> stringResource(R.string.common_ui_32_bit)
        else -> null
    }
}

@Composable
private fun EmulatorSectionHeader(title: String, usage: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = SettingSectionLabelSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        if (usage != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBlue.copy(alpha = 0.15f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = usage,
                    color = AccentBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun SettingGroup(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingGroupCorner))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(SettingGroupCorner))
            .padding(SettingGroupPadding)
    ) {
        content()
    }
}

@Composable
private fun SettingDropdown(
    label: String,
    entries: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val menuOffset = rememberSmartDropdownOffset()
    val selectedText = entries.getOrElse(selectedIndex) { "" }
    val alpha = if (enabled) 1f else 0.4f

    Column(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Text(
            label,
            color = TextSecondary,
            fontSize = SettingLabelSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(bottom = SettingTightGap)
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SettingFieldCorner))
                    .background(InputSurface)
                    .border(1.dp, InputBorder, RoundedCornerShape(SettingFieldCorner))
                    .smartDropdownAnchor(enabled = enabled, offset = menuOffset) { expanded = true }
                    .padding(horizontal = SettingFieldHorizontalPadding, vertical = SettingFieldVerticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedText,
                    color = TextPrimary,
                    fontSize = SettingValueSize,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(SettingIconSize)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = menuOffset.value,
                shape = RoundedCornerShape(8.dp),
                containerColor = CardSurface,
            ) {
                entries.forEachIndexed { index, entry ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                entry,
                                color = if (index == selectedIndex) AccentBlue else TextPrimary,
                                fontSize = SettingValueSize,
                                fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                        modifier = if (index == selectedIndex) {
                            Modifier.background(AccentBlue.copy(alpha = 0.06f))
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = TextSecondary,
            fontSize = SettingLabelSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(bottom = SettingTightGap)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = SettingValueSize
            ),
            cursorBrush = SolidColor(AccentBlue),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingFieldCorner))
                .background(InputSurface)
                .border(1.dp, InputBorder, RoundedCornerShape(SettingFieldCorner))
                .padding(horizontal = SettingFieldHorizontalPadding, vertical = SettingFieldVerticalPadding)
        )
    }
}

@Composable
private fun SettingCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(vertical = SettingTightGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = AccentBlue,
                uncheckedColor = CheckBorder,
                checkmarkColor = Color.White
            )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = TextPrimary,
            fontSize = SettingValueSize
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = TextSecondary,
                fontSize = SettingLabelSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.weight(1f))
            // Value badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBlue.copy(alpha = 0.1f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    "$value%",
                    color = AccentBlue,
                    fontSize = SettingLabelSize,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(SettingTightGap))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = TrackInactive
            )
        )
    }
}
