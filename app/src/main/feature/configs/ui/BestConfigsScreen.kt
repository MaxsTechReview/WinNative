package com.winlator.cmod.feature.configs.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winlator.cmod.R
import com.winlator.cmod.feature.configs.data.ConfigRow
import com.winlator.cmod.feature.configs.ui.MissingComponentsDialog
import com.winlator.cmod.feature.leaderboard.HardwareDictionary
import com.winlator.cmod.shared.theme.WinNativeAccent
import com.winlator.cmod.shared.theme.WinNativeBackground
import com.winlator.cmod.shared.theme.WinNativeDanger
import com.winlator.cmod.shared.theme.WinNativeOutline
import com.winlator.cmod.shared.theme.WinNativeSurface
import com.winlator.cmod.shared.theme.WinNativeTextPrimary
import com.winlator.cmod.shared.theme.WinNativeTextSecondary

@Composable
fun BestConfigsScreen(
    onBack: () -> Unit,
    viewModel: BestConfigsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Missing-Components import dialog. Hidden when state == Idle (no import in
    // flight) or Done (terminal — toast is fired by the coordinator's onResult
    // callback that the host passes in).
    MissingComponentsDialog(
        state = importState,
        onToggleSelection = { viewModel.toggleImportSelection(it) },
        onConfirmDownload = { viewModel.confirmImportDownload() },
        onApplyAvailableOnly = { viewModel.applyImportAvailableOnly() },
        onRetry = { viewModel.retryImportRow(it) },
        onDismiss = { viewModel.cancelImport() },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WinNativeBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Header(state = state, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        FilterRow(
            current = state.filter,
            onPick = { viewModel.setFilter(it) },
        )
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.phase) {
                LoadPhase.Idle, LoadPhase.Loading -> LoadingState()
                LoadPhase.Error -> ErrorState(
                    message = state.errorMessage ?: stringResource(R.string.best_configs_error_generic),
                    onRetry = { viewModel.refresh() },
                )
                LoadPhase.Loaded -> {
                    val rows = state.visibleRows(viewModel.currentDeviceModel, viewModel.currentGpuModel)
                    if (rows.isEmpty()) EmptyState()
                    else BestConfigsList(
                        rows = rows,
                        myVotes = state.myVotesByConfigId,
                        myUserId = state.myUserId,
                        onUpvote = { viewModel.upvote(it) },
                        onDownvote = { viewModel.downvote(it) },
                        onImport = { row ->
                            viewModel.import(row) { msg ->
                                activity?.let { a ->
                                    if (!a.isFinishing && !a.isDestroyed) {
                                        Toast.makeText(a.applicationContext, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: BestConfigsUiState, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(WinNativeSurface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_ui_back),
                tint = WinNativeTextPrimary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.best_configs_screen_title),
                color = WinNativeTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.best_configs_subtitle, state.gameName.ifBlank { "—" }),
                color = WinNativeTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterRow(current: ConfigFilter, onPick: (ConfigFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            label = stringResource(R.string.best_configs_filter_my_device),
            selected = current == ConfigFilter.MY_DEVICE,
            onClick = { onPick(ConfigFilter.MY_DEVICE) },
        )
        FilterChip(
            label = stringResource(R.string.best_configs_filter_my_gpu),
            selected = current == ConfigFilter.MY_GPU,
            onClick = { onPick(ConfigFilter.MY_GPU) },
        )
        FilterChip(
            label = stringResource(R.string.best_configs_filter_all),
            selected = current == ConfigFilter.ALL,
            onClick = { onPick(ConfigFilter.ALL) },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) WinNativeAccent.copy(alpha = 0.18f) else WinNativeSurface
    val border = if (selected) WinNativeAccent else WinNativeOutline
    val textColor = if (selected) WinNativeAccent else WinNativeTextSecondary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BestConfigsList(
    rows: List<ConfigRow>,
    myVotes: Map<String, Int>,
    myUserId: String?,
    onUpvote: (ConfigRow) -> Unit,
    onDownvote: (ConfigRow) -> Unit,
    onImport: (ConfigRow) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items = rows, key = { it.id }) { row ->
            ConfigRowCard(
                row = row,
                myVote = myVotes[row.id] ?: 0,
                isMine = myUserId != null && row.userId == myUserId,
                onUpvote = { onUpvote(row) },
                onDownvote = { onDownvote(row) },
                onImport = { onImport(row) },
            )
        }
    }
}

@Composable
private fun ConfigRowCard(
    row: ConfigRow,
    myVote: Int,
    isMine: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onImport: () -> Unit,
) {
    val gpuLabel = row.gpuRenderer?.takeIf { it.isNotBlank() } ?: "—"
    val socLabel = row.socModel?.takeIf { it.isNotBlank() } ?: "—"
    val deviceLabel = row.deviceModel?.takeIf { it.isNotBlank() } ?: row.manufacturer ?: "—"
    val borderColor = if (isMine) WinNativeAccent else WinNativeOutline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WinNativeSurface)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoteBar(count = row.voteCount, myVote = myVote, onUp = onUpvote, onDown = onDownvote)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayName,
                    color = WinNativeTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$deviceLabel · $gpuLabel · $socLabel",
                    color = WinNativeTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note,
                        color = WinNativeTextPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ImportButton(onClick = onImport)
        }
    }
}

/**
 * Horizontal thumbs-up / net-count / thumbs-down. The active vote direction is
 * highlighted (blue for upvote, red for downvote); the inactive thumb stays neutral.
 * Tap an already-active thumb to clear your vote; tap the opposite to flip (the
 * ViewModel handles the delta math, including switching ±1 → ∓1 in one server call).
 *
 * Sort order is computed server-side: `vote_count` is the SUM of all upvote/downvote
 * rows for this config (downvotes are stored as −1, so they pull the net down). The
 * query orders by `vote_count.desc, created_at.desc`, so more upvotes → higher on
 * the list and more downvotes → lower.
 */
@Composable
private fun VoteBar(count: Int, myVote: Int, onUp: () -> Unit, onDown: () -> Unit) {
    val upActive = myVote == 1
    val downActive = myVote == -1
    val upTint = if (upActive) WinNativeAccent else WinNativeTextSecondary
    val downTint = if (downActive) WinNativeDanger else WinNativeTextSecondary
    val countTint = when {
        upActive -> WinNativeAccent
        downActive -> WinNativeDanger
        else -> WinNativeTextPrimary
    }
    val borderColor = when {
        upActive -> WinNativeAccent
        downActive -> WinNativeDanger
        else -> WinNativeOutline
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WinNativeSurface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ThumbUp,
            contentDescription = stringResource(R.string.best_configs_upvote_label),
            tint = upTint,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onUp)
                .padding(6.dp)
                .size(18.dp),
        )
        Text(
            text = count.toString(),
            color = countTint,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ThumbDown,
            contentDescription = stringResource(R.string.best_configs_downvote_label),
            tint = downTint,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDown)
                .padding(6.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WinNativeAccent.copy(alpha = 0.18f))
            .border(1.dp, WinNativeAccent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            tint = WinNativeAccent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.best_configs_import_label),
            color = WinNativeAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = WinNativeAccent)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.best_configs_loading), color = WinNativeTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.best_configs_empty_title),
            color = WinNativeTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.best_configs_empty_body), color = WinNativeTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onRetry),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = WinNativeDanger, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = WinNativeTextSecondary, fontSize = 13.sp)
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
