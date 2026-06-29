package com.winlator.cmod.feature.sync

import android.app.Activity
import android.app.Dialog
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme

private val EpicCloudConflictWindow = Color(0xFF171A21)
private val EpicCloudConflictPanel = Color(0xFF1B2838)
private val EpicCloudConflictText = Color(0xFFD6D7D9)
private val EpicCloudConflictBlue = Color(0xFF66C0F4)
private val EpicPanelAlt = Color(0xFF101822)
private val EpicBorder = Color(0xFF2A475E)
private val EpicButton = Color(0xFF2A9FD6)
private val EpicButtonText = Color(0xFFE5F3FF)
private val EpicMuted = Color(0xFF8F98A0)

data class EpicCloudConflictTimestamps(
    val localTimestampLabel: String,
    val cloudTimestampLabel: String,
)

object EpicCloudConflictDialog {
    @JvmStatic
    fun show(
        activity: Activity,
        timestamps: EpicCloudConflictTimestamps,
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
                                primary = EpicCloudConflictBlue,
                                surface = EpicCloudConflictPanel,
                                background = EpicCloudConflictWindow,
                                onSurface = EpicCloudConflictText,
                                onBackground = EpicCloudConflictText,
                            ),
                    ) {
                        EpicCloudConflictDialogContent(
                            timestamps = timestamps,
                            onUseCloud = { keep ->
                                dialog.dismiss()
                                onUseCloud(keep)
                            },
                            onUseLocal = { keep ->
                                dialog.dismiss()
                                onUseLocal(keep)
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

@Composable
internal fun EpicCloudConflictDialogContent(
    timestamps: EpicCloudConflictTimestamps,
    onUseCloud: (keepBackup: Boolean) -> Unit,
    onUseLocal: (keepBackup: Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    var keepBackup by remember { mutableStateOf(true) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .widthIn(max = 520.dp),
        shape = RoundedCornerShape(3.dp),
        color = EpicCloudConflictWindow,
        border = BorderStroke(1.dp, EpicBorder),
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints {
            val compactActions = maxWidth < 380.dp
            Column(
                modifier =
                    Modifier
                        .background(EpicCloudConflictWindow)
                        .heightIn(max = 430.dp),
            ) {
                Text(
                    text = "Epic Cloud Sync Conflict",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(EpicCloudConflictPanel)
                            .border(BorderStroke(0.dp, EpicCloudConflictPanel))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = EpicCloudConflictBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Your local save data does not match Epic Games cloud saves.",
                        color = EpicCloudConflictText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Choose whether to keep the saves on this device or replace them with the Epic Games cloud version before launching.",
                        color = EpicMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(EpicPanelAlt)
                                .border(1.dp, EpicBorder, RoundedCornerShape(2.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EpicVersionLine("Local saves", timestamps.localTimestampLabel)
                        EpicVersionLine("Epic cloud saves", timestamps.cloudTimestampLabel)
                    }

                    EpicKeepBackupCheckbox(
                        checked = keepBackup,
                        onCheckedChange = { keepBackup = it },
                    )
                }

                if (compactActions) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(EpicCloudConflictPanel)
                                .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EpicOutlinedButton("Use Local Saves", Modifier.fillMaxWidth()) {
                            onUseLocal(keepBackup)
                        }
                        EpicPrimaryButton("Use Epic Cloud", Modifier.fillMaxWidth()) {
                            onUseCloud(keepBackup)
                        }
                    }
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(EpicCloudConflictPanel)
                                .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        EpicOutlinedButton("Use Local Saves", Modifier.widthIn(min = 132.dp)) {
                            onUseLocal(keepBackup)
                        }
                        EpicPrimaryButton("Use Epic Cloud", Modifier.widthIn(min = 132.dp)) {
                            onUseCloud(keepBackup)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpicKeepBackupCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(2.dp),
        color = EpicPanelAlt,
        border = BorderStroke(1.dp, EpicBorder),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = EpicCloudConflictBlue,
                        uncheckedColor = EpicMuted,
                        checkmarkColor = EpicCloudConflictWindow,
                    ),
            )
            Spacer(Modifier.widthIn(min = 2.dp))
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.cloud_saves_keep_replaced_backup),
                    color = EpicCloudConflictText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.cloud_saves_keep_replaced_backup_summary),
                    color = EpicMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EpicVersionLine(
    label: String,
    timestamp: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 86.dp),
            color = EpicMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = timestamp,
            color = EpicCloudConflictText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun EpicPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(2.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = EpicButton,
                contentColor = EpicButtonText,
            ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EpicOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(2.dp),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = EpicCloudConflictText,
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, EpicBorder),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
