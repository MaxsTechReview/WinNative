package com.winlator.cmod

import android.app.Activity
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border

import androidx.compose.material3.Slider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

data class XServerDrawerItem(
    val itemId: Int,
    val title: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val active: Boolean = false,
)

data class XServerDrawerState(
    val items: List<XServerDrawerItem>,
    val hudTransparency: Float = 1.0f,
    val hudScale: Float = 1.0f,
    val hudElements: BooleanArray = booleanArrayOf(true, true, true, true, true, true),
    val dualSeriesBatteryEnabled: Boolean = false,
)

interface XServerDrawerActionListener {
    fun onActionSelected(itemId: Int)
    fun onHUDElementToggled(index: Int, enabled: Boolean)
    fun onHUDTransparencyChanged(transparency: Float)
    fun onHUDScaleChanged(scale: Float)
    fun onDualSeriesBatteryChanged(enabled: Boolean)
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
): XServerDrawerState {
    val items = mutableListOf(
        XServerDrawerItem(
            itemId = R.id.main_menu_keyboard,
            title = context.getString(R.string.session_drawer_keyboard),
            subtitle = context.getString(R.string.session_drawer_keyboard_subtitle),
            iconRes = R.drawable.icon_keyboard,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_input_controls,
            title = context.getString(R.string.common_ui_input_controls),
            subtitle = context.getString(R.string.session_drawer_input_controls_subtitle),
            iconRes = R.drawable.icon_input_controls,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_fps_monitor,
            title = context.getString(R.string.session_drawer_fps_monitor),
            subtitle = if (fpsMonitorEnabled) context.getString(R.string.common_ui_enabled) else context.getString(R.string.common_ui_disabled),
            iconRes = R.drawable.icon_monitor,
            active = fpsMonitorEnabled,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_relative_mouse_movement,
            title = context.getString(R.string.session_drawer_relative_mouse_movement),
            subtitle = if (relativeMouseEnabled) context.getString(R.string.common_ui_enabled) else context.getString(R.string.common_ui_disabled),
            iconRes = R.drawable.ic_input_kbd_mouse_move,
            active = relativeMouseEnabled,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_disable_mouse,
            title = context.getString(R.string.session_drawer_mouse_input),
            subtitle = if (mouseDisabled) context.getString(R.string.common_ui_disabled) else context.getString(R.string.common_ui_enabled),
            iconRes = R.drawable.ic_input_kbd_mouse,
            active = !mouseDisabled,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_screen_effects,
            title = context.getString(R.string.session_effects_title),
            subtitle = context.getString(R.string.session_drawer_screen_effects_subtitle),
            iconRes = R.drawable.icon_screen_effect,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_toggle_fullscreen,
            title = context.getString(R.string.session_drawer_toggle_fullscreen),
            subtitle = context.getString(R.string.session_drawer_fullscreen_subtitle),
            iconRes = R.drawable.icon_fullscreen,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_native_rendering,
            title = nativeRenderingTitle,
            subtitle = nativeRenderingSubtitle,
            iconRes = R.drawable.ic_drivers,
            active = nativeRenderingEnabled,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_pause,
            title = if (paused) context.getString(R.string.session_drawer_resume) else context.getString(R.string.session_drawer_pause),
            subtitle = if (paused) context.getString(R.string.session_drawer_wine_processes_paused) else context.getString(R.string.session_drawer_pause_all_wine_processes),
            iconRes = if (paused) R.drawable.icon_play else R.drawable.icon_pause,
            active = paused,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_pip_mode,
            title = context.getString(R.string.session_drawer_picture_in_picture),
            subtitle = context.getString(R.string.session_drawer_pip_subtitle),
            iconRes = R.drawable.ic_picture_in_picture_alt,
        ),
        XServerDrawerItem(
            itemId = R.id.main_menu_task_manager,
            title = context.getString(R.string.session_task_title),
            subtitle = context.getString(R.string.session_drawer_task_manager_subtitle),
            iconRes = R.drawable.icon_task_manager,
        ),
    )

    if (showMagnifier) {
        items += XServerDrawerItem(
            itemId = R.id.main_menu_magnifier,
            title = context.getString(R.string.session_drawer_magnifier),
            subtitle = context.getString(R.string.session_drawer_magnifier_subtitle),
            iconRes = R.drawable.icon_magnifier,
        )
    }

    if (showLogs) {
        items += XServerDrawerItem(
            itemId = R.id.main_menu_logs,
            title = context.getString(R.string.session_drawer_logs),
            subtitle = context.getString(R.string.session_drawer_logs_subtitle),
            iconRes = R.drawable.icon_debug,
        )
    }

    items += XServerDrawerItem(
        itemId = R.id.main_menu_exit,
        title = context.getString(R.string.common_ui_exit),
        subtitle = context.getString(R.string.session_drawer_exit_subtitle),
        iconRes = R.drawable.icon_exit,
    )

    return XServerDrawerState(items, hudTransparency, hudScale, hudElements, dualSeriesBatteryEnabled)
}

fun setupXServerDrawerComposeView(
    composeView: ComposeView,
    state: XServerDrawerState,
    activity: Activity,
    listener: XServerDrawerActionListener,
) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF2F81F7),
                background = Color(0xFF141B24),
                surface = Color(0xFF1E252E),
                onSurface = Color(0xFFE6EDF3),
            )
        ) {
            XServerDrawerContent(state = state, listener = listener)
        }
    }
}

@Composable
private fun XServerDrawerContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(336.dp),
        color = Color(0xFF141B24),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141B24))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.items.forEach { item ->
                XServerDrawerRow(item = item, onClick = { listener.onActionSelected(item.itemId) })
                if (item.itemId == R.id.main_menu_fps_monitor && item.active) {
                    XServerHUDSettingsExpanded(state, listener)
                }
            }
        }
    }
}

@Composable
private fun XServerHUDSettingsExpanded(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    val card = Color(0xFF232C40)
    val accent = Color(0xFF2F81F7)
    val textSecondary = Color(0xFF8B949E)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(card)
            .padding(12.dp)
    ) {
        // Transparency Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alpha ${(state.hudTransparency * 100).toInt()}%",
                color = textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = state.hudTransparency,
                onValueChange = { listener.onHUDTransparencyChanged(it) },
                valueRange = 0.1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.24f)
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        // Scale Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scale ${(state.hudScale * 100).toInt()}%",
                color = textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(70.dp)
            )
            Slider(
                value = state.hudScale,
                onValueChange = { listener.onHUDScaleChanged(it) },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.24f)
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        // Toggles Grid (3 columns, 2 rows)
        val elementNames = listOf("FPS", "API", "GPU", "CPU", "BAT", "Graph")
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        if (index < elementNames.size) {
                            HUDCheckmarkToggle(
                                label = elementNames[index],
                                checked = state.hudElements[index],
                                onCheckedChange = { listener.onHUDElementToggled(index, it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        HUDCheckmarkToggle(
            label = stringResource(R.string.session_drawer_dual_series_battery),
            checked = state.dualSeriesBatteryEnabled,
            onCheckedChange = listener::onDualSeriesBatteryChanged,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HUDCheckmarkToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val textPrimary = Color(0xFFE6EDF3)
    val accent = Color(0xFF2F81F7)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = accent,
                uncheckedColor = Color(0xFF394048),
                checkmarkColor = Color.White
            )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = textPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun XServerDrawerRow(
    item: XServerDrawerItem,
    onClick: () -> Unit,
) {
    val accent = Color(0xFF2F81F7)
    val surface = Color(0xFF1E252E)
    val card = Color(0xFF232C40)
    val textPrimary = Color(0xFFE6EDF3)
    val textSecondary = Color(0xFF8B949E)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(card)
            .then(
                if (item.active) Modifier.border(
                    BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    RoundedCornerShape(18.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = if (item.active) accent else textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                color = textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (item.active) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.common_ui_on).uppercase(),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
