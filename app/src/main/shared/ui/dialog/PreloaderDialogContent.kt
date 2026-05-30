package com.winlator.cmod.shared.ui.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// State holder - Java-friendly mutable properties.
class PreloaderDialogState {
    val text = mutableStateOf("")
    val isIndeterminate = mutableStateOf(true)
    val progress = mutableIntStateOf(0)
    val title = mutableStateOf("")
    val badge = mutableStateOf("")
    val subtitle = mutableStateOf("")
    val stableContentLayout = mutableStateOf(false)

    fun setText(value: String) {
        text.value = value
    }

    fun setIndeterminate(value: Boolean) {
        isIndeterminate.value = value
    }

    fun setProgress(value: Int) {
        progress.intValue = value
    }

    fun setTitle(value: String) {
        title.value = value
    }

    fun setBadge(value: String) {
        badge.value = value
    }

    fun setSubtitle(value: String) {
        subtitle.value = value
    }

    fun setStableContentLayout(value: Boolean) {
        stableContentLayout.value = value
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val phaseOffset: Float,
)

private val BgTop = Color(0xFF07090F)
private val BgBottom = Color(0xFF0C1018)
private val TextPrimary = Color(0xFFF5F8FF)
private val TextSecondary = Color(0xFFA1B1C8)
private val TextDim = Color(0xFF65748B)
private val TrackColor = Color(0xFF202A3A)

private val InterFont = FontFamily(Font(R.font.inter_medium, FontWeight.Medium))
private val BricolageDisplayFont =
    FontFamily(Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold))

private fun badgeStringRes(value: String): Int? =
    when (value.uppercase()) {
        "STEAM" -> R.string.preloader_platform_steam
        "EPIC" -> R.string.preloader_platform_epic
        "GOG" -> R.string.preloader_platform_gog
        "CUSTOM" -> R.string.preloader_platform_custom
        else -> null
    }

private fun badgeColor(value: String): Color =
    when (value.uppercase()) {
        "STEAM" -> Color(0xFF66C0F4)
        "EPIC" -> Color(0xFFB8BAC4)
        "GOG" -> Color(0xFFC55CFF)
        "CUSTOM" -> Color(0xFF4FE3C1)
        else -> Color(0xFF57CBDE)
    }

@Composable
fun PreloaderDialogContent(state: PreloaderDialogState) {
    val text by state.text
    val isIndeterminate by state.isIndeterminate
    val progress by state.progress
    val title by state.title
    val badge by state.badge
    val subtitle by state.subtitle
    val stableContentLayout by state.stableContentLayout

    val accentColor = badgeColor(badge)
    val particles =
        remember {
            List(8) { i ->
                val hash = ((i * 7919 + 104729) % 10000) / 10000f
                Particle(
                    x = ((i * 3571 + 7321) % 10000) / 10000f,
                    y = ((i * 5323 + 1931) % 10000) / 10000f,
                    // Whole-number traversals per cycle so the shared phase wraps seamlessly
                    // (a fractional speed makes every particle teleport when the tween restarts).
                    speed = (1 + (i % 2)).toFloat(),
                    size = 1.1f + hash * 1.6f,
                    alpha = 0.08f + hash * 0.1f,
                    phaseOffset = hash * 6.2832f,
                )
            }
        }

    val infiniteTransition = rememberInfiniteTransition(label = "preloaderMotion")
    val glowPulse =
        infiniteTransition.animateFloat(
            initialValue = 0.74f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5200, easing = EaseInOut), RepeatMode.Reverse),
            label = "glowPulse",
        )
    val particlePhase =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
            label = "particlePhase",
        )

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "contentAlpha",
    )
    val contentRise by animateFloatAsState(
        targetValue = if (entered) 0f else 18f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "contentRise",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val pulse = glowPulse.value
                    val glowCenter =
                        Offset(
                            x = w * (0.5f + 0.025f * sin(particlePhase.value * 2f * PI).toFloat()),
                            y = h * 0.42f,
                        )

                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        accentColor.copy(alpha = 0.16f * pulse),
                                        accentColor.copy(alpha = 0.045f * pulse),
                                        Color.Transparent,
                                    ),
                                center = glowCenter,
                                radius = w * 0.58f,
                            ),
                        radius = w * 0.58f,
                        center = glowCenter,
                    )

                    particles.forEach { particle ->
                        val raw = (particle.y - particlePhase.value * particle.speed) % 1f
                        val t = if (raw < 0f) raw + 1f else raw
                        val drift =
                            sin((particlePhase.value * 2f * PI).toFloat() + particle.phaseOffset) * w * 0.018f
                        val fade =
                            when {
                                t < 0.12f -> t / 0.12f
                                t > 0.9f -> (1f - t) / 0.1f
                                else -> 1f
                            }
                        drawCircle(
                            color = accentColor.copy(alpha = particle.alpha * fade),
                            radius = particle.size.dp.toPx(),
                            center = Offset(w * particle.x + drift, h * t),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 28.dp)
                    .offset { IntOffset(0, contentRise.roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val displayTitle = title.ifEmpty { stringResource(R.string.preloader_default_name) }
            val badgeRes = badgeStringRes(badge)

            Box(
                modifier =
                    Modifier
                        .widthIn(max = 520.dp)
                        .height(if (stableContentLayout) 76.dp else 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = BricolageDisplayFont,
                    color = TextPrimary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (stableContentLayout || subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = 440.dp)
                            .height(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = subtitle.ifEmpty { " " },
                        fontSize = 15.sp,
                        fontFamily = InterFont,
                        color = TextDim.copy(alpha = contentAlpha),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (stableContentLayout || badgeRes != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (badgeRes != null) {
                        PlatformBadge(
                            label = stringResource(badgeRes),
                            accentColor = accentColor,
                            alpha = contentAlpha,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            NeonCometRing(
                isIndeterminate = isIndeterminate,
                progress = progress,
                accentColor = accentColor,
                alpha = contentAlpha,
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = text,
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    // Cross-fade with a gentle upward drift: the new line rises into place from
                    // slightly below while the old one fades out drifting up. No 3D flip.
                    (
                        fadeIn(tween(260, delayMillis = 60)) +
                            slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 }
                    ) togetherWith (
                        fadeOut(tween(200)) +
                            slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 6 }
                    )
                },
                label = "statusText",
            ) { value ->
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontFamily = InterFont,
                    color = TextSecondary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}

@Composable
private fun PlatformBadge(
    label: String,
    accentColor: Color,
    alpha: Float,
) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(accentColor.copy(alpha = 0.1f * alpha))
                .border(width = 1.dp, color = accentColor.copy(alpha = 0.32f * alpha), shape = shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.9f * alpha)),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            letterSpacing = 0.3.sp,
            fontFamily = InterFont,
            fontWeight = FontWeight.Medium,
            color = accentColor.copy(alpha = alpha),
            maxLines = 1,
            // Drop default font padding and center within the line box so the label sits dead-center
            // against the dot instead of riding high.
            style =
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                ),
        )
    }
}

@Composable
private fun NeonCometRing(
    isIndeterminate: Boolean,
    progress: Int,
    accentColor: Color,
    alpha: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ringMotion")
    val rotation =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "ringRotation",
        )
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "ringProgress",
    )

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            // Inset enough to keep the rounded cap and lead dot from clipping the bounds.
            val inset = strokeWidth / 2f + 2.dp.toPx()
            val arcSize =
                Size(
                    width = size.width - inset * 2f,
                    height = size.height - inset * 2f,
                )
            val topLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = arcSize.width / 2f
            // A long ~280° arc reads as a proper spinner; determinate fills to progress.
            val sweep = if (isIndeterminate) 280f else 360f * animatedProgress
            val startAngle = if (isIndeterminate) rotation.value else -90f
            val endAngle = startAngle + sweep
            val endRadians = Math.toRadians(endAngle.toDouble())
            val leadDot =
                Offset(
                    x = center.x + radius * cos(endRadians).toFloat(),
                    y = center.y + radius * sin(endRadians).toFloat(),
                )

            // Track ring behind the spinner.
            drawArc(
                color = TrackColor.copy(alpha = 0.7f * alpha),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (sweep > 0.5f) {
                // Tapered arc: faint tail growing into a solid accent head. A sweep gradient is
                // anchored to the canvas (0° = +x axis), so rotate the canvas to the arc's start
                // and place the bright head at the arc's actual end (sweep/360 of the gradient).
                val head = (sweep / 360f).coerceIn(0.05f, 1f)
                val cometStops =
                    if (head >= 0.999f) {
                        arrayOf(
                            0f to accentColor.copy(alpha = 0.35f * alpha),
                            0.7f to accentColor.copy(alpha = 0.8f * alpha),
                            1f to Color.White.copy(alpha = 0.95f * alpha),
                        )
                    } else {
                        arrayOf(
                            0f to Color.Transparent,
                            head * 0.55f to accentColor.copy(alpha = 0.35f * alpha),
                            head * 0.85f to accentColor.copy(alpha = 0.85f * alpha),
                            head to accentColor.copy(alpha = 0.95f * alpha),
                            1f to Color.Transparent,
                        )
                    }
                rotate(degrees = startAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(colorStops = cometStops, center = center),
                        startAngle = 0f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            if (isIndeterminate || animatedProgress > 0f) {
                // Subtle glow on the leading edge.
                drawCircle(
                    color = accentColor.copy(alpha = 0.2f * alpha),
                    radius = 3.5.dp.toPx(),
                    center = leadDot,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f * alpha),
                    radius = 1.5.dp.toPx(),
                    center = leadDot,
                )
            }
        }
    }
}

// Java bridge - called from PreloaderDialog.java as:
// PreloaderDialogContentKt.setupPreloaderComposeView(composeView, state, activity)
fun setupPreloaderComposeView(
    composeView: ComposeView,
    state: PreloaderDialogState,
    activity: android.app.Activity,
) {
    if (activity is androidx.lifecycle.LifecycleOwner) {
        composeView.setViewTreeLifecycleOwner(activity)
    }
    if (activity is androidx.savedstate.SavedStateRegistryOwner) {
        composeView.setViewTreeSavedStateRegistryOwner(activity)
    }
    composeView.setContent {
        WinNativeTheme {
            PreloaderDialogContent(state)
        }
    }
}
