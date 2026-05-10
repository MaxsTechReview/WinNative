package com.winlator.cmod.feature.steamcloudsync

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R

data class SteamCloudConflictTimestamps(
    val localTimestampLabel: String,
    val cloudTimestampLabel: String,
)

internal val SteamCloudConflictWindow = Color(0xFF171A21)
internal val SteamCloudConflictPanel = Color(0xFF1B2838)
internal val SteamCloudConflictText = Color(0xFFD6D7D9)
internal val SteamCloudConflictBlue = Color(0xFF66C0F4)

private val SteamPanelAlt = Color(0xFF101822)
private val SteamBorder = Color(0xFF2A475E)
private val SteamButton = Color(0xFF2A9FD6)
private val SteamButtonText = Color(0xFFE5F3FF)
private val SteamMuted = Color(0xFF8F98A0)

@Composable
internal fun SteamCloudConflictDialogContent(
    timestamps: SteamCloudConflictTimestamps,
    initialKeepBackup: Boolean,
    onKeepBackupChanged: (Boolean) -> Unit,
    onUseCloud: (keepBackup: Boolean) -> Unit,
    onUseLocal: (keepBackup: Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    var keepBackup by remember { mutableStateOf(initialKeepBackup) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .widthIn(max = 520.dp),
        shape = RoundedCornerShape(3.dp),
        color = SteamCloudConflictWindow,
        border = BorderStroke(1.dp, SteamBorder),
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints {
            val compactActions = maxWidth < 380.dp

            Column(
                modifier =
                    Modifier
                        .background(SteamCloudConflictWindow)
                        .heightIn(max = 430.dp),
            ) {
                Text(
                    text = "Steam Cloud Sync Conflict",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(SteamCloudConflictPanel)
                            .border(BorderStroke(0.dp, SteamCloudConflictPanel))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = SteamCloudConflictBlue,
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
                        text = "Your local save data does not match Steam Cloud.",
                        color = SteamCloudConflictText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Choose which version to use before launching. The other version will be replaced.",
                        color = SteamMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(SteamPanelAlt)
                                .border(1.dp, SteamBorder, RoundedCornerShape(2.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VersionLine("Local files", timestamps.localTimestampLabel)
                        VersionLine("Cloud files", timestamps.cloudTimestampLabel)
                    }

                    KeepBackupCheckbox(
                        checked = keepBackup,
                        onCheckedChange = { v ->
                            keepBackup = v
                            onKeepBackupChanged(v)
                        },
                    )
                }

                if (compactActions) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(SteamCloudConflictPanel)
                                .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SteamOutlinedButton("Use Local Files", Modifier.fillMaxWidth()) {
                            onUseLocal(keepBackup)
                        }
                        SteamPrimaryButton("Use Cloud Files", Modifier.fillMaxWidth()) {
                            onUseCloud(keepBackup)
                        }
                    }
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(SteamCloudConflictPanel)
                                .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        SteamOutlinedButton("Use Local Files", Modifier.widthIn(min = 132.dp)) {
                            onUseLocal(keepBackup)
                        }
                        SteamPrimaryButton("Use Cloud Files", Modifier.widthIn(min = 132.dp)) {
                            onUseCloud(keepBackup)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionLine(
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
            color = SteamMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = timestamp,
            color = SteamCloudConflictText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SteamPrimaryButton(
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
                containerColor = SteamButton,
                contentColor = SteamButtonText,
            ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SteamOutlinedButton(
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
                contentColor = SteamCloudConflictText,
                containerColor = Color.Transparent,
            ),
        border = BorderStroke(1.dp, SteamBorder),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeepBackupCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(2.dp),
        color = SteamPanelAlt,
        border = BorderStroke(1.dp, SteamBorder),
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
                        checkedColor = SteamCloudConflictBlue,
                        uncheckedColor = SteamMuted,
                        checkmarkColor = SteamCloudConflictWindow,
                    ),
            )
            Spacer(Modifier.widthIn(min = 2.dp))
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.cloud_saves_keep_replaced_backup),
                    color = SteamCloudConflictText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.cloud_saves_keep_replaced_backup_summary),
                    color = SteamMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
