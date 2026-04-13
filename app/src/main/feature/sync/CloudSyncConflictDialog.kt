package com.winlator.cmod.feature.sync
import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        CloudSyncConflictDialogContent(
                            timestamps = timestamps,
                            onUseCloud = {
                                dialog.dismiss()
                                onUseCloud.run()
                            },
                            onUseLocal = {
                                dialog.dismiss()
                                onUseLocal.run()
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
private fun CloudSyncConflictDialogContent(
    timestamps: CloudSyncConflictTimestamps,
    onUseCloud: () -> Unit,
    onUseLocal: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF171E27),
        tonalElevation = 0.dp,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter =
                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
        ) {
            Column(
                modifier =
                    Modifier
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1B2430), Color(0xFF151B24)),
                                ),
                        ).border(1.dp, Color(0xFF263547), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0x1F57CBDE),
                ) {
                    Text(
                        text = "Cloud Save Sync",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFF7BD8E8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = "A newer cloud save was detected for this title.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Choose which version to keep before launch. Syncing from cloud will replace the local save data on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF121922),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF233141), RoundedCornerShape(18.dp)),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Version Details",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF8FA7C1),
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Default,
                        )
                        Text(
                            text = "Local save\n${timestamps.localTimestampLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Cloud save\n${timestamps.cloudTimestampLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onUseLocal,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        border = BorderStroke(1.dp, Color(0xFF31455B)),
                    ) {
                        Text("Keep Local")
                    }
                    Button(
                        onClick = onUseCloud,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF57CBDE),
                                contentColor = Color(0xFF0E141B),
                            ),
                    ) {
                        Text("Sync from Cloud")
                    }
                }
            }
        }
    }
}
