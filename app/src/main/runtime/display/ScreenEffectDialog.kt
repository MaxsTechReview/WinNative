package com.winlator.cmod.runtime.display
import android.app.Dialog
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.runtime.display.renderer.GLRenderer
import com.winlator.cmod.runtime.display.renderer.effects.CRTEffect
import com.winlator.cmod.runtime.display.renderer.effects.ColorEffect
import com.winlator.cmod.runtime.display.renderer.effects.FXAAEffect
import com.winlator.cmod.runtime.display.renderer.effects.NTSCCombinedEffect
import com.winlator.cmod.runtime.display.renderer.effects.ToonEffect
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.ContentDialog
import com.winlator.cmod.shared.util.KeyValueSet

class ScreenEffectDialog(
    private val activity: XServerDisplayActivity,
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    private val dialog: Dialog

    // Compose state
    val brightness = mutableFloatStateOf(0f)
    val contrast = mutableFloatStateOf(0f)
    val gamma = mutableFloatStateOf(1f)
    val enableFXAA = mutableStateOf(false)
    val enableCRT = mutableStateOf(false)
    val enableToon = mutableStateOf(false)
    val enableNTSC = mutableStateOf(false)
    val profileNames = mutableStateOf<List<String>>(emptyList())
    val selectedProfileIndex = mutableIntStateOf(0)

    var onConfirmCallback: Runnable? = null

    init {
        // Read current state from renderer
        val renderer = activity.xServerView?.renderer
        if (renderer != null) {
            val colorEffect = renderer.effectComposer?.getEffect(ColorEffect::class.java) as? ColorEffect
            if (colorEffect != null) {
                brightness.floatValue = colorEffect.brightness * 100
                contrast.floatValue = colorEffect.contrast * 100
                gamma.floatValue = colorEffect.gamma
            }
            enableFXAA.value = renderer.effectComposer?.getEffect(FXAAEffect::class.java) != null
            enableCRT.value = renderer.effectComposer?.getEffect(CRTEffect::class.java) != null
            enableToon.value = renderer.effectComposer?.getEffect(ToonEffect::class.java) != null
            enableNTSC.value = renderer.effectComposer?.getEffect(NTSCCombinedEffect::class.java) != null
        }

        loadProfileList(activity.screenEffectProfile)

        // Build dialog
        dialog =
            Dialog(activity, R.style.ContentDialog).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(true)
                setCanceledOnTouchOutside(true)
                window?.apply {
                    setBackgroundDrawableResource(android.R.color.transparent)
                    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                }
            }

        val composeView =
            ComposeView(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setContent {
                    WinNativeTheme {
                        ScreenEffectDialogContent(
                            state =
                                ScreenEffectState(
                                    brightness = brightness.floatValue,
                                    contrast = contrast.floatValue,
                                    gamma = gamma.floatValue,
                                    enableFXAA = enableFXAA.value,
                                    enableCRT = enableCRT.value,
                                    enableToon = enableToon.value,
                                    enableNTSC = enableNTSC.value,
                                    profileNames = profileNames.value,
                                    selectedProfileIndex = selectedProfileIndex.intValue,
                                ),
                            onBrightnessChange = { brightness.floatValue = it },
                            onContrastChange = { contrast.floatValue = it },
                            onGammaChange = { gamma.floatValue = it },
                            onFXAAChange = { enableFXAA.value = it },
                            onCRTChange = { enableCRT.value = it },
                            onToonChange = { enableToon.value = it },
                            onNTSCChange = { enableNTSC.value = it },
                            onProfileSelected = { onProfileSelected(it) },
                            onAddProfile = { promptAddProfile() },
                            onRemoveProfile = { promptDeleteProfile() },
                            onReset = { resetSettings() },
                            onCancel = { dismiss() },
                            onConfirm = { onConfirm() },
                        )
                    }
                }
            }
        dialog.setContentView(composeView)
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            val dm = activity.resources.displayMetrics
            setLayout(dm.widthPixels, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    fun dismiss() {
        AppUtils.hideKeyboard(activity)
        dialog.dismiss()
    }

    // -- Profile management --

    private fun loadProfileList(selectedName: String?) {
        val profiles = LinkedHashSet(preferences.getStringSet("screen_effect_profiles", LinkedHashSet()) ?: LinkedHashSet())
        val items = mutableListOf("-- ${activity.getString(R.string.session_effects_default_profile)} --")
        var selectedPosition = 0
        var position = 1
        for (profile in profiles) {
            val name = profile.split(":")[0]
            items.add(name)
            if (name == selectedName) selectedPosition = position
            position++
        }
        profileNames.value = items
        selectedProfileIndex.intValue = selectedPosition
    }

    private fun onProfileSelected(index: Int) {
        selectedProfileIndex.intValue = index
        if (index > 0) {
            loadProfile(profileNames.value[index])
        }
    }

    private fun loadProfile(name: String) {
        val profiles = LinkedHashSet(preferences.getStringSet("screen_effect_profiles", LinkedHashSet()) ?: LinkedHashSet())
        for (profile in profiles) {
            val parts = profile.split(":")
            if (parts[0] == name && parts.size > 1 && parts[1].isNotEmpty()) {
                val settings = KeyValueSet(parts[1])
                brightness.floatValue = settings.getFloat("brightness", 0f)
                contrast.floatValue = settings.getFloat("contrast", 0f)
                gamma.floatValue = settings.getFloat("gamma", 1.0f)
                enableFXAA.value = settings.getBoolean("fxaa", false)
                enableCRT.value = settings.getBoolean("crt_shader", false)
                enableToon.value = settings.getBoolean("toon_shader", false)
                enableNTSC.value = settings.getBoolean("ntsc_effect", false)
                return
            }
        }
    }

    private fun promptAddProfile() {
        ContentDialog.prompt(activity, R.string.session_effects_confirm_add_profile, null) { name ->
            addProfile(name)
        }
    }

    private fun addProfile(newName: String) {
        val profiles = LinkedHashSet(preferences.getStringSet("screen_effect_profiles", LinkedHashSet()) ?: LinkedHashSet())
        if (profiles.any { it.split(":")[0] == newName }) return
        profiles.add("$newName:")
        preferences.edit().putStringSet("screen_effect_profiles", profiles).apply()
        loadProfileList(newName)
    }

    private fun promptDeleteProfile() {
        if (selectedProfileIndex.intValue > 0) {
            val selectedProfile = profileNames.value[selectedProfileIndex.intValue]
            ContentDialog.confirm(activity, R.string.input_controls_editor_confirm_remove_profile) {
                removeProfile(selectedProfile)
            }
        } else {
            AppUtils.showToast(activity, R.string.input_controls_editor_no_profile_selected)
        }
    }

    private fun removeProfile(targetName: String) {
        val profiles = LinkedHashSet(preferences.getStringSet("screen_effect_profiles", LinkedHashSet()) ?: LinkedHashSet())
        profiles.removeIf { it.split(":")[0] == targetName }
        preferences.edit().putStringSet("screen_effect_profiles", profiles).apply()
        loadProfileList(null)
        resetSettings()
    }

    fun resetSettings() {
        brightness.floatValue = 0f
        contrast.floatValue = 0f
        gamma.floatValue = 1.0f
        enableFXAA.value = false
        enableCRT.value = false
        enableToon.value = false
        enableNTSC.value = false
    }

    private fun saveProfile() {
        if (selectedProfileIndex.intValue > 0) {
            val selectedProfile = profileNames.value[selectedProfileIndex.intValue]
            val oldProfiles = LinkedHashSet(preferences.getStringSet("screen_effect_profiles", LinkedHashSet()) ?: LinkedHashSet())
            val newProfiles = LinkedHashSet<String>()
            val settings = KeyValueSet()
            settings.put("brightness", brightness.floatValue)
            settings.put("contrast", contrast.floatValue)
            settings.put("gamma", gamma.floatValue)
            settings.put("fxaa", enableFXAA.value)
            settings.put("crt_shader", enableCRT.value)
            settings.put("toon_shader", enableToon.value)
            settings.put("ntsc_effect", enableNTSC.value)

            for (profile in oldProfiles) {
                val parts = profile.split(":")
                if (parts[0] == selectedProfile) {
                    newProfiles.add("$selectedProfile:$settings")
                } else {
                    newProfiles.add(profile)
                }
            }
            preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply()
            activity.screenEffectProfile = selectedProfile
        }
    }

    private fun onConfirm() {
        Log.d(TAG, "Confirm clicked. Saving profile and applying effects.")
        saveProfile()

        val renderer = activity.xServerView?.renderer
        if (renderer == null || renderer.effectComposer == null) {
            Log.e(TAG, "Renderer or EffectComposer is null!")
            dismiss()
            return
        }

        var colorEffect = renderer.effectComposer.getEffect(ColorEffect::class.java) as? ColorEffect
        var fxaaEffect = renderer.effectComposer.getEffect(FXAAEffect::class.java) as? FXAAEffect
        var crtEffect = renderer.effectComposer.getEffect(CRTEffect::class.java) as? CRTEffect
        var toonEffect = renderer.effectComposer.getEffect(ToonEffect::class.java) as? ToonEffect
        var ntscEffect = renderer.effectComposer.getEffect(NTSCCombinedEffect::class.java) as? NTSCCombinedEffect

        applyEffects(colorEffect, renderer, fxaaEffect, crtEffect, toonEffect, ntscEffect)

        onConfirmCallback?.run()
        dismiss()
    }

    fun applyEffects(
        colorEffect: ColorEffect?,
        renderer: GLRenderer,
        fxaaEffect: FXAAEffect?,
        crtEffect: CRTEffect?,
        toonEffect: ToonEffect?,
        ntscEffect: NTSCCombinedEffect?,
    ) {
        Log.d(TAG, "applyEffects() called")

        val brightnessVal = brightness.floatValue
        val contrastVal = contrast.floatValue
        val gammaVal = gamma.floatValue

        val composer =
            renderer.effectComposer ?: run {
                Log.e(TAG, "EffectComposer is null!")
                return
            }

        // ColorEffect
        var ce = colorEffect ?: ColorEffect()
        if (brightnessVal == 0f && contrastVal == 0f && gammaVal == 1.0f) {
            composer.removeEffect(ce)
        } else {
            ce.brightness = brightnessVal / 100f
            ce.contrast = contrastVal / 100f
            ce.gamma = gammaVal
            composer.addEffect(ce)
        }

        // FXAAEffect
        if (enableFXAA.value) {
            if (fxaaEffect == null) composer.addEffect(FXAAEffect())
        } else {
            fxaaEffect?.let { composer.removeEffect(it) }
        }

        // CRTEffect
        if (enableCRT.value) {
            if (crtEffect == null) composer.addEffect(CRTEffect())
        } else {
            crtEffect?.let { composer.removeEffect(it) }
        }

        // ToonEffect
        if (enableToon.value) {
            if (toonEffect == null) composer.addEffect(ToonEffect())
        } else {
            toonEffect?.let { composer.removeEffect(it) }
        }

        // NTSCCombinedEffect
        if (enableNTSC.value) {
            if (ntscEffect == null) composer.addEffect(NTSCCombinedEffect())
        } else {
            ntscEffect?.let { composer.removeEffect(it) }
        }

        saveProfile()
        Log.d(TAG, "Profile saved after applying effects.")
    }

    companion object {
        private const val TAG = "ScreenEffectDialog"
    }
}
