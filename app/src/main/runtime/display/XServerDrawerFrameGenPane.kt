package com.winlator.cmod.runtime.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.framegen.FrameGenPreset
import kotlin.math.roundToInt

@Composable
internal fun FrameGenPaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    var fpsLimitMemory by remember {
        mutableStateOf(if (state.fpsLimit > 0) state.fpsLimit else FPS_LIMITER_DEFAULT)
    }
    LaunchedEffect(state.fpsLimit) {
        if (state.fpsLimit > 0) fpsLimitMemory = state.fpsLimit
    }

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
                FrameGenerationSection(state = state, listener = listener, paneScale = paneScale)

                ThinDivider()

                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    PaneSectionLabel(stringResource(R.string.session_drawer_fps_limiter))

                    Box(
                        Modifier.fillMaxWidth().paneNavItem(
                            cornerRadius = (12f * paneScale).dp,
                            onActivate = {
                                listener.onFPSLimitChanged(
                                    if (state.fpsLimit > 0) {
                                        0
                                    } else {
                                        fpsLimitMemory.coerceIn(FPS_LIMITER_MIN, state.maxRefreshRate)
                                    },
                                )
                            },
                            onAdjust = { dir ->
                                val base = if (state.fpsLimit > 0) state.fpsLimit else fpsLimitMemory
                                val q = base / 5.0
                                val units = if (dir > 0) Math.floor(q + 1e-4) + 1 else Math.ceil(q - 1e-4) - 1
                                listener.onFPSLimitChanged(
                                    (units * 5).toInt().coerceIn(FPS_LIMITER_MIN, state.maxRefreshRate),
                                )
                            },
                        ),
                    ) {
                        FPSLimiterCard(
                            currentLimit = state.fpsLimit,
                            maxRefreshRate = state.maxRefreshRate,
                            onLimitChanged = listener::onFPSLimitChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameGenerationSection(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
    paneScale: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
        PaneSectionLabel(stringResource(R.string.session_drawer_frame_generation))

        if (!state.frameGenAvailable) {
            FrameGenNote(stringResource(R.string.session_drawer_frame_generation_missing), paneScale)
        } else {
            NavBooleanRow(
                title = stringResource(R.string.session_drawer_frame_generation_enable),
                checked = state.frameGenEnabled,
                onCheckedChange = listener::onFrameGenEnabledChanged,
            )

            FrameGenNote(stringResource(R.string.session_drawer_frame_generation_note), paneScale)

            AnimatedVisibility(
                visible = state.frameGenEnabled,
                enter =
                    expandVertically(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
                exit =
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                    FrameGenFieldLabel(
                        stringResource(R.string.session_drawer_frame_generation_target),
                        paneScale,
                    )

                    val rates =
                        remember(state.maxRefreshRate, state.frameGenTargetRate) {
                            (
                                FrameGenTargetRates.filter { it <= state.maxRefreshRate } +
                                    listOfNotNull(state.frameGenTargetRate.takeIf { it > 0 })
                            )
                                .distinct()
                                .sorted()
                        }

                    ChipFlow {
                        HUDToggleChip(
                            label = stringResource(R.string.session_drawer_frame_generation_target_off),
                            checked = state.frameGenTargetRate == 0,
                            onClick = { listener.onFrameGenTargetRateSelected(0) },
                            modifier = Modifier.paneNavItem(
                                cornerRadius = (16f * paneScale).dp,
                                onActivate = { listener.onFrameGenTargetRateSelected(0) },
                            ),
                        )
                        rates.forEach { rate ->
                            HUDToggleChip(
                                label = stringResource(
                                    R.string.session_drawer_frame_generation_target_value,
                                    rate,
                                ),
                                checked = state.frameGenTargetRate == rate,
                                onClick = { listener.onFrameGenTargetRateSelected(rate) },
                                modifier = Modifier.paneNavItem(
                                    cornerRadius = (16f * paneScale).dp,
                                    onActivate = { listener.onFrameGenTargetRateSelected(rate) },
                                ),
                            )
                        }
                    }

                    if (state.frameGenTargetRate == 0) {
                        FrameGenFieldLabel(
                            stringResource(R.string.session_drawer_frame_generation_multiplier),
                            paneScale,
                        )
                        ChipFlow {
                            FrameGenMultipliers.forEach { multiplier ->
                                HUDToggleChip(
                                    label = stringResource(
                                        R.string.session_drawer_frame_generation_multiplier_value,
                                        multiplier,
                                    ),
                                    checked = state.frameGenMultiplier == multiplier,
                                    onClick = { listener.onFrameGenMultiplierSelected(multiplier) },
                                    modifier = Modifier.paneNavItem(
                                        cornerRadius = (16f * paneScale).dp,
                                        onActivate = { listener.onFrameGenMultiplierSelected(multiplier) },
                                    ),
                                )
                            }
                        }
                    } else {
                        FrameGenNote(
                            stringResource(R.string.session_drawer_frame_generation_target_note),
                            paneScale,
                        )
                    }

                    FrameGenPresetRow(
                        selected = FrameGenPreset.fromFlowScale(state.frameGenFlowScale),
                        onSelected = { listener.onFrameGenFlowScaleChanged(it.flowScale) },
                        paneScale = paneScale,
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameGenPresetRow(
    selected: FrameGenPreset,
    onSelected: (FrameGenPreset) -> Unit,
    paneScale: Float,
) {
    val presets = FrameGenPreset.values()
    val index = presets.indexOf(selected).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy((4f * paneScale).dp)) {
        NavSliderRow(
            label = stringResource(R.string.frame_generation_preset),
            valueText = stringResource(selected.labelRes),
            value = index.toFloat(),
            valueRange = 0f..(presets.size - 1).toFloat(),
            steps = presets.size - 2,
            adjustStep = 1f,
            onValueChange = { onSelected(FrameGenPreset.atIndex(it.roundToInt())) },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            presets.forEachIndexed { i, preset ->
                Text(
                    text = stringResource(preset.shortLabelRes),
                    color = if (i == index) DrawerAccent else DrawerTextSecondary,
                    fontSize = (10f * paneScale).sp,
                    fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        presets.size - 1 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FrameGenNote(stringResource(selected.descriptionRes), paneScale)
    }
}

@Composable
private fun FrameGenFieldLabel(text: String, paneScale: Float) {
    Text(
        text = text,
        color = DrawerTextSecondary,
        fontSize = (12f * paneScale).sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun FrameGenNote(text: String, paneScale: Float) {
    Text(
        text = text,
        color = DrawerTextSecondary,
        fontSize = (11f * paneScale).sp,
        lineHeight = (15f * paneScale).sp,
    )
}
