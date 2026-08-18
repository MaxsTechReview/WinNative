package com.winlator.cmod.feature.retro

import android.content.Context
import androidx.preference.PreferenceManager
import com.winlator.cmod.runtime.container.Shortcut
import org.json.JSONArray
import org.json.JSONObject

object Gen1EngineSettings {
    private const val CACHE_KEY = "gen1_engine_rows"

    data class CachedRow(
        val id: String,
        val label: String,
        val values: List<String>,
        val pane: String,
    )

    fun cache(
        context: Context,
        rows: List<Gen1EngineBridge.Row>,
    ) {
        val usable = rows.filter { it.values.size > 1 && it.id != Gen1StadiumRom.ROW_ID }
        if (usable.isEmpty()) return
        val array = JSONArray()
        usable.forEach { row ->
            array.put(
                JSONObject()
                    .put("id", row.id)
                    .put("label", row.label)
                    .put("pane", paneOf(row.id))
                    .put("values", JSONArray(row.values)),
            )
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(CACHE_KEY, array.toString()).apply()
    }

    fun cached(context: Context): List<CachedRow> =
        runCatching {
            val raw =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(CACHE_KEY, null) ?: return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val values = o.optJSONArray("values") ?: return@mapNotNull null
                val list = (0 until values.length()).map { values.optString(it) }
                if (list.size < 2) return@mapNotNull null
                CachedRow(
                    id = o.optString("id"),
                    label = o.optString("label"),
                    values = list,
                    pane = o.optString("pane", PANE_SYSTEM),
                )
            }
        }.getOrDefault(emptyList())

    fun selection(
        shortcut: Shortcut,
        row: CachedRow,
    ): String? =
        shortcut.getExtra(RetroShortcuts.VAR_PREFIX + row.id)
            .takeIf { it.isNotEmpty() && it in row.values }

    fun resolve(
        context: Context,
        shortcut: Shortcut,
    ): HashMap<String, String> {
        val out = HashMap<String, String>()
        cached(context).forEach { row ->
            selection(shortcut, row)?.let { out[row.id] = it }
        }
        return out
    }

    fun applyTo(
        bridge: Gen1EngineBridge,
        wanted: Map<String, String>,
    ) {
        if (wanted.isEmpty()) return
        bridge.state.rows.forEach { row ->
            val target = wanted[row.id] ?: return@forEach
            if (row.values.isEmpty()) return@forEach
            val index = row.values.indexOf(target)
            if (index >= 0 && index != row.selectedIndex) bridge.setRow(row.id, index)
        }
    }

    const val PANE_DISPLAY = "display"
    const val PANE_SOUND = "sound"
    const val PANE_PERFORMANCE = "performance"
    const val PANE_CONTROLS = "controls"
    const val PANE_SYSTEM = "system"

    private val SOUND_ROWS = setOf("musicVol", "sfxVol", "pikaVol", "musicFilter")
    private val DISPLAY_ROWS =
        setOf("colors", "tilt", "gbcfx", "zoom", "voidFill", "videoMode", "animations")
    private val PERFORMANCE_ROWS = setOf("fpsCap", "speed")
    private val CONTROL_ROWS = setOf("controls")

    private fun paneOf(id: String): String =
        when {
            Gen1EngineBridge.isModRow(id) -> PANE_DISPLAY
            id in SOUND_ROWS -> PANE_SOUND
            id in DISPLAY_ROWS -> PANE_DISPLAY
            id in PERFORMANCE_ROWS -> PANE_PERFORMANCE
            id in CONTROL_ROWS -> PANE_CONTROLS
            else -> PANE_SYSTEM
        }
}
