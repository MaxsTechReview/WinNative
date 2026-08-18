package com.winlator.cmod.feature.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.feature.community.ComponentChecker
import com.winlator.cmod.shared.theme.GameSettingsStyle
import com.winlator.cmod.shared.ui.nav.paneNavItem

@Composable
fun MissingComponentDialog(missing: List<ComponentChecker.Missing>, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().fillMaxHeight().background(Color(0xFF000000).copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(14.dp))
                .background(GameSettingsStyle.CardSurface)
                .border(1.dp, GameSettingsStyle.CardBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .clickable(enabled = false) {},
        ) {
            Text(
                "⚠ Missing Component",
                color = GameSettingsStyle.WarningAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "This config needs components you don't have installed. Install them, then try again:",
                color = GameSettingsStyle.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Column(
                Modifier.padding(top = 4.dp).verticalScroll(rememberScrollState()),
            ) {
                missing.forEach { item ->
                    Text(
                        "•  ${item.label}",
                        color = GameSettingsStyle.WarningAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(GameSettingsStyle.AccentBlue.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            GameSettingsStyle.AccentBlue.copy(alpha = 0.25f),
                            RoundedCornerShape(8.dp),
                        )
                        .paneNavItem(
                            cornerRadius = 8.dp,
                            onActivate = onDismiss,
                            highlightColor = GameSettingsStyle.NavHighlight,
                            tapToSelect = true,
                            isEntry = true,
                        )
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "OK",
                        color = GameSettingsStyle.AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
