package com.winlator.cmod.runtime.display

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlin.math.roundToInt

// Drawer-local "Carbon Cyan" palette. Fully self-contained — does not pull from
// the shared WinNative theme tokens, so the rest of the app keeps its blue cast
// while the session drawer reads as warm-charcoal with a cool cyan accent.
//
// All surfaces are OPAQUE on purpose: the drawer overlays gameplay, and
// translucent surfaces (the previous design) wash out unpredictably depending
// on what's on screen behind them.

// Drawer-local replacements for the shared theme tokens.
// DrawerAccent matches the shared WinNativeAccent so the drawer reads as part
// of the app, while the surrounding surfaces stay on the warm-charcoal palette.
private val DrawerAccent = Color(0xFF1A9FFF)
private val DrawerTextPrimary = Color(0xFFE8ECF1)
private val DrawerTextSecondary = Color(0xFF8B95A6)
private val DrawerOutline = Color(0xFF2E343F)
private val DrawerBackground = Color(0xFF14161A)

// Container surfaces — opaque warm charcoal. Body sits one stop darker than
// the rail so the tab strip stratifies cleanly without translucency tricks.
internal val PaneSurfaceColor = Color(0xFF14161A)
private val PaneSurfacePressed = Color(0xFF1F232B)

// Top rail surface — one step lighter than the body for a clean section break.
private val TopRailSurfaceColor = Color(0xFF1B1E24)

// Tile / inner-row fills — solid surface steps, not white-overlay tints.
private val TileResting = Color(0xFF22262E)
// Exit tile: deep maroon-charcoal. Signals "destructive" but pairs with the
// cyan accent rather than fighting it (the previous saturated red did).
private val TileExitResting = Color(0xFF3A2125)
private val TileExitPressed = Color(0xFF4A2A30)
// Inner pane sections — slightly darker than tiles to recede into the body.
private val PaneInnerResting = Color(0xFF1A1D23)
private val PaneInnerPressed = Color(0xFF2D323C)
private val RestingCardBorder = Color(0xFF2E343F)
private val DisabledCardBorder = Color(0xFF1F232B)

// Selection / active treatment — colored fill so it's recognizable in
// peripheral vision, plus a blue border. The previous "more white" approach
// disappeared on busy backgrounds.
private val SelectedTileFill = Color(0xFF1F2E45)
private val ActiveCardBorder = DrawerAccent

// Hairline that separates the bottom Pause/Exit row from the body content.
private val BottomDividerColor = Color(0xFF2E343F)

// Tint for exit-tile/icons in the rail and the activator dialog's outline.
private val GlassExitTint = Color(0xFFE07B6B)

// Pane content scales down on short displays (e.g. 5.5" landscape) so text and
// controls keep usable proportions instead of overflowing or wasting space.
// Reference height (520.dp) maps to scale 1.0; smaller heights compress linearly
// down to 0.78. Wider tablets always render at full scale.
private val LocalPaneScale = staticCompositionLocalOf { 1f }
private const val PaneScaleMin = 0.78f
private const val PaneScaleReferenceHeightDp = 520f

private fun computePaneScale(availableHeight: Dp): Float =
    (availableHeight.value / PaneScaleReferenceHeightDp).coerceIn(PaneScaleMin, 1f)

private enum class HUDMetricEditor(
    val minPercent: Int,
    val maxPercent: Int,
) {
    ALPHA(minPercent = 10, maxPercent = 100),
    SCALE(minPercent = 50, maxPercent = 200),
}

internal enum class DrawerPane { INPUT_CONTROLS, HUD, GYROSCOPE, SCREEN_EFFECTS }

// Declarative spec for a top-rail tab that opens a pane below. To add a new pane:
//   1. Add a value to DrawerPane.
//   2. Append a RailPaneSpec entry below.
//   3. Add a `when (openPane)` branch rendering its pane content.
private data class RailPaneSpec(
    val pane: DrawerPane,
    val itemId: Int,
    val labelRes: Int,
    val iconOverride: ImageVector? = null,
)

private val RAIL_PANES =
    listOf(
        RailPaneSpec(
            pane = DrawerPane.INPUT_CONTROLS,
            itemId = R.id.main_menu_input_controls,
            labelRes = R.string.session_drawer_rail_label_input_controls,
            iconOverride = Icons.Outlined.SportsEsports,
        ),
        RailPaneSpec(
            pane = DrawerPane.HUD,
            itemId = R.id.main_menu_fps_monitor,
            labelRes = R.string.session_drawer_rail_label_hud,
        ),
        RailPaneSpec(
            pane = DrawerPane.GYROSCOPE,
            itemId = R.id.main_menu_gyroscope,
            labelRes = R.string.session_drawer_rail_label_gyro,
            iconOverride = Icons.Outlined.ScreenRotation,
        ),
        RailPaneSpec(
            pane = DrawerPane.SCREEN_EFFECTS,
            itemId = R.id.main_menu_screen_effects,
            labelRes = R.string.session_drawer_rail_label_effects,
        ),
    )

private val RAIL_PANE_ITEM_IDS = RAIL_PANES.map { it.itemId }.toSet()
private val PINNED_BOTTOM_ITEM_IDS = setOf(R.id.main_menu_pause, R.id.main_menu_exit)

// Top rail (pane tabs) — horizontal scroll of compact icon+label tiles.
// The tile sizes itself to its content with symmetric padding so the selected
// outline reads as evenly inset on all sides (rather than hugging the icon
// horizontally and the label vertically).
private val TopRailTileMinWidth = 64.dp
private val TopRailTileHorizontalPadding = 10.dp
private val TopRailTileTopPadding = 6.dp
private val TopRailTileBottomPadding = 4.dp
private val TopRailTileSpacing = 6.dp

// Action cards (formerly the right drawer's items) — the body's default view.
private const val ActionCardColumns = 3
private val ActionCardMinHeight = 72.dp
private val ActionCardSpacing = 8.dp

data class XServerDrawerItem(
    val itemId: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val enabled: Boolean = true,
)

data class XServerDrawerState(
    val items: List<XServerDrawerItem>,
    val hudTransparency: Float = 1.0f,
    val hudScale: Float = 1.0f,
    val hudElements: BooleanArray = booleanArrayOf(true, true, true, true, true, true, true),
    val dualSeriesBatteryEnabled: Boolean = false,
    val hudCardExpanded: Boolean = false,
    val gyroscopeEnabled: Boolean = false,
    val gyroscopeModeIndex: Int = 0,
    val gyroscopeActivatorLabel: String = "",
    val rightStickGyroEnabled: Boolean = false,
    val gyroMouseEnabled: Boolean = false,
    val gyroMouseScale: Float = 50.0f,
    val gyroXSensitivity: Float = 1.0f,
    val gyroYSensitivity: Float = 1.0f,
    val gyroSmoothing: Float = 0.9f,
    val gyroDeadzone: Float = 0.05f,
    val invertGyroX: Boolean = false,
    val invertGyroY: Boolean = false,
    val gyroscopeCardExpanded: Boolean = false,
    val fpsLimit: Int = 0,
    val screenEffectsCardExpanded: Boolean = false,
    val fsrEnabled: Boolean = false,
    val fsrMode: Int = 0,
    val fsrSharpness: Int = 100,
    val colorProfile: Int = 0,
    val inputControlsProfileNames: List<String> = emptyList(),
    val inputControlsSelectedProfileIndex: Int = 0,
    val inputControlsShowOverlay: Boolean = false,
    val inputControlsTapToClick: Boolean = true,
    val inputControlsOverlayOpacity: Float = 0.4f,
    val inputControlsTouchscreenHaptics: Boolean = false,
    val inputControlsGamepadVibration: Boolean = true,
)

class XServerDrawerStateHolder(
    initialState: XServerDrawerState,
) {
    var state by mutableStateOf(initialState, neverEqualPolicy())
    private var drawerOpen by mutableStateOf(false)
    internal var openPane by mutableStateOf<DrawerPane?>(null)
    private var paneVisibilityListener: ((Boolean) -> Unit)? = null

    val isDrawerOpen: Boolean
        get() = drawerOpen

    fun openDrawer() {
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        openPane = null
    }

    fun isPaneOpen(): Boolean = openPane != null

    fun closeOpenPane() {
        if (openPane != null) {
            openPane = null
            paneVisibilityListener?.invoke(false)
        }
    }

    internal fun setPaneVisibilityListener(listener: (Boolean) -> Unit) {
        paneVisibilityListener = listener
    }

    internal fun clearPaneVisibilityListener() {
        paneVisibilityListener = null
    }

    internal fun setOpenPaneAndNotify(newPane: DrawerPane?) {
        val wasVisible = openPane != null
        val nowVisible = newPane != null
        openPane = newPane
        if (wasVisible != nowVisible) paneVisibilityListener?.invoke(nowVisible)
    }

}

interface XServerDrawerActionListener {
    fun onActionSelected(itemId: Int)

    fun onHUDElementToggled(
        index: Int,
        enabled: Boolean,
    )

    fun onHUDTransparencyChanged(transparency: Float)

    fun onHUDScaleChanged(scale: Float)

    fun onDualSeriesBatteryChanged(enabled: Boolean)

    fun onHUDCardExpandedChanged(expanded: Boolean)

    fun onGyroscopeEnabledChanged(enabled: Boolean)

    fun onGyroscopeModeSelected(mode: Int)

    fun onGyroscopeActivatorSelected(keycode: Int)

    fun onRightStickGyroChanged(enabled: Boolean)

    fun onGyroMouseEnabledChanged(enabled: Boolean)

    fun onGyroMouseScaleChanged(scale: Float)

    fun onGyroXSensitivityChanged(sensitivity: Float)

    fun onGyroYSensitivityChanged(sensitivity: Float)

    fun onGyroSmoothingChanged(smoothing: Float)

    fun onGyroDeadzoneChanged(deadzone: Float)

    fun onInvertGyroXChanged(enabled: Boolean)

    fun onInvertGyroYChanged(enabled: Boolean)

    fun onGyroscopeCardExpandedChanged(expanded: Boolean)

    fun onFPSLimitChanged(limit: Int)

    fun onScreenEffectsCardExpandedChanged(expanded: Boolean)

    fun onFSREnabledChanged(enabled: Boolean)

    fun onFSRModeSelected(mode: Int)

    fun onFSRSharpnessChanged(sharpness: Int)

    fun onColorProfileSelected(profile: Int)

    fun onInputControlsProfileSelected(index: Int)

    fun onInputControlsShowOverlayChanged(enabled: Boolean)

    fun onInputControlsTapToClickChanged(enabled: Boolean)

    fun onInputControlsOverlayOpacityChanged(opacity: Float)

    fun onInputControlsTouchscreenHapticsChanged(enabled: Boolean)

    fun onInputControlsGamepadVibrationChanged(enabled: Boolean)

    fun onInputControlsEditClick()
}

fun buildXServerDrawerState(
    context: Context,
    relativeMouseEnabled: Boolean,
    mouseDisabled: Boolean,
    fpsMonitorEnabled: Boolean,
    paused: Boolean,
    showMagnifier: Boolean,
    showLogs: Boolean,
    nativeRenderingEnabled: Boolean,
    nativeRenderingTitle: String,
    nativeRenderingSubtitle: String,
    hudTransparency: Float = 1.0f,
    hudScale: Float = 1.0f,
    hudElements: BooleanArray = booleanArrayOf(true, true, true, true, true, true, true),
    dualSeriesBatteryEnabled: Boolean = false,
    hudCardExpanded: Boolean = false,
    gyroscopeEnabled: Boolean = false,
    gyroscopeModeIndex: Int = 0,
    gyroscopeActivatorLabel: String = "",
    rightStickGyroEnabled: Boolean = false,
    gyroMouseEnabled: Boolean = false,
    gyroMouseScale: Float = 50.0f,
    gyroXSensitivity: Float = 1.0f,
    gyroYSensitivity: Float = 1.0f,
    gyroSmoothing: Float = 0.9f,
    gyroDeadzone: Float = 0.05f,
    invertGyroX: Boolean = false,
    invertGyroY: Boolean = false,
    gyroscopeCardExpanded: Boolean = false,
    fpsLimit: Int = 0,
    screenEffectsCardExpanded: Boolean = false,
    fsrEnabled: Boolean = false,
    fsrMode: Int = 0,
    fsrSharpness: Int = 100,
    colorProfile: Int = 0,
    inputControlsProfileNames: List<String> = emptyList(),
    inputControlsSelectedProfileIndex: Int = 0,
    inputControlsShowOverlay: Boolean = false,
    inputControlsTapToClick: Boolean = true,
    inputControlsOverlayOpacity: Float = 0.4f,
    inputControlsTouchscreenHaptics: Boolean = false,
    inputControlsGamepadVibration: Boolean = true,
): XServerDrawerState {
    val items =
        mutableListOf(
            XServerDrawerItem(
                itemId = R.id.main_menu_fps_monitor,
                title = context.getString(R.string.session_drawer_fps_monitor),
                subtitle =
                    if (fpsMonitorEnabled) context.getString(R.string.common_ui_enabled) else context.getString(R.string.common_ui_disabled),
                icon = Icons.Outlined.Monitor,
                active = fpsMonitorEnabled,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_keyboard,
                title = context.getString(R.string.session_drawer_keyboard),
                subtitle = "",
                icon = Icons.Outlined.Keyboard,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_input_controls,
                title = context.getString(R.string.common_ui_input_controls),
                subtitle = "",
                icon = Icons.Outlined.SportsEsports,
                active = inputControlsSelectedProfileIndex > 0,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_gyroscope,
                title = "Gyroscope",
                subtitle = "",
                icon = Icons.Outlined.SportsEsports,
                active = gyroscopeEnabled,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_relative_mouse_movement,
                title = context.getString(R.string.session_drawer_relative_mouse_movement),
                subtitle =
                    if (relativeMouseEnabled) context.getString(R.string.common_ui_enabled) else context.getString(R.string.common_ui_disabled),
                icon = Icons.Outlined.Mouse,
                active = relativeMouseEnabled,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_disable_mouse,
                title = context.getString(R.string.session_drawer_mouse_input),
                subtitle =
                    if (mouseDisabled) context.getString(R.string.common_ui_disabled) else context.getString(R.string.common_ui_enabled),
                icon = Icons.Outlined.Mouse,
                active = !mouseDisabled,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_toggle_fullscreen,
                title = context.getString(R.string.session_drawer_toggle_fullscreen),
                subtitle = "",
                icon = Icons.Outlined.Fullscreen,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_screen_effects,
                title = context.getString(R.string.session_drawer_screen_effects),
                subtitle = context.getString(R.string.session_drawer_screen_effects_subtitle),
                icon = Icons.Outlined.Tune,
                active = fsrEnabled || colorProfile > 0,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_native_rendering,
                title = nativeRenderingTitle,
                subtitle = nativeRenderingSubtitle,
                icon = Icons.Outlined.Memory,
                active = nativeRenderingEnabled,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_pause,
                title = if (paused) context.getString(R.string.session_drawer_resume) else context.getString(R.string.session_drawer_pause),
                subtitle =
                    if (paused) context.getString(R.string.session_drawer_wine_processes_paused) else context.getString(R.string.session_drawer_pause_all_wine_processes),
                icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                active = paused,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_pip_mode,
                title = context.getString(R.string.session_drawer_picture_in_picture),
                subtitle = "",
                icon = Icons.Outlined.PictureInPictureAlt,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_task_manager,
                title = context.getString(R.string.session_task_title),
                subtitle = "",
                icon = Icons.AutoMirrored.Outlined.ViewList,
            ),
        )

    if (showMagnifier) {
        items +=
            XServerDrawerItem(
                itemId = R.id.main_menu_magnifier,
                title = context.getString(R.string.session_drawer_magnifier),
                subtitle = "",
                icon = Icons.Outlined.ZoomIn,
            )
    }

    if (showLogs) {
        // Inserted at index 0 so it's the first card in the filtered grid
        // (the rail-pane items at the top of the list — fps_monitor, gyroscope,
        // screen_effects — are filtered out of the cards section).
        items.add(
            0,
            XServerDrawerItem(
                itemId = R.id.main_menu_logs,
                title = context.getString(R.string.session_drawer_logs),
                subtitle = "",
                icon = Icons.Outlined.Terminal,
            ),
        )
    }

    items +=
        XServerDrawerItem(
            itemId = R.id.main_menu_exit,
            title = context.getString(R.string.common_ui_exit),
            subtitle = context.getString(R.string.session_drawer_exit_subtitle),
            icon = Icons.AutoMirrored.Outlined.ExitToApp,
        )

    return XServerDrawerState(
        items = items,
        hudTransparency = hudTransparency,
        hudScale = hudScale,
        hudElements = hudElements,
        dualSeriesBatteryEnabled = dualSeriesBatteryEnabled,
        hudCardExpanded = hudCardExpanded,
        gyroscopeEnabled = gyroscopeEnabled,
        gyroscopeModeIndex = gyroscopeModeIndex,
        gyroscopeActivatorLabel = gyroscopeActivatorLabel,
        rightStickGyroEnabled = rightStickGyroEnabled,
        gyroMouseEnabled = gyroMouseEnabled,
        gyroMouseScale = gyroMouseScale,
        gyroXSensitivity = gyroXSensitivity,
        gyroYSensitivity = gyroYSensitivity,
        gyroSmoothing = gyroSmoothing,
        gyroDeadzone = gyroDeadzone,
        invertGyroX = invertGyroX,
        invertGyroY = invertGyroY,
        gyroscopeCardExpanded = gyroscopeCardExpanded,
        fpsLimit = fpsLimit,
        screenEffectsCardExpanded = screenEffectsCardExpanded,
        fsrEnabled = fsrEnabled,
        fsrMode = fsrMode,
        fsrSharpness = fsrSharpness,
        colorProfile = colorProfile,
        inputControlsProfileNames = inputControlsProfileNames,
        inputControlsSelectedProfileIndex = inputControlsSelectedProfileIndex,
        inputControlsShowOverlay = inputControlsShowOverlay,
        inputControlsTapToClick = inputControlsTapToClick,
        inputControlsOverlayOpacity = inputControlsOverlayOpacity,
        inputControlsTouchscreenHaptics = inputControlsTouchscreenHaptics,
        inputControlsGamepadVibration = inputControlsGamepadVibration,
    )
}

fun setupXServerDrawerComposeView(
    composeView: ComposeView,
    stateHolder: XServerDrawerStateHolder,
    _activity: Activity,
    listener: XServerDrawerActionListener,
    onDismiss: Runnable,
    onPaneVisibilityChanged: (Boolean) -> Unit = {},
) {
    stateHolder.setPaneVisibilityListener(onPaneVisibilityChanged)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        WinNativeTheme {
            XServerDrawerContent(
                state = stateHolder.state,
                openPane = stateHolder.openPane,
                onOpenPaneChange = { stateHolder.setOpenPaneAndNotify(it) },
                listener = listener,
                onDismiss = { onDismiss.run() },
            )
        }
    }
}

@Composable
internal fun XServerDrawerContent(
    state: XServerDrawerState,
    openPane: DrawerPane?,
    onOpenPaneChange: (DrawerPane?) -> Unit,
    listener: XServerDrawerActionListener,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val paneScale = computePaneScale(maxHeight)
            CompositionLocalProvider(LocalPaneScale provides paneScale) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopRail(
                        state = state,
                        openPane = openPane,
                        onTabClick = { spec ->
                            onOpenPaneChange(if (openPane == spec.pane) null else spec.pane)
                        },
                        onMenuClick = { onOpenPaneChange(null) },
                    )

                    ThinDivider()

                    Box(
                        modifier =
                            Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth(),
                    ) {
                        Crossfade(
                            targetState = openPane,
                            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                            label = "drawerBody",
                        ) { pane ->
                            when (pane) {
                                DrawerPane.INPUT_CONTROLS -> InputControlsPaneContent(state = state, listener = listener)
                                DrawerPane.HUD -> HUDPaneContent(state = state, listener = listener)
                                DrawerPane.GYROSCOPE -> GyroscopePaneContent(state = state, listener = listener)
                                DrawerPane.SCREEN_EFFECTS -> ScreenEffectsPaneContent(state = state, listener = listener)
                                null ->
                                    ActionCardGrid(
                                        state = state,
                                        listener = listener,
                                        onActionInvoked = onDismiss,
                                    )
                            }
                        }
                    }

                    if (openPane == null) {
                        ThinDivider()

                        BottomActions(
                            state = state,
                            listener = listener,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRail(
    state: XServerDrawerState,
    openPane: DrawerPane?,
    onTabClick: (RailPaneSpec) -> Unit,
    onMenuClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val activeSpecs = RAIL_PANES.filter { spec -> state.items.any { it.itemId == spec.itemId } }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(TopRailSurfaceColor)
                .padding(
                    start = (10f * paneScale).dp,
                    end = (10f * paneScale).dp,
                    top = (5f * paneScale).dp,
                    bottom = (2f * paneScale).dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(TopRailTileSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Menu tile is selected whenever no pane is open — the body shows the action card grid.
        TopRailTile(
            icon = Icons.Outlined.Apps,
            label = stringResource(R.string.session_drawer_main_menu_title),
            active = false,
            selected = openPane == null,
            onClick = onMenuClick,
            tileKey = "menu",
        )
        activeSpecs.forEach { spec ->
            val item = state.items.first { it.itemId == spec.itemId }
            TopRailTile(
                icon = spec.iconOverride ?: item.icon,
                label = stringResource(spec.labelRes),
                active = item.active,
                selected = openPane == spec.pane,
                onClick = { onTabClick(spec) },
                tileKey = item.itemId.toString(),
            )
        }
    }
}

@Composable
private fun TopRailTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    tileKey: String,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value

    val minWidth = TopRailTileMinWidth * paneScale
    val horizontalPadding = TopRailTileHorizontalPadding * paneScale
    val topPadding = TopRailTileTopPadding * paneScale
    val bottomPadding = TopRailTileBottomPadding * paneScale
    val cornerRadius = (12f * paneScale).dp

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "topRailScale_$tileKey",
    )
    // Selected tiles use a subtle neutral fill rather than an accent outline so the
    // rail stays quiet; the active pane is signalled by fill + tint, not a blue border.
    val bgColor by animateColorAsState(
        targetValue =
            when {
                pressed -> PaneSurfacePressed
                selected -> SelectedTileFill
                else -> Color.Transparent
            },
        animationSpec = tween(120),
        label = "topRailBg_$tileKey",
    )
    val borderColor by animateColorAsState(
        targetValue = Color.Transparent,
        animationSpec = tween(120),
        label = "topRailBorder_$tileKey",
    )
    val tint by animateColorAsState(
        targetValue =
            when {
                selected || active -> DrawerAccent
                else -> DrawerTextPrimary
            },
        animationSpec = tween(120),
        label = "topRailTint_$tileKey",
    )

    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier =
            Modifier
                .defaultMinSize(minWidth = minWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size((22f * paneScale).dp),
        )
        Spacer(Modifier.height((2f * paneScale).dp))
        Text(
            text = label,
            color = DrawerTextPrimary,
            fontSize = (12f * paneScale).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCardGrid(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    onActionInvoked: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val cards =
        state.items.filter {
            it.itemId !in RAIL_PANE_ITEM_IDS && it.itemId !in PINNED_BOTTOM_ITEM_IDS
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = (10f * paneScale).dp, vertical = (10f * paneScale).dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ActionCardSpacing),
            verticalArrangement = Arrangement.spacedBy(ActionCardSpacing),
            maxItemsInEachRow = ActionCardColumns,
        ) {
            cards.forEach { item ->
                val label = railLabelResFor(item.itemId)?.let { stringResource(it) } ?: item.title
                ActionCard(
                    item = item,
                    label = label,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = ActionCardMinHeight * paneScale),
                    onClick = {
                        onActionInvoked()
                        listener.onActionSelected(item.itemId)
                    },
                )
            }
            // Pad the trailing row so a lone card (e.g. Logs when total isn't a
            // multiple of ActionCardColumns) stays 1/3 width instead of stretching
            // across the whole row via .weight(1f).
            val trailing = (ActionCardColumns - cards.size % ActionCardColumns) % ActionCardColumns
            repeat(trailing) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionCard(
    item: XServerDrawerItem,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val enabled = item.enabled

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "actionCardScale_${item.itemId}",
    )
    val bgColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> Color(0x05FFFFFF)
                pressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(120),
        label = "actionCardBg_${item.itemId}",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> DisabledCardBorder
                item.active -> ActiveCardBorder
                else -> RestingCardBorder
            },
        animationSpec = tween(120),
        label = "actionCardBorder_${item.itemId}",
    )
    val tint by animateColorAsState(
        targetValue =
            when {
                !enabled -> DrawerTextSecondary.copy(alpha = 0.45f)
                item.active -> DrawerAccent
                else -> DrawerTextPrimary
            },
        animationSpec = tween(120),
        label = "actionCardTint_${item.itemId}",
    )

    val cornerRadius = (12f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = (8f * paneScale).dp, horizontal = (4f * paneScale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = tint,
            modifier = Modifier.size((24f * paneScale).dp),
        )
        Spacer(Modifier.height((4f * paneScale).dp))
        Text(
            text = label,
            color = if (enabled) DrawerTextPrimary else DrawerTextSecondary.copy(alpha = 0.45f),
            fontSize = (13f * paneScale).sp,
            fontWeight = if (item.active) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.2.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomActions(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    val paneScale = LocalPaneScale.current
    val pause = state.items.firstOrNull { it.itemId == R.id.main_menu_pause }
    val exit = state.items.firstOrNull { it.itemId == R.id.main_menu_exit }
    if (pause == null && exit == null) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = (10f * paneScale).dp, vertical = (8f * paneScale).dp),
        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pause != null) {
            BottomActionButton(
                item = pause,
                label = stringResource(if (pause.active) R.string.session_drawer_resume else R.string.session_drawer_pause),
                isExit = false,
                modifier = Modifier.weight(1f),
                onClick = { listener.onActionSelected(pause.itemId) },
            )
        }
        if (exit != null) {
            BottomActionButton(
                item = exit,
                label = stringResource(R.string.common_ui_exit),
                isExit = true,
                modifier = Modifier.weight(1f),
                onClick = { listener.onActionSelected(exit.itemId) },
            )
        }
    }
}

@Composable
private fun BottomActionButton(
    item: XServerDrawerItem,
    label: String,
    isExit: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value

    val bgColor by animateColorAsState(
        targetValue =
            when {
                isExit && pressed -> TileExitPressed
                isExit -> TileExitResting
                pressed -> PaneSurfacePressed
                else -> PaneInnerResting
            },
        animationSpec = tween(120),
        label = "bottomActionBg_${item.itemId}",
    )
    val borderColor =
        when {
            isExit -> GlassExitTint.copy(alpha = 0.34f)
            item.active -> ActiveCardBorder
            else -> RestingCardBorder
        }
    val tint =
        when {
            isExit -> GlassExitTint
            item.active -> DrawerAccent
            else -> DrawerTextPrimary
        }

    val cornerRadius = (14f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = tint,
            modifier = Modifier.size((18f * paneScale).dp),
        )
        Spacer(Modifier.width((8f * paneScale).dp))
        Text(
            text = label,
            color = tint,
            fontSize = (13f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThinDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BottomDividerColor),
    )
}

// Short labels for action cards. Each fits on one line inside the card; falls back
// to item.title for items without an entry.
private fun railLabelResFor(itemId: Int): Int? =
    when (itemId) {
        R.id.main_menu_keyboard -> R.string.session_drawer_rail_label_keyboard
        R.id.main_menu_input_controls -> R.string.session_drawer_rail_label_input_controls
        R.id.main_menu_relative_mouse_movement -> R.string.session_drawer_rail_label_relative_mouse
        R.id.main_menu_disable_mouse -> R.string.session_drawer_rail_label_mouse
        R.id.main_menu_toggle_fullscreen -> R.string.session_drawer_rail_label_fullscreen
        R.id.main_menu_pip_mode -> R.string.session_drawer_rail_label_pip
        R.id.main_menu_native_rendering -> R.string.session_drawer_rail_label_native
        R.id.main_menu_magnifier -> R.string.session_drawer_rail_label_magnifier
        R.id.main_menu_task_manager -> R.string.session_drawer_rail_label_task_manager
        R.id.main_menu_logs -> R.string.session_drawer_rail_label_logs
        else -> null
    }

@Composable
private fun PaneEnableRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Pane-header toggles render identically to the in-pane DrawerBooleanRow so the
    // FPS Monitor / Gyroscope headers visually match Dual Series Battery / Super
    // Resolution rather than letting the Switch dominate a single-line title.
    DrawerBooleanRow(
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun HUDPaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    var activeEditor by remember { mutableStateOf<HUDMetricEditor?>(null) }
    val elementNames =
        listOf(
            stringResource(R.string.session_drawer_hud_element_fps),
            stringResource(R.string.session_drawer_hud_element_api),
            stringResource(R.string.session_drawer_hud_element_gpu),
            stringResource(R.string.session_drawer_hud_element_cpu),
            stringResource(R.string.session_drawer_hud_element_ram),
            stringResource(R.string.session_drawer_hud_element_battery),
            stringResource(R.string.session_drawer_hud_element_graph),
        )
    val active =
        state.items.firstOrNull { it.itemId == R.id.main_menu_fps_monitor }?.active ?: false

    activeEditor?.let { editor ->
        HUDMetricInputDialog(
            editor = editor,
            initialPercent =
                when (editor) {
                    HUDMetricEditor.ALPHA -> (state.hudTransparency * 100).roundToInt()
                    HUDMetricEditor.SCALE -> (state.hudScale * 100).roundToInt()
                },
            onDismiss = { activeEditor = null },
            onConfirm = { enteredPercent ->
                activeEditor = null
                when (editor) {
                    HUDMetricEditor.ALPHA -> {
                        listener.onHUDTransparencyChanged(enteredPercent.coerceIn(editor.minPercent, editor.maxPercent) / 100f)
                    }
                    HUDMetricEditor.SCALE -> {
                        listener.onHUDScaleChanged(enteredPercent.coerceIn(editor.minPercent, editor.maxPercent) / 100f)
                    }
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
            PaneEnableRow(
                title = stringResource(R.string.session_drawer_fps_monitor),
                checked = active,
                onCheckedChange = { listener.onActionSelected(R.id.main_menu_fps_monitor) },
            )

            if (active) {
                DrawerSliderRow(
                    label = stringResource(R.string.session_drawer_hud_alpha),
                    valueText = "${(state.hudTransparency * 100).toInt()}%",
                    value = state.hudTransparency,
                    valueRange = 0.1f..1f,
                    steps = 8,
                    onValueClick = { activeEditor = HUDMetricEditor.ALPHA },
                    onValueChange = { listener.onHUDTransparencyChanged(it.snapToStep(0.1f, 0.1f, 1f)) },
                )

                DrawerSliderRow(
                    label = stringResource(R.string.session_drawer_hud_scale),
                    valueText = "${(state.hudScale * 100).toInt()}%",
                    value = state.hudScale,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    onValueClick = { activeEditor = HUDMetricEditor.SCALE },
                    onValueChange = { listener.onHUDScaleChanged(it.snapToStep(0.1f, 0.5f, 2.0f)) },
                )

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_drawer_hud_elements))
                    ChipFlow {
                        elementNames.forEachIndexed { index, name ->
                            HUDToggleChip(
                                label = name,
                                checked = state.hudElements[index],
                                onClick = { listener.onHUDElementToggled(index, !state.hudElements[index]) },
                            )
                        }
                    }
                }

                DrawerBooleanRow(
                    title = stringResource(R.string.session_drawer_dual_series_battery),
                    checked = state.dualSeriesBatteryEnabled,
                    onCheckedChange = listener::onDualSeriesBatteryChanged,
                )

                FPSLimiterSelection(
                    currentLimit = state.fpsLimit,
                    onLimitSelected = listener::onFPSLimitChanged,
                )
            }
            }
        }
    }
}

@Composable
private fun GyroscopePaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    var calibrateExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
            PaneEnableRow(
                title = stringResource(R.string.session_gyroscope_title),
                checked = state.gyroscopeEnabled,
                onCheckedChange = listener::onGyroscopeEnabledChanged,
            )

            if (state.gyroscopeEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_gyroscope_mode))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
                    ) {
                        listOf(
                            stringResource(R.string.session_gyroscope_hold),
                            stringResource(R.string.session_gyroscope_toggle),
                        ).forEachIndexed { index, label ->
                            HUDToggleChip(
                                label = label,
                                checked = state.gyroscopeModeIndex == index,
                                onClick = { listener.onGyroscopeModeSelected(index) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_gyroscope_activator_button))
                    GyroscopeActivatorDropdown(
                        currentLabel = state.gyroscopeActivatorLabel,
                        onSelected = listener::onGyroscopeActivatorSelected,
                    )
                }

                DrawerBooleanRow(
                    title = stringResource(R.string.session_gyroscope_enable_right_stick),
                    checked = state.rightStickGyroEnabled,
                    onCheckedChange = listener::onRightStickGyroChanged,
                )

                DrawerBooleanRow(
                    title = stringResource(R.string.session_gyroscope_experimental_mouse_movement),
                    checked = state.gyroMouseEnabled,
                    onCheckedChange = listener::onGyroMouseEnabledChanged,
                )

                if (state.gyroMouseEnabled) {
                    DrawerSliderRow(
                        label = stringResource(R.string.session_gyroscope_mouse_scale),
                        valueText = "${state.gyroMouseScale.toInt()}%",
                        value = state.gyroMouseScale,
                        valueRange = 0f..200f,
                        steps = 199,
                        onValueChange = { listener.onGyroMouseScaleChanged(it.roundToInt().toFloat()) },
                    )
                }

                ExpandableSection(
                    title = stringResource(R.string.session_drawer_calibrate_advanced),
                    expanded = calibrateExpanded,
                    onToggle = { calibrateExpanded = !calibrateExpanded },
                ) {
                    DrawerSliderRow(
                        label = stringResource(R.string.session_gyroscope_x_sensitivity),
                        valueText = "${(state.gyroXSensitivity * 100).toInt()}%",
                        value = state.gyroXSensitivity,
                        valueRange = 0f..2f,
                        steps = 199,
                        onValueChange = { listener.onGyroXSensitivityChanged(it) },
                    )

                    DrawerSliderRow(
                        label = stringResource(R.string.session_gyroscope_y_sensitivity),
                        valueText = "${(state.gyroYSensitivity * 100).toInt()}%",
                        value = state.gyroYSensitivity,
                        valueRange = 0f..2f,
                        steps = 199,
                        onValueChange = { listener.onGyroYSensitivityChanged(it) },
                    )

                    DrawerSliderRow(
                        label = stringResource(R.string.session_gyroscope_smoothing),
                        valueText = "${(state.gyroSmoothing * 100).toInt()}%",
                        value = state.gyroSmoothing,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { listener.onGyroSmoothingChanged(it) },
                    )

                    DrawerSliderRow(
                        label = stringResource(R.string.session_gyroscope_deadzone),
                        valueText = "${(state.gyroDeadzone * 100).toInt()}%",
                        value = state.gyroDeadzone,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { listener.onGyroDeadzoneChanged(it) },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
                    ) {
                        HUDToggleChip(
                            label = stringResource(R.string.session_gyroscope_invert_x),
                            checked = state.invertGyroX,
                            onClick = { listener.onInvertGyroXChanged(!state.invertGyroX) },
                            modifier = Modifier.weight(1f),
                        )
                        HUDToggleChip(
                            label = stringResource(R.string.session_gyroscope_invert_y),
                            checked = state.invertGyroY,
                            onClick = { listener.onInvertGyroYChanged(!state.invertGyroY) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    WinNativeDialogButton(
                        label = stringResource(R.string.session_gyroscope_reset_stick),
                        textColor = DrawerAccent,
                        backgroundColor = DrawerAccent.copy(alpha = 0.12f),
                        borderColor = DrawerAccent.copy(alpha = 0.3f),
                        onClick = { listener.onActionSelected(R.id.main_menu_gyroscope_reset) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun InputControlsPaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.input_controls_editor_select_profile))
                    InputControlsProfileSelector(
                        profileNames = state.inputControlsProfileNames,
                        selectedIndex = state.inputControlsSelectedProfileIndex,
                        onProfileSelected = listener::onInputControlsProfileSelected,
                        onEditClick = listener::onInputControlsEditClick,
                    )
                }

                DrawerBooleanRow(
                    title = stringResource(R.string.session_drawer_show_touchscreen_controls),
                    checked = state.inputControlsShowOverlay,
                    onCheckedChange = listener::onInputControlsShowOverlayChanged,
                )

                if (state.inputControlsShowOverlay) {
                    DrawerSliderRow(
                        label = stringResource(R.string.input_controls_editor_overlay_opacity),
                        valueText = "${(state.inputControlsOverlayOpacity * 100).toInt()}%",
                        value = state.inputControlsOverlayOpacity,
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                        onValueChange = listener::onInputControlsOverlayOpacityChanged,
                    )
                    Spacer(Modifier.height(4.dp))

                    DrawerBooleanRow(
                        title = stringResource(R.string.input_controls_tap_to_click),
                        checked = state.inputControlsTapToClick,
                        onCheckedChange = listener::onInputControlsTapToClickChanged,
                    )
                }

                DrawerBooleanRow(
                    title = stringResource(R.string.settings_general_touchscreen_haptics),
                    checked = state.inputControlsTouchscreenHaptics,
                    onCheckedChange = listener::onInputControlsTouchscreenHapticsChanged,
                )

                DrawerBooleanRow(
                    title = stringResource(R.string.session_gamepad_enable_vibration),
                    checked = state.inputControlsGamepadVibration,
                    onCheckedChange = listener::onInputControlsGamepadVibrationChanged,
                )
            }
        }
    }
}

@Composable
private fun InputControlsProfileSelector(
    profileNames: List<String>,
    selectedIndex: Int,
    onProfileSelected: (Int) -> Unit,
    onEditClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    var expanded by remember { mutableStateOf(false) }
    val disabledPlaceholder = stringResource(R.string.common_ui_disabled_placeholder)
    val selectedText = profileNames.getOrElse(selectedIndex) { disabledPlaceholder }

    val cornerRadius = (14f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(140),
        label = "inputControlsProfileBg",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(bgColor)
                        .border(1.dp, RestingCardBorder, shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { expanded = true }
                        .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedText,
                    color = DrawerTextPrimary,
                    fontSize = (14f * paneScale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = DrawerTextSecondary,
                    modifier = Modifier.size((22f * paneScale).dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier =
                    Modifier
                        .background(PaneSurfaceColor)
                        .heightIn(max = 280.dp),
            ) {
                profileNames.forEachIndexed { index, name ->
                    val isSelected = index == selectedIndex
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                color = if (isSelected) DrawerAccent else DrawerTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        trailingIcon =
                            if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = DrawerAccent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        onClick = {
                            onProfileSelected(index)
                            expanded = false
                        },
                        colors =
                            MenuDefaults.itemColors(
                                textColor = DrawerTextPrimary,
                            ),
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .size((44f * paneScale).dp)
                    .clip(shape)
                    .background(PaneInnerResting)
                    .border(1.dp, RestingCardBorder, shape)
                    .clickable(onClick = onEditClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.common_ui_settings),
                tint = DrawerTextPrimary,
                modifier = Modifier.size((20f * paneScale).dp),
            )
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "expandableRotation",
    )
    val headerInteractionSource = remember { MutableInteractionSource() }
    val headerPressed = headerInteractionSource.collectIsPressedAsState().value
    val headerBg by animateColorAsState(
        targetValue =
            when {
                headerPressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(140),
        label = "expandableHeaderBg",
    )
    val headerBorder by animateColorAsState(
        targetValue = if (expanded) DrawerAccent else RestingCardBorder,
        animationSpec = tween(140),
        label = "expandableHeaderBorder",
    )
    val headerShape = RoundedCornerShape((12f * paneScale).dp)
    Column(verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(headerBg)
                    .border(1.dp, headerBorder, headerShape)
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                        onClick = onToggle,
                    )
                    .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (expanded) DrawerAccent else DrawerTextPrimary,
                fontSize = (14f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (expanded) DrawerAccent else DrawerTextSecondary,
                modifier =
                    Modifier
                        .size((18f * paneScale).dp)
                        .graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy((12f * paneScale).dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ScreenEffectsPaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
            DrawerBooleanRow(
                title = stringResource(R.string.session_drawer_super_resolution),
                checked = state.fsrEnabled,
                onCheckedChange = listener::onFSREnabledChanged,
            )

            if (state.fsrEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_drawer_upscaler_mode))
                    val upscaleLabels =
                        listOf(
                            stringResource(R.string.session_drawer_upscaler_fsr),
                            stringResource(R.string.session_drawer_upscaler_dls),
                        )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
                    ) {
                        upscaleLabels.forEachIndexed { index, label ->
                            HUDToggleChip(
                                label = label,
                                checked = state.fsrMode == index,
                                onClick = { listener.onFSRModeSelected(index) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    DrawerSliderRow(
                        label = stringResource(R.string.session_drawer_sharpness),
                        valueText = "${state.fsrSharpness}%",
                        value = state.fsrSharpness.toFloat(),
                        valueRange = 0f..100f,
                        steps = 99,
                        onValueChange = { listener.onFSRSharpnessChanged(it.roundToInt()) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                PaneSectionLabel(stringResource(R.string.session_drawer_color_profile))

                val profiles =
                    listOf(
                        stringResource(R.string.session_drawer_color_profile_disabled),
                        stringResource(R.string.session_drawer_color_profile_hdr),
                        stringResource(R.string.session_drawer_color_profile_natural),
                        stringResource(R.string.session_drawer_color_profile_crt),
                    )

                ChipFlow {
                    profiles.forEachIndexed { index, label ->
                        HUDToggleChip(
                            label = label,
                            checked = state.colorProfile == index,
                            onClick = { listener.onColorProfileSelected(index) },
                        )
                    }
                }
            }
            }
        }
    }
}


@Composable
private fun PaneSectionLabel(text: String) {
    val paneScale = LocalPaneScale.current
    Text(
        text = text,
        color = DrawerTextPrimary,
        fontSize = (12f * paneScale).sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    )
}

@Composable
private fun GyroscopeActivatorDropdown(
    currentLabel: String,
    onSelected: (Int) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val names = stringArrayResource(R.array.button_options)
    val keycodes = integerArrayResource(R.array.button_keycodes)
    var expanded by remember { mutableStateOf(false) }

    val cornerRadius = (14f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(140),
        label = "gyroActivatorDropdownBg",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(bgColor)
                    .border(1.dp, RestingCardBorder, shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { expanded = true }
                    .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentLabel,
                color = DrawerTextPrimary,
                fontSize = (14f * paneScale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = DrawerTextSecondary,
                modifier = Modifier.size((22f * paneScale).dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .background(PaneSurfaceColor)
                    .heightIn(max = 280.dp),
        ) {
            names.forEachIndexed { index, name ->
                val isSelected = name == currentLabel
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            color = if (isSelected) DrawerAccent else DrawerTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    trailingIcon =
                        if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = DrawerAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    onClick = {
                        onSelected(keycodes[index])
                        expanded = false
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = DrawerTextPrimary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DrawerMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "drawerMetricScale_$label",
    )
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(140),
        label = "drawerMetricBg",
    )

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape((12f * paneScale).dp))
                .background(bgColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = (10f * paneScale).dp, vertical = (7f * paneScale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = DrawerTextPrimary,
            fontSize = (13f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DrawerSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueClick: (() -> Unit)? = null,
) {
    val paneScale = LocalPaneScale.current
    Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = DrawerTextPrimary,
                fontSize = (14f * paneScale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            val valueModifier =
                if (onValueClick != null) {
                    Modifier
                        .clip(RoundedCornerShape((8f * paneScale).dp))
                        .background(PaneInnerResting)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onValueClick,
                        )
                        .padding(horizontal = (8f * paneScale).dp, vertical = (2f * paneScale).dp)
                } else {
                    Modifier
                }
            Text(
                text = valueText,
                color = DrawerAccent,
                fontSize = (13f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = valueModifier,
            )
        }
        CompactSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = DrawerAccent,
            activeTrackColor = DrawerAccent,
            inactiveTrackColor = TileResting,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.fillMaxWidth().requiredHeight(20.dp),
        colors = sliderColors,
        thumb = {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(DrawerAccent),
            )
        },
        track = { sliderState ->
            val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
            val fraction =
                if (span <= 0f) 0f else ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TileResting),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(DrawerAccent),
                )
            }
        },
    )
}

private fun Float.snapToStep(
    step: Float,
    min: Float,
    max: Float,
): Float = (min + (((this - min) / step).roundToInt() * step)).coerceIn(min, max)

@Composable
private fun HUDMetricInputDialog(
    editor: HUDMetricEditor,
    initialPercent: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember { mutableStateOf(initialPercent.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        val parsed = value.toIntOrNull() ?: initialPercent
        onConfirm(parsed.coerceIn(editor.minPercent, editor.maxPercent))
    }

    WinNativeDialogShell(
        onDismiss = onDismiss,
        title =
            when (editor) {
                HUDMetricEditor.ALPHA -> stringResource(R.string.session_drawer_hud_alpha_input_title)
                HUDMetricEditor.SCALE -> stringResource(R.string.session_drawer_hud_scale_input_title)
            },
        maxWidth = 380.dp,
    ) {
        Text(
            text = stringResource(R.string.session_drawer_hud_input_hint, editor.minPercent, editor.maxPercent),
            color = DrawerTextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { incoming -> value = incoming.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            suffix = {
                Text(
                    text = "%",
                    color = DrawerTextSecondary,
                    fontSize = 13.sp,
                )
            },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = DrawerTextPrimary),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DrawerAccent,
                    unfocusedBorderColor = DrawerOutline,
                    focusedTextColor = DrawerTextPrimary,
                    unfocusedTextColor = DrawerTextPrimary,
                    focusedContainerColor = DrawerBackground,
                    unfocusedContainerColor = DrawerBackground,
                    focusedLabelColor = DrawerTextSecondary,
                    unfocusedLabelColor = DrawerTextSecondary,
                    cursorColor = DrawerAccent,
                ),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        submit()
                    },
                ),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DrawerOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            WinNativeDialogButton(
                label = stringResource(R.string.common_ui_cancel),
                textColor = DrawerTextPrimary,
                onClick = onDismiss,
            )
            WinNativeDialogButton(
                label = stringResource(R.string.common_ui_apply),
                textColor = DrawerAccent,
                backgroundColor = DrawerAccent.copy(alpha = 0.12f),
                borderColor = DrawerAccent.copy(alpha = 0.3f),
                onClick = {
                    keyboardController?.hide()
                    submit()
                },
            )
        }
    }
}

@Composable
private fun HUDToggleChip(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue =
            when {
                pressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(140),
        label = "hudChipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) DrawerAccent else RestingCardBorder,
        animationSpec = tween(140),
        label = "hudChipBorder",
    )
    val cornerRadius = (12f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    val indicatorSize = (10f * paneScale).dp

    Row(
        modifier =
            modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = (10f * paneScale).dp, vertical = (9f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(if (checked) DrawerAccent else Color(0x14FFFFFF)),
        )
        Spacer(Modifier.width((8f * paneScale).dp))
        Text(
            text = label,
            color = DrawerTextPrimary,
            fontSize = (13f * paneScale).sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerBooleanRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val rowInteractionSource = remember { MutableInteractionSource() }
    val pressed = rowInteractionSource.collectIsPressedAsState().value
    val switchInteractionSource = remember { MutableInteractionSource() }

    val bgColor by animateColorAsState(
        targetValue =
            when {
                pressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(140),
        label = "drawerBooleanRowBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) ActiveCardBorder else RestingCardBorder,
        animationSpec = tween(140),
        label = "drawerBooleanRowBorder",
    )
    val cornerRadius = (14f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                ) { onCheckedChange(!checked) }
                .padding(horizontal = (12f * paneScale).dp, vertical = (8f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = DrawerTextPrimary,
                fontSize = (14f * paneScale).sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text =
                    if (checked) {
                        stringResource(R.string.common_ui_enabled)
                    } else {
                        stringResource(R.string.common_ui_disabled)
                    },
                color = DrawerTextSecondary,
                fontSize = (12f * paneScale).sp,
            )
        }
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = switchInteractionSource,
                colors = outlinedSwitchColors(DrawerAccent, DrawerTextSecondary),
            )
        }
    }
}

@Composable
private fun FPSLimiterSelection(
    currentLimit: Int,
    onLimitSelected: (Int) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val limits = listOf(0, 30, 45, 60, 90, 120)
    val offLabel = stringResource(R.string.session_drawer_fps_limiter_off)

    Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
        PaneSectionLabel(stringResource(R.string.session_drawer_fps_limiter))

        ChipFlow {
            limits.forEach { limit ->
                val label = if (limit == 0) offLabel else "$limit"
                HUDToggleChip(
                    label = label,
                    checked = currentLimit == limit,
                    onClick = { onLimitSelected(limit) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    val paneScale = LocalPaneScale.current
    val gap = (8f * paneScale).dp
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        content()
    }
}
