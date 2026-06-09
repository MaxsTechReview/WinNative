package com.winlator.cmod.runtime.input.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import com.winlator.cmod.runtime.display.xserver.XServer

// Drives the gamepad right stick from one finger (mouse-like), GameHub-style velocity curve.
class ScreenTouchStick(context: Context, private val xServer: XServer) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private var activePointerId = -1
    private var lastX = 0f
    private var lastY = 0f
    private var sensitivity = DEFAULT_SENSITIVITY
    private val recenterRunnable = Runnable { push(0f, 0f) }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == -1) {
                    val index = event.actionIndex
                    activePointerId = event.getPointerId(index)
                    lastX = event.getX(index)
                    lastY = event.getY(index)
                    sensitivity = readSensitivity()
                    push(0f, 0f)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId != -1) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        val x = event.getX(index)
                        val y = event.getY(index)
                        val ax = curve(x - lastX)
                        val ay = curve(-(y - lastY))
                        lastX = x
                        lastY = y
                        push(ax, ay)
                        handler.removeCallbacks(recenterRunnable)
                        handler.postDelayed(recenterRunnable, RECENTER_TIMEOUT_MS)
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
        handler.removeCallbacks(recenterRunnable)
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

    private fun curve(delta: Float): Float {
        val scaled = delta * sensitivity / REFERENCE_DELTA
        if (Math.abs(scaled) < OUTPUT_DEADZONE) return 0f
        val magnitude = Math.min(Math.abs(scaled), 1.0f)
        val out = (magnitude * 0.82f + 0.18f) * Math.signum(scaled)
        return out.coerceIn(-1.0f, 1.0f)
    }

    companion object {
        private const val DEFAULT_SENSITIVITY = 1.25f
        private const val MIN_SENSITIVITY = 0.25f
        private const val MAX_SENSITIVITY = 3.0f
        private const val REFERENCE_DELTA = 8.0f
        private const val OUTPUT_DEADZONE = 0.18f
        private const val RECENTER_TIMEOUT_MS = 80L
    }
}
