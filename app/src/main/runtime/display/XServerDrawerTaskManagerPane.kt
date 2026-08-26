package com.winlator.cmod.runtime.display

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import com.winlator.cmod.shared.ui.focus.controllerMenuInput
import com.winlator.cmod.shared.ui.focus.controllerFocusBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativePanel
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.nav.DialogPaneNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav as SharedLocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry as SharedPaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem as sharedPaneNavItem
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import com.winlator.cmod.shared.ui.widget.chasingBorder
import kotlin.math.roundToInt

// Task-manager pane composables, split out of XServerDrawerMenu.kt (behavior-identical).

@Composable
internal fun TaskManagerPaneContent(
    taskManagerState: TaskManagerPaneState,
    listener: XServerDrawerActionListener,
    onClose: () -> Unit,
) {
    var showNewTaskDialog by remember { mutableStateOf(false) }
    var processPendingEnd by remember { mutableStateOf<TaskManagerProcess?>(null) }
    var expandedAffinityPid by remember { mutableStateOf<Int?>(null) }
    val pendingAffinities = remember { mutableStateMapOf<Int, PendingTaskAffinity>() }

    DisposableEffect(Unit) {
        listener.onTaskManagerVisibilityChanged(true)
        onDispose { listener.onTaskManagerVisibilityChanged(false) }
    }

    LaunchedEffect(taskManagerState.processes) {
        val visibleProcessPids = taskManagerState.processes.map { it.pid }.toSet()
        val now = System.currentTimeMillis()
        pendingAffinities.keys.toList().forEach { pid ->
            if (pid !in visibleProcessPids) pendingAffinities.remove(pid)
        }
        taskManagerState.processes.forEach { process ->
            val pending = pendingAffinities[process.pid]
            if (
                pending != null &&
                    (pending.affinityMask == process.affinityMask ||
                        now - pending.requestedAtMillis > PendingTaskAffinityTimeoutMs)
            ) {
                pendingAffinities.remove(process.pid)
            }
        }
        if (expandedAffinityPid != null && expandedAffinityPid !in visibleProcessPids) {
            expandedAffinityPid = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        val affinityCoreCount =
            if (taskManagerState.cpuCoreCount > 0) {
                taskManagerState.cpuCoreCount
            } else {
                Runtime.getRuntime().availableProcessors()
            }
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
                TaskManagerHeader(
                    cpuPercent = taskManagerState.cpuPercent,
                    cpuCoreCount = taskManagerState.cpuCoreCount,
                    cpuCorePercents = taskManagerState.cpuCorePercents,
                    memoryPercent = taskManagerState.memoryPercent,
                    memoryDetail = taskManagerState.memoryDetail,
                    onNewTask = { showNewTaskDialog = true },
                    onClose = onClose,
                    onCpuExpandedChanged = listener::onTaskManagerCpuExpandedChanged,
                )

                TaskManagerProcessHeader()

                Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                    if (taskManagerState.processes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.common_ui_no_items_to_display),
                            color = DrawerTextSecondary,
                            fontSize = (13f * paneScale).sp,
                            modifier = Modifier.fillMaxWidth().padding(top = (24f * paneScale).dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy((12f * paneScale).dp),
                        ) {
                            taskManagerState.processes.forEach { process ->
                                key(process.pid) {
                                    val selectedAffinityMask =
                                        pendingAffinities[process.pid]?.affinityMask ?: process.affinityMask
                                    TaskManagerProcessCard(
                                        process = process,
                                        expanded = expandedAffinityPid == process.pid,
                                        affinityMask = selectedAffinityMask,
                                        coreCount = affinityCoreCount,
                                        onToggleAffinity = {
                                            expandedAffinityPid =
                                                if (expandedAffinityPid == process.pid) null else process.pid
                                        },
                                        onAffinityMaskChanged = { affinityMask ->
                                            pendingAffinities[process.pid] =
                                                PendingTaskAffinity(affinityMask, System.currentTimeMillis())
                                            listener.onTaskManagerSetAffinity(process.pid, affinityMask)
                                        },
                                        onEndProcess = { processPendingEnd = process },
                                        onBringToFront = { listener.onTaskManagerBringToFront(process.name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewTaskDialog) {
        TaskManagerNewTaskDialog(
            onDismiss = { showNewTaskDialog = false },
            onConfirm = { command ->
                showNewTaskDialog = false
                listener.onTaskManagerNewTask(command)
            },
        )
    }

    processPendingEnd?.let { process ->
        TaskManagerEndProcessDialog(
            process = process,
            onDismiss = { processPendingEnd = null },
            onConfirm = {
                processPendingEnd = null
                listener.onTaskManagerEndProcess(process.name)
            },
        )
    }
}

@Composable
internal fun TaskManagerHeader(
    cpuPercent: Int,
    cpuCoreCount: Int,
    cpuCorePercents: List<Int>,
    memoryPercent: Int,
    memoryDetail: String,
    onNewTask: () -> Unit,
    onClose: () -> Unit,
    onCpuExpandedChanged: (Boolean) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    var cpuExpanded by remember { mutableStateOf(false) }
    DisposableEffect(cpuExpanded) {
        onCpuExpandedChanged(cpuExpanded)
        onDispose { if (cpuExpanded) onCpuExpandedChanged(false) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.session_task_title),
            color = DrawerTextPrimary,
            fontSize = (16f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        TaskManagerCloseButton(onClick = onClose)
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskManagerStatTile(
            title = stringResource(R.string.session_task_cpu_usage_format, cpuPercent),
            detail =
                if (cpuCoreCount > 0) {
                    pluralStringResource(R.plurals.session_task_core_count, cpuCoreCount, cpuCoreCount)
                } else {
                    ""
                },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            selected = cpuExpanded,
            onClick = { cpuExpanded = !cpuExpanded },
        )
        TaskManagerStatTile(
            title = stringResource(R.string.session_task_memory) + " ($memoryPercent%)",
            detail = memoryDetail,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }

    AnimatedVisibility(
        visible = cpuExpanded && cpuCorePercents.isNotEmpty(),
        enter =
            fadeIn(animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing)) +
                expandVertically(
                    animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
        exit =
            fadeOut(animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing)) +
                shrinkVertically(
                    animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
    ) {
        TaskManagerCpuCoreGrid(cpuCorePercents = cpuCorePercents)
    }

    TaskManagerNewTaskButton(onClick = onNewTask)
}

@Composable
internal fun TaskManagerCloseButton(onClick: () -> Unit) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(0),
        label = "taskManagerCloseBg",
    )
    val size = (38f * paneScale).dp
    val shape = RoundedCornerShape((10f * paneScale).dp)
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(bgColor)
                .border(1.dp, RestingCardBorder, shape)
                .paneNavItem(cornerRadius = (10f * paneScale).dp, onActivate = onClick)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.common_ui_close),
            tint = DrawerTextPrimary,
            modifier = Modifier.size((22f * paneScale).dp),
        )
    }
}

@Composable
internal fun TaskManagerStatTile(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((10f * paneScale).dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue =
            when {
                pressed -> PaneInnerPressed
                else -> PaneInnerResting
            },
        animationSpec = tween(0),
        label = "taskManagerStatTileBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) DrawerAccent else RestingCardBorder,
        animationSpec = tween(0),
        label = "taskManagerStatTileBorder",
    )
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .then(clickModifier)
                .padding(horizontal = (8f * paneScale).dp, vertical = (6f * paneScale).dp),
        verticalArrangement = Arrangement.spacedBy((1f * paneScale).dp),
    ) {
        Text(
            text = title,
            color = DrawerAccent,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = detail,
            color = DrawerTextSecondary,
            fontSize = (10f * paneScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskManagerCpuCoreGrid(cpuCorePercents: List<Int>) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((10f * paneScale).dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PaneInnerResting)
                .border(1.dp, RestingCardBorder, shape)
                .padding(horizontal = (8f * paneScale).dp, vertical = (6f * paneScale).dp),
        verticalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
    ) {
        Text(
            text = stringResource(R.string.session_task_per_core_usage),
            color = DrawerTextPrimary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
            verticalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
        ) {
            cpuCorePercents.forEachIndexed { index, percent ->
                TaskManagerCpuCoreChip(coreIndex = index, percent = percent)
            }
        }
    }
}

@Composable
internal fun TaskManagerCpuCoreChip(coreIndex: Int, percent: Int) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((6f * paneScale).dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(PaneSurfaceColor)
                .border(1.dp, RestingCardBorder, shape)
                .padding(horizontal = (6f * paneScale).dp, vertical = (3f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
    ) {
        Text(
            text = stringResource(R.string.session_task_core_label, coreIndex),
            color = DrawerTextSecondary,
            fontSize = (10f * paneScale).sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "$percent%",
            color = DrawerAccent,
            fontSize = (10f * paneScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun TaskManagerNewTaskButton(onClick: () -> Unit) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(0),
        label = "taskManagerNewTaskBg",
    )
    val tint = if (pressed) DrawerAccent.copy(alpha = 0.76f) else DrawerAccent
    val shape = RoundedCornerShape((12f * paneScale).dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(1.dp, RestingCardBorder, shape)
                .paneNavItem(cornerRadius = (12f * paneScale).dp, onActivate = onClick)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((18f * paneScale).dp),
        )
        Spacer(Modifier.width((6f * paneScale).dp))
        Text(
            text = stringResource(R.string.session_task_new_task),
            color = tint,
            fontSize = (14f * paneScale).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskManagerAffinityOptions(
    affinityMask: Int,
    coreCount: Int,
    onAffinityMaskChanged: (Int) -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val effectiveCoreCount = coreCount.coerceAtLeast(1).coerceAtMost(32)
    val selectedMask = sanitizeTaskAffinityMask(affinityMask, effectiveCoreCount)
    val fullMask = taskAffinityFullMask(effectiveCoreCount)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = (8f * paneScale).dp,
                    end = (8f * paneScale).dp,
                    bottom = (8f * paneScale).dp,
                ),
        verticalArrangement = Arrangement.spacedBy((7f * paneScale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((6f * paneScale).dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = DrawerAccent,
                modifier = Modifier.size((15f * paneScale).dp),
            )
            Text(
                text = stringResource(R.string.session_task_affinity_title),
                color = DrawerTextPrimary,
                fontSize = (12f * paneScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((5f * paneScale).dp),
            verticalArrangement = Arrangement.spacedBy((5f * paneScale).dp),
        ) {
            TaskManagerAffinityChip(
                label = stringResource(R.string.session_task_affinity_all_cores),
                selected = selectedMask == fullMask,
                onClick = { onAffinityMaskChanged(fullMask) },
            )
            for (coreIndex in 0 until effectiveCoreCount) {
                val bit = 1 shl coreIndex
                TaskManagerAffinityChip(
                    label = stringResource(R.string.session_task_core_label, coreIndex),
                    selected = (selectedMask and bit) != 0,
                    onClick = {
                        val nextMask =
                            if ((selectedMask and bit) != 0) {
                                selectedMask and bit.inv()
                            } else {
                                selectedMask or bit
                            }
                        if ((nextMask and fullMask) != 0) {
                            onAffinityMaskChanged(nextMask and fullMask)
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun TaskManagerAffinityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor =
        if (selected) {
            DrawerAccent.copy(alpha = 0.16f)
        } else {
            PaneInnerResting
        }
    val borderColor = if (selected) DrawerAccent.copy(alpha = 0.56f) else RestingCardBorder
    val textColor = if (selected) DrawerAccent else DrawerTextPrimary
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .paneNavItem(cornerRadius = 8.dp, onActivate = onClick)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = DrawerAccent.copy(alpha = if (selected) 1f else 0f),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

internal fun taskAffinityFullMask(coreCount: Int): Int {
    var mask = 0
    for (index in 0 until coreCount.coerceAtLeast(1).coerceAtMost(32)) {
        mask = mask or (1 shl index)
    }
    return mask
}

internal fun sanitizeTaskAffinityMask(affinityMask: Int, coreCount: Int): Int {
    val fullMask = taskAffinityFullMask(coreCount)
    val sanitizedMask = affinityMask and fullMask
    return if (sanitizedMask != 0) sanitizedMask else fullMask
}

@Composable
internal fun TaskManagerActionPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val paneScale = LocalPaneScale.current
    val density = LocalDensity.current
    val gapPx = with(density) { (4f * paneScale).dp.roundToPx() }
    val shape = RoundedCornerShape((12f * paneScale).dp)
    Popup(
        popupPositionProvider = remember(gapPx) { TaskManagerPopupPositionProvider(gapPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = (150f * paneScale).dp, max = (240f * paneScale).dp)
                    .clip(shape)
                    .background(PaneSurfaceColor)
                    .border(1.dp, RestingCardBorder, shape)
                    .padding((5f * paneScale).dp),
            verticalArrangement = Arrangement.spacedBy((4f * paneScale).dp),
            content = content,
        )
    }
}

@Composable
internal fun TaskManagerActionPopupItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) DrawerAccent.copy(alpha = 0.16f) else PaneInnerResting,
        animationSpec = tween(0),
        label = "taskManagerPopupItem",
    )
    val shape = RoundedCornerShape((8f * paneScale).dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(1.dp, RestingCardBorder, shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = (12f * paneScale).dp, vertical = (10f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((8f * paneScale).dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DrawerAccent,
                modifier = Modifier.size((16f * paneScale).dp),
            )
        }
        Text(
            text = label,
            color = DrawerTextPrimary,
            fontSize = (13f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TaskManagerEndProcessDialog(
    process: TaskManagerProcess,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val displayName = if (process.isWow64) "${process.name} *32" else process.name
    val shape = RoundedCornerShape(12.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 292.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(PaneSurfaceColor)
                        .border(1.dp, GlassExitTint.copy(alpha = 0.32f), shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = GlassExitTint,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = stringResource(R.string.session_task_end_process),
                        color = DrawerTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = displayName,
                    color = DrawerTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.session_task_confirm_end_process),
                    color = DrawerTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskManagerDialogButton(
                        label = stringResource(R.string.common_ui_cancel),
                        textColor = DrawerTextPrimary,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        onClick = onDismiss,
                    )
                    TaskManagerDialogButton(
                        label = stringResource(R.string.session_task_end_process),
                        textColor = GlassExitTint,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        fontWeight = FontWeight.Medium,
                        backgroundColor = GlassExitTint.copy(alpha = 0.12f),
                        borderColor = GlassExitTint.copy(alpha = 0.34f),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskManagerNewTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val customLabel = stringResource(R.string.session_task_custom_value)
    var selectedLabel by remember { mutableStateOf(NEW_TASK_PRESETS.first()) }
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(14.dp)
    val fieldShape = RoundedCornerShape(10.dp)

    fun submit() {
        val command = if (customMode) customText.trim() else selectedLabel.trim().lowercase()
        if (command.isNotEmpty()) onConfirm(command)
    }

    LaunchedEffect(customMode) {
        if (customMode) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 310.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(PaneSurfaceColor)
                        .border(1.dp, RestingCardBorder, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = DrawerAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.session_task_new_task),
                        color = DrawerTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    if (customMode) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle =
                                androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                                    color = DrawerTextPrimary,
                                    fontSize = 13.sp,
                                ),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DrawerAccent,
                                    unfocusedBorderColor = RestingCardBorder,
                                    focusedTextColor = DrawerTextPrimary,
                                    unfocusedTextColor = DrawerTextPrimary,
                                    focusedContainerColor = PaneInnerResting,
                                    unfocusedContainerColor = PaneInnerResting,
                                    cursorColor = DrawerAccent,
                                ),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    tint = DrawerTextSecondary,
                                    modifier =
                                        Modifier
                                            .size(22.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { dropdownExpanded = true },
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        submit()
                                    },
                                ),
                        )
                    } else {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(fieldShape)
                                    .background(PaneInnerResting)
                                    .border(1.dp, RestingCardBorder, fieldShape)
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = selectedLabel,
                                color = DrawerTextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = DrawerTextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    TaskManagerActionPopup(
                        expanded = dropdownExpanded,
                        onDismiss = { dropdownExpanded = false },
                    ) {
                        NEW_TASK_PRESETS.forEach { item ->
                            TaskManagerActionPopupItem(
                                label = item,
                                onClick = {
                                    selectedLabel = item
                                    customMode = false
                                    dropdownExpanded = false
                                },
                            )
                        }
                        TaskManagerActionPopupItem(
                            label = customLabel,
                            onClick = {
                                customMode = true
                                dropdownExpanded = false
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskManagerDialogButton(
                        label = stringResource(R.string.common_ui_cancel),
                        textColor = DrawerTextPrimary,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        onClick = onDismiss,
                    )
                    TaskManagerDialogButton(
                        label = stringResource(R.string.common_ui_ok),
                        textColor = DrawerAccent,
                        modifier = Modifier.height(34.dp),
                        verticalPadding = 0.dp,
                        backgroundColor = DrawerAccent.copy(alpha = 0.12f),
                        borderColor = DrawerAccent.copy(alpha = 0.34f),
                        onClick = {
                            keyboardController?.hide()
                            submit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskManagerDialogButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = PaneInnerResting,
    borderColor: Color = RestingCardBorder,
    fontWeight: FontWeight = FontWeight.SemiBold,
    verticalPadding: Dp = 8.dp,
) {
    Box(
        modifier =
            modifier
                .widthIn(min = 72.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(backgroundColor)
                .border(1.dp, borderColor, RoundedCornerShape(9.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TaskManagerProcessHeader() {
    val paneScale = LocalPaneScale.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = (4f * paneScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.session_task_process_name),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.session_task_pid),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width((54f * paneScale).dp),
        )
        Text(
            text = stringResource(R.string.session_task_memory),
            color = DrawerTextSecondary,
            fontSize = (11f * paneScale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width((78f * paneScale).dp),
        )
        Spacer(modifier = Modifier.width((46f * paneScale).dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskManagerProcessCard(
    process: TaskManagerProcess,
    expanded: Boolean,
    affinityMask: Int,
    coreCount: Int,
    onToggleAffinity: () -> Unit,
    onAffinityMaskChanged: (Int) -> Unit,
    onEndProcess: () -> Unit,
    onBringToFront: () -> Unit,
) {
    val paneScale = LocalPaneScale.current
    val shape = RoundedCornerShape((8f * paneScale).dp)
    val displayName = if (process.isWow64) "${process.name} *32" else process.name
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    var menuExpanded by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (pressed) PaneInnerPressed else PaneInnerResting,
        animationSpec = tween(0),
        label = "taskManagerProcessRowBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) DrawerAccent.copy(alpha = 0.62f) else RestingCardBorder,
        animationSpec = tween(0),
        label = "taskManagerProcessCardBorder",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape),
    ) {
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .paneNavItem(
                            cornerRadius = (8f * paneScale).dp,
                            onActivate = onToggleAffinity,
                            onSecondary = onBringToFront,
                        )
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggleAffinity,
                            onLongClick = { menuExpanded = true },
                        )
                        .padding(horizontal = (8f * paneScale).dp, vertical = (6f * paneScale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    color = DrawerTextPrimary,
                    fontSize = (12f * paneScale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = process.pid.toString(),
                    color = DrawerTextSecondary,
                    fontSize = (12f * paneScale).sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width((54f * paneScale).dp),
                )
                Text(
                    text = process.memoryFormatted,
                    color = DrawerTextSecondary,
                    fontSize = (12f * paneScale).sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width((78f * paneScale).dp),
                )
                Spacer(modifier = Modifier.width((10f * paneScale).dp))
                Box(
                    Modifier.paneNavItem(cornerRadius = (8f * paneScale).dp, onActivate = onEndProcess),
                ) {
                    TaskManagerEndButton(onClick = onEndProcess)
                }
            }

            TaskManagerActionPopup(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
            ) {
                TaskManagerActionPopupItem(
                    label = stringResource(R.string.session_task_bring_to_front),
                    icon = Icons.Outlined.Monitor,
                    onClick = {
                        menuExpanded = false
                        onBringToFront()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter =
                fadeIn(animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ),
            exit =
                fadeOut(animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 0, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ),
        ) {
            TaskManagerAffinityOptions(
                affinityMask = affinityMask,
                coreCount = coreCount,
                onAffinityMaskChanged = onAffinityMaskChanged,
            )
        }
    }
}

@Composable
internal fun TaskManagerEndButton(onClick: () -> Unit) {
    val paneScale = LocalPaneScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val bgColor by animateColorAsState(
        targetValue = if (pressed) TileExitPressed else TileExitResting,
        animationSpec = tween(0),
        label = "taskManagerEndBtn",
    )
    val size = (32f * paneScale).dp
    val shape = RoundedCornerShape((8f * paneScale).dp)
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(bgColor)
                .border(1.dp, GlassExitTint.copy(alpha = 0.34f), shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.session_task_end_process),
            tint = GlassExitTint,
            modifier = Modifier.size((16f * paneScale).dp),
        )
    }
}
