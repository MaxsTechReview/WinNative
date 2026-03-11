package com.winlator.cmod

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.winlator.cmod.box64.Box64Preset
import com.winlator.cmod.box64.Box64PresetManager
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.EnvVars
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.databinding.ContentSectionHeaderItemBinding
import com.winlator.cmod.databinding.PresetEnvVarCardBinding
import com.winlator.cmod.databinding.PresetSelectorCardBinding
import com.winlator.cmod.databinding.PresetsFragmentBinding
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.fexcore.FEXCorePresetManager
import org.json.JSONArray
import java.util.Locale

class PresetsFragment : Fragment() {
    private var _binding: PresetsFragmentBinding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var preferences: SharedPreferences
    private lateinit var presetAdapter: PresetRowAdapter

    private val envVarDefinitions = mutableMapOf<PresetCategory, List<EnvVarDefinition>>()
    private val selectedPresetIds = linkedMapOf<PresetCategory, String>()
    private val currentValues = linkedMapOf<String, String>()

    private var currentCategory = PresetCategory.BOX64
    private var pendingImportCategory: PresetCategory? = null

    private val importPresetPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val category = pendingImportCategory
            pendingImportCategory = null
            if (uri == null || category == null || !isAdded) {
                return@registerForActivityResult
            }

            runCatching {
                requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    importPreset(category, stream)
                } ?: error("Missing input stream")
            }.onSuccess {
                refreshRows(selectLatestPreset = true)
            }.onFailure {
                AppUtils.showToast(requireContext(), R.string.unable_to_import_preset)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        currentCategory = savedInstanceState
            ?.getString(STATE_CATEGORY)
            ?.let { value -> PresetCategory.values().firstOrNull { it.name == value } }
            ?: PresetCategory.BOX64

        PresetCategory.values().forEach { category ->
            selectedPresetIds[category] =
                preferences.getString(category.preferenceKey, category.defaultPresetId)
                    ?: category.defaultPresetId
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PresetsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.presets)

        presetAdapter = PresetRowAdapter()
        val gridLayoutManager = GridLayoutManager(requireContext(), GRID_SPAN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    presetAdapter.getSpanSize(position, GRID_SPAN_COUNT)
            }
        }

        binding.RecyclerView.apply {
            layoutManager = gridLayoutManager
            adapter = presetAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            isFocusable = true
            isFocusableInTouchMode = true
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (e.action == MotionEvent.ACTION_DOWN) {
                        val focused = rv.findFocus()
                        if (focused is android.widget.EditText) {
                            rv.requestFocus()
                            val imm = rv.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.hideSoftInputFromWindow(rv.windowToken, 0)
                        }
                    }
                    return false
                }
            })
        }

        refreshRows()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, currentCategory.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.RecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun selectCategory(category: PresetCategory) {
        currentCategory = category
        refreshRows(animated = true)
    }

    private fun refreshRows(
        selectLatestPreset: Boolean = false,
        animated: Boolean = false
    ) {
        val presets = loadPresets(currentCategory)
        val selectedPresetId = resolveSelectedPresetId(
            category = currentCategory,
            presets = presets,
            preferredPresetId = selectedPresetIds[currentCategory],
            selectLatestPreset = selectLatestPreset
        )
        setSelectedPreset(currentCategory, selectedPresetId)
        loadCurrentValues(currentCategory, selectedPresetId)

        val rows = buildRows(
            category = currentCategory,
            presets = presets,
            selectedPresetId = selectedPresetId
        )

        if (animated && binding.root.isLaidOut) {
            applyRows(rows)
            binding.RecyclerView.post { fadeInVisibleVariableRows() }
        } else {
            applyRows(rows)
        }
    }

    private fun applyRows(rows: List<PresetRow>) {
        binding.RecyclerView.isVisible = rows.isNotEmpty()
        binding.TVEmptyText.isVisible = rows.isEmpty()
        presetAdapter.submitRows(rows)
    }

    private fun fadeInVisibleVariableRows() {
        visibleVariableItemViews().forEach { view ->
            view.alpha = VARIABLE_FADE_START_ALPHA
            view.animate().cancel()
            view.animate()
                .alpha(1.0f)
                .setDuration(CONTENT_FADE_IN_DURATION_MS)
                .start()
        }
    }

    private fun visibleVariableItemViews(): List<View> {
        val recyclerView = binding.RecyclerView
        return buildList {
            for (index in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(index)
                val holder = recyclerView.getChildViewHolder(child)
                if (holder.itemViewType == VIEW_TYPE_VARIABLE) {
                    add(child)
                }
            }
        }
    }

    private fun resolveSelectedPresetId(
        category: PresetCategory,
        presets: List<PresetOption>,
        preferredPresetId: String?,
        selectLatestPreset: Boolean
    ): String {
        if (presets.isEmpty()) {
            return category.defaultPresetId
        }

        val candidateId = if (selectLatestPreset) {
            presets.lastOrNull()?.id
        } else {
            preferredPresetId
        }

        return candidateId
            ?.takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull { it.id == category.defaultPresetId }?.id
            ?: presets.first().id
    }

    private fun buildRows(
        category: PresetCategory,
        presets: List<PresetOption>,
        selectedPresetId: String
    ): List<PresetRow> {
        val definitions = loadEnvVarDefinitions(category)
        val selectedPreset = presets.firstOrNull { it.id == selectedPresetId }
        val editable = selectedPreset?.isCustom == true

        return buildList {
            add(
                PresetRow.Selector(
                    category = category,
                    presets = presets,
                    selectedPresetId = selectedPresetId,
                    editable = editable
                )
            )
            add(PresetRow.Header(R.string.environment_variables))

            if (definitions.isEmpty()) {
                add(PresetRow.Empty(getString(R.string.no_items_to_display)))
            } else {
                definitions.forEach { definition ->
                    add(
                        PresetRow.Variable(
                            definition = definition,
                            value = currentValues[definition.name] ?: definition.defaultValue,
                            editable = editable
                        )
                    )
                }
            }
        }
    }

    private fun loadCurrentValues(category: PresetCategory, presetId: String) {
        currentValues.clear()
        val presetEnvVars = loadEnvVars(category, presetId)
        loadEnvVarDefinitions(category).forEach { definition ->
            currentValues[definition.name] =
                presetEnvVars.get(definition.name).ifBlank { definition.defaultValue }
        }
    }

    private fun loadEnvVarDefinitions(category: PresetCategory): List<EnvVarDefinition> {
        return envVarDefinitions.getOrPut(category) {
            val jsonText = FileUtils.readString(requireContext(), category.assetFile).orEmpty()
            val jsonArray = JSONArray(jsonText.ifBlank { "[]" })
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.isBlank()) {
                        continue
                    }

                    val values = buildList {
                        val jsonValues = item.optJSONArray("values") ?: JSONArray()
                        for (valueIndex in 0 until jsonValues.length()) {
                            add(jsonValues.optString(valueIndex))
                        }
                    }

                    val fullDescription = buildFullDescription(category, name)
                    add(
                        EnvVarDefinition(
                            name = name,
                            defaultValue = item.optString("defaultValue"),
                            values = values,
                            controlType = when {
                                item.optBoolean("toggleSwitch") || item.optBoolean("toggleswitch") -> ControlType.TOGGLE
                                item.optBoolean("editText") -> ControlType.TEXT
                                else -> ControlType.DROPDOWN
                            },
                            summary = summarizeDescription(fullDescription),
                            fullDescription = fullDescription
                        )
                    )
                }
            }
        }
    }

    private fun buildFullDescription(category: PresetCategory, envVarName: String): String {
        val suffix = when (category) {
            PresetCategory.BOX64 -> envVarName
                .removePrefix("BOX64_")
                .lowercase(Locale.ENGLISH)

            PresetCategory.FEXCORE -> envVarName
                .removePrefix("FEX_")
                .lowercase(Locale.ENGLISH)
        }

        return StringUtils.getString(
            requireContext(),
            "${category.helpKeyPrefix}$suffix"
        ).orEmpty()
    }

    private fun summarizeDescription(fullDescription: String): String {
        if (fullDescription.isBlank()) {
            return getString(R.string.no_description_available)
        }

        val plainText = HtmlCompat.fromHtml(
            fullDescription,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return plainText.ifBlank { getString(R.string.no_description_available) }
    }

    private fun loadPresets(category: PresetCategory): List<PresetOption> {
        val context = requireContext()
        return when (category) {
            PresetCategory.BOX64 -> Box64PresetManager.getPresets("box64", context).map { preset ->
                PresetOption(preset.id, preset.name, preset.isCustom())
            }

            PresetCategory.FEXCORE -> FEXCorePresetManager.getPresets(context).map { preset ->
                PresetOption(preset.id, preset.name, preset.isCustom())
            }
        }
    }

    private fun loadEnvVars(category: PresetCategory, presetId: String): EnvVars {
        val context = requireContext()
        return when (category) {
            PresetCategory.BOX64 -> Box64PresetManager.getEnvVars("box64", context, presetId)
            PresetCategory.FEXCORE -> FEXCorePresetManager.getEnvVars(context, presetId)
        }
    }

    private fun setSelectedPreset(category: PresetCategory, presetId: String) {
        selectedPresetIds[category] = presetId
        preferences.edit().putString(category.preferenceKey, presetId).apply()
    }

    private fun createPreset(category: PresetCategory) {
        val defaultName = buildDefaultPresetName(category)
        ContentDialog.prompt(
            requireContext(),
            R.string.new_preset,
            defaultName
        ) { rawName ->
            val sanitizedName = sanitizePresetName(rawName)
            if (sanitizedName.isEmpty()) {
                return@prompt
            }

            savePreset(
                category = category,
                presetId = null,
                presetName = sanitizedName,
                envVars = buildEnvVarsFromMap(category, buildDefaultValueMap(category))
            )
            refreshRows(selectLatestPreset = true)
        }
    }

    private fun renameCurrentPreset() {
        val currentPreset = currentPresetOption() ?: return
        if (!currentPreset.isCustom) {
            AppUtils.showToast(requireContext(), R.string.cannot_rename_this_preset)
            return
        }

        ContentDialog.prompt(
            requireContext(),
            R.string.rename_preset,
            currentPreset.name
        ) { rawName ->
            val sanitizedName = sanitizePresetName(rawName)
            if (sanitizedName.isEmpty()) {
                return@prompt
            }

            savePreset(
                category = currentCategory,
                presetId = currentPreset.id,
                presetName = sanitizedName,
                envVars = buildEnvVarsFromMap(currentCategory, currentValues)
            )
            refreshRows()
        }
    }

    private fun duplicateCurrentPreset() {
        val currentPreset = currentPresetOption() ?: return
        ContentDialog.confirm(
            requireContext(),
            R.string.do_you_want_to_duplicate_this_preset
        ) {
            when (currentCategory) {
                PresetCategory.BOX64 -> Box64PresetManager.duplicatePreset(
                    "box64",
                    requireContext(),
                    currentPreset.id
                )

                PresetCategory.FEXCORE -> FEXCorePresetManager.duplicatePreset(
                    requireContext(),
                    currentPreset.id
                )
            }
            refreshRows(selectLatestPreset = true)
        }
    }

    private fun removeCurrentPreset() {
        val currentPreset = currentPresetOption() ?: return
        if (!currentPreset.isCustom) {
            AppUtils.showToast(requireContext(), R.string.you_cannot_remove_this_preset)
            return
        }

        ContentDialog.confirm(
            requireContext(),
            R.string.do_you_want_to_remove_this_preset
        ) {
            when (currentCategory) {
                PresetCategory.BOX64 -> Box64PresetManager.removePreset(
                    "box64",
                    requireContext(),
                    currentPreset.id
                )

                PresetCategory.FEXCORE -> FEXCorePresetManager.removePreset(
                    requireContext(),
                    currentPreset.id
                )
            }
            refreshRows()
        }
    }

    private fun exportCurrentPreset() {
        val currentPreset = currentPresetOption() ?: return
        if (!currentPreset.isCustom) {
            AppUtils.showToast(requireContext(), R.string.cannot_export_this_preset)
            return
        }

        when (currentCategory) {
            PresetCategory.BOX64 -> Box64PresetManager.exportPreset(
                "box64",
                requireContext(),
                currentPreset.id
            )

            PresetCategory.FEXCORE -> FEXCorePresetManager.exportPreset(
                requireContext(),
                currentPreset.id
            )
        }
    }

    private fun importPreset(category: PresetCategory, stream: java.io.InputStream) {
        when (category) {
            PresetCategory.BOX64 -> Box64PresetManager.importPreset("box64", requireContext(), stream)
            PresetCategory.FEXCORE -> FEXCorePresetManager.importPreset(requireContext(), stream)
        }
    }

    private fun savePreset(
        category: PresetCategory,
        presetId: String?,
        presetName: String,
        envVars: EnvVars
    ) {
        when (category) {
            PresetCategory.BOX64 -> Box64PresetManager.editPreset(
                "box64",
                requireContext(),
                presetId,
                presetName,
                envVars
            )

            PresetCategory.FEXCORE -> FEXCorePresetManager.editPreset(
                requireContext(),
                presetId,
                presetName,
                envVars
            )
        }
    }

    private fun buildDefaultPresetName(category: PresetCategory): String {
        val nextId = when (category) {
            PresetCategory.BOX64 -> Box64PresetManager.getNextPresetId(requireContext(), "box64")
            PresetCategory.FEXCORE -> FEXCorePresetManager.getNextPresetId(requireContext())
        }
        return "${getString(R.string.preset)}-$nextId"
    }

    private fun buildDefaultValueMap(category: PresetCategory): LinkedHashMap<String, String> {
        val defaults = linkedMapOf<String, String>()
        loadEnvVarDefinitions(category).forEach { definition ->
            defaults[definition.name] = definition.defaultValue
        }
        return defaults
    }

    private fun buildEnvVarsFromMap(
        category: PresetCategory,
        values: Map<String, String>
    ): EnvVars {
        val envVars = EnvVars()
        loadEnvVarDefinitions(category).forEach { definition ->
            envVars.put(definition.name, values[definition.name] ?: definition.defaultValue)
        }
        return envVars
    }

    private fun sanitizePresetName(rawName: String): String {
        return rawName.trim().replace(Regex("[,|]+"), "")
    }

    private fun currentPresetOption(): PresetOption? {
        val selectedPresetId = selectedPresetIds[currentCategory] ?: return null
        return loadPresets(currentCategory).firstOrNull { it.id == selectedPresetId }
    }

    private fun onPresetSelectionChanged(presetId: String) {
        setSelectedPreset(currentCategory, presetId)
        refreshRows()
    }

    private fun onEnvVarValueChanged(envVarName: String, value: String) {
        currentValues[envVarName] = value

        val currentPreset = currentPresetOption() ?: return
        if (!currentPreset.isCustom) {
            return
        }

        savePreset(
            category = currentCategory,
            presetId = currentPreset.id,
            presetName = currentPreset.name,
            envVars = buildEnvVarsFromMap(currentCategory, currentValues)
        )
    }

    private fun showPresetActions(anchor: View) {
        val currentPreset = currentPresetOption() ?: return
        val popupContext = ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_ContentPopupMenu)
        val popupMenu = PopupMenu(
            popupContext,
            anchor,
            Gravity.END,
            0,
            R.style.Widget_ContentPopupMenu
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setForceShowIcon(true)
        }
        popupMenu.menuInflater.inflate(R.menu.preset_popup_menu, popupMenu.menu)
        popupMenu.menu.findItem(R.id.action_rename_preset).isVisible = currentPreset.isCustom
        popupMenu.menu.findItem(R.id.action_export_preset).isVisible = currentPreset.isCustom
        popupMenu.menu.findItem(R.id.action_remove_preset).isVisible = currentPreset.isCustom
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename_preset -> {
                    renameCurrentPreset()
                    true
                }

                R.id.action_duplicate_preset -> {
                    duplicateCurrentPreset()
                    true
                }

                R.id.action_export_preset -> {
                    exportCurrentPreset()
                    true
                }

                R.id.action_import_preset -> {
                    pendingImportCategory = currentCategory
                    importPresetPicker.launch(arrayOf("*/*"))
                    true
                }

                R.id.action_remove_preset -> {
                    removeCurrentPreset()
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private inner class PresetRowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val rows = mutableListOf<PresetRow>()

        fun submitRows(newRows: List<PresetRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        fun getSpanSize(position: Int, fullSpan: Int): Int {
            return when (rows.getOrNull(position)) {
                is PresetRow.Variable -> 1
                else -> fullSpan
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                is PresetRow.Selector -> VIEW_TYPE_SELECTOR
                is PresetRow.Header -> VIEW_TYPE_HEADER
                is PresetRow.Variable -> VIEW_TYPE_VARIABLE
                is PresetRow.Empty -> VIEW_TYPE_EMPTY
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_SELECTOR -> SelectorViewHolder(
                    PresetSelectorCardBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )

                VIEW_TYPE_HEADER -> HeaderViewHolder(
                    ContentSectionHeaderItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )

                VIEW_TYPE_VARIABLE -> VariableViewHolder(
                    PresetEnvVarCardBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )

                else -> EmptyViewHolder(createEmptyView(parent.context))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is PresetRow.Selector -> (holder as SelectorViewHolder).bind(row)
                is PresetRow.Header -> (holder as HeaderViewHolder).bind(row)
                is PresetRow.Variable -> (holder as VariableViewHolder).bind(row)
                is PresetRow.Empty -> (holder as EmptyViewHolder).bind(row)
            }
        }
    }

    private inner class SelectorViewHolder(
        private val itemBinding: PresetSelectorCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(row: PresetRow.Selector) {
            bindCategoryTabs(row.category)
            itemBinding.TVPresetType.text = getString(
                if (row.editable) R.string.custom_preset else R.string.built_in_preset
            )
            itemBinding.TVSelectorHint.text = getString(
                if (row.editable) {
                    R.string.preset_changes_auto_saved
                } else {
                    R.string.preset_builtin_readonly_hint
                }
            )

            val spinnerAdapter = ArrayAdapter(
                itemBinding.root.context,
                R.layout.spinner_item_themed,
                row.presets.map { it.name }
            ).apply {
                setDropDownViewResource(R.layout.spinner_dropdown_item_themed)
            }

            itemBinding.SPresetSelector.onItemSelectedListener = null
            itemBinding.SPresetSelector.adapter = spinnerAdapter
            val selectedIndex = row.presets.indexOfFirst { it.id == row.selectedPresetId }
                .coerceAtLeast(0)
            itemBinding.SPresetSelector.setSelection(selectedIndex, false)
            itemBinding.SPresetSelector.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selectedPreset = row.presets.getOrNull(position) ?: return
                        if (selectedPreset.id != row.selectedPresetId) {
                            onPresetSelectionChanged(selectedPreset.id)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }

            itemBinding.BTNewPreset.setOnClickListener {
                createPreset(row.category)
            }
            itemBinding.BTMoreActions.setOnClickListener {
                showPresetActions(it)
            }
        }

        private fun bindCategoryTabs(selectedCategory: PresetCategory) {
            itemBinding.LLCategoryTabs.removeAllViews()
            val inflater = LayoutInflater.from(itemBinding.root.context)
            var selectedTab: TextView? = null

            PresetCategory.values().forEach { category ->
                val tab = inflater.inflate(
                    R.layout.content_type_tab_item,
                    itemBinding.LLCategoryTabs,
                    false
                ) as TextView
                tab.text = getString(category.labelRes)
                tab.isSelected = category == selectedCategory
                tab.setOnClickListener {
                    if (category != currentCategory) {
                        selectCategory(category)
                    }
                }
                if (tab.isSelected) {
                    selectedTab = tab
                }
                itemBinding.LLCategoryTabs.addView(tab)
            }

            val targetTab = selectedTab ?: return
            itemBinding.HSVCategoryNav.post {
                val scrollX =
                    (targetTab.left - (itemBinding.HSVCategoryNav.width - targetTab.width) / 2)
                        .coerceAtLeast(0)
                itemBinding.HSVCategoryNav.smoothScrollTo(scrollX, 0)
            }
        }
    }

    private inner class HeaderViewHolder(
        private val itemBinding: ContentSectionHeaderItemBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(row: PresetRow.Header) {
            itemBinding.TVSectionTitle.setText(row.titleResId)
        }
    }

    private inner class VariableViewHolder(
        private val itemBinding: PresetEnvVarCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        private var activeTextWatcher: TextWatcher? = null

        fun bind(row: PresetRow.Variable) {
            itemBinding.root.animate().cancel()
            itemBinding.root.alpha = 1.0f
            itemBinding.TVVarName.text = row.definition.name
            itemBinding.TVVarDescription.text = row.definition.summary

            itemBinding.SWToggleControl.isVisible = row.definition.controlType == ControlType.TOGGLE
            itemBinding.SValueControl.isVisible = row.definition.controlType == ControlType.DROPDOWN
            itemBinding.ETValueControl.isVisible = row.definition.controlType == ControlType.TEXT

            itemBinding.SWToggleControl.alpha = if (row.editable) 1.0f else 0.55f
            itemBinding.SValueControl.alpha = if (row.editable) 1.0f else 0.55f
            itemBinding.ETValueControl.alpha = if (row.editable) 1.0f else 0.55f

            when (row.definition.controlType) {
                ControlType.TOGGLE -> bindToggle(row)
                ControlType.DROPDOWN -> bindDropdown(row)
                ControlType.TEXT -> bindEditText(row)
            }
        }

        private fun bindToggle(row: PresetRow.Variable) {
            itemBinding.SWToggleControl.setOnCheckedChangeListener(null)
            itemBinding.SWToggleControl.isEnabled = row.editable
            itemBinding.SWToggleControl.isChecked = row.value == "1"
            itemBinding.SWToggleControl.setOnCheckedChangeListener { _, isChecked ->
                if (row.editable) {
                    onEnvVarValueChanged(row.definition.name, if (isChecked) "1" else "0")
                }
            }
        }

        private fun bindDropdown(row: PresetRow.Variable) {
            val adapter = ArrayAdapter(
                itemBinding.root.context,
                R.layout.spinner_item_themed,
                row.definition.values
            ).apply {
                setDropDownViewResource(R.layout.spinner_dropdown_item_themed)
            }

            itemBinding.SValueControl.onItemSelectedListener = null
            itemBinding.SValueControl.isEnabled = row.editable
            itemBinding.SValueControl.adapter = adapter
            val selectedIndex = row.definition.values.indexOf(row.value).coerceAtLeast(0)
            itemBinding.SValueControl.setSelection(selectedIndex, false)
            itemBinding.SValueControl.tag = row.value
            itemBinding.SValueControl.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (!row.editable) {
                            return
                        }

                        val selectedValue = row.definition.values.getOrNull(position) ?: return
                        val previousValue = itemBinding.SValueControl.tag as? String
                        if (selectedValue != previousValue) {
                            itemBinding.SValueControl.tag = selectedValue
                            onEnvVarValueChanged(row.definition.name, selectedValue)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
        }

        private fun bindEditText(row: PresetRow.Variable) {
            activeTextWatcher?.let(itemBinding.ETValueControl::removeTextChangedListener)
            itemBinding.ETValueControl.isEnabled = row.editable
            itemBinding.ETValueControl.setText(row.value)
            activeTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (row.editable) {
                        onEnvVarValueChanged(row.definition.name, s?.toString().orEmpty())
                    }
                }
            }
            itemBinding.ETValueControl.addTextChangedListener(activeTextWatcher)
        }
    }

    private class EmptyViewHolder(
        private val textView: TextView
    ) : RecyclerView.ViewHolder(textView) {
        fun bind(row: PresetRow.Empty) {
            textView.text = row.message
        }
    }

    private fun createEmptyView(context: Context): TextView {
        return TextView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val horizontal = (resources.displayMetrics.density * 24).toInt()
            val vertical = (resources.displayMetrics.density * 18).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            setTextColor(context.getColor(R.color.settings_text_secondary))
            textSize = 14f
            gravity = Gravity.CENTER
        }
    }

    private enum class PresetCategory(
        val labelRes: Int,
        val preferenceKey: String,
        val defaultPresetId: String,
        val assetFile: String,
        val helpKeyPrefix: String
    ) {
        BOX64(
            labelRes = R.string.box64,
            preferenceKey = "box64_preset",
            defaultPresetId = Box64Preset.COMPATIBILITY,
            assetFile = "box64_env_vars.json",
            helpKeyPrefix = "box64_env_var_help__"
        ),
        FEXCORE(
            labelRes = R.string.fexcore_config,
            preferenceKey = "fexcore_preset",
            defaultPresetId = FEXCorePreset.INTERMEDIATE,
            assetFile = "fexcore_env_vars.json",
            helpKeyPrefix = "fexcore_env_var_help__"
        )
    }

    private enum class ControlType {
        TOGGLE,
        DROPDOWN,
        TEXT
    }

    private data class EnvVarDefinition(
        val name: String,
        val defaultValue: String,
        val values: List<String>,
        val controlType: ControlType,
        val summary: String,
        val fullDescription: String
    )

    private data class PresetOption(
        val id: String,
        val name: String,
        val isCustom: Boolean
    )

    private sealed interface PresetRow {
        data class Selector(
            val category: PresetCategory,
            val presets: List<PresetOption>,
            val selectedPresetId: String,
            val editable: Boolean
        ) : PresetRow

        data class Header(val titleResId: Int) : PresetRow

        data class Variable(
            val definition: EnvVarDefinition,
            val value: String,
            val editable: Boolean
        ) : PresetRow

        data class Empty(val message: String) : PresetRow
    }

    companion object {
        private const val GRID_SPAN_COUNT = 3
        private const val STATE_CATEGORY = "presets_category"
        private const val CONTENT_FADE_IN_DURATION_MS = 350L
        private const val VARIABLE_FADE_START_ALPHA = 0.35f

        private const val VIEW_TYPE_SELECTOR = 0
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_VARIABLE = 2
        private const val VIEW_TYPE_EMPTY = 3
    }
}
