package com.winlator.cmod.feature.community

import com.winlator.cmod.runtime.container.Shortcut
import org.json.JSONObject

object ConfigSerializer {

    fun serialize(shortcut: Shortcut): JSONObject {
        val steam = CommunitySettings.isSteam(shortcut)
        val out = JSONObject()
        for (entry in CommunitySettings.ENTRIES) {
            if (entry.steamOnly && !steam) continue
            val value = CommunitySettings.effective(shortcut, entry)
            if (value.isBlank()) continue
            if (!CommunitySettings.accepts(entry, value)) continue
            out.put(entry.key, value)
        }
        return out
    }

    fun rejectedKeys(shortcut: Shortcut): List<String> {
        val steam = CommunitySettings.isSteam(shortcut)
        return CommunitySettings.ENTRIES.filter { entry ->
            if (entry.steamOnly && !steam) return@filter false
            val value = CommunitySettings.effective(shortcut, entry)
            value.isNotBlank() && !CommunitySettings.accepts(entry, value)
        }.map { it.key }
    }

    fun gameKey(shortcut: Shortcut): String {
        return when (storeOf(shortcut)) {
            "STEAM" -> "steam:" + shortcut.getExtra("app_id", "").ifBlank { slug(shortcut.name) }
            "GOG" -> "gog:" + shortcut.getExtra("gog_id", "").ifBlank { slug(shortcut.name) }
            "EPIC" -> "epic:" + slug(shortcut.name)
            else -> "name:" + slug(shortcut.name)
        }.take(128)
    }

    fun storeOf(shortcut: Shortcut): String {
        val raw = shortcut.getExtra("game_source", "").uppercase()
        val allowed = setOf("STEAM", "EPIC", "GOG", "AMAZON", "UBISOFT", "EA", "BATTLENET")
        return if (raw in allowed) raw else "CUSTOM"
    }

    private fun slug(name: String): String {
        val s = name.lowercase().map { if (it.isLetterOrDigit() || it == '-' || it == '.') it else '-' }
            .joinToString("").trim('-')
        return s.ifBlank { "game" }.take(100)
    }
}
