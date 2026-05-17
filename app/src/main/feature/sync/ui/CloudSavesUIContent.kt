package com.winlator.cmod.feature.sync.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.app.shell.LaunchDangerConfirmDialog
import com.winlator.cmod.feature.steamcloudsync.SteamCloudHistoryProvider
import com.winlator.cmod.feature.steamcloudsync.SteamCloudSyncHelper
import com.winlator.cmod.feature.steamcloudsync.SteamSaveSnapshotManager
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.feature.sync.google.WinePathUtils
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.shared.android.DirectoryPickerDialog
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeDanger
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogButton
import com.winlator.cmod.shared.ui.dialog.WinNativeDialogShell
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import com.winlator.cmod.shared.ui.toast.WinToast
import kotlinx.coroutines.launch

private val PageBg = Color(0xFF12121B)
private val SurfaceDark = Color(0xFF1C1C2A)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val DangerRed = Color(0xFFFF6B6B)
private val CloudPanel = Color(0xFF1C1C2A)
private val CloudPanelRaised = Color(0xFF232334)
private val CloudBorder = Color(0xFF2A2A3A)
private val CloudAccent = Color(0xFF5CC8FF)
private val CloudSuccess = Color(0xFF65D394)
private val CloudWarning = Color(0xFFFFB85C)

@Composable
internal fun CloudSavesContent(
    activity: Activity,
    isWorking: Boolean,
    cloudSyncEnabled: Boolean,
    offlineModeEnabled: Boolean,
    gameSource: GameSaveBackupManager.GameSource,
    gameId: String,
    gameName: String,
    shortcut: Shortcut?,
    onCloudSyncToggle: (Boolean) -> Unit,
    onOfflineModeToggle: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSyncFromCloud: () -> Unit,
    showTitle: Boolean = true,
    showBottomBack: Boolean = true,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var historyRefreshKey by remember { mutableStateOf(0) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyEntries by remember { mutableStateOf<List<GameSaveBackupManager.BackupHistoryEntry>>(emptyList()) }
    var entryPendingRestore by remember {
        mutableStateOf<GameSaveBackupManager.BackupHistoryEntry?>(null)
    }
    var entryPendingRename by remember {
        mutableStateOf<GameSaveBackupManager.BackupHistoryEntry?>(null)
    }
    var entryPendingDelete by remember {
        mutableStateOf<GameSaveBackupManager.BackupHistoryEntry?>(null)
    }
    val steamManagedCloud = gameSource == GameSaveBackupManager.GameSource.STEAM

    LaunchedEffect(gameSource, gameId, historyRefreshKey) {
        historyLoading = true
        historyEntries =
            if (gameSource == GameSaveBackupManager.GameSource.STEAM) {
                val appId = gameId.toIntOrNull()
                if (appId != null) {
                    SteamCloudHistoryProvider
                        .listCloudSaveGroups(context, appId)
                } else {
                    emptyList()
                }
            } else {
                GameSaveBackupManager.listBackupHistory(
                    activity,
                    gameSource,
                    gameId,
                    gameName,
                )
            }
        historyLoading = false
    }

    var wasWorking by remember { mutableStateOf(false) }
    LaunchedEffect(isWorking) {
        if (wasWorking && !isWorking) historyRefreshKey++
        wasWorking = isWorking
    }

    val providerLabel =
        when (gameSource) {
            GameSaveBackupManager.GameSource.STEAM -> stringResource(R.string.preloader_platform_steam)
            GameSaveBackupManager.GameSource.EPIC -> stringResource(R.string.preloader_platform_epic)
            GameSaveBackupManager.GameSource.GOG -> stringResource(R.string.preloader_platform_gog)
            GameSaveBackupManager.GameSource.CUSTOM -> stringResource(R.string.preloader_platform_custom)
        }

    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density) {
        Density(density = currentDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides fixedDensity) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PageBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTitle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.cloud_saves_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(CloudAccent.copy(alpha = 0.12f))
                            .border(1.dp, CloudAccent.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        providerLabel.uppercase(),
                        color = CloudAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        }

        TogglePairCard(
            cloudSyncEnabled = cloudSyncEnabled,
            offlineModeEnabled = offlineModeEnabled,
            showCloudSync = !steamManagedCloud,
            onCloudSyncToggle = onCloudSyncToggle,
            onOfflineModeToggle = onOfflineModeToggle,
        )

        if (isWorking) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
                trackColor = CardBorder,
            )
        }

        if (!steamManagedCloud) {
            var customSavePath by remember(shortcut?.file?.absolutePath, historyRefreshKey) {
                mutableStateOf(shortcut?.let { GameSaveBackupManager.getCustomGameSaveWindowsPath(it) })
            }
            val customNoContainer = stringResource(R.string.cloud_saves_custom_no_container)
            val customPickerTitle = stringResource(R.string.cloud_saves_custom_picker_title)
            val customOutsideDriveC = stringResource(R.string.cloud_saves_custom_outside_drive_c)
            val customPathMapFailed = stringResource(R.string.cloud_saves_custom_path_map_failed)
            val firstAction: @Composable (Modifier) -> Unit = { mod ->
                if (gameSource == GameSaveBackupManager.GameSource.CUSTOM) {
                    val pickerLabel =
                        if (customSavePath.isNullOrEmpty()) {
                            stringResource(R.string.cloud_saves_custom_select_folder)
                        } else {
                            stringResource(R.string.cloud_saves_custom_folder_label)
                        }
                    val pickerHelper =
                        if (customSavePath.isNullOrEmpty()) {
                            stringResource(R.string.cloud_saves_custom_select_folder_summary)
                        } else {
                            customSavePath.orEmpty()
                        }
                    ActionWithHelper(
                        icon = Icons.Outlined.FolderOpen,
                        label = pickerLabel,
                        helper = pickerHelper,
                        tint = CloudWarning,
                        modifier = mod,
                        enabled = !isWorking,
                        onClick = {
                            val sc = shortcut
                            val container = sc?.container
                            if (sc == null || container == null) {
                                WinToast.show(
                                    context,
                                    customNoContainer,
                                    Toast.LENGTH_SHORT,
                                )
                                return@ActionWithHelper
                            }
                            val driveC = WinePathUtils.driveCRoot(container)
                            DirectoryPickerDialog.show(
                                activity,
                                initialPath = driveC.absolutePath,
                                title = customPickerTitle,
                            ) { picked ->
                                val pickedFile = java.io.File(picked)
                                if (!WinePathUtils.isInsideDriveC(pickedFile, container)) {
                                    WinToast.show(
                                        context,
                                        customOutsideDriveC,
                                        Toast.LENGTH_LONG,
                                    )
                                    return@show
                                }
                                val winPath =
                                    WinePathUtils
                                        .androidToWindowsPath(picked, container)
                                if (winPath == null) {
                                    WinToast.show(
                                        context,
                                        customPathMapFailed,
                                        Toast.LENGTH_LONG,
                                    )
                                    return@show
                                }
                                GameSaveBackupManager.setCustomGameSavePath(sc, container, winPath)
                                customSavePath = winPath
                                WinToast.show(
                                    context,
                                    context.getString(R.string.cloud_saves_custom_folder_set, winPath),
                                    Toast.LENGTH_SHORT,
                                )
                            }
                        },
                    )
                } else {
                    ActionWithHelper(
                        icon = Icons.Outlined.CloudSync,
                        label = stringResource(R.string.cloud_saves_sync_from_provider, providerLabel),
                        helper = stringResource(R.string.cloud_saves_sync_summary, providerLabel),
                        tint = CloudAccent,
                        modifier = mod,
                        enabled = !isWorking,
                        onClick = { if (!isWorking) onSyncFromCloud() },
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 520.dp
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            firstAction(Modifier.weight(1f))
                            ActionWithHelper(
                                icon = Icons.Outlined.CloudUpload,
                                label = stringResource(R.string.cloud_saves_backup),
                                helper = stringResource(R.string.cloud_saves_backup_summary),
                                tint = CloudSuccess,
                                modifier = Modifier.weight(1f),
                                enabled = !isWorking,
                                onClick = onBackup,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionWithHelper(
                                icon = Icons.Outlined.CloudDownload,
                                label = stringResource(R.string.cloud_saves_restore),
                                helper = stringResource(R.string.cloud_saves_restore_summary),
                                tint = CloudAccent,
                                modifier = Modifier.weight(1f),
                                enabled = !isWorking,
                                onClick = onRestore,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        firstAction(Modifier.weight(1f))
                        ActionWithHelper(
                            icon = Icons.Outlined.CloudUpload,
                            label = stringResource(R.string.cloud_saves_backup),
                            helper = stringResource(R.string.cloud_saves_backup_summary),
                            tint = CloudSuccess,
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking,
                            onClick = onBackup,
                        )
                        ActionWithHelper(
                            icon = Icons.Outlined.CloudDownload,
                            label = stringResource(R.string.cloud_saves_restore),
                            helper = stringResource(R.string.cloud_saves_restore_summary),
                            tint = CloudAccent,
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking,
                            onClick = onRestore,
                        )
                    }
                }
            }

        }

        if (steamManagedCloud) {
            val steamAppIdInt = gameId.toIntOrNull()
            var steamBusy by remember { mutableStateOf(false) }
            val importLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris: List<android.net.Uri> ->
                    if (uris.isEmpty() || steamAppIdInt == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        steamBusy = true
                        try {
                            val result =
                                SteamSaveSnapshotManager
                                    .importSnapshotFromFiles(activity, steamAppIdInt, uris)
                            WinToast.show(
                                context,
                                result.message,
                                Toast.LENGTH_LONG,
                            )
                        } finally {
                            steamBusy = false
                            historyRefreshKey++
                        }
                    }
                }

            if (steamBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = CardBorder,
                )
            }

            val steamSyncSuccess = stringResource(R.string.cloud_saves_steam_sync_success)
            val steamSyncFailed = stringResource(R.string.cloud_saves_steam_sync_failed)
            val steamBrowseNoBrowser = stringResource(R.string.cloud_saves_steam_browse_no_browser)
            val steamImportPickerUnavailable = stringResource(R.string.cloud_saves_steam_import_picker_unavailable)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 520.dp
                val syncAction: @Composable (Modifier) -> Unit = { mod ->
                    ActionWithHelper(
                        icon = Icons.Outlined.CloudSync,
                        label = stringResource(R.string.cloud_saves_steam_sync_label),
                        helper = stringResource(R.string.cloud_saves_steam_sync_helper),
                        tint = CloudAccent,
                        modifier = mod,
                        enabled = !steamBusy && steamAppIdInt != null,
                        onClick = {
                            val appId = steamAppIdInt ?: return@ActionWithHelper
                            if (steamBusy) return@ActionWithHelper
                            scope.launch {
                                steamBusy = true
                                try {
                                    val ok =
                                        SteamCloudSyncHelper
                                            .forceDownloadById(activity, appId)
                                    WinToast.show(
                                        context,
                                        if (ok) steamSyncSuccess else steamSyncFailed,
                                        Toast.LENGTH_SHORT,
                                    )
                                } finally {
                                    steamBusy = false
                                    historyRefreshKey++
                                }
                            }
                        },
                    )
                }
                val browseAction: @Composable (Modifier) -> Unit = { mod ->
                    ActionWithHelper(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = stringResource(R.string.cloud_saves_steam_browse_label),
                        helper = stringResource(R.string.cloud_saves_steam_browse_helper),
                        tint = CloudWarning,
                        modifier = mod,
                        enabled = steamAppIdInt != null,
                        onClick = {
                            val appId = steamAppIdInt ?: return@ActionWithHelper
                            val url = "https://store.steampowered.com/account/remotestorageapp/?appid=$appId"
                            runCatching {
                                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }.onFailure {
                                WinToast.show(
                                    context,
                                    steamBrowseNoBrowser,
                                    Toast.LENGTH_SHORT,
                                )
                            }
                        },
                    )
                }
                val importAction: @Composable (Modifier) -> Unit = { mod ->
                    ActionWithHelper(
                        icon = Icons.Outlined.UploadFile,
                        label = stringResource(R.string.cloud_saves_steam_import_label),
                        helper = stringResource(R.string.cloud_saves_steam_import_helper),
                        tint = CloudSuccess,
                        modifier = mod,
                        enabled = !steamBusy && steamAppIdInt != null,
                        onClick = {
                            runCatching {
                                importLauncher.launch(arrayOf("*/*"))
                            }.onFailure {
                                WinToast.show(
                                    context,
                                    steamImportPickerUnavailable,
                                    Toast.LENGTH_SHORT,
                                )
                            }
                        },
                    )
                }
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            syncAction(Modifier.weight(1f))
                            browseAction(Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            importAction(Modifier.weight(1f))
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        syncAction(Modifier.weight(1f))
                        browseAction(Modifier.weight(1f))
                        importAction(Modifier.weight(1f))
                    }
                }
            }
        }

        SaveHistorySection(
            loading = historyLoading,
            entries = historyEntries,
            onRefresh = {
                historyRefreshKey++
            },
            onRestore = { entry -> entryPendingRestore = entry },
            onRename = { entry -> entryPendingRename = entry },
            onDelete = { entry -> entryPendingDelete = entry },
        )

        if (showBottomBack) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_ui_back), color = TextSecondary)
            }
        }
    }

    entryPendingRestore?.let { entry ->
        val whenLabel =
            remember(entry.timestampMs) {
                android.text.format.DateUtils
                    .getRelativeTimeSpanString(
                        entry.timestampMs,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
            }
        val bodyText =
            if (entry.storage == GameSaveBackupManager.BackupStorage.STEAM_CLOUD) {
                stringResource(R.string.cloud_saves_steam_restore_overwrite_body)
            } else {
                stringResource(R.string.cloud_saves_history_restore_confirm_body, whenLabel)
            }
        LaunchDangerConfirmDialog(
            visible = true,
            title = stringResource(R.string.cloud_saves_history_restore_confirm_title),
            message = bodyText,
            confirmLabel = stringResource(R.string.cloud_saves_history_restore),
            icon = Icons.Outlined.Restore,
            titleTextAlign = TextAlign.Center,
            messageTextAlign = TextAlign.Center,
            accentColor = CloudSuccess,
            onDismissRequest = { entryPendingRestore = null },
            onConfirm = {
                val target = entryPendingRestore ?: return@LaunchDangerConfirmDialog
                entryPendingRestore = null
                scope.launch {
                    val result =
                        when (target.storage) {
                            GameSaveBackupManager.BackupStorage.STEAM_CLOUD -> {
                                val appId = gameId.toIntOrNull()
                                if (appId != null) {
                                    SteamCloudHistoryProvider
                                        .restoreSaveGroup(
                                            activity,
                                            appId,
                                            target.fileId,
                                            shortcut?.container,
                                        )
                                } else {
                                    GameSaveBackupManager.BackupResult(false, context.getString(R.string.cloud_saves_invalid_app_id))
                                }
                            }
                            GameSaveBackupManager.BackupStorage.STEAM_LOCAL -> {
                                val appId = gameId.toIntOrNull()
                                if (appId != null) {
                                    SteamSaveSnapshotManager
                                        .restoreFromEntry(
                                            activity,
                                            appId,
                                            target.fileId,
                                            shortcut?.container,
                                        )
                                } else {
                                    GameSaveBackupManager.BackupResult(false, context.getString(R.string.cloud_saves_invalid_app_id))
                                }
                            }
                            GameSaveBackupManager.BackupStorage.GOOGLE_SNAPSHOTS ->
                                GameSaveBackupManager.restoreFromHistoryEntry(
                                    activity,
                                    gameSource,
                                    gameId,
                                    target,
                                )
                        }
                    WinToast.show(
                        context,
                        if (result.success) {
                            context.getString(R.string.cloud_saves_history_restore_success)
                        } else {
                            context.getString(R.string.cloud_saves_history_restore_failed)
                        },
                        Toast.LENGTH_SHORT,
                    )
                    historyRefreshKey++
                }
            },
        )
    }

    entryPendingRename?.let { entry ->
        var labelInput by remember(entry.fileId) { mutableStateOf(entry.label.orEmpty()) }
        WinNativeDialogShell(
            onDismiss = { entryPendingRename = null },
            maxWidth = 336.dp,
            contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.cloud_saves_history_rename_title),
                color = WinNativeTextPrimary,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WinNativeOutline),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = labelInput,
                onValueChange = { v ->
                    labelInput = v.take(GameSaveBackupManager.MAX_HISTORY_LABEL_LENGTH)
                },
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.cloud_saves_history_rename_hint),
                        color = WinNativeTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    )
                },
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(
                        color = WinNativeTextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WinNativeAccent,
                        unfocusedBorderColor = WinNativeOutline,
                        focusedTextColor = WinNativeTextPrimary,
                        unfocusedTextColor = WinNativeTextPrimary,
                        focusedContainerColor = WinNativeBackground,
                        unfocusedContainerColor = WinNativeBackground,
                        cursorColor = WinNativeAccent,
                    ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WinNativeOutline),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!entry.label.isNullOrBlank()) {
                    CompactRenameDialogButton(
                        label = stringResource(R.string.cloud_saves_history_rename_clear),
                        textColor = WinNativeTextSecondary,
                        onClick = {
                            val target = entryPendingRename ?: return@CompactRenameDialogButton
                            entryPendingRename = null
                            scope.launch {
                                when (target.storage) {
                                    GameSaveBackupManager.BackupStorage.STEAM_CLOUD -> {
                                        SteamCloudHistoryProvider
                                            .setLabel(context, target.fileId, null)
                                    }
                                    GameSaveBackupManager.BackupStorage.STEAM_LOCAL -> {
                                        val appId = gameId.toIntOrNull()
                                        if (appId != null) {
                                            SteamSaveSnapshotManager
                                                .renameEntry(activity, appId, target.fileId, null)
                                        }
                                    }
                                    GameSaveBackupManager.BackupStorage.GOOGLE_SNAPSHOTS ->
                                        GameSaveBackupManager.renameBackupHistoryEntry(
                                            activity,
                                            target,
                                            null,
                                        )
                                }
                                historyRefreshKey++
                            }
                        },
                    )
                }
                CompactRenameDialogButton(
                    label = stringResource(R.string.common_ui_cancel),
                    textColor = WinNativeTextPrimary,
                    onClick = { entryPendingRename = null },
                )
                CompactRenameDialogButton(
                    label = stringResource(R.string.cloud_saves_history_rename_save),
                    textColor = WinNativeAccent,
                    backgroundColor = WinNativeAccent.copy(alpha = 0.12f),
                    borderColor = WinNativeAccent.copy(alpha = 0.3f),
                    onClick = {
                        val target = entryPendingRename ?: return@CompactRenameDialogButton
                        val newLabel = labelInput
                        entryPendingRename = null
                        scope.launch {
                            val result =
                                when (target.storage) {
                                    GameSaveBackupManager.BackupStorage.STEAM_CLOUD -> {
                                        SteamCloudHistoryProvider
                                            .setLabel(context, target.fileId, newLabel)
                                        GameSaveBackupManager.BackupResult(true, context.getString(R.string.cloud_saves_label_saved))
                                    }
                                    GameSaveBackupManager.BackupStorage.STEAM_LOCAL -> {
                                        val appId = gameId.toIntOrNull()
                                        if (appId != null) {
                                            SteamSaveSnapshotManager
                                                .renameEntry(activity, appId, target.fileId, newLabel)
                                        } else {
                                            GameSaveBackupManager.BackupResult(false, context.getString(R.string.cloud_saves_invalid_app_id))
                                        }
                                    }
                                    GameSaveBackupManager.BackupStorage.GOOGLE_SNAPSHOTS ->
                                        GameSaveBackupManager.renameBackupHistoryEntry(
                                            activity,
                                            target,
                                            newLabel,
                                        )
                                }
                            WinToast.show(
                                context,
                                if (result.success) {
                                    context.getString(R.string.cloud_saves_history_rename_success)
                                } else {
                                    context.getString(R.string.cloud_saves_history_rename_failed)
                                },
                                Toast.LENGTH_SHORT,
                            )
                            historyRefreshKey++
                        }
                    },
                )
            }
        }
    }

    entryPendingDelete?.let { entry ->
        WinNativeDialogShell(
            onDismiss = { entryPendingDelete = null },
            title = stringResource(R.string.cloud_saves_history_delete_confirm_title),
        ) {
            Text(
                text = stringResource(R.string.cloud_saves_history_delete_confirm_body),
                color = WinNativeTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WinNativeOutline),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                WinNativeDialogButton(
                    label = stringResource(R.string.common_ui_cancel),
                    textColor = WinNativeTextPrimary,
                    onClick = { entryPendingDelete = null },
                )
                WinNativeDialogButton(
                    label = stringResource(R.string.cloud_saves_history_delete),
                    textColor = WinNativeDanger,
                    backgroundColor = WinNativeDanger.copy(alpha = 0.12f),
                    borderColor = WinNativeDanger.copy(alpha = 0.3f),
                    onClick = {
                        val target = entryPendingDelete ?: return@WinNativeDialogButton
                        entryPendingDelete = null
                        if (target.storage == GameSaveBackupManager.BackupStorage.STEAM_LOCAL ||
                            target.storage == GameSaveBackupManager.BackupStorage.STEAM_CLOUD
                        ) {
                            return@WinNativeDialogButton
                        }
                        scope.launch {
                            val result =
                                GameSaveBackupManager.deleteBackupHistoryEntry(
                                    activity,
                                    target,
                                )
                            WinToast.show(
                                context,
                                if (result.success) {
                                    context.getString(R.string.cloud_saves_history_delete_success)
                                } else {
                                    context.getString(R.string.cloud_saves_history_delete_failed)
                                },
                                Toast.LENGTH_SHORT,
                            )
                            historyRefreshKey++
                        }
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun SaveHistorySection(
    loading: Boolean,
    entries: List<GameSaveBackupManager.BackupHistoryEntry>,
    onRefresh: () -> Unit,
    onRestore: (GameSaveBackupManager.BackupHistoryEntry) -> Unit,
    onRename: (GameSaveBackupManager.BackupHistoryEntry) -> Unit,
    onDelete: (GameSaveBackupManager.BackupHistoryEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.cloud_saves_history_title),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.cloud_saves_history_refresh),
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CloudPanel,
        border = BorderStroke(1.dp, CloudBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            when {
                loading -> {
                    Text(
                        stringResource(R.string.cloud_saves_history_loading),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }

                entries.isEmpty() -> {
                    Text(
                        stringResource(R.string.cloud_saves_history_empty),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }

                else -> {
                    entries.forEachIndexed { index, entry ->
                        SaveHistoryRow(
                            entry = entry,
                            onRestore = { onRestore(entry) },
                            onRename = { onRename(entry) },
                            onDelete = { onDelete(entry) },
                        )
                        if (index < entries.lastIndex) {
                            HorizontalDivider(
                                color = CloudBorder.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveHistoryRow(
    entry: GameSaveBackupManager.BackupHistoryEntry,
    onRestore: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val whenLabel =
        remember(entry.timestampMs) {
            android.text.format.DateUtils
                .getRelativeTimeSpanString(
                    entry.timestampMs,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                ).toString()
        }
    val originLabel =
        when (entry.origin) {
            GameSaveBackupManager.BackupOrigin.LOCAL -> stringResource(R.string.cloud_saves_history_origin_local)
            GameSaveBackupManager.BackupOrigin.CLOUD -> stringResource(R.string.cloud_saves_history_origin_cloud)
            GameSaveBackupManager.BackupOrigin.MANUAL -> stringResource(R.string.cloud_saves_history_origin_manual)
            GameSaveBackupManager.BackupOrigin.AUTO -> stringResource(R.string.cloud_saves_history_origin_auto)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = CloudAccent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val title = entry.label?.takeIf { it.isNotBlank() } ?: whenLabel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CloudPanelRaised)
                            .border(1.dp, CloudBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 0.dp),
                ) {
                    Text(
                        originLabel.uppercase(),
                        color = CloudAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatBytes(entry.sizeBytes),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
                if (!entry.label.isNullOrBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "\u2022 $whenLabel",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryActionChip(
                icon = Icons.Outlined.Restore,
                label = stringResource(R.string.cloud_saves_history_restore),
                tint = CloudSuccess,
                onClick = onRestore,
            )
            Spacer(Modifier.width(6.dp))
            HistoryIconButton(
                icon = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.cloud_saves_history_rename),
                tint = TextPrimary,
                onClick = onRename,
            )
            if (entry.storage != GameSaveBackupManager.BackupStorage.STEAM_LOCAL &&
                entry.storage != GameSaveBackupManager.BackupStorage.STEAM_CLOUD
            ) {
                HistoryIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cloud_saves_history_delete),
                    tint = DangerRed,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HistoryActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tint.copy(alpha = 0.14f))
                .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HistoryIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, CloudBorder, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

@Composable
private fun CompactRenameDialogButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    backgroundColor: Color = CloudPanelRaised,
    borderColor: Color = CloudBorder,
) {
    Box(
        modifier =
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TogglePairCard(
    cloudSyncEnabled: Boolean,
    offlineModeEnabled: Boolean,
    showCloudSync: Boolean = true,
    onCloudSyncToggle: (Boolean) -> Unit,
    onOfflineModeToggle: (Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 380.dp
        val cloudSyncCell: @Composable (Modifier) -> Unit = { mod ->
            TogglePaneCell(
                modifier = mod,
                title = stringResource(R.string.cloud_sync_title),
                summary =
                    if (cloudSyncEnabled) {
                        stringResource(R.string.cloud_sync_enabled_summary)
                    } else {
                        stringResource(R.string.cloud_sync_disabled_summary)
                    },
                checked = cloudSyncEnabled && !offlineModeEnabled,
                enabled = !offlineModeEnabled,
                onCheckedChange = onCloudSyncToggle,
            )
        }
        val offlineCell: @Composable (Modifier) -> Unit = { mod ->
            TogglePaneCell(
                modifier = mod,
                title = stringResource(R.string.cloud_saves_offline_mode),
                summary = stringResource(R.string.cloud_saves_offline_mode_summary),
                checked = offlineModeEnabled,
                enabled = true,
                compact = true,
                onCheckedChange = onOfflineModeToggle,
            )
        }
        if (!showCloudSync) {
            offlineCell(Modifier.fillMaxWidth())
            return@BoxWithConstraints
        }
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cloudSyncCell(Modifier.fillMaxWidth())
                offlineCell(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cloudSyncCell(Modifier.weight(1f).fillMaxHeight())
                offlineCell(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TogglePaneCell(
    modifier: Modifier = Modifier,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val switchScale = if (compact) 0.72f else 0.8f
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CloudPanel)
                .border(1.dp, CloudBorder, RoundedCornerShape(8.dp))
                .padding(
                    start = 10.dp,
                    end = 6.dp,
                    top = if (compact) 0.dp else 2.dp,
                    bottom = if (compact) 5.dp else 8.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else { _ -> },
                enabled = enabled,
                modifier = Modifier.graphicsLayer { scaleX = switchScale; scaleY = switchScale },
                colors =
                    outlinedSwitchColors(
                        accentColor = Accent,
                        textSecondaryColor = TextSecondary,
                        checkedThumbColor = TextPrimary,
                    ),
            )
        }
        Text(
            summary,
            color = TextSecondary,
            fontSize = if (compact) 9.sp else 10.sp,
            lineHeight = if (compact) 11.sp else 12.sp,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionWithHelper(
    icon: ImageVector,
    label: String,
    helper: String,
    tint: Color = CloudAccent,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "cloudActionScale",
    )
    Surface(
        modifier =
            modifier
                .height(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        color = if (enabled) CloudPanelRaised else CloudPanel,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CloudBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint.copy(alpha = if (enabled) 0.14f else 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint.copy(alpha = if (enabled) 1f else 0.48f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    label,
                    color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.64f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    helper,
                    color = TextSecondary.copy(alpha = if (enabled) 1f else 0.58f),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
