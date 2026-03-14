package com.winlator.cmod.widget

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose modifier that delegates to [ChasingBorderDrawable] — single source of truth
 * for the animated chasing border used in both View and Compose UIs.
 */
fun Modifier.chasingBorder(
    isFocused: Boolean = true,
    cornerRadius: Dp = 8.dp,
    borderWidth: Dp = 4.dp,
    animationDurationMs: Long = 5000L
): Modifier = composed {
    if (!isFocused) return@composed this

    val density = LocalDensity.current.density
    val drawable = remember(cornerRadius, borderWidth, density, animationDurationMs) {
        ChasingBorderDrawable(
            cornerRadiusDp = cornerRadius.value,
            borderWidthDp = borderWidth.value,
            density = density,
            animationDurationMs = animationDurationMs
        )
    }

    // Start/stop animation based on visibility
    drawable.setVisible(true, true)

    this.drawWithContent {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (drawable.bounds.width() != w || drawable.bounds.height() != h) {
            drawable.setBounds(0, 0, w, h)
        }
        drawContent()
        drawContext.canvas.nativeCanvas.let { canvas ->
            drawable.draw(canvas)
        }
    }
}
