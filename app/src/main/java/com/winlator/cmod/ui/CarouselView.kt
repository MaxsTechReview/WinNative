package com.winlator.cmod.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Horizontal snap-carousel layout for library items.
 *
 * @param items The data to display.
 * @param modifier Outer modifier.
 * @param listState Shared [LazyListState] for external scroll control.
 * @param selectedIndex Currently focused item index (drives scale/rise animation).
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
    // Track the last index we reported so we don't re-report during programmatic scrolls
    val lastReportedIndex = remember { mutableIntStateOf(selectedIndex) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val spacing = 14.dp
        val baseCardWidth = maxWidth * 0.22f
        val baseCardHeight = baseCardWidth * 1.2f
        val sidePadding = ((maxWidth - baseCardWidth) / 2).coerceAtLeast(0.dp)
        val flingBehavior = rememberSnapFlingBehavior(listState)

        // Scroll to selected index when changed externally (d-pad / joystick updates libraryFocusIndex)
        LaunchedEffect(selectedIndex) {
            if (selectedIndex in items.indices) {
                lastReportedIndex.intValue = selectedIndex
                listState.animateScrollToItem(selectedIndex)
            }
        }

        // Once scroll settles, report the actually centered item
        LaunchedEffect(listState, items.size) {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isScrolling ->
                    if (!isScrolling && items.isNotEmpty()) {
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) return@collect
                        val viewportCenter =
                            (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                        val centeredItem = visibleItems.minByOrNull { item ->
                            abs((item.offset + item.size / 2) - viewportCenter)
                        } ?: return@collect
                        if (centeredItem.index != lastReportedIndex.intValue) {
                            lastReportedIndex.intValue = centeredItem.index
                            onCenteredIndexChanged(centeredItem.index)
                        }
                    }
                }
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 18.dp, bottom = 18.dp),
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 0.9f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "carouselScale"
                )
                val rise by animateDpAsState(
                    targetValue = if (isSelected) 14.dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "carouselRise"
                )

                Box(
                    modifier = Modifier
                        .width(baseCardWidth)
                        .height(baseCardHeight + 28.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = -rise.toPx()
                        }
                ) {
                    itemContent(item, index, isSelected, baseCardWidth, baseCardHeight)
                }
            }
        }
    }
}

/**
 * Drives item-by-item horizontal navigation of a [LazyListState] from an analog-stick flow.
 * Updates [onIndexChanged] so the parent can update its focus index, which then drives
 * the carousel's selectedIndex.
 *
 * @param listState The list to scroll.
 * @param stickFlow The analog-stick X-axis value flow (–1..1).
 * @param currentIndex The current focused index.
 * @param itemCount Total number of items.
 * @param onIndexChanged Called with the new target index.
 * @param deadZone Minimum absolute value before navigation triggers.
 * @param cooldownMs Cooldown between item navigations in milliseconds.
 */
@Composable
fun JoystickCarouselScroll(
    listState: LazyListState,
    stickFlow: StateFlow<Float>?,
    currentIndex: Int = 0,
    itemCount: Int = 0,
    onIndexChanged: (Int) -> Unit = {},
    deadZone: Float = 0.4f,
    cooldownMs: Long = 300,
) {
    if (stickFlow == null || itemCount <= 0) return

    LaunchedEffect(listState, itemCount) {
        var lastNavTime = 0L
        stickFlow.collect { value ->
            if (abs(value) > deadZone) {
                val now = System.currentTimeMillis()
                if (now - lastNavTime > cooldownMs) {
                    lastNavTime = now
                    val newIndex = if (value > 0) {
                        (currentIndex + 1).coerceAtMost(itemCount - 1)
                    } else {
                        (currentIndex - 1).coerceAtLeast(0)
                    }
                    if (newIndex != currentIndex) {
                        onIndexChanged(newIndex)
                    }
                }
            }
        }
    }
}
