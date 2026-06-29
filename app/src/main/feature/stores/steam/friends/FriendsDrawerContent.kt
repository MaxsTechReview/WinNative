package com.winlator.cmod.feature.stores.steam.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.steam.chat.ChatOverlayService
import com.winlator.cmod.feature.stores.steam.data.SteamFriend
import com.winlator.cmod.feature.stores.steam.data.SteamFriendEntry
import com.winlator.cmod.feature.stores.steam.enums.EPersonaState
import com.winlator.cmod.feature.stores.steam.utils.PrefManager

private val BgDark = Color(0xFF18181D)
private val SurfaceDark = Color(0xFF1E252E)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val StatusOnline = Color(0xFF3FB950)
private val StatusAway = Color(0xFFF0C040)
private val StatusOffline = Color(0xFF6E7681)

private fun statusColor(state: EPersonaState): Color = when (state) {
    EPersonaState.Online, EPersonaState.LookingToTrade, EPersonaState.LookingToPlay -> StatusOnline
    EPersonaState.Away, EPersonaState.Snooze, EPersonaState.Busy -> StatusAway
    else -> StatusOffline
}

@Composable
private fun statusLabel(state: EPersonaState): String = when (state) {
    EPersonaState.Online -> stringResource(R.string.stores_accounts_status_online)
    EPersonaState.Away -> stringResource(R.string.stores_accounts_status_away)
    EPersonaState.Snooze -> stringResource(R.string.steam_presence_snooze)
    EPersonaState.Busy -> stringResource(R.string.steam_presence_busy)
    EPersonaState.LookingToTrade -> stringResource(R.string.steam_presence_looking_to_trade)
    EPersonaState.LookingToPlay -> stringResource(R.string.steam_presence_looking_to_play)
    EPersonaState.Invisible -> stringResource(R.string.stores_accounts_status_invisible)
    else -> stringResource(R.string.stores_accounts_status_offline)
}

@Composable
fun FriendsDrawerContent(
    self: SteamFriend,
    friends: List<SteamFriendEntry>,
    installedGameIds: Set<Int>,
    onSetState: (EPersonaState) -> Unit,
    onOpenChat: (SteamFriendEntry) -> Unit,
    onJoinGame: (SteamFriendEntry) -> Unit,
    onPlayGame: (SteamFriendEntry) -> Unit,
) {
    val inGame = friends.filter { it.isPlayingGame }.sortedBy { it.name.lowercase() }
    val online = friends.filter { it.isOnline && !it.isPlayingGame }.sortedBy { it.name.lowercase() }
    val offline = friends.filter { !it.isOnline }.sortedBy { it.name.lowercase() }

    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = BgDark,
        drawerContentColor = TextPrimary,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.width(332.dp),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            SelfCard(self = self, onSetState = onSetState)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.steam_friends_count, friends.count { it.isOnline }),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (inGame.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.steam_friends_section_in_game, inGame.size)) }
                    items(inGame, key = { it.steamId }) {
                        InGameFriendCard(it, it.gameAppId in installedGameIds, onOpenChat, onJoinGame, onPlayGame)
                    }
                }
                if (online.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.steam_friends_section_online, online.size)) }
                    items(online, key = { it.steamId }) {
                        FriendRow(it, it.gameAppId in installedGameIds, onOpenChat, onJoinGame, onPlayGame)
                    }
                }
                if (offline.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.steam_friends_section_offline, offline.size)) }
                    items(offline, key = { it.steamId }) {
                        FriendRow(it, it.gameAppId in installedGameIds, onOpenChat, onJoinGame, onPlayGame)
                    }
                }
                if (friends.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.steam_friends_none_loaded),
                            color = TextSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SelfCard(self: SteamFriend, onSetState: (EPersonaState) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(self.avatarHashUrl(), 44.dp, statusColor(self.state))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        self.name.ifBlank { stringResource(R.string.steam_friends_self_you) },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(self.state)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            statusLabel(self.state),
                            color = TextSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.steam_friends_settings),
                    tint = TextSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showChatSettings = true }
                        .padding(6.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    if (expanded) "▲" else "▼",
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { expanded = !expanded }
                        .padding(6.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                    StatusOption(stringResource(R.string.stores_accounts_status_online), StatusOnline) { onSetState(EPersonaState.Online); expanded = false }
                    StatusOption(stringResource(R.string.stores_accounts_status_away), StatusAway) { onSetState(EPersonaState.Away); expanded = false }
                    StatusOption(stringResource(R.string.stores_accounts_status_invisible), StatusOffline) { onSetState(EPersonaState.Invisible); expanded = false }
                }
            }
        }
    }
    if (showChatSettings) {
        ChatSettingsDialog { showChatSettings = false }
    }
}

@Composable
private fun ChatSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(PrefManager.chatNotificationsEnabled) }
    var heads by remember { mutableStateOf(PrefManager.chatHeadsEnabled) }
    var autoHide by remember { mutableStateOf(PrefManager.chatHeadsAutoHide) }
    var inGame by remember { mutableStateOf(PrefManager.chatInGameEnabled) }
    var stayRunning by remember { mutableStateOf(PrefManager.chatStayRunningOnExit) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(Modifier.padding(18.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.steam_chat_settings_title),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChatSettingToggle(
                            stringResource(R.string.steam_chat_setting_notifications),
                            stringResource(R.string.steam_chat_setting_notifications_desc),
                            notifications,
                        ) { v -> notifications = v; PrefManager.chatNotificationsEnabled = v }
                        ChatSettingToggle(
                            stringResource(R.string.steam_chat_setting_heads),
                            stringResource(R.string.steam_chat_setting_heads_desc),
                            heads,
                        ) { v ->
                            if (v) {
                                if (android.provider.Settings.canDrawOverlays(context)) {
                                    heads = true
                                    PrefManager.chatHeadsEnabled = true
                                    ChatOverlayService.start(context)
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:" + context.packageName),
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            } else {
                                heads = false
                                PrefManager.chatHeadsEnabled = false
                                ChatOverlayService.stop(context)
                            }
                        }
                        ChatSettingToggle(
                            stringResource(R.string.steam_chat_setting_autohide),
                            stringResource(R.string.steam_chat_setting_autohide_desc),
                            autoHide,
                        ) { v -> autoHide = v; PrefManager.chatHeadsAutoHide = v }
                    }
                    Column(Modifier.weight(1f)) {
                        ChatSettingToggle(
                            stringResource(R.string.steam_chat_setting_ingame),
                            stringResource(R.string.steam_chat_setting_ingame_desc),
                            inGame,
                        ) { v -> inGame = v; PrefManager.chatInGameEnabled = v }
                        ChatSettingToggle(
                            stringResource(R.string.steam_chat_setting_stay_running),
                            stringResource(R.string.steam_chat_setting_stay_running_desc),
                            stayRunning,
                        ) { v -> stayRunning = v; PrefManager.chatStayRunningOnExit = v }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSettingToggle(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(desc, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun StatusOption(label: String, dot: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FriendActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.6f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FriendActionButtons(
    friend: SteamFriendEntry,
    gameInstalled: Boolean,
    onJoinGame: (SteamFriendEntry) -> Unit,
    onPlayGame: (SteamFriendEntry) -> Unit,
) {
    when {
        friend.isJoinable -> {
            Spacer(Modifier.width(8.dp))
            FriendActionButton(stringResource(R.string.steam_friends_join), Icons.Outlined.PlayArrow) { onJoinGame(friend) }
        }
        friend.isPlayingGame && gameInstalled -> {
            Spacer(Modifier.width(8.dp))
            FriendActionButton(stringResource(R.string.steam_friends_play), Icons.Outlined.SportsEsports) { onPlayGame(friend) }
        }
    }
}

@Composable
private fun InGameFriendCard(
    friend: SteamFriendEntry,
    gameInstalled: Boolean,
    onOpenChat: (SteamFriendEntry) -> Unit,
    onJoinGame: (SteamFriendEntry) -> Unit,
    onPlayGame: (SteamFriendEntry) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Accent.copy(alpha = 0.07f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpenChat(friend) },
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(friend.avatarUrl, 40.dp, StatusOnline)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    friend.name.ifBlank { friend.steamId.toString() },
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Small game art at text height, with the game name to the right.
                Row(
                    Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (friend.gameCapsuleUrl != null) {
                        AsyncImage(
                            model = friend.gameCapsuleUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(56.dp)
                                .height(21.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SurfaceDark),
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        friend.gameName.ifBlank { stringResource(R.string.steam_friends_in_game) },
                        color = StatusOnline,
                        fontWeight = FontWeight.Medium,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FriendActionButtons(friend, gameInstalled, onJoinGame, onPlayGame)
        }
    }
}

@Composable
private fun FriendRow(
    friend: SteamFriendEntry,
    gameInstalled: Boolean,
    onOpenChat: (SteamFriendEntry) -> Unit,
    onJoinGame: (SteamFriendEntry) -> Unit,
    onPlayGame: (SteamFriendEntry) -> Unit,
) {
    val scale by animateFloatAsState(1f, label = "row")
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (friend.isPlayingGame) Accent.copy(alpha = 0.07f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpenChat(friend) },
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(friend.avatarUrl, 40.dp, statusColor(friend.state), dim = !friend.isOnline)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    friend.name.ifBlank { friend.steamId.toString() },
                    color = if (friend.isOnline) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    friend.isPlayingGame -> friend.gameName.ifBlank { stringResource(R.string.steam_friends_in_game) }
                    else -> statusLabel(friend.state)
                }
                Text(
                    sub,
                    color = if (friend.isPlayingGame) StatusOnline else TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FriendActionButtons(friend, gameInstalled, onJoinGame, onPlayGame)
        }
    }
}

@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp, ring: Color, dim: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceDark),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
                alpha = if (dim) 0.55f else 1f,
            )
        }
    }
}

private fun SteamFriend.avatarHashUrl(): String? =
    avatarHash.takeIf { it.isNotBlank() }
        ?.let { "https://avatars.akamai.steamstatic.com/${it}_full.jpg" }
