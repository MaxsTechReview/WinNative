package com.winlator.cmod.feature.settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.runtime.input.ui.InputControlsView
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlin.math.roundToInt

private val InputBg = Color(0xFF18181D)
private val InputCard = Color(0xFF1C1C2A)
private val InputSubcard = Color(0xFF161622)
private val InputField = Color(0xFF14141E)
private val InputOutline = Color(0xFF2A2A3A)
private val InputIconBox = Color(0xFF242434)
private val InputAccent = Color(0xFF1A9FFF)
private val InputTextPrimary = Color(0xFFF0F4FF)
private val InputTextSecondary = Color(0xFF7A8FA8)
private val InputDanger = Color(0xFFFF7A88)
private val InputTickHidden = Color.Transparent

data class InputControlsScreenState(
    val selectedProfileName: String? = null,
    val selectedProfileElementCount: Int = 0,
    val overlayOpacity: Int = 40,
    val gyroscopeEnabled: Boolean = false,
    val gyroscopeModeIndex: Int = 0,
    val gyroscopeActivatorLabel: String = "",
    val rightStickGyroEnabled: Boolean = false,
    val gyroscopeExpanded: Boolean = false,
    val gyroXSensitivity: Int = 100,
    val gyroYSensitivity: Int = 100,
    val gyroSmoothing: Int = 90,
    val gyroDeadzone: Int = 5,
    val invertGyroX: Boolean = false,
    val invertGyroY: Boolean = false,
    val triggerTypeIndex: Int = 1,
    val triggerCardExpanded: Boolean = false,
    val triggerDescription: String = "",
    val controllerCards: List<InputControllerCardState> = emptyList(),
    val dialog: InputControlsDialogUiState = InputControlsDialogUiState.None,
)

sealed interface InputControlsDialogUiState {
    data object None : InputControlsDialogUiState

    data class Prompt(
        val title: String,
        val initialValue: String,
        val confirmLabel: String,
    ) : InputControlsDialogUiState

    data class Confirm(
        val message: String,
        val confirmLabel: String,
        val tone: InputDialogTone,
    ) : InputControlsDialogUiState

    data class Choice(
        val title: String,
        val options: List<String>,
        val selectedIndex: Int,
    ) : InputControlsDialogUiState

    data class MultiChoice(
        val title: String,
        val options: List<String>,
        val selectedIndices: Set<Int> = emptySet(),
        val disabledIndices: Set<Int> = emptySet(),
        val confirmLabel: String,
    ) : InputControlsDialogUiState
}

enum class InputDialogTone {
    Accent,
    Danger,
}

data class InputControllerCardState(
    val controllerId: String,
    val name: String,
    val bindingCount: Int,
    val connected: Boolean,
    val expanded: Boolean,
    val showBindings: Boolean,
    val bindings: List<InputControllerBindingState> = emptyList(),
)

data class InputControllerBindingState(
    val keyCode: Int,
    val label: String,
    val typeLabel: String,
    val bindingLabel: String,
)

data class InputControlsScreenActions(
    val onSelectProfile: () -> Unit,
    val onOpenEditor: () -> Unit,
    val onAddProfile: () -> Unit,
    val onEditProfile: () -> Unit,
    val onDuplicateProfile: () -> Unit,
    val onRemoveProfile: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onConfirmDialog: () -> Unit,
    val onPromptDialogConfirm: (String) -> Unit,
    val onChoiceDialogSelect: (Int) -> Unit,
    val onMultiChoiceDialogConfirm: (Set<Int>) -> Unit,
    val onOverlayOpacityChanged: (Int) -> Unit,
    val onGyroscopeEnabledChanged: (Boolean) -> Unit,
    val onGyroscopeModeSelected: (Int) -> Unit,
    val onGyroscopeActivatorClick: () -> Unit,
    val onRightStickGyroChanged: (Boolean) -> Unit,
    val onGyroscopeExpandedChanged: (Boolean) -> Unit,
    val onGyroXSensitivityChanged: (Int) -> Unit,
    val onGyroYSensitivityChanged: (Int) -> Unit,
    val onGyroSmoothingChanged: (Int) -> Unit,
    val onGyroDeadzoneChanged: (Int) -> Unit,
    val onInvertGyroXChanged: (Boolean) -> Unit,
    val onInvertGyroYChanged: (Boolean) -> Unit,
    val onResetGyroPreview: () -> Unit,
    val onAttachGyroPreview: (InputControlsView) -> Unit,
    val onDetachGyroPreview: () -> Unit,
    val onTriggerTypeSelected: (Int) -> Unit,
    val onTriggerCardExpandedChanged: (Boolean) -> Unit,
    val onImportProfile: () -> Unit,
    val onDownloadProfile: () -> Unit,
    val onExportProfile: () -> Unit,
    val onControllerExpandedToggle: (String) -> Unit,
    val onRemoveController: (String) -> Unit,
    val onBindingTypeClick: (String, Int) -> Unit,
    val onBindingValueClick: (String, Int) -> Unit,
    val onRemoveBinding: (String, Int) -> Unit,
)

@Composable
fun InputControlsScreen(
    state: InputControlsScreenState,
    actions: InputControlsScreenActions,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(InputBg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 26.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("profile-label") { SectionLabel(stringResource(R.string.common_ui_profile)) }
            item("profile-card") { ProfileCard(state, actions) }
            item("overlay-label") { SectionLabel(stringResource(R.string.input_controls_editor_overlay_opacity)) }
            item("overlay-card") { OverlayOpacityCard(state, actions) }
            item("gyro-label") { SectionLabel(stringResource(R.string.session_gyroscope_title)) }
            item("gyro-card") { GyroscopeCard(state, actions) }
            item("trigger-label") { SectionLabel(stringResource(R.string.session_gamepad_trigger_type)) }
            item("trigger-card") { TriggerTypeCard(state, actions) }
            item("actions-label") { SectionLabel(stringResource(R.string.common_ui_profile)) }
            item("import-card") {
                ActionCard(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.input_controls_editor_import_profile),
                    onClick = actions.onImportProfile,
                )
            }
            item("download-card") {
                ActionCard(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.common_ui_download),
                    onClick = actions.onDownloadProfile,
                )
            }
            item("export-card") {
                ActionCard(
                    icon = Icons.Outlined.FileUpload,
                    title = stringResource(R.string.input_controls_editor_export_profile),
                    onClick = actions.onExportProfile,
                )
            }
            item("controllers-label") { SectionLabel(stringResource(R.string.session_gamepad_external_controllers)) }
            if (state.controllerCards.isEmpty()) {
                item("controllers-empty") {
                    EmptyStateCard(stringResource(R.string.common_ui_no_items_to_display))
                }
            } else {
                items(state.controllerCards, key = { it.controllerId }) { controller ->
                    ControllerCard(controller, actions)
                }
            }
        }

        when (val dialog = state.dialog) {
            InputControlsDialogUiState.None -> {
                Unit
            }

            is InputControlsDialogUiState.Prompt -> {
                InputPromptDialog(
                    title = dialog.title,
                    initialValue = dialog.initialValue,
                    confirmLabel = dialog.confirmLabel,
                    onDismiss = actions.onDismissDialog,
                    onConfirm = actions.onPromptDialogConfirm,
                )
            }

            is InputControlsDialogUiState.Confirm -> {
                InputConfirmDialog(
                    message = dialog.message,
                    confirmLabel = dialog.confirmLabel,
                    tone = dialog.tone,
                    onDismiss = actions.onDismissDialog,
                    onConfirm = actions.onConfirmDialog,
                )
            }

            is InputControlsDialogUiState.Choice -> {
                InputChoiceDialog(
                    title = dialog.title,
                    options = dialog.options,
                    selectedIndex = dialog.selectedIndex,
                    onDismiss = actions.onDismissDialog,
                    onSelected = actions.onChoiceDialogSelect,
                )
            }

            is InputControlsDialogUiState.MultiChoice -> {
                InputMultiChoiceDialog(
                    title = dialog.title,
                    options = dialog.options,
                    selectedIndices = dialog.selectedIndices,
                    disabledIndices = dialog.disabledIndices,
                    confirmLabel = dialog.confirmLabel,
                    onDismiss = actions.onDismissDialog,
                    onConfirm = actions.onMultiChoiceDialogConfirm,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = InputTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun CardShell(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickableModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InputCard)
                .border(1.dp, InputOutline, RoundedCornerShape(12.dp))
                .then(clickableModifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun IconBox(
    image: ImageVector,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(InputIconBox),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun IconActionButton(
    image: ImageVector,
    contentDescription: String,
    tint: Color = InputTextSecondary,
    onClick: () -> Unit,
    size: Dp = 34.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(InputSubcard)
                .border(1.dp, InputOutline, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (size <= 28.dp) 14.dp else 18.dp),
        )
    }
}

@Composable
private fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        modifier = Modifier.scale(0.78f),
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors =
            outlinedSwitchColors(
                accentColor = InputAccent,
                textSecondaryColor = InputTextSecondary,
            ),
    )
}

@Composable
private fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            Chip(
                text = label,
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) InputAccent.copy(alpha = 0.18f) else InputSubcard)
                .border(
                    1.dp,
                    if (selected) InputAccent.copy(alpha = 0.35f) else InputOutline,
                    RoundedCornerShape(9.dp),
                ).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) InputAccent else InputTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SelectionPill(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(InputField)
                .border(1.dp, InputOutline, RoundedCornerShape(9.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = InputTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = InputTextSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InputCard)
                .border(1.dp, InputOutline, RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = InputTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InputDialogShell(
    onDismiss: () -> Unit,
    title: String? = null,
    maxWidth: Dp = 440.dp,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(InputCard)
                        .border(1.dp, InputOutline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            color = InputTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(InputOutline),
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun ProfileSelectorIconBox(tint: Color) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(InputIconBox),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(tint),
                    )
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(15.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(tint.copy(alpha = 0.95f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputFixedFooterDialogShell(
    onDismiss: () -> Unit,
    title: String? = null,
    maxWidth: Dp = 440.dp,
    maxBodyHeight: Dp = 320.dp,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val responsiveBodyMaxHeight =
                minOf(
                    maxBodyHeight,
                    (maxHeight - 176.dp).coerceAtLeast(140.dp),
                )

            Box(
                modifier =
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(InputCard)
                        .border(1.dp, InputOutline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (title != null) {
                        Text(
                            text = title,
                            color = InputTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(InputOutline),
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = responsiveBodyMaxHeight)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }

                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(InputOutline),
                    )
                    Spacer(Modifier.height(14.dp))
                    footer()
                }
            }
        }
    }
}

@Composable
private fun InputDialogButton(
    label: String,
    primary: Boolean,
    textColor: Color,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val resolvedBackground = backgroundColor ?: if (primary) InputAccent else InputSubcard
    val resolvedBorder = borderColor ?: if (primary) InputAccent.copy(alpha = 0.5f) else InputOutline
    val disabledBackground = InputSubcard.copy(alpha = 0.96f)
    val disabledBorder = InputOutline.copy(alpha = 0.9f)
    val clickModifier =
        if (enabled) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            Modifier
                .widthIn(min = 84.dp)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) resolvedBackground else disabledBackground)
                .border(
                    1.dp,
                    if (enabled) resolvedBorder else disabledBorder,
                    RoundedCornerShape(10.dp),
                ).then(clickModifier)
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) textColor else InputTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InputConfirmDialog(
    message: String,
    confirmLabel: String,
    tone: InputDialogTone,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    InputDialogShell(
        onDismiss = onDismiss,
        maxWidth = 420.dp,
    ) {
        Text(
            text = message,
            color = InputTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(InputOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            InputDialogButton(
                label = stringResource(R.string.common_ui_cancel),
                primary = false,
                textColor = InputTextPrimary,
                onClick = onDismiss,
            )
            InputDialogButton(
                label = confirmLabel,
                primary = tone == InputDialogTone.Accent,
                textColor = if (tone == InputDialogTone.Danger) InputDanger else InputTextPrimary,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun InputPromptDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(title, initialValue) { mutableStateOf(initialValue) }
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    InputDialogShell(
        onDismiss = onDismiss,
        title = title,
        maxWidth = 440.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .requiredHeightIn(min = 46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputField)
                    .border(
                        if (focused) 1.5.dp else 1.dp,
                        if (focused) InputAccent else InputOutline,
                        RoundedCornerShape(12.dp),
                    ).padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = InputTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(InputAccent),
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConfirm(text.trim())
                        },
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(InputOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            InputDialogButton(
                label = stringResource(R.string.common_ui_cancel),
                primary = false,
                textColor = InputTextPrimary,
                onClick = onDismiss,
            )
            InputDialogButton(
                label = confirmLabel,
                primary = true,
                textColor = InputAccent,
                backgroundColor = InputAccent.copy(alpha = 0.12f),
                borderColor = InputAccent.copy(alpha = 0.3f),
                onClick = { onConfirm(text.trim()) },
            )
        }
    }
}

@Composable
private fun InputChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    InputFixedFooterDialogShell(
        onDismiss = onDismiss,
        title = title,
        maxWidth = 430.dp,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                InputDialogButton(
                    label = stringResource(R.string.common_ui_cancel),
                    primary = false,
                    textColor = InputTextPrimary,
                    onClick = onDismiss,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) InputAccent.copy(alpha = 0.08f) else InputField)
                            .border(
                                1.dp,
                                if (selected) InputAccent.copy(alpha = 0.24f) else InputOutline,
                                RoundedCornerShape(12.dp),
                            ).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelected(index) },
                            ).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .border(
                                    1.4.dp,
                                    if (selected) InputAccent else InputOutline,
                                    RoundedCornerShape(9.dp),
                                ).background(if (selected) InputAccent.copy(alpha = 0.12f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(InputAccent),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = option,
                        color = InputTextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.common_ui_no_items_to_display),
                    color = InputTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun InputMultiChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndices: Set<Int>,
    disabledIndices: Set<Int>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
) {
    var currentSelection by remember(title, options, selectedIndices) {
        mutableStateOf(selectedIndices - disabledIndices)
    }
    val hasSelection = currentSelection.isNotEmpty()

    InputFixedFooterDialogShell(
        onDismiss = onDismiss,
        title = title,
        maxWidth = 430.dp,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                InputDialogButton(
                    label = stringResource(R.string.common_ui_cancel),
                    primary = false,
                    textColor = InputTextPrimary,
                    onClick = onDismiss,
                )
                InputDialogButton(
                    label = confirmLabel,
                    primary = true,
                    textColor = InputAccent,
                    backgroundColor = InputAccent.copy(alpha = 0.12f),
                    borderColor = InputAccent.copy(alpha = 0.3f),
                    enabled = hasSelection,
                    onClick = { onConfirm(currentSelection) },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index in currentSelection
                val disabled = index in disabledIndices
                val rowModifier =
                    if (disabled) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                currentSelection =
                                    if (selected) {
                                        currentSelection - index
                                    } else {
                                        currentSelection + index
                                    }
                            },
                        )
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    disabled -> InputField.copy(alpha = 0.55f)
                                    selected -> InputAccent.copy(alpha = 0.08f)
                                    else -> InputField
                                },
                            ).border(
                                1.dp,
                                when {
                                    disabled -> InputOutline.copy(alpha = 0.6f)
                                    selected -> InputAccent.copy(alpha = 0.24f)
                                    else -> InputOutline
                                },
                                RoundedCornerShape(12.dp),
                            ).then(rowModifier)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!disabled) {
                        Box(
                            modifier =
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .border(
                                        1.4.dp,
                                        if (selected) InputAccent else InputOutline,
                                        RoundedCornerShape(5.dp),
                                    ).background(
                                        if (selected) InputAccent.copy(alpha = 0.12f) else Color.Transparent,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(InputAccent),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = option,
                        color = if (disabled) InputTextSecondary else InputTextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (disabled) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.common_ui_installed),
                            color = InputTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.common_ui_no_items_to_display),
                    color = InputTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun sliderColors() =
    SliderDefaults.colors(
        thumbColor = InputAccent,
        activeTrackColor = InputAccent,
        inactiveTrackColor = InputOutline,
        activeTickColor = InputTickHidden,
        inactiveTickColor = InputTickHidden,
    )

private fun snapToStep(
    value: Float,
    step: Int,
    min: Int,
    max: Int,
): Int {
    val rounded = (value / step).roundToInt() * step
    return rounded.coerceIn(min, max)
}

@Composable
private fun ProfileCard(
    state: InputControlsScreenState,
    actions: InputControlsScreenActions,
) {
    val selectionInteraction = remember { MutableInteractionSource() }
    val selectorPressed by selectionInteraction.collectIsPressedAsState()
    val selectorTint by animateFloatAsState(
        targetValue = if (selectorPressed) 1f else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "profileSelectorPressed",
    )

    CardShell {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Color(
                                red = InputField.red,
                                green = InputField.green,
                                blue = InputField.blue,
                                alpha = 0.28f + (0.34f * selectorTint),
                            ),
                        ).border(
                            1.dp,
                            Color(
                                red = InputOutline.red + ((InputAccent.red - InputOutline.red) * selectorTint * 0.45f),
                                green = InputOutline.green + ((InputAccent.green - InputOutline.green) * selectorTint * 0.45f),
                                blue = InputOutline.blue + ((InputAccent.blue - InputOutline.blue) * selectorTint * 0.45f),
                                alpha = 0.8f + (0.15f * selectorTint),
                            ),
                            RoundedCornerShape(12.dp),
                        ).clickable(
                            interactionSource = selectionInteraction,
                            indication = null,
                            onClick = actions.onSelectProfile,
                        ).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileSelectorIconBox(if (selectorPressed) InputTextPrimary else InputAccent)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            state.selectedProfileName
                                ?: stringResource(R.string.input_controls_editor_select_profile),
                        color = InputTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text =
                            if (state.selectedProfileName != null) {
                                stringResource(R.string.common_ui_elements_count, state.selectedProfileElementCount)
                            } else {
                                stringResource(R.string.input_controls_editor_no_profile_selected)
                            },
                        color = InputTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.input_controls_editor_tap_to_switch),
                    color = if (selectorPressed) InputTextPrimary else InputAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            IconActionButton(
                image = Icons.Outlined.SportsEsports,
                contentDescription = stringResource(R.string.input_controls_editor_title),
                onClick = actions.onOpenEditor,
            )
            Spacer(Modifier.width(6.dp))
            IconActionButton(
                image = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.common_ui_add),
                onClick = actions.onAddProfile,
            )
            Spacer(Modifier.width(6.dp))
            IconActionButton(
                image = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.common_ui_edit),
                onClick = actions.onEditProfile,
            )
            Spacer(Modifier.width(6.dp))
            IconActionButton(
                image = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.common_ui_duplicate),
                onClick = actions.onDuplicateProfile,
            )
            Spacer(Modifier.width(6.dp))
            IconActionButton(
                image = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.common_ui_remove),
                tint = InputDanger,
                onClick = actions.onRemoveProfile,
            )
        }
    }
}

@Composable
private fun OverlayOpacityCard(
    state: InputControlsScreenState,
    actions: InputControlsScreenActions,
) {
    CardShell {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Outlined.Visibility, InputTextSecondary)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.input_controls_editor_overlay_opacity),
                        color = InputTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${state.overlayOpacity}%",
                        color = InputTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = state.overlayOpacity.toFloat(),
                onValueChange = {
                    actions.onOverlayOpacityChanged(snapToStep(it, 5, 10, 100))
                },
                valueRange = 10f..100f,
                steps = 17,
                colors = sliderColors(),
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    CardShell(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(InputIconBox),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = InputTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = InputTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconActionButton(
                image = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = title,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun Subcard(
    title: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "subcardChevronRotation",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InputSubcard)
                .border(1.dp, InputOutline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpanded,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputIconBox),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = InputTextSecondary,
                    modifier =
                        Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = InputTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SliderField(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = InputTextSecondary,
            fontSize = 13.sp,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = sliderColors(),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = InputTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CenteredPillButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(34.dp)
                .widthIn(min = 96.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(InputSubcard)
                .border(1.dp, InputOutline, RoundedCornerShape(9.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = InputTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GyroscopeCard(
    state: InputControlsScreenState,
    actions: InputControlsScreenActions,
) {
    CardShell {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Outlined.ScreenRotationAlt, InputTextSecondary)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.session_gyroscope_title),
                    color = InputTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(
                    checked = state.gyroscopeEnabled,
                    onCheckedChange = actions.onGyroscopeEnabledChanged,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.common_ui_mode),
                    color = InputTextSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(12.dp))
                ChipRow(
                    options =
                        listOf(
                            stringResource(R.string.session_gyroscope_hold),
                            stringResource(R.string.session_gyroscope_toggle),
                        ),
                    selectedIndex = state.gyroscopeModeIndex,
                    onSelected = actions.onGyroscopeModeSelected,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_gyroscope_activator_button),
                    color = InputTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                SelectionPill(
                    text = state.gyroscopeActivatorLabel,
                    onClick = actions.onGyroscopeActivatorClick,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_gyroscope_enable_right_stick),
                    color = InputTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.common_ui_experimental),
                    color = InputAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                AppSwitch(
                    checked = state.rightStickGyroEnabled,
                    onCheckedChange = actions.onRightStickGyroChanged,
                )
            }

            Spacer(Modifier.height(8.dp))
            Subcard(
                title = stringResource(R.string.session_gyroscope_calibrate),
                expanded = state.gyroscopeExpanded,
                onToggleExpanded = {
                    actions.onGyroscopeExpandedChanged(!state.gyroscopeExpanded)
                },
            ) {
                Spacer(Modifier.height(10.dp))
                SliderField(
                    label = stringResource(R.string.session_gyroscope_x_sensitivity_format, state.gyroXSensitivity),
                    value = state.gyroXSensitivity.toFloat(),
                    valueRange = 0f..200f,
                    steps = 199,
                    onValueChange = { actions.onGyroXSensitivityChanged(it.roundToInt().coerceIn(0, 200)) },
                )
                Spacer(Modifier.height(8.dp))
                SliderField(
                    label = stringResource(R.string.session_gyroscope_y_sensitivity_format, state.gyroYSensitivity),
                    value = state.gyroYSensitivity.toFloat(),
                    valueRange = 0f..200f,
                    steps = 199,
                    onValueChange = { actions.onGyroYSensitivityChanged(it.roundToInt().coerceIn(0, 200)) },
                )
                Spacer(Modifier.height(8.dp))
                SliderField(
                    label = stringResource(R.string.session_gyroscope_smoothing_format, state.gyroSmoothing),
                    value = state.gyroSmoothing.toFloat(),
                    valueRange = 0f..100f,
                    steps = 99,
                    onValueChange = { actions.onGyroSmoothingChanged(it.roundToInt().coerceIn(0, 100)) },
                )
                Spacer(Modifier.height(8.dp))
                SliderField(
                    label = stringResource(R.string.session_gyroscope_deadzone_format, state.gyroDeadzone),
                    value = state.gyroDeadzone.toFloat(),
                    valueRange = 0f..100f,
                    steps = 99,
                    onValueChange = { actions.onGyroDeadzoneChanged(it.roundToInt().coerceIn(0, 100)) },
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    title = stringResource(R.string.session_gamepad_invert_x),
                    checked = state.invertGyroX,
                    onCheckedChange = actions.onInvertGyroXChanged,
                )
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.session_gamepad_invert_y),
                    checked = state.invertGyroY,
                    onCheckedChange = actions.onInvertGyroYChanged,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { context ->
                            InputControlsView(context, true).apply {
                                actions.onAttachGyroPreview(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            actions.onAttachGyroPreview(view)
                        },
                    )
                    DisposableEffect(Unit) {
                        onDispose { actions.onDetachGyroPreview() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                CenteredPillButton(
                    text = stringResource(R.string.session_gyroscope_reset_stick),
                    onClick = actions.onResetGyroPreview,
                )
            }
        }
    }
}

@Composable
private fun TriggerTypeCard(
    state: InputControlsScreenState,
    actions: InputControlsScreenActions,
) {
    CardShell {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Outlined.Tune, InputTextSecondary)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.session_gamepad_trigger_type),
                    color = InputTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            ChipRow(
                options =
                    listOf(
                        stringResource(R.string.session_gamepad_as_button),
                        stringResource(R.string.session_gamepad_as_axis),
                    ),
                selectedIndex = state.triggerTypeIndex,
                onSelected = actions.onTriggerTypeSelected,
            )
            Spacer(Modifier.height(8.dp))
            Subcard(
                title = stringResource(R.string.session_gamepad_trigger_type),
                expanded = state.triggerCardExpanded,
                onToggleExpanded = {
                    actions.onTriggerCardExpandedChanged(!state.triggerCardExpanded)
                },
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.triggerDescription,
                    color = InputTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun ControllerCard(
    state: InputControllerCardState,
    actions: InputControlsScreenActions,
) {
    CardShell {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(
                    image = Icons.Outlined.SportsEsports,
                    tint = if (state.connected) InputAccent else InputDanger,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.name,
                        color = InputTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${state.bindingCount} ${stringResource(R.string.session_gamepad_bindings)}",
                        color = InputTextSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (state.showBindings && state.bindingCount > 0) {
                    IconActionButton(
                        image = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.common_ui_remove),
                        tint = InputDanger,
                        onClick = { actions.onRemoveController(state.controllerId) },
                    )
                }
            }

            if (state.showBindings) {
                Spacer(Modifier.height(8.dp))
                Subcard(
                    title = stringResource(R.string.session_gamepad_control_bindings),
                    expanded = state.expanded,
                    onToggleExpanded = {
                        actions.onControllerExpandedToggle(state.controllerId)
                    },
                ) {
                    Spacer(Modifier.height(8.dp))
                    if (state.bindings.isEmpty()) {
                        Text(
                            text = stringResource(R.string.session_gamepad_press_any_button),
                            color = InputTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.bindings.forEach { binding ->
                                BindingRow(
                                    controllerId = state.controllerId,
                                    state = binding,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingRow(
    controllerId: String,
    state: InputControllerBindingState,
    actions: InputControlsScreenActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InputSubcard)
                .border(1.dp, InputOutline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.label,
            color = InputTextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        BindingSelectionButton(
            text = state.typeLabel,
            modifier = Modifier.weight(0.8f),
            onClick = { actions.onBindingTypeClick(controllerId, state.keyCode) },
        )
        Spacer(Modifier.width(4.dp))
        BindingSelectionButton(
            text = state.bindingLabel,
            modifier = Modifier.weight(1f),
            onClick = { actions.onBindingValueClick(controllerId, state.keyCode) },
        )
        Spacer(Modifier.width(6.dp))
        IconActionButton(
            image = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.common_ui_remove),
            tint = InputDanger,
            onClick = { actions.onRemoveBinding(controllerId, state.keyCode) },
            size = 28.dp,
        )
    }
}

@Composable
private fun BindingSelectionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(InputField)
                .border(1.dp, InputOutline, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = InputTextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = InputTextSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
}
