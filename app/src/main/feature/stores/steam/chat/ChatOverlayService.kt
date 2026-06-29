package com.winlator.cmod.feature.stores.steam.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.steam.data.SteamChatMessage
import com.winlator.cmod.feature.stores.steam.data.SteamFriendEntry
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val IMG_BBCODE = Regex("\\[img\\](.*?)\\[/img\\]", RegexOption.IGNORE_CASE)
private val IMG_SRC = Regex("\\[img\\s+src=[\"']?(.*?)[\"']?\\s*\\]", RegexOption.IGNORE_CASE)
private val STEAM_IMG_URL = Regex("https://images\\.steamusercontent\\.com/ugc/\\S+")
private val BARE_IMG_URL = Regex("https?://\\S+\\.(?:png|jpe?g|gif|webp)", RegexOption.IGNORE_CASE)

private fun overlayImageUrlOf(text: String): String? {
    val t = text.trim()
    return IMG_BBCODE.find(t)?.groupValues?.getOrNull(1)
        ?: IMG_SRC.find(t)?.groupValues?.getOrNull(1)
        ?: STEAM_IMG_URL.find(t)?.value
        ?: BARE_IMG_URL.find(t)?.value
}

@Composable
private fun overlayImage(data: Any?): ImageRequest {
    val ctx = LocalContext.current
    return remember(data) { ImageRequest.Builder(ctx).data(data).allowHardware(false).build() }
}

private val BgDark = Color(0xFF18181D)
private val SurfaceDark = Color(0xFF1E252E)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val Danger = Color(0xFFFF5A5A)

/** Facebook-Messenger-style floating chat heads rendered as a system overlay so they work over games. */
class ChatOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var bubbleView: View? = null
    private var panelView: ComposeView? = null
    private var targetView: ComposeView? = null

    private val bubbleParams by lazy { buildBubbleParams() }
    private val panelParams by lazy { buildPanelParams() }
    private val targetParams by lazy { buildTargetParams() }

    private val headFriendId = mutableLongStateOf(0L)
    private val expanded = mutableStateOf(false)
    private val conversationId = mutableLongStateOf(0L)
    private val dragging = mutableStateOf(false)
    private val bubbleDimmed = mutableStateOf(false)

    private val bubbleX = mutableIntStateOf(0)
    private val bubbleY = mutableIntStateOf(0)

    private val uiScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var idleLoopJob: Job? = null
    @Volatile private var lastInteractionMs = 0L
    private val touchHandler = Handler(Looper.getMainLooper())

    private val density by lazy { resources.displayMetrics.density }
    // Collapsed chat-head footprint; the bubble window is pinned to this exact size.
    private val bubbleSizePx by lazy { (56 * density).toInt() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.create()
        showBubble()
        startIdleLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fid = intent?.getLongExtra(EXTRA_FRIEND_ID, 0L) ?: 0L
        if (fid != 0L) {
            headFriendId.longValue = fid
            if (!expanded.value) showBubble()
        }
        pokeAutoHide()
        startIdleLoop()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        touchHandler.postDelayed({
            if (expanded.value) collapse()
            snapToEdge()
            applyBubblePosition()
        }, 150L)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleLoopJob?.cancel()
        uiScope.cancel()
        touchHandler.removeCallbacksAndMessages(null)
        removeView(panelView); panelView = null
        removeView(targetView); targetView = null
        removeView(bubbleView); bubbleView = null
        lifecycleOwner.destroy()
    }

    private fun pokeAutoHide() {
        lastInteractionMs = System.currentTimeMillis()
        if (bubbleDimmed.value) bubbleDimmed.value = false
    }

    private fun startIdleLoop() {
        if (idleLoopJob?.isActive == true) return
        idleLoopJob = uiScope.launch {
            while (true) {
                delay(1000L)
                val dim = PrefManager.chatHeadsAutoHide &&
                    bubbleView?.parent != null &&
                    System.currentTimeMillis() - lastInteractionMs >= 5000L
                if (bubbleDimmed.value != dim) bubbleDimmed.value = dim
            }
        }
    }

    private fun openConversation(friendId: Long) {
        conversationId.longValue = friendId
        if (friendId != 0L) headFriendId.longValue = friendId
    }

    private fun prepare(view: View) {
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
    }

    private fun removeView(view: View?) {
        if (view?.parent != null) runCatching { windowManager.removeView(view) }
    }

    private fun showBubble() {
        if (bubbleView?.parent != null) return
        if (bubbleX.intValue == 0 && bubbleY.intValue == 0) {
            val m = resources.displayMetrics
            bubbleX.intValue = m.widthPixels - bubbleSizePx - (12 * density).toInt()
            bubbleY.intValue = (140 * density).toInt()
        }
        bubbleParams.x = bubbleX.intValue
        bubbleParams.y = bubbleY.intValue
        val composeView = ComposeView(this).apply {
            setContent {
                WinNativeTheme {
                    val svc = remember { SteamService.instance }
                    val friends by svc?.friendsList?.collectAsState() ?: remember { mutableStateOf(emptyList<SteamFriendEntry>()) }
                    val unread by svc?.unreadCounts?.collectAsState() ?: remember { mutableStateOf(emptyMap<Long, Int>()) }
                    val head = friends.firstOrNull { it.steamId == headFriendId.longValue }
                    val alpha by animateFloatAsState(if (bubbleDimmed.value) 0.2f else 1f, label = "bubbleAlpha")
                    Box(Modifier.alpha(alpha)) {
                        BubbleContent(head, unread.values.sum())
                    }
                }
            }
        }
        val container = object : FrameLayout(this) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true
        }
        container.addView(composeView)
        prepare(container)
        attachBubbleTouch(container)
        bubbleView = container
        runCatching { windowManager.addView(container, bubbleParams) }
        pokeAutoHide()
    }

    private fun applyBubblePosition() {
        bubbleParams.x = bubbleX.intValue
        bubbleParams.y = bubbleY.intValue
        bubbleView?.let { runCatching { windowManager.updateViewLayout(it, bubbleParams) } }
    }

    private fun hideBubble() {
        touchHandler.removeCallbacksAndMessages(null)
        removeView(bubbleView)
        bubbleView = null
    }

    private fun showPanel() {
        if (panelView?.parent != null) return
        val view = ComposeView(this).apply {
            setContent {
                WinNativeTheme {
                    PanelContent()
                }
            }
        }
        prepare(view)
        panelView = view
        runCatching { windowManager.addView(view, panelParams) }
    }

    private fun hidePanel() {
        removeView(panelView)
        panelView = null
    }

    private fun showTarget() {
        if (targetView?.parent != null) return
        val view = ComposeView(this).apply {
            setContent { WinNativeTheme { DismissTarget(dragging.value) } }
        }
        prepare(view)
        targetView = view
        runCatching { windowManager.addView(view, targetParams) }
    }

    private fun hideTarget() {
        removeView(targetView)
        targetView = null
    }

    private fun expand() {
        if (expanded.value) return
        conversationId.longValue = headFriendId.longValue
        expanded.value = true
        showPanel()
        hideBubble()
    }

    private fun collapse() {
        if (!expanded.value) return
        if (conversationId.longValue == 0L) headFriendId.longValue = 0L
        expanded.value = false
        hidePanel()
        showBubble()
    }

    private fun attachBubbleTouch(view: View) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragAllowed = false
        var gestureDone = false
        val tapSlop = 14 * density
        val longPress = Runnable {
            if (bubbleView?.parent == null || gestureDone) return@Runnable
            dragAllowed = true
            dragging.value = true
            showTarget()
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = bubbleX.intValue
                    startY = bubbleY.intValue
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragAllowed = false
                    gestureDone = false
                    pokeAutoHide()
                    touchHandler.postDelayed(longPress, 400L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragAllowed) {
                        bubbleX.intValue = startX + (event.rawX - downRawX).toInt()
                        bubbleY.intValue = startY + (event.rawY - downRawY).toInt()
                        applyBubblePosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    gestureDone = true
                    touchHandler.removeCallbacks(longPress)
                    if (dragAllowed) {
                        dragging.value = false
                        hideTarget()
                        if (overDismissTarget()) stopSelf() else { snapToEdge(); applyBubblePosition() }
                    } else if (hypot(event.rawX - downRawX, event.rawY - downRawY) <= tapSlop) {
                        view.performClick()
                        expand()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    gestureDone = true
                    touchHandler.removeCallbacks(longPress)
                    if (dragAllowed) {
                        dragging.value = false
                        hideTarget()
                        snapToEdge()
                        applyBubblePosition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun overDismissTarget(): Boolean {
        val m = resources.displayMetrics
        val bubbleCx = bubbleX.intValue + bubbleSizePx / 2f
        val bubbleCy = bubbleY.intValue + bubbleSizePx / 2f
        val targetCx = m.widthPixels / 2f
        val targetCy = m.heightPixels - (96 * density)
        return hypot(bubbleCx - targetCx, bubbleCy - targetCy) < (80 * density)
    }

    private fun snapToEdge() {
        val m = resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val maxX = w - bubbleSizePx
        val maxY = h - bubbleSizePx
        val x = bubbleX.intValue.coerceIn(0, maxX)
        val y = bubbleY.intValue.coerceIn(0, maxY)
        val cx = x + bubbleSizePx / 2
        val cy = y + bubbleSizePx / 2
        when (minOf(cx, w - cx, cy, h - cy)) {
            cx -> { bubbleX.intValue = 0; bubbleY.intValue = y }
            w - cx -> { bubbleX.intValue = maxX; bubbleY.intValue = y }
            cy -> { bubbleX.intValue = x; bubbleY.intValue = 0 }
            else -> { bubbleX.intValue = x; bubbleY.intValue = maxY }
        }
    }

    @Composable
    private fun BubbleContent(head: SteamFriendEntry?, unread: Int) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                shape = CircleShape,
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(2.dp, Accent.copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val url = head?.avatarUrl
                    if (url != null) {
                        AsyncImage(
                            model = overlayImage(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
                    }
                }
            }
            if (unread > 0) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(Danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (unread > 9) "9+" else unread.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    @Composable
    private fun DismissTarget(active: Boolean) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(if (active) Danger else Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }

    @Composable
    private fun PanelContent() {
        val svc = remember { SteamService.instance }
        val friends by svc?.friendsList?.collectAsState() ?: remember { mutableStateOf(emptyList<SteamFriendEntry>()) }
        val unread by svc?.unreadCounts?.collectAsState() ?: remember { mutableStateOf(emptyMap<Long, Int>()) }
        val recent by svc?.recentChats?.collectAsState() ?: remember { mutableStateOf(emptyMap<Long, Long>()) }
        val convId = conversationId.longValue
        val current = friends.firstOrNull { it.steamId == convId }
        val head = if (convId != 0L) current else null

        val ordered = remember(friends, unread, recent) {
            friends.sortedWith(
                compareByDescending<SteamFriendEntry> { (unread[it.steamId] ?: 0) > 0 }
                    .thenByDescending { recent[it.steamId] ?: 0L }
                    .thenByDescending { it.isPlayingGame }
                    .thenByDescending { it.isOnline }
                    .thenBy { it.name.lowercase() },
            )
        }

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val marginPx = (8 * density).toInt()
        val rightSide = bubbleX.intValue + bubbleSizePx / 2 >= screenW / 2
        val panelWpx = (screenW * 0.4f).toInt().coerceIn((280 * density).toInt(), screenW - 2 * marginPx)
        val panelHpx = (screenH * 0.82f).toInt().coerceAtLeast((320 * density).toInt())
        val gapPx = (10 * density).toInt()
        val panelXpx = if (rightSide) {
            (bubbleX.intValue - panelWpx - gapPx).coerceAtLeast(marginPx)
        } else {
            (bubbleX.intValue + bubbleSizePx + gapPx).coerceAtMost((screenW - panelWpx - marginPx).coerceAtLeast(marginPx))
        }
        val panelYpx = bubbleY.intValue.coerceIn(marginPx, (screenH - panelHpx - marginPx).coerceAtLeast(marginPx))
        val originY = if (panelHpx > 0) ((bubbleY.intValue - panelYpx).toFloat() / panelHpx).coerceIn(0f, 1f) else 0f
        val origin = TransformOrigin(if (rightSide) 1f else 0f, originY)

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { collapse() },
            )
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(transformOrigin = origin) + fadeIn(),
                modifier = Modifier.offset { IntOffset(panelXpx, panelYpx) },
            ) {
                Surface(
                    color = BgDark,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shadowElevation = 12.dp,
                    modifier = Modifier.width((panelWpx / density).dp).height((panelHpx / density).dp),
                ) {
                    Column(Modifier.fillMaxSize().imePadding()) {
                        Box(
                            Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            val title = current?.let { it.name.ifBlank { it.steamId.toString() } }
                                ?: stringResource(R.string.steam_chat_heads_friends_title)
                            Text(
                                title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 96.dp),
                            )
                            if (convId != 0L) {
                                IconButton(onClick = { conversationId.longValue = 0L }, modifier = Modifier.align(Alignment.CenterStart)) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.steam_common_back), tint = TextPrimary)
                                }
                            }
                            IconButton(onClick = { collapse() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.steam_common_back), tint = TextSecondary)
                            }
                        }
                        Crossfade(targetState = convId, label = "panelBody", modifier = Modifier.weight(1f)) { id ->
                            val conv = friends.firstOrNull { it.steamId == id }
                            if (id != 0L && conv != null) {
                                ConversationView(conv, Modifier.fillMaxSize())
                            } else {
                                ConversationListView(ordered, unread, Modifier.fillMaxSize()) { picked ->
                                    openConversation(picked)
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(bubbleX.intValue, bubbleY.intValue) }
                    .clickable { collapse() },
            ) {
                BubbleContent(head, unread.values.sum())
            }
        }
    }

    @Composable
    private fun ConversationListView(
        list: List<SteamFriendEntry>,
        unread: Map<Long, Int>,
        modifier: Modifier,
        onPick: (Long) -> Unit,
    ) {
        if (list.isEmpty()) {
            Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.steam_friends_none_loaded), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            return
        }
        LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(list, key = { it.steamId }) { f -> FriendRow(f, unread[f.steamId] ?: 0) { onPick(f.steamId) } }
        }
    }

    @Composable
    private fun FriendRow(f: SteamFriendEntry, unread: Int, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(SurfaceDark), contentAlignment = Alignment.Center) {
                if (f.avatarUrl != null) {
                    AsyncImage(model = overlayImage(f.avatarUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.name.ifBlank { f.steamId.toString() },
                    color = if (f.isOnline) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (f.isPlayingGame) f.gameName.ifBlank { stringResource(R.string.steam_friends_in_game) }
                    else if (f.isOnline) stringResource(R.string.stores_accounts_status_online)
                    else stringResource(R.string.stores_accounts_status_offline),
                    color = if (f.isPlayingGame) Accent else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (f.isPlayingGame && f.gameCapsuleUrl != null) {
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = overlayImage(f.gameCapsuleUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(56.dp).height(21.dp).clip(RoundedCornerShape(3.dp)).background(BgDark),
                )
            }
            if (unread > 0) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Danger), contentAlignment = Alignment.Center) {
                    Text(if (unread > 9) "9+" else unread.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    @Composable
    private fun ConversationView(friend: SteamFriendEntry, modifier: Modifier) {
        val messages = remember(friend.steamId) { SnapshotStateList<SteamChatMessage>() }
        var input by remember(friend.steamId) { mutableStateOf("") }
        var sending by remember(friend.steamId) { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        DisposableEffect(friend.steamId) {
            SteamService.instance?.setActiveConversation(friend.steamId)
            onDispose { SteamService.instance?.clearActiveConversation(friend.steamId) }
        }
        LaunchedEffect(friend.steamId) {
            messages.clear()
            messages.addAll(SteamService.instance?.loadChatHistory(friend.steamId) ?: emptyList())
            if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
            SteamService.instance?.incomingChat?.collect { (fid, m) ->
                if (fid != friend.steamId) return@collect
                val known = messages.map { it.timestamp to it.ordinal }.toHashSet()
                if (m.timestamp != 0 && (m.timestamp to m.ordinal) in known) return@collect
                val optIdx = if (m.fromSelf) messages.indexOfFirst { it.fromSelf && it.timestamp == 0 && it.text == m.text } else -1
                if (optIdx >= 0) messages[optIdx] = m else {
                    messages.add(m)
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
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
                    if (idx >= 0) messages[idx] = optimistic.copy(text = "$text  " + getString(R.string.steam_chat_not_sent))
                }
                sending = false
            }
        }

        Column(modifier.fillMaxWidth()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(messages.size) { i -> OverlayMessageBubble(messages[i]) }
            }
            Row(
                Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    headFriendId.longValue = friend.steamId
                    collapse()
                    runCatching {
                        startActivity(
                            Intent(this@ChatOverlayService, ChatImagePickerActivity::class.java)
                                .putExtra(ChatImagePickerActivity.EXTRA_FRIEND_ID, friend.steamId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) {
                    Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.steam_chat_send_image), tint = Accent)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.steam_chat_message_hint), color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                        focusedIndicatorColor = Accent,
                        unfocusedIndicatorColor = CardBorder,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { send() }, enabled = input.isNotBlank() && !sending) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.steam_chat_send),
                        tint = if (input.isNotBlank() && !sending) Accent else TextSecondary,
                    )
                }
            }
        }
    }

    @Composable
    private fun OverlayMessageBubble(message: SteamChatMessage) {
        val url = overlayImageUrlOf(message.text)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.fromSelf) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (message.fromSelf) Accent.copy(alpha = 0.18f) else SurfaceDark,
                modifier = Modifier.widthIn(max = 260.dp),
            ) {
                if (url != null) {
                    AsyncImage(
                        model = overlayImage(url),
                        contentDescription = stringResource(R.string.steam_chat_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(4.dp).width(220.dp).heightIn(min = 100.dp, max = 220.dp).clip(RoundedCornerShape(10.dp)).background(BgDark),
                    )
                } else {
                    Text(message.text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
    }

    private fun buildBubbleParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            // Fixed size, not WRAP_CONTENT, so the overlay can't mis-measure to full screen.
            bubbleSizePx,
            bubbleSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun buildPanelParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

    private fun buildTargetParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (64 * density).toInt()
        }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun create() {
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    companion object {
        private const val EXTRA_FRIEND_ID = "friendId"

        fun onIncoming(context: Context, friendId: Long) {
            if (!PrefManager.chatHeadsEnabled || !Settings.canDrawOverlays(context)) return
            val intent = Intent(context, ChatOverlayService::class.java).putExtra(EXTRA_FRIEND_ID, friendId)
            runCatching { context.startService(intent) }
        }

        fun start(context: Context) {
            if (!PrefManager.chatHeadsEnabled || !Settings.canDrawOverlays(context)) return
            runCatching { context.startService(Intent(context, ChatOverlayService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ChatOverlayService::class.java)) }
        }
    }
}
