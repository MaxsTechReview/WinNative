package com.winlator.cmod.contentdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.winlator.cmod.core.AppUtils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import kotlin.math.roundToInt

// Color scheme matching updated UI
private val BgDark        = Color(0xFF1C2838)
private val SurfaceDark   = Color(0xFF212E3F)
private val CardBorder    = Color(0xFF2C4058)
private val Accent        = Color(0xFF1A9FFF)
private val TextPrimary   = Color(0xFFF5F9FF)
private val TextSecondary = Color(0xFF9CB0C7)
private val DividerColor  = Color(0xFF2C4058)
private val CheckBorder   = Color(0xFF344C68)
private val TrackInactive = Color(0xFF2C4058)

data class ScreenEffectState(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val gamma: Float = 1f,
    val enableFXAA: Boolean = false,
    val enableCRT: Boolean = false,
    val enableToon: Boolean = false,
    val enableNTSC: Boolean = false,
    val profileNames: List<String> = emptyList(),
    val selectedProfileIndex: Int = 0
)

@Composable
fun ScreenEffectDialogContent(
    state: ScreenEffectState,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onGammaChange: (Float) -> Unit,
    onFXAAChange: (Boolean) -> Unit,
    onCRTChange: (Boolean) -> Unit,
    onToonChange: (Boolean) -> Unit,
    onNTSCChange: (Boolean) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onAddProfile: () -> Unit,
    onRemoveProfile: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(20.dp))
            .background(BgDark)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 16.dp)
    ) {
        // Color Adjustment sliders
        SectionLabel(stringResource(R.string.session_effects_color_adjustment))
        Spacer(Modifier.height(10.dp))
        SliderField(stringResource(R.string.session_effects_brightness), state.brightness, -50f, 50f, 1f, onBrightnessChange)
        Spacer(Modifier.height(6.dp))
        SliderField(stringResource(R.string.session_effects_contrast), state.contrast, -100f, 100f, 1f, onContrastChange)
        Spacer(Modifier.height(6.dp))
        SliderField(stringResource(R.string.session_effects_gamma), state.gamma, 0.5f, 3f, 0.01f, onGammaChange, decimalPlaces = 2)

        Spacer(Modifier.height(16.dp))

        // Profile + Post-Processing on the same row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Profile selector
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.common_ui_profile))
                Spacer(Modifier.height(8.dp))
                ProfileSelector(
                    profileNames = state.profileNames,
                    selectedIndex = state.selectedProfileIndex,
                    onProfileSelected = onProfileSelected,
                    onAdd = onAddProfile,
                    onRemove = onRemoveProfile,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Post-Processing toggles
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.session_effects_post_processing))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EffectToggle("FXAA", state.enableFXAA, onFXAAChange)
                    EffectToggle("CRT", state.enableCRT, onCRTChange)
                    EffectToggle("Toon", state.enableToon, onToonChange)
                    EffectToggle("NTSC", state.enableNTSC, onNTSCChange)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Bottom buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.common_ui_reset), color = TextSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
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
    } // Box
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
private fun SliderField(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onValueChange: (Float) -> Unit,
    decimalPlaces: Int = 0
) {
    val displayValue = if (decimalPlaces == 0) {
        value.roundToInt().toString()
    } else {
        String.format("%.${decimalPlaces}f", value)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(displayValue, color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = { newVal ->
                val snapped = if (step >= 1f) {
                    (newVal / step).roundToInt() * step
                } else {
                    ((newVal / step).roundToInt() * step * 100).roundToInt() / 100f
                }
                onValueChange(snapped.coerceIn(min, max))
            },
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Accent,
                inactiveTrackColor = TrackInactive
            )
        )
    }
}

@Composable
private fun ProfileSelector(
    profileNames: List<String>,
    selectedIndex: Int,
    onProfileSelected: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val defaultProfileText = stringResource(R.string.session_effects_default_profile)
    val selectedText = profileNames.getOrElse(selectedIndex) { "-- $defaultProfileText --" }

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
        modifier = modifier
    ) {
        // Profile button
        Row(
            modifier = Modifier
                .weight(0.6f)
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

        Spacer(Modifier.width(10.dp))

        // Add/Remove buttons
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add profile", tint = TextPrimary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove profile", tint = TextPrimary, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun EffectToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 4.dp)
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
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextPrimary, fontSize = 13.sp)
    }
}
