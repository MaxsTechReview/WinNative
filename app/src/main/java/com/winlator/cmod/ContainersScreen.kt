package com.winlator.cmod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.container.Container
import java.util.Locale

private val ContainersBg = Color(0xFF18181D)
private val ContainersCard = Color(0xFF1C1C2A)
private val ContainersSubcard = Color(0xFF161622)
private val ContainersOutline = Color(0xFF2A2A3A)
private val ContainersIconBox = Color(0xFF242434)
private val ContainersAccent = Color(0xFF1A9FFF)
private val ContainersTextPrimary = Color(0xFFF0F4FF)
private val ContainersTextSecondary = Color(0xFF7A8FA8)
private val ContainersDanger = Color(0xFFFF7A88)

data class ContainersScreenState(
    val containers: List<Container> = emptyList(),
    val dialog: ContainersDialogUiState = ContainersDialogUiState.None,
)

sealed interface ContainersDialogUiState {
    data object None : ContainersDialogUiState
    data class ConfirmDuplicate(val container: Container) : ContainersDialogUiState
    data class ConfirmRemove(val container: Container) : ContainersDialogUiState
    data class Backups(val container: Container) : ContainersDialogUiState
    data class BackupSelection(
        val container: Container,
        val backupNames: List<String>,
    ) : ContainersDialogUiState
    data class StorageInfo(val data: ContainerStorageInfoUiState) : ContainersDialogUiState
    data class Message(
        val title: String,
        val message: String,
    ) : ContainersDialogUiState
}

data class ContainerStorageInfoUiState(
    val container: Container,
    val driveCBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val usedPercent: Float = 0f,
    val isLoading: Boolean = true,
)

@Composable
fun ContainersScreen(
    state: ContainersScreenState,
    onAddContainer: () -> Unit,
    onRunContainer: (Container) -> Unit,
    onEditContainer: (Container) -> Unit,
    onDuplicateContainer: (Container) -> Unit,
    onShowBackups: (Container) -> Unit,
    onRemoveContainer: (Container) -> Unit,
    onShowInfo: (Container) -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmDuplicateDialog: (Container) -> Unit,
    onConfirmRemoveDialog: (Container) -> Unit,
    onConfirmBackupDialog: (Container) -> Unit,
    onConfirmRestoreDialog: (Container) -> Unit,
    onClearCacheDialog: (Container) -> Unit,
    onBackupSelectionChosen: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ContainersBg)
            .padding(start = 16.dp, top = 16.dp, end = 26.dp, bottom = 16.dp),
    ) {
        SectionLabel(text = stringResource(R.string.common_ui_containers))
        if (state.containers.isEmpty()) {
            Text(
                text = stringResource(R.string.common_ui_no_items_to_display),
                color = ContainersTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 14.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "add") {
                AddContainerCard(onClick = onAddContainer)
            }
            items(
                items = state.containers,
                key = { container -> container.id },
            ) { container ->
                ContainerCard(
                    container = container,
                    onRun = { onRunContainer(container) },
                    onEdit = { onEditContainer(container) },
                    onDuplicate = { onDuplicateContainer(container) },
                    onShowBackups = { onShowBackups(container) },
                    onRemove = { onRemoveContainer(container) },
                    onShowInfo = { onShowInfo(container) },
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        ContainersDialogUiState.None -> Unit
        is ContainersDialogUiState.ConfirmDuplicate -> {
            ContainersConfirmDialog(
                message = stringResource(R.string.containers_list_confirm_duplicate),
                confirmLabel = stringResource(R.string.common_ui_duplicate),
                confirmColor = ContainersAccent,
                onDismiss = onDismissDialog,
                onConfirm = { onConfirmDuplicateDialog(dialog.container) },
            )
        }
        is ContainersDialogUiState.ConfirmRemove -> {
            ContainersConfirmDialog(
                message = stringResource(R.string.containers_list_confirm_remove),
                confirmLabel = stringResource(R.string.common_ui_remove),
                confirmColor = ContainersDanger,
                onDismiss = onDismissDialog,
                onConfirm = { onConfirmRemoveDialog(dialog.container) },
            )
        }
        is ContainersDialogUiState.Backups -> {
            ContainersBackupsDialog(
                onDismiss = onDismissDialog,
                onBackup = { onConfirmBackupDialog(dialog.container) },
                onRestore = { onConfirmRestoreDialog(dialog.container) },
            )
        }
        is ContainersDialogUiState.BackupSelection -> {
            ContainersSelectionDialog(
                title = stringResource(R.string.container_backups_select_title),
                options = dialog.backupNames,
                onDismiss = onDismissDialog,
                onSelected = onBackupSelectionChosen,
            )
        }
        is ContainersDialogUiState.StorageInfo -> {
            ContainerStorageInfoDialog(
                state = dialog.data,
                onDismiss = onDismissDialog,
                onClearCache = { onClearCacheDialog(dialog.data.container) },
            )
        }
        is ContainersDialogUiState.Message -> {
            ContainersMessageDialog(
                title = dialog.title,
                message = dialog.message,
                onDismiss = onDismissDialog,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = ContainersTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun AddContainerCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ContainersCard)
            .border(1.dp, ContainersOutline, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ContainersIconBox),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_content_add),
                contentDescription = null,
                tint = ContainersAccent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.containers_list_new),
            color = ContainersTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ContainerCard(
    container: Container,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShowBackups: () -> Unit,
    onRemove: () -> Unit,
    onShowInfo: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ContainersCard)
            .border(1.dp, ContainersOutline, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBox(
                painterRes = R.drawable.ic_containers,
                tint = ContainersTextSecondary,
            )
            Spacer(Modifier.weight(1f))
            SmallIconButton(
                painterRes = R.drawable.ic_controls_duplicate,
                contentDescription = stringResource(R.string.common_ui_duplicate),
                tint = ContainersTextSecondary,
                onClick = onDuplicate,
            )
            Spacer(Modifier.width(8.dp))
            Box {
                SmallVectorIconButton(
                    image = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.common_ui_edit),
                    tint = ContainersTextSecondary,
                    onClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = ContainersCard,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.container_backups_title), color = ContainersTextPrimary) },
                        onClick = {
                            menuExpanded = false
                            onShowBackups()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.container_config_storage_info), color = ContainersTextPrimary) },
                        onClick = {
                            menuExpanded = false
                            onShowInfo()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_ui_remove), color = ContainersDanger) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = container.name,
                color = ContainersTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                painterRes = R.drawable.icon_play,
                contentDescription = stringResource(R.string.common_ui_run),
                tint = ContainersAccent,
                onClick = onRun,
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                painterRes = R.drawable.ic_controls_edit,
                contentDescription = stringResource(R.string.common_ui_edit),
                tint = ContainersTextSecondary,
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun IconBox(
    painterRes: Int,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ContainersIconBox),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ContainersDialogShell(
    onDismiss: () -> Unit,
    title: String? = null,
    iconRes: Int? = null,
    iconImage: ImageVector? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 460.dp,
    content: @Composable () -> Unit,
) {
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
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ContainersCard)
                    .border(1.dp, ContainersOutline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (title != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (iconImage != null) {
                                Icon(
                                    imageVector = iconImage,
                                    contentDescription = null,
                                    tint = ContainersTextPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                            } else if (iconRes != null) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = ContainersTextPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = title,
                                color = ContainersTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ContainersOutline),
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
private fun ContainersDialogButton(
    label: String,
    primary: Boolean,
    textColor: Color,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    onClick: () -> Unit,
) {
    val resolvedBackground = backgroundColor ?: if (primary) ContainersAccent else ContainersSubcard
    val resolvedBorder = borderColor ?: if (primary) ContainersAccent.copy(alpha = 0.5f) else ContainersOutline

    Box(
        modifier = Modifier
            .widthIn(min = 84.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(resolvedBackground)
            .border(
                1.dp,
                resolvedBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ContainersConfirmDialog(
    message: String,
    confirmLabel: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ContainersDialogShell(
        onDismiss = onDismiss,
        maxWidth = 420.dp,
    ) {
        Text(
            text = message,
            color = ContainersTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ContainersOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            ContainersDialogButton(
                label = stringResource(R.string.common_ui_cancel),
                primary = false,
                textColor = ContainersTextPrimary,
                onClick = onDismiss,
            )
            ContainersDialogButton(
                label = confirmLabel,
                primary = false,
                textColor = confirmColor,
                backgroundColor = confirmColor.copy(alpha = 0.12f),
                borderColor = confirmColor.copy(alpha = 0.3f),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun ContainersBackupsDialog(
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    ContainersDialogShell(
        onDismiss = onDismiss,
        title = stringResource(R.string.container_backups_title),
        maxWidth = 420.dp,
    ) {
        Text(
            text = stringResource(R.string.container_backups_prompt),
            color = ContainersTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ContainersOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            ContainersDialogButton(
                label = stringResource(R.string.common_ui_cancel),
                primary = false,
                textColor = ContainersTextPrimary,
                onClick = onDismiss,
            )
            ContainersDialogButton(
                label = stringResource(R.string.google_cloud_restore),
                primary = false,
                textColor = ContainersTextPrimary,
                onClick = onRestore,
            )
            ContainersDialogButton(
                label = stringResource(R.string.google_cloud_backup),
                primary = false,
                textColor = ContainersAccent,
                backgroundColor = ContainersAccent.copy(alpha = 0.12f),
                borderColor = ContainersAccent.copy(alpha = 0.3f),
                onClick = onBackup,
            )
        }
    }
}

@Composable
private fun ContainersSelectionDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    ContainersDialogShell(
        onDismiss = onDismiss,
        title = title,
        maxWidth = 420.dp,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ContainersSubcard)
                            .border(1.dp, ContainersOutline, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelected(index) },
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = option,
                            color = ContainersTextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ContainersOutline),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ContainersDialogButton(
                    label = stringResource(R.string.common_ui_cancel),
                    primary = false,
                    textColor = ContainersTextPrimary,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ContainersMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    ContainersDialogShell(
        onDismiss = onDismiss,
        title = title,
        maxWidth = 420.dp,
    ) {
        Text(
            text = message,
            color = ContainersTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ContainersOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ContainersDialogButton(
                label = stringResource(R.string.common_ui_ok),
                primary = false,
                textColor = ContainersAccent,
                backgroundColor = ContainersAccent.copy(alpha = 0.12f),
                borderColor = ContainersAccent.copy(alpha = 0.3f),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun ContainerStorageInfoDialog(
    state: ContainerStorageInfoUiState,
    onDismiss: () -> Unit,
    onClearCache: () -> Unit,
) {
    ContainersDialogShell(
        onDismiss = onDismiss,
        title = stringResource(R.string.container_config_storage_info),
        iconImage = Icons.Outlined.Info,
        maxWidth = 500.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StorageMetric(
                    label = stringResource(R.string.container_config_drive_c),
                    value = formatBytes(state.driveCBytes),
                )
                StorageMetric(
                    label = stringResource(R.string.container_config_cache),
                    value = formatBytes(state.cacheBytes),
                )
                StorageMetric(
                    label = stringResource(R.string.container_config_total),
                    value = formatBytes(state.totalBytes),
                )
            }
            Column(
                modifier = Modifier.widthIn(min = 180.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.usedPercent.coerceIn(0f, 100f) / 100f },
                        modifier = Modifier.size(132.dp),
                        color = ContainersAccent.copy(alpha = 0.38f),
                        trackColor = ContainersAccent.copy(alpha = 0.12f),
                        strokeWidth = 18.dp,
                    )
                    Text(
                        text = formatUsedPercent(state.usedPercent),
                        color = ContainersTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.container_config_estimated_used_space),
                    color = ContainersTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ContainersOutline),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            ContainersDialogButton(
                label = stringResource(R.string.container_config_clear_cache),
                primary = false,
                textColor = ContainersTextPrimary,
                onClick = onClearCache,
            )
            ContainersDialogButton(
                label = stringResource(R.string.common_ui_ok),
                primary = true,
                textColor = ContainersAccent,
                backgroundColor = ContainersAccent.copy(alpha = 0.12f),
                borderColor = ContainersAccent.copy(alpha = 0.3f),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun StorageMetric(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            color = ContainersTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = ContainersAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return com.winlator.cmod.core.StringUtils.formatBytes(bytes)
}

private fun formatUsedPercent(percent: Float): String {
    val clamped = percent.coerceIn(0f, 100f)
    return when {
        clamped > 0f && clamped < 1f -> "<1%"
        clamped < 10f -> String.format(Locale.US, "%.1f%%", clamped)
        else -> "${clamped.toInt()}%"
    }
}

@Composable
private fun SmallIconButton(
    painterRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ContainersSubcard)
            .border(1.dp, ContainersOutline, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SmallVectorIconButton(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ContainersSubcard)
            .border(1.dp, ContainersOutline, RoundedCornerShape(8.dp))
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
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    painterRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(ContainersSubcard)
            .border(1.dp, ContainersOutline, RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
