package com.winlator.cmod.runtime.input

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.runtime.input.controls.Binding
import com.winlator.cmod.runtime.input.controls.ControlElement
import com.winlator.cmod.runtime.input.controls.ControlsProfile
import com.winlator.cmod.runtime.input.ui.InputControlsView
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.ui.toast.WinToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EditorPanel = Color(0xFF282834)
private val EditorSurface = Color(0xFF2E2E3C)
private val EditorSurfaceAlt = Color(0xFF33333E)
private val EditorField = Color(0xFF24242F)
private val EditorOutline = Color(0xFF3C3C4C)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControlsEditorScreen(profile: ControlsProfile, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val redrawTrigger = remember { mutableIntStateOf(0) }
    val controlsView = remember {
        object : InputControlsView(context) {
            override fun invalidate() {
                super.invalidate()
                redrawTrigger.intValue++
            }
        }.apply {
            setEditMode(true)
            setOverlayOpacity(0.6f)
            setProfile(profile)
        }
    }

    var settingsTarget by remember { mutableStateOf<ControlElement?>(null) }
    var colorTarget by remember { mutableStateOf<ControlElement?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WinNativeBackground),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    controlsView.dispatchTouchEvent(motionEvent)
                },
        ) {
            redrawTrigger.intValue
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w > 0 && h > 0) {
                if (controlsView.measuredWidth != w || controlsView.measuredHeight != h) {
                    controlsView.measure(
                        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
                    )
                    controlsView.layout(0, 0, w, h)
                }
                drawIntoCanvas { canvas ->
                    controlsView.draw(canvas.nativeCanvas)
                }
            }
        }

        FloatingEditorToolbar(
            profileName = profile.name,
            onAdd = {
                if (!controlsView.addElement()) {
                    WinToast.show(context, R.string.input_controls_editor_no_profile_selected)
                }
            },
            onRemove = {
                if (!controlsView.removeElement()) {
                    WinToast.show(context, R.string.input_controls_editor_no_element_selected)
                }
            },
            onSettings = {
                val selected = controlsView.selectedElement
                if (selected != null) settingsTarget = selected
                else WinToast.show(context, R.string.input_controls_editor_no_element_selected)
            },
            onColorPicker = {
                val selected = controlsView.selectedElement
                if (selected != null) colorTarget = selected
                else WinToast.show(context, R.string.input_controls_editor_no_element_selected)
            },
            onDone = {
                profile.save()
                onClose()
            },
        )
    }

    settingsTarget?.let { element ->
        ElementSettingsDialog(
            element = element,
            profile = profile,
            controlsView = controlsView,
            onDismiss = { settingsTarget = null },
        )
    }

    colorTarget?.let { element ->
        ColorPickerDialog(
            element = element,
            profile = profile,
            controlsView = controlsView,
            onDismiss = { colorTarget = null },
        )
    }
}

@Composable
private fun FloatingEditorToolbar(
    profileName: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onSettings: () -> Unit,
    onColorPicker: () -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(with(density) { 12.dp.toPx() }) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        offsetX += dragAmount.x
                        offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = EditorSurface,
            border = BorderStroke(1.dp, EditorOutline),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    tint = WinNativeTextSecondary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.common_ui_profile).uppercase(),
                        color = WinNativeTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.sp,
                    )
                    Text(
                        text = profileName,
                        color = WinNativeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                }
                ToolbarDivider()
                ToolbarIconButton(Icons.Outlined.Add, "Add", onAdd)
                ToolbarIconButton(Icons.Outlined.Delete, "Remove", onRemove)
                ToolbarIconButton(Icons.Outlined.Tune, "Settings", onSettings)
                ToolbarIconButton(Icons.Outlined.ColorLens, "Color", onColorPicker)
                ToolbarDivider()
                ToolbarDoneButton(onClick = onDone)
            }
        }
    }
}

@Composable
private fun ToolbarDoneButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp, end = 2.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(WinNativeAccent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = "Save and exit",
                tint = WinNativeBackground,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Done",
                color = WinNativeBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(EditorOutline),
    )
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = WinNativeTextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ElementSettingsDialog(
    element: ControlElement,
    profile: ControlsProfile,
    controlsView: InputControlsView,
    onDismiss: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val tick = version
    val save: () -> Unit = {
        profile.save()
        controlsView.invalidate()
        version++
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .size(340.dp),
            shape = RoundedCornerShape(16.dp),
            color = EditorPanel,
            border = BorderStroke(1.dp, EditorOutline),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                DialogHeader(
                    typeName = element.type.name.replace('_', ' '),
                    onDone = onDismiss,
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Section(title = "Layout") {
                        LayoutSectionContent(
                            element = element,
                            save = save,
                        )
                    }

                    if (element.type == ControlElement.Type.BUTTON ||
                        element.type == ControlElement.Type.RADIAL_MENU
                    ) {
                        Section(title = "Appearance") {
                            AppearanceSectionContent(
                                element = element,
                                profile = profile,
                                controlsView = controlsView,
                                save = save,
                                onIconChanged = { version++ },
                            )
                        }
                    }

                    if (bindingsVisibleFor(element.type)) {
                        Section(title = "Bindings") {
                            BindingsSection(element = element, onChanged = save)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(
    typeName: String,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(WinNativeAccent),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Configure",
                color = WinNativeTextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
            )
            Text(
                text = typeName,
                color = WinNativeTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDone,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.common_ui_ok),
                color = WinNativeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EditorSurfaceAlt)
            .border(1.dp, EditorOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = WinNativeAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
        )
        content()
    }
}

@Composable
private fun LayoutSectionContent(
    element: ControlElement,
    save: () -> Unit,
) {
    val typeField: @Composable () -> Unit = {
        EnumDropdown(
            label = stringResource(R.string.input_controls_editor_type),
            options = ControlElement.Type.names().toList(),
            selectedIndex = element.type.ordinal,
            onSelected = { idx ->
                val newType = ControlElement.Type.values()[idx]
                if (newType != element.type) {
                    element.type = newType
                    save()
                }
            },
        )
    }

    when (element.type) {
        ControlElement.Type.BUTTON -> FieldPair(
            left = typeField,
            right = {
                EnumDropdown(
                    label = stringResource(R.string.input_controls_editor_shape),
                    options = ControlElement.Shape.names().toList(),
                    selectedIndex = element.shape.ordinal,
                    onSelected = { idx ->
                        element.shape = ControlElement.Shape.values()[idx]
                        save()
                    },
                )
            },
        )
        ControlElement.Type.RANGE_BUTTON -> FieldPair(
            left = typeField,
            right = {
                EnumDropdown(
                    label = stringResource(R.string.input_controls_editor_range),
                    options = ControlElement.Range.names().toList(),
                    selectedIndex = element.range.ordinal,
                    onSelected = { idx ->
                        element.range = ControlElement.Range.values()[idx]
                        save()
                    },
                )
            },
        )
        ControlElement.Type.RADIAL_MENU -> FieldPair(
            left = typeField,
            right = {
                NumberDropdown(
                    label = stringResource(R.string.input_controls_editor_number_of_bindings),
                    value = element.bindingCount,
                    min = 3,
                    max = 12,
                    onChange = { v ->
                        if (element.bindingCount != v) {
                            element.bindingCount = v
                            save()
                        }
                    },
                )
            },
        )
        else -> FieldPair(left = typeField, right = null)
    }

    FieldPair(
        left = {
            InlineSlider(
                label = stringResource(R.string.input_controls_editor_scale),
                valuePercent = (element.scale * 100f).roundToInt().coerceIn(50, 150),
                rangeStart = 50,
                rangeEnd = 150,
                step = 5,
                onChange = { v ->
                    element.scale = v / 100f
                    save()
                },
            )
        },
        right = {
            InlineSlider(
                label = stringResource(R.string.input_controls_editor_opacity),
                valuePercent = (element.opacity * 100f).roundToInt().coerceIn(10, 100),
                rangeStart = 10,
                rangeEnd = 100,
                step = 1,
                onChange = { v ->
                    element.opacity = v / 100f
                    save()
                },
            )
        },
    )

    if (element.type == ControlElement.Type.RANGE_BUTTON) {
        FieldPair(
            left = {
                OrientationSegmented(
                    selectedVertical = element.orientation.toInt() == 1,
                    onChange = { vertical ->
                        element.orientation = (if (vertical) 1 else 0).toByte()
                        save()
                    },
                )
            },
            right = {
                NumberDropdown(
                    label = stringResource(R.string.session_display_columns),
                    value = element.bindingCount,
                    min = 3,
                    max = 8,
                    onChange = { v ->
                        if (element.bindingCount != v) {
                            element.bindingCount = v
                            save()
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun AppearanceSectionContent(
    element: ControlElement,
    profile: ControlsProfile,
    controlsView: InputControlsView,
    save: () -> Unit,
    onIconChanged: () -> Unit,
) {
    FieldPair(
        left = {
            CustomTextField(
                value = element.text ?: "",
                onChange = {
                    element.text = it
                    profile.save()
                    controlsView.invalidate()
                },
            )
        },
        right = if (element.type == ControlElement.Type.BUTTON) {
            {
                ToggleSwitchRow(
                    checked = element.isToggleSwitch,
                    onChange = {
                        element.isToggleSwitch = it
                        save()
                    },
                )
            }
        } else null,
    )
    IconPickerRow(
        selectedId = element.iconId.toInt(),
        onSelected = { id ->
            element.setIconId(id.toInt())
            profile.save()
            controlsView.invalidate()
            onIconChanged()
        },
    )
}

private fun bindingsVisibleFor(type: ControlElement.Type): Boolean = when (type) {
    ControlElement.Type.BUTTON,
    ControlElement.Type.RADIAL_MENU,
    ControlElement.Type.D_PAD,
    ControlElement.Type.STICK,
    ControlElement.Type.TRACKPAD -> true
    ControlElement.Type.RANGE_BUTTON -> false
    else -> false
}

@Composable
private fun FieldPair(
    left: @Composable () -> Unit,
    right: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        left()
        if (right != null) right()
    }
}

@Composable
private fun ToggleSwitchRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.input_controls_editor_toggle_switch),
            color = WinNativeTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (checked) WinNativeAccent else EditorOutline,
                    RoundedCornerShape(8.dp),
                )
                .background(
                    if (checked) WinNativeAccent.copy(alpha = 0.14f) else EditorField,
                )
                .clickable { onChange(!checked) }
                .padding(horizontal = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (checked) WinNativeAccent else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (checked) WinNativeAccent else WinNativeTextSecondary,
                        RoundedCornerShape(4.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = WinNativeBackground,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (checked) "On" else "Off",
                color = WinNativeTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EnumDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = WinNativeTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, EditorOutline, RoundedCornerShape(8.dp))
                    .background(EditorField)
                    .clickable { expanded = !expanded }
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = options.getOrElse(selectedIndex) { "" },
                    color = WinNativeTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = WinNativeTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(EditorPanel),
            ) {
                options.forEachIndexed { idx, opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                color = if (idx == selectedIndex) WinNativeAccent else WinNativeTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (idx == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(idx)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberDropdown(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val options = (min..max).map { it.toString() }
    EnumDropdown(
        label = label,
        options = options,
        selectedIndex = (value - min).coerceIn(0, max - min),
        onSelected = { idx -> onChange(min + idx) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineSlider(
    label: String,
    valuePercent: Int,
    rangeStart: Int,
    rangeEnd: Int,
    step: Int,
    onChange: (Int) -> Unit,
) {
    var current by remember(label) { mutableIntStateOf(valuePercent) }
    LaunchedEffect(valuePercent) { current = valuePercent }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = WinNativeTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$current%",
                color = WinNativeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = current.toFloat(),
            onValueChange = {
                val rounded = (it / step).toInt() * step
                current = rounded.coerceIn(rangeStart, rangeEnd)
            },
            onValueChangeFinished = { onChange(current) },
            valueRange = rangeStart.toFloat()..rangeEnd.toFloat(),
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = WinNativeAccent,
                activeTrackColor = WinNativeAccent,
                inactiveTrackColor = EditorOutline,
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(WinNativeAccent),
                )
            },
            track = { sliderState ->
                val range = sliderState.valueRange
                val span = range.endInclusive - range.start
                val fraction = if (span > 0f) {
                    ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(EditorOutline),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(WinNativeAccent),
                    )
                }
            },
            modifier = Modifier.height(28.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrientationSegmented(
    selectedVertical: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.session_display_orientation),
            color = WinNativeTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentedButton(
                selected = !selectedVertical,
                onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = WinNativeAccent.copy(alpha = 0.18f),
                    activeContentColor = WinNativeAccent,
                    activeBorderColor = WinNativeAccent,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = WinNativeTextPrimary,
                    inactiveBorderColor = EditorOutline,
                ),
            ) {
                Text(stringResource(R.string.session_display_horizontal))
            }
            SegmentedButton(
                selected = selectedVertical,
                onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = WinNativeAccent.copy(alpha = 0.18f),
                    activeContentColor = WinNativeAccent,
                    activeBorderColor = WinNativeAccent,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = WinNativeTextPrimary,
                    inactiveBorderColor = EditorOutline,
                ),
            ) {
                Text(stringResource(R.string.session_display_vertical))
            }
        }
    }
}

@Composable
private fun CustomTextField(
    value: String,
    onChange: (String) -> Unit,
) {
    val placeholder = stringResource(R.string.common_ui_none)
    Column {
        Text(
            text = stringResource(R.string.common_ui_custom_text),
            color = WinNativeTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, EditorOutline, RoundedCornerShape(8.dp))
                .background(EditorField)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 8) onChange(it) },
                singleLine = true,
                textStyle = TextStyle(
                    color = WinNativeTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(WinNativeAccent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = WinNativeTextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun IconPickerRow(
    selectedId: Int,
    onSelected: (Byte) -> Unit,
) {
    val context = LocalContext.current
    val iconIds = remember {
        runCatching {
            context.assets.list("inputcontrols/icons/")
                ?.mapNotNull { it.substringBeforeLast('.').toByteOrNull() }
                ?.sorted()
                ?: emptyList()
        }.getOrDefault(emptyList())
    }
    val icons = remember(iconIds) {
        iconIds.associateWith { id ->
            runCatching {
                context.assets.open("inputcontrols/icons/$id.png").use { stream ->
                    BitmapFactory.decodeStream(stream).asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Column {
        Text(
            text = stringResource(R.string.common_ui_icon),
            color = WinNativeTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(iconIds) { id ->
                val bitmap = icons[id]
                val isSelected = id.toInt() == selectedId
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isSelected) WinNativeAccent.copy(alpha = 0.18f)
                            else EditorField,
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) WinNativeAccent else EditorOutline,
                            shape = RoundedCornerShape(7.dp),
                        )
                        .clickable { onSelected(id) }
                        .padding(5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            colorFilter = ColorFilter.tint(WinNativeTextPrimary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingsSection(
    element: ControlElement,
    onChanged: () -> Unit,
) {
    val titles = when (element.type) {
        ControlElement.Type.BUTTON -> listOf(
            stringResource(R.string.input_controls_editor_binding),
            stringResource(R.string.binding_secondary),
        )
        ControlElement.Type.RADIAL_MENU ->
            (0 until element.bindingCount).map { "Binding ${it + 1}" }
        ControlElement.Type.D_PAD,
        ControlElement.Type.STICK,
        ControlElement.Type.TRACKPAD -> listOf(
            stringResource(R.string.input_controls_editor_binding_up),
            stringResource(R.string.input_controls_editor_binding_right),
            stringResource(R.string.input_controls_editor_binding_down),
            stringResource(R.string.input_controls_editor_binding_left),
        )
        else -> return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.indices.chunked(2).forEach { pair ->
            val leftIndex = pair[0]
            val rightIndex = pair.getOrNull(1)
            FieldPair(
                left = {
                    BindingRow(
                        title = titles[leftIndex],
                        element = element,
                        index = leftIndex,
                        onChanged = onChanged,
                    )
                },
                right = if (rightIndex != null) {
                    {
                        BindingRow(
                            title = titles[rightIndex],
                            element = element,
                            index = rightIndex,
                            onChanged = onChanged,
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun BindingRow(
    title: String,
    element: ControlElement,
    index: Int,
    onChanged: () -> Unit,
) {
    val typeEntries = stringArrayResource(R.array.binding_type_entries).toList()
    val current = element.getBindingAt(index)
    val initialTypeIndex = remember(current, element.type) {
        when {
            current.isMouse -> 1
            current.isGamepad ||
                (current == Binding.NONE &&
                    (element.type == ControlElement.Type.STICK ||
                        element.type == ControlElement.Type.D_PAD)) -> 2
            else -> 0
        }
    }
    var typeIndex by remember(element, index) { mutableIntStateOf(initialTypeIndex) }

    val (labels, values) = when (typeIndex) {
        1 -> Binding.mouseBindingLabels().toList() to Binding.mouseBindingValues().toList()
        2 -> Binding.gamepadBindingLabels().toList() to Binding.gamepadBindingValues().toList()
        else -> Binding.keyboardBindingLabels().toList() to Binding.keyboardBindingValues().toList()
    }

    val selectedBindingIndex = values.indexOfFirst { it == current }.let {
        if (it < 0) 0 else it
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            text = title,
            color = WinNativeTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                EnumDropdown(
                    label = "",
                    options = typeEntries,
                    selectedIndex = typeIndex.coerceIn(0, typeEntries.lastIndex),
                    onSelected = { typeIndex = it },
                )
            }
            Box(modifier = Modifier.weight(1.2f)) {
                EnumDropdown(
                    label = "",
                    options = labels,
                    selectedIndex = selectedBindingIndex,
                    onSelected = { idx ->
                        val newBinding = values.getOrNull(idx) ?: Binding.NONE
                        if (newBinding != element.getBindingAt(index)) {
                            element.setBindingAt(index, newBinding)
                            onChanged()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    element: ControlElement,
    profile: ControlsProfile,
    controlsView: InputControlsView,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember {
        FloatArray(3).also {
            val src = if (element.customColor != -1) element.customColor else 0xFFFFFFFF.toInt()
            AndroidColor.colorToHSV(src, it)
        }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var bright by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = AndroidColor.HSVToColor(floatArrayOf(hue, sat, bright))
    var hex by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and currentColor)) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentColor) {
        element.customColor = currentColor
        controlsView.invalidate()
        val typedColor = runCatching {
            AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        }.getOrNull()
        if (typedColor != currentColor) {
            hex = String.format("#%06X", 0xFFFFFF and currentColor)
        }
    }

    val close: () -> Unit = {
        profile.save()
        onDismiss()
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .width(280.dp)
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(16.dp),
                color = EditorPanel,
                border = BorderStroke(1.dp, EditorOutline),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Color",
                        color = WinNativeTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                    )
                    SaturationValuePlane(
                        hue = hue,
                        saturation = sat,
                        brightness = bright,
                        onChange = { s, v ->
                            sat = s
                            bright = v
                        },
                    )
                    HueSlider(
                        hue = hue,
                        onChange = { hue = it },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(currentColor))
                                .border(1.dp, EditorOutline, CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, EditorOutline, RoundedCornerShape(8.dp))
                                .background(EditorField)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = hex,
                                onValueChange = { typed ->
                                    hex = typed
                                    val cleaned = if (typed.startsWith("#")) typed else "#$typed"
                                    runCatching { AndroidColor.parseColor(cleaned) }.onSuccess { parsed ->
                                        if (parsed != currentColor) {
                                            val arr = FloatArray(3)
                                            AndroidColor.colorToHSV(parsed, arr)
                                            hue = arr[0]
                                            sat = arr[1]
                                            bright = arr[2]
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = WinNativeTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(WinNativeAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch {
                                                delay(250)
                                                scrollState.animateScrollTo(scrollState.maxValue)
                                            }
                                        }
                                    },
                                decorationBox = { inner ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (hex.isEmpty()) {
                                            Text(
                                                text = "#RRGGBB",
                                                color = WinNativeTextSecondary,
                                                fontSize = 11.sp,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                element.customColor = -1
                                controlsView.invalidate()
                                profile.save()
                                onDismiss()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = "Reset",
                                color = WinNativeTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(
                            onClick = close,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.common_ui_ok),
                                color = WinNativeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaturationValuePlane(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    val pureHue = remember(hue) { Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, EditorOutline, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun apply(off: Offset) {
                        val s = (off.x / w).coerceIn(0f, 1f)
                        val v = (1f - off.y / h).coerceIn(0f, 1f)
                        onChange(s, v)
                    }
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        event.changes.forEach { ch ->
                            if (ch.pressed) {
                                apply(ch.position)
                                ch.consume()
                            }
                        }
                        if (!anyPressed) break
                    }
                }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pureHue)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = saturation * size.width
        val cy = (1f - brightness) * size.height
        val r = 7.dp.toPx()
        drawCircle(
            color = Color.Black.copy(alpha = 0.6f),
            radius = r + 1f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = Color.White,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val hueColors = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map {
            Color(AndroidColor.HSVToColor(floatArrayOf(it, 1f, 1f)))
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    fun apply(off: Offset) {
                        onChange(((off.x / w).coerceIn(0f, 1f)) * 360f)
                    }
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        event.changes.forEach { ch ->
                            if (ch.pressed) {
                                apply(ch.position)
                                ch.consume()
                            }
                        }
                        if (!anyPressed) break
                    }
                }
            },
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(hueColors),
            cornerRadius = CornerRadius(radius, radius),
        )
        val cx = ((hue / 360f).coerceIn(0f, 1f)) * size.width
        drawCircle(
            color = Color.White,
            radius = radius - 1f,
            center = Offset(cx, size.height / 2f),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = radius - 1f,
            center = Offset(cx, size.height / 2f),
            style = Stroke(width = 0.5.dp.toPx()),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun darkOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WinNativeTextPrimary,
    unfocusedTextColor = WinNativeTextPrimary,
    disabledTextColor = WinNativeTextSecondary,
    focusedBorderColor = WinNativeAccent,
    unfocusedBorderColor = EditorOutline,
    cursorColor = WinNativeAccent,
    focusedContainerColor = EditorField,
    unfocusedContainerColor = EditorField,
    focusedLabelColor = WinNativeTextSecondary,
    unfocusedLabelColor = WinNativeTextSecondary,
)
