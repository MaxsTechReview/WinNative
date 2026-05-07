package com.winlator.cmod.runtime.input

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.winlator.cmod.R
import com.winlator.cmod.runtime.input.controls.ControlsProfile
import com.winlator.cmod.runtime.input.controls.InputControlsManager
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.android.FixedFontScaleAppCompatActivity
import com.winlator.cmod.shared.theme.WinNativeTheme

class ControlsEditorActivity : FixedFontScaleAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUtils.hideSystemUI(this)

        val profileId = intent.getIntExtra("profile_id", 0)
        val profile = InputControlsManager.loadProfile(
            this,
            ControlsProfile.getProfileFile(this, profileId),
        )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                    AppUtils.applyCloseActivityTransition(
                        this@ControlsEditorActivity,
                        R.anim.slide_in_down,
                        R.anim.slide_out_up,
                    )
                }
            },
        )

        setContent {
            WinNativeTheme {
                ControlsEditorScreen(
                    profile = profile,
                    onClose = {
                        finish()
                        AppUtils.applyCloseActivityTransition(
                            this@ControlsEditorActivity,
                            R.anim.slide_in_down,
                            R.anim.slide_out_up,
                        )
                    },
                )
            }
        }
    }
}
