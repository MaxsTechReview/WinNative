package com.winlator.cmod.feature.community

import com.winlator.cmod.runtime.container.Shortcut
import org.json.JSONObject

object ConfigApplier {

    fun apply(shortcut: Shortcut, settings: JSONObject) {
        val steam = CommunitySettings.isSteam(shortcut)
        for (entry in CommunitySettings.ENTRIES) {
            if (entry.steamOnly && !steam) continue
            if (!settings.has(entry.key)) {
                shortcut.putExtra(entry.key, null)
                continue
            }
            val value = settings.optString(entry.key, "")
            if (!CommunitySettings.accepts(entry, value)) continue
            shortcut.putExtra(entry.key, value)
        }
        shortcut.putExtra("use_container_defaults", "0")
        shortcut.saveData()
    }
}
