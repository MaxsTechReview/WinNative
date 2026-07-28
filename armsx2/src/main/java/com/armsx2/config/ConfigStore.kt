package com.armsx2.config

import com.armsx2.runtime.MainActivityRuntime
import org.json.JSONObject
import androidx.core.content.edit
import java.io.File

enum class SettingsScope { Global, Game }

object ConfigStore {
    private const val KEY_GLOBAL = "config.global"
    private const val KEY_BLEND_BASIC_MIGRATED = "config.migrated.blendBasic"
    private const val KEY_RENDERER_MIGRATED = "config.migrated.rendererUpscale"
    private const val KEY_ADRENO_FBFETCH_MIGRATED = "config.migrated.adrenoFbFetchOn"
    private const val KEY_OSD_OFF_MIGRATED = "config.migrated.osdDefaultOff"
    private const val KEY_FOLDER_RECONCILE = "config.migrated.folderReconcile"
    private const val BACKUP_FILENAME = "armsx2-settings.json"
    private fun keyForGame(serial: String) = "config.game.$serial"

    fun loadGlobal(): Settings {
        val raw = MainActivityRuntime.prefs.getString(KEY_GLOBAL, null)
        var parsed = if (raw != null) {
            try { Settings.fromJson(JSONObject(raw)) } catch (_: Exception) { Settings() }
        } else {
            Settings()
        }
        var dirty = false

        if (raw != null && !MainActivityRuntime.prefs.getBoolean(KEY_BLEND_BASIC_MIGRATED, false) &&
            parsed.accurateBlendingUnit == 4) {
            parsed = parsed.copy(accurateBlendingUnit = 1)
            dirty = true
        }
        if (!MainActivityRuntime.prefs.getBoolean(KEY_BLEND_BASIC_MIGRATED, false)) {
            MainActivityRuntime.prefs.edit { putBoolean(KEY_BLEND_BASIC_MIGRATED, true) }
        }

        if (!MainActivityRuntime.prefs.getBoolean(KEY_RENDERER_MIGRATED, false)) {
            MainActivityRuntime.prefs.getString("renderer", null)?.takeIf { it.isNotBlank() }?.let {
                parsed = parsed.copy(renderer = it)
                dirty = true
            }
            legacyUpscalePref()?.let {
                parsed = parsed.copy(upscaleFloat = it)
                dirty = true
            }
            MainActivityRuntime.prefs.edit { putBoolean(KEY_RENDERER_MIGRATED, true) }
        }

        if (raw != null && !MainActivityRuntime.prefs.getBoolean(KEY_ADRENO_FBFETCH_MIGRATED, false) &&
            !parsed.adrenoFbFetch) {
            parsed = parsed.copy(adrenoFbFetch = true)
            dirty = true
        }
        if (!MainActivityRuntime.prefs.getBoolean(KEY_ADRENO_FBFETCH_MIGRATED, false)) {
            MainActivityRuntime.prefs.edit { putBoolean(KEY_ADRENO_FBFETCH_MIGRATED, true) }
        }

        if (raw != null && !MainActivityRuntime.prefs.getBoolean(KEY_OSD_OFF_MIGRATED, false) &&
            parsed.osdShowFps && parsed.osdShowVps && parsed.osdShowSpeed &&
            parsed.osdShowCpu && parsed.osdShowGpu && parsed.osdShowResolution &&
            parsed.osdShowGsStats && parsed.osdShowFrameTimes &&
            parsed.osdShowHardwareInfo && parsed.osdShowVersion) {
            parsed = parsed.copy(
                osdShowFps = false, osdShowVps = false, osdShowSpeed = false,
                osdShowCpu = false, osdShowGpu = false, osdShowResolution = false,
                osdShowGsStats = false, osdShowFrameTimes = false,
                osdShowHardwareInfo = false, osdShowVersion = false,
            )
            dirty = true
        }
        if (!MainActivityRuntime.prefs.getBoolean(KEY_OSD_OFF_MIGRATED, false)) {
            MainActivityRuntime.prefs.edit { putBoolean(KEY_OSD_OFF_MIGRATED, true) }
        }

        if (dirty) saveGlobal(parsed)
        return parsed
    }

    private fun legacyUpscalePref(): Float? {
        val all = MainActivityRuntime.prefs.all
        fun coerce(raw: Any?): Float? = when (raw) {
            is Float -> raw
            is Double -> raw.toFloat()
            is Int -> raw.toFloat()
            is Long -> raw.toFloat()
            is String -> raw.toFloatOrNull()
            else -> null
        }?.coerceIn(0.25f, 8.0f)
        return coerce(all["upscaleFloat"]) ?: coerce(all["upscale"])
    }

    fun saveGlobal(s: Settings) {
        MainActivityRuntime.prefs.edit { putString(KEY_GLOBAL, s.toJson().toString()) }
        writeBackupMirror()
    }

    fun loadOverrides(serial: String): JSONObject? {
        val raw = MainActivityRuntime.prefs.getString(keyForGame(serial), null) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun saveOverrides(serial: String, overrides: JSONObject) {
        MainActivityRuntime.prefs.edit { putString(keyForGame(serial), overrides.toString()) }
        writeBackupMirror()
    }

    fun clearOverrides(serial: String) {
        MainActivityRuntime.prefs.edit { remove(keyForGame(serial)) }
        writeBackupMirror()
    }

    fun resolveForGame(serial: String?): Settings {
        val global = loadGlobal()
        if (serial == null) return global
        val overrides = loadOverrides(serial) ?: return global
        return Settings.merge(global, overrides)
    }

    fun save(scope: SettingsScope, serial: String?, updated: Settings, previous: Settings? = null) {
        if (scope == SettingsScope.Game && serial != null) {
            val global = loadGlobal()
            val overrides = Settings.diff(global, updated)
            val full = updated.toJson()
            val pinned = LinkedHashSet<String>()
            loadOverrides(serial)?.keys()?.forEach { pinned.add(it) }
            previous?.let { Settings.diff(it, updated).keys().forEach { k -> pinned.add(k) } }
            pinned.forEach { key ->
                if (!overrides.has(key) && full.has(key)) overrides.put(key, full.get(key))
            }
            saveOverrides(serial, overrides)
        } else {
            saveGlobal(updated)
        }
    }

    private fun backupFile(): File? {
        val root = MainActivityRuntime.currentInitDataRoot()?.takeIf { it.isNotBlank() } ?: return null
        return File(root, BACKUP_FILENAME)
    }

    private fun writeBackupMirror() {
        val file = backupFile() ?: return
        runCatching {
            val root = JSONObject()
            MainActivityRuntime.prefs.getString(KEY_GLOBAL, null)?.let { root.put("global", JSONObject(it)) }
            val games = JSONObject()
            for ((k, v) in MainActivityRuntime.prefs.all) {
                if (k.startsWith("config.game.") && v is String) {
                    runCatching { games.put(k.removePrefix("config.game."), JSONObject(v)) }
                }
            }
            if (games.length() > 0) root.put("games", games)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }
    }

    fun reconcileReusedFolder() {
        if (MainActivityRuntime.prefs.getBoolean(KEY_FOLDER_RECONCILE, false)) return
        MainActivityRuntime.prefs.edit { putBoolean(KEY_FOLDER_RECONCILE, true) }
        if (MainActivityRuntime.prefs.getString(KEY_GLOBAL, null) != null) return

        val mirror = backupFile()
        if (mirror != null && mirror.exists() && mirror.length() > 0L) {
            val restored = runCatching {
                val root = JSONObject(mirror.readText())
                root.optJSONObject("global")?.let { g ->
                    MainActivityRuntime.prefs.edit { putString(KEY_GLOBAL, g.toString()) }
                }
                root.optJSONObject("games")?.let { games ->
                    val it = games.keys()
                    while (it.hasNext()) {
                        val serial = it.next()
                        games.optJSONObject(serial)?.let { g ->
                            MainActivityRuntime.prefs.edit { putString(keyForGame(serial), g.toString()) }
                        }
                    }
                }
                MainActivityRuntime.prefs.getString(KEY_GLOBAL, null) != null
            }.getOrDefault(false)
            if (restored) return
        }

        val root = MainActivityRuntime.currentInitDataRoot()?.takeIf { it.isNotBlank() } ?: return
        val ini = File(root, "PCSX2-Android.ini")
        if (!ini.exists() || ini.length() == 0L) return
        runCatching {
            val map = parseIni(ini.readText())
            if (map.isNotEmpty()) saveGlobal(Settings().readFromIni(map))
        }
    }

    private fun parseIni(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        var section = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim()
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key.isNotEmpty()) map["$section/$key"] = value
        }
        return map
    }
}
