package com.winlator.cmod.feature.community

import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import org.json.JSONObject

/**
 * Serializes the *effective* value of every portable shortcut setting (resolving
 * shortcut override -> container default via [Shortcut.getSettingExtra]) into a
 * settings map for sharing. Device-local driver tokens (version=/gpuName=) are
 * blanked so a downloader keeps their own GPU driver. PII / structural / local
 * fields (paths, uuid, container_id, controlsProfile, artwork) are excluded.
 */
object ConfigSerializer {

    fun serialize(shortcut: Shortcut): JSONObject {
        val c = shortcut.container
        val s = JSONObject()

        fun put(key: String, value: String?) {
            if (!value.isNullOrBlank()) s.put(key, value)
        }
        fun eff(key: String, containerValue: String): String =
            shortcut.getSettingExtra(key, containerValue)
        fun extra(key: String, def: String): String = shortcut.getExtra(key, def)

        // General / Display (explicit Java getter calls to avoid Kotlin
        // property-name ambiguity for ALL-CAPS getters like getDXWrapper).
        put("screenSize", eff("screenSize", c.getScreenSize()))
        put("audioDriver", eff("audioDriver", c.getAudioDriver()))
        put("midiSoundFont", eff("midiSoundFont", c.getMIDISoundFont()))
        put("graphicsDriver", eff("graphicsDriver", c.getGraphicsDriver()))
        put("graphicsDriverConfig", eff("graphicsDriverConfig", c.getGraphicsDriverConfig()))
        put("dxwrapper", eff("dxwrapper", c.getDXWrapper()))
        put("dxwrapperConfig", eff("dxwrapperConfig", c.getDXWrapperConfig()))
        put("swapRB", extra("swapRB", ""))
        put("refreshRate", extra("refreshRate", ""))
        put("fpsLimit", extra("fpsLimit", ""))
        put("sgsrEnabled", extra("sgsrEnabled", ""))
        put("sgsrUpscaleMode", extra("sgsrUpscaleMode", ""))
        put("sgsrSharpness", extra("sgsrSharpness", ""))

        // Wine
        put("wineVersion", eff("wineVersion", c.getWineVersion()))
        put("emulator", eff("emulator", c.getEmulator()))
        put("emulator64", eff("emulator64", c.getEmulator64()))
        put("lc_all", eff("lc_all", c.getLC_ALL()))
        put("desktopTheme", eff("desktopTheme", c.getDesktopTheme()))

        // Components / Variables
        put("wincomponents", eff("wincomponents", c.getWinComponents()))
        put("envVars", eff("envVars", c.getEnvVars()))

        // Advanced
        put("box64Version", eff("box64Version", c.getBox64Version()))
        put("box64Preset", eff("box64Preset", c.getBox64Preset()))
        put("fexcoreVersion", eff("fexcoreVersion", c.getFEXCoreVersion()))
        put("fexcorePreset", eff("fexcorePreset", c.getFEXCorePreset()))
        put("startupSelection", eff("startupSelection", c.getStartupSelection().toString()))
        put("execArgs", eff("execArgs", c.getExecArgs()))
        put("fullscreenStretched", eff("fullscreenStretched", if (c.isFullscreenStretched()) "1" else "0"))
        put("cpuList", eff("cpuList", c.getCPUList(true)))
        put("cpuListWoW64", eff("cpuListWoW64", c.getCPUListWoW64(true)))

        // Input
        put("inputType", eff("inputType", c.getInputType().toString()))
        put("exclusiveXInput", eff("exclusiveXInput", if (c.isExclusiveXInput()) "1" else "0"))
        put("numControllers", extra("numControllers", ""))
        put("disableXinput", extra("disableXinput", ""))
        put("simTouchScreen", extra("simTouchScreen", ""))

        // Steam (only for Steam games)
        if (isSteam(shortcut)) {
            put("useColdClient", eff("useColdClient", if (c.isUseColdClient()) "1" else "0"))
            put("unpackFiles", eff("unpackFiles", if (c.isUnpackFiles()) "1" else "0"))
            put("useSteamInput", extra("useSteamInput", ""))
            put("steamOfflineMode", eff("steamOfflineMode", if (c.isSteamOfflineMode()) "1" else "0"))
            put("runtimePatcher", eff("runtimePatcher", if (c.isRuntimePatcher()) "1" else "0"))
        }
        return s
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

    private fun isSteam(shortcut: Shortcut): Boolean =
        shortcut.getExtra("game_source", "").equals("steam", ignoreCase = true)

    private fun slug(name: String): String {
        val s = name.lowercase().map { if (it.isLetterOrDigit() || it == '-' || it == '.') it else '-' }
            .joinToString("").trim('-')
        return s.ifBlank { "game" }.take(100)
    }
}
