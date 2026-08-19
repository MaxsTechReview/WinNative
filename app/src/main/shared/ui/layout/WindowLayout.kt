package com.winlator.cmod.shared.ui.layout

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val COMPACT_WIDTH_DP = 600

@Composable
private fun windowSizeDp(): Pair<Int, Int> {
    val configuration = LocalConfiguration.current
    val configWidth = configuration.screenWidthDp
    val configHeight = configuration.screenHeightDp
    if (configWidth != Configuration.SCREEN_WIDTH_DP_UNDEFINED &&
        configHeight != Configuration.SCREEN_HEIGHT_DP_UNDEFINED &&
        configWidth > 0 &&
        configHeight > 0
    ) {
        return configWidth to configHeight
    }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { containerSize.width.toDp().value.toInt() to containerSize.height.toDp().value.toInt() }
}

@Composable
fun isPortraitLayout(): Boolean {
    val (width, height) = windowSizeDp()
    return height > width
}

@Composable
fun isCompactWidth(): Boolean = windowSizeDp().first < COMPACT_WIDTH_DP

@Composable
fun screenWidthDp(): Dp = windowSizeDp().first.dp

@Composable
fun screenHeightDp(): Dp = windowSizeDp().second.dp

@Composable
fun <T> byOrientation(
    portrait: T,
    landscape: T,
): T = if (isPortraitLayout()) portrait else landscape
