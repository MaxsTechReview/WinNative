package com.winlator.cmod

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

data class CloudSyncConflictTimestamps(
    val localTimestampLabel: String,
    val cloudTimestampLabel: String,
)

object CloudSyncConflictDialog {
    @JvmStatic
    fun show(
        activity: Activity,
        timestamps: CloudSyncConflictTimestamps,
        onUseCloud: Runnable,
        onUseLocal: Runnable,
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            (activity as? ComponentActivity)?.let {
                setViewTreeLifecycleOwner(it)
                setViewTreeSavedStateRegistryOwner(it)
            }
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF57CBDE),
                        surface = Color(0xFF1A2028),
                        background = Color(0xFF141B24),
                        onSurface = Color(0xFFF0F4FF),
                        onBackground = Color(0xFFF0F4FF),
                    )
                ) {
                    CloudSyncConflictDialogContent(
                        timestamps = timestamps,
                        onUseCloud = {
                            dialog.dismiss()
                            onUseCloud.run()
                        },
                        onUseLocal = {
                            dialog.dismiss()
                            onUseLocal.run()
                        }
                    )
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.show()
    }
}

@Composable
private fun CloudSyncConflictDialogContent(
    timestamps: CloudSyncConflictTimestamps,
    onUseCloud: () -> Unit,
    onUseLocal: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A2028),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1A2028))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Cloud Save Conflict",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "The local and cloud saves do not match. Choose which version to use before launch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141B24),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Local save: ${timestamps.localTimestampLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Cloud save: ${timestamps.cloudTimestampLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onUseLocal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Keep Local")
                }
                Button(
                    onClick = onUseCloud,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Use Cloud")
                }
            }
        }
    }
}
