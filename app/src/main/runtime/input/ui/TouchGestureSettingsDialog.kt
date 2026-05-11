package com.winlator.cmod.runtime.input.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.winlator.cmod.runtime.input.controls.Binding
import com.winlator.cmod.shared.theme.*

@Composable
fun TouchGestureSettingsDialog(
    config: TouchGestureConfig,
    onConfigChange: (TouchGestureConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val dialogWidth = (configuration.screenWidthDp.dp * 0.95f).coerceAtMost(420.dp)
    val dialogHeight = (configuration.screenHeightDp.dp * 0.85f).coerceAtMost(700.dp)

    var currentConfig by remember { mutableStateOf(config.copy()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = dialogHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(WinNativeSurface)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(16.dp))
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WinNativePanel)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.touch_gestures),
                    color = WinNativeTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: MOVEMENT
                GestureSectionLabel("Movement")
                GestureToggleSettingRow(
                    label = stringResource(R.string.touch_gestures_pinch),
                    checked = currentConfig.pinchEnabled,
                    onCheckedChange = { currentConfig = currentConfig.copy(pinchEnabled = it) },
                    selectedOption = currentConfig.pinchAction.name,
                    options = TouchGestureConfig.PinchAction.entries.map { it.name to getPinchActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(pinchAction = TouchGestureConfig.PinchAction.valueOf(it)) }
                )

                // Section: TAPS
                GestureSectionLabel("Taps")
                BindingSettingRow(
                    label = "One-Finger Tap",
                    selectedBinding = currentConfig.oneFingerTapAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerTapAction = it) }
                )
                BindingSettingRow(
                    label = "One-Finger Double Tap",
                    selectedBinding = currentConfig.oneFingerDoubleTapAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerDoubleTapAction = it) }
                )
                BindingSettingRow(
                    label = "Two-Finger Tap",
                    selectedBinding = currentConfig.twoFingerTapAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(twoFingerTapAction = it) }
                )
                BindingSettingRow(
                    label = stringResource(R.string.touch_gestures_three_finger_tap),
                    selectedBinding = currentConfig.threeFingerTapAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(threeFingerTapAction = it) }
                )
                BindingSettingRow(
                    label = stringResource(R.string.touch_gestures_four_finger_tap),
                    selectedBinding = currentConfig.fourFingerTapAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(fourFingerTapAction = it) }
                )

                // Section: HOLDS (LONG PRESS)
                GestureSectionLabel("Holds (Long Press)")
                
                GestureSubsectionLabel("One-Finger Hold")
                BindingSettingRow(
                    label = "Action",
                    selectedBinding = currentConfig.oneFingerLongPressAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerLongPressAction = it) }
                )
                DurationSettingRow(
                    label = "Duration",
                    value = currentConfig.oneFingerLongPressDuration,
                    onValueChange = { currentConfig = currentConfig.copy(oneFingerLongPressDuration = it) }
                )

                GestureSubsectionLabel("Two-Finger Hold")
                BindingSettingRow(
                    label = "Action",
                    selectedBinding = currentConfig.twoFingerLongPressAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(twoFingerLongPressAction = it) }
                )
                DurationSettingRow(
                    label = "Duration",
                    value = currentConfig.twoFingerLongPressDuration,
                    onValueChange = { currentConfig = currentConfig.copy(twoFingerLongPressDuration = it) }
                )

                GestureSubsectionLabel("Three-Finger Hold")
                BindingSettingRow(
                    label = "Action",
                    selectedBinding = currentConfig.threeFingerLongPressAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(threeFingerLongPressAction = it) }
                )
                DurationSettingRow(
                    label = "Duration",
                    value = currentConfig.threeFingerLongPressDuration,
                    onValueChange = { currentConfig = currentConfig.copy(threeFingerLongPressDuration = it) }
                )

                GestureSubsectionLabel("Four-Finger Hold")
                BindingSettingRow(
                    label = "Action",
                    selectedBinding = currentConfig.fourFingerLongPressAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(fourFingerLongPressAction = it) }
                )
                DurationSettingRow(
                    label = "Duration",
                    value = currentConfig.fourFingerLongPressDuration,
                    onValueChange = { currentConfig = currentConfig.copy(fourFingerLongPressDuration = it) }
                )

                // Section: SWIPES
                GestureSectionLabel("Swipes")

                GestureSubsectionLabel("One-Finger Swipes/Drag")
                GestureSettingRow(
                    label = "One-Finger Drag Action",
                    selectedOption = currentConfig.oneFingerDragAction.name,
                    options = TouchGestureConfig.DragAction.entries.map { it.name to getDragActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(oneFingerDragAction = TouchGestureConfig.DragAction.valueOf(it)) }
                )
                BindingSettingRow(
                    label = "Swipe Up",
                    selectedBinding = currentConfig.oneFingerSwipeUpAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerSwipeUpAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Down",
                    selectedBinding = currentConfig.oneFingerSwipeDownAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerSwipeDownAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Left",
                    selectedBinding = currentConfig.oneFingerSwipeLeftAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerSwipeLeftAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Right",
                    selectedBinding = currentConfig.oneFingerSwipeRightAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(oneFingerSwipeRightAction = it) }
                )

                GestureSubsectionLabel("Two-Finger Swipe/Drag")
                GestureSettingRow(
                    label = "Action",
                    selectedOption = currentConfig.twoFingerSwipeAction.name,
                    options = TouchGestureConfig.PanAction.entries.map { it.name to getPanActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(twoFingerSwipeAction = TouchGestureConfig.PanAction.valueOf(it)) }
                )

                GestureSubsectionLabel("Three-Finger Swipes")
                BindingSettingRow(
                    label = "Swipe Up",
                    selectedBinding = currentConfig.threeFingerSwipeUpAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(threeFingerSwipeUpAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Down",
                    selectedBinding = currentConfig.threeFingerSwipeDownAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(threeFingerSwipeDownAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Left",
                    selectedBinding = currentConfig.threeFingerSwipeLeftAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(threeFingerSwipeLeftAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Right",
                    selectedBinding = currentConfig.threeFingerSwipeRightAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(threeFingerSwipeRightAction = it) }
                )

                GestureSubsectionLabel("Four-Finger Swipes")
                BindingSettingRow(
                    label = "Swipe Up",
                    selectedBinding = currentConfig.fourFingerSwipeUpAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(fourFingerSwipeUpAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Down",
                    selectedBinding = currentConfig.fourFingerSwipeDownAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(fourFingerSwipeDownAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Left",
                    selectedBinding = currentConfig.fourFingerSwipeLeftAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(fourFingerSwipeLeftAction = it) }
                )
                BindingSettingRow(
                    label = "Swipe Right",
                    selectedBinding = currentConfig.fourFingerSwipeRightAction,
                    onBindingSelected = { currentConfig = currentConfig.copy(fourFingerSwipeRightAction = it) }
                )
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f))
                ) {
                    Text(stringResource(R.string.common_ui_cancel), color = WinNativeTextPrimary)
                }
                Button(
                    onClick = {
                        // FIX: Preserve the enabled state from the original config
                        val finalConfig = currentConfig.copy(enabled = config.enabled)
                        onConfigChange(finalConfig)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = WinNativeAccent)
                ) {
                    Text(stringResource(R.string.common_ui_ok), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GestureSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        color = WinNativeAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun GestureSubsectionLabel(label: String) {
    Text(
        text = label,
        color = WinNativeTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun BindingSettingRow(
    label: String,
    selectedBinding: Binding,
    onBindingSelected: (Binding) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = WinNativeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WinNativePanel)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedBinding.toString(),
                    color = WinNativeTextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = WinNativeTextSecondary, modifier = Modifier.size(16.dp))
            }

            if (expanded) {
                BindingPickerDialog(
                    selectedBinding = selectedBinding,
                    onBindingSelected = {
                        onBindingSelected(it)
                        expanded = false
                    },
                    onDismiss = { expanded = false }
                )
            }
        }
    }
}

@Composable
private fun BindingPickerDialog(
    selectedBinding: Binding,
    onBindingSelected: (Binding) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(16.dp))
                .background(WinNativeSurface)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Select Binding",
                color = WinNativeTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val bindings = remember { Binding.values() }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bindings.forEach { binding ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (binding == selectedBinding) WinNativeAccent.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onBindingSelected(binding) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = binding.toString(),
                            color = if (binding == selectedBinding) WinNativeAccent else WinNativeTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (binding == selectedBinding) {
                            Icon(Icons.Filled.Check, null, tint = WinNativeAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WinNativePanel)
            ) {
                Text("Close", color = WinNativeTextPrimary)
            }
        }
    }
}

@Composable
private fun getBindingActionLabel(binding: Binding): String {
    return when (binding) {
        Binding.NONE -> stringResource(R.string.touch_gestures_action_none)
        Binding.MOUSE_LEFT_BUTTON -> "Left Click"
        Binding.MOUSE_RIGHT_BUTTON -> "Right Click"
        Binding.MOUSE_MIDDLE_BUTTON -> "Middle Click"
        else -> binding.toString()
    }
}

@Composable
private fun GestureSettingRow(
    label: String,
    selectedOption: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = WinNativeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WinNativePanel)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = options.find { it.first == selectedOption }?.second ?: selectedOption,
                    color = WinNativeTextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = WinNativeTextSecondary, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(WinNativeSurface).border(1.dp, WinNativeOutline)
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = WinNativeTextPrimary) },
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureToggleSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    selectedOption: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = WinNativeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = WinNativeAccent)
            )
        }
        if (checked) {
            Spacer(Modifier.height(4.dp))
            GestureSettingRow("", selectedOption, options, onOptionSelected)
        }
    }
}

@Composable
private fun DurationSettingRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = WinNativeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(text = "${value}ms", color = WinNativeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 200f..1000f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = WinNativeAccent,
                activeTrackColor = WinNativeAccent,
                inactiveTrackColor = WinNativeAccent.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun getDragActionLabel(action: TouchGestureConfig.DragAction): String {
    return when (action) {
        TouchGestureConfig.DragAction.MOUSE_MOVE -> "Mouse Movement Only"
        TouchGestureConfig.DragAction.LEFT_CLICK -> "Left-Click Drag (Selection)"
    }
}

@Composable
private fun getPanActionLabel(action: TouchGestureConfig.PanAction): String {
    return when (action) {
        TouchGestureConfig.PanAction.NONE -> stringResource(R.string.touch_gestures_action_none)
        TouchGestureConfig.PanAction.MIDDLE_CLICK -> stringResource(R.string.touch_gestures_action_middle_click)
        TouchGestureConfig.PanAction.WASD -> stringResource(R.string.touch_gestures_action_wasd)
        TouchGestureConfig.PanAction.ARROW_KEYS -> stringResource(R.string.touch_gestures_action_arrow_keys)
        TouchGestureConfig.PanAction.SCROLL -> stringResource(R.string.touch_gestures_action_scroll)
    }
}

@Composable
private fun getPinchActionLabel(action: TouchGestureConfig.PinchAction): String {
    return when (action) {
        TouchGestureConfig.PinchAction.NONE -> stringResource(R.string.touch_gestures_action_none)
        TouchGestureConfig.PinchAction.SCROLL_WHEEL -> stringResource(R.string.touch_gestures_action_scroll_wheel)
        TouchGestureConfig.PinchAction.ZOOM_KEYS -> stringResource(R.string.touch_gestures_action_zoom_keys)
    }
}
