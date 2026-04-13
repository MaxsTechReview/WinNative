package com.winlator.cmod.shared.ui.widget
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * Animated border that sweeps a gradient around a rounded rectangle.
 *
 * Uses a SweepGradient shader rotated by a ValueAnimator — single draw call,
 * GPU blended colors, no segment artifacts.
 */
class ChasingBorderDrawable
    @JvmOverloads
    constructor(
        private val cornerRadiusDp: Float,
        private val borderWidthDp: Float,
        private val density: Float,
        private val animationDurationMs: Long = 8200L,
        private val gradientColors: IntArray =
            intArrayOf(
                0xFF2196F3.toInt(), // blue
                0xFF29B6F6.toInt(), // sky blue
                0xFF00E5FF.toInt(), // electric cyan
                0xFF29B6F6.toInt(), // sky blue
                0xFF2196F3.toInt(), // blue (seamless)
            ),
        private val gradientStops: FloatArray =
            floatArrayOf(
                0f,
                0.25f,
                0.50f,
                0.75f,
                1f,
            ),
    ) : Drawable() {
        private val cornerRadius = cornerRadiusDp * density
        private val borderWidth = borderWidthDp * density
        private val drawRect = RectF()
        private val borderPath = Path()
        private val rotationMatrix = Matrix()

        // Current rotation angle in degrees, driven by the animator
        private var rotationDegrees = 0f

        // Stroke paint — shader is set in rebuildShader()
        private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                setShadowLayer(6f * density, 0f, 0f, 0x3029B6F6)
            }

        // Loops 0→360 forever, rotating the sweep gradient
        private val animator =
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = animationDurationMs
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    rotationDegrees = animation.animatedValue as Float
                    invalidateSelf()
                }
            }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            rebuildPath()
            rebuildShader()
        }

        override fun draw(canvas: Canvas) {
            if (bounds.width() <= 0 || bounds.height() <= 0) return

            // Rotate the sweep gradient around the center
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            rotationMatrix.setRotate(rotationDegrees, cx, cy)
            borderPaint.shader?.setLocalMatrix(rotationMatrix)

            canvas.drawPath(borderPath, borderPaint)
        }

        // Build the rounded-rectangle stroke path
        private fun rebuildPath() {
            drawRect.set(bounds)
            drawRect.inset(borderWidth / 2f, borderWidth / 2f)
            borderPath.reset()
            borderPath.addRoundRect(drawRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        // Create the SweepGradient centered on the drawable
        private fun rebuildShader() {
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            borderPaint.shader = SweepGradient(cx, cy, gradientColors, gradientStops)
        }

        override fun setVisible(
            visible: Boolean,
            restart: Boolean,
        ): Boolean {
            val changed = super.setVisible(visible, restart)
            if (visible) {
                if (restart) {
                    animator.cancel()
                    rotationDegrees = 0f
                }
                if (!animator.isRunning) animator.start()
            } else {
                animator.cancel()
            }
            return changed
        }

        override fun setAlpha(alpha: Int) {
            borderPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            borderPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getConstantState(): ConstantState =
            ChasingBorderState(
                cornerRadiusDp,
                borderWidthDp,
                density,
                animationDurationMs,
                gradientColors,
                gradientStops,
            )

        private class ChasingBorderState(
            private val cornerRadiusDp: Float,
            private val borderWidthDp: Float,
            private val density: Float,
            private val animationDurationMs: Long,
            private val gradientColors: IntArray,
            private val gradientStops: FloatArray,
        ) : ConstantState() {
            override fun newDrawable(): Drawable =
                ChasingBorderDrawable(
                    cornerRadiusDp = cornerRadiusDp,
                    borderWidthDp = borderWidthDp,
                    density = density,
                    animationDurationMs = animationDurationMs,
                    gradientColors = gradientColors,
                    gradientStops = gradientStops,
                )

            override fun getChangingConfigurations(): Int = 0
        }
    }
