package com.winlator.cmod.feature.configs

import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import org.json.JSONObject
import timber.log.Timber

/**
 * Serializes a Container + Shortcut to/from the `config_json` blob stored in
 * Supabase, and applies an imported config to a target shortcut.
 *
 * Round-trip goals:
 *  - Every value the user can tweak on the settings tabs (General, Display, Steam,
 *    Advanced, Input, Variables, Wine, Components) must be captured on export and
 *    re-applied on import.
 *  - Effective values are exported, not just shortcut overrides. A user who never
 *    overrode anything still gets a complete config because Container defaults are
 *    captured too.
 *  - On import, everything is written as per-shortcut overrides via [Shortcut.putExtra]
 *    so the shared Container is never mutated and sibling shortcuts that use the same
 *    container are unaffected.
 *
 * Filters:
 *  - **Drives** are excluded entirely (per-device storage layout — pointless to ship).
 *  - **wineprefixArch / paths / device IDs** are device-bound; not exported.
 *  - **Steam tab** is exported only when the source shortcut is Steam, and applied
 *    only when the target shortcut is Steam. A non-Steam target silently drops Steam
 *    settings even if the JSON happens to contain them.
 */
object ConfigSerializer {
    const val SCHEMA_VERSION: Int = 1
    private const val TAG = "ConfigSerializer"
    /** Per-extra value cap to defang malicious JSON. Any single string longer than
     *  this is silently dropped on import. Real settings values are well under 4 KB. */
    private const val MAX_EXTRA_VALUE_LEN: Int = 8 * 1024

    /** Keys whose semantics are Steam-specific and must not cross to non-Steam shortcuts. */
    val STEAM_ONLY_KEYS: Set<String> = setOf(
        "useColdClient",
        "useSteamInput",
        "forceDlc",
        "steamOfflineMode",
        "unpackFiles",
        "runtimePatcher",
        "launchRealSteam",
        "steamType",
        "allowSteamUpdates",
    )

    /**
     * Keys that should never be exported or applied — they only make sense on the
     * device where the config was captured (filesystem paths, wine prefix arch,
     * device identifiers, art paths, etc.).
     */
    private val DEVICE_BOUND_KEYS: Set<String> = setOf(
        "wineprefixArch",
        "game_install_path",
        "launch_exe_path",
        "custom_exe",
        "custom_game_folder",
        "custom_mount_path",
        "customLibraryIconPath",
        "customCoverArtPath",
        "customLibraryHeroArtPath",
        "container_id",
        "uuid",
        "custom_name",
        // Drives map to per-device storage paths. Per user spec, never exported or
        // applied; the entry here is defense-in-depth against legacy JSON that may
        // have a "drives" key in containerExtras from before this filter existed.
        "drives",
        // controlsProfile is a numeric ID into the *local* InputControlsManager
        // table; importing a foreign value binds to whichever local profile happens
        // to share that ID. Per-device by definition.
        "controlsProfile",
        // Runtime version markers — these are written by the launcher each run
        "appVersion",
        "imgVersion",
        "mono_installed",
        "mono_version",
        "wineprefixNeedsUpdate",
    )

    /**
     * Canonical resolver for the game ID stored on a shortcut. Steam + Epic both write
     * `app_id`; GOG writes `gog_id`; everything else falls back to the EXE path.
     */
    fun gameIdForShortcut(shortcut: Shortcut, gameSource: String): String? = when (gameSource) {
        "STEAM", "EPIC" -> shortcut.getExtra("app_id")?.takeIf { it.isNotBlank() }
        "GOG" -> shortcut.getExtra("gog_id")?.takeIf { it.isNotBlank() }
            ?: shortcut.getExtra("app_id")?.takeIf { it.isNotBlank() }
        else -> shortcut.path?.takeIf { it.isNotBlank() }
    }

    /**
     * Container fields with typed string getters/setters. Order is stable to keep
     * round-trip diffs human-readable. `drives` is intentionally absent — see the
     * filter rules at the top of the file.
     */
    private val CONTAINER_STRING_FIELDS: List<ContainerField<String>> = listOf(
        cf("screenSize", { it.screenSize }, { c, v -> c.screenSize = v }),
        cf("graphicsDriver", { it.graphicsDriver }, { c, v -> c.graphicsDriver = v }),
        cf("graphicsDriverConfig", { it.graphicsDriverConfig }, { c, v -> c.graphicsDriverConfig = v }),
        cf("dxwrapper", { it.dxWrapper }, { c, v -> c.dxWrapper = v }),
        cf("dxwrapperConfig", { it.dxWrapperConfig }, { c, v -> c.dxWrapperConfig = v }),
        cf("audioDriver", { it.audioDriver }, { c, v -> c.audioDriver = v }),
        cf("wincomponents", { it.winComponents }, { c, v -> c.winComponents = v }),
        cf("envVars", { it.envVars }, { c, v -> c.envVars = v }),
        cf("cpuList", { it.cpuList }, { c, v -> c.cpuList = v }),
        cf("cpuListWoW64", { it.cpuListWoW64 }, { c, v -> c.cpuListWoW64 = v }),
        cf("emulator", { it.emulator }, { c, v -> c.emulator = v }),
        cf("emulator64", { it.emulator64 }, { c, v -> c.emulator64 = v }),
        cf("box64Preset", { it.box64Preset }, { c, v -> c.box64Preset = v }),
        cf("box64Version", { it.box64Version }, { c, v -> c.box64Version = v }),
        // FEXCore preset/version use explicit method names (Kotlin can't synthesize
        // a clean `fexcorePreset` property from getFEXCorePreset/setFEXCorePreset
        // because of the capitalization mismatch).
        cf("fexcorePreset", { it.getFEXCorePreset() }, { c, v -> c.setFEXCorePreset(v) }),
        cf("fexcoreVersion", { it.getFEXCoreVersion() }, { c, v -> c.setFEXCoreVersion(v) }),
        cf("wineVersion", { it.wineVersion }, { c, v -> c.wineVersion = v }),
        // The launcher reads this shortcut extra as `lc_all` — keep the JSON key in
        // sync with what `Shortcut.getSettingExtra("lc_all", ...)` looks for.
        cf("lc_all", { it.lC_ALL }, { c, v -> c.lC_ALL = v }),
        cf("desktopTheme", { it.desktopTheme }, { c, v -> c.desktopTheme = v }),
        // getMIDISoundFont / setMidiSoundFont — asymmetric Java naming means Kotlin
        // doesn't auto-synthesize a writable property. Use the explicit setter.
        cf("midiSoundFont", { it.midiSoundFont }, { c, v -> c.setMidiSoundFont(v) }),
        cf("execArgs", { it.execArgs }, { c, v -> c.execArgs = v }),
    )

    private val CONTAINER_BOOL_FIELDS: List<ContainerBoolField> = listOf(
        bf("fullscreenStretched", { it.isFullscreenStretched }, { c, v -> c.isFullscreenStretched = v }),
    )

    /** Container fields with `byte` getters/setters (currently just startupSelection). */
    private val CONTAINER_BYTE_FIELDS: List<ContainerByteField> = listOf(
        byf("startupSelection", { it.startupSelection }, { c, v -> c.startupSelection = v }),
    )

    /** Container fields with `int` getters/setters (currently just inputType bitfield). */
    private val CONTAINER_INT_FIELDS: List<ContainerIntField> = listOf(
        intf("inputType", { it.inputType }, { c, v -> c.inputType = v }),
    )

    /**
     * Container "extras" keys we serialize. These are values stored in the
     * container's extraData JSONObject rather than via dedicated setters.
     * `wineprefixArch` is intentionally absent — it's device-bound.
     */
    private val CONTAINER_EXTRA_KEYS: List<String> = listOf(
        "containerLanguage",
        "swapRB",
        // hudSettings is written only at the container level (XServerDisplayActivity
        // line ~3596) — never on a shortcut. Reading it from the container extras
        // is the only way to capture it; the shortcut-extras path would always miss.
        "hudSettings",
    )

    /**
     * Per-shortcut keys (mirror container fields when overridden, plus shortcut-only
     * settings like input bindings and HUD). Steam-only keys are filtered at export
     * and import time based on whether the source/target is Steam.
     */
    private val SHORTCUT_OVERRIDE_KEYS: List<String> = listOf(
        // Display / graphics
        "screenSize",
        "graphicsDriver",
        "graphicsDriverConfig",
        "dxwrapper",
        "dxwrapperConfig",
        "audioDriver",
        "envVars",
        "wincomponents",
        "swapRB",
        "fullscreenStretched",
        // Emulator / preset / version
        "box64Preset",
        "box64Version",
        "fexcorePreset",
        "fexcoreVersion",
        "wineVersion",
        "emulator",
        "emulator64",
        // Locale / theme / audio
        "lc_all",
        "midiSoundFont",
        "desktopTheme",
        "containerLanguage",
        // Advanced
        "startupSelection",
        "cpuList",
        "cpuListWoW64",
        // Input — `controlsProfile` and `extra_exec_args` deliberately excluded:
        // controlsProfile is a per-device local ID (filtered via DEVICE_BOUND_KEYS),
        // extra_exec_args only ever lives on a transient launch Intent.
        "inputType",
        "numControllers",
        "disableXinput",
        "simTouchScreen",
        "execArgs",
        // Framerate (HUD settings live on the container, not the shortcut)
        "fpsLimit",
        "refreshRate",
        // Note: cloud_sync_disabled / cloud_force_download / offline_mode are
        // intentionally NOT in this list — they're per-device/per-account user
        // preferences and a community config has no business overwriting them.
    )

    /**
     * Build the JSON payload for an Export. Captures the effective container state
     * plus any shortcut-level overrides. The Steam tab is only included when the
     * source shortcut is Steam.
     *
     * For each export key the *effective* value is preferred — shortcut override if
     * present, else the container's value. This means even a config that never
     * touched any shortcut-level override still exports a complete picture.
     */
    fun exportToJson(container: Container, shortcut: Shortcut): JSONObject {
        val out = JSONObject()
        out.put("schemaVersion", SCHEMA_VERSION)

        val containerJson = JSONObject()
        CONTAINER_STRING_FIELDS.forEach { f ->
            val v = f.getter(container)
            if (!v.isNullOrEmpty()) containerJson.put(f.key, v)
        }
        CONTAINER_BOOL_FIELDS.forEach { f -> containerJson.put(f.key, f.getter(container)) }
        CONTAINER_BYTE_FIELDS.forEach { f -> containerJson.put(f.key, f.getter(container).toInt()) }
        CONTAINER_INT_FIELDS.forEach { f -> containerJson.put(f.key, f.getter(container)) }
        out.put("container", containerJson)

        val containerExtras = JSONObject()
        CONTAINER_EXTRA_KEYS.forEach { key ->
            if (key in DEVICE_BOUND_KEYS) return@forEach
            val v = container.getExtra(key, "")
            if (v.isNotEmpty()) containerExtras.put(key, v)
        }
        out.put("containerExtras", containerExtras)

        val isSteamShortcut = shortcut.getExtra("game_source") == "STEAM"
        val shortcutExtras = JSONObject()
        SHORTCUT_OVERRIDE_KEYS.forEach { key ->
            if (key in DEVICE_BOUND_KEYS) return@forEach
            // Steam-only keys belong in the steamTab block, not here — keeps the
            // import-time filter logic in one place.
            if (key in STEAM_ONLY_KEYS) return@forEach
            val v = shortcut.getExtra(key)
            if (!v.isNullOrEmpty()) shortcutExtras.put(key, v)
        }
        out.put("shortcutExtras", shortcutExtras)

        // Steam tab: only if the source is a Steam shortcut. Each key falls back to
        // the typed Container getter when the shortcut has no override, so the
        // effective state is captured even for users who never opened the Steam tab.
        if (isSteamShortcut) {
            val steamTab = JSONObject()
            steamTab.put("useColdClient", effectiveSteamBool(shortcut, "useColdClient", container.isUseColdClient))
            steamTab.put("forceDlc", effectiveSteamBool(shortcut, "forceDlc", container.isForceDlc))
            steamTab.put("steamOfflineMode", effectiveSteamBool(shortcut, "steamOfflineMode", container.isSteamOfflineMode))
            steamTab.put("unpackFiles", effectiveSteamBool(shortcut, "unpackFiles", container.isUnpackFiles))
            steamTab.put("runtimePatcher", effectiveSteamBool(shortcut, "runtimePatcher", container.isRuntimePatcher))
            steamTab.put("launchRealSteam", effectiveSteamBool(shortcut, "launchRealSteam", container.isLaunchRealSteam))
            steamTab.put("allowSteamUpdates", effectiveSteamBool(shortcut, "allowSteamUpdates", container.isAllowSteamUpdates))
            steamTab.put("steamType", effectiveSteamString(shortcut, "steamType", container.steamType))
            // useSteamInput is stored only as an extra (no typed getter), so read effective extra.
            val useSteamInput = shortcut.getExtra("useSteamInput")?.takeIf { it.isNotEmpty() }
                ?: container.getExtra("useSteamInput", "")
            if (useSteamInput.isNotEmpty()) steamTab.put("useSteamInput", useSteamInput)
            out.put("steamTab", steamTab)
            out.put("includesSteamSubtab", true)
        } else {
            out.put("includesSteamSubtab", false)
        }

        return out
    }

    /**
     * Apply an imported config to the target shortcut. Every value is written as a
     * per-shortcut override via [Shortcut.putExtra] — the shared Container is never
     * mutated, so other shortcuts using the same container are unaffected.
     *
     * Filters applied during import:
     *  - [DEVICE_BOUND_KEYS] are always skipped (paths, arch, device IDs).
     *  - Steam tab is applied only when the target shortcut is a Steam game,
     *    regardless of whether the source was Steam.
     *  - Steam-only keys that somehow appear elsewhere in the JSON are still filtered
     *    out for non-Steam targets.
     *
     * Caller is responsible for invoking [Shortcut.saveData].
     */
    fun applyToShortcut(
        json: JSONObject,
        container: Container,
        shortcut: Shortcut,
    ): ApplyResult {
        val warnings = mutableListOf<String>()
        val schema = json.optInt("schemaVersion", -1)
        if (schema != SCHEMA_VERSION) {
            warnings += "Schema version $schema does not match expected $SCHEMA_VERSION; importing best-effort."
        }
        val targetIsSteam = shortcut.getExtra("game_source") == "STEAM"

        json.optJSONObject("container")?.let { containerObj ->
            CONTAINER_STRING_FIELDS.forEach { f ->
                if (f.key in DEVICE_BOUND_KEYS) return@forEach
                val v = containerObj.optString(f.key, "").takeIf { it.isNotEmpty() } ?: return@forEach
                runCatching { shortcut.putExtra(f.key, v) }
                    .onFailure { warnings += "shortcut override ${f.key}: ${it.message}" }
            }
            CONTAINER_BOOL_FIELDS.forEach { f ->
                if (containerObj.has(f.key)) {
                    val v = if (containerObj.optBoolean(f.key)) "1" else "0"
                    runCatching { shortcut.putExtra(f.key, v) }
                        .onFailure { warnings += "shortcut override ${f.key}: ${it.message}" }
                }
            }
            CONTAINER_BYTE_FIELDS.forEach { f ->
                if (containerObj.has(f.key)) {
                    val v = containerObj.optInt(f.key).toString()
                    runCatching { shortcut.putExtra(f.key, v) }
                        .onFailure { warnings += "shortcut override ${f.key}: ${it.message}" }
                }
            }
            CONTAINER_INT_FIELDS.forEach { f ->
                if (containerObj.has(f.key)) {
                    val v = containerObj.optInt(f.key).toString()
                    runCatching { shortcut.putExtra(f.key, v) }
                        .onFailure { warnings += "shortcut override ${f.key}: ${it.message}" }
                }
            }
        }

        // Allowlist enforcement: only iterate keys we explicitly know how to handle.
        // Untrusted JSON (file-import, community import) cannot smuggle in arbitrary
        // shortcut-extras keys this way. Unknown keys in the JSON are silently
        // ignored — same as if they were absent. Values are also length-capped.
        val maxValueLen = MAX_EXTRA_VALUE_LEN
        json.optJSONObject("containerExtras")?.let { extras ->
            CONTAINER_EXTRA_KEYS.forEach { key ->
                if (key in DEVICE_BOUND_KEYS) return@forEach
                if (!extras.has(key)) return@forEach
                val v = extras.optString(key, "")
                if (v.isEmpty() || v.length > maxValueLen) return@forEach
                runCatching { shortcut.putExtra(key, v) }
                    .onFailure { warnings += "shortcut override $key: ${it.message}" }
            }
        }

        json.optJSONObject("shortcutExtras")?.let { extras ->
            SHORTCUT_OVERRIDE_KEYS.forEach { key ->
                if (key in DEVICE_BOUND_KEYS) return@forEach
                if (!targetIsSteam && key in STEAM_ONLY_KEYS) {
                    if (extras.has(key)) {
                        warnings += "Skipped Steam-only key '$key' (target shortcut is not a Steam game)."
                    }
                    return@forEach
                }
                if (!extras.has(key)) return@forEach
                val v = extras.optString(key, "")
                if (v.isEmpty() || v.length > maxValueLen) return@forEach
                runCatching { shortcut.putExtra(key, v) }
                    .onFailure { warnings += "shortcut extra $key: ${it.message}" }
            }
        }

        // Steam-tab block: only applies when the target is itself a Steam shortcut.
        // A non-Steam target silently drops the entire block — the importer keeps
        // their existing non-Steam behavior intact.
        if (targetIsSteam) {
            json.optJSONObject("steamTab")?.let { steamTab ->
                steamTab.keys().forEach { key ->
                    if (key in DEVICE_BOUND_KEYS) return@forEach
                    val v = when (val raw = steamTab.opt(key)) {
                        is Boolean -> if (raw) "1" else "0"
                        null -> ""
                        else -> raw.toString()
                    }
                    if (v.isNotEmpty()) {
                        runCatching { shortcut.putExtra(key, v) }
                            .onFailure { warnings += "steam tab $key: ${it.message}" }
                    }
                }
            }
        } else if (json.has("steamTab")) {
            warnings += "Source was a Steam config; Steam-specific settings dropped because this shortcut isn't a Steam game."
        }

        return ApplyResult(warnings = warnings)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Read a Steam-only boolean's effective value. Shortcut override ("0"/"1" string)
     * wins; otherwise the typed Container default. Emitted as a JSON boolean for
     * cleaner JSON, even though Shortcut.extraData stores strings.
     */
    private fun effectiveSteamBool(shortcut: Shortcut, key: String, containerDefault: Boolean): Boolean {
        val ov = shortcut.getExtra(key)
        return when {
            ov.isNullOrEmpty() -> containerDefault
            ov == "1" || ov.equals("true", ignoreCase = true) -> true
            ov == "0" || ov.equals("false", ignoreCase = true) -> false
            else -> containerDefault
        }
    }

    private fun effectiveSteamString(shortcut: Shortcut, key: String, containerDefault: String?): String {
        val ov = shortcut.getExtra(key)
        return when {
            !ov.isNullOrEmpty() -> ov
            !containerDefault.isNullOrEmpty() -> containerDefault
            else -> ""
        }
    }

    data class ApplyResult(val warnings: List<String>) {
        val isClean: Boolean get() = warnings.isEmpty()
    }

    private class ContainerField<T>(
        val key: String,
        val getter: (Container) -> T?,
        val setter: (Container, T) -> Unit,
    )

    private class ContainerBoolField(
        val key: String,
        val getter: (Container) -> Boolean,
        val setter: (Container, Boolean) -> Unit,
    )

    private class ContainerByteField(
        val key: String,
        val getter: (Container) -> Byte,
        val setter: (Container, Byte) -> Unit,
    )

    private class ContainerIntField(
        val key: String,
        val getter: (Container) -> Int,
        val setter: (Container, Int) -> Unit,
    )

    private fun cf(
        key: String,
        getter: (Container) -> String?,
        setter: (Container, String) -> Unit,
    ) = ContainerField(key, getter, setter)

    private fun bf(
        key: String,
        getter: (Container) -> Boolean,
        setter: (Container, Boolean) -> Unit,
    ) = ContainerBoolField(key, getter, setter)

    private fun byf(
        key: String,
        getter: (Container) -> Byte,
        setter: (Container, Byte) -> Unit,
    ) = ContainerByteField(key, getter, setter)

    private fun intf(
        key: String,
        getter: (Container) -> Int,
        setter: (Container, Int) -> Unit,
    ) = ContainerIntField(key, getter, setter)
}
