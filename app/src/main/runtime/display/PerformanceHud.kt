package com.winlator.cmod.runtime.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Single source of truth for the performance HUD: FrameRating pushes live values each tick and the
// XServer menu pushes which elements are enabled, so the in-game overlay and the phone gauges always
// show the same set. Element indices match FrameRating: 0 FPS, 1 renderer, 2 GPU, 3 CPU, 4 RAM,
// 5 battery+temp, 6 frametime.
object PerformanceHudState {
    data class Snapshot(
        val enabled: BooleanArray = BooleanArray(7),
        val fps: Float = 0f,
        val frametimeMs: Float = 0f,
        val gpuLoad: Int = -1,
        val cpuPercent: Int = -1,
        val ramPercent: Int = -1,
        val batteryWatts: Float = 0f,
        val tempC: Int = -1,
        val renderer: String = "",
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    @JvmStatic
    fun updateEnabled(enabled: BooleanArray) {
        _state.value = _state.value.copy(enabled = enabled.copyOf())
    }

    @JvmStatic
    fun updateValues(
        fps: Float, frametimeMs: Float, gpuLoad: Int, cpuPercent: Int,
        ramPercent: Int, batteryWatts: Float, tempC: Int, renderer: String,
    ) {
        _state.value = _state.value.copy(
            fps = fps, frametimeMs = frametimeMs, gpuLoad = gpuLoad, cpuPercent = cpuPercent,
            ramPercent = ramPercent, batteryWatts = batteryWatts, tempC = tempC, renderer = renderer,
        )
    }
}

private val HudAccent = Color(0xFF1A9FFF)
private val HudGood = Color(0xFF35D0BA)
private val HudWarn = Color(0xFFFFB020)
private val HudBad = Color(0xFFFF5A5A)
private val HudText = Color(0xFFF0F4FF)
private val HudSub = Color(0xFF7A8FA8)
private val HudTrack = Color(0x33FFFFFF)

// Mount the gauge HUD into a ComposeView from the Java activity.
fun mountPerformanceHud(view: androidx.compose.ui.platform.ComposeView) {
    view.setContent { PerformanceHudOverlay() }
}

private data class GaugeSpec(val label: String, val value: String, val fraction: Float, val color: Color)

@Composable
fun PerformanceHudOverlay(modifier: Modifier = Modifier) {
    val s by PerformanceHudState.state.collectAsState()
    val gauges = ArrayList<GaugeSpec>(8)
    if (s.enabled.getOrElse(0) { false }) {
        gauges.add(GaugeSpec("FPS", s.fps.toInt().toString(), s.fps / 120f, HudAccent))
    }
    if (s.enabled.getOrElse(2) { false } && s.gpuLoad >= 0) {
        gauges.add(GaugeSpec("GPU", "${s.gpuLoad}%", s.gpuLoad / 100f, loadColor(s.gpuLoad)))
    }
    if (s.enabled.getOrElse(3) { false } && s.cpuPercent >= 0) {
        gauges.add(GaugeSpec("CPU", "${s.cpuPercent}%", s.cpuPercent / 100f, loadColor(s.cpuPercent)))
    }
    if (s.enabled.getOrElse(4) { false } && s.ramPercent >= 0) {
        gauges.add(GaugeSpec("RAM", "${s.ramPercent}%", s.ramPercent / 100f, loadColor(s.ramPercent)))
    }
    if (s.enabled.getOrElse(6) { false }) {
        gauges.add(GaugeSpec("ms", String.format("%.1f", s.frametimeMs), 1f - (s.frametimeMs / 33.3f), HudGood))
    }
    if (s.enabled.getOrElse(5) { false }) {
        gauges.add(GaugeSpec("Watt", String.format("%.1f", s.batteryWatts), s.batteryWatts / 12f, HudAccent))
        if (s.tempC >= 0) gauges.add(GaugeSpec("°C", s.tempC.toString(), s.tempC / 60f, tempColor(s.tempC)))
    }
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xF00A0D13)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            gauges.chunked(3).forEach { rowGauges ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)) {
                    rowGauges.forEach { g -> HudGauge(g.label, g.value, g.fraction, g.color) }
                }
            }
            if (s.enabled.getOrElse(1) { false } && s.renderer.isNotEmpty()) {
                Text(s.renderer, color = HudText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun loadColor(pct: Int): Color =
    if (pct >= 90) HudBad else if (pct >= 70) HudWarn else HudGood

private fun tempColor(c: Int): Color =
    if (c >= 45) HudBad else if (c >= 40) HudWarn else HudGood

@Composable
private fun HudGauge(label: String, valueText: String, fraction: Float, accent: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = HudTrack, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent, startAngle = 135f, sweepAngle = 270f * fraction.coerceIn(0f, 1f),
                    useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(valueText, color = HudText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = HudSub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
