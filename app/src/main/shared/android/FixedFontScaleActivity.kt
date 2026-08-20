package com.winlator.cmod.shared.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity

private const val LOCKED_APP_FONT_SCALE = 1f

private fun fontScaleOnlyConfiguration(): Configuration =
    Configuration().apply {
        fontScale = LOCKED_APP_FONT_SCALE
    }

private fun Configuration.clearDisplayMetricOverrides() {
    orientation = Configuration.ORIENTATION_UNDEFINED
    screenWidthDp = Configuration.SCREEN_WIDTH_DP_UNDEFINED
    screenHeightDp = Configuration.SCREEN_HEIGHT_DP_UNDEFINED
    smallestScreenWidthDp = Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED
    densityDpi = Configuration.DENSITY_DPI_UNDEFINED
    screenLayout = screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK
}

private fun lockFontScale(configuration: Configuration?): Configuration? =
    configuration?.let {
        Configuration(it).apply {
            fontScale = LOCKED_APP_FONT_SCALE
            clearDisplayMetricOverrides()
        }
    }

private fun lockFontScale(base: Context?): Context? {
    if (base == null) return null
    return base.createConfigurationContext(fontScaleOnlyConfiguration())
}

open class FixedFontScaleAppCompatActivity : AppCompatActivity() {
    private val orientationLockListener = { applyOrientationLock() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyOrientationLock()
        super.onCreate(savedInstanceState)
        OrientationLock.addListener(orientationLockListener)
    }

    override fun onDestroy() {
        OrientationLock.removeListener(orientationLockListener)
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(lockFontScale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(lockFontScale(overrideConfiguration))
    }
}

open class FixedFontScaleComponentActivity : ComponentActivity() {
    private val orientationLockListener = { applyOrientationLock() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyOrientationLock()
        super.onCreate(savedInstanceState)
        OrientationLock.addListener(orientationLockListener)
    }

    override fun onDestroy() {
        OrientationLock.removeListener(orientationLockListener)
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(lockFontScale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(lockFontScale(overrideConfiguration))
    }
}

open class FixedFontScaleFragmentActivity : FragmentActivity() {
    private val orientationLockListener = { applyOrientationLock() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyOrientationLock()
        super.onCreate(savedInstanceState)
        OrientationLock.addListener(orientationLockListener)
    }

    override fun onDestroy() {
        OrientationLock.removeListener(orientationLockListener)
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(lockFontScale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(lockFontScale(overrideConfiguration))
    }
}
