package com.winlator.cmod.runtime.input.ui

import com.winlator.cmod.runtime.input.controls.Binding
import org.json.JSONObject

data class TouchGestureConfig(
    var enabled: Boolean = false,

    // Movement & Pinch
    var pinchEnabled: Boolean = true,
    var pinchAction: PinchAction = PinchAction.SCROLL_WHEEL,

    // Taps
    var oneFingerTapAction: Binding = Binding.MOUSE_LEFT_BUTTON,
    var oneFingerDoubleTapAction: Binding = Binding.NONE,
    var twoFingerTapAction: Binding = Binding.MOUSE_RIGHT_BUTTON,
    var threeFingerTapAction: Binding = Binding.MOUSE_MIDDLE_BUTTON,
    var fourFingerTapAction: Binding = Binding.NONE,

    // Holds (Long Press)
    var oneFingerLongPressAction: Binding = Binding.MOUSE_RIGHT_BUTTON,
    var oneFingerLongPressDuration: Int = 500,
    var twoFingerLongPressAction: Binding = Binding.NONE,
    var twoFingerLongPressDuration: Int = 500,
    var threeFingerLongPressAction: Binding = Binding.NONE,
    var threeFingerLongPressDuration: Int = 500,
    var fourFingerLongPressAction: Binding = Binding.NONE,
    var fourFingerLongPressDuration: Int = 500,

    // Swipes & Drag
    var oneFingerDragAction: DragAction = DragAction.LEFT_CLICK,
    var oneFingerSwipeUpAction: Binding = Binding.NONE,
    var oneFingerSwipeDownAction: Binding = Binding.NONE,
    var oneFingerSwipeLeftAction: Binding = Binding.NONE,
    var oneFingerSwipeRightAction: Binding = Binding.NONE,
    var twoFingerSwipeAction: PanAction = PanAction.SCROLL,
    var threeFingerSwipeUpAction: Binding = Binding.NONE,
    var threeFingerSwipeDownAction: Binding = Binding.NONE,
    var threeFingerSwipeLeftAction: Binding = Binding.NONE,
    var threeFingerSwipeRightAction: Binding = Binding.NONE,
    var fourFingerSwipeUpAction: Binding = Binding.NONE,
    var fourFingerSwipeDownAction: Binding = Binding.NONE,
    var fourFingerSwipeLeftAction: Binding = Binding.NONE,
    var fourFingerSwipeRightAction: Binding = Binding.NONE
) {
    enum class DragAction {
        MOUSE_MOVE,
        LEFT_CLICK
    }

    enum class PanAction {
        NONE,
        MIDDLE_CLICK,
        WASD,
        ARROW_KEYS,
        SCROLL
    }

    enum class PinchAction {
        NONE,
        SCROLL_WHEEL,
        ZOOM_KEYS
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("enabled", enabled)
        json.put("pinchEnabled", pinchEnabled)
        json.put("pinchAction", pinchAction.name)
        json.put("oneFingerTapAction", oneFingerTapAction.name)
        json.put("oneFingerDoubleTapAction", oneFingerDoubleTapAction.name)
        json.put("twoFingerTapAction", twoFingerTapAction.name)
        json.put("threeFingerTapAction", threeFingerTapAction.name)
        json.put("fourFingerTapAction", fourFingerTapAction.name)
        json.put("oneFingerLongPressAction", oneFingerLongPressAction.name)
        json.put("oneFingerLongPressDuration", oneFingerLongPressDuration)
        json.put("twoFingerLongPressAction", twoFingerLongPressAction.name)
        json.put("twoFingerLongPressDuration", twoFingerLongPressDuration)
        json.put("threeFingerLongPressAction", threeFingerLongPressAction.name)
        json.put("threeFingerLongPressDuration", threeFingerLongPressDuration)
        json.put("fourFingerLongPressAction", fourFingerLongPressAction.name)
        json.put("fourFingerLongPressDuration", fourFingerLongPressDuration)
        json.put("oneFingerDragAction", oneFingerDragAction.name)
        json.put("oneFingerSwipeUpAction", oneFingerSwipeUpAction.name)
        json.put("oneFingerSwipeDownAction", oneFingerSwipeDownAction.name)
        json.put("oneFingerSwipeLeftAction", oneFingerSwipeLeftAction.name)
        json.put("oneFingerSwipeRightAction", oneFingerSwipeRightAction.name)
        json.put("twoFingerSwipeAction", twoFingerSwipeAction.name)
        json.put("threeFingerSwipeUpAction", threeFingerSwipeUpAction.name)
        json.put("threeFingerSwipeDownAction", threeFingerSwipeDownAction.name)
        json.put("threeFingerSwipeLeftAction", threeFingerSwipeLeftAction.name)
        json.put("threeFingerSwipeRightAction", threeFingerSwipeRightAction.name)
        json.put("fourFingerSwipeUpAction", fourFingerSwipeUpAction.name)
        json.put("fourFingerSwipeDownAction", fourFingerSwipeDownAction.name)
        json.put("fourFingerSwipeLeftAction", fourFingerSwipeLeftAction.name)
        json.put("fourFingerSwipeRightAction", fourFingerSwipeRightAction.name)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject?): TouchGestureConfig {
            if (json == null) return TouchGestureConfig()
            val config = TouchGestureConfig()
            config.enabled = json.optBoolean("enabled", false)
            config.pinchEnabled = json.optBoolean("pinchEnabled", true)
            config.pinchAction = PinchAction.valueOf(json.optString("pinchAction", PinchAction.SCROLL_WHEEL.name))
            config.oneFingerTapAction = Binding.valueOf(json.optString("oneFingerTapAction", Binding.MOUSE_LEFT_BUTTON.name))
            config.oneFingerDoubleTapAction = Binding.valueOf(json.optString("oneFingerDoubleTapAction", Binding.NONE.name))
            config.twoFingerTapAction = Binding.valueOf(json.optString("twoFingerTapAction", Binding.MOUSE_RIGHT_BUTTON.name))
            config.threeFingerTapAction = Binding.valueOf(json.optString("threeFingerTapAction", Binding.MOUSE_MIDDLE_BUTTON.name))
            config.fourFingerTapAction = Binding.valueOf(json.optString("fourFingerTapAction", Binding.NONE.name))
            config.oneFingerLongPressAction = Binding.valueOf(json.optString("oneFingerLongPressAction", Binding.MOUSE_RIGHT_BUTTON.name))
            config.oneFingerLongPressDuration = json.optInt("oneFingerLongPressDuration", 500)
            config.twoFingerLongPressAction = Binding.valueOf(json.optString("twoFingerLongPressAction", Binding.NONE.name))
            config.twoFingerLongPressDuration = json.optInt("twoFingerLongPressDuration", 500)
            config.threeFingerLongPressAction = Binding.valueOf(json.optString("threeFingerLongPressAction", Binding.NONE.name))
            config.threeFingerLongPressDuration = json.optInt("threeFingerLongPressDuration", 500)
            config.fourFingerLongPressAction = Binding.valueOf(json.optString("fourFingerLongPressAction", Binding.NONE.name))
            config.fourFingerLongPressDuration = json.optInt("fourFingerLongPressDuration", 500)
            config.oneFingerDragAction = DragAction.valueOf(json.optString("oneFingerDragAction", DragAction.LEFT_CLICK.name))
            config.oneFingerSwipeUpAction = Binding.valueOf(json.optString("oneFingerSwipeUpAction", Binding.NONE.name))
            config.oneFingerSwipeDownAction = Binding.valueOf(json.optString("oneFingerSwipeDownAction", Binding.NONE.name))
            config.oneFingerSwipeLeftAction = Binding.valueOf(json.optString("oneFingerSwipeLeftAction", Binding.NONE.name))
            config.oneFingerSwipeRightAction = Binding.valueOf(json.optString("oneFingerSwipeRightAction", Binding.NONE.name))
            config.twoFingerSwipeAction = PanAction.valueOf(json.optString("twoFingerSwipeAction", PanAction.SCROLL.name))
            config.threeFingerSwipeUpAction = Binding.valueOf(json.optString("threeFingerSwipeUpAction", Binding.NONE.name))
            config.threeFingerSwipeDownAction = Binding.valueOf(json.optString("threeFingerSwipeDownAction", Binding.NONE.name))
            config.threeFingerSwipeLeftAction = Binding.valueOf(json.optString("threeFingerSwipeLeftAction", Binding.NONE.name))
            config.threeFingerSwipeRightAction = Binding.valueOf(json.optString("threeFingerSwipeRightAction", Binding.NONE.name))
            config.fourFingerSwipeUpAction = Binding.valueOf(json.optString("fourFingerSwipeUpAction", Binding.NONE.name))
            config.fourFingerSwipeDownAction = Binding.valueOf(json.optString("fourFingerSwipeDownAction", Binding.NONE.name))
            config.fourFingerSwipeLeftAction = Binding.valueOf(json.optString("fourFingerSwipeLeftAction", Binding.NONE.name))
            config.fourFingerSwipeRightAction = Binding.valueOf(json.optString("fourFingerSwipeRightAction", Binding.NONE.name))
            return config
        }
    }
}
