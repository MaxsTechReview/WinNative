package com.winlator.cmod.runtime.input
import android.view.InputDevice

object ControllerHelper {
    private const val SONY_VENDOR_ID = 0x054C

    fun isControllerConnected(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            val sources = device.sources
            if (!device.isVirtual &&
                (
                    (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                        (
                            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                                (sources and InputDevice.SOURCE_MOUSE) == 0
                        )
                )
            ) {
                return true
            }
        }
        return false
    }

    fun isPlayStationController(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            if (isPlayStationDevice(device)) return true
        }
        return false
    }

    fun isPlayStationDevice(device: InputDevice): Boolean {
        if (device.vendorId == SONY_VENDOR_ID) return true
        val name = device.name.lowercase()
        return name.contains("dualshock") || name.contains("playstation") ||
            name.contains("dualsense") || name.contains("wireless controller")
    }

    fun isPlayStationControllerById(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        return isPlayStationDevice(device)
    }
}
