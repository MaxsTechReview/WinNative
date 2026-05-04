package com.winlator.cmod.runtime.display

import android.widget.FrameLayout
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.winlator.cmod.shared.theme.WinNativeTheme

const val XSERVER_DRAWER_EDGE_SWIPE_DP = 140

private val DrawerWidth = 340.dp

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
    val density = LocalDensity.current
    val edgeSwipePx = with(density) { XSERVER_DRAWER_EDGE_SWIPE_DP.dp.toPx() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    DisposableEffect(stateHolder) {
        stateHolder.setPaneVisibilityListener { }
        onDispose {
            stateHolder.clearPaneVisibilityListener()
        }
    }

    LaunchedEffect(stateHolder.isDrawerOpen) {
        if (stateHolder.isDrawerOpen) {
            if (!drawerState.isOpen) {
                callbacks.onDrawerSlide()
                drawerState.open()
            }
        } else if (!drawerState.isClosed) {
            callbacks.onDrawerSlide()
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState) {
        var initialized = false
        snapshotFlow { drawerState.currentValue }
            .collect { value ->
                if (!initialized) {
                    initialized = true
                    return@collect
                }
                if (value == DrawerValue.Open) {
                    if (!stateHolder.isDrawerOpen) stateHolder.openDrawer()
                    callbacks.onDrawerOpened()
                } else {
                    if (stateHolder.isDrawerOpen) stateHolder.closeDrawer()
                    callbacks.onDrawerClosed()
                }
            }
    }

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentOffset }
            .collect { offset ->
                if (!offset.isNaN()) callbacks.onDrawerSlide()
            }
    }

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }
            .collect { value ->
                if (value == DrawerValue.Open && !drawerState.isOpen) {
                    callbacks.onDrawerGestureClaimed()
                }
            }
    }

    val drawerInMotion =
        drawerState.currentValue == DrawerValue.Open ||
            drawerState.targetValue == DrawerValue.Open ||
            stateHolder.isDrawerOpen
    val drawerContentVisible = drawerInMotion
    val dialogVisible = false

    LaunchedEffect(dialogVisible) {
        callbacks.onDialogVisibilityChanged(dialogVisible)
    }

    WinNativeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !dialogVisible,
                scrimColor = Color.Transparent,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerShape = RectangleShape,
                        drawerContainerColor = PaneSurfaceColor,
                        drawerContentColor = Color.Unspecified,
                        drawerTonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(DrawerWidth),
                    ) {
                        if (drawerContentVisible) {
                            XServerDrawerContent(
                                state = stateHolder.state,
                                openPane = stateHolder.openPane,
                                onOpenPaneChange = { stateHolder.setOpenPaneAndNotify(it) },
                                listener = listener,
                                onDismiss = { stateHolder.closeDrawer() },
                            )
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
                                .xServerDrawerEdgeGate(
                                    enabled = drawerState.isClosed,
                                    edgeWidthPx = edgeSwipePx,
                                ),
                        update = {},
                    )
                }
            }

        }
    }
}

private fun Modifier.xServerDrawerEdgeGate(
    enabled: Boolean,
    edgeWidthPx: Float,
): Modifier =
    pointerInput(enabled, edgeWidthPx) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x <= edgeWidthPx) continue

                val pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }
            }
        }
    }

