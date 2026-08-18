package com.winlator.cmod.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val retroLibrarySystemIds = mutableStateOf<Map<Int, String>>(emptyMap())

internal fun libraryBadgeLabel(
    appId: Int,
    isCustom: Boolean,
): String? {
    val systemId = retroLibrarySystemIds.value[appId]
    if (systemId != null) {
        return com.winlator.cmod.feature.retro.RetroSystems
            .fromId(systemId)
            ?.badgeLabel
            ?: systemId
    }
    return if (isCustom) "PC" else null
}

@Composable
internal fun RetroConsoleRibbon(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(14.dp)
                .background(Color(0xD9090C10)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFFE6EDF3),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.verticalRibbonText(),
        )
    }
}

private fun Modifier.verticalRibbonText(): Modifier =
    this.layout { measurable, _ ->
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())

        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = -(placeable.width - placeable.height) / 2,
                y = -(placeable.height - placeable.width) / 2,
            ) {
                rotationZ = -90f
            }
        }
    }
