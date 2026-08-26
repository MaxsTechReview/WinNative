package com.winlator.cmod.runtime.display

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import com.winlator.cmod.shared.ui.focus.controllerMenuInput
import com.winlator.cmod.shared.ui.focus.controllerFocusBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.winlator.cmod.R
import com.winlator.cmod.runtime.input.controls.Binding
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.nav.DialogPaneNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav as SharedLocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry as SharedPaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem as sharedPaneNavItem
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import com.winlator.cmod.shared.ui.widget.chasingBorder
import kotlin.math.roundToInt

// Touch + gyroscope pane composables, split out of XServerDrawerMenu.kt (behavior-identical).

@Composable
internal fun TouchPaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    onClose: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size((30f * paneScale).dp)
                            .clip(RoundedCornerShape((8f * paneScale).dp))
                            .paneNavItem(cornerRadius = (8f * paneScale).dp, onActivate = onClose)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClose,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.common_ui_back),
                        tint = DrawerTextSecondary,
                        modifier = Modifier.size((20f * paneScale).dp),
                    )
                }
                Box(Modifier.fillMaxWidth().paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = { listener.onActionSelected(R.id.main_menu_disable_mouse) })) {
                    DrawerBooleanRow(
                        title = stringResource(R.string.session_drawer_mouse_input),
                        checked = state.mouseEnabled,
                        onCheckedChange = { listener.onActionSelected(R.id.main_menu_disable_mouse) },
                    )
                }
                Box(Modifier.fillMaxWidth().paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = { listener.onActionSelected(R.id.main_menu_relative_mouse_movement) })) {
                    DrawerBooleanRow(
                        title = stringResource(R.string.session_drawer_relative_mouse_movement),
                        checked = state.relativeMouseEnabled,
                        onCheckedChange = { listener.onActionSelected(R.id.main_menu_relative_mouse_movement) },
                    )
                }
                Box(Modifier.fillMaxWidth().paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = { listener.onScreenTouchModeChanged(0) })) {
                    DrawerBooleanRow(
                        title = stringResource(R.string.session_drawer_touch_trackpad),
                        checked = state.screenTouchMode == 0 && !state.rtsGesturesEnabled,
                        onCheckedChange = { if (it) listener.onScreenTouchModeChanged(0) },
                    )
                }
                Box(Modifier.fillMaxWidth().paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = { listener.onScreenTouchModeChanged(1) })) {
                    DrawerBooleanRow(
                        title = stringResource(R.string.session_drawer_touch_touchscreen),
                        checked = state.screenTouchMode == 1 && !state.rtsGesturesEnabled,
                        onCheckedChange = { listener.onScreenTouchModeChanged(if (it) 1 else 0) },
                    )
                }
                Box(Modifier.fillMaxWidth().paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = { listener.onScreenTouchModeChanged(2) })) {
                    DrawerBooleanRow(
                        title = stringResource(R.string.session_drawer_touch_map_right_stick),
                        checked = state.screenTouchMode == 2 && !state.rtsGesturesEnabled,
                        onCheckedChange = { listener.onScreenTouchModeChanged(if (it) 2 else 0) },
                    )
                }
                Box(Modifier.fillMaxWidth().paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = { listener.onRtsGesturesToggled(!state.rtsGesturesEnabled) })) {
                    DrawerBooleanRow(
                        title = stringResource(R.string.session_drawer_rts_gestures),
                        checked = state.rtsGesturesEnabled,
                        onCheckedChange = { listener.onRtsGesturesToggled(it) },
                    )
                }
                if (state.rtsGesturesEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                        PaneSectionLabel(stringResource(R.string.session_gesture_profile_section))
                        InputControlsProfileSelector(
                            profileNames = state.gestureProfileNames,
                            selectedIndex = state.gestureSelectedProfileIndex,
                            onProfileSelected = listener::onGestureProfileSelected,
                            onEditClick = listener::onRtsGesturesEditClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GyroscopePaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    var calibrateExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
            NavEnableRow(
                title = stringResource(R.string.session_gyroscope_title),
                checked = state.gyroscopeEnabled,
                onCheckedChange = listener::onGyroscopeEnabledChanged,
            )

            if (state.gyroscopeEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_gyroscope_mode))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
                    ) {
                        listOf(
                            stringResource(R.string.session_gyroscope_hold),
                            stringResource(R.string.session_gyroscope_toggle),
                        ).forEachIndexed { index, label ->
                            HUDToggleChip(
                                label = label,
                                checked = state.gyroscopeModeIndex == index,
                                onClick = { listener.onGyroscopeModeSelected(index) },
                                modifier = Modifier.weight(1f).paneNavItem(
                                    cornerRadius = (16f * paneScale).dp,
                                    onActivate = { listener.onGyroscopeModeSelected(index) },
                                ),
                            )
                        }
                    }
                }

                NavBooleanRow(
                    title = stringResource(R.string.session_gyroscope_orientation_mode),
                    checked = state.gyroOrientationEnabled,
                    onCheckedChange = listener::onGyroOrientationModeChanged,
                )

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_gyroscope_activator_button))
                    if (state.gyroMouseEnabled) {
                        val bindings = remember { Binding.activatorBindingValues() }
                        GyroscopeActivatorDropdown(
                            currentLabel = state.gyroscopeActivatorLabel,
                            options = remember(bindings) { bindings.map { it.toString() } },
                            onSelectedIndex = { listener.onGyroscopeActivatorBindingSelected(bindings[it].name) },
                        )
                    } else {
                        val names = stringArrayResource(R.array.button_options)
                        val keycodes = integerArrayResource(R.array.button_keycodes)
                        GyroscopeActivatorDropdown(
                            currentLabel = state.gyroscopeActivatorLabel,
                            options = remember(names) { names.toList() },
                            onSelectedIndex = { listener.onGyroscopeActivatorSelected(keycodes[it]) },
                        )
                    }
                }

                NavBooleanRow(
                    title = stringResource(R.string.session_gyroscope_enable_right_stick),
                    checked = state.rightStickGyroEnabled,
                    onCheckedChange = listener::onRightStickGyroChanged,
                )

                NavBooleanRow(
                    title = stringResource(R.string.session_gyroscope_experimental_mouse_movement),
                    checked = state.gyroMouseEnabled,
                    onCheckedChange = listener::onGyroMouseEnabledChanged,
                )

                if (state.gyroMouseEnabled) {
                    NavSliderRow(
                        label = stringResource(R.string.session_gyroscope_mouse_scale),
                        valueText = "${state.gyroMouseScale.toInt()}%",
                        value = state.gyroMouseScale,
                        valueRange = 0f..200f,
                        steps = 199,
                        onValueChange = { listener.onGyroMouseScaleChanged(it.roundToInt().toFloat()) },
                    )
                }

                ExpandableSection(
                    title = stringResource(R.string.session_drawer_calibrate_advanced),
                    expanded = calibrateExpanded,
                    onToggle = { calibrateExpanded = !calibrateExpanded },
                ) {
                    NavSliderRow(
                        label = stringResource(R.string.session_gyroscope_x_sensitivity),
                        valueText = "${(state.gyroXSensitivity * 100).roundToInt()}%",
                        value = state.gyroXSensitivity,
                        valueRange = 0.01f..3f,
                        steps = 0,
                        onValueChange = { listener.onGyroXSensitivityChanged(it) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_gyroscope_y_sensitivity),
                        valueText = "${(state.gyroYSensitivity * 100).roundToInt()}%",
                        value = state.gyroYSensitivity,
                        valueRange = 0.01f..3f,
                        steps = 0,
                        onValueChange = { listener.onGyroYSensitivityChanged(it) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_gyroscope_smoothing),
                        valueText = "${(state.gyroSmoothing * 100).toInt()}%",
                        value = state.gyroSmoothing,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { listener.onGyroSmoothingChanged(it) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_gyroscope_deadzone),
                        valueText = "${(state.gyroDeadzone * 100).toInt()}%",
                        value = state.gyroDeadzone,
                        valueRange = 0f..1f,
                        steps = 99,
                        onValueChange = { listener.onGyroDeadzoneChanged(it) },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
                    ) {
                        HUDToggleChip(
                            label = stringResource(R.string.session_gyroscope_invert_x),
                            checked = state.invertGyroX,
                            onClick = { listener.onInvertGyroXChanged(!state.invertGyroX) },
                            modifier = Modifier.weight(1f).paneNavItem(cornerRadius = (16f * paneScale).dp, onActivate = { listener.onInvertGyroXChanged(!state.invertGyroX) }),
                        )
                        HUDToggleChip(
                            label = stringResource(R.string.session_gyroscope_invert_y),
                            checked = state.invertGyroY,
                            onClick = { listener.onInvertGyroYChanged(!state.invertGyroY) },
                            modifier = Modifier.weight(1f).paneNavItem(cornerRadius = (16f * paneScale).dp, onActivate = { listener.onInvertGyroYChanged(!state.invertGyroY) }),
                        )
                    }

                    Box(
                        Modifier.paneNavItem(
                            cornerRadius = 10.dp,
                            onActivate = { listener.onActionSelected(R.id.main_menu_gyroscope_reset) },
                        ),
                    ) {
                        WinNativeDialogButton(
                            label = stringResource(R.string.session_gyroscope_reset_stick),
                            textColor = DrawerAccent,
                            backgroundColor = DrawerAccent.copy(alpha = 0.12f),
                            borderColor = DrawerAccent.copy(alpha = 0.3f),
                            onClick = { listener.onActionSelected(R.id.main_menu_gyroscope_reset) },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun GyroscopeActivatorDropdown(
    currentLabel: String,
    options: List<String>,
    onSelectedIndex: (Int) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    var expanded by remember { mutableStateOf(false) }
    val parentNav = LocalPaneNav.current
    val optionRegistry = remember { PaneNavRegistry() }
    LaunchedEffect(expanded) {
        if (expanded) {
            optionRegistry.reset()
            optionRegistry.controllerActive = true
            parentNav?.overlay = optionRegistry
            parentNav?.overlayClose = { expanded = false }
        } else if (parentNav?.overlay === optionRegistry) {
            parentNav.overlay = null
            parentNav.overlayClose = null
        }
    }

    val cornerRadius = (14f * paneScale).dp
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(0),
        label = "gyroActivatorDropdownBg",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(bgColor)
                    .border(1.dp, RestingCardBorder, shape)
                    .paneNavItem(cornerRadius = cornerRadius, onActivate = { expanded = true })
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { expanded = true }
                    .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentLabel,
                color = DrawerTextPrimary,
                fontSize = (14f * paneScale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = DrawerTextSecondary,
                modifier = Modifier.size((22f * paneScale).dp),
            )
        }

        InputControlsOptionsPopup(
            expanded = expanded,
            options = options,
            selectedIndex = options.indexOfFirst { it == currentLabel }.coerceAtLeast(0),
            onSelected = { index -> onSelectedIndex(index) },
            onDismiss = { expanded = false },
            optionRegistry = optionRegistry,
        )
    }
}
