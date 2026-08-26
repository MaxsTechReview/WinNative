package com.winlator.cmod.shared.ui
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

enum class ViewMode { Grid }

/**
 * Portrait-aware column count for [FourByTwoGridView] when `columns` is left null.
 * Landscape/tablet-width containers keep the original 4-wide shelf; narrower
 * (phone-portrait) widths drop to fewer, larger tiles so art stays legible.
 */
private fun adaptiveGridColumns(availableWidth: Dp): Int =
    when {
        availableWidth < 340.dp -> 2
        availableWidth < 560.dp -> 3
        else -> 4
    }

/**
 * Portrait-aware visible-row count. In landscape the grid is sized as a fixed
 * "shelf" that exactly fits 2 rows into the available height. In portrait the
 * container is much taller than it is wide, so forcing 2 rows to fill that
 * height would blow tiles up huge; instead we let row height follow column
 * width (the existing `availableColumnWidth * 1.25f` cap) by handing the
 * height-based calculation a much larger row count so it stops being the
 * limiting factor, giving a normal scrolling multi-row grid instead.
 */
private fun adaptiveVisibleRows(isPortrait: Boolean): Int = if (isPortrait) 8 else 2

/**
 * Unified grid layout used by store tabs
 *
 * @param items The data to display.
 * @param modifier Outer modifier (padding, size, etc.).
 * @param columns Number of grid columns, or `null` to pick one automatically based on
 *   available width (2 columns on narrow phone-portrait widths, 3 on wider portrait/small
 *   tablet, 4 on landscape/tablet — matching the previous fixed default).
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
    columns: Int? = null,
    spacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gridState: LazyGridState = rememberLazyGridState(),
    clipContent: Boolean = true,
    viewMode: ViewMode = ViewMode.Grid,
    keyOf: ((T) -> Any)? = null,
    itemContent: @Composable (item: T, index: Int, rowHeight: Dp) -> Unit,
) {
    when (viewMode) {
        ViewMode.Grid -> {
            BoxWithConstraints(modifier.fillMaxSize()) {
                val isPortrait = maxHeight > maxWidth
                val resolvedColumns = (columns ?: adaptiveGridColumns(maxWidth)).coerceAtLeast(1)
                val visibleRows = adaptiveVisibleRows(isPortrait)
                val layoutDirection = LocalLayoutDirection.current
                val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val effectiveContentPadding =
                    PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding() + navBottomInset,
                    )
                // Visible rows: 2 in landscape (fixed shelf), more in portrait so height
                // stops constraining tile size and width takes over instead.
                val verticalInset =
                    effectiveContentPadding.calculateTopPadding() +
                        effectiveContentPadding.calculateBottomPadding()
                val horizontalInset =
                    effectiveContentPadding.calculateStartPadding(layoutDirection) +
                        effectiveContentPadding.calculateEndPadding(layoutDirection)
                val effectiveColumns = resolvedColumns
                val availableRowHeight = ((maxHeight - spacing - verticalInset) / visibleRows).coerceAtLeast(1.dp)
                val availableColumnWidth =
                    ((maxWidth - horizontalInset - spacing * (effectiveColumns - 1).toFloat()) / effectiveColumns.toFloat())
                        .coerceAtLeast(1.dp)
                val targetRowHeight = minOf(availableRowHeight, availableColumnWidth * 1.25f)
                val rowHeight = targetRowHeight

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(resolvedColumns),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = effectiveContentPadding,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(if (!clipContent) Modifier.graphicsLayer { clip = false } else Modifier),
                ) {
                    itemsIndexed(
                        items = items,
                        key =
                            if (keyOf != null) {
                                { _, item -> keyOf(item) }
                            } else {
                                null
                            },
                    ) { index, item ->
                        itemContent(item, index, rowHeight)
                    }
                }

                // Snap to nearest row when mouse/touch scroll ends. Only meaningful for the
                // fixed 2-row landscape shelf; in portrait's normal scrolling grid there's no
                // fixed row window to snap to, so skip it there.
                if (!isPortrait) {
                    LaunchedEffect(gridState, resolvedColumns) {
                        snapshotFlow { gridState.isScrollInProgress }
                            .collect { scrolling ->
                                if (!scrolling) {
                                    val info = gridState.layoutInfo
                                    val firstVisible = info.visibleItemsInfo.firstOrNull() ?: return@collect
                                    val row = firstVisible.index / resolvedColumns
                                    // If more than half the first row is scrolled off, snap to the next row
                                    val snapToNext = firstVisible.offset.y < -(firstVisible.size.height / 2)
                                    val targetRow = if (snapToNext) row + 1 else row
                                    gridState.scrollToItem(targetRow * resolvedColumns)
                                }
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
