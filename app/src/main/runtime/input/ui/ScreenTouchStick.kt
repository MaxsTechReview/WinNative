package com.winlator.cmod.runtime.input.ui

import android.content.Context
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import com.winlator.cmod.runtime.display.xserver.XServer

class ScreenTouchStick(context: Context, private val xServer: XServer) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var activePointerId = -1
    private var originX = 0f
    private var originY = 0f
    private var sensitivity = DEFAULT_SENSITIVITY
    private var surfaceMin = DEFAULT_SURFACE_MIN

    fun setSurfaceSize(w: Int, h: Int) {
        if (w > 0 && h > 0) surfaceMin = Math.min(w, h).toFloat()
    }

    fun onTouch(event: MotionEvent): Boolean {
        if (activePointerId != -1
            && event.actionMasked != MotionEvent.ACTION_CANCEL
            && event.findPointerIndex(activePointerId) < 0) {
            releaseAll()
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == -1) {
                    val index = event.actionIndex
                    activePointerId = event.getPointerId(index)
                    originX = event.getX(index)
                    originY = event.getY(index)
                    sensitivity = readSensitivity()
                    push(0f, 0f)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId != -1) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        val offsetX = event.getX(index) - originX
                        val offsetY = event.getY(index) - originY
                        val distance = Math.hypot(offsetX.toDouble(), offsetY.toDouble()).toFloat()
                        var stickX = 0f
                        var stickY = 0f
                        if (distance > 0f) {
                            val radius = surfaceMin * RADIUS_FRACTION / (sensitivity + STRENGTH_BIAS)
                            val magnitude = Math.min(distance / radius, 1.0f)
                            if (magnitude > DEAD_ZONE) {
                                val scaled = (magnitude - DEAD_ZONE) / (1.0f - DEAD_ZONE)
                                stickX = offsetX / distance * scaled
                                stickY = offsetY / distance * scaled
                            }
                        }
                        push(stickX, stickY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == activePointerId) releaseAll()
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    fun releaseAll() {
        if (activePointerId != -1) {
            activePointerId = -1
            push(0f, 0f)
        }
    }

    private fun push(x: Float, y: Float) {
        xServer.winHandler?.setScreenTouchRightStick(x, y)
    }

    private fun readSensitivity(): Float =
        preferences.getFloat("screen_touch_rs_sensitivity", DEFAULT_SENSITIVITY)
            .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

    companion object {
        private const val DEFAULT_SENSITIVITY = 1.25f
        private const val MIN_SENSITIVITY = 0.25f
        private const val MAX_SENSITIVITY = 2.0f
        private const val STRENGTH_BIAS = 0.5f
        private const val RADIUS_FRACTION = 0.20f
        private const val DEAD_ZONE = 0.08f
        private const val DEFAULT_SURFACE_MIN = 1080f
    }
}
