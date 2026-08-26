package com.winlator.cmod.shared.ui
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

/**
 * Horizontal snap-carousel layout for library items.
 *
 * Items smoothly scale, rise, and fade based on their distance from the viewport center,
 * creating a fluid parallax-like effect during scrolling.
 *
 * @param items The data to display.
 * @param modifier Outer modifier.
 * @param listState Shared [LazyListState] for external scroll control.
 * @param selectedIndex Currently focused item index (drives scroll position).
 * @param onCenteredIndexChanged Called when the visually centered item changes after scroll settles.
 * @param itemContent Composable for each item; receives item, index, selection state, base card width, and base card height.
 */
@Composable
fun <T> CarouselView(
    items: List<T>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    selectedIndex: Int = 0,
    onCenteredIndexChanged: (Int) -> Unit = {},
    itemContent: @Composable (item: T, index: Int, isSelected: Boolean, cardWidth: Dp, cardHeight: Dp) -> Unit,
) {
    val lastReportedIndex = remember { mutableIntStateOf(selectedIndex) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val spacing = 14.dp
        val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Landscape/tablet: cards sized off width, as before (viewport is short and wide,
        // so width is the constraining dimension).
        // Portrait: the viewport here is often much taller than it is wide (this carousel
        // sits in a box that claims the full remaining screen height), so sizing purely off
        // width produces tiny cards adrift in a mostly-empty column. Size off height instead
        // (capped so at least ~1.6 cards' worth of width still shows, preserving the
        // carousel's peek-of-next-card feel) and center the row in the cross axis so the
        // card sits in the middle of that height rather than pinned to the top.
        val isPortrait = maxHeight > maxWidth
        val baseCardWidth =
            if (isPortrait) {
                ((maxHeight * 0.56f) / 1.2f).coerceAtMost(maxWidth * 0.62f)
            } else {
                maxWidth * 0.22f
            }
        val baseCardHeight = baseCardWidth * 1.2f
        val sidePadding = ((maxWidth - baseCardWidth) / 2).coerceAtLeast(0.dp)
        val flingBehavior = rememberSnapFlingBehavior(listState)
        val cardWidthPx = with(density) { baseCardWidth.toPx() }
        // Scroll to selected index when changed externally (d-pad / joystick)
        LaunchedEffect(selectedIndex) {
            if (selectedIndex in items.indices) {
                lastReportedIndex.intValue = selectedIndex
                listState.scrollToItem(selectedIndex)
            }
        }

        // Report the centered item as soon as it gets close enough to center,
        // then still reconcile once scrolling fully settles.
        LaunchedEffect(listState, items.size) {
            snapshotFlow {
                if (items.isEmpty()) {
                    null
                } else {
                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                    if (visibleItems.isEmpty()) {
                        null
                    } else {
                        val viewportCenter =
                            (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                        val centeredItem =
                            visibleItems.minByOrNull { item ->
                                abs((item.offset + item.size / 2) - viewportCenter)
                            }
                        centeredItem?.let { item -> item.index to listState.isScrollInProgress }
                    }
                }
            }.filterNotNull()
                .distinctUntilChanged()
                .collect { (centeredIndex, isScrolling) ->
                    if (!isScrolling && centeredIndex != lastReportedIndex.intValue) {
                        lastReportedIndex.intValue = centeredIndex
                        onCenteredIndexChanged(centeredIndex)
                    }
                }
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            // Center the cards in the available height instead of the LazyRow's default
            // top alignment, which used to leave the (small, width-driven) cards pinned
            // near the top with a large dead zone below on tall/portrait viewports.
            verticalAlignment = Alignment.CenterVertically,
            contentPadding =
                PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    top = 24.dp,
                    bottom = 24.dp + navBottomInset,
                ),
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items) { index, item ->
                // Compute distance from viewport center as a 0..1 fraction
                val distanceFraction by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val itemInfo =
                            layoutInfo.visibleItemsInfo.find { it.index == index }
                                ?: return@derivedStateOf 1f
                        val viewportCenter =
                            (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        val itemCenter = itemInfo.offset + itemInfo.size / 2f
                        val rawDistance = abs(itemCenter - viewportCenter)
                        // Normalize: 0 = centered, 1 = one full card-width away or more
                        (rawDistance / cardWidthPx).coerceIn(0f, 1.5f) / 1.5f
                    }
                }

                // Smooth animated values driven by distance
                val scale = 1.12f - (0.22f * distanceFraction)
                val rise = 1f - distanceFraction
                val itemAlpha = 1f - (0.15f * distanceFraction)

                val risePx = with(density) { (16.dp * rise).toPx() }
                val isSelected = index == selectedIndex

                Box(
                    modifier =
                        Modifier
                            .width(baseCardWidth)
                            .height(baseCardHeight + 28.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationY = -risePx
                                alpha = itemAlpha
                            },
                ) {
                    itemContent(item, index, isSelected, baseCardWidth, baseCardHeight)
                }
            }
        }
    }
}
