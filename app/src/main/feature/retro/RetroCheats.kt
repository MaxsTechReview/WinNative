package com.winlator.cmod.feature.retro

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

data class RetroCheat(
    val name: String,
    val code: String,
    val enabled: Boolean,
)

object RetroCheats {
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    private fun safeName(gameName: String): String = gameName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun key(gameName: String) = "retro_cheats_${safeName(gameName)}"

    fun load(
        context: Context,
        gameName: String,
    ): List<RetroCheat> {
        val raw = prefs(context).getString(key(gameName), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RetroCheat(
                    name = o.optString("name"),
                    code = o.optString("code"),
                    enabled = o.optBoolean("enabled", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(
        context: Context,
        gameName: String,
        cheats: List<RetroCheat>,
    ) {
        val arr = JSONArray()
        cheats.forEach { c ->
            arr.put(
                JSONObject()
                    .put("name", c.name)
                    .put("code", c.code)
                    .put("enabled", c.enabled),
            )
        }
        prefs(context).edit().putString(key(gameName), arr.toString()).apply()
    }

    fun hasEnabled(
        context: Context,
        gameName: String,
    ): Boolean = load(context, gameName).any { it.enabled }

    fun formatHint(systemId: String?): String =
        when (systemId) {
            RetroSystems.NES.id -> "Game Genie or raw codes (e.g. SXIOPO)"
            RetroSystems.SNES.id -> "Game Genie or Pro Action Replay codes"
            RetroSystems.GAMEBOY.id, RetroSystems.GAMEBOY_COLOR.id -> "Game Genie or GameShark codes"
            RetroSystems.GBA.id -> "GameShark, Action Replay or Code Breaker codes"
            RetroSystems.GENESIS.id, RetroSystems.MASTER_SYSTEM.id, RetroSystems.GAME_GEAR.id -> "Game Genie or raw codes"
            RetroSystems.N64.id -> "GameShark codes (8-digit address + value)"
            RetroSystems.PSX.id -> "GameShark codes (8-digit address + value)"
            else -> "Cheat code"
        }
}
