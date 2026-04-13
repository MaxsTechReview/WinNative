package com.winlator.cmod.shared.ui
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
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
        val baseCardWidth = maxWidth * 0.22f
        val baseCardHeight = baseCardWidth * 1.2f
        val sidePadding = ((maxWidth - baseCardWidth) / 2).coerceAtLeast(0.dp)
        val flingBehavior = rememberSnapFlingBehavior(listState)
        val cardWidthPx = with(density) { baseCardWidth.toPx() }
        val snapThresholdPx = cardWidthPx * 0.2f

        // Scroll to selected index when changed externally (d-pad / joystick)
        LaunchedEffect(selectedIndex) {
            if (selectedIndex in items.indices) {
                lastReportedIndex.intValue = selectedIndex
                listState.animateScrollToItem(selectedIndex)
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
                        centeredItem?.let { item ->
                            val distance = abs((item.offset + item.size / 2) - viewportCenter).toFloat()
                            Triple(item.index, distance, listState.isScrollInProgress)
                        }
                    }
                }
            }.filterNotNull()
                .distinctUntilChanged()
                .collect { (centeredIndex, distance, isScrolling) ->
                    val shouldPromote = !isScrolling || distance <= snapThresholdPx
                    if (shouldPromote && centeredIndex != lastReportedIndex.intValue) {
                        lastReportedIndex.intValue = centeredIndex
                        onCenteredIndexChanged(centeredIndex)
                    }
                }
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(spacing),
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
                val scale by animateFloatAsState(
                    targetValue = 1.12f - (0.22f * distanceFraction),
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    label = "carouselScale",
                )
                val rise by animateFloatAsState(
                    targetValue = 1f - distanceFraction,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    label = "carouselRise",
                )
                val itemAlpha by animateFloatAsState(
                    targetValue = 1f - (0.35f * distanceFraction),
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    label = "carouselAlpha",
                )

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
                    val newIndex =
                        if (value > 0) {
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
