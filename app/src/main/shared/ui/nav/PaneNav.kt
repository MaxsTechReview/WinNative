package com.winlator.cmod.shared.ui.nav

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val PANE_DIR_LEFT = 1
internal const val PANE_DIR_RIGHT = 2
internal const val PANE_DIR_UP = 3
internal const val PANE_DIR_DOWN = 4
internal const val PANE_DIR_ACTIVATE = 5
internal const val PANE_DIR_SECONDARY = 6
internal const val PANE_ROW_Y_THRESHOLD = 24f

private val PaneDefaultHighlight = Color(0xFF2196F3)

private class PaneNavEntry(
    var x: Float,
    var y: Float,
    var onActivate: () -> Unit,
    var onAdjust: ((Int) -> Unit)?,
    var onSecondary: () -> Unit,
)

@Stable
internal class PaneNavRegistry(initialSignal: Int = -1) {
    private val items = mutableStateMapOf<Int, PaneNavEntry>()
    private var slotCounter = 0
    private var lastSignal = initialSignal
    var controllerActive by mutableStateOf(false)
    var onEdgeLeft: (() -> Unit)? = null
    var activeRow by mutableStateOf(0)
        private set
    var activeCol by mutableStateOf(0)
        private set

    fun nextSlot(): Int = slotCounter++

    fun reportCallbacks(slot: Int, onActivate: () -> Unit, onAdjust: ((Int) -> Unit)?, onSecondary: () -> Unit) {
        val e = items[slot]
        if (e == null) {
            items[slot] = PaneNavEntry(0f, 0f, onActivate, onAdjust, onSecondary)
        } else {
            e.onActivate = onActivate
            e.onAdjust = onAdjust
            e.onSecondary = onSecondary
        }
    }

    fun reportPosition(slot: Int, x: Float, y: Float) {
        val e = items[slot] ?: return
        if (e.x != x || e.y != y) {
            items[slot] = PaneNavEntry(x, y, e.onActivate, e.onAdjust, e.onSecondary)
        }
    }

    fun unregister(slot: Int) { items.remove(slot) }

    val rows: List<List<Int>>
        get() {
            val sorted = items.entries.sortedWith(compareBy({ it.value.y }, { it.value.x }))
            val result = mutableListOf<MutableList<Int>>()
            var prevY = Float.NaN
            for (entry in sorted) {
                val y = entry.value.y
                if (result.isEmpty() || kotlin.math.abs(y - prevY) > PANE_ROW_Y_THRESHOLD) {
                    result.add(mutableListOf(entry.key))
                } else {
                    result.last().add(entry.key)
                }
                prevY = y
            }
            return result
        }

    fun isActive(slot: Int): Boolean {
        if (!controllerActive) return false
        val r = rows
        if (r.isEmpty()) return false
        val row = r[activeRow.coerceIn(0, r.size - 1)]
        return row[activeCol.coerceIn(0, row.size - 1)] == slot
    }

    fun selectSlot(slot: Int) {
        val r = rows
        for (ri in r.indices) {
            val ci = r[ri].indexOf(slot)
            if (ci >= 0) {
                activeRow = ri
                activeCol = ci
                return
            }
        }
    }

    fun reset() {
        activeRow = 0
        activeCol = 0
    }

    fun processNav(signal: Int, dir: Int) {
        if (lastSignal == -1) {
            lastSignal = signal
            return
        }
        if (signal == lastSignal) return
        lastSignal = signal
        handleNav(dir)
    }

    private fun handleNav(dir: Int) {
        val r = rows
        if (r.isEmpty()) return
        var row = activeRow.coerceIn(0, r.size - 1)
        var col = activeCol.coerceIn(0, r[row].size - 1)
        when (dir) {
            PANE_DIR_UP -> if (row > 0) { row--; col = col.coerceAtMost(r[row].size - 1) }
            PANE_DIR_DOWN -> if (row < r.size - 1) { row++; col = col.coerceAtMost(r[row].size - 1) }
            PANE_DIR_LEFT ->
                if (r[row].size <= 1) {
                    val adjust = items[r[row][0]]?.onAdjust
                    if (adjust != null) adjust(-1) else onEdgeLeft?.invoke()
                } else if (col > 0) {
                    col--
                } else {
                    onEdgeLeft?.invoke()
                }
            PANE_DIR_RIGHT ->
                if (r[row].size <= 1) items[r[row][0]]?.onAdjust?.invoke(1) else if (col < r[row].size - 1) col++
            PANE_DIR_ACTIVATE -> items[r[row][col]]?.onActivate?.invoke()
            PANE_DIR_SECONDARY -> items[r[row][col]]?.onSecondary?.invoke()
        }
        activeRow = row
        activeCol = col
    }
}

internal val LocalPaneNav = staticCompositionLocalOf<PaneNavRegistry?> { null }

internal fun Modifier.paneHighlight(
    highlighted: Boolean,
    cornerRadius: Dp,
    highlightColor: Color = PaneDefaultHighlight,
): Modifier =
    drawWithContent {
        val cr = cornerRadius.toPx()
        if (highlighted) {
            drawRoundRect(color = highlightColor.copy(alpha = 0.20f), cornerRadius = CornerRadius(cr, cr))
        }
        drawContent()
        if (highlighted) {
            drawRoundRect(
                color = highlightColor,
                cornerRadius = CornerRadius(cr, cr),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.paneNavItem(
    cornerRadius: Dp = 10.dp,
    onActivate: () -> Unit = {},
    onAdjust: ((Int) -> Unit)? = null,
    onSecondary: () -> Unit = {},
    highlightColor: Color = PaneDefaultHighlight,
    tapToSelect: Boolean = false,
): Modifier {
    val nav = LocalPaneNav.current ?: return this
    val slot = remember { nav.nextSlot() }
    DisposableEffect(slot) { onDispose { nav.unregister(slot) } }
    SideEffect { nav.reportCallbacks(slot, onActivate, onAdjust, onSecondary) }
    val highlighted = nav.isActive(slot)

    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) { if (highlighted) runCatching { bring.bringIntoView() } }

    val tapInteraction = remember { MutableInteractionSource() }
    return this
        .onGloballyPositioned {
            val p = it.positionInWindow()
            nav.reportPosition(slot, p.x, p.y)
        }
        .bringIntoViewRequester(bring)
        .then(
            if (tapToSelect) {
                Modifier.clickable(interactionSource = tapInteraction, indication = null) {
                    nav.selectSlot(slot)
                    onActivate()
                }
            } else {
                Modifier
            },
        )
        .paneHighlight(highlighted, cornerRadius, highlightColor)
}
