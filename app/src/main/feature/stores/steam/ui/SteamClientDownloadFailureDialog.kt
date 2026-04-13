package com.winlator.cmod.feature.stores.steam.ui
import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

object SteamClientDownloadFailureDialog {
    @JvmStatic
    fun show(
        activity: Activity,
        title: String,
        message: String,
        onRetry: Runnable,
        onClose: Runnable,
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
                    MaterialTheme(
                        colorScheme =
                            darkColorScheme(
                                primary = Color(0xFF57CBDE),
                                surface = Color(0xFF1A2028),
                                background = Color(0xFF141B24),
                                onSurface = Color(0xFFF0F4FF),
                                onBackground = Color(0xFFF0F4FF),
                            ),
                    ) {
                        SteamClientDownloadFailureDialogContent(
                            title = title,
                            message = message,
                            onRetry = {
                                dialog.dismiss()
                                onRetry.run()
                            },
                            onClose = {
                                dialog.dismiss()
                                onClose.run()
                            },
                        )
                    }
                }
            }

        dialog.setContentView(composeView)
        dialog.show()
    }
}

@Composable
private fun SteamClientDownloadFailureDialogContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A2028),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .background(Color(0xFF1A2028))
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Close")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
