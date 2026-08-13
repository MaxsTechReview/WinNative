package com.armsx2.ui.achievements

import org.json.JSONObject

data class AchievementItem(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val progress: String,
    val iconUrl: String,
    val subsetId: Int = 0,
)

fun parseAchievementItems(json: String): List<AchievementItem> {
    if (json.isBlank()) return emptyList()
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val array = root.optJSONArray("items") ?: return emptyList()
    return buildList {
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            add(
                AchievementItem(
                    id = item.optInt("id"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    points = item.optInt("points"),
                    unlocked = item.optBoolean("unlocked"),
                    progress = item.optString("measuredProgress"),
                    iconUrl = item.optString("iconUrl", item.optString("badgeUrl")),
                    subsetId = item.optInt("subsetId"),
                ),
            )
        }
    }
}
