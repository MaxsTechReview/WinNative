package com.winlator.cmod.runtime.input.ui
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.runtime.display.renderer.ViewTransformation
import com.winlator.cmod.runtime.display.winhandler.MouseEventFlags
import com.winlator.cmod.runtime.display.winhandler.WinHandler
import com.winlator.cmod.runtime.display.xserver.Pointer
import com.winlator.cmod.runtime.display.xserver.XServer
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.math.Mathf
import com.winlator.cmod.shared.math.XForm

class TouchpadView(
    context: Context,
    private val xServer: XServer,
    private val timeoutHandler: Handler?,
    private val hideControlsRunnable: Runnable?,
) : View(context) {
    companion object {
        private const val MAX_FINGERS: Byte = 4
        private const val MAX_TWO_FINGERS_SCROLL_DISTANCE: Short = 350
        const val MAX_TAP_TRAVEL_DISTANCE: Byte = 10
        const val MAX_TAP_MILLISECONDS: Short = 200
        const val CURSOR_ACCELERATION = 1.25f
        const val CURSOR_ACCELERATION_THRESHOLD: Byte = 6
        private const val CLICK_DELAYED_TIME: Byte = 50
        private const val EFFECTIVE_TOUCH_DISTANCE: Byte = 20
        private const val UPDATE_FORM_DELAYED_TIME = 50
        private const val LONG_PRESS_RIGHT_CLICK_MS = 500L
    }

    private val fingers = arrayOfNulls<Finger>(MAX_FINGERS.toInt())
    private var numFingers: Byte = 0
    private var sensitivity = 1.0f
    private var pointerButtonLeftEnabled = true
    private var pointerButtonRightEnabled = true
    private var fingerPointerButtonLeft: Finger? = null
    private var fingerPointerButtonRight: Finger? = null
    private var scrollAccumY = 0f
    private var scrolling = false
    private val xform = XForm.getInstance()
    private var simTouchScreen = false
    private var continueClick = true
    private var lastTouchedPosX = 0
    private var lastTouchedPosY = 0
    private var resolutionScale = 0f
    private var mouseEnabled = true
    private var fourFingersTapCallback: Runnable? = null
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressActive = false
    private val longPressRunnable =
        Runnable {
            if (numFingers.toInt() == 1 && fingers[0] != null && fingers[0]!!.travelDistance() < MAX_TAP_TRAVEL_DISTANCE) {
                longPressActive = true
                if (xServer.isRelativeMouseMovement) {
                    xServer.winHandler.mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0)
                    xServer.winHandler.mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0)
                } else {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
                }
            }
        }

    init {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        background = createTransparentBg()
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = false
        pointerIcon = PointerIcon.load(resources, R.drawable.hidden_pointer_arrow)
        updateXform(
            AppUtils.getScreenWidth(),
            AppUtils.getScreenHeight(),
            xServer.screenInfo.width.toInt(),
            xServer.screenInfo.height.toInt(),
        )

        setOnGenericMotionListener { _, event ->
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                handleStylusHoverEvent(event)
            } else {
                false
            }
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateXform(w, h, xServer.screenInfo.width.toInt(), xServer.screenInfo.height.toInt())
        resolutionScale = 1000.0f / Math.min(xServer.screenInfo.width.toInt(), xServer.screenInfo.height.toInt())
    }

    private fun updateXform(
        outerWidth: Int,
        outerHeight: Int,
        innerWidth: Int,
        innerHeight: Int,
    ) {
        val viewTransformation = ViewTransformation()
        viewTransformation.update(outerWidth, outerHeight, innerWidth, innerHeight)
        val invAspect = 1.0f / viewTransformation.aspect
        if (!xServer.renderer.isFullscreen) {
            XForm.makeTranslation(xform, -viewTransformation.viewOffsetX.toFloat(), -viewTransformation.viewOffsetY.toFloat())
            XForm.scale(xform, invAspect, invAspect)
        } else {
            XForm.makeScale(xform, innerWidth.toFloat() / outerWidth, innerHeight.toFloat() / outerHeight)
        }
    }

    inner class Finger(
        x: Float,
        y: Float,
    ) {
        var x: Int
        var y: Int
        val startX: Int
        val startY: Int
        var lastX: Int
        var lastY: Int
        val touchTime: Long = System.currentTimeMillis()

        init {
            val transformedPoint = XForm.transformPoint(xform, x, y)
            this.x = transformedPoint[0].toInt().also { this.lastX = it }.also { this.startX = it }
            this.y = transformedPoint[1].toInt().also { this.lastY = it }.also { this.startY = it }
        }

        fun update(
            x: Float,
            y: Float,
        ) {
            lastX = this.x
            lastY = this.y
            val transformedPoint = XForm.transformPoint(xform, x, y)
            this.x = transformedPoint[0].toInt()
            this.y = transformedPoint[1].toInt()
        }

        fun deltaX(): Int {
            var dx = (x - lastX) * sensitivity
            if (Math.abs(dx) > CURSOR_ACCELERATION_THRESHOLD) dx *= CURSOR_ACCELERATION
            return Mathf.roundPoint(dx)
        }

        fun deltaY(): Int {
            var dy = (y - lastY) * sensitivity
            if (Math.abs(dy) > CURSOR_ACCELERATION_THRESHOLD) dy *= CURSOR_ACCELERATION
            return Mathf.roundPoint(dy)
        }

        fun isTap(): Boolean = (System.currentTimeMillis() - touchTime) < MAX_TAP_MILLISECONDS && travelDistance() < MAX_TAP_TRAVEL_DISTANCE

        fun travelDistance(): Float = Math.hypot((x - startX).toDouble(), (y - startY).toDouble()).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mouseEnabled) return true
        val isTouchscreenMode = preferences.getBoolean("touchscreen_toggle", false)
        resetTouchscreenTimeout()

        return when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS -> handleStylusEvent(event)
            else -> if (isTouchscreenMode) handleTouchscreenEvent(event) else handleTouchpadEvent(event)
        }
    }

    private fun resetTouchscreenTimeout() {
        if (!preferences.getBoolean("touchscreen_timeout_enabled", false)) return
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable)
            timeoutHandler.postDelayed(hideControlsRunnable, 5000)
        }
    }

    private fun handleStylusHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
                xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
            }
        }
        return true
    }

    private fun handleStylusEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val buttonState = event.buttonState
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if ((buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                    handleStylusRightClick(event)
                } else {
                    handleStylusLeftClick(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                handleStylusMove(event)
            }

            MotionEvent.ACTION_UP -> {
                handleStylusUp()
            }
        }
        return true
    }

    private fun handleStylusLeftClick(event: MotionEvent) {
        val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
        xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
    }

    private fun handleStylusRightClick(event: MotionEvent) {
        val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
        xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
    }

    private fun handleStylusMove(event: MotionEvent) {
        val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
        xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
    }

    private fun handleStylusUp() {
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
    }

    private fun handleTouchpadEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        if (pointerId >= MAX_FINGERS) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return true
                scrollAccumY = 0f
                scrolling = false
                fingers[pointerId] = Finger(event.getX(actionIndex), event.getY(actionIndex))
                numFingers++

                if (pointerId == 0 && numFingers.toInt() == 1 && !simTouchScreen) {
                    longPressActive = false
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_RIGHT_CLICK_MS)
                } else {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }

                if (simTouchScreen) {
                    val clickDelay =
                        Runnable {
                            if (continueClick) {
                                xServer.injectPointerMove(lastTouchedPosX, lastTouchedPosY)
                                xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                            }
                        }
                    if (pointerId == 0) {
                        continueClick = true
                        val finger = fingers[0]!!
                        if (Math.hypot((finger.x - lastTouchedPosX).toDouble(), (finger.y - lastTouchedPosY).toDouble()) * resolutionScale >
                            EFFECTIVE_TOUCH_DISTANCE
                        ) {
                            lastTouchedPosX = finger.x
                            lastTouchedPosY = finger.y
                        }
                        postDelayed(clickDelay, CLICK_DELAYED_TIME.toLong())
                    } else if (pointerId == 1 && numFingers < 2) {
                        continueClick = true
                        val finger = fingers[1]!!
                        if (Math.hypot((finger.x - lastTouchedPosX).toDouble(), (finger.y - lastTouchedPosY).toDouble()) * resolutionScale >
                            EFFECTIVE_TOUCH_DISTANCE
                        ) {
                            lastTouchedPosX = finger.x
                            lastTouchedPosY = finger.y
                        }
                        postDelayed(clickDelay, CLICK_DELAYED_TIME.toLong())
                    } else if (pointerId == 1) {
                        continueClick = (System.currentTimeMillis() - fingers[0]!!.touchTime) > CLICK_DELAYED_TIME
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
                    if (xServer.isRelativeMouseMovement) {
                        xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, transformedPoint[0].toInt(), transformedPoint[1].toInt(), 0)
                    } else {
                        xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
                    }
                } else {
                    for (i in 0 until MAX_FINGERS.toInt()) {
                        val finger = fingers[i] ?: continue
                        val pointerIndex = event.findPointerIndex(i)
                        if (pointerIndex >= 0) {
                            finger.update(event.getX(pointerIndex), event.getY(pointerIndex))
                            handleFingerMove(finger)
                        } else {
                            handleFingerUp(finger)
                            fingers[i] = null
                            numFingers--
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                fingers[pointerId]?.let {
                    it.update(event.getX(actionIndex), event.getY(actionIndex))
                    if (!longPressActive) handleFingerUp(it)
                    longPressActive = false
                    fingers[pointerId] = null
                    numFingers--
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressActive = false
                for (i in 0 until MAX_FINGERS.toInt()) fingers[i] = null
                numFingers = 0
            }
        }
        return true
    }

    private fun handleTouchscreenEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        if (pointerId >= MAX_FINGERS) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                fingers[pointerId] = Finger(event.getX(actionIndex), event.getY(actionIndex))
                numFingers++
                handleTouchDown(event)
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until MAX_FINGERS.toInt()) {
                    val finger = fingers[i] ?: continue
                    val pIdx = event.findPointerIndex(i)
                    if (pIdx >= 0) finger.update(event.getX(pIdx), event.getY(pIdx))
                }
                if (numFingers.toInt() == 2) {
                    handleTwoFingerScroll(event)
                } else {
                    handleTouchMove(event)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val finger = fingers[pointerId]
                if (numFingers.toInt() == 2 && finger?.isTap() == true) {
                    handleTwoFingerTap()
                } else {
                    handleTouchUp()
                }
                fingers[pointerId] = null
                numFingers--
            }

            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until MAX_FINGERS.toInt()) fingers[i] = null
                numFingers = 0
                handleAllUp()
            }
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent) {
        val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
        if (xServer.isRelativeMouseMovement) {
            xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, transformedPoint[0].toInt(), transformedPoint[1].toInt(), 0)
        } else {
            xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
        }

        if (numFingers.toInt() == 1) {
            if (xServer.isRelativeMouseMovement) {
                xServer.winHandler.mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0)
            } else {
                xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
            }
        }
    }

    private fun handleTouchMove(event: MotionEvent) {
        val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
        if (xServer.isRelativeMouseMovement) {
            xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, transformedPoint[0].toInt(), transformedPoint[1].toInt(), 0)
        } else {
            xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
        }
    }

    private fun handleTouchUp() {
        if (xServer.isRelativeMouseMovement) {
            xServer.winHandler.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0)
        } else {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        }
    }

    private fun handleTwoFingerScroll(event: MotionEvent) {
        val activeFingers = fingers.filterNotNull()
        if (activeFingers.size < 2) return
        val finger1 = activeFingers[0]
        val finger2 = activeFingers[1]
        val scrollDistance = finger1.y - finger2.y
        if (Math.abs(scrollDistance) > 10) {
            val button = if (scrollDistance > 0) Pointer.Button.BUTTON_SCROLL_UP else Pointer.Button.BUTTON_SCROLL_DOWN
            xServer.injectPointerButtonPress(button)
            xServer.injectPointerButtonRelease(button)
        }
    }

    private fun handleTwoFingerTap() {
        // FIX: Ensure clean right-click by clearing left button first
        if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
            if (xServer.isRelativeMouseMovement) {
                xServer.winHandler.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0)
            } else {
                xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
            }
        }

        if (xServer.isRelativeMouseMovement) {
            xServer.winHandler.mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0)
            xServer.winHandler.mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0)
        } else {
            xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
        }
    }

    private fun handleAllUp() {
        if (xServer.isRelativeMouseMovement) {
            xServer.winHandler.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0)
            xServer.winHandler.mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0)
        } else {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
        }
    }

    private fun handleFingerUp(finger1: Finger) {
        when (numFingers.toInt()) {
            1 -> {
                if (simTouchScreen) {
                    postDelayed(
                        { if (continueClick) xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT) },
                        CLICK_DELAYED_TIME.toLong(),
                    )
                } else if (finger1.isTap()) {
                    pressPointerButtonLeft(finger1)
                }
            }

            2 -> {
                val finger2 = findSecondFinger(finger1)
                if (finger2 != null && finger1.isTap()) pressPointerButtonRight(finger1)
            }

            4 -> {
                fourFingersTapCallback?.let {
                    if (fingers.filterNotNull().all { it.isTap() }) it.run()
                }
            }
        }
        releasePointerButtonLeft(finger1)
        releasePointerButtonRight(finger1)
    }

    private fun handleFingerMove(finger1: Finger) {
        if (finger1.travelDistance() >= MAX_TAP_TRAVEL_DISTANCE) {
            longPressHandler.removeCallbacks(longPressRunnable)
        }
        var skipPointerMove = false
        val finger2 = if (numFingers.toInt() == 2) findSecondFinger(finger1) else null

        if (finger2 != null) {
            val currDistance =
                Math.hypot((finger1.x - finger2.x).toDouble(), (finger1.y - finger2.y).toDouble()).toFloat() * resolutionScale
            if (currDistance < MAX_TWO_FINGERS_SCROLL_DISTANCE) {
                scrollAccumY += ((finger1.y + finger2.y) * 0.5f) - (finger1.lastY + finger2.lastY) * 0.5f
                if (Math.abs(scrollAccumY) > 100) {
                    val button = if (scrollAccumY < 0) Pointer.Button.BUTTON_SCROLL_DOWN else Pointer.Button.BUTTON_SCROLL_UP
                    xServer.injectPointerButtonPress(button)
                    xServer.injectPointerButtonRelease(button)
                    scrollAccumY = 0f
                }
                scrolling = true
            } else if (currDistance >= MAX_TWO_FINGERS_SCROLL_DISTANCE && !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT) &&
                finger2.travelDistance() < MAX_TAP_TRAVEL_DISTANCE
            ) {
                pressPointerButtonLeft(finger1)
                skipPointerMove = true
            }
        }

        if (!scrolling && numFingers <= 2 && !skipPointerMove) {
            val dx = finger1.deltaX()
            val dy = finger1.deltaY()
            if (simTouchScreen) {
                if (System.currentTimeMillis() - finger1.touchTime > CLICK_DELAYED_TIME) xServer.injectPointerMove(finger1.x, finger1.y)
            } else if (xServer.isRelativeMouseMovement) {
                xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0)
            } else {
                xServer.injectPointerMoveDelta(dx, dy)
            }
        }
    }

    private fun findSecondFinger(finger: Finger): Finger? = fingers.firstOrNull { it != null && it != finger }

    private fun pressPointerButtonLeft(finger: Finger) {
        if (pointerButtonLeftEnabled && !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
            xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
            fingerPointerButtonLeft = finger
        }
    }

    private fun pressPointerButtonRight(finger: Finger) {
        if (pointerButtonRightEnabled && !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_RIGHT)) {
            xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
            fingerPointerButtonRight = finger
        }
    }

    private fun releasePointerButtonLeft(finger: Finger) {
        if (pointerButtonLeftEnabled && finger == fingerPointerButtonLeft && xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
            postDelayed({
                xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
                fingerPointerButtonLeft = null
            }, 30)
        }
    }

    private fun releasePointerButtonRight(finger: Finger) {
        if (pointerButtonRightEnabled && finger == fingerPointerButtonRight &&
            xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_RIGHT)
        ) {
            postDelayed({
                xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
                fingerPointerButtonRight = null
            }, 30)
        }
    }

    fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity
    }

    fun setPointerButtonLeftEnabled(enabled: Boolean) {
        this.pointerButtonLeftEnabled = enabled
    }

    fun setPointerButtonRightEnabled(enabled: Boolean) {
        this.pointerButtonRightEnabled = enabled
    }

    fun setFourFingersTapCallback(callback: Runnable?) {
        this.fourFingersTapCallback = callback
    }

    fun onExternalMouseEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        val actionButton = event.actionButton
        return when (event.action) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                val button =
                    when (actionButton) {
                        MotionEvent.BUTTON_PRIMARY -> Pointer.Button.BUTTON_LEFT
                        MotionEvent.BUTTON_SECONDARY -> Pointer.Button.BUTTON_RIGHT
                        MotionEvent.BUTTON_TERTIARY -> Pointer.Button.BUTTON_MIDDLE
                        else -> null
                    }
                button?.let {
                    if (xServer.isRelativeMouseMovement) {
                        xServer.winHandler.mouseEvent(MouseEventFlags.getFlagFor(it, true), 0, 0, 0)
                    } else {
                        xServer.injectPointerButtonPress(it)
                    }
                }
                true
            }

            MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button =
                    when (actionButton) {
                        MotionEvent.BUTTON_PRIMARY -> Pointer.Button.BUTTON_LEFT
                        MotionEvent.BUTTON_SECONDARY -> Pointer.Button.BUTTON_RIGHT
                        MotionEvent.BUTTON_TERTIARY -> Pointer.Button.BUTTON_MIDDLE
                        else -> null
                    }
                button?.let {
                    if (xServer.isRelativeMouseMovement) {
                        xServer.winHandler.mouseEvent(MouseEventFlags.getFlagFor(it, false), 0, 0, 0)
                    } else {
                        xServer.injectPointerButtonRelease(it)
                    }
                }
                true
            }

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                val transformedPoint = XForm.transformPoint(xform, event.x, event.y)
                if (xServer.isRelativeMouseMovement) {
                    xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, transformedPoint[0].toInt(), transformedPoint[1].toInt(), 0)
                } else {
                    xServer.injectPointerMove(transformedPoint[0].toInt(), transformedPoint[1].toInt())
                }
                true
            }

            MotionEvent.ACTION_SCROLL -> {
                val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (Math.abs(scrollY) >= 1.0f) {
                    val button = if (scrollY <= -1.0f) Pointer.Button.BUTTON_SCROLL_DOWN else Pointer.Button.BUTTON_SCROLL_UP
                    if (xServer.isRelativeMouseMovement) {
                        xServer.winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, scrollY.toInt())
                    } else {
                        xServer.injectPointerButtonPress(button)
                        xServer.injectPointerButtonRelease(button)
                    }
                }
                true
            }

            else -> {
                false
            }
        }
    }

    fun computeDeltaPoint(
        lastX: Float,
        lastY: Float,
        x: Float,
        y: Float,
    ): FloatArray {
        val result = floatArrayOf(0f, 0f)
        XForm.transformPoint(xform, lastX, lastY, result)
        val lX = result[0]
        val lY = result[1]
        XForm.transformPoint(xform, x, y, result)
        val nX = result[0]
        val nY = result[1]
        result[0] = nX - lX
        result[1] = nY - lY
        return result
    }

    private fun createTransparentBg(): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(Color.TRANSPARENT))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }

    fun setSimTouchScreen(sim: Boolean) {
        this.simTouchScreen = sim
        xServer.setSimulateTouchScreen(sim)
    }

    fun isSimTouchScreen(): Boolean = simTouchScreen

    fun toggleFullscreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            updateXform(width, height, xServer.screenInfo.width.toInt(), xServer.screenInfo.height.toInt())
        }, UPDATE_FORM_DELAYED_TIME.toLong())
    }

    fun setMouseEnabled(enabled: Boolean) {
        this.mouseEnabled = enabled
    }
}
