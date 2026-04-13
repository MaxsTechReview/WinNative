/* Settings > Presets screen — Jetpack Compose / Material3.
 * Scrolling delegated to a View-level ScrollView in PresetsFragment, matching DriversFragment. */
package com.winlator.cmod

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.ui.outlinedSwitchColors

private val BgDark        = Color(0xFF18181D)
private val CardDark      = Color(0xFF1C1C2A)
private val CardDarker    = Color(0xFF15151E)
private val CardBorder    = Color(0xFF2A2A3A)
private val IconBoxBg     = Color(0xFF242434)
private val SurfaceDark   = Color(0xFF21212A)
private val Accent        = Color(0xFF1A9FFF)
private val DangerRed     = Color(0xFFFF7A88)
private val TextPrimary   = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)

@Composable
private fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

// ============================================================================
// State
// ============================================================================

/**
 * Which preset flavor is currently being edited. Matches the two [PresetEngine]
 * backends handled by [Box64PresetManager] / [FEXCorePresetManager]; the fragment
 * reads/writes the SharedPref keys tied to each category so container integration
 * keeps using the same `box64_preset` / `fexcore_preset` entries unchanged.
 */
enum class PresetEngine { BOX64, FEXCORE }

enum class PresetControlType { TOGGLE, DROPDOWN, TEXT }

data class PresetOption(
    val id: String,
    val name: String,
    val isCustom: Boolean,
)

data class EnvVarDefinition(
    val name: String,
    val defaultValue: String,
    val values: List<String>,
    val controlType: PresetControlType,
    val summary: String,
    val fullDescription: String,
)

/**
 * Full per-engine snapshot. Keeping a copy for each engine in [PresetsState] means
 * the AnimatedContent crossfade can render the *outgoing* engine's data during the
 * fade-out phase instead of momentarily flashing the new engine's data through the
 * old layout.
 */
data class PresetEngineData(
    val presets: List<PresetOption> = emptyList(),
    val selectedPresetId: String = "",
    val envVarDefinitions: List<EnvVarDefinition> = emptyList(),
    val currentValues: Map<String, String> = emptyMap(),
    val editable: Boolean = false,
)

data class PresetsState(
    val currentEngine: PresetEngine = PresetEngine.BOX64,
    val engines: Map<PresetEngine, PresetEngineData> = mapOf(
        PresetEngine.BOX64 to PresetEngineData(),
        PresetEngine.FEXCORE to PresetEngineData(),
    ),
) {
    /** Data for the currently-selected engine. Used by callers that don't participate in the crossfade. */
    val current: PresetEngineData
        get() = engines[currentEngine] ?: PresetEngineData()
}

// ============================================================================
// Root
// ============================================================================

@Composable
fun PresetsScreen(
    state: PresetsState,
    onEngineSelected: (PresetEngine) -> Unit,
    onPresetSelected: (String) -> Unit,
    onEnvVarValueChanged: (name: String, value: String) -> Unit,
    onCreatePreset: (name: String) -> Unit,
    onRenamePreset: (name: String) -> Unit,
    onDuplicatePreset: () -> Unit,
    onExportPreset: () -> Unit,
    onImportPreset: () -> Unit,
    onRemovePreset: () -> Unit,
    suggestedNewPresetName: () -> String,
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showDuplicateConfirm by remember { mutableStateOf(false) }

    if (showNewDialog) {
        val defaultName = remember { suggestedNewPresetName() }
        PromptDialog(
            title = stringResource(R.string.container_presets_new),
            initialValue = defaultName,
            confirmLabel = stringResource(R.string.common_ui_add),
            onDismiss = { showNewDialog = false },
            onConfirm = { value ->
                showNewDialog = false
                onCreatePreset(value)
            },
        )
    }

    if (showRenameDialog) {
        val current = state.current
        val selected = current.presets.firstOrNull { it.id == current.selectedPresetId }
        PromptDialog(
            title = stringResource(R.string.container_presets_rename),
            initialValue = selected?.name.orEmpty(),
            confirmLabel = stringResource(R.string.common_ui_confirm),
            onDismiss = { showRenameDialog = false },
            onConfirm = { value ->
                showRenameDialog = false
                onRenamePreset(value)
            },
        )
    }

    if (showRemoveConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.container_presets_title),
            message = stringResource(R.string.container_presets_confirm_remove),
            confirmLabel = stringResource(R.string.common_ui_remove),
            confirmColor = DangerRed,
            onDismiss = { showRemoveConfirm = false },
            onConfirm = {
                showRemoveConfirm = false
                onRemovePreset()
            },
        )
    }

    if (showDuplicateConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.container_presets_title),
            message = stringResource(R.string.container_presets_confirm_duplicate),
            confirmLabel = stringResource(R.string.common_ui_duplicate),
            confirmColor = Accent,
            onDismiss = { showDuplicateConfirm = false },
            onConfirm = {
                showDuplicateConfirm = false
                onDuplicatePreset()
            },
        )
    }

    val currentData = state.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PresetHeroHeader(
            engine = state.currentEngine,
            presetCount = currentData.presets.size,
            customCount = currentData.presets.count { it.isCustom },
            onCreatePreset = { showNewDialog = true },
            onImportPreset = onImportPreset,
        )

        // Combined engine-tabs + preset selector card. The tabs themselves stay
        // static; only the preset dropdown / badge / menu / hint and env-var grid
        // are inside the AnimatedContent below, so tapping a tab doesn't make the
        // tab itself flicker.
        PresetSelectorCard(
            engine = state.currentEngine,
            state = state,
            onEngineSelected = onEngineSelected,
            onPresetSelected = onPresetSelected,
            onRename = { showRenameDialog = true },
            onDuplicate = { showDuplicateConfirm = true },
            onExport = onExportPreset,
            onImport = onImportPreset,
            onRemove = { showRemoveConfirm = true },
        )

        SectionLabel(
            text = stringResource(R.string.container_config_env_vars),
            modifier = Modifier.padding(top = 6.dp),
        )

        // Env var grid crossfades + slides when switching engines. Keyed on the
        // engine enum so AnimatedContent can gracefully interrupt rapid taps:
        // mid-animation state changes swap to the new target from current progress
        // instead of completing the old animation first.
        AnimatedContent(
            targetState = state.currentEngine,
            transitionSpec = { engineSwapTransition() },
            contentKey = { it },
            label = "presetEnvVarGrid",
        ) { frameEngine ->
            val data = state.engines[frameEngine] ?: PresetEngineData()
            if (data.envVarDefinitions.isEmpty()) {
                EmptyEnvVarsCard()
            } else {
                EnvVarGrid(
                    definitions = data.envVarDefinitions,
                    values = data.currentValues,
                    editable = data.editable,
                    onValueChanged = onEnvVarValueChanged,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Shared transition spec for the engine crossfade. Slides the outgoing content
 * out by a small offset and the incoming content in from the opposite side,
 * with a short fade on top. Direction is derived from enum ordinal so Box64 →
 * FEXCore slides left-to-right and FEXCore → Box64 slides right-to-left.
 *
 * Durations are deliberately short (~220ms) so rapid tab taps feel responsive.
 * [AnimatedContent] interrupts in-flight animations when the target state
 * changes, so a spam-tapping user keeps animating from the current progress
 * instead of queueing up and waiting for the previous animation to finish.
 */
private fun AnimatedContentTransitionScope<PresetEngine>.engineSwapTransition(): ContentTransform {
    val forward = targetState.ordinal > initialState.ordinal
    val direction = if (forward) 1 else -1
    val slideSpec = tween<IntOffset>(
        durationMillis = 220,
        easing = FastOutSlowInEasing,
    )
    val fadeInSpec = tween<Float>(
        durationMillis = 200,
        easing = FastOutSlowInEasing,
    )
    val fadeOutSpec = tween<Float>(
        durationMillis = 140,
        easing = FastOutSlowInEasing,
    )
    return (
        fadeIn(animationSpec = fadeInSpec) +
            slideInHorizontally(animationSpec = slideSpec) { fullWidth ->
                (fullWidth / 12) * direction
            }
        ).togetherWith(
        fadeOut(animationSpec = fadeOutSpec) +
            slideOutHorizontally(animationSpec = slideSpec) { fullWidth ->
                -(fullWidth / 12) * direction
            }
    ).using(
        SizeTransform(clip = false) { _, _ ->
            tween(durationMillis = 240, easing = FastOutSlowInEasing)
        }
    )
}

// ============================================================================
// Hero header
// ============================================================================

@Composable
private fun PresetHeroHeader(
    engine: PresetEngine,
    presetCount: Int,
    customCount: Int,
    onCreatePreset: () -> Unit,
    onImportPreset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.container_presets_title),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountPill(
                        label = when (engine) {
                            PresetEngine.BOX64 -> "Box64"
                            PresetEngine.FEXCORE -> "FEXCore"
                        },
                        count = presetCount,
                    )
                    Spacer(Modifier.width(5.dp))
                    CountPill(label = "Custom", count = customCount)
                }
            }
            Spacer(Modifier.width(8.dp))
            HeroPillButton(
                label = stringResource(R.string.container_presets_new),
                icon = Icons.Outlined.Add,
                onClick = onCreatePreset,
            )
            Spacer(Modifier.width(6.dp))
            HeroPillButton(
                label = stringResource(R.string.container_presets_import),
                icon = Icons.Outlined.FileDownload,
                onClick = onImportPreset,
            )
        }
    }
}

@Composable
private fun CountPill(label: String, count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Accent.copy(alpha = 0.12f))
            .border(1.dp, Accent.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count.toString(),
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HeroPillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Accent.copy(alpha = 0.12f))
            .border(1.dp, Accent.copy(alpha = 0.32f), RoundedCornerShape(7.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ============================================================================
// Engine tabs (Box64 / FEXCore)
// ============================================================================

@Composable
private fun EngineTabs(
    selected: PresetEngine,
    onEngineSelected: (PresetEngine) -> Unit,
) {
    // Compact segmented tabs — sized inline (not stretched across the row) so they
    // don't dominate the screen the way a fillMaxWidth segmented control does.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        EngineTab(
            label = stringResource(R.string.container_box64_title),
            selected = selected == PresetEngine.BOX64,
            onClick = { onEngineSelected(PresetEngine.BOX64) },
        )
        EngineTab(
            label = stringResource(R.string.container_fexcore_config),
            selected = selected == PresetEngine.FEXCORE,
            onClick = { onEngineSelected(PresetEngine.FEXCORE) },
        )
    }
}

@Composable
private fun EngineTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (selected) Accent.copy(alpha = 0.45f) else Color.Transparent
    val textColor = if (selected) Accent else TextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ============================================================================
// Combined engine-tabs + preset selector card
// ============================================================================

@Composable
private fun PresetSelectorCard(
    engine: PresetEngine,
    state: PresetsState,
    onEngineSelected: (PresetEngine) -> Unit,
    onPresetSelected: (String) -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Single row — engine tabs stay static on the left, the dropdown /
            // badge / menu group on the right swaps via AnimatedContent keyed on
            // the current engine. Keeping the tabs outside the AnimatedContent
            // means tapping a tab doesn't cause the tab itself to flash in/out.
            Row(verticalAlignment = Alignment.CenterVertically) {
                EngineTabs(
                    selected = engine,
                    onEngineSelected = onEngineSelected,
                )

                Spacer(Modifier.width(10.dp))

                AnimatedContent(
                    targetState = engine,
                    transitionSpec = { engineSwapTransition() },
                    contentKey = { it },
                    label = "presetSelectorRow",
                    modifier = Modifier.weight(1f),
                ) { frameEngine ->
                    val data = state.engines[frameEngine] ?: PresetEngineData()
                    PresetSelectorRowContent(
                        data = data,
                        onPresetSelected = onPresetSelected,
                        onRename = onRename,
                        onDuplicate = onDuplicate,
                        onExport = onExport,
                        onImport = onImport,
                        onRemove = onRemove,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = engine,
                transitionSpec = { engineSwapTransition() },
                contentKey = { it },
                label = "presetHint",
            ) { frameEngine ->
                val editable = state.engines[frameEngine]?.editable == true
                Text(
                    text = stringResource(
                        if (editable) {
                            R.string.container_presets_changes_auto_saved
                        } else {
                            R.string.container_presets_builtin_readonly_hint
                        }
                    ),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

/**
 * Dropdown + badge + overflow menu row. Factored out so it can be the inner
 * content of the selector card's AnimatedContent — during the engine crossfade,
 * both the outgoing and incoming instances need to read from their own
 * [PresetEngineData] snapshot so the fading-out row doesn't briefly show the
 * new engine's preset name / badge color through the old layout.
 */
@Composable
private fun PresetSelectorRowContent(
    data: PresetEngineData,
    onPresetSelected: (String) -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val selected = data.presets.firstOrNull { it.id == data.selectedPresetId }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Dropdown trigger — takes the middle space.
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(CardDarker)
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .noRippleClickable(enabled = data.presets.isNotEmpty()) { dropdownOpen = true }
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected?.name ?: "—",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (dropdownOpen) 180f else 0f),
                )
            }

            DropdownMenu(
                expanded = dropdownOpen,
                onDismissRequest = { dropdownOpen = false },
                containerColor = CardDark,
            ) {
                data.presets.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = option.name,
                                    color = if (option.id == data.selectedPresetId) Accent else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (option.id == data.selectedPresetId) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (option.isCustom) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.container_presets_custom),
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        },
                        onClick = {
                            dropdownOpen = false
                            if (option.id != data.selectedPresetId) {
                                onPresetSelected(option.id)
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        PresetTypeBadge(isCustom = selected?.isCustom == true)
        Spacer(Modifier.width(2.dp))
        Box {
            IconTapButton(
                icon = Icons.Outlined.MoreVert,
                tint = TextPrimary,
                onClick = { menuOpen = true },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = CardDark,
            ) {
                MenuRow(
                    icon = Icons.Outlined.Edit,
                    iconTint = Accent,
                    label = stringResource(R.string.container_presets_rename),
                    textColor = TextPrimary,
                    enabled = data.editable,
                    onClick = { menuOpen = false; onRename() },
                )
                MenuRow(
                    icon = Icons.Outlined.ContentCopy,
                    iconTint = Accent,
                    label = stringResource(R.string.common_ui_duplicate),
                    textColor = TextPrimary,
                    enabled = true,
                    onClick = { menuOpen = false; onDuplicate() },
                )
                MenuRow(
                    icon = Icons.Outlined.FileUpload,
                    iconTint = Accent,
                    label = stringResource(R.string.common_ui_export),
                    textColor = TextPrimary,
                    enabled = data.editable,
                    onClick = { menuOpen = false; onExport() },
                )
                MenuRow(
                    icon = Icons.Outlined.FileDownload,
                    iconTint = Accent,
                    label = stringResource(R.string.common_ui_import),
                    textColor = TextPrimary,
                    enabled = true,
                    onClick = { menuOpen = false; onImport() },
                )
                MenuRow(
                    icon = Icons.Outlined.Delete,
                    iconTint = DangerRed,
                    label = stringResource(R.string.common_ui_remove),
                    textColor = DangerRed,
                    enabled = data.editable,
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun PresetTypeBadge(isCustom: Boolean) {
    val color = if (isCustom) Accent else TextSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (isCustom) R.string.container_presets_custom else R.string.container_presets_built_in
            ),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val effectiveIconTint = if (enabled) iconTint else TextSecondary.copy(alpha = 0.45f)
    val effectiveTextColor = if (enabled) textColor else TextSecondary.copy(alpha = 0.55f)
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveIconTint,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = effectiveTextColor, fontSize = 13.sp)
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}

// ============================================================================
// Section label
// ============================================================================

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

// ============================================================================
// Env var grid — square cards with toggle / dropdown / text input
// ============================================================================

@Composable
private fun EnvVarGrid(
    definitions: List<EnvVarDefinition>,
    values: Map<String, String>,
    editable: Boolean,
    onValueChanged: (name: String, value: String) -> Unit,
) {
    // 3-column layout via chunked rows to match the original recyclerview
    // grid span count. LazyVerticalGrid is harder to size inside the parent
    // ScrollView, so we flow rows manually and let each card wrap its content.
    val columns = 3
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        definitions.chunked(columns).forEach { rowItems ->
            // height(IntrinsicSize.Min) lets every card in the row grow to match
            // the tallest card's content, so cards in a row align visually even
            // when one has a much longer description.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { def ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        EnvVarCard(
                            definition = def,
                            value = values[def.name] ?: def.defaultValue,
                            editable = editable,
                            onValueChanged = { onValueChanged(def.name, it) },
                        )
                    }
                }
                // Filler spacer so a lone trailing card keeps the same width as
                // its siblings in earlier rows (prevents the last odd card from
                // stretching to full row width).
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EnvVarCard(
    definition: EnvVarDefinition,
    value: String,
    editable: Boolean,
    onValueChanged: (String) -> Unit,
) {
    // Card layout mirrors the original preset_env_var_card.xml: variable name on
    // top, control in the middle, multi-line description underneath. minHeight
    // matches the old 140dp so all cards in a row align neatly even when their
    // descriptions vary in length.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .heightIn(min = 150.dp)
            .alpha(if (editable) 1f else 0.55f)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = definition.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            when (definition.controlType) {
                PresetControlType.TOGGLE -> EnvVarToggleControl(
                    value = value,
                    editable = editable,
                    onValueChanged = onValueChanged,
                )
                PresetControlType.DROPDOWN -> EnvVarDropdownControl(
                    values = definition.values,
                    value = value,
                    editable = editable,
                    onValueChanged = onValueChanged,
                )
                PresetControlType.TEXT -> EnvVarTextControl(
                    value = value,
                    editable = editable,
                    onValueChanged = onValueChanged,
                )
            }
            if (definition.summary.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = definition.summary,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EnvVarToggleControl(
    value: String,
    editable: Boolean,
    onValueChanged: (String) -> Unit,
) {
    val checked = value == "1"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "Enabled" else "Disabled",
            color = if (checked) Accent else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            enabled = editable,
            onCheckedChange = { onValueChanged(if (it) "1" else "0") },
            colors = outlinedSwitchColors(
                accentColor = Accent,
                textSecondaryColor = TextSecondary,
            ),
            // scale() preserves the switch's native proportions and just shrinks
            // the whole thing; using .size() with a custom width/height ends up
            // squishing the thumb vs. track ratio.
            modifier = Modifier.scale(0.72f),
        )
    }
}

@Composable
private fun EnvVarDropdownControl(
    values: List<String>,
    value: String,
    editable: Boolean,
    onValueChanged: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDarker)
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .noRippleClickable(enabled = editable && values.isNotEmpty()) { open = true }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value.ifBlank { "—" },
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = CardDark,
        ) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == value) Accent else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        if (option != value) onValueChanged(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun EnvVarTextControl(
    value: String,
    editable: Boolean,
    onValueChanged: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) Accent else CardBorder
    val borderWidth = if (isFocused) 1.5.dp else 1.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardDarker)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (editable) onValueChanged(it) },
            enabled = editable,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ============================================================================
// Empty state for env vars
// ============================================================================

@Composable
private fun EmptyEnvVarsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.common_ui_no_items_to_display),
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

// ============================================================================
// Small shared controls
// ============================================================================

@Composable
private fun IconTapButton(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DialogActionButton(label: String, textColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardDarker)
            .border(1.dp, textColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ============================================================================
// Dialogs
// ============================================================================

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(22.dp),
        ) {
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    DialogActionButton(
                        label = stringResource(R.string.common_ui_cancel),
                        textColor = TextSecondary,
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        label = confirmLabel,
                        textColor = confirmColor,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.container_presets_preset).uppercase(),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val borderColor = if (isFocused) Accent else CardBorder
                    val borderWidth = if (isFocused) 1.5.dp else 1.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeightIn(min = 40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(CardDarker)
                            .border(borderWidth, borderColor, RoundedCornerShape(9.dp))
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            interactionSource = interactionSource,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        DialogActionButton(
                            label = stringResource(R.string.common_ui_cancel),
                            textColor = TextSecondary,
                            onClick = {
                                focusManager.clearFocus()
                                onDismiss()
                            },
                        )
                        DialogActionButton(
                            label = confirmLabel,
                            textColor = Accent,
                            onClick = {
                                val trimmed = text.trim()
                                if (trimmed.isNotEmpty()) {
                                    focusManager.clearFocus()
                                    onConfirm(trimmed)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
