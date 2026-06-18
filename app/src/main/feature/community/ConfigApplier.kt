package com.winlator.cmod.feature.community

import com.winlator.cmod.runtime.container.Shortcut
import org.json.JSONObject

/**
 * Applies a downloaded settings document to the CURRENT shortcut only, as
 * shortcut-level overrides. It never mutates the Container, other shortcuts, the
 * Exec line, icon, artwork, or container_id -- so containers can't be broken.
 * EVERY known setting is applied verbatim (including the graphics-driver version
 * and dxvk/vkd3d/gpuName tokens); availability is gated beforehand by
 * ComponentChecker, which blocks apply + shows MISSING COMPONENT if anything the
 * config needs isn't installed.
 */
object ConfigApplier {

    /** The only keys an applied config may write. Mirrors the serializer. */
    private val ALLOWED = setOf(
        "screenSize", "audioDriver", "midiSoundFont", "graphicsDriver",
        "graphicsDriverConfig", "dxwrapper", "dxwrapperConfig", "swapRB",
        "refreshRate", "fpsLimit", "sgsrEnabled", "sgsrUpscaleMode", "sgsrSharpness",
        "wineVersion", "emulator", "emulator64", "lc_all", "desktopTheme",
        "wincomponents", "envVars", "box64Version", "box64Preset",
        "fexcoreVersion", "fexcorePreset", "startupSelection", "execArgs",
        "fullscreenStretched", "cpuList", "cpuListWoW64", "inputType",
        "exclusiveXInput", "numControllers", "disableXinput", "simTouchScreen",
        "useColdClient", "unpackFiles", "useSteamInput", "steamOfflineMode",
        "runtimePatcher",
    )

    fun apply(shortcut: Shortcut, settings: JSONObject) {
        val keys = settings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in ALLOWED) continue
            shortcut.putExtra(key, settings.optString(key, ""))
        }
        // Force shortcut-level resolution so the applied overrides take effect.
        shortcut.putExtra("use_container_defaults", "0")
        shortcut.saveData()
    }
}
