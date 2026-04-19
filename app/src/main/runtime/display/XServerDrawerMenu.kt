package com.winlator.cmod.runtime.display

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeSurfaceAlt
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlin.math.roundToInt

private val DrawerHeroTop = Color(0xFF171E2E)
private val DrawerHeroBottom = Color(0xFF11161F)
private val DrawerIconBox = Color(0xFF242434)
private val DrawerActiveSurface = Color(0xFF1E2A3D)
private val DrawerExitSurface = Color(0xFF1F1A21)
private val DrawerExitSurfacePressed = Color(0xFF251D25)
private val DrawerExitOutline = Color(0xFF4B3038)
private val DrawerExitIconBox = Color(0xFF2B2026)
private val DrawerExitTint = Color(0xFFE07A84)

private val DrawerPrimaryItemIds =
    setOf(
        R.id.main_menu_keyboard,
        R.id.main_menu_input_controls,
        R.id.main_menu_fps_monitor,
    )

private enum class HUDMetricEditor(
    val minPercent: Int,
    val maxPercent: Int,
) {
    ALPHA(minPercent = 10, maxPercent = 100),
    SCALE(minPercent = 50, maxPercent = 200),
}

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
)

class XServerDrawerStateHolder(
    initialState: XServerDrawerState,
) {
    var state by mutableStateOf(initialState, neverEqualPolicy())
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
): XServerDrawerState {
    val items =
        mutableListOf(
            XServerDrawerItem(
                itemId = R.id.main_menu_gyroscope,
                title = "Gyroscope",
                subtitle = "Configure Gyroscope Controls",
                icon = Icons.Outlined.SportsEsports,
            ),
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
                subtitle = context.getString(R.string.session_drawer_keyboard_subtitle),
                icon = Icons.Outlined.Keyboard,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_input_controls,
                title = context.getString(R.string.common_ui_input_controls),
                subtitle = context.getString(R.string.session_drawer_input_controls_subtitle),
                icon = Icons.Outlined.SportsEsports,
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
                itemId = R.id.main_menu_screen_effects,
                title = context.getString(R.string.session_effects_title),
                subtitle = context.getString(R.string.session_drawer_screen_effects_subtitle),
                icon = Icons.Outlined.Tune,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_toggle_fullscreen,
                title = context.getString(R.string.session_drawer_toggle_fullscreen),
                subtitle = context.getString(R.string.session_drawer_fullscreen_subtitle),
                icon = Icons.Outlined.Fullscreen,
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
                subtitle = context.getString(R.string.session_drawer_pip_subtitle),
                icon = Icons.Outlined.PictureInPictureAlt,
            ),
            XServerDrawerItem(
                itemId = R.id.main_menu_task_manager,
                title = context.getString(R.string.session_task_title),
                subtitle = context.getString(R.string.session_drawer_task_manager_subtitle),
                icon = Icons.AutoMirrored.Outlined.ViewList,
            ),
        )

    // items.add(
    //     3,
    //     XServerDrawerItem(
    //         itemId = R.id.main_menu_controller_manager,
    //         title = context.getString(R.string.session_gamepad_controller_manager),
    //         subtitle = context.getString(R.string.session_gamepad_external_controllers),
    //         icon = Icons.Outlined.SportsEsports,
    //     ),
    // )

    if (showMagnifier) {
        items +=
            XServerDrawerItem(
                itemId = R.id.main_menu_magnifier,
                title = context.getString(R.string.session_drawer_magnifier),
                subtitle =
                    if (nativeRenderingEnabled) {
                        context.getString(R.string.session_drawer_magnifier_disabled_native_subtitle)
                    } else {
                        context.getString(R.string.session_drawer_magnifier_subtitle)
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
                subtitle = context.getString(R.string.session_drawer_logs_subtitle),
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
    )
}

fun setupXServerDrawerComposeView(
    composeView: ComposeView,
    stateHolder: XServerDrawerStateHolder,
    _activity: Activity,
    listener: XServerDrawerActionListener,
) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        WinNativeTheme {
            XServerDrawerContent(state = stateHolder.state, listener = listener)
        }
    }
}

@Composable
private fun XServerDrawerContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    val primaryItems = remember(state.items) { state.items.filter { it.itemId in DrawerPrimaryItemIds } }
    val secondaryItems = remember(state.items) { state.items.filterNot { it.itemId in DrawerPrimaryItemIds } }

    Surface(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(336.dp),
        color = WinNativeBackground,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to DrawerHeroTop,
                                    0.42f to DrawerHeroBottom,
                                    1.0f to WinNativeBackground,
                                ),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrawerHeroCard()
            primaryItems.forEach { item ->
                if (item.itemId == R.id.main_menu_fps_monitor) {
                    XServerHUDCard(
                        item = item,
                        state = state,
                        listener = listener,
                        onToggleMonitor = { listener.onActionSelected(item.itemId) },
                    )
                } else {
                    XServerDrawerActionCard(
                        item = item,
                        onClick = { listener.onActionSelected(item.itemId) },
                    )
                }
            }

            secondaryItems.forEach { item ->
                XServerDrawerActionCard(
                    item = item,
                    onClick = { listener.onActionSelected(item.itemId) },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DrawerHeroCard() {
    Text(
        text = "WinNative",
        color = WinNativeAccent,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.65.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun XServerHUDCard(
    item: XServerDrawerItem,
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    onToggleMonitor: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val statusInteractionSource = remember { MutableInteractionSource() }
    val active = item.active
    val expanded = active && state.hudCardExpanded
    val cardClick =
        if (active) {
            { listener.onHUDCardExpandedChanged(!state.hudCardExpanded) }
        } else {
            onToggleMonitor
        }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "hudCardScale",
    )
    val cardColor by animateColorAsState(
        targetValue =
            when {
                active -> DrawerActiveSurface
                pressed -> WinNativeSurfaceAlt
                else -> WinNativeSurface
            },
        animationSpec = tween(180),
        label = "hudCardColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) WinNativeAccent.copy(alpha = 0.34f) else WinNativeOutline,
        animationSpec = tween(180),
        label = "hudCardBorder",
    )
    val iconBoxColor by animateColorAsState(
        targetValue = if (active) WinNativeAccent.copy(alpha = 0.16f) else DrawerIconBox,
        animationSpec = tween(180),
        label = "hudIconBoxColor",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "hudChevronRotation",
    )
    val subtitle =
        when {
            !active -> stringResource(R.string.session_drawer_hud_disabled_hint)
            expanded -> stringResource(R.string.session_drawer_hud_summary)
            else -> item.subtitle
        }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(cardColor)
                .border(BorderStroke(1.dp, borderColor), shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = cardClick,
                    ).padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(42.dp)
                        .clip(CircleShape)
                        .background(if (active) WinNativeAccent else Color.Transparent),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBoxColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (active) WinNativeAccent else WinNativeTextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = WinNativeTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = WinNativeTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            DrawerStatusPill(
                text = if (active) stringResource(R.string.common_ui_on) else stringResource(R.string.common_ui_off),
                active = active,
                interactionSource = statusInteractionSource,
                onClick = onToggleMonitor,
            )
            if (active) {
                Spacer(Modifier.width(6.dp))
                val chevronSource = remember { MutableInteractionSource() }
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(WinNativePanel)
                            .border(1.dp, WinNativeOutline, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = chevronSource,
                                indication = null,
                                onClick = { listener.onHUDCardExpandedChanged(!state.hudCardExpanded) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = WinNativeAccent,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .rotate(chevronRotation),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180, easing = FastOutSlowInEasing)),
        ) {
            XServerHUDSettingsExpanded(state = state, listener = listener)
        }
    }
}

@Composable
private fun XServerHUDSettingsExpanded(
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

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(WinNativePanel)
                    .border(1.dp, WinNativeOutline, RoundedCornerShape(18.dp))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                DrawerMetricChip(
                    label = stringResource(R.string.session_drawer_hud_alpha),
                    value = "${(state.hudTransparency * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                    onClick = { activeEditor = HUDMetricEditor.ALPHA },
                )
                DrawerMetricChip(
                    label = stringResource(R.string.session_drawer_hud_scale),
                    value = "${(state.hudScale * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                    onClick = { activeEditor = HUDMetricEditor.SCALE },
                )
            }

            DrawerSliderRow(
                label = stringResource(R.string.session_drawer_hud_alpha),
                valueText = "${(state.hudTransparency * 100).toInt()}%",
                value = state.hudTransparency,
                valueRange = 0.1f..1f,
                steps = 8,
                onValueChange = { listener.onHUDTransparencyChanged(it.snapToStep(0.1f, 0.1f, 1f)) },
            )

            DrawerSliderRow(
                label = stringResource(R.string.session_drawer_hud_scale),
                valueText = "${(state.hudScale * 100).toInt()}%",
                value = state.hudScale,
                valueRange = 0.5f..2.0f,
                steps = 14,
                onValueChange = { listener.onHUDScaleChanged(it.snapToStep(0.1f, 0.5f, 2.0f)) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.session_drawer_hud_elements),
                    color = WinNativeTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                )

                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            HUDToggleChip(
                                label = elementNames[index],
                                checked = state.hudElements[index],
                                onClick = { listener.onHUDElementToggled(index, !state.hudElements[index]) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            DrawerBooleanRow(
                title = stringResource(R.string.session_drawer_dual_series_battery),
                checked = state.dualSeriesBatteryEnabled,
                onCheckedChange = listener::onDualSeriesBatteryChanged,
            )
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "drawerMetricScale_$label",
    )

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WinNativeSurfaceAlt)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = WinNativeTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            color = WinNativeTextPrimary,
            fontSize = 12.sp,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = WinNativeTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                color = WinNativeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
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
                    inactiveTrackColor = WinNativeSurfaceAlt,
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue =
            when {
                checked -> WinNativeAccent.copy(alpha = 0.16f)
                pressed -> WinNativeSurface
                else -> WinNativeSurfaceAlt
            },
        animationSpec = tween(160),
        label = "hudChipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) WinNativeAccent.copy(alpha = 0.34f) else WinNativeOutline,
        animationSpec = tween(160),
        label = "hudChipBorder",
    )

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) WinNativeAccent else DrawerIconBox),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = WinNativeTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
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
    val rowInteractionSource = remember { MutableInteractionSource() }
    val switchInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WinNativeSurfaceAlt)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                ) { onCheckedChange(!checked) }
                .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = WinNativeTextPrimary,
                fontSize = 12.sp,
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
                fontSize = 10.sp,
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
private fun XServerDrawerActionCard(
    item: XServerDrawerItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val enabled = item.enabled
    val isExitAction = item.itemId == R.id.main_menu_exit
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "drawerActionScale_${item.itemId}",
    )
    val cardColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> WinNativePanel
                item.active -> DrawerActiveSurface
                isExitAction && pressed -> DrawerExitSurfacePressed
                isExitAction -> DrawerExitSurface
                pressed -> WinNativeSurfaceAlt
                else -> WinNativeSurface
            },
        animationSpec = tween(180),
        label = "drawerActionCardColor_${item.itemId}",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> WinNativeOutline.copy(alpha = 0.72f)
                item.active -> WinNativeAccent.copy(alpha = 0.28f)
                isExitAction -> DrawerExitOutline
                else -> WinNativeOutline
            },
        animationSpec = tween(180),
        label = "drawerActionBorderColor_${item.itemId}",
    )
    val iconBoxColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> DrawerIconBox.copy(alpha = 0.72f)
                item.active -> WinNativeAccent.copy(alpha = 0.16f)
                isExitAction -> DrawerExitIconBox
                else -> DrawerIconBox
            },
        animationSpec = tween(180),
        label = "drawerActionIconColor_${item.itemId}",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.56f,
        animationSpec = tween(180),
        label = "drawerActionAlpha_${item.itemId}",
    )
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(cardColor)
                .border(BorderStroke(1.dp, borderColor), shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (item.active && enabled) WinNativeAccent else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBoxColor)
                    .graphicsLayer {
                        alpha = contentAlpha
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint =
                    when {
                        !enabled -> WinNativeTextSecondary
                        item.active -> WinNativeAccent
                        isExitAction -> DrawerExitTint
                        else -> WinNativeTextPrimary
                    },
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = contentAlpha
                    },
        ) {
            Text(
                text = item.title,
                color = WinNativeTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                color = WinNativeTextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!enabled) {
            Spacer(Modifier.width(10.dp))
            DrawerStatusPill(
                text = stringResource(R.string.common_ui_disabled),
                active = false,
            )
        } else if (item.active) {
            Spacer(Modifier.width(10.dp))
            DrawerStatusPill(
                text = stringResource(R.string.common_ui_on),
                active = true,
            )
        }
    }
}

@Composable
private fun DrawerStatusPill(
    text: String,
    active: Boolean,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
) {
    val background = if (active) WinNativeAccent.copy(alpha = 0.16f) else WinNativePanel
    val border = if (active) WinNativeAccent.copy(alpha = 0.26f) else WinNativeOutline
    val textColor = if (active) WinNativeAccent else WinNativeTextSecondary
    val baseModifier =
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)

    Box(
        modifier =
            if (onClick != null && interactionSource != null) {
                baseModifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else {
                baseModifier
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}
