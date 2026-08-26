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

// Screen-effects pane composable, split out of XServerDrawerMenu.kt (behavior-identical).

@Composable
internal fun ScreenEffectsPaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
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
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.shortcuts_graphics_sgsr_full_title))
                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_upscaler_fsr),
                        checked = state.sgsrEnabled,
                        onCheckedChange = listener::onSGSREnabledChanged,
                    )

                    AnimatedVisibility(
                        visible = state.sgsrEnabled,
                        enter =
                            expandVertically(
                                animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.Top,
                            ) + fadeIn(animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing)),
                        exit =
                            shrinkVertically(
                                animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                            NavSliderRow(
                                label = stringResource(R.string.session_drawer_sgsr_edge_sharpness),
                                valueText = "${state.sgsrSharpness}%",
                                value = state.sgsrSharpness.toFloat(),
                                valueRange = 0f..100f,
                                steps = 99,
                                onValueChange = { listener.onSGSRSharpnessChanged(it.roundToInt().coerceIn(0, 100)) },
                            )
                        }
                    }
                }

                ThinDivider()

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_drawer_color_profile))

                    val profiles =
                        listOf(
                            0 to stringResource(R.string.session_drawer_color_profile_disabled),
                            1 to stringResource(R.string.session_drawer_color_profile_hdr),
                            2 to stringResource(R.string.session_drawer_color_profile_natural),
                            4 to stringResource(R.string.session_drawer_color_profile_toon),
                            3 to stringResource(R.string.session_drawer_color_profile_crt),
                            5 to stringResource(R.string.session_drawer_color_profile_ntsc),
                            6 to stringResource(R.string.session_drawer_color_profile_ntsc2),
                        )

                    ChipFlow {
                        profiles.forEach { (id, label) ->
                            HUDToggleChip(
                                label = label,
                                checked = state.colorProfile == id,
                                onClick = { listener.onColorProfileSelected(id) },
                                modifier = Modifier.paneNavItem(
                                    cornerRadius = (16f * paneScale).dp,
                                    onActivate = { listener.onColorProfileSelected(id) },
                                ),
                            )
                        }
                    }

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_vivid),
                        checked = state.vividEnabled,
                        onCheckedChange = listener::onVividEnabledChanged,
                    )

                    if (state.vividEnabled) {
                        NavSliderRow(
                            label = stringResource(R.string.session_drawer_vivid_strength),
                            valueText = "${state.vividStrength}%",
                            value = state.vividStrength.toFloat(),
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { listener.onVividStrengthChanged(it.roundToInt()) },
                        )
                    }

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_sharpen),
                        checked = state.sharpenEnabled,
                        onCheckedChange = listener::onSharpenEnabledChanged,
                    )

                    if (state.sharpenEnabled) {
                        NavSliderRow(
                            label = stringResource(R.string.session_drawer_strength),
                            valueText = "${state.sharpenStrength}%",
                            value = state.sharpenStrength.toFloat(),
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { listener.onSharpenStrengthChanged(it.roundToInt().coerceIn(0, 100)) },
                        )
                    }

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_scanlines),
                        checked = state.scanlinesEnabled,
                        onCheckedChange = listener::onScanlinesEnabledChanged,
                    )

                    if (state.scanlinesEnabled) {
                        NavSliderRow(
                            label = stringResource(R.string.session_drawer_intensity),
                            valueText = "${state.scanlinesIntensity}%",
                            value = state.scanlinesIntensity.toFloat(),
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { listener.onScanlinesIntensityChanged(it.roundToInt().coerceIn(0, 100)) },
                        )
                    }

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_pixelate),
                        checked = state.pixelateEnabled,
                        onCheckedChange = listener::onPixelateEnabledChanged,
                    )

                    if (state.pixelateEnabled) {
                        NavSliderRow(
                            label = stringResource(R.string.session_drawer_block_size),
                            valueText = "${state.pixelateBlock}px",
                            value = state.pixelateBlock.toFloat(),
                            valueRange = 2f..14f,
                            steps = 11,
                            onValueChange = { listener.onPixelateBlockChanged(it.roundToInt().coerceIn(2, 14)) },
                        )
                    }

                    NavSliderRow(
                        label = stringResource(R.string.session_drawer_brightness),
                        valueText = "${state.brightness}",
                        value = state.brightness.toFloat(),
                        valueRange = -100f..100f,
                        steps = 39,
                        onValueChange = { listener.onBrightnessChanged(it.roundToInt().coerceIn(-100, 100)) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_drawer_contrast),
                        valueText = "${state.contrast}",
                        value = state.contrast.toFloat(),
                        valueRange = -100f..100f,
                        steps = 39,
                        onValueChange = { listener.onContrastChanged(it.roundToInt().coerceIn(-100, 100)) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_drawer_gamma),
                        valueText = String.format("%.2fx", state.gammaPercent / 100f),
                        value = state.gammaPercent.toFloat(),
                        valueRange = 50f..250f,
                        steps = 19,
                        onValueChange = { listener.onGammaChanged(it.roundToInt().coerceIn(50, 250)) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_drawer_saturation),
                        valueText = "${state.saturation}%",
                        value = state.saturation.toFloat(),
                        valueRange = 0f..200f,
                        steps = 39,
                        onValueChange = { listener.onSaturationChanged(it.roundToInt().coerceIn(0, 200)) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_drawer_temperature),
                        valueText = "${state.temperature}",
                        value = state.temperature.toFloat(),
                        valueRange = -100f..100f,
                        steps = 39,
                        onValueChange = { listener.onTemperatureChanged(it.roundToInt().coerceIn(-100, 100)) },
                    )

                    NavSliderRow(
                        label = stringResource(R.string.session_drawer_tint),
                        valueText = "${state.tint}",
                        value = state.tint.toFloat(),
                        valueRange = -100f..100f,
                        steps = 39,
                        onValueChange = { listener.onTintChanged(it.roundToInt().coerceIn(-100, 100)) },
                    )

                    Box(
                        Modifier.fillMaxWidth().paneNavItem(
                            cornerRadius = (12f * paneScale).dp,
                            onActivate = { listener.onResetEffects() },
                        ),
                    ) {
                        DrawerResetRow(
                            label = stringResource(R.string.session_drawer_reset_effects),
                            onClick = listener::onResetEffects,
                        )
                    }
                }

                ThinDivider()

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_drawer_color_blind))

                    Row(horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                        HUDToggleChip(
                            label = stringResource(R.string.session_drawer_color_blind_protan),
                            checked = state.colorBlind == 1,
                            onClick = { listener.onColorBlindSelected(if (state.colorBlind == 1) 0 else 1) },
                            modifier = Modifier.weight(1f).paneNavItem(
                                cornerRadius = (16f * paneScale).dp,
                                onActivate = { listener.onColorBlindSelected(if (state.colorBlind == 1) 0 else 1) },
                            ),
                        )
                        HUDToggleChip(
                            label = stringResource(R.string.session_drawer_color_blind_deutan),
                            checked = state.colorBlind == 2,
                            onClick = { listener.onColorBlindSelected(if (state.colorBlind == 2) 0 else 2) },
                            modifier = Modifier.weight(1f).paneNavItem(
                                cornerRadius = (16f * paneScale).dp,
                                onActivate = { listener.onColorBlindSelected(if (state.colorBlind == 2) 0 else 2) },
                            ),
                        )
                        HUDToggleChip(
                            label = stringResource(R.string.session_drawer_color_blind_tritan),
                            checked = state.colorBlind == 3,
                            onClick = { listener.onColorBlindSelected(if (state.colorBlind == 3) 0 else 3) },
                            modifier = Modifier.weight(1f).paneNavItem(
                                cornerRadius = (16f * paneScale).dp,
                                onActivate = { listener.onColorBlindSelected(if (state.colorBlind == 3) 0 else 3) },
                            ),
                        )
                    }
                }

                ThinDivider()

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_drawer_scale))

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_scale_nearest),
                        checked = state.scaleFilter == 1,
                        onCheckedChange = { on -> listener.onScaleFilterSelected(if (on) 1 else 0) },
                    )

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_scale_linear),
                        checked = state.scaleFilter == 2,
                        onCheckedChange = { on -> listener.onScaleFilterSelected(if (on) 2 else 0) },
                    )

                    NavBooleanRow(
                        title = stringResource(R.string.session_drawer_scale_bicubic),
                        checked = state.scaleFilter == 3,
                        onCheckedChange = { on -> listener.onScaleFilterSelected(if (on) 3 else 0) },
                    )
                }
            }
        }
    }
}
