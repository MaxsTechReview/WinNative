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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    @JvmStatic
    fun setVisible(v: Boolean) { _visible.value = v }

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

private data class GaugeSpec(
    val label: String,
    val value: String,
    val fraction: Float,
    val color: Color,
    val sublabel: String? = null,
    val sublabelColor: Color = HudSub,
)

@Composable
fun PerformanceHudOverlay(modifier: Modifier = Modifier) {
    val s by PerformanceHudState.state.collectAsState()
    // A gauge stays on screen for as long as its element is enabled; a momentarily-unavailable
    // value shows N/A rather than dropping the gauge (which would make the row jump around).
    val gauges = ArrayList<GaugeSpec>(8)
    if (s.enabled.getOrElse(0) { false }) {
        gauges.add(GaugeSpec("FPS", s.fps.toInt().toString(), s.fps / 120f, HudAccent))
    }
    if (s.enabled.getOrElse(2) { false }) {
        gauges.add(GaugeSpec("GPU", pctText(s.gpuLoad), pctFraction(s.gpuLoad), loadColor(maxOf(s.gpuLoad, 0))))
    }
    if (s.enabled.getOrElse(3) { false }) {
        gauges.add(GaugeSpec("CPU", pctText(s.cpuPercent), pctFraction(s.cpuPercent), loadColor(maxOf(s.cpuPercent, 0))))
    }
    if (s.enabled.getOrElse(4) { false }) {
        gauges.add(GaugeSpec("RAM", pctText(s.ramPercent), pctFraction(s.ramPercent), loadColor(maxOf(s.ramPercent, 0))))
    }
    if (s.enabled.getOrElse(6) { false }) {
        gauges.add(GaugeSpec("ms", String.format("%.1f", s.frametimeMs), 1f - (s.frametimeMs / 33.3f), HudGood))
    }
    if (s.enabled.getOrElse(5) { false }) {
        // Battery + temperature is a single HUD element: watts is the gauge value, temp the sublabel.
        gauges.add(GaugeSpec(
            "WATT", if (s.batteryWatts >= 0f) String.format("%.1f", s.batteryWatts) else "N/A",
            if (s.batteryWatts >= 0f) s.batteryWatts / 12f else 0f, HudAccent,
            sublabel = if (s.tempC >= 0) "${s.tempC}°C" else null,
            sublabelColor = if (s.tempC >= 0) tempColor(s.tempC) else HudSub,
        ))
    }
    val showRenderer = s.enabled.getOrElse(1) { false } && s.renderer.isNotEmpty()
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xF00A0D13)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .padding(bottom = if (showRenderer) 48.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            gauges.chunked(3).forEach { rowGauges ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                    rowGauges.forEach { g -> HudGauge(g.label, g.value, g.fraction, g.color, g.sublabel, g.sublabelColor) }
                }
            }
        }
        if (showRenderer) {
            // Pinned just above the bottom edge so it is never clipped, regardless of gauge count.
            Text(
                s.renderer,
                color = HudAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x1A1A9FFF))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

private fun pctText(v: Int): String = if (v >= 0) "$v%" else "N/A"

private fun pctFraction(v: Int): Float = if (v >= 0) v / 100f else 0f

private fun loadColor(pct: Int): Color =
    if (pct >= 90) HudBad else if (pct >= 70) HudWarn else HudGood

private fun tempColor(c: Int): Color =
    if (c >= 45) HudBad else if (c >= 40) HudWarn else HudGood

@Composable
private fun HudGauge(
    label: String,
    valueText: String,
    fraction: Float,
    accent: Color,
    sublabel: String? = null,
    sublabelColor: Color = HudSub,
) {
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
        if (sublabel != null) {
            Text(sublabel, color = sublabelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
