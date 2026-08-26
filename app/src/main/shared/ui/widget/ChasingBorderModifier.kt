package com.winlator.cmod.shared.ui.widget

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winlator.cmod.shared.theme.WinNativeAccent

/**
 * Compose modifier that draws a flat, single-color focus border.
 *
 * Previously this animated a rotating multi-color SweepGradient on an
 * infinite transition, which meant every focused item on every screen
 * (drawers, settings nav, setup wizard, retro menus, the main hub, etc.)
 * kept invalidating and redrawing on every frame for as long as it stayed
 * focused. That's wasted GPU/CPU work in an app that is already
 * GPU/CPU-bound (Wine, GPU-accelerated emulation), and a busy animated
 * rainbow border doesn't fit a minimal look anyway.
 *
 * This version draws one flat accent-colored stroke. drawWithCache only
 * re-runs when the drawn size actually changes, so a focused item now
 * costs a single draw call instead of a perpetual animation loop.
 *
 * Signature kept identical to the previous implementation (including the
 * now-unused `paused` / `animationDurationMs` params) so every existing
 * call site keeps compiling unchanged.
 */
fun Modifier.chasingBorder(
    isFocused: Boolean = true,
    paused: Boolean = false,
    cornerRadius: Dp = 8.dp,
    borderWidth: Dp = 4.dp,
    animationDurationMs: Int = 5000,
): Modifier =
    composed {
        if (!isFocused) return@composed this

        val density = LocalDensity.current.density
        val cornerRadiusPx = cornerRadius.value * density
        val borderWidthPx = borderWidth.value * density
        val borderColorArgb = WinNativeAccent.toArgb()

        drawWithCache {
            val w = size.width
            val h = size.height

            if (w <= 0f || h <= 0f) {
                onDrawWithContent { drawContent() }
            } else {
                // Native round-rect primitive keeps the corner curve in Skia's
                // optimized AA pipeline, same as before - just no shader/matrix
                // and nothing animated, so this only redraws when size changes.
                val inset = borderWidthPx / 2f
                val rect = RectF(inset, inset, w - inset, h - inset)
                val strokeCornerRadius = (cornerRadiusPx - inset).coerceAtLeast(0f)
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeWidth = borderWidthPx
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        color = borderColorArgb
                    }

                onDrawWithContent {
                    drawContent()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        rect,
                        strokeCornerRadius,
                        strokeCornerRadius,
                        paint,
                    )
                }
            }
        }
    }
