package com.winlator.cmod.shared.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme

/**
 * Lightweight popup shown while a container action (create/remove/
 * duplicate) is in flight. Uses the shared [PopupDialog] chrome instead
 * of the fullscreen [PreloaderDialog] takeover, with a fade + scale
 * entrance. Indeterminate by default; pass `indeterminate = false` and
 * drive via [setProgress] for a determinate bar (smoothed by
 * [PopupDialog]'s built-in animation).
 */
class ContainerProgressPopup(
    private val activity: Activity,
    @StringRes private val titleRes: Int,
    indeterminate: Boolean = true,
) {
    private val dialog: Dialog = Dialog(activity, R.style.ContentDialog).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
    private val visible = mutableStateOf(false)
    private val progress = mutableStateOf(if (indeterminate) Float.NaN else 0f)

    init {
        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewTreeLifecycleOwner(activity as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)
            setContent {
                WinNativeTheme {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(tween(180)) +
                                scaleIn(tween(180), initialScale = 0.9f),
                            exit = fadeOut(tween(120)) +
                                scaleOut(tween(120), targetScale = 0.9f),
                        ) {
                            PopupDialog(
                                title = stringResource(titleRes),
                                icon = Icons.Outlined.Info,
                                progress = progress.value,
                                accentColor = Color(0xFF1A9FFF),
                                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                            )
                        }
                    }
                }
            }
        }
        dialog.setContentView(composeView)
    }

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!dialog.isShowing) {
            try { dialog.show() } catch (_: Throwable) { return }
        }
        visible.value = true
    }

    fun close() {
        try { if (dialog.isShowing) dialog.dismiss() } catch (_: Throwable) {}
    }

    fun setProgress(percent: Int) {
        progress.value = (percent / 100f).coerceIn(0f, 1f)
    }

    fun closeWithDelay(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ close() }, delayMs)
    }
}
