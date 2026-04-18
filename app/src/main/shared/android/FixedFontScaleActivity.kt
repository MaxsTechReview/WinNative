package com.winlator.cmod.shared.android

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity

private const val LOCKED_APP_FONT_SCALE = 1f

private fun lockFontScale(configuration: Configuration?): Configuration? =
    configuration?.let {
        Configuration(it).apply {
            fontScale = LOCKED_APP_FONT_SCALE
        }
    }

private fun lockFontScale(base: Context?): Context? {
    if (base == null) return null
    val configuration = lockFontScale(base.resources.configuration) ?: return base
    return base.createConfigurationContext(configuration)
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
