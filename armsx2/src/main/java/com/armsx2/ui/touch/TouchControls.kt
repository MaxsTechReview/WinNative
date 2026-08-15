package com.armsx2.ui.touch

import android.view.KeyEvent
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.InGameOverlay
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.core.content.edit

object TouchControls {
    private const val KEY_PROFILES = "touch.profiles"
    private const val KEY_ACTIVE = "touch.active"
    private const val KEY_OPACITY = "touch.opacity"
    private const val KEY_FACE_MULTI = "touch.faceMulti"
    private const val KEY_TOUCH_GLIDING = "touch.gliding"
    private const val KEY_TOUCH_HAPTICS = "touch.haptics"
    private const val KEY_MULTI_RADIUS = "touch.multiRadius"
    private const val KEY_DPAD_SPACING = "touch.dpadSpacing"
    private const val KEY_FLOATING_STICK = "touch.floatingStick"
    private const val KEY_VIS_MODE = "touch.visibilityMode"
    private const val KEY_DEFAULTS_MIGRATED_247 = "touch.defaults.migrated.247"
    private const val KEY_ACTIVE_GAME_PREFIX = "touch.active.game."
    private const val KEY_LAYOUT_GAME_PREFIX = "touch.layout.game."
    private const val PROFILE_FILE_SUFFIX = ".touch.json"

    val visible = mutableStateOf(true)

    val editMode = mutableStateOf(false)

    fun exitEditMode() {
        editMode.value = false
        if (MainActivityRuntime.eState.value == EmuState.PAUSED) MainActivityRuntime.resume()
    }

    val selectedButton = mutableStateOf<TouchButtonId?>(null)

    val profileDialogOpen = mutableStateOf(false)

    val profiles = mutableStateListOf<TouchProfile>()

    val activeProfileName = mutableStateOf("Default")

    val activeLayout = mutableStateOf(TouchLayout.default())

    val opacity = mutableFloatStateOf(0.55f)

    val faceMultiTouch = mutableStateOf(true)

    val touchGliding = mutableStateOf(false)

    val touchHaptics = mutableStateOf(true)

    val multiTouchRadius = mutableFloatStateOf(0.62f)

    val dpadSpacing = mutableFloatStateOf(0.0f)

    val floatingStick = mutableStateOf(false)

    val pressureModifierHeld = mutableStateOf(false)

    const val PRESSURE_HALF_RANGE = 16383

    private val PRESSURE_KEYCODES = setOf(
        19, 20, 21, 22,
        96, 97, 99, 100,
        102, 103, 104, 105,
    )

    fun pressureRangeFor(keycode: Int): Int =
        if (pressureModifierHeld.value && keycode in PRESSURE_KEYCODES) PRESSURE_HALF_RANGE else 0

    val visibilityMode = mutableIntStateOf(11)

    val interactionTick = mutableIntStateOf(0)

    private const val KEY_MACRO_PREFIX = "touch.macro."

    val macroBindTick = mutableIntStateOf(0)

    data class MacroTarget(val code: Int, val label: String)

    const val MACRO_CODE_PRESSURE = -2

    val macroAssignableTargets: List<MacroTarget> = listOf(
        MacroTarget(KeyEvent.KEYCODE_BUTTON_Y, "Triangle"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_B, "Circle"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_A, "Cross"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_X, "Square"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_L1, "L1"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_R1, "R1"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_L2, "L2"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_R2, "R2"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_START, "Start"),
        MacroTarget(KeyEvent.KEYCODE_BUTTON_SELECT, "Select"),
        MacroTarget(KeyEvent.KEYCODE_DPAD_UP, "Up"),
        MacroTarget(KeyEvent.KEYCODE_DPAD_RIGHT, "Right"),
        MacroTarget(KeyEvent.KEYCODE_DPAD_DOWN, "Down"),
        MacroTarget(KeyEvent.KEYCODE_DPAD_LEFT, "Left"),
        MacroTarget(200, "Analog"),
        MacroTarget(MACRO_CODE_PRESSURE, "Pressure"),
        MacroTarget(110, "L-Stick Up"),
        MacroTarget(111, "L-Stick Right"),
        MacroTarget(112, "L-Stick Down"),
        MacroTarget(113, "L-Stick Left"),
        MacroTarget(120, "R-Stick Up"),
        MacroTarget(121, "R-Stick Right"),
        MacroTarget(122, "R-Stick Down"),
        MacroTarget(123, "R-Stick Left"),
    )

    fun macroTargetFor(code: Int): MacroTarget? = macroAssignableTargets.firstOrNull { it.code == code }

    fun macroCodes(id: TouchButtonId): List<Int> {
        val raw = MainActivityRuntime.prefs.getString(KEY_MACRO_PREFIX + id.name, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        val codes = raw.split(",").mapNotNull { token ->
            token.toIntOrNull()
                ?: runCatching { TouchButtonId.valueOf(token).keycode }.getOrNull()
        }.toSet()
        return macroAssignableTargets.map { it.code }.filter { it in codes }
    }

    fun setMacroCodes(id: TouchButtonId, codes: List<Int>) {
        val wanted = codes.toSet()
        val csv = macroAssignableTargets.map { it.code }.filter { it in wanted }.joinToString(",")
        MainActivityRuntime.prefs.edit { putString(KEY_MACRO_PREFIX + id.name, csv) }
        macroBindTick.intValue++
    }

    private const val KEY_MACRO_PHYS_PREFIX = "touch.macro.phys."

    fun macroPhysicalCode(id: TouchButtonId): Int =
        MainActivityRuntime.prefs.getInt(KEY_MACRO_PHYS_PREFIX + id.name, KeyEvent.KEYCODE_UNKNOWN)

    fun setMacroPhysicalCode(id: TouchButtonId, keycode: Int) {
        MainActivityRuntime.prefs.edit { putInt(KEY_MACRO_PHYS_PREFIX + id.name, keycode)}
        macroBindTick.intValue++
    }

    fun clearMacroPhysicalCode(id: TouchButtonId) = setMacroPhysicalCode(id, KeyEvent.KEYCODE_UNKNOWN)

    private const val KEY_MACRO_FREQ_PREFIX = "touch.macro.freq."

    const val MACRO_FREQ_MAX = 60

    fun macroFrequency(id: TouchButtonId): Int =
        MainActivityRuntime.prefs.getInt(KEY_MACRO_FREQ_PREFIX + id.name, 0).coerceIn(0, MACRO_FREQ_MAX)

    fun setMacroFrequency(id: TouchButtonId, frames: Int) {
        MainActivityRuntime.prefs.edit {
            putInt(KEY_MACRO_FREQ_PREFIX + id.name, frames.coerceIn(0, MACRO_FREQ_MAX))
        }
        macroBindTick.intValue++
    }

    private const val MACRO_FRAME_MS = 1000.0 / 60.0

    private val macroHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val macroRunnables = HashMap<String, Runnable>()

    fun fireMacro(id: TouchButtonId, key: String, down: Boolean, emit: (Int, Boolean) -> Unit) {
        val codes = macroCodes(id)
        if (codes.isEmpty()) return
        val wantsPressure = MACRO_CODE_PRESSURE in codes
        val buttons = codes.filter { it != MACRO_CODE_PRESSURE }
        val runKey = "${id.name}:$key"
        if (!down) {
            macroRunnables.remove(runKey)?.let { macroHandler.removeCallbacks(it) }
            buttons.forEach { emit(it, false) }
            if (wantsPressure) pressureModifierHeld.value = false
            return
        }
        if (wantsPressure) pressureModifierHeld.value = true
        if (buttons.isEmpty()) return
        val frames = macroFrequency(id)
        if (frames <= 0) {
            buttons.forEach { emit(it, true) }
            return
        }
        if (macroRunnables.containsKey(runKey)) return
        val periodMs = (frames * MACRO_FRAME_MS).toLong().coerceAtLeast(16L)
        var pressed = false
        val runnable = object : Runnable {
            override fun run() {
                pressed = !pressed
                buttons.forEach { emit(it, pressed) }
                macroHandler.postDelayed(this, periodMs)
            }
        }
        macroRunnables[runKey] = runnable
        macroHandler.post(runnable)
    }

    fun macroForPhysicalCode(keycode: Int): TouchButtonId? {
        if (keycode == KeyEvent.KEYCODE_UNKNOWN) return null
        for (id in listOf(TouchButtonId.MACRO1, TouchButtonId.MACRO2, TouchButtonId.MACRO3, TouchButtonId.MACRO4)) {
            if (macroPhysicalCode(id) == keycode && macroCodes(id).isNotEmpty()) return id
        }
        return null
    }

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        load()
    }

    private fun load() {
        val raw = MainActivityRuntime.prefs.getString(KEY_PROFILES, null)
        val list = mutableListOf<TouchProfile>()
        if (raw != null) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(TouchProfile.fromJson(obj))
                }
            }
        }
        importFolderProfilesInto(list)
        if (list.isEmpty()) {
            list.add(TouchProfile("Default", TouchLayout.default()))
        }
        profiles.clear()
        profiles.addAll(list)

        val active = MainActivityRuntime.prefs.getString(KEY_ACTIVE, list.first().name) ?: list.first().name
        activeProfileName.value = active
        val match = list.firstOrNull { it.name == active } ?: list.first()
        activeLayout.value = match.layout.copy()
        opacity.floatValue = MainActivityRuntime.prefs.getFloat(KEY_OPACITY, 0.55f).coerceIn(0.20f, 1.0f)
        faceMultiTouch.value = MainActivityRuntime.prefs.getBoolean(KEY_FACE_MULTI, true)
        touchGliding.value = MainActivityRuntime.prefs.getBoolean(KEY_TOUCH_GLIDING, false)
        touchHaptics.value = MainActivityRuntime.prefs.getBoolean(KEY_TOUCH_HAPTICS, true)
        multiTouchRadius.floatValue = MainActivityRuntime.prefs.getFloat(KEY_MULTI_RADIUS, 0.62f).coerceIn(0.50f, 0.95f)
        dpadSpacing.floatValue = MainActivityRuntime.prefs.getFloat(KEY_DPAD_SPACING, 0.0f).coerceIn(0.0f, 0.35f)
        floatingStick.value = MainActivityRuntime.prefs.getBoolean(KEY_FLOATING_STICK, false)
        visibilityMode.intValue = MainActivityRuntime.prefs.getInt(KEY_VIS_MODE, 11).coerceIn(0, 11)
        if (visibilityMode.intValue == 0) visible.value = false

        if (!MainActivityRuntime.prefs.getBoolean(KEY_DEFAULTS_MIGRATED_247, false)) {
            faceMultiTouch.value = true
            fun hidePressure(layout: TouchLayout): TouchLayout = layout.copy(
                buttons = layout.buttons.map {
                    if (it.id.kind == TouchButtonId.Kind.PRESSURE) it.copy(enabled = false) else it
                }
            )
            for (i in profiles.indices) profiles[i] = profiles[i].copy(layout = hidePressure(profiles[i].layout))
            activeLayout.value = hidePressure(activeLayout.value)
            MainActivityRuntime.prefs.edit { putBoolean(KEY_DEFAULTS_MIGRATED_247, true) }
            persist()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (p in profiles) arr.put(p.toJson())
        MainActivityRuntime.prefs.edit {
            putString(KEY_PROFILES, arr.toString())
                .putString(KEY_ACTIVE, activeProfileName.value)
                .putFloat(KEY_OPACITY, opacity.floatValue)
                .putBoolean(KEY_FACE_MULTI, faceMultiTouch.value)
                .putBoolean(KEY_TOUCH_GLIDING, touchGliding.value)
                .putBoolean(KEY_TOUCH_HAPTICS, touchHaptics.value)
                .putFloat(KEY_MULTI_RADIUS, multiTouchRadius.floatValue)
                .putFloat(KEY_DPAD_SPACING, dpadSpacing.floatValue)
                .putBoolean(KEY_FLOATING_STICK, floatingStick.value)
                .putInt(KEY_VIS_MODE, visibilityMode.intValue)
        }
        syncFolder()
    }

    fun setVisibilityMode(mode: Int) {
        visibilityMode.intValue = mode.coerceIn(0, 11)
        visible.value = visibilityMode.intValue != 0
        interactionTick.intValue++
        persist()
    }

    fun noteTouchInteraction() {
        if (visibilityMode.intValue == 0) return
        if (!visible.value) visible.value = true
        interactionTick.intValue++
    }

    fun saveLiveLayoutToActive() {
        if (gameIsRunning()) {
            val serial = runningSerial()
            if (serial != null) {
                MainActivityRuntime.prefs.edit {
                    putString(
                        KEY_LAYOUT_GAME_PREFIX + serial,
                        activeLayout.value.toJson().toString(),
                    )
                }
            } else {
                val crc = runCatching { NativeApp.getGameCRC() }.getOrNull()
                    ?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != "00000000" }
                if (crc != null) {
                    MainActivityRuntime.prefs.edit {
                        putString(
                            KEY_LAYOUT_GAME_PREFIX + "crc." + crc,
                            activeLayout.value.toJson().toString(),
                        )
                    }
                } else {
                    println(
                        "@@ARMSX2_TOUCH@@ refusing to save in-game layout to global " +
                            "Default: no serial/CRC resolved for running game"
                    )
                }
            }
        } else {
            val idx = profiles.indexOfFirst { it.name == activeProfileName.value }
            if (idx >= 0) {
                profiles[idx] = profiles[idx].copy(layout = activeLayout.value.copy())
                persist()
            }
        }
        selectedButton.value = null
    }

    fun saveAsNewProfile(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val newProf = TouchProfile(trimmed, activeLayout.value.copy())
        val existing = profiles.indexOfFirst { it.name == trimmed }
        if (existing >= 0) profiles[existing] = newProf
        else profiles.add(newProf)
        activeProfileName.value = trimmed
        val serial = if (gameIsRunning()) runningSerial() else null
        if (serial != null)
            MainActivityRuntime.prefs.edit { putString(KEY_ACTIVE_GAME_PREFIX + serial, trimmed) }
        persist()
    }

    fun switchProfile(name: String) {
        val match = profiles.firstOrNull { it.name == name } ?: return
        activeProfileName.value = name
        activeLayout.value = match.layout.copy()
        val serial = if (gameIsRunning()) runningSerial() else null
        if (serial != null)
            MainActivityRuntime.prefs.edit { putString(KEY_ACTIVE_GAME_PREFIX + serial, name) }
        persist()
    }

    fun deleteProfile(name: String) {
        if (profiles.size <= 1) return
        val idx = profiles.indexOfFirst { it.name == name }
        if (idx < 0) return
        profiles.removeAt(idx)
        if (activeProfileName.value == name) {
            val fallback = profiles.first()
            activeProfileName.value = fallback.name
            activeLayout.value = fallback.layout.copy()
        }
        clearGameOverridesFor(name)
        persist()
    }

    fun resetActiveToDefault() {
        activeLayout.value = TouchLayout.default()
    }

    private fun gameIsRunning(): Boolean =
        MainActivityRuntime.eState.value == EmuState.RUNNING || MainActivityRuntime.eState.value == EmuState.PAUSED

    private fun coreSerial(): String? =
        runCatching { NativeApp.getGameSerial() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun runningSerial(): String? =
        MainActivityRuntime.currentGame.value?.serial?.takeIf { it.isNotEmpty() }
            ?: coreSerial()
            ?: InGameOverlay.currentSerial.value?.takeIf { it.isNotEmpty() }

    fun applyForSerial(serial: String?) {
        val effSerial = serial?.takeIf { it.isNotEmpty() } ?: coreSerial()
        if (effSerial == null) {
            val crc = runCatching { NativeApp.getGameCRC() }.getOrNull()
                ?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != "00000000" }
            if (crc != null) {
                val rawCrc = MainActivityRuntime.prefs.getString(KEY_LAYOUT_GAME_PREFIX + "crc." + crc, null)
                if (rawCrc != null) {
                    runCatching { TouchLayout.fromJson(JSONObject(rawCrc)) }.getOrNull()?.let {
                        activeProfileName.value = "Default"
                        activeLayout.value = it
                        return
                    }
                }
            }
            return
        }
        val rawLayout = MainActivityRuntime.prefs.getString(KEY_LAYOUT_GAME_PREFIX + effSerial, null)
        if (rawLayout != null) {
            runCatching { TouchLayout.fromJson(JSONObject(rawLayout)) }.getOrNull()?.let {
                activeProfileName.value = "Default"
                activeLayout.value = it
                return
            }
        }
        val name = MainActivityRuntime.prefs.getString(KEY_ACTIVE_GAME_PREFIX + effSerial, null)
        if (name != null) {
            val match = profiles.firstOrNull { it.name == name }
            if (match != null) {
                activeProfileName.value = name
                activeLayout.value = match.layout.copy()
                return
            }
        }
        val def = profiles.firstOrNull { it.name == "Default" } ?: profiles.firstOrNull()
        if (def != null) {
            activeProfileName.value = def.name
            activeLayout.value = def.layout.copy()
        } else {
            activeProfileName.value = "Default"
            activeLayout.value = TouchLayout.default()
        }
    }

    private fun clearGameOverridesFor(profileName: String) {
        MainActivityRuntime.prefs.edit {
            for ((k, v) in MainActivityRuntime.prefs.all) {
                if (k.startsWith(KEY_ACTIVE_GAME_PREFIX) && v == profileName) remove(k)
            }
        }
    }

    fun clearGameLayout(serial: String?) {
        if (serial == null) return
        MainActivityRuntime.prefs.edit {remove(KEY_LAYOUT_GAME_PREFIX + serial) }
    }

    fun clearGameLayoutIfRunning() {
        if (gameIsRunning()) clearGameLayout(runningSerial())
    }

    private fun profilesDir(): File? = MainActivityRuntime.inputProfilesDir()

    private fun fileNameFor(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _.-]"), "_") + PROFILE_FILE_SUFFIX

    private fun syncFolder() {
        val dir = profilesDir() ?: return
        runCatching {
            val want = HashMap<String, TouchProfile>()
            for (p in profiles) want[fileNameFor(p.name)] = p
            for ((fn, p) in want) File(dir, fn).writeText(p.toJson().toString())
            dir.listFiles { f -> f.name.endsWith(PROFILE_FILE_SUFFIX) }?.forEach { f ->
                if (f.name !in want) runCatching { f.delete() }
            }
        }
    }

    private fun importFolderProfilesInto(list: MutableList<TouchProfile>) {
        val dir = profilesDir() ?: return
        runCatching {
            val have = list.map { it.name }.toHashSet()
            dir.listFiles { f -> f.name.endsWith(PROFILE_FILE_SUFFIX) }
                ?.sortedBy { it.name }
                ?.forEach { f ->
                    runCatching {
                        val prof = TouchProfile.fromJson(JSONObject(f.readText()))
                        if (prof.name !in have) { list.add(prof); have.add(prof.name) }
                    }
                }
        }
    }

    fun discardEdits() {
        val match = profiles.firstOrNull { it.name == activeProfileName.value }
        if (match != null) activeLayout.value = match.layout.copy()
        selectedButton.value = null
    }

    fun setOpacity(o: Float) {
        opacity.floatValue = o.coerceIn(0.20f, 1.0f)
        persist()
    }

    fun setFaceMultiTouch(enabled: Boolean) {
        faceMultiTouch.value = enabled
        persist()
    }

    fun setTouchGliding(enabled: Boolean) {
        touchGliding.value = enabled
        persist()
    }

    fun setTouchHaptics(enabled: Boolean) {
        touchHaptics.value = enabled
        persist()
    }

    fun setMultiTouchRadius(v: Float) {
        multiTouchRadius.floatValue = v.coerceIn(0.50f, 0.95f)
        persist()
    }

    fun setDpadSpacing(v: Float) {
        dpadSpacing.floatValue = v.coerceIn(0.0f, 0.35f)
        persist()
    }

    fun setFloatingStick(enabled: Boolean) {
        floatingStick.value = enabled
        persist()
    }

    fun updateButton(id: TouchButtonId, transform: (TouchButtonCfg) -> TouchButtonCfg) {
        val current = activeLayout.value
        val newButtons = current.buttons.map { if (it.id == id) transform(it) else it }
        activeLayout.value = current.copy(buttons = newButtons)
    }

    fun onControllerInputDetected() {
        if (visibilityMode.intValue == 11 && visible.value) visible.value = false
    }

    fun onSurfaceTouched() {
        noteTouchInteraction()
    }
}

enum class TouchButtonId(val label: String, val keycode: Int, val kind: Kind) {
    CROSS("✕", KeyEvent.KEYCODE_BUTTON_A, Kind.FACE),
    CIRCLE("○", KeyEvent.KEYCODE_BUTTON_B, Kind.FACE),
    SQUARE("□", KeyEvent.KEYCODE_BUTTON_X, Kind.FACE),
    TRIANGLE("△", KeyEvent.KEYCODE_BUTTON_Y, Kind.FACE),
    L1("L1", KeyEvent.KEYCODE_BUTTON_L1, Kind.SHOULDER),
    R1("R1", KeyEvent.KEYCODE_BUTTON_R1, Kind.SHOULDER),
    L2("L2", KeyEvent.KEYCODE_BUTTON_L2, Kind.SHOULDER),
    R2("R2", KeyEvent.KEYCODE_BUTTON_R2, Kind.SHOULDER),
    START("Start", KeyEvent.KEYCODE_BUTTON_START, Kind.MENU),
    SELECT("Select", KeyEvent.KEYCODE_BUTTON_SELECT, Kind.MENU),
    L3("L3", KeyEvent.KEYCODE_BUTTON_THUMBL, Kind.MENU),
    R3("R3", KeyEvent.KEYCODE_BUTTON_THUMBR, Kind.MENU),
    DPAD("D-Pad", KeyEvent.KEYCODE_DPAD_UP, Kind.DPAD),
    L_STICK("L-Stick", 110, Kind.STICK),
    R_STICK("R-Stick", 120, Kind.STICK),
    PAUSE("Pause", 0, Kind.PAUSE),

    FAST_FORWARD("▶▶", 0, Kind.FASTFORWARD),

    SAVE_STATE("SAVE", 0, Kind.STATEACTION),
    LOAD_STATE("LOAD", 0, Kind.STATEACTION),

    MACRO1("M1", 0, Kind.MACRO),
    MACRO2("M2", 0, Kind.MACRO),
    MACRO3("M3", 0, Kind.MACRO),
    MACRO4("M4", 0, Kind.MACRO),

    PRESSURE("P½", 0, Kind.PRESSURE);

    enum class Kind { FACE, SHOULDER, MENU, DPAD, STICK, PAUSE, PRESSURE, FASTFORWARD, MACRO, STATEACTION }
}

data class TouchButtonCfg(
    val id: TouchButtonId,
    val xFrac: Float,
    val yFrac: Float,
    val sizeDp: Float,
    val enabled: Boolean = true,
    val tapToHold: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id.name)
        put("x", xFrac.toDouble())
        put("y", yFrac.toDouble())
        put("size", sizeDp.toDouble())
        put("on", enabled)
        put("hold", tapToHold)
    }

    companion object {
        fun fromJson(json: JSONObject): TouchButtonCfg? {
            val idName = json.optString("id", "") ?: ""
            val id = runCatching { TouchButtonId.valueOf(idName) }.getOrNull() ?: return null
            return TouchButtonCfg(
                id = id,
                xFrac = json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                yFrac = json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                sizeDp = json.optDouble("size", 64.0).toFloat().coerceIn(28f, 220f),
                enabled = json.optBoolean("on", true),
                tapToHold = json.optBoolean("hold", false),
            )
        }
    }
}

data class TouchLayout(val buttons: List<TouchButtonCfg>) {
    fun toJson(): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        for (b in buttons) arr.put(b.toJson())
        put("buttons", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): TouchLayout {
            val arr = json.optJSONArray("buttons") ?: return default()
            val list = mutableListOf<TouchButtonCfg>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                TouchButtonCfg.fromJson(obj)?.let { list.add(it) }
            }
            val have = list.map { it.id }.toSet()
            val merged = list + default().buttons.filter { it.id !in have }
            return TouchLayout(merged)
        }

        fun default(): TouchLayout = TouchLayout(
            buttons = listOf(
                TouchButtonCfg(TouchButtonId.DPAD,     0.10f, 0.55f, 150f),
                TouchButtonCfg(TouchButtonId.TRIANGLE, 0.86f, 0.45f, 58f),
                TouchButtonCfg(TouchButtonId.SQUARE,   0.80f, 0.55f, 58f),
                TouchButtonCfg(TouchButtonId.CIRCLE,   0.92f, 0.55f, 58f),
                TouchButtonCfg(TouchButtonId.CROSS,    0.86f, 0.65f, 58f),
                TouchButtonCfg(TouchButtonId.L2,       0.08f, 0.10f, 56f),
                TouchButtonCfg(TouchButtonId.L1,       0.08f, 0.23f, 56f),
                TouchButtonCfg(TouchButtonId.R2,       0.92f, 0.10f, 56f),
                TouchButtonCfg(TouchButtonId.R1,       0.92f, 0.23f, 56f),
                TouchButtonCfg(TouchButtonId.SELECT,   0.45f, 0.92f, 48f),
                TouchButtonCfg(TouchButtonId.START,    0.55f, 0.92f, 48f),
                TouchButtonCfg(TouchButtonId.FAST_FORWARD, 0.30f, 0.40f, 44f, enabled = false),
                TouchButtonCfg(TouchButtonId.MACRO1, 0.40f, 0.40f, 42f, enabled = false),
                TouchButtonCfg(TouchButtonId.MACRO2, 0.48f, 0.40f, 42f, enabled = false),
                TouchButtonCfg(TouchButtonId.MACRO3, 0.56f, 0.40f, 42f, enabled = false),
                TouchButtonCfg(TouchButtonId.MACRO4, 0.64f, 0.40f, 42f, enabled = false),
                TouchButtonCfg(TouchButtonId.SAVE_STATE, 0.30f, 0.54f, 44f, enabled = false),
                TouchButtonCfg(TouchButtonId.LOAD_STATE, 0.38f, 0.54f, 44f, enabled = false),
                TouchButtonCfg(TouchButtonId.L_STICK,  0.28f, 0.80f, 130f),
                TouchButtonCfg(TouchButtonId.R_STICK,  0.72f, 0.80f, 130f),
                TouchButtonCfg(TouchButtonId.L3,       0.18f, 0.93f, 42f),
                TouchButtonCfg(TouchButtonId.R3,       0.82f, 0.93f, 42f),
                TouchButtonCfg(TouchButtonId.PAUSE,    0.48f, 0.50f, 120f),
                TouchButtonCfg(TouchButtonId.PRESSURE, 0.10f, 0.78f, 44f, enabled = false),
            ),
        )
    }
}

data class TouchProfile(val name: String, val layout: TouchLayout) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("layout", layout.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): TouchProfile {
            return TouchProfile(
                name = json.optString("name", "Profile"),
                layout = json.optJSONObject("layout")?.let { TouchLayout.fromJson(it) }
                    ?: TouchLayout.default(),
            )
        }
    }
}
