/* Settings > Debug screen — Jetpack Compose / Material3.
 * Uses a LazyColumn for the main content. */
package com.winlator.cmod.feature.settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.dialog.PopupDialog
import com.winlator.cmod.shared.ui.toast.WinToast
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Palette (mirrors StoresScreen)
private val BgDark = Color(0xFF11111C)
private val CardDark = Color(0xFF1C1C2A)
private val CardBorder = Color(0xFF2A2A3A)
private val IconBoxBg = Color(0xFF242434)
private val SurfaceDark = Color(0xFF21212A)
private val Accent = Color(0xFF1A9FFF)
private val Warning = Color(0xFFFF4444)
private val Success = Color(0xFF7CC142)
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
    val vulkanValidationLayers: Boolean = false,
    val wnHybridMode: Boolean = false,
    val logsSize: String = "0 B",
)

data class LogFileEntry(
    val name: String,
    val sizeText: String,
    val dateText: String,
    val absolutePath: String,
    val downloaded: Boolean = false,
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
    onVulkanValidationLayersChanged: (Boolean) -> Unit,
    onWnHybridModeChanged: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
    onDownloadLogs: () -> String?,
    onDeleteLogs: () -> Unit,
    onListLogFiles: () -> List<LogFileEntry>,
    onReadLogFile: (LogFileEntry) -> String,
    onShareLogFile: (LogFileEntry) -> Unit,
    onDownloadLogFile: (LogFileEntry) -> String?,
    onDeleteLogFile: (LogFileEntry) -> Unit,
) {
    var showChannelsDialog by remember { mutableStateOf(false) }
    var showLogsBrowser by remember { mutableStateOf(false) }
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

    if (showLogsBrowser) {
        val initialFiles = remember { onListLogFiles() }
        LogsBrowserDialog(
            initialFiles = initialFiles,
            logsSize = state.logsSize,
            onReadLogFile = onReadLogFile,
            onShareAllLogs = onShareLogs,
            onDownloadAllLogs = onDownloadLogs,
            onDeleteAllLogs = onDeleteLogs,
            onShareLogFile = onShareLogFile,
            onDownloadLogFile = onDownloadLogFile,
            onDeleteLogFile = onDeleteLogFile,
            onDismiss = { showLogsBrowser = false },
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

/*
        item(key = "vulkan_validation_layers_card") {
            SettingsToggleCard(
                title = stringResource(R.string.settings_debug_vulkan_validation_layers_title),
                subtitle = stringResource(R.string.settings_debug_vulkan_validation_layers_subtitle),
                icon = Icons.Outlined.BugReport,
                accentColor = Warning,
                checked = state.vulkanValidationLayers,
                onCheckedChange = onVulkanValidationLayersChanged,
            )
        }
*/

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

        item(key = "log_actions_row") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogActionButton(
                    icon = Icons.Outlined.FolderOpen,
                    label = stringResource(R.string.settings_debug_open_logs_folder_short),
                    accentColor = Accent,
                    onClick = { showLogsBrowser = true },
                )
            }
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

@Composable
private fun RowScope.LogActionButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    sublabel: String? = null,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "logActionScale",
    )
    Row(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LogsBrowserDialog(
    initialFiles: List<LogFileEntry>,
    logsSize: String,
    onReadLogFile: (LogFileEntry) -> String,
    onShareAllLogs: () -> Unit,
    onDownloadAllLogs: () -> String?,
    onDeleteAllLogs: () -> Unit,
    onShareLogFile: (LogFileEntry) -> Unit,
    onDownloadLogFile: (LogFileEntry) -> String?,
    onDeleteLogFile: (LogFileEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var files by remember { mutableStateOf(initialFiles) }
    var selected by remember { mutableStateOf<LogFileEntry?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var downloaded by remember {
        mutableStateOf(initialFiles.filter { it.downloaded }.map { it.absolutePath }.toSet())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val dialogView = LocalView.current
        val context = LocalContext.current
        val showSavedToast: (String) -> Unit = { path ->
            WinToast.show(context, context.getString(R.string.settings_debug_logs_saved, path), dialogView)
        }
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
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = availableHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardDark)
                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            val forward = targetState != null
                            val direction =
                                if (forward) {
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                } else {
                                    AnimatedContentTransitionScope.SlideDirection.Right
                                }
                            (slideIntoContainer(direction, tween(280)) + fadeIn(tween(280)))
                                .togetherWith(
                                    slideOutOfContainer(direction, tween(280)) + fadeOut(tween(280)),
                                )
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "logsBrowserNav",
                    ) { target ->
                        if (target == null) {
                            LogFileListView(
                                files = files,
                                onOpen = { selected = it },
                                onShareAllLogs = onShareAllLogs,
                                onDownloadAllLogs = { onDownloadAllLogs()?.let(showSavedToast) },
                                onDeleteAllLogs = { showDeleteAllConfirm = true },
                                onShareLogFile = onShareLogFile,
                                downloadedPaths = downloaded,
                                onDownloadLogFile = { entry ->
                                    onDownloadLogFile(entry)?.let { path ->
                                        downloaded = downloaded + entry.absolutePath
                                        showSavedToast(path)
                                    }
                                },
                                onDeleteLogFile = { entry ->
                                    onDeleteLogFile(entry)
                                    files = files.filterNot { it.absolutePath == entry.absolutePath }
                                },
                                onClose = onDismiss,
                            )
                        } else {
                            LogDetailView(
                                entry = target,
                                onBack = { selected = null },
                                onClose = onDismiss,
                                onReadLogFile = onReadLogFile,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        Dialog(onDismissRequest = { showDeleteAllConfirm = false }) {
            PopupDialog(
                title = stringResource(R.string.settings_debug_delete_logs_confirm_title),
                message = stringResource(R.string.settings_debug_delete_logs_confirm_message, logsSize),
                confirmLabel = stringResource(R.string.settings_debug_delete_logs_short),
                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                icon = Icons.Outlined.Delete,
                accentColor = Warning,
                onCancel = { showDeleteAllConfirm = false },
                onConfirm = {
                    showDeleteAllConfirm = false
                    onDeleteAllLogs()
                    files = emptyList()
                },
            )
        }
    }
}

@Composable
private fun LogsHeaderShareAll(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "shareAllScale",
    )
    Row(
        modifier =
            Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222232))
                .border(1.dp, Accent.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.settings_debug_share_all_logs_short),
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LogsHeaderDownloadAll(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "downloadAllScale",
    )
    Row(
        modifier =
            Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222232))
                .border(1.dp, Success.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.settings_debug_download_all_logs_short),
            color = Success,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LogsHeaderDeleteAll(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "deleteAllScale",
    )
    Row(
        modifier =
            Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222232))
                .border(1.dp, Warning.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                .pointerInput(onClick) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() },
                    )
                }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = null,
            tint = Warning,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.settings_debug_delete_all_logs_short),
            color = Warning,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LogsHeaderIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = TextSecondary,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ColumnScope.LogFileList(
    files: List<LogFileEntry>,
    onOpen: (LogFileEntry) -> Unit,
    onShare: (LogFileEntry) -> Unit,
    downloadedPaths: Set<String>,
    onDownload: (LogFileEntry) -> Unit,
    onDelete: (LogFileEntry) -> Unit,
) {
    if (files.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_debug_no_logs_available),
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(files, key = { it.absolutePath }) { entry ->
            LogFileRow(
                entry = entry,
                onOpen = { onOpen(entry) },
                onShare = { onShare(entry) },
                isDownloaded = entry.absolutePath in downloadedPaths,
                onDownload = { onDownload(entry) },
                onDelete = { onDelete(entry) },
            )
        }
    }
}

@Composable
private fun LogFileRow(
    entry: LogFileEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(IconBoxBg)
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .pointerInput(entry.absolutePath) {
                    detectTapGestures(onTap = { onOpen() })
                }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.sizeText} · ${entry.dateText}",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        LogRowIconButton(
            icon = Icons.Outlined.Delete,
            tint = Warning,
            contentDescription = stringResource(R.string.settings_debug_delete_logs),
            onClick = onDelete,
        )
        Spacer(Modifier.width(12.dp))
        LogRowIconButton(
            icon = Icons.Outlined.Share,
            tint = Accent,
            contentDescription = stringResource(R.string.settings_debug_share_logs),
            onClick = onShare,
        )
        Spacer(Modifier.width(12.dp))
        LogRowIconButton(
            icon = Icons.Outlined.Download,
            tint = Success,
            contentDescription = stringResource(R.string.settings_debug_download_logs),
            onClick = onDownload,
            filled = isDownloaded,
        )
    }
}

@Composable
private fun LogRowIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val fillBrush =
        if (filled) {
            Brush.verticalGradient(listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.14f)))
        } else {
            SolidColor(Color(0xFF222232))
        }
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(fillBrush, shape)
                .border(1.dp, tint.copy(alpha = if (filled) 0.65f else 0.30f), shape)
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LogFileListView(
    files: List<LogFileEntry>,
    onOpen: (LogFileEntry) -> Unit,
    onShareAllLogs: () -> Unit,
    onDownloadAllLogs: () -> Unit,
    onDeleteAllLogs: () -> Unit,
    onShareLogFile: (LogFileEntry) -> Unit,
    downloadedPaths: Set<String>,
    onDownloadLogFile: (LogFileEntry) -> Unit,
    onDeleteLogFile: (LogFileEntry) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_debug_logs_browser_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LogsHeaderDeleteAll(onClick = onDeleteAllLogs)
            Spacer(Modifier.width(16.dp))
            LogsHeaderShareAll(onClick = onShareAllLogs)
            Spacer(Modifier.width(16.dp))
            LogsHeaderDownloadAll(onClick = onDownloadAllLogs)
            Spacer(Modifier.width(8.dp))
            LogsHeaderIcon(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.common_ui_close),
                onClick = onClose,
                tint = TextPrimary,
                iconSize = 22.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        LogFileList(
            files = files,
            onOpen = onOpen,
            onShare = onShareLogFile,
            downloadedPaths = downloadedPaths,
            onDownload = onDownloadLogFile,
            onDelete = onDeleteLogFile,
        )
    }
}

@Composable
private fun LogDetailView(
    entry: LogFileEntry,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onReadLogFile: (LogFileEntry) -> String,
) {
    var content by remember(entry.absolutePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.absolutePath) {
        content = withContext(Dispatchers.IO) { onReadLogFile(entry) }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (content != null) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "logTitleFade",
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogsHeaderIcon(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_ui_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = entry.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .alpha(titleAlpha),
            )
            Spacer(Modifier.width(6.dp))
            LogsHeaderIcon(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.common_ui_close),
                onClick = onClose,
                tint = TextPrimary,
                iconSize = 22.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        LogContentBody(
            content = content,
            entry = entry,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@Composable
private fun LogContentBody(
    content: String?,
    entry: LogFileEntry,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = content == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp),
            )
        }
        AnimatedVisibility(
            visible = content != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 320)),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "${entry.sizeText} · ${entry.dateText}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val logScrollState = rememberScrollState()
                val scrollbarAlpha by animateFloatAsState(
                    targetValue = if (logScrollState.isScrollInProgress) 1f else 0f,
                    animationSpec =
                        tween(
                            durationMillis = if (logScrollState.isScrollInProgress) 150 else 500,
                            delayMillis = if (logScrollState.isScrollInProgress) 0 else 300,
                        ),
                    label = "scrollbarAlpha",
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .verticalScrollbar(logScrollState, TextSecondary.copy(alpha = 0.6f)) { scrollbarAlpha }
                            .padding(10.dp)
                            .verticalScroll(logScrollState),
                ) {
                    SelectionContainer {
                        Text(
                            text = content.orEmpty(),
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    thumbColor: Color,
    width: Dp = 3.dp,
    minThumbHeight: Dp = 24.dp,
    edgePadding: Dp = 5.dp,
    alpha: () -> Float = { 1f },
): Modifier =
    drawWithContent {
        drawContent()
        val thumbAlpha = alpha().coerceIn(0f, 1f)
        val maxValue = scrollState.maxValue
        if (maxValue <= 0 || thumbAlpha <= 0f) return@drawWithContent
        val widthPx = width.toPx()
        val padPx = edgePadding.toPx()
        val viewportHeight = size.height
        val trackHeight = (viewportHeight - 2 * padPx).coerceAtLeast(0f)
        val contentHeight = viewportHeight + maxValue
        val thumbHeight =
            (trackHeight * (viewportHeight / contentHeight))
                .coerceIn(minThumbHeight.toPx().coerceAtMost(trackHeight), trackHeight)
        val thumbOffsetY = padPx + (scrollState.value.toFloat() / maxValue) * (trackHeight - thumbHeight)
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(size.width - widthPx - padPx, thumbOffsetY),
            size = Size(widthPx, thumbHeight),
            cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
            alpha = thumbAlpha,
        )
    }
