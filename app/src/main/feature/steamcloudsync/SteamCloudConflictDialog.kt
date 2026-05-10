package com.winlator.cmod.feature.steamcloudsync

import android.app.Activity
import android.app.Dialog
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.shared.theme.WinNativeTheme

object SteamCloudConflictDialog {
    @JvmStatic
    fun show(
        activity: Activity,
        timestamps: SteamCloudConflictTimestamps,
        onUseCloud: (keepBackup: Boolean) -> Unit,
        onUseLocal: (keepBackup: Boolean) -> Unit,
    ) {
        val dialog =
            Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                window?.apply {
                    setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                    )
                    setBackgroundDrawableResource(android.R.color.transparent)
                }
            }

        val composeView =
            ComposeView(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                (activity as? ComponentActivity)?.let {
                    setViewTreeLifecycleOwner(it)
                    setViewTreeSavedStateRegistryOwner(it)
                }
                setContent {
                    WinNativeTheme(
                        colorScheme =
                            darkColorScheme(
                                primary = SteamCloudConflictBlue,
                                surface = SteamCloudConflictPanel,
                                background = SteamCloudConflictWindow,
                                onSurface = SteamCloudConflictText,
                                onBackground = SteamCloudConflictText,
                            ),
                    ) {
                        SteamCloudConflictDialogContent(
                            timestamps = timestamps,
                            initialKeepBackup = GameSaveBackupManager.isKeepReplacedBackupEnabled(activity),
                            onKeepBackupChanged = { enabled ->
                                GameSaveBackupManager.setKeepReplacedBackupEnabled(activity, enabled)
                            },
                            onUseCloud = { keepBackup ->
                                dialog.dismiss()
                                onUseCloud(keepBackup)
                            },
                            onUseLocal = { keepBackup ->
                                dialog.dismiss()
                                onUseLocal(keepBackup)
                            },
                        )
                    }
                }
            }

        dialog.setContentView(composeView)
        dialog.show()
        dialog.window?.apply {
            val dm = activity.resources.displayMetrics
            val horizontalMarginPx =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, dm).toInt()
            val maxDialogWidthPx =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 520f, dm).toInt()
            val targetWidth = (dm.widthPixels - (horizontalMarginPx * 2)).coerceAtMost(maxDialogWidthPx)
            setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }
}
