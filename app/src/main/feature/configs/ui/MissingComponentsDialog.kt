package com.winlator.cmod.feature.configs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.feature.configs.installflow.ArchMismatch
import com.winlator.cmod.feature.configs.installflow.ImportState
import com.winlator.cmod.feature.configs.installflow.RequirementEntry
import com.winlator.cmod.feature.configs.installflow.RequirementResolution
import com.winlator.cmod.feature.configs.installflow.RowState
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeDanger
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeSurfaceAlt
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary

/**
 * Material 3 "Missing Components" dialog driven by [ImportState].
 *
 * Built as a bare [Dialog] composable (not [androidx.compose.material3.AlertDialog])
 * because M3 reserves AlertDialog for two-button simple content; a multi-select
 * list with per-row progress doesn't fit. Composition follows the WinNativeTheme
 * palette so the dialog looks like the rest of the app.
 *
 * The dialog handles five of the six [ImportState] variants:
 *  - [ImportState.Analyzing] → indeterminate spinner + "Checking your device…" message
 *  - [ImportState.ChoosingComponents] → header + LazyColumn of selectable rows + footer
 *  - [ImportState.Downloading] → same row layout but each selected row morphs into
 *    a progress bar in-place (per Material 3 selection-then-progress pattern)
 *  - [ImportState.Applying] → indeterminate spinner + "Applying config…"
 *  - [ImportState.Failed] → error icon + reason + Dismiss
 * (Idle and Done are handled by the host: the dialog isn't rendered at all in
 * those states.)
 */
@Composable
fun MissingComponentsDialog(
    state: ImportState,
    onToggleSelection: (String) -> Unit,
    onConfirmDownload: () -> Unit,
    onApplyAvailableOnly: () -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is ImportState.Idle || state is ImportState.Done) return
    val isBusy = state is ImportState.Analyzing ||
        state is ImportState.Downloading ||
        state is ImportState.Applying

    BackHandler(enabled = true) { if (!isBusy) onDismiss() }

    // Compose's `Dialog` with `usePlatformDefaultWidth = false` doesn't bound the
    // height of its content. If the dialog grows taller than the screen, the
    // bottom (the footer with our Download/Cancel buttons) gets clipped off the
    // bottom edge. Cap at ~85% of the screen so the footer is always reachable;
    // the LazyColumn inside takes the remaining space with `weight`, leaving the
    // footer pinned at the bottom of the visible dialog.
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = maxDialogHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(WinNativeBackground)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            when (state) {
                is ImportState.Analyzing -> AnalyzingContent()
                is ImportState.ChoosingComponents -> ChoosingContent(
                    state = state,
                    maxHeight = maxDialogHeight,
                    onToggle = onToggleSelection,
                    onConfirm = onConfirmDownload,
                    onApplyAvailableOnly = onApplyAvailableOnly,
                    onCancel = onDismiss,
                )
                is ImportState.Downloading -> DownloadingContent(
                    state = state,
                    maxHeight = maxDialogHeight,
                    onRetry = onRetry,
                    onApplyAvailableOnly = onApplyAvailableOnly,
                    onCancel = onDismiss,
                )
                is ImportState.Applying -> ApplyingContent()
                is ImportState.Failed -> FailedContent(state.reason, onDismiss)
                ImportState.Idle, is ImportState.Done -> Unit
            }
        }
    }
}

@Composable
private fun AnalyzingContent() {
    SpinnerBlock(label = stringResource(R.string.best_configs_import_analyzing))
}

@Composable
private fun ApplyingContent() {
    SpinnerBlock(label = stringResource(R.string.best_configs_import_applying))
}

@Composable
private fun SpinnerBlock(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = WinNativeAccent)
        Spacer(Modifier.height(10.dp))
        Text(label, color = WinNativeTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ChoosingContent(
    state: ImportState.ChoosingComponents,
    maxHeight: androidx.compose.ui.unit.Dp,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onApplyAvailableOnly: () -> Unit,
    onCancel: () -> Unit,
) {
    val anyAvailable = state.entries.any { it.resolution is RequirementResolution.Available }
    // Column hugs its content but caps at `maxHeight` (computed from screen height
    // in the parent). The LazyColumn's `weight(1f, fill = false)` then shrinks to
    // its content when there are few rows (small dialogs hug their content), or
    // scrolls within the remaining space when there are many rows (footer stays
    // pinned at the bottom). Using `fillMaxHeight` on the Column would have made
    // every dialog render at the full cap height regardless of content — a giant
    // blank area below the footer for short row lists.
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        DialogTitle(
            stringResource(
                if (anyAvailable) R.string.best_configs_missing_title
                else R.string.best_configs_review_title,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                if (anyAvailable) R.string.best_configs_missing_subtitle
                else R.string.best_configs_review_subtitle,
            ),
            color = WinNativeTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        if (state.archMismatch != null) {
            Spacer(Modifier.height(12.dp))
            ArchMismatchWarning(state.archMismatch)
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(items = state.entries, key = { it.requirement.id }) { entry ->
                ChooseRow(
                    entry = entry,
                    isSelected = entry.requirement.id in state.selectedIds,
                    onToggle = { onToggle(entry.requirement.id) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // Footer label depends on whether there's anything actionable. When no
        // Available rows exist (only Installed + Unavailable + Unsupported), the
        // user can't download anything — show a single "Apply" button that just
        // applies the config (skipping keys whose component isn't installed).
        DialogFooter {
            TextDialogButton(text = stringResource(R.string.common_ui_cancel), onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            if (anyAvailable) {
                TextDialogButton(
                    text = stringResource(R.string.best_configs_import_apply_available_only),
                    onClick = onApplyAvailableOnly,
                )
                Spacer(Modifier.width(8.dp))
                FilledDialogButton(
                    text = stringResource(R.string.best_configs_import_download),
                    onClick = onConfirm,
                    enabled = state.selectedIds.isNotEmpty(),
                )
            } else {
                FilledDialogButton(
                    text = stringResource(R.string.best_configs_import_apply_now),
                    onClick = onApplyAvailableOnly,
                )
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    state: ImportState.Downloading,
    maxHeight: androidx.compose.ui.unit.Dp,
    onRetry: (String) -> Unit,
    onApplyAvailableOnly: () -> Unit,
    onCancel: () -> Unit,
) {
    // Same wrap-with-cap pattern as ChoosingContent — see comment there.
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        DialogTitle(stringResource(R.string.best_configs_import_downloading))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.best_configs_import_downloading_subtitle),
            color = WinNativeTextSecondary,
            fontSize = 12.sp,
        )
        if (state.archMismatch != null) {
            Spacer(Modifier.height(12.dp))
            ArchMismatchWarning(state.archMismatch)
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(items = state.entries, key = { it.requirement.id }) { entry ->
                DownloadRow(
                    entry = entry,
                    rowState = state.rowStates[entry.requirement.id],
                    onRetry = { onRetry(entry.requirement.id) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val anyFailed = state.rowStates.values.any { it is RowState.Failed }
        DialogFooter {
            TextDialogButton(text = stringResource(R.string.common_ui_cancel), onClick = onCancel)
            if (anyFailed) {
                Spacer(Modifier.width(8.dp))
                FilledDialogButton(
                    text = stringResource(R.string.best_configs_import_apply_available_only),
                    onClick = onApplyAvailableOnly,
                )
            }
        }
    }
}

@Composable
private fun FailedContent(reason: String, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = null,
                tint = WinNativeDanger,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            DialogTitle(stringResource(R.string.best_configs_import_failed))
        }
        Spacer(Modifier.height(8.dp))
        Text(reason, color = WinNativeTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        DialogFooter {
            FilledDialogButton(text = stringResource(R.string.common_ui_close), onClick = onDismiss)
        }
    }
}

@Composable
private fun ChooseRow(entry: RequirementEntry, isSelected: Boolean, onToggle: () -> Unit) {
    val resolution = entry.resolution
    val enabled = resolution is RequirementResolution.Available
    val rowAlpha = if (enabled) 1f else 0.6f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WinNativeSurface)
            .border(1.dp, WinNativeOutline, RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable(onClick = onToggle) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint) = when (resolution) {
            is RequirementResolution.Installed -> Icons.Outlined.Check to WinNativeAccent
            is RequirementResolution.Available -> {
                val ic = if (isSelected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank
                ic to if (isSelected) WinNativeAccent else WinNativeTextSecondary
            }
            is RequirementResolution.Unavailable -> Icons.Outlined.Block to WinNativeDanger
            is RequirementResolution.Unsupported -> Icons.Outlined.Warning to WinNativeDanger
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = rowAlpha),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.requirement.displayLabel,
                color = WinNativeTextPrimary.copy(alpha = rowAlpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when (resolution) {
                is RequirementResolution.Installed -> stringResource(R.string.best_configs_import_status_installed)
                is RequirementResolution.Available -> {
                    val sub = resolution.substituteFor
                    if (sub != null) {
                        // Concise "→ latest" hint so the user knows we picked a
                        // sibling because the literal version they asked for isn't
                        // in the catalog anymore (typical for pruned nightlies).
                        stringResource(
                            R.string.best_configs_import_status_substitute,
                            resolution.profile.verName,
                        )
                    } else {
                        stringResource(R.string.best_configs_import_status_available)
                    }
                }
                is RequirementResolution.Unavailable -> resolution.reason
                is RequirementResolution.Unsupported -> resolution.reason
            }
            Text(
                text = status,
                color = WinNativeTextSecondary.copy(alpha = rowAlpha),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadRow(entry: RequirementEntry, rowState: RowState?, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WinNativeSurface)
            .border(1.dp, WinNativeOutline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.requirement.displayLabel,
                color = WinNativeTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            DownloadRowProgress(rowState)
        }
        if (rowState is RowState.Failed) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(WinNativeDanger.copy(alpha = 0.15f))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = WinNativeDanger,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.best_configs_import_retry),
                        color = WinNativeDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRowProgress(rowState: RowState?) {
    when (rowState) {
        null, RowState.Queued -> Text(
            text = stringResource(R.string.best_configs_import_status_queued),
            color = WinNativeTextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        is RowState.Downloading -> {
            val frac = rowState.fraction
            if (frac == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = WinNativeAccent,
                    trackColor = WinNativeSurfaceAlt,
                )
            } else {
                val animated by animateFloatAsState(
                    targetValue = frac,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "rowProgress",
                )
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = WinNativeAccent,
                    trackColor = WinNativeSurfaceAlt,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
            val pct = rowState.fraction?.let { "${(it * 100).toInt()}%" } ?: "…"
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.best_configs_import_status_downloading, pct),
                color = WinNativeTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        RowState.Installing -> Text(
            text = stringResource(R.string.best_configs_import_status_installing),
            color = WinNativeAccent,
            fontSize = 11.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        RowState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = WinNativeAccent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.best_configs_import_status_done),
                color = WinNativeAccent,
                fontSize = 11.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        is RowState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Error, contentDescription = null, tint = WinNativeDanger, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = rowState.reason,
                color = WinNativeDanger,
                fontSize = 11.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ArchMismatchWarning(mismatch: ArchMismatch) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WinNativeDanger.copy(alpha = 0.10f))
            .border(1.dp, WinNativeDanger.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = WinNativeDanger,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                R.string.best_configs_import_arch_mismatch,
                mismatch.expectedArch,
                mismatch.containerArch,
            ),
            color = WinNativeTextPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        color = WinNativeTextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogFooter(content: @Composable () -> Unit) {
    // FlowRow rather than Row — three buttons ("Cancel" / "Apply available" /
    // "Download & apply") at 13sp + padding overflow the 320dp minimum dialog
    // width. With FlowRow the last button wraps to a second line below the
    // others instead of getting clipped on the left.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun TextDialogButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = WinNativeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilledDialogButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val bg = if (enabled) WinNativeAccent else WinNativeAccent.copy(alpha = 0.3f)
    val fg = if (enabled) Color.White else Color.White.copy(alpha = 0.7f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
