package com.winlator.cmod.runtime.display

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.winlator.cmod.shared.theme.WinNativeTheme

const val XSERVER_DRAWER_EDGE_SWIPE_DP = 120

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
                gesturesEnabled = drawerState.isOpen && !dialogVisible,
                scrimColor = Color.Transparent,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerShape = RoundedCornerShape(20.dp),
                        drawerContainerColor = PaneSurfaceColor,
                        drawerContentColor = Color.Unspecified,
                        drawerTonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier =
                            Modifier
                                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                                .fillMaxHeight()
                                .width(DrawerWidth),
                    ) {
                        if (drawerContentVisible) {
                            XServerDrawerContent(
                                state = stateHolder.state,
                                taskManagerState = stateHolder.taskManagerState,
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
                        modifier = Modifier.fillMaxSize(),
                        update = {},
                    )
                }
            }
        }
    }
}
