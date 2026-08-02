package com.winlator.cmod.feature.retro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.GameSettingsStyle

@Composable
fun RetroNetplayEditField(
    label: String,
    value: String,
    placeholder: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            color = GameSettingsStyle.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))

        if (editing) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(placeholder, fontSize = 12.sp) },
                    keyboardOptions =
                        if (numeric) {
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        } else {
                            KeyboardOptions.Default
                        },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GameSettingsStyle.AccentBlue,
                            unfocusedBorderColor = GameSettingsStyle.CardBorder,
                            focusedTextColor = GameSettingsStyle.TextPrimary,
                            unfocusedTextColor = GameSettingsStyle.TextPrimary,
                        ),
                    modifier = Modifier.weight(1f),
                )
                NetplayPillButton(
                    text = stringResource(R.string.retro_gs_save),
                    color = GameSettingsStyle.AccentBlue,
                ) {
                    onChange(draft.trim())
                    editing = false
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value.ifBlank { placeholder },
                    color =
                        if (value.isBlank()) {
                            GameSettingsStyle.TextDim
                        } else {
                            GameSettingsStyle.TextPrimary
                        },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                NetplayPillButton(
                    text = stringResource(R.string.common_ui_edit),
                    color = GameSettingsStyle.AccentBlue,
                ) {
                    draft = value
                    editing = true
                }
                if (value.isNotBlank()) {
                    NetplayIconPillButton(
                        icon = Icons.Outlined.Delete,
                        color = GameSettingsStyle.DangerRed,
                        contentDescription = stringResource(R.string.retro_scr_delete),
                    ) {
                        draft = ""
                        onChange("")
                    }
                }
            }
        }
    }
}

@Composable
private fun NetplayPillButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier.height(38.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun NetplayIconPillButton(
    icon: ImageVector,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.height(38.dp).width(46.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}
