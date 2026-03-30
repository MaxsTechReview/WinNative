package com.winlator.cmod.steam.ui

import android.os.FileObserver
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.function.Consumer

/**
 * Watches the GSE Saves achievements.json for newly earned achievements
 * and fires a callback with the achievement display info.
 */
class AchievementWatcher(
    private val gseSaveDir: File,
    private val steamSettingsDir: File?,
    private val language: String,
    private val onAchievementUnlocked: Consumer<AchievementNotification>
) {
    private var observer: FileObserver? = null
    private val previouslyEarned = mutableSetOf<String>()

    fun start() {
        val achFile = File(gseSaveDir, "achievements.json")

        // Seed with already-earned achievements so we only fire on NEW ones
        loadEarned(achFile)

        // Watch the GSE save directory for writes to achievements.json
        observer = object : FileObserver(gseSaveDir.absolutePath, CLOSE_WRITE or MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "achievements.json") {
                    checkForNew(achFile)
                }
            }
        }
        observer?.startWatching()
        Timber.d("AchievementWatcher started for ${gseSaveDir.absolutePath}")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        Timber.d("AchievementWatcher stopped")
    }

    private fun loadEarned(achFile: File) {
        if (!achFile.exists()) return
        try {
            val json = JSONObject(achFile.readText(Charsets.UTF_8))
            for (name in json.keys()) {
                val entry = json.optJSONObject(name) ?: continue
                if (entry.optBoolean("earned", false)) {
                    previouslyEarned.add(name)
                }
            }
            Timber.d("AchievementWatcher seeded with ${previouslyEarned.size} earned achievements")
        } catch (e: Exception) {
            Timber.w(e, "AchievementWatcher failed to seed earned achievements")
        }
    }

    private fun checkForNew(achFile: File) {
        if (!achFile.exists()) return
        try {
            val json = JSONObject(achFile.readText(Charsets.UTF_8))
            for (name in json.keys()) {
                val entry = json.optJSONObject(name) ?: continue
                if (entry.optBoolean("earned", false) && name !in previouslyEarned) {
                    previouslyEarned.add(name)
                    val displayName = resolveDisplayName(name)
                    val iconPath = resolveIconPath(name)
                    Timber.d("Achievement unlocked: $name ($displayName)")
                    onAchievementUnlocked.accept(
                        AchievementNotification(
                            name = name,
                            displayName = displayName,
                            iconPath = iconPath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "AchievementWatcher failed to check for new achievements")
        }
    }

    /**
     * Looks up the display name from steam_settings/achievements.json
     * which contains the full achievement metadata with localized names.
     */
    private fun resolveDisplayName(apiName: String): String {
        val settingsAchFile = steamSettingsDir?.let { File(it, "achievements.json") }
        if (settingsAchFile == null || !settingsAchFile.exists()) return apiName
        try {
            val arr = JSONArray(settingsAchFile.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val ach = arr.optJSONObject(i) ?: continue
                if (ach.optString("name") == apiName) {
                    val displayNameObj = ach.optJSONObject("displayName")
                    if (displayNameObj != null) {
                        return displayNameObj.optString(language, "")
                            .ifEmpty { displayNameObj.optString("english", "") }
                            .ifEmpty { apiName }
                    }
                    return apiName
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve display name for $apiName")
        }
        return apiName
    }

    /**
     * Resolves the icon file path from steam_settings/achievements.json.
     * Icons are stored as "img/<hash>" relative to steam_settings.
     */
    private fun resolveIconPath(apiName: String): String? {
        val settingsAchFile = steamSettingsDir?.let { File(it, "achievements.json") }
        if (settingsAchFile == null || !settingsAchFile.exists()) return null
        try {
            val arr = JSONArray(settingsAchFile.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val ach = arr.optJSONObject(i) ?: continue
                if (ach.optString("name") == apiName) {
                    val iconRelative = ach.optString("icon", "")
                    if (iconRelative.isNotEmpty() && steamSettingsDir != null) {
                        val iconFile = File(steamSettingsDir, iconRelative)
                        if (iconFile.exists()) return iconFile.absolutePath
                    }
                    return null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve icon for $apiName")
        }
        return null
    }
}
