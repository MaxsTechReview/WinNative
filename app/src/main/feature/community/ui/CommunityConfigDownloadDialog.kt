package com.winlator.cmod.feature.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.feature.community.ComponentChecker
import com.winlator.cmod.feature.community.DeviceIdentity
import com.winlator.cmod.feature.community.net.CommunityApiClient
import com.winlator.cmod.feature.community.net.CommunityFilter
import com.winlator.cmod.feature.community.net.ConfigSummary
import com.winlator.cmod.shared.theme.GameSettingsStyle
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem
import kotlinx.coroutines.launch
import org.json.JSONObject

private val WsBg = Color(0xFF12121B)
private val Card = GameSettingsStyle.CardSurface
private val CardBorder = GameSettingsStyle.CardBorder
private val InputBg = GameSettingsStyle.InputSurface
private val Accent = GameSettingsStyle.AccentBlue
private val TextPrimary = GameSettingsStyle.TextPrimary
private val TextSecondary = GameSettingsStyle.TextSecondary
private val TextDim = GameSettingsStyle.TextDim
private val NavHighlight = GameSettingsStyle.NavHighlight
private val Up = Color(0xFF4CD07D)
private val Down = GameSettingsStyle.DangerRed
private val Scrim = Color(0xFF000000)

@Composable
internal fun CommunityConfigDownloadScreen(
    gameTitle: String,
    gameKey: String,
    hw: DeviceIdentity.HardwareBlock,
    api: CommunityApiClient,
    registry: PaneNavRegistry,
    applyConfig: suspend (JSONObject) -> List<ComponentChecker.Missing>,
    onAppliedDismiss: () -> Unit,
    onClose: () -> Unit,
    toast: (String) -> Unit,
    voteGate: (() -> Unit) -> Unit = { it() },
    openPreview: (JSONObject) -> Unit = {},
    registerBackHandler: (() -> Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(CommunityFilter.CHIPSET) }
    var configs by remember { mutableStateOf<List<ConfigSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deviceDisplay by remember { mutableStateOf("") }
    var chipsetDisplay by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf<List<ComponentChecker.Missing>?>(null) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val overlayRegistry = remember { PaneNavRegistry() }
    val overlayOpen = missing != null || reportTarget != null || deleteTarget != null

    SideEffect {
        registry.overlay = if (overlayOpen) overlayRegistry else null
        registerBackHandler {
            when {
                missing != null -> { missing = null; true }
                reportTarget != null -> { reportTarget = null; true }
                deleteTarget != null -> { deleteTarget = null; true }
                else -> false
            }
        }
    }
    DisposableEffect(Unit) { onDispose { registry.overlay = null } }
    LaunchedEffect(overlayOpen) { if (overlayOpen) overlayRegistry.reset() }

    suspend fun reload() {
        loading = true
        error = null
        runCatching { api.listConfigs(gameKey, filter, hw) }
            .onSuccess {
                configs = it.configs
                deviceDisplay = it.deviceDisplay
                chipsetDisplay = it.chipsetDisplay
                loading = false
            }
            .onFailure { error = it.message ?: "Failed to load"; loading = false }
    }
    LaunchedEffect(filter) { reload() }

    fun update(id: String, transform: (ConfigSummary) -> ConfigSummary) {
        configs = configs.map { if (it.id == id) transform(it) else it }
    }
    fun doApply(settings: JSONObject) {
        scope.launch {
            runCatching { applyConfig(settings) }
                .onSuccess { miss -> if (miss.isEmpty()) onAppliedDismiss() else missing = miss }
                .onFailure { toast(it.message ?: "Failed to apply") }
        }
    }
    fun fetchThen(id: String, action: (JSONObject) -> Unit) {
        scope.launch {
            runCatching { api.fetchSettings(id) }
                .onSuccess { action(it) }
                .onFailure { toast(it.message ?: "This config is no longer available"); reload() }
        }
    }

    Box(
        Modifier.fillMaxWidth().fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalPaneNav provides registry) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .background(WsBg).border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            "Community Configs", color = TextPrimary, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(gameTitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Chipset", filter == CommunityFilter.CHIPSET, isEntry = true) {
                            filter = CommunityFilter.CHIPSET
                        }
                        Chip("Device", filter == CommunityFilter.DEVICE) {
                            filter = CommunityFilter.DEVICE
                        }
                        Chip("All", filter == CommunityFilter.ALL) { filter = CommunityFilter.ALL }
                    }
                    Spacer(Modifier.weight(1f))
                    Pill("Close", TextSecondary, onClick = onClose)
                }
                val sub = when (filter) {
                    CommunityFilter.CHIPSET ->
                        "Chipset: ${chipsetDisplay.ifBlank { hw.socModel.ifBlank { hw.boardPlatform } }}"
                    CommunityFilter.DEVICE -> "Device: ${deviceDisplay.ifBlank { hw.modelNumber }}"
                    CommunityFilter.ALL -> "All devices"
                }
                Text(sub, color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        }
                        error != null -> CenterText("⚠ $error", Down)
                        configs.isEmpty() ->
                            CenterText("No community configs for this game yet.", TextDim)
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(configs, key = { it.id }) { cfg ->
                                ConfigRow(
                                    cfg = cfg,
                                    onApply = { fetchThen(cfg.id) { doApply(it) } },
                                    onPreview = { fetchThen(cfg.id) { openPreview(it) } },
                                    onVote = { up ->
                                        voteGate {
                                            scope.launch {
                                                runCatching { api.vote(cfg.id, up) }
                                                    .onSuccess { v ->
                                                        update(cfg.id) {
                                                            it.copy(up = v.up, down = v.down, myVote = v.myVote)
                                                        }
                                                    }
                                                    .onFailure { toast(it.message ?: "Vote failed") }
                                            }
                                        }
                                    },
                                    onReport = { reportTarget = cfg.id },
                                    onDelete = { deleteTarget = cfg.id },
                                )
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalPaneNav provides overlayRegistry) {
            missing?.let { MissingComponentDialog(it) { missing = null } }

            deleteTarget?.let { id ->
                ConfirmDialog(
                    title = "Delete this config?",
                    message = "It will be removed from the community list for everyone. " +
                        "This cannot be undone.",
                    confirmLabel = "Delete",
                    confirmTint = Down,
                    onConfirm = {
                        deleteTarget = null
                        scope.launch {
                            runCatching { api.deleteConfig(id) }
                                .onSuccess {
                                    configs = configs.filterNot { c -> c.id == id }
                                    toast("Deleted")
                                }
                                .onFailure { toast(it.message ?: "Delete failed") }
                        }
                    },
                    onCancel = { deleteTarget = null },
                )
            }

            reportTarget?.let { id ->
                ReportDialog(
                    onSubmit = { reason ->
                        reportTarget = null
                        scope.launch {
                            runCatching { api.report(id, reason) }
                                .onSuccess { toast("Reported — thank you") }
                                .onFailure { toast(it.message ?: "Report failed") }
                        }
                    },
                    onCancel = { reportTarget = null },
                )
            }
        }
    }
}

@Composable
private fun ConfigRow(
    cfg: ConfigSummary,
    onApply: () -> Unit,
    onPreview: () -> Unit,
    onVote: (Boolean) -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f)
                    .paneNavItem(
                        cornerRadius = 8.dp,
                        onActivate = onApply,
                        onSecondary = onPreview,
                        highlightColor = NavHighlight,
                        tapToSelect = true,
                    ),
            ) {
                Text(
                    "${cfg.resolution.ifBlank { "—" }}   ·   ${cfg.store.ifBlank { "—" }}   ·   ${cfg.uploaderHandle}",
                    color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
                val meta = listOfNotNull(
                    cfg.dxwrapper.takeIf { it.isNotBlank() },
                    cfg.wineVersion.takeIf { it.isNotBlank() },
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(
                        meta, color = TextDim, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            VoteBtn("▲", cfg.up, cfg.myVote == 1, Up) { onVote(true) }
            Spacer(Modifier.width(6.dp))
            VoteBtn("▼", cfg.down, cfg.myVote == -1, Down) { onVote(false) }
        }
        Spacer(Modifier.height(8.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("Apply", Accent, onClick = onApply)
            Pill("Report", TextSecondary, onClick = onReport)
            Pill("Preview", Accent, onClick = onPreview)
            if (cfg.ownedByMe) Pill("Delete", Down, onClick = onDelete)
        }
    }
}

@Composable
private fun VoteBtn(glyph: String, count: Int, active: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.16f) else InputBg)
            .border(
                1.dp, if (active) color.copy(alpha = 0.5f) else CardBorder,
                RoundedCornerShape(8.dp),
            )
            .paneNavItem(
                cornerRadius = 8.dp,
                onActivate = onClick,
                highlightColor = NavHighlight,
                tapToSelect = true,
            )
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, color = if (active) color else TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            "$count", color = if (active) color else TextSecondary, fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, isEntry: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.12f) else InputBg)
            .border(
                1.dp, if (selected) Accent.copy(alpha = 0.5f) else CardBorder,
                RoundedCornerShape(8.dp),
            )
            .paneNavItem(
                cornerRadius = 8.dp,
                onActivate = onClick,
                highlightColor = NavHighlight,
                tapToSelect = true,
                isEntry = isEntry,
            )
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label, color = if (selected) Accent else TextSecondary, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Pill(
    label: String,
    tint: Color,
    enabled: Boolean = true,
    isEntry: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .paneNavItem(
                cornerRadius = 8.dp,
                onActivate = { if (enabled) onClick() },
                highlightColor = NavHighlight,
                tapToSelect = true,
                isEntry = isEntry,
            )
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label, color = if (enabled) tint else TextDim, fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CenterText(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmTint: Color,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().fillMaxHeight().background(Scrim.copy(alpha = 0.6f))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.78f).clip(RoundedCornerShape(14.dp)).background(Card)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp)).padding(16.dp)
                .clickable(enabled = false) {},
        ) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                message, color = TextDim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                Pill("Cancel", TextSecondary, isEntry = true, onClick = onCancel)
                Pill(confirmLabel, confirmTint, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun ReportDialog(onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Box(
        Modifier.fillMaxWidth().fillMaxHeight().background(Scrim.copy(alpha = 0.6f))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(14.dp)).background(Card)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp)).padding(16.dp)
                .clickable(enabled = false) {},
        ) {
            Text(
                "Report config", color = TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Why are you reporting this? (letters, digits and basic punctuation)",
                color = TextDim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Box(
                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp))
                    .background(InputBg).border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .paneNavItem(
                        cornerRadius = 8.dp,
                        onActivate = { focus.requestFocus() },
                        highlightColor = NavHighlight,
                        tapToSelect = true,
                        isEntry = true,
                    )
                    .padding(10.dp),
            ) {
                BasicTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (reason.isBlank()) Text("Reason…", color = TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                Pill("Cancel", TextSecondary, onClick = onCancel)
                Pill("Submit", Accent, enabled = reason.trim().length >= 3) {
                    if (reason.trim().length >= 3) onSubmit(reason.trim())
                }
            }
        }
    }
}
