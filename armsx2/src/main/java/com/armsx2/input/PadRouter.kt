package com.armsx2.input

import android.view.InputDevice

object PadRouter {
    private val slots = IntArray(8) { -1 }
    @Volatile private var pad2Enabled = false

    @Volatile var multitapEnabled = false

    @Volatile var onPlayer2Joined: (() -> Unit)? = null

    fun reset() {
        for (i in slots.indices) slots[i] = -1
        pad2Enabled = false
    }

    fun coopActive(): Boolean = slots[1] != -1

    fun deviceIdForPort(port: Int): Int =
        if (port in slots.indices) slots[port] else -1

    fun portForDevice(deviceId: Int): Int {
        if (deviceId < 0) return 0
        for (i in slots.indices) if (slots[i] == deviceId) return i
        val src = InputDevice.getDevice(deviceId)?.sources ?: 0
        val isPad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isPad) return 0
        val maxSlots = if (multitapEnabled) 8 else 2
        for (i in 0 until maxSlots) {
            if (slots[i] == -1) {
                slots[i] = deviceId
                if (i == 1 && !pad2Enabled) { pad2Enabled = true; onPlayer2Joined?.invoke() }
                return i
            }
        }
        return 0
    }
}
