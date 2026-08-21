package com.winlator.cmod.shared.android

import android.app.Activity
import android.content.pm.ActivityInfo
import com.winlator.cmod.feature.stores.steam.utils.PrefManager

interface LandscapeOnlyActivity

interface SelfManagedOrientationActivity

object OrientationLock {
    private val listeners = mutableListOf<() -> Unit>()

    var forceLandscape: Boolean
        get() = PrefManager.libraryForceLandscape
        set(value) {
            if (PrefManager.libraryForceLandscape == value) return
            PrefManager.libraryForceLandscape = value
            listeners.toList().forEach { it() }
        }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}

fun Activity.applyOrientationLock() {
    if (this is SelfManagedOrientationActivity) return
    val requested =
        if (this is LandscapeOnlyActivity || OrientationLock.forceLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    if (requestedOrientation != requested) requestedOrientation = requested
}
