/* Settings > Other screen — Jetpack Compose / Material3.
 * Scrolling delegated to a View-level ScrollView in OtherSettingsFragment,
 * matching StoresFragment and DebugFragment. */
package com.winlator.cmod

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.winlator.cmod.ui.outlinedSwitchColors
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Palette (mirrors DebugScreen / StoresScreen)
private val BgDark        = Color(0xFF18181D)
private val CardDark      = Color(0xFF1C1C2A)
private val CardBorder    = Color(0xFF2A2A3A)
private val IconBoxBg     = Color(0xFF242434)
private val SurfaceDark   = Color(0xFF21212A)
private val Accent        = Color(0xFF1A9FFF)
private val TextPrimary   = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)

// State
data class OtherSettingsState(
    val checkForUpdates: Boolean = true,
    val languageLabels: List<String> = emptyList(),
    val languageIndex: Int = 0,
    val refreshRateLabels: List<String> = emptyList(),
    val refreshRateIndex: Int = 0,
    val soundFontFiles: List<String> = emptyList(),
    val soundFontIndex: Int = 0,
    val winlatorPath: String = "",
    val shortcutExportPath: String = "",
    val cursorSpeedPercent: Int = 100,
    val useDRI3: Boolean = true,
    val cursorLock: Boolean = false,
    val xinputDisabled: Boolean = false,
    val enableFileProvider: Boolean = true,
    val openInBrowser: Boolean = false,
    val shareClipboard: Boolean = false,
    val imagefsInstallProgress: Int? = null,
)

// Root
@Composable
fun OtherSettingsScreen(
    state: OtherSettingsState,
    onCheckForUpdatesChanged: (Boolean) -> Unit,
    onCheckForUpdatesNow: () -> Unit,
    onLanguageSelected: (Int) -> Unit,
    onRefreshRateSelected: (Int) -> Unit,
    onSoundFontSelected: (Int) -> Unit,
    onInstallSoundFont: () -> Unit,
    onRemoveSoundFont: () -> Unit,
    onPickWinlatorPath: () -> Unit,
    onPickShortcutExportPath: () -> Unit,
    onCursorSpeedChanged: (Int) -> Unit,
    onUseDRI3Changed: (Boolean) -> Unit,
    onCursorLockChanged: (Boolean) -> Unit,
    onXinputDisabledChanged: (Boolean) -> Unit,
    onEnableFileProviderChanged: (Boolean) -> Unit,
    onOpenInBrowserChanged: (Boolean) -> Unit,
    onShareClipboardChanged: (Boolean) -> Unit,
    onReinstallImagefs: () -> Unit,
) {
    var showReinstallDialog by remember { mutableStateOf(false) }

    if (showReinstallDialog) {
        ReinstallImagefsConfirmDialog(
            onConfirm = {
                showReinstallDialog = false
                onReinstallImagefs()
            },
            onDismiss = { showReinstallDialog = false },
        )
    }

    state.imagefsInstallProgress?.let { percent ->
        ImagefsInstallProgressDialog(percent = percent)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.common_ui_application))

        UpdatesCard(
            checked = state.checkForUpdates,
            onCheckedChange = onCheckForUpdatesChanged,
            onCheckNow = onCheckForUpdatesNow,
        )

        SettingsDropdownCard(
            title = stringResource(R.string.settings_other_language_title),
            subtitle = stringResource(R.string.settings_other_language_summary),
            icon = Icons.Filled.Language,
            options = state.languageLabels,
            selectedIndex = state.languageIndex,
            onOptionSelected = onLanguageSelected,
        )

        SectionLabel(stringResource(R.string.session_display_title), modifier = Modifier.padding(top = 8.dp))

        SettingsDropdownCard(
            title = stringResource(R.string.settings_general_refresh_rate),
            subtitle = stringResource(R.string.settings_general_refresh_rate_summary),
            icon = Icons.Filled.Monitor,
            options = state.refreshRateLabels,
            selectedIndex = state.refreshRateIndex,
            onOptionSelected = onRefreshRateSelected,
        )

        SectionLabel(stringResource(R.string.settings_audio_sound), modifier = Modifier.padding(top = 8.dp))

        SoundFontCard(
            files = state.soundFontFiles,
            selectedIndex = state.soundFontIndex,
            onSelected = onSoundFontSelected,
            onInstall = onInstallSoundFont,
            onRemove = onRemoveSoundFont,
        )

        SectionLabel(stringResource(R.string.settings_general_paths_title), modifier = Modifier.padding(top = 8.dp))

        FolderPathCard(
            label = stringResource(R.string.settings_general_winlator_path_title),
            path = state.winlatorPath,
            onBrowse = onPickWinlatorPath,
        )
        FolderPathCard(
            label = stringResource(R.string.settings_general_shortcut_export_path_title),
            path = state.shortcutExportPath,
            onBrowse = onPickShortcutExportPath,
        )

        SectionLabel(stringResource(R.string.session_xserver_title), modifier = Modifier.padding(top = 8.dp))

        CursorSpeedCard(
            percent = state.cursorSpeedPercent,
            onPercentChanged = onCursorSpeedChanged,
        )

        SettingsToggleCard(
            title = stringResource(R.string.session_xserver_use_dri3_extension),
            subtitle = stringResource(R.string.session_xserver_use_dri3_description),
            icon = Icons.Filled.Visibility,
            checked = state.useDRI3,
            onCheckedChange = onUseDRI3Changed,
        )

        SettingsToggleCard(
            title = stringResource(R.string.settings_general_cursor_lock_title),
            subtitle = stringResource(R.string.settings_general_cursor_lock_summary),
            icon = Icons.Filled.Mouse,
            checked = state.cursorLock,
            onCheckedChange = onCursorLockChanged,
        )

        SettingsToggleCard(
            title = stringResource(R.string.settings_general_xinput_toggle_title),
            subtitle = stringResource(R.string.settings_general_xinput_toggle_summary),
            icon = Icons.Filled.SportsEsports,
            checked = state.xinputDisabled,
            onCheckedChange = onXinputDisabledChanged,
        )

        SectionLabel(stringResource(R.string.settings_other_section_integration), modifier = Modifier.padding(top = 8.dp))

        SettingsToggleCard(
            title = stringResource(R.string.settings_general_enable_file_provider),
            subtitle = stringResource(R.string.settings_general_file_provider_summary),
            icon = Icons.Filled.Folder,
            checked = state.enableFileProvider,
            onCheckedChange = onEnableFileProviderChanged,
        )

        SettingsToggleCard(
            title = stringResource(R.string.settings_general_open_with_android_browser),
            subtitle = stringResource(R.string.settings_general_open_browser_summary),
            icon = Icons.Filled.OpenInBrowser,
            checked = state.openInBrowser,
            onCheckedChange = onOpenInBrowserChanged,
        )

        SettingsToggleCard(
            title = stringResource(R.string.settings_general_share_android_clipboard),
            subtitle = stringResource(R.string.settings_general_clipboard_summary),
            icon = Icons.Filled.ContentCopy,
            checked = state.shareClipboard,
            onCheckedChange = onShareClipboardChanged,
        )

        SectionLabel(stringResource(R.string.settings_general_imagefs), modifier = Modifier.padding(top = 8.dp))

        ReinstallImagefsCard(onClick = { showReinstallDialog = true })

        Spacer(Modifier.height(24.dp))
    }
}

// Section label
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
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
                colors = outlinedSwitchColors(
                    accentColor = accentColor,
                    textSecondaryColor = TextSecondary,
                ),
            )
        }
    }
}

// Check for Updates card: switch + "Check Now" inline button
@Composable
private fun UpdatesCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_general_check_for_updates),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.settings_general_check_for_updates_summary),
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            SmallActionButton(label = stringResource(R.string.common_ui_check), textColor = Accent, onClick = onCheckNow)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.78f),
                colors = outlinedSwitchColors(
                    accentColor = Accent,
                    textSecondaryColor = TextSecondary,
                ),
            )
        }
    }
}

// Generic dropdown card (labels list + index selection)
@Composable
private fun SettingsDropdownCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    accentColor: Color = Accent,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val selectedLabel = options.getOrNull(safeIndex) ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
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
            Spacer(Modifier.width(8.dp))
            Box {
                var isPressed by remember { mutableStateOf(false) }
                val btnScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.93f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "otherDropdownScale",
                )
                Row(
                    modifier = Modifier
                        .scale(btnScale)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222232))
                        .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                        .pointerInput(options) {
                            detectTapGestures(
                                onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                                onTap = { if (options.isNotEmpty()) expanded = true },
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .widthIn(max = 180.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedLabel,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = Color(0xFF24243B),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.widthIn(max = 260.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options.forEachIndexed { index, label ->
                            val isSelected = index == safeIndex
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (isSelected) accentColor else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        softWrap = true,
                                    )
                                },
                                onClick = { onOptionSelected(index); expanded = false },
                                modifier = Modifier.background(
                                    if (isSelected) accentColor.copy(alpha = 0.08f) else Color.Transparent
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// SoundFont card: dropdown + Install + Remove
@Composable
private fun SoundFontCard(
    files: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))
    val selectedLabel = files.getOrNull(safeIndex) ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IconBoxBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_audio_midi_sound_font),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.settings_audio_summary),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    var isPressed by remember { mutableStateOf(false) }
                    val btnScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "sfDropdownScale",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(btnScale)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222232))
                            .border(1.dp, Accent.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                            .pointerInput(files) {
                                detectTapGestures(
                                    onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                                    onTap = { if (files.isNotEmpty()) expanded = true },
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = selectedLabel,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = RoundedCornerShape(8.dp),
                        containerColor = Color(0xFF24243B),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            files.forEachIndexed { index, label ->
                                val isSelected = index == safeIndex
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Accent else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            softWrap = true,
                                        )
                                    },
                                    onClick = { onSelected(index); expanded = false },
                                    modifier = Modifier.background(
                                        if (isSelected) Accent.copy(alpha = 0.08f) else Color.Transparent
                                    ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                SmallActionButton(label = stringResource(R.string.common_ui_install), textColor = Accent, onClick = onInstall)
                Spacer(Modifier.width(6.dp))
                SmallActionButton(label = stringResource(R.string.common_ui_remove), textColor = TextSecondary, onClick = onRemove)
            }
        }
    }
}

// Folder path card (mirrors StoresScreen.FolderPathCard)
@Composable
private fun FolderPathCard(
    label: String,
    path: String,
    onBrowse: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = path.ifEmpty { stringResource(R.string.common_ui_not_configured) },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            SmallActionButton(
                label = stringResource(R.string.settings_general_choose_path),
                textColor = Accent,
                onClick = onBrowse,
            )
        }
    }
}

// Cursor speed slider card
@Composable
private fun CursorSpeedCard(
    percent: Int,
    onPercentChanged: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IconBoxBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_general_cursor_speed),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.settings_general_xserver_cursor_summary),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${percent}%",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = percent.toFloat(),
                onValueChange = { onPercentChanged(it.toInt()) },
                valueRange = 10f..200f,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = SurfaceDark,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Reinstall imagefs confirm dialog (Compose replacement for ContentDialog.confirm)
@Composable
private fun ReinstallImagefsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IconBoxBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_general_reinstall_imagefs),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_general_confirm_reinstall_imagefs),
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(20.dp))
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
                        label = stringResource(R.string.common_ui_reinstall),
                        textColor = Accent,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

// ImageFS install progress dialog (non-dismissable, shown while reinstall is running)
@Composable
private fun ImagefsInstallProgressDialog(percent: Int) {
    val safePercent = percent.coerceIn(0, 100)
    // Smoothly interpolate the bar between discrete progress updates from the install callback.
    val animatedProgress by animateFloatAsState(
        targetValue = safePercent / 100f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "imagefsBarProgress",
    )
    // Animate the percent label alongside the bar so the number ticks smoothly too.
    val animatedPercent by animateIntAsState(
        targetValue = safePercent,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "imagefsPercentLabel",
    )
    Dialog(
        onDismissRequest = { /* non-dismissable */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IconBoxBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setup_wizard_installing_system_files),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_other_keep_app_open),
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${animatedPercent}%",
                        color = Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Accent,
                    trackColor = SurfaceDark,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

// Reinstall imagefs card with centered action button
@Composable
private fun ReinstallImagefsCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IconBoxBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_general_reinstall_imagefs),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.settings_general_imagefs_summary),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ReinstallButton(onClick = onClick)
        }
    }
}

@Composable
private fun ReinstallButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "reinstallScale",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Accent.copy(alpha = 0.12f))
            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                    onTap = { onClick() },
                )
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_general_reinstall_imagefs),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// Small pill button (mirrors DebugScreen.SmallActionButton)
@Composable
private fun SmallActionButton(label: String, textColor: Color, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "otherBtnScale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222232))
            .border(1.dp, textColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = 11.dp, vertical = 6.dp),
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
