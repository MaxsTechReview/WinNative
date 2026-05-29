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
import com.winlator.cmod.runtime.display.XServerDisplayActivity
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
        const val CURSOR_ACCELERATION_THRESHOLD: Byte = 8
        private const val MIN_SENSITIVITY = 0.1f
        private const val CLICK_DELAYED_TIME: Byte = 50
        private const val EFFECTIVE_TOUCH_DISTANCE: Byte = 20
        private const val UPDATE_FORM_DELAYED_TIME = 50
        private const val LONG_PRESS_RIGHT_CLICK_MS = 1000L
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
    private var lastTouchedPosX = 0f
    private var lastTouchedPosY = 0f
    private var resolutionScale = 0f
    private var mouseEnabled = true
    var tapToClickEnabled = true
    private var fourFingersTapCallback: Runnable? = null
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressActive = false
    private val pointerIdsToIgnore = mutableSetOf<Int>()

    fun setPointerIdsToIgnore(ids: Set<Int>) {
        pointerIdsToIgnore.clear()
        pointerIdsToIgnore.addAll(ids)
    }

    private val longPressRunnable =
        Runnable {
            if (tapToClickEnabled && numFingers.toInt() == 1 && fingers[0] != null && fingers[0]!!.travelDistance() < MAX_TAP_TRAVEL_DISTANCE) {
                longPressActive = true

                if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
                }

                xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
                xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
            }
        }

    init {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        background = createTransparentBg()
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
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
        
        if (xServer.renderer?.isFullscreen == false) {
            XForm.makeTranslation(xform, -viewTransformation.viewOffsetX.toFloat(), -viewTransformation.viewOffsetY.toFloat())
            XForm.scale(xform, invAspect, invAspect)
        } else {
            XForm.makeScale(xform, innerWidth.toFloat() / outerWidth.toFloat(), innerHeight.toFloat() / outerHeight.toFloat())
        }
    }

    inner class Finger(
        rawX: Float,
        rawY: Float,
    ) {
        var x: Float = 0f
        var y: Float = 0f
        var startX: Float = 0f
        var startY: Float = 0f
        var lastX: Float = 0f
        var lastY: Float = 0f
        val touchTime: Long = System.currentTimeMillis()
        var isFlick = false

        init {
            val transformedPoint = XForm.transformPoint(xform, rawX, rawY)
            this.x = transformedPoint[0]
            this.lastX = this.x
            this.startX = this.x
            this.y = transformedPoint[1]
            this.lastY = this.y
            this.startY = this.y
        }

        fun update(
            newRawX: Float,
            newRawY: Float,
        ) {
            this.lastX = this.x
            this.lastY = this.y
            val transformedPoint = XForm.transformPoint(xform, newRawX, newRawY)
            this.x = transformedPoint[0]
            this.y = transformedPoint[1]
        }

        fun updateLastPos() {
            this.lastX = this.x
            this.lastY = this.y
        }

        fun isTap(): Boolean = (System.currentTimeMillis() - touchTime) < MAX_TAP_MILLISECONDS && travelDistance() < MAX_TAP_TRAVEL_DISTANCE * 4

        fun travelDistance(): Float = Math.hypot((x - startX).toDouble(), (y - startY).toDouble()).toFloat()

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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mouseEnabled) return true
        if ((context as? XServerDisplayActivity)?.isInputSuspended == true) return true
        resetMousePointerTimeout()

        return when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS -> handleStylusEvent(event)
            else -> if (simTouchScreen) handleTouchscreenEvent(event) else handleTouchpadEvent(event)
        }
    }

    private fun resetMousePointerTimeout() {
        if (!mouseEnabled) return
        xServer.renderer?.setCursorVisible(true)
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable)
            timeoutHandler.postDelayed(hideControlsRunnable, 5000)
        }
    }

    fun cancelMousePointerTimeout() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable)
        }
    }

    private fun isExternalPointerEvent(event: MotionEvent): Boolean {
        val source = event.source
        val isPointerClass =
            (source and InputDevice.SOURCE_CLASS_POINTER) == InputDevice.SOURCE_CLASS_POINTER
        return isPointerClass && !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
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
        if (event.actionMasked != MotionEvent.ACTION_MOVE && (pointerId >= MAX_FINGERS || pointerIdsToIgnore.contains(pointerId))) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return true
                scrollAccumY = 0f
                scrolling = false
                fingers[pointerId] = Finger(event.getX(actionIndex), event.getY(actionIndex))
                numFingers++

                if (numFingers.toInt() > 1) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }

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
                                xServer.injectPointerMove(lastTouchedPosX.toInt(), lastTouchedPosY.toInt())
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
                    val delta = getExternalMouseDelta(event)
                    val dx = Mathf.roundPoint(delta[0])
                    val dy = Mathf.roundPoint(delta[1])

                    if (dx != 0 || dy != 0) {
                        if (xServer.isRelativeMouseMovement) xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0)
                        xServer.injectPointerMoveDelta(dx, dy)
                        if (!xServer.isRelativeMouseMovement) xServer.renderer?.requestCursorRender()
                    }
                } else {
                    for (i in 0 until MAX_FINGERS.toInt()) {
                        val finger = fingers[i] ?: continue
                        if (pointerIdsToIgnore.contains(i)) {
                            fingers[i] = null
                            numFingers--
                            continue
                        }
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
                    for (i in 0 until MAX_FINGERS.toInt()) fingers[i]?.updateLastPos()
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

    private fun handleFingerMove(finger1: Finger) {
        var skipPointerMove = false
        val finger2 = if (numFingers.toInt() == 2) findSecondFinger(finger1) else null
        
        if (finger2 != null) {
            val resolutionScale = 1000.0f / Math.min(xServer.screenInfo.width.toInt(), xServer.screenInfo.height.toInt())
            val currDistance = Math.hypot((finger1.x - finger2.x).toDouble(), (finger1.y - finger2.y).toDouble()).toFloat() * resolutionScale
            
            if (currDistance < 350.0f) {
                scrollAccumY += ((finger1.y + finger2.y) * 0.5f) - ((finger1.lastY + finger2.lastY) * 0.5f)
                if (scrollAccumY < -100.0f) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN)
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN)
                    scrollAccumY = 0.0f
                } else if (scrollAccumY > 100.0f) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP)
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP)
                    scrollAccumY = 0.0f
                }
                scrolling = true
            } else if (currDistance >= 350.0f && !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT) && finger2.travelDistance() < 10.0f) {
                pressPointerButtonLeft(finger1)
                skipPointerMove = true
            }
        }
        
        if (!scrolling && numFingers <= 2 && !skipPointerMove) {
            val dx = finger1.deltaX()
            val dy = finger1.deltaY()
            
            if (dx != 0 || dy != 0) {
                if (simTouchScreen) {
                    if (System.currentTimeMillis() - finger1.touchTime > CLICK_DELAYED_TIME) {
                        xServer.injectPointerMove(finger1.x.toInt(), finger1.y.toInt())
                    }
                } else {
                    if (xServer.isRelativeMouseMovement) xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0)
                    xServer.injectPointerMoveDelta(dx, dy)
                    if (!xServer.isRelativeMouseMovement) xServer.renderer?.requestCursorRender()
                }
            }
        }
    }

    private fun handleTouchscreenEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        if (event.actionMasked != MotionEvent.ACTION_MOVE && (pointerId >= MAX_FINGERS || pointerIdsToIgnore.contains(pointerId))) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                fingers[pointerId] = Finger(event.getX(actionIndex), event.getY(actionIndex))
                numFingers++

                if (numFingers.toInt() > 1) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }

                if (pointerId == 0 && numFingers.toInt() == 1) {
                    longPressActive = false
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_RIGHT_CLICK_MS)
                } else {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }

                handleTouchDown(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val historySize = event.historySize
                for (h in 0..historySize) {
                    for (i in 0 until MAX_FINGERS.toInt()) {
                        val finger = fingers[i] ?: continue
                        if (pointerIdsToIgnore.contains(i)) {
                            fingers[i] = null
                            numFingers--
                            continue
                        }
                        val pointerIndex = event.findPointerIndex(i)
                        if (pointerIndex >= 0) {
                            val x = if (h < historySize) event.getHistoricalX(pointerIndex, h) else event.getX(pointerIndex)
                            val y = if (h < historySize) event.getHistoricalY(pointerIndex, h) else event.getY(pointerIndex)
                            finger.update(x, y)
                        }
                    }

                    if (numFingers.toInt() == 2) {
                        handleTwoFingerScroll(event)
                    } else {
                        val fingerIdx = fingers.indexOfFirst { it != null }
                        if (fingerIdx >= 0) {
                            handleTouchMove(fingers[fingerIdx]!!)
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                val finger = fingers[pointerId]
                if (numFingers.toInt() == 2 && finger?.isTap() == true) {
                    handleTwoFingerTap()
                } else if (!longPressActive) {
                    handleTouchUp()
                }
                longPressActive = false
                fingers[pointerId] = null
                numFingers--
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressActive = false
                for (i in 0 until MAX_FINGERS.toInt()) fingers[i] = null
                numFingers = 0
                handleAllUp()
            }
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent) {
        val finger = fingers[event.getPointerId(event.actionIndex)] ?: return
        xServer.injectPointerMove(finger.x.toInt(), finger.y.toInt())

        if (tapToClickEnabled && numFingers.toInt() == 1) {
            xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
        }
    }

    private fun handleTouchMove(finger: Finger) {
        if (finger.travelDistance() >= MAX_TAP_TRAVEL_DISTANCE) {
            longPressHandler.removeCallbacks(longPressRunnable)
        } else if (numFingers.toInt() == 1 && !longPressActive) {
            longPressHandler.removeCallbacks(longPressRunnable)
            longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_RIGHT_CLICK_MS)
        }

        xServer.injectPointerMove(finger.x.toInt(), finger.y.toInt())
    }

    private fun handleTouchUp() {
        if (tapToClickEnabled) {
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
        if (!tapToClickEnabled) return
        if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        }

        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
    }

    private fun handleAllUp() {
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
    }

    private fun handleFingerUp(finger1: Finger) {
        if (tapToClickEnabled) {
            when (numFingers.toInt()) {
                1 -> {
                    if (simTouchScreen) {
                        postDelayed(
                            { if (continueClick) xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT) },
                            CLICK_DELAYED_TIME.toLong(),
                        )
                    } else if (finger1.isTap()) {
                        if (finger1.isFlick) {
                            pressPointerButtonRight(finger1)
                            finger1.isFlick = false
                        } else pressPointerButtonLeft(finger1)
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
        }
        releasePointerButtonLeft(finger1)
        releasePointerButtonRight(finger1)
    }

    private fun updateFingerLogic(finger1: Finger) {
        if (finger1.travelDistance() >= MAX_TAP_TRAVEL_DISTANCE * 4) {
            longPressHandler.removeCallbacks(longPressRunnable)
        }
        val finger2 = if (numFingers.toInt() == 2) findSecondFinger(finger1) else null

        if (finger2 != null) {
            val currDistance =
                Math.hypot((finger1.x - finger2.x).toDouble(), (finger1.y - finger2.y).toDouble()).toFloat() * resolutionScale
            if (currDistance < MAX_TWO_FINGERS_SCROLL_DISTANCE) {
                scrollAccumY += ((finger1.y + finger2.y) * 0.5f) - (finger1.lastY + finger2.lastY) * 0.5f
                if (Math.abs(scrollAccumY) > 10) {
                    val button = if (scrollAccumY < 0) Pointer.Button.BUTTON_SCROLL_DOWN else Pointer.Button.BUTTON_SCROLL_UP
                    xServer.injectPointerButtonPress(button)
                    xServer.injectPointerButtonRelease(button)
                    scrollAccumY = 0f
                }
                scrolling = true
            } else if (currDistance >= MAX_TWO_FINGERS_SCROLL_DISTANCE && !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT) &&
                finger2.travelDistance() < MAX_TAP_TRAVEL_DISTANCE * 4
            ) {
                pressPointerButtonLeft(finger1)
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
        this.sensitivity = sensitivity.coerceAtLeast(MIN_SENSITIVITY)
    }

    fun setPointerButtonLeftEnabled(enabled: Boolean) {
        this.pointerButtonLeftEnabled = enabled
    }

    fun setPointerButtonRightEnabled(enabled: Boolean) {
        this.pointerButtonRightEnabled = enabled
    }

    fun resetInputState() {
        longPressHandler.removeCallbacks(longPressRunnable)
        longPressActive = false
        continueClick = false
        scrolling = false
        scrollAccumY = 0f
        for (i in 0 until MAX_FINGERS.toInt()) {
            fingers[i] = null
        }
        numFingers = 0
        fingerPointerButtonLeft = null
        fingerPointerButtonRight = null

        if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        }
        if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_RIGHT)) {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
        }
        if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_MIDDLE)) {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE)
        }
    }

    fun setFourFingersTapCallback(callback: Runnable?) {
        this.fourFingersTapCallback = callback
    }

    fun onExternalMouseEvent(event: MotionEvent): Boolean {
        if (!isExternalPointerEvent(event)) return false
        if (!mouseEnabled) return true
        resetMousePointerTimeout()
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
                button?.let { xServer.injectPointerButtonPress(it) }
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
                button?.let { xServer.injectPointerButtonRelease(it) }
                true
            }

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                val delta = getExternalMouseDelta(event)
                val dx = Mathf.roundPoint(delta[0])
                val dy = Mathf.roundPoint(delta[1])
                if (dx != 0 || dy != 0) {
                    if (xServer.isRelativeMouseMovement) xServer.winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0)
                    xServer.injectPointerMoveDelta(dx, dy)
                    if (!xServer.isRelativeMouseMovement) xServer.renderer?.requestCursorRender()
                }
                true
            }

            MotionEvent.ACTION_SCROLL -> {
                val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (Math.abs(scrollY) >= 1.0f) {
                    val button = if (scrollY <= -1.0f) Pointer.Button.BUTTON_SCROLL_DOWN else Pointer.Button.BUTTON_SCROLL_UP
                    xServer.injectPointerButtonPress(button)
                    xServer.injectPointerButtonRelease(button)
                }
                true
            }

            else -> {
                false
            }
        }
    }

    private fun getExternalMouseDelta(event: MotionEvent, historyPos: Int = -1): FloatArray {
        var dx = if (historyPos < 0) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) else event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, historyPos)
        var dy = if (historyPos < 0) event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) else event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, historyPos)
        if (dx == 0f && dy == 0f) {
            val x = if (historyPos < 0) event.x else event.getHistoricalX(historyPos)
            val y = if (historyPos < 0) event.y else event.getHistoricalY(historyPos)
            val transformedPoint = XForm.transformPoint(xform, x, y)
            dx = transformedPoint[0] - xServer.pointer.x
            dy = transformedPoint[1] - xServer.pointer.y
        } else {
            if (!xServer.isRelativeMouseMovement && !simTouchScreen) {
                if (Math.abs(dx) > CURSOR_ACCELERATION_THRESHOLD) dx *= CURSOR_ACCELERATION
                if (Math.abs(dy) > CURSOR_ACCELERATION_THRESHOLD) dy *= CURSOR_ACCELERATION
            }

            val rawDx = dx
            dx = xform[0] * rawDx + xform[2] * dy
            dy = xform[1] * rawDx + xform[3] * dy
        }
        dx *= sensitivity
        dy *= sensitivity
        return floatArrayOf(dx, dy)
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
        if (!enabled) {
            resetInputState()
            cancelMousePointerTimeout()
            xServer.renderer?.setCursorVisible(false)
        } else {
            resetMousePointerTimeout()
        }
    }
}
