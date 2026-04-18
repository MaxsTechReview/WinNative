package com.winlator.cmod.runtime.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary

private val InputControlsSecondarySurface = Color(0xFF202635)
private val InputControlsSecondaryOutline = Color(0xFF313A4D)
private val InputControlsDivider = Color(0xFF30384A)
private val InputControlsDividerStrong = Color(0xFF39445A)
private val InputControlsMenuItemPressed = Color(0xFF2B3243)

data class InputControlsState(
    val profileNames: List<String> = emptyList(),
    val selectedProfileIndex: Int = 0,
    val showTouchscreenControls: Boolean = false,
    val touchscreenHaptics: Boolean = false,
    val gamepadVibration: Boolean = true,
)

@Composable
fun InputControlsDialogContent(
    state: InputControlsState,
    onProfileSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onShowTouchscreenControlsChange: (Boolean) -> Unit,
    onTouchscreenHapticsChange: (Boolean) -> Unit,
    onGamepadVibrationChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val configuration = LocalConfiguration.current
        val compactLayout = maxWidth < 380.dp
        val dialogWidthFraction =
            when {
                maxWidth < 420.dp -> 0.98f
                maxWidth < 720.dp -> 0.9f
                else -> 0.56f
            }
        val dialogMaxWidth =
            when {
                compactLayout -> 344.dp
                maxWidth < 720.dp -> 440.dp
                else -> 520.dp
            }
        val dialogMaxHeight = (configuration.screenHeightDp.dp - if (compactLayout) 20.dp else 32.dp)
        val contentHorizontalPadding = if (compactLayout) 11.dp else 14.dp
        val contentVerticalPadding = if (compactLayout) 9.dp else 10.dp

        Column(
            modifier =
                Modifier
                    .fillMaxWidth(dialogWidthFraction)
                    .widthIn(max = dialogMaxWidth)
                    .heightIn(max = dialogMaxHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WinNativeSurface)
                    .border(1.dp, WinNativeOutline, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = contentHorizontalPadding,
                            end = contentHorizontalPadding,
                            top = contentVerticalPadding,
                            bottom = 6.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 7.dp else 8.dp),
            ) {
                SectionCard {
                    SectionLabel(stringResource(R.string.session_effects_profile_selection))
                    Spacer(Modifier.height(5.dp))
                    ProfileRow(
                        profileNames = state.profileNames,
                        selectedIndex = state.selectedProfileIndex,
                        onProfileSelected = onProfileSelected,
                        onSettingsClick = onSettingsClick,
                    )
                }

                SectionCard {
                    SectionLabel(stringResource(R.string.session_drawer_touchscreen_overlay))
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionCheckbox(
                            label = stringResource(R.string.session_drawer_show_touchscreen_controls),
                            checked = state.showTouchscreenControls,
                            onCheckedChange = onShowTouchscreenControlsChange,
                        )
                        OptionCheckbox(
                            label = stringResource(R.string.settings_general_touchscreen_haptics),
                            checked = state.touchscreenHaptics,
                            onCheckedChange = onTouchscreenHapticsChange,
                        )
                        OptionCheckbox(
                            label = stringResource(R.string.session_gamepad_enable_vibration),
                            checked = state.gamepadVibration,
                            onCheckedChange = onGamepadVibrationChange,
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(InputControlsDividerStrong),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentHorizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(WinNativePanel)
                                .border(1.dp, WinNativeOutline, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = WinNativeAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.common_ui_input_controls),
                        color = WinNativeTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FooterButton(
                        label = stringResource(R.string.common_ui_cancel),
                        textColor = WinNativeTextPrimary,
                        onClick = onCancel,
                        backgroundColor = InputControlsSecondarySurface,
                        borderColor = InputControlsSecondaryOutline,
                    )
                    FooterButton(
                        label = stringResource(R.string.common_ui_ok),
                        textColor = WinNativeAccent,
                        onClick = onConfirm,
                        backgroundColor = WinNativeAccent.copy(alpha = 0.14f),
                        borderColor = WinNativeAccent.copy(alpha = 0.34f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = WinNativeTextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WinNativePanel)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun FooterButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    backgroundColor: Color,
    borderColor: Color,
) {
    Box(
        modifier =
            Modifier
                .height(34.dp)
                .widthIn(min = 74.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(backgroundColor)
                .border(1.dp, borderColor, RoundedCornerShape(11.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileRow(
    profileNames: List<String>,
    selectedIndex: Int,
    onProfileSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val profileInteractionSource = remember { MutableInteractionSource() }
    val configuration = LocalConfiguration.current
    val disabledPlaceholder = stringResource(R.string.common_ui_disabled_placeholder)
    val selectedText = profileNames.getOrElse(selectedIndex) { disabledPlaceholder }
    val menuMaxHeight = (configuration.screenHeightDp.dp * 0.58f).coerceAtMost(400.dp)
    val menuWidth =
        when {
            configuration.screenWidthDp < 420 -> 0.72f
            else -> 0.48f
        }

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(menuWidth)
                        .widthIn(min = 220.dp, max = 420.dp)
                        .heightIn(max = menuMaxHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WinNativeSurface)
                        .border(1.dp, WinNativeOutline, RoundedCornerShape(16.dp)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(WinNativePanel)
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = stringResource(R.string.input_controls_editor_select_profile),
                        color = WinNativeTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(InputControlsDividerStrong),
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                ) {
                    profileNames.forEachIndexed { index, name ->
                        if (index > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .height(1.dp)
                                        .background(InputControlsDivider),
                            )
                        }
                        ProfilePopupItem(
                            label = name,
                            selected = index == selectedIndex,
                            onClick = {
                                onProfileSelected(index)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputControlsSecondarySurface)
                    .border(1.dp, InputControlsSecondaryOutline, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = profileInteractionSource,
                        indication = null,
                    ) { expanded = true }
                    .height(42.dp)
                    .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedText,
                color = WinNativeTextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = WinNativeTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputControlsSecondarySurface)
                    .border(1.dp, InputControlsSecondaryOutline, RoundedCornerShape(10.dp))
                    .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.common_ui_settings),
                tint = WinNativeTextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ProfilePopupItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundColor =
        when {
            pressed -> InputControlsMenuItemPressed
            else -> Color.Transparent
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(backgroundColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).defaultMinSize(minHeight = 42.dp)
                .padding(start = 20.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) WinNativeAccent else WinNativeTextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OptionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(InputControlsSecondarySurface)
                .border(1.dp, InputControlsSecondaryOutline, RoundedCornerShape(11.dp))
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                ) { onCheckedChange(!checked) }
                .height(44.dp)
                .padding(horizontal = 9.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(15.dp),
            colors =
                CheckboxDefaults.colors(
                    checkedColor = WinNativeAccent,
                    uncheckedColor = InputControlsSecondaryOutline,
                    checkmarkColor = Color.White,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = WinNativeTextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
