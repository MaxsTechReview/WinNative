package com.winlator.cmod.widget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A modern Jetpack Compose Modifier that creates the same rotating Chasing Border Outline.
 * 
 * Simply append `.chasingBorder(isFocused = myVariable)` to any Compose UI Element.
 */
fun Modifier.chasingBorder(
    isFocused: Boolean = true,
    cornerRadius: Dp = 8.dp,
    borderWidth: Dp = 1.5.dp,
    animationDurationMs: Int = 3000,
    glowColor: Color = Color(0x1A00E5FF),
    primaryColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFFFFFFFF)
): Modifier = composed {
    // Optimization: If it isn't focused, do no drawing and run no animations
    if (!isFocused) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "ChasingBorderAnim")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ChasingBorderAngle"
    )

    this.drawWithCache {
        val transparentColor = primaryColor.copy(alpha = 0f)

        // We use Android's SweepGradient here specifically because standard 
        // Compose SweepGradients cannot be easily matrix-rotated on the fly
        val sweepGradient = android.graphics.SweepGradient(
            size.width / 2f,
            size.height / 2f,
            intArrayOf(
                transparentColor.toArgb(), 
                primaryColor.toArgb(), 
                secondaryColor.toArgb(), 
                transparentColor.toArgb()
            ),
            floatArrayOf(0.0f, 0.75f, 0.95f, 1.0f)
        )

        // Rotate the gradient shader matrix
        val matrix = android.graphics.Matrix()
        matrix.setRotate(angle, size.width / 2f, size.height / 2f)
        sweepGradient.setLocalMatrix(matrix)

        val brush = ShaderBrush(sweepGradient)
        val strokeWidthPx = borderWidth.toPx()
        val cornerRadiusPx = cornerRadius.toPx()

        onDrawBehind {
            val drawRectSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            val drawRectTopLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            
            // Draw underlying subtle fill glow
            drawRoundRect(
                color = glowColor,
                topLeft = drawRectTopLeft,
                size = drawRectSize,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )

            // Draw chasing border mask
            drawRoundRect(
                brush = brush,
                topLeft = drawRectTopLeft,
                size = drawRectSize,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = strokeWidthPx)
            )
        }
    }
}
