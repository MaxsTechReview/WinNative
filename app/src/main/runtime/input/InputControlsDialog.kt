package com.winlator.cmod.runtime.input
import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme

class InputControlsDialog(
    private val activity: Activity,
) {
    private val dialog: Dialog

    // Compose state
    val profileNames = mutableStateOf<List<String>>(emptyList())
    val selectedProfileIndex = mutableIntStateOf(0)
    val showTouchscreenControls = mutableStateOf(false)
    val tapToClickEnabled = mutableStateOf(true)
    val overlayOpacity = mutableStateOf(0.4f)
    val touchscreenHaptics = mutableStateOf(false)
    val gamepadVibration = mutableStateOf(true)

    var onConfirmCallback: Runnable? = null
    var onCancelCallback: Runnable? = null
    var onSettingsClickCallback: Runnable? = null

    init {
        dialog =
            Dialog(activity, R.style.ContentDialog).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(true)
                setCanceledOnTouchOutside(false)
                setOwnerActivity(activity)
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
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setViewTreeLifecycleOwner(activity as LifecycleOwner)
                setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)
                setContent {
                    WinNativeTheme {
                        InputControlsDialogContent(
                            state =
                                InputControlsState(
                                    profileNames = profileNames.value,
                                    selectedProfileIndex = selectedProfileIndex.intValue,
                                    showTouchscreenControls = showTouchscreenControls.value,
                                    tapToClickEnabled = tapToClickEnabled.value,
                                    overlayOpacity = overlayOpacity.value,
                                    touchscreenHaptics = touchscreenHaptics.value,
                                    gamepadVibration = gamepadVibration.value,
                                ),
                            onProfileSelected = { index ->
                                selectedProfileIndex.intValue = index
                            },
                            onSettingsClick = { onSettingsClickCallback?.run() },
                            onShowTouchscreenControlsChange = { showTouchscreenControls.value = it },
                            onTapToClickChange = { tapToClickEnabled.value = it },
                            onOverlayOpacityChange = { overlayOpacity.value = it },
                            onTouchscreenHapticsChange = { touchscreenHaptics.value = it },
                            onGamepadVibrationChange = { gamepadVibration.value = it },
                            onCancel = {
                                onCancelCallback?.run()
                                dismiss()
                            },
                            onConfirm = {
                                onConfirmCallback?.run()
                                dismiss()
                            },
                        )
                    }
                }
            }
        dialog.setContentView(composeView)

        // Auto-dismiss when activity is destroyed to prevent window leaks
        (activity as LifecycleOwner).lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dialog.isShowing) dialog.dismiss()
                }
            },
        )
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            val dm = activity.resources.displayMetrics
            setLayout(dm.widthPixels, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    fun dismiss() {
        com.winlator.cmod.shared.android.AppUtils
            .hideKeyboard(activity)
        dialog.dismiss()
    }
}
