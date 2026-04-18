// Settings > Presets fragment — hosts PresetsScreen via ComposeView.
package com.winlator.cmod.feature.settings
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.runtime.compat.box64.Box64Preset
import com.winlator.cmod.runtime.compat.box64.Box64PresetManager
import com.winlator.cmod.runtime.compat.fexcore.FEXCorePreset
import com.winlator.cmod.runtime.compat.fexcore.FEXCorePresetManager
import com.winlator.cmod.runtime.wine.EnvVars
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.io.AssetPaths
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.util.StringUtils
import org.json.JSONArray
import java.util.Locale

/**
 * Thin Compose host for the Presets screen.
 *
 * Responsibilities: load preset lists via [Box64PresetManager] / [FEXCorePresetManager],
 * translate env var JSON assets into [EnvVarDefinition]s, and persist the currently
 * selected preset ID into the same `box64_preset` / `fexcore_preset` SharedPref
 * keys the container/shortcut dialogs already read from. We intentionally *don't*
 * touch the manager APIs or storage format — preset env vars continue to patch
 * through [com.winlator.cmod.runtime.display.environment.components.GuestProgramLauncherComponent]
 * at container launch time exactly as before.
 */
class PresetsFragment : Fragment() {
    private lateinit var preferences: SharedPreferences

    private var presetsState by mutableStateOf(PresetsState())

    private val definitionsCache = mutableMapOf<PresetEngine, List<EnvVarDefinition>>()
    private val selectedPresetIds = linkedMapOf<PresetEngine, String>()

    // Per-engine map of env-var name → current value. We keep snapshots of BOTH
    // engines so PresetsScreen's AnimatedContent can render each side of the
    // crossfade with its own data.
    private val currentValues =
        mutableMapOf<PresetEngine, LinkedHashMap<String, String>>(
            PresetEngine.BOX64 to linkedMapOf(),
            PresetEngine.FEXCORE to linkedMapOf(),
        )
    private var currentEngine = PresetEngine.BOX64
    private var pendingImportEngine: PresetEngine? = null

    private val importPresetPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val engine = pendingImportEngine
            pendingImportEngine = null
            if (uri == null || engine == null || !isAdded) {
                return@registerForActivityResult
            }
            runCatching {
                requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    when (engine) {
                        PresetEngine.BOX64 -> {
                            Box64PresetManager.importPreset("box64", requireContext(), stream)
                        }

                        PresetEngine.FEXCORE -> {
                            FEXCorePresetManager.importPreset(requireContext(), stream)
                        }
                    }
                } ?: error("Missing input stream")
            }.onSuccess {
                refresh(selectLatestPreset = true)
            }.onFailure {
                AppUtils.showToast(requireContext(), R.string.container_presets_unable_to_import)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        currentEngine = savedInstanceState
            ?.getString(STATE_ENGINE)
            ?.let { name -> PresetEngine.values().firstOrNull { it.name == name } }
            ?: PresetEngine.BOX64

        PresetEngine.values().forEach { engine ->
            selectedPresetIds[engine] =
                preferences.getString(engine.preferenceKey, engine.defaultPresetId)
                    ?: engine.defaultPresetId
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        refresh()

        return ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WinNativeTheme(
                    colorScheme =
                        darkColorScheme(
                            primary = Color(0xFF1A9FFF),
                            background = Color(0xFF141B24),
                            surface = Color(0xFF1E252E),
                        ),
                ) {
                    PresetsScreen(
                        state = presetsState,
                        onEngineSelected = { engine ->
                            if (engine != currentEngine) {
                                currentEngine = engine
                                refresh()
                            }
                        },
                        onPresetSelected = { presetId ->
                            setSelectedPreset(currentEngine, presetId)
                            refresh()
                        },
                        onEnvVarValueChanged = { name, value ->
                            onEnvVarValueChanged(name, value)
                        },
                        onCreatePreset = { rawName ->
                            val sanitized = sanitizePresetName(rawName)
                            if (sanitized.isEmpty()) return@PresetsScreen
                            savePreset(
                                engine = currentEngine,
                                presetId = null,
                                presetName = sanitized,
                                envVars = buildEnvVarsFromMap(currentEngine, buildDefaultValueMap(currentEngine)),
                            )
                            refresh(selectLatestPreset = true)
                        },
                        onRenamePreset = { rawName ->
                            val selected = selectedPresetOption() ?: return@PresetsScreen
                            if (!selected.isCustom) {
                                AppUtils.showToast(requireContext(), R.string.container_presets_cannot_rename)
                                return@PresetsScreen
                            }
                            val sanitized = sanitizePresetName(rawName)
                            if (sanitized.isEmpty()) return@PresetsScreen
                            savePreset(
                                engine = currentEngine,
                                presetId = selected.id,
                                presetName = sanitized,
                                envVars =
                                    buildEnvVarsFromMap(
                                        currentEngine,
                                        currentValues[currentEngine] ?: linkedMapOf(),
                                    ),
                            )
                            refresh()
                        },
                        onDuplicatePreset = {
                            val selected = selectedPresetOption() ?: return@PresetsScreen
                            when (currentEngine) {
                                PresetEngine.BOX64 -> {
                                    Box64PresetManager.duplicatePreset(
                                        "box64",
                                        requireContext(),
                                        selected.id,
                                    )
                                }

                                PresetEngine.FEXCORE -> {
                                    FEXCorePresetManager.duplicatePreset(
                                        requireContext(),
                                        selected.id,
                                    )
                                }
                            }
                            refresh(selectLatestPreset = true)
                        },
                        onExportPreset = {
                            val selected = selectedPresetOption() ?: return@PresetsScreen
                            if (!selected.isCustom) {
                                AppUtils.showToast(requireContext(), R.string.container_presets_cannot_export)
                                return@PresetsScreen
                            }
                            when (currentEngine) {
                                PresetEngine.BOX64 -> {
                                    Box64PresetManager.exportPreset(
                                        "box64",
                                        requireContext(),
                                        selected.id,
                                    )
                                }

                                PresetEngine.FEXCORE -> {
                                    FEXCorePresetManager.exportPreset(
                                        requireContext(),
                                        selected.id,
                                    )
                                }
                            }
                        },
                        onImportPreset = {
                            pendingImportEngine = currentEngine
                            importPresetPicker.launch(arrayOf("*/*"))
                        },
                        onRemovePreset = {
                            val selected = selectedPresetOption() ?: return@PresetsScreen
                            if (!selected.isCustom) {
                                AppUtils.showToast(requireContext(), R.string.container_presets_cannot_remove)
                                return@PresetsScreen
                            }
                            when (currentEngine) {
                                PresetEngine.BOX64 -> {
                                    Box64PresetManager.removePreset(
                                        "box64",
                                        requireContext(),
                                        selected.id,
                                    )
                                }

                                PresetEngine.FEXCORE -> {
                                    FEXCorePresetManager.removePreset(
                                        requireContext(),
                                        selected.id,
                                    )
                                }
                            }
                            refresh()
                        },
                        suggestedNewPresetName = { buildDefaultPresetName(currentEngine) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.container_presets_title)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ENGINE, currentEngine.name)
        super.onSaveInstanceState(outState)
    }

    // ========================================================================
    // State builders
    // ========================================================================

    /**
     * Re-loads each engine's presets + resolved selection + env-var values from the
     * managers and publishes a new [PresetsState] covering both engines. The
     * `selectLatestPreset` flag only applies to the current engine — callers use
     * it after an import/duplicate/create to auto-focus the newly-added preset.
     */
    private fun refresh(selectLatestPreset: Boolean = false) {
        PresetEngine.values().forEach { engine ->
            val presets = loadPresets(engine)
            val resolvedId =
                resolveSelectedPresetId(
                    engine = engine,
                    presets = presets,
                    preferredPresetId = selectedPresetIds[engine],
                    selectLatestPreset = selectLatestPreset && engine == currentEngine,
                )
            setSelectedPreset(engine, resolvedId)
            loadCurrentValues(engine, resolvedId)
        }
        publishState()
    }

    /**
     * Rebuilds [PresetsState] from the in-memory maps without re-reading from
     * SharedPrefs. Use this on the fast path (env-var edits) where we just
     * mutated `currentValues` and don't need to round-trip through the managers.
     */
    private fun publishState() {
        val engines =
            PresetEngine.values().associateWith { engine ->
                val presets = loadPresets(engine)
                val id = selectedPresetIds[engine] ?: engine.defaultPresetId
                val selected = presets.firstOrNull { it.id == id }
                PresetEngineData(
                    presets = presets,
                    selectedPresetId = id,
                    envVarDefinitions = loadEnvVarDefinitions(engine),
                    currentValues = (currentValues[engine] ?: linkedMapOf()).toMap(),
                    editable = selected?.isCustom == true,
                )
            }
        presetsState =
            PresetsState(
                currentEngine = currentEngine,
                engines = engines,
            )
    }

    private fun resolveSelectedPresetId(
        engine: PresetEngine,
        presets: List<PresetOption>,
        preferredPresetId: String?,
        selectLatestPreset: Boolean,
    ): String {
        if (presets.isEmpty()) {
            return engine.defaultPresetId
        }
        val candidateId =
            if (selectLatestPreset) {
                presets.lastOrNull()?.id
            } else {
                preferredPresetId
            }
        return candidateId
            ?.takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull { it.id == engine.defaultPresetId }?.id
            ?: presets.first().id
    }

    private fun loadPresets(engine: PresetEngine): List<PresetOption> {
        val ctx = requireContext()
        return when (engine) {
            PresetEngine.BOX64 -> {
                Box64PresetManager.getPresets("box64", ctx).map {
                    PresetOption(id = it.id, name = it.name, isCustom = it.isCustom())
                }
            }

            PresetEngine.FEXCORE -> {
                FEXCorePresetManager.getPresets(ctx).map {
                    PresetOption(id = it.id, name = it.name, isCustom = it.isCustom())
                }
            }
        }
    }

    private fun loadCurrentValues(
        engine: PresetEngine,
        presetId: String,
    ) {
        val map = currentValues.getOrPut(engine) { linkedMapOf() }
        map.clear()
        val envVars =
            when (engine) {
                PresetEngine.BOX64 -> Box64PresetManager.getEnvVars("box64", requireContext(), presetId)
                PresetEngine.FEXCORE -> FEXCorePresetManager.getEnvVars(requireContext(), presetId)
            }
        loadEnvVarDefinitions(engine).forEach { definition ->
            map[definition.name] = envVars.get(definition.name).ifBlank { definition.defaultValue }
        }
    }

    private fun loadEnvVarDefinitions(engine: PresetEngine): List<EnvVarDefinition> =
        definitionsCache.getOrPut(engine) {
            val jsonText = FileUtils.readString(requireContext(), engine.assetFile).orEmpty()
            val jsonArray = JSONArray(jsonText.ifBlank { "[]" })
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.isBlank()) continue

                    val values =
                        buildList {
                            val jsonValues = item.optJSONArray("values") ?: JSONArray()
                            for (valueIndex in 0 until jsonValues.length()) {
                                add(jsonValues.optString(valueIndex))
                            }
                        }

                    val fullDescription = buildFullDescription(engine, name)
                    add(
                        EnvVarDefinition(
                            name = name,
                            defaultValue = item.optString("defaultValue"),
                            values = values,
                            controlType =
                                when {
                                    item.optBoolean("toggleSwitch") || item.optBoolean("toggleswitch") -> PresetControlType.TOGGLE
                                    item.optBoolean("editText") -> PresetControlType.TEXT
                                    else -> PresetControlType.DROPDOWN
                                },
                            summary = summarizeDescription(fullDescription),
                            fullDescription = fullDescription,
                        ),
                    )
                }
            }
        }

    private fun buildFullDescription(
        engine: PresetEngine,
        envVarName: String,
    ): String {
        val suffix =
            when (engine) {
                PresetEngine.BOX64 -> envVarName.removePrefix("BOX64_").lowercase(Locale.ENGLISH)
                PresetEngine.FEXCORE -> envVarName.removePrefix("FEX_").lowercase(Locale.ENGLISH)
            }
        return StringUtils.getString(requireContext(), "${engine.helpKeyPrefix}$suffix").orEmpty()
    }

    private fun summarizeDescription(fullDescription: String): String {
        if (fullDescription.isBlank()) {
            return getString(R.string.container_presets_no_description)
        }
        val plainText =
            HtmlCompat
                .fromHtml(fullDescription, HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        return plainText.ifBlank { getString(R.string.container_presets_no_description) }
    }

    // ========================================================================
    // Persistence helpers
    // ========================================================================

    private fun setSelectedPreset(
        engine: PresetEngine,
        presetId: String,
    ) {
        selectedPresetIds[engine] = presetId
        preferences.edit().putString(engine.preferenceKey, presetId).apply()
    }

    private fun selectedPresetOption(): PresetOption? {
        val id = selectedPresetIds[currentEngine] ?: return null
        return loadPresets(currentEngine).firstOrNull { it.id == id }
    }

    private fun onEnvVarValueChanged(
        envVarName: String,
        value: String,
    ) {
        val map = currentValues.getOrPut(currentEngine) { linkedMapOf() }
        map[envVarName] = value
        val selected = selectedPresetOption() ?: return
        if (!selected.isCustom) return

        savePreset(
            engine = currentEngine,
            presetId = selected.id,
            presetName = selected.name,
            envVars = buildEnvVarsFromMap(currentEngine, map),
        )

        // Fast path: rebuild state from in-memory maps so the card reflects the
        // new value immediately without a manager round-trip.
        publishState()
    }

    private fun savePreset(
        engine: PresetEngine,
        presetId: String?,
        presetName: String,
        envVars: EnvVars,
    ) {
        when (engine) {
            PresetEngine.BOX64 -> {
                Box64PresetManager.editPreset(
                    "box64",
                    requireContext(),
                    presetId,
                    presetName,
                    envVars,
                )
            }

            PresetEngine.FEXCORE -> {
                FEXCorePresetManager.editPreset(
                    requireContext(),
                    presetId,
                    presetName,
                    envVars,
                )
            }
        }
    }

    private fun buildDefaultPresetName(engine: PresetEngine): String {
        val nextId =
            when (engine) {
                PresetEngine.BOX64 -> Box64PresetManager.getNextPresetId(requireContext(), "box64")
                PresetEngine.FEXCORE -> FEXCorePresetManager.getNextPresetId(requireContext())
            }
        return "${getString(R.string.container_presets_preset)}-$nextId"
    }

    private fun buildDefaultValueMap(engine: PresetEngine): LinkedHashMap<String, String> {
        val defaults = linkedMapOf<String, String>()
        loadEnvVarDefinitions(engine).forEach { definition ->
            defaults[definition.name] = definition.defaultValue
        }
        return defaults
    }

    private fun buildEnvVarsFromMap(
        engine: PresetEngine,
        values: Map<String, String>,
    ): EnvVars {
        val envVars = EnvVars()
        loadEnvVarDefinitions(engine).forEach { definition ->
            envVars.put(definition.name, values[definition.name] ?: definition.defaultValue)
        }
        return envVars
    }

    private fun sanitizePresetName(rawName: String): String = rawName.trim().replace(Regex("[,|]+"), "")

    // ========================================================================
    // Engine metadata
    // ========================================================================

    private val PresetEngine.preferenceKey: String
        get() =
            when (this) {
                PresetEngine.BOX64 -> "box64_preset"
                PresetEngine.FEXCORE -> "fexcore_preset"
            }

    private val PresetEngine.defaultPresetId: String
        get() =
            when (this) {
                PresetEngine.BOX64 -> Box64Preset.COMPATIBILITY
                PresetEngine.FEXCORE -> FEXCorePreset.INTERMEDIATE
            }

    private val PresetEngine.assetFile: String
        get() =
            when (this) {
                PresetEngine.BOX64 -> AssetPaths.BOX64_ENV_VARS
                PresetEngine.FEXCORE -> AssetPaths.FEXCORE_ENV_VARS
            }

    private val PresetEngine.helpKeyPrefix: String
        get() =
            when (this) {
                PresetEngine.BOX64 -> "box64_env_var_help__"
                PresetEngine.FEXCORE -> "fexcore_env_var_help__"
            }

    companion object {
        private const val STATE_ENGINE = "presets_engine"
    }
}
