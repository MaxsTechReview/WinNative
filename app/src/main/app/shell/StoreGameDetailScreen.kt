package com.winlator.cmod.app.shell

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.winlator.cmod.R
import com.winlator.cmod.shared.io.StorageUtils

internal data class StoreDlcItem(
    val id: Int,
    val name: String,
    val downloadSize: Long,
    val isInstalled: Boolean = false,
)

private val StoreBlack = Color.Black
private val StoreCard = Color(0xFF12121B)
private val StoreAccent = Color(0xFF1A9FFF)
private val StoreAccentGlow = Color(0xFF58A6FF)
private val StoreTextPrimary = Color(0xFFF0F4FF)
private val StoreTextSecondary = Color(0xFF93A6BC)
private val StoreDanger = Color(0xFFFF6B6B)

@Composable
internal fun StoreGameDetailScreen(
    title: String,
    subtitle: String,
    sourceLabel: String,
    heroImageUrl: Any?,
    isLoading: Boolean,
    isInstalled: Boolean,
    installPathDisplay: String,
    downloadSize: Long,
    installSize: Long,
    availableBytes: Long,
    isInstallEnabled: Boolean,
    isDownloadActionEnabled: Boolean = isInstallEnabled,
    customPathLabel: String,
    showCustomPath: Boolean = true,
    showCloudSync: Boolean = false,
    showUninstall: Boolean = true,
    showBestConfigs: Boolean = false,
    showUpdateCheck: Boolean = false,
    isCheckingForUpdate: Boolean = false,
    isUpdateAvailable: Boolean = false,
    updateDownloadSize: Long = 0L,
    updateStatusText: String? = null,
    isUpdateActionEnabled: Boolean = true,
    isUpdateCheckCoolingDown: Boolean = false,
    showWorkshop: Boolean = false,
    showVerifyFiles: Boolean = false,
    dlcs: List<StoreDlcItem> = emptyList(),
    selectedDlcIds: Set<Int> = emptySet(),
    isDlcSelectionEnabled: Boolean = true,
    onBack: () -> Unit,
    onInstall: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
    onWorkshop: () -> Unit = {},
    onVerifyFiles: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onUninstall: () -> Unit = {},
    onCloudSync: () -> Unit = {},
    onCustomPath: () -> Unit = {},
    onBestConfigs: () -> Unit = {},
    onToggleDlc: (Int) -> Unit = {},
    onToggleSelectAllDlcs: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var dlcExpanded by remember { mutableStateOf(false) }
    var dlcHeaderHeightPx by remember { mutableIntStateOf(0) }

    StoreScreenCutoutMode()

    Box(Modifier.fillMaxSize()) {
        val edgePadding = 22.dp
        val bottomPadding = 8.dp
        val actionIconSize = 48.dp
        val actionIconSpacing = 8.dp
        val actionWidth = actionIconSize * 5 + actionIconSpacing * 4
        val ctaHeight = 56.dp
        val contentGap = 18.dp
        val horizontalNavInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
        val hasSelectedInstallableDlc = dlcs.any { !it.isInstalled && it.id in selectedDlcIds }
        val showDownloadCta = !isInstalled || hasSelectedInstallableDlc
        val showUpdateCheckButton = showUpdateCheck && isInstalled
        val showUpdateCta = showUpdateCheckButton && isUpdateAvailable
        val showWorkshopButton = showWorkshop && isInstalled
        val showVerifyFilesButton = showVerifyFiles && isInstalled
        val showDlcCard = dlcs.isNotEmpty() && (!isInstalled || dlcs.any { !it.isInstalled })
        val showActionColumn =
            showDownloadCta || showUpdateCheckButton || showWorkshopButton || showVerifyFilesButton ||
                (showCloudSync || showUninstall || showBestConfigs)

        if (heroImageUrl != null) {
            val heroRequest =
                remember(heroImageUrl, context) {
                    ImageRequest
                        .Builder(context)
                        .data(heroImageUrl)
                        .crossfade(150)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
            AsyncImage(
                model = heroRequest,
                contentDescription = "$title artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(StoreAccent.copy(alpha = 0.34f), StoreCard, StoreBlack),
                            radius = 980f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = StoreTextPrimary.copy(alpha = 0.18f),
                    modifier = Modifier.size(132.dp),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to StoreBlack.copy(alpha = 0.9f),
                                0.36f to StoreBlack.copy(alpha = 0.58f),
                                0.72f to StoreBlack.copy(alpha = 0.18f),
                                1.0f to StoreBlack.copy(alpha = 0.62f),
                            ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to StoreBlack.copy(alpha = 0.54f),
                                0.36f to Color.Transparent,
                                0.72f to StoreBlack.copy(alpha = 0.32f),
                                1.0f to StoreBlack.copy(alpha = 0.94f),
                            ),
                    ),
                ),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(horizontalNavInsets)
                    .padding(start = edgePadding, top = 12.dp, end = edgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StoreBlack.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.common_ui_back),
                    tint = StoreTextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            StoreSourceTag(sourceLabel = sourceLabel)
        }

        val dlcHeaderReserveHeight =
            if (showDlcCard && dlcHeaderHeightPx > 0) {
                with(density) { dlcHeaderHeightPx.toDp() } + 12.dp
            } else {
                0.dp
            }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        start = edgePadding,
                        top = 68.dp,
                        end = edgePadding,
                        bottom = bottomPadding + dlcHeaderReserveHeight,
                    ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = StoreTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = StoreTextPrimary.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(contentGap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = StoreAccent,
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    stringResource(R.string.common_ui_loading),
                                    color = StoreTextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        } else if (isInstalled) {
                            StoreStatChip(
                                icon = Icons.Outlined.Storage,
                                label = stringResource(R.string.library_games_install_path),
                                value = installPathDisplay,
                            )
                            if (isUpdateAvailable && updateDownloadSize > 0L) {
                                StoreStatChip(
                                    icon = Icons.Outlined.SystemUpdate,
                                    label = stringResource(R.string.store_game_update),
                                    value = StorageUtils.formatBinarySize(updateDownloadSize),
                                )
                            }
                        } else {
                            if (downloadSize > 0L) {
                                StoreStatChip(
                                    icon = Icons.Outlined.Download,
                                    label = stringResource(R.string.common_ui_download),
                                    value = StorageUtils.formatBinarySize(downloadSize),
                                )
                            }
                            if (installSize > 0L) {
                                StoreStatChip(
                                    icon = Icons.Outlined.Storage,
                                    label = stringResource(R.string.common_ui_size),
                                    value = StorageUtils.formatBinarySize(installSize),
                                    valueColor = if (!isInstallEnabled) StoreDanger else null,
                                )
                            }
                            if (availableBytes > 0L) {
                                StoreStatChip(
                                    icon = Icons.Outlined.Folder,
                                    label = stringResource(R.string.common_ui_available),
                                    value = StorageUtils.formatBinarySize(availableBytes),
                                    valueColor = if (!isInstallEnabled) StoreDanger else null,
                                )
                            }
                            if (showCustomPath) {
                                StoreActionChip(
                                    icon = Icons.Outlined.Folder,
                                    label = customPathLabel,
                                    onClick = onCustomPath,
                                )
                            }
                        }
                    }

                    if (showActionColumn) {
                        Column(
                            modifier = Modifier.width(actionWidth),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (showUpdateCta) {
                                StoreCtaButton(
                                    height = ctaHeight,
                                    icon = Icons.Outlined.SystemUpdate,
                                    label = stringResource(R.string.store_game_download_update),
                                    enabled =
                                        !isLoading &&
                                            isUpdateActionEnabled &&
                                            !isCheckingForUpdate,
                                    loading = false,
                                    onClick = onDownloadUpdate,
                                )
                            }

                            if (showVerifyFilesButton) {
                                StoreSecondaryActionButton(
                                    icon = Icons.Outlined.FactCheck,
                                    label = stringResource(R.string.store_game_verify_files),
                                    enabled = !isLoading && !isCheckingForUpdate,
                                    loading = false,
                                    onClick = onVerifyFiles,
                                )
                            }

                            if (showUpdateCheckButton) {
                                StoreSecondaryActionButton(
                                    icon = Icons.Outlined.Refresh,
                                    label =
                                        if (isCheckingForUpdate) {
                                            stringResource(R.string.store_game_checking_for_update)
                                        } else {
                                            stringResource(R.string.store_game_check_for_update)
                                        },
                                    enabled =
                                        !isLoading &&
                                            !isCheckingForUpdate &&
                                            !isUpdateCheckCoolingDown &&
                                            isUpdateActionEnabled,
                                    loading = isCheckingForUpdate,
                                    onClick = onCheckForUpdate,
                                )
                                if (!updateStatusText.isNullOrBlank()) {
                                    Text(
                                        updateStatusText,
                                        color =
                                            if (updateStatusText == stringResource(R.string.store_game_update_check_failed)) {
                                                StoreDanger
                                            } else {
                                                StoreTextSecondary
                                            },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (showWorkshopButton) {
                                StoreSecondaryActionButton(
                                    icon = Icons.Outlined.Construction,
                                    label = stringResource(R.string.store_game_workshop),
                                    enabled = !isLoading,
                                    loading = false,
                                    onClick = onWorkshop,
                                )
                            }

                            if (showDownloadCta && !isLoading && !isInstallEnabled && installSize > 0L) {
                                val deficit = (installSize - availableBytes).coerceAtLeast(0L)
                                if (deficit > 0L) {
                                    Text(
                                        stringResource(
                                            R.string.library_games_not_enough_space,
                                            StorageUtils.formatBinarySize(deficit),
                                        ),
                                        color = StoreDanger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(actionIconSpacing),
                                verticalAlignment = Alignment.Top,
                            ) {
                                if (showBestConfigs && isInstalled) {
                                    StoreIconActionButton(
                                        icon = Icons.Outlined.SettingsSuggest,
                                        contentDescription = stringResource(R.string.best_configs_button_label),
                                        size = actionIconSize,
                                        onClick = onBestConfigs,
                                    )
                                }
                                if (showCloudSync && isInstalled) {
                                    StoreIconActionButton(
                                        icon = Icons.Outlined.CloudSync,
                                        contentDescription = stringResource(R.string.cloud_saves_title),
                                        size = actionIconSize,
                                        onClick = onCloudSync,
                                    )
                                }
                                if (showUninstall && isInstalled) {
                                    StoreIconActionButton(
                                        icon = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.common_ui_uninstall),
                                        size = actionIconSize,
                                        onClick = onUninstall,
                                        tint = StoreDanger,
                                    )
                                }
                            }

                            if (showDownloadCta) {
                                StoreCtaButton(
                                    height = ctaHeight,
                                    icon = Icons.Outlined.Download,
                                    label = stringResource(R.string.common_ui_download),
                                    enabled = !isLoading && isDownloadActionEnabled,
                                    loading = isLoading,
                                    onClick = onInstall,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDlcCard) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            start = edgePadding,
                            top = 68.dp,
                            end = edgePadding,
                            bottom = bottomPadding,
                        ),
                contentAlignment = Alignment.BottomStart,
            ) {
                val maxListHeight = (maxHeight - 96.dp).coerceAtLeast(120.dp)
                StoreDlcCard(
                    dlcs = dlcs,
                    selectedDlcIds = selectedDlcIds,
                    selectionEnabled = isDlcSelectionEnabled,
                    expanded = dlcExpanded,
                    onToggleExpanded = { dlcExpanded = !dlcExpanded },
                    onToggleDlc = onToggleDlc,
                    onToggleSelectAll = onToggleSelectAllDlcs,
                    maxListHeight = maxListHeight,
                    onHeaderMeasured = { dlcHeaderHeightPx = it },
                )
            }
        }
    }
}

@Composable
private fun StoreDlcCard(
    dlcs: List<StoreDlcItem>,
    selectedDlcIds: Set<Int>,
    selectionEnabled: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleDlc: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    maxListHeight: Dp = 280.dp,
    onHeaderMeasured: (Int) -> Unit = {},
) {
    val selectableDlcs = remember(dlcs) { dlcs.filterNot { it.isInstalled } }
    val totalSize = remember(selectableDlcs) { selectableDlcs.sumOf { it.downloadSize.coerceAtLeast(0L) } }
    val selectedCount = selectableDlcs.count { it.id in selectedDlcIds }
    val installedCount = dlcs.count { it.isInstalled }
    val selectedSize = remember(dlcs, selectedDlcIds) {
        dlcs.filter { !it.isInstalled && it.id in selectedDlcIds }.sumOf { it.downloadSize.coerceAtLeast(0L) }
    }
    val allSelected = selectableDlcs.isNotEmpty() && selectedCount == selectableDlcs.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StoreBlack,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { onHeaderMeasured(it.height) }
                        .clickable(onClick = onToggleExpanded)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = StoreAccentGlow,
                    modifier = Modifier.size(16.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        stringResource(R.string.library_games_dlcs).uppercase(),
                        color = StoreTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                    )
                    Text(
                        buildDlcSummary(
                            selectedCount = selectedCount,
                            totalCount = selectableDlcs.size,
                            installedCount = installedCount,
                            selectedSize = selectedSize,
                            totalSize = totalSize,
                        ),
                        color = StoreTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = StoreTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                    if (selectableDlcs.isNotEmpty()) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectionEnabled, onClick = onToggleSelectAll)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { onToggleSelectAll() },
                                enabled = selectionEnabled,
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = StoreAccent,
                                        uncheckedColor = StoreTextSecondary,
                                        checkmarkColor = Color.White,
                                    ),
                            )
                            Text(
                                stringResource(
                                    if (allSelected) R.string.common_ui_deselect_all else R.string.common_ui_select_all,
                                ),
                                color = StoreTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                    }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxListHeight)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        dlcs.forEachIndexed { index, dlc ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.16f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (dlc.isInstalled) {
                                                Modifier
                                            } else if (!selectionEnabled) {
                                                Modifier
                                            } else {
                                                Modifier.clickable { onToggleDlc(dlc.id) }
                                            },
                                        )
                                        .padding(horizontal = 6.dp, vertical = 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (dlc.isInstalled) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = null,
                                        enabled = false,
                                        colors =
                                            CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF38D77A),
                                                disabledCheckedColor = Color(0xFF38D77A),
                                                checkmarkColor = Color.White,
                                            ),
                                    )
                                } else {
                                    Checkbox(
                                        checked = dlc.id in selectedDlcIds,
                                        onCheckedChange = { onToggleDlc(dlc.id) },
                                        enabled = selectionEnabled,
                                        colors =
                                            CheckboxDefaults.colors(
                                                checkedColor = StoreAccent,
                                                uncheckedColor = StoreTextSecondary,
                                                checkmarkColor = Color.White,
                                            ),
                                    )
                                }
                                Text(
                                    dlc.name,
                                    color = if (dlc.isInstalled) Color(0xFFB7F8CE) else StoreTextPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (dlc.isInstalled) {
                                    Text(
                                        stringResource(R.string.common_ui_installed),
                                        color = Color(0xFF38D77A),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                    )
                                } else {
                                    Text(
                                        if (dlc.downloadSize > 0L) StorageUtils.formatBinarySize(dlc.downloadSize) else "—",
                                        color = StoreTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildDlcSummary(
    selectedCount: Int,
    totalCount: Int,
    installedCount: Int,
    selectedSize: Long,
    totalSize: Long,
): String {
    val totalSizeStr = if (totalSize > 0L) StorageUtils.formatBinarySize(totalSize) else null
    val selectedSizeStr = if (selectedSize > 0L) StorageUtils.formatBinarySize(selectedSize) else null
    val selectionText =
        when {
            totalCount == 0 -> null
            selectedCount == 0 && totalSizeStr != null -> "$totalCount available · $totalSizeStr total"
            selectedCount == 0 -> "$totalCount available"
            selectedSizeStr != null && totalSizeStr != null ->
                "$selectedCount of $totalCount · $selectedSizeStr / $totalSizeStr"
            else -> "$selectedCount of $totalCount selected"
        }
    val installedText = if (installedCount > 0) "$installedCount installed" else null
    return when {
        selectionText != null && installedText != null -> "$selectionText · $installedText"
        selectionText != null -> selectionText
        installedText != null -> installedText
        else -> ""
    }
}

@Composable
private fun StoreSourceTag(sourceLabel: String) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StoreAccent),
            )
            Text(
                sourceLabel.uppercase(),
                color = StoreTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoreStatChip(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    Surface(
        color = StoreBlack.copy(alpha = 0.44f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.11f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = StoreAccentGlow)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    label.uppercase(),
                    color = StoreTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    value,
                    color = valueColor ?: StoreTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StoreActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        color = StoreBlack.copy(alpha = 0.44f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, StoreAccentGlow.copy(alpha = 0.36f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = StoreAccentGlow)
            Text(
                label,
                color = StoreTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoreCtaButton(
    height: Dp,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "storeCtaScale",
    )
    val shape = remember { RoundedCornerShape(12.dp) }
    val activeBrush =
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF00B4D8), StoreAccent, Color(0xFF7B2FF7)),
        )
    val disabledBrush =
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF3A3A4A), Color(0xFF2A2A36)),
        )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(if (enabled) activeBrush else disabledBrush)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { if (enabled && !loading) onClick() },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StoreSecondaryActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 620f),
        label = "storeSecondaryActionScale",
    )
    val shape = remember { RoundedCornerShape(8.dp) }
    val contentColor = if (enabled) StoreTextPrimary else StoreTextSecondary.copy(alpha = 0.58f)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { if (enabled && !loading) onClick() },
                ),
        color = StoreBlack.copy(alpha = if (enabled) 0.52f else 0.34f),
        shape = shape,
        border = BorderStroke(1.dp, StoreAccentGlow.copy(alpha = if (enabled) 0.36f else 0.14f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = StoreAccentGlow,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        label,
                        color = contentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Surface(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        color = StoreBlack.copy(alpha = 0.46f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun StoreScreenCutoutMode() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

    val view = LocalView.current
    DisposableEffect(view) {
        val window =
            (view.parent as? DialogWindowProvider)?.window
                ?: return@DisposableEffect onDispose { }

        val originalCutoutMode = window.attributes.layoutInDisplayCutoutMode
        val originalWidth = window.attributes.width
        val originalHeight = window.attributes.height

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.attributes =
            window.attributes.apply {
                layoutInDisplayCutoutMode = storeCutoutMode()
            }

        onDispose {
            window.attributes =
                window.attributes.apply {
                    layoutInDisplayCutoutMode = originalCutoutMode
                }
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setLayout(originalWidth, originalHeight)
        }
    }
}

private fun storeCutoutMode(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
