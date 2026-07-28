package com.winlator.cmod.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.shared.theme.GameSettingsStyle
import com.winlator.cmod.shared.ui.nav.paneNavItem
import com.winlator.cmod.shared.ui.outlinedSwitchColors

@Composable
fun SharedSettingGroup(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GameSettingsStyle.CardSurface)
                .border(1.dp, GameSettingsStyle.CardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        content()
    }
}

@Composable
fun SharedGroupTitle(text: String) {
    Text(
        text = text,
        color = GameSettingsStyle.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun SharedInfoRow(
    label: String,
    value: String,
    singleLineValue: Boolean = false,
) {
    val treatAsPath =
        singleLineValue ||
            value.contains('/') ||
            value.contains('\\') ||
            value.contains(".so")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = GameSettingsStyle.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = GameSettingsStyle.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (treatAsPath) 1 else Int.MAX_VALUE,
            overflow = if (treatAsPath) TextOverflow.Ellipsis else TextOverflow.Clip,
            softWrap = !treatAsPath,
        )
    }
}

@Composable
fun SharedSettingSwitch(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onCheckedChange(!checked) }
                .paneNavItem(
                    cornerRadius = 8.dp,
                    onActivate = { onCheckedChange(!checked) },
                    highlightColor = GameSettingsStyle.NavHighlight,
                    tapToSelect = true,
                )
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                label,
                color = GameSettingsStyle.TextPrimary,
                fontSize = 12.sp,
                softWrap = true,
                maxLines = 3,
                overflow = TextOverflow.Clip,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = GameSettingsStyle.TextSecondary,
                    fontSize = 11.sp,
                    softWrap = true,
                    maxLines = 4,
                    overflow = TextOverflow.Clip,
                    lineHeight = 14.sp,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                outlinedSwitchColors(
                    accentColor = GameSettingsStyle.AccentBlue,
                    textSecondaryColor = GameSettingsStyle.TextSecondary,
                ),
        )
    }
}
