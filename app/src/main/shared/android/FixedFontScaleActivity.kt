package com.winlator.cmod.shared.android

import android.content.Context
import android.content.res.Configuration
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
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(lockFontScale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(lockFontScale(overrideConfiguration))
    }
}

open class FixedFontScaleComponentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(lockFontScale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(lockFontScale(overrideConfiguration))
    }
}

open class FixedFontScaleFragmentActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(lockFontScale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        super.applyOverrideConfiguration(lockFontScale(overrideConfiguration))
    }
}
