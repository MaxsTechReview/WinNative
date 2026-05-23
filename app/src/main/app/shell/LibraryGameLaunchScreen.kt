package com.winlator.cmod.app.shell

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.winlator.cmod.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LaunchBlack = Color.Black
private val LaunchCard = Color(0xFF12121B)
private val LaunchAccent = Color(0xFF1A9FFF)
private val LaunchAccentGlow = Color(0xFF58A6FF)
private val LaunchTextPrimary = Color(0xFFF0F4FF)
private val LaunchTextSecondary = Color(0xFF93A6BC)
private val LaunchDanger = Color(0xFFFF6B6B)

@Composable
internal fun LibraryGameLaunchScreen(
    appName: String,
    subtitle: String,
    sourceLabel: String,
    heroImageUrl: Any?,
    customHeroImageCacheKey: String?,
    releaseDateEpochSeconds: Long,
    totalPlaytimeMillis: Long,
    playCount: Int,
    lastPlayedMillis: Long,
    installSizeText: String?,
    isCustom: Boolean,
    hasPinnedShortcut: Boolean,
    showSavesAction: Boolean,
    steamMenuEnabled: Boolean = false,
    areSteamActionsEnabled: Boolean = true,
    showVerifyFiles: Boolean = true,
    showCheckForUpdate: Boolean = true,
    showWorkshop: Boolean = true,
    playEnabled: Boolean = true,
    playDisabledLabel: String? = null,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onShortcut: () -> Unit,
    onSaves: () -> Unit,
    onCloudSaves: () -> Unit,
    onUninstall: () -> Unit,
    onVerifyFiles: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
    onWorkshop: () -> Unit = {},
) {
    val context = LocalContext.current
    var uninstallMenuOpen by remember { mutableStateOf(false) }

    LaunchScreenCutoutMode()

    Box(Modifier.fillMaxSize()) {
        val edgePadding = 22.dp
        val bottomPadding = 20.dp
        val actionIconSize = 48.dp
        val actionIconSpacing = 8.dp
        // 5 action icons: Settings, Shortcut, (Saves), CloudSync, Delete.
        // Saves only renders for stores that expose it; layout width tracks the static
        // count to keep the play button centered.
        val actionWidth = actionIconSize * 5 + actionIconSpacing * 4
        val playHeight = 56.dp
        val contentGap = 18.dp
        val horizontalNavInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)

        if (heroImageUrl != null) {
            val heroRequest =
                remember(heroImageUrl, customHeroImageCacheKey, context) {
                    ImageRequest
                        .Builder(context)
                        .data(heroImageUrl)
                        .apply {
                            if (customHeroImageCacheKey != null) {
                                memoryCacheKey(customHeroImageCacheKey)
                                diskCacheKey(customHeroImageCacheKey)
                            }
                        }.crossfade(150)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
            AsyncImage(
                model = heroRequest,
                contentDescription = "$appName artwork",
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
                            colors = listOf(LaunchAccent.copy(alpha = 0.34f), LaunchCard, LaunchBlack),
                            radius = 980f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = LaunchTextPrimary.copy(alpha = 0.18f),
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
                                0.0f to LaunchBlack.copy(alpha = 0.9f),
                                0.36f to LaunchBlack.copy(alpha = 0.58f),
                                0.72f to LaunchBlack.copy(alpha = 0.18f),
                                1.0f to LaunchBlack.copy(alpha = 0.62f),
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
                                0.0f to LaunchBlack.copy(alpha = 0.54f),
                                0.36f to Color.Transparent,
                                0.72f to LaunchBlack.copy(alpha = 0.32f),
                                1.0f to LaunchBlack.copy(alpha = 0.94f),
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
                        .background(LaunchBlack.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.common_ui_back),
                    tint = LaunchTextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            SourceTag(
                sourceLabel = sourceLabel,
                menuEnabled = steamMenuEnabled,
                showVerifyFiles = showVerifyFiles,
                showCheckForUpdate = showCheckForUpdate,
                showWorkshop = showWorkshop,
                areSteamActionsEnabled = areSteamActionsEnabled,
                onVerifyFiles = onVerifyFiles,
                onCheckForUpdate = onCheckForUpdate,
                onWorkshop = onWorkshop,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = edgePadding, top = 68.dp, end = edgePadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    appName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = LaunchTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = LaunchTextPrimary.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (releaseDateEpochSeconds > 0L) {
                    val releaseDateText = remember(releaseDateEpochSeconds) { formatReleaseDate(releaseDateEpochSeconds) }
                    Text(
                        releaseDateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LaunchTextPrimary.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

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
                    if (totalPlaytimeMillis > 0L) {
                        val playtimeText = remember(totalPlaytimeMillis) { formatLibraryPlaytime(totalPlaytimeMillis) }
                        GameStatChip(
                            icon = Icons.Outlined.Schedule,
                            label = stringResource(R.string.library_games_playtime),
                            value = playtimeText,
                        )
                    }
                    if (playCount > 0) {
                        GameStatChip(
                            icon = Icons.Outlined.SportsEsports,
                            label = stringResource(R.string.library_games_plays),
                            value = playCount.toString(),
                        )
                    }
                    if (lastPlayedMillis > 0L) {
                        val lastPlayedText = remember(lastPlayedMillis) { formatLibraryLastPlayed(lastPlayedMillis) }
                        GameStatChip(
                            icon = Icons.Outlined.History,
                            label = stringResource(R.string.library_games_last_played),
                            value = lastPlayedText,
                        )
                    }
                    if (installSizeText != null) {
                        GameStatChip(
                            icon = Icons.Outlined.Storage,
                            label = stringResource(R.string.common_ui_size),
                            value = installSizeText,
                        )
                    }
                }

                Column(
                    modifier = Modifier.width(actionWidth),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LaunchPlayButton(
                        height = playHeight,
                        enabled = playEnabled,
                        disabledLabel = playDisabledLabel,
                        onClick = onPlay,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(actionIconSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Order (per user spec): Settings → Shortcut → (Saves) → Cloud Saves → Uninstall.
                        // Saves is conditional but slots between Shortcut and Cloud Saves because the two
                        // saves-related buttons read better next to each other.
                        LaunchIconActionButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.common_ui_settings),
                            size = actionIconSize,
                            onClick = onSettings,
                        )
                        LaunchIconActionButton(
                            icon = Icons.Outlined.Home,
                            contentDescription =
                                stringResource(
                                    if (hasPinnedShortcut) R.string.common_ui_remove else R.string.common_ui_shortcut,
                            ),
                            size = actionIconSize,
                            onClick = onShortcut,
                        )
                        if (showSavesAction) {
                            LaunchIconActionButton(
                                icon = Icons.Outlined.Save,
                                contentDescription = stringResource(R.string.saves_import_export_title),
                                size = actionIconSize,
                                onClick = onSaves,
                            )
                        }
                        LaunchIconActionButton(
                            icon = Icons.Outlined.CloudSync,
                            contentDescription = stringResource(R.string.cloud_saves_title),
                            size = actionIconSize,
                            onClick = onCloudSaves,
                        )
                        Box {
                            LaunchIconActionButton(
                                icon = Icons.Outlined.Delete,
                                contentDescription =
                                    stringResource(if (isCustom) R.string.common_ui_remove else R.string.common_ui_uninstall),
                                size = actionIconSize,
                                onClick = { uninstallMenuOpen = true },
                                tint = LaunchDanger,
                            )
                            LaunchUninstallMenu(
                                expanded = uninstallMenuOpen,
                                appName = appName,
                                isCustom = isCustom,
                                onDismissRequest = { uninstallMenuOpen = false },
                                onConfirm = {
                                    uninstallMenuOpen = false
                                    onUninstall()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (uninstallMenuOpen) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(LaunchBlack.copy(alpha = 0.46f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { uninstallMenuOpen = false },
                        ),
            )
        }
    }
}

@Composable
private fun LaunchScreenCutoutMode() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: return@DisposableEffect onDispose { }

        val originalCutoutMode = window.attributes.layoutInDisplayCutoutMode
        val originalWidth = window.attributes.width
        val originalHeight = window.attributes.height

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = launchScreenCutoutMode()
        }

        onDispose {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = originalCutoutMode
            }
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setLayout(originalWidth, originalHeight)
        }
    }
}

private fun launchScreenCutoutMode(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

@Composable
private fun LaunchUninstallMenu(
    expanded: Boolean,
    appName: String,
    isCustom: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = stringResource(if (isCustom) R.string.library_games_remove_game else R.string.library_games_uninstall_game)
    val confirmLabel = stringResource(if (isCustom) R.string.common_ui_remove else R.string.common_ui_uninstall)
    val message =
        stringResource(
            if (isCustom) R.string.library_games_remove_confirm else R.string.library_games_uninstall_confirm,
            appName,
        )

    LaunchDangerConfirmMenu(
        expanded = expanded,
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun LaunchDangerConfirmMenu(
    expanded: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector = Icons.Outlined.Delete,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = (-56).dp),
        modifier = Modifier.width(286.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = LaunchCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = LaunchDanger,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    title,
                    color = LaunchTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                message,
                color = LaunchTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LaunchMenuTextAction(
                    label = stringResource(R.string.common_ui_cancel),
                    textColor = LaunchTextSecondary,
                    onClick = onDismissRequest,
                )
                LaunchMenuTextAction(
                    label = confirmLabel,
                    textColor = LaunchDanger,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
internal fun LaunchDangerConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector = Icons.Outlined.Warning,
    titleTextAlign: TextAlign = TextAlign.Start,
    messageTextAlign: TextAlign = TextAlign.Start,
    accentColor: Color = LaunchDanger,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(LaunchBlack.copy(alpha = 0.46f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .width(286.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { },
                        ),
                shape = RoundedCornerShape(12.dp),
                color = LaunchCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shadowElevation = 14.dp,
                tonalElevation = 0.dp,
            ) {
                LaunchDangerConfirmContent(
                    title = title,
                    message = message,
                    confirmLabel = confirmLabel,
                    onDismissRequest = onDismissRequest,
                    onConfirm = onConfirm,
                    icon = icon,
                    titleTextAlign = titleTextAlign,
                    messageTextAlign = messageTextAlign,
                    accentColor = accentColor,
                )
            }
        }
    }
}

@Composable
private fun LaunchDangerConfirmContent(
    title: String,
    message: String,
    confirmLabel: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector,
    titleTextAlign: TextAlign,
    messageTextAlign: TextAlign,
    accentColor: Color,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (titleTextAlign == TextAlign.Center) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(18.dp),
                )
                Text(
                    title,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp),
                    color = LaunchTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    title,
                    color = LaunchTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            message,
            modifier = Modifier.fillMaxWidth(),
            color = LaunchTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = messageTextAlign,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LaunchMenuTextAction(
                label = stringResource(R.string.common_ui_cancel),
                textColor = LaunchTextSecondary,
                onClick = onDismissRequest,
            )
            LaunchMenuTextAction(
                label = confirmLabel,
                textColor = accentColor,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun LaunchMenuTextAction(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SourceTag(
    sourceLabel: String,
    menuEnabled: Boolean = false,
    showVerifyFiles: Boolean = true,
    showCheckForUpdate: Boolean = true,
    showWorkshop: Boolean = true,
    areSteamActionsEnabled: Boolean = true,
    onVerifyFiles: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
    onWorkshop: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    Box {
        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier =
                Modifier
                    .onSizeChanged { anchorHeightPx = it.height }
                    .then(if (menuEnabled) Modifier.clickable { menuOpen = true } else Modifier),
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
                        .background(LaunchAccent),
                )
                Text(
                    sourceLabel.uppercase(),
                    color = LaunchTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (menuEnabled) {
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = stringResource(R.string.store_game_steam_options),
                        tint = LaunchTextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (menuEnabled) {
            val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
            LaunchSourceActionPopup(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                offset = IntOffset(0, anchorHeightPx + gapPx),
            ) {
                if (showVerifyFiles) {
                    LaunchSourceMenuItem(
                        icon = Icons.Outlined.FactCheck,
                        label = stringResource(R.string.store_game_verify_files),
                        enabled = areSteamActionsEnabled,
                    ) { menuOpen = false; onVerifyFiles() }
                }
                if (showCheckForUpdate) {
                    LaunchSourceMenuItem(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(R.string.store_game_check_for_update),
                        enabled = areSteamActionsEnabled,
                    ) { menuOpen = false; onCheckForUpdate() }
                }
                if (showWorkshop) {
                    LaunchSourceMenuItem(
                        icon = Icons.Outlined.Construction,
                        label = stringResource(R.string.store_game_workshop),
                        enabled = areSteamActionsEnabled,
                    ) { menuOpen = false; onWorkshop() }
                }
            }
        }
    }
}

@Composable
private fun LaunchSourceActionPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: IntOffset,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = expanded
    if (!transitionState.currentState && !transitionState.targetState) return

    Popup(
        alignment = Alignment.TopEnd,
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter =
                fadeIn(animationSpec = tween(durationMillis = 90)) +
                    scaleIn(
                        animationSpec =
                            spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        initialScale = 0.88f,
                        transformOrigin = TransformOrigin(1f, 0f),
                    ),
            exit =
                fadeOut(animationSpec = tween(durationMillis = 80)) +
                    scaleOut(
                        animationSpec = tween(durationMillis = 110),
                        targetScale = 0.92f,
                        transformOrigin = TransformOrigin(1f, 0f),
                    ),
        ) {
            Surface(
                color = LaunchBlack.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
            ) {
                Column { content() }
            }
        }
    }
}

@Composable
private fun LaunchSourceMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.45f)
    Row(
        modifier =
            Modifier
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GameStatChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = LaunchBlack.copy(alpha = 0.44f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.11f)),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = LaunchAccentGlow)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    label.uppercase(),
                    color = LaunchTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    value,
                    color = LaunchTextPrimary,
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
private fun LaunchPlayButton(
    height: Dp,
    enabled: Boolean = true,
    disabledLabel: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "launchPlayScale",
    )

    val playShape = remember { RoundedCornerShape(12.dp) }
    // When disabled, the clickable is removed entirely (not no-op'd) so
    // accessibility / focus skip it and a stray controller A-press can't fire onClick.
    val backgroundBrush =
        if (enabled) {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF00B4D8), LaunchAccent, Color(0xFF7B2FF7)),
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF3A3F4A), Color(0xFF2D313A), Color(0xFF3A3F4A)),
            )
        }
    val foregroundAlpha = if (enabled) 1f else 0.75f

    val baseModifier =
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(playShape)
            .background(backgroundBrush)
    val finalModifier =
        if (enabled) {
            baseModifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            baseModifier
        }

    val showStatus = !enabled && !disabledLabel.isNullOrBlank()
    val icon = if (showStatus) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow
    val label = if (showStatus) disabledLabel!! else stringResource(R.string.library_games_play)

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.White.copy(alpha = foregroundAlpha),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = Color.White.copy(alpha = foregroundAlpha),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LaunchIconActionButton(
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
        color = LaunchBlack.copy(alpha = 0.46f),
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

private fun formatReleaseDate(releaseDateEpochSeconds: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .format(Date(releaseDateEpochSeconds * 1000L))

private fun formatLibraryPlaytime(playtimeMillis: Long): String {
    val totalMinutes = (playtimeMillis / 60000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun formatLibraryLastPlayed(lastPlayedMillis: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastPlayedMillis))
