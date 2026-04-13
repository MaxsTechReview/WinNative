/* Settings > Components screen — Jetpack Compose / Material3.
 * Scrolling delegated to a View-level ScrollView in ContentsFragment. */
package com.winlator.cmod.feature.settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.runtime.content.ContentProfile

// ============================================================================
// Palette (unified with Drivers / Stores / Other / Debug)
// ============================================================================
private val BgDark = Color(0xFF18181D)
private val CardDark = Color(0xFF1C1C2A)
private val CardDarker = Color(0xFF15151E)
private val CardBorder = Color(0xFF2A2A3A)
private val IconBoxBg = Color(0xFF242434)
private val SurfaceDark = Color(0xFF21212A)
private val Accent = Color(0xFF1A9FFF)
private val SuccessGreen = Color(0xFF5BD68F)
private val DangerRed = Color(0xFFFF7A88)
private val TextPrimary = Color(0xFFD6DAE0)
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

data class ComponentItem(
    val key: String,
    val type: ContentProfile.ContentType,
    val verName: String,
    val isInstalled: Boolean,
    val hasRemote: Boolean,
    val sizeBytes: Long? = null,
)

data class ComponentsDownloadProgress(
    val title: String,
    val message: String,
    val progress: Float = 0f,
    val indeterminate: Boolean = false,
)

data class ComponentsState(
    val currentType: ContentProfile.ContentType = ContentProfile.ContentType.CONTENT_TYPE_WINE,
    val installed: List<ComponentItem> = emptyList(),
    val available: List<ComponentItem> = emptyList(),
    val downloadProgress: ComponentsDownloadProgress? = null,
    val autoCreateContainer: Boolean = true,
)

// ============================================================================
// Root
// ============================================================================

@Composable
fun ComponentsScreen(
    state: ComponentsState,
    onTypeSelected: (ContentProfile.ContentType) -> Unit,
    onInstallFromFile: () -> Unit,
    onDownloadItem: (ComponentItem) -> Unit,
    onRemoveItem: (ComponentItem) -> Unit,
    onToggleAutoCreateContainer: (Boolean) -> Unit,
) {
    var itemPendingRemoval by remember { mutableStateOf<ComponentItem?>(null) }

    itemPendingRemoval?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.settings_content_remove_title),
            message = stringResource(R.string.settings_content_confirm_remove),
            confirmLabel = stringResource(R.string.common_ui_remove),
            confirmColor = DangerRed,
            onDismiss = { itemPendingRemoval = null },
            onConfirm = {
                onRemoveItem(item)
                itemPendingRemoval = null
            },
        )
    }

    state.downloadProgress?.let { progress ->
        DownloadProgressDialog(progress = progress)
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(BgDark)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroHeader(
            installedCount = state.installed.size,
            availableCount = state.available.size,
            autoCreateContainer = state.autoCreateContainer,
            onInstallFromFile = onInstallFromFile,
            onToggleAutoCreateContainer = onToggleAutoCreateContainer,
        )

        TypeTabsCard(
            currentType = state.currentType,
            onTypeSelected = onTypeSelected,
        )

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (
                    fadeIn(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
                        togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
                ).using(
                    SizeTransform(clip = false) { _, _ ->
                        tween(durationMillis = 140, easing = FastOutSlowInEasing)
                    },
                )
            },
            contentKey = { it.currentType },
            label = "componentsTypeContent",
        ) { snapshot ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot.installed.isEmpty() && snapshot.available.isEmpty()) {
                    EmptyState()
                }

                if (snapshot.installed.isNotEmpty()) {
                    SectionLabel(
                        text = stringResource(R.string.common_ui_installed),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    snapshot.installed.forEach { item ->
                        ComponentItemCard(
                            item = item,
                            onDownload = { onDownloadItem(item) },
                            onRemove = { itemPendingRemoval = item },
                        )
                    }
                }

                if (snapshot.available.isNotEmpty()) {
                    SectionLabel(
                        text = stringResource(R.string.common_ui_available),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    snapshot.available.forEach { item ->
                        ComponentItemCard(
                            item = item,
                            onDownload = { onDownloadItem(item) },
                            onRemove = { itemPendingRemoval = item },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================================
// Hero header
// ============================================================================

@Composable
private fun HeroHeader(
    installedCount: Int,
    availableCount: Int,
    autoCreateContainer: Boolean,
    onInstallFromFile: () -> Unit,
    onToggleAutoCreateContainer: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_content_components),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountPill(label = stringResource(R.string.common_ui_installed), count = installedCount)
                    Spacer(Modifier.width(6.dp))
                    CountPill(label = stringResource(R.string.common_ui_available), count = availableCount)
                }
            }
            Spacer(Modifier.width(10.dp))
            ToggleChip(
                label = stringResource(R.string.settings_content_auto_create_container),
                enabled = autoCreateContainer,
                onToggle = { onToggleAutoCreateContainer(!autoCreateContainer) },
            )
            Spacer(Modifier.width(8.dp))
            SmallPillButton(
                label = stringResource(R.string.settings_content_install),
                icon = Icons.Outlined.Upload,
                tint = Accent,
                onClick = onInstallFromFile,
            )
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val tint = if (enabled) SuccessGreen else TextSecondary
    val background = if (enabled) SuccessGreen.copy(alpha = 0.14f) else SurfaceDark
    val borderColor = if (enabled) SuccessGreen.copy(alpha = 0.45f) else CardBorder
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .noRippleClickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CountPill(
    label: String,
    count: Int,
) {
    Row(
        modifier =
            Modifier
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

// ============================================================================
// Content type tabs
// ============================================================================

@Composable
private fun TypeTabsCard(
    currentType: ContentProfile.ContentType,
    onTypeSelected: (ContentProfile.ContentType) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_content_type).uppercase(),
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val types = ContentProfile.ContentType.values()
                types.forEachIndexed { index, type ->
                    TypeTabChip(
                        label = type.toString(),
                        selected = type == currentType,
                        onClick = { onTypeSelected(type) },
                    )
                    if (index < types.lastIndex) {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Crossfade(
                targetState = currentType,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "componentsTypeDescription",
            ) { type ->
                Text(
                    text = stringResource(descriptionResFor(type)),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                )
            }
        }
    }
}

private fun descriptionResFor(type: ContentProfile.ContentType): Int =
    when (type) {
        ContentProfile.ContentType.CONTENT_TYPE_WINE -> R.string.settings_content_desc_wine
        ContentProfile.ContentType.CONTENT_TYPE_PROTON -> R.string.settings_content_desc_proton
        ContentProfile.ContentType.CONTENT_TYPE_DXVK -> R.string.settings_content_desc_dxvk
        ContentProfile.ContentType.CONTENT_TYPE_VKD3D -> R.string.settings_content_desc_vkd3d
        ContentProfile.ContentType.CONTENT_TYPE_BOX64 -> R.string.settings_content_desc_box64
        ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 -> R.string.settings_content_desc_wowbox64
        ContentProfile.ContentType.CONTENT_TYPE_FEXCORE -> R.string.settings_content_desc_fexcore
    }

@Composable
private fun TypeTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) Accent.copy(alpha = 0.18f) else SurfaceDark
    val borderColor = if (selected) Accent.copy(alpha = 0.45f) else CardBorder
    val textColor = if (selected) Accent else TextSecondary
    Box(
        modifier =
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .noRippleClickable(onClick = onClick)
                .padding(horizontal = 14.dp),
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
// Section label
// ============================================================================

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
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
// Component item card
// ============================================================================

@Composable
private fun ComponentItemCard(
    item: ComponentItem,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(item.type),
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.verName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sizeLabel = formatSizeLabel(item)
                if (sizeLabel != null) {
                    Text(
                        text = sizeLabel,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (item.isInstalled) {
                IconTapButton(
                    icon = Icons.Outlined.Delete,
                    tint = DangerRed,
                    onClick = onRemove,
                )
            } else if (item.hasRemote) {
                SmallPillButton(
                    label = stringResource(R.string.common_ui_download),
                    icon = Icons.Outlined.Download,
                    tint = Accent,
                    onClick = onDownload,
                )
            } else {
                // Locally extracted profile with no remote URL — non-interactive placeholder.
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ============================================================================
// Generic small controls
// ============================================================================

@Composable
private fun IconTapButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
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
private fun SmallPillButton(
    label: String,
    icon: ImageVector?,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.14f))
                .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                .noRippleClickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DialogActionButton(
    label: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
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
// Empty state
// ============================================================================

@Composable
private fun EmptyState() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.common_ui_no_items_to_display),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_content_empty_subtitle),
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

// ============================================================================
// Confirm dialog
// ============================================================================

@Composable
private fun DownloadProgressDialog(progress: ComponentsDownloadProgress) {
    Dialog(
        onDismissRequest = { /* non-dismissable while a transfer is in flight */ },
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = progress.title.uppercase(),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = progress.message,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))

                val barHeight = 5.dp
                val barShape = RoundedCornerShape(3.dp)
                if (progress.indeterminate) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(barShape),
                        color = Accent,
                        trackColor = CardDarker,
                    )
                } else {
                    val smoothed by animateFloatAsState(
                        targetValue = progress.progress.coerceIn(0f, 1f),
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "dlProgress",
                    )
                    LinearProgressIndicator(
                        progress = { smoothed },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(barShape),
                        color = Accent,
                        trackColor = CardDarker,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val percentText =
                        if (progress.indeterminate) {
                            stringResource(R.string.common_ui_working)
                        } else {
                            "${(progress.progress * 100).toInt().coerceIn(0, 100)}%"
                        }
                    Text(
                        text = percentText,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

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
            modifier =
                Modifier
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
                    DialogActionButton(label = stringResource(R.string.common_ui_cancel), textColor = TextSecondary, onClick = onDismiss)
                    DialogActionButton(label = confirmLabel, textColor = confirmColor, onClick = onConfirm)
                }
            }
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================

@Composable
private fun formatSizeLabel(item: ComponentItem): String? {
    if (item.isInstalled) {
        val bytes = item.sizeBytes ?: return "${stringResource(R.string.common_ui_size)}: …"
        if (bytes <= 0L) return null
        return "${stringResource(R.string.common_ui_size)}: ${formatBytes(bytes)}"
    }
    if (!item.hasRemote) return null
    val bytes = item.sizeBytes ?: return "${stringResource(R.string.common_ui_size)}: …"
    if (bytes <= 0L) return null
    return "${stringResource(R.string.common_ui_size)}: ${formatBytes(bytes)}"
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

private fun iconFor(type: ContentProfile.ContentType): ImageVector =
    when (type) {
        ContentProfile.ContentType.CONTENT_TYPE_WINE,
        ContentProfile.ContentType.CONTENT_TYPE_PROTON,
        -> Icons.Outlined.Science

        ContentProfile.ContentType.CONTENT_TYPE_DXVK,
        ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
        -> Icons.Outlined.DeveloperBoard

        ContentProfile.ContentType.CONTENT_TYPE_BOX64,
        ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
        ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
        -> Icons.Outlined.Memory
    }
