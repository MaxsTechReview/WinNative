package com.winlator.cmod.feature.community

import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.reshade.ReshadeConfigWriter
import com.winlator.cmod.runtime.reshade.ReshadeLoadout
import org.json.JSONArray
import org.json.JSONObject

object CommunitySettings {

    const val SCHEMA_VERSION = 2
    const val MIN_SCHEMA_VERSION = 1

    enum class Source { CONTAINER, FALLBACK, SHORTCUT }

    class Entry(
        val key: String,
        val source: Source,
        val maxLength: Int,
        val steamOnly: Boolean,
        val validate: (String) -> Boolean,
        val containerDefault: (Container) -> String,
    )

    private val FORBIDDEN = listOf(
        "..", "\$(", "\${", "`", "&&", "||", "|", "<", ">", "\\x", "/*", "*/",
    )

    private val BOOL01 = Regex("^[01]$")
    private val INT_SMALL = Regex("^\\d{1,5}$")
    private val CONTROLLER_COUNT = Regex("^[1-4]$")
    private val RES = Regex("^\\d{2,5}x\\d{2,5}$")
    private val IDENT = Regex("^[A-Za-z0-9._+\\- ]{0,64}$")
    private val LOCALE = Regex("^[A-Za-z0-9._@\\-]{0,32}$")
    private val CPULIST = Regex("^(\\d{1,3})(,\\d{1,3})*$")
    private val WINCOMPONENTS = Regex("^([a-z0-9]+=-?\\d{1,2})(,[a-z0-9]+=-?\\d{1,2})*$")
    private val THEME = Regex("^[A-Za-z0-9_,#\\- ]{0,48}$")
    private val FONT = Regex("^[A-Za-z0-9._\\- ]{0,128}$")
    private val KVBLOB = Regex("^[A-Za-z0-9_.,;=+\\-()\\[\\]:/ ]{0,2048}$")
    private val ENV_TOKEN = Regex("^[A-Za-z_][A-Za-z0-9_]*=[A-Za-z0-9_.:,+/=\\-]{0,256}$")
    private val EXECARGS = Regex("^[A-Za-z0-9 _.,:=+/\"'\\-]{0,512}$")

    private val AUDIO = setOf("alsa", "jack", "pulse", "pulseaudio")
    private val PRESETS = setOf(
        "COMPATIBILITY", "INTERMEDIATE", "PERFORMANCE", "STABILITY", "CUSTOM",
        "MONOTHREAD", "PRIMUS", "",
    )
    private val STARTUP = setOf("0", "1", "2")
    private val TOUCH_MODE = setOf("0", "1", "2")
    private val ZINK_MODE = setOf("", "unix", "windows")
    private val RESHADE_MODE = setOf("", ReshadeLoadout.MODE_SOLO, ReshadeLoadout.MODE_STACK)

    private const val MAX_ENV_TOKENS = 64
    private const val MAX_RESHADE_PARAMS = 128

    fun isSafeText(value: String): Boolean {
        if (value.any { it.code < 0x20 || it.code == 0x7f }) return false
        val low = value.lowercase()
        return FORBIDDEN.none { low.contains(it) }
    }

    private fun validEnvVars(value: String): Boolean {
        if (value.isEmpty()) return true
        val tokens = value.split(" ")
        if (tokens.size > MAX_ENV_TOKENS) return false
        return tokens.all { it.isEmpty() || ENV_TOKEN.matches(it) }
    }

    private fun validReshadeLoadout(value: String): Boolean {
        if (value.isEmpty()) return true
        return try {
            val arr = JSONArray(value)
            if (arr.length() > ReshadeLoadout.MAX_EFFECTS) return false
            var ok = true
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i)
                val name = item?.optString("name", "") ?: ""
                if (item == null || name.isBlank() || !IDENT.matches(name) || !item.has("enabled")) {
                    ok = false
                }
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun validReshadeParams(value: String): Boolean {
        if (value.isEmpty()) return true
        return try {
            val root = JSONObject(value)
            if (root.length() > ReshadeLoadout.MAX_EFFECTS) return false
            var ok = true
            for (name in root.keys()) {
                val effect = root.optJSONObject(name)
                if (!IDENT.matches(name) || effect == null || effect.length() > MAX_RESHADE_PARAMS) {
                    ok = false
                    continue
                }
                for (param in effect.keys()) {
                    if (!IDENT.matches(param) || effect.opt(param) !is Number) ok = false
                }
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun entry(
        key: String,
        source: Source,
        maxLength: Int,
        validate: (String) -> Boolean,
        steamOnly: Boolean = false,
        containerDefault: (Container) -> String = { it.getExtra(key, "") ?: "" },
    ) = Entry(key, source, maxLength, steamOnly, validate, containerDefault)

    private fun flag(value: Boolean) = if (value) "1" else "0"

    val ENTRIES: List<Entry> = listOf(
        entry("screenSize", Source.CONTAINER, 16, { RES.matches(it) }) { it.getScreenSize() ?: "" },
        entry("refreshRate", Source.FALLBACK, 5, { INT_SMALL.matches(it) }),
        entry("fpsLimit", Source.SHORTCUT, 5, { INT_SMALL.matches(it) }),
        entry("audioDriver", Source.CONTAINER, 16, { it in AUDIO }) { it.getAudioDriver() ?: "" },
        entry("midiSoundFont", Source.CONTAINER, 128, { FONT.matches(it) }) {
            it.getMIDISoundFont() ?: ""
        },
        entry("graphicsDriver", Source.CONTAINER, 32, { IDENT.matches(it) }) {
            it.getGraphicsDriver() ?: ""
        },
        entry("graphicsDriverConfig", Source.CONTAINER, 2048, { KVBLOB.matches(it) }) {
            it.getGraphicsDriverConfig() ?: ""
        },
        entry("zinkMode", Source.CONTAINER, 16, { it in ZINK_MODE }) { it.getZinkMode() ?: "" },
        entry("dxwrapper", Source.CONTAINER, 32, { IDENT.matches(it) }) { it.getDXWrapper() ?: "" },
        entry("dxwrapperConfig", Source.CONTAINER, 2048, { KVBLOB.matches(it) }) {
            it.getDXWrapperConfig() ?: ""
        },
        entry("swapRB", Source.CONTAINER, 1, { BOOL01.matches(it) }),
        entry("sgsrEnabled", Source.SHORTCUT, 1, { BOOL01.matches(it) }),
        entry("sgsrUpscaleMode", Source.SHORTCUT, 16, { IDENT.matches(it) }),
        entry("sgsrSharpness", Source.SHORTCUT, 8, { IDENT.matches(it) }),
        entry("wineVersion", Source.CONTAINER, 64, { IDENT.matches(it) }) { it.getWineVersion() ?: "" },
        entry("emulator", Source.CONTAINER, 32, { IDENT.matches(it) }) { it.getEmulator() ?: "" },
        entry("emulator64", Source.CONTAINER, 32, { IDENT.matches(it) }) { it.getEmulator64() ?: "" },
        entry("useUnixLibs", Source.CONTAINER, 1, { BOOL01.matches(it) }) { flag(it.isUseUnixLibs) },
        entry("lc_all", Source.CONTAINER, 32, { LOCALE.matches(it) }) { it.getLC_ALL() ?: "" },
        entry("desktopTheme", Source.CONTAINER, 48, { THEME.matches(it) }) {
            it.getDesktopTheme() ?: ""
        },
        entry("wincomponents", Source.CONTAINER, 512, { WINCOMPONENTS.matches(it) }) {
            it.getWinComponents() ?: ""
        },
        entry("envVars", Source.CONTAINER, 4096, { validEnvVars(it) }) { it.getEnvVars() ?: "" },
        entry("box64Version", Source.CONTAINER, 64, { IDENT.matches(it) }) {
            it.getBox64Version() ?: ""
        },
        entry("box64Preset", Source.CONTAINER, 24, { it in PRESETS }) { it.getBox64Preset() ?: "" },
        entry("fexcoreVersion", Source.CONTAINER, 64, { IDENT.matches(it) }) {
            it.getFEXCoreVersion() ?: ""
        },
        entry("fexcorePreset", Source.CONTAINER, 24, { it in PRESETS }) {
            it.getFEXCorePreset() ?: ""
        },
        entry("startupSelection", Source.CONTAINER, 1, { it in STARTUP }) {
            it.getStartupSelection().toInt().toString()
        },
        entry("execArgs", Source.CONTAINER, 512, { EXECARGS.matches(it) }) { it.getExecArgs() ?: "" },
        entry("fullscreenStretched", Source.CONTAINER, 1, { BOOL01.matches(it) }) {
            flag(it.isFullscreenStretched)
        },
        entry("cpuList", Source.CONTAINER, 96, { CPULIST.matches(it) }) { it.getCPUList(true) ?: "" },
        entry("cpuListWoW64", Source.CONTAINER, 96, { CPULIST.matches(it) }) {
            it.getCPUListWoW64(true) ?: ""
        },
        entry("inputType", Source.CONTAINER, 5, { INT_SMALL.matches(it) }) {
            it.getInputType().toString()
        },
        entry("exclusiveXInput", Source.CONTAINER, 1, { BOOL01.matches(it) }) {
            flag(it.isExclusiveXInput)
        },
        entry("numControllers", Source.SHORTCUT, 1, { CONTROLLER_COUNT.matches(it) }),
        entry("disableXinput", Source.SHORTCUT, 1, { BOOL01.matches(it) }),
        entry("simTouchScreen", Source.SHORTCUT, 1, { BOOL01.matches(it) }),
        entry("screenTouchMode", Source.SHORTCUT, 1, { it in TOUCH_MODE }),
        entry(ReshadeConfigWriter.EXTRA_LOADOUT, Source.CONTAINER, 512, { validReshadeLoadout(it) }),
        entry(ReshadeConfigWriter.EXTRA_MODE, Source.CONTAINER, 8, { it in RESHADE_MODE }),
        entry(ReshadeConfigWriter.EXTRA_PARAMS, Source.CONTAINER, 4096, { validReshadeParams(it) }),
        entry(ReshadeConfigWriter.EXTRA_EFFECT, Source.CONTAINER, 64, { IDENT.matches(it) }),
        entry("useColdClient", Source.CONTAINER, 1, { BOOL01.matches(it) }, steamOnly = true) {
            flag(it.isUseColdClient)
        },
        entry("unpackFiles", Source.CONTAINER, 1, { BOOL01.matches(it) }, steamOnly = true) {
            flag(it.isUnpackFiles)
        },
        entry("useSteamInput", Source.CONTAINER, 1, { BOOL01.matches(it) }, steamOnly = true),
        entry("steamOfflineMode", Source.CONTAINER, 1, { BOOL01.matches(it) }, steamOnly = true) {
            flag(it.isSteamOfflineMode)
        },
        entry("runtimePatcher", Source.CONTAINER, 1, { BOOL01.matches(it) }, steamOnly = true) {
            flag(it.isRuntimePatcher)
        },
    )

    val BY_KEY: Map<String, Entry> = ENTRIES.associateBy { it.key }

    val KEYS: Set<String> = BY_KEY.keys

    val NON_PORTABLE: Set<String> = setOf(
        "custom_name",
        "container_id",
        "use_container_defaults",
        "controlsProfile",
        "gestureProfileId",
        "launch_exe_path",
        "custom_exe",
        "custom_game_folder",
        "cloud_force_download",
        "launchRealSteam",
        "steamType",
    )

    fun isSteam(shortcut: Shortcut): Boolean =
        shortcut.getExtra("game_source", "").equals("steam", ignoreCase = true)

    fun effective(shortcut: Shortcut, entry: Entry): String {
        val container: Container? = shortcut.container
        val fromContainer = if (container != null) entry.containerDefault(container) else ""
        return when (entry.source) {
            Source.CONTAINER -> shortcut.getSettingExtra(entry.key, fromContainer) ?: ""
            Source.FALLBACK -> shortcut.getExtra(entry.key, "").ifEmpty { fromContainer }
            Source.SHORTCUT -> shortcut.getExtra(entry.key, "")
        }
    }

    fun accepts(entry: Entry, value: String): Boolean =
        value.length <= entry.maxLength && isSafeText(value) && entry.validate(value)

    fun accepts(key: String, value: String): Boolean {
        val entry = BY_KEY[key] ?: return false
        return accepts(entry, value)
    }
}
