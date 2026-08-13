package com.armsx2.ui

import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.config.ConfigStore
import com.armsx2.config.Settings
import com.armsx2.config.SettingsScope
import kr.co.iefriends.pcsx2.NativeApp

object InGameOverlay {
    val settingsState = mutableStateOf(Settings())
    val settingsScope = mutableStateOf(SettingsScope.Global)
    val currentSerial = mutableStateOf<String?>(null)
    val hardcoreOn = mutableStateOf(false)
    val frameLimitOn = mutableStateOf(true)

    val osdHidden = mutableStateOf(false)

    fun saveSettings(updated: Settings) {
        val previous = settingsState.value
        settingsState.value = updated
        ConfigStore.save(settingsScope.value, currentSerial.value, updated, previous)
        frameLimitOn.value = updated.frameLimitEnable

        if (MainActivityRuntime.nativeReady.value) {
            runCatching {
                if (previous.frameLimitEnable != updated.frameLimitEnable) {
                    NativeApp.setSetting("EmuCore/GS", "FrameLimitEnable", "bool", updated.frameLimitEnable.toString())
                    NativeApp.speedhackLimitermode(if (updated.frameLimitEnable) 0 else 3)
                    MainActivityRuntime.fastForwardToggleActive = false
                }
                if (previous.upscaleFloat != updated.upscaleFloat &&
                    MainActivityRuntime.eState.value != EmuState.STOPPED
                ) {
                    NativeApp.renderUpscalemultiplier(updated.upscaleFloat.coerceIn(0.25f, 8.0f))
                    MainActivityRuntime.upscale.value = updated.upscaleFloat.coerceIn(0.25f, 8.0f)
                }
                if (MainActivityRuntime.eState.value != EmuState.STOPPED) updated.applyTo()
            }
        }
    }

    fun toggleOsd() {
        val hide = !osdHidden.value
        osdHidden.value = hide
        if (hide) {
            NativeApp.osdApplyFlags(false, false, false, false, false, false, false, false, false, false, false, false)
        } else {
            val s = settingsState.value
            NativeApp.osdApplyFlags(
                s.osdShowFps, s.osdShowVps, s.osdShowSpeed, s.osdShowCpu, s.osdShowGpu,
                s.osdShowResolution, s.osdShowGsStats, s.osdShowFrameTimes, s.osdShowHardwareInfo,
                s.osdShowVersion, s.osdShowSettings, s.osdShowInputs,
            )
        }
    }
}
