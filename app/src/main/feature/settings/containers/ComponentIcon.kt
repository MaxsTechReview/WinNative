package com.winlator.cmod.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A simple, tintable shipping-container glyph designed to match the outlined Material icons
 * used on the container card (corrugated cargo box: outer frame + vertical ribs). Filled with
 * black so Compose's [androidx.compose.material3.Icon] tint recolors it (e.g. the blue accent).
 */
val ComponentContainerIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector
        .Builder(
            name = "ComponentContainerIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Outer body frame (ring via even-odd: outer rect minus inner rect).
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(2.5f, 7.5f)
                lineTo(21.5f, 7.5f)
                lineTo(21.5f, 16.5f)
                lineTo(2.5f, 16.5f)
                close()
                moveTo(4.3f, 9.3f)
                lineTo(19.7f, 9.3f)
                lineTo(19.7f, 14.7f)
                lineTo(4.3f, 14.7f)
                close()
            }
            // Vertical corrugation ribs.
            path(fill = SolidColor(Color.Black)) {
                val ribTop = 9.3f
                val ribBottom = 14.7f
                val ribW = 1.0f
                for (x in floatArrayOf(7.0f, 10.2f, 13.4f, 16.6f)) {
                    moveTo(x, ribTop)
                    lineTo(x + ribW, ribTop)
                    lineTo(x + ribW, ribBottom)
                    lineTo(x, ribBottom)
                    close()
                }
            }
        }.build()
}
