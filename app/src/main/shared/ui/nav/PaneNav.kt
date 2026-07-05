package com.winlator.cmod.shared.ui.nav

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
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
    var h: Float,
    var onActivate: () -> Unit,
    var onAdjust: ((Int) -> Unit)?,
    var onSecondary: () -> Unit,
    var gx: Int? = null,
    var gy: Int? = null,
)

@Stable
internal class PaneNavRegistry(initialSignal: Int = -1) {
    private val items = mutableStateMapOf<Int, PaneNavEntry>()
    private var slotCounter = 0
    private var lastSignal = initialSignal
    var controllerActive by mutableStateOf(false)
    var onEdgeLeft: (() -> Unit)? = null
    var onEdgeRight: (() -> Unit)? = null
    var onEdgeUp: (() -> Unit)? = null
    var onEdgeDown: (() -> Unit)? = null
    var singleRow = false
    var overlay: PaneNavRegistry? = null
    var overlayClose: (() -> Unit)? = null
    var activeRow by mutableStateOf(0)
        private set
    var activeCol by mutableStateOf(0)
        private set

    var entrySlot: Int? = null
        private set
    private var pendingEntry = true
    var manualSelection = false
        private set

    var explicitGrid by mutableStateOf(false)
        private set
    private var activeSlot by mutableStateOf<Int?>(null)
    var stableCursor = false
    private var cursorSlot by mutableStateOf<Int?>(null)

    fun nextSlot(): Int = slotCounter++

    fun markEntry(slot: Int) {
        entrySlot = slot
        if (pendingEntry) selectSlot(slot)
    }

    fun reportCallbacks(slot: Int, onActivate: () -> Unit, onAdjust: ((Int) -> Unit)?, onSecondary: () -> Unit) {
        val e = items[slot]
        if (e == null) {
            items[slot] = PaneNavEntry(0f, 0f, 0f, onActivate, onAdjust, onSecondary)
        } else {
            e.onActivate = onActivate
            e.onAdjust = onAdjust
            e.onSecondary = onSecondary
        }
    }

    fun reportPosition(slot: Int, x: Float, y: Float, h: Float) {
        val e = items[slot] ?: return
        if (e.x != x || e.y != y || e.h != h) {
            items[slot] = PaneNavEntry(x, y, h, e.onActivate, e.onAdjust, e.onSecondary, e.gx, e.gy)
            if (pendingEntry) entrySlot?.let { selectSlot(it) }
        }
    }

    fun reportGrid(slot: Int, gx: Int, gy: Int) {
        explicitGrid = true
        val e = items[slot]
        if (e == null) {
            items[slot] = PaneNavEntry(0f, 0f, 0f, {}, null, {}, gx, gy)
        } else if (e.gx != gx || e.gy != gy) {
            items[slot] = PaneNavEntry(e.x, e.y, e.h, e.onActivate, e.onAdjust, e.onSecondary, gx, gy)
        }
        if (pendingEntry) entrySlot?.let { selectSlot(it) }
    }

    fun activeItemBounds(): Pair<Float, Float>? {
        if (stableCursor) {
            cursorSlot?.let { cs ->
                val e = items[cs] ?: return null
                return e.y to (e.y + e.h)
            }
        }
        val r = rows
        if (r.isEmpty()) return null
        val row = r[activeRow.coerceIn(0, r.size - 1)]
        val slot = row[activeCol.coerceIn(0, row.size - 1)]
        val e = items[slot] ?: return null
        return e.y to (e.y + e.h)
    }

    fun unregister(slot: Int) { items.remove(slot) }

    val rows: List<List<Int>>
        get() {
            if (singleRow) return if (items.isEmpty()) emptyList() else listOf(items.keys.sorted())
            val sorted = items.entries.sortedWith(compareBy({ it.value.y + it.value.h / 2f }, { it.value.x }))
            val result = mutableListOf<MutableList<Int>>()
            var prevCenterY = Float.NaN
            for (entry in sorted) {
                val centerY = entry.value.y + entry.value.h / 2f
                if (result.isEmpty() || kotlin.math.abs(centerY - prevCenterY) > PANE_ROW_Y_THRESHOLD) {
                    result.add(mutableListOf(entry.key))
                } else {
                    result.last().add(entry.key)
                }
                prevCenterY = centerY
            }
            return result
        }

    fun isActive(slot: Int): Boolean {
        if (!controllerActive) return false
        if (explicitGrid) return (activeSlot ?: entrySlot) == slot
        cursorSlot?.let { if (stableCursor) return it == slot }
        val r = rows
        if (r.isEmpty()) return false
        val row = r[activeRow.coerceIn(0, r.size - 1)]
        return row[activeCol.coerceIn(0, row.size - 1)] == slot
    }

    fun tapSelect(slot: Int) {
        pendingEntry = false
        manualSelection = true
        selectSlot(slot)
    }

    fun selectSlot(slot: Int) {
        if (explicitGrid) {
            activeSlot = slot
            return
        }
        cursorSlot = slot
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
        cursorSlot = entrySlot
        pendingEntry = true
        manualSelection = false
        entrySlot?.let { selectSlot(it) }
    }

    fun navDir(dir: Int) {
        val wasActive = controllerActive
        controllerActive = true
        if (!wasActive) {
            pendingEntry = false
            if (!manualSelection) entrySlot?.let { selectSlot(it) }
            manualSelection = false
            if (dir == PANE_DIR_ACTIVATE || dir == PANE_DIR_SECONDARY) handleNav(dir)
            return
        }
        pendingEntry = false
        handleNav(dir)
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
        pendingEntry = false
        manualSelection = false
        overlay?.let { ov ->
            ov.controllerActive = true
            ov.handleNav(dir)
            return
        }
        if (explicitGrid) {
            explicitHandleNav(dir)
            return
        }
        val r = rows
        if (r.isEmpty()) return
        var row = activeRow.coerceIn(0, r.size - 1)
        var col = activeCol.coerceIn(0, r[row].size - 1)
        if (stableCursor) {
            cursorSlot?.let { cs ->
                for (ri in r.indices) {
                    val ci = r[ri].indexOf(cs)
                    if (ci >= 0) {
                        row = ri
                        col = ci
                        break
                    }
                }
            }
        }
        when (dir) {
            PANE_DIR_UP -> if (row > 0) { row--; col = col.coerceAtMost(r[row].size - 1) } else onEdgeUp?.invoke()
            PANE_DIR_DOWN -> if (row < r.size - 1) { row++; col = col.coerceAtMost(r[row].size - 1) } else onEdgeDown?.invoke()
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
                if (r[row].size <= 1) items[r[row][0]]?.onAdjust?.invoke(1) else if (col < r[row].size - 1) col++ else onEdgeRight?.invoke()
            PANE_DIR_ACTIVATE -> items[r[row][col]]?.onActivate?.invoke()
            PANE_DIR_SECONDARY -> items[r[row][col]]?.onSecondary?.invoke()
        }
        activeRow = row
        activeCol = col
        if (stableCursor) cursorSlot = r.getOrNull(row)?.getOrNull(col) ?: cursorSlot
    }

    private fun explicitHandleNav(dir: Int) {
        val curSlot = activeSlot ?: entrySlot ?: return
        val cur = items[curSlot] ?: return
        if (dir == PANE_DIR_ACTIVATE) {
            cur.onActivate()
            return
        }
        if (dir == PANE_DIR_SECONDARY) {
            cur.onSecondary()
            return
        }
        val cx = cur.gx ?: return
        val cy = cur.gy ?: return
        var best: Int? = null
        var bestScore = Int.MAX_VALUE
        for ((slot, e) in items) {
            if (slot == curSlot) continue
            val ex = e.gx ?: continue
            val ey = e.gy ?: continue
            val inDir =
                when (dir) {
                    PANE_DIR_LEFT -> ex < cx
                    PANE_DIR_RIGHT -> ex > cx
                    PANE_DIR_UP -> ey < cy
                    PANE_DIR_DOWN -> ey > cy
                    else -> false
                }
            if (!inDir) continue
            val score =
                when (dir) {
                    PANE_DIR_LEFT -> kotlin.math.abs(ey - cy) * 1000 + (cx - ex)
                    PANE_DIR_RIGHT -> kotlin.math.abs(ey - cy) * 1000 + (ex - cx)
                    PANE_DIR_UP -> kotlin.math.abs(ex - cx) * 1000 + (cy - ey)
                    else -> kotlin.math.abs(ex - cx) * 1000 + (ey - cy)
                }
            if (score < bestScore) {
                bestScore = score
                best = slot
            }
        }
        if (best != null) {
            activeSlot = best
        } else when (dir) {
            PANE_DIR_LEFT -> onEdgeLeft?.invoke()
            PANE_DIR_RIGHT -> onEdgeRight?.invoke()
            PANE_DIR_UP -> onEdgeUp?.invoke()
            PANE_DIR_DOWN -> onEdgeDown?.invoke()
        }
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
    isEntry: Boolean = false,
    navRow: Int? = null,
    navCol: Int? = null,
    onHighlighted: () -> Unit = {},
    pinTop: Boolean = false,
): Modifier {
    val nav = LocalPaneNav.current ?: return this
    val slot = remember { nav.nextSlot() }
    DisposableEffect(slot) { onDispose { nav.unregister(slot) } }
    SideEffect {
        nav.reportCallbacks(slot, onActivate, onAdjust, onSecondary)
        if (navRow != null && navCol != null) nav.reportGrid(slot, navCol, navRow)
        if (isEntry) nav.markEntry(slot)
    }
    val highlighted = nav.isActive(slot)

    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            if (!nav.manualSelection) runCatching { bring.bringIntoView() }
            onHighlighted()
        }
    }

    val tapInteraction = remember { MutableInteractionSource() }
    return this
        .onGloballyPositioned {
            val p = it.positionInWindow()
            nav.reportPosition(slot, p.x, if (pinTop) -1_000_000f else p.y, it.size.height.toFloat())
        }
        .bringIntoViewRequester(bring)
        .pointerInput(slot) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                nav.tapSelect(slot)
            }
        }
        .then(
            if (tapToSelect) {
                Modifier.clickable(interactionSource = tapInteraction, indication = null) {
                    nav.tapSelect(slot)
                    onActivate()
                }
            } else {
                Modifier
            },
        )
        .paneHighlight(highlighted, cornerRadius, highlightColor)
}
