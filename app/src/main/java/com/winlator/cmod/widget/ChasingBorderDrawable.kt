package com.winlator.cmod.widget

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.max

class ChasingBorderDrawable @JvmOverloads constructor(
    private val cornerRadiusDp: Float,
    private val borderWidthDp: Float,
    private val density: Float,
    private val animationDurationMs: Long = 8200L,
    private val glowColor: Int = 0x1400C7E2,
    private val primaryColor: Int = 0xFF00BDD8.toInt(),
    private val secondaryColor: Int = 0xBFE9F6FA.toInt()
) : Drawable() {

    private val cornerRadius = cornerRadiusDp * density
    private val borderWidth = borderWidthDp * density
    private val drawRect = RectF()
    private val borderPath = Path()
    private val segmentPath = Path()
    private val pathMeasure = PathMeasure()

    private var progress = 0f
    private var pathLength = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x0F06131B
    }

    private val baseBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x3000BFD8
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(7f * density, 0f, 0f, glowColor)
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animationDurationMs
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            progress = animation.animatedValue as Float
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rebuildPath()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 0 || bounds.height() <= 0 || pathLength <= 0f) return

        canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, fillPaint)
        canvas.drawPath(borderPath, baseBorderPaint)

        val stepCount = 120
        val segmentLength = pathLength / stepCount.toFloat()

        for (stepIndex in 0 until stepCount) {
            val segmentStart = stepIndex * segmentLength
            val segmentEnd = if (stepIndex == stepCount - 1) {
                pathLength
            } else {
                (segmentStart + (segmentLength * 1.15f)).coerceAtMost(pathLength)
            }
            val colorProgress = ((stepIndex / stepCount.toFloat()) + progress) % 1f

            borderPaint.color = colorFor(colorProgress)
            borderPaint.strokeWidth = borderWidth

            drawSegment(canvas, segmentStart, segmentEnd)
        }
    }

    private fun rebuildPath() {
        drawRect.set(bounds)
        drawRect.inset(borderWidth / 2f, borderWidth / 2f)

        borderPath.reset()
        borderPath.addRoundRect(drawRect, cornerRadius, cornerRadius, Path.Direction.CW)

        pathMeasure.setPath(borderPath, false)
        pathLength = max(pathMeasure.length, 0f)
    }

    private fun drawSegment(canvas: Canvas, startDistance: Float, endDistance: Float) {
        if (endDistance <= startDistance) return

        segmentPath.rewind()
        pathMeasure.getSegment(startDistance, endDistance, segmentPath, true)
        canvas.drawPath(segmentPath, borderPaint)
    }

    private fun colorFor(stepProgress: Float): Int {
        val stops = floatArrayOf(0f, 0.16f, 0.34f, 0.52f, 0.7f, 0.86f, 1f)
        val colors = intArrayOf(
            0x4D0090A7,
            0x8C00ABC3.toInt(),
            primaryColor,
            0xB373DCEB.toInt(),
            secondaryColor,
            0x8C87DCEB.toInt(),
            0x4D0090A7
        )

        for (index in 0 until stops.lastIndex) {
            val startStop = stops[index]
            val endStop = stops[index + 1]
            if (stepProgress <= endStop) {
                val localT = ((stepProgress - startStop) / (endStop - startStop)).coerceIn(0f, 1f)
                return ColorUtils.blendARGB(colors[index], colors[index + 1], localT)
            }
        }

        return colors.last()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) {
            if (restart) {
                animator.cancel()
                progress = 0f
            }
            if (!animator.isRunning) animator.start()
        } else {
            animator.cancel()
        }
        return changed
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        baseBorderPaint.alpha = alpha
        borderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        baseBorderPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState {
        return ChasingBorderState(
            cornerRadiusDp = cornerRadiusDp,
            borderWidthDp = borderWidthDp,
            density = density,
            animationDurationMs = animationDurationMs,
            glowColor = glowColor,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor
        )
    }

    private class ChasingBorderState(
        private val cornerRadiusDp: Float,
        private val borderWidthDp: Float,
        private val density: Float,
        private val animationDurationMs: Long,
        private val glowColor: Int,
        private val primaryColor: Int,
        private val secondaryColor: Int
    ) : ConstantState() {
        override fun newDrawable(): Drawable {
            return ChasingBorderDrawable(
                cornerRadiusDp = cornerRadiusDp,
                borderWidthDp = borderWidthDp,
                density = density,
                animationDurationMs = animationDurationMs,
                glowColor = glowColor,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor
            )
        }

        override fun getChangingConfigurations(): Int = 0
    }
}
