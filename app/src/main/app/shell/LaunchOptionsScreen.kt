package com.winlator.cmod.app.shell

import android.content.Context
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.shared.ui.nav.DialogPaneNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A single Steam launch option (appinfo `config.launch` entry). */
internal data class StoreLaunchOptionItem(
    // Relative path, '/'-separated.
    val executable: String,
    val arguments: String,
    val label: String,
)

// Workshop window palette (WsBg scheme).
private val WsBg = Color(0xFF12121B)
private val WsBorder = Color(0xFF2A2A3A)
private val WsAccent = Color(0xFF1A9FFF)
private val WsAccentGlow = Color(0xFF58A6FF)
private val WsTextPrimary = Color(0xFFF0F4FF)
private val WsTextSecondary = Color(0xFF93A6BC)
private val WsScrim = Color(0xFF000000)

/** Workshop-styled modal listing the game's `config.launch` entries; selecting a row persists it. */
@Composable
internal fun StoreLaunchOptionsScreen(
    gameTitle: String,
    options: List<StoreLaunchOptionItem>,
    selectedOption: StoreLaunchOptionItem?,
    onSelect: (StoreLaunchOptionItem) -> Unit,
    onClose: () -> Unit,
) {
    val registry = remember { PaneNavRegistry() }
    CompositionLocalProvider(LocalPaneNav provides registry) {
    DialogPaneNav(registry, onDismiss = onClose)
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                // Dim the game-detail screen behind so the modal reads as foreground.
                .background(WsScrim.copy(alpha = 0.6f))
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
            color = WsBg,
            border = BorderStroke(1.dp, WsBorder),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                LaunchOptionsHeader(
                    gameTitle = gameTitle,
                    optionCount = options.size,
                    onClose = onClose,
                )
                HorizontalDivider(color = WsBorder, thickness = 0.5.dp)
                LazyColumn(
                    // fill = false: wrap short lists instead of stretching to max height.
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(options) { index, option ->
                        LaunchOptionPickerRow(
                            option = option,
                            selected = option == selectedOption,
                            onClick = { onSelect(option) },
                        )
                        if (index < options.lastIndex) {
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
}

@Composable
private fun LaunchOptionsHeader(
    gameTitle: String,
    optionCount: Int,
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
                .background(WsAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = WsAccentGlow,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.store_game_launch_options).uppercase(),
                color = WsTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
            Text(
                gameTitle,
                style = MaterialTheme.typography.titleSmall,
                color = WsTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val optionCountDescription = stringResource(R.string.store_game_launch_options_count, optionCount)
        Surface(
            modifier =
                Modifier.semantics {
                    contentDescription = optionCountDescription
                },
            color = WsAccent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(7.dp),
        ) {
            Text(
                optionCount.toString(),
                color = WsAccentGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).paneNavItem(onActivate = onClose)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.common_ui_close),
                tint = WsTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LaunchOptionPickerRow(
    option: StoreLaunchOptionItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .paneNavItem(onActivate = onClick, tapToSelect = true)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                option.label,
                color = if (selected) WsAccentGlow else WsTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(option.executable)
                    if (option.arguments.isNotBlank()) {
                        append("  ·  ")
                        append(option.arguments)
                    }
                },
                color = WsTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = WsAccentGlow,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** UI state for the launch-options picker, shared by both game-detail screens. */
@Stable
internal class SteamLaunchOptionsState {
    var options by mutableStateOf<List<StoreLaunchOptionItem>>(emptyList())
    var selected by mutableStateOf<StoreLaunchOptionItem?>(null)
    var showDialog by mutableStateOf(false)
    var reloadToken by mutableStateOf(0)
        private set

    fun show() {
        reloadToken++
        showDialog = true
    }

    fun dismiss() {
        showDialog = false
    }
}

/** Loads the launch options from cache while [enabled]; the PICS heal rides picker opens. */
@Composable
internal fun rememberSteamLaunchOptionsState(
    appId: Int,
    enabled: Boolean,
): SteamLaunchOptionsState {
    val appContext = LocalContext.current.applicationContext
    val state = remember(appId) { SteamLaunchOptionsState() }
    // reloadToken key: re-read from disk on every picker open (not close) — shortcut
    // settings can change the extras behind this screen's back.
    LaunchedEffect(appId, enabled, state.reloadToken) {
        val ready = enabled && withContext(Dispatchers.IO) { SteamService.isAppInstalled(appId) }
        if (!ready) {
            state.options = emptyList()
            state.selected = null
            return@LaunchedEffect
        }
        val (options, selected) = loadSteamLaunchOptions(appContext, appId)
        state.options = options
        state.selected = selected
        // Cache-only until the picker is opened (reloadToken > 0), so browsing game screens never
        // hits the network; the once-per-session PICS heal runs on first open. Shares
        // picsRefreshedAppsThisSession with depot-selection so the two can't re-fetch the same app.
        if (state.reloadToken > 0 && SteamService.picsRefreshedAppsThisSession.add(appId)) {
            if (SteamService.refreshAppInfoFromPics(appId)) {
                val (fresh, freshSelected) = loadSteamLaunchOptions(appContext, appId)
                state.options = fresh
                state.selected = freshSelected
            } else {
                // Offline or fetch failed — allow a retry on the next open.
                SteamService.picsRefreshedAppsThisSession.remove(appId)
            }
        }
    }
    return state
}

/** Hosts the launch-option picker over a game-detail screen; persists the tapped row. */
@Composable
internal fun SteamLaunchOptionsDialogHost(
    appId: Int,
    gameTitle: String,
    state: SteamLaunchOptionsState,
) {
    val context = LocalContext.current
    // Scope outlives the dialog so a tap-then-dismiss still persists and reports failures.
    val scope = rememberCoroutineScope()
    if (!state.showDialog) return
    Dialog(
        onDismissRequest = state::dismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        StoreLaunchOptionsScreen(
            gameTitle = gameTitle,
            options = state.options,
            selectedOption = state.selected,
            onSelect = { option ->
                persistSteamLaunchOptionSelection(context, appId, option, scope) { state.selected = it }
            },
            onClose = state::dismiss,
        )
    }
}

/** Launch-option list (appinfo config.launch) plus the currently effective selection. */
private suspend fun loadSteamLaunchOptions(
    context: Context,
    appId: Int,
): Pair<List<StoreLaunchOptionItem>, StoreLaunchOptionItem?> =
    withContext(Dispatchers.IO) {
        val appDir = java.io.File(SteamService.getAppDirPath(appId))
        val allOptions =
            SteamService
                .getWindowsLaunchInfos(appId)
                .map { info ->
                    StoreLaunchOptionItem(
                        executable = info.executable,
                        arguments = info.arguments,
                        label = info.description.ifBlank { info.executable.substringAfterLast('/') },
                    )
                }
                // Label kept in the key: stale cached rows have "" args and would
                // collapse distinct options like "Play (DX11)" / "Play (DX12)".
                .distinctBy { Triple(SteamService.normalizeRelativeExe(it.executable).lowercase(), it.arguments, it.label) }
        // Hide options whose exe is missing on disk; case-insensitive to match how
        // the launch path resolves these same appinfo paths against the depot files.
        val onDisk = allOptions.filter { SteamService.fileExistsIgnoreCase(appDir.path, it.executable) }
        val options = onDisk.ifEmpty { allOptions }
        // Exact match against the explicitly picked option, or no checkmark at all —
        // a manually configured exe is never adopted as a launch option.
        val selected =
            SteamService.getSelectedLaunchOption(context, appId)?.let { (exe, args) ->
                // Normalized like every persist/launch-side compare (separators, drive prefix).
                val selectedExe = SteamService.normalizeRelativeExe(exe)
                options.firstOrNull {
                    SteamService.normalizeRelativeExe(it.executable).equals(selectedExe, ignoreCase = true) &&
                        it.arguments == args
                }
            }
        options to selected
    }

private fun persistSteamLaunchOptionSelection(
    context: Context,
    appId: Int,
    option: StoreLaunchOptionItem,
    scope: CoroutineScope,
    onSaved: (StoreLaunchOptionItem) -> Unit,
) {
    scope.launch(Dispatchers.IO) {
        val saved =
            SteamService.setSelectedLaunchOption(
                context.applicationContext,
                appId,
                option.executable,
                option.arguments,
            )
        withContext(Dispatchers.Main) {
            if (saved) {
                onSaved(option)
            } else {
                com.winlator.cmod.shared.ui.toast.WinToast.show(
                    context,
                    context.getString(R.string.store_game_launch_option_failed),
                    android.widget.Toast.LENGTH_SHORT,
                )
            }
        }
    }
}
