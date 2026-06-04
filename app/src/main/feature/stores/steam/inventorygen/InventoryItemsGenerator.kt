package com.winlator.cmod.feature.stores.steam.inventorygen

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Converts a Steam Inventory item-definition archive — the JSON array returned
 * by `IGameInventory/GetItemDefArchive` — into the two files gbe_fork's
 * emulated steamclient consumes:
 *
 *  - `items.json`         — item definitions, a JSON object keyed by
 *                           `itemdefid`. gbe_fork's `GetItemDefinitionProperty`
 *                           reads every property VALUE with `.get<string>()`,
 *                           so numbers and booleans from the archive are
 *                           stringified here.
 *  - `default_items.json` — the emulated account's starting inventory. Written
 *                           as an empty object: a fresh inventory is correct
 *                           for the vast majority of games (their own drop /
 *                           grant logic populates it at runtime), and seeding
 *                           every defined item would break economy/trading UIs.
 */
object InventoryItemsGenerator {
    private const val TAG = "InventoryItemsGen"

    /**
     * Parse [archiveJson] (the raw `GetItemDefArchive` array) and write
     * `items.json` + `default_items.json` into [configDir] (a `steam_settings`
     * directory). Returns the number of item definitions written, 0 for an
     * empty archive, or -1 on a parse failure.
     */
    fun generate(archiveJson: String, configDir: String): Int {
        val trimmed = archiveJson.trim()
        if (trimmed.isEmpty()) {
            Timber.tag(TAG).i("Empty item-def archive — nothing to write")
            return 0
        }
        val archive =
            try {
                JSONArray(trimmed)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Item-def archive is not a JSON array")
                return -1
            }

        val items = JSONObject()
        for (i in 0 until archive.length()) {
            val def = archive.optJSONObject(i) ?: continue
            // itemdefid becomes the object key; it may arrive as a number or
            // a string on the wire.
            val itemDefId = def.opt("itemdefid")?.toString()?.trim().orEmpty()
            if (itemDefId.isEmpty()) continue

            val value = JSONObject()
            for (key in def.keys()) {
                if (key == "itemdefid") continue
                // gbe_fork requires string-valued properties — stringify all.
                value.put(key, def.opt(key)?.toString() ?: "")
            }
            items.put(itemDefId, value)
        }

        val dir = File(configDir)
        dir.mkdirs()
        File(dir, "items.json").writeText(items.toString(2), Charsets.UTF_8)
        // Empty starting inventory — see the class doc.
        File(dir, "default_items.json").writeText("{}", Charsets.UTF_8)

        Timber.tag(TAG).i("Wrote items.json with %d definitions to %s", items.length(), configDir)
        return items.length()
    }
}
