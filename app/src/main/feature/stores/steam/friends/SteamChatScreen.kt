package com.winlator.cmod.feature.stores.steam.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.winlator.cmod.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.shared.ui.nav.DialogPaneNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.winlator.cmod.feature.stores.steam.data.SteamChatMessage
import com.winlator.cmod.feature.stores.steam.data.SteamFriendEntry
import com.winlator.cmod.feature.stores.steam.service.SteamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WsBg = Color(0xFF12121B)
private val CardBorder = Color(0xFF2A2A3A)
private val BgDark = Color(0xFF171722)
private val SurfaceDark = Color(0xFF1B1B27)
private val Accent = Color(0xFF1A9FFF)
private val AccentGlow = Color(0xFF58A6FF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF93A6BC)

private val IMG_BBCODE = Regex("""\[img](.+?)\[/img]""", RegexOption.IGNORE_CASE)
// Steam delivers chat images as [img src=URL] or as a bare UGC URL.
private val IMG_SRC = Regex("""\[img\b[^]]*?\bsrc=["']?([^\s"'\]]+)""", RegexOption.IGNORE_CASE)
private val BARE_IMG_URL = Regex("""https?://\S+\.(?:png|jpe?g|gif|webp|bmp)""", RegexOption.IGNORE_CASE)
// Public displayable Steam UGC images (not /filedownload/ endpoints, which need auth).
private val STEAM_IMG_URL = Regex("""https?://\S*images\.steamusercontent\.com/ugc/\S+""", RegexOption.IGNORE_CASE)

private fun imageUrlOf(text: String): String? {
    val t = text.trim()
    return IMG_BBCODE.find(t)?.groupValues?.getOrNull(1)
        ?: IMG_SRC.find(t)?.groupValues?.getOrNull(1)
        ?: STEAM_IMG_URL.find(t)?.value
        ?: BARE_IMG_URL.find(t)?.value
}

@Composable
fun SteamChatScreen(
    friend: SteamFriendEntry,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<SteamChatMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && !uploading) {
            uploading = true
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                        .getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    uploading = false
                    return@launch
                }
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpeg"
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    else -> "png"
                }
                val url = SteamService.instance?.sendChatImage(friend.steamId, bytes, "image.$ext")
                if (url.isNullOrBlank()) {
                    messages.add(SteamChatMessage(fromSelf = true, text = context.getString(R.string.steam_chat_image_failed), timestamp = 0))
                    listState.animateScrollToItem(messages.size - 1)
                }
                uploading = false
            }
        }
    }

    LaunchedEffect(friend.steamId) {
        loading = true
        messages.clear()
        SteamService.instance?.setActiveConversation(friend.steamId)
        messages.addAll(SteamService.instance?.loadChatHistory(friend.steamId) ?: emptyList())
        loading = false
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
        SteamService.instance?.incomingChat?.collect { (fid, m) ->
            if (fid != friend.steamId) return@collect
            val known = messages.map { it.timestamp to it.ordinal }.toHashSet()
            if (m.timestamp != 0 && (m.timestamp to m.ordinal) in known) return@collect
            val mImg = imageUrlOf(m.text)
            val optIdx = if (m.fromSelf) {
                messages.indexOfFirst {
                    it.fromSelf && it.timestamp == 0 &&
                        (it.text == m.text || (mImg != null && imageUrlOf(it.text) == mImg))
                }
            } else -1
            if (optIdx >= 0) {
                messages[optIdx] = m
            } else {
                messages.add(m)
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    DisposableEffect(friend.steamId) {
        onDispose { SteamService.instance?.clearActiveConversation(friend.steamId) }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        sending = true
        input = ""
        val optimistic = SteamChatMessage(fromSelf = true, text = text, timestamp = 0, ordinal = 0)
        messages.add(optimistic)
        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val ok = SteamService.instance?.sendChatMessage(friend.steamId, text) ?: false
            if (!ok) {
                val idx = messages.indexOf(optimistic)
                if (idx >= 0) messages[idx] = optimistic.copy(text = "$text  " + context.getString(R.string.steam_chat_not_sent))
            }
            sending = false
        }
    }

    val registry = remember { PaneNavRegistry() }
    val fieldFocus = remember { FocusRequester() }
    registry.onEdgeUp = { scope.launch { runCatching { listState.animateScrollBy(-280f) } } }
    registry.onEdgeDown = { scope.launch { runCatching { listState.animateScrollBy(280f) } } }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CompositionLocalProvider(LocalPaneNav provides registry) {
            DialogPaneNav(registry, onDismiss = onClose)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = WsBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.95f)
                    .imePadding(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(BgDark),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (friend.avatarUrl != null) {
                                AsyncImage(
                                    model = friend.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                friend.name.ifBlank { friend.steamId.toString() },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (friend.isPlayingGame) friend.gameName.ifBlank { stringResource(R.string.steam_friends_in_game) }
                                else if (friend.isOnline) stringResource(R.string.stores_accounts_status_online) else stringResource(R.string.stores_accounts_status_offline),
                                color = if (friend.isOnline) Accent else TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(36.dp).paneNavItem(cornerRadius = 18.dp, onActivate = onClose, tapToSelect = true, navRow = 0, navCol = 0),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.steam_common_back), tint = TextSecondary)
                        }
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Accent,
                                modifier = Modifier.size(28.dp).align(Alignment.Center),
                            )
                        } else if (messages.isEmpty()) {
                            Text(
                                stringResource(R.string.steam_chat_empty),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item { Spacer(Modifier.height(8.dp)) }
                                items(messages) { msg -> MessageBubble(msg) }
                                item { Spacer(Modifier.height(8.dp)) }
                            }
                        }
                    }

                    if (uploading) {
                        Row(
                            Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.steam_chat_uploading_image), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !uploading,
                            modifier = Modifier.size(42.dp).paneNavItem(
                                cornerRadius = 21.dp,
                                onActivate = {
                                    if (!uploading) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                tapToSelect = true,
                                navRow = 1,
                                navCol = 0,
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = stringResource(R.string.steam_chat_send_image),
                                tint = if (uploading) TextSecondary else Accent,
                            )
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(fieldFocus)
                                .paneNavItem(cornerRadius = 22.dp, onActivate = { runCatching { fieldFocus.requestFocus() } }, navRow = 1, navCol = 1, isEntry = true),
                            placeholder = { Text(stringResource(R.string.steam_chat_message_hint), color = TextSecondary) },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = BgDark,
                                unfocusedContainerColor = BgDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Accent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { send() },
                            enabled = input.isNotBlank() && !sending,
                            modifier = Modifier.size(42.dp).paneNavItem(cornerRadius = 21.dp, onActivate = { send() }, tapToSelect = true, navRow = 1, navCol = 2),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.steam_chat_send),
                                tint = if (input.isNotBlank() && !sending) Accent else TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: SteamChatMessage) {
    val imageUrl = imageUrlOf(msg.text)
    val bubbleColor = if (msg.fromSelf) Accent.copy(alpha = 0.22f) else SurfaceDark
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromSelf) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (msg.fromSelf) 14.dp else 4.dp,
                bottomEnd = if (msg.fromSelf) 4.dp else 14.dp,
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.steam_chat_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(4.dp)
                        .width(236.dp)
                        .heightIn(min = 120.dp, max = 240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgDark),
                )
            } else {
                Text(
                    msg.text,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
