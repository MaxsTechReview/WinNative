package com.winlator.cmod.shared.ui.dialog
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// State holder — Java-friendly mutable properties
class PreloaderDialogState {
    val text = mutableStateOf("")
    val isIndeterminate = mutableStateOf(true)
    val progress = mutableIntStateOf(0)
    val gameName = mutableStateOf("")
    val platform = mutableStateOf("")
    val containerName = mutableStateOf("")
    val stableLaunchLayout = mutableStateOf(false)

    fun setText(value: String) {
        text.value = value
    }

    fun setIndeterminate(value: Boolean) {
        isIndeterminate.value = value
    }

    fun setProgress(value: Int) {
        progress.intValue = value
    }

    fun setGameName(value: String) {
        gameName.value = value
    }

    fun setPlatform(value: String) {
        platform.value = value
    }

    fun setContainerName(value: String) {
        containerName.value = value
    }

    fun setStableLaunchLayout(value: Boolean) {
        stableLaunchLayout.value = value
    }
}

private data class Particle(
    val x: Float,
    val speed: Float,
    val size: Float,
    val phaseOffset: Float,
)

// Colors
private val BgDark = Color(0xFF111822)
private val TextPrimary = Color(0xFFF5F9FF)
private val TextSecondary = Color(0xFF9CB0C7)
private val TextDim = Color(0xFF6B7F95)
private val TrackColor = Color(0xFF21293D)
private val IndicatorColor = Color(0xFF57CBDE)
private val CardBg = Color(0xFF1A2028)
private val CardBorder = Color(0xFF253443)
private val SteamPlatformBlue = Color(0xFF66C0F4)

private val InterFont = FontFamily(Font(R.font.inter_medium))

private fun platformStringRes(source: String): Int? =
    when (source.uppercase()) {
        "STEAM" -> R.string.preloader_platform_steam
        "EPIC" -> R.string.preloader_platform_epic
        "GOG" -> R.string.preloader_platform_gog
        "CUSTOM" -> R.string.preloader_platform_custom
        else -> null
    }

private fun platformColor(source: String): Color =
    when (source.uppercase()) {
        "STEAM" -> SteamPlatformBlue
        "EPIC" -> Color(0xFF8A8A8A)
        "GOG" -> Color(0xFFAB47BC)
        "CUSTOM" -> SteamPlatformBlue
        else -> Color(0xFF57CBDE)
    }

@Composable
fun PreloaderDialogContent(state: PreloaderDialogState) {
    val text by state.text
    val isIndeterminate by state.isIndeterminate
    val progress by state.progress
    val gameName by state.gameName
    val platform by state.platform
    val containerName by state.containerName
    val stableLaunchLayout by state.stableLaunchLayout

    // Particle seeds — stable across recomposition
    val particles =
        remember {
            List(20) { i ->
                val hash = ((i * 7919 + 104729) % 10000) / 10000f
                Particle(
                    x = ((i * 3571 + 7321) % 10000) / 10000f,
                    speed = 0.6f + hash * 0.4f,
                    size = 1f + hash * 1.5f,
                    phaseOffset = hash * 6.2832f,
                )
            }
        }

    // Orb positions — static for launch screen
    val orbAnim = tween<Float>(2000, easing = EaseInOut)
    val o1x by animateFloatAsState(0.3f, orbAnim, label = "o1x")
    val o1y by animateFloatAsState(0.25f, orbAnim, label = "o1y")
    val o2x by animateFloatAsState(0.7f, orbAnim, label = "o2x")
    val o2y by animateFloatAsState(0.65f, orbAnim, label = "o2y")
    val o3x by animateFloatAsState(0.5f, orbAnim, label = "o3x")
    val o3y by animateFloatAsState(0.45f, orbAnim, label = "o3y")

    val infiniteTransition = rememberInfiniteTransition(label = "bgGlow")
    val phase1 =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.2832f,
            animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase1",
        )
    val phase2 =
        infiniteTransition.animateFloat(
            initialValue = 6.2832f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(38000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase2",
        )
    val pulse =
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8000, easing = EaseInOut), RepeatMode.Reverse),
            label = "pulse",
        )
    val particlePhase =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
            label = "particlePhase",
        )

    // Orbital dots rotation
    val orbitalPhase =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.2832f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "orbital",
        )

    // Shimmer sweep for platform badge
    val shimmerPhase =
        infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer",
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BgDark)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val p1 = phase1.value
                    val p2 = phase2.value
                    val p = pulse.value

                    // Orb 1 — cyan
                    val c1 =
                        Offset(
                            w * (o1x + 0.04f * cos(p1)),
                            h * (o1y + 0.03f * sin(p1)),
                        )
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0xFF57CBDE).copy(alpha = 0.04f * p),
                                        Color(0xFF57CBDE).copy(alpha = 0.015f * p),
                                        Color.Transparent,
                                    ),
                                center = c1,
                                radius = w * 0.6f,
                            ),
                        radius = w * 0.6f,
                        center = c1,
                    )

                    // Orb 2 — blue
                    val c2 =
                        Offset(
                            w * (o2x + 0.04f * cos(p2)),
                            h * (o2y + 0.03f * sin(p2)),
                        )
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0xFF3B82F6).copy(alpha = 0.035f * p),
                                        Color(0xFF3B82F6).copy(alpha = 0.01f * p),
                                        Color.Transparent,
                                    ),
                                center = c2,
                                radius = w * 0.55f,
                            ),
                        radius = w * 0.55f,
                        center = c2,
                    )

                    // Orb 3 — teal accent
                    val c3 =
                        Offset(
                            w * (o3x + 0.03f * sin(p1 * 0.7f)),
                            h * (o3y + 0.03f * cos(p2 * 0.6f)),
                        )
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0xFF2DD4BF).copy(alpha = 0.02f * p),
                                        Color.Transparent,
                                    ),
                                center = c3,
                                radius = w * 0.45f,
                            ),
                        radius = w * 0.45f,
                        center = c3,
                    )

                    // Floating particles
                    val pp = particlePhase.value
                    particles.forEach { pt ->
                        val t = (pp * pt.speed + pt.phaseOffset) % 1f
                        val py = h * (1f - t)
                        val px = w * pt.x + w * 0.02f * sin((t * 2f * PI).toFloat() + pt.phaseOffset)
                        val alpha =
                            when {
                                t < 0.15f -> t / 0.15f
                                t > 0.85f -> (1f - t) / 0.15f
                                else -> 1f
                            } * 0.12f
                        drawCircle(
                            color = Color(0xFF57CBDE).copy(alpha = alpha),
                            radius = pt.size.dp.toPx(),
                            center = Offset(px, py),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val displayName = gameName.ifEmpty { stringResource(R.string.preloader_default_name) }
            val platRes = platformStringRes(platform)
            val platColor = platformColor(platform)

            Box(
                modifier =
                    Modifier
                        .height(if (stableLaunchLayout) 72.dp else 36.dp)
                        .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (stableLaunchLayout || containerName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier =
                        Modifier
                            .height(22.dp)
                            .padding(horizontal = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = containerName.ifEmpty { " " },
                        fontSize = 16.sp,
                        fontFamily = InterFont,
                        color = TextDim,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (stableLaunchLayout || platRes != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (platRes != null) {
                    val badgeShape = RoundedCornerShape(20.dp)
                    val shimVal = shimmerPhase.value
                    val glowAlpha = pulse.value

                    Box(
                        modifier =
                            Modifier
                                .height(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .drawBehind {
                                        drawCircle(
                                            brush =
                                                Brush.radialGradient(
                                                    colors =
                                                        listOf(
                                                            platColor.copy(alpha = 0.08f * glowAlpha),
                                                            platColor.copy(alpha = 0.02f * glowAlpha),
                                                            Color.Transparent,
                                                        ),
                                                    center = Offset(size.width / 2f, size.height / 2f),
                                                    radius = size.width * 0.45f,
                                                ),
                                            radius = size.width * 0.45f,
                                            center = Offset(size.width / 2f, size.height / 2f),
                                        )
                                    }.clip(badgeShape)
                                    .background(platColor.copy(alpha = 0.15f))
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush =
                                                Brush.linearGradient(
                                                    colors =
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.White.copy(alpha = 0.15f),
                                                            Color.Transparent,
                                                        ),
                                                    start = Offset(size.width * shimVal, 0f),
                                                    end = Offset(size.width * (shimVal + 0.4f), size.height),
                                                ),
                                            blendMode = BlendMode.SrcAtop,
                                        )
                                    }.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(platRes),
                                fontSize = 14.sp,
                                fontFamily = InterFont,
                                color = platColor,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier =
                    Modifier
                        .width(320.dp)
                        .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Current step text — fixed to 2-line height to avoid layout shift
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontFamily = InterFont,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator
                if (isIndeterminate) {
                    val orb = orbitalPhase.value
                    Canvas(
                        modifier = Modifier.size(52.dp),
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.width * 0.38f
                        val dotCount = 7
                        for (i in 0 until dotCount) {
                            val angle = orb - (i * 0.65f)
                            val dx = cx + radius * cos(angle)
                            val dy = cy + radius * sin(angle)
                            val fraction = i.toFloat() / (dotCount - 1)
                            val dotRadius = (3.8f - fraction * 2.6f).dp.toPx()
                            val alpha = 1f - fraction * 0.85f

                            // Glow behind the lead dot
                            if (i == 0) {
                                drawCircle(
                                    color = IndicatorColor.copy(alpha = 0.15f),
                                    radius = dotRadius * 2.2f,
                                    center = Offset(dx, dy),
                                )
                            }

                            drawCircle(
                                color = IndicatorColor.copy(alpha = alpha),
                                radius = dotRadius,
                                center = Offset(dx, dy),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress / 100f,
                        animationSpec = tween(durationMillis = 300),
                        label = "progress",
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        color = IndicatorColor,
                        trackColor = TrackColor,
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// Java bridge — called from PreloaderDialog.java as:
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
