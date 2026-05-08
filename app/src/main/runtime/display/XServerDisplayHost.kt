package com.winlator.cmod.runtime.display

import android.widget.FrameLayout
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

const val XSERVER_DRAWER_EDGE_SWIPE_DP = 35

private val DrawerWidth = 340.dp
private val DrawerStartPadding = 6.dp
private val DrawerVerticalPadding = 6.dp
private const val DrawerSettleAnimationMs = 200
private const val DrawerOpenSettleThreshold = 0.4f
private const val DrawerCloseSettleThreshold = 0.65f
private val DrawerSettleAnimationSpec =
    tween<Float>(
        durationMillis = DrawerSettleAnimationMs,
        easing = LinearEasing,
    )

interface XServerDisplayHostCallbacks {
    fun onDrawerSlide()

    fun onDrawerOpened()

    fun onDrawerClosed()

    fun onDrawerGestureClaimed()

    fun onDialogVisibilityChanged(visible: Boolean)
}

fun setupXServerDisplayHost(
    composeView: ComposeView,
    displayFrame: FrameLayout,
    stateHolder: XServerDrawerStateHolder,
    listener: XServerDrawerActionListener,
    callbacks: XServerDisplayHostCallbacks,
) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        XServerDisplayHost(
            displayFrame = displayFrame,
            stateHolder = stateHolder,
            listener = listener,
            callbacks = callbacks,
        )
    }
}

@Composable
private fun XServerDisplayHost(
    displayFrame: FrameLayout,
    stateHolder: XServerDrawerStateHolder,
    listener: XServerDrawerActionListener,
    callbacks: XServerDisplayHostCallbacks,
) {
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    var drawerOffsetPx by remember { mutableFloatStateOf(0f) }
    var drawerWidthPx by remember { mutableFloatStateOf(0f) }
    val drawerClosedOffset =
        if (drawerWidthPx > 0f) {
            -drawerWidthPx - with(density) { DrawerStartPadding.toPx() }
        } else {
            0f
        }
    val drawerOpenOffset = 0f
    val drawerSheetVisible = drawerWidthPx <= 0f ||
        drawerOffsetPx > drawerClosedOffset + 1f ||
        stateHolder.isDrawerOpen
    val dialogVisible = false

    DisposableEffect(stateHolder) {
        stateHolder.setPaneVisibilityListener { }
        onDispose {
            stateHolder.clearPaneVisibilityListener()
        }
    }

    LaunchedEffect(drawerWidthPx) {
        if (drawerWidthPx > 0f && drawerOffsetPx == 0f && !stateHolder.isDrawerOpen) {
            drawerOffsetPx = drawerClosedOffset
        }
    }

    LaunchedEffect(stateHolder.isDrawerOpen, drawerWidthPx) {
        if (drawerWidthPx <= 0f) return@LaunchedEffect
        val target = if (stateHolder.isDrawerOpen) drawerOpenOffset else drawerClosedOffset
        if (drawerOffsetPx != target) {
            callbacks.onDrawerSlide()
            animate(
                initialValue = drawerOffsetPx,
                targetValue = target,
                animationSpec = DrawerSettleAnimationSpec,
            ) { value, _ ->
                drawerOffsetPx = value
                callbacks.onDrawerSlide()
            }
            if (stateHolder.isDrawerOpen) {
                callbacks.onDrawerOpened()
            } else {
                callbacks.onDrawerClosed()
            }
        }
    }

    LaunchedEffect(dialogVisible) {
        callbacks.onDialogVisibilityChanged(dialogVisible)
    }

    WinNativeTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(drawerWidthPx, dialogVisible) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (dialogVisible || drawerWidthPx <= 0f) return@awaitEachGesture

                            val edgeWidthPx = XSERVER_DRAWER_EDGE_SWIPE_DP.dp.toPx()
                            val canStartFromHere =
                                if (stateHolder.isDrawerOpen) {
                                    down.position.x >= drawerWidthPx &&
                                        down.position.x <= drawerWidthPx + edgeWidthPx
                                } else {
                                    down.position.x <= edgeWidthPx
                                }
                            if (!canStartFromHere) {
                                if (stateHolder.isDrawerOpen && down.position.x > drawerWidthPx) {
                                    stateHolder.closeDrawer()
                                }
                                return@awaitEachGesture
                            }

                            var gestureClaimed = false
                            var cancelledByVerticalDrag = false
                            var totalDx = 0f
                            var totalDy = 0f
                            var dragStartOffset = drawerOffsetPx

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) break

                                val delta = change.positionChange()
                                totalDx += delta.x
                                totalDy += delta.y

                                if (!gestureClaimed) {
                                    if (abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)) {
                                        cancelledByVerticalDrag = true
                                        break
                                    }
                                    val horizontalDragClaimed =
                                        if (stateHolder.isDrawerOpen) {
                                            totalDx < -viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                                        } else {
                                            totalDx > viewConfiguration.touchSlop && totalDx > abs(totalDy)
                                        }
                                    if (horizontalDragClaimed) {
                                        gestureClaimed = true
                                        dragStartOffset = drawerOffsetPx
                                        totalDx = 0f
                                        callbacks.onDrawerGestureClaimed()
                                    }
                                }

                                if (gestureClaimed) {
                                    change.consume()
                                    val nextOffset =
                                        (dragStartOffset + totalDx)
                                            .coerceIn(drawerClosedOffset, drawerOpenOffset)
                                    drawerOffsetPx = nextOffset
                                    callbacks.onDrawerSlide()
                                }
                            }

                            if (gestureClaimed && !cancelledByVerticalDrag) {
                                val drawerOpenProgress =
                                    if (drawerClosedOffset < 0f) {
                                        ((drawerOffsetPx - drawerClosedOffset) / -drawerClosedOffset)
                                            .coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                val shouldOpen =
                                    if (stateHolder.isDrawerOpen) {
                                        drawerOpenProgress > DrawerCloseSettleThreshold
                                    } else {
                                        drawerOpenProgress >= DrawerOpenSettleThreshold
                                    }
                                val target = if (shouldOpen) drawerOpenOffset else drawerClosedOffset
                                animationScope.launch {
                                    animate(
                                        initialValue = drawerOffsetPx,
                                        targetValue = target,
                                        animationSpec = DrawerSettleAnimationSpec,
                                    ) { value, _ ->
                                        drawerOffsetPx = value
                                        callbacks.onDrawerSlide()
                                    }
                                    if (shouldOpen) {
                                        if (!stateHolder.isDrawerOpen) stateHolder.openDrawer()
                                        callbacks.onDrawerOpened()
                                    } else {
                                        if (stateHolder.isDrawerOpen) stateHolder.closeDrawer()
                                        callbacks.onDrawerClosed()
                                    }
                                }
                            }
                        }
                    },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                AndroidView(
                    factory = { displayFrame },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(0f),
                    update = {},
                )
            }

            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(20.dp),
                drawerContainerColor = PaneSurfaceColor,
                drawerContentColor = Color.Unspecified,
                drawerTonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier =
                    Modifier
                        .zIndex(2f)
                        .padding(start = DrawerStartPadding, top = DrawerVerticalPadding, bottom = DrawerVerticalPadding)
                        .fillMaxHeight()
                        .width(DrawerWidth)
                        .onSizeChanged { size ->
                            drawerWidthPx = size.width.toFloat()
                        }
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                drawerOffsetPx.roundToInt(),
                                0,
                            )
                        },
            ) {
                if (drawerSheetVisible) {
                    XServerDrawerContent(
                        state = stateHolder.state,
                        taskManagerState = stateHolder.taskManagerState,
                        logsState = stateHolder.logsState,
                        openPane = stateHolder.openPane,
                        onOpenPaneChange = { stateHolder.setOpenPaneAndNotify(it) },
                        listener = listener,
                        onDismiss = { stateHolder.closeDrawer() },
                    )
                }
            }
        }
    }
}
