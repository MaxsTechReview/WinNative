package com.winlator.cmod.core

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
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

// State holder — Java-friendly mutable properties
class PreloaderDialogState {
    val text = mutableStateOf("")
    val isIndeterminate = mutableStateOf(true)
    val progress = mutableIntStateOf(0)

    fun setText(value: String) { text.value = value }
    fun setIndeterminate(value: Boolean) { isIndeterminate.value = value }
    fun setProgress(value: Int) { progress.intValue = value }
}

// Colors matching the app theme
private val BgDark = Color(0xFF171A1C)
private val TextPrimary = Color(0xFFF5F9FF)
private val TextSecondary = Color(0xFF9CB0C7)
private val TrackColor = Color(0xFF252A2E)
private val IndicatorColor = Color(0xFF8A9BB0)
private val Scrim = Color(0x80000000)

// Gradient: blue → cyan → blue (clean, no dark spots or white flash)
private val BorderColors = intArrayOf(
    0xFF2196F3.toInt(),  // blue
    0xFF29B6F6.toInt(),  // sky blue
    0xFF00E5FF.toInt(),  // electric cyan
    0xFF29B6F6.toInt(),  // sky blue
    0xFF2196F3.toInt()   // blue (seamless)
)
private val BorderStops = floatArrayOf(
    0f, 0.25f, 0.50f, 0.75f, 1f
)

private val InterFont = FontFamily(Font(R.font.inter_medium))

@Composable
fun PreloaderDialogContent(state: PreloaderDialogState) {
    val text by state.text
    val isIndeterminate by state.isIndeterminate
    val progress by state.progress

    val infiniteTransition = rememberInfiniteTransition(label = "border")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )

    val shape = RoundedCornerShape(16.dp)
    val borderWidth = 1.5.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .drawWithCache {
                    // Create SweepGradient centered on this component
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val shader = SweepGradient(cx, cy, BorderColors, BorderStops)
                    val matrix = Matrix()
                    val brush = ShaderBrush(shader)
                    val bw = borderWidth.toPx()
                    val cr = CornerRadius(16.dp.toPx())

                    onDrawWithContent {
                        drawContent()
                        // Rotate only the shader matrix, not the rect
                        matrix.setRotate(angle, cx, cy)
                        shader.setLocalMatrix(matrix)
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(bw / 2, bw / 2),
                            size = Size(size.width - bw, size.height - bw),
                            cornerRadius = cr,
                            style = Stroke(width = bw, cap = StrokeCap.Round)
                        )
                    }
                }
                .clip(shape)
                .background(BgDark)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WinNative",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFont,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = text,
                fontSize = 14.sp,
                fontFamily = InterFont,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isIndeterminate) {
                LinearProgressIndicator(
                    modifier = Modifier.width(292.dp).height(6.dp),
                    color = IndicatorColor,
                    trackColor = TrackColor,
                    strokeCap = StrokeCap.Round
                )
            } else {
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress / 100f,
                    animationSpec = tween(durationMillis = 300),
                    label = "progress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.width(292.dp).height(6.dp),
                    color = IndicatorColor,
                    trackColor = TrackColor,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "$progress%",
                    fontSize = 13.sp,
                    fontFamily = InterFont,
                    color = TextPrimary
                )
            }
        }
    }
}

// Java bridge — called from PreloaderDialog.java as:
// PreloaderDialogContentKt.setupPreloaderComposeView(composeView, state, activity)
fun setupPreloaderComposeView(
    composeView: ComposeView,
    state: PreloaderDialogState,
    activity: android.app.Activity
) {
    if (activity is androidx.lifecycle.LifecycleOwner) {
        composeView.setViewTreeLifecycleOwner(activity)
    }
    if (activity is androidx.savedstate.SavedStateRegistryOwner) {
        composeView.setViewTreeSavedStateRegistryOwner(activity)
    }
    composeView.setContent {
        PreloaderDialogContent(state)
    }
}
