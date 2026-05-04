package com.winlator.cmod.feature.stores.steam.utils
import timber.log.Timber
import java.nio.file.Files
import java.nio.file.Path

object SteamControllerVdfUtils {
    const val WINNATIVE_ACTION_MAP_FILE_NAME = "winnative_steaminput_actions.txt"

    private data class InferredBinding(
        val binding: String,
        val sourceMode: String? = null,
        val isAnalog: Boolean = false,
        val usedFallback: Boolean = false,
    )

    private val keymapDigital =
        mapOf(
            "button_a" to "A",
            "button_b" to "B",
            "button_x" to "X",
            "button_y" to "Y",
            "dpad_north" to "DUP",
            "dpad_south" to "DDOWN",
            "dpad_east" to "DRIGHT",
            "dpad_west" to "DLEFT",
            "button_escape" to "START",
            "button_menu" to "BACK",
            "left_bumper" to "LBUMPER",
            "right_bumper" to "RBUMPER",
            "button_back_left" to "A",
            "button_back_right" to "X",
            "button_back_left_upper" to "B",
            "button_back_right_upper" to "Y",
        )

    private val fallbackDigitalBindings =
        listOf("A", "B", "X", "Y", "LBUMPER", "RBUMPER", "BACK", "START")

    private val genericFallbackActionSets =
        listOf("default", "InGameControls", "Gameplay", "MenuControls", "Menu")

    fun generateControllerConfig(
        controllerVdfText: String,
        outputDir: Path,
    ): Boolean {
        val root = VdfParser(controllerVdfText).parse()
        val controllerMappings = root.getObjectIgnoreCase("controller_mappings")
        if (controllerMappings != null) {
            return generateControllerMappingsConfig(controllerMappings, outputDir)
        }

        val actionManifest =
            root.getObjectIgnoreCase("Action Manifest")
                ?: root.takeIf { it.getObjectIgnoreCase("actions") != null }
                ?: return false

        return generateActionManifestConfig(actionManifest, outputDir)
    }

    fun generateFallbackControllerConfig(outputDir: Path): Boolean {
        val bindings =
            linkedMapOf(
                "Move" to mutableListOf("LJOY=joystick_move"),
                "Movement" to mutableListOf("LJOY=joystick_move"),
                "LeftStick" to mutableListOf("LJOY=joystick_move"),
                "JoystickMove" to mutableListOf("LJOY=joystick_move"),
                "Camera" to mutableListOf("RJOY=joystick_camera"),
                "Look" to mutableListOf("RJOY=joystick_camera"),
                "RightStick" to mutableListOf("RJOY=joystick_camera"),
                "JoystickCamera" to mutableListOf("RJOY=joystick_camera"),
                "LeftTrigger" to mutableListOf("LTRIGGER=trigger"),
                "RightTrigger" to mutableListOf("RTRIGGER=trigger"),
                "Accelerate" to mutableListOf("RTRIGGER=trigger"),
                "Brake" to mutableListOf("LTRIGGER=trigger"),
                "Jump" to mutableListOf("A"),
                "Confirm" to mutableListOf("A"),
                "Accept" to mutableListOf("A"),
                "Submit" to mutableListOf("A"),
                "Select" to mutableListOf("A"),
                "Cancel" to mutableListOf("B"),
                "Back" to mutableListOf("B"),
                "Attack" to mutableListOf("X"),
                "Action" to mutableListOf("X"),
                "Boost" to mutableListOf("X"),
                "Dash" to mutableListOf("X"),
                "Special" to mutableListOf("Y"),
                "Item" to mutableListOf("Y"),
                "Pause" to mutableListOf("START"),
                "Start" to mutableListOf("START"),
                "Menu" to mutableListOf("START"),
                "Options" to mutableListOf("START"),
                "View" to mutableListOf("BACK"),
                "Map" to mutableListOf("BACK"),
                "LeftBumper" to mutableListOf("LBUMPER"),
                "RightBumper" to mutableListOf("RBUMPER"),
                "DPadUp" to mutableListOf("DUP"),
                "DPadDown" to mutableListOf("DDOWN"),
                "DPadLeft" to mutableListOf("DLEFT"),
                "DPadRight" to mutableListOf("DRIGHT"),
            )

        val allBindings =
            LinkedHashMap<String, LinkedHashMap<String, MutableList<String>>>()
        genericFallbackActionSets.forEach { actionSet ->
            allBindings[actionSet] = cloneBindings(bindings)
        }

        return writeControllerConfigs(outputDir, allBindings)
    }

    private fun generateControllerMappingsConfig(
        controllerMappings: VdfObject,
        outputDir: Path,
    ): Boolean {

        val groupsById = LinkedHashMap<String, VdfObject>()
        controllerMappings.getObjects("group").forEach { group ->
            group.getString("id")?.let { groupsById[it] = group }
        }

        val actionList = mutableListOf<String>()
        controllerMappings.getObjects("actions").forEach { actions ->
            actionList.addAll(actions.keys())
        }

        val presets = controllerMappings.getObjects("preset")
        val presetsByName =
            presets
                .mapNotNull { preset ->
                    preset.getString("name")?.let { name -> name to preset }
                }.toMap()
        val allBindings = LinkedHashMap<String, LinkedHashMap<String, MutableList<String>>>()

        for (preset in presets) {
            val name = preset.getString("name") ?: continue
            if (!actionList.contains(name) && name.lowercase() != "default") continue

            val bindings = buildPresetBindings(name, preset, groupsById)
            allBindings[name] = bindings
        }

        controllerMappings.getObject("action_layers")?.keys()?.forEach { layerName ->
            val preset = presetsByName[layerName]
            if (preset == null) {
                Timber.tag("SteamControllerVdf").d("Missing preset for action layer $layerName")
                return@forEach
            }
            val bindings = buildPresetBindings(layerName, preset, groupsById)
            allBindings[layerName] = bindings
        }

        if (allBindings.isEmpty()) {
            val bindings = buildRootControllerMappingsBindings(controllerMappings, groupsById)
            if (bindings.isNotEmpty()) {
                allBindings["default"] = bindings
            }
        }

        return writeControllerConfigs(outputDir, allBindings)
    }

    private fun writeControllerConfigs(
        outputDir: Path,
        allBindings: Map<String, LinkedHashMap<String, MutableList<String>>>,
    ): Boolean {
        if (allBindings.isEmpty()) return false

        Files.createDirectories(outputDir)
        for ((presetName, bindings) in allBindings) {
            if (bindings.isEmpty()) continue
            val outputFile = outputDir.resolve("$presetName.txt")
            val content =
                buildString {
                    for ((actionName, actionBindings) in bindings) {
                        append(actionName)
                        append("=")
                        appendLine(actionBindings.joinToString(","))
                    }
                }
            outputFile.toFile().writeText(content, Charsets.UTF_8)
        }
        return writeWinNativeActionMap(outputDir, allBindings)
    }

    private fun cloneBindings(
        bindings: Map<String, MutableList<String>>,
    ): LinkedHashMap<String, MutableList<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        for ((actionName, actionBindings) in bindings) {
            result[actionName] = actionBindings.toMutableList()
        }
        return result
    }

    private fun writeWinNativeActionMap(
        outputDir: Path,
        allBindings: Map<String, LinkedHashMap<String, MutableList<String>>>,
    ): Boolean {
        val settingsDir = outputDir.parent ?: return false
        val actionMapFile = settingsDir.resolve(WINNATIVE_ACTION_MAP_FILE_NAME).toFile()
        val bindings =
            allBindings.entries
                .firstOrNull { it.key.equals("default", ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotEmpty() }
                ?: allBindings.values.firstOrNull { it.isNotEmpty() }

        if (bindings.isNullOrEmpty()) {
            if (actionMapFile.exists()) actionMapFile.delete()
            return false
        }

        val content =
            buildString {
                for ((actionName, actionBindings) in bindings) {
                    val normalizedBindings =
                        actionBindings
                            .map { it.substringBefore("=") }
                            .filter { it.isNotEmpty() }
                            .distinct()
                    if (normalizedBindings.isEmpty()) continue
                    append(actionName)
                    append("=")
                    appendLine(normalizedBindings.joinToString(","))
                }
            }

        if (content.isEmpty()) {
            if (actionMapFile.exists()) actionMapFile.delete()
            return false
        }

        actionMapFile.writeText(content, Charsets.UTF_8)
        return true
    }

    private fun generateActionManifestConfig(
        actionManifest: VdfObject,
        outputDir: Path,
    ): Boolean {
        val actions = actionManifest.getObjectIgnoreCase("actions") ?: return false
        val allBindings = LinkedHashMap<String, LinkedHashMap<String, MutableList<String>>>()

        for ((actionSetName, actionSet) in actions.objectEntries()) {
            val bindings = buildActionManifestSetBindings(actionSet)
            if (bindings.isNotEmpty()) {
                allBindings[actionSetName] = bindings
            }
        }

        if (allBindings.isEmpty()) return false
        addMergedDefaultActionSet(allBindings)
        return writeControllerConfigs(outputDir, allBindings)
    }

    private fun addMergedDefaultActionSet(
        allBindings: LinkedHashMap<String, LinkedHashMap<String, MutableList<String>>>,
    ) {
        if (allBindings.keys.any { it.equals("default", ignoreCase = true) }) return

        val merged = LinkedHashMap<String, MutableList<String>>()
        for (bindings in allBindings.values) {
            for ((actionName, actionBindings) in bindings) {
                val target = merged.getOrPut(actionName) { mutableListOf() }
                for (binding in actionBindings) {
                    if (!target.contains(binding)) {
                        target.add(binding)
                    }
                }
            }
        }

        if (merged.isNotEmpty()) {
            allBindings["default"] = merged
        }
    }

    private fun buildActionManifestSetBindings(
        actionSet: VdfObject,
    ): LinkedHashMap<String, MutableList<String>> {
        val bindings = LinkedHashMap<String, MutableList<String>>()
        var fallbackDigitalIndex = 0
        var fallbackAnalogIndex = 0

        for ((categoryName, category) in actionSet.objectEntries()) {
            if (isManifestMetadataBlock(categoryName)) continue

            for ((actionName, action) in category.objectEntries()) {
                if (isManifestMetadataBlock(actionName)) continue

                val inferred =
                    inferManifestActionBinding(
                        actionName,
                        categoryName,
                        action,
                        fallbackDigitalIndex,
                        fallbackAnalogIndex,
                    ) ?: continue

                addInferredBinding(bindings, actionName, inferred)
                if (inferred.usedFallback) {
                    if (inferred.isAnalog) {
                        fallbackAnalogIndex++
                    } else {
                        fallbackDigitalIndex++
                    }
                }
            }
        }

        return bindings
    }

    private fun isManifestMetadataBlock(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized in
            setOf(
                "title",
                "description",
                "localization",
                "configurations",
                "action_layers",
                "settings",
            )
    }

    private fun addInferredBinding(
        bindings: MutableMap<String, MutableList<String>>,
        actionName: String,
        inferred: InferredBinding,
    ) {
        val actionBindings = bindings.getOrPut(actionName) { mutableListOf() }
        val value =
            if (inferred.sourceMode.isNullOrEmpty()) {
                inferred.binding
            } else {
                "${inferred.binding}=${inferred.sourceMode}"
            }
        if (!actionBindings.contains(value)) {
            actionBindings.add(value)
        }
    }

    private fun inferManifestActionBinding(
        actionName: String,
        categoryName: String,
        action: VdfObject,
        fallbackDigitalIndex: Int,
        fallbackAnalogIndex: Int,
    ): InferredBinding? {
        val inputMode = action.getStringIgnoreCase("input_mode").orEmpty()
        val title = action.getStringIgnoreCase("title").orEmpty()
        val normalizedText = normalizeActionText(actionName, title)
        val normalizedCategory = normalizeActionText(categoryName, inputMode)
        val normalizedInputMode = inputMode.lowercase()

        val analogMode =
            when {
                normalizedInputMode.contains("joystick_camera") -> "joystick_camera"
                normalizedInputMode.contains("camera") -> "joystick_camera"
                normalizedInputMode.contains("mouse") -> "joystick_camera"
                normalizedInputMode.contains("joystick") -> "joystick_move"
                normalizedInputMode.contains("trigger") -> "trigger"
                normalizedCategory.contains("stick") -> "joystick_move"
                normalizedCategory.contains("gyro") -> "joystick_camera"
                normalizedCategory.contains("trigger") -> "trigger"
                else -> null
            }

        if (analogMode != null) {
            return inferAnalogBinding(normalizedText, analogMode, fallbackAnalogIndex)
        }

        return inferDigitalBinding(normalizedText, fallbackDigitalIndex)
    }

    private fun inferAnalogBinding(
        normalizedText: String,
        sourceMode: String,
        fallbackAnalogIndex: Int,
    ): InferredBinding {
        val isLeftTrigger =
            containsAny(normalizedText, "left trigger", "ltrigger", "lt", "l2", "brake", "reverse")
        val isRightTrigger =
            containsAny(normalizedText, "right trigger", "rtrigger", "rt", "r2", "accelerate", "accel", "gas", "throttle")
        if (sourceMode == "trigger" || isLeftTrigger || isRightTrigger) {
            val binding =
                when {
                    isLeftTrigger -> "LTRIGGER"
                    isRightTrigger -> "RTRIGGER"
                    fallbackAnalogIndex % 2 == 0 -> "RTRIGGER"
                    else -> "LTRIGGER"
                }
            return InferredBinding(binding, "trigger", isAnalog = true, usedFallback = !isLeftTrigger && !isRightTrigger)
        }

        val isRightStick =
            sourceMode == "joystick_camera" ||
                containsAny(normalizedText, "camera", "look", "aim", "view", "right stick", "rightstick", "rstick", "rs")
        val isLeftStick =
            containsAny(normalizedText, "move", "movement", "walk", "run", "drive", "steer", "left stick", "leftstick", "lstick", "ls")

        val binding =
            when {
                isRightStick -> "RJOY"
                isLeftStick -> "LJOY"
                fallbackAnalogIndex == 0 -> "LJOY"
                else -> "RJOY"
            }
        val mode = if (binding == "RJOY") "joystick_camera" else "joystick_move"
        return InferredBinding(binding, mode, isAnalog = true, usedFallback = !isRightStick && !isLeftStick)
    }

    private fun inferDigitalBinding(
        normalizedText: String,
        fallbackDigitalIndex: Int,
    ): InferredBinding {
        val isDpadUp = containsAny(normalizedText, "dpad up", "d pad up", "pad up") || normalizedText == "up"
        val isDpadDown = containsAny(normalizedText, "dpad down", "d pad down", "pad down") || normalizedText == "down"
        val isDpadLeft = containsAny(normalizedText, "dpad left", "d pad left", "pad left") || normalizedText == "left"
        val isDpadRight = containsAny(normalizedText, "dpad right", "d pad right", "pad right") || normalizedText == "right"
        val binding =
            when {
                containsAny(normalizedText, "left bumper", "left shoulder", "lb", "l1", "previous", "prev") -> "LBUMPER"
                containsAny(normalizedText, "right bumper", "right shoulder", "rb", "r1", "next") -> "RBUMPER"
                containsAny(normalizedText, "left stick", "lstick", "l3") -> "LSTICK"
                containsAny(normalizedText, "right stick", "rstick", "r3") -> "RSTICK"
                isDpadUp -> "DUP"
                isDpadDown -> "DDOWN"
                isDpadLeft -> "DLEFT"
                isDpadRight -> "DRIGHT"
                containsAny(normalizedText, "pause", "start", "menu", "options") -> "START"
                containsAny(normalizedText, "view", "map", "inventory", "select button", "back button") -> "BACK"
                containsAny(normalizedText, "cancel", "close", "decline", "back", "no") -> "B"
                containsAny(normalizedText, "jump", "confirm", "accept", "submit", "ok", "yes", "interact", "use", "continue", "select") -> "A"
                containsAny(normalizedText, "attack", "shoot", "fire", "boost", "dash", "sprint", "primary", "action") -> "X"
                containsAny(normalizedText, "special", "secondary", "item", "ability", "power", "reset") -> "Y"
                containsAny(normalizedText, "left trigger", "ltrigger", "lt", "l2") -> "DLTRIGGER"
                containsAny(normalizedText, "right trigger", "rtrigger", "rt", "r2") -> "DRTRIGGER"
                else -> fallbackDigitalBindings[fallbackDigitalIndex % fallbackDigitalBindings.size]
            }

        val usedFallback =
            binding == fallbackDigitalBindings[fallbackDigitalIndex % fallbackDigitalBindings.size] &&
                !containsAny(
                    normalizedText,
                    "jump",
                    "confirm",
                    "accept",
                    "submit",
                    "cancel",
                    "attack",
                    "shoot",
                    "fire",
                    "boost",
                    "dash",
                    "special",
                    "pause",
                    "start",
                    "menu",
                    "map",
                    "left",
                    "right",
                    "up",
                    "down",
                )
        return InferredBinding(binding, usedFallback = usedFallback)
    }

    private fun normalizeActionText(vararg values: String): String =
        values
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace('#', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .lowercase()
            .trim()

    private fun containsAny(
        text: String,
        vararg needles: String,
    ): Boolean = needles.any { text.contains(it) }

    private fun addInputBindings(
        group: VdfObject,
        bindings: MutableMap<String, MutableList<String>>,
        forceBinding: String? = null,
        keymap: Map<String, String> = keymapDigital,
    ) {
        for ((inputName, actionName) in collectInputActionNames(group)) {
            if (actionName.isNullOrEmpty()) continue

            val binding = forceBinding ?: keymap[inputName.lowercase()]
            if (binding.isNullOrEmpty()) {
                Timber.tag("SteamControllerVdf").d("Missing keymap for $inputName")
                continue
            }

            val list = bindings.getOrPut(actionName) { mutableListOf() }
            if (!list.contains(binding)) {
                list.add(binding)
            }
        }
    }

    private fun collectInputActionNames(group: VdfObject): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()

        val inputs = group.getObject("inputs")
        if (inputs != null) {
            for ((inputName, inputValue) in inputs.objectEntries()) {
                for (activator in inputValue.objectValues()) {
                    for (fullPress in activator.objectValues()) {
                        for (bindingGroup in fullPress.objectValues()) {
                            for ((bindingKey, bindingValue) in bindingGroup.stringEntries()) {
                                if (!bindingKey.equals("binding", ignoreCase = true)) continue
                                val actionName = parseBindingActionName(bindingValue) ?: continue
                                result.add(inputName to actionName)
                            }
                        }
                    }
                }
            }
        }

        val directBindings = group.getObject("bindings")
        if (directBindings != null) {
            for ((inputName, bindingValue) in directBindings.stringEntries()) {
                val actionName = parseBindingActionName(bindingValue) ?: continue
                result.add(inputName to actionName)
            }
        }

        return result
    }

    private fun parseBindingActionName(bindingValue: String): String? {
        val tokens = bindingValue.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        return when (tokens[0].lowercase()) {
            "game_action" -> tokens.getOrNull(2)?.trimEnd(',')
            "xinput_button" -> tokens.getOrNull(1)?.trimEnd(',')
            else -> null
        }
    }

    private fun resolveGroupActionName(
        group: VdfObject,
        presetName: String,
        fallbackXinputButtons: Set<String>,
    ): String? {
        val explicitActionName = group.getObject("gameactions")?.getString(presetName)
        if (!explicitActionName.isNullOrEmpty()) {
            return explicitActionName
        }

        return collectInputActionNames(group)
            .firstOrNull { (_, actionName) -> fallbackXinputButtons.contains(actionName.uppercase()) }
            ?.second
    }

    private fun addActionBinding(
        bindings: MutableMap<String, MutableList<String>>,
        actionName: String,
        binding: String,
        bindingSuffix: String,
    ) {
        val list = bindings.getOrPut(actionName) { mutableListOf() }
        val bindingWithSuffix = "$binding=$bindingSuffix"
        if (!list.contains(binding) && !list.contains(bindingWithSuffix)) {
            if (list.isEmpty()) {
                list.add(bindingWithSuffix)
            } else {
                list.add(0, binding)
            }
        }
    }

    private fun buildPresetBindings(
        presetName: String,
        preset: VdfObject,
        groupsById: Map<String, VdfObject>,
    ): LinkedHashMap<String, MutableList<String>> {
        val groupBindings = preset.getObject("group_source_bindings") ?: return LinkedHashMap()
        val bindings = LinkedHashMap<String, MutableList<String>>()

        for ((groupId, groupBinding) in groupBindings.stringEntries()) {
            val tokens = groupBinding.split(Regex("\\s+"))
            if (tokens.size < 2 || tokens[1].lowercase() != "active") continue

            val group = groupsById[groupId] ?: continue
            val groupMode = group.getString("mode")?.lowercase().orEmpty()
            val bindingType = tokens[0].lowercase()
            val isLeftStickSource = bindingType in listOf("joystick", "left_trackpad")
            val isRightStickSource = bindingType in listOf("right_joystick", "right_trackpad")

            if (bindingType in listOf("switch", "button_diamond", "dpad")) {
                addInputBindings(group, bindings)
            }

            if (bindingType in listOf("left_trackpad", "right_trackpad") && groupMode == "dpad") {
                addInputBindings(group, bindings)
            }

            if (bindingType in listOf("left_trigger", "right_trigger")) {
                if (groupMode == "trigger") {
                    val actionName =
                        resolveGroupActionName(
                            group,
                            presetName,
                            fallbackXinputButtons = setOf("TRIGGER_LEFT", "TRIGGER_RIGHT"),
                        )
                    if (!actionName.isNullOrEmpty()) {
                        val binding = if (bindingType == "left_trigger") "LTRIGGER" else "RTRIGGER"
                        addActionBinding(bindings, actionName, binding, bindingSuffix = "trigger")
                    }
                    val forceBinding = if (bindingType == "left_trigger") "DLTRIGGER" else "DRTRIGGER"
                    addInputBindings(group, bindings, forceBinding = forceBinding)
                } else {
                    Timber.tag("SteamControllerVdf").d("Unhandled trigger mode: $groupMode")
                }
            }

            if (bindingType in listOf("joystick", "right_joystick", "dpad", "left_trackpad", "right_trackpad")) {
                if (groupMode == "joystick_move" || groupMode == "joystick_camera") {
                    val actionName =
                        resolveGroupActionName(
                            group,
                            presetName,
                            fallbackXinputButtons = setOf("JOYSTICK_LEFT", "JOYSTICK_RIGHT"),
                        )
                    if (!actionName.isNullOrEmpty()) {
                        val binding =
                            when (bindingType) {
                                "joystick", "left_trackpad" -> "LJOY"
                                "right_joystick", "right_trackpad" -> "RJOY"
                                "dpad" -> "DPAD"
                                else -> ""
                            }
                        if (binding.isNotEmpty()) {
                            addActionBinding(bindings, actionName, binding, bindingSuffix = "joystick_move")
                        }
                    }
                    val forceBinding =
                        when {
                            isLeftStickSource -> "LSTICK"
                            isRightStickSource -> "RSTICK"
                            bindingType == "dpad" -> "RSTICK"
                            else -> null
                        }
                    if (forceBinding != null) {
                        addInputBindings(group, bindings, forceBinding = forceBinding)
                    }
                } else if (groupMode == "dpad") {
                    if (isLeftStickSource) {
                        val bindingMap =
                            mapOf(
                                "dpad_north" to "DLJOYUP",
                                "dpad_south" to "DLJOYDOWN",
                                "dpad_west" to "DLJOYLEFT",
                                "dpad_east" to "DLJOYRIGHT",
                                "click" to "LSTICK",
                            )
                        addInputBindings(group, bindings, keymap = bindingMap)
                    } else if (isRightStickSource) {
                        val bindingMap =
                            mapOf(
                                "dpad_north" to "DRJOYUP",
                                "dpad_south" to "DRJOYDOWN",
                                "dpad_west" to "DRJOYLEFT",
                                "dpad_east" to "DRJOYRIGHT",
                                "click" to "RSTICK",
                            )
                        addInputBindings(group, bindings, keymap = bindingMap)
                    }
                }
            }
        }

        return bindings
    }

    private fun buildRootControllerMappingsBindings(
        controllerMappings: VdfObject,
        groupsById: Map<String, VdfObject>,
    ): LinkedHashMap<String, MutableList<String>> {
        val groupSourceBindings = controllerMappings.getObject("group_source_bindings")
        val syntheticPreset = VdfObject()
        if (groupSourceBindings != null) {
            syntheticPreset.add("group_source_bindings", groupSourceBindings)
        }
        val bindings = buildPresetBindings("default", syntheticPreset, groupsById)

        controllerMappings.getObject("switch_bindings")
            ?.getObject("bindings")
            ?.stringEntries()
            ?.forEach { (inputName, bindingValue) ->
                val actionName = parseBindingActionName(bindingValue) ?: return@forEach
                val binding = keymapDigital[inputName.lowercase()] ?: return@forEach
                val list = bindings.getOrPut(actionName) { mutableListOf() }
                if (!list.contains(binding)) {
                    list.add(binding)
                }
            }

        return bindings
    }
}

private sealed interface VdfValue

private data class VdfEntry(
    val key: String,
    val value: VdfValue,
)

private data class VdfString(
    val value: String,
) : VdfValue

private class VdfObject : VdfValue {
    private val entries = mutableListOf<VdfEntry>()

    fun add(
        key: String,
        value: VdfValue,
    ) {
        entries.add(VdfEntry(key, value))
    }

    fun getObject(key: String): VdfObject? = getObjects(key).firstOrNull()

    fun getObjects(key: String): List<VdfObject> =
        entries.mapNotNull {
            if (it.key == key && it.value is VdfObject) it.value else null
        }

    fun getObjectIgnoreCase(key: String): VdfObject? = getObjectsIgnoreCase(key).firstOrNull()

    fun getObjectsIgnoreCase(key: String): List<VdfObject> =
        entries.mapNotNull {
            if (it.key.equals(key, ignoreCase = true) && it.value is VdfObject) it.value else null
        }

    fun getString(key: String): String? = getStrings(key).firstOrNull()

    fun getStrings(key: String): List<String> =
        entries.mapNotNull {
            if (it.key == key && it.value is VdfString) it.value.value else null
        }

    fun getStringIgnoreCase(key: String): String? = getStringsIgnoreCase(key).firstOrNull()

    fun getStringsIgnoreCase(key: String): List<String> =
        entries.mapNotNull {
            if (it.key.equals(key, ignoreCase = true) && it.value is VdfString) it.value.value else null
        }

    fun objectEntries(): List<Pair<String, VdfObject>> =
        entries.mapNotNull {
            if (it.value is VdfObject) it.key to it.value else null
        }

    fun objectValues(): List<VdfObject> = entries.mapNotNull { it.value as? VdfObject }

    fun stringEntries(): List<Pair<String, String>> =
        entries.mapNotNull {
            if (it.value is VdfString) it.key to it.value.value else null
        }

    fun keys(): List<String> = entries.map { it.key }
}

private class VdfParser(
    text: String,
) {
    private val source = if (text.startsWith("\uFEFF")) text.substring(1) else text
    private var index = 0

    fun parse(): VdfObject = parseObject()

    private fun parseObject(): VdfObject {
        val obj = VdfObject()
        while (true) {
            val token = nextToken() ?: break
            if (token == "}") break
            val key = token
            val valueToken = nextToken() ?: break
            if (valueToken == "{") {
                obj.add(key, parseObject())
            } else if (valueToken == "}") {
                break
            } else {
                obj.add(key, VdfString(valueToken))
            }
        }
        return obj
    }

    private fun nextToken(): String? {
        skipWhitespaceAndComments()
        if (index >= source.length) return null
        return when (val ch = source[index]) {
            '{', '}' -> {
                index++
                ch.toString()
            }

            '"' -> {
                parseQuoted()
            }

            else -> {
                parseUnquoted()
            }
        }
    }

    private fun parseQuoted(): String {
        index++ // skip opening quote
        val sb = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            if (ch == '"') break
            if (ch == '\\' && index < source.length) {
                val escaped = source[index++]
                sb.append(unescapeChar(escaped))
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parseUnquoted(): String {
        val start = index
        while (index < source.length) {
            val ch = source[index]
            if (ch.isWhitespace() || ch == '{' || ch == '}') break
            index++
        }
        return unescape(source.substring(start, index))
    }

    private fun skipWhitespaceAndComments() {
        while (index < source.length) {
            val ch = source[index]
            if (ch.isWhitespace()) {
                index++
                continue
            }
            if (ch == '/' && index + 1 < source.length && source[index + 1] == '/') {
                index += 2
                while (index < source.length && source[index] != '\n') index++
                continue
            }
            break
        }
    }

    private fun unescapeChar(ch: Char): Char =
        when (ch) {
            'n' -> '\n'
            't' -> '\t'
            'v' -> '\u000B'
            'b' -> '\b'
            'r' -> '\r'
            'f' -> '\u000C'
            'a' -> '\u0007'
            '\\' -> '\\'
            '?' -> '?'
            '"' -> '"'
            '\'' -> '\''
            else -> ch
        }

    private fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                sb.append(unescapeChar(next))
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
