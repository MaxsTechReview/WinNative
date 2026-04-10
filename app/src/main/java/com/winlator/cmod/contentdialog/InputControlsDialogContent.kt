package com.winlator.cmod.contentdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.core.AppUtils
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Color scheme matching updated UI
private val BgDark        = Color(0xFF1C2838)
private val SurfaceDark   = Color(0xFF212E3F)
private val CardBorder    = Color(0xFF2C4058)
private val Accent        = Color(0xFF1A9FFF)
private val TextPrimary   = Color(0xFFF5F9FF)
private val TextSecondary = Color(0xFF9CB0C7)
private val DividerColor  = Color(0xFF2C4058)
private val CheckBorder   = Color(0xFF344C68)

data class InputControlsState(
    val profileNames: List<String> = emptyList(),
    val selectedProfileIndex: Int = 0,
    val showTouchscreenControls: Boolean = true,
    val touchscreenTimeout: Boolean = false,
    val touchscreenHaptics: Boolean = false
)

@Composable
fun InputControlsDialogContent(
    state: InputControlsState,
    onProfileSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onShowTouchscreenControlsChange: (Boolean) -> Unit,
    onTouchscreenTimeoutChange: (Boolean) -> Unit,
    onTouchscreenHapticsChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(20.dp))
                .background(BgDark)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 16.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                Text(
                    stringResource(R.string.common_ui_input_controls),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Profile Selection section
            SectionLabel(stringResource(R.string.session_effects_profile_selection))
            Spacer(Modifier.height(10.dp))

            ProfileRow(
                profileNames = state.profileNames,
                selectedIndex = state.selectedProfileIndex,
                onProfileSelected = onProfileSelected,
                onSettingsClick = onSettingsClick
            )

            Spacer(Modifier.height(16.dp))

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )

            Spacer(Modifier.height(16.dp))

            // Touchscreen Overlay section
            SectionLabel(stringResource(R.string.session_drawer_touchscreen_overlay))
            Spacer(Modifier.height(10.dp))

            OptionCheckbox(
                label = stringResource(R.string.session_drawer_show_touchscreen_controls),
                checked = state.showTouchscreenControls,
                onCheckedChange = onShowTouchscreenControlsChange
            )
            Spacer(Modifier.height(4.dp))
            OptionCheckbox(
                label = stringResource(R.string.settings_general_touchscreen_timeout),
                checked = state.touchscreenTimeout,
                onCheckedChange = onTouchscreenTimeoutChange
            )
            Spacer(Modifier.height(4.dp))
            OptionCheckbox(
                label = stringResource(R.string.settings_general_touchscreen_haptics),
                checked = state.touchscreenHaptics,
                onCheckedChange = onTouchscreenHapticsChange
            )

            Spacer(Modifier.height(18.dp))

            // Bottom buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.common_ui_cancel), color = TextSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Accent.copy(alpha = 0.12f))
                        .clickable { onConfirm() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.common_ui_ok), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun ProfileRow(
    profileNames: List<String>,
    selectedIndex: Int,
    onProfileSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val disabledPlaceholder = stringResource(R.string.common_ui_disabled_placeholder)
    val selectedText = profileNames.getOrElse(selectedIndex) { disabledPlaceholder }

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.input_controls_editor_select_profile),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                profileNames.forEachIndexed { index, name ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(CardBorder)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppUtils.hideKeyboard(context as? Activity)
                                onProfileSelected(index)
                                expanded = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            name,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Profile dropdown button
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selectedText,
                color = TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Settings button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = TextPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun OptionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(22.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Accent,
                uncheckedColor = CheckBorder,
                checkmarkColor = Color.White
            )
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp)
    }
}
