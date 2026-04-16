/* Settings > Debug screen — Jetpack Compose / Material3.
 * Uses a LazyColumn for the main content. */
package com.winlator.cmod.feature.settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.outlinedSwitchColors

// Palette (mirrors StoresScreen)
private val BgDark = Color(0xFF18181D)
private val CardDark = Color(0xFF1C1C2A)
private val CardBorder = Color(0xFF2A2A3A)
private val IconBoxBg = Color(0xFF242434)
private val SurfaceDark = Color(0xFF21212A)
private val Accent = Color(0xFF1A9FFF)
private val Warning = Color(0xFFFF4444)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)

// State
data class DebugState(
    val appDebug: Boolean = false,
    val wineDebug: Boolean = false,
    val wineChannels: List<String> = emptyList(),
    val box64Logs: Boolean = false,
    val fexcoreLogs: Boolean = false,
    val steamLogs: Boolean = false,
    val inputLogs: Boolean = false,
    val downloadLogs: Boolean = false,
)

// Root
@Composable
fun DebugScreen(
    state: DebugState,
    wineChannelOptions: List<String>,
    onAppDebugChanged: (Boolean) -> Unit,
    onWineDebugChanged: (Boolean) -> Unit,
    onWineChannelsChanged: (List<String>) -> Unit,
    onResetWineChannels: () -> Unit,
    onRemoveWineChannel: (String) -> Unit,
    onBox64LogsChanged: (Boolean) -> Unit,
    onFexcoreLogsChanged: (Boolean) -> Unit,
    onSteamLogsChanged: (Boolean) -> Unit,
    onInputLogsChanged: (Boolean) -> Unit,
    onDownloadLogsChanged: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
) {
    var showChannelsDialog by remember { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val navBarStartPadding = navBarPadding.calculateStartPadding(layoutDirection)
    val navBarEndPadding = navBarPadding.calculateEndPadding(layoutDirection)
    val navBarBottomPadding = navBarPadding.calculateBottomPadding()

    if (showChannelsDialog) {
        WineChannelsDialog(
            options = wineChannelOptions,
            initiallySelected = state.wineChannels,
            onDismiss = { showChannelsDialog = false },
            onConfirm = { selected ->
                onWineChannelsChanged(selected)
                showChannelsDialog = false
            },
        )
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BgDark),
        contentPadding =
            PaddingValues(
                start = 16.dp + navBarStartPadding,
                end = 16.dp + navBarEndPadding,
                top = 16.dp,
                bottom = 4.dp + navBarBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "application_section") {
            SectionLabel(stringResource(R.string.common_ui_application))
        }

        item(key = "app_debug_card") {
            SettingsToggleCard(
                title = stringResource(R.string.common_ui_application),
                subtitle = stringResource(R.string.settings_debug_log_to_file_desc),
                icon = Icons.Outlined.BugReport,
                accentColor = Warning,
                checked = state.appDebug,
                onCheckedChange = onAppDebugChanged,
            )
        }

        item(key = "emulation_section") {
            SectionLabel(stringResource(R.string.settings_debug_section_emulation), modifier = Modifier.padding(top = 8.dp))
        }

        item(key = "wine_logs_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_wine_logs_title),
                subtitle = stringResource(R.string.settings_debug_wine_logs_subtitle),
                icon = Icons.Outlined.Terminal,
                checked = state.wineDebug,
                onCheckedChange = onWineDebugChanged,
            )
        }

        item(key = "wine_channels_card") {
            WineChannelsCard(
                channels = state.wineChannels,
                enabled = state.wineDebug,
                onEdit = { showChannelsDialog = true },
                onReset = onResetWineChannels,
                onRemoveChannel = onRemoveWineChannel,
            )
        }

        item(key = "box64_logs_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_box_logs_title),
                subtitle = stringResource(R.string.settings_debug_box_logs_subtitle),
                icon = Icons.Outlined.Memory,
                checked = state.box64Logs,
                onCheckedChange = onBox64LogsChanged,
            )
        }

        item(key = "fexcore_logs_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_fex_logs_title),
                subtitle = stringResource(R.string.settings_debug_fex_logs_subtitle),
                icon = Icons.Outlined.Memory,
                checked = state.fexcoreLogs,
                onCheckedChange = onFexcoreLogsChanged,
            )
        }

        item(key = "subsystems_section") {
            SectionLabel(stringResource(R.string.settings_debug_section_subsystems), modifier = Modifier.padding(top = 8.dp))
        }

        item(key = "steam_logs_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_steam_logs_title),
                subtitle = stringResource(R.string.settings_debug_steam_logs_subtitle),
                icon = Icons.Outlined.SportsEsports,
                checked = state.steamLogs,
                onCheckedChange = onSteamLogsChanged,
            )
        }

        item(key = "input_logs_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_input_logs),
                subtitle = stringResource(R.string.settings_debug_input_logs_description),
                icon = Icons.Outlined.Gamepad,
                checked = state.inputLogs,
                onCheckedChange = onInputLogsChanged,
            )
        }

        item(key = "download_logs_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_download_logs),
                subtitle = stringResource(R.string.settings_debug_download_logs_description),
                icon = Icons.Outlined.CloudDownload,
                checked = state.downloadLogs,
                onCheckedChange = onDownloadLogsChanged,
            )
        }

        item(key = "tools_section") {
            SectionLabel(stringResource(R.string.settings_debug_section_tools), modifier = Modifier.padding(top = 8.dp))
        }

        item(key = "share_logs_button") {
            ShareLogsButton(onClick = onShareLogs)
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Section label
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

// Settings toggle card
@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = Accent,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.78f),
                colors =
                    outlinedSwitchColors(
                        accentColor = accentColor,
                        textSecondaryColor = TextSecondary,
                    ),
            )
        }
    }
}

// Wine debug channels card (shown when Wine debug is enabled)
@Composable
private fun WineChannelsCard(
    channels: List<String>,
    enabled: Boolean,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onRemoveChannel: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.48f)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(IconBoxBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_debug_wine_channels_title),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.settings_debug_wine_channels_summary),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SmallActionButton(
                    label = stringResource(R.string.common_ui_select),
                    textColor = Accent,
                    onClick = { if (enabled) onEdit() },
                )
                Spacer(Modifier.width(6.dp))
                SmallActionButton(
                    label = stringResource(R.string.common_ui_reset),
                    textColor = TextSecondary,
                    onClick = { if (enabled) onReset() },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (channels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_debug_no_channels_selected),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                } else {
                    channels.forEach { channel ->
                        ChannelChip(
                            label = channel,
                            onRemove = { if (enabled) onRemoveChannel(channel) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelChip(
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(IconBoxBg)
                .border(1.dp, CardBorder, RoundedCornerShape(7.dp))
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .pointerInput(onRemove) {
                        detectTapGestures(onTap = { onRemove() })
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.settings_debug_remove_channel_desc, label),
                tint = TextSecondary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "debugBtnScale",
    )
    Box(
        modifier =
            Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222232))
                .border(1.dp, textColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }.padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// Wine debug channel selector dialog
@Composable
private fun WineChannelsDialog(
    options: List<String>,
    initiallySelected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val selected =
        remember(initiallySelected) {
            mutableStateOf(initiallySelected.toSet())
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                // Parent activity runs edge-to-edge (WindowCompat.setDecorFitsSystemWindows(window, false)),
                // so we also take the dialog window edge-to-edge and pad for insets manually below.
                // This gives predictable behavior regardless of platform defaults.
                decorFitsSystemWindows = false,
            ),
    ) {
        // fillMaxSize + safeDrawing inset padding keeps the dialog clear of the
        // system status/nav bars and any display cutout on every device.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val availableHeight = maxHeight
            Box(
                modifier =
                    Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .heightIn(max = availableHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardDark)
                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text(
                        text = stringResource(R.string.settings_debug_wine_debug_channel),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_debug_channel_toggle_hint),
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))

                    ChannelGrid(
                        options = options,
                        selected = selected.value,
                        onToggle = { channel ->
                            selected.value =
                                if (channel in selected.value) {
                                    selected.value - channel
                                } else {
                                    selected.value + channel
                                }
                        },
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        SmallActionButton(
                            label = stringResource(R.string.common_ui_cancel),
                            textColor = TextSecondary,
                            onClick = onDismiss,
                        )
                        SmallActionButton(
                            label = stringResource(R.string.common_ui_confirm),
                            textColor = Accent,
                            onClick = {
                                val ordered = options.filter { it in selected.value }
                                onConfirm(ordered)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChannelGrid(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (options.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_debug_no_channels_available),
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    // Adaptive grid reflows columns on smaller screens (3 cols on ~300dp+ wide,
    // 2 cols on narrow ~200dp wide). weight(1f, fill = false) lets the grid
    // shrink on short landscape screens without pushing buttons off-screen.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 92.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { channel ->
            SelectableChannelChip(
                label = channel,
                isSelected = channel in selected,
                onToggle = { onToggle(channel) },
            )
        }
    }
}

@Composable
private fun SelectableChannelChip(
    label: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val bg = if (isSelected) Accent.copy(alpha = 0.18f) else IconBoxBg
    val borderColor = if (isSelected) Accent.copy(alpha = 0.55f) else CardBorder
    val textColor = if (isSelected) Accent else TextPrimary
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .pointerInput(label) {
                    detectTapGestures(onTap = { onToggle() })
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// Share logs button
@Composable
private fun ShareLogsButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "shareScale",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_debug_share_logs),
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
