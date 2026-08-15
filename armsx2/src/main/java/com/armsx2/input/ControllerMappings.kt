package com.armsx2.input

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import com.armsx2.runtime.MainActivityRuntime
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File

object ControllerMappings {
    data class Action(
        val id: String,
        val label: String,
        val targetKeyCode: Int,
        val defaultPhysicalKeyCode: Int,
    )

    val actions = listOf(
        Action("dpad_up", "D-Pad Up", KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP),
        Action("dpad_down", "D-Pad Down", KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
        Action("dpad_left", "D-Pad Left", KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
        Action("dpad_right", "D-Pad Right", KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT),
        Action("cross", "Cross", KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A),
        Action("circle", "Circle", KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_B),
        Action("square", "Square", KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_X),
        Action("triangle", "Triangle", KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Y),
        Action("l1", "L1", KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1),
        Action("r1", "R1", KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1),
        Action("l2", "L2", KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_L2),
        Action("r2", "R2", KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_R2),
        Action("l3", "L3", KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBL),
        Action("r3", "R3", KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_THUMBR),
        Action("select", "Select", KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_SELECT),
        Action("start", "Start", KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START),
        Action("analog", "Analog (toggle)", 200, KeyEvent.KEYCODE_UNKNOWN),
        Action("ls_up", "L-Stick Up (send)", 110, KeyEvent.KEYCODE_UNKNOWN),
        Action("ls_down", "L-Stick Down (send)", 112, KeyEvent.KEYCODE_UNKNOWN),
        Action("ls_left", "L-Stick Left (send)", 113, KeyEvent.KEYCODE_UNKNOWN),
        Action("ls_right", "L-Stick Right (send)", 111, KeyEvent.KEYCODE_UNKNOWN),
        Action("rs_up", "R-Stick Up (send)", 120, KeyEvent.KEYCODE_UNKNOWN),
        Action("rs_down", "R-Stick Down (send)", 122, KeyEvent.KEYCODE_UNKNOWN),
        Action("rs_left", "R-Stick Left (send)", 123, KeyEvent.KEYCODE_UNKNOWN),
        Action("rs_right", "R-Stick Right (send)", 121, KeyEvent.KEYCODE_UNKNOWN),
    )

    enum class StickMode(val id: String, val label: String) {
        ANALOG("analog", "Analog"),
        FACE("face", "Face"),
        CUSTOM("custom", "Custom"),
    }

    const val P1 = 0
    const val P2 = 1
    private fun playerPrefix(player: Int) = if (player == 1) "p2." else ""

    private fun gameKey(serial: String, baseKey: String) = "game.$serial.$baseKey"
    private fun scopedKey(baseKey: String, serial: String?) =
        if (serial.isNullOrEmpty()) baseKey else gameKey(serial, baseKey)

    private fun runtimeSerial(): String? = MainActivityRuntime.currentGame.value?.serial?.takeIf { it.isNotEmpty() }

    private fun resolveInt(baseKey: String, default: Int): Int {
        val s = runtimeSerial()
        if (s != null) {
            val gk = gameKey(s, baseKey)
            if (MainActivityRuntime.prefs.contains(gk)) return MainActivityRuntime.prefs.getInt(gk, default)
        }
        return MainActivityRuntime.prefs.getInt(baseKey, default)
    }

    private fun resolveString(baseKey: String, default: String): String {
        val s = runtimeSerial()
        if (s != null) MainActivityRuntime.prefs.getString(gameKey(s, baseKey), null)?.let { return it }
        return MainActivityRuntime.prefs.getString(baseKey, default) ?: default
    }

    private fun resolveBoolean(baseKey: String, default: Boolean): Boolean {
        val s = runtimeSerial()
        if (s != null) {
            val gk = gameKey(s, baseKey)
            if (MainActivityRuntime.prefs.contains(gk)) return MainActivityRuntime.prefs.getBoolean(gk, default)
        }
        return MainActivityRuntime.prefs.getBoolean(baseKey, default)
    }

    private fun scopedInt(baseKey: String, serial: String?, default: Int): Int {
        val key = scopedKey(baseKey, serial)
        if (MainActivityRuntime.prefs.contains(key)) return MainActivityRuntime.prefs.getInt(key, default)
        return MainActivityRuntime.prefs.getInt(baseKey, default)
    }

    private fun scopedBoolean(baseKey: String, serial: String?, default: Boolean): Boolean {
        val key = scopedKey(baseKey, serial)
        if (MainActivityRuntime.prefs.contains(key)) return MainActivityRuntime.prefs.getBoolean(key, default)
        return MainActivityRuntime.prefs.getBoolean(baseKey, default)
    }

    private const val KEY_LSTICK = "pad.lstick.mode"
    private const val KEY_RSTICK = "pad.rstick.mode"

    private fun stickModeFromId(id: String?): StickMode =
        StickMode.entries.firstOrNull { it.id == id } ?: StickMode.ANALOG

    fun leftStickMode(player: Int = 0): StickMode =
        stickModeFromId(resolveString(playerPrefix(player) + KEY_LSTICK, StickMode.ANALOG.id))
    fun rightStickMode(player: Int = 0): StickMode =
        stickModeFromId(resolveString(playerPrefix(player) + KEY_RSTICK, StickMode.ANALOG.id))
    fun stickModeFor(left: Boolean, player: Int = 0): StickMode =
        if (left) leftStickMode(player) else rightStickMode(player)

    fun leftStickModeScope(player: Int, serial: String?): StickMode =
        stickModeFromId(MainActivityRuntime.prefs.getString(scopedKey(playerPrefix(player) + KEY_LSTICK, serial), null)
            ?: MainActivityRuntime.prefs.getString(playerPrefix(player) + KEY_LSTICK, StickMode.ANALOG.id))
    fun rightStickModeScope(player: Int, serial: String?): StickMode =
        stickModeFromId(MainActivityRuntime.prefs.getString(scopedKey(playerPrefix(player) + KEY_RSTICK, serial), null)
            ?: MainActivityRuntime.prefs.getString(playerPrefix(player) + KEY_RSTICK, StickMode.ANALOG.id))
    fun setLeftStickMode(m: StickMode, player: Int = 0, serial: String? = null) =
        MainActivityRuntime.prefs.edit {
            putString(
                scopedKey(
                    playerPrefix(player) + KEY_LSTICK,
                    serial
                ), m.id
            )
        }

    fun setRightStickMode(m: StickMode, player: Int = 0, serial: String? = null) =
        MainActivityRuntime.prefs.edit {
            putString(
                scopedKey(
                    playerPrefix(player) + KEY_RSTICK,
                    serial
                ), m.id
            )
        }

    private const val KEY_LSTICK_INVX = "pad.lstick.invertX"
    private const val KEY_LSTICK_INVY = "pad.lstick.invertY"
    private const val KEY_LSTICK_SWAP = "pad.lstick.swapXY"
    private const val KEY_RSTICK_INVX = "pad.rstick.invertX"
    private const val KEY_RSTICK_INVY = "pad.rstick.invertY"
    private const val KEY_RSTICK_SWAP = "pad.rstick.swapXY"
    private fun invXKey(left: Boolean) = if (left) KEY_LSTICK_INVX else KEY_RSTICK_INVX
    private fun invYKey(left: Boolean) = if (left) KEY_LSTICK_INVY else KEY_RSTICK_INVY
    private fun swapKey(left: Boolean) = if (left) KEY_LSTICK_SWAP else KEY_RSTICK_SWAP
    fun stickInvertX(left: Boolean): Boolean = resolveBoolean(invXKey(left), false)
    fun stickInvertY(left: Boolean): Boolean = resolveBoolean(invYKey(left), false)
    fun stickSwapXY(left: Boolean): Boolean = resolveBoolean(swapKey(left), false)
    fun stickInvertXScope(left: Boolean, serial: String?): Boolean = scopedBoolean(invXKey(left), serial, false)
    fun stickInvertYScope(left: Boolean, serial: String?): Boolean = scopedBoolean(invYKey(left), serial, false)
    fun stickSwapXYScope(left: Boolean, serial: String?): Boolean = scopedBoolean(swapKey(left), serial, false)
    fun setStickInvertX(left: Boolean, on: Boolean, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putBoolean(scopedKey(invXKey(left), serial), on) }
    fun setStickInvertY(left: Boolean, on: Boolean, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putBoolean(scopedKey(invYKey(left), serial), on) }
    fun setStickSwapXY(left: Boolean, on: Boolean, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putBoolean(scopedKey(swapKey(left), serial), on) }

    private const val KEY_DPAD_AS_LSTICK = "pad.dpadAsLeftStick"
    fun dpadAsLeftStick(): Boolean = resolveBoolean(KEY_DPAD_AS_LSTICK, false)
    fun dpadAsLeftStickScope(serial: String?): Boolean = scopedBoolean(KEY_DPAD_AS_LSTICK, serial, false)
    fun setDpadAsLeftStick(on: Boolean, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putBoolean(scopedKey(KEY_DPAD_AS_LSTICK, serial), on) }

    private const val KEY_STICK_SENS = "pad.stick.sensitivity"
    private const val KEY_STICK_ACCEL = "pad.stick.acceleration"
    const val STICK_SENS_MIN = 0.5f
    const val STICK_SENS_MAX = 2.0f
    const val STICK_ACCEL_MAX = 2.0f

    private class PerStickPref(val baseKey: String, val def: Float, val lo: Float, val hi: Float) {
        @Volatile var cacheL = Float.NaN
        @Volatile var cacheR = Float.NaN
        fun key(left: Boolean) = baseKey + if (left) ".l" else ".r"
        fun get(left: Boolean): Float {
            val c = if (left) cacheL else cacheR
            if (!c.isNaN()) return c
            val v = (if (MainActivityRuntime.prefs.contains(key(left))) MainActivityRuntime.prefs.getFloat(key(left), def)
                else MainActivityRuntime.prefs.getFloat(baseKey, def)).coerceIn(lo, hi)
            if (left) cacheL = v else cacheR = v
            return v
        }
        fun set(left: Boolean, v: Float) {
            val c = v.coerceIn(lo, hi)
            if (left) cacheL = c else cacheR = c
            MainActivityRuntime.prefs.edit { putFloat(key(left), c) }
        }
        fun reset(edit: android.content.SharedPreferences.Editor) {
            edit.remove(baseKey).remove(key(true)).remove(key(false))
            cacheL = Float.NaN; cacheR = Float.NaN
        }
    }
    private val prefStickSens = PerStickPref(KEY_STICK_SENS, 1.0f, STICK_SENS_MIN, STICK_SENS_MAX)
    private val prefStickAccel = PerStickPref(KEY_STICK_ACCEL, 0.0f, 0f, STICK_ACCEL_MAX)
    fun stickSensitivity(left: Boolean): Float = prefStickSens.get(left)
    fun setStickSensitivity(left: Boolean, v: Float) = prefStickSens.set(left, v)
    fun stickAcceleration(left: Boolean): Float = prefStickAccel.get(left)
    fun setStickAcceleration(left: Boolean, v: Float) = prefStickAccel.set(left, v)

    private const val KEY_STICK_DZ = "pad.stick.deadzone"
    const val STICK_DZ_MAX = 0.40f
    private val prefStickDz = PerStickPref(KEY_STICK_DZ, 0.05f, 0f, STICK_DZ_MAX)
    fun stickDeadzone(left: Boolean): Float = prefStickDz.get(left)
    fun setStickDeadzone(left: Boolean, v: Float) = prefStickDz.set(left, v)

    private const val KEY_STICK_OUTER = "pad.stick.outerDeadzone"
    const val STICK_OUTER_MAX = 0.40f
    private val prefStickOuter = PerStickPref(KEY_STICK_OUTER, 0.0f, 0f, STICK_OUTER_MAX)
    fun stickOuterDeadzone(left: Boolean): Float = prefStickOuter.get(left)
    fun setStickOuterDeadzone(left: Boolean, v: Float) = prefStickOuter.set(left, v)

    private const val KEY_STICK_ANTIDZ = "pad.stick.antiDeadzone"
    const val STICK_ANTIDZ_MAX = 0.60f
    private val prefStickAntiDz = PerStickPref(KEY_STICK_ANTIDZ, 0.0f, 0f, STICK_ANTIDZ_MAX)
    fun stickAntiDeadzone(left: Boolean): Float = prefStickAntiDz.get(left)
    fun setStickAntiDeadzone(left: Boolean, v: Float) = prefStickAntiDz.set(left, v)

    private const val KEY_RUMBLE = "pad.rumble.enabled"
    fun rumbleEnabled(): Boolean = MainActivityRuntime.prefs.getBoolean(KEY_RUMBLE, true)
    fun setRumbleEnabled(on: Boolean) {
        MainActivityRuntime.prefs.edit { putBoolean(KEY_RUMBLE, on) }
        MainActivityRuntime.nativeSetRumbleEnabled(on)
    }

    private const val KEY_MULTITAP = "pad.multitap.enabled"
    fun multitapEnabled(): Boolean = MainActivityRuntime.prefs.getBoolean(KEY_MULTITAP, false)
    fun setMultitapEnabled(on: Boolean) {
        MainActivityRuntime.prefs.edit { putBoolean(KEY_MULTITAP, on) }
        com.armsx2.input.PadRouter.multitapEnabled = on
        if (MainActivityRuntime.nativeReady.value) {
            kotlin.concurrent.thread(name = "armsx2-multitap") {
                runCatching {
                    MainActivityRuntime.nativeSetMultitap(0, on)
                    MainActivityRuntime.nativeSetMultitap(1, on)
                }
            }
        }
    }

    const val GYRO_OFF = 0
    const val GYRO_AIM = 1
    const val GYRO_STEER = 2
    private const val KEY_GYRO_MODE = "pad.gyro.mode"
    private const val KEY_GYRO_SENS = "pad.gyro.sensitivity"
    private const val KEY_GYRO_SMOOTH = "pad.gyro.smoothing"
    private const val KEY_GYRO_INVX = "pad.gyro.invertX"
    private const val KEY_GYRO_INVY = "pad.gyro.invertY"
    const val GYRO_STICK_RIGHT = 0
    const val GYRO_STICK_LEFT = 1
    private const val KEY_GYRO_AIM_STICK = "pad.gyro.aimStick"

    fun gyroMode(): Int = resolveInt(KEY_GYRO_MODE, GYRO_OFF).coerceIn(0, 2)
    fun gyroSensitivity(): Int = resolveInt(KEY_GYRO_SENS, 100).coerceIn(25, 300)
    fun gyroSmoothing(): Int = resolveInt(KEY_GYRO_SMOOTH, 45).coerceIn(0, 90)
    fun gyroInvertX(): Boolean = resolveBoolean(KEY_GYRO_INVX, false)
    fun gyroInvertY(): Boolean = resolveBoolean(KEY_GYRO_INVY, false)
    fun gyroAimStick(): Int = resolveInt(KEY_GYRO_AIM_STICK, GYRO_STICK_RIGHT).coerceIn(0, 1)

    fun gyroModeScope(serial: String?): Int = scopedInt(KEY_GYRO_MODE, serial, GYRO_OFF).coerceIn(0, 2)
    fun gyroSensitivityScope(serial: String?): Int = scopedInt(KEY_GYRO_SENS, serial, 100).coerceIn(25, 300)
    fun gyroSmoothingScope(serial: String?): Int = scopedInt(KEY_GYRO_SMOOTH, serial, 45).coerceIn(0, 90)
    fun gyroInvertXScope(serial: String?): Boolean = scopedBoolean(KEY_GYRO_INVX, serial, false)
    fun gyroInvertYScope(serial: String?): Boolean = scopedBoolean(KEY_GYRO_INVY, serial, false)
    fun gyroAimStickScope(serial: String?): Int = scopedInt(KEY_GYRO_AIM_STICK, serial, GYRO_STICK_RIGHT).coerceIn(0, 1)

    fun setGyroMode(value: Int, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putInt(scopedKey(KEY_GYRO_MODE, serial), value) }
    fun setGyroSensitivity(value: Int, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putInt(scopedKey(KEY_GYRO_SENS, serial), value) }
    fun setGyroSmoothing(value: Int, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putInt(scopedKey(KEY_GYRO_SMOOTH, serial), value) }
    fun setGyroInvertX(on: Boolean, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putBoolean(scopedKey(KEY_GYRO_INVX, serial), on) }
    fun setGyroInvertY(on: Boolean, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putBoolean(scopedKey(KEY_GYRO_INVY, serial), on) }
    fun setGyroAimStick(value: Int, serial: String? = null) =
        MainActivityRuntime.prefs.edit { putInt(scopedKey(KEY_GYRO_AIM_STICK, serial), value) }

    enum class StickDir(val id: String) { UP("up"), DOWN("down"), LEFT("left"), RIGHT("right") }

    data class PsButton(val code: Int, val label: String)
    val stickTargets = listOf(
        PsButton(19, "D-Pad Up"), PsButton(20, "D-Pad Down"),
        PsButton(21, "D-Pad Left"), PsButton(22, "D-Pad Right"),
        PsButton(96, "Cross"), PsButton(97, "Circle"),
        PsButton(99, "Square"), PsButton(100, "Triangle"),
        PsButton(102, "L1"), PsButton(103, "R1"),
        PsButton(104, "L2"), PsButton(105, "R2"),
        PsButton(106, "L3"), PsButton(107, "R3"),
        PsButton(108, "Start"), PsButton(109, "Select"),
        PsButton(200, "Analog (toggle)"),
    )
    fun stickTargetLabel(code: Int): String =
        hotkeyForStickCode(code)?.let { "Hotkey: ${it.label}" }
            ?: if (code in 110..123) "Analog (default)"
            else stickTargets.firstOrNull { it.code == code }?.label ?: "Code $code"

    private fun defaultCustomCode(left: Boolean, dir: StickDir): Int = when {
        left && dir == StickDir.UP -> 110
        left && dir == StickDir.DOWN -> 112
        left && dir == StickDir.LEFT -> 113
        left && dir == StickDir.RIGHT -> 111
        !left && dir == StickDir.UP -> 120
        !left && dir == StickDir.DOWN -> 122
        !left && dir == StickDir.LEFT -> 123
        else -> 121
    }
    private fun customKey(left: Boolean, dir: StickDir, player: Int = 0) =
        playerPrefix(player) + "pad.${if (left) "lstick" else "rstick"}.${dir.id}.code"
    fun customStickCode(left: Boolean, dir: StickDir, player: Int = 0): Int =
        resolveInt(customKey(left, dir, player), defaultCustomCode(left, dir))
    fun setCustomStickCode(left: Boolean, dir: StickDir, code: Int, player: Int = 0, serial: String? = null) =
        MainActivityRuntime.prefs.edit {
            putInt(
                scopedKey(customKey(left, dir, player), serial),
                code
            )
        }
    fun customStickCodeScope(left: Boolean, dir: StickDir, player: Int, serial: String?): Int =
        scopedInt(customKey(left, dir, player), serial, defaultCustomCode(left, dir))

    const val HOTKEY_STICK_CODE_BASE = 300
    fun stickCodeForHotkey(h: SysHotkey): Int = HOTKEY_STICK_CODE_BASE + h.ordinal
    fun hotkeyForStickCode(code: Int): SysHotkey? {
        val i = code - HOTKEY_STICK_CODE_BASE
        return if (i in SysHotkey.values().indices) SysHotkey.values()[i] else null
    }

    val captureStickDir = mutableStateOf<Pair<Boolean, StickDir>?>(null)

    val stickBindTick = mutableStateOf(0)

    fun stickCodeForPhysical(physicalKeyCode: Int, player: Int = 0): Int? =
        targetForPhysical(physicalKeyCode, player)

    fun beginStickCapture(left: Boolean, dir: StickDir) { captureStickDir.value = left to dir }
    fun endStickCapture() { captureStickDir.value = null; stickBindTick.value++ }

    fun resetStickCode(left: Boolean, dir: StickDir, player: Int = 0, serial: String? = null) {
        MainActivityRuntime.prefs.edit { remove(scopedKey(customKey(left, dir, player), serial)) }; stickBindTick.value++
    }

    private const val KEY_PREFIX = "pad.map."

    fun physicalFor(action: Action, player: Int = 0): Int =
        resolveInt(playerPrefix(player) + KEY_PREFIX + action.id, action.defaultPhysicalKeyCode)

    fun physicalForScope(action: Action, player: Int, serial: String?): Int =
        scopedInt(playerPrefix(player) + KEY_PREFIX + action.id, serial, action.defaultPhysicalKeyCode)

    const val STICK_HOTKEY_KEY_BASE = 1000
    fun stickHotkeyKeyCode(left: Boolean, dir: StickDir): Int =
        STICK_HOTKEY_KEY_BASE + (if (left) 0 else 4) + dir.ordinal

    fun labelForKey(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_UNKNOWN -> "Not set"
        in STICK_HOTKEY_KEY_BASE until STICK_HOTKEY_KEY_BASE + 8 -> {
            val i = keyCode - STICK_HOTKEY_KEY_BASE
            "${if (i < 4) "L-Stick" else "R-Stick"} ${StickDir.values()[i % 4].id.replaceFirstChar { it.uppercase() }}"
        }
        KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
        KeyEvent.KEYCODE_BUTTON_A -> "Button A"
        KeyEvent.KEYCODE_BUTTON_B -> "Button B"
        KeyEvent.KEYCODE_BUTTON_X -> "Button X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Button Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }

    fun bind(action: Action, physicalKeyCode: Int, player: Int = 0, serial: String? = null) {
        MainActivityRuntime.prefs.edit {
            putInt(
                scopedKey(
                    playerPrefix(player) + KEY_PREFIX + action.id,
                    serial
                ), physicalKeyCode
            )
        }
    }

    fun clearAction(action: Action, player: Int = 0, serial: String? = null) {
        MainActivityRuntime.prefs.edit().putInt(scopedKey(playerPrefix(player) + KEY_PREFIX + action.id, serial), KeyEvent.KEYCODE_UNKNOWN).apply()
    }

    fun reset(player: Int = 0, serial: String? = null) {
        val edit = MainActivityRuntime.prefs.edit()
        actions.forEach { edit.remove(scopedKey(playerPrefix(player) + KEY_PREFIX + it.id, serial)) }
        edit.apply()
    }

    fun clearGameOverrides(serial: String, player: Int) {
        if (serial.isEmpty()) return
        val edit = MainActivityRuntime.prefs.edit()
        actions.forEach { edit.remove(gameKey(serial, playerPrefix(player) + KEY_PREFIX + it.id)) }
        edit.remove(gameKey(serial, playerPrefix(player) + KEY_LSTICK))
            .remove(gameKey(serial, playerPrefix(player) + KEY_RSTICK))
        for (left in booleanArrayOf(true, false))
            for (dir in StickDir.values())
                edit.remove(gameKey(serial, customKey(left, dir, player)))
        edit.apply()
        stickBindTick.value++
    }

    private const val KEY_PAD_PROFILES = "pad.profiles"
    private const val PAD_PROFILE_FILE_SUFFIX = ".pad.json"

    private fun mappingKeys(): List<String> = buildList {
        actions.forEach { add(KEY_PREFIX + it.id) }
        add(KEY_LSTICK)
        add(KEY_RSTICK)
        for (left in booleanArrayOf(true, false))
            for (dir in StickDir.values())
                add("pad.${if (left) "lstick" else "rstick"}.${dir.id}.code")
    }

    fun captureProfile(player: Int, serial: String?): JSONObject {
        val o = JSONObject()
        actions.forEach { o.put(KEY_PREFIX + it.id, physicalForScope(it, player, serial)) }
        o.put(KEY_LSTICK, leftStickModeScope(player, serial).id)
        o.put(KEY_RSTICK, rightStickModeScope(player, serial).id)
        for (left in booleanArrayOf(true, false))
            for (dir in StickDir.values())
                o.put(
                    "pad.${if (left) "lstick" else "rstick"}.${dir.id}.code",
                    customStickCodeScope(left, dir, player, serial),
                )
        return o
    }

    fun applyProfile(values: JSONObject, player: Int, serial: String?) {
        val pfx = playerPrefix(player)
        val edit = MainActivityRuntime.prefs.edit()
        mappingKeys().forEach { base ->
            if (!values.has(base)) return@forEach
            val target = scopedKey(pfx + base, serial)
            when (base) {
                KEY_LSTICK, KEY_RSTICK -> edit.putString(target, values.optString(base))
                else -> edit.putInt(target, values.optInt(base))
            }
        }
        edit.apply()
        stickBindTick.value++
    }

    private fun readProfiles(): JSONObject =
        runCatching { JSONObject(MainActivityRuntime.prefs.getString(KEY_PAD_PROFILES, "{}") ?: "{}") }
            .getOrDefault(JSONObject())

    private fun padFileNameFor(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _.-]"), "_") + PAD_PROFILE_FILE_SUFFIX

    private fun syncPadProfileFolder() {
        val dir = MainActivityRuntime.inputProfilesDir() ?: return
        runCatching {
            val store = readProfiles()
            val want = HashMap<String, JSONObject>()
            store.keys().forEach { n ->
                store.optJSONObject(n)?.let { v ->
                    want[padFileNameFor(n)] = JSONObject().put("name", n).put("values", v)
                }
            }
            for ((fn, body) in want) File(dir, fn).writeText(body.toString())
            dir.listFiles { f -> f.name.endsWith(PAD_PROFILE_FILE_SUFFIX) }?.forEach { f ->
                if (f.name !in want) runCatching { f.delete() }
            }
        }
    }

    private fun importPadProfileFolder(store: JSONObject) {
        val dir = MainActivityRuntime.inputProfilesDir() ?: return
        runCatching {
            dir.listFiles { f -> f.name.endsWith(PAD_PROFILE_FILE_SUFFIX) }
                ?.sortedBy { it.name }
                ?.forEach { f ->
                    runCatching {
                        val o = JSONObject(f.readText())
                        val n = o.optString("name")
                        val v = o.optJSONObject("values")
                        if (n.isNotEmpty() && v != null && !store.has(n)) store.put(n, v)
                    }
                }
        }
    }

    val padProfileTick = mutableStateOf(0)

    fun listProfiles(): List<String> {
        val store = readProfiles()
        importPadProfileFolder(store)
        return store.keys().asSequence().toList().sortedBy { it.lowercase() }
    }

    fun saveProfile(name: String, player: Int, serial: String?): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val store = readProfiles()
        importPadProfileFolder(store)
        store.put(trimmed, captureProfile(player, serial))
        MainActivityRuntime.prefs.edit { putString(KEY_PAD_PROFILES, store.toString()) }
        syncPadProfileFolder()
        padProfileTick.value++
        return true
    }

    fun applyProfile(name: String, player: Int, serial: String?): Boolean {
        val store = readProfiles()
        importPadProfileFolder(store)
        val values = store.optJSONObject(name) ?: return false
        applyProfile(values, player, serial)
        return true
    }

    fun deleteProfile(name: String) {
        val store = readProfiles()
        importPadProfileFolder(store)
        store.remove(name)
        MainActivityRuntime.prefs.edit { putString(KEY_PAD_PROFILES, store.toString()) }
        syncPadProfileFolder()
        padProfileTick.value++
    }

    fun hasGameOverrides(serial: String?, player: Int): Boolean {
        if (serial.isNullOrEmpty()) return false
        if (actions.any { MainActivityRuntime.prefs.contains(gameKey(serial, playerPrefix(player) + KEY_PREFIX + it.id)) }) return true
        if (MainActivityRuntime.prefs.contains(gameKey(serial, playerPrefix(player) + KEY_LSTICK))) return true
        if (MainActivityRuntime.prefs.contains(gameKey(serial, playerPrefix(player) + KEY_RSTICK))) return true
        for (left in booleanArrayOf(true, false))
            for (dir in StickDir.values())
                if (MainActivityRuntime.prefs.contains(gameKey(serial, customKey(left, dir, player)))) return true
        return false
    }

    fun resetTunables() {
        MainActivityRuntime.prefs.edit {
            remove(KEY_DPAD_AS_LSTICK)
                .remove(KEY_LSTICK_INVX).remove(KEY_LSTICK_INVY).remove(KEY_LSTICK_SWAP)
                .remove(KEY_RSTICK_INVX).remove(KEY_RSTICK_INVY).remove(KEY_RSTICK_SWAP)
            prefStickSens.reset(this); prefStickAccel.reset(this); prefStickDz.reset(this)
            prefStickOuter.reset(this); prefStickAntiDz.reset(this)
            for (p in intArrayOf(P1, P2)) {
                remove(playerPrefix(p) + KEY_LSTICK).remove(playerPrefix(p) + KEY_RSTICK)
                for (left in booleanArrayOf(true, false))
                    for (dir in StickDir.values())
                        remove(customKey(left, dir, p))
            }
        }
        stickBindTick.value++
    }

    fun targetForPhysical(physicalKeyCode: Int, player: Int = 0): Int? {
        if (physicalKeyCode == KeyEvent.KEYCODE_UNKNOWN) return null
        return actions.firstOrNull { physicalFor(it, player) == physicalKeyCode }?.targetKeyCode
    }

    private const val TURBO_PREFIX = "pad.turbo."
    private fun turboKey(action: Action, player: Int) = playerPrefix(player) + TURBO_PREFIX + action.id
    fun isTurboAction(action: Action, player: Int = 0): Boolean =
        MainActivityRuntime.prefs.getBoolean(turboKey(action, player), false)
    fun setTurboAction(action: Action, player: Int, on: Boolean) =
        MainActivityRuntime.prefs.edit { putBoolean(turboKey(action, player), on) }

    fun isTurboTarget(targetKeyCode: Int, player: Int = 0): Boolean {
        val action = actions.firstOrNull { it.targetKeyCode == targetKeyCode } ?: return false
        return isTurboAction(action, player)
    }

    enum class SysHotkey(val prefKey: String, val label: String) {
        MENU("pad.menu.keycode", "Menu / Pause"),
        SAVE_STATE("pad.savestate.keycode", "Quick Save State"),
        LOAD_STATE("pad.loadstate.keycode", "Quick Load State"),
        CYCLE_SLOT("pad.cycleslot.keycode", "Cycle Save Slot"),
        TEXTURE_DUMP("pad.texdump.keycode", "Toggle Texture Dumping"),
        TOGGLE_OSD("pad.toggleosd.keycode", "Toggle Perf Stats (OSD)"),
        FAST_FORWARD("pad.fastforward.keycode", "Fast Forward (hold)"),
        FAST_FORWARD_TOGGLE("pad.fastforwardtoggle.keycode", "Fast Forward (toggle)"),
        SLOW_DOWN("pad.slowdown.keycode", "Slow Down (toggle)"),
        RES_UP("pad.resup.keycode", "Increase Resolution"),
        RES_DOWN("pad.resdown.keycode", "Decrease Resolution"),
        ACHIEVEMENTS("pad.achievements.keycode", "Open Achievements"),
        CLOSE_GAME("pad.closegame.keycode", "Close Game"),
        QUIT_APP("pad.quitapp.keycode", "Close Game & Quit"),
        SAVE_AND_EXIT("pad.saveandexit.keycode", "Save State & Exit"),
        RESET_GAME("pad.resetgame.keycode", "Reset Game"),
        PRESSURE_MOD("pad.pressuremod.keycode", "Pressure Modifier (hold)"),
        GYRO_TOGGLE("pad.gyrotoggle.keycode", "Gyro On/Off (toggle)"),
        GYRO_HOLD("pad.gyrohold.keycode", "Gyro (hold to aim)"),
    }

    private const val MOD_SUFFIX = ".mod"

    fun hotkeyCode(h: SysHotkey): Int =
        MainActivityRuntime.prefs.getInt(h.prefKey, KeyEvent.KEYCODE_UNKNOWN)

    fun hotkeyModCode(h: SysHotkey): Int =
        MainActivityRuntime.prefs.getInt(h.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)

    fun bindHotkey(h: SysHotkey, physicalKeyCode: Int) {
        MainActivityRuntime.prefs.edit {
            putInt(h.prefKey, physicalKeyCode)
                .putInt(h.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)
        }
    }

    fun bindHotkeyCombo(h: SysHotkey, modCode: Int, keyCode: Int) {
        MainActivityRuntime.prefs.edit {
            putInt(h.prefKey, keyCode)
                .putInt(h.prefKey + MOD_SUFFIX, modCode)
        }
    }

    fun clearHotkey(h: SysHotkey) {
        MainActivityRuntime.prefs.edit {
            putInt(h.prefKey, KeyEvent.KEYCODE_UNKNOWN)
                .putInt(h.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)
        }
    }

    fun clearAllHotkeys() {
        MainActivityRuntime.prefs.edit {
            SysHotkey.values().forEach {
                putInt(it.prefKey, KeyEvent.KEYCODE_UNKNOWN)
                    .putInt(it.prefKey + MOD_SUFFIX, KeyEvent.KEYCODE_UNKNOWN)
            }
        }
        hotkeyBindTick.value++
    }

    fun hotkeyLabel(h: SysHotkey): String {
        val key = hotkeyCode(h)
        if (key == KeyEvent.KEYCODE_UNKNOWN) return ""
        val mod = hotkeyModCode(h)
        return if (mod == KeyEvent.KEYCODE_UNKNOWN) labelForKey(key)
        else "${labelForKey(mod)} + ${labelForKey(key)}"
    }

    fun hotkeyFor(physicalKeyCode: Int): SysHotkey? {
        if (physicalKeyCode == KeyEvent.KEYCODE_UNKNOWN) return null
        return SysHotkey.values().firstOrNull {
            hotkeyCode(it) == physicalKeyCode && hotkeyModCode(it) == KeyEvent.KEYCODE_UNKNOWN
        }
    }

    fun matchHotkey(keyCode: Int, heldKeys: Set<Int>): SysHotkey? {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return null
        SysHotkey.values().firstOrNull {
            hotkeyCode(it) == keyCode &&
                hotkeyModCode(it) != KeyEvent.KEYCODE_UNKNOWN &&
                heldKeys.contains(hotkeyModCode(it))
        }?.let { return it }
        return SysHotkey.values().firstOrNull {
            hotkeyCode(it) == keyCode && hotkeyModCode(it) == KeyEvent.KEYCODE_UNKNOWN
        }
    }

    val padCapturing = mutableStateOf(false)

    val capturePadAction = mutableStateOf<((Int) -> Boolean)?>(null)

    val captureHotkey = mutableStateOf<SysHotkey?>(null)

    val captureKeys = mutableListOf<Int>()

    var captureFirstDownMs = 0L

    fun beginHotkeyCapture(h: SysHotkey) {
        captureKeys.clear()
        captureHotkey.value = h
    }

    fun endHotkeyCapture() {
        captureKeys.clear()
        captureHotkey.value = null
        hotkeyBindTick.value++
    }

    val hotkeyBindTick = mutableStateOf(0)
}
