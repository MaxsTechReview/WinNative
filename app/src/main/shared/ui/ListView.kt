package com.winlator.cmod.shared.ui
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Vertical scrolling list layout for library items.
 *
 * Each item occupies the full width and is rendered as a themed card row.
 *
 * @param items The data to display.
 * @param modifier Outer modifier.
 * @param listState Shared [LazyListState] for external scroll control.
 * @param contentPadding Extra padding inside the list.
 * @param selectedIndex Currently focused item index (for gamepad highlight).
 * @param onSelectedIndexChanged Called when focused item changes via scroll.
 * @param itemContent Composable for each item; receives item, index, and selection state.
 */
@Composable
fun <T> ListView(
    items: List<T>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedIndex: Int = 0,
    onSelectedIndexChanged: (Int) -> Unit = {},
    keyOf: ((T) -> Any)? = null,
    itemContent: @Composable (item: T, index: Int, isSelected: Boolean) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val effectiveContentPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + navBottomInset,
        )

    // Scroll to selected index when changed externally (d-pad)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = effectiveContentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
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
            Box(
                modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                itemContent(item, index, index == selectedIndex)
            }
        }
    }
}

/**
 * Drives vertical scrolling of a [LazyListState] from an analog-stick flow.
 *
 * @param listState The list to scroll.
 * @param stickFlow The analog-stick Y-axis value flow (–1..1).
 * @param deadZone Minimum absolute value before scrolling starts.
 * @param minSpeed Pixels-per-frame at the dead-zone edge.
 * @param maxSpeed Pixels-per-frame at full deflection.
 * @param quadratic Use a squared curve for acceleration.
 */
@Composable
fun JoystickListScroll(
    listState: LazyListState,
    stickFlow: StateFlow<Float>?,
    deadZone: Float = 0.1f,
    minSpeed: Float = 1.25f,
    maxSpeed: Float = 8f,
    quadratic: Boolean = false,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    if (stickFlow == null) return

    LaunchedEffect(listState) {
        stickFlow.collect { value ->
            if (abs(value) > deadZone) {
                while (abs(stickFlow.value) > deadZone) {
                    val current = stickFlow.value
                    val factor = abs(current)
                    val curve = if (quadratic) factor * factor else factor
                    val speed = minSpeed + (curve * (maxSpeed - minSpeed))
                    val direction = if (current > 0) 1f else -1f
                    listState.dispatchRawDelta(speed * direction * density)
                    delay(16)
                }
            }
        }
    }
}
