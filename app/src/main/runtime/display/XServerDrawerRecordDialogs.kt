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

// Record-settings + FPS-limiter dialog composables, split out of XServerDrawerMenu.kt (behavior-identical).

/** Centered popup for choosing recording fps / resolution / quality (+ Record UI), then Record Now. */
@Composable
internal fun RecordSettingsDialog(
    config: RecordUiConfig,
    onDismiss: () -> Unit,
    onRecordNow: (fpsIndex: Int, resolutionIndex: Int, quality: Int, recordUI: Boolean) -> Unit,
) {
    val fpsOptions = config.fpsOptions.ifEmpty { listOf(60) }
    val resOptions = config.resolutionLabels.ifEmpty { listOf("Native") }

    var fpsIndex by remember { mutableStateOf(config.fpsIndex.coerceIn(0, fpsOptions.lastIndex)) }
    var resIndex by remember { mutableStateOf(config.resolutionIndex.coerceIn(0, resOptions.lastIndex)) }
    var quality by remember { mutableStateOf(config.quality.coerceIn(0, RECORD_QUALITY_LABELS.lastIndex)) }
    var recordUI by remember { mutableStateOf(config.recordUI) }

    val recordNav = remember { SharedPaneNavRegistry() }
    val doRecord = { onRecordNow(fpsIndex, resIndex, quality, recordUI) }

    val shape = RoundedCornerShape(16.dp)
    // Cap card height (landscape is short); settings scroll, the Record Now button stays pinned.
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CompositionLocalProvider(SharedLocalPaneNav provides recordNav) {
        DialogPaneNav(recordNav, onDismiss = onDismiss, onStart = doRecord)
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxCardHeight)
                        .clip(shape)
                        .background(PaneSurfaceColor)
                        .border(1.dp, RestingCardBorder, shape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FiberManualRecord,
                        contentDescription = null,
                        tint = RecordRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.session_record_settings_title),
                        color = DrawerTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().sharedPaneNavItem(
                            onAdjust = { dir -> if (fpsOptions.size > 1) fpsIndex = (fpsIndex + dir).coerceIn(0, fpsOptions.lastIndex) },
                        ),
                    ) {
                        DrawerSliderRow(
                            label = stringResource(R.string.session_record_fps),
                            valueText = "${fpsOptions[fpsIndex]} fps",
                            value = fpsIndex.toFloat(),
                            valueRange = 0f..(fpsOptions.lastIndex.coerceAtLeast(1)).toFloat(),
                            steps = (fpsOptions.size - 2).coerceAtLeast(0),
                            onValueChange = { if (fpsOptions.size > 1) fpsIndex = it.roundToInt().coerceIn(0, fpsOptions.lastIndex) },
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().sharedPaneNavItem(
                            onAdjust = { dir -> if (resOptions.size > 1) resIndex = (resIndex + dir).coerceIn(0, resOptions.lastIndex) },
                        ),
                    ) {
                        DrawerSliderRow(
                            label = stringResource(R.string.session_record_resolution),
                            valueText = resOptions[resIndex],
                            value = resIndex.toFloat(),
                            valueRange = 0f..(resOptions.lastIndex.coerceAtLeast(1)).toFloat(),
                            steps = (resOptions.size - 2).coerceAtLeast(0),
                            onValueChange = { if (resOptions.size > 1) resIndex = it.roundToInt().coerceIn(0, resOptions.lastIndex) },
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().sharedPaneNavItem(
                            onAdjust = { dir -> quality = (quality + dir).coerceIn(0, RECORD_QUALITY_LABELS.lastIndex) },
                        ),
                    ) {
                        DrawerSliderRow(
                            label = stringResource(R.string.session_record_quality),
                            valueText = RECORD_QUALITY_LABELS[quality],
                            value = quality.toFloat(),
                            valueRange = 0f..(RECORD_QUALITY_LABELS.lastIndex).toFloat(),
                            steps = (RECORD_QUALITY_LABELS.size - 2).coerceAtLeast(0),
                            onValueChange = { quality = it.roundToInt().coerceIn(0, RECORD_QUALITY_LABELS.lastIndex) },
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().sharedPaneNavItem(
                            onActivate = { recordUI = !recordUI },
                        ),
                    ) {
                        DrawerBooleanRow(
                            title = stringResource(R.string.session_record_include_ui),
                            checked = recordUI,
                            onCheckedChange = { recordUI = it },
                            subtitle = stringResource(R.string.session_record_include_ui_subtitle),
                        )
                    }
                }

                Button(
                    onClick = doRecord,
                    modifier = Modifier.fillMaxWidth().height(48.dp).sharedPaneNavItem(
                        cornerRadius = 12.dp,
                        onActivate = doRecord,
                        isEntry = true,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = RecordRed,
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FiberManualRecord,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.session_record_now),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        }
    }
}

@Composable
internal fun FPSLimiterCard(
    currentLimit: Int,
    maxRefreshRate: Int,
    onLimitChanged: (Int) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val enabled = currentLimit > 0
    val maxFps = maxRefreshRate.coerceAtLeast(FPS_LIMITER_MIN)
    val steps = (maxFps - FPS_LIMITER_MIN - 1).coerceAtLeast(0)

    // Slider position tracked locally (readout follows the drag, value survives an off/on toggle); the commit is deferred to release and re-seeds when maxFps changes (e.g. a mid-game refresh-rate change that clamps the limit).
    var sliderValue by remember(maxFps) {
        mutableStateOf(
            (if (currentLimit > 0) currentLimit else FPS_LIMITER_DEFAULT)
                .coerceIn(FPS_LIMITER_MIN, maxFps)
                .toFloat(),
        )
    }

    LaunchedEffect(currentLimit) {
        if (currentLimit > 0) {
            val target = currentLimit.coerceIn(FPS_LIMITER_MIN, maxFps).toFloat()
            if (target != sliderValue) sliderValue = target
        }
    }

    val borderColor by animateColorAsState(
        targetValue = if (enabled) ActiveCardBorder else RestingCardBorder,
        animationSpec = tween(0),
        label = "fpsLimiterCardBorder",
    )
    val shape = RoundedCornerShape((14f * paneScale).dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PaneInnerResting)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = (12f * paneScale).dp, vertical = (8f * paneScale).dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLimitChanged(if (enabled) 0 else sliderValue.roundToInt()) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.session_drawer_fps_limiter),
                color = DrawerTextPrimary,
                fontSize = (14f * paneScale).sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    if (enabled) {
                        "${sliderValue.roundToInt()} FPS"
                    } else {
                        stringResource(R.string.session_drawer_fps_limiter_off)
                    },
                color = if (enabled) DrawerAccent else DrawerTextSecondary,
                fontSize = (14f * paneScale).sp,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = (8f * paneScale).dp),
            )
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { on -> onLimitChanged(if (on) sliderValue.roundToInt() else 0) },
                    colors = outlinedSwitchColors(DrawerAccent, DrawerTextSecondary),
                )
            }
        }

        AnimatedVisibility(
            visible = enabled,
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
            Column {
                Spacer(Modifier.height((6f * paneScale).dp))
                CompactSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = FPS_LIMITER_MIN.toFloat()..maxFps.toFloat(),
                    steps = steps,
                    onValueChangeFinished = { onLimitChanged(sliderValue.roundToInt()) },
                )
            }
        }
    }
}
