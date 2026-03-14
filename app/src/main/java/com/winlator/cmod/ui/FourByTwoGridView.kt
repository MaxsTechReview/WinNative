package com.winlator.cmod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

enum class ViewMode { Grid }

/**
 * Unified grid layout used by store tabs              
 *
 * @param items The data to display.
 * @param modifier Outer modifier (padding, size, etc.).
 * @param columns Number of grid columns.
 * @param spacing Gap between rows and columns.
 * @param contentPadding Extra padding inside the grid (e.g. for chasing-border inset).
 * @param gridState Shared [LazyGridState] — pass one in when you need joystick scroll.
 * @param clipContent Set to `false` to allow items to draw outside bounds (chasing border).
 * @param viewMode Reserved for future layout switching.
 * @param itemContent Composable for each item; receives index and computed row height.
 */
@Composable
fun <T> FourByTwoGridView(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    spacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gridState: LazyGridState = rememberLazyGridState(),
    clipContent: Boolean = true,
    viewMode: ViewMode = ViewMode.Grid,
    itemContent: @Composable (item: T, index: Int, rowHeight: Dp) -> Unit,
) {
    when (viewMode) {
        ViewMode.Grid -> {
            BoxWithConstraints(modifier.fillMaxSize()) {
                // Visible rows = 2; subtract one gap between them plus any content-padding inset
                val verticalInset = contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()
                val targetRowHeight = (maxHeight - spacing - verticalInset) / 2
                val rowHeight by animateDpAsState(
                    targetValue = targetRowHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh
                    ),
                    label = "rowHeight"
                )

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!clipContent) Modifier.graphicsLayer { clip = false } else Modifier)
                ) {
                    itemsIndexed(items) { index, item ->
                        itemContent(item, index, rowHeight)
                    }
                }

                // Snap to nearest row when mouse/touch scroll ends
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.isScrollInProgress }
                        .collect { scrolling ->
                            if (!scrolling) {
                                val info = gridState.layoutInfo
                                val firstVisible = info.visibleItemsInfo.firstOrNull() ?: return@collect
                                val row = firstVisible.index / columns
                                // If more than half the first row is scrolled off, snap to the next row
                                val snapToNext = firstVisible.offset.y < -(firstVisible.size.height / 2)
                                val targetRow = if (snapToNext) row + 1 else row
                                gridState.animateScrollToItem(targetRow * columns)
                            }
                        }
                }
            }
        }
    }
}

/**
 *
 * @param gridState The grid to scroll.
 * @param stickFlow The analog-stick value flow (–1..1).
 * @param deadZone Minimum absolute value before scrolling starts.
 * @param minSpeed Pixels-per-frame at the dead-zone edge.
 * @param maxSpeed Pixels-per-frame at full deflection.
 * @param quadratic Use a squared curve for acceleration (smoother ramp up).
 */
@Composable
fun JoystickGridScroll(
    gridState: LazyGridState,
    stickFlow: StateFlow<Float>?,
    deadZone: Float = 0.1f,
    minSpeed: Float = 1.25f,
    maxSpeed: Float = 8f,
    quadratic: Boolean = false,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    if (stickFlow == null) return

    LaunchedEffect(gridState) {
        stickFlow.collect { value ->
            if (abs(value) > deadZone) {
                while (abs(stickFlow.value) > deadZone) {
                    val current = stickFlow.value
                    val factor = abs(current)
                    val curve = if (quadratic) factor * factor else factor
                    val speed = minSpeed + (curve * (maxSpeed - minSpeed))
                    val direction = if (current > 0) 1f else -1f
                    gridState.dispatchRawDelta(speed * direction * density)
                    delay(16)
                }
            }
        }
    }
}
