package com.winlator.cmod.feature.community.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.feature.community.ComponentChecker

/**
 * "⚠ MISSING COMPONENT" dialog. Lists the components the downloaded config needs
 * that are not installed. A single OK button just dismisses (per spec) — the
 * config is NOT applied when components are missing.
 */
@Composable
fun MissingComponentDialog(missing: List<ComponentChecker.Missing>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2A),
        titleContentColor = Color(0xFFFFB74D),
        textContentColor = Color(0xFFF0F4FF),
        title = { Text("⚠ Missing Component", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "This config needs components you don't have installed. Install them, " +
                        "then try again:",
                    fontSize = 13.sp,
                )
                missing.forEach { m ->
                    Text("•  ${m.label}", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp), color = Color(0xFFFFB74D))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = Color(0xFF1A9FFF)) }
        },
    )
}
