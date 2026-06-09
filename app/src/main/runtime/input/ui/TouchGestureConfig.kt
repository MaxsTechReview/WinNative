package com.winlator.cmod.runtime.input.ui

import com.winlator.cmod.runtime.input.controls.Binding
import org.json.JSONObject

enum class PanAction { NONE, MIDDLE_DRAG, WASD, ARROWS }

enum class ZoomAction { NONE, SCROLL, ZOOM_KEYS }

enum class HoldBehavior { HOLD, CLICK }

enum class DragAction { NONE, MOVE, LEFT_DRAG }

class TouchGestureConfig {
    var tap1Enabled = true
    var tap1 = Binding.MOUSE_LEFT_BUTTON
    var tap2Enabled = true
    var tap2 = Binding.MOUSE_RIGHT_BUTTON
    var tap3Enabled = false
    var tap3 = Binding.MOUSE_MIDDLE_BUTTON
    var tap4Enabled = false
    var tap4 = Binding.NONE

    var doubleTapEnabled = false
    var doubleTapDelay = 300

    var longPressEnabled = false
    var longPress = Binding.MOUSE_RIGHT_BUTTON
    var longPressBehavior = HoldBehavior.CLICK
    var longPressDelay = 500

    var hold2Enabled = false
    var hold2 = Binding.MOUSE_MIDDLE_BUTTON
    var hold2Behavior = HoldBehavior.HOLD
    var hold3Enabled = false
    var hold3 = Binding.NONE
    var hold3Behavior = HoldBehavior.HOLD
    var hold4Enabled = false
    var hold4 = Binding.NONE
    var hold4Behavior = HoldBehavior.HOLD
    var holdDelay = 400

    var swipe3Up = Binding.NONE
    var swipe3Down = Binding.NONE
    var swipe3Left = Binding.NONE
    var swipe3Right = Binding.NONE
    var swipe4Up = Binding.NONE
    var swipe4Down = Binding.NONE
    var swipe4Left = Binding.NONE
    var swipe4Right = Binding.NONE

    var panAction = PanAction.NONE
    var zoomAction = ZoomAction.NONE
    var dragAction = DragAction.MOVE

    var swipeThreshold = 60
    var gestureThreshold = 40

    fun clone(): TouchGestureConfig {
        val c = TouchGestureConfig()
        c.tap1Enabled = tap1Enabled; c.tap1 = tap1
        c.tap2Enabled = tap2Enabled; c.tap2 = tap2
        c.tap3Enabled = tap3Enabled; c.tap3 = tap3
        c.tap4Enabled = tap4Enabled; c.tap4 = tap4
        c.doubleTapEnabled = doubleTapEnabled; c.doubleTapDelay = doubleTapDelay
        c.longPressEnabled = longPressEnabled; c.longPress = longPress
        c.longPressBehavior = longPressBehavior; c.longPressDelay = longPressDelay
        c.hold2Enabled = hold2Enabled; c.hold2 = hold2; c.hold2Behavior = hold2Behavior
        c.hold3Enabled = hold3Enabled; c.hold3 = hold3; c.hold3Behavior = hold3Behavior
        c.hold4Enabled = hold4Enabled; c.hold4 = hold4; c.hold4Behavior = hold4Behavior
        c.holdDelay = holdDelay
        c.swipe3Up = swipe3Up; c.swipe3Down = swipe3Down; c.swipe3Left = swipe3Left; c.swipe3Right = swipe3Right
        c.swipe4Up = swipe4Up; c.swipe4Down = swipe4Down; c.swipe4Left = swipe4Left; c.swipe4Right = swipe4Right
        c.panAction = panAction; c.zoomAction = zoomAction; c.dragAction = dragAction
        c.swipeThreshold = swipeThreshold; c.gestureThreshold = gestureThreshold
        return c
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("tap1Enabled", tap1Enabled); o.put("tap1", tap1.name)
        o.put("tap2Enabled", tap2Enabled); o.put("tap2", tap2.name)
        o.put("tap3Enabled", tap3Enabled); o.put("tap3", tap3.name)
        o.put("tap4Enabled", tap4Enabled); o.put("tap4", tap4.name)
        o.put("doubleTapEnabled", doubleTapEnabled); o.put("doubleTapDelay", doubleTapDelay)
        o.put("longPressEnabled", longPressEnabled); o.put("longPress", longPress.name)
        o.put("longPressBehavior", longPressBehavior.name); o.put("longPressDelay", longPressDelay)
        o.put("hold2Enabled", hold2Enabled); o.put("hold2", hold2.name); o.put("hold2Behavior", hold2Behavior.name)
        o.put("hold3Enabled", hold3Enabled); o.put("hold3", hold3.name); o.put("hold3Behavior", hold3Behavior.name)
        o.put("hold4Enabled", hold4Enabled); o.put("hold4", hold4.name); o.put("hold4Behavior", hold4Behavior.name)
        o.put("holdDelay", holdDelay)
        o.put("swipe3Up", swipe3Up.name); o.put("swipe3Down", swipe3Down.name)
        o.put("swipe3Left", swipe3Left.name); o.put("swipe3Right", swipe3Right.name)
        o.put("swipe4Up", swipe4Up.name); o.put("swipe4Down", swipe4Down.name)
        o.put("swipe4Left", swipe4Left.name); o.put("swipe4Right", swipe4Right.name)
        o.put("panAction", panAction.name); o.put("zoomAction", zoomAction.name); o.put("dragAction", dragAction.name)
        o.put("swipeThreshold", swipeThreshold); o.put("gestureThreshold", gestureThreshold)
        return o.toString()
    }

    companion object {
        fun fromJson(json: String?): TouchGestureConfig {
            val c = TouchGestureConfig()
            if (json.isNullOrBlank()) return c
            val o = try { JSONObject(json) } catch (e: Exception) { return c }
            c.tap1Enabled = o.optBoolean("tap1Enabled", c.tap1Enabled); c.tap1 = binding(o, "tap1", c.tap1)
            c.tap2Enabled = o.optBoolean("tap2Enabled", c.tap2Enabled); c.tap2 = binding(o, "tap2", c.tap2)
            c.tap3Enabled = o.optBoolean("tap3Enabled", c.tap3Enabled); c.tap3 = binding(o, "tap3", c.tap3)
            c.tap4Enabled = o.optBoolean("tap4Enabled", c.tap4Enabled); c.tap4 = binding(o, "tap4", c.tap4)
            c.doubleTapEnabled = o.optBoolean("doubleTapEnabled", c.doubleTapEnabled)
            c.doubleTapDelay = o.optInt("doubleTapDelay", c.doubleTapDelay)
            c.longPressEnabled = o.optBoolean("longPressEnabled", c.longPressEnabled)
            c.longPress = binding(o, "longPress", c.longPress)
            c.longPressBehavior = holdBehavior(o, "longPressBehavior", c.longPressBehavior)
            c.longPressDelay = o.optInt("longPressDelay", c.longPressDelay)
            c.hold2Enabled = o.optBoolean("hold2Enabled", c.hold2Enabled); c.hold2 = binding(o, "hold2", c.hold2)
            c.hold2Behavior = holdBehavior(o, "hold2Behavior", c.hold2Behavior)
            c.hold3Enabled = o.optBoolean("hold3Enabled", c.hold3Enabled); c.hold3 = binding(o, "hold3", c.hold3)
            c.hold3Behavior = holdBehavior(o, "hold3Behavior", c.hold3Behavior)
            c.hold4Enabled = o.optBoolean("hold4Enabled", c.hold4Enabled); c.hold4 = binding(o, "hold4", c.hold4)
            c.hold4Behavior = holdBehavior(o, "hold4Behavior", c.hold4Behavior)
            c.holdDelay = o.optInt("holdDelay", c.holdDelay)
            c.swipe3Up = binding(o, "swipe3Up", c.swipe3Up); c.swipe3Down = binding(o, "swipe3Down", c.swipe3Down)
            c.swipe3Left = binding(o, "swipe3Left", c.swipe3Left); c.swipe3Right = binding(o, "swipe3Right", c.swipe3Right)
            c.swipe4Up = binding(o, "swipe4Up", c.swipe4Up); c.swipe4Down = binding(o, "swipe4Down", c.swipe4Down)
            c.swipe4Left = binding(o, "swipe4Left", c.swipe4Left); c.swipe4Right = binding(o, "swipe4Right", c.swipe4Right)
            c.panAction = panAction(o, c.panAction); c.zoomAction = zoomAction(o, c.zoomAction); c.dragAction = dragAction(o, c.dragAction)
            c.swipeThreshold = o.optInt("swipeThreshold", c.swipeThreshold)
            c.gestureThreshold = o.optInt("gestureThreshold", c.gestureThreshold)
            return c
        }

        private fun binding(o: JSONObject, key: String, def: Binding): Binding =
            try { Binding.valueOf(o.optString(key, def.name)) } catch (e: Exception) { def }

        private fun holdBehavior(o: JSONObject, key: String, def: HoldBehavior): HoldBehavior =
            try { HoldBehavior.valueOf(o.optString(key, def.name)) } catch (e: Exception) { def }

        private fun panAction(o: JSONObject, def: PanAction): PanAction =
            try { PanAction.valueOf(o.optString("panAction", def.name)) } catch (e: Exception) { def }

        private fun zoomAction(o: JSONObject, def: ZoomAction): ZoomAction =
            try { ZoomAction.valueOf(o.optString("zoomAction", def.name)) } catch (e: Exception) { def }

        private fun dragAction(o: JSONObject, def: DragAction): DragAction =
            try { DragAction.valueOf(o.optString("dragAction", def.name)) } catch (e: Exception) { def }
    }
}
