package com.winlator.cmod.feature.configs.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One row from the `configs_with_votes` view. Mirrors the columns 1:1 plus the
 * computed `voteCount`. `configJson` is left as a JSONObject so the consumer
 * (ConfigSerializer.applyToShortcut) can read it directly.
 */
data class ConfigRow(
    val id: String,
    val userId: String,
    val gameSource: String,
    val gameId: String,
    val gameName: String,
    /**
     * Public uploader identity (GPG display name or "Anonymous"). Auto-resolved
     * at upload time — never user-typed. Older rows uploaded before the rename
     * may carry the legacy `custom_name` value, so [displayName] falls back to
     * `customName` if `uploaderName` is missing.
     */
    val uploaderName: String?,
    val customName: String?,
    /**
     * Stable per-install identifier of the device that uploaded the row.
     * Combined with [uploaderName] to gate the delete button on the client.
     */
    val deviceId: String?,
    val deviceModel: String?,
    val manufacturer: String?,
    val socModel: String?,
    val gpuRenderer: String?,
    val androidRelease: String?,
    val androidApi: Int?,
    val appVersion: String?,
    val configJson: JSONObject,
    val perfSummary: JSONObject?,
    val notes: String?,
    val includesSteamSubtab: Boolean,
    val schemaVersion: Int,
    val createdAt: String,
    val updatedAt: String,
    val voteCount: Int,
) {
    /**
     * Resolution string (e.g. "1280x720") pulled from the embedded container config.
     * Used as part of the default display name when the uploader didn't pick one.
     */
    val resolutionLabel: String?
        get() = configJson.optJSONObject("container")?.optString("screenSize")?.takeIf { it.isNotBlank() }

    /**
     * What to show as the config name on the leaderboard row. Prefers the
     * uploader's public display name (GPG name or "Anonymous"). Falls back to
     * the legacy `custom_name` column, then to a "resolution / device" label
     * so older rows without either field are still distinguishable.
     */
    val displayName: String
        get() = uploaderName?.takeIf { it.isNotBlank() }
            ?: customName?.takeIf { it.isNotBlank() }
            ?: run {
                val res = resolutionLabel ?: "—"
                val dev = deviceModel?.takeIf { it.isNotBlank() }
                    ?: manufacturer?.takeIf { it.isNotBlank() }
                    ?: "Unknown"
                "$res / $dev"
            }

    companion object {
        fun parseArray(body: String): List<ConfigRow> {
            val arr = JSONArray(body)
            val out = ArrayList<ConfigRow>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out += parse(obj)
            }
            return out
        }

        fun parse(obj: JSONObject): ConfigRow = ConfigRow(
            id = obj.getString("id"),
            userId = obj.getString("user_id"),
            gameSource = obj.getString("game_source"),
            gameId = obj.getString("game_id"),
            gameName = obj.getString("game_name"),
            uploaderName = obj.optStringOrNull("uploader_name"),
            customName = obj.optStringOrNull("custom_name"),
            deviceId = obj.optStringOrNull("device_id"),
            deviceModel = obj.optStringOrNull("device_model"),
            manufacturer = obj.optStringOrNull("manufacturer"),
            socModel = obj.optStringOrNull("soc_model"),
            gpuRenderer = obj.optStringOrNull("gpu_renderer"),
            androidRelease = obj.optStringOrNull("android_release"),
            androidApi = if (obj.isNull("android_api")) null else obj.optInt("android_api"),
            appVersion = obj.optStringOrNull("app_version"),
            configJson = obj.optJSONObject("config_json") ?: JSONObject(),
            perfSummary = obj.optJSONObject("perf_summary"),
            notes = obj.optStringOrNull("notes"),
            includesSteamSubtab = obj.optBoolean("includes_steam_subtab", false),
            schemaVersion = obj.optInt("schema_version", 1),
            createdAt = obj.optString("created_at", ""),
            updatedAt = obj.optString("updated_at", ""),
            voteCount = obj.optInt("vote_count", 0),
        )

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val v = optString(key, "")
            return v.ifEmpty { null }
        }
    }
}
