package com.winlator.cmod.runtime.display

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlin.math.roundToInt

// Drawer-local colors.
private const val DrawerSheetAlpha = 0.86f
private const val DrawerSurfaceAlpha = 0.72f
private const val DrawerPressedAlpha = 0.88f
private const val DrawerGradientLift = 0.014f

private val DrawerAccent = Color(0xFF2196F3)
private val DrawerActiveAccent = Color(0xFF29B6F6)
private val DrawerTextPrimary = WinNativeTextPrimary.copy(alpha = 0.88f)
private val DrawerTextSecondary = WinNativeTextSecondary.copy(alpha = 0.82f)
private val DrawerOutline = WinNativeOutline
private val DrawerBackground = WinNativeBackground.copy(alpha = DrawerSheetAlpha)

internal val PaneSurfaceColor = WinNativeBackground.copy(alpha = DrawerSheetAlpha)
private val PaneSurfacePressed = Color(0xFF232B3A).copy(alpha = DrawerPressedAlpha)

private val TopRailSurfaceColor = WinNativeSurface.copy(alpha = DrawerSheetAlpha)

private val TileResting = Color(0xFF20283A).copy(alpha = DrawerSurfaceAlpha)
private val TileExitResting = Color(0xFF3A2125).copy(alpha = DrawerSurfaceAlpha)
private val TileExitPressed = Color(0xFF4A2A30).copy(alpha = DrawerPressedAlpha)
private val PaneInnerResting = WinNativePanel.copy(alpha = DrawerSurfaceAlpha)
private val PaneInnerPressed = Color(0xFF242B3A).copy(alpha = DrawerPressedAlpha)
private val RestingCardBorder = WinNativeOutline.copy(alpha = 0.72f)
private val DisabledCardBorder = Color(0xFF202033).copy(alpha = 0.58f)
private val ActiveCardBorder = DrawerActiveAccent
private val BottomDividerColor = WinNativeOutline
private val GlassExitTint = Color(0xFFE07B6B)

// Pane content scales down on short displays.
private val LocalPaneScale = staticCompositionLocalOf { 1f }
private const val PaneScaleMin = 0.78f
private const val PaneScaleReferenceHeightDp = 520f
private const val PendingTaskAffinityTimeoutMs = 2500L

private fun computePaneScale(availableHeight: Dp): Float =
    (availableHeight.value / PaneScaleReferenceHeightDp).coerceIn(PaneScaleMin, 1f)

private enum class HUDMetricEditor(
    val minPercent: Int,
    val maxPercent: Int,
) {
    ALPHA(minPercent = 10, maxPercent = 100),
    SCALE(minPercent = 50, maxPercent = 200),
}

internal enum class DrawerPane { INPUT_CONTROLS, HUD, GYROSCOPE, SCREEN_EFFECTS, TASK_MANAGER }

data class TaskManagerProcess(
    val pid: Int,
    val name: String,
    val memoryFormatted: String,
    val affinityMask: Int,
    val isWow64: Boolean,
)

data class TaskManagerPaneState(
    val processes: List<TaskManagerProcess> = emptyList(),
    val cpuPercent: Int = 0,
    val cpuCoreCount: Int = 0,
    val cpuCorePercents: List<Int> = emptyList(),
    val memoryPercent: Int = 0,
    val memoryDetail: String = "",
)

private data class PendingTaskAffinity(
    val affinityMask: Int,
    val requestedAtMillis: Long,
)

// Top-rail pane specs.
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

private val TopRailTileMinWidth = 64.dp
private val TopRailTileHorizontalPadding = 10.dp
private val TopRailTileTopPadding = 6.dp
private val TopRailTileBottomPadding = 4.dp
private val TopRailTileSpacing = 6.dp

private const val ActionCardColumns = 3
private val ActionCardMinHeight = 72.dp
private val ActionCardSpacing = 8.dp

private const val ActionCardRevealStaggerMs = 28
private const val ActionCardRevealDurationMs = 220

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
    var taskManagerState by mutableStateOf(TaskManagerPaneState(), neverEqualPolicy())
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

    fun onTaskManagerVisibilityChanged(visible: Boolean)

    fun onTaskManagerCpuExpandedChanged(expanded: Boolean)

    fun onTaskManagerEndProcess(name: String)

    fun onTaskManagerSetAffinity(pid: Int, affinityMask: Int)

    fun onTaskManagerNewTask(command: String)
}

fun buildXServerDrawerState(
    context: Context,
    relativeMouseEnabled: Boolean,
    mouseDisabled: Boolean,
    fpsMonitorEnabled: Boolean,
    paused: Boolean,
    showMagnifier: Boolean,
    magnifierActive: Boolean,
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
    fullscreenEnabled: Boolean = false,
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
                active = fullscreenEnabled,
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
                active = magnifierActive,
            )
    }

    if (showLogs) {
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
                taskManagerState = stateHolder.taskManagerState,
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
    taskManagerState: TaskManagerPaneState,
    openPane: DrawerPane?,
    onOpenPaneChange: (DrawerPane?) -> Unit,
    listener: XServerDrawerActionListener,
    onDismiss: () -> Unit,
) {
    // Keep card reveal state stable while switching between panes.
    val cardsRevealed = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardsRevealed.value = true }

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
                    val railVisible = openPane != DrawerPane.TASK_MANAGER
                    val chromeEnter =
                        expandVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                    val chromeExit =
                        shrinkVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))

                    AnimatedVisibility(
                        visible = railVisible,
                        enter = chromeEnter,
                        exit = chromeExit,
                    ) {
                        Column {
                            TopRail(
                                state = state,
                                openPane = openPane,
                                onTabClick = { spec ->
                                    onOpenPaneChange(if (openPane == spec.pane) null else spec.pane)
                                },
                                onMenuClick = { onOpenPaneChange(null) },
                            )

                            ThinDivider()
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth(),
                    ) {
                        AnimatedContent(
                            targetState = openPane,
                            transitionSpec = {
                                val enteringTaskManager = targetState == DrawerPane.TASK_MANAGER
                                val returningToMenu = targetState == null
                                if (enteringTaskManager) {
                                    (
                                        slideInVertically(
                                            initialOffsetY = { it / 3 },
                                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                        ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
                                    ) togetherWith fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)) using
                                        SizeTransform(clip = false)
                                } else if (returningToMenu) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) togetherWith
                                        fadeOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
                                }
                            },
                            label = "drawerBody",
                        ) { pane ->
                            when (pane) {
                                DrawerPane.INPUT_CONTROLS -> InputControlsPaneContent(state = state, listener = listener)
                                DrawerPane.HUD -> HUDPaneContent(state = state, listener = listener)
                                DrawerPane.GYROSCOPE -> GyroscopePaneContent(state = state, listener = listener)
                                DrawerPane.SCREEN_EFFECTS -> ScreenEffectsPaneContent(state = state, listener = listener)
                                DrawerPane.TASK_MANAGER ->
                                    TaskManagerPaneContent(
                                        taskManagerState = taskManagerState,
                                        listener = listener,
                                        onClose = { onOpenPaneChange(null) },
                                    )
                                null ->
                                    ActionCardGrid(
                                        state = state,
                                        listener = listener,
                                        cardsRevealed = cardsRevealed.value,
                                        onActionInvoked = onDismiss,
                                        onOpenTaskManager = { onOpenPaneChange(DrawerPane.TASK_MANAGER) },
                                    )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = openPane == null,
                        enter = chromeEnter,
                        exit = chromeExit,
                    ) {
                        Column {
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
}

private data class RailTileBounds(val offsetX: Float, val width: Float, val height: Float)

@Composable
private fun TopRail(
    state: XServerDrawerState,
    openPane: DrawerPane?,
    onTabClick: (RailPaneSpec) -> Unit,
    onMenuClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val density = LocalDensity.current
    val activeSpecs = RAIL_PANES.filter { spec -> state.items.any { it.itemId == spec.itemId } }

    val tileBounds = remember { mutableStateMapOf<String, RailTileBounds>() }

    val selectedKey =
        when (openPane) {
            null -> "menu"
            else -> activeSpecs.firstOrNull { it.pane == openPane }?.itemId?.toString() ?: "menu"
        }
    val selectedBounds = tileBounds[selectedKey]

    val indicatorAnimSpec = tween<Dp>(durationMillis = 240, easing = FastOutSlowInEasing)
    val indicatorX by animateDpAsState(
        targetValue = selectedBounds?.let { with(density) { it.offsetX.toDp() } } ?: 0.dp,
        animationSpec = indicatorAnimSpec,
        label = "topRailIndicatorX",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = selectedBounds?.let { with(density) { it.width.toDp() } } ?: 0.dp,
        animationSpec = indicatorAnimSpec,
        label = "topRailIndicatorW",
    )
    val indicatorTileHeight by animateDpAsState(
        targetValue = selectedBounds?.let { with(density) { it.height.toDp() } } ?: 0.dp,
        animationSpec = indicatorAnimSpec,
        label = "topRailIndicatorTileHeight",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedBounds != null) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "topRailIndicatorAlpha",
    )

    val underlineThickness = (2f * paneScale).dp
    val underlineHorizontalInset = (6f * paneScale).dp

    Box(
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
    ) {
        if (selectedBounds != null) {
            Box(
                modifier =
                    Modifier
                        .offset(
                            x = indicatorX + underlineHorizontalInset,
                            y = indicatorTileHeight - underlineThickness,
                        )
                        .width((indicatorWidth - underlineHorizontalInset * 2).coerceAtLeast(0.dp))
                        .height(underlineThickness)
                        .graphicsLayer { alpha = indicatorAlpha }
                        .clip(RoundedCornerShape(underlineThickness / 2))
                        .background(DrawerAccent),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(TopRailTileSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopRailTile(
                icon = Icons.Outlined.Apps,
                label = stringResource(R.string.session_drawer_main_menu_title),
                active = false,
                selected = openPane == null,
                onClick = onMenuClick,
                tileKey = "menu",
                onBoundsChanged = { tileBounds["menu"] = it },
            )
            activeSpecs.forEach { spec ->
                val item = state.items.first { it.itemId == spec.itemId }
                val key = item.itemId.toString()
                TopRailTile(
                    icon = spec.iconOverride ?: item.icon,
                    label = stringResource(spec.labelRes),
                    active = item.active,
                    selected = openPane == spec.pane,
                    onClick = { onTabClick(spec) },
                    tileKey = key,
                    onBoundsChanged = { tileBounds[key] = it },
                )
            }
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
    onBoundsChanged: (RailTileBounds) -> Unit,
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
    val bgColor by animateColorAsState(
        targetValue =
            when {
                pressed && !selected -> PaneSurfacePressed
                else -> Color.Transparent
            },
        animationSpec = tween(120),
        label = "topRailBg_$tileKey",
    )
    val tint by animateColorAsState(
        targetValue =
            when {
                selected -> DrawerAccent
                active -> DrawerActiveAccent
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
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInParent()
                    onBoundsChanged(
                        RailTileBounds(
                            offsetX = bounds.left,
                            width = bounds.width,
                            height = bounds.height,
                        ),
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(bgColor)
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
    cardsRevealed: Boolean,
    onActionInvoked: () -> Unit,
    onOpenTaskManager: () -> Unit,
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
            cards.forEachIndexed { index, item ->
                val label = railLabelResFor(item.itemId)?.let { stringResource(it) } ?: item.title
                ActionCard(
                    item = item,
                    label = label,
                    revealIndex = index,
                    revealed = cardsRevealed,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = ActionCardMinHeight * paneScale),
                    onClick = {
                        when (item.itemId) {
                            R.id.main_menu_task_manager -> onOpenTaskManager()
                            R.id.main_menu_relative_mouse_movement,
                            R.id.main_menu_disable_mouse,
                            R.id.main_menu_toggle_fullscreen -> listener.onActionSelected(item.itemId)
                            else -> {
                                onActionInvoked()
                                listener.onActionSelected(item.itemId)
                            }
                        }
                    },
                )
            }
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
    revealIndex: Int,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val enabled = item.enabled

    val staggerDelay = revealIndex * ActionCardRevealStaggerMs
    val revealAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = ActionCardRevealDurationMs,
                delayMillis = staggerDelay,
                easing = FastOutSlowInEasing,
            ),
        label = "actionCardReveal_${item.itemId}",
    )
    val revealOffsetY by animateDpAsState(
        targetValue = if (revealed) 0.dp else 8.dp,
        animationSpec =
            tween(
                durationMillis = ActionCardRevealDurationMs,
                delayMillis = staggerDelay,
                easing = FastOutSlowInEasing,
            ),
        label = "actionCardRevealOffset_${item.itemId}",
    )

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
                item.active -> DrawerActiveAccent
                else -> DrawerTextPrimary
            },
        animationSpec = tween(120),
        label = "actionCardTint_${item.itemId}",
    )

    val cornerRadius = (12f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    val topColor =
        Color(
            red = (bgColor.red + (1f - bgColor.red) * DrawerGradientLift).coerceIn(0f, 1f),
            green = (bgColor.green + (1f - bgColor.green) * DrawerGradientLift).coerceIn(0f, 1f),
            blue = (bgColor.blue + (1f - bgColor.blue) * DrawerGradientLift).coerceIn(0f, 1f),
            alpha = bgColor.alpha,
        )
    val cardBrush = Brush.verticalGradient(listOf(topColor, bgColor))
    Column(
        modifier =
            modifier
                .offset(y = revealOffsetY)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = revealAlpha
                }
                .clip(shape)
                .background(cardBrush)
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
            item.active -> DrawerActiveAccent
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
private fun TaskManagerPaneContent(
    taskManagerState: TaskManagerPaneState,
    listener: XServerDrawerActionListener,
    onClose: () -> Unit,
) {
    var showNewTaskDialog by remember { mutableStateOf(false) }
    var processPendingEnd by remember { mutableStateOf<TaskManagerProcess?>(null) }
    var expandedAffinityPid by remember { mutableStateOf<Int?>(null) }
    val pendingAffinities = remember { mutableStateMapOf<Int, PendingTaskAffinity>() }

    DisposableEffect(Unit) {
        listener.onTaskManagerVisibilityChanged(true)
        onDispose { listener.onTaskManagerVisibilityChanged(false) }
    }

    LaunchedEffect(taskManagerState.processes) {
        val visibleProcessPids = taskManagerState.processes.map { it.pid }.toSet()
        val now = System.currentTimeMillis()
        pendingAffinities.keys.toList().forEach { pid ->
            if (pid !in visibleProcessPids) pendingAffinities.remove(pid)
        }
        taskManagerState.processes.forEach { process ->
            val pending = pendingAffinities[process.pid]
            if (
                pending != null &&
                    (pending.affinityMask == process.affinityMask ||
                        now - pending.requestedAtMillis > PendingTaskAffinityTimeoutMs)
            ) {
                pendingAffinities.remove(process.pid)
            }
        }
        if (expandedAffinityPid != null && expandedAffinityPid !in visibleProcessPids) {
            expandedAffinityPid = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        val affinityCoreCount =
            if (taskManagerState.cpuCoreCount > 0) {
                taskManagerState.cpuCoreCount
            } else {
                Runtime.getRuntime().availableProcessors()
            }
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
                TaskManagerHeader(
                    cpuPercent = taskManagerState.cpuPercent,
                    cpuCoreCount = taskManagerState.cpuCoreCount,
                    cpuCorePercents = taskManagerState.cpuCorePercents,
                    memoryPercent = taskManagerState.memoryPercent,
                    memoryDetail = taskManagerState.memoryDetail,
                    onNewTask = { showNewTaskDialog = true },
                    onClose = onClose,
                    onCpuExpandedChanged = listener::onTaskManagerCpuExpandedChanged,
                )

                TaskManagerProcessHeader()

                Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                    if (taskManagerState.processes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.common_ui_no_items_to_display),
                            color = DrawerTextSecondary,
                            fontSize = (13f * paneScale).sp,
                            modifier = Modifier.fillMaxWidth().padding(top = (24f * paneScale).dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy((12f * paneScale).dp),
                        ) {
                            taskManagerState.processes.forEach { process ->
                                key(process.pid) {
                                    val selectedAffinityMask =
                                        pendingAffinities[process.pid]?.affinityMask ?: process.affinityMask
                                    TaskManagerProcessCard(
                                        process = process,
                                        expanded = expandedAffinityPid == process.pid,
                                        affinityMask = selectedAffinityMask,
                                        coreCount = affinityCoreCount,
                                        onToggleAffinity = {
                                            expandedAffinityPid =
                                                if (expandedAffinityPid == process.pid) null else process.pid
                                        },
                                        onAffinityMaskChanged = { affinityMask ->
                                            pendingAffinities[process.pid] =
                                                PendingTaskAffinity(affinityMask, System.currentTimeMillis())
                                            listener.onTaskManagerSetAffinity(process.pid, affinityMask)
                                        },
                                        onEndProcess = { processPendingEnd = process },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewTaskDialog) {
        TaskManagerNewTaskDialog(
            onDismiss = { showNewTaskDialog = false },
            onConfirm = { command ->
                showNewTaskDialog = false
                listener.onTaskManagerNewTask(command)
            },
        )
    }

    processPendingEnd?.let { process ->
        TaskManagerEndProcessDialog(
            process = process,
            onDismiss = { processPendingEnd = null },
            onConfirm = {
                processPendingEnd = null
                listener.onTaskManagerEndProcess(process.name)
            },
        )
    }
}

@Composable
private fun TaskManagerHeader(
    cpuPercent: Int,
    cpuCoreCount: Int,
    cpuCorePercents: List<Int>,
    memoryPercent: Int,
    memoryDetail: String,
    onNewTask: () -> Unit,
    onClose: () -> Unit,
    onCpuExpandedChanged: (Boolean) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    var cpuExpanded by remember { mutableStateOf(false) }
    DisposableEffect(cpuExpanded) {
        onCpuExpandedChanged(cpuExpanded)
        onDispose { if (cpuExpanded) onCpuExpandedChanged(false) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.session_task_title),
            color = DrawerTextPrimary,
            fontSize = (16f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        TaskManagerCloseButton(onClick = onClose)
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskManagerStatTile(
            title = stringResource(R.string.session_task_cpu_usage_format, cpuPercent),
            detail =
                if (cpuCoreCount > 0) {
                    pluralStringResource(R.plurals.session_task_core_count, cpuCoreCount, cpuCoreCount)
                } else {
                    ""
                },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            selected = cpuExpanded,
            onClick = { cpuExpanded = !cpuExpanded },
        )
        TaskManagerStatTile(
            title = stringResource(R.string.session_task_memory) + " ($memoryPercent%)",
            detail = memoryDetail,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }

    AnimatedVisibility(
        visible = cpuExpanded && cpuCorePercents.isNotEmpty(),
        enter =
            fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                expandVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
        exit =
            fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
                shrinkVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
    ) {
        TaskManagerCpuCoreGrid(cpuCorePercents = cpuCorePercents)
    }

    TaskManagerNewTaskButton(onClick = onNewTask)
}

@Composable
private fun TaskManagerCloseButton(onClick: () -> Unit) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(120),
        label = "taskManagerCloseBg",
    )
    val size = (38f * paneScale).dp
    val shape = RoundedCornerShape((10f * paneScale).dp)
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(bgColor)
                .border(1.dp, RestingCardBorder, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.common_ui_close),
            tint = DrawerTextPrimary,
            modifier = Modifier.size((22f * paneScale).dp),
        )
    }
}

@Composable
private fun TaskManagerStatTile(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((10f * paneScale).dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue =
            when {
                pressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(120),
        label = "taskManagerStatTileBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) DrawerAccent else RestingCardBorder,
        animationSpec = tween(120),
        label = "taskManagerStatTileBorder",
    )
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .then(clickModifier)
                .padding(horizontal = (8f * paneScale).dp, vertical = (6f * paneScale).dp),
        verticalArrangement = Arrangement.spacedBy((1f * paneScale).dp),
    ) {
        Text(
            text = title,
            color = DrawerAccent,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = detail,
            color = DrawerTextSecondary,
            fontSize = (10f * paneScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskManagerCpuCoreGrid(cpuCorePercents: List<Int>) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((10f * paneScale).dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PaneInnerResting)
                .border(1.dp, RestingCardBorder, shape)
                .padding(horizontal = (8f * paneScale).dp, vertical = (6f * paneScale).dp),
        verticalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
    ) {
        Text(
            text = stringResource(R.string.session_task_per_core_usage),
            color = DrawerTextPrimary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
            verticalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
        ) {
            cpuCorePercents.forEachIndexed { index, percent ->
                TaskManagerCpuCoreChip(coreIndex = index, percent = percent)
            }
        }
    }
}

@Composable
private fun TaskManagerCpuCoreChip(coreIndex: Int, percent: Int) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((6f * paneScale).dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(PaneSurfaceColor)
                .border(1.dp, RestingCardBorder, shape)
                .padding(horizontal = (6f * paneScale).dp, vertical = (3f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
    ) {
        Text(
            text = stringResource(R.string.session_task_core_label, coreIndex),
            color = DrawerTextSecondary,
            fontSize = (10f * paneScale).sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "$percent%",
            color = DrawerAccent,
            fontSize = (10f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TaskManagerNewTaskButton(onClick: () -> Unit) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(120),
        label = "taskManagerNewTaskBg",
    )
    val tint = if (pressed) DrawerAccent.copy(alpha = 0.76f) else DrawerAccent
    val shape = RoundedCornerShape((12f * paneScale).dp)
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
                    onClick = onClick,
                )
                .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((18f * paneScale).dp),
        )
        Spacer(Modifier.width((6f * paneScale).dp))
        Text(
            text = stringResource(R.string.session_task_new_task),
            color = tint,
            fontSize = (14f * paneScale).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskManagerAffinityOptions(
    affinityMask: Int,
    coreCount: Int,
    onAffinityMaskChanged: (Int) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val effectiveCoreCount = coreCount.coerceAtLeast(1).coerceAtMost(32)
    val selectedMask = sanitizeTaskAffinityMask(affinityMask, effectiveCoreCount)
    val fullMask = taskAffinityFullMask(effectiveCoreCount)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = (8f * paneScale).dp,
                    end = (8f * paneScale).dp,
                    bottom = (8f * paneScale).dp,
                ),
        verticalArrangement = Arrangement.spacedBy((7f * paneScale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((6f * paneScale).dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = DrawerAccent,
                modifier = Modifier.size((15f * paneScale).dp),
            )
            Text(
                text = stringResource(R.string.session_task_affinity_title),
                color = DrawerTextPrimary,
                fontSize = (12f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((5f * paneScale).dp),
            verticalArrangement = Arrangement.spacedBy((5f * paneScale).dp),
        ) {
            TaskManagerAffinityChip(
                label = stringResource(R.string.session_task_affinity_all_cores),
                selected = selectedMask == fullMask,
                onClick = { onAffinityMaskChanged(fullMask) },
            )
            for (coreIndex in 0 until effectiveCoreCount) {
                val bit = 1 shl coreIndex
                TaskManagerAffinityChip(
                    label = stringResource(R.string.session_task_core_label, coreIndex),
                    selected = (selectedMask and bit) != 0,
                    onClick = {
                        val nextMask =
                            if ((selectedMask and bit) != 0) {
                                selectedMask and bit.inv()
                            } else {
                                selectedMask or bit
                            }
                        if ((nextMask and fullMask) != 0) {
                            onAffinityMaskChanged(nextMask and fullMask)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskManagerAffinityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor =
        if (selected) {
            DrawerAccent.copy(alpha = 0.16f)
        } else {
            PaneInnerResting
        }
    val borderColor = if (selected) DrawerAccent.copy(alpha = 0.56f) else RestingCardBorder
    val textColor = if (selected) DrawerAccent else DrawerTextPrimary
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = DrawerAccent.copy(alpha = if (selected) 1f else 0f),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private fun taskAffinityFullMask(coreCount: Int): Int {
    var mask = 0
    for (index in 0 until coreCount.coerceAtLeast(1).coerceAtMost(32)) {
        mask = mask or (1 shl index)
    }
    return mask
}

private fun sanitizeTaskAffinityMask(affinityMask: Int, coreCount: Int): Int {
    val fullMask = taskAffinityFullMask(coreCount)
    val sanitizedMask = affinityMask and fullMask
    return if (sanitizedMask != 0) sanitizedMask else fullMask
}

@Composable
private fun TaskManagerEndProcessDialog(
    process: TaskManagerProcess,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val displayName = if (process.isWow64) "${process.name} *32" else process.name
    val shape = RoundedCornerShape(12.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 292.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(PaneSurfaceColor)
                        .border(1.dp, GlassExitTint.copy(alpha = 0.32f), shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = GlassExitTint,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = stringResource(R.string.session_task_end_process),
                        color = DrawerTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = displayName,
                    color = DrawerTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.session_task_confirm_end_process),
                    color = DrawerTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskManagerDialogButton(
                        label = stringResource(R.string.common_ui_cancel),
                        textColor = DrawerTextPrimary,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        onClick = onDismiss,
                    )
                    TaskManagerDialogButton(
                        label = stringResource(R.string.session_task_end_process),
                        textColor = GlassExitTint,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        fontWeight = FontWeight.Medium,
                        backgroundColor = GlassExitTint.copy(alpha = 0.12f),
                        borderColor = GlassExitTint.copy(alpha = 0.34f),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskManagerNewTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var command by remember { mutableStateOf("taskmgr.exe") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(14.dp)

    fun submit() {
        val trimmed = command.trim()
        if (trimmed.isNotEmpty()) onConfirm(trimmed)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 310.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(PaneSurfaceColor)
                        .border(1.dp, RestingCardBorder, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = DrawerAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.session_task_new_task),
                        color = DrawerTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle =
                        androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                            color = DrawerTextPrimary,
                            fontSize = 13.sp,
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DrawerAccent,
                            unfocusedBorderColor = RestingCardBorder,
                            focusedTextColor = DrawerTextPrimary,
                            unfocusedTextColor = DrawerTextPrimary,
                            focusedContainerColor = PaneInnerResting,
                            unfocusedContainerColor = PaneInnerResting,
                            cursorColor = DrawerAccent,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                submit()
                            },
                        ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskManagerDialogButton(
                        label = stringResource(R.string.common_ui_cancel),
                        textColor = DrawerTextPrimary,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        onClick = onDismiss,
                    )
                    TaskManagerDialogButton(
                        label = stringResource(R.string.common_ui_ok),
                        textColor = DrawerAccent,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        backgroundColor = DrawerAccent.copy(alpha = 0.12f),
                        borderColor = DrawerAccent.copy(alpha = 0.34f),
                        onClick = {
                            keyboardController?.hide()
                            submit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskManagerDialogButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = PaneInnerResting,
    borderColor: Color = RestingCardBorder,
    fontWeight: FontWeight = FontWeight.SemiBold,
    verticalPadding: Dp = 8.dp,
) {
    Box(
        modifier =
            modifier
                .widthIn(min = 72.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(backgroundColor)
                .border(1.dp, borderColor, RoundedCornerShape(9.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TaskManagerProcessHeader() {
    val paneScale = LocalPaneScale.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = (4f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.session_task_process_name),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.session_task_pid),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width((54f * paneScale).dp),
        )
        Text(
            text = stringResource(R.string.session_task_memory),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width((78f * paneScale).dp),
        )
        Spacer(modifier = Modifier.width((46f * paneScale).dp))
    }
}

@Composable
private fun TaskManagerProcessCard(
    process: TaskManagerProcess,
    expanded: Boolean,
    affinityMask: Int,
    coreCount: Int,
    onToggleAffinity: () -> Unit,
    onAffinityMaskChanged: (Int) -> Unit,
    onEndProcess: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((8f * paneScale).dp)
    val displayName = if (process.isWow64) "${process.name} *32" else process.name
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(120),
        label = "taskManagerProcessRowBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) DrawerAccent.copy(alpha = 0.62f) else RestingCardBorder,
        animationSpec = tween(160),
        label = "taskManagerProcessCardBorder",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggleAffinity,
                    )
                    .padding(horizontal = (8f * paneScale).dp, vertical = (6f * paneScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayName,
                color = DrawerTextPrimary,
                fontSize = (12f * paneScale).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = process.pid.toString(),
                color = DrawerTextSecondary,
                fontSize = (12f * paneScale).sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width((54f * paneScale).dp),
            )
            Text(
                text = process.memoryFormatted,
                color = DrawerTextSecondary,
                fontSize = (12f * paneScale).sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width((78f * paneScale).dp),
            )
            Spacer(modifier = Modifier.width((10f * paneScale).dp))
            TaskManagerEndButton(onClick = onEndProcess)
        }

        AnimatedVisibility(
            visible = expanded,
            enter =
                fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ),
            exit =
                fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ),
        ) {
            TaskManagerAffinityOptions(
                affinityMask = affinityMask,
                coreCount = coreCount,
                onAffinityMaskChanged = onAffinityMaskChanged,
            )
        }
    }
}

@Composable
private fun TaskManagerEndButton(onClick: () -> Unit) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) TileExitPressed else TileExitResting,
        animationSpec = tween(120),
        label = "taskManagerEndBtn",
    )
    val size = (32f * paneScale).dp
    val shape = RoundedCornerShape((8f * paneScale).dp)
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(bgColor)
                .border(1.dp, GlassExitTint.copy(alpha = 0.34f), shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.session_task_end_process),
            tint = GlassExitTint,
            modifier = Modifier.size((16f * paneScale).dp),
        )
    }
}

@Composable
private fun PaneSectionLabel(text: String) {
    val paneScale = LocalPaneScale.current
    Text(
        text = text,
        color = DrawerTextPrimary,
        fontSize = (14f * paneScale).sp,
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
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(0.96f).requiredHeight(20.dp),
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
