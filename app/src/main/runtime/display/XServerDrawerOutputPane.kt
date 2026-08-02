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

// Output/display pane composables, split out of XServerDrawerMenu.kt (behavior-identical).

@Composable
internal fun OutputPaneContent(
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
                if (state.outputSwapActive) {
                    OutputActiveControls(state = state, listener = listener, paneScale = paneScale)
                } else if (state.outputDisplayAvailable) {
                    OutputSendToDisplay(state = state, listener = listener, paneScale = paneScale)
                } else {
                    OutputCastEntry(listener = listener, paneScale = paneScale)
                }
            }
        }
    }
}

@Composable
internal fun OutputActiveControls(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    paneScale: Float,
) {
    OutputDeviceHeader(state = state, paneScale = paneScale)

    OutputCard(paneScale = paneScale, title = stringResource(R.string.session_drawer_output_display)) {
        if (state.outputResolutionLabels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
                OutputFieldLabel(stringResource(R.string.session_drawer_output_resolution), paneScale)
                InputControlsSimpleDropdown(
                    options = state.outputResolutionLabels,
                    selectedIndex = state.outputSelectedResolutionIndex,
                    onSelected = listener::onOutputResolutionSelected,
                )
                Text(
                    text = if (state.outputPanelScaling) {
                        stringResource(R.string.session_drawer_output_scaling_note, state.outputPanelNative)
                    } else {
                        stringResource(R.string.session_drawer_output_render_note)
                    },
                    color = DrawerTextSecondary,
                    fontSize = (11f * paneScale).sp,
                    lineHeight = (15f * paneScale).sp,
                )
            }
        }
        if (!state.outputPanelScaling && state.outputRefreshLabels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
                OutputFieldLabel(stringResource(R.string.session_drawer_output_refresh_rate), paneScale)
                InputControlsSimpleDropdown(
                    options = state.outputRefreshLabels,
                    selectedIndex = state.outputSelectedRefreshIndex,
                    onSelected = listener::onOutputRefreshRateSelected,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
            OutputFieldLabel(stringResource(R.string.session_drawer_output_aspect_ratio), paneScale)
            val aspectLabels =
                listOf(
                    stringResource(R.string.session_drawer_output_aspect_fit),
                    stringResource(R.string.session_drawer_output_aspect_stretch),
                    stringResource(R.string.session_drawer_output_aspect_zoom),
                )
            ChipFlow {
                aspectLabels.forEachIndexed { index, label ->
                    HUDToggleChip(
                        label = label,
                        checked = state.outputAspectMode == index,
                        onClick = { listener.onOutputAspectModeSelected(index) },
                        modifier = Modifier.paneNavItem(
                            cornerRadius = (12f * paneScale).dp,
                            onActivate = { listener.onOutputAspectModeSelected(index) },
                        ),
                    )
                }
            }
        }
    }

    if (state.outputGameModeSupported) {
        OutputCard(paneScale = paneScale, title = stringResource(R.string.session_drawer_output_game_mode)) {
            ChipFlow {
                HUDToggleChip(
                    label = stringResource(R.string.session_drawer_output_game_mode_on),
                    checked = state.outputGameModeEnabled,
                    onClick = { listener.onOutputGameModeToggled(true) },
                    modifier = Modifier.paneNavItem(
                        cornerRadius = (12f * paneScale).dp,
                        onActivate = { listener.onOutputGameModeToggled(true) },
                    ),
                )
                HUDToggleChip(
                    label = stringResource(R.string.session_drawer_output_game_mode_off),
                    checked = !state.outputGameModeEnabled,
                    onClick = { listener.onOutputGameModeToggled(false) },
                    modifier = Modifier.paneNavItem(
                        cornerRadius = (12f * paneScale).dp,
                        onActivate = { listener.onOutputGameModeToggled(false) },
                    ),
                )
            }
            Text(
                text = stringResource(R.string.session_drawer_output_game_mode_note),
                color = DrawerTextSecondary,
                fontSize = (11f * paneScale).sp,
                lineHeight = (15f * paneScale).sp,
            )
        }
    }

    if (state.outputVitureConnected) {
        OutputGlassesCard(state = state, listener = listener, paneScale = paneScale)
    }

    OutputPaneButton(
        label = stringResource(R.string.session_drawer_output_return_to_phone),
        paneScale = paneScale,
        onClick = listener::onOutputReturnToPhone,
    )
}

@Composable
internal fun OutputGlassesCard(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    paneScale: Float,
) {
    OutputCard(
        paneScale = paneScale,
        title = state.outputVitureName.ifEmpty { stringResource(R.string.session_drawer_output_glasses) },
    ) {
        if (state.outputVitureSupportsBrightness) {
            DrawerSliderRow(
                label = stringResource(R.string.session_drawer_output_brightness),
                valueText = "${state.outputVitureBrightness}/${state.outputVitureBrightnessMax}",
                value = state.outputVitureBrightness.toFloat(),
                valueRange = 0f..state.outputVitureBrightnessMax.toFloat(),
                steps = (state.outputVitureBrightnessMax - 1).coerceAtLeast(0),
                onValueChange = { listener.onOutputVitureBrightness(it.roundToInt()) },
            )
        }
        if (state.outputVitureSupportsFilm) {
            if (state.outputVitureFilmStepped) {
                DrawerSliderRow(
                    label = stringResource(R.string.session_drawer_output_shade),
                    valueText = "${state.outputVitureFilm}/8",
                    value = state.outputVitureFilm.toFloat(),
                    valueRange = 0f..8f,
                    steps = 7,
                    onValueChange = { listener.onOutputVitureFilm(it.roundToInt()) },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
                    OutputFieldLabel(stringResource(R.string.session_drawer_output_shade), paneScale)
                    ChipFlow {
                        HUDToggleChip(
                            label = stringResource(R.string.session_drawer_output_game_mode_on),
                            checked = state.outputVitureFilm > 0,
                            onClick = { listener.onOutputVitureFilm(1) },
                            modifier = Modifier.paneNavItem(
                                cornerRadius = (12f * paneScale).dp,
                                onActivate = { listener.onOutputVitureFilm(1) },
                            ),
                        )
                        HUDToggleChip(
                            label = stringResource(R.string.session_drawer_output_game_mode_off),
                            checked = state.outputVitureFilm == 0,
                            onClick = { listener.onOutputVitureFilm(0) },
                            modifier = Modifier.paneNavItem(
                                cornerRadius = (12f * paneScale).dp,
                                onActivate = { listener.onOutputVitureFilm(0) },
                            ),
                        )
                    }
                }
            }
        }
        if (state.outputVitureSupportsVolume) {
            DrawerSliderRow(
                label = stringResource(R.string.session_drawer_output_volume),
                valueText = "${state.outputVitureVolume}/${state.outputVitureVolumeMax}",
                value = state.outputVitureVolume.toFloat(),
                valueRange = 0f..state.outputVitureVolumeMax.toFloat(),
                steps = (state.outputVitureVolumeMax - 1).coerceAtLeast(0),
                onValueChange = { listener.onOutputVitureVolume(it.roundToInt()) },
            )
        }
        if (state.outputVitureSupports3D) {
            Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
                OutputFieldLabel(stringResource(R.string.session_drawer_output_3d), paneScale)
                ChipFlow {
                    HUDToggleChip(
                        label = stringResource(R.string.session_drawer_output_game_mode_on),
                        checked = state.outputViture3D,
                        onClick = { listener.onOutputViture3D(true) },
                        modifier = Modifier.paneNavItem(
                            cornerRadius = (12f * paneScale).dp,
                            onActivate = { listener.onOutputViture3D(true) },
                        ),
                    )
                    HUDToggleChip(
                        label = stringResource(R.string.session_drawer_output_game_mode_off),
                        checked = !state.outputViture3D,
                        onClick = { listener.onOutputViture3D(false) },
                        modifier = Modifier.paneNavItem(
                            cornerRadius = (12f * paneScale).dp,
                            onActivate = { listener.onOutputViture3D(false) },
                        ),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.session_drawer_output_glasses_note),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            lineHeight = (15f * paneScale).sp,
        )
    }
}

@Composable
internal fun OutputCard(
    paneScale: Float,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((14f * paneScale).dp))
                .background(PaneInnerResting)
                .border(1.dp, RestingCardBorder, RoundedCornerShape((14f * paneScale).dp))
                .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
        verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
    ) {
        PaneSectionLabel(title)
        content()
    }
}

@Composable
internal fun OutputFieldLabel(text: String, paneScale: Float) {
    Text(
        text = text,
        color = DrawerTextSecondary,
        fontSize = (12f * paneScale).sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun OutputDeviceHeader(state: XServerDrawerState, paneScale: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
        modifier = Modifier.padding(horizontal = (2f * paneScale).dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Monitor,
            contentDescription = null,
            tint = DrawerAccent,
            modifier = Modifier.size((22f * paneScale).dp),
        )
        Text(
            text = state.outputDisplayName.ifEmpty { stringResource(R.string.session_drawer_output_title) },
            color = DrawerTextPrimary,
            fontSize = (15f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun OutputSendToDisplay(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    paneScale: Float,
) {
    OutputDeviceHeader(state = state, paneScale = paneScale)
    OutputPaneButton(
        label = stringResource(R.string.session_drawer_output_send_to_display),
        paneScale = paneScale,
        onClick = listener::onOutputSwapToDisplay,
    )
    Text(
        text = stringResource(R.string.session_drawer_output_send_note),
        color = DrawerTextSecondary,
        fontSize = (11f * paneScale).sp,
        lineHeight = (15f * paneScale).sp,
    )
}

@Composable
internal fun OutputCastEntry(
    listener: XServerDrawerActionListener,
    paneScale: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy((6f * paneScale).dp)) {
        PaneSectionLabel(stringResource(R.string.session_drawer_output_cast_title))
        Text(
            text = stringResource(R.string.session_drawer_output_cast_body),
            color = DrawerTextSecondary,
            fontSize = (12f * paneScale).sp,
            lineHeight = (16f * paneScale).sp,
        )
    }
    OutputPaneButton(
        label = stringResource(R.string.session_drawer_output_cast_button),
        paneScale = paneScale,
        onClick = listener::onOutputCastClick,
    )
    Text(
        text = stringResource(R.string.session_drawer_output_cast_note),
        color = DrawerTextSecondary,
        fontSize = (11f * paneScale).sp,
        lineHeight = (15f * paneScale).sp,
    )
}

@Composable
internal fun OutputPaneButton(
    label: String,
    paneScale: Float,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((14f * paneScale).dp))
                .background(PaneInnerResting)
                .border(1.dp, RestingCardBorder, RoundedCornerShape((14f * paneScale).dp))
                .paneNavItem(cornerRadius = (14f * paneScale).dp, onActivate = onClick)
                .clickable { onClick() }
                .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = DrawerTextPrimary,
            fontSize = (14f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
