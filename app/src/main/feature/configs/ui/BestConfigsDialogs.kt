package com.winlator.cmod.feature.configs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.feature.configs.ConfigExportImport
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary

/**
 * Material 3 "action sheet" replacement for the legacy AlertDialog.Builder.setItems
 * pattern. Each row is icon + label + supporting text, styled with the WinNative
 * theme so it lives consistently inside the shortcut settings dialog.
 */
@Composable
fun BestConfigsExportSheet(
    onDismiss: () -> Unit,
    onSaveToFile: () -> Unit,
    onShareToCommunity: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinNativeBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringRes(R.string.best_configs_export_label),
                color = WinNativeTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionRow(
                    icon = Icons.Outlined.FileDownload,
                    label = stringRes(R.string.best_configs_export_to_file),
                    supporting = stringRes(R.string.best_configs_export_to_file_supporting),
                    onClick = {
                        onSaveToFile()
                        onDismiss()
                    },
                )
                ActionRow(
                    icon = Icons.Outlined.CloudUpload,
                    label = stringRes(R.string.best_configs_export_to_community),
                    supporting = stringRes(R.string.best_configs_export_to_community_supporting),
                    onClick = {
                        onShareToCommunity()
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButtonTinted(
                text = stringRes(R.string.best_configs_export_cancel),
                onClick = onDismiss,
            )
        },
    )
}

/** Same action-sheet pattern, but for the Import flow. */
@Composable
fun BestConfigsImportSheet(
    onDismiss: () -> Unit,
    onBrowseCommunity: () -> Unit,
    onPickFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinNativeBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringRes(R.string.best_configs_import_label),
                color = WinNativeTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionRow(
                    icon = Icons.Outlined.InsertDriveFile,
                    label = stringRes(R.string.best_configs_import_from_file),
                    supporting = stringRes(R.string.best_configs_import_from_file_supporting),
                    onClick = {
                        onPickFile()
                        onDismiss()
                    },
                )
                ActionRow(
                    icon = Icons.Outlined.SettingsSuggest,
                    label = stringRes(R.string.best_configs_import_from_community),
                    supporting = stringRes(R.string.best_configs_import_from_community_supporting),
                    onClick = {
                        onBrowseCommunity()
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButtonTinted(
                text = stringRes(R.string.best_configs_export_cancel),
                onClick = onDismiss,
            )
        },
    )
}

/**
 * Name-this-config dialog. Material 3 [OutlinedTextField] capped to 10 chars to
 * match the server-side validation.
 */
@Composable
fun BestConfigsNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        keyboard?.hide()
        val name = text.trim().take(ConfigExportImport.CUSTOM_NAME_MAX_LEN).ifBlank { null }
        onConfirm(name)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinNativeBackground,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringRes(R.string.best_configs_custom_name_dialog_title),
                color = WinNativeTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringRes(R.string.best_configs_custom_name_dialog_message),
                    color = WinNativeTextSecondary,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        // Hard-cap to the same length the server enforces — feels
                        // better than letting the user type past it and silently
                        // truncating on submit.
                        text = v.take(ConfigExportImport.CUSTOM_NAME_MAX_LEN)
                    },
                    placeholder = {
                        Text(
                            stringRes(R.string.best_configs_custom_name_dialog_hint),
                            color = WinNativeTextSecondary.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WinNativeTextPrimary,
                        unfocusedTextColor = WinNativeTextPrimary,
                        cursorColor = WinNativeAccent,
                        focusedBorderColor = WinNativeAccent,
                        unfocusedBorderColor = WinNativeOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${text.length}/${ConfigExportImport.CUSTOM_NAME_MAX_LEN}",
                    color = WinNativeTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            FilledButtonTinted(
                text = stringRes(R.string.best_configs_custom_name_dialog_share),
                onClick = submit,
            )
        },
        dismissButton = {
            TextButtonTinted(
                text = stringRes(R.string.best_configs_export_cancel),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    supporting: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WinNativeSurface)
            .border(1.dp, WinNativeOutline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(WinNativeAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WinNativeAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = WinNativeTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = supporting,
                color = WinNativeTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TextButtonTinted(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = WinNativeTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FilledButtonTinted(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WinNativeAccent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun stringRes(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
