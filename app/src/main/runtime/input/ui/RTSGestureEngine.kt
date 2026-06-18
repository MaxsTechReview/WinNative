package com.winlator.cmod.runtime.input.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.winlator.cmod.runtime.display.xserver.Pointer
import com.winlator.cmod.runtime.display.xserver.XKeycode
import com.winlator.cmod.runtime.display.xserver.XServer
import com.winlator.cmod.runtime.input.controls.Binding
import com.winlator.cmod.shared.math.XForm

// Multi-finger gesture detector -> Bindings. Isolated, opt-in; never mutates the trackpad path.
class RTSGestureEngine(
    private val xServer: XServer,
    private val xform: FloatArray,
) {
    private enum class PanMode { NONE, PAN }

    private val handler = Handler(Looper.getMainLooper())

    private var config = TouchGestureConfig()
    private val owned = ArrayList<Int>()
    private val positions = HashMap<Int, FloatArray>()
    private var fingerCount = 0
    private var maxFingerCount = 0
    private var sessionStartMs = 0L
    private var startCx = 0f
    private var startCy = 0f

    private val pressedKeys = HashSet<XKeycode>()
    private val pressedButtons = HashSet<Pointer.Button>()
    private val pressedGamepad = HashSet<Binding>()

    private var holdFired = false
    private var heldBinding = Binding.NONE
    private var heldBehavior = HoldBehavior.HOLD
    private var movedBeyondTap = false

    private var dragActive = false
    private var panMode = PanMode.NONE
    private var accumPan = 0f
    private var zoomActive = false
    private var lastDistance = 0f
    private var lastCx = 0f
    private var lastCy = 0f
    private var zoomAccum = 0f
    private val panKeys = HashSet<XKeycode>()

    private var lastTapMs = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val holdRunnable = Runnable { fireHold() }

    fun setConfig(newConfig: TouchGestureConfig) {
        config = newConfig
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                if (fingerCount == 0) startSession()
                if (!owned.contains(id)) {
                    owned.add(id)
                    positions[id] = floatArrayOf(event.getX(index), event.getY(index))
                    fingerCount++
                    if (fingerCount > maxFingerCount) maxFingerCount = fingerCount
                }
                val c = centroid()
                startCx = c[0]; startCy = c[1]
                onFingerCountChanged()
            }
            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (id in owned) {
                    val idx = event.findPointerIndex(id)
                    if (idx >= 0) {
                        positions[id]?.let { it[0] = event.getX(idx); it[1] = event.getY(idx); changed = true }
                    }
                }
                if (changed) onMove()
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val id = event.getPointerId(event.actionIndex)
                if (owned.contains(id)) {
                    owned.remove(id)
                    positions.remove(id)
                    fingerCount--
                    if (fingerCount <= 0) finalizeSession() else onFingerCountChanged()
                }
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    private fun startSession() {
        owned.clear(); positions.clear()
        maxFingerCount = 0
        sessionStartMs = SystemClock.uptimeMillis()
        holdFired = false; movedBeyondTap = false; dragActive = false
        panMode = PanMode.NONE; accumPan = 0f; zoomAccum = 0f; zoomActive = false
    }

    private fun onFingerCountChanged() {
        handler.removeCallbacks(holdRunnable)
        // Release prior continuous state so the new finger count re-classifies cleanly.
        if (panMode != PanMode.NONE || dragActive) {
            releaseContinuousInputs()
            panMode = PanMode.NONE; dragActive = false
            accumPan = 0f; zoomAccum = 0f; zoomActive = false
        }
        if (holdFired) {
            if (heldBehavior == HoldBehavior.HOLD) releaseBinding(heldBinding)
            holdFired = false; heldBinding = Binding.NONE
        }
        val centroid = centroid()
        lastCx = centroid[0]; lastCy = centroid[1]
        if (fingerCount == 2) lastDistance = distance()
        val delay = holdDelayFor(fingerCount)
        if (delay > 0 && holdBindingFor(fingerCount) != Binding.NONE) {
            handler.postDelayed(holdRunnable, delay.toLong())
        }
    }

    private fun releaseContinuousInputs() {
        for (k in panKeys.toList()) xServer.injectKeyRelease(k)
        panKeys.clear()
        if (panMode == PanMode.PAN) releaseButton(Pointer.Button.BUTTON_MIDDLE)
        if (dragActive) releaseButton(Pointer.Button.BUTTON_LEFT)
    }

    private fun onMove() {
        val centroid = centroid()
        val travel = Math.hypot((centroid[0] - startCx).toDouble(), (centroid[1] - startCy).toDouble()).toFloat()
        if (travel > TAP_TRAVEL_MAX && !movedBeyondTap) {
            movedBeyondTap = true
            handler.removeCallbacks(holdRunnable)
        }
        when (fingerCount) {
            1 -> handleOneFingerMove(centroid)
            2 -> handleTwoFingerMove(centroid)
        }
        lastCx = centroid[0]; lastCy = centroid[1]
    }

    private fun handleOneFingerMove(centroid: FloatArray) {
        if (holdFired) return
        if (config.dragAction != DragAction.NONE) {
            if (!dragActive) {
                val travel = Math.hypot((centroid[0] - startCx).toDouble(), (centroid[1] - startCy).toDouble()).toFloat()
                if (travel < config.dragThreshold) return
                dragActive = true
                if (config.dragAction == DragAction.LEFT_DRAG) {
                    moveCursorTo(centroid[0], centroid[1])
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                    pressedButtons.add(Pointer.Button.BUTTON_LEFT)
                }
            }
            moveCursorTo(centroid[0], centroid[1])
            return
        }
        if (config.pan1Action != PanAction.NONE) {
            if (panMode == PanMode.NONE) {
                val travel = Math.hypot((centroid[0] - startCx).toDouble(), (centroid[1] - startCy).toDouble()).toFloat()
                if (travel < config.gestureThreshold) return
                handler.removeCallbacks(holdRunnable)
                panMode = PanMode.PAN
            }
            if (panMode == PanMode.PAN) performPan(config.pan1Action, centroid[0] - lastCx, centroid[1] - lastCy)
        }
    }

    private fun handleTwoFingerMove(centroid: FloatArray) {
        val dist = distance()
        // Pinch zoom is its own function: it runs alongside any 2-finger pan/drag, driven by finger-distance change.
        if (config.zoomAction != ZoomAction.NONE) {
            zoomAccum += dist - lastDistance
            while (Math.abs(zoomAccum) >= ZOOM_STEP) {
                val zoomIn = zoomAccum > 0
                performZoom(zoomIn)
                zoomAccum -= if (zoomIn) ZOOM_STEP else -ZOOM_STEP
                zoomActive = true
                handler.removeCallbacks(holdRunnable)
            }
        }
        lastDistance = dist
        // Translation gesture: 2-finger pan (priority) or 2-finger drag.
        if (config.panAction != PanAction.NONE) {
            if (panMode == PanMode.NONE) {
                accumPan += Math.hypot((centroid[0] - lastCx).toDouble(), (centroid[1] - lastCy).toDouble()).toFloat()
                if (accumPan > config.gestureThreshold) {
                    handler.removeCallbacks(holdRunnable)
                    panMode = PanMode.PAN
                }
            }
            if (panMode == PanMode.PAN) performPan(config.panAction, centroid[0] - lastCx, centroid[1] - lastCy)
        } else if (config.drag2Action != DragAction.NONE) {
            handleTwoFingerDrag(centroid)
        }
    }

    private fun handleTwoFingerDrag(centroid: FloatArray) {
        if (holdFired) return
        if (!dragActive) {
            val travel = Math.hypot((centroid[0] - startCx).toDouble(), (centroid[1] - startCy).toDouble()).toFloat()
            if (travel < config.dragThreshold) return
            handler.removeCallbacks(holdRunnable)
            dragActive = true
            if (config.drag2Action == DragAction.LEFT_DRAG) {
                moveCursorTo(centroid[0], centroid[1])
                xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                pressedButtons.add(Pointer.Button.BUTTON_LEFT)
            }
        }
        moveCursorTo(centroid[0], centroid[1])
    }

    private fun finalizeSession() {
        handler.removeCallbacks(holdRunnable)
        val continuous = holdFired || dragActive || panMode != PanMode.NONE || zoomActive
        if (!continuous) {
            val centroid = lastCentroidGuess()
            val travel = Math.hypot((centroid[0] - startCx).toDouble(), (centroid[1] - startCy).toDouble()).toFloat()
            val count = maxFingerCount
            val swipeOk = (count == 3 && config.swipe3Enabled && travel >= config.swipe3Threshold) ||
                (count == 4 && config.swipe4Enabled && travel >= config.swipe4Threshold)
            if (swipeOk) {
                fireSwipe(count, centroid[0] - startCx, centroid[1] - startCy)
            } else if (!movedBeyondTap) {
                fireTap(count, centroid[0], centroid[1])
            }
        }
        releaseAll()
    }

    private fun fireHold() {
        if (movedBeyondTap || holdFired) return
        val binding = holdBindingFor(fingerCount)
        if (binding == Binding.NONE) return
        holdFired = true
        heldBinding = binding
        heldBehavior = holdBehaviorFor(fingerCount)
        val centroid = centroid()
        moveCursorTo(centroid[0], centroid[1])
        if (heldBehavior == HoldBehavior.CLICK) clickBinding(binding) else pressBinding(binding)
    }

    private fun fireTap(count: Int, x: Float, y: Float) {
        if (count == 1) {
            val now = SystemClock.uptimeMillis()
            val near = Math.hypot((x - lastTapX).toDouble(), (y - lastTapY).toDouble()) < DOUBLE_TAP_DIST
            if (config.doubleTapEnabled && now - lastTapMs < config.doubleTapDelay && near) {
                moveCursorTo(x, y)
                clickButton(Pointer.Button.BUTTON_LEFT)
                lastTapMs = 0L
                return
            }
            lastTapMs = now; lastTapX = x; lastTapY = y
            if (config.tap1Enabled) { moveCursorTo(x, y); clickBinding(config.tap1) }
            return
        }
        val (enabled, binding) = when (count) {
            2 -> config.tap2Enabled to config.tap2
            3 -> config.tap3Enabled to config.tap3
            else -> config.tap4Enabled to config.tap4
        }
        if (enabled) { moveCursorTo(x, y); clickBinding(binding) }
    }

    private fun fireSwipe(count: Int, dx: Float, dy: Float) {
        val horizontal = Math.abs(dx) > Math.abs(dy)
        val binding = if (count == 3) {
            if (horizontal) (if (dx > 0) config.swipe3Right else config.swipe3Left)
            else (if (dy > 0) config.swipe3Down else config.swipe3Up)
        } else {
            if (horizontal) (if (dx > 0) config.swipe4Right else config.swipe4Left)
            else (if (dy > 0) config.swipe4Down else config.swipe4Up)
        }
        clickBinding(binding)
    }

    private fun performPan(action: PanAction, dx: Float, dy: Float) {
        when (action) {
            PanAction.MIDDLE_DRAG -> {
                if (!pressedButtons.contains(Pointer.Button.BUTTON_MIDDLE)) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE)
                    pressedButtons.add(Pointer.Button.BUTTON_MIDDLE)
                }
                xServer.injectPointerMoveDelta(dx.toInt(), dy.toInt())
            }
            PanAction.WASD -> updatePanKeys(dx, dy, XKeycode.KEY_A, XKeycode.KEY_D, XKeycode.KEY_W, XKeycode.KEY_S)
            PanAction.ARROWS -> updatePanKeys(dx, dy, XKeycode.KEY_LEFT, XKeycode.KEY_RIGHT, XKeycode.KEY_UP, XKeycode.KEY_DOWN)
            PanAction.NONE -> {}
        }
    }

    private fun updatePanKeys(dx: Float, dy: Float, left: XKeycode, right: XKeycode, up: XKeycode, down: XKeycode) {
        val want = HashSet<XKeycode>()
        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > PAN_DEADZONE) want.add(right) else if (dx < -PAN_DEADZONE) want.add(left)
        } else {
            if (dy > PAN_DEADZONE) want.add(down) else if (dy < -PAN_DEADZONE) want.add(up)
        }
        val iterator = panKeys.iterator()
        while (iterator.hasNext()) {
            val k = iterator.next()
            if (!want.contains(k)) { xServer.injectKeyRelease(k); iterator.remove() }
        }
        for (k in want) if (panKeys.add(k)) xServer.injectKeyPress(k)
    }

    private fun performZoom(zoomIn: Boolean) {
        when (config.zoomAction) {
            ZoomAction.SCROLL -> clickButton(if (zoomIn) Pointer.Button.BUTTON_SCROLL_UP else Pointer.Button.BUTTON_SCROLL_DOWN)
            ZoomAction.ZOOM_KEYS -> { val k = if (zoomIn) XKeycode.KEY_EQUAL else XKeycode.KEY_MINUS; xServer.injectKeyPress(k); xServer.injectKeyRelease(k) }
            ZoomAction.NONE -> {}
        }
    }

    fun releaseAll() {
        handler.removeCallbacks(holdRunnable)
        for (k in pressedKeys.toList()) xServer.injectKeyRelease(k)
        for (b in pressedButtons.toList()) xServer.injectPointerButtonRelease(b)
        for (k in panKeys.toList()) xServer.injectKeyRelease(k)
        for (b in pressedGamepad.toList()) injectGamepad(b, false)
        pressedKeys.clear(); pressedButtons.clear(); panKeys.clear(); pressedGamepad.clear()
        resetSession()
    }

    private fun resetSession() {
        owned.clear(); positions.clear()
        fingerCount = 0; maxFingerCount = 0
        holdFired = false; movedBeyondTap = false; dragActive = false
        panMode = PanMode.NONE; accumPan = 0f; zoomAccum = 0f; zoomActive = false
    }

    private fun centroid(): FloatArray {
        if (positions.isEmpty()) return floatArrayOf(lastCx, lastCy)
        var sx = 0f; var sy = 0f
        for (p in positions.values) { sx += p[0]; sy += p[1] }
        return floatArrayOf(sx / positions.size, sy / positions.size)
    }

    private fun lastCentroidGuess(): FloatArray = floatArrayOf(lastCx, lastCy)

    private fun distance(): Float {
        if (positions.size < 2) return lastDistance
        val it = positions.values.iterator()
        val a = it.next(); val b = it.next()
        return Math.hypot((a[0] - b[0]).toDouble(), (a[1] - b[1]).toDouble()).toFloat()
    }

    private fun moveCursorTo(x: Float, y: Float) {
        val p = XForm.transformPoint(xform, x, y)
        xServer.injectPointerMove(p[0].toInt(), p[1].toInt())
    }

    private fun pressBinding(b: Binding) {
        if (b == Binding.NONE) return
        if (b.isGamepad()) { injectGamepad(b, true); pressedGamepad.add(b); return }
        val btn = b.getPointerButton()
        if (btn != null) { xServer.injectPointerButtonPress(btn); pressedButtons.add(btn) }
        else if (b.isKeyboard()) { xServer.injectKeyPress(b.keycode); pressedKeys.add(b.keycode) }
    }

    private fun releaseBinding(b: Binding) {
        if (b == Binding.NONE) return
        if (b.isGamepad()) { if (pressedGamepad.remove(b)) injectGamepad(b, false); return }
        val btn = b.getPointerButton()
        if (btn != null) releaseButton(btn)
        else if (b.isKeyboard()) { if (pressedKeys.remove(b.keycode)) xServer.injectKeyRelease(b.keycode) }
    }

    private fun clickBinding(b: Binding) {
        if (b == Binding.NONE) return
        if (b.isGamepad()) { injectGamepad(b, true); injectGamepad(b, false); return }
        val btn = b.getPointerButton()
        if (btn != null) clickButton(btn)
        else if (b.isKeyboard()) { xServer.injectKeyPress(b.keycode); xServer.injectKeyRelease(b.keycode) }
    }

    private fun injectGamepad(b: Binding, pressed: Boolean) {
        xServer.winHandler?.injectGestureGamepad(b, pressed)
    }

    private fun clickButton(btn: Pointer.Button) {
        xServer.injectPointerButtonPress(btn)
        xServer.injectPointerButtonRelease(btn)
    }

    private fun releaseButton(btn: Pointer.Button) {
        if (pressedButtons.remove(btn)) xServer.injectPointerButtonRelease(btn)
    }

    private fun holdBindingFor(count: Int): Binding = when (count) {
        1 -> if (config.longPressEnabled) config.longPress else Binding.NONE
        2 -> if (config.hold2Enabled) config.hold2 else Binding.NONE
        3 -> if (config.hold3Enabled) config.hold3 else Binding.NONE
        4 -> if (config.hold4Enabled) config.hold4 else Binding.NONE
        else -> Binding.NONE
    }

    private fun holdBehaviorFor(count: Int): HoldBehavior = when (count) {
        1 -> config.longPressBehavior
        2 -> config.hold2Behavior
        3 -> config.hold3Behavior
        else -> config.hold4Behavior
    }

    private fun holdDelayFor(count: Int): Int = when (count) {
        1 -> config.longPressDelay
        2 -> config.hold2Delay
        3 -> config.hold3Delay
        else -> config.hold4Delay
    }

    companion object {
        private const val TAP_TRAVEL_MAX = 30f
        private const val DOUBLE_TAP_DIST = 100.0
        private const val ZOOM_STEP = 40f
        private const val PAN_DEADZONE = 3f
    }
}
