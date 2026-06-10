package com.winlator.cmod.app.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single Steam beta branch entry from appinfo depots.branches. */
internal data class StoreBetaBranchItem(
    val name: String,
    val buildId: Long,
    val timeUpdated: Date?,
    val pwdRequired: Boolean,
)

// Palette — mirrors the LaunchOptions / Workshop window so the modal feels native.
private val BbBg = Color(0xFF12121B)
private val BbBorder = Color(0xFF2A2A3A)
private val BbAccent = Color(0xFF1A9FFF)
private val BbAccentGlow = Color(0xFF58A6FF)
private val BbTextPrimary = Color(0xFFF0F4FF)
private val BbTextSecondary = Color(0xFF93A6BC)
private val BbScrim = Color(0xFF000000)
private val BbLocked = Color(0xFF505060)

/**
 * Steam beta-branch picker — a Workshop-shaped modal window listing the game's
 * PICS depots.branches entries. Tapping an unlocked row persists the selection
 * and triggers the update flow. Password-protected branches are shown as
 * disabled (no beta-password support in this app).
 *
 * Stateless: data and callbacks are hoisted to the BetaBranchesDialog wrapper.
 */
@Composable
internal fun StoreBetaBranchScreen(
    gameTitle: String,
    branches: List<StoreBetaBranchItem>,
    selectedBranch: StoreBetaBranchItem?,
    onSelect: (StoreBetaBranchItem) -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BbScrim.copy(alpha = 0.6f))
                .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        val dialogWidth = (maxWidth - 32.dp).coerceAtMost(560.dp)
        val dialogMaxHeight = (maxHeight - 48.dp).coerceIn(220.dp, 640.dp)
        Surface(
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = dialogWidth)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight),
            shape = RoundedCornerShape(14.dp),
            color = BbBg,
            border = BorderStroke(1.dp, BbBorder),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                BetaBranchHeader(
                    gameTitle = gameTitle,
                    branchCount = branches.size,
                    onClose = onClose,
                )
                HorizontalDivider(color = BbBorder, thickness = 0.5.dp)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(branches) { index, branch ->
                        BetaBranchPickerRow(
                            branch = branch,
                            selected = branch == selectedBranch,
                            onClick = { if (!branch.pwdRequired) onSelect(branch) },
                        )
                        if (index < branches.lastIndex) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.06f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BetaBranchHeader(
    gameTitle: String,
    branchCount: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(BbAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AltRoute,
                contentDescription = null,
                tint = BbAccentGlow,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.store_game_beta_branch).uppercase(),
                color = BbTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
            Text(
                gameTitle,
                style = MaterialTheme.typography.titleSmall,
                color = BbTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier =
                Modifier.semantics {
                    contentDescription = "$branchCount branches"
                },
            color = BbAccent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(7.dp),
        ) {
            Text(
                branchCount.toString(),
                color = BbAccentGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Close",
                tint = BbTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BetaBranchPickerRow(
    branch: StoreBetaBranchItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowAlpha = if (branch.pwdRequired) 0.45f else 1f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (!branch.pwdRequired) Modifier.clickable(onClick = onClick) else Modifier)
                .alpha(rowAlpha)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val displayName = if (branch.name == "public") "${branch.name}  (default)" else branch.name
            Text(
                displayName,
                color = if (selected) BbAccentGlow else BbTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val dateStr =
                remember(branch.timeUpdated) {
                    branch.timeUpdated
                        ?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it) }
                        ?: "—"
                }
            Text(
                "build ${branch.buildId}  ·  $dateStr",
                color = BbTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            branch.pwdRequired -> Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = BbLocked,
                modifier = Modifier.size(17.dp),
            )
            selected -> Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = BbAccentGlow,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
