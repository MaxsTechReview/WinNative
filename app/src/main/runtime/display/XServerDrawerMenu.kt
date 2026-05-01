package com.winlator.cmod.runtime.display

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativeSurfaceAlt
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlin.math.roundToInt

// Container surfaces (M3 dark glass) — rail is transparent so tiles float over the game.
// Pane uses a neutral dark glass (no blue tint) so blue is reserved for on/true accents.
// Rail tiles share this fill so the rail and pane read as one continuous surface.
private val PaneSurfaceColor = Color(0xE6141416)
private val PaneSurfacePressed = Color(0xE61F1F22)

// Aurora palette for the drawer's edge bleed (deep blue → blue → electric cyan → mint,
// seamless). Scoped to this drawer; the rest of the app uses the chasingBorder palette.
private val EdgeGlowDeepBlue = Color(0xFF1565C0)
private val EdgeGlowBlue = Color(0xFF2196F3)
private val EdgeGlowCyan = Color(0xFF00E5FF)
private val EdgeGlowMint = Color(0xFF1DE9B6)

// Tile / inner-row fills (flat translucent M3 surface tints)
private val TileResting = Color(0x24FFFFFF)
// Exit tile: dark red glass at the same opacity/brightness as PaneSurfaceColor,
// just shifted into the red channel so it reads as a danger affordance.
private val TileExitResting = Color(0xE6401418)
private val TileExitPressed = Color(0xE6541A20)
// Subtle cool blue-gray glass — complements WinNativeAccent without competing with
// the on/true outlines, and reads as a layered surface over PaneSurfaceColor.
private val PaneInnerResting = Color(0x331E2530)
private val PaneInnerPressed = Color(0x40293142)
private val RestingCardBorder = Color(0x1FFFFFFF)
private val DisabledCardBorder = Color(0x0FFFFFFF)

// Tint for exit-tile/icons in the rail and the activator dialog's outline.
private val GlassExitTint = Color(0xFFE07A84)

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

internal enum class DrawerPane { HUD, GYROSCOPE, SCREEN_EFFECTS, MORE }

private const val MORE_TILE_ID = -1

// Declarative spec for a rail tile that opens a side pane. To add a new pane:
//   1. Add a value to DrawerPane.
//   2. Append a RailPaneSpec entry below.
//   3. Add a `when (openPane)` branch rendering its pane content.
// `paneItemIds` and the More-pane filter both derive from this list.
private data class RailPaneSpec(
    val pane: DrawerPane,
    val itemId: Int,
    val labelRes: Int,
    val iconOverride: ImageVector? = null,
)

private val RAIL_PANES =
    listOf(
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
private val RAIL_PINNED_ITEM_IDS = setOf(R.id.main_menu_pause, R.id.main_menu_exit)

// Bounds for responsive tile sizing. Icons/labels scale proportionally between these.
private val RailTileMinSize = 44.dp
private val RailTileMaxSize = 64.dp
private val RailTileSpacing = 6.dp
private val RailPinnedSpacingTop = 12.dp
private val RailPinnedSpacingBetween = 6.dp
private val PaneWidth = 262.dp

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
    val hudElements: BooleanArray = booleanArrayOf(true, true, true, true, true, true),
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
)

class XServerDrawerStateHolder(
    initialState: XServerDrawerState,
) {
    var state by mutableStateOf(initialState, neverEqualPolicy())
    private var drawerOpen by mutableStateOf(false)
    internal var openPane by mutableStateOf<DrawerPane?>(null)
    internal var gyroActivatorDialogState by mutableStateOf<GyroActivatorDialogState?>(null)
    private var paneVisibilityListener: ((Boolean) -> Unit)? = null

    val isDrawerOpen: Boolean
        get() = drawerOpen

    fun openDrawer() {
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
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

    fun showGyroActivatorDialog(
        currentLabel: String,
        names: Array<String>,
        keycodes: IntArray,
        onDismiss: () -> Unit,
        onSelected: (Int) -> Unit,
    ) {
        gyroActivatorDialogState =
            GyroActivatorDialogState(
                currentLabel = currentLabel,
                names = names,
                keycodes = keycodes,
                onDismiss = onDismiss,
                onSelected = onSelected,
            )
    }

    fun hideGyroActivatorDialog() {
        gyroActivatorDialogState = null
    }
}

internal data class GyroActivatorDialogState(
    val currentLabel: String,
    val names: Array<String>,
    val keycodes: IntArray,
    val onDismiss: () -> Unit,
    val onSelected: (Int) -> Unit,
)

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

    fun onGyroscopeActivatorClick()

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
    hudElements: BooleanArray = booleanArrayOf(true, true, true, true, true, true),
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
                subtitle =
                    if (nativeRenderingEnabled) {
                        context.getString(R.string.session_drawer_magnifier_disabled_native_subtitle)
                    } else {
                        ""
                    },
                icon = Icons.Outlined.ZoomIn,
                enabled = !nativeRenderingEnabled,
            )
    }

    if (showLogs) {
        items +=
            XServerDrawerItem(
                itemId = R.id.main_menu_logs,
                title = context.getString(R.string.session_drawer_logs),
                subtitle = "",
                icon = Icons.Outlined.Terminal,
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
    val dismissInteractionSource = remember { MutableInteractionSource() }

    // Pane-only drag (closes the open pane, leaves the rail alone). Resets per pane.
    // Same spring as the AnimatedVisibility exit so the snap-back and the close slide
    // share physics — no perceptible mismatch on partial-drag release.
    var paneDragPx by remember(openPane) { mutableStateOf(0f) }
    val animatedPaneDrag by animateFloatAsState(
        targetValue = paneDragPx,
        animationSpec =
            if (paneDragPx == 0f) {
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            } else {
                tween(0)
            },
        label = "paneDragOffset",
    )

    Surface(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(340.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // Subtle cyan/blue edge glow — fades in with the rail and gently pulses.
        DrawerEdgeGlow(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Side rail — transparent so tiles float directly over the game canvas.
            // Swipe-to-dismiss is handled by the parent ModalNavigationDrawer.
            Surface(
                modifier =
                    Modifier
                        .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        .width(68.dp)
                        .fillMaxHeight(),
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                ) {
                    val exitItem = state.items.firstOrNull { it.itemId == R.id.main_menu_exit }
                    val pauseItem = state.items.firstOrNull { it.itemId == R.id.main_menu_pause }
                    val moreItems =
                        state.items.filter { it.itemId !in RAIL_PANE_ITEM_IDS && it.itemId !in RAIL_PINNED_ITEM_IDS }

                    val activePaneSpecs = RAIL_PANES.filter { spec -> state.items.any { it.itemId == spec.itemId } }
                    val showMore = moreItems.isNotEmpty()
                    val scrollableTileCount = activePaneSpecs.size + (if (showMore) 1 else 0)
                    val pinnedTileCount = (if (pauseItem != null) 1 else 0) + (if (exitItem != null) 1 else 0)

                    val tileSize = computeRailTileSize(
                        availableHeight = maxHeight,
                        scrollableTileCount = scrollableTileCount,
                        pinnedTileCount = pinnedTileCount,
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = true)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(RailTileSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            activePaneSpecs.forEach { spec ->
                                val item = state.items.first { it.itemId == spec.itemId }
                                val displayItem = if (spec.iconOverride != null) item.copy(icon = spec.iconOverride) else item
                                RailTile(
                                    item = displayItem,
                                    label = stringResource(spec.labelRes),
                                    selected = openPane == spec.pane,
                                    tileSize = tileSize,
                                    onClick = {
                                        onOpenPaneChange(if (openPane == spec.pane) null else spec.pane)
                                    },
                                )
                            }

                            if (showMore) {
                                val moreLabel = stringResource(R.string.session_drawer_rail_label_apps)
                                val moreTile =
                                    XServerDrawerItem(
                                        itemId = MORE_TILE_ID,
                                        title = moreLabel,
                                        subtitle = "",
                                        icon = Icons.Outlined.Apps,
                                    )
                                RailTile(
                                    item = moreTile,
                                    label = moreLabel,
                                    selected = openPane == DrawerPane.MORE,
                                    tileSize = tileSize,
                                    onClick = {
                                        onOpenPaneChange(
                                            if (openPane == DrawerPane.MORE) null else DrawerPane.MORE,
                                        )
                                    },
                                )
                            }
                        }

                        if (pinnedTileCount > 0) {
                            Spacer(Modifier.height(RailPinnedSpacingTop))
                        }

                        if (pauseItem != null) {
                            RailTile(
                                item = pauseItem,
                                label = stringResource(if (pauseItem.active) R.string.session_drawer_resume else R.string.session_drawer_pause),
                                selected = false,
                                tileSize = tileSize,
                                onClick = {
                                    onOpenPaneChange(null)
                                    listener.onActionSelected(pauseItem.itemId)
                                },
                            )
                        }

                        if (exitItem != null) {
                            if (pauseItem != null) Spacer(Modifier.height(RailPinnedSpacingBetween))
                            RailTile(
                                item = exitItem,
                                label = stringResource(R.string.common_ui_exit),
                                selected = false,
                                tileSize = tileSize,
                                onClick = {
                                    onOpenPaneChange(null)
                                    listener.onActionSelected(exitItem.itemId)
                                },
                            )
                        }

                    }
                }
            }

            // Empty area: tap-to-dismiss when no pane is open. Swipe-to-dismiss is
            // handled by the parent ModalNavigationDrawer.
            if (openPane == null) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = dismissInteractionSource,
                                indication = null,
                                onClick = onDismiss,
                            ),
                )
            }

            // Pane — sits to the rail's right and slides in/out as a rigid sheet, like the
            // parent ModalNavigationDrawer's own slide. clipToBounds on the AnimatedVisibility
            // keeps the slide from painting over the rail when it translates leftward past
            // its slot's left edge. Same spring on enter/exit so close is a true mirror.
            AnimatedVisibility(
                modifier = Modifier.clipToBounds(),
                visible = openPane != null,
                enter =
                    slideInHorizontally(
                        animationSpec =
                            spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        initialOffsetX = { -it },
                    ),
                exit =
                    slideOutHorizontally(
                        animationSpec =
                            spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        targetOffsetX = { -it },
                    ),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(PaneWidth)
                            .padding(start = 2.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            .graphicsLayer { translationX = animatedPaneDrag }
                            .clipToBounds()
                            .pointerInput(openPane) {
                                // Pane-only swipe-to-close: closes the pane, not the drawer.
                                val velocityTracker = VelocityTracker()
                                var totalDx = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        totalDx = 0f
                                        velocityTracker.resetTracking()
                                    },
                                    onDragEnd = {
                                        val vx = velocityTracker.calculateVelocity().x
                                        if (totalDx < -40f || vx < -500f) onOpenPaneChange(null)
                                        paneDragPx = 0f
                                        totalDx = 0f
                                    },
                                    onDragCancel = {
                                        paneDragPx = 0f
                                        totalDx = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        totalDx += dragAmount
                                        if (totalDx < 0f) {
                                            // 1:1 finger tracking — soft taper only after the pane
                                            // has been pulled most of the way off-screen.
                                            val absRaw = -totalDx
                                            val tracked =
                                                if (absRaw < 240f) absRaw else 240f + (absRaw - 240f) * 0.6f
                                            paneDragPx = (-tracked).coerceAtLeast(-420f)
                                            change.consume()
                                        }
                                    },
                                )
                            },
                    shape = RoundedCornerShape(22.dp),
                    color = PaneSurfaceColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Crossfade(
                        targetState = openPane,
                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                        label = "drawerPaneContent",
                    ) { pane ->
                        Column(modifier = Modifier.fillMaxHeight()) {
                            when (pane) {
                                DrawerPane.HUD ->
                                    HUDPaneContent(
                                        state = state,
                                        listener = listener,
                                    )
                                DrawerPane.GYROSCOPE ->
                                    GyroscopePaneContent(
                                        state = state,
                                        listener = listener,
                                    )
                                DrawerPane.SCREEN_EFFECTS ->
                                    ScreenEffectsPaneContent(
                                        state = state,
                                        listener = listener,
                                    )
                                DrawerPane.MORE ->
                                    MorePaneContent(
                                        items =
                                            state.items.filter {
                                                it.itemId != R.id.main_menu_fps_monitor &&
                                                    it.itemId != R.id.main_menu_gyroscope &&
                                                    it.itemId != R.id.main_menu_screen_effects &&
                                                    it.itemId != R.id.main_menu_pause &&
                                                    it.itemId != R.id.main_menu_exit
                                            },
                                        listener = listener,
                                        onClose = { onOpenPaneChange(null) },
                                    )
                                null -> {}
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun DrawerEdgeGlow(modifier: Modifier = Modifier) {
    val edgeGlowFlowColors =
        listOf(
            EdgeGlowDeepBlue,
            EdgeGlowBlue,
            EdgeGlowCyan,
            EdgeGlowMint,
            EdgeGlowDeepBlue,
        )
    val transition = rememberInfiniteTransition(label = "drawerEdgeGlow")
    // Matches the chasingBorder modifier's 5000ms rotation so the glow reads as the
    // rail's animated border bleeding outward, not a separate effect.
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "drawerEdgeGlowFlow",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "drawerEdgeGlowPulse",
    )

    Box(
        modifier =
            modifier
                .width(52.dp)
                .graphicsLayer {
                    alpha = pulse
                    // Offscreen layer so the DstIn feather masks the gradient instead of
                    // the underlying scene.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@drawBehind

                    // Gradient period equals the shift per cycle so TileMode.Repeated
                    // wraps seamlessly — otherwise the top of the strip snaps a half-period
                    // when `flow` resets to 0.
                    val offset = flow * h
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colors = edgeGlowFlowColors,
                                start = Offset(0f, offset),
                                end = Offset(0f, offset + h),
                                tileMode = TileMode.Repeated,
                            ),
                        alpha = 0.22f,
                    )

                    // Smooth horizontal feather: never fully opaque, with a gentle long
                    // tail so the bleed extends further before easing to transparent.
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.Black.copy(alpha = 0.55f),
                                        0.4f to Color.Black.copy(alpha = 0.22f),
                                        0.75f to Color.Black.copy(alpha = 0.08f),
                                        1f to Color.Transparent,
                                    ),
                                startX = 0f,
                                endX = w,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    )
}

// Returns the largest square tile size that lets every rail tile (scrollable + pinned) fit
// without scrolling, clamped to RailTileMin/MaxSize. Falls back to the max size when there
// are no tiles.
private fun computeRailTileSize(
    availableHeight: Dp,
    scrollableTileCount: Int,
    pinnedTileCount: Int,
): Dp {
    val totalTiles = scrollableTileCount + pinnedTileCount
    if (totalTiles <= 0) return RailTileMaxSize

    val scrollableSpacing =
        if (scrollableTileCount > 1) RailTileSpacing * (scrollableTileCount - 1) else 0.dp
    val pinnedTopGap = if (pinnedTileCount > 0) RailPinnedSpacingTop else 0.dp
    val pinnedBetween = if (pinnedTileCount > 1) RailPinnedSpacingBetween else 0.dp
    val totalGaps = scrollableSpacing + pinnedTopGap + pinnedBetween

    val perTile = (availableHeight - totalGaps) / totalTiles
    return perTile.coerceIn(RailTileMinSize, RailTileMaxSize)
}

@Composable
private fun RailTile(
    item: XServerDrawerItem,
    label: String,
    selected: Boolean,
    tileSize: Dp = RailTileMaxSize,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val enabled = item.enabled
    val isExit = item.itemId == R.id.main_menu_exit
    val showLabel = item.itemId != R.id.main_menu_exit && item.itemId != R.id.main_menu_pause

    val sizeRatio = (tileSize / RailTileMaxSize).coerceIn(0.6f, 1f)
    val iconSize = (if (showLabel) 22.dp else 26.dp) * sizeRatio
    val labelFontSize = (10f * sizeRatio).sp
    val cornerRadius = 16.dp * sizeRatio
    val activeBadgeSize = 6.dp * sizeRatio
    val selectionMarkerHeight = 24.dp * sizeRatio

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "railTileScale_${item.itemId}",
    )
    val bgColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> Color(0x05FFFFFF)
                isExit && pressed -> TileExitPressed
                isExit -> TileExitResting
                pressed -> PaneSurfacePressed
                else -> PaneSurfaceColor
            },
        animationSpec = tween(80),
        label = "railTileBg_${item.itemId}",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                selected && !isExit -> WinNativeAccent
                !enabled -> DisabledCardBorder
                isExit -> GlassExitTint.copy(alpha = 0.28f)
                else -> RestingCardBorder
            },
        animationSpec = tween(80),
        label = "railTileBorder_${item.itemId}",
    )
    val iconTint by animateColorAsState(
        targetValue =
            when {
                !enabled -> WinNativeTextSecondary.copy(alpha = 0.45f)
                isExit -> GlassExitTint
                selected || item.active -> WinNativeAccent
                else -> WinNativeTextPrimary
            },
        animationSpec = tween(90),
        label = "railTileIcon_${item.itemId}",
    )
    val labelColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> WinNativeTextSecondary.copy(alpha = 0.5f)
                isExit -> GlassExitTint
                selected || item.active -> WinNativeAccent
                else -> WinNativeTextPrimary.copy(alpha = 0.9f)
            },
        animationSpec = tween(90),
        label = "railTileLabel_${item.itemId}",
    )

    val tileShape = RoundedCornerShape(cornerRadius)
    Box(
        modifier =
            Modifier
                .size(tileSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(tileShape)
                .background(bgColor)
                .border(1.dp, borderColor, tileShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
            if (showLabel) {
                Spacer(Modifier.height(4.dp * sizeRatio))
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = labelFontSize,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp * sizeRatio)
                        .width(3.dp)
                        .height(selectionMarkerHeight)
                        .clip(CircleShape)
                        .background(WinNativeAccent),
            )
        } else if (item.active && enabled && !isExit) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp * sizeRatio)
                        .size(activeBadgeSize)
                        .clip(CircleShape)
                        .background(WinNativeAccent),
            )
        }
    }
}

@Composable
private fun PaneEnableRow(
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
        label = "paneEnableRowBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) WinNativeAccent else RestingCardBorder,
        animationSpec = tween(140),
        label = "paneEnableRowBorder",
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
                .padding(horizontal = (12f * paneScale).dp, vertical = (6f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = if (checked) WinNativeAccent else WinNativeTextPrimary,
            fontSize = (13f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = switchInteractionSource,
                colors = outlinedSwitchColors(WinNativeAccent, WinNativeTextSecondary),
            )
        }
    }
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
                    DrawerMetricChip(
                        label = stringResource(R.string.session_gyroscope_activator),
                        value = state.gyroscopeActivatorLabel,
                        onClick = { listener.onGyroscopeActivatorClick() },
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
                        textColor = WinNativeAccent,
                        backgroundColor = WinNativeAccent.copy(alpha = 0.12f),
                        borderColor = WinNativeAccent.copy(alpha = 0.3f),
                        onClick = { listener.onActionSelected(R.id.main_menu_gyroscope_reset) },
                    )
                }
            }
            }
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
        targetValue = if (expanded) WinNativeAccent else RestingCardBorder,
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
                color = if (expanded) WinNativeAccent else WinNativeTextPrimary,
                fontSize = (12f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (expanded) WinNativeAccent else WinNativeTextSecondary,
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
                            stringResource(R.string.session_drawer_super_resolution),
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
private fun MorePaneContent(
    items: List<XServerDrawerItem>,
    listener: XServerDrawerActionListener,
    onClose: () -> Unit,
) {
    val inputIds = setOf(R.id.main_menu_keyboard, R.id.main_menu_input_controls, R.id.main_menu_relative_mouse_movement, R.id.main_menu_disable_mouse)
    val displayIds = setOf(R.id.main_menu_toggle_fullscreen, R.id.main_menu_pip_mode, R.id.main_menu_native_rendering, R.id.main_menu_magnifier)
    val systemIds = setOf(R.id.main_menu_task_manager, R.id.main_menu_logs)

    val inputItems = items.filter { it.itemId in inputIds }
    val displayItems = items.filter { it.itemId in displayIds }
    val systemItems = items.filter { it.itemId in systemIds }
    val otherItems = items.filter { it.itemId !in inputIds && it.itemId !in displayIds && it.itemId !in systemIds }
    val enabledSubtitle = stringResource(R.string.common_ui_enabled)
    val disabledSubtitle = stringResource(R.string.common_ui_disabled)

    fun XServerDrawerItem.forMoreMenu(): XServerDrawerItem {
        val keepSubtitle = subtitle == enabledSubtitle || subtitle == disabledSubtitle || !enabled
        return if (keepSubtitle) this else copy(subtitle = "")
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
                verticalArrangement = Arrangement.spacedBy((12f * paneScale).dp),
            ) {
            MoreGroup(
                title = stringResource(R.string.session_drawer_command_center_input),
                items = inputItems.map { it.forMoreMenu() },
                listener = listener,
                onClose = onClose,
            )
            MoreGroup(
                title = stringResource(R.string.session_drawer_command_center_display),
                items = displayItems.map { it.forMoreMenu() },
                listener = listener,
                onClose = onClose,
            )
            MoreGroup(
                title = stringResource(R.string.session_drawer_command_center_system),
                items = systemItems.map { it.forMoreMenu() },
                listener = listener,
                onClose = onClose,
            )
            if (otherItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    otherItems.map { it.forMoreMenu() }.forEach { item ->
                        MoreRow(
                            item = item,
                            onClick = {
                                onClose()
                                listener.onActionSelected(item.itemId)
                            },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MoreGroup(
    title: String,
    items: List<XServerDrawerItem>,
    listener: XServerDrawerActionListener,
    onClose: () -> Unit,
) {
    if (items.isEmpty()) return
    val paneScale = LocalPaneScale.current
    Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
        PaneSectionLabel(title)
        items.forEach { item ->
            MoreRow(
                item = item,
                onClick = {
                    onClose()
                    listener.onActionSelected(item.itemId)
                },
            )
        }
    }
}

@Composable
private fun MoreRow(
    item: XServerDrawerItem,
    onClick: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue =
            when {
                !item.enabled -> Color(0x05FFFFFF)
                pressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(140),
        label = "moreRowBg_${item.itemId}",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                item.active && item.enabled -> WinNativeAccent
                !item.enabled -> DisabledCardBorder
                else -> RestingCardBorder
            },
        animationSpec = tween(140),
        label = "moreRowBorder_${item.itemId}",
    )
    val iconTint =
        when {
            !item.enabled -> WinNativeTextSecondary.copy(alpha = 0.45f)
            item.active -> WinNativeAccent
            else -> WinNativeTextPrimary
        }
    val iconBgColor =
        if (item.active) {
            WinNativeAccent.copy(alpha = 0.18f)
        } else {
            Color(0x14FFFFFF)
        }
    val rowShape = RoundedCornerShape((16f * paneScale).dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(bgColor)
                .border(1.dp, borderColor, rowShape)
                .clickable(
                    enabled = item.enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size((34f * paneScale).dp)
                    .clip(RoundedCornerShape((12f * paneScale).dp))
                    .background(iconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size((18f * paneScale).dp),
            )
        }
        Spacer(Modifier.width((12f * paneScale).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (item.enabled) WinNativeTextPrimary else WinNativeTextSecondary.copy(alpha = 0.6f),
                fontSize = (13f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.subtitle,
                    color = WinNativeTextSecondary,
                    fontSize = (11f * paneScale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.active) {
            Spacer(Modifier.width((8f * paneScale).dp))
            Box(
                modifier =
                    Modifier
                        .size((8f * paneScale).dp)
                        .clip(CircleShape)
                        .background(WinNativeAccent),
            )
        }
    }
}

@Composable
private fun PaneSectionLabel(text: String) {
    val paneScale = LocalPaneScale.current
    Text(
        text = text,
        color = WinNativeTextSecondary,
        fontSize = (11f * paneScale).sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    )
}

fun setupGyroActivatorDialog(
    composeView: androidx.compose.ui.platform.ComposeView,
    currentLabel: String,
    names: Array<String>,
    keycodes: IntArray,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit
) {
    composeView.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        WinNativeTheme {
            XServerGyroscopeActivatorDialog(
                currentLabel = currentLabel,
                names = names,
                keycodes = keycodes,
                onDismiss = onDismiss,
                onSelected = onSelected
            )
        }
    }
}

@Composable
fun XServerGyroscopeActivatorDialog(
    currentLabel: String,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
    names: Array<String>,
    keycodes: IntArray,
) {
    WinNativeDialogShell(
        onDismiss = onDismiss,
        title = stringResource(R.string.session_gyroscope_activator_button),
        maxWidth = 400.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}
        )) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                names.forEachIndexed { index, name ->
                    val isSelected = name == currentLabel
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed = interactionSource.collectIsPressedAsState().value
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) WinNativeAccent.copy(alpha = 0.16f) else if (pressed) WinNativeSurfaceAlt else Color.Transparent,
                        label = "activatorBg",
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(
                                1.dp,
                                if (isSelected) WinNativeAccent.copy(alpha = 0.34f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelected(keycodes[index]) }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) WinNativeAccent else WinNativeTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = WinNativeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
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
            color = WinNativeTextSecondary,
            fontSize = (9f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            color = WinNativeTextPrimary,
            fontSize = (12f * paneScale).sp,
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
                color = WinNativeTextPrimary,
                fontSize = (13f * paneScale).sp,
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
                color = WinNativeAccent,
                fontSize = (12f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = valueModifier,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors =
                SliderDefaults.colors(
                    thumbColor = WinNativeAccent,
                    activeTrackColor = WinNativeAccent,
                    inactiveTrackColor = TileResting,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
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
            color = WinNativeTextSecondary,
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
                    color = WinNativeTextSecondary,
                    fontSize = 13.sp,
                )
            },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = WinNativeTextPrimary),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WinNativeAccent,
                    unfocusedBorderColor = WinNativeOutline,
                    focusedTextColor = WinNativeTextPrimary,
                    unfocusedTextColor = WinNativeTextPrimary,
                    focusedContainerColor = WinNativeBackground,
                    unfocusedContainerColor = WinNativeBackground,
                    focusedLabelColor = WinNativeTextSecondary,
                    unfocusedLabelColor = WinNativeTextSecondary,
                    cursorColor = WinNativeAccent,
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
                    .background(WinNativeOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            WinNativeDialogButton(
                label = stringResource(R.string.common_ui_cancel),
                textColor = WinNativeTextPrimary,
                onClick = onDismiss,
            )
            WinNativeDialogButton(
                label = stringResource(R.string.common_ui_apply),
                textColor = WinNativeAccent,
                backgroundColor = WinNativeAccent.copy(alpha = 0.12f),
                borderColor = WinNativeAccent.copy(alpha = 0.3f),
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
        targetValue = if (checked) WinNativeAccent else RestingCardBorder,
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
                    .background(if (checked) WinNativeAccent else Color(0x14FFFFFF)),
        )
        Spacer(Modifier.width((8f * paneScale).dp))
        Text(
            text = label,
            color = if (checked) WinNativeAccent else WinNativeTextPrimary,
            fontSize = (11f * paneScale).sp,
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
        targetValue = if (checked) WinNativeAccent else RestingCardBorder,
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
                .padding(horizontal = (11f * paneScale).dp, vertical = (6f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (checked) WinNativeAccent else WinNativeTextPrimary,
                fontSize = (12f * paneScale).sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text =
                    if (checked) {
                        stringResource(R.string.common_ui_enabled)
                    } else {
                        stringResource(R.string.common_ui_disabled)
                    },
                color = WinNativeTextSecondary,
                fontSize = (10f * paneScale).sp,
            )
        }
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = switchInteractionSource,
                colors = outlinedSwitchColors(WinNativeAccent, WinNativeTextSecondary),
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
