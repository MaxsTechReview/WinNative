package com.winlator.cmod.shared.ui
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SwitchTrackOff = Color(0xFF212838)
private val SwitchBorderOff = Color(0xFF35455C)

@Composable
fun outlinedSwitchColors(
    accentColor: Color,
    textSecondaryColor: Color,
    checkedThumbColor: Color = Color.White,
): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = accentColor,
        uncheckedThumbColor = textSecondaryColor.copy(alpha = 0.92f),
        uncheckedTrackColor = SwitchTrackOff,
        uncheckedBorderColor = SwitchBorderOff,
        checkedBorderColor = Color.Transparent,
        disabledCheckedThumbColor = checkedThumbColor.copy(alpha = 0.7f),
        disabledCheckedTrackColor = accentColor.copy(alpha = 0.5f),
        disabledUncheckedThumbColor = textSecondaryColor.copy(alpha = 0.55f),
        disabledUncheckedTrackColor = SwitchTrackOff.copy(alpha = 0.65f),
        disabledUncheckedBorderColor = SwitchBorderOff.copy(alpha = 0.65f),
        disabledCheckedBorderColor = Color.Transparent,
    )
