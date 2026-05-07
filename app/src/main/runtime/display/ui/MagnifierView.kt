package com.winlator.cmod.runtime.display.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.util.Callback
import kotlin.math.roundToInt

class MagnifierView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs) {
        private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        private var restoreSavedPosition = true
        private var zoomCallback: Callback<Float>? = null
        private var hideCallback: Runnable? = null
        private var zoomState: ((Float) -> Unit)? = null
        private var currentZoom = 1.0f

        private val composeView =
            androidx.compose.ui.platform.ComposeView(context).apply {
                layoutParams =
                    LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setViewCompositionStrategy(
                    androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow,
                )
                setContent {
                    WinNativeTheme {
                        MagnifierPanel(
                            initialZoom = currentZoom,
                            registerZoomSetter = { zoomState = it },
                            onZoomChange = { delta -> zoomCallback?.call(delta) },
                            onHide = { hideCallback?.run() },
                            onDrag = { dx, dy ->
                                movePanel(this@MagnifierView.x + dx, this@MagnifierView.y + dy)
                            },
                            onDragEnd = { saveCurrentOffset() },
                        )
                    }
                }
            }

        init {
            layoutParams =
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            addView(composeView)
        }

        fun setZoomButtonCallback(callback: Callback<Float>) {
            zoomCallback = callback
        }

        fun setHideButtonCallback(callback: Runnable) {
            hideCallback = callback
        }

        fun setZoomValue(value: Float) {
            currentZoom = value
            zoomState?.invoke(value)
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)

            if (!restoreSavedPosition) return
            val padding = panelPaddingPx()
            val saved = readSavedOffset()
            val parentView = parent as? ViewGroup
            val targetX = saved?.first ?: ((parentView?.width ?: 0) - width - padding)
            val targetY = saved?.second ?: padding
            movePanel(targetX, targetY)
            restoreSavedPosition = false
        }

        private fun movePanel(
            newX: Float,
            newY: Float,
        ) {
            val parentView = parent as? ViewGroup ?: return
            val padding = panelPaddingPx()
            val maxX = (parentView.width - width - padding).coerceAtLeast(padding)
            val maxY = (parentView.height - height - padding).coerceAtLeast(padding)
            x = newX.coerceIn(padding, maxX)
            y = newY.coerceIn(padding, maxY)
        }

        private fun saveCurrentOffset() {
            preferences
                .edit()
                .putString("magnifier_view", "${x.roundToInt()}|${y.roundToInt()}")
                .apply()
        }

        private fun readSavedOffset(): Pair<Float, Float>? {
            val raw = preferences.getString("magnifier_view", null) ?: return null
            return runCatching {
                val parts = raw.split("|")
                if (parts.size < 2) return null
                parts[0].toFloat() to parts[1].toFloat()
            }.getOrNull()
        }

        private fun panelPaddingPx(): Float = 8f * resources.displayMetrics.density
    }

@Composable
private fun MagnifierPanel(
    initialZoom: Float,
    registerZoomSetter: ((Float) -> Unit) -> Unit,
    onZoomChange: (Float) -> Unit,
    onHide: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var zoomLabel by remember { mutableStateOf("${(initialZoom * 100).toInt()}%") }
    LaunchedEffect(Unit) {
        registerZoomSetter { value ->
            zoomLabel = "${(value * 100).toInt()}%"
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE60F1115),
        contentColor = Color(0xFFE7ECF3),
        modifier = Modifier.shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp), clip = false),
    ) {
        Column(
            modifier =
                Modifier
                    .width(44.dp)
                    .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { onDragEnd() }) { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenWith,
                    contentDescription = "Move",
                    tint = Color(0xFFB7C0CC),
                    modifier = Modifier.size(16.dp),
                )
            }

            ToolbarIconButton(icon = Icons.Outlined.Add, description = "Zoom in") {
                onZoomChange(0.25f)
            }

            Text(
                text = zoomLabel,
                color = Color(0xFFE7ECF3),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )

            ToolbarIconButton(icon = Icons.Outlined.Remove, description = "Zoom out") {
                onZoomChange(-0.25f)
            }

            Box(
                modifier =
                    Modifier
                        .padding(vertical = 3.dp)
                        .height(1.dp)
                        .width(20.dp)
                        .background(Color(0x33FFFFFF), CircleShape),
            )

            ToolbarIconButton(icon = Icons.Outlined.Close, description = "Hide") {
                onHide()
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = Color(0xFFE7ECF3),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
        )
    }
}
